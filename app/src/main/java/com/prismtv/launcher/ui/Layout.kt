package com.prismtv.launcher.ui

import androidx.compose.ui.geometry.Rect
import kotlin.math.max

/**
 * All Home Screen geometry lives here, in dp on a 960×540 canvas (1080p TV at xhdpi).
 * Measured from tvOS 27 reference captures: 5:3 tiles, 24dp gutters, a 20dp-padded glass tray.
 */
object HomeLayout {
    const val ScreenW = 960f
    const val ScreenH = 540f
    const val GridW = 864f
    const val Gap = 24f
    const val TrayPad = 20f
    const val TrayInset = 20f
    const val TrayTopHero = 392f
    const val TrayTopHidden = 600f
    const val TrayTopGrid = 30f
    const val RowGapHero = 22f
    const val RowGapGrid = 34f
    const val LabelSpace = 55f
    const val FocusScale = 1.2f

    fun left(): Float = (ScreenW - GridW) / 2f
    fun iconW(cols: Int): Float = (GridW - Gap * (cols - 1)) / cols
    fun iconH(cols: Int): Float = iconW(cols) * 0.6f
    fun trayH(cols: Int): Float = iconH(cols) + TrayPad * 2
    fun pitch(cols: Int): Float = iconH(cols) + LabelSpace
    fun x(cols: Int, c: Int): Float = left() + (iconW(cols) + Gap) * c

    fun y(cols: Int, r: Int, trayTop: Float, rowGap: Float): Float =
        if (r == 0) trayTop + TrayPad else trayTop + trayH(cols) + rowGap + pitch(cols) * (r - 1)

    /** Tray top when browsing the grid with [focusRow] focused (scrolls for deep rows). */
    fun gridTrayTop(cols: Int, focusRow: Int): Float {
        val bottom = y(cols, focusRow, TrayTopGrid, RowGapGrid) + iconH(cols) + 44f
        return TrayTopGrid - max(0f, bottom - (ScreenH - 8f))
    }

    fun tileRect(cols: Int, index: Int, trayTop: Float, rowGap: Float): Rect {
        val r = index / cols
        val c = index % cols
        val x = x(cols, c)
        val y = y(cols, r, trayTop, rowGap)
        return Rect(x, y, x + iconW(cols), y + iconH(cols))
    }
}

/** Folder panel geometry (centred glass panel, 3 columns). */
object FolderLayout {
    const val Cols = 3
    const val PanelW = 604f
    const val PanelTop = 76f
    const val TileW = 124f
    const val TileH = 74.4f
    const val ColPitch = 200f
    const val RowPitch = 122f
    const val PadTop = 26f

    fun panelLeft(): Float = (HomeLayout.ScreenW - PanelW) / 2f
    fun tileRect(index: Int): Rect {
        val r = index / Cols
        val c = index % Cols
        val x = panelLeft() + (PanelW - (ColPitch * (Cols - 1) + TileW)) / 2f + ColPitch * c
        val y = PanelTop + PadTop + RowPitch * r
        return Rect(x, y, x + TileW, y + TileH)
    }
}

/** App switcher geometry: a cascading deck to the left, the focused card, upcoming cards to the right. */
object SwitcherLayout {
    const val CardW = 700f
    const val CardH = 393.75f
    const val CardTop = 78f
    const val FocusLeft = 166f
    const val RightGap = 14f

    fun cardLeft(rel: Int): Float = when {
        rel == 0 -> FocusLeft
        rel > 0 -> FocusLeft + (CardW + RightGap) * rel
        else -> {
            // Deck: each older card peeks out a little less.
            val steps = floatArrayOf(0f, 96f, 136f, 152f, 160f)
            FocusLeft - steps[(-rel).coerceAtMost(steps.size - 1)]
        }
    }

    fun cardScale(rel: Int): Float = when {
        rel >= 0 -> 1f
        else -> 1f - 0.03f * (-rel).coerceAtMost(4)
    }

    fun focusedRect(): Rect = Rect(FocusLeft, CardTop, FocusLeft + CardW, CardTop + CardH)
}
