package com.prismtv.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.layer.drawLayer
import com.prismtv.launcher.ui.glass.SupportsLiveBlur
import com.prismtv.launcher.ui.glass.LocalBackdrop
import com.prismtv.launcher.LaunchAnim
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalView
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
 * tvOS-style app launch (matched frame-by-frame to tvOS). The icon presses in, then grows straight
 * out of its slot into a full-screen window while the Home Screen behind it blurs; the icon artwork
 * dissolves into the app's launch colour as it grows. Part-way through, the real app is started with
 * its window scaling up from the icon's current rect, so the app itself appears to open out of the
 * tile. Returning runs it backwards: a full-screen window shrinks back into the icon and Home sharpens.
 */
@Composable
fun LaunchOverlay(vm: LauncherViewModel) {
    val anim = vm.launchAnim ?: return
    val progress = remember(anim.id) { Animatable(if (anim.reverse) 1f else 0f) }
    val press = remember(anim.id) { Animatable(1f) }
    val backdrop = LocalBackdrop.current
    LaunchedEffect(anim.id) {
        launch { snapshotFlow { progress.value }.collect { vm.launchProgress = it } }
        if (anim.reverse) {
            progress.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 300f, visibilityThreshold = 0.002f))
        } else {
            launch {
                // Start the app as the tile nears full screen; it fades in over the grown artwork, so no
                // empty launch window (often black) ever shows inside the tile.
                snapshotFlow { progress.value }.first { it >= 0.55f }
                vm.onLaunchMidpoint(anim, null, null)
            }
            press.animateTo(0.93f, tween(90, easing = FastOutSlowInEasing))
            launch { press.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
            progress.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 300f, visibilityThreshold = 0.002f))
        }
        vm.onLaunchZoomComplete(anim)
    }
    val pr = progress.value
    val pc = pr.coerceIn(0f, 1f)
    val ps = press.value
    val w = windowRect(anim, pr)
    val fs = HomeLayout.FocusScale
    // Rounded like the icon while it travels; square only as it lands full screen.
    val radius = 13f * fs * (1f - smoothstep(0.78f, 1f, pc)) + 8f * smoothstep(0.1f, 0.5f, pc) * (1f - smoothstep(0.7f, 1f, pc))
    val blur = smoothstep(0f, 0.55f, pc)

    Box(Modifier.fillMaxSize()) {
        // Home blurs behind the growing window.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = blur }
                .drawBehind {
                    if (SupportsLiveBlur) {
                        drawLayer(backdrop.fullBlur)
                    } else {
                        val img = backdrop.fallbackHeavy
                        if (img != null) {
                            drawImage(
                                img, IntOffset.Zero, IntSize(img.width, img.height),
                                IntOffset.Zero, IntSize(size.width.toInt(), size.height.toInt()),
                            )
                        }
                    }
                    drawRect(Color.Black.copy(alpha = 0.22f))
                }
        )
        // The window: the icon itself, growing; its artwork dissolves into the launch colour.
        Box(
            Modifier
                .offset { IntOffset(w.left.dp.roundToPx(), w.top.dp.roundToPx()) }
                .size(w.width.dp, w.height.dp)
                .graphicsLayer {
                    scaleX = ps
                    scaleY = ps
                    shape = RoundedCornerShape(radius.dp)
                    clip = true
                    shadowElevation = 34.dp.toPx() * (1f - pc)
                    ambientShadowColor = anim.app.tint
                    spotShadowColor = anim.app.tint
                }
                .background(Color.Black)
        ) {
            // The tile itself grows to fill the screen (Flow / tvOS style); the app fades in over it.
            AppArtwork(anim.app)
            if (pc < 0.9f) {
                // Glass rim catching the light while it travels.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - smoothstep(0.5f, 0.9f, pc) }
                        .drawWithContent {
                            drawRoundRect(
                                Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.22f)),
                                    start = Offset.Zero, end = Offset(size.width, size.height),
                                ),
                                cornerRadius = CornerRadius(radius.dp.toPx()),
                                style = Stroke(1.5.dp.toPx()),
                            )
                        }
                )
            }
        }
    }
}

/** The window's rect (dp) at progress [p]: from the focused tile to full screen. */
private fun windowRect(anim: LaunchAnim, p: Float): Rect {
    val fs = HomeLayout.FocusScale
    val from = anim.from
    val fw = from.width * fs
    val fh = from.height * fs
    val fl = from.center.x - fw / 2f
    val ft = from.center.y - fh / 2f
    val q = p.coerceAtMost(1.01f)
    val l = lerpF(fl, 0f, q)
    val t = lerpF(ft, 0f, q)
    return Rect(l, t, l + lerpF(fw, HomeLayout.ScreenW, q), t + lerpF(fh, HomeLayout.ScreenH, q))
}

private fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

