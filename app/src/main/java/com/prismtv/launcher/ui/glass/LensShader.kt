package com.prismtv.launcher.ui.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

val SupportsLensShader: Boolean = Build.VERSION.SDK_INT >= 33

/**
 * Liquid Glass lens (Android 13+). The content behind the glass stays sharp; a rounded-rect bevel
 * refracts what's just outside the shape into the rim (stronger toward the edge, like a thick
 * convex slab), with slight chromatic dispersion and a specular highlight from the top-left.
 */
private const val LENS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float2 margin;
uniform float radius;
uniform float bevel;
uniform float strength;
uniform float dispersion;
uniform float darkMode;

float sdRoundBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r, r);
    return length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - margin - halfSize;
    float r = min(radius, min(halfSize.x, halfSize.y));
    float d = sdRoundBox(p, halfSize, r);

    float e = 1.0;
    float gx = sdRoundBox(p + float2(e, 0.0), halfSize, r) - sdRoundBox(p - float2(e, 0.0), halfSize, r);
    float gy = sdRoundBox(p + float2(0.0, e), halfSize, r) - sdRoundBox(p - float2(0.0, e), halfSize, r);
    float2 grad = float2(gx, gy);
    float gl = length(grad);
    float2 n = gl > 0.0001 ? grad / gl : float2(0.0, 0.0);

    // 0 in the flat centre, 1 at the very edge; circular bevel profile.
    float t = clamp(1.0 + d / bevel, 0.0, 1.0);
    float curve = 1.0 - sqrt(max(1.0 - t * t, 0.0));

    float2 off = n * strength * curve;
    float2 mag = -p * 0.012;

    float4 cg = float4(content.eval(coord + off + mag));
    float4 cr = float4(content.eval(coord + off * (1.0 + dispersion) + mag));
    float4 cb = float4(content.eval(coord + off * (1.0 - dispersion) + mag));
    float3 col = float3(cr.r, cg.g, cb.b);
    // Keep what's behind see-through where it is (premultiplied): an aerial on the video layer leaves Home's
    // backdrop clear, and past the screen's edge there's nothing at all. Forced opaque, both came out black.
    float a = cg.a;

    float2 L = normalize(float2(-0.55, -0.85));
    float spec = pow(max(dot(n, L), 0.0), 2.0) * curve;
    float shade = pow(max(dot(n, -L), 0.0), 2.0) * curve;
    col = col + float3(spec * 0.42) * a;
    col = col * (1.0 - shade * mix(0.10, 0.22, darkMode));
    return half4(float4(min(col, float3(a)), a));
}
"""

@RequiresApi(33)
object LensShader {
    private data class Key(
        val width: Float,
        val height: Float,
        val margin: Float,
        val radius: Float,
        val bevel: Float,
        val strength: Float,
        val dark: Boolean,
    )

    /**
     * Effects already made, by their uniforms. Each new RuntimeShader compiles the shader source again on the
     * main thread, and every glass surface asked for one whenever it was first drawn: Control Centre's tiles
     * compiled it a dozen times in the first frame of each opening, and Home's glass again each time the App
     * Switcher closed. A RenderEffect can't change once made, so surfaces of the same size share one.
     */
    private val made = object : LinkedHashMap<Key, RenderEffect>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, RenderEffect>?): Boolean = size > 64
    }

    fun effect(
        width: Float,
        height: Float,
        margin: Float,
        radius: Float,
        bevel: Float,
        strength: Float,
        dark: Boolean,
    ): RenderEffect = synchronized(made) {
        made.getOrPut(Key(width, height, margin, radius, bevel, strength, dark)) {
            make(width, height, margin, radius, bevel, strength, dark)
        }
    }

    private fun make(
        width: Float,
        height: Float,
        margin: Float,
        radius: Float,
        bevel: Float,
        strength: Float,
        dark: Boolean,
    ): RenderEffect {
        val shader = RuntimeShader(LENS_AGSL)
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("margin", margin, margin)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("bevel", bevel)
        shader.setFloatUniform("strength", strength)
        shader.setFloatUniform("dispersion", 0.14f)
        shader.setFloatUniform("darkMode", if (dark) 1f else 0f)
        return android.graphics.RenderEffect
            .createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
    }
}
