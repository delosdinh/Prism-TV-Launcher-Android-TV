package com.prismtv.launcher.ui.keyboard

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prismtv.launcher.sound.Cue
import java.text.BreakIterator

/** Where typed text goes: an app's text field (system keyboard) or a string in Prism. */
interface KeyboardEditor {
    fun textBefore(n: Int): String
    fun commit(text: String)
    fun deleteBefore(n: Int)
    /** True when the next letter should be a capital (start of the field or a sentence). */
    fun capsMode(): Boolean
    /** The return / Go / Search key. */
    fun action()
    fun batch(block: () -> Unit) = block()
}

enum class KbNav { Up, Down, Left, Right, Select, LongSelect, Delete }

data class Suggestion(val text: String, val kind: Kind) {
    enum class Kind { Typed, Correction, Completion, Prediction }
}

/**
 * Prism Keyboard: an iOS-style keyboard driven by the D-pad, with suggestions, auto-correction, emoji
 * and dictation. Shared by the system keyboard (any app) and Prism's own text entry (Rename Folder).
 * Row -1 is the suggestion bar; on the emoji keyboard rows 0..2 are the emoji grid and row 3 the bar.
 */
class KeyboardController(
    context: Context,
    private val sound: (Cue) -> Unit,
    /** Starts (true) or stops (false) dictation. */
    private val dictate: (Boolean) -> Unit,
) {
    private val app = context.applicationContext
    private val sp = app.getSharedPreferences("prism_keyboard", Context.MODE_PRIVATE)

    var editor: KeyboardEditor? = null

    // Per field.
    var suggestionsOn by mutableStateOf(true)
    var autoCorrectOn by mutableStateOf(true)
    var autoCapOn by mutableStateOf(true)
    var actionLabel by mutableStateOf("return")
    var micOn by mutableStateOf(true)

    // Keyboard state.
    var layer by mutableIntStateOf(KeyboardLayout.Letters)
        private set
    var shift by mutableStateOf(false)
        private set
    var capsLock by mutableStateOf(false)
        private set
    var row by mutableIntStateOf(1)
        private set
    var col by mutableIntStateOf(4)
        private set
    var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
        private set

    /** Held on a key with accents: its variants, shown in a strip above it (null when closed). */
    var accents by mutableStateOf<List<String>?>(null)
        private set
    var accentIndex by mutableIntStateOf(0)
        private set

    // Emoji.
    var emojiCategory by mutableIntStateOf(1)
        private set
    var emojiIndex by mutableIntStateOf(0)
        private set
    var emojiFirstRow by mutableIntStateOf(0)
        private set
    var recentEmoji by mutableStateOf(sp.getString("recentEmoji", "").orEmpty().split(' ').filter { it.isNotBlank() })
        private set

    // Dictation.
    var listening by mutableStateOf(false)
    var partial by mutableStateOf("")
    /** A short message in the suggestion bar ("Didn't catch that"). */
    var notice by mutableStateOf<String?>(null)
    private var noticeAt = 0L

    private var lastShiftAt = 0L
    /** The last auto-correction (original, replacement), undone by the next Delete. */
    private var lastCorrection: Pair<String, String>? = null
    /** A word the user undid a correction for: left alone at the next space (and learnt). */
    private var keepWord: String? = null
    private val predictor: WordPredictor? get() = WordPredictor.peek()

    /** A new field (or Prism's own entry) starts: [numbers] opens on 123. */
    fun reset(numbers: Boolean = false) {
        layer = if (numbers) KeyboardLayout.Numbers else KeyboardLayout.Letters
        capsLock = false
        row = 1
        col = 4
        listening = false
        partial = ""
        notice = null
        accents = null
        lastCorrection = null
        keepWord = null
        updateAutoCap()
        refresh()
    }

    /** Back, or a press that leaves the strip: closes the accents without typing. True if it was open. */
    fun closeAccents(): Boolean {
        if (accents == null) return false
        accents = null
        sound(Cue.FocusSmall)
        return true
    }

    private fun accentNav(n: KbNav): Boolean {
        val list = accents ?: return false
        when (n) {
            KbNav.Left -> if (accentIndex > 0) { accentIndex--; sound(Cue.FocusKey) } else { sound(Cue.FocusSmall); return false }
            KbNav.Right -> if (accentIndex < list.size - 1) { accentIndex++; sound(Cue.FocusKey) } else { sound(Cue.FocusSmall); return false }
            KbNav.Select -> {
                accents = null
                editor?.let { typeChar(list[accentIndex], it) } // already in the right case
            }
            else -> closeAccents()
        }
        return true
    }

    /** CI screenshots: a given layer and key. Row -1 is the suggestion bar. */
    fun debugState(layer: Int, row: Int, col: Int, accent: Int = -1) {
        this.layer = layer.coerceIn(0, KeyboardLayout.Emoji)
        accents = null
        this.row = if (this.layer == KeyboardLayout.Emoji) row.coerceIn(0, 3) else row.coerceIn(-1, 3)
        this.col = col
        if (this.layer == KeyboardLayout.Emoji) { emojiCategory = 1; emojiIndex = if (this.row < 3) row * 10 + col else 0 }
        refresh()
        // The accent strip open on that key, with variant [accent] focused.
        if (accent >= 0 && this.row >= 0 && this.layer != KeyboardLayout.Emoji) {
            KeyboardLayout.of(this.layer).getOrNull(this.row)?.getOrNull(this.col)?.let { k ->
                Accents.of(k.label, shift || capsLock)?.let { accents = it; accentIndex = accent.coerceIn(0, it.size - 1) }
            }
        }
    }

    fun emojiList(): List<String> =
        if (emojiCategory == 0) recentEmoji.ifEmpty { EmojiData.categories[1].emoji.take(30) } else EmojiData.categories[emojiCategory].emoji

    // ----------------------------------------------------------------------------- navigation

    /** Handles a remote key. Returns false when it went nowhere (an edge), for the bump sound. */
    fun nav(n: KbNav): Boolean {
        clearOldNotice()
        if (accents != null) return accentNav(n)
        if (n == KbNav.Delete) { delete(); return true }
        if (layer == KeyboardLayout.Emoji) return emojiNav(n)
        val rows = KeyboardLayout.of(layer)
        val bar = suggestions.isNotEmpty() && !listening
        if (row == -1 && !bar) row = 0
        val moved = when (n) {
            KbNav.Left -> if (col > 0) { col--; true } else false
            KbNav.Right -> {
                val max = if (row == -1) suggestions.size - 1 else rows[row].size - 1
                if (col < max) { col++; true } else false
            }
            KbNav.Up -> when {
                row == -1 -> false
                row == 0 -> if (bar) {
                    val x = KeyboardLayout.centre(layer, 0, col)
                    row = -1
                    col = slotFor(x)
                    true
                } else false
                else -> {
                    val x = KeyboardLayout.centre(layer, row, col)
                    row--
                    col = KeyboardLayout.nearest(layer, row, x)
                    true
                }
            }
            KbNav.Down -> when {
                row == -1 -> { row = 0; col = KeyboardLayout.nearest(layer, 0, slotX(col)); true }
                row < rows.size - 1 -> {
                    val x = KeyboardLayout.centre(layer, row, col)
                    row++
                    col = KeyboardLayout.nearest(layer, row, x)
                    true
                }
                else -> false
            }
            KbNav.Select -> {
                if (row == -1) pick(col) else press(rows[row][col])
                return true
            }
            KbNav.LongSelect -> {
                if (row >= 0) longPress(rows[row][col])
                return true
            }
            KbNav.Delete -> true
        }
        sound(if (moved) Cue.FocusKey else Cue.FocusSmall)
        return moved
    }

    /** Suggestion slots sit over the left, middle and right thirds of the keys. */
    private fun slotFor(x: Float): Int = (x / (10f / 3f)).toInt().coerceIn(0, (suggestions.size - 1).coerceAtLeast(0))
    private fun slotX(slot: Int): Float = slot * (10f / 3f) + 10f / 6f

    private fun emojiNav(n: KbNav): Boolean {
        val list = emojiList()
        val bar = KeyboardLayout.emojiBar
        val inBar = row == 3 || list.isEmpty()
        val moved = when (n) {
            KbNav.Left -> if (inBar) { if (col > 0) { col--; true } else false } else if (emojiIndex % 10 > 0) { emojiIndex--; true } else false
            KbNav.Right -> if (inBar) { if (col < bar.size - 1) { col++; true } else false }
            else if (emojiIndex % 10 < 9 && emojiIndex + 1 < list.size) { emojiIndex++; true } else false
            KbNav.Up -> when {
                inBar && list.isNotEmpty() -> {
                    val lastRow = (list.size - 1) / 10
                    val r = (emojiFirstRow + 2).coerceAtMost(lastRow)
                    emojiIndex = (r * 10 + (col - 0).coerceIn(0, 9)).coerceAtMost(list.size - 1)
                    row = 0
                    true
                }
                !inBar && emojiIndex >= 10 -> { emojiIndex -= 10; true }
                else -> false
            }
            KbNav.Down -> when {
                inBar -> false
                emojiIndex + 10 < list.size -> { emojiIndex += 10; true }
                emojiIndex / 10 < (list.size - 1) / 10 -> { emojiIndex = list.size - 1; true }
                else -> { col = (emojiIndex % 10).coerceIn(0, bar.size - 1); row = 3; true }
            }
            KbNav.Select -> {
                if (inBar) press(bar[col]) else list.getOrNull(emojiIndex)?.let { typeEmoji(it) }
                return true
            }
            KbNav.LongSelect -> {
                if (inBar && bar[col].kind == KeyKind.Delete) deleteWord()
                return true
            }
            KbNav.Delete -> true
        }
        keepEmojiVisible()
        sound(if (moved) Cue.FocusKey else Cue.FocusSmall)
        return moved
    }

    private fun keepEmojiVisible() {
        val r = emojiIndex / 10
        if (r < emojiFirstRow) emojiFirstRow = r
        if (r > emojiFirstRow + 2) emojiFirstRow = r - 2
    }

    // ----------------------------------------------------------------------------- keys

    fun press(k: Key) {
        val ed = editor ?: return
        when (k.kind) {
            KeyKind.Char -> typeChar(if (shift || capsLock) k.label.uppercase() else k.label, ed)
            KeyKind.Space -> space(ed)
            KeyKind.Delete -> delete()
            KeyKind.Shift -> {
                val now = SystemClock.uptimeMillis()
                when {
                    capsLock -> { capsLock = false; shift = false }
                    shift && now - lastShiftAt < 450 -> capsLock = true
                    else -> shift = !shift
                }
                lastShiftAt = now
                sound(Cue.Toggle)
            }
            KeyKind.Layer -> switchLayer(k.target)
            KeyKind.Emoji -> switchLayer(KeyboardLayout.Emoji)
            KeyKind.Mic -> toggleDictation()
            KeyKind.EmojiCategory -> {
                emojiCategory = k.target
                emojiIndex = 0
                emojiFirstRow = 0
                sound(Cue.Toggle)
            }
            KeyKind.Done -> {
                sound(Cue.Click)
                if (autoCorrectOn) applyAutoCorrect(ed)
                lastCorrection = null
                ed.action()
                refresh()
            }
        }
    }

    private fun longPress(k: Key) {
        when (k.kind) {
            KeyKind.Delete -> deleteWord()
            KeyKind.Shift -> { capsLock = !capsLock; shift = capsLock; sound(Cue.Toggle) }
            // Hold a letter for its accents (é, ü…) in a strip above it, as on iOS: Left/Right to choose,
            // Select to type, Back to close. A key without any still types its capital.
            KeyKind.Char -> {
                val variants = Accents.of(k.label, upper = shift || capsLock)
                if (variants != null) {
                    accents = variants
                    accentIndex = 0
                    sound(Cue.Toggle)
                } else editor?.let { typeChar(k.label.uppercase(), it) }
            }
            KeyKind.Space -> editor?.let { it.commit("."); sound(Cue.Key); refresh() }
            else -> press(k)
        }
    }

    private fun switchLayer(target: Int) {
        val x = if (row >= 0 && layer != KeyboardLayout.Emoji) KeyboardLayout.centre(layer, row.coerceAtMost(3), col) else 5f
        layer = target
        if (target == KeyboardLayout.Emoji) {
            row = 3
            col = KeyboardLayout.emojiBar.indexOfFirst { it.kind == KeyKind.Layer }.coerceAtLeast(0)
            emojiCategory = if (recentEmoji.isNotEmpty()) 0 else 1
            emojiIndex = 0
            emojiFirstRow = 0
        } else {
            row = 3
            col = KeyboardLayout.nearest(target, 3, x)
            updateAutoCap()
        }
        sound(Cue.Toggle)
        refresh()
    }

    private fun typeChar(c: String, ed: KeyboardEditor) {
        val sentenceEnd = c in setOf(".", "!", "?")
        if (autoCorrectOn && c in setOf(".", ",", "!", "?", ";", ":")) ed.batch { applyAutoCorrect(ed) }
        ed.commit(c)
        lastCorrection = null
        if (shift && !capsLock) shift = false
        if (sentenceEnd) keepWord = null
        sound(Cue.Key)
        refresh()
    }

    private fun typeEmoji(e: String) {
        val ed = editor ?: return
        ed.commit(e)
        lastCorrection = null
        recentEmoji = (listOf(e) + recentEmoji.filter { it != e }).take(30)
        sp.edit().putString("recentEmoji", recentEmoji.joinToString(" ")).apply()
        sound(Cue.Key)
    }

    private fun space(ed: KeyboardEditor) {
        val before = ed.textBefore(2)
        // Double space types a full stop, as on iOS.
        if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
            ed.batch {
                ed.deleteBefore(1)
                ed.commit(". ")
            }
            lastCorrection = null
            sound(Cue.Key)
            updateAutoCap()
            refresh()
            return
        }
        ed.batch {
            val corrected = autoCorrectOn && applyAutoCorrect(ed)
            if (!corrected) lastCorrection = null
            ed.commit(" ")
        }
        learnLastPair(ed)
        sound(Cue.Key)
        updateAutoCap()
        refresh()
    }

    /** The two words before the cursor (the earlier may be null), if the text ends with a word and one space. */
    private fun wordsBeforeSpace(ed: KeyboardEditor): Pair<String?, String>? {
        val before = ed.textBefore(64)
        if (before.length < 2 || before.last() != ' ' || before[before.length - 2].isWhitespace()) return null
        val body = before.dropLast(1)
        if (!(body.last().isLetter() || body.last() == '\'')) return null // after "." or "," start afresh
        val words = body.split(' ').filter { it.isNotEmpty() }
        val last = words.lastOrNull()?.trim('\'') ?: return null
        if (last.isEmpty() || !last.all { it.isLetter() || it == '\'' }) return null
        val prev = words.getOrNull(words.size - 2)?.takeIf { w -> w.isNotEmpty() && w.all { it.isLetter() || it == '\'' } }
        return prev to last
    }

    /** What you typed after which word, for next-word predictions (not in password fields). */
    private fun learnLastPair(ed: KeyboardEditor) {
        if (!suggestionsOn) return
        val (prev, last) = wordsBeforeSpace(ed) ?: return
        if (prev != null) predictor?.learnNext(prev, last)
    }

    /** Replaces the word before the cursor with its auto-correction. Returns true if it changed. */
    private fun applyAutoCorrect(ed: KeyboardEditor): Boolean {
        val word = currentWord(ed)
        if (word.isEmpty()) return false
        if (word.equals(keepWord, ignoreCase = true)) {
            predictor?.learn(word)
            keepWord = null
            return false
        }
        val fix = predictor?.autoCorrection(word)?.let { matchCase(it, word) } ?: return false
        if (fix == word) return false
        ed.deleteBefore(word.length)
        ed.commit(fix)
        lastCorrection = word to fix
        return true
    }

    fun delete() {
        val ed = editor ?: return
        val lc = lastCorrection
        if (lc != null && ed.textBefore(lc.second.length + 1) == lc.second + " ") {
            // Delete right after an auto-correction puts back what you typed (and keeps it next time).
            ed.batch {
                ed.deleteBefore(lc.second.length + 1)
                ed.commit(lc.first)
            }
            lastCorrection = null
            keepWord = lc.first
            sound(Cue.Delete)
            refresh()
            return
        }
        val before = ed.textBefore(24)
        if (before.isEmpty()) {
            sound(Cue.FocusSmall)
            return
        }
        ed.deleteBefore(lastGraphemeLength(before))
        lastCorrection = null
        sound(Cue.Delete)
        updateAutoCap()
        refresh()
    }

    private fun deleteWord() {
        val ed = editor ?: return
        val before = ed.textBefore(64)
        if (before.isEmpty()) return
        val trimmed = before.trimEnd()
        val start = trimmed.indexOfLast { it.isWhitespace() } + 1
        ed.deleteBefore(before.length - start)
        lastCorrection = null
        sound(Cue.Clear)
        updateAutoCap()
        refresh()
    }

    // ----------------------------------------------------------------------------- suggestions

    private fun pick(slot: Int) {
        val ed = editor ?: return
        val s = suggestions.getOrNull(slot) ?: return
        val word = currentWord(ed)
        ed.batch {
            if (s.kind == Suggestion.Kind.Typed) {
                predictor?.learn(word)
                ed.commit(" ")
            } else {
                ed.deleteBefore(word.length)
                ed.commit(s.text + " ")
            }
        }
        learnLastPair(ed)
        lastCorrection = null
        keepWord = null
        sound(Cue.Key)
        updateAutoCap()
        refresh()
    }

    /** Re-reads the word being typed and updates the suggestion bar. */
    fun refresh() {
        val ed = editor
        val p = predictor
        if (ed == null || p == null || !suggestionsOn || layer == KeyboardLayout.Emoji) {
            suggestions = emptyList()
            if (row == -1) { row = 0; col = 0 }
            return
        }
        val word = currentWord(ed)
        val out = ArrayList<Suggestion>()
        if (word.isEmpty()) {
            // After a word and a space: what usually comes next ("of" → the, a, his).
            wordsBeforeSpace(ed)?.let { (prev, last) ->
                val caps = ed.capsMode()
                val loud = last.length > 1 && last.all { !it.isLetter() || it.isUpperCase() }
                p.nextWords(prev, last, 3).forEach { w ->
                    val shown = when {
                        loud -> w.uppercase()
                        caps -> w.replaceFirstChar { it.uppercase() }
                        else -> w
                    }
                    out += Suggestion(shown, Suggestion.Kind.Prediction)
                }
            }
            suggestions = out
            if (row == -1) {
                if (suggestions.isEmpty()) { row = 0; col = KeyboardLayout.nearest(layer, 0, slotX(col)) } else col = col.coerceAtMost(suggestions.size - 1)
            }
            return
        }
        if (word.any { it.isLetter() }) {
            out += Suggestion(word, Suggestion.Kind.Typed)
            val fix = if (autoCorrectOn && !word.equals(keepWord, true)) p.autoCorrection(word)?.let { matchCase(it, word) } else null
            if (fix != null && fix != word) out += Suggestion(fix, Suggestion.Kind.Correction)
            val more = (p.complete(word, 3) + if (!p.isWord(word)) p.corrections(word, 3) else emptyList())
                .map { matchCase(it, word) }
                .filter { m -> m != word && out.none { it.text == m } }
            more.forEach { if (out.size < 3) out += Suggestion(it, Suggestion.Kind.Completion) }
        }
        suggestions = if (out.size <= 1) emptyList() else out
        if (row == -1) {
            if (suggestions.isEmpty()) { row = 0; col = KeyboardLayout.nearest(layer, 0, slotX(col)) } else col = col.coerceAtMost(suggestions.size - 1)
        }
    }

    private fun currentWord(ed: KeyboardEditor): String {
        val before = ed.textBefore(48)
        var i = before.length
        while (i > 0 && (before[i - 1].isLetterOrDigit() || before[i - 1] == '\'')) i--
        return before.substring(i).trimStart('\'')
    }

    private fun matchCase(s: String, typed: String): String = when {
        s == "I" || s.startsWith("I'") -> s
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> s.uppercase()
        typed.firstOrNull()?.isUpperCase() == true -> s.replaceFirstChar { it.uppercase() }
        else -> s
    }

    fun updateAutoCap() {
        if (capsLock || layer != KeyboardLayout.Letters) return
        shift = autoCapOn && (editor?.capsMode() == true)
    }

    // ----------------------------------------------------------------------------- dictation

    fun toggleDictation() {
        if (!micOn) {
            showNotice("Dictation isn't available here")
            sound(Cue.Error)
            return
        }
        if (listening) {
            dictate(false)
        } else {
            sound(Cue.Click)
            dictate(true)
        }
    }

    /** Text from dictation, typed like iOS: a space before it if needed, a capital where one belongs. */
    fun insertDictated(text: String) {
        val ed = editor ?: return
        val clean = text.trim()
        if (clean.isEmpty()) return
        val before = ed.textBefore(1)
        val lead = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val cased = if (ed.capsMode()) clean.replaceFirstChar { it.uppercase() } else clean
        ed.commit("$lead$cased ")
        lastCorrection = null
        sound(Cue.Confirm)
        updateAutoCap()
        refresh()
    }

    fun showNotice(text: String) {
        notice = text
        noticeAt = SystemClock.uptimeMillis()
    }

    private fun clearOldNotice() {
        if (notice != null && SystemClock.uptimeMillis() - noticeAt > 2500) notice = null
    }

    companion object {
        /** Length (in chars) of the last character as you see it (an emoji can be several). */
        fun lastGraphemeLength(s: String): Int {
            if (s.isEmpty()) return 0
            val it = BreakIterator.getCharacterInstance()
            it.setText(s)
            val end = it.last()
            val start = it.previous()
            return if (start == BreakIterator.DONE) 1 else (end - start).coerceAtLeast(1)
        }
    }
}
