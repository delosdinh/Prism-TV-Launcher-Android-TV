package com.prismtv.launcher.keyboard

import com.prismtv.launcher.ui.keyboard.Accents
import com.prismtv.launcher.ui.keyboard.KeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {
    @Test
    fun accentsFollowTheShiftState() {
        assertEquals(listOf("è", "é", "ê", "ë", "ē", "ė", "ę"), Accents.of("e", upper = false))
        assertEquals(listOf("È", "É", "Ê", "Ë", "Ē", "Ė", "Ę"), Accents.of("E", upper = true))
        // ß has no everyday capital: it stays as it is.
        assertTrue("ß" in Accents.of("s", upper = true)!!)
    }

    @Test
    fun keysWithoutAccentsHaveNone() {
        for (c in "bdfghjkmpqrtvwx") assertNull("$c", Accents.of(c.toString(), upper = false))
        assertNull(Accents.of("space", upper = false))
    }

    @Test
    fun punctuationHasItsRelatives() {
        assertEquals(listOf("…"), Accents.of(".", upper = false))
        assertEquals(listOf("¿"), Accents.of("?", upper = true))
        assertEquals(listOf("\\"), Accents.of("/", upper = false))
        assertTrue("€" in Accents.of("$", upper = false)!!)
    }

    @Test
    fun everyVariantIsOneCharacter() {
        val labels = ('a'..'z').map { it.toString() } + listOf(".", "?", "!", "'", "\"", "-", "$", "&", "0", "%", "/")
        for (l in labels) for (upper in listOf(false, true)) {
            Accents.of(l, upper)?.forEach { v -> assertEquals("$l → $v", 1, v.codePointCount(0, v.length)) }
        }
    }

    @Test
    fun nearestFindsEachKeyFromItsOwnCentre() {
        for (layer in listOf(KeyboardLayout.Letters, KeyboardLayout.Numbers, KeyboardLayout.Symbols)) {
            val rows = KeyboardLayout.of(layer)
            assertFalse(rows.isEmpty())
            rows.forEachIndexed { r, keys ->
                assertFalse("layer $layer row $r", keys.isEmpty())
                keys.indices.forEach { c ->
                    assertEquals("layer $layer row $r col $c", c, KeyboardLayout.nearest(layer, r, KeyboardLayout.centre(layer, r, c)))
                }
            }
        }
    }
}
