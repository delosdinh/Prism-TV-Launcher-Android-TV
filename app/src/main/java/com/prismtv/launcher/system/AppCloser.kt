package com.prismtv.launcher.system

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File

/**
 * Closes apps swiped away in the App Switcher — really closes them, the way Settings' Force Stop does.
 *
 * Android doesn't let one app stop another, so a launcher on its own can only ask Android to drop an
 * app's cached processes (killBackgroundProcesses). That frees memory, but an app with a foreground
 * service (music, a download, a cast session) keeps running, and Android's own Recents still lists it.
 * To stop an app completely Prism TV uses, in order:
 *
 *  1. **Shizuku**, when it's running and Prism TV is allowed: `am force-stop`, instantly and invisibly.
 *  2. **Root**, when you've allowed it in Settings › Apps › Closing Apps.
 *  3. **Power & Home Button** (Prism TV's accessibility service): it opens the app's page in Settings
 *     behind a still of the App Switcher and presses Force Stop and OK for you, then comes back.
 *  4. Otherwise memory only (and a one-time note on how to close apps completely).
 */
object AppCloser {
    enum class Method(val label: String) {
        Shizuku("Shizuku"),
        Root("Root"),
        Accessibility("Accessibility"),
        MemoryOnly("Memory Only"),
    }

    /**
     * [stopped]: every app now fully stopped (including ones that already were); [closed]: the ones that
     * were running and have been stopped now; [failed]: running apps that couldn't be stopped (they only
     * had their memory freed).
     */
    class Result(val method: Method, val stopped: Set<String>, val closed: Set<String>, val failed: Set<String>)

    /** A still of Prism TV's window (set by MainActivity), shown while Settings does the closing. */
    @Volatile
    var snapshotter: (suspend () -> Bitmap?)? = null

    private val lock = Mutex()

    fun method(completely: Boolean, rootAllowed: Boolean): Method = when {
        !completely -> Method.MemoryOnly
        ShizukuShell.ready() -> Method.Shizuku
        rootAllowed && RootShell.present() -> Method.Root
        PrismAccessibilityService.canCloseApps -> Method.Accessibility
        else -> Method.MemoryOnly
    }

    /** Closing this way briefly opens Settings (under a still of Prism TV), so it's best done in a batch. */
    fun opensSettings(m: Method) = m == Method.Accessibility

    /** True once Android has the app in its "stopped" state (after a force stop, until it's opened again). */
    fun isStopped(ctx: Context, pkg: String): Boolean = try {
        (ctx.packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_STOPPED) != 0
    } catch (_: Throwable) {
        false
    }

