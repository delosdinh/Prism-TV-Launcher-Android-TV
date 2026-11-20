package com.prismtv.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.data.DemoGlyph
import com.prismtv.launcher.data.FolderItem
import com.prismtv.launcher.data.LauncherApp
import com.prismtv.launcher.ui.theme.Inter

@Composable
fun PText(
    text: String,
    size: TextUnit,
    weight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    shadow: Boolean = false,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = Inter,
            fontSize = size,
            fontWeight = weight,
            color = color,
            textAlign = align,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
            shadow = if (shadow) Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1.5f), 10f) else null,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun PIcon(vector: ImageVector, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(
        imageVector = vector,
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

private val Spectrum = listOf(
    Color(0xFFFF453A), Color(0xFFFF9F0A), Color(0xFFFFD60A),
    Color(0xFF30D158), Color(0xFF0A84FF), Color(0xFFBF5AF2),
)

/** The Prism mark: a light beam entering a triangular prism and splitting into a spectrum. */
@Composable
fun PrismMark(modifier: Modifier, color: Color, beams: Boolean = true) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val triH = h * 0.78f
        val triW = triH * 1.1547f
        val cx = w * 0.5f
        val top = (h - triH) / 2f
        val tri = Path().apply {
            moveTo(cx, top)
            lineTo(cx + triW / 2f, top + triH)
            lineTo(cx - triW / 2f, top + triH)
            close()
        }
        val stroke = h * 0.055f
        if (beams) {
            val entry = Offset(cx - triW * 0.21f, top + triH * 0.52f)
            drawLine(color.copy(alpha = 0.9f), Offset(0f, h * 0.66f), entry, strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
            val exit = Offset(cx + triW * 0.17f, top + triH * 0.50f)
            Spectrum.forEachIndexed { i, c ->
                val ey = h * 0.40f + i * h * 0.075f
                drawLine(c, exit, Offset(w, ey), strokeWidth = stroke * 0.8f)
            }
        }
        drawPath(tri, color = color.copy(alpha = 0.16f))
        drawPath(tri, color = color, style = Stroke(width = stroke, join = StrokeJoin.Round))
    }
}

@Composable
fun Avatar(size: Dp, focused: Boolean = false, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "avatar")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(size)
            .then(if (focused) Modifier.shadow(10.dp, CircleShape) else Modifier)
            .background(Brush.linearGradient(listOf(Color(0xFFFFB340), Color(0xFFFF5E62))), CircleShape)
            .then(if (focused) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        PIcon(Icons.Rounded.Person, Color.White.copy(alpha = 0.95f), size * 0.62f)
    }
}

@Composable
fun Badge(text: String, color: Color = Color.White) {
    Box(
        Modifier
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        PText(text.uppercase(), 9.sp, FontWeight.Bold, color.copy(alpha = 0.88f), letterSpacing = 0.4.sp)
    }
}

/** Renders an app's tile artwork: its TV banner, demo glyph, or icon on its dominant colour. */
@Composable
fun AppArtwork(app: LauncherApp, modifier: Modifier = Modifier.fillMaxSize()) {
    val banner = app.banner
    val demo = app.demo
    val icon = app.icon
    when {
        banner != null -> Image(banner, app.label, modifier, contentScale = ContentScale.Crop)
        demo != null -> DemoIcon(demo, modifier)
        else -> Box(
            modifier.background(Brush.verticalGradient(listOf(lighten(app.tint, 0.16f), darken(app.tint, 0.12f)))),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(icon, app.label, Modifier.fillMaxHeight(0.62f).aspectRatio(1f))
            }
        }
    }
}

@Composable
fun DemoIcon(g: DemoGlyph, modifier: Modifier) {
    val colors = if (g.colors.size >= 2) g.colors else listOf(g.colors.first(), g.colors.first())
    Box(modifier.background(Brush.verticalGradient(colors)), contentAlignment = Alignment.Center) {
        val wordmark = g.wordmark
        val vector = g.vector
        when {
            g.prism -> PrismMark(Modifier.fillMaxHeight(0.66f).aspectRatio(1.7f), Color.White)
            wordmark != null -> PText(wordmark, 22.sp, FontWeight.Bold, g.fg)
            vector != null -> Image(
                vector, null,
                Modifier.fillMaxHeight(0.52f).aspectRatio(1f),
                colorFilter = ColorFilter.tint(g.fg),
            )
        }
    }
}

/** A folder tile: frosted plate with a 3×2 (or 3×3) grid of miniature app tiles. */
@Composable
fun FolderArtwork(folder: FolderItem, modifier: Modifier = Modifier.fillMaxSize(), plate: Color) {
    Box(modifier.background(plate).padding(horizontal = 9.dp, vertical = 8.dp)) {
        val apps = folder.apps.take(9)
        val cols = 3
        val rows = if (apps.size > 6) 3 else 2
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        ) {
            for (r in 0 until rows) {
                androidx.compose.foundation.layout.Row(
                    Modifier.weight(1f),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp),
                ) {
                    for (c in 0 until cols) {
                        val app = apps.getOrNull(r * cols + c)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            if (app != null) AppArtwork(app)
                        }
                    }
                }
            }
        }
    }
}

fun lighten(c: Color, f: Float) = Color(
    red = c.red + (1f - c.red) * f, green = c.green + (1f - c.green) * f, blue = c.blue + (1f - c.blue) * f, alpha = 1f
)

fun darken(c: Color, f: Float) = Color(red = c.red * (1 - f), green = c.green * (1 - f), blue = c.blue * (1 - f), alpha = 1f)

fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
