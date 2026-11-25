package com.prismtv.launcher

/**
 * What differs in the Google Play edition (BuildConfig.PLAY) besides features: its wording. Google Play's
 * intellectual-property rules make Apple's names (Apple TV, tvOS, Liquid Glass) a review risk, so the Play
 * edition says them its own way. The GitHub edition keeps the original words.
 */
object Edition {
    const val PrivacyPolicyUrl = "https://delosdinh.github.io/Prism-TV-Launcher-Release/privacy-policy.html"

    private val PlayWords = listOf(
        ", like tvOS 26." to ".",
        ", like tvOS." to ".",
        ", as on Apple TV." to ".",
        " (tvOS)" to "",
        "Apple TV Aerials" to "Aerials",
        "Apple TV aerials" to "aerials",
        "Liquid Glass" to "Prism Glass",
        "tvOS" to "TV",
    )

    /** [s] as the Play edition shows it. */
    fun text(s: String): String {
        if (!BuildConfig.PLAY) return s
        var t = s
        for ((from, to) in PlayWords) if (t.contains(from)) t = t.replace(from, to)
        return t
    }
}
