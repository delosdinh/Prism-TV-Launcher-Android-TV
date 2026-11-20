package com.prismtv.launcher.system

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.prismtv.launcher.data.LauncherApp

/**
 * Optional accessibility service. Third-party launchers can't power off or sleep the device
 * directly; with this enabled, Control Centre can open the system power menu and the Sleep
 * Timer can put the TV to sleep. It also lets the remote's Home button open Prism TV on
 * Google TV devices that keep Home bound to their own launcher (Xiaomi remotes send
 * KEYCODE_ALL_APPS for Home). It never reads window content.
 */
class PrismAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_HOME && code != KeyEvent.KEYCODE_ALL_APPS) return false
        val enabled = getSharedPreferences("prism_prefs", Context.MODE_PRIVATE).getBoolean("homeOverride", true)
        if (!enabled) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            val home = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setClassName(packageName, "com.prismtv.launcher.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            return try {
                startActivity(home)
                true
            } catch (_: Throwable) {
                false
            }
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: PrismAccessibilityService? = null
            private set

        val isEnabled: Boolean get() = instance != null

        fun powerDialog(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) ?: false

        fun sleep(): Boolean = if (Build.VERSION.SDK_INT >= 28) {
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) ?: false
        } else {
            false
        }
    }
}

data class NetStatus(val title: String, val subtitle: String, val connected: Boolean, val ethernet: Boolean)

object SystemActions {

    fun start(ctx: Context, intent: Intent, options: Bundle? = null): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
        true
    } catch (_: Throwable) {
        false
    }

    /** Cross-fades from the launcher's zoom transition into the app's first frame. */
    fun fadeOptions(ctx: Context): Bundle? = try {
        ActivityOptions.makeCustomAnimation(ctx, android.R.anim.fade_in, android.R.anim.fade_out).toBundle()
    } catch (_: Throwable) {
        null
    }

    fun open(ctx: Context, action: String): Boolean =
        start(ctx, Intent(action)) || start(ctx, Intent(Settings.ACTION_SETTINGS))

    fun launchIntent(ctx: Context, app: LauncherApp): Intent? {
        val pm = ctx.packageManager
        val activity = app.activityName
        val intent = if (activity != null) {
            Intent(Intent.ACTION_MAIN)
                .setClassName(app.packageName, activity)
                .addCategory(if (app.isTvApp) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
        } else {
            pm.getLeanbackLaunchIntentForPackage(app.packageName) ?: pm.getLaunchIntentForPackage(app.packageName)
        }
        return intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    fun appInfo(ctx: Context, pkg: String): Boolean =
        start(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg")))

    fun uninstall(ctx: Context, pkg: String): Boolean =
        start(ctx, Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg"))) ||
            appInfo(ctx, pkg)

    fun openUri(ctx: Context, uri: String): Boolean = try {
        start(ctx, Intent.parseUri(uri, Intent.URI_INTENT_SCHEME))
    } catch (_: Throwable) {
        false
    }

    fun search(ctx: Context): Boolean =
        start(ctx, Intent("android.search.action.GLOBAL_SEARCH")) ||
            start(ctx, Intent(Intent.ACTION_ASSIST)) ||
            start(ctx, Intent(Intent.ACTION_WEB_SEARCH))

    fun killBackground(ctx: Context, pkg: String) {
        try {
            (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(pkg)
        } catch (_: Throwable) {
        }
    }

    fun network(ctx: Context): NetStatus = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> NetStatus("Wi-Fi", "Not Connected", connected = false, ethernet = false)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetStatus("Ethernet", "Connected", true, true)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetStatus("Wi-Fi", "Connected", true, false)
            else -> NetStatus("Network", "Connected", true, false)
        }
    } catch (_: Throwable) {
        NetStatus("Wi-Fi", "Unavailable", connected = false, ethernet = false)
    }

    fun dndOn(ctx: Context): Boolean = try {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    } catch (_: Throwable) {
        false
    }

    /** Returns the new state, or null when the user must first grant access. */
    fun toggleDnd(ctx: Context): Boolean? = try {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            open(ctx, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            null
        } else {
            val on = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            on
        }
    } catch (_: Throwable) {
        null
    }
}
