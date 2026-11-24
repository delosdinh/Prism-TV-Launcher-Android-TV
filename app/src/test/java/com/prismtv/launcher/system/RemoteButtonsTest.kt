package com.prismtv.launcher.system

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteButtonsTest {
    @Test
    fun mappingsSurviveEncodeAndDecode() {
        val list = listOf(
            RemoteButtons.Mapping(KeyEvent.KEYCODE_PROG_RED, 0, "switcher", "default"),
            RemoteButtons.Mapping(KeyEvent.KEYCODE_UNKNOWN, 377, "app:com.netflix.ninja", "cc"),
        )
        assertEquals(list, RemoteButtons.decode(RemoteButtons.encode(list)))
    }

    @Test
    fun brokenLinesAreSkipped() {
        assertEquals(emptyList<RemoteButtons.Mapping>(), RemoteButtons.decode(null))
        val d = RemoteButtons.decode("x\tbad\n183\t0\t\t")
        assertEquals(1, d.size)
        assertEquals("default", d[0].press)
        assertEquals("default", d[0].hold)
    }

    @Test
    fun theDpadBackAndHomeCanNeverBeChanged() {
        for (k in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_DPAD_UP)) {
            assertFalse(RemoteButtons.canChange(k))
        }
        assertTrue(RemoteButtons.canChange(KeyEvent.KEYCODE_PROG_RED))
    }

    @Test
    fun buttonsAreNamedAsRemotesLabelThem() {
        assertEquals("Red Button", RemoteButtons.name(KeyEvent.KEYCODE_PROG_RED, 0))
        assertEquals("Button 377", RemoteButtons.name(KeyEvent.KEYCODE_UNKNOWN, 377))
        assertEquals("Netflix", RemoteButtons.label("app:x") { "Netflix" })
        assertEquals("App Switcher", RemoteButtons.label("switcher") { it })
    }
}
