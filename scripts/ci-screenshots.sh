#!/usr/bin/env bash
# Drives Prism TV on an Android TV emulator and captures screenshots + short recordings.
# Uses the debug-only intent extras (see LauncherViewModel.applyDebug) for deterministic states,
# and real D-pad key events for the navigation/animation captures.
set -uo pipefail
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
OUT="${2:-shots}"
PKG=com.prismtv.launcher
ACT="$PKG/.MainActivity"
mkdir -p "$OUT"

adb wait-for-device
for _ in $(seq 1 150); do
  [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break
  sleep 2
done
sleep 8
{
  echo "sdk: $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "device: $(adb shell getprop ro.product.model | tr -d '\r')"
  adb shell wm size
  adb shell wm density
} > "$OUT/device.txt" 2>&1
cat "$OUT/device.txt"
# Android 13+ emulators render the AGSL lens in software and are very slow: take the stills only.
SDK="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
QUICK=0
[ "${SDK:-0}" -ge 33 ] && QUICK=1

RELEASE_APK="${3:-}"
if [ -n "$RELEASE_APK" ] && [ -f "$RELEASE_APK" ]; then
  # Smoke-test the optimised (R8) build as a real user would see it: no demo content.
  echo "smoke testing release build"
  adb install -r -g "$RELEASE_APK"
  adb logcat -c || true
  adb shell am start -W -n "$ACT" >/dev/null 2>&1
  sleep 8
  adb exec-out screencap -p > "$OUT/00-release-real-apps.png"
  adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 2
  adb exec-out screencap -p > "$OUT/00-release-real-apps-grid.png"
  adb shell input keyevent KEYCODE_DPAD_UP; sleep 1
  adb shell input keyevent KEYCODE_DPAD_UP; sleep 2
  adb exec-out screencap -p > "$OUT/00-release-top-shelf.png"
  PID="$(adb shell pidof "$PKG" | tr -d '\r')"
  adb logcat -d -b crash > "$OUT/release-crash.txt" 2>&1 || true
  if [ -n "$PID" ] && ! grep -q "$PKG" "$OUT/release-crash.txt"; then echo "RELEASE OK (pid $PID)" | tee "$OUT/release-smoke.txt"; else echo "RELEASE FAILED" | tee "$OUT/release-smoke.txt"; fi
  adb shell am force-stop "$PKG"
fi

adb install -r -g "$APK" || { adb uninstall "$PKG"; adb install -r -g "$APK"; }
# Dismiss anything the TV setup flow left on screen.
adb shell input keyevent KEYCODE_WAKEUP || true

start() { timeout 120 adb shell am start -W -n "$ACT" "$@" >/dev/null 2>&1; }
cap() { timeout 120 adb exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
shot() { local name="$1"; shift; start "$@"; sleep "${WAIT:-4}"; cap "$name"; }
key() { adb shell input keyevent "$1"; sleep "${2:-0.9}"; }

B=(--ez demo true --ez autorotate false --es clock "9:41")
DARK=(--es appearance dark --es glass liquid)

# Warm up (first launch decodes artwork).
start "${B[@]}" "${DARK[@]}" --es screen home --ei col 0; sleep 6

shot 01-home-dock-liquid-dark      "${B[@]}" "${DARK[@]}" --es screen home --ei col 0
shot 02-home-dock-music            "${B[@]}" "${DARK[@]}" --es screen home --ei col 2
shot 03-top-shelf-fullscreen       "${B[@]}" "${DARK[@]}" --es screen hero --ei col 1
shot 03b-top-shelf-info            "${B[@]}" "${DARK[@]}" --es screen heroinfo --ei col 1
shot 04-grid-focus-balanced        "${B[@]}" --es appearance balanced --es glass liquid --es screen grid --ei row 1 --ei col 1
shot 05-grid-folder-focus          "${B[@]}" "${DARK[@]}" --es screen grid --ei row 1 --ei col 3
shot 06-folder-open                "${B[@]}" "${DARK[@]}" --es screen folder --ei row 1 --ei col 3 --ei focus 0
shot 07-control-centre-grid        "${B[@]}" "${DARK[@]}" --es screen controlcentre --ei row 1 --ei col 2 --ei focus 6
shot 08-control-centre-hero        "${B[@]}" "${DARK[@]}" --es screen controlcentre --ei col 3 --ei focus 13
if [ "$QUICK" = 0 ]; then
shot 09-app-switcher               "${B[@]}" "${DARK[@]}" --es screen switcher
shot 10-app-switcher-swipe-up      "${B[@]}" "${DARK[@]}" --es screen switcher --ez lift true
fi
shot 11-settings-dark              "${B[@]}" "${DARK[@]}" --es screen settings --ei focus 0
shot 12-settings-appearance-bal    "${B[@]}" --es appearance balanced --es glass liquid --es screen settings --es page Appearance --ei focus 1
shot 12b-settings-glass-colour     "${B[@]}" --es appearance dark --es glassColour light --es glass liquid --es screen settings --es page Appearance --ei focus 7
shot 12c-cc-dark-ui-light-glass     "${B[@]}" --es appearance dark --es glassColour light --es glass liquid --es screen controlcentre --ei focus 4
shot 13-settings-dialog-light      "${B[@]}" --es appearance light --es glass liquid --es screen dialog
shot 14-home-frosted-light         "${B[@]}" --es appearance light --es glass frosted --es screen home --ei col 1
shot 15-grid-frosted-light         "${B[@]}" --es appearance light --es glass frosted --es screen grid --ei row 2 --ei col 2
shot 16-control-centre-light       "${B[@]}" --es appearance light --es glass liquid --es screen controlcentre --ei row 1 --ei col 1 --ei focus 4
shot 20-context-menu-grid          "${B[@]}" "${DARK[@]}" --es screen menu --ei row 1 --ei col 1 --ei focus 1
shot 20b-context-menu-clear        "${B[@]}" "${DARK[@]}" --es glass clear --es screen menu --ei row 0 --ei col 2 --ei focus 2
shot 25b-home-clear-dark           "${B[@]}" "${DARK[@]}" --es glass clear --es screen home --ei col 2
shot 21-context-menu-dock          "${B[@]}" "${DARK[@]}" --es screen menu --ei row 0 --ei col 2 --ei focus 3
shot 22-move-mode                  "${B[@]}" "${DARK[@]}" --es screen move --ei row 1 --ei col 2
shot 23-sidebar                    "${B[@]}" "${DARK[@]}" --es screen sidebar --ei focus 4
shot 24-sidebar-light              "${B[@]}" --es appearance light --es glass liquid --es screen sidebar --ei focus 1
shot 25-home-liquid-light          "${B[@]}" --es appearance light --es glass liquid --es screen home --ei col 1
shot 26-settings-apps              "${B[@]}" "${DARK[@]}" --es screen settings --es page Apps --ei focus 2
shot 27-settings-screensaver       "${B[@]}" "${DARK[@]}" --es screen settings --es page Screensaver --ei focus 1
shot 27b-settings-root-screensaver "${B[@]}" "${DARK[@]}" --es screen settings --ei focus 3
shot 27c-settings-root-about-light "${B[@]}" --es appearance light --es glass liquid --es screen settings --ei focus 14
shot 27d-settings-software-update  "${B[@]}" "${DARK[@]}" --es screen settings --es page SoftwareUpdate --ei focus 0
# Spotlight Top Shelf, widget rows, keyboard, Text Size, notifications, wallpapers, Balanced menu.
shot 33-home-spotlight             "${B[@]}" "${DARK[@]}" --es topShelf spotlight --es wallpaper prism-dusk --es screen home --ei col 0
shot 33b-spotlight-expanded        "${B[@]}" "${DARK[@]}" --es topShelf spotlight --es wallpaper prism-dusk --es screen hero --ei col 1
shot 34-widgets-above-dock         "${B[@]}" "${DARK[@]}" --es widgets above --es screen widget --ei widget 0 --ei focus 1
shot 34b-widgets-below-dock        "${B[@]}" "${DARK[@]}" --es widgets below --es screen widget --ei widget 0 --ei focus 2
shot 35-keyboard-dark              "${B[@]}" "${DARK[@]}" --es screen keyboard --ei keyRow 1 --ei keyCol 4
shot 35b-keyboard-light            "${B[@]}" --es appearance light --es glass liquid --es screen keyboard --ei keyRow 3 --ei keyCol 2
shot 35c-keyboard-balanced-123     "${B[@]}" --es appearance balanced --es glass liquid --es screen keyboard --ei layer 1 --ei keyRow 0 --ei keyCol 3
shot 36-settings-text-size         "${B[@]}" "${DARK[@]}" --es screen settings --es page TextSize --ei textSize 4
shot 37-cc-notifications           "${B[@]}" "${DARK[@]}" --ei notify 4 --es screen controlcentre --ei focus 100
shot 37b-time-pill-badge           "${B[@]}" "${DARK[@]}" --ei notify 2 --es screen top --ei button 0
shot 38-settings-wallpaper         "${B[@]}" "${DARK[@]}" --es screen settings --es page Wallpaper --ei focus 1
shot 39-context-menu-balanced      "${B[@]}" --es appearance balanced --es glass liquid --es screen menu --ei row 1 --ei col 1 --ei focus 1
shot 40-grid-liquid-dark-labels    "${B[@]}" "${DARK[@]}" --es screen grid --ei row 2 --ei col 0
# Appearance (Colour Mode, Material, Material Colour, Settings & Pop-ups Transparency), Keyboard, Profile,
# widget apps and order, wallpaper shuffle, title logos, the four Control Centre circles.
shot 42-settings-appearance-ui     "${B[@]}" "${DARK[@]}" --es screen settings --es page Appearance --ei focus 11
shot 42b-settings-ui-high          "${B[@]}" "${DARK[@]}" --es ui high --es screen settings --ei focus 0
shot 42c-settings-ui-low-light     "${B[@]}" --es appearance light --es glass liquid --es ui low --es screen settings --ei focus 0
shot 42d-home-clear-frosted        "${B[@]}" --es appearance dark --es glass frosted --es glassColour clear --es screen home --ei col 1
shot 43-settings-keyboard          "${B[@]}" "${DARK[@]}" --es screen settings --es page Keyboard --ei focus 0
shot 44-keyboard-suggestions       "${B[@]}" "${DARK[@]}" --es screen keyboard --es text teh --ei keyRow -1 --ei keyCol 1
shot 44b-keyboard-emoji            "${B[@]}" --es appearance light --es glass liquid --es screen keyboard --ei layer 3 --ei keyRow 1 --ei keyCol 3
shot 45-settings-profile           "${B[@]}" "${DARK[@]}" --es avatar preset:3 --es screen settings --es page Profile --ei focus 5
shot 45b-cc-four-circles           "${B[@]}" "${DARK[@]}" --es avatar preset:3 --es screen controlcentre --ei focus 15
shot 46-settings-widget-apps       "${B[@]}" "${DARK[@]}" --es widgets both --es screen settings --es page WidgetApps --es widgetKey all:continue --ei focus 2
shot 46b-settings-widget-order     "${B[@]}" "${DARK[@]}" --es widgets both --es screen settings --es page WidgetOrder --ei focus 0
shot 47-settings-wallpaper-shuffle "${B[@]}" "${DARK[@]}" --ei shuffle 300 --es screen settings --es page Wallpaper --ei focus 0
shot 48-top-shelf-title-logo       "${B[@]}" "${DARK[@]}" --es logoId tt0944947 --es screen home --ei col 1
shot 48b-spotlight-title-logo      "${B[@]}" "${DARK[@]}" --es logoId tt0944947 --es topShelf spotlight --es wallpaper prism-dusk --es screen hero --ei col 1
# Spotlight: a real Up from the dock into the Top Shelf (the spring used to overshoot and crash Prism).
start "${B[@]}" "${DARK[@]}" --es topShelf spotlight --es wallpaper prism-dusk --es screen home --ei col 1; sleep 4
adb logcat -c || true
key KEYCODE_DPAD_UP 2.5; cap 41-spotlight-dpad-up
key KEYCODE_DPAD_DOWN 2; cap 41b-spotlight-back-to-dock
adb logcat -d -b crash > "$OUT/crash-spotlight.txt" 2>&1 || true
if grep -q "$PKG" "$OUT/crash-spotlight.txt"; then echo "SPOTLIGHT CRASHED"; else echo "spotlight ok"; fi
# Prism Keyboard as the system keyboard in an app's search field (debug-only test screen): type "teh",
# space auto-corrects it, then the emoji keyboard.
adb shell ime enable "$PKG/.ime.PrismKeyboardService" >/dev/null 2>&1 || true
adb shell ime set "$PKG/.ime.PrismKeyboardService" >/dev/null 2>&1 || true
timeout 60 adb shell am start -W -n "$PKG/.ImeTestActivity" >/dev/null 2>&1; sleep 5
cap 50-ime-keyboard
key KEYCODE_DPAD_UP 0.6; key KEYCODE_DPAD_CENTER 0.8                         # t
key KEYCODE_DPAD_LEFT 0.5; key KEYCODE_DPAD_LEFT 0.5; key KEYCODE_DPAD_CENTER 0.8   # e
key KEYCODE_DPAD_DOWN 0.5; for _ in 1 2 3 4; do key KEYCODE_DPAD_RIGHT 0.4; done; key KEYCODE_DPAD_CENTER 1.5  # h
cap 50b-ime-suggestions
key KEYCODE_DPAD_DOWN 0.5; key KEYCODE_DPAD_DOWN 0.5; key KEYCODE_DPAD_CENTER 1.5   # space: "Teh" -> "The"
cap 50c-ime-autocorrected
key KEYCODE_DPAD_LEFT 0.5; key KEYCODE_DPAD_CENTER 1.5   # emoji (left of space)
cap 50d-ime-emoji
adb shell ime reset >/dev/null 2>&1 || true
adb shell am force-stop "$PKG"
adb logcat -d -b crash > "$OUT/crash-ime.txt" 2>&1 || true
# Context menu → Add to Folder picker.
start "${B[@]}" "${DARK[@]}" --es screen menu --ei row 1 --ei col 1 --ei focus 1; sleep 3
key KEYCODE_DPAD_CENTER 2; cap 28-folder-picker
key KEYCODE_BACK 1
# Real hold-OK on a tile opens the context menu.
start "${B[@]}" "${DARK[@]}" --es screen grid --ei row 1 --ei col 2; sleep 3
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 2; cap 29-long-press-menu
key KEYCODE_BACK 1
# Aerial screensaver: Prism scenes (offline) and Apple TV aerials (network).
start "${B[@]}" "${DARK[@]}" --es aerial prism --es screen aerial; sleep 6; cap 30-aerial-prism-scenes
adb shell input keyevent KEYCODE_BACK; sleep 2
if [ "$QUICK" = 0 ]; then
  start "${B[@]}" "${DARK[@]}" --es aerial apple --es screen aerial; sleep 20; cap 31-aerial-apple
  sleep 10; cap 32-aerial-apple-later
  adb shell input keyevent KEYCODE_BACK; sleep 2
fi

if [ "$QUICK" = 1 ]; then
  timeout 60 adb logcat -d -t 3000 > "$OUT/logcat.txt" 2>&1 || true
  # The switcher last: on the software-rendered API 34 emulator it has stalled the device.
  shot 09-app-switcher "${B[@]}" "${DARK[@]}" --es screen switcher
  timeout 60 adb logcat -d -t 800 > "$OUT/logcat-switcher.txt" 2>&1 || true
  ls -la "$OUT"
  exit 0
fi

# Real D-pad navigation (no debug state): right, right, down.
start "${B[@]}" "${DARK[@]}" --es screen home --ei col 0; sleep 4
key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_RIGHT 2.5; cap 17-dpad-dock-right
key KEYCODE_DPAD_DOWN 2.5; cap 18-dpad-into-grid
key KEYCODE_MENU 2.5; cap 19-dpad-menu-control-centre
key KEYCODE_BACK 1.5

record() { # name seconds
  adb shell rm -f "/sdcard/$1.mp4" >/dev/null 2>&1
  adb shell screenrecord --time-limit "$2" --bit-rate 10000000 "/sdcard/$1.mp4" &
  REC=$!
  sleep 1.2
}
finish() { wait "$REC" 2>/dev/null; sleep 1; adb pull "/sdcard/$1.mp4" "$OUT/$1.mp4" >/dev/null 2>&1 && echo "recorded $1"; }

# Motion: dock focus + hero, scroll into grid, open a folder.
start "${B[@]}" "${DARK[@]}" --es screen home --ei col 0; sleep 4
record motion-home 14
key KEYCODE_DPAD_RIGHT 1.4; key KEYCODE_DPAD_RIGHT 1.4; key KEYCODE_DPAD_UP 1.8; key KEYCODE_DPAD_DOWN 1.4
key KEYCODE_DPAD_DOWN 1.3; key KEYCODE_DPAD_RIGHT 0.8; key KEYCODE_DPAD_CENTER 1.8; key KEYCODE_BACK 1.2
key KEYCODE_DPAD_UP 1.4
finish motion-home

# Motion: app switcher – home shrinks into its card, swipe a card away, back home.
start "${B[@]}" "${DARK[@]}" --es screen home --ei col 1; sleep 4
record motion-switcher 11
start "${B[@]}" "${DARK[@]}" --es screen switcher; sleep 2
key KEYCODE_DPAD_LEFT 1.2; key KEYCODE_DPAD_UP 1.6; key KEYCODE_DPAD_RIGHT 1.0; key KEYCODE_DPAD_RIGHT 1.0
key KEYCODE_DPAD_CENTER 2
finish motion-switcher

# Motion: launch zoom (demo app zooms to full screen and back).
start "${B[@]}" "${DARK[@]}" --es screen home --ei col 1; sleep 4
record motion-launch 7
key KEYCODE_DPAD_CENTER 5
finish motion-launch

# Motion: hold OK → context menu → Move → rearrange → done.
start "${B[@]}" "${DARK[@]}" --es screen grid --ei row 1 --ei col 1; sleep 4
record motion-move 12
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 1.6
key KEYCODE_DPAD_CENTER 1.4; key KEYCODE_DPAD_RIGHT 1.0; key KEYCODE_DPAD_RIGHT 1.0; key KEYCODE_DPAD_DOWN 1.2
key KEYCODE_DPAD_CENTER 1.5
finish motion-move

# Motion: sidebar capsule (Left from the first tile).
start "${B[@]}" "${DARK[@]}" --es screen grid --ei row 1 --ei col 0; sleep 4
record motion-sidebar 7
key KEYCODE_DPAD_LEFT 1.4; key KEYCODE_DPAD_DOWN 0.8; key KEYCODE_DPAD_DOWN 0.8; key KEYCODE_DPAD_DOWN 1; key KEYCODE_DPAD_RIGHT 1.5
finish motion-sidebar

# Motion: Control Centre.
start "${B[@]}" "${DARK[@]}" --es screen grid --ei row 1 --ei col 2; sleep 4
record motion-control-centre 8
key KEYCODE_MENU 1.6; key KEYCODE_DPAD_RIGHT 0.9; key KEYCODE_DPAD_DOWN 0.9; key KEYCODE_DPAD_DOWN 0.9; key KEYCODE_DPAD_LEFT 1; key KEYCODE_BACK 1.5
finish motion-control-centre

adb logcat -d -t 3000 > "$OUT/logcat.txt" 2>&1 || true
grep -E "FATAL|AndroidRuntime|prismtv" "$OUT/logcat.txt" | tail -60 > "$OUT/logcat-app.txt" || true
adb logcat -d -b crash > "$OUT/crash.txt" 2>&1 || true
ls -la "$OUT"
exit 0
