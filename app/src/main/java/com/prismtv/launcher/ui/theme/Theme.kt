package com.prismtv.launcher.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.prismtv.launcher.R
import com.prismtv.launcher.data.FocusEffect
import com.prismtv.launcher.data.GlassMaterial
import com.prismtv.launcher.data.LabelMode

enum class Appearance { Light, Balanced, Dark }

/**
 * Colour tokens, taken from the Light / Balanced / Dark design sheets
 * (Settings UI component libraries + Liquid Glass and Classic control libraries).
 */
@Immutable
data class PrismPalette(
    val appearance: Appearance,
    val isDark: Boolean,
    // Surfaces
    val bgBase: Color,
    val bgAccent: Color,
    val panelGlass: Color,
    val panelOpacity: Float,
    val backgroundDim: Float,
    // Settings buttons
    val buttonUnfocused: Color,
    val buttonFocused: Color,
    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textFocused: Color,
    // Liquid Glass controls
    val liquidIdle: Color,
    val liquidFocus: Color,
    val liquidTint: Color,
    val liquidEdge: Color,
    // Classic (frosted) controls
    val classicBase: Color,
    val classicFocused: Color,
    val classicSecondary: Color,
    val classicDisabled: Color,
    // Semantic
    val accent: Color,
    val toggleOn: Color,
)

object Palettes {
    val Light = PrismPalette(
        appearance = Appearance.Light, isDark = false,
        bgBase = Color(0xFFD6D9E8), bgAccent = Color(0xFFB8C5E3),
        panelGlass = Color(0xFFE4E7EF), panelOpacity = 0.85f, backgroundDim = 0.30f,
        buttonUnfocused = Color(0xFFB9C0D1), buttonFocused = Color(0xFFF5F6FA),
        textPrimary = Color(0xFF161923), textSecondary = Color(0xFF626879), textFocused = Color(0xFF11131A),
        liquidIdle = Color(0xFFB9C0D1), liquidFocus = Color(0xFFFFFFFF), liquidTint = Color(0xFFF4F6FF), liquidEdge = Color(0xFFFFFFFF),
        classicBase = Color(0xFFE8EAF3), classicFocused = Color(0xFFFFFFFF), classicSecondary = Color(0xFFF2F4F9), classicDisabled = Color(0xFFD5D8E1),
        accent = Color(0xFF007AFF), toggleOn = Color(0xFF007AFF),
    )

    val Balanced = PrismPalette(
        appearance = Appearance.Balanced, isDark = true,
        bgBase = Color(0xFF454B76), bgAccent = Color(0xFF71628E),
        panelGlass = Color(0xFF777BA5), panelOpacity = 0.75f, backgroundDim = 0.35f,
        buttonUnfocused = Color(0xFF666D94), buttonFocused = Color(0xFFDADCF0),
        textPrimary = Color(0xFFF4F4FA), textSecondary = Color(0xFFC4C7DE), textFocused = Color(0xFF181B30),
        liquidIdle = Color(0xFF666D94), liquidFocus = Color(0xFFE6E7FF), liquidTint = Color(0xFF8F8FB5), liquidEdge = Color(0xFFD8D9F5),
        classicBase = Color(0xFF757BA7), classicFocused = Color(0xFFF2F2F7), classicSecondary = Color(0xFF8F8FB5), classicDisabled = Color(0xFF666D94),
        accent = Color(0xFF007AFF), toggleOn = Color(0xFF7B6CF6),
    )

    val Dark = PrismPalette(
        appearance = Appearance.Dark, isDark = true,
        bgBase = Color(0xFF101319), bgAccent = Color(0xFF292E39),
        panelGlass = Color(0xFF30343D), panelOpacity = 0.85f, backgroundDim = 0.45f,
        buttonUnfocused = Color(0xFF454951), buttonFocused = Color(0xFFE4E5E9),
        textPrimary = Color(0xFFF5F5F7), textSecondary = Color(0xFFA1A5AF), textFocused = Color(0xFF111318),
        liquidIdle = Color(0xFF454951), liquidFocus = Color(0xFFE4E5E9), liquidTint = Color(0xFF30343D), liquidEdge = Color(0xFFA5AEC8),
        classicBase = Color(0xFF3A3C45), classicFocused = Color(0xFFE4E5E9), classicSecondary = Color(0xFF4A4D57), classicDisabled = Color(0xFF30343D),
        accent = Color(0xFF0A84FF), toggleOn = Color(0xFF0A84FF),
    )

    fun of(a: Appearance): PrismPalette = when (a) {
        Appearance.Light -> Light
        Appearance.Balanced -> Balanced
        Appearance.Dark -> Dark
    }
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

@Immutable
data class PrismTheme(
    val palette: PrismPalette,
    val material: GlassMaterial,
    val reduceTransparency: Boolean,
    val focusEffect: FocusEffect,
    val labels: LabelMode,
) {
    val liquid: Boolean get() = material == GlassMaterial.Liquid

    /** Large floating panels: Control Centre, dialogs, banners. */
    fun panelFill(): Color = if (liquid) {
        palette.panelGlass.copy(alpha = if (palette.isDark) 0.46f else 0.50f)
    } else {
        palette.panelGlass.copy(alpha = palette.panelOpacity * 0.86f)
    }

    /** Dialogs and banners: denser so text stays legible over bright content. */
    fun sheetFill(): Color = palette.panelGlass.copy(
        alpha = if (liquid) (if (palette.isDark) 0.74f else 0.80f) else palette.panelOpacity
    )

    /** The dock tray on the Home Screen. */
    fun trayFill(): Color = if (liquid) {
        if (palette.isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.26f)
    } else {
        palette.panelGlass.copy(alpha = 0.55f)
    }

    /** Small floating pills (time pill, hero buttons). */
    fun pillFill(): Color = if (liquid) {
        if (palette.isDark) Color.Black.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.34f)
    } else {
        palette.panelGlass.copy(alpha = 0.72f)
    }

    fun controlIdle(): Color = if (liquid) palette.liquidIdle.copy(alpha = 0.42f) else palette.classicBase.copy(alpha = 0.78f)

    fun controlFocused(): Color = if (liquid) palette.liquidFocus else palette.classicFocused

    fun rowFill(): Color = if (liquid) palette.liquidIdle.copy(alpha = 0.40f) else palette.buttonUnfocused.copy(alpha = 0.58f)

    fun edgeBrush(strength: Float = 1f): Brush = if (liquid) {
        Brush.linearGradient(
            0f to palette.liquidEdge.copy(alpha = 0.70f * strength),
            0.45f to palette.liquidEdge.copy(alpha = 0.06f * strength),
            1f to palette.liquidEdge.copy(alpha = 0.32f * strength),
        )
    } else {
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.10f * strength), Color.White.copy(alpha = 0.05f * strength))
        )
    }
}

val LocalPrism = staticCompositionLocalOf {
    PrismTheme(Palettes.Dark, GlassMaterial.Liquid, false, FocusEffect.GrowShrink, LabelMode.Focused)
}
