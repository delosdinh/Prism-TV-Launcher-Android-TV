package com.prismtv.launcher.aerial

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * "Show Screensaver": aerials full screen until any button is pressed. Started by Start After on an
 * idle Home Screen ([EXTRA_AUTO]), it keeps the screen on for [AUTO_KEEP_ON_MS] and then lets the TV's
 * own screensaver/sleep timer take over, so an idle TV still goes to sleep.
 */
class AerialActivity : Activity() {
    private var aerial: AerialView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val releaseScreen = Runnable {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        aerial?.keepScreenOn = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val view = AerialView(this)
        aerial = view
        setContentView(view)
        if (intent?.getBooleanExtra(EXTRA_AUTO, false) == true) handler.postDelayed(releaseScreen, AUTO_KEEP_ON_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(releaseScreen)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        aerial?.start()
    }

    override fun onStop() {
        aerial?.stop()
        super.onStop()
        // Leaving the screensaver (Home, another app, screen off) should never resume it later.
        if (!isChangingConfigurations) finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Volume keys keep working; every other button wakes back to the Home Screen.
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE ->
                return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        return true
    }

    companion object {
        /** Started by Settings › Screensaver › Start After rather than by the user. */
        const val EXTRA_AUTO = "auto"
        const val AUTO_KEEP_ON_MS = 30L * 60 * 1000
    }
}
