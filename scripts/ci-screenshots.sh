#!/usr/bin/env bash
# Drives Prism TV on an Android TV emulator and captures screenshots + short recordings.
# Uses the debug-only intent extras (see LauncherViewModel.applyDebug) for deterministic states,
# and real D-pad key events for the navigation/animation captures.
set -uo pipefail
APK="${1:-app/build/outputs/apk/github/debug/app-github-debug.apk}"
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
# The Play edition's debug APK (optional): its Prism Pro page, and what a locked setting looks like.
PLAY_APK="${4:-}"
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

  # Performance of the release build, as installed from a store (baseline profile applied).
  # Emulator numbers: compare runs with each other, not with real TVs.
  PERF="$OUT/perf.txt"
  echo "apk bytes: $(stat -c %s "$RELEASE_APK")" | tee "$PERF"
  # What the APK is made of (compressed bytes in the zip), and whether its audio is stored as SoundPool needs.
  unzip -v "$RELEASE_APK" | awk '$1 ~ /^[0-9]+$/ && NF >= 8 {
      n = $NF; e = n; sub(/^.*\./, "", e)
      k = (n ~ /\.dex$/) ? "dex" : (e ~ /^(wav|flac|ogg)$/) ? "audio" : (e ~ /^(jpg|png|webp)$/) ? "images" : "other"
      b[k] += $3; if (k == "audio" && $2 != "Stored") z++ }
    END { printf "apk dex %d, images %d, audio %d (%d compressed), other %d\n", b["dex"], b["images"], b["audio"], z, b["other"] }' | tee -a "$PERF"
  adb shell cmd package compile -f -m speed-profile "$PKG" >/dev/null 2>&1
  median() { sort -n | awk '{a[NR]=$1} END {if (NR) print a[int((NR+1)/2)]; else print "n/a"}'; }
  cold=""; for _ in 1 2 3 4 5; do
    adb shell am force-stop "$PKG"; sleep 2
    t="$(adb shell am start -W -n "$ACT" 2>/dev/null | tr -d '\r' | awk -F': ' '/TotalTime/ {print $2}')"
    cold="$cold $t"; sleep 4
  done
  echo "cold start ms: $cold  (median $(echo $cold | tr ' ' '\n' | median))" | tee -a "$PERF"
  # Back to Home from another app (Prism's process stays up): what a launcher does all day. A full-screen
  # app, so Prism is really stopped (Android TV's own Settings is a side panel on 12+ that leaves it showing).
  OTHER=""
  for cand in com.google.android.youtube.tv com.google.android.videos com.android.vending com.android.gallery3d; do
    OTHER="$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER "$cand" 2>/dev/null | tr -d '\r' | tail -n 1 | grep / || true)"
    [ -n "$OTHER" ] && break
  done
  echo "other app: ${OTHER:-Android TV Settings}" | tee -a "$PERF"
  other() { if [ -n "$OTHER" ]; then adb shell am start -W -n "$OTHER" >/dev/null 2>&1; else adb shell am start -W -a android.settings.SETTINGS >/dev/null 2>&1; fi; }
  other; sleep 8 # first open: let it settle
  adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n "$ACT" >/dev/null 2>&1; sleep 3
  warm=""; for _ in 1 2 3 4 5; do
    other; sleep 3
    t="$(adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n "$ACT" 2>/dev/null | tr -d '\r' | awk -F': ' '/TotalTime/ {print $2}')"
    warm="$warm $t"; sleep 3
  done
  echo "back to home ms: $warm  (median $(echo $warm | tr ' ' '\n' | median))" | tee -a "$PERF"
  # D-pad walk: frame timing.
  sleep 3
  adb shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
  for k in RIGHT RIGHT RIGHT DOWN RIGHT RIGHT DOWN LEFT LEFT UP UP LEFT RIGHT RIGHT DOWN UP; do adb shell input keyevent "KEYCODE_DPAD_$k"; sleep 0.3; done
  sleep 1.5
  adb shell dumpsys gfxinfo "$PKG" | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile|Slow UI thread" | sed 's/^ */walk: /' | tee -a "$PERF"
  # Rapid D-pad: 40 presses as fast as adb sends them (key repeat on a held button).
  adb shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
  adb shell 'for i in 1 2 3 4 5 6 7 8 9 10; do input keyevent KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT; done'
  sleep 1.5
  adb shell dumpsys gfxinfo "$PKG" | grep -E "Total frames rendered|Janky frames|90th percentile|Slow UI thread" | sed 's/^ */rapid: /' | tee -a "$PERF"
  # Memory after the walk and ten returns to Home.
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    other; sleep 1.5
    adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n "$ACT" >/dev/null 2>&1; sleep 1.5
  done
  sleep 3
  adb shell dumpsys meminfo "$PKG" | grep -E "TOTAL PSS|TOTAL:|Java Heap|Native Heap|Graphics" | head -5 | sed 's/^ */memory: /' | tee -a "$PERF"
  # Idle CPU: clock ticks Prism uses in 60 s on Home, then 60 s behind another app.
  ticks() { adb shell cat "/proc/$(adb shell pidof "$PKG" | tr -d '\r')/stat" 2>/dev/null | awk '{print $14 + $15}'; }
  a="$(ticks)"; sleep 60; b="$(ticks)"
  echo "idle on home, 60 s: $((b - a)) ticks" | tee -a "$PERF"
  other; sleep 5
  a="$(ticks)"; sleep 60; b="$(ticks)"
  echo "behind another app, 60 s: $((b - a)) ticks" | tee -a "$PERF"
  adb shell am force-stop "$PKG"
  if [ -n "$OTHER" ]; then adb shell am force-stop "${OTHER%%/*}"; fi
fi

adb install -r -g "$APK" || { adb uninstall "$PKG"; adb install -r -g "$APK"; }
# Dismiss anything the TV setup flow left on screen.
adb shell input keyevent KEYCODE_WAKEUP || true

