package com.prismtv.launcher.keyboard

import androidx.test.core.app.ApplicationProvider
import com.prismtv.launcher.ui.keyboard.KbNav
import com.prismtv.launcher.ui.keyboard.KeyboardController
import com.prismtv.launcher.ui.keyboard.KeyboardEditor
import com.prismtv.launcher.ui.keyboard.KeyKind
import com.prismtv.launcher.ui.keyboard.KeyboardLayout
import com.prismtv.launcher.ui.keyboard.Suggestion
import com.prismtv.launcher.ui.keyboard.WordPredictor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Prism Keyboard driven as the remote would, typing into a plain text buffer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardControllerTest {
    private class Field : KeyboardEditor {
        val text = StringBuilder()
        override fun textBefore(n: Int) = text.takeLast(n).toString()
        override fun commit(text: String) { this.text.append(text) }
        override fun deleteBefore(n: Int) { text.setLength((text.length - n).coerceAtLeast(0)) }
        override fun capsMode() = text.isEmpty() || text.trimEnd().endsWith('.')
        override fun action() = Unit
    }

    private val field = Field()
    private lateinit var kb: KeyboardController

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        WordPredictor.get(ctx)
        kb = KeyboardController(ctx, sound = {}, dictate = {})
        kb.editor = field
    }

    /** Puts [text] in the field and focuses [row]/[col] of the letters. */
    private fun at(text: String, row: Int, col: Int) {
        field.text.setLength(0)
        field.text.append(text)
        kb.reset()
        kb.debugState(KeyboardLayout.Letters, row, col)
    }

    @Test
    fun holdingALetterOffersItsAccents() {
        at("caf", 0, 2) // e
        kb.nav(KbNav.LongSelect)
        assertEquals(listOf("è", "é", "ê", "ë", "ē", "ė", "ę"), kb.accents)
        kb.nav(KbNav.Right)
        kb.nav(KbNav.Select)
        assertEquals("café", field.text.toString())
        assertNull(kb.accents)
    }

    @Test
    fun backClosesTheAccentsWithoutTyping() {
        at("caf", 0, 2)
        kb.nav(KbNav.LongSelect)
        assertTrue(kb.closeAccents())
        assertNull(kb.accents)
        assertEquals("caf", field.text.toString())
    }

    @Test
    fun aLetterWithoutAccentsStillTypesItsCapital() {
        at("x", 0, 0) // q
        kb.nav(KbNav.LongSelect)
        assertNull(kb.accents)
        assertEquals("xQ", field.text.toString())
    }

    @Test
    fun predictsTheNextWordAfterASpace() {
        at("I am ", 1, 4)
        val s = kb.suggestions
        assertTrue(s.isNotEmpty())
        assertTrue(s.all { it.kind == Suggestion.Kind.Prediction })
        assertTrue(s.any { it.text == "not" })
    }

    @Test
    fun noPredictionsAfterAFullStop() {
        at("Hello. ", 1, 4)
        assertTrue(kb.suggestions.isEmpty())
    }

    @Test
    fun typingSuggestsCompletions() {
        at("tel", 1, 4)
        assertNotNull(kb.suggestions.firstOrNull { it.text.startsWith("tel") })
    }

    // ------------------------------------------------------------------ wrap-around navigation

    @Test
    fun leftAndRightWrapRoundARow() {
        at("Hello. ", 0, 0) // q, no suggestion bar
        assertTrue(kb.nav(KbNav.Left))
        assertEquals(0, kb.row); assertEquals(9, kb.col) // p
        assertTrue(kb.nav(KbNav.Right))
        assertEquals(0, kb.col) // back to q
        kb.debugState(KeyboardLayout.Letters, 1, 8) // l, the end of the second row
        assertTrue(kb.nav(KbNav.Right))
        assertEquals(0, kb.col) // a
    }

    @Test
    fun upAndDownWrapBetweenTopAndBottomRows() {
        at("Hello. ", 0, 4) // t
        assertTrue(kb.suggestions.isEmpty())
        assertTrue(kb.nav(KbNav.Up))
        assertEquals(3, kb.row)
        assertEquals(KeyKind.Space, KeyboardLayout.of(KeyboardLayout.Letters)[3][kb.col].kind) // t sits over space
        assertTrue(kb.nav(KbNav.Down))
        assertEquals(0, kb.row)
        assertEquals(4, kb.col) // back to t
    }

    @Test
    fun theSuggestionBarWrapsToo() {
        at("I am ", 0, 0)
        val n = kb.suggestions.size
        assertTrue(n > 1)
        kb.nav(KbNav.Up) // q -> the bar's left slot
        assertEquals(-1, kb.row); assertEquals(0, kb.col)
        assertTrue(kb.nav(KbNav.Left))
        assertEquals(-1, kb.row); assertEquals(n - 1, kb.col)
        assertTrue(kb.nav(KbNav.Right))
        assertEquals(0, kb.col)
        // Up from the bar wraps to the bottom row; Down from the bottom row comes back to the bar.
        assertTrue(kb.nav(KbNav.Up))
        assertEquals(3, kb.row)
        assertTrue(kb.nav(KbNav.Down))
        assertEquals(-1, kb.row)
    }

    @Test
    fun searchKeepsUpAtTheTopForItsResults() {
        at("Hello. ", 0, 4)
        assertTrue(!kb.nav(KbNav.Up, wrapUp = false))
        assertEquals(0, kb.row)
    }

    @Test
    fun theAccentStripWraps() {
        at("caf", 0, 2) // e: 7 accents
        kb.nav(KbNav.LongSelect)
        assertTrue(kb.nav(KbNav.Left))
        assertEquals(6, kb.accentIndex)
        assertTrue(kb.nav(KbNav.Right))
        assertEquals(0, kb.accentIndex)
    }

    @Test
    fun theEmojiGridAndBarWrap() {
        at("", 0, 0)
        kb.debugState(KeyboardLayout.Emoji, 0, 0)
        assertTrue(kb.nav(KbNav.Left))
        assertEquals(9, kb.emojiIndex) // end of the first emoji row
        assertTrue(kb.nav(KbNav.Up)) // top of the grid -> the bar
        assertEquals(3, kb.row)
        kb.debugState(KeyboardLayout.Emoji, 3, 0)
        assertTrue(kb.nav(KbNav.Left))
        assertEquals(KeyboardLayout.emojiBar.size - 1, kb.col)
        assertTrue(kb.nav(KbNav.Down)) // the bar -> top of the grid
        assertEquals(0, kb.row)
    }
}
