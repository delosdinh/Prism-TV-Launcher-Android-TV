# Startup profile recorder

Prism TV ships a *baseline profile*: the list of code Android compiles ahead of time at install, so the
first launches and first visits to each screen after an update run at full speed. Two parts:

- `app/src/main/baseline-prof.txt` — hand-written: all of Prism TV's own code.
- `app/src/main/generated/baselineProfiles/` — **recorded**: `StartupProfileGenerator` drives Prism
  with the D-pad (Home Screen, Settings, Control Centre, sidebar) while Android notes every method and
  class used, including the libraries'. `startup-prof.txt` also orders the APK's dex files so a cold
  start reads startup code first.

To re-record (after big changes), push a commit with `[profile]` in its message:
`.github/workflows/baseline-profile.yml` runs it on the Android TV emulator (API 34, no root needed) and
publishes the two files to the `ci-results` branch under `profile/<branch>/` (and as the
`startup-profile` artifact); commit them to `app/src/main/generated/baselineProfiles/`.

Locally, with a device or emulator on API 33+ connected:

```
./gradlew :app:generateBaselineProfile -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
```
