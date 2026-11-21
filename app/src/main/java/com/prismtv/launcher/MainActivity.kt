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
import com.prismtv.launcher.ui.components.BannerHost
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

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            vm.refreshApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
        if (savedInstanceState == null && intent?.let { isDebugIntent(it) } != true) askForRecommendationsOnce()
    }

    /** First launch: ask once for access to other apps' recommendation cards. */
    private fun askForRecommendationsOnce() {
        if (Build.VERSION.SDK_INT < 23 || vm.hasRecommendationAccess()) return
        val sp = getSharedPreferences("prism_prefs", MODE_PRIVATE)
        if (sp.getBoolean("askedTvListings", false)) return
        sp.edit().putBoolean("askedTvListings", true).apply()
        requestRecommendationAccess()
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
    }

    private companion object {
        const val REQ_TV_LISTINGS = 41
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

    override fun onDestroy() {
        runCatching { unregisterReceiver(packageReceiver) }
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
    val config = LocalConfiguration.current
    val base = LocalDensity.current
    // Design canvas is 960×540dp (1080p @ xhdpi). Normalise so every TV renders the same layout.
    val scale = min(config.screenWidthDp / 960f, config.screenHeightDp / 540f).takeIf { it > 0f } ?: 1f
    val density = Density(base.density * scale, base.fontScale)

    val appearance = when (vm.prefs.appearance) {
        AppearanceSetting.Light -> Appearance.Light
        AppearanceSetting.Balanced -> Appearance.Balanced
        AppearanceSetting.Dark -> Appearance.Dark
        AppearanceSetting.Automatic -> if (vm.automaticIsDark()) Appearance.Dark else Appearance.Light
    }
    val theme = PrismTheme(
        palette = Palettes.of(appearance),
        material = vm.prefs.material,
        reduceTransparency = vm.prefs.reduceTransparency,
        focusEffect = vm.prefs.focusEffect,
        labels = vm.prefs.labels,
    )

    val bg = rememberGraphicsLayer()
    val bgBlur = rememberGraphicsLayer()
    val scene = rememberGraphicsLayer()
    val sceneBlur = rememberGraphicsLayer()
    val full = rememberGraphicsLayer()
    val fullBlur = rememberGraphicsLayer()
    val backdrop = remember { BackdropState(bg, bgBlur, scene, sceneBlur, full, fullBlur) }
    val blurPx = with(density) { blurRadiusFor(theme.liquid).toPx() }
    val art = vm.currentArt
    val gridMode = vm.isGridMode

    SideEffect {
        if (SupportsLiveBlur) {
            listOf(backdrop.bgBlur, backdrop.sceneBlur, backdrop.fullBlur).forEach {
                it.renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                it.colorFilter = if (theme.liquid) LiquidSaturation else null
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
    LaunchedEffect(vm.restartTick) {
        if (vm.restartTick > 0) activity?.recreate()
    }
    LaunchedEffect(vm.permissionRequestTick) {
        if (vm.permissionRequestTick > 0) (activity as? MainActivity)?.requestRecommendationAccess()
    }
    // Rename Folder: a small system dialog so the TV's own keyboard (and voice typing) can be used.
    val rename = vm.renameRequest
    LaunchedEffect(rename) {
        val folder = rename ?: return@LaunchedEffect
        val act = activity ?: return@LaunchedEffect
        val input = android.widget.EditText(act).apply {
            setText(folder.label)
            setSelection(text.length)
            setSingleLine()
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        val pad = (24 * act.resources.displayMetrics.density).toInt()
        val frame = android.widget.FrameLayout(act).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        val dialog = android.app.AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Rename Folder")
            .setView(frame)
            .setPositiveButton("Done") { _, _ -> vm.renameFolder(folder.id, input.text.toString()) }
            .setNegativeButton("Cancel") { _, _ -> vm.renameRequest = null }
            .setOnCancelListener { vm.renameRequest = null }
            .create()
        input.setOnEditorActionListener { _, _, _ ->
            vm.renameFolder(folder.id, input.text.toString())
            dialog.dismiss()
            true
        }
        dialog.show()
        input.requestFocus()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    CompositionLocalProvider(
        LocalDensity provides density,
        LocalPrism provides theme,
        LocalBackdrop provides backdrop,
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
                SettingsScreen(vm)
            }
            ContextMenuHost(vm)
            DialogHost(vm.dialog, vm.dialogFocus)
            LaunchOverlay(vm)
            BannerHost(vm.banner)
        }
    }

    LaunchedEffect(vm.resumeTick) {
        runCatching { focusRequester.requestFocus() }
    }
    BackHandler { vm.onBack() }
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
            AndroidKeyEvent.KEYCODE_APP_SWITCH -> {
                if (native.action == AndroidKeyEvent.ACTION_DOWN && native.repeatCount == 0) vm.openSwitcher()
                return true
            }
            else -> return false
        }
        if (native.action == AndroidKeyEvent.ACTION_DOWN) vm.onKey(key)
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
