package com.prismtv.launcher.ui.settings

import com.prismtv.launcher.SettingsPage

/**
 * The short explanation under the artwork in Settings, for the focused row (as on tvOS). Rows made
 * per app, album, profile and so on share one line by their id's prefix.
 */
fun settingsHelp(page: SettingsPage, id: String?): String? {
    if (id == null) return null
    Exact[id]?.let { return it }
    Prefixes.firstOrNull { (prefix, _) -> id.startsWith(prefix) }?.let { return it.second }
    return when (page) {
        SettingsPage.About -> "About Prism TV and this TV."
        else -> null
    }
}

private const val PicturesNeeded = "Save pictures to the TV's Pictures or Download folder to see them here."
private const val AllowPictures = "Lets Prism TV see the pictures saved on this TV."

private val Exact: Map<String, String> = mapOf(
    // Settings
    "profile" to "Who uses this TV: names, pictures, passcodes and the apps each person can see.",
    "appearance" to "Colour mode, Liquid or Frosted Glass, the glass colour, transparency and text size.",
    "home" to "Focus effects, the Top Shelf, widgets, the time pill and how apps are laid out.",
    "screensaver" to "What plays after a while without a button press: aerials, your pictures or both.",
    "keyboard" to "Prism Keyboard for every app: colours, auto-correction, predictions and dictation.",
    "sounds" to "Soft clicks and swishes as you move around, open and close things.",
    "apps" to "Choose which apps appear on the Home Screen.",
    "startupApp" to "Open an app automatically each time the TV starts, such as Kodi.",
    "iconPack" to "Use an installed icon pack's artwork for app banners.",
    "inputs" to "The TV's HDMI inputs as Home Screen tiles: show, hide or rename them.",
    "closing" to "Whether apps you swipe away in the App Switcher stop completely, and how Prism TV stops them.",
    "a11y" to "Lets Prism TV handle the remote's Home button and power menu, and close apps completely, through Android Accessibility.",
    "homeOverride" to "Pressing Home on the remote comes back to Prism TV instead of the TV's own Home.",
    "startup" to "Shows Prism TV once after the TV starts, even when it isn't the default Home app.",
    "sleep" to "Turns the TV off after a set time. Select to choose how long.",
    "update" to "Check for and install new versions of Prism TV.",
    "backup" to "Save your settings, layout and folders to a file, or bring them back.",
    "access" to "What Prism TV may see: notifications, recommendations, recent apps, pictures and the microphone.",
    "about" to "The version, this TV's details, memory use and the last problem Prism TV ran into.",
    "restart" to "Closes and reopens Prism TV. Your settings and layout stay as they are.",
    "reset" to "Puts every Prism TV setting and the Home Screen layout back to how they started.",

    // Home App
    "cl:recents" to "Clears every card from Android's own Recent apps screen (not Prism TV's). Needs Shizuku, root or the TV's debugging.",
    // Remote Buttons
    "remote" to "Change what your remote's app, colour and Guide buttons do, and what holding Home does.",
    "rb:a11y" to "Remote Buttons see your remote's buttons through Power & Home Button, before apps do. Turn it on first.",
    "rb:homeHold" to "Hold Home for a moment to open Control Centre, the App Switcher, Search or anything else, as on Apple TV.",
    "rb:add" to "Press the button you'd like to change, then choose what it does when pressed and when held.",
    "rbe:press" to "What a quick press does. Usual Action leaves the button as it was.",
    "rbe:hold" to "What holding the button for a moment does. Keeping the usual press as well needs Shizuku.",
    "rbe:remove" to "The button goes back to doing what it did before.",
    // Screen Time
    "pr:screentime" to "A daily limit and downtime for this profile. When time's up, apps close and a passcode gives more time.",
    "st:limit" to "How long apps can be used each day. Time on the Home Screen doesn't count.",
    "st:downtime" to "Hours with no apps, such as bedtime. A passcode lifts it until it next starts.",
    "st:used" to "How long apps have been on screen today in this profile.",
    "st:left" to "Time left today before apps close.",
    "st:passcode" to "With a passcode on your profile, only you can give more time or change Screen Time.",
    // Home Screen extras
    "bannersOver" to "New notifications slide in at the top right over whatever you're watching, then slide away.",
    "snapshots" to "App Switcher cards show each app as you left it. A snapshot is taken as you press Home, kept on the TV and deleted when you swipe the app away.",
    "wp:aerial" to "Apple TV aerials play silently behind the Home Screen (with Card Spotlight, or with the Top Shelf off), moving on to a new shot by themselves, so Shuffle pauses while they're on. Lite Mode shows a still.",

    "homeApp" to "Which app the TV starts to and the Home button opens. Prism TV can set this itself, and put the TV's own launcher back.",
    "ha:prism" to "Home, startup and the remote's Home button open Prism TV. If Google TV keeps its own launcher, Prism TV can hide it.",
    "ha:stock" to "The TV's own launcher as the Home app. Choose it before uninstalling Prism TV.",
    "ha:restore" to "Puts the TV's own launcher back as the Home app (and shows it again if it was hidden), so you can uninstall Prism TV safely.",

    // Closing Apps
    "cl:full" to "Swiped-away apps are force stopped, so music and downloads stop too. They stay closed until you open them again. Off just frees their memory.",
    "cl:using" to "How Prism TV closes apps right now. Shizuku and root are instant; Power & Home Button presses Force Stop in Settings for you.",
    "cl:shizuku" to "Closes apps instantly and out of sight. Select to install and set up Shizuku, step by step.",

    // Shizuku
    "sz:install" to "Downloads the latest Shizuku from its official GitHub page and installs it. Android asks you to confirm.",
    "sz:debug" to "Shizuku is started through the TV's own debugging. Turn on Developer options, then USB debugging.",
    "sz:start" to "Starts Shizuku through this TV's debugging. The first time, the TV asks to allow it: tick Always allow.",
    "sz:allow" to "Shizuku asks whether Prism TV may use it. Then apps you swipe away close instantly.",
    "sz:boot" to "Shizuku stops when the TV restarts. Prism TV starts it again quietly, once it's set up.",
    "sz:open" to "Opens the Shizuku app: its status, and the apps allowed to use it.",
    "cl:a11y" to "Prism TV opens the app's page in Settings behind a still of the screen and presses Force Stop for you. It takes a moment.",
    "cl:root" to "On a rooted box, closes apps instantly with root. Your root manager asks once.",

    // Appearance
    "ap:Light" to "Light Settings, menus and pop-ups.",
    "ap:Balanced" to "Soft lavender-grey, between Light and Dark.",
    "ap:Dark" to "Dark Settings, menus and pop-ups.",
    "ap:Automatic" to "Light during the day and Dark at night.",
    "mat:Liquid" to "Glass that bends and refracts what's behind it, like tvOS 26.",
    "mat:Frosted" to "Classic frosted glass that softly blurs what's behind it.",
    "gc:Automatic" to "The Home Screen glass follows Colour Mode.",
    "gc:Light" to "Light-tinted Home Screen glass.",
    "gc:Balanced" to "Lavender-grey Home Screen glass.",
    "gc:Dark" to "Smoked, dark Home Screen glass.",
    "gc:Clear" to "Untinted glass that lets the Home Screen show through.",
    "ui:High" to "Settings and pop-ups show the most of what's behind them.",
    "ui:Medium" to "A balance between seeing through and reading easily.",
    "ui:Low" to "Settings and pop-ups are the most solid and easiest to read.",
    "textSize" to "Makes Prism TV's text larger or smaller.",
    "reduce" to "Makes the glass more solid, for easier reading.",
    "lite" to "Trades blur, refraction and some animation for speed on slower boxes.",

    // Home Screen
    "fx:Grow" to "The focused app grows.",
    "fx:GrowShrink" to "The focused app grows and the others shrink back a little.",
    "fx:Lift" to "The focused app lifts with a moving shine, like tvOS.",
    "wobble" to "The focused app leans as you move and nudges at the edges.",
    "hero" to "The large artwork area at the top of the Home Screen.",
    "topShelf" to "Full-screen artwork behind everything, or a Card Spotlight above the dock.",
    "source" to "Top Shelf artwork from apps' recommendations, or from their own branding.",
    "logos" to "Shows a film or show's title logo instead of plain text, where there is one.",
    "autorotate" to "Moves to the next Top Shelf item every few seconds.",
    "recs" to "Lets Prism TV read the cards apps publish, for the Top Shelf and widgets.",
    "wallpaper" to "The picture (or aerial) behind the Home Screen, with Card Spotlight or with the Top Shelf off. Full Screen fills the screen with each title's artwork instead.",
    "widgets" to "Rows of cards from apps, such as Continue Watching, above or below the dock.",
    "weather" to "Shows the temperature and conditions in the time pill.",
    "tempUnit" to "Celsius or Fahrenheit.",
    "wifi" to "Shows the Wi-Fi signal in the time pill, and a crossed-out Wi-Fi when the TV is offline. Nothing shows when the TV is wired (Ethernet).",
    "notif" to "Lets Prism TV show notifications and Now Playing in Control Centre.",
    "columns" to "How many apps fit in each row.",
    "labels" to "When app names show under the apps.",
    "resetLayout" to "Puts apps back in their original order and removes folders.",
    "demo" to "Sample apps and artwork, for trying Prism TV out.",

    // Screensaver
    "ss:show" to "Starts the screensaver now.",
    "ss:after" to "How long without a button press on the Home Screen before it starts.",
    "ss:media" to "Holds the screensaver back while music or a video is playing.",
    "ss:source" to "Apple TV aerials, Prism's own scenes, your pictures, or a shuffle of them all.",
    "ss:pictures" to "Which of your pictures to show: all of them, whole albums or single pictures.",
    "ss:location" to "Shows where each aerial was filmed, or the scene's name.",
    "ss:quality" to "The aerial video resolution. Automatic picks the best your TV can play.",
    "ss:range" to "SDR, HDR10 or Dolby Vision aerials.",
    "ss:system" to "Use Prism Aerials as Android's own screensaver, so it also runs over other apps.",
    "sp:all" to "Show every picture on the TV.",
    "sp:allow" to AllowPictures,
    "sp:none" to PicturesNeeded,

    // Keyboard
    "kb:system" to "Use Prism Keyboard for typing in every app.",
    "kbColour" to "The keyboard's colour. Automatic follows Colour Mode; Clear is clear Liquid Glass.",
    "kbField" to "The colour of the text box above Prism's own keyboard. Other apps draw their own.",
    "kb:autocorrect" to "Fixes common typos as you type.",
    "kb:predictive" to "Suggests words above the keyboard as you type, and the likely next word after a space (learning the phrases you use, on this TV only).",
    "kb:mic" to "Lets the keyboard's microphone key use the remote's microphone for dictation.",

    // Privacy & Access
    "usage" to "Lets Prism TV see which apps you used recently, for the App Switcher.",
    "acc:photos" to "Lets Prism TV use your pictures for wallpapers, the screensaver, banners and profile pictures.",

    // Open at Startup
    "su:delay" to "How long to wait after the TV starts before opening the app.",
    "su:off" to "Prism TV stays on the Home Screen when the TV starts.",

    // Inputs
    "in:none" to "Only TVs with HDMI inputs have these. Streaming boxes don't.",

    // Backup & Restore
    "bk:now" to "Saves a backup to Download › Prism TV. A file manager can copy it to another TV.",
    "bk:pick" to "Restore a backup from anywhere on the TV, with Android's file picker.",
    "bk:none" to "Back up first, and your backups will be listed here.",

    // Icon Pack
    "ip:none" to "Apps use their own banners.",
    "ip:hint" to "Icon packs from the Play Store will be listed here.",

    // Banner
    "bn:default" to "The app's own banner (or its icon pack's).",
    "bn:allow" to AllowPictures,
    "bn:none" to PicturesNeeded,

    // Profiles
    "pr:add" to "Add someone with their own name, picture, passcode and restrictions.",
    "pr:wake" to "Shows Who's Watching? when the TV wakes up.",
    "pr:edit" to "Change this profile's name and picture.",
    "pr:passcode" to "A 4-digit passcode needed to switch to this profile.",
    "pr:restrict" to "Choose which apps this profile can see.",
    "pr:delete" to "Removes this profile. Your apps and Home Screen stay as they are.",
    "rs:all" to "Allow or block every app for this profile at once.",

    // Profile
    "pf:name" to "The name shown in the time pill, the sidebar and Control Centre.",
    "pf:accounts" to "The Android accounts on this TV.",
    "pf:allow" to AllowPictures,
    "pf:none" to PicturesNeeded,

    // Wallpaper
    "wp:shuffle" to "Changes the wallpaper on a timer, from every 30 seconds to daily.",
    "wp:allow" to AllowPictures,
    "wp:none" to PicturesNeeded,

    // Widgets
    "w:order" to "Change the order of the widget rows.",
    "w:none" to "Apps that publish cards will have rows here.",
    "wd:place" to "Above the dock, below it, or off.",
    "wd:all" to "Show cards from every app in this row.",
    "wo:none" to "Turn on two or more widgets to arrange them.",

    // Software Update
    "upd:check" to "Checks for a new version, or installs one that's ready.",
    "upd:auto" to "Checks for updates on its own.",
    "upd:ver" to "The version installed now.",

    // About
    "i:glass" to "How Liquid Glass is drawn here: a refraction shader on Android 13 and later, refraction bands before that.",
    "i:memory" to "How much memory Prism TV is using now.",
    "i:crash" to "Details of the last problem, to help fix it.",
)

