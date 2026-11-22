# Prism TV Originals 1.6.2 — interface sounds

The navigation sounds in `app/src/main/res/raw/ui_*.flac` come from the Prism TV Originals 1.6.2 sound
pack (the companion Kodi skin's GUI sounds). They are procedurally synthesised by
`generate_sounds.py` in this folder (NumPy; `python generate_sounds.py rebuilt`) and are licensed
GPL-2.0-or-later (see `COPYING`). The pack's startup arrangement and its noise-based swish aren't used by the launcher. The launcher's own cues (`open`, `dismiss` and the gesture cues) reuse the Select and Back instruments exactly and add the motion of what's on screen: stereo travel, roll direction, a small pitch inflection and a breath of air (render with `python generate_sounds.py OUTPUT_DIR`).

The script writes WAV (16-bit, 48 kHz, stereo). The app ships them as lossless FLAC, sample-for-sample the
same audio at about a seventh of the size (the build keeps `.flac` uncompressed so SoundPool can open it):

```sh
for f in OUTPUT_DIR/*.wav; do
  out="app/src/main/res/raw/ui_$(basename "$f" .wav | tr - _).flac"
  [ -e "$out" ] || continue  # capture and swish aren't used by the launcher
  ffmpeg -y -i "$f" -map_metadata -1 -fflags +bitexact -compression_level 12 -c:a flac "$out"
done
python3 tools/ui-sounds/strip_flac_metadata.py app/src/main/res/raw/ui_*.flac  # drops ffmpeg's 8 KB padding
```


| File in app | Pack file | Used for |
| --- | --- | --- |
| ui_focus_app | focus-app.wav | Focus moves across app tiles and folders |
| ui_cursor | cursor.wav | Focus in menus, Settings, Control Centre, sidebar, dialogs |
| ui_focus_large | focus-large.wav | Focus on Top Shelf items and App Switcher cards |
| ui_click | click.wav | Select / open |
| ui_back | back.wav | Back / close |
| ui_toggle | toggle.wav | A setting changes |
| ui_key | key.wav | A key pressed on Prism's keyboard |
| ui_focus_key | focus-key.wav | Focus moving across keyboard keys |
| ui_open | open.wav (launcher addition) | Settings, menus, folders and the keyboard opening: Select's own chord and voices, rolled quickly upward |
| ui_dismiss | dismiss.wav (launcher addition) | Flicking an app away in the App Switcher: Back's voices in a fast upward roll that rises about 7 semitones and thins out, with air rushing up |
| ui_reveal_left / ui_hide_left | reveal-left.wav / hide-left.wav (launcher additions) | The sidebar: Select's chord travels in from the left and scoops up into pitch; Back's chord rolls down, sinks a little and travels away left |
| ui_reveal_right / ui_hide_right | reveal-right.wav / hide-right.wav (launcher additions) | Control Centre: Select's chord rolls down from its top note as it comes in from the right; Back's chord gathers upward and draws away right |
| ui_switcher_open / ui_switcher_close | switcher-open.wav / switcher-close.wav (launcher additions) | The Home Screen shrinking into the App Switcher (Select's chord fans out from the middle, wider than usual) and growing back (Back's chord draws in to the middle) |
| ui_confirm | confirm.wav | Something completed (folder created, update ready) |
| ui_error | error.wav | Something failed |
| ui_notify | notify.wav | A banner or prompt appears |
| ui_delete | delete.wav | Hide / remove from folder |
| ui_clear | clear.wav | Free Up Memory |
