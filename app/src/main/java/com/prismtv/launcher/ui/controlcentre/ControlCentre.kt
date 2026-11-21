package com.prismtv.launcher.ui.controlcentre

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import com.prismtv.launcher.system.PrismNotification
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.CC
import com.prismtv.launcher.LauncherViewModel
import com.prismtv.launcher.data.label
import com.prismtv.launcher.ui.home.WeatherGlyph
import com.prismtv.launcher.Overlay
import com.prismtv.launcher.system.SystemActions
import com.prismtv.launcher.ui.components.Avatar
import com.prismtv.launcher.ui.components.HSpace
import com.prismtv.launcher.ui.components.PIcon
import com.prismtv.launcher.ui.components.PText
import com.prismtv.launcher.ui.components.VSpace
import com.prismtv.launcher.ui.components.lerpF
import com.prismtv.launcher.ui.glass.GlassSource
import com.prismtv.launcher.ui.glass.glass
import com.prismtv.launcher.ui.theme.LocalPrism

private const val ClusterW = 252f
private const val ClusterLeft = 960f - 34f - ClusterW
private const val TilesTop = 44f
private const val Big = 110f
private const val PillW = ClusterW - Big - 7f
private const val PillH = 50f
private const val Gutter = 7f
private const val Circle = 46f
private const val CirclesTop = TilesTop + Big + Gutter + PillH + 9f
private const val NotesTop = CirclesTop + Circle + 40f
private const val NoteH = 54f

private val captions = mapOf(
    CC.Picture to "Picture & Display",
    CC.Memory to "Free Up Memory",
    CC.Access to "Accessibility",
    CC.Profile to "Accounts",
)

/**
 * Control Centre: quick TV controls in floating glass, in the top-right corner. Navigation (Search,
 * Recent Apps, Settings) lives in the sidebar capsule, so nothing here doubles up with it.
 */
@Composable
fun ControlCentre(vm: LauncherViewModel, clock: String) {
    val visible = vm.overlay == Overlay.ControlCentre
    val t by animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.84f, stiffness = 280f), label = "cc")
    if (t < 0.002f) return
    val theme = LocalPrism.current
    val p = theme.palette
    val ctx = LocalContext.current
    val net = remember(vm.resumeTick, visible) { SystemActions.network(ctx) }
    val f = vm.ccFocus
    // Header and caption sit straight on the dimmed backdrop, not on glass, so they're always white
    // (whatever the Glass Colour) with a soft shadow, over a shade that darkens the corner behind them.
    val ink = Color.White
    val weather = if (vm.prefs.weather) vm.weather else null

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = t }
                .background(Color(0xFF1B1C20).copy(alpha = if (p.isDark) 0.46f else 0.30f))
                .background(
                    Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent),
                        center = Offset(Float.POSITIVE_INFINITY, 0f),
                        radius = 1100f,
                    )
                )
        )
        Box(
            Modifier
                .offset(ClusterLeft.dp, 24.dp)
                .size(ClusterW.dp, 500.dp)
                .graphicsLayer {
                    alpha = t
                    val s = lerpF(0.86f, 1f, t)
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(1f, 0f)
                }
        ) {
            // Header: the time pill unfolds into the Control Centre header.
            Row(
                Modifier
                    .size(ClusterW.dp, 32.dp)
                    .padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PText(clock, 16.sp, FontWeight.SemiBold, ink, shadow = true)
                if (weather != null) {
                    HSpace(10.dp)
                    WeatherGlyph(weather, 17.dp)
                    HSpace(4.dp)
                    PText(weather.label(vm.prefs.temperatureUnit), 15.sp, FontWeight.SemiBold, ink, shadow = true)
                    if (weather.place.isNotBlank()) {
                        HSpace(6.dp)
                        PText(weather.place, 12.sp, FontWeight.Medium, ink.copy(alpha = 0.78f), shadow = true)
                    }
                }
                Box(Modifier.weight(1f))
                Avatar(30.dp, focused = f == CC.Profile)
            }

            Tile(f == CC.Power, Modifier.offset(0.dp, TilesTop.dp).size(Big.dp, Big.dp), RoundedCornerShape(28.dp)) { c ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    PIcon(Icons.Rounded.PowerSettingsNew, c, 50.dp)
                    VSpace(6.dp)
                    PText("Power", 13.sp, FontWeight.SemiBold, c)
                }
            }
            PillTile(
                f == CC.WiFi, Modifier.offset((Big + Gutter).dp, TilesTop.dp),
                icon = when {
                    net.ethernet -> Icons.Rounded.SettingsEthernet
                    net.connected -> Icons.Rounded.Wifi
                    else -> Icons.Rounded.WifiOff
                },
                iconColor = if (net.connected) Color(0xFF0A84FF) else null,
                circled = true,
                title = net.title, subtitle = if (net.connected && !net.ethernet) "On" else net.subtitle,
            )
            PillTile(
                f == CC.Bluetooth, Modifier.offset((Big + Gutter).dp, (TilesTop + PillH + Gutter).dp),
                icon = Icons.Rounded.Bluetooth,
                iconColor = Color(0xFF0A84FF),
                circled = true,
                title = "Bluetooth", subtitle = "Remotes & More",
            )
            val output = remember(vm.resumeTick, visible) { SystemActions.audioOutput(ctx) }
            PillTile(
                f == CC.Sound, Modifier.offset((Big + Gutter).dp, (TilesTop + Big + Gutter).dp),
                icon = Icons.AutoMirrored.Rounded.VolumeUp, iconColor = Color(0xFFFF375F), circled = true,
                title = "Sound", subtitle = output,
            )
            PillTile(
                f == CC.Sleep, Modifier.offset(0.dp, (TilesTop + Big + Gutter).dp), width = Big,
                icon = Icons.Rounded.Timer,
                iconColor = if (vm.sleepMinutes > 0) Color(0xFFFF9F0A) else null,
                circled = true,
                title = "Sleep", subtitle = if (vm.sleepMinutes > 0) vm.sleepLabel() else "Timer", subtitleStrong = vm.sleepMinutes == 0,
            )
            val circles = listOf(
                CC.Picture to Icons.Rounded.Tv,
                CC.Memory to Icons.Rounded.Memory,
                CC.Access to Icons.Rounded.AccessibilityNew,
            )
            val step = (ClusterW - Circle) / 2f
            circles.forEachIndexed { i, (id, icon) ->
                Tile(
                    f == id,
                    Modifier
                        .offset((step * i).dp, CirclesTop.dp)
                        .size(Circle.dp),
                    CircleShape,
                ) { c ->
                    PIcon(icon, c, 24.dp, Modifier.align(Alignment.Center))
                }
            }
            // Caption for icon-only controls, like the tvOS focus label.
            val caption = captions[f]
            PText(
                caption ?: "", 13.sp, FontWeight.SemiBold, ink,
                Modifier
                    .offset(0.dp, (CirclesTop + Circle + 12f).dp)
                    .size(ClusterW.dp, 20.dp)
                    .graphicsLayer { alpha = if (caption != null) 1f else 0f },
                align = TextAlign.Center, shadow = true,
            )
            // Notifications: the latest three, newest first. Select opens one; hold OK clears it.
            vm.notifications.take(3).forEachIndexed { i, n ->
                NotificationTile(
                    n, f == CC.Notification + i,
                    Modifier
                        .offset(0.dp, (NotesTop + i * (NoteH + Gutter)).dp)
                        .size(ClusterW.dp, NoteH.dp),
                )
            }
            val more = vm.notifications.size - 3
            if (more > 0) {
                PText(
                    "$more more", 12.sp, FontWeight.Medium, ink.copy(alpha = 0.75f),
                    Modifier.offset(6.dp, (NotesTop + 3 * (NoteH + Gutter)).dp), shadow = true,
                )
            }
        }
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, focused: Boolean) {
    val theme = LocalPrism.current
    val scale by animateFloatAsState(if (focused) 1.16f else 1f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "hdr")
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(30.dp)
            .then(if (focused) Modifier.shadow(10.dp, CircleShape) else Modifier)
            .then(
                if (focused) Modifier.background(theme.controlFocused(), CircleShape)
                else Modifier.glass(CircleShape, theme.controlGlass(), GlassSource.Scene, elevation = 4.dp)
            ),
        contentAlignment = Alignment.Center,
    ) {
        PIcon(icon, if (focused) theme.palette.textFocused else if (theme.palette.isDark) Color.White else theme.palette.textPrimary, 18.dp)
    }
}

