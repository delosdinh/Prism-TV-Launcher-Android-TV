package com.prismtv.launcher.ui.home

import androidx.compose.animation.AnimatedContent
import com.prismtv.launcher.ui.components.atLeast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.graphics.luminance
import com.prismtv.launcher.ui.SpotlightLayout
import com.prismtv.launcher.ui.rememberWobble
import com.prismtv.launcher.ui.wobble
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.LauncherViewModel
import com.prismtv.launcher.data.TemperatureUnit
import com.prismtv.launcher.data.Weather
import com.prismtv.launcher.data.WeatherKind
import com.prismtv.launcher.data.kind
import com.prismtv.launcher.data.label
import com.prismtv.launcher.Overlay
import com.prismtv.launcher.Zone
import com.prismtv.launcher.data.AppItem
import com.prismtv.launcher.data.FocusEffect
import com.prismtv.launcher.data.FolderItem
import com.prismtv.launcher.data.GridItem
import com.prismtv.launcher.data.HeroArt
import com.prismtv.launcher.data.HeroItem
import com.prismtv.launcher.data.LabelMode
import com.prismtv.launcher.ui.HomeLayout
import com.prismtv.launcher.ui.components.AppArtwork
import com.prismtv.launcher.ui.components.Avatar
import com.prismtv.launcher.ui.components.Badge
import com.prismtv.launcher.ui.components.FolderArtwork
import com.prismtv.launcher.ui.components.HSpace
import com.prismtv.launcher.ui.components.PIcon
import com.prismtv.launcher.ui.components.PText
import com.prismtv.launcher.ui.components.VSpace
import com.prismtv.launcher.ui.components.lerpF
import com.prismtv.launcher.ui.glass.BackdropState
import com.prismtv.launcher.ui.glass.GlassSource
import com.prismtv.launcher.ui.glass.LocalBackdrop
import com.prismtv.launcher.ui.glass.PerfSwitches
import com.prismtv.launcher.ui.glass.backdropSource
import com.prismtv.launcher.ui.glass.glass
import com.prismtv.launcher.ui.theme.LocalPrism
import com.prismtv.launcher.ui.theme.PrismPalette

private val TileShape = RoundedCornerShape(13.dp)
private val Pill = RoundedCornerShape(50)

/** How bright Home treats a playing aerial for its labels: dark enough for white text. */
private const val AerialLuma = 0.3f

/**
 * App names and widget row titles follow what's actually behind them while browsing: the blurred
 * wallpaper, tinted by the glass colour. Dark ink only over a bright backdrop; white (with a shadow)
 * otherwise. One rule for both, so a row title never turns dark while the app names beside it stay white.
 */
private fun homeLabelColor(gridMode: Boolean, backdropLuma: Float, palette: PrismPalette): Color {
    val tintAlpha = palette.backgroundDim * 0.6f
    val bgLuma = backdropLuma * (1f - tintAlpha) + palette.bgBase.luminance() * tintAlpha
    return if (gridMode && bgLuma > 0.55f) Color(0xFF15171E) else Color.White
}

