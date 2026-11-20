package com.prismtv.launcher.data

import android.content.Context

enum class AppearanceSetting(val label: String) {
    Light("Light"), Balanced("Balanced"), Dark("Dark"), Automatic("Automatic")
}

enum class GlassMaterial(val label: String) {
    Liquid("Liquid Glass"), Frosted("Frosted Glass")
}

enum class FocusEffect(val label: String) {
    Grow("Grow"), GrowShrink("Grow & Shrink"), Lift("Lift & Shine")
}

enum class LabelMode(val label: String) {
    Focused("Focused Only"), Always("Always"), Never("Never")
}

enum class HeroSource(val label: String) {
    Recommendations("Recommendations"), AppArtwork("App Artwork")
}

data class LauncherPrefs(
    val appearance: AppearanceSetting = AppearanceSetting.Dark,
    val material: GlassMaterial = GlassMaterial.Liquid,
    val focusEffect: FocusEffect = FocusEffect.GrowShrink,
    val labels: LabelMode = LabelMode.Focused,
    val reduceTransparency: Boolean = false,
    val heroEnabled: Boolean = true,
    val heroAutoRotate: Boolean = true,
    val heroSource: HeroSource = HeroSource.Recommendations,
    val columns: Int = 6,
    val demoContent: Boolean = false,
    val hiddenApps: Set<String> = emptySet(),
    /** With the accessibility service on, the remote's Home button opens Prism TV. */
    val homeOverride: Boolean = true,
)

class PrefsStore(context: Context) {
    private val sp = context.getSharedPreferences("prism_prefs", Context.MODE_PRIVATE)

    fun load(): LauncherPrefs {
        val d = LauncherPrefs()
        return LauncherPrefs(
            appearance = enumOr(sp.getString("appearance", null), d.appearance),
            material = enumOr(sp.getString("material", null), d.material),
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
            .apply()
    }

    fun clear() {
        sp.edit().clear().apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
