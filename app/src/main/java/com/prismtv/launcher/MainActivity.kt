package com.prismtv.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.prismtv.launcher.ui.theme.uiBlurRadius
import com.prismtv.launcher.ui.theme.uiSaturation
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.prismtv.launcher.data.AppearanceSetting
import com.prismtv.launcher.data.GlassColour
import com.prismtv.launcher.data.GlassMaterial
import com.prismtv.launcher.data.KeyboardColour
import com.prismtv.launcher.ui.keyboard.KeyboardOverlay
import com.prismtv.launcher.ui.components.BannerHost
import com.prismtv.launcher.ui.components.LocalAvatar
import com.prismtv.launcher.ui.LocalFocusMotion
import com.prismtv.launcher.ui.components.DialogHost
import com.prismtv.launcher.ui.components.LaunchOverlay
import com.prismtv.launcher.ui.controlcentre.ControlCentre
import com.prismtv.launcher.ui.glass.BackdropState
import com.prismtv.launcher.ui.glass.GlassSource
import com.prismtv.launcher.ui.glass.LiquidSaturation
import com.prismtv.launcher.ui.glass.LocalBackdrop
import com.prismtv.launcher.ui.glass.SupportsLiveBlur
import com.prismtv.launcher.ui.glass.backdropSource
import com.prismtv.launcher.ui.glass.blurRadiusFor
import com.prismtv.launcher.ui.home.ContextMenuHost
import com.prismtv.launcher.ui.home.FolderOverlay
import com.prismtv.launcher.ui.home.HomeScreen
import com.prismtv.launcher.ui.home.Sidebar
import com.prismtv.launcher.ui.settings.SettingsScreen
import com.prismtv.launcher.ui.switcher.AppSwitcher
import com.prismtv.launcher.ui.theme.Appearance
import com.prismtv.launcher.ui.theme.LocalPrism
import com.prismtv.launcher.ui.theme.Palettes
import com.prismtv.launcher.ui.theme.PrismTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    private var lastHomePress = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> vm.onScreenOff()
                Intent.ACTION_SCREEN_ON -> vm.onScreenOn()
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            vm.refreshApps(debounce = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.prismtv.launcher.system.CrashLog.install(this)
        super.onCreate(savedInstanceState)
        hideSystemBars()
        intent?.let { i -> i.extras?.let { if (isDebugIntent(i)) vm.applyDebug(it) } }
        setContent { PrismApp(vm) }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        // Who's Watching? when the TV wakes (Settings › Profiles).
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (savedInstanceState == null && intent?.let { isDebugIntent(it) } != true) askForRecommendationsOnce()
        com.prismtv.launcher.system.AppCloser.snapshotter = snapshot
    }

    /** A still of this window, shown over Settings while it closes apps you swiped away. */
    private val snapshot: suspend () -> android.graphics.Bitmap? = { captureWindow() }

    private suspend fun captureWindow(): android.graphics.Bitmap? {
        if (Build.VERSION.SDK_INT < 26 || isFinishing) return null
        val v = window.decorView
        if (v.width <= 0 || v.height <= 0) return null
        val bmp = try {
            android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            return null
        }
        return kotlinx.coroutines.withTimeoutOrNull(1000) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                try {
                    android.view.PixelCopy.request(
                        window, bmp,
                        { result -> if (cont.isActive) cont.resumeWith(Result.success(if (result == android.view.PixelCopy.SUCCESS) bmp else null)) },
                        android.os.Handler(android.os.Looper.getMainLooper()),
                    )
                } catch (_: Throwable) {
                    if (cont.isActive) cont.resumeWith(Result.success(null))
                }
            }
        }
    }

    /** First launch: ask once for access to other apps' recommendation cards. */
    private fun askForRecommendationsOnce() {
        if (Build.VERSION.SDK_INT < 23 || vm.hasRecommendationAccess()) return
        val sp = getSharedPreferences("prism_prefs", MODE_PRIVATE)
        if (sp.getBoolean("askedTvListings", false)) return
        sp.edit().putBoolean("askedTvListings", true).apply()
        requestRecommendationAccess()
    }

    /** Settings › Backup & Restore › Choose a File… (Android's document picker, where the TV has one). */
    private val backupPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.confirmRestore(uri)
    }

    fun pickBackupFile() {
        val probe = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        if (probe.resolveActivity(packageManager) == null) {
            vm.noFilePicker()
            return
        }
        runCatching { backupPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            .onFailure { vm.noFilePicker() }
    }

    /** Settings › Wallpaper › Your Pictures. */
    fun requestPhotoAccess() {
        if (Build.VERSION.SDK_INT < 23) return
        val perm = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES else android.Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) {
            vm.loadPhotos()
            return
        }
        requestPermissions(arrayOf(perm), REQ_PHOTOS)
    }

    fun requestRecommendationAccess() {
        if (Build.VERSION.SDK_INT < 23) return
        if (!shouldShowRequestPermissionRationale(TV_LISTINGS) &&
            getSharedPreferences("prism_prefs", MODE_PRIVATE).getBoolean("deniedTvListings", false)
        ) {
            // Previously denied with "don't ask again": send the user to the app's permission page.
            runCatching {
                startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:$packageName"))
                )
            }
            return
        }
        requestPermissions(arrayOf(TV_LISTINGS), REQ_TV_LISTINGS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_TV_LISTINGS) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            getSharedPreferences("prism_prefs", MODE_PRIVATE).edit().putBoolean("deniedTvListings", !granted).apply()
            if (granted) vm.refreshRecommendations(force = true)
        }
        if (requestCode == REQ_PHOTOS) vm.loadPhotos()
        if (requestCode == REQ_MIC) vm.resumeTick++
    }

    /** Settings › Keyboard › Dictation Microphone: Prism Keyboard listens through the remote's mic. */
    fun requestMicAccess() {
        if (Build.VERSION.SDK_INT < 23) return
        requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_MIC)
    }

    private companion object {
        const val REQ_TV_LISTINGS = 41
        const val REQ_PHOTOS = 42
        const val REQ_MIC = 43
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val extras = intent.extras
        if (extras != null && isDebugIntent(intent)) {
            vm.applyDebug(extras)
            return
        }
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Double-press Home/TV opens the App Switcher, like tvOS.
            val now = SystemClock.uptimeMillis()
            if (now - lastHomePress < 450) {
                vm.openSwitcher()
                lastHomePress = 0
            } else {
                vm.onHomePressed()
                lastHomePress = now
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        vm.onResume()
    }

    override fun onPause() {
        vm.onPause()
        super.onPause()
    }

    /** Every button press (volume included) counts as activity for Screensaver › Start After. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        vm.noteInteraction()
    }

    /** Android is short of memory: give back cached artwork rather than be killed (and "reset"). */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        vm.trimMemory(level)
    }

    override fun onDestroy() {
        if (com.prismtv.launcher.system.AppCloser.snapshotter === snapshot) com.prismtv.launcher.system.AppCloser.snapshotter = null
        runCatching { unregisterReceiver(packageReceiver) }
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    /** Screenshot/preview driver – debug builds only. */
    private fun isDebugIntent(i: Intent): Boolean =
        BuildConfig.DEBUG && (i.hasExtra("screen") || i.hasExtra("demo") || i.hasExtra("appearance"))

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
fun PrismApp(vm: LauncherViewModel) {
    // Scale to the window Prism actually draws into. Configuration.screenWidthDp/HeightDp leave out
    // the system bars even while they're hidden, and on tablet-style firmware (Ugoos and others) that
    // made the canvas too small and pinned Home to the left.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        PrismCanvas(vm, if (constraints.hasBoundedWidth) w else 0, if (constraints.hasBoundedHeight) h else 0)
    }
}

