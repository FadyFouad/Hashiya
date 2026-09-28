# Library Search and Reading Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user find any saved paper by searching its title, authors, abstract or venue (offline, prefix-aware, ignoring case, accents and Arabic tashkeel/letter variants) and track each paper as To read, Reading or Read, with status chips that show counts and combine with the search.

**Architecture:** `core/model` gains `ReadingStatus`, `LibraryPaper` and `searchableText`, the one normalization used for both indexing and queries. `core/database` moves to schema version 2 — a `reading_status` column, a standalone FTS4 table `paper_search` kept in step by the DAO's write transactions, and `MIGRATION_1_2`, which indexes every existing paper; the version bump, the migration and its `MigrationTestHelper` test are one task (the suggested tasks 2 and 3 are merged) so no commit bumps the version without its migration. `core/data` turns typed text into a safe FTS `MATCH` (`ftsMatch`) and exposes `observeLibrary`, `observeStatusCounts` and `setStatus`; `core/designsystem` adds the status labels and the preview's segmented selector; `feature/library` gets a debounced search, status chips, a status badge with a menu, and a "No papers match" state (ViewModel and UI are separate tasks).

**Tech Stack:** Kotlin 2.4.20, AGP 9.2.1, Jetpack Compose + Material 3 (BOM 2026.09.00), Navigation Compose 2.10.2, Hilt 2.60.1 + KSP, Room 2.8.5 (`@Fts4`, `room-testing` `MigrationTestHelper`), Coroutines/Flow, JUnit 4, Robolectric 4.17 (sdk 35), Roborazzi 1.75.0, Turbine.

**Spec:** docs/superpowers/specs/2026-09-28-library-search-and-status-design.md

## Global Constraints

- Everything from sub-projects 1–2 still applies: base package `com.etatech.hashiya`; `compileSdk = 37`, `targetSdk = 36`, `minSdk = 24`; versions only in `gradle/libs.versions.toml`; KSP only; no `org.jetbrains.kotlin.android` plugin.
- Dependency rules: `feature/*` → `core/data`, `core/model`, `core/designsystem` only; `core/network`, `core/database`, `core/datastore` never depend on each other; `core/model` has no Android dependency. The one new edge is `core/database` → `core/model` (`implementation`, for `searchableText`, spec §4); `core/network` and `core/datastore` still don't see `core/model`.
- `searchableText` (`core/model/.../SearchableText.kt`) is the only search normalization: NFKD, drop every `\p{M}`, drop tatweel U+0640, `أ إ آ ٱ` → `ا`, `ى` → `ي`, then `lowercase(Locale.ROOT)`. Every indexed column goes through it via `searchEntityFor(...)` (saves and the migration); every query goes through it via `ftsMatch(...)`. `normalizedTitle` in `core/data/lookup` stays separate.
- `MATCH` strings come only from `ftsMatch`: letters and digits only, each word a quoted prefix term `"word*"` (star inside the quotes — FTS4 treats `"word"*` as the exact word), words joined by spaces, `null` for no search. User text is never concatenated into SQL.
- Reading status is stored as the fixed strings `to_read`, `reading`, `read` (never enum names), column `papers.reading_status TEXT NOT NULL DEFAULT 'to_read'`. The mapping lives only in `core/data/.../mapping/ReadingStatusMapping.kt`; an unknown stored value reads (and counts) as To read.
- Schema version 2 with `MIGRATION_1_2` registered through `addMigrations(MIGRATION_1_2)`; no `fallbackToDestructiveMigration*` anywhere. `schemas/com.etatech.hashiya.core.database.HashiyaDatabase/1.json` stays and the generated `2.json` is committed. A schema change never lands in a commit without its version bump and migration.
- Status is never shown by colour alone: the row badge always shows its label (Read adds a check icon), the selected chip and the selected preview segment show a check.
- Strings from spec §7.6, verbatim, in both `values/strings.xml` and `values-ar/strings.xml`: `library_search_hint` "Search your library" / "ابحث في مكتبتك"; `library_filter_all` "All" / "الكل"; `library_filter_count` `%1$s · %2$s` / `%1$s (%2$s)` (in Arabic a "·" next to Arabic-Indic digits reads like "٠", so the count goes in parentheses); `library_status_badge_description` "Status: %1$s. Change status" / "الحالة: %1$s. تغيير الحالة"; `library_no_matches_title` "No papers match" / "لا توجد أوراق مطابقة"; `library_no_matches_action` "Clear search and filters" / "مسح البحث والفلاتر"; `library_status_update_failed` "Couldn\'t update the status" / "تعذّر تحديث الحالة"; and in `core/designsystem` `status_to_read` "To read" / "للقراءة", `status_reading` "Reading" / "قيد القراءة", `status_read` "Read" / "مقروءة". One addition the spec's table lacks: the search field's clear button needs a label, `library_search_clear` "Clear search" / "مسح البحث".
- Every other user-visible string lives in both locales of the module that uses it; no string literals in composables. Layouts use start/end; paper content and the search field use `TextDirection.Content`; direction-implying icons are `Icons.AutoMirrored`. Chip counts use `NumberFormat.getInstance(locale)`, like Search's result count, so Arabic shows Arabic-Indic digits.
- Tests use hand-written fakes; no mocking libraries. Robolectric tests run at SDK 35 (`src/test/resources/robolectric.properties` → `sdk=35`). Add `@Config(qualifiers = PHONE_QUALIFIERS)` to a Compose UI test class when an element sits below Robolectric's small default viewport.
- Screenshot tests use `ScreenshotVariantRule` at `@get:Rule(order = 0)` and the compose rule at `order = 1`; every `captureScreenshot(name, variant, arabicText)` passes an `arabicText` that is a string from the app's `values-ar` resources (never paper content) and that appears on exactly one node (status labels appear on both a chip and a badge, so they can't be used on list screens). Menus are separate windows: capture them with `wholeScreen = true`.
- **Screenshot baselines come from CI Linux only.** Commit code without screenshots (`git add -A -- . ':(exclude).idea/**' ':(exclude,glob)**/src/test/screenshots/**'`), then run `bash scripts/record-screenshots-on-linux.sh` (10–15 minutes; run it in the background) in a dedicated final step and commit the downloaded PNGs (`git add -- ':(glob)**/src/test/screenshots/**'`). Local `recordRoborazziDebug` output is for inspection only.
- The OpenAlex API key is never logged, never placed in exception messages, and never sent anywhere but OpenAlex.
- Git: work on branch `feat/library-search-and-status` in a worktree; never commit to `main`; never stage `.idea/`. Run `./gradlew spotlessApply` before every commit (ktlint `android_studio` style, `max_line_length = 140`; it may re-wrap long lines — that is expected). Commit messages use `feat:`/`fix:`/`test:`/`docs:`/`build:` and contain no AI or Claude attribution (no trailers, links or credits), nor do code comments or docs.
- Task 2 adds `androidx.room:room-testing` to the catalog; the first Gradle run after it needs network access (don't pass `--offline` to that run).

## Review Focus

1. **Prefix search written the way the spec shows it (`"transf"*`) silently matches only the exact word in SQLite FTS4** → "transf" must find "Transformer" through the real index, so `ftsMatch` emits `"transf*"`. Pinned by `RoomLibraryRepositoryTest.searchFindsTitleAuthorAbstractAndVenueByPrefix` (Task 3; typed text → `ftsMatch` → real FTS4) and `PaperDaoTest.prefixTermsMatchLongerWordsAndEveryWordMustMatch` (Task 2).
2. **Search text that is FTS syntax — `BERT:` (a colon is FTS column-filter syntax), `C++`, a stray `"`, `-word`, `(`, `*`, `a AND`, `NEAR/2`** → plain words or no search; never an SQLite "malformed MATCH" / "no such column" error. Pinned by `RoomLibraryRepositoryTest.searchTextWithFtsSyntaxNeverFails` (Task 3), which runs each string through real SQLite, plus `FtsQueryTest` (Task 3).
3. **A search that briefly matches nothing while the user types (a typo)** → "No papers match" replaces the list but the search field and chips stay, keeping focus and the keyboard; Empty (which hides the field) only when the whole library is empty, even if the last paper is removed during a search. Pinned by `LibraryContentTest.searchFieldKeepsFocusWhenNothingMatches` (Task 6) and `LibraryViewModelTest.noMatchesWhenTheLibraryHasPapersButNoneMatch` / `removingTheLastPaperDuringASearchShowsEmpty` (Task 5).
4. **Upgrading a library whose papers have no abstract, no venue or no authors (common in OpenAlex), or Arabic titles with tashkeel** → the migration doesn't crash on NULL columns, every paper is kept as To read, indexed with normalized text, and the migrated file opens with Room and is searchable. Pinned by `MigrationTest.migrationKeepsEveryPaperAsToReadAndValidatesAgainstVersion2Schema` and `migratedLibraryOpensWithRoomAndIsSearchable` (Task 2).
5. **Changing the status in the preview while a chip that excludes the new status is selected** → the row leaves the list but the sheet stays open showing the new status (the selected paper is looked up in the whole library, not the filtered list). Pinned by `LibraryViewModelTest.statusChangeOutOfTheChipKeepsThePreviewOpen` (Task 5).

---

## File Structure

```
core/model/.../core/model/LibraryPaper.kt, SearchableText.kt                       ReadingStatus, LibraryPaper, searchableText (Task 1)
core/database/build.gradle.kts, gradle/libs.versions.toml                          core/model dependency, room-testing, schemas as test assets (Task 2)
core/database/.../model/PaperEntity.kt, PaperSearchEntity.kt, StatusCount.kt       reading_status, FTS4 entity, searchEntityFor (Task 2)
core/database/.../HashiyaDatabase.kt, dao/PaperDao.kt, di/DatabaseModule.kt        version 2, queries, index upkeep, migration registered (Task 2)
core/database/.../migration/Migrations.kt, schemas/.../2.json                      MIGRATION_1_2, exported schema (Task 2)
core/data/.../search/FtsQuery.kt, mapping/ReadingStatusMapping.kt                  ftsMatch, stored status strings (Task 3)
core/data/.../repository/LibraryRepository.kt, RoomLibraryRepository.kt           observeLibrary, observeStatusCounts, setStatus (Task 3)
core/testing/.../FakeLibraryRepository.kt                                          searching, status-aware fake (Task 3)
core/designsystem/.../component/ReadingStatusSelector.kt, PaperPreview.kt          labels, segmented selector (Task 4)
core/designsystem/.../component/MessageStates.kt                                   EmptyState without a message (Task 4)
feature/library/.../LibraryUiState.kt, LibraryViewModel.kt                          search, chips, status changes (Task 5)
feature/library/.../LibraryActions.kt, LibraryScreen.kt, components/*              search field, chips, badge + menu, No matches (Task 6)
core/testing/.../Screenshots.kt                                                    whole-screen capture for menus (Task 6)
README.md (Task 7)
```

---

### Task 1: `core/model` — `ReadingStatus`, `LibraryPaper` and `searchableText`

**Files:**
- Create: `core/model/src/main/kotlin/com/etatech/hashiya/core/model/LibraryPaper.kt`
- Create: `core/model/src/main/kotlin/com/etatech/hashiya/core/model/SearchableText.kt`
- Test: `core/model/src/test/kotlin/com/etatech/hashiya/core/model/SearchableTextTest.kt`

**Interfaces:**
- Consumes: existing `Paper`.
- Produces:
  - `enum class ReadingStatus { ToRead, Reading, Read }`
  - `data class LibraryPaper(val paper: Paper, val status: ReadingStatus)`
  - `fun searchableText(text: String): String`

- [ ] **Step 1: Write the failing tests**

`core/model/src/test/kotlin/com/etatech/hashiya/core/model/SearchableTextTest.kt`:
```kotlin
package com.etatech.hashiya.core.model

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchableTextTest {
    private fun same(vararg variants: String) {
        val expected = searchableText(variants.first())
        variants.forEach { assertEquals("searchableText(\"$it\")", expected, searchableText(it)) }
    }

    @Test
    fun ignoresCaseAndLatinAccents() {
        assertEquals("schrodinger", searchableText("Schrödinger"))
        same("Schrödinger", "schrodinger", "SCHRÖDINGER", "Schrödinger")
        assertEquals("cafe naive", searchableText("Café Naïve"))
    }

    @Test
    fun removesArabicTashkeel() {
        assertEquals("التعلم", searchableText("التَّعلُّم"))
        same("التعلم", "التَّعلُّم", "اَلتَّعَلُّمُ")
    }

    @Test
    fun removesTatweel() {
        assertEquals("العربية", searchableText("العـــربية"))
    }

    @Test
    fun unifiesAlefForms() {
        assertEquals("احمد", searchableText("أحمد"))
        assertEquals("اسلام", searchableText("إسلام"))
        assertEquals("اية", searchableText("آية"))
        assertEquals("الكتاب", searchableText("ٱلكتاب"))
    }

    @Test
    fun unifiesAlefMaksuraWithYaa() {
        assertEquals("مستشفي", searchableText("مستشفى"))
        same("مستشفى", "مستشفي")
    }

    @Test
    fun handlesMixedArabicAndEnglish() {
        assertEquals("تعلم الالة machine learning", searchableText("تعلُّم الآلة Machine LEARNING"))
    }

    @Test
    fun keepsEmptyAndPunctuationOnlyInputHarmless() {
        assertEquals("", searchableText(""))
        assertEquals("-- !? ()", searchableText("-- !? ()"))
    }

    /** On a Turkish phone, "TITLE".lowercase() would give "tıtle" and never match text indexed elsewhere. */
    @Test
    fun lowercasingIgnoresTheDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals("title", searchableText("TITLE"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:model:test --tests "*SearchableTextTest"`
Expected: FAIL — compilation error `Unresolved reference 'searchableText'`.

- [ ] **Step 3: Implement**

`core/model/src/main/kotlin/com/etatech/hashiya/core/model/LibraryPaper.kt`:
```kotlin
package com.etatech.hashiya.core.model

/** Where the user is with a saved paper. New saves start as [ToRead]. */
enum class ReadingStatus { ToRead, Reading, Read }

/** A paper in the user's library, with its reading status. */
data class LibraryPaper(val paper: Paper, val status: ReadingStatus)
```

`core/model/src/main/kotlin/com/etatech/hashiya/core/model/SearchableText.kt`:
```kotlin
package com.etatech.hashiya.core.model

import java.text.Normalizer
import java.util.Locale

private val COMBINING_MARKS = Regex("""\p{M}+""")

// Tatweel (kashida), the Arabic line-stretching character.
private const val TATWEEL = "ـ"

// أ إ آ ٱ: alef with hamza above, hamza below, madda, and alef wasla.
private val ALEF_VARIANTS = Regex("[أإآٱ]")
private const val ALEF = "ا"
private const val ALEF_MAKSURA = 'ى'
private const val YAA = 'ي'

/** Lowercased text with accents and marks removed and Arabic letter variants unified, for full-text search. */
fun searchableText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD)
    .replace(COMBINING_MARKS, "")
    .replace(TATWEEL, "")
    .replace(ALEF_VARIANTS, ALEF)
    .replace(ALEF_MAKSURA, YAA)
    .lowercase(Locale.ROOT)
```

Note: NFKD already splits `أ إ آ` into `ا` plus a combining hamza or madda, which step 1 removes; the explicit alef rule is still needed for `ٱ` (alef wasla has no decomposition) and keeps the spec's order readable.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:model:test`
Expected: `BUILD SUCCESSFUL`; the 8 `SearchableTextTest` tests and the existing `core/model` tests pass.

- [ ] **Step 5: Format**

Run: `./gradlew spotlessApply`
Expected: `BUILD SUCCESSFUL`, no changes to the code above.

- [ ] **Step 6: Commit**

```bash
git add -A -- . ':(exclude).idea/**'
git commit -m "feat: add reading status and search text normalization to the model"
```

---

### Task 2: `core/database` — schema version 2 with the search index and its migration

The version bump, the new schema and `MIGRATION_1_2` land together: a commit that bumped the version without the migration would crash every upgraded install. `core/data` gets the smallest change that keeps it compiling (it writes the search row and `to_read`); Task 3 replaces those two files.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/database/build.gradle.kts` (full replacement below)
- Modify: `core/database/src/main/java/com/etatech/hashiya/core/database/model/PaperEntity.kt` (full replacement below)
- Create: `core/database/src/main/java/com/etatech/hashiya/core/database/model/PaperSearchEntity.kt`
- Create: `core/database/src/main/java/com/etatech/hashiya/core/database/model/StatusCount.kt`
- Modify: `core/database/src/main/java/com/etatech/hashiya/core/database/HashiyaDatabase.kt` (full replacement below)
- Modify: `core/database/src/main/java/com/etatech/hashiya/core/database/dao/PaperDao.kt` (full replacement below)
- Create: `core/database/src/main/java/com/etatech/hashiya/core/database/migration/Migrations.kt`
- Modify: `core/database/src/main/java/com/etatech/hashiya/core/database/di/DatabaseModule.kt` (full replacement below)
- Generated: `core/database/schemas/com.etatech.hashiya.core.database.HashiyaDatabase/2.json`
- Modify: `core/data/src/main/java/com/etatech/hashiya/core/data/mapping/PaperEntityMapping.kt` (full replacement below)
- Modify: `core/data/src/main/java/com/etatech/hashiya/core/data/repository/RoomLibraryRepository.kt` (full replacement below)
- Test: `core/database/src/test/java/com/etatech/hashiya/core/database/dao/PaperDaoTest.kt` (full replacement below)
- Test: `core/database/src/test/java/com/etatech/hashiya/core/database/migration/MigrationTest.kt`

**Interfaces:**
- Consumes: `searchableText` (Task 1).
- Produces:
  - `PaperEntity(..., savedAt: Long, readingStatus: String)` — column `reading_status`, default `'to_read'`.
  - `@Fts4 PaperSearchEntity(paperId: String, title: String, authors: String, abstract: String, venue: String)`, table `paper_search`.
  - `fun searchEntityFor(paperId: String, title: String, authorNames: List<String>, abstract: String?, venue: String?): PaperSearchEntity` — every column through `searchableText`.
  - `data class StatusCount(readingStatus: String, count: Int)`.
  - `PaperDao.observeLibrary(match: String?, status: String?): Flow<List<PaperWithAuthors>>`, `observeStatusCounts(match: String?): Flow<List<StatusCount>>`, `suspend setStatus(openAlexId: String, status: String): Int`, `suspend insertPaperWithAuthors(paper, authors, search: PaperSearchEntity): Boolean`, `suspend deleteByOpenAlexId(openAlexId): PaperWithAuthors?` (also deletes the search row). `observeSavedPapers()` is gone.
  - `internal val MIGRATION_1_2: Migration` in `com.etatech.hashiya.core.database.migration`.
  - `internal data class PaperEntities(paper, authors, search: PaperSearchEntity)` in `core/data` (Task 3 keeps it).

- [ ] **Step 1: Add the test dependency and the schemas as test assets**

In `gradle/libs.versions.toml`, under `[libraries]`, add after the `room-compiler` line:
```toml
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
```

`core/database/build.gradle.kts` (replace the whole file):
```kotlin
plugins {
    id("hashiya.android.library")
    id("hashiya.android.room")
    id("hashiya.hilt")
}

android {
    namespace = "com.etatech.hashiya.core.database"
    // MigrationTestHelper reads the exported schemas (schemas/<database class>/<version>.json) as assets.
    sourceSets {
        named("test") { assets.directories.add("$projectDir/schemas") }
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    // searchableText, shared with core/data so indexed text and queries are normalized the same way.
    implementation(project(":core:model"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.room.testing)
}

// MigrationTest reads schemas/ as assets: export a changed schema before the test assets are merged.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("UnitTestAssets") }.configureEach {
    dependsOn("copyRoomSchemas")
}
```

Two details are deliberate: `sourceSets { named("test") { … } }`, because in AGP 9 `sourceSets.getByName("test")` in this script fails with a `ClassCastException` on `DefaultAndroidLibrarySourceSet`; and the `dependsOn("copyRoomSchemas")`, because without it a clean build merges the test assets before Room exports `2.json`, and `MigrationTest` fails with `Missing file: com.etatech.hashiya.core.database.HashiyaDatabase/2.json`.

- [ ] **Step 2: Write the failing tests**

`core/database/src/test/java/com/etatech/hashiya/core/database/dao/PaperDaoTest.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.StatusCount
import com.etatech.hashiya.core.database.model.searchEntityFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaperDaoTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var dao: PaperDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.paperDao()
    }

    @After
    fun tearDown() = db.close()

    private fun paper(
        id: String,
        openAlexId: String,
        savedAt: Long,
        doi: String? = null,
        title: String = "Title $id",
        abstract: String? = null,
        venue: String? = "Venue",
        status: String = "to_read"
    ) = PaperEntity(
        id = id,
        openAlexId = openAlexId,
        doi = doi,
        title = title,
        year = 2020,
        venue = venue,
        abstract = abstract,
        citationCount = 1,
        isOpenAccess = false,
        oaPdfUrl = null,
        savedAt = savedAt,
        readingStatus = status
    )

    private fun authors(paperId: String, vararg names: String) =
        names.mapIndexed { index, name -> PaperAuthorEntity(paperId, index, name, null) }

    /** Saves [paper] with [authorNames] and its search row, as the repository does. */
    private suspend fun save(paper: PaperEntity, vararg authorNames: String): Boolean = dao.insertPaperWithAuthors(
        paper,
        authors(paper.id, *authorNames),
        searchEntityFor(paper.id, paper.title, authorNames.toList(), paper.abstract, paper.venue)
    )

    private fun count(table: String): Int = db.query("SELECT COUNT(*) FROM $table", null).use {
        it.moveToFirst()
        it.getInt(0)
    }

    private suspend fun ids(match: String? = null, status: String? = null) = dao.observeLibrary(match, status).first().map { it.paper.id }

    @Test
    fun savedPapersAreNewestFirst() = runTest {
        save(paper("a", "W1", savedAt = 100), "Ada")
        save(paper("b", "W2", savedAt = 200), "Bo")

        assertEquals(listOf("b", "a"), ids())
    }

    @Test
    fun keepsAuthorPositions() = runTest {
        save(paper("a", "W1", 100), "First", "Second", "Third")

        val saved = dao.observeLibrary(null, null).first().single()
        assertEquals(listOf("First", "Second", "Third"), saved.authors.sortedBy { it.position }.map { it.name })
    }

    @Test
    fun savingAgainIsANoOp() = runTest {
        assertTrue(save(paper("a", "W1", 100), "Ada"))
        assertFalse(save(paper("other-id", "W1", 999), "Ada"))

        val saved = dao.observeLibrary(null, null).first()
        assertEquals(1, saved.size)
        assertEquals(100L, saved.single().paper.savedAt)
        assertEquals(1, count("paper_authors"))
        assertEquals(1, count("paper_search"))
    }

    @Test
    fun worksSharingADoiCanBothBeSaved() = runTest {
        assertTrue(save(paper("a", "W1", 100, doi = "10.1000/xyz")))
        assertTrue(save(paper("b", "W2", 200, doi = "10.1000/xyz")))

        assertEquals(setOf("W1", "W2"), dao.observeSavedOpenAlexIds().first().toSet())
    }

    @Test
    fun deletingReturnsRowAndCascadesToAuthorsAndSearchRow() = runTest {
        save(paper("a", "W1", 100), "Ada", "Bo")

        val removed = dao.deleteByOpenAlexId("W1")

        assertEquals("a", removed?.paper?.id)
        assertEquals(2, removed?.authors?.size)
        assertTrue(dao.observeLibrary(null, null).first().isEmpty())
        assertEquals(0, count("paper_authors"))
        assertEquals(0, count("paper_search"))
    }

    @Test
    fun restoringDeletedRowKeepsIdSavedAtAndStatus() = runTest {
        save(paper("a", "W1", 100, status = "reading"), "Ada")
        save(paper("b", "W2", 200), "Bo")
        val removed = dao.deleteByOpenAlexId("W1")!!

        save(removed.paper, "Ada")

        val saved = dao.observeLibrary(null, null).first()
        assertEquals(listOf("b", "a"), saved.map { it.paper.id })
        assertEquals(100L, saved.last().paper.savedAt)
        assertEquals("reading", saved.last().paper.readingStatus)
        assertEquals(listOf("a"), ids(match = "\"ada*\""))
    }

    @Test
    fun deletingUnknownPaperReturnsNull() = runTest {
        assertNull(dao.deleteByOpenAlexId("missing"))
    }

    @Test
    fun observesSavedOpenAlexIds() = runTest {
        save(paper("a", "W1", 100))
        save(paper("b", "W2", 200))

        assertEquals(setOf("W1", "W2"), dao.observeSavedOpenAlexIds().first().toSet())
    }

    @Test
    fun searchesTitleAuthorsAbstractAndVenue() = runTest {
        save(
            paper("a", "W1", 100, title = "Attention Is All You Need", abstract = "Sequence transduction", venue = "NeurIPS"),
            "Ashish Vaswani"
        )
        save(paper("b", "W2", 200, title = "Deep Residual Learning", abstract = "Image recognition", venue = "CVPR"), "Kaiming He")

        assertEquals(listOf("a"), ids(match = "\"attention*\""))
        assertEquals(listOf("a"), ids(match = "\"vaswani*\""))
        assertEquals(listOf("b"), ids(match = "\"recognition*\""))
        assertEquals(listOf("b"), ids(match = "\"cvpr*\""))
        assertEquals(emptyList<String>(), ids(match = "\"transformer*\""))
    }

    @Test
    fun prefixTermsMatchLongerWordsAndEveryWordMustMatch() = runTest {
        save(paper("a", "W1", 100, title = "Transformers for language"))
        save(paper("b", "W2", 200, title = "Transformers for images"))

        assertEquals(listOf("b", "a"), ids(match = "\"transf*\""))
        assertEquals(listOf("a"), ids(match = "\"transf*\" \"lang*\""))
    }

    /** The paper's local id is stored in the index but not indexed, so it never matches a search. */
    @Test
    fun paperIdIsNotSearchable() = runTest {
        save(paper("zzlocalid", "W1", 100, title = "Deep learning"))

        assertEquals(emptyList<String>(), ids(match = "\"zzlocalid*\""))
    }

    @Test
    fun searchCombinesWithStatus() = runTest {
        save(paper("a", "W1", 100, title = "Transformers one", status = "reading"))
        save(paper("b", "W2", 200, title = "Transformers two"))
        save(paper("c", "W3", 300, title = "Convolutions", status = "reading"))

        assertEquals(listOf("c", "a"), ids(status = "reading"))
        assertEquals(listOf("a"), ids(match = "\"transf*\"", status = "reading"))
    }

    @Test
    fun countsPerStatusFollowTheSearch() = runTest {
        save(paper("a", "W1", 100, title = "Transformers one", status = "reading"))
        save(paper("b", "W2", 200, title = "Transformers two"))
        save(paper("c", "W3", 300, title = "Convolutions", status = "reading"))

        assertEquals(
            setOf(StatusCount("reading", 2), StatusCount("to_read", 1)),
            dao.observeStatusCounts(null).first().toSet()
        )
        assertEquals(
            setOf(StatusCount("reading", 1), StatusCount("to_read", 1)),
            dao.observeStatusCounts("\"transf*\"").first().toSet()
        )
        assertEquals(emptyList<StatusCount>(), dao.observeStatusCounts("\"missing*\"").first())
    }

    @Test
    fun settingStatusKeepsOrderAndIndex() = runTest {
        save(paper("a", "W1", 100, title = "Transformers one"))
        save(paper("b", "W2", 200, title = "Transformers two"))

        assertEquals(1, dao.setStatus("W1", "read"))

        assertEquals(listOf("b", "a"), ids())
        assertEquals("read", dao.getByOpenAlexId("W1")?.paper?.readingStatus)
        assertEquals(listOf("b", "a"), ids(match = "\"transf*\""))
        assertEquals(2, count("paper_search"))
    }

    @Test
    fun settingStatusOfUnknownPaperChangesNothing() = runTest {
        assertEquals(0, dao.setStatus("missing", "read"))
    }
}
```

`core/database/src/test/java/com/etatech/hashiya/core/database/migration/MigrationTest.kt`:
```kotlin
package com.etatech.hashiya.core.database.migration

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.etatech.hashiya.core.database.HashiyaDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val DB_NAME = "migration-test.db"

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HashiyaDatabase::class.java)

    /** A version 1 library as sub-project 2 left it: one paper with authors, one with no authors and no abstract or venue. */
    private fun createVersion1() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, " +
                    "oa_pdf_url, saved_at) VALUES ('a', 'W1', '10.48550/arxiv.1706.03762', 'Attention Is All You Need', 2017, " +
                    "'Neural Information Processing Systems', 'The dominant sequence transduction models', 128412, 1, NULL, 100)"
            )
            db.execSQL("INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 0, 'Ashish Vaswani', NULL)")
            db.execSQL("INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 1, 'Noam Shazeer', NULL)")
            db.execSQL(
                "INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, " +
                    "oa_pdf_url, saved_at) VALUES ('b', 'W2', NULL, 'تطبيقات التَّعلُّم العميق', NULL, NULL, NULL, 0, 0, NULL, 200)"
            )
        }
    }

    private fun SupportSQLiteDatabase.strings(sql: String): List<String> = query(sql).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    @Test
    fun migrationKeepsEveryPaperAsToReadAndValidatesAgainstVersion2Schema() {
        createVersion1()

        // Validates every table, including paper_search, against schemas/…/2.json.
        helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2).use { db ->
            assertEquals(listOf("a:to_read", "b:to_read"), db.strings("SELECT id || ':' || reading_status FROM papers ORDER BY id"))
            assertEquals(listOf("Ashish Vaswani", "Noam Shazeer"), db.strings("SELECT name FROM paper_authors ORDER BY position"))
            assertEquals(
                listOf("a:attention is all you need:ashish vaswani noam shazeer", "b:تطبيقات التعلم العميق:"),
                db.strings("SELECT paper_id || ':' || title || ':' || authors FROM paper_search ORDER BY paper_id")
            )
        }
    }

    @Test
    fun migratedLibraryOpensWithRoomAndIsSearchable() = runTest {
        createVersion1()
        helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2).close()

        val database = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.paperDao()
            suspend fun ids(match: String) = dao.observeLibrary(match, null).first().map { it.paper.id }

            assertEquals(listOf("b", "a"), dao.observeLibrary(null, null).first().map { it.paper.id })
            assertEquals(listOf("a"), ids("\"attention*\""))
            assertEquals(listOf("a"), ids("\"shazeer*\""))
            assertEquals(listOf("a"), ids("\"transduction*\""))
            assertEquals(listOf("b"), ids("\"التعلم*\""))
        } finally {
            database.close()
        }
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run (this first run downloads `room-testing`, so no `--offline`): `./gradlew :core:database:testDebugUnitTest`
Expected: FAIL — test compilation errors: `Unresolved reference 'searchEntityFor'`, `'StatusCount'`, `'observeLibrary'`, `'MIGRATION_1_2'`, `No parameter with name 'readingStatus' found`.

