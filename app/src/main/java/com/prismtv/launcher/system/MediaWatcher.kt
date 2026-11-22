package com.prismtv.launcher.system

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What's playing right now, for Control Centre's Now Playing tile. */
data class NowPlaying(
    val pkg: String,
    val appLabel: String,
    val title: String,
    val subtitle: String,
    val art: ImageBitmap?,
    val appIcon: ImageBitmap?,
    val playing: Boolean,
)

/**
 * Follows other apps' media sessions (needs the same Notification access as Control Centre's
 * notifications). Picks the session that's playing, or else the most recent paused one, and controls it.
 */
object MediaWatcher {
    private val state = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = state

    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var manager: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()
    private var demo: NowPlaying? = null

    // Playback updates arrive often (pause, seek, buffering): keep the scaled artwork and app icons
    // instead of rebuilding them each time.
    private var artKey: String? = null
    private var artCache: ImageBitmap? = null
    private val icons = HashMap<String, ImageBitmap?>()
    private val labels = HashMap<String, String>()

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list.orEmpty())
    }

    /** Starts following sessions, or picks them up again (e.g. once Notification access is granted). */
    fun start(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        // Already following: the sessions listener keeps us up to date, nothing to ask Android again.
        if (manager != null) return
        val component = ComponentName(ctx, PrismNotificationListener::class.java)
        val m = manager ?: ctx.getSystemService(MediaSessionManager::class.java) ?: return
        try {
            if (manager == null) {
                m.addOnActiveSessionsChangedListener(sessionsChanged, component, main)
                manager = m
            }
            attach(m.getActiveSessions(component).orEmpty())
        } catch (_: SecurityException) {
            // No Notification access yet: Now Playing stays hidden.
            manager = null
            attach(emptyList())
        } catch (_: Throwable) {
        }
    }

    /** CI screenshots and previews: a fixed Now Playing (null goes back to the real sessions). */
    fun demo(value: NowPlaying?) {
        demo = value
        refresh()
    }

    /** Something is playing sound: a media session, or any music stream. */
    fun isPlaying(context: Context): Boolean {
        if (demo?.playing == true) return true
        if (controllers.any { runCatching { it.playbackState?.state == PlaybackState.STATE_PLAYING }.getOrDefault(false) }) return true
        val audio = context.getSystemService(AudioManager::class.java)
        return runCatching { audio?.isMusicActive == true }.getOrDefault(false)
    }

    fun playPause() {
        val c = current() ?: return
        runCatching {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
            else c.transportControls.play()
        }
    }

    fun next() = skip(forward = true)

    fun previous() = skip(forward = false)

    /** Opens the app that's playing (its player, when the session says which screen that is). */
    fun open(context: Context): Boolean {
        val c = current() ?: return false
        val session = runCatching { c.sessionActivity }.getOrNull()
        if (session != null && runCatching { session.send(); true }.getOrDefault(false)) return true
        val launch = context.packageManager.getLeanbackLaunchIntentForPackage(c.packageName)
            ?: context.packageManager.getLaunchIntentForPackage(c.packageName)
            ?: return false
        return SystemActions.start(context, launch)
    }

    private fun skip(forward: Boolean) {
        val c = current() ?: return
        val ps = c.playbackState
        val actions = ps?.actions ?: 0L
        runCatching {
            val skipAction = if (forward) PlaybackState.ACTION_SKIP_TO_NEXT else PlaybackState.ACTION_SKIP_TO_PREVIOUS
            when {
                actions and skipAction != 0L -> if (forward) c.transportControls.skipToNext() else c.transportControls.skipToPrevious()
                // Video players often can't skip tracks: jump within the programme instead.
                ps != null && actions and PlaybackState.ACTION_SEEK_TO != 0L ->
                    c.transportControls.seekTo((ps.position + if (forward) 15_000 else -15_000).coerceAtLeast(0))
                forward -> c.transportControls.fastForward()
                else -> c.transportControls.rewind()
            }
        }
    }

    private fun attach(list: List<MediaController>) {
        val ctx = appContext ?: return
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = list.filter { it.packageName != ctx.packageName }
        controllers.forEach { runCatching { it.registerCallback(callback, main) } }
        refresh()
    }

    private fun current(): MediaController? {
        fun stateOf(c: MediaController) = runCatching { c.playbackState?.state }.getOrNull()
        return controllers.firstOrNull { stateOf(it) == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull {
                runCatching { it.metadata }.getOrNull() != null &&
                    stateOf(it) in setOf(PlaybackState.STATE_PAUSED, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING)
            }
    }

    private fun refresh() {
        demo?.let { state.value = it; return }
        val ctx = appContext
        val c = current()
        val next = if (ctx == null || c == null) null else runCatching { describe(ctx, c) }.getOrNull()
        // The same title, art and state as before: nothing for Control Centre to redraw.
        if (next != state.value) state.value = next
    }

    private fun describe(ctx: Context, c: MediaController): NowPlaying? {
        val md = c.metadata ?: return null
        val pm = ctx.packageManager
        val label = labels.getOrPut(c.packageName) {
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString() }.getOrDefault(c.packageName)
        }
        fun text(key: String) = runCatching { md.getString(key) }.getOrNull()?.takeIf { it.isNotBlank() }
        val title = text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: text(MediaMetadata.METADATA_KEY_TITLE) ?: return null
        val subtitle = text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: text(MediaMetadata.METADATA_KEY_ARTIST)
            ?: text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: text(MediaMetadata.METADATA_KEY_ALBUM) ?: label
        val key = c.packageName + "|" + title + "|" + subtitle
        if (key != artKey) {
            val art = listOf(MediaMetadata.METADATA_KEY_DISPLAY_ICON, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART)
                .firstNotNullOfOrNull { runCatching { md.getBitmap(it) }.getOrNull() }
            artCache = art?.let { small(it) }?.asImageBitmap()
            artKey = key
        }
        val icon = icons.getOrPut(c.packageName) {
            runCatching { pm.getApplicationIcon(c.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
        return NowPlaying(
            pkg = c.packageName,
            appLabel = label,
            title = title,
            subtitle = subtitle,
            art = artCache,
            appIcon = icon,
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }

    /** A copy small enough for a 46dp tile, so a 3000px album cover isn't kept in memory. */
    private fun small(b: Bitmap): Bitmap {
        val max = 192
        if (b.width <= max && b.height <= max) return b.copy(Bitmap.Config.ARGB_8888, false) ?: b
        val s = max / maxOf(b.width, b.height).toFloat()
        return Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
    }
}
