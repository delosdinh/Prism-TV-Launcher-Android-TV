package com.prismtv.launcher.ui.settings

import androidx.compose.animation.AnimatedContent
import com.prismtv.launcher.ui.components.atLeast
import com.prismtv.launcher.ui.glass.glass
import com.prismtv.launcher.ui.glass.GlassSource
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material.icons.rounded.Panorama
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ManageAccounts
import com.prismtv.launcher.ui.components.Avatar
import com.prismtv.launcher.ui.components.AvatarLook
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.LinearOutSlowInEasing
import com.prismtv.launcher.data.TextScales
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.HdrOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.LauncherViewModel
import com.prismtv.launcher.Overlay
import com.prismtv.launcher.SettingsPage
import com.prismtv.launcher.SettingsRow
import com.prismtv.launcher.data.LauncherApp
import com.prismtv.launcher.ui.components.AppArtwork
import com.prismtv.launcher.ui.components.HSpace
import com.prismtv.launcher.ui.components.PIcon
import com.prismtv.launcher.ui.components.PText
import com.prismtv.launcher.ui.components.PrismLogo
import com.prismtv.launcher.ui.components.PrismLogoMark
import com.prismtv.launcher.ui.components.VSpace
import com.prismtv.launcher.ui.components.lerpF
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
    val tState = animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 320f), label = "settings")
    val t by tState
    if (!tState.atLeast(0.002f)) return
    if (com.prismtv.launcher.BuildConfig.DEBUG) androidx.compose.runtime.SideEffect { android.util.Log.d("PrismComp", "settings") }
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
            // Settings are always frosted glass over the Home Screen, whatever the material; Settings &
            // Pop-ups Transparency sets the blur (SceneUi) and the tint.
            .glass(
                RectangleShape,
                theme.settingsVeil(),
                GlassSource.SceneUi, rimStrength = 0f, lens = false, frosted = true,
            )
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(p.bgAccent.copy(alpha = 0.35f), Color.Transparent),
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
            if (pg == SettingsPage.TextSize) {
                TextSizePage(vm.prefs.textSize, p)
                return@AnimatedContent
            }
            Box(Modifier.fillMaxSize()) {
                PText(
                    vm.pageTitle(pg), 26.sp, FontWeight.SemiBold, p.textPrimary,
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 30.dp),
                )
                val rows = vm.settingsRows(pg)
                val focus = (vm.settingsFocus[pg] ?: 0).coerceIn(0, (rows.size - 1).coerceAtLeast(0))
                // Each tab has its own glyph; on the main list it follows focus, like tvOS.
                val art: Any = when (pg) {
                    SettingsPage.Root, SettingsPage.Appearance, SettingsPage.HomeScreen, SettingsPage.Keyboard,
                    SettingsPage.Screensaver, SettingsPage.Access, SettingsPage.Closing, SettingsPage.Shizuku, SettingsPage.HomeApp,
                    SettingsPage.RemoteButtons, SettingsPage.RemoteButton, SettingsPage.ScreenTime ->
                        rows.getOrNull(focus)?.let { if (it.id == "profile") vm.avatarLook else glyphFor(it.id) } ?: pageGlyph(pg)
                    SettingsPage.Apps, SettingsPage.WidgetApps, SettingsPage.Startup, SettingsPage.Restrictions, SettingsPage.Inputs,
                    SettingsPage.RemoteAction ->
                        rows.getOrNull(focus)?.app ?: rows.getOrNull(focus)?.let { glyphFor(it.id) } ?: pageGlyph(pg)
                    SettingsPage.Banner -> rows.getOrNull(focus)?.let { vm.bannerPreview(it.id) } ?: pageGlyph(pg)
                    SettingsPage.ScreensaverPictures -> rows.getOrNull(focus)?.let { vm.picturePreview(it.id) } ?: pageGlyph(pg)
                    SettingsPage.Profiles -> rows.getOrNull(focus)?.let { vm.profileListPreview(it.id) } ?: pageGlyph(pg)
                    SettingsPage.Wallpaper -> rows.getOrNull(focus)?.let { vm.wallpaperPreview(it.id) } ?: pageGlyph(pg)
                    SettingsPage.Profile -> rows.getOrNull(focus)?.let { vm.profilePreview(it.id) } ?: vm.avatarLook
                    else -> pageGlyph(pg)
                }
                PageArt(
                    art,
                    Modifier
                        .offset(126.dp, 156.dp)
                        .size(228.dp),
                    p,
                )
                // What the focused setting does, under its artwork (as on tvOS).
                androidx.compose.animation.Crossfade(
                    settingsHelp(pg, rows.getOrNull(focus)?.id),
                    Modifier
                        .offset(80.dp, 404.dp)
                        .width(320.dp),
                    animationSpec = tween(220),
                    label = "settingsHelp",
                ) { text ->
                    if (text != null) {
                        PText(
                            text, 13.sp, FontWeight.Normal, p.textSecondary,
                            Modifier.fillMaxWidth(), maxLines = 4, align = TextAlign.Center, lineHeight = 18.sp,
                        )
                    }
                }
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

