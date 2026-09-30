# Paper Details and Structured Notes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping a saved paper in the Library opens a Details screen with every author, the full abstract, the DOI and PDF links, the reading status, and six note sections (Summary, Research question, Method, Key findings, Limitations, My thoughts). The notes save as you type, can be searched from the Library, and come back with Undo.

**Architecture:**
- **`core/model`** gains `NoteSection` and `PaperNotes`.
- **`core/database`** moves to schema version 3:
  - a `paper_notes` table that cascades from `papers`;
  - a `notes` column in the FTS4 `paper_search` table, rebuilt by `MIGRATION_2_3` through a plain temporary table;
  - DAO transactions that keep notes and index in step.
- **`LibraryRepository`** gains `observePaper`, `observeNotes` and `saveNotes`, and `RemovedPaper` carries the notes.
- **`feature/paperdetails`** is a new module:
  - a ViewModel that reads the notes once, autosaves them after a 500 ms pause (one sequential writer behind a `Mutex`), and flushes them on `ON_STOP` and `onCleared` through the `@ApplicationScope`;
  - a scrolling Compose screen.
- **Library and Search** open Details through `onOpenPaper`. A Remove on Details is handed back to the screen below through a key in that back stack entry's `SavedStateHandle`, so the existing Undo is reused.

**Tech Stack:** Kotlin 2.4.20, AGP 9.2.1, Jetpack Compose + Material 3 (BOM 2026.09.00), Navigation Compose 2.10.2 (type-safe routes), Hilt 2.60.1 + KSP, Room 2.8.5 (`@Fts4`, `@Upsert`, `MigrationTestHelper`), Coroutines/Flow, JUnit 4, Robolectric 4.17 (sdk 35), Roborazzi 1.75.0.

**Spec:** docs/superpowers/specs/2026-09-30-paper-details-and-notes-design.md

## Global Constraints

- **Unchanged from sub-projects 1–3:**
  - base package `com.etatech.hashiya`;
  - `compileSdk = 37`, `targetSdk = 36`, `minSdk = 24`;
  - versions only in `gradle/libs.versions.toml`, and no new libraries are needed;
  - KSP only;
  - no `org.jetbrains.kotlin.android` plugin.
- **Dependency rules:**
  - `feature/*` → `core/data`, `core/model`, `core/designsystem` only, and a feature never depends on another feature;
  - `core/network`, `core/database`, `core/datastore` never depend on each other;
  - `core/model` has no Android dependency.

  The new module `feature/paperdetails` uses the `hashiya.android.feature` convention plugin, which adds exactly those three.
- **Search normalization:** `searchableText` stays the only search normalization. The notes column gets its text from `notesSearchText(notes)` in `core/database`: `""` for empty notes, otherwise `searchableText` of the six sections joined with spaces. Queries still come only from `ftsMatch`.
- **Schema version 3:**
  - `MIGRATION_1_2` and `MIGRATION_2_3` are both registered through `addMigrations`;
  - there is no `fallbackToDestructiveMigration*` anywhere;
  - `1.json` and `2.json` stay, and the generated `3.json` is committed;
  - a schema change never lands in a commit without its version bump and migration.
- **Notes rows:** a `paper_notes` row exists only while `PaperNotes.isEmpty` is false. Blank notes delete the row. Note text is stored exactly as typed, never trimmed.
- **The Details ViewModel reads notes once** (the first value of `observeNotes`), and later emissions are ignored. Every write goes through one `save(value)` guarded by a `Mutex`, which skips a value equal to the last one stored.
- **Strings** come from spec §8, verbatim, in both `values/strings.xml` and `values-ar/strings.xml` of `feature/paperdetails`:
  - `details_back` "Back" / "رجوع";
  - `details_more_options` "More options" / "خيارات أخرى";
  - `details_open_pdf` "Open PDF" / "فتح ملف PDF";
  - `details_notes_title` "My notes" / "ملاحظاتي";
  - `details_notes_saving` "Saving…" / "جارٍ الحفظ…";
  - `details_notes_saved` "Saved" / "تم الحفظ";
  - `details_notes_save_failed` "Couldn\'t save" / "تعذّر الحفظ";
  - `details_notes_save_failed_message` "Couldn\'t save your notes" / "تعذّر حفظ ملاحظاتك";
  - `details_retry` "Retry" / "إعادة المحاولة";
  - `details_status_update_failed` "Couldn\'t update the status" / "تعذّر تحديث الحالة";
  - `note_summary` "Summary" / "الخلاصة", and `note_summary_hint` "What is this paper about, in your own words?" / "عمّ تتحدث هذه الورقة، بكلماتك أنت؟";
  - `note_research_question` "Research question" / "سؤال البحث", and `note_research_question_hint` "What question or problem does it address?" / "ما السؤال أو المشكلة التي تعالجها؟";
  - `note_method` "Method" / "المنهجية", and `note_method_hint` "How did the authors approach it?" / "كيف تناولها المؤلفون؟";
  - `note_key_findings` "Key findings" / "أهم النتائج", and `note_key_findings_hint` "What did they find?" / "ما الذي توصّلوا إليه؟";
  - `note_limitations` "Limitations" / "القيود", and `note_limitations_hint` "What are its weaknesses or open questions?" / "ما نقاط ضعفها أو الأسئلة التي تتركها مفتوحة؟";
  - `note_thoughts` "My thoughts" / "أفكاري", and `note_thoughts_hint` "How does it relate to your work?" / "ما علاقتها ببحثك؟".

  In `core/designsystem` there is one new string, `designsystem_open_details` "Open details" / "فتح التفاصيل". Existing strings are reused: `designsystem_open_doi`, `designsystem_remove_from_library`, `designsystem_abstract`, `designsystem_no_abstract`, `designsystem_untitled`, and the `status_*` labels.
- **Layout and text rules:**
  - no string literals in composables;
  - layouts use start/end;
  - paper content and note fields use `TextDirection.Content`;
  - direction-implying icons are `Icons.AutoMirrored`;
  - icons come only from `HashiyaIcons`.
- **Tests:**
  - hand-written fakes, with no mocking libraries;
  - Robolectric tests run at SDK 35 (`src/test/resources/robolectric.properties` → `sdk=35`);
  - Compose UI test classes use `@Config(qualifiers = PHONE_QUALIFIERS)`.
- **Screenshot tests:**
  - `ScreenshotVariantRule` at `@get:Rule(order = 0)` and the compose rule at `order = 1`;
  - every `captureScreenshot(name, variant, arabicText)` passes an `arabicText` that is a string from the app's `values-ar` resources (never paper content) and appears on exactly one node.
- **Screenshot baselines come from CI Linux only.**
  - Commit code without screenshots: `git add -A -- . ':(exclude).idea/**' ':(exclude,glob)**/src/test/screenshots/**'`.
  - In the final task, run `bash scripts/record-screenshots-on-linux.sh` (10–15 minutes, so run it in the background) and commit the downloaded PNGs with `git add -- ':(glob)**/src/test/screenshots/**'`.
  - Local `recordRoborazziDebug` output is for inspection only.
- **Git:**
  - work on the existing branch `feat/paper-details-and-notes`, which already holds the spec commit;
  - never commit to `main` and never stage `.idea/`;
  - run `./gradlew spotlessApply` before every commit (ktlint `android_studio` style, `max_line_length = 140`; it re-wraps long lines and removes unused imports, which is expected);
  - commit messages use `feat:`/`fix:`/`test:`/`docs:`/`build:`;
  - before the first commit, check that `git config user.email` is `fady.fouad.a@gmail.com`;
  - no AI or Claude attribution anywhere: no trailers, links or credits in commits, code comments or docs.

## Review Focus

1. **Leaving Details less than 500 ms after typing** (Back, Home, the app killed from the recents screen, or Remove) → what was typed is saved. Pinned by `PaperDetailsViewModelTest`:
   - `flushSavesPendingNotesAtOnce`;
   - `clearingTheViewModelSavesPendingNotes`, which cancels `viewModelScope` the way leaving does;
   - `removeSavesPendingNotesBeforeLeaving` (Task 5).
