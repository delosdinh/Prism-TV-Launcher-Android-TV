package com.prismtv.launcher.system

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.util.IdentityHashMap

/**
 * Adds up the bitmaps Prism holds, by kind, for `dumpsys activity com.prismtv.launcher/.MainActivity`
 * (printed under "Prism memory"). Hardware bitmaps live in graphics memory; the rest in the native heap.
 * Each bitmap counts once in a tally, however many places share it.
 */
class BitmapTally(private val name: String) {
    private val seen = IdentityHashMap<Bitmap, Unit>()
    private var software = 0L
    private var hardware = 0L

    fun add(image: ImageBitmap?) {
        if (image != null) add(runCatching { image.asAndroidBitmap() }.getOrNull())
    }

    fun add(b: Bitmap?) {
        if (b == null || b.isRecycled || seen.put(b, Unit) != null) return
        val bytes = runCatching { b.allocationByteCount.toLong() }.getOrDefault(b.width.toLong() * b.height * 4)
        if (android.os.Build.VERSION.SDK_INT >= 26 && b.config == Bitmap.Config.HARDWARE) hardware += bytes else software += bytes
    }

    override fun toString(): String = "$name: ${seen.size} bitmaps, native ${mb(software)}, graphics ${mb(hardware)}"

    companion object {
        fun mb(bytes: Long): String = "%.1f MB".format(bytes / 1_048_576.0)
    }
}
