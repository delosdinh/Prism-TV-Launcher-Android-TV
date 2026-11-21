package com.prismtv.launcher.data

import android.content.Context

enum class AppearanceSetting(val label: String) {
    Light("Light"), Balanced("Balanced"), Dark("Dark"), Automatic("Automatic")
}

/** Colour of the Home Screen glass (dock, sidebar, Control Centre, menus, folders). */
enum class GlassColour(val label: String) {
    /** Follows Appearance (the Settings and pop-up colour). */
    Automatic("Automatic"), Light("Light"), Balanced("Balanced"), Dark("Dark")
}

enum class GlassMaterial(val label: String) {
    /** Liquid Glass, tinted: the lens with a gentle veil so text stays legible (iOS "Tinted"). */
    Liquid("Liquid Glass"),
    /** Liquid Glass, clear: the most transparent lens, no veil (iOS "Clear"). */
    Clear("Clear Glass"),
    Frosted("Frosted Glass"),
}

/** Liquid Glass look, as in iOS 26 (Settings › Display & Brightness › Liquid Glass). */
enum class LiquidTint(val label: String) { Clear("Clear"), Tinted("Tinted") }

/** How the Top Shelf shows the focused app's titles. */
enum class TopShelfStyle(val label: String) { FullScreen("Full Screen"), Spotlight("Card Spotlight") }

/** Colour of Prism TV's on-screen keyboard; Automatic follows Appearance. */
enum class KeyboardColour(val label: String) { Automatic("Automatic"), Light("Light"), Balanced("Balanced"), Dark("Dark") }

/** Where a widget row sits on the Home Screen. */
enum class WidgetPlace(val label: String) { Off("Off"), Above("Above Dock"), Below("Below Dock") }

/** Settings › Appearance › Text Size steps; index [DefaultTextSize] is the standard size. */
val TextScales = listOf(0.9f, 1f, 1.05f, 1.1f, 1.15f, 1.2f, 1.27f, 1.33f, 1.4f)
const val DefaultTextSize = 1

enum class FocusEffect(val label: String) {
    Grow("Grow"), GrowShrink("Grow & Shrink"), Lift("Lift & Shine")
}

enum class LabelMode(val label: String) {
    Focused("Focused Only"), Always("Always"), Never("Never")
}

enum class AerialSource(val label: String) {
    Apple("Apple TV Aerials"), Prism("Prism Scenes")
}

enum class AerialQuality(val label: String) {
    Auto("Automatic"), HD("1080p"), UHD("4K")
}

enum class TemperatureUnit(val label: String) {
    Auto("Automatic"), Celsius("Celsius"), Fahrenheit("Fahrenheit")
}

/** Apple publishes its HDR aerials in Dolby Vision (profile 5) only, so HDR here means Dolby Vision. */
enum class AerialRange(val label: String) {
    Auto("Automatic"), SDR("SDR"), DolbyVision("Dolby Vision")
}

enum class HeroSource(val label: String) {
    Recommendations("Recommendations"), AppArtwork("App Artwork")
}

