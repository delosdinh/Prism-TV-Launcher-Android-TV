package com.prismtv.launcher.system

import android.content.Context

/**
 * Runs a command as Android's shell user, the best way available: Shizuku (no prompt), root (once
 * allowed in Closing Apps), or Prism TV's own adb client (the TV asks once to allow debugging).
 * Blocking: call off the main thread.
 */
object ShellRunner {
    fun run(ctx: Context, command: String, allowPrompt: Boolean, rootAllowed: Boolean): LocalAdb.Result {
        // Play edition: no Shizuku, root or adb (Play's policies); you type commands yourself if you want.
        if (com.prismtv.launcher.BuildConfig.PLAY) return LocalAdb.Result.Off
        if (ShizukuShell.ready()) {
            ShizukuShell.exec(command)?.let { (exit, out) -> return LocalAdb.Result.Ok(out.trim(), exit) }
        }
        if (rootAllowed && RootShell.present()) {
            RootShell.exec(command)?.let { (exit, out) -> return LocalAdb.Result.Ok(out.trim(), exit) }
        }
        return LocalAdb.shell(ctx, command, allowPrompt)
    }

    /** True when a command can run without anything to set up first (Shizuku, root or USB debugging is on). */
    fun available(ctx: Context, rootAllowed: Boolean): Boolean = !com.prismtv.launcher.BuildConfig.PLAY && (
        ShizukuShell.ready() || (rootAllowed && RootShell.present()) || LocalAdb.enabled(ctx))
}
