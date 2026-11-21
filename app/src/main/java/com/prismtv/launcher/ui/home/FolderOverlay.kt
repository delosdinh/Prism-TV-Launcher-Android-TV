package com.prismtv.launcher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.LauncherViewModel
import com.prismtv.launcher.Overlay
import com.prismtv.launcher.data.AppItem
import com.prismtv.launcher.data.FolderItem
import com.prismtv.launcher.ui.FolderLayout
import com.prismtv.launcher.ui.components.PText
import com.prismtv.launcher.ui.components.lerpF
import com.prismtv.launcher.ui.glass.GlassSource
import com.prismtv.launcher.ui.glass.glass
import com.prismtv.launcher.ui.theme.LocalPrism

/**
 * An open folder, as on iOS: the Home Screen melts into a dark blur, the folder's name sits large above
 * a frosted glass panel, and the apps sit in a 4-wide grid that scrolls inside the panel.
 */
@Composable
fun FolderOverlay(vm: LauncherViewModel) {
    val holder = remember { arrayOfNulls<FolderItem>(1) }
    vm.openFolder?.let { holder[0] = it }
    val folder = holder[0] ?: return
    val visible = vm.overlay == Overlay.Folder && vm.openFolder != null
    val t by animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 300f), label = "folder")
    if (t < 0.002f) return
    val theme = LocalPrism.current
    val p = theme.palette
    val n = folder.apps.size
    val panelH = FolderLayout.panelH(n)
    val panelLeft = FolderLayout.panelLeft()
    val rows = FolderLayout.rows(n)
    val first = FolderLayout.firstRow(vm.folderFocus, n)
    val scroll by animateFloatAsState(first.toFloat(), spring(dampingRatio = 0.86f, stiffness = 320f), label = "folderScroll")
    val panelShape = RoundedCornerShape(44.dp)
    val panelTint = when {
        theme.clear -> Color.White.copy(alpha = if (p.isDark) 0.06f else 0.18f)
        theme.liquid -> Color.White.copy(alpha = if (p.isDark) 0.10f else 0.32f)
        else -> p.panelGlass.copy(alpha = 0.55f)
    }

    Box(Modifier.fillMaxSize()) {
        // The Home Screen, blurred and dimmed so the folder reads clearly.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = t }
                .glass(
                    RectangleShape,
                    Color.Black.copy(alpha = if (p.isDark) 0.42f else 0.22f),
                    GlassSource.Scene, rimStrength = 0f, lens = false, frosted = true,
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = t
                    val s = lerpF(0.9f, 1f, t)
                    scaleX = s
                    scaleY = s
                }
        ) {
            PText(
                folder.label, 34.sp, FontWeight.Bold, Color.White,
                Modifier.offset((panelLeft + 10f).dp, FolderLayout.TitleTop.dp),
                letterSpacing = (-0.5).sp, shadow = true,
            )
            Box(
                Modifier
                    .offset(panelLeft.dp, FolderLayout.PanelTop.dp)
                    .size(FolderLayout.PanelW.dp, panelH.dp)
                    .glass(panelShape, panelTint, GlassSource.Scene, elevation = 22.dp, frosted = true, bentRim = theme.liquid)
            )
            // Tiles, clipped to the panel so scrolled rows never spill outside it.
            Box(
                Modifier
                    .offset(panelLeft.dp, FolderLayout.PanelTop.dp)
                    .size(FolderLayout.PanelW.dp, panelH.dp)
                    .clip(panelShape)
            ) {
                folder.apps.forEachIndexed { i, app ->
                    val r = FolderLayout.tileRect(i, scroll)
                    val top = r.top - FolderLayout.PanelTop
                    if (top > panelH + 40f || top + r.height < -40f) return@forEachIndexed
                    key(app.id) {
                        GridTile(
                            item = AppItem(app),
                            focused = i == vm.folderFocus,
                            unfocusedScale = 1f,
                            showLabel = i == vm.folderFocus,
                            labelColor = Color.White,
                            labelOffset = 0f,
                            tileH = r.height,
                            modifier = Modifier
                                .offset((r.left - panelLeft).dp, top.dp)
                                .size(r.width.dp, r.height.dp),
                        )
                    }
                }
            }
            // Row indicator when the folder has more than fits.
            if (rows > FolderLayout.VisibleRows) {
                val pages = rows - FolderLayout.VisibleRows + 1
                Row(
                    Modifier
                        .offset(0.dp, (FolderLayout.PanelTop + panelH + 14f).dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(pages) { k ->
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(7.dp)
                                .background(Color.White.copy(alpha = if (k == first) 0.95f else 0.35f), CircleShape)
                        )
                    }
                }
            }
        }
    }
}
