package com.prismtv.launcher.system

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.prismtv.launcher.data.LauncherApp
import kotlin.coroutines.resume

/**
 * Optional accessibility service. Third-party launchers can't power off or sleep the device
 * directly; with this enabled, Control Centre can open the system power menu and the Sleep
 * Timer can put the TV to sleep. It also lets the remote's Home button open Prism TV on
 * Google TV devices that keep Home bound to their own launcher (Xiaomi remotes send
 * KEYCODE_ALL_APPS for Home). And it lets the App Switcher close apps completely: it presses Force
 * Stop on the app's page in Settings (see [ForceStopper]) — the only time it reads what's on screen.
 */
class PrismAccessibilityService : AccessibilityService() {
    private var stopperOrNull: ForceStopper? = null
    private val stopper: ForceStopper get() = stopperOrNull ?: ForceStopper(this).also { stopperOrNull = it }

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

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var bannersOrNull: BannerOverlay? = null

    // ---- Home: snapshot first, then Prism TV; a hold action if one's set.
    private var capturing = false
    private var pendingHome: Runnable? = null
    private var homeHeld = false
    private val homeHoldTimer = Runnable {
        homeHeld = true
        runAction(RemoteButtons.homeHold(this))
    }

    // ---- Changed remote buttons: the one being pressed, and whether its hold action has run.
    private var downMapping: RemoteButtons.Mapping? = null
    private var mappingHeld = false
    private val mappingHoldTimer = Runnable {
        val m = downMapping ?: return@Runnable
        mappingHeld = true
        runAction(m.hold)
    }
    /** A key Prism TV sent on itself (the usual press of a button with only a hold action). */
    private var passThroughCode = -1
    private var passThroughUntil = 0L

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (com.prismtv.launcher.BuildConfig.DEBUG) Log.d("PrismKeys", "key $code scan ${event.scanCode} action ${event.action} repeat ${event.repeatCount}")
        if (stopperOrNull?.active == true) {
            // Settings is open behind the still while an app closes: hold the remote so nothing lands
            // there. Home still works (and stops the closing).
            if (code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_ALL_APPS) stopperOrNull?.abort()
            else return true
        }
        // Settings › Remote Buttons › Add a Button: this press is the button being learned.
        RemoteButtons.learning?.let { learn ->
            if (RemoteButtons.canChange(code) || code == KeyEvent.KEYCODE_UNKNOWN) {
                if (event.action == KeyEvent.ACTION_UP) {
                    RemoteButtons.learning = null
                    val scan = event.scanCode
                    main.post { learn(code, scan) }
                }
                return true
            }
        }
        if (code == passThroughCode && android.os.SystemClock.uptimeMillis() < passThroughUntil) {
            if (event.action == KeyEvent.ACTION_UP) passThroughCode = -1
            return false
        }
        if (code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_ALL_APPS) return homeKey(event)
        return mappedKey(event)
    }

    private fun homeKey(event: KeyEvent): Boolean {
        val sp = getSharedPreferences("prism_prefs", Context.MODE_PRIVATE)
        val override = sp.getBoolean("homeOverride", true)
        // Hold Home works for remotes whose Home sends Apps (Xiaomi) too, while Home opens Prism TV.
        val holdSet = RemoteButtons.homeHold(this) != "default" && (event.keyCode == KeyEvent.KEYCODE_HOME || override)
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) {
                homeHeld = false
                captureForSwitcher()
                if (holdSet) main.postDelayed(homeHoldTimer, RemoteButtons.HoldMs)
            } else if (holdSet && !homeHeld) {
                // The remote's own repeat (or a long-press flag) says it's being held.
                main.removeCallbacks(homeHoldTimer)
                homeHoldTimer.run()
            }
            return override || holdSet
        }
        // Key up.
        main.removeCallbacks(homeHoldTimer)
        if (homeHeld) {
            homeHeld = false
            return true
        }
        if (event.isCanceled) return override || holdSet
        if (!override) {
            if (!holdSet) return false
            // Home is held back while we wait to see if it's a hold, so press it now.
            performGlobalAction(GLOBAL_ACTION_HOME)
            return true
        }
        val go = Runnable {
            pendingHome = null
            try {
                startActivity(prismHome())
            } catch (_: Throwable) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        if (capturing) {
            // The snapshot is a few frames from done: Prism TV comes up once it's taken (or soon anyway).
            pendingHome = go
            main.postDelayed({ pendingHome?.run() }, 220)
        } else go.run()
        return true
    }

    private fun prismHome(): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)
        .setClassName(packageName, "com.prismtv.launcher.MainActivity")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    /** App Switcher snapshots: what the app in front is showing, taken as you press Home. */
    private fun captureForSwitcher() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || capturing) return
        if (Foreground.prismResumed || !AppSnapshots.enabled(this)) return
        val pkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: Foreground.lastPackage
        if (!AppSnapshots.worthCapturing(this, pkg)) return
        capturing = true
        AppSnapshots.capture(this, pkg!!) {
            capturing = false
            pendingHome?.run()
        }
    }

    private fun mappedKey(event: KeyEvent): Boolean {
        val down = downMapping
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount > 0) {
                if (down == null || !down.matches(event)) return false
                if (down.hold != "default" && !mappingHeld) {
                    main.removeCallbacks(mappingHoldTimer)
                    mappingHoldTimer.run()
                }
                return true
            }
            val m = RemoteButtons.mappings(this).firstOrNull { it.matches(event) } ?: return false
            if (m.press == "default" && m.hold == "default") return false
            downMapping = m
            mappingHeld = false
            if (m.hold != "default") main.postDelayed(mappingHoldTimer, RemoteButtons.HoldMs)
            return true
        }
        if (down == null || !down.matches(event)) return false
        main.removeCallbacks(mappingHoldTimer)
        downMapping = null
        if (mappingHeld || event.isCanceled) return true
        if (down.press != "default") runAction(down.press)
        else if (!sendOn(event.keyCode)) Log.i("PrismKeys", "the usual press of ${event.keyCode} needs Shizuku or root")
        return true
    }

    /** The button's usual press, sent on for real (only possible with Shizuku, root or adb). */
    private fun sendOn(code: Int): Boolean {
        if (code == KeyEvent.KEYCODE_UNKNOWN) return false
        val ctx = applicationContext
        val root = getSharedPreferences("prism_prefs", Context.MODE_PRIVATE).getBoolean("rootClose", false)
        if (!ShellRunner.available(ctx, root)) return false
        passThroughCode = code
        passThroughUntil = android.os.SystemClock.uptimeMillis() + 3000
        Thread {
            val r = ShellRunner.run(ctx, "input keyevent $code", allowPrompt = false, rootAllowed = root)
            if (r !is LocalAdb.Result.Ok) passThroughCode = -1
        }.start()
        return true
    }

    /** Does what a changed button (or Home held) is set to do. */
    private fun runAction(action: String) {
        try {
            when {
                action == "default" -> Unit
                action == "home" -> startActivity(prismHome())
                action == "playpause" -> MediaWatcher.playPause()
                action == "sleep" -> sleepNow()
                action.startsWith("app:") -> {
                    val pkg = action.removePrefix("app:")
                    val i = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
                    i?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) }
                }
                else -> {
                    // A Prism TV action: Prism TV opens with it (snapshot first, as for Home).
                    captureForSwitcher()
                    val i = Intent(Intent.ACTION_MAIN)
                        .setClassName(packageName, "com.prismtv.launcher.MainActivity")
                        .putExtra(ExtraAction, action)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    main.postDelayed({ runCatching { startActivity(i) } }, if (capturing) 120 else 0)
                }
            }
        } catch (t: Throwable) {
            Log.i("PrismKeys", "couldn't do $action: ${t.message}")
        }
    }

    private fun sleepNow() {
        if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            stopperOrNull?.onWindowChanged(pkg)
            if (pkg != null && AppSnapshots.worthCapturing(this, pkg)) Foreground.lastPackage = pkg
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        stopperOrNull?.abort()
        bannersOrNull?.remove()
        main.removeCallbacksAndMessages(null)
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: PrismAccessibilityService? = null
            private set

        val isEnabled: Boolean get() = instance != null

        /** On, and Android has given it the (newer) ability to see the Settings screen it presses Force Stop in. */
        val canCloseApps: Boolean
            get() = instance?.let { s ->
                runCatching {
                    (s.serviceInfo.capabilities and android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT) != 0
                }.getOrDefault(false)
            } ?: false

        /** Force stops [targets] (package → name) through Settings; the packages that stopped. */
        suspend fun forceStop(targets: List<Pair<String, String>>, still: android.graphics.Bitmap?): Set<String> =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val s = instance ?: return@withContext emptySet()
                kotlinx.coroutines.suspendCancellableCoroutine<Set<String>> { cont ->
                    s.stopper.start(targets, still) { stopped -> if (cont.isActive) cont.resume(stopped) }
                }
            }

        fun powerDialog(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) ?: false

        /** The extra MainActivity reads for a remote button's Prism TV action. */
        const val ExtraAction = "prism.action"

        /** A notification banner over the app you're watching (main thread). */
        fun banner(app: String, title: String, text: String, icon: android.graphics.Bitmap?, light: Boolean): Boolean {
            val s = instance ?: return false
            val b = s.bannersOrNull ?: BannerOverlay(s).also { s.bannersOrNull = it }
            b.show(app, title, text, icon, light)
            return true
        }

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
        if (com.prismtv.launcher.data.TvInputs.isInput(app)) return com.prismtv.launcher.data.TvInputs.intent(app)
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

}
