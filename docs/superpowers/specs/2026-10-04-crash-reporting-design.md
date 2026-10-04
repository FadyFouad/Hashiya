# Crash reporting — Design

- **Date:** 2026-10-04
- **Status:** Approved in brainstorming; awaiting spec review
- **Scope:** Both platforms, plus an update to the existing privacy policy. Android ships first, then iOS, each with its own plan and PR.

## 1. Context

Hashiya keeps the whole library on the device, and the backup work showed how easily a rare path (a restore, a migration, a failed PDF write) can go wrong without anyone noticing. Today there is no crash reporting: the store answers say "No, we do not collect data from this app … there is no Hashiya server, analytics or crash reporting" (`docs/store/metadata.md`), Play's Data safety says "No", and `PrivacyInfo.xcprivacy` declares no collected data. The free store dashboards (Xcode Organizer, Android vitals) see only a fraction of crashes on iOS (opted-in users) and give no context.

The repository is public.

### Decisions made during brainstorming

| Topic | Decision |
|---|---|
| Goal | Every crash, fast, with full stack traces and context. |
| Service | Firebase Crashlytics on both platforms. No Firebase Analytics, no ads identifiers, no user IDs. |
| Consent | On by default, with a **Send crash reports** switch in Settings to turn it off. |
| Content | Crashes (and Android ANRs), a few coarse custom keys, and non-fatal reports from the data-safety paths. No breadcrumbs. |
| Never sent | Titles, DOIs, OpenAlex ids, search text, notes, collection names, file paths, the API key, URLs, error messages. |
| Config files | `google-services.json` and `GoogleService-Info.plist` are committed; the API keys are restricted in Google Cloud. |
| Architecture | A small reporting interface per platform; Firebase only in the app target. |
| Builds | Collection only in release builds; never in debug builds, unit, UI or snapshot tests. |
| Privacy policy | The existing page, https://fadyfouad.github.io/Hashiya-Privacy-Policy/ (repo FadyFouad/Hashiya-Privacy-Policy, English and Arabic), is updated; contact fady.fouad.a@gmail.com. |

## 2. Goals and non-goals

### Goals

1. Release builds report crashes (and ANRs on Android) to Crashlytics with readable stack traces.
2. A defined set of non-fatal failures is reported with the place it happened and the error's type, and nothing a user wrote or read.
3. A user can turn reporting off in Settings, and an opt-out applies from launch and drops reports not yet sent.
4. The store answers, the privacy manifest and the privacy policy describe exactly what is collected.

### Non-goals

- Analytics, breadcrumbs, performance monitoring, Remote Config.
- Crash reporting in the iOS Share Extension.
- A consent prompt at first launch.
- Linking reports to a person or account (the app has no accounts).

## 3. Architecture

Firebase appears only in the app target on each platform. Everything else depends on a small interface, so the data layer, features and tests never import Firebase.

### Android

- New pure-Kotlin module `:core:crash` (no Android, no Firebase):

```kotlin
interface CrashReporter {
    fun setEnabled(enabled: Boolean)
    fun setKey(key: CrashKey, value: String)
    fun recordNonFatal(error: Throwable, site: CrashSite)
}
object NoOpCrashReporter : CrashReporter
enum class CrashKey { Screen, Language, LibrarySizeBucket, BackupInProgress }
enum class CrashSite { Migration, DatabaseOpen, Restore, Export, PdfStore, UnexpectedUiError }
```

- `FirebaseCrashReporter` lives in `:app`; Hilt binds it in release builds and `NoOpCrashReporter` in debug builds.
- `FakeCrashReporter` in `:core:testing` records every call.

### iOS

- New SwiftPM target `HashiyaDiagnostics` (no dependencies) with `protocol CrashReporting: Sendable`, `NoCrashReporting`, `CrashKey`, `CrashSite` — the same members as Android.
- `FirebaseCrashReporting` lives in the `Hashiya` app target; `AppContainer` passes it into `LiveDependencies` in release builds. Everything that takes a reporter defaults to `NoCrashReporting()`, so existing call sites and tests don't change.
- A fake in `HashiyaTesting` records every call.

### Closed lists

`CrashKey` and `CrashSite` are closed enums: a caller can't attach free text, and adding a key or site is a reviewed code change.

## 4. Consent

- **Settings:** a new **Privacy** section with a **Send crash reports** switch, on by default, a footer — "Crash details and app errors help fix bugs. They never include your papers, notes or searches." — and a **Privacy policy** link. English and Arabic.
- **Storage:** Android DataStore `crashReportsEnabled` (default true); iOS app `UserDefaults` (default true). The Share Extension never reads it.
- **Off until decided:** collection is disabled in the manifest (`firebase_crashlytics_collection_enabled=false`) and Info.plist (`FirebaseCrashlyticsCollectionEnabled=NO`). At launch the app calls `setEnabled(isReleaseBuild && preference)`.
- **Turning it off** stops collection at once and calls `deleteUnsentReports()`. **Turning it on** resumes collection from then.

## 5. What is reported

### Automatically

Crashes on both platforms; ANRs on Android.

### Custom keys

