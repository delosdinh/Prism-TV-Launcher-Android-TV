package com.prismtv.launcher.baselineprofile

import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which code Prism TV runs as it starts and is used, so Android compiles exactly that ahead of
 * time (and the APK lays startup code out first). It drives Prism like a remote would: the Home Screen,
 * Control Centre, Settings and the sidebar. Run by .github/workflows/baseline-profile.yml.
 */
@RunWith(AndroidJUnit4::class)
class StartupProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.prismtv.launcher",
        includeInStartupProfile = true,
    ) {
        // Otherwise the first launch asks Android's TV listings question over Prism.
        device.executeShellCommand("pm grant $packageName android.permission.READ_TV_LISTINGS")
        pressHome()
        startActivityAndWait()
        settle(3_000)

        // Home: along the dock, into the rows and widgets, up to the Top Shelf.
        press(KeyEvent.KEYCODE_DPAD_RIGHT, 3)
        press(KeyEvent.KEYCODE_DPAD_LEFT, 3)
        press(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        press(KeyEvent.KEYCODE_DPAD_UP, 3)
        press(KeyEvent.KEYCODE_DPAD_RIGHT, 2)
        press(KeyEvent.KEYCODE_DPAD_LEFT, 2)

        // Settings, from the profile picture next to the time pill: a page and back.
        press(KeyEvent.KEYCODE_DPAD_UP, 2)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_CENTER, wait = 1_200)
        press(KeyEvent.KEYCODE_DPAD_DOWN, 4)
        press(KeyEvent.KEYCODE_DPAD_UP, 2)
        press(KeyEvent.KEYCODE_DPAD_CENTER, wait = 1_000)
        press(KeyEvent.KEYCODE_DPAD_DOWN, 5)
        press(KeyEvent.KEYCODE_BACK, wait = 800)
        press(KeyEvent.KEYCODE_BACK, wait = 1_000)

        // Control Centre (Back on the Home Screen), around its tiles, and closed again.
        press(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        press(KeyEvent.KEYCODE_BACK, wait = 1_200)
        press(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        press(KeyEvent.KEYCODE_DPAD_RIGHT, 2)
        press(KeyEvent.KEYCODE_BACK, wait = 1_000)

        // The sidebar, from the left edge of the dock.
        press(KeyEvent.KEYCODE_DPAD_LEFT, 6)
        press(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        press(KeyEvent.KEYCODE_BACK, wait = 1_000)
        press(KeyEvent.KEYCODE_DPAD_RIGHT, 2)
        settle(1_500)
    }

    private fun MacrobenchmarkScope.press(code: Int, times: Int = 1, wait: Long = 450) {
        repeat(times) {
            device.pressKeyCode(code)
            Thread.sleep(wait)
        }
    }

    private fun MacrobenchmarkScope.settle(ms: Long) {
        device.waitForIdle()
        Thread.sleep(ms)
    }
}
