package com.prismtv.launcher.aerial

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.core.content.res.ResourcesCompat
import com.prismtv.launcher.BuildConfig
import com.prismtv.launcher.R
import com.prismtv.launcher.data.LauncherPrefs
import com.prismtv.launcher.data.ScreensaverClock
import com.prismtv.launcher.data.Weather
import com.prismtv.launcher.data.WeatherKind
import com.prismtv.launcher.data.WeatherRepository
import com.prismtv.launcher.data.conditions
import com.prismtv.launcher.data.highLow
import com.prismtv.launcher.data.kind
import com.prismtv.launcher.data.label
import com.prismtv.launcher.system.MediaWatcher
import com.prismtv.launcher.system.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

private const val Wrap = android.view.ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * What the screensaver shows over the aerials (Settings › Screensaver): the clock (Small in the corner,
 * Large at the top left with the date, or Centred under the date like a lock screen), the weather beside
 * it, and a Now Playing card while music plays.
 *
 * Plain views that only redraw when something changes (the time once a minute, the weather every half
 * hour, Now Playing when the song changes), so the video underneath plays exactly as it does without
 * them; nothing here animates on its own. Once a minute the layer moves a step (a couple of dp, within a
 * small box), so an OLED TV never keeps the same pixels lit all night.
 */
class AerialInfoLayer(context: Context, private val prefs: LauncherPrefs) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private val weatherRepo = WeatherRepository(context)

    private val clockStyle = prefs.screensaverClock
    private val showWeather = prefs.screensaverWeather
    private val showNowPlaying = prefs.screensaverNowPlaying

    private val semibold = ResourcesCompat.getFont(context, R.font.inter_semibold)
    private val medium = ResourcesCompat.getFont(context, R.font.inter_medium)

    // ------------------------------------------------------------------------------------ the clock

    private val time: TextClock? = if (clockStyle == ScreensaverClock.Off) null else TextClock(context).apply {
        setTextColor(Color.WHITE)
        includeFontPadding = false
        if (clockStyle == ScreensaverClock.Small) {
            // As before: the TV's own time format (with AM/PM where it uses one).
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = medium
            setShadowLayer(dp(4f), 0f, dp(1f), 0x66000000)
            alpha = 0.92f
        } else {
            // Big numerals, no AM/PM (like a lock screen). Sized in dp so a large system font can't overflow.
            format12Hour = "h:mm"
            format24Hour = "H:mm"
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, if (clockStyle == ScreensaverClock.Centred) 104f else 88f)
            typeface = semibold
            letterSpacing = -0.03f
            setShadowLayer(dp(12f), 0f, dp(2f), 0x55000000)
        }
        // CI screenshots: the same fixed time as the Home Screen's ("9:41").
        if (BuildConfig.DEBUG) demoClock?.let { t ->
            format12Hour = "'$t'"
            format24Hour = "'$t'"
        }
    }

    /** "Thursday, 27 November", in the TV's language and order. */
    private val date: TextClock? = if (clockStyle == ScreensaverClock.Large || clockStyle == ScreensaverClock.Centred) {
        TextClock(context).apply {
            val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
            format12Hour = pattern
            format24Hour = pattern
            secondaryText(this, 21f)
        }
    } else null

    // ------------------------------------------------------------------------------------ weather

    private val weatherIcon = GlyphView(context)
    private val temperature = TextView(context).apply { primaryText(this, if (clockStyle == ScreensaverClock.Small || clockStyle == ScreensaverClock.Off) 22f else 21f) }
    /** Large clock only: "Partly Cloudy · H 15°  L 8°". */
    private val forecast = TextView(context).apply {
        secondaryText(this, 19f)
        visibility = GONE
    }
    private val weatherRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        val icon = dp(if (clockStyle == ScreensaverClock.Large) 26f else 22f).toInt()
        addView(weatherIcon, LinearLayout.LayoutParams(icon, icon).apply { rightMargin = dp(6f).toInt() })
        addView(temperature)
        addView(forecast, LinearLayout.LayoutParams(Wrap, Wrap).apply { leftMargin = dp(12f).toInt() })
    }

    // ------------------------------------------------------------------------------------ Now Playing

    private val art = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(10f))
            }
        }
        clipToOutline = true
    }
    private val note = GlyphView(context).apply { visibility = GONE }
    private val songTitle = TextView(context).apply { primaryText(this, 18f); oneLine(this) }
    private val songArtist = TextView(context).apply { secondaryText(this, 15f); oneLine(this) }
    private val songApp = TextView(context).apply {
        secondaryText(this, 13f)
        oneLine(this)
        alpha = 0.72f
    }
    private val nowCard = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = dp(12f).toInt()
        setPadding(pad, pad, dp(20f).toInt(), pad)
        background = GradientDrawable().apply {
            cornerRadius = dp(22f)
            setColor(0x59000000)
            setStroke(maxOf(1, dp(0.5f).toInt()), 0x26FFFFFF)
        }
        val size = dp(64f).toInt()
        addView(FrameLayout(context).apply {
            addView(art, LayoutParams(size, size))
            addView(note, LayoutParams(size, size))
        }, LinearLayout.LayoutParams(size, size))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(songTitle)
            addView(songArtist, LinearLayout.LayoutParams(Wrap, Wrap).apply { topMargin = dp(1f).toInt() })
            addView(songApp, LinearLayout.LayoutParams(Wrap, Wrap).apply { topMargin = dp(3f).toInt() })
        }, LinearLayout.LayoutParams(Wrap, Wrap).apply { leftMargin = dp(14f).toInt() })
        alpha = 0f
        visibility = GONE
    }
    private var shownSong: NowPlaying? = null

    init {
        clipChildren = false
        arrange()
        if (showNowPlaying) {
            addView(
                nowCard,
                LayoutParams(Wrap, Wrap, Gravity.BOTTOM or Gravity.END).apply {
                    rightMargin = dp(56f).toInt()
                    bottomMargin = dp(48f).toInt()
                },
            )
        }
    }

    /** Puts the clock, date and weather where [clockStyle] has them. */
    private fun arrange() {
        when (clockStyle) {
            // The time at the top right, with the weather just before it on the same line.
            ScreensaverClock.Off, ScreensaverClock.Small -> {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                if (showWeather) row.addView(weatherRow)
                time?.let {
                    row.addView(it, LinearLayout.LayoutParams(Wrap, Wrap).apply {
                        leftMargin = if (showWeather) dp(18f).toInt() else 0
                    })
                }
                addView(row, LayoutParams(Wrap, Wrap, Gravity.TOP or Gravity.END).apply {
                    rightMargin = dp(56f).toInt()
                    topMargin = dp(40f).toInt()
                })
            }
            // Big time at the top left, then the date, then the weather in words.
            ScreensaverClock.Large -> {
                val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                time?.let { column.addView(it) }
                date?.let { column.addView(it, LinearLayout.LayoutParams(Wrap, Wrap).apply { topMargin = dp(4f).toInt(); leftMargin = dp(4f).toInt() }) }
                if (showWeather) {
                    forecast.visibility = VISIBLE
                    column.addView(weatherRow, LinearLayout.LayoutParams(Wrap, Wrap).apply { topMargin = dp(12f).toInt(); leftMargin = dp(2f).toInt() })
                }
                addView(column, LayoutParams(Wrap, Wrap, Gravity.TOP or Gravity.START).apply {
                    leftMargin = dp(60f).toInt()
                    topMargin = dp(36f).toInt()
                })
            }
            // The date (and weather) on one line, the big time under it, both centred.
            ScreensaverClock.Centred -> {
                val column = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                val top = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                date?.let { top.addView(it) }
                if (showWeather) top.addView(weatherRow, LinearLayout.LayoutParams(Wrap, Wrap).apply { leftMargin = dp(16f).toInt() })
                column.addView(top)
                time?.let { column.addView(it, LinearLayout.LayoutParams(Wrap, Wrap).apply { topMargin = dp(2f).toInt() }) }
                addView(column, LayoutParams(Wrap, Wrap, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                    topMargin = dp(32f).toInt()
                })
            }
        }
    }

    /** For the screensaver's log: what's shown over the aerials. */
    val summary: String
        get() = "clock ${clockStyle.name}, weather ${if (showWeather) "on" else "off"}, now playing " +
            when {
                !showNowPlaying -> "off"
                nowCard.visibility == VISIBLE -> "shown"
                else -> "on (nothing playing)"
            }

    fun start() {
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        if (showWeather) s.launch { followWeather() }
        if (showNowPlaying) {
            MediaWatcher.start(context)
            s.launch { MediaWatcher.nowPlaying.collect { showSong(it) } }
        }
        if (time != null || showWeather || showNowPlaying) scheduleStep()
    }

    fun stop() {
        scope?.cancel()
        scope = null
        handler.removeCallbacksAndMessages(null)
        nowCard.animate().cancel()
    }

    // ------------------------------------------------------------------------------------ weather

    /** The last weather straight away, then fresh every half hour (Open-Meteo, as on the Home Screen). */
    private suspend fun followWeather() {
        if (BuildConfig.DEBUG) demoWeather?.let { showWeather(it); return }
        showWeather(weatherRepo.cached())
        while (true) {
            val age = System.currentTimeMillis() - weatherRepo.fetchedAt
            if (age in 0 until WeatherMs && weatherRepo.cached() != null) {
                delay(WeatherMs - age)
                continue
            }
            // Offline (or Open-Meteo didn't answer): keep the last weather and try again in a few minutes.
            val fresh = weatherRepo.refresh()
            if (fresh != null) showWeather(fresh)
            delay(if (fresh != null) WeatherMs else RetryMs)
        }
    }

    private fun showWeather(w: Weather?) {
        if (w == null) {
            weatherRow.visibility = GONE
            return
        }
        weatherIcon.set(weatherLayers(w))
        temperature.text = w.label(prefs.temperatureUnit)
        forecast.text = listOfNotNull(w.conditions(), w.highLow(prefs.temperatureUnit)).joinToString("  ·  ")
        weatherRow.visibility = VISIBLE
    }

    // ------------------------------------------------------------------------------------ Now Playing

    /** The card shows while something plays, and fades away when it's paused or stops. */
    private fun showSong(np: NowPlaying?) {
        val playing = np?.takeIf { it.playing }
        if (playing == null) {
            if (nowCard.visibility == VISIBLE) {
                nowCard.animate().cancel()
                nowCard.animate().alpha(0f).setStartDelay(0).setDuration(600).withEndAction { nowCard.visibility = GONE }.start()
            }
            shownSong = null
            return
        }
        val before = shownSong
        if (before == null || before.title != playing.title || before.subtitle != playing.subtitle || before.art !== playing.art || before.pkg != playing.pkg) {
            songTitle.text = playing.title
            songArtist.text = playing.subtitle
            songApp.text = playing.appLabel
            songApp.visibility = if (playing.appLabel.isBlank() || playing.appLabel == playing.subtitle) GONE else VISIBLE
            val picture = playing.art ?: playing.appIcon
            if (picture != null) {
                art.setImageBitmap(picture.asAndroidBitmap())
                art.visibility = VISIBLE
                note.visibility = GONE
            } else {
                art.setImageDrawable(null)
                art.visibility = GONE
                note.set(listOf(Layer(glyph(Icons.Rounded.MusicNote), Color.WHITE, 0.56f, Align.Centre)))
                note.background = GradientDrawable().apply {
                    cornerRadius = dp(10f)
                    setColor(0x33FFFFFF)
                }
                note.visibility = VISIBLE
            }
        }
        shownSong = playing
        if (nowCard.visibility != VISIBLE || nowCard.alpha < 1f) {
            nowCard.visibility = VISIBLE
            nowCard.animate().cancel()
            nowCard.animate().alpha(1f).setStartDelay(0).setDuration(700).start()
        }
    }

    // ------------------------------------------------------------------------------------ OLED care

    private var stepX = 0
    private var stepY = 0

    /**
     * Once a minute, just after the time changes (the clock redraws then anyway), the layer moves a step
     * of 2 dp, wandering within ±12 × ±8 dp: too slow and small to notice, enough that no pixel stays lit.
     */
    private val step = object : Runnable {
        override fun run() {
            stepX = (stepX + Random.nextInt(-1, 2)).coerceIn(-6, 6)
            stepY = (stepY + Random.nextInt(-1, 2)).coerceIn(-4, 4)
            translationX = dp(2f * stepX)
            translationY = dp(2f * stepY)
            scheduleStep()
        }
    }

    private fun scheduleStep() {
        val now = System.currentTimeMillis()
        handler.postDelayed(step, 60_000L - now % 60_000L + 150L)
    }

    // ------------------------------------------------------------------------------------ helpers

    private fun primaryText(v: TextView, sp: Float) {
        v.setTextColor(Color.WHITE)
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        v.typeface = semibold
        v.includeFontPadding = false
        v.setShadowLayer(dp(5f), 0f, dp(1f), 0x66000000)
    }

    private fun secondaryText(v: TextView, sp: Float) {
        v.setTextColor(0xEBFFFFFF.toInt())
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        v.typeface = medium
        v.includeFontPadding = false
        v.setShadowLayer(dp(5f), 0f, dp(1f), 0x66000000)
    }

    private fun oneLine(v: TextView) {
        v.setSingleLine(true)
        v.ellipsize = TextUtils.TruncateAt.END
        v.maxWidth = dp(320f).toInt()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    /** Where a symbol's layer sits in its square. */
    enum class Align { Centre, TopEnd, BottomStart }

    /** One shape of a symbol: [scale] of the square, in [color]. */
    class Layer(val path: Path, val color: Int, val scale: Float, val align: Align)

    /**
     * A symbol drawn from Material's icon shapes, straight onto the canvas: no Compose in the screensaver,
     * and nothing to redraw until the symbol changes.
     */
    class GlyphView(context: Context) : View(context) {
        private var layers: List<Layer> = emptyList()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun set(next: List<Layer>) {
            layers = next
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val side = minOf(width, height).toFloat()
            if (side <= 0f) return
            for (l in layers) {
                val size = side * l.scale
                val (x, y) = when (l.align) {
                    Align.Centre -> (side - size) / 2f to (side - size) / 2f
                    Align.TopEnd -> side - size to 0f
                    Align.BottomStart -> 0f to side - size
                }
                val k = size / 24f
                canvas.save()
                canvas.translate(x, y)
                canvas.scale(k, k)
                paint.color = l.color
                // The same soft shadow as the text, whatever the symbol's size.
                paint.setShadowLayer(4f * resources.displayMetrics.density / k, 0f, resources.displayMetrics.density / k, 0x55000000)
                canvas.drawPath(l.path, paint)
                canvas.restore()
            }
        }
    }

    companion object {
        /** How often the screensaver looks for new weather (Open-Meteo updates about every 15 minutes). */
        private const val WeatherMs = 30L * 60 * 1000
        private const val RetryMs = 5L * 60 * 1000

        /** Debug builds (CI screenshots): fixed weather instead of asking Open-Meteo. */
        @Volatile
        var demoWeather: Weather? = null

        /** Debug builds (CI screenshots): a fixed time ("9:41") instead of the real one. */
        @Volatile
        var demoClock: String? = null

        private val glyphs = java.util.IdentityHashMap<ImageVector, Path>()

        /** [icon]'s shapes as one Android path, in its 24 × 24 viewport (made once per icon). */
        fun glyph(icon: ImageVector): Path = glyphs.getOrPut(icon) {
            val out = Path()
            fun walk(group: VectorGroup) {
                for (node in group) when (node) {
                    is VectorPath -> {
                        val p = PathParser().addPathNodes(node.pathData).toPath().asAndroidPath()
                        if (node.pathFillType == PathFillType.EvenOdd) p.fillType = Path.FillType.EVEN_ODD
                        out.addPath(p)
                    }
                    is VectorGroup -> walk(node)
                }
            }
            walk(icon.root)
            out
        }

        /** The time pill's symbols and colours (HomeScreen's WeatherGlyph): yellow sun, white clouds, blue rain. */
        fun weatherLayers(w: Weather): List<Layer> {
            val sun = 0xFFFFD60A.toInt()
            val moon = 0xFFE5E5EA.toInt()
            fun one(icon: ImageVector, color: Long) = listOf(Layer(glyph(icon), color.toInt(), 1f, Align.Centre))
            return when (w.kind()) {
                WeatherKind.Clear -> if (w.isDay) listOf(Layer(glyph(Icons.Rounded.WbSunny), sun, 1f, Align.Centre)) else listOf(Layer(glyph(Icons.Rounded.NightsStay), moon, 1f, Align.Centre))
                // The sun peeking out from behind the cloud.
                WeatherKind.PartlyCloudy -> if (w.isDay) listOf(
                    Layer(glyph(Icons.Rounded.WbSunny), sun, 0.72f, Align.TopEnd),
                    Layer(glyph(Icons.Rounded.WbCloudy), 0xFFF2F2F7.toInt(), 0.8f, Align.BottomStart),
                ) else listOf(Layer(glyph(Icons.Rounded.NightsStay), moon, 1f, Align.Centre))
                WeatherKind.Cloudy -> one(Icons.Rounded.Cloud, 0xFFE5E5EA)
                WeatherKind.Fog -> one(Icons.Rounded.Dehaze, 0xFFD1D1D6)
                WeatherKind.Drizzle -> one(Icons.Rounded.Grain, 0xFF64D2FF)
                WeatherKind.Rain -> one(Icons.Rounded.Umbrella, 0xFF64D2FF)
                WeatherKind.Snow -> one(Icons.Rounded.AcUnit, 0xFFE5F6FF)
                WeatherKind.Storm -> one(Icons.Rounded.FlashOn, 0xFFFFD60A)
            }
        }
    }
}