@Composable
fun HomeScreen(vm: LauncherViewModel, clock: String) {
    val theme = LocalPrism.current
    val p = theme.palette
    val cols = vm.prefs.columns
    val gridMode = vm.isGridMode
    val expanded = vm.isHeroExpanded
    val heroOn = vm.prefs.heroEnabled

    val above = vm.aboveRows
    val below = vm.belowRows
    val belowH = vm.belowHeight
    val widgetBelowFocus = vm.zone == Zone.Widget && !vm.widgetFocusAbove
    val trayTopTarget = when {
        expanded -> HomeLayout.TrayTopHidden
        widgetBelowFocus -> HomeLayout.widgetTrayTop(cols, vm.widgetIndex - above.size)
        gridMode -> HomeLayout.gridTrayTop(cols, vm.row, belowH)
        else -> HomeLayout.TrayTopHero
    }
    val motion = spring<Float>(dampingRatio = 0.86f, stiffness = 160f)
    val trayTop = animateFloatAsState(trayTopTarget, motion, label = "trayTop")
    val rowGap = animateFloatAsState(if (gridMode) HomeLayout.RowGapGrid else HomeLayout.RowGapHero, motion, label = "rowGap")
    val below0 = animateFloatAsState(belowH, motion, label = "belowH")
    val spotlight = vm.spotlight
    // Spotlight shows the wallpaper behind the Top Shelf card; with the Top Shelf off it's the wallpaper too.
    val backdropArt = if (spotlight || !heroOn) vm.wallpaperArt ?: vm.currentArt else vm.currentArt
    // Moving aerials behind Home (a still in Lite Mode, and while Home is behind an app).
    val aerial = (spotlight || !heroOn) && vm.wallpaperKey == com.prismtv.launcher.AerialWallpaperKey &&
        vm.homeVisible && !vm.prefs.liteMode
    // Labels over a playing aerial are white with a shadow, as on tvOS: the footage changes as it plays,
    // and the picture standing in for it before it starts says nothing about how bright it is.
    val luma = if (aerial) AerialLuma else backdropArt?.luma ?: if (p.isDark) 0.15f else 0.7f

    Box(Modifier.fillMaxSize()) {
        HomeBackdrop(
            backdropArt, blurred = gridMode, palette = p, backdrop = LocalBackdrop.current,
            card = if (spotlight) vm.currentArt else null, expanded = expanded,
            aerial = aerial,
        )

        HeroOverlay(
            item = vm.currentHeroItem,
            count = vm.currentHeroItems.size,
            index = vm.heroIndex,
            expanded = expanded,
            // Search's results row takes the Top Shelf's place, so the title steps aside for it.
            visible = heroOn && !gridMode && vm.overlay != Overlay.Sidebar && !vm.searchOpen,
            focusedButton = if (vm.zone == Zone.Hero && vm.overlay == Overlay.None) vm.heroButton else -1,
            spotlight = spotlight,
            compact = above.size >= 2 && !spotlight,
            logoFor = { h -> if (vm.prefs.titleLogos) (h.titleArt ?: vm.debugTitleArt)?.let { vm.thumb(it, 800) } else null },
        )

        WidgetRows(
            vm = vm,
            above = above,
            below = below,
            cols = cols,
            trayTop = trayTop,
            rowGap = rowGap,
            gridMode = gridMode,
            hidden = expanded || vm.overlay == Overlay.Sidebar,
            labelColor = homeLabelColor(gridMode, luma, p),
        )

        val focusIndex = if (vm.zone == Zone.Grid && vm.overlay != Overlay.Sidebar) vm.row * cols + vm.col else -1
        AppGrid(
            items = vm.items,
            cols = cols,
            trayTop = trayTop,
            rowGap = rowGap,
            below = below0,
            focusIndex = focusIndex,
            gridMode = gridMode,
            showHint = heroOn && !gridMode && !expanded && above.isEmpty() && vm.zone != Zone.Widget &&
                vm.overlay == Overlay.None && vm.movingId == null && vm.contextMenu == null,
            palette = p,
            movingId = vm.movingId,
            backdropLuma = luma,
        )

        MoveHint(visible = vm.movingId != null, modifier = Modifier.align(Alignment.BottomCenter))

        val pillVisible = !gridMode && vm.overlay != Overlay.ControlCentre && !vm.searchOpen
        val pillAlpha = animateFloatAsState(if (pillVisible) 1f else 0f, tween(300), label = "pill")
        val backdrop = LocalBackdrop.current
        backdrop.pillAlpha = pillAlpha
        TimePill(
            time = clock,
            weather = if (vm.prefs.weather) vm.weather else null,
            network = if (vm.prefs.wifiStatus) vm.network else null,
            notifications = vm.notifications.size,
            unit = vm.prefs.temperatureUnit,
            focus = if (vm.zone == Zone.TopBar && vm.overlay == Overlay.None) vm.topIndex else -1,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 27.dp)
                // While Control Centre is up, its stand-in above Home's layer may draw it instead (PerfSwitches.ccPillAbove).
                .graphicsLayer { alpha = if (PerfSwitches.ccPillAbove && backdrop.ccShown) 0f else pillAlpha.value },
        )
    }
}

/**
 * The time pill drawn just above the Home Screen's layer, in the same place and with the same fade, while
 * Control Centre is composed ([PerfSwitches.ccPillAbove]). Its fade then changes nothing in the Home Screen's
 * layer, which Control Centre's glass looks through: in the layer, each frame of it re-rendered Home's layer,
 * its blurred copy and every Control Centre lens.
 */
@Composable
fun TimePillAbove(vm: LauncherViewModel, clock: String) {
    val backdrop = LocalBackdrop.current
    if (!PerfSwitches.ccPillAbove || !backdrop.ccShown) return
    val pillAlpha = backdrop.pillAlpha ?: return
    Box(Modifier.fillMaxSize()) {
        TimePill(
            time = clock,
            weather = if (vm.prefs.weather) vm.weather else null,
            network = if (vm.prefs.wifiStatus) vm.network else null,
            notifications = vm.notifications.size,
            unit = vm.prefs.temperatureUnit,
            // Control Centre opened from the pill hands focus back to it as it closes.
            focus = if (vm.zone == Zone.TopBar && vm.overlay == Overlay.None) vm.topIndex else -1,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 27.dp)
                .graphicsLayer { alpha = pillAlpha.value },
        )
    }
}

// ------------------------------------------------------------------------------------------ backdrop

/** How much of the blurred still frosts over a video-layer aerial behind the app grid (the rest is the moving video). */
private const val VideoLayerFrost = 0.50f