- [ ] **Step 4: Implement the schema**

`core/database/src/main/java/com/etatech/hashiya/core/database/model/PaperEntity.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "papers",
    indices = [
        Index(value = ["open_alex_id"], unique = true),
        // Not unique: OpenAlex sometimes has several works (preprint, published version) with one DOI,
        // and each must be savable. Deduplication by DOI is a later sub-project's decision.
        Index(value = ["doi"])
    ]
)
data class PaperEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "open_alex_id") val openAlexId: String?,
    val doi: String?,
    val title: String,
    val year: Int?,
    val venue: String?,
    val abstract: String?,
    @ColumnInfo(name = "citation_count") val citationCount: Int,
    @ColumnInfo(name = "is_open_access") val isOpenAccess: Boolean,
    @ColumnInfo(name = "oa_pdf_url") val oaPdfUrl: String?,
    @ColumnInfo(name = "saved_at") val savedAt: Long,
    /** One of "to_read", "reading", "read"; core/data maps it to ReadingStatus. */
    @ColumnInfo(name = "reading_status", defaultValue = "'to_read'") val readingStatus: String
)
```

`core/database/src/main/java/com/etatech/hashiya/core/database/model/PaperSearchEntity.kt`:
```kotlin
package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import com.etatech.hashiya.core.model.searchableText

/**
 * The full-text index: one row per saved paper, keyed by [paperId] (the paper's local id, not indexed).
 * A standalone FTS table, because author names live in another table; [PaperDao] keeps it in step with `papers`.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["paper_id"])
@Entity(tableName = "paper_search")
data class PaperSearchEntity(
    @ColumnInfo(name = "paper_id") val paperId: String,
    val title: String,
    /** Author names joined with spaces. */
    val authors: String,
    val abstract: String,
    val venue: String
)

/** The search row for a paper, every column passed through [searchableText]. Saves and the 1 → 2 migration both use it. */
fun searchEntityFor(paperId: String, title: String, authorNames: List<String>, abstract: String?, venue: String?): PaperSearchEntity =
    PaperSearchEntity(
        paperId = paperId,
        title = searchableText(title),
        authors = searchableText(authorNames.joinToString(" ")),
        abstract = searchableText(abstract.orEmpty()),
        venue = searchableText(venue.orEmpty())
    )
```

`core/database/src/main/java/com/etatech/hashiya/core/database/model/StatusCount.kt`:
```kotlin
package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo

/** How many saved papers have one stored reading status. */
data class StatusCount(@ColumnInfo(name = "reading_status") val readingStatus: String, val count: Int)
```

`core/database/src/main/java/com/etatech/hashiya/core/database/HashiyaDatabase.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.etatech.hashiya.core.database.dao.PaperDao
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperSearchEntity

@Database(
    entities = [PaperEntity::class, PaperAuthorEntity::class, PaperSearchEntity::class],
    version = 2,
    exportSchema = true
)
abstract class HashiyaDatabase : RoomDatabase() {
    abstract fun paperDao(): PaperDao
}
```

`core/database/src/main/java/com/etatech/hashiya/core/database/dao/PaperDao.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperSearchEntity
import com.etatech.hashiya.core.database.model.PaperWithAuthors
import com.etatech.hashiya.core.database.model.StatusCount
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PaperDao {
    /** Newest saved first. [match] is an FTS MATCH expression and [status] a stored status; null means "any". */
    @Transaction
    @Query(
        """
        SELECT papers.* FROM papers
        WHERE (:match IS NULL OR papers.id IN (SELECT paper_id FROM paper_search WHERE paper_search MATCH :match))
          AND (:status IS NULL OR papers.reading_status = :status)
        ORDER BY papers.saved_at DESC
        """
    )
    abstract fun observeLibrary(match: String?, status: String?): Flow<List<PaperWithAuthors>>

    /** How many papers matching [match] (null = all) have each stored status. Statuses with none are missing. */
    @Query(
        """
        SELECT reading_status, COUNT(*) AS count FROM papers
        WHERE (:match IS NULL OR papers.id IN (SELECT paper_id FROM paper_search WHERE paper_search MATCH :match))
        GROUP BY reading_status
        """
    )
    abstract fun observeStatusCounts(match: String?): Flow<List<StatusCount>>

    @Query("SELECT open_alex_id FROM papers WHERE open_alex_id IS NOT NULL")
    abstract fun observeSavedOpenAlexIds(): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM papers WHERE open_alex_id = :openAlexId")
    abstract suspend fun getByOpenAlexId(openAlexId: String): PaperWithAuthors?

    /** Returns the number of papers changed: 0 when the paper isn't saved. The search index is not touched. */
    @Query("UPDATE papers SET reading_status = :status WHERE open_alex_id = :openAlexId")
    abstract suspend fun setStatus(openAlexId: String, status: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPaper(paper: PaperEntity): Long

    @Insert
    abstract suspend fun insertAuthors(authors: List<PaperAuthorEntity>)

    @Insert
    abstract suspend fun insertSearch(search: PaperSearchEntity)

    @Query("DELETE FROM papers WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    // FTS rows don't cascade, so every paper deletion deletes its search row too.
    @Query("DELETE FROM paper_search WHERE paper_id = :id")
    abstract suspend fun deleteSearchById(id: String)

    /** Writes the paper, its authors and its search row atomically. Returns false, writing nothing, if it is already saved. */
    @Transaction
    open suspend fun insertPaperWithAuthors(paper: PaperEntity, authors: List<PaperAuthorEntity>, search: PaperSearchEntity): Boolean {
        require(search.paperId == paper.id) { "The search row must belong to the paper" }
        if (insertPaper(paper) == -1L) return false
        insertAuthors(authors)
        insertSearch(search)
        return true
    }

    /** Deletes the paper (authors cascade) and its search row, and returns what was deleted, so it can be restored. */
    @Transaction
    open suspend fun deleteByOpenAlexId(openAlexId: String): PaperWithAuthors? {
        val existing = getByOpenAlexId(openAlexId) ?: return null
        deleteById(existing.paper.id)
        deleteSearchById(existing.paper.id)
        return existing
    }
}
```

- [ ] **Step 5: Implement the migration and register it**

