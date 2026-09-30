# Sub-project 4: Paper details and structured notes — Design

- **Date:** 2026-09-30
- **Status:** Awaiting review
- **Scope:** Fourth of six MVP sub-projects for Hashiya, Android only (builds on sub-projects 1–3, merged in `3f721a9`, `1be1d8f` and `67e850c`). The iOS version gets its own spec once this one is merged.

## 1. Context

Hashiya's core loop is **Discover → Save → Read → Extract → Compare → Cite**. Sub-projects 1–3 made discovering, saving and finding papers work. A saved paper is still only a bottom-sheet preview. This sub-project starts **Extract**: every saved paper gets a full details screen with notes in a fixed template, and the notes are searchable from the Library.

The same standards as before apply: modular architecture and dependency rules, English and Arabic with full RTL, TDD with hand-written fakes, Roborazzi screenshots with baselines recorded on CI Linux, and a green CI.

### Decisions made during brainstorming

| Topic | Decision |
|---|---|
| Note template | Six fixed sections, all optional plain text: Summary, Research question, Method, Key findings, Limitations, My thoughts. |
| Entry point | Tapping a Library row opens Details. The Library no longer uses the preview sheet. Search keeps its sheet, which gains **Open details** once the paper is saved. |
| Editing | Always editable. Changes save automatically after a 500 ms pause and when leaving the screen. No Edit or Save buttons. |
| Storage | A separate `paper_notes` table, one row per paper with notes (approach A). The search index gains a `notes` column. |
| Remove | From the Details overflow menu. Details hands the removal back to the screen below, so there is one Undo implementation. |
| Platforms | Android in this spec. iOS follows in its own spec and plan. |

## 2. Goals and non-goals

### Goals

1. Tapping a saved paper in the Library opens a Details screen with every author, the full abstract, venue, year, citations, open access, the DOI and PDF links, and the reading status.
2. Each saved paper has six note sections that are written on the Details screen and saved without an explicit action.
3. Library search finds a paper by words in its notes, with the same folding (case, accents, tashkeel, alef/yaa variants, Arabic-Indic digits) as the other columns.
4. Removing a paper and tapping Undo brings back its notes along with its status.
5. Existing libraries upgrade in place: no paper, status or search result is lost.

### Non-goals

