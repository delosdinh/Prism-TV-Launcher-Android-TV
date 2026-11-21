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
            previous?.uncaughtException(thread, error)
        }
    }

    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

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
