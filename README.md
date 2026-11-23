# Prism TV Launcher for Android TV

An Apple TV–style home screen for Android TV / Google TV boxes, modelled on tvOS 27 — the
launcher counterpart to the Prism TV skin for Kodi.

> Status: early preview (0.1). Built with Kotlin + Jetpack Compose. Requires Android 7.0+.
> Liquid Glass uses a real refraction shader on Android 13+ (e.g. Google TV boxes) and a
> multi-ring lens approximation on older Android (e.g. NVIDIA SHIELD on Android 11).

## Features

**Home Screen**
- Dynamic **Top Shelf hero**: full-bleed backdrop and title treatment for the focused dock app,
  sourced from Android TV *Watch Next* / preview-channel cards where the device allows it, with
  app-banner artwork as the fallback. Swipe up for a full-screen Top Shelf with Play / Info.
  **Top Shelf Style › Card Spotlight** shows the title as a large inset card over a wallpaper
  (Prism wallpapers, the scenes, or your own pictures from the TV's Pictures/Download folders);
  going up grows the card to full screen. Info opens the title's own page where the app supports
  it (Nuvio: `nuvio://detail/…`). **Title Logos**: cards that carry an IMDb id (Nuvio and other
  Stremio-style apps) show the title's logo artwork in place of its name, from Metahub.
- **Wallpaper Shuffle** (Settings › Home Screen › Wallpaper): every 30 seconds up to daily, through
  the Prism wallpapers and your own pictures you tick.
- **Apple TV Aerials as the wallpaper** (Settings › Home Screen › Wallpaper › Apple TV Aerials): the
  aerials play silently behind Home (with Card Spotlight, or with the Top Shelf off), in 1080p SDR so
  they're light on the TV. They pause behind apps, keep moving (blurred) behind the grid — on
  Android 11 TVs like the SHIELD too, drawn through OpenGL — and Liquid Glass bends them like any
  wallpaper. Lite Mode shows a still instead.
- **Now Playing on the Top Shelf**: when an app is playing (music, a video in the background), its
  dock tile's Top Shelf leads with a Now Playing card — artwork, title, Play/Pause and Skip.
- **Widgets** (Settings › Home Screen › Widgets): rows of cards from your apps — Continue Watching,
  Up Next, each app's recommendation channels, and Recommended for You / Continue Watching across
  all apps — placed **above** or **below** the dock. Choose which apps the all-app rows draw from,
  and **Arrange Widgets** to set their order.
- **Glass dock**: a Liquid Glass tray that bends the live hero behind it like a lens — the
  content stays sharp, the bevelled rim refracts what's just outside it with a specular edge.
  5:3 tiles, 24dp gutters.
- **Focus effects**: Grow, Grow & Shrink (tvOS: a quick grow and lift; neighbours stay put), or
  Lift & Shine — with an app-coloured glow under the focused tile. **Focus Wobble** gives the
  Apple TV feel without a touch remote: the focused tile settles with a little spring, leans toward
  the move, and nudges when you press past the end of a row. App names switch to dark ink over
  bright backdrops.
- **Folders** are glass tiles (Liquid or Frosted, following Material); opening one grows it out of
  its tile over the blurred Home Screen, like iOS, and it shrinks back when closed.
- **Hold OK** on any app for an iOS-style context menu (icons on the left, destructive actions in
  red): **Delete App**, **Move** (the tile wiggles; arrows rearrange it, OK when done),
  **Add to Folder** / New Folder, **App Info**, and **Hide from Home Screen** (unhide in
  Settings › Apps). Hold OK on a folder for **Rename Folder**, Move and Remove Folder. The layout
  is saved.
- **Sidebar capsule**: press **Left** on the first app, or **Back** on the Home Screen, for a glass
  capsule (sized to its entries) with Search, Home, Recent Apps, Prism Settings, Android Settings
  and **Play Store** (with its own icon; Amazon Appstore or Aptoide TV on boxes without Google Play).
- **Time pill** (top-right) with the current weather, **Wi-Fi** as on iOS (signal strength, crossed
  out when offline, nothing when wired; Settings › Home Screen › Time Pill › Wi-Fi), a
  **notification bell with a badge** (only
  when something's waiting) and the profile avatar — opens Control Centre, which lists the
  notifications (OK opens one; Left or Right slides it aside to show **Clear**, as on iOS; the small ✕ above the list opens into **Clear All**). Needs Notification access for Prism TV:
  Settings › Privacy & Access › **Notifications & Now Playing** opens Android's page, or **Do It
  for Me** turns it on through the TV's USB debugging (see [Shell commands](#shell-commands)).
  Weather comes from Open-Meteo, located from your internet connection; turn it off or change
  °C/°F in Settings › Home Screen.

**Appearance** (Settings › Appearance)
- **Colour Mode**: Light / Balanced (the default) / Dark / Automatic, using the tvOS design-sheet tokens. It
  colours Settings and pop-ups. **Automatic** is dark from sunset to sunrise where you are (from the
  weather), else 7 PM – 7 AM.
- **Material** for the Home Screen glass: **Liquid Glass** or **Frosted Glass**.
- **Material Colour**: Automatic (follows Colour Mode), Light, Balanced, Dark or **Clear** (no
  tint). Liquid Glass takes the control-library tints: frosty #F4F6FF, lavender #8F8FB5, smoked
  #30343D.
- **Settings & Pop-ups Transparency**: High, Medium or Low. Settings and pop-ups are always frosted
  glass; High shows the most of the Home Screen through them, Low the least.
- **Text Size**: a tvOS-style slider that scales Prism's text. Reduce Transparency option.
- **Lite Mode** (Performance): glass uses pre-blurred artwork instead of live blur and Liquid Glass
  refraction, the extra motion is off, and backdrops are kept at 720p in 16-bit colour (about a fifth
  of the memory). For slower or 2 GB boxes. Everything else works the same.
- On/off settings are **switches**; with Liquid Glass the knob turns into a clear glass lens as it
  slides, like iOS 26. Notification **banners** follow Material (Liquid Glass or Frosted).

**Prism Keyboard** (Settings › Keyboard)
- The iOS keyboard for **every app**, driven by the D-pad: letters, 123, #+=, **emoji** (with
  Frequently Used), a **suggestion bar** (the word as typed, the correction, completions) and
  **auto-correction** tuned for remotes (a neighbouring key is the most likely slip). Delete right
  after a correction puts back what you typed. Double space types a full stop.
- **Next-word predictions** after a space ("I am" → not, a, sure), from common English phrases and
  the phrases you type yourself (learnt on the TV only, included in Backup).
- **Hold a key for its accents**, as on iOS: é è ê ë on e, ü ú on u, ñ, ç, ß… in a strip above it
  (Left / Right, Select; Back closes). Holding . ? ! ' " - $ offers … ¿ ¡ curly quotes, dashes and
  currencies. A letter without accents still types its capital. Accented words are never
  auto-corrected.
- **Dictation**: the mic key (or the remote's Search / mic button while the keyboard is up) listens
  through the remote's microphone once Dictation Microphone is allowed, or opens Android's voice
  input screen.
- Light, Balanced, Dark or **Clear** — **Keyboard Colour** follows Colour Mode or is set by hand;
  **Text Field Colour** gives Prism's own text field its own colour (or matches the keyboard).
  Clear is clear Liquid Glass over the Home Screen; in other apps (which a keyboard can't see
  through to blur or bend) it's smoked see-through glass. Prism uses the same keyboard for its own
  text entry (Rename Folder, Profile Name).
- Turn it on: Settings › Keyboard › Prism Keyboard, or from a computer / ATV Connect's Shell:
  `ime enable com.prismtv.launcher/.ime.PrismKeyboardService` then
  `ime set com.prismtv.launcher/.ime.PrismKeyboardService` (`ime reset` goes back).
- Suggestions come from 56,000 English words ([wordfreq](https://github.com/rspeer/wordfreq) data,
  CC BY-SA 4.0), next-word predictions from the Google Books Ngram Corpus as published by
  [orgtre/google-books-ngram-frequency](https://github.com/orgtre/google-books-ngram-frequency)
  (CC BY 3.0); see [`tools/keyboard`](tools/keyboard).

**Profile** (Settings › Profile, or the avatar in Control Centre)
- Your name and picture: one of twelve Prism avatars or one of your own pictures.
- **Navigation sounds** from the Prism TV Originals pack (focus, select, back, toggles,
  notifications); turn them off in Settings › Navigation Sounds.

**System UI**
- **Control Centre** (Menu, or the time pill): floating glass controls built for Android TV —
  Power, Wi-Fi/Ethernet, Remotes & More (Bluetooth), Sleep Timer, **Sound** (shows where audio is
  going), Picture, **Free Up Memory**, Accessibility and **Show Screensaver**, with the time and
  weather in the header, and your latest notifications. The avatar opens your Profile. **Now
  Playing** shows what another app is playing (artwork, title, previous / play-pause / next; hold
  OK to go to the app; the remote's play/pause key works anywhere in Control Centre). It needs
  Notification access, like the notifications.
- **Aerial screensaver**: Apple TV aerial videos streamed from Apple's own tvOS manifest (the
  same source as the Kodi Aerial add-on), 4K on 4K TVs with HEVC, with a dip to black between
  shots and the location shown at the start of each. Falls back to slowly drifting Prism scenes
  offline. Other sources: **Your Pictures** (the same slow pan and zoom over your photos) and
  **Shuffle** (like tvOS 27.2: an aerial, then a few of your pictures and Prism's scenes, then the
  next aerial). **Pictures** chooses which of your photos they use: all of them, whole albums
  (Camera, Download…) or single pictures. **Wait While Media Plays** (on by default) holds the screensaver back while music
  or a video is playing. Available on demand and as the system screensaver ("Prism Aerials").
- **Inputs** (TVs with HDMI inputs): each input is a tile on the Home Screen, one press to the
  console or set-top box, like Projectivy's input shortcuts. Rename them (hold OK › Rename Input, or
  Settings › Inputs), hide them, move them or put them in folders like apps. Streaming boxes have no
  inputs, so there's nothing to show there.
- **Profiles** (Settings › Profiles): everyone gets a name, a picture and, if they like, a 4-digit
  passcode. **Who's Watching?** (tvOS style) appears when the TV starts up or wakes (each can be
  turned off) and from Control Centre's profile picture; an app set to open at start-up waits until
  someone's chosen. Switching to a profile with a passcode asks for it (the remote's number keys work).
  **Restrictions** (with All Apps on top to allow or block everything at once) hides apps from a profile (tvOS 27's per-profile app blocking): they leave the
  Home Screen, widgets, recent apps and startup, and a restricted profile needs a passcode to open
  Settings and doesn't get Android Settings in the sidebar. The Home Screen layout is shared.
- **Icon Pack** (Settings › Icon Pack): use an installed icon pack (the usual launcher format).
  TV banner packs replace banners outright; square icons sit on a tile of the app's colour.
  **Change Banner** (hold OK on an app) uses one of your own pictures as that app's banner.
- **Backup & Restore** (Settings › Backup & Restore): saves your settings, Home Screen layout,
  folders, hidden apps and the keyboard's learned words to Download › Prism TV, and restores them
  (after an uninstall, or on another TV: copy the file across, or use Choose a File… where the TV
  has Android's file picker).
- **Open at Startup**: pick an app (and an optional wait) to open each time the TV starts, such
  as Kodi. It opens once per start, only if nobody has pressed anything in the meantime.
- **App Switcher**, laid out like tvOS: the Home Screen shrinks into a centred card, recent apps
  recede in a stacked deck to its left with their icon and name above each card, and the newer card
  lies over the focused one's edge. Press up to fling an app away — and it really closes (see
  [Closing apps](#closing-apps)). **App Snapshots** (Settings › Home Screen, Android 11+, with
  Power & Home Button on): each card shows the app as you left it, from one screenshot taken as you
  press Home. Snapshots stay in Prism TV's private files, are deleted when you swipe the app away or
  turn the setting off, and protected video (which Android blanks) falls back to the app's artwork.
- **Prism Search** (Sidebar › Search, or a remote button): type on Prism Keyboard (or dictate) and
  results update above it with each letter — apps, titles from your apps' rows and Continue
  Watching, and places in Settings. Up from the keyboard's top row moves into the results.
- **Banners Over Apps** (Settings › Home Screen, opt-in): new notifications slide in at the top
  right over whatever you're watching, like tvOS, then slide away. They never take focus. Needs
  Notifications and Power & Home Button.
- **Remote Buttons** (Settings › Remote & Power): give your remote's app, colour, Guide or Bookmark
  buttons a new job — open any app, the App Switcher, Control Centre, Search, the previous app, the
  screensaver, Play/Pause or Sleep — for a press, a hold, or both. **Hold Home** can open Control
  Centre, as on Apple TV. Add a Button… learns whichever button you press. Home, Back, Select, the
  arrows, volume and power always stay as they are. (Keeping a button's usual press while giving it
  a hold action needs Shizuku.)
- **Screen Time** (Settings › Profiles › Screen Time): a daily limit and a downtime for each profile.
  Time counts while apps are on screen (not on the Home Screen or the screensaver). When time's up,
  Prism TV comes forward, closes the app and says so; **Ask for More Time** takes a passcode and adds
  15 minutes, an hour or the rest of the day. Settings are locked for a profile with limits.
- **Home layout per profile**: each profile keeps its own Home Screen arrangement and folders (a new
  profile starts from the main one's).
- **Launch transitions** (matched to tvOS): the tile's artwork grows out of its slot to fill the
  screen while the Home Screen blurs behind it, and the app fades in over the top — no black
  frame. Coming back shrinks the artwork into its icon.
- **Settings** in the tvOS Settings style: each tab has its own glyph, the large artwork on the
  left follows focus with a short description of the focused setting beneath it, and the Apps page
  shows each app's artwork; pop-up dialogs and banners.
  Grouped like tvOS: General (Profiles, Appearance, Home Screen, Screensaver, Keyboard, Sounds),
  Apps (Apps, Open at Startup, Icon Pack, Inputs on TVs, **Closing Apps**), Remote & Power, and
  System (Software Update, Backup & Restore, **Privacy & Access** with every permission in one
  place, About). Lists wrap: Up on the first row goes to the last, Down on the last to the first
  (holding the button stops at the end).

## Remote controls

| Input | Action |
| --- | --- |
| D-pad | Move focus |
| OK | Open app / folder / control |
| Hold OK | Context menu (Move, Add to Folder, App Info, Hide, Delete) |
| Left on the first app | Sidebar capsule (Search, Recent Apps, Prism Settings…) |
| Up from the dock | Full-screen Top Shelf → time pill |
| Menu (or the time pill) | Control Centre |
| Home twice | App Switcher (or the App Switch key on remotes that have one) |
| Hold Home | Whatever Settings › Remote Buttons › Hold Home is set to (Control Centre, Search…) |
| Back | Close the current layer |

Any other button (app buttons, colour buttons, Guide…) can be changed in Settings › Remote Buttons.

## Optional permissions

- **Recommendations from Apps** (asked once on first launch, or Settings › Home Screen) — lets the
  Top Shelf show the cards apps publish to the Android TV home screen (Continue Watching, channels).
  Without it, the hero uses app artwork. Where the TV doesn't ask (or stopped asking), **Do It for
  Me** grants it through the TV's shell, or type
  `adb shell pm grant com.prismtv.launcher android.permission.READ_TV_LISTINGS`.
- **Usage access** (Settings › Apps › Recent Apps Access) — lets the App Switcher list apps you
  opened outside the launcher.
- **Dictation Microphone** (Settings › Keyboard) — lets Prism Keyboard listen through the remote.
- **Accessibility › Prism TV Power & Home Button** — makes the remote's Home button open Prism TV
  (including Google TV boxes and Xiaomi remotes, which send `KEYCODE_ALL_APPS` for Home), and lets
  Control Centre open the power menu and the Sleep Timer put the TV to sleep. It also closes apps
  you swipe away (below), runs Remote Buttons, shows Banners Over Apps, and takes the App Switcher
  snapshot as you press Home (Android 11+). It reads the screen only to press Force Stop in
  Settings and to know which app is in front.

### Closing apps

Android doesn't let one app stop another, so on its own a launcher can only ask Android to free
an app's cached memory (`killBackgroundProcesses`). Apps with something running in the
background — music, a download, a cast session — carry on, and Android's own Recents still lists
them. So when you swipe an app away (or use Control Centre › Free Up Memory) Prism TV **force
stops** it, the same as Settings › Apps › the app › Force Stop, using the first of these it can:

1. **Shizuku** (instant and invisible). Settings › Apps › **Closing Apps** › **Shizuku** walks
   through it on the TV:
   1. **Install Shizuku** downloads the latest release from
      [Shizuku's GitHub](https://github.com/RikkaApps/Shizuku/releases) and opens Android's installer
      (allow Prism TV to install apps the first time).
   2. **Turn On USB Debugging** opens Developer options (or About, to turn them on).
   3. **Start Shizuku** connects to the TV's own adb (127.0.0.1:5555, like ATV Connect does) and runs
      Shizuku's starter. The TV asks to allow debugging once: tick **Always allow**. TVs whose adb
      only listens for Wireless debugging (TLS pairing) need Shizuku started once from a computer:
      `adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`.
   4. **Allow Prism TV** shows Shizuku's own prompt.

   Shizuku stops when the TV restarts; **Start Shizuku When the TV Starts** has Prism TV start it
   again quietly a few seconds after it opens.
2. **Root Access** on rooted boxes (turn it on in Closing Apps; your root manager asks once).
3. **Power & Home Button** (the accessibility service above): Prism TV opens the app's page in
   Settings behind a still of the screen, presses Force Stop and OK for you, and comes back. Apps
   you swipe away in a row are closed together once you stop swiping. The remote is held for that
   second or two (Home still works).

With Shizuku or root (Android 11+; on Android 11 only apps still running when they're closed),
closed apps also leave **Android's own app switcher** (Recent apps), and Closing Apps › **Clear
Android's Recent Apps** clears every card there at once.

**Free Up Memory** (Control Centre) goes further than closing the App Switcher's apps: every
installed app's background processes are stopped (not only those on the Home Screen), and Prism TV
lets go of the artwork it keeps for later (what's on screen stays). With Shizuku or root, every app
still running is then asked to give back what it can — the request Android itself makes when memory
runs short: hidden apps drop their caches and graphics memory, running ones trim theirs. Anything
playing or on screen, the keyboard and the Assistant carry on. It says how much was freed — under
the button while Control Centre is open, otherwise in a banner — counting what came back before
Android restarted the services it keeps running.

With none of these, swiped-away apps just have their memory freed and Prism TV says so once.
A force-stopped app stays closed — no background refreshes, notifications or Top Shelf updates —
until you open it again. **Close Apps Completely** (Closing Apps) turns it off. Prism TV never
stops itself, Settings, the keyboard in use, accessibility services, Shizuku or Android's
persistent system apps.

### HDR aerials

Settings › Screensaver › Dynamic Range: **Automatic** (Dolby Vision when the box has a Dolby Vision
decoder and the TV reports Dolby Vision, otherwise SDR), **SDR**, or **Dolby Vision**. Apple
publishes its HDR aerials in Dolby Vision (profile 5) only, so HDR10-only TVs stay on SDR. Turn on
the box's "match content dynamic range" option so the TV switches into Dolby Vision for them.

### Prism Aerials as the system screensaver

Settings › System › Ambient mode / Screen saver › choose **Prism Aerials**. On Google TV, where the
picker is hidden, Prism Settings › Screensaver › **Use as System Screensaver** › **Do It for Me** sets
it, or type `settings put secure screensaver_components com.prismtv.launcher/.aerial.AerialDreamService`
(see [Shell commands](#shell-commands)).

Or let Prism TV start them itself: Settings › Screensaver › **Start After** (Never, 2–30 minutes; 2 minutes by default) shows
the aerials when the Home Screen has been left alone that long. After 30 minutes the screensaver
stops holding the screen on, so the TV's own sleep timer still applies.

## Build

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Sideload with Downloader

Every build of the working branch is published to the `preview` release:
`github.com/delosdinh/prism-tv-launcher-android-tv/releases/download/preview/prismtv.apk`

### Shell commands

Some settings can only be changed by Android's shell. Where Prism TV can, it offers **Do It for
Me**, which runs the command through the TV's own USB debugging (Developer options › USB
debugging; the TV asks once to allow Prism TV — tick **Always allow**). To type a command
yourself:

- **In ATV Connect's Shell, TV Box Tools or another shell that's already on the TV**: type it as
  shown here, *without* `adb shell` (there's no `adb` on the TV, so `adb shell …` fails with
  "adb: inaccessible or not found").
- **From a computer**: `adb connect <tv-ip>`, then put `adb shell` in front of the command.

Notification access (time pill bell and Now Playing):

```
cmd notification allow_listener com.prismtv.launcher/com.prismtv.launcher.system.PrismNotificationListener
```

### Make it the Home app

From a computer (in a shell on the TV, leave out `adb connect` and the `adb shell` prefixes):

```bash
adb connect <box-ip>
adb shell cmd package set-home-activity --user 0 com.prismtv.launcher/.MainActivity
# Accessibility service for the Home button (append if you already use other services):
adb shell settings put secure enabled_accessibility_services com.prismtv.launcher/.system.PrismAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

On Android TV (e.g. NVIDIA SHIELD) you can instead disable the stock launcher:
`adb shell pm disable-user --user 0 com.google.android.tvlauncher` (undo with `pm enable`).

Or let Prism TV do it: Settings › **Home App** › choose **Prism TV**. It runs the same command for you
(through Shizuku, root or the TV's own network debugging — allow it once), then checks that Android
really switched. Google TV sometimes keeps its own launcher anyway; Prism TV then offers to hide it.

> **Before uninstalling Prism TV**, go to Settings › Home App › **Switch Back to** (Google TV, Android
> TV Home…) — especially if the stock launcher was hidden. Otherwise the TV may be left with no launcher
> to go Home to. From a shell: `pm enable <launcher package>` and
> `cmd package set-home-activity --user 0 <launcher package>/<its activity>`.

Tools such as TV Box Tools do the same `set-home-activity` step for you. Uninstalling Prism TV
resets the Home app, so run it again after a reinstall; in-place updates keep it.

Without a computer (Google TV): turn on Prism Settings › **Power & Home Button** (the
accessibility service). The Home button then opens Prism TV, and **Open Prism TV at Startup**
brings it up once after the TV restarts.

Settings › **Power & Home Button** › **Turn On for Me** switches the service on through Shizuku, root or
the TV's own debugging (some Google TV builds have no Accessibility screen to open: "No application can
handle this action"). It keeps any other accessibility services on.

If the Power & Home Button switch turns itself straight back off, Android 13+ is blocking
"restricted settings" for an app installed outside the Play Store. Allow them once from a shell
(ATV Connect or adb), then turn it on again:
`appops set com.prismtv.launcher ACCESS_RESTRICTED_SETTINGS allow`.

If Prism TV ever restarts on its own, Settings › About › **Last Problem** shows what happened.

### Software Update

Prism TV updates itself from the `preview` release. On the Home Screen it checks every few hours
(Settings › Software Update › Check Automatically) and asks **Update Now / Later**;
Settings › Software Update › Check for Updates checks on demand. The first update asks you to allow Prism TV to install apps.
On Android 12+ the update then installs straight away; Android 11 (e.g. SHIELD) also shows the
system's install confirmation.

Updates only install over a build signed with the same key, so every CI build is signed with one
permanent key (`scripts/signing-key.sh`). It comes from the repository secrets `PRISM_KEYSTORE_B64`
(base64 PKCS12 keystore, alias `prism`) and `PRISM_KEYSTORE_PASSWORD` if they're set; otherwise
from the private draft release `prism-signing-key`, which the first push build creates. Draft
releases are only visible to people with write access to the repo. **Never publish or delete that
draft**: publishing makes the key public, and deleting it means a new key and a reinstall on every
TV. `update.json` is only published for builds that carry the permanent key; anything else (e.g.
pull requests from forks) uses a throwaway key and has to be uninstalled before a newer build.

### Demo content

Settings › Home Screen › Demo Content fills the grid with original placeholder apps and artwork
(no third-party logos), useful on emulators. Artwork is generated by `tools/generate_artwork.py`.

### CI

`.github/workflows/android.yml` builds debug and release APKs (downloadable from the workflow
run), then boots Android TV emulators (API 34, 31 and 30), drives the launcher with real D-pad
events and publishes screenshots and screen recordings to the `ci-results` branch under
`screenshots/<branch>/api<level>/`.

Debug builds accept intent extras for deterministic screenshots, e.g.

```bash
adb shell am start -n com.prismtv.launcher/.MainActivity \
  --ez demo true --es appearance balanced --es glass liquid --es screen controlcentre
```

## Credits

Typeface: [Inter](https://rsms.me/inter/) (SIL Open Font License). Not affiliated with Apple.

Navigation sounds: Prism TV Originals 1.6.2, procedurally synthesised (no recordings) and licensed
GPL-2.0-or-later — the generator, licence and cue mapping are in
[`tools/ui-sounds`](tools/ui-sounds). The pack's startup sound isn't used.
tvOS and Apple TV are trademarks of Apple Inc.
