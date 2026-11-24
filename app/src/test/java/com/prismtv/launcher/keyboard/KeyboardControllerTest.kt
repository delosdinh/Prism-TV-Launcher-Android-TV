package com.prismtv.launcher.keyboard

import androidx.test.core.app.ApplicationProvider
import com.prismtv.launcher.ui.keyboard.KbNav
import com.prismtv.launcher.ui.keyboard.KeyboardController
import com.prismtv.launcher.ui.keyboard.KeyboardEditor
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
}
