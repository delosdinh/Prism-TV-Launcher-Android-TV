package com.prismtv.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

/** Debug builds only (`--ez directThresholds true`): the old check, for CI to count against. */
internal object OverlayDebug {
    @Volatile var directThresholds = false
}

/**
 * Whether an overlay animated by this value is open far enough to be composed.
 *
 * Overlays used to test their animated value directly (`if (t < 0.002f) return`). Reading it in
 * composition recomposed the whole overlay (every tile, row and label) on each frame of its open and
 * close animation, although only its graphicsLayer blocks use the value, and those read it themselves.
 * This changes only when the value crosses [threshold]: the same frames, without the recompositions.
 */
@Composable
fun State<Float>.atLeast(threshold: Float): Boolean {
    if (OverlayDebug.directThresholds) return value >= threshold
    val state = this
    return remember(state, threshold) { derivedStateOf { state.value >= threshold } }.value
}