@Composable
private fun PrismCanvas(vm: LauncherViewModel, windowW: Int, windowH: Int) {
    val config = LocalConfiguration.current
    val base = LocalDensity.current
    // Design canvas is 960×540dp (1080p @ xhdpi). Normalise so every TV renders the same layout.
    val px = if (windowW > 0 && windowH > 0) min(windowW / 960f, windowH / 540f)
    else base.density * (min(config.screenWidthDp / 960f, config.screenHeightDp / 540f).takeIf { it > 0f } ?: 1f)
    // Settings › Appearance › Text Size scales Prism's text on top of the system font size.
    val density = Density(px, base.fontScale * vm.prefs.textScale)

    val appearance = when (vm.prefs.appearance) {
        AppearanceSetting.Light -> Appearance.Light
        AppearanceSetting.Balanced -> Appearance.Balanced
        AppearanceSetting.Dark -> Appearance.Dark
        AppearanceSetting.Automatic -> if (vm.automaticIsDark()) Appearance.Dark else Appearance.Light
    }
    // Glass surfaces take the Glass Colour (Automatic follows Appearance); Settings and pop-ups take
    // Appearance and are always frosted.
    val glassAppearance = when (vm.prefs.glassColour) {
        GlassColour.Automatic -> appearance
        GlassColour.Light -> Appearance.Light
        GlassColour.Balanced -> Appearance.Balanced
        GlassColour.Dark -> Appearance.Dark
        GlassColour.Clear -> appearance
    }
    val theme = PrismTheme(
        palette = Palettes.of(glassAppearance),
        material = vm.prefs.glass,
        reduceTransparency = vm.prefs.reduceTransparency,
        focusEffect = vm.prefs.focusEffect,
        labels = vm.prefs.labels,
        clearTint = vm.prefs.glassColour == GlassColour.Clear,
        ui = vm.prefs.uiTransparency,
        wobble = vm.prefs.focusWobble && !vm.prefs.liteMode,
        liquidControls = vm.prefs.material != GlassMaterial.Frosted && !vm.prefs.liteMode,
        lite = vm.prefs.liteMode,
    )
    val uiTheme = theme.copy(palette = Palettes.of(appearance), material = GlassMaterial.Frosted, clearTint = false)

    val bg = rememberGraphicsLayer()
    val bgBlur = rememberGraphicsLayer()
    val scene = rememberGraphicsLayer()
    val sceneBlur = rememberGraphicsLayer()
    val full = rememberGraphicsLayer()
    val fullBlur = rememberGraphicsLayer()
    val sceneUiBlur = rememberGraphicsLayer()
    val backdrop = remember { BackdropState(bg, bgBlur, scene, sceneBlur, full, fullBlur, sceneUiBlur) }
    val blurPx = with(density) { blurRadiusFor(theme.liquid).toPx() }
    // Settings and pop-ups: always frosted, as transparent as Settings & Pop-ups Transparency says.
    val uiBlurPx = with(density) { uiBlurRadius(vm.prefs.uiTransparency).toPx() }
    val uiSat = uiSaturation(vm.prefs.uiTransparency)
    val art = vm.currentArt
    val gridMode = vm.isGridMode

    // Blur settings are applied only when they change: setting a new RenderEffect re-renders the blurred
    // layers, and this composable also recomposes for unrelated things (art, grid mode).
    val blurKey = remember { arrayOfNulls<Any>(1) }
    val blurConfig = listOf(theme.lite, theme.liquid, blurPx, uiBlurPx, uiSat)
    SideEffect {
        if (blurKey[0] == blurConfig) {
            // unchanged
        } else if (SupportsLiveBlur && theme.lite) {
            blurKey[0] = blurConfig
            // Lite Mode: nothing blurs live, so the blurred twins cost nothing even if drawn.
            listOf(backdrop.bgBlur, backdrop.sceneBlur, backdrop.fullBlur, backdrop.sceneUiBlur).forEach {
                it.renderEffect = null
                it.colorFilter = null
            }
        } else if (SupportsLiveBlur) {
            blurKey[0] = blurConfig
            listOf(backdrop.bgBlur, backdrop.sceneBlur).forEach {
                it.renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                it.colorFilter = if (theme.liquid) LiquidSaturation else null
            }
            listOf(backdrop.fullBlur, backdrop.sceneUiBlur).forEach {
                it.renderEffect = BlurEffect(uiBlurPx, uiBlurPx, TileMode.Clamp)
                it.colorFilter = if (uiSat > 1.01f) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(uiSat) }) else null
            }
        }
        backdrop.fallbackLight = if (gridMode) art?.heavy else art?.light
        backdrop.fallbackHeavy = art?.heavy
        backdrop.fallbackDim = if (gridMode) theme.palette.bgBase.copy(alpha = theme.palette.backgroundDim * 0.6f) else Color.Transparent
    }

    val clock = rememberClock(vm.clockOverride)
    val scope = rememberCoroutineScope()
    val keys = remember { RemoteKeys(vm, scope) }
    val focusRequester = remember { FocusRequester() }
    val activity = LocalContext.current as? Activity
    // One-off requests from the view model. It outlives the window, so each request is marked as handled:
    // otherwise a rebuilt window saw the same request again (Restart Launcher restarted forever).
    LaunchedEffect(vm.restartTick) {
        if (vm.consumeTick("restart", vm.restartTick)) {
            if (activity == null || !com.prismtv.launcher.system.RestartActivity.restart(activity)) activity?.recreate()
        }
    }
    LaunchedEffect(vm.permissionRequestTick) {
        if (vm.consumeTick("recs", vm.permissionRequestTick)) (activity as? MainActivity)?.requestRecommendationAccess()
    }
    LaunchedEffect(vm.backupPickTick) {
        if (vm.consumeTick("backup", vm.backupPickTick)) (activity as? MainActivity)?.pickBackupFile()
    }
    LaunchedEffect(vm.photoPermissionTick) {
        if (vm.consumeTick("photos", vm.photoPermissionTick)) (activity as? MainActivity)?.requestPhotoAccess()
    }
    LaunchedEffect(vm.micPermissionTick) {
        if (vm.consumeTick("mic", vm.micPermissionTick)) (activity as? MainActivity)?.requestMicAccess()
    }
    val keyboardLook = when (vm.prefs.keyboardColour) {
        KeyboardColour.Automatic -> appearance
        KeyboardColour.Light -> Appearance.Light
        KeyboardColour.Balanced -> Appearance.Balanced
        KeyboardColour.Dark -> Appearance.Dark
        KeyboardColour.Clear -> Appearance.Dark
    }
    // The text field above the keyboard: its own colour, or the keyboard's.
    val keyboardClear = vm.prefs.keyboardColour == KeyboardColour.Clear
    val fieldColour = vm.prefs.keyboardFieldColour
    val fieldLook = when (fieldColour) {
        KeyboardColour.Automatic -> keyboardLook
        KeyboardColour.Light -> Appearance.Light
        KeyboardColour.Balanced -> Appearance.Balanced
        KeyboardColour.Dark, KeyboardColour.Clear -> Appearance.Dark
    }
    val fieldClear = if (fieldColour == KeyboardColour.Automatic) keyboardClear else fieldColour == KeyboardColour.Clear

    CompositionLocalProvider(
        LocalDensity provides density,
        LocalPrism provides theme,
        LocalBackdrop provides backdrop,
        LocalAvatar provides vm.avatarLook,
        LocalFocusMotion provides vm.focusMotion,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { backdrop.rootSize = it }
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { keys.handle(it) }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .backdropSource(backdrop, GlassSource.Full)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .backdropSource(backdrop, GlassSource.Scene)
                ) {
                    HomeScreen(vm, clock)
                }
                FolderOverlay(vm)
                Sidebar(vm, clock)
                ControlCentre(vm, clock)
                AppSwitcher(vm)
                CompositionLocalProvider(LocalPrism provides uiTheme) { SettingsScreen(vm) }
            }
            ContextMenuHost(vm)
            CompositionLocalProvider(LocalPrism provides uiTheme) { DialogLayer(vm) }
            com.prismtv.launcher.ui.home.ProfilePicker(vm)
            CompositionLocalProvider(LocalPrism provides uiTheme) { KeyboardOverlay(vm, keyboardLook, clear = keyboardClear, fieldLook = fieldLook, fieldClear = fieldClear) }
            CompositionLocalProvider(LocalPrism provides uiTheme) { com.prismtv.launcher.ui.home.PasscodePad(vm) }
            LaunchOverlay(vm)
            // Banners take the Home Screen's Material and Material Colour; Settings and dialogs stay frosted.
            BannerLayer(vm)
        }
    }

    FocusKeeper(vm, focusRequester)
    BackHandler { vm.onBack() }
}

