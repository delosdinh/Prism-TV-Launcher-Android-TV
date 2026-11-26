package com.prismtv.launcher.ui.keyboard

import androidx.compose.animation.core.RepeatMode
import com.prismtv.launcher.ui.components.atLeast
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.KeyboardCapslock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.LauncherViewModel
import com.prismtv.launcher.ui.components.HSpace
import com.prismtv.launcher.ui.components.PIcon
import com.prismtv.launcher.ui.components.PText
import com.prismtv.launcher.ui.components.lerpF
import com.prismtv.launcher.ui.glass.GlassSource
import com.prismtv.launcher.ui.glass.glass
import com.prismtv.launcher.ui.theme.Appearance
import com.prismtv.launcher.ui.theme.LocalPrism
import com.prismtv.launcher.ui.theme.Palettes
import com.prismtv.launcher.data.GlassMaterial
import androidx.compose.runtime.CompositionLocalProvider

/** Key and panel colours for each keyboard look (Balanced is Prism's own indigo variant). */
data class KeyColours(
    val panel: Color,
    val key: Color,
    val function: Color,
    val ink: Color,
    val focus: Color,
    val focusInk: Color,
    val field: Color,
    val fieldInk: Color,
    val bar: Color,
    /** Clear Liquid Glass: see-through keys, so labels carry a soft shadow. */
    val clear: Boolean = false,
)

/** Clear Liquid Glass keys: barely-there glass with white labels. */
val ClearKeyColours = KeyColours(
    panel = Color.White.copy(alpha = 0.05f), key = Color.White.copy(alpha = 0.17f), function = Color.White.copy(alpha = 0.10f),
    ink = Color.White, focus = Color.White.copy(alpha = 0.95f), focusInk = Color(0xFF111318),
    field = Color.White.copy(alpha = 0.10f), fieldInk = Color.White, bar = Color.White.copy(alpha = 0.35f), clear = true,
)

fun keyColoursFor(look: Appearance, clear: Boolean = false): KeyColours = if (clear) ClearKeyColours else when (look) {
    Appearance.Light -> KeyColours(
        panel = Color(0xFFD3D6DE).copy(alpha = 0.78f), key = Color.White, function = Color(0xFFF3F4F7),
        ink = Color(0xFF0B0C0F), focus = Color(0xFF1C1D22), focusInk = Color.White,
        field = Color.White.copy(alpha = 0.86f), fieldInk = Color(0xFF0B0C0F), bar = Color(0xFFBFC3CE),
    )
    Appearance.Balanced -> KeyColours(
        panel = Color(0xFF3E4370).copy(alpha = 0.80f), key = Color(0xFF6C71A3), function = Color(0xFF585D8E),
        ink = Color.White, focus = Color(0xFFE6E7FF), focusInk = Color(0xFF181B30),
        field = Color(0xFF555A88).copy(alpha = 0.90f), fieldInk = Color.White, bar = Color(0xFF8F8FB5),
    )
    Appearance.Dark -> KeyColours(
        panel = Color(0xFF1E1E21).copy(alpha = 0.80f), key = Color(0xFF4A4A4F), function = Color(0xFF3A3A3E),
        ink = Color.White, focus = Color(0xFFE4E5E9), focusInk = Color(0xFF111318),
        field = Color(0xFF3A3A3E).copy(alpha = 0.92f), fieldInk = Color.White, bar = Color(0xFF6A6A70),
    )
}

/** Sizes for one place the keyboard appears. */
data class KeyboardMetrics(
    val width: Float,
    val keyH: Float,
    val gap: Float,
    val pad: Float,
    val barH: Float,
    /** Key height on the ABC grid, which has six rows. */
    val gridKeyH: Float = keyH,
) {
    val unit: Float get() = (width - pad * 2 - gap * 9) / 10f
    /** Four rows of QWERTY, or six of the ABC grid (every layer is drawn at that height). */
    val keysH: Float get() = if (KeyboardLayout.rows() > 4) gridKeyH * 6 + gap * 5 else keyH * 4 + gap * 3
    val height: Float get() = pad * 2 + barH + gap + keysH

    /** The block of keys split into [rows] rows: the ABC grid's 123 and emoji layers get taller keys. */
    fun forRows(rows: Int): KeyboardMetrics = copy(keyH = (keysH - gap * (rows - 1)) / rows)

    companion object {
        /** Prism's own text entry (over the Home Screen). */
        val Overlay = KeyboardMetrics(width = 900f, keyH = 44f, gap = 8f, pad = 14f, barH = 34f, gridKeyH = 37f)
        /** The ABC grid down the left of the screen, as on Apple TV (Prism's own Search and text entry). */
        val Side = KeyboardMetrics(width = 316f, keyH = 34f, gap = 5f, pad = 14f, barH = 30f, gridKeyH = 34f)
        /** The system keyboard in other apps: a little shorter so more of the app stays visible. */
        val System = KeyboardMetrics(width = 880f, keyH = 40f, gap = 7f, pad = 10f, barH = 32f, gridKeyH = 33f)
    }
}

