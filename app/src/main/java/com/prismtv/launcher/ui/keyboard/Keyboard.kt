package com.prismtv.launcher.ui.keyboard

import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
)

fun keyColoursFor(look: Appearance): KeyColours = when (look) {
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
data class KeyboardMetrics(val width: Float, val keyH: Float, val gap: Float, val pad: Float, val barH: Float) {
    val unit: Float get() = (width - pad * 2 - gap * 9) / 10f
    val keysH: Float get() = keyH * 4 + gap * 3
    val height: Float get() = pad * 2 + barH + gap + keysH

    companion object {
        /** Prism's own text entry (over the Home Screen). */
        val Overlay = KeyboardMetrics(width = 900f, keyH = 44f, gap = 8f, pad = 14f, barH = 34f)
        /** The system keyboard in other apps: a little shorter so more of the app stays visible. */
        val System = KeyboardMetrics(width = 880f, keyH = 40f, gap = 7f, pad = 10f, barH = 32f)
    }
}

/** The keys, the suggestion bar and the emoji grid, laid out on [m]. No background. */
@Composable
fun KeyboardKeys(kb: KeyboardController, c: KeyColours, m: KeyboardMetrics, active: Boolean = true) {
    Box(Modifier.size((m.width - m.pad * 2).dp, (m.barH + m.gap + m.keysH).dp)) {
        SuggestionBar(kb, c, m, active)
        Box(
            Modifier
                .offset(0.dp, (m.barH + m.gap).dp)
                .graphicsLayer { alpha = if (kb.listening) 0.45f else 1f }
        ) {
            if (kb.layer == KeyboardLayout.Emoji) EmojiKeys(kb, c, m, active) else LetterKeys(kb, c, m, active)
        }
    }
}

@Composable
private fun LetterKeys(kb: KeyboardController, c: KeyColours, m: KeyboardMetrics, active: Boolean) {
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
                modifier = Modifier
                    .offset(x.dp, y.dp)
                    .size(w.dp, m.keyH.dp),
            )
            x += w + m.gap
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
            .graphicsLayer {
                scaleX = s
                scaleY = s
                shadowElevation = lerpF(1f, 14f, ((s - 1f) / 0.14f).coerceIn(0f, 1f)).dp.toPx()
                this.shape = shape
                clip = true
            }
            .background(bg, shape)
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), shape),
        contentAlignment = Alignment.Center,
    ) {
        when (key.kind) {
            KeyKind.Char -> PText(if (shift) key.label.uppercase() else key.label, 21.sp, FontWeight.Normal, ink)
            KeyKind.Shift -> PIcon(if (capsLock) Icons.Rounded.KeyboardCapslock else Icons.Rounded.ArrowUpward, ink, 21.dp)
            KeyKind.Delete -> PIcon(Icons.AutoMirrored.Rounded.Backspace, ink, 21.dp)
            KeyKind.Layer -> PText(key.label, 16.sp, FontWeight.Medium, ink)
            KeyKind.Space -> PText("space", 15.sp, FontWeight.Medium, ink.copy(alpha = 0.75f))
            KeyKind.Emoji -> PIcon(Icons.Rounded.EmojiEmotions, ink, 22.dp)
            KeyKind.Mic -> PIcon(Icons.Rounded.Mic, ink, 22.dp)
            KeyKind.EmojiCategory -> Unit
            KeyKind.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
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
fun KeyboardOverlay(vm: LauncherViewModel, look: Appearance) {
    val live = vm.keyboard
    val holder = remember { arrayOfNulls<com.prismtv.launcher.KeyboardRequest>(1) }
    if (live != null) holder[0] = live
    val req = holder[0] ?: return
    val t by animateFloatAsState(if (live != null) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 300f), label = "kb")
    if (t < 0.002f) return
    val c = keyColoursFor(look)
    val m = KeyboardMetrics.Overlay
    val kb = vm.keyboardController

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
            PText(req.title, 15.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.9f), shadow = true)
            Box(Modifier.height(8.dp))
            val caret = rememberInfiniteTransition(label = "caret")
            val blink by caret.animateFloat(1f, 0f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "blink")
            // The text field is Liquid Glass in the keyboard's colour: frosty, lavender or smoked.
            val base = LocalPrism.current
            val fieldTheme = base.copy(palette = Palettes.of(look), material = GlassMaterial.Liquid, clearTint = false)
            val fieldInk = if (look == Appearance.Light) Color(0xFF111318) else Color.White
            CompositionLocalProvider(LocalPrism provides fieldTheme) {
                Row(
                    Modifier
                        .width(560.dp)
                        .height(50.dp)
                        .glass(
                            RoundedCornerShape(25.dp),
                            fieldTheme.palette.liquidTint.copy(alpha = if (look == Appearance.Light) 0.58f else 0.50f),
                            GlassSource.Full, elevation = 18.dp, rimStrength = 1.2f,
                        )
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val text = vm.keyboardText
                    if (text.isEmpty()) {
                        PText(req.placeholder, 18.sp, FontWeight.Normal, fieldInk.copy(alpha = 0.45f), shadow = fieldInk == Color.White)
                    } else {
                        PText(text, 18.sp, FontWeight.Medium, fieldInk, maxLines = 1, shadow = fieldInk == Color.White)
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
            Box(
                Modifier
                    .size(m.width.dp, m.height.dp)
                    .glass(RoundedCornerShape(30.dp), c.panel, GlassSource.Full, elevation = 26.dp, frosted = true)
                    .padding(m.pad.dp)
            ) {
                KeyboardKeys(kb, c, m, active = live != null)
            }
        }
    }
}
