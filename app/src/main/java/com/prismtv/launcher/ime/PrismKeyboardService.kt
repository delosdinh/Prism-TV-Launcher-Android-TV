package com.prismtv.launcher.ime

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.prismtv.launcher.data.AppearanceSetting
import com.prismtv.launcher.data.KeyboardColour
import com.prismtv.launcher.data.PrefsStore
import com.prismtv.launcher.sound.UiSounds
import com.prismtv.launcher.ui.keyboard.KbNav
import com.prismtv.launcher.ui.keyboard.KeyboardController
import com.prismtv.launcher.ui.keyboard.KeyboardEditor
import com.prismtv.launcher.ui.keyboard.KeyboardKeys
import com.prismtv.launcher.ui.keyboard.KeyboardMetrics
import com.prismtv.launcher.ui.keyboard.WordPredictor
import com.prismtv.launcher.ui.keyboard.keyColoursFor
import com.prismtv.launcher.ui.theme.Appearance
import java.util.Calendar
import kotlin.math.min

/**
 * Prism Keyboard for every app: the iOS-style keyboard, with suggestions, auto-correction, emoji and
 * dictation, driven by the remote's D-pad. Turn it on in Settings › Keyboard (or with
 * `ime enable com.prismtv.launcher/.ime.PrismKeyboardService` then `ime set …`).
 */
