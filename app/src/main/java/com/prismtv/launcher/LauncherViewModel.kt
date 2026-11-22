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
import androidx.compose.material.icons.rounded.SystemUpdate
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
import com.prismtv.launcher.data.TextScales
import com.prismtv.launcher.data.TopShelfStyle
import com.prismtv.launcher.data.UriArt
import com.prismtv.launcher.data.WidgetPlace
import com.prismtv.launcher.data.WidgetRepository
import com.prismtv.launcher.data.WidgetRow
import com.prismtv.launcher.system.PrismNotification
import com.prismtv.launcher.system.PrismNotificationListener
import com.prismtv.launcher.ui.SpotlightLayout
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
)
data class BannerSpec(val title: String, val message: String, val icon: ImageVector, val id: Long = System.nanoTime())

enum class SettingsPage(val title: String) {
    Root("Settings"), Appearance("Appearance"), HomeScreen("Home Screen"), Screensaver("Screensaver"),
    Apps("Apps"), SoftwareUpdate("Software Update"), About("About"),
    TextSize("Text Size"), Wallpaper("Wallpaper"), Widgets("Widgets"),
    Keyboard("Keyboard"), WidgetApps("Widget"), WidgetOrder("Arrange Widgets"), Profile("Profile"),
    Startup("Open at Startup"), Inputs("Inputs"), Backup("Backup & Restore"), IconPack("Icon Pack"),
    Banner("Banner"), Profiles("Profiles"), Restrictions("Restrictions"), ScreensaverPictures("Screensaver Pictures"),
    Access("Privacy & Access"), Closing("Closing Apps"),
}

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
    /** Notification rows in Control Centre: [Notification] + index. */
    const val Notification = 100
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
    "aurora" to R.drawable.hero_aurora,
    "glacier" to R.drawable.hero_glacier,
    "dunes" to R.drawable.hero_dunes,
    "nebula" to R.drawable.hero_nebula,
    "ocean" to R.drawable.hero_ocean,
)

