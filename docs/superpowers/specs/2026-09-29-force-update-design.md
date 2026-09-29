# Force update — Design

- **Date:** 2026-09-29
- **Status:** Awaiting review
- **Scope:** Both apps (Android and iOS), before the first store release, 0.1.0 (build 1)

## 1. Context

Hashiya has no backend. Once 0.1.0 is in users' hands, there is no way to stop an old version from running. That matters if a release has a serious bug, or breaks because OpenAlex or the app's built-in API key changes. A version can only be forced to update if it already contains the check, so the check has to ship in the first release.

### Decisions made during brainstorming

| Topic | Decision |
|---|---|
| Purpose | An emergency safety net, used rarely. No "a new version is available" prompts. |
| Where the rule lives | A JSON file on the existing GitHub Pages site (`Hashiya-Privacy-Policy` repo). No SDK, and nothing collected. |
| What is compared | Build numbers: Android `versionCode`, iOS `CFBundleVersion`. Both are integers the stores require to increase. |
| What a blocked user sees | A full-screen "Update required" screen with an Update button to the store; nothing else is usable. |
| First version | 0.1.0, build 1, on both platforms. |

## 2. Goals and non-goals

### Goals

1. Raising the minimum build in the JSON file blocks every older build on that platform, within about 10 minutes (GitHub Pages' cache time) and the next time the app starts or returns to the foreground.
2. A blocked build shows only the Update required screen, in English or Arabic, whose button opens the app's store page.
3. When the check can't complete (offline, GitHub unreachable, a bad status, bad or incomplete JSON), the app works normally. The check never blocks by mistake.
4. The check sends nothing about the user: an anonymous GET with no API key, no identifiers and no query.

### Non-goals

- Optional "update available" prompts, or comparing with the store's latest version.
- Messages that change per release (the text is fixed in the app).
- Google Play's in-app update API.
- Checking from the iOS Share Extension or the Android share target (the next app launch shows the block).
- Caching the last result for offline use (an offline user is never blocked).

## 3. The config file

`app-config.json` at the root of the `Hashiya-Privacy-Policy` repo, served at `https://fadyfouad.github.io/Hashiya-Privacy-Policy/app-config.json`:

```json
{
  "android": {
    "minimumVersionCode": 1,
    "storeUrl": "https://play.google.com/store/apps/details?id=com.etatech.hashiya"
  },
  "ios": {
    "minimumBuild": 1,
    "storeUrl": "https://apps.apple.com/app/id0000000000"
  }
}
```

- **Rule:** a build is blocked when its build number is lower than its platform's minimum. Equal or higher is allowed.
- **Store link:** comes from the file, not the app, because the iOS App Store ID only exists once the App Store Connect record is created. Replace the placeholder ID before raising the iOS minimum.
- **Unknown keys are ignored**, so fields can be added later without breaking old builds.
- **A platform entry that is missing, or has no minimum or no store link,** counts as "no block".
- **Starting values:** both minimums are 1, so 0.1.0 (build 1) blocks nothing.

## 4. Android

### 4.1 Network (`core:network`)

- `AppConfigDataSource`: fetches and parses the file.
  - Its own `OkHttpClient` with 5-second connect and read timeouts, no API-key interceptor and no logging of the body, like `OkHttpArxivDataSource`.
  - Parsed with the module's kotlinx.serialization `Json` (`ignoreUnknownKeys = true`) into `NetworkAppConfig(android: NetworkPlatformConfig?)`, where `NetworkPlatformConfig(minimumVersionCode: Long?, storeUrl: String?)`.
  - Throws on transport or HTTP errors; the repository turns every failure into "no block".
- `APP_CONFIG_URL` constant beside `OPENALEX_BASE_URL`, provided through `NetworkModule`.

### 4.2 Repository (`core:data`)

- `AppUpdateRepository` interface: `suspend fun requiredUpdate(currentVersionCode: Long): RequiredUpdate?`.
- `RequiredUpdate(storeUrl: String)` in `core:model`.
- `ConfigAppUpdateRepository`: returns `RequiredUpdate(storeUrl)` only when the config has both fields, the store link is an `https://` URL, and `currentVersionCode < minimumVersionCode`. Any failure (network, HTTP status, parsing) returns `null`; coroutine cancellation is rethrown.
- Bound in `DataModule`; a `FakeAppUpdateRepository` in `core:testing`.

### 4.3 App (`app`)

- `AppUpdateViewModel` (Hilt): exposes `requiredUpdate: StateFlow<RequiredUpdate?>` and `fun check()`. It reads the installed `versionCode` with `PackageInfoCompat.getLongVersionCode`. Once blocked, it stays blocked for the life of the process: a later failed check never unblocks.
- `MainActivity` calls `check()` on every `ON_START` (launch and each return to the foreground).
- `HashiyaApp` gains a `requiredUpdate: RequiredUpdate?` parameter. When it is non-null, it shows `UpdateRequiredScreen` instead of the navigation scaffold.
- `UpdateRequiredScreen`: centred app glyph, title, message, and a filled "Update" button that opens `storeUrl` with `Intent.ACTION_VIEW`. No top bar, no navigation. Back leaves the app as usual.

## 5. iOS

### 5.1 Network (`HashiyaNetwork`)

- `AppConfigClient`: its own `URLSession` with a 5-second timeout and no API key, like `ArxivTitleClient`. It decodes `AppConfig(ios: PlatformConfig?)`, where `PlatformConfig(minimumBuild: Int?, storeUrl: String?)`, and ignores unknown keys (the default for `Decodable`).

### 5.2 Repository (`HashiyaData`)

- `AppUpdateRepository` protocol: `func requiredUpdate(currentBuild: Int) async -> RequiredUpdate?`, where `RequiredUpdate` holds `storeURL: URL`.
- `ConfigAppUpdateRepository`: the same rule and the same "any failure means no block" behaviour. A `storeUrl` that isn't an `https://` URL also means no block.
- A fake in `HashiyaTesting`. `LiveDependencies` and `AppContainer` provide the live one; the UI-testing stubs provide one that never blocks.

### 5.3 App

- `AppUpdateModel` (`@Observable`, main actor): `requiredUpdate: RequiredUpdate?` and `func check() async`. It reads `CFBundleVersion` from the main bundle. Once blocked, it stays blocked for the life of the process.
- `RootView` calls `check()` from its existing `scenePhase` `.active` case, which runs at launch and on every return to the foreground. When `requiredUpdate` is set, it shows `UpdateRequiredView` instead of the tab view and the Settings sheet.
- `UpdateRequiredView`: the same layout as Android, with the button calling `openURL(storeURL)`.

## 6. Strings (both apps, both languages)

| Key | English | Arabic |
|---|---|---|
| Title | Update required | يلزم التحديث |
| Message | This version of Hashiya is no longer supported. Update to keep using it. Your saved papers stay on your device. | لم يعد هذا الإصدار من حاشية مدعومًا. حدّث التطبيق لمتابعة استخدامه، وستبقى أوراقك المحفوظة على جهازك. |
| Button | Update | تحديث |

Android: `app/src/main/res/values{,-ar}/strings.xml`. iOS: `Localizable.xcstrings` in the app target, read through `AppStrings`.

## 7. Version 0.1.0 (build 1)

- Android: `versionName = "0.1.0"`, `versionCode = 1`.
- iOS: `MARKETING_VERSION: "0.1.0"`, `CURRENT_PROJECT_VERSION: "1"` in `ios/project.yml`.
- `docs/release.md` says 0.1.0 wherever it says 1.0, and gains a section "Forcing an update": edit `app-config.json`, raise the platform's minimum to the first good build, push, and expect up to 10 minutes for the cache.

## 8. Privacy

- The check is one GET to GitHub Pages that contains nothing about the user; GitHub sees the IP address, like any website.
- The privacy policy (`index.html` in `Hashiya-Privacy-Policy`) gains this in "What the app sends, and to whom", in both languages.
- App Privacy ("Data Not Collected"), Data safety and the privacy manifests don't change, because nothing is collected, stored or linked to the user.

## 9. Testing

- **Rule (both platforms):** below the minimum blocks with the store link; equal and above don't; a missing platform, missing minimum, missing or non-`https` store link, bad JSON, an HTTP error and a network error all mean no block.
- **Data source / client:** parses the sample file above, and ignores an unknown extra key.
- **App state:** once blocked, a later failed check keeps the block (Android `AppUpdateViewModel`, iOS `AppUpdateModel`).
- **UI:**
  - Android: a Robolectric test that `HashiyaApp` with a `RequiredUpdate` shows only the Update required screen, and the button fires the store link. Roborazzi screenshots of the screen in English and Arabic, light and dark.
  - iOS: snapshot tests of `UpdateRequiredView` in the same four variants.
- Screenshot and snapshot baselines are recorded on CI, as for every other screen.

## 10. Acceptance criteria (on device)

1. With the file at 1/1, 0.1.0 (1) opens normally on both platforms.
2. Raising a platform's minimum to 2 shows the Update required screen on that platform after at most about 10 minutes and one return to the foreground; the other platform is unaffected.
3. The Update button opens the store page.
4. In Arabic, the screen is right-to-left and fully translated.
5. In airplane mode with the minimum raised, the app opens normally and the library works.
6. Lowering the minimum back lets the app through after the next restart.

## 11. Risks

- **The file is the only control.** A typo that raises the minimum above every released build blocks all users. The runbook says to double-check the number before pushing, and a mistake is fixed by pushing the corrected file.
- **GitHub Pages availability and caching.** An outage means no block (by design); cache delay means up to 10 minutes before a change reaches users.
- **CI is currently not running** (GitHub Actions billing). Screenshot and snapshot baselines for the new screen can only be recorded once it runs again; until then, those tests can't be verified.
