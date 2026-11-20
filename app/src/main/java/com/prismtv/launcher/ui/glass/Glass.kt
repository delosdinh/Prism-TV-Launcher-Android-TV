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
import androidx.compose.ui.graphics.drawscope.withTransform
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
 * Liquid: light blur, saturation boost, a magnified "lens" interior and a refracting rim band
 * with a specular edge highlight. Frosted (classic tvOS): heavier blur, denser tint, hairline edge.
 */
fun Modifier.glass(
    shape: Shape,
    tint: Color,
    source: GlassSource = GlassSource.Background,
    elevation: Dp = 0.dp,
    rimStrength: Float = 1f,
    lens: Boolean = true,
): Modifier = composed {
    val theme = LocalPrism.current
    val backdrop = LocalBackdrop.current
    val liquid = theme.liquid
    val reduce = theme.reduceTransparency || (!SupportsLiveBlur && source == GlassSource.Full)
    val edge = theme.palette.liquidEdge
    val isDark = theme.palette.isDark
    val originState = remember { mutableStateOf(Offset.Zero) }

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
            val rimWidth = 7.dp.toPx()
            val innerOutline = shape.createOutline(
                Size((size.width - rimWidth * 2).coerceAtLeast(1f), (size.height - rimWidth * 2).coerceAtLeast(1f)),
                layoutDirection, this
            )
            val inner = Path().apply {
                addOutline(innerOutline)
                translate(Offset(rimWidth, rimWidth))
            }
            val strokeW = if (liquid) 1.3.dp.toPx() else 1.dp.toPx()
            val edgeBrush = if (liquid) {
                Brush.linearGradient(
                    0f to edge.copy(alpha = 0.95f * rimStrength),
                    0.30f to edge.copy(alpha = 0.18f * rimStrength),
                    0.62f to edge.copy(alpha = 0.06f * rimStrength),
                    1f to edge.copy(alpha = 0.55f * rimStrength),
                    start = Offset.Zero, end = Offset(size.width, size.height),
                )
            } else {
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.16f * rimStrength), Color.White.copy(alpha = 0.06f * rimStrength)),
                    start = Offset.Zero, end = Offset(size.width, size.height),
                )
            }
            val specular = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (isDark) 0.10f else 0.22f),
                0.42f to Color.Transparent,
                startY = 0f, endY = size.height,
            )
            val rimShade = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.10f),
                0.5f to Color.Transparent,
                1f to Color.Black.copy(alpha = if (isDark) 0.10f else 0.04f),
                startY = 0f, endY = size.height,
            )

            onDrawBehind {
                val origin = originState.value

                fun DrawScope.drawSource(factor: Float) {
                    withTransform({
                        scale(factor, factor, pivot = center)
                        translate(-origin.x, -origin.y)
                    }) {
                        if (SupportsLiveBlur) {
                            drawLayer(backdrop.blurred(source))
                        } else {
                            val img = (if (liquid) backdrop.fallbackLight else backdrop.fallbackHeavy)
                                ?: backdrop.fallbackHeavy
                            if (img != null) {
                                drawImage(
                                    image = img,
                                    srcOffset = IntOffset.Zero,
                                    srcSize = IntSize(img.width, img.height),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = backdrop.rootSize,
                                    filterQuality = FilterQuality.Low,
                                )
                                drawRect(backdrop.fallbackDim, size = Size(backdrop.rootSize.width.toFloat(), backdrop.rootSize.height.toFloat()))
                            }
                        }
                    }
                }

                if (reduce) {
                    drawOutline(outline, tint.copy(alpha = 1f))
                } else {
                    clipPath(clip) {
                        // Lens: interior is very slightly magnified, like a thick slab of glass.
                        drawSource(if (liquid && lens) 1.035f else 1f)
                        if (liquid && lens) {
                            // Rim band refracts more strongly.
                            clipPath(inner, clipOp = ClipOp.Difference) {
                                drawSource(1.16f)
                                drawRect(rimShade)
                            }
                        }
                        drawRect(tint)
                        if (liquid) drawRect(specular)
                    }
                }
                drawOutline(outline, edgeBrush, style = Stroke(strokeW))
            }
        }
}

/** Width of the blur applied to live backdrops, per material. */
fun blurRadiusFor(liquid: Boolean): Dp = if (liquid) 22.dp else 34.dp