    /**
     * Apps that must never be force-stopped from here: Prism TV itself, Settings, the keyboard in use,
     * accessibility services, Shizuku and Android's persistent system apps.
     */
    fun closable(ctx: Context, pkg: String): Boolean {
        if (pkg == ctx.packageName || pkg == ShizukuShell.Package || !SafePackage.matches(pkg)) return false
        val info = try {
            ctx.packageManager.getApplicationInfo(pkg, 0)
        } catch (_: Throwable) {
            return false
        }
        if ((info.flags and ApplicationInfo.FLAG_PERSISTENT) != 0) return false
        if (pkg in settingsPackages(ctx)) return false
        val cr = ctx.contentResolver
        val ime = runCatching { Settings.Secure.getString(cr, Settings.Secure.DEFAULT_INPUT_METHOD) }.getOrNull().orEmpty()
        if (ime.substringBefore('/') == pkg) return false
        val services = runCatching { Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) }.getOrNull().orEmpty()
        if (services.split(':').any { it.substringBefore('/') == pkg }) return false
        return true
    }

    private fun settingsPackages(ctx: Context): Set<String> {
        val out = hashSetOf("com.android.settings", "com.android.tv.settings", "com.google.android.tv.settings", "com.nvidia.tvsettings")
        detailsPackage(ctx, "android")?.let(out::add)
        return out
    }

    /** The app that shows an app's page (Settings › Apps › the app), where Force Stop lives. */
    fun detailsPackage(ctx: Context, pkg: String): String? = try {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg"))
        ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            ?.takeIf { it != "android" }
    } catch (_: Throwable) {
        null
    }

    /**
     * Closes [apps] (package → name) with the best method available. Anything that couldn't be stopped
     * still has its memory freed. One batch at a time.
     */
    suspend fun close(ctx: Context, apps: List<Pair<String, String>>, completely: Boolean, rootAllowed: Boolean): Result = lock.withLock {
        val m = method(completely, rootAllowed)
        val stopped = HashSet<String>()
        var tried = emptySet<String>()
        if (m != Method.MemoryOnly) {
            val targets = withContext(Dispatchers.IO) { apps.filter { (pkg, _) -> closable(ctx, pkg) } }
            // Already stopped (not opened since its last force stop): nothing is running.
            val (idle, running) = targets.partition { isStopped(ctx, it.first) }
            stopped += idle.map { it.first }
            tried = running.map { it.first }.toSet()
            when (m) {
                Method.Shizuku -> withContext(Dispatchers.IO) {
                    // Gone from Android's own app switcher too (a force stop leaves its card there).
                    val recents = { SystemRecents.remove(ctx, targets.map { it.first }) { ShizukuShell.exec(it) } }
                    if (SystemRecents.removeFirst) recents()
                    running.forEach { (pkg, _) -> if (ShizukuShell.forceStop(pkg)) stopped += pkg }
                    if (!SystemRecents.removeFirst) recents()
                }
                Method.Root -> withContext(Dispatchers.IO) {
                    val recents = { SystemRecents.remove(ctx, targets.map { it.first }) { RootShell.exec(it) } }
                    if (SystemRecents.removeFirst) recents()
                    running.forEach { (pkg, _) -> if (RootShell.forceStop(pkg)) stopped += pkg }
                    if (!SystemRecents.removeFirst) recents()
                }
                Method.Accessibility -> if (running.isNotEmpty()) {
                    val still = try {
                        snapshotter?.invoke()
                    } catch (_: Throwable) {
                        null
                    }
                    stopped += PrismAccessibilityService.forceStop(running, still)
                }
                Method.MemoryOnly -> Unit
            }
        }
        withContext(Dispatchers.IO) {
            apps.forEach { (pkg, _) -> if (pkg !in stopped) SystemActions.killBackground(ctx, pkg) }
        }
        Result(m, stopped, tried intersect stopped, tried - stopped)
    }

    private val SafePackage = Regex("[A-Za-z0-9_.]+")

    /** Reads [p]'s output (stdout then stderr) until it ends, up to [ms]; its exit code and output. */
    internal fun readAll(p: Process, ms: Long): Pair<Int?, String> {
        var out = ""
        val reader = Thread {
            out = runCatching { p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText() }.getOrDefault("")
        }
        reader.isDaemon = true
        reader.start()
        reader.join(ms)
        if (reader.isAlive) {
            runCatching { p.destroy() }
            return null to out
        }
        return waitFor(p, 2000) to out
    }

    /** Waits up to [ms] for [p] to finish; its exit code, or null if it didn't. */
    internal fun waitFor(p: Process, ms: Long): Int? {
        var code: Int? = null
        val t = Thread { code = runCatching { p.waitFor() }.getOrNull() }
        t.isDaemon = true
        t.start()
        t.join(ms)
        if (t.isAlive) {
            runCatching { p.destroy() }
            return null
        }
        runCatching { p.inputStream.close() }
        runCatching { p.errorStream.close() }
        runCatching { p.outputStream.close() }
        return code
    }
}

/**
 * Shizuku (https://shizuku.rikka.app): runs commands as Android's shell user, which may force stop
 * apps. Started over Wireless debugging or adb; Prism TV asks for permission once.
 */
