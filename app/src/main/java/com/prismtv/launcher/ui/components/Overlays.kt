package com.prismtv.launcher.ui.components

import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.FastOutSlowInEasing
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
                .glass(RoundedCornerShape(32.dp), theme.sheetFill(), GlassSource.Full, elevation = 40.dp)
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
                .glass(RoundedCornerShape(24.dp), theme.sheetFill(), GlassSource.Full, elevation = 24.dp)
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

/**
 * tvOS-style app launch. The focused icon presses in, then springs open into a full-screen launch
 * window (the app's backdrop with its icon centred) while the Home Screen recedes behind it. The app
 * is started part-way through so its own start-up overlaps the motion and it fades in over the
 * launch window. Coming back runs the same motion in reverse: the window shrinks back into its icon.
 */
@Composable
fun LaunchOverlay(vm: LauncherViewModel) {
    val anim = vm.launchAnim ?: return
    val progress = remember(anim.id) { Animatable(if (anim.reverse) 1f else 0f) }
    val press = remember(anim.id) { Animatable(1f) }
    val art = anim.art
    LaunchedEffect(anim.id) {
        if (art != null) vm.requestArt(art)
        launch { snapshotFlow { progress.value }.collect { vm.launchProgress = it } }
        if (anim.reverse) {
            progress.animateTo(0f, spring(dampingRatio = 0.88f, stiffness = 200f, visibilityThreshold = 0.002f))
        } else {
            launch {
                snapshotFlow { progress.value }.first { it >= 0.45f }
                vm.onLaunchMidpoint(anim)
            }
            press.animateTo(0.92f, tween(120, easing = FastOutSlowInEasing))
            launch { press.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
            progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 165f, visibilityThreshold = 0.002f))
        }
        vm.onLaunchZoomComplete(anim)
    }
    val heroArt = art?.let { vm.artCache[it.key] }
    val pr = progress.value
    val pc = pr.coerceIn(0f, 1f)
    val ps = press.value

    // The focused tile is drawn enlarged on the grid, so start from its focused size.
    val fs = HomeLayout.FocusScale
    val from = anim.from
    val fw = from.width * fs
    val fh = from.height * fs
    val fl = from.center.x - fw / 2f
    val ft = from.center.y - fh / 2f

    // Window: tile → full screen (allowed to overshoot a touch for a springy settle).
    val wp = pr.coerceAtMost(1.015f)
    val wl = lerpF(fl, 0f, wp)
    val wt = lerpF(ft, 0f, wp)
    val ww = lerpF(fw, HomeLayout.ScreenW, wp)
    val wh = lerpF(fh, HomeLayout.ScreenH, wp)
    val wRadius = lerpF(13f * fs, 0f, pc)

    // Icon: tile → large centred mark on the launch window.
    val iw1 = 300f
    val ih1 = iw1 * fh / fw
    val il = lerpF(fl, (HomeLayout.ScreenW - iw1) / 2f, pr)
    val itp = lerpF(ft, (HomeLayout.ScreenH - ih1) / 2f, pr)
    val iw = lerpF(fw, iw1, pr)
    val ih = lerpF(fh, ih1, pr)
    val iRadius = lerpF(13f * fs, 24f, pc)

    // The window's own backdrop fades up behind the icon as it opens.
    val bgAlpha = (pc / 0.38f).coerceIn(0f, 1f)
    val dim = 0.42f * pc

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim))
        )
        // Launch window.
        Box(
            Modifier
                .offset { IntOffset(wl.dp.roundToPx(), wt.dp.roundToPx()) }
                .size(ww.dp, wh.dp)
                .graphicsLayer {
                    scaleX = ps
                    scaleY = ps
                    alpha = bgAlpha
                    shape = RoundedCornerShape(wRadius.dp)
                    clip = true
                    shadowElevation = 40.dp.toPx() * (1f - pc * 0.7f)
                }
                .background(
                    Brush.verticalGradient(
                        listOf(lightenColor(anim.app.tint, 0.10f), darkenColor(anim.app.tint, 0.35f))
                    )
                )
        ) {
            if (heroArt != null) {
                Image(
                    heroArt.heavy, null,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.9f },
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))))
            )
        }
        // The icon itself, growing out of the grid.
        Box(
            Modifier
                .offset { IntOffset(il.dp.roundToPx(), itp.dp.roundToPx()) }
                .size(iw.dp, ih.dp)
                .graphicsLayer {
                    scaleX = ps
                    scaleY = ps
                    shape = RoundedCornerShape(iRadius.dp)
                    clip = true
                    shadowElevation = 28.dp.toPx()
                    ambientShadowColor = anim.app.tint
                    spotShadowColor = anim.app.tint
                }
                .drawWithContent {
                    drawContent()
                    // Glass rim catching the light as it grows.
                    drawRoundRect(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.25f)),
                            start = Offset.Zero, end = Offset(size.width, size.height),
                        ),
                        cornerRadius = CornerRadius(iRadius.dp.toPx()),
                        style = Stroke(1.5.dp.toPx()),
                    )
                }
        ) {
            AppArtwork(anim.app)
        }
    }
}

private fun lightenColor(c: Color, f: Float) = Color(c.red + (1f - c.red) * f, c.green + (1f - c.green) * f, c.blue + (1f - c.blue) * f, 1f)
private fun darkenColor(c: Color, f: Float) = Color(c.red * (1f - f), c.green * (1f - f), c.blue * (1f - f), 1f)