@Composable
private fun HomeBackdrop(
    art: HeroArt?,
    blurred: Boolean,
    palette: PrismPalette,
    backdrop: BackdropState,
    card: HeroArt? = null,
    expanded: Boolean = false,
    aerial: Boolean = false,
) {
    val blur = animateFloatAsState(if (blurred) 1f else 0f, tween(650, easing = FastOutSlowInEasing), label = "wallBlur")
    val spot = card != null
    Box(
        Modifier
            .fillMaxSize()
            .backdropSource(backdrop, GlassSource.Background)
    ) {
        // Apple TV Aerials: the video sits under the still, which fades once the aerial is playing. Behind
        // the grid the aerial blurs live (Android 11 and older: through OpenGL; if that can't start there,
        // the still comes back, blurred, as before).
        var aerialShown by remember { mutableStateOf(false) }
        var aerialGlBlur by remember { mutableStateOf(false) }
        // Android 12+ blurs the video with RenderEffect, unless this GPU was measured too slow for it.
        // Never for an aerial on the video layer (AerialVideoLayerHost), which nothing in the app can blur: behind
        // the grid the blurred still covers it (it was left sharp while the GPU was being measured again).
        val aerialLiveBlur = (aerialGlBlur || (android.os.Build.VERSION.SDK_INT >= 31 && !com.prismtv.launcher.ui.glass.GlassPerf.cheap)) &&
            com.prismtv.launcher.aerial.AerialVideoLayer.player == null
        // The still fades out once the aerial plays, but comes straight back when it stops (Home paused
        // under a system pop-up, the wallpaper changed): fading it in from nothing showed the dark gradient
        // underneath for most of a second, a black flash on the TV.
        val stillAnim = remember { Animatable(1f) }
        val aerialOn = aerial && aerialShown
        LaunchedEffect(aerialOn) {
            if (aerialOn) stillAnim.animateTo(0f, tween(900)) else stillAnim.snapTo(1f)
        }
        val stillAlpha by stillAnim.asState()
        // A slow TV's aerial plays under Home (AerialVideoLayerHost): what's drawn here has to clear away
        // as the still does, or the gradient would hide it.
        val underHome = com.prismtv.launcher.aerial.AerialVideoLayer.player != null
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (underHome) stillAlpha else 1f }
                .background(Brush.linearGradient(listOf(palette.bgAccent, palette.bgBase)))
        )
        if (aerial) com.prismtv.launcher.aerial.AerialWallpaper(
            blur = { blur.value },
            modifier = Modifier.fillMaxSize(),
            save = vm.prefs.aerialSave.count,
            onShown = { aerialShown = it },
            onLiveBlur = { aerialGlBlur = it },
        )
        LaunchedEffect(aerial) {
            // From the moment the aerial starts loading (its start-up frames are heavy too), so they're
            // timed as frames over an aerial, not held against the glass in general.
            com.prismtv.launcher.ui.glass.GlassPerf.aerialPlaying = aerial
            if (!aerial) aerialShown = false
        }
        // Lite Mode: a quicker cross-fade (two full-screen images are drawn while it runs).
        Crossfade(
            targetState = art,
            animationSpec = tween(if (LocalPrism.current.lite) 350 else 900),
            label = "heroArt",
            modifier = Modifier.graphicsLayer {
                // An aerial on the video layer can't be blurred, so behind the grid the blurred still lies over
                // it like frosted glass: most of the way, so the motion reads through as soft, moving colour.
                alpha = when {
                    aerialLiveBlur -> stillAlpha
                    underHome -> maxOf(stillAlpha, blur.value * VideoLayerFrost)
                    else -> maxOf(stillAlpha, blur.value)
                }
                // Once only the blurred copy draws (below), its alpha can go straight onto it: no offscreen
                // layer to fill and blend back, which costs a tiled GPU like the AM9's a flush each frame.
                compositingStrategy = if (blur.value >= 1f) CompositingStrategy.ModulateAlpha else CompositingStrategy.Auto
            },
        ) { a ->
            if (a != null) {
                Box(Modifier.fillMaxSize()) {
                    // Hidden under the opaque blurred copy once it's fully in, so not drawn then.
                    Image(
                        a.full, null,
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (blur.value >= 1f) 0f else 1f },
                        contentScale = ContentScale.Crop,
                    )
                    Image(
                        a.heavy, null,
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = blur.value },
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Low,
                    )
                }
            }
        }
        // A slow TV's aerial, coming back (Home from an app, the App Switcher closing): until the video's
        // first frame the wallpaper picture showed, which looked like an older aerial held still. A frame of
        // the shot it picks up from stands in instead, and fades with the still as the video comes in.
        val poster = if (aerial) com.prismtv.launcher.aerial.AerialVideoLayer.poster else null
        if (poster != null) {
            Image(
                poster, null,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = stillAlpha * (1f - blur.value) },
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
            )
        }
        if (!spot) {
            // Hero scrims keep type legible without looking "dimmed".
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - blur.value }
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.30f),
                            0.28f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.42f),
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - blur.value }
                    .background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.22f), 0.5f to Color.Transparent))
            )
        }
        // Wallpaper tint while browsing the grid.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = blur.value }
                .background(palette.bgBase.copy(alpha = palette.backgroundDim * 0.6f))
        )
        if (underHome) Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = blur.value * (1f - stillAlpha) }
                .background(Color.Black.copy(alpha = 0.12f))
        )
        if (spot) SpotlightCard(card, expanded, visible = !blurred)
    }
}

