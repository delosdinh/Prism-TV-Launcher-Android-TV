package com.prismtv.launcher.ui.home

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.LauncherViewModel
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
import com.prismtv.launcher.ui.glass.backdropSource
import com.prismtv.launcher.ui.glass.glass
import com.prismtv.launcher.ui.theme.LocalPrism
import com.prismtv.launcher.ui.theme.PrismPalette

private val TileShape = RoundedCornerShape(13.dp)
private val Pill = RoundedCornerShape(50)

@Composable
fun HomeScreen(vm: LauncherViewModel, clock: String) {
    val theme = LocalPrism.current
    val p = theme.palette
    val cols = vm.prefs.columns
    val gridMode = vm.isGridMode
    val expanded = vm.isHeroExpanded
    val heroOn = vm.prefs.heroEnabled

    val trayTopTarget = when {
        expanded -> HomeLayout.TrayTopHidden
        gridMode -> HomeLayout.gridTrayTop(cols, vm.row)
        else -> HomeLayout.TrayTopHero
    }
    val motion = spring<Float>(dampingRatio = 0.86f, stiffness = 160f)
    val trayTop = animateFloatAsState(trayTopTarget, motion, label = "trayTop")
    val rowGap = animateFloatAsState(if (gridMode) HomeLayout.RowGapGrid else HomeLayout.RowGapHero, motion, label = "rowGap")

    Box(Modifier.fillMaxSize()) {
        HomeBackdrop(vm.currentArt, blurred = gridMode, palette = p, backdrop = LocalBackdrop.current)

        HeroOverlay(
            item = vm.currentHeroItem,
            count = vm.currentHeroItems.size,
            index = vm.heroIndex,
            expanded = expanded,
            visible = heroOn && !gridMode && vm.overlay != Overlay.Sidebar,
            focusedButton = if (vm.zone == Zone.Hero && vm.overlay == Overlay.None) vm.heroButton else -1,
        )

        val focusIndex = if (vm.zone == Zone.Grid && vm.overlay != Overlay.Sidebar) vm.row * cols + vm.col else -1
        AppGrid(
            items = vm.items,
            cols = cols,
            trayTop = trayTop,
            rowGap = rowGap,
            focusIndex = focusIndex,
            gridMode = gridMode,
            showHint = heroOn && !gridMode && !expanded && vm.overlay == Overlay.None && vm.movingId == null && vm.contextMenu == null,
            palette = p,
            movingId = vm.movingId,
        )

        MoveHint(visible = vm.movingId != null, modifier = Modifier.align(Alignment.BottomCenter))

        val pillVisible = !gridMode && vm.overlay != Overlay.ControlCentre
        val pillAlpha by animateFloatAsState(if (pillVisible) 1f else 0f, tween(300), label = "pill")
        TimePill(
            time = clock,
            focus = if (vm.zone == Zone.TopBar && vm.overlay == Overlay.None) vm.topIndex else -1,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 27.dp)
                .graphicsLayer { alpha = pillAlpha },
        )
    }
}

// ------------------------------------------------------------------------------------------ backdrop

