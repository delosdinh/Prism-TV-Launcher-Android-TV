package com.prismtv.launcher.ui.glass

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether this TV's GPU keeps up with the Liquid Glass refraction shader (Android 13+).
 *
 * The shader samples the backdrop three times per pixel for every lens on screen. A SHIELD-class GPU
 * doesn't notice, but a Mali in a 1080p box (Ugoos AM9 Pro) spent 16 ms of GPU time per frame on the app
 * grid over a moving aerial (5 ms over a still), so every other frame was late. So Prism times its frames
 * (Android's own frame metrics: GPU time and total time against the screen's refresh interval), over an
 * aerial and not, apart. When two windows in a row
 * are clearly over budget it switches this TV to Refraction Bands, the path older Android uses (two
 * plain backdrop draws, no shader, no blur under the rim), and remembers that for this TV and Android
 * build (when it was only slow over an aerial, only while one plays; behind the app grid the blurred
 * still then stands in for blurring the video live). Lite Mode is the user's own setting and is never touched.
 *
 * `adb shell dumpsys activity com.prismtv.launcher/.MainActivity glass` prints what was measured;
 * add `reprobe` to forget the decision and measure again.
 */
object GlassPerf {
    /** False once this TV has been measured too slow for the refraction shader. Snapshot state. */
    var shaderOk by mutableStateOf(true)
        private set

    /**
     * Measured too slow with an aerial moving behind Home (Home's glass then re-renders, and the aerial
     * blurs, on every video frame). Only applies while one plays.
     */
    var aerialOk by mutableStateOf(true)
        private set

    /** An aerial is playing behind Home (set by Home's backdrop). */
    var aerialPlaying: Boolean
        get() = aerialState
        set(value) {
            aerialState = value
            aerialNow = value
        }
    private var aerialState by mutableStateOf(false)
    @Volatile private var aerialNow = false

    /**
     * Draw the cheaper glass now: Refraction Bands with no blur under the rim, and over a moving aerial
     * the blurred still behind the app grid instead of blurring the video live. Snapshot state.
     */
    val cheap: Boolean get() = !shaderOk || (aerialState && !aerialOk)

    /** Bump when the shader gets cheaper or dearer, so every TV measures again. */
    private const val PROBE = 1
    private const val PREFS = "prism_glass_perf"
    private const val WINDOW = 120
    private const val TAG = "PrismGlass"

    private var prefs: SharedPreferences? = null
    @Volatile private var detail = "not measured yet"
    private val main = Handler(Looper.getMainLooper())

    // Timing state, touched only on the metrics thread.
    private var thread: HandlerThread? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private var watchedWindow: Window? = null
    private val gpu = LongArray(WINDOW)
    private val total = LongArray(WINDOW)
    private var n = 0
    private var windowAerial = false
    private var slowWindows = 0
    private var startedAt = 0L
    private var intervalNs = 16_666_667L

    /** CI's emulators render in software: their timings say nothing, and screenshots must stay comparable. */
    private val emulator = Build.HARDWARE.let { it == "ranchu" || it == "goldfish" } || Build.FINGERPRINT.contains("generic")

    private fun buildKey() = "$PROBE|${Build.FINGERPRINT}"