start() { timeout 120 adb shell am start -W -n "$ACT" "$@" >/dev/null 2>&1; }
cap() { timeout 120 adb exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
shot() { local name="$1"; shift; start "$@"; sleep "${WAIT:-4}"; cap "$name"; }
key() { adb shell input keyevent "$1"; sleep "${2:-0.9}"; }
# A key from the emulator's own keyboard device (a Linux key code). Unlike `input keyevent`, it goes
# through Android's input filter, so Power & Home Button (accessibility) sees it like a remote press.
emu_key() { # linux code, hold seconds
  adb emu event send "1:$1:1" "0:0:0" >/dev/null 2>&1
  sleep "${2:-0.08}"
  adb emu event send "1:$1:0" "0:0:0" >/dev/null 2>&1
}

B=(--ez demo true --ez autorotate false --es clock "9:41" --ez bannersOver false --es net wifi3 --es invite ok)
DARK=(--es appearance dark --es glass liquid)

# Play edition: Settings › Prism Pro during the trial and after it, a page of locked settings, and the
# banner from picking one. Its own package, uninstalled afterwards so it never answers the Home button.
if [ -n "$PLAY_APK" ] && [ -f "$PLAY_APK" ]; then
  PLAY_PKG=com.prismtv.launcher.play
  if adb install -r -g "$PLAY_APK"; then
    pshot() { local name="$1"; shift; timeout 120 adb shell am start -W -n "$PLAY_PKG/com.prismtv.launcher.MainActivity" "$@" >/dev/null 2>&1; sleep "${WAIT:-4}"; cap "$name"; }
    WAIT=6 pshot play-00-warm-up "${B[@]}" "${DARK[@]}" --es screen home --ei col 0
    WAIT=5 pshot play-01-settings-root-trial "${B[@]}" "${DARK[@]}" --es pro trial --es screen settings --ei focus 0
    WAIT=5 pshot play-02-pro-page-trial      "${B[@]}" "${DARK[@]}" --es pro trial --es screen settings --es page Pro --ei focus 0
    WAIT=5 pshot play-03-pro-page-ended      "${B[@]}" "${DARK[@]}" --es pro ended --es screen settings --es page Pro --ei focus 0
    WAIT=5 pshot play-04-locked-appearance   "${B[@]}" "${DARK[@]}" --es pro ended --es screen settings --es page Appearance --ei focus 4
    WAIT=5 pshot play-05-locked-widgets-banner "${B[@]}" "${DARK[@]}" --es pro ended --es screen settings --es proLocked Widgets
    WAIT=5 pshot play-06-pro-page-owned      "${B[@]}" "${DARK[@]}" --es pro owned --es screen settings --es page Pro --ei focus 0
    rm -f "$OUT/play-00-warm-up.png"
    adb uninstall "$PLAY_PKG" >/dev/null 2>&1 || true
  else
    echo "::warning::Play edition APK didn't install"
  fi
fi

# Warm up (first launch decodes artwork).
start "${B[@]}" "${DARK[@]}" --es screen home --ei col 0; sleep 6

# Reference set (ref-*): Settings & Pop-ups Transparency Low / Medium / High in each colour mode over the
# same Top Shelf, and Liquid Glass (Tinted, Clear) beside Frosted on each surface.
REF=(--es screen settings --ei col 2 --ei focus 1)
for mode in dark light balanced; do
  for ui in low medium high; do
    WAIT=5 shot "ref-01-settings-$mode-$ui" "${B[@]}" --es appearance "$mode" --es glass liquid --es ui "$ui" "${REF[@]}"
  done
done
for ui in low high; do
  WAIT=5 shot "ref-02-popup-dark-$ui"  "${B[@]}" --es appearance dark  --es glass liquid --es ui "$ui" --es screen heroinfo --ei col 2
  WAIT=5 shot "ref-02-popup-light-$ui" "${B[@]}" --es appearance light --es glass liquid --es ui "$ui" --es screen heroinfo --ei col 2
done
for look in "liquid-dark:--es appearance dark --es glass liquid" "liquid-light:--es appearance light --es glass liquid" \
  "liquid-balanced:--es appearance balanced --es glass liquid" "clear-dark:--es appearance dark --es glass clear" \
  "frosted-dark:--es appearance dark --es glass frosted"; do
  name="${look%%:*}"; args=(${look#*:})
  WAIT=5 shot "ref-10-home-$name"           "${B[@]}" "${args[@]}" --es screen home --ei col 2
  WAIT=5 shot "ref-11-grid-$name"           "${B[@]}" "${args[@]}" --es screen grid --ei row 1 --ei col 2
  WAIT=5 shot "ref-12-control-centre-$name" "${B[@]}" "${args[@]}" --es screen controlcentre --ei col 2 --ei focus 3 --es nowPlaying "'Golden Hour'" --ei notify 2
  WAIT=5 shot "ref-13-sidebar-$name"        "${B[@]}" "${args[@]}" --es screen sidebar --ei focus 1
  WAIT=5 shot "ref-14-context-menu-$name"   "${B[@]}" "${args[@]}" --es screen menu --ei row 0 --ei col 2 --ei focus 1
  WAIT=5 shot "ref-15-folder-$name"         "${B[@]}" "${args[@]}" --es screen folder --ei row 1 --ei col 3 --ei focus 1
  WAIT=5 shot "ref-16-banner-$name"         "${B[@]}" "${args[@]}" --es banner "'Liquid Glass reference'" --es screen home --ei col 2
done
WAIT=5 shot ref-17-keyboard-clear "${B[@]}" --es appearance dark --es glass liquid --es keyboardColour clear --es screen keyboard --es text Prism --ei keyRow 1 --ei keyCol 3
WAIT=5 shot ref-17b-keyboard-light "${B[@]}" --es appearance light --es glass liquid --es screen keyboard --es text Prism --ei keyRow 1 --ei keyCol 3

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
shot 09b-app-switcher-app          "${B[@]}" "${DARK[@]}" --es screen switcher --ei focus 3
shot 09c-app-switcher-deck-light   "${B[@]}" --es appearance light --es glass liquid --es screen switcher --ei focus 1
shot 10-app-switcher-swipe-up      "${B[@]}" "${DARK[@]}" --es screen switcher --ei focus 3 --ez lift true
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
shot 27c-settings-screensaver-clock "${B[@]}" "${DARK[@]}" --es aerial prism --es ssClock large --es screen settings --es page Screensaver --ei focus 5
shot 27c-settings-root-about-light "${B[@]}" --es appearance light --es glass liquid --es screen settings --ei focus 19
shot 27f-settings-about             "${B[@]}" "${DARK[@]}" --es screen settings --es page About --ei focus 2
shot 27g-settings-acknowledgements  "${B[@]}" "${DARK[@]}" --es screen settings --es page Acknowledgements --ei focus 1
shot 27d-settings-software-update  "${B[@]}" "${DARK[@]}" --es screen settings --es page SoftwareUpdate --ei focus 0
# Spotlight Top Shelf, widget rows, keyboard, Text Size, notifications, wallpapers, Balanced menu.
shot 33-home-spotlight             "${B[@]}" "${DARK[@]}" --es topShelf spotlight --es wallpaper prism-dusk --es screen home --ei col 0
shot 33b-spotlight-expanded        "${B[@]}" "${DARK[@]}" --es topShelf spotlight --es wallpaper prism-dusk --es screen hero --ei col 1
# The Prism wave wallpapers, each with the look it was drawn for.
shot 33c-spotlight-waves-dark      "${B[@]}" "${DARK[@]}" --es topShelf spotlight --es wallpaper prism-night --es screen home --ei col 0
shot 33d-spotlight-waves-balanced  "${B[@]}" --es appearance balanced --es glass liquid --es topShelf spotlight --es wallpaper prism-dusk --es screen home --ei col 0
shot 33e-spotlight-waves-light     "${B[@]}" --es appearance light --es glass liquid --es topShelf spotlight --es wallpaper prism-light --es screen home --ei col 0
shot 34-widgets-above-dock         "${B[@]}" "${DARK[@]}" --es widgets above --es screen widget --ei widget 0 --ei focus 1
shot 34b-widgets-below-dock        "${B[@]}" "${DARK[@]}" --es widgets below --es screen widget --ei widget 0 --ei focus 2
shot 35-keyboard-dark              "${B[@]}" "${DARK[@]}" --es screen keyboard --ei keyRow 1 --ei keyCol 4
shot 35b-keyboard-light            "${B[@]}" --es appearance light --es glass liquid --es screen keyboard --ei keyRow 3 --ei keyCol 2
shot 35c-keyboard-balanced-123     "${B[@]}" --es appearance balanced --es glass liquid --es screen keyboard --ei layer 1 --ei keyRow 0 --ei keyCol 3
shot 36-settings-text-size         "${B[@]}" "${DARK[@]}" --es screen settings --es page TextSize --ei textSize 4
shot 37-cc-notifications           "${B[@]}" "${DARK[@]}" --ei notify 4 --es screen controlcentre --ei focus 100
shot 37b-time-pill-badge           "${B[@]}" "${DARK[@]}" --ei notify 2 --es screen top --ei button 0
# The time pill's Wi-Fi: weak signal (dark), offline (light), nothing on Ethernet; and its switch in Settings.
shot 37e-time-pill-wifi-weak      "${B[@]}" "${DARK[@]}" --es net wifi1 --es screen top --ei button 0
shot 37f-time-pill-offline-light  "${B[@]}" --es appearance light --es glass liquid --es net offline --es screen home
shot 37g-time-pill-ethernet-none  "${B[@]}" "${DARK[@]}" --es net ethernet --es screen home
shot 37h-settings-wifi-row        "${B[@]}" "${DARK[@]}" --es screen settings --es page HomeScreen --ei focus 14
shot 37c-cc-notification-clear     "${B[@]}" "${DARK[@]}" --ei notify 4 --es screen controlcentre --ei focus 200
shot 37d-cc-clear-all              "${B[@]}" --es appearance light --es glass liquid --ei notify 4 --es screen controlcentre --ei focus 30
shot 38-settings-wallpaper         "${B[@]}" "${DARK[@]}" --es screen settings --es page Wallpaper --ei focus 2
shot 38b-settings-wallpaper-aerial  "${B[@]}" "${DARK[@]}" --es screen settings --es page Wallpaper --ei focus 1
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
# Hold a letter for its accents: e (dark, é focused), a at the left edge (light), " on 123 (clear keys).
shot 44c-keyboard-accents-e        "${B[@]}" "${DARK[@]}" --es screen keyboard --es text caf --ei keyRow 0 --ei keyCol 2 --ei accent 1
shot 44d-keyboard-accents-a-light  "${B[@]}" --es appearance light --es glass liquid --es screen keyboard --es text Pok --ei keyRow 1 --ei keyCol 0 --ei accent 0
shot 44e-keyboard-accents-quote-clear "${B[@]}" "${DARK[@]}" --es keyboardColour clear --es screen keyboard --ei layer 1 --ei keyRow 1 --ei keyCol 9 --ei accent 0
# Next-word predictions after a space: "I am " (a three-word phrase), "new " (York, capitalised).
shot 44g-keyboard-next-word        "${B[@]}" "${DARK[@]}" --es screen keyboard --es text "'I am '" --ei keyRow -1 --ei keyCol 0
shot 44h-keyboard-next-word-light  "${B[@]}" --es appearance light --es glass liquid --es screen keyboard --es text "'Visit new '" --ei keyRow 0 --ei keyCol 4
# ABC layout (Settings › Keyboard › Keyboard Layout): a tvOS-style grid, six across, switches on top.
shot 44i-keyboard-abc-dark         "${B[@]}" "${DARK[@]}" --es kbLayout abc --es screen keyboard --es text teh --ei keyRow 5 --ei keyCol 2
shot 44j-keyboard-abc-clear        "${B[@]}" "${DARK[@]}" --es kbLayout abc --es keyboardColour clear --es screen keyboard --es text Prism --ei keyRow 3 --ei keyCol 3
shot 44k-keyboard-abc-light        "${B[@]}" --es appearance light --es glass liquid --es kbLayout abc --es screen keyboard --ei keyRow 1 --ei keyCol 0
shot 44l-keyboard-abc-123-balanced "${B[@]}" --es appearance balanced --es glass liquid --es kbLayout abc --es screen keyboard --ei layer 1 --ei keyRow 0 --ei keyCol 1
shot 44m-keyboard-qwerty-clear-key "${B[@]}" "${DARK[@]}" --es screen keyboard --es text "'Hello world'" --ei keyRow 3 --ei keyCol 4
shot 44n-search-abc-side           "${B[@]}" "${DARK[@]}" --es kbLayout abc --es screen search --es text Pri --ei keyRow 1 --ei keyCol 5
shot 44o-search-abc-side-result    "${B[@]}" --es appearance balanced --es glass liquid --es kbLayout abc --es screen search --es text Pri --ei keyRow 1 --ei keyCol 5 --ei focus 0
# Driven: hold e, Right to é, Select — "café", and no auto-correction back to "cafe".
start "${B[@]}" "${DARK[@]}" --es screen keyboard --es text caf --ei keyRow 0 --ei keyCol 2; sleep 3
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 1.2
key KEYCODE_DPAD_RIGHT 0.6; key KEYCODE_DPAD_CENTER 1; cap 44f-keyboard-accent-typed
shot 45-settings-profile           "${B[@]}" "${DARK[@]}" --es avatar preset:3 --es screen settings --es page Profile --ei focus 5
# Profile › Google Account: the TV's accounts page on Android TV 12+, the TV's Settings before that.
start "${B[@]}" "${DARK[@]}" --es screen settings --es page Profile --ei focus 1; sleep 3
cap 45c-settings-google-account-row
key KEYCODE_DPAD_CENTER 4
echo "google account opens: $(adb shell dumpsys activity activities | grep -m1 -E 'ResumedActivity' | tr -d '\r')" | tee "$OUT/google-account-test.txt"
cap 45d-google-account-opened
shot 45b-cc-four-circles           "${B[@]}" "${DARK[@]}" --es avatar preset:3 --es screen controlcentre --ei focus 15
shot 46-settings-widget-apps       "${B[@]}" "${DARK[@]}" --es widgets both --es screen settings --es page WidgetApps --es widgetKey all:continue --ei focus 2
shot 46b-settings-widget-order     "${B[@]}" "${DARK[@]}" --es widgets both --es screen settings --es page WidgetOrder --ei focus 0
shot 47-settings-wallpaper-shuffle "${B[@]}" "${DARK[@]}" --ei shuffle 300 --es screen settings --es page Wallpaper --ei focus 0
shot 48-top-shelf-title-logo       "${B[@]}" "${DARK[@]}" --es logoId tt0944947 --es screen home --ei col 1
shot 48b-spotlight-title-logo      "${B[@]}" "${DARK[@]}" --es logoId tt0944947 --es topShelf spotlight --es wallpaper prism-dusk --es screen hero --ei col 1
# Banners follow Material; switches; the Liquid Glass keyboard field; Lite Mode.
shot 51-banner-liquid-balanced     "${B[@]}" --es appearance balanced --es glass liquid --es banner "'Liquid Glass banners and switches'" --es screen home --ei col 2
shot 51b-banner-frosted-dark       "${B[@]}" "${DARK[@]}" --es glass frosted --es banner "'Liquid Glass banners and switches'" --es screen grid --ei row 1 --ei col 2
shot 52-settings-switches          "${B[@]}" "${DARK[@]}" --es screen settings --es page HomeScreen --ei focus 3
shot 52b-settings-apps-switches    "${B[@]}" --es appearance light --es glass liquid --es screen settings --es page Apps --ei focus 1
shot 53-keyboard-field-balanced    "${B[@]}" --es appearance balanced --es glass liquid --es screen keyboard --es text Delos --ei keyRow 1 --ei keyCol 1
shot 53b-keyboard-clear            "${B[@]}" --es appearance balanced --es glass liquid --es keyboardColour clear --es screen keyboard --es text Delos --ei keyRow 1 --ei keyCol 3
shot 55-cc-now-playing             "${B[@]}" "${DARK[@]}" --es screen controlcentre --ei focus 21 --es nowPlaying "'Golden Hour'" --ei notify 3
shot 55b-cc-now-playing-light      "${B[@]}" --es appearance light --es glass liquid --es screen controlcentre --ei focus 22 --es nowPlaying "'Golden Hour'" --ez paused true
shot 56-settings-inputs            "${B[@]}" "${DARK[@]}" --ez inputs true --es screen settings --es page Inputs --ei focus 1
shot 56b-settings-startup          "${B[@]}" "${DARK[@]}" --es screen settings --es page Startup --ei focus 2
shot 57-settings-backup            "${B[@]}" "${DARK[@]}" --es screen settings --es page Backup --ei focus 0
shot 57b-settings-icon-pack        "${B[@]}" "${DARK[@]}" --es screen settings --es page IconPack --ei focus 0
shot 58-whos-watching              "${B[@]}" "${DARK[@]}" --ez demoProfiles true --es screen profiles --ei focus 1
shot 58b-passcode                  "${B[@]}" "${DARK[@]}" --ez demoProfiles true --es screen passcode --ei entered 2 --ei focus 6
shot 58c-settings-profiles         "${B[@]}" "${DARK[@]}" --ez demoProfiles true --es screen settings --es page Profiles --ei focus 3
shot 58d-settings-restrictions     "${B[@]}" "${DARK[@]}" --ez demoProfiles true --es activeProfile p-kids --es screen settings --es page Restrictions --ei focus 1
shot 58e-kids-home                 "${B[@]}" "${DARK[@]}" --ez demoProfiles true --es activeProfile p-kids --es screen sidebar --ei focus 1
shot 59-screensaver-pictures       "${B[@]}" "${DARK[@]}" --es aerial pictures --ez demoLibrary true --es screen settings --es page ScreensaverPictures --ei focus 2
shot 59b-keyboard-field-light      "${B[@]}" "${DARK[@]}" --es keyboardColour clear --es fieldColour light --es screen keyboard --es text Delos --ei keyRow 1 --ei keyCol 2
shot 59c-restrictions-all          "${B[@]}" "${DARK[@]}" --ez demoProfiles true --es activeProfile p-kids --es screen settings --es page Restrictions --ei focus 0
shot 60-settings-root-new          "${B[@]}" "${DARK[@]}" --es screen settings --ei focus 16
shot 60b-settings-access           "${B[@]}" "${DARK[@]}" --es screen settings --es page Access --ei focus 0
shot 60c-settings-home-screen      "${B[@]}" "${DARK[@]}" --es screen settings --es page HomeScreen --ei focus 6
# Closing Apps (Settings › Apps), and each Screensaver / Privacy row's own glyph.
shot 61-settings-root-closing      "${B[@]}" "${DARK[@]}" --es screen settings --ei focus 9
shot 61b-settings-closing          "${B[@]}" "${DARK[@]}" --es screen settings --es page Closing --ei focus 0
shot 61c-settings-closing-a11y     "${B[@]}" --es appearance light --es glass liquid --es screen settings --es page Closing --ei focus 3
shot 61d-settings-screensaver-row  "${B[@]}" "${DARK[@]}" --es screen settings --es page Screensaver --ei focus 4
# Home App (make Prism TV the Home app, hide or restore the stock launcher), Now Playing on the Top Shelf.
shot 65-settings-root-home-app     "${B[@]}" "${DARK[@]}" --es screen settings --ei focus 10
shot 65b-settings-home-app         "${B[@]}" "${DARK[@]}" --es screen settings --es page HomeApp --ei focus 0
shot 65c-settings-home-app-restore "${B[@]}" --es appearance light --es glass liquid --es screen settings --es page HomeApp --ei focus 2
shot 66-top-shelf-now-playing      "${B[@]}" "${DARK[@]}" --es nowPlaying "'Golden Hour'" --es screen hero --ei col 2
shot 66b-top-shelf-paused-light    "${B[@]}" --es appearance light --es glass liquid --es nowPlaying "'Golden Hour'" --ez paused true --es screen home --ei col 2
# Remote Buttons, Screen Time, Prism Search, Banners Over Apps and App Snapshots.
shot 67-settings-root-remote       "${B[@]}" "${DARK[@]}" --ez demoRemote false --es screen settings --ei focus 12
shot 67b-settings-remote-buttons   "${B[@]}" "${DARK[@]}" --ez demoRemote true --es screen settings --es page RemoteButtons --ei focus 2
shot 67c-settings-remote-button    "${B[@]}" "${DARK[@]}" --ez demoRemote true --es remoteEditing k183 --es screen settings --es page RemoteButton --ei focus 1
shot 67d-settings-remote-action    "${B[@]}" --es appearance light --es glass liquid --ez demoRemote true --es remoteEditing k183 --es remoteSlot press --es screen settings --es page RemoteAction --ei focus 3
shot 68-settings-screen-time       "${B[@]}" "${DARK[@]}" --ez demoRemote false --ez demoProfiles true --es activeProfile p-kids --ei screenLimit 60 --es downtime 21:00-07:00 --ei usedMinutes 42 --es screen settings --es page ScreenTime --ei focus 0
shot 68b-screen-time-up            "${B[@]}" "${DARK[@]}" --ez demoProfiles true --es activeProfile p-kids --ei screenLimit 60 --ei usedMinutes 60 --es screen timesup
shot 68c-downtime-light            "${B[@]}" --es appearance light --es glass liquid --ez demoProfiles true --es activeProfile p-kids --es downtime 21:00-07:00 --es screen timesup --es block downtime
shot 68d-settings-profiles-time    "${B[@]}" "${DARK[@]}" --ez demoProfiles true --es activeProfile p-kids --ei screenLimit 60 --es screen settings --es page Profiles --ei focus 9
shot 69-search-recent              "${B[@]}" "${DARK[@]}" --es screen search
shot 69b-search-typed              "${B[@]}" "${DARK[@]}" --es screen search --es text ci --ei keyRow 1 --ei keyCol 2
shot 69c-search-results-focus      "${B[@]}" "${DARK[@]}" --es screen search --es text s --ei focus 1
shot 69d-search-settings-light     "${B[@]}" --es appearance light --es glass liquid --es screen search --es text lite --ei focus 0
shot 70-settings-banners-over      "${B[@]}" "${DARK[@]}" --es screen settings --es page HomeScreen --ei focus 16
shot 70b-settings-snapshots        "${B[@]}" "${DARK[@]}" --es screen settings --es page HomeScreen --ei focus 17
# Set-up dialogs: Power & Home Button (Turn On for Me), wallpaper with a Full Screen Top Shelf.
start "${B[@]}" "${DARK[@]}" --es screen settings --ei focus 11; sleep 3
key KEYCODE_DPAD_CENTER 2; cap 75-power-home-turn-on
key KEYCODE_BACK 1.5
start "${B[@]}" "${DARK[@]}" --es topShelf fullscreen --es screen settings --es page HomeScreen --ei focus 10; sleep 3
cap 75b-settings-wallpaper-not-shown
key KEYCODE_DPAD_CENTER 2; cap 75c-wallpaper-not-shown-dialog
key KEYCODE_BACK 1.5
# Settings lists wrap: Up from the first row goes to the last, Down from the last back to the first.
start "${B[@]}" "${DARK[@]}" --es screen settings --es page Appearance --ei focus 0; sleep 4
key KEYCODE_DPAD_UP 2.5; cap 62-settings-wrap-up-to-last
key KEYCODE_DPAD_DOWN 2.5; cap 62b-settings-wrap-down-to-first
shot 54-home-lite                  "${B[@]}" "${DARK[@]}" --ez lite true --es screen grid --ei row 1 --ei col 2
shot 54b-settings-lite             "${B[@]}" "${DARK[@]}" --ez lite true --es screen settings --es page Appearance --ei focus 16
shot 54c-control-centre-lite       "${B[@]}" "${DARK[@]}" --ez lite true --es screen controlcentre --ei focus 14
# Frame timing for the same D-pad walk around Home, Lite Mode off and on (dumpsys gfxinfo).
frames() { # label, extra args
  local label="$1"; shift
  start "${B[@]}" "${DARK[@]}" "$@" --es screen home --ei col 0; sleep 5
  adb shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
  for k in RIGHT RIGHT RIGHT DOWN RIGHT RIGHT DOWN LEFT LEFT UP UP LEFT; do adb shell input keyevent "KEYCODE_DPAD_$k"; sleep 0.35; done
  sleep 1.5
  adb shell dumpsys gfxinfo "$PKG" | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile|Slow UI thread|Slow bitmap uploads|Slow issue draw" \
    | sed "s/^ */$label: /" | tee -a "$OUT/frames.txt"
}
frames full
frames lite --ez lite true
# Sitting on Home: frames drawn in 20 s with nothing moving (should be none), and with Auto-Advance on
# (a cross-fade every 9 s and nothing in between).
idleframes() { # label, extra args
  local label="$1"; shift
  start "${B[@]}" "${DARK[@]}" "$@" --es screen home --ei col 0; sleep 6
  adb shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
  adb logcat -c
  sleep 20
  local log frames; log=$(adb logcat -d -s PrismDraw:D)
  frames=$(adb shell dumpsys gfxinfo "$PKG" | grep -m1 "Total frames rendered" | grep -oE "[0-9]+$")
  echo "$label: 20 s on Home: ${frames:-?} frames, scene re-recorded $(grep -c 'PrismDraw: Scene' <<<"$log")x" | tee -a "$OUT/frames.txt"
}
idleframes idle-still
idleframes idle-advancing --ez autorotate true
# The same for Settings: down the main list (rebuilt rows and page art on every press), into Apps and down.
start "${B[@]}" "${DARK[@]}" --es screen settings --ei focus 0; sleep 4
adb shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
adb logcat -c
for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 0.3; done
for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do adb shell input keyevent KEYCODE_DPAD_UP; sleep 0.3; done
sleep 1.5
adb shell dumpsys gfxinfo "$PKG" | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile|Slow UI thread" \
  | sed "s/^ */settings: /" | tee -a "$OUT/frames.txt"
# Debug-build counts (logcat): how many Settings rows recomposed over those 24 presses.
LOG=$(adb logcat -d -s PrismComp:D)
echo "settings: 24 presses, page composed $(grep -c 'PrismComp: settings' <<<"$LOG")x, rows composed $(grep -c 'PrismComp: row' <<<"$LOG")x" | tee -a "$OUT/frames.txt"
# Opening and closing Settings and Control Centre: how often each overlay recomposes during its animation,
# with the threshold read through derivedStateOf (new) and directly (old, --ez directThresholds true).
overlayopen() { # label, extra args
  local label="$1"; shift
  start "${B[@]}" "${DARK[@]}" "$@" --es screen home --ei col 0; sleep 4
  adb logcat -c
  adb shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
  adb shell input keyevent KEYCODE_MENU; sleep 2.5
  adb shell input keyevent KEYCODE_BACK; sleep 2.5
  local log frames; log=$(adb logcat -d -s PrismComp:D PrismDraw:D)
  frames=$(adb shell dumpsys gfxinfo "$PKG" | grep -m1 "Total frames rendered" | grep -oE "[0-9]+$")
  echo "$label: Control Centre open+close: $frames frames, composed $(grep -c 'PrismComp: cc' <<<"$log")x, scene re-recorded $(grep -c 'PrismDraw: Scene' <<<"$log")x" | tee -a "$OUT/frames.txt"
}
overlayopen open-new
overlayopen open-old --ez directThresholds true --ez isolateScene false
overlayopen open-new-again
# Control Centre in Liquid Glass: open it, move around, close it. Liquid Glass draws
# through offscreen copies of the scene; timed with the copies on, off, and on again.
ccwalk() { # label, extra args
  local label="$1"; shift
  start "${B[@]}" "${DARK[@]}" "$@" --es screen home --ei col 0; sleep 5
  adb shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
  adb logcat -c
  adb shell input keyevent KEYCODE_MENU; sleep 1.2
  for k in RIGHT DOWN DOWN LEFT DOWN RIGHT DOWN DOWN UP UP LEFT UP; do adb shell input keyevent "KEYCODE_DPAD_$k"; sleep 0.35; done
  adb shell input keyevent KEYCODE_BACK; sleep 1.2
  adb shell dumpsys gfxinfo "$PKG" | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile|Slow UI thread|Slow issue draw" \
    | sed "s/^ */$label: /" | tee -a "$OUT/frames.txt"
  local log; log=$(adb logcat -d -s PrismComp:D PrismDraw:D)
  echo "$label: Control Centre composed $(grep -c 'PrismComp: cc' <<<"$log")x, scene re-recorded $(grep -c 'PrismDraw: Scene' <<<"$log")x, full $(grep -c 'PrismDraw: Full' <<<"$log")x" | tee -a "$OUT/frames.txt"
}
# New (scene in its own layer, thresholds through derivedStateOf) against the old way, then new again.
ccwalk cc-new
ccwalk cc-old --ez isolateScene false --ez directThresholds true
ccwalk cc-new-again
# Main-thread cost of building each Settings page's rows, and of preparing one Top Shelf backdrop.
adb logcat -c || true
start "${B[@]}" "${DARK[@]}" --es screen settings --ei focus 0 --es bench settings; sleep 22
adb logcat -d -s PrismBench:I | sed -n "s/^.*PrismBench: //p" | tee "$OUT/bench.txt"
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
# Clear (smoked see-through) system keyboard: Prism saves the colour, then the test screen again.
start "${B[@]}" "${DARK[@]}" --es keyboardColour clear --es screen home; sleep 2
timeout 60 adb shell am start -W -n "$PKG/.ImeTestActivity" >/dev/null 2>&1; sleep 4
cap 50e-ime-clear
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
# The screensaver's Large and Centred clocks with weather, and Now Playing (no artwork: the note symbol).
start "${B[@]}" "${DARK[@]}" --es aerial prism --es ssClock large --es nowPlaying "'Golden Hour'" --es screen aerial; sleep 6; cap 30b-aerial-clock-large-now-playing
adb shell input keyevent KEYCODE_BACK; sleep 2
start "${B[@]}" "${DARK[@]}" --es aerial prism --es ssClock centred --es screen aerial; sleep 6; cap 30c-aerial-clock-centred
adb shell input keyevent KEYCODE_BACK; sleep 2
if [ "$QUICK" = 0 ]; then
  start "${B[@]}" "${DARK[@]}" --es aerial apple --es screen aerial; sleep 20; cap 31-aerial-apple
  sleep 10; cap 32-aerial-apple-later
  # Prism's memory while the screensaver plays and 6 s after it ends, without forcing a GC (as the SHIELD
  # reads it), then the same with the release-time collection switched off (--ez reclaim false).
  memline() { adb shell dumpsys meminfo -d "$PKG" | grep -E "^ +\.(Heap|LOS) |TOTAL PSS" | sed "s/^ */$1: /"; }
  {
    memline "playing"
    adb shell input keyevent KEYCODE_BACK; sleep 6; memline "6 s after"
    start "${B[@]}" "${DARK[@]}" --es aerial apple --es screen aerial --ez reclaim false; sleep 25
    memline "playing (reclaim off)"
    adb shell input keyevent KEYCODE_BACK; sleep 6; memline "6 s after (reclaim off)"
  } | tee "$OUT/screensaver-memory.txt"
  # Apple TV Aerials as the Home wallpaper (Spotlight shows the wallpaper around its card).
  start "${B[@]}" "${DARK[@]}" --es wallpaper aerial --es topShelf spotlight --es screen home --ei col 0; sleep 16
  cap 74-home-aerial-wallpaper
  adb logcat -d -s PrismAerial:V | tail -n 12 > "$OUT/aerial-wallpaper.txt"
  # How much of Prism's memory the playing wallpaper's read-ahead holds (ExoPlayer's buffers live in .LOS).
  sleep 20
  adb shell dumpsys meminfo -d "$PKG" | grep -E "^ +\.(Heap|LOS) |TOTAL PSS" | sed 's/^ */memory: /' | tee -a "$OUT/aerial-wallpaper.txt"
  start "${B[@]}" "${DARK[@]}" --es wallpaper aerial --es topShelf spotlight --es screen grid --ei row 1 --ei col 1; sleep 5
  cap 74b-grid-aerial-wallpaper
  # Behind the grid the aerial keeps moving (Android 11: drawn through OpenGL): a later shot differs.
  sleep 3; cap 74d-grid-aerial-wallpaper-later
  adb logcat -d -s PrismAerial:V | grep -E "OpenGL|playing|ERROR" | tail -n 4 >> "$OUT/aerial-wallpaper.txt"
  start "${B[@]}" "${DARK[@]}" --es wallpaper aerial --es topShelf spotlight --ez lite true --es screen home --ei col 0; sleep 4
  cap 74c-aerial-wallpaper-lite-still
fi

# Closing apps for real: with Power & Home Button on, swiping an app away presses Force Stop in Settings
# (behind a still of Prism TV). Open a real app, come back, close it, and check Android stopped it.
TARGET=""
for cand in com.google.android.youtube.tv com.google.android.tv com.android.tv com.google.android.videos \
  com.android.vending com.google.android.play.games com.google.android.apps.mediashell com.android.gallery3d \
  $(adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER 2>/dev/null \
    | tr -d '\r' | grep -oE 'packageName=[A-Za-z0-9_.]+' | cut -d= -f2 | sort -u); do
  case "$cand" in "$PKG"|*settings*|*launcher*|*Launcher*|*katniss*|*setupwraith*) continue;; esac
  if adb shell pm path "$cand" 2>/dev/null | grep -q package:; then TARGET="$cand"; break; fi
done
echo "close test target: ${TARGET:-none}" | tee "$OUT/close-test.txt"
if [ -n "$TARGET" ]; then
  adb shell settings put secure enabled_accessibility_services "$PKG/$PKG.system.PrismAccessibilityService"
  adb shell settings put secure accessibility_enabled 1
  sleep 4
  timeout 60 adb shell monkey -p "$TARGET" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1 ||
    timeout 60 adb shell monkey -p "$TARGET" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 8
  cap 63-close-test-app-open
  echo "before: pid=$(adb shell pidof "$TARGET" | tr -d '\r') $(adb shell dumpsys package "$TARGET" | grep -m1 -oE 'stopped=(true|false)')" | tee -a "$OUT/close-test.txt"
  # App Switcher snapshots: pressing Home in the app takes one (Android 11+) for its card.
  adb logcat -c || true
  emu_key 102; sleep 4 # KEY_HOME → KEYCODE_HOME on the emulator's keyboard
  cap 63a-home-after-snapshot
  echo "snapshots: $(adb shell run-as "$PKG" ls files/snapshots 2>&1 | tr -d '\r' | tr '\n' ' ')" | tee "$OUT/snapshot-test.txt"
  adb logcat -d -s PrismSnap:V PrismKeys:V | tail -n 8 | tee -a "$OUT/snapshot-test.txt"
  if [ "$QUICK" = 0 ]; then
    shot 63s-switcher-snapshot "${B[@]}" "${DARK[@]}" --es demoSnapshot "$TARGET" --es screen switcher --ei focus 0
  fi
  timeout 60 adb shell monkey -p "$TARGET" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1 ||
    timeout 60 adb shell monkey -p "$TARGET" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 5
  adb logcat -c || true
  # The switcher stalls the software-rendered API 34 emulator (see the end), so close from Home there.
  CLOSE_FROM=switcher; [ "$QUICK" = 1 ] && CLOSE_FROM=home
  start "${B[@]}" "${DARK[@]}" --es screen "$CLOSE_FROM" --es closeApp "$TARGET" --ei closeDelay 3000; sleep 3.2
  cap 63b-closing-still
  sleep 1.2; cap 63c-closing-still-later
  sleep 8; cap 63d-closed-back-in-switcher
  echo "after: pid=$(adb shell pidof "$TARGET" | tr -d '\r') $(adb shell dumpsys package "$TARGET" | grep -m1 -oE 'stopped=(true|false)')" | tee -a "$OUT/close-test.txt"
  adb logcat -d -s PrismClose:V | tee -a "$OUT/close-test.txt"
  echo "snapshots after closing: $(adb shell run-as "$PKG" ls files/snapshots 2>&1 | tr -d '\r' | tr '\n' ' ')" | tee -a "$OUT/snapshot-test.txt"

  # Banners Over Apps: a notification arriving while an app is on screen slides in at the top right.
  adb shell cmd notification allow_listener "$PKG/$PKG.system.PrismNotificationListener" >/dev/null 2>&1 || true
  start "${B[@]}" "${DARK[@]}" --ez bannersOver true --es screen home; sleep 3
  timeout 60 adb shell monkey -p "$TARGET" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1 ||
    timeout 60 adb shell monkey -p "$TARGET" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 7
  adb shell "cmd notification post -t 'Delivery' prism_banner_test 'Your order is on its way'" >/dev/null 2>&1
  sleep 1.4; cap 73-banner-over-app
  sleep 7; cap 73b-banner-gone
  # The same notification posted again (players re-post on every pause, seek or buffer) doesn't show
  # again, and media / Now Playing notifications never do: the shot should have no banner.
  for _ in 1 2; do
    adb shell "cmd notification post -t 'Delivery' prism_banner_test 'Your order is on its way'" >/dev/null 2>&1; sleep 1
  done
  adb shell "cmd notification post -S media -t 'Guardians of the Galaxy' prism_banner_media 'Nuvio'" >/dev/null 2>&1
  sleep 1.4; cap 73d-banner-not-again
  adb logcat -d -s PrismBanner:I | sed -n 's/^.*PrismBanner: //p' | tee "$OUT/banner-test.txt"
  # The same banner in Light, Liquid Glass (banners follow Prism TV's Material and Material Colour).
  start "${B[@]}" --es appearance light --es glass liquid --ez bannersOver true --es screen home; sleep 3
  timeout 60 adb shell monkey -p "$TARGET" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1 ||
    timeout 60 adb shell monkey -p "$TARGET" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 7
  adb shell "cmd notification post -t 'Prism TV' prism_banner_test2 'Banners take your Material and colour'" >/dev/null 2>&1
  sleep 1.4; cap 73c-banner-over-app-light-liquid
  adb shell input keyevent KEYCODE_HOME; sleep 3

  # Remote Buttons: learn the Red button in Settings, give it Control Centre, then press it at Home.
  start "${B[@]}" "${DARK[@]}" --ez demoRemote false --ez bannersOver false --es screen settings --es page RemoteButtons --ei focus 1; sleep 4
  key KEYCODE_DPAD_CENTER 2; cap 72-remote-press-a-button
  key KEYCODE_BACK 1.5
  adb shell settings delete secure enabled_accessibility_services >/dev/null 2>&1 || true
  adb shell settings put secure accessibility_enabled 0 >/dev/null 2>&1 || true
  adb logcat -d -b crash > "$OUT/crash-close.txt" 2>&1 || true
fi

# Restart Launcher: Prism TV comes back once in a new process (it used to restart over and over).
PID0="$(adb shell pidof "$PKG" | tr -d '\r')"
start "${B[@]}" "${DARK[@]}" --es screen settings --ez restart true; sleep 9
PID1="$(adb shell pidof "$PKG" | tr -d '\r')"; sleep 5
PID2="$(adb shell pidof "$PKG" | tr -d '\r')"
cap 64-after-restart
if [ -n "$PID1" ] && [ "$PID1" != "$PID0" ] && [ "$PID1" = "$PID2" ]; then R=OK; else R=FAILED; fi
echo "RESTART $R (before $PID0, after $PID1, later $PID2)" | tee "$OUT/restart-test.txt"

# Shizuku, step by step (Settings › Apps › Closing Apps › Shizuku): Install downloads Shizuku from GitHub
# and opens Android's installer; then start it (from the host here: the emulator's adbd has no TCP port
# for Prism TV's own adb client), allow Prism TV, and close an app through Shizuku.
SZ=moe.shizuku.privileged.api
# The screen's view tree, one node per line.
ui_dump() {
  timeout 30 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 1
  adb shell cat /sdcard/ui.xml | tr '>' '\n'
}
# Taps the element whose text is exactly $1 in a system dialog (they differ in button order and focus).
# Prism TV's own UI is left out: it's driven by the remote and ignores taps.
tap_text() {
  local b
  b="$(ui_dump | grep -i "text=\"$1\"" | grep -v 'package="com.prismtv' | head -n 1 \
    | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')"
  [ -n "$b" ] || { echo "no \"$1\" on screen"; return 1; }
  local label="$1"
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
  echo "tapped \"$label\""
}
# Answers the TV's "Allow USB debugging?" for Prism TV's key (Always allow ticked) if it's showing.
accept_adb_prompt() {
  for _ in $(seq 1 ${1:-8}); do adb shell dumpsys window | grep -qi "UsbDebugging" && break; sleep 1; done
  adb shell dumpsys window | grep -qi "UsbDebugging" || return 1
  sleep 1
  cap "${2:-65a-adb-allow-prompt}"
  ui_dump | grep -i 'text="Always allow from this computer"' | grep -q 'checked="true"' || tap_text "Always allow from this computer"
  sleep 0.8
  ui_dump | grep -i 'text="Always allow from this computer"' | grep -q 'checked="true"' && echo "always allow ticked"
  tap_text "Allow"
  sleep 2
}
{
  adb shell appops set "$PKG" REQUEST_INSTALL_PACKAGES allow >/dev/null 2>&1 || true
  start "${B[@]}" "${DARK[@]}" --es screen settings --es page Shizuku --ei focus 0; sleep 4
  cap 65-shizuku-steps
  adb logcat -c || true
  # Prism TV's own adb client: the TV asks "Allow USB debugging?" for Prism TV's key (focus starts on
  # "Always allow": tick it, then Allow).
  start "${B[@]}" "${DARK[@]}" --ez adbTest true --es screen settings --es page Shizuku --ei focus 0; sleep 2
  accept_adb_prompt 8 65a-adb-allow-prompt
  for _ in $(seq 1 15); do adb logcat -d -s PrismAdb:I | grep -q result= && break; sleep 1; done
  ADB_RESULT="$(adb logcat -d -s PrismAdb:I | grep result= | tail -1)"
  echo "own adb client: $ADB_RESULT"
  start "${B[@]}" "${DARK[@]}" --es screen settings --es page Shizuku --ei focus 0; sleep 3
  key KEYCODE_DPAD_CENTER 3; cap 65b-shizuku-downloading
  for _ in $(seq 1 30); do adb shell dumpsys window | grep -qi "packageinstaller" && break; sleep 2; done
  sleep 2; cap 65c-shizuku-installer
  tap_text "Install"
  for _ in $(seq 1 30); do adb shell pm path "$SZ" 2>/dev/null | grep -q package: && break; sleep 2; done
  sleep 3; cap 65d-shizuku-installed
  echo "installed: $(adb shell pm path "$SZ" | tr -d '\r')"
  adb shell input keyevent KEYCODE_BACK; sleep 1
  if echo "$ADB_RESULT" | grep -q "Ok("; then
    # Start Shizuku from Prism TV itself (Settings › … › Shizuku › Start Shizuku, through the TV's own adb).
    adb logcat -c || true
    start "${B[@]}" "${DARK[@]}" --es screen settings --es page Shizuku --ei focus 2; sleep 3
    key KEYCODE_DPAD_CENTER 1.5; cap 65d2-shizuku-starting
    accept_adb_prompt 5 65d1-adb-prompt-again && echo "(asked again)"
    sleep 12
    echo "started by Prism TV: $(adb logcat -d -s PrismShizuku:I | tail -3)"
    echo "server: $(adb shell ps -A -o PID,USER,NAME | grep -i shizuku | tr '\n' ' ')"
    adb logcat -d | grep -iE "shizuku" | grep -v PrismShizuku | tail -25
  else
    # This emulator's adbd has no TCP port for Prism TV: start Shizuku as the shell user from the host.
    adb shell 'P=$(pm path moe.shizuku.privileged.api | head -n 1 | cut -d: -f2); D=$(dirname "$P"); S=$(ls "$D"/lib/*/libshizuku.so 2>/dev/null | head -n 1); echo "starter: $S"; cp "$S" /data/local/tmp/shizuku_starter && chmod 700 /data/local/tmp/shizuku_starter && /data/local/tmp/shizuku_starter' 2>&1 | tail -5
    sleep 4
  fi
  start "${B[@]}" "${DARK[@]}" --es screen settings --es page Shizuku --ei focus 3; sleep 4
  cap 65e-shizuku-running
  key KEYCODE_DPAD_CENTER 4; cap 65f-shizuku-allow-prompt
  tap_text "Allow all the time" || key KEYCODE_DPAD_CENTER
  sleep 3
  start "${B[@]}" "${DARK[@]}" --es screen settings --es page Shizuku --ei focus 3; sleep 4
  cap 65g-shizuku-allowed
  if [ -n "$TARGET" ]; then
    timeout 60 adb shell monkey -p "$TARGET" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1; sleep 8
    echo "before: pid=$(adb shell pidof "$TARGET" | tr -d '\r')"
    # Its card in Android's own Recents (Android 11 prints StackId=, 12+ rootTaskId=).
    echo "recents before: $(adb shell dumpsys activity recents | grep -E "Recent #.*$TARGET" | head -2 | tr -d '\r')"
    adb logcat -c || true
    start "${B[@]}" "${DARK[@]}" --es screen home --es closeApp "$TARGET" --ei closeDelay 2000; sleep 8
    echo "after: pid=$(adb shell pidof "$TARGET" | tr -d '\r') $(adb shell dumpsys package "$TARGET" | grep -m1 -oE 'stopped=(true|false)')"
    echo "recents after: $(adb shell dumpsys activity recents | grep -cE "Recent #.*$TARGET") card(s)"
    # Clear Android's Recent Apps: the same line Prism runs (SystemRecents.ClearAll).
    adb shell monkey -p com.android.vending -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1; sleep 4
    start "${B[@]}" "${DARK[@]}" --es screen home; sleep 2
    echo "all cards before clear: $(adb shell dumpsys activity recents | grep 'Recent #' | grep -c 'type=standard')"
    if [ "$SDK" -ge 31 ]; then ID="sed -E 's/.*Task\\{[0-9a-f]+ #([0-9]+).*/\\1/'"; else ID="grep -oE 'StackId=[0-9]+' | cut -d= -f2"; fi
    adb shell "dumpsys activity recents | grep 'Recent #' | grep 'type=standard' | grep -v com.prismtv.launcher | $ID | while read t; do am stack remove \$t; done; true"
    sleep 2
    echo "all cards after clear: $(adb shell dumpsys activity recents | grep 'Recent #' | grep -c 'type=standard') ($(adb shell dumpsys activity recents | grep 'Recent #' | grep 'type=standard' | grep -oE 'A=[0-9]*:?[A-Za-z0-9_.]+' | tr '\n' ' '))"
    adb logcat -d -s PrismClose:I | grep "method=" | tail -1
  fi
} 2>&1 | tee "$OUT/shizuku-test.txt"

# Prism's memory at each step of what the user did on the SHIELD (Control Centre five times, then Free Up
# Memory, closing apps through Shizuku where it runs): the Java heap split into .Heap (small objects)
# and .LOS (big arrays), each after a garbage collection (a heap dump forces one), so only live memory counts.
memsnap() { # label
  adb shell am dumpheap -g "$PKG" /data/local/tmp/prism.hprof >/dev/null 2>&1 \
    || adb shell am dumpheap "$PKG" /data/local/tmp/prism.hprof >/dev/null 2>&1
  sleep 6; adb shell rm -f /data/local/tmp/prism.hprof
  adb shell dumpsys meminfo -d "$PKG" | grep -E "^ +\.(Heap|LOS) |Native Heap:|Graphics:|TOTAL PSS|Activities:" | sed "s/^ */$1: /"
}
{
  if [ -n "$TARGET" ]; then timeout 60 adb shell monkey -p "$TARGET" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1; sleep 8; fi
  start "${B[@]}" "${DARK[@]}" --es screen home; sleep 6
  memsnap home
  # Prism's own breakdown of the bitmaps it holds (Activity.dump).
  adb shell dumpsys activity "$PKG/.MainActivity" gc | sed -n '/Prism memory:/,$p' | head -16
  adb shell dumpsys meminfo "$PKG" | grep -A12 "App Summary" | sed 's/^/summary: /'
  for _ in 1 2 3 4 5; do adb shell input keyevent KEYCODE_MENU; sleep 1.5; adb shell input keyevent KEYCODE_BACK; sleep 1.5; done
  memsnap "after cc x5"
  adb logcat -c || true
  avail() { adb shell grep MemAvailable /proc/meminfo | tr -s ' ' | cut -d' ' -f2; }
  procs() { adb shell ps -A -o USER | grep -c '^u[0-9]*_a'; }
  echo "before free up memory: MemAvailable $(avail) kB, app processes $(procs)"
  start "${B[@]}" "${DARK[@]}" --es screen controlcentre --ei focus 13; sleep 4
  adb shell input keyevent KEYCODE_DPAD_CENTER; sleep 1.2; cap 47m-free-up-memory-working
  sleep 4; cap 47n-free-up-memory-done
  echo "after free up memory: MemAvailable $(avail) kB, app processes $(procs)"
  adb logcat -d -s PrismFree:I | tail -2
  # Artwork on screen is kept (and counted here); the rest of Prism's caches are empty.
  adb shell dumpsys activity "$PKG/.MainActivity" | sed -n '/Prism memory:/,/Prism memory history/p' | grep -E "PSS|backdrops|cards|let go"
  sleep 6
  memsnap "after free up memory"
  adb logcat -d -s PrismClose:I | tail -3
  # Closing Control Centre while it works: the result comes as a banner instead of the caption.
  start "${B[@]}" "${DARK[@]}" --es screen controlcentre --ei focus 13; sleep 4
  adb shell input keyevent KEYCODE_DPAD_CENTER; sleep 0.3; adb shell input keyevent KEYCODE_BACK
  sleep 3; cap 47o-free-up-memory-banner
  adb logcat -d -s PrismFree:I | tail -1
} 2>&1 | tee "$OUT/memory-test.txt"

# Notification access through Do It for Me (Prism TV's own adb client, allowed above where it can reach adbd).
{
  start "${B[@]}" "${DARK[@]}" --es screen settings --es page Access --ei focus 0; sleep 3
  key KEYCODE_DPAD_CENTER 2.5; cap 66-allow-notifications-dialog
  # Do It for Me is the second button when the TV has a Notification access page, else the first.
  ui_dump | grep -q 'text="Open Notification Access"' && key KEYCODE_DPAD_DOWN 0.6
  key KEYCODE_DPAD_CENTER 1
  accept_adb_prompt 6 66a-adb-prompt
  sleep 5; cap 66b-allow-notifications-done
  echo "listeners: $(adb shell settings get secure enabled_notification_listeners | tr -d '\r')"
} 2>&1 | tee "$OUT/notif-test.txt"

# Recent Apps (usage access) through Do It for Me: the dialog offers Usage Access where the TV has the page.
# Usage access is put back as it was afterwards, so the later shots don't change.
{
  USAGE_BEFORE=$(adb shell appops get "$PKG" GET_USAGE_STATS | tr -d '\r')
  echo "before: $USAGE_BEFORE"
  adb shell appops set "$PKG" GET_USAGE_STATS default
  start "${B[@]}" "${DARK[@]}" --es screen settings --es page Access --ei focus 2; sleep 3
  key KEYCODE_DPAD_CENTER 2.5; cap 66c-allow-recent-apps-dialog
  ui_dump | grep -q 'text="Open Usage Access"' && { echo "has Usage Access page"; key KEYCODE_DPAD_DOWN 0.6; }
  key KEYCODE_DPAD_CENTER 1
  accept_adb_prompt 6 66d-adb-prompt
  sleep 5; cap 66e-allow-recent-apps-done
  echo "after: $(adb shell appops get "$PKG" GET_USAGE_STATS | tr -d '\r')"
  case "$USAGE_BEFORE" in *allow*) adb shell appops set "$PKG" GET_USAGE_STATS allow ;; *) adb shell appops set "$PKG" GET_USAGE_STATS default ;; esac
} 2>&1 | tee "$OUT/usage-test.txt"

