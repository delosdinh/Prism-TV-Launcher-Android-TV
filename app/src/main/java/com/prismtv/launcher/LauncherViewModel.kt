package com.prismtv.launcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prismtv.launcher.aerial.AerialActivity
import com.prismtv.launcher.aerial.AerialCatalog
import com.prismtv.launcher.data.AerialQuality
import com.prismtv.launcher.data.AerialRange
import com.prismtv.launcher.data.StartAfterChoices
import com.prismtv.launcher.data.DeepLinks
import com.prismtv.launcher.data.KeyboardColour
import com.prismtv.launcher.data.KeyboardLayoutStyle
import com.prismtv.launcher.data.TextScales
import com.prismtv.launcher.data.TopShelfStyle
import com.prismtv.launcher.data.UriArt
import com.prismtv.launcher.data.WidgetPlace
import com.prismtv.launcher.data.WidgetRepository
import com.prismtv.launcher.data.WidgetRow
import com.prismtv.launcher.system.PrismNotification
import com.prismtv.launcher.system.PrismNotificationListener
import com.prismtv.launcher.ui.WidgetLayout
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.material.icons.rounded.Notifications
import com.prismtv.launcher.data.TemperatureUnit
import com.prismtv.launcher.data.Weather
import com.prismtv.launcher.data.WeatherRepository
import com.prismtv.launcher.data.AerialSource
import com.prismtv.launcher.data.AppItem
import com.prismtv.launcher.data.AppRepository
import com.prismtv.launcher.data.AppearanceSetting
import com.prismtv.launcher.data.ArtRef
import com.prismtv.launcher.data.ArtRepository
import com.prismtv.launcher.data.BannerArt
import com.prismtv.launcher.data.DemoContent
import com.prismtv.launcher.data.FocusEffect
import com.prismtv.launcher.data.FolderItem
import com.prismtv.launcher.data.GlassColour
import com.prismtv.launcher.data.GlassMaterial
import com.prismtv.launcher.data.GridItem
import com.prismtv.launcher.data.GridLayout
import com.prismtv.launcher.data.GridLayouts
import com.prismtv.launcher.data.HeroArt
import com.prismtv.launcher.data.HeroItem
import com.prismtv.launcher.data.HeroSource
import com.prismtv.launcher.data.LabelMode
import com.prismtv.launcher.data.LauncherApp
import com.prismtv.launcher.data.LauncherPrefs
import com.prismtv.launcher.data.LayoutStore
import com.prismtv.launcher.data.PrefsStore
import com.prismtv.launcher.data.RecentEntry
import com.prismtv.launcher.data.RecentsRepository
import com.prismtv.launcher.data.RecommendationsRepository
import com.prismtv.launcher.data.ResArt
import com.prismtv.launcher.data.MemoryArt
import com.prismtv.launcher.sound.Cue
import com.prismtv.launcher.sound.UiSounds
import com.prismtv.launcher.system.PrismAccessibilityService
import com.prismtv.launcher.system.SystemActions
import com.prismtv.launcher.update.InstallEvent
import com.prismtv.launcher.update.UpdateInfo
import com.prismtv.launcher.update.Updater
import com.prismtv.launcher.ui.FolderLayout
import com.prismtv.launcher.ui.HomeLayout
import com.prismtv.launcher.ui.SwitcherLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import com.prismtv.launcher.data.UiTransparency
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

const val TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

enum class Zone { TopBar, Hero, Grid, Widget }
enum class Overlay { None, ControlCentre, Switcher, Settings, Folder, Sidebar }
enum class NavKey { Up, Down, Left, Right, Select, LongSelect, Menu, PlayPause }

data class DialogButton(val label: String, val action: () -> Unit)

/** Text entry on Prism's own keyboard (Rename Folder). */
data class KeyboardRequest(
    val title: String,
    val initial: String,
    val placeholder: String = "",
    val onDone: (String) -> Unit,
)
data class DialogSpec(
    val title: String,
    val message: String,
    val buttons: List<DialogButton>,
    /** A details line under the title (the Top Shelf Info sheet's genre · year · runtime). */
    val subtitle: String = "",
    /** Outlined chips (4K, Dolby Vision…) under the subtitle. */
    val badges: List<String> = emptyList(),
    /** A shell command to type, shown exactly (monospace, in its own box) under the message. */
    val code: String = "",
    /** A short line under [code]. */
    val codeNote: String = "",
)
/** Settings › Wallpaper › Apple TV Aerials: moving aerials behind Home (a still in Lite Mode). */
const val AerialWallpaperKey = "aerial"

/** Under a command in a dialog: where to type it. */
private const val TypeNote = "Type it exactly as shown in ATV Connect's Shell (or any shell running on the TV). From a computer, put adb shell in front."

data class BannerSpec(val title: String, val message: String, val icon: ImageVector, val id: Long = System.nanoTime())

enum class SettingsPage(val title: String) {
    Root("Settings"), Appearance("Appearance"), HomeScreen("Home Screen"), Screensaver("Screensaver"),
    Apps("Apps"), SoftwareUpdate("Software Update"), About("About"), Acknowledgements("Acknowledgements"),
    TextSize("Text Size"), Wallpaper("Wallpaper"), Widgets("Widgets"),
    Keyboard("Keyboard"), WidgetApps("Widget"), WidgetOrder("Arrange Widgets"), Profile("Profile"),
    Startup("Open at Startup"), Inputs("Inputs"), Backup("Backup & Restore"), IconPack("Icon Pack"),
    Banner("Banner"), Profiles("Profiles"), Restrictions("Restrictions"), ScreensaverPictures("Screensaver Pictures"),
    Access("Privacy & Access"), Closing("Closing Apps"), Shizuku("Shizuku"), HomeApp("Home App"),
    RemoteButtons("Remote Buttons"), RemoteButton("Button"), RemoteAction("Action"), ScreenTime("Screen Time"),
    Pro("Prism Pro"),
}

/**
 * Settings › About › Acknowledgements: what Prism TV is made with, and under which licences. Each row's
 * description (SettingsHelp, "ack:") names the authors and where the licence is.
 */
private val AcknowledgementRows = listOf(
    SettingsRow("ack:inter", "Inter", "TYPEFACE", "SIL Open Font License 1.1"),
    SettingsRow("ack:wordfreq", "Word List", "KEYBOARD", "CC BY-SA 4.0"),
    SettingsRow("ack:ngram", "Next-Word Predictions", "KEYBOARD", "CC BY 3.0"),
    SettingsRow("ack:sounds", "Prism TV Originals", "NAVIGATION SOUNDS", "GPL 2.0 or Later"),
    SettingsRow("ack:androidx", "Android Jetpack and Compose", "SOFTWARE", "Apache 2.0"),
    SettingsRow("ack:media3", "Media3", "SOFTWARE", "Apache 2.0"),
    SettingsRow("ack:kotlin", "Kotlin", "SOFTWARE", "Apache 2.0"),
    SettingsRow("ack:shizuku", "Shizuku API", "SOFTWARE", "Apache 2.0"),
    SettingsRow("ack:apple", "Apple TV and tvOS", "TRADEMARKS", "Apple Inc."),
)

/** Settings › Prism Pro: what Pro adds (data/Pro.kt has the full rules). */
private val ProFeatures = listOf(
    "Your Pictures, Shuffle and Aerials as Wallpaper",
    "Card Spotlight and Title Logos",
    "Widgets",
    "Lift & Shine and Focus Wobble",
    "Material Colour by Hand and Transparency Levels",
    "Icon Packs and Custom Banners",
    "Profiles, Restrictions and Screen Time",
    "Banners Over Apps",
    "More Remote Buttons and Hold Actions",
    "Keyboard Colours, Predictions and Dictation",
    "Screensaver Shuffle, 4K and Dolby Vision",
)

/** Prism Search results: apps, titles from apps' rows, and places in Settings. */
sealed interface SearchResult {
    val key: String
    val label: String
}
data class AppSearchResult(val app: LauncherApp) : SearchResult {
    override val key: String get() = "app:${app.id}"
    override val label: String get() = app.label
}
data class TitleSearchResult(val item: HeroItem) : SearchResult {
    override val key: String get() = "title:${item.key}"
    override val label: String get() = item.title
}
data class SettingSearchResult(override val label: String, val where: String, val page: SettingsPage, val rowId: String? = null) : SearchResult {
    override val key: String get() = "setting:${page.name}:${rowId.orEmpty()}:$label"
}

/**
 * One row of a Settings list. Rows are rebuilt on every D-pad press, and a row holding an app counted as
 * unstable (its artwork is an ImageBitmap), so Compose compared rows by instance and recomposed every row
 * of the page on every press. Every field is a val (the artwork is never changed in place), so Compose may
 * compare rows by value: only the rows that actually changed (the old and new focus) recompose.
 */
@androidx.compose.runtime.Immutable
data class SettingsRow(
    val id: String,
    val title: String,
    val section: String? = null,
    val value: String? = null,
    val selectable: Boolean = false,
    val checked: Boolean = false,
    val chevron: Boolean = false,
    /** Apps page: the app this row shows/hides (drawn with its artwork). */
    val app: LauncherApp? = null,
    /** An on/off switch on the right (lists where several can be on). Rows valued "On"/"Off" get one too. */
    val toggle: Boolean? = null,
)

/** A zoom transition between a tile (in dp) and full screen. */
data class LaunchAnim(
    val app: LauncherApp,
    val from: Rect,
    val art: ArtRef?,
    val reverse: Boolean = false,
    val id: Long = System.nanoTime(),
    /** Coming back with Home: the app's screen as you left it, shrinking into its icon (tvOS). */
    val still: ImageBitmap? = null,
)

data class MenuAction(val id: String, val label: String, val icon: ImageVector, val destructive: Boolean = false)

/** The hold-OK context menu for an app or folder. */
data class ContextMenuState(
    val title: String,
    val item: GridItem,
    val app: LauncherApp?,
    val parentFolder: FolderItem?,
    val anchor: Rect,
    val actions: List<MenuAction>,
    val id: Long = System.nanoTime(),
)

/** Control Centre focus targets, in reading order. */
/** Control Centre controls — TV controls only; navigation (Search, Recents, Settings) lives in the sidebar. */
object CC {
    const val Profile = 2
    const val Power = 3
    const val WiFi = 4
    const val Sound = 6
    const val Sleep = 7
    const val Bluetooth = 9
    const val Picture = 12
    const val Memory = 13
    const val Access = 14
    const val Screensaver = 15
    /** Now Playing's buttons: previous, play/pause, next. */
    const val NowPrev = 20
    const val NowPlay = 21
    const val NowNext = 22
    /** The small ✕ above the notifications (Clear All, as on iOS). */
    const val ClearAll = 30
    /** Notification rows in Control Centre: [Notification] + index. */
    const val Notification = 100
    /** A notification slid aside to show its Clear button: [NotificationClear] + index. */
    const val NotificationClear = 200

    /** The notification row a Control Centre focus belongs to (its card or its Clear button), or null. */
    fun noteIndex(focus: Int): Int? = when {
        focus >= NotificationClear -> focus - NotificationClear
        focus >= Notification -> focus - Notification
        else -> null
    }
}

/** The passcode keypad, left to right (as on tvOS: one row of digits, then delete). */
val PasscodeKeys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "⌫")
const val PasscodeDelete = "⌫"

/** Cards in a widget row (after narrowing an all-app row to the chosen apps). */
const val WidgetRowMax = 30

/** Widgets › an all-app row with every app switched off. */
const val NoApps = "-"

/** Prism wallpapers: name and artwork. The scenes double as Demo Content Top Shelves. */
val Wallpapers: List<Pair<String, Int>> = listOf(
    "prism-light" to R.drawable.wall_prism_light,
    "prism-dusk" to R.drawable.wall_prism_dusk,
    "prism-night" to R.drawable.wall_prism_night,
    "prism-blue" to R.drawable.wall_prism_blue,
    "ocean-blue" to R.drawable.wall_ocean_blue,
    "beach" to R.drawable.wall_beach,
    "deep-blue" to R.drawable.wall_deep_blue,
    "aurora" to R.drawable.hero_aurora,
    "glacier" to R.drawable.hero_glacier,
    "dunes" to R.drawable.hero_dunes,
    "nebula" to R.drawable.hero_nebula,
    "ocean" to R.drawable.hero_ocean,
)

fun wallpaperName(key: String): String = when (val k = key.removePrefix("scene:")) {
    "ocean" -> "Ocean Sunset"
    else -> k.split('-').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}

