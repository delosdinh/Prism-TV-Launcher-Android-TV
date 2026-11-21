package com.prismtv.launcher.ui.settings

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
            // Settings are always frosted glass over the Home Screen, whatever the material.
            .glass(
                RectangleShape,
                if (p.isDark) p.bgBase.copy(alpha = 0.66f) else Color.White.copy(alpha = 0.58f),
                GlassSource.Scene, rimStrength = 0f, lens = false, frosted = true,
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
            Box(Modifier.fillMaxSize()) {
                PText(
                    pg.title, 26.sp, FontWeight.SemiBold, p.textPrimary,
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 30.dp),
                )
                val rows = vm.settingsRows(pg)
                val focus = (vm.settingsFocus[pg] ?: 0).coerceIn(0, (rows.size - 1).coerceAtLeast(0))
                // Each tab has its own glyph; on the main list it follows focus, like tvOS.
                val art: Any = when (pg) {
                    SettingsPage.Root -> rows.getOrNull(focus)?.let { glyphFor(it.id) } ?: LogoGlyph
                    SettingsPage.Apps -> rows.getOrNull(focus)?.app ?: pageGlyph(pg)
                    else -> pageGlyph(pg)
                }
                PageArt(
                    art,
                    Modifier
                        .offset(126.dp, 156.dp)
                        .size(228.dp),
                    p,
                )
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
    "sleep" to glyph(Icons.Rounded.Timer, 0xFFC27BFF, 0xFF8A3FD1),
    "about" to glyph(Icons.Rounded.Info, 0xFF9C9CA3, 0xFF5E5E64),
    "restart" to glyph(Icons.Rounded.RestartAlt, 0xFF9C9CA3, 0xFF5E5E64),
    "reset" to glyph(Icons.Rounded.Restore, 0xFFFF6961, 0xFFD63A31),
    "upd:check" to glyph(Icons.Rounded.SystemUpdate, 0xFF5AC8FA, 0xFF0A6CFF),
    "ss:range" to glyph(Icons.Rounded.HdrOn, 0xFFFFC94D, 0xFFE08A00),
)

private fun glyphFor(id: String): Any? = if (id == "about") LogoGlyph else Glyphs[id]

private fun pageGlyph(page: SettingsPage): Any = when (page) {
    SettingsPage.Root, SettingsPage.About -> LogoGlyph
    SettingsPage.Appearance -> Glyphs.getValue("appearance")
    SettingsPage.HomeScreen -> Glyphs.getValue("home")
    SettingsPage.Screensaver -> Glyphs.getValue("screensaver")
    SettingsPage.Apps -> Glyphs.getValue("apps")
}

/** The large artwork on the left of Settings: the tab's glyph, the logo, or an app's artwork. */
@Composable
private fun PageArt(art: Any, modifier: Modifier, p: PrismPalette) {
    // A quick dip-and-pop between glyphs (tvOS swaps the artwork the same way). Drawn without
    // offscreen layers or elevation shadows so nothing gets clipped mid-animation.
    var shown by remember { mutableStateOf(art) }
    val v = remember { Animatable(1f) }
    LaunchedEffect(art) {
        if (art == shown && v.value == 1f) return@LaunchedEffect
        v.animateTo(0f, tween(100, easing = FastOutLinearInEasing))
        shown = art
        v.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 440f))
    }
    Box(
        modifier.graphicsLayer {
            val k = v.value
            alpha = k.coerceIn(0f, 1f)
            val s = 0.9f + 0.1f * k
            scaleX = s
            scaleY = s
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
        contentAlignment = Alignment.Center,
    ) {
        when (val a = shown) {
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
            else -> LogoTile(Modifier.size(228.dp), p)
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
                // Lay out the whole list (not just what fits the viewport) so long pages scroll fully.
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
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
            .background(p.buttonUnfocused.copy(alpha = if (p.isDark) 0.46f else 0.62f), RowShape)
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f))), RowShape)
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