/** A Settings tab's glyph: a white symbol on a coloured, gently lit tile (iOS / tvOS style). */
private data class Glyph(val icon: ImageVector, val top: Color, val bottom: Color)

/** Marker for the Prism TV logo tile. */
private object LogoGlyph

private fun glyph(icon: ImageVector, top: Long, bottom: Long) = Glyph(icon, Color(top), Color(bottom))

private val Glyphs: Map<String, Glyph> = mapOf(
    "appearance" to glyph(Icons.Rounded.Palette, 0xFF7D7AFF, 0xFF4B48D6),
    "material" to glyph(Icons.Rounded.Opacity, 0xFF64D2FF, 0xFF1A8CFF),
    "home" to glyph(Icons.Rounded.Dashboard, 0xFF3D9BFF, 0xFF0A5FE0),
    "screensaver" to glyph(Icons.Rounded.Landscape, 0xFF4CD0DF, 0xFF1C8AA6),
    "sounds" to glyph(Icons.AutoMirrored.Rounded.VolumeUp, 0xFFFF6482, 0xFFE0264A),
    "apps" to glyph(Icons.Rounded.Apps, 0xFF4CD964, 0xFF239B3E),
    "usage" to glyph(Icons.Rounded.History, 0xFFFFB340, 0xFFE07A0B),
    "a11y" to glyph(Icons.Rounded.SettingsRemote, 0xFF9C9CA3, 0xFF5E5E64),
    "homeOverride" to glyph(Icons.Rounded.Home, 0xFF8E8BFF, 0xFF5E5BDA),
    "startup" to glyph(Icons.Rounded.PowerSettingsNew, 0xFF4CD964, 0xFF239B3E),
    "sleep" to glyph(Icons.Rounded.Timer, 0xFFC27BFF, 0xFF8A3FD1),
    "about" to glyph(Icons.Rounded.Info, 0xFF9C9CA3, 0xFF5E5E64),
    "restart" to glyph(Icons.Rounded.RestartAlt, 0xFF9C9CA3, 0xFF5E5E64),
    "reset" to glyph(Icons.Rounded.Restore, 0xFFFF6961, 0xFFD63A31),
    "upd:check" to glyph(com.prismtv.launcher.ui.components.PrismIcons.TvUpdate, 0xFF5AC8FA, 0xFF0A6CFF),
    "update" to glyph(com.prismtv.launcher.ui.components.PrismIcons.TvUpdate, 0xFF5AC8FA, 0xFF0A6CFF),
    "ss:range" to glyph(Icons.Rounded.HdrOn, 0xFFFFC94D, 0xFFE08A00),
    "kbColour" to glyph(Icons.Rounded.Keyboard, 0xFF9C9CA3, 0xFF5E5E64),
    "textSize" to glyph(Icons.Rounded.FormatSize, 0xFF3D9BFF, 0xFF0A5FE0),
    "reduce" to glyph(Icons.Rounded.Contrast, 0xFF3D9BFF, 0xFF0A5FE0),
    "notif" to glyph(Icons.Rounded.Notifications, 0xFFFF6961, 0xFFD63A31),
    "widgets" to glyph(Icons.Rounded.Widgets, 0xFF5E5CE6, 0xFF3634A3),
    "wallpaper" to glyph(Icons.Rounded.Wallpaper, 0xFF4CD0DF, 0xFF1C8AA6),
    "topShelf" to glyph(Icons.Rounded.ViewCarousel, 0xFFFF9F0A, 0xFFE06A00),
    "keyboard" to glyph(Icons.Rounded.Keyboard, 0xFF9C9CA3, 0xFF5E5E64),
    "kb:system" to glyph(Icons.Rounded.Keyboard, 0xFF3D9BFF, 0xFF0A5FE0),
    "kb:autocorrect" to glyph(Icons.Rounded.Spellcheck, 0xFF4CD964, 0xFF239B3E),
    "kb:predictive" to glyph(Icons.AutoMirrored.Rounded.ShortText, 0xFF5E5CE6, 0xFF3634A3),
    "kb:mic" to glyph(Icons.Rounded.Mic, 0xFFFF6482, 0xFFE0264A),
    "wobble" to glyph(Icons.Rounded.Animation, 0xFF8E8BFF, 0xFF5E5BDA),
    "logos" to glyph(Icons.Rounded.Title, 0xFFFF9F0A, 0xFFE06A00),
    "wp:shuffle" to glyph(Icons.Rounded.Shuffle, 0xFF4CD0DF, 0xFF1C8AA6),
    "w:order" to glyph(Icons.Rounded.SwapVert, 0xFF5E5CE6, 0xFF3634A3),
    "pf:name" to glyph(Icons.Rounded.Badge, 0xFF3D9BFF, 0xFF0A5FE0),
    "pf:accounts" to glyph(Icons.Rounded.ManageAccounts, 0xFF9C9CA3, 0xFF5E5E64),
    "ui:High" to glyph(Icons.Rounded.Opacity, 0xFF64D2FF, 0xFF1A8CFF),
    "ui:Medium" to glyph(Icons.Rounded.Opacity, 0xFF5AA9FF, 0xFF1A6CE0),
    "ui:Low" to glyph(Icons.Rounded.Opacity, 0xFF7C86A8, 0xFF4A5170),
    "lite" to glyph(Icons.Rounded.Speed, 0xFF4CD964, 0xFF239B3E),
    "ss:media" to glyph(Icons.Rounded.MusicNote, 0xFFFF6482, 0xFFE0264A),
    "startupApp" to glyph(Icons.Rounded.RocketLaunch, 0xFFFF9F0A, 0xFFE06A00),
    "inputs" to glyph(Icons.AutoMirrored.Rounded.Input, 0xFF5AC8FA, 0xFF0A6CFF),
    "backup" to glyph(Icons.Rounded.SettingsBackupRestore, 0xFF4CD964, 0xFF239B3E),
    "iconPack" to glyph(Icons.Rounded.Style, 0xFFFF6482, 0xFFE0264A),
    "banner" to glyph(Icons.Rounded.Image, 0xFFFF9F0A, 0xFFE06A00),
    "profiles" to glyph(Icons.Rounded.Groups, 0xFF3D9BFF, 0xFF0A5FE0),
    "restrictions" to glyph(Icons.Rounded.Lock, 0xFFFF9F0A, 0xFFE06A00),
    "kbField" to glyph(Icons.Rounded.TextFields, 0xFF9C9CA3, 0xFF5E5E64),
    "ss:pictures" to glyph(Icons.Rounded.PhotoLibrary, 0xFFFFCC00, 0xFFFF9500),
    "acc:photos" to glyph(Icons.Rounded.PhotoLibrary, 0xFFFFCC00, 0xFFFF9500),
    "access" to glyph(Icons.Rounded.PrivacyTip, 0xFF3D9BFF, 0xFF0A5FE0),
    "recs" to glyph(Icons.Rounded.Recommend, 0xFF3D9BFF, 0xFF0A5FE0),
    "hero" to glyph(Icons.Rounded.Panorama, 0xFF5AC8FA, 0xFF0A6CFF),
    "source" to glyph(Icons.Rounded.Collections, 0xFFFF9F0A, 0xFFE06A00),
    "autorotate" to glyph(Icons.Rounded.Autorenew, 0xFF4CD964, 0xFF239B3E),
    "ss:show" to glyph(Icons.Rounded.PlayCircle, 0xFF5AC8FA, 0xFF0A84FF),
    "ss:after" to glyph(Icons.Rounded.Timer, 0xFF9C9CA3, 0xFF5E5E64),
    "ss:source" to glyph(Icons.Rounded.Landscape, 0xFF64D2FF, 0xFF1A8FB8),
    "ss:location" to glyph(Icons.Rounded.Place, 0xFFFF6961, 0xFFD63A31),
    "ss:quality" to glyph(Icons.Rounded.HighQuality, 0xFFBF5AF2, 0xFF8E2DC4),
    "closing" to glyph(Icons.Rounded.HighlightOff, 0xFFFF6961, 0xFFD63A31),
    "cl:full" to glyph(Icons.Rounded.HighlightOff, 0xFFFF6961, 0xFFD63A31),
    "cl:using" to glyph(Icons.Rounded.Tune, 0xFF9C9CA3, 0xFF5E5E64),
    "cl:shizuku" to glyph(Icons.Rounded.Terminal, 0xFF5AC8FA, 0xFF0A6CFF),
    "cl:a11y" to glyph(Icons.Rounded.SettingsRemote, 0xFF9C9CA3, 0xFF5E5E64),
    "cl:root" to glyph(Icons.Rounded.AdminPanelSettings, 0xFF4CD964, 0xFF239B3E),
    "sz:install" to glyph(Icons.Rounded.Download, 0xFF5AC8FA, 0xFF0A6CFF),
    "sz:debug" to glyph(Icons.Rounded.DeveloperMode, 0xFF9C9CA3, 0xFF5E5E64),
    "sz:start" to glyph(Icons.Rounded.PlayArrow, 0xFF4CD964, 0xFF239B3E),
    "sz:allow" to glyph(Icons.Rounded.VerifiedUser, 0xFFFF9F0A, 0xFFE06A00),
    "sz:boot" to glyph(Icons.Rounded.PowerSettingsNew, 0xFF4CD964, 0xFF239B3E),
    "sz:open" to glyph(Icons.Rounded.Terminal, 0xFF5AC8FA, 0xFF0A6CFF),
    "homeApp" to glyph(Icons.Rounded.Home, 0xFF3D9BFF, 0xFF0A5FE0),
    "ha:prism" to glyph(Icons.Rounded.Home, 0xFF3D9BFF, 0xFF0A5FE0),
    "ha:stock" to glyph(Icons.Rounded.Dashboard, 0xFF9C9CA3, 0xFF5E5E64),
    "ha:restore" to glyph(Icons.Rounded.Restore, 0xFFFF9F0A, 0xFFE06A00),
    "remote" to glyph(Icons.Rounded.SettingsRemote, 0xFFB66CFF, 0xFF7B2FD6),
    "cl:recents" to glyph(Icons.Rounded.Dashboard, 0xFF9C9CA3, 0xFF5E5E64),
    "rb:a11y" to glyph(Icons.Rounded.SettingsRemote, 0xFF9C9CA3, 0xFF5E5E64),
    "rb:homeHold" to glyph(Icons.Rounded.Home, 0xFF3D9BFF, 0xFF0A5FE0),
    "rb:add" to glyph(Icons.Rounded.AddCircle, 0xFF34C759, 0xFF1E9E40),
    "rb:btn" to glyph(Icons.Rounded.SettingsRemote, 0xFFB66CFF, 0xFF7B2FD6),
    "rbe:press" to glyph(Icons.Rounded.TouchApp, 0xFFB66CFF, 0xFF7B2FD6),
    "rbe:hold" to glyph(Icons.Rounded.Timer, 0xFFC27BFF, 0xFF8A3FD1),
    "rbe:remove" to glyph(Icons.Rounded.Restore, 0xFFFF9F0A, 0xFFE06A00),
    "pr:screentime" to glyph(Icons.Rounded.HourglassBottom, 0xFF7D7AFF, 0xFF4B3FD6),
    "st:limit" to glyph(Icons.Rounded.HourglassBottom, 0xFF7D7AFF, 0xFF4B3FD6),
    "st:downtime" to glyph(Icons.Rounded.Bedtime, 0xFF5E5CE6, 0xFF3634A3),
    "st:used" to glyph(Icons.Rounded.BarChart, 0xFF7D7AFF, 0xFF4B3FD6),
    "st:left" to glyph(Icons.Rounded.Timelapse, 0xFF7D7AFF, 0xFF4B3FD6),
    "st:passcode" to glyph(Icons.Rounded.Lock, 0xFFFF9F0A, 0xFFE06A00),
    "bannersOver" to glyph(Icons.Rounded.NotificationsActive, 0xFFFF6961, 0xFFD63A31),
    "snapshots" to glyph(Icons.Rounded.ViewCarousel, 0xFF3D9BFF, 0xFF0A5FE0),
    "wifi" to glyph(Icons.Rounded.Wifi, 0xFF3D9BFF, 0xFF0A5FE0),
)

