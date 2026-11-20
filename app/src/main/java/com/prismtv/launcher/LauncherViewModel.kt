package com.prismtv.launcher

import android.app.Application
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prismtv.launcher.data.AppItem
import com.prismtv.launcher.data.AppRepository
import com.prismtv.launcher.data.AppearanceSetting
import com.prismtv.launcher.data.ArtRef
import com.prismtv.launcher.data.ArtRepository
import com.prismtv.launcher.data.DemoContent
import com.prismtv.launcher.data.FocusEffect
import com.prismtv.launcher.data.FolderItem
import com.prismtv.launcher.data.GlassMaterial
import com.prismtv.launcher.data.GridItem
import com.prismtv.launcher.data.HeroArt
import com.prismtv.launcher.data.HeroItem
import com.prismtv.launcher.data.HeroSource
import com.prismtv.launcher.data.LabelMode
import com.prismtv.launcher.data.LauncherApp
import com.prismtv.launcher.data.LauncherPrefs
import com.prismtv.launcher.data.PrefsStore
import com.prismtv.launcher.data.RecentEntry
import com.prismtv.launcher.data.RecentsRepository
import com.prismtv.launcher.data.RecommendationsRepository
import com.prismtv.launcher.data.ResArt
import com.prismtv.launcher.system.PrismAccessibilityService
import com.prismtv.launcher.system.SystemActions
import com.prismtv.launcher.ui.FolderLayout
import com.prismtv.launcher.ui.HomeLayout
import com.prismtv.launcher.ui.SwitcherLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class Zone { TopBar, Hero, Grid }
enum class Overlay { None, ControlCentre, Switcher, Settings, Folder }
enum class NavKey { Up, Down, Left, Right, Select, Menu, PlayPause }

data class DialogButton(val label: String, val action: () -> Unit)
data class DialogSpec(val title: String, val message: String, val buttons: List<DialogButton>)
data class BannerSpec(val title: String, val message: String, val icon: ImageVector, val id: Long = System.nanoTime())

enum class SettingsPage(val title: String) {
    Root("Settings"), Appearance("Appearance"), HomeScreen("Home Screen"), Apps("Apps"), About("About")
}

data class SettingsRow(
    val id: String,
    val title: String,
    val section: String? = null,
    val value: String? = null,
    val selectable: Boolean = false,
    val checked: Boolean = false,
    val chevron: Boolean = false,
)

/** A zoom transition between a tile (in dp) and full screen. */
data class LaunchAnim(
    val app: LauncherApp,
    val from: Rect,
    val art: ArtRef?,
    val reverse: Boolean = false,
    val id: Long = System.nanoTime(),
)

