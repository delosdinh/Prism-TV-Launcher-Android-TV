package com.prismtv.launcher.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Notification banners while you're watching (Settings › Notifications & Now Playing › Banners Over
 * Apps): a tvOS-style card slides in at the top right over whatever's playing, then slides away after
 * a few seconds. It never takes focus or the remote. Shown by Power & Home Button as an accessibility
 * overlay, so no "draw over other apps" permission is needed.
 */
class BannerOverlay(private val service: AccessibilityService) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var card: LinearLayout? = null
    private var icon: ImageView? = null
    private var appText: TextView? = null
    private var titleText: TextView? = null
    private var bodyText: TextView? = null
    private val hide = Runnable { slideOut() }

    private fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, service.resources.displayMetrics).toInt()

    /** Shows (or replaces) the banner. Main thread. */
    fun show(app: String, title: String, text: String, appIcon: Bitmap?, light: Boolean) {
        val view = root ?: build() ?: return
        val ink = if (light) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
        val soft = if (light) 0x991C1C1E.toInt() else 0x99FFFFFF.toInt()
        (card?.background as? GradientDrawable)?.setColor(if (light) 0xF2F2F2F7.toInt() else 0xE6202024.toInt())
        appText?.apply { this.text = app.uppercase(); setTextColor(soft) }
        titleText?.apply { this.text = title; setTextColor(ink) }
        bodyText?.apply {
            this.text = text
            setTextColor(soft)
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
        icon?.apply {
            if (appIcon != null) { setImageBitmap(appIcon); visibility = View.VISIBLE } else visibility = View.GONE
        }
        main.removeCallbacks(hide)
        val c = card ?: return
        if (view.visibility != View.VISIBLE || c.alpha < 0.99f) {
            view.visibility = View.VISIBLE
            c.animate().cancel()
            c.translationX = dp(60f).toFloat()
            c.alpha = 0f
            c.animate().translationX(0f).alpha(1f).setDuration(380).setInterpolator(DecelerateInterpolator(2f)).start()
        } else {
            // Already up: a quick bump, like a new banner landing on the old one.
            c.animate().cancel()
            c.scaleX = 0.96f; c.scaleY = 0.96f
            c.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
        }
        main.postDelayed(hide, ShowMs)
    }

    private fun slideOut() {
        val c = card ?: return
        c.animate().cancel()
        c.animate().translationX(dp(60f).toFloat()).alpha(0f).setDuration(300).setInterpolator(AccelerateInterpolator(1.6f))
            .withEndAction { root?.visibility = View.GONE }
            .start()
    }

    fun remove() {
        main.removeCallbacks(hide)
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        card = null
    }

    private fun build(): FrameLayout? {
        val ctx = service
        // Room inside the window for the card's shadow and its slide.
        val frame = FrameLayout(ctx).apply {
            setPadding(dp(16f), dp(16f), dp(16f), dp(20f))
            clipToPadding = false
            clipChildren = false
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(12f), dp(18f), dp(12f))
            background = GradientDrawable().apply { cornerRadius = dp(18f).toFloat(); setColor(0xE6202024.toInt()) }
            elevation = dp(10f).toFloat()
        }
        val img = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = GradientDrawable().apply { cornerRadius = dp(8f).toFloat(); setColor(0x33FFFFFF) }
        }
        box.addView(img, LinearLayout.LayoutParams(dp(34f), dp(34f)).apply { marginEnd = dp(12f) })
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val a = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            letterSpacing = 0.08f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val t = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val b = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        col.addView(a)
        col.addView(t)
        col.addView(b)
        box.addView(col, LinearLayout.LayoutParams(dp(250f), LinearLayout.LayoutParams.WRAP_CONTENT))
        frame.addView(box, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12f)
            y = dp(8f)
            windowAnimations = 0
        }
        return try {
            wm.addView(frame, lp)
            root = frame; card = box; icon = img; appText = a; titleText = t; bodyText = b
            frame
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        const val PrefKey = "bannersOverApps"
        const val ShowMs = 5500L
    }
}
