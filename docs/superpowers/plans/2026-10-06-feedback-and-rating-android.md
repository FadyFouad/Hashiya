# Feedback and Rating (Android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An About section in Settings (Send feedback, Rate Hashiya, Version) and a rating prompt through Google Play's In-App Review, asked after a few saves or a BibTeX export.

**Architecture:** A new JVM module `:core:review` holds the rule (`ReviewRule`), the counters store interface and `DefaultReviewPrompt`, the same way `:core:analytics` holds `Analytics` with its service only in `:app`. `:app` supplies a SharedPreferences store and a `ReviewRequests` flow that `MainActivity` turns into Play's review flow; debug builds get `NoOpReviewPrompt`. The Search and Library view models count saves and exports and call `askIfDue()` at moments nothing covers the screen. The About section is plain Compose in `:feature:settings`, with the intents built by small testable functions.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Robolectric + Compose UI tests, Roborazzi, `com.google.android.play:review-ktx`.

**Spec:** `docs/superpowers/specs/2026-10-06-feedback-and-rating-design.md`

## Global Constraints

- Feedback address `fady.fouad.a@gmail.com`; subject "Hashiya feedback" / «ملاحظات حول حاشية».
- Info line format: `Hashiya 0.3.0 (3) · Android 14 · Pixel 7 · ar` (version and build, platform and OS release, device model, app language). Nothing from the library.
- Rule: at least **5** saves **or** one BibTeX export; first opened at least **3 days** ago; never asked or last asked at least **120 days** ago. A stored time in the future means "not yet".
- Counters only go up. Restore doesn't count as saves; Copy BibTeX for one paper doesn't count as an export.
- "Last asked" is stored when the request is made.
- Off in debug builds (`NoOpReviewPrompt`), so UI tests and screenshot tests never ask.
- No new analytics events. The counters never leave the device; they count whether or not usage statistics are on.
- Android store link: `market://details?id=com.etatech.hashiya`, falling back to `https://play.google.com/store/apps/details?id=com.etatech.hashiya`.
- Version row: "Version 0.3.0 (3)" / «الإصدار 0.3.0 (3)» with Latin digits.
- No email app: copy the address, show "Email address copied: fady.fouad.a@gmail.com" / «تم نسخ عنوان البريد: …».
- Strings: About «حول التطبيق», Send feedback «إرسال ملاحظات», Rate Hashiya «قيّم حاشية».
- minSdk is 24: no `java.time` in main code; run `./gradlew lintDebug` before the final commit of each task.
- Commits authored `Fady <fady.fouad.a@gmail.com>`, no AI attribution or trailers.

## Review Focus

1. Arabic version and email lines: an LTR value inside an RTL sentence must keep its order — the version and the address are wrapped in U+2066/U+2068 … U+2069 in the Arabic strings (Task 4 tests the exact strings).
2. A save made from the preview sheet must not ask over the sheet; it asks when the sheet is dismissed (Task 3 test).
3. A BibTeX export asks only after the share sheet has closed (`onScreenResumed`), never in `onExportShared` (Task 3 test).
4. A device clock set backwards never asks early (Task 1 test).
5. Tapping Send feedback with no email app does not crash and copies the address (Task 4 test).

---

### Task 1: `:core:review` — the rule and the prompt

**Files:**
- Modify: `android/settings.gradle.kts` (add `include(":core:review")` after `:core:analytics`)
- Create: `android/core/review/build.gradle.kts`
- Create: `android/core/review/src/main/kotlin/com/etatech/hashiya/core/review/ReviewRule.kt`
- Create: `android/core/review/src/main/kotlin/com/etatech/hashiya/core/review/ReviewPrompt.kt`
- Test: `android/core/review/src/test/kotlin/com/etatech/hashiya/core/review/ReviewRuleTest.kt`
- Test: `android/core/review/src/test/kotlin/com/etatech/hashiya/core/review/DefaultReviewPromptTest.kt`
- Create: `android/core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeReviewPrompt.kt`
- Modify: `android/core/testing/build.gradle.kts` (add `api(project(":core:review"))`)

**Interfaces:**
- Produces: `data class ReviewCounters(firstOpenedAt: Long?, saves: Int, exported: Boolean, lastAskedAt: Long?)`; `object ReviewRule { MIN_SAVES; SETTLE_MILLIS; GAP_MILLIS; fun shouldAsk(counters: ReviewCounters, now: Long): Boolean }`; `interface ReviewCounterStore { read(); markOpened(now: Long); addSave(); markExported(); markAsked(now: Long) }`; `class InMemoryReviewCounterStore : ReviewCounterStore`; `interface ReviewPrompt { markOpened(); recordSave(); recordExport(); askIfDue() }`; `object NoOpReviewPrompt : ReviewPrompt`; `fun interface ReviewRequester { fun request() }`; `class DefaultReviewPrompt(store, requester, now: () -> Long = System::currentTimeMillis) : ReviewPrompt`; `class FakeReviewPrompt : ReviewPrompt` (in `:core:testing`) with `saves: Int`, `exports: Int`, `asks: Int`, `opened: Int`.

- [ ] **Step 1: Module build file**

`android/core/review/build.gradle.kts`:
```kotlin
plugins {
    id("hashiya.jvm.library")
}

dependencies {
    testImplementation(libs.junit)
}
```
Add `include(":core:review")` to `android/settings.gradle.kts` after `include(":core:analytics")`.

- [ ] **Step 2: Write the failing rule tests**

