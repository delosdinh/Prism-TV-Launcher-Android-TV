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
    /** Notification rows in Control Centre: [Notification] + index. */
    const val Notification = 100
}

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
            GridLayouts.build(DemoContent.allApps, layout ?: DemoContent.defaultLayout, prefs.hiddenApps)
        } else {
            GridLayouts.build(realApps, layout, prefs.hiddenApps)
        }
    }

    val allApps: List<LauncherApp> get() = if (prefs.demoContent) DemoContent.allApps else realApps

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
        widgetRowsAll
            .map { r ->
                val only = chosen[r.key]
                if (only.isNullOrEmpty()) r else r.copy(items = r.items.filter { it.appId.substringBefore('/') in only })
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

    /** The apps that have cards in [key]'s row (before filtering): id (package) and name. */
    fun widgetSourceApps(key: String): List<Pair<String, String>> {
        val row = widgetRowsAll.firstOrNull { it.key == key } ?: return emptyList()
        return row.items.map { it.appId.substringBefore('/') }.distinct().map { id ->
            id to (allApps.firstOrNull { it.id.substringBefore('/') == id || it.packageName == id }?.label
                ?: row.items.firstOrNull { it.appId.substringBefore('/') == id }?.appLabel?.takeIf { it.isNotBlank() } ?: id)
        }.sortedBy { it.second.lowercase() }
    }

    private fun widgetAppsLabel(key: String): String {
        val n = prefs.widgetApps[key]?.size ?: 0
        return if (n == 0) "All Apps" else if (n == 1) "1 App" else "$n Apps"
    }

    /** All Apps on: unticking an app keeps every other one. Ticking the last one goes back to All Apps. */
    private fun toggleWidgetApp(key: String, id: String) {
        val all = widgetSourceApps(key).map { it.first }.toSet()
        val current = prefs.widgetApps[key].orEmpty().ifEmpty { all }
        val next = if (id in current) current - id else current + id
        if (next.isEmpty()) {
            cue(Cue.Error)
            return
        }
        val store = if (next.containsAll(all)) emptySet() else next
        updatePrefs(prefs.copy(widgetApps = if (store.isEmpty()) prefs.widgetApps - key else prefs.widgetApps + (key to store)))
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
        @Suppress("UNUSED_VARIABLE") val tick = thumbTick // redraw when a thumbnail arrives
        artRepo.cachedThumb(ref)?.let { return it }
        if (ref.key !in loadingThumbs && ref.key !in failedThumbs) {
            loadingThumbs += ref.key
            viewModelScope.launch {
                val t = artRepo.thumb(ref, width)
                loadingThumbs -= ref.key
                if (t == null) failedThumbs += ref.key
                thumbTick++
            }
        }
        return null
    }
    private var thumbTick by mutableIntStateOf(0)
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

    /** Low memory (Android's onTrimMemory): drop cached artwork; what's on screen stays referenced. */
    fun trimMemory(level: Int) {
        artRepo.trim(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) failedThumbs.clear()
    }

    fun refreshApps() {
        if (prefs.demoContent) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { appsRepo.load() }
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
                        item.copy(appId = owner?.id ?: item.appId, appLabel = owner?.label ?: item.appLabel)
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
        prefs = p
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
            is AppItem -> buildList {
                add(MenuAction("delete", "Delete App", Icons.Rounded.RemoveCircleOutline, destructive = true))
                if (parent == null) add(MenuAction("move", "Move", Icons.Rounded.OpenWith))
                add(MenuAction("folder", if (parent == null) "Add to Folder" else "Move to Folder", Icons.Rounded.CreateNewFolder))
                if (parent != null) add(MenuAction("unfolder", "Remove from Folder", Icons.Rounded.FolderOpen))
                if (parent != null) add(MenuAction("renameParent", "Rename Folder", Icons.Rounded.Edit))
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
            a.id == "delete" && app != null -> if (demo) {
                showBanner(app.label, "Demo apps can't be deleted. Use Hide instead.", Icons.Rounded.Info, Cue.Error)
            } else {
                SystemActions.uninstall(ctx, app.packageName)
            }
        }
    }

    // ================================================================= input

    fun onKey(k: NavKey): Boolean {
        noteInteraction()
        if (launchAnim != null) return true
        val focusBefore = focusSignature()
        val surfaceBefore = surfaceSignature()
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
            surfaceSignature() != surfaceBefore -> if (depth() < depthBefore) Cue.Back else Cue.Open
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
    )

    /** What's on screen: overlays, menus, dialogs, Settings pages, a card being dismissed. */
    private fun surfaceSignature(): List<Any?> = listOf(
        overlay, dialog, contextMenu?.id, openFolder?.id, movingId != null, dismissingKey, keyboard,
        if (overlay == Overlay.Settings) settingsStack.size else 0,
    )

    /** How many layers are open — more after a key press plays Swish, fewer plays Back. */
    private fun depth(): Int =
        (if (overlay != Overlay.None) 1 else 0) +
            (if (overlay == Overlay.Settings) settingsStack.size - 1 else 0) +
            (if (contextMenu != null) 1 else 0) +
            (if (dialog != null) 1 else 0) +
            (if (keyboard != null) 1 else 0) +
            (if (movingId != null) 1 else 0)

    /** tvOS-style contextual focus: app tiles, large cards and small controls each have a cue. */
    private fun focusCue(heroStepped: Boolean): Cue = when {
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
        keyboard?.let { return keyboardKey(it, k) }
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

    fun onBack() {
        if (launchAnim != null) return
        if (keyboard != null) {
            sounds.play(Cue.Back)
            closeKeyboard()
            return
        }
        val opensSidebar = dialog == null && contextMenu == null && movingId == null &&
            overlay == Overlay.None && zone == Zone.Grid && row == 0
        sounds.play(if (opensSidebar) Cue.Open else Cue.Back)
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
        if (dialog != null || contextMenu != null || movingId != null || overlay != Overlay.None) sounds.play(Cue.Back)
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
        pendingReturn?.let { anim ->
            pendingReturn = null
            launchAnim = anim.copy(reverse = true, id = System.nanoTime())
        }
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

    fun openSidebar() {
        sidebarFocus = SidebarEntry.Home.ordinal
        overlay = Overlay.Sidebar
    }

    private fun sidebarKey(k: NavKey): Boolean {
        val n = SidebarEntry.entries.size
        when (k) {
            NavKey.Up -> sidebarFocus = (sidebarFocus - 1).coerceAtLeast(0)
            NavKey.Down -> sidebarFocus = (sidebarFocus + 1).coerceAtMost(n - 1)
            NavKey.Right, NavKey.Menu -> closeOverlay()
            NavKey.Select -> sidebarSelect(SidebarEntry.entries[sidebarFocus])
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
        }
    }

    // ================================================================= control centre

    fun openControlCentre(toNotifications: Boolean = false) {
        ccFocus = if (toNotifications && notifications.isNotEmpty()) CC.Notification else CC.Power
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
        val shown = notifications.size.coerceAtMost(3)
        if (ccFocus >= CC.Notification) {
            val i = ccFocus - CC.Notification
            when (k) {
                NavKey.Up -> ccFocus = if (i > 0) ccFocus - 1 else CC.Memory
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
        if (next == CC.Notification && shown == 0) return true
        if (next >= 0) ccFocus = next
        return true
    }

    private fun ccSelect(i: Int) {
        when (i) {
            CC.Profile -> openProfilePage()
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
                "Closed background apps · %.1f GB available".format(freeGb).takeIf { closed > 0 } ?: "%.1f GB available".format(freeGb),
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

    /** Settings › Screensaver › Start After: aerials after that long without a button press on Home. */
    private fun watchForIdle() {
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                val minutes = prefs.screensaverAfter
                if (minutes <= 0 || !isResumed || debugDriven) continue
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
        }
        // Oldest on the left, most recent next to Home.
        recents = apps.map { a ->
            val hero = heroesFor(a).firstOrNull()
            RecentEntry(a.id, a, hero?.art, hero?.title?.takeIf { it.isNotBlank() })
        } + RecentEntry("home", null, null)
        switcherFocus = (recents.size - 2).coerceAtLeast(0)
        switcherLift = false
        switcherClosingToHome = false
        dismissingKey = null
        overlay = Overlay.Switcher
        cue(Cue.Open)
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
                if (app == null) closeSwitcherToHome() else startLaunch(app, SwitcherLayout.focusedRect())
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
                SystemActions.killBackground(ctx, app.packageName)
            }
        }
    }

    // ================================================================= settings

    fun openSettings() {
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

    fun settingsRows(page: SettingsPage): List<SettingsRow> = when (page) {
        // Reading resumeTick refreshes rows (Recent Apps Access, Power & Home Button) as soon as the
        // user comes back from Android's settings.
        SettingsPage.Root -> resumeTick.let { _ -> listOf(
            SettingsRow("appearance", "Appearance", "GENERAL", prefs.appearance.label, chevron = true),
            SettingsRow("material", "Material", "GENERAL", materialLabel(), chevron = true),
            SettingsRow("home", "Home Screen", "GENERAL", chevron = true),
            SettingsRow("screensaver", "Screensaver", "GENERAL", prefs.aerialSource.label, chevron = true),
            SettingsRow("sounds", "Navigation Sounds", "GENERAL", onOff(prefs.sounds)),
            SettingsRow("keyboard", "Keyboard", "GENERAL", keyboardStatus(), chevron = true),
            SettingsRow("profile", "Profile", "GENERAL", profileName(), chevron = true),
            SettingsRow("apps", "Apps", "APPS", if (prefs.hiddenApps.isEmpty()) null else "${prefs.hiddenApps.size} Hidden", chevron = true),
            SettingsRow("usage", "Recent Apps Access", "APPS", onOff(recentsRepo.hasUsageAccess()), chevron = true),
            SettingsRow("a11y", "Power & Home Button", "REMOTE", onOff(PrismAccessibilityService.isEnabled), chevron = true),
            SettingsRow("homeOverride", "Home Button Opens Prism TV", "REMOTE", onOff(prefs.homeOverride)),
            SettingsRow("startup", "Open Prism TV at Startup", "REMOTE", onOff(prefs.openAtStartup)),
            SettingsRow("sleep", "Sleep Timer", "REMOTE", sleepLabel()),
            SettingsRow("update", "Software Update", "SYSTEM", if (availableUpdate != null) "Update Available" else null, chevron = true),
            SettingsRow("about", "About", "SYSTEM", chevron = true),
            SettingsRow("restart", "Restart Launcher", "SYSTEM"),
            SettingsRow("reset", "Reset Launcher", "SYSTEM"),
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
        )
        SettingsPage.Keyboard -> resumeTick.let { _ -> listOf(
            SettingsRow("kb:system", "Prism Keyboard", "SYSTEM KEYBOARD", keyboardStatus(), chevron = true),
            SettingsRow("kbColour", "Keyboard Colour", "APPEARANCE", keyboardColourLabel()),
            SettingsRow("kb:autocorrect", "Auto-Correction", "TYPING", onOff(prefs.autoCorrect)),
            SettingsRow("kb:predictive", "Predictive", "TYPING", onOff(prefs.predictive)),
            SettingsRow("kb:mic", "Dictation Microphone", "DICTATION", if (hasMicAccess()) "Allowed" else "Not Allowed", chevron = !hasMicAccess()),
        ) }
        SettingsPage.HomeScreen -> FocusEffect.entries.map {
            SettingsRow("fx:${it.name}", it.label, "FOCUS EFFECT", selectable = true, checked = prefs.focusEffect == it)
        } + listOf(
            SettingsRow("wobble", "Focus Wobble", "FOCUS EFFECT", onOff(prefs.focusWobble)),
            SettingsRow("labels", "App Names", "APP NAMES", prefs.labels.label),
            SettingsRow("hero", "Top Shelf", "TOP SHELF", onOff(prefs.heroEnabled)),
            SettingsRow("topShelf", "Top Shelf Style", "TOP SHELF", prefs.topShelfStyle.label),
            SettingsRow("logos", "Title Logos", "TOP SHELF", onOff(prefs.titleLogos)),
            SettingsRow("wallpaper", "Wallpaper", "TOP SHELF", wallpaperLabel(), chevron = true),
            SettingsRow("widgets", "Widgets", "WIDGETS", widgetsLabel(), chevron = true),
            SettingsRow("recs", "Recommendations from Apps", "TOP SHELF", if (hasRecommendationAccess()) "Allowed" else "Not Allowed"),
            SettingsRow("autorotate", "Auto-Advance", "TOP SHELF", onOff(prefs.heroAutoRotate)),
            SettingsRow("weather", "Weather", "TIME PILL", onOff(prefs.weather)),
            SettingsRow("tempUnit", "Temperature", "TIME PILL", prefs.temperatureUnit.label),
            SettingsRow("notif", "Notifications", "TIME PILL", if (PrismNotificationListener.isEnabled(ctx)) "Allowed" else "Not Allowed", chevron = true),
            SettingsRow("source", "Artwork Source", "TOP SHELF", prefs.heroSource.label),
            SettingsRow("columns", "Columns", "LAYOUT", prefs.columns.toString()),
            SettingsRow("resetLayout", "Reset Home Screen Layout", "LAYOUT"),
            SettingsRow("demo", "Demo Content", "LAYOUT", onOff(prefs.demoContent)),
        )
        SettingsPage.Screensaver -> listOf(
            SettingsRow("ss:show", "Show Screensaver", "SCREENSAVER", chevron = true),
            SettingsRow("ss:after", "Start After", "SCREENSAVER", startAfterLabel(prefs.screensaverAfter)),
            SettingsRow("ss:source", "Video Source", "SCREENSAVER", prefs.aerialSource.label),
            SettingsRow("ss:quality", "Video Quality", "SCREENSAVER", prefs.aerialQuality.label),
            SettingsRow("ss:range", "Dynamic Range", "SCREENSAVER", rangeLabel()),
            SettingsRow("ss:location", "Show Location", "SCREENSAVER", onOff(prefs.aerialShowLocation)),
            SettingsRow("ss:system", "Use as System Screensaver", "SYSTEM", chevron = true),
        )
        SettingsPage.Apps -> allApps.sortedBy { it.label.lowercase() }.map {
            SettingsRow("app:${it.id}", it.label, "SHOW ON HOME SCREEN", if (it.id in prefs.hiddenApps) "Hidden" else "Shown", app = it)
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
            fun checked(key: String) = if (shuffling) key in inShuffle else prefs.wallpaper == key
            listOf(
                SettingsRow("wp:shuffle", "Shuffle", "SHUFFLE", com.prismtv.launcher.data.shuffleLabel(prefs.wallpaperShuffle)),
            ) + Wallpapers.map { (name, _) ->
                SettingsRow(
                    "wp:scene:$name", wallpaperName(name),
                    if (shuffling) "PRISM WALLPAPERS · IN SHUFFLE" else "PRISM WALLPAPERS",
                    selectable = true, checked = checked("scene:$name"),
                )
            } + if (!hasPhotoAccess()) {
                listOf(SettingsRow("wp:allow", "Allow Access to Your Pictures", "YOUR PICTURES", chevron = true))
            } else if (photos.isEmpty()) {
                listOf(SettingsRow("wp:none", "Save pictures to the TV's Pictures or Download folder", "YOUR PICTURES"))
            } else {
                photos.map { (uri, name) ->
                    SettingsRow(
                        "wp:photo:$uri", name, if (shuffling) "YOUR PICTURES · IN SHUFFLE" else "YOUR PICTURES",
                        selectable = true, checked = checked("photo:$uri"),
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
            val chosen = prefs.widgetApps[key].orEmpty()
            listOf(
                SettingsRow("wd:place", "Show", "WIDGET", (prefs.widgets[key] ?: WidgetPlace.Off).label),
                SettingsRow("wd:all", "All Apps", "SHOW CARDS FROM", selectable = true, checked = chosen.isEmpty()),
            ) + widgetSourceApps(key).map { (id, label) ->
                SettingsRow(
                    "wd:app:$id", label, "SHOW CARDS FROM", selectable = true,
                    checked = chosen.isEmpty() || id in chosen,
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
        SettingsPage.About -> listOf(
            SettingsRow("i:name", "Name", "PRISM TV", "Prism TV Launcher"),
            SettingsRow("i:ver", "Version", "PRISM TV", BuildConfig.VERSION_NAME),
            SettingsRow("i:model", "Model", "DEVICE", "${Build.MANUFACTURER} ${Build.MODEL}"),
            SettingsRow("i:os", "Android", "DEVICE", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
            SettingsRow("i:glass", "Liquid Glass", "DEVICE", if (Build.VERSION.SDK_INT >= 33) "Refraction Shader" else "Refraction Bands"),
            SettingsRow("i:memory", "Memory", "DEVICE", memoryLabel()),
        ) + listOfNotNull(
            com.prismtv.launcher.system.CrashLog.lastTime(ctx)?.let { SettingsRow("i:crash", "Last Problem", "DIAGNOSTICS", it, chevron = true) },
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
            NavKey.Up -> settingsFocus[page] = (f - 1).coerceAtLeast(0)
            NavKey.Down -> settingsFocus[page] = (f + 1).coerceAtMost(rows.size - 1)
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
            r.id == "profile" -> openProfilePage()
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
            r.id == "wd:all" -> updatePrefs(p.copy(widgetApps = p.widgetApps - widgetDetailKey))
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
            r.id == "ss:source" -> updatePrefs(p.copy(aerialSource = AerialSource.entries[(p.aerialSource.ordinal + 1) % AerialSource.entries.size]))
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

    fun openProfilePage() {
        loadPhotos()
        if (overlay != Overlay.Settings) {
            overlay = Overlay.Settings
            settingsStack = listOf(SettingsPage.Root)
        }
        pushPage(SettingsPage.Profile)
        settingsFocus[SettingsPage.Profile] = settingsRows(SettingsPage.Profile).indexOfFirst { it.checked }.coerceAtLeast(0)
    }

    /** Settings › Profile: a preview of the focused avatar or picture. */
    fun profilePreview(rowId: String): Any? = when {
        rowId.startsWith("pf:preset:") -> com.prismtv.launcher.ui.components.AvatarLook.Preset(rowId.removePrefix("pf:preset:").toIntOrNull() ?: 0)
        rowId.startsWith("pf:photo:") -> thumb(UriArt(rowId.removePrefix("pf:photo:")))?.let { com.prismtv.launcher.ui.components.AvatarLook.Photo(it) }
        else -> avatarLook
    }

    // ---------------------------------------------------------------- keyboard settings

    /** Prism Keyboard as the TV's keyboard: In Use, On (enabled, not chosen) or Off. */
    private fun keyboardStatus(): String = when {
        com.prismtv.launcher.ime.PrismIme.isCurrent(ctx) -> "In Use"
        com.prismtv.launcher.ime.PrismIme.isEnabled(ctx) -> "On"
        else -> "Off"
    }

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
        )
        debugTitleArt = b.getString("logoId")?.let { com.prismtv.launcher.data.TitleLogos.refFor(it) }
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
                settingsStack = if (page == SettingsPage.Root) listOf(SettingsPage.Root) else listOf(SettingsPage.Root, page)
                settingsFocus[page] = b.getInt("focus", 0)
            }
            "dialog" -> {
                overlay = Overlay.Settings
                settingsFocus[SettingsPage.Root] = 16
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
    }

    private var stopDictationBus: (() -> Unit)? = null

    // Last in the class on purpose: Kotlin runs initialisers top to bottom, and this block starts
    // coroutines that read state declared throughout the ViewModel.
    init {
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
                if (overlay == Overlay.ControlCentre && ccFocus >= CC.Notification && ccFocus - CC.Notification >= list.size.coerceAtMost(3)) {
                    ccFocus = if (list.isEmpty()) CC.Picture else CC.Notification + (list.size.coerceAtMost(3) - 1)
                }
            }
        }
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
