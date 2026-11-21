package com.prismtv.launcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

const val TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

enum class Zone { TopBar, Hero, Grid }
enum class Overlay { None, ControlCentre, Switcher, Settings, Folder, Sidebar }
enum class NavKey { Up, Down, Left, Right, Select, LongSelect, Menu, PlayPause }

data class DialogButton(val label: String, val action: () -> Unit)
data class DialogSpec(val title: String, val message: String, val buttons: List<DialogButton>)
data class BannerSpec(val title: String, val message: String, val icon: ImageVector, val id: Long = System.nanoTime())

enum class SettingsPage(val title: String) {
    Root("Settings"), Appearance("Appearance"), HomeScreen("Home Screen"), Screensaver("Screensaver"),
    Apps("Apps"), About("About")
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
    const val Screensaver = 5
    const val Sound = 6
    const val Sleep = 7
    const val Bluetooth = 9
    const val Picture = 12
    const val Memory = 13
    const val Access = 14
}

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
    val artCache = mutableStateMapOf<String, HeroArt>()
    private var heroTimer: Job? = null
    private var bannerJob: Job? = null
    private var lastRecsLoad = 0L

    val isGridMode: Boolean get() = (zone == Zone.Grid && row > 0) || !prefs.heroEnabled
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
            val list = currentHeroItems
            return if (list.isEmpty()) null else list[heroIndex.coerceIn(0, list.size - 1)]
        }

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
                    }
                }
            }
        }
    }

    // ================================================================= software update

    /** A newer build found on GitHub (shown in Settings › About and offered once per version). */
    var availableUpdate by mutableStateOf<UpdateInfo?>(null)
        private set
    var updateStatus by mutableStateOf("Check Now")
        private set
    private var updateJob: Job? = null
    private var pendingAfterPermission: UpdateInfo? = null
    /** Set when CI drives the UI through debug intents; update prompts would cover the screenshots. */
    private var debugDriven = false
    /** CI's Android TV emulators never prompt (their builds race the published ones). */
    private val isEmulator = Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") || Build.FINGERPRINT.startsWith("generic")
    private val updateSp = ctx.getSharedPreferences("prism_update", Context.MODE_PRIVATE)

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
                    if (manual) showBanner("Software Update", "Prism TV ${BuildConfig.VERSION_NAME} is up to date.", Icons.Rounded.CheckCircle)
                } else {
                    updateStatus = "${info.versionName} Available"
                    if (manual || info.versionCode != prefs.skippedUpdate) offerUpdate(info)
                }
            }.onFailure {
                updateStatus = "Couldn't Check"
                if (manual) showBanner("Software Update", "Couldn't reach GitHub. Check your internet connection.", Icons.Rounded.Info)
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
                showBanner("Software Update", "The download didn't finish. Try again from Settings › About.", Icons.Rounded.Info)
                return@launch
            }
            when (withContext(Dispatchers.IO) { Updater.verify(ctx, file) }) {
                Updater.Verdict.Ok -> runCatching { Updater.install(ctx, file) }.onFailure {
                    updateStatus = "Update Failed"
                    showBanner("Software Update", "Android couldn't start the install.", Icons.Rounded.Info)
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
                }
                Updater.Verdict.NotAnUpdate -> {
                    updateStatus = "Up to Date"
                    showBanner("Software Update", "Prism TV is up to date.", Icons.Rounded.CheckCircle)
                }
                Updater.Verdict.Unreadable -> {
                    updateStatus = "Update Failed"
                    showBanner("Software Update", "The downloaded update was damaged. Try again later.", Icons.Rounded.Info)
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

    fun requestArt(ref: ArtRef) {
        if (artCache.containsKey(ref.key)) return
        viewModelScope.launch {
            artRepo.load(ref)?.let { artCache[ref.key] = it }
        }
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
        }
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
        prefs = p
        store.save(p)
        if (demoChanged) {
            layout = layoutStore.load(layoutKey)
            row = 0; col = 0; heroAppIndex = 0; heroIndex = 0
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
        showBanner(app.label, "Moved to $name.", Icons.Rounded.Folder)
    }

    private fun newFolderWith(app: LauncherApp) {
        val name = folderNameFor(app)
        val folder = FolderItem("folder:${System.currentTimeMillis()}", name, listOf(app))
        val index = GridLayouts.indexOfApp(items, app.id)
        val rest = GridLayouts.withoutApp(items, app.id).toMutableList()
        rest.add(index.coerceIn(0, rest.size), folder)
        commitItems(rest)
        showBanner(name, "New folder created with ${app.label}.", Icons.Rounded.CreateNewFolder)
    }

    private fun removeFromFolder(app: LauncherApp, folder: FolderItem) {
        val folderIndex = items.indexOfFirst { it.id == folder.id }
        val rest = GridLayouts.withoutApp(items, app.id).toMutableList()
        val stillThere = rest.any { it.id == folder.id }
        val insertAt = (if (stillThere) folderIndex + 1 else folderIndex).coerceIn(0, rest.size)
        rest.add(insertAt, AppItem(app))
        commitItems(rest)
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
        showBanner(app.label, "Hidden. Unhide it in Settings › Apps.", Icons.Rounded.VisibilityOff)
    }

    // ================================================================= context menu

    private fun tileAnchor(index: Int): Rect {
        val cols = prefs.columns
        val r = index / cols
        val trayTop = if (r == 0 && !isGridMode) HomeLayout.TrayTopHero else HomeLayout.gridTrayTop(cols, row)
        val gap = if (isGridMode) HomeLayout.RowGapGrid else HomeLayout.RowGapHero
        return HomeLayout.tileRect(cols, index, trayTop, gap)
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
        openFolder?.let { f -> if (f.id == folderId) openFolder = items.firstOrNull { it.id == folderId } as? FolderItem }
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
            a.id == "rename" -> renameRequest = m.item as? FolderItem
            a.id == "renameParent" -> renameRequest = parent
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
                showBanner(app.label, "Demo apps can't be deleted. Use Hide instead.", Icons.Rounded.Info)
            } else {
                SystemActions.uninstall(ctx, app.packageName)
            }
        }
    }

    // ================================================================= input

    fun onKey(k: NavKey): Boolean {
        if (launchAnim != null) return true
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
        when {
            launchAnim != null -> Unit
            dialog != null -> dialog = null
            contextMenu != null -> contextMenu = null
            movingId != null -> movingId = null
            overlay == Overlay.Settings && settingsStack.size > 1 -> settingsStack = settingsStack.dropLast(1)
            overlay == Overlay.Switcher -> closeSwitcherToHome()
            overlay != Overlay.None -> closeOverlay()
            zone != Zone.Grid -> { zone = Zone.Grid; row = 0; col = heroAppIndex }
            row > 0 -> { row = 0; col = col.coerceAtMost(rowSize(0) - 1) }
            // On the Home Screen, Back opens the sidebar capsule (Back again closes it).
            else -> openSidebar()
        }
    }

    fun onHomePressed() {
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
                    row > 0 -> { row--; col = col.coerceAtMost(rowSize(row) - 1); if (row == 0) setDockCol(col) }
                    prefs.heroEnabled -> { zone = Zone.Hero; heroButton = 0 }
                    else -> { zone = Zone.TopBar; topIndex = 0 }
                }
                NavKey.Down -> if (row < rowCount() - 1) { row++; col = col.coerceAtMost(rowSize(row) - 1) }
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
            Zone.Hero -> when (k) {
                NavKey.Up -> { zone = Zone.TopBar; topIndex = 0 }
                NavKey.Down -> { zone = Zone.Grid; row = 0; col = heroAppIndex }
                NavKey.Left -> if (heroButton > 0) heroButton-- else stepHero(-1)
                NavKey.Right -> if (heroButton < 1) heroButton++ else stepHero(1)
                NavKey.Select, NavKey.PlayPause -> if (heroButton == 0) playHero() else openApp(dockItem)
                NavKey.LongSelect -> Unit
                NavKey.Menu -> openControlCentre()
            }
            Zone.TopBar -> when (k) {
                NavKey.Left -> topIndex = 0
                NavKey.Right -> topIndex = 1
                NavKey.Down -> if (prefs.heroEnabled) zone = Zone.Hero else { zone = Zone.Grid; row = 0 }
                NavKey.Select -> if (topIndex == 0) openControlCentre() else openSettings()
                NavKey.Menu -> openControlCentre()
                else -> Unit
            }
        }
        return true
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
            is FolderItem -> { openFolder = item; folderFocus = 0; overlay = Overlay.Folder }
            is AppItem -> startLaunch(item.app, tileAnchor(index))
        }
    }

    private fun openApp(item: GridItem?) {
        val idx = items.indexOf(item ?: return)
        if (idx >= 0) activateItem(item, idx)
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
                showBanner(app.label, "This app couldn't be opened.", Icons.Rounded.Info)
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

    fun openControlCentre() {
        ccFocus = CC.Power
        overlay = Overlay.ControlCentre
    }

    private val ccNav: Map<Int, IntArray> = mapOf(
        // up, down, left, right  (-1 = none)
        CC.Profile to intArrayOf(-1, CC.WiFi, -1, -1),
        CC.Power to intArrayOf(CC.Profile, CC.Sleep, -1, CC.WiFi),
        CC.WiFi to intArrayOf(CC.Profile, CC.Bluetooth, CC.Power, -1),
        CC.Bluetooth to intArrayOf(CC.WiFi, CC.Screensaver, CC.Power, -1),
        CC.Sleep to intArrayOf(CC.Power, CC.Picture, -1, CC.Screensaver),
        CC.Screensaver to intArrayOf(CC.Bluetooth, CC.Memory, CC.Sleep, -1),
        CC.Picture to intArrayOf(CC.Sleep, -1, -1, CC.Sound),
        CC.Sound to intArrayOf(CC.Sleep, -1, CC.Picture, CC.Memory),
        CC.Memory to intArrayOf(CC.Screensaver, -1, CC.Sound, CC.Access),
        CC.Access to intArrayOf(CC.Screensaver, -1, CC.Memory, -1),
    )

    private fun ccKey(k: NavKey): Boolean {
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
        if (next >= 0) ccFocus = next
        return true
    }

    private fun ccSelect(i: Int) {
        when (i) {
            CC.Profile -> SystemActions.open(ctx, Settings.ACTION_SYNC_SETTINGS)
            CC.Power -> if (PrismAccessibilityService.powerDialog()) closeOverlay() else askForPowerAccess()
            CC.WiFi -> SystemActions.open(ctx, Settings.ACTION_WIFI_SETTINGS)
            CC.Bluetooth -> SystemActions.open(ctx, Settings.ACTION_BLUETOOTH_SETTINGS)
            CC.Screensaver -> startScreensaver()
            CC.Sleep -> cycleSleepTimer()
            CC.Picture -> SystemActions.open(ctx, Settings.ACTION_DISPLAY_SETTINGS)
            CC.Sound -> SystemActions.open(ctx, Settings.ACTION_SOUND_SETTINGS)
            CC.Memory -> freeUpMemory()
            CC.Access -> SystemActions.open(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS)
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
            )
        }
    }

    fun startScreensaver() {
        closeOverlay()
        SystemActions.start(ctx, Intent(ctx, AerialActivity::class.java))
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
            showBanner("Sleep Timer", "Your TV will sleep in $mins minutes.", Icons.Rounded.Timer)
            sleepJob = viewModelScope.launch {
                delay(mins * 60_000L)
                sleepMinutes = 0
                if (!PrismAccessibilityService.sleep()) PrismAccessibilityService.powerDialog()
            }
        } else {
            showBanner("Sleep Timer", "Off", Icons.Rounded.Timer)
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
            else -> 0
        }
    }

    private fun onOff(b: Boolean) = if (b) "On" else "Off"

    fun settingsRows(page: SettingsPage): List<SettingsRow> = when (page) {
        // Reading resumeTick refreshes rows (Recent Apps Access, Power & Home Button) as soon as the
        // user comes back from Android's settings.
        SettingsPage.Root -> resumeTick.let { _ -> listOf(
            SettingsRow("appearance", "Appearance", "GENERAL", prefs.appearance.label, chevron = true),
            SettingsRow("material", "Material", "GENERAL", prefs.material.label, chevron = true),
            SettingsRow("home", "Home Screen", "GENERAL", chevron = true),
            SettingsRow("screensaver", "Screensaver", "GENERAL", prefs.aerialSource.label, chevron = true),
            SettingsRow("apps", "Apps", "APPS", if (prefs.hiddenApps.isEmpty()) null else "${prefs.hiddenApps.size} Hidden", chevron = true),
            SettingsRow("usage", "Recent Apps Access", "APPS", onOff(recentsRepo.hasUsageAccess()), chevron = true),
            SettingsRow("a11y", "Power & Home Button", "REMOTE", onOff(PrismAccessibilityService.isEnabled), chevron = true),
            SettingsRow("homeOverride", "Home Button Opens Prism TV", "REMOTE", onOff(prefs.homeOverride)),
            SettingsRow("sleep", "Sleep Timer", "REMOTE", sleepLabel()),
            SettingsRow("about", "About", "SYSTEM", if (availableUpdate != null) "Update Available" else null, chevron = true),
            SettingsRow("restart", "Restart Launcher", "SYSTEM"),
            SettingsRow("reset", "Reset Launcher", "SYSTEM"),
        ) }
        SettingsPage.Appearance -> AppearanceSetting.entries.map {
            SettingsRow("ap:${it.name}", it.label, "APPEARANCE", selectable = true, checked = prefs.appearance == it)
        } + GlassMaterial.entries.map {
            SettingsRow("mat:${it.name}", when (it) {
                GlassMaterial.Frosted -> "Frosted Glass (Classic)"
                GlassMaterial.Liquid -> "Liquid Glass (Tinted)"
                GlassMaterial.Clear -> "Liquid Glass (Clear)"
            }, "MATERIAL",
                selectable = true, checked = prefs.material == it)
        } + listOf(
            SettingsRow("reduce", "Reduce Transparency", "ACCESSIBILITY", onOff(prefs.reduceTransparency)),
        )
        SettingsPage.HomeScreen -> FocusEffect.entries.map {
            SettingsRow("fx:${it.name}", it.label, "FOCUS EFFECT", selectable = true, checked = prefs.focusEffect == it)
        } + listOf(
            SettingsRow("labels", "App Names", "APP NAMES", prefs.labels.label),
            SettingsRow("hero", "Top Shelf", "TOP SHELF", onOff(prefs.heroEnabled)),
            SettingsRow("recs", "Recommendations from Apps", "TOP SHELF", if (hasRecommendationAccess()) "Allowed" else "Not Allowed"),
            SettingsRow("autorotate", "Auto-Advance", "TOP SHELF", onOff(prefs.heroAutoRotate)),
            SettingsRow("weather", "Weather", "TIME PILL", onOff(prefs.weather)),
            SettingsRow("tempUnit", "Temperature", "TIME PILL", prefs.temperatureUnit.label),
            SettingsRow("source", "Artwork Source", "TOP SHELF", prefs.heroSource.label),
            SettingsRow("columns", "Columns", "LAYOUT", prefs.columns.toString()),
            SettingsRow("resetLayout", "Reset Home Screen Layout", "LAYOUT"),
            SettingsRow("demo", "Demo Content", "LAYOUT", onOff(prefs.demoContent)),
        )
        SettingsPage.Screensaver -> listOf(
            SettingsRow("ss:show", "Show Screensaver", "SCREENSAVER", chevron = true),
            SettingsRow("ss:source", "Video Source", "SCREENSAVER", prefs.aerialSource.label),
            SettingsRow("ss:quality", "Video Quality", "SCREENSAVER", prefs.aerialQuality.label),
            SettingsRow("ss:range", "Dynamic Range", "SCREENSAVER", rangeLabel()),
            SettingsRow("ss:location", "Show Location", "SCREENSAVER", onOff(prefs.aerialShowLocation)),
            SettingsRow("ss:system", "Use as System Screensaver", "SYSTEM", chevron = true),
        )
        SettingsPage.Apps -> allApps.sortedBy { it.label.lowercase() }.map {
            SettingsRow("app:${it.id}", it.label, "SHOW ON HOME SCREEN", if (it.id in prefs.hiddenApps) "Hidden" else "Shown", app = it)
        }.ifEmpty { listOf(SettingsRow("none", "No Apps Installed", "SHOW ON HOME SCREEN")) }
        SettingsPage.About -> listOf(
            SettingsRow("i:name", "Name", "PRISM TV", "Prism TV Launcher"),
            SettingsRow("i:ver", "Version", "PRISM TV", BuildConfig.VERSION_NAME),
            SettingsRow("upd:check", "Software Update", "SOFTWARE UPDATE", availableUpdate?.let { "Install ${it.versionName}" } ?: updateStatus, chevron = true),
            SettingsRow("upd:auto", "Check Automatically", "SOFTWARE UPDATE", onOff(prefs.updateCheck)),
            SettingsRow("i:model", "Model", "DEVICE", "${Build.MANUFACTURER} ${Build.MODEL}"),
            SettingsRow("i:os", "Android", "DEVICE", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
            SettingsRow("i:glass", "Liquid Glass", "DEVICE", if (Build.VERSION.SDK_INT >= 33) "Refraction Shader" else "Refraction Bands"),
        )
    }

    private fun settingsKey(k: NavKey): Boolean {
        val page = settingsStack.last()
        val rows = settingsRows(page)
        val f = (settingsFocus[page] ?: 0).coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        when (k) {
            NavKey.Up -> settingsFocus[page] = (f - 1).coerceAtLeast(0)
            NavKey.Down -> settingsFocus[page] = (f + 1).coerceAtMost(rows.size - 1)
            NavKey.Select -> rows.getOrNull(f)?.let { settingsSelect(it) }
            NavKey.Left -> if (settingsStack.size > 1) settingsStack = settingsStack.dropLast(1)
            NavKey.Menu -> closeOverlay()
            else -> Unit
        }
        return true
    }

    private fun settingsSelect(r: SettingsRow) {
        val p = prefs
        when {
            r.id == "appearance" || r.id == "material" -> pushPage(SettingsPage.Appearance)
            r.id == "home" -> pushPage(SettingsPage.HomeScreen)
            r.id == "screensaver" -> pushPage(SettingsPage.Screensaver)
            r.id == "apps" -> pushPage(SettingsPage.Apps)
            r.id == "about" -> pushPage(SettingsPage.About)
            r.id == "usage" -> SystemActions.open(ctx, Settings.ACTION_USAGE_ACCESS_SETTINGS)
            r.id == "a11y" -> SystemActions.open(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS)
            r.id == "homeOverride" -> updatePrefs(p.copy(homeOverride = !p.homeOverride))
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
        showBanner("Prism TV", "Settings have been reset.", Icons.Rounded.Info)
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

    fun showBanner(title: String, message: String, icon: ImageVector = Icons.Rounded.PowerSettingsNew) {
        val spec = BannerSpec(title, message, icon)
        banner = spec
        bannerJob?.cancel()
        bannerJob = viewModelScope.launch {
            delay(3_600)
            if (banner?.id == spec.id) banner = null
        }
    }

    /** "Automatic" follows the time of day, like tvOS. */
    fun automaticIsDark(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h >= 19 || h < 7
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
        b.getString("glass")?.let { v ->
            p = p.copy(material = when {
                v.equals("frosted", true) || v.equals("classic", true) -> GlassMaterial.Frosted
                v.equals("clear", true) -> GlassMaterial.Clear
                else -> GlassMaterial.Liquid
            })
        }
        b.getString("focusEffect")?.let { v -> FocusEffect.entries.firstOrNull { it.name.equals(v, true) }?.let { p = p.copy(focusEffect = it) } }
        b.getString("aerial")?.let { v -> AerialSource.entries.firstOrNull { it.name.equals(v, true) }?.let { p = p.copy(aerialSource = it) } }
        if (b.containsKey("autorotate")) p = p.copy(heroAutoRotate = b.getBoolean("autorotate"))
        if (b.containsKey("columns")) p = p.copy(columns = b.getInt("columns", 6).coerceIn(5, 6))
        updatePrefs(p)
        b.getString("clock")?.let { clockOverride = it }

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
                settingsStack = if (page == SettingsPage.Root) listOf(SettingsPage.Root) else listOf(SettingsPage.Root, page)
                settingsFocus[page] = b.getInt("focus", 0)
            }
            "dialog" -> {
                overlay = Overlay.Settings
                settingsFocus[SettingsPage.Root] = 11
                confirm(
                    "Reset Prism TV?",
                    "This restores the default appearance, layout and hidden apps. Your installed apps aren't affected.",
                    "Reset",
                ) { resetPrefs() }
            }
            "launch" -> (items.getOrNull(c) as? AppItem)?.let { activateItem(it, c) }
            "aerial" -> startScreensaver()
        }
    }
}

private const val UPDATE_INTERVAL_MS = 6L * 60 * 60 * 1000