private fun glyphFor(id: String): Any? = when {
    id == "about" -> LogoGlyph
    id.startsWith("rb:btn:") -> Glyphs["rb:btn"]
    id.startsWith("ra:") -> Glyphs["remote"]
    else -> Glyphs[id]
}

private fun pageGlyph(page: SettingsPage): Any = when (page) {
    SettingsPage.Root, SettingsPage.About -> LogoGlyph
    SettingsPage.Appearance -> Glyphs.getValue("appearance")
    SettingsPage.HomeScreen -> Glyphs.getValue("home")
    SettingsPage.Screensaver -> Glyphs.getValue("screensaver")
    SettingsPage.Apps -> Glyphs.getValue("apps")
    SettingsPage.SoftwareUpdate -> Glyphs.getValue("update")
    SettingsPage.TextSize -> Glyphs.getValue("textSize")
    SettingsPage.Wallpaper -> Glyphs.getValue("wallpaper")
    SettingsPage.Widgets, SettingsPage.WidgetApps, SettingsPage.WidgetOrder -> Glyphs.getValue("widgets")
    SettingsPage.Keyboard -> Glyphs.getValue("keyboard")
    SettingsPage.Profile -> Glyphs.getValue("pf:name")
    SettingsPage.Startup -> Glyphs.getValue("startupApp")
    SettingsPage.Inputs -> Glyphs.getValue("inputs")
    SettingsPage.Backup -> Glyphs.getValue("backup")
    SettingsPage.IconPack -> Glyphs.getValue("iconPack")
    SettingsPage.Banner -> Glyphs.getValue("banner")
    SettingsPage.Profiles -> Glyphs.getValue("profiles")
    SettingsPage.Restrictions -> Glyphs.getValue("restrictions")
    SettingsPage.ScreensaverPictures -> Glyphs.getValue("ss:pictures")
    SettingsPage.Access -> Glyphs.getValue("access")
    SettingsPage.Closing -> Glyphs.getValue("closing")
    SettingsPage.Shizuku -> Glyphs.getValue("cl:shizuku")
    SettingsPage.HomeApp -> Glyphs.getValue("homeApp")
    SettingsPage.RemoteButtons, SettingsPage.RemoteButton, SettingsPage.RemoteAction -> Glyphs.getValue("remote")
    SettingsPage.ScreenTime -> Glyphs.getValue("pr:screentime")
}