data class LauncherPrefs(
    val appearance: AppearanceSetting = AppearanceSetting.Dark,
    val material: GlassMaterial = GlassMaterial.Liquid,
    val focusEffect: FocusEffect = FocusEffect.GrowShrink,
    val labels: LabelMode = LabelMode.Focused,
    val reduceTransparency: Boolean = false,
    val heroEnabled: Boolean = true,
    val heroAutoRotate: Boolean = true,
    val heroSource: HeroSource = HeroSource.Recommendations,
    val columns: Int = 6,
    val demoContent: Boolean = false,
    val hiddenApps: Set<String> = emptySet(),
    /** With the accessibility service on, the remote's Home button opens Prism TV. */
    val homeOverride: Boolean = true,
    val aerialSource: AerialSource = AerialSource.Apple,
    val aerialQuality: AerialQuality = AerialQuality.Auto,
    val aerialShowLocation: Boolean = true,
    val aerialRange: AerialRange = AerialRange.Auto,
    /** Look for new Prism TV builds and offer to install them. */
    val updateCheck: Boolean = true,
    /** A version the user chose "Later" for; not offered again automatically. */
    val skippedUpdate: Int = 0,
    /** Current weather in the time pill. */
    val weather: Boolean = true,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.Auto,
    /** Prism TV Originals navigation sounds (focus, select, back…). */
    val sounds: Boolean = true,
    /** Home Screen glass colour; Automatic follows [appearance]. */
    val glassColour: GlassColour = GlassColour.Automatic,
    /** Minutes without a button press on the Home Screen before the aerials start; 0 = Never. */
    val screensaverAfter: Int = 0,
    /** Liquid Glass: Clear or Tinted (ignored when [material] is Frosted). */
    val liquidTint: LiquidTint = LiquidTint.Tinted,
    /** Index into [TextScales]. */
    val textSize: Int = DefaultTextSize,
    val topShelfStyle: TopShelfStyle = TopShelfStyle.FullScreen,
    /** "scene:<name>" (a Prism wallpaper) or "photo:<content uri>" (one of the user's pictures). */
    val wallpaper: String = "scene:aurora",
    /** Widget rows (by row key) placed above or below the dock. */
    val widgets: Map<String, WidgetPlace> = emptyMap(),
    val keyboardColour: KeyboardColour = KeyboardColour.Automatic,
    /** With the Power & Home Button service on: show Prism TV after the TV restarts (Google TV). */
    val openAtStartup: Boolean = true,
) {
    /** The material glass is drawn with: Liquid Glass (Tinted or Clear) or Frosted. */
    val glass: GlassMaterial
        get() = when {
            material == GlassMaterial.Frosted -> GlassMaterial.Frosted
            liquidTint == LiquidTint.Clear -> GlassMaterial.Clear
            else -> GlassMaterial.Liquid
        }

    val textScale: Float get() = TextScales[textSize.coerceIn(0, TextScales.size - 1)]
}

/** Settings › Screensaver › Start After choices, in minutes (0 = Never). */
val StartAfterChoices = listOf(0, 2, 5, 10, 15, 30)

class PrefsStore(context: Context) {
    private val sp = context.getSharedPreferences("prism_prefs", Context.MODE_PRIVATE)