/** The keys, the suggestion bar and the emoji grid, laid out on [m]. No background. */
@Composable
fun KeyboardKeys(
    kb: KeyboardController,
    c: KeyColours,
    m: KeyboardMetrics,
    active: Boolean = true,
    /** Apple TV style: letters without key caps; only the focused key (and the lit switch) has a tile. */
    bare: Boolean = false,
) {
    Box(Modifier.size((m.width - m.pad * 2).dp, (m.barH + m.gap + m.keysH).dp)) {
        SuggestionBar(kb, c, m, active)
        Box(
            Modifier
                .offset(0.dp, (m.barH + m.gap).dp)
                .graphicsLayer { alpha = if (kb.listening) 0.45f else 1f }
        ) {
            if (kb.layer == KeyboardLayout.Emoji) EmojiKeys(kb, c, m.forRows(4), active)
            else {
                val lm = m.forRows(KeyboardLayout.of(kb.layer).size)
                LetterKeys(kb, c, lm, active, bare)
                AccentStrip(kb, c, lm, active)
            }
        }
    }
}

@Composable
private fun LetterKeys(kb: KeyboardController, c: KeyColours, m: KeyboardMetrics, active: Boolean, bare: Boolean) {
    val layer = kb.layer
    val rows = KeyboardLayout.of(layer)
    rows.forEachIndexed { r, keys ->
        var x = KeyboardLayout.indent(layer, r) * (m.unit + m.gap)
        val y = r * (m.keyH + m.gap)
        keys.forEachIndexed { i, key ->
            val w = m.unit * key.units + m.gap * (key.units - 1f)
            KeyCap(
                key = key,
                focused = active && r == kb.row && i == kb.col,
                shift = kb.shift || kb.capsLock,
                capsLock = kb.capsLock,
                listening = kb.listening,
                actionLabel = kb.actionLabel,
                c = c,
                // ABC grid: the switch for the layer you're on is lit, like tvOS's abc / 123.
                selected = KeyboardLayout.isSwitchRow(layer, r) && (
                    (key.kind == KeyKind.Shift && layer == KeyboardLayout.Letters) ||
                        (key.kind == KeyKind.Layer && key.target == layer)
                    ),
                bare = bare,
                modifier = Modifier
                    .offset(x.dp, y.dp)
                    .size(w.dp, m.keyH.dp),
            )
            x += w + m.gap
        }
    }
}

