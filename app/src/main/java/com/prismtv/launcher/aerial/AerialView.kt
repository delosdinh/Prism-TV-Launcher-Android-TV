package com.prismtv.launcher.aerial

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.prismtv.launcher.R
import com.prismtv.launcher.data.AerialSource
import com.prismtv.launcher.data.LauncherPrefs
import com.prismtv.launcher.data.PrefsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The aerial screensaver surface, shared by [AerialActivity] (Show Screensaver) and
 * [AerialDreamService] (the system screensaver). Plays Apple TV aerials full screen with a soft dip to
 * black between shots and the location shown briefly at the start of each one, like the Kodi Aerial
 * add-on. With no network (or "Prism Scenes" chosen) it pans slowly across Prism's own scenes instead.
 */
class AerialView(context: Context) : FrameLayout(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val prefs: LauncherPrefs = PrefsStore(context).load()

    private val surface = SurfaceView(context)
    private val sceneA = sceneImage()
    private val sceneB = sceneImage()
    private val curtain = View(context).apply { setBackgroundColor(Color.BLACK) }
    private val location = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        typeface = ResourcesCompat.getFont(context, R.font.inter_semibold)
        letterSpacing = -0.01f
        setShadowLayer(dp(5f), 0f, dp(1f), 0x77000000)
        alpha = 0f
    }
    private val clock = TextClock(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        typeface = ResourcesCompat.getFont(context, R.font.inter_medium)
        setShadowLayer(dp(4f), 0f, dp(1f), 0x66000000)
        alpha = 0.92f
    }

    private var player: ExoPlayer? = null
    private var videos: List<AerialVideo> = emptyList()
    private var useHttp = false
    private var errors = 0
    private var fadedOut = false
    private var started = false
    private var sceneIndex = 0
    private var frontIsA = true

    init {
        setBackgroundColor(Color.BLACK)
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(sceneB, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(sceneA, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(curtain, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(
            location,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = dp(64f).toInt()
                bottomMargin = dp(52f).toInt()
            },
        )
        addView(
            clock,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                rightMargin = dp(56f).toInt()
                topMargin = dp(40f).toInt()
            },
        )
        keepScreenOn = true
    }

    fun start() {
        if (started) return
        started = true
        if (prefs.aerialSource == AerialSource.Prism) {
            startScenes()
            return
        }
        scope.launch {
            val list = AerialCatalog.load(context, prefs.aerialQuality, prefs.aerialRange).shuffled().take(40)
            if (!started) return@launch
            if (list.isEmpty()) startScenes() else startVideos(list)
        }
    }

    fun stop() {
        started = false
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        sceneA.animate().cancel()
        sceneB.animate().cancel()
    }

    // ------------------------------------------------------------------------------------ videos

    private fun startVideos(list: List<AerialVideo>) {
        videos = list
        sceneA.visibility = GONE
        sceneB.visibility = GONE
        val p = ExoPlayer.Builder(context).build()
        player = p
        p.setVideoSurfaceView(surface)
        p.volume = 0f
        p.repeatMode = Player.REPEAT_MODE_ALL
        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                onNewShot(mediaItem)
            }

            override fun onRenderedFirstFrame() {
                reveal()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w("PrismAerial", "Playback error", error)
                recover()
            }
        })
        p.setMediaItems(items(), 0, 0L)
        p.prepare()
        p.playWhenReady = true
        onNewShot(p.currentMediaItem)
        handler.post(fadeWatcher)
    }

    private fun items(): List<MediaItem> = videos.map { v ->
        MediaItem.Builder()
            .setMediaId(v.id)
            .setUri(if (useHttp) v.fallbackUrl else v.url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(v.label).build())
            .build()
    }

    /** First failure: retry everything over HTTP (some TV trust stores reject Apple's CDN). Then skip. */
    private fun recover() {
        val p = player ?: return
        errors++
        if (errors > 6) {
            p.release()
            player = null
            startScenes()
            return
        }
        val index = p.currentMediaItemIndex.coerceAtLeast(0)
        if (!useHttp) {
            useHttp = true
            p.setMediaItems(items(), index, 0L)
        } else if (p.mediaItemCount > 1) {
            p.removeMediaItem(index)
            videos = videos.filterIndexed { i, _ -> i != index }
        }
        p.prepare()
        p.playWhenReady = true
    }

    private fun onNewShot(item: MediaItem?) {
        fadedOut = false
        showLocation(item?.mediaMetadata?.title?.toString().orEmpty())
        // Between shots the curtain is already down; lift it once the new shot is moving.
        handler.postDelayed({ reveal() }, 350)
    }

    private fun reveal() {
        if (fadedOut) return
        curtain.animate().alpha(0f).setDuration(1400).setStartDelay(0).start()
    }

    /** Dips to black just before each shot ends, like the Apple TV screensaver. */
    private val fadeWatcher = object : Runnable {
        override fun run() {
            val p = player ?: return
            val duration = p.duration
            if (duration > 0 && !fadedOut && duration - p.currentPosition < 1500) {
                fadedOut = true
                curtain.animate().alpha(1f).setDuration(1100).setStartDelay(0).start()
                location.animate().cancel()
                location.animate().alpha(0f).setStartDelay(0).setDuration(600).start()
            }
            handler.postDelayed(this, 300)
        }
    }

    private fun showLocation(text: String) {
        if (!prefs.aerialShowLocation || text.isBlank()) {
            location.alpha = 0f
            return
        }
        location.animate().cancel()
        location.alpha = 0f
        location.text = text
        location.animate().alpha(1f).setStartDelay(1600).setDuration(900).withEndAction {
            location.animate().alpha(0f).setStartDelay(14_000).setDuration(900).start()
        }.start()
    }

    // ------------------------------------------------------------------------------------ scenes

    private val scenes = listOf(
        R.drawable.hero_glacier to "Glacier Bay",
        R.drawable.hero_aurora to "Northern Lights",
        R.drawable.hero_ocean to "Open Ocean",
        R.drawable.hero_dunes to "Desert Dunes",
        R.drawable.hero_nebula to "Nebula",
    )

    private fun startScenes() {
        surface.visibility = GONE
        sceneA.visibility = VISIBLE
        sceneB.visibility = VISIBLE
        sceneB.alpha = 0f
        sceneIndex = (0 until scenes.size).random()
        showScene(sceneA)
        curtain.animate().alpha(0f).setStartDelay(0).setDuration(1400).start()
        handler.postDelayed(nextScene, SCENE_MS)
    }

    private val nextScene = object : Runnable {
        override fun run() {
            sceneIndex = (sceneIndex + 1) % scenes.size
            val incoming = if (frontIsA) sceneB else sceneA
            val outgoing = if (frontIsA) sceneA else sceneB
            frontIsA = !frontIsA
            incoming.bringToFront()
            location.bringToFront()
            clock.bringToFront()
            incoming.alpha = 0f
            showScene(incoming)
            incoming.animate().alpha(1f).setDuration(1800).withEndAction { outgoing.animate().cancel() }.start()
            handler.postDelayed(this, SCENE_MS)
        }
    }

    /** Slow Ken Burns drift across a still. */
    private fun showScene(view: ImageView) {
        val (res, label) = scenes[sceneIndex]
        view.setImageResource(res)
        view.animate().cancel()
        val dir = if (sceneIndex % 2 == 0) 1f else -1f
        view.scaleX = 1.04f
        view.scaleY = 1.04f
        view.translationX = -dir * dp(28f)
        view.animate()
            .scaleX(1.16f).scaleY(1.16f)
            .translationX(dir * dp(28f))
            .setDuration(SCENE_MS + 2500)
            .setInterpolator(LinearInterpolator())
            .start()
        showLocation(label)
    }

    private fun sceneImage() = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = GONE
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private companion object {
        const val SCENE_MS = 14_000L
    }
}
