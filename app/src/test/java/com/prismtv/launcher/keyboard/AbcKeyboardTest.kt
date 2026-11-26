package com.prismtv.launcher.keyboard

import androidx.test.core.app.ApplicationProvider
import com.prismtv.launcher.ui.keyboard.KbNav
import com.prismtv.launcher.ui.keyboard.KeyKind
import com.prismtv.launcher.ui.keyboard.KeyboardController
import com.prismtv.launcher.ui.keyboard.KeyboardEditor
import com.prismtv.launcher.ui.keyboard.KeyboardLayout
import com.prismtv.launcher.ui.keyboard.WordPredictor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The ABC layout (Settings › Keyboard › Keyboard Layout): A to Z in rows of seven, driven by the remote. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AbcKeyboardTest {
    private class Field : KeyboardEditor {
        val text = StringBuilder()
        override fun textBefore(n: Int) = text.takeLast(n).toString()
        override fun commit(text: String) { this.text.append(text) }
        override fun deleteBefore(n: Int) { text.setLength((text.length - n).coerceAtLeast(0)) }
        override fun capsMode() = false
        override fun action() = Unit
    }

    private val field = Field()
    private lateinit var kb: KeyboardController
    private val letters get() = KeyboardLayout.of(KeyboardLayout.Letters)

    @Before
    fun setUp() {
        KeyboardLayout.alphabetical = true
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        WordPredictor.get(ctx)
        kb = KeyboardController(ctx, sound = {}, dictate = {})
        kb.editor = field
    }

    @After
    fun tearDown() {
        KeyboardLayout.alphabetical = false
    }

    private fun at(text: String, row: Int, col: Int) {
        field.text.setLength(0)
        field.text.append(text)
        kb.reset()
        kb.debugState(KeyboardLayout.Letters, row, col)
    }

    private fun focused() = letters[kb.row][kb.col]

    @Test
    fun laidOutLikeTvOS() {
        val chars = letters.flatten().filter { it.kind == KeyKind.Char }.joinToString("") { it.label }
        assertEquals("abcdefghijklmnopqrstuvwxyz", chars)
        assertEquals(6, letters.size)
        assertEquals(listOf("abc", "123", "#+=", "emoji", "mic", "return"), letters[0].map { it.label })
        (1..4).forEach { assertEquals(6, letters[it].size) }
        assertEquals(
            listOf(KeyKind.Char, KeyKind.Char, KeyKind.Space, KeyKind.Delete, KeyKind.Clear),
            letters[5].map { it.kind },
        )
        // 123 and #+= are the same grid, with the same switches on top.
        for (layer in listOf(KeyboardLayout.Numbers, KeyboardLayout.Symbols)) {
            val rows = KeyboardLayout.of(layer)
            assertEquals(6, rows.size)
            assertEquals(letters[0].map { it.label }, rows[0].map { it.label })
            assertEquals(26, rows.flatten().count { it.kind == KeyKind.Char })
        }
        // Every row of every layer is the keyboard's full ten units wide.
        for (layer in listOf(KeyboardLayout.Letters, KeyboardLayout.Numbers, KeyboardLayout.Symbols)) {
            KeyboardLayout.of(layer).forEach { r -> assertEquals(10f, r.sumOf { it.units.toDouble() }.toFloat(), 0.001f) }
        }
    }

    @Test
    fun nearestFindsEachKeyFromItsOwnCentre() {
        for (layer in listOf(KeyboardLayout.Letters, KeyboardLayout.Numbers, KeyboardLayout.Symbols)) {
            KeyboardLayout.of(layer).forEachIndexed { r, keys ->
                keys.indices.forEach { c ->
                    assertEquals("layer $layer row $r col $c", c, KeyboardLayout.nearest(layer, r, KeyboardLayout.centre(layer, r, c)))
                }
            }
        }
    }

    @Test
    fun startsOnA() {
        field.text.setLength(0)
        kb.reset()
        assertEquals("a", focused().label)
    }

    @Test
    fun columnsLineUpDownTheGrid() {
        at("Hello. ", 1, 0) // a
        kb.nav(KbNav.Down); assertEquals("g", focused().label)
        kb.nav(KbNav.Down); assertEquals("m", focused().label)
        kb.nav(KbNav.Down); assertEquals("s", focused().label)
        kb.nav(KbNav.Down); assertEquals("y", focused().label)
        kb.debugState(KeyboardLayout.Letters, 4, 2) // u
        kb.nav(KbNav.Down); assertEquals(KeyKind.Space, focused().kind)
    }

    @Test
    fun leftAndRightWrapRoundARow() {
        at("Hello. ", 1, 0) // a
        assertTrue(kb.nav(KbNav.Left)); assertEquals("f", focused().label)
        assertTrue(kb.nav(KbNav.Right)); assertEquals("a", focused().label)
    }

    @Test
    fun upAndDownWrapThroughTheSwitchesAndTheBar() {
        at("I am ", 1, 0) // a, with next-word predictions showing
        assertTrue(kb.suggestions.size > 1)
        kb.nav(KbNav.Up) // a -> abc
        assertEquals(0, kb.row); assertEquals("abc", focused().label)
        kb.nav(KbNav.Up) // -> the bar
        assertEquals(-1, kb.row)
        kb.nav(KbNav.Up) // the bar wraps round to the bottom row
        assertEquals(5, kb.row)
        kb.nav(KbNav.Down) // and back
        assertEquals(-1, kb.row)
    }

    @Test
    fun typingUsesAutoCorrection() {
        at("", 1, 0)
        kb.debugState(KeyboardLayout.Letters, 4, 1); kb.nav(KbNav.Select) // t
        kb.debugState(KeyboardLayout.Letters, 1, 4); kb.nav(KbNav.Select) // e
        kb.debugState(KeyboardLayout.Letters, 2, 1); kb.nav(KbNav.Select) // h
        assertEquals("teh", field.text.toString())
        kb.debugState(KeyboardLayout.Letters, 5, 2); kb.nav(KbNav.Select) // space
        assertEquals("the ", field.text.toString())
    }

    @Test
    fun theSwitchesStayPutAcrossLayers() {
        at("", 0, 1) // 123
        kb.nav(KbNav.Select)
        assertEquals(KeyboardLayout.Numbers, kb.layer)
        assertEquals(0, kb.row); assertEquals(1, kb.col)
        kb.nav(KbNav.Right); kb.nav(KbNav.Select) // #+=
        assertEquals(KeyboardLayout.Symbols, kb.layer)
        assertEquals(0, kb.row); assertEquals(2, kb.col)
        kb.debugState(KeyboardLayout.Symbols, 0, 0); kb.nav(KbNav.Select) // abc
        assertEquals(KeyboardLayout.Letters, kb.layer)
        assertEquals(0, kb.row); assertEquals("abc", focused().label)
    }

    @Test
    fun abcTogglesCapitals() {
        at("x", 0, 0) // abc
        kb.nav(KbNav.Select)
        assertTrue(kb.shift)
        kb.debugState(KeyboardLayout.Letters, 1, 1); kb.nav(KbNav.Select) // B
        assertEquals("xB", field.text.toString())
        assertTrue(!kb.shift)
    }

    @Test
    fun clearEmptiesTheFieldAndDeleteBringsItBack() {
        at("hello world", 5, 4) // clear
        assertEquals(KeyKind.Clear, focused().kind)
        kb.nav(KbNav.Select)
        assertEquals("", field.text.toString())
        kb.nav(KbNav.Delete)
        assertEquals("hello world", field.text.toString())
        // Only straight after: a letter in between makes Delete delete again.
        kb.nav(KbNav.Select)
        kb.debugState(KeyboardLayout.Letters, 1, 0); kb.nav(KbNav.Select) // a
        kb.nav(KbNav.Delete)
        assertEquals("", field.text.toString())
    }

    @Test
    fun knowsTheEndOfARowForSearchResults() {
        at("Hello. ", 1, 5) // f
        assertTrue(kb.atRowEnd())
        kb.nav(KbNav.Left) // e
        assertTrue(!kb.atRowEnd())
        kb.debugState(KeyboardLayout.Letters, 5, 4) // clear
        assertTrue(kb.atRowEnd())
    }
}
