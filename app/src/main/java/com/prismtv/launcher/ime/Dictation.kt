package com.prismtv.launcher.ime

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.prismtv.launcher.ui.keyboard.KeyboardController
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Speech to text for Prism Keyboard. With the microphone allowed, it listens right in the keyboard
 * (Android's speech recogniser, which on Android TV listens through the remote's microphone). Otherwise,
 * or if the recogniser can't hear anything, it opens Android's own voice input screen and types the result.
 */
class Dictation(private val context: Context, private val kb: KeyboardController) {
    private var recognizer: SpeechRecognizer? = null
    private var usedFallback = false

    private fun micAllowed(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start() {
        usedFallback = false
        if (!micAllowed() || !SpeechRecognizer.isRecognitionAvailable(context)) {
            openVoiceScreen()
            return
        }
        release()
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull() ?: run { openVoiceScreen(); return }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                kb.listening = true
            }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { kb.partial = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                kb.listening = false
                kb.partial = ""
                release()
                if (text.isNullOrBlank()) kb.showNotice("Didn't catch that. Press the mic to try again.") else kb.insertDictated(text)
            }
            override fun onError(error: Int) {
                kb.listening = false
                kb.partial = ""
                release()
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        kb.showNotice("Didn't catch that. Press the mic to try again.")
                    // The recogniser couldn't open the microphone or isn't usable here: Android's own
                    // voice screen knows how to talk to the remote.
                    else -> if (!usedFallback) openVoiceScreen() else kb.showNotice("Dictation isn't available right now")
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        kb.listening = true
        kb.partial = ""
        runCatching { r.startListening(intent) }.onFailure { kb.listening = false; release(); openVoiceScreen() }
    }

    fun stop() {
        recognizer?.stopListening() ?: run { kb.listening = false }
    }

    fun cancel() {
        recognizer?.cancel()
        release()
        kb.listening = false
        kb.partial = ""
    }

    fun release() {
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
    }

    private fun openVoiceScreen() {
        usedFallback = true
        val i = Intent(context, VoiceInputActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        if (runCatching { context.startActivity(i) }.isFailure) kb.showNotice("Dictation isn't available right now")
    }
}

/** Hands text from the voice screen back to whichever keyboard is waiting for it. */
object DictationBus {
    private val listeners = CopyOnWriteArrayList<(String) -> Boolean>()
    @Volatile private var pending: String? = null

    fun deliver(text: String) {
        for (l in listeners) if (l(text)) return
        pending = text
    }

    /** Text that arrived while no keyboard could take it (it's typed when the field comes back). */
    fun take(): String? = pending.also { pending = null }

    fun listen(l: (String) -> Boolean): () -> Unit {
        listeners += l
        return { listeners -= l }
    }
}

/** Android's voice input screen (for keyboards that can't listen themselves). */
class VoiceInputActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to type")
        if (runCatching { startActivityForResult(intent, REQ) }.isFailure) finish()
    }

    @Deprecated("Activity result API is fine for a one-shot trampoline")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ && resultCode == RESULT_OK) {
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { DictationBus.deliver(it) }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private companion object {
        const val REQ = 7
    }
}

/** Prism Keyboard as an Android input method: is it on, is it the keyboard in use, and setting it up. */
object PrismIme {
    const val ID = "com.prismtv.launcher/.ime.PrismKeyboardService"

    private fun component(ctx: Context) = ComponentName(ctx, PrismKeyboardService::class.java)

    private fun matches(ctx: Context, id: String?): Boolean =
        id != null && ComponentName.unflattenFromString(id) == component(ctx)

    fun isEnabled(ctx: Context): Boolean = runCatching {
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
            ?.split(':')?.any { matches(ctx, it.substringBefore(';')) } == true
    }.getOrDefault(false)

    fun isCurrent(ctx: Context): Boolean = runCatching {
        matches(ctx, Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
    }.getOrDefault(false)

    /** Android's keyboard chooser (only works once Prism Keyboard is turned on). */
    fun showPicker(ctx: Context): Boolean = runCatching {
        (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        true
    }.getOrDefault(false)

    fun openSettings(ctx: Context): Boolean = runCatching {
        ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
