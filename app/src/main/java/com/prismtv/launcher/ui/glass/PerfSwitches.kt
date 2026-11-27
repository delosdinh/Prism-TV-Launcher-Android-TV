package com.prismtv.launcher.ui.glass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Drawing shortcuts for overlays' opening and closing animations on slow TVs. Each one changes only how
 * frames are produced while something moves, never how anything looks at rest, so they can be timed against
 * each other on a TV and compared by eye there:
 *
 *     adb shell dumpsys activity com.prismtv.launcher/.MainActivity perf               (lists them)
 *     adb shell dumpsys activity com.prismtv.launcher/.MainActivity perf <name> on|off
 *
 * Not saved: restarting Prism TV puts back the defaults.
 */
object PerfSwitches {
    /**
     * The Home Screen stays cached as one layer under an overlay until the overlay has finished closing,
     * not only while it's open. Closing used to drop the cache on its first frame, so every frame of the
     * closing animation drew the whole Home Screen again (the cache is what took Control Centre from 300 to
     * 15 ms frames on the AM9). Looks the same as while the overlay is open.
     */
    var cacheUntilClosed by mutableStateOf(true)

    /**
     * App Switcher: while the Home card grows or shrinks, it draws the Home Screen's offscreen copy (rendered
     * once, at full size) scaled to the card, instead of replaying everything on the Home Screen at each new
     * size, which redraws every line of text at that size. At rest the card draws Home live, as before.
     */
    var switcherHomeCopy by mutableStateOf(false)

    /**
     * Control Centre: while the cluster zooms in or out, its Liquid Glass tiles keep the backdrop they show at
     * rest instead of following each frame's position. Every tile's lens (a separate layer with its own
     * shader) was re-rendered on each frame of the zoom.
     */
    var ccSteadyLens by mutableStateOf(false)

    /**
     * Control Centre: while it's open or opening or closing, the Home Screen's time pill is drawn just above
     * the Home Screen's layer instead of in it, in the same place. The pill fades as Control Centre opens and
     * closes; in the layer, each frame of that fade re-rendered the whole Home Screen layer, its blurred copy
     * and every Control Centre lens that looks through it. Only the lenses' view of the fading pill differs.
     */
    var ccPillAbove by mutableStateOf(false)

    private val all: Map<String, Pair<() -> Boolean, (Boolean) -> Unit>> = mapOf(
        "cacheUntilClosed" to ({ cacheUntilClosed } to { v: Boolean -> cacheUntilClosed = v }),
        "switcherHomeCopy" to ({ switcherHomeCopy } to { v: Boolean -> switcherHomeCopy = v }),
        "ccSteadyLens" to ({ ccSteadyLens } to { v: Boolean -> ccSteadyLens = v }),
        "ccPillAbove" to ({ ccPillAbove } to { v: Boolean -> ccPillAbove = v }),
    )

    fun report(): String = all.entries.joinToString(", ") { (k, v) -> "$k=${if (v.first()) "on" else "off"}" }

    /** `perf <name> on|off` from dumpsys; call on the main thread. Returns what happened, for the dump. */
    fun apply(args: List<String>): String {
        val i = args.indexOf("perf")
        val name = args.getOrNull(i + 1) ?: return report()
        val entry = all.entries.firstOrNull { it.key.equals(name, ignoreCase = true) } ?: return "unknown: $name (${all.keys.joinToString()})"
        val on = when (args.getOrNull(i + 2)?.lowercase()) {
            "on", "1", "true" -> true
            "off", "0", "false" -> false
            else -> return "${entry.key}=${if (entry.value.first()) "on" else "off"}"
        }
        entry.value.second(on)
        return "${entry.key}=${if (on) "on" else "off"}"
    }
}