`ReviewRuleTest.kt`:
```kotlin
package com.etatech.hashiya.core.review

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRuleTest {
    private val day = 24L * 60 * 60 * 1000
    private val opened = 1_000L * day

    private fun counters(saves: Int = 0, exported: Boolean = false, lastAskedAt: Long? = null, firstOpenedAt: Long? = opened) =
        ReviewCounters(firstOpenedAt, saves, exported, lastAskedAt)

    @Test
    fun fiveSavesAfterThreeDaysAsks() = assertTrue(ReviewRule.shouldAsk(counters(saves = 5), opened + 3 * day))

    @Test
    fun fourSavesDoNot() = assertFalse(ReviewRule.shouldAsk(counters(saves = 4), opened + 30 * day))

    @Test
    fun oneExportIsEnough() = assertTrue(ReviewRule.shouldAsk(counters(exported = true), opened + 3 * day))

    @Test
    fun notBeforeThreeDays() = assertFalse(ReviewRule.shouldAsk(counters(saves = 9), opened + 3 * day - 1))

    @Test
    fun neverOpenedNeverAsks() = assertFalse(ReviewRule.shouldAsk(counters(saves = 9, firstOpenedAt = null), opened + 30 * day))

    @Test
    fun waitsHundredTwentyDaysAfterAsking() {
        val asked = opened + 10 * day
        assertFalse(ReviewRule.shouldAsk(counters(saves = 9, lastAskedAt = asked), asked + 120 * day - 1))
        assertTrue(ReviewRule.shouldAsk(counters(saves = 9, lastAskedAt = asked), asked + 120 * day))
    }

    @Test
    fun aClockSetBackwardsNeverAsksEarly() {
        // First opened "in the future" (the clock went back): not settled yet.
        assertFalse(ReviewRule.shouldAsk(counters(saves = 9), opened - day))
        // Last asked "in the future": not 120 days yet.
        assertFalse(ReviewRule.shouldAsk(counters(saves = 9, lastAskedAt = opened + 50 * day), opened + 40 * day))
    }
}
```

- [ ] **Step 3: Run to verify they fail**

Run: `cd android && ./gradlew :core:review:test`
Expected: FAIL to compile — `ReviewRule` / `ReviewCounters` unresolved.

- [ ] **Step 4: Implement the rule**

`ReviewRule.kt`:
```kotlin
package com.etatech.hashiya.core.review

/** What the rating rule reads. Kept on the device; it never leaves it. */
data class ReviewCounters(
    /** When the app was first opened, epoch millis; null before the first open was recorded. */
    val firstOpenedAt: Long?,
    /** Papers saved since install. Removing a paper doesn't lower it. */
    val saves: Int,
    /** Whether a BibTeX export ever finished. */
    val exported: Boolean,
    /** When the app last asked the system for its rating prompt; null if never. */
    val lastAskedAt: Long?
)

/**
 * When to ask for a rating: once the app has shown its value (5 saves or a BibTeX export), the person has had it for 3 days,
 * and it hasn't asked in 120 days. A stored time later than now (the clock went back) reads as "not yet".
 */
object ReviewRule {
    const val MIN_SAVES = 5
    const val SETTLE_MILLIS = 3L * 24 * 60 * 60 * 1000
    const val GAP_MILLIS = 120L * 24 * 60 * 60 * 1000

    fun shouldAsk(counters: ReviewCounters, now: Long): Boolean {
        val firstOpenedAt = counters.firstOpenedAt ?: return false
        if (counters.saves < MIN_SAVES && !counters.exported) return false
        if (now - firstOpenedAt < SETTLE_MILLIS) return false
        val lastAskedAt = counters.lastAskedAt ?: return true
        return now - lastAskedAt >= GAP_MILLIS
    }
}
```

- [ ] **Step 5: Run the rule tests**

Run: `cd android && ./gradlew :core:review:test`
Expected: PASS (7 tests).

- [ ] **Step 6: Write the failing prompt tests**

`DefaultReviewPromptTest.kt`:
```kotlin
package com.etatech.hashiya.core.review

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultReviewPromptTest {
    private val day = 24L * 60 * 60 * 1000
    private var now = 1_000L * day
    private var requests = 0
    private val store = InMemoryReviewCounterStore()
    private val prompt = DefaultReviewPrompt(store, { requests++ }, { now })

    @Test
    fun asksOnceTheRuleHoldsAndRecordsWhen() {
        prompt.markOpened()
        repeat(5) { prompt.recordSave() }
        prompt.askIfDue()
        assertEquals(0, requests) // not settled in yet
        now += 3 * day
        prompt.askIfDue()
        assertEquals(1, requests)
        assertEquals(now, store.read().lastAskedAt)
        prompt.askIfDue()
        assertEquals(1, requests) // not again within 120 days
    }

    @Test
    fun markOpenedKeepsTheFirstTime() {
        prompt.markOpened()
        val first = now
        now += 10 * day
        prompt.markOpened()
        assertEquals(first, store.read().firstOpenedAt)
    }

    @Test
    fun anExportCounts() {
        prompt.markOpened()
        now += 3 * day
        prompt.recordExport()
        prompt.askIfDue()
        assertEquals(1, requests)
    }

    @Test
    fun noOpNeverAsks() {
        NoOpReviewPrompt.markOpened()
        repeat(9) { NoOpReviewPrompt.recordSave() }
        NoOpReviewPrompt.askIfDue()
        assertEquals(0, requests)
    }
}
```

- [ ] **Step 7: Run to verify they fail**

Run: `cd android && ./gradlew :core:review:test`
Expected: FAIL to compile — `DefaultReviewPrompt`, `InMemoryReviewCounterStore`, `NoOpReviewPrompt` unresolved.

- [ ] **Step 8: Implement the prompt**