/**
 * Spotlight Top Shelf: the title's artwork as a large inset card over the wallpaper. Going up into the
 * Top Shelf grows the card to full screen (and back down into the card).
 */
@Composable
private fun SpotlightCard(art: HeroArt?, expanded: Boolean, visible: Boolean) {
    // Critically damped and clamped: the spring used to overshoot past full screen, which made the
    // corner radius negative and crashed Prism ("Corner size in Px can't be negative") on the way up.
    val eRaw by animateFloatAsState(if (expanded) 1f else 0f, spring(dampingRatio = 1f, stiffness = 190f), label = "spotGrow")
    val e = eRaw.coerceIn(0f, 1f)
    val visState = animateFloatAsState(if (visible) 1f else 0f, tween(450), label = "spotVis")
    val vis by visState
    if (!visState.atLeast(0.01f)) return
    val left = lerpF(SpotlightLayout.Left, 0f, e)
    val top = lerpF(SpotlightLayout.Top, 0f, e)
    val w = lerpF(SpotlightLayout.W, HomeLayout.ScreenW, e)
    val h = lerpF(SpotlightLayout.H, HomeLayout.ScreenH, e)
    val shape = RoundedCornerShape(lerpF(28f, 0f, e).coerceAtLeast(0f).dp)
    Box(
        Modifier
            .offset(left.dp, top.dp)
            .size(w.dp, h.dp)
            .graphicsLayer {
                alpha = vis
                shadowElevation = lerpF(28f, 0f, e).dp.toPx()
                this.shape = shape
                clip = true
            }
            .background(Color.Black)
    ) {
        Crossfade(targetState = art, animationSpec = tween(700), label = "spotArt") { a ->
            if (a != null) Image(a.full, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        // Scrims for the title (bottom-left) at both sizes.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f)))
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.30f), 0.55f to Color.Transparent))
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - e }
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.06f))), shape)
        )
    }
}

// ------------------------------------------------------------------------------------------ hero

@Composable
private fun HeroOverlay(
    item: HeroItem?,
    count: Int,
    index: Int,
    expanded: Boolean,
    visible: Boolean,
    focusedButton: Int,
    spotlight: Boolean = false,
    compact: Boolean = false,
    logoFor: (HeroItem) -> ImageBitmap? = { null },
) {
    val vis by animateFloatAsState(if (visible) 1f else 0f, tween(450), label = "heroVis")
    val e by animateFloatAsState(if (expanded) 1f else 0f, tween(520, easing = FastOutSlowInEasing), label = "heroExp")
    if (item == null || vis < 0.01f) return
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = vis }) {
        AnimatedContent(
            targetState = item,
            transitionSpec = { fadeIn(tween(420, delayMillis = 200)).togetherWith(fadeOut(tween(200))) },
            label = "heroText",
        ) { h ->
            Box(Modifier.fillMaxSize()) {
                // Collapsed: title treatment top-left, like tvOS Top Shelf (inside the card in Spotlight).
                if (e < 0.99f && h.title.isNotBlank() && !compact) {
                    Column(
                        Modifier
                            .then(
                                if (spotlight) {
                                    Modifier
                                        .offset(x = (SpotlightLayout.Left + 30f).dp, y = SpotlightLayout.Top.dp)
                                        .height((SpotlightLayout.H - 26f).dp)
                                        .wrapContentHeight(Alignment.Bottom)
                                } else {
                                    Modifier.offset(x = 46.dp, y = 38.dp)
                                }
                            )
                            .graphicsLayer { alpha = 1f - e; translationY = -18.dp.toPx() * e }
                            .widthIn(max = 600.dp)
                    ) {
                        HeroLogo(h)
                        PText(h.appLabel.uppercase(), 11.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.72f), letterSpacing = 1.4.sp, shadow = true)
                        VSpace(2.dp)
                        val titleLogo = logoFor(h)
                        if (titleLogo != null) {
                            VSpace(6.dp)
                            TitleLogo(titleLogo, h.title, maxW = 400.dp, maxH = 112.dp)
                        } else {
                            val long = h.title.length > 26
                            PText(h.title, if (long) 34.sp else 50.sp, FontWeight.Bold, Color.White, maxLines = 2, letterSpacing = if (long) (-0.6).sp else (-1.2).sp, shadow = true, lineHeight = if (long) 38.sp else 52.sp)
                        }
                        VSpace(8.dp)
                        MetaRow(h)
                    }
                }
                // Expanded ("full screen" Top Shelf): details bottom-left with actions.
                if (e > 0.01f) {
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 56.dp, bottom = 64.dp)
                            .graphicsLayer { alpha = e; translationY = 26.dp.toPx() * (1f - e) }
                            .widthIn(max = 560.dp)
                    ) {
                        if (h.title.isNotBlank()) {
                            HeroLogo(h)
                            PText(h.appLabel.uppercase(), 11.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.72f), letterSpacing = 1.4.sp, shadow = true)
                            VSpace(4.dp)
                            val titleLogo = logoFor(h)
                            if (titleLogo != null) {
                                VSpace(6.dp)
                                TitleLogo(titleLogo, h.title, maxW = 380.dp, maxH = 104.dp)
                            } else {
                                val longX = h.title.length > 26
                                PText(h.title, if (longX) 32.sp else 44.sp, FontWeight.Bold, Color.White, maxLines = 2, letterSpacing = if (longX) (-0.5).sp else (-1).sp, shadow = true, lineHeight = if (longX) 36.sp else 46.sp)
                            }
                            VSpace(8.dp)
                            MetaRow(h)
                        }
                        if (h.description.isNotEmpty()) {
                            VSpace(12.dp)
                            PText(h.description, 15.sp, FontWeight.Normal, Color.White.copy(alpha = 0.86f), maxLines = 3, shadow = true, lineHeight = 21.sp)
                        }
                        VSpace(20.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (h.media) {
                                // Now Playing: play/pause and next, for the app that's playing.
                                HeroButton(
                                    if (h.playing) "Pause" else "Play",
                                    if (h.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    focusedButton == 0,
                                )
                                HSpace(14.dp)
                                HeroButton(null, Icons.Rounded.SkipNext, focusedButton == 1)
                            } else {
                                HeroButton("Play", Icons.Rounded.PlayArrow, focusedButton == 0)
                                HSpace(14.dp)
                                HeroButton(null, Icons.Rounded.Info, focusedButton == 1)
                            }
                        }
                    }
                }
            }
        }
        if (count > 1 && e > 0.01f) {
            PageDots(
                count, index,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .graphicsLayer { alpha = e }
            )
        }
    }
}