object ShizukuShell {
    const val Package = "moe.shizuku.privileged.api"
    private const val RequestCode = 7301

    enum class Status(val label: String) {
        NotInstalled("Not Installed"),
        NotRunning("Not Running"),
        NeedsPermission("Allow"),
        Denied("Not Allowed"),
        TooOld("Update Shizuku"),
        Ready("On"),
    }

    private fun alive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getApplicationInfo(Package, 0)
        true
    } catch (_: Throwable) {
        false
    }

    fun status(ctx: Context): Status {
        if (!alive()) return if (installed(ctx)) Status.NotRunning else Status.NotInstalled
        return try {
            when {
                Shizuku.isPreV11() -> Status.TooOld
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Status.Ready
                Shizuku.shouldShowRequestPermissionRationale() -> Status.Denied
                else -> Status.NeedsPermission
            }
        } catch (_: Throwable) {
            Status.NotRunning
        }
    }

    fun ready(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /** Shows Shizuku's own Allow / Deny prompt; [done] gets the answer (on the main thread). */
    fun requestPermission(done: (Boolean) -> Unit) {
        try {
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != RequestCode) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    done(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(RequestCode)
        } catch (_: Throwable) {
            done(false)
        }
    }

    fun forceStop(pkg: String): Boolean = run(arrayOf("am", "force-stop", pkg)) == 0

    /** Runs a shell command as Shizuku's shell user: its exit code and output, or null if Shizuku couldn't. */
    fun exec(command: String): Pair<Int?, String>? = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        AppCloser.readAll(p, 15_000)
    } catch (_: Throwable) {
        null
    }

    /** Shizuku API 13 keeps newProcess private (it prefers bound services); it's still the simplest way to run one command. */
    private fun run(cmd: Array<String>): Int? = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        )
        m.isAccessible = true
        val p = m.invoke(null, cmd, null, null) as Process
        AppCloser.waitFor(p, 5000)
    } catch (_: Throwable) {
        null
    }
}

/** `su`, on rooted boxes — only used once you've turned on Root Access in Closing Apps. */
object RootShell {
    private val Paths = listOf(
        "/system/xbin/su", "/system/bin/su", "/sbin/su", "/su/bin/su", "/system/sbin/su",
        "/vendor/bin/su", "/debug_ramdisk/su", "/data/adb/ksu/bin/su", "/data/adb/magisk",
    )

    fun present(): Boolean = Paths.any { runCatching { File(it).exists() }.getOrDefault(false) }

    /** Asks for root (the root manager may show its prompt) and checks it works. */
    fun test(): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id -u"))
        var out = ""
        // Reads until su exits; the root manager's prompt can take a while to be answered.
        val reader = Thread { out = runCatching { p.inputStream.bufferedReader().readText() }.getOrDefault("") }
        reader.isDaemon = true
        reader.start()
        reader.join(20_000)
        if (reader.isAlive) {
            runCatching { p.destroy() }
            false
        } else {
            AppCloser.waitFor(p, 2000) == 0 && out.trim() == "0"
        }
    } catch (_: Throwable) {
        false
    }

    fun forceStop(pkg: String): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $pkg"))
        AppCloser.waitFor(p, 8000) == 0
    } catch (_: Throwable) {
        false
    }

    fun exec(command: String): Pair<Int?, String>? = try {
        AppCloser.readAll(Runtime.getRuntime().exec(arrayOf("su", "-c", command)), 15_000)
    } catch (_: Throwable) {
        null
    }
}


/**
 * Android's own app switcher (Recent apps). A force stop leaves an app's card there, so with Shizuku or
 * root Prism TV removes the cards of the apps it closes, or all of them.
 */