`ReviewPrompt.kt`:
```kotlin
package com.etatech.hashiya.core.review

/** Asks for a store rating at good moments. Only `:app` knows the store's API. */
interface ReviewPrompt {
    /** The app opened; the first call starts the 3-day wait. */
    fun markOpened()

    /** A paper was saved from Search, Add by ID or Share. */
    fun recordSave()

    /** A BibTeX export finished writing its file. */
    fun recordExport()

    /** Nothing covers the screen now: asks for the system's rating prompt if [ReviewRule] says so. */
    fun askIfDue()
}

/** Debug builds and tests: counts nothing, never asks. */
object NoOpReviewPrompt : ReviewPrompt {
    override fun markOpened() = Unit

    override fun recordSave() = Unit

    override fun recordExport() = Unit

    override fun askIfDue() = Unit
}

/** Hands the request to whatever shows the store's prompt. */
fun interface ReviewRequester {
    fun request()
}

/** Where the counters live; `:app` keeps them in private preferences that aren't backed up. */
interface ReviewCounterStore {
    fun read(): ReviewCounters

    /** Stores [now] as the first open unless one is stored. */
    fun markOpened(now: Long)

    fun addSave()

    fun markExported()

    fun markAsked(now: Long)
}

/** For tests and previews. */
class InMemoryReviewCounterStore : ReviewCounterStore {
    private var counters = ReviewCounters(firstOpenedAt = null, saves = 0, exported = false, lastAskedAt = null)

    override fun read(): ReviewCounters = counters

    override fun markOpened(now: Long) {
        if (counters.firstOpenedAt == null) counters = counters.copy(firstOpenedAt = now)
    }

    override fun addSave() {
        counters = counters.copy(saves = counters.saves + 1)
    }

    override fun markExported() {
        counters = counters.copy(exported = true)
    }

    override fun markAsked(now: Long) {
        counters = counters.copy(lastAskedAt = now)
    }
}

/**
 * Counts and decides; "last asked" is stored when the request is made, since the store never says whether its prompt
 * appeared. Calls may come from any thread.
 */
class DefaultReviewPrompt(
    private val store: ReviewCounterStore,
    private val requester: ReviewRequester,
    private val now: () -> Long = System::currentTimeMillis
) : ReviewPrompt {
    private val lock = Any()

    override fun markOpened() = synchronized(lock) { store.markOpened(now()) }

    override fun recordSave() = synchronized(lock) { store.addSave() }

    override fun recordExport() = synchronized(lock) { store.markExported() }

    override fun askIfDue() {
        val ask = synchronized(lock) {
            val time = now()
            ReviewRule.shouldAsk(store.read(), time).also { if (it) store.markAsked(time) }
        }
        if (ask) requester.request()
    }
}
```

- [ ] **Step 9: Run the module tests**

Run: `cd android && ./gradlew :core:review:test`
Expected: PASS (11 tests).

- [ ] **Step 10: The fake for view-model tests**

Add `api(project(":core:review"))` to `android/core/testing/build.gradle.kts` dependencies, and create `FakeReviewPrompt.kt`:
```kotlin
package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.review.ReviewPrompt

/** Counts every call, for tests. */
class FakeReviewPrompt : ReviewPrompt {
    var opened = 0
        private set
    var saves = 0
        private set
    var exports = 0
        private set
    var asks = 0
        private set

    override fun markOpened() {
        opened++
    }

    override fun recordSave() {
        saves++
    }

    override fun recordExport() {
        exports++
    }

    override fun askIfDue() {
        asks++
    }
}
```

- [ ] **Step 11: Format, compile and commit**

Run: `cd android && ./gradlew spotlessApply :core:review:test :core:testing:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.
```bash
git add android/settings.gradle.kts android/core/review android/core/testing
git commit -m "feat(android): decide when to ask for a store rating"
```

---

### Task 2: `:app` — counters on the device and Play's review flow

**Files:**
- Modify: `android/gradle/libs.versions.toml` (version `playReview = "2.0.2"`; library `play-review-ktx = { group = "com.google.android.play", name = "review-ktx", version.ref = "playReview" }`)
- Modify: `android/app/build.gradle.kts` (add `implementation(project(":core:review"))` and `implementation(libs.play.review.ktx)`)
- Create: `android/app/src/main/java/com/etatech/hashiya/review/SharedPreferencesReviewCounterStore.kt`
- Create: `android/app/src/main/java/com/etatech/hashiya/review/ReviewRequests.kt`
- Create: `android/app/src/main/java/com/etatech/hashiya/review/ReviewModule.kt`
- Create: `android/app/src/main/java/com/etatech/hashiya/review/PlayReview.kt`
- Modify: `android/app/src/main/java/com/etatech/hashiya/MainActivity.kt`
- Test: `android/app/src/test/java/com/etatech/hashiya/review/SharedPreferencesReviewCounterStoreTest.kt`
- Modify: `android/app/src/test/java/com/etatech/hashiya/BackupRulesTest.kt` (one test: no `sharedpref` domain is included)

**Interfaces:**
- Consumes: Task 1's `ReviewPrompt`, `DefaultReviewPrompt`, `NoOpReviewPrompt`, `ReviewCounterStore`, `ReviewCounters`, `ReviewRequester`.
- Produces: Hilt binding `ReviewPrompt` (`@Singleton`); `@Singleton class ReviewRequests @Inject constructor() : ReviewRequester` with `val requests: SharedFlow<Unit>`; `fun Activity.launchPlayReview()`.

- [ ] **Step 1: Write the failing store test**

```kotlin
package com.etatech.hashiya.review