/** The large artwork on the left of Settings: the tab's glyph, the logo, an app or a wallpaper. */
@Composable
private fun PageArt(art: Any, modifier: Modifier, p: PrismPalette) {
    // Glyph changes cross-fade, as on tvOS. Each layer fades per draw (ModulateAlpha) rather than in an
    // offscreen buffer, so the glows outside the tile are never clipped mid-fade.
    var current by remember { mutableStateOf(art) }
    var previous by remember { mutableStateOf<Any?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(art) {
        if (art == current) return@LaunchedEffect
        previous = current
        current = art
        fade.snapTo(0f)
        fade.animateTo(1f, tween(320, easing = LinearOutSlowInEasing))
        previous = null
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        previous?.let { old ->
            ArtContent(old, p, Modifier.graphicsLayer {
                alpha = 1f - fade.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
            })
        }
        ArtContent(current, p, Modifier.graphicsLayer {
            alpha = fade.value
            compositingStrategy = CompositingStrategy.ModulateAlpha
        })
    }
}

@Composable
private fun ArtContent(a: Any, p: PrismPalette, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (a) {
            is Glyph -> GlyphTile(a, Modifier.size(228.dp), corner = 54.dp, iconSize = 116.dp)
            is LauncherApp -> {
                val shape = RoundedCornerShape(26.dp)
                Box(
                    Modifier
                        .size(228.dp, 137.dp)
                        .glow(a.tint, 26.dp)
                        .clip(shape)
                ) { AppArtwork(a) }
            }
            is AvatarLook -> Avatar(196.dp, look = a, modifier = Modifier.glow(Color(0xFF6C5CFF), 98.dp))
            is ImageBitmap -> {
                val shape = RoundedCornerShape(20.dp)
                Image(
                    a, null,
                    Modifier
                        .size(300.dp, 169.dp)
                        .shadow(24.dp, shape)
                        .clip(shape)
                        .border(1.dp, Color.White.copy(alpha = 0.25f), shape),
                    contentScale = ContentScale.Crop,
                )
            }
            else -> LogoTile(Modifier.size(228.dp), p)
        }
    }
}

