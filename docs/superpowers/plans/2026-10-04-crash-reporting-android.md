# Crash Reporting (Android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Release builds of the Android app report crashes, ANRs and a defined set of non-fatal failures to Firebase Crashlytics, with a Settings switch (on by default) to turn it off, and nothing a user wrote or read ever leaves the device.

**Architecture:** A pure-Kotlin `:core:crash` module defines `CrashReporter`, the closed `CrashKey`/`CrashSite` enums, the sanitizer and the library-size buckets. The data layer and features depend only on it; `:app` binds `NoOpCrashReporter` in debug builds and `FirebaseCrashReporter` in release builds. Firebase (Gradle plugins, BoM, `google-services.json`) lives in `:app` only and is added in the last task, so every earlier task builds without the Firebase config file.

**Tech Stack:** Kotlin, Hilt, DataStore, Jetpack Compose, Navigation Compose, Firebase Crashlytics (BoM), Robolectric/Roborazzi tests.

**Spec:** `docs/superpowers/specs/2026-10-04-crash-reporting-design.md`

## Global Constraints

- Firebase only in `:app`. `:core:*` and `:feature:*` never depend on Firebase.
- No Firebase Analytics, no ads identifiers, no `setUserId`.
- Collection only in release builds; debug builds, unit, UI and screenshot tests bind `NoOpCrashReporter`.
- `firebase_crashlytics_collection_enabled=false` in the manifest; the app enables collection at launch only when the build is release and the preference is on.
- Turning the switch off calls `setCrashlyticsCollectionEnabled(false)` and `deleteUnsentReports()`.
- Custom keys and sites are closed enums. Key values: `screen` ∈ library, search, details, reader, settings, restore; `language` ∈ en, ar, system; `librarySizeBucket` ∈ 0, 1-50, 51-500, 501-5000, 5000+; `backupInProgress` ∈ none, export, restore.
- A non-fatal report carries the site, the error's class name and its stack trace — never `message`, `localizedMessage` or causes' messages.
- Non-fatal sites on Android: `Restore` (a restore ending in WriteFailed or Unreadable), `Export` (an export ending in WriteFailed), `PdfStore` (a `PdfWriteException` while downloading or attaching). Not reported: NoSpace, Busy, NotPdf, TooLarge, cancellation.
- Strings in English and Arabic (`values-ar`), Arabic plurals six forms; `·` never between numbers in Arabic.
- Commits authored `Fady <fady.fouad.a@gmail.com>`; no AI attribution. Run Gradle from `android/`.

## Review Focus

