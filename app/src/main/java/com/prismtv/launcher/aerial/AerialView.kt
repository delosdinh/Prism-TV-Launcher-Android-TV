package com.prismtv.launcher.aerial

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
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
import kotlinx.coroutines.withContext

/**
 * The aerial screensaver surface, shared by [AerialActivity] (Show Screensaver) and
 * [AerialDreamService] (the system screensaver). Plays Apple TV aerials full screen with a soft dip to
 * black between shots and the location shown briefly at the start of each one, like the Kodi Aerial
 * add-on. With no network (or "Prism Scenes" chosen) it pans slowly across Prism's own scenes instead.
 * "Your Pictures" is the same slow pan-and-zoom slideshow over your own photos, and "Shuffle" (like tvOS
 * 27.2) plays an aerial, then a few of your pictures and Prism's scenes, then the next aerial.
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
    private var stillIndex = 0
    private var frontIsA = true

    /** A still for the slideshows: one of Prism's scenes or one of your pictures. */
    private data class Still(val res: Int = 0, val uri: Uri? = null, val label: String = "")

    private var stills: List<Still> = emptyList()
    /** Shuffle: aerials take turns with short runs of stills. */
    private var shuffle = false
    private var showingStills = false
    private var stillsLeft = 0

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
        val source = prefs.aerialSource
        if (source == AerialSource.Prism) {
            startStills(sceneStills())
            return
        }
        scope.launch {
            val photos = if (source == AerialSource.Pictures || source == AerialSource.Shuffle) {
                withContext(Dispatchers.IO) { photoStills() }
            } else emptyList()
            if (!started) return@launch
            if (source == AerialSource.Pictures) {
                startStills(photos.ifEmpty { sceneStills() })
                return@launch
            }
            val list = AerialCatalog.load(context, prefs.aerialQuality, prefs.aerialRange).shuffled().take(40)
            if (!started) return@launch
            shuffle = source == AerialSource.Shuffle
            val mixed = (photos + sceneStills()).shuffled()
            when {
                list.isEmpty() -> startStills(if (shuffle) mixed else sceneStills())
                else -> {
                    stills = mixed
                    startVideos(list)
                }
            }
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
        // Shuffle pauses at the end of each aerial for a run of stills.
        p.pauseAtEndOfMediaItems = shuffle && stills.isNotEmpty()
        p.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) interlude()
            }

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
            startStills(if (shuffle) stills.ifEmpty { sceneStills() } else sceneStills())
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

    // ------------------------------------------------------------------------------------ stills

    private val scenes = listOf(
        R.drawable.hero_glacier to "Glacier Bay",
        R.drawable.hero_aurora to "Northern Lights",
        R.drawable.hero_ocean to "Open Ocean",
        R.drawable.hero_dunes to "Desert Dunes",
        R.drawable.hero_nebula to "Nebula",
    )

    private fun sceneStills(): List<Still> = scenes.map { (res, label) -> Still(res = res, label = label) }.shuffled()

    /** Your pictures: all of them, or the albums and pictures chosen in Settings › Screensaver › Pictures. */
    private fun photoStills(): List<Still> =
        com.prismtv.launcher.data.PhotoLibrary.chosen(context, prefs.screensaverAllPictures, prefs.screensaverPictures)
            .map { Still(uri = it) }
            .shuffled()

    /** A slideshow of [list] (Prism Scenes, Your Pictures, or Shuffle without a network). */
    private fun startStills(list: List<Still>) {
        stills = list.ifEmpty { sceneStills() }
        shuffle = false
        surface.visibility = GONE
        sceneA.visibility = VISIBLE
        sceneB.visibility = VISIBLE
        sceneB.alpha = 0f
        sceneA.alpha = 1f
        stillIndex = (stills.indices).random()
        showStill(sceneA) { curtain.animate().alpha(0f).setStartDelay(0).setDuration(1400).start() }
        handler.postDelayed(nextStill, SCENE_MS)
    }

    /** Shuffle: an aerial has ended, so show a few stills before the next one. */
    private fun interlude() {
        val p = player ?: return
        if (!started) return
        if (stills.isEmpty()) {
            p.seekToNextMediaItem()
            p.play()
            return
        }
        showingStills = true
        stillsLeft = STILLS_PER_RUN
        sceneA.visibility = VISIBLE
        sceneB.visibility = VISIBLE
        sceneB.alpha = 0f
        sceneA.alpha = 1f
        frontIsA = true
        sceneA.bringToFront()
        curtain.bringToFront()
        location.bringToFront()
        clock.bringToFront()
        stillIndex = (stillIndex + 1) % stills.size
        showStill(sceneA) { curtain.animate().alpha(0f).setStartDelay(0).setDuration(1400).start() }
        handler.postDelayed(nextStill, SCENE_MS)
    }

    /** Shuffle: back from the stills to the next aerial, through black. */
    private fun backToVideo() {
        val p = player ?: return
        curtain.bringToFront()
        location.bringToFront()
        clock.bringToFront()
        location.animate().cancel()
        location.animate().alpha(0f).setStartDelay(0).setDuration(500).start()
        curtain.animate().alpha(1f).setStartDelay(0).setDuration(1000).withEndAction {
            showingStills = false
            sceneA.animate().cancel()
            sceneB.animate().cancel()
            sceneA.visibility = GONE
            sceneB.visibility = GONE
            p.seekToNextMediaItem()
            p.play()
        }.start()
    }

    private val nextStill = object : Runnable {
        override fun run() {
            if (!started) return
            if (player != null && showingStills && --stillsLeft <= 0) {
                backToVideo()
                return
            }
            stillIndex = (stillIndex + 1) % stills.size
            val incoming = if (frontIsA) sceneB else sceneA
            val outgoing = if (frontIsA) sceneA else sceneB
            frontIsA = !frontIsA
            incoming.bringToFront()
            curtain.bringToFront()
            location.bringToFront()
            clock.bringToFront()
            incoming.alpha = 0f
            showStill(incoming) {
                incoming.animate().alpha(1f).setDuration(1800).withEndAction { outgoing.animate().cancel() }.start()
            }
            handler.postDelayed(this, SCENE_MS)
        }
    }

    /** Puts the current still on [view] (pictures load in the background) with a slow Ken Burns drift. */
    private fun showStill(view: ImageView, ready: () -> Unit) {
        val still = stills.getOrNull(stillIndex) ?: return
        val index = stillIndex
        fun drift() {
            view.animate().cancel()
            val dir = if (index % 2 == 0) 1f else -1f
            view.scaleX = 1.04f
            view.scaleY = 1.04f
            view.translationX = -dir * dp(28f)
            view.animate()
                .scaleX(1.16f).scaleY(1.16f)
                .translationX(dir * dp(28f))
                .setDuration(SCENE_MS + 2500)
                .setInterpolator(LinearInterpolator())
                .start()
            showLocation(still.label)
            ready()
        }
        val uri = still.uri
        if (uri == null) {
            view.setImageResource(still.res)
            drift()
            return
        }
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { decode(uri) }
            if (!started) return@launch
            if (bitmap != null) view.setImageBitmap(bitmap) else view.setImageResource(scenes[index % scenes.size].first)
            drift()
        }
    }

    /** A picture at about screen size (never the full camera resolution). */
    private fun decode(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val target = maxOf(resources.displayMetrics.widthPixels, 1280)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target * 9 / 16) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()

    private fun sceneImage() = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = GONE
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private companion object {
        const val SCENE_MS = 14_000L
        /** Shuffle: stills between two aerials. */
        const val STILLS_PER_RUN = 3
    }
}
