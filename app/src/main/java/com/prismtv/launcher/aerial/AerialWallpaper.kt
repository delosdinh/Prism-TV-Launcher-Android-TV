package com.prismtv.launcher.aerial

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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
 *
 * Behind the app grid it blurs: with RenderEffect on Android 12 and later, through [AerialGlRenderer] on
 * Android 11 and older ([onLiveBlur] says when that's running, so the blurred still can stay away).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun AerialWallpaper(
    blur: () -> Float,
    modifier: Modifier = Modifier,
    onShown: (Boolean) -> Unit = {},
    onLiveBlur: (Boolean) -> Unit = {},
) {
    val ctx = LocalContext.current
    val liveBlur by rememberUpdatedState(onLiveBlur)
    var videos by remember { mutableStateOf(Resume.list) }
    LaunchedEffect(Unit) {
        if (videos == null) {
            videos = AerialCatalog.load(ctx, AerialQuality.HD, AerialRange.SDR).shuffled().take(12).also { Resume.list = it }
        }
        android.util.Log.i("PrismAerial", "Home wallpaper: ${videos?.size ?: 0} aerials")
    }
    val list = videos ?: return
    if (list.isEmpty()) return
    var shown by remember { mutableStateOf(false) }
    val gl = remember { GlHolder() }
    val player = remember {
        if (AerialNet.preferHttp(ctx)) Resume.http = true
        ExoPlayer.Builder(ctx).setLoadControl(AerialBuffer.wallpaper()).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            setMediaItems(list.map { MediaItem.fromUri(if (Resume.http) it.fallbackUrl else it.url) }, Resume.index.coerceIn(0, list.size - 1), Resume.position)
            prepare()
            playWhenReady = true
        }
    }
    // A GPU measured too slow over a moving aerial (GlassPerf): the aerial pauses behind the app grid,
    // where the blurred still covers it anyway, and while you move around Home; it plays on after.
    LaunchedEffect(player) {
        snapshotFlow {
            com.prismtv.launcher.ui.glass.GlassPerf.cheap &&
                (blur() > 0.98f || com.prismtv.launcher.ui.glass.GlassPerf.navigating)
        }.collect { hold -> player.playWhenReady = !hold }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (!shown) android.util.Log.i("PrismAerial", "Home wallpaper: playing")
                shown = true
                onShown(true)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.w("PrismAerial", "Home wallpaper: ${error.errorCodeName}")
                // As the screensaver does: first try the same files over HTTP (some TVs don't trust Apple's
                // CDN certificate), then skip a clip that won't play, and stop trying if none will.
                if (Resume.errors++ >= 6) return
                val index = player.currentMediaItemIndex.coerceAtLeast(0)
                if (!Resume.http) {
                    Resume.http = true
                    AerialNet.fellBack(ctx, error)
                    player.setMediaItems(list.map { MediaItem.fromUri(it.fallbackUrl) }, index, 0L)
                } else if (player.mediaItemCount > 1) {
                    player.seekToNextMediaItem()
                }
                player.prepare()
                player.playWhenReady = true
            }
        }
        player.addListener(listener)
        onDispose {
            // Carries on from the same shot next time Home shows.
            Resume.index = player.currentMediaItemIndex
            Resume.position = player.currentPosition
            player.removeListener(listener)
            gl.playerReleased = true
            player.release()
            AerialBuffer.released()
            onShown(false)
        }
    }
    // Android 11 and older: drawn through OpenGL so the blur behind the grid can move (no RenderEffect).
    var useGl by remember { mutableStateOf(android.os.Build.VERSION.SDK_INT < 31 && !Resume.glFailed) }
    LaunchedEffect(useGl) {
        if (!useGl) liveBlur(false)
        else snapshotFlow { blur() }.collect { b ->
            gl.blur = b
            gl.renderer?.setBlur(b)
        }
    }
    // Always drawn at full strength (a TextureView that's never drawn never gets its first frame):
    // the still above it fades away once the aerial is playing.
    key(useGl) {
        AndroidView(
            factory = { c ->
                TextureView(c).also { tv ->
                    if (!useGl) {
                        player.setVideoTextureView(tv)
                    } else {
                        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                val main = Handler(Looper.getMainLooper())
                                val r = AerialGlRenderer(
                                    st, width, height,
                                    onInput = { surface ->
                                        main.post {
                                            if (!gl.playerReleased && gl.renderer != null) {
                                                gl.surface = surface
                                                player.setVideoSurface(surface)
                                            }
                                        }
                                    },
                                    onFirstFrame = { main.post { if (gl.renderer != null) liveBlur(true) } },
                                    onFailed = {
                                        main.post {
                                            Resume.glFailed = true
                                            gl.renderer = null
                                            useGl = false
                                        }
                                    },
                                )
                                r.setBlur(gl.blur)
                                gl.renderer = r
                            }

                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                                gl.renderer?.resize(width, height)
                            }

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                val r = gl.renderer ?: return true
                                gl.surface?.let { if (!gl.playerReleased) player.clearVideoSurface(it) }
                                gl.surface = null
                                gl.renderer = null
                                liveBlur(false)
                                r.release() // releases st once it has stopped drawing into it
                                return false
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                        }
                    }
                }
            },
            modifier = modifier.graphicsLayer {
                val b = blur()
                // A GPU measured too slow for this (GlassPerf) gets the blurred still over the grid instead.
                if (android.os.Build.VERSION.SDK_INT >= 31 && b > 0.01f && !com.prismtv.launcher.ui.glass.GlassPerf.cheap) {
                    // Lighter than a still's blur, so the aerial still reads as moving behind the app grid.
                    val r = (22f * b).dp.toPx()
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                } else renderEffect = null
            },
        )
    }
}

/** The OpenGL path's pieces, shared between the TextureView's listener and the player's disposal. */
private class GlHolder {
    var renderer: AerialGlRenderer? = null
    var surface: Surface? = null
    var blur = 0f
    var playerReleased = false
}

/** Where the Home aerials were, so they pick up there. */
private object Resume {
    var list: List<AerialVideo>? = null
    var index = 0
    var position = 0L
    var errors = 0
    var http = false
    /** OpenGL wouldn't start on this TV: the still stands in behind the grid, as before. */
    var glFailed = false
}
