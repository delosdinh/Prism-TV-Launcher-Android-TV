package com.prismtv.launcher.ui.switcher

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.LauncherViewModel
import com.prismtv.launcher.Overlay
import com.prismtv.launcher.data.RecentEntry
import com.prismtv.launcher.ui.HomeLayout
import com.prismtv.launcher.ui.SwitcherLayout
import com.prismtv.launcher.ui.components.AppArtwork
import com.prismtv.launcher.ui.components.HSpace
import com.prismtv.launcher.ui.components.PText
import com.prismtv.launcher.ui.components.lerpF
import com.prismtv.launcher.ui.glass.GlassSource
import com.prismtv.launcher.ui.glass.LocalBackdrop
import com.prismtv.launcher.ui.glass.glass
import kotlin.math.roundToInt

/**
 * App Switcher, laid out like tvOS: Home shrinks into a centred card with recent apps stacked in a
 * receding deck to its left. The focused card sits at 17% with the next newer card lying over its
 * right edge. Press up to fling a card away; select to zoom back into an app.
 */
@Composable
fun AppSwitcher(vm: LauncherViewModel) {
    val visible = vm.overlay == Overlay.Switcher
    val open by animateFloatAsState(
        if (visible) 1f else 0f,
        spring(dampingRatio = 0.88f, stiffness = 210f),
        label = "switcherOpen",
    )
    if (open < 0.002f) return
    val backdrop = LocalBackdrop.current
    DisposableEffect(backdrop) {
        backdrop.lensSuspended = true
        onDispose { backdrop.lensSuspended = false }
    }

    Box(Modifier.fillMaxSize()) {
        // Blurred, dimmed Home Screen behind the deck.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = open }
                .glass(RectangleShape, Color.Black.copy(alpha = 0.30f), GlassSource.Scene, rimStrength = 0f, lens = false, frosted = true)
        )

        val entries = vm.recents
        val focus = vm.switcherFocus
        val focusIsLast = focus == entries.lastIndex
        entries.forEachIndexed { i, e ->
            val rel = i - focus
            if (rel < -5 || rel > 2) return@forEachIndexed
            key(e.key) {
                SwitcherCard(vm, e, rel, focusIsLast, open)
            }
        }
    }
}

