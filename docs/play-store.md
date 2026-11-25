# Publishing Prism TV on Google Play

A walkthrough from no account to a live listing. The ready-made listing text, graphics, screenshots,
content-rating and data-safety answers and the privacy policy page are in the project's listing kit
(`play/` in the project files); this page is the order to do things in, and what's specific to the build. Steps marked **(once)** are only done the first time.
Google's screens get renamed now and then; the names here were current in November 2026.

## 0. What you need

- A Google account for publishing, and **$25** (one-time) for the developer account.
- An Android phone to verify the account (Google asks for it).
- The Play bundle: every push builds it. On GitHub, open **Actions**, the latest green **Android CI**
  run, and download the **prism-tv-play-bundle** artifact (a zip with `app-play-release.aab` inside).

## 1. Create the developer account (once)

1. Go to [play.google.com/console](https://play.google.com/console) and sign up.
2. Choose **Yourself** (a personal account) unless you have a registered business.
   - Personal: needs ID verification, and **before production you must run a closed test with at least
     12 testers for 14 days in a row** (see step 9).
   - Organisation: needs a D-U-N-S number for a registered business; organisation accounts aren't held
     to the 12-tester rule.
3. Pay the $25 and finish identity verification. This can take a few days.
4. **Payments profile** (Setup › Payments profile): needed before you can sell Prism Pro. Add your
   bank account for payouts and your tax details.

## 2. Create the app (once)

**Home › Create app**:

- App name: **Prism TV Launcher** (keep "Apple", "tvOS" and "Apple TV" out of the name, listing and
  screenshots).
- Default language, **App**, **Free** (Prism Pro is sold inside it), tick the declarations, **Create app**.

## 3. App signing (once)

Let Google create and keep the **app signing key** (the default, "Use a Google-generated key"). The
two editions have different package names and never update each other, so Play's copy doesn't need
the `prism` key. CI signs the bundle with the `prism` key, which makes `prism` your **upload key**:
Play registers it from the first bundle you upload. If the upload key is ever lost, Play support can
reset it; the GitHub edition keeps using `prism` as before.

(If the Play edition ever shares the GitHub edition's package name instead, choose **Use a different
key › Export and upload a key from Java keystore** and upload `prism` with Google's PEPK tool, so
the two can update each other.)

## 4. Prism Pro, the in-app product (once)

**Monetise with Play › Products › One-time products › Create**:

- Product ID: **`prism_pro`** (exactly this; the app asks Play for it by this name).
- Name: **Prism Pro**. Description: "Wallpapers, widgets, icon packs, profiles, Screen Time and more,
  unlocked once."
- Price: **$3.99** for the launch month (use **Set prices** to let Play convert it per country), then
  edit it to **$5.99**. Activate the product.

The product can only be created after a bundle with Play Billing has been uploaded (step 5), so you may
need to come back to this after the first internal test upload.

To test buying without paying: **Setup › License testing**, add your own Google account. Purchases from
that account are free test purchases.

## 5. Upload the bundle to internal testing

**Test and release › Testing › Internal testing › Create new release**:

1. Upload `app-play-release.aab`.
2. Release name: the version (e.g. `0.2.300`); notes: a line on what's new.
3. **Save › Review release › Start rollout**.
4. **Testers** tab: create a list with your own Gmail, save, and open the **opt-in link** on your
   computer while signed in with that account. Then install from Play on your TV (search won't find it
   yet: use the link, then "Install on: your TV").

Internal testing has no review delay, so this is how you try each Play build yourself. The Play edition
installs beside the GitHub one (it's a separate app, `com.prismtv.launcher.play`).

## 6. Store listing

**Grow users › Store presence › Main store listing**:

- **Short description** (80 characters max): "A glassy, modern Home screen for Android TV and Google TV."
- **Full description** (4,000 max), a starting point:

  > Prism TV Launcher gives your Android TV a clean, glassy Home screen. Your apps sit in a frosted-glass
  > dock, the Top Shelf shows what's new in the app you're on, and Control Centre puts Wi-Fi, Bluetooth,
  > the Sleep Timer and Picture settings one press away.
  >
  > Free: the dock, folders, the Top Shelf, Liquid and Frosted Glass, Colour Mode, Control Centre, the App
  > Switcher, Prism Keyboard, Prism's own wallpapers and screensaver, Backup & Restore and a Home button
  > that comes back to Prism.
  >
  > Prism Pro (one-time, try it free for 7 days): your own pictures and moving wallpapers, Card Spotlight,
  > widgets, icon packs and custom banners, profiles with Restrictions and Screen Time, banners over apps,
  > more remote buttons, keyboard predictions and dictation, and more.

- **Graphics** (sizes Google requires):
  - App icon: 512 × 512 PNG.
  - Feature graphic: 1024 × 500.
  - **Android TV banner: 1280 × 720** (the app's own banner art, scaled up, works).
  - **Android TV screenshots: at least 1, up to 8, 1920 × 1080** (16:9). Take them on the SHIELD with
    `adb exec-out screencap -p > shot.png`, or use the emulator screenshots CI saves on the `ci-results`
    branch. Show only Prism TV and your own content: no Apple aerial names or logos.
  - Phone screenshots: Google may still ask for 2. Cropped TV screenshots are fine.
- Category: **Personalisation**. Contact email: required (shown publicly).

## 7. App content (the policy forms)

**Monitor and improve › Policy and programmes › App content**. Each card is a short form:

- **Privacy policy**: a public web address. The listing kit's `privacy-policy.html` is ready (fill in
  your support email). Host it free with GitHub Pages on the public `Prism-TV-Releases` repository:
  add the file there, then that repository's **Settings › Pages › Deploy from a branch › main / (root)**.
  The address becomes `https://delosdinh.github.io/Prism-TV-Releases/privacy-policy.html`, which the
  app's Settings › About › Privacy Policy already shows. (Pages needs a public repository on the free
  plan, which is why it goes there and not in the code repository.)
- **Ads**: No.
- **App access**: "All functionality is available without special access" (the 7-day trial unlocks Pro
  for reviewers).
- **Content rating**: start the questionnaire, category **Utility, Productivity, Communication or
  Other**, answer No to violence, sexuality, gambling and user interaction. Expect **Everyone / PEGI 3**.
- **Target audience**: **18 and over** (or 13+). Don't include under-13s: that brings the Families
  policy, which a launcher with Accessibility and usage access wouldn't pass.
- **Data safety**: Prism TV has no account, ads or analytics, and stores everything on the TV. Data
  that leaves the TV:
  - **Approximate location**: collected, not shared; processed ephemerally; for **App functionality**
    (the weather in the time pill). The TV's IP address goes to ipwho.is / ipapi.co to find the city,
    and its coordinates to Open-Meteo for the forecast. Optional (Weather can be turned off).
  - Everything is sent **encrypted in transit** (HTTPS). Users can't request deletion because nothing
    is stored off the TV: answer that no data is retained.
  - Purchases go through Google Play, which Google covers; Prism TV doesn't see payment details.
  - Dictation uses Android's own speech recogniser (Google's service), and the TV's photos, notifications
    and app usage are read only on the TV. None of these leave it from Prism TV.
