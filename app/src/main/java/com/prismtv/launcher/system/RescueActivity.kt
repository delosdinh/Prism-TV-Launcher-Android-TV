package com.prismtv.launcher.system

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Shown instead of the Home screen when Prism TV has crashed several times in a row (see
 * [CrashLog.isCrashLooping]), so the TV is never stuck on a launcher that dies as soon as it opens.
 * Plain Android views in their own process: nothing here touches the code that was crashing.
 */
class RescueActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (resources.displayMetrics.density * 48).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.BLACK)
        }
        fun text(s: String, size: Float, color: Int) = TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad / 3)
        }
        root.addView(text("Prism TV keeps closing", 28f, Color.WHITE))
        root.addView(
            text(
                "It stopped unexpectedly several times in a row, so it's paused here instead of trying again straight away.",
                18f, Color.LTGRAY,
            ),
        )
        CrashLog.summary(this)?.let { root.addView(text(it, 13f, Color.GRAY)) }

        val tryAgain = Button(this).apply {
            text = "Try Again"
            setOnClickListener {
                CrashLog.clearLoop(this@RescueActivity)
                runCatching {
                    startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .setClassName(packageName, "com.prismtv.launcher.MainActivity")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    )
                }
                finish()
            }
        }
        // From Android Settings you can choose another Home app, or clear Prism TV's data (Apps › Prism TV).
        val settings = Button(this).apply {
            text = "Open Android Settings"
            setOnClickListener {
                runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        root.addView(tryAgain)
        root.addView(settings)
        setContentView(root)
        tryAgain.requestFocus()
    }
}
