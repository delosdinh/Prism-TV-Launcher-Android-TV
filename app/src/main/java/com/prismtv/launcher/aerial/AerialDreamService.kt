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
        aerial?.start()
    }

    override fun onDreamingStopped() {
        aerial?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        aerial?.stop()
        aerial = null
        super.onDetachedFromWindow()
    }
}
