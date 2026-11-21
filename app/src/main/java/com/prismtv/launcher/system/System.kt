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
        openAtStartup()
    }

    /**
     * Google TV keeps its own launcher as Home, so after a restart that's what appears first. When
     * Prism TV isn't the system's Home app, bring it up once, just after the TV starts (Android
     * connects this service during startup). Never later, so nothing you're watching gets interrupted.
     */
    private fun openAtStartup() {
        try {
            val sp = getSharedPreferences("prism_prefs", Context.MODE_PRIVATE)
            if (!sp.getBoolean("homeOverride", true) || !sp.getBoolean("openAtStartup", true)) return
            val uptime = android.os.SystemClock.elapsedRealtime()
            if (uptime > 4 * 60_000L) return
            val bootedAt = System.currentTimeMillis() - uptime
            if (kotlin.math.abs(sp.getLong("startupBootAt", 0L) - bootedAt) < 60_000L) return // once per start
            sp.edit().putLong("startupBootAt", bootedAt).apply()
            val home = packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
            )
            if (home?.activityInfo?.packageName == packageName) return // already the Home app
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .setClassName(packageName, "com.prismtv.launcher.MainActivity")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    )
                } catch (_: Throwable) {
                }
            }, 2500)
        } catch (_: Throwable) {
        }
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

    /**
     * tvOS-style launch: the app's own window zooms out of [rect] (the growing icon, in [source]'s
     * pixel coordinates), so the real app — its launch screen first — appears to open out of the tile.
     */
    fun scaleUpOptions(source: android.view.View, rect: android.graphics.Rect): Bundle? = try {
        ActivityOptions.makeScaleUpAnimation(source, rect.left, rect.top, rect.width().coerceAtLeast(1), rect.height().coerceAtLeast(1))
            .toBundle()
    } catch (_: Throwable) {
        null
    }

    fun open(ctx: Context, action: String): Boolean {
        if (action == Settings.ACTION_ACCESSIBILITY_SETTINGS) return openAccessibility(ctx)
        val intent = Intent(action)
        return (canHandle(ctx, intent) && start(ctx, intent)) || start(ctx, Intent(Settings.ACTION_SETTINGS))
    }

    /**
     * True when a real screen handles [intent]. Some TV boxes resolve settings actions to Android's
     * chooser with nothing in it ("No apps can perform this action") instead of failing, which hid the
     * fallback.
     */
    fun canHandle(ctx: Context, intent: Intent): Boolean = try {
        val ri = ctx.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        val pkg = ri?.activityInfo?.packageName
        ri != null && pkg != null && pkg != "android" && !ri.activityInfo.name.endsWith("ResolverActivity")
    } catch (_: Throwable) {
        false
    }

    /**
     * Accessibility settings. The standard action works on Google TV; the SHIELD's TV Settings doesn't
     * register it, so look for TV Settings' own accessibility screen, then fall back to Settings.
     */
    fun openAccessibility(ctx: Context): Boolean {
        val standard = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        if (canHandle(ctx, standard) && start(ctx, standard)) return true
        for (pkg in listOf("com.android.tv.settings", "com.google.android.tv.settings", "com.nvidia.tvsettings", "com.android.settings")) {
            val activities = try {
                @Suppress("DEPRECATION")
                ctx.packageManager.getPackageInfo(pkg, android.content.pm.PackageManager.GET_ACTIVITIES).activities.orEmpty()
            } catch (_: Throwable) {
                emptyArray<android.content.pm.ActivityInfo>()
            }
            val screen = activities
                .filter { it.exported && it.enabled && it.name.contains("ccessibility") && !it.name.contains("Shortcut") && !it.name.contains("Dialog") }
                .minByOrNull { it.name.length }
            if (screen != null && start(ctx, Intent().setClassName(pkg, screen.name))) return true
        }
        return start(ctx, Intent(Settings.ACTION_SETTINGS))
    }

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

    /** Where sound is going: a connected Bluetooth/USB device's name, or HDMI / TV Speakers. */
    fun audioOutput(ctx: Context): String = try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val outs = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
        val wireless = outs.firstOrNull {
            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                (Build.VERSION.SDK_INT >= 31 && it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        val usb = outs.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE || it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET }
        val hdmi = outs.firstOrNull {
            it.type == android.media.AudioDeviceInfo.TYPE_HDMI || it.type == android.media.AudioDeviceInfo.TYPE_HDMI_ARC ||
                (Build.VERSION.SDK_INT >= 30 && it.type == android.media.AudioDeviceInfo.TYPE_HDMI_EARC)
        }
        when {
            wireless != null -> wireless.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
            usb != null -> usb.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB Audio"
            hdmi?.type == android.media.AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
            hdmi != null && hdmi.type != android.media.AudioDeviceInfo.TYPE_HDMI -> "HDMI eARC"
            hdmi != null -> "HDMI"
            else -> "TV Speakers"
        }
    } catch (_: Throwable) {
        "TV Speakers"
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