/** Settings › Appearance › Text Size: a tvOS-style slider from small to large type. */
@Composable
private fun TextSizePage(step: Int, p: PrismPalette) {
    val steps = TextScales.size
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PText("Text Size", 38.sp, FontWeight.Bold, p.textPrimary)
            VSpace(12.dp)
            PText(
                "Prism TV's text adjusts to your preferred reading size below.",
                16.sp, FontWeight.Normal, p.textSecondary,
                Modifier.width(520.dp), maxLines = 2, align = TextAlign.Center, lineHeight = 22.sp,
            )
            VSpace(64.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) { PText("A", 14.sp, FontWeight.SemiBold, p.textPrimary) }
                HSpace(18.dp)
                val trackW = 460f
                val knobX by animateFloatAsState(trackW * step / (steps - 1).toFloat(), spring(dampingRatio = 0.8f, stiffness = 420f), label = "knob")
                Box(Modifier.size(trackW.dp, 56.dp)) {
                    // Track: filled up to the knob.
                    Box(
                        Modifier
                            .offset(0.dp, 25.dp)
                            .size(trackW.dp, 6.dp)
                            .background(p.textSecondary.copy(alpha = 0.32f), RoundedCornerShape(3.dp))
                    )
                    Box(
                        Modifier
                            .offset(0.dp, 25.dp)
                            .size(knobX.dp.coerceAtLeast(6.dp), 6.dp)
                            .background(p.textPrimary.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                    )
                    // Step dots.
                    for (i in 0 until steps) {
                        Box(
                            Modifier
                                .offset((trackW * i / (steps - 1) - 2f).dp, 44.dp)
                                .size(4.dp)
                                .background(p.textSecondary.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                        )
                    }
                    // Knob: a little glass capsule.
                    Box(
                        Modifier
                            .offset((knobX - 24f).dp, 13.dp)
                            .size(48.dp, 30.dp)
                            .shadow(10.dp, RoundedCornerShape(15.dp))
                            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE6E8EE))), RoundedCornerShape(15.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(15.dp))
                    )
                }
                HSpace(18.dp)
                Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) { PText("A", 30.sp, FontWeight.SemiBold, p.textPrimary) }
            }
        }
    }
}

