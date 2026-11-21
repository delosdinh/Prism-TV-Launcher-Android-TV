package com.prismtv.launcher.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.LauncherViewModel
import com.prismtv.launcher.Zone
import com.prismtv.launcher.data.HeroItem
import com.prismtv.launcher.data.WidgetRow
import com.prismtv.launcher.ui.HomeLayout
import com.prismtv.launcher.ui.WidgetLayout
import com.prismtv.launcher.ui.components.PText

private val CardShape = RoundedCornerShape(12.dp)

/**
 * Widget rows on the Home Screen: up to two above the dock (over the Top Shelf) and any number
 * below it, between the dock and the app grid. Each is a titled row of 16:9 cards.
 */
@Composable
fun WidgetRows(
    vm: LauncherViewModel,
    above: List<WidgetRow>,
    below: List<WidgetRow>,
    cols: Int,
    trayTop: State<Float>,
    rowGap: State<Float>,
    gridMode: Boolean,
    hidden: Boolean,
    labelColor: Color,
) {
    if (above.isEmpty() && below.isEmpty()) return
    val vis by animateFloatAsState(if (hidden) 0f else 1f, tween(350), label = "widgetsVis")
    if (vis < 0.01f) return
    val focusZone = vm.zone == Zone.Widget
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = vis }) {
        above.forEachIndexed { i, row ->
            key("a:" + row.key) {
                // Above the dock in the Top Shelf; they slide away (up) while browsing the grid.
                val gone by animateFloatAsState(if (gridMode) 1f else 0f, tween(450, easing = FastOutSlowInEasing), label = "aboveGone")
                WidgetRowView(
                    vm = vm,
                    row = row,
                    focusCol = if (focusZone && vm.widgetIndex == i) vm.widgetCol else -1,
                    labelColor = Color.White,
                    topOf = { WidgetLayout.aboveTop(trayTop.value, i, above.size) },
                    modifier = Modifier.graphicsLayer { alpha = 1f - gone },
                )
            }
        }
        below.forEachIndexed { j, row ->
            key("b:" + row.key) {
                WidgetRowView(
                    vm = vm,
                    row = row,
                    focusCol = if (focusZone && vm.widgetIndex == above.size + j) vm.widgetCol else -1,
                    labelColor = if (gridMode) labelColor else Color.White,
                    topOf = { WidgetLayout.belowTop(trayTop.value, cols, rowGap.value, j) },
                    modifier = Modifier,
                )
            }
        }
    }
}

@Composable
private fun WidgetRowView(
    vm: LauncherViewModel,
    row: WidgetRow,
    focusCol: Int,
    labelColor: Color,
    topOf: () -> Float,
    modifier: Modifier,
) {
    val focused = focusCol >= 0
    val first = WidgetLayout.firstVisible(focusCol.coerceAtLeast(0), row.items.size)
    val scroll by animateFloatAsState(first.toFloat(), spring(dampingRatio = 0.9f, stiffness = 260f), label = "widgetScroll")
    val left = HomeLayout.left()
    Box(
        modifier
            .fillMaxSize()
            .offset { IntOffset(0, topOf().dp.roundToPx()) }
    ) {
        val title = if (row.appLabel.isNotBlank() && row.pkg.isNotEmpty()) "${row.title} · ${row.appLabel}" else row.title
        PText(
            title, 15.sp, FontWeight.SemiBold, labelColor.copy(alpha = if (focused) 1f else 0.85f),
            Modifier.offset(left.dp, 0.dp),
            shadow = labelColor == Color.White,
        )
        row.items.forEachIndexed { c, item ->
            val x = left + (c - scroll) * (WidgetLayout.CardW + WidgetLayout.CardGap)
            if (x < -WidgetLayout.CardW - 40f || x > HomeLayout.ScreenW + 40f) return@forEachIndexed
            key(item.key) {
                WidgetCard(
                    vm = vm,
                    item = item,
                    focused = c == focusCol,
                    labelColor = labelColor,
                    modifier = Modifier
                        .offset(x.dp, WidgetLayout.TitleH.dp)
                        .size(WidgetLayout.CardW.dp, WidgetLayout.CardH.dp),
                )
            }
        }
    }
}

@Composable
private fun WidgetCard(vm: LauncherViewModel, item: HeroItem, focused: Boolean, labelColor: Color, modifier: Modifier) {
    val scale by animateFloatAsState(
        if (focused) 1.14f else 1f,
        if (focused) spring(dampingRatio = 0.82f, stiffness = 520f) else spring(dampingRatio = 1f, stiffness = 620f),
        label = "cardScale",
    )
    val label by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "cardLabel")
    val thumb = vm.thumb(item.art)
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = (if (focused) 24f else 4f).dp.toPx()
                    shape = CardShape
                    clip = true
                }
                .background(Color(0xFF2A2C34))
        ) {
            if (thumb != null) {
                Image(thumb, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                PText(
                    item.title, 13.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.85f),
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                    maxLines = 2,
                )
            }
            if (focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.14f), 0.45f to Color.Transparent))
                )
            }
        }
        if (label > 0.01f) {
            PText(
                item.title, 13.sp, FontWeight.Medium, labelColor,
                Modifier
                    .align(Alignment.BottomCenter)
                    .requiredWidth(220.dp)
                    .offset { IntOffset(0, (WidgetLayout.CardH * 0.07f + 22f).dp.roundToPx()) }
                    .graphicsLayer { alpha = label },
                align = androidx.compose.ui.text.style.TextAlign.Center,
                shadow = labelColor == Color.White,
            )
        }
    }
}