    fun init(context: Context) {
        if (!SupportsLensShader || prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = sp
        if (sp.getString("build", null) == buildKey()) {
            shaderOk = !sp.getBoolean("slow", false)
            aerialOk = !sp.getBoolean("slowAerial", false)
            if (!shaderOk || !aerialOk) {
                detail = sp.getString("detail", null) ?: "too slow for the shader"
                Log.i(TAG, "Liquid Glass: cheaper glass on this TV${if (shaderOk) " over aerials" else ""} ($detail)")
            }
        }
    }

    /** Starts timing [window]'s frames (no-op once decided, or before Android 13). */
    fun watch(window: Window) {
        if (!SupportsLensShader || !shaderOk || listener != null || emulator) return
        if (Build.VERSION.SDK_INT >= 33) watchFrames(window)
    }

    fun unwatch() {
        val l = listener ?: return
        runCatching { watchedWindow?.removeOnFrameMetricsAvailableListener(l) }
        listener = null
        watchedWindow = null
        thread?.quitSafely()
        thread = null
    }

    @RequiresApi(33)
    private fun watchFrames(window: Window) {
        @Suppress("DEPRECATION")
        val hz = window.windowManager.defaultDisplay.refreshRate.takeIf { it in 20f..240f } ?: 60f
        intervalNs = (1_000_000_000.0 / hz).toLong()
        val t = HandlerThread("PrismGlassPerf").also { it.start() }
        val h = Handler(t.looper)
        h.post { n = 0; slowWindows = 0; startedAt = System.nanoTime() }
        val l = Window.OnFrameMetricsAvailableListener { _, metrics, _ -> onFrame(metrics) }
        runCatching { window.addOnFrameMetricsAvailableListener(l, h) }.onFailure {
            t.quitSafely()
            return
        }
        thread = t
        listener = l
        watchedWindow = window
    }

    @RequiresApi(33)
    private fun onFrame(m: FrameMetrics) {
        val now = System.nanoTime()
        // Let start-up, first shader compiles and returning from an app settle first.
        if (now - startedAt < 6_000_000_000L) return
        if (m.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return
        // Every frame counts: Home draws none while nothing moves, and the glass is redrawn (from its
        // recorded lenses) in every frame, re-recorded or not. Frames over an aerial are timed apart.
        val aerial = aerialNow
        if (aerial && !aerialOk) return
        if (aerial != windowAerial) {
            windowAerial = aerial
            n = 0
            slowWindows = 0
        }
        val tot = m.getMetric(FrameMetrics.TOTAL_DURATION)
        val g = m.getMetric(FrameMetrics.GPU_DURATION)
        if (tot <= 0 || tot > 500_000_000L || g < 0) return
        gpu[n] = g
        total[n] = tot
        if (++n < WINDOW) return
        n = 0
        val gpuMed = gpu.sorted()[WINDOW / 2]
        val totMed = total.sorted()[WINDOW / 2]
        val budget = intervalNs
        // GPU busy for most of the frame interval, or frames twice the interval with the GPU a big part of it.
        val slow = gpuMed > budget * 3 / 4 || (totMed > budget * 2 && gpuMed > budget / 2)
        val line = "GPU p50 ${ms(gpuMed)} ms, frame p50 ${ms(totMed)} ms, budget ${ms(budget)} ms${if (aerial) ", over an aerial" else ""}"
        detail = "measured $line"
        Log.i(TAG, "Liquid Glass frames: $line${if (slow) " (over budget)" else ""}")
        slowWindows = if (slow) slowWindows + 1 else 0
        if (slowWindows >= 2) {
            slowWindows = 0
            val why = "switched to Refraction Bands${if (aerial) " while an aerial plays" else ""}: $line"
            detail = why
            Log.i(TAG, "Liquid Glass: $why")
            prefs?.edit()?.putString("build", buildKey())?.putBoolean(if (aerial) "slowAerial" else "slow", true)
                ?.putString("detail", line)?.apply()
            main.post {
                if (aerial) aerialOk = false
                else {
                    shaderOk = false
                    unwatch()
                }
            }
        }
    }

    private fun ms(ns: Long) = String.format(java.util.Locale.ROOT, "%.1f", ns / 1_000_000.0)

    /** For `dumpsys … glass`. */
    fun report(): String = when {
        !SupportsLensShader -> "Liquid Glass: Refraction Bands (Android ${Build.VERSION.RELEASE} has no runtime shaders)"
        !shaderOk -> "Liquid Glass: Refraction Bands for this GPU ($detail)"
        !aerialOk -> "Liquid Glass: Refraction Shader, Refraction Bands while an aerial plays ($detail)"
        else -> "Liquid Glass: Refraction Shader ($detail${if (listener != null) ", still timing" else ""})"
    }

    /** For `dumpsys … glass reprobe`: forget the decision and time the shader again. */
    fun reprobe(window: Window) {
        prefs?.edit()?.clear()?.apply()
        detail = "not measured yet"
        unwatch()
        shaderOk = true
        aerialOk = true
        watch(window)
    }
}
