package com.prismtv.launcher.system

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A notification shown in the time pill's badge and listed in Control Centre. */
data class PrismNotification(
    val key: String,
    val pkg: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val icon: ImageBitmap?,
    val intent: PendingIntent?,
    val autoCancel: Boolean,
)

/**
 * Reads the notifications other apps post (needs Notification access for Prism TV, granted once in
 * Android's settings or with `adb shell cmd notification allow_listener …`). Only ordinary,
 * dismissible notifications are kept: no media sessions, downloads or "running" services.
 */
class PrismNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        publish()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        state.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        publish()
        if (sbn != null) maybeBanner(sbn)
    }

    /** Posted times of notifications already shown as a banner, so updates don't show again. */
    private val bannered = LinkedHashMap<String, Long>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Banners Over Apps: a new notification slides in over the app you're watching (Prism TV shows
     * its own in the time pill). Only new, ordinary notifications that would make a sound or show on
     * a phone: no quiet ones, media, progress or updates to one already shown.
     */
    private fun maybeBanner(sbn: StatusBarNotification) {
        if (Foreground.prismResumed || !PrismAccessibilityService.isEnabled) return
        val sp = getSharedPreferences("prism_prefs", Context.MODE_PRIVATE)
        if (!sp.getBoolean(BannerOverlay.PrefKey, false) || !useful(sbn)) return
        if (System.currentTimeMillis() - sbn.postTime > 15_000L) return
        if (bannered[sbn.key] == sbn.postTime) return
        val ranking = NotificationListenerService.Ranking()
        if (runCatching { currentRanking?.getRanking(sbn.key, ranking) }.getOrNull() == true &&
            ranking.importance in 0 until android.app.NotificationManager.IMPORTANCE_DEFAULT
        ) return
        bannered[sbn.key] = sbn.postTime
        if (bannered.size > 40) bannered.remove(bannered.keys.first())
        val light = sp.getString("appearance", null) == "Light"
        worker.post {
            val e = entry(sbn) ?: return@post
            val icon = runCatching { e.icon?.asAndroidBitmap() }.getOrNull()
            main.post { PrismAccessibilityService.banner(e.appLabel, e.title, e.text, icon, light) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    /**
     * Rebuilds the list off the main thread (these callbacks arrive on it, sometimes in bursts), with
     * each app's name and icon read once and kept.
     */
    private fun publish() {
        worker.removeCallbacksAndMessages(null)
        worker.post {
            val active = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()
            state.value = active
                .filter { useful(it) }
                .sortedByDescending { it.postTime }
                .take(20)
                .mapNotNull { entry(it) }
        }
    }

    private class AppLook(val label: String, val icon: ImageBitmap?)

    private fun look(pkg: String): AppLook = synchronized(looks) {
        looks[pkg] ?: run {
            val pm = packageManager
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            val icon = runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            AppLook(label, icon).also {
                if (looks.size > 24) looks.remove(looks.keys.first())
                looks[pkg] = it
            }
        }
    }

    private val looks = LinkedHashMap<String, AppLook>()

    private fun useful(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return false
        if (sbn.packageName == packageName || sbn.isOngoing) return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return false
        val skip = setOf(
            Notification.CATEGORY_SERVICE, Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SYSTEM, Notification.CATEGORY_STATUS,
        )
        return n.category !in skip
    }

    private fun entry(sbn: StatusBarNotification): PrismNotification? {
        val n = sbn.notification
        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras?.getCharSequence(Notification.EXTRA_TEXT) ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val app = look(sbn.packageName)
        val label = app.label
        val icon = app.icon
        return PrismNotification(
            key = sbn.key,
            pkg = sbn.packageName,
            appLabel = label,
            title = title.ifBlank { label },
            text = text,
            postTime = sbn.postTime,
            icon = icon,
            intent = n.contentIntent,
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
        )
    }

    companion object {
        @Volatile
        private var instance: PrismNotificationListener? = null

        private val state = MutableStateFlow<List<PrismNotification>>(emptyList())
        val notifications: StateFlow<List<PrismNotification>> = state

        fun isEnabled(ctx: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

        fun component(ctx: Context) = ComponentName(ctx, PrismNotificationListener::class.java)

        /** Android's Notification access page (missing on some Google TV builds). */
        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Opens what the notification points at (or its app), then clears it if it's meant to go. */
        fun open(ctx: Context, n: PrismNotification): Boolean {
            val pi = n.intent
            val sent = pi != null && runCatching {
                if (Build.VERSION.SDK_INT >= 34) {
                    val opts = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    pi.send(ctx, 0, null, null, null, null, opts.toBundle())
                } else {
                    pi.send()
                }
                true
            }.getOrDefault(false)
            val opened = sent || run {
                val launch = ctx.packageManager.getLeanbackLaunchIntentForPackage(n.pkg)
                    ?: ctx.packageManager.getLaunchIntentForPackage(n.pkg)
                launch != null && SystemActions.start(ctx, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            if (opened && n.autoCancel) dismiss(n)
            return opened
        }

        fun dismiss(n: PrismNotification) {
            runCatching { instance?.cancelNotification(n.key) }
            state.value = state.value.filterNot { it.key == n.key }
        }

        /** Control Centre › Clear All: every notification Prism TV lists. */
        fun dismissAll(list: List<PrismNotification>) {
            if (list.isEmpty()) return
            runCatching { instance?.cancelNotifications(list.map { it.key }.toTypedArray()) }
            val keys = list.map { it.key }.toSet()
            state.value = state.value.filterNot { it.key in keys }
        }

        private val worker: android.os.Handler by lazy {
            android.os.Handler(android.os.HandlerThread("prism-notifications").apply { start() }.looper)
        }

        /** CI / demo: pretend these were posted. */
        fun demo(list: List<PrismNotification>) {
            state.value = list
        }
    }
}
