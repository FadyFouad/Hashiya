# Sub-project 3: Library search and reading status — Design

- **Date:** 2026-09-28
- **Status:** Awaiting review
- **Scope:** Third of six MVP sub-projects for Hashiya (builds on sub-project 1, merged in `3f721a9`, and sub-project 2, merged in `1be1d8f`)

## 1. Context

Hashiya's core loop is **Discover → Save → Read → Extract → Compare → Cite**. Sub-projects 1 and 2 made discovering and saving papers work: OpenAlex search, adding a paper by DOI, arXiv ID, link or Android Share, and an offline library. The library is still a flat list, newest first. This sub-project makes it usable once it grows: find a saved paper by searching its text, and track where you are with each paper.

The same standards as before apply: modular architecture and dependency rules, English and Arabic with full RTL, TDD with hand-written fakes, Roborazzi screenshots with baselines recorded on CI Linux, and a green CI.

### Decisions made during brainstorming

| Topic | Decision |
|---|---|
| What search covers | The paper's own metadata: title, authors, abstract, venue. Notes (sub-project 4) and PDF text (sub-project 6) are later columns of the same index. |
| Search technology | SQLite full-text search through Room `@Fts4`, not `LIKE` and not in-memory filtering. |
| Reading statuses | Three: To read, Reading, Read. New saves start as To read. |
| Layout | One list: a search field, then status chips (All · To read · Reading · Read, with counts). Search and chip combine. |
| Changing status | From a status badge on each row (opens a small menu) and from a three-way selector in the preview sheet. Swipe stays remove + Undo. |
| Sort | Recently saved only. No sort menu. |

## 2. Goals and non-goals

### Goals

1. Searching the Library finds saved papers by words in the title, author names, abstract or venue, including word prefixes ("transf" finds "transformer"), offline.
2. Arabic search ignores tashkeel, tatweel and alef/yaa variants; Latin search ignores case and accents.
3. Every saved paper has a reading status that can be changed from its row or its preview, and survives removal + Undo.
4. Status chips filter the list and show how many papers of each status match the current search.
5. Existing libraries upgrade in place: no saved paper is lost, and every existing paper is searchable after the upgrade.

### Non-goals

- Sort options.
- Reading status in Search results.
- Notes and PDF text in the index (sub-projects 4 and 6).
- Stemming or root-based Arabic matching.
- Reading statistics ("papers read this month").

## 3. Model (`core/model`)

```kotlin
enum class ReadingStatus { ToRead, Reading, Read }

/** A paper in the user's library, with its reading status. */
data class LibraryPaper(val paper: Paper, val status: ReadingStatus)
```

`RemovedPaper` (in `core/data`) gains `status: ReadingStatus`, so Undo restores it.

## 4. Search text normalization

One function, used both when writing the index and when building a query, so the two always agree:

```kotlin
/** Lowercased text with accents and marks removed and Arabic letter variants unified, for full-text search. */
fun searchableText(text: String): String
```

It applies, in order:

1. Unicode NFKD, then removes every combining mark (`\p{M}`), which covers Latin accents and Arabic tashkeel.
2. Removes tatweel (`ـ`, U+0640).
3. Unifies Arabic letters: `أ إ آ ٱ` → `ا`; `ى` → `ي`.
4. Lowercases (locale-independent, `Locale.ROOT`).

It lives in `core/model` (`SearchableText.kt`), a pure-Kotlin module, because both `core/data` (indexing, queries) and `core/database` (the migration, §5.4) need it. `core/database` gains `implementation(project(":core:model"))` for this. The existing `normalizedTitle` in `core/data/lookup` has a different purpose (comparing whole titles) and stays separate.

## 5. Database (`core/database`), schema version 2

### 5.1 Reading status

`papers` gains `reading_status TEXT NOT NULL DEFAULT 'to_read'`. Stored values are fixed strings, not Kotlin enum names: `to_read`, `reading`, `read`. The mapping lives in `core/data`; an unknown stored value reads as To read.

### 5.2 Search index

```kotlin
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["paper_id"])
@Entity(tableName = "paper_search")
data class PaperSearchEntity(
    @ColumnInfo(name = "paper_id") val paperId: String,
    val title: String,
    val authors: String,   // author names joined with spaces
    val abstract: String,
    val venue: String
)
```