object SystemRecents {
    // A card's first line: "Recent #0: Task{3f9b6e1 #1234 type=standard A=10123:com.example.app.main U=0
    // StackId=1234 sz=1}" (Android 11; 12+ has no StackId). The A= name is the task's affinity, which
    // isn't always the package (YouTube's is "com.google.android.youtube.tv.main"), so a card is matched
    // by its activity (realActivity=, mActivityComponent= or cmp= further down) or by the app's user id.
    private val header = Regex("""Recent #\d+: Task(?:Record)?\{[0-9a-f]+ #(\d+)(.*)""")
    private val rootTask = Regex("""StackId=(\d+)""")
    private val appUid = Regex("""A=(\d+):""")
    private val component = Regex("""(?:realActivity|mActivityComponent|cmp)=\{?([A-Za-z0-9_.]+)/""")
    private val affinity = Regex("""(?:A=(?:\d+:)?|I=)([A-Za-z0-9_.]+)""")

    /**
     * `am stack remove` takes a task id from Android 12. On Android 11 (the SHIELD) it takes the task's
     * root task ("StackId="), and only while the task is still open, so there the cards go before the
     * force stop (a stopped app's card is then only a record).
     */
    val removeSupported: Boolean get() = android.os.Build.VERSION.SDK_INT >= 30
    val removeFirst: Boolean get() = android.os.Build.VERSION.SDK_INT < 31

    /** Closing Apps › Clear Android's Recent Apps. */
    val clearAllSupported: Boolean get() = android.os.Build.VERSION.SDK_INT >= 30

    private class Card(val id: String, val names: Set<String>, val uid: Int?)

    private fun cards(dump: String): List<Card> {
        val out = ArrayList<Card>()
        var id: String? = null
        var names = HashSet<String>()
        var uid: Int? = null
        fun flush() { id?.let { out += Card(it, names, uid) } }
        for (line in dump.lineSequence()) {
            val h = header.find(line)
            if (h != null) {
                flush()
                val rest = h.groupValues[2]
                id = if (android.os.Build.VERSION.SDK_INT >= 31) h.groupValues[1]
                    else rootTask.find(rest)?.groupValues?.get(1)?.takeIf { it != "-1" && it != "0" } ?: h.groupValues[1]
                names = HashSet<String>().apply { affinity.find(rest)?.let { add(it.groupValues[1]) } }
                uid = appUid.find(rest)?.groupValues?.get(1)?.toIntOrNull()
            } else if (id != null) {
                component.findAll(line).forEach { names += it.groupValues[1] }
            }
        }
        flush()
        return out
    }

    /** Removes the cards of [pkgs]; how many went. */
    fun remove(ctx: android.content.Context, pkgs: Collection<String>, exec: (String) -> Pair<Int?, String>?): Int {
        if (!removeSupported || pkgs.isEmpty()) return 0
        val out = runCatching { exec("dumpsys activity recents")?.second }.getOrNull() ?: return 0
        // App user ids (10000 and up: shared system ids would match other apps' cards).
        val uids = pkgs.mapNotNull { p -> runCatching { ctx.packageManager.getApplicationInfo(p, 0).uid }.getOrNull()?.takeIf { it >= 10_000 } }.toSet()
        val ids = cards(out).filter { c -> c.names.any { it in pkgs } || (c.uid != null && c.uid in uids) }.map { it.id }.distinct()
        if (ids.isEmpty()) return 0
        runCatching { exec(ids.joinToString("; ") { "am stack remove $it" }) }
        return ids.size
    }

    /**
     * Every app's card goes (Android's own "Clear all"), except Prism TV's. One line you could type too.
     * Android 11 removes only the apps still open; their stopped apps' cards are records it can't reach.
     */
    val ClearAll: String get() {
        val id = if (android.os.Build.VERSION.SDK_INT >= 31) "sed -E 's/.*Task\\{[0-9a-f]+ #([0-9]+).*/\\1/'"
            else "grep -oE 'StackId=[0-9]+' | cut -d= -f2"
        return "dumpsys activity recents | grep 'Recent #' | grep 'type=standard' | grep -v com.prismtv.launcher | $id | " +
            "while read t; do am stack remove \$t; done; true"
    }
}