2. **A database emission while the user is typing** (another write, or the debounced save's own write re-emitting `observeNotes`) → the fields keep what is being typed. Pinned by `PaperDetailsViewModelTest.laterStoredNotesDoNotReplaceTyping` (Task 5).
3. **Deleting every note** (select all, then delete) → the row is deleted, a search for the old note words stops finding the paper, and a search for its title still finds it. Pinned by:
   - `PaperDaoTest.savingBlankNotesDeletesTheRowAndClearsTheIndex` (Task 2);
   - `PaperDetailsViewModelTest.clearingEveryNoteSavesBlankNotes` (Task 5).
4. **Undo after Remove on Details** → the paper comes back with its notes, and the notes are searchable again. Pinned by:
   - `RoomLibraryRepositoryTest.removeThenRestoreKeepsTheNotesAndTheirSearch` (Task 3);
   - `HashiyaAppNavigationTest.removeOnDetailsReturnsToTheLibraryWithUndo` (Task 8).
5. **A remove request that reaches a Library ViewModel created after it was written** (process death between the pop and the handling) → the paper is removed once, with Undo, and no crash from an uninitialized property. Pinned by `LibraryViewModelTest.removeRequestWaitingWhenTheViewModelStartsIsHandled` (Task 7).

---

## File Structure

```
core/model/.../core/model/PaperNotes.kt                                   NoteSection, PaperNotes (Task 1)
core/database/.../model/PaperNotesEntity.kt, DeletedPaper.kt              notes table, conversions, deletion result (Task 2)
core/database/.../model/PaperSearchEntity.kt                              notes column, notesSearchText (Task 2)
core/database/.../dao/PaperDao.kt, HashiyaDatabase.kt, di/DatabaseModule.kt   notes queries, version 3, MIGRATION_2_3 registered (Task 2)
core/database/.../migration/Migrations.kt, schemas/.../3.json             MIGRATION_2_3, exported schema (Task 2)
core/data/.../repository/LibraryRepository.kt, RoomLibraryRepository.kt  observePaper, observeNotes, saveNotes, notes in RemovedPaper (Task 3)
core/data/.../mapping/PaperEntityMapping.kt                               notes into the search row (Task 3)
core/testing/.../FakeLibraryRepository.kt                                 notes, notes search, failOnSaveNotes, notesSaves (Task 3)
core/designsystem/.../component/PaperHeader.kt, PaperPreview.kt           shared header + abstract, Open details (Task 4)
core/designsystem/.../icon/HashiyaIcons.kt                                MoreOptions (Task 4)
feature/paperdetails/ (new module)                                        ViewModel (Task 5); screen, navigation, strings (Task 6)
feature/library/..., feature/search/...                                   open Details, remove requests, no Library sheet (Task 7)
app/.../navigation/HashiyaApp.kt, app/build.gradle.kts, settings.gradle.kts   wiring (Task 5 includes the module; Task 8 wires it)
README.md, screenshots (Task 9)
```

---

### Task 1: `core/model` — `NoteSection` and `PaperNotes`

**Files:**
- Create: `core/model/src/main/kotlin/com/etatech/hashiya/core/model/PaperNotes.kt`
- Test: `core/model/src/test/kotlin/com/etatech/hashiya/core/model/PaperNotesTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum class NoteSection { Summary, ResearchQuestion, Method, KeyFindings, Limitations, Thoughts }` (declaration order is the on-screen order);
  - `data class PaperNotes(summary, researchQuestion, method, keyFindings, limitations, thoughts: String = "")` with `operator fun get(section: NoteSection): String`, `fun with(section: NoteSection, text: String): PaperNotes` and `val isEmpty: Boolean`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.etatech.hashiya.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperNotesTest {
    @Test
    fun sectionsAreInTemplateOrder() {
        assertEquals(
            listOf(
                NoteSection.Summary,
                NoteSection.ResearchQuestion,
                NoteSection.Method,
                NoteSection.KeyFindings,
                NoteSection.Limitations,
                NoteSection.Thoughts
            ),
            NoteSection.entries
        )
    }

    @Test
    fun everySectionReadsBackWhatWasWritten() {
        val notes = NoteSection.entries.fold(PaperNotes()) { acc, section -> acc.with(section, "text ${section.name}") }

        NoteSection.entries.forEach { section -> assertEquals("text ${section.name}", notes[section]) }
    }

    @Test
    fun withChangesOnlyItsSection() {
        val notes = PaperNotes(summary = "s", method = "m").with(NoteSection.Method, "new")

        assertEquals(PaperNotes(summary = "s", method = "new"), notes)
    }

    @Test
    fun textIsKeptExactlyAsTyped() {
        assertEquals("  two\nlines ", PaperNotes().with(NoteSection.Thoughts, "  two\nlines ")[NoteSection.Thoughts])
    }

    @Test
    fun blankSectionsAreEmpty() {
        assertTrue(PaperNotes().isEmpty)
        assertTrue(PaperNotes(summary = "  ", thoughts = "\n\t").isEmpty)
        assertFalse(PaperNotes(limitations = "small sample").isEmpty)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:model:test --tests '*PaperNotesTest'`
Expected: compilation FAILS with "Unresolved reference: NoteSection".

- [ ] **Step 3: Write the implementation**

```kotlin
package com.etatech.hashiya.core.model

/** The fixed note template. Declaration order is the order on screen. */
enum class NoteSection { Summary, ResearchQuestion, Method, KeyFindings, Limitations, Thoughts }

/** The user's notes on a saved paper, one plain-text field per [NoteSection]. Text is kept exactly as typed. */
data class PaperNotes(
    val summary: String = "",
    val researchQuestion: String = "",
    val method: String = "",
    val keyFindings: String = "",
    val limitations: String = "",
    val thoughts: String = ""
) {
    operator fun get(section: NoteSection): String = when (section) {
        NoteSection.Summary -> summary
        NoteSection.ResearchQuestion -> researchQuestion
        NoteSection.Method -> method
        NoteSection.KeyFindings -> keyFindings
        NoteSection.Limitations -> limitations
        NoteSection.Thoughts -> thoughts
    }

    fun with(section: NoteSection, text: String): PaperNotes = when (section) {
        NoteSection.Summary -> copy(summary = text)
        NoteSection.ResearchQuestion -> copy(researchQuestion = text)
        NoteSection.Method -> copy(method = text)
        NoteSection.KeyFindings -> copy(keyFindings = text)
        NoteSection.Limitations -> copy(limitations = text)
        NoteSection.Thoughts -> copy(thoughts = text)
    }

    /** True when every section is blank (empty or whitespace only). Blank notes are not stored. */
    val isEmpty: Boolean get() = NoteSection.entries.all { this[it].isBlank() }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:model:test --tests '*PaperNotesTest'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git config user.email   # must print fady.fouad.a@gmail.com
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**' ':(exclude,glob)**/src/test/screenshots/**'
git commit -m "feat: add the paper notes model"
```

---

### Task 2: `core/database` — schema version 3 with the notes table, the notes search column and `MIGRATION_2_3`

**Files:**
- Create: `core/database/src/main/java/com/etatech/hashiya/core/database/model/PaperNotesEntity.kt`
- Create: `core/database/src/main/java/com/etatech/hashiya/core/database/model/DeletedPaper.kt`
- Modify: `core/database/src/main/java/com/etatech/hashiya/core/database/model/PaperSearchEntity.kt`
- Modify: `core/database/src/main/java/com/etatech/hashiya/core/database/dao/PaperDao.kt`
- Modify: `core/database/src/main/java/com/etatech/hashiya/core/database/HashiyaDatabase.kt`
- Modify: `core/database/src/main/java/com/etatech/hashiya/core/database/migration/Migrations.kt`
- Modify: `core/database/src/main/java/com/etatech/hashiya/core/database/di/DatabaseModule.kt`
- Create (generated): `core/database/schemas/com.etatech.hashiya.core.database.HashiyaDatabase/3.json`
- Test: `core/database/src/test/java/com/etatech/hashiya/core/database/dao/PaperDaoTest.kt`, `core/database/src/test/java/com/etatech/hashiya/core/database/migration/MigrationTest.kt`

**Interfaces:**
- Consumes: `PaperNotes`, `NoteSection` (Task 1); the existing `searchableText`.
- Produces:
  - `PaperNotesEntity(paperId, summary, researchQuestion, method, keyFindings, limitations, thoughts: String, updatedAt: Long)`;
  - `fun PaperNotes.asEntity(paperId: String, updatedAt: Long): PaperNotesEntity`;
  - `fun PaperNotesEntity.asPaperNotes(): PaperNotes`;
  - `fun notesSearchText(notes: PaperNotes): String`;
  - `searchEntityFor(paperId, title, authorNames, abstract, venue, notes: PaperNotes? = null)`;
  - `data class DeletedPaper(val paper: PaperWithAuthors, val notes: PaperNotesEntity?)`.

  New `PaperDao` members:
  - `observeByOpenAlexId(openAlexId: String): Flow<PaperWithAuthors?>`;
  - `observeNotes(openAlexId: String): Flow<PaperNotesEntity?>`;
  - `suspend fun saveNotes(openAlexId: String, notes: PaperNotes, updatedAt: Long): Boolean`;
  - `deleteByOpenAlexId(openAlexId): DeletedPaper?`;
  - `insertPaperWithAuthors(paper, authors, search, notes: PaperNotesEntity? = null): Boolean`;
  - `internal val MIGRATION_2_3`.

- [ ] **Step 1: Write the failing DAO tests**

In `PaperDaoTest.kt`:
- add the imports `com.etatech.hashiya.core.database.model.asPaperNotes` and `com.etatech.hashiya.core.model.PaperNotes`;
- update the two existing tests that read `deleteByOpenAlexId`'s result, because it now returns a `DeletedPaper`;
- add the helper and the new tests below the existing ones.

Replace `deletingReturnsRowAndCascadesToAuthorsAndSearchRow` and `restoringDeletedRowKeepsIdSavedAtAndStatus` with:

```kotlin
    @Test
    fun deletingReturnsRowAndCascadesToAuthorsAndSearchRow() = runTest {
        save(paper("a", "W1", 100), "Ada", "Bo")

        val removed = dao.deleteByOpenAlexId("W1")

        assertEquals("a", removed?.paper?.paper?.id)
        assertEquals(2, removed?.paper?.authors?.size)
        assertNull(removed?.notes)
        assertTrue(dao.observeLibrary(null, null).first().isEmpty())
        assertEquals(0, count("paper_authors"))
        assertEquals(0, count("paper_search"))
    }

    @Test
    fun restoringDeletedRowKeepsIdSavedAtAndStatus() = runTest {
        save(paper("a", "W1", 100, status = "reading"), "Ada")
        save(paper("b", "W2", 200), "Bo")
        val removed = dao.deleteByOpenAlexId("W1")!!

        save(removed.paper.paper, "Ada")

        val saved = dao.observeLibrary(null, null).first()
        assertEquals(listOf("b", "a"), saved.map { it.paper.id })
        assertEquals(100L, saved.last().paper.savedAt)
        assertEquals("reading", saved.last().paper.readingStatus)
        assertEquals(listOf("a"), ids(match = "\"ada*\""))
    }
```

Add after the `ids` helper:

```kotlin
    private fun searchNotes(paperId: String): String =
        db.query("SELECT notes FROM paper_search WHERE paper_id = ?", arrayOf(paperId)).use {
            it.moveToFirst()
            it.getString(0)
        }
```

Add at the end of the class:

```kotlin
    @Test
    fun savingNotesStoresThemAndIndexesThem() = runTest {
        save(paper("a", "W1", 100))

        assertTrue(dao.saveNotes("W1", PaperNotes(summary = "Self-attention only", method = "Ablation"), updatedAt = 5))

        val stored = dao.observeNotes("W1").first()
        assertEquals(PaperNotes(summary = "Self-attention only", method = "Ablation"), stored?.asPaperNotes())
        assertEquals(5L, stored?.updatedAt)
        assertEquals(listOf("a"), ids(match = "\"ablation*\""))
    }

    @Test
    fun savingNotesAgainReplacesThemAndTheirIndex() = runTest {
        save(paper("a", "W1", 100))
        dao.saveNotes("W1", PaperNotes(method = "Ablation"), updatedAt = 5)

        dao.saveNotes("W1", PaperNotes(method = "Survey"), updatedAt = 6)

        assertEquals("Survey", dao.observeNotes("W1").first()?.method)
        assertEquals(1, count("paper_notes"))
        assertEquals(emptyList<String>(), ids(match = "\"ablation*\""))
        assertEquals(listOf("a"), ids(match = "\"survey*\""))
    }

    @Test
    fun savingBlankNotesDeletesTheRowAndClearsTheIndex() = runTest {
        save(paper("a", "W1", 100))
        dao.saveNotes("W1", PaperNotes(method = "Ablation"), updatedAt = 5)

        assertTrue(dao.saveNotes("W1", PaperNotes(method = "  "), updatedAt = 6))

        assertNull(dao.observeNotes("W1").first())
        assertEquals(0, count("paper_notes"))
        assertEquals("", searchNotes("a"))
        assertEquals(emptyList<String>(), ids(match = "\"ablation*\""))
        // The rest of the search row is untouched: the title ("Title a") still finds it.
        assertEquals(listOf("a"), ids(match = "\"title*\""))
    }

    @Test
    fun savingNotesForAnUnsavedPaperWritesNothing() = runTest {
        assertFalse(dao.saveNotes("missing", PaperNotes(summary = "x"), updatedAt = 1))

        assertEquals(0, count("paper_notes"))
    }

    @Test
    fun notesAreSearchedWithTheSameFolding() = runTest {
        save(paper("a", "W1", 100))
        dao.saveNotes("W1", PaperNotes(keyFindings = "التَّعلُّم العميق يتفوّق"), updatedAt = 1)

        assertEquals(listOf("a"), ids(match = "\"التعلم*\""))
    }

    @Test
    fun observingAPaperFollowsItUntilItIsDeleted() = runTest {
        save(paper("a", "W1", 100), "Ada")
        assertEquals("a", dao.observeByOpenAlexId("W1").first()?.paper?.id)

        dao.deleteByOpenAlexId("W1")

        assertNull(dao.observeByOpenAlexId("W1").first())
    }

    @Test
    fun deletingReturnsTheNotesAndLeavesNoNotesRow() = runTest {
        save(paper("a", "W1", 100))
        dao.saveNotes("W1", PaperNotes(thoughts = "Useful for chapter 2"), updatedAt = 7)

        val removed = dao.deleteByOpenAlexId("W1")

        assertEquals(PaperNotes(thoughts = "Useful for chapter 2"), removed?.notes?.asPaperNotes())
        assertEquals(0, count("paper_notes"))
    }

    @Test
    fun restoringWithNotesBringsBackTheRowAndItsIndex() = runTest {
        save(paper("a", "W1", 100), "Ada")
        dao.saveNotes("W1", PaperNotes(thoughts = "Useful for chapter 2"), updatedAt = 7)
        val removed = dao.deleteByOpenAlexId("W1")!!
        val notes = removed.notes!!.asPaperNotes()

        dao.insertPaperWithAuthors(
            removed.paper.paper,
            removed.paper.authors,
            searchEntityFor("a", removed.paper.paper.title, listOf("Ada"), null, "Venue", notes),
            removed.notes
        )

        assertEquals(notes, dao.observeNotes("W1").first()?.asPaperNotes())
        assertEquals(listOf("a"), ids(match = "\"chapter*\""))
    }
```

- [ ] **Step 2: Write the failing migration tests**

In `MigrationTest.kt`:
- add the imports `com.etatech.hashiya.core.model.PaperNotes` and `org.junit.Assert.assertTrue`;
- add `createVersion2()` and an `openWithRoom()` helper;
- switch `migratedLibraryOpensWithRoomAndIsSearchable` to the helper, because a Room database now opens at version 3 and needs both migrations;
- add three tests.

Add below `createVersion1()`:

```kotlin
    /** A version 2 library as sub-project 3 left it: statuses mixed, search rows already normalized. */
    private fun createVersion2() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            db.execSQL(
                "INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, " +
                    "oa_pdf_url, saved_at, reading_status) VALUES ('a', 'W1', NULL, 'Attention Is All You Need', 2017, " +
                    "'Neural Information Processing Systems', 'The dominant sequence transduction models', 128412, 1, NULL, 100, 'reading')"
            )
            db.execSQL("INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 0, 'Ashish Vaswani', NULL)")
            db.execSQL("INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 1, 'Noam Shazeer', NULL)")
            db.execSQL(
                "INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, " +
                    "oa_pdf_url, saved_at, reading_status) VALUES ('b', 'W2', NULL, 'تطبيقات التَّعلُّم العميق', NULL, NULL, NULL, 0, 0, " +
                    "NULL, 200, 'to_read')"
            )
            db.execSQL(
                "INSERT INTO paper_search (paper_id, title, authors, abstract, venue) VALUES ('a', 'attention is all you need', " +
                    "'ashish vaswani noam shazeer', 'the dominant sequence transduction models', 'neural information processing systems')"
            )
            db.execSQL("INSERT INTO paper_search (paper_id, title, authors, abstract, venue) VALUES ('b', 'تطبيقات التعلم العميق', '', '', '')")
        }
    }

    private fun openWithRoom(): HashiyaDatabase =
        Room.databaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
```

In `migratedLibraryOpensWithRoomAndIsSearchable`, replace the `Room.databaseBuilder(…).build()` expression with `openWithRoom()`.

Add at the end of the class:

```kotlin
    @Test
    fun migration2To3KeepsPapersStatusesAndSearchRowsAndValidatesAgainstVersion3Schema() {
        createVersion2()

        // Validates every table, including paper_notes and the rebuilt paper_search, against schemas/…/3.json.
        helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3).use { db ->
            assertEquals(listOf("a:reading", "b:to_read"), db.strings("SELECT id || ':' || reading_status FROM papers ORDER BY id"))
            assertEquals(
                listOf("a:attention is all you need:ashish vaswani noam shazeer:", "b:تطبيقات التعلم العميق::"),
                db.strings("SELECT paper_id || ':' || title || ':' || authors || ':' || notes FROM paper_search ORDER BY paper_id")
            )
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM paper_notes"))
        }
    }

    @Test
    fun libraryMigratedFromVersion2FindsTheSamePapersAndTakesNotes() = runTest {
        createVersion2()
        helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3).close()

        val database = openWithRoom()
        try {
            val dao = database.paperDao()
            suspend fun ids(match: String) = dao.observeLibrary(match, null).first().map { it.paper.id }

            assertEquals(listOf("a"), ids("\"shazeer*\""))
            assertEquals(listOf("a"), ids("\"transduction*\""))
            assertEquals(listOf("b"), ids("\"التعلم*\""))
            assertTrue(dao.saveNotes("W1", PaperNotes(method = "Ablation study"), updatedAt = 1))
            assertEquals(listOf("a"), ids("\"ablation*\""))
        } finally {
            database.close()
        }
    }

    @Test
    fun version1LibraryMigratesAllTheWayToVersion3() {
        createVersion1()

        helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_1_2, MIGRATION_2_3).use { db ->
            assertEquals(listOf("a:to_read", "b:to_read"), db.strings("SELECT id || ':' || reading_status FROM papers ORDER BY id"))
            assertEquals(
                listOf("a:attention is all you need:"),
                db.strings("SELECT paper_id || ':' || title || ':' || notes FROM paper_search WHERE paper_id = 'a'")
            )
        }
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :core:database:testDebugUnitTest --tests '*PaperDaoTest' --tests '*MigrationTest'`
Expected: compilation FAILS with unresolved references to `saveNotes`, `observeNotes`, `asPaperNotes`, `MIGRATION_2_3`.

- [ ] **Step 4: Add the notes entity and the deletion result**

`core/database/src/main/java/com/etatech/hashiya/core/database/model/PaperNotesEntity.kt`:

```kotlin
package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.etatech.hashiya.core.model.PaperNotes