/** A title's clear logo in place of its name: fitted to [maxW] × [maxH], left-aligned, with a soft shadow. */
@Composable
private fun TitleLogo(logo: ImageBitmap, title: String, maxW: Dp, maxH: Dp) {
    val ratio = (logo.width.toFloat() / logo.height.coerceAtLeast(1)).coerceIn(0.5f, 8f)
    val w = minOf(maxW, maxH * ratio)
    val h = w / ratio
    Box(Modifier.size(w, h)) {
        Image(
            logo, null,
            Modifier
                .fillMaxSize()
                .offset(1.5.dp, 2.dp)
                .graphicsLayer { alpha = 0.45f },
            contentScale = ContentScale.Fit,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Black),
        )
        Image(logo, title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun HeroLogo(item: HeroItem) {
    val logo = item.logo ?: return
    Image(
        logo, null,
        Modifier
            .height(64.dp)
            .width(114.dp)
            .shadow(14.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop,
    )
    VSpace(12.dp)
}

@Composable
private fun MetaRow(item: HeroItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (item.meta.isNotEmpty()) {
            PText(item.meta, 14.sp, FontWeight.Medium, Color.White.copy(alpha = 0.86f), shadow = true)
        }
        item.badges.forEach {
            HSpace(8.dp)
            Badge(it)
        }
    }
}

@Composable
private fun HeroButton(label: String?, icon: ImageVector, focused: Boolean) {
    val theme = LocalPrism.current
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, spring(dampingRatio = 0.6f, stiffness = 380f), label = "heroBtn")
    val fg = if (focused) Color(0xFF111318) else Color.White
    val base = Modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .height(46.dp)
        .then(if (label == null) Modifier.width(46.dp) else Modifier)
    val surface = if (focused) {
        Modifier.shadow(18.dp, Pill).background(Color.White, Pill)
    } else {
        Modifier.glass(Pill, theme.pillFill(), GlassSource.Background)
    }
    Box(
        base.then(surface).padding(horizontal = if (label != null) 24.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PIcon(icon, fg, if (label != null) 22.dp else 24.dp)
            if (label != null) {
                HSpace(6.dp)
                PText(label, 16.sp, FontWeight.SemiBold, fg)
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, index: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == index) 18.dp else 7.dp, label = "dot")
            Box(
                Modifier
                    .size(w, 7.dp)
                    .background(Color.White.copy(alpha = if (i == index) 0.95f else 0.42f), Pill)
            )
        }
    }
}

// ------------------------------------------------------------------------------------------ grid + dock

