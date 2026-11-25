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
/**
 * `dumpsys activity com.prismtv.launcher/.MainActivity hwtest`: what one full-screen picture costs the
 * native heap at each step of moving it to the graphics chip (as backdrops, cards and snapshots are).
 * On most TVs the graphics copy lives only in graphics memory; if the native heap keeps its size too,
 * the TV's driver holds a second copy.
 */
object HardwareProbe {
    fun run(): String {
        if (android.os.Build.VERSION.SDK_INT < 28) return "hardware test: Prism doesn't use graphics-chip pictures before Android 9"
        fun native() = android.os.Debug.getNativeHeapAllocatedSize()
        val mb = { b: Long -> (if (b >= 0) "+" else "−") + BitmapTally.mb(kotlin.math.abs(b)) }
        val n0 = native()
        val sw = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2A4D7A.toInt()) }
        val n1 = native()
        val hw = runCatching { sw.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
        val n2 = native()
        sw.recycle()
        val n3 = native()
        Thread.sleep(300)
        val n4 = native()
        hw?.recycle()
        Thread.sleep(300)
        val n5 = native()
        return "hardware test, one 1920×1080 picture (7.9 MB): made ${mb(n1 - n0)}, copied to the graphics chip ${mb(n2 - n1)}" +
            (if (hw == null) " (failed)" else "") + ", original freed ${mb(n3 - n2)}, 0.3 s later ${mb(n4 - n3)}, " +
            "graphics copy freed ${mb(n5 - n4)}; left over ${mb(n5 - n0)} (native heap still holding ${mb(n4 - n0)} while only the graphics copy existed)"
    }
}

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
