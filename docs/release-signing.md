# Release signing: moving the key into GitHub secrets

Every Prism TV build is signed with one permanent key. Android only installs an update over an
existing Prism TV if both are signed with the same key, so this key must never change.

Today the build reads that key from a private draft release called `prism-signing-key`. That works,
but it means the key file and its password sit together in one place, and every build job needs
permission to write to the repository just to read them. GitHub has a purpose-built, encrypted place
for this instead: **repository secrets**. Once the key is there, the build stops reading the draft.

These steps copy the **same** key into secrets. Nothing about the key changes, so every TV keeps
updating as before. Only you should do this; it takes about five minutes.

## 1. Download the two key files (from your own browser)

1. Open the repository on GitHub, then **Releases**.
2. Open the draft named **"Prism TV signing key (private, never publish or delete)"**.
3. Download both files: `prism.p12` and `prism-p12-password.txt`.
4. Don't publish, edit or delete the draft. Just download.

Keep these files private. Anyone with both can sign apps as Prism TV.

## 2. Turn the key file into text

GitHub secrets hold text, so the `.p12` file is converted to base64 (plain letters and digits).

**Windows (PowerShell)**, in the folder you downloaded to:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("prism.p12")) | Set-Clipboard
```

That copies the text to your clipboard. (On macOS: `base64 -i prism.p12 | pbcopy`.)

## 3. Add two repository secrets

1. On GitHub: the repository's **Settings › Secrets and variables › Actions › New repository secret**.
2. Name `PRISM_KEYSTORE_B64`, value: paste the clipboard from step 2. **Add secret**.
3. New repository secret again. Name `PRISM_KEYSTORE_PASSWORD`, value: the text inside
   `prism-p12-password.txt` (open it in Notepad; copy it exactly, with no extra spaces or new line).

## 4. Check it worked

1. Open **Actions**, then the next run of **Android CI** (or press **Run workflow** to start one).
2. In the **Build APKs** job, open the **Signing key** step. It should say
   `Permanent Prism TV key from repo secrets, certificate SHA-256 …`.
3. That SHA-256 must be **the same** as in earlier runs (it was printed in the same place, with
   "from draft release" instead). Same fingerprint means same key, so TVs keep updating.

If it says "draft release" there's a typo in a secret name; if the step fails, the password
doesn't match the file.

## 5. Afterwards

- Store `prism.p12` and the password somewhere safe and offline too (a password manager is good).
  If both GitHub and your copy were lost, no future build could update existing installs.
- Delete the downloaded files from your Downloads folder once they're backed up.
- Tell Claude when step 4 shows "repo secrets". A small follow-up change then removes the draft
  release from the build entirely and drops the build job's write permission to read-only for
  the key. The draft itself stays untouched until **you** decide what to do with it.

## Google Play later

The Play edition is planned as a separate app id (`com.prismtv.launcher.play`), so Play installs
and GitHub installs never update each other and don't need to share a key. For Play, let Google
create the app signing key (Play App Signing) and use a separate **upload key** of your own; keep
the GitHub key above for the GitHub edition only. CI builds the Play bundle (`app-release.aab`, in
each run's artifacts as `prism-tv-play-bundle`).

## What the build does now if the key is missing

- **Push builds** (which publish the `preview` release): the run stops with a clear error and
  nothing is published.
- **Pull request builds**: they still build and test, signed with a throwaway key, and are never
  published.

## Release channels

A push publishes to up to three places (the `publish` job in `.github/workflows/android.yml`):

- **This repository's `preview` release**, for the test TVs: every branch push, or, once the
  repository variable `PREVIEW_BRANCHES` is set (space-separated branch names), only those branches.
- **The public releases-only repository** (`RELEASES_REPO`), which every other TV's Software Update
  reads first: only pushes to `main`, so work-in-progress branches never reach them. Merging a pull
  request is what releases it.
- **The releases repository's `testing` release**: the same builds as preview. TVs set to
  Settings › Software Update › **Update Channel › Testing** read it (and the stable channel, taking
  whichever is newer), so test TVs keep updating themselves. Stable, the default, only gets `main`.
