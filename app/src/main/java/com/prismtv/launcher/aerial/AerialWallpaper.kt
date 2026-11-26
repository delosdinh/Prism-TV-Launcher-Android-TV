package com.prismtv.launcher.aerial

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceView
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.launch
import com.prismtv.launcher.data.AerialQuality
import com.prismtv.launcher.data.AerialRange

/**
 * Settings › Wallpaper › Apple TV Aerials: the aerials play, silently, behind the Home Screen. 1080p
 * SDR (light on the TV's decoder; on the video layer, below, the screensaver's resolution), shown only while Home is on screen; the still underneath shows
 * until the first frame and in Lite Mode. Drawn through a TextureView so Liquid Glass can see it.
 *
 * On a TV measured too slow for that ([GlassPerf.slowTv]: every video frame in a TextureView redraws all
 * of Home, which ran at 29 ms a frame on a Ugoos AM9 Pro) it plays through a SurfaceView instead, on the
 * hardware video layer under Home, as the screensaver does: Home only redraws when something on it
 * moves, and the aerial never has to hold still. The glass over it then can't refract the video, and
 * behind the app grid the blurred still covers it.
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
    // A TV measured too slow for the glass (see below); decided once, so the stream and buffer match.
    val videoLayer = remember { com.prismtv.launcher.ui.glass.GlassPerf.slowTv && android.os.Build.VERSION.SDK_INT >= 31 }
    var videos by remember { mutableStateOf(Resume.list?.takeIf { Resume.videoLayer == videoLayer }) }
    LaunchedEffect(Unit) {
        if (videos == null) {
            // On the video layer, the same resolution as the screensaver (4K on a 4K panel, by default):
            // a TV that matches its output to the video (Ugoos AM9 Pro, auto frame rate) otherwise
            // switched to 1080p a few seconds in and back to 4K when the aerial stopped, blacking out
            // the screen for a moment each time. In a TextureView, 4K would cost the GPU too much.
            val quality = if (videoLayer) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.prismtv.launcher.data.PrefsStore(ctx).load().aerialQuality
                }
            } else AerialQuality.HD
            videos = AerialCatalog.load(ctx, quality, AerialRange.SDR).shuffled().take(12).also {
                Resume.list = it
                Resume.videoLayer = videoLayer
            }
        }
        android.util.Log.i("PrismAerial", "Home wallpaper: ${videos?.size ?: 0} aerials")
    }
    val list = videos ?: return
    if (list.isEmpty()) return
    var shown by remember { mutableStateOf(false) }
    val gl = remember { GlHolder() }
    val player = remember {
        if (AerialNet.preferHttp(ctx)) Resume.http = true
        ExoPlayer.Builder(ctx).setLoadControl(if (videoLayer) AerialBuffer.screensaver() else AerialBuffer.wallpaper()).build().apply {
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
    DisposableEffect(player) {
        var disposed = false
        val retry = Runnable {
            if (!disposed) {
                player.prepare()
                player.playWhenReady = true
            }
        }
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (!shown) android.util.Log.i("PrismAerial", "Home wallpaper: playing")
                shown = true
                // Playing again: the error budget below is for failures in a row, not for the whole session
                // (it never reset, so after a few screensaver -> Home trips on the AM9 the aerial stopped for good).
                Resume.errors = 0
                onShown(true)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.w("PrismAerial", "Home wallpaper: ${error.errorCodeName}")
                if (Resume.errors++ >= 6) return
                // The decoder wasn't free yet: on the AM9 the screensaver's 4K decoder is still being released as
                // Home comes back (DECODER_INIT_FAILED). That's not the network, so the same shot again in a
                // moment, instead of switching to HTTP and skipping it.
                if (isDecoderBusy(error) && Resume.errors <= 2) {
                    retryHandler.removeCallbacks(retry)
                    retryHandler.postDelayed(retry, 1000)
                    return
                }
                // As the screensaver does: first try the same files over HTTP (some TVs don't trust Apple's
                // CDN certificate), then skip a clip that won't play, and stop trying if none will.
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
            disposed = true
            retryHandler.removeCallbacks(retry)
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
    // Also once measured slow mid-session (its stream is still 1080p then; 4K from the next time Home shows).
    val onVideoLayer = (videoLayer || com.prismtv.launcher.ui.glass.GlassPerf.slowTv) && !useGl
    LaunchedEffect(onVideoLayer) {
        android.util.Log.i("PrismAerial", "Home wallpaper: ${if (onVideoLayer) "SurfaceView (slow GPU)" else "TextureView"}")
    }
    // On the video layer the SurfaceView is drawn by [AerialVideoLayerHost], under all of Home: in here it
    // would be replayed by every glass surface drawing Home's backdrop, and the video moves to wherever it
    // was drawn last (on the AM9 it ended up a rectangle above the dock, with black around it).
    DisposableEffect(player, onVideoLayer) {
        if (onVideoLayer) {
            AerialVideoLayer.videos = list
            AerialVideoLayer.player = player
        }
        // A new shot: a poster of it a few seconds in, once it's playing (see AerialVideoLayer.poster).
        val posterListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                posterHandler.removeCallbacksAndMessages(null)
                posterHandler.postDelayed({ if (AerialVideoLayer.player === player) AerialVideoLayer.refreshPoster() }, 4000)
            }
        }
        if (onVideoLayer) {
            player.addListener(posterListener)
            posterHandler.postDelayed({ if (AerialVideoLayer.player === player) AerialVideoLayer.refreshPoster(maxAgeMs = Long.MAX_VALUE) }, 4000)
        }
        onDispose {
            player.removeListener(posterListener)
            posterHandler.removeCallbacksAndMessages(null)
            if (AerialVideoLayer.player === player) AerialVideoLayer.player = null
        }
    }
    if (onVideoLayer) return
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

/** The Home aerial's player while it plays on the video layer (see [AerialVideoLayerHost]). Snapshot state. */
object AerialVideoLayer {
    var player by mutableStateOf<ExoPlayer?>(null)

