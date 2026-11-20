package com.prismtv.launcher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.ContextMenuState
import com.prismtv.launcher.LauncherViewModel
import com.prismtv.launcher.MenuAction
import com.prismtv.launcher.data.AppItem
import com.prismtv.launcher.data.FolderItem
import com.prismtv.launcher.ui.HomeLayout
import com.prismtv.launcher.ui.components.AppArtwork
import com.prismtv.launcher.ui.components.FolderArtwork
import com.prismtv.launcher.ui.components.PIcon
import com.prismtv.launcher.ui.components.PText
import com.prismtv.launcher.ui.components.lerpF
import com.prismtv.launcher.ui.glass.GlassSource
import com.prismtv.launcher.ui.glass.glass
import com.prismtv.launcher.ui.theme.LocalPrism

private const val MenuW = 272f
private const val RowH = 42f
private const val TitleH = 32f
private const val Pad = 8f
private val Destructive = Color(0xFFFF453A)

/** Hold-OK context menu, anchored to the focused tile like the tvOS / iOS 27 menus. */
@Composable
fun ContextMenuHost(vm: LauncherViewModel) {
    val holder = remember { arrayOfNulls<ContextMenuState>(1) }
    val live = vm.contextMenu
    if (live != null) holder[0] = live
    val m = holder[0] ?: return
    val t by animateFloatAsState(if (live != null) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 420f), label = "menu")
    if (t < 0.002f) return
    val theme = LocalPrism.current
    val p = theme.palette

    val a = m.anchor
    val h = TitleH + m.actions.size * RowH + Pad * 2
    val gap = 16f
    val grow = a.height * (HomeLayout.FocusScale - 1f) / 2f
    val growX = a.width * (HomeLayout.FocusScale - 1f) / 2f
    // Below the tile if it fits, else above, else beside it (vertically centred on the tile).
    val fitsBelow = a.bottom + grow + gap + h < HomeLayout.ScreenH - 10f
    val fitsAbove = a.top - grow - gap - h > 10f
    val beside = !fitsBelow && !fitsAbove
    val onRight = a.center.x < HomeLayout.ScreenW / 2f
    val top = when {
        fitsBelow -> a.bottom + grow + gap
        fitsAbove -> a.top - grow - gap - h
        else -> (a.center.y - h / 2f).coerceIn(10f, HomeLayout.ScreenH - 10f - h)
    }
    val left = when {
        !beside -> (a.center.x - MenuW / 2f).coerceIn(18f, HomeLayout.ScreenW - 18f - MenuW)
        onRight -> a.right + growX + gap
        else -> a.left - growX - gap - MenuW
    }
    val origin = when {
        beside -> TransformOrigin(if (onRight) 0f else 1f, 0.5f)
        fitsBelow -> TransformOrigin(0.5f, 0f)
        else -> TransformOrigin(0.5f, 1f)
    }

    // The lens samples the undimmed screen, so the veil carries the scrim's dimming for legibility.
    val menuTint = if (theme.liquid) {
        if (p.isDark) Color.Black.copy(alpha = 0.52f) else Color.White.copy(alpha = 0.60f)
    } else theme.controlGlass()

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = t }
                .background(Color.Black.copy(alpha = 0.36f))
        )
        // The held tile stays lifted above the dimmed screen, as in tvOS.
        Box(
            Modifier
                .offset(a.left.dp, a.top.dp)
                .size(a.width.dp, a.height.dp)
                .graphicsLayer {
                    alpha = t
                    val s = HomeLayout.FocusScale + 0.04f * t
                    scaleX = s
                    scaleY = s
                    shadowElevation = 30.dp.toPx()
                    shape = RoundedCornerShape(13.dp)
                    clip = true
                }
        ) {
            when (val item = m.item) {
                is AppItem -> AppArtwork(item.app)
                is FolderItem -> FolderArtwork(item, plate = Color.White.copy(alpha = if (p.isDark) 0.22f else 0.45f))
            }
        }
        Column(
            Modifier
                .offset(left.dp, top.dp)
                .width(MenuW.dp)
                .graphicsLayer {
                    alpha = t
                    val s = lerpF(0.82f, 1f, t)
                    scaleX = s
                    scaleY = s
                    transformOrigin = origin
                }
                .glass(RoundedCornerShape(26.dp), menuTint, GlassSource.Full, elevation = 28.dp)
                .padding(Pad.dp)
        ) {
            PText(
                m.title, 12.sp, FontWeight.SemiBold,
                if (p.isDark) Color.White.copy(alpha = 0.62f) else p.textSecondary,
                Modifier
                    .height(TitleH.dp)
                    .padding(start = 14.dp, top = 9.dp),
            )
            m.actions.forEachIndexed { i, action ->
                MenuRow(action, focused = live != null && i == vm.contextFocus)
            }
        }
    }
}

@Composable
private fun MenuRow(action: MenuAction, focused: Boolean) {
    val theme = LocalPrism.current
    val p = theme.palette
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, spring(dampingRatio = 0.7f, stiffness = 480f), label = "menuRow")
    val shape = RoundedCornerShape(15.dp)
    val base = if (p.isDark) Color.White else p.textPrimary
    val fg = when {
        action.destructive -> Destructive
        focused -> p.textFocused
        else -> base
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowH.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.shadow(12.dp, shape).background(theme.controlFocused(), shape) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PText(action.label, 15.sp, if (focused) FontWeight.SemiBold else FontWeight.Medium, fg, Modifier.weight(1f))
        PIcon(action.icon, fg, 19.dp)
    }
}
