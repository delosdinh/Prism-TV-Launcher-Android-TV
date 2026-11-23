package com.prismtv.launcher.system

import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Finds what grows Prism's native memory on a real TV (`logcat -d -s PrismMem`). Once a minute, when
 * anything happened, it logs native memory in use and how many times each kind of event ran since the
 * last line; each screen opened also logs what its first frames took. One short line at a time, from a
 * background thread.
 */
object MemTrace {
    private const val Tag = "PrismMem"
    private const val MinuteMs = 60_000L

    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val handler by lazy { Handler(HandlerThread("PrismMem").apply { start() }.looper) }
    @Volatile private var last = -1L
    @Volatile private var started = false

    fun count(event: String) {
        counts.getOrPut(event) { AtomicInteger() }.incrementAndGet()
    }

    fun start() {
        if (started) return
        started = true
        handler.post {
            last = native()
            Log.i(Tag, "start: native ${mb(last)}, Java ${mb(java())}")
        }
        handler.postDelayed(tick, MinuteMs)
    }

    /** A screen (Control Centre, Settings, the App Switcher…) opened: its first frames' cost. */
    fun opened(name: String) {
        val before = native()
        handler.postDelayed({ Log.i(Tag, "$name opened: native ${mb(before)} → ${mb(native())}") }, 2_500)
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = native()
            val events = counts.entries.mapNotNull { (k, v) -> v.getAndSet(0).takeIf { it > 0 }?.let { "$k $it" } }
            val delta = now - last
            if (events.isNotEmpty() || kotlin.math.abs(delta) >= 512 * 1024) {
                val sign = if (delta >= 0) "+" else "−"
                Log.i(Tag, "native ${mb(now)} ($sign${mb(kotlin.math.abs(delta))}), Java ${mb(java())}" +
                    if (events.isEmpty()) "" else " | " + events.sorted().joinToString(", "))
            }
            last = now
            handler.postDelayed(this, MinuteMs)
        }
    }

    private fun native(): Long = Debug.getNativeHeapAllocatedSize()
    private fun java(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
    private fun mb(bytes: Long): String = "%.1f MB".format(bytes / 1_048_576.0)
}
