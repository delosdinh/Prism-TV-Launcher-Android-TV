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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.LauncherViewModel
import com.prismtv.launcher.Overlay
import com.prismtv.launcher.data.RecentEntry
import com.prismtv.launcher.ui.HomeLayout
import com.prismtv.launcher.ui.SwitcherLayout
import com.prismtv.launcher.ui.components.AppArtwork
import com.prismtv.launcher.ui.components.HSpace
import com.prismtv.launcher.ui.components.PIcon
import com.prismtv.launcher.ui.components.PText
import com.prismtv.launcher.ui.components.lerpF
import com.prismtv.launcher.ui.glass.GlassSource
import com.prismtv.launcher.ui.glass.LocalBackdrop
import com.prismtv.launcher.ui.glass.glass

private val CardShape = RoundedCornerShape(28.dp)

/**
 * App Switcher. Opening it shrinks the Home Screen into the right-most card while recent apps
 * slide in as a cascading deck. Press up to fling a card away; select to zoom back into an app.
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

    Box(Modifier.fillMaxSize()) {
        // Blurred, dimmed Home Screen behind the deck.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = open }
                .glass(RectangleShape, Color.Black.copy(alpha = 0.30f), GlassSource.Scene, rimStrength = 0f, lens = false)
        )

        val entries = vm.recents
        val focus = vm.switcherFocus
        entries.forEachIndexed { i, e ->
            val rel = i - focus
            if (rel < -4 || rel > 2) return@forEachIndexed
            key(e.key) {
                SwitcherCard(vm, e, rel, open)
            }
        }

        val focusedIsApp = entries.getOrNull(focus)?.app != null
        val hint by animateFloatAsState(if (focusedIsApp && visible) 1f else 0f, tween(250), label = "swHint")
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
                .graphicsLayer { alpha = hint * open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PIcon(Icons.Rounded.KeyboardArrowUp, Color.White.copy(alpha = 0.8f), 18.dp)
            HSpace(4.dp)
            PText("Swipe up to close", 12.sp, FontWeight.Medium, Color.White.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun SwitcherCard(vm: LauncherViewModel, e: RecentEntry, rel: Int, open: Float) {
    val backdrop = LocalBackdrop.current
    val art = e.art
    LaunchedEffect(art?.key) { if (art != null) vm.requestArt(art) }
    val heroArt = art?.let { vm.artCache[it.key] }

    val leftTarget = SwitcherLayout.cardLeft(rel)
    val left by animateFloatAsState(leftTarget, spring(dampingRatio = 0.86f, stiffness = 240f), label = "cardLeft")
    val scale by animateFloatAsState(SwitcherLayout.cardScale(rel), spring(dampingRatio = 0.86f, stiffness = 240f), label = "cardScale")
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
    val radius = if (isHome) lerpF(0f, 28f, open) else 28f
    val shape = RoundedCornerShape(radius.dp)

    // Icon (and name) above the card. Drawn outside the card's layer so the deck's stacked icons are
    // never clipped by a translucent card; icons stack up like the cards, names only where there's room.
    val labelTop = y + ch * (1f - scale) / 2f - 44f - lift * 640f
    Row(
        Modifier
            .offset { IntOffset(x.dp.roundToPx(), labelTop.dp.roundToPx()) }
            .graphicsLayer { this.alpha = open * (1f - lift) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp, 31.dp)
                .shadow(8.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2B2E36))
        ) {
            val app = e.app
            if (app != null) AppArtwork(app) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PIcon(Icons.Rounded.Home, Color.White, 20.dp)
            }
        }
        if (rel >= 0) {
            HSpace(12.dp)
            PText(e.app?.label ?: "Home", 19.sp, FontWeight.SemiBold, Color.White, shadow = true)
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
                this.alpha = alpha * (1f - lift * 0.85f) * (if (rel < 0) 0.92f else 1f)
                shadowElevation = (if (rel == 0) 36.dp else 16.dp).toPx()
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
            val app = e.app
            if (app == null) {
                // A live miniature of the Home Screen.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val root = backdrop.rootSize
                            val f = size.width / root.width.toFloat()
                            withTransform({ scale(f, f, pivot = Offset.Zero) }) {
                                drawLayer(backdrop.scene)
                            }
                        }
                )
            } else {
                if (heroArt != null) {
                    Image(heroArt.full, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
            if (rel == 0 && !isHome) {
                // Focus rim.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRoundRect(
                                Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.25f)),
                                    start = Offset.Zero, end = Offset(size.width, size.height),
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
                            )
                        }
                )
            }
        }
    }
}