import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.review.ReviewCounters
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesReviewCounterStoreTest {
    private fun store() = SharedPreferencesReviewCounterStore(ApplicationProvider.getApplicationContext())

    @Test
    fun startsEmpty() = assertEquals(ReviewCounters(null, 0, false, null), store().read())

    @Test
    fun keepsEverythingAcrossInstances() {
        store().apply {
            markOpened(10L)
            markOpened(20L)
            addSave()
            addSave()
            markExported()
            markAsked(30L)
        }
        assertEquals(ReviewCounters(firstOpenedAt = 10L, saves = 2, exported = true, lastAskedAt = 30L), store().read())
    }
}
```
If `androidx.test.core` isn't on the app's test classpath, use `RuntimeEnvironment.getApplication()` from Robolectric instead (it is, through `:core:testing`'s Robolectric).

- [ ] **Step 2: Run to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*SharedPreferencesReviewCounterStoreTest'`
Expected: FAIL to compile — `SharedPreferencesReviewCounterStore` unresolved.

- [ ] **Step 3: Dependencies and the store**

Add to `libs.versions.toml` `[versions]`: `playReview = "2.0.2"`; to `[libraries]`: `play-review-ktx = { group = "com.google.android.play", name = "review-ktx", version.ref = "playReview" }`. Add both `implementation` lines to `app/build.gradle.kts`.

`SharedPreferencesReviewCounterStore.kt`:
```kotlin
package com.etatech.hashiya.review

import android.content.Context
import androidx.core.content.edit
import com.etatech.hashiya.core.review.ReviewCounterStore
import com.etatech.hashiya.core.review.ReviewCounters

/** The rating counters, in a private preferences file the backup rules leave out (they include only database/ and datastore/). */
class SharedPreferencesReviewCounterStore(context: Context) : ReviewCounterStore {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read() = ReviewCounters(
        firstOpenedAt = prefs.getLong(FIRST_OPENED_AT, -1).takeIf { it >= 0 },
        saves = prefs.getInt(SAVES, 0),
        exported = prefs.getBoolean(EXPORTED, false),
        lastAskedAt = prefs.getLong(LAST_ASKED_AT, -1).takeIf { it >= 0 }
    )

    override fun markOpened(now: Long) {
        if (!prefs.contains(FIRST_OPENED_AT)) prefs.edit { putLong(FIRST_OPENED_AT, now) }
    }

    override fun addSave() = prefs.edit { putInt(SAVES, prefs.getInt(SAVES, 0) + 1) }

    override fun markExported() = prefs.edit { putBoolean(EXPORTED, true) }

    override fun markAsked(now: Long) = prefs.edit { putLong(LAST_ASKED_AT, now) }

    private companion object {
        const val FILE = "review_prompt"
        const val FIRST_OPENED_AT = "first_opened_at"
        const val SAVES = "saves"
        const val EXPORTED = "exported"
        const val LAST_ASKED_AT = "last_asked_at"
    }
}
```

- [ ] **Step 4: Run the store test**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*SharedPreferencesReviewCounterStoreTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: Backup rules test**

In `BackupRulesTest.kt`, add a test using the file's existing `includes(xml)` helper (it maps each `<include>` domain to its paths for a rules XML). Name it `ratingCountersAreNotBackedUp` and assert that no section of either rules file (`R.xml.data_extraction_rules`, `R.xml.backup_rules`, whichever ids the existing tests use) includes the domain `sharedpref`. Read the existing tests first and follow their shape exactly. Run `./gradlew :app:testDebugUnitTest --tests '*BackupRulesTest'` → PASS.

- [ ] **Step 6: Requests, module and Play's flow**

`ReviewRequests.kt`:
```kotlin
package com.etatech.hashiya.review

import com.etatech.hashiya.core.review.ReviewRequester
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Requests for the store's rating prompt; [com.etatech.hashiya.MainActivity] shows them while it is started. */
@Singleton
class ReviewRequests @Inject constructor() : ReviewRequester {
    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    override fun request() {
        _requests.tryEmit(Unit)
    }
}
```

`ReviewModule.kt`:
```kotlin
package com.etatech.hashiya.review

import android.content.Context
import com.etatech.hashiya.core.review.DefaultReviewPrompt
import com.etatech.hashiya.core.review.NoOpReviewPrompt
import com.etatech.hashiya.core.review.ReviewPrompt
import com.etatech.hashiya.crash.isDebuggable
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReviewModule {
    /** Debug builds (and so every UI and screenshot test) never ask. */
    @Provides
    @Singleton
    fun provideReviewPrompt(@ApplicationContext context: Context, requests: ReviewRequests): ReviewPrompt =
        if (isDebuggable(context)) NoOpReviewPrompt else DefaultReviewPrompt(SharedPreferencesReviewCounterStore(context), requests)
}
```

`PlayReview.kt`:
```kotlin
package com.etatech.hashiya.review

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Google Play's in-app rating sheet. Play decides whether it actually appears (never for apps not installed from Play, and
 * only a few times a year); failures are ignored.
 */
fun Activity.launchPlayReview() {
    val manager = ReviewManagerFactory.create(this)
    manager.requestReviewFlow().addOnCompleteListener { task ->
        if (task.isSuccessful && !isFinishing && !isDestroyed) manager.launchReviewFlow(this, task.result)
    }
}
```

- [ ] **Step 7: Wire `MainActivity`**