/** A soft coloured glow under a tile (drawn, not an elevation shadow), fading fully to nothing. */
@Suppress("UNUSED_PARAMETER")
private fun Modifier.glow(color: Color, corner: Dp): Modifier = drawBehind {
    val r = size.maxDimension * 0.78f
    val c = Offset(size.width / 2f, size.height / 2f + size.height * 0.08f)
    drawCircle(
        Brush.radialGradient(
            0f to color.copy(alpha = 0.32f),
            0.55f to color.copy(alpha = 0.12f),
            1f to Color.Transparent,
            center = c,
            radius = r,
        ),
        radius = r,
        center = c,
    )
}

@Composable
private fun GlyphTile(g: Glyph, modifier: Modifier, corner: Dp, iconSize: Dp) {
    val shape = RoundedCornerShape(corner)
    val big = iconSize > 40.dp
    Box(
        modifier
            .then(if (big) Modifier.glow(g.bottom, corner) else Modifier)
            // Clip after the glow so the top light below stays inside the rounded tile (it was
            // drawn as a full rectangle, leaving faint square corners above the tile).
            .clip(shape)
            .background(Brush.verticalGradient(listOf(g.top, g.bottom)), shape)
            .drawBehind {
                // Soft top light, like the glossy tvOS settings tiles.
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.18f), 0.5f to Color.Transparent))
            }
            .border(
                (if (big) 1.dp else 0.5.dp),
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f))),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        PIcon(g.icon, Color.White, iconSize)
    }
}