// Small wrappers so a dialog's focus, a banner or a refresh only recomposes these, not the whole canvas.
@Composable
private fun DialogLayer(vm: LauncherViewModel) = DialogHost(vm.dialog, vm.dialogFocus)

@Composable
private fun BannerLayer(vm: LauncherViewModel) = BannerHost(vm.banner)

@Composable
private fun FocusKeeper(vm: LauncherViewModel, focusRequester: FocusRequester) {
    LaunchedEffect(vm.resumeTick) {
        runCatching { focusRequester.requestFocus() }
    }
}

/**
 * Maps remote keys to [NavKey]s. OK/Select fires on release so that holding it can open the context
 * menu instead (like tvOS): a hold is detected from key repeats, the long-press flag, or a timer for
 * remotes that don't auto-repeat.
 */
private class RemoteKeys(private val vm: LauncherViewModel, private val scope: CoroutineScope) {
    private var selectDown = false
    private var longFired = false
    private var holdJob: Job? = null

    fun handle(e: KeyEvent): Boolean {
        val native = e.nativeKeyEvent
        val key = when (native.keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_UP -> NavKey.Up
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> NavKey.Down
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> NavKey.Left
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> NavKey.Right
            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
            AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
            AndroidKeyEvent.KEYCODE_BUTTON_A -> return handleSelect(native)
            AndroidKeyEvent.KEYCODE_MENU,
            AndroidKeyEvent.KEYCODE_BUTTON_Y -> NavKey.Menu
            AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> NavKey.PlayPause
            in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> {
                // Number keys type into a passcode; otherwise they're left to Android.
                if (vm.passcode == null) return false
                if (native.action == AndroidKeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                    vm.typePasscodeDigit('0' + (native.keyCode - AndroidKeyEvent.KEYCODE_0))
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_APP_SWITCH -> {
                if (native.action == AndroidKeyEvent.ACTION_DOWN && native.repeatCount == 0) vm.openSwitcher()
                return true
            }
            else -> return false
        }
        if (native.action == AndroidKeyEvent.ACTION_DOWN) vm.onKey(key, repeat = native.repeatCount > 0)
        return true
    }

    private fun handleSelect(native: AndroidKeyEvent): Boolean {
        when (native.action) {
            AndroidKeyEvent.ACTION_DOWN -> {
                if (native.repeatCount == 0) {
                    selectDown = true
                    longFired = false
                    holdJob?.cancel()
                    holdJob = scope.launch {
                        delay(LONG_PRESS_MS)
                        fireLong()
                    }
                } else if (selectDown && (native.isLongPress || native.eventTime - native.downTime >= LONG_PRESS_MS)) {
                    fireLong()
                }
            }
            AndroidKeyEvent.ACTION_UP -> {
                holdJob?.cancel()
                holdJob = null
                // Ignore a release whose press happened before we had focus (e.g. the press that opened us).
                if (selectDown && !longFired) vm.onKey(NavKey.Select)
                selectDown = false
                longFired = false
            }
        }
        return true
    }

    private fun fireLong() {
        if (!selectDown || longFired) return
        longFired = true
        holdJob?.cancel()
        vm.onKey(NavKey.LongSelect)
    }

    private companion object {
        const val LONG_PRESS_MS = 550L
    }
}

@Composable
private fun rememberClock(override: String?): String {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val t = System.currentTimeMillis()
            now = t
            delay(60_000L - t % 60_000L + 50L)
        }
    }
    return override ?: android.text.format.DateFormat.getTimeFormat(ctx).format(Date(now))
}