@Composable
private fun SwitcherCard(vm: LauncherViewModel, e: RecentEntry, rel: Int, focusIsLast: Boolean, open: Float) {
    val backdrop = LocalBackdrop.current
    val art = e.art
    // A screen-sized copy is plenty for a card (and far lighter than the full Top Shelf artwork).
    val heroArt = art?.let { vm.thumb(it, 960) }

    val move = spring<Float>(dampingRatio = 0.9f, stiffness = 260f)
    val left by animateFloatAsState(SwitcherLayout.cardLeft(rel, focusIsLast), move, label = "cardLeft")
    val scale by animateFloatAsState(SwitcherLayout.cardScale(rel), move, label = "cardScale")
    // Cards further back in the deck sit a little in shadow.
    val shade by animateFloatAsState(if (rel < 0) (0.10f + 0.06f * (-rel - 1)).coerceAtMost(0.34f) else 0f, move, label = "cardShade")
    val labelInk by animateFloatAsState(if (rel == 0) 0.88f else 0.55f, tween(250), label = "labelInk")
    val dismissing = vm.dismissingKey == e.key
    val lift by animateFloatAsState(
        when {
            dismissing -> 1f
            rel == 0 && vm.switcherLift -> 0.14f
            else -> 0f
        },
        if (dismissing) tween(320, easing = FastOutLinearInEasing) else spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "lift",
    )
    val isHome = e.app == null

    // Opening transition: Home starts full-screen and shrinks into its card; apps slide in from the left.
    val w = SwitcherLayout.CardW
    val h = SwitcherLayout.CardH
    val top = SwitcherLayout.CardTop
    val x: Float
    val y: Float
    val cw: Float
    val ch: Float
    val alpha: Float
    if (isHome) {
        x = lerpF(0f, left, open)
        y = lerpF(0f, top, open)
        cw = lerpF(HomeLayout.ScreenW, w, open)
        ch = lerpF(HomeLayout.ScreenH, h, open)
        alpha = 1f
    } else {
        x = left - (1f - open) * 260f
        y = top
        cw = w
        ch = h
        alpha = open
    }
    val radius = if (isHome) lerpF(0f, SwitcherLayout.Radius, open) else SwitcherLayout.Radius
    val shape = RoundedCornerShape(radius.dp)

    // The app's icon and name just above its card (Home has none). Drawn outside the card's layer;
    // the deck's icons stack up like its cards, names only where there's room.
    val app = e.app
    if (app != null) {
        val labelTop = y + ch * (1f - scale) / 2f - 36f - lift * 640f
        Row(
            Modifier
                .offset { IntOffset((x + 12f).dp.roundToPx(), labelTop.dp.roundToPx()) }
                .graphicsLayer { this.alpha = open * (1f - lift) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(47.dp, 28.dp)
                    .shadow(6.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2B2E36))
            ) { AppArtwork(app) }
            if (rel >= 0) {
                HSpace(12.dp)
                PText(app.label, 18.sp, FontWeight.Medium, Color.White.copy(alpha = labelInk), shadow = true)
            }
        }
    }

    Box(
        Modifier
            .offset { IntOffset(x.dp.roundToPx(), (y - lift * 640f).dp.roundToPx()) }
            .size(cw.dp, ch.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
                this.alpha = alpha * (1f - lift * 0.85f)
                shadowElevation = (if (rel == 0) 30.dp else 14.dp).toPx()
                this.shape = shape
                clip = false
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color(0xFF15161A))
        ) {
            if (app == null) {
                // A live miniature of the Home Screen. A slow TV's aerial plays on the video layer, under the
                // whole app (AerialVideoLayerHost), and Home clears its own wallpaper away over it, so the scene
                // has no wallpaper in it there: the card showed its dark fill. The video plane can't be read
                // back (PixelCopy gives black or green on Amlogic), so the wallpaper's still stands in for it.
                val videoLayerStill = if (com.prismtv.launcher.aerial.AerialVideoLayer.player != null) {
                    (vm.wallpaperArt ?: vm.currentArt)?.full
                } else null
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val root = backdrop.rootSize
                            val f = size.width / root.width.toFloat()
                            withTransform({ scale(f, f, pivot = Offset.Zero) }) {
                                if (videoLayerStill != null) drawCropped(videoLayerStill, root)
                                drawLayer(backdrop.scene)
                            }
                        }
                )
            } else if (e.snapshot) {
                // The app as you left it, like tvOS.
                if (heroArt != null) Image(heroArt, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                if (heroArt != null) {
                    Image(heroArt, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.25f),
                                0.35f to Color.Transparent,
                                0.65f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.55f),
                            )
                        )
                )
                if (app.demo == null) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(272.dp, 153.dp)
                            .clip(RoundedCornerShape(18.dp))
                    ) { AppArtwork(app) }
                } else {
                    Column(Modifier.align(Alignment.BottomStart).padding(start = 30.dp, bottom = 26.dp)) {
                        PText(app.label.uppercase(), 11.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.75f), letterSpacing = 1.4.sp)
                        PText(e.title ?: app.label, 30.sp, FontWeight.Bold, Color.White, letterSpacing = (-0.6).sp, shadow = true)
                    }
                }
            }
            if (shade > 0.005f) Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = shade)) })
        }
    }
}

/** [img] filling [dst] from the origin, centre-cropped like ContentScale.Crop (as Home draws its wallpaper). */
private fun DrawScope.drawCropped(img: ImageBitmap, dst: IntSize) {
    if (img.width <= 0 || img.height <= 0 || dst.width <= 0 || dst.height <= 0) return
    val s = maxOf(dst.width / img.width.toFloat(), dst.height / img.height.toFloat())
    val sw = (dst.width / s).roundToInt().coerceIn(1, img.width)
    val sh = (dst.height / s).roundToInt().coerceIn(1, img.height)
    drawImage(
        image = img,
        srcOffset = IntOffset((img.width - sw) / 2, (img.height - sh) / 2),
        srcSize = IntSize(sw, sh),
        dstOffset = IntOffset.Zero,
        dstSize = dst,
        filterQuality = FilterQuality.Low,
    )
}
