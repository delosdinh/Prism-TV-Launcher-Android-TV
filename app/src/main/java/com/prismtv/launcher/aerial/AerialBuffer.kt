package com.prismtv.launcher.aerial

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * How much of an aerial ExoPlayer reads ahead, and giving that memory back when the player goes.
 *
 * ExoPlayer's default for video is 50 seconds or 125 MB, whichever comes first, and an Apple aerial
 * reaches the 125 MB first. A muted aerial only needs to ride out a hiccup, so these keep 10 to 30 seconds
 * within a byte budget (about 10 s of a 1080p aerial, more of a smaller one).
 */
internal object AerialBuffer {
    private const val MB = 1024 * 1024

    /** The Home Screen's wallpaper (1080p SDR). */
    fun wallpaper(): LoadControl = control(24 * MB)

    /** The screensaver, which may play 4K and has the screen to itself. */
    fun screensaver(): LoadControl = control(48 * MB)

    private fun control(bytes: Int): LoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(10_000, 30_000, 2_500, 5_000)
        .setTargetBufferBytes(bytes)
        .setPrioritizeTimeOverSizeThresholds(false)
        .build()

    /** Debug A/B only (`--ez reclaim false`): leave a released player's memory to Android's own timing. */
    @Volatile var reclaim = true

    private val collector by lazy {
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "PrismReclaim").apply { isDaemon = true } }
    }
    private var queued: ScheduledFuture<*>? = null

    /**
     * Call after releasing an aerial player (or the screensaver's stills). The read-ahead lives in Java
     * arrays (64 KB each), which become garbage on release, but Android only collects garbage once the
     * app allocates enough again, and an idle Home Screen barely does. On the SHIELD the screensaver's
     * 48 MB stayed in Prism after it ended (and before the cap, about 139 MB). So collect a couple of
     * seconds later, once Home has finished appearing, on a background thread.
     */
    @Synchronized fun released() {
        if (!reclaim) return
        queued?.cancel(false)
        queued = collector.schedule({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            Runtime.getRuntime().gc()
        }, 2, TimeUnit.SECONDS)
    }
}
