package com.prismtv.launcher.ui.glass

import android.os.Build
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.prismtv.launcher.ui.theme.LocalPrism

/** What a glass surface refracts. */
enum class GlassSource {
    /** Only the wallpaper / hero artwork. Used by surfaces that live *inside* the Home Screen (dock tray, pills). */
    Background,

    /** The whole Home Screen (wallpaper + icons). Used by Control Centre, folders, the app switcher. */
    Scene,

    /** Everything on screen, including Settings and overlays. Used by dialogs and banners. */
    Full,
}

val SupportsLiveBlur: Boolean = Build.VERSION.SDK_INT >= 31

/**
 * Holds the live layers that glass surfaces sample from. Each source is recorded once per frame
 * by its host and a blurred twin (RenderEffect, Android 12+) is drawn by every glass surface.
 * On older Android versions glass falls back to pre-blurred artwork.
 */
@Stable
class BackdropState(
    val bg: GraphicsLayer,
    val bgBlur: GraphicsLayer,
    val scene: GraphicsLayer,
    val sceneBlur: GraphicsLayer,
    val full: GraphicsLayer,
    val fullBlur: GraphicsLayer,
) {
    var fallbackLight by mutableStateOf<ImageBitmap?>(null)
    var fallbackHeavy by mutableStateOf<ImageBitmap?>(null)
    var fallbackDim by mutableStateOf(Color.Transparent)
    var rootSize by mutableStateOf(IntSize(1920, 1080))

    /**
     * While the App Switcher shows the Home Screen as a scaled, translucent card, lens shaders inside
     * it fall back to the band approximation: nesting runtime-shader layers inside that card has
     * stalled GPU drivers (it reliably crashed the Android 14 emulator).
     */
    var lensSuspended by mutableStateOf(false)

    fun blurred(source: GlassSource): GraphicsLayer = when (source) {
        GlassSource.Background -> bgBlur
        GlassSource.Scene -> sceneBlur
        GlassSource.Full -> fullBlur
    }

    fun raw(source: GlassSource): GraphicsLayer = when (source) {
        GlassSource.Background -> bg
        GlassSource.Scene -> scene
        GlassSource.Full -> full
    }
}

val LocalBackdrop = staticCompositionLocalOf<BackdropState> { error("No BackdropState provided") }

/** Records this node's content into [source]'s layer (and its blurred twin) and draws it. */
fun Modifier.backdropSource(state: BackdropState, source: GlassSource): Modifier = this.drawWithContent {
    val layer = state.raw(source)
    val blurLayer = state.blurred(source)
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
    blurLayer.record { drawLayer(layer) }
}

/** Liquid Glass picks up more colour from what's behind it. */
val LiquidSaturation: ColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.45f) })

/**
 * Liquid Glass / Frosted Glass surface.
 *
 * Liquid (iOS / tvOS 27): the content behind stays sharp. The glass is a lens: a rounded bevel
 * refracts what lies just outside the shape into the rim, with a specular edge. On Android 13+
 * this is a real refraction shader with chromatic dispersion; on older Android it is built from
 * concentric refraction bands of the live backdrop.
 *
 * Frosted (classic tvOS): blurred backdrop, denser tint, hairline edge.
 */
