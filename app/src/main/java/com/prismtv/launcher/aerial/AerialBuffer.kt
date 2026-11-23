package com.prismtv.launcher.aerial

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * How much of an aerial ExoPlayer reads ahead. Its default for video is 50 seconds or 125 MB, whichever
 * comes first, and an Apple aerial reaches the 125 MB first: on the SHIELD that was 139 MB of Prism's
 * memory just for the Home wallpaper. A looping, muted background clip only needs to ride out a hiccup,
 * so these keep 10 to 30 seconds within a byte budget (about 10 s of a 1080p aerial, more of a smaller one).
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
}
