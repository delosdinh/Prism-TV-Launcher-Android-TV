package com.prismtv.launcher.system

/**
 * Which of the TV's Home handlers are launchers, and which are the setup wizard.
 *
 * A setup wizard registers for Home (with a raised priority) so it opens first on a new TV, and is meant
 * to switch that off when setup finishes. Some TVs (a Xiaomi seen in Nov 2026) leave it on. Android then
 * answers "Setup Wizard" when asked for the Home app, whatever the person chose, because a handler with a
 * higher priority wins before the person's choice is looked at. Settings › Home App must not offer the
 * wizard as "the TV's own launcher" (switching back to it runs setup again) and can offer to finish it.
 */
object HomeApps {
    /** The TV's own launchers, best first. */
    val knownLaunchers = listOf(
        "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.leanbacklauncher",
        "com.amazon.tv.launcher", "com.android.tv.launcher",
        // Xiaomi PatchWall.
        "com.mitv.tvhome.atv", "com.mitv.tvhome",
    )

    private val wizardPackages = setOf(
        "com.google.android.tungsten.setupwraith", "com.google.android.setupwizard", "com.android.provision",
        "com.google.android.apps.tv.setup",
    )
    private val wizardWords = listOf("setupwizard", "setupwraith", "setup_wizard", "provision", "oobe", "tvsetup")
    private val wizardLabel = Regex("""\bset\s*-?\s*up\b|wizard|oobe|provision""", RegexOption.IGNORE_CASE)

    /** A setup wizard (or the provisioning screen), by its package, activity and label. */
    fun isSetupWizard(pkg: String, cls: String?, label: String?): Boolean {
        if (pkg in wizardPackages) return true
        if (pkg in knownLaunchers) return false
        val p = pkg.lowercase()
        if (wizardWords.any { p.contains(it) } || p.split('.').any { it == "setup" }) return true
        val c = cls.orEmpty().substringAfterLast('.').lowercase()
        if (c.contains("setupwizard") || c.contains("wraith")) return true
        return label != null && wizardLabel.containsMatchIn(label)
    }

    /**
     * The command that does what a finished setup wizard does itself: marks setup done, turns the wizard
     * [wizardPackage] off (Android's shell can't turn off one screen of an app, only the whole app;
     * `pm enable` [wizardPackage] undoes it, and a factory reset brings it back) and makes [prismMain] Home.
     */
    fun finishWizardCommand(wizardPackage: String, prismMain: String): String =
        "settings put global device_provisioned 1; settings put secure user_setup_complete 1; " +
            "settings put secure tv_user_setup_complete 1; pm disable-user --user 0 $wizardPackage && " +
            "cmd package set-home-activity --user 0 $prismMain"
}
