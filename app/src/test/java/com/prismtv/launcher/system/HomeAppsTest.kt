package com.prismtv.launcher.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAppsTest {
    @Test
    fun setupWizardsAreRecognised() {
        assertTrue(HomeApps.isSetupWizard("com.google.android.tungsten.setupwraith", "com.google.android.tungsten.setupwraith.MainActivity", "Android TV Setup"))
        assertTrue(HomeApps.isSetupWizard("com.android.provision", "com.android.provision.DefaultActivity", null))
        assertTrue(HomeApps.isSetupWizard("com.xiaomi.mitv.setupwizard", "com.xiaomi.mitv.setupwizard.HomeActivity", null))
        assertTrue(HomeApps.isSetupWizard("com.mitv.oem.setup", ".Main", null))
        // Only the label gives it away.
        assertTrue(HomeApps.isSetupWizard("com.vendor.firstboot", "com.vendor.firstboot.Start", "Setup Wizard"))
    }

    @Test
    fun launchersAreNot() {
        assertFalse(HomeApps.isSetupWizard("com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx.home.HomeActivity", "Google TV Home"))
        assertFalse(HomeApps.isSetupWizard("com.google.android.tvlauncher", "com.google.android.tvlauncher.MainActivity", "Android TV Home"))
        assertFalse(HomeApps.isSetupWizard("com.mitv.tvhome.atv", "com.mitv.tvhome.atv.MainActivity", "PatchWall"))
        assertFalse(HomeApps.isSetupWizard("com.prismtv.launcher", "com.prismtv.launcher.MainActivity", "Prism TV"))
        // A known launcher stays a launcher whatever its label says.
        assertFalse(HomeApps.isSetupWizard("com.google.android.tvlauncher", "x.SetupActivity", "Setup"))
    }

    @Test
    fun finishCommandMakesPrismHomeOnlyIfTheWizardTurnedOff() {
        val c = HomeApps.finishWizardCommand("com.x.setupwizard", "com.prismtv.launcher/com.prismtv.launcher.MainActivity")
        assertTrue(c.contains("pm disable-user --user 0 com.x.setupwizard && cmd package set-home-activity --user 0 com.prismtv.launcher/"))
        assertTrue(c.contains("settings put secure user_setup_complete 1"))
    }
}
