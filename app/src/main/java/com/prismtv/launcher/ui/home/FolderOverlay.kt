package com.prismtv.launcher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlin.math.max

/** An open folder: the Home Screen melts into blur and a glass panel holds the folder's apps. */
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
    val rows = max(3, (folder.apps.size + FolderLayout.Cols - 1) / FolderLayout.Cols)
    val panelH = FolderLayout.PadTop + rows * FolderLayout.RowPitch + 8f

    Box(Modifier.fillMaxSize()) {
        // Full-screen blur of the Home Screen.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = t }
                .glass(
                    RectangleShape,
                    if (p.isDark) Color.Black.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.16f),
                    GlassSource.Scene, rimStrength = 0f, lens = false,
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
            // Title pill.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp)
                    .height(44.dp)
                    .glass(RoundedCornerShape(50), theme.pillFill(), GlassSource.Scene, elevation = 8.dp)
                    .padding(horizontal = 26.dp),
                contentAlignment = Alignment.Center,
            ) {
                PText(folder.label, 21.sp, FontWeight.Bold, if (p.isDark) Color.White else p.textPrimary)
            }
            // Panel.
            Box(
                Modifier
                    .offset(FolderLayout.panelLeft().dp, FolderLayout.PanelTop.dp)
                    .size(FolderLayout.PanelW.dp, panelH.dp)
                    .glass(
                        RoundedCornerShape(36.dp),
                        if (theme.liquid) Color.White.copy(alpha = if (p.isDark) 0.06f else 0.20f) else p.panelGlass.copy(alpha = 0.6f),
                        GlassSource.Scene,
                        elevation = 18.dp,
                    )
            )
            folder.apps.forEachIndexed { i, app ->
                val r = FolderLayout.tileRect(i)
                key(app.id) {
                    GridTile(
                        item = AppItem(app),
                        focused = i == vm.folderFocus,
                        unfocusedScale = 1f,
                        showLabel = i == vm.folderFocus,
                        labelColor = if (p.isDark) Color.White else p.textPrimary,
                        labelOffset = 0f,
                        tileH = r.height,
                        modifier = Modifier
                            .offset(r.left.dp, r.top.dp)
                            .size(r.width.dp, r.height.dp),
                    )
                }
            }
        }
    }
}