| Key | Values | Set by |
|---|---|---|
| `screen` | `library`, `search`, `details`, `reader`, `settings`, `restore`, `export` | Android: navigation destination changes in `HashiyaApp`. iOS: RootView and each screen's `onAppear`. |
| `language` | `en`, `ar`, `system` | at launch and when the language changes |
| `librarySizeBucket` | `0`, `1-50`, `51-500`, `501-5000`, `5000+` | after the library first loads at launch |
| `backupInProgress` | `none`, `export`, `restore` | `LibraryBackup` when an export or restore starts and ends (also on failure and cancel) |

### Non-fatal reports

Each report carries the site, the error's type and code, and the stack trace. The error's message is never sent.

| Site | Reported when |
|---|---|
| `migration` | a database migration throws (Room; GRDB `migrator`) |
| `databaseOpen` | the database fails to open (Room; App Group unavailable or file coordination fails on iOS) |
| `restore` | a restore ends in `writeFailed` or `unreadable` — not `noSpace` or `busy` |
| `export` | an export ends in `writeFailed` |
| `pdfStore` | a PDF write fails for a reason other than "not a PDF" or "too large" |
| `unexpectedUiError` | the generic catch blocks in the Settings export and Restore view models |

To keep messages out, the Firebase implementation reports a new error built from the site, the original error's type name and code, and the original stack trace — never the original error object's message or user info.

## 6. Setup and build

### Firebase console (the user's part)

1. Create project **Hashiya** with Google Analytics off.
2. Add Android app `com.etatech.hashiya`; download `google-services.json`.
3. Add iOS app `com.etatech.hashiya`; download `GoogleService-Info.plist`.
4. Restrict the API keys in Google Cloud → Credentials: Android to the package and the upload and Play app-signing SHA-1 fingerprints; iOS to the bundle ID.
5. Enable Crashlytics.

These steps go into `docs/release.md`.

### Android

- `com.google.gms.google-services` and `com.google.firebase.crashlytics` Gradle plugins on `:app` only, via the version catalog; `firebase-crashlytics` from the Firebase BoM.
- `android/app/google-services.json` committed.
- R8 is off (`optimization { enable = false }`), so stack traces are readable without a mapping file; if R8 is turned on later, the Crashlytics plugin uploads the mapping.

### iOS

- `firebase-ios-sdk` in `ios/project.yml`, exact version, product `FirebaseCrashlytics`, linked to the app target only.
- `ios/Hashiya/GoogleService-Info.plist` committed.
- A post-build script phase runs Crashlytics' symbol upload for Release builds; Release keeps `DEBUG_INFORMATION_FORMAT = dwarf-with-dsym`.
- `FirebaseApp.configure()` only in release launches; never when hosting snapshot tests or under `-ui-testing`.

### CI

Nothing to configure: the config files are committed, Firebase may initialize, but debug builds and tests never enable collection, so nothing is sent, and CI still compiles the Firebase code paths.

## 7. Privacy and store answers

- **App Store → App Privacy:** Crash Data, Other Diagnostic Data, and Device ID (the Firebase installation ID) — each not linked to identity, not used for tracking, purpose App Functionality. Check against Firebase's current Apple data-disclosure page at release.
- **`PrivacyInfo.xcprivacy` (app):** collected types `NSPrivacyCollectedDataTypeCrashData` and `NSPrivacyCollectedDataTypeOtherDiagnosticData`, not linked, no tracking, App Functionality; `NSPrivacyTracking` stays false. The Share Extension's manifest is unchanged.
- **Play → Data safety:** collected — App info and performance (crash logs, diagnostics) and Device or other IDs; not shared; encrypted in transit; optional; for app functionality and stability.
- **Privacy policy:** the existing page https://fadyfouad.github.io/Hashiya-Privacy-Policy/ (repo FadyFouad/Hashiya-Privacy-Policy) gains a "Crash reports" section and catches up with notes, collections, PDFs and backups, in English and Arabic (its PR #1). It is merged when the first version with crash reporting is released. Settings links to it (`#ar` for Arabic); the store listings already point there.

## 8. Testing

### Unit tests (fake reporter)

- The switch defaults on; toggling calls `setEnabled` and, when off, deletes unsent reports.
- Debug builds never enable collection; release builds follow the preference.
- Each non-fatal site reports once with the right `CrashSite`; `noSpace`, `busy`, `notPDF` and `tooLarge` report nothing.
- `backupInProgress` returns to `none` after success, failure and cancel; size-bucket boundaries; `screen` follows navigation.
- A failure whose message contains a title and a file path is reported without either string reaching the reporter.

### UI and snapshot tests

- Settings with the Privacy section, English and Arabic, light and dark.
- A UI test: turn the switch off, relaunch, it stays off.

### Manual checks (release builds)

- Android: a release build sends a test crash that appears in Crashlytics with readable frames.
- iOS: a TestFlight build does the same, with dSYMs uploaded.
- With the switch off, a crash sends nothing.
- The test crash trigger never reaches public users: Android accepts it only from `debuggable` builds or an internal-test flag; iOS only in TestFlight builds via a launch argument. The plans define the exact mechanism.

## 9. Delivery

1. Policy update: PR #1 in FadyFouad/Hashiya-Privacy-Policy, merged at release.
2. Android PR: `:core:crash`, Firebase in `:app`, the Settings switch, non-fatal sites, store answers for Play.
3. iOS PR: `HashiyaDiagnostics`, Firebase in the app target, the switch, non-fatal sites, privacy manifest, App Store answers.

The user creates the Firebase project and provides the two config files before the platform PRs.