/** Control Centre focus targets, in reading order. */
object CC {
    const val Home = 0
    const val Toggle = 1
    const val Profile = 2
    const val Power = 3
    const val WiFi = 4
    const val Dnd = 5
    const val Audio = 6
    const val Sleep = 7
    const val Game = 8
    const val Access = 9
    const val Restrict = 10
    const val Search = 11
}

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Application get() = getApplication()
    private val store = PrefsStore(app)
    private val appsRepo = AppRepository(app)
    private val artRepo = ArtRepository(app)
    private val recsRepo = RecommendationsRepository(app)
    private val recentsRepo = RecentsRepository(app)

    var prefs by mutableStateOf(store.load())
        private set

    private var realApps by mutableStateOf<List<LauncherApp>>(emptyList())
    private var heroItemsByApp by mutableStateOf<Map<String, List<HeroItem>>>(emptyMap())

    /** Visible Home Screen items (apps + folders), in order. */
    val items: List<GridItem> by derivedStateOf {
        if (prefs.demoContent) DemoContent.items
        else realApps.filterNot { it.id in prefs.hiddenApps }.map { AppItem(it) }
    }

    val allApps: List<LauncherApp>
        get() = if (prefs.demoContent) DemoContent.items.flatMap {
            when (it) {
                is AppItem -> listOf(it.app)
                is FolderItem -> it.apps
            }
        } else realApps

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

    // ---------------------------------------------------------------- folder
    var openFolder by mutableStateOf<FolderItem?>(null)
    var folderFocus by mutableIntStateOf(0)

    // ---------------------------------------------------------------- control centre
    var ccFocus by mutableIntStateOf(CC.Power)
    var sleepMinutes by mutableIntStateOf(0)
    var dndOn by mutableStateOf(false)
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
    private var pendingReturn: LaunchAnim? = null
    var currentArt by mutableStateOf<HeroArt?>(null)
        private set
    val artCache = mutableStateMapOf<String, HeroArt>()
    private var heroTimer: Job? = null

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
    }

    fun heroesFor(app: LauncherApp): List<HeroItem> {
        val fallback = HeroItem(
            key = "fallback:${app.id}",
            appId = app.id,
            appLabel = app.label,
            title = app.label,
            meta = if (app.demo != null) "" else "Open ${app.label}",
            art = ResArt(DemoContent.sceneFor(app.packageName)),
            logo = app.banner,
        )
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
            realApps = loaded
            val recs = withContext(Dispatchers.IO) { recsRepo.load() }
            heroItemsByApp = loaded.associate { app ->
                app.id to (recs[app.packageName] ?: emptyList()).map { it.copy(appId = app.id, appLabel = app.label) }
            }.filterValues { it.isNotEmpty() }
        }
    }

    private fun restartHeroTimer() {
        heroTimer?.cancel()
        heroTimer = viewModelScope.launch {
            while (true) {
                delay(9_000)
                val n = currentHeroItems.size
                val idle = overlay == Overlay.None && dialog == null && launchAnim == null
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
            row = 0; col = 0; heroAppIndex = 0; heroIndex = 0
            if (!p.demoContent) refreshApps()
        }
    }

    // ================================================================= input

    fun onKey(k: NavKey): Boolean {
        if (launchAnim != null) return true
        dialog?.let { return dialogKey(it, k) }
        return when (overlay) {
            Overlay.ControlCentre -> ccKey(k)
            Overlay.Switcher -> switcherKey(k)
            Overlay.Settings -> settingsKey(k)
            Overlay.Folder -> folderKey(k)
            Overlay.None -> homeKey(k)
        }
    }

    fun onBack() {
        when {
            launchAnim != null -> Unit
            dialog != null -> dialog = null
            overlay == Overlay.Settings && settingsStack.size > 1 -> settingsStack = settingsStack.dropLast(1)
            overlay == Overlay.Switcher -> closeSwitcherToHome()
            overlay != Overlay.None -> closeOverlay()
            zone != Zone.Grid -> { zone = Zone.Grid; row = 0; col = heroAppIndex }
            row > 0 -> { row = 0; col = col.coerceAtMost(rowSize(0) - 1) }
            else -> Unit
        }
    }

    fun onHomePressed() {
        dialog = null
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
        dndOn = SystemActions.dndOn(ctx)
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
        when (zone) {
            Zone.Grid -> when (k) {
                NavKey.Up -> when {
                    row > 0 -> { row--; col = col.coerceAtMost(rowSize(row) - 1); if (row == 0) setDockCol(col) }
                    prefs.heroEnabled -> { zone = Zone.Hero; heroButton = 0 }
                    else -> { zone = Zone.TopBar; topIndex = 0 }
                }
                NavKey.Down -> if (row < rowCount() - 1) { row++; col = col.coerceAtMost(rowSize(row) - 1) }
                NavKey.Left -> if (col > 0) { col--; if (row == 0) setDockCol(col) }
                NavKey.Right -> if (col < rowSize(row) - 1) { col++; if (row == 0) setDockCol(col) }
                NavKey.Select -> items.getOrNull(row * cols + col)?.let { activateItem(it, row * cols + col) }
                NavKey.Menu -> openControlCentre()
                NavKey.PlayPause -> if (row == 0) playHero()
            }
            Zone.Hero -> when (k) {
                NavKey.Up -> { zone = Zone.TopBar; topIndex = 0 }
                NavKey.Down -> { zone = Zone.Grid; row = 0; col = heroAppIndex }
                NavKey.Left -> if (heroButton > 0) heroButton-- else stepHero(-1)
                NavKey.Right -> if (heroButton < 1) heroButton++ else stepHero(1)
                NavKey.Select, NavKey.PlayPause -> if (heroButton == 0) playHero() else openApp(dockItem)
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
            is AppItem -> {
                val cols = prefs.columns
                val r = index / cols
                val trayTop = if (r == 0) HomeLayout.TrayTopHero else HomeLayout.gridTrayTop(cols, r)
                val gap = if (r == 0) HomeLayout.RowGapHero else HomeLayout.RowGapGrid
                startLaunch(item.app, HomeLayout.tileRect(cols, index, trayTop, gap))
            }
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
        launchAnim = LaunchAnim(app, from, heroesFor(app).firstOrNull()?.art)
    }

    /** Called by the transition overlay once the zoom-in has filled the screen. */
    fun onLaunchZoomComplete(anim: LaunchAnim) {
        if (anim.reverse) {
            launchAnim = null
            return
        }
        val app = anim.app
        if (app.demo != null) {
            showBanner(app.label, "Demo app — install apps to see them here.", Icons.Rounded.Info)
            viewModelScope.launch {
                delay(500)
                launchAnim = anim.copy(reverse = true, id = System.nanoTime())
            }
            return
        }
        val intent = SystemActions.launchIntent(ctx, app)
        if (intent != null && SystemActions.start(ctx, intent, SystemActions.fadeOptions(ctx))) {
            recentsRepo.record(app.packageName)
            pendingReturn = anim
            overlay = Overlay.None
            openFolder = null
        } else {
            showBanner(app.label, "This app couldn't be opened.", Icons.Rounded.Info)
            launchAnim = anim.copy(reverse = true, id = System.nanoTime())
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
            NavKey.Down -> if (folderFocus + c < n) folderFocus += c
            NavKey.Select -> folder.apps.getOrNull(folderFocus)?.let { startLaunch(it, FolderLayout.tileRect(folderFocus)) }
            NavKey.Menu -> openControlCentre()
            NavKey.PlayPause -> Unit
        }
        return true
    }

    // ================================================================= control centre

    fun openControlCentre() {
        dndOn = SystemActions.dndOn(ctx)
        ccFocus = CC.Power
        overlay = Overlay.ControlCentre
    }

    private val ccNav: Map<Int, IntArray> = mapOf(
        // up, down, left, right  (-1 = none)
        CC.Home to intArrayOf(-1, CC.Power, -1, CC.Toggle),
        CC.Toggle to intArrayOf(-1, CC.WiFi, -1, CC.Profile),
        CC.Profile to intArrayOf(-1, CC.WiFi, CC.Toggle, -1),
        CC.Power to intArrayOf(CC.Toggle, CC.Audio, -1, CC.WiFi),
        CC.WiFi to intArrayOf(CC.Toggle, CC.Dnd, CC.Power, -1),
        CC.Dnd to intArrayOf(CC.WiFi, CC.Sleep, CC.Power, -1),
        CC.Audio to intArrayOf(CC.Power, CC.Game, -1, CC.Sleep),
        CC.Sleep to intArrayOf(CC.Dnd, CC.Restrict, CC.Audio, -1),
        CC.Game to intArrayOf(CC.Audio, -1, -1, CC.Access),
        CC.Access to intArrayOf(CC.Audio, -1, CC.Game, CC.Restrict),
        CC.Restrict to intArrayOf(CC.Sleep, -1, CC.Access, CC.Search),
        CC.Search to intArrayOf(CC.Sleep, -1, CC.Restrict, -1),
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
            NavKey.PlayPause -> -1
        }
        if (next >= 0) ccFocus = next
        return true
    }

    private fun ccSelect(i: Int) {
        when (i) {
            CC.Home -> onHomePressed()
            CC.Toggle -> closeOverlay()
            CC.Profile -> openSettings()
            CC.Power -> if (PrismAccessibilityService.powerDialog()) closeOverlay() else askForPowerAccess()
            CC.WiFi -> SystemActions.open(ctx, Settings.ACTION_WIFI_SETTINGS)
            CC.Dnd -> SystemActions.toggleDnd(ctx)?.let { dndOn = it }
            CC.Audio -> SystemActions.open(ctx, Settings.ACTION_SOUND_SETTINGS)
            CC.Sleep -> cycleSleepTimer()
            CC.Game -> SystemActions.open(ctx, Settings.ACTION_BLUETOOTH_SETTINGS)
            CC.Access -> SystemActions.open(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS)
            CC.Restrict -> SystemActions.open(ctx, Settings.ACTION_SECURITY_SETTINGS)
            CC.Search -> { closeOverlay(); SystemActions.search(ctx) }
        }
    }

    private fun askForPowerAccess() {
        dialog = DialogSpec(
            "Turn On Power Controls",
            "To show the power menu and put your TV to sleep, turn on Prism TV Power Controls in Accessibility settings.",
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
        openFolder = null
        val apps: List<LauncherApp> = if (prefs.demoContent) {
            listOf("demo:cinema", "demo:arcade", "demo:music", "demo:prism")
                .mapNotNull { id -> allApps.firstOrNull { it.id == id } }
        } else {
            recentsRepo.recentPackages().take(8).reversed()
                .mapNotNull { pkg -> realApps.firstOrNull { it.packageName == pkg } }
        }
        // Oldest on the left, most recent next to Home.
        recents = apps.map { RecentEntry(it.id, it, heroesFor(it).firstOrNull()?.art, heroesFor(it).firstOrNull()?.title) } +
            RecentEntry("home", null, null)
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
            NavKey.Down -> Unit
            NavKey.Select -> {
                val e = recents.getOrNull(switcherFocus) ?: return true
                val app = e.app
                if (app == null) closeSwitcherToHome() else startLaunch(app, SwitcherLayout.focusedRect())
            }
            NavKey.Menu -> closeSwitcherToHome()
            NavKey.PlayPause -> Unit
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
        SettingsPage.Root -> listOf(
            SettingsRow("appearance", "Appearance", "GENERAL", prefs.appearance.label, chevron = true),
            SettingsRow("material", "Material", "GENERAL", prefs.material.label, chevron = true),
            SettingsRow("home", "Home Screen", "GENERAL", chevron = true),
            SettingsRow("apps", "Apps", "APPS", if (prefs.hiddenApps.isEmpty()) null else "${prefs.hiddenApps.size} Hidden", chevron = true),
            SettingsRow("usage", "Recent Apps Access", "APPS", onOff(recentsRepo.hasUsageAccess()), chevron = true),
            SettingsRow("a11y", "Power & Sleep Controls", "CONTROL CENTRE", onOff(PrismAccessibilityService.isEnabled), chevron = true),
            SettingsRow("sleep", "Sleep Timer", "CONTROL CENTRE", sleepLabel()),
            SettingsRow("about", "About", "SYSTEM", chevron = true),
            SettingsRow("restart", "Restart Launcher", "SYSTEM"),
            SettingsRow("reset", "Reset Launcher", "SYSTEM"),
        )
        SettingsPage.Appearance -> AppearanceSetting.entries.map {
            SettingsRow("ap:${it.name}", it.label, "APPEARANCE", selectable = true, checked = prefs.appearance == it)
        } + GlassMaterial.entries.map {
            SettingsRow("mat:${it.name}", if (it == GlassMaterial.Frosted) "Frosted Glass (Classic)" else it.label, "MATERIAL",
                selectable = true, checked = prefs.material == it)
        } + listOf(
            SettingsRow("reduce", "Reduce Transparency", "ACCESSIBILITY", onOff(prefs.reduceTransparency)),
        )
        SettingsPage.HomeScreen -> FocusEffect.entries.map {
            SettingsRow("fx:${it.name}", it.label, "FOCUS EFFECT", selectable = true, checked = prefs.focusEffect == it)
        } + listOf(
            SettingsRow("labels", "App Names", "APP NAMES", prefs.labels.label),
            SettingsRow("hero", "Top Shelf", "TOP SHELF", onOff(prefs.heroEnabled)),
            SettingsRow("autorotate", "Auto-Advance", "TOP SHELF", onOff(prefs.heroAutoRotate)),
            SettingsRow("source", "Artwork Source", "TOP SHELF", prefs.heroSource.label),
            SettingsRow("columns", "Columns", "LAYOUT", prefs.columns.toString()),
            SettingsRow("demo", "Demo Content", "LAYOUT", onOff(prefs.demoContent)),
        )
        SettingsPage.Apps -> realApps.map {
            SettingsRow("app:${it.id}", it.label, "SHOW ON HOME SCREEN", if (it.id in prefs.hiddenApps) "Hidden" else "Shown")
        }.ifEmpty { listOf(SettingsRow("none", "No Apps Installed", "SHOW ON HOME SCREEN")) }
        SettingsPage.About -> listOf(
            SettingsRow("i:name", "Name", "PRISM TV", "Prism TV Launcher"),
            SettingsRow("i:ver", "Version", "PRISM TV", BuildConfig.VERSION_NAME),
            SettingsRow("i:model", "Model", "DEVICE", "${Build.MANUFACTURER} ${Build.MODEL}"),
            SettingsRow("i:os", "Android", "DEVICE", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
            SettingsRow("i:blur", "Live Glass", "DEVICE", if (Build.VERSION.SDK_INT >= 31) "Supported" else "Fallback"),
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
            r.id == "apps" -> pushPage(SettingsPage.Apps)
            r.id == "about" -> pushPage(SettingsPage.About)
            r.id == "usage" -> SystemActions.open(ctx, Settings.ACTION_USAGE_ACCESS_SETTINGS)
            r.id == "a11y" -> SystemActions.open(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS)
            r.id == "sleep" -> cycleSleepTimer()
            r.id == "restart" -> confirm("Restart Prism TV?", "The launcher will close and reopen.", "Restart") { restartTick++ }
            r.id == "reset" -> confirm(
                "Reset Prism TV?",
                "This restores the default appearance, layout and hidden apps. Your installed apps aren't affected.",
                "Reset",
            ) { resetPrefs() }
            r.id.startsWith("ap:") -> updatePrefs(p.copy(appearance = AppearanceSetting.valueOf(r.id.removePrefix("ap:"))))
            r.id.startsWith("mat:") -> updatePrefs(p.copy(material = GlassMaterial.valueOf(r.id.removePrefix("mat:"))))
            r.id == "reduce" -> updatePrefs(p.copy(reduceTransparency = !p.reduceTransparency))
            r.id.startsWith("fx:") -> updatePrefs(p.copy(focusEffect = FocusEffect.valueOf(r.id.removePrefix("fx:"))))
            r.id == "labels" -> updatePrefs(p.copy(labels = LabelMode.entries[(p.labels.ordinal + 1) % LabelMode.entries.size]))
            r.id == "hero" -> updatePrefs(p.copy(heroEnabled = !p.heroEnabled))
            r.id == "autorotate" -> updatePrefs(p.copy(heroAutoRotate = !p.heroAutoRotate))
            r.id == "source" -> updatePrefs(p.copy(heroSource = HeroSource.entries[(p.heroSource.ordinal + 1) % HeroSource.entries.size]))
            r.id == "columns" -> updatePrefs(p.copy(columns = if (p.columns == 6) 5 else 6))
            r.id == "demo" -> updatePrefs(p.copy(demoContent = !p.demoContent))
            r.id.startsWith("app:") -> {
                val id = r.id.removePrefix("app:")
                updatePrefs(p.copy(hiddenApps = if (id in p.hiddenApps) p.hiddenApps - id else p.hiddenApps + id))
            }
        }
    }

    private fun confirm(title: String, message: String, action: String, onConfirm: () -> Unit) {
        dialog = DialogSpec(title, message, listOf(DialogButton(action, onConfirm), DialogButton("Cancel") {}))
        dialogFocus = 0
    }

    private fun resetPrefs() {
        store.clear()
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

    private var bannerJob: Job? = null

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
        var p = prefs
        if (b.containsKey("demo")) p = p.copy(demoContent = b.getBoolean("demo"))
        b.getString("appearance")?.let { v -> AppearanceSetting.entries.firstOrNull { it.name.equals(v, true) }?.let { p = p.copy(appearance = it) } }
        b.getString("glass")?.let { v -> p = p.copy(material = if (v.equals("frosted", true) || v.equals("classic", true)) GlassMaterial.Frosted else GlassMaterial.Liquid) }
        b.getString("focusEffect")?.let { v -> FocusEffect.entries.firstOrNull { it.name.equals(v, true) }?.let { p = p.copy(focusEffect = it) } }
        if (b.containsKey("autorotate")) p = p.copy(heroAutoRotate = b.getBoolean("autorotate"))
        if (b.containsKey("columns")) p = p.copy(columns = b.getInt("columns", 6).coerceIn(5, 6))
        updatePrefs(p)
        b.getString("clock")?.let { clockOverride = it }

        dialog = null
        launchAnim = null
        pendingReturn = null
        openFolder = null
        settingsStack = listOf(SettingsPage.Root)
        overlay = Overlay.None
        val r = b.getInt("row", 0)
        val c = b.getInt("col", 0)
        zone = Zone.Grid
        row = 0
        col = c
        heroAppIndex = if (r == 0) c else heroAppIndex
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
                settingsFocus[SettingsPage.Root] = 9
                confirm(
                    "Reset Prism TV?",
                    "This restores the default appearance, layout and hidden apps. Your installed apps aren't affected.",
                    "Reset",
                ) { resetPrefs() }
            }
            "launch" -> {
                (items.getOrNull(c) as? AppItem)?.let { activateItem(it, c) }
            }
        }
    }
}