1. **A release build started with the switch off** must never send anything, including a crash during launch before Settings is opened. (Task 4 `startupLeavesCollectionOffWhenThePreferenceIsOff`.)
2. **A paper title or file path inside an exception message** must not reach Crashlytics through any site, including through the exception's cause. (Task 1 `sanitizedDropsMessagesAndCauses`.)
3. **A cancelled or failed export/restore** must set `backupInProgress` back to `none`. (Task 3 tests.)
4. **The app with no `google-services.json`** (forks, fresh clones before Task 5) — Tasks 1–4 must build and test without it. (Each task's build step.)
5. **Debug and CI builds** must never initialize Firebase or send reports. (Task 5 `debugBuildsBindTheNoOpReporter`.)

---

### Task 1: `:core:crash` — the interface, closed lists, sanitizer and buckets

**Files:**
- Modify: `android/settings.gradle.kts` (`include(":core:crash")`)
- Create: `android/core/crash/build.gradle.kts`, `android/core/crash/src/main/kotlin/com/etatech/hashiya/core/crash/CrashReporter.kt`, `.../Sanitized.kt`, `.../LibrarySizeBucket.kt`
- Create: `android/core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeCrashReporter.kt`; modify `android/core/testing/build.gradle.kts` (`api(project(":core:crash"))`)
- Test: `android/core/crash/src/test/kotlin/com/etatech/hashiya/core/crash/SanitizedTest.kt`, `LibrarySizeBucketTest.kt`

**Interfaces (produces):**

```kotlin
package com.etatech.hashiya.core.crash
interface CrashReporter { fun setEnabled(enabled: Boolean); fun setKey(key: CrashKey, value: String); fun recordNonFatal(error: Throwable, site: CrashSite) }
object NoOpCrashReporter : CrashReporter
enum class CrashKey(val id: String) { Screen("screen"), Language("language"), LibrarySizeBucket("librarySizeBucket"), BackupInProgress("backupInProgress") }
enum class CrashSite(val id: String) { Migration("migration"), DatabaseOpen("databaseOpen"), Restore("restore"), Export("export"), PdfStore("pdfStore"), UnexpectedUiError("unexpectedUiError") }
class ReportedFailure(...) : Exception   // what actually gets sent
fun sanitized(error: Throwable, site: CrashSite): ReportedFailure
fun librarySizeBucket(papers: Int): String
// FakeCrashReporter (core:testing): enabled: Boolean?, keys: Map<CrashKey, String>, nonFatals: List<Pair<Throwable, CrashSite>>, enabledCalls: List<Boolean>
```

- [ ] **Step 1: Module setup**

`android/core/crash/build.gradle.kts`:

```kotlin
plugins {
    id("hashiya.jvm.library")
}

dependencies {
    testImplementation(libs.junit)
}
```

(Use the same convention plugin id `:core:model` uses — check `android/core/model/build.gradle.kts`.) Add `include(":core:crash")` to `settings.gradle.kts` next to the other core modules.

- [ ] **Step 2: Write the failing tests**

`SanitizedTest.kt`:

```kotlin
package com.etatech.hashiya.core.crash

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SanitizedTest {
    @Test
    fun sanitizedDropsMessagesAndCauses() {
        val cause = IllegalStateException("Deep learning in /data/user/0/com.etatech.hashiya/files/pdfs/x.pdf")
        val error = IOException("Couldn't read 'Attention is all you need'", cause)

        val reported = sanitized(error, CrashSite.Restore)

        assertEquals("restore: java.io.IOException", reported.message)
        assertNull(reported.cause)
        assertEquals(error.stackTrace.toList(), reported.stackTrace.toList())
        val everything = reported.toString() + reported.stackTraceToString()
        assertFalse(everything.contains("Attention"))
        assertFalse(everything.contains("/data/user"))
    }

    @Test
    fun anonymousErrorsStillHaveAType() {
        val reported = sanitized(object : RuntimeException("secret") {}, CrashSite.Export)
        assertFalse(reported.message!!.contains("secret"))
    }
}
```

`LibrarySizeBucketTest.kt`:

```kotlin
package com.etatech.hashiya.core.crash

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySizeBucketTest {
    @Test
    fun boundaries() {
        mapOf(0 to "0", 1 to "1-50", 50 to "1-50", 51 to "51-500", 500 to "51-500", 501 to "501-5000", 5000 to "501-5000", 5001 to "5000+")
            .forEach { (papers, bucket) -> assertEquals("$papers", bucket, librarySizeBucket(papers)) }
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :core:crash:test`
Expected: compilation fails (`sanitized`, `librarySizeBucket` missing).

- [ ] **Step 4: Implement**

`CrashReporter.kt`:

```kotlin
package com.etatech.hashiya.core.crash

/**
 * Reports crashes' context and non-fatal failures. Only `:app` knows the service behind it; everything else depends on this.
 * Keys and sites are closed lists, so free text — titles, searches, notes, paths — can't be attached by accident.
 */
interface CrashReporter {
    /** Starts or stops collection. Stopping also drops reports not yet sent. */
    fun setEnabled(enabled: Boolean)

    fun setKey(key: CrashKey, value: String)

    /** Reports [error] without its message or causes (see [sanitized]). */
    fun recordNonFatal(error: Throwable, site: CrashSite)
}

/** Debug builds and tests: reports nothing. */
object NoOpCrashReporter : CrashReporter {
    override fun setEnabled(enabled: Boolean) = Unit

    override fun setKey(key: CrashKey, value: String) = Unit

    override fun recordNonFatal(error: Throwable, site: CrashSite) = Unit
}

enum class CrashKey(val id: String) {
    Screen("screen"),
    Language("language"),
    LibrarySizeBucket("librarySizeBucket"),
    BackupInProgress("backupInProgress"),
}

enum class CrashSite(val id: String) {
    Migration("migration"),
    DatabaseOpen("databaseOpen"),
    Restore("restore"),
    Export("export"),
    PdfStore("pdfStore"),
    UnexpectedUiError("unexpectedUiError"),
}
```

`Sanitized.kt`:

```kotlin
package com.etatech.hashiya.core.crash

/** What a non-fatal report sends: the site and the error's type, with its stack — never its message or causes. */
class ReportedFailure internal constructor(site: CrashSite, type: String, trace: Array<StackTraceElement>) :
    Exception("${site.id}: $type") {
    init {
        stackTrace = trace
    }

    // Keeps the reporter from capturing this constructor's frames instead of the original ones.
    override fun fillInStackTrace(): Throwable = this
}

/** Messages and causes can hold titles, searches or file paths, so only the class name and stack survive. */
fun sanitized(error: Throwable, site: CrashSite): ReportedFailure {
    val type = error::class.java.name
    return ReportedFailure(site, type, error.stackTrace)
}
```

(An anonymous class's `java.name` is like `…SanitizedTest$anonymousErrorsStillHaveAType$1` — fine: a type, not content.)

`LibrarySizeBucket.kt`:

```kotlin
package com.etatech.hashiya.core.crash

/** The library's size, coarse enough to say nothing about a person: 0, 1-50, 51-500, 501-5000, 5000+. */
fun librarySizeBucket(papers: Int): String = when {
    papers <= 0 -> "0"
    papers <= 50 -> "1-50"
    papers <= 500 -> "51-500"
    papers <= 5000 -> "501-5000"
    else -> "5000+"
}
```

`FakeCrashReporter.kt` (core:testing):

```kotlin
package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.crash.CrashKey
import com.etatech.hashiya.core.crash.CrashReporter
import com.etatech.hashiya.core.crash.CrashSite

/** Records every call, so tests can check exactly what would be reported. */
class FakeCrashReporter : CrashReporter {
    val enabledCalls = mutableListOf<Boolean>()
    val keys = mutableMapOf<CrashKey, String>()
    val keyHistory = mutableListOf<Pair<CrashKey, String>>()
    val nonFatals = mutableListOf<Pair<Throwable, CrashSite>>()

    override fun setEnabled(enabled: Boolean) {
        enabledCalls += enabled
    }

    override fun setKey(key: CrashKey, value: String) {
        keys[key] = value
        keyHistory += key to value
    }

    override fun recordNonFatal(error: Throwable, site: CrashSite) {
        nonFatals += error to site
    }
}
```

Make `FakeCrashReporter` thread-safe with `synchronized` blocks if the data-layer tests call it from IO threads (Task 3 does).

- [ ] **Step 5: Run the tests**

Run: `./gradlew :core:crash:test :core:testing:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/settings.gradle.kts android/core/crash android/core/testing
git commit -m "feat(android): crash reporter interface with closed keys and sites"
```

---

### Task 2: The Privacy section in Settings

**Files:**
- Modify: `android/core/datastore/.../UserPreferencesDataSource.kt`, `android/core/data/.../repository/UserPreferencesRepository.kt`, `android/core/testing/.../FakeUserPreferencesRepository.kt`
- Modify: `android/feature/settings/build.gradle.kts` (`implementation(project(":core:crash"))`), `SettingsViewModel.kt`, `SettingsScreen.kt`, `values/strings.xml`, `values-ar/strings.xml`
- Test: `UserPreferencesDataSourceTest.kt`, `SettingsViewModelTest.kt` (or a new `SettingsPrivacyViewModelTest.kt`), `SettingsContentTest.kt`, `SettingsScreenshotTest.kt`

**Interfaces:**
- Produces: `UserPreferencesRepository.crashReportsEnabled: Flow<Boolean>` (default true) and `suspend fun setCrashReportsEnabled(enabled: Boolean)`; `SettingsUiState.crashReportsEnabled: Boolean = true`; `SettingsViewModel(…, crashReporter: CrashReporter)` with `fun onCrashReportsChange(enabled: Boolean)`; `PRIVACY_POLICY_URL = "https://fadyfouad.github.io/Hashiya/privacy/"` (Arabic UI opens `…/privacy/ar`).

- [ ] **Step 1: Write the failing tests**

- `UserPreferencesDataSourceTest`: `crashReportsEnabled` emits `true` when nothing is stored; after `setCrashReportsEnabled(false)` it emits `false`; after `true` again, `true`.
- ViewModel: `uiState.crashReportsEnabled` reflects the repository; `onCrashReportsChange(false)` stores false and calls `crashReporter.setEnabled(false)` (use `FakeCrashReporter`); `onCrashReportsChange(true)` stores true and calls `setEnabled(true)`.
- Content test: the switch shows checked when `crashReportsEnabled = true`, toggling calls the callback with `false`; the footer and "Privacy policy" link are shown.

Write them in the style of the neighbouring tests in each file.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:datastore:testDebugUnitTest :feature:settings:testDebugUnitTest`
Expected: compilation fails.

- [ ] **Step 3: Implement**

- `UserPreferencesDataSource`: `booleanPreferencesKey("crash_reports_enabled")`; `val crashReportsEnabled: Flow<Boolean> = dataStore.data.map { it[CRASH_REPORTS_ENABLED] ?: true }`; `suspend fun setCrashReportsEnabled(enabled: Boolean)`.
- `UserPreferencesRepository` + `DataStoreUserPreferencesRepository` + `FakeUserPreferencesRepository`: pass through (the fake defaults to true).
- `SettingsViewModel`: inject `CrashReporter`; add `crashReportsEnabled` to the combined state (the existing `combine` already takes five flows — nest a `combine` or move to the vararg overload); 

```kotlin
fun onCrashReportsChange(enabled: Boolean) {
    crashReporter.setEnabled(enabled)
    viewModelScope.launch { preferences.setCrashReportsEnabled(enabled) }
}
```

  (Call `setEnabled` first so an opt-out takes effect before the write; the bound reporter is the no-op in debug builds, so the ViewModel doesn't need to know the build type.)
- `SettingsScreen`: a **Privacy** section after Backup — title, a row with a `Switch` (`toggleable` row, `Role.Switch`, like the export dialog's), the footer text in `bodySmall`/`onSurfaceVariant`, and a `TextButton` "Privacy policy" that opens the URL with `LocalUriHandler` (Arabic language → the `/ar` page). `SettingsContent` gets `onCrashReportsChange: (Boolean) -> Unit = {}`.
- Strings:

| Key | English | Arabic |
|---|---|---|
| `settings_privacy` | Privacy | الخصوصية |
| `settings_crash_reports` | Send crash reports | إرسال تقارير الأعطال |
| `settings_crash_reports_footer` | Crash details and app errors help fix bugs. They never include your papers, notes or searches. | تساعد تفاصيل الأعطال وأخطاء التطبيق على إصلاح المشكلات، ولا تتضمّن أبدًا أوراقك أو ملاحظاتك أو عمليات بحثك. |
| `settings_privacy_policy` | Privacy policy | سياسة الخصوصية |

- [ ] **Step 4: Run the tests and add the screenshot**

Add a `privacy` screenshot to `SettingsScreenshotTest` (`arabicText = "إرسال تقارير الأعطال"`, `wholeScreen = true` scrolled to the section if needed — follow the `storage` test). Existing Settings screenshots change (the screen is longer); baselines are recorded on CI.
Run: `./gradlew :core:datastore:testDebugUnitTest :core:data:testDebugUnitTest :feature:settings:testDebugUnitTest lintDebug`
Expected: PASS except new/changed screenshot baselines.

- [ ] **Step 5: Commit**

```bash
git add android
git commit -m "feat(android): a Send crash reports switch in Settings"
```

---

### Task 3: Non-fatal sites and the backup key in the data layer

**Files:**
- Modify: `android/core/data/build.gradle.kts` (`implementation(project(":core:crash"))`), `core/data/.../backup/ArchiveLibraryBackup.kt`, `core/data/.../di/BackupModule.kt`, `core/data/.../repository/RoomPdfRepository.kt`
- Test: `ArchiveLibraryBackupTest.kt`, `RoomPdfRepositoryTest.kt`

**Interfaces:**
- Consumes: `CrashReporter`, `CrashKey.BackupInProgress`, `CrashSite.Restore/Export/PdfStore` (Task 1).
- Produces: `ArchiveLibraryBackup(…, crashReporter: CrashReporter = NoOpCrashReporter)` (last parameter, after the existing test seams); `RoomPdfRepository` internal constructor gains `crashReporter: CrashReporter = NoOpCrashReporter` (last), and the `@Inject` constructor takes it. Hilt needs a `CrashReporter` binding — Task 4 adds it in `:app`; until then, for `:core:data` tests nothing changes because of the defaults.

- [ ] **Step 1: Write the failing tests**

In `ArchiveLibraryBackupTest` (construct with a `FakeCrashReporter`):

```kotlin
@Test fun aFailedMergeIsReportedAsRestore() // merge seam throws IllegalStateException → BackupException(WriteFailed) and exactly one nonFatal with CrashSite.Restore whose throwable is the IllegalStateException
@Test fun anUnreadableArchiveDuringStagingIsReportedAsRestore() // a PDF entry that makes the zip read throw an IOException → Unreadable + one Restore report
@Test fun noSpaceAndBusyAreNotReported() // NoSpace via usableSpace seam; nothing in nonFatals
@Test fun aFailedExportIsReportedAsExport() // writeArchive throws IOException (e.g. a work dir that can't be written) → WriteFailed + one Export report
@Test fun backupInProgressIsSetDuringWorkAndClearedAfterSuccessFailureAndCancel() // keyHistory shows Restore/Export then None for each outcome
```

In `RoomPdfRepositoryTest`:

```kotlin
@Test fun aPdfWriteFailureIsReportedAsPdfStore() // a PdfFileStore whose folder is a file (so stage can't create the temp file) → attach returns Unreadable and one PdfStore report
@Test fun notAPdfAndTooLargeAreNotReported()
```

Write each test fully, following the neighbouring tests' setup.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*ArchiveLibraryBackupTest' --tests '*RoomPdfRepositoryTest'`
Expected: compilation fails (no `crashReporter` parameter).

- [ ] **Step 3: Implement**

- `ArchiveLibraryBackup`:
  - At the start of `export` and `apply`: `crashReporter.setKey(CrashKey.BackupInProgress, "export" / "restore")`; in a `finally` around the whole operation: `setKey(BackupInProgress, "none")`.
  - At every place that converts a caught exception into `BackupException(BackupFailure.WriteFailed, e)` or `BackupException(BackupFailure.Unreadable, e)`, call `crashReporter.recordNonFatal(e, CrashSite.Restore)` (in `apply`/`open`) or `CrashSite.Export` (in `export`/`save`) first. Not for `NoSpace`, `Busy`, or cancellation. `open` failing on a file that isn't a backup (`OpenFailure`) is not reported — it's the user's file, not a bug.
- `RoomPdfRepository`: where a `PdfWriteException` is caught (the download `attempt` and `attach`), call `crashReporter.recordNonFatal(e, CrashSite.PdfStore)`.
- `BackupModule.provideLibraryBackup` and the `RoomPdfRepository` `@Inject` constructor take a `CrashReporter` from Hilt.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS. (`:app` won't compile under Hilt until Task 4 binds `CrashReporter` — run `:app` checks in Task 4.)

- [ ] **Step 5: Commit**

```bash
git add android/core/data
git commit -m "feat(android): report failed restores, exports and PDF writes without their content"
```

---

### Task 4: App wiring without Firebase — binding, startup, screen and language keys, test crash

**Files:**
- Modify: `android/app/build.gradle.kts` (`implementation(project(":core:crash"))`), `HashiyaApplication.kt`, `navigation/HashiyaApp.kt`, `AndroidManifest.xml`
- Create: `android/app/src/main/java/com/etatech/hashiya/crash/CrashModule.kt`, `crash/CrashStartup.kt`, `crash/TestCrashReceiver.kt`
- Test: `android/app/src/test/java/com/etatech/hashiya/crash/CrashStartupTest.kt`, `ScreenKeyTest.kt`

**Interfaces:**
- Produces: Hilt `@Provides @Singleton fun crashReporter(): CrashReporter` returning `NoOpCrashReporter` (Task 5 switches release builds to Firebase); `class CrashStartup(reporter, preferences, libraryBackup, languageTag: () -> String, isRelease: Boolean)` with `suspend fun run()`; `fun screenFor(destination: NavDestination): String?`.

- [ ] **Step 1: Write the failing tests**

`CrashStartupTest` (plain coroutine tests with fakes):
- `startupLeavesCollectionOffWhenThePreferenceIsOff`: release, preference false → `enabledCalls == [false]`.
- `releaseBuildWithThePreferenceOnEnablesCollection`: → `[true]`.
- `debugBuildsNeverEnable`: isRelease = false, preference true → `[false]`.
- `setsLanguageAndLibrarySize`: language tag "ar" → `keys[Language] == "ar"`; empty tag → "system"; 182 papers → `keys[LibrarySizeBucket] == "51-500"`; `keys[BackupInProgress] == "none"`.

`ScreenKeyTest` (Robolectric, like `HashiyaAppNavigationTest`): navigating Library → Details → Settings sets `screen` to `library`, `details`, `settings` in order; the Restore route sets `restore`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*CrashStartupTest' --tests '*ScreenKeyTest'`
Expected: compilation fails.

- [ ] **Step 3: Implement**

`CrashModule.kt`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object CrashModule {
    /** Debug builds and tests report nothing; release builds get Firebase (added with the Firebase setup). */
    @Provides
    @Singleton
    fun provideCrashReporter(): CrashReporter = NoOpCrashReporter
}
```

`CrashStartup.kt`:

```kotlin
/** Runs once at launch: collection follows the switch (release builds only), then the context keys are set. */
class CrashStartup(
    private val reporter: CrashReporter,
    private val preferences: UserPreferencesRepository,
    private val libraryBackup: LibraryBackup,
    private val languageTag: () -> String,
    private val isRelease: Boolean,
) {
    suspend fun run() {
        reporter.setEnabled(isRelease && preferences.crashReportsEnabled.first())
        reporter.setKey(CrashKey.Language, languageTag().substringBefore('-').ifEmpty { "system" })
        reporter.setKey(CrashKey.BackupInProgress, "none")
        val papers = runCatching { libraryBackup.summary().papers }.getOrNull()
        if (papers != null) reporter.setKey(CrashKey.LibrarySizeBucket, librarySizeBucket(papers))
    }
}
```

`HashiyaApplication`: inject `CrashReporter`, `UserPreferencesRepository`, `LibraryBackup`; in `onCreate` **before** launching anything else, launch `CrashStartup(..., languageTag = { AppCompatDelegate.getApplicationLocales().toLanguageTags() }, isRelease = !BuildConfig.DEBUG).run()` on the application scope first. (Collection starts off from the manifest, so a crash before `run()` finishes is never sent while the switch is off.) Update `language` again where the language changes: `AppLanguageController.set` lives in `:feature:settings`; have `SettingsViewModel.onLanguageSelected` call `crashReporter.setKey(CrashKey.Language, …)` with `en`/`ar`/`system`.

Screen key: in `HashiyaApp`, `DisposableEffect(navController) { val listener = NavController.OnDestinationChangedListener { _, destination, _ -> screenFor(destination)?.let { crashReporter.setKey(CrashKey.Screen, it) } }; navController.addOnDestinationChangedListener(listener); onDispose { navController.removeOnDestinationChangedListener(listener) } }`. `HashiyaApp` gets the reporter as a parameter from `MainActivity` (injected there). `screenFor` maps `hasRoute<LibraryRoute>()` → "library", Search → "search", PaperDetails → "details", Reader → "reader", Settings → "settings", Restore → "restore"; anything else → null.

`TestCrashReceiver.kt` + manifest — a way to send a test crash from a release build that only `adb` can trigger:

```xml
<!-- Only the shell (adb) holds DUMP, so no app or user on the device can trigger this. -->
<receiver
    android:name=".crash.TestCrashReceiver"
    android:exported="true"
    android:permission="android.permission.DUMP">
    <intent-filter>
        <action android:name="com.etatech.hashiya.TEST_CRASH" />
    </intent-filter>
</receiver>
```

```kotlin
/** `adb shell am broadcast -a com.etatech.hashiya.TEST_CRASH -p com.etatech.hashiya` crashes the app on purpose, to check reporting. */
class TestCrashReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Handler(Looper.getMainLooper()).post { throw RuntimeException("Test crash requested over adb") }
    }
}
```

Add a test that the receiver is declared with `android.permission.DUMP` (parse the merged manifest via `PackageManager.getReceiverInfo` under Robolectric).

- [ ] **Step 4: Run the tests**

Run: `./gradlew :app:testDebugUnitTest assembleDebug lintDebug`
Expected: PASS (the app now compiles with the Hilt `CrashReporter` binding Task 3 needed).

- [ ] **Step 5: Commit**

```bash
git add android/app android/feature/settings
git commit -m "feat(android): crash context keys at launch and on navigation, and an adb-only test crash"
```

---

### Task 5: Firebase Crashlytics in release builds

**Prerequisite (the user):** the Firebase project exists and `google-services.json` for `com.etatech.hashiya` is available (spec §6). Put it at `android/app/google-services.json`. If it's missing, stop and report BLOCKED.

**Files:**
- Modify: `android/gradle/libs.versions.toml`, `android/build.gradle.kts`, `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`, `crash/CrashModule.kt`
- Create: `android/app/google-services.json` (from the user), `android/app/src/main/java/com/etatech/hashiya/crash/FirebaseCrashReporter.kt`
- Modify: `docs/store/metadata.md` (Play Data safety), `docs/release.md` (Firebase setup and the test-crash check)
- Test: `android/app/src/test/java/com/etatech/hashiya/crash/CrashModuleTest.kt`

**Interfaces:**
- Produces: `FirebaseCrashReporter(crashlytics: FirebaseCrashlytics) : CrashReporter`; `CrashModule` returns it when `!BuildConfig.DEBUG`.

- [ ] **Step 1: Versions and plugins**

In `libs.versions.toml` add the latest stable versions (check Maven Central / Google Maven at implementation time and record them in the report):

```toml
[versions]
firebaseBom = "<latest stable, e.g. 34.x>"
googleServices = "<latest 4.4.x>"
firebaseCrashlyticsGradle = "<latest 3.x>"

[libraries]
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-crashlytics = { group = "com.google.firebase", name = "firebase-crashlytics" }

[plugins]
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version.ref = "firebaseCrashlyticsGradle" }
```

Root `build.gradle.kts`: `alias(libs.plugins.google.services) apply false`, `alias(libs.plugins.firebase.crashlytics) apply false`. `:app`: apply both plugins; `implementation(platform(libs.firebase.bom))`, `implementation(libs.firebase.crashlytics)`. Do not add `firebase-analytics`.

- [ ] **Step 2: Manifest**

Inside `<application>`:

```xml
<!-- Off until the app decides at launch: release builds with the Settings switch on (CrashStartup). -->
<meta-data
    android:name="firebase_crashlytics_collection_enabled"
    android:value="false" />
