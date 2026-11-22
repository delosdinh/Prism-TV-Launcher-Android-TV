package com.prismtv.launcher.system

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.prismtv.launcher.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.File

/**
 * Settings › Apps › Closing Apps › Shizuku: gets Shizuku going from the TV, step by step.
 *
 *  1. Install — downloads the latest Shizuku from its GitHub releases and installs it (Android asks).
 *  2. USB debugging — opens Developer options (or About, to turn them on).
 *  3. Start — connects to the TV's own adb ([LocalAdb]) and runs Shizuku's starter as the shell user.
 *  4. Allow — Shizuku's own prompt for Prism TV.
 *
 * Shizuku stops when the TV restarts; with "Start Shizuku When the TV Starts" Prism TV starts it again
 * quietly (only once you've allowed Prism TV's adb key with "Always allow").
 */
object ShizukuSetup {
    private const val TAG = "PrismShizuku"
    private const val Releases = "https://api.github.com/repos/RikkaApps/Shizuku/releases/latest"
    private const val StartScript = "/storage/emulated/0/Android/data/${ShizukuShell.Package}/start.sh"

    sealed interface Event {
        data object Installed : Event
        data class Failed(val message: String) : Event
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events
    internal fun emit(e: Event) {
        _events.tryEmit(e)
    }

    // ------------------------------------------------------------------ 1. install

    /** Downloads the newest Shizuku APK (progress 0..1); null when GitHub couldn't be reached. */
    suspend fun download(ctx: Context, progress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        runCatching {
            val release = Updater.open(Releases).let { c ->
                try {
                    c.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    c.disconnect()
                }
            }
            val assets = JSONObject(release).getJSONArray("assets")
            val url = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url")
                ?: error("No APK in the latest Shizuku release")
            val dir = File(ctx.cacheDir, "shizuku").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "shizuku.apk")
            val conn = Updater.open(url)
            try {
                val total = conn.contentLengthLong.takeIf { it > 0 }
                conn.inputStream.use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var last = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            done += n
                            if (total != null && done - last > 128 * 1024) {
                                last = done
                                progress((done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            @Suppress("DEPRECATION")
            val info = ctx.packageManager.getPackageArchiveInfo(out.absolutePath, 0)
            require(info?.packageName == ShizukuShell.Package) { "The download isn't Shizuku" }
            out
        }.onFailure { Log.w(TAG, "Shizuku download failed", it) }.getOrNull()
    }

    /** Hands the APK to Android's installer; the result (and its confirmation screen) comes through [events]. */
    suspend fun install(ctx: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ShizukuShell.Package)
            setSize(apk.length())
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("shizuku.apk", 0, apk.length()).use { out ->
                    input.copyTo(out, 64 * 1024)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(
                ctx, 7302, Intent(ctx, ShizukuInstallReceiver::class.java).setPackage(ctx.packageName), flags,
            )
            session.commit(callback.intentSender)
        }
    }

    // ------------------------------------------------------------------ 3. start

    sealed interface StartResult {
        data object Started : StartResult
        data object NotInstalled : StartResult
        data object DebuggingOff : StartResult
        data object Unreachable : StartResult
        data object NotAllowed : StartResult
        data class Failed(val detail: String) : StartResult
    }

    private fun alive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /**
     * Starts Shizuku through the TV's own adb. Its start.sh (written when Shizuku is first opened) is
     * used when it's there; otherwise its starter is copied out of the app and run, as start.sh does.
     */
    fun start(ctx: Context, allowPrompt: Boolean): StartResult {
        if (!ShizukuShell.installed(ctx)) return StartResult.NotInstalled
        if (alive()) return StartResult.Started
        val cmd = "if [ -f $StartScript ]; then sh $StartScript; else " +
            "P=\$(pm path ${ShizukuShell.Package} | head -n 1 | cut -d: -f2); D=\$(dirname \"\$P\"); " +
            "S=\$(ls \"\$D\"/lib/*/libshizuku.so 2>/dev/null | head -n 1); " +
            "if [ -z \"\$S\" ]; then echo 'no starter'; false; else " +
            "cp \"\$S\" /data/local/tmp/shizuku_starter && chmod 700 /data/local/tmp/shizuku_starter && /data/local/tmp/shizuku_starter; fi; fi"
        return when (val r = LocalAdb.shell(ctx, cmd, allowPrompt)) {
            LocalAdb.Result.Off -> StartResult.DebuggingOff
            LocalAdb.Result.Unreachable -> StartResult.Unreachable
            LocalAdb.Result.NotAllowed -> StartResult.NotAllowed
            is LocalAdb.Result.Failed -> StartResult.Failed(r.why)
            is LocalAdb.Result.Ok -> {
                ctx.getSharedPreferences("prism_prefs", Context.MODE_PRIVATE).edit().putBoolean("adbAuthorized", true).apply()
                Log.i(TAG, "starter exit ${r.exit}: ${r.output.takeLast(400)}")
                // Shizuku hands Prism TV its binder once it's up.
                repeat(40) {
                    if (alive()) return StartResult.Started
                    Thread.sleep(250)
                }
                StartResult.Failed(r.output.lines().lastOrNull { it.isNotBlank() }?.take(160) ?: "Shizuku didn't start.")
            }
        }
    }

    /** After a restart: start Shizuku again without any prompt, if you've set it up before. */
    fun startQuietly(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences("prism_prefs", Context.MODE_PRIVATE)
        if (!sp.getBoolean("adbAuthorized", false) || !ShizukuShell.installed(ctx) || alive() || !LocalAdb.enabled(ctx)) return false
        return start(ctx, allowPrompt = false) == StartResult.Started
    }
}

/** The result of installing Shizuku: Android's confirmation screen, then success or failure. */
class ShizukuInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm == null || !SystemActions.start(context, confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                    ShizukuSetup.emit(ShizukuSetup.Event.Failed("Android didn't show its install screen."))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> ShizukuSetup.emit(ShizukuSetup.Event.Installed)
            else -> ShizukuSetup.emit(
                ShizukuSetup.Event.Failed(
                    when (status) {
                        PackageInstaller.STATUS_FAILURE_ABORTED -> "Shizuku wasn't installed."
                        PackageInstaller.STATUS_FAILURE_STORAGE -> "There isn't enough storage for Shizuku."
                        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the install. Allow Prism TV to install unknown apps."
                        else -> intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Shizuku couldn't be installed."
                    },
                ),
            )
        }
    }
}