enum class SidebarEntry(val label: String) {
    Search("Search"), Home("Home"), Recents("Recent Apps"),
    PrismSettings("Prism Settings"), SystemSettings("Android Settings"),
    /** The TV's app store (Google Play; Amazon Appstore or Aptoide TV on boxes without it), with its own icon. */
    Store("Play Store"),
}

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Application get() = getApplication()
    private val store = PrefsStore(app)
    private val layoutStore = LayoutStore(app)
    private val appsRepo = AppRepository(app)
    private val artRepo = ArtRepository(app)
    private val recsRepo = RecommendationsRepository(app)
    private val recentsRepo = RecentsRepository(app)

    var prefs by mutableStateOf(store.load())
        private set

    // ---------------------------------------------------------------- sounds
    private val sounds = UiSounds(app).also { it.enabled = prefs.sounds }
    /** The cue picked by whatever handled the current key press; otherwise one is inferred. */
    private var keyCue: Cue? = null
    private var inKey = false

    /** Plays [c] now, or — during a key press — in place of the inferred cue. */
    private fun cue(c: Cue) {
        if (inKey) keyCue = c else sounds.play(c)
    }

    private var realApps by mutableStateOf<List<LauncherApp>>(emptyList())
    private var heroItemsByApp by mutableStateOf<Map<String, List<HeroItem>>>(emptyMap())
    private var layout by mutableStateOf(layoutStore.load(if (prefs.demoContent) "demo" else "real"))
    init {
        // The layout above is read before the profile is known: switch to this profile's own one.
        if (!prefs.demoContent && prefs.activeProfile.isNotEmpty() && prefs.activeProfile != com.prismtv.launcher.data.Profiles.MainId) {
            layout = loadLayout()
        }
    }
    /** Each profile keeps its own Home Screen layout (the first profile keeps the original one). */
    private val layoutKey: String
        get() = when {
            prefs.demoContent -> "demo"
            prefs.activeProfile.isEmpty() || prefs.activeProfile == com.prismtv.launcher.data.Profiles.MainId -> "real"
            else -> "real:${prefs.activeProfile}"
        }

    /** A profile without a layout of its own yet starts from the first profile's. */
    private fun loadLayout(): GridLayout? = layoutStore.load(layoutKey) ?: if (layoutKey.startsWith("real:")) layoutStore.load("real") else null

    /** Visible Home Screen items (apps + folders), in order. */
    val items: List<GridItem> by derivedStateOf {
        if (prefs.demoContent) {
            GridLayouts.build(DemoContent.allApps + inputApps, layout ?: DemoContent.defaultLayout, prefs.hiddenApps + blockedApps)
        } else {
            GridLayouts.build(realApps + inputApps, layout, prefs.hiddenApps + blockedApps)
        }
    }

    /** The TV's HDMI inputs as tiles (TVs only; boxes have none). */
    private var inputApps by mutableStateOf<List<LauncherApp>>(emptyList())

    /** Installed apps plus the TV's inputs; cached until either changes. */
    val allApps: List<LauncherApp> by derivedStateOf { (if (prefs.demoContent) DemoContent.allApps else realApps) + inputApps }

    /** Every app A to Z, for Settings' app lists: sorted once when the apps change, not on every D-pad press. */
    private val appsByName: List<LauncherApp> by derivedStateOf { allApps.sortedBy { it.label.lowercase() } }

    // ---------------------------------------------------------------- home focus
    var zone by mutableStateOf(Zone.Grid)
    var row by mutableIntStateOf(0)
    var col by mutableIntStateOf(0)
    var heroButton by mutableIntStateOf(0)
    var topIndex by mutableIntStateOf(0)
    var heroAppIndex by mutableIntStateOf(0)
    var heroIndex by mutableIntStateOf(0)

    var overlay by mutableStateOf(Overlay.None)
    var dialog by mutableStateOf<DialogSpec?>(null)
    var dialogFocus by mutableIntStateOf(0)
    var banner by mutableStateOf<BannerSpec?>(null)
    var clockOverride by mutableStateOf<String?>(null)
    var resumeTick by mutableIntStateOf(0)
    var restartTick by mutableIntStateOf(0)

    private val handledTicks = HashMap<String, Int>()

    /** True the first time the window sees this value of a one-off request tick ([restartTick] and co). */
    fun consumeTick(name: String, value: Int): Boolean {
        if (value <= 0 || handledTicks[name] == value) return false
        handledTicks[name] = value
        return true
    }
    var permissionRequestTick by mutableIntStateOf(0)

    // ---------------------------------------------------------------- Prism Pro (Play edition)

    /** Pro is on: always in the GitHub edition; bought, or in the free trial, in the Play edition. */
    var proActive by mutableStateOf(!BuildConfig.PLAY)
        private set
    /** Pro's price from Google Play, in the viewer's currency (null until Play has said). */
    private var proPrice by mutableStateOf<String?>(null)
    /** Settings › Prism Pro › Unlock: MainActivity opens Google Play's purchase sheet. */
    var purchaseTick by mutableIntStateOf(0)

    /**
     * Works out whether Pro is on. Without it, anything set with Pro goes back to its free choice
     * ([com.prismtv.launcher.data.Pro.freeOnly]), so a trial that ended (or a refund) leaves nothing paid on.
     */
    private fun refreshPro() {
        if (!BuildConfig.PLAY) return
        val on = com.prismtv.launcher.data.Pro.active(ctx)
        val allowed = com.prismtv.launcher.data.Pro.playOnly(prefs).let { if (on) it else com.prismtv.launcher.data.Pro.freeOnly(it) }
        if (allowed != prefs) updatePrefs(allowed)
        if (proActive != on) {
            if (proActive && !on) showBanner("Prism Pro", "Your free trial of Prism Pro has ended. Settings › Prism Pro unlocks it again.", Icons.Rounded.Info)
            proActive = on
        }
    }

    /** Settings that need Pro to change (selecting one without it opens Settings › Prism Pro). */
    private fun needsPro(id: String, p: LauncherPrefs): Boolean = when {
        id.startsWith("gc:") -> id != "gc:${GlassColour.Automatic.name}"
        id.startsWith("ui:") -> id != "ui:${UiTransparency.High.name}"
        id == "fx:${FocusEffect.Lift.name}" -> true
        id == "wobble" -> !p.focusWobble
        id == "topShelf" -> p.topShelfStyle != TopShelfStyle.Spotlight
        id == "logos" -> !p.titleLogos
        id == "widgets" -> true
        id == "wp:shuffle" -> p.wallpaperShuffle == 0
        id == "wp:aerial" -> !aerialWallpaperOn
        id.startsWith("wp:photo:") -> true
        id == "iconPack" -> true
        id == "kbColour" || id == "kbField" -> true
        id == "kb:predictive" -> !p.predictive
        id == "bannersOver" -> !p.bannersOverApps
        id == "pr:add" -> true
        // Limits already set can still be seen and removed.
        id == "pr:restrict" -> activeProfileSpec.blocked.isEmpty()
        id == "pr:screentime" -> !activeProfileSpec.timeLimited
        id == "rb:homeHold" || id == "rbe:hold" -> true
        id == "rb:add" -> remoteMappings().size >= com.prismtv.launcher.data.Pro.FreeRemoteButtons
        else -> false
    }

    private fun proLabel(): String = when {
        proActive && com.prismtv.launcher.data.Pro.owned(ctx) -> "Unlocked"
        proActive -> com.prismtv.launcher.data.Pro.trialDaysLeft(ctx).let { "Trial · $it ${if (it == 1) "Day" else "Days"} Left" }
        else -> proPrice ?: "Unlock"
    }

    private fun proRows(): List<SettingsRow> {
        val owned = com.prismtv.launcher.data.Pro.owned(ctx)
        val days = com.prismtv.launcher.data.Pro.trialDaysLeft(ctx)
        val top = if (owned) {
            listOf(SettingsRow("pro:status", "Prism Pro", "PRISM PRO", "Unlocked"))
        } else {
            listOf(
                SettingsRow("pro:buy", "Unlock Prism Pro", "PRISM PRO", proPrice ?: "Google Play", chevron = true),
                SettingsRow("pro:status", "Free Trial", "PRISM PRO", if (days > 0) "$days ${if (days == 1) "Day" else "Days"} Left" else "Ended"),
                SettingsRow("pro:restore", "Restore Purchase", "PRISM PRO", chevron = true),
            )
        }
        return top + ProFeatures.mapIndexed { i, f -> SettingsRow("pro:what:$i", f, "WHAT'S IN PRO") }
    }

    /** Without Pro: says what's Pro, and opens Settings › Prism Pro. */
    private fun openPro(feature: String) {
        pushPage(SettingsPage.Pro)
        settingsFocus[SettingsPage.Pro] = 0
        com.prismtv.launcher.data.ProStore.refresh()
        showBanner("Prism Pro", "$feature is part of Prism Pro.", Icons.Rounded.Info)
    }

    /** MainActivity: Google Play's purchase sheet couldn't open. */
    fun proUnavailable() {
        showBanner("Prism Pro", "Google Play isn't ready yet. Check that this TV is signed in to Google Play, then try again.", Icons.Rounded.Info)
    }

    // ---------------------------------------------------------------- editing
    var contextMenu by mutableStateOf<ContextMenuState?>(null)
    var contextFocus by mutableIntStateOf(0)
    /** Id of the item being rearranged ("jiggle" mode), or null. */
    var movingId by mutableStateOf<String?>(null)

    // ---------------------------------------------------------------- folder
    var openFolder by mutableStateOf<FolderItem?>(null)
    var folderFocus by mutableIntStateOf(0)
    /** The folder tile the open folder grows out of (and shrinks back into). */
    var folderAnchor by mutableStateOf<Rect?>(null)

    // ---------------------------------------------------------------- widgets
    private val widgetRepo = WidgetRepository(app)
    /** Every row the apps publish (Continue Watching, Up Next, channels, Recommended for You), unfiltered. */
    private var widgetRowsAll by mutableStateOf<List<WidgetRow>>(emptyList())

    /** The rows as shown: in the user's order, "all:" rows limited to the apps chosen for them. */
    val widgetRows: List<WidgetRow> by derivedStateOf {
        val order = prefs.widgetOrder
        val chosen = prefs.widgetApps
        // Apps the profile in use can't see don't show their cards either.
        val blocked = blockedApps.let { ids -> allApps.filter { it.id in ids }.map { it.packageName }.toSet() }
        widgetRowsAll
            .filter { r -> r.pkg !in blocked }
            .map { r -> if (blocked.isEmpty()) r else r.copy(items = r.items.filter { it.appId.substringBefore('/') !in blocked }) }
            .map { r ->
                val only = chosen[r.key]
                val items = if (only.isNullOrEmpty()) r.items else r.items.filter { it.appId.substringBefore('/') in only }
                r.copy(items = items.take(WidgetRowMax))
            }
            .withIndex()
            .sortedBy { (i, r) -> order.indexOf(r.key).let { o -> if (o < 0) 10_000 + i else o } }
            .map { it.value }
    }

    /** Settings › Widgets › (an "All Apps" row): which row the app picker is for. */
    var widgetDetailKey by mutableStateOf("")
        private set

    /** Settings › Widgets › Arrange Widgets: the row being moved, or null. */
    var widgetMoving by mutableStateOf<String?>(null)
        private set

    /** A name for a package that isn't on the Home Screen (Google's recommendation service, for one). */
    private fun labelFor(pkg: String): String = when (pkg) {
        "com.google.android.tvrecommendations" -> "Google TV Recommendations"
        else -> runCatching {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    /** The apps that have cards in [key]'s row (before filtering): id (package) and name. */
    fun widgetSourceApps(key: String): List<Pair<String, String>> {
        val row = widgetRowsAll.firstOrNull { it.key == key } ?: return emptyList()
        return row.items.map { it.appId.substringBefore('/') }.distinct().map { id ->
            id to (allApps.firstOrNull { it.id.substringBefore('/') == id || it.packageName == id }?.label
                ?: row.items.firstOrNull { it.appId.substringBefore('/') == id }?.appLabel?.takeIf { it.isNotBlank() } ?: id)
        }.sortedBy { it.second.lowercase() }
    }

    private fun widgetAppsLabel(key: String): String {
        val chosen = prefs.widgetApps[key] ?: return "All Apps"
        val n = (chosen - NoApps).size
        return if (n == 0) "No Apps" else if (n == 1) "1 App" else "$n Apps"
    }

    /** All Apps: on (every app) ⇄ off (none, then switch on the ones you want). */
    private fun toggleAllWidgetApps(key: String) {
        val all = prefs.widgetApps[key] == null
        updatePrefs(prefs.copy(widgetApps = if (all) prefs.widgetApps + (key to setOf(NoApps)) else prefs.widgetApps - key))
    }

    /** One app on or off. Every app on is the same as All Apps; none on keeps the row empty. */
    private fun toggleWidgetApp(key: String, id: String) {
        val all = widgetSourceApps(key).map { it.first }.toSet()
        val current = (prefs.widgetApps[key] ?: all) - NoApps
        val next = if (id in current) current - id else current + id
        updatePrefs(
            prefs.copy(
                widgetApps = when {
                    next.containsAll(all) -> prefs.widgetApps - key
                    next.isEmpty() -> prefs.widgetApps + (key to setOf(NoApps))
                    else -> prefs.widgetApps + (key to next)
                }
            )
        )
    }

    /** Arrange Widgets: moves [key] up or down among the rows in the same place (above / below the dock). */
    private fun moveWidget(key: String, delta: Int) {
        val keys = widgetRows.map { it.key }.toMutableList()
        val place = prefs.widgets[key] ?: return
        val group = keys.filter { prefs.widgets[it] == place }
        val gi = group.indexOf(key)
        val gj = gi + delta
        if (gi < 0 || gj !in group.indices) {
            cue(Cue.Error)
            return
        }
        val a = keys.indexOf(key)
        val b = keys.indexOf(group[gj])
        keys[a] = group[gj]
        keys[b] = key
        updatePrefs(prefs.copy(widgetOrder = keys))
        val rows = settingsRows(SettingsPage.WidgetOrder)
        settingsFocus[SettingsPage.WidgetOrder] = rows.indexOfFirst { it.id == "wo:$key" }.coerceAtLeast(0)
        cue(Cue.FocusSmall)
    }

    /** Title shown at the top of a Settings page. */
    fun pageTitle(page: SettingsPage): String = when (page) {
        SettingsPage.WidgetApps -> widgetRowsAll.firstOrNull { it.key == widgetDetailKey }?.title ?: page.title
        SettingsPage.Banner -> allApps.firstOrNull { it.id == bannerAppId }?.label?.let { "$it Banner" } ?: page.title
        SettingsPage.RemoteButton -> remoteEditingName()
        SettingsPage.RemoteAction -> if (remoteEditing == "home") "Hold Home" else "${remoteEditingName()} · ${if (remoteSlot == "hold") "Hold" else "Press"}"
        SettingsPage.ScreenTime -> "Screen Time"
        else -> page.title
    }
    /** Rows shown above the dock, top to bottom (at most two fit above it). */
    val aboveRows: List<WidgetRow> by derivedStateOf {
        widgetRows.filter { prefs.widgets[it.key] == WidgetPlace.Above && it.items.isNotEmpty() }.take(2)
    }
    /** Rows shown below the dock, between it and the app grid. */
    val belowRows: List<WidgetRow> by derivedStateOf {
        widgetRows.filter { prefs.widgets[it.key] == WidgetPlace.Below && it.items.isNotEmpty() }.take(4)
    }
    /** With [Zone.Widget]: the focused row (above rows first, then below rows) and card. */
    var widgetIndex by mutableIntStateOf(0)
    var widgetCol by mutableIntStateOf(0)
    val widgetFocusAbove: Boolean get() = zone == Zone.Widget && widgetIndex < aboveRows.size
    val belowHeight: Float get() = belowRows.size * WidgetLayout.RowH

    private fun widgetRowAt(i: Int): WidgetRow? =
        if (i < aboveRows.size) aboveRows.getOrNull(i) else belowRows.getOrNull(i - aboveRows.size)

    /**
     * Small artwork for cards, logos and previews; loads in the background the first time it's asked for.
     * Kept in the repository's size-limited cache (this used to be an ever-growing map: every card ever
     * shown stayed in memory, which slowed Prism down and got it killed on 2 GB boxes).
     */
    fun thumb(ref: ArtRef, width: Int = 400): ImageBitmap? {
        // Each picture has its own version, so only what shows it redraws when it arrives.
        val version = thumbVersions.getOrPut(ref.key) { mutableIntStateOf(0) }
        @Suppress("UNUSED_VARIABLE") val v = version.intValue
        artRepo.cachedThumb(ref)?.let { return it }
        val failed = failedThumbs[ref.key]
        if (ref.key !in loadingThumbs && (failed == null || android.os.SystemClock.elapsedRealtime() >= failed.retryAt)) {
            loadingThumbs += ref.key
            viewModelScope.launch {
                val t = artRepo.thumb(ref, width)
                loadingThumbs -= ref.key
                if (t != null) {
                    failedThumbs.remove(ref.key)
                    version.intValue++
                } else {
                    // Try again later: 30 s, then doubling up to 15 minutes. Whatever shows it asks again
                    // when its version changes; nothing does once it's off screen.
                    val attempts = (failedThumbs[ref.key]?.attempts ?: 0) + 1
                    val wait = minOf(30_000L shl minOf(attempts - 1, 5), 15 * 60_000L)
                    failedThumbs[ref.key] = ThumbFailure(attempts, android.os.SystemClock.elapsedRealtime() + wait)
                    version.intValue++
                    delay(wait)
                    version.intValue++
                }
            }
        }
        return null
    }
    private val thumbVersions = HashMap<String, androidx.compose.runtime.MutableIntState>()
    private val loadingThumbs = HashSet<String>()
    /**
     * Artwork that couldn't be loaded, and when to try again. It used to be never (this session): a card
     * whose picture failed while the network was still coming up after a restart stayed blank for good.
     */
    private class ThumbFailure(val attempts: Int, val retryAt: Long)
    private val failedThumbs = HashMap<String, ThumbFailure>()

    // ---------------------------------------------------------------- wallpaper
    /** The Spotlight Top Shelf's background (and the Home Screen's when the Top Shelf is off). */
    var wallpaperArt by mutableStateOf<HeroArt?>(null)
        private set
    /** The user's pictures for Settings › Wallpaper: (content uri, name). */
    var photos by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var photoPermissionTick by mutableIntStateOf(0)

    val spotlight: Boolean get() = prefs.topShelfStyle == TopShelfStyle.Spotlight && prefs.heroEnabled

    // ---------------------------------------------------------------- keyboard
    var keyboard by mutableStateOf<KeyboardRequest?>(null)
        private set
    /** The text being typed on Prism's own keyboard (Rename Folder, Profile Name). */
    private val keyboardEditor = object : com.prismtv.launcher.ui.keyboard.KeyboardEditor {
        var text by mutableStateOf("")
        override fun textBefore(n: Int): String = text.takeLast(n)
        override fun commit(text: String) { this.text = (this.text + text).take(40) }
        override fun deleteBefore(n: Int) { text = text.dropLast(n) }
        override fun capsMode(): Boolean = text.isEmpty() || text.trimEnd().let { it.isEmpty() || it.last() in ".!?" } && text.endsWith(" ")
        override fun action() = finishKeyboard()
    }
    val keyboardText: String get() = keyboardEditor.text
    private var dictation: com.prismtv.launcher.ime.Dictation? = null
    /** Prism Keyboard (the same one other apps get), typing into [keyboardEditor]. */
    val keyboardController = com.prismtv.launcher.ui.keyboard.KeyboardController(
        app,
        sound = { cue(it) },
        dictate = { start -> if (start) dictation?.start() else dictation?.stop() },
    ).also { it.editor = keyboardEditor }

    // ---------------------------------------------------------------- notifications
    var notifications by mutableStateOf<List<PrismNotification>>(emptyList())
        private set
    /** What another app is playing (Control Centre's Now Playing tile); null when nothing is. */
    var nowPlaying by mutableStateOf<com.prismtv.launcher.system.NowPlaying?>(null)
        private set
    /** Notifications Control Centre has room for (two when Now Playing is showing). */
    val ccNotificationsShown: Int get() = notifications.size.coerceAtMost(if (nowPlaying != null) 2 else 3)

    /** Settings › Backup & Restore: backups found on this TV, newest first. */
    var backups by mutableStateOf<List<com.prismtv.launcher.data.Backup.Entry>>(emptyList())
        private set
    /** Asks the activity for Android's document picker (Backup & Restore › Choose a File…). */
    var backupPickTick by mutableIntStateOf(0)
        private set

    /** Settings › Banner: the app whose banner is being chosen. */
    var bannerAppId by mutableStateOf<String?>(null)
        private set
    /** Settings › Icon Pack: icon packs installed on the TV. */
    var iconPacks by mutableStateOf<List<com.prismtv.launcher.data.IconPacks.Pack>>(emptyList())
        private set

    /** CI: a title logo for the demo Top Shelf (demo titles have no IMDb id). */
    var debugTitleArt by mutableStateOf<ArtRef?>(null)
        private set

    /** The last D-pad move, for tvOS focus motion (see [com.prismtv.launcher.ui.FocusMotion]). */
    val focusMotion = com.prismtv.launcher.ui.FocusMotion()

    // ---------------------------------------------------------------- sidebar
    var sidebarFocus by mutableIntStateOf(1)

    // ---------------------------------------------------------------- control centre
    var ccFocus by mutableIntStateOf(CC.Power)
    var sleepMinutes by mutableIntStateOf(0)
    private var sleepJob: Job? = null

    // ---------------------------------------------------------------- switcher
    var recents by mutableStateOf<List<RecentEntry>>(emptyList())
    var switcherFocus by mutableIntStateOf(0)
    var dismissingKey by mutableStateOf<String?>(null)
    var switcherLift by mutableStateOf(false)
    var switcherClosingToHome by mutableStateOf(false)

    // ---------------------------------------------------------------- settings
    var settingsStack by mutableStateOf(listOf(SettingsPage.Root))
    val settingsFocus = mutableStateMapOf<SettingsPage, Int>()

    // ---------------------------------------------------------------- transitions & art
    var launchAnim by mutableStateOf<LaunchAnim?>(null)
    /** 0 → 1 as an app zooms out of its icon; drives the Home Screen recede. */
    var launchProgress by mutableFloatStateOf(0f)
    private var pendingReturn: LaunchAnim? = null
    private var launchFailed = false
    var currentArt by mutableStateOf<HeroArt?>(null)
        private set
    private var heroTimer: Job? = null
    private var bannerJob: Job? = null
    private var lastRecsLoad = 0L

    val isGridMode: Boolean
        get() = (zone == Zone.Grid && row > 0) || (zone == Zone.Widget && !widgetFocusAbove) || !prefs.heroEnabled
    val isHeroExpanded: Boolean get() = zone == Zone.Hero || zone == Zone.TopBar

    val dockItem: GridItem? get() = items.getOrNull(heroAppIndex)

    val currentHeroItems: List<HeroItem>
        get() = when (val item = dockItem) {
            is AppItem -> heroesFor(item.app)
            is FolderItem -> item.apps.firstOrNull()?.let { heroesFor(it) } ?: emptyList()
            null -> emptyList()
        }

    val currentHeroItem: HeroItem?
        get() {
            // Browsing a widget above the dock: the Top Shelf shows the focused card.
            if (widgetFocusAbove) widgetRowAt(widgetIndex)?.items?.getOrNull(widgetCol)?.let { return it }
            val list = currentHeroItems
            return if (list.isEmpty()) null else list[heroIndex.coerceIn(0, list.size - 1)]
        }

    // init { … } is at the end of the class, after every property it uses has been initialised.


    // ================================================================= software update

    /** A newer build found on GitHub (shown in Settings › Software Update and offered once per version). */
    var availableUpdate by mutableStateOf<UpdateInfo?>(null)
        private set
    var updateStatus by mutableStateOf("")
        private set
    private var updateJob: Job? = null
    private var pendingAfterPermission: UpdateInfo? = null
    /** Set when CI drives the UI through debug intents; update prompts would cover the screenshots. */
    private var debugDriven = false
    /** CI's Android TV emulators never prompt (their builds race the published ones). */
    private val isEmulator = Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") || Build.FINGERPRINT.startsWith("generic")
    private val updateSp = ctx.getSharedPreferences("prism_update", Context.MODE_PRIVATE)

    /** After Prism TV has updated itself, say so once on the Home Screen. */
    private fun announceInstalledVersion() {
        val last = updateSp.getInt("lastRunVersion", 0)
        val now = BuildConfig.VERSION_CODE
        if (last == now) return
        updateSp.edit().putInt("lastRunVersion", now).apply()
        if (last == 0 || now < last || debugDriven || isEmulator) return
        val notes = updateSp.getString("notes:$now", null)?.trim()?.takeIf { it.isNotEmpty() && it.length <= 90 }
        showBanner(
            "Prism TV Updated",
            notes?.let { "${BuildConfig.VERSION_NAME}: $it" } ?: "You're now on version ${BuildConfig.VERSION_NAME}.",
            Icons.Rounded.CheckCircle,
            Cue.Confirm,
        )
    }

    private fun maybeAutoCheckForUpdate() {
        if (BuildConfig.PLAY) return // Google Play updates the Play edition
        pendingAfterPermission?.let { info ->
            if (Updater.canInstall(ctx)) {
                pendingAfterPermission = null
                startUpdate(info)
                return
            }
        }
        if (!prefs.updateCheck || debugDriven || isEmulator) return
        val last = updateSp.getLong("lastCheck", 0L)
        if (System.currentTimeMillis() - last < UPDATE_INTERVAL_MS) {
            // Re-offer a found update that the user hasn't declined yet.
            availableUpdate?.let { if (it.versionCode != prefs.skippedUpdate) offerUpdate(it) }
            return
        }
        checkForUpdates(manual = false)
    }

    fun checkForUpdates(manual: Boolean) {
        if (BuildConfig.PLAY || updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            updateStatus = "Checking…"
            val result = Updater.check()
            updateSp.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
            result.onSuccess { info ->
                availableUpdate = info
                if (info == null) {
                    updateStatus = "Up to Date"
                    if (manual) showBanner("Software Update", "Prism TV ${BuildConfig.VERSION_NAME} is up to date.", Icons.Rounded.CheckCircle, Cue.Confirm)
                } else {
                    updateStatus = "${info.versionName} Available"
                    if (manual || info.versionCode != prefs.skippedUpdate) offerUpdate(info)
                }
            }.onFailure {
                updateStatus = "Couldn't Check"
                if (manual) showBanner("Software Update", "Couldn't reach GitHub. Check your internet connection.", Icons.Rounded.Info, Cue.Error)
            }
        }
    }

    /** "Prism TV x is available" — Update Now / Later. Only shown on a quiet Home Screen. */
    private fun offerUpdate(info: UpdateInfo) {
        if (BuildConfig.PLAY) return
        if (debugDriven || dialog != null || launchAnim != null || (overlay != Overlay.None && overlay != Overlay.Settings)) return
        val notes = info.notes.takeIf { it.isNotBlank() }?.let { "\n\nWhat's new: $it" } ?: ""
        dialog = DialogSpec(
            "Software Update",
            "Prism TV ${info.versionName} is available. You have ${BuildConfig.VERSION_NAME}.$notes",
            listOf(
                DialogButton("Update Now") { startUpdate(info) },
                DialogButton("Later") { updatePrefs(prefs.copy(skippedUpdate = info.versionCode)) },
            ),
        )
        dialogFocus = 0
        cue(Cue.Notify)
    }

    private fun startUpdate(info: UpdateInfo) {
        if (BuildConfig.PLAY) return // Google Play installs the Play edition's updates
        if (!Updater.canInstall(ctx)) {
            dialog = DialogSpec(
                "Allow Updates",
                "To update itself, Prism TV needs permission to install apps. Turn on Prism TV on the next screen, then press Back — the update continues automatically.",
                listOf(
                    DialogButton("Open Settings") {
                        pendingAfterPermission = info
                        if (!SystemActions.start(ctx, Updater.permissionIntent(ctx))) {
                            SystemActions.start(ctx, Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                    DialogButton("Cancel") {},
                ),
            )
            dialogFocus = 0
            return
        }
        updateJob?.cancel()
        updateSp.edit().putString("notes:${info.versionCode}", info.notes).apply()
        updateJob = viewModelScope.launch {
            val bannerId = System.nanoTime()
            bannerJob?.cancel()
            fun progressBanner(pct: Int) {
                banner = BannerSpec("Downloading Update", "Prism TV ${info.versionName} · $pct%", Icons.Rounded.Download, bannerId)
                updateStatus = "Downloading $pct%"
            }
            progressBanner(0)
            val file = try {
                Updater.download(ctx, info) { p -> progressBanner((p * 100).toInt()) }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                updateStatus = "Update Failed"
                showBanner("Software Update", "The download didn't finish. Try again from Settings › Software Update.", Icons.Rounded.Info, Cue.Error)
                return@launch
            }
            when (withContext(Dispatchers.IO) { Updater.verify(ctx, file) }) {
                Updater.Verdict.Ok -> runCatching { Updater.install(ctx, file) }.onFailure {
                    updateStatus = "Update Failed"
                    showBanner("Software Update", "Android couldn't start the install.", Icons.Rounded.Info, Cue.Error)
                }
                Updater.Verdict.DifferentSigner -> {
                    banner = null
                    updateStatus = "Reinstall Needed"
                    dialog = DialogSpec(
                        "Reinstall Needed",
                        "This update is signed with Prism TV's permanent key, but the version you have was signed with a temporary one. " +
                            "Uninstall Prism TV and install it again from Downloader once — after that, updates install right here.",
                        listOf(DialogButton("OK") {}),
                    )
                    dialogFocus = 0
                    cue(Cue.Notify)
                }
                Updater.Verdict.NotAnUpdate -> {
                    updateStatus = "Up to Date"
                    showBanner("Software Update", "Prism TV is up to date.", Icons.Rounded.CheckCircle)
                }
                Updater.Verdict.Unreadable -> {
                    updateStatus = "Update Failed"
                    showBanner("Software Update", "The downloaded update was damaged. Try again later.", Icons.Rounded.Info, Cue.Error)
                }
            }
        }
    }

    // ================================================================= weather

    private val weatherRepo = WeatherRepository(ctx)

    /** Current conditions for the time pill (Open-Meteo, located from the internet connection). */
    var weather by mutableStateOf(weatherRepo.cached())
        private set
    private var weatherJob: Job? = null

    private fun refreshWeather(force: Boolean = false) {
        if (!prefs.weather) return
        if (debugDriven) {
            weather = Weather(24.0, 1, true, "Brisbane")
            return
        }
        if (weatherJob?.isActive == true) return
        if (!force && System.currentTimeMillis() - weatherRepo.fetchedAt < 30L * 60 * 1000 && weather != null) return
        weatherJob = viewModelScope.launch {
            weatherRepo.refresh()?.let { weather = it }
        }
    }

    /** Dolby Vision support (box decoder + TV), checked once when the Screensaver page is shown. */
    private val dolbyVision by lazy { AerialCatalog.dolbyVisionSupport(ctx) }

    fun heroesFor(app: LauncherApp): List<HeroItem> {
        // Apps without recommendation cards get a backdrop built from their own branding.
        val fallback = if (app.demo == null) {
            HeroItem(
                key = "brand:${app.id}", appId = app.id, appLabel = app.label, title = "",
                art = BannerArt(app.id),
            )
        } else {
            HeroItem(
                key = "fallback:${app.id}", appId = app.id, appLabel = app.label, title = app.label,
                art = ResArt(DemoContent.sceneFor(app.packageName)),
            )
        }
        // Something this app is playing comes first, with play/pause and next (like tvOS's Now Playing).
        val playingNow = nowPlayingHero(app)
        val list = if (prefs.heroSource == HeroSource.AppArtwork) listOf(fallback)
        else heroItemsByApp[app.id] ?: DemoContent.heroes[app.id] ?: listOf(fallback)
        return if (playingNow != null) listOf(playingNow) + list else list
    }

    private fun nowPlayingHero(app: LauncherApp): HeroItem? {
        val np = nowPlaying ?: return null
        if (np.pkg != app.packageName || np.title.isBlank()) return null
        val art = np.art?.let { a ->
            runCatching { MemoryArt("np:${np.pkg}:${np.title.hashCode()}:${System.identityHashCode(a)}", a.asAndroidBitmap()) }.getOrNull()
        }
        return HeroItem(
            key = "np:${np.pkg}",
            appId = app.id,
            appLabel = np.appLabel.ifBlank { app.label },
            title = np.title,
            meta = np.subtitle,
            badges = listOf(if (np.playing) "Now Playing" else "Paused"),
            art = art ?: if (app.demo != null) ResArt(DemoContent.sceneFor(app.packageName)) else BannerArt(app.id),
            media = true,
            playing = np.playing,
        )
    }

    /** After Lite Mode changes: the Top Shelf and wallpaper load again at the new size. */
    private fun reloadArt() {
        viewModelScope.launch {
            currentHeroItem?.art?.let { ref -> artRepo.load(ref)?.let { currentArt = it } }
            artRepo.load(wallpaperRef(wallpaperKey))?.let { wallpaperArt = it }
        }
    }

    /** Low memory (Android's onTrimMemory): drop cached artwork; what's on screen stays referenced. */
    @Suppress("DEPRECATION") // Android 13 and older (the SHIELD) still send the finer trim levels
    fun trimMemory(level: Int) {
        artRepo.trim(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) failedThumbs.clear()
        // One small entry per picture ever shown added up over days on a launcher that's never closed. Keep
        // the ones still loading or waiting to retry; what's on screen already holds its own, and anything
        // shown again gets a fresh one.
        thumbVersions.keys.retainAll { it in loadingThumbs || it in failedThumbs }
    }

    /** Reads the TV's inputs (and your names for them) into Home Screen tiles. */
    fun loadInputs() {
        viewModelScope.launch {
            val names = prefs.inputNames
            val list = withContext(Dispatchers.IO) {
                runCatching { com.prismtv.launcher.data.TvInputs.apps(ctx, names) }.getOrDefault(emptyList())
            }
            list.forEach { a -> artRepo.registerBrand(a.id, a.banner?.asAndroidBitmap(), null, a.tint.toArgb()) }
            inputApps = list
        }
    }

    private var refreshJob: kotlinx.coroutines.Job? = null

    /** Reloads the apps. [debounce]: Store updates arrive as bursts of package changes; refresh once after. */
    fun refreshApps(debounce: Boolean = false) {
        com.prismtv.launcher.system.MemTrace.count("app list reloads")
        loadInputs()
        if (prefs.demoContent) return
        val wait = debounce && realApps.isNotEmpty()
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (wait) delay(600)
            val pack = prefs.iconPack
            val custom = prefs.customBanners
            val loaded = withContext(Dispatchers.IO) {
                val base = appsRepo.load()
                // An icon pack's banners, then any you picked from your pictures, over the apps' own.
                val packed = runCatching { com.prismtv.launcher.data.IconPacks.apply(ctx, pack, base) }.getOrDefault(base)
                runCatching { com.prismtv.launcher.data.IconPacks.applyCustom(ctx, custom, packed) }.getOrDefault(packed)
            }
            loaded.forEach { a ->
                artRepo.registerBrand(a.id, a.banner?.asAndroidBitmap(), a.icon?.asAndroidBitmap(), a.tint.toArgb())
            }
            realApps = loaded
            refreshRecommendations(force = true)
        }
    }

    /** Re-reads other apps' recommendation cards (they change as you watch things). */
    fun refreshRecommendations(force: Boolean = false) {
        com.prismtv.launcher.system.MemTrace.count("recommendation reloads")
        if (prefs.demoContent) return
        val now = System.currentTimeMillis()
        if (!force && now - lastRecsLoad < 60_000) return
        lastRecsLoad = now
        viewModelScope.launch {
            val apps = realApps
            val recs = withContext(Dispatchers.IO) { recsRepo.load() }
            heroItemsByApp = apps.associate { app ->
                app.id to (recs[app.packageName] ?: emptyList()).map { it.copy(appId = app.id, appLabel = app.label) }
            }.filterValues { it.isNotEmpty() }
            val rows = withContext(Dispatchers.IO) { widgetRepo.load() }
            widgetRowsAll = rows.map { r ->
                val app = apps.firstOrNull { it.packageName == r.pkg }
                r.copy(
                    appLabel = app?.label ?: if (r.pkg.isEmpty()) "All Apps" else r.pkg,
                    items = r.items.map { item ->
                        val owner = apps.firstOrNull { it.packageName == item.appId }
                        item.copy(appId = owner?.id ?: item.appId, appLabel = owner?.label ?: item.appLabel.ifBlank { labelFor(item.appId) })
                    },
                )
            }.filter { r -> r.pkg.isEmpty() || apps.any { it.packageName == r.pkg } }
            keepWidgetFocusValid()
        }
    }

    /** Demo Content: rows built from the demo apps' cards. */
    private fun demoWidgetRows(): List<WidgetRow> {
        val all = DemoContent.allApps.flatMap { app -> (DemoContent.heroes[app.id] ?: emptyList()).map { it.copy(appId = app.id, appLabel = app.label) } }
        if (all.isEmpty()) return emptyList()
        return listOf(
            WidgetRow("all:continue", "", "Continue Watching", all.filterIndexed { i, _ -> i % 2 == 0 }.take(10), "All Apps"),
            WidgetRow("all:recommended", "", "Recommended for You", all.filterIndexed { i, _ -> i % 2 == 1 }.take(10), "All Apps"),
        )
    }

    private fun keepWidgetFocusValid() {
        if (zone != Zone.Widget) return
        val total = aboveRows.size + belowRows.size
        if (total == 0) {
            zone = Zone.Grid; row = 0; col = heroAppIndex
            return
        }
        widgetIndex = widgetIndex.coerceIn(0, total - 1)
        widgetCol = widgetCol.coerceIn(0, ((widgetRowAt(widgetIndex)?.items?.size ?: 1) - 1).coerceAtLeast(0))
    }

    fun wallpaperRef(key: String): ArtRef = when {
        key.startsWith("photo:") -> UriArt(key.removePrefix("photo:"))
        else -> ResArt(Wallpapers.firstOrNull { "scene:${it.first}" == key }?.second ?: DemoContent.scenes[0])
    }

    /** Settings › Wallpaper: a preview of the focused row's picture. */
    fun wallpaperPreview(rowId: String): Any? = when {
        rowId.startsWith("wp:") -> thumb(wallpaperRef(rowId.removePrefix("wp:")))
        else -> null
    }

    /** Lists the user's pictures (newest first) for Settings › Wallpaper. */
    fun loadPhotos() {
        if (!hasPhotoAccess()) {
            photos = emptyList()
            return
        }
        viewModelScope.launch {
            photos = withContext(Dispatchers.IO) {
                val out = ArrayList<Pair<String, String>>()
                runCatching {
                    val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val proj = arrayOf(android.provider.MediaStore.Images.Media._ID, android.provider.MediaStore.Images.Media.DISPLAY_NAME)
                    ctx.contentResolver.query(uri, proj, null, null, android.provider.MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { c ->
                        while (c.moveToNext() && out.size < 40) {
                            val id = c.getLong(0)
                            val name = c.getString(1) ?: "Picture"
                            out += android.content.ContentUris.withAppendedId(uri, id).toString() to name.substringBeforeLast('.')
                        }
                    }
                }
                out
            }
        }
    }

    fun hasPhotoAccess(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES else android.Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun hasRecommendationAccess(): Boolean =
        ContextCompat.checkSelfPermission(ctx, TV_LISTINGS) == PackageManager.PERMISSION_GRANTED

    private fun restartHeroTimer() {
        heroTimer?.cancel()
        heroTimer = viewModelScope.launch {
            while (true) {
                delay(9_000)
                val n = currentHeroItems.size
                // Only on a visible Home Screen: nothing new is decoded while an app (or video) is on top.
                val idle = homeVisible && overlay == Overlay.None && dialog == null && launchAnim == null && contextMenu == null && movingId == null
                if (prefs.heroAutoRotate && idle && n > 1 && !isGridMode) {
                    heroIndex = (heroIndex + 1) % n
                }
            }
        }
    }

    private fun updatePrefs(p: LauncherPrefs) {
        val demoChanged = p.demoContent != prefs.demoContent
        val profileChanged = p.activeProfile != prefs.activeProfile
        val shuffleChanged = p.wallpaperShuffle != prefs.wallpaperShuffle
        val liteChanged = p.liteMode != prefs.liteMode
        prefs = p
        if (liteChanged) {
            artRepo.lite = p.liteMode
            // Reload what's on screen at the new size.
            reloadArt()
        }
        if (shuffleChanged) restartShuffle()
        store.save(p)
        sounds.enabled = p.sounds
        if (profileChanged && !demoChanged && !p.demoContent) {
            // Someone else is watching: their own Home Screen.
            layout = loadLayout()
            row = 0; col = 0; heroAppIndex = 0; heroIndex = 0
            if (zone == Zone.Widget) zone = Zone.Grid
        }
        if (demoChanged) {
            layout = loadLayout()
            row = 0; col = 0; heroAppIndex = 0; heroIndex = 0
            if (zone == Zone.Widget) zone = Zone.Grid
            widgetRowsAll = if (p.demoContent) demoWidgetRows() else emptyList()
            if (!p.demoContent) refreshApps()
        }
    }

    // ================================================================= layout editing

    private fun commitItems(newItems: List<GridItem>) {
        val l: GridLayout = GridLayouts.fromItems(newItems)
        layout = l
        layoutStore.save(layoutKey, l)
    }

    private fun focusIndex(i: Int) {
        val cols = prefs.columns
        val idx = i.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        row = idx / cols
        col = idx % cols
        if (row == 0) setDockCol(col)
    }

    private fun moveFocused(delta: Int) {
        val id = movingId ?: return
        val list = items.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.size - 1)
        if (to == from) return
        val item = list.removeAt(from)
        list.add(to, item)
        commitItems(list)
        focusIndex(to)
    }

    private fun folderNameFor(app: LauncherApp): String {
        val base = if (app.demo != null) "Folder" else try {
            if (Build.VERSION.SDK_INT >= 26) {
                when (ctx.packageManager.getApplicationInfo(app.packageName, 0).category) {
                    ApplicationInfo.CATEGORY_GAME -> "Games"
                    ApplicationInfo.CATEGORY_AUDIO -> "Music"
                    ApplicationInfo.CATEGORY_VIDEO -> "Entertainment"
                    ApplicationInfo.CATEGORY_IMAGE -> "Photos"
                    ApplicationInfo.CATEGORY_NEWS -> "News"
                    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                    ApplicationInfo.CATEGORY_MAPS -> "Travel"
                    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                    else -> "Folder"
                }
            } else "Folder"
        } catch (_: Throwable) {
            "Folder"
        }
        val existing = items.filterIsInstance<FolderItem>().map { it.label }.toSet()
        if (base !in existing) return base
        var n = 2
        while ("$base $n" in existing) n++
        return "$base $n"
    }

    private fun addToFolder(app: LauncherApp, folderId: String) {
        val name = (items.firstOrNull { it.id == folderId } as? FolderItem)?.label ?: "folder"
        val rest = GridLayouts.withoutApp(items, app.id)
        commitItems(rest.map { if (it is FolderItem && it.id == folderId) it.copy(apps = it.apps + app) else it })
        showBanner(app.label, "Moved to $name.", Icons.Rounded.Folder, Cue.Confirm)
    }

    private fun newFolderWith(app: LauncherApp) {
        val name = folderNameFor(app)
        val folder = FolderItem("folder:${System.currentTimeMillis()}", name, listOf(app))
        val index = GridLayouts.indexOfApp(items, app.id)
        val rest = GridLayouts.withoutApp(items, app.id).toMutableList()
        rest.add(index.coerceIn(0, rest.size), folder)
        commitItems(rest)
        showBanner(name, "New folder created with ${app.label}.", Icons.Rounded.CreateNewFolder, Cue.Confirm)
    }

    private fun removeFromFolder(app: LauncherApp, folder: FolderItem) {
        val folderIndex = items.indexOfFirst { it.id == folder.id }
        val rest = GridLayouts.withoutApp(items, app.id).toMutableList()
        val stillThere = rest.any { it.id == folder.id }
        val insertAt = (if (stillThere) folderIndex + 1 else folderIndex).coerceIn(0, rest.size)
        rest.add(insertAt, AppItem(app))
        commitItems(rest)
        cue(Cue.Delete)
        if (openFolder?.id == folder.id) {
            openFolder = rest.firstOrNull { it.id == folder.id } as? FolderItem
            if (openFolder == null) overlay = Overlay.None
            folderFocus = folderFocus.coerceAtMost(((openFolder?.apps?.size ?: 1) - 1).coerceAtLeast(0))
        }
    }

    private fun ungroup(folder: FolderItem) {
        val index = items.indexOfFirst { it.id == folder.id }
        if (index < 0) return
        val list = items.toMutableList()
        list.removeAt(index)
        list.addAll(index, folder.apps.map { AppItem(it) })
        commitItems(list)
        cue(Cue.Delete)
    }

    private fun hideApp(app: LauncherApp) {
        val folderId = openFolder?.id
        updatePrefs(prefs.copy(hiddenApps = prefs.hiddenApps + app.id))
        if (folderId != null) {
            openFolder = items.firstOrNull { it.id == folderId } as? FolderItem
            if (openFolder == null) overlay = Overlay.None
            folderFocus = folderFocus.coerceAtMost(((openFolder?.apps?.size ?: 1) - 1).coerceAtLeast(0))
        }
        focusIndex(row * prefs.columns + col)
        showBanner(app.label, "Hidden. Unhide it in Settings › Apps.", Icons.Rounded.VisibilityOff, Cue.Delete)
    }

    // ================================================================= context menu

    private fun tileAnchor(index: Int): Rect {
        val cols = prefs.columns
        val r = index / cols
        val trayTop = if (r == 0 && !isGridMode) HomeLayout.TrayTopHero else HomeLayout.gridTrayTop(cols, row, belowHeight)
        val gap = if (isGridMode) HomeLayout.RowGapGrid else HomeLayout.RowGapHero
        return HomeLayout.tileRect(cols, index, trayTop, gap, belowHeight)
    }

    private fun openContextMenu(item: GridItem, anchor: Rect, parent: FolderItem?) {
        // iOS order: the destructive action leads in red; focus starts on the first safe one.
        val actions = when (item) {
            is FolderItem -> listOf(
                MenuAction("ungroup", "Remove Folder", Icons.Rounded.RemoveCircleOutline, destructive = true),
                MenuAction("rename", "Rename Folder", Icons.Rounded.Edit),
                MenuAction("move", "Move", Icons.Rounded.OpenWith),
            )
            is AppItem -> if (com.prismtv.launcher.data.TvInputs.isInput(item.app)) buildList {
                add(MenuAction("renameInput", "Rename Input", Icons.Rounded.Edit))
                if (parent == null) add(MenuAction("move", "Move", Icons.Rounded.OpenWith))
                add(MenuAction("folder", if (parent == null) "Add to Folder" else "Move to Folder", Icons.Rounded.CreateNewFolder))
                if (parent != null) add(MenuAction("unfolder", "Remove from Folder", Icons.Rounded.FolderOpen))
                add(MenuAction("hide", "Hide from Home Screen", Icons.Rounded.VisibilityOff))
            } else buildList {
                add(MenuAction("delete", "Delete App", Icons.Rounded.RemoveCircleOutline, destructive = true))
                if (parent == null) add(MenuAction("move", "Move", Icons.Rounded.OpenWith))
                add(MenuAction("folder", if (parent == null) "Add to Folder" else "Move to Folder", Icons.Rounded.CreateNewFolder))
                if (parent != null) add(MenuAction("unfolder", "Remove from Folder", Icons.Rounded.FolderOpen))
                if (parent != null) add(MenuAction("renameParent", "Rename Folder", Icons.Rounded.Edit))
                add(MenuAction("banner", "Change Banner", Icons.Rounded.Image))
                add(MenuAction("info", "App Info", Icons.Rounded.Info))
                add(MenuAction("hide", "Hide from Home Screen", Icons.Rounded.VisibilityOff))
            }
        }
        contextMenu = ContextMenuState(item.label, item, (item as? AppItem)?.app, parent, anchor, actions)
        contextFocus = actions.indexOfFirst { !it.destructive }.coerceAtLeast(0)
    }

    /** Asks the activity to show the TV keyboard for a new folder name. */
    var renameRequest by mutableStateOf<FolderItem?>(null)

    fun renameFolder(folderId: String, name: String) {
        renameRequest = null
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        commitItems(items.map { if (it is FolderItem && it.id == folderId) it.copy(label = clean) else it })
        cue(Cue.Confirm)
        openFolder?.let { f -> if (f.id == folderId) openFolder = items.firstOrNull { it.id == folderId } as? FolderItem }
    }

    /** Gives one of the TV's inputs your own name ("PlayStation 5"); empty goes back to the TV's. */
    private fun renameInput(inputId: String) {
        val current = prefs.inputNames[inputId] ?: inputApps.firstOrNull { it.id.endsWith(inputId) }?.label.orEmpty()
        openKeyboard("Rename Input", current, "Input Name") { name ->
            val clean = name.trim().take(30)
            updatePrefs(prefs.copy(inputNames = if (clean.isEmpty()) prefs.inputNames - inputId else prefs.inputNames + (inputId to clean)))
            loadInputs()
            cue(Cue.Confirm)
        }
    }

    private fun renameWithKeyboard(folder: FolderItem) {
        openKeyboard("Rename Folder", folder.label, "Folder Name") { renameFolder(folder.id, it) }
    }

    // ================================================================= keyboard

    fun openKeyboard(title: String, initial: String, placeholder: String = "", onDone: (String) -> Unit) {
        keyboardEditor.text = initial
        val kb = keyboardController
        kb.suggestionsOn = prefs.predictive || prefs.autoCorrect
        kb.autoCorrectOn = prefs.autoCorrect
        kb.autoCapOn = true
        kb.micOn = true
        kb.actionLabel = "done"
        com.prismtv.launcher.ui.keyboard.KeyboardLayout.alphabetical = prefs.keyboardLayout == KeyboardLayoutStyle.Abc
        kb.reset()
        if (dictation == null) dictation = com.prismtv.launcher.ime.Dictation(ctx, kb)
        keyboard = KeyboardRequest(title, initial, placeholder, onDone)
        cue(Cue.Open)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { com.prismtv.launcher.ui.keyboard.WordPredictor.get(ctx) }
            kb.refresh()
        }
    }

    private fun keyboardKey(@Suppress("UNUSED_PARAMETER") req: KeyboardRequest, k: NavKey): Boolean {
        val kb = keyboardController
        if (searchOpen && searchKey(k)) return true
        when (k) {
            NavKey.Left -> kb.nav(com.prismtv.launcher.ui.keyboard.KbNav.Left)
            NavKey.Right -> kb.nav(com.prismtv.launcher.ui.keyboard.KbNav.Right)
            NavKey.Up -> kb.nav(com.prismtv.launcher.ui.keyboard.KbNav.Up)
            NavKey.Down -> kb.nav(com.prismtv.launcher.ui.keyboard.KbNav.Down)
            NavKey.Select -> kb.nav(com.prismtv.launcher.ui.keyboard.KbNav.Select)
            NavKey.LongSelect -> kb.nav(com.prismtv.launcher.ui.keyboard.KbNav.LongSelect)
            NavKey.PlayPause -> kb.nav(com.prismtv.launcher.ui.keyboard.KbNav.Delete)
            // Back closes the accent strip first, then the keyboard.
            NavKey.Menu -> if (!kb.closeAccents()) closeKeyboard()
        }
        return true
    }

    /** Done on Prism's keyboard. */
    private fun finishKeyboard() {
        val req = keyboard ?: return
        val text = keyboardEditor.text.trim()
        closeKeyboard()
        if (text.isNotEmpty()) req.onDone(text)
    }

    fun closeKeyboard() {
        dictation?.cancel()
        keyboard = null
        searchOpen = false
        searchFocus = -1
    }

    private fun openFolderPicker(app: LauncherApp, base: ContextMenuState) {
        val folders = items.filterIsInstance<FolderItem>().filter { it.id != base.parentFolder?.id }
        val actions = folders.map { MenuAction("pick:${it.id}", it.label, Icons.Rounded.Folder) } +
            MenuAction("pick:new", "New Folder", Icons.Rounded.CreateNewFolder)
        contextMenu = base.copy(title = "Add ${app.label} to…", actions = actions, id = System.nanoTime())
        contextFocus = 0
    }

    private fun contextKey(m: ContextMenuState, k: NavKey): Boolean {
        when (k) {
            NavKey.Up, NavKey.Left -> contextFocus = (contextFocus - 1).coerceAtLeast(0)
            NavKey.Down, NavKey.Right -> contextFocus = (contextFocus + 1).coerceAtMost(m.actions.size - 1)
            NavKey.Select -> m.actions.getOrNull(contextFocus)?.let { contextAction(m, it) }
            NavKey.Menu, NavKey.LongSelect -> contextMenu = null
            NavKey.PlayPause -> Unit
        }
        return true
    }

    private fun contextAction(m: ContextMenuState, a: MenuAction) {
        val app = m.app
        val parent = m.parentFolder
        val demo = app?.demo != null
        contextMenu = null
        when {
            a.id == "move" -> movingId = m.item.id
            a.id == "rename" -> (m.item as? FolderItem)?.let { renameWithKeyboard(it) }
            a.id == "renameParent" -> parent?.let { renameWithKeyboard(it) }
            a.id == "ungroup" -> (m.item as? FolderItem)?.let { ungroup(it) }
            a.id == "folder" && app != null -> openFolderPicker(app, m)
            a.id == "unfolder" && app != null && parent != null -> removeFromFolder(app, parent)
            a.id == "pick:new" && app != null -> {
                if (parent != null) removeFromFolder(app, parent)
                newFolderWith(app)
            }
            a.id.startsWith("pick:") && app != null -> {
                addToFolder(app, a.id.removePrefix("pick:"))
                if (parent != null) {
                    openFolder = items.firstOrNull { it.id == parent.id } as? FolderItem
                    if (openFolder == null) overlay = Overlay.None
                }
            }
            a.id == "info" && app != null -> if (demo) {
                showBanner(app.label, "Demo app — App Info opens for installed apps.", Icons.Rounded.Info)
            } else {
                SystemActions.appInfo(ctx, app.packageName)
            }
            a.id == "hide" && app != null -> hideApp(app)
            a.id == "banner" && app != null -> if (demo) {
                showBanner(app.label, "Demo apps keep their artwork. Change Banner works for installed apps.", Icons.Rounded.Info)
            } else {
                openBannerPage(app)
            }
            a.id == "renameInput" && app != null -> renameInput(app.id.removePrefix(com.prismtv.launcher.data.TvInputs.Prefix))
            a.id == "delete" && app != null -> if (demo) {
                showBanner(app.label, "Demo apps can't be deleted. Use Hide instead.", Icons.Rounded.Info, Cue.Error)
            } else {
                SystemActions.uninstall(ctx, app.packageName)
            }
        }
    }

    // ================================================================= input

    /** The key being handled is a held-down repeat (lists stop at their ends instead of wrapping). */
    private var keyRepeat = false

    fun onKey(k: NavKey, repeat: Boolean = false): Boolean {
        keyRepeat = repeat
        noteInteraction()
        if (launchAnim != null) {
            if (k == NavKey.Menu) android.util.Log.i("PrismKeys", "Menu: ignored during an app launch")
            return true
        }
        val focusBefore = focusSignature()
        val surfaceBefore = surfaceSignature()
        val overlayBefore = overlay
        val depthBefore = depth()
        val heroBefore = heroIndex
        keyCue = null
        inKey = true
        val handled = try {
            routeKey(k)
        } finally {
            inKey = false
        }
        // Menu is the one button two paths deliver (Power & Home Button and the screen): say where it went.
        if (k == NavKey.Menu && !repeat) android.util.Log.i("PrismKeys", "Menu: overlay $overlayBefore -> $overlay, handled $handled")
        if (k in arrowKeys && dialog == null && contextMenu == null && keyboard == null && movingId == null &&
            (overlay == Overlay.None || overlay == Overlay.Folder) && surfaceSignature() == surfaceBefore
        ) {
            val dx = when (k) { NavKey.Left -> -1f; NavKey.Right -> 1f; else -> 0f }
            val dy = when (k) { NavKey.Up -> -1f; NavKey.Down -> 1f; else -> 0f }
            if (focusSignature() != focusBefore) focusMotion.moved(dx, dy) else focusMotion.bumped(dx, dy)
        }
        val c = keyCue ?: when {
            k == NavKey.Select -> Cue.Click
            surfaceSignature() != surfaceBefore -> transitionCue(overlayBefore, back = depth() < depthBefore)
            k in arrowKeys && focusSignature() != focusBefore -> focusCue(heroIndex != heroBefore)
            else -> null
        }
        keyCue = null
        c?.let(sounds::play)
        return handled
    }

    private val arrowKeys = setOf(NavKey.Up, NavKey.Down, NavKey.Left, NavKey.Right)

    /** Everything a focus move can change; a difference after a key press means focus moved. */
    private fun focusSignature(): List<Any?> = listOf(
        zone, row, col, heroButton, topIndex, heroIndex, ccFocus, sidebarFocus, contextFocus, dialogFocus,
        widgetIndex, widgetCol,
        folderFocus, switcherFocus, settingsFocus[settingsStack.last()],
        movingId?.let { id -> items.indexOfFirst { it.id == id } },
        pickerFocus, passcodeFocus,
    )

    /** What's on screen: overlays, menus, dialogs, Settings pages, a card being dismissed. */
    private fun surfaceSignature(): List<Any?> = listOf(
        overlay, dialog, contextMenu?.id, openFolder?.id, movingId != null, dismissingKey, keyboard,
        if (overlay == Overlay.Settings) settingsStack.size else 0,
        profilePicker, passcode,
    )

    /** How many layers are open — more after a key press plays Swish, fewer plays Back. */
    private fun depth(): Int =
        (if (overlay != Overlay.None) 1 else 0) +
            (if (overlay == Overlay.Settings) settingsStack.size - 1 else 0) +
            (if (contextMenu != null) 1 else 0) +
            (if (dialog != null) 1 else 0) +
            (if (keyboard != null) 1 else 0) +
            (if (movingId != null) 1 else 0) +
            (if (profilePicker) 1 else 0) +
            (if (passcode != null) 1 else 0)

    /** tvOS-style contextual focus: app tiles, large cards and small controls each have a cue. */
    private fun focusCue(heroStepped: Boolean): Cue = when {
        passcode != null -> Cue.FocusKey
        profilePicker -> Cue.FocusLarge
        keyboard != null -> Cue.FocusKey
        dialog != null || contextMenu != null -> Cue.FocusSmall
        overlay == Overlay.Switcher -> Cue.FocusLarge
        overlay == Overlay.Folder -> Cue.FocusApp
        overlay != Overlay.None -> Cue.FocusSmall
        zone == Zone.Hero -> if (heroStepped) Cue.FocusLarge else Cue.FocusSmall
        zone == Zone.TopBar -> Cue.FocusSmall
        zone == Zone.Widget -> Cue.FocusLarge
        else -> Cue.FocusApp
    }

    private fun routeKey(k: NavKey): Boolean {
        passcode?.let { return passcodeKey(it, k) }
        keyboard?.let { return keyboardKey(it, k) }
        if (profilePicker) return pickerKey(k)
        dialog?.let { return dialogKey(it, k) }
        contextMenu?.let { return contextKey(it, k) }
        return when (overlay) {
            Overlay.ControlCentre -> ccKey(k)
            Overlay.Switcher -> switcherKey(k)
            Overlay.Settings -> settingsKey(k)
            Overlay.Folder -> folderKey(k)
            Overlay.Sidebar -> sidebarKey(k)
            Overlay.None -> homeKey(k)
        }
    }

    /**
     * The sound for moving between surfaces: panels that slide in or out from a side carry their own
     * gesture (the sidebar from the left, Control Centre from the top-right, the switcher's deck);
     * everything else opens or goes back.
     */
    private fun transitionCue(before: Overlay, back: Boolean): Cue {
        val after = overlay
        return when {
            after == before -> if (back) Cue.Back else Cue.Open
            after == Overlay.Sidebar -> Cue.RevealLeft
            before == Overlay.Sidebar && after == Overlay.None -> Cue.HideLeft
            after == Overlay.ControlCentre -> Cue.RevealRight
            before == Overlay.ControlCentre && after == Overlay.None -> Cue.HideRight
            after == Overlay.Switcher -> Cue.SwitcherOpen
            before == Overlay.Switcher && after == Overlay.None -> Cue.SwitcherClose
            back -> Cue.Back
            else -> Cue.Open
        }
    }

    fun onBack() {
        if (BuildConfig.DEBUG) android.util.Log.d("PrismClose", "Prism got Back (overlay $overlay)")
        if (launchAnim != null) return
        if (passcode != null) {
            sounds.play(Cue.Back)
            cancelPasscode()
            return
        }
        if (profilePicker && keyboard == null) {
            if (!pickerForced) {
                sounds.play(Cue.Back)
                closePicker()
            }
            return
        }
        if (keyboard != null) {
            sounds.play(Cue.Back)
            closeKeyboard()
            return
        }
        val before = overlay
        val layered = dialog != null || contextMenu != null || movingId != null
        backStep()
        sounds.play(if (!layered && overlay != before) transitionCue(before, back = overlay == Overlay.None) else Cue.Back)
    }

    private fun backStep() {
        when {
            launchAnim != null -> Unit
            dialog != null -> dialog = null
            contextMenu != null -> contextMenu = null
            movingId != null -> movingId = null
            overlay == Overlay.Settings && settingsStack.size > 1 -> settingsStack = settingsStack.dropLast(1)
            overlay == Overlay.Switcher -> closeSwitcherToHome()
            // Back slides a notification back first (its Clear button was showing).
            overlay == Overlay.ControlCentre && ccFocus >= CC.NotificationClear -> ccFocus -= CC.NotificationClear - CC.Notification
            overlay != Overlay.None -> closeOverlay()
            zone == Zone.Widget && !widgetFocusAbove && belowRows.isNotEmpty() && widgetIndex > aboveRows.size -> {
                widgetIndex = aboveRows.size
            }
            zone != Zone.Grid -> { zone = Zone.Grid; row = 0; col = heroAppIndex }
            row > 0 -> { row = 0; col = col.coerceAtMost(rowSize(0) - 1) }
            // On the Home Screen, Back opens the sidebar capsule (Back again closes it).
            else -> openSidebar()
        }
    }

    fun onHomePressed() {
        if (passcode != null) cancelPasscode()
        if (profilePicker && !pickerForced) closePicker()
        if (dialog != null || contextMenu != null || movingId != null) sounds.play(Cue.Back)
        else when (overlay) {
            Overlay.None -> Unit
            Overlay.Sidebar -> sounds.play(Cue.HideLeft)
            Overlay.ControlCentre -> sounds.play(Cue.HideRight)
            Overlay.Switcher -> sounds.play(Cue.SwitcherClose)
            else -> sounds.play(Cue.Back)
        }
        // Home leaves Search and Prism's keyboard too (they draw over Home whatever the overlay).
        val typing = keyboard != null || searchOpen
        if (typing && dialog == null && contextMenu == null && movingId == null && overlay == Overlay.None) sounds.play(Cue.Back)
        if (typing) closeKeyboard()
        dialog = null
        contextMenu = null
        movingId = null
        if (overlay == Overlay.Switcher) {
            closeSwitcherToHome(); return
        }
        overlay = Overlay.None
        openFolder = null
        settingsStack = listOf(SettingsPage.Root)
        if (zone != Zone.Grid || row > 0) {
            zone = Zone.Grid; row = 0; col = heroAppIndex
        }
    }

    fun onResume() {
        countScreenTime()
        isResumed = true
        homeVisible = true
        noteInteraction()
        resumeTick++
        refreshPro()
        if (BuildConfig.PLAY) com.prismtv.launcher.data.ProStore.refresh()
        refreshRecommendations()
        maybeAutoCheckForUpdate()
        refreshWeather()
        refreshNetwork()
        // Picks up Notification access granted while Prism was away.
        com.prismtv.launcher.system.MediaWatcher.start(ctx)
        maybeAskWhoAtStartUp()
        maybeOpenAtStartup()
        if (pickerOnResume) {
            pickerOnResume = false
            showProfilePicker()
        }
        val shot = com.prismtv.launcher.system.AppSnapshots
        val back = pendingReturn
        if (back != null) {
            pendingReturn = null
            val still = shot.takeLive(back.app.packageName)?.asImageBitmap()
            launchAnim = back.copy(reverse = true, id = System.nanoTime(), still = still)
        } else if (launchAnim == null && overlay == Overlay.None && dialog == null) {
            // Home pressed in an app Prism TV didn't open: it still shrinks into its icon if that's in the dock.
            val pkg = com.prismtv.launcher.system.Foreground.lastPackage
            val idx = items.take(prefs.columns).indexOfFirst { it is AppItem && it.app.packageName == pkg }
            val app = (items.getOrNull(idx) as? AppItem)?.app
            if (app != null) shot.takeLive(pkg)?.let { bmp ->
                launchAnim = LaunchAnim(app, tileAnchor(idx), null, reverse = true, still = bmp.asImageBitmap())
            }
        }
        if (pendingClose.isNotEmpty() && !closeFlushing) scheduleClose()
        maybeStartShizuku()
        // Back from an app: the App Switcher's list has changed.
        if (!prefs.demoContent) refreshRecents()
    }

    private fun rowSize(r: Int): Int {
        val cols = prefs.columns
        return (items.size - r * cols).coerceIn(0, cols)
    }

    private fun rowCount(): Int = (items.size + prefs.columns - 1) / prefs.columns

    private fun homeKey(k: NavKey): Boolean {
        val cols = prefs.columns
        if (movingId != null) {
            when (k) {
                NavKey.Left -> moveFocused(-1)
                NavKey.Right -> moveFocused(1)
                NavKey.Up -> moveFocused(-cols)
                NavKey.Down -> moveFocused(cols)
                NavKey.Select, NavKey.LongSelect, NavKey.Menu -> movingId = null
                NavKey.PlayPause -> Unit
            }
            return true
        }
        when (zone) {
            Zone.Grid -> when (k) {
                NavKey.Up -> when {
                    row == 1 && belowRows.isNotEmpty() -> enterWidget(aboveRows.size + belowRows.size - 1, HomeLayout.x(cols, col))
                    row > 0 -> { row--; col = col.coerceAtMost(rowSize(row) - 1); if (row == 0) setDockCol(col) }
                    aboveRows.isNotEmpty() -> enterWidget(aboveRows.size - 1, HomeLayout.x(cols, col))
                    prefs.heroEnabled -> { zone = Zone.Hero; heroButton = 0 }
                    else -> { zone = Zone.TopBar; topIndex = 0 }
                }
                NavKey.Down -> when {
                    row == 0 && belowRows.isNotEmpty() -> enterWidget(aboveRows.size, HomeLayout.x(cols, col))
                    row < rowCount() - 1 -> { row++; col = col.coerceAtMost(rowSize(row) - 1) }
                }
                NavKey.Left -> if (col > 0) {
                    col--
                    if (row == 0) setDockCol(col)
                } else {
                    openSidebar()
                }
                NavKey.Right -> if (col < rowSize(row) - 1) { col++; if (row == 0) setDockCol(col) }
                NavKey.Select -> items.getOrNull(row * cols + col)?.let { activateItem(it, row * cols + col) }
                NavKey.LongSelect -> {
                    val i = row * cols + col
                    items.getOrNull(i)?.let { openContextMenu(it, tileAnchor(i), null) }
                }
                NavKey.Menu -> openControlCentre()
                NavKey.PlayPause -> if (row == 0) playHero()
            }
            Zone.Widget -> widgetKey(k)
            Zone.Hero -> when (k) {
                NavKey.Up -> { zone = Zone.TopBar; topIndex = 0 }
                NavKey.Down -> { zone = Zone.Grid; row = 0; col = heroAppIndex }
                NavKey.Left -> if (heroButton > 0) heroButton-- else stepHero(-1)
                NavKey.Right -> if (heroButton < 1) heroButton++ else stepHero(1)
                NavKey.Select -> if (currentHeroItem?.media == true) {
                    // Now Playing: play/pause, and next.
                    if (heroButton == 0) com.prismtv.launcher.system.MediaWatcher.playPause() else com.prismtv.launcher.system.MediaWatcher.next()
                    cue(Cue.Toggle)
                } else if (heroButton == 0) playHero() else showHeroInfo()
                NavKey.PlayPause -> playHero()
                NavKey.LongSelect -> Unit
                NavKey.Menu -> openControlCentre()
            }
            Zone.TopBar -> when (k) {
                NavKey.Left -> topIndex = 0
                NavKey.Right -> topIndex = 1
                NavKey.Down -> if (prefs.heroEnabled) zone = Zone.Hero else { zone = Zone.Grid; row = 0 }
                NavKey.Select -> if (topIndex == 0) openControlCentre(toNotifications = true) else openSettings()
                NavKey.Menu -> openControlCentre()
                else -> Unit
            }
        }
        return true
    }

    /** Focus widget row [index] at the card nearest [x] (dp on the Home Screen). */
    private fun enterWidget(index: Int, x: Float) {
        val r = widgetRowAt(index) ?: return
        zone = Zone.Widget
        widgetIndex = index
        val first = WidgetLayout.firstVisible(0, r.items.size)
        val c = ((x - HomeLayout.left()) / (WidgetLayout.CardW + WidgetLayout.CardGap)).toInt() + first
        widgetCol = c.coerceIn(0, r.items.size - 1)
        if (index < aboveRows.size) heroIndex = 0
    }

    private fun widgetCardX(index: Int, c: Int): Float {
        val n = widgetRowAt(index)?.items?.size ?: 0
        val first = WidgetLayout.firstVisible(c, n)
        return HomeLayout.left() + (c - first) * (WidgetLayout.CardW + WidgetLayout.CardGap)
    }

    private fun widgetKey(k: NavKey) {
        val r = widgetRowAt(widgetIndex) ?: run { zone = Zone.Grid; row = 0; return }
        val above = aboveRows.size
        val x = widgetCardX(widgetIndex, widgetCol) + WidgetLayout.CardW / 2f
        val cols = prefs.columns
        fun toGrid(targetRow: Int) {
            zone = Zone.Grid
            row = targetRow
            val c = ((x - HomeLayout.left()) / (HomeLayout.iconW(cols) + HomeLayout.Gap)).toInt()
            col = c.coerceIn(0, (rowSize(targetRow) - 1).coerceAtLeast(0))
            if (targetRow == 0) setDockCol(col)
        }
        when (k) {
            NavKey.Left -> if (widgetCol > 0) widgetCol--
            NavKey.Right -> if (widgetCol < r.items.size - 1) widgetCol++
            NavKey.Up -> when {
                widgetIndex == above -> toGrid(0)            // first row below the dock
                widgetIndex > 0 -> enterWidget(widgetIndex - 1, x)
                prefs.heroEnabled -> { zone = Zone.Hero; heroButton = 0 }
                else -> { zone = Zone.TopBar; topIndex = 0 }
            }
            NavKey.Down -> when {
                widgetIndex == above - 1 -> toGrid(0)        // last row above the dock
                widgetIndex < above + belowRows.size - 1 -> enterWidget(widgetIndex + 1, x)
                rowCount() > 1 -> toGrid(1)
            }
            NavKey.Select, NavKey.PlayPause -> r.items.getOrNull(widgetCol)?.let { openWidgetItem(it) }
            NavKey.LongSelect -> r.items.getOrNull(widgetCol)?.let { item ->
                DeepLinks.details(ctx, item.intentUri)?.let { SystemActions.start(ctx, it) } ?: openWidgetItem(item)
            }
            NavKey.Menu -> openControlCentre()
        }
    }

    private fun openWidgetItem(item: HeroItem) {
        if (!prefs.demoContent && screenTimeBlocks(null)) return
        val uri = item.intentUri
        if (uri != null && SystemActions.openUri(ctx, uri)) {
            allApps.firstOrNull { it.id == item.appId }?.let { noteRecent(it.packageName) }
            return
        }
        allApps.firstOrNull { it.id == item.appId }?.let { app ->
            val idx = items.indexOfFirst { it is AppItem && it.app.id == app.id }
            startLaunch(app, if (idx >= 0) tileAnchor(idx) else SwitcherLayout.focusedRect())
        }
    }

    private fun setDockCol(c: Int) {
        if (heroAppIndex != c) {
            heroAppIndex = c
            heroIndex = 0
        }
    }

    private fun stepHero(d: Int) {
        val n = currentHeroItems.size
        if (n > 1) heroIndex = (heroIndex + d + n) % n
    }

    private fun activateItem(item: GridItem, index: Int) {
        when (item) {
            is FolderItem -> {
                folderAnchor = tileAnchor(index)
                openFolder = item
                folderFocus = 0
                overlay = Overlay.Folder
            }
            is AppItem -> startLaunch(item.app, tileAnchor(index))
        }
    }

    private fun openApp(item: GridItem?) {
        val idx = items.indexOf(item ?: return)
        if (idx >= 0) activateItem(item, idx)
    }

    /**
     * Info on the Top Shelf: the title's details (meta, badges, description) in a sheet, with Play and
     * Open App. With nothing to show, it goes straight to the title in its app.
     */
    private fun showHeroInfo() {
        val hero = currentHeroItem
        DeepLinks.details(ctx, hero?.intentUri)?.let { details ->
            if (SystemActions.start(ctx, details)) return
        }
        if (hero == null || (hero.description.isBlank() && hero.meta.isBlank() && hero.badges.isEmpty())) {
            playHero()
            return
        }
        val appLabel = (dockItem as? AppItem)?.app?.label ?: hero.appLabel
        // As on tvOS: details line, then the format chips, then the synopsis.
        dialog = DialogSpec(
            hero.title,
            hero.description.trim(),
            listOf(
                DialogButton("Play") { playHero() },
                DialogButton("Open $appLabel") { openApp(dockItem) },
                DialogButton("Close") {},
            ),
            subtitle = hero.meta.trim(),
            badges = hero.badges,
        )
        dialogFocus = 0
    }

    private fun playHero() {
        val hero = currentHeroItem
        if (!prefs.demoContent && screenTimeBlocks(null)) return
        val intent = hero?.intentUri
        if (intent != null && SystemActions.openUri(ctx, intent)) return
        openApp(dockItem)
    }

    // ================================================================= launching

    fun startLaunch(app: LauncherApp, from: Rect) {
        com.prismtv.launcher.system.MemTrace.count("app launches")
        if (app.demo == null && screenTimeBlocks(app)) return
        // Apps you just swiped away close first, so (say) their music doesn't play on under the next app.
        if (pendingClose.isNotEmpty() && !closeFlushing) {
            closeTimer?.cancel()
            viewModelScope.launch {
                flushCloses()
                startLaunch(app, from)
            }
            return
        }
        launchFailed = false
        launchAnim = LaunchAnim(app, from, heroesFor(app).firstOrNull()?.art)
    }

    /**
     * Called part-way through the zoom. The app is started with its window scaling up from the icon's
     * current rect ([source] pixels), so the real app continues the zoom like tvOS.
     */
    fun onLaunchMidpoint(anim: LaunchAnim, source: android.view.View?, rect: android.graphics.Rect?) {
        if (anim.reverse || anim.app.demo != null) return
        val app = anim.app
        val intent = SystemActions.launchIntent(ctx, app)
        val options = if (source != null && rect != null) SystemActions.scaleUpOptions(source, rect) else null
        val starter = source?.context ?: ctx
        if (intent != null && SystemActions.start(starter, intent, options ?: SystemActions.fadeOptions(ctx))) {
            noteRecent(app.packageName)
            pendingReturn = anim
            launchFailed = false
        } else {
            launchFailed = true
        }
    }

    /** Called once the zoom has finished (in either direction). */
    fun onLaunchZoomComplete(anim: LaunchAnim) {
        if (anim.reverse) {
            launchAnim = null
            return
        }
        val app = anim.app
        when {
            app.demo != null -> {
                showBanner(app.label, "Demo app — install apps to see them here.", Icons.Rounded.Info)
                viewModelScope.launch {
                    delay(450)
                    launchAnim = anim.copy(reverse = true, id = System.nanoTime())
                }
            }
            launchFailed -> {
                showBanner(app.label, "This app couldn't be opened.", Icons.Rounded.Info, Cue.Error)
                launchAnim = anim.copy(reverse = true, id = System.nanoTime())
            }
            else -> {
                overlay = Overlay.None
                openFolder = null
            }
        }
    }

    // ================================================================= folder

    private fun folderKey(k: NavKey): Boolean {
        val folder = openFolder ?: run { overlay = Overlay.None; return true }
        val n = folder.apps.size
        val c = FolderLayout.Cols
        when (k) {
            NavKey.Left -> if (folderFocus % c > 0) folderFocus--
            NavKey.Right -> if (folderFocus % c < c - 1 && folderFocus + 1 < n) folderFocus++
            NavKey.Up -> if (folderFocus >= c) folderFocus -= c
            NavKey.Down -> when {
                folderFocus + c < n -> folderFocus += c
                (folderFocus / c + 1) * c < n -> folderFocus = n - 1
            }
            NavKey.Select -> folder.apps.getOrNull(folderFocus)?.let { startLaunch(it, FolderLayout.tileRect(folderFocus, FolderLayout.firstRow(folderFocus, folder.apps.size).toFloat())) }
            NavKey.LongSelect -> folder.apps.getOrNull(folderFocus)?.let {
                openContextMenu(AppItem(it), FolderLayout.tileRect(folderFocus, FolderLayout.firstRow(folderFocus, folder.apps.size).toFloat()), folder)
            }
            NavKey.Menu -> openControlCentre()
            NavKey.PlayPause -> Unit
        }
        return true
    }

    // ================================================================= sidebar

    /** The app store for the sidebar, with its real (coloured) icon. Looked up once the apps are loaded. */
    val storeApp: LauncherApp? by derivedStateOf {
        val stores = listOf("com.android.vending", "com.amazon.venezia", "cm.aptoidetv.pt")
        val listed = realApps.filter { it.packageName in stores }.minByOrNull { stores.indexOf(it.packageName) }
        listed ?: stores.firstNotNullOfOrNull { pkg -> storeFromPackage(pkg) }
    }

    /** Google TV hides Play Store from the app list; build its entry from the package itself. */
    private fun storeFromPackage(pkg: String): LauncherApp? = try {
        val pm = ctx.packageManager
        val info = pm.getApplicationInfo(pkg, 0)
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (intent == null) null else {
            val icon = pm.getApplicationIcon(info).toBitmap(144, 144)
            LauncherApp(
                id = "$pkg/${intent.component?.className}",
                label = pm.getApplicationLabel(info).toString(),
                packageName = pkg,
                activityName = intent.component?.className,
                icon = icon.asImageBitmap(),
                isTvApp = pm.getLeanbackLaunchIntentForPackage(pkg) != null,
            )
        }
    } catch (_: Throwable) {
        null
    }

    /** Sidebar entries; the store only when the box has one. */
    val sidebarEntries: List<SidebarEntry> by derivedStateOf {
        val restricted = activeProfileSpec.restricted
        val store = storeApp
        SidebarEntry.entries.filter {
            when (it) {
                SidebarEntry.Store -> store != null && store.id !in blockedApps
                // A profile with restrictions doesn't get a way round them.
                SidebarEntry.SystemSettings -> !restricted
                else -> true
            }
        }
    }

    fun openSidebar() {
        sidebarFocus = sidebarEntries.indexOf(SidebarEntry.Home).coerceAtLeast(0)
        overlay = Overlay.Sidebar
    }

    private fun sidebarKey(k: NavKey): Boolean {
        val n = sidebarEntries.size
        when (k) {
            NavKey.Up -> sidebarFocus = (sidebarFocus - 1).coerceAtLeast(0)
            NavKey.Down -> sidebarFocus = (sidebarFocus + 1).coerceAtMost(n - 1)
            NavKey.Right, NavKey.Menu -> closeOverlay()
            NavKey.Select -> sidebarEntries.getOrNull(sidebarFocus)?.let { sidebarSelect(it) }
            NavKey.Left, NavKey.LongSelect, NavKey.PlayPause -> Unit
        }
        return true
    }

    private fun sidebarSelect(e: SidebarEntry) {
        when (e) {
            SidebarEntry.Search -> { closeOverlay(); openSearch() }
            SidebarEntry.Home -> { closeOverlay(); zone = Zone.Grid; row = 0; col = 0; setDockCol(0) }
            SidebarEntry.Recents -> openSwitcher()
            SidebarEntry.PrismSettings -> openSettings()
            SidebarEntry.SystemSettings -> { closeOverlay(); SystemActions.open(ctx, Settings.ACTION_SETTINGS) }
            SidebarEntry.Store -> storeApp?.let { app ->
                closeOverlay()
                val intent = SystemActions.launchIntent(ctx, app)
                if (intent == null || !SystemActions.start(ctx, intent)) showBanner(app.label, "The store couldn't be opened.", Icons.Rounded.Info, Cue.Error)
                else noteRecent(app.packageName)
            }
        }
    }

    // ================================================================= control centre

    fun openControlCentre(toNotifications: Boolean = false) {
        ccFocus = if (toNotifications && notifications.isNotEmpty()) CC.Notification else CC.Power
        if (overlay != Overlay.ControlCentre) cue(Cue.RevealRight)
        overlay = Overlay.ControlCentre
    }

    private val ccNav: Map<Int, IntArray> = mapOf(
        // up, down, left, right  (-1 = none)
        CC.Profile to intArrayOf(-1, CC.WiFi, -1, -1),
        CC.Power to intArrayOf(CC.Profile, CC.Sleep, -1, CC.WiFi),
        CC.WiFi to intArrayOf(CC.Profile, CC.Bluetooth, CC.Power, -1),
        CC.Bluetooth to intArrayOf(CC.WiFi, CC.Sound, CC.Power, -1),
        CC.Sleep to intArrayOf(CC.Power, CC.Picture, -1, CC.Sound),
        CC.Sound to intArrayOf(CC.Bluetooth, CC.Access, CC.Sleep, -1),
        CC.Picture to intArrayOf(CC.Sleep, CC.Notification, -1, CC.Memory),
        CC.Memory to intArrayOf(CC.Sleep, CC.Notification, CC.Picture, CC.Access),
        CC.Access to intArrayOf(CC.Sound, CC.Notification, CC.Memory, CC.Screensaver),
        CC.Screensaver to intArrayOf(CC.Sound, CC.Notification, CC.Access, -1),
    )

    private fun ccKey(k: NavKey): Boolean {
        val shown = ccNotificationsShown
        val playing = nowPlaying != null
        // The remote's play/pause key controls whatever is playing, from anywhere in Control Centre.
        if (k == NavKey.PlayPause && playing) {
            com.prismtv.launcher.system.MediaWatcher.playPause()
            cue(Cue.Toggle)
            return true
        }
        if (ccFocus in CC.NowPrev..CC.NowNext) {
            if (!playing) {
                ccFocus = CC.Memory
                return true
            }
            when (k) {
                NavKey.Up -> ccFocus = when (ccFocus) { CC.NowPrev -> CC.Picture; CC.NowNext -> CC.Screensaver; else -> CC.Memory }
                NavKey.Down -> if (shown > 0) ccFocus = CC.Notification
                NavKey.Left -> if (ccFocus > CC.NowPrev) ccFocus--
                NavKey.Right -> if (ccFocus < CC.NowNext) ccFocus++
                NavKey.Select -> {
                    when (ccFocus) {
                        CC.NowPrev -> com.prismtv.launcher.system.MediaWatcher.previous()
                        CC.NowNext -> com.prismtv.launcher.system.MediaWatcher.next()
                        else -> com.prismtv.launcher.system.MediaWatcher.playPause()
                    }
                    cue(Cue.Toggle)
                }
                // Hold OK to go to the app that's playing.
                NavKey.LongSelect -> {
                    closeOverlay()
                    if (!com.prismtv.launcher.system.MediaWatcher.open(ctx)) {
                        nowPlaying?.let { showBanner(it.appLabel, "This app couldn't be opened.", Icons.Rounded.Info, Cue.Error) }
                    }
                }
                NavKey.Menu -> closeOverlay()
                NavKey.PlayPause -> Unit
            }
            return true
        }
        // The ✕ above the notifications: Clear All.
        if (ccFocus == CC.ClearAll) {
            when (k) {
                NavKey.Up -> ccFocus = if (playing) CC.NowNext else CC.Screensaver
                NavKey.Down -> if (shown > 0) ccFocus = CC.Notification
                NavKey.Select -> clearAllNotifications()
                NavKey.Menu -> closeOverlay()
                else -> Unit
            }
            return true
        }
        // A notification slid aside, its Clear button focused (as when swiping one on iOS).
        if (ccFocus >= CC.NotificationClear) {
            val i = ccFocus - CC.NotificationClear
            when (k) {
                NavKey.Left, NavKey.Right -> ccFocus = CC.Notification + i
                NavKey.Up -> ccFocus = if (i > 0) CC.Notification + i - 1 else CC.ClearAll
                NavKey.Down -> if (i < shown - 1) ccFocus = CC.Notification + i + 1
                NavKey.Select, NavKey.LongSelect -> notifications.getOrNull(i)?.let { n ->
                    PrismNotificationListener.dismiss(n)
                    notifications = notifications.filterNot { it.key == n.key }
                    cue(Cue.Delete)
                    ccFocus = CC.Notification + i
                    keepCcFocusOnNotifications()
                }
                NavKey.Menu -> closeOverlay()
                NavKey.PlayPause -> Unit
            }
            return true
        }
        if (ccFocus >= CC.Notification) {
            val i = ccFocus - CC.Notification
            when (k) {
                NavKey.Up -> ccFocus = if (i > 0) ccFocus - 1 else CC.ClearAll
                NavKey.Down -> if (i < shown - 1) ccFocus++
                // Left or Right slides the notification aside to show Clear.
                NavKey.Left, NavKey.Right -> ccFocus = CC.NotificationClear + i
                NavKey.Select -> notifications.getOrNull(i)?.let { n ->
                    closeOverlay()
                    if (!PrismNotificationListener.open(ctx, n)) showBanner(n.appLabel, "This notification couldn't be opened.", Icons.Rounded.Info, Cue.Error)
                }
                NavKey.LongSelect -> notifications.getOrNull(i)?.let { n ->
                    PrismNotificationListener.dismiss(n)
                    notifications = notifications.filterNot { it.key == n.key }
                    cue(Cue.Delete)
                    keepCcFocusOnNotifications()
                }
                NavKey.Menu -> closeOverlay()
                else -> Unit
            }
            return true
        }
        val nav = ccNav[ccFocus] ?: intArrayOf(-1, -1, -1, -1)
        val next = when (k) {
            NavKey.Up -> nav[0]
            NavKey.Down -> nav[1]
            NavKey.Left -> nav[2]
            NavKey.Right -> nav[3]
            NavKey.Select -> { ccSelect(ccFocus); -1 }
            NavKey.Menu -> { closeOverlay(); -1 }
            NavKey.LongSelect, NavKey.PlayPause -> -1
        }
        // Below the circles: Now Playing's buttons when something's playing, then the notifications.
        if (next == CC.Notification && playing) {
            ccFocus = when (ccFocus) { CC.Picture -> CC.NowPrev; CC.Screensaver -> CC.NowNext; else -> CC.NowPlay }
            return true
        }
        if (next == CC.Notification && shown == 0) return true
        if (next >= 0) ccFocus = next
        return true
    }

    /** After notifications come or go: focus stays on a row that still exists (or leaves the list). */
    private fun keepCcFocusOnNotifications() {
        val shown = ccNotificationsShown
        val i = CC.noteIndex(ccFocus)
        when {
            shown == 0 && (i != null || ccFocus == CC.ClearAll) -> ccFocus = if (nowPlaying != null) CC.NowPlay else CC.Memory
            i != null && i >= shown -> ccFocus = (if (ccFocus >= CC.NotificationClear) CC.NotificationClear else CC.Notification) + shown - 1
        }
    }

    /** Control Centre's ✕: clears every notification, as on iOS. */
    private fun clearAllNotifications() {
        val all = notifications
        if (all.isEmpty()) return
        PrismNotificationListener.dismissAll(all)
        notifications = emptyList()
        cue(Cue.Clear)
        keepCcFocusOnNotifications()
    }

    private fun ccSelect(i: Int) {
        when (i) {
            CC.Profile -> if (profileList.size >= 2) {
                closeOverlay()
                showProfilePicker()
            } else openProfilePage()
            CC.Power -> if (PrismAccessibilityService.powerDialog()) closeOverlay() else askForPowerAccess()
            CC.WiFi -> SystemActions.open(ctx, Settings.ACTION_WIFI_SETTINGS)
            CC.Bluetooth -> SystemActions.open(ctx, Settings.ACTION_BLUETOOTH_SETTINGS)
            CC.Sleep -> cycleSleepTimer()
            CC.Picture -> SystemActions.open(ctx, Settings.ACTION_DISPLAY_SETTINGS)
            CC.Sound -> SystemActions.open(ctx, Settings.ACTION_SOUND_SETTINGS)
            CC.Memory -> freeUpMemory()
            CC.Access -> SystemActions.open(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS)
            CC.Screensaver -> startScreensaver()
        }
    }

    /**
     * Control Centre › Free Up Memory: closes the App Switcher's apps (completely, where Closing Apps
     * can), stops every installed app's background processes, has what's still running trim its memory
     * (with Shizuku or root) and lets go of the artwork Prism TV keeps for later. What's on screen, what's
     * playing, the keyboard and the Assistant stay as they are.
     */
    private fun freeUpMemory() {
        if (freeingMemory) return
        freeingMemory = true
        memoryCaptionJob?.cancel()
        memoryCaption = "Freeing Memory…"
        viewModelScope.launch {
            val t0 = SystemClock.elapsedRealtime()
            val cleaner = com.prismtv.launcher.system.MemoryCleaner
            val availBefore = withContext(Dispatchers.IO) { cleaner.available(ctx) }
            // Memory comes back as each process ends, and some of it is taken again as Android restarts the
            // services it keeps running (Google Play services, the recommendations service…). The figure
            // is the best reading while it works and just after: what Free Up Memory gave back.
            val best = java.util.concurrent.atomic.AtomicLong(availBefore)
            var samples = 0
            val sampler = launch(Dispatchers.IO) {
                while (isActive) {
                    best.accumulateAndGet(cleaner.available(ctx)) { a, b -> maxOf(a, b) }
                    samples++
                    delay(150)
                }
            }
            // Closing apps one by one through Shizuku can take a moment: say so (Control Centre's caption
            // already does, under the button).
            val working = launch {
                delay(700)
                if (!memoryCaptionShown()) showBanner("Free Up Memory", "Freeing memory…", Icons.Rounded.Memory, null)
            }
            try {
                // The App Switcher's apps are closed completely when Closing Apps can (and leave the switcher).
                var closedApps = 0
                if (closeMethod() != com.prismtv.launcher.system.AppCloser.Method.MemoryOnly && !closeFlushing) {
                    val open = withContext(Dispatchers.IO) { recentsRepo.recentPackages().take(8) }
                        .mapNotNull { pkg -> realApps.firstOrNull { it.packageName == pkg } }
                        .filter { it.id !in blockedApps && !com.prismtv.launcher.data.TvInputs.isInput(it) }
                        .distinctBy { it.packageName }
                    if (open.isNotEmpty()) {
                        closeFlushing = true
                        val r = try {
                            withContext(kotlinx.coroutines.NonCancellable) {
                                com.prismtv.launcher.system.AppCloser.close(ctx, open.map { it.packageName to it.label }, true, prefs.rootClose)
                            }
                        } finally {
                            closeFlushing = false
                        }
                        lastCloseResult = r
                        r.stopped.forEach(recentsRepo::remove)
                        recentPackagesCache = recentPackagesCache?.filterNot { it in r.stopped }
                        closedApps = r.closed.size
                    }
                }
                val playing = nowPlaying?.pkg
                val keep = withContext(Dispatchers.IO) { cleaner.keep(ctx, playing) }
                val asked = withContext(Dispatchers.IO) { cleaner.killBackground(ctx, keep) }
                val shell: ((String) -> Pair<Int?, String>?)? = when {
                    com.prismtv.launcher.system.ShizukuShell.ready() -> com.prismtv.launcher.system.ShizukuShell::exec
                    prefs.rootClose && com.prismtv.launcher.system.RootShell.present() -> com.prismtv.launcher.system.RootShell::exec
                    else -> null
                }
                val trims = shell?.let { sh ->
                    viewModelScope.launch(Dispatchers.IO) { sh(cleaner.shellScript(ctx.packageName)) }
                }
                // Prism TV's own: artwork kept for later goes (what's on screen stays), then the garbage
                // collector frees it and whatever else nothing holds any more.
                val held = artRepo.releaseUnused()
                withContext(Dispatchers.IO) { repeat(2) { Runtime.getRuntime().gc(); System.runFinalization() } }
                trims?.let { kotlinx.coroutines.withTimeoutOrNull(8_000) { it.join() } }
                delay(1_200) // Android finishes tearing down what was stopped
                sampler.cancel()
                val availAfter = withContext(Dispatchers.IO) { cleaner.available(ctx) }
                val peak = maxOf(best.get(), availAfter)
                val freed = (peak - availBefore).coerceAtLeast(0L)
                android.util.Log.i(
                    "PrismFree",
                    "closed $closedApps, asked $asked packages, shell ${if (shell == null) "none" else "trims"}, " +
                        "Prism artwork let go ${held / 1_048_576} MB, available ${availBefore / 1_048_576} → best " +
                        "${peak / 1_048_576} → ${availAfter / 1_048_576} MB after ($samples readings), freed " +
                        "${freed / 1_048_576} MB in ${SystemClock.elapsedRealtime() - t0} ms",
                )
                com.prismtv.launcher.system.MemTrace.count("free up memory")
                working.cancel()
                val freedText = when {
                    freed >= 1_073_741_824L -> "Freed %.1f GB".format(freed / 1_073_741_824.0)
                    freed >= 1_048_576L -> "Freed ${freed / 1_048_576} MB"
                    else -> "Nothing more to free"
                }
                val availText = "%.1f GB available".format(availAfter / 1_073_741_824.0)
                val closedText = when {
                    closedApps == 1 -> "Closed 1 app"
                    closedApps > 1 -> "Closed $closedApps apps"
                    else -> null
                }
                if (memoryCaptionShown()) {
                    // Control Centre is open on Free Up Memory: the result shows under the button, where
                    // you're looking (a banner would sit over the tiles).
                    sounds.play(Cue.Clear)
                    val freedCaption = if (freed >= 1_048_576L) freedText else "Nothing to Free"
                    showMemoryCaption("$freedCaption · " + "%.1f GB Available".format(availAfter / 1_073_741_824.0))
                } else {
                    memoryCaption = null
                    showBanner(
                        "Free Up Memory",
                        listOfNotNull(closedText, freedText, availText).joinToString(" · "),
                        Icons.Rounded.Memory,
                        Cue.Clear,
                    )
                }
            } finally {
                sampler.cancel()
                working.cancel()
                if (memoryCaption == "Freeing Memory…") memoryCaption = null
                freeingMemory = false
            }
        }
    }

    /** The TV's network, for the time pill and Control Centre (kept current by [networkWatch] and on resume). */
    var network by mutableStateOf(SystemActions.network(app))
        private set
    private val networkMain = android.os.Handler(android.os.Looper.getMainLooper())
    private val networkWatch = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(n: android.net.Network) = refreshNetwork()
        override fun onLost(n: android.net.Network) = refreshNetwork()
        override fun onCapabilitiesChanged(n: android.net.Network, caps: android.net.NetworkCapabilities) = refreshNetwork()
    }

    /**
     * Re-reads the network on the main thread (a callback burst becomes one read). Only while Prism is on
     * screen: Wi-Fi signal changes arrive every few seconds, and [onResume] reads it again anyway.
     */
    fun refreshNetwork() {
        if (!homeVisible) return
        networkMain.removeCallbacks(readNetwork)
        networkMain.postDelayed(readNetwork, 150)
    }
    private var debugNetwork: com.prismtv.launcher.system.NetStatus? = null
    private val readNetwork = Runnable {
        val now = debugNetwork ?: SystemActions.network(ctx)
        if (now != network) network = now
    }

    /**
     * Control Centre's caption under Free Up Memory while it works ("Freeing Memory…") and for a few seconds
     * after ("Freed 412 MB · 1.6 GB Available"); null shows the usual "Free Up Memory".
     */
    var memoryCaption by mutableStateOf<String?>(null)
        private set
    private var memoryCaptionJob: kotlinx.coroutines.Job? = null

    private fun memoryCaptionShown() = overlay == Overlay.ControlCentre && ccFocus == CC.Memory

    private fun showMemoryCaption(text: String) {
        memoryCaption = text
        memoryCaptionJob?.cancel()
        memoryCaptionJob = viewModelScope.launch {
            // Until focus leaves the button or Control Centre closes, for up to 8 seconds.
            val until = SystemClock.elapsedRealtime() + 8_000
            while (SystemClock.elapsedRealtime() < until && memoryCaptionShown()) delay(200)
            if (memoryCaption == text) memoryCaption = null
        }
    }
    private var freeingMemory = false

    /**
     * Shows the aerials. [auto] = started by Start After on an idle Home Screen: whatever was open
     * stays as it was for when you wake it, and the screensaver lets the TV's own sleep timer run.
     */
    fun startScreensaver(auto: Boolean = false) {
        if (!auto) closeOverlay()
        SystemActions.start(ctx, Intent(ctx, AerialActivity::class.java).putExtra(AerialActivity.EXTRA_AUTO, auto))
    }

    // ---------------------------------------------------------------- idle → screensaver

    private var lastInteraction = SystemClock.elapsedRealtime()
    private var isResumed = false
    /** Prism is on screen (not behind an app). */
    var homeVisible by mutableStateOf(false)
        private set

    /** Any remote button (MainActivity also reports presses that never reach [onKey]). */
    fun noteInteraction() {
        lastInteraction = SystemClock.elapsedRealtime()
    }

    fun onPause() {
        isResumed = false
        homeVisible = false
        screenTimeMark = SystemClock.elapsedRealtime()
    }

    private fun startupAppLabel(): String =
        prefs.startupApp.takeIf { it.isNotEmpty() }?.let { id -> allApps.firstOrNull { it.id == id }?.label } ?: "Off"

    private fun startupDelayLabel(): String = when (val s = prefs.startupDelay) {
        0 -> "None"
        else -> "$s Seconds"
    }

    /**
     * Settings › Open at Startup: the first time Prism appears after the TV starts, open the chosen app
     * (once per start, only in the first few minutes, and not if you've already moved on).
     */
    /**
     * Who's Watching? once after the TV starts (Settings › Profiles › Who's Watching at Start-Up): on
     * Prism's first resume within five minutes of boot, once per boot (not after Prism restarts).
     */
    private fun maybeAskWhoAtStartUp() {
        if (!prefs.profilePickerOnStart || profileList.size < 2 || debugDriven) return
        val uptime = SystemClock.elapsedRealtime()
        if (uptime > 5 * 60_000L) return
        val bootedAt = System.currentTimeMillis() - uptime
        val sp = ctx.getSharedPreferences("prism_prefs", android.content.Context.MODE_PRIVATE)
        if (kotlin.math.abs(sp.getLong("pickerBootAt", 0L) - bootedAt) < 60_000L) return
        sp.edit().putLong("pickerBootAt", bootedAt).apply()
        pickerOnResume = true
    }

    private fun maybeOpenAtStartup() {
        val id = prefs.startupApp
        if (id.isEmpty() || debugDriven) return
        val uptime = SystemClock.elapsedRealtime()
        if (uptime > 5 * 60_000L) return
        val bootedAt = System.currentTimeMillis() - uptime
        val sp = ctx.getSharedPreferences("prism_prefs", android.content.Context.MODE_PRIVATE)
        if (kotlin.math.abs(sp.getLong("startupAppBootAt", 0L) - bootedAt) < 60_000L) return
        sp.edit().putLong("startupAppBootAt", bootedAt).apply()
        viewModelScope.launch {
            // Apps load in the background just after startup; give them a moment.
            var waited = 0
            while (allApps.none { it.id == id } && waited < 15_000) { delay(250); waited += 250 }
            // Who's Watching? first: the app opens once someone's chosen (and only if they may use it).
            while (profilePicker || passcode != null) delay(250)
            val app = allApps.firstOrNull { it.id == id } ?: return@launch
            if (app.id in blockedApps) return@launch
            val pressesBefore = lastInteraction
            delay(prefs.startupDelay * 1000L)
            // Someone picked up the remote while we waited, or Prism isn't on screen: leave it be.
            if (!isResumed || lastInteraction != pressesBefore || overlay != Overlay.None) return@launch
            val idx = items.indexOfFirst { it is com.prismtv.launcher.data.AppItem && it.app.id == app.id }
            startLaunch(app, if (idx >= 0) tileAnchor(idx) else SwitcherLayout.focusedRect(focusIsLast = true))
        }
    }

    /** Settings › Screensaver › Start After: aerials after that long without a button press on Home. */
    private fun watchForIdle() {
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                val minutes = prefs.screensaverAfter
                if (minutes <= 0 || !isResumed || debugDriven) continue
                // Music or a video playing counts as being watched: wait the full time after it stops.
                if (prefs.screensaverWaitForMedia && com.prismtv.launcher.system.MediaWatcher.isPlaying(ctx)) {
                    lastInteraction = SystemClock.elapsedRealtime()
                    continue
                }
                if (SystemClock.elapsedRealtime() - lastInteraction < minutes * 60_000L) continue
                // Never over something half-done.
                if (dialog != null || launchAnim != null || movingId != null || contextMenu != null || keyboard != null) continue
                noteInteraction()
                startScreensaver(auto = true)
            }
        }
    }

    private fun askForPowerAccess() {
        dialog = DialogSpec(
            "Turn On Power Controls",
            "To show the power menu and put your TV to sleep, turn on Prism TV Power & Home Button in Accessibility settings.",
            listOf(
                DialogButton("Set Up") { setUpPowerAndHome() },
                DialogButton("Not Now") {},
            ),
        )
        dialogFocus = 0
    }

    fun sleepLabel(): String = if (sleepMinutes == 0) "Off" else "$sleepMinutes min"

    private fun cycleSleepTimer() {
        val steps = listOf(0, 15, 30, 60, 90, 120)
        sleepMinutes = steps[(steps.indexOf(sleepMinutes) + 1) % steps.size]
        sleepJob?.cancel()
        if (sleepMinutes > 0) {
            val mins = sleepMinutes
            showBanner("Sleep Timer", "Your TV will sleep in $mins minutes.", Icons.Rounded.Timer, Cue.Toggle)
            sleepJob = viewModelScope.launch {
                delay(mins * 60_000L)
                sleepMinutes = 0
                if (!PrismAccessibilityService.sleep()) PrismAccessibilityService.powerDialog()
            }
        } else {
            showBanner("Sleep Timer", "Off", Icons.Rounded.Timer, Cue.Toggle)
        }
    }

    // ================================================================= switcher

    /**
     * Recent apps, most recent first. Reading Android's usage history can take a while, so it's read in
     * the background (whenever Prism TV comes back to the front) and the App Switcher opens from this.
     */
    private var recentPackagesCache: List<String>? = null
    private var recentsRefresh: Job? = null

    private fun refreshRecents(thenUpdateSwitcher: Boolean = false) {
        recentsRefresh?.cancel()
        recentsRefresh = viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) { recentsRepo.recentPackages() }
            val changed = fresh != recentPackagesCache
            recentPackagesCache = fresh
            // Opened while this was still being read (double-pressing Home in an app): show the app
            // just left, as long as nothing in the switcher has been touched yet.
            if (thenUpdateSwitcher && changed && overlay == Overlay.Switcher && dismissingKey == null &&
                switcherFocus == recents.lastIndex && !switcherClosingToHome
            ) {
                recents = recentEntries()
                switcherFocus = recents.lastIndex
            }
        }
    }

    private fun recentEntries(): List<RecentEntry> {
        val apps: List<LauncherApp> = if (prefs.demoContent) {
            listOf("demo:cinema", "demo:arcade", "demo:music", "demo:prism")
                .mapNotNull { id -> allApps.firstOrNull { it.id == id } }
        } else {
            val pkgs = recentPackagesCache ?: recentsRepo.recentPackages().also { recentPackagesCache = it }
            pkgs.take(8).reversed()
                .mapNotNull { pkg -> realApps.firstOrNull { it.packageName == pkg } }
                .filter { it.id !in blockedApps }
        }
        // Oldest on the left, most recent next to Home. With snapshots, each card shows the app as you
        // left it; apps without one (or showing protected video) keep their artwork.
        val snaps = prefs.appSnapshots && com.prismtv.launcher.system.AppSnapshots.supported()
        return apps.map { a ->
            val hero = heroesFor(a).firstOrNull()
            val snapPkg = if (a.demo != null) demoSnapshotPkg?.takeIf { a.id == "demo:cinema" } else a.packageName
            val snap = if (snaps && snapPkg != null) com.prismtv.launcher.system.AppSnapshots.art(ctx, snapPkg) else null
            RecentEntry(a.id, a, snap ?: hero?.art, hero?.title?.takeIf { it.isNotBlank() }, snapshot = snap != null)
        } + RecentEntry("home", null, null)
    }

    private fun noteRecent(pkg: String) {
        recentsRepo.record(pkg)
        recentPackagesCache = recentPackagesCache?.let { listOf(pkg) + it.filterNot { p -> p == pkg } }
    }

    fun openSwitcher() {
        dialog = null
        contextMenu = null
        openFolder = null
        val reading = recentsRefresh?.isActive == true
        recents = recentEntries()
        if (reading) refreshRecents(thenUpdateSwitcher = true)
        // Like tvOS: Home shrinks into the focused card; the last app is one press to the left.
        switcherFocus = recents.lastIndex
        switcherLift = false
        switcherClosingToHome = false
        dismissingKey = null
        overlay = Overlay.Switcher
        cue(Cue.SwitcherOpen)
        // The snapshot of the app you just left is still being saved: swap it in when it lands.
        if (prefs.appSnapshots) {
            val v = com.prismtv.launcher.system.AppSnapshots.version
            viewModelScope.launch {
                repeat(8) {
                    delay(200)
                    if (overlay != Overlay.Switcher) return@launch
                    if (com.prismtv.launcher.system.AppSnapshots.version != v && dismissingKey == null) {
                        val focusKey = recents.getOrNull(switcherFocus)?.key
                        recents = recentEntries()
                        switcherFocus = recents.indexOfFirst { it.key == focusKey }.takeIf { it >= 0 } ?: recents.lastIndex
                        return@launch
                    }
                }
            }
        }
    }

    private fun switcherKey(k: NavKey): Boolean {
        val n = recents.size
        when (k) {
            NavKey.Left -> if (switcherFocus > 0) switcherFocus--
            NavKey.Right -> if (switcherFocus < n - 1) switcherFocus++
            NavKey.Up -> dismissRecent()
            NavKey.Down, NavKey.LongSelect, NavKey.PlayPause -> Unit
            NavKey.Select -> {
                val e = recents.getOrNull(switcherFocus) ?: return true
                val app = e.app
                if (app == null) {
                    closeSwitcherToHome()
                    cue(Cue.SwitcherClose)
                } else startLaunch(app, SwitcherLayout.focusedRect())
            }
            NavKey.Menu -> closeSwitcherToHome()
        }
        return true
    }

    fun closeSwitcherToHome() {
        if (overlay != Overlay.Switcher) return
        switcherClosingToHome = true
        overlay = Overlay.None
    }

    private fun dismissRecent() {
        val e = recents.getOrNull(switcherFocus) ?: return
        val app = e.app ?: return // Home can't be closed
        if (dismissingKey != null) return
        dismissingKey = e.key
        cue(Cue.Dismiss)
        viewModelScope.launch {
            delay(320)
            recents = recents.filterNot { it.key == e.key }
            if (switcherFocus >= recents.size - 1 && switcherFocus > 0) switcherFocus--
            switcherFocus = switcherFocus.coerceIn(0, (recents.size - 1).coerceAtLeast(0))
            dismissingKey = null
            if (app.demo == null) {
                recentsRepo.remove(app.packageName)
                recentPackagesCache = recentPackagesCache?.filterNot { it == app.packageName }
                com.prismtv.launcher.system.AppSnapshots.delete(ctx, app.packageName)
                queueClose(app)
            }
        }
    }

    // ================================================================= closing apps

    /** Swiped away and waiting to be closed (package → name); closed together. */
    private val pendingClose = LinkedHashMap<String, String>()
    private var closeTimer: Job? = null
    private var closeFlushing = false
    private var closeHintShown = false

    private fun closeMethod() = com.prismtv.launcher.system.AppCloser.method(prefs.closeCompletely, prefs.rootClose)

    private fun queueClose(app: LauncherApp) {
        // An HDMI input tile has nothing to stop: its memory is freed and that's all.
        if (com.prismtv.launcher.data.TvInputs.isInput(app)) {
            SystemActions.killBackground(ctx, app.packageName)
            return
        }
        pendingClose[app.packageName] = app.label
        scheduleClose()
    }

    /**
     * Shizuku and root close straight away. Closing through Settings waits until you've stopped swiping
     * (so several apps close in one go) and only while Prism TV is on screen.
     */
    private fun scheduleClose() {
        if (!closeFlushing) closeTimer?.cancel()
        val viaSettings = com.prismtv.launcher.system.AppCloser.opensSettings(closeMethod())
        closeTimer = viewModelScope.launch {
            if (viaSettings) {
                delay(900)
                if (!isResumed || launchAnim != null) return@launch // closes when you're back (onResume)
            }
            flushCloses()
        }
    }

    /** Closes everything swiped away so far. */
    private suspend fun flushCloses() {
        if (pendingClose.isEmpty() || closeFlushing) return
        val batch = pendingClose.toList()
        pendingClose.clear()
        closeFlushing = true
        val result = try {
            withContext(kotlinx.coroutines.NonCancellable) {
                com.prismtv.launcher.system.AppCloser.close(ctx, batch, prefs.closeCompletely, prefs.rootClose)
            }
        } finally {
            closeFlushing = false
        }
        lastCloseResult = result
        if (result.method == com.prismtv.launcher.system.AppCloser.Method.MemoryOnly) {
            // Once per run: say why apps may keep running, and where to change it.
            if (prefs.closeCompletely && !closeHintShown && !debugDriven) {
                closeHintShown = true
                showBanner(
                    "Closing Apps",
                    "Only memory was freed, so some apps keep running. Settings › Apps › Closing Apps can close them completely.",
                    Icons.Rounded.Info,
                )
            }
        } else if (result.failed.isNotEmpty()) {
            val names = batch.filter { it.first in result.failed }.map { it.second }
            val message = if (names.size == 1) {
                "${names[0]} couldn't be closed completely, so only its memory was freed."
            } else {
                "${names.size} apps couldn't be closed completely, so only their memory was freed."
            }
            showBanner("Closing Apps", message, Icons.Rounded.Info)
        }
        if (pendingClose.isNotEmpty()) scheduleClose()
    }

    /** CI: what the last close did. */
    var lastCloseResult: com.prismtv.launcher.system.AppCloser.Result? = null
        private set

    // ================================================================= profiles

    /** CI screenshots: three people, one with a passcode and one with restrictions. */
    private fun demoProfiles(): List<com.prismtv.launcher.data.ProfileSpec> = listOf(
        com.prismtv.launcher.data.ProfileSpec(com.prismtv.launcher.data.Profiles.MainId, "Sam", "preset:0"),
        com.prismtv.launcher.data.ProfileSpec("p-alex", "Alex", "preset:1", passcode = com.prismtv.launcher.data.Profiles.hash("1234")),
        com.prismtv.launcher.data.ProfileSpec("p-kids", "Kids", "preset:2", blocked = setOf("demo:arcade", "demo:prism")),
    )

    /** A passcode being asked for (switching to a profile, opening locked Settings, choosing one). */
    class PasscodeRequest(
        val title: String,
        val message: String,
        val check: (String) -> Boolean,
        val onDone: (String) -> Unit,
        val onCancel: () -> Unit = {},
    )

    var passcode by mutableStateOf<PasscodeRequest?>(null)
        private set
    var passcodeEntry by mutableStateOf("")
        private set
    var passcodeFocus by mutableIntStateOf(0)
        private set
    /** Bumped on a wrong passcode, so the dots shake. */
    var passcodeShake by mutableIntStateOf(0)
        private set

    /** "Who's Watching?" is showing. */
    var profilePicker by mutableStateOf(false)
        private set
    var pickerFocus by mutableIntStateOf(0)
        private set
    /** The picker can't be closed without choosing (after deleting the profile in use). */
    private var pickerForced = false
    private var pickerOnResume = false

    private val activeId: String
        get() = prefs.activeProfile.ifEmpty { prefs.profiles.firstOrNull()?.id ?: com.prismtv.launcher.data.Profiles.MainId }

    /** Every profile; the one in use carries the live name and picture. Cached until prefs change. */
    val profileList: List<com.prismtv.launcher.data.ProfileSpec> by derivedStateOf {
        val saved = prefs.profiles.ifEmpty {
            listOf(com.prismtv.launcher.data.ProfileSpec(com.prismtv.launcher.data.Profiles.MainId, prefs.profileName, prefs.avatar))
        }
        val id = activeId
        saved.map { if (it.id == id) it.copy(name = prefs.profileName, avatar = prefs.avatar) else it }
    }

    val activeProfileSpec: com.prismtv.launcher.data.ProfileSpec by derivedStateOf {
        profileList.let { list -> list.firstOrNull { it.id == activeId } ?: list.first() }
    }

    /** Apps the profile in use can't see (Settings › Profiles › Restrictions). */
    val blockedApps: Set<String> get() = activeProfileSpec.blocked

    fun nameOf(p: com.prismtv.launcher.data.ProfileSpec): String =
        p.name.ifBlank { if (p.id == com.prismtv.launcher.data.Profiles.MainId) deviceName() else "Profile" }

    fun lookOf(p: com.prismtv.launcher.data.ProfileSpec): com.prismtv.launcher.ui.components.AvatarLook {
        val a = p.avatar
        if (a.startsWith("photo:")) thumb(UriArt(a.removePrefix("photo:")))?.let { return com.prismtv.launcher.ui.components.AvatarLook.Photo(it) }
        val i = a.removePrefix("preset:").toIntOrNull() ?: 0
        return com.prismtv.launcher.ui.components.AvatarLook.Preset(i.coerceIn(0, com.prismtv.launcher.ui.components.AvatarPresets.size - 1))
    }

    private fun saveProfiles(list: List<com.prismtv.launcher.data.ProfileSpec>, active: com.prismtv.launcher.data.ProfileSpec) {
        updatePrefs(prefs.copy(profiles = list, activeProfile = active.id, profileName = active.name, avatar = active.avatar))
    }

    private fun updateActive(change: (com.prismtv.launcher.data.ProfileSpec) -> com.prismtv.launcher.data.ProfileSpec) {
        val id = activeId
        val list = profileList.map { if (it.id == id) change(it) else it }
        saveProfiles(list, list.first { it.id == id })
    }

    /** Switches to [p], asking for its passcode when it has one. */
    fun chooseProfile(p: com.prismtv.launcher.data.ProfileSpec, then: () -> Unit = {}) {
        if (p.id == activeId && !(pickerForced && p.locked)) {
            then()
            return
        }
        val go = {
            saveProfiles(profileList, p)
            cue(Cue.Confirm)
            then()
        }
        if (p.locked) askPasscode("Enter Passcode", "Enter ${nameOf(p)}'s passcode.", { code -> com.prismtv.launcher.data.Profiles.matches(p, code) }) { go() }
        else go()
    }

    /** tvOS's "Who's Watching?": shown on wake and from Control Centre's profile picture. */
    fun showProfilePicker(forced: Boolean = false) {
        if (profileList.size < 2 && !forced) return
        dialog = null
        contextMenu = null
        if (overlay != Overlay.None) closeOverlay()
        pickerFocus = profileList.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
        pickerForced = forced
        profilePicker = true
    }

    private fun closePicker() {
        profilePicker = false
        pickerForced = false
    }

    private fun pickerKey(k: NavKey): Boolean {
        val list = profileList
        when (k) {
            NavKey.Left -> pickerFocus = (pickerFocus - 1).coerceAtLeast(0)
            NavKey.Right -> pickerFocus = (pickerFocus + 1).coerceAtMost(list.size - 1)
            NavKey.Select -> list.getOrNull(pickerFocus)?.let { p -> chooseProfile(p) { closePicker() } }
            NavKey.Menu -> if (!pickerForced) closePicker()
            else -> Unit
        }
        return true
    }

    /** The screen went off: offer "Who's Watching?" when it's back (Settings › Profiles). */
    fun onScreenOff() {
        if (prefs.profilePickerOnWake && profileList.size >= 2) pickerOnResume = true
    }

    fun onScreenOn() {
        if (pickerOnResume && isResumed) {
            pickerOnResume = false
            showProfilePicker()
        }
    }

    private fun askPasscode(title: String, message: String, check: (String) -> Boolean, onCancel: () -> Unit = {}, onDone: (String) -> Unit) {
        passcodeEntry = ""
        passcodeFocus = 0
        passcode = PasscodeRequest(title, message, check, onDone, onCancel)
    }

    private fun passcodeKey(r: PasscodeRequest, k: NavKey): Boolean {
        when (k) {
            NavKey.Left -> passcodeFocus = (passcodeFocus - 1).coerceAtLeast(0)
            NavKey.Right -> passcodeFocus = (passcodeFocus + 1).coerceAtMost(PasscodeKeys.size - 1)
            NavKey.Select -> {
                val key = PasscodeKeys[passcodeFocus]
                if (key == PasscodeDelete) {
                    passcodeEntry = passcodeEntry.dropLast(1)
                    cue(Cue.Delete)
                } else typePasscode(r, key)
            }
            NavKey.Menu -> cancelPasscode()
            else -> Unit
        }
        return true
    }

    /** The remote's number keys type straight into the passcode. */
    fun typePasscodeDigit(d: Char) {
        val r = passcode ?: return
        typePasscode(r, d.toString())
    }

    private fun typePasscode(r: PasscodeRequest, digit: String) {
        if (passcodeEntry.length >= 4) return
        passcodeEntry += digit
        cue(Cue.Key)
        if (passcodeEntry.length == 4) {
            val code = passcodeEntry
            viewModelScope.launch {
                delay(180) // let the fourth dot fill before it's checked
                if (passcode !== r) return@launch
                if (r.check(code)) {
                    passcode = null
                    passcodeEntry = ""
                    r.onDone(code)
                } else {
                    passcodeShake++
                    passcodeEntry = ""
                    sounds.play(Cue.Error)
                }
            }
        }
    }

    fun cancelPasscode() {
        val r = passcode ?: return
        passcode = null
        passcodeEntry = ""
        r.onCancel()
    }

    /** In a profile with restrictions, Settings needs a passcode (when any profile has one). */
    private fun unlockSettings(action: () -> Unit) {
        val me = activeProfileSpec
        if (!me.restricted || profileList.none { it.locked } || debugDriven) {
            action()
            return
        }
        askPasscode(
            "Settings Are Locked",
            "Enter a passcode to change settings for ${nameOf(me)}.",
            { code -> profileList.any { com.prismtv.launcher.data.Profiles.matches(it, code) } },
        ) { action() }
    }

    private fun addProfile() {
        openKeyboard("New Profile", "", "Name") { name ->
            val clean = name.trim().take(30)
            if (clean.isEmpty()) return@openKeyboard
            val used = profileList.map { it.avatar }.toSet()
            val presets = com.prismtv.launcher.ui.components.AvatarPresets.indices
            val preset = presets.firstOrNull { "preset:$it" !in used } ?: (profileList.size % presets.count())
            val new = com.prismtv.launcher.data.ProfileSpec(com.prismtv.launcher.data.Profiles.newId(), clean, "preset:$preset")
            val list = profileList + new
            saveProfiles(list, list.first { it.id == activeId })
            showBanner("Profiles", "$clean was added. Switch to them here or from Who's Watching.", Icons.Rounded.CheckCircle, Cue.Confirm)
        }
    }

    private fun setPasscode() {
        val me = activeProfileSpec
        askPasscode("Choose a Passcode", "Enter four digits for ${nameOf(me)}.", { true }) { first ->
            askPasscode("Enter It Again", "Enter the same four digits again.", { it == first }) {
                updateActive { pr -> pr.copy(passcode = com.prismtv.launcher.data.Profiles.hash(first)) }
                showBanner("Passcode", "Switching to ${nameOf(me)} now needs the passcode.", Icons.Rounded.Lock, Cue.Confirm)
            }
        }
    }

    private fun turnOffPasscode() {
        val me = activeProfileSpec
        askPasscode("Turn Off Passcode", "Enter ${nameOf(me)}'s passcode.", { com.prismtv.launcher.data.Profiles.matches(me, it) }) {
            updateActive { pr -> pr.copy(passcode = "") }
            showBanner("Passcode", "${nameOf(me)} no longer needs a passcode.", Icons.Rounded.Info, Cue.Confirm)
        }
    }

    private fun deleteActiveProfile() {
        val me = activeProfileSpec
        val rest = profileList.filter { it.id != me.id }
        if (rest.isEmpty()) return
        confirm(
            "Delete ${nameOf(me)}?",
            "Their name, picture, passcode, restrictions and Home Screen layout are removed. Your apps stay installed.",
            "Delete",
        ) {
            if (me.id != com.prismtv.launcher.data.Profiles.MainId) layoutStore.remove("real:${me.id}")
            val open = rest.firstOrNull { !it.locked }
            if (open != null) {
                saveProfiles(rest, open)
            } else {
                // Every other profile has a passcode: choose one properly.
                saveProfiles(rest, rest.first())
                showProfilePicker(forced = true)
            }
        }
    }

    // ================================================================= Prism Search

    /** Search is open (on Prism's keyboard, with results above it). */
    var searchOpen by mutableStateOf(false)
        private set
    /** -1: the keyboard; otherwise the focused result. */
    var searchFocus by mutableStateOf(-1)
        private set

    /** What matches what's typed (or, before anything's typed, the apps you used last). */
    val searchResults: List<SearchResult> by derivedStateOf {
        if (!searchOpen) emptyList() else search(keyboardText)
    }

    val searchHeading: String get() = if (keyboardText.isBlank()) "Recently Used" else if (searchResults.isEmpty()) "No Results" else "Top Results"

    fun openSearch() {
        dialog = null
        contextMenu = null
        if (overlay != Overlay.None && overlay != Overlay.Settings) overlay = Overlay.None
        openKeyboard("", "", "Search Apps, Titles and Settings") { q -> search(q).firstOrNull()?.let { openSearchResult(it) } }
        keyboardController.actionLabel = "search"
        // Search isn't a sentence: lowercase from the first letter, as on Apple TV.
        keyboardController.autoCapOn = false
        keyboardController.updateAutoCap()
        searchOpen = true
        searchFocus = -1
    }

    /** Remote keys while Search is open; false lets the keyboard have them. */
    private fun searchKey(k: NavKey): Boolean {
        val results = searchResults
        if (searchFocus >= 0) {
            when (k) {
                NavKey.Left -> if (searchFocus > 0) { searchFocus--; cue(Cue.FocusSmall) }
                    // The first result is next to the side keyboard: Left goes back to it.
                    else if (com.prismtv.launcher.ui.keyboard.KeyboardLayout.alphabetical) { searchFocus = -1; cue(Cue.FocusSmall) }
                NavKey.Right -> if (searchFocus < results.size - 1) { searchFocus++; cue(Cue.FocusSmall) }
                NavKey.Down, NavKey.Menu -> { searchFocus = -1; cue(Cue.FocusSmall) }
                NavKey.Select -> results.getOrNull(searchFocus)?.let { openSearchResult(it) }
                else -> Unit
            }
            return true
        }
        // ABC keyboard down the left: Right from the end of a row goes across to the results.
        val side = com.prismtv.launcher.ui.keyboard.KeyboardLayout.alphabetical
        if (side) {
            if (k == NavKey.Right && results.isNotEmpty() && keyboardController.atRowEnd()) {
                searchFocus = 0
                cue(Cue.FocusSmall)
                return true
            }
            return false
        }
        if (k == NavKey.Up && !keyboardController.nav(com.prismtv.launcher.ui.keyboard.KbNav.Up, wrapUp = results.isEmpty())) {
            // Up from the keyboard's top row goes to the results.
            if (results.isNotEmpty()) {
                searchFocus = 0
                cue(Cue.FocusSmall)
            }
            return true
        }
        return k == NavKey.Up
    }

    private fun openSearchResult(r: SearchResult) {
        closeKeyboard()
        when (r) {
            is AppSearchResult -> {
                val idx = items.indexOfFirst { it is AppItem && it.app.id == r.app.id }
                startLaunch(r.app, if (idx >= 0) tileAnchor(idx) else SwitcherLayout.focusedRect())
            }
            is TitleSearchResult -> openWidgetItem(r.item)
            is SettingSearchResult -> unlockSettings {
                showSettings()
                if (r.page != SettingsPage.Root) pushPage(r.page)
                r.rowId?.let { id ->
                    val i = settingsRows(r.page).indexOfFirst { it.id == id }
                    if (i >= 0) settingsFocus[r.page] = i
                }
            }
        }
    }

    /** Places in Settings worth finding by name (the page, and the row on it). */
    private val settingsIndex: List<SettingSearchResult> by lazy {
        val pages = listOf(
            SettingsPage.Appearance, SettingsPage.HomeScreen, SettingsPage.Screensaver, SettingsPage.Keyboard,
            SettingsPage.Apps, SettingsPage.Wallpaper, SettingsPage.Widgets, SettingsPage.TextSize, SettingsPage.Profiles,
            SettingsPage.Backup, SettingsPage.IconPack, SettingsPage.Access, SettingsPage.Closing, SettingsPage.Shizuku,
            SettingsPage.HomeApp, SettingsPage.RemoteButtons, SettingsPage.SoftwareUpdate, SettingsPage.About,
        ).filter { !BuildConfig.PLAY || (it != SettingsPage.Shizuku && it != SettingsPage.SoftwareUpdate) }
            .map { SettingSearchResult(it.title, "Settings", it) }
        val rows = listOf(
            SettingSearchResult("Lite Mode", "Appearance", SettingsPage.Appearance, "lite"),
            SettingSearchResult("Liquid Glass", "Appearance", SettingsPage.Appearance, "material"),
            SettingSearchResult("Dark Mode", "Appearance", SettingsPage.Appearance),
            SettingSearchResult("Light Mode", "Appearance", SettingsPage.Appearance),
            SettingSearchResult("Focus Effect", "Home Screen", SettingsPage.HomeScreen),
            SettingSearchResult("Top Shelf", "Home Screen", SettingsPage.HomeScreen, "hero"),
            SettingSearchResult("Banners Over Apps", "Home Screen", SettingsPage.HomeScreen, "bannersOver"),
            SettingSearchResult("App Snapshots", "Home Screen", SettingsPage.HomeScreen, "snapshots"),
            SettingSearchResult("Weather", "Home Screen", SettingsPage.HomeScreen, "weather"),
            SettingSearchResult("Wi-Fi in the Time Pill", "Home Screen", SettingsPage.HomeScreen, "wifi"),
            SettingSearchResult("Aerials", "Wallpaper", SettingsPage.Wallpaper, "wp:aerial"),
            SettingSearchResult("Screen Time", "Profiles", SettingsPage.ScreenTime),
            SettingSearchResult("Restrictions", "Profiles", SettingsPage.Restrictions),
            SettingSearchResult("Passcode", "Profiles", SettingsPage.Profiles, "pr:passcode"),
            SettingSearchResult("Sleep Timer", "Settings", SettingsPage.Root, "sleep"),
            SettingSearchResult("Power & Home Button", "Settings", SettingsPage.Root, "a11y"),
            SettingSearchResult("Navigation Sounds", "Settings", SettingsPage.Root, "sounds"),
            SettingSearchResult("Restart Launcher", "Settings", SettingsPage.Root, "restart"),
            SettingSearchResult("Notifications", "Privacy & Access", SettingsPage.Access, "notif"),
            SettingSearchResult("Dictation", "Keyboard", SettingsPage.Keyboard),
        )
        pages + rows.filter { !BuildConfig.PLAY || it.rowId != "snapshots" }
    }

    private fun search(query: String): List<SearchResult> {
        val q = query.trim().lowercase()
        val blocked = blockedApps
        val visibleApps = allApps.filter { it.id !in blocked && it.id !in prefs.hiddenApps }
        if (q.isEmpty()) {
            return recentEntries().mapNotNull { it.app }.reversed().filter { it.id !in blocked }.take(8).map { AppSearchResult(it) }
        }
        // Best first: the name starts with it, then a word in it does, then it's anywhere.
        fun rank(text: String): Int {
            val t = text.lowercase()
            return when {
                t.startsWith(q) -> 0
                t.split(' ', '-', ':', '.').any { it.startsWith(q) } -> 1
                q.length >= 3 && t.contains(q) -> 2
                else -> -1
            }
        }
        val apps = visibleApps.mapNotNull { a -> rank(a.label).takeIf { it >= 0 }?.let { it to a } }
            .sortedWith(compareBy({ it.first }, { it.second.label.length })).map { AppSearchResult(it.second) }.take(8)
        val seen = HashSet<String>()
        val titles = (heroItemsByApp.values.flatten() + widgetRowsAll.flatMap { it.items } + if (prefs.demoContent) DemoContent.heroes.values.flatten() else emptyList())
            .filter { it.title.isNotBlank() && it.appId !in blocked && seen.add(it.title.lowercase()) }
            .mapNotNull { h -> rank(h.title).takeIf { it >= 0 }?.let { it to h } }
            .sortedBy { it.first }.map { TitleSearchResult(it.second) }.take(8)
        val settings = if (activeProfileSpec.restricted) emptyList() else settingsIndex
            .mapNotNull { st -> rank(st.label).takeIf { it >= 0 }?.let { it to st } }
            .sortedBy { it.first }.map { it.second }.distinctBy { it.label }.take(4)
        return (apps + titles + settings).take(16)
    }

    // ================================================================= remote buttons

    /** A remote button's Prism TV action (from Power & Home Button, through MainActivity). */
    fun remoteAction(action: String) {
        when (action) {
            "switcher" -> if (overlay == Overlay.Switcher) closeSwitcherToHome() else openSwitcher()
            "cc" -> if (overlay == Overlay.ControlCentre) closeOverlay() else openControlCentre()
            "search" -> if (!searchOpen) openSearch()
            "screensaver" -> startScreensaver()
            "timesup" -> activeProfileSpec.let { me ->
                com.prismtv.launcher.data.ScreenTime.blocked(ctx, me)?.let { b -> if (dialog == null) showTimesUp(me, b) }
            }
            "previous" -> {
                // The app before the one you were watching (or the last one, from Home).
                val pkgs = recentPackagesCache ?: recentsRepo.recentPackages()
                val current = com.prismtv.launcher.system.Foreground.lastPackage
                val target = pkgs.firstOrNull { it != current && it != ctx.packageName }
                realApps.firstOrNull { it.packageName == target }?.let { startLaunch(it, SwitcherLayout.focusedRect()) }
            }
        }
    }

    /** The button being changed ("home", or a mapping id) and which of its actions. */
    private var remoteEditing by mutableStateOf("")
    private var remoteSlot by mutableStateOf("press")

    private fun remoteMappings(): List<com.prismtv.launcher.system.RemoteButtons.Mapping> =
        com.prismtv.launcher.system.RemoteButtons.decode(prefs.remoteButtons)

    private fun saveRemoteMappings(list: List<com.prismtv.launcher.system.RemoteButtons.Mapping>) {
        updatePrefs(prefs.copy(remoteButtons = com.prismtv.launcher.system.RemoteButtons.encode(list)))
    }

    private fun remoteEditingName(): String =
        if (remoteEditing == "home") "Home Button"
        else remoteMappings().firstOrNull { it.id == remoteEditing }?.let { com.prismtv.launcher.system.RemoteButtons.name(it.code, it.scan) } ?: "Button"

    private fun actionLabel(action: String): String = com.prismtv.launcher.system.RemoteButtons.label(action) { pkg ->
        realApps.firstOrNull { it.packageName == pkg }?.label ?: appName(pkg)
    }

    private fun remoteButtonsLabel(): String {
        val n = remoteMappings().size + if (prefs.homeHold != "default") 1 else 0
        return if (n == 0) "None Changed" else if (n == 1) "1 Changed" else "$n Changed"
    }

    private fun remoteButtonRows(): List<SettingsRow> {
        val a11y = PrismAccessibilityService.isEnabled
        val rows = mutableListOf<SettingsRow>()
        if (!a11y) rows += SettingsRow("rb:a11y", "Turn On Power & Home Button", "NEEDED FOR REMOTE BUTTONS", "Off", chevron = true)
        rows += SettingsRow("rb:homeHold", "Hold Home", "HOME BUTTON", actionLabel(prefs.homeHold), chevron = true)
        remoteMappings().forEach { m ->
            val hold = if (m.hold != "default") " · Hold: ${actionLabel(m.hold)}" else ""
            rows += SettingsRow("rb:btn:${m.id}", com.prismtv.launcher.system.RemoteButtons.name(m.code, m.scan), "CHANGED BUTTONS", actionLabel(m.press) + hold, chevron = true)
        }
        rows += SettingsRow("rb:add", "Add a Button…", "CHANGED BUTTONS", if (a11y) null else "Needs Power & Home Button", chevron = a11y)
        return rows
    }

    private fun remoteButtonEditRows(): List<SettingsRow> {
        val m = remoteMappings().firstOrNull { it.id == remoteEditing } ?: return emptyList()
        return listOf(
            SettingsRow("rbe:press", "Press", "WHEN YOU…", actionLabel(m.press), chevron = true),
            SettingsRow("rbe:hold", "Hold", "WHEN YOU…", actionLabel(m.hold), chevron = true),
            SettingsRow("rbe:remove", "Put Back as It Was", "BUTTON"),
        )
    }

    private fun currentRemoteAction(): String =
        if (remoteEditing == "home") prefs.homeHold
        else remoteMappings().firstOrNull { it.id == remoteEditing }?.let { if (remoteSlot == "hold") it.hold else it.press } ?: "default"

    private fun remoteActionRows(): List<SettingsRow> {
        val now = currentRemoteAction()
        val actions = com.prismtv.launcher.system.RemoteButtons.Action.entries
            .filter { !(remoteEditing == "home" && it == com.prismtv.launcher.system.RemoteButtons.Action.Home) }
            .map { a -> SettingsRow("ra:${a.id}", if (a.id == "default" && remoteEditing == "home") "Nothing" else a.label, "DO", selectable = true, checked = now == a.id) }
        val apps = realApps.filter { it.id !in blockedApps && !com.prismtv.launcher.data.TvInputs.isInput(it) }
            .sortedBy { it.label.lowercase() }
            .map { SettingsRow("ra:app:${it.packageName}", it.label, "OPEN AN APP", selectable = true, checked = now == "app:${it.packageName}", app = it) }
        return actions + apps
    }

    private fun selectRemote(id: String) {
        val p = prefs
        when {
            id == "rb:a11y" -> setUpPowerAndHome()
            id == "rb:homeHold" -> {
                remoteEditing = "home"
                remoteSlot = "hold"
                pushPage(SettingsPage.RemoteAction)
                settingsFocus[SettingsPage.RemoteAction] = remoteActionRows().indexOfFirst { it.checked }.coerceAtLeast(0)
            }
            id.startsWith("rb:btn:") -> {
                remoteEditing = id.removePrefix("rb:btn:")
                pushPage(SettingsPage.RemoteButton)
            }
            id == "rb:add" -> learnRemoteButton()
            id == "rbe:press" || id == "rbe:hold" -> {
                remoteSlot = id.removePrefix("rbe:")
                pushPage(SettingsPage.RemoteAction)
                settingsFocus[SettingsPage.RemoteAction] = remoteActionRows().indexOfFirst { it.checked }.coerceAtLeast(0)
            }
            id == "rbe:remove" -> {
                saveRemoteMappings(remoteMappings().filterNot { it.id == remoteEditing })
                settingsStack = settingsStack.dropLast(1)
                cue(Cue.Confirm)
            }
            id.startsWith("ra:") -> {
                val action = id.removePrefix("ra:")
                if (remoteEditing == "home") updatePrefs(p.copy(homeHold = action))
                else saveRemoteMappings(remoteMappings().map { m ->
                    if (m.id != remoteEditing) m else if (remoteSlot == "hold") m.copy(hold = action) else m.copy(press = action)
                })
                settingsStack = settingsStack.dropLast(1)
                cue(Cue.Confirm)
                val m = remoteMappings().firstOrNull { it.id == remoteEditing }
                if (m != null && m.press == "default" && m.hold != "default" &&
                    !com.prismtv.launcher.system.ShellRunner.available(ctx, prefs.rootClose)
                ) {
                    showBanner(
                        "Remote Buttons",
                        if (BuildConfig.PLAY) "Holding works now. While it has a hold action, the button's usual press does nothing: choose a Press action for it too."
                        else "Holding works now. To keep the button's usual press as well, set up Shizuku (Settings › Apps › Closing Apps), or choose a Press action.",
                        Icons.Rounded.Info,
                    )
                }
            }
        }
    }

    /** Add a Button: the next button pressed is the one to change. */
    private fun learnRemoteButton() {
        if (!PrismAccessibilityService.isEnabled) {
            setUpPowerAndHome()
            return
        }
        val spec = DialogSpec(
            "Press a Button",
            "Press the button on your remote you'd like to change, such as an app button, a colour button or Guide. Home, Back, Select, the arrows, volume and power stay as they are.",
            listOf(DialogButton("Cancel") { com.prismtv.launcher.system.RemoteButtons.learning = null }),
        )
        dialog = spec
        dialogFocus = 0
        val learn: (Int, Int) -> Unit = { code, scan ->
            if (dialog === spec) {
                dialog = null
                val list = remoteMappings()
                val m = com.prismtv.launcher.system.RemoteButtons.Mapping(code, scan, "default", "default")
                if (list.none { it.id == m.id }) saveRemoteMappings(list + m)
                remoteEditing = m.id
                cue(Cue.Confirm)
                pushPage(SettingsPage.RemoteButton)
            }
        }
        com.prismtv.launcher.system.RemoteButtons.learning = learn
        // Stops listening once the dialog's gone (or after a while), so no later press is taken.
        viewModelScope.launch {
            val until = SystemClock.elapsedRealtime() + 20_000L
            while (dialog === spec && SystemClock.elapsedRealtime() < until) delay(300)
            if (dialog === spec) dialog = null
            if (com.prismtv.launcher.system.RemoteButtons.learning === learn) com.prismtv.launcher.system.RemoteButtons.learning = null
        }
    }

    // ================================================================= snapshots & banners

    private fun snapshotsLabel(): String = when {
        !com.prismtv.launcher.system.AppSnapshots.supported() -> "Needs Android 11"
        !prefs.appSnapshots -> "Off"
        !PrismAccessibilityService.isEnabled -> "Needs Power & Home Button"
        else -> "On"
    }

    private fun toggleSnapshots() {
        if (!com.prismtv.launcher.system.AppSnapshots.supported()) {
            showBanner("App Snapshots", "Snapshots need Android 11 or later. The App Switcher shows each app's artwork instead.", Icons.Rounded.Info)
            return
        }
        val on = !prefs.appSnapshots
        updatePrefs(prefs.copy(appSnapshots = on))
        if (!on) com.prismtv.launcher.system.AppSnapshots.clear(ctx)
        else if (!PrismAccessibilityService.isEnabled) {
            showBanner("App Snapshots", "Turn on Power & Home Button so Prism TV can take a snapshot as you press Home.", Icons.Rounded.Info)
        }
    }

    private fun bannersOverLabel(): String = when {
        !prefs.bannersOverApps -> "Off"
        !PrismAccessibilityService.isEnabled -> "Needs Power & Home Button"
        !status().notif -> "Needs Notifications"
        else -> "On"
    }

    private fun toggleBannersOver() {
        val on = !prefs.bannersOverApps
        updatePrefs(prefs.copy(bannersOverApps = on))
        if (!on) return
        when {
            !PrismAccessibilityService.isEnabled ->
                showBanner("Banners Over Apps", "Turn on Power & Home Button too: banners are shown through it, over whatever's playing.", Icons.Rounded.Info)
            !status().notif ->
                showBanner("Banners Over Apps", "Allow Notifications for Prism TV too (the row above), so it can see what arrives.", Icons.Rounded.Info)
            else -> viewModelScope.launch {
                // A sample over Settings, so you can see where they appear.
                delay(600)
                com.prismtv.launcher.system.PrismAccessibilityService.banner(
                    "Prism TV", "Banners Over Apps", "New notifications appear here while you watch.", null,
                    prefs.appearance == AppearanceSetting.Light,
                )
            }
        }
    }

    // ================================================================= Screen Time

    /** When Prism TV last went behind an app (elapsed time), for counting Screen Time. */
    private var screenTimeMark = 0L
    /** Bumped when Screen Time numbers change, so its page refreshes. */
    private var screenTimeTick by mutableStateOf(0)

    private fun screenTimeLabel(p: com.prismtv.launcher.data.ProfileSpec): String = when {
        p.limitMinutes > 0 -> com.prismtv.launcher.data.ScreenTime.limitLabel(p.limitMinutes) + " a Day"
        p.downtime.isNotEmpty() -> "Downtime"
        else -> "Off"
    }

    private fun screenTimeRows(): List<SettingsRow> {
        val me = activeProfileSpec
        val st = com.prismtv.launcher.data.ScreenTime
        val used = st.usedToday(ctx, me.id) + pendingScreenTime()
        val section = "SCREEN TIME FOR ${nameOf(me).uppercase()}"
        return listOfNotNull(
            SettingsRow("st:limit", "Daily Limit", section, st.limitLabel(me.limitMinutes)),
            SettingsRow("st:downtime", "Downtime", section, st.downtimeLabel(me.downtime)),
            SettingsRow("st:used", "Used Today", "TODAY", st.duration(used)),
            st.remaining(ctx, me)?.let { left ->
                // Rounded up: a minute and a bit left reads as 2 Min, not 1.
                val ms = (left - pendingScreenTime()).coerceAtLeast(0)
                SettingsRow("st:left", "Left Today", "TODAY", st.duration((ms + 59_999L) / 60_000L * 60_000L))
            },
            if (profileList.none { it.locked }) SettingsRow("st:passcode", "Add a Passcode", "SO ONLY YOU CAN GIVE MORE TIME", chevron = true) else null,
        )
    }

    private fun selectScreenTime(id: String) {
        val st = com.prismtv.launcher.data.ScreenTime
        when (id) {
            "st:limit" -> updateActive { pr ->
                val i = st.Limits.indexOf(pr.limitMinutes)
                pr.copy(limitMinutes = st.Limits[(i + 1).mod(st.Limits.size)])
            }
            "st:downtime" -> updateActive { pr ->
                val i = st.Downtimes.indexOf(pr.downtime)
                pr.copy(downtime = st.Downtimes[(i + 1).mod(st.Downtimes.size)])
            }
            "st:used", "st:left" -> showBanner(
                "Screen Time",
                "Time counts while an app is on screen, not while you're on the Home Screen. It starts again at midnight.",
                Icons.Rounded.Info,
            )
            "st:passcode" -> {
                settingsStack = listOf(SettingsPage.Root, SettingsPage.Profiles)
                showBanner("Screen Time", "Choose the profile that gives more time (usually yours), then turn on its Passcode.", Icons.Rounded.Lock)
            }
        }
        screenTimeTick++
    }

    /** App time not yet added: Prism TV has been behind an app since [screenTimeMark]. */
    private fun pendingScreenTime(): Long =
        if (!isResumed && screenTimeMark > 0) (SystemClock.elapsedRealtime() - screenTimeMark).coerceAtLeast(0) else 0L

    /** Adds the time since Prism TV went behind an app to the active profile's Screen Time. */
    private fun countScreenTime() {
        val mark = screenTimeMark
        if (mark <= 0 || isResumed) return
        val now = SystemClock.elapsedRealtime()
        screenTimeMark = now
        val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isInteractive || com.prismtv.launcher.system.Foreground.screensaver) return
        // Long gaps are the TV asleep or Prism TV stopped: they don't count.
        val ms = (now - mark).coerceAtMost(5 * 60_000L)
        val me = activeProfileSpec
        if (me.timeLimited) com.prismtv.launcher.data.ScreenTime.add(ctx, me.id, ms)
    }

    /** Checks once a minute (and as Prism TV goes behind an app) whether the active profile's time is up. */
    private fun watchScreenTime() {
        viewModelScope.launch {
            while (true) {
                delay(if (isResumed) 60_000L else 20_000L)
                val me = activeProfileSpec
                if (!me.timeLimited || debugDriven) continue
                if (!isResumed) {
                    countScreenTime()
                    if (com.prismtv.launcher.system.Foreground.screensaver) continue
                    val block = com.prismtv.launcher.data.ScreenTime.blocked(ctx, me) ?: continue
                    timesUp(me, block)
                }
            }
        }
    }

    /** Time's up while an app is on screen: Prism TV comes forward (closing the app if it can). */
    private fun timesUp(me: com.prismtv.launcher.data.ProfileSpec, block: com.prismtv.launcher.data.ScreenTime.Block) {
        val pkg = com.prismtv.launcher.system.Foreground.lastPackage
        runCatching {
            // Not as Home (which closes dialogs): with the Time's Up action, shown once Prism TV is up.
            ctx.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .setClassName(ctx, "com.prismtv.launcher.MainActivity")
                    .putExtra(PrismAccessibilityService.ExtraAction, "timesup")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        }
        showTimesUp(me, block)
        if (pkg != null && pkg != ctx.packageName) {
            val label = realApps.firstOrNull { it.packageName == pkg }?.label ?: appName(pkg)
            viewModelScope.launch {
                delay(1500)
                if (isResumed) {
                    pendingClose[pkg] = label
                    scheduleClose()
                }
            }
        }
    }

    /** An app can't open: time's up or it's downtime. Shows why, with Ask for More Time. */
    private fun screenTimeBlocks(@Suppress("UNUSED_PARAMETER") app: LauncherApp?): Boolean {
        val me = activeProfileSpec
        if (!me.timeLimited || debugDriven) return false
        val block = com.prismtv.launcher.data.ScreenTime.blocked(ctx, me) ?: return false
        showTimesUp(me, block)
        return true
    }

    private fun showTimesUp(me: com.prismtv.launcher.data.ProfileSpec, block: com.prismtv.launcher.data.ScreenTime.Block) {
        val st = com.prismtv.launcher.data.ScreenTime
        val (title, message) = when (block) {
            com.prismtv.launcher.data.ScreenTime.Block.Limit ->
                "Time's Up" to "You've reached your ${st.limitLabel(me.limitMinutes).lowercase()} limit for today, ${nameOf(me)}. Apps open again tomorrow."
            com.prismtv.launcher.data.ScreenTime.Block.Downtime ->
                "Downtime" to "It's downtime (${st.downtimeLabel(me.downtime)}), ${nameOf(me)}. Apps open again when it ends."
        }
        sounds.play(Cue.Notify)
        dialog = DialogSpec(
            title, message,
            listOfNotNull(
                DialogButton("OK") {},
                if (profileList.any { it.locked }) DialogButton("Ask for More Time") { askForMoreTime(me, block) } else null,
            ),
        )
        dialogFocus = 0
    }

    private fun askForMoreTime(me: com.prismtv.launcher.data.ProfileSpec, block: com.prismtv.launcher.data.ScreenTime.Block) {
        askPasscode(
            "Ask for More Time",
            "Enter a passcode to give ${nameOf(me)} more time.",
            { code -> profileList.any { com.prismtv.launcher.data.Profiles.matches(it, code) } },
        ) {
            val st = com.prismtv.launcher.data.ScreenTime
            fun grant(label: String, ms: Long) = DialogButton(label) {
                if (block == com.prismtv.launcher.data.ScreenTime.Block.Downtime) {
                    st.skipDowntime(ctx, me.id, if (ms == Long.MAX_VALUE) st.downtimeEnds(me) else System.currentTimeMillis() + ms)
                }
                if (ms == Long.MAX_VALUE) st.grant(ctx, me.id, 24 * 60 * 60_000L) else st.grant(ctx, me.id, ms)
                screenTimeTick++
                showBanner("Screen Time", if (ms == Long.MAX_VALUE) "No more limits today for ${nameOf(me)}." else "${nameOf(me)} has ${st.duration(ms)} more.", Icons.Rounded.CheckCircle, Cue.Confirm)
            }
            dialog = DialogSpec(
                "More Time for ${nameOf(me)}", "How much more?",
                listOf(grant("15 Minutes", 15 * 60_000L), grant("1 Hour", 60 * 60_000L), grant("All Day", Long.MAX_VALUE), DialogButton("Cancel") {}),
            )
            dialogFocus = 0
        }
    }

    // ================================================================= settings

    fun openSettings() = unlockSettings { showSettings() }

    private fun showSettings() {
        statusCache = null
        loadIconPacks()
        settingsStack = listOf(SettingsPage.Root)
        overlay = Overlay.Settings
    }

    fun closeOverlay() {
        overlay = Overlay.None
        openFolder = null
    }

    private fun pushPage(p: SettingsPage) {
        settingsStack = settingsStack + p
        settingsFocus[p] = when (p) {
            SettingsPage.Appearance -> AppearanceSetting.entries.indexOf(prefs.appearance)
            SettingsPage.Wallpaper -> settingsRows(p).indexOfFirst { it.checked }.coerceAtLeast(0)
            else -> 0
        }
    }

    private fun onOff(b: Boolean) = if (b) "On" else "Off"

    private var closeMethodCache: Triple<Int, LauncherPrefs, com.prismtv.launcher.system.AppCloser.Method>? = null

    /** The closing method for Settings rows, worked out once per refresh (it can ask Shizuku over IPC). */
    private fun shownCloseMethod(): com.prismtv.launcher.system.AppCloser.Method {
        val t = resumeTick
        val p = prefs
        closeMethodCache?.takeIf { it.first == t && it.second === p }?.let { return it.third }
        return closeMethod().also { closeMethodCache = Triple(t, p, it) }
    }

    /** Settings › Apps › Closing Apps on the main list: whether swiped-away apps really stop. */
    private fun closingLabel(): String =
        if (shownCloseMethod() == com.prismtv.launcher.system.AppCloser.Method.MemoryOnly) "Memory Only" else "Completely"

    private fun closingWithLabel(): String = when (shownCloseMethod()) {
        com.prismtv.launcher.system.AppCloser.Method.Shizuku -> "Shizuku"
        com.prismtv.launcher.system.AppCloser.Method.Root -> "Root Access"
        com.prismtv.launcher.system.AppCloser.Method.Accessibility -> "Power & Home Button"
        com.prismtv.launcher.system.AppCloser.Method.MemoryOnly -> if (prefs.closeCompletely) "Not Set Up" else "Freeing Memory"
    }

    private fun closingRows(): List<SettingsRow> {
        // Play edition: apps you swipe away have their memory freed. Closing them completely needs
        // Shizuku, root or pressing Force Stop through Accessibility, which are GitHub-edition only.
        if (BuildConfig.PLAY) return listOf(
            SettingsRow("cl:full", "Close Apps Completely", "APP SWITCHER", onOff(prefs.closeCompletely)),
            SettingsRow("cl:using", "Closing With", "APP SWITCHER", closingWithLabel()),
        )
        val shizuku = shizukuStatus()
        val a11y = PrismAccessibilityService.isEnabled
        return listOfNotNull(
            SettingsRow("cl:full", "Close Apps Completely", "APP SWITCHER", onOff(prefs.closeCompletely)),
            SettingsRow("cl:using", "Closing With", "APP SWITCHER", closingWithLabel()),
            SettingsRow(
                "cl:shizuku", "Shizuku", "WAYS TO CLOSE APPS", shizuku.label,
                chevron = shizuku != com.prismtv.launcher.system.ShizukuShell.Status.Ready,
            ),
            SettingsRow(
                "cl:a11y", "Power & Home Button", "WAYS TO CLOSE APPS",
                if (a11y && !PrismAccessibilityService.canCloseApps) "Turn Off and On" else onOff(a11y),
                chevron = true,
            ),
            if (com.prismtv.launcher.system.RootShell.present() || prefs.rootClose) {
                SettingsRow("cl:root", "Root Access", "WAYS TO CLOSE APPS", onOff(prefs.rootClose))
            } else {
                null
            },
            if (com.prismtv.launcher.system.SystemRecents.clearAllSupported) {
                SettingsRow("cl:recents", "Clear Android's Recent Apps", "ANDROID'S OWN APP SWITCHER")
            } else null,
        )
    }

    // ---------------------------------------------------------------- Power & Home Button

    private val a11yComponent get() = "${ctx.packageName}/com.prismtv.launcher.system.PrismAccessibilityService"

    /**
     * Power & Home Button (Prism TV's accessibility service). Some Google TV builds have no screen for
     * Android's Accessibility settings ("No application can handle this action"), so Prism TV turns it
     * on itself where it can (Shizuku, root or the TV's own debugging), keeping any other services on.
     */
    private fun setUpPowerAndHome() {
        if (BuildConfig.PLAY) {
            setUpPowerAndHomePlay()
            return
        }
        val shell = com.prismtv.launcher.system.ShellRunner.available(ctx, prefs.rootClose)
        val open = DialogButton("Open Accessibility Settings") {
            if (!SystemActions.openAccessibility(ctx)) {
                showBanner("Power & Home Button", "This TV has no Accessibility settings screen. Use Turn On for Me, or the command shown.", Icons.Rounded.Info)
            }
        }
        if (PrismAccessibilityService.isEnabled) {
            dialog = DialogSpec(
                "Power & Home Button Is On",
                "It opens Prism TV from the remote's Home button and runs the power menu, Remote Buttons, App Snapshots, " +
                    "Banners Over Apps and closing apps.",
                listOfNotNull(
                    if (shell) DialogButton("Turn Off") { turnOffPowerAndHome() } else open,
                    DialogButton("Keep On") {},
                ),
            )
            dialogFocus = 1
            return
        }
        dialog = DialogSpec(
            "Turn On Power & Home Button",
            "It lets the remote's Home button open Prism TV, and runs the power menu, Remote Buttons, App Snapshots, " +
                "Banners Over Apps and closing apps. " +
                (if (shell) "Select Turn On for Me." else "Find Prism TV Power & Home Button in the TV's Settings › System › Accessibility, or type these commands in a shell on the TV:"),
            listOfNotNull(
                DialogButton("Turn On for Me") { turnOnPowerAndHome() },
                open,
                DialogButton("Cancel") {},
            ),
            code = "settings put secure enabled_accessibility_services ${ctx.packageName}/.system.PrismAccessibilityService\n" +
                "settings put secure accessibility_enabled 1",
            codeNote = TypeNote,
        )
        dialogFocus = 0
    }

    /**
     * Play edition: Google Play asks apps that use Accessibility for something other than helping people
     * with disabilities to say exactly what it's used for, and to get a yes, before it's turned on. This is
     * that disclosure; Prism TV only opens Android's Accessibility settings after Agree.
     */
    private fun setUpPowerAndHomePlay() {
        val open = {
            if (!SystemActions.openAccessibility(ctx)) {
                showBanner("Power & Home Button", "This TV has no Accessibility settings screen.", Icons.Rounded.Info)
            }
        }
        if (PrismAccessibilityService.isEnabled) {
            dialog = DialogSpec(
                "Power & Home Button Is On",
                "It lets the remote's Home button open Prism TV, and runs Remote Buttons, the power menu, the Sleep Timer " +
                    "and Banners Over Apps. To turn it off, open Accessibility settings and turn off Prism TV Power & Home Button.",
                listOf(DialogButton("Open Accessibility Settings") { open() }, DialogButton("Keep On") {}),
            )
            dialogFocus = 1
            return
        }
        dialog = DialogSpec(
            "Use the Accessibility service?",
            "Prism TV uses Android's Accessibility service, \"Prism TV Power & Home Button\", so that:\n" +
                "• your remote's Home button opens Prism TV, even on TVs that keep another home screen,\n" +
                "• Remote Buttons can give your remote's buttons new actions,\n" +
                "• Control Centre can open the power menu and the Sleep Timer can put the TV to sleep,\n" +
                "• notification banners can appear over other apps, if you turn them on.\n\n" +
                "To do this, it sees which remote buttons you press and which app is on screen. It doesn't read or " +
                "record what's on screen or what you type, and nothing it sees leaves your TV or is shared with anyone. " +
                "You can turn it off at any time in Settings › Accessibility.",
            listOf(
                DialogButton("Agree & Open Settings") {
                    ctx.getSharedPreferences("prism_pro", Context.MODE_PRIVATE).edit().putLong("a11yConsentAt", System.currentTimeMillis()).apply()
                    open()
                },
                DialogButton("Not Now") {},
            ),
        )
        dialogFocus = 0
    }

    private fun turnOnPowerAndHome() {
        val c = a11yComponent
        // Keeps other accessibility services on; allows "restricted settings" for a sideloaded app first.
        val command = "appops set ${ctx.packageName} ACCESS_RESTRICTED_SETTINGS allow >/dev/null 2>&1; " +
            "S=\$(settings get secure enabled_accessibility_services); " +
            "case \"\$S\" in *PrismAccessibilityService*) ;; ''|null) settings put secure enabled_accessibility_services $c ;; " +
            "*) settings put secure enabled_accessibility_services \"\$S:$c\" ;; esac; " +
            "settings put secure accessibility_enabled 1"
        runShellSteps("Turn On Power & Home Button", command) { ok ->
            if (!ok) return@runShellSteps
            viewModelScope.launch {
                val on = settles(6000) { PrismAccessibilityService.isEnabled }
                resumeTick++
                if (on) showBanner("Power & Home Button", "Power & Home Button is on.", Icons.Rounded.CheckCircle, Cue.Confirm)
                else showBanner("Power & Home Button", "Android didn't start it. Restart the TV, then try again.", Icons.Rounded.Info)
            }
        }
    }

    private fun turnOffPowerAndHome() {
        val command = "S=\$(settings get secure enabled_accessibility_services); " +
            "N=\$(echo \"\$S\" | tr ':' '\\n' | grep -v PrismAccessibilityService | tr '\\n' ':' | sed -e 's/:*\$//'); " +
            "if [ -z \"\$N\" ]; then settings delete secure enabled_accessibility_services; else settings put secure enabled_accessibility_services \"\$N\"; fi"
        runShellSteps("Turn Off Power & Home Button", command) { ok ->
            if (!ok) return@runShellSteps
            viewModelScope.launch {
                val off = settles(4000) { !PrismAccessibilityService.isEnabled }
                resumeTick++
                if (off) showBanner("Power & Home Button", "Power & Home Button is off.", Icons.Rounded.CheckCircle, Cue.Confirm)
            }
        }
    }

    // ---------------------------------------------------------------- Home app

    private val homeIntent get() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /** The activity Android opens for Home right now, or null when Android would ask. */
    private fun resolvedHome(): android.content.pm.ActivityInfo? = runCatching {
        ctx.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            ?.takeIf { it.packageName != "android" }
    }.getOrNull()

    /** The TV's Home app right now (its package), or null when Android would ask. */
    private fun currentHomePackage(): String? = resolvedHome()?.packageName

    private fun isSetupWizard(pkg: String, cls: String?): Boolean {
        val label = runCatching {
            if (cls != null) ctx.packageManager.getActivityInfo(android.content.ComponentName(pkg, cls), PackageManager.MATCH_DISABLED_COMPONENTS)
                .loadLabel(ctx.packageManager).toString()
            else appName(pkg)
        }.getOrNull()
        return com.prismtv.launcher.system.HomeApps.isSetupWizard(pkg, cls, label)
    }

    /**
     * The TV's setup wizard, when it's still what Android opens for Home. It should switch that off when
     * setup ends; some TVs (a Xiaomi) don't, and it then outranks any Home app the person picks.
     */
    private fun stuckSetupWizard(): android.content.pm.ActivityInfo? =
        resolvedHome()?.takeIf { it.packageName != ctx.packageName && isSetupWizard(it.packageName, it.name) }

    /** The TV's own launcher (Google TV, Android TV Home, PatchWall…): package and activity. Never the setup wizard. */
    private fun stockLauncher(): android.content.ComponentName? {
        prefs.hiddenLauncher.takeIf { it.contains('/') }?.let { android.content.ComponentName.unflattenFromString(it) }
            ?.takeIf { !isSetupWizard(it.packageName, it.className) }?.let { return it }
        val known = com.prismtv.launcher.system.HomeApps.knownLaunchers
        @Suppress("DEPRECATION")
        val all = runCatching {
            ctx.packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DISABLED_COMPONENTS)
        }.getOrDefault(emptyList())
            .filter { ri ->
                val ai = ri.activityInfo
                ai.packageName != ctx.packageName && ai.packageName != "com.android.settings" && ai.packageName != "com.android.tv.settings" &&
                    !com.prismtv.launcher.system.HomeApps.isSetupWizard(ai.packageName, ai.name, runCatching { ri.loadLabel(ctx.packageManager).toString() }.getOrNull())
            }
            .map { android.content.ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
        return all.minByOrNull { c -> known.indexOf(c.packageName).let { if (it < 0) 99 else it } }
    }

    private fun appName(pkg: String): String = runCatching {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS)).toString()
    }.getOrDefault(pkg)

    private fun homeAppLabel(): String = when (val pkg = currentHomePackage()) {
        ctx.packageName -> "Prism TV"
        null -> "Not Chosen"
        else -> appName(pkg)
    }

    /**
     * Settings' main list is rebuilt twice per D-pad press, and asking Android for the Home app takes two
     * or three calls into its package manager. The answer is reused for up to a second, and asked again
     * as soon as Prism comes back or changes the Home app (both bump [resumeTick]).
     */
    private var homeLabelCache: Triple<Int, Long, String>? = null

    private fun shownHomeAppLabel(): String {
        val t = resumeTick
        val now = SystemClock.elapsedRealtime()
        homeLabelCache?.takeIf { it.first == t && now - it.second < 1_000L }?.let { return it.third }
        return homeAppLabel().also { homeLabelCache = Triple(t, now, it) }
    }

    /** The TV's own launcher is turned off (pm disable-user), by Prism TV or anyone else. */
    private fun launcherHidden(stock: android.content.ComponentName?): Boolean {
        if (stock == null) return prefs.hiddenLauncher.isNotEmpty()
        return runCatching {
            val state = ctx.packageManager.getApplicationEnabledSetting(stock.packageName)
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }.getOrDefault(prefs.hiddenLauncher.isNotEmpty())
    }

    /**
     * Settings › Home App: one choice, like tvOS's lists: Prism TV or the TV's own launcher. Choosing
     * one runs the command (through Shizuku, root or the TV's own debugging), then checks what Android
     * actually did, since Google TV applies the change a moment later, or keeps its launcher anyway.
     */
    private fun homeAppRows(): List<SettingsRow> {
        val stock = stockLauncher()
        val stockName = stock?.let { appName(it.packageName) } ?: "TV's Own Launcher"
        val hidden = launcherHidden(stock)
        val home = currentHomePackage()
        val isPrism = home == ctx.packageName
        val isStock = stock != null && home == stock.packageName
        val ready = isStock && !hidden
        val wizard = stuckSetupWizard()
        return listOfNotNull(
            if (wizard != null) {
                SettingsRow("ha:wizard", "Finish ${appName(wizard.packageName)}", "THE TV'S SETUP WIZARD IS STILL HOME", chevron = true)
            } else null,
            SettingsRow("ha:prism", "Prism TV", "HOME APP", selectable = true, checked = isPrism),
            if (stock != null) {
                SettingsRow("ha:stock", stockName, "HOME APP", if (hidden) "Hidden" else null, selectable = true, checked = isStock)
            } else null,
            if (stock != null) {
                SettingsRow(
                    "ha:restore", "Switch Back to $stockName", "BEFORE UNINSTALLING PRISM TV",
                    if (ready) "Done" else null, chevron = !ready,
                )
            } else null,
        )
    }

    private fun selectHomeApp(id: String) {
        val stock = stockLauncher()
        val stockName = stock?.let { appName(it.packageName) } ?: "the TV's launcher"
        when (id) {
            "ha:prism" -> makePrismHome()
            "ha:wizard" -> stuckSetupWizard()?.let { askFinishWizard(it.packageName) }
                ?: showBanner("Home App", "The setup wizard isn't the Home app any more.", Icons.Rounded.CheckCircle, Cue.Confirm)
            "ha:stock", "ha:restore" -> when {
                stock == null -> showBanner("Home App", "This TV has no other launcher to go back to.", Icons.Rounded.Info)
                currentHomePackage() == stock.packageName && !launcherHidden(stock) ->
                    showBanner(
                        "Home App",
                        if (id == "ha:restore") "$stockName is the Home app, so you can uninstall Prism TV safely." else "$stockName is the Home app.",
                        Icons.Rounded.CheckCircle, Cue.Confirm,
                    )
                else -> {
                    dialog = DialogSpec(
                        "Switch Back to $stockName?",
                        "$stockName becomes the Home app again" + (if (launcherHidden(stock)) " (and is shown again)" else "") +
                            ". Do this before uninstalling Prism TV, so the TV always has a launcher to start to. You can choose Prism TV here again at any time.",
                        listOf(DialogButton("Switch Back") { switchToStock(stock, stockName) }, DialogButton("Cancel") {}),
                    )
                    dialogFocus = 0
                }
            }
        }
    }

    /** Waits up to [ms] for [check]: Android (Google TV especially) applies Home changes a moment later. */
    private suspend fun settles(ms: Long, check: () -> Boolean): Boolean {
        val until = SystemClock.elapsedRealtime() + ms
        while (true) {
            if (check()) return true
            if (SystemClock.elapsedRealtime() >= until) return false
            delay(400)
        }
    }

    private fun makePrismHome() {
        val stock = stockLauncher()
        val stockName = stock?.let { appName(it.packageName) } ?: "the TV's launcher"
        if (currentHomePackage() == ctx.packageName) {
            showBanner(
                "Home App",
                if (launcherHidden(stock)) "Prism TV is the Home app. $stockName is hidden: switch back here before you uninstall Prism TV."
                else "Prism TV is the Home app.",
                Icons.Rounded.CheckCircle, Cue.Confirm,
            )
            return
        }
        val main = "${ctx.packageName}/com.prismtv.launcher.MainActivity"
        runShellSteps("Make Prism TV the Home App", "cmd package set-home-activity --user 0 $main") { ok ->
            if (!ok) return@runShellSteps
            viewModelScope.launch {
                if (settles(6000) { currentHomePackage() == ctx.packageName }) {
                    resumeTick++
                    showBanner("Home App", "Prism TV is now the Home app.", Icons.Rounded.CheckCircle, Cue.Confirm)
                    return@launch
                }
                resumeTick++
                stuckSetupWizard()?.let {
                    askFinishWizard(it.packageName)
                    return@launch
                }
                if (stock == null) {
                    showBanner("Home App", "Android didn't change the Home app. Press Home and choose Prism TV if Android asks.", Icons.Rounded.Info)
                    return@launch
                }
                // Google TV accepts the command but keeps its own launcher: offer to hide it.
                dialog = DialogSpec(
                    "$stockName Stayed as Home",
                    "Android accepted the change, but $stockName is still the Home app (Google TV does this). Prism TV can hide " +
                        "$stockName so Prism TV becomes the Home app. Switch back here before you uninstall Prism TV.",
                    listOf(DialogButton("Hide $stockName") { hideLauncher(stock, stockName) }, DialogButton("Not Now") {}),
                )
                dialogFocus = 0
            }
        }
    }

    /** The setup wizard still answers Home: offer to do what it should have done when setup ended. */
    private fun askFinishWizard(wizardPackage: String) {
        val name = appName(wizardPackage)
        dialog = DialogSpec(
            "Finish $name?",
            "$name is still set to open when you press Home, so Android keeps it as the Home app whatever you choose. " +
                "Prism TV can finish it: mark setup as done, turn $name off and make Prism TV the Home app. " +
                "Your apps and accounts aren't touched. To undo it, run pm enable $wizardPackage.",
            listOf(DialogButton("Finish Setup") { finishWizard(wizardPackage, name) }, DialogButton("Cancel") {}),
        )
        dialogFocus = 0
    }

    private fun finishWizard(wizardPackage: String, name: String) {
        val main = "${ctx.packageName}/com.prismtv.launcher.MainActivity"
        runShellSteps("Finish $name", com.prismtv.launcher.system.HomeApps.finishWizardCommand(wizardPackage, main)) { ok ->
            if (!ok) return@runShellSteps
            viewModelScope.launch {
                val done = settles(6000) { currentHomePackage() == ctx.packageName }
                resumeTick++
                when {
                    done -> showBanner("Home App", "$name is finished and Prism TV is now the Home app.", Icons.Rounded.CheckCircle, Cue.Confirm)
                    stuckSetupWizard() == null -> showBanner("Home App", "$name is finished. Press Home and choose Prism TV if Android asks.", Icons.Rounded.Info)
                    else -> showBanner("Home App", "Android kept $name as Home. Restart the TV, then choose Prism TV here again.", Icons.Rounded.Info)
                }
            }
        }
    }

    private fun hideLauncher(stock: android.content.ComponentName, name: String) {
        val main = "${ctx.packageName}/com.prismtv.launcher.MainActivity"
        runShellSteps(
            "Hide $name",
            "pm disable-user --user 0 ${stock.packageName} && cmd package set-home-activity --user 0 $main",
        ) { ok ->
            if (!ok) return@runShellSteps
            updatePrefs(prefs.copy(hiddenLauncher = stock.flattenToString()))
            viewModelScope.launch {
                val done = settles(6000) { currentHomePackage() == ctx.packageName }
                resumeTick++
                if (done) {
                    showBanner("Home App", "Prism TV is now the Home app and $name is hidden. Switch back here before you uninstall Prism TV.", Icons.Rounded.CheckCircle, Cue.Confirm)
                } else {
                    showBanner("Home App", "$name is hidden. If Android asks which app to use for Home, choose Prism TV.", Icons.Rounded.Info)
                }
            }
        }
    }

    private fun switchToStock(stock: android.content.ComponentName, name: String) {
        runShellSteps(
            "Switch Back to $name",
            "pm enable ${stock.packageName} >/dev/null 2>&1; cmd package set-home-activity --user 0 ${stock.flattenToString()}",
        ) { ok ->
            if (!ok) return@runShellSteps
            updatePrefs(prefs.copy(hiddenLauncher = ""))
            viewModelScope.launch {
                val done = settles(6000) { currentHomePackage() == stock.packageName && !launcherHidden(stock) }
                resumeTick++
                val homeButton = if (PrismAccessibilityService.isEnabled && prefs.homeOverride)
                    " The remote's Home button still opens Prism TV while Home Button Opens Prism TV is on." else ""
                if (done) {
                    showBanner("Home App", "$name is the Home app again, so you can uninstall Prism TV safely.$homeButton", Icons.Rounded.CheckCircle, Cue.Confirm)
                } else if (!launcherHidden(stock)) {
                    showBanner("Home App", "$name is shown again. If Android asks which app to use for Home, choose $name.", Icons.Rounded.Info)
                } else {
                    showBanner("Home App", "$name couldn't be shown again. Try once more, or run pm enable ${stock.packageName} in a shell.", Icons.Rounded.Info)
                }
            }
        }
    }

    /** Runs [command] through Shizuku, root or the TV's own adb, telling you what happened. */
    private fun runShellSteps(title: String, command: String, then: (Boolean) -> Unit) {
        if (!com.prismtv.launcher.system.ShellRunner.available(ctx, prefs.rootClose)) {
            dialog = DialogSpec(
                title,
                if (BuildConfig.PLAY) "Only Android's own shell can do this. Type this command in a shell on the TV:"
                else "This needs USB debugging (Settings › Apps › Closing Apps › Shizuku shows how) or Shizuku. Or type this " +
                    "command yourself:",
                listOf(DialogButton("OK") {}),
                code = command,
                codeNote = TypeNote,
            )
            dialogFocus = 0
            return
        }
        if (!com.prismtv.launcher.system.ShizukuShell.ready()) {
            showBanner(title, "If your TV asks to allow debugging, tick Always allow and select Allow.", Icons.Rounded.Info, null)
        }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                com.prismtv.launcher.system.ShellRunner.run(ctx, command, allowPrompt = true, rootAllowed = prefs.rootClose)
            }
            resumeTick++
            val ok = r is com.prismtv.launcher.system.LocalAdb.Result.Ok && (r.exit == null || r.exit == 0)
            if (!ok) {
                val why = when (r) {
                    is com.prismtv.launcher.system.LocalAdb.Result.Ok -> r.output.lines().firstOrNull { it.isNotBlank() } ?: "It didn't work."
                    com.prismtv.launcher.system.LocalAdb.Result.Unreachable -> "Prism TV can't reach this TV's debugging."
                    com.prismtv.launcher.system.LocalAdb.Result.NotAllowed -> "The TV didn't allow debugging for Prism TV."
                    com.prismtv.launcher.system.LocalAdb.Result.Off -> "USB debugging is off."
                    is com.prismtv.launcher.system.LocalAdb.Result.Failed -> r.why
                }
                dialog = DialogSpec(title, "$why You can also type this command yourself:", listOf(DialogButton("OK") {}), code = command, codeNote = TypeNote)
                dialogFocus = 0
            }
            then(ok)
        }
    }

    // ---------------------------------------------------------------- Shizuku, step by step

    /** Install Shizuku: download progress in percent while it downloads. */
    var shizukuDownload by mutableStateOf<Int?>(null)
        private set
    var shizukuStarting by mutableStateOf(false)
        private set
    private var shizukuAutoStartTried = false

    private val shizukuSteps = listOf("sz:install", "sz:debug", "sz:start", "sz:allow")

    private fun shizukuStepDone(id: String): Boolean = when (id) {
        "sz:install" -> com.prismtv.launcher.system.ShizukuShell.installed(ctx)
        "sz:debug" -> com.prismtv.launcher.system.LocalAdb.enabled(ctx)
        "sz:start" -> shizukuRunning()
        "sz:allow" -> com.prismtv.launcher.system.ShizukuShell.ready()
        else -> true
    }

    private var shizukuCache: Pair<Int, com.prismtv.launcher.system.ShizukuShell.Status>? = null

    /** Shizuku's state for Settings rows, read once per refresh (Shizuku connecting bumps [resumeTick]). */
    private fun shizukuStatus(): com.prismtv.launcher.system.ShizukuShell.Status {
        val t = resumeTick
        shizukuCache?.takeIf { it.first == t }?.let { return it.second }
        return com.prismtv.launcher.system.ShizukuShell.status(ctx).also { shizukuCache = t to it }
    }

    private fun shizukuRunning(): Boolean = shizukuStatus().let {
        it != com.prismtv.launcher.system.ShizukuShell.Status.NotInstalled && it != com.prismtv.launcher.system.ShizukuShell.Status.NotRunning
    }

    /** Puts focus on the first step that's still to do (or the last one when all are done). */
    private fun focusNextShizukuStep() {
        val next = shizukuSteps.indexOfFirst { !shizukuStepDone(it) }
        settingsFocus[SettingsPage.Shizuku] = if (next >= 0) next else shizukuSteps.size - 1
    }

    private fun shizukuRows(): List<SettingsRow> {
        val status = shizukuStatus()
        val installed = status != com.prismtv.launcher.system.ShizukuShell.Status.NotInstalled
        val running = shizukuRunning()
        val debugging = com.prismtv.launcher.system.LocalAdb.enabled(ctx)
        val download = shizukuDownload
        return listOfNotNull(
            SettingsRow(
                "sz:install", "Install Shizuku", "SET UP SHIZUKU",
                when {
                    download != null -> "Downloading $download%"
                    installed -> "Installed"
                    else -> "Install"
                },
                chevron = !installed && download == null,
            ),
            SettingsRow("sz:debug", "Turn On USB Debugging", "SET UP SHIZUKU", if (debugging) "Turned On" else "Turn On", chevron = !debugging),
            SettingsRow(
                "sz:start", "Start Shizuku", "SET UP SHIZUKU",
                when {
                    shizukuStarting -> "Starting…"
                    running -> "Running"
                    else -> "Start"
                },
                chevron = !running && !shizukuStarting,
            ),
            SettingsRow(
                "sz:allow", "Allow Prism TV", "SET UP SHIZUKU",
                when (status) {
                    com.prismtv.launcher.system.ShizukuShell.Status.Ready -> "Allowed"
                    com.prismtv.launcher.system.ShizukuShell.Status.Denied -> "Not Allowed"
                    com.prismtv.launcher.system.ShizukuShell.Status.TooOld -> "Update Shizuku"
                    else -> "Allow"
                },
                chevron = status != com.prismtv.launcher.system.ShizukuShell.Status.Ready,
            ),
            SettingsRow("sz:boot", "Start Shizuku When the TV Starts", "OPTIONS", onOff(prefs.shizukuAutoStart)),
            if (installed) SettingsRow("sz:open", "Open Shizuku", "OPTIONS", chevron = true) else null,
        )
    }

    private fun selectShizuku(id: String) {
        val p = prefs
        when (id) {
            "sz:install" -> installShizuku()
            "sz:debug" -> when {
                com.prismtv.launcher.system.LocalAdb.enabled(ctx) -> {
                    showBanner("USB Debugging", "USB debugging is on. Next, start Shizuku.", Icons.Rounded.CheckCircle, Cue.Confirm)
                    focusNextShizukuStep()
                }
                com.prismtv.launcher.system.LocalAdb.developerOptions(ctx) -> {
                    dialog = DialogSpec(
                        "Turn On USB Debugging",
                        "1. Select Open Developer Options.\n2. Turn on USB debugging. If there's also Network debugging " +
                            "or ADB over network, turn that on too.\n3. Press Back to return here.",
                        listOf(
                            DialogButton("Open Developer Options") { SystemActions.open(ctx, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) },
                            DialogButton("Cancel") {},
                        ),
                    )
                    dialogFocus = 0
                }
                else -> {
                    dialog = DialogSpec(
                        "Turn On Developer Options",
                        "USB debugging lives in Developer options, which are hidden at first.\n1. Select Open About.\n" +
                            "2. Select Android TV OS build (or Build) seven times, until it says you're a developer.\n" +
                            "3. Press Back to return here, then select Turn On USB Debugging again.",
                        listOf(
                            DialogButton("Open About") { SystemActions.open(ctx, Settings.ACTION_DEVICE_INFO_SETTINGS) },
                            DialogButton("Cancel") {},
                        ),
                    )
                    dialogFocus = 0
                }
            }
            "sz:start" -> startShizuku()
            "sz:allow" -> when (com.prismtv.launcher.system.ShizukuShell.status(ctx)) {
                com.prismtv.launcher.system.ShizukuShell.Status.Ready ->
                    showBanner("Shizuku", "Prism TV is allowed. Apps you swipe away now close completely.", Icons.Rounded.CheckCircle, Cue.Confirm)
                com.prismtv.launcher.system.ShizukuShell.Status.NotInstalled, com.prismtv.launcher.system.ShizukuShell.Status.NotRunning ->
                    showBanner("Shizuku", "Start Shizuku first.", Icons.Rounded.Info)
                com.prismtv.launcher.system.ShizukuShell.Status.NeedsPermission ->
                    com.prismtv.launcher.system.ShizukuShell.requestPermission { granted ->
                        viewModelScope.launch {
                            resumeTick++
                            if (granted) {
                                showBanner("Shizuku", "All set. Apps you swipe away now close completely.", Icons.Rounded.CheckCircle, Cue.Confirm)
                            } else {
                                showBanner("Shizuku", "Prism TV wasn't allowed. Open Shizuku to allow it in its app list.", Icons.Rounded.Info)
                            }
                        }
                    }
                else -> openShizukuApp("Allow Prism TV in Shizuku's app list, then come back.")
            }
            "sz:boot" -> updatePrefs(p.copy(shizukuAutoStart = !p.shizukuAutoStart))
            "sz:open" -> openShizukuApp(null)
        }
    }

    private fun openShizukuApp(hint: String?) {
        val pkg = com.prismtv.launcher.system.ShizukuShell.Package
        val open = ctx.packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (open != null && SystemActions.start(ctx, open)) {
            hint?.let { showBanner("Shizuku", it, Icons.Rounded.Info) }
        } else {
            showBanner("Shizuku", "Shizuku couldn't be opened.", Icons.Rounded.Info)
        }
    }

    private fun installShizuku() {
        if (BuildConfig.PLAY) return
        if (com.prismtv.launcher.system.ShizukuShell.installed(ctx)) {
            showBanner("Shizuku", "Shizuku is installed. Next, turn on USB debugging.", Icons.Rounded.CheckCircle, Cue.Confirm)
            focusNextShizukuStep()
            return
        }
        if (shizukuDownload != null) return
        if (!Updater.canInstall(ctx)) {
            if (!SystemActions.start(ctx, Updater.permissionIntent(ctx))) SystemActions.open(ctx, Settings.ACTION_SECURITY_SETTINGS)
            showBanner("Install Shizuku", "Allow Prism TV to install apps, then come back and select Install Shizuku again.", Icons.Rounded.Info)
            return
        }
        shizukuDownload = 0
        viewModelScope.launch {
            val apk = com.prismtv.launcher.system.ShizukuSetup.download(ctx) { f -> shizukuDownload = (f * 100).toInt() }
            if (apk == null) {
                shizukuDownload = null
                showBanner("Install Shizuku", "Shizuku couldn't be downloaded. Check the TV's internet connection and try again.", Icons.Rounded.Info)
                return@launch
            }
            runCatching { com.prismtv.launcher.system.ShizukuSetup.install(ctx, apk) }.onFailure {
                showBanner("Install Shizuku", "Shizuku couldn't be installed.", Icons.Rounded.Info)
            }
            shizukuDownload = null
        }
    }

    private fun startShizuku() {
        if (shizukuStarting) return
        if (shizukuRunning()) {
            showBanner("Shizuku", "Shizuku is running. Next, allow Prism TV.", Icons.Rounded.CheckCircle, Cue.Confirm)
            focusNextShizukuStep()
            return
        }
        shizukuStarting = true
        // The TV may ask "Allow debugging?" now: tick Always allow so Shizuku can start again after a restart.
        showBanner("Start Shizuku", "If your TV asks to allow debugging, tick Always allow and select Allow.", Icons.Rounded.Info, null)
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { com.prismtv.launcher.system.ShizukuSetup.start(ctx, allowPrompt = true) }
            shizukuStarting = false
            resumeTick++
            when (r) {
                com.prismtv.launcher.system.ShizukuSetup.StartResult.Started -> {
                    showBanner("Shizuku", "Shizuku is running. Next, allow Prism TV.", Icons.Rounded.CheckCircle, Cue.Confirm)
                    if (overlay == Overlay.Settings && settingsStack.last() == SettingsPage.Shizuku) focusNextShizukuStep()
                }
                com.prismtv.launcher.system.ShizukuSetup.StartResult.NotInstalled -> showBanner("Shizuku", "Install Shizuku first.", Icons.Rounded.Info)
                com.prismtv.launcher.system.ShizukuSetup.StartResult.DebuggingOff -> showBanner("Shizuku", "Turn on USB debugging first.", Icons.Rounded.Info)
                com.prismtv.launcher.system.ShizukuSetup.StartResult.NotAllowed ->
                    showBanner("Shizuku", "The TV didn't allow debugging for Prism TV. Select Start Shizuku to ask again.", Icons.Rounded.Info)
                com.prismtv.launcher.system.ShizukuSetup.StartResult.Unreachable -> {
                    dialog = DialogSpec(
                        "Start Shizuku",
                        "Prism TV couldn't reach this TV's debugging. If Developer options has Network debugging (or ADB over " +
                            "network), turn it on and try again.\n\nOtherwise open Shizuku once, then type this in ATV Connect's Shell " +
                            "(or another shell connected to the TV):",
                        listOf(DialogButton("OK") {}),
                        code = "sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh",
                        codeNote = TypeNote,
                    )
                    dialogFocus = 0
                    cue(Cue.Error)
                }
                is com.prismtv.launcher.system.ShizukuSetup.StartResult.Failed ->
                    showBanner("Shizuku", "Shizuku didn't start: ${r.detail}", Icons.Rounded.Info)
            }
        }
    }

    /** Once per run, a little after Prism TV opens: Shizuku stops when the TV restarts, so start it again. */
    private fun maybeStartShizuku() {
        if (BuildConfig.PLAY) return
        if (shizukuAutoStartTried || !prefs.shizukuAutoStart || debugDriven) return
        shizukuAutoStartTried = true
        if (!com.prismtv.launcher.system.ShizukuShell.installed(ctx)) return
        viewModelScope.launch {
            delay(6_000)
            val started = withContext(Dispatchers.IO) { com.prismtv.launcher.system.ShizukuSetup.startQuietly(ctx) }
            if (started) resumeTick++
        }
    }

    /**
     * For things only Android's shell may change: a Do It for Me button (through the TV's own debugging,
     * [com.prismtv.launcher.system.LocalAdb]) and the exact command for doing it yourself.
     */
    private fun shellDialog(
        title: String,
        intro: String,
        command: String,
        done: () -> Boolean,
        doneMessage: String,
        open: Pair<String, () -> Unit>? = null,
    ) {
        dialog = DialogSpec(
            title,
            if (!BuildConfig.PLAY) intro else intro
                .replace("select Do It for Me, or type", "type")
                .replace("Select Do It for Me, or type", "Type")
                .replace("Select Ask Android or Do It for Me, or type", "Select Ask Android, or type"),
            listOfNotNull(
                open?.let { (label, action) -> DialogButton(label, action) },
                // Play edition: no Shizuku, root or adb, so only the command to type yourself.
                if (BuildConfig.PLAY) null else DialogButton("Do It for Me") { runShellForYou(title, command, done, doneMessage) },
                DialogButton("Cancel") {},
            ),
            code = command,
            codeNote = TypeNote,
        )
        dialogFocus = 0
    }

    /**
     * Recommendations from Apps (Android's TV listings permission): Android's own question where the TV
     * shows it, or granted through the shell in one press (some TVs never ask, or stop asking after a No).
     */
    private fun allowRecommendations() {
        shellDialog(
            title = "Allow Recommendations",
            intro = "Shows the cards your apps publish, like Continue Watching. " +
                "Select Ask Android or Do It for Me, or type this command yourself:",
            command = "pm grant ${ctx.packageName} $TV_LISTINGS",
            done = { hasRecommendationAccess().also { if (it) refreshRecommendations(force = true) } },
            doneMessage = "Done. Cards from your apps now show on the Top Shelf and in widgets.",
            open = "Ask Android" to { permissionRequestTick++; Unit },
        )
    }

    private fun runShellForYou(title: String, command: String, done: () -> Boolean, doneMessage: String) {
        if (!com.prismtv.launcher.system.ShellRunner.available(ctx, prefs.rootClose)) {
            dialog = DialogSpec(
                "Turn On USB Debugging",
                "Do It for Me works through the TV's USB debugging, which is off. Settings › Apps › Closing Apps › Shizuku › " +
                    "Turn On USB Debugging shows how to turn it on. Or type the command in ATV Connect's Shell.",
                listOf(DialogButton("OK") {}),
            )
            dialogFocus = 0
            return
        }
        if (!com.prismtv.launcher.system.ShizukuShell.ready()) {
            showBanner(title, "If your TV asks to allow debugging, tick Always allow and select Allow.", Icons.Rounded.Info, null)
        }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                com.prismtv.launcher.system.ShellRunner.run(ctx, command, allowPrompt = true, rootAllowed = prefs.rootClose)
            }
            resumeTick++
            when {
                r is com.prismtv.launcher.system.LocalAdb.Result.Ok && done() ->
                    showBanner(title, doneMessage, Icons.Rounded.CheckCircle, Cue.Confirm)
                r is com.prismtv.launcher.system.LocalAdb.Result.Ok ->
                    showBanner(title, "That didn't take${r.output.lines().firstOrNull { it.isNotBlank() }?.let { ": $it" } ?: "."}", Icons.Rounded.Info)
                r == com.prismtv.launcher.system.LocalAdb.Result.Unreachable ->
                    showBanner(title, "Prism TV can't reach this TV's debugging. Type the command in a shell instead.", Icons.Rounded.Info)
                r == com.prismtv.launcher.system.LocalAdb.Result.NotAllowed ->
                    showBanner(title, "The TV didn't allow debugging for Prism TV. Try again and select Allow.", Icons.Rounded.Info)
                r == com.prismtv.launcher.system.LocalAdb.Result.Off ->
                    showBanner(title, "Turn on USB debugging in Developer options first.", Icons.Rounded.Info)
                r is com.prismtv.launcher.system.LocalAdb.Result.Failed ->
                    showBanner(title, "That didn't work: ${r.why}", Icons.Rounded.Info)
            }
        }
    }

    private fun selectClosing(id: String) {
        val p = prefs
        when (id) {
            "cl:full" -> updatePrefs(p.copy(closeCompletely = !p.closeCompletely))
            "cl:using" -> showBanner(
                "Closing Apps",
                when (closeMethod()) {
                    com.prismtv.launcher.system.AppCloser.Method.Shizuku -> "Shizuku force stops apps you swipe away, straight away."
                    com.prismtv.launcher.system.AppCloser.Method.Root -> "Root access force stops apps you swipe away, straight away."
                    com.prismtv.launcher.system.AppCloser.Method.Accessibility ->
                        "Prism TV presses Force Stop in Settings for you, behind a still of the screen. It takes a moment."
                    com.prismtv.launcher.system.AppCloser.Method.MemoryOnly ->
                        if (BuildConfig.PLAY) "Apps you swipe away have their memory freed. Android may keep a few of them ready in the background."
                        else if (p.closeCompletely) "Turn on Shizuku or Power & Home Button below to close apps completely."
                        else "Apps you swipe away have their memory freed, and may keep running."
                },
                Icons.Rounded.Info,
            )
            "cl:a11y" -> setUpPowerAndHome()
            "cl:recents" -> runShellSteps("Clear Android's Recent Apps", com.prismtv.launcher.system.SystemRecents.ClearAll) { ok ->
                if (ok) showBanner(
                    "Closing Apps",
                    if (Build.VERSION.SDK_INT >= 31) "Android's own app switcher is clear. Apps you close in Prism TV leave it too, with Shizuku or root."
                    else "Open apps are gone from Android's own app switcher. Android 11 keeps cards of apps that had already stopped: dismiss those there once.",
                    Icons.Rounded.CheckCircle, Cue.Confirm,
                )
            }
            "cl:shizuku" -> {
                pushPage(SettingsPage.Shizuku)
                focusNextShizukuStep()
            }
            "cl:root" -> if (p.rootClose) {
                updatePrefs(p.copy(rootClose = false))
            } else {
                viewModelScope.launch {
                    val ok = withContext(Dispatchers.IO) { com.prismtv.launcher.system.RootShell.test() }
                    if (ok) {
                        updatePrefs(prefs.copy(rootClose = true))
                        showBanner("Root Access", "Apps you swipe away now close completely.", Icons.Rounded.CheckCircle, Cue.Confirm)
                    } else {
                        showBanner("Root Access", "Root access wasn't allowed.", Icons.Rounded.Info)
                    }
                }
            }
        }
    }

    /** A page's rows. Without Pro, settings that need it say so on the right. */
    fun settingsRows(page: SettingsPage): List<SettingsRow> {
        var rows = rawSettingsRows(page)
        if (BuildConfig.PLAY) rows = rows.map { r ->
            val t = Edition.text(r.title)
            val v = r.value?.let(Edition::text)
            if (t == r.title && v == r.value) r else r.copy(title = t, value = v)
        }
        if (proActive) return rows
        val p = prefs
        return rows.map { r -> if (r.value == null && r.toggle == null && needsPro(r.id, p)) r.copy(value = "Pro") else r }
    }

    private fun rawSettingsRows(page: SettingsPage): List<SettingsRow> = when (page) {
        // Reading resumeTick refreshes rows (Recent Apps Access, Power & Home Button) as soon as the
        // user comes back from Android's settings.
        // Grouped like tvOS: you and how it looks, then apps, the remote, and the system.
        SettingsPage.Root -> resumeTick.let { _ -> listOfNotNull(
            if (BuildConfig.PLAY) SettingsRow("pro", "Prism Pro", "GENERAL", proLabel(), chevron = true) else null,
            SettingsRow("profile", "Profiles", "GENERAL", profileName(), chevron = true),
            SettingsRow("appearance", "Appearance", "GENERAL", prefs.appearance.label, chevron = true),
            SettingsRow("home", "Home Screen", "GENERAL", chevron = true),
            SettingsRow("screensaver", "Screensaver", "GENERAL", prefs.aerialSource.label, chevron = true),
            SettingsRow("keyboard", "Keyboard", "GENERAL", keyboardStatus(), chevron = true),
            SettingsRow("sounds", "Navigation Sounds", "GENERAL", onOff(prefs.sounds)),
            SettingsRow("apps", "Apps", "APPS", if (prefs.hiddenApps.isEmpty()) null else "${prefs.hiddenApps.size} Hidden", chevron = true),
            SettingsRow("startupApp", "Open at Startup", "APPS", startupAppLabel(), chevron = true),
            SettingsRow("iconPack", "Icon Pack", "APPS", iconPackLabel(), chevron = true),
            // Only TVs have inputs; on a box there'd be nothing behind this row.
            if (inputApps.isEmpty()) null else SettingsRow("inputs", "Inputs", "APPS", inputApps.size.toString(), chevron = true),
            SettingsRow("closing", "Closing Apps", "APPS", closingLabel(), chevron = true),
            SettingsRow("homeApp", "Home App", "REMOTE & POWER", shownHomeAppLabel(), chevron = true),
            SettingsRow("a11y", "Power & Home Button", "REMOTE & POWER", onOff(PrismAccessibilityService.isEnabled), chevron = true),
            SettingsRow("remote", "Remote Buttons", "REMOTE & POWER", remoteButtonsLabel(), chevron = true),
            SettingsRow("homeOverride", "Home Button Opens Prism TV", "REMOTE & POWER", onOff(prefs.homeOverride)),
            SettingsRow("startup", "Open Prism TV at Startup", "REMOTE & POWER", onOff(prefs.openAtStartup)),
            SettingsRow("sleep", "Sleep Timer", "REMOTE & POWER", sleepLabel()),
            // Google Play updates the Play edition.
            if (BuildConfig.PLAY) null else SettingsRow("update", "Software Update", "SYSTEM", if (availableUpdate != null) "Update Available" else null, chevron = true),
            SettingsRow("backup", "Backup & Restore", "SYSTEM", chevron = true),
            SettingsRow("access", "Privacy & Access", "SYSTEM", accessLabel(), chevron = true),
            SettingsRow("about", "About", "SYSTEM", chevron = true),
            SettingsRow("restart", "Restart Launcher", "SYSTEM"),
            SettingsRow("reset", "Reset Launcher", "SYSTEM"),
        ) }
        // Everything Prism can be allowed to see, in one place (each also sits where it's used).
        SettingsPage.Access -> resumeTick.let { _ -> listOf(
            // An arrow only where selecting goes on to set something up (tvOS: the arrow opens more).
            SettingsRow("notif", "Notifications & Now Playing", "CONTROL CENTRE", if (status().notif) "Allowed" else "Not Allowed", chevron = !status().notif),
            SettingsRow("recs", "Recommendations from Apps", "HOME SCREEN", if (hasRecommendationAccess()) "Allowed" else "Not Allowed", chevron = !hasRecommendationAccess()),
            SettingsRow("usage", "Recent Apps", "HOME SCREEN", if (status().usage) "Allowed" else "Not Allowed", chevron = !status().usage),
            SettingsRow("acc:photos", "Your Pictures", "WALLPAPER, SCREENSAVER & BANNERS", if (hasPhotoAccess()) "Allowed" else "Not Allowed", chevron = !hasPhotoAccess()),
            SettingsRow("kb:mic", "Dictation Microphone", "KEYBOARD", if (hasMicAccess()) "Allowed" else "Not Allowed", chevron = !hasMicAccess()),
        ) }
        // Colour Mode colours Settings and pop-ups (always frosted glass); Material and Material Colour
        // are the Home Screen glass (Automatic follows Colour Mode, Clear drops the tint).
        SettingsPage.Appearance -> AppearanceSetting.entries.map {
            SettingsRow(
                "ap:${it.name}", it.label, "COLOUR MODE",
                value = if (it == AppearanceSetting.Automatic) automaticLabel() else null,
                selectable = true, checked = prefs.appearance == it,
            )
        } + listOf(
            SettingsRow("mat:Liquid", "Liquid Glass", "MATERIAL", selectable = true, checked = prefs.material != GlassMaterial.Frosted),
            SettingsRow("mat:Frosted", "Frosted Glass", "MATERIAL", selectable = true, checked = prefs.material == GlassMaterial.Frosted),
        ) + GlassColour.entries.map {
            SettingsRow(
                "gc:${it.name}",
                if (it == GlassColour.Automatic) "Automatic (${prefs.appearance.label})" else it.label,
                "MATERIAL COLOUR",
                selectable = true,
                checked = prefs.glassColour == it,
            )
        } + UiTransparency.entries.map {
            SettingsRow("ui:${it.name}", it.label, "SETTINGS & POP-UPS TRANSPARENCY", selectable = true, checked = prefs.uiTransparency == it)
        } + listOf(
            SettingsRow("textSize", "Text Size", "ACCESSIBILITY", textSizeLabel(), chevron = true),
            SettingsRow("reduce", "Reduce Transparency", "ACCESSIBILITY", onOff(prefs.reduceTransparency)),
            SettingsRow("lite", "Lite Mode", "PERFORMANCE", onOff(prefs.liteMode)),
        )
        SettingsPage.Keyboard -> resumeTick.let { _ -> listOf(
            SettingsRow("kb:system", "Prism Keyboard", "SYSTEM KEYBOARD", keyboardStatus(), chevron = true),
            SettingsRow("kbLayout", "Keyboard Layout", "APPEARANCE", prefs.keyboardLayout.label),
            SettingsRow("kbColour", "Keyboard Colour", "APPEARANCE", keyboardColourLabel()),
            SettingsRow("kbField", "Text Field Colour", "APPEARANCE", fieldColourLabel()),
            SettingsRow("kb:autocorrect", "Auto-Correction", "TYPING", onOff(prefs.autoCorrect)),
            SettingsRow("kb:predictive", "Predictive", "TYPING", onOff(prefs.predictive)),
            SettingsRow("kb:mic", "Dictation Microphone", "DICTATION", if (hasMicAccess()) "Allowed" else "Not Allowed", chevron = !hasMicAccess()),
        ) }
        // Each group together: focus, the Top Shelf, widgets, the time pill, then the layout.
        SettingsPage.HomeScreen -> FocusEffect.entries.map {
            SettingsRow("fx:${it.name}", it.label, "FOCUS EFFECT", selectable = true, checked = prefs.focusEffect == it)
        } + listOfNotNull(
            SettingsRow("wobble", "Focus Wobble", "FOCUS EFFECT", onOff(prefs.focusWobble)),
            SettingsRow("hero", "Top Shelf", "TOP SHELF", onOff(prefs.heroEnabled)),
            SettingsRow("topShelf", "Top Shelf Style", "TOP SHELF", prefs.topShelfStyle.label),
            SettingsRow("source", "Artwork Source", "TOP SHELF", prefs.heroSource.label),
            SettingsRow("logos", "Title Logos", "TOP SHELF", onOff(prefs.titleLogos)),
            SettingsRow("autorotate", "Auto-Advance", "TOP SHELF", onOff(prefs.heroAutoRotate)),
            SettingsRow("recs", "Recommendations from Apps", "TOP SHELF", if (hasRecommendationAccess()) "Allowed" else "Not Allowed", chevron = !hasRecommendationAccess()),
            SettingsRow("wallpaper", "Wallpaper", "TOP SHELF", if (wallpaperShown) wallpaperLabel() else "Not Shown", chevron = true),
            SettingsRow("widgets", "Widgets", "WIDGETS", widgetsLabel(), chevron = true),
            SettingsRow("weather", "Weather", "TIME PILL", onOff(prefs.weather)),
            SettingsRow("tempUnit", "Temperature", "TIME PILL", prefs.temperatureUnit.label),
            SettingsRow("wifi", "Wi-Fi", "TIME PILL", onOff(prefs.wifiStatus)),
            SettingsRow("notif", "Notifications", "TIME PILL", if (status().notif) "Allowed" else "Not Allowed", chevron = !status().notif),
            SettingsRow("bannersOver", "Banners Over Apps", "TIME PILL", bannersOverLabel()),
            // GitHub edition only: the Play edition's Power & Home Button takes no screenshots.
            if (BuildConfig.PLAY) null else SettingsRow("snapshots", "App Snapshots", "APP SWITCHER", snapshotsLabel()),
            SettingsRow("columns", "Columns", "LAYOUT", prefs.columns.toString()),
            SettingsRow("labels", "App Names", "LAYOUT", prefs.labels.label),
            SettingsRow("resetLayout", "Reset Home Screen Layout", "LAYOUT"),
            SettingsRow("demo", "Demo Content", "LAYOUT", onOff(prefs.demoContent)),
        )
        // When it starts, what it shows, how aerials play, and the system screensaver.
        SettingsPage.Screensaver -> prefs.aerialSource.let { source ->
            val pictures = source == AerialSource.Pictures || source == AerialSource.Shuffle
            val aerials = !BuildConfig.PLAY && (source == AerialSource.Apple || source == AerialSource.Shuffle)
            listOfNotNull(
                SettingsRow("ss:show", "Show Screensaver", "SCREENSAVER", chevron = true),
                SettingsRow("ss:after", "Start After", "SCREENSAVER", startAfterLabel(prefs.screensaverAfter)),
                SettingsRow("ss:media", "Wait While Media Plays", "SCREENSAVER", onOff(prefs.screensaverWaitForMedia)),
                SettingsRow("ss:source", "Source", "WHAT TO SHOW", source.label),
                if (pictures) SettingsRow("ss:pictures", "Pictures", "WHAT TO SHOW", screensaverPicturesLabel(), chevron = true) else null,
                SettingsRow("ss:location", "Show Location", "WHAT TO SHOW", onOff(prefs.aerialShowLocation)),
                if (aerials) SettingsRow("ss:quality", "Video Quality", "AERIALS", prefs.aerialQuality.label) else null,
                if (aerials) SettingsRow("ss:range", "Dynamic Range", "AERIALS", rangeLabel()) else null,
                SettingsRow("ss:system", "Use as System Screensaver", "SYSTEM", chevron = true),
            )
        }
        SettingsPage.Apps -> appsByName.map {
            SettingsRow("app:${it.id}", it.label, "SHOW ON HOME SCREEN", app = it, toggle = it.id !in prefs.hiddenApps)
        }.ifEmpty { listOf(SettingsRow("none", "No Apps Installed", "SHOW ON HOME SCREEN")) }
        SettingsPage.SoftwareUpdate -> listOf(
            SettingsRow(
                "upd:check",
                if (availableUpdate != null) "Update Now" else "Check for Updates",
                "PRISM TV LAUNCHER",
                availableUpdate?.let { "${it.versionName} Available" } ?: updateStatus.takeIf { it.isNotEmpty() },
                chevron = true,
            ),
            SettingsRow("upd:auto", "Check Automatically", "PRISM TV LAUNCHER", onOff(prefs.updateCheck)),
            SettingsRow("upd:ver", "Installed Version", "PRISM TV LAUNCHER", BuildConfig.VERSION_NAME),
        )
        SettingsPage.TextSize -> emptyList()
        // With Shuffle on, the checkmarks are the wallpapers in the shuffle (pick as many as you like).
        SettingsPage.Wallpaper -> {
            val shuffling = prefs.wallpaperShuffle > 0
            val inShuffle = shuffleKeys().toSet()
            fun checked(key: String) = !shuffling && prefs.wallpaper == key
            fun toggle(key: String): Boolean? = if (shuffling) key in inShuffle else null
            listOf(
                SettingsRow("wp:shuffle", "Shuffle", "SHUFFLE", com.prismtv.launcher.data.shuffleLabel(prefs.wallpaperShuffle)),
                if (BuildConfig.PLAY) null else SettingsRow("wp:aerial", "Apple TV Aerials", "MOVING WALLPAPER", toggle = aerialWallpaperOn),
            ).filterNotNull() + Wallpapers.map { (name, _) ->
                SettingsRow(
                    "wp:scene:$name", wallpaperName(name),
                    if (shuffling) "PRISM WALLPAPERS · IN SHUFFLE" else "PRISM WALLPAPERS",
                    selectable = !shuffling, checked = checked("scene:$name"), toggle = toggle("scene:$name"),
                )
            } + if (!hasPhotoAccess()) {
                listOf(SettingsRow("wp:allow", "Allow Access to Your Pictures", "YOUR PICTURES", chevron = true))
            } else if (photos.isEmpty()) {
                listOf(SettingsRow("wp:none", "Save pictures to the TV's Pictures or Download folder", "YOUR PICTURES"))
            } else {
                photos.map { (uri, name) ->
                    SettingsRow(
                        "wp:photo:$uri", name, if (shuffling) "YOUR PICTURES · IN SHUFFLE" else "YOUR PICTURES",
                        selectable = !shuffling, checked = checked("photo:$uri"), toggle = toggle("photo:$uri"),
                    )
                }
            }
        }
        SettingsPage.Widgets -> (if (widgetRows.count { (prefs.widgets[it.key] ?: WidgetPlace.Off) != WidgetPlace.Off } >= 2) {
            listOf(SettingsRow("w:order", "Arrange Widgets", "ORDER", chevron = true))
        } else emptyList()) + widgetRows.map { r ->
            val all = r.pkg.isEmpty()
            SettingsRow(
                "w:${r.key}", r.title, r.appLabel.uppercase(),
                (prefs.widgets[r.key] ?: WidgetPlace.Off).label + if (all) " · " + widgetAppsLabel(r.key) else "",
                chevron = all,
            )
        }.ifEmpty {
            listOf(
                SettingsRow(
                    "w:none",
                    if (hasRecommendationAccess()) "No apps have rows to show yet" else "Allow Recommendations from Apps",
                    "WIDGETS",
                    chevron = !hasRecommendationAccess(),
                )
            )
        }
        SettingsPage.WidgetApps -> {
            val key = widgetDetailKey
            val chosen = prefs.widgetApps[key]
            listOf(
                SettingsRow("wd:place", "Show", "WIDGET", (prefs.widgets[key] ?: WidgetPlace.Off).label),
                SettingsRow("wd:all", "All Apps", "SHOW CARDS FROM", toggle = chosen == null),
            ) + widgetSourceApps(key).map { (id, label) ->
                SettingsRow(
                    "wd:app:$id", label, "SHOW CARDS FROM",
                    toggle = chosen == null || id in chosen,
                    app = allApps.firstOrNull { it.id.substringBefore('/') == id || it.packageName == id },
                )
            }
        }
        SettingsPage.WidgetOrder -> {
            val on = widgetRows.filter { (prefs.widgets[it.key] ?: WidgetPlace.Off) != WidgetPlace.Off }
            on.map { r ->
                val place = prefs.widgets[r.key] ?: WidgetPlace.Off
                SettingsRow(
                    "wo:${r.key}", r.title, if (place == WidgetPlace.Above) "ABOVE DOCK" else "BELOW DOCK",
                    if (widgetMoving == r.key) "Moving · ↑↓" else r.appLabel,
                    selectable = widgetMoving == r.key, checked = widgetMoving == r.key,
                )
            }.ifEmpty { listOf(SettingsRow("wo:none", "Turn on two or more widgets to arrange them", "ORDER")) }
        }
        SettingsPage.Profile -> listOfNotNull(
            SettingsRow("pf:name", "Name", "PROFILE", profileName(), chevron = true),
            // The TV's own Google account belongs to the TV's owner: only on the main profile.
            if (activeProfileSpec.id == com.prismtv.launcher.data.Profiles.MainId) {
                SettingsRow("pf:google", "Google Account", "PROFILE", chevron = true)
            } else null,
        ) + com.prismtv.launcher.ui.components.AvatarPresets.mapIndexed { i, a ->
            SettingsRow("pf:preset:$i", a.name, "PRISM AVATARS", selectable = true, checked = prefs.avatar == "preset:$i")
        } + if (!hasPhotoAccess()) {
            listOf(SettingsRow("pf:allow", "Allow Access to Your Pictures", "YOUR PICTURES", chevron = true))
        } else if (photos.isEmpty()) {
            listOf(SettingsRow("pf:none", "Save pictures to the TV's Pictures or Download folder", "YOUR PICTURES"))
        } else {
            photos.map { (uri, name) ->
                SettingsRow("pf:photo:$uri", name, "YOUR PICTURES", selectable = true, checked = prefs.avatar == "photo:$uri")
            }
        }
        SettingsPage.Startup -> listOf(
            SettingsRow("su:delay", "Wait Before Opening", "WHEN THE TV STARTS", startupDelayLabel()),
            SettingsRow("su:off", "Stay on the Home Screen", "WHEN THE TV STARTS", selectable = true, checked = prefs.startupApp.isEmpty()),
        ) + appsByName.filter { it.demo == null }.map {
            SettingsRow("su:app:${it.id}", it.label, "OPEN AN APP", selectable = true, checked = prefs.startupApp == it.id, app = it)
        }
        SettingsPage.Inputs -> inputRows()
        SettingsPage.Closing -> resumeTick.let { _ -> closingRows() }
        SettingsPage.Shizuku -> resumeTick.let { _ -> shizukuRows() }
        SettingsPage.HomeApp -> resumeTick.let { _ -> homeAppRows() }
        SettingsPage.RemoteButtons -> resumeTick.let { _ -> remoteButtonRows() }
        SettingsPage.RemoteButton -> remoteButtonEditRows()
        SettingsPage.RemoteAction -> remoteActionRows()
        SettingsPage.ScreenTime -> screenTimeTick.let { _ -> screenTimeRows() }
        SettingsPage.Backup -> backupRows()
        SettingsPage.IconPack -> iconPackRows()
        SettingsPage.Banner -> bannerRows()
        SettingsPage.Profiles -> profileRows()
        SettingsPage.Restrictions -> restrictionRows()
        SettingsPage.ScreensaverPictures -> screensaverPictureRows()
        SettingsPage.Acknowledgements -> if (BuildConfig.PLAY) AcknowledgementRows.filter { it.id != "ack:shizuku" && it.id != "ack:apple" } else AcknowledgementRows
        SettingsPage.Pro -> proRows()
        SettingsPage.About -> listOf(
            SettingsRow("i:name", "Name", "PRISM TV", "Prism TV Launcher"),
            SettingsRow("i:ver", "Version", "PRISM TV", BuildConfig.VERSION_NAME),
            SettingsRow("i:legal", "Acknowledgements", "PRISM TV", chevron = true),
            SettingsRow("i:privacy", "Privacy Policy", "PRISM TV", chevron = true),
            SettingsRow("i:model", "Model", "DEVICE", "${Build.MANUFACTURER} ${Build.MODEL}"),
            SettingsRow("i:os", "Android", "DEVICE", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
            SettingsRow("i:glass", "Liquid Glass", "DEVICE", when {
                Build.VERSION.SDK_INT < 33 -> "Refraction Bands"
                com.prismtv.launcher.ui.glass.GlassPerf.shaderOk -> "Refraction Shader"
                else -> "Refraction Bands (for this GPU)"
            }),
            SettingsRow("i:memory", "Memory", "DEVICE", status().memory),
        ) + listOfNotNull(
            status().crash?.let { SettingsRow("i:crash", "Last Problem", "DIAGNOSTICS", it, chevron = true) },
        )
    }

    private fun settingsKey(k: NavKey): Boolean {
        val page = settingsStack.last()
        if (page == SettingsPage.TextSize) {
            val p = prefs
            when (k) {
                NavKey.Left -> if (p.textSize > 0) updatePrefs(p.copy(textSize = p.textSize - 1))
                NavKey.Right -> if (p.textSize < TextScales.size - 1) updatePrefs(p.copy(textSize = p.textSize + 1))
                NavKey.Select -> settingsStack = settingsStack.dropLast(1)
                NavKey.Menu -> closeOverlay()
                else -> Unit
            }
            if (prefs != p) cue(Cue.FocusSmall)
            return true
        }
        val rows = settingsRows(page)
        val f = (settingsFocus[page] ?: 0).coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        if (page == SettingsPage.WidgetOrder && widgetMoving != null && (k == NavKey.Up || k == NavKey.Down)) {
            moveWidget(widgetMoving ?: return true, if (k == NavKey.Up) -1 else 1)
            return true
        }
        when (k) {
            // Up from the first row goes to the last and Down from the last to the first (a fresh press;
            // holding the button stops at the end).
            NavKey.Up -> settingsFocus[page] = when {
                f > 0 -> f - 1
                !keyRepeat && rows.size > 1 -> rows.size - 1
                else -> 0
            }
            NavKey.Down -> settingsFocus[page] = when {
                f < rows.size - 1 -> f + 1
                !keyRepeat && rows.size > 1 -> 0
                else -> f
            }
            NavKey.Select -> rows.getOrNull(f)?.let { settingsSelect(it) }
            NavKey.Left -> if (settingsStack.size > 1) { widgetMoving = null; settingsStack = settingsStack.dropLast(1) }
            NavKey.Menu -> closeOverlay()
            else -> Unit
        }
        return true
    }

    private fun settingsSelect(r: SettingsRow) {
        val p = prefs
        if (!proActive && needsPro(r.id, p)) {
            openPro(r.title)
            return
        }
        when {
            r.id == "pro" -> {
                pushPage(SettingsPage.Pro)
                com.prismtv.launcher.data.ProStore.refresh()
            }
            r.id == "pro:buy" -> purchaseTick++
            r.id == "pro:restore" -> {
                com.prismtv.launcher.data.ProStore.refresh()
                showBanner("Prism Pro", "Asking Google Play… If Prism Pro was bought with this TV's Google account, it unlocks in a moment.", Icons.Rounded.Info)
            }
            r.id == "appearance" -> pushPage(SettingsPage.Appearance)
            r.id == "material" -> {
                pushPage(SettingsPage.Appearance)
                settingsFocus[SettingsPage.Appearance] = AppearanceSetting.entries.size + if (prefs.material == GlassMaterial.Frosted) 1 else 0
            }
            r.id == "keyboard" -> pushPage(SettingsPage.Keyboard)
            r.id == "profile" -> pushPage(SettingsPage.Profiles)
            r.id.startsWith("pr:switch:") -> profileList.firstOrNull { it.id == r.id.removePrefix("pr:switch:") }?.let { chooseProfile(it) }
            r.id == "pr:add" -> addProfile()
            r.id == "pr:wake" -> updatePrefs(p.copy(profilePickerOnWake = !p.profilePickerOnWake))
            r.id == "pr:start" -> updatePrefs(p.copy(profilePickerOnStart = !p.profilePickerOnStart))
            r.id == "pr:edit" -> {
                loadPhotos()
                pushPage(SettingsPage.Profile)
                settingsFocus[SettingsPage.Profile] = settingsRows(SettingsPage.Profile).indexOfFirst { it.checked }.coerceAtLeast(0)
            }
            r.id == "pr:passcode" -> if (activeProfileSpec.locked) turnOffPasscode() else setPasscode()
            r.id == "pr:restrict" -> pushPage(SettingsPage.Restrictions)
            r.id == "pr:screentime" -> pushPage(SettingsPage.ScreenTime)
            r.id.startsWith("st:") -> selectScreenTime(r.id)
            r.id == "remote" -> pushPage(SettingsPage.RemoteButtons)
            r.id.startsWith("rb:") || r.id.startsWith("rbe:") || r.id.startsWith("ra:") -> selectRemote(r.id)
            r.id == "bannersOver" -> toggleBannersOver()
            r.id == "snapshots" -> toggleSnapshots()
            r.id == "wp:aerial" -> if (aerialWallpaperOn) {
                // Off: back to the still (or shuffle) you had before.
                val back = p.lastStillWallpaper.ifEmpty { "scene:${Wallpapers.first().first}" }
                updatePrefs(p.copy(wallpaper = back, wallpaperShuffle = p.lastShuffle))
                shuffleKey = null
            } else {
                // On: the aerials take over (they change shot by themselves, so Shuffle pauses).
                updatePrefs(
                    p.copy(
                        wallpaper = AerialWallpaperKey,
                        lastStillWallpaper = if (p.wallpaper != AerialWallpaperKey) p.wallpaper else p.lastStillWallpaper,
                        lastShuffle = p.wallpaperShuffle,
                        wallpaperShuffle = 0,
                    )
                )
                shuffleKey = null
                when {
                    p.liteMode -> showBanner("Apple TV Aerials", "Lite Mode shows a still. Turn off Lite Mode for moving aerials on the Home Screen.", Icons.Rounded.Info)
                    !wallpaperShown -> showBanner("Apple TV Aerials", "Aerials play behind Home with Top Shelf Style set to Card Spotlight, or with the Top Shelf off.", Icons.Rounded.Info)
                    p.wallpaperShuffle > 0 -> showBanner("Apple TV Aerials", "Aerials are on. Shuffle is paused while they play: each aerial moves on to the next by itself.", Icons.Rounded.Info)
                }
            }
            r.id == "pr:delete" -> deleteActiveProfile()
            r.id == "rs:all" -> updateActive { pr ->
                pr.copy(blocked = if (pr.blocked.isEmpty()) allApps.map { it.id }.toSet() else emptySet())
            }
            r.id.startsWith("rs:") -> {
                val id = r.id.removePrefix("rs:")
                updateActive { pr -> pr.copy(blocked = if (id in pr.blocked) pr.blocked - id else pr.blocked + id) }
            }
            r.id == "pf:name" -> openKeyboard("Profile Name", p.profileName, deviceName()) { name ->
                updatePrefs(prefs.copy(profileName = name.trim().take(30)))
                cue(Cue.Confirm)
            }
            r.id == "pf:google" -> SystemActions.openAccounts(ctx)
            r.id == "pf:allow" -> photoPermissionTick++
            r.id.startsWith("pf:preset:") || r.id.startsWith("pf:photo:") -> updatePrefs(p.copy(avatar = r.id.removePrefix("pf:")))
            r.id == "kb:system" -> openKeyboardSetup()
            r.id == "kb:autocorrect" -> updatePrefs(p.copy(autoCorrect = !p.autoCorrect))
            r.id == "kb:predictive" -> updatePrefs(p.copy(predictive = !p.predictive))
            r.id == "kb:mic" -> if (hasMicAccess()) {
                showBanner("Dictation", "Prism Keyboard can already use your remote's microphone.", Icons.Rounded.Info)
            } else {
                micPermissionTick++
            }
            r.id.startsWith("ui:") -> updatePrefs(p.copy(uiTransparency = UiTransparency.valueOf(r.id.removePrefix("ui:"))))
            r.id == "wobble" -> updatePrefs(p.copy(focusWobble = !p.focusWobble))
            r.id == "lite" -> {
                updatePrefs(p.copy(liteMode = !p.liteMode))
                showBanner(
                    "Lite Mode",
                    if (prefs.liteMode) "On: simpler glass and motion, smaller artwork in memory." else "Off: full Liquid Glass and motion.",
                    Icons.Rounded.Memory, Cue.Toggle,
                )
            }
            r.id == "logos" -> updatePrefs(p.copy(titleLogos = !p.titleLogos))
            r.id == "wp:shuffle" -> {
                val i = com.prismtv.launcher.data.ShuffleChoices.indexOf(p.wallpaperShuffle).coerceAtLeast(0)
                val next = com.prismtv.launcher.data.ShuffleChoices[(i + 1) % com.prismtv.launcher.data.ShuffleChoices.size]
                // Shuffle is for stills: turning it on turns the aerials off.
                val still = if (p.wallpaper == AerialWallpaperKey) p.lastStillWallpaper.ifEmpty { "scene:${Wallpapers.first().first}" } else p.wallpaper
                // Turning Shuffle on starts from every Prism wallpaper plus the current one.
                val set = if (p.shuffleSet.isEmpty() && next > 0) (Wallpapers.map { "scene:${it.first}" } + still).toSet() else p.shuffleSet
                updatePrefs(p.copy(wallpaperShuffle = next, shuffleSet = set, wallpaper = if (next > 0) still else p.wallpaper))
            }
            r.id == "w:order" -> { widgetMoving = null; pushPage(SettingsPage.WidgetOrder) }
            r.id.startsWith("wo:") && r.id != "wo:none" -> {
                val key = r.id.removePrefix("wo:")
                widgetMoving = if (widgetMoving == key) null else key
                cue(if (widgetMoving != null) Cue.Click else Cue.Confirm)
            }
            r.id == "wd:place" -> {
                val key = widgetDetailKey
                val next = WidgetPlace.entries[((p.widgets[key] ?: WidgetPlace.Off).ordinal + 1) % WidgetPlace.entries.size]
                updatePrefs(p.copy(widgets = if (next == WidgetPlace.Off) p.widgets - key else p.widgets + (key to next)))
            }
            r.id == "wd:all" -> toggleAllWidgetApps(widgetDetailKey)
            r.id.startsWith("wd:app:") -> toggleWidgetApp(widgetDetailKey, r.id.removePrefix("wd:app:"))
            r.id == "update" -> {
                pushPage(SettingsPage.SoftwareUpdate)
                if (availableUpdate == null && updateJob?.isActive != true) checkForUpdates(manual = false)
            }
            r.id == "home" -> pushPage(SettingsPage.HomeScreen)
            r.id == "screensaver" -> pushPage(SettingsPage.Screensaver)
            r.id == "apps" -> pushPage(SettingsPage.Apps)
            r.id == "about" -> pushPage(SettingsPage.About)
            r.id == "i:legal" -> pushPage(SettingsPage.Acknowledgements)
            r.id == "i:privacy" -> {
                dialog = DialogSpec(
                    "Privacy Policy",
                    "Prism TV has no account, ads or analytics, and keeps your settings, pictures and app use on this TV. " +
                        "It goes online only for what you see: the weather (your approximate location, from the TV's internet " +
                        "address), title logos and artwork" + (if (BuildConfig.PLAY) "" else ", aerial videos and Software Update") +
                        (if (BuildConfig.PLAY) ", and Prism Pro purchases through Google Play." else ".") +
                        "\n\nThe full policy: " + Edition.PrivacyPolicyUrl,
                    listOf(DialogButton("OK") {}),
                )
                dialogFocus = 0
            }
            r.id == "i:crash" -> {
                dialog = DialogSpec(
                    "Last Problem",
                    com.prismtv.launcher.system.CrashLog.summary(ctx) ?: "No details were saved.",
                    listOf(
                        DialogButton("OK") {},
                        DialogButton("Clear") {
                            com.prismtv.launcher.system.CrashLog.clear(ctx)
                            statusCache = null
                            resumeTick++
                            settingsFocus[SettingsPage.About] = 0
                        },
                    ),
                )
                dialogFocus = 0
            }
            r.id == "usage" -> if (status().usage) {
                showBanner("Recent Apps", "Prism TV can already see the apps you've used. To turn this off, open Android's Settings › Apps › Special app access › Usage access.", Icons.Rounded.Info)
            } else {
                // Some boxes (the Ugoos AM9's Android TV firmware) have no Usage access page: opening it landed
                // on Settings' main menu. Offer the page where there is one, Do It for Me and the command.
                val page = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                val hasPage = SystemActions.canHandle(ctx, page)
                shellDialog(
                    title = "Allow Recent Apps",
                    intro = if (hasPage) "Open Usage Access and turn on Prism TV. If it isn't listed there, select Do It for Me, or type this command yourself:"
                        else "This TV has no Usage access page in its Settings. Select Do It for Me, or type this command yourself:",
                    command = "appops set ${ctx.packageName} GET_USAGE_STATS allow",
                    done = { recentsRepo.hasUsageAccess() },
                    doneMessage = "Done. The App Switcher now shows the apps you've used.",
                    open = if (hasPage) "Open Usage Access" to { SystemActions.start(ctx, page); Unit } else null,
                )
            }
            r.id == "a11y" -> setUpPowerAndHome()
            r.id == "homeOverride" -> updatePrefs(p.copy(homeOverride = !p.homeOverride))
            r.id == "startup" -> {
                updatePrefs(p.copy(openAtStartup = !p.openAtStartup))
                if (prefs.openAtStartup && !PrismAccessibilityService.isEnabled) {
                    showBanner("Open at Startup", "Turn on Power & Home Button too, so Prism TV can open when the TV starts.", Icons.Rounded.Info)
                }
            }
            r.id == "sounds" -> updatePrefs(p.copy(sounds = !p.sounds))
            r.id == "sleep" -> cycleSleepTimer()
            r.id == "restart" -> confirm("Restart Prism TV?", "The launcher will close and reopen.", "Restart") { restartTick++ }
            r.id == "reset" -> confirm(
                "Reset Prism TV?",
                "This restores the default appearance, layout and hidden apps. Your installed apps aren't affected.",
                "Reset",
            ) { resetPrefs() }
            r.id == "resetLayout" -> confirm(
                "Reset Home Screen?",
                "Folders and the order of your apps go back to the default arrangement.",
                "Reset",
            ) {
                layoutStore.clear()
                layout = null
            }
            r.id.startsWith("ap:") -> updatePrefs(p.copy(appearance = AppearanceSetting.valueOf(r.id.removePrefix("ap:"))))
            r.id.startsWith("mat:") -> updatePrefs(p.copy(material = GlassMaterial.valueOf(r.id.removePrefix("mat:"))))
            r.id == "kbLayout" -> updatePrefs(p.copy(keyboardLayout = KeyboardLayoutStyle.entries[(p.keyboardLayout.ordinal + 1) % KeyboardLayoutStyle.entries.size]))
            r.id == "kbColour" -> updatePrefs(p.copy(keyboardColour = KeyboardColour.entries[(p.keyboardColour.ordinal + 1) % KeyboardColour.entries.size]))
            r.id == "kbField" -> updatePrefs(p.copy(keyboardFieldColour = KeyboardColour.entries[(p.keyboardFieldColour.ordinal + 1) % KeyboardColour.entries.size]))
            r.id == "textSize" -> pushPage(SettingsPage.TextSize)
            r.id == "topShelf" -> updatePrefs(p.copy(topShelfStyle = TopShelfStyle.entries[(p.topShelfStyle.ordinal + 1) % TopShelfStyle.entries.size]))
            r.id == "wallpaper" -> if (wallpaperShown) {
                loadPhotos()
                pushPage(SettingsPage.Wallpaper)
            } else {
                // Full Screen Top Shelf: the title's artwork fills the screen, so the wallpaper never shows.
                dialog = DialogSpec(
                    "Wallpaper Isn't Shown",
                    "With Top Shelf Style set to Full Screen, each title's artwork fills the screen, so there's no wallpaper. " +
                        "It shows with Card Spotlight (around the title's card), or with the Top Shelf off.",
                    listOf(
                        DialogButton("Use Card Spotlight") {
                            if (!proActive) {
                                openPro("Card Spotlight")
                            } else {
                                updatePrefs(prefs.copy(topShelfStyle = TopShelfStyle.Spotlight, heroEnabled = true))
                                loadPhotos()
                                pushPage(SettingsPage.Wallpaper)
                            }
                        },
                        DialogButton("Choose Anyway") { loadPhotos(); pushPage(SettingsPage.Wallpaper) },
                        DialogButton("Cancel") {},
                    ),
                )
                dialogFocus = 0
            }
            r.id == "widgets" -> {
                refreshRecommendations(force = true)
                pushPage(SettingsPage.Widgets)
            }
            r.id == "wp:allow" -> photoPermissionTick++
            r.id.startsWith("wp:scene:") || r.id.startsWith("wp:photo:") -> {
                val key = r.id.removePrefix("wp:")
                if (p.wallpaperShuffle > 0) {
                    val set = shuffleKeys().toMutableSet()
                    if (key in set) {
                        if (set.size > 1) set -= key else cue(Cue.Error)
                    } else {
                        set += key
                    }
                    updatePrefs(p.copy(shuffleSet = set))
                } else {
                    updatePrefs(p.copy(wallpaper = key))
                    shuffleKey = null
                }
            }
            r.id == "w:none" -> if (!hasRecommendationAccess()) allowRecommendations()
            r.id.startsWith("w:all:") -> {
                widgetDetailKey = r.id.removePrefix("w:")
                pushPage(SettingsPage.WidgetApps)
            }
            r.id.startsWith("w:") -> {
                val key = r.id.removePrefix("w:")
                val next = WidgetPlace.entries[((p.widgets[key] ?: WidgetPlace.Off).ordinal + 1) % WidgetPlace.entries.size]
                updatePrefs(p.copy(widgets = if (next == WidgetPlace.Off) p.widgets - key else p.widgets + (key to next)))
            }
            r.id == "notif" -> if (PrismNotificationListener.isEnabled(ctx)) {
                showBanner("Notifications", "Prism TV can already show your notifications.", Icons.Rounded.Notifications)
            } else {
                val page = PrismNotificationListener.settingsIntent()
                val hasPage = SystemActions.canHandle(ctx, page)
                shellDialog(
                    title = "Allow Notifications",
                    intro = if (hasPage) "Open Notification Access and turn on Prism TV. If it isn't listed there, select Do It for Me, or type this command yourself:"
                        else "This TV has no Notification access page. Select Do It for Me, or type this command yourself:",
                    command = "cmd notification allow_listener ${ctx.packageName}/com.prismtv.launcher.system.PrismNotificationListener",
                    done = { PrismNotificationListener.isEnabled(ctx) },
                    doneMessage = "Done. Notifications and Now Playing now show in Prism TV.",
                    open = if (hasPage) "Open Notification Access" to { SystemActions.start(ctx, page); Unit } else null,
                )
            }
            r.id.startsWith("gc:") -> updatePrefs(p.copy(glassColour = GlassColour.valueOf(r.id.removePrefix("gc:"))))
            r.id == "reduce" -> updatePrefs(p.copy(reduceTransparency = !p.reduceTransparency))
            r.id.startsWith("fx:") -> updatePrefs(p.copy(focusEffect = FocusEffect.valueOf(r.id.removePrefix("fx:"))))
            r.id == "labels" -> updatePrefs(p.copy(labels = LabelMode.entries[(p.labels.ordinal + 1) % LabelMode.entries.size]))
            r.id == "hero" -> updatePrefs(p.copy(heroEnabled = !p.heroEnabled))
            r.id == "recs" -> if (hasRecommendationAccess()) {
                showBanner("Recommendations", "Prism TV can already show cards from your apps.", Icons.Rounded.Info)
            } else {
                allowRecommendations()
            }
            r.id == "autorotate" -> updatePrefs(p.copy(heroAutoRotate = !p.heroAutoRotate))
            r.id == "source" -> updatePrefs(p.copy(heroSource = HeroSource.entries[(p.heroSource.ordinal + 1) % HeroSource.entries.size]))
            r.id == "columns" -> updatePrefs(p.copy(columns = if (p.columns == 6) 5 else 6))
            r.id == "demo" -> updatePrefs(p.copy(demoContent = !p.demoContent))
            r.id == "ss:show" -> startScreensaver()
            r.id == "ss:after" -> {
                val i = StartAfterChoices.indexOf(p.screensaverAfter)
                updatePrefs(p.copy(screensaverAfter = StartAfterChoices[(i + 1) % StartAfterChoices.size]))
            }
            r.id == "startupApp" -> pushPage(SettingsPage.Startup)
            r.id == "inputs" -> pushPage(SettingsPage.Inputs)
            r.id == "closing" -> pushPage(SettingsPage.Closing)
            r.id.startsWith("cl:") -> selectClosing(r.id)
            r.id.startsWith("sz:") -> if (!BuildConfig.PLAY) selectShizuku(r.id)
            r.id == "homeApp" -> pushPage(SettingsPage.HomeApp)
            r.id.startsWith("ha:") -> selectHomeApp(r.id)
            r.id == "iconPack" -> {
                pushPage(SettingsPage.IconPack)
                loadIconPacks()
            }
            r.id == "ip:none" -> {
                updatePrefs(p.copy(iconPack = ""))
                refreshApps()
            }
            r.id.startsWith("ip:") && r.id != "ip:hint" -> {
                updatePrefs(p.copy(iconPack = r.id.removePrefix("ip:")))
                refreshApps()
                showBanner("Icon Pack", "${r.title} is on. Apps it doesn't cover keep their own banners.", Icons.Rounded.CheckCircle, Cue.Confirm)
            }
            r.id == "bn:default" -> bannerAppId?.let { id ->
                updatePrefs(p.copy(customBanners = p.customBanners - id))
                refreshApps()
            }
            r.id == "bn:allow" -> photoPermissionTick++
            r.id.startsWith("bn:photo:") -> bannerAppId?.let { id ->
                updatePrefs(p.copy(customBanners = p.customBanners + (id to "photo:" + r.id.removePrefix("bn:photo:"))))
                refreshApps()
            }
            r.id == "backup" -> {
                pushPage(SettingsPage.Backup)
                loadBackups()
            }
            r.id == "bk:now" -> backUpNow()
            r.id.startsWith("bk:file:") -> confirmRestore(android.net.Uri.parse(r.id.removePrefix("bk:file:")))
            r.id == "bk:pick" -> backupPickTick++
            r.id.startsWith("in:show:") -> {
                val appId = com.prismtv.launcher.data.TvInputs.Prefix + r.id.removePrefix("in:show:")
                updatePrefs(p.copy(hiddenApps = if (appId in p.hiddenApps) p.hiddenApps - appId else p.hiddenApps + appId))
            }
            r.id.startsWith("in:name:") -> renameInput(r.id.removePrefix("in:name:"))
            r.id == "su:delay" -> {
                val i = com.prismtv.launcher.data.StartupDelays.indexOf(p.startupDelay)
                updatePrefs(p.copy(startupDelay = com.prismtv.launcher.data.StartupDelays[(i + 1) % com.prismtv.launcher.data.StartupDelays.size]))
            }
            r.id == "su:off" -> updatePrefs(p.copy(startupApp = ""))
            r.id.startsWith("su:app:") -> {
                val id = r.id.removePrefix("su:app:")
                updatePrefs(p.copy(startupApp = id))
                allApps.firstOrNull { it.id == id }?.let {
                    showBanner("Open at Startup", "${it.label} will open when the TV starts.", Icons.Rounded.CheckCircle, Cue.Confirm)
                }
            }
            r.id == "access" -> pushPage(SettingsPage.Access)
            r.id == "acc:photos" -> if (hasPhotoAccess()) {
                showBanner("Your Pictures", "Prism TV can already see your pictures.", Icons.Rounded.CheckCircle)
            } else photoPermissionTick++
            r.id == "ss:pictures" -> {
                pushPage(SettingsPage.ScreensaverPictures)
                loadPhotoLibrary()
            }
            r.id == "sp:allow" -> photoPermissionTick++
            r.id == "sp:all" -> {
                updatePrefs(p.copy(screensaverAllPictures = !p.screensaverAllPictures))
                if (p.screensaverAllPictures && p.screensaverPictures.isEmpty()) {
                    showBanner("Pictures", "Choose the albums or pictures to show below.", Icons.Rounded.Info)
                }
            }
            r.id.startsWith("sp:album:") -> togglePicturePick("album:" + r.id.removePrefix("sp:album:"))
            r.id.startsWith("sp:photo:") -> {
                val id = r.id.removePrefix("sp:photo:")
                togglePicturePick("photo:$id", libraryPhotos.firstOrNull { it.id.toString() == id }?.album)
            }
            r.id == "ss:media" -> updatePrefs(p.copy(screensaverWaitForMedia = !p.screensaverWaitForMedia))
            r.id == "ss:source" -> {
                var next = AerialSource.entries[(p.aerialSource.ordinal + 1) % AerialSource.entries.size]
                if (BuildConfig.PLAY && next == AerialSource.Apple) next = AerialSource.entries[(next.ordinal + 1) % AerialSource.entries.size]
                if (!proActive && next == AerialSource.Shuffle) {
                    next = AerialSource.entries[(next.ordinal + 1) % AerialSource.entries.size]
                    showBanner("Prism Pro", "Shuffle is part of Prism Pro.", Icons.Rounded.Info)
                }
                updatePrefs(p.copy(aerialSource = next))
                // Your pictures need photo access; ask once when a source that uses them is picked.
                if ((next == AerialSource.Pictures || next == AerialSource.Shuffle) && !hasPhotoAccess()) photoPermissionTick++
            }
            r.id == "ss:quality" -> {
                var next = AerialQuality.entries[(p.aerialQuality.ordinal + 1) % AerialQuality.entries.size]
                if (!proActive && next == AerialQuality.UHD) {
                    next = AerialQuality.entries[(next.ordinal + 1) % AerialQuality.entries.size]
                    showBanner("Prism Pro", "4K aerials are part of Prism Pro.", Icons.Rounded.Info)
                }
                updatePrefs(p.copy(aerialQuality = next))
            }
            r.id == "ss:location" -> updatePrefs(p.copy(aerialShowLocation = !p.aerialShowLocation))
            r.id == "ss:range" -> {
                var next = AerialRange.entries[(p.aerialRange.ordinal + 1) % AerialRange.entries.size]
                if (!proActive && next == AerialRange.DolbyVision) {
                    next = AerialRange.entries[(next.ordinal + 1) % AerialRange.entries.size]
                    showBanner("Prism Pro", "Dolby Vision aerials are part of Prism Pro.", Icons.Rounded.Info)
                }
                updatePrefs(p.copy(aerialRange = next))
                if (next == AerialRange.DolbyVision) {
                    val dv = dolbyVision
                    when {
                        !dv.decoder -> showBanner("Dolby Vision", "This box has no Dolby Vision decoder, so aerials will play in SDR.", Icons.Rounded.Info)
                        !dv.display -> showBanner("Dolby Vision", "Your TV didn't report Dolby Vision. Aerials will play in Dolby Vision anyway — switch back if colours look wrong.", Icons.Rounded.Info)
                    }
                }
            }
            r.id == "upd:check" -> availableUpdate?.let { offerUpdate(it) } ?: checkForUpdates(manual = true)
            r.id == "upd:auto" -> updatePrefs(p.copy(updateCheck = !p.updateCheck))
            r.id == "weather" -> {
                updatePrefs(p.copy(weather = !p.weather))
                if (prefs.weather) refreshWeather(force = true)
            }
            r.id == "wifi" -> {
                updatePrefs(p.copy(wifiStatus = !p.wifiStatus))
                refreshNetwork()
            }
            r.id == "tempUnit" -> updatePrefs(p.copy(temperatureUnit = TemperatureUnit.entries[(p.temperatureUnit.ordinal + 1) % TemperatureUnit.entries.size]))
            r.id == "ss:system" -> {
                val page = Intent(Settings.ACTION_DREAM_SETTINGS)
                val hasPage = SystemActions.canHandle(ctx, page)
                val component = "${ctx.packageName}/com.prismtv.launcher.aerial.AerialDreamService"
                shellDialog(
                    title = "Use as System Screensaver",
                    intro = (if (hasPage) "Open Screensaver settings and choose Prism Aerials. If it isn't listed (Google TV), " else "") +
                        "Select Do It for Me, or type this command yourself:",
                    open = if (hasPage) "Open Screensaver Settings" to { SystemActions.start(ctx, page); Unit } else null,
                    command = "settings put secure screensaver_components $component",
                    done = {
                        runCatching { Settings.Secure.getString(ctx.contentResolver, "screensaver_components") }.getOrNull()
                            ?.contains("AerialDreamService") == true
                    },
                    doneMessage = "Prism Aerials is now the TV's screensaver.",
                )
            }
            r.id.startsWith("app:") -> {
                val id = r.id.removePrefix("app:")
                updatePrefs(p.copy(hiddenApps = if (id in p.hiddenApps) p.hiddenApps - id else p.hiddenApps + id))
            }
        }
        // A setting that changed in place gets the toggle cue (a banner's cue wins if one was shown).
        if (prefs != p && keyCue == null) cue(Cue.Toggle)
    }

    private fun startAfterLabel(minutes: Int): String = when (minutes) {
        0 -> "Never"
        1 -> "1 Minute"
        else -> "$minutes Minutes"
    }

    // ---------------------------------------------------------------- profile

    /** The profile picture, ready to draw (a picture loads in the background). */
    val avatarLook: com.prismtv.launcher.ui.components.AvatarLook
        get() {
            val a = prefs.avatar
            if (a.startsWith("photo:")) avatarPhoto?.let { return com.prismtv.launcher.ui.components.AvatarLook.Photo(it) }
            val i = a.removePrefix("preset:").toIntOrNull() ?: 0
            return com.prismtv.launcher.ui.components.AvatarLook.Preset(i.coerceIn(0, com.prismtv.launcher.ui.components.AvatarPresets.size - 1))
        }
    private var avatarPhoto by mutableStateOf<ImageBitmap?>(null)

    fun deviceName(): String =
        runCatching { Settings.Global.getString(ctx.contentResolver, "device_name") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL

    fun profileName(): String = prefs.profileName.ifBlank { deviceName() }

    fun openProfilePage() = unlockSettings { showProfilePage() }

    private fun showProfilePage() {
        loadPhotos()
        if (overlay != Overlay.Settings) {
            overlay = Overlay.Settings
            settingsStack = listOf(SettingsPage.Root)
        }
        pushPage(SettingsPage.Profile)
        settingsFocus[SettingsPage.Profile] = settingsRows(SettingsPage.Profile).indexOfFirst { it.checked }.coerceAtLeast(0)
    }

    /** Settings › Profile: a preview of the focused avatar or picture. */
    // Pages filled in by later features.
    private fun inputRows(): List<SettingsRow> = if (inputApps.isEmpty()) {
        listOf(SettingsRow("in:none", "This TV has no HDMI inputs", "INPUTS"))
    } else inputApps.flatMap { a ->
        val id = a.id.removePrefix(com.prismtv.launcher.data.TvInputs.Prefix)
        listOf(
            SettingsRow("in:show:$id", "Show on Home Screen", a.label.uppercase(), toggle = a.id !in prefs.hiddenApps, app = a),
            SettingsRow("in:name:$id", "Name", a.label.uppercase(), a.label, chevron = true, app = a),
        )
    }
    private fun backupRows(): List<SettingsRow> {
        val fmt = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        return listOf(SettingsRow("bk:now", "Back Up Now", "BACK UP", "Download › Prism TV", chevron = true)) +
            (if (backups.isEmpty()) listOf(SettingsRow("bk:none", "No backups on this TV yet", "RESTORE"))
            else backups.map { SettingsRow("bk:file:${it.uri}", fmt.format(java.util.Date(it.time)), "RESTORE", chevron = true) }) +
            listOf(SettingsRow("bk:pick", "Choose a File…", "RESTORE", chevron = true))
    }

    fun loadBackups() {
        viewModelScope.launch {
            backups = withContext(Dispatchers.IO) { com.prismtv.launcher.data.Backup.list(ctx) }
        }
    }

    private fun backUpNow() {
        viewModelScope.launch {
            val where = withContext(Dispatchers.IO) { com.prismtv.launcher.data.Backup.save(ctx, BuildConfig.VERSION_NAME) }
            if (where != null) {
                showBanner("Backed Up", "Your settings, layout and folders were saved to $where.", Icons.Rounded.CheckCircle, Cue.Confirm)
                loadBackups()
            } else {
                showBanner("Backup", "The backup couldn't be saved.", Icons.Rounded.Info, Cue.Error)
            }
        }
    }

    /** Asks before a backup replaces what's set up now. */
    fun confirmRestore(uri: android.net.Uri) {
        confirm(
            "Restore This Backup?",
            "Your current settings, Home Screen layout and folders will be replaced with the backup's.",
            "Restore",
        ) { restoreBackup(uri) }
    }

    fun noFilePicker() {
        showBanner("Choose a File", "This TV doesn't have a file picker. Copy the backup into Download › Prism TV and it'll be listed here.", Icons.Rounded.Info, Cue.Error)
    }

    private fun restoreBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { com.prismtv.launcher.data.Backup.restore(ctx, uri) }
            if (!ok) {
                showBanner("Restore", "That file isn't a Prism TV backup.", Icons.Rounded.Info, Cue.Error)
                return@launch
            }
            updatePrefs(store.load())
            layout = loadLayout()
            loadInputs()
            refreshApps()
            restartShuffle()
            showBanner("Restored", "Your settings, layout and folders are back.", Icons.Rounded.CheckCircle, Cue.Confirm)
        }
    }
    private fun iconPackLabel(): String =
        prefs.iconPack.takeIf { it.isNotEmpty() }?.let { pkg -> iconPacks.firstOrNull { it.pkg == pkg }?.label ?: "On" } ?: "None"

    private fun iconPackRows(): List<SettingsRow> =
        listOf(SettingsRow("ip:none", "None", "ICON PACK", "Apps' own banners", selectable = true, checked = prefs.iconPack.isEmpty())) +
            iconPacks.map { SettingsRow("ip:${it.pkg}", it.label, "INSTALLED", selectable = true, checked = prefs.iconPack == it.pkg) }
                .ifEmpty { listOf(SettingsRow("ip:hint", "Install an icon pack from the Play Store to use it here", "INSTALLED")) }

    fun loadIconPacks() {
        viewModelScope.launch {
            iconPacks = withContext(Dispatchers.IO) { com.prismtv.launcher.data.IconPacks.installed(ctx) }
        }
    }

    private fun bannerRows(): List<SettingsRow> {
        val id = bannerAppId ?: return emptyList()
        val current = prefs.customBanners[id]
        return listOf(SettingsRow("bn:default", "App's Own Banner", "BANNER", selectable = true, checked = current == null)) +
            if (!hasPhotoAccess()) {
                listOf(SettingsRow("bn:allow", "Allow Access to Your Pictures", "YOUR PICTURES", chevron = true))
            } else if (photos.isEmpty()) {
                listOf(SettingsRow("bn:none", "Save pictures to the TV's Pictures or Download folder", "YOUR PICTURES"))
            } else {
                photos.map { (uri, name) ->
                    SettingsRow("bn:photo:$uri", name, "YOUR PICTURES", selectable = true, checked = current == "photo:$uri")
                }
            }
    }

    /** Hold OK › Change Banner: pick one of your pictures for an app. */
    private fun openBannerPage(app: LauncherApp) {
        if (!proActive) {
            settingsStack = listOf(SettingsPage.Root)
            overlay = Overlay.Settings
            openPro("Change Banner")
            return
        }
        bannerAppId = app.id
        statusCache = null
        settingsStack = listOf(SettingsPage.Banner)
        settingsFocus[SettingsPage.Banner] = if (prefs.customBanners[app.id] == null) 0 else 1
        overlay = Overlay.Settings
        loadPhotos()
    }
    private fun profileRows(): List<SettingsRow> {
        val list = profileList
        val me = activeProfileSpec
        val mine = nameOf(me).uppercase()
        return list.map { pr ->
            SettingsRow("pr:switch:${pr.id}", nameOf(pr), "PROFILES", if (pr.locked) "Passcode" else null, selectable = true, checked = pr.id == me.id)
        } + listOf(
            SettingsRow("pr:add", "Add Profile", "PROFILES", chevron = true),
            SettingsRow("pr:wake", "Who's Watching on Wake", "PROFILES", onOff(prefs.profilePickerOnWake)),
            SettingsRow("pr:start", "Who's Watching at Start-Up", "PROFILES", onOff(prefs.profilePickerOnStart)),
            SettingsRow("pr:edit", "Name & Picture", mine, chevron = true),
            SettingsRow("pr:passcode", "Passcode", mine, onOff(me.locked)),
            SettingsRow("pr:restrict", "Restrictions", mine, if (me.blocked.isEmpty()) "None" else "${me.blocked.size} Blocked", chevron = true),
            SettingsRow("pr:screentime", "Screen Time", mine, screenTimeLabel(me), chevron = true),
        ) + if (list.size > 1) listOf(SettingsRow("pr:delete", "Delete Profile", mine)) else emptyList()
    }

    private fun restrictionRows(): List<SettingsRow> {
        val me = activeProfileSpec
        val section = "ALLOWED FOR ${nameOf(me).uppercase()}"
        // All Apps on top: on when nothing is blocked; select it to block or allow everything at once.
        return listOf(SettingsRow("rs:all", "All Apps", section, toggle = me.blocked.isEmpty())) +
            appsByName.map {
                SettingsRow("rs:${it.id}", it.label, section, app = it, toggle = it.id !in me.blocked)
            }
    }

    // ------------------------------------------------------------ screensaver pictures

    /** Settings › Screensaver › Pictures: albums and the newest pictures on the TV. */
    var photoAlbums by mutableStateOf<List<com.prismtv.launcher.data.PhotoLibrary.Album>>(emptyList())
        private set
    var libraryPhotos by mutableStateOf<List<com.prismtv.launcher.data.PhotoLibrary.Photo>>(emptyList())
        private set
    /** CI: show the picture page with pretend albums (the emulator has no photos or access). */
    private var demoLibrary = false
    /** CI: the demo App Switcher's first card shows this (real) app's snapshot. */
    private var demoSnapshotPkg: String? = null

    fun loadPhotoLibrary() {
        if (demoLibrary) return
        if (!hasPhotoAccess()) {
            photoAlbums = emptyList()
            libraryPhotos = emptyList()
            return
        }
        viewModelScope.launch {
            val (albums, recent) = withContext(Dispatchers.IO) {
                com.prismtv.launcher.data.PhotoLibrary.albums(ctx) to com.prismtv.launcher.data.PhotoLibrary.recent(ctx, 60)
            }
            photoAlbums = albums
            libraryPhotos = recent
        }
    }

    private fun screensaverPicturesLabel(): String {
        if (prefs.screensaverAllPictures) return "All Pictures"
        val picks = prefs.screensaverPictures
        val albums = picks.count { it.startsWith("album:") }
        val photos = picks.count { it.startsWith("photo:") }
        return listOfNotNull(
            albums.takeIf { it > 0 }?.let { if (it == 1) "1 Album" else "$it Albums" },
            photos.takeIf { it > 0 }?.let { if (it == 1) "1 Picture" else "$it Pictures" },
        ).joinToString(", ").ifEmpty { "None" }
    }

    private fun screensaverPictureRows(): List<SettingsRow> {
        if (!hasPhotoAccess() && !demoLibrary) {
            return listOf(SettingsRow("sp:allow", "Allow Access to Your Pictures", "PICTURES", chevron = true))
        }
        val all = prefs.screensaverAllPictures
        val picks = prefs.screensaverPictures
        val rows = mutableListOf(SettingsRow("sp:all", "All Pictures", "PICTURES", toggle = all))
        photoAlbums.forEach { a ->
            rows += SettingsRow("sp:album:${a.id}", "${a.name}  ·  ${a.count}", "ALBUMS", toggle = all || "album:${a.id}" in picks)
        }
        libraryPhotos.forEach { ph ->
            rows += SettingsRow(
                "sp:photo:${ph.id}", ph.name, "RECENT PICTURES",
                toggle = all || "photo:${ph.id}" in picks || "album:${ph.album}" in picks,
            )
        }
        if (photoAlbums.isEmpty() && libraryPhotos.isEmpty()) {
            rows += SettingsRow("sp:none", "Save pictures to the TV to see them here", "ALBUMS")
        }
        return rows
    }

    /** Settings › Screensaver › Pictures: the focused album's cover or picture. */
    fun picturePreview(rowId: String): Any? = when {
        rowId.startsWith("sp:album:") -> photoAlbums.firstOrNull { it.id == rowId.removePrefix("sp:album:") }?.let { thumb(UriArt(it.cover.toString())) }
        rowId.startsWith("sp:photo:") -> libraryPhotos.firstOrNull { it.id.toString() == rowId.removePrefix("sp:photo:") }?.let { thumb(UriArt(it.uri.toString())) }
        else -> null
    }

    /** Choosing from "All Pictures" starts a selection with just that album or picture. */
    private fun togglePicturePick(key: String, album: String? = null) {
        val p = prefs
        if (p.screensaverAllPictures) {
            updatePrefs(p.copy(screensaverAllPictures = false, screensaverPictures = setOf(key)))
            return
        }
        if (album != null && "album:$album" in p.screensaverPictures) {
            showBanner("Pictures", "This picture's whole album is on. Turn the album off to choose its pictures one by one.", Icons.Rounded.Info)
            return
        }
        val now = if (key in p.screensaverPictures) p.screensaverPictures - key else p.screensaverPictures + key
        updatePrefs(p.copy(screensaverPictures = now))
    }

    /** Settings › Banner: the picture on the focused row. */
    fun bannerPreview(rowId: String): Any? = when {
        rowId.startsWith("bn:photo:") -> thumb(UriArt(rowId.removePrefix("bn:photo:")))
        else -> allApps.firstOrNull { it.id == bannerAppId }
    }

    /** Settings › Profiles: the focused profile's picture. */
    fun profileListPreview(rowId: String): Any? =
        if (rowId.startsWith("pr:switch:")) profileList.firstOrNull { it.id == rowId.removePrefix("pr:switch:") }?.let { lookOf(it) } ?: avatarLook
        else avatarLook

    fun profilePreview(rowId: String): Any? = when {
        rowId.startsWith("pf:preset:") -> com.prismtv.launcher.ui.components.AvatarLook.Preset(rowId.removePrefix("pf:preset:").toIntOrNull() ?: 0)
        rowId.startsWith("pf:photo:") -> thumb(UriArt(rowId.removePrefix("pf:photo:")))?.let { com.prismtv.launcher.ui.components.AvatarLook.Photo(it) }
        else -> avatarLook
    }

    // ---------------------------------------------------------------- system status (cached)

    /**
     * Statuses Settings shows that come from Android (usage access, keyboard, notification access, the
     * last crash, memory). Read once per return to Prism instead of on every key press in Settings.
     */
    private class Status(val tick: Int, val usage: Boolean, val keyboard: String, val notif: Boolean, val crash: String?, val memory: String)
    private var statusCache: Status? = null

    private fun status(): Status {
        val t = resumeTick
        statusCache?.takeIf { it.tick == t }?.let { return it }
        val ime = com.prismtv.launcher.ime.PrismIme
        return Status(
            tick = t,
            usage = recentsRepo.hasUsageAccess(),
            keyboard = when {
                ime.isCurrent(ctx) -> "In Use"
                ime.isEnabled(ctx) -> "On"
                else -> "Off"
            },
            notif = PrismNotificationListener.isEnabled(ctx),
            crash = com.prismtv.launcher.system.CrashLog.lastTime(ctx),
            memory = memoryLabel(),
        ).also { statusCache = it }
    }

    // ---------------------------------------------------------------- keyboard settings

    /** Prism Keyboard as the TV's keyboard: In Use, On (enabled, not chosen) or Off. */
    private fun keyboardStatus(): String = status().keyboard

    fun hasMicAccess(): Boolean =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Asks the activity for the microphone (Settings › Keyboard › Dictation Microphone). */
    var micPermissionTick by mutableIntStateOf(0)

    private fun openKeyboardSetup() {
        val ime = com.prismtv.launcher.ime.PrismIme
        when {
            ime.isCurrent(ctx) -> showBanner("Prism Keyboard", "Prism Keyboard is your TV's keyboard.", Icons.Rounded.CheckCircle, Cue.Confirm)
            ime.isEnabled(ctx) -> if (!ime.showPicker(ctx)) ime.openSettings(ctx)
            else -> {
                val opened = ime.openSettings(ctx)
                dialog = DialogSpec(
                    "Use Prism Keyboard",
                    (if (opened) "Turn on Prism Keyboard in the list, then press Back and choose it here again. " else "") +
                        "From a computer or ATV Connect's Shell: ime enable ${ime.ID} then ime set ${ime.ID}",
                    listOf(DialogButton("OK") {}),
                )
                dialogFocus = 0
            }
        }
    }

    // ---------------------------------------------------------------- wallpaper shuffle

    private val shuffleSp = ctx.getSharedPreferences("prism_shuffle", Context.MODE_PRIVATE)
    /** The wallpaper Shuffle is showing (kept across restarts); null = the chosen wallpaper. */
    private var shuffleKey by mutableStateOf(shuffleSp.getString("key", null))
    private var shuffleJob: Job? = null

    /** The wallpaper on screen now. */
    val wallpaperKey: String get() = if (prefs.wallpaperShuffle > 0) shuffleKey ?: prefs.wallpaper else prefs.wallpaper

    /** The wallpapers in the shuffle: the chosen set, or every Prism wallpaper. */
    private fun shuffleKeys(): List<String> =
        prefs.shuffleSet.toList().ifEmpty { Wallpapers.map { "scene:${it.first}" } }

    private fun restartShuffle() {
        shuffleJob?.cancel()
        val secs = prefs.wallpaperShuffle
        if (secs <= 0 || debugDriven) return
        shuffleJob = viewModelScope.launch {
            while (true) {
                val period = secs * 1000L
                val last = shuffleSp.getLong("at", 0L)
                delay((last + period - System.currentTimeMillis()).coerceIn(0L, period))
                // Only change on a visible Home Screen: nothing is decoded while an app is on top.
                snapshotFlow { homeVisible }.first { it }
                val keys = shuffleKeys()
                val current = wallpaperKey
                val next = keys.filter { it != current }.ifEmpty { keys }.random()
                artRepo.load(wallpaperRef(next)) // decoded before the cross-fade starts
                shuffleKey = next
                shuffleSp.edit().putString("key", next).putLong("at", System.currentTimeMillis()).apply()
            }
        }
    }

    private fun memoryLabel(): String = runCatching {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        "%.1f of %.1f GB free".format(info.availMem / 1_073_741_824.0, info.totalMem / 1_073_741_824.0)
    }.getOrDefault("")

    private fun automaticLabel(): String =
        if (weather != null && prefs.weather) "Sunset to Sunrise" else "7 PM to 7 AM"

    /** Privacy & Access: how many of Prism's permissions are on. */
    private fun accessLabel(): String {
        val st = status()
        val on = listOf(st.notif, hasRecommendationAccess(), st.usage, hasPhotoAccess(), hasMicAccess()).count { it }
        return "$on of 5 Allowed"
    }

    /** The text field above Prism's keyboard (Rename Folder, Profile Name…); other apps draw their own. */
    private fun fieldColourLabel(): String = when (prefs.keyboardFieldColour) {
        KeyboardColour.Automatic -> "Same as Keyboard"
        else -> prefs.keyboardFieldColour.label
    }

    private fun keyboardColourLabel(): String = when (prefs.keyboardColour) {
        KeyboardColour.Automatic -> "Automatic"
        else -> prefs.keyboardColour.label
    }

    private fun textSizeLabel(): String = when {
        prefs.textSize == com.prismtv.launcher.data.DefaultTextSize -> "Default"
        prefs.textSize < com.prismtv.launcher.data.DefaultTextSize -> "Smaller"
        else -> "Larger"
    }

    /** Apple TV Aerials are the wallpaper (Shuffle is for stills, so it's off while they play). */
    private val aerialWallpaperOn: Boolean get() = prefs.wallpaper == AerialWallpaperKey && prefs.wallpaperShuffle == 0

    /** The wallpaper is on screen: with Card Spotlight around the title's card, or with no Top Shelf. */
    private val wallpaperShown: Boolean get() = !prefs.heroEnabled || prefs.topShelfStyle == TopShelfStyle.Spotlight

    private fun wallpaperLabel(): String = when {
        prefs.wallpaper.startsWith("photo:") -> "Your Picture"
        prefs.wallpaper == AerialWallpaperKey -> "Apple TV Aerials"
        else -> wallpaperName(prefs.wallpaper)
    }

    private fun widgetsLabel(): String {
        val n = prefs.widgets.count { (k, v) -> v != WidgetPlace.Off && widgetRows.any { it.key == k } }
        return if (n == 0) "None" else "$n On"
    }

    private fun rangeLabel(): String = when (prefs.aerialRange) {
        AerialRange.Auto -> if (dolbyVision.full) "Automatic (Dolby Vision)" else "Automatic (SDR)"
        else -> prefs.aerialRange.label
    }

    private fun confirm(title: String, message: String, action: String, onConfirm: () -> Unit) {
        dialog = DialogSpec(title, message, listOf(DialogButton(action, onConfirm), DialogButton("Cancel") {}))
        dialogFocus = 0
    }

    private fun resetPrefs() {
        store.clear()
        layoutStore.clear()
        layout = null
        updatePrefs(LauncherPrefs())
        settingsStack = listOf(SettingsPage.Root)
        showBanner("Prism TV", "Settings have been reset.", Icons.Rounded.Info, Cue.Confirm)
    }

    // ================================================================= dialogs & banners

    private fun dialogKey(d: DialogSpec, k: NavKey): Boolean {
        when (k) {
            NavKey.Up, NavKey.Left -> dialogFocus = (dialogFocus - 1).coerceAtLeast(0)
            NavKey.Down, NavKey.Right -> dialogFocus = (dialogFocus + 1).coerceAtMost(d.buttons.size - 1)
            NavKey.Select -> {
                val b = d.buttons.getOrNull(dialogFocus)
                dialog = null
                b?.action?.invoke()
            }
            else -> Unit
        }
        return true
    }

    fun showBanner(
        title: String,
        message: String,
        icon: ImageVector = Icons.Rounded.PowerSettingsNew,
        sound: Cue? = Cue.Notify,
    ) {
        // During a key press an explicit cue (e.g. Toggle) wins; otherwise the banner's cue plays.
        if (sound != null) {
            if (inKey) {
                if (keyCue == null || sound != Cue.Notify) keyCue = sound
            } else {
                sounds.play(sound)
            }
        }
        val spec = BannerSpec(Edition.text(title), Edition.text(message), icon)
        banner = spec
        bannerJob?.cancel()
        bannerJob = viewModelScope.launch {
            // Longer messages stay up longer, so there's time to read them.
            delay((3_600L + (message.length - 70).coerceAtLeast(0) * 45L).coerceAtMost(9_000L))
            if (banner?.id == spec.id) banner = null
        }
    }

    /** "Automatic" follows the time of day, like tvOS. */
    /**
     * Appearance › Automatic: dark from sunset to sunrise where you are (from the weather, when it's
     * on and recent), otherwise from 7 PM to 7 AM.
     */
    fun automaticIsDark(): Boolean {
        val w = weather
        if (w != null && prefs.weather && System.currentTimeMillis() - weatherRepo.fetchedAt < 2L * 3600 * 1000) return !w.isDay
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h >= 19 || h < 7
    }

    /** CI: a few believable notifications. */
    private fun demoNotifications(n: Int): List<PrismNotification> {
        val now = System.currentTimeMillis()
        val list = listOf(
            Triple("Nuvio", "New episode available", "Severance · Season 2, Episode 4 is ready to watch."),
            Triple("Plex", "Downloads finished", "3 movies are ready to play offline."),
            Triple("YouTube", "New from a channel you follow", "A first look at Liquid Glass on the big screen."),
            Triple("Google Play", "Updates available", "5 apps can be updated."),
        )
        return list.take(n).mapIndexed { i, (app, title, text) ->
            PrismNotification("demo:$i", "demo.$app", app, title, text, now - i * 600_000L, null, null, false)
        }
    }

    // ================================================================= debug / screenshot driver

    /**
     * Deterministic state for previews and CI screenshots, e.g.
     * `adb shell am start -n com.prismtv.launcher/.MainActivity --ez demo true --es screen controlcentre`.
     */
    /**
     * Debug builds (CI): how long building each Settings page's rows takes on the main thread (a D-pad
     * press in Settings builds them twice), and how long one Top Shelf backdrop takes to prepare.
     */
    private fun benchSettings() {
        viewModelScope.launch {
            delay(3000) // after the apps have loaded
            val pages = listOf(
                SettingsPage.Root, SettingsPage.Appearance, SettingsPage.Apps, SettingsPage.Access,
                SettingsPage.Closing, SettingsPage.HomeApp, SettingsPage.Keyboard,
            )
            for (pg in pages) {
                repeat(20) { settingsRows(pg) }
                val n = 200
                val t0 = SystemClock.elapsedRealtimeNanos()
                repeat(n) { settingsRows(pg) }
                val us = (SystemClock.elapsedRealtimeNanos() - t0) / 1000.0 / n
                android.util.Log.i("PrismBench", "settingsRows ${pg.name}: ${"%.1f".format(java.util.Locale.ROOT, us)} us")
            }
            run {
                val n = 200
                val t0 = SystemClock.elapsedRealtimeNanos()
                repeat(n) { homeAppLabel() }
                val us = (SystemClock.elapsedRealtimeNanos() - t0) / 1000.0 / n
                android.util.Log.i("PrismBench", "home app label, asked of Android: ${"%.1f".format(java.util.Locale.ROOT, us)} us")
            }
            // A fresh cache, so each backdrop is decoded, cropped and blurred from scratch.
            val repo = ArtRepository(ctx)
            DemoContent.scenes.take(4).forEachIndexed { i, res ->
                val t0 = SystemClock.elapsedRealtimeNanos()
                repo.load(ResArt(res))
                val ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
                android.util.Log.i("PrismBench", "backdrop $i: ${"%.1f".format(java.util.Locale.ROOT, ms)} ms")
            }
            @Suppress("DEPRECATION")
            repo.trim(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            // Navigation sounds: time on the main thread to start loading them, and until all are ready.
            val s0 = SystemClock.elapsedRealtimeNanos()
            val probe = UiSounds(ctx)
            val mainMs = (SystemClock.elapsedRealtimeNanos() - s0) / 1_000_000.0
            var waited = 0
            while (probe.loaded < Cue.entries.size && waited < 8000) { delay(5); waited += 5 }
            val allMs = (SystemClock.elapsedRealtimeNanos() - s0) / 1_000_000.0
            android.util.Log.i(
                "PrismBench",
                "sounds: ${probe.loaded} of ${Cue.entries.size} loaded, main thread ${"%.1f".format(java.util.Locale.ROOT, mainMs)} ms, " +
                    "all ready ${"%.0f".format(java.util.Locale.ROOT, allMs)} ms",
            )
            probe.release()
        }
    }

    /** What Prism holds in memory, by kind: printed by `dumpsys activity com.prismtv.launcher/.MainActivity`. */
    fun memoryReport(collect: Boolean = false): List<String> {
        val mb = { bytes: Long -> com.prismtv.launcher.system.BitmapTally.mb(bytes) }
        val pss = runCatching {
            val mi = android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }
            fun stat(k: String) = (mi.getMemoryStat(k)?.toLongOrNull() ?: 0L) * 1024
            "PSS ${mb(mi.totalPss * 1024L)}: native heap ${mb(stat("summary.native-heap"))}, graphics ${mb(stat("summary.graphics"))}, " +
                "Java heap ${mb(stat("summary.java-heap"))}, code ${mb(stat("summary.code"))}, private other ${mb(stat("summary.private-other"))}"
        }.getOrElse { "PSS unavailable" }
        val rt0 = Runtime.getRuntime()
        val before = "Java heap in use ${mb(rt0.totalMemory() - rt0.freeMemory())}, native heap in use ${mb(android.os.Debug.getNativeHeapAllocatedSize())}"
        if (collect) {
            // Bitmaps nobody holds any more keep their pixels until the garbage collector frees them.
            repeat(2) { Runtime.getRuntime().gc(); System.runFinalization(); Thread.sleep(300) }
        }
        val banners = com.prismtv.launcher.system.BitmapTally("app banners (${realApps.size} apps)")
        val icons = com.prismtv.launcher.system.BitmapTally("app icons")
        realApps.forEach { banners.add(it.banner); icons.add(it.icon) }
        val shown = com.prismtv.launcher.system.BitmapTally("artwork on the Home Screen (icon packs, your pictures)")
        allApps.forEach { shown.add(it.banner); shown.add(it.icon) }
        val inputs = com.prismtv.launcher.system.BitmapTally("inputs (${inputApps.size})")
        inputApps.forEach { inputs.add(it.banner); inputs.add(it.icon) }
        val rt = Runtime.getRuntime()
        return listOfNotNull(
            pss,
            before,
            if (collect) "after collecting garbage: Java heap in use ${mb(rt.totalMemory() - rt.freeMemory())}, native heap in use ${mb(android.os.Debug.getNativeHeapAllocatedSize())}" else null,
            banners.toString(), icons.toString(), shown.toString(), inputs.toString(),
        ) + artRepo.memoryLines() + listOf(
            com.prismtv.launcher.data.IconPacks.memoryLine(),
            com.prismtv.launcher.system.MediaWatcher.memoryLine(),
        )
    }

    /** Debug builds (CI): Liquid Glass's offscreen copies on or off, to time one against the other. */
    var sharpCopies by mutableStateOf(true)
        private set
    /** Debug A/B only (`--ez isolateScene false`): the Home scene drawn in its parent's layer, as before 0.2.165. */
    var isolateScene by mutableStateOf(true)
        private set

    fun applyDebug(b: Bundle) {
        debugDriven = true
        sharpCopies = b.getBoolean("sharpCopies", true)
        com.prismtv.launcher.aerial.AerialBuffer.reclaim = b.getBoolean("reclaim", true)
        isolateScene = b.getBoolean("isolateScene", true)
        com.prismtv.launcher.ui.components.OverlayDebug.directThresholds = b.getBoolean("directThresholds", false)
        if (b.getString("bench") == "settings") benchSettings()
        // The time pill's network as given (wifi1–wifi3, ethernet, offline), whatever the emulator has.
        debugNetwork = when (val n = b.getString("net")) {
            null -> null
            "ethernet" -> com.prismtv.launcher.system.NetStatus("Ethernet", "Connected", true, true)
            "offline" -> com.prismtv.launcher.system.NetStatus("Wi-Fi", "Not Connected", connected = false, ethernet = false, bars = 0)
            else -> com.prismtv.launcher.system.NetStatus("Wi-Fi", "Connected", true, false, n.removePrefix("wifi").toIntOrNull()?.coerceIn(1, 3) ?: 3)
        }
        network = debugNetwork ?: SystemActions.network(ctx)
        // Each shot starts clean: no picker or passcode left over from the last one.
        passcode = null
        passcodeEntry = ""
        profilePicker = false
        pickerForced = false
        refreshWeather()
        var p = prefs
        if (b.containsKey("demo")) p = p.copy(demoContent = b.getBoolean("demo"))
        b.getString("appearance")?.let { v -> AppearanceSetting.entries.firstOrNull { it.name.equals(v, true) }?.let { p = p.copy(appearance = it) } }
        val glassArg = b.getString("glass")
        glassArg?.let { v ->
            p = if (v.equals("frosted", true) || v.equals("classic", true)) p.copy(material = GlassMaterial.Frosted) else p.copy(material = GlassMaterial.Liquid)
        }
        // Per-shot options fall back to their defaults so one CI shot can't leak into the next.
        p = p.copy(
            topShelfStyle = if (b.getString("topShelf").equals("spotlight", true)) TopShelfStyle.Spotlight else TopShelfStyle.FullScreen,
            textSize = b.getInt("textSize", com.prismtv.launcher.data.DefaultTextSize).coerceIn(0, TextScales.size - 1),
            keyboardColour = KeyboardColour.entries.firstOrNull { it.name.equals(b.getString("keyboardColour"), true) } ?: KeyboardColour.Automatic,
            keyboardLayout = if (b.getString("kbLayout").equals("abc", true)) KeyboardLayoutStyle.Abc else KeyboardLayoutStyle.Qwerty,
            keyboardFieldColour = KeyboardColour.entries.firstOrNull { it.name.equals(b.getString("fieldColour"), true) } ?: KeyboardColour.Automatic,
            widgets = when (b.getString("widgets")) {
                "above" -> mapOf("all:continue" to WidgetPlace.Above)
                "below" -> mapOf("all:continue" to WidgetPlace.Below, "all:recommended" to WidgetPlace.Below)
                "both" -> mapOf("all:continue" to WidgetPlace.Above, "all:recommended" to WidgetPlace.Below)
                else -> emptyMap()
            },
        )
        b.getString("wallpaper")?.let { p = p.copy(wallpaper = if (it == AerialWallpaperKey) it else "scene:$it") }
        // Each CI shot states its glass colour (or gets Automatic), so one shot can't leak into the next.
        p = p.copy(
            glassColour = if (glassArg.equals("clear", true)) GlassColour.Clear
            else GlassColour.entries.firstOrNull { it.name.equals(b.getString("glassColour"), true) } ?: GlassColour.Automatic,
            uiTransparency = UiTransparency.entries.firstOrNull { it.name.equals(b.getString("ui"), true) } ?: UiTransparency.Medium,
            avatar = b.getString("avatar") ?: "preset:0",
            titleLogos = b.getBoolean("logos", true),
            wallpaperShuffle = b.getInt("shuffle", 0).takeIf { it in com.prismtv.launcher.data.ShuffleChoices } ?: 0,
            liteMode = b.getBoolean("lite", false),
            profiles = if (b.getBoolean("demoProfiles", false)) demoProfiles() else emptyList(),
            activeProfile = b.getString("activeProfile") ?: "",
        )
        // The demo profile in use brings its own name and picture.
        p.profiles.firstOrNull { it.id == p.activeProfile.ifEmpty { com.prismtv.launcher.data.Profiles.MainId } }?.let { a ->
            p = p.copy(profileName = a.name, avatar = a.avatar)
        }
        debugTitleArt = b.getString("logoId")?.let { com.prismtv.launcher.data.TitleLogos.refFor(it) }
        // Pretend HDMI inputs for screenshots.
        val wantInputs = b.getBoolean("inputs", false)
        if (wantInputs != com.prismtv.launcher.data.TvInputs.demo) {
            com.prismtv.launcher.data.TvInputs.demo = wantInputs
            loadInputs()
        }
        // A fixed Now Playing for Control Centre screenshots.
        com.prismtv.launcher.system.MediaWatcher.demo(
            b.getString("nowPlaying")?.let { t ->
                com.prismtv.launcher.system.NowPlaying(
                    pkg = "demo.music", appLabel = "Music", title = t, subtitle = "Low Tide Sessions",
                    art = null, appIcon = null, playing = !b.getBoolean("paused", false),
                )
            }
        )
        demoSnapshotPkg = b.getString("demoSnapshot")?.takeIf { it.isNotBlank() }
        // Play edition: Prism Pro as on a trial, after it, or bought.
        b.getString("pro")?.let {
            com.prismtv.launcher.data.Pro.debugSet(ctx, it)
            refreshPro()
        }
        // A banner that stays up for the screenshot.
        bannerJob?.cancel()
        banner = b.getString("banner")?.let { BannerSpec("Prism TV Updated", it, Icons.Rounded.CheckCircle) }
        b.getString("focusEffect")?.let { v -> FocusEffect.entries.firstOrNull { it.name.equals(v, true) }?.let { p = p.copy(focusEffect = it) } }
        b.getString("aerial")?.let { v -> AerialSource.entries.firstOrNull { it.name.equals(v, true) }?.let { p = p.copy(aerialSource = it) } }
        if (b.containsKey("autorotate")) p = p.copy(heroAutoRotate = b.getBoolean("autorotate"))
        if (b.containsKey("columns")) p = p.copy(columns = b.getInt("columns", 6).coerceIn(5, 6))
        // Remote Buttons: a Red button that opens Control Centre (Search when held), Guide → App Switcher.
        if (b.containsKey("demoRemote")) {
            p = if (b.getBoolean("demoRemote")) p.copy(remoteButtons = "183\t0\tcc\tsearch\n172\t0\tswitcher\tdefault", homeHold = "cc")
            else p.copy(remoteButtons = "", homeHold = "default")
        }
        if (b.containsKey("snapshots")) p = p.copy(appSnapshots = b.getBoolean("snapshots"))
        if (b.containsKey("bannersOver")) p = p.copy(bannersOverApps = b.getBoolean("bannersOver"))
        // Screen Time for the profile in use.
        if (b.containsKey("screenLimit") || b.containsKey("downtime")) {
            val id = p.activeProfile.ifEmpty { com.prismtv.launcher.data.Profiles.MainId }
            val limit = b.getInt("screenLimit", 0)
            val down = b.getString("downtime").orEmpty()
            val base = p.profiles.ifEmpty { listOf(com.prismtv.launcher.data.ProfileSpec(id, p.profileName, p.avatar)) }
            p = p.copy(profiles = base.map { if (it.id == id) it.copy(limitMinutes = limit, downtime = down) else it })
            b.getInt("usedMinutes", 0).takeIf { it > 0 }?.let { m ->
                val used = com.prismtv.launcher.data.ScreenTime.usedToday(ctx, id)
                com.prismtv.launcher.data.ScreenTime.add(ctx, id, m * 60_000L - used)
            }
        }
        remoteEditing = b.getString("remoteEditing") ?: remoteEditing
        b.getString("remoteSlot")?.let { remoteSlot = it }
        updatePrefs(p)
        b.getString("clock")?.let { clockOverride = it }
        PrismNotificationListener.demo(if (b.getInt("notify", 0) > 0) demoNotifications(b.getInt("notify", 0)) else emptyList())
        keyboard = null
        searchOpen = false
        searchFocus = -1

        dialog = null
        contextMenu = null
        movingId = null
        launchAnim = null
        pendingReturn = null
        openFolder = null
        settingsStack = listOf(SettingsPage.Root)
        overlay = Overlay.None
        val r = b.getInt("row", 0)
        val c = b.getInt("col", 0)
        zone = Zone.Grid
        row = 0
        col = if (b.containsKey("col")) c else heroAppIndex
        if (r == 0 && b.containsKey("col")) heroAppIndex = c
        heroIndex = b.getInt("heroIndex", 0)
        when (b.getString("screen") ?: "home") {
            "hero" -> { zone = Zone.Hero; heroButton = b.getInt("button", 0) }
            "heroinfo" -> { zone = Zone.Hero; heroButton = 1; showHeroInfo() }
            "top" -> { zone = Zone.TopBar; topIndex = b.getInt("button", 0) }
            "grid" -> { row = r.coerceAtLeast(1); col = c }
            "folder" -> {
                row = r; col = c
                (items.getOrNull(r * prefs.columns + c) as? FolderItem ?: items.filterIsInstance<FolderItem>().firstOrNull())?.let {
                    openFolder = it; folderFocus = b.getInt("focus", 0); overlay = Overlay.Folder
                }
            }
            "menu" -> {
                row = r; col = c
                val i = r * prefs.columns + c
                items.getOrNull(i)?.let { openContextMenu(it, tileAnchor(i), null) }
                contextFocus = b.getInt("focus", 0)
            }
            "move" -> {
                row = r; col = c
                movingId = items.getOrNull(r * prefs.columns + c)?.id
            }
            "sidebar" -> { row = r; col = 0; openSidebar(); if (b.containsKey("focus")) sidebarFocus = b.getInt("focus", 1) }
            "controlcentre" -> { row = r; col = c; openControlCentre(); ccFocus = b.getInt("focus", CC.Power) }
            "profiles" -> {
                showProfilePicker()
                if (b.containsKey("focus")) pickerFocus = b.getInt("focus", 0).coerceIn(0, profileList.size - 1)
            }
            "passcode" -> {
                askPasscode("Enter Passcode", "Enter Alex's passcode.", { false }) {}
                passcodeEntry = "1".repeat(b.getInt("entered", 0).coerceIn(0, 3))
                passcodeFocus = b.getInt("focus", 4).coerceIn(0, PasscodeKeys.size - 1)
            }
            "switcher" -> {
                openSwitcher()
                if (b.containsKey("focus")) switcherFocus = b.getInt("focus", 0).coerceIn(0, recents.size - 1)
                switcherLift = b.getBoolean("lift", false)
            }
            "settings" -> {
                overlay = Overlay.Settings
                val page = SettingsPage.entries.firstOrNull { it.name.equals(b.getString("page") ?: "Root", true) } ?: SettingsPage.Root
                if (page == SettingsPage.WidgetApps) widgetDetailKey = b.getString("widgetKey") ?: "all:continue"
                if (page == SettingsPage.Wallpaper || page == SettingsPage.Profile) loadPhotos()
                if (page == SettingsPage.Backup) loadBackups()
                if (page == SettingsPage.ScreensaverPictures) {
                    demoLibrary = b.getBoolean("demoLibrary", false)
                    if (demoLibrary) {
                        val fake = android.net.Uri.parse("content://demo/1")
                        photoAlbums = listOf(
                            com.prismtv.launcher.data.PhotoLibrary.Album("cam", "Camera", 214, fake),
                            com.prismtv.launcher.data.PhotoLibrary.Album("dl", "Download", 38, fake),
                            com.prismtv.launcher.data.PhotoLibrary.Album("trip", "Japan 2026", 96, fake),
                        )
                        libraryPhotos = listOf("Sunset", "Shibuya", "Harbour", "Mount Fuji").mapIndexed { i, n ->
                            com.prismtv.launcher.data.PhotoLibrary.Photo(i.toLong(), fake, n, if (i == 3) "trip" else "cam")
                        }
                    } else loadPhotoLibrary()
                }
                if (page == SettingsPage.IconPack) loadIconPacks()
                settingsStack = if (page == SettingsPage.Root) listOf(SettingsPage.Root) else listOf(SettingsPage.Root, page)
                settingsFocus[page] = b.getInt("focus", 0)
            }
            "dialog" -> {
                overlay = Overlay.Settings
                settingsFocus[SettingsPage.Root] = settingsRows(SettingsPage.Root).indexOfFirst { it.id == "reset" }.coerceAtLeast(0)
                confirm(
                    "Reset Prism TV?",
                    "This restores the default appearance, layout and hidden apps. Your installed apps aren't affected.",
                    "Reset",
                ) { resetPrefs() }
            }
            "launch" -> (items.getOrNull(c) as? AppItem)?.let { activateItem(it, c) }
            "widget" -> {
                val i = b.getInt("widget", 0)
                widgetRowAt(i)?.let { zone = Zone.Widget; widgetIndex = i; widgetCol = b.getInt("focus", 0).coerceIn(0, it.items.size - 1) }
            }
            "keyboard" -> {
                openKeyboard("Rename Folder", b.getString("text") ?: "Streaming", "Folder Name") {}
                val layer = b.getInt("layer", 0)
                val kr = b.getInt("keyRow", 1)
                val kc = b.getInt("keyCol", 4)
                viewModelScope.launch {
                    withContext(Dispatchers.IO) { com.prismtv.launcher.ui.keyboard.WordPredictor.get(ctx) }
                    keyboardController.debugState(layer, kr, kc, b.getInt("accent", -1))
                }
            }
            "aerial" -> startScreensaver()
            "search" -> {
                openSearch()
                b.getString("text")?.let { keyboardEditor.text = it }
                val kr = b.getInt("keyRow", 0)
                val kc = b.getInt("keyCol", 2)
                viewModelScope.launch {
                    withContext(Dispatchers.IO) { com.prismtv.launcher.ui.keyboard.WordPredictor.get(ctx) }
                    keyboardController.debugState(0, kr, kc)
                    searchFocus = b.getInt("focus", -1)
                }
            }
            "timesup" -> showTimesUp(
                activeProfileSpec,
                if (b.getString("block") == "downtime") com.prismtv.launcher.data.ScreenTime.Block.Downtime else com.prismtv.launcher.data.ScreenTime.Block.Limit,
            )
        }
        // CI: Prism TV's own adb client against the emulator's adbd.
        if (b.getBoolean("adbTest", false)) {
            viewModelScope.launch {
                val r = withContext(Dispatchers.IO) {
                    com.prismtv.launcher.system.LocalAdb.shell(ctx, "echo prism-adb-ok; id -u", allowPrompt = true)
                }
                android.util.Log.i("PrismAdb", "result=$r")
            }
        }
        // CI: Settings › Restart Launcher.
        // Play edition without Pro: picking a locked setting (opens Settings › Prism Pro with its banner).
        b.getString("proLocked")?.takeIf { it.isNotBlank() && !proActive }?.let {
            openPro(it)
            bannerJob?.cancel()
        }
        if (b.getBoolean("restart", false)) restartTick++
        // CI: close a real app the way a swipe-up in the App Switcher does, and log what happened.
        b.getString("closeApp")?.takeIf { it.isNotBlank() }?.let { pkg ->
            val label = runCatching {
                ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            viewModelScope.launch {
                delay(b.getInt("closeDelay", 1500).toLong())
                closeTimer?.cancel()
                pendingClose[pkg] = label
                com.prismtv.launcher.system.AppSnapshots.delete(ctx, pkg)
                flushCloses()
                val r = lastCloseResult
                android.util.Log.i(
                    "PrismClose",
                    "pkg=$pkg method=${r?.method} stopped=${r?.stopped} closed=${r?.closed} failed=${r?.failed} " +
                        "isStopped=${com.prismtv.launcher.system.AppCloser.isStopped(ctx, pkg)}",
                )
            }
        }
    }

    private var stopDictationBus: (() -> Unit)? = null

    // Shizuku keeps its listeners in a global list: these are removed again in onCleared, or the list
    // would hold on to this ViewModel (and the artwork it caches) after its screen has gone.
    private val shizukuConnected = rikka.shizuku.Shizuku.OnBinderReceivedListener {
        android.util.Log.i("PrismShizuku", "Shizuku connected")
        viewModelScope.launch { resumeTick++ }
    }
    private val shizukuGone = rikka.shizuku.Shizuku.OnBinderDeadListener { viewModelScope.launch { resumeTick++ } }

    // Last in the class on purpose: Kotlin runs initialisers top to bottom, and this block starts
    // coroutines that read state declared throughout the ViewModel.
    init {
        artRepo.lite = prefs.liteMode
        refreshApps()
        viewModelScope.launch {
            snapshotFlow { currentHeroItem?.art }
                .distinctUntilChanged()
                .collectLatest { ref ->
                    if (ref == null) return@collectLatest
                    delay(70)
                    currentArt = artRepo.load(ref) ?: artRepo.load(ResArt(DemoContent.scenes[0]))
                }
        }
        restartHeroTimer()
        viewModelScope.launch {
            delay(2500) // after the Home Screen has settled (and CI has had its chance to drive it)
            announceInstalledVersion()
        }
        watchForIdle()
        watchScreenTime()
        viewModelScope.launch {
            PrismNotificationListener.notifications.collect { list ->
                notifications = list
                if (overlay == Overlay.ControlCentre) keepCcFocusOnNotifications()
            }
        }
        viewModelScope.launch {
            com.prismtv.launcher.system.MediaWatcher.nowPlaying.collect { np ->
                nowPlaying = np
                if (overlay == Overlay.ControlCentre) {
                    if (np == null && ccFocus in CC.NowPrev..CC.NowNext) ccFocus = CC.Memory
                    keepCcFocusOnNotifications()
                }
            }
        }
        com.prismtv.launcher.system.MediaWatcher.start(ctx)
        viewModelScope.launch {
            snapshotFlow { wallpaperKey }
                .distinctUntilChanged()
                .collectLatest { key -> wallpaperArt = artRepo.load(wallpaperRef(key)) ?: artRepo.load(ResArt(DemoContent.scenes[0])) }
        }
        viewModelScope.launch {
            snapshotFlow { prefs.avatar }
                .distinctUntilChanged()
                .collectLatest { a ->
                    avatarPhoto = if (a.startsWith("photo:")) artRepo.thumb(UriArt(a.removePrefix("photo:")), 320) else null
                }
        }
        restartShuffle()
        // Dictation from Android's voice screen comes back here while Prism's keyboard is open.
        stopDictationBus = com.prismtv.launcher.ime.DictationBus.listen { text ->
            if (keyboard != null) {
                viewModelScope.launch { keyboardController.insertDictated(text) }
                true
            } else false
        }
        if (prefs.demoContent) widgetRowsAll = demoWidgetRows()
        // The time pill's Wi-Fi: Android says when the network or its signal changes.
        runCatching {
            (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager)
                .registerDefaultNetworkCallback(networkWatch)
        }
        // Prism Pro (Play edition): what Google Play says is owned, and the price to show.
        if (BuildConfig.PLAY) {
            refreshPro()
            com.prismtv.launcher.data.ProStore.start(ctx)
            viewModelScope.launch { com.prismtv.launcher.data.ProStore.price.collect { proPrice = it } }
            viewModelScope.launch {
                var owned = com.prismtv.launcher.data.Pro.owned(ctx)
                com.prismtv.launcher.data.ProStore.changes.collect {
                    refreshPro()
                    val now = com.prismtv.launcher.data.Pro.owned(ctx)
                    if (now && !owned) showBanner("Prism Pro", "Thank you! Prism Pro is unlocked.", Icons.Rounded.CheckCircle, Cue.Confirm)
                    owned = now
                    resumeTick++
                }
            }
        }
        // Shizuku hands over (or loses) its connection whenever it starts or stops: refresh Settings.
        if (!BuildConfig.PLAY) runCatching {
            rikka.shizuku.Shizuku.addBinderReceivedListenerSticky(shizukuConnected)
            rikka.shizuku.Shizuku.addBinderDeadListener(shizukuGone)
        }
        viewModelScope.launch {
            com.prismtv.launcher.system.ShizukuSetup.events.collect { e ->
                resumeTick++
                when (e) {
                    com.prismtv.launcher.system.ShizukuSetup.Event.Installed -> {
                        showBanner("Shizuku", "Shizuku is installed. Next, turn on USB debugging.", Icons.Rounded.CheckCircle, Cue.Confirm)
                        if (overlay == Overlay.Settings && settingsStack.last() == SettingsPage.Shizuku) focusNextShizukuStep()
                    }
                    is com.prismtv.launcher.system.ShizukuSetup.Event.Failed -> showBanner("Install Shizuku", e.message, Icons.Rounded.Info)
                }
            }
        }
        viewModelScope.launch {
            Updater.events.collect { e ->
                when (e) {
                    InstallEvent.Installing -> {
                        updateStatus = "Installing…"
                        showBanner("Installing Update", "Prism TV will restart in a moment.", com.prismtv.launcher.ui.components.PrismIcons.TvUpdate)
                    }
                    is InstallEvent.NeedsConfirmation -> {
                        updateStatus = "Waiting to Install"
                        SystemActions.start(ctx, e.intent)
                    }
                    is InstallEvent.Failed -> {
                        updateStatus = "Update Failed"
                        dialog = DialogSpec("Software Update", e.message, listOf(DialogButton("OK") {}))
                        dialogFocus = 0
                        cue(Cue.Error)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        stopDictationBus?.invoke()
        runCatching {
            (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager)
                .unregisterNetworkCallback(networkWatch)
        }
        if (!BuildConfig.PLAY) runCatching {
            rikka.shizuku.Shizuku.removeBinderReceivedListener(shizukuConnected)
            rikka.shizuku.Shizuku.removeBinderDeadListener(shizukuGone)
        }
        dictation?.release()
        sounds.release()
    }
}

private const val UPDATE_INTERVAL_MS = 6L * 60 * 60 * 1000