@Composable
private fun LogoTile(modifier: Modifier, p: PrismPalette) {
    val shape = RoundedCornerShape(54.dp)
    Box(
        modifier
            .glow(Color(0xFF6C5CFF), 54.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF262833), Color(0xFF0B0C10))), shape)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (p.isDark) 0.30f else 0.45f), Color.White.copy(alpha = 0.04f))), shape),
        contentAlignment = Alignment.Center,
    ) {
        PrismLogo(Modifier.size(180.dp, 88.dp))
    }
}

@Composable
private fun SettingsList(rows: List<SettingsRow>, focus: Int, modifier: Modifier) {
    // Pre-compute row positions (section headers take extra space).
    val ys = FloatArray(rows.size)
    val headed = BooleanArray(rows.size)
    var y = 0f
    var last: String? = "\u0000"
    rows.forEachIndexed { i, r ->
        if (r.section != last) {
            if (r.section != null) {
                y += HeaderH
                headed[i] = true
            }
            last = r.section
        }
        ys[i] = y
        y += RowH + RowGap
    }
    val total = y
    val target = if (focus < 0 || rows.isEmpty()) 0f else (ys[focus] - 150f).coerceIn(0f, (total - ViewportH + 12f).coerceAtLeast(0f))
    val scroll = animateFloatAsState(target, spring(dampingRatio = 0.9f, stiffness = 300f, visibilityThreshold = 0.1f), label = "settingsScroll")
    // Only the rows on screen, and a few either side of it, are composed: long pages (Apps, Restrictions)
    // open without building every row. The range follows the scroll, but changes only when a row comes
    // within (or goes beyond) that margin, so scrolling doesn't recompose the list on every frame.
    val window by remember(rows) {
        derivedStateOf(structuralEqualityPolicy()) {
            if (ys.isEmpty()) IntRange.EMPTY
            else {
                val top = scroll.value - ListMargin
                val bottom = scroll.value + ViewportH + ListMargin
                var first = 0
                while (first < ys.size - 1 && ys[first] + RowH < top) first++
                var end = first
                while (end < ys.size - 1 && ys[end + 1] - (if (headed[end + 1]) HeaderH else 0f) < bottom) end++
                first..end
            }
        }
    }

    Box(modifier.clipToBounds()) {
        Box(
            Modifier
                // The whole list's height (not just what fits the viewport) so long pages scroll fully.
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .padding(horizontal = 12.dp)
                .offset { IntOffset(0, -scroll.value.dp.roundToPx() + 6.dp.roundToPx()) }
                .fillMaxWidth()
                .height(total.dp)
        ) {
            for (i in window) {
                val r = rows[i]
                key(r.id) {
                    val s = r.section
                    if (headed[i] && s != null) {
                        val headerY = ys[i] - HeaderH
                        SectionHeader(s, Modifier.offset { IntOffset(0, headerY.dp.roundToPx()) })
                    }
                    val rowY = ys[i]
                    SettingsRowView(r, i == focus, Modifier.offset { IntOffset(0, rowY.dp.roundToPx()) })
                }
            }
        }
    }
}

/** How far beyond the viewport rows stay composed (in dp): about three rows each side. */
private const val ListMargin = 3 * (RowH + RowGap)

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    val p = LocalPrism.current.palette
    Box(modifier.fillMaxWidth().height(HeaderH.dp), contentAlignment = Alignment.BottomStart) {
        PText(
            title, 11.sp, FontWeight.SemiBold, p.textSecondary,
            Modifier.padding(start = 14.dp, bottom = 7.dp),
            letterSpacing = 0.7.sp,
        )
    }
}

