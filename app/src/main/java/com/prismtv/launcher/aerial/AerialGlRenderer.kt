package com.prismtv.launcher.aerial

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The Home aerial on Android 11 and older (SHIELD), where there's no RenderEffect to blur a video live:
 * ExoPlayer decodes into [Surface] given to [onInput], and on its own thread each frame is drawn into the
 * TextureView's [output] — sharp over the Top Shelf, or mixed with a blurred copy behind the app grid, so
 * the aerial keeps moving there instead of turning into a still.
 *
 * The blurred copy is made small, as the stills' are: the frame is box-filtered down to 1/4 and then 1/8
 * of the screen (135 rows), blurred there with a two-pass Gaussian (sigma 3.2 rows: the same strength as
 * Android 12's 22 dp RenderEffect on the canvas), and scaled back up with linear filtering. It's only
 * remade when a new video frame arrives while the grid is showing; the sharp frame is drawn straight.
 * Moving between the two cross-fades, as the still's sharp and blurred copies do.
 */
class AerialGlRenderer(
    private val output: SurfaceTexture,
    width: Int,
    height: Int,
    private val onInput: (Surface) -> Unit,
    private val onFirstFrame: () -> Unit,
    private val onFailed: () -> Unit,
) : SurfaceTexture.OnFrameAvailableListener {
    private val thread = HandlerThread("PrismAerialGl").also { it.start() }
    private val handler = Handler(thread.looper)

    @Volatile private var blur = 0f
    private val renderQueued = AtomicBoolean(false)
    @Volatile private var released = false

    // GL-thread state.
    private var viewW = width
    private var viewH = height
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var videoTex = 0
    private var input: SurfaceTexture? = null
    private var inputSurface: Surface? = null
    private val texMatrix = FloatArray(16)
    private val identity = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
    private var hasFrame = false
    private var blurStale = true
    private var firstShown = false
    private lateinit var quad: FloatBuffer
    private var downOes = 0
    private var copy2d = 0
    private var gauss = 0
    private var finalProg = 0
    private val fbo = IntArray(3)
    private val fboTex = IntArray(3)
    private var quarterW = 0
    private var quarterH = 0
    private var eighthW = 0
    private var eighthH = 0

    init {
        handler.post {
            try {
                setUp()
            } catch (t: Throwable) {
                android.util.Log.w("PrismAerial", "Home wallpaper: OpenGL blur unavailable, using the still", t)
                tearDown()
                released = true // the TextureView keeps its own SurfaceTexture (its listener returns true)
                thread.quitSafely()
                onFailed()
            }
        }
    }

    /** 0 = sharp, 1 = fully blurred (behind the app grid). Any thread. */
    fun setBlur(value: Float) {
        val v = value.coerceIn(0f, 1f)
        if (kotlin.math.abs(v - blur) < 0.002f && !(v == 0f && blur != 0f)) return
        blur = v
        requestRender()
    }

    fun resize(width: Int, height: Int) {
        handler.post {
            viewW = width
            viewH = height
            if (context != EGL14.EGL_NO_CONTEXT) {
                makeTargets()
                blurStale = true
                render()
            }
        }
    }

    /** Stops drawing, frees everything, then releases [output] (the TextureView's listener returned false). */
    fun release() {
        if (released) return
        released = true
        handler.post {
            tearDown()
            runCatching { output.release() }
            thread.quitSafely()
        }
    }

    override fun onFrameAvailable(st: SurfaceTexture) {
        if (released) return
        try {
            st.updateTexImage()
            st.getTransformMatrix(texMatrix)
        } catch (t: Throwable) {
            return
        }
        hasFrame = true
        blurStale = true
        render()
    }

    private fun requestRender() {
        if (released || !renderQueued.compareAndSet(false, true)) return
        handler.post {
            renderQueued.set(false)
            render()
        }
    }

    // ------------------------------------------------------------------------------------------ setup

    private fun setUp() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "no EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE,
        )
        check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) && count[0] > 0) { "no EGL config" }
        val config = configs[0]!!
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext" }
        eglSurface = EGL14.eglCreateWindowSurface(display, config, output, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface" }
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "eglMakeCurrent" }

        quad = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            // x, y, u, v: a full-screen strip.
            put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f))
            position(0)
        }
        downOes = program(Vertex, DownOes)
        copy2d = program(Vertex, Copy2d)
        gauss = program(Vertex, gaussian(3.2f))
        finalProg = program(Vertex, Final)

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        videoTex = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTex)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        makeTargets()

        val st = SurfaceTexture(videoTex)
        st.setOnFrameAvailableListener(this, handler)
        input = st
        val surface = Surface(st)
        inputSurface = surface
        android.util.Log.i("PrismAerial", "Home wallpaper: drawn through OpenGL ($viewW×$viewH, blur at ${eighthW}×$eighthH)")
        onInput(surface)
    }

    /** The two small targets: 1/4 and 1/8 of the screen (135 rows on any screen), plus a second 1/8 for the blur. */
    private fun makeTargets() {
        if (fbo[0] != 0) {
            GLES20.glDeleteFramebuffers(3, fbo, 0)
            GLES20.glDeleteTextures(3, fboTex, 0)
        }
        val aspect = viewW.toFloat() / viewH.coerceAtLeast(1)
        eighthH = 135
        eighthW = (135 * aspect).roundToInt().coerceAtLeast(1)
        quarterH = eighthH * 2
        quarterW = eighthW * 2
        GLES20.glGenFramebuffers(3, fbo, 0)
        GLES20.glGenTextures(3, fboTex, 0)
        for (i in 0 until 3) {
            val w = if (i == 0) quarterW else eighthW
            val h = if (i == 0) quarterH else eighthH
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex[i])
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[i])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex[i], 0)
            check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) { "framebuffer $i" }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun tearDown() {
        runCatching { inputSurface?.release() }
        runCatching { input?.release() }
        inputSurface = null
        input = null
        if (display != EGL14.EGL_NO_DISPLAY) {
            if (context != EGL14.EGL_NO_CONTEXT && eglSurface != EGL14.EGL_NO_SURFACE) {
                runCatching {
                    EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)
                    if (fbo[0] != 0) {
                        GLES20.glDeleteFramebuffers(3, fbo, 0)
                        GLES20.glDeleteTextures(3, fboTex, 0)
                    }
                    if (videoTex != 0) GLES20.glDeleteTextures(1, intArrayOf(videoTex), 0)
                    listOf(downOes, copy2d, gauss, finalProg).filter { it != 0 }.forEach(GLES20::glDeleteProgram)
                }
            }
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            // No eglTerminate: the default display is shared with Android's own renderer in this process.
        }
        eglSurface = EGL14.EGL_NO_SURFACE
        context = EGL14.EGL_NO_CONTEXT
        display = EGL14.EGL_NO_DISPLAY
        fbo.fill(0)
        fboTex.fill(0)
        videoTex = 0
    }

    // ----------------------------------------------------------------------------------------- render

    private fun render() {
        if (released || !hasFrame || context == EGL14.EGL_NO_CONTEXT) return
        val mix = blur
        if (mix > 0.002f && blurStale) {
            // 1/4: four bilinear taps a source texel apart average a 4×4 block of the video frame.
            pass(downOes, fbo[0], quarterW, quarterH, oes = videoTex, matrix = texMatrix) { p ->
                GLES20.glUniform2f(GLES20.glGetUniformLocation(p, "uStep"), 1f / (quarterW * 4), 1f / (quarterH * 4))
            }
            // 1/8: one bilinear tap between four texels is their average.
            pass(copy2d, fbo[1], eighthW, eighthH, tex2d = fboTex[0])
            pass(gauss, fbo[2], eighthW, eighthH, tex2d = fboTex[1]) { p ->
                GLES20.glUniform2f(GLES20.glGetUniformLocation(p, "uDir"), 1f / eighthW, 0f)
            }
            pass(gauss, fbo[1], eighthW, eighthH, tex2d = fboTex[2]) { p ->
                GLES20.glUniform2f(GLES20.glGetUniformLocation(p, "uDir"), 0f, 1f / eighthH)
            }
            blurStale = false
        }
        pass(finalProg, 0, viewW, viewH, oes = videoTex, matrix = texMatrix, tex2d = fboTex[1]) { p ->
            GLES20.glUniform1f(GLES20.glGetUniformLocation(p, "uMix"), if (mix > 0.002f) mix else 0f)
        }
        EGL14.eglSwapBuffers(display, eglSurface)
        if (!firstShown) {
            firstShown = true
            onFirstFrame()
        }
    }

    private inline fun pass(
        program: Int,
        target: Int,
        w: Int,
        h: Int,
        oes: Int = 0,
        matrix: FloatArray = identity,
        tex2d: Int = 0,
        uniforms: (Int) -> Unit = {},
    ) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, target)
        GLES20.glViewport(0, 0, w, h)
        GLES20.glUseProgram(program)
        if (oes != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oes)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uVideo"), 0)
        }
        if (tex2d != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex2d)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uSrc"), 1)
        }
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTex"), 1, false, matrix, 0)
        uniforms(program)
        val pos = GLES20.glGetAttribLocation(program, "aPos")
        val uv = GLES20.glGetAttribLocation(program, "aUv")
        quad.position(0)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(pos)
        quad.position(2)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun program(vertex: String, fragment: String): Int {
        fun shader(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "shader: " + GLES20.glGetShaderInfoLog(s) }
            return s
        }
        val p = GLES20.glCreateProgram()
        val vs = shader(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = shader(GLES20.GL_FRAGMENT_SHADER, fragment)
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "program: " + GLES20.glGetProgramInfoLog(p) }
        return p
    }

    private companion object {
        /** vUv: the video's own texture coordinates (SurfaceTexture's transform); vPlain: the screen's. */
        const val Vertex = """
attribute vec2 aPos;
attribute vec2 aUv;
uniform mat4 uTex;
varying vec2 vUv;
varying vec2 vPlain;
void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);
    vUv = (uTex * vec4(aUv, 0.0, 1.0)).xy;
    vPlain = aUv;
}
"""

        const val DownOes = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES uVideo;
uniform vec2 uStep;
varying vec2 vUv;
varying vec2 vPlain;
void main() {
    gl_FragColor = 0.25 * (
        texture2D(uVideo, vUv + vec2(-uStep.x, -uStep.y)) + texture2D(uVideo, vUv + vec2(uStep.x, -uStep.y)) +
        texture2D(uVideo, vUv + vec2(-uStep.x, uStep.y)) + texture2D(uVideo, vUv + vec2(uStep.x, uStep.y)));
}
"""

        const val Copy2d = """
precision mediump float;
uniform sampler2D uSrc;
varying vec2 vUv;
varying vec2 vPlain;
void main() { gl_FragColor = texture2D(uSrc, vPlain); }
"""

        /** Sharp video, or mixed with the blurred copy; a little noise on the blur keeps skies from banding. */
        const val Final = """
#extension GL_OES_EGL_image_external : require
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform samplerExternalOES uVideo;
uniform sampler2D uSrc;
uniform float uMix;
varying vec2 vUv;
varying vec2 vPlain;
void main() {
    vec4 sharp = texture2D(uVideo, vUv);
    if (uMix <= 0.0) {
        gl_FragColor = vec4(sharp.rgb, 1.0);
        return;
    }
    vec3 soft = texture2D(uSrc, vPlain).rgb;
    float n = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    gl_FragColor = vec4(mix(sharp.rgb, soft + n / 255.0, uMix), 1.0);
}
"""

        /**
         * A separable Gaussian with the given sigma (in pixels of the small target), 17 taps read in 9
         * fetches: pairs of neighbours share one bilinear fetch placed between them by weight.
         */
        fun gaussian(sigma: Float): String {
            val radius = 8
            val w = FloatArray(radius + 1) { i -> exp(-(i * i) / (2f * sigma * sigma)) }
            val total = w[0] + 2f * (1..radius).sumOf { w[it].toDouble() }.toFloat()
            for (i in w.indices) w[i] /= total
            // GLSL wants plain decimals with a point, whatever the TV's language.
            fun f(x: Float) = String.format(java.util.Locale.ROOT, "%.7f", x)
            val body = StringBuilder("    vec4 c = texture2D(uSrc, vPlain) * ${f(w[0])};\n")
            var a = 1
            while (a < radius) {
                val b = a + 1
                val weight = w[a] + w[b]
                val offset = (a * w[a] + b * w[b]) / weight
                body.append("    c += (texture2D(uSrc, vPlain + uDir * ${f(offset)}) + texture2D(uSrc, vPlain - uDir * ${f(offset)})) * ${f(weight)};\n")
                a += 2
            }
            return """
precision mediump float;
uniform sampler2D uSrc;
uniform vec2 uDir;
varying vec2 vUv;
varying vec2 vPlain;
void main() {
$body    gl_FragColor = c;
}
"""
        }
    }
}