`core/database/src/main/java/com/etatech/hashiya/core/database/migration/Migrations.kt`:
```kotlin
package com.etatech.hashiya.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.etatech.hashiya.core.database.model.searchEntityFor

/** Adds the reading status (every existing paper becomes "to_read") and the search index, filled for every existing paper. */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE papers ADD COLUMN reading_status TEXT NOT NULL DEFAULT 'to_read'")
        // Exactly the statement Room generates for PaperSearchEntity (schemas/…/2.json), so the schema validates.
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `paper_search` USING FTS4(`paper_id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`authors` TEXT NOT NULL, `abstract` TEXT NOT NULL, `venue` TEXT NOT NULL, tokenize=unicode61, notindexed=`paper_id`)"
        )
        val authorNames = mutableMapOf<String, MutableList<String>>()
        db.query("SELECT paper_id, name FROM paper_authors ORDER BY paper_id, position").use { cursor ->
            while (cursor.moveToNext()) {
                authorNames.getOrPut(cursor.getString(0)) { mutableListOf() } += cursor.getString(1)
            }
        }
        db.query("SELECT id, title, abstract, venue FROM papers").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val row = searchEntityFor(
                    paperId = id,
                    title = cursor.getString(1),
                    authorNames = authorNames[id].orEmpty(),
                    abstract = if (cursor.isNull(2)) null else cursor.getString(2),
                    venue = if (cursor.isNull(3)) null else cursor.getString(3)
                )
                db.execSQL(
                    "INSERT INTO paper_search (paper_id, title, authors, abstract, venue) VALUES (?, ?, ?, ?, ?)",
                    arrayOf(row.paperId, row.title, row.authors, row.abstract, row.venue)
                )
            }
        }
    }
}
```

`core/database/src/main/java/com/etatech/hashiya/core/database/di/DatabaseModule.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.database.di

import android.content.Context
import androidx.room.Room
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.database.dao.PaperDao
import com.etatech.hashiya.core.database.migration.MIGRATION_1_2
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    // No destructive fallback: a failing migration must never delete the user's library.
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HashiyaDatabase =
        Room.databaseBuilder(context, HashiyaDatabase::class.java, "hashiya.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun providePaperDao(database: HashiyaDatabase): PaperDao = database.paperDao()
}
```

- [ ] **Step 6: Keep `core/data` compiling**

`core/data/src/main/java/com/etatech/hashiya/core/data/mapping/PaperEntityMapping.kt` (replace the whole file; Task 3 replaces it again with status support):
```kotlin
package com.etatech.hashiya.core.data.mapping

import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperSearchEntity
import com.etatech.hashiya.core.database.model.PaperWithAuthors
import com.etatech.hashiya.core.database.model.searchEntityFor
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper

internal data class PaperEntities(val paper: PaperEntity, val authors: List<PaperAuthorEntity>, val search: PaperSearchEntity)

internal fun Paper.asEntities(localId: String, savedAt: Long): PaperEntities = PaperEntities(
    paper = PaperEntity(
        id = localId,
        openAlexId = openAlexId,
        doi = doi,
        title = title,
        year = year,
        venue = venue,
        abstract = abstract,
        citationCount = citationCount,
        isOpenAccess = isOpenAccess,
        oaPdfUrl = openAccessPdfUrl,
        savedAt = savedAt,
        readingStatus = "to_read"
    ),
    authors = authors.mapIndexed { index, author ->
        PaperAuthorEntity(paperId = localId, position = index, name = author.name, openAlexAuthorId = author.openAlexId)
    },
    search = searchEntityFor(localId, title, authors.map { it.name }, abstract, venue)
)