fun Modifier.glass(
    shape: Shape,
    tint: Color,
    source: GlassSource = GlassSource.Background,
    elevation: Dp = 0.dp,
    rimStrength: Float = 1f,
    lens: Boolean = true,
    /** Always blur (full-screen scrims behind the switcher and folders), whatever the material. */
    frosted: Boolean = false,
    /**
     * With [frosted] on a lens material: keep the blurred interior but bend the backdrop around the
     * rim, like iOS menus and folders (frosted for legibility, Liquid Glass at the edge).
     */
    bentRim: Boolean = false,
): Modifier = composed {
    val theme = LocalPrism.current
    val backdrop = LocalBackdrop.current
    val liquid = theme.liquid
    val reduce = theme.reduceTransparency || (!liquid && !SupportsLiveBlur && source == GlassSource.Full)
    val edge = theme.palette.liquidEdge
    val isDark = theme.palette.isDark
    val originState = remember { mutableStateOf(Offset.Zero) }
    val lensLayer = rememberGraphicsLayer()

    val base = if (elevation > 0.dp) {
        Modifier.shadow(
            elevation, shape, clip = false,
            ambientColor = Color.Black.copy(alpha = 0.30f),
            spotColor = Color.Black.copy(alpha = 0.42f),
        )
    } else Modifier

    this
        .then(base)
        .onGloballyPositioned { originState.value = it.positionInRoot() }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = Path().apply { addOutline(outline) }
            fun insetPath(inset: Float): Path {
                val o = shape.createOutline(
                    Size((size.width - inset * 2).coerceAtLeast(1f), (size.height - inset * 2).coerceAtLeast(1f)),
                    layoutDirection, this
                )
                return Path().apply {
                    addOutline(o)
                    translate(Offset(inset, inset))
                }
            }
            val minSide = minOf(size.width, size.height)
            val bevel = minOf(16.dp.toPx(), minSide * 0.42f)
            val strength = minOf(22.dp.toPx(), minSide * 0.5f)
            val margin = strength + 4.dp.toPx()
            // Corner radius in px, for the shader's rounded-rect model.
            val radius = when (outline) {
                is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
                else -> 0f
            }
            val useShader = liquid && lens && !frosted && SupportsLensShader && !backdrop.lensSuspended
            val lensEffect = if (useShader) {
                LensShader.effect(size.width, size.height, margin, radius, bevel, strength, isDark)
            } else null
            val layerSize = IntSize((size.width + margin * 2).roundToInt(), (size.height + margin * 2).roundToInt())
            // Approximation for Android < 13 (no runtime shaders), kept to two backdrop draws so it stays
            // fast on older TV boxes: a sharp, slightly magnified interior plus one refracting rim band
            // that samples from further outside. On Android 12 the rim uses the shared blurred layer,
            // which hides the step between interior and rim like a smeared bevel.
            val rimBand = insetPath(bevel * 0.62f)

            val strokeW = if (liquid) 1.3.dp.toPx() else 1.dp.toPx()
            val edgeBrush = if (liquid) {
                Brush.linearGradient(
                    0f to edge.copy(alpha = 0.95f * rimStrength),
                    0.30f to edge.copy(alpha = 0.22f * rimStrength),
                    0.62f to edge.copy(alpha = 0.08f * rimStrength),
                    1f to edge.copy(alpha = 0.60f * rimStrength),
                    start = Offset.Zero, end = Offset(size.width, size.height),
                )
            } else {
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.16f * rimStrength), Color.White.copy(alpha = 0.06f * rimStrength)),
                    start = Offset.Zero, end = Offset(size.width, size.height),
                )
            }
            val specular = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (isDark) 0.08f else 0.16f),
                0.35f to Color.Transparent,
                startY = 0f, endY = size.height,
            )
            val rimLight = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.22f),
                0.5f to Color.Transparent,
                1f to Color.White.copy(alpha = 0.08f),
                start = Offset.Zero, end = Offset(size.width, size.height),
            )

            onDrawBehind {
                val origin = originState.value

                fun DrawScope.drawRaw(dx: Float, dy: Float) {
                    // Non-uniform scale so the displacement is the same on every edge.
                    val sx = (1f - 2f * dx / size.width).coerceAtLeast(0.2f)
                    val sy = (1f - 2f * dy / size.height).coerceAtLeast(0.2f)
                    withTransform({
                        scale(sx, sy, pivot = center)
                        translate(-origin.x, -origin.y)
                    }) {
                        drawLayer(backdrop.raw(source))
                    }
                }

                fun DrawScope.drawBlurred(d: Float = 0f) {
                    val sx = (1f - 2f * d / size.width).coerceAtLeast(0.2f)
                    val sy = (1f - 2f * d / size.height).coerceAtLeast(0.2f)
                    withTransform({
                        if (d != 0f) scale(sx, sy, pivot = center)
                        translate(-origin.x, -origin.y)
                    }) {
                        if (SupportsLiveBlur) {
                            drawLayer(backdrop.blurred(source))
                        } else {
                            val img = backdrop.fallbackHeavy
                            if (img != null) {
                                drawImage(
                                    image = img,
                                    srcOffset = IntOffset.Zero,
                                    srcSize = IntSize(img.width, img.height),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = backdrop.rootSize,
                                    filterQuality = FilterQuality.Low,
                                )
                                drawRect(
                                    backdrop.fallbackDim,
                                    size = Size(backdrop.rootSize.width.toFloat(), backdrop.rootSize.height.toFloat()),
                                )
                            }
                        }
                    }
                }

                when {
                    reduce -> drawOutline(outline, tint.copy(alpha = 1f))
                    !liquid || frosted -> clipPath(clip) {
                        drawBlurred()
                        drawRect(tint)
                        if (liquid && bentRim) {
                            // Bend the (blurred) backdrop around the rim: thick-glass edges without
                            // sharp fragments of what's behind showing through.
                            clipPath(rimBand, clipOp = ClipOp.Difference) {
                                drawBlurred(strength * 0.45f)
                                drawRect(tint)
                                drawRect(rimLight)
                            }
                            drawRect(specular)
                        }
                    }
                    useShader -> {
                        lensLayer.renderEffect = lensEffect
                        lensLayer.record(size = layerSize) {
                            translate(-origin.x + margin, -origin.y + margin) {
                                drawLayer(backdrop.raw(source))
                            }
                        }
                        clipPath(clip) {
                            translate(-margin, -margin) { drawLayer(lensLayer) }
                            drawRect(tint)
                        }
                    }
                    else -> clipPath(clip) {
                        drawRaw(-strength * 0.04f, -strength * 0.04f)
                        if (lens) {
                            clipPath(rimBand, clipOp = ClipOp.Difference) {
                                if (SupportsLiveBlur) drawBlurred(strength * 0.40f) else drawRaw(strength * 0.30f, strength * 0.30f)
                                drawRect(rimLight)
                            }
                        }
                        drawRect(tint)
                        drawRect(specular)
                    }
                }
                drawOutline(outline, edgeBrush, style = Stroke(strokeW))
            }
        }
}

/** Width of the blur applied to live backdrops, per material. */
fun blurRadiusFor(liquid: Boolean): Dp = if (liquid) 22.dp else 34.dp