class PrismKeyboardService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private lateinit var kb: KeyboardController
    private lateinit var sounds: UiSounds
    private lateinit var dictation: Dictation
    private var soundsOn = true
    private var look by mutableStateOf(Appearance.Dark)
    private var clear by mutableStateOf(false)
    private var info: EditorInfo? = null
    private val handler = Handler(Looper.getMainLooper())
    private var unlisten: (() -> Unit)? = null

    private var selectDown = false
    private var longFired = false
    private val longPress = Runnable {
        if (selectDown && !longFired) {
            longFired = true
            kb.nav(KbNav.LongSelect)
        }
    }

    private val editor = object : KeyboardEditor {
        private val ic: InputConnection? get() = currentInputConnection
        override fun textBefore(n: Int): String = ic?.getTextBeforeCursor(n, 0)?.toString().orEmpty()
        override fun commit(text: String) {
            ic?.commitText(text, 1)
        }
        override fun deleteBefore(n: Int) {
            if (n > 0) ic?.deleteSurroundingText(n, 0)
        }
        override fun capsMode(): Boolean {
            val type = info?.inputType ?: return false
            return (ic?.getCursorCapsMode(type) ?: 0) != 0
        }
        override fun action() = performAction()
        /** Clear empties the whole field, after the cursor too. */
        override fun clearAll(): String {
            val c = ic ?: return ""
            val before = c.getTextBeforeCursor(4096, 0)?.toString().orEmpty()
            val after = c.getTextAfterCursor(4096, 0)?.toString().orEmpty()
            if (before.isEmpty() && after.isEmpty()) return ""
            c.deleteSurroundingText(before.length, after.length)
            return before + after
        }
        override fun batch(block: () -> Unit) {
            val c = ic
            c?.beginBatchEdit()
            try {
                block()
            } finally {
                c?.endBatchEdit()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        sounds = UiSounds(this)
        kb = KeyboardController(
            this,
            sound = { if (soundsOn) sounds.play(it) },
            dictate = { start -> if (start) dictation.start() else dictation.stop() },
        )
        kb.editor = editor
        dictation = Dictation(this, kb)
        Thread {
            WordPredictor.get(this)
            handler.post { kb.refresh() }
        }.start()
        unlisten = DictationBus.listen { text ->
            if (currentInputConnection != null && isInputViewShown) {
                handler.post { kb.insertDictated(text) }
                true
            } else false
        }
    }

    override fun onCreateInputView(): View {
        val view = ComposeView(this)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        window?.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        view.setContent { Panel() }
        return view
    }

    /** Pixels per canvas dp from the display's real size, or null when it can't be read. */
    private fun canvasDensity(): Float? = runCatching {
        val wm = getSystemService(android.view.WindowManager::class.java)
        val (w, h) = if (android.os.Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
        if (w <= 0 || h <= 0) null else min(w / 960f, h / 540f)
    }.getOrNull()

    @androidx.compose.runtime.Composable
    private fun Panel() {
        val cfg = LocalConfiguration.current
        val base = LocalDensity.current
        // The same 960 × 540 design canvas as the launcher, so the keyboard matches on every TV. Sized
        // from the real display: the Configuration leaves out system bars even when they're hidden.
        val px = remember(cfg) { canvasDensity() }
            ?: base.density * (min(cfg.screenWidthDp / 960f, cfg.screenHeightDp / 540f).takeIf { it > 0f } ?: 1f)
        CompositionLocalProvider(LocalDensity provides Density(px, base.fontScale)) {
            val c = keyColoursFor(look, clear)
            val m = KeyboardMetrics.System
            val solid = c.panel.copy(alpha = 1f)
            // Clear: an app's screen can't be read by a keyboard, so there's no lens or blur here, just
            // smoked see-through glass with a bright top edge.
            val fill = if (clear) Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.46f)))
            else Brush.verticalGradient(listOf(lift(solid), solid))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(fill)
                    // A hairline along the top edge, where the keyboard meets the app.
                    .drawBehind { drawRect(Color.White.copy(alpha = if (clear) 0.28f else 0.10f), size = Size(size.width, 1.dp.toPx())) }
                    .padding(top = m.pad.dp, bottom = (m.pad + 8f).dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                KeyboardKeys(kb, c, m)
            }
        }
    }

    private fun lift(c: Color): Color = Color(
        red = c.red + (1f - c.red) * 0.06f,
        green = c.green + (1f - c.green) * 0.06f,
        blue = c.blue + (1f - c.blue) * 0.06f,
    )

    /** Shown over the app, never as a full-screen editor. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        info = attribute
        // Text from Android's voice screen, which took focus away while you spoke.
        DictationBus.take()?.let { text -> handler.postDelayed({ kb.insertDictated(text) }, 120) }
    }

    override fun onStartInputView(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        info = attribute
        val prefs = PrefsStore(this).load()
        com.prismtv.launcher.ui.keyboard.KeyboardLayout.alphabetical = prefs.keyboardLayout == com.prismtv.launcher.data.KeyboardLayoutStyle.Abc
        soundsOn = prefs.sounds
        sounds.enabled = prefs.sounds
        clear = prefs.keyboardColour == KeyboardColour.Clear
        look = when (prefs.keyboardColour) {
            KeyboardColour.Clear -> Appearance.Dark
            KeyboardColour.Light -> Appearance.Light
            KeyboardColour.Balanced -> Appearance.Balanced
            KeyboardColour.Dark -> Appearance.Dark
            KeyboardColour.Automatic -> when (prefs.appearance) {
                AppearanceSetting.Light -> Appearance.Light
                AppearanceSetting.Balanced -> Appearance.Balanced
                AppearanceSetting.Dark -> Appearance.Dark
                AppearanceSetting.Automatic -> {
                    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    if (h >= 19 || h < 7) Appearance.Dark else Appearance.Light
                }
            }
        }
        val type = attribute?.inputType ?: InputType.TYPE_CLASS_TEXT
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        val text = cls == InputType.TYPE_CLASS_TEXT
        val password = (text && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )) || (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val address = text && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
        )
        val noSuggest = type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        kb.suggestionsOn = text && !password && !noSuggest && (prefs.predictive || prefs.autoCorrect)
        kb.autoCorrectOn = text && !password && !address && !noSuggest && prefs.autoCorrect
        kb.autoCapOn = text && !password && !address
        kb.micOn = !password
        kb.actionLabel = actionLabel(attribute)
        if (!restarting) kb.reset(numbers = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME)
        else kb.refresh()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (::dictation.isInitialized) dictation.cancel()
        handler.removeCallbacks(longPress)
        selectDown = false
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The app (or the remote's arrows in its field) moved the cursor: suggestions follow it.
        kb.updateAutoCap()
        kb.refresh()
    }

    override fun onDestroy() {
        unlisten?.invoke()
        handler.removeCallbacksAndMessages(null)
        if (::dictation.isInitialized) dictation.release()
        if (::sounds.isInitialized) sounds.release()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun isSelect(code: Int) = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
        code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isInputViewShown) return super.onKeyDown(keyCode, event)
        when {
            keyCode == KeyEvent.KEYCODE_DPAD_UP -> kb.nav(KbNav.Up)
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> kb.nav(KbNav.Down)
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> kb.nav(KbNav.Left)
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> kb.nav(KbNav.Right)
            isSelect(keyCode) -> if (event.repeatCount == 0) {
                selectDown = true
                longFired = false
                handler.removeCallbacks(longPress)
                handler.postDelayed(longPress, 550)
            } else if (selectDown && !longFired && event.isLongPress) {
                handler.removeCallbacks(longPress)
                longPress.run()
            }
            keyCode == KeyEvent.KEYCODE_BACK -> Unit // handled on release
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_DEL -> kb.nav(KbNav.Delete)
            keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST -> {
                if (event.repeatCount == 0) kb.toggleDictation()
            }
            keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                if (event.repeatCount == 0) editor.commit(('0' + (keyCode - KeyEvent.KEYCODE_0)).toString())
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!isInputViewShown) return super.onKeyUp(keyCode, event)
        when {
            isSelect(keyCode) -> {
                handler.removeCallbacks(longPress)
                if (selectDown && !longFired) kb.nav(KbNav.Select)
                selectDown = false
                longFired = false
            }
            keyCode == KeyEvent.KEYCODE_BACK -> {
                if (kb.closeAccents()) {
                    // Back closes the accent strip first.
                } else if (kb.listening) {
                    dictation.cancel()
                } else {
                    requestHideSelf(0)
                }
            }
            keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_DEL ||
                keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
                keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> Unit
            else -> return super.onKeyUp(keyCode, event)
        }
        return true
    }

    /** The return key: a new line in multi-line fields, otherwise the field's own action (Go, Search…). */
    private fun performAction() {
        val ic = currentInputConnection ?: return
        val ei = info
        val options = ei?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        val multiLine = (ei?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        when {
            multiLine && (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0 || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) ->
                ic.commitText("\n", 1)
            action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED -> {
                ic.performEditorAction(action)
                if (action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_GO ||
                    action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_SEND
                ) requestHideSelf(0)
            }
            else -> {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }
        }
    }

    private fun actionLabel(ei: EditorInfo?): String {
        val options = ei?.imeOptions ?: return "return"
        if ((ei.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0 && options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return "return"
        ei.actionLabel?.toString()?.takeIf { it.isNotBlank() }?.let { return it.lowercase() }
        return when (options and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "go"
            EditorInfo.IME_ACTION_SEARCH -> "search"
            EditorInfo.IME_ACTION_SEND -> "send"
            EditorInfo.IME_ACTION_NEXT -> "next"
            EditorInfo.IME_ACTION_DONE -> "done"
            EditorInfo.IME_ACTION_PREVIOUS -> "previous"
            else -> "return"
        }
    }
}