/** A saved paper's notes. The row exists only while at least one section has text; deleting the paper deletes it. */
@Entity(
    tableName = "paper_notes",
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paper_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PaperNotesEntity(
    @PrimaryKey @ColumnInfo(name = "paper_id") val paperId: String,
    val summary: String,
    @ColumnInfo(name = "research_question") val researchQuestion: String,
    val method: String,
    @ColumnInfo(name = "key_findings") val keyFindings: String,
    val limitations: String,
    val thoughts: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

fun PaperNotes.asEntity(paperId: String, updatedAt: Long): PaperNotesEntity = PaperNotesEntity(
    paperId = paperId,
    summary = summary,
    researchQuestion = researchQuestion,
    method = method,
    keyFindings = keyFindings,
    limitations = limitations,
    thoughts = thoughts,
    updatedAt = updatedAt
)

fun PaperNotesEntity.asPaperNotes(): PaperNotes = PaperNotes(
    summary = summary,
    researchQuestion = researchQuestion,
    method = method,
    keyFindings = keyFindings,
    limitations = limitations,
    thoughts = thoughts
)
```

`core/database/src/main/java/com/etatech/hashiya/core/database/model/DeletedPaper.kt`:

```kotlin
package com.etatech.hashiya.core.database.model

/** What [com.etatech.hashiya.core.database.dao.PaperDao.deleteByOpenAlexId] deleted, so it can be restored. */
data class DeletedPaper(val paper: PaperWithAuthors, val notes: PaperNotesEntity?)
```

- [ ] **Step 5: Add the notes column to the search index**

Replace `PaperSearchEntity.kt` with:

```kotlin
package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.searchableText

/**
 * The full-text index: one row per saved paper, keyed by [paperId] (the paper's local id, not indexed).
 * A standalone FTS table, because author names and notes live in other tables; [PaperDao] keeps it in step with them.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["paper_id"])
@Entity(tableName = "paper_search")
data class PaperSearchEntity(
    @ColumnInfo(name = "paper_id") val paperId: String,
    val title: String,
    /** Author names joined with spaces. */
    val authors: String,
    val abstract: String,
    val venue: String,
    /** Every note section joined with spaces ([notesSearchText]); empty when the paper has no notes. */
    val notes: String
)

/** The search row for a paper, every column passed through [searchableText]. Saves, restores and the 1 → 2 migration use it. */
fun searchEntityFor(
    paperId: String,
    title: String,
    authorNames: List<String>,
    abstract: String?,
    venue: String?,
    notes: PaperNotes? = null
): PaperSearchEntity = PaperSearchEntity(
    paperId = paperId,
    title = searchableText(title),
    authors = searchableText(authorNames.joinToString(" ")),
    abstract = searchableText(abstract.orEmpty()),
    venue = searchableText(venue.orEmpty()),
    notes = notes?.let(::notesSearchText).orEmpty()
)

/** The search column text for [notes]: every section joined with spaces, through [searchableText]; empty for empty notes. */
fun notesSearchText(notes: PaperNotes): String =
    if (notes.isEmpty) "" else searchableText(NoteSection.entries.joinToString(" ") { notes[it] })
```

Check the existing imports in the file first. If it imports other things (for example `androidx.room.ColumnInfo` under a different alias), keep them. Only the `notes` field, the `notes` parameter and `notesSearchText` are new.

`MIGRATION_1_2` calls `searchEntityFor` without `notes`, so it still compiles. Its `INSERT INTO paper_search` names five columns and runs against the version 2 table it creates, so it doesn't change.

- [ ] **Step 6: Add the DAO members**

In `PaperDao.kt`, add these imports:

```kotlin
import androidx.room.Upsert
import com.etatech.hashiya.core.database.model.DeletedPaper
import com.etatech.hashiya.core.database.model.PaperNotesEntity
import com.etatech.hashiya.core.database.model.asEntity
import com.etatech.hashiya.core.database.model.notesSearchText
import com.etatech.hashiya.core.model.PaperNotes
```

Add after `getByOpenAlexId`:

```kotlin
    /** The Details screen's paper; emits null once it is deleted. */
    @Transaction
    @Query("SELECT * FROM papers WHERE open_alex_id = :openAlexId")
    abstract fun observeByOpenAlexId(openAlexId: String): Flow<PaperWithAuthors?>

    /** The paper's notes, or null when it has none (or isn't saved). */
    @Query(
        """
        SELECT paper_notes.* FROM paper_notes
        JOIN papers ON papers.id = paper_notes.paper_id
        WHERE papers.open_alex_id = :openAlexId
        """
    )
    abstract fun observeNotes(openAlexId: String): Flow<PaperNotesEntity?>
```

Add to the protected building blocks, after `deleteSearchById`:

```kotlin
    @Query("SELECT id FROM papers WHERE open_alex_id = :openAlexId")
    protected abstract suspend fun getPaperId(openAlexId: String): String?

    @Query("SELECT * FROM paper_notes WHERE paper_id = :paperId")
    protected abstract suspend fun getNotes(paperId: String): PaperNotesEntity?

    @Upsert
    protected abstract suspend fun upsertNotes(notes: PaperNotesEntity)

    @Query("DELETE FROM paper_notes WHERE paper_id = :paperId")
    protected abstract suspend fun deleteNotes(paperId: String)

    @Query("UPDATE paper_search SET notes = :text WHERE paper_id = :paperId")
    protected abstract suspend fun setSearchNotes(paperId: String, text: String)
```

Replace `insertPaperWithAuthors` and `deleteByOpenAlexId` with the following, and add `saveNotes` after them:

```kotlin
    /**
     * Writes the paper, its authors, its search row and (on a restore) its [notes] atomically.
     * Returns false, writing nothing, if it is already saved. [search] must already hold the notes' search text.
     */
    @Transaction
    open suspend fun insertPaperWithAuthors(
        paper: PaperEntity,
        authors: List<PaperAuthorEntity>,
        search: PaperSearchEntity,
        notes: PaperNotesEntity? = null
    ): Boolean {
        require(search.paperId == paper.id) { "The search row must belong to the paper" }
        require(notes == null || notes.paperId == paper.id) { "The notes must belong to the paper" }
        if (insertPaper(paper) == -1L) return false
        insertAuthors(authors)
        insertSearch(search)
        notes?.let { upsertNotes(it) }
        return true
    }

    /** Deletes the paper (authors and notes cascade) and its search row, and returns what was deleted, so it can be restored. */
    @Transaction
    open suspend fun deleteByOpenAlexId(openAlexId: String): DeletedPaper? {
        val existing = getByOpenAlexId(openAlexId) ?: return null
        val notes = getNotes(existing.paper.id)
        deleteById(existing.paper.id)
        deleteSearchById(existing.paper.id)
        return DeletedPaper(existing, notes)
    }

    /**
     * Stores [notes] (blank notes delete the row) and puts their text in the search index, atomically.
     * Returns false, writing nothing, if the paper isn't saved.
     */
    @Transaction
    open suspend fun saveNotes(openAlexId: String, notes: PaperNotes, updatedAt: Long): Boolean {
        val paperId = getPaperId(openAlexId) ?: return false
        if (notes.isEmpty) deleteNotes(paperId) else upsertNotes(notes.asEntity(paperId, updatedAt))
        setSearchNotes(paperId, notesSearchText(notes))
        return true
    }
```

Update the comment above the building blocks to say "…never written without its authors, search row and notes."

- [ ] **Step 7: Bump the database to version 3**

In `HashiyaDatabase.kt`, add `import com.etatech.hashiya.core.database.model.PaperNotesEntity`, then change the annotation to:

```kotlin
@Database(
    entities = [PaperEntity::class, PaperAuthorEntity::class, PaperSearchEntity::class, PaperNotesEntity::class],
    version = 3,
    exportSchema = true
)
```

- [ ] **Step 8: Generate the version 3 schema and read its `CREATE` statements**

Run: `./gradlew :core:database:kspDebugKotlin :core:database:copyRoomSchemas`
Expected: BUILD SUCCESSFUL. `core/database/schemas/com.etatech.hashiya.core.database.HashiyaDatabase/3.json` now exists.

Print the two statements the migration must match:

```bash
python3 -c "
import json
d = json.load(open('core/database/schemas/com.etatech.hashiya.core.database.HashiyaDatabase/3.json'))
for e in d['database']['entities']:
    if e['tableName'] in ('paper_notes', 'paper_search'): print(e['tableName'], e['createSql'])"
```

Expected (with `${TABLE_NAME}` standing for the table name):

```
paper_search CREATE VIRTUAL TABLE IF NOT EXISTS `${TABLE_NAME}` USING FTS4(`paper_id` TEXT NOT NULL, `title` TEXT NOT NULL, `authors` TEXT NOT NULL, `abstract` TEXT NOT NULL, `venue` TEXT NOT NULL, `notes` TEXT NOT NULL, tokenize=unicode61, notindexed=`paper_id`)
paper_notes CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`paper_id` TEXT NOT NULL, `summary` TEXT NOT NULL, `research_question` TEXT NOT NULL, `method` TEXT NOT NULL, `key_findings` TEXT NOT NULL, `limitations` TEXT NOT NULL, `thoughts` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`paper_id`), FOREIGN KEY(`paper_id`) REFERENCES `papers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
```

If the printed text differs in any way, use the printed text in Step 9, not this plan's.

- [ ] **Step 9: Write `MIGRATION_2_3` and register it**

Append to `Migrations.kt`:

