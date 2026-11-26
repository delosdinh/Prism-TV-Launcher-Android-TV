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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.BuildConfig
import com.prismtv.launcher.LauncherViewModel
import com.prismtv.launcher.Overlay
import com.prismtv.launcher.data.RecentEntry
import com.prismtv.launcher.ui.HomeLayout
import com.prismtv.launcher.ui.SwitcherLayout
import com.prismtv.launcher.ui.components.AppArtwork
import com.prismtv.launcher.ui.components.HSpace
import com.prismtv.launcher.ui.components.PText
import com.prismtv.launcher.ui.components.atLeast
import com.prismtv.launcher.ui.components.lerpF
import com.prismtv.launcher.ui.glass.GlassSource
import com.prismtv.launcher.ui.glass.LocalBackdrop
import com.prismtv.launcher.ui.glass.PerfSwitches
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
    // Read only in layout, graphicsLayer and draw blocks (and here only whether it's past the threshold):
    // reading it in composition rebuilt the whole deck, every card, label and image, on each frame of
    // the opening and closing animation.
    val open = animateFloatAsState(
        if (visible) 1f else 0f,
        spring(dampingRatio = 0.88f, stiffness = 210f),
        label = "switcherOpen",
    )
    if (!open.atLeast(0.002f)) return
    com.prismtv.launcher.ui.glass.HoldSceneCache()
    // Debug builds: CI counts how often the deck and its cards recompose while it opens and closes.
    if (BuildConfig.DEBUG) SideEffect { android.util.Log.d("PrismComp", "deck") }
    val backdrop = LocalBackdrop.current
    androidx.compose.runtime.LaunchedEffect(visible) {
        // For checking the Home card on a TV: what it has to draw the wallpaper with.
        if (visible) android.util.Log.i(
            "PrismSwitcher",
            "open: wallpaper=${vm.wallpaperKey} videoLayer=${com.prismtv.launcher.aerial.AerialVideoLayer.player != null} " +
                "poster=${com.prismtv.launcher.aerial.AerialVideoLayer.poster != null} still=${(vm.wallpaperArt ?: vm.currentArt)?.full?.let { "${it.width}x${it.height}" }}",
        )
        // The poster is grabbed a few seconds into each shot. If that one is over a minute old, a frame from
        // about now, once the deck has opened: the grab (a second or so, and a decoder) made the opening
        // animation stutter on the AM9.
        if (visible) {
            kotlinx.coroutines.delay(1200)
            com.prismtv.launcher.aerial.AerialVideoLayer.refreshPoster(maxAgeMs = 60_000L)
        }
    }
    DisposableEffect(backdrop) {
        backdrop.lensSuspended = true
        onDispose { backdrop.lensSuspended = false }
    }

    Box(Modifier.fillMaxSize()) {
        // Blurred, dimmed Home Screen behind the deck.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = open.value }
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
private fun SwitcherCard(vm: LauncherViewModel, e: RecentEntry, rel: Int, focusIsLast: Boolean, open: State<Float>) {
    if (BuildConfig.DEBUG) SideEffect { android.util.Log.d("PrismComp", "deckCard") }
    val backdrop = LocalBackdrop.current
    val art = e.art
    // A screen-sized copy is plenty for a card (and far lighter than the full Top Shelf artwork).
    val heroArt = art?.let { vm.thumb(it, 960) }

    // Like the deck's opening, these animated values are read only in layout, graphicsLayer and draw
    // blocks, never in composition, so moving along the deck doesn't rebuild every card on each frame.
    val move = spring<Float>(dampingRatio = 0.9f, stiffness = 260f)
    val left = animateFloatAsState(SwitcherLayout.cardLeft(rel, focusIsLast), move, label = "cardLeft")
    val scale = animateFloatAsState(SwitcherLayout.cardScale(rel), move, label = "cardScale")
    // Cards further back in the deck sit a little in shadow.
    val shade = animateFloatAsState(if (rel < 0) (0.10f + 0.06f * (-rel - 1)).coerceAtMost(0.34f) else 0f, move, label = "cardShade")
    val labelInk = animateFloatAsState(if (rel == 0) 0.88f else 0.55f, tween(250), label = "labelInk")
    val dismissing = vm.dismissingKey == e.key
    val lift = animateFloatAsState(
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
    fun x() = if (isHome) lerpF(0f, left.value, open.value) else left.value - (1f - open.value) * 260f
    fun y() = if (isHome) lerpF(0f, top, open.value) else top
    fun cw() = if (isHome) lerpF(HomeLayout.ScreenW, w, open.value) else w
    fun ch() = if (isHome) lerpF(HomeLayout.ScreenH, h, open.value) else h
    fun alpha() = if (isHome) 1f else open.value
    val restShape = RoundedCornerShape(SwitcherLayout.Radius.dp)
    fun shape(): Shape = if (isHome) RoundedCornerShape(lerpF(0f, SwitcherLayout.Radius, open.value).dp) else restShape

    // The app's icon and name just above its card (Home has none). Drawn outside the card's layer;
    // the deck's icons stack up like its cards, names only where there's room.
    val app = e.app
    if (app != null) {
        Row(
            Modifier
                .offset {
                    val labelTop = y() + ch() * (1f - scale.value) / 2f - 36f - lift.value * 640f
                    IntOffset((x() + 12f).dp.roundToPx(), labelTop.dp.roundToPx())
                }
                .graphicsLayer { this.alpha = open.value * (1f - lift.value) },
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
                CardLabel(app.label, labelInk)
            }
        }
    }

    Box(
        Modifier
            .offset { IntOffset(x().dp.roundToPx(), (y() - lift.value * 640f).dp.roundToPx()) }
            // Modifier.size, with the size read while measuring: only the Home card's changes as it opens.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.constrain(Constraints.fixed(cw().dp.roundToPx(), ch().dp.roundToPx())))
                layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
            }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                transformOrigin = TransformOrigin(0f, 0.5f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
                this.alpha = alpha() * (1f - lift.value * 0.85f)
                shadowElevation = (if (rel == 0) 30.dp else 14.dp).toPx()
                this.shape = shape()
                clip = false
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // Modifier.clip, with the Home card's corners read in the layer.
                .graphicsLayer {
                    this.shape = shape()
                    clip = true
                }
                .background(Color(0xFF15161A))
        ) {
            if (app == null) {
                // A live miniature of the Home Screen. A slow TV's aerial plays on the video layer, under the
                // whole app (AerialVideoLayerHost), and Home clears its own wallpaper away over it, so the scene
                // has no wallpaper in it there: the card showed its dark fill. The video plane can't be read
                // back (PixelCopy gives black or green on Amlogic), so the wallpaper's still stands in for it.
                // Only while the aerial plays on the video layer: otherwise Home's own backdrop is opaque
                // (HomeBackdrop's gradient is fully drawn whenever there's no video-layer player) and covered
                // the still completely, so drawing it was a hidden full-screen image on every frame.
                val videoLayerStill = if (com.prismtv.launcher.aerial.AerialVideoLayer.player != null) {
                    // A frame of the shot playing, once one's been grabbed; the wallpaper's still until then.
                    com.prismtv.launcher.aerial.AerialVideoLayer.poster ?: (vm.wallpaperArt ?: vm.currentArt)?.full
                } else null
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val root = backdrop.rootSize
                            val f = size.width / root.width.toFloat()
                            // While the card grows, shrinks or moves along the deck (PerfSwitches.switcherHomeCopy):
                            // the scene's offscreen copy, rendered once at full size and scaled, instead of replaying
                            // all of Home at every new size. At rest, Home itself, drawn sharp at the card's size.
                            val moving = open.value != 1f || scale.value != SwitcherLayout.cardScale(rel)
                            val home = if (PerfSwitches.switcherHomeCopy && moving) backdrop.sharp(GlassSource.Scene) else backdrop.scene
                            withTransform({ scale(f, f, pivot = Offset.Zero) }) {
                                if (videoLayerStill != null) drawCropped(videoLayerStill, root)
                                drawLayer(home)
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
            val shaded by remember(shade) { derivedStateOf { shade.value > 0.005f } }
            if (shaded) Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = shade.value)) })
        }
    }
}

/**
 * The app's name over its card. Its ink fades as focus moves, read here so that only this text
 * recomposes. Not a layer alpha: that would fade the text's shadow with it.
 */
@Composable
private fun CardLabel(text: String, ink: State<Float>) {
    PText(text, 18.sp, FontWeight.Medium, Color.White.copy(alpha = ink.value), shadow = true)
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
