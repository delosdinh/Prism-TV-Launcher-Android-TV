package com.prismtv.launcher.system

import android.content.Context
import com.prismtv.launcher.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash on the box (Settings › About › Last Problem), so a launcher that "resets" can be
 * diagnosed without a computer. Android's own handler still runs afterwards, exactly as before.
 */
object CrashLog {
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val stamp = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date())
                file(app).writeText(
                    "$stamp\nPrism TV ${BuildConfig.VERSION_NAME} · ${thread.name}\n" +
                        error.stackTraceToString().take(8000)
                )
            }
            runCatching { noteCrash(app, System.currentTimeMillis()) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")
    private fun timesFile(context: Context) = File(context.filesDir, "crash_times.txt")

    /** Crashes this close together (3 within 2 minutes) mean Prism TV is crashing as fast as Android reopens it. */
    private const val LoopCrashes = 3
    private const val LoopWindowMs = 2 * 60_000L

    private fun crashTimes(context: Context): List<Long> =
        runCatching { timesFile(context).takeIf { it.exists() }?.readLines()?.mapNotNull { it.trim().toLongOrNull() } }
            .getOrNull() ?: emptyList()

    private fun noteCrash(context: Context, now: Long) {
        val times = (crashTimes(context) + now).takeLast(LoopCrashes)
        timesFile(context).writeText(times.joinToString("\n"))
    }

    /** True when the last [LoopCrashes] crashes all happened within [LoopWindowMs] of [now]. */
    internal fun isLoop(times: List<Long>, now: Long): Boolean =
        times.size >= LoopCrashes && times.takeLast(LoopCrashes).all { now - it in 0..LoopWindowMs }

    /** Prism TV keeps crashing on start: show the rescue screen rather than crash again. */
    fun isCrashLooping(context: Context): Boolean = isLoop(crashTimes(context), System.currentTimeMillis())

    /** Forget the recent crashes (Try Again on the rescue screen), so the next start is a normal one. */
    fun clearLoop(context: Context) {
        runCatching { timesFile(context).delete() }
    }

    /** When it happened (first line), or null. */
    fun lastTime(context: Context): String? = runCatching { file(context).takeIf { it.exists() }?.useLines { it.firstOrNull() } }.getOrNull()

    /** A short, readable summary: the error and the first few Prism frames. */
    fun summary(context: Context): String? = runCatching {
        val lines = file(context).takeIf { it.exists() }?.readLines() ?: return null
        val head = lines.drop(1).take(2)
        val frames = lines.drop(3).filter { it.contains("com.prismtv") }.take(4).map { it.trim().removePrefix("at ") }
        (head + frames).joinToString("\n")
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
