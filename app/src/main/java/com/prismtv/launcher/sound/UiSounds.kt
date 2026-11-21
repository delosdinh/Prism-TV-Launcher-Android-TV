package com.prismtv.launcher.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import com.prismtv.launcher.R
import java.util.concurrent.ConcurrentHashMap

/** Interface cues from the Prism TV Originals pack (procedurally synthesised; see tools/ui-sounds). */
enum class Cue(@RawRes val res: Int, val volume: Float) {
    FocusApp(R.raw.ui_focus_app, 0.55f),
    FocusSmall(R.raw.ui_cursor, 0.5f),
    FocusLarge(R.raw.ui_focus_large, 0.55f),
    Click(R.raw.ui_click, 0.6f),
    Back(R.raw.ui_back, 0.55f),
    Toggle(R.raw.ui_toggle, 0.6f),
    Open(R.raw.ui_open, 0.55f),
    /** An app card flicked up and away in the App Switcher. */
    Dismiss(R.raw.ui_dismiss, 0.55f),
    /** The sidebar sliding in from / back out to the left. */
    RevealLeft(R.raw.ui_reveal_left, 0.55f),
    HideLeft(R.raw.ui_hide_left, 0.55f),
    /** Control Centre unfolding from / folding back into the top-right corner. */
    RevealRight(R.raw.ui_reveal_right, 0.55f),
    HideRight(R.raw.ui_hide_right, 0.55f),
    /** The Home Screen shrinking into the App Switcher's deck, and growing back. */
    SwitcherOpen(R.raw.ui_switcher_open, 0.55f),
    SwitcherClose(R.raw.ui_switcher_close, 0.55f),
    Key(R.raw.ui_key, 0.5f),
    FocusKey(R.raw.ui_focus_key, 0.45f),
    Confirm(R.raw.ui_confirm, 0.6f),
    Error(R.raw.ui_error, 0.6f),
    Notify(R.raw.ui_notify, 0.6f),
    Delete(R.raw.ui_delete, 0.6f),
    Clear(R.raw.ui_clear, 0.6f),
}

/**
 * Low-latency navigation sounds (SoundPool, sonification usage so they follow the system volume and
 * duck like other interface sounds). Loaded once; cues that haven't loaded yet are skipped.
 */
class UiSounds(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val ready = ConcurrentHashMap.newKeySet<Int>()
    private val ids: Map<Cue, Int>

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready += id }
        ids = Cue.entries.associateWith { pool.load(context, it.res, 1) }
    }

    var enabled = true
    private var lastFocusAt = 0L

    fun play(cue: Cue) {
        if (!enabled) return
        val id = ids[cue] ?: return
        if (id !in ready) return
        // Holding an arrow repeats fast; keep focus ticks from piling up.
        if (cue == Cue.FocusApp || cue == Cue.FocusSmall || cue == Cue.FocusLarge || cue == Cue.FocusKey) {
            val now = System.nanoTime()
            if (now - lastFocusAt < 45_000_000L) return
            lastFocusAt = now
        }
        pool.play(id, cue.volume, cue.volume, 1, 0, 1f)
    }

    fun release() = pool.release()
}
