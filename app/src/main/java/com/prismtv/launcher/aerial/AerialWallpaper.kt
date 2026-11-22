package com.prismtv.launcher.aerial

import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.prismtv.launcher.data.AerialQuality
import com.prismtv.launcher.data.AerialRange

/**
 * Settings › Wallpaper › Apple TV Aerials: the aerials play, silently, behind the Home Screen. 1080p
 * SDR (light on the TV's decoder), shown only while Home is on screen; the still underneath shows
 * until the first frame and in Lite Mode. Drawn through a TextureView so Liquid Glass can see it.
 */
@Composable
fun AerialWallpaper(blur: () -> Float, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var videos by remember { mutableStateOf(Resume.list) }
    LaunchedEffect(Unit) {
        if (videos == null) {
            videos = AerialCatalog.load(ctx, AerialQuality.HD, AerialRange.SDR).shuffled().take(12).also { Resume.list = it }
        }
    }
    val list = videos ?: return
    if (list.isEmpty()) return
    var shown by remember { mutableStateOf(false) }
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            setMediaItems(list.map { MediaItem.fromUri(it.url) }, Resume.index.coerceIn(0, list.size - 1), Resume.position)
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                shown = true
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Skip a clip that won't play (and stop trying if none will).
                if (player.mediaItemCount > 1 && Resume.errors++ < 6) {
                    player.seekToNextMediaItem()
                    player.prepare()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            // Carries on from the same shot next time Home shows.
            Resume.index = player.currentMediaItemIndex
            Resume.position = player.currentPosition
            player.removeListener(listener)
            player.release()
        }
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(900), label = "aerialWall")
    AndroidView(
        factory = { c -> TextureView(c).also { player.setVideoTextureView(it) } },
        modifier = modifier.graphicsLayer {
            val b = blur()
            this.alpha = alpha * if (android.os.Build.VERSION.SDK_INT >= 31) 1f else 1f - b
            if (android.os.Build.VERSION.SDK_INT >= 31 && b > 0.01f) {
                val r = (48f * b).dp.toPx()
                renderEffect = android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            } else renderEffect = null
        },
    )
}

/** Where the Home aerials were, so they pick up there. */
private object Resume {
    var list: List<AerialVideo>? = null
    var index = 0
    var position = 0L
    var errors = 0
}
