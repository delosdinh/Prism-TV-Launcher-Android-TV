package com.prismtv.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The app and the PC script (invite-code.py) must make the same code for a TV. */
class InviteTest {
    @Test
    fun matchesTheScript() {
        // python invite-code.py ABCD-1234, with the key "testkey123"
        assertEquals("M41NA-N6BBD", Invite.codeFor("testkey123", "ABCD-1234"))
    }

    @Test
    fun idIsReadForgivingly() {
        assertEquals(Invite.codeFor("testkey123", "ABCD-1234"), Invite.codeFor("testkey123", "abcd 1234"))
        assertEquals("ABCD1234", Invite.normalise("abcd-i234"))
        assertEquals("000", Invite.normalise("ooo"))
    }

    @Test
    fun codeIsForOneTv() {
        assertNotEquals(Invite.codeFor("testkey123", "ABCD-1234"), Invite.codeFor("testkey123", "ABCD-1235"))
        assertNotEquals(Invite.codeFor("testkey123", "ABCD-1234"), Invite.codeFor("otherkey", "ABCD-1234"))
    }
}
