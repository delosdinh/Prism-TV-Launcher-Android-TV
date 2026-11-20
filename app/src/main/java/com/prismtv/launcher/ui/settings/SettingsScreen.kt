package com.prismtv.launcher.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.LauncherViewModel
import com.prismtv.launcher.Overlay
import com.prismtv.launcher.SettingsPage
import com.prismtv.launcher.SettingsRow
import com.prismtv.launcher.ui.components.HSpace
import com.prismtv.launcher.ui.components.PIcon
import com.prismtv.launcher.ui.components.PText
import com.prismtv.launcher.ui.components.PrismMark
import com.prismtv.launcher.ui.components.VSpace
import com.prismtv.launcher.ui.components.lerpF
import com.prismtv.launcher.ui.theme.Appearance
import com.prismtv.launcher.ui.theme.LocalPrism
import com.prismtv.launcher.ui.theme.PrismPalette

private val RowShape = RoundedCornerShape(12.dp)
private const val RowH = 40f
private const val RowGap = 6f
private const val HeaderH = 30f
private const val ViewportH = 404f

/** tvOS-style Settings: device artwork on the left, a focused list on the right. */
@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val visible = vm.overlay == Overlay.Settings
    val t by animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 320f), label = "settings")
    if (t < 0.002f) return
    val theme = LocalPrism.current
    val p = theme.palette
    val page = vm.settingsStack.last()

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = t
                val s = lerpF(1.04f, 1f, t)
                scaleX = s
                scaleY = s
            }
            .background(Brush.linearGradient(listOf(p.bgAccent, p.bgBase, p.bgBase)))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(p.bgAccent.copy(alpha = 0.65f), Color.Transparent),
                        center = Offset(size.width * 0.24f, size.height * 0.5f),
                        radius = size.width * 0.5f,
                    )
                )
            }
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                (fadeIn(tween(260)) + slideInHorizontally(tween(320)) { w -> if (forward) w / 10 else -w / 10 })
                    .togetherWith(fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { w -> if (forward) -w / 10 else w / 10 })
            },
            label = "settingsPage",
        ) { pg ->
            Box(Modifier.fillMaxSize()) {
                PText(
                    pg.title, 26.sp, FontWeight.SemiBold, p.textPrimary,
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 30.dp),
                )
                DeviceArt(
                    Modifier
                        .offset(126.dp, 156.dp)
                        .size(228.dp),
                    p,
                )
                val rows = vm.settingsRows(pg)
                val focus = (vm.settingsFocus[pg] ?: 0).coerceIn(0, (rows.size - 1).coerceAtLeast(0))
                SettingsList(
                    rows = rows,
                    focus = if (pg == page) focus else -1,
                    modifier = Modifier
                        .offset(458.dp, 96.dp)
                        .size(456.dp, ViewportH.dp),
                )
            }
        }
    }
}

@Composable
private fun DeviceArt(modifier: Modifier, p: PrismPalette) {
    val shape = RoundedCornerShape(54.dp)
    val colors = when (p.appearance) {
        Appearance.Light -> listOf(Color(0xFFFFFFFF), Color(0xFFE1E4EE))
        Appearance.Balanced -> listOf(Color(0xFFB9BCE3), Color(0xFF8D91C2))
        Appearance.Dark -> listOf(Color(0xFF4B4F5A), Color(0xFF2A2D35))
    }
    Box(
        modifier
            .shadow(40.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.45f))
            .background(Brush.verticalGradient(colors), shape)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f))), shape),
        contentAlignment = Alignment.Center,
    ) {
        PrismMark(
            Modifier.size(128.dp, 84.dp),
            color = if (p.appearance == Appearance.Dark) Color(0xFF15161A) else Color(0xFF2C2F3A),
        )
    }
}

@Composable
private fun SettingsList(rows: List<SettingsRow>, focus: Int, modifier: Modifier) {
    // Pre-compute row positions (section headers take extra space).
    val ys = FloatArray(rows.size)
    var y = 0f
    var last: String? = "\u0000"
    rows.forEachIndexed { i, r ->
        if (r.section != last) {
            if (r.section != null) y += HeaderH
            last = r.section
        }
        ys[i] = y
        y += RowH + RowGap
    }
    val total = y
    val target = if (focus < 0 || rows.isEmpty()) 0f else (ys[focus] - 150f).coerceIn(0f, (total - ViewportH + 12f).coerceAtLeast(0f))
    val scroll by animateDpAsState(target.dp, spring(dampingRatio = 0.9f, stiffness = 300f), label = "settingsScroll")

    Box(modifier.clipToBounds()) {
        Column(
            Modifier
                .padding(horizontal = 12.dp)
                .offset { IntOffset(0, -scroll.roundToPx() + 6.dp.roundToPx()) }
        ) {
            var section: String? = "\u0000"
            rows.forEachIndexed { i, r ->
                if (r.section != section) {
                    section = r.section
                    val s = r.section
                    if (s != null) SectionHeader(s)
                }
                SettingsRowView(r, i == focus)
                VSpace(RowGap.dp)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val p = LocalPrism.current.palette
    Box(Modifier.fillMaxWidth().height(HeaderH.dp), contentAlignment = Alignment.BottomStart) {
        PText(
            title, 11.sp, FontWeight.SemiBold, p.textSecondary,
            Modifier.padding(start = 14.dp, bottom = 7.dp),
            letterSpacing = 0.7.sp,
        )
    }
}

@Composable
private fun SettingsRowView(r: SettingsRow, focused: Boolean) {
    val theme = LocalPrism.current
    val p = theme.palette
    val scale by animateFloatAsState(if (focused) 1.035f else 1f, spring(dampingRatio = 0.7f, stiffness = 420f), label = "row")
    val fg = if (focused) p.textFocused else p.textPrimary
    val surface = if (focused) {
        Modifier
            .shadow(16.dp, RowShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.32f))
            .background(theme.controlFocused(), RowShape)
    } else {
        Modifier
            .background(theme.rowFill(), RowShape)
            .border(1.dp, theme.edgeBrush(0.7f), RowShape)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowH.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PText(r.title, 15.sp, if (focused) FontWeight.SemiBold else FontWeight.Medium, fg, Modifier.weight(1f))
        val value = r.value
        if (value != null) {
            PText(value, 14.sp, FontWeight.Normal, if (focused) p.textFocused.copy(alpha = 0.55f) else p.textSecondary)
        }
        if (r.selectable && r.checked) {
            HSpace(8.dp)
            PIcon(Icons.Rounded.Check, if (focused) p.textFocused else p.textPrimary, 18.dp)
        }
        if (r.chevron) {
            HSpace(6.dp)
            PIcon(Icons.Rounded.ChevronRight, if (focused) p.textFocused.copy(alpha = 0.5f) else p.textSecondary, 18.dp)
        }
    }
}

@Suppress("unused")
private fun Dp.px(): Dp = this
