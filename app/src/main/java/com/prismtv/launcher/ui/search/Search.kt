package com.prismtv.launcher.ui.search

import androidx.compose.animation.core.animateFloatAsState
import com.prismtv.launcher.ui.components.atLeast
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.AppSearchResult
import com.prismtv.launcher.LauncherViewModel
import com.prismtv.launcher.SearchResult
import com.prismtv.launcher.SettingSearchResult
import com.prismtv.launcher.TitleSearchResult
import com.prismtv.launcher.ui.components.AppArtwork
import com.prismtv.launcher.ui.components.PIcon
import com.prismtv.launcher.ui.components.PText

/** Card size on the 960 × 540 canvas, and the gap between cards. */
private const val CardW = 150f
private const val CardH = 84f
private const val Gap = 22f
private const val RowLeft = 60f

/**
 * Prism Search, as on tvOS: results in a row above Prism's keyboard, updating with each letter. Up
 * from the keyboard's top row moves into them; Down goes back to typing.
 */
@Composable
fun SearchResults(vm: LauncherViewModel) {
    val open = vm.searchOpen
    val tState = animateFloatAsState(if (open) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 300f), label = "search")
    val t by tState
    if (!tState.atLeast(0.01f)) return
    val results = vm.searchResults
    val focus = vm.searchFocus
    // The row scrolls so the focused card stays on screen.
    val visible = ((960f - RowLeft * 2) / (CardW + Gap)).toInt()
    val first = if (focus < 0) 0 else (focus - visible + 2).coerceIn(0, (results.size - visible).coerceAtLeast(0))
    val scroll by animateFloatAsState(first * (CardW + Gap), spring(dampingRatio = 0.9f, stiffness = 260f), label = "searchScroll")

    // A deeper shade over the top of the screen, so results read clearly over the Top Shelf.
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = t }
            .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.62f), 0.42f to Color.Black.copy(alpha = 0.38f), 0.6f to Color.Transparent))
    )
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = t; translationY = (1f - t) * -24.dp.toPx() }
    ) {
        PText(
            vm.searchHeading, 14.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.78f),
            Modifier.offset(RowLeft.dp, 22.dp), shadow = true,
        )
        if (results.isEmpty() && vm.keyboardText.isNotBlank()) {
            PText(
                "Try another word, or an app's name.", 13.sp, FontWeight.Normal, Color.White.copy(alpha = 0.6f),
                Modifier.offset(RowLeft.dp, 46.dp), shadow = true,
            )
        }
        results.forEachIndexed { i, r ->
            key(r.key) {
                val x = RowLeft + i * (CardW + Gap) - scroll
                if (x > -CardW - 40f && x < 1000f) ResultCard(vm, r, i == focus, x)
            }
        }
    }
}

@Composable
private fun ResultCard(vm: LauncherViewModel, r: SearchResult, focused: Boolean, x: Float) {
    val grow by animateFloatAsState(if (focused) 1.1f else 1f, spring(dampingRatio = 0.7f, stiffness = 320f), label = "resultGrow")
    val lift by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "resultLift")
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .offset(x.dp, 46.dp)
            .width(CardW.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(CardW.dp, CardH.dp)
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                    shadowElevation = (6f + 18f * lift).dp.toPx()
                    this.shape = shape
                    clip = true
                }
                .background(Color(0xFF24262C))
                .then(if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.85f), shape) else Modifier)
        ) {
            when (r) {
                is AppSearchResult -> AppArtwork(r.app)
                is TitleSearchResult -> {
                    val art = vm.thumb(r.item.art, 400)
                    if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f)))
                    )
                    PText(
                        r.item.appLabel.uppercase(), 8.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.8f),
                        Modifier.align(Alignment.BottomStart).padding(start = 9.dp, bottom = 7.dp), letterSpacing = 0.8.sp,
                    )
                }
                is SettingSearchResult -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFFA3A6AE), Color(0xFF6C6F77)))),
                    contentAlignment = Alignment.Center,
                ) { PIcon(Icons.Rounded.Settings, Color.White, 40.dp) }
            }
        }
        val sub = when (r) {
            is AppSearchResult -> "App"
            is TitleSearchResult -> r.item.meta.ifBlank { r.item.appLabel }
            is SettingSearchResult -> r.where
        }
        PText(
            r.label, 12.sp, FontWeight.SemiBold, Color.White.copy(alpha = if (focused) 1f else 0.85f),
            Modifier.padding(top = if (focused) 12.dp else 8.dp), align = TextAlign.Center, shadow = true,
        )
        PText(sub, 10.sp, FontWeight.Normal, Color.White.copy(alpha = 0.6f), align = TextAlign.Center, shadow = true)
    }
}
