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
    fun lettersRunAToZThenTheKeysYouNeed() {
        val chars = letters.flatten().filter { it.kind == KeyKind.Char }.joinToString("") { it.label }
        assertEquals("abcdefghijklmnopqrstuvwxyz", chars)
        assertEquals(5, letters.size)
        assertEquals(KeyKind.Space, letters[1].last().kind) // after m
        assertEquals(KeyKind.Delete, letters[3].last().kind) // after z
        assertEquals(
            listOf(KeyKind.Shift, KeyKind.Layer, KeyKind.Emoji, KeyKind.Mic, KeyKind.Done),
            letters[4].map { it.kind },
        )
        // Every row is the keyboard's full ten units wide.
        letters.forEach { r -> assertEquals(10f, r.sumOf { it.units.toDouble() }.toFloat(), 0.001f) }
    }

    @Test
    fun nearestFindsEachKeyFromItsOwnCentre() {
        letters.forEachIndexed { r, keys ->
            keys.indices.forEach { c ->
                assertEquals("row $r col $c", c, KeyboardLayout.nearest(KeyboardLayout.Letters, r, KeyboardLayout.centre(KeyboardLayout.Letters, r, c)))
            }
        }
    }

    @Test
    fun startsOnH() {
        field.text.setLength(0)
        kb.reset()
        assertEquals("h", focused().label)
    }

    @Test
    fun spaceIsOnePressFromHAndM() {
        at("Hello. ", 1, 0) // h
        assertTrue(kb.nav(KbNav.Left)) // wraps round to space
        assertEquals(KeyKind.Space, focused().kind)
        kb.debugState(KeyboardLayout.Letters, 1, 5) // m
        assertTrue(kb.nav(KbNav.Right))
        assertEquals(KeyKind.Space, focused().kind)
    }

    @Test
    fun columnsLineUpDownTheGrid() {
        at("Hello. ", 0, 2) // c
        kb.nav(KbNav.Down); assertEquals("j", focused().label)
        kb.nav(KbNav.Down); assertEquals("p", focused().label)
        kb.nav(KbNav.Down); assertEquals("w", focused().label)
    }

    @Test
    fun upAndDownWrapThroughTheBarAndTheFunctionRow() {
        at("I am ", 0, 0) // a, with next-word predictions showing
        assertTrue(kb.suggestions.size > 1)
        kb.nav(KbNav.Up) // a -> the bar
        assertEquals(-1, kb.row)
        kb.nav(KbNav.Up) // the bar wraps round to the bottom row
        assertEquals(4, kb.row)
        kb.nav(KbNav.Down) // and back
        assertEquals(-1, kb.row)
        kb.nav(KbNav.Down)
        assertEquals(0, kb.row)
    }

    @Test
    fun typingUsesAutoCorrection() {
        at("", 3, 6) // delete
        kb.debugState(KeyboardLayout.Letters, 2, 6); kb.nav(KbNav.Select) // t
        kb.debugState(KeyboardLayout.Letters, 0, 4); kb.nav(KbNav.Select) // e
        kb.debugState(KeyboardLayout.Letters, 1, 0); kb.nav(KbNav.Select) // h
        assertEquals("teh", field.text.toString())
        kb.nav(KbNav.Left) // h -> space
        kb.nav(KbNav.Select)
        assertEquals("the ", field.text.toString())
    }

    @Test
    fun oneTwoThreeAndBackLandsOnTheSwitchKey() {
        at("", 4, 1) // 123
        kb.nav(KbNav.Select)
        assertEquals(KeyboardLayout.Numbers, kb.layer)
        assertEquals(KeyKind.Layer, KeyboardLayout.of(KeyboardLayout.Numbers)[kb.row][kb.col].kind) // ABC
        kb.nav(KbNav.Select)
        assertEquals(KeyboardLayout.Letters, kb.layer)
        assertEquals(4, kb.row)
        assertEquals("123", focused().label)
    }
}