/** Held on a key with accents: its variants in a strip rising from it, as on iOS. */
@Composable
private fun AccentStrip(kb: KeyboardController, c: KeyColours, m: KeyboardMetrics, active: Boolean) {
    val list = kb.accents ?: return
    if (!active) return
    val layer = kb.layer
    val row = kb.row.coerceAtLeast(0)
    val keys = KeyboardLayout.of(layer).getOrNull(row) ?: return
    val col = kb.col.coerceIn(0, keys.size - 1)
    val cell = m.unit + m.gap
    val keyCx = KeyboardLayout.centre(layer, row, col) * cell - m.gap / 2f
    val pad = 6f
    val inner = 4f
    val stripW = list.size * m.unit + (list.size - 1) * inner + pad * 2
    val stripH = m.keyH + pad * 2
    val x = (keyCx - stripW / 2f).coerceIn(0f, (m.width - m.pad * 2 - stripW).coerceAtLeast(0f))
    val y = row * (m.keyH + m.gap) - stripH + 2f
    val shape = RoundedCornerShape(14.dp)
    val appear = remember(list) { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(list) { appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 520f)) }
    val lightKeys = !c.clear && c.key.luminance() > 0.7f
    Box(
        Modifier
            .offset(x.dp, y.dp)
            .size(stripW.dp, stripH.dp)
            // Over the held key, which is raised as the focused key (it overlaps its top by a few dp).
            .zIndex(2f)
            .graphicsLayer {
                val a = appear.value
                alpha = a.coerceIn(0f, 1f)
                scaleX = 0.88f + 0.12f * a
                scaleY = 0.88f + 0.12f * a
                // Grows out of the held key.
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(((keyCx - x) / stripW).coerceIn(0f, 1f), 1f)
                shadowElevation = 18.dp.toPx()
                this.shape = shape
                clip = true
            }
            // Solid enough to read over the keys, whatever the keyboard's colour. Over white keys (Light)
            // a dark hairline outlines it, as the white strip would otherwise run into the row it covers.
            .background(if (c.clear) Color(0xFF26262B).copy(alpha = 0.94f) else c.key, shape)
            .border(
                if (lightKeys) 1.dp else 0.5.dp,
                if (lightKeys) Color.Black.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.12f),
                shape,
            ),
    ) {
        list.forEachIndexed { i, v ->
            val focused = i == kb.accentIndex
            val sc by animateFloatAsState(if (focused) 1.12f else 1f, spring(dampingRatio = 0.7f, stiffness = 520f), label = "accent")
            Box(
                Modifier
                    .offset((pad + i * (m.unit + inner)).dp, pad.dp)
                    .size(m.unit.dp, m.keyH.dp)
                    .graphicsLayer { scaleX = sc; scaleY = sc }
                    .then(if (focused) Modifier.background(c.focus, RoundedCornerShape(10.dp)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                PText(v, 21.sp, FontWeight.Normal, if (focused) c.focusInk else if (c.clear) Color.White else c.ink, align = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun EmojiKeys(kb: KeyboardController, c: KeyColours, m: KeyboardMetrics, active: Boolean) {
    val list = kb.emojiList()
    val inBar = kb.row == 3
    val cat = EmojiData.categories.getOrNull(kb.emojiCategory)
    // Category name, then three rows of emoji.
    PText(
        (cat?.name ?: "").uppercase(), 10.sp, FontWeight.SemiBold, c.ink.copy(alpha = 0.6f),
        Modifier.offset(4.dp, (-2).dp), letterSpacing = 0.8.sp,
    )
    val cell = m.unit + m.gap
    val gridTop = 14f
    val rowH = (m.keyH * 3 + m.gap * 2 - gridTop) / 3f
    for (vr in 0 until 3) {
        val r = kb.emojiFirstRow + vr
        for (cc in 0 until 10) {
            val i = r * 10 + cc
            val e = list.getOrNull(i)
            if (e != null) key(vr * 10 + cc) {
                val focused = active && !inBar && i == kb.emojiIndex
                val s by animateFloatAsState(if (focused) 1.3f else 1f, spring(dampingRatio = 0.6f, stiffness = 420f), label = "emoji")
                Box(
                    Modifier
                        .offset((cc * cell).dp, (gridTop + vr * rowH).dp)
                        .size(m.unit.dp, rowH.dp)
                        .zIndex(if (focused || s > 1.001f) 1f else 0f)
                        .graphicsLayer { scaleX = s; scaleY = s }
                        .then(if (focused) Modifier.shadow(10.dp, RoundedCornerShape(10.dp)).background(c.focus, RoundedCornerShape(10.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    PText(e, 22.sp, FontWeight.Normal, Color.White, align = TextAlign.Center)
                }
            }
        }
    }
    // Bottom bar: ABC, categories, delete.
    var x = 0f
    val y = 3 * (m.keyH + m.gap)
    KeyboardLayout.emojiBar.forEachIndexed { i, key ->
        val w = m.unit * key.units + m.gap * (key.units - 1f)
        val focused = active && inBar && i == kb.col
        if (key.kind == KeyKind.EmojiCategory) {
            val selected = key.target == kb.emojiCategory
            val s by animateFloatAsState(if (focused) 1.14f else 1f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "cat")
            Box(
                Modifier
                    .offset(x.dp, y.dp)
                    .size(w.dp, m.keyH.dp)
                    .zIndex(if (focused || s > 1.001f) 1f else 0f)
                    .graphicsLayer { scaleX = s; scaleY = s }
                    .background(
                        when {
                            focused -> c.focus
                            selected -> c.function
                            else -> Color.Transparent
                        },
                        RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                PIcon(
                    EmojiData.categories[key.target].icon,
                    if (focused) c.focusInk else c.ink.copy(alpha = if (selected) 1f else 0.6f),
                    20.dp,
                )
            }
        } else {
            KeyCap(
                key, focused, shift = false, capsLock = false, listening = false, actionLabel = kb.actionLabel, c = c,
                modifier = Modifier.offset(x.dp, y.dp).size(w.dp, m.keyH.dp),
            )
        }
        x += w + m.gap
    }
}

/** iOS-style predictions: the word as typed (in quotes), the correction (highlighted), completions. */
@Composable
private fun SuggestionBar(kb: KeyboardController, c: KeyColours, m: KeyboardMetrics, active: Boolean) {
    val w = m.width - m.pad * 2
    val listening = kb.listening
    val notice = kb.notice
    Box(Modifier.size(w.dp, m.barH.dp)) {
        when {
            listening -> {
                val pulse = rememberInfiniteTransition(label = "mic")
                val p by pulse.animateFloat(0.55f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "micP")
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .graphicsLayer { scaleX = p; scaleY = p }
                            .background(Color(0xFFFF375F), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center,
                    ) { PIcon(Icons.Rounded.Mic, Color.White, 16.dp) }
                    HSpace(10.dp)
                    PText(kb.partial.ifBlank { "Listening… speak into your remote" }, 15.sp, FontWeight.Medium, c.ink, maxLines = 1)
                }
            }
            notice != null -> PText(notice, 14.sp, FontWeight.Medium, c.ink.copy(alpha = 0.8f), Modifier.align(Alignment.Center))
            kb.suggestions.isNotEmpty() -> {
                val slotW = w / 3f
                kb.suggestions.take(3).forEachIndexed { i, s ->
                    val focused = active && kb.row == -1 && kb.col == i
                    val sc by animateFloatAsState(if (focused) 1.08f else 1f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "sugg")
                    val corr = s.kind == Suggestion.Kind.Correction
                    Box(
                        Modifier
                            .offset((i * slotW).dp, 0.dp)
                            .size(slotW.dp, m.barH.dp)
                            .zIndex(if (focused || sc > 1.001f) 1f else 0f)
                            .padding(horizontal = 3.dp)
                            .graphicsLayer { scaleX = sc; scaleY = sc }
                            .background(
                                when {
                                    focused -> c.focus
                                    corr -> c.key
                                    else -> Color.Transparent
                                },
                                RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        PText(
                            if (s.kind == Suggestion.Kind.Typed) "“${s.text}”" else s.text,
                            15.sp, if (corr) FontWeight.SemiBold else FontWeight.Medium,
                            if (focused) c.focusInk else c.ink, maxLines = 1, align = TextAlign.Center,
                        )
                    }
                    if (i < 2 && i < kb.suggestions.size - 1) {
                        Box(
                            Modifier
                                .offset(((i + 1) * slotW - 0.5f).dp, 8.dp)
                                .size(1.dp, (m.barH - 16f).dp)
                                .background(c.ink.copy(alpha = 0.18f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyCap(
    key: Key,
    focused: Boolean,
    shift: Boolean,
    capsLock: Boolean,
    listening: Boolean,
    actionLabel: String,
    c: KeyColours,
    modifier: Modifier,
    selected: Boolean = false,
    /** No key cap unless focused or lit, and icons for the wide labels (the narrow Apple TV-style grid). */
    bare: Boolean = false,
) {
    val s by animateFloatAsState(if (focused) 1.14f else 1f, spring(dampingRatio = 0.8f, stiffness = 520f), label = "key")
    val shape = RoundedCornerShape(10.dp)
    val isFunction = key.kind != KeyKind.Char && key.kind != KeyKind.Space
    val action = key.kind == KeyKind.Done && actionLabel != "return"
    val mic = key.kind == KeyKind.Mic && listening
    val bg = when {
        focused -> c.focus
        mic -> Color(0xFFFF375F)
        action -> Color(0xFF0A84FF)
        selected -> if (bare) c.function else c.key
        bare -> Color.Transparent
        key.kind == KeyKind.Shift && shift -> c.key
        isFunction -> c.function
        else -> c.key
    }
    val ink = when {
        focused -> c.focusInk
        mic || action -> Color.White
        else -> c.ink
    }
    Box(
        modifier
            // The focused key pops out over its neighbours (a key drawn after it, like the mic beside the
            // space bar, covered its edge), and stays on top while it settles back.
            .zIndex(if (focused || s > 1.001f) 1f else 0f)
            .graphicsLayer {
                scaleX = s
                scaleY = s
                // See-through Clear keys would show their own shadow through them, so only the focused key has one.
                val lift = ((s - 1f) / 0.14f).coerceIn(0f, 1f)
                shadowElevation = (if (c.clear || bare) lerpF(0f, 14f, lift) else lerpF(1f, 14f, lift)).dp.toPx()
                this.shape = shape
                clip = true
            }
            .background(bg, shape)
            .then(if (bare && bg == Color.Transparent) Modifier else Modifier.border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)),
        contentAlignment = Alignment.Center,
    ) {
        when (key.kind) {
            KeyKind.Char -> PText(if (shift) key.label.uppercase() else key.label, 21.sp, FontWeight.Normal, ink, shadow = c.clear && !focused)
            // The ABC grid's abc switch shows the case it types: abc, ABC, or a bold ABC for caps lock.
            KeyKind.Shift -> if (key.label == "abc") PText(
                if (shift) "ABC" else "abc", 16.sp, if (capsLock) FontWeight.Bold else FontWeight.Medium, ink,
            ) else PIcon(if (capsLock) Icons.Rounded.KeyboardCapslock else Icons.Rounded.ArrowUpward, ink, 21.dp)
            KeyKind.Delete -> PIcon(Icons.AutoMirrored.Rounded.Backspace, ink, 21.dp)
            KeyKind.Clear -> PText("clear", if (bare) 13.sp else 15.sp, FontWeight.Medium, ink, shadow = c.clear && !focused)
            KeyKind.Layer -> PText(key.label, 16.sp, FontWeight.Medium, ink)
            KeyKind.Space -> PText("space", 15.sp, FontWeight.Medium, ink.copy(alpha = 0.75f), shadow = c.clear && !focused)
            KeyKind.Emoji -> PIcon(Icons.Rounded.EmojiEmotions, ink, 22.dp)
            KeyKind.Mic -> PIcon(Icons.Rounded.Mic, ink, 22.dp)
            KeyKind.EmojiCategory -> Unit
            // Too narrow for a word on the side grid: the return arrow, or a magnifier for Search.
            KeyKind.Done -> if (bare) PIcon(
                if (actionLabel == "search") Icons.Rounded.Search else Icons.AutoMirrored.Rounded.KeyboardReturn, ink, 20.dp,
            ) else Row(verticalAlignment = Alignment.CenterVertically) {
                if (!action) {
                    PIcon(Icons.AutoMirrored.Rounded.KeyboardReturn, ink, 20.dp)
                    HSpace(6.dp)
                }
                PText(actionLabel.replaceFirstChar { it.uppercase() }, 15.sp, FontWeight.SemiBold, ink)
            }
        }
    }
}

/** Prism TV's own text entry (Rename Folder, Profile Name): a field over the keyboard, sliding up. */
@Composable
fun KeyboardOverlay(
    vm: LauncherViewModel,
    look: Appearance,
    clear: Boolean = false,
    /** The text field's own colour (Settings › Keyboard › Text Field Colour); the keyboard's by default. */
    fieldLook: Appearance = look,
    fieldClear: Boolean = clear,
) {
    val live = vm.keyboard
    val holder = remember { arrayOfNulls<com.prismtv.launcher.KeyboardRequest>(1) }
    if (live != null) holder[0] = live
    val req = holder[0] ?: return
    val tState = animateFloatAsState(if (live != null) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 300f), label = "kb")
    val t by tState
    if (!tState.atLeast(0.002f)) return
    val c = keyColoursFor(look, clear)
    val m = KeyboardMetrics.Overlay
    val kb = vm.keyboardController
    if (KeyboardLayout.alphabetical) {
        SideKeyboard(vm, req, t, live != null, look, clear, fieldLook, fieldClear)
        return
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = t }
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 1f to Color.Black.copy(alpha = 0.55f)))
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { translationY = (1f - t) * (m.height + 120f).dp.toPx(); alpha = t }
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The text being edited, iOS-style: a rounded field with a blinking caret.
            if (req.title.isNotBlank()) {
                PText(req.title, 15.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.9f), shadow = true)
                Box(Modifier.height(8.dp))
            }
            val caret = rememberInfiniteTransition(label = "caret")
            val blink by caret.animateFloat(1f, 0f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "blink")
            // The text field is Liquid Glass in its own colour (or the keyboard's): frosty, lavender, smoked or clear,
            // always bending a softened copy of the screen, as iOS's search field does.
            val base = LocalPrism.current
            val fieldTheme = base.copy(palette = Palettes.of(fieldLook), material = GlassMaterial.Liquid, clearTint = fieldClear)
            val fieldInk = if (fieldLook == Appearance.Light && !fieldClear) Color(0xFF111318) else Color.White
            CompositionLocalProvider(LocalPrism provides fieldTheme) {
                // As wide as the keyboard below it.
                Row(
                    Modifier
                        .width(m.width.dp)
                        .height(50.dp)
                        .glass(
                            RoundedCornerShape(25.dp),
                            if (fieldClear) Color.White.copy(alpha = 0.08f)
                            else fieldTheme.palette.liquidTint.copy(alpha = if (fieldLook == Appearance.Light) 0.58f else 0.50f),
                            // Over the blurred screen, like the Clear keyboard below it: sharp icons and
                            // artwork behind a long field showed through as hard-edged slices (on TVs that
                            // draw Refraction Bands, offset in the rim band too).
                            GlassSource.Full, elevation = 18.dp, rimStrength = 1.2f, blurredLens = true,
                        )
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val text = vm.keyboardText
                    if (text.isEmpty()) {
                        PText(req.placeholder, 18.sp, FontWeight.Normal, fieldInk.copy(alpha = 0.45f), Modifier.weight(1f, fill = false), shadow = fieldInk == Color.White)
                    } else {
                        PText(text, 18.sp, FontWeight.Medium, fieldInk, Modifier.weight(1f, fill = false), maxLines = 1, shadow = fieldInk == Color.White)
                    }
                    Box(
                        Modifier
                            .padding(start = 2.dp)
                            .size(2.dp, 24.dp)
                            .graphicsLayer { alpha = if (blink > 0.5f) 1f else 0f }
                            .background(Color(0xFF0A84FF), RoundedCornerShape(1.dp))
                    )
                }
            }
            Box(Modifier.height(16.dp))
            // Clear is a clear Liquid Glass lens over the Home Screen; the other colours are frosted.
            val panelTheme = if (clear) base.copy(palette = Palettes.of(Appearance.Dark), material = GlassMaterial.Liquid, clearTint = true) else base
            CompositionLocalProvider(LocalPrism provides panelTheme) {
                Box(
                    Modifier
                        .size(m.width.dp, m.height.dp)
                        .then(
                            // Clear glass bends a softened copy of the screen (like an open folder), so the
                            // keys stay legible over busy icons; there's no tint, just a light dimming.
                            if (clear) Modifier.glass(RoundedCornerShape(30.dp), Color.Black.copy(alpha = 0.16f), GlassSource.Full, elevation = 26.dp, blurredLens = true, rimStrength = 1.3f)
                            else Modifier.glass(RoundedCornerShape(30.dp), c.panel, GlassSource.Full, elevation = 26.dp, frosted = true)
                        )
                        .padding(m.pad.dp)
                ) {
                    KeyboardKeys(kb, c, m, active = live != null)
                }
            }
        }
    }
}

/** Where the side keyboard's panel sits on the 960 × 540 canvas; Search puts its results to the right of it. */
object SideKeyboardLayout {
    const val Left = 28f
    const val Top = 28f
    val Width: Float get() = KeyboardMetrics.Side.width
    val Right: Float get() = Left + Width
}

/**
 * The ABC grid down the left of the screen, as on Apple TV: the text at the top, then suggestions and the
 * keys, on a Liquid Glass panel in the keyboard's colour. Search's results sit to the right.
 */
@Composable
private fun SideKeyboard(
    vm: LauncherViewModel,
    req: com.prismtv.launcher.KeyboardRequest,
    t: Float,
    active: Boolean,
    look: Appearance,
    clear: Boolean,
    fieldLook: Appearance,
    fieldClear: Boolean,
) {
    val c = keyColoursFor(look, clear)
    val m = KeyboardMetrics.Side
    val kb = vm.keyboardController
    val base = LocalPrism.current
    Box(Modifier.fillMaxSize()) {
        // Shade the left of the screen, where the panel is, more than the right, where results show.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = t }
                .background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.55f to Color.Black.copy(alpha = 0.25f), 1f to Color.Black.copy(alpha = 0.15f)))
        )
        val panelTheme = if (clear) base.copy(palette = Palettes.of(Appearance.Dark), material = GlassMaterial.Liquid, clearTint = true) else base
        CompositionLocalProvider(LocalPrism provides panelTheme) {
            Column(
                Modifier
                    .offset(SideKeyboardLayout.Left.dp, SideKeyboardLayout.Top.dp)
                    .graphicsLayer { translationX = (1f - t) * -(m.width + 60f).dp.toPx(); alpha = t }
                    .width(m.width.dp)
                    .then(
                        if (clear) Modifier.glass(RoundedCornerShape(28.dp), Color.Black.copy(alpha = 0.16f), GlassSource.Full, elevation = 26.dp, blurredLens = true, rimStrength = 1.3f)
                        else Modifier.glass(RoundedCornerShape(28.dp), c.panel, GlassSource.Full, elevation = 26.dp, frosted = true)
                    )
                    .padding(m.pad.dp),
            ) {
                if (req.title.isNotBlank()) {
                    PText(req.title, 13.sp, FontWeight.SemiBold, c.ink.copy(alpha = 0.75f), Modifier.padding(start = 4.dp, bottom = 6.dp), shadow = c.clear)
                }
                SideField(vm, req, fieldLook, fieldClear, m)
                Box(Modifier.height(12.dp))
                KeyboardKeys(kb, c, m, active = active, bare = true)
            }
        }
    }
}

/** The text being typed, at the top of the side keyboard: a magnifier for Search, a blinking caret. */
@Composable
private fun SideField(vm: LauncherViewModel, req: com.prismtv.launcher.KeyboardRequest, fieldLook: Appearance, fieldClear: Boolean, m: KeyboardMetrics) {
    val base = LocalPrism.current
    val fieldTheme = base.copy(palette = Palettes.of(fieldLook), material = GlassMaterial.Liquid, clearTint = fieldClear)
    val fieldInk = if (fieldLook == Appearance.Light && !fieldClear) Color(0xFF111318) else Color.White
    val caret = rememberInfiniteTransition(label = "caret")
    val blink by caret.animateFloat(1f, 0f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "blink")
    CompositionLocalProvider(LocalPrism provides fieldTheme) {
        Row(
            Modifier
                .width((m.width - m.pad * 2).dp)
                .height(44.dp)
                .glass(
                    RoundedCornerShape(22.dp),
                    if (fieldClear) Color.White.copy(alpha = 0.08f)
                    else fieldTheme.palette.liquidTint.copy(alpha = if (fieldLook == Appearance.Light) 0.58f else 0.50f),
                    GlassSource.Full, elevation = 10.dp, rimStrength = 1.1f, blurredLens = true,
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (vm.searchOpen) {
                PIcon(Icons.Rounded.Search, fieldInk.copy(alpha = 0.7f), 18.dp)
                HSpace(8.dp)
            }
            val text = vm.keyboardText
            if (text.isEmpty()) {
                PText(req.placeholder.ifBlank { "Type" }, 15.sp, FontWeight.Normal, fieldInk.copy(alpha = 0.45f), Modifier.weight(1f, fill = false), shadow = fieldInk == Color.White)
            } else {
                // The end of long text stays in view, next to the caret.
                PText(text.takeLast(26), 16.sp, FontWeight.Medium, fieldInk, Modifier.weight(1f, fill = false), maxLines = 1, shadow = fieldInk == Color.White)
            }
            Box(
                Modifier
                    .padding(start = 2.dp)
                    .size(2.dp, 20.dp)
                    .graphicsLayer { alpha = if (blink > 0.5f) 1f else 0f }
                    .background(Color(0xFF0A84FF), RoundedCornerShape(1.dp))
            )
        }
    }
}