- A standalone FTS table (no `contentEntity`), because author names live in `paper_authors`. All indexed text is already passed through `searchableText`.
- The DAO keeps it in step inside the same transactions that write papers:
  - `insertPaperWithAuthors(paper, authors, search)` inserts the search row only when the paper was inserted.
  - Deleting a paper deletes its search row (`DELETE FROM paper_search WHERE paper_id = :id`); FTS rows don't cascade.
  - Status changes don't touch the index.

### 5.3 Queries

- **Library list:** papers joined with their authors (`@Transaction`, as today), filtered by an optional FTS match and an optional status, ordered by `saved_at DESC`:
  ```sql
  SELECT papers.* FROM papers
  WHERE (:match IS NULL OR papers.id IN (SELECT paper_id FROM paper_search WHERE paper_search MATCH :match))
    AND (:status IS NULL OR papers.reading_status = :status)
  ORDER BY papers.saved_at DESC
  ```
- **Counts per status** for the same optional match: `SELECT reading_status, COUNT(*) … GROUP BY reading_status`.
- **Set status:** `UPDATE papers SET reading_status = :status WHERE open_alex_id = :openAlexId`.

### 5.4 Migration 1 → 2

A `Migration(1, 2)` in `core/database` that:

1. `ALTER TABLE papers ADD COLUMN reading_status TEXT NOT NULL DEFAULT 'to_read'`.
2. Creates `paper_search` with the exact SQL Room generates for version 2 (taken from the exported `2.json`).
3. Reads every existing paper with its authors (ordered by `position`) and inserts one `paper_search` row each, with the text passed through `searchableText`.

The migration is registered in `DatabaseModule` with `addMigrations(MIGRATION_1_2)`. There is no destructive fallback.

### 5.5 Schema export

`core/database/schemas/…/2.json` is generated and committed. `1.json` stays for the migration test.

## 6. Repository (`core/data`)

```kotlin
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

    suspend fun remove(openAlexId: String): RemovedPaper?

    /** Puts a removed paper back where it was, with its status. */
    suspend fun restore(removed: RemovedPaper)
}
```

`observeSavedPapers()` is replaced by `observeLibrary("", null)`; its callers move over.

**Query builder** (`search/FtsQuery.kt`): `fun ftsMatch(query: String): String?`

- Normalizes with `searchableText`, then keeps only letters and digits (everything else becomes a space), so FTS syntax (`" * - ( ) :`, `AND`/`OR`/`NOT`/`NEAR` as operators) can't reach `MATCH`.
- Splits into words; each word becomes a quoted prefix term: `"transf"*`. Terms are joined with spaces, so every word must match.
- Returns `null` when no word remains, meaning "no search".

## 7. Library screen (`feature/library`)

### 7.1 Layout

Top to bottom:

1. The top bar (unchanged).
2. A search field, hint "Search your library", with a clear (✕) button. It uses `TextDirection.Content`, like Search's field. Typing applies after a 300 ms pause. The keyboard's Search key applies at once and hides the keyboard.
3. A single-select row of filter chips: **All · To read · Reading · Read**, each with its count (e.g. "Reading · 3"). All is the default. The row scrolls sideways. Counts use the app's number formatting, so Arabic shows Arabic-Indic digits.
4. The list, as today, with a status badge at the end of each row. The "Add paper" button, swipe-to-remove and Undo are unchanged.

### 7.2 Status badge and menu

- A small labelled pill: **To read** (outlined), **Reading** (teal container), **Read** (check icon + container). Status is never shown by colour alone.
- Tapping it opens a menu with the three statuses, the current one checked. Choosing one changes it at once; there is no snackbar.
- When a status filter is active, a paper whose status changes out of the filter leaves the list.
- Accessibility: the badge reads "Status: To read. Change status".

### 7.3 Preview sheet

`PaperPreviewContent` (in `core/designsystem`, shared with Search) gains `status: ReadingStatus? = null` and `onStatusChange: (ReadingStatus) -> Unit = {}`. When `status` is not null, it shows a single-choice segmented selector (To read · Reading · Read) above the buttons. The Library passes the status; Search passes nothing and looks the same as today.

### 7.4 Empty states

- **Empty library:** unchanged ("No saved papers yet" + Go to Search); the search field and chips are hidden.
- **No matches** (the library has papers, but the search and chip match none): "No papers match" with a **Clear search and filters** button that resets both.

### 7.5 ViewModel

