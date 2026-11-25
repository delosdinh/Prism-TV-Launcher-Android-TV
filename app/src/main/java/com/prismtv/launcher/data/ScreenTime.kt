package com.prismtv.launcher.data

import android.content.Context
import java.util.Calendar

/**
 * Screen Time, per profile (Settings › Profiles › Screen Time): a daily limit for apps and a downtime
 * when there are no apps. Time counts while an app (not Prism TV) is on screen and the TV is on.
 * "Ask for More Time" (with a passcode) adds time for today, or lifts downtime until it next starts.
 */
object ScreenTime {
    /** Daily limit choices, minutes (0 = no limit). */
    val Limits = listOf(0, 15, 30, 45, 60, 90, 120, 180, 240)

    /** Downtime choices ("" = none). */
    val Downtimes = listOf("", "19:00-07:00", "20:00-07:00", "21:00-07:00", "22:00-07:00", "23:00-07:00")

    enum class Block { Limit, Downtime }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("prism_screen_time", Context.MODE_PRIVATE)

    private fun today(now: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun usedToday(ctx: Context, profile: String): Long = sp(ctx).getLong("used:$profile:${today()}", 0L)

    fun add(ctx: Context, profile: String, ms: Long) {
        if (ms <= 0) return
        val key = "used:$profile:${today()}"
        val s = sp(ctx)
        val e = s.edit().putLong(key, s.getLong(key, 0L) + ms)
        // Keep only today's numbers.
        s.all.keys.filter { (it.startsWith("used:") || it.startsWith("extra:")) && !it.endsWith(today()) }.forEach { e.remove(it) }
        e.apply()
    }

    private fun extraToday(ctx: Context, profile: String): Long = sp(ctx).getLong("extra:$profile:${today()}", 0L)

    fun grant(ctx: Context, profile: String, ms: Long) {
        val key = "extra:$profile:${today()}"
        val s = sp(ctx)
        s.edit().putLong(key, (s.getLong(key, 0L) + ms).coerceAtMost(Long.MAX_VALUE / 4)).apply()
    }

    /** Downtime is off until this time (after "Ask for More Time" during downtime). */
    fun skipDowntime(ctx: Context, profile: String, until: Long) {
        sp(ctx).edit().putLong("skip:$profile", until).apply()
    }

    /** Minutes left today (null when there's no limit). */
    fun remaining(ctx: Context, p: ProfileSpec): Long? {
        if (p.limitMinutes <= 0) return null
        return (p.limitMinutes * 60_000L + extraToday(ctx, p.id) - usedToday(ctx, p.id)).coerceAtLeast(0L)
    }

    /** Start and end of downtime as minutes after midnight. */
    private fun window(downtime: String): Pair<Int, Int>? {
        val parts = downtime.split('-')
        if (parts.size != 2) return null
        fun mins(t: String): Int? {
            val hm = t.split(':')
            if (hm.size != 2) return null
            val h = hm[0].toIntOrNull() ?: return null
            val m = hm[1].toIntOrNull() ?: return null
            return h * 60 + m
        }
        val start = mins(parts[0]) ?: return null
        val end = mins(parts[1]) ?: return null
        return start to end
    }

    fun inDowntime(ctx: Context, p: ProfileSpec, now: Long = System.currentTimeMillis()): Boolean {
        val (start, end) = window(p.downtime) ?: return false
        if (now < sp(ctx).getLong("skip:${p.id}", 0L)) return false
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return if (start <= end) m in start until end else m >= start || m < end
    }

    /** When downtime next ends (for lifting it "until tomorrow"). */
    fun downtimeEnds(p: ProfileSpec, now: Long = System.currentTimeMillis()): Long {
        val (_, end) = window(p.downtime) ?: return now
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, end / 60); set(Calendar.MINUTE, end % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** Why apps can't be used right now, if they can't. */
    fun blocked(ctx: Context, p: ProfileSpec): Block? = when {
        inDowntime(ctx, p) -> Block.Downtime
        remaining(ctx, p) == 0L -> Block.Limit
        else -> null
    }

    fun limitLabel(minutes: Int): String = when {
        minutes <= 0 -> "Off"
        else -> duration(minutes * 60_000L)
    }

    fun downtimeLabel(downtime: String): String {
        val (start, end) = window(downtime) ?: return "Off"
        return "${clock(start)} – ${clock(end)}"
    }

    private fun clock(mins: Int): String {
        val h = mins / 60
        val m = mins % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        val ampm = if (h < 12) "AM" else "PM"
        return if (m == 0) "$h12 $ampm" else "$h12:%02d $ampm".format(m)
    }

    /** "45 Min", "1 Hr", "1 Hr 30 Min". */
    fun duration(ms: Long): String {
        val total = (ms / 60_000L).toInt()
        val h = total / 60
        val m = total % 60
        return when {
            h == 0 -> "$m Min"
            m == 0 -> if (h == 1) "1 Hr" else "$h Hrs"
            else -> "$h Hr $m Min"
        }
    }
}
