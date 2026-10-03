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
- **App Privacy:** privacy policy URL, then "Data Not Collected".
- **Version 0.1.0 → English (U.S.) and Arabic:**
  - Screenshots: iPhone 6.9" from `docs/store/app-store/<lang>/`, in order 1 to 4.
  - Promotional text, description, keywords, support URL and copyright.
- **iPad:** 0.1.0 is iPhone-only (`TARGETED_DEVICE_FAMILY: "1"` in `ios/project.yml`), so App Store Connect asks for iPhone screenshots only; iPad users can run the iPhone version. To add iPad later, set it back to `"1,2"` and add 13" iPad screenshots.
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
  - Data safety: no data collected or shared.
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

## Large screens (Android)

The Android app adapts to the window it has: a bottom bar on phones, a rail from 600 dp, list and detail side by side from 840 dp (or at a book-posture hinge), and keyboard and mouse support. The unit tests cover the layout rules at compact, medium, expanded and landscape-phone sizes, a half-open fold, continuity across resizes, and the keyboard and mouse paths; the screenshot tests include a 1280 × 800 tablet in English and Arabic, light and dark. What only a device or emulator shows, check before a release that touches layouts:

| Case | Where | Check |
| --- | --- | --- |
| Small phone | compact phone emulator | Unchanged from before: bar, sheets, Details as a screen |
| Tablet, portrait and landscape | Pixel Tablet emulator | Library and Search show two panes in landscape, one in portrait; rail on every screen |
| Foldable | 7.6" fold-in emulator (with outer display) | Open a paper folded, unfold: it moves into the pane; fold again: it's a screen. Half open in book posture: one pane each side of the hinge |
| Flip cover screen | a flip emulator's outer display | Search and the Library usable and scrolling |
| Free resize | Resizable emulator, desktop windowing | Drag across 600, 840 and 1200 dp: bar → rail → two panes → expanded rail, without losing the open paper or the reader's zoom |
| Split screen | tablet at ½ and ⅓ | Everything works at each size |
| Large text | font size at maximum (200%) | Cards keep their Save button; nothing cut off |
| Keyboard and mouse | tablet or Chromebook with both | Ctrl+F, Ctrl+N, Ctrl+,, Esc; right-click a Library row and a Search result; Ctrl+scroll in the reader |

## Forcing an update

Both apps read `app-config.json` from the [Hashiya-Privacy-Policy](https://github.com/FadyFouad/Hashiya-Privacy-Policy) repo on every launch and every return to the foreground. A build lower than its platform's minimum shows a full-screen "Update required" screen whose button opens the store page.

1. Find the first good build number: `versionCode` on Android, the build (`CURRENT_PROJECT_VERSION`) on iOS.
2. In `app-config.json`, set `android.minimumVersionCode` or `ios.minimumBuild` to it. Before the first iOS block, replace the placeholder `id0000000000` in `ios.storeUrl` with the real App Store ID.
3. Check the number twice: a minimum above every released build blocks everyone. Then push.
4. GitHub Pages caches the file for 10 minutes, so it takes effect within about 10 minutes and the user's next return to the app.

If the file can't be read (offline, a typo in the JSON, GitHub down), nobody is blocked. To undo a block, lower the number and push; blocked users get back in after restarting the app.

## Each release

1. Bump the version:
   - iOS: `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` in `ios/project.yml`.
   - Android: `versionName` and `versionCode` in `android/app/build.gradle.kts`.
   - Build numbers and version codes must always increase.
2. Update "What's New" / release notes in `docs/store/metadata.md` and run the metadata checker.
3. If the UI changed, update the screenshots (`docs/store/README.md`).
4. Archive and upload the iOS build, and build and upload the Android bundle; test through TestFlight and internal testing (and the large-screen table above when layouts changed), then submit.
5. Tag the release: `git tag v0.1.0 && git push origin v0.1.0`.
