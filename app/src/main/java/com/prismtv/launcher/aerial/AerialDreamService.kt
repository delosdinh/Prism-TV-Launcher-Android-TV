package com.prismtv.launcher.aerial

import android.service.dreams.DreamService

/** Prism Aerials as the system screensaver (Settings › Display & Sound › Screen saver). */
class AerialDreamService : DreamService() {
    private var aerial: AerialView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val view = AerialView(this)
        aerial = view
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        com.prismtv.launcher.system.Foreground.screensaver = true
        aerial?.start()
    }

    override fun onDreamingStopped() {
        com.prismtv.launcher.system.Foreground.screensaver = false
        aerial?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        aerial?.stop()
        aerial = null
        super.onDetachedFromWindow()
    }
}
