package com.prismtv.launcher.ui.components

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector

/** Glyphs Material doesn't have in a TV version. */
object PrismIcons {
    /** Software Update: a TV with a download arrow (Material's System Update is a phone). */
    val TvUpdate: ImageVector by lazy {
        materialIcon(name = "Prism.TvUpdate") {
            // The screen's frame: outer and inner rounded rectangles, the inside cut out.
            materialPath(pathFillType = PathFillType.EvenOdd) {
                moveTo(3f, 3f)
                horizontalLineTo(21f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineTo(17f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(3f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(5f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
                moveTo(4f, 5f)
                horizontalLineTo(20f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
                verticalLineTo(16f)
                arcToRelative(1f, 1f, 0f, false, true, -1f, 1f)
                horizontalLineTo(4f)
                arcToRelative(1f, 1f, 0f, false, true, -1f, -1f)
                verticalLineTo(6f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
                close()
            }
            // The stand.
            materialPath {
                moveTo(8f, 19f)
                horizontalLineTo(16f)
                verticalLineTo(20f)
                arcToRelative(1f, 1f, 0f, false, true, -1f, 1f)
                horizontalLineTo(9f)
                arcToRelative(1f, 1f, 0f, false, true, -1f, -1f)
                close()
            }
            // The arrow.
            materialPath {
                moveTo(11f, 6.5f)
                horizontalLineTo(13f)
                verticalLineTo(10.5f)
                horizontalLineTo(16f)
                lineTo(12f, 14.8f)
                lineTo(8f, 10.5f)
                horizontalLineTo(11f)
                close()
            }
        }
    }
}
