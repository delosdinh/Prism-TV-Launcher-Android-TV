package com.prismtv.launcher.ui.home

import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
    SidebarEntry.Screensaver -> Icons.Rounded.Landscape
    SidebarEntry.PrismSettings -> Icons.Rounded.Tune
    SidebarEntry.SystemSettings -> Icons.Rounded.Settings
}

/** Left-hand glass capsule (as in the tvOS 27 TV app sidebar). Press Left on the first app to open. */
@Composable
fun Sidebar(vm: LauncherViewModel, clock: String) {
    val visible = vm.overlay == Overlay.Sidebar
    val t by animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 300f), label = "sidebar")
    if (t < 0.002f) return
    val theme = LocalPrism.current
    val p = theme.palette
    val ctx = LocalContext.current
    val deviceName = remember {
        runCatching { Settings.Global.getString(ctx.contentResolver, "device_name") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL
    }
    val fg = if (p.isDark) Color.White else p.textPrimary

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = t }
                .background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.50f), 0.55f to Color.Transparent))
        )
        Column(
            Modifier
                .offset { IntOffset(lerpF(-300f, 18f, t).dp.roundToPx(), 18.dp.roundToPx()) }
                .size(264.dp, 504.dp)
                .graphicsLayer { alpha = t }
                .glass(RoundedCornerShape(40.dp), theme.controlGlass(), GlassSource.Scene, elevation = 30.dp)
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(start = 6.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(38.dp)
                HSpace(12.dp)
                PText(deviceName, 17.sp, FontWeight.SemiBold, fg, Modifier.weight(1f))
                PText(clock, 14.sp, FontWeight.Medium, fg.copy(alpha = 0.7f))
            }
            VSpace(14.dp)
            SidebarEntry.entries.forEachIndexed { i, e ->
                if (e == SidebarEntry.PrismSettings) {
                    VSpace(10.dp)
                    PText("Settings", 13.sp, FontWeight.SemiBold, fg.copy(alpha = 0.55f), Modifier.padding(start = 16.dp, bottom = 6.dp))
                }
                SidebarRow(e.icon(), e.label, focused = visible && i == vm.sidebarFocus)
            }
        }
    }
}

@Composable
private fun SidebarRow(icon: ImageVector, label: String, focused: Boolean) {
    val theme = LocalPrism.current
    val p = theme.palette
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, spring(dampingRatio = 0.7f, stiffness = 420f), label = "sideRow")
    val shape = RoundedCornerShape(18.dp)
    val fg = if (focused) p.textFocused else if (p.isDark) Color.White else p.textPrimary
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.shadow(14.dp, shape).background(theme.controlFocused(), shape) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PIcon(icon, fg, 24.dp)
        HSpace(14.dp)
        PText(label, 17.sp, if (focused) FontWeight.SemiBold else FontWeight.Medium, fg)
    }
}
