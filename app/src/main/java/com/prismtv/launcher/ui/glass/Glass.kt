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
import androidx.compose.ui.graphics.layer.CompositingStrategy
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

    /** The Home Screen, blurred to Settings & Pop-ups Transparency (Settings' own backdrop). */
    SceneUi,
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
    /** The scene blurred for Settings (its own radius, from Settings & Pop-ups Transparency). */
    val sceneUiBlur: GraphicsLayer,
    /**
     * Offscreen copies of the scene and the full canvas, for Liquid Glass before Android 13. Its lens
     * draws the backdrop twice per surface, and drawing a source replays everything in it (every tile,
     * image and line of text on the Home Screen): Control Centre's tiles replayed the whole Home Screen
     * about twenty times a frame. A copy rendered once per frame makes each of those one texture draw.
     */
    val sceneSharp: GraphicsLayer,
    val fullSharp: GraphicsLayer,
) {
    init {
        sceneSharp.compositingStrategy = CompositingStrategy.Offscreen
        fullSharp.compositingStrategy = CompositingStrategy.Offscreen
    }

    /** Debug builds: CI turns the copies off to time them against drawing the sources directly. */
    var sharpCopies by mutableStateOf(true)

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
        GlassSource.SceneUi -> sceneUiBlur
    }

    /** The source's offscreen copy, or its own layer (the background is a single image: cheap to replay). */
    fun copyOf(source: GlassSource): GraphicsLayer? = when (source) {
        GlassSource.Background -> null
        GlassSource.Scene, GlassSource.SceneUi -> sceneSharp
        GlassSource.Full -> fullSharp
    }

    /** What the Liquid Glass approximation draws through: the copy when there is one. */
    fun sharp(source: GlassSource): GraphicsLayer = copyOf(source)?.takeIf { sharpCopies } ?: raw(source)

    fun raw(source: GlassSource): GraphicsLayer = when (source) {
        GlassSource.Background -> bg
        GlassSource.Scene -> scene
        GlassSource.Full -> full
        GlassSource.SceneUi -> scene
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
    // Settings blurs the same scene with its own radius. Recording is a single draw op; the blur
    // itself only runs in frames where Settings actually draws it.
    if (source == GlassSource.Scene) state.sceneUiBlur.record { drawLayer(layer) }
    // The same for the copy Liquid Glass draws through before Android 13: rendered only in frames
    // where a glass surface draws it (Frosted never does).
    if (!SupportsLensShader) state.copyOf(source)?.record { drawLayer(layer) }
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
    /**
     * Liquid Glass over a blurred backdrop (open folders): the lens bends the *blurred* scene, so the
     * panel reads as glass over a soft background rather than over sharp icons.
     */
    blurredLens: Boolean = false,
): Modifier = composed {
    val theme = LocalPrism.current
    val backdrop = LocalBackdrop.current
    // Lite Mode draws every glass as frosted over pre-blurred artwork: no live blur, no refraction.
    val lite = theme.lite
    val liquid = theme.liquid && !lite
    val liveBlur = SupportsLiveBlur && !lite
    val reduce = theme.reduceTransparency || (!liquid && !liveBlur && source == GlassSource.Full)
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
            // Frosted panels with a bent rim keep the bend subtle: a narrow band and a softer edge.
            val softRim = liquid && frosted && bentRim
            val bentBand = if (softRim) insetPath(bevel * 0.36f) else rimBand
            val edgeScale = if (softRim) 0.5f else 1f

            val strokeW = if (liquid) 1.3.dp.toPx() else 1.dp.toPx()
            val edgeBrush = if (liquid) {
                Brush.linearGradient(
                    0f to edge.copy(alpha = 0.72f * rimStrength * edgeScale),
                    0.30f to edge.copy(alpha = 0.18f * rimStrength * edgeScale),
                    0.62f to edge.copy(alpha = 0.06f * rimStrength * edgeScale),
                    1f to edge.copy(alpha = 0.42f * rimStrength * edgeScale),
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
                        drawLayer(backdrop.sharp(source))
                    }
                }

                fun DrawScope.drawBlurred(d: Float = 0f) {
                    val sx = (1f - 2f * d / size.width).coerceAtLeast(0.2f)
                    val sy = (1f - 2f * d / size.height).coerceAtLeast(0.2f)
                    withTransform({
                        if (d != 0f) scale(sx, sy, pivot = center)
                        translate(-origin.x, -origin.y)
                    }) {
                        if (liveBlur) {
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
                            clipPath(bentBand, clipOp = ClipOp.Difference) {
                                drawBlurred(strength * 0.30f)
                                drawRect(tint)
                            }
                        }
                    }
                    useShader -> {
                        lensLayer.renderEffect = lensEffect
                        lensLayer.record(size = layerSize) {
                            translate(-origin.x + margin, -origin.y + margin) {
                                drawLayer(if (blurredLens) backdrop.blurred(source) else backdrop.raw(source))
                            }
                        }
                        clipPath(clip) {
                            translate(-margin, -margin) { drawLayer(lensLayer) }
                            drawRect(tint)
                        }
                    }
                    blurredLens -> clipPath(clip) {
                        drawBlurred(-strength * 0.04f)
                        if (lens) {
                            clipPath(rimBand, clipOp = ClipOp.Difference) {
                                drawBlurred(strength * 0.40f)
                                drawRect(rimLight)
                            }
                        }
                        drawRect(tint)
                        drawRect(specular)
                    }
                    else -> clipPath(clip) {
                        drawRaw(-strength * 0.04f, -strength * 0.04f)
                        if (lens) {
                            clipPath(rimBand, clipOp = ClipOp.Difference) {
                                if (liveBlur) drawBlurred(strength * 0.40f) else drawRaw(strength * 0.30f, strength * 0.30f)
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