/** Lines shared by rows made per app, album, profile, picture… (checked in order). */
private val Prefixes: List<Pair<String, String>> = listOf(
    "app:" to "Show or hide this app on the Home Screen.",
    "rb:btn:" to "What this button does when pressed, and when held.",
    "ra:app:" to "The button opens this app.",
    "ra:" to "What the button does.",
    "su:app:" to "Opens this app each time the TV starts.",
    "in:show:" to "Show or hide this input on the Home Screen.",
    "in:name:" to "Give this input your own name, like PlayStation 5.",
    "bk:file:" to "Restores this backup, replacing your current settings and layout.",
    "ip:" to "Uses this pack's artwork. Apps it doesn't cover keep their own banners.",
    "bn:photo:" to "Use this picture as the app's banner.",
    "pr:switch:" to "Switch to this profile. A profile with a passcode asks for it.",
    "rs:" to "Turn off to hide this app from this profile.",
    "sp:album:" to "Show every picture in this album.",
    "sp:photo:" to "Show this picture.",
    "pf:preset:" to "Use this avatar as your profile picture.",
    "pf:photo:" to "Use this picture as your profile picture.",
    "wp:scene:" to "Use this Prism scene behind the Home Screen.",
    "wp:photo:" to "Use this picture behind the Home Screen.",
    "wd:app:" to "Show cards from this app in this row.",
    "wo:" to "Move this row up or down.",
    "w:" to "Show this row of cards above or below the dock, or hide it.",
    "i:" to "About this TV and Prism TV.",
)
