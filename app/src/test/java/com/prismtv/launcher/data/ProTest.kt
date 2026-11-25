package com.prismtv.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Without Pro, paid settings go back to their free choice and free ones are left alone. */
class ProTest {
    private val paid = LauncherPrefs(
        appearance = AppearanceSetting.Dark,
        material = GlassMaterial.Frosted,
        glassColour = GlassColour.Dark,
        uiTransparency = UiTransparency.Low,
        topShelfStyle = TopShelfStyle.Spotlight,
        titleLogos = true,
        widgets = mapOf("all:continue" to WidgetPlace.Below),
        focusEffect = FocusEffect.Lift,
        focusWobble = true,
        wallpaper = "photo:content://x/1",
        wallpaperShuffle = 60,
        aerialSource = AerialSource.Shuffle,
        aerialQuality = AerialQuality.UHD,
        aerialRange = AerialRange.DolbyVision,
        iconPack = "com.example.icons",
        customBanners = mapOf("app" to "photo:content://x/2"),
        keyboardColour = KeyboardColour.Dark,
        predictive = true,
        bannersOverApps = true,
        remoteButtons = "183\t0\tcc\tsleep\n184\t0\tsearch\tdefault",
        homeHold = "cc",
        columns = 6,
        liteMode = true,
    )

    @Test
    fun paidSettingsGoBackToFree() {
        val free = Pro.freeOnly(paid)
        assertEquals(GlassColour.Automatic, free.glassColour)
        assertEquals(UiTransparency.High, free.uiTransparency)
        assertEquals(TopShelfStyle.FullScreen, free.topShelfStyle)
        assertFalse(free.titleLogos)
        assertEquals(emptyMap<String, WidgetPlace>(), free.widgets)
        assertEquals(FocusEffect.GrowShrink, free.focusEffect)
        assertFalse(free.focusWobble)
        assertEquals("scene:aurora", free.wallpaper)
        assertEquals(0, free.wallpaperShuffle)
        assertEquals(AerialSource.Prism, free.aerialSource)
        assertEquals(AerialQuality.Auto, free.aerialQuality)
        assertEquals(AerialRange.Auto, free.aerialRange)
        assertEquals("", free.iconPack)
        assertFalse(free.predictive)
        assertFalse(free.bannersOverApps)
        assertEquals("default", free.homeHold)
        // One changed button stays, press only.
        assertEquals("183\t0\tcc\tdefault", free.remoteButtons)
    }

    @Test
    fun freeSettingsAreKept() {
        val free = Pro.freeOnly(paid)
        assertEquals(AppearanceSetting.Dark, free.appearance)
        assertEquals(GlassMaterial.Frosted, free.material)
        assertEquals(6, free.columns)
        assertEquals(true, free.liteMode)
        assertEquals("scene:dunes", Pro.freeOnly(paid.copy(wallpaper = "scene:dunes")).wallpaper)
    }

    @Test
    fun applyingTwiceChangesNothing() {
        val once = Pro.freeOnly(paid)
        assertEquals(once, Pro.freeOnly(once))
    }
}
