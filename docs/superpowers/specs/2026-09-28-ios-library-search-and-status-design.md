# iOS sub-project 3: Library search and reading status — Design

- **Date:** 2026-09-28
- **Status:** Awaiting review
- **Scope:** iOS counterpart of Android sub-project 3 (`2026-09-28-library-search-and-status-design.md`), matching the Android behaviour merged on `main` at `67e850c`. Builds on `2026-09-28-ios-foundation-openalex-search-design.md` ("spec 1") and `2026-09-28-ios-add-by-id-and-share-design.md` ("spec 2"); their platform decisions, package rules, string conventions, testing and CI apply unchanged.

## 1. Context

After specs 1 and 2 the iOS Library is a flat list, newest first. This spec makes it usable once it grows, exactly as Android sub-project 3 did: search saved papers by the words in their title, authors, abstract and venue (offline, Arabic-aware), and track each paper as **To read**, **Reading** or **Read**, filtering the list by status with live counts.

### Decisions

| Topic | Decision |
|---|---|
| Model | `ReadingStatus { toRead, reading, read }` and `LibraryPaper { paper, status }` in `HashiyaModel`. New saves start as To read. |
| Normalization | `searchableText(_:)`, identical to Android: NFKD (`decomposedStringWithCompatibilityMapping`), drop combining marks, drop tatweel, `أ إ آ ٱ` → `ا`, `ى` → `ي`, every decimal digit → ASCII, locale-independent lowercase. Android's test tables ported. |
| Schema | GRDB migration `v2`: `papers.reading_status TEXT NOT NULL DEFAULT 'to_read'`, stored as the fixed strings `to_read` / `reading` / `read` (an unknown value reads as To read). |
| Search index | FTS4 table `paper_search` (tokenizer `unicode61`, `paper_id` stored but not indexed) with title, authors, abstract, venue, all passed through `searchableText`; kept in step in the same write transactions as save, remove and restore; the migration backfills existing papers (authors in position order) calling `searchableText` directly. |
| Query builder | `ftsMatch(_:)`: letters and digits only; each word becomes `"word*"` (star inside the quotes — FTS4 treats `"word"*` as the exact word); every word must match; `nil` for blank. |
| Repository | `observeLibrary(query:status:)` emits **one consistent snapshot** — papers for query + status, per-status counts for the query, whole-library total — read in a single GRDB `ValueObservation` fetch, so the Android "No papers match" flash can't happen. `setStatus` doesn't reorder; saves start To read; Undo keeps the status. |
| Library screen | `.searchable` "Search your library" (300 ms debounce, Search key immediate); chips All · To read · Reading · Read with counts (English "Reading · 3", Arabic "قيد القراءة (٣)" — parentheses, because "·" next to Arabic-Indic digits reads like "٠"); the selected chip shows a check. |
| Status badge | A labelled pill on each row that opens a `Menu` (current status checked; Read's pill has a check icon; never colour alone; VoiceOver "Status: To read. Change status"). |
| Preview | A segmented `Picker` for the status, only in the Library's preview. |
| Empty states | Empty library (decided from the whole-library total) vs **No papers match** + **Clear search and filters**; the field and chips stay, so the keyboard stays. |
| Failures | A status write failure shows the banner "Couldn't update the status". |
| Restoration | Query and chip in `@SceneStorage`. |
| Strings | Android's keys and texts in both languages. |

## 2. Goals and non-goals

### Goals

1. Searching the Library finds saved papers by words in the title, author names, abstract or venue, including prefixes ("transf" finds "transformer"), offline.
2. Arabic search ignores tashkeel, tatweel and alef/yaa variants and matches Arabic-Indic and ASCII digits either way; Latin search ignores case and accents.
3. Every saved paper has a reading status that can be changed from its row or its preview, and survives removal + Undo.
4. Status chips filter the list and show how many papers of each status match the current search.
5. Existing libraries upgrade in place: no saved paper is lost, and every existing paper is searchable after the upgrade.

### Non-goals

- Sort options (the order stays newest saved first).
- Reading status in Search results.
- Notes and PDF text in the index (later sub-projects).
- Stemming or root-based Arabic matching; reading statistics.

## 3. Model (`HashiyaModel`)

```swift
public enum ReadingStatus: String, CaseIterable, Sendable { case toRead, reading, read }   // new saves start as .toRead
public struct LibraryPaper: Equatable, Hashable, Sendable { public var paper: Paper; public var status: ReadingStatus }
```

`RemovedPaper` (in `HashiyaData`) gains `status: ReadingStatus`, so Undo restores it.

## 4. Search text normalization

In `HashiyaModel/SearchableText.swift`, next to spec 2's `withoutArabicMarks`:

```swift
/// Lowercased text with accents and marks removed, Arabic letter variants unified and digits in ASCII, for full-text search.
public func searchableText(_ text: String) -> String
```

In this order, on Unicode scalars:

1. NFKD: `text.decomposedStringWithCompatibilityMapping`.
2. Remove every scalar whose general category is a mark (Mn, Mc, Me): Latin accents and Arabic tashkeel.
3. Remove tatweel U+0640.
4. Replace U+0623 `أ`, U+0625 `إ`, U+0622 `آ`, U+0671 `ٱ` with U+0627 `ا`; replace U+0649 `ى` with U+064A `ي`.
5. Replace every scalar whose numeric type is decimal (general category Nd, e.g. Arabic-Indic `١٩`, Persian `۱۹`) with the ASCII digit of its value.
6. Lowercase with `lowercased()`, which in Swift doesn't depend on the device locale (a Turkish device still maps `TITLE` to `title`).

Used both when writing the index and when building a query, so the two always agree. The `titlesMatch` normalization of spec 2 has a different purpose and stays separate.

Tests (ported from `SearchableTextTest.kt`):

| Input | Expected |
|---|---|
| `Schrödinger` (composed), `schrodinger`, `SCHRÖDINGER`, `Schro\u{0308}dinger` (decomposed) | all `schrodinger` |
| `Café Naïve` | `cafe naive` |
| `التَّعلُّم`, `التعلم`, `اَلتَّعَلُّمُ` | all `التعلم` |
| `العـــربية` | `العربية` |
| `أحمد` | `احمد` |
| `إسلام` | `اسلام` |
| `آية` | `اية` |
| `ٱلكتاب` | `الكتاب` |
| `مستشفى`, `مستشفي` | both `مستشفي` |
| `تعلُّم الآلة Machine LEARNING` | `تعلم الالة machine learning` |
| `١٩٨٤` | `1984` |
| `۱۹۸۴` | `1984` |
| `كوفيد-١٩` | `كوفيد-19` |
| (empty) | (empty) |
| `-- !? ()` | `-- !? ()` |
| `TITLE` | `title` |

## 5. Database (`HashiyaDatabase`), migration `v2`

`HashiyaDatabase` now imports `HashiyaModel` (for `searchableText`), as spec 1 §3.2 anticipated.

### 5.1 Migration `"v2"`

Registered after `"v1"` in the same `DatabaseMigrator`; it runs in one transaction:

1. `ALTER TABLE papers ADD COLUMN reading_status TEXT NOT NULL DEFAULT 'to_read'`
2. `CREATE VIRTUAL TABLE paper_search USING fts4(paper_id, title, authors, abstract, venue, tokenize=unicode61, notindexed=paper_id)`
   - **Requirement:** `paper_id` is stored but **not indexed**, so a search never matches a paper's local UUID. The raw SQL above is used rather than GRDB's FTS4 table builder so this option is certain.
3. Backfill: read `SELECT paper_id, name FROM paper_authors ORDER BY paper_id, position` into a map, then for each `SELECT id, title, abstract, venue FROM papers` insert one `paper_search` row built by `PaperSearchRow.make(paperID:title:authorNames:abstract:venue:)` — `searchableText(title)`, `searchableText(authorNames.joined(separator: " "))`, `searchableText(abstract ?? "")`, `searchableText(venue ?? "")`. The same function builds the row for new saves, so migrated and new rows are identical.

There is no destructive fallback; a failing migration surfaces as an error when opening the database, and the migration test guards it.

### 5.2 Stored status values

| `ReadingStatus` | Stored |
|---|---|
| `.toRead` | `to_read` |
| `.reading` | `reading` |
| `.read` | `read` |

The mapping lives in `HashiyaData` (`ReadingStatus.storedValue`, `ReadingStatus(stored:)`). Stored values are fixed strings, never Swift case names, so renaming a case never changes data. Any other stored value (e.g. `archived`, `""`, `ToRead`) reads as `.toRead` and is counted as To read.

### 5.3 `PaperStore` changes

All in single transactions:

| Operation | Behaviour |
|---|---|
| `insert(paper:authors:search:) -> Bool` | as before, plus the search row, inserted only when the paper was inserted; precondition `search.paperID == paper.id` |
| `deleteByOpenAlexID(_:)` | also `DELETE FROM paper_search WHERE paper_id = ?` (FTS rows don't cascade) |
| `setStatus(openAlexID:status:) -> Int` | `UPDATE papers SET reading_status = ? WHERE open_alex_id = ?`; returns rows changed (0 when not saved); the index is not touched |
| `librarySnapshot(db, match: String?, status: String?)` | one function reading, in the same transaction: (a) the papers query below with their authors, (b) the counts query below, (c) `SELECT COUNT(*) FROM papers` |

```sql
-- (a) papers: newest saved first
SELECT papers.* FROM papers
WHERE (:match IS NULL OR papers.id IN (SELECT paper_id FROM paper_search WHERE paper_search MATCH :match))
  AND (:status IS NULL OR papers.reading_status = :status)
ORDER BY papers.saved_at DESC;

-- (b) counts for the same match; statuses with none are absent
SELECT reading_status, COUNT(*) AS count FROM papers
WHERE (:match IS NULL OR papers.id IN (SELECT paper_id FROM paper_search WHERE paper_search MATCH :match))
GROUP BY reading_status;
```

`observeSavedPapers()` from spec 1 is removed; its callers move to the snapshot.

## 6. Query builder (`HashiyaData/Search/FtsQuery.swift`)

```swift
/// The FTS MATCH expression for what the user typed, or nil when no word is left ("no search").
func ftsMatch(_ query: String) -> String?
```

1. `searchableText(query)`.
2. Split on every run of scalars that are neither letters (Lu, Ll, Lt, Lm, Lo) nor numbers (Nd, Nl, No); drop empty parts. So quotes, `*`, `-`, parentheses, `:`, `^`, `/` never reach `MATCH`, and `AND`/`OR`/`NOT`/`NEAR` become ordinary lowercase words.
3. No words → `nil`. Otherwise each word becomes `"word*"` (the star **inside** the quotes: FTS4 reads a prefix only there; `"word"*` would match the exact word), joined with single spaces, so every word must match.

Tests (ported from `FtsQueryTest.kt`):

| Input | Expected |
|---|---|
| `transf` | `"transf*"` |
| `  Deep   LEARNING ` | `"deep*" "learning*"` |
| `Schrödinger` | `"schrodinger*"` |
| `التَّعلُّم` | `"التعلم*"` |
| `أحمد` | `"احمد*"` |
| `١٩` | `"19*"` |
| `C++` | `"c*"` |
| `"attention` | `"attention*"` |
| `title:deep` | `"title*" "deep*"` |
| `-bert (gpt*)` | `"bert*" "gpt*"` |
| `Ming-Wei` | `"ming*" "wei*"` |
| `cats AND dogs` | `"cats*" "and*" "dogs*"` |
| `OR NOT NEAR` | `"or*" "not*" "near*"` |
| (empty), `   `, `"*-():`, `ـــ` | `nil` |

## 7. Repository (`HashiyaData`)

```swift
public struct LibrarySnapshot: Equatable, Sendable {
    public var papers: [LibraryPaper]            // match query and status; newest saved first
    public var counts: [ReadingStatus: Int]      // match query only; all three keys present, missing = 0
    public var libraryTotal: Int                 // the whole library, ignoring query and status
    public var matchingTotal: Int { counts.values.reduce(0, +) }   // the All chip
}

public protocol LibraryRepository: Sendable {
    /// One consistent snapshot per database change. Blank query = all; nil status = all.
    func observeLibrary(query: String, status: ReadingStatus?) -> AsyncStream<LibrarySnapshot>
    func observeSavedIDs() -> AsyncStream<Set<String>>
    func save(_ paper: Paper) async throws                              // starts as To read; already saved → no-op
    func setStatus(openAlexID: String, status: ReadingStatus) async throws   // doesn't reorder; unsaved → no-op
    func remove(openAlexID: String) async throws -> RemovedPaper?
    func restore(_ removed: RemovedPaper) async throws                  // same id, saved_at and status
    func refreshAfterExternalChanges() async                            // spec 2
}
public struct RemovedPaper: Equatable, Sendable {
    public var paper: Paper; public var localID: String; public var savedAt: Int64; public var status: ReadingStatus
}
```

- `observeLibrary` computes `ftsMatch(query)` once and runs one `ValueObservation.tracking { db in try PaperStore.librarySnapshot(db, match:, status:) }` — inside `PaperStore.observeLibrary(match:status:)`, which returns an `AsyncStream<LibraryRows>`, because `HashiyaData` never imports GRDB; the repository maps the rows to `LibrarySnapshot`. GRDB runs the tracking closure in a single read, so papers, counts and total always describe the same database state; a write that empties the library produces exactly one snapshot with no papers, zero counts and `libraryTotal == 0`.
- Counts: each stored value maps through `ReadingStatus(stored:)` and is added to that status (unknown values are counted as To read); statuses with no rows are `0`.
- Saves made by the Share Extension (spec 2) start as To read and are indexed by the same `PaperStore.insert`.

## 8. Library screen (`FeatureLibrary`)

### 8.1 `LibraryViewModel`

```swift
public enum LibraryState: Equatable {
    case loading
    case empty                                   // nothing saved; field and chips hidden
    case noMatches(LibraryFilter)                // papers exist, none match
    case papers([LibraryPaper], LibraryFilter)
}
public struct LibraryFilter: Equatable {
    public var query: String                     // as typed
    public var status: ReadingStatus?            // nil = All
    public var counts: [ReadingStatus: Int]
    public var total: Int { counts.values.reduce(0, +) }
}
```

- `text` is what the field shows; `appliedQuery` follows it after 300 ms, at once on the keyboard's Search key, and at once whenever the text becomes empty (iOS's clear button, or deleting everything). `status` changes apply at once.
- Each (`appliedQuery`, `status`) pair cancels the previous observation task and starts `observeLibrary`. Until the new stream's first snapshot, the current state stays on screen (no loading flash between keystrokes); `loading` is shown only before the very first snapshot.
- For each snapshot: `libraryTotal == 0` → `.empty`; papers non-empty → `.papers`; else `.noMatches`. The filter carries the typed `text`, the selected `status` and the snapshot's `counts`. There is no stale-list/stale-counts case to handle (§14).
- `clearSearchAndFilters()` sets text and applied query to `""` and status to `nil`.
- `setStatus(paper, status)` calls the repository; a thrown error sets `message = .statusUpdateFailed`, shown as a `HashiyaBanner` with `library.statusUpdateFailed` for 4 s; the badge keeps showing the stored status because it comes from the snapshot.
- Preview: `selectedPaper` observes `observeLibrary(query: "", status: nil)` and picks the selected ID, so a status change that moves the paper out of the current chip keeps the sheet open with the new status; the sheet closes if the paper is removed.
- Remove, Undo, "only the latest removal can be undone" and expiry are unchanged from spec 1; Undo restores the status too.
- Restoration: `@SceneStorage("library_query")` (text) and `@SceneStorage("library_status")` (`toRead`, `reading`, `read`, absent = All). Restored text is applied at once.

### 8.2 Layout

Top to bottom, inside the Library's `NavigationStack`:

1. Navigation title and gear (unchanged).
2. Search field: `.searchable(text:placement: .navigationBarDrawer(displayMode: .always), prompt: library.searchHint)`, `.onSubmit(of: .search)` applies at once and hides the keyboard. Present in `.papers` and `.noMatches`, absent in `.empty` and `.loading`.
3. Chip row (horizontal `ScrollView`, 12 pt side padding, 8 pt spacing, single selection): **All**, **To read**, **Reading**, **Read**, each labelled with `library.filterCount` (label, count) — "Reading · 3" in English, "قيد القراءة (٣)" in Arabic; the All chip shows `total`. Counts are formatted with the locale's number format (`PaperFormat.number`), so Arabic shows Arabic-Indic digits where the user's region uses them; iOS's plain `ar` locale uses Latin digits ("قيد القراءة (3)"). The parentheses are used either way. The selected chip has the `PrimaryContainer` fill **and** a leading `checkmark`, and exposes `.isSelected` to VoiceOver.
4. Body:
   - `.papers`: the count line (`library.paperCount` for the listed papers), then the rows from spec 1 with a status badge at the trailing end. Swipe-to-remove, Undo, the Add paper button and the 88 pt bottom margin are unchanged. When a status filter is active and a paper's status changes out of it, the row leaves the list.
   - `.noMatches`: `EmptyStateView` (`doc.text.magnifyingglass`), title `library.noMatchesTitle`, no message, button `library.noMatchesAction` → `clearSearchAndFilters()`.
   - `.empty`: unchanged from spec 1 (`library.emptyTitle`, `library.emptyMessage`, **Go to Search**, plus Add paper).
   - `.papers` and `.noMatches` render inside the same container view with the same `.searchable` and chip row, so moving between them never rebuilds the field and the keyboard stays up while typing.

### 8.3 Status badge (`ReadingStatusBadge`, `FeatureLibrary`)

- A capsule pill, 10×4 pt padding, `label` font, text from `readingStatusLabel(_:)`:
  - **To read**: no fill, 1 pt `Outline` border, `OnSurfaceVariant` text.
  - **Reading**: `PrimaryContainer` fill, `OnPrimaryContainer` text.
  - **Read**: `SurfaceContainerHighest` fill, `OnSurface` text, a leading 14 pt `checkmark`.
- The pill is the label of a `Menu` containing a `Picker` over the three statuses (the current one shows a checkmark); choosing a different one calls `setStatus` at once, with no banner; choosing the current one does nothing.
- Inside a `List` row, the row's tap target (open preview) and the badge's `Menu` are separate controls (`.buttonStyle(.borderless)` on the row content) so tapping the badge never opens the preview.
- Accessibility: the badge's label is `library.statusBadgeDescription` with the status label ("Status: To read. Change status"); status is never conveyed by colour alone.

### 8.4 Preview selector

`PaperPreviewContent` (`HashiyaDesignSystem`) gains `status: ReadingStatus? = nil` and `onStatusChange: (ReadingStatus) -> Void = { _ in }`. When `status` is non-nil it shows a segmented `Picker` (To read · Reading · Read, labels from `readingStatusLabel`) above the buttons, 16 pt above and below. The Library passes the status; Search and the Share Extension pass nothing and look as before. `ReadingStatusSelector` and `readingStatusLabel(_:)` live in `HashiyaDesignSystem`, the one place statuses get their names (badge, menu, chips, selector).

## 9. Strings

Same key convention as spec 1 §10.

`HashiyaDesignSystem`:

| Key | English | Arabic |
|---|---|---|
| `status.toRead` | To read | للقراءة |
| `status.reading` | Reading | قيد القراءة |
| `status.read` | Read | مقروءة |

`FeatureLibrary`:

| Key | English | Arabic |
|---|---|---|
| `library.searchHint` | Search your library | ابحث في مكتبتك |
| `library.filterAll` | All | الكل |
| `library.filterCount` | %1$@ · %2$@ | %1$@ (%2$@) |
| `library.statusBadgeDescription` | Status: %1$@. Change status | الحالة: %1$@. تغيير الحالة |
| `library.noMatchesTitle` | No papers match | لا توجد أوراق مطابقة |
| `library.noMatchesAction` | Clear search and filters | مسح البحث والفلاتر |
| `library.statusUpdateFailed` | Couldn't update the status | تعذّر تحديث الحالة |

`library.filterCount` takes the label and the formatted count. The Arabic form uses parentheses because a "·" beside Arabic-Indic digits reads like "٠".

Android string not used on iOS: `library_search_clear` (iOS supplies the search field's clear button).

## 10. Errors

| Situation | Result |
|---|---|
| Search text with FTS syntax or only punctuation | Treated as plain words, or as no search. Never an error. |
| Status write fails | Banner "Couldn't update the status"; the badge keeps the stored status. |
| Unknown stored status value | Read and counted as To read. |
| Migration fails | Opening the database throws; the app shows no library rather than deleting data. The migration test guards this. |

## 11. Testing

| Target | Coverage |
|---|---|
| `HashiyaModelTests` | The `searchableText` table (§4) |
| `HashiyaDatabaseTests` | In-memory database migrated to v2: search by title, author, abstract and venue (`"attention*"`, `"vaswani*"`, `"recognition*"`, `"cvpr*"` each find their paper; `"transformer*"` finds none); prefixes match longer words and every word must match (`"transf*"` → both, newest first; `"transf*" "lang*"` → one); the local id isn't searchable (`zzlocalid`); search + status; counts follow the match (`reading`: 2 / `to_read`: 1 unfiltered; 1/1 for `"transf*"`; none for `"missing*"`); `setStatus` keeps order, doesn't touch the index (row count unchanged) and returns 1, or 0 for an unknown paper; delete removes the search row; restore re-adds it. **Migration**: create a database migrated only to `"v1"` (`migrate(_:upTo:)`), insert paper `a` (`W1`, DOI `10.48550/arxiv.1706.03762`, "Attention Is All You Need", 2017, venue "Neural Information Processing Systems", abstract "The dominant sequence transduction models", 128412 citations, open access, saved_at 100) with authors inserted position 1 "Noam Shazeer" **before** position 0 "Ashish Vaswani", and paper `b` (`W2`, title `تطبيقات التَّعلُّم العميق`, no authors, abstract or venue, saved_at 200); migrate to v2; expect statuses `a:to_read`, `b:to_read`, authors in position order, index rows `a:attention is all you need:ashish vaswani noam shazeer` and `b:تطبيقات التعلم العميق:`; the migrated library lists `b, a` and is searchable with `"attention*"`, `"shazeer*"`, `"transduction*"`, `"neural*" "processing*"` and `"التعلم*"` |
| `HashiyaDataTests` | `ftsMatch` table (§6); stored status values fixed and read back; unknown values (`archived`, `""`, `ToRead`) read as To read. Repository on an in-memory database: newest first with authors in order, starting To read; saving twice keeps one copy and its status; remove + restore returns the paper to its position with its status; restore after a re-save is a no-op; `setStatus` doesn't reorder; `setStatus` of an unsaved paper does nothing; search by prefix across fields (`transf`, `kaiming`, `neurips`, `DEEP resid`; `attention residual` → none; blank → all); accents, tashkeel and alef forms (`schrodinger`, `التَّعلُّم`, `اساسيات`, `الاحصاء`); digits either way (`COVID-19 outcomes` and `جائحة كوفيد-١٩` both found by `١٩` and by `19`; `كوفيد ۱۹` finds the Arabic one); hostile input through real SQLite never throws — `"`, `C++`, `templates"`, `-templates`, `-x`, `(guide`, `title:guide`, `BERT:`, `a AND`, `NEAR/2`, `*`, `^x` — and `"templates` and `*` still find `C++ templates: a guide`; counts fill missing statuses with 0 (`{toRead: 2, reading: 1, read: 0}` unfiltered, `{1, 1, 0}` for `transf`, all 0 for `missing`); an unknown stored status reads and counts as To read; **snapshot consistency**: removing the only paper emits no snapshot where papers and counts disagree (every emitted snapshot has `papers.count` equal to the count for its status filter and `libraryTotal >= papers.count`) |
| `FeatureLibraryTests` | Empty library; papers newest first with statuses and counts; typing searches after 300 ms; Search key applies at once; emptying the text applies at once; chip and search combine; No matches when the library has papers but none match; Clear search and filters resets both; restores text and chip from scene storage; a status change updates the list and counts; a status change out of the chip keeps the preview open; a status change failure shows the message and keeps the stored status; select and dismiss preview; removing offers Undo and closes the preview; removing the last paper during a search shows Empty; removing and restoring the only paper never shows No matches; Undo restores the paper in place with its status; two quick removals keep only the latest; dismissing Undo forgets the paper |
| `HashiyaDesignSystemTests` | The preview selector appears only with a status; choosing a segment calls `onStatusChange` |
| Snapshots | English/Arabic × light/dark: library with chips and badges (all three statuses, an Arabic title); a filtered search; no matches; the three badge styles (a SwiftUI `Menu` popup is a separate system window that swift-snapshot-testing 1.19.6 can't capture, so the open menu's items and selection are asserted in `LibraryFlowTests`); the preview with its selector. Spec 1 and 2 Library baselines are re-recorded. |
| `HashiyaUITests` | Library tab: with two sample papers saved, change one to Reading from its badge, see the Reading chip count become 1, filter by Reading, search a word from the other paper's title, see No papers match, tap Clear search and filters, see both papers |

## 12. Acceptance criteria (on a device or simulator)

1. Installing over the spec 2 build keeps every saved paper, each marked To read.
2. Searching the Library for an author's surname, or a word from an abstract, finds the paper; "transf" finds "transformer"; this works in airplane mode.
3. Changing a status from the badge's menu and from the preview's selector updates the chip counts at once.
4. Chips combine with the search; "No papers match" → **Clear search and filters** resets both; while typing into a search with no matches, the keyboard stays up.
5. Removing a Reading paper and tapping **Undo** brings it back as Reading, in its place.
6. In العربية, a search with or without tashkeel finds the same papers, `١٩` and `19` find the same papers, the chips read like "قيد القراءة (٣)", and the layout mirrors.
7. A paper saved from the Share Extension appears as To read and is found by the Library search.
8. VoiceOver reads a row's badge as "Status: To read. Change status" and announces the selected chip.
9. CI is green with re-recorded snapshot baselines.

## 13. Risks

| Risk | Mitigation |
|---|---|
| The first real migration breaks existing installs | Migration test from a v1 database with Android's fixture rows, plus acceptance check 1 on a real upgrade |
| The index drifts out of step with `papers` | Every write goes through `PaperStore` transactions (app and extension); tests cover every write path |
| Arabic search quality | Normalization covers common variants; stemming is a non-goal |
| Very large libraries | FTS4 queries stay fast for thousands of papers; counts use one grouped query inside the same snapshot read |
| `.searchable` rebuilt when the state changes | `.papers` and `.noMatches` share one container; covered by the keyboard acceptance check |

## 14. Differences from Android

| Area | Android | iOS | Why |
|---|---|---|---|
| Library data flow | Separate flows for the list, the counts and a whole-library emptiness query, combined in the ViewModel | One `LibrarySnapshot` per change from a single `ValueObservation` read | GRDB can read all three in one transaction; the snapshot is always consistent |
| Stale-state follow-ups | Three rules: a separate emptiness query decides Empty; an empty list with no search and no chip counts as Empty before that query answers; a list the counts disagree with is treated as stale and the last state is kept | Not needed | A single consistent snapshot can't disagree with itself, so "No papers match" can't flash |
| Clearing the search | The ✕ button applies at once; deleting the text by hand waits 300 ms | Any change to empty text applies at once | `.searchable`'s clear button can't be told apart from deleting |
| Search field | Custom field with ✕ (`library_search_clear`) | `.searchable`; iOS supplies the clear button | System search UI |
| Status menu | Material dropdown with a check icon | SwiftUI `Menu` with a `Picker` (system checkmark) | Platform menu |
| Preview selector | Material segmented buttons | Segmented `Picker` | Platform control |
| Status write failure | Snackbar | `HashiyaBanner` | No system snackbar |
| Saved state | `SavedStateHandle` keys `library_query`, `library_status` (enum names) | `@SceneStorage` with the same keys; values `toRead`, `reading`, `read` | Platform mechanism |
| FTS table creation | Room `@Fts4` entity + exported schema | Raw `CREATE VIRTUAL TABLE … fts4(…)` in the migration | Guarantees `notindexed=paper_id` |
| Unused Android string | — | `library_search_clear` | System clear button |
