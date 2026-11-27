package com.prismtv.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun freshInstallNeedsACode() {
        // Downloaded and opened for the first time: nothing from an older Prism TV.
        assertFalse(Invite.earlierInstall(lastRunVersion = 0, currentVersion = 1382, updated = false, checkedForUpdates = false))
    }

    @Test
    fun newUserProfileNeedsACode() {
        // Android 11: a second user shares the app's install dates (looks updated) but has none of its files.
        assertFalse(Invite.earlierInstall(lastRunVersion = 0, currentVersion = 1382, updated = true, checkedForUpdates = false))
    }

    @Test
    fun updatedBeforeFirstOpenNeedsACode() {
        // Installed, then installed again over itself with adb before it ever ran.
        assertFalse(Invite.earlierInstall(lastRunVersion = 0, currentVersion = 1382, updated = true, checkedForUpdates = false))
    }

    @Test
    fun earlierInstallsKeepPro() {
        // Updated from 0.2.376, which ran here.
        assertTrue(Invite.earlierInstall(lastRunVersion = 1376, currentVersion = 1382, updated = true, checkedForUpdates = true))
        // Updated from a build older than lastRunVersion, which checked for updates.
        assertTrue(Invite.earlierInstall(lastRunVersion = 0, currentVersion = 1382, updated = true, checkedForUpdates = true))
    }

    @Test
    fun thisVersionHavingRunIsNotAnEarlierInstall() {
        assertFalse(Invite.earlierInstall(lastRunVersion = 1382, currentVersion = 1382, updated = false, checkedForUpdates = true))
    }
}