    fun load(): LauncherPrefs {
        val d = LauncherPrefs()
        // Clear used to be its own material; it's now Liquid Glass with the Clear look.
        val storedMaterial = enumOr(sp.getString("material", null), d.material)
        return LauncherPrefs(
            appearance = enumOr(sp.getString("appearance", null), d.appearance),
            material = if (storedMaterial == GlassMaterial.Clear) GlassMaterial.Liquid else storedMaterial,
            liquidTint = sp.getString("liquidTint", null)?.let { enumOr(it, d.liquidTint) }
                ?: if (storedMaterial == GlassMaterial.Clear) LiquidTint.Clear else d.liquidTint,
            textSize = sp.getInt("textSize", d.textSize).coerceIn(0, TextScales.size - 1),
            topShelfStyle = enumOr(sp.getString("topShelfStyle", null), d.topShelfStyle),
            wallpaper = sp.getString("wallpaper", null) ?: d.wallpaper,
            widgets = parseWidgets(sp.getString("widgets", null)),
            keyboardColour = enumOr(sp.getString("keyboardColour", null), d.keyboardColour),
            openAtStartup = sp.getBoolean("openAtStartup", d.openAtStartup),
            focusEffect = enumOr(sp.getString("focusEffect", null), d.focusEffect),
            labels = enumOr(sp.getString("labels", null), d.labels),
            reduceTransparency = sp.getBoolean("reduceTransparency", d.reduceTransparency),
            heroEnabled = sp.getBoolean("heroEnabled", d.heroEnabled),
            heroAutoRotate = sp.getBoolean("heroAutoRotate", d.heroAutoRotate),
            heroSource = enumOr(sp.getString("heroSource", null), d.heroSource),
            columns = sp.getInt("columns", d.columns).coerceIn(5, 6),
            demoContent = sp.getBoolean("demoContent", d.demoContent),
            hiddenApps = sp.getStringSet("hiddenApps", emptySet())?.toSet() ?: emptySet(),
            homeOverride = sp.getBoolean("homeOverride", d.homeOverride),
            aerialSource = enumOr(sp.getString("aerialSource", null), d.aerialSource),
            aerialQuality = enumOr(sp.getString("aerialQuality", null), d.aerialQuality),
            aerialShowLocation = sp.getBoolean("aerialShowLocation", d.aerialShowLocation),
            aerialRange = enumOr(sp.getString("aerialRange", null), d.aerialRange),
            updateCheck = sp.getBoolean("updateCheck", d.updateCheck),
            skippedUpdate = sp.getInt("skippedUpdate", d.skippedUpdate),
            weather = sp.getBoolean("weather", d.weather),
            temperatureUnit = enumOr(sp.getString("temperatureUnit", null), d.temperatureUnit),
            sounds = sp.getBoolean("sounds", d.sounds),
            glassColour = enumOr(sp.getString("glassColour", null), d.glassColour),
            screensaverAfter = sp.getInt("screensaverAfter", d.screensaverAfter).takeIf { it in StartAfterChoices } ?: d.screensaverAfter,
        )
    }

    fun save(p: LauncherPrefs) {
        sp.edit()
            .putString("appearance", p.appearance.name)
            .putString("material", p.material.name)
            .putString("focusEffect", p.focusEffect.name)
            .putString("labels", p.labels.name)
            .putBoolean("reduceTransparency", p.reduceTransparency)
            .putBoolean("heroEnabled", p.heroEnabled)
            .putBoolean("heroAutoRotate", p.heroAutoRotate)
            .putString("heroSource", p.heroSource.name)
            .putInt("columns", p.columns)
            .putBoolean("demoContent", p.demoContent)
            .putStringSet("hiddenApps", p.hiddenApps)
            .putBoolean("homeOverride", p.homeOverride)
            .putString("aerialSource", p.aerialSource.name)
            .putString("aerialQuality", p.aerialQuality.name)
            .putBoolean("aerialShowLocation", p.aerialShowLocation)
            .putString("aerialRange", p.aerialRange.name)
            .putBoolean("updateCheck", p.updateCheck)
            .putInt("skippedUpdate", p.skippedUpdate)
            .putBoolean("weather", p.weather)
            .putString("temperatureUnit", p.temperatureUnit.name)
            .putBoolean("sounds", p.sounds)
            .putString("glassColour", p.glassColour.name)
            .putInt("screensaverAfter", p.screensaverAfter)
            .putString("liquidTint", p.liquidTint.name)
            .putInt("textSize", p.textSize)
            .putString("topShelfStyle", p.topShelfStyle.name)
            .putString("wallpaper", p.wallpaper)
            .putString("widgets", p.widgets.entries.joinToString("\n") { "${it.key}\t${it.value.name}" })
            .putString("keyboardColour", p.keyboardColour.name)
            .putBoolean("openAtStartup", p.openAtStartup)
            .apply()
    }

    fun clear() {
        sp.edit().clear().apply()
    }

    private fun parseWidgets(raw: String?): Map<String, WidgetPlace> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, WidgetPlace>()
        raw.split('\n').forEach { line ->
            val tab = line.lastIndexOf('\t')
            if (tab > 0) {
                val place = enumOr(line.substring(tab + 1), WidgetPlace.Off)
                if (place != WidgetPlace.Off) out[line.substring(0, tab)] = place
            }
        }
        return out
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