@Composable
private fun HomeBackdrop(art: HeroArt?, blurred: Boolean, palette: PrismPalette, backdrop: BackdropState) {
    val blur = animateFloatAsState(if (blurred) 1f else 0f, tween(650, easing = FastOutSlowInEasing), label = "wallBlur")
    Box(
        Modifier
            .fillMaxSize()
            .backdropSource(backdrop, GlassSource.Background)
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(palette.bgAccent, palette.bgBase))))
        Crossfade(targetState = art, animationSpec = tween(900), label = "heroArt") { a ->
            if (a != null) {
                Box(Modifier.fillMaxSize()) {
                    Image(a.full, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
        // Wallpaper tint while browsing the grid.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = blur.value }
                .background(palette.bgBase.copy(alpha = palette.backgroundDim * 0.6f))
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
                // Collapsed: title treatment top-left, like tvOS Top Shelf.
                if (e < 0.99f && h.title.isNotBlank()) {
                    Column(
                        Modifier
                            .offset(x = 46.dp, y = 38.dp)
                            .graphicsLayer { alpha = 1f - e; translationY = -18.dp.toPx() * e }
                            .widthIn(max = 600.dp)
                    ) {
                        HeroLogo(h)
                        PText(h.appLabel.uppercase(), 11.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.72f), letterSpacing = 1.4.sp, shadow = true)
                        VSpace(2.dp)
                        val long = h.title.length > 26
                        PText(h.title, if (long) 34.sp else 50.sp, FontWeight.Bold, Color.White, maxLines = 2, letterSpacing = if (long) (-0.6).sp else (-1.2).sp, shadow = true, lineHeight = if (long) 38.sp else 52.sp)
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
                            val longX = h.title.length > 26
                            PText(h.title, if (longX) 32.sp else 44.sp, FontWeight.Bold, Color.White, maxLines = 2, letterSpacing = if (longX) (-0.5).sp else (-1).sp, shadow = true, lineHeight = if (longX) 36.sp else 46.sp)
                            VSpace(8.dp)
                            MetaRow(h)
                        }
                        if (h.description.isNotEmpty()) {
                            VSpace(12.dp)
                            PText(h.description, 15.sp, FontWeight.Normal, Color.White.copy(alpha = 0.86f), maxLines = 3, shadow = true, lineHeight = 21.sp)
                        }
                        VSpace(20.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HeroButton("Play", Icons.Rounded.PlayArrow, focusedButton == 0)
                            HSpace(14.dp)
                            HeroButton(null, Icons.Rounded.Info, focusedButton == 1)
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
    focusIndex: Int,
    gridMode: Boolean,
    showHint: Boolean,
    palette: PrismPalette,
    movingId: String?,
) {
    val theme = LocalPrism.current
    val iconW = HomeLayout.iconW(cols)
    val iconH = HomeLayout.iconH(cols)
    val trayH = HomeLayout.trayH(cols)
    val focusRow = if (focusIndex >= 0) focusIndex / cols else -1
    val labelColor = if (gridMode && !palette.isDark) palette.textPrimary else Color.White

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
                val unfocusedScale = when {
                    focusIndex < 0 -> 1f
                    theme.focusEffect == FocusEffect.GrowShrink -> if (r == focusRow) 0.94f else 0.97f
                    else -> 1f
                }
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
                            val y0 = HomeLayout.y(cols, r0, trayTop.value, rowGap.value)
                            val y1 = HomeLayout.y(cols, r0 + 1, trayTop.value, rowGap.value)
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
    val scale by animateFloatAsState(target, spring(dampingRatio = 0.62f, stiffness = 300f), label = "tileScale")
    val elevation by animateDpAsState(if (moving) 34.dp else if (focused) 26.dp else 4.dp, spring(stiffness = 300f), label = "tileElev")
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
        if (focused && effect == FocusEffect.Lift) {
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
    Box(modifier) {
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
                }
                .drawWithContent {
                    drawContent()
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
                    plate = if (theme.palette.isDark) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.42f),
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, Color.White.copy(alpha = 0.22f), TileShape),
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

// ------------------------------------------------------------------------------------------ move hint

@Composable
private fun MoveHint(visible: Boolean, modifier: Modifier) {
    val theme = LocalPrism.current
    val p = theme.palette
    val t by animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 300f), label = "moveHint")
    if (t < 0.01f) return
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

@Composable
private fun TimePill(time: String, focus: Int, modifier: Modifier) {
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
        PText(time, 14.sp, FontWeight.SemiBold, if (pillFocused) p.textFocused else if (p.isDark) Color.White else p.textPrimary)
        HSpace(9.dp)
        Avatar(26.dp, focused = focus == 1)
    }
}