@Composable
private fun SettingsRowView(r: SettingsRow, focused: Boolean, modifier: Modifier = Modifier) {
    if (com.prismtv.launcher.BuildConfig.DEBUG) androidx.compose.runtime.SideEffect { android.util.Log.d("PrismComp", "row") }
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
            .background(p.buttonUnfocused.copy(alpha = if (p.isDark) 0.46f else 0.62f), RowShape)
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f))), RowShape)
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(RowH.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val app = r.app
        val g = Glyphs[r.id]
        when {
            app != null -> {
                Box(Modifier.size(38.dp, 23.dp).clip(RoundedCornerShape(5.dp))) { AppArtwork(app) }
                HSpace(12.dp)
            }
            g != null -> {
                GlyphTile(g, Modifier.size(26.dp), corner = 7.dp, iconSize = 17.dp)
                HSpace(12.dp)
            }
            r.id == "profile" -> {
                Avatar(26.dp)
                HSpace(12.dp)
            }
            r.id.startsWith("pf:preset:") -> {
                Avatar(26.dp, look = AvatarLook.Preset(r.id.removePrefix("pf:preset:").toIntOrNull() ?: 0))
                HSpace(12.dp)
            }
            r.id == "about" -> {
                Box(
                    Modifier
                        .size(26.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFF2A2C36), Color(0xFF0B0C10))), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) { PrismLogoMark(Modifier.size(17.dp)) }
                HSpace(12.dp)
            }
        }
        PText(r.title, 15.sp, if (focused) FontWeight.SemiBold else FontWeight.Medium, fg, Modifier.weight(1f))
        val value = r.value
        // On/off settings are switches, as on iOS; text values stay text.
        val toggle = r.toggle ?: if (!r.chevron && !r.selectable && (value == "On" || value == "Off")) value == "On" else null
        if (toggle != null) {
            Switch(toggle)
        } else if (value != null) {
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

/**
 * An iOS 26 switch. With Liquid Glass the knob turns into a clear glass lens and stretches as it slides
 * across, then settles back into a white knob; Frosted (classic) just slides.
 */
@Composable
private fun Switch(on: Boolean) {
    val theme = LocalPrism.current
    val p = theme.palette
    val pos by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 380f), label = "switchPos")
    val morph = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(on) {
        if (first) { first = false; return@LaunchedEffect }
        if (!theme.liquidControls) return@LaunchedEffect
        morph.animateTo(1f, tween(130, easing = FastOutLinearInEasing))
        morph.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 260f))
    }
    val trackW = 46f
    val trackH = 28f
    val onColour = p.toggleOn
    val offColour = if (p.isDark) Color.White.copy(alpha = 0.22f) else Color(0xFF787880).copy(alpha = 0.24f)
    val track = androidx.compose.ui.graphics.lerp(offColour, onColour, pos)
    Box(
        Modifier
            .size(trackW.dp, trackH.dp)
            .background(track, RoundedCornerShape(50)),
    ) {
        val m = morph.value.coerceIn(0f, 1.2f)
        val knobW = 24f + 14f * m
        val knobH = 24f + 6f * m
        val x = 2f + (trackW - 4f - knobW) * pos
        val y = (trackH - knobH) / 2f
        val shape = RoundedCornerShape(50)
        Box(
            Modifier
                .offset(x.dp, y.dp)
                .size(knobW.dp, knobH.dp)
                .shadow(lerpF(3f, 8f, m).dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.3f))
                .background(Color.White.copy(alpha = lerpF(1f, 0.18f, m.coerceAtMost(1f))), shape)
                .border(
                    lerpF(0f, 1.4f, m.coerceAtMost(1f)).dp,
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.6f))),
                    shape,
                )
                .drawBehind {
                    if (m > 0.01f) {
                        // The lens catches light along its top edge.
                        drawRoundRect(
                            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.45f * m.coerceAtMost(1f)), 0.5f to Color.Transparent),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                        )
                    }
                }
        )
    }
}