    /**
     * A frame of the shot that's playing, for what can't show the video plane (the App Switcher's Home
     * card): the app can't read the plane back (PixelCopy gives black or green on Amlogic). Null until
     * one has been grabbed.
     */
    var poster by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
        private set
    private var posterId: String? = null
    private var posterAt = 0L
    internal var videos: List<AerialVideo> = emptyList()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    /** One grab at a time: each holds a decoder, and a TV like the AM9 has few to spare. */
    private var grabbing = false

    /**
     * Grabs a frame of the shot playing now, near where it is, from its lightest stream (one keyframe:
     * a small ranged download). Skipped while a grab is running, and when the poster is already from
     * this shot and newer than [maxAgeMs].
     */
    fun refreshPoster(maxAgeMs: Long = 0L) {
        val p = player ?: return
        if (grabbing) return
        val video = videos.getOrNull(p.currentMediaItemIndex) ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (posterId == video.id && poster != null && now - posterAt < maxAgeMs) return
        val atUs = p.currentPosition.coerceAtLeast(0L) * 1000L
        val url = if (Resume.http) video.posterUrl.replaceFirst("https://", "http://") else video.posterUrl
        grabbing = true
        scope.launch {
            try {
                val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { grabFrame(url, atUs) }
                if (bmp != null) {
                    poster = bmp
                    posterId = video.id
                    posterAt = android.os.SystemClock.elapsedRealtime()
                }
            } finally {
                grabbing = false
            }
        }
    }

    private fun grabFrame(url: String, atUs: Long): androidx.compose.ui.graphics.ImageBitmap? {
        val t0 = android.os.SystemClock.elapsedRealtime()
        val r = android.media.MediaMetadataRetriever()
        return try {
            r.setDataSource(url, emptyMap())
            val opt = android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            val frame = if (android.os.Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(atUs, opt, 960, 540)
            else r.getFrameAtTime(atUs, opt)
            android.util.Log.i("PrismAerial", "Home wallpaper: poster ${frame?.width}x${frame?.height} in ${android.os.SystemClock.elapsedRealtime() - t0} ms")
            frame?.asImageBitmap()
        } catch (t: Throwable) {
            android.util.Log.w("PrismAerial", "Home wallpaper: no poster frame", t)
            null
        } finally {
            runCatching { r.release() }
        }
    }
}

/**
 * The Home aerial's SurfaceView on a slow TV: drawn once, full screen, under everything else on the
 * screen and outside every Liquid Glass backdrop. A SurfaceView's video follows where its view was last
 * drawn, and a backdrop is drawn again, offset, by each glass surface over it. It punches a hole through
 * the black under it; Home's backdrop above it stays clear where the aerial plays.
 */
@Composable
fun AerialVideoLayerHost(modifier: Modifier = Modifier) {
    val p = AerialVideoLayer.player ?: return
    key(p) {
        AndroidView(
            factory = { c -> SurfaceView(c).also { p.setVideoSurfaceView(it) } },
            modifier = modifier,
        )
    }
}

private val posterHandler = Handler(Looper.getMainLooper())
private val retryHandler = Handler(Looper.getMainLooper())

/** A decoder that couldn't start or was taken back (another player still holds it), not a bad file or network. */
private fun isDecoderBusy(e: androidx.media3.common.PlaybackException): Boolean =
    e.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
        e.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED

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
    /** Whether [list] was picked for the video layer (the screensaver's resolution) or a TextureView (1080p). */
    var videoLayer = false
}
