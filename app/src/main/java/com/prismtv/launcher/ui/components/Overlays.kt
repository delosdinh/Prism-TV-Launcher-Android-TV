package com.prismtv.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.BannerSpec
import com.prismtv.launcher.DialogSpec
import com.prismtv.launcher.LauncherViewModel
import com.prismtv.launcher.ui.HomeLayout
import com.prismtv.launcher.ui.glass.GlassSource
import com.prismtv.launcher.ui.glass.glass
import com.prismtv.launcher.ui.theme.LocalPrism

/** Centred confirmation / action sheet, as in the tvOS pop-up component library. */
@Composable
fun DialogHost(spec: DialogSpec?, focus: Int) {
    val holder = remember { arrayOfNulls<DialogSpec>(1) }
    if (spec != null) holder[0] = spec
    val shown = holder[0] ?: return
    val t by animateFloatAsState(if (spec != null) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 380f), label = "dialog")
    if (t < 0.002f) return
    val theme = LocalPrism.current
    val p = theme.palette

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = t }
            .background(p.bgBase.copy(alpha = p.backgroundDim)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .graphicsLayer {
                    val s = lerpF(0.9f, 1f, t)
                    scaleX = s
                    scaleY = s
                }
                .width(318.dp)
                .glass(RoundedCornerShape(32.dp), theme.panelFill(), GlassSource.Full, elevation = 40.dp)
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PText(shown.title, 20.sp, FontWeight.SemiBold, p.textPrimary, align = TextAlign.Center)
            VSpace(8.dp)
            PText(
                shown.message, 13.sp, FontWeight.Normal, p.textSecondary,
                maxLines = 5, align = TextAlign.Center, lineHeight = 18.sp,
            )
            VSpace(18.dp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.buttons.forEachIndexed { i, b ->
                    val focused = i == focus && spec != null
                    val scale by animateFloatAsState(if (focused) 1.04f else 1f, spring(dampingRatio = 0.65f, stiffness = 420f), label = "dlgBtn")
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .then(
                                if (focused) Modifier.shadow(14.dp, shape).background(theme.controlFocused(), shape)
                                else Modifier.background(theme.controlIdle(), shape)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        PText(b.label, 16.sp, if (focused) FontWeight.SemiBold else FontWeight.Medium, if (focused) p.textFocused else p.textPrimary)
                    }
                }
            }
        }
    }
}

/** Non-blocking notification banner (top-right). */
@Composable
fun BannerHost(spec: BannerSpec?) {
    val holder = remember { arrayOfNulls<BannerSpec>(1) }
    if (spec != null) holder[0] = spec
    val shown = holder[0] ?: return
    val t by animateFloatAsState(if (spec != null) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 260f), label = "banner")
    if (t < 0.002f) return
    val theme = LocalPrism.current
    val p = theme.palette
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 70.dp, end = 30.dp)
                .graphicsLayer {
                    alpha = t
                    translationY = -24.dp.toPx() * (1f - t)
                }
                .width(312.dp)
                .glass(RoundedCornerShape(24.dp), theme.panelFill(), GlassSource.Full, elevation = 24.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(p.accent, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                PIcon(shown.icon, Color.White, 22.dp)
            }
            HSpace(12.dp)
            Column {
                PText(shown.title, 14.sp, FontWeight.SemiBold, p.textPrimary)
                PText(shown.message, 12.sp, FontWeight.Normal, p.textSecondary, maxLines = 2, lineHeight = 16.sp)
            }
        }
    }
}

private val LaunchEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * The launch transition: the focused tile expands to fill the screen (and collapses back into
 * place when you return), the way tvOS zooms between an icon and its app.
 */
@Composable
fun LaunchOverlay(vm: LauncherViewModel) {
    val anim = vm.launchAnim ?: return
    val progress = remember(anim.id) { Animatable(if (anim.reverse) 1f else 0f) }
    val art = anim.art
    LaunchedEffect(anim.id) {
        if (art != null) vm.requestArt(art)
        progress.animateTo(
            if (anim.reverse) 0f else 1f,
            tween(if (anim.reverse) 520 else 480, easing = LaunchEasing),
        )
        vm.onLaunchZoomComplete(anim)
    }
    val heroArt = art?.let { vm.artCache[it.key] }
    val pr = progress.value
    val from = anim.from
    val l = lerpF(from.left, 0f, pr)
    val tp = lerpF(from.top, 0f, pr)
    val w = lerpF(from.width, HomeLayout.ScreenW, pr)
    val h = lerpF(from.height, HomeLayout.ScreenH, pr)
    val radius = lerpF(14f, 0f, pr)
    val artAlpha = ((pr - 0.25f) / 0.5f).coerceIn(0f, 1f)
    val blackout = if (anim.app.demo != null) 0f else if (anim.reverse) ((pr - 0.75f) / 0.25f).coerceIn(0f, 1f) else ((pr - 0.82f) / 0.18f).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * pr))
        )
        Box(
            Modifier
                .offset { IntOffset(l.dp.roundToPx(), tp.dp.roundToPx()) }
                .size(w.dp, h.dp)
                .graphicsLayer {
                    shape = RoundedCornerShape(radius.dp)
                    clip = true
                    shadowElevation = 30.dp.toPx() * (1f - pr * 0.6f)
                }
                .background(anim.app.tint)
        ) {
            AppArtwork(anim.app)
            if (heroArt != null && artAlpha > 0f) {
                Image(
                    heroArt.full, null,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = artAlpha },
                    contentScale = ContentScale.Crop,
                )
                // App mark settles in the centre of the "launch screen".
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size((HomeLayout.iconW(6) * 1.6f).dp, (HomeLayout.iconH(6) * 1.6f).dp)
                        .graphicsLayer { alpha = artAlpha; shape = RoundedCornerShape(20.dp); clip = true; shadowElevation = 24.dp.toPx() }
                ) { AppArtwork(anim.app) }
            }
            if (blackout > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = blackout))
                )
            }
        }
    }
}