Add fields next to `analytics`:
```kotlin
    @Inject
    lateinit var reviewPrompt: ReviewPrompt

    @Inject
    lateinit var reviewRequests: ReviewRequests
```
In `onCreate`, after the `analytics.setProperty(...)` line:
```kotlin
        reviewPrompt.markOpened()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                reviewRequests.requests.collect { launchPlayReview() }
            }
        }
```
Imports: `androidx.lifecycle.Lifecycle`, `androidx.lifecycle.lifecycleScope`, `androidx.lifecycle.repeatOnLifecycle`, `kotlinx.coroutines.launch`, `com.etatech.hashiya.core.review.ReviewPrompt`, `com.etatech.hashiya.review.ReviewRequests`, `com.etatech.hashiya.review.launchPlayReview`. If `androidx.lifecycle.runtime.ktx` isn't on the app classpath, add `implementation(libs.androidx.lifecycle.runtime.ktx)` if the catalog has it; otherwise add the catalog entry with the version the other lifecycle libraries use.

- [ ] **Step 8: Build, test, lint, commit**

Run: `cd android && ./gradlew spotlessApply :app:testDebugUnitTest :app:hiltJavaCompileDebug :app:lintDebug -Proborazzi.test.verify=false`
Expected: BUILD SUCCESSFUL.
```bash
git add android/gradle/libs.versions.toml android/app
git commit -m "feat(android): ask Google Play for its rating prompt when the rule says so"
```

---

### Task 3: Count saves and exports, ask at safe moments

**Files:**
- Modify: `android/feature/search/build.gradle.kts` (add `implementation(project(":core:review"))`)
- Modify: `android/feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchViewModel.kt` (constructor gains `private val reviewPrompt: ReviewPrompt` after `analytics`; `onToggleSave`; `onDismissPreview`)
- Modify: `android/feature/library/build.gradle.kts` (add `implementation(project(":core:review"))`)
- Modify: `android/feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryViewModel.kt` (constructor gains `private val reviewPrompt: ReviewPrompt` after `analytics`; `onExportShared`; `onScreenResumed`)
- Modify every test that constructs these view models (pass `FakeReviewPrompt()` last): `SearchViewModelTest.kt`, `SearchAnalyticsTest.kt`, `LibraryViewModelTest.kt` (2 sites), `LibraryInputTest.kt`, `LibrarySwipeUndoTest.kt`, `LibraryTwoPaneTest.kt`, `LibraryCollectionsViewModelTest.kt`, `LibraryTabletScreenshotTest.kt`. Grep `SearchViewModel(` and `LibraryViewModel(` under `android/feature/*/src/test` to catch any others.
- Test: `android/feature/search/src/test/java/com/etatech/hashiya/feature/search/SearchReviewPromptTest.kt`
- Test: `android/feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryReviewPromptTest.kt`

**Interfaces:**
- Consumes: `ReviewPrompt` (Task 1), `FakeReviewPrompt` (Task 1, `:core:testing`).
- Produces: nothing new for later tasks.

- [ ] **Step 1: Write the failing Search tests**

Build the view model the way `SearchAnalyticsTest.kt` does (copy its setup: fakes, `MainDispatcherRule`, `SavedStateHandle`), passing a `FakeReviewPrompt` last. Three tests:
```kotlin
    @Test
    fun aSaveCountsAndAsksWhenNoPreviewIsOpen() = runTest {
        val viewModel = viewModel()
        viewModel.onToggleSave(PaperItem(paper, inLibrary = false))
        advanceUntilIdle()
        assertEquals(1, review.saves)
        assertEquals(1, review.asks)
    }

    @Test
    fun aSaveFromThePreviewAsksWhenThePreviewCloses() = runTest {
        val viewModel = viewModel()
        viewModel.onPaperClick(paper)
        viewModel.onToggleSave(PaperItem(paper, inLibrary = false))
        advanceUntilIdle()
        assertEquals(1, review.saves)
        assertEquals(0, review.asks)
        viewModel.onDismissPreview()
        assertEquals(1, review.asks)
    }

    @Test
    fun aRemovalOrAFailedSaveDoesNotCount() = runTest {
        val viewModel = viewModel()
        viewModel.onToggleSave(PaperItem(paper, inLibrary = true))
        library.failNextSave() // use the FakeLibraryRepository's existing failure switch; read the fake for its exact name
        viewModel.onToggleSave(PaperItem(paper, inLibrary = false))
        advanceUntilIdle()
        assertEquals(0, review.saves)
        assertEquals(0, review.asks)
    }
```
Use the fake library repository's real failure mechanism — open `FakeLibraryRepository.kt` and use whatever it offers (a flag, an exception property); if it has none, add `var saveFailure: Exception? = null` thrown from `save`, following its style. Use the paper fixture the neighbouring tests use (`SamplePapers`).

- [ ] **Step 2: Run to verify they fail**

Run: `cd android && ./gradlew :feature:search:testDebugUnitTest --tests '*SearchReviewPromptTest' -Proborazzi.test.verify=false`
Expected: FAIL to compile — the constructor has no review prompt.

- [ ] **Step 3: Implement in `SearchViewModel`**

Add the constructor parameter `private val reviewPrompt: ReviewPrompt` after `private val analytics: Analytics`. In `onToggleSave`, after `analytics.log(AnalyticsEvent.PaperSaved(source))`:
```kotlin
                    reviewPrompt.recordSave()
                    // Never over the preview sheet: that waits until the sheet closes.
                    if (selectedPaper.value == null) reviewPrompt.askIfDue()
```
`onDismissPreview` becomes:
```kotlin
    fun onDismissPreview() {
        selectedPaper.value = null
        reviewPrompt.askIfDue()
    }
```
Update every `SearchViewModel(` construction in tests to pass `FakeReviewPrompt()` last.

