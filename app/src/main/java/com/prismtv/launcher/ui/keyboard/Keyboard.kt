package com.prismtv.launcher.ui.keyboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.KeyboardCapslock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
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

enum class KeyKind { Char, Shift, Delete, Layer, Space, Done }

data class Key(val label: String, val kind: KeyKind = KeyKind.Char, val units: Float = 1f, val target: Int = 0)

/** iOS-style layouts: letters, numbers and symbols. Widths are in key units (10 per row). */
object KeyboardLayout {
    private fun chars(s: String) = s.map { Key(it.toString()) }

    val Letters: List<List<Key>> = listOf(
        chars("qwertyuiop"),
        chars("asdfghjkl"),
        listOf(Key("shift", KeyKind.Shift, 1.5f)) + chars("zxcvbnm") + Key("delete", KeyKind.Delete, 1.5f),
        listOf(Key("123", KeyKind.Layer, 2.5f, target = 1), Key("space", KeyKind.Space, 5f), Key("done", KeyKind.Done, 2.5f)),
    )
    val Numbers: List<List<Key>> = listOf(
        chars("1234567890"),
        chars("-/:;()$&@\""),
        listOf(Key("#+=", KeyKind.Layer, 1.5f, target = 2)) + ".,?!'".map { Key(it.toString(), units = 1.4f) } + Key("delete", KeyKind.Delete, 1.5f),
        listOf(Key("ABC", KeyKind.Layer, 2.5f, target = 0), Key("space", KeyKind.Space, 5f), Key("done", KeyKind.Done, 2.5f)),
    )
    val Symbols: List<List<Key>> = listOf(
        chars("[]{}#%^*+="),
        chars("_\\|~<>€£¥•"),
        listOf(Key("123", KeyKind.Layer, 1.5f, target = 1)) + ".,?!'".map { Key(it.toString(), units = 1.4f) } + Key("delete", KeyKind.Delete, 1.5f),
        listOf(Key("ABC", KeyKind.Layer, 2.5f, target = 0), Key("space", KeyKind.Space, 5f), Key("done", KeyKind.Done, 2.5f)),
    )

    fun of(layer: Int): List<List<Key>> = when (layer) {
        1 -> Numbers
        2 -> Symbols
        else -> Letters
    }

    /** Row starts in key units: the second letter row is indented half a key, like iOS. */
    fun indent(layer: Int, row: Int): Float = if (layer == 0 && row == 1) 0.5f else 0f

    /** Horizontal centre (in units) of [col] in [row]. */
    fun centre(layer: Int, row: Int, col: Int): Float {
        val keys = of(layer)[row]
        var x = indent(layer, row)
        for (i in 0 until col) x += keys[i].units
        return x + keys[col].units / 2f
    }

    /** The key in [row] nearest to [x] units. */
    fun nearest(layer: Int, row: Int, x: Float): Int {
        val keys = of(layer)[row]
        return keys.indices.minByOrNull { kotlin.math.abs(centre(layer, row, it) - x) } ?: 0
    }
}

/** Key and panel colours for each keyboard look (Balanced is Prism's own indigo variant). */
private data class KeyColours(
    val panel: Color,
    val key: Color,
    val function: Color,
    val ink: Color,
    val focus: Color,
    val focusInk: Color,
    val field: Color,
    val fieldInk: Color,
)

private fun coloursFor(look: Appearance): KeyColours = when (look) {
    Appearance.Light -> KeyColours(
        panel = Color(0xFFD3D6DE).copy(alpha = 0.78f), key = Color.White, function = Color(0xFFF3F4F7),
        ink = Color(0xFF0B0C0F), focus = Color(0xFF1C1D22), focusInk = Color.White,
        field = Color.White.copy(alpha = 0.86f), fieldInk = Color(0xFF0B0C0F),
    )
    Appearance.Balanced -> KeyColours(
        panel = Color(0xFF3E4370).copy(alpha = 0.80f), key = Color(0xFF6C71A3), function = Color(0xFF585D8E),
        ink = Color.White, focus = Color(0xFFE6E7FF), focusInk = Color(0xFF181B30),
        field = Color(0xFF555A88).copy(alpha = 0.90f), fieldInk = Color.White,
    )
    Appearance.Dark -> KeyColours(
        panel = Color(0xFF1E1E21).copy(alpha = 0.80f), key = Color(0xFF4A4A4F), function = Color(0xFF3A3A3E),
        ink = Color.White, focus = Color(0xFFE4E5E9), focusInk = Color(0xFF111318),
        field = Color(0xFF3A3A3E).copy(alpha = 0.92f), fieldInk = Color.White,
    )
}

private const val PanelW = 900f
private const val KeyH = 46f
private const val KeyGap = 9f
private const val PanelPad = 14f