@Composable
private fun AppGrid(
    items: List<GridItem>,
    cols: Int,
    trayTop: State<Float>,
    rowGap: State<Float>,
    below: State<Float>,
    focusIndex: Int,
    gridMode: Boolean,
    showHint: Boolean,
    palette: PrismPalette,
    movingId: String?,
    backdropLuma: Float,
) {
    val theme = LocalPrism.current
    val iconW = HomeLayout.iconW(cols)
    val iconH = HomeLayout.iconH(cols)
    val trayH = HomeLayout.trayH(cols)
    val focusRow = if (focusIndex >= 0) focusIndex / cols else -1
    val labelColor = homeLabelColor(gridMode, backdropLuma, palette)

    Box(Modifier.fillMaxSize()) {
        // "Swipe up" affordance above the dock.
        val hintAlpha by animateFloatAsState(if (showHint) 1f else 0f, tween(300), label = "hint")
        if (hintAlpha > 0.01f) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, (trayTop.value - 40f).dp.roundToPx()) }
                    .graphicsLayer { alpha = hintAlpha },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PIcon(Icons.Rounded.KeyboardArrowUp, Color.White.copy(alpha = 0.85f), 20.dp)
                PText("Swipe up for full screen", 10.sp, FontWeight.Medium, Color.White.copy(alpha = 0.7f), shadow = true)
            }
        }

        // The glass dock tray.
        Box(
            Modifier
                .offset { IntOffset((HomeLayout.left() - HomeLayout.TrayInset).dp.roundToPx(), trayTop.value.dp.roundToPx()) }
                .size((HomeLayout.GridW + HomeLayout.TrayInset * 2).dp, trayH.dp)
                .glass(RoundedCornerShape(34.dp), theme.trayFill(), GlassSource.Background, rimStrength = 1.1f)
        )

        items.forEachIndexed { i, item ->
            val r = i / cols
            val c = i % cols
            // Cull rows far below the focus.
            if (r > focusRow.coerceAtLeast(0) + 4) return@forEachIndexed
            key(item.id) {
                // Tiles glide to new slots when apps are moved, hidden or foldered.
                val slide = spring<Float>(dampingRatio = 0.82f, stiffness = 340f)
                val ax by animateFloatAsState(HomeLayout.x(cols, c), slide, label = "tileX")
                val ar by animateFloatAsState(r.toFloat(), slide, label = "tileRow")
                val focused = i == focusIndex
                // Like tvOS, only the focused tile changes size; its neighbours stay put.
                val unfocusedScale = 1f
                val dockHidden = r == 0 && !gridMode
                val showLabel = when (theme.labels) {
                    LabelMode.Always -> !dockHidden
                    LabelMode.Never -> false
                    LabelMode.Focused -> focused && !dockHidden
                }
                GridTile(
                    item = item,
                    focused = focused,
                    moving = item.id == movingId,
                    unfocusedScale = unfocusedScale,
                    showLabel = showLabel,
                    labelColor = labelColor,
                    labelOffset = if (r == 0) HomeLayout.TrayPad - 6f else 0f,
                    tileH = iconH,
                    modifier = Modifier
                        .offset {
                            val r0 = ar.toInt().coerceAtLeast(0)
                            val fr = (ar - r0).coerceIn(0f, 1f)
                            val y0 = HomeLayout.y(cols, r0, trayTop.value, rowGap.value, below.value)
                            val y1 = HomeLayout.y(cols, r0 + 1, trayTop.value, rowGap.value, below.value)
                            IntOffset(ax.dp.roundToPx(), lerpF(y0, y1, fr).dp.roundToPx())
                        }
                        .size(iconW.dp, iconH.dp),
                )
            }
        }
    }
}

