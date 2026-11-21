package com.prismtv.launcher.data

import android.content.Context

enum class AppearanceSetting(val label: String) {
    Light("Light"), Balanced("Balanced"), Dark("Dark"), Automatic("Automatic")
}

/** Material Colour: the Home Screen glass (dock, sidebar, Control Centre, menus, folders). */
enum class GlassColour(val label: String) {
    /** Follows Colour Mode. */
    Automatic("Automatic"), Light("Light"), Balanced("Balanced"), Dark("Dark"),
    /** The most see-through glass: no tint, colours follow Colour Mode. */
    Clear("Clear"),
}

/** How much of the Home Screen shows through Settings and pop-ups (always frosted glass). */
enum class UiTransparency(val label: String) { High("High"), Medium("Medium"), Low("Low") }

/** Wallpaper › Shuffle intervals, in seconds (0 = Off). */
val ShuffleChoices = listOf(0, 30, 60, 300, 900, 1800, 3600, 86400)

fun shuffleLabel(seconds: Int): String = when (seconds) {
    0 -> "Off"
    30 -> "Every 30 Seconds"
    60 -> "Every Minute"
    3600 -> "Every Hour"
    86400 -> "Daily"
    else -> "Every ${seconds / 60} Minutes"
}

enum class GlassMaterial(val label: String) {
    /** Liquid Glass: the lens with a gentle veil so text stays legible. */
    Liquid("Liquid Glass"),
    /** Drawing material only: Liquid Glass with Material Colour › Clear (no veil). Never stored. */
    Clear("Clear Glass"),
    Frosted("Frosted Glass"),
}

/** How the Top Shelf shows the focused app's titles. */
enum class TopShelfStyle(val label: String) { FullScreen("Full Screen"), Spotlight("Card Spotlight") }

/** Colour of Prism TV's on-screen keyboard; Automatic follows Appearance. */
/** Keyboard colour; Clear is clear Liquid Glass (over the Home Screen) or smoked see-through glass (other apps). */
enum class KeyboardColour(val label: String) { Automatic("Automatic"), Light("Light"), Balanced("Balanced"), Dark("Dark"), Clear("Clear") }

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

/** Screensaver source. Shuffle takes turns between aerials, your pictures and Prism's scenes. */
enum class AerialSource(val label: String) {
    Apple("Apple TV Aerials"), Prism("Prism Scenes"), Pictures("Your Pictures"), Shuffle("Shuffle")
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
    val appearance: AppearanceSetting = AppearanceSetting.Balanced,
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
    /** Material Colour; Automatic follows [appearance] (Colour Mode), Clear has no tint. */
    val glassColour: GlassColour = GlassColour.Automatic,
    /** Settings & Pop-ups Transparency. */
    val uiTransparency: UiTransparency = UiTransparency.Medium,
    /** Minutes without a button press on the Home Screen before the aerials start; 0 = Never. */
    val screensaverAfter: Int = 2,
    /** Don't start the screensaver while music or a video is playing. */
    val screensaverWaitForMedia: Boolean = true,
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
    /** tvOS-style focus motion: a springy settle, a lean toward the move and a nudge at edges. */
    val focusWobble: Boolean = true,
    /** Prism Keyboard: fix misspelt words on space, and show suggestions above the keys. */
    val autoCorrect: Boolean = true,
    val predictive: Boolean = true,
    /** Widget row keys in the order the user arranged them (others follow in their own order). */
    val widgetOrder: List<String> = emptyList(),
    /** Rows gathered from every app ("all:…"): only these packages; empty = every app. */
    val widgetApps: Map<String, Set<String>> = emptyMap(),
    /** Seconds between wallpapers (0 = Off). */
    val wallpaperShuffle: Int = 0,
    /** Wallpapers in the shuffle ("scene:…" / "photo:…"); empty = every Prism wallpaper. */
    val shuffleSet: Set<String> = emptySet(),
    /** The Top Shelf shows a title's logo artwork in place of its name when the app or Nuvio has one. */
    val titleLogos: Boolean = true,
    /** Profile picture: "preset:<n>" (a Prism avatar) or "photo:<content uri>". */
    val avatar: String = "preset:0",
    /** Name in the sidebar and Settings › Profile; blank = the TV's device name. */
    val profileName: String = "",
    /**
     * Lite Mode: pre-blurred artwork instead of live blur and Liquid Glass refraction, no extra motion,
     * and smaller artwork in memory. For slower or 2 GB boxes.
     */
    val liteMode: Boolean = false,
    /** An app opened once each time the TV starts (its id); empty = stay on the Home Screen. */
    val startupApp: String = "",
    /** Seconds to wait before opening [startupApp]. */
    val startupDelay: Int = 0,
) {
    /** The material glass is drawn with: Liquid Glass (Tinted or Clear) or Frosted. */
    val glass: GlassMaterial
        get() = when {
            material == GlassMaterial.Frosted -> GlassMaterial.Frosted
            glassColour == GlassColour.Clear -> GlassMaterial.Clear
            else -> GlassMaterial.Liquid
        }

    val textScale: Float get() = TextScales[textSize.coerceIn(0, TextScales.size - 1)]
}

