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

RELEASE_APK="${3:-}"
if [ -n "$RELEASE_APK" ] && [ -f "$RELEASE_APK" ]; then
  # Smoke-test the optimised (R8) build as a real user would see it: no demo content.
  echo "smoke testing release build"
  adb install -r "$RELEASE_APK"
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

start() { adb shell am start -W -n "$ACT" "$@" >/dev/null 2>&1; }
cap() { adb exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
shot() { local name="$1"; shift; start "$@"; sleep "${WAIT:-4}"; cap "$name"; }
key() { adb shell input keyevent "$1"; sleep "${2:-0.9}"; }

B=(--ez demo true --ez autorotate false --es clock "9:41")
DARK=(--es appearance dark --es glass liquid)

# Warm up (first launch decodes artwork).
start "${B[@]}" "${DARK[@]}" --es screen home --ei col 0; sleep 6

shot 01-home-dock-liquid-dark      "${B[@]}" "${DARK[@]}" --es screen home --ei col 0
shot 02-home-dock-music            "${B[@]}" "${DARK[@]}" --es screen home --ei col 2
shot 03-top-shelf-fullscreen       "${B[@]}" "${DARK[@]}" --es screen hero --ei col 1
shot 04-grid-focus-balanced        "${B[@]}" --es appearance balanced --es glass liquid --es screen grid --ei row 1 --ei col 1
shot 05-grid-folder-focus          "${B[@]}" "${DARK[@]}" --es screen grid --ei row 1 --ei col 3
shot 06-folder-open                "${B[@]}" "${DARK[@]}" --es screen folder --ei row 1 --ei col 3 --ei focus 0
shot 07-control-centre-grid        "${B[@]}" "${DARK[@]}" --es screen controlcentre --ei row 1 --ei col 2 --ei focus 3
shot 08-control-centre-hero        "${B[@]}" "${DARK[@]}" --es screen controlcentre --ei col 3 --ei focus 8
shot 09-app-switcher               "${B[@]}" "${DARK[@]}" --es screen switcher
shot 10-app-switcher-swipe-up      "${B[@]}" "${DARK[@]}" --es screen switcher --ez lift true
shot 11-settings-dark              "${B[@]}" "${DARK[@]}" --es screen settings --ei focus 0
shot 12-settings-appearance-bal    "${B[@]}" --es appearance balanced --es glass liquid --es screen settings --es page Appearance --ei focus 1
shot 13-settings-dialog-light      "${B[@]}" --es appearance light --es glass liquid --es screen dialog
shot 14-home-frosted-light         "${B[@]}" --es appearance light --es glass frosted --es screen home --ei col 1
shot 15-grid-frosted-light         "${B[@]}" --es appearance light --es glass frosted --es screen grid --ei row 2 --ei col 2
shot 16-control-centre-light       "${B[@]}" --es appearance light --es glass liquid --es screen controlcentre --ei row 1 --ei col 1 --ei focus 4

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
record motion-launch 6
key KEYCODE_DPAD_CENTER 4
finish motion-launch

# Motion: Control Centre.
start "${B[@]}" "${DARK[@]}" --es screen grid --ei row 1 --ei col 2; sleep 4
record motion-control-centre 8
key KEYCODE_MENU 1.6; key KEYCODE_DPAD_RIGHT 0.9; key KEYCODE_DPAD_DOWN 0.9; key KEYCODE_DPAD_DOWN 0.9; key KEYCODE_DPAD_LEFT 1; key KEYCODE_BACK 1.5
finish motion-control-centre

adb logcat -d -t 3000 > "$OUT/logcat.txt" 2>&1 || true
grep -E "FATAL|AndroidRuntime|prismtv" "$OUT/logcat.txt" | tail -60 > "$OUT/logcat-app.txt" || true
ls -la "$OUT"
exit 0
