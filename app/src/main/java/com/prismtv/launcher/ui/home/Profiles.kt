package com.prismtv.launcher.ui.home

import androidx.compose.animation.core.Animatable
import com.prismtv.launcher.ui.components.atLeast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismtv.launcher.LauncherViewModel
import com.prismtv.launcher.PasscodeDelete
import com.prismtv.launcher.PasscodeKeys
import com.prismtv.launcher.ui.components.Avatar
import com.prismtv.launcher.ui.components.AvatarLook
import com.prismtv.launcher.ui.components.PIcon
import com.prismtv.launcher.ui.components.PText
import com.prismtv.launcher.ui.components.VSpace
import com.prismtv.launcher.ui.components.lerpF
import com.prismtv.launcher.ui.glass.GlassSource
import com.prismtv.launcher.ui.glass.glass
import com.prismtv.launcher.ui.theme.LocalPrism

/**
 * "Who's Watching?" — tvOS's profile picker: everyone's picture in a row over the frosted Home Screen.
 * Shown when the TV wakes (with two or more profiles) and from Control Centre's profile picture.
 */
@Composable
fun ProfilePicker(vm: LauncherViewModel) {
    val visible = vm.profilePicker
    val tState = animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 240f), label = "picker")
    val t by tState
    if (!tState.atLeast(0.002f)) return
    val list = vm.profileList
    val focus = vm.pickerFocus
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = t }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .glass(RectangleShape, Color.Black.copy(alpha = 0.44f), GlassSource.Full, rimStrength = 0f, lens = false, frosted = true)
        )
        Column(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    translationY = (1f - t) * 28.dp.toPx()
                    val s = lerpF(0.96f, 1f, t)
                    scaleX = s
                    scaleY = s
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PText("Who's Watching?", 34.sp, FontWeight.Bold, Color.White, shadow = true, letterSpacing = (-0.5).sp)
            VSpace(44.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.Top) {
                list.forEachIndexed { i, p ->
                    ProfileChoice(vm.nameOf(p), vm.lookOf(p), p.locked, focused = visible && i == focus)
                }
            }
        }
    }
}

@Composable
private fun ProfileChoice(name: String, look: AvatarLook, locked: Boolean, focused: Boolean) {
    val s by animateFloatAsState(if (focused) 1.15f else 1f, spring(dampingRatio = 0.62f, stiffness = 380f), label = "pick")
    Column(Modifier.width(132.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.graphicsLayer {
                scaleX = s
                scaleY = s
                alpha = if (focused) 1f else 0.72f
            }
        ) {
            Avatar(112.dp, focused = focused, look = look)
            if (locked) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(2.dp, 2.dp)
                        .size(30.dp)
                        .shadow(6.dp, CircleShape)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { PIcon(Icons.Rounded.Lock, Color(0xFF1C1C1E), 16.dp) }
            }
        }
        VSpace(20.dp)
        PText(
            name, 16.sp, if (focused) FontWeight.SemiBold else FontWeight.Medium,
            Color.White.copy(alpha = if (focused) 1f else 0.7f), shadow = true, align = TextAlign.Center,
        )
    }
}

/** Four dots and a row of digits, like the tvOS passcode screen. The remote's number keys work too. */
@Composable
fun PasscodePad(vm: LauncherViewModel) {
    val req = vm.passcode
    val holder = remember { arrayOfNulls<LauncherViewModel.PasscodeRequest>(1) }
    if (req != null) holder[0] = req
    val shown = holder[0] ?: return
    val tState = animateFloatAsState(if (req != null) 1f else 0f, spring(dampingRatio = 0.82f, stiffness = 360f), label = "passcode")
    val t by tState
    if (!tState.atLeast(0.002f)) return
    val theme = LocalPrism.current
    val p = theme.palette
    val shake = remember { Animatable(0f) }
    LaunchedEffect(vm.passcodeShake) {
        if (vm.passcodeShake > 0) {
            shake.snapTo(0f)
            shake.animateTo(0f, keyframes {
                durationMillis = 440
                -16f at 60
                14f at 130
                -10f at 200
                7f at 270
                -3f at 350
            })
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = t }
            .background(p.bgBase.copy(alpha = p.backgroundDim)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .graphicsLayer {
                    val s = lerpF(0.92f, 1f, t)
                    scaleX = s
                    scaleY = s
                }
                .width(600.dp)
                .glass(RoundedCornerShape(34.dp), theme.sheetFill(), GlassSource.Full, elevation = 40.dp)
                .padding(horizontal = 26.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PText(shown.title, 22.sp, FontWeight.SemiBold, p.textPrimary, align = TextAlign.Center)
            VSpace(6.dp)
            PText(shown.message, 13.sp, FontWeight.Normal, p.textSecondary, maxLines = 2, align = TextAlign.Center)
            VSpace(24.dp)
            Row(
                Modifier.graphicsLayer { translationX = shake.value.dp.toPx() },
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                repeat(4) { i ->
                    val filled = i < vm.passcodeEntry.length
                    Box(
                        Modifier
                            .size(16.dp)
                            .then(
                                if (filled) Modifier.background(p.textPrimary, CircleShape)
                                else Modifier.border(2.dp, p.textPrimary.copy(alpha = 0.45f), CircleShape)
                            )
                    )
                }
            }
            VSpace(28.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PasscodeKeys.forEachIndexed { i, key ->
                    PasscodeKey(key, focused = req != null && i == vm.passcodeFocus)
                }
            }
        }
    }
}

@Composable
private fun PasscodeKey(key: String, focused: Boolean) {
    val theme = LocalPrism.current
    val p = theme.palette
    val s by animateFloatAsState(if (focused) 1.16f else 1f, spring(dampingRatio = 0.6f, stiffness = 460f), label = "pkey")
    val fg = if (focused) p.textFocused else p.textPrimary
    Box(
        Modifier
            .graphicsLayer {
                scaleX = s
                scaleY = s
            }
            .size(42.dp)
            .then(
                if (focused) Modifier.shadow(12.dp, CircleShape).background(theme.controlFocused(), CircleShape)
                else Modifier.background(p.textPrimary.copy(alpha = 0.10f), CircleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (key == PasscodeDelete) PIcon(Icons.AutoMirrored.Rounded.Backspace, fg, 19.dp)
        else PText(key, 19.sp, FontWeight.Medium, fg)
    }
}