/** Settings › Open at Startup › Wait choices, in seconds. */
val StartupDelays = listOf(0, 5, 10, 20, 30)

/** Settings › Screensaver › Start After choices, in minutes (0 = Never). */
val StartAfterChoices = listOf(0, 2, 5, 10, 15, 30)

class PrefsStore(context: Context) {
    private val sp = context.getSharedPreferences("prism_prefs", Context.MODE_PRIVATE)

    fun load(): LauncherPrefs {
        val d = LauncherPrefs()
        // Clear was once a material, then a Liquid Glass look; it's now a Material Colour.
        val storedMaterial = enumOr(sp.getString("material", null), d.material)
        val wasClear = storedMaterial == GlassMaterial.Clear || sp.getString("liquidTint", null) == "Clear"
        val storedColour = enumOr(sp.getString("glassColour", null), d.glassColour)
        return LauncherPrefs(
            appearance = enumOr(sp.getString("appearance", null), d.appearance),
            material = if (storedMaterial == GlassMaterial.Clear) GlassMaterial.Liquid else storedMaterial,
            glassColour = if (wasClear && storedColour == GlassColour.Automatic && !sp.contains("uiTransparency")) GlassColour.Clear else storedColour,
            uiTransparency = enumOr(sp.getString("uiTransparency", null), d.uiTransparency),
            focusWobble = sp.getBoolean("focusWobble", d.focusWobble),
            autoCorrect = sp.getBoolean("autoCorrect", d.autoCorrect),
            predictive = sp.getBoolean("predictive", d.predictive),
            widgetOrder = sp.getString("widgetOrder", null)?.split('\n')?.filter { it.isNotBlank() } ?: d.widgetOrder,
            widgetApps = parseWidgetApps(sp.getString("widgetApps", null)),
            wallpaperShuffle = sp.getInt("wallpaperShuffle", d.wallpaperShuffle).takeIf { it in ShuffleChoices } ?: d.wallpaperShuffle,
            shuffleSet = sp.getStringSet("shuffleSet", emptySet())?.toSet() ?: emptySet(),
            titleLogos = sp.getBoolean("titleLogos", d.titleLogos),
            avatar = sp.getString("avatar", null) ?: d.avatar,
            profileName = sp.getString("profileName", null) ?: d.profileName,
            liteMode = sp.getBoolean("liteMode", d.liteMode),
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
            screensaverAfter = sp.getInt("screensaverAfter", d.screensaverAfter).takeIf { it in StartAfterChoices } ?: d.screensaverAfter,
            screensaverWaitForMedia = sp.getBoolean("screensaverWaitForMedia", d.screensaverWaitForMedia),
            startupApp = sp.getString("startupApp", d.startupApp).orEmpty(),
            startupDelay = sp.getInt("startupDelay", d.startupDelay).takeIf { it in StartupDelays } ?: d.startupDelay,
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
            .putBoolean("screensaverWaitForMedia", p.screensaverWaitForMedia)
            .putString("startupApp", p.startupApp)
            .putInt("startupDelay", p.startupDelay)
            .remove("liquidTint")
            .putString("uiTransparency", p.uiTransparency.name)
            .putBoolean("focusWobble", p.focusWobble)
            .putBoolean("autoCorrect", p.autoCorrect)
            .putBoolean("predictive", p.predictive)
            .putString("widgetOrder", p.widgetOrder.joinToString("\n"))
            .putString("widgetApps", p.widgetApps.entries.joinToString("\n") { "${it.key}\t${it.value.joinToString(",")}" })
            .putInt("wallpaperShuffle", p.wallpaperShuffle)
            .putStringSet("shuffleSet", p.shuffleSet)
            .putBoolean("titleLogos", p.titleLogos)
            .putString("avatar", p.avatar)
            .putString("profileName", p.profileName)
            .putBoolean("liteMode", p.liteMode)
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

    private fun parseWidgetApps(raw: String?): Map<String, Set<String>> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, Set<String>>()
        raw.split('\n').forEach { line ->
            val tab = line.indexOf('\t')
            if (tab > 0) {
                val pkgs = line.substring(tab + 1).split(',').filter { it.isNotBlank() }.toSet()
                if (pkgs.isNotEmpty()) out[line.substring(0, tab)] = pkgs
            }
        }
        return out
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