- Holds `query` (as typed) and `status: ReadingStatus?`, both in `SavedStateHandle`.
- The applied query follows the typed one after 300 ms, or at once on the keyboard's Search key or on Clear.
- `uiState` combines `observeLibrary(applied, status)`, `observeStatusCounts(applied)` and the typed text through `flatMapLatest`. It is one of: `Empty` (library empty), `NoMatches`, `Papers(list)`.
- Existing selection, preview, removal and Undo keep working; the selected paper's status comes from the library flow.
- `onStatusChange(paper, status)` calls `setStatus`; a failure shows the snackbar "Couldn't update the status" (the pattern used for failed saves).

### 7.6 Strings (both locales)

| Key | English | Arabic |
|---|---|---|
| `library_search_hint` | Search your library | ابحث في مكتبتك |
| `library_filter_all` | All | الكل |
| `library_filter_count` | %1$s · %2$s (label · formatted count) | %1$s · %2$s |
| `library_status_badge_description` | Status: %1$s. Change status | الحالة: %1$s. تغيير الحالة |
| `library_no_matches_title` | No papers match | لا توجد أوراق مطابقة |
| `library_no_matches_action` | Clear search and filters | مسح البحث والفلاتر |
| `library_status_update_failed` | Couldn\'t update the status | تعذّر تحديث الحالة |

The status labels live once, in `core/designsystem` (used by the badge, the menu, the chips and the preview selector):

| Key | English | Arabic |
|---|---|---|
| `status_to_read` | To read | للقراءة |
| `status_reading` | Reading | قيد القراءة |
| `status_read` | Read | مقروءة |

The "All" chip shows the total of the three counts.

## 8. Errors

| Situation | Result |
|---|---|
| Search text with FTS syntax or only punctuation | Treated as plain words, or as no search. Never an error. |
| Status write fails | Snackbar "Couldn't update the status"; the badge keeps showing the stored status. |
| Unknown stored status value | Read as To read. |
| Migration fails | Room throws on open (a crash). There is no fallback that deletes data; the migration test guards this. |

## 9. Testing

- **`searchableText`** (`core/model`): case and Latin accents ("Schrödinger" ↔ "schrodinger"); Arabic tashkeel ("التَّعلُّم" ↔ "التعلم"), tatweel, alef forms and ى/ي; mixed Arabic and English; empty and punctuation-only input.
- **`ftsMatch`** (`core/data`): prefix terms, every word must match, FTS syntax characters and operator words neutralized, blank → null.
- **DAO** (Robolectric, in-memory Room): search by title, author, abstract and venue; prefix; search + status; counts per status for a search; the index stays in step through save, remove, restore and status change; removing a paper removes its search row.
- **Migration 1 → 2** (Room `MigrationTestHelper` under Robolectric): build a v1 database with papers and authors, migrate, then check statuses are To read, no data is lost, old papers are searchable by title and author, and the schema validates against `2.json`.
- **Repository:** saves start as To read; `restore` keeps the status; `setStatus` doesn't reorder; `observeStatusCounts` fills missing statuses with 0.
- **ViewModel:** debounce and immediate apply; chip + search combine; restore from `SavedStateHandle`; status change and its failure snackbar; Undo keeps the status; Empty vs NoMatches.
- **UI:** the badge menu changes status; the preview selector shows in Library and not in Search; No matches + Clear; chip counts.
- **Screenshots** (light/dark × English/Arabic, recorded on CI Linux): library with chips and badges, a filtered search, no matches, the badge menu open, the preview with its selector. Existing library screenshots change (badges, search field, chips) and are re-recorded.

## 10. Acceptance criteria (on device)

1. Installing over the sub-project 2 build keeps every saved paper, each marked To read.
2. Searching the Library for an author's surname, or a word from an abstract, finds the paper; "transf" finds "transformer".
3. Changing a status from the badge and from the preview updates the chip counts.
4. Chips combine with the search; "No papers match" → Clear resets both.
5. Removing a Reading paper and tapping Undo brings it back as Reading.
6. In العربية, a search with or without tashkeel finds the same papers, and the layout mirrors.

The README gains a feature line, and roadmap item 3 is marked done.

## 11. Risks

| Risk | Mitigation |
|---|---|
| The first real migration crashes existing installs | Migration test against the exported v1 schema, plus acceptance check 1 on a real upgrade. |
| The index drifts out of step with `papers` | All writes go through DAO transactions; tests cover every write path. |
| Arabic search quality | Normalization covers common variants; stemming is a non-goal. |
| Very large libraries | FTS4 queries stay fast for thousands of papers; counts use one grouped query. |