# Recommendations from Apps (TV listings) through Do It for Me: Ask Android comes first, then Do It for Me.
# The permission is put back as it was afterwards.
{
  TVL=android.permission.READ_TV_LISTINGS
  granted() { adb shell dumpsys package "$PKG" | grep -m1 "$TVL: granted" | tr -d '\r' | sed 's/^ *//'; }
  TVL_BEFORE=$(granted); echo "before: $TVL_BEFORE"
  adb shell pm revoke "$PKG" "$TVL" 2>&1 | tail -1
  # Revoking restarts Prism TV, which then asks Android's own question once (first launch): answer
  # Don't allow (Allow is focused, Don't allow under it), as on a TV that stopped asking.
  start "${B[@]}" "${DARK[@]}" --es screen home; sleep 5
  if ui_dump | grep -qi "TV listings"; then key KEYCODE_DPAD_DOWN 0.6; key KEYCODE_DPAD_CENTER 1.5; echo "declined Android's question at launch: $(granted)"; fi
  start "${B[@]}" "${DARK[@]}" --es screen settings --es page Access --ei focus 1; sleep 3
  key KEYCODE_DPAD_CENTER 2.5; cap 66f-allow-recommendations-dialog
  key KEYCODE_DPAD_DOWN 0.6
  key KEYCODE_DPAD_CENTER 1
  accept_adb_prompt 6 66g-adb-prompt
  sleep 5; cap 66h-allow-recommendations-done
  echo "after: $(granted)"
  case "$TVL_BEFORE" in *granted=true*) adb shell pm grant "$PKG" "$TVL" ;; esac
} 2>&1 | tee "$OUT/tvlistings-test.txt"

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
key KEYCODE_DPAD_LEFT 1.0; key KEYCODE_DPAD_LEFT 1.0; key KEYCODE_DPAD_UP 1.6; key KEYCODE_DPAD_RIGHT 1.0; key KEYCODE_DPAD_RIGHT 1.0
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

# GitHub edition without an invite code (last: going without Pro puts paid settings back to free ones).
WAIT=5 shot invite-01-settings-root-locked "${B[@]}" "${DARK[@]}" --es invite locked --es screen settings --ei focus 0
WAIT=5 shot invite-02-pro-page-locked      "${B[@]}" "${DARK[@]}" --es invite locked --es screen settings --es page Pro --ei focus 0
key KEYCODE_DPAD_CENTER 2; cap invite-03-code-keyboard

adb logcat -d -t 3000 > "$OUT/logcat.txt" 2>&1 || true
grep -E "FATAL|AndroidRuntime|prismtv" "$OUT/logcat.txt" | tail -60 > "$OUT/logcat-app.txt" || true
adb logcat -d -b crash > "$OUT/crash.txt" 2>&1 || true
ls -la "$OUT"
exit 0
