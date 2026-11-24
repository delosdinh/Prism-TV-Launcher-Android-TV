package com.prismtv.launcher.keyboard

import androidx.test.core.app.ApplicationProvider
import com.prismtv.launcher.ui.keyboard.WordPredictor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The bundled dictionary and next-word lists, loaded from the real resources. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WordPredictorTest {
    private val p: WordPredictor by lazy { WordPredictor.get(ApplicationProvider.getApplicationContext()) }

    @Test
    fun knowsEverydayWords() {
        assertTrue(p.isWord("the"))
        assertTrue(p.isWord("watch"))
        assertFalse(p.isWord("qzxv"))
    }

    @Test
    fun completesAPrefix() {
        assertTrue(p.complete("tel", 3).any { it.startsWith("tel") })
    }

    @Test
    fun correctsANeighbouringKeySlip() {
        // r sits next to t on the keyboard; "rhe" is in the word list (a misreading in scanned books).
        assertEquals("the", p.autoCorrection("rhe"))
        assertEquals("you", p.autoCorrection("yoy"))
        // Real words, however rare, are left alone ("mow" isn't "now").
        assertNull(p.autoCorrection("mow"))
        assertNull(p.autoCorrection("watch"))
    }

    @Test
    fun aKeptSlipIsNotCorrectedAgain() {
        assertEquals("you", p.autoCorrection("tou"))
        // Delete after the correction, then space: the word is kept as typed, and learnt.
        p.learn("tou")
        assertNull(p.autoCorrection("tou"))
    }

    @Test
    fun leavesChosenAccentsAlone() {
        assertNull(p.autoCorrection("café"))
        assertNull(p.autoCorrection("naïve"))
        // Nor offered without the accent ("café" → "caf").
        assertTrue(p.corrections("café", 3).isEmpty())
    }

    @Test
    fun predictsTheNextWord() {
        assertEquals("the", p.nextWords(null, "of", 3).first())
        assertTrue("not" in p.nextWords("I", "am", 3))
        // Usual capitals are kept.
        assertEquals("York", p.nextWords(null, "new", 3).first())
        assertTrue(p.nextWords(null, "qzxv", 3).isEmpty())
    }

    @Test
    fun learntPhrasesComeFirst() {
        p.learnNext("watch", "bluey")
        assertEquals("bluey", p.nextWords(null, "watch", 3).first())
        // Never anything that isn't a word.
        p.learnNext("watch", "a1")
        assertFalse("a1" in p.nextWords(null, "watch", 6))
    }
}