```

No other Firebase manifest keys are needed: Analytics isn't included, so there is nothing else to turn off.

- [ ] **Step 3: Write the failing test**

`CrashModuleTest`: under the debug unit-test build, `CrashModule.provideCrashReporter()` returns `NoOpCrashReporter` (`debugBuildsBindTheNoOpReporter`). A `FirebaseCrashReporter` test with a mocked/faked `FirebaseCrashlytics` isn't practical (final class); instead test the mapping in a small seam: `FirebaseCrashReporter` takes function references (`setCollectionEnabled`, `deleteUnsentReports`, `setCustomKey`, `recordException`) in an internal constructor; test that `setEnabled(false)` calls collection-off then delete-unsent, `setEnabled(true)` only collection-on, `recordNonFatal` passes a `ReportedFailure` (from `sanitized`) and never the original throwable.

- [ ] **Step 4: Implement**

```kotlin
/** Release builds' reporter. Everything it sends goes through [sanitized]; keys come from closed lists. */
class FirebaseCrashReporter internal constructor(
    private val setCollectionEnabled: (Boolean) -> Unit,
    private val deleteUnsentReports: () -> Unit,
    private val setCustomKey: (String, String) -> Unit,
    private val recordException: (Throwable) -> Unit,
) : CrashReporter {
    constructor(crashlytics: FirebaseCrashlytics) : this(
        crashlytics::setCrashlyticsCollectionEnabled,
        crashlytics::deleteUnsentReports,
        crashlytics::setCustomKey,
        crashlytics::recordException,
    )

    override fun setEnabled(enabled: Boolean) {
        setCollectionEnabled(enabled)
        if (!enabled) deleteUnsentReports()
    }

    override fun setKey(key: CrashKey, value: String) = setCustomKey(key.id, value)

    override fun recordNonFatal(error: Throwable, site: CrashSite) = recordException(sanitized(error, site))
}
```

`CrashModule`:

```kotlin
@Provides
@Singleton
fun provideCrashReporter(): CrashReporter =
    if (BuildConfig.DEBUG) NoOpCrashReporter else FirebaseCrashReporter(FirebaseCrashlytics.getInstance())
