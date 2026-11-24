package com.prismtv.launcher.ui.controlcentre

import androidx.compose.animation.core.animateFloatAsState
import com.prismtv.launcher.ui.components.atLeast
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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Close
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
private const val NotesTop = CirclesTop + Circle + 36f
private const val NotesHeaderH = 24f
private const val NoteH = 50f
private const val ClearW = 62f
private const val NowTop = CirclesTop + Circle + 38f
private const val NowH = 72f

private val captions = mapOf(
    CC.Picture to "Picture & Display",
    CC.Memory to "Free Up Memory",
    CC.Access to "Accessibility",
    CC.Screensaver to "Show Screensaver",
)

/**
 * Control Centre: quick TV controls in floating glass, in the top-right corner. Navigation (Search,
 * Recent Apps, Settings) lives in the sidebar capsule, so nothing here doubles up with it.
 */
@Composable
fun ControlCentre(vm: LauncherViewModel, clock: String) {
    val visible = vm.overlay == Overlay.ControlCentre
    val tState = animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.84f, stiffness = 280f), label = "cc")
    val t by tState
    if (!tState.atLeast(0.002f)) return
    if (com.prismtv.launcher.BuildConfig.DEBUG) androidx.compose.runtime.SideEffect { android.util.Log.d("PrismComp", "cc") }
    val theme = LocalPrism.current
    val p = theme.palette
    val ctx = LocalContext.current
    val net = vm.network
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
                CC.Screensaver to Icons.Rounded.Landscape,
            )
            // Four circles spread evenly under the tiles above.
            val step = (ClusterW - Circle) / (circles.size - 1)
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
            // Free Up Memory says what it's doing, then what it freed, in its caption.
            // The profile picture opens Who's Watching? with two or more profiles, else the profile page.
            val caption = when (f) {
                CC.Memory -> vm.memoryCaption ?: captions[f]
                CC.Profile -> if (vm.profileList.size >= 2) "Switch Profile" else "Profile"
                else -> captions[f]
            }
            PText(
                caption ?: "", 13.sp, FontWeight.SemiBold, ink,
                Modifier
                    .offset(0.dp, (CirclesTop + Circle + 12f).dp)
                    .size(ClusterW.dp, 20.dp)
                    .graphicsLayer { alpha = if (caption != null) 1f else 0f },
                align = TextAlign.Center, shadow = true,
            )
            // Now Playing: what another app is playing, with previous / play-pause / next.
            val np = vm.nowPlaying
            if (np != null) {
                NowPlayingTile(
                    np, f,
                    Modifier
                        .offset(0.dp, NowTop.dp)
                        .size(ClusterW.dp, NowH.dp),
                )
            }
            // Notifications: the latest few, newest first, under a header with a small ✕ (Clear All, as on
            // iOS). Select opens one; Left or Right slides it aside to show Clear; hold OK clears it too.
            val shown = vm.ccNotificationsShown
            if (shown > 0) {
                val headerTop = if (np != null) NowTop + NowH + Gutter else NotesTop
                val notesTop = headerTop + NotesHeaderH
                val more = vm.notifications.size - shown
                Row(
                    Modifier
                        .offset(0.dp, headerTop.dp)
                        .size(ClusterW.dp, (NotesHeaderH - 4f).dp)
                        .padding(start = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PText("Notifications", 13.sp, FontWeight.SemiBold, ink, shadow = true)
                    if (more > 0) {
                        HSpace(6.dp)
                        PText("$more more", 11.sp, FontWeight.Medium, ink.copy(alpha = 0.72f), shadow = true)
                    }
                    Box(Modifier.weight(1f))
                    ClearAllButton(f == CC.ClearAll)
                }
                vm.notifications.take(shown).forEachIndexed { i, n ->
                    NotificationTile(
                        n,
                        focused = f == CC.Notification + i,
                        clearing = f == CC.NotificationClear + i,
                        modifier = Modifier
                            .offset(0.dp, (notesTop + i * (NoteH + Gutter)).dp)
                            .size(ClusterW.dp, NoteH.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Tile(
    focused: Boolean,
    modifier: Modifier,
    shape: Shape,
    /**
     * Text cards (notifications, Now Playing) bend a blurred Home Screen, like open folders: bright tiles
     * in the dock behind them soften instead of showing through the text.
     */
    soft: Boolean = false,
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
                else Modifier.glass(shape, tint, GlassSource.Scene, elevation = 6.dp, blurredLens = soft)
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

/** The small ✕ above the notifications; focused, it opens out into "Clear All". */
@Composable
private fun ClearAllButton(focused: Boolean) {
    val theme = LocalPrism.current
    val p = theme.palette
    val open by animateFloatAsState(if (focused) 1f else 0f, spring(dampingRatio = 0.78f, stiffness = 420f), label = "clearAll")
    val h = 20f
    val fg = if (focused) p.textFocused else Color.White
    Box(
        Modifier
            .size(lerpF(h, 76f, open).dp, h.dp)
            .then(
                if (focused) Modifier.shadow(10.dp, CircleShape).background(theme.controlFocused(), CircleShape)
                else Modifier.glass(CircleShape, theme.overlayVeil(dark = 0.40f, light = 0.56f), GlassSource.Scene, elevation = 3.dp)
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PIcon(Icons.Rounded.Close, fg, 12.dp)
            if (open > 0.02f) {
                HSpace((4f * open).dp)
                PText("Clear All", 10.5.sp, FontWeight.SemiBold, fg, Modifier.graphicsLayer { alpha = open }, maxLines = 1)
            }
        }
    }
}

/**
 * A notification. While [clearing], it slides left to show a Clear button beside it (focused), like
 * swiping one aside on iOS.
 */
@Composable
private fun NotificationTile(n: PrismNotification, focused: Boolean, clearing: Boolean, modifier: Modifier) {
    val slide by animateFloatAsState(if (clearing) 1f else 0f, spring(dampingRatio = 0.82f, stiffness = 420f), label = "noteSlide")
    Box(modifier) {
        if (slide > 0.01f) {
            Tile(
                clearing,
                Modifier
                    .offset((ClusterW - ClearW).dp, 0.dp)
                    .size(ClearW.dp, NoteH.dp)
                    .graphicsLayer {
                        alpha = slide
                        val s = lerpF(0.7f, 1f, slide)
                        scaleX = s
                        scaleY = s
                    },
                RoundedCornerShape(20.dp),
                soft = true,
            ) { c ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    PIcon(Icons.Rounded.Close, c, 16.dp)
                    PText("Clear", 11.sp, FontWeight.SemiBold, c)
                }
            }
        }
        NotificationCard(
            n, focused,
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = -(ClearW + Gutter).dp.toPx() * slide },
        )
    }
}

@Composable
private fun NotificationCard(n: PrismNotification, focused: Boolean, modifier: Modifier) {
    Tile(focused, modifier, RoundedCornerShape(20.dp), soft = true) { c ->
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

@Composable
private fun NowPlayingTile(np: com.prismtv.launcher.system.NowPlaying, focus: Int, modifier: Modifier) {
    val theme = LocalPrism.current
    val p = theme.palette
    val fg = if (p.isDark) Color.White else p.textPrimary
    val shape = RoundedCornerShape(22.dp)
    val tint = theme.overlayVeil(dark = 0.40f, light = 0.56f)
    Box(modifier.glass(shape, tint, GlassSource.Scene, elevation = 6.dp, blurredLens = true)) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Artwork (or the app's icon), like the tvOS Now Playing tile.
            val art = np.art ?: np.appIcon
            Box(
                Modifier
                    .size(52.dp)
                    .shadow(6.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFF375F), Color(0xFFBF5AF2)))),
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) {
                    androidx.compose.foundation.Image(
                        art, null, Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    PIcon(Icons.Rounded.MusicNote, Color.White, 26.dp)
                }
            }
            HSpace(10.dp)
            Column(Modifier.weight(1f)) {
                PText(np.title, 12.sp, FontWeight.SemiBold, fg, lineHeight = 14.sp)
                PText(np.subtitle, 11.sp, FontWeight.Normal, fg.copy(alpha = 0.68f), lineHeight = 13.sp)
                VSpace(4.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MediaButton(Icons.Rounded.SkipPrevious, focus == CC.NowPrev)
                    HSpace(6.dp)
                    MediaButton(if (np.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, focus == CC.NowPlay)
                    HSpace(6.dp)
                    MediaButton(Icons.Rounded.SkipNext, focus == CC.NowNext)
                }
            }
        }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, focused: Boolean) {
    val theme = LocalPrism.current
    val p = theme.palette
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, spring(dampingRatio = 0.6f, stiffness = 420f), label = "media")
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(28.dp)
            .then(if (focused) Modifier.shadow(10.dp, CircleShape).background(theme.controlFocused(), CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        PIcon(icon, if (focused) p.textFocused else if (p.isDark) Color.White else p.textPrimary, 20.dp)
    }
}
