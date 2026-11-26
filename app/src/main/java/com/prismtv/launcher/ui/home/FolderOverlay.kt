package com.prismtv.launcher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import com.prismtv.launcher.ui.components.atLeast
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
import com.prismtv.launcher.ui.HomeLayout
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
    val tState = animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 300f), label = "folder")
    val t by tState
    if (!tState.atLeast(0.002f)) return
    com.prismtv.launcher.ui.glass.HoldSceneCache()
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
        theme.clear -> p.liquidTint.copy(alpha = if (p.isDark) 0.10f else 0.18f)
        theme.liquid -> p.liquidTint.copy(alpha = if (p.isDark) 0.34f else 0.36f)
        else -> p.panelGlass.copy(alpha = 0.55f)
    }
    // iOS: the folder grows out of its tile and shrinks back into it.
    val anchor = vm.folderAnchor
    val panelCx = panelLeft + FolderLayout.PanelW / 2f
    val panelCy = FolderLayout.PanelTop + panelH / 2f
    val fromScaleX = anchor?.let { (it.width * HomeLayout.FocusScale / FolderLayout.PanelW).coerceIn(0.05f, 1f) } ?: 0.9f
    val fromScaleY = anchor?.let { (it.height * HomeLayout.FocusScale / panelH).coerceIn(0.05f, 1f) } ?: 0.9f
    val fromDx = anchor?.let { it.center.x - panelCx } ?: 0f
    val fromDy = anchor?.let { it.center.y - panelCy } ?: 0f

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
        PText(
            folder.label, 34.sp, FontWeight.Bold, Color.White,
            Modifier
                .offset((panelLeft + 10f).dp, FolderLayout.TitleTop.dp)
                .graphicsLayer { alpha = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f); translationY = (1f - t) * 14.dp.toPx() },
            letterSpacing = (-0.5).sp, shadow = true,
        )
        Box(
            Modifier
                .offset(panelLeft.dp, FolderLayout.PanelTop.dp)
                .size(FolderLayout.PanelW.dp, panelH.dp)
                .graphicsLayer {
                    alpha = (t * 3f).coerceIn(0f, 1f)
                    scaleX = lerpF(fromScaleX, 1f, t)
                    scaleY = lerpF(fromScaleY, 1f, t)
                    translationX = lerpF(fromDx, 0f, t).dp.toPx()
                    translationY = lerpF(fromDy, 0f, t).dp.toPx()
                }
        ) {
            // Liquid Glass bends the blurred Home Screen behind it; Frosted is the classic blur.
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (theme.liquid) {
                            Modifier.glass(panelShape, panelTint, GlassSource.Scene, elevation = 22.dp, blurredLens = true)
                        } else {
                            Modifier.glass(panelShape, panelTint, GlassSource.Scene, elevation = 22.dp, frosted = true)
                        }
                    )
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = ((t - 0.25f) / 0.75f).coerceIn(0f, 1f)
                    val k = lerpF(0.92f, 1f, t)
                    scaleX = k
                    scaleY = k
                    translationX = lerpF(fromDx * 0.25f, 0f, t).dp.toPx()
                    translationY = lerpF(fromDy * 0.25f, 0f, t).dp.toPx()
                }
        ) {
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