- [ ] **Step 4: Run the Search tests**

Run: `cd android && ./gradlew :feature:search:testDebugUnitTest -Proborazzi.test.verify=false`
Expected: PASS (all, including the 3 new).

- [ ] **Step 5: Write the failing Library tests**

Build the view model the way `LibraryCollectionsViewModelTest.kt` does around its export tests (line ~320: export → `onExportShared()` → `onScreenResumed()`), passing a `FakeReviewPrompt` last:
```kotlin
    @Test
    fun anExportCountsAndAsksOnlyAfterTheShareSheetCloses() = runTest {
        val viewModel = viewModel()
        viewModel.onExportClick(null) // the export entry point the existing export tests call; match their call exactly
        advanceUntilIdle()
        viewModel.onExportShared()
        assertEquals(1, review.exports)
        assertEquals(0, review.asks)
        viewModel.onScreenResumed()
        assertEquals(1, review.asks)
        viewModel.onScreenResumed()
        assertEquals(1, review.asks) // one export, one ask
    }

    @Test
    fun aFailedExportNeitherCountsNorAsks() = runTest {
        val viewModel = viewModel()
        viewModel.onExportClick(null)
        advanceUntilIdle()
        viewModel.onExportFailed()
        viewModel.onScreenResumed()
        assertEquals(0, review.exports)
        assertEquals(0, review.asks)
    }
```
Match the existing export tests' entry-point name and arguments exactly (read them first).

- [ ] **Step 6: Run to verify they fail**

Run: `cd android && ./gradlew :feature:library:testDebugUnitTest --tests '*LibraryReviewPromptTest' -Proborazzi.test.verify=false`
Expected: FAIL to compile.

- [ ] **Step 7: Implement in `LibraryViewModel`**

Constructor gains `private val reviewPrompt: ReviewPrompt` after `analytics`. Add a field next to `incompleteExportPending`:
```kotlin
    /** A BibTeX export reached the share sheet; the rating prompt waits until the person is back from it. */
    private var reviewPending = false
```
In `onExportShared()`, after the `analytics.log(...)` line:
```kotlin
        reviewPrompt.recordExport()
        reviewPending = true
```
`onScreenResumed()` becomes:
```kotlin
    fun onScreenResumed() {
        if (reviewPending) {
            reviewPending = false
            reviewPrompt.askIfDue()
        }
        if (!incompleteExportPending) return
        incompleteExportPending = false
        _message.value = LibraryMessage.ExportIncomplete
    }
```
Update every `LibraryViewModel(` construction in tests to pass `FakeReviewPrompt()` last.

- [ ] **Step 8: Run, lint, commit**

Run: `cd android && ./gradlew spotlessApply :feature:search:testDebugUnitTest :feature:library:testDebugUnitTest :app:hiltJavaCompileDebug lintDebug -Proborazzi.test.verify=false`
Expected: BUILD SUCCESSFUL.
```bash
git add android/feature android/core/testing
git commit -m "feat(android): count saves and BibTeX exports for the rating prompt"
```

---

### Task 4: The About section

**Files:**
- Create: `android/feature/settings/src/main/java/com/etatech/hashiya/feature/settings/About.kt`
- Modify: `android/feature/settings/src/main/java/com/etatech/hashiya/feature/settings/SettingsScreen.kt`
- Modify: `android/feature/settings/src/main/res/values/strings.xml`, `android/feature/settings/src/main/res/values-ar/strings.xml`
- Test: `android/feature/settings/src/test/java/com/etatech/hashiya/feature/settings/AboutTest.kt`
- Modify: `android/feature/settings/src/test/java/com/etatech/hashiya/feature/settings/SettingsContentTest.kt`, `SettingsScreenshotTest.kt` (new required `appVersion` argument; new tests and capture)

**Interfaces:**
- Produces: `data class AppVersion(val name: String, val code: Long) { val label: String }`; `fun feedbackInfoLine(version: AppVersion, osRelease: String, model: String, language: String): String`; `fun feedbackIntent(subject: String, version: AppVersion, osRelease: String, model: String, language: String): Intent`; `fun rateIntents(): List<Intent>`; `const val FEEDBACK_EMAIL`; `SettingsContent(..., appVersion: AppVersion, onSendFeedback: () -> Unit = {}, onRate: () -> Unit = {})`.

- [ ] **Step 1: Strings**