@Composable
private fun Tile(
    focused: Boolean,
    modifier: Modifier,
    shape: Shape,
    content: @Composable BoxScope.(Color) -> Unit,
) {
    val theme = LocalPrism.current
    val p = theme.palette
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, spring(dampingRatio = 0.62f, stiffness = 420f), label = "tile")
    val fg = if (focused) p.textFocused else if (p.isDark) Color.White else p.textPrimary
    // The lens samples the undimmed Home Screen, so the veil carries the scrim's dimming: controls stay
    // legible over busy app grids while still bending what's behind them.
    val tint = theme.overlayVeil(dark = 0.40f, light = 0.56f)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (focused) Modifier.shadow(20.dp, shape).background(theme.controlFocused(), shape)
                else Modifier.glass(shape, tint, GlassSource.Scene, elevation = 6.dp)
            )
    ) {
        content(fg)
    }
}

@Composable
private fun PillTile(
    focused: Boolean,
    modifier: Modifier,
    icon: ImageVector,
    iconColor: Color?,
    title: String,
    subtitle: String?,
    width: Float = PillW,
    circled: Boolean = false,
    subtitleStrong: Boolean = false,
) {
    Tile(focused, modifier.size(width.dp, PillH.dp), RoundedCornerShape(50)) { fg ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (circled) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(iconColor ?: if (focused) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.9f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    PIcon(icon, if (iconColor != null) Color.White else Color(0xFF1C1C1E), 17.dp)
                }
            } else {
                PIcon(icon, iconColor ?: fg, 26.dp)
            }
            HSpace(8.dp)
            Column {
                PText(title, 12.sp, FontWeight.Medium, fg, lineHeight = 14.sp)
                if (subtitle != null) {
                    PText(
                        subtitle, 11.5.sp, if (subtitleStrong) FontWeight.Medium else FontWeight.Normal,
                        if (subtitleStrong) fg else fg.copy(alpha = 0.68f), lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationTile(n: PrismNotification, focused: Boolean, modifier: Modifier) {
    Tile(focused, modifier, RoundedCornerShape(20.dp)) { c ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = n.icon
            if (icon != null) {
                androidx.compose.foundation.Image(
                    icon, null,
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(Color(0xFFFF3B30), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { PIcon(Icons.Rounded.Notifications, Color.White, 18.dp) }
            }
            HSpace(10.dp)
            Column(Modifier.weight(1f)) {
                PText(n.title, 12.sp, FontWeight.SemiBold, c)
                PText(n.text.ifBlank { n.appLabel }, 11.sp, FontWeight.Normal, c.copy(alpha = 0.72f))
            }
        }
    }
}