```kotlin
/**
 * Adds the notes table and a notes column to the search index. An FTS table can't gain a column, so the existing
 * search rows are copied out through a plain temporary table and back into the rebuilt one, unchanged and with no notes.
 */
internal val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Both CREATE statements are exactly what Room generates (schemas/…/3.json), so the schema validates.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `paper_notes` (`paper_id` TEXT NOT NULL, `summary` TEXT NOT NULL, " +
                "`research_question` TEXT NOT NULL, `method` TEXT NOT NULL, `key_findings` TEXT NOT NULL, " +
                "`limitations` TEXT NOT NULL, `thoughts` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`paper_id`), " +
                "FOREIGN KEY(`paper_id`) REFERENCES `papers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE TEMP TABLE paper_search_copy AS SELECT paper_id, title, authors, abstract, venue FROM paper_search")
        db.execSQL("DROP TABLE paper_search")
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `paper_search` USING FTS4(`paper_id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`authors` TEXT NOT NULL, `abstract` TEXT NOT NULL, `venue` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                "tokenize=unicode61, notindexed=`paper_id`)"
        )
        db.execSQL(
            "INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes) " +
                "SELECT paper_id, title, authors, abstract, venue, '' FROM paper_search_copy"
        )
        db.execSQL("DROP TABLE paper_search_copy")
    }
}
```

In `DatabaseModule.kt`, import `com.etatech.hashiya.core.database.migration.MIGRATION_2_3` and change `.addMigrations(MIGRATION_1_2)` to `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`.

- [ ] **Step 10: Run the tests to verify they pass**

Run: `./gradlew :core:database:testDebugUnitTest`
Expected: PASS. That covers every `PaperDaoTest` and `MigrationTest` test, including the 3 existing migration tests and the 3 new ones.

- [ ] **Step 11: Commit (with the schema)**

```bash
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**' ':(exclude,glob)**/src/test/screenshots/**'
git status --short   # must list core/database/schemas/…/3.json
git commit -m "feat: store paper notes and index them for library search"
```

`core/data` doesn't compile after this commit until Task 3, because `deleteByOpenAlexId` changed its return type. Task 3 follows directly. Don't push between Task 2 and Task 3.

---

### Task 3: `core/data` and `core/testing` — notes in `LibraryRepository` and its fake

**Files:**
- Modify: `core/data/src/main/java/com/etatech/hashiya/core/data/repository/LibraryRepository.kt`
- Modify: `core/data/src/main/java/com/etatech/hashiya/core/data/repository/RoomLibraryRepository.kt`
- Modify: `core/data/src/main/java/com/etatech/hashiya/core/data/mapping/PaperEntityMapping.kt`
- Modify: `core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeLibraryRepository.kt`
- Test: `core/data/src/test/java/com/etatech/hashiya/core/data/repository/RoomLibraryRepositoryTest.kt`, `core/testing/src/test/java/com/etatech/hashiya/core/testing/FakeLibraryRepositoryTest.kt`

**Interfaces:**
- Consumes: `PaperNotes` (Task 1); `PaperDao.observeByOpenAlexId`, `observeNotes`, `saveNotes`, `DeletedPaper`, `insertPaperWithAuthors(…, notes)`, `asEntity`, `asPaperNotes` (Task 2).
- Produces:
  - `LibraryRepository.observePaper(openAlexId: String): Flow<LibraryPaper?>`;
  - `observeNotes(openAlexId: String): Flow<PaperNotes>`;
  - `suspend fun saveNotes(openAlexId: String, notes: PaperNotes)`;
  - `RemovedPaper(paper, localId, savedAt, status, notes: PaperNotes = PaperNotes())`;
  - `FakeLibraryRepository.failOnSaveNotes: Boolean` and `FakeLibraryRepository.notesSaves: MutableList<Pair<String, PaperNotes>>`, which records every `saveNotes` call that didn't throw.

- [ ] **Step 1: Write the failing repository tests**

In `RoomLibraryRepositoryTest.kt`, add `import com.etatech.hashiya.core.model.PaperNotes`, and then these tests at the end of the class:

```kotlin
    @Test
    fun observePaperFollowsTheSavedPaperAndItsStatus() = runTest {
        repository.save(paper("W1"))
        repository.setStatus("W1", ReadingStatus.Reading)

        assertEquals(LibraryPaper(paper("W1"), ReadingStatus.Reading), repository.observePaper("W1").first())
        repository.remove("W1")
        assertNull(repository.observePaper("W1").first())
    }

    @Test
    fun notesAreEmptyUntilSavedAndReadBackAfter() = runTest {
        repository.save(paper("W1"))
        assertEquals(PaperNotes(), repository.observeNotes("W1").first())

        repository.saveNotes("W1", PaperNotes(summary = "Transformers", limitations = "English only"))

        assertEquals(PaperNotes(summary = "Transformers", limitations = "English only"), repository.observeNotes("W1").first())
    }

    @Test
    fun savingNotesForAnUnsavedPaperDoesNothing() = runTest {
        repository.saveNotes("W1", PaperNotes(summary = "x"))

        assertEquals(PaperNotes(), repository.observeNotes("W1").first())
        assertEquals(emptyList<String>(), ids())
    }

    @Test
    fun libraryIsSearchableByItsNotes() = runTest {
        repository.save(paper("W1"))
        repository.save(paper("W2"))

        repository.saveNotes("W2", PaperNotes(method = "Randomized controlled trial"))

        assertEquals(listOf("W2"), ids("randomiz"))
        assertEquals(listOf("W2"), ids("Controlled TRIAL"))
        assertEquals(mapOf(ReadingStatus.ToRead to 1, ReadingStatus.Reading to 0, ReadingStatus.Read to 0), repository.observeStatusCounts("trial").first())
    }

    @Test
    fun removeThenRestoreKeepsTheNotesAndTheirSearch() = runTest {
        repository.save(paper("W1"))
        repository.saveNotes("W1", PaperNotes(thoughts = "Cite in chapter two"))

        val removed = repository.remove("W1")!!
        assertEquals(PaperNotes(thoughts = "Cite in chapter two"), removed.notes)
        assertEquals(emptyList<String>(), ids("chapter"))

        repository.restore(removed)
        assertEquals(PaperNotes(thoughts = "Cite in chapter two"), repository.observeNotes("W1").first())
        assertEquals(listOf("W1"), ids("chapter"))
    }

    @Test
    fun removeThenRestoreWithoutNotesStaysWithoutNotes() = runTest {
        repository.save(paper("W1"))

        val removed = repository.remove("W1")!!
        repository.restore(removed)

        assertEquals(PaperNotes(), removed.notes)
        assertEquals(PaperNotes(), repository.observeNotes("W1").first())
        assertEquals(listOf("W1"), ids("paper"))
    }
```

- [ ] **Step 2: Write the failing fake tests**

In `FakeLibraryRepositoryTest.kt`, add these imports:
- `com.etatech.hashiya.core.model.PaperNotes`
- `org.junit.Assert.assertNull`
- `org.junit.Assert.assertThrows` (if it isn't there)
- `java.io.IOException`
- `kotlinx.coroutines.runBlocking`

Then add these tests:

```kotlin
    @Test
    fun keepsNotesLikeTheRoomRepository() = runTest {
        repository.save(SamplePapers.bert)
        assertEquals(PaperNotes(), repository.observeNotes(SamplePapers.bert.openAlexId).first())

        repository.saveNotes(SamplePapers.bert.openAlexId, PaperNotes(method = "Masked language model"))
        assertEquals(PaperNotes(method = "Masked language model"), repository.observeNotes(SamplePapers.bert.openAlexId).first())
        assertEquals(listOf(SamplePapers.bert.title), titles("masked"))

        val removed = repository.remove(SamplePapers.bert.openAlexId)!!
        assertEquals(PaperNotes(method = "Masked language model"), removed.notes)
        assertNull(repository.observePaper(SamplePapers.bert.openAlexId).first())
        repository.restore(removed)
        assertEquals(PaperNotes(method = "Masked language model"), repository.observeNotes(SamplePapers.bert.openAlexId).first())
        assertEquals(LibraryPaper(SamplePapers.bert, ReadingStatus.ToRead), repository.observePaper(SamplePapers.bert.openAlexId).first())
    }

    @Test
    fun notesForAnUnsavedPaperAreIgnoredButRecorded() = runTest {
        repository.saveNotes("missing", PaperNotes(summary = "x"))

        assertEquals(PaperNotes(), repository.observeNotes("missing").first())
        assertEquals(listOf("missing" to PaperNotes(summary = "x")), repository.notesSaves)
    }

    @Test
    fun failOnSaveNotesThrowsAndRecordsNothing() {
        repository.failOnSaveNotes = true

        assertThrows(IOException::class.java) { runBlocking { repository.saveNotes("W1", PaperNotes(summary = "x")) } }
        assertEquals(emptyList<Pair<String, PaperNotes>>(), repository.notesSaves)
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*RoomLibraryRepositoryTest' :core:testing:testDebugUnitTest --tests '*FakeLibraryRepositoryTest'`
Expected: compilation FAILS, because `RoomLibraryRepository.remove` uses the old `deleteByOpenAlexId` result and `observePaper` / `saveNotes` don't exist.

- [ ] **Step 4: Extend the interface**

Replace `LibraryRepository.kt`'s body. Keep its existing imports and add `com.etatech.hashiya.core.model.PaperNotes`.

```kotlin
interface LibraryRepository {
    /** Papers matching [query] (blank = all) and [status] (null = all), newest saved first. Notes are searched too. */
    fun observeLibrary(query: String, status: ReadingStatus?): Flow<List<LibraryPaper>>

    /** How many papers of each status match [query]; statuses with none are 0. */
    fun observeStatusCounts(query: String): Flow<Map<ReadingStatus, Int>>

    fun observeSavedIds(): Flow<Set<String>>

    /** The saved paper with its status; null when it isn't saved (or stops being saved). */
    fun observePaper(openAlexId: String): Flow<LibraryPaper?>

    /** The paper's notes; empty PaperNotes when it has none or isn't saved. */
    fun observeNotes(openAlexId: String): Flow<PaperNotes>

    /** New papers start as To read. Saving a paper that is already saved does nothing. */
    suspend fun save(paper: Paper)

    /** Changes the status without reordering the library. Does nothing if the paper isn't saved. */
    suspend fun setStatus(openAlexId: String, status: ReadingStatus)

    /** Saves the notes (blank notes delete them) and updates the search index. Does nothing if the paper isn't saved. */
    suspend fun saveNotes(openAlexId: String, notes: PaperNotes)

    /** Returns what was removed, for Undo, or null if the paper was not saved. */
    suspend fun remove(openAlexId: String): RemovedPaper?

    /** Puts a removed paper back where it was, with its status and notes. Does nothing if it has been saved again meanwhile. */
    suspend fun restore(removed: RemovedPaper)
}

data class RemovedPaper(
    val paper: Paper,
    val localId: String,
    val savedAt: Long,
    val status: ReadingStatus,
    val notes: PaperNotes = PaperNotes()
)
```

- [ ] **Step 5: Carry the notes into the search row**

In `PaperEntityMapping.kt`, add `import com.etatech.hashiya.core.model.PaperNotes` and change `asEntities`:

```kotlin
internal fun Paper.asEntities(localId: String, savedAt: Long, status: ReadingStatus, notes: PaperNotes? = null): PaperEntities =
    PaperEntities(
        paper = PaperEntity(
            // …unchanged…
        ),
        authors = authors.mapIndexed { index, author ->
            PaperAuthorEntity(paperId = localId, position = index, name = author.name, openAlexAuthorId = author.openAlexId)
        },
        search = searchEntityFor(localId, title, authors.map { it.name }, abstract, venue, notes)
    )
```

Keep the `PaperEntity(…)` arguments exactly as they are. Only the new parameter and the last argument of `searchEntityFor` change.

- [ ] **Step 6: Implement the repository**

In `RoomLibraryRepository.kt`, add these imports:
- `com.etatech.hashiya.core.database.model.asEntity`
- `com.etatech.hashiya.core.database.model.asPaperNotes`
- `com.etatech.hashiya.core.model.PaperNotes`

Add after `observeSavedIds`:

```kotlin
    override fun observePaper(openAlexId: String): Flow<LibraryPaper?> = paperDao.observeByOpenAlexId(openAlexId).map { row ->
        row?.let { LibraryPaper(it.asPaper(), readingStatusOf(it.paper.readingStatus)) }
    }

    override fun observeNotes(openAlexId: String): Flow<PaperNotes> =
        paperDao.observeNotes(openAlexId).map { it?.asPaperNotes() ?: PaperNotes() }
```

Add after `setStatus`:

```kotlin
    override suspend fun saveNotes(openAlexId: String, notes: PaperNotes) {
        paperDao.saveNotes(openAlexId, notes, updatedAt = now())
    }
```

Replace `remove` and `restore`:

```kotlin
    override suspend fun remove(openAlexId: String): RemovedPaper? = paperDao.deleteByOpenAlexId(openAlexId)?.let { deleted ->
        val row = deleted.paper
        RemovedPaper(
            paper = row.asPaper(),
            localId = row.paper.id,
            savedAt = row.paper.savedAt,
            status = readingStatusOf(row.paper.readingStatus),
            notes = deleted.notes?.asPaperNotes() ?: PaperNotes()
        )
    }

    override suspend fun restore(removed: RemovedPaper) {
        val entities = removed.paper.asEntities(
            localId = removed.localId,
            savedAt = removed.savedAt,
            status = removed.status,
            notes = removed.notes
        )
        // Nothing reads updated_at yet, so a restore doesn't need the original value.
        val notes = removed.notes.takeUnless { it.isEmpty }?.asEntity(removed.localId, updatedAt = now())
        paperDao.insertPaperWithAuthors(entities.paper, entities.authors, entities.search, notes)
    }
```

- [ ] **Step 7: Extend the fake**

Replace `FakeLibraryRepository.kt` with:

```kotlin
package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperNotes
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

    /** When true, [saveNotes] throws like a failing disk would. */
    var failOnSaveNotes = false

    /** Every [saveNotes] call that didn't throw, in order, including those for papers that aren't saved. */
    val notesSaves = mutableListOf<Pair<String, PaperNotes>>()

    override fun observeLibrary(query: String, status: ReadingStatus?): Flow<List<LibraryPaper>> = rows.map { list ->
        list.filter { (status == null || it.status == status) && it.matches(query) }
            .sortedByDescending { it.savedAt }
            .map { LibraryPaper(it.paper, it.status) }
    }

    override fun observeStatusCounts(query: String): Flow<Map<ReadingStatus, Int>> = rows.map { list ->
        val matching = list.filter { it.matches(query) }
        ReadingStatus.entries.associateWith { status -> matching.count { it.status == status } }
    }

    override fun observeSavedIds(): Flow<Set<String>> = rows.map { list -> list.map { it.paper.openAlexId }.toSet() }

    override fun observePaper(openAlexId: String): Flow<LibraryPaper?> = rows.map { list ->
        list.firstOrNull { it.paper.openAlexId == openAlexId }?.let { LibraryPaper(it.paper, it.status) }
    }

    override fun observeNotes(openAlexId: String): Flow<PaperNotes> = rows.map { list ->
        list.firstOrNull { it.paper.openAlexId == openAlexId }?.notes ?: PaperNotes()
    }

    override suspend fun save(paper: Paper) {
        if (failOnSave) throw IOException("disk full")
        if (isSaved(paper.openAlexId)) return
        rows.update { it + RemovedPaper(paper, localId = "local-${paper.openAlexId}", savedAt = ++clock, status = ReadingStatus.ToRead) }
    }

    override suspend fun setStatus(openAlexId: String, status: ReadingStatus) {
        if (failOnSetStatus) throw IOException("disk full")
        rows.update { list -> list.map { if (it.paper.openAlexId == openAlexId) it.copy(status = status) else it } }
    }

    override suspend fun saveNotes(openAlexId: String, notes: PaperNotes) {
        if (failOnSaveNotes) throw IOException("disk full")
        notesSaves += openAlexId to notes
        rows.update { list -> list.map { if (it.paper.openAlexId == openAlexId) it.copy(notes = notes) else it } }
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

/** Like the real index: every word of [query] must start a word of the title, authors, abstract, venue or notes. */
private fun RemovedPaper.matches(query: String): Boolean {
    val noteText = NoteSection.entries.joinToString(" ") { notes[it] }
    val indexed = words(
        listOf(paper.title, paper.authors.joinToString(" ") { it.name }, paper.abstract.orEmpty(), paper.venue.orEmpty(), noteText)
            .joinToString(" ")
    )
    return words(query).all { word -> indexed.any { it.startsWith(word) } }
}
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest :core:testing:testDebugUnitTest`
Expected: PASS for every test in both modules.

Then run: `./gradlew :feature:library:testDebugUnitTest :feature:search:testDebugUnitTest`
Expected: PASS. Their fakes delegate with `LibraryRepository by delegate`, so they pick up the new members, and `RemovedPaper`'s new parameter has a default.

- [ ] **Step 9: Commit**

```bash
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**' ':(exclude,glob)**/src/test/screenshots/**'
git commit -m "feat: read, save and restore paper notes through the library repository"
```

---
### Task 4: `core/designsystem` — a shared paper header, **Open details** in the preview, and the overflow icon

**Files:**
- Create: `core/designsystem/src/main/java/com/etatech/hashiya/core/designsystem/component/PaperHeader.kt`
- Modify: `core/designsystem/src/main/java/com/etatech/hashiya/core/designsystem/component/PaperPreview.kt`
- Modify: `core/designsystem/src/main/java/com/etatech/hashiya/core/designsystem/icon/HashiyaIcons.kt`
- Modify: `core/designsystem/src/main/res/values/strings.xml`, `core/designsystem/src/main/res/values-ar/strings.xml`
- Test: `core/designsystem/src/test/java/com/etatech/hashiya/core/designsystem/component/PaperPreviewTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `@Composable fun PaperHeader(paper: Paper, modifier: Modifier = Modifier)`: title, every author, venue · year · citations, and the open access badge;
  - `@Composable fun PaperAbstract(paper: Paper, modifier: Modifier = Modifier)`: the "Abstract" label and the text or "No abstract available";
  - `PaperPreviewContent(…, onOpenDetails: (() -> Unit)? = null)` and `PaperPreviewSheet(…, onOpenDetails: (() -> Unit)? = null)`;
  - `HashiyaIcons.MoreOptions`;
  - `R.string.designsystem_open_details`.

The preview's header and abstract move into `PaperHeader` / `PaperAbstract` unchanged, so Details shows exactly what the sheet shows. The existing preview screenshot baselines must not change (checked on CI in Task 9).

- [ ] **Step 1: Write the failing tests**

Add to `PaperPreviewTest.kt`:

```kotlin
    @Test
    fun openDetailsShowsWhenOfferedAndCallsBack() {
        var opened = 0
        composeRule.setContent {
            HashiyaTheme {
                PaperPreviewContent(SamplePapers.bert, inLibrary = true, onToggleSave = {}, onOpenDoi = {}, onOpenDetails = { opened++ })
            }
        }

        composeRule.onNodeWithText("Open details").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun noOpenDetailsWithoutTheCallback() {
        show(SamplePapers.bert, inLibrary = true)
        composeRule.onNodeWithText("Open details").assertDoesNotExist()
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests '*PaperPreviewTest'`
Expected: compilation FAILS with "No parameter with name 'onOpenDetails' found".

- [ ] **Step 3: Add the string and the icon**

In `core/designsystem/src/main/res/values/strings.xml`, add after `designsystem_remove_from_library`:

```xml
    <string name="designsystem_open_details">Open details</string>
```

In `values-ar/strings.xml`, add at the same place:

```xml
    <string name="designsystem_open_details">فتح التفاصيل</string>
```

In `HashiyaIcons.kt`, add `import androidx.compose.material.icons.outlined.MoreVert` and, after `Update`:

```kotlin
    val MoreOptions: ImageVector = Icons.Outlined.MoreVert
```

- [ ] **Step 4: Extract the header and the abstract**

Create `PaperHeader.kt`:

```kotlin
package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.R
import com.etatech.hashiya.core.model.Paper

/**
 * The title, every author, venue · year · citations and the open access badge: the top of the preview sheet and of Details.
 * Paper text is full width so it aligns by its own direction (Latin left, Arabic right) in either locale.
 */
@Composable
fun PaperHeader(paper: Paper, modifier: Modifier = Modifier) {
    val contentText = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)
    Column(modifier) {
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
    }
}

/** The "Abstract" label and the full abstract, or "No abstract available". */
@Composable
fun PaperAbstract(paper: Paper, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            stringResource(R.string.designsystem_abstract),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            paper.abstract ?: stringResource(R.string.designsystem_no_abstract),
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
            color = if (paper.abstract == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

- [ ] **Step 5: Use them in the preview and add Open details**

In `PaperPreview.kt`:

1. `PaperPreviewSheet`: add the parameter `onOpenDetails: (() -> Unit)? = null` after `onStatusChange`, and pass `onOpenDetails = onOpenDetails` to `PaperPreviewContent`.
2. `PaperPreviewContent`: add the parameter `onOpenDetails: (() -> Unit)? = null` after `onStatusChange`. Extend its KDoc with "With [onOpenDetails] (a saved paper in Search), an Open details button sits above the other buttons."
3. Delete the `contentText` val. Then replace the whole scrolling column (from `Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {` to its closing brace) with:

```kotlin
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            PaperHeader(paper)
            Spacer(Modifier.height(16.dp))
            PaperAbstract(paper)
        }
```

4. Replace the lines from `if (status != null) {` up to `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {` with:

```kotlin
        if (status != null) {
            Spacer(Modifier.height(16.dp))
            ReadingStatusSelector(status, onStatusChange)
        }
        if (onOpenDetails != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onOpenDetails, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.designsystem_open_details))
            }
        }
        Spacer(Modifier.height(if (onOpenDetails != null) 8.dp else 16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
```

5. Add `import androidx.compose.material3.FilledTonalButton`. `spotlessApply` removes imports that are now unused (`TextDirection`, `StatusBadge`-related ones if unused, etc.).

- [ ] **Step 6: Run the module's tests**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: PASS for every test, including the two new ones and the existing `PaperPreviewTest` / `PaperCardTest` tests.

Screenshot baselines aren't verified locally. CI verifies them in Task 9, and `paper_preview*` baselines must come back unchanged there.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**' ':(exclude,glob)**/src/test/screenshots/**'
git commit -m "feat: share the paper header and offer Open details in the preview"
```

---

### Task 5: `feature/paperdetails` — module and ViewModel with autosave

**Files:**
- Modify: `settings.gradle.kts`
- Create: `feature/paperdetails/build.gradle.kts`
- Create: `feature/paperdetails/src/test/resources/robolectric.properties`
- Create: `feature/paperdetails/src/main/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsUiState.kt`
- Create: `feature/paperdetails/src/main/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsViewModel.kt`
- Test: `feature/paperdetails/src/test/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsViewModelTest.kt`

**Interfaces:**
- Consumes:
  - `LibraryRepository.observePaper`, `observeNotes`, `saveNotes`, `setStatus`, and `FakeLibraryRepository.failOnSaveNotes` / `notesSaves` (Task 3);
  - `NoteSection`, `PaperNotes` (Task 1);
  - `@ApplicationScope` from `com.etatech.hashiya.core.data.di`.
- Produces:
  - `sealed interface PaperDetailsUiState { Loading; Loaded(paper: LibraryPaper, notes: PaperNotes, saveState: NotesSaveState) }`;
  - `enum class NotesSaveState { Idle, Saving, Saved, Failed }`;
  - `enum class PaperDetailsMessage { NotesSaveFailed, StatusUpdateFailed }`;
  - `enum class PaperDetailsExit { Closed, Removed }`;
  - `internal const val ARG_OPEN_ALEX_ID = "openAlexId"` and `internal const val NOTES_SAVE_DEBOUNCE_MS = 500L`;
  - `PaperDetailsViewModel`: `openAlexId`, `uiState`, `message`, `exit`, `onNoteChange(section, text)`, `onStatusChange(status)`, `onRetrySave()`, `onMessageShown()`, `flushNotes()`, `onRemove()`.

- [ ] **Step 1: Create the module**

In `settings.gradle.kts`, add after `include(":feature:search")`:

```kotlin
include(":feature:paperdetails")
```

`feature/paperdetails/build.gradle.kts`:

```kotlin
plugins {
    id("hashiya.android.feature")
}

android {
    namespace = "com.etatech.hashiya.feature.paperdetails"
}
```

`feature/paperdetails/src/test/resources/robolectric.properties`:

```properties
sdk=35
```

Like the other feature modules, it has no `AndroidManifest.xml`.

- [ ] **Step 2: Write the failing ViewModel test**

`feature/paperdetails/src/test/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsViewModelTest.kt`:

```kotlin
package com.etatech.hashiya.feature.paperdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import com.etatech.hashiya.core.testing.SamplePapers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

private const val SAVE_DURATION_MS = 1_000L

@OptIn(ExperimentalCoroutinesApi::class)
class PaperDetailsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLibraryRepository()
    private val paper = SamplePapers.bert
    private val id = paper.openAlexId

    /** The application scope is the test's backgroundScope: it outlives viewModelScope, like the real one. */
    private fun TestScope.viewModel(libraryRepository: LibraryRepository = repository): PaperDetailsViewModel {
        val viewModel = PaperDetailsViewModel(SavedStateHandle(mapOf(ARG_OPEN_ALEX_ID to id)), libraryRepository, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        return viewModel
    }

    private fun PaperDetailsViewModel.loaded(): PaperDetailsUiState.Loaded =
        uiState.value as? PaperDetailsUiState.Loaded ?: error("Expected Loaded but was ${uiState.value}")

    /** Lets exactly the debounce pause pass, then runs what is due. */
    private fun TestScope.waitOutTheDebounce() {
        advanceTimeBy(NOTES_SAVE_DEBOUNCE_MS)
        runCurrent()
    }

    private val noSaves = emptyList<Pair<String, PaperNotes>>()

    @Test
    fun loadsThePaperWithItsStatusAndStoredNotesWithoutSaving() = runTest {
        repository.save(paper)
        repository.setStatus(id, ReadingStatus.Reading)
        repository.saveNotes(id, PaperNotes(summary = "Bidirectional pre-training"))
        repository.notesSaves.clear()

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            PaperDetailsUiState.Loaded(
                LibraryPaper(paper, ReadingStatus.Reading),
                PaperNotes(summary = "Bidirectional pre-training"),
                NotesSaveState.Idle
            ),
            viewModel.uiState.value
        )
        assertEquals(noSaves, repository.notesSaves)
    }

    @Test
    fun typingSavesOnlyAfterThePause() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNoteChange(NoteSection.Method, "Masked LM")
        advanceTimeBy(NOTES_SAVE_DEBOUNCE_MS - 1)
        runCurrent()
        assertEquals(noSaves, repository.notesSaves)
        assertEquals(PaperNotes(method = "Masked LM"), viewModel.loaded().notes)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(id to PaperNotes(method = "Masked LM")), repository.notesSaves)
        assertEquals(NotesSaveState.Saved, viewModel.loaded().saveState)
    }

    @Test
    fun aBurstOfTypingIsOneWrite() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNoteChange(NoteSection.Method, "M")
        advanceTimeBy(200)
        viewModel.onNoteChange(NoteSection.Method, "Ma")
        advanceTimeBy(200)
        viewModel.onNoteChange(NoteSection.Method, "Mas")
        advanceUntilIdle()

        assertEquals(listOf(id to PaperNotes(method = "Mas")), repository.notesSaves)
    }

    @Test
    fun saveStateShowsSavingWhileTheWriteRuns() = runTest {
        repository.save(paper)
        val viewModel = viewModel(SlowNotesRepository(repository))
        advanceUntilIdle()
        assertEquals(NotesSaveState.Idle, viewModel.loaded().saveState)

        viewModel.onNoteChange(NoteSection.Summary, "Pre-training")
        waitOutTheDebounce()
        assertEquals(NotesSaveState.Saving, viewModel.loaded().saveState)

        advanceTimeBy(SAVE_DURATION_MS)
        runCurrent()
        assertEquals(NotesSaveState.Saved, viewModel.loaded().saveState)
    }

    @Test
    fun failedSaveShowsCouldntSaveKeepsTheTextAndRetrySaves() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()
        repository.failOnSaveNotes = true

        viewModel.onNoteChange(NoteSection.Limitations, "Small sample")
        advanceUntilIdle()

        assertEquals(NotesSaveState.Failed, viewModel.loaded().saveState)
        assertEquals(PaperDetailsMessage.NotesSaveFailed, viewModel.message.value)
        assertEquals(PaperNotes(limitations = "Small sample"), viewModel.loaded().notes)
        viewModel.onMessageShown()
        assertNull(viewModel.message.value)

        repository.failOnSaveNotes = false
        viewModel.onRetrySave()
        advanceUntilIdle()
        assertEquals(NotesSaveState.Saved, viewModel.loaded().saveState)
        assertEquals(PaperNotes(limitations = "Small sample"), repository.observeNotes(id).first())
    }

    @Test
    fun theNextEditAfterAFailureSavesToo() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()
        repository.failOnSaveNotes = true
        viewModel.onNoteChange(NoteSection.Method, "a")
        advanceUntilIdle()
        repository.failOnSaveNotes = false

        viewModel.onNoteChange(NoteSection.Method, "ab")
        advanceUntilIdle()

        assertEquals(NotesSaveState.Saved, viewModel.loaded().saveState)
        assertEquals(PaperNotes(method = "ab"), repository.observeNotes(id).first())
    }

    @Test
    fun clearingEveryNoteSavesBlankNotes() = runTest {
        repository.save(paper)
        repository.saveNotes(id, PaperNotes(method = "Survey"))
        repository.notesSaves.clear()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNoteChange(NoteSection.Method, "")
        advanceUntilIdle()

        assertEquals(listOf(id to PaperNotes()), repository.notesSaves)
        assertEquals(PaperNotes(), repository.observeNotes(id).first())
    }

    @Test
    fun laterStoredNotesDoNotReplaceTyping() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNoteChange(NoteSection.Summary, "Mine")
        repository.saveNotes(id, PaperNotes(summary = "From elsewhere"))
        runCurrent()

        assertEquals(PaperNotes(summary = "Mine"), viewModel.loaded().notes)
    }

    @Test
    fun flushSavesPendingNotesAtOnce() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNoteChange(NoteSection.Thoughts, "Chapter 2")
        viewModel.flushNotes()
        runCurrent()
        assertEquals(listOf(id to PaperNotes(thoughts = "Chapter 2")), repository.notesSaves)

        // The debounced save that follows finds nothing new to write.
        advanceUntilIdle()
        assertEquals(1, repository.notesSaves.size)
    }

    @Test
    fun flushWithNothingNewWritesNothing() = runTest {
        repository.save(paper)
        repository.saveNotes(id, PaperNotes(summary = "Stored"))
        repository.notesSaves.clear()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.flushNotes()
        advanceUntilIdle()

        assertEquals(noSaves, repository.notesSaves)
    }

    @Test
    fun clearingTheViewModelSavesPendingNotes() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onNoteChange(NoteSection.KeyFindings, "Beats ELMo")

        // Leaving the screen clears the ViewModel and cancels viewModelScope, so the debounced save can never run:
        // only the flush on the application scope can save. ViewModelStore.put is library-internal, which is fine in a test.
        ViewModelStore().apply { put("details", viewModel) }.clear()
        advanceUntilIdle()

        assertEquals(listOf(id to PaperNotes(keyFindings = "Beats ELMo")), repository.notesSaves)
    }

    @Test
    fun changingTheStatusUpdatesThePaper() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onStatusChange(ReadingStatus.Read)
        advanceUntilIdle()

        assertEquals(ReadingStatus.Read, viewModel.loaded().paper.status)
    }

    @Test
    fun failedStatusChangeShowsTheMessageAndKeepsTheStoredStatus() = runTest {
        repository.save(paper)
        repository.failOnSetStatus = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onStatusChange(ReadingStatus.Read)
        advanceUntilIdle()

        assertEquals(PaperDetailsMessage.StatusUpdateFailed, viewModel.message.value)
        assertEquals(ReadingStatus.ToRead, viewModel.loaded().paper.status)
    }

    @Test
    fun aPaperRemovedElsewhereClosesTheScreen() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertNull(viewModel.exit.value)

        repository.remove(id)
        advanceUntilIdle()

        assertEquals(PaperDetailsExit.Closed, viewModel.exit.value)
    }

    @Test
    fun anUnsavedPaperClosesTheScreenAndStaysLoading() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(PaperDetailsExit.Closed, viewModel.exit.value)
        assertEquals(PaperDetailsUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun removeSavesPendingNotesBeforeLeaving() = runTest {
        repository.save(paper)
        val viewModel = viewModel(SlowNotesRepository(repository))
        advanceUntilIdle()
        viewModel.onNoteChange(NoteSection.Thoughts, "Keep this")

        viewModel.onRemove()
        runCurrent()
        assertNull(viewModel.exit.value)

        advanceTimeBy(SAVE_DURATION_MS)
        runCurrent()
        assertEquals(PaperDetailsExit.Removed, viewModel.exit.value)
        assertEquals(PaperNotes(thoughts = "Keep this"), repository.observeNotes(id).first())
    }
}