- Rich text, Markdown, bullet lists or images in notes.
- Custom or user-defined note sections.
- Notes on papers that are not saved.
- Highlighting the matched note text in Library results.
- Exporting notes (BibTeX export is sub-project 5, and notes aren't part of it).
- Showing a "has notes" marker on Library rows.
- Details for unsaved papers. Search's preview sheet stays the view for those.

## 3. Model (`core/model`)

```kotlin
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
    operator fun get(section: NoteSection): String
    fun with(section: NoteSection, text: String): PaperNotes

    /** True when every section is blank (empty or whitespace only). */
    val isEmpty: Boolean
}
```

The search column's text is built by `searchableText` over the six sections joined with spaces. That function lives next to `searchEntityFor` in `core/database` (`notesSearchText(notes)`), because it mirrors how the other columns are built.

`NoteSection` sets the on-screen order.

## 4. Database (`core/database`), schema version 3

### 4.1 Notes table

```kotlin
@Entity(
    tableName = "paper_notes",
    foreignKeys = [ForeignKey(entity = PaperEntity::class, parentColumns = ["id"], childColumns = ["paper_id"], onDelete = ForeignKey.CASCADE)]
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
```

A row exists only while at least one section is not blank. Saving notes that are all blank deletes the row.

### 4.2 Search index

`PaperSearchEntity` gains `val notes: String`, which holds `notesSearchText` of the paper's notes, or `""`. `searchEntityFor` takes an optional `notes: PaperNotes?` (default null). An FTS4 table can't be altered, so the migration rebuilds it (§4.4).

The existing `ftsMatch` and Library queries don't change. `MATCH` against the table name already searches every indexed column, so notes become searchable as soon as the column is there.

### 4.3 DAO (`PaperDao`)

| Member | Purpose |
|---|---|
| `observeByOpenAlexId(openAlexId): Flow<PaperWithAuthors?>` | `@Transaction`. The Details screen's paper. Emits null once the paper is deleted. |
| `observeNotes(openAlexId): Flow<PaperNotesEntity?>` | Joins `papers` on `paper_notes.paper_id = papers.id`. |
| `saveNotes(openAlexId: String, notes: PaperNotes, updatedAt: Long): Boolean` | `@Transaction`. Looks up the paper's local id and returns false if the paper isn't saved. It then upserts the notes row built from `notes` (or deletes the row if `notes.isEmpty`) and runs `UPDATE paper_search SET notes = :text WHERE paper_id = :id` with `notesSearchText(notes)`. |
| `deleteByOpenAlexId(openAlexId): DeletedPaper?` | Now returns `DeletedPaper(paper: PaperWithAuthors, notes: PaperNotesEntity?)`. The notes row is read before the delete, and the delete cascades it. |
| `insertPaperWithAuthors(paper, authors, search, notes: PaperNotesEntity? = null): Boolean` | Also writes the notes row when one is given. `search.notes` must already hold that row's search text. |

`core/database` already depends on `core/model` (for `searchableText`), so the DAO takes `PaperNotes` directly. The conversions between `PaperNotes` and `PaperNotesEntity` (`PaperNotes.asEntity(paperId, updatedAt)`, `PaperNotesEntity.asPaperNotes()`) live in `core/database/model/PaperNotesEntity.kt`.

### 4.4 Migration 2 → 3

```text
CREATE TABLE IF NOT EXISTS `paper_notes` (… exactly as Room generates it in schemas/…/3.json …)
CREATE TEMP TABLE paper_search_copy AS SELECT paper_id, title, authors, abstract, venue FROM paper_search
DROP TABLE paper_search
CREATE VIRTUAL TABLE IF NOT EXISTS `paper_search` USING FTS4(… exactly as Room generates it, now with `notes` …)
INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes)
    SELECT paper_id, title, authors, abstract, venue, '' FROM paper_search_copy
DROP TABLE paper_search_copy
```

The migration copies the existing rows into a plain temporary table and back. It doesn't rename an FTS table, so the stored `CREATE` statement is exactly the one Room validates. No text is re-normalized, and no paper has notes before version 3.

### 4.5 Schema export

`schemas/com.etatech.hashiya.core.database.HashiyaDatabase/3.json` is generated and committed. `HashiyaDatabase` lists `PaperNotesEntity`, has `version = 3`, and `DatabaseModule` registers `MIGRATION_2_3`.

## 5. Repository (`core/data`)

`LibraryRepository` gains:

```kotlin
/** The saved paper with its status; null when it isn't saved (or stops being saved). */
fun observePaper(openAlexId: String): Flow<LibraryPaper?>

/** The paper's notes; empty PaperNotes when it has none or isn't saved. */
fun observeNotes(openAlexId: String): Flow<PaperNotes>

/** Saves the notes (blank notes delete them) and updates the search index. Does nothing if the paper isn't saved. */
suspend fun saveNotes(openAlexId: String, notes: PaperNotes)
```

- `RemovedPaper` gains `val notes: PaperNotes`. `remove` fills it from `DeletedPaper.notes`, and `restore` writes it back, including the search column.
- `saveNotes` and `restore` pass the repository's existing `now()` as `updatedAt`. Nothing reads `updated_at` yet, so a restore doesn't need to keep the original value.
- `FakeLibraryRepository` (`core/testing`) implements the new members. It stores notes per paper, drops them on `remove`, returns them in `RemovedPaper`, and has a `failSaveNotes` switch.

## 6. Details screen (`feature/paperdetails`)

A new module using the `hashiya.android.feature` convention plugin, so its dependencies are `core:data`, `core:model` and `core:designsystem`, the same as the other features.

### 6.1 Navigation

```kotlin
@Serializable data class PaperDetailsRoute(val openAlexId: String)

fun NavController.navigateToPaperDetails(openAlexId: String)
fun NavGraphBuilder.paperDetailsScreen(onBack: () -> Unit, onRemove: (openAlexId: String) -> Unit)

/** The key under which Details hands a removal back to the previous back stack entry's SavedStateHandle. */
const val REMOVE_PAPER_RESULT = "remove_paper"
```

- The route is pushed on top of Library or Search, and the bottom bar stays visible. `TopLevelDestination` matching doesn't change: the tab that opened Details stays selected.
- **Remove:** `HashiyaApp` wires `onRemove` to set `REMOVE_PAPER_RESULT` on `previousBackStackEntry.savedStateHandle`, then `popBackStack()`.
  - The Library entry collects the key and calls `LibraryViewModel.onRemove`, which shows the existing Undo snackbar, then clears the key.
  - The Search entry does the same with a new `SearchViewModel.onRemove(openAlexId)`, which removes it the way the sheet's toggle does, with the same `RemoveFailed` message on failure.
- **Library:** `libraryScreen` gains `onOpenPaper: (openAlexId) -> Unit`. A row tap calls it.
- **Search:** `searchScreen` gains `onOpenPaper`. The sheet's **Open details** button closes the sheet and then calls it.

### 6.2 Layout

One `LazyColumn` (or a scrolling `Column`), top to bottom:

1. **Top app bar:** Back (auto-mirrored arrow). The title stays empty until the header scrolls away, then shows the paper title on one line. The overflow menu (**More options**) has one item, **Remove from library**.
2. **Header:**
   - the title (`designsystem_untitled` when empty);
   - every author, comma-separated, in full;
   - venue · year;
   - the citations count and the open access badge, using the existing `PaperFormatting` helpers.
3. **Reading status:** the existing `ReadingStatusSelector`.
4. **Links:** **Open DOI** when there is a DOI, and **Open PDF** when there is an open-access PDF URL. Both open the browser through `LocalUriHandler`, wrapped in `runCatching` as in the Library.
5. **Abstract:** the `designsystem_abstract` heading and the full text, or `designsystem_no_abstract`.
6. **My notes:**
   - a heading with a trailing save-status line (§7.2);
   - six `OutlinedTextField`s in `NoteSection` order, each with its section name as the label and a hint as the placeholder;
   - the fields are multi-line and grow with their text, with a sentence-capitalized keyboard;
   - `TextDirection.Content`, so Arabic notes lay out right-to-left inside an English UI and the other way round;
   - the list scrolls the focused field above the keyboard (`imePadding`).

`PaperDetailsContent` is separate from `PaperDetailsScreen` so it can be tested and screenshotted without a ViewModel, as in the other features.

### 6.3 Library changes (`feature/library`)

- `LibraryViewModel` loses `selectedId`, `selectedPaper`, `onPaperClick` and `onDismissPreview`.
- `LibraryScreen` no longer shows `PaperPreviewSheet`, and a row tap calls `onOpenPaper`.
- Swipe to remove with Undo, the status badge menu, search and chips are unchanged.

### 6.4 Search changes (`feature/search`, `core/designsystem`)

- `PaperPreviewContent` / `PaperPreviewSheet` gain `onOpenDetails: (() -> Unit)? = null`. When it is non-null, an **Open details** button shows above the other buttons. Search passes it only when the paper is in the library.
- Everything else in the sheet is unchanged.

## 7. ViewModel and autosave

### 7.1 State

```kotlin
sealed interface PaperDetailsUiState {
    data object Loading : PaperDetailsUiState
    data class Loaded(val paper: LibraryPaper, val notes: PaperNotes, val saveState: NotesSaveState) : PaperDetailsUiState
}

enum class NotesSaveState { Idle, Saving, Saved, Failed }
enum class PaperDetailsMessage { NotesSaveFailed, StatusUpdateFailed }
```

Plus `message: StateFlow<PaperDetailsMessage?>` and `closeRequested: StateFlow<Boolean>`.

- The id comes from `SavedStateHandle.toRoute<PaperDetailsRoute>()`.
- The paper comes from `observePaper(id)`. When it emits null after a paper was shown (or as its first value), `closeRequested` becomes true and the screen calls `onBack`.
- **Notes are read once:** the ViewModel takes the first value of `observeNotes(id)` into its own `MutableStateFlow<PaperNotes?>`. Later database emissions are ignored, so they can't overwrite text being typed. The state stays `Loading` until both the paper and the notes are in.

### 7.2 Autosave

- `onNoteChange(section, text)` updates the local notes immediately and marks them as not saved.
- Local changes flow through `debounce(500)` → `distinctUntilChanged()` → a sequential `collect` in `viewModelScope`. Each value is saved with `saveNotes`. Writes run in order and are never cancelled halfway, and a burst of typing produces a single write.
- `saveState` works like this:
  - `Idle` until the first edit;
  - `Saving` while a write runs;
  - `Saved` after it succeeds;
  - `Failed` after it throws (`CancellationException` is rethrown).
- The line under the "My notes" heading shows nothing, **Saving…**, **Saved** or **Couldn't save** to match.
- `flushNotes()` writes the current notes at once if they differ from the last successful save. It runs on the injected `@ApplicationScope` `CoroutineScope`, so closing the screen can't cancel it. It is called:
  - when the screen's lifecycle reaches `ON_STOP` (backgrounding, navigating away);
  - from `onCleared`.
- `onRetrySave()` calls `flushNotes()`. A failed save doesn't block the next edit's save either.

### 7.3 Other actions

- `onStatusChange(status)`: calls `setStatus`, and on failure sets `StatusUpdateFailed`.
- `onRemove()`: flushes notes first, so Undo restores what was just typed, then calls the screen's `onRemove(id)`.

## 8. Strings (both locales)

In `feature/paperdetails`:

| Key | English | Arabic |
|---|---|---|
| `details_back` | Back | رجوع |
| `details_more_options` | More options | خيارات أخرى |
| `details_open_pdf` | Open PDF | فتح ملف PDF |
| `details_notes_title` | My notes | ملاحظاتي |
| `details_notes_saving` | Saving… | جارٍ الحفظ… |
| `details_notes_saved` | Saved | تم الحفظ |
| `details_notes_save_failed` | Couldn\'t save | تعذّر الحفظ |
| `details_notes_save_failed_message` | Couldn\'t save your notes | تعذّر حفظ ملاحظاتك |
| `details_retry` | Retry | إعادة المحاولة |
| `details_status_update_failed` | Couldn\'t update the status | تعذّر تحديث الحالة |
| `note_summary` | Summary | الخلاصة |
| `note_summary_hint` | What is this paper about, in your own words? | عمّ تتحدث هذه الورقة، بكلماتك أنت؟ |
| `note_research_question` | Research question | سؤال البحث |
| `note_research_question_hint` | What question or problem does it address? | ما السؤال أو المشكلة التي تعالجها؟ |
| `note_method` | Method | المنهجية |
| `note_method_hint` | How did the authors approach it? | كيف تناولها المؤلفون؟ |
| `note_key_findings` | Key findings | أهم النتائج |
| `note_key_findings_hint` | What did they find? | ما الذي توصّلوا إليه؟ |
| `note_limitations` | Limitations | القيود |
| `note_limitations_hint` | What are its weaknesses or open questions? | ما نقاط ضعفها أو الأسئلة التي تتركها مفتوحة؟ |
| `note_thoughts` | My thoughts | أفكاري |
| `note_thoughts_hint` | How does it relate to your work? | ما علاقتها ببحثك؟ |

In `core/designsystem`: `designsystem_open_details` with the values Open details and فتح التفاصيل.

The note's Summary is **الخلاصة**, because **الملخص** is already the paper's Abstract. Existing strings are reused for Open DOI, Remove from library, Abstract, No abstract, Untitled and the status labels.

## 9. Errors

| Case | Behaviour |
|---|---|
| Saving notes fails | The status line shows **Couldn't save**, and a snackbar "Couldn't save your notes" appears with **Retry**. The text stays in the fields, and the next edit also tries again. |
| Changing the status fails | A snackbar "Couldn't update the status". The selector shows the stored status. |
| The paper isn't saved (any more) | `observePaper` emits null, so the screen closes. `saveNotes` does nothing, so a flush after removal is harmless. |
| Process death | The id is restored from `SavedStateHandle`. Only typing after the last save and before `ON_STOP` can be lost. |
| Very long notes | No limit. Each section is one `TEXT` column, and FTS4 handles long text. |

## 10. Testing

All tests are written test-first and run on the JVM (Robolectric where Android is needed), with hand-written fakes.

- **`core/model`:**
  - `PaperNotes`: `get` / `with` for every section;
  - `isEmpty` treats whitespace-only sections as blank.
- **`core/database`:**
  - `PaperDaoTest`:
    - `saveNotes` creates the row, updates it, and deletes it when blank;
    - `paper_search.notes` follows every save;
    - it returns false for an unsaved paper;
    - a `MATCH` finds a paper by a word only in its notes, including folded Arabic (a note with tashkeel is found without it);
    - `observeByOpenAlexId` emits the paper, then null after a delete;
    - `deleteByOpenAlexId` returns the notes and leaves no notes row;
    - `insertPaperWithAuthors` with notes restores the row and the search column.
  - `MigrationTest`, 2 → 3: build a version 2 database with papers, authors and mixed statuses, then migrate. Every paper and status is kept, searches that worked before find the same papers, `notes` is empty, `paper_notes` exists, and the schema validates against `3.json`. The existing 1 → 2 test keeps passing, and a 1 → 3 run passes too.
- **`core/data`:**
  - `RoomLibraryRepositoryTest`:
    - `observePaper`;
    - `observeNotes` is empty with no notes;
    - save and read back;
    - `saveNotes` on an unsaved paper does nothing;
    - remove → restore keeps the notes and their searchability;
    - Library search by a note word.
  - `FakeLibraryRepositoryTest` covers the new members.
- **`feature/paperdetails`:**
  - `PaperDetailsViewModelTest`, using `MainDispatcherRule` and virtual time:
    - loading waits for both the paper and the notes;
    - typing doesn't save before 500 ms and saves once after it, and a burst saves once;
    - `saveState` goes `Idle → Saving → Saved`;
    - a failure gives `Failed` and `NotesSaveFailed`, and Retry saves again;
    - the flush on `ON_STOP` and on `onCleared` saves pending notes at once, and saves nothing when there is nothing new;
    - a later `observeNotes` emission doesn't overwrite typed text;
    - a failing status change sets `StatusUpdateFailed`;
    - the paper becoming null requests close;
    - `onRemove` flushes before calling back.
  - `PaperDetailsContentTest`:
    - the header shows every author;
    - Untitled and no-abstract cases render;
    - Open PDF shows only with a URL;
    - the six labels appear in order;
    - typing calls `onNoteChange` with the right section;
    - the save-status line shows each state;
    - the overflow's Remove calls back.
  - `PaperDetailsScreenshotTest`, English and Arabic × light and dark:
    - a paper with notes filled;
    - a paper with no notes and no abstract;
    - the **Couldn't save** state.

    Baselines are recorded on CI Linux.
- **`feature/library`:**
  - `LibraryViewModelTest` drops the preview tests;
  - `LibraryContentTest`: a row tap calls `onOpenPaper`, and no sheet appears;
  - screenshots are re-recorded only if something changed;
  - `LibrarySwipeUndoTest` is unchanged.
- **`feature/search` and `core/designsystem`:**
  - `PaperPreviewTest`: **Open details** shows only with `onOpenDetails`;
  - `SearchContentTest`: it opens Details for a saved paper;
  - `SearchViewModelTest`: `onRemove`.
- **`app`:** `HashiyaAppNavigationTest`:
  - Library row → Details → back;
  - Search sheet → Details;
  - Remove on Details → Library with the Undo snackbar, and Undo brings the paper back;
  - the bottom bar stays visible on Details.

## 11. Acceptance criteria (on device)

1. Install the sub-project 3 build, save three papers and set one to Reading, then install this branch over it without uninstalling. The app opens, every paper and status is still there, and Library search still finds them.
2. Tap a Library row. Details shows every author, the abstract and the status, and Open DOI / Open PDF open the browser.
3. Type in Summary and Method, wait a moment, and see **Saved**. Go back, reopen the paper, and the text is there.
4. Type a note, press Home right away, then force-stop the app. After reopening, the note is there.
5. In the Library, search for a word that is only in a note, and the paper is found. Do the same with an Arabic word typed without tashkeel against a note written with it.
6. Remove from the Details overflow menu. The app goes back to the Library with an Undo snackbar, and Undo brings the paper back with its notes and status.
7. In Search, open a saved paper's sheet and tap **Open details**, and Details opens. An unsaved paper's sheet has no **Open details**.
8. Switch the app to Arabic. The Details screen is right-to-left, the labels and hints are Arabic, and an English note stays left-to-right.

## 12. Risks

| Risk | Mitigation |
|---|---|
| The FTS rebuild loses or changes search rows | A copy through a temporary table with no re-normalization, and a migration test that compares searches before and after. |
| Notes are lost when leaving the screen | The debounce is combined with a flush on `ON_STOP` and `onCleared` on the application scope. ViewModel tests cover both. |
| The database overwrites text while typing | Notes are read once. Later emissions are ignored, and a test covers it. |
| The Remove result is handled twice or missed | The key is cleared after it is handled, and a navigation test covers Remove → Undo. |
| The Library's sheet removal breaks existing tests | Tests for the removed members are removed on purpose, and the navigation test covers the new path. |
| Room schema validation fails for the rebuilt FTS table | The `CREATE` statements are copied from the exported `3.json`, as in `MIGRATION_1_2`. |
