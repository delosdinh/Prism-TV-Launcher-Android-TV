# Prism TV Originals 1.6.2 — interface sounds

The navigation sounds in `app/src/main/res/raw/ui_*.wav` come from the Prism TV Originals 1.6.2 sound
pack (the companion Kodi skin's GUI sounds). They are procedurally synthesised by
`generate_sounds.py` in this folder (NumPy; `python generate_sounds.py rebuilt`) and are licensed
GPL-2.0-or-later (see `COPYING`). The pack's startup arrangement and its noise-based swish aren't used by the launcher; `open` and `dismiss` were added for it (render with `python generate_sounds.py OUTPUT_DIR`).

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
| ui_open | open.wav (launcher addition) | Control Centre, sidebar, switcher and menus opening: Select's own chord and voices, rolled quickly upward |
| ui_dismiss | dismiss.wav (launcher addition) | Swiping an app away in the App Switcher: Back's own chord and voices, rolled upward on a shorter timeline, narrowing as it fades |
| ui_confirm | confirm.wav | Something completed (folder created, update ready) |
| ui_error | error.wav | Something failed |
| ui_notify | notify.wav | A banner or prompt appears |
| ui_delete | delete.wav | Hide / remove from folder |
| ui_clear | clear.wav | Free Up Memory |
