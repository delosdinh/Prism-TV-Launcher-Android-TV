package com.prismtv.launcher.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogTest {
    private val min = 60_000L

    @Test
    fun threeQuickCrashesAreALoop() {
        val now = 10 * min
        assertTrue(CrashLog.isLoop(listOf(now - 90_000, now - 40_000, now - 5_000), now))
    }

    @Test
    fun fewerOrOlderCrashesAreNot() {
        val now = 10 * min
        assertFalse(CrashLog.isLoop(emptyList(), now))
        assertFalse(CrashLog.isLoop(listOf(now - 10_000, now - 5_000), now))
        // The oldest of the three is more than two minutes ago.
        assertFalse(CrashLog.isLoop(listOf(now - 3 * min, now - 40_000, now - 5_000), now))
        // Long after the crashes (the TV was left on the rescue screen, then restarted): a normal start.
        assertFalse(CrashLog.isLoop(listOf(now - 30_000, now - 20_000, now - 10_000), now + 5 * min))
    }

    @Test
    fun aClockSetBackwardsDoesNotCount() {
        val now = 10 * min
        assertFalse(CrashLog.isLoop(listOf(now + 50_000, now + 60_000, now + 70_000), now))
    }
}