fun wallpaperName(key: String): String = key.removePrefix("scene:").split('-').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

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
    private val layoutKey: String get() = if (prefs.demoContent) "demo" else "real"

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
    var permissionRequestTick by mutableIntStateOf(0)

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
        if (ref.key !in loadingThumbs && ref.key !in failedThumbs) {
            loadingThumbs += ref.key
            viewModelScope.launch {
                val t = artRepo.thumb(ref, width)
                loadingThumbs -= ref.key
                if (t == null) failedThumbs += ref.key
                version.intValue++
            }
        }
        return null
    }
    private val thumbVersions = HashMap<String, androidx.compose.runtime.MutableIntState>()
    private val loadingThumbs = HashSet<String>()
    /** Artwork that couldn't be loaded (no logo for a title, a dead link): not retried this session. */
    private val failedThumbs = HashSet<String>()

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
        if (updateJob?.isActive == true) return
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
        if (prefs.heroSource == HeroSource.AppArtwork) return listOf(fallback)
        return heroItemsByApp[app.id] ?: DemoContent.heroes[app.id] ?: listOf(fallback)
    }

    /** After Lite Mode changes: the Top Shelf and wallpaper load again at the new size. */
    private fun reloadArt() {
        viewModelScope.launch {
            currentHeroItem?.art?.let { ref -> artRepo.load(ref)?.let { currentArt = it } }
            artRepo.load(wallpaperRef(wallpaperKey))?.let { wallpaperArt = it }
        }
    }

    /** Low memory (Android's onTrimMemory): drop cached artwork; what's on screen stays referenced. */
    fun trimMemory(level: Int) {
        artRepo.trim(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) failedThumbs.clear()
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
                val idle = overlay == Overlay.None && dialog == null && launchAnim == null && contextMenu == null && movingId == null
                if (prefs.heroAutoRotate && idle && n > 1 && !isGridMode) {
                    heroIndex = (heroIndex + 1) % n
                }
            }
        }
    }

    private fun updatePrefs(p: LauncherPrefs) {
        val demoChanged = p.demoContent != prefs.demoContent
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
        if (demoChanged) {
            layout = layoutStore.load(layoutKey)
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
        when (k) {
            NavKey.Left -> kb.nav(com.prismtv.launcher.ui.keyboard.KbNav.Left)
            NavKey.Right -> kb.nav(com.prismtv.launcher.ui.keyboard.KbNav.Right)
            NavKey.Up -> kb.nav(com.prismtv.launcher.ui.keyboard.KbNav.Up)
            NavKey.Down -> kb.nav(com.prismtv.launcher.ui.keyboard.KbNav.Down)
            NavKey.Select -> kb.nav(com.prismtv.launcher.ui.keyboard.KbNav.Select)
            NavKey.LongSelect -> kb.nav(com.prismtv.launcher.ui.keyboard.KbNav.LongSelect)
            NavKey.PlayPause -> kb.nav(com.prismtv.launcher.ui.keyboard.KbNav.Delete)
            NavKey.Menu -> closeKeyboard()
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
        if (launchAnim != null) return true
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
        isResumed = true
        homeVisible = true
        noteInteraction()
        resumeTick++
        refreshRecommendations()
        maybeAutoCheckForUpdate()
        refreshWeather()
        // Picks up Notification access granted while Prism was away.
        com.prismtv.launcher.system.MediaWatcher.start(ctx)
        maybeOpenAtStartup()
        if (pickerOnResume) {
            pickerOnResume = false
            showProfilePicker()
        }
        pendingReturn?.let { anim ->
            pendingReturn = null
            launchAnim = anim.copy(reverse = true, id = System.nanoTime())
        }
        if (pendingClose.isNotEmpty() && !closeFlushing) scheduleClose()
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
                NavKey.Select -> if (heroButton == 0) playHero() else showHeroInfo()
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
        val uri = item.intentUri
        if (uri != null && SystemActions.openUri(ctx, uri)) {
            allApps.firstOrNull { it.id == item.appId }?.let { recentsRepo.record(it.packageName) }
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
        val intent = hero?.intentUri
        if (intent != null && SystemActions.openUri(ctx, intent)) return
        openApp(dockItem)
    }

    // ================================================================= launching

    fun startLaunch(app: LauncherApp, from: Rect) {
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
            recentsRepo.record(app.packageName)
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
            SidebarEntry.Search -> { closeOverlay(); SystemActions.search(ctx) }
            SidebarEntry.Home -> { closeOverlay(); zone = Zone.Grid; row = 0; col = 0; setDockCol(0) }
            SidebarEntry.Recents -> openSwitcher()
            SidebarEntry.PrismSettings -> openSettings()
            SidebarEntry.SystemSettings -> { closeOverlay(); SystemActions.open(ctx, Settings.ACTION_SETTINGS) }
            SidebarEntry.Store -> storeApp?.let { app ->
                closeOverlay()
                val intent = SystemActions.launchIntent(ctx, app)
                if (intent == null || !SystemActions.start(ctx, intent)) showBanner(app.label, "The store couldn't be opened.", Icons.Rounded.Info, Cue.Error)
                else recentsRepo.record(app.packageName)
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
        if (ccFocus >= CC.Notification) {
            val i = ccFocus - CC.Notification
            when (k) {
                NavKey.Up -> ccFocus = if (i > 0) ccFocus - 1 else if (playing) CC.NowPlay else CC.Memory
                NavKey.Down -> if (i < shown - 1) ccFocus++
                NavKey.Select -> notifications.getOrNull(i)?.let { n ->
                    closeOverlay()
                    if (!PrismNotificationListener.open(ctx, n)) showBanner(n.appLabel, "This notification couldn't be opened.", Icons.Rounded.Info, Cue.Error)
                }
                NavKey.LongSelect -> notifications.getOrNull(i)?.let { n -> PrismNotificationListener.dismiss(n); cue(Cue.Delete) }
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

    /** Closes background apps (everything that isn't the foreground or a system service). */
    private fun freeUpMemory() {
        viewModelScope.launch {
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
                    closedApps = r.closed.size
                }
            }
            val closed = withContext(Dispatchers.IO) {
                val pm = ctx.packageManager
                val launchable = allApps.filter { it.demo == null }.map { it.packageName }.distinct()
                launchable.count { pkg ->
                    pkg != ctx.packageName && runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess &&
                        run { SystemActions.killBackground(ctx, pkg); true }
                }
            }
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val info = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val freeGb = info.availMem / 1_073_741_824.0
            showBanner(
                "Free Up Memory",
                when {
                    closedApps == 1 -> "Closed 1 app · %.1f GB available".format(freeGb)
                    closedApps > 1 -> "Closed $closedApps apps · %.1f GB available".format(freeGb)
                    closed > 0 -> "Closed background apps · %.1f GB available".format(freeGb)
                    else -> "%.1f GB available".format(freeGb)
                },
                Icons.Rounded.Memory,
                Cue.Clear,
            )
        }
    }

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
    private var homeVisible by mutableStateOf(false)

    /** Any remote button (MainActivity also reports presses that never reach [onKey]). */
    fun noteInteraction() {
        lastInteraction = SystemClock.elapsedRealtime()
    }

    fun onPause() {
        isResumed = false
        homeVisible = false
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
                DialogButton("Open Settings") { SystemActions.open(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS) },
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

    fun openSwitcher() {
        dialog = null
        contextMenu = null
        openFolder = null
        val apps: List<LauncherApp> = if (prefs.demoContent) {
            listOf("demo:cinema", "demo:arcade", "demo:music", "demo:prism")
                .mapNotNull { id -> allApps.firstOrNull { it.id == id } }
        } else {
            recentsRepo.recentPackages().take(8).reversed()
                .mapNotNull { pkg -> realApps.firstOrNull { it.packageName == pkg } }
                .filter { it.id !in blockedApps }
        }
        // Oldest on the left, most recent next to Home.
        recents = apps.map { a ->
            val hero = heroesFor(a).firstOrNull()
            RecentEntry(a.id, a, hero?.art, hero?.title?.takeIf { it.isNotBlank() })
        } + RecentEntry("home", null, null)
        // Like tvOS: Home shrinks into the focused card; the last app is one press to the left.
        switcherFocus = recents.lastIndex
        switcherLift = false
        switcherClosingToHome = false
        dismissingKey = null
        overlay = Overlay.Switcher
        cue(Cue.SwitcherOpen)
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
            "Their name, picture, passcode and restrictions are removed. Your apps and Home Screen stay as they are.",
            "Delete",
        ) {
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

    /** Settings › Apps › Closing Apps on the main list: whether swiped-away apps really stop. */
    private fun closingLabel(): String =
        if (closeMethod() == com.prismtv.launcher.system.AppCloser.Method.MemoryOnly) "Memory Only" else "Completely"

    private fun closingWithLabel(): String = when (closeMethod()) {
        com.prismtv.launcher.system.AppCloser.Method.Shizuku -> "Shizuku"
        com.prismtv.launcher.system.AppCloser.Method.Root -> "Root Access"
        com.prismtv.launcher.system.AppCloser.Method.Accessibility -> "Power & Home Button"
        com.prismtv.launcher.system.AppCloser.Method.MemoryOnly -> if (prefs.closeCompletely) "Not Set Up" else "Freeing Memory"
    }

    private fun closingRows(): List<SettingsRow> {
        val shizuku = com.prismtv.launcher.system.ShizukuShell.status(ctx)
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
        )
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
                        if (p.closeCompletely) "Turn on Shizuku or Power & Home Button below to close apps completely."
                        else "Apps you swipe away have their memory freed, and may keep running."
                },
                Icons.Rounded.Info,
            )
            "cl:a11y" -> SystemActions.open(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "cl:shizuku" -> when (com.prismtv.launcher.system.ShizukuShell.status(ctx)) {
                com.prismtv.launcher.system.ShizukuShell.Status.NotInstalled -> showBanner(
                    "Shizuku", "Install Shizuku on this TV and start it with Wireless debugging or adb, then come back here.", Icons.Rounded.Info,
                )
                com.prismtv.launcher.system.ShizukuShell.Status.Ready ->
                    showBanner("Shizuku", "Shizuku is closing apps for Prism TV.", Icons.Rounded.CheckCircle, Cue.Confirm)
                com.prismtv.launcher.system.ShizukuShell.Status.NeedsPermission ->
                    com.prismtv.launcher.system.ShizukuShell.requestPermission { granted ->
                        viewModelScope.launch {
                            resumeTick++
                            if (granted) showBanner("Shizuku", "Apps you swipe away now close completely.", Icons.Rounded.CheckCircle, Cue.Confirm)
                            else showBanner("Shizuku", "Prism TV wasn't allowed. You can allow it in Shizuku's app list.", Icons.Rounded.Info)
                        }
                    }
                else -> {
                    // Not running, too old, or denied before: Shizuku's own app is where that's fixed.
                    val open = ctx.packageManager.getLeanbackLaunchIntentForPackage(com.prismtv.launcher.system.ShizukuShell.Package)
                        ?: ctx.packageManager.getLaunchIntentForPackage(com.prismtv.launcher.system.ShizukuShell.Package)
                    if (open == null || !SystemActions.start(ctx, open)) {
                        showBanner("Shizuku", "Start Shizuku, allow Prism TV, then come back here.", Icons.Rounded.Info)
                    }
                }
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

    fun settingsRows(page: SettingsPage): List<SettingsRow> = when (page) {
        // Reading resumeTick refreshes rows (Recent Apps Access, Power & Home Button) as soon as the
        // user comes back from Android's settings.
        // Grouped like tvOS: you and how it looks, then apps, the remote, and the system.
        SettingsPage.Root -> resumeTick.let { _ -> listOfNotNull(
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
            SettingsRow("a11y", "Power & Home Button", "REMOTE & POWER", onOff(PrismAccessibilityService.isEnabled), chevron = true),
            SettingsRow("homeOverride", "Home Button Opens Prism TV", "REMOTE & POWER", onOff(prefs.homeOverride)),
            SettingsRow("startup", "Open Prism TV at Startup", "REMOTE & POWER", onOff(prefs.openAtStartup)),
            SettingsRow("sleep", "Sleep Timer", "REMOTE & POWER", sleepLabel()),
            SettingsRow("update", "Software Update", "SYSTEM", if (availableUpdate != null) "Update Available" else null, chevron = true),
            SettingsRow("backup", "Backup & Restore", "SYSTEM", chevron = true),
            SettingsRow("access", "Privacy & Access", "SYSTEM", accessLabel(), chevron = true),
            SettingsRow("about", "About", "SYSTEM", chevron = true),
            SettingsRow("restart", "Restart Launcher", "SYSTEM"),
            SettingsRow("reset", "Reset Launcher", "SYSTEM"),
        ) }
        // Everything Prism can be allowed to see, in one place (each also sits where it's used).
        SettingsPage.Access -> resumeTick.let { _ -> listOf(
            SettingsRow("notif", "Notifications & Now Playing", "CONTROL CENTRE", if (status().notif) "Allowed" else "Not Allowed", chevron = true),
            SettingsRow("recs", "Recommendations from Apps", "HOME SCREEN", if (hasRecommendationAccess()) "Allowed" else "Not Allowed"),
            SettingsRow("usage", "Recent Apps", "HOME SCREEN", if (status().usage) "Allowed" else "Not Allowed", chevron = true),
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
            SettingsRow("kbColour", "Keyboard Colour", "APPEARANCE", keyboardColourLabel()),
            SettingsRow("kbField", "Text Field Colour", "APPEARANCE", fieldColourLabel()),
            SettingsRow("kb:autocorrect", "Auto-Correction", "TYPING", onOff(prefs.autoCorrect)),
            SettingsRow("kb:predictive", "Predictive", "TYPING", onOff(prefs.predictive)),
            SettingsRow("kb:mic", "Dictation Microphone", "DICTATION", if (hasMicAccess()) "Allowed" else "Not Allowed", chevron = !hasMicAccess()),
        ) }
        // Each group together: focus, the Top Shelf, widgets, the time pill, then the layout.
        SettingsPage.HomeScreen -> FocusEffect.entries.map {
            SettingsRow("fx:${it.name}", it.label, "FOCUS EFFECT", selectable = true, checked = prefs.focusEffect == it)
        } + listOf(
            SettingsRow("wobble", "Focus Wobble", "FOCUS EFFECT", onOff(prefs.focusWobble)),
            SettingsRow("hero", "Top Shelf", "TOP SHELF", onOff(prefs.heroEnabled)),
            SettingsRow("topShelf", "Top Shelf Style", "TOP SHELF", prefs.topShelfStyle.label),
            SettingsRow("source", "Artwork Source", "TOP SHELF", prefs.heroSource.label),
            SettingsRow("logos", "Title Logos", "TOP SHELF", onOff(prefs.titleLogos)),
            SettingsRow("autorotate", "Auto-Advance", "TOP SHELF", onOff(prefs.heroAutoRotate)),
            SettingsRow("recs", "Recommendations from Apps", "TOP SHELF", if (hasRecommendationAccess()) "Allowed" else "Not Allowed"),
            SettingsRow("wallpaper", "Wallpaper", "TOP SHELF", wallpaperLabel(), chevron = true),
            SettingsRow("widgets", "Widgets", "WIDGETS", widgetsLabel(), chevron = true),
            SettingsRow("weather", "Weather", "TIME PILL", onOff(prefs.weather)),
            SettingsRow("tempUnit", "Temperature", "TIME PILL", prefs.temperatureUnit.label),
            SettingsRow("notif", "Notifications", "TIME PILL", if (status().notif) "Allowed" else "Not Allowed", chevron = true),
            SettingsRow("columns", "Columns", "LAYOUT", prefs.columns.toString()),
            SettingsRow("labels", "App Names", "LAYOUT", prefs.labels.label),
            SettingsRow("resetLayout", "Reset Home Screen Layout", "LAYOUT"),
            SettingsRow("demo", "Demo Content", "LAYOUT", onOff(prefs.demoContent)),
        )
        // When it starts, what it shows, how aerials play, and the system screensaver.
        SettingsPage.Screensaver -> prefs.aerialSource.let { source ->
            val pictures = source == AerialSource.Pictures || source == AerialSource.Shuffle
            val aerials = source == AerialSource.Apple || source == AerialSource.Shuffle
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
        SettingsPage.Apps -> allApps.sortedBy { it.label.lowercase() }.map {
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
            ) + Wallpapers.map { (name, _) ->
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
        SettingsPage.Profile -> listOf(
            SettingsRow("pf:name", "Name", "PROFILE", profileName(), chevron = true),
            SettingsRow("pf:accounts", "Accounts", "PROFILE", chevron = true),
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
        ) + allApps.filter { it.demo == null }.sortedBy { it.label.lowercase() }.map {
            SettingsRow("su:app:${it.id}", it.label, "OPEN AN APP", selectable = true, checked = prefs.startupApp == it.id, app = it)
        }
        SettingsPage.Inputs -> inputRows()
        SettingsPage.Closing -> resumeTick.let { _ -> closingRows() }
        SettingsPage.Backup -> backupRows()
        SettingsPage.IconPack -> iconPackRows()
        SettingsPage.Banner -> bannerRows()
        SettingsPage.Profiles -> profileRows()
        SettingsPage.Restrictions -> restrictionRows()
        SettingsPage.ScreensaverPictures -> screensaverPictureRows()
        SettingsPage.About -> listOf(
            SettingsRow("i:name", "Name", "PRISM TV", "Prism TV Launcher"),
            SettingsRow("i:ver", "Version", "PRISM TV", BuildConfig.VERSION_NAME),
            SettingsRow("i:model", "Model", "DEVICE", "${Build.MANUFACTURER} ${Build.MODEL}"),
            SettingsRow("i:os", "Android", "DEVICE", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
            SettingsRow("i:glass", "Liquid Glass", "DEVICE", if (Build.VERSION.SDK_INT >= 33) "Refraction Shader" else "Refraction Bands"),
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
        when {
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
            r.id == "pr:edit" -> {
                loadPhotos()
                pushPage(SettingsPage.Profile)
                settingsFocus[SettingsPage.Profile] = settingsRows(SettingsPage.Profile).indexOfFirst { it.checked }.coerceAtLeast(0)
            }
            r.id == "pr:passcode" -> if (activeProfileSpec.locked) turnOffPasscode() else setPasscode()
            r.id == "pr:restrict" -> pushPage(SettingsPage.Restrictions)
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
            r.id == "pf:accounts" -> SystemActions.open(ctx, Settings.ACTION_SYNC_SETTINGS)
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
                // Turning Shuffle on starts from every Prism wallpaper plus the current one.
                val set = if (p.shuffleSet.isEmpty() && next > 0) (Wallpapers.map { "scene:${it.first}" } + p.wallpaper).toSet() else p.shuffleSet
                updatePrefs(p.copy(wallpaperShuffle = next, shuffleSet = set))
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
            r.id == "usage" -> SystemActions.open(ctx, Settings.ACTION_USAGE_ACCESS_SETTINGS)
            r.id == "a11y" -> SystemActions.open(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS)
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
            r.id == "kbColour" -> updatePrefs(p.copy(keyboardColour = KeyboardColour.entries[(p.keyboardColour.ordinal + 1) % KeyboardColour.entries.size]))
            r.id == "kbField" -> updatePrefs(p.copy(keyboardFieldColour = KeyboardColour.entries[(p.keyboardFieldColour.ordinal + 1) % KeyboardColour.entries.size]))
            r.id == "textSize" -> pushPage(SettingsPage.TextSize)
            r.id == "topShelf" -> updatePrefs(p.copy(topShelfStyle = TopShelfStyle.entries[(p.topShelfStyle.ordinal + 1) % TopShelfStyle.entries.size]))
            r.id == "wallpaper" -> {
                loadPhotos()
                pushPage(SettingsPage.Wallpaper)
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
            r.id == "w:none" -> if (!hasRecommendationAccess()) permissionRequestTick++
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
                val opened = SystemActions.start(ctx, PrismNotificationListener.settingsIntent())
                dialog = DialogSpec(
                    "Allow Notifications",
                    (if (opened) "Turn on Prism TV in Notification access, then press Back. " else "") +
                        "If your TV has no such page, run this from a computer: adb shell cmd notification allow_listener " +
                        "com.prismtv.launcher/com.prismtv.launcher.system.PrismNotificationListener",
                    listOf(DialogButton("OK") {}),
                )
                dialogFocus = 0
            }
            r.id.startsWith("gc:") -> updatePrefs(p.copy(glassColour = GlassColour.valueOf(r.id.removePrefix("gc:"))))
            r.id == "reduce" -> updatePrefs(p.copy(reduceTransparency = !p.reduceTransparency))
            r.id.startsWith("fx:") -> updatePrefs(p.copy(focusEffect = FocusEffect.valueOf(r.id.removePrefix("fx:"))))
            r.id == "labels" -> updatePrefs(p.copy(labels = LabelMode.entries[(p.labels.ordinal + 1) % LabelMode.entries.size]))
            r.id == "hero" -> updatePrefs(p.copy(heroEnabled = !p.heroEnabled))
            r.id == "recs" -> if (hasRecommendationAccess()) {
                showBanner("Recommendations", "Prism TV can already show cards from your apps.", Icons.Rounded.Info)
            } else {
                permissionRequestTick++
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
                val next = AerialSource.entries[(p.aerialSource.ordinal + 1) % AerialSource.entries.size]
                updatePrefs(p.copy(aerialSource = next))
                // Your pictures need photo access; ask once when a source that uses them is picked.
                if ((next == AerialSource.Pictures || next == AerialSource.Shuffle) && !hasPhotoAccess()) photoPermissionTick++
            }
            r.id == "ss:quality" -> updatePrefs(p.copy(aerialQuality = AerialQuality.entries[(p.aerialQuality.ordinal + 1) % AerialQuality.entries.size]))
            r.id == "ss:location" -> updatePrefs(p.copy(aerialShowLocation = !p.aerialShowLocation))
            r.id == "ss:range" -> {
                val next = AerialRange.entries[(p.aerialRange.ordinal + 1) % AerialRange.entries.size]
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
            r.id == "tempUnit" -> updatePrefs(p.copy(temperatureUnit = TemperatureUnit.entries[(p.temperatureUnit.ordinal + 1) % TemperatureUnit.entries.size]))
            r.id == "ss:system" -> {
                val opened = SystemActions.start(ctx, Intent(Settings.ACTION_DREAM_SETTINGS))
                dialog = DialogSpec(
                    "Use as System Screensaver",
                    (if (opened) "Choose Prism Aerials in the screensaver list. " else "") +
                        "On Google TV, set it from a computer with: adb shell settings put secure screensaver_components " +
                        "com.prismtv.launcher/.aerial.AerialDreamService",
                    listOf(DialogButton("OK") {}),
                )
                dialogFocus = 0
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
            layout = layoutStore.load(layoutKey)
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
            SettingsRow("pr:edit", "Name & Picture", mine, chevron = true),
            SettingsRow("pr:passcode", "Passcode", mine, onOff(me.locked)),
            SettingsRow("pr:restrict", "Restrictions", mine, if (me.blocked.isEmpty()) "None" else "${me.blocked.size} Blocked", chevron = true),
        ) + if (list.size > 1) listOf(SettingsRow("pr:delete", "Delete Profile", mine)) else emptyList()
    }

    private fun restrictionRows(): List<SettingsRow> {
        val me = activeProfileSpec
        val section = "ALLOWED FOR ${nameOf(me).uppercase()}"
        // All Apps on top: on when nothing is blocked; select it to block or allow everything at once.
        return listOf(SettingsRow("rs:all", "All Apps", section, toggle = me.blocked.isEmpty())) +
            allApps.sortedBy { it.label.lowercase() }.map {
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

    private fun materialLabel(): String =
        (if (prefs.material == GlassMaterial.Frosted) "Frosted Glass" else "Liquid Glass") +
            if (prefs.glassColour == GlassColour.Clear) " (Clear)" else ""

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

    private fun wallpaperLabel(): String = when {
        prefs.wallpaper.startsWith("photo:") -> "Your Picture"
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
        val spec = BannerSpec(title, message, icon)
        banner = spec
        bannerJob?.cancel()
        bannerJob = viewModelScope.launch {
            delay(3_600)
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
    fun applyDebug(b: Bundle) {
        debugDriven = true
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
            keyboardFieldColour = KeyboardColour.entries.firstOrNull { it.name.equals(b.getString("fieldColour"), true) } ?: KeyboardColour.Automatic,
            widgets = when (b.getString("widgets")) {
                "above" -> mapOf("all:continue" to WidgetPlace.Above)
                "below" -> mapOf("all:continue" to WidgetPlace.Below, "all:recommended" to WidgetPlace.Below)
                "both" -> mapOf("all:continue" to WidgetPlace.Above, "all:recommended" to WidgetPlace.Below)
                else -> emptyMap()
            },
        )
        b.getString("wallpaper")?.let { p = p.copy(wallpaper = "scene:$it") }
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
        // A banner that stays up for the screenshot.
        bannerJob?.cancel()
        banner = b.getString("banner")?.let { BannerSpec("Prism TV Updated", it, Icons.Rounded.CheckCircle) }
        b.getString("focusEffect")?.let { v -> FocusEffect.entries.firstOrNull { it.name.equals(v, true) }?.let { p = p.copy(focusEffect = it) } }
        b.getString("aerial")?.let { v -> AerialSource.entries.firstOrNull { it.name.equals(v, true) }?.let { p = p.copy(aerialSource = it) } }
        if (b.containsKey("autorotate")) p = p.copy(heroAutoRotate = b.getBoolean("autorotate"))
        if (b.containsKey("columns")) p = p.copy(columns = b.getInt("columns", 6).coerceIn(5, 6))
        updatePrefs(p)
        b.getString("clock")?.let { clockOverride = it }
        PrismNotificationListener.demo(if (b.getInt("notify", 0) > 0) demoNotifications(b.getInt("notify", 0)) else emptyList())
        keyboard = null

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
                    keyboardController.debugState(layer, kr, kc)
                }
            }
            "aerial" -> startScreensaver()
        }
        // CI: close a real app the way a swipe-up in the App Switcher does, and log what happened.
        b.getString("closeApp")?.takeIf { it.isNotBlank() }?.let { pkg ->
            val label = runCatching {
                ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            viewModelScope.launch {
                delay(b.getInt("closeDelay", 1500).toLong())
                closeTimer?.cancel()
                pendingClose[pkg] = label
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
        viewModelScope.launch {
            PrismNotificationListener.notifications.collect { list ->
                notifications = list
                if (overlay == Overlay.ControlCentre && ccFocus >= CC.Notification && ccFocus - CC.Notification >= ccNotificationsShown) {
                    ccFocus = if (ccNotificationsShown == 0) CC.Picture else CC.Notification + (ccNotificationsShown - 1)
                }
            }
        }
        viewModelScope.launch {
            com.prismtv.launcher.system.MediaWatcher.nowPlaying.collect { np ->
                nowPlaying = np
                if (overlay == Overlay.ControlCentre) {
                    if (np == null && ccFocus in CC.NowPrev..CC.NowNext) ccFocus = CC.Memory
                    if (ccFocus >= CC.Notification && ccFocus - CC.Notification >= ccNotificationsShown) {
                        ccFocus = if (ccNotificationsShown == 0) CC.Memory else CC.Notification + (ccNotificationsShown - 1)
                    }
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
        viewModelScope.launch {
            Updater.events.collect { e ->
                when (e) {
                    InstallEvent.Installing -> {
                        updateStatus = "Installing…"
                        showBanner("Installing Update", "Prism TV will restart in a moment.", Icons.Rounded.SystemUpdate)
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
        dictation?.release()
        sounds.release()
    }
}

private const val UPDATE_INTERVAL_MS = 6L * 60 * 60 * 1000
