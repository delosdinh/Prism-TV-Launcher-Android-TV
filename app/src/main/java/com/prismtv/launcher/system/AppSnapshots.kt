package com.prismtv.launcher.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.prismtv.launcher.data.UriArt
import java.io.File
import java.util.concurrent.Executors

/**
 * App Switcher snapshots, like tvOS: when you press Home in an app, Power & Home Button (the
 * accessibility service) takes one screenshot of what that app was showing. Android TV turns off its
 * own task snapshots, so this is the only way to get them.
 *
 * Kept small (960 × 540 JPEG) in Prism TV's private files, one per app, never sent anywhere. Deleted
 * when the app is swiped away or snapshots are turned off. A black frame (protected video, which
 * Android blanks in screenshots) isn't kept, so that app shows its artwork instead.
 */
object AppSnapshots {
    private const val Tag = "PrismSnap"
    private const val W = 960
    private const val H = 540
    const val PrefKey = "appSnapshots"

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "prism-snapshots").apply { priority = Thread.MIN_PRIORITY } }

    /** Bumped after each save or delete; the App Switcher reads it to pick up new pictures. */
    @Volatile var version = 0L
        private set

    fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun enabled(ctx: Context): Boolean =
        supported() && ctx.getSharedPreferences("prism_prefs", Context.MODE_PRIVATE).getBoolean(PrefKey, true)

    private fun dir(ctx: Context) = File(ctx.filesDir, "snapshots")
    private fun file(ctx: Context, pkg: String) = File(dir(ctx), "${pkg.replace('/', '_')}.jpg")

    /** This app's last snapshot, if there is one (the address changes with each new picture). */
    fun art(ctx: Context, pkg: String): UriArt? {
        if (!enabled(ctx)) return null
        val f = file(ctx, pkg)
        if (!f.exists()) return null
        return UriArt(Uri.fromFile(f).buildUpon().appendQueryParameter("v", f.lastModified().toString()).build().toString())
    }

    fun has(ctx: Context, pkg: String): Boolean = file(ctx, pkg).exists()

    fun delete(ctx: Context, pkg: String) {
        if (file(ctx, pkg).delete()) version++
    }

    fun clear(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
        version++
    }

    fun count(ctx: Context): Int = dir(ctx).listFiles()?.size ?: 0

    /** Apps there's no point capturing: Prism TV itself, launchers, the system bars and keyboards. */
    fun worthCapturing(ctx: Context, pkg: String?): Boolean {
        if (pkg.isNullOrBlank() || pkg == ctx.packageName) return false
        if (pkg == "android" || pkg == "com.android.systemui" || pkg.contains("inputmethod") || pkg.contains("keyboard")) return false
        if (pkg.contains("launcher", ignoreCase = true) || pkg == "com.google.android.tvlauncher" ||
            pkg == "com.google.android.apps.tv.launcherx" || pkg == "com.amazon.tv.launcher"
        ) return false
        return true
    }

    /**
     * Takes the snapshot for [pkg]; [done] runs on the main thread once the screen has been captured
     * (saving carries on in the background), so Prism TV only comes forward after that.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun capture(service: AccessibilityService, pkg: String, done: () -> Unit) {
        val ctx = service.applicationContext
        try {
            service.takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        done()
                        worker.execute { save(ctx, pkg, result) }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.i(Tag, "capture failed for $pkg: $errorCode")
                        done()
                    }
                },
            )
        } catch (t: Throwable) {
            Log.i(Tag, "capture unavailable: ${t.message}")
            done()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun save(ctx: Context, pkg: String, result: AccessibilityService.ScreenshotResult) {
        val buffer = result.hardwareBuffer
        try {
            val hw = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return
            // Hardware pictures can't be drawn in software: read it back once, then shrink it.
            val full = hw.copy(Bitmap.Config.ARGB_8888, false)
            hw.recycle()
            if (full == null) return
            val small = if (full.width == W && full.height == H) full else Bitmap.createScaledBitmap(full, W, H, true)
            if (small !== full) full.recycle()
            if (isBlank(small)) {
                // Protected video comes out black: show the app's artwork rather than a black card.
                Log.i(Tag, "$pkg: black frame (protected content), not kept")
                delete(ctx, pkg)
                small.recycle()
                return
            }
            val d = dir(ctx).apply { mkdirs() }
            val tmp = File(d, "$pkg.tmp")
            tmp.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            tmp.renameTo(file(ctx, pkg))
            small.recycle()
            version++
            Log.i(Tag, "$pkg: snapshot saved")
        } catch (t: Throwable) {
            Log.i(Tag, "$pkg: couldn't save snapshot: ${t.message}")
        } finally {
            runCatching { buffer.close() }
        }
    }

    /** Almost entirely one dark colour: a blanked (protected) frame or a blank screen. */
    private fun isBlank(b: Bitmap): Boolean {
        val step = 24
        var n = 0
        var bright = 0
        var sum = 0L
        var sumSq = 0L
        var y = step / 2
        while (y < b.height) {
            var x = step / 2
            while (x < b.width) {
                val c = b.getPixel(x, y)
                val l = (((c shr 16) and 0xFF) * 54 + ((c shr 8) and 0xFF) * 183 + (c and 0xFF) * 19) shr 8
                sum += l
                sumSq += l * l
                if (l > 40) bright++
                n++
                x += step
            }
            y += step
        }
        if (n == 0) return true
        val mean = sum.toDouble() / n
        val variance = sumSq.toDouble() / n - mean * mean
        return mean < 10 && variance < 40 && bright * 200 < n
    }
}
