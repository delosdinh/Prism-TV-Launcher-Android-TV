package com.prismtv.launcher.system

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Control Centre › Free Up Memory, beyond closing the App Switcher's apps:
 *
 *  - every installed app's background processes go, not only those of apps on the Home Screen
 *    (Google services' spare processes, the Play Store, apps that woke for a job). Android only lets this
 *    stop processes that are idle or running a plain background service: anything playing, on screen or
 *    holding a notification keeps going.
 *  - with Shizuku or root (Settings › Apps › Closing Apps), what's still running is asked to give back
 *    what it can (the same request Android makes when memory runs short): hidden apps drop their caches
 *    and graphics memory, running ones trim theirs.
 *
 * The keyboard, the Assistant and accessibility services keep their processes, so they still answer at
 * once.
 */
object MemoryCleaner {
    private val Assistant = setOf("com.google.android.katniss", "com.google.android.googlequicksearchbox", "com.amazon.vizzini")

    /** Packages whose processes stay. [playing]: the app with something playing, if any. */
    fun keep(ctx: Context, playing: String?): Set<String> {
        val out = hashSetOf(ctx.packageName, ShizukuShell.Package)
        out += Assistant
        playing?.let(out::add)
        val cr = ctx.contentResolver
        runCatching { Settings.Secure.getString(cr, Settings.Secure.DEFAULT_INPUT_METHOD) }.getOrNull()
            ?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let(out::add)
        runCatching { Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) }.getOrNull().orEmpty()
            .split(':').map { it.substringBefore('/') }.filter { it.isNotBlank() }.forEach(out::add)
        // Android 11 still lets apps read which Assistant is chosen; later versions return nothing.
        runCatching { Settings.Secure.getString(cr, "voice_interaction_service") }.getOrNull()
            ?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let(out::add)
        return out
    }

    /**
     * Asks Android to stop the background processes of every installed app but [keep]'s and its own
     * system apps (shared with Android itself, or persistent). Returns how many packages were asked.
     * Android 14+ only lets an app stop its own processes this way, so there it's the shell's job.
     */
    fun killBackground(ctx: Context, keep: Set<String>): Int {
        if (Build.VERSION.SDK_INT >= 34) return 0
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val apps = runCatching { ctx.packageManager.getInstalledApplications(0) }.getOrDefault(emptyList())
        var asked = 0
        for (info in apps) {
            if (info.packageName in keep) continue
            if (info.uid < Process.FIRST_APPLICATION_UID || (info.flags and ApplicationInfo.FLAG_PERSISTENT) != 0) continue
            if ((info.flags and ApplicationInfo.FLAG_STOPPED) != 0) continue // not running since its last force stop
            runCatching { am.killBackgroundProcesses(info.packageName) }
            asked++
        }
        return asked
    }

    /**
     * The shell's part (run as Shizuku's shell user or root). Each app process still running gets a
     * memory trim: COMPLETE where Android allows it (processes nothing on screen depends on: caches and
     * graphics memory go), otherwise RUNNING_CRITICAL (the app trims its caches; nothing it shows or plays
     * stops). Prism TV releases its own memory itself. On Android 14+, where [killBackground] can't,
     * `am kill-all` first stops every idle (cached) process. (Not on older versions: there it also makes
     * Android act as if memory were short until apps are opened again.)
     */
    fun shellScript(ownPackage: String): String {
        require(ownPackage.matches(Regex("[A-Za-z0-9_.]+")))
        return (if (Build.VERSION.SDK_INT >= 34) "am kill-all >/dev/null 2>&1; " else "") +
            "ps -A -o USER,NAME | grep '^u[0-9]*_a' | while read u n; do " +
            "case \"\$n\" in $ownPackage|$ownPackage:*) continue;; esac; " +
            "am send-trim-memory \"\$n\" COMPLETE >/dev/null 2>&1 || am send-trim-memory \"\$n\" RUNNING_CRITICAL >/dev/null 2>&1; " +
            "done; true"
    }

    /** Memory available to apps now, in bytes. */
    fun available(ctx: Context): Long = runCatching {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
    }.getOrDefault(0L)
}
