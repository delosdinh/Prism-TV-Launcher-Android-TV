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

    /** [below]: height of the widget rows between the dock and the first grid row. */
    fun y(cols: Int, r: Int, trayTop: Float, rowGap: Float, below: Float = 0f): Float =
        if (r == 0) trayTop + TrayPad else trayTop + trayH(cols) + rowGap + below + pitch(cols) * (r - 1)

    /** Tray top when browsing the grid with [focusRow] focused (scrolls for deep rows). */
    fun gridTrayTop(cols: Int, focusRow: Int, below: Float = 0f): Float {
        val bottom = y(cols, focusRow, TrayTopGrid, RowGapGrid, below) + iconH(cols) + 44f
        return TrayTopGrid - max(0f, bottom - (ScreenH - 8f))
    }

    /** Tray top with widget row [index] (below the dock) focused. */
    fun widgetTrayTop(cols: Int, index: Int): Float {
        val bottom = TrayTopGrid + trayH(cols) + RowGapGrid + WidgetLayout.RowH * (index + 1) + 10f
        return TrayTopGrid - max(0f, bottom - (ScreenH - 8f))
    }

    fun tileRect(cols: Int, index: Int, trayTop: Float, rowGap: Float, below: Float = 0f): Rect {
        val r = index / cols
        val c = index % cols
        val x = x(cols, c)
        val y = y(cols, r, trayTop, rowGap, below)
        return Rect(x, y, x + iconW(cols), y + iconH(cols))
    }
}

/** Widget rows: a title over a row of 16:9 cards. */
object WidgetLayout {
    const val CardW = 168f
    const val CardH = 94.5f
    const val CardGap = 20f
    const val TitleH = 24f
    const val RowH = TitleH + CardH + 30f

    /** Top of above-dock row [i] of [count], stacked up from the dock. */
    fun aboveTop(trayTop: Float, i: Int, count: Int): Float = trayTop - 8f - RowH * (count - i)

    /** Top of below-dock row [j]. */
    fun belowTop(trayTop: Float, cols: Int, rowGap: Float, j: Int): Float =
        trayTop + HomeLayout.trayH(cols) + rowGap + RowH * j

    /** First card shown so the focused one stays on screen. */
    fun firstVisible(focus: Int, count: Int): Int {
        val fit = ((HomeLayout.GridW + CardGap) / (CardW + CardGap)).toInt()
        return (focus - fit + 2).coerceIn(0, (count - fit).coerceAtLeast(0))
    }
}

/** Spotlight Top Shelf: the hero as a large inset card over the wallpaper. */
object SpotlightLayout {
    const val Left = 48f
    const val Top = 34f
    const val W = HomeLayout.ScreenW - Left * 2
    const val H = HomeLayout.TrayTopHero - Top - 26f
}

/** Folder panel geometry (centred glass panel, 3 columns). */
object FolderLayout {
    const val Cols = 4
    const val VisibleRows = 3
    const val TileW = 124f
    const val TileH = 74.4f
    const val ColPitch = 168f
    const val RowPitch = 112f
    const val PadX = 48f
    const val PadY = 40f
    const val PanelW = ColPitch * (Cols - 1) + TileW + PadX * 2
    const val PanelTop = 118f
    const val TitleTop = 50f

    fun panelLeft(): Float = (HomeLayout.ScreenW - PanelW) / 2f
    fun rows(count: Int): Int = maxOf(1, (count + Cols - 1) / Cols)
    fun visibleRows(count: Int): Int = rows(count).coerceAtMost(VisibleRows)
    fun panelH(count: Int): Float = PadY * 2 + RowPitch * (visibleRows(count) - 1) + TileH

    /** First visible row: focus stays on the middle row while the folder scrolls. */
    fun firstRow(focus: Int, count: Int): Int {
        val rows = rows(count)
        if (rows <= VisibleRows) return 0
        return (focus / Cols - 1).coerceIn(0, rows - VisibleRows)
    }

    /** A tile's rect on screen, for [scroll] rows scrolled away. */
    fun tileRect(index: Int, scroll: Float = 0f): Rect {
        val r = index / Cols
        val c = index % Cols
        val x = panelLeft() + PadX + ColPitch * c
        val y = PanelTop + PadY + RowPitch * (r - scroll)
        return Rect(x, y, x + TileW, y + TileH)
    }
}

/** App switcher geometry: a cascading deck to the left, the focused card, upcoming cards to the right. */
object SwitcherLayout {
    // Measured from tvOS 26: a 75% card, centred vertically.
    const val CardW = 720f
    const val CardH = 405f
    const val CardTop = (HomeLayout.ScreenH - CardH) / 2f
    const val Radius = 14f
    /** Focused card's left edge with a newer card beside it (17%), and when it's the last card (centred). */
    const val FocusLeft = 164f
    const val FocusLeftLast = (HomeLayout.ScreenW - CardW) / 2f
    /** The newer card lies on top of the focused one, overlapping its edge. */
    const val RightOverlap = 18f
    /** Each older card in the deck peeks out this fraction as far as the one in front of it. */
    private const val DeckFalloff = 0.43f

    fun focusLeft(focusIsLast: Boolean): Float = if (focusIsLast) FocusLeftLast else FocusLeft

    fun cardLeft(rel: Int, focusIsLast: Boolean = false): Float {
        val base = focusLeft(focusIsLast)
        return when {
            rel == 0 -> base
            rel > 0 -> base + (CardW - RightOverlap) * rel
            else -> {
                var x = base
                repeat((-rel).coerceAtMost(5)) { x *= DeckFalloff }
                x
            }
        }
    }

    /** Older cards recede a little with each step back into the deck. */
    fun cardScale(rel: Int): Float = when {
        rel >= 0 -> 1f
        else -> 1f - 0.048f * (-rel).coerceAtMost(5)
    }

    fun focusedRect(focusIsLast: Boolean = false): Rect {
        val l = focusLeft(focusIsLast)
        return Rect(l, CardTop, l + CardW, CardTop + CardH)
    }
}