- **Government apps, financial features, health, news**: No.

## 8. Permission declarations

Still under **App content**:

- **Accessibility API** (Power & Home Button). Google treats a launcher's accessibility service as "not
  an accessibility tool", which is allowed with a disclosure, your declaration and a video.
  - Describe it: "Power & Home Button lets the remote's Home button and the buttons the user chooses in
    Remote Buttons open Prism TV or an action they picked, shows the power menu and Sleep Timer, and
    shows notification banners over other apps. It filters key events and reads the package name of the
    app in front. It does not read screen content, collects no data, and nothing leaves the device."
  - The app shows a disclosure ("Prism TV Uses Accessibility") with **Agree** / **Not Now** before it
    opens Android's Accessibility settings.
  - **Video** (unlisted YouTube link, under a minute, film the TV or screen-record with `adb shell
    screenrecord`): Settings › Power & Home Button → the disclosure → Agree → Android's Accessibility
    settings → turn on Prism TV Power & Home Button → back in Prism TV, press Home in another app and it
    returns to Prism; set a Remote Button and press it.
- **QUERY_ALL_PACKAGES**: declare **Launcher / device search** ("Prism TV is a Home screen launcher: it
  lists, searches and opens every installed app").
- **Photo and video permissions** (`READ_MEDIA_IMAGES`): Google may ask why Prism TV doesn't use the
  photo picker. The answer: the screensaver and wallpaper show the user's whole Pictures folder as a
  rotating slideshow, which a one-off picker can't do. If they push back, Claude can switch Your Pictures
  to the system photo picker.

## 9. Closed test: 12 testers for 14 days (personal accounts)

1. **Testing › Closed testing › Create track**, upload the same bundle, roll it out.
2. **Testers**: add an email list (or a Google Group) with at least 12 Google accounts, and share the
   opt-in link.
3. Testers must **stay opted in for 14 days in a row**. Installing and opening it a few times helps:
   Google asks about tester engagement afterwards. Any Android device with Play works; testers don't
   need a TV.
4. After 14 days: **Dashboard › Apply for production**, answer the questions about the test, and wait
   for review (usually up to 7 days).

### Finding 12 testers without asking friends

- **Tester-swap communities** (most common and free): r/AndroidClosedTesting and r/TestersCommunity on
  Reddit, and groups like "Testers Community" on Telegram or Discord. You test other developers' apps
  for 14 days and they test yours. Post a short description and your Google Group or opt-in link.
- **Paid testing services** exist (they sell "12 testers for 14 days"). They work, but Google wants
  real testers and the questions after the test ask what you learned, so a swap community, where people
  actually open the app, is the safer choice.
- **An organisation account** skips the rule, but needs a registered business with a D-U-N-S number.
- Family members' Google accounts count too, if they'll keep the opt-in for two weeks.

## 10. TV distribution and launch

1. **Test and release › Advanced settings › Form factors › Add form factor › Android TV**, then opt in
   to TV distribution. Google runs a separate TV quality review (it checks the TV banner, D-pad
   navigation and that nothing needs a touchscreen; Prism TV already meets these).
2. **Production › Create new release**, upload the latest bundle, and roll out. A first review usually
   takes a few days.
3. After launch: each update is a new bundle uploaded to a track (internal first, then production).
   The version number goes up with every CI build, so any newer bundle is accepted.

## What Claude changed in the app for Play

- TV only (`android.software.leanback` required), its own package name `com.prismtv.launcher.play`.
- No Software Update, no install permissions, no Shizuku download or Shizuku/root/adb helpers.
- No Apple aerials (the screensaver and wallpaper use Prism's scenes and your pictures), and no Apple
  names: "Liquid Glass" reads "Prism Glass", and "like tvOS" comparisons are gone.
- Power & Home Button can't read the screen or take screenshots, and shows the disclosure first
  (the listing kit's exact text, with Agree & Open Settings / Not Now).
- Settings › About › Privacy Policy.
- Prism Pro through Google Play Billing (`prism_pro`), with a 7-day trial, and Settings › Prism Pro
  for buying and restoring.