/** Prism TV's on-screen keyboard: a text field over an iOS-style keyboard sliding up from the bottom. */
@Composable
fun KeyboardOverlay(vm: LauncherViewModel, look: Appearance) {
    val live = vm.keyboard
    val holder = remember { arrayOfNulls<com.prismtv.launcher.KeyboardRequest>(1) }
    if (live != null) holder[0] = live
    val req = holder[0] ?: return
    val t by animateFloatAsState(if (live != null) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 300f), label = "kb")
    if (t < 0.002f) return
    val c = coloursFor(look)
    val layer = vm.keyLayer
    val rows = KeyboardLayout.of(layer)
    val unit = (PanelW - PanelPad * 2 - KeyGap * 9) / 10f
    val panelH = PanelPad * 2 + KeyH * 4 + KeyGap * 3

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
                .graphicsLayer { translationY = (1f - t) * (panelH + 120f).dp.toPx(); alpha = t }
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The text being edited, iOS-style: a rounded field with a blinking caret.
            PText(req.title, 15.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.9f), shadow = true)
            Box(Modifier.height(8.dp))
            val caret = rememberInfiniteTransition(label = "caret")
            val blink by caret.animateFloat(1f, 0f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "blink")
            Row(
                Modifier
                    .width(560.dp)
                    .height(48.dp)
                    .shadow(18.dp, RoundedCornerShape(16.dp))
                    .background(c.field, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val text = vm.keyboardText
                if (text.isEmpty()) {
                    PText(req.placeholder, 18.sp, FontWeight.Normal, c.fieldInk.copy(alpha = 0.4f))
                } else {
                    PText(text, 18.sp, FontWeight.Medium, c.fieldInk)
                }
                Box(
                    Modifier
                        .padding(start = 2.dp)
                        .size(2.dp, 24.dp)
                        .graphicsLayer { alpha = if (blink > 0.5f) 1f else 0f }
                        .background(Color(0xFF0A84FF), RoundedCornerShape(1.dp))
                )
            }
            Box(Modifier.height(16.dp))
            Box(
                Modifier
                    .size(PanelW.dp, panelH.dp)
                    .glass(RoundedCornerShape(30.dp), c.panel, GlassSource.Full, elevation = 26.dp, frosted = true)
            ) {
                rows.forEachIndexed { r, keys ->
                    var x = PanelPad + KeyboardLayout.indent(layer, r) * (unit + KeyGap)
                    val y = PanelPad + r * (KeyH + KeyGap)
                    keys.forEachIndexed { i, key ->
                        val w = unit * key.units + KeyGap * (key.units - 1f)
                        KeyCap(
                            key = key,
                            focused = live != null && r == vm.keyRow && i == vm.keyCol,
                            shift = vm.keyShift,
                            c = c,
                            modifier = Modifier
                                .offset(x.dp, y.dp)
                                .size(w.dp, KeyH.dp),
                        )
                        x += w + KeyGap
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyCap(key: Key, focused: Boolean, shift: Boolean, c: KeyColours, modifier: Modifier) {
    val s by animateFloatAsState(if (focused) 1.14f else 1f, spring(dampingRatio = 0.8f, stiffness = 520f), label = "key")
    val shape = RoundedCornerShape(10.dp)
    val isFunction = key.kind != KeyKind.Char && key.kind != KeyKind.Space
    val bg = when {
        focused -> c.focus
        key.kind == KeyKind.Done -> Color(0xFF0A84FF)
        isFunction -> c.function
        else -> c.key
    }
    val ink = when {
        focused -> c.focusInk
        key.kind == KeyKind.Done -> Color.White
        else -> c.ink
    }
    Box(
        modifier
            .graphicsLayer {
                scaleX = s
                scaleY = s
                shadowElevation = lerpF(1f, 14f, (s - 1f) / 0.14f).dp.toPx()
                this.shape = shape
                clip = true
            }
            .background(bg, shape)
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), shape),
        contentAlignment = Alignment.Center,
    ) {
        when (key.kind) {
            KeyKind.Char -> PText(if (shift) key.label.uppercase() else key.label, 22.sp, FontWeight.Normal, ink)
            KeyKind.Shift -> PIcon(if (shift) Icons.Rounded.KeyboardCapslock else Icons.Rounded.ArrowUpward, ink, 22.dp)
            KeyKind.Delete -> PIcon(Icons.AutoMirrored.Rounded.Backspace, ink, 22.dp)
            KeyKind.Layer -> PText(key.label, 17.sp, FontWeight.Medium, ink)
            KeyKind.Space -> PText("space", 16.sp, FontWeight.Medium, ink.copy(alpha = 0.75f))
            KeyKind.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
                PIcon(Icons.AutoMirrored.Rounded.KeyboardReturn, ink, 20.dp)
                HSpace(6.dp)
                PText("Done", 16.sp, FontWeight.SemiBold, ink)
            }
        }
    }
}