internal fun PaperWithAuthors.asPaper(): Paper = Paper(
    openAlexId = requireNotNull(paper.openAlexId) { "Papers without an OpenAlex ID are not supported yet" },
    doi = paper.doi,
    title = paper.title,
    authors = authors.sortedBy { it.position }.map { Author(it.name, it.openAlexAuthorId) },
    year = paper.year,
    venue = paper.venue,
    abstract = paper.abstract,
    citationCount = paper.citationCount,
    isOpenAccess = paper.isOpenAccess,
    openAccessPdfUrl = paper.oaPdfUrl
)
```

`core/data/src/main/java/com/etatech/hashiya/core/data/repository/RoomLibraryRepository.kt` (replace the whole file; Task 3 replaces it again):
```kotlin
package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.data.mapping.asEntities
import com.etatech.hashiya.core.data.mapping.asPaper
import com.etatech.hashiya.core.database.dao.PaperDao
import com.etatech.hashiya.core.model.Paper
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomLibraryRepository(private val paperDao: PaperDao, private val now: () -> Long, private val newId: () -> String) :
    LibraryRepository {
    @Inject
    constructor(paperDao: PaperDao) : this(paperDao, System::currentTimeMillis, { UUID.randomUUID().toString() })

    override fun observeSavedPapers(): Flow<List<Paper>> = paperDao.observeLibrary(match = null, status = null).map { rows ->
        rows.map { it.asPaper() }
    }

    override fun observeSavedIds(): Flow<Set<String>> = paperDao.observeSavedOpenAlexIds().map { it.toSet() }

    override suspend fun save(paper: Paper) {
        val entities = paper.asEntities(localId = newId(), savedAt = now())
        paperDao.insertPaperWithAuthors(entities.paper, entities.authors, entities.search)
    }

    override suspend fun remove(openAlexId: String): RemovedPaper? = paperDao.deleteByOpenAlexId(openAlexId)?.let { row ->
        RemovedPaper(paper = row.asPaper(), localId = row.paper.id, savedAt = row.paper.savedAt)
    }

    override suspend fun restore(removed: RemovedPaper) {
        val entities = removed.paper.asEntities(localId = removed.localId, savedAt = removed.savedAt)
        paperDao.insertPaperWithAuthors(entities.paper, entities.authors, entities.search)
    }
}
```

- [ ] **Step 7: Run tests to verify they pass, and check the exported schema**

Run: `./gradlew :core:database:testDebugUnitTest :core:data:testDebugUnitTest :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; the 15 `PaperDaoTest` and 2 `MigrationTest` tests pass, and the existing `core/data` and `app` tests (the app's Hilt graph opens the real database) still pass.

Run: `git status --short core/database/schemas`
Expected: `?? core/database/schemas/com.etatech.hashiya.core.database.HashiyaDatabase/2.json`. Open it and check that the `paper_search` entity's `createSql` is exactly
```
CREATE VIRTUAL TABLE IF NOT EXISTS `${TABLE_NAME}` USING FTS4(`paper_id` TEXT NOT NULL, `title` TEXT NOT NULL, `authors` TEXT NOT NULL, `abstract` TEXT NOT NULL, `venue` TEXT NOT NULL, tokenize=unicode61, notindexed=`paper_id`)
```
(the migration's statement with `paper_search` for `${TABLE_NAME}`) and that the `papers` `createSql` ends with `` `reading_status` TEXT NOT NULL DEFAULT 'to_read', PRIMARY KEY(`id`)) ``. `1.json` must be unchanged.

- [ ] **Step 8: Format**

Run: `./gradlew spotlessApply`
Expected: `BUILD SUCCESSFUL`, no changes to the code above.

- [ ] **Step 9: Commit**

```bash
git add -A -- . ':(exclude).idea/**'
git commit -m "feat: add reading status and a full-text search index to the database, with a migration"
```

---

### Task 3: `core/data` — FTS queries, reading status and the new `LibraryRepository`

**Files:**
- Create: `core/data/src/main/java/com/etatech/hashiya/core/data/search/FtsQuery.kt`
- Create: `core/data/src/main/java/com/etatech/hashiya/core/data/mapping/ReadingStatusMapping.kt`
- Modify: `core/data/src/main/java/com/etatech/hashiya/core/data/mapping/PaperEntityMapping.kt` (full replacement below)
- Modify: `core/data/src/main/java/com/etatech/hashiya/core/data/repository/LibraryRepository.kt` (full replacement below)
- Modify: `core/data/src/main/java/com/etatech/hashiya/core/data/repository/RoomLibraryRepository.kt` (full replacement below)
- Modify: `core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeLibraryRepository.kt` (full replacement below)
- Modify: `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryViewModel.kt` (one line; Task 5 replaces the file)
- Test: `core/data/src/test/java/com/etatech/hashiya/core/data/search/FtsQueryTest.kt`
- Test: `core/data/src/test/java/com/etatech/hashiya/core/data/mapping/ReadingStatusMappingTest.kt`
- Test: `core/data/src/test/java/com/etatech/hashiya/core/data/mapping/PaperEntityMappingTest.kt` (full replacement below)
- Test: `core/data/src/test/java/com/etatech/hashiya/core/data/repository/RoomLibraryRepositoryTest.kt` (full replacement below)
- Test: `core/testing/src/test/java/com/etatech/hashiya/core/testing/FakeLibraryRepositoryTest.kt` (full replacement below)
- Test (call sites of the removed `observeSavedPapers` and the new `RemovedPaper.status`): `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryViewModelTest.kt`, `LibraryContentTest.kt`, `feature/search/src/test/java/com/etatech/hashiya/feature/search/SearchViewModelTest.kt`

**Interfaces:**
- Consumes: `ReadingStatus`, `LibraryPaper`, `searchableText` (Task 1); `PaperDao.observeLibrary/observeStatusCounts/setStatus/insertPaperWithAuthors(paper, authors, search)/deleteByOpenAlexId`, `searchEntityFor`, `PaperEntity.readingStatus`, `StatusCount` (Task 2).
- Produces:
  - `internal fun ftsMatch(query: String): String?` (`com.etatech.hashiya.core.data.search`).
  - `internal val ReadingStatus.storedValue: String`, `internal fun readingStatusOf(stored: String): ReadingStatus`.
  - `internal fun Paper.asEntities(localId: String, savedAt: Long, status: ReadingStatus): PaperEntities`.
  - `LibraryRepository { observeLibrary(query: String, status: ReadingStatus?): Flow<List<LibraryPaper>>; observeStatusCounts(query: String): Flow<Map<ReadingStatus, Int>>; observeSavedIds(); suspend save(paper); suspend setStatus(openAlexId: String, status: ReadingStatus); suspend remove(openAlexId): RemovedPaper?; suspend restore(removed) }` — `observeSavedPapers()` is removed.
  - `data class RemovedPaper(val paper: Paper, val localId: String, val savedAt: Long, val status: ReadingStatus)`.
  - `FakeLibraryRepository` gains `failOnSetStatus: Boolean` and searches like the index (every typed word must start a word of the title, authors, abstract or venue, after `searchableText`).

- [ ] **Step 1: Write the failing tests**

`core/data/src/test/java/com/etatech/hashiya/core/data/search/FtsQueryTest.kt`:
```kotlin
package com.etatech.hashiya.core.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {
    @Test
    fun eachWordBecomesAQuotedPrefixTerm() {
        assertEquals("\"transf*\"", ftsMatch("transf"))
        assertEquals("\"deep*\" \"learning*\"", ftsMatch("  Deep   LEARNING "))
    }

    @Test
    fun normalizesLikeTheIndex() {
        assertEquals("\"schrodinger*\"", ftsMatch("Schrödinger"))
        assertEquals("\"التعلم*\"", ftsMatch("التَّعلُّم"))
        assertEquals("\"احمد*\"", ftsMatch("أحمد"))
    }

    @Test
    fun ftsSyntaxBecomesPlainWords() {
        assertEquals("\"c*\"", ftsMatch("C++"))
        assertEquals("\"attention*\"", ftsMatch("\"attention"))
        assertEquals("\"title*\" \"deep*\"", ftsMatch("title:deep"))
        assertEquals("\"bert*\" \"gpt*\"", ftsMatch("-bert (gpt*)"))
        assertEquals("\"ming*\" \"wei*\"", ftsMatch("Ming-Wei"))
    }

    @Test
    fun operatorWordsAreSearchedAsWords() {
        assertEquals("\"cats*\" \"and*\" \"dogs*\"", ftsMatch("cats AND dogs"))
        assertEquals("\"or*\" \"not*\" \"near*\"", ftsMatch("OR NOT NEAR"))
    }

    @Test
    fun blankOrPunctuationOnlyMeansNoSearch() {
        assertNull(ftsMatch(""))
        assertNull(ftsMatch("   "))
        assertNull(ftsMatch("\"*-():"))
        assertNull(ftsMatch("ـــ"))
    }
}
```

`core/data/src/test/java/com/etatech/hashiya/core/data/mapping/ReadingStatusMappingTest.kt`:
```kotlin
package com.etatech.hashiya.core.data.mapping

import com.etatech.hashiya.core.model.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStatusMappingTest {
    /** These strings are stored on the user's phone: renaming the enum must never change them. */
    @Test
    fun storedValuesAreFixed() {
        assertEquals(listOf("to_read", "reading", "read"), ReadingStatus.entries.map { it.storedValue })
    }

    @Test
    fun readsStoredValuesBack() {
        ReadingStatus.entries.forEach { assertEquals(it, readingStatusOf(it.storedValue)) }
    }

    @Test
    fun unknownStoredValueReadsAsToRead() {
        assertEquals(ReadingStatus.ToRead, readingStatusOf("archived"))
        assertEquals(ReadingStatus.ToRead, readingStatusOf(""))
        assertEquals(ReadingStatus.ToRead, readingStatusOf("ToRead"))
    }
}
```

`core/data/src/test/java/com/etatech/hashiya/core/data/mapping/PaperEntityMappingTest.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.data.mapping

import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperWithAuthors
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PaperEntityMappingTest {
    private val paper = Paper(
        openAlexId = "W1",
        doi = "10.1000/xyz",
        title = "Title",
        authors = listOf(Author("First", "A1"), Author("Second", null)),
        year = 2020,
        venue = "Venue",
        abstract = "Abstract",
        citationCount = 7,
        isOpenAccess = true,
        openAccessPdfUrl = "https://example.org/x.pdf"
    )

    @Test
    fun roundTripsThroughEntities() {
        val entities = paper.asEntities(localId = "local-1", savedAt = 42L, status = ReadingStatus.Reading)

        assertEquals("local-1", entities.paper.id)
        assertEquals(42L, entities.paper.savedAt)
        assertEquals("reading", entities.paper.readingStatus)
        assertEquals(listOf(0, 1), entities.authors.map { it.position })
        assertEquals(paper, PaperWithAuthors(entities.paper, entities.authors).asPaper())
    }

    @Test
    fun sortsAuthorsByPositionWhenReading() {
        val entities = paper.asEntities(localId = "local-1", savedAt = 42L, status = ReadingStatus.ToRead)
        val shuffled = PaperWithAuthors(entities.paper, entities.authors.reversed())

        assertEquals(listOf("First", "Second"), shuffled.asPaper().authors.map { it.name })
    }

    @Test
    fun authorEntitiesPointAtThePaper() {
        val authors: List<PaperAuthorEntity> = paper.asEntities(localId = "local-1", savedAt = 0, status = ReadingStatus.ToRead).authors
        assertEquals(setOf("local-1"), authors.map { it.paperId }.toSet())
    }

    @Test
    fun searchRowHoldsNormalizedTextForThePaper() {
        val search = paper.copy(title = "Schrödinger", abstract = null, venue = null)
            .asEntities(localId = "local-1", savedAt = 0, status = ReadingStatus.ToRead)
            .search

        assertEquals("local-1", search.paperId)
        assertEquals("schrodinger", search.title)
        assertEquals("first second", search.authors)
        assertEquals("", search.abstract)
        assertEquals("", search.venue)
    }
}
```

`core/data/src/test/java/com/etatech/hashiya/core/data/repository/RoomLibraryRepositoryTest.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomLibraryRepositoryTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var repository: RoomLibraryRepository
    private var clock = 0L
    private var idCounter = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomLibraryRepository(db.paperDao(), now = { ++clock }, newId = { "local-${++idCounter}" })
    }

    @After
    fun tearDown() = db.close()

    private fun paper(id: String, title: String = "Paper $id", abstract: String? = null, venue: String? = null) = Paper(
        openAlexId = id,
        doi = null,
        title = title,
        authors = listOf(Author("First", null), Author("Second", null)),
        year = 2020,
        venue = venue,
        abstract = abstract,
        citationCount = 0,
        isOpenAccess = false,
        openAccessPdfUrl = null
    )

    private suspend fun ids(query: String = "", status: ReadingStatus? = null) =
        repository.observeLibrary(query, status).first().map { it.paper.openAlexId }

    @Test
    fun savedPapersAreNewestFirstWithAuthorsInOrderAndStartAsToRead() = runTest {
        repository.save(paper("W1"))
        repository.save(paper("W2"))

        val saved = repository.observeLibrary("", null).first()
        assertEquals(listOf("W2", "W1"), saved.map { it.paper.openAlexId })
        assertEquals(listOf("First", "Second"), saved.first().paper.authors.map { it.name })
        assertEquals(listOf(ReadingStatus.ToRead, ReadingStatus.ToRead), saved.map { it.status })
    }

    @Test
    fun observesSavedIds() = runTest {
        repository.save(paper("W1"))
        assertEquals(setOf("W1"), repository.observeSavedIds().first())
    }

    @Test
    fun savingTwiceKeepsOneCopyAndItsStatus() = runTest {
        repository.save(paper("W1"))
        repository.setStatus("W1", ReadingStatus.Reading)
        repository.save(paper("W1"))

        assertEquals(listOf(LibraryPaper(paper("W1"), ReadingStatus.Reading)), repository.observeLibrary("", null).first())
    }

    @Test
    fun removeThenRestoreReturnsPaperToItsPositionWithItsStatus() = runTest {
        repository.save(paper("W1"))
        repository.save(paper("W2"))
        repository.save(paper("W3"))
        repository.setStatus("W2", ReadingStatus.Reading)

        val removed = repository.remove("W2")!!
        assertEquals(ReadingStatus.Reading, removed.status)
        assertEquals(listOf("W3", "W1"), ids())

        repository.restore(removed)
        assertEquals(listOf("W3", "W2", "W1"), ids())
        assertEquals(listOf("W2"), ids(status = ReadingStatus.Reading))
        assertEquals(listOf("W2"), ids(query = "paper w2"))
    }

    @Test
    fun restoreAfterPaperWasSavedAgainIsNoOp() = runTest {
        repository.save(paper("W1"))
        val removed = repository.remove("W1")!!
        repository.save(paper("W1"))

        repository.restore(removed)

        assertEquals(1, repository.observeLibrary("", null).first().size)
    }

    @Test
    fun removingUnknownPaperReturnsNull() = runTest {
        assertNull(repository.remove("missing"))
    }

    @Test
    fun worksSharingADoiAreBothSaved() = runTest {
        repository.save(paper("W1").copy(doi = "10.1000/xyz"))
        repository.save(paper("W2").copy(doi = "10.1000/xyz"))

        assertEquals(setOf("W1", "W2"), repository.observeSavedIds().first())
    }

    @Test
    fun setStatusDoesNotReorder() = runTest {
        repository.save(paper("W1"))
        repository.save(paper("W2"))

        repository.setStatus("W1", ReadingStatus.Read)

        assertEquals(listOf("W2", "W1"), ids())
        assertEquals(listOf("W1"), ids(status = ReadingStatus.Read))
    }

    @Test
    fun setStatusOfUnsavedPaperDoesNothing() = runTest {
        repository.setStatus("missing", ReadingStatus.Read)
        assertEquals(emptyList<String>(), ids())
    }

    @Test
    fun searchFindsTitleAuthorAbstractAndVenueByPrefix() = runTest {
        repository.save(paper("W1", title = "Attention Is All You Need", abstract = "The Transformer architecture", venue = "NeurIPS"))
        repository.save(paper("W2", title = "Deep Residual Learning", venue = "CVPR").copy(authors = listOf(Author("Kaiming He", null))))

        assertEquals(listOf("W1"), ids("transf"))
        assertEquals(listOf("W2"), ids("kaiming"))
        assertEquals(listOf("W1"), ids("neurips"))
        assertEquals(listOf("W2"), ids("DEEP resid"))
        assertEquals(emptyList<String>(), ids("attention residual"))
        assertEquals(listOf("W2", "W1"), ids("   "))
    }

    @Test
    fun searchIgnoresAccentsTashkeelAndAlefForms() = runTest {
        repository.save(paper("W1", title = "Schrödinger equations"))
        repository.save(paper("W2", title = "تطبيقات التعلم العميق في معالجة اللغة"))
        repository.save(paper("W3", title = "أساسيات الإحصاء"))

        assertEquals(listOf("W1"), ids("schrodinger"))
        assertEquals(listOf("W2"), ids("التَّعلُّم"))
        assertEquals(listOf("W3"), ids("اساسيات"))
        assertEquals(listOf("W3"), ids("الاحصاء"))
    }

    /** Whatever the user types, the query reaches SQLite as plain words: never a syntax error. */
    @Test
    fun searchTextWithFtsSyntaxNeverFails() = runTest {
        repository.save(paper("W1", title = "C++ templates: a guide"))

        val queries = listOf("\"", "C++", "templates\"", "-templates", "(guide", "title:guide", "BERT:", "a AND", "NEAR/2", "*", "^x")
        queries.forEach { query ->
            repository.observeLibrary(query, null).first()
            repository.observeStatusCounts(query).first()
        }
        assertEquals(listOf("W1"), ids("\"templates"))
        assertEquals(listOf("W1"), ids("*"))
    }

    @Test
    fun statusCountsFollowTheSearchAndFillMissingStatusesWithZero() = runTest {
        repository.save(paper("W1", title = "Transformers one"))
        repository.save(paper("W2", title = "Transformers two"))
        repository.save(paper("W3", title = "Convolutions"))
        repository.setStatus("W1", ReadingStatus.Reading)

        assertEquals(
            mapOf(ReadingStatus.ToRead to 2, ReadingStatus.Reading to 1, ReadingStatus.Read to 0),
            repository.observeStatusCounts("").first()
        )
        assertEquals(
            mapOf(ReadingStatus.ToRead to 1, ReadingStatus.Reading to 1, ReadingStatus.Read to 0),
            repository.observeStatusCounts("transf").first()
        )
        assertEquals(
            mapOf(ReadingStatus.ToRead to 0, ReadingStatus.Reading to 0, ReadingStatus.Read to 0),
            repository.observeStatusCounts("missing").first()
        )
    }

    @Test
    fun unknownStoredStatusReadsAsToReadAndIsCountedThere() = runTest {
        repository.save(paper("W1"))
        repository.save(paper("W2"))
        db.openHelper.writableDatabase.execSQL("UPDATE papers SET reading_status = 'archived' WHERE open_alex_id = 'W1'")

        assertEquals(ReadingStatus.ToRead, repository.observeLibrary("", null).first().last().status)
        assertEquals(
            mapOf(ReadingStatus.ToRead to 2, ReadingStatus.Reading to 0, ReadingStatus.Read to 0),
            repository.observeStatusCounts("").first()
        )
    }
}
```

`core/testing/src/test/java/com/etatech/hashiya/core/testing/FakeLibraryRepositoryTest.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The fake must behave like the real repository, or feature tests prove nothing. */
class FakeLibraryRepositoryTest {
    private val repository = FakeLibraryRepository()

    private suspend fun titles(query: String = "", status: ReadingStatus? = null) =
        repository.observeLibrary(query, status).first().map { it.paper.title }

    @Test
    fun behavesLikeRoomRepository() = runTest {
        repository.save(SamplePapers.attention)
        repository.save(SamplePapers.bert)
        repository.save(SamplePapers.attention)
        assertEquals(
            listOf(LibraryPaper(SamplePapers.bert, ReadingStatus.ToRead), LibraryPaper(SamplePapers.attention, ReadingStatus.ToRead)),
            repository.observeLibrary("", null).first()
        )

        repository.setStatus(SamplePapers.attention.openAlexId, ReadingStatus.Reading)
        val removed = repository.remove(SamplePapers.attention.openAlexId)!!
        repository.restore(removed)
        assertEquals(
            listOf(LibraryPaper(SamplePapers.bert, ReadingStatus.ToRead), LibraryPaper(SamplePapers.attention, ReadingStatus.Reading)),
            repository.observeLibrary("", null).first()
        )
        assertEquals(
            setOf(SamplePapers.attention.openAlexId, SamplePapers.bert.openAlexId),
            repository.observeSavedIds().first()
        )
    }

    @Test
    fun searchesLikeTheIndex() = runTest {
        SamplePapers.all.forEach { repository.save(it) }
        repository.save(SamplePapers.arabicTitled)

        assertEquals(listOf(SamplePapers.vit.title, SamplePapers.bert.title, SamplePapers.attention.title), titles("transf"))
        assertEquals(listOf(SamplePapers.attention.title), titles("transf vaswani"))
        assertEquals(listOf(SamplePapers.bert.title), titles("naacl"))
        assertEquals(listOf(SamplePapers.arabicTitled.title), titles("التَّعلُّم"))
        assertEquals(emptyList<String>(), titles("vaswani devlin"))
        assertEquals(4, titles("  ").size)
    }

    @Test
    fun filtersAndCountsByStatus() = runTest {
        SamplePapers.all.forEach { repository.save(it) }
        repository.setStatus(SamplePapers.bert.openAlexId, ReadingStatus.Read)

        assertEquals(listOf(SamplePapers.bert.title), titles(status = ReadingStatus.Read))
        assertEquals(
            mapOf(ReadingStatus.ToRead to 2, ReadingStatus.Reading to 0, ReadingStatus.Read to 1),
            repository.observeStatusCounts("").first()
        )
        assertEquals(
            mapOf(ReadingStatus.ToRead to 0, ReadingStatus.Reading to 0, ReadingStatus.Read to 1),
            repository.observeStatusCounts("naacl").first()
        )
    }
}
```

Move the other callers of `observeSavedPapers()` and `RemovedPaper(...)`:

In `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryViewModelTest.kt`, in `undoRestoresPaperInItsPlace`, replace
```kotlin
        assertEquals(listOf(SamplePapers.vit, SamplePapers.bert, SamplePapers.attention), repository.observeSavedPapers().first())
```
with
```kotlin
        assertEquals(
            listOf(SamplePapers.vit, SamplePapers.bert, SamplePapers.attention),
            repository.observeLibrary("", null).first().map { it.paper }
        )
```
and in `twoQuickRemovalsKeepOnlyTheLatestForUndo`, replace
```kotlin
        assertEquals(listOf(SamplePapers.bert), repository.observeSavedPapers().first())
```
with
```kotlin
        assertEquals(listOf(SamplePapers.bert), repository.observeLibrary("", null).first().map { it.paper })
```

In `feature/search/src/test/java/com/etatech/hashiya/feature/search/SearchViewModelTest.kt`, in `toggleSaveSavesThenRemoves`, replace
```kotlin
        assertEquals(listOf(SamplePapers.bert), libraryRepository.observeSavedPapers().first())
```
with
```kotlin
        assertEquals(listOf(SamplePapers.bert), libraryRepository.observeLibrary("", null).first().map { it.paper })
```
and replace
```kotlin
        assertTrue(libraryRepository.observeSavedPapers().first().isEmpty())
```
with
```kotlin
        assertTrue(libraryRepository.observeLibrary("", null).first().isEmpty())
```

In `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryContentTest.kt`, add the import `import com.etatech.hashiya.core.model.ReadingStatus` and replace
```kotlin
    private val removedBert = RemovedPaper(SamplePapers.bert, localId = "local-1", savedAt = 1)
    private val removedVit = RemovedPaper(SamplePapers.vit, localId = "local-2", savedAt = 2)
```
with
```kotlin
    private val removedBert = RemovedPaper(SamplePapers.bert, localId = "local-1", savedAt = 1, status = ReadingStatus.ToRead)
    private val removedVit = RemovedPaper(SamplePapers.vit, localId = "local-2", savedAt = 2, status = ReadingStatus.ToRead)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: FAIL — test compilation errors: `Unresolved reference 'ftsMatch'`, `'storedValue'`, `'readingStatusOf'`, `'observeLibrary'`, `'setStatus'`, `No parameter with name 'status' found`.

- [ ] **Step 3: Implement**

`core/data/src/main/java/com/etatech/hashiya/core/data/search/FtsQuery.kt`:
```kotlin
package com.etatech.hashiya.core.data.search

import com.etatech.hashiya.core.model.searchableText

private val NOT_LETTER_OR_DIGIT = Regex("""[^\p{L}\p{N}]+""")

/**
 * The FTS MATCH expression for what the user typed, or null when no word is left ("no search").
 * Each word becomes a quoted prefix term and every word must match: "Deep lear" → `"deep*" "lear*"`.
 * Only letters and digits survive, so FTS syntax (quotes, `*`, `-`, parentheses, `column:`, operators) never reaches MATCH.
 * FTS4 reads a prefix only inside the quotes (`"transf*"`); `"transf"*` would match the exact word.
 */
internal fun ftsMatch(query: String): String? = searchableText(query)
    .split(NOT_LETTER_OR_DIGIT)
    .filter { it.isNotEmpty() }
    .takeIf { it.isNotEmpty() }
    ?.joinToString(" ") { "\"$it*\"" }
```

`core/data/src/main/java/com/etatech/hashiya/core/data/mapping/ReadingStatusMapping.kt`:
```kotlin
package com.etatech.hashiya.core.data.mapping

import com.etatech.hashiya.core.model.ReadingStatus

/** The value stored in `papers.reading_status`. Fixed strings, so renaming the enum never changes stored data. */
internal val ReadingStatus.storedValue: String
    get() = when (this) {
        ReadingStatus.ToRead -> "to_read"
        ReadingStatus.Reading -> "reading"
        ReadingStatus.Read -> "read"
    }

/** Reads a stored value back; anything unknown is To read. */
internal fun readingStatusOf(stored: String): ReadingStatus =
    ReadingStatus.entries.firstOrNull { it.storedValue == stored } ?: ReadingStatus.ToRead
```

`core/data/src/main/java/com/etatech/hashiya/core/data/mapping/PaperEntityMapping.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.data.mapping

import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperSearchEntity
import com.etatech.hashiya.core.database.model.PaperWithAuthors
import com.etatech.hashiya.core.database.model.searchEntityFor
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus

internal data class PaperEntities(val paper: PaperEntity, val authors: List<PaperAuthorEntity>, val search: PaperSearchEntity)

internal fun Paper.asEntities(localId: String, savedAt: Long, status: ReadingStatus): PaperEntities = PaperEntities(
    paper = PaperEntity(
        id = localId,
        openAlexId = openAlexId,
        doi = doi,
        title = title,
        year = year,
        venue = venue,
        abstract = abstract,
        citationCount = citationCount,
        isOpenAccess = isOpenAccess,
        oaPdfUrl = openAccessPdfUrl,
        savedAt = savedAt,
        readingStatus = status.storedValue
    ),
    authors = authors.mapIndexed { index, author ->
        PaperAuthorEntity(paperId = localId, position = index, name = author.name, openAlexAuthorId = author.openAlexId)
    },
    search = searchEntityFor(localId, title, authors.map { it.name }, abstract, venue)
)

internal fun PaperWithAuthors.asPaper(): Paper = Paper(
    openAlexId = requireNotNull(paper.openAlexId) { "Papers without an OpenAlex ID are not supported yet" },
    doi = paper.doi,
    title = paper.title,
    authors = authors.sortedBy { it.position }.map { Author(it.name, it.openAlexAuthorId) },
    year = paper.year,
    venue = paper.venue,
    abstract = paper.abstract,
    citationCount = paper.citationCount,
    isOpenAccess = paper.isOpenAccess,
    openAccessPdfUrl = paper.oaPdfUrl
)
```

`core/data/src/main/java/com/etatech/hashiya/core/data/repository/LibraryRepository.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    /** Papers matching [query] (blank = all) and [status] (null = all), newest saved first. */
    fun observeLibrary(query: String, status: ReadingStatus?): Flow<List<LibraryPaper>>

    /** How many papers of each status match [query]; statuses with none are 0. */
    fun observeStatusCounts(query: String): Flow<Map<ReadingStatus, Int>>

    fun observeSavedIds(): Flow<Set<String>>

    /** New papers start as To read. Saving a paper that is already saved does nothing. */
    suspend fun save(paper: Paper)

    /** Changes the status without reordering the library. Does nothing if the paper isn't saved. */
    suspend fun setStatus(openAlexId: String, status: ReadingStatus)

    /** Returns what was removed, for Undo, or null if the paper was not saved. */
    suspend fun remove(openAlexId: String): RemovedPaper?

    /** Puts a removed paper back where it was, with its status. Does nothing if it has been saved again meanwhile. */
    suspend fun restore(removed: RemovedPaper)
}

data class RemovedPaper(val paper: Paper, val localId: String, val savedAt: Long, val status: ReadingStatus)
```

`core/data/src/main/java/com/etatech/hashiya/core/data/repository/RoomLibraryRepository.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.data.mapping.asEntities
import com.etatech.hashiya.core.data.mapping.asPaper
import com.etatech.hashiya.core.data.mapping.readingStatusOf
import com.etatech.hashiya.core.data.mapping.storedValue
import com.etatech.hashiya.core.data.search.ftsMatch
import com.etatech.hashiya.core.database.dao.PaperDao
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomLibraryRepository(private val paperDao: PaperDao, private val now: () -> Long, private val newId: () -> String) :
    LibraryRepository {
    @Inject
    constructor(paperDao: PaperDao) : this(paperDao, System::currentTimeMillis, { UUID.randomUUID().toString() })

    override fun observeLibrary(query: String, status: ReadingStatus?): Flow<List<LibraryPaper>> =
        paperDao.observeLibrary(ftsMatch(query), status?.storedValue).map { rows ->
            rows.map { LibraryPaper(it.asPaper(), readingStatusOf(it.paper.readingStatus)) }
        }

    override fun observeStatusCounts(query: String): Flow<Map<ReadingStatus, Int>> =
        paperDao.observeStatusCounts(ftsMatch(query)).map { rows ->
            // Unknown stored values read as To read, so they are counted there too.
            val counts = ReadingStatus.entries.associateWith { 0 }.toMutableMap()
            rows.forEach { row -> counts.merge(readingStatusOf(row.readingStatus), row.count, Int::plus) }
            counts
        }

    override fun observeSavedIds(): Flow<Set<String>> = paperDao.observeSavedOpenAlexIds().map { it.toSet() }

    override suspend fun save(paper: Paper) {
        val entities = paper.asEntities(localId = newId(), savedAt = now(), status = ReadingStatus.ToRead)
        paperDao.insertPaperWithAuthors(entities.paper, entities.authors, entities.search)
    }

    override suspend fun setStatus(openAlexId: String, status: ReadingStatus) {
        paperDao.setStatus(openAlexId, status.storedValue)
    }

    override suspend fun remove(openAlexId: String): RemovedPaper? = paperDao.deleteByOpenAlexId(openAlexId)?.let { row ->
        RemovedPaper(
            paper = row.asPaper(),
            localId = row.paper.id,
            savedAt = row.paper.savedAt,
            status = readingStatusOf(row.paper.readingStatus)
        )
    }

    override suspend fun restore(removed: RemovedPaper) {
        val entities = removed.paper.asEntities(localId = removed.localId, savedAt = removed.savedAt, status = removed.status)
        paperDao.insertPaperWithAuthors(entities.paper, entities.authors, entities.search)
    }
}
```

`core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeLibraryRepository.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.model.searchableText
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeLibraryRepository : LibraryRepository {
    private val rows = MutableStateFlow<List<RemovedPaper>>(emptyList())
    private var clock = 0L

    /** When true, [save] throws like a failing disk would. */
    var failOnSave = false

    /** When true, [remove] throws like a failing disk would. */
    var failOnRemove = false

    /** When true, [setStatus] throws like a failing disk would. */
    var failOnSetStatus = false

    override fun observeLibrary(query: String, status: ReadingStatus?): Flow<List<LibraryPaper>> = rows.map { list ->
        list.filter { (status == null || it.status == status) && it.paper.matches(query) }
            .sortedByDescending { it.savedAt }
            .map { LibraryPaper(it.paper, it.status) }
    }

    override fun observeStatusCounts(query: String): Flow<Map<ReadingStatus, Int>> = rows.map { list ->
        val matching = list.filter { it.paper.matches(query) }
        ReadingStatus.entries.associateWith { status -> matching.count { it.status == status } }
    }

    override fun observeSavedIds(): Flow<Set<String>> = rows.map { list -> list.map { it.paper.openAlexId }.toSet() }

    override suspend fun save(paper: Paper) {
        if (failOnSave) throw IOException("disk full")
        if (isSaved(paper.openAlexId)) return
        rows.update { it + RemovedPaper(paper, localId = "local-${paper.openAlexId}", savedAt = ++clock, status = ReadingStatus.ToRead) }
    }

    override suspend fun setStatus(openAlexId: String, status: ReadingStatus) {
        if (failOnSetStatus) throw IOException("disk full")
        rows.update { list -> list.map { if (it.paper.openAlexId == openAlexId) it.copy(status = status) else it } }
    }

    override suspend fun remove(openAlexId: String): RemovedPaper? {
        if (failOnRemove) throw IOException("disk full")
        val row = rows.value.firstOrNull { it.paper.openAlexId == openAlexId } ?: return null
        rows.update { it - row }
        return row
    }

    override suspend fun restore(removed: RemovedPaper) {
        if (isSaved(removed.paper.openAlexId)) return
        rows.update { it + removed }
    }

    private fun isSaved(openAlexId: String) = rows.value.any { it.paper.openAlexId == openAlexId }
}

private val NOT_LETTER_OR_DIGIT = Regex("""[^\p{L}\p{N}]+""")

private fun words(text: String) = searchableText(text).split(NOT_LETTER_OR_DIGIT).filter { it.isNotEmpty() }

/** Like the real index: every word of [query] must start a word of the title, authors, abstract or venue. */
private fun Paper.matches(query: String): Boolean {
    val indexed = words(listOf(title, authors.joinToString(" ") { it.name }, abstract.orEmpty(), venue.orEmpty()).joinToString(" "))
    return words(query).all { word -> indexed.any { it.startsWith(word) } }
}
```

In `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryViewModel.kt`, replace
```kotlin
    private val savedPapers = libraryRepository.observeSavedPapers()
```
with
```kotlin
    private val savedPapers = libraryRepository.observeLibrary(query = "", status = null).map { list -> list.map { it.paper } }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest :core:testing:testDebugUnitTest :feature:library:testDebugUnitTest :feature:search:testDebugUnitTest :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; `FtsQueryTest` (5), `ReadingStatusMappingTest` (3), `PaperEntityMappingTest` (4), `RoomLibraryRepositoryTest` (14) and `FakeLibraryRepositoryTest` (3) pass, and every existing test still passes.

- [ ] **Step 5: Format**

Run: `./gradlew spotlessApply`
Expected: `BUILD SUCCESSFUL`, no changes to the code above.

- [ ] **Step 6: Commit**

```bash
git add -A -- . ':(exclude).idea/**'
git commit -m "feat: search the library and track reading status in the repository"
```

---

### Task 4: `core/designsystem` — status labels and the preview's status selector

**Files:**
- Modify: `core/designsystem/src/main/res/values/strings.xml`, `core/designsystem/src/main/res/values-ar/strings.xml`
- Create: `core/designsystem/src/main/java/com/etatech/hashiya/core/designsystem/component/ReadingStatusSelector.kt`
- Modify: `core/designsystem/src/main/java/com/etatech/hashiya/core/designsystem/component/PaperPreview.kt` (full replacement below)
- Modify: `core/designsystem/src/main/java/com/etatech/hashiya/core/designsystem/component/MessageStates.kt` (full replacement below)
- Test: `core/designsystem/src/test/java/com/etatech/hashiya/core/designsystem/component/PaperPreviewTest.kt` (full replacement below)
- Test: `core/designsystem/src/test/java/com/etatech/hashiya/core/designsystem/component/MessageStatesTest.kt` (full replacement below)
- Test: `core/designsystem/src/test/java/com/etatech/hashiya/core/designsystem/component/PaperScreenshotTest.kt` (full replacement below)
- Test output: 4 new `paper_preview_status-*` baselines; no existing baseline changes (Search passes no status).

**Interfaces:**
- Consumes: `ReadingStatus` (Task 1).
- Produces:
  - `@Composable fun readingStatusLabel(status: ReadingStatus): String` — the only source of the three labels (badge, menu, chips, selector).
  - `@Composable fun ReadingStatusSelector(status: ReadingStatus, onStatusChange: (ReadingStatus) -> Unit, modifier: Modifier = Modifier)`.
  - `PaperPreviewContent(paper, inLibrary, onToggleSave, onOpenDoi, modifier = Modifier, status: ReadingStatus? = null, onStatusChange: (ReadingStatus) -> Unit = {})` and `PaperPreviewSheet(paper, inLibrary, onDismiss, onToggleSave, onOpenDoi, status: ReadingStatus? = null, onStatusChange: (ReadingStatus) -> Unit = {})`.
  - `EmptyState(icon, title, message: String?, modifier, actionLabel, onAction)` — `null` shows no message line.

- [ ] **Step 1: Write the failing tests**

`core/designsystem/src/test/java/com/etatech/hashiya/core/designsystem/component/PaperPreviewTest.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.SamplePapers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaperPreviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var toggles = 0
    private val openedDois = mutableListOf<String>()

    private fun show(paper: Paper, inLibrary: Boolean) = composeRule.setContent {
        HashiyaTheme {
            PaperPreviewContent(paper, inLibrary, onToggleSave = { toggles++ }, onOpenDoi = { openedDois += it })
        }
    }

    @Test
    fun unsavedPaperOffersSaveToLibrary() {
        show(SamplePapers.bert, inLibrary = false)

        composeRule.onNodeWithText("Save to library").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun savedPaperOffersRemove() {
        show(SamplePapers.bert, inLibrary = true)
        composeRule.onNodeWithText("Remove from library").assertIsDisplayed()
    }

    @Test
    fun openDoiPassesTheDoi() {
        show(SamplePapers.bert, inLibrary = false)

        composeRule.onNodeWithText("Open DOI").performClick()
        assertEquals(listOf("10.18653/v1/n19-1423"), openedDois)
    }

    @Test
    fun hidesOpenDoiWithoutDoi() {
        show(SamplePapers.vit, inLibrary = false)
        composeRule.onNodeWithText("Open DOI").assertDoesNotExist()
    }

    @Test
    fun showsAllAuthorsAndFullCitationCount() {
        show(SamplePapers.attention, inLibrary = false)

        composeRule.onNodeWithText(
            "Ashish Vaswani, Noam Shazeer, Niki Parmar, Jakob Uszkoreit, Llion Jones"
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Neural Information Processing Systems · 2017 · 128,412 citations").assertIsDisplayed()
        composeRule.onNodeWithText("Open access · PDF available").assertIsDisplayed()
    }

    @Test
    fun missingAbstractIsExplained() {
        show(SamplePapers.vit, inLibrary = false)
        composeRule.onNodeWithText("No abstract available").assertIsDisplayed()
    }

    @Test
    fun statusSelectorShowsTheStatusAndChangesIt() {
        val changes = mutableListOf<ReadingStatus>()
        composeRule.setContent {
            HashiyaTheme {
                PaperPreviewContent(
                    SamplePapers.bert,
                    inLibrary = true,
                    onToggleSave = {},
                    onOpenDoi = {},
                    status = ReadingStatus.Reading,
                    onStatusChange = { changes += it }
                )
            }
        }

        composeRule.onNodeWithText("Reading").assertIsSelected()
        composeRule.onNodeWithText("To read").assertIsNotSelected()
        composeRule.onNodeWithText("Reading").performClick()
        composeRule.onNodeWithText("Read").performClick()
        assertEquals(listOf(ReadingStatus.Read), changes)
    }

    /** Search passes no status, so its preview looks the same as before. */
    @Test
    fun noStatusSelectorWithoutAStatus() {
        show(SamplePapers.bert, inLibrary = false)

        composeRule.onNodeWithText("To read").assertDoesNotExist()
        composeRule.onNodeWithText("Reading").assertDoesNotExist()
    }
}
```

`core/designsystem/src/test/java/com/etatech/hashiya/core/designsystem/component/MessageStatesTest.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.designsystem.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageStatesTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateActionInvokesCallback() {
        var clicks = 0
        composeRule.setContent {
            HashiyaTheme {
                EmptyState(HashiyaIcons.Library, "Nothing here", "Add something", actionLabel = "Go", onAction = { clicks++ })
            }
        }

        composeRule.onNodeWithText("Nothing here").assertIsDisplayed()
        composeRule.onNodeWithText("Go").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun emptyStateWithoutActionShowsNoButton() {
        composeRule.setContent {
            HashiyaTheme { EmptyState(HashiyaIcons.Library, "Nothing here", "Add something") }
        }

        composeRule.onNodeWithText("Go").assertDoesNotExist()
    }

    @Test
    fun emptyStateWithoutMessageLeavesNoBlankLine() {
        composeRule.setContent {
            HashiyaTheme { EmptyState(HashiyaIcons.SearchOff, "No papers match", message = null, actionLabel = "Clear") }
        }

        composeRule.onNodeWithText("No papers match").assertIsDisplayed()
        composeRule.onAllNodesWithText("").assertCountEquals(0)
    }

    @Test
    fun errorStateActionInvokesCallback() {
        var clicks = 0
        composeRule.setContent {
            HashiyaTheme { ErrorState("Offline", "Check your connection", "Retry", onAction = { clicks++ }) }
        }

        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, clicks)
    }
}
```

`core/designsystem/src/test/java/com/etatech/hashiya/core/designsystem/component/PaperScreenshotTest.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.ScreenshotVariant
import com.etatech.hashiya.core.testing.ScreenshotVariantRule
import com.etatech.hashiya.core.testing.captureScreenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class PaperScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun cards() = composeRule.captureScreenshot("paper_cards", variant, arabicText = "في المكتبة") {
        Column(Modifier.width(360.dp).padding(vertical = 6.dp)) {
            PaperCard(SamplePapers.attention, inLibrary = true, onClick = {}, onSave = {})
            PaperCard(SamplePapers.bert, inLibrary = false, onClick = {}, onSave = {})
            PaperCard(SamplePapers.arabicTitled, inLibrary = false, onClick = {}, onSave = {})
        }
    }

    @Test
    fun preview() = composeRule.captureScreenshot("paper_preview", variant, arabicText = "الملخص") {
        PaperPreviewContent(SamplePapers.bert, inLibrary = false, onToggleSave = {}, onOpenDoi = {}, modifier = Modifier.width(360.dp))
    }

    @Test
    fun previewWithStatus() = composeRule.captureScreenshot("paper_preview_status", variant, arabicText = "قيد القراءة") {
        PaperPreviewContent(
            SamplePapers.bert,
            inLibrary = true,
            onToggleSave = {},
            onOpenDoi = {},
            modifier = Modifier.width(360.dp),
            status = ReadingStatus.Reading
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: FAIL — test compilation errors: `No parameter with name 'status' found`, `No parameter with name 'onStatusChange' found`, `Null cannot be a value of a non-null type 'String'`.

- [ ] **Step 3: Add the status labels**

In `core/designsystem/src/main/res/values/strings.xml`, add before `</resources>`:
```xml
    <string name="status_to_read">To read</string>
    <string name="status_reading">Reading</string>
    <string name="status_read">Read</string>
```
In `core/designsystem/src/main/res/values-ar/strings.xml`, add before `</resources>`:
```xml
    <string name="status_to_read">للقراءة</string>
    <string name="status_reading">قيد القراءة</string>
    <string name="status_read">مقروءة</string>
```

- [ ] **Step 4: Implement**

`core/designsystem/src/main/java/com/etatech/hashiya/core/designsystem/component/ReadingStatusSelector.kt`:
```kotlin
package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.etatech.hashiya.core.designsystem.R
import com.etatech.hashiya.core.model.ReadingStatus

/** The one place reading statuses get their names: badges, menus, chips and the preview selector all use it. */
@Composable
fun readingStatusLabel(status: ReadingStatus): String = stringResource(
    when (status) {
        ReadingStatus.ToRead -> R.string.status_to_read
        ReadingStatus.Reading -> R.string.status_reading
        ReadingStatus.Read -> R.string.status_read
    }
)

/** Single-choice To read · Reading · Read. The selected segment is filled and checked, so it never relies on colour alone. */
@Composable
fun ReadingStatusSelector(status: ReadingStatus, onStatusChange: (ReadingStatus) -> Unit, modifier: Modifier = Modifier) {
    val options = ReadingStatus.entries
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == status,
                onClick = { if (option != status) onStatusChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(readingStatusLabel(option), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}
```

`core/designsystem/src/main/java/com/etatech/hashiya/core/designsystem/component/PaperPreview.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.R
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperPreviewSheet(
    paper: Paper,
    inLibrary: Boolean,
    onDismiss: () -> Unit,
    onToggleSave: () -> Unit,
    onOpenDoi: (String) -> Unit,
    status: ReadingStatus? = null,
    onStatusChange: (ReadingStatus) -> Unit = {}
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PaperPreviewContent(paper, inLibrary, onToggleSave, onOpenDoi, status = status, onStatusChange = onStatusChange)
    }
}

/**
 * The sheet's body, separate so it can be tested and screenshotted without a window.
 * With a [status] (the Library), a To read · Reading · Read selector sits above the buttons; Search passes none.
 */
@Composable
fun PaperPreviewContent(
    paper: Paper,
    inLibrary: Boolean,
    onToggleSave: () -> Unit,
    onOpenDoi: (String) -> Unit,
    modifier: Modifier = Modifier,
    status: ReadingStatus? = null,
    onStatusChange: (ReadingStatus) -> Unit = {}
) {
    // Paper text is full width so it aligns by its own direction (Latin left, Arabic right) in either locale.
    val contentText = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)
    Column(modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Text(
                paperTitle(paper),
                style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            if (paper.authors.isNotEmpty()) {
                Text(paper.authors.joinToString(", ") { it.name }, style = contentText, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
            }
            Text(
                listOfNotNull(
                    paper.venue,
                    paper.year?.toString(),
                    stringResource(R.string.designsystem_citations, fullCount(paper.citationCount))
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            if (paper.isOpenAccess) {
                Spacer(Modifier.height(8.dp))
                StatusBadge(
                    text = stringResource(
                        if (paper.openAccessPdfUrl != null) R.string.designsystem_open_access_pdf else R.string.designsystem_open_access
                    ),
                    kind = BadgeKind.OpenAccess
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.designsystem_abstract),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                paper.abstract ?: stringResource(R.string.designsystem_no_abstract),
                style = contentText,
                color = if (paper.abstract == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (status != null) {
            Spacer(Modifier.height(16.dp))
            ReadingStatusSelector(status, onStatusChange)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            paper.doi?.let { doi ->
                OutlinedButton(onClick = { onOpenDoi(doi) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.designsystem_open_doi))
                    Spacer(Modifier.width(6.dp))
                    Icon(HashiyaIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            Button(onClick = onToggleSave, modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (inLibrary) R.string.designsystem_remove_from_library else R.string.designsystem_save_to_library
                    )
                )
            }
        }
    }
}
```

`core/designsystem/src/main/java/com/etatech/hashiya/core/designsystem/component/MessageStates.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    MessageLayout(icon, title, message, actionLabel, onAction, modifier)
}

@Composable
fun ErrorState(title: String, message: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    MessageLayout(HashiyaIcons.Error, title, message, actionLabel, onAction, modifier)
}

@Composable
private fun MessageLayout(
    icon: ImageVector,
    title: String,
    message: String?,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
```

- [ ] **Step 5: Run the tests and inspect the screenshots locally**

Run: `./gradlew :core:designsystem:testDebugUnitTest :feature:library:testDebugUnitTest :feature:search:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; `PaperPreviewTest` (8), `MessageStatesTest` (4) and `PaperScreenshotTest` (12, the Arabic assertions included) pass, and every `feature/*` test still compiles and passes (named arguments keep the old `PaperPreviewContent` and `EmptyState` calls valid).

Run: `./gradlew :core:designsystem:recordRoborazziDebug` and open the four `paper_preview_status-*` images. Check: the To read · Reading · Read selector sits between the abstract and the buttons; Reading is filled and checked; in Arabic the segments run right to left (للقراءة on the right) and the English title stays left-to-right; no label is cut off. These local images are for inspection only; discard them with `git checkout -- core/designsystem/src/test/screenshots && git clean -fq core/designsystem/src/test/screenshots`.

- [ ] **Step 6: Format**

Run: `./gradlew spotlessApply`
Expected: `BUILD SUCCESSFUL`, no changes to the code above.

- [ ] **Step 7: Commit the code without the local screenshots**

```bash
git add -A -- . ':(exclude).idea/**' ':(exclude,glob)**/src/test/screenshots/**'
git commit -m "feat: add reading status labels and a status selector to the paper preview"
```

- [ ] **Step 8: Record the baselines on Linux and commit them**

Run (10–15 minutes; run it in the background): `bash scripts/record-screenshots-on-linux.sh`
Expected: ends with `Baselines copied from run <id>`; `git status` shows the 4 new `paper_preview_status-*` files and no other changed baselines. Open one English and one Arabic image to confirm they match what you inspected.

```bash
git add -- ':(glob)**/src/test/screenshots/**'
git commit -m "test: record the preview status selector screenshot baselines on Linux"
```

---

### Task 5: `feature/library` — ViewModel: search, status chips and status changes

**Files:**
- Create: `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryUiState.kt`
- Modify: `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryViewModel.kt` (full replacement below)
- Modify: `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryScreen.kt` (two edits so it compiles; Task 6 replaces the file)
- Test: `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryViewModelTest.kt` (full replacement below)
- Test: `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryContentTest.kt`, `LibraryScreenshotTest.kt`, `LibrarySwipeUndoTest.kt` (call-site updates only)

**Interfaces:**
- Consumes: `LibraryRepository.observeLibrary/observeStatusCounts/setStatus/remove/restore`, `FakeLibraryRepository.failOnSetStatus` (Task 3); `LibraryPaper`, `ReadingStatus` (Task 1).
- Produces:
  - `data class LibraryFilter(query: String = "", status: ReadingStatus? = null, counts: Map<ReadingStatus, Int> = all 0) { val total: Int }`.
  - `sealed interface LibraryUiState { Loading; Empty; data class NoMatches(filter: LibraryFilter); data class Papers(papers: List<LibraryPaper>, filter: LibraryFilter) }`.
  - `enum class LibraryMessage { StatusUpdateFailed }`.
  - `internal const val SEARCH_DEBOUNCE_MS = 300L`.
  - `LibraryViewModel(savedStateHandle: SavedStateHandle, libraryRepository: LibraryRepository)` with `uiState: StateFlow<LibraryUiState>`, `selectedPaper: StateFlow<LibraryPaper?>`, `pendingUndo: StateFlow<RemovedPaper?>`, `message: StateFlow<LibraryMessage?>` and `onQueryChange(String)`, `onSearch()`, `onClearQuery()`, `onStatusFilterChange(ReadingStatus?)`, `onClearSearchAndFilters()`, `onStatusChange(Paper, ReadingStatus)`, `onMessageShown()`, `onPaperClick(Paper)`, `onDismissPreview()`, `onRemove(Paper)`, `onUndoRemove()`, `onUndoDismissed()`. Saved-state keys: `library_query`, `library_status` (the enum name).

Decisions the spec leaves open, made here: Empty versus NoMatches is decided by `observeStatusCounts("")` (the whole library), so a search or chip that matches nothing never hides the search field; the preview's paper is looked up in the whole library (`observeLibrary("", null)`), so a status change that moves it out of the selected chip keeps its sheet open.

- [ ] **Step 1: Write the failing tests**

`feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryViewModelTest.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.feature.library

import androidx.lifecycle.SavedStateHandle
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import com.etatech.hashiya.core.testing.SamplePapers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLibraryRepository()
    private val savedStateHandle = SavedStateHandle()

    private fun TestScope.viewModel(handle: SavedStateHandle = savedStateHandle): LibraryViewModel {
        val viewModel = LibraryViewModel(handle, repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.selectedPaper.collect() }
        return viewModel
    }

    /** Saves attention, bert and vit in that order, so the library lists vit, bert, attention. */
    private suspend fun saveSamples() = SamplePapers.all.forEach { repository.save(it) }

    private fun LibraryViewModel.titles(): List<String> = when (val state = uiState.value) {
        is LibraryUiState.Papers -> state.papers.map { it.paper.title }
        else -> error("Expected papers but was $state")
    }

    private fun LibraryViewModel.filter(): LibraryFilter = when (val state = uiState.value) {
        is LibraryUiState.Papers -> state.filter
        is LibraryUiState.NoMatches -> state.filter
        else -> error("Expected papers or no matches but was $state")
    }

    private fun counts(toRead: Int, reading: Int, read: Int) =
        mapOf(ReadingStatus.ToRead to toRead, ReadingStatus.Reading to reading, ReadingStatus.Read to read)

    private val all = listOf(SamplePapers.vit.title, SamplePapers.bert.title, SamplePapers.attention.title)

    @Test
    fun emptyLibrary() = runTest {
        assertEquals(LibraryUiState.Empty, viewModel().uiState.value)
    }

    @Test
    fun listsPapersNewestFirstWithStatusesAndCounts() = runTest {
        saveSamples()
        repository.setStatus(SamplePapers.bert.openAlexId, ReadingStatus.Reading)

        assertEquals(
            LibraryUiState.Papers(
                listOf(
                    LibraryPaper(SamplePapers.vit, ReadingStatus.ToRead),
                    LibraryPaper(SamplePapers.bert, ReadingStatus.Reading),
                    LibraryPaper(SamplePapers.attention, ReadingStatus.ToRead)
                ),
                LibraryFilter(query = "", status = null, counts = counts(toRead = 2, reading = 1, read = 0))
            ),
            viewModel().uiState.value
        )
    }

    @Test
    fun typingSearchesAfterAPause() = runTest {
        saveSamples()
        val viewModel = viewModel()

        viewModel.onQueryChange("vaswani")
        assertEquals("vaswani", viewModel.filter().query)
        advanceTimeBy(SEARCH_DEBOUNCE_MS - 1)
        assertEquals(all, viewModel.titles())

        advanceTimeBy(2)
        assertEquals(listOf(SamplePapers.attention.title), viewModel.titles())
    }

    @Test
    fun searchKeyAppliesAtOnce() = runTest {
        saveSamples()
        val viewModel = viewModel()

        viewModel.onQueryChange("devlin")
        viewModel.onSearch()

        assertEquals(listOf(SamplePapers.bert.title), viewModel.titles())
    }

    @Test
    fun clearAppliesAtOnce() = runTest {
        saveSamples()
        val viewModel = viewModel()
        viewModel.onQueryChange("devlin")
        viewModel.onSearch()

        viewModel.onClearQuery()

        assertEquals(all, viewModel.titles())
        assertEquals("", viewModel.filter().query)
    }

    @Test
    fun chipAndSearchCombine() = runTest {
        saveSamples()
        repository.setStatus(SamplePapers.bert.openAlexId, ReadingStatus.Reading)
        repository.setStatus(SamplePapers.attention.openAlexId, ReadingStatus.Reading)
        val viewModel = viewModel()

        viewModel.onStatusFilterChange(ReadingStatus.Reading)
        assertEquals(listOf(SamplePapers.bert.title, SamplePapers.attention.title), viewModel.titles())

        viewModel.onQueryChange("devlin")
        viewModel.onSearch()
        assertEquals(listOf(SamplePapers.bert.title), viewModel.titles())
        assertEquals(LibraryFilter("devlin", ReadingStatus.Reading, counts(toRead = 0, reading = 1, read = 0)), viewModel.filter())
    }

    @Test
    fun noMatchesWhenTheLibraryHasPapersButNoneMatch() = runTest {
        saveSamples()
        val viewModel = viewModel()

        viewModel.onQueryChange("zebra")
        viewModel.onSearch()
        assertEquals(LibraryUiState.NoMatches(LibraryFilter("zebra", null, counts(0, 0, 0))), viewModel.uiState.value)

        viewModel.onClearQuery()
        viewModel.onStatusFilterChange(ReadingStatus.Read)
        assertEquals(LibraryUiState.NoMatches(LibraryFilter("", ReadingStatus.Read, counts(3, 0, 0))), viewModel.uiState.value)
    }

    @Test
    fun clearSearchAndFiltersResetsBoth() = runTest {
        saveSamples()
        val viewModel = viewModel()
        viewModel.onQueryChange("zebra")
        viewModel.onSearch()
        viewModel.onStatusFilterChange(ReadingStatus.Read)

        viewModel.onClearSearchAndFilters()

        assertEquals(all, viewModel.titles())
        assertEquals(LibraryFilter("", null, counts(3, 0, 0)), viewModel.filter())
    }

    /** After process death the typed search and the chip come back, and the search applies without waiting. */
    @Test
    fun restoresSearchAndChipFromSavedState() = runTest {
        saveSamples()
        val first = viewModel()
        first.onQueryChange("devlin")
        first.onStatusFilterChange(ReadingStatus.ToRead)

        val restored = viewModel(SavedStateHandle(savedStateHandle.keys().associateWith { savedStateHandle.get<Any>(it) }))

        assertEquals(listOf(SamplePapers.bert.title), restored.titles())
        assertEquals(LibraryFilter("devlin", ReadingStatus.ToRead, counts(1, 0, 0)), restored.filter())
    }

    @Test
    fun statusChangeUpdatesTheListAndCounts() = runTest {
        saveSamples()
        val viewModel = viewModel()

        viewModel.onStatusChange(SamplePapers.bert, ReadingStatus.Read)

        assertEquals(ReadingStatus.Read, (viewModel.uiState.value as LibraryUiState.Papers).papers[1].status)
        assertEquals(counts(toRead = 2, reading = 0, read = 1), viewModel.filter().counts)
    }

    /** With the To read chip selected, marking the open paper as Reading moves it out of the list but keeps its sheet open. */
    @Test
    fun statusChangeOutOfTheChipKeepsThePreviewOpen() = runTest {
        saveSamples()
        val viewModel = viewModel()
        viewModel.onStatusFilterChange(ReadingStatus.ToRead)
        viewModel.onPaperClick(SamplePapers.bert)

        viewModel.onStatusChange(SamplePapers.bert, ReadingStatus.Reading)

        assertEquals(listOf(SamplePapers.vit.title, SamplePapers.attention.title), viewModel.titles())
        assertEquals(LibraryPaper(SamplePapers.bert, ReadingStatus.Reading), viewModel.selectedPaper.value)
    }

    @Test
    fun statusChangeFailureShowsTheMessageAndKeepsTheStoredStatus() = runTest {
        saveSamples()
        repository.failOnSetStatus = true
        val viewModel = viewModel()

        viewModel.onStatusChange(SamplePapers.bert, ReadingStatus.Read)

        assertEquals(LibraryMessage.StatusUpdateFailed, viewModel.message.value)
        assertEquals(counts(toRead = 3, reading = 0, read = 0), viewModel.filter().counts)
        viewModel.onMessageShown()
        assertNull(viewModel.message.value)
    }

    @Test
    fun selectingAndDismissingPreview() = runTest {
        repository.save(SamplePapers.bert)
        val viewModel = viewModel()

        viewModel.onPaperClick(SamplePapers.bert)
        assertEquals(LibraryPaper(SamplePapers.bert, ReadingStatus.ToRead), viewModel.selectedPaper.value)
        viewModel.onDismissPreview()
        assertNull(viewModel.selectedPaper.value)
    }

    @Test
    fun removingOffersUndoAndClosesPreview() = runTest {
        repository.save(SamplePapers.bert)
        val viewModel = viewModel()
        viewModel.onPaperClick(SamplePapers.bert)

        viewModel.onRemove(SamplePapers.bert)

        assertEquals(LibraryUiState.Empty, viewModel.uiState.value)
        assertEquals(SamplePapers.bert, viewModel.pendingUndo.value?.paper)
        assertNull(viewModel.selectedPaper.value)
    }

    /** Removing the only paper a search matched empties the library: Empty, not "No papers match". */
    @Test
    fun removingTheLastPaperDuringASearchShowsEmpty() = runTest {
        repository.save(SamplePapers.bert)
        val viewModel = viewModel()
        viewModel.onQueryChange("devlin")
        viewModel.onSearch()

        viewModel.onRemove(SamplePapers.bert)

        assertEquals(LibraryUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun undoRestoresPaperInItsPlaceWithItsStatus() = runTest {
        saveSamples()
        repository.setStatus(SamplePapers.bert.openAlexId, ReadingStatus.Reading)
        val viewModel = viewModel()

        viewModel.onRemove(SamplePapers.bert)
        viewModel.onUndoRemove()

        assertEquals(
            listOf(
                LibraryPaper(SamplePapers.vit, ReadingStatus.ToRead),
                LibraryPaper(SamplePapers.bert, ReadingStatus.Reading),
                LibraryPaper(SamplePapers.attention, ReadingStatus.ToRead)
            ),
            repository.observeLibrary("", null).first()
        )
        assertNull(viewModel.pendingUndo.value)
    }

    @Test
    fun twoQuickRemovalsKeepOnlyTheLatestForUndo() = runTest {
        repository.save(SamplePapers.attention)
        repository.save(SamplePapers.bert)
        val viewModel = viewModel()

        viewModel.onRemove(SamplePapers.attention)
        viewModel.onRemove(SamplePapers.bert)
        assertEquals(SamplePapers.bert, viewModel.pendingUndo.value?.paper)

        viewModel.onUndoRemove()
        assertEquals(listOf(SamplePapers.bert), repository.observeLibrary("", null).first().map { it.paper })
    }

    @Test
    fun dismissingUndoForgetsRemovedPaper() = runTest {
        repository.save(SamplePapers.bert)
        val viewModel = viewModel()
        viewModel.onRemove(SamplePapers.bert)

        viewModel.onUndoDismissed()

        assertNull(viewModel.pendingUndo.value)
        assertEquals(LibraryUiState.Empty, viewModel.uiState.value)
    }
}
```

In `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryContentTest.kt`:
1. Add the import `import com.etatech.hashiya.core.model.LibraryPaper`.
2. Replace every `LibraryUiState.Papers(` in the file with `papersState(` (four places: `listShowsCountTitlesAndShortAuthorLine`, `tappingRowOpensPreview`, `addPaperButtonWithPapers`, `lastPaperStaysClearOfTheAddPaperButton`). Do this before step 3, whose helper must keep its own `LibraryUiState.Papers(`.
3. Add, right above `private fun show(`:
```kotlin
    private fun papersState(papers: List<Paper>) =
        LibraryUiState.Papers(papers.map { LibraryPaper(it, ReadingStatus.ToRead) }, LibraryFilter())

```

In `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryScreenshotTest.kt`, add the imports `import com.etatech.hashiya.core.model.LibraryPaper` and `import com.etatech.hashiya.core.model.ReadingStatus`, and in `papers()` replace
```kotlin
        LibraryUiState.Papers(listOf(SamplePapers.attention, SamplePapers.bert, SamplePapers.arabicTitled)),
```
with
```kotlin
        LibraryUiState.Papers(
            listOf(SamplePapers.attention, SamplePapers.bert, SamplePapers.arabicTitled).map { LibraryPaper(it, ReadingStatus.ToRead) },
            LibraryFilter()
        ),
```

In `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibrarySwipeUndoTest.kt`, add the import `import androidx.lifecycle.SavedStateHandle` and replace `val viewModel = LibraryViewModel(repository)` with `val viewModel = LibraryViewModel(SavedStateHandle(), repository)`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :feature:library:testDebugUnitTest`
Expected: FAIL — test compilation errors: `Unresolved reference 'LibraryFilter'`, `'SEARCH_DEBOUNCE_MS'`, `'LibraryMessage'`, `'onQueryChange'`, `'onStatusChange'`, and `Too many arguments for 'constructor(libraryRepository: LibraryRepository): LibraryViewModel'`.

- [ ] **Step 3: Implement**

`feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryUiState.kt`:
```kotlin
package com.etatech.hashiya.feature.library

import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.ReadingStatus

/** What the search field and the status chips show: the typed [query], the selected [status] (null = All) and the counts. */
data class LibraryFilter(
    val query: String = "",
    val status: ReadingStatus? = null,
    /** Papers of each status matching the applied search; every status is present. */
    val counts: Map<ReadingStatus, Int> = ReadingStatus.entries.associateWith { 0 }
) {
    /** The "All" chip's count: the total of the three. */
    val total: Int get() = counts.values.sum()
}

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    /** Nothing saved yet; the search field and chips are hidden. */
    data object Empty : LibraryUiState

    /** The library has papers, but none match the search and the chip. */
    data class NoMatches(val filter: LibraryFilter) : LibraryUiState

    data class Papers(val papers: List<LibraryPaper>, val filter: LibraryFilter) : LibraryUiState
}

enum class LibraryMessage { StatusUpdateFailed }
```

`feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryViewModel.kt` (replace the whole file; `LibraryUiState` moved to its own file):
```kotlin
package com.etatech.hashiya.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal const val SEARCH_DEBOUNCE_MS = 300L
private const val KEY_QUERY = "library_query"
private const val KEY_STATUS = "library_status"

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository
) : ViewModel() {
    /** The search text as typed. */
    private val query = MutableStateFlow(savedStateHandle.get<String>(KEY_QUERY).orEmpty())

    /** The selected status chip; null is All. */
    private val status = MutableStateFlow(
        savedStateHandle.get<String>(KEY_STATUS)?.let { name -> ReadingStatus.entries.firstOrNull { it.name == name } }
    )

    /** The text actually searched: follows [query] after a pause, or at once on Search or Clear. Restored text applies at once. */
    private val appliedQuery = MutableStateFlow(query.value)

    init {
        viewModelScope.launch {
            query.drop(1).debounce(SEARCH_DEBOUNCE_MS).collect { appliedQuery.value = it }
        }
        viewModelScope.launch {
            query.collect { savedStateHandle[KEY_QUERY] = it }
        }
        viewModelScope.launch {
            status.collect { savedStateHandle[KEY_STATUS] = it?.name }
        }
    }

    /** Decides Empty (nothing saved) versus NoMatches (nothing matches), whatever the search and chip. */
    private val libraryIsEmpty = libraryRepository.observeStatusCounts("").map { counts -> counts.values.sum() == 0 }.distinctUntilChanged()

    private val results = combine(appliedQuery, status, ::Pair).flatMapLatest { (applied, selected) ->
        combine(libraryRepository.observeLibrary(applied, selected), libraryRepository.observeStatusCounts(applied), ::Pair)
    }

    val uiState: StateFlow<LibraryUiState> = combine(libraryIsEmpty, results, query, status) { empty, (papers, counts), typed, selected ->
        val filter = LibraryFilter(query = typed, status = selected, counts = counts)
        when {
            empty -> LibraryUiState.Empty
            papers.isEmpty() -> LibraryUiState.NoMatches(filter)
            else -> LibraryUiState.Papers(papers, filter)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    private val selectedId = MutableStateFlow<String?>(null)

    /**
     * The paper in the preview sheet, with its current status. Looked up in the whole library, so a status change that
     * moves it out of the selected chip keeps the sheet open; clears itself if the paper is removed.
     */
    val selectedPaper: StateFlow<LibraryPaper?> = selectedId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            libraryRepository.observeLibrary(query = "", status = null).map { papers -> papers.firstOrNull { it.paper.openAlexId == id } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _pendingUndo = MutableStateFlow<RemovedPaper?>(null)
    val pendingUndo: StateFlow<RemovedPaper?> = _pendingUndo.asStateFlow()

    private val _message = MutableStateFlow<LibraryMessage?>(null)
    val message: StateFlow<LibraryMessage?> = _message.asStateFlow()

    fun onQueryChange(text: String) {
        query.value = text
    }

    /** The keyboard's Search key: search now instead of after the pause. */
    fun onSearch() {
        appliedQuery.value = query.value
    }

    fun onClearQuery() {
        query.value = ""
        appliedQuery.value = ""
    }

    fun onStatusFilterChange(status: ReadingStatus?) {
        this.status.value = status
    }

    fun onClearSearchAndFilters() {
        onClearQuery()
        status.value = null
    }

    fun onStatusChange(paper: Paper, status: ReadingStatus) {
        viewModelScope.launch {
            try {
                libraryRepository.setStatus(paper.openAlexId, status)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = LibraryMessage.StatusUpdateFailed
            }
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    fun onPaperClick(paper: Paper) {
        selectedId.value = paper.openAlexId
    }

    fun onDismissPreview() {
        selectedId.value = null
    }

    fun onRemove(paper: Paper) {
        selectedId.value = null
        viewModelScope.launch {
            _pendingUndo.value = libraryRepository.remove(paper.openAlexId)
        }
    }

    fun onUndoRemove() {
        val removed = _pendingUndo.value ?: return
        _pendingUndo.value = null
        viewModelScope.launch { libraryRepository.restore(removed) }
    }

    fun onUndoDismissed() {
        _pendingUndo.value = null
    }
}
```

In `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryScreen.kt` (the screen keeps its current look until Task 6):
1. In `LibraryScreen`, replace `selectedPaper = selectedPaper,` with `selectedPaper = selectedPaper?.paper,`.
2. In `LibraryContent`'s `when (uiState)`, replace
```kotlin
                is LibraryUiState.Papers -> PaperList(uiState.papers, onPaperClick, onRemove)
```
with
```kotlin
                is LibraryUiState.NoMatches -> Unit

                is LibraryUiState.Papers -> PaperList(uiState.papers.map { it.paper }, onPaperClick, onRemove)
```
(`NoMatches` can't happen yet: nothing can set a search or a chip before Task 6.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :feature:library:testDebugUnitTest :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; the 18 `LibraryViewModelTest` tests pass, and the existing library content, screenshot and swipe tests and the app navigation tests still pass.

- [ ] **Step 5: Format**

Run: `./gradlew spotlessApply`
Expected: `BUILD SUCCESSFUL`, no changes to the code above.

- [ ] **Step 6: Commit**

```bash
git add -A -- . ':(exclude).idea/**'
git commit -m "feat: search the library and filter it by reading status in the ViewModel"
```

---

### Task 6: `feature/library` — search field, status chips, badges, No matches and the preview selector

**Files:**
- Modify: `feature/library/src/main/res/values/strings.xml`, `feature/library/src/main/res/values-ar/strings.xml`
- Create: `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryActions.kt`
- Create: `feature/library/src/main/java/com/etatech/hashiya/feature/library/components/LibrarySearchField.kt`
- Create: `feature/library/src/main/java/com/etatech/hashiya/feature/library/components/StatusFilterChips.kt`
- Create: `feature/library/src/main/java/com/etatech/hashiya/feature/library/components/ReadingStatusBadge.kt`
- Modify: `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryScreen.kt` (full replacement below)
- Modify: `core/testing/src/main/java/com/etatech/hashiya/core/testing/Screenshots.kt` (full replacement below)
- Test: `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryContentTest.kt` (full replacement below)
- Test: `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryScreenshotTest.kt` (full replacement below)
- Test: `feature/search/src/test/java/com/etatech/hashiya/feature/search/SearchLookupContentTest.kt` (one guard test)
- Test output: 12 new baselines (`library_search-*`, `library_no_matches-*`, `library_status_menu-*`); the 4 `library_papers-*` baselines change (search field, chips, badges). `library_empty-*` stays the same.

**Interfaces:**
- Consumes: everything from Task 5; `readingStatusLabel`, `PaperPreviewSheet(..., status, onStatusChange)`, `EmptyState(message = null)` (Task 4); `HashiyaIcons.Search`, `Close`, `Check`, `SearchOff`.
- Produces:
  - `internal data class LibraryActions(onQueryChange, onSearch, onClearQuery, onStatusFilterChange, onClearSearchAndFilters, onPaperClick, onStatusChange: (Paper, ReadingStatus) -> Unit, onDismissPreview, onRemove, onUndo, onUndoDismissed, onMessageShown, onGoToSearch, onAddPaper, onOpenSettings, onOpenDoi)` — all default to no-ops.
  - `internal fun LibraryContent(uiState: LibraryUiState, selectedPaper: LibraryPaper?, pendingUndo: RemovedPaper?, actions: LibraryActions, modifier: Modifier = Modifier, message: LibraryMessage? = null)`; `LibraryScreen(onGoToSearch, onAddPaper, onOpenSettings, viewModel)` keeps its signature, so `app` needs no change.
  - `LIBRARY_SEARCH_FIELD_TAG`, `READING_STATUS_BADGE_TAG` (`com.etatech.hashiya.feature.library.components`).
  - `captureScreenshot(name, variant, arabicText, wholeScreen: Boolean = false, beforeCapture: ComposeContentTestRule.() -> Unit = {}, content)` in `core/testing`; existing calls are unchanged.

- [ ] **Step 1: Write the failing tests**

`core/testing/src/main/java/com/etatech/hashiya/core/testing/Screenshots.kt` (replace the whole file; the menu screenshot needs a whole-screen capture, because a menu is its own window):
```kotlin
package com.etatech.hashiya.core.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.RuntimeEnvironment

/** Default device for screen-level screenshots. Use with `@Config(qualifiers = PHONE_QUALIFIERS)`. */
const val PHONE_QUALIFIERS = "w360dp-h780dp-xhdpi"

private const val SCREENSHOT_TAG = "screenshot_root"

/** [qualifiers] are Robolectric qualifiers added on top of the class's `@Config` ones. */
enum class ScreenshotVariant(val qualifiers: String, val darkTheme: Boolean) {
    EnglishLight("+en", darkTheme = false),
    EnglishDark("+en-night", darkTheme = true),
    ArabicLight("+ar", darkTheme = false),
    ArabicDark("+ar-night", darkTheme = true)
    ;

    val isArabic: Boolean get() = qualifiers.startsWith("+ar")

    companion object {
        /** For `@ParameterizedRobolectricTestRunner.Parameters`. */
        @JvmStatic
        fun parameters(): List<Array<Any>> = entries.map { arrayOf(it) }
    }
}

/**
 * Applies the variant's locale and night mode as Robolectric qualifiers before the activity starts,
 * so resources resolve exactly as on a device. Declare it first:
 * `@get:Rule(order = 0) val variantRule = ScreenshotVariantRule(variant)` and the compose rule with `order = 1`.
 */
class ScreenshotVariantRule(private val variant: ScreenshotVariant) : TestWatcher() {
    override fun starting(description: Description) {
        RuntimeEnvironment.setQualifiers(variant.qualifiers)
    }
}

/**
 * Renders [content] in the Hashiya theme for [variant] and records or verifies
 * `src/test/screenshots/<name>-<variant>.png`.
 *
 * In Arabic variants, [arabicText] (a string the content shows in Arabic) must be on screen; this fails the test
 * instead of recording English text as an "Arabic" baseline.
 *
 * [beforeCapture] runs once the content is shown, e.g. to open a menu. Menus and dialogs are separate windows, so
 * capturing one needs [wholeScreen], which records every window instead of the content alone.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun ComposeContentTestRule.captureScreenshot(
    name: String,
    variant: ScreenshotVariant,
    arabicText: String,
    wholeScreen: Boolean = false,
    beforeCapture: ComposeContentTestRule.() -> Unit = {},
    content: @Composable () -> Unit
) {
    setContent {
        // Forced so the direction does not depend on the test manifest's android:supportsRtl.
        val direction = if (variant.isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            HashiyaTheme(darkTheme = variant.darkTheme) {
                Box(Modifier.testTag(SCREENSHOT_TAG)) {
                    Surface { content() }
                }
            }
        }
    }
    beforeCapture()
    if (variant.isArabic) {
        onNodeWithText(arabicText, substring = true, useUnmergedTree = true)
            .assertExists("Arabic variant did not render \"$arabicText\"; check the locale qualifiers")
    }
    val filePath = "src/test/screenshots/$name-${variant.name}.png"
    val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))
    if (wholeScreen) {
        waitForIdle()
        captureScreenRoboImage(filePath, options)
    } else {
        onNodeWithTag(SCREENSHOT_TAG).captureRoboImage(filePath = filePath, roborazziOptions = options)
    }
}
```

`feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryContentTest.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.feature.library.components.LIBRARY_SEARCH_FIELD_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class LibraryContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()
    private val removedBert = RemovedPaper(SamplePapers.bert, localId = "local-1", savedAt = 1, status = ReadingStatus.ToRead)
    private val removedVit = RemovedPaper(SamplePapers.vit, localId = "local-2", savedAt = 2, status = ReadingStatus.ToRead)

    private val actions = LibraryActions(
        onQueryChange = { events += "query:$it" },
        onSearch = { events += "search" },
        onClearQuery = { events += "clearQuery" },
        onStatusFilterChange = { events += "filter:$it" },
        onClearSearchAndFilters = { events += "clearAll" },
        onPaperClick = { events += "open:${it.openAlexId}" },
        onStatusChange = { paper, status -> events += "status:${paper.openAlexId}:$status" },
        onRemove = { paper: Paper -> events += "remove:${paper.openAlexId}" },
        onUndo = { events += "undo" },
        onUndoDismissed = { events += "undoDismissed" },
        onMessageShown = { events += "messageShown" },
        onGoToSearch = { events += "search-tab" },
        onAddPaper = { events += "addPaper" },
        onOpenSettings = { events += "settings" }
    )

    private fun counts(toRead: Int, reading: Int, read: Int) =
        mapOf(ReadingStatus.ToRead to toRead, ReadingStatus.Reading to reading, ReadingStatus.Read to read)

    private fun papersState(vararg papers: Pair<Paper, ReadingStatus>, filter: LibraryFilter = LibraryFilter()) =
        LibraryUiState.Papers(papers.map { (paper, status) -> LibraryPaper(paper, status) }, filter)

    private fun toRead(vararg papers: Paper) = papersState(*papers.map { it to ReadingStatus.ToRead }.toTypedArray())

    private fun show(
        state: () -> LibraryUiState,
        pendingUndo: () -> RemovedPaper? = { null },
        selectedPaper: LibraryPaper? = null,
        message: LibraryMessage? = null,
        actions: LibraryActions = this.actions
    ) = composeRule.setContent {
        HashiyaTheme {
            LibraryContent(
                uiState = state(),
                selectedPaper = selectedPaper,
                pendingUndo = pendingUndo(),
                actions = actions,
                message = message
            )
        }
    }

    private fun show(state: LibraryUiState, pendingUndo: () -> RemovedPaper? = { null }) = show({ state }, pendingUndo)

    @Test
    fun emptyStateLeadsToSearchWithoutSearchFieldOrChips() {
        show(LibraryUiState.Empty)

        composeRule.onNodeWithText("No saved papers yet").assertIsDisplayed()
        composeRule.onNodeWithText("Search your library").assertDoesNotExist()
        composeRule.onNodeWithText("All", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Go to Search").performClick()
        assertEquals(listOf("search-tab"), events)
    }

    @Test
    fun listShowsCountTitlesAndShortAuthorLine() {
        show(toRead(SamplePapers.attention, SamplePapers.vit))

        composeRule.onNodeWithText("2 papers").assertIsDisplayed()
        composeRule.onNodeWithText("Attention Is All You Need").assertIsDisplayed()
        composeRule.onNodeWithText("Ashish Vaswani et al. · 2017 · Neural Information Processing Systems").assertIsDisplayed()
    }

    @Test
    fun tappingRowOpensPreview() {
        show(toRead(SamplePapers.bert))

        composeRule.onNodeWithText(SamplePapers.bert.title).performClick()
        assertEquals(listOf("open:${SamplePapers.bert.openAlexId}"), events)
    }

    @Test
    fun typingAndTheSearchKeyReachTheViewModel() {
        var query by mutableStateOf("")
        show(
            state = { papersState(SamplePapers.bert to ReadingStatus.ToRead, filter = LibraryFilter(query = query)) },
            actions = actions.copy(
                onQueryChange = {
                    query = it
                    events += "query:$it"
                }
            )
        )

        composeRule.onNodeWithText("Search your library").assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_SEARCH_FIELD_TAG).performTextInput("bert")
        composeRule.onNodeWithTag(LIBRARY_SEARCH_FIELD_TAG).performImeAction()

        assertEquals(listOf("query:bert", "search"), events)
    }

    @Test
    fun clearButtonClearsTheSearch() {
        show(papersState(SamplePapers.bert to ReadingStatus.ToRead, filter = LibraryFilter(query = "bert")))

        composeRule.onNodeWithContentDescription("Clear search").performClick()
        assertEquals(listOf("clearQuery"), events)
    }

    @Test
    fun chipsShowCountsAndSelectAStatus() {
        show(
            papersState(
                SamplePapers.bert to ReadingStatus.Reading,
                filter = LibraryFilter(status = ReadingStatus.Reading, counts = counts(toRead = 2, reading = 1, read = 0))
            )
        )

        composeRule.onNodeWithText("All · 3").assertIsNotSelected()
        composeRule.onNodeWithText("To read · 2").assertIsDisplayed()
        composeRule.onNodeWithText("Reading · 1").assertIsSelected()
        composeRule.onNodeWithText("Read · 0").performClick()
        composeRule.onNodeWithText("All · 3").performClick()
        assertEquals(listOf("filter:Read", "filter:null"), events)
    }

    @Test
    fun badgeShowsTheStatusAndItsMenuChangesIt() {
        show(papersState(SamplePapers.bert to ReadingStatus.ToRead))

        composeRule.onNodeWithContentDescription("Status: To read. Change status").performClick()
        composeRule.onNode(hasText("To read") and hasAnyAncestor(isPopup())).assertIsSelected()
        composeRule.onNode(hasText("Reading") and hasAnyAncestor(isPopup())).assertIsNotSelected()
        composeRule.onNode(hasText("Reading") and hasAnyAncestor(isPopup())).performClick()

        assertEquals(listOf("status:${SamplePapers.bert.openAlexId}:Reading"), events)
        composeRule.onNode(hasAnyAncestor(isPopup())).assertDoesNotExist()
    }

    @Test
    fun readBadgeSaysReadNotJustAColour() {
        show(papersState(SamplePapers.bert to ReadingStatus.Read))

        composeRule.onNodeWithContentDescription("Status: Read. Change status").assertIsDisplayed()
    }

    @Test
    fun noMatchesOffersToClearSearchAndFilters() {
        show(LibraryUiState.NoMatches(LibraryFilter(query = "zebra", counts = counts(0, 0, 0))))

        composeRule.onNodeWithText("No papers match").assertIsDisplayed()
        composeRule.onNodeWithText("All · 0").assertIsDisplayed()
        composeRule.onNodeWithText("Clear search and filters").performClick()
        assertEquals(listOf("clearAll"), events)
    }

    /** Typing a word that matches nothing swaps the list for "No papers match"; the field must keep focus and the keyboard. */
    @Test
    fun searchFieldKeepsFocusWhenNothingMatches() {
        var state by mutableStateOf<LibraryUiState>(toRead(SamplePapers.bert))
        show({ state })
        composeRule.onNodeWithTag(LIBRARY_SEARCH_FIELD_TAG).performClick()
        composeRule.onNodeWithTag(LIBRARY_SEARCH_FIELD_TAG).assertIsFocused()

        composeRule.runOnIdle { state = LibraryUiState.NoMatches(LibraryFilter(query = "zebra")) }

        composeRule.onNodeWithText("No papers match").assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_SEARCH_FIELD_TAG).assertIsFocused()
    }

    @Test
    fun previewHasTheStatusSelector() {
        show({ toRead(SamplePapers.bert) }, selectedPaper = LibraryPaper(SamplePapers.bert, ReadingStatus.Reading))

        // The sheet is a dialog window.
        composeRule.onNode(hasText("Reading") and hasAnyAncestor(isDialog())).assertIsSelected()
        composeRule.onNode(hasText("Read") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("status:${SamplePapers.bert.openAlexId}:Read"), events)
    }

    @Test
    fun statusUpdateFailureShowsASnackbar() {
        show({ toRead(SamplePapers.bert) }, message = LibraryMessage.StatusUpdateFailed)

        composeRule.onNodeWithText("Couldn't update the status").assertIsDisplayed()
    }

    @Test
    fun undoSnackbarActionInvokesUndo() {
        show(LibraryUiState.Empty, pendingUndo = { removedBert })

        composeRule.onNodeWithText("Removed from library").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("undo"), events)
    }

    /** A second removal must get its own full snackbar, not the remainder of the first one's timeout. */
    @Test
    fun secondRemovalRestartsTheUndoSnackbar() {
        composeRule.mainClock.autoAdvance = false
        var pending by mutableStateOf<RemovedPaper?>(removedBert)
        show(LibraryUiState.Empty, pendingUndo = { pending })

        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.runOnIdle { pending = removedVit }
        // With the main clock paused, a plain snapshot write made from outside composition (as
        // here) is not otherwise flushed to the recomposer until something drives the real
        // Robolectric looper (e.g. a node query) - which would happen too late relative to the
        // next advanceTimeBy below. Forcing the flush now reproduces what a live Choreographer
        // does within a frame of a real state change, so the assertions below observe the
        // second removal's own snackbar restart rather than a stale one racing the first's timeout.
        Snapshot.sendApplyNotifications()
        // The first snackbar (4 s, short duration) would have timed out by now.
        composeRule.mainClock.advanceTimeBy(2_000)

        composeRule.onNodeWithText("Removed from library").assertExists()
        assertFalse("undoDismissed" in events)
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf("undo"), events)
    }

    // The extended FAB's label is only in the unmerged semantics tree.
    @Test
    fun addPaperButtonOnEmptyLibrary() {
        show(LibraryUiState.Empty)

        composeRule.onNodeWithText("Add paper", useUnmergedTree = true).performClick()
        assertEquals(listOf("addPaper"), events)
    }

    @Test
    fun addPaperButtonWithPapers() {
        show(toRead(SamplePapers.bert))

        composeRule.onNodeWithText("Add paper", useUnmergedTree = true).performClick()
        assertEquals(listOf("addPaper"), events)
    }

    /** With enough papers to overflow the screen, the FAB must not cover the last, scrolled-to row. */
    @Test
    fun lastPaperStaysClearOfTheAddPaperButton() {
        val manyPapers = (1..20).map { SamplePapers.bert.copy(openAlexId = "paper-$it", title = "Paper $it") }
        show(toRead(*manyPapers.toTypedArray()))

        // The list, not the sideways-scrolling chips.
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Paper 20"))

        val lastRowBounds = composeRule.onNodeWithText("Paper 20").getUnclippedBoundsInRoot()
        val fabBounds = composeRule.onNodeWithText("Add paper", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(
            "last row bottom (${lastRowBounds.bottom}) must be above the FAB top (${fabBounds.top})",
            lastRowBounds.bottom <= fabBounds.top
        )
    }
}
```

`feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryScreenshotTest.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.feature.library

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.ScreenshotVariant
import com.etatech.hashiya.core.testing.ScreenshotVariantRule
import com.etatech.hashiya.core.testing.captureScreenshot
import com.etatech.hashiya.feature.library.components.READING_STATUS_BADGE_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class LibraryScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    /** One paper of each status, so every badge style shows. */
    private val library = LibraryUiState.Papers(
        listOf(
            LibraryPaper(SamplePapers.attention, ReadingStatus.Reading),
            LibraryPaper(SamplePapers.bert, ReadingStatus.Read),
            LibraryPaper(SamplePapers.arabicTitled, ReadingStatus.ToRead)
        ),
        LibraryFilter(counts = mapOf(ReadingStatus.ToRead to 1, ReadingStatus.Reading to 1, ReadingStatus.Read to 1))
    )

    private fun capture(
        name: String,
        state: LibraryUiState,
        arabicText: String,
        wholeScreen: Boolean = false,
        beforeCapture: ComposeContentTestRule.() -> Unit = {}
    ) = composeRule.captureScreenshot(name, variant, arabicText, wholeScreen, beforeCapture) {
        LibraryContent(uiState = state, selectedPaper = null, pendingUndo = null, actions = LibraryActions())
    }

    @Test
    fun empty() = capture("library_empty", LibraryUiState.Empty, arabicText = "لا توجد أوراق محفوظة بعد")

    // The top app bar title (library_title) is a values-ar string that appears exactly once on these screens;
    // status labels appear on both a chip and a badge, and paper titles are content, not app strings.
    @Test
    fun papers() = capture("library_papers", library, arabicText = "المكتبة")

    @Test
    fun filteredSearch() = capture(
        "library_search",
        LibraryUiState.Papers(
            listOf(LibraryPaper(SamplePapers.attention, ReadingStatus.Reading)),
            LibraryFilter(
                query = "transformer",
                status = ReadingStatus.Reading,
                counts = mapOf(ReadingStatus.ToRead to 2, ReadingStatus.Reading to 1, ReadingStatus.Read to 0)
            )
        ),
        arabicText = "الكل"
    )

    @Test
    fun noMatches() = capture(
        "library_no_matches",
        LibraryUiState.NoMatches(LibraryFilter(query = "zebra", status = ReadingStatus.Read)),
        arabicText = "لا توجد أوراق مطابقة"
    )

    @Test
    fun statusMenu() = capture("library_status_menu", library, arabicText = "المكتبة", wholeScreen = true) {
        onAllNodesWithTag(READING_STATUS_BADGE_TAG).onFirst().performClick()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
```

In `feature/search/src/test/java/com/etatech/hashiya/feature/search/SearchLookupContentTest.kt`, add after `foundPaperAlreadySavedOffersRemove()` (a guard: it passes before and after this task, and fails if Search ever starts passing a status):
```kotlin
    /** Reading status belongs to the Library; Search's preview has no status selector, even for a saved paper. */
    @Test
    fun foundPreviewHasNoStatusSelector() {
        show(lookupState = LookupUiState.Found(SamplePapers.attention), savedIds = setOf(SamplePapers.attention.openAlexId))

        composeRule.onNodeWithText("To read").assertDoesNotExist()
        composeRule.onNodeWithText("Reading").assertDoesNotExist()
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :feature:library:testDebugUnitTest`
Expected: FAIL — test compilation errors: `Unresolved reference 'LibraryActions'`, `'LIBRARY_SEARCH_FIELD_TAG'`, `'READING_STATUS_BADGE_TAG'`, `No parameter with name 'actions' found`.

- [ ] **Step 3: Add strings**

In `feature/library/src/main/res/values/strings.xml`, add before `</resources>`:
```xml
    <string name="library_search_hint">Search your library</string>
    <string name="library_search_clear">Clear search</string>
    <string name="library_filter_all">All</string>
    <string name="library_filter_count">%1$s · %2$s</string>
    <string name="library_status_badge_description">Status: %1$s. Change status</string>
    <string name="library_no_matches_title">No papers match</string>
    <string name="library_no_matches_action">Clear search and filters</string>
    <string name="library_status_update_failed">Couldn\'t update the status</string>
```
In `feature/library/src/main/res/values-ar/strings.xml`, add before `</resources>`:
```xml
    <string name="library_search_hint">ابحث في مكتبتك</string>
    <string name="library_search_clear">مسح البحث</string>
    <string name="library_filter_all">الكل</string>
    <string name="library_filter_count">%1$s (%2$s)</string>
    <string name="library_status_badge_description">الحالة: %1$s. تغيير الحالة</string>
    <string name="library_no_matches_title">لا توجد أوراق مطابقة</string>
    <string name="library_no_matches_action">مسح البحث والفلاتر</string>
    <string name="library_status_update_failed">تعذّر تحديث الحالة</string>
```

- [ ] **Step 4: Implement**

`feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryActions.kt`:
```kotlin
package com.etatech.hashiya.feature.library

import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus

/** Every user action on the Library screen. Defaults are no-ops so tests set only what they check. */
internal data class LibraryActions(
    val onQueryChange: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onClearQuery: () -> Unit = {},
    val onStatusFilterChange: (ReadingStatus?) -> Unit = {},
    val onClearSearchAndFilters: () -> Unit = {},
    val onPaperClick: (Paper) -> Unit = {},
    val onStatusChange: (Paper, ReadingStatus) -> Unit = { _, _ -> },
    val onDismissPreview: () -> Unit = {},
    val onRemove: (Paper) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onUndoDismissed: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onGoToSearch: () -> Unit = {},
    val onAddPaper: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenDoi: (String) -> Unit = {}
)
```

`feature/library/src/main/java/com/etatech/hashiya/feature/library/components/LibrarySearchField.kt`:
```kotlin
package com.etatech.hashiya.feature.library.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.feature.library.R

internal const val LIBRARY_SEARCH_FIELD_TAG = "library_search_field"

/** The Library's search box. The keyboard's Search key searches at once and hides the keyboard. */
@Composable
internal fun LibrarySearchField(
    text: String,
    onTextChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = { Text(stringResource(R.string.library_search_hint)) },
        leadingIcon = { Icon(HashiyaIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(HashiyaIcons.Close, contentDescription = stringResource(R.string.library_search_clear))
                }
            }
        },
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearch()
                focusManager.clearFocus()
            }
        ),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag(LIBRARY_SEARCH_FIELD_TAG)
    )
}
```

`feature/library/src/main/java/com/etatech/hashiya/feature/library/components/StatusFilterChips.kt`:
```kotlin
package com.etatech.hashiya.feature.library.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.component.readingStatusLabel
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.feature.library.LibraryFilter
import com.etatech.hashiya.feature.library.R
import java.text.NumberFormat

/** Single-select All · To read · Reading · Read, each with how many papers match the search. Scrolls sideways. */
@Composable
internal fun StatusFilterChips(filter: LibraryFilter, onSelect: (ReadingStatus?) -> Unit, modifier: Modifier = Modifier) {
    // The same number formatting as Search's result count, so Arabic shows Arabic-Indic digits.
    val numbers = NumberFormat.getInstance(LocalConfiguration.current.locales[0])
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusChip(stringResource(R.string.library_filter_all), numbers.format(filter.total), filter.status == null) { onSelect(null) }
        ReadingStatus.entries.forEach { status ->
            StatusChip(readingStatusLabel(status), numbers.format(filter.counts[status] ?: 0), filter.status == status) {
                onSelect(status)
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, count: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(R.string.library_filter_count, label, count)) },
        // The check marks the selection without relying on colour.
        leadingIcon = if (selected) {
            { Icon(HashiyaIcons.Check, contentDescription = null, Modifier.size(18.dp)) }
        } else {
            null
        }
    )
}
```

`feature/library/src/main/java/com/etatech/hashiya/feature/library/components/ReadingStatusBadge.kt`:
```kotlin
package com.etatech.hashiya.feature.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.component.readingStatusLabel
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.feature.library.R

internal const val READING_STATUS_BADGE_TAG = "reading_status_badge"

/**
 * A labelled pill showing a paper's status: To read is outlined, Reading is filled teal, Read is filled with a check.
 * The label always says the status, so it never relies on colour. Tapping it opens a menu to change the status.
 */
@Composable
internal fun ReadingStatusBadge(status: ReadingStatus, onStatusChange: (ReadingStatus) -> Unit, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    val label = readingStatusLabel(status)
    val description = stringResource(R.string.library_status_badge_description, label)
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        ReadingStatus.ToRead -> Color.Transparent to colors.onSurfaceVariant
        ReadingStatus.Reading -> colors.primaryContainer to colors.onPrimaryContainer
        ReadingStatus.Read -> colors.surfaceContainerHighest to colors.onSurface
    }
    Box(modifier) {
        Surface(
            onClick = { menuOpen = true },
            shape = RoundedCornerShape(50),
            color = container,
            contentColor = content,
            border = if (status == ReadingStatus.ToRead) BorderStroke(1.dp, colors.outline) else null,
            modifier = Modifier
                .testTag(READING_STATUS_BADGE_TAG)
                .semantics { contentDescription = description }
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (status == ReadingStatus.Read) {
                    Icon(HashiyaIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ReadingStatus.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(readingStatusLabel(option)) },
                    onClick = {
                        menuOpen = false
                        if (option != status) onStatusChange(option)
                    },
                    leadingIcon = {
                        if (option == status) {
                            Icon(HashiyaIcons.Check, contentDescription = null)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    modifier = Modifier.semantics { selected = option == status }
                )
            }
        }
    }
}
```

`feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryScreen.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.designsystem.component.EmptyState
import com.etatech.hashiya.core.designsystem.component.LoadingSkeleton
import com.etatech.hashiya.core.designsystem.component.PaperPreviewSheet
import com.etatech.hashiya.core.designsystem.component.paperTitle
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.feature.library.components.LibrarySearchField
import com.etatech.hashiya.feature.library.components.ReadingStatusBadge
import com.etatech.hashiya.feature.library.components.StatusFilterChips

@Composable
internal fun LibraryScreen(
    onGoToSearch: () -> Unit,
    onAddPaper: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPaper by viewModel.selectedPaper.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LibraryContent(
        uiState = uiState,
        selectedPaper = selectedPaper,
        pendingUndo = pendingUndo,
        message = message,
        actions = LibraryActions(
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::onSearch,
            onClearQuery = viewModel::onClearQuery,
            onStatusFilterChange = viewModel::onStatusFilterChange,
            onClearSearchAndFilters = viewModel::onClearSearchAndFilters,
            onPaperClick = viewModel::onPaperClick,
            onStatusChange = viewModel::onStatusChange,
            onDismissPreview = viewModel::onDismissPreview,
            onRemove = viewModel::onRemove,
            onUndo = viewModel::onUndoRemove,
            onUndoDismissed = viewModel::onUndoDismissed,
            onMessageShown = viewModel::onMessageShown,
            onGoToSearch = onGoToSearch,
            onAddPaper = onAddPaper,
            onOpenSettings = onOpenSettings,
            onOpenDoi = { doi -> runCatching { uriHandler.openUri("https://doi.org/$doi") } }
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryContent(
    uiState: LibraryUiState,
    selectedPaper: LibraryPaper?,
    pendingUndo: RemovedPaper?,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
    message: LibraryMessage? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val removedMessage = stringResource(R.string.library_removed)
    val undoLabel = stringResource(R.string.library_undo)
    // Keyed on the removed paper: each removal restarts the snackbar with its own full timeout.
    LaunchedEffect(pendingUndo) {
        if (pendingUndo != null) {
            val result = snackbarHostState.showSnackbar(removedMessage, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) actions.onUndo() else actions.onUndoDismissed()
        }
    }
    val statusUpdateFailed = stringResource(R.string.library_status_update_failed)
    LaunchedEffect(message) {
        when (message) {
            LibraryMessage.StatusUpdateFailed -> {
                snackbarHostState.showSnackbar(statusUpdateFailed)
                actions.onMessageShown()
            }

            null -> Unit
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    IconButton(onClick = actions.onOpenSettings) {
                        Icon(HashiyaIcons.Settings, contentDescription = stringResource(R.string.library_settings))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onAddPaper,
                icon = { Icon(HashiyaIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.library_add_paper)) }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            val filter = when (uiState) {
                is LibraryUiState.Papers -> uiState.filter
                is LibraryUiState.NoMatches -> uiState.filter
                LibraryUiState.Loading, LibraryUiState.Empty -> null
            }
            // The same place in the tree for Papers and NoMatches, so the field keeps focus when nothing matches.
            if (filter != null) {
                LibrarySearchField(filter.query, actions.onQueryChange, actions.onSearch, actions.onClearQuery)
                StatusFilterChips(filter, actions.onStatusFilterChange)
            }
            Box(Modifier.fillMaxSize()) {
                when (uiState) {
                    LibraryUiState.Loading -> LoadingSkeleton()

                    LibraryUiState.Empty -> EmptyState(
                        icon = HashiyaIcons.Library,
                        title = stringResource(R.string.library_empty_title),
                        message = stringResource(R.string.library_empty_message),
                        actionLabel = stringResource(R.string.library_go_to_search),
                        onAction = actions.onGoToSearch
                    )

                    is LibraryUiState.NoMatches -> EmptyState(
                        icon = HashiyaIcons.SearchOff,
                        title = stringResource(R.string.library_no_matches_title),
                        message = null,
                        actionLabel = stringResource(R.string.library_no_matches_action),
                        onAction = actions.onClearSearchAndFilters
                    )

                    is LibraryUiState.Papers -> PaperList(uiState.papers, actions)
                }
            }
        }
    }

    selectedPaper?.let { selected ->
        PaperPreviewSheet(
            paper = selected.paper,
            inLibrary = true,
            onDismiss = actions.onDismissPreview,
            onToggleSave = { actions.onRemove(selected.paper) },
            onOpenDoi = actions.onOpenDoi,
            status = selected.status,
            onStatusChange = { status -> actions.onStatusChange(selected.paper, status) }
        )
    }
}

// Scaffold's floatingActionButton doesn't reserve content padding for the FAB, so the list must
// leave room itself: the extended FAB is 56dp tall with a 16dp margin, plus a little breathing room.
private val FAB_CLEARANCE = PaddingValues(bottom = 88.dp)

@Composable
private fun PaperList(papers: List<LibraryPaper>, actions: LibraryActions) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = FAB_CLEARANCE) {
        item {
            Text(
                pluralStringResource(R.plurals.library_paper_count, papers.size, papers.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        items(papers, key = { it.paper.openAlexId }) { item ->
            SwipeToRemove(onRemove = { actions.onRemove(item.paper) }) {
                LibraryRow(
                    item = item,
                    onClick = { actions.onPaperClick(item.paper) },
                    onStatusChange = { status -> actions.onStatusChange(item.paper, status) }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun SwipeToRemove(onRemove: () -> Unit, content: @Composable () -> Unit) {
    // Deliberately not rememberSwipeToDismissBoxState(): that one is saveable, so when Undo brings the same key
    // back, the lazy list restores its dismissed value and SwipeToDismissBox removes the paper again.
    val positionalThreshold = SwipeToDismissBoxDefaults.positionalThreshold
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold) }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onRemove() },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    HashiyaIcons.Delete,
                    contentDescription = stringResource(R.string.library_remove),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) { content() }
}

@Composable
private fun LibraryRow(item: LibraryPaper, onClick: () -> Unit, onStatusChange: (ReadingStatus) -> Unit) {
    val paper = item.paper
    val firstAuthor = paper.authors.firstOrNull()?.name
    val authorText = when {
        firstAuthor == null -> null
        paper.authors.size == 1 -> firstAuthor
        else -> stringResource(R.string.library_et_al, firstAuthor)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                paperTitle(paper),
                style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // Full width so the text aligns by its own direction, even on one line.
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                listOfNotNull(authorText, paper.year?.toString(), paper.venue).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(12.dp))
        ReadingStatusBadge(item.status, onStatusChange)
    }
}
```

- [ ] **Step 5: Run the tests and inspect the screenshots locally**

Run: `./gradlew :feature:library:testDebugUnitTest :feature:search:testDebugUnitTest :core:designsystem:testDebugUnitTest :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; the 17 `LibraryContentTest` tests, the 20 `LibraryScreenshotTest` variants (the Arabic assertions included), the 18 `LibraryViewModelTest` tests, `LibrarySwipeUndoTest`, the new Search guard and every existing test pass.

Run: `./gradlew :feature:library:recordRoborazziDebug` and open `library_papers-*`, `library_search-*`, `library_no_matches-*` and `library_status_menu-*`. Check: the search field and a sideways-scrolling chip row (All · 3, To read · 1, …) sit under the top bar; each row ends with its badge — Reading filled teal, Read with a check, To read outlined; the selected chip has a check; the menu lists the three statuses with the current one checked; "No papers match" has no blank line under the title; in Arabic the layout mirrors (badges on the left, chips starting at the right), counts use Arabic-Indic digits in parentheses (الكل (٣)) and English titles stay left-to-right. `library_empty-*` should look unchanged. Local images are for inspection only; discard them with `git checkout -- feature/library/src/test/screenshots && git clean -fq feature/library/src/test/screenshots`.

- [ ] **Step 6: Format**

Run: `./gradlew spotlessApply`
Expected: `BUILD SUCCESSFUL`, no changes to the code above.

- [ ] **Step 7: Commit the code without the local screenshots**

```bash
git add -A -- . ':(exclude).idea/**' ':(exclude,glob)**/src/test/screenshots/**'
git commit -m "feat: add library search, status chips and status badges to the Library screen"
```

- [ ] **Step 8: Record the baselines on Linux and commit them**

Run (10–15 minutes; run it in the background): `bash scripts/record-screenshots-on-linux.sh`
Expected: ends with `Baselines copied from run <id>`; `git status` shows the 12 new `library_search-*`, `library_no_matches-*` and `library_status_menu-*` files and the 4 changed `library_papers-*` files, and no other changed baselines (if `library_empty-*` shows up, open it: it must look identical; the layout moved into a column but nothing visible changed). Open one English and one Arabic image of each new screen to confirm they match what you inspected.

```bash
git add -- ':(glob)**/src/test/screenshots/**'
git commit -m "test: record library search and status screenshot baselines on Linux"
```

---

### Task 7: README, full verification and on-device checks

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Update the README**

In `README.md`, under `## Features`, add this line before `- Swipe to remove from the library, with Undo.`:
```markdown
- Search your library offline by words from a paper's title, authors, abstract or venue (Arabic search ignores tashkeel and letter variants), and track each paper as To read, Reading or Read with status filters and counts.
```
In the `## Architecture` diagram, add the new edge after `    core/designsystem --> core/model`:
```
    core/database --> core/model
```
In the `## Roadmap` list, change `3. Library: full-text search and reading status` to `3. ✅ Library: full-text search and reading status`.

- [ ] **Step 2: Run the full verification**

Run: `./gradlew spotlessCheck assembleDebug testDebugUnitTest :core:model:test lintDebug`
Expected: `BUILD SUCCESSFUL`; Lint reports no `MissingTranslation` errors.

Then let CI verify the screenshots against the Linux baselines:
```bash
git add README.md
git commit -m "docs: describe library search and reading status"
git push -u origin feat/library-search-and-status
run_id=$(gh run list --branch feat/library-search-and-status --workflow ci.yml --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$run_id" --exit-status
```
Expected: the `build` job succeeds. If it fails only on screenshot diffs, download the `screenshot-diffs` artifact and report; re-record only when the change is intended.

- [ ] **Step 3: On-device acceptance checks** (spec §10; needs a connected phone)

1. Install the sub-project 2 build (`git checkout 1be1d8f && ./gradlew :app:installDebug`), save three papers, then install this branch over it (`git checkout feat/library-search-and-status && ./gradlew :app:installDebug`) without uninstalling → the app opens, every saved paper is still there, each marked To read, and the chips read All · 3, To read · 3.
2. In the Library, search for an author's surname, then a word from an abstract → the paper is found; type "transf" → papers with "transformer" appear after a short pause; the keyboard's Search key applies at once and hides the keyboard.
3. Change a status from a row's badge, and another from the preview's selector → the badge, the selector and the chip counts update; no snackbar.
4. Select the Reading chip, then search → both apply; search for a word no paper has → "No papers match" with the field still focused; **Clear search and filters** resets both.
5. Mark a paper Reading, swipe it away, tap **Undo** → it returns in its place, still Reading.
6. Switch to العربية: search for "التَّعلُّم" and for "التعلم" → the same papers; the layout mirrors, counts use Arabic-Indic digits and English titles stay left-to-right. With TalkBack, a badge reads "الحالة: قيد القراءة. تغيير الحالة".