@Composable
fun GridTile(
    item: GridItem,
    focused: Boolean,
    unfocusedScale: Float,
    showLabel: Boolean,
    labelColor: Color,
    labelOffset: Float,
    modifier: Modifier,
    tileH: Float = 74.4f,
    moving: Boolean = false,
) {
    val theme = LocalPrism.current
    val effect = theme.focusEffect
    val target = when {
        moving -> HomeLayout.FocusScale + 0.06f
        focused -> if (effect == FocusEffect.Lift) 1.17f else HomeLayout.FocusScale
        else -> unfocusedScale
    }
    // Grow & Shrink matches tvOS: a quick grow that settles with a little give (more with Focus Wobble,
    // as on Apple TV) and a fully damped shrink back as focus moves on. Grow keeps a springier pop.
    val wobbleOn = theme.wobble && effect != FocusEffect.Lift && !moving
    val spec = when {
        effect == FocusEffect.Grow -> spring<Float>(dampingRatio = 0.62f, stiffness = 300f)
        (focused || moving) && wobbleOn -> spring(dampingRatio = 0.6f, stiffness = 380f)
        focused || moving -> spring(dampingRatio = 0.82f, stiffness = 520f)
        else -> spring(dampingRatio = 1f, stiffness = 620f)
    }
    val sway = rememberWobble(focused, wobbleOn)
    val scale by animateFloatAsState(target, spec, label = "tileScale")
    val elevation by animateDpAsState(
        if (moving) 34.dp else if (focused) 28.dp else 3.dp,
        if (focused) tween(220, easing = FastOutSlowInEasing) else tween(140),
        label = "tileElev",
    )
    val lift by animateFloatAsState(if (focused && effect == FocusEffect.GrowShrink) 1f else 0f, tween(200, easing = FastOutSlowInEasing), label = "lift")
    // Rearranging: the picked-up tile wiggles, as on tvOS.
    val wiggle = if (moving) {
        val inf = rememberInfiniteTransition(label = "wiggle")
        inf.animateFloat(
            -1f, 1f,
            infiniteRepeatable(tween(150, easing = LinearEasing), RepeatMode.Reverse),
            label = "wiggleT",
        ).value
    } else 0f
    val labelAlpha by animateFloatAsState(if (showLabel) 1f else 0f, tween(220), label = "label")
    val sheen = remember { Animatable(-1f) }
    LaunchedEffect(focused, effect) {
        if (focused && effect == FocusEffect.Lift && !theme.lite) {
            sheen.snapTo(0f)
            sheen.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        } else {
            sheen.snapTo(-1f)
        }
    }
    val glow = when (item) {
        is AppItem -> item.app.tint
        is FolderItem -> Color.Black
    }
    val liquid = theme.liquid
    Box(modifier.wobble(sway)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = elevation.toPx()
                    shape = TileShape
                    clip = true
                    ambientShadowColor = glow
                    spotShadowColor = glow
                    if (effect == FocusEffect.Lift && focused) rotationX = 3f
                    rotationZ = wiggle * 1.6f
                    translationY = -3.dp.toPx() * lift
                }
                .drawWithContent {
                    drawContent()
                    if (lift > 0.01f) {
                        // tvOS: light catches the top of the focused tile.
                        drawRect(
                            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f * lift), 0.5f to Color.Transparent)
                        )
                    }
                    if (focused && liquid) {
                        // Specular sheen + rim, like light catching the edge of a glass tile.
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.20f),
                                0.42f to Color.Transparent,
                            )
                        )
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.35f)),
                                start = Offset.Zero, end = Offset(size.width, size.height),
                            ),
                            cornerRadius = CornerRadius(13.dp.toPx()),
                            style = Stroke(1.6.dp.toPx()),
                        )
                    }
                    val s = sheen.value
                    if (s >= 0f) {
                        val x0 = -size.width * 0.6f + s * size.width * 2.1f
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.34f), Color.Transparent),
                                start = Offset(x0, 0f), end = Offset(x0 + size.width * 0.42f, size.height),
                            )
                        )
                    }
                }
        ) {
            when (item) {
                is AppItem -> AppArtwork(item.app)
                is FolderItem -> FolderArtwork(
                    item,
                    plate = Color.Transparent,
                    modifier = Modifier
                        .fillMaxSize()
                        .glass(
                            TileShape,
                            when {
                                theme.clear -> theme.palette.liquidTint.copy(alpha = 0.10f)
                                theme.liquid -> theme.palette.liquidTint.copy(alpha = if (theme.palette.isDark) 0.30f else 0.34f)
                                else -> theme.palette.panelGlass.copy(alpha = 0.62f)
                            },
                            GlassSource.Background,
                            rimStrength = 0.9f,
                            frosted = !theme.liquid,
                        ),
                )
            }
        }
        if (labelAlpha > 0.01f) {
            PText(
                item.label, 15.sp, FontWeight.Medium, labelColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .requiredWidth(200.dp)
                    .offset { IntOffset(0, (tileH * (scale - 1f) / 2f + labelOffset + 30f).dp.roundToPx()) }
                    .graphicsLayer { alpha = labelAlpha },
                align = TextAlign.Center,
                shadow = labelColor == Color.White,
            )
        }
    }
}

// ------------------------------------------------------------------------------------------ weather

/** A multicolour weather symbol, like tvOS's: yellow sun, white clouds, blue rain. */
@Composable
fun WeatherGlyph(w: Weather, size: Dp, monochrome: Boolean = false, ink: Color = Color.White) {
    val (icon, tint) = when (w.kind()) {
        WeatherKind.Clear -> if (w.isDay) Icons.Rounded.WbSunny to Color(0xFFFFD60A) else Icons.Rounded.NightsStay to Color(0xFFE5E5EA)
        WeatherKind.PartlyCloudy -> if (w.isDay) Icons.Rounded.WbCloudy to Color(0xFFF2F2F7) else Icons.Rounded.NightsStay to Color(0xFFE5E5EA)
        WeatherKind.Cloudy -> Icons.Rounded.Cloud to Color(0xFFE5E5EA)
        WeatherKind.Fog -> Icons.Rounded.Dehaze to Color(0xFFD1D1D6)
        WeatherKind.Drizzle -> Icons.Rounded.Grain to Color(0xFF64D2FF)
        WeatherKind.Rain -> Icons.Rounded.Umbrella to Color(0xFF64D2FF)
        WeatherKind.Snow -> Icons.Rounded.AcUnit to Color(0xFFE5F6FF)
        WeatherKind.Storm -> Icons.Rounded.FlashOn to Color(0xFFFFD60A)
    }
    val partlySun = w.kind() == WeatherKind.PartlyCloudy && w.isDay
    Box(Modifier.size(size)) {
        if (partlySun) {
            // Sun peeking from behind the cloud.
            PIcon(Icons.Rounded.WbSunny, if (monochrome) ink else Color(0xFFFFD60A), size * 0.72f, Modifier.align(Alignment.TopEnd))
        }
        PIcon(icon, if (monochrome) ink else tint, if (partlySun) size * 0.8f else size, Modifier.align(Alignment.BottomStart))
    }
}

