package com.prismtv.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The last D-pad move on the Home Screen, for tvOS-style focus motion. On Apple TV the focused item
 * follows your thumb on the Siri Remote (it leans and sways, then settles), and bumps when you push past
 * the end of a row. Android remotes have no touch surface, so each press gives the same feel: the newly
 * focused tile leans toward the move and settles with a little sway; a press that goes nowhere nudges it.
 */
@Stable
class FocusMotion {
    var tick by mutableIntStateOf(0)
        private set
    var dx by mutableFloatStateOf(0f)
        private set
    var dy by mutableFloatStateOf(0f)
        private set
    /** True when the press didn't move focus (end of a row / column). */
    var edge by mutableStateOf(false)
        private set
    /** Uptime of the press, so a tile focused some other way (coming back from an app) doesn't lean. */
    var at = 0L
        private set

    fun moved(dx: Float, dy: Float) = set(dx, dy, false)
    fun bumped(dx: Float, dy: Float) = set(dx, dy, true)

    private fun set(x: Float, y: Float, e: Boolean) {
        dx = x
        dy = y
        edge = e
        at = android.os.SystemClock.uptimeMillis()
        tick++
    }
}

val LocalFocusMotion = staticCompositionLocalOf { FocusMotion() }

/** Lean (arrival) and nudge (edge) offsets for one focused item. */
@Stable
class WobbleState {
    val lean = Animatable(0f)
    val nudge = Animatable(0f)
    var dx by mutableFloatStateOf(0f)
    var dy by mutableFloatStateOf(0f)
}

/**
 * Drives [WobbleState] while [focused]. Arrival: a push toward the move that sways back (under-damped
 * spring). Edge: a quicker, stiffer bump in the pressed direction.
 */
@Composable
fun rememberWobble(focused: Boolean, enabled: Boolean): WobbleState {
    val state = remember { WobbleState() }
    val motion = LocalFocusMotion.current
    LaunchedEffect(focused, enabled) {
        if (!focused || !enabled) {
            state.lean.snapTo(0f)
            state.nudge.snapTo(0f)
            return@LaunchedEffect
        }
        var first = true
        snapshotFlow { motion.tick }.collect {
            val arrival = first
            first = false
            if (motion.edge && !arrival) {
                state.dx = motion.dx
                state.dy = motion.dy
                launch { state.nudge.animateTo(0f, spring(dampingRatio = 0.34f, stiffness = 420f), initialVelocity = 15f) }
            } else if (arrival && !motion.edge && android.os.SystemClock.uptimeMillis() - motion.at < 300) {
                state.dx = motion.dx
                state.dy = motion.dy
                launch { state.lean.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 230f), initialVelocity = 9f) }
            }
        }
    }
    return state
}

/** Applies a wobble: a small shift and tilt toward the move. */
fun Modifier.wobble(w: WobbleState, shift: Float = 9f, tilt: Float = 9f, bump: Float = 11f): Modifier = graphicsLayer {
    val l = w.lean.value
    val n = w.nudge.value
    translationX = (w.dx * (l * shift + n * bump)).dp.toPx()
    translationY = (w.dy * (l * shift * 0.7f + n * bump)).dp.toPx()
    rotationY = w.dx * l * tilt
    rotationX = -w.dy * l * tilt * 0.7f
    cameraDistance = 14f * density
}
