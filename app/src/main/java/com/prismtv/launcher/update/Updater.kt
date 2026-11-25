package com.prismtv.launcher.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.prismtv.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A newer Prism TV build published by CI to the repo's `preview` release. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
)

sealed interface InstallEvent {
    data object Installing : InstallEvent
    data class Failed(val message: String) : InstallEvent
    /** Android needs the user to confirm (always on Android 11 and older). */
    data class NeedsConfirmation(val intent: Intent) : InstallEvent
}

/**
 * Self-update from GitHub. CI publishes `update.json` next to the APKs on the `preview` release; the
 * launcher compares its version code, asks the user, downloads the APK, checks it's signed with the
 * same key, and installs it through [PackageInstaller]. On Android 12+ an app updating itself needs no
 * extra system prompt once the user has agreed in Prism TV; Android 11 and older show the system's
 * install confirmation as well.
 */
object Updater {
    private const val TAG = "PrismUpdate"
    /**
     * Where CI publishes the APK and update.json: a public releases-only repository (BuildConfig.UPDATE_URL),
     * so the code repository can be private. Builds made before the move read the code repository's own
     * "preview" release ([LEGACY]); CI publishes to both until it's made private, and this falls back to it.
     */
    private val RELEASES: List<String> = if (BuildConfig.PLAY) emptyList() else listOf(
        BuildConfig.UPDATE_URL,
        "https://github.com/delosdinh/Prism-TV-Launcher-Android-TV/releases/download/preview",
    ).filter { it.isNotBlank() }.distinct()

    private val _events = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<InstallEvent> = _events

    internal fun emit(e: InstallEvent) {
        _events.tryEmit(e)
    }

    /** Returns the newer build, or null when this is the latest (or the check failed). */
    suspend fun check(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            var lastError: Throwable? = null
            var release = ""
            var text: String? = null
            for (r in RELEASES) {
                text = runCatching { httpText("$r/update.json") }.onFailure { lastError = it }.getOrNull()
                if (text != null) {
                    release = r
                    break
                }
            }
            val json = JSONObject(text ?: throw (lastError ?: IllegalStateException("No update source")))
            val code = json.getInt("versionCode")
            if (code <= BuildConfig.VERSION_CODE) return@runCatching null
            val apk = if (BuildConfig.DEBUG) json.optString("debugApk", "prismtv-debug.apk") else json.optString("apk", "prismtv.apk")
            UpdateInfo(
                versionCode = code,
                versionName = json.optString("versionName", code.toString()),
                notes = json.optString("notes").trim(),
                apkUrl = if (apk.startsWith("http")) apk else "$release/$apk",
            )
        }.onFailure { Log.w(TAG, "Update check failed", it) }
    }

    /** Downloads the APK into the cache, reporting progress 0..1. */
    suspend fun download(ctx: Context, info: UpdateInfo, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "prismtv-${info.versionCode}.apk")
        val conn = open(info.apkUrl)
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
                        if (total != null && done - last > 256 * 1024) {
                            last = done
                            progress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        progress(1f)
        out
    }

    enum class Verdict { Ok, NotAnUpdate, DifferentSigner, Unreadable }

    /** The download must be Prism TV, newer, and signed with the same key as this install. */
    fun verify(ctx: Context, apk: File): Verdict {
        val pm = ctx.packageManager
        val archive = archiveInfo(pm, apk) ?: return Verdict.Unreadable
        if (archive.packageName != ctx.packageName) return Verdict.Unreadable
        if (versionCodeOf(archive) <= BuildConfig.VERSION_CODE) return Verdict.NotAnUpdate
        val installed = installedInfo(pm, ctx.packageName) ?: return Verdict.Unreadable
        return if (signers(archive).toSet() == signers(installed).toSet() && signers(installed).isNotEmpty()) Verdict.Ok else Verdict.DifferentSigner
    }

    /** "Install unknown apps" must be allowed for Prism TV (Android 8+). */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun permissionIntent(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Streams the APK into a PackageInstaller session and commits it. Results arrive via [events]. */
    suspend fun install(ctx: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("prismtv.apk", 0, apk.length()).use { out ->
                    input.copyTo(out, 64 * 1024)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, UpdateReceiver::class.java).setPackage(ctx.packageName), flags,
            )
            emit(InstallEvent.Installing)
            session.commit(callback.intentSender)
        }
    }

    // ------------------------------------------------------------------------------------ helpers

    internal fun open(url: String): HttpURLConnection {
        var target = url
        repeat(5) {
            val c = URL(target).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "PrismTV/${BuildConfig.VERSION_NAME}")
            c.setRequestProperty("Cache-Control", "no-cache")
            val code = c.responseCode
            if (code in 300..399) {
                val next = c.getHeaderField("Location") ?: error("Redirect without a location")
                c.disconnect()
                target = URL(URL(target), next).toString()
            } else {
                if (code !in 200..299) {
                    c.disconnect()
                    error("HTTP $code for $target")
                }
                return c
            }
        }
        error("Too many redirects")
    }

    private fun httpText(url: String): String {
        val c = open(url)
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        pm.getPackageArchiveInfo(apk.absolutePath, flags)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager, pkg: String): PackageInfo? = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        pm.getPackageInfo(pkg, flags)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): List<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return sigs?.map { it.toCharsString() } ?: emptyList()
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
}

/** Receives the PackageInstaller result for a self-update. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    Updater.emit(InstallEvent.NeedsConfirmation(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
                } else {
                    Updater.emit(InstallEvent.Failed("Android didn't return an install prompt."))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // Prism TV is replaced and restarts as the Home app.
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w("PrismUpdate", "Install failed ($status): $msg")
                Updater.emit(
                    InstallEvent.Failed(
                        when (status) {
                            PackageInstaller.STATUS_FAILURE_ABORTED -> "The update was cancelled."
                            PackageInstaller.STATUS_FAILURE_STORAGE -> "There isn't enough storage for the update."
                            PackageInstaller.STATUS_FAILURE_CONFLICT,
                            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This update can't replace the installed version. Reinstall Prism TV once from Downloader."
                            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the update. Allow Prism TV to install unknown apps."
                            else -> msg ?: "The update couldn't be installed."
                        },
                    ),
                )
            }
        }
    }
}
