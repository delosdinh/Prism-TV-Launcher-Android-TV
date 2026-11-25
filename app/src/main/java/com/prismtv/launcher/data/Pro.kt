package com.prismtv.launcher.data

import android.content.Context
import com.prismtv.launcher.BuildConfig

/**
 * Prism Pro: the one-time unlock sold through Google Play (Play edition). The GitHub edition, for you
 * and friends, has Pro on always. A new Play install gets [TrialDays] days of Pro first.
 *
 * What's Pro follows the pricing plan: wallpapers beyond Prism's own, Card Spotlight and Title Logos,
 * Widgets, Lift & Shine and Focus Wobble, choosing Material Colour by hand, transparency levels, icon
 * packs and banners, keyboard colours, predictions and dictation, more profiles, Restrictions and Screen
 * Time, Banners Over Apps, more than one Remote Button and hold actions, and the screensaver's Shuffle,
 * 4K and Dolby Vision. Colour Mode and Material Colour › Automatic stay free.
 *
 * Kept in its own preferences file, so Reset Launcher (and restoring a backup) leave it alone.
 */
object Pro {
    /** The in-app product's ID in Play Console (Monetise › Products › In-app products). */
    const val ProductId = "prism_pro"
    const val TrialDays = 7
    private const val DayMs = 24L * 60 * 60 * 1000

    /** The GitHub edition: everything unlocked. */
    val alwaysOn: Boolean get() = !BuildConfig.PLAY

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("prism_pro", Context.MODE_PRIVATE)

    /** Bought (as Google Play last told us; remembered for when Play can't be reached). */
    fun owned(ctx: Context): Boolean = sp(ctx).getBoolean("owned", false)

    fun setOwned(ctx: Context, owned: Boolean) {
        if (owned(ctx) != owned) sp(ctx).edit().putBoolean("owned", owned).apply()
    }

    /** Days of the free trial left (0 once it's over). The trial starts the first time Prism TV runs. */
    fun trialDaysLeft(ctx: Context): Int {
        val sp = sp(ctx)
        val now = System.currentTimeMillis()
        var start = sp.getLong("trialStart", 0L)
        if (start == 0L || start > now) {
            start = now
            sp.edit().putLong("trialStart", now).apply()
        }
        val left = start + TrialDays * DayMs - now
        return if (left <= 0) 0 else ((left + DayMs - 1) / DayMs).toInt()
    }

    /** Debug builds only (CI screenshots): "trial" (5 days left), "ended" or "owned". */
    fun debugSet(ctx: Context, state: String) {
        if (!BuildConfig.DEBUG) return
        val now = System.currentTimeMillis()
        val start = when (state) {
            "ended" -> now - (TrialDays + 1) * DayMs
            else -> now - 2 * DayMs - 60_000
        }
        sp(ctx).edit().putLong("trialStart", start).putBoolean("owned", state == "owned").apply()
    }

    fun active(ctx: Context): Boolean = alwaysOn || owned(ctx) || trialDaysLeft(ctx) > 0

    /**
     * Settings as they are without Pro: what was set with Pro goes back to the free choice, so nothing
     * paid keeps working once a trial ends (or a purchase is refunded). Free settings are untouched.
     */
    fun freeOnly(p: LauncherPrefs): LauncherPrefs {
        val rb = com.prismtv.launcher.system.RemoteButtons
        return p.copy(
            glassColour = GlassColour.Automatic,
            uiTransparency = UiTransparency.High,
            topShelfStyle = TopShelfStyle.FullScreen,
            titleLogos = false,
            widgets = emptyMap(),
            focusEffect = if (p.focusEffect == FocusEffect.Lift) FocusEffect.GrowShrink else p.focusEffect,
            focusWobble = false,
            // Prism's own wallpapers are free; your pictures and the aerials are Pro.
            wallpaper = if (p.wallpaper.startsWith("scene:")) p.wallpaper else DefaultWallpaper,
            wallpaperShuffle = 0,
            aerialSource = if (p.aerialSource == AerialSource.Shuffle) AerialSource.Prism else p.aerialSource,
            aerialQuality = if (p.aerialQuality == AerialQuality.UHD) AerialQuality.Auto else p.aerialQuality,
            aerialRange = if (p.aerialRange == AerialRange.DolbyVision) AerialRange.Auto else p.aerialRange,
            iconPack = "",
            customBanners = emptyMap(),
            keyboardColour = KeyboardColour.Automatic,
            keyboardFieldColour = KeyboardColour.Clear,
            predictive = false,
            bannersOverApps = false,
            appSnapshots = false,
            // One changed button, press only. Profiles, Restrictions and Screen Time limits already set are
            // kept and keep working (a child's limits never lift by themselves); only adding more is Pro.
            remoteButtons = rb.encode(rb.decode(p.remoteButtons).take(FreeRemoteButtons).map { it.copy(hold = "default") }),
            homeHold = "default",
        )
    }

    /** Remote Buttons without Pro: this many changed buttons, press only. */
    const val FreeRemoteButtons = 1

    /**
     * Play edition, Pro or not: Apple's aerials aren't offered there, so a screensaver or wallpaper set to
     * them (the defaults, or a restored backup) uses Prism's own instead.
     */
    fun playOnly(p: LauncherPrefs): LauncherPrefs = p.copy(
        aerialSource = if (p.aerialSource == AerialSource.Apple) AerialSource.Prism else p.aerialSource,
        wallpaper = if (p.wallpaper == "aerial") p.lastStillWallpaper.ifEmpty { DefaultWallpaper } else p.wallpaper,
        aerialQuality = AerialQuality.Auto,
        aerialRange = AerialRange.Auto,
        appSnapshots = false,
    )

    private const val DefaultWallpaper = "scene:aurora"
}
