# Two editions: GitHub and Google Play

Prism TV is built two ways from this one repository. Every bug fix lands in both.

| | **GitHub edition** (you and friends) | **Play edition** (everyone, on Google Play) |
|---|---|---|
| File | `prismtv.apk` (sideloaded with Downloader) | `app-play-release.aab`, uploaded to Play Console |
| Package name | `com.prismtv.launcher` | `com.prismtv.launcher.play` |
| Updates | Prism TV updates itself (Settings › Software Update) | Google Play updates it |
| Prism Pro | Always unlocked | 7-day free trial, then a one-time purchase through Google Play |
| Shizuku, root, "Do It for Me" (the TV's own adb) | Yes | No (Play doesn't allow them); the commands are shown to type yourself |
| Closing apps completely | Shizuku, root, or Power & Home Button pressing Force Stop | Frees memory only |
| App Snapshots | Yes | No (Power & Home Button can't take screenshots there) |
| Power & Home Button (Accessibility) | Yes | Yes, for Home, Remote Buttons, power menu and banners, after a disclosure screen |

Both are signed with the same permanent `prism` key. Because the package names differ, both can be
installed on one TV, and Google Play never replaces a friend's GitHub install with the Play edition.
(Settings don't move between them by themselves; Backup & Restore carries them over.)

In the code, `BuildConfig.PLAY` is `true` in the Play edition. The Play edition's manifest
(`app/src/play/AndroidManifest.xml`) drops the install permissions, the updater's receiver and Shizuku;
its accessibility service (`app/src/play/res/xml/accessibility_service.xml`) can't read the screen or
take screenshots. Prism Pro's rules are in `data/Pro.kt`, and Google Play Billing in
`app/src/play/java/.../ProStore.kt`.

## Making the code repository private without breaking updates

Today Software Update downloads `update.json` and `prismtv.apk` from this repository's `preview`
release. That only works because the repository is public: a private repository's files need a
GitHub sign-in, which a TV doesn't have.

So the installer files move to a second, **public repository that holds only releases**: no code, just
the APK and `update.json`. Builds from this change on look there first and fall back to the old place.
The code itself stays private.

### What you do (about 10 minutes, once)

1. **Create the releases repository.** On GitHub: **New repository**, name `Prism-TV-Launcher-Releases`,
   **Public**, and tick **Add a README** (a release needs at least one commit to point at).
   If you choose a different name, tell Claude so the app's address is changed to match.
2. **Make a token that can only publish there.** GitHub › your picture › **Settings › Developer settings
   › Personal access tokens › Fine-grained tokens › Generate new token**.
   - Name: `Prism releases`. Expiration: 1 year (set a reminder to renew it).
   - Repository access: **Only select repositories** › `Prism-TV-Launcher-Releases`.
   - Permissions › Repository permissions › **Contents: Read and write**. Nothing else.
   - Generate, and copy the token.
3. **Give it to the build.** In **this** repository: **Settings › Secrets and variables › Actions**.
   - **Secrets** tab › New repository secret › name `RELEASES_TOKEN`, value: the token.
   - **Variables** tab › New repository variable › name `RELEASES_REPO`, value `delosdinh/Prism-TV-Launcher-Releases`.
4. **Push any commit** (or re-run the latest build). Check that `Prism-TV-Launcher-Releases` now has a
   **Prism TV** release with `prismtv.apk`, `prismtv-debug.apk` and `update.json`.
5. **Update every TV once**, including your friend's: Settings › Software Update › Check for Updates.
   The build installed now knows the new address.

### When it's safe to flip the repository to private

All of these must be true:

- Step 4 worked: the releases repository has the latest build.
- Every TV (yours and your friend's) is on a build made after this change. On each TV, Settings ›
  About › Version should be the same as or newer than the version in the releases repository.
- You've given your friend the new download link (below), for reinstalling one day.

Then: this repository › **Settings › General › Danger Zone › Change visibility › Make private**.
TVs on older builds would stop finding updates, which is why the second point matters.

**GitHub Actions minutes.** Public repositories build for free. Private ones on the free plan get 2,000
minutes a month. A build here takes roughly 15–25 minutes plus lint and tests; the emulator screenshot
jobs take 2–3 hours, so once the repository is private they only run when a commit message contains
`[screens]`.

## How you update the GitHub edition

Nothing changes for you: push a change, CI builds and signs it, and publishes it to both releases.
Each TV checks every few hours and shows **Update Now**, or you can run Settings › Software Update ›
Check for Updates.

## How your friend installs and updates

Once:

1. On the TV, install **Downloader** (by AFTVnews) from the Play Store.
2. Open it and type `https://github.com/delosdinh/Prism-TV-Launcher-Releases/releases/download/preview/prismtv.apk`
   (or make a short code for it in Downloader's own shortener).
3. Install, and allow Downloader to install apps when Android asks.
4. In Prism TV, Settings › Software Update: allow Prism TV to install apps when it asks, once.

After that, they do nothing: Prism TV offers each update on their Home screen.

The link works for anyone who has it (that's what lets a TV download without signing in), but the code
stays private and the releases repository holds nothing but the installer.
