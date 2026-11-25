package com.prismtv.launcher.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Closes apps the way you would by hand: opens the app's page in Settings, presses Force Stop, then OK,
 * and comes back. A still of Prism TV covers the screen meanwhile (with a small "Closing…" pill), and the
 * remote is held so a press can't land in Settings. Only ever touches the Settings app, only those two
 * buttons, and gives up after a few seconds if the page isn't what it expects.
 */
internal class ForceStopper(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private var job: Job? = null
    private var overlay: View? = null

    val active: Boolean get() = job != null

    private enum class Phase { Open, Confirm, Verify, Return }

    private class Job(
        val queue: ArrayDeque<Pair<String, String>>,
        val count: Int,
        val done: (Set<String>) -> Unit,
    ) {
        var pkg: String = ""
        var settingsPkg: String? = null
        var phase = Phase.Open
        var phaseAt = SystemClock.uptimeMillis()
        var targetAt = SystemClock.uptimeMillis()
        var backs = 0
        var relaunched = false
        /** Settings has shown a new window since this app's page was asked for (not the last app's page). */
        var fresh = false
        var label: String = ""
        val stopped = HashSet<String>()
    }

    fun start(targets: List<Pair<String, String>>, still: Bitmap?, done: (Set<String>) -> Unit) {
        if (job != null || targets.isEmpty()) {
            done(emptySet())
            return
        }
        val j = Job(ArrayDeque(targets), targets.size, done)
        job = j
        showOverlay(still, if (targets.size == 1) "Closing ${targets[0].second}…" else "Closing ${targets.size} Apps…")
        if (!next(j)) finish(j)
        else handler.postDelayed(tick, 250)
    }

    /** A window in Settings appeared or changed. */
    fun onWindowChanged(pkg: String?) {
        val j = job ?: return
        if (pkg != null && pkg == j.settingsPkg) {
            j.fresh = true
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    /** Home pressed while closing: stop where we are (Home then does its usual thing). */
    fun abort() {
        job?.let { finish(it) }
    }

    /** Opens the next app's page in Settings; false when there's nothing (left) to close. */
    private fun next(j: Job): Boolean {
        while (j.queue.isNotEmpty()) {
            val (pkg, _) = j.queue.removeFirst()
            val settingsPkg = AppCloser.detailsPackage(service, pkg) ?: continue
            j.pkg = pkg
            j.settingsPkg = settingsPkg
            j.fresh = false
            j.label = runCatching {
                service.packageManager.getApplicationLabel(service.packageManager.getApplicationInfo(pkg, 0)).toString().trim()
            }.getOrDefault("")
            j.phase = Phase.Open
            j.phaseAt = SystemClock.uptimeMillis()
            j.targetAt = j.phaseAt
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
            if (runCatching { service.startActivity(intent) }.isSuccess) return true
        }
        return false
    }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            val j = job ?: return
            try {
                step(j)
            } catch (_: Throwable) {
                toReturn(j)
            }
            if (job === j) handler.postDelayed(this, 120)
        }
    }

    private fun step(j: Job) {
        val now = SystemClock.uptimeMillis()
        val inPhase = now - j.phaseAt
        if (j.phase != Phase.Return && now - j.targetAt > 7000) {
            log("gave up on ${j.pkg} in ${j.phase} (window: ${service.rootInActiveWindow?.packageName})")
            // This page isn't what we expect (or Settings is slow): leave the app to memory freeing.
            if (!next(j)) toReturn(j)
            return
        }
        val root = service.rootInActiveWindow
        val inSettings = root != null && root.packageName?.toString() == j.settingsPkg
        when (j.phase) {
            Phase.Open -> {
                if (!inSettings || inPhase < 250 || (!j.fresh && inPhase < 1200)) return
                // Make sure it's this app's page, not the last one's still on its way out.
                if (j.label.isNotEmpty() && inPhase < 1500 && find(root!!, listOf(j.label)) == null) return
                val node = find(root!!, forceStopLabels(j.settingsPkg!!)) ?: return
                val target = clickable(node)
                if (target == null || !node.isEnabled || !target.isEnabled) {
                    // Force Stop is greyed out: the app isn't running (or Android protects it).
                    log("Force Stop is greyed out for ${j.pkg}")
                    if (AppCloser.isStopped(service, j.pkg)) j.stopped += j.pkg
                    advance(j)
                } else if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    log("pressed Force Stop for ${j.pkg} after ${inPhase}ms")
                    j.phase = Phase.Confirm
                    j.phaseAt = now
                }
            }
            Phase.Confirm -> {
                val ok = if (inSettings) find(root!!, okLabels(j.settingsPkg!!)) else null
                val target = ok?.let { clickable(it) }
                if (target != null && target.isEnabled && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    log("pressed OK after ${inPhase}ms")
                    j.phase = Phase.Verify
                    j.phaseAt = now
                } else if (inPhase > 1800) {
                    // Some Settings stop the app without asking.
                    if (AppCloser.isStopped(service, j.pkg)) j.stopped += j.pkg
                    advance(j)
                }
            }
            Phase.Verify -> {
                if (AppCloser.isStopped(service, j.pkg) || inPhase > 700) {
                    j.stopped += j.pkg // OK was pressed on Force Stop's own confirmation
                    advance(j)
                }
            }
            Phase.Return -> {
                val pkg = root?.packageName?.toString()
                // Back is pressed once, and only while Settings is in front: a second press could land on
                // Prism TV once Settings has gone (closing the App Switcher). If Settings is still there
                // after that, Prism TV is brought back over it instead.
                when {
                    pkg != null && pkg != j.settingsPkg -> {
                        log("back in $pkg after ${inPhase}ms")
                        finish(j)
                    }
                    inPhase > 3200 -> finish(j)
                    pkg == j.settingsPkg && j.backs == 0 -> {
                        j.backs = 1
                        log("back")
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    }
                    pkg == j.settingsPkg && inPhase > 1800 && !j.relaunched -> {
                        j.relaunched = true
                        log("Settings still open: bringing Prism TV back")
                        bringBackPrism()
                    }
                }
            }
        }
    }

    private fun advance(j: Job) {
        if (!next(j)) toReturn(j)
    }

    private fun toReturn(j: Job) {
        if (j.phase == Phase.Return) return
        j.phase = Phase.Return
        j.phaseAt = SystemClock.uptimeMillis()
        j.backs = 0
    }

    private fun bringBackPrism() {
        runCatching {
            service.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .setClassName(service.packageName, "com.prismtv.launcher.MainActivity")
                    .putExtra(ExtraReturning, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        }
    }

    private fun log(msg: String) {
        android.util.Log.i("PrismClose", msg)
    }

    private fun finish(j: Job) {
        if (job !== j) return
        log("done: ${j.stopped}")
        job = null
        handler.removeCallbacks(tick)
        hideOverlay()
        j.done(j.stopped.toSet())
    }

    // ------------------------------------------------------------------ finding the buttons

    private fun find(root: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        for (label in labels) {
            val hits = runCatching { root.findAccessibilityNodeInfosByText(label) }.getOrNull() ?: continue
            hits.firstOrNull { n ->
                val text = (n.text ?: n.contentDescription)?.toString()?.trim()
                text != null && text.equals(label, ignoreCase = true) && n.isVisibleToUser
            }?.let { return it }
        }
        return null
    }

    private fun clickable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var c: AccessibilityNodeInfo? = n
        var depth = 0
        while (c != null && depth < 6) {
            if (c.isClickable) return c
            c = c.parent
            depth++
        }
        return null
    }

    private val labelCache = HashMap<String, List<String>>()

    private fun forceStopLabels(settingsPkg: String) = labelCache.getOrPut("stop:$settingsPkg") {
        labels(settingsPkg, listOf("force_stop", "device_apps_app_management_force_stop", "app_force_stop", "force_stop_button"), listOf("Force stop", "Force Stop"))
    }

    private fun okLabels(settingsPkg: String) = labelCache.getOrPut("ok:$settingsPkg") {
        val system = runCatching { Resources.getSystem().getString(android.R.string.ok) }.getOrNull()
        labels(settingsPkg, listOf("settings_ok", "dlg_ok", "okay", "ok", "force_stop_dlg_ok", "device_apps_app_management_force_stop_ok"), listOfNotNull(system, "OK", "Ok"))
    }

    /** The button's words in the TV's language, from Settings' own strings, then English. */
    private fun labels(settingsPkg: String, names: List<String>, english: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        val res = runCatching { service.packageManager.getResourcesForApplication(settingsPkg) }.getOrNull()
        if (res != null) {
            for (name in names) {
                val id = res.getIdentifier(name, "string", settingsPkg)
                if (id != 0) runCatching { res.getString(id).trim() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let(out::add)
            }
        }
        out += english
        return out.toList()
    }

    // ------------------------------------------------------------------ the still

    private fun showOverlay(still: Bitmap?, text: String) {
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val ctx: Context = service
        fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()
        val root = FrameLayout(ctx)
        if (still != null) {
            root.addView(
                ImageView(ctx).apply {
                    setImageBitmap(still)
                    scaleType = ImageView.ScaleType.FIT_XY
                },
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
        } else {
            root.setBackgroundColor(Color.rgb(16, 16, 20))
        }
        val pill = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18f), dp(10f), dp(22f), dp(10f))
            background = GradientDrawable().apply {
                cornerRadius = dp(22f).toFloat()
                setColor(Color.argb(214, 28, 28, 32))
                setStroke(dp(1f), Color.argb(46, 255, 255, 255))
            }
            addView(
                ProgressBar(ctx).apply {
                    isIndeterminate = true
                    indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                },
                LinearLayout.LayoutParams(dp(18f), dp(18f)).apply { marginEnd = dp(12f) },
            )
            addView(
                TextView(ctx).apply {
                    this.text = text
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                },
            )
        }
        root.addView(
            pill,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(40f)
            },
        )
        pill.alpha = 0f
        pill.animate().alpha(1f).setStartDelay(250).setDuration(200).start()
        @Suppress("DEPRECATION")
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT,
        )
        lp.windowAnimations = 0
        if (runCatching { wm.addView(root, lp) }.isSuccess) overlay = root
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        overlay = null
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // Prism TV is drawn again underneath by now; fade the still away so nothing jumps.
        v.animate().alpha(0f).setDuration(160).withEndAction { runCatching { wm.removeView(v) } }.start()
    }

    companion object {
        /** MainActivity ignores this "return" launch (it isn't a Home press). */
        const val ExtraReturning = "prism.closingReturn"
    }
}
