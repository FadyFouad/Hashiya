# Releasing Hashiya

How to ship a version to the App Store and Google Play. Written for 0.1.0; later releases repeat the "Each release" steps only.

**In the repo:** the icons, screenshots (`docs/store/`), listing text and questionnaire answers (`docs/store/metadata.md`), the iOS privacy manifests and export flag, and Android release signing.

**Done by hand:** everything in the developer consoles below.

## 0. Before either store

1. **OpenAlex API key.** Create a free key at [openalex.org](https://openalex.org) (sign in, then the API key page in your account settings). Then:
   - In `android/local.properties`, add `OPENALEX_API_KEY=<key>`.
   - Copy `ios/Config/Secrets.example.xcconfig` to `ios/Config/Secrets.xcconfig` and put the key after `OPENALEX_API_KEY =`.
   - Both files are git-ignored. The key ends up inside the app, where a determined user could extract it; that's acceptable for a free key, and users can still enter their own in Settings.
2. **Privacy policy link:** <https://fadyfouad.github.io/Hashiya-Privacy-Policy/>, served by GitHub Pages from the public [Hashiya-Privacy-Policy](https://github.com/FadyFouad/Hashiya-Privacy-Policy) repo. It is both the privacy policy URL and the support URL. To change the policy, edit that repo's `index.html` and push; the page updates within a minute or two.
3. **Check the listing text** with `python3 scripts/check-store-metadata.py`; every field should say `ok`.

## 1. App Store

### One-time setup (developer.apple.com → Certificates, Identifiers & Profiles)

1. **Register the App Group:** Identifiers → **+** → App Groups → `group.com.etatech.hashiya`.
2. **Register the app's App ID:** Identifiers → **+** → App IDs → App.
   - Description `Hashiya`, Bundle ID (explicit) `com.etatech.hashiya`.
   - Capabilities: **App Groups** (then Configure → pick `group.com.etatech.hashiya`).
3. **(Pending) Register the share extension's App ID** the same way: Bundle ID `com.etatech.hashiya.share`, capability **App Groups** with the same group.

   The keychain group `$(AppIdentifierPrefix)com.etatech.hashiya.shared` needs no registration: keychain sharing between apps of one team is always allowed.

   If Xcode's automatic signing registers these for you on the first archive, check they match; don't create duplicates.
4. **Team ID:** in `ios/Config/Secrets.xcconfig`, uncomment `DEVELOPMENT_TEAM` and set your team ID (Membership details at developer.apple.com/account).

### Create the app (appstoreconnect.apple.com → Apps → +)

| Field | Value |
|---|---|
| Platform | iOS |
| Name | `Hashiya: Research Papers` (if taken, try `Hashiya – Research Library`) |
| Primary language | English (U.S.) |
| Bundle ID | `com.etatech.hashiya` |
| SKU | `hashiya-ios` |
| User access | Full access |

Then add **Arabic** under App Information → Localizable information, so each language gets its own listing.

### Build and upload

1. `cd ios && xcodegen generate`, then open `Hashiya.xcodeproj`.
2. Select the **Hashiya** scheme and **Any iOS Device (arm64)**, then **Product → Archive**.
3. In the Organizer: **Distribute App → App Store Connect → Upload**, with automatic signing.
4. The build appears under TestFlight after processing (usually 10–30 minutes). There's no encryption question: `ITSAppUsesNonExemptEncryption` is already `false`.
5. **TestFlight:** add yourself as an internal tester and install on a real iPhone. Try search, save, status changes, library search, and the share extension from Safari.

### Fill in the listing (App Store Connect → the app)

All text is in `docs/store/metadata.md`.

- **App Information:** categories Education and Reference, content rights (the app shows third-party metadata from OpenAlex, which is CC0), age rating (answer "None" or "No" throughout, giving 4+).
- **Pricing and Availability:** Free, all countries.
- **App Privacy:** privacy policy URL, then the answers in `docs/store/metadata.md` → App Privacy.
- **Version 0.1.0 → English (U.S.) and Arabic:**
  - Screenshots: iPhone 6.9" from `docs/store/app-store/<lang>/`, in order 1 to 4.
  - Promotional text, description, keywords, support URL and copyright.
- **iPad:** 0.1.0 and 0.2.0 are iPhone-only. From the next version the app runs on iPad (`TARGETED_DEVICE_FAMILY: "1,2"` in `ios/project.yml`), so App Store Connect asks for 13" iPad screenshots too: upload `docs/store/app-store-ipad/<lang>/`, in order 1 to 6.
- **Build:** choose the TestFlight build.
- **App Review Information:** no sign-in required; paste the review notes from `metadata.md`; add your phone and email.
- **Version release:** "Manually release this version", so you choose the launch moment.

Then **Add for Review → Submit**. Reviews usually take 1–2 days.

## 2. Google Play

### One-time setup

1. **Upload key.** Create it once, keep it outside the repo, and back it up with its passwords (a password manager is ideal):
   ```bash
   keytool -genkeypair -v -keystore ~/keys/hashiya-upload.jks -alias upload \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. **Point the build at it** in `android/local.properties` (git-ignored):
   ```properties
   UPLOAD_STORE_FILE=/Users/<you>/keys/hashiya-upload.jks
   UPLOAD_STORE_PASSWORD=...
   UPLOAD_KEY_ALIAS=upload
   UPLOAD_KEY_PASSWORD=...
   ```
3. **Create the app** in the Play Console → Create app:
   - Name `Hashiya: Research Papers`, default language English (United States).
   - App, Free.
   - Accept the declarations.
4. **Play App Signing:** keep the default (Google manages the app signing key). Your upload key only signs what you upload, and it can be reset through Play support if lost.

### Build

```bash
cd android
./gradlew :app:bundleRelease
```
The signed bundle is `android/app/build/outputs/bundle/release/app-release.aab`. Without the `UPLOAD_*` entries the bundle is unsigned, and Play rejects it.

### Fill in the listing (Play Console → the app)

All text is in `docs/store/metadata.md`.

- **App content:**
  - Privacy policy URL.
  - Ads: No.
  - App access: all functionality available.
  - Content rating: IARC questionnaire, Reference/News/Educational category, "No" throughout.
  - Target audience: 18+.
  - Data safety: crash logs, diagnostics, and device or other IDs collected, not shared, optional (see `docs/store/metadata.md`).
  - Government app: No. Financial features: None. Health: None.
- **Main store listing, English:**
  - App name, short and full descriptions.
  - App icon: `android/app/src/main/ic_launcher-playstore.png` (512 px).
  - Phone screenshots from `docs/store/play-store/en/`.
  - 7" and 10" tablet screenshots from `docs/store/play-store-tablet/en/`.
  - Feature graphic: `docs/store/play-store/feature-graphic.png` (1024 × 500).
- **Store listing, Arabic:** Translations → add Arabic → the Arabic text and the `docs/store/play-store/ar/` and `docs/store/play-store-tablet/ar/` screenshots.
- **Store settings:** category Education, contact email.

### Testing and release

1. **Internal testing** → Create release → upload the `.aab` → release notes → roll out. Install from the opt-in link and try the same flows as on iOS, plus sharing a link from Chrome.
2. **Closed testing:** personal developer accounts created after 13 November 2023 must run a closed test with **at least 12 testers opted in for 14 continuous days** before they can apply for production. Organization accounts skip this step. Create a closed track, add testers by email list or Google Group, upload the same bundle, and keep 12+ testers opted in for the full 14 days.
3. **Production:** after the closed test (or directly for organization accounts), Production → Create release → promote the tested bundle → roll out, optionally as a staged rollout (e.g. 20%). Reviews usually take a few hours to a few days.

## Crashlytics (Android)

Release builds send crash reports through Firebase Crashlytics, only while Settings → Send crash reports is on. Firebase also initializes in debug builds (its content provider starts it), but debug builds, unit tests and UI tests never enable collection, so nothing is sent. Crashlytics may keep crash files on the device that are never uploaded. Android usage statistics (Firebase Analytics) come in a separate change; see the analytics spec.

### One-time setup (console.firebase.google.com)

1. Create the Firebase project (`hashiya-research`) and turn Google Analytics off for it.
2. Add an Android app with the package `com.etatech.hashiya`, download `google-services.json` and put it at `android/app/google-services.json`. It is committed on purpose: the repository is public and the API key is restricted to this app in Google Cloud.
3. Crashlytics → Enable. R8 is off, so no mapping file is needed and frames are readable. If R8 is turned on later, the Crashlytics Gradle plugin uploads the mapping.
4. Restrict the API key, because the key in the committed `google-services.json` is public. In the Google Cloud console go to APIs & Services → Credentials, open the "Android key (auto created by Firebase)" for project `hashiya-research`, and under Application restrictions choose Android apps. Add package `com.etatech.hashiya` with the SHA-1 of:
   - the upload key: `keytool -list -v -keystore <UPLOAD_STORE_FILE from local.properties> -alias <UPLOAD_KEY_ALIAS>`
   - Play's app-signing key: Play Console → Test and release → App integrity → App signing.
5. Add the same SHA-1s to the Android app in Firebase project settings.
6. The iOS key is restricted the same way, with the iOS apps restriction and bundle ID `com.etatech.hashiya`. The iOS app relies on it.

### Check before a release that touches crash reporting

1. Install the release build (`./gradlew :app:installRelease`, signed) with Send crash reports on, and launch the app first: the test-crash broadcast only works while the app is running.
2. `adb shell am broadcast -a com.etatech.hashiya.TEST_CRASH -p com.etatech.hashiya`
3. Reopen the app: reports are sent on the next launch. In the Crashlytics console the crash appears with readable frames and the keys `screen`, `language`, `librarySizeBucket`, `backupInProgress`.
4. Turn the switch off, repeat steps 2 and 3, and confirm nothing new arrives.

## Crashlytics and Analytics (iOS)

Release builds configure Firebase (project `hashiya-research`, app `1:10078456816:ios:b46501427f2408bcb12b35`); Debug
builds, tests and the Share Extension never do. Settings → Privacy has **Send crash reports** and **Share usage
statistics**, both on by default.

### One-time setup
1. Google Cloud → Credentials: restrict the iOS API key to iOS apps, bundle id `com.etatech.hashiya`.
2. Firebase console → Crashlytics → Enable (if not already). dSYMs upload from the Release build's script phase.
3. Firebase console → Project settings → Integrations → Google Analytics: link a GA4 property. In GA: data retention
   2 months; Google signals off; no Google Ads links.

### Before a release with this work
- [ ] The privacy-policy update (Usage statistics) is merged and live — **before** the build reaches TestFlight external
      testers or the App Store.
- [ ] App Privacy answers match `docs/store/metadata.md`; Xcode → Archive → Generate Privacy Report shows the same.
- [ ] Test crash: run the Release configuration from Xcode once with the argument `-hashiya-test-crash`, stop it, open
      the app from the Home Screen: it crashes after two seconds; reopen it; the crash appears in Crashlytics with
      readable frames and the keys `screen`, `language`, `librarySizeBucket`, `backupInProgress`.
- [ ] Analytics: run the Release configuration with `-FIRDebugEnabled`; Firebase DebugView shows the events with only
      the listed parameters (e.g. `search` with `category`), and no advertising id.
- [ ] With both switches off, nothing new arrives.

## Large screens (Android)

The Android app adapts to the window it has: a bottom bar on phones, a rail from 600 dp, list and detail side by side from 840 dp (or at a book-posture hinge), and keyboard and mouse support. The unit tests cover the layout rules at compact, medium, expanded and landscape-phone sizes, a half-open fold, continuity across resizes, and the keyboard and mouse paths; the screenshot tests include a 1280 × 800 tablet in English and Arabic, light and dark. What only a device or emulator shows, check before a release that touches layouts:

| Case | Where | Check |
| --- | --- | --- |
| Small phone | compact phone emulator | Unchanged from before: bar, sheets, Details as a screen |
| Tablet, portrait and landscape | Pixel Tablet emulator | Library and Search show two panes in landscape, one in portrait; rail on every screen |
| Foldable | 7.6" fold-in emulator (with outer display) | Open a paper folded, unfold: it moves into the pane; fold again: it's a screen. Half open in book posture: one pane each side of the hinge |
| Flip cover screen | a flip emulator's outer display | Search and the Library usable and scrolling |
| Free resize | Resizable emulator, desktop windowing | Drag across 600 and 840 dp: bar → rail → two panes, without losing the open paper or the reader's zoom. The rail's menu button expands and collapses it, and the choice survives the resize |
| Split screen | tablet at ½ and ⅓ | Everything works at each size |
| Large text | font size at maximum (200%) | Cards keep their Save button; nothing cut off |
| Keyboard and mouse | tablet or Chromebook with both | Ctrl+F, Ctrl+N, Ctrl+,, Esc; right-click a Library row and a Search result; Ctrl+scroll in the reader |

## Large screens (iPad)

The iOS app lays out by the window it has, not by device: compact width (iPhone, narrow iPad windows) keeps one stack per tab; regular width shows the list beside the paper, Search's preview beside its results, and the reader's notes beside the PDF. The UI tests run on an iPad simulator in CI (`IPadFlowTests`: panes, resizing a window, shortcuts and menus, a second window), and the whole UI suite passes on iPad locally. What only a device or simulator shows, check before a release that touches layouts:

| Case | Where | Check |
| --- | --- | --- |
| iPhone | iPhone simulator, iOS 18 and 26 | Unchanged: bottom tab bar, preview sheet, Details and the reader as screens |
| iPad, portrait and landscape | iPad Pro 13" simulator, full screen | Library and Search show two panes; the reader hides the list and its button brings it back |
| Resizable windows | iPad in Windowed Apps (Settings → Multitasking & Gestures) | Drag a window across the compact/regular width with a paper and its reader open: neither closes, and the list comes back when wide |
| Stage Manager, Split View | iPad | Smallest window (375 pt) and short windows work; window controls don't cover any button |
| Several windows | iPad | "Open in New Window" from a Library row; change a status in one window, the other follows; close one window, the other still saves |
| Keyboard and pointer | iPad with a keyboard (Simulator → I/O → Keyboard) | ⌘N, ⌘1, ⌘2, ⌘,; ⌘F in the reader; right-click a row and a result |
| Arabic | iPad in Arabic | Panes mirror; the list sits on the right |

## Forcing an update

Both apps read `app-config.json` from the [Hashiya-Privacy-Policy](https://github.com/FadyFouad/Hashiya-Privacy-Policy) repo on every launch and every return to the foreground. A build lower than its platform's minimum shows a full-screen "Update required" screen whose button opens the store page.

1. Find the first good build number: `versionCode` on Android, the build (`CURRENT_PROJECT_VERSION`) on iOS.
2. In `app-config.json`, set `android.minimumVersionCode` or `ios.minimumBuild` to it. Before the first iOS block, replace the placeholder `id0000000000` in `ios.storeUrl` with the real App Store ID.
3. Check the number twice: a minimum above every released build blocks everyone. Then push.
4. GitHub Pages caches the file for 10 minutes, so it takes effect within about 10 minutes and the user's next return to the app.

If the file can't be read (offline, a typo in the JSON, GitHub down), nobody is blocked. To undo a block, lower the number and push; blocked users get back in after restarting the app.

## OpenAlex quota

Without a personal key, each install searches on the shared route — the built-in key, or a proxy once one is set — up
to a daily cap per device, then without a key, then shows "Daily search limit reached" with the reset time. Lookups
by id or DOI are free and always go out. Searches are cached on the device for 24 hours.

Usage of the built-in key: openalex.org → Settings → API (budget used today, resets at midnight UTC).

### The `openAlex` section of `app-config.json`

In FadyFouad/Hashiya-Privacy-Policy. Optional: the defaults apply without it; each field is checked on its own.

```json
"openAlex": { "dailyDeviceCalls": 60, "maxPagesPerQuery": 8, "baseUrl": null }
```

- `dailyDeviceCalls` (0–1000): searches and filter lists per device per UTC day on the shared route; 0 turns it off.
- `maxPagesPerQuery` (1–40): pages of 25 one search can load.
- `baseUrl` (`https://` or null): the proxy for the shared route.

Apps read it at launch; new values apply to the next search.

### Switching to a proxy

The proxy must:

1. Accept the same paths and query parameters as `https://api.openalex.org` (at least `GET /works` and
   `GET /works/{id}`) and return OpenAlex's bodies and status codes unchanged.
2. Add the OpenAlex key itself (the apps send none to it).
3. Pass through `X-RateLimit-Remaining`, `X-RateLimit-Reset` and `Retry-After`, and answer `429` with
   `X-RateLimit-Remaining: 0` when a client's budget is used up.
4. Limit by network address, keep no request logs, and store nothing about clients beyond short-lived counters.

Then:

1. Add to the privacy policy (English and Arabic): search requests pass through Hashiya's server, which sees network
   addresses and keeps no logs. Check the App Store privacy answers and Play's Data safety form against it.
2. Set `baseUrl` in `app-config.json`. Requests with a personal key, and keyless ones, still go straight to OpenAlex.
3. If the proxy fails (unreachable or 5xx), apps fall back to keyless requests, so search keeps working.

## Each release

1. Bump the version:
   - iOS: `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` in `ios/project.yml`.
   - Android: `versionName` and `versionCode` in `android/app/build.gradle.kts`.
   - Build numbers and version codes must always increase.
2. Move the `[Unreleased]` entries in `CHANGELOG.md` under the new version and its build. Then update "What's New" / release notes in `docs/store/metadata.md` from it, and run the metadata checker.
3. If the UI changed, update the screenshots (`docs/store/README.md`).
4. Archive and upload the iOS build, and build and upload the Android bundle; test through TestFlight and internal testing (and the large-screen table above when layouts changed), then submit.
5. Tag the release: `git tag v0.1.0 && git push origin v0.1.0`.
