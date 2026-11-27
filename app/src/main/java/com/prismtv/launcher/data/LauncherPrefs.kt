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

/** The letter keys of Prism's keyboard: QWERTY, or A to Z in a grid tuned for the D-pad. */
enum class KeyboardLayoutStyle(val label: String) { Qwerty("QWERTY"), Abc("ABC") }

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

/**
 * The screensaver's clock: Small is the time in the top corner (free); Large (top left, with the date) and
 * Centred (top middle, under the date, like a lock screen) are Prism Pro.
 */
enum class ScreensaverClock(val label: String) {
    Off("Off"), Small("Small"), Large("Large"), Centred("Centred")
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
    val columns: Int = 5,
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
    /** Software Update › Update Channel: Testing gets every test build, not only main (GitHub edition). */
    val updateTesting: Boolean = false,
    /** A version the user chose "Later" for; not offered again automatically. */
    val skippedUpdate: Int = 0,
    /** Current weather in the time pill. */
    val weather: Boolean = true,
    /** Wi-Fi signal (or Ethernet, or no connection) in the time pill. */
    val wifiStatus: Boolean = true,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.Auto,
    /** Prism TV Originals navigation sounds (focus, select, back…). */
    val sounds: Boolean = true,
    /** Material Colour; Automatic follows [appearance] (Colour Mode), Clear has no tint. */
    val glassColour: GlassColour = GlassColour.Clear,
    /** Settings & Pop-ups Transparency. */
    val uiTransparency: UiTransparency = UiTransparency.High,
    /** Minutes without a button press on the Home Screen before the aerials start; 0 = Never. */
    val screensaverAfter: Int = 2,
    /** Don't start the screensaver while music or a video is playing. */
    val screensaverWaitForMedia: Boolean = true,
    /** Your Pictures / Shuffle: every picture, or just [screensaverPictures]. */
    val screensaverAllPictures: Boolean = true,
    /** Chosen albums ("album:<id>") and pictures ("photo:<id>") for the screensaver. */
    val screensaverPictures: Set<String> = emptySet(),
    /** Settings › Screensaver › Clock. */
    val screensaverClock: ScreensaverClock = ScreensaverClock.Small,
    /** The temperature and conditions beside the screensaver's clock (Prism Pro). */
    val screensaverWeather: Boolean = true,
    /** What's playing, with its artwork, over the screensaver (Prism Pro; needs Notification access). */
    val screensaverNowPlaying: Boolean = true,
    /** Index into [TextScales]. */
    val textSize: Int = DefaultTextSize,
    val topShelfStyle: TopShelfStyle = TopShelfStyle.Spotlight,
    /** "scene:<name>" (a Prism wallpaper) or "photo:<content uri>" (one of the user's pictures). */
    val wallpaper: String = "scene:aurora",
    /** Widget rows (by row key) placed above or below the dock. */
    val widgets: Map<String, WidgetPlace> = mapOf("all:continue" to WidgetPlace.Below),
    val keyboardColour: KeyboardColour = KeyboardColour.Automatic,
    val keyboardLayout: KeyboardLayoutStyle = KeyboardLayoutStyle.Qwerty,
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
    val wallpaperShuffle: Int = 30,
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
    /** Your names for the TV's inputs (input id → name). */
    val inputNames: Map<String, String> = emptyMap(),
    /** Banners you picked for apps (app id → "photo:<uri>"). */
    val customBanners: Map<String, String> = emptyMap(),
    /** An installed icon pack's package; empty = the apps' own banners. */
    val iconPack: String = "",
    /** Everyone's profiles; empty = just the one (whose name and picture are [profileName]/[avatar]). */
    val profiles: List<ProfileSpec> = emptyList(),
    /** The profile in use; its name and picture live in [profileName] and [avatar]. */
    val activeProfile: String = "",
    /** Show "Who's Watching?" when the TV wakes (with two or more profiles). */
    val profilePickerOnWake: Boolean = true,
    /** Show "Who's Watching?" when the TV starts up (with two or more profiles). */
    val profilePickerOnStart: Boolean = true,
    /** Prism's own keyboard text field; Automatic matches the keyboard. */
    val keyboardFieldColour: KeyboardColour = KeyboardColour.Clear,
    /** Apps swiped away in the App Switcher are force stopped (Shizuku, root or Power & Home Button), not just trimmed. */
    val closeCompletely: Boolean = true,
    /** Closing Apps may use `su` (asked for once, from Settings). */
    val rootClose: Boolean = false,
    /** Shizuku stops when the TV restarts: start it again through the TV's own adb (once set up). */
    val shizukuAutoStart: Boolean = true,
    /** The TV's own launcher Prism TV hid ("package/activity"), to restore before uninstalling. */
    val hiddenLauncher: String = "",
    /** App Switcher cards show what each app was showing (a snapshot taken as you press Home). */
    val appSnapshots: Boolean = true,
    /** New notifications slide in over the app you're watching (through Power & Home Button). */
    val bannersOverApps: Boolean = true,
    /** Settings › Remote Buttons (see RemoteButtons.encode), and what holding Home does. */
    val remoteButtons: String = "",
    val homeHold: String = "default",
    /** The still (and Shuffle) to go back to when Apple TV Aerials are turned off. */
    val lastStillWallpaper: String = "",
    val lastShuffle: Int = 0,
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
            // Never saved → the default rows; saved empty → you turned every row off.
            widgets = sp.getString("widgets", null)?.let { parseWidgets(it) } ?: d.widgets,
            keyboardColour = enumOr(sp.getString("keyboardColour", null), d.keyboardColour),
            keyboardLayout = enumOr(sp.getString("keyboardLayout", null), d.keyboardLayout),
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
            updateTesting = sp.getBoolean("updateTesting", d.updateTesting),
            skippedUpdate = sp.getInt("skippedUpdate", d.skippedUpdate),
            weather = sp.getBoolean("weather", d.weather),
            wifiStatus = sp.getBoolean("wifiStatus", d.wifiStatus),
            temperatureUnit = enumOr(sp.getString("temperatureUnit", null), d.temperatureUnit),
            sounds = sp.getBoolean("sounds", d.sounds),
            screensaverAfter = sp.getInt("screensaverAfter", d.screensaverAfter).takeIf { it in StartAfterChoices } ?: d.screensaverAfter,
            screensaverWaitForMedia = sp.getBoolean("screensaverWaitForMedia", d.screensaverWaitForMedia),
            screensaverAllPictures = sp.getBoolean("screensaverAllPictures", d.screensaverAllPictures),
            screensaverPictures = sp.getStringSet("screensaverPictures", null)?.toSet() ?: d.screensaverPictures,
            screensaverClock = enumOr(sp.getString("screensaverClock", null), d.screensaverClock),
            screensaverWeather = sp.getBoolean("screensaverWeather", d.screensaverWeather),
            screensaverNowPlaying = sp.getBoolean("screensaverNowPlaying", d.screensaverNowPlaying),
            startupApp = sp.getString("startupApp", d.startupApp).orEmpty(),
            startupDelay = sp.getInt("startupDelay", d.startupDelay).takeIf { it in StartupDelays } ?: d.startupDelay,
            inputNames = parseStringMap(sp.getString("inputNames", null)),
            customBanners = parseStringMap(sp.getString("customBanners", null)),
            iconPack = sp.getString("iconPack", d.iconPack).orEmpty(),
            profiles = Profiles.decode(sp.getString("profiles", null)),
            activeProfile = sp.getString("activeProfile", d.activeProfile).orEmpty(),
            profilePickerOnWake = sp.getBoolean("profilePickerOnWake", d.profilePickerOnWake),
            profilePickerOnStart = sp.getBoolean("profilePickerOnStart", d.profilePickerOnStart),
            keyboardFieldColour = KeyboardColour.entries.firstOrNull { it.name == sp.getString("keyboardFieldColour", null) } ?: d.keyboardFieldColour,
            closeCompletely = sp.getBoolean("closeCompletely", d.closeCompletely),
            rootClose = sp.getBoolean("rootClose", d.rootClose),
            shizukuAutoStart = sp.getBoolean("shizukuAutoStart", d.shizukuAutoStart),
            hiddenLauncher = sp.getString("hiddenLauncher", d.hiddenLauncher).orEmpty(),
            appSnapshots = sp.getBoolean("appSnapshots", d.appSnapshots),
            bannersOverApps = sp.getBoolean("bannersOverApps", d.bannersOverApps),
            remoteButtons = sp.getString("remoteButtons", d.remoteButtons).orEmpty(),
            homeHold = sp.getString("homeHold", d.homeHold) ?: d.homeHold,
            lastStillWallpaper = sp.getString("lastStillWallpaper", d.lastStillWallpaper).orEmpty(),
            lastShuffle = sp.getInt("lastShuffle", d.lastShuffle),
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
            .putBoolean("updateTesting", p.updateTesting)
            .putInt("skippedUpdate", p.skippedUpdate)
            .putBoolean("weather", p.weather)
            .putBoolean("wifiStatus", p.wifiStatus)
            .putString("temperatureUnit", p.temperatureUnit.name)
            .putBoolean("sounds", p.sounds)
            .putString("glassColour", p.glassColour.name)
            .putInt("screensaverAfter", p.screensaverAfter)
            .putBoolean("screensaverWaitForMedia", p.screensaverWaitForMedia)
            .putBoolean("screensaverAllPictures", p.screensaverAllPictures)
            .putStringSet("screensaverPictures", p.screensaverPictures)
            .putString("screensaverClock", p.screensaverClock.name)
            .putBoolean("screensaverWeather", p.screensaverWeather)
            .putBoolean("screensaverNowPlaying", p.screensaverNowPlaying)
            .putString("startupApp", p.startupApp)
            .putInt("startupDelay", p.startupDelay)
            .putString("inputNames", encodeStringMap(p.inputNames))
            .putString("customBanners", encodeStringMap(p.customBanners))
            .putString("iconPack", p.iconPack)
            .putString("profiles", Profiles.encode(p.profiles))
            .putString("activeProfile", p.activeProfile)
            .putBoolean("profilePickerOnWake", p.profilePickerOnWake)
            .putBoolean("profilePickerOnStart", p.profilePickerOnStart)
            .putString("keyboardFieldColour", p.keyboardFieldColour.name)
            .putBoolean("closeCompletely", p.closeCompletely)
            .putBoolean("rootClose", p.rootClose)
            .putBoolean("shizukuAutoStart", p.shizukuAutoStart)
            .putString("hiddenLauncher", p.hiddenLauncher)
            .putBoolean("appSnapshots", p.appSnapshots)
            .putBoolean("bannersOverApps", p.bannersOverApps)
            .putString("remoteButtons", p.remoteButtons)
            .putString("homeHold", p.homeHold)
            .putString("lastStillWallpaper", p.lastStillWallpaper)
            .putInt("lastShuffle", p.lastShuffle)
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
            .putString("keyboardLayout", p.keyboardLayout.name)
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

    private fun parseStringMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        raw.split('\n').forEach { line ->
            val tab = line.indexOf('\t')
            if (tab > 0 && tab < line.length - 1) out[line.substring(0, tab)] = line.substring(tab + 1)
        }
        return out
    }

    private fun encodeStringMap(map: Map<String, String>): String =
        map.entries.joinToString("\n") { (k, v) -> k.replace('\t', ' ').replace('\n', ' ') + "\t" + v.replace('\t', ' ').replace('\n', ' ') }

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
