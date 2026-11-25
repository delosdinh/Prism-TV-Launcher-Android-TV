package com.prismtv.launcher.system

import android.content.Context
import android.view.KeyEvent

/** What Prism TV knows about the screen right now (set by MainActivity and Power & Home Button). */
object Foreground {
    /** Prism TV's own screen is showing. */
    @Volatile var prismResumed = false

    /** The last app whose window came to the front (from Power & Home Button), if known. */
    @Volatile var lastPackage: String? = null

    /** Prism TV's screensaver is showing (its own or as the system's). Not Screen Time. */
    @Volatile var screensaver = false

    /**
     * While Prism TV's own screen is showing: hands it a Menu press. Some TVs take Menu before it reaches
     * any app (the SHIELD's "Customize menu button" handling in its window manager), so Power & Home
     * Button, which sees the remote's buttons first, gives it to Prism TV directly.
     */
    @Volatile var menuSink: (() -> Unit)? = null

    private var lastMenuDown = Long.MIN_VALUE

    /**
     * True the first time a given Menu press (by its down time) is seen. Power & Home Button and Prism
     * TV's own screen can both be handed the same press: when the main thread is busy, Android stops
     * waiting for the accessibility service and delivers the key to the screen as well (AM9: 2.3 s
     * later), and the second Menu closed the Control Centre the first had just opened.
     */
    @Synchronized fun claimMenu(downTime: Long): Boolean {
        if (downTime == lastMenuDown) return false
        lastMenuDown = downTime
        return true
    }
}

/**
 * Settings › Remote Buttons: a remote's app buttons (and colour, Guide, Bookmark… buttons) can open any
 * app or do a Prism TV action instead, and each can do something else when held. Home can be given a
 * hold action too (like holding TV on an Apple TV remote for Control Centre). Works everywhere, through
 * Power & Home Button, which sees the remote's buttons before apps do.
 */
object RemoteButtons {
    const val PrefKey = "remoteButtons"
    const val HomeHoldKey = "homeHold"
    const val HoldMs = 550L

    /** One changed button: matched by key code (or scan code when the remote sends no key code). */
    data class Mapping(val code: Int, val scan: Int, val press: String, val hold: String) {
        val id: String get() = if (code != KeyEvent.KEYCODE_UNKNOWN) "k$code" else "s$scan"
        fun matches(e: KeyEvent): Boolean =
            if (code != KeyEvent.KEYCODE_UNKNOWN) e.keyCode == code else e.scanCode == scan
    }

    /** The actions a button can do, in the order they're offered. */
    enum class Action(val id: String, val label: String) {
        Default("default", "Usual Action"),
        Home("home", "Home"),
        Switcher("switcher", "App Switcher"),
        ControlCentre("cc", "Control Centre"),
        Search("search", "Search"),
        Previous("previous", "Previous App"),
        Screensaver("screensaver", "Screensaver"),
        PlayPause("playpause", "Play/Pause"),
        Sleep("sleep", "Sleep"),
    }

    fun label(action: String, appName: (String) -> String): String =
        if (action.startsWith("app:")) appName(action.removePrefix("app:"))
        else Action.entries.firstOrNull { it.id == action }?.label ?: Action.Default.label

    /** Buttons that stay as they are, so the remote can always get you around (and back out). */
    fun canChange(code: Int): Boolean = code !in setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_TV_POWER,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE,
        KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP, KeyEvent.KEYCODE_SOFT_SLEEP,
    )

    /** A name for the button, as remotes label them. */
    fun name(code: Int, scan: Int): String = when (code) {
        KeyEvent.KEYCODE_UNKNOWN -> "Button $scan"
        KeyEvent.KEYCODE_PROG_RED -> "Red Button"
        KeyEvent.KEYCODE_PROG_GREEN -> "Green Button"
        KeyEvent.KEYCODE_PROG_YELLOW -> "Yellow Button"
        KeyEvent.KEYCODE_PROG_BLUE -> "Blue Button"
        KeyEvent.KEYCODE_GUIDE -> "Guide"
        KeyEvent.KEYCODE_BOOKMARK -> "Bookmark"
        KeyEvent.KEYCODE_SETTINGS -> "Settings Button"
        KeyEvent.KEYCODE_TV_INPUT -> "Input"
        KeyEvent.KEYCODE_TV -> "TV Button"
        KeyEvent.KEYCODE_SEARCH -> "Search Button"
        KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_VOICE_ASSIST -> "Assistant Button"
        KeyEvent.KEYCODE_ALL_APPS -> "Apps Button"
        KeyEvent.KEYCODE_MENU -> "Menu Button"
        KeyEvent.KEYCODE_INFO -> "Info Button"
        KeyEvent.KEYCODE_CAPTIONS -> "Subtitles Button"
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Play/Pause"
        KeyEvent.KEYCODE_MEDIA_REWIND -> "Rewind"
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "Fast Forward"
        KeyEvent.KEYCODE_DVR -> "Recordings"
        KeyEvent.KEYCODE_CHANNEL_UP -> "Channel Up"
        KeyEvent.KEYCODE_CHANNEL_DOWN -> "Channel Down"
        else -> {
            val raw = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
            if (raw.all { it.isDigit() }) "Button $raw"
            else raw.split('_').joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
        }
    }

    fun decode(raw: String?): List<Mapping> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val f = line.split('\t')
            val code = f.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            Mapping(code, f.getOrNull(1)?.toIntOrNull() ?: 0, f.getOrNull(2).orEmpty().ifBlank { "default" }, f.getOrNull(3).orEmpty().ifBlank { "default" })
        }
    }

    fun encode(list: List<Mapping>): String = list.joinToString("\n") { "${it.code}\t${it.scan}\t${it.press}\t${it.hold}" }

    private var cachedRaw: String? = null
    private var cached: List<Mapping> = emptyList()

    /** The saved buttons (read by Power & Home Button on every press, so kept parsed). */
    fun mappings(ctx: Context): List<Mapping> {
        val raw = ctx.getSharedPreferences("prism_prefs", Context.MODE_PRIVATE).getString(PrefKey, null)
        if (raw != cachedRaw) {
            cached = decode(raw)
            cachedRaw = raw
        }
        return cached
    }

    fun homeHold(ctx: Context): String =
        ctx.getSharedPreferences("prism_prefs", Context.MODE_PRIVATE).getString(HomeHoldKey, null) ?: "default"

    /** Settings › Remote Buttons › Add a Button: the next button pressed is handed here instead. */
    @Volatile var learning: ((code: Int, scan: Int) -> Unit)? = null
}
