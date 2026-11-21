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
- **Glass dock**: a Liquid Glass tray that bends the live hero behind it like a lens — the
  content stays sharp, the bevelled rim refracts what's just outside it with a specular edge.
  5:3 tiles, 24dp gutters.
- **Focus effects**: Grow, Grow & Shrink, or Lift & Shine — with spring motion and an
  app-coloured glow under the focused tile.
- **Folders** with mini-icon tiles; opening one frosts the Home Screen behind a large title and a
  glass panel, like tvOS 27.
- **Hold OK** on any app for an iOS-style context menu (icons on the left, destructive actions in
  red): **Delete App**, **Move** (the tile wiggles; arrows rearrange it, OK when done),
  **Add to Folder** / New Folder, **App Info**, and **Hide from Home Screen** (unhide in
  Settings › Apps). Hold OK on a folder for **Rename Folder**, Move and Remove Folder. The layout
  is saved.
- **Sidebar capsule**: press **Left** on the first app, or **Back** on the Home Screen, for a glass
  capsule with Search, Home, Recent Apps, Prism Settings and Android Settings.
- **Time pill** (top-right) with the current weather and profile avatar — opens Control Centre.
  Weather comes from Open-Meteo, located from your internet connection; turn it off or change
  °C/°F in Settings › Home Screen.

**Appearance**
- **Light / Balanced / Dark / Automatic** modes using the tvOS design-sheet tokens.
- **Liquid Glass (Tinted)**, **Liquid Glass (Clear)** or **Frosted Glass (Classic)** materials;
  Reduce Transparency option. Settings always uses frosted glass, like tvOS.
- **Navigation sounds** from the Prism TV Originals pack (focus, select, back, toggles,
  notifications); turn them off in Settings › Navigation Sounds.

**System UI**
- **Control Centre** (Menu, or the time pill): floating glass controls built for Android TV —
  Power, Wi-Fi/Ethernet, Remotes & More (Bluetooth), Sleep Timer, **Show Screensaver**, Picture,
  Sound, **Free Up Memory** and Accessibility, with the time and weather in the header. The
  avatar opens Accounts.
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
| Left on the first app | Sidebar capsule (Search, Recent Apps, Screensaver, Prism Settings…) |
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

### Software Update

Prism TV updates itself from the `preview` release. On the Home Screen it checks every few hours
(Settings › About › Check Automatically) and asks **Update Now / Later**; Settings › About ›
Software Update checks on demand. The first update asks you to allow Prism TV to install apps.
On Android 12+ the update then installs straight away; Android 11 (e.g. SHIELD) also shows the
system's install confirmation.

Updates only install over a build signed with the same key. CI signs with a permanent key from
the repository secrets `PRISM_KEYSTORE_B64` (base64 PKCS12 keystore, alias `prism`) and
`PRISM_KEYSTORE_PASSWORD`, and publishes `update.json` only for those builds. Without the secrets,
builds use a throwaway key and must be uninstalled before installing a newer one.

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

Navigation sounds: Prism TV Originals 1.6.0, procedurally synthesised (no recordings) and licensed
GPL-2.0-or-later — the generator, licence and cue mapping are in
[`tools/ui-sounds`](tools/ui-sounds). The pack's startup sound isn't used.
tvOS and Apple TV are trademarks of Apple Inc.