```

(Firebase initializes itself through its content provider from `google-services.json`; with collection off in the manifest it sends nothing until `CrashStartup` enables it.)

- [ ] **Step 5: Docs**

- `docs/release.md`: a "Crashlytics" section — the Firebase console steps (spec §6), and the release check: install the release build, `adb shell am broadcast -a com.etatech.hashiya.TEST_CRASH -p com.etatech.hashiya`, reopen the app (reports are sent on the next launch), confirm the crash in the Crashlytics console with readable frames; turn the switch off, repeat, confirm nothing new arrives.
- `docs/store/metadata.md` → Play Console Data safety: collected — App info and performance (Crash logs, Diagnostics) and Device or other IDs; not shared; encrypted in transit; optional (users can turn it off); purpose App functionality and analytics for stability. Privacy policy URL as in the privacy-policy PR. Leave the App Store section for the iOS PR.

- [ ] **Step 6: Run everything**

Run: `./gradlew spotlessCheck assembleDebug assembleRelease testDebugUnitTest :core:model:test :core:bibtex:test :core:crash:test lintDebug`
Expected: PASS (release assembles unsigned if `local.properties` has no upload key — that's fine).

- [ ] **Step 7: Commit**

```bash
git add android docs/release.md docs/store/metadata.md
git commit -m "feat(android): Firebase Crashlytics in release builds"
```

---

## Manual checks (before merging)

1. Release build, switch on: the adb test crash appears in Crashlytics with readable frames, keys `screen`, `language`, `librarySizeBucket`, `backupInProgress` set.
2. Switch off, test crash, relaunch: nothing new arrives.
3. A restore of a deliberately broken backup (e.g. a hand-made archive whose merge fails) shows one non-fatal `restore: <type>` with no title or path anywhere in the report.
4. Arabic Settings: the Privacy section reads right-to-left; the policy link opens the Arabic page.
