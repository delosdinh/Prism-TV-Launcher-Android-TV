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
- **Time pill** (top-right) with the current weather, a **notification bell with a badge** (only
  when something's waiting) and the profile avatar — opens Control Centre, which lists the
  notifications (OK opens one, hold OK clears it). Needs Notification access for Prism TV
  (Settings › Home Screen › Notifications, or
  `adb shell cmd notification allow_listener com.prismtv.launcher/com.prismtv.launcher.system.PrismNotificationListener`).
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
- **Dictation**: the mic key (or the remote's Search / mic button while the keyboard is up) listens
  through the remote's microphone once Dictation Microphone is allowed, or opens Android's voice
  input screen.
- Light, Balanced or Dark — **Keyboard Colour** follows Colour Mode or is set by hand. Prism uses
  the same keyboard for its own text entry (Rename Folder, Profile Name).
- Turn it on: Settings › Keyboard › Prism Keyboard, or from a computer / ATV Connect's Shell:
  `ime enable com.prismtv.launcher/.ime.PrismKeyboardService` then
  `ime set com.prismtv.launcher/.ime.PrismKeyboardService` (`ime reset` goes back).
- Suggestions come from 56,000 English words ([wordfreq](https://github.com/rspeer/wordfreq) data,
  CC BY-SA 4.0; see [`tools/keyboard`](tools/keyboard)).

**Profile** (Settings › Profile, or the avatar in Control Centre)
- Your name and picture: one of twelve Prism avatars or one of your own pictures.
- **Navigation sounds** from the Prism TV Originals pack (focus, select, back, toggles,
  notifications); turn them off in Settings › Navigation Sounds.

**System UI**
- **Control Centre** (Menu, or the time pill): floating glass controls built for Android TV —
  Power, Wi-Fi/Ethernet, Remotes & More (Bluetooth), Sleep Timer, **Sound** (shows where audio is
  going), Picture, **Free Up Memory**, Accessibility and **Show Screensaver**, with the time and
  weather in the header, and your latest notifications. The avatar opens your Profile.
- **Aerial screensaver**: Apple TV aerial videos streamed from Apple's own tvOS manifest (the
  same source as the Kodi Aerial add-on), 4K on 4K TVs with HEVC, with a dip to black between
  shots and the location shown at the start of each. Falls back to slowly drifting Prism scenes
  offline. Available on demand and as the system screensaver ("Prism Aerials").
- **App Switcher**: cascading card deck with Home as the last card; the Home Screen shrinks into
  its card when the switcher opens. Press up to fling an app away.
- **Launch transitions** (matched to tvOS): the tile's artwork grows out of its slot to fill the
  screen while the Home Screen blurs behind it, and the app fades in over the top — no black
  frame. Coming back shrinks the artwork into its icon.
- **Settings** in the tvOS Settings style: each tab has its own glyph, the large artwork on the
  left follows focus, and the Apps page shows each app's artwork; pop-up dialogs and banners.

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
| Back | Close the current layer |

## Optional permissions

- **Recommendations from Apps** (asked once on first launch, or Settings › Home Screen) — lets the
  Top Shelf show the cards apps publish to the Android TV home screen (Continue Watching, channels).
  Without it, the hero uses app artwork. Can also be granted with
  `adb shell pm grant com.prismtv.launcher android.permission.READ_TV_LISTINGS`.
- **Usage access** (Settings › Apps › Recent Apps Access) — lets the App Switcher list apps you
  opened outside the launcher.
- **Dictation Microphone** (Settings › Keyboard) — lets Prism Keyboard listen through the remote.
- **Accessibility › Prism TV Power & Home Button** — makes the remote's Home button open Prism TV
  (including Google TV boxes and Xiaomi remotes, which send `KEYCODE_ALL_APPS` for Home), and lets
  Control Centre open the power menu and the Sleep Timer put the TV to sleep. It never reads screen
  content.

### HDR aerials

Settings › Screensaver › Dynamic Range: **Automatic** (Dolby Vision when the box has a Dolby Vision
decoder and the TV reports Dolby Vision, otherwise SDR), **SDR**, or **Dolby Vision**. Apple
publishes its HDR aerials in Dolby Vision (profile 5) only, so HDR10-only TVs stay on SDR. Turn on
the box's "match content dynamic range" option so the TV switches into Dolby Vision for them.

### Prism Aerials as the system screensaver

Settings › System › Ambient mode / Screen saver › choose **Prism Aerials**. On Google TV, where the
picker is hidden:
`adb shell settings put secure screensaver_components com.prismtv.launcher/.aerial.AerialDreamService`

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

### Make it the Home app

```bash
adb connect <box-ip>
adb shell cmd package set-home-activity --user 0 com.prismtv.launcher/.MainActivity
# Accessibility service for the Home button (append if you already use other services):
adb shell settings put secure enabled_accessibility_services com.prismtv.launcher/.system.PrismAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

On Android TV (e.g. NVIDIA SHIELD) you can instead disable the stock launcher:
`adb shell pm disable-user --user 0 com.google.android.tvlauncher` (undo with `pm enable`).

Tools such as TV Box Tools do the same `set-home-activity` step for you. Uninstalling Prism TV
resets the Home app, so run it again after a reinstall; in-place updates keep it.

Without a computer (Google TV): turn on Prism Settings › **Power & Home Button** (the
accessibility service). The Home button then opens Prism TV, and **Open Prism TV at Startup**
brings it up once after the TV restarts.

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
