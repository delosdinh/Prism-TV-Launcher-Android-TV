package com.prismtv.launcher.ui.home

import com.prismtv.launcher.ui.components.atLeast
import android.provider.Settings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.LauncherViewModel
import com.prismtv.launcher.Overlay
import com.prismtv.launcher.SidebarEntry
import com.prismtv.launcher.ui.components.Avatar
import com.prismtv.launcher.ui.components.HSpace
import com.prismtv.launcher.ui.components.PIcon
import com.prismtv.launcher.ui.components.PText
import com.prismtv.launcher.ui.components.VSpace
import com.prismtv.launcher.ui.components.lerpF
import com.prismtv.launcher.ui.glass.GlassSource
import com.prismtv.launcher.ui.glass.glass
import com.prismtv.launcher.ui.theme.LocalPrism

private fun SidebarEntry.icon(): ImageVector = when (this) {
    SidebarEntry.Search -> Icons.Rounded.Search
    SidebarEntry.Home -> Icons.Rounded.Home
    SidebarEntry.Recents -> Icons.Rounded.ViewCarousel
    SidebarEntry.PrismSettings -> Icons.Rounded.Tune
    SidebarEntry.SystemSettings -> Icons.Rounded.Settings
    SidebarEntry.Store -> Icons.Rounded.Shop
}

/** Left-hand glass capsule (as in the tvOS 27 TV app sidebar). Press Left on the first app to open. */
@Composable
fun Sidebar(vm: LauncherViewModel, clock: String) {
    val visible = vm.overlay == Overlay.Sidebar
    val tState = animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 300f), label = "sidebar")
    val t by tState
    if (!tState.atLeast(0.002f)) return
    val theme = LocalPrism.current
    val p = theme.palette
    val deviceName = vm.profileName()
    val fg = if (p.isDark) Color.White else p.textPrimary
    // Clear lens over the Home Screen, with a veil that matches the scrim so the list stays legible.
    val panelTint = theme.overlayVeil(dark = 0.44f, light = 0.55f)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = t }
                .background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.62f), 0.38f to Color.Black.copy(alpha = 0.30f), 0.75f to Color.Transparent))
        )
        // Proportions measured from the tvOS 26 sidebar: a narrow rounded rectangle, as tall as its
        // entries, with compact rows whose focus is a full pill.
        Column(
            Modifier
                .offset { IntOffset(lerpF(-220f, 19f, t).dp.roundToPx(), 18.dp.roundToPx()) }
                .width(180.dp)
                .graphicsLayer { alpha = t }
                .glass(RoundedCornerShape(22.dp), panelTint, GlassSource.Scene, elevation = 30.dp)
                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 6.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(start = 4.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(26.dp)
                HSpace(8.dp)
                PText(deviceName, 13.sp, FontWeight.Medium, fg, Modifier.weight(1f))
                HSpace(6.dp)
                PText(clock, 11.sp, FontWeight.Medium, fg.copy(alpha = 0.6f))
            }
            VSpace(14.dp)
            vm.sidebarEntries.forEachIndexed { i, e ->
                if (e == SidebarEntry.PrismSettings) {
                    VSpace(4.dp)
                    PText("Settings", 11.sp, FontWeight.SemiBold, fg.copy(alpha = 0.5f), Modifier.padding(start = 10.dp, bottom = 2.dp))
                }
                if (e == SidebarEntry.Store) {
                    // The store's own coloured icon, like an app.
                    val store = vm.storeApp
                    SidebarRow(e.icon(), if (store?.packageName == "com.android.vending") e.label else store?.label ?: e.label,
                        focused = visible && i == vm.sidebarFocus, image = store?.icon)
                } else {
                    SidebarRow(e.icon(), e.label, focused = visible && i == vm.sidebarFocus)
                }
            }
        }
    }
}

@Composable
private fun SidebarRow(icon: ImageVector, label: String, focused: Boolean, image: androidx.compose.ui.graphics.ImageBitmap? = null) {
    val theme = LocalPrism.current
    val p = theme.palette
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, spring(dampingRatio = 0.7f, stiffness = 420f), label = "sideRow")
    val shape = RoundedCornerShape(50)
    val fg = if (focused) p.textFocused else if (p.isDark) Color.White else p.textPrimary
    // A 38dp slot holding a 33dp pill, as on tvOS.
    Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(33.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .then(if (focused) Modifier.shadow(12.dp, shape).background(theme.controlFocused(), shape) else Modifier)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (image != null) {
                androidx.compose.foundation.Image(
                    image, null,
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(5.dp)),
                )
            } else {
                PIcon(icon, fg, 17.dp)
            }
            HSpace(9.dp)
            PText(label, 13.sp, FontWeight.Medium, fg)
        }
    }
}
