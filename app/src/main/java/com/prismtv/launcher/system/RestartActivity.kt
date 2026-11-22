package com.prismtv.launcher.system

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process

/**
 * Settings › Restart Launcher. Runs for a moment in its own little process: it ends Prism TV's main
 * process (so everything really starts again: apps, artwork, settings), opens Prism TV afresh, then
 * ends itself. Android brings the Power & Home Button service and Prism Keyboard back on its own.
 */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pid = intent.getIntExtra(ExtraPid, -1)
        if (pid > 0 && pid != Process.myPid()) Process.killProcess(pid)
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setClassName(packageName, "com.prismtv.launcher.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }
        finish()
        overridePendingTransition(0, 0)
        Runtime.getRuntime().exit(0)
    }

    companion object {
        private const val ExtraPid = "prism.restartPid"

        /** False if the restart screen couldn't be opened (the caller then just rebuilds its window). */
        fun restart(ctx: Context): Boolean = try {
            ctx.startActivity(
                Intent(ctx, RestartActivity::class.java)
                    .putExtra(ExtraPid, Process.myPid())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
            true
        } catch (_: Throwable) {
            false
        }
    }
}