`values/strings.xml`:
```xml
    <string name="settings_about">About</string>
    <string name="settings_send_feedback">Send feedback</string>
    <string name="settings_rate">Rate Hashiya</string>
    <string name="settings_version">Version %1$s</string>
    <string name="settings_feedback_subject">Hashiya feedback</string>
    <string name="settings_feedback_copied">Email address copied: %1$s</string>
```
`values-ar/strings.xml` (the U+2066 … U+2069 and U+2068 … U+2069 pairs keep the LTR values in order; write them as the literal escape sequences `⁦`, `⁨`, `⁩`, as the file's other bidi strings do — check how it writes them and match):
```xml
    <string name="settings_about">حول التطبيق</string>
    <string name="settings_send_feedback">إرسال ملاحظات</string>
    <string name="settings_rate">قيّم حاشية</string>
    <string name="settings_version">الإصدار ⁦%1$s⁩</string>
    <string name="settings_feedback_subject">ملاحظات حول حاشية</string>
    <string name="settings_feedback_copied">تم نسخ عنوان البريد: ⁨%1$s⁩</string>
```

- [ ] **Step 2: Write the failing About tests**

`AboutTest.kt` (Robolectric, for `Intent`):
```kotlin
package com.etatech.hashiya.feature.settings

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AboutTest {
    private val version = AppVersion("0.3.0", 3)

    @Test
    fun versionLabelUsesLatinDigits() = assertEquals("0.3.0 (3)", version.label)

    @Test
    fun infoLine() = assertEquals("Hashiya 0.3.0 (3) · Android 14 · Pixel 7 · ar", feedbackInfoLine(version, "14", "Pixel 7", "ar"))

    @Test
    fun feedbackIntentIsADraftToTheDeveloper() {
        val intent = feedbackIntent("Hashiya feedback", version, "14", "Pixel 7", "en")
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals(Uri.parse("mailto:"), intent.data)
        assertEquals(listOf(FEEDBACK_EMAIL), intent.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.toList())
        assertEquals("Hashiya feedback", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("\n\nHashiya 0.3.0 (3) · Android 14 · Pixel 7 · en", intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun rateTriesThePlayStoreAppThenTheWeb() {
        val intents = rateIntents()
        assertEquals(Uri.parse("market://details?id=com.etatech.hashiya"), intents[0].data)
        assertEquals(Uri.parse("https://play.google.com/store/apps/details?id=com.etatech.hashiya"), intents[1].data)
        assertFalse(intents.any { it.action != Intent.ACTION_VIEW })
    }
}
```

- [ ] **Step 3: Run to verify they fail**

Run: `cd android && ./gradlew :feature:settings:testDebugUnitTest --tests '*AboutTest' -Proborazzi.test.verify=false`
Expected: FAIL to compile.

- [ ] **Step 4: Implement `About.kt`**

```kotlin
package com.etatech.hashiya.feature.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

internal const val FEEDBACK_EMAIL = "fady.fouad.a@gmail.com"
private const val PACKAGE = "com.etatech.hashiya"

/** This install's version, as stores and support quote it (Latin digits in every language). */
data class AppVersion(val name: String, val code: Long) {
    val label: String get() = "$name ($code)"

    companion object {
        fun of(context: Context): AppVersion = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            AppVersion(info.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(info))
        } catch (_: PackageManager.NameNotFoundException) {
            AppVersion("", 0)
        }
    }
}

/** What the developer needs to reproduce a report; nothing from the library. */
internal fun feedbackInfoLine(version: AppVersion, osRelease: String, model: String, language: String): String =
    "Hashiya ${version.label} · Android $osRelease · $model · $language"

/** An email draft to the developer, the message first and the info line under it. */
internal fun feedbackIntent(subject: String, version: AppVersion, osRelease: String, model: String, language: String): Intent =
    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, "\n\n" + feedbackInfoLine(version, osRelease, model, language))
    }

/** The Play Store app, then the web listing. */
internal fun rateIntents(): List<Intent> = listOf(
    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE")),
    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE"))
)

/** Opens the draft; false when no email app can (the caller then copies the address). */
internal fun Context.sendFeedback(subject: String, version: AppVersion, language: String): Boolean = try {
    startActivity(feedbackIntent(subject, version, Build.VERSION.RELEASE, Build.MODEL, language))
    true
} catch (_: ActivityNotFoundException) {
    false
}

internal fun Context.copyFeedbackAddress() {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(FEEDBACK_EMAIL, FEEDBACK_EMAIL))
}

/** The first intent something can open; nothing happens if none can. */
internal fun Context.openRatePage() {
    for (intent in rateIntents()) {
        try {
            startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}
```

- [ ] **Step 5: Run the About tests**

Run: `cd android && ./gradlew :feature:settings:testDebugUnitTest --tests '*AboutTest' -Proborazzi.test.verify=false`
Expected: PASS (4 tests).

- [ ] **Step 6: Write the failing Settings content tests**

In `SettingsContentTest.kt`, the `show(...)` helper passes `appVersion = AppVersion("0.3.0", 3)`, `onSendFeedback = { events += "feedback" }` and `onRate = { events += "rate" }`. Add:
```kotlin
    @Test
    fun aboutShowsFeedbackRateAndVersion() {
        show(SettingsUiState())
        composeRule.onNodeWithText("Send feedback").performScrollTo().performClick()
        composeRule.onNodeWithText("Rate Hashiya").performScrollTo().performClick()
        composeRule.onNodeWithText("Version 0.3.0 (3)").performScrollTo().assertIsDisplayed()
        assertEquals(listOf("feedback", "rate"), events)
    }
```
And an Arabic test following the file's existing Arabic-configuration pattern (it already imports `LocalConfiguration`, `Configuration`, `Locale` — copy how an existing Arabic test sets the locale), asserting the node with text `"الإصدار ⁦0.3.0 (3)⁩"` exists.

Every other `SettingsContent(` call in `SettingsContentTest.kt` and `SettingsScreenshotTest.kt` gains `appVersion = AppVersion("0.3.0", 3)`.

- [ ] **Step 7: Run to verify they fail**

Run: `cd android && ./gradlew :feature:settings:testDebugUnitTest --tests '*SettingsContentTest' -Proborazzi.test.verify=false`
Expected: FAIL to compile — no `appVersion` / `onSendFeedback` / `onRate` parameters.

- [ ] **Step 8: Implement the section**

In `SettingsContent`, add parameters before `modifier`:
```kotlin
    appVersion: AppVersion,
    onSendFeedback: () -> Unit = {},
    onRate: () -> Unit = {},
```
`appVersion` goes right after `uiState` (it is required). After the `PrivacySection(...)` call:
```kotlin
            Spacer(Modifier.height(32.dp))
            AboutSection(appVersion, onSendFeedback, onRate)
```
Add:
```kotlin
@Composable
private fun AboutSection(appVersion: AppVersion, onSendFeedback: () -> Unit, onRate: () -> Unit) {
    Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onSendFeedback) { Text(stringResource(R.string.settings_send_feedback)) }
    TextButton(onClick = onRate) { Text(stringResource(R.string.settings_rate)) }
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_version, appVersion.label),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
```
The snackbar for the copied address: `SettingsContent` already owns `snackbarHostState`. Add a parameter `feedbackCopied: Boolean = false` and `onFeedbackCopiedShown: () -> Unit = {}`; add, next to the existing message `LaunchedEffect`:
```kotlin
    val copiedText = stringResource(R.string.settings_feedback_copied, FEEDBACK_EMAIL)
    LaunchedEffect(feedbackCopied) {
        if (feedbackCopied) {
            snackbarHostState.showSnackbar(copiedText)
            onFeedbackCopiedShown()
        }
    }
```
In `SettingsScreen`:
```kotlin
    val context = LocalContext.current
    val appVersion = remember { AppVersion.of(context) }
    val language = LocalConfiguration.current.locales[0].language
    val subject = stringResource(R.string.settings_feedback_subject)
    var feedbackCopied by rememberSaveable { mutableStateOf(false) }
```
and pass to `SettingsContent`:
```kotlin
        appVersion = appVersion,
        onSendFeedback = {
            if (!context.sendFeedback(subject, appVersion, language)) {
                context.copyFeedbackAddress()
                feedbackCopied = true
            }
        },
        onRate = { context.openRatePage() },
        feedbackCopied = feedbackCopied,
        onFeedbackCopiedShown = { feedbackCopied = false },
```

- [ ] **Step 9: The no-email-app test**

Add to `AboutTest.kt` (imports: `android.app.Activity`, `android.content.ClipboardManager`, `android.content.ContextWrapper`, `org.robolectric.Robolectric`, `org.robolectric.Shadows.shadowOf`, `org.junit.Assert.assertTrue`):
```kotlin
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun sendFeedbackStartsTheDraft() {
        assertTrue(activity.sendFeedback("Hashiya feedback", version, "en"))
        assertEquals(Intent.ACTION_SENDTO, shadowOf(activity).nextStartedActivity.action)
    }

    @Test
    fun noEmailAppReportsFalseAndTheAddressCanBeCopied() {
        // A context whose startActivity always fails, as on a device with no email app.
        val noEmailApp = object : ContextWrapper(activity) {
            override fun startActivity(intent: Intent) = throw android.content.ActivityNotFoundException()
        }
        assertFalse(noEmailApp.sendFeedback("Hashiya feedback", version, "en"))
        activity.copyFeedbackAddress()
        val clip = activity.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals(FEEDBACK_EMAIL, clip.getItemAt(0).text.toString())
    }

    @Test
    fun ratingFallsBackToTheWebWhenThereIsNoPlayStore() {
        val started = mutableListOf<Intent>()
        val noPlayStore = object : ContextWrapper(activity) {
            override fun startActivity(intent: Intent) {
                if (intent.data?.scheme == "market") throw android.content.ActivityNotFoundException()
                started += intent
            }
        }
        noPlayStore.openRatePage()
        assertEquals(listOf("https"), started.map { it.data?.scheme })
    }
```

- [ ] **Step 10: Screenshot capture**

In `SettingsScreenshotTest.kt`, add (imports `androidx.compose.ui.test.onNodeWithTag`, `androidx.compose.ui.test.performScrollTo`):
```kotlin
    @Test
    fun about() = composeRule.captureScreenshot(
        "settings_about",
        variant,
        arabicText = "حول التطبيق",
        beforeCapture = { onNodeWithTag(ABOUT_SECTION_TAG).performScrollTo() }
    ) {
        SettingsContent(
            uiState = SettingsUiState(language = AppLanguage.System, crashReportsEnabled = true, analyticsEnabled = true),
            appVersion = AppVersion("0.3.0", 3),
            onBack = {},
            onKeyInputChange = {},
            onSaveKey = {},
            onResetKey = {},
            onLanguageSelected = {}
        )
    }
```
and in `About.kt` add `internal const val ABOUT_SECTION_TAG = "settings_about"`, with `AboutSection`'s title `Text` getting `modifier = Modifier.testTag(ABOUT_SECTION_TAG)` (import `androidx.compose.ui.platform.testTag`). Do NOT record baselines locally; CI records them on Linux.

- [ ] **Step 11: Run, lint, commit**

Run: `cd android && ./gradlew spotlessApply :feature:settings:testDebugUnitTest :app:hiltJavaCompileDebug lintDebug -Proborazzi.test.verify=false`
Expected: BUILD SUCCESSFUL.
```bash
git add android/feature/settings
git commit -m "feat(android): an About section with feedback, rating and the version"
```

---

### Task 5: Docs and changelog

- [ ] `CHANGELOG.md` `[Unreleased]` → Added: "- **Android: send feedback and rate Hashiya.** Settings → About opens an email to the developer with the app version and device filled in (nothing from your library), opens the Play listing to rate the app, and shows the version. After five saved papers or a BibTeX export, and no sooner than three days after installing, the app may ask for a rating through Google Play's own prompt, at most once every four months."
- [ ] In the spec §7, mark the Android PR delivered.
- [ ] Commit — `docs: feedback and the rating prompt on Android`

### Task 6: Screenshot baselines (controller, after pushing the branch)

- [ ] `scripts/record-screenshots-on-linux.sh` from the pushed branch; look at `settings_about-*` and the re-recorded `settings*` captures in English and Arabic (the version reads "الإصدار 0.3.0 (3)" in order) before committing.