/** Takes [SAVE_DURATION_MS] of virtual time to write notes, so a test can see a write in progress. */
private class SlowNotesRepository(private val delegate: FakeLibraryRepository) : LibraryRepository by delegate {
    override suspend fun saveNotes(openAlexId: String, notes: PaperNotes) {
        delay(SAVE_DURATION_MS)
        delegate.saveNotes(openAlexId, notes)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :feature:paperdetails:testDebugUnitTest --tests '*PaperDetailsViewModelTest'`
Expected: compilation FAILS with "Unresolved reference: PaperDetailsViewModel".

- [ ] **Step 4: Write the UI state**

`PaperDetailsUiState.kt`:

```kotlin
package com.etatech.hashiya.feature.paperdetails

import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.PaperNotes

sealed interface PaperDetailsUiState {
    data object Loading : PaperDetailsUiState

    data class Loaded(val paper: LibraryPaper, val notes: PaperNotes, val saveState: NotesSaveState) : PaperDetailsUiState
}

/** What the line beside "My notes" shows: nothing, Saving…, Saved or Couldn't save. */
enum class NotesSaveState { Idle, Saving, Saved, Failed }

enum class PaperDetailsMessage { NotesSaveFailed, StatusUpdateFailed }

/** Tells the screen to leave: [Closed] when the paper is gone, [Removed] once the user removed it and its notes are saved. */
enum class PaperDetailsExit { Closed, Removed }
```

- [ ] **Step 5: Write the ViewModel**

`PaperDetailsViewModel.kt`:

```kotlin
package com.etatech.hashiya.feature.paperdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.data.di.ApplicationScope
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.ReadingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val NOTES_SAVE_DEBOUNCE_MS = 500L

/** The route's argument: PaperDetailsRoute.openAlexId. */
internal const val ARG_OPEN_ALEX_ID = "openAlexId"

@OptIn(FlowPreview::class)
@HiltViewModel
class PaperDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {
    val openAlexId: String = checkNotNull(savedStateHandle[ARG_OPEN_ALEX_ID]) { "PaperDetailsRoute needs an openAlexId" }

    /** The notes as typed; null until the stored notes are read. Read once: later database emissions never replace typing. */
    private val notes = MutableStateFlow<PaperNotes?>(null)

    /** Every write goes through [save], one at a time, so writes land in order and a flush can't be overtaken. */
    private val saveMutex = Mutex()

    /** The notes last read or written. Guarded by [saveMutex]. */
    private var storedNotes: PaperNotes? = null

    private val saveState = MutableStateFlow(NotesSaveState.Idle)

    private val _message = MutableStateFlow<PaperDetailsMessage?>(null)
    val message: StateFlow<PaperDetailsMessage?> = _message.asStateFlow()

    private val _exit = MutableStateFlow<PaperDetailsExit?>(null)
    val exit: StateFlow<PaperDetailsExit?> = _exit.asStateFlow()

    /** Eager, so a paper that is gone closes the screen even before anything collects the UI state. */
    private val paper: StateFlow<LibraryPaper?> = libraryRepository.observePaper(openAlexId)
        .onEach { if (it == null) _exit.compareAndSet(null, PaperDetailsExit.Closed) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<PaperDetailsUiState> = combine(paper.filterNotNull(), notes.filterNotNull(), saveState) { current, typed, state ->
        PaperDetailsUiState.Loaded(current, typed, state)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaperDetailsUiState.Loading)

    init {
        viewModelScope.launch {
            val stored = libraryRepository.observeNotes(openAlexId).first()
            saveMutex.withLock { storedNotes = stored }
            notes.value = stored
        }
        viewModelScope.launch {
            // collect is sequential: each write finishes before the next starts, and a burst of typing is one write.
            notes.filterNotNull().debounce(NOTES_SAVE_DEBOUNCE_MS).collect { save(it) }
        }
    }

    fun onNoteChange(section: NoteSection, text: String) {
        notes.update { current -> current?.with(section, text) }
    }

    fun onStatusChange(status: ReadingStatus) {
        viewModelScope.launch {
            try {
                libraryRepository.setStatus(openAlexId, status)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = PaperDetailsMessage.StatusUpdateFailed
            }
        }
    }

    fun onRetrySave() {
        viewModelScope.launch { notes.value?.let { save(it) } }
    }

    fun onMessageShown() {
        _message.value = null
    }

    /** Writes unsaved notes now, on the application scope, so leaving the screen can't cancel the write. */
    fun flushNotes() {
        val current = notes.value ?: return
        applicationScope.launch { save(current) }
    }

    /** Saves unsaved notes first, so Undo on the screen below restores what was just typed, then asks to leave. */
    fun onRemove() {
        viewModelScope.launch {
            notes.value?.let { save(it) }
            _exit.value = PaperDetailsExit.Removed
        }
    }

    override fun onCleared() {
        flushNotes()
    }

    private suspend fun save(value: PaperNotes) = saveMutex.withLock {
        if (value == storedNotes) return@withLock
        saveState.value = NotesSaveState.Saving
        try {
            libraryRepository.saveNotes(openAlexId, value)
            storedNotes = value
            saveState.value = NotesSaveState.Saved
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            saveState.value = NotesSaveState.Failed
            _message.value = PaperDetailsMessage.NotesSaveFailed
        }
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :feature:paperdetails:testDebugUnitTest --tests '*PaperDetailsViewModelTest'`
Expected: PASS (16 tests).

If `clearingTheViewModelSavesPendingNotes` fails to compile because `ViewModelStore.put` isn't visible, replace that line with one that creates the ViewModel through a store:

```kotlin
val store = ViewModelStore()
ViewModelProvider.create(store, viewModelFactory { initializer { viewModel } })[PaperDetailsViewModel::class]
store.clear()
```

That needs the imports `androidx.lifecycle.ViewModelProvider`, `androidx.lifecycle.viewmodel.initializer` and `androidx.lifecycle.viewmodel.viewModelFactory`.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**' ':(exclude,glob)**/src/test/screenshots/**'
git commit -m "feat: add the paper details ViewModel with note autosave"
```

---

### Task 6: `feature/paperdetails` — the Details screen, its navigation and strings

**Files:**
- Create: `feature/paperdetails/src/main/res/values/strings.xml`, `feature/paperdetails/src/main/res/values-ar/strings.xml`
- Create: `feature/paperdetails/src/main/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsActions.kt`
- Create: `feature/paperdetails/src/main/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsScreen.kt`
- Create: `feature/paperdetails/src/main/java/com/etatech/hashiya/feature/paperdetails/navigation/PaperDetailsNavigation.kt`
- Test: `feature/paperdetails/src/test/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsContentTest.kt`, `feature/paperdetails/src/test/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsScreenshotTest.kt`

**Interfaces:**
- Consumes:
  - `PaperDetailsViewModel` and its state types (Task 5);
  - `PaperHeader`, `PaperAbstract`, `ReadingStatusSelector`, `LoadingSkeleton`, `HashiyaIcons.Back` / `MoreOptions` / `Delete` / `OpenInNew`, and `designsystem_open_doi` / `designsystem_remove_from_library` (Task 4 and existing code).
- Produces:
  - `@Serializable data class PaperDetailsRoute(val openAlexId: String)`;
  - `fun NavController.navigateToPaperDetails(openAlexId: String)`;
  - `fun NavGraphBuilder.paperDetailsScreen(onBack: () -> Unit, onRemove: (openAlexId: String) -> Unit)`;
  - `internal const val NOTE_FIELD_TAG_PREFIX = "note_field_"`.

- [ ] **Step 1: Add the strings**

`feature/paperdetails/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="details_back">Back</string>
    <string name="details_more_options">More options</string>
    <string name="details_open_pdf">Open PDF</string>
    <string name="details_notes_title">My notes</string>
    <string name="details_notes_saving">Saving…</string>
    <string name="details_notes_saved">Saved</string>
    <string name="details_notes_save_failed">Couldn\'t save</string>
    <string name="details_notes_save_failed_message">Couldn\'t save your notes</string>
    <string name="details_retry">Retry</string>
    <string name="details_status_update_failed">Couldn\'t update the status</string>
    <string name="note_summary">Summary</string>
    <string name="note_summary_hint">What is this paper about, in your own words?</string>
    <string name="note_research_question">Research question</string>
    <string name="note_research_question_hint">What question or problem does it address?</string>
    <string name="note_method">Method</string>
    <string name="note_method_hint">How did the authors approach it?</string>
    <string name="note_key_findings">Key findings</string>
    <string name="note_key_findings_hint">What did they find?</string>
    <string name="note_limitations">Limitations</string>
    <string name="note_limitations_hint">What are its weaknesses or open questions?</string>
    <string name="note_thoughts">My thoughts</string>
    <string name="note_thoughts_hint">How does it relate to your work?</string>
</resources>
```

`feature/paperdetails/src/main/res/values-ar/strings.xml`:

```xml
<resources>
    <string name="details_back">رجوع</string>
    <string name="details_more_options">خيارات أخرى</string>
    <string name="details_open_pdf">فتح ملف PDF</string>
    <string name="details_notes_title">ملاحظاتي</string>
    <string name="details_notes_saving">جارٍ الحفظ…</string>
    <string name="details_notes_saved">تم الحفظ</string>
    <string name="details_notes_save_failed">تعذّر الحفظ</string>
    <string name="details_notes_save_failed_message">تعذّر حفظ ملاحظاتك</string>
    <string name="details_retry">إعادة المحاولة</string>
    <string name="details_status_update_failed">تعذّر تحديث الحالة</string>
    <string name="note_summary">الخلاصة</string>
    <string name="note_summary_hint">عمّ تتحدث هذه الورقة، بكلماتك أنت؟</string>
    <string name="note_research_question">سؤال البحث</string>
    <string name="note_research_question_hint">ما السؤال أو المشكلة التي تعالجها؟</string>
    <string name="note_method">المنهجية</string>
    <string name="note_method_hint">كيف تناولها المؤلفون؟</string>
    <string name="note_key_findings">أهم النتائج</string>
    <string name="note_key_findings_hint">ما الذي توصّلوا إليه؟</string>
    <string name="note_limitations">القيود</string>
    <string name="note_limitations_hint">ما نقاط ضعفها أو الأسئلة التي تتركها مفتوحة؟</string>
    <string name="note_thoughts">أفكاري</string>
    <string name="note_thoughts_hint">ما علاقتها ببحثك؟</string>
</resources>
```

- [ ] **Step 2: Write the failing content test**

`PaperDetailsContentTest.kt`:

```kotlin
package com.etatech.hashiya.feature.paperdetails

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class PaperDetailsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()
    private val actions = PaperDetailsActions(
        onBack = { events += "back" },
        onRemove = { events += "remove" },
        onStatusChange = { events += "status:$it" },
        onNoteChange = { section, text -> events += "note:$section:$text" },
        onRetrySave = { events += "retry" },
        onMessageShown = { events += "messageShown" },
        onOpenLink = { events += "open:$it" }
    )

    private fun loaded(
        paper: Paper = SamplePapers.attention,
        status: ReadingStatus = ReadingStatus.ToRead,
        notes: PaperNotes = PaperNotes(),
        saveState: NotesSaveState = NotesSaveState.Idle
    ) = PaperDetailsUiState.Loaded(LibraryPaper(paper, status), notes, saveState)

    private fun show(state: PaperDetailsUiState, message: PaperDetailsMessage? = null) = composeRule.setContent {
        HashiyaTheme { PaperDetailsContent(uiState = state, actions = actions, message = message) }
    }

    private fun field(section: NoteSection) = composeRule.onNodeWithTag(NOTE_FIELD_TAG_PREFIX + section.name)

    @Test
    fun headerShowsEveryAuthorVenueYearAndCitations() {
        show(loaded())

        composeRule.onNodeWithText("Attention Is All You Need").assertIsDisplayed()
        composeRule.onNodeWithText("Ashish Vaswani, Noam Shazeer, Niki Parmar, Jakob Uszkoreit, Llion Jones").assertIsDisplayed()
        composeRule.onNodeWithText("Neural Information Processing Systems · 2017 · 128,412 citations").assertIsDisplayed()
    }

    @Test
    fun untitledPaperWithoutAbstractSaysSo() {
        show(loaded(SamplePapers.untitled))

        composeRule.onNodeWithText("Untitled").assertIsDisplayed()
        composeRule.onNodeWithText("No abstract available").assertIsDisplayed()
    }

    @Test
    fun linksOpenTheDoiAndThePdf() {
        show(loaded(SamplePapers.attention))

        composeRule.onNodeWithText("Open DOI").performClick()
        composeRule.onNodeWithText("Open PDF").performClick()

        assertEquals(listOf("open:https://doi.org/10.48550/arxiv.1706.03762", "open:https://arxiv.org/pdf/1706.03762"), events)
    }

    @Test
    fun noPdfButtonWithoutAnOpenAccessPdf() {
        show(loaded(SamplePapers.bert))

        composeRule.onNodeWithText("Open DOI").assertIsDisplayed()
        composeRule.onNodeWithText("Open PDF").assertDoesNotExist()
    }

    @Test
    fun statusSelectorReportsTheNewStatus() {
        show(loaded(status = ReadingStatus.ToRead))

        composeRule.onNodeWithText("Reading").performClick()

        assertEquals(listOf("status:Reading"), events)
    }

    @Test
    fun theSixNoteSectionsAreLabelledAndInTemplateOrder() {
        show(loaded())
        val labels = listOf("Summary", "Research question", "Method", "Key findings", "Limitations", "My thoughts")

        NoteSection.entries.zip(labels).forEach { (section, label) -> field(section).assert(hasText(label)) }
        val tops = NoteSection.entries.map { field(it).getUnclippedBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun typingInASectionReportsThatSection() {
        show(loaded())

        field(NoteSection.Method).performScrollTo().performTextInput("Ablation")

        assertEquals(listOf("note:Method:Ablation"), events)
    }

    @Test
    fun storedNotesShowInTheirFields() {
        show(loaded(notes = PaperNotes(keyFindings = "Beats RNNs on WMT")))

        field(NoteSection.KeyFindings).performScrollTo().assert(hasText("Beats RNNs on WMT"))
    }

    @Test
    fun saveStatusLineFollowsTheSaveState() {
        var state by mutableStateOf(loaded())
        composeRule.setContent { HashiyaTheme { PaperDetailsContent(uiState = state, actions = actions) } }
        listOf("Saving…", "Saved", "Couldn't save").forEach { composeRule.onNodeWithText(it).assertDoesNotExist() }

        state = loaded(saveState = NotesSaveState.Saving)
        composeRule.onNodeWithText("Saving…").assertExists()
        state = loaded(saveState = NotesSaveState.Saved)
        composeRule.onNodeWithText("Saved").assertExists()
        state = loaded(saveState = NotesSaveState.Failed)
        composeRule.onNodeWithText("Couldn't save").assertExists()
    }

    @Test
    fun saveFailureOffersRetry() {
        show(loaded(saveState = NotesSaveState.Failed), message = PaperDetailsMessage.NotesSaveFailed)

        composeRule.onNodeWithText("Couldn't save your notes").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("messageShown", "retry"), events)
    }

    @Test
    fun statusUpdateFailureShowsASnackbar() {
        show(loaded(), message = PaperDetailsMessage.StatusUpdateFailed)

        composeRule.onNodeWithText("Couldn't update the status").assertIsDisplayed()
    }

    @Test
    fun removeFromTheOverflowMenu() {
        show(loaded())

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Remove from library").performClick()

        assertEquals(listOf("remove"), events)
    }

    @Test
    fun backCallsBack() {
        show(loaded())

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("back"), events)
    }

    @Test
    fun loadingHasNoMenu() {
        show(PaperDetailsUiState.Loading)

        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More options").assertDoesNotExist()
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :feature:paperdetails:testDebugUnitTest --tests '*PaperDetailsContentTest'`
Expected: compilation FAILS with "Unresolved reference: PaperDetailsActions".

- [ ] **Step 4: Write the actions, the screen and the navigation**

`PaperDetailsActions.kt`:

```kotlin
package com.etatech.hashiya.feature.paperdetails

import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.ReadingStatus

/** Every user action on the Details screen. Defaults are no-ops so tests set only what they check. */
internal data class PaperDetailsActions(
    val onBack: () -> Unit = {},
    val onRemove: () -> Unit = {},
    val onStatusChange: (ReadingStatus) -> Unit = {},
    val onNoteChange: (NoteSection, String) -> Unit = { _, _ -> },
    val onRetrySave: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onOpenLink: (String) -> Unit = {}
)
```

`PaperDetailsScreen.kt`:

```kotlin
package com.etatech.hashiya.feature.paperdetails

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.etatech.hashiya.core.designsystem.component.LoadingSkeleton
import com.etatech.hashiya.core.designsystem.component.PaperAbstract
import com.etatech.hashiya.core.designsystem.component.PaperHeader
import com.etatech.hashiya.core.designsystem.component.ReadingStatusSelector
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.designsystem.R as DesignR

internal const val NOTE_FIELD_TAG_PREFIX = "note_field_"

@Composable
internal fun PaperDetailsScreen(
    onBack: () -> Unit,
    onRemove: (openAlexId: String) -> Unit,
    viewModel: PaperDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val exit by viewModel.exit.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    // Backgrounding the app or leaving the screen writes what was typed without waiting for the pause.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flushNotes() }
    LaunchedEffect(exit) {
        when (exit) {
            PaperDetailsExit.Closed -> onBack()
            PaperDetailsExit.Removed -> onRemove(viewModel.openAlexId)
            null -> Unit
        }
    }
    PaperDetailsContent(
        uiState = uiState,
        message = message,
        actions = PaperDetailsActions(
            onBack = onBack,
            onRemove = viewModel::onRemove,
            onStatusChange = viewModel::onStatusChange,
            onNoteChange = viewModel::onNoteChange,
            onRetrySave = viewModel::onRetrySave,
            onMessageShown = viewModel::onMessageShown,
            onOpenLink = { url -> runCatching { uriHandler.openUri(url) } }
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaperDetailsContent(
    uiState: PaperDetailsUiState,
    actions: PaperDetailsActions,
    modifier: Modifier = Modifier,
    message: PaperDetailsMessage? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailed = stringResource(R.string.details_notes_save_failed_message)
    val retry = stringResource(R.string.details_retry)
    val statusUpdateFailed = stringResource(R.string.details_status_update_failed)
    LaunchedEffect(message) {
        when (message) {
            PaperDetailsMessage.NotesSaveFailed -> {
                val result = snackbarHostState.showSnackbar(saveFailed, retry, duration = SnackbarDuration.Long)
                actions.onMessageShown()
                if (result == SnackbarResult.ActionPerformed) actions.onRetrySave()
            }

            PaperDetailsMessage.StatusUpdateFailed -> {
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
                title = {},
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(HashiyaIcons.Back, contentDescription = stringResource(R.string.details_back))
                    }
                },
                actions = { if (uiState is PaperDetailsUiState.Loaded) OverflowMenu(actions.onRemove) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (uiState) {
            PaperDetailsUiState.Loading -> LoadingSkeleton(Modifier.padding(padding))
            is PaperDetailsUiState.Loaded -> DetailsBody(uiState, actions, Modifier.padding(padding))
        }
    }
}

@Composable
private fun OverflowMenu(onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(HashiyaIcons.MoreOptions, contentDescription = stringResource(R.string.details_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(DesignR.string.designsystem_remove_from_library)) },
                leadingIcon = { Icon(HashiyaIcons.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRemove()
                }
            )
        }
    }
}

// A scrolling Column, not a LazyColumn: text fields in a lazy list lose focus when they scroll out of composition.
@Composable
private fun DetailsBody(state: PaperDetailsUiState.Loaded, actions: PaperDetailsActions, modifier: Modifier = Modifier) {
    val paper = state.paper.paper
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
        PaperHeader(paper)
        Spacer(Modifier.height(16.dp))
        ReadingStatusSelector(state.paper.status, actions.onStatusChange)
        PaperLinks(paper, actions.onOpenLink)
        Spacer(Modifier.height(16.dp))
        PaperAbstract(paper)
        Spacer(Modifier.height(24.dp))
        NotesHeading(state.saveState)
        NoteSection.entries.forEach { section ->
            Spacer(Modifier.height(12.dp))
            NoteField(section, state.notes[section], onTextChange = { text -> actions.onNoteChange(section, text) })
        }
    }
}

@Composable
private fun PaperLinks(paper: Paper, onOpenLink: (String) -> Unit) {
    val doi = paper.doi
    val pdfUrl = paper.openAccessPdfUrl
    if (doi == null && pdfUrl == null) return
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (doi != null) {
            LinkButton(stringResource(DesignR.string.designsystem_open_doi), Modifier.weight(1f), onClick = { onOpenLink("https://doi.org/$doi") })
        }
        if (pdfUrl != null) {
            LinkButton(stringResource(R.string.details_open_pdf), Modifier.weight(1f), onClick = { onOpenLink(pdfUrl) })
        }
    }
}

@Composable
private fun LinkButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text(label)
        Spacer(Modifier.width(6.dp))
        Icon(HashiyaIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun NotesHeading(saveState: NotesSaveState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.details_notes_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        val status = when (saveState) {
            NotesSaveState.Idle -> null
            NotesSaveState.Saving -> R.string.details_notes_saving
            NotesSaveState.Saved -> R.string.details_notes_saved
            NotesSaveState.Failed -> R.string.details_notes_save_failed
        }
        if (status != null) {
            Text(
                stringResource(status),
                style = MaterialTheme.typography.labelMedium,
                color = if (saveState == NotesSaveState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

@Composable
private fun NoteField(section: NoteSection, text: String, onTextChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text(stringResource(section.labelRes)) },
        placeholder = { Text(stringResource(section.hintRes)) },
        // Arabic notes lay out right to left in an English UI, and English notes left to right in an Arabic one.
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(NOTE_FIELD_TAG_PREFIX + section.name)
    )
}

@get:StringRes
private val NoteSection.labelRes: Int
    get() = when (this) {
        NoteSection.Summary -> R.string.note_summary
        NoteSection.ResearchQuestion -> R.string.note_research_question
        NoteSection.Method -> R.string.note_method
        NoteSection.KeyFindings -> R.string.note_key_findings
        NoteSection.Limitations -> R.string.note_limitations
        NoteSection.Thoughts -> R.string.note_thoughts
    }

@get:StringRes
private val NoteSection.hintRes: Int
    get() = when (this) {
        NoteSection.Summary -> R.string.note_summary_hint
        NoteSection.ResearchQuestion -> R.string.note_research_question_hint
        NoteSection.Method -> R.string.note_method_hint
        NoteSection.KeyFindings -> R.string.note_key_findings_hint
        NoteSection.Limitations -> R.string.note_limitations_hint
        NoteSection.Thoughts -> R.string.note_thoughts_hint
    }
```

`navigation/PaperDetailsNavigation.kt`:

```kotlin
package com.etatech.hashiya.feature.paperdetails.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.etatech.hashiya.feature.paperdetails.PaperDetailsScreen
import kotlinx.serialization.Serializable

/** A saved paper's details and notes. The property name is the ViewModel's ARG_OPEN_ALEX_ID. */
@Serializable
data class PaperDetailsRoute(val openAlexId: String)

fun NavController.navigateToPaperDetails(openAlexId: String) = navigate(PaperDetailsRoute(openAlexId))

fun NavGraphBuilder.paperDetailsScreen(onBack: () -> Unit, onRemove: (openAlexId: String) -> Unit) {
    composable<PaperDetailsRoute> { PaperDetailsScreen(onBack = onBack, onRemove = onRemove) }
}
```

- [ ] **Step 5: Run the content test to verify it passes**

Run: `./gradlew :feature:paperdetails:testDebugUnitTest --tests '*PaperDetailsContentTest'`
Expected: PASS (14 tests).

- [ ] **Step 6: Add the screenshot test**

`PaperDetailsScreenshotTest.kt`:

```kotlin
package com.etatech.hashiya.feature.paperdetails

import androidx.compose.ui.test.junit4.createComposeRule
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.PaperNotes
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
class PaperDetailsScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    // ViT has no abstract, so the notes start high enough to be on screen.
    private val notes = PaperNotes(
        summary = "Images split into 16x16 patches go straight into a standard Transformer.",
        researchQuestion = "Can attention alone match CNNs on image classification?"
    )

    @Test
    fun withNotes() = composeRule.captureScreenshot("details_notes", variant, arabicText = "ملاحظاتي") {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(LibraryPaper(SamplePapers.vit, ReadingStatus.Reading), notes, NotesSaveState.Saved),
            actions = PaperDetailsActions()
        )
    }

    @Test
    fun empty() = composeRule.captureScreenshot("details_empty", variant, arabicText = "الخلاصة") {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(LibraryPaper(SamplePapers.untitled, ReadingStatus.ToRead), PaperNotes(), NotesSaveState.Idle),
            actions = PaperDetailsActions()
        )
    }

    @Test
    fun saveFailed() = composeRule.captureScreenshot("details_save_failed", variant, arabicText = "تعذّر الحفظ") {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(LibraryPaper(SamplePapers.vit, ReadingStatus.Reading), notes, NotesSaveState.Failed),
            actions = PaperDetailsActions()
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
```

Copy the imports from `feature/settings/.../SettingsScreenshotTest.kt` if any path differs.

- [ ] **Step 7: Run the whole module and look at the images locally**

Run: `./gradlew :feature:paperdetails:testDebugUnitTest :feature:paperdetails:recordRoborazziDebug`
Expected: PASS.

Open `feature/paperdetails/build/outputs/roborazzi/` or `feature/paperdetails/src/test/screenshots/` and check the images:
- the Arabic ones are right-to-left, with Arabic labels;
- the English note text still reads left-to-right in the Arabic images;
- the status line shows **Saved** or **Couldn't save**.

These local PNGs are for inspection only, so don't stage them.

- [ ] **Step 8: Commit**

```bash
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**' ':(exclude,glob)**/src/test/screenshots/**'
git commit -m "feat: add the paper details screen with the note template"
```

---
### Task 7: `feature/library` and `feature/search` — open Details, handle remove requests, retire the Library's sheet

**Files:**
- Modify: `feature/library/src/main/java/com/etatech/hashiya/feature/library/navigation/LibraryNavigation.kt`
- Modify: `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryViewModel.kt`
- Modify: `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryActions.kt`
- Modify: `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryScreen.kt`
- Modify: `feature/search/src/main/java/com/etatech/hashiya/feature/search/navigation/SearchNavigation.kt`
- Modify: `feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchViewModel.kt`
- Modify: `feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchActions.kt`
- Modify: `feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchScreen.kt`
- Test (library): `LibraryViewModelTest.kt`, `LibraryContentTest.kt`, `LibraryScreenshotTest.kt`, `LibrarySwipeUndoTest.kt`
- Test (search): `SearchViewModelTest.kt`, `SearchContentTest.kt`

**Interfaces:**
- Consumes: `PaperPreviewSheet(…, onOpenDetails)` (Task 4); `FakeLibraryRepository.failOnRemove` (existing).
- Produces:
  - `libraryScreen(onGoToSearch, onAddPaper, onOpenSettings, onOpenPaper: (openAlexId: String) -> Unit)`;
  - `fun NavBackStackEntry.requestLibraryRemove(openAlexId: String)`;
  - `internal const val LIBRARY_REMOVE_REQUEST = "library_remove_request"`;
  - `searchScreen(onOpenSettings, onOpenPaper: (openAlexId: String) -> Unit)`;
  - `fun NavBackStackEntry.requestSearchRemove(openAlexId: String)`;
  - `internal const val SEARCH_REMOVE_REQUEST = "search_remove_request"`;
  - `SearchActions.onOpenDetails: (Paper) -> Unit`.

A back stack entry's `SavedStateHandle` is the same instance its Hilt ViewModel receives. The app writes a request into the entry below Details, and that ViewModel reacts to it.

- [ ] **Step 1: Update the Library ViewModel tests (failing)**

In `LibraryViewModelTest.kt`:
- delete the line `backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.selectedPaper.collect() }` from the `viewModel()` helper;
- delete the tests `statusChangeOutOfTheChipKeepsThePreviewOpen` and `selectingAndDismissingPreview`;
- replace `removingOffersUndoAndClosesPreview`;
- add the import `com.etatech.hashiya.feature.library.navigation.LIBRARY_REMOVE_REQUEST` and the two remove-request tests.

```kotlin
    @Test
    fun removingOffersUndo() = runTest {
        repository.save(SamplePapers.bert)
        val viewModel = viewModel()

        viewModel.onRemove(SamplePapers.bert)

        assertEquals(LibraryUiState.Empty, viewModel.uiState.value)
        assertEquals(SamplePapers.bert, viewModel.pendingUndo.value?.paper)
    }

    @Test
    fun removeRequestFromDetailsRemovesWithUndoOnce() = runTest {
        saveSamples()
        val viewModel = viewModel()

        savedStateHandle[LIBRARY_REMOVE_REQUEST] = SamplePapers.bert.openAlexId

        assertEquals(listOf(SamplePapers.vit.title, SamplePapers.attention.title), viewModel.titles())
        assertEquals(SamplePapers.bert, viewModel.pendingUndo.value?.paper)
        assertNull(savedStateHandle.get<String>(LIBRARY_REMOVE_REQUEST))
        viewModel.onUndoRemove()
        assertEquals(all, viewModel.titles())
    }

    /** The request can be waiting before the ViewModel exists (process death between Details and the Library). */
    @Test
    fun removeRequestWaitingWhenTheViewModelStartsIsHandled() = runTest {
        saveSamples()

        val viewModel = viewModel(SavedStateHandle(mapOf(LIBRARY_REMOVE_REQUEST to SamplePapers.bert.openAlexId)))

        assertEquals(listOf(SamplePapers.vit.title, SamplePapers.attention.title), viewModel.titles())
        assertEquals(SamplePapers.bert, viewModel.pendingUndo.value?.paper)
    }
```

In `LibraryContentTest.kt`:
- remove the `selectedPaper` parameter from the first `show(…)` overload and its `selectedPaper = selectedPaper,` argument;
- delete the test `previewHasTheStatusSelector`;
- rename `tappingRowOpensPreview` to `tappingRowOpensThePaper` (its body stays the same).

In `LibraryScreenshotTest.kt`, change `LibraryContent(uiState = state, selectedPaper = null, pendingUndo = null, actions = LibraryActions())` to `LibraryContent(uiState = state, pendingUndo = null, actions = LibraryActions())`.

In `LibrarySwipeUndoTest.kt`, change `LibraryScreen(onGoToSearch = {}, onAddPaper = {}, onOpenSettings = {}, viewModel = viewModel)` to `LibraryScreen(onGoToSearch = {}, onAddPaper = {}, onOpenSettings = {}, onOpenPaper = {}, viewModel = viewModel)`.

- [ ] **Step 2: Add the Search tests (failing)**

In `SearchViewModelTest.kt`, add the import `com.etatech.hashiya.feature.search.navigation.SEARCH_REMOVE_REQUEST` and these tests. The fake is named `libraryRepository` there, and `savedStateHandle` is the default handle:

```kotlin
    @Test
    fun removeRequestFromDetailsRemovesThePaper() = runTest {
        libraryRepository.save(SamplePapers.bert)
        val viewModel = viewModel()

        savedStateHandle[SEARCH_REMOVE_REQUEST] = SamplePapers.bert.openAlexId
        runCurrent()

        assertEquals(emptySet<String>(), viewModel.savedIds.value)
        assertNull(savedStateHandle.get<String>(SEARCH_REMOVE_REQUEST))
    }

    @Test
    fun failedRemoveRequestShowsRemoveFailed() = runTest {
        libraryRepository.save(SamplePapers.bert)
        libraryRepository.failOnRemove = true
        val viewModel = viewModel()

        savedStateHandle[SEARCH_REMOVE_REQUEST] = SamplePapers.bert.openAlexId
        runCurrent()

        assertEquals(SearchMessage.RemoveFailed, viewModel.message.value)
    }
```

In `SearchContentTest.kt`, add the imports below if they're missing, then the test:
- `androidx.compose.ui.test.hasAnyAncestor`
- `androidx.compose.ui.test.hasText`
- `androidx.compose.ui.test.isDialog`

```kotlin
    @Test
    fun savedPapersSheetOpensDetails() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            HashiyaTheme {
                SearchContent(
                    uiState = SearchUiState(),
                    papers = flowOf(PagingData.empty<Paper>()).collectAsLazyPagingItems(),
                    savedIds = setOf(SamplePapers.bert.openAlexId),
                    selectedItem = PaperItem(SamplePapers.bert, inLibrary = true),
                    message = null,
                    actions = SearchActions(
                        onDismissPreview = { events += "dismiss" },
                        onOpenDetails = { events += "details:${it.openAlexId}" }
                    ),
                    currentYear = 2026
                )
            }
        }

        // The sheet is a dialog window.
        composeRule.onNode(hasText("Open details") and hasAnyAncestor(isDialog())).performClick()

        assertEquals(listOf("dismiss", "details:${SamplePapers.bert.openAlexId}"), events)
    }

    @Test
    fun unsavedPapersSheetHasNoOpenDetails() {
        composeRule.setContent {
            HashiyaTheme {
                SearchContent(
                    uiState = SearchUiState(),
                    papers = flowOf(PagingData.empty<Paper>()).collectAsLazyPagingItems(),
                    savedIds = emptySet(),
                    selectedItem = PaperItem(SamplePapers.bert, inLibrary = false),
                    message = null,
                    actions = SearchActions(),
                    currentYear = 2026
                )
            }
        }

        composeRule.onNode(hasText("Save to library") and hasAnyAncestor(isDialog())).assertExists()
        composeRule.onNodeWithText("Open details").assertDoesNotExist()
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :feature:library:testDebugUnitTest :feature:search:testDebugUnitTest`
Expected: compilation FAILS with unresolved `LIBRARY_REMOVE_REQUEST`, `SEARCH_REMOVE_REQUEST`, `onOpenPaper` and `onOpenDetails`.

- [ ] **Step 4: Library navigation**

Replace `LibraryNavigation.kt` with:

```kotlin
package com.etatech.hashiya.feature.library.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.etatech.hashiya.feature.library.LibraryScreen
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

/** The key in the Library entry's SavedStateHandle, which its ViewModel shares, that asks it to remove a paper. */
internal const val LIBRARY_REMOVE_REQUEST = "library_remove_request"

fun NavController.navigateToLibrary(navOptions: NavOptions? = null) = navigate(LibraryRoute, navOptions)

fun NavGraphBuilder.libraryScreen(
    onGoToSearch: () -> Unit,
    onAddPaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPaper: (openAlexId: String) -> Unit
) {
    composable<LibraryRoute> {
        LibraryScreen(onGoToSearch = onGoToSearch, onAddPaper = onAddPaper, onOpenSettings = onOpenSettings, onOpenPaper = onOpenPaper)
    }
}

/** Asks this Library entry to remove [openAlexId] the way a swipe does, with Undo: Details' "Remove from library". */
fun NavBackStackEntry.requestLibraryRemove(openAlexId: String) {
    savedStateHandle[LIBRARY_REMOVE_REQUEST] = openAlexId
}
```

- [ ] **Step 5: Library ViewModel**

In `LibraryViewModel.kt`:

1. Delete the `selectedId` property, the `selectedPaper` property with its KDoc, `onPaperClick` and `onDismissPreview`.
2. Replace `onRemove`:

```kotlin
    fun onRemove(paper: Paper) = remove(paper.openAlexId)

    private fun remove(openAlexId: String) {
        viewModelScope.launch {
            _pendingUndo.value = libraryRepository.remove(openAlexId)
        }
    }
```

3. Directly after `val message: StateFlow<LibraryMessage?> = _message.asStateFlow()`, add:

```kotlin
    // Declared after _pendingUndo on purpose: with an immediate dispatcher, a request that is already waiting
    // (restored after process death) is handled right here, and remove() needs _pendingUndo to exist.
    init {
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(LIBRARY_REMOVE_REQUEST, null).filterNotNull().collect { openAlexId ->
                savedStateHandle[LIBRARY_REMOVE_REQUEST] = null
                remove(openAlexId)
            }
        }
    }
```

4. Add the imports `com.etatech.hashiya.feature.library.navigation.LIBRARY_REMOVE_REQUEST` and `kotlinx.coroutines.flow.filterNotNull`. `spotlessApply` removes the ones that are now unused (such as `flowOf`, if nothing else uses it).

- [ ] **Step 6: Library actions and screen**

In `LibraryActions.kt`, delete the lines `val onDismissPreview: () -> Unit = {},` and `val onOpenDoi: (String) -> Unit = {}`, and make `val onOpenSettings: () -> Unit = {}` the last line.

In `LibraryScreen.kt`, replace `LibraryScreen` with:

```kotlin
@Composable
internal fun LibraryScreen(
    onGoToSearch: () -> Unit,
    onAddPaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPaper: (openAlexId: String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    LibraryContent(
        uiState = uiState,
        pendingUndo = pendingUndo,
        message = message,
        actions = LibraryActions(
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::onSearch,
            onClearQuery = viewModel::onClearQuery,
            onStatusFilterChange = viewModel::onStatusFilterChange,
            onClearSearchAndFilters = viewModel::onClearSearchAndFilters,
            onPaperClick = { paper -> onOpenPaper(paper.openAlexId) },
            onStatusChange = viewModel::onStatusChange,
            onRemove = viewModel::onRemove,
            onUndo = viewModel::onUndoRemove,
            onUndoDismissed = viewModel::onUndoDismissed,
            onMessageShown = viewModel::onMessageShown,
            onGoToSearch = onGoToSearch,
            onAddPaper = onAddPaper,
            onOpenSettings = onOpenSettings
        )
    )
}
```

In `LibraryContent`:
- remove the `selectedPaper: LibraryPaper?,` parameter;
- delete the whole `selectedPaper?.let { selected -> PaperPreviewSheet(…) }` block at the end.

Run `./gradlew spotlessApply` to drop the now-unused imports (`PaperPreviewSheet`, `LocalUriHandler`).

- [ ] **Step 7: Search navigation, ViewModel, actions and screen**

In `SearchNavigation.kt`, add `import androidx.navigation.NavBackStackEntry`, then:

```kotlin
/** The key in the Search entry's SavedStateHandle, which its ViewModel shares, that asks it to remove a paper. */
internal const val SEARCH_REMOVE_REQUEST = "search_remove_request"
```

Replace `searchScreen` with:

```kotlin
fun NavGraphBuilder.searchScreen(onOpenSettings: () -> Unit, onOpenPaper: (openAlexId: String) -> Unit) {
    composable<SearchRoute> { SearchScreen(onOpenSettings = onOpenSettings, onOpenPaper = onOpenPaper) }
}

/** Asks this Search entry to remove [openAlexId] from the library, as its sheet's Remove does: Details' "Remove from library". */
fun NavBackStackEntry.requestSearchRemove(openAlexId: String) {
    savedStateHandle[SEARCH_REMOVE_REQUEST] = openAlexId
}
```

In `SearchViewModel.kt`, directly after `val message: StateFlow<SearchMessage?> = _message.asStateFlow()`, add:

```kotlin
    // After _message, which a failed removal sets; a request already waiting (process death) is handled right here.
    init {
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(SEARCH_REMOVE_REQUEST, null).filterNotNull().collect { openAlexId ->
                savedStateHandle[SEARCH_REMOVE_REQUEST] = null
                try {
                    libraryRepository.remove(openAlexId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _message.value = SearchMessage.RemoveFailed
                }
            }
        }
    }
```

Then add these imports if they're missing:
- `com.etatech.hashiya.feature.search.navigation.SEARCH_REMOVE_REQUEST`
- `kotlinx.coroutines.flow.filterNotNull`

In `SearchActions.kt`, add after `val onPaperClick: (Paper) -> Unit = {},`:

```kotlin
    val onOpenDetails: (Paper) -> Unit = {},
```

In `SearchScreen.kt`:

1. Change the signature to `internal fun SearchScreen(onOpenSettings: () -> Unit, onOpenPaper: (openAlexId: String) -> Unit, viewModel: SearchViewModel = hiltViewModel())`.
2. In the `SearchActions(…)` it builds, add `onOpenDetails = { paper -> onOpenPaper(paper.openAlexId) },`.
3. Replace the sheet at the end of `SearchContent` with:

```kotlin
    selectedItem?.let { item ->
        PaperPreviewSheet(
            paper = item.paper,
            inLibrary = item.inLibrary,
            onDismiss = actions.onDismissPreview,
            onToggleSave = { actions.onToggleSave(item) },
            onOpenDoi = actions.onOpenDoi,
            // Details is for saved papers only.
            onOpenDetails = if (item.inLibrary) {
                {
                    actions.onDismissPreview()
                    actions.onOpenDetails(item.paper)
                }
            } else {
                null
            }
        )
    }
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew :feature:library:testDebugUnitTest :feature:search:testDebugUnitTest`
Expected: PASS for every test in both modules. That includes `LibrarySwipeUndoTest` and the existing Library and Search screenshot tests, which are verified on CI.

- [ ] **Step 9: Commit**

```bash
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**' ':(exclude,glob)**/src/test/screenshots/**'
git commit -m "feat: open paper details from the library and from saved search results"
```

---

### Task 8: `app` — wire Details into navigation

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/etatech/hashiya/navigation/HashiyaApp.kt`
- Test: `app/src/test/java/com/etatech/hashiya/HashiyaAppNavigationTest.kt`

**Interfaces:**
- Consumes: `paperDetailsScreen`, `navigateToPaperDetails` (Task 6); `libraryScreen(…, onOpenPaper)`, `searchScreen(…, onOpenPaper)`, `requestLibraryRemove`, `requestSearchRemove`, `LibraryRoute`, `SearchRoute` (Task 7).
- Produces: the running app flow.

- [ ] **Step 1: Write the failing navigation tests**

In `HashiyaAppNavigationTest.kt`:
- add the imports `com.etatech.hashiya.core.data.repository.LibraryRepository`, `com.etatech.hashiya.core.testing.SamplePapers`, `javax.inject.Inject`, `kotlinx.coroutines.runBlocking`, `org.junit.Before` and `androidx.compose.ui.test.onNodeWithContentDescription` (if missing);
- add the injected repository, the `@Before`, and the tests.

```kotlin
    @Inject
    lateinit var libraryRepository: LibraryRepository

    @Before
    fun inject() = hiltRule.inject()

    private fun openSavedPaper() {
        runBlocking { libraryRepository.save(SamplePapers.bert) }
        waitForText(SamplePapers.bert.title)
        composeRule.onNodeWithText(SamplePapers.bert.title).performClick()
        waitForText("My notes")
    }

    @Test
    fun libraryRowOpensDetailsWithoutTheNavigationBarAndBackReturns() {
        openSavedPaper()

        // The navigation bar (with its "Search" item) is gone once the transition from the Library ends.
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithText("Search").fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithContentDescription("Back").performClick()

        waitForText("Search your library")
    }

    @Test
    fun removeOnDetailsReturnsToTheLibraryWithUndo() {
        openSavedPaper()

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Remove from library").performClick()

        waitForText("Removed from library")
        composeRule.onNodeWithText("Undo").performClick()
        waitForText(SamplePapers.bert.title)
    }
```

The existing tests start from an empty library, and the new ones save through the same Hilt singleton the app uses. Each Robolectric test gets a fresh database, so the existing tests still see an empty library.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*HashiyaAppNavigationTest'`
Expected: compilation FAILS, because `libraryScreen(…)` is missing `onOpenPaper` and `searchScreen(…)` is missing `onOpenPaper`.

- [ ] **Step 3: Wire it**

In `app/build.gradle.kts`, add after `implementation(project(":feature:library"))`:

```kotlin
    implementation(project(":feature:paperdetails"))
```

In `HashiyaApp.kt`, add these imports:
- `androidx.navigation.NavDestination.Companion.hasRoute` (it's probably already there)
- `com.etatech.hashiya.feature.library.navigation.requestLibraryRemove`
- `com.etatech.hashiya.feature.paperdetails.navigation.navigateToPaperDetails`
- `com.etatech.hashiya.feature.paperdetails.navigation.paperDetailsScreen`
- `com.etatech.hashiya.feature.search.navigation.requestSearchRemove`

Replace the `NavHost` block's contents with:

```kotlin
        NavHost(navController = navController, startDestination = LibraryRoute) {
            libraryScreen(
                onGoToSearch = { navController.navigateToTopLevel(TopLevelDestination.Search) },
                onAddPaper = { navController.openSearch(SearchRoute(focusSearch = true)) },
                onOpenSettings = { navController.navigateToSettings() },
                onOpenPaper = { openAlexId -> navController.navigateToPaperDetails(openAlexId) }
            )
            searchScreen(
                onOpenSettings = { navController.navigateToSettings() },
                onOpenPaper = { openAlexId -> navController.navigateToPaperDetails(openAlexId) }
            )
            // Not a top-level destination, so the navigation bar is hidden, as on Settings.
            paperDetailsScreen(
                onBack = { navController.popBackStack() },
                onRemove = { openAlexId -> navController.removeFromDetails(openAlexId) }
            )
            settingsScreen(onBack = { navController.popBackStack() })
        }
```

Add at the end of the file:

```kotlin
/**
 * Details' "Remove from library": the screen below does the removal, so the Library offers its usual Undo,
 * then Details closes. Details is only ever opened from the Library or Search.
 */
private fun NavController.removeFromDetails(openAlexId: String) {
    previousBackStackEntry?.let { previous ->
        when {
            previous.destination.hasRoute<LibraryRoute>() -> previous.requestLibraryRemove(openAlexId)
            previous.destination.hasRoute<SearchRoute>() -> previous.requestSearchRemove(openAlexId)
        }
    }
    popBackStack()
}
```

- [ ] **Step 4: Run the app tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS for every test, including `ShareNavigationTest`, the update tests and the 2 new navigation tests.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**' ':(exclude,glob)**/src/test/screenshots/**'
git commit -m "feat: navigate to paper details and hand removals back to the screen below"
```

---

### Task 9: README, screenshot baselines, full verification and on-device checks

**Files:**
- Modify: `README.md`
- Create (downloaded from CI): `feature/paperdetails/src/test/screenshots/details_*-*.png` (12 files)

**Interfaces:**
- Consumes: everything above.
- Produces: the finished branch.

- [ ] **Step 1: Update the README**

In `README.md`, in `## Features`, insert after the line that starts `- Search your library offline`:

```markdown
- Open a saved paper's details (every author, the full abstract, DOI and PDF links) and write structured notes: Summary, Research question, Method, Key findings, Limitations and My thoughts. Notes save as you type and are searchable from the Library.
```

In the architecture diagram, replace the first two graph lines with:

```
    app --> feature/search & feature/library & feature/paperdetails & feature/settings
    feature/search & feature/library & feature/paperdetails & feature/settings --> core/data & core/designsystem & core/model
```

In `## Roadmap`, change `4. Paper details and structured notes` to `4. ✅ Paper details and structured notes`.

Leave the `## iOS` section as it is. iOS gets this feature in its own sub-project.

- [ ] **Step 2: Run the full local check**

Run: `./gradlew spotlessCheck assembleDebug testDebugUnitTest :core:model:test lintDebug --continue`
Expected: BUILD SUCCESSFUL. Screenshots aren't verified locally, because the baselines come from Linux.

- [ ] **Step 3: Commit the README**

```bash
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**' ':(exclude,glob)**/src/test/screenshots/**'
git commit -m "docs: describe paper details and notes in the README"
```

- [ ] **Step 4: Record the screenshot baselines on CI Linux**

Run in the background (it takes 10–15 minutes): `bash scripts/record-screenshots-on-linux.sh`
Expected: it finishes by copying the recorded baselines into the checkout.

Then check what changed:

```bash
git status --short -- ':(glob)**/src/test/screenshots/**'
```

Expected:
- 12 new files, `feature/paperdetails/src/test/screenshots/details_{notes,empty,save_failed}-{EnglishLight,EnglishDark,ArabicLight,ArabicDark}.png`;
- no changed `core/designsystem` baselines, because the header extraction must be pixel-identical;
- no changed `feature/library` or `feature/search` baselines.

If an existing baseline changed, open the old and new images side by side. Commit it only if the change is intended; otherwise fix the code and record again.

Open the 12 new images and check them the way Task 6 Step 7 describes.

```bash
git add -- ':(glob)**/src/test/screenshots/**'
git commit -m "test: record the paper details screenshot baselines on CI"
```

- [ ] **Step 5: Check the authors and push**

```bash
git log --format='%an <%ae>' origin/main..HEAD | sort -u   # must print only: Fady <fady.fouad.a@gmail.com>
git push -u origin feat/paper-details-and-notes
```

Wait for the `CI` workflow on the branch to pass. It runs `spotlessCheck assembleDebug testDebugUnitTest :core:model:test lintDebug -Proborazzi.test.verify=true`. If screenshot verification fails, download the `screenshot-diffs` artifact and compare.

- [ ] **Step 6: On-device checks (spec §11)**

On a phone or emulator:

1. Install the `main` build, save three papers and set one to Reading. Then install this branch over it without uninstalling (`./gradlew :app:installDebug`). The app opens, every paper and status is still there, and Library search still finds them.
2. Tap a Library row. Details shows every author, the abstract and the status, and Open DOI / Open PDF open the browser.
3. Type in Summary and Method, wait a moment, and see **Saved**. Go back, reopen the paper, and the text is there.
4. Type a note, press Home right away, then force-stop the app from Settings. After reopening, the note is there.
5. In the Library, search for a word that is only in a note, and the paper is found. Do the same with an Arabic word typed without tashkeel against a note written with it.
6. Remove from the Details overflow menu. The app goes back to the Library with an Undo snackbar, and Undo brings the paper back with its notes and status.
7. In Search, open a saved paper's sheet and tap **Open details**, and Details opens. Remove there, and the app goes back to Search with the paper no longer saved. An unsaved paper's sheet has no **Open details**.
8. Switch the app to Arabic. Details is right-to-left, the labels and hints are Arabic, and an English note stays left-to-right.

Report which checks were done and their results. Don't report a check as passed if it wasn't run.