// ------------------------------------------------------------------------------------------ move hint

@Composable
private fun MoveHint(visible: Boolean, modifier: Modifier) {
    val theme = LocalPrism.current
    val p = theme.palette
    val tState = animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 300f), label = "moveHint")
    val t by tState
    if (!tState.atLeast(0.01f)) return
    Row(
        modifier
            .padding(bottom = 22.dp)
            .graphicsLayer { alpha = t; translationY = (1f - t) * 30.dp.toPx() }
            .height(38.dp)
            .glass(Pill, theme.controlGlass(), GlassSource.Background, elevation = 10.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (p.isDark) Color.White else p.textPrimary
        PIcon(Icons.Rounded.OpenWith, fg, 18.dp)
        HSpace(10.dp)
        PText("Move with the arrows · Press OK when done", 14.sp, FontWeight.Medium, fg)
    }
}

// ------------------------------------------------------------------------------------------ time pill

/**
 * Wi-Fi as iOS draws it: a wedge and two arcs, those above the signal faint. Offline: all faint and
 * struck through.
 */
@Composable
private fun WifiGlyph(bars: Int, offline: Boolean, ink: Color, size: Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2f, h * 0.80f)
        val faint = ink.copy(alpha = ink.alpha * 0.32f)
        fun arcSize(r: Float) = androidx.compose.ui.geometry.Size(r * 2f, r * 2f)
        val wedge = w * 0.23f
        drawArc(
            if (bars >= 1) ink else faint, -135f, 90f, useCenter = true,
            topLeft = Offset(c.x - wedge, c.y - wedge), size = arcSize(wedge),
        )
        val stroke = Stroke(w * 0.12f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        listOf(0.40f, 0.61f).forEachIndexed { i, k ->
            val r = w * k
            drawArc(
                if (bars >= i + 2) ink else faint, -135f, 90f, useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = arcSize(r), style = stroke,
            )
        }
        if (offline) {
            drawLine(ink, Offset(w * 0.16f, h * 0.10f), Offset(w * 0.84f, h * 0.90f), w * 0.11f, androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

@Composable
private fun TimePill(
    time: String,
    weather: Weather?,
    network: com.prismtv.launcher.system.NetStatus?,
    notifications: Int,
    unit: TemperatureUnit,
    focus: Int,
    modifier: Modifier,
) {
    val theme = LocalPrism.current
    val p = theme.palette
    val pillFocused = focus == 0
    val scale by animateFloatAsState(if (pillFocused) 1.1f else 1f, spring(dampingRatio = 0.6f, stiffness = 380f), label = "pillScale")
    val surface = if (pillFocused) {
        Modifier.shadow(16.dp, Pill).background(theme.controlFocused(), Pill)
    } else {
        Modifier.glass(Pill, theme.pillFill(), GlassSource.Background, rimStrength = 0.9f)
    }
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f) }
            .height(34.dp)
            .then(surface)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (pillFocused) p.textFocused else if (p.isDark) Color.White else p.textPrimary
        PText(time, 14.sp, FontWeight.SemiBold, ink)
        if (weather != null) {
            HSpace(10.dp)
            WeatherGlyph(weather, 17.dp, monochrome = pillFocused, ink = ink)
            HSpace(4.dp)
            PText(weather.label(unit), 14.sp, FontWeight.SemiBold, ink)
        }
        // Wi-Fi only: a wired TV shows nothing here (Control Centre still says Ethernet).
        if (network != null && !network.ethernet) {
            HSpace(10.dp)
            WifiGlyph(if (network.connected) network.bars else 0, offline = !network.connected, ink = ink, size = 17.dp)
        }
        if (notifications > 0) {
            HSpace(10.dp)
            Box(Modifier.size(22.dp)) {
                PIcon(Icons.Rounded.Notifications, ink, 19.dp, Modifier.align(Alignment.Center))
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(5.dp, (-4).dp)
                        .height(14.dp)
                        .widthIn(min = 14.dp)
                        .background(Color(0xFFFF3B30), Pill)
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PText(if (notifications > 9) "9+" else notifications.toString(), 9.sp, FontWeight.Bold, Color.White)
                }
            }
        }
        HSpace(9.dp)
        Avatar(26.dp, focused = focus == 1)
    }
}

