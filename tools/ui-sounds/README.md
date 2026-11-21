# Prism TV Originals — interface sounds

The navigation sounds in `app/src/main/res/raw/ui_*.wav` come from the Prism TV Originals 1.6.0 sound
pack (the companion Kodi skin's GUI sounds). They are procedurally synthesised by
`generate_sounds.py` in this folder (NumPy; `python generate_sounds.py rebuilt`) and are licensed
GPL-2.0-or-later (see `COPYING`). The pack's startup arrangement isn't used by the launcher.

| File in app | Pack file | Used for |
| --- | --- | --- |
| ui_focus_app | focus-app.wav | Focus moves across app tiles and folders |
| ui_cursor | cursor.wav | Focus in menus, Settings, Control Centre, sidebar, dialogs |
| ui_focus_large | focus-large.wav | Focus on Top Shelf items and App Switcher cards |
| ui_click | click.wav | Select / open |
| ui_back | back.wav | Back / close |
| ui_toggle | toggle.wav | A setting changes |
| ui_swish | swish.wav | Control Centre, sidebar, switcher and menus opening |
| ui_confirm | confirm.wav | Something completed (folder created, update ready) |
| ui_error | error.wav | Something failed |
| ui_notify | notify.wav | A banner or prompt appears |
| ui_delete | delete.wav | Hide / remove from folder |
| ui_clear | clear.wav | Free Up Memory |
