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
    /** A hairline rim around the card (the glass edge), and what slides in and out. */
    private var rim: FrameLayout? = null
    private var icon: ImageView? = null
    private var appText: TextView? = null
    private var titleText: TextView? = null
    private var bodyText: TextView? = null
    private val hide = Runnable { slideOut() }

    private fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, service.resources.displayMetrics).toInt()

    /**
     * How banners look: Prism TV's Material (Liquid Glass, Clear or Frosted) and Material Colour, the
     * same as its own banners. Set by Prism TV whenever they change. Another app's picture can't be
     * bent or blurred from here, so the glass is a touch denser than in Prism TV, with the same rim.
     */
    data class Style(
        val fill: Int,
        val rimTop: Int,
        val rimBottom: Int,
        val title: Int,
        val body: Int,
        val shadow: Boolean,
    )

    /** Shows (or replaces) the banner. Main thread. */
    fun show(app: String, title: String, text: String, appIcon: Bitmap?, light: Boolean) {
        val view = root ?: build() ?: return
        val st = style ?: if (light) LightStyle else DarkStyle
        card?.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(st.fill, st.fill)).apply {
            cornerRadius = dp(24f).toFloat()
        }
        rim?.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(st.rimTop, st.rimBottom)).apply {
            cornerRadius = dp(24f).toFloat()
        }
        fun TextView.ink(color: Int) {
            setTextColor(color)
            if (st.shadow) setShadowLayer(dp(3f).toFloat(), 0f, dp(0.5f).toFloat(), 0x66000000) else setShadowLayer(0f, 0f, 0f, 0)
        }
        appText?.apply { this.text = app.uppercase(); ink(st.body) }
        titleText?.apply { this.text = title; ink(st.title) }
        bodyText?.apply {
            this.text = text
            ink(st.body)
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
        icon?.apply {
            if (appIcon != null) { setImageBitmap(appIcon); visibility = View.VISIBLE } else visibility = View.GONE
        }
        main.removeCallbacks(hide)
        val c = rim ?: return
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
        val c = rim ?: return
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
        rim = null
    }

    private fun build(): FrameLayout? {
        val ctx = service
        // Room inside the window for the card's shadow and its slide.
        val frame = FrameLayout(ctx).apply {
            setPadding(dp(16f), dp(16f), dp(16f), dp(20f))
            clipToPadding = false
            clipChildren = false
        }
        val edge = FrameLayout(ctx).apply {
            setPadding(dp(1.2f), dp(1.2f), dp(1.2f), dp(1.2f))
            elevation = dp(12f).toFloat()
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(12f), dp(16f), dp(12f))
        }
        edge.addView(box, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        val img = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0x33FFFFFF) }
        }
        box.addView(img, LinearLayout.LayoutParams(dp(42f), dp(42f)).apply { marginEnd = dp(12f) })
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
        box.addView(col, LinearLayout.LayoutParams(dp(232f), LinearLayout.LayoutParams.WRAP_CONTENT))
        frame.addView(edge, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            // Where Prism TV's own banners sit (30 dp from the right), less the window's shadow room.
            x = dp(14f)
            y = dp(24f)
            windowAnimations = 0
        }
        return try {
            wm.addView(frame, lp)
            root = frame; card = box; rim = edge; icon = img; appText = a; titleText = t; bodyText = b
            frame
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        const val PrefKey = "bannersOverApps"
        const val ShowMs = 5500L

        /** Prism TV's current banner look (null until Prism TV has drawn once). */
        @Volatile var style: Style? = null

        private val DarkStyle = Style(0xEB26272D.toInt(), 0x5CFFFFFF, 0x14FFFFFF, 0xFFFFFFFF.toInt(), 0xB3FFFFFF.toInt(), false)
        private val LightStyle = Style(0xF2F4F4F7.toInt(), 0xCCFFFFFF.toInt(), 0x33FFFFFF, 0xFF15171E.toInt(), 0xA615171E.toInt(), false)
    }
}
