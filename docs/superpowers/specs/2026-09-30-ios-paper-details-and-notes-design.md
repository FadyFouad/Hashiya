# iOS sub-project 4: Paper details and structured notes — Design

- **Date:** 2026-09-30
- **Status:** Awaiting review
- **Scope:** iOS counterpart of Android sub-project 4 (`2026-09-30-paper-details-and-notes-design.md`), matching the Android behaviour merged on `main` at `ab5f552`, including the review fixes in `8be6d6e` (each note field owns its text; Remove waits for a successful save). Builds on the iOS specs 1–3 (`2026-09-28-ios-foundation-openalex-search-design.md`, `2026-09-28-ios-add-by-id-and-share-design.md`, `2026-09-28-ios-library-search-and-status-design.md`) and the Liquid Glass work (`2026-09-29-ios-liquid-glass`); their platform decisions, package rules, string conventions, testing and CI apply unchanged.

## 1. Context

On iOS a saved paper is still only the Library's preview sheet. This spec starts **Extract** exactly as Android sub-project 4 did: every saved paper gets a Details screen with six fixed note sections, the notes save themselves, and the Library search finds papers by their notes.

### Decisions

| Topic | Decision |
|---|---|
| Note template | Six fixed sections, all optional plain text: Summary, Research question, Method, Key findings, Limitations, My thoughts (`NoteSection`, in that order). |
| Model | `NoteSection` and `PaperNotes` in `HashiyaModel`, as Android's. |
| Schema | GRDB migration `v3`: table `paper_notes` (Android's columns), and `paper_search` rebuilt with a `notes` column through a temporary copy, exactly as Android's `MIGRATION_2_3`. |
| Repository | `observePaper(openAlexID:)`, `notes(openAlexID:)` (one read), `saveNotes(openAlexID:notes:)`. `RemovedPaper` gains `notes`, so Undo brings them back. |
| Module | New package target and product `FeaturePaperDetails` (depends on `HashiyaData`, `HashiyaModel`, `HashiyaDesignSystem`, like the other features). |
| Entry points | A Library row tap pushes Details on the Library's `NavigationStack`, and the Library loses its preview sheet. Search's preview sheet gains **Open details** for saved papers only; it closes the sheet and pushes Details on the Search stack. |
| Navigation | `RootView` owns one `[PaperDetailsRoute]` path per tab and the `navigationDestination`. The tab bar is hidden on Details, as Android hides its navigation bar. |
| Editing | Always editable. Autosave 500 ms after typing stops, and a flush when the screen disappears and when the app goes inactive or to the background. No Edit or Save buttons. |
| Typing | Each note field keeps its own `@State` text, seeded once from the view model, and reports changes to it; typing never waits for the view model's state. |
| Notes read once | The view model reads the stored notes once (`notes(openAlexID:)`), so no database update can overwrite typing. |
| Remove | **Remove from library** in the Details **More options** menu. The view model first saves pending notes; only if that succeeds does the screen close and hand the removal to the screen below: the Library removes it with its Undo banner, Search removes it as its sheet's toggle does. If the save fails, the screen stays with **Couldn't save** and **Retry**. |
| Backgrounding | `RootView` suspends the shared database on `.background` (spec 2). A new `PendingWrites` tracker makes it wait for note writes already started before suspending, so the background flush is not refused by the suspended pool. |
| Look | English and Arabic with right-to-left layouts, light and dark, Liquid Glass on iOS 26 through the existing `Glass.swift` helpers, as in the rest of the app. |

## 2. Goals and non-goals

### Goals

1. Tapping a saved paper in the Library opens Details with every author, the full abstract, venue, year, citations, open access, the DOI and PDF links, and the reading status.
2. Each saved paper has six note sections, written on Details and saved without an explicit action.
3. The Library search finds a paper by words in its notes, with the same folding as the other columns (case, accents, tashkeel, alef/yaa variants, Arabic-Indic digits).
4. Removing a paper and tapping **Undo** brings back its notes with its status.
5. Existing libraries upgrade in place: no paper, status or search result is lost.

### Non-goals

Same as Android: rich text, custom sections, notes on unsaved papers, highlighting matches, exporting notes, a "has notes" marker on rows, Details for unsaved papers. Also on iOS:

- Restoring an open Details screen after the system ends the app. The Library and Search keep their `@SceneStorage` restoration; the notes themselves are never lost (§7.4).
- An iPad or multi-window layout (the app is iPhone only, `TARGETED_DEVICE_FAMILY: 1`).

## 3. Model (`HashiyaModel`)

```swift
public enum NoteSection: CaseIterable, Sendable { case summary, researchQuestion, method, keyFindings, limitations, thoughts }

/// The user's notes on a saved paper, one plain-text field per section. Text is kept exactly as typed.
public struct PaperNotes: Equatable, Hashable, Sendable {
    public var summary = "", researchQuestion = "", method = "", keyFindings = "", limitations = "", thoughts = ""
    public subscript(section: NoteSection) -> String { get set }
    public func with(_ section: NoteSection, _ text: String) -> PaperNotes
    /// True when every section is empty or whitespace (and newlines) only.
    public var isEmpty: Bool { get }
}
```

`NoteSection.allCases` sets the on-screen order.

## 4. Database (`HashiyaDatabase`), migration `v3`

### 4.1 Migration `"v3"`

Registered after `"v2"`, in one transaction, the same statements as Android's `MIGRATION_2_3` in SQLite form:

```sql
CREATE TABLE paper_notes (
  paper_id TEXT NOT NULL PRIMARY KEY REFERENCES papers(id) ON DELETE CASCADE,
  summary TEXT NOT NULL,
  research_question TEXT NOT NULL,
  method TEXT NOT NULL,
  key_findings TEXT NOT NULL,
  limitations TEXT NOT NULL,
  thoughts TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE TEMP TABLE paper_search_copy AS SELECT paper_id, title, authors, abstract, venue FROM paper_search;
DROP TABLE paper_search;
CREATE VIRTUAL TABLE paper_search USING fts4(paper_id, title, authors, abstract, venue, notes, tokenize=unicode61, notindexed=paper_id);
INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes)
  SELECT paper_id, title, authors, abstract, venue, '' FROM paper_search_copy;
DROP TABLE paper_search_copy;
```

- A notes row exists only while at least one section isn't blank.
- The copy keeps every search row as it was: nothing is re-normalized, and no paper has notes before `v3`.
- `paper_id` stays stored but not indexed.

### 4.2 Records

- `PaperNotesRecord` (`paper_notes`), with conversions `PaperNotesRecord(paperID:notes:updatedAt:)` and `.notes`.
- `PaperSearchRow` gains `notes: String`, and `make(…, notes: PaperNotes? = nil)` fills it with `PaperSearchRow.notesText(_:)`: `searchableText` over the six sections joined with spaces, `""` for nil. New saves, Undo and `saveNotes` all build it this way.

### 4.3 `PaperStore` changes

All in single transactions:

| Operation | Behaviour |
|---|---|
| `observePaper(openAlexID:) -> AsyncStream<PaperWithAuthors?>` | A `ValueObservation` of the paper and its authors; emits nil once it's deleted. |
| `notes(openAlexID:) async throws -> PaperNotesRecord?` | One read, joining `papers` on `paper_notes.paper_id = papers.id`. |
| `saveNotes(openAlexID:notes:updatedAt:) async throws -> Bool` | Finds the paper's local id; returns false, writing nothing, when it isn't saved. Upserts the notes row, or deletes it when `notes.isEmpty`. Then `UPDATE paper_search SET notes = ? WHERE paper_id = ?`, and `db.notifyChanges(in: Table("papers"))` so open Library observations fetch again. SQLite's update hook doesn't report virtual-table writes, so without this a Library search wouldn't see a new note until another change. A test (§11) covers it. |
| `deleteByOpenAlexID(_:) -> DeletedPaper?` | Now returns `DeletedPaper(paper: PaperWithAuthors, notes: PaperNotesRecord?)`. It reads the notes before the delete, which cascades them, and still deletes the search row by hand. |
| `insert(paper:authors:search:notes:) -> Bool` | New `notes: PaperNotesRecord? = nil`, written when the paper is inserted; precondition that `search.notes` already holds its search text. |
| `notifyExternalChanges()` | Also notifies `paper_notes`. |

## 5. Repository (`HashiyaData`)

```swift
public protocol LibraryRepository: Sendable {
    // … spec 3 members unchanged …
    /// The saved paper with its status; nil when it isn't saved or stops being saved. Starts with the current value.
    func observePaper(openAlexID: String) -> AsyncStream<LibraryPaper?>
    /// The paper's notes, read once; empty when it has none or isn't saved.
    func notes(openAlexID: String) async throws -> PaperNotes
    /// Saves the notes (blank notes delete them) and updates the search index. Not saved → no-op.
    func saveNotes(openAlexID: String, notes: PaperNotes) async throws
}
public struct RemovedPaper { /* … */ public var notes: PaperNotes }   // `restore` writes them back, search column included
```

- `saveNotes` and `restore` use the repository's `now()` for `updated_at`. Nothing reads it yet, so a restore doesn't keep the original value (as Android).
- `FakeLibraryRepository` (`HashiyaTesting`) implements the new members. It keeps notes per paper, drops them on `remove`, returns them in `RemovedPaper`, and finds a paper by its notes in its stand-in search. It gains the switches `setFailSaveNotes(_:)` and `setFailNotesRead(_:)`, and `saveNotesCalls` records every write for the tests.

## 6. `PendingWrites` (`HashiyaData`)

```swift
/// Note writes that must finish before the app suspends the shared database.
@MainActor public final class PendingWrites {
    /// Runs `work` in a task that isn't cancelled with any view, and tracks it until it ends.
    public func run(_ work: @escaping @Sendable () async -> Void)
    /// Returns once every tracked write has ended.
    public func drained() async
}
```

- The Details view model starts each write through it.
- `RootView`'s `.background` handler becomes `Task { await pendingWrites.drained(); if still in the background, SharedLibraryDatabase.suspend() }`. A scene phase generation counter makes sure a return to `.active` in the meantime isn't followed by a late suspend.
- Registering a write is synchronous on the main actor, and the drain task runs on a later main-actor turn. So a flush started by the Details screen for the same phase change is always included, whichever `onChange` SwiftUI calls first.
- The app has about 5 s after backgrounding. A notes write takes milliseconds, and nothing else is tracked.

## 7. Details screen (`FeaturePaperDetails`)

### 7.1 Navigation (app target)

```swift
public struct PaperDetailsRoute: Hashable, Codable, Sendable { public var openAlexID: String }   // FeaturePaperDetails
```

- `RootView` keeps `@State var libraryPath: [PaperDetailsRoute]` and `searchPath`. Each tab's `NavigationStack(path:)` gets `.navigationDestination(for: PaperDetailsRoute.self)`, which builds `PaperDetailsScreen` with a view model from `AppContainer.makePaperDetailsViewModel(openAlexID:)`.
- `PaperDetailsScreen` holds its view model in `@State`, created once per pushed screen.
- `LibraryView` gains `onOpenPaper: (String) -> Void`, which a row tap calls; `RootView` appends the route. `SearchView` gains the same callback for **Open details**: it sets `selectedPaper = nil` (closing the sheet), then calls `onOpenPaper`.
- **Remove:** the screen calls `onRemove(openAlexID)`. `RootView` pops that tab's path, then calls `libraryViewModel.remove(openAlexID:)`, which works like a swipe with the Undo banner, or `searchViewModel.remove(openAlexID:)`, which works like the sheet's toggle (`removeFailed` on failure).
- **Closed:** when the paper stops being saved (for example, removed through the Share Extension), the screen pops itself.
- The tab bar is hidden on Details: `.toolbar(.hidden, for: .tabBar)`.

### 7.2 Layout

`PaperDetailsContent(state:actions:)` is the testable body, separate from `PaperDetailsScreen` (view model, scene phase, navigation), as in the other features. One `ScrollView` with a `VStack` rather than a `List`, so the text fields keep focus. From top to bottom:

1. **Navigation bar:** the system back button, an inline empty title (the header shows it), and a trailing `Menu` (**More options**, `ellipsis.circle`) with one destructive item, **Remove from library**. On iOS 26 the bar and the menu are system glass.
2. **Header:**
   - the title in `previewTitle` style (**Untitled** when empty), marked as a header for VoiceOver;
   - every author, comma-separated;
   - `PaperFormat.previewMeta` (venue · year · citations);
   - the open-access `StatusBadge`.

   All paper text goes through `PaperText`, so it lays out in its own direction.
3. **Reading status:** the existing `ReadingStatusSelector`.
4. **Links:** **Open DOI** when there is a DOI and **Open PDF** when there is an open-access PDF URL. They are side by side, `hashiyaSecondaryButton()`, inside a `HashiyaGlassGroup`, and open through `openURL`.
5. **Abstract:** the `designsystem.abstract` heading and the full text, or `designsystem.noAbstract`.
6. **My notes:**
   - A heading with the save-status line at its trailing edge: nothing, **Saving…**, **Saved** or **Couldn't save**.
   - Six `NoteField`s in `NoteSection` order. Each has its section name as a visible label above a `TextField(…, axis: .vertical)`, the hint as the prompt, `.textInputAutocapitalization(.sentences)`, and a rounded 1 pt `outline` border (2 pt `primary` while focused), growing with its text.
   - Each field lays out in its text's own direction through `ContentDirection.of`, falling back to the UI's. So an Arabic note is right-to-left in an English UI and an English note left-to-right in an Arabic one, as Android's `TextDirection.Content`.
   - A keyboard toolbar **Done** button ends editing, since Return adds a new line. The scroll view dismisses the keyboard interactively and keeps the focused field above it.
7. **Banners** (`HashiyaBanner`, bottom overlay, glass on iOS 26):
   - "Couldn't save your notes" with **Retry**;
   - "Couldn't update the status".

While `.loading`, the screen shows `LoadingSkeleton`. If the notes can't be read (§7.3), the notes section shows "Couldn't load your notes" with **Retry** instead of the fields, and the rest of the screen still shows.

### 7.3 `PaperDetailsViewModel`

```swift
public enum PaperDetailsState: Equatable { case loading, loaded(LibraryPaper, notes: NotesLoad, saveState: NotesSaveState) }
public enum NotesLoad: Equatable { case loaded(PaperNotes), failed }
public enum NotesSaveState: Equatable, Sendable { case idle, saving, saved, failed }
public enum PaperDetailsMessage: Equatable, Sendable { case notesSaveFailed, statusUpdateFailed }
public enum PaperDetailsExit: Equatable, Sendable { case closed, removed }
```

`@Observable @MainActor`, with `state`, `message` and `exit`. It is built with the id, the repository, `PendingWrites` and an injectable `sleep`, the same pattern as `LibraryViewModel`.

- **Paper:** from `observePaper(id)`. The first nil sets `exit = .closed`.
- **Notes read once:** `notes(openAlexID:)` runs once at start; later database changes are never read. A failed read gives `NotesLoad.failed` and no fields, so an empty form can never overwrite stored notes. `retryLoadNotes()` reads again. The state stays `.loading` until the paper and the first notes read (success or failure) are in.
- `notes` in the state is the view model's copy, used only to seed the fields. Fields never read it back (§7.5).

### 7.4 Autosave

- `updateNote(_ section:, _ text:)` changes the local copy at once and restarts a 500 ms debounce task. A burst of typing produces one write.
- **Writes are sequential and never cancelled halfway:** each write is started through `PendingWrites.run` and chained after the previous one, so they run in order. A write whose value equals the last saved value is skipped, which is Android's `distinctUntilChanged` plus its `storedNotes` check.
- `saveState`:
  - `.idle` until the first write;
  - `.saving` while a write runs;
  - `.saved` after it succeeds;
  - `.failed` after it throws, with `message = .notesSaveFailed`.
- `flush()` cancels the debounce and writes the current notes at once if they differ from the last saved value. The screen calls it:
  - in `.onDisappear`: back, tab switch, Remove;
  - when the scene phase becomes `.inactive` or `.background`.

  The write outlives the view and the view model, because the task captures the repository and the value, not `self`.
- `retrySave()` is `flush()`. A failed write doesn't block the next edit's write.

### 7.5 Note fields keep their own text

- `NoteField` holds `@State private var text: String`, seeded once from the view model's copy when the field first appears. Its `onChange(of: text)` calls `actions.updateNote(section, text)`.
- The field never reads the view model's notes again, so typing, and Arabic or Japanese marked text in progress, never waits for a round trip. This is the SwiftUI form of Android's `rememberSaveable` `TextFieldValue`.
- The six fields are keyed by `NoteSection` in a `ForEach(NoteSection.allCases, id: \.self)`, so each keeps its own state.

### 7.6 Other actions

- `setStatus(_:)` calls the repository; a failure sets `message = .statusUpdateFailed`. The selector shows the stored status from `observePaper`.
- `remove()` cancels the debounce and waits for the write chain plus a write of the current notes. If that succeeds (or there was nothing to write), it sets `exit = .removed`. If it fails, it stays with `saveState = .failed` and the Retry banner, so Undo can never bring back older notes.
- `openDOI` and `openPDF` go through `DOILink.url(for:)` and `URL(string:)`; an invalid URL does nothing.

## 8. Library and Search changes

**`FeatureLibrary`:**

- `LibraryViewModel` loses `selectedPaperID`, `selectedPaper`, `select(_:)` and the whole-library observation behind them (`allPapers`).
- It gains `remove(openAlexID:)`: the same as the swipe's `remove(_:)`, Undo included.
- `LibraryView` loses the `.sheet` and the `preview`. A row tap calls `onOpenPaper(openAlexID)`.
- Swipe to remove with Undo, the badge menu, search, chips and restoration are unchanged.

**`HashiyaDesignSystem`:** `PaperPreviewContent` gains `onOpenDetails: (() -> Void)? = nil`. When non-nil, a full-width **Open details** button (`hashiyaSecondaryButton()`, `doc.text` icon) shows above the other buttons. Its `status` / `onStatusChange` parameters are removed: the Library preview was their only caller, and Details uses `ReadingStatusSelector` directly. The selector's tests move from the preview to the selector itself.

**`FeatureSearch`:**

- `SearchView` passes `onOpenDetails` only when `viewModel.isSaved(paper)`.
- `SearchViewModel` gains `remove(openAlexID:)`: the removal half of `toggleSave`.
- The Share Extension's sheet passes nothing and is unchanged.

## 9. Strings

Same key convention as spec 1 §10. Android's texts in both languages.

`FeaturePaperDetails` (`Resources/Localizable.xcstrings`):

| Key | English | Arabic |
|---|---|---|
| `details.moreOptions` | More options | خيارات أخرى |
| `details.openPDF` | Open PDF | فتح ملف PDF |
| `details.notesTitle` | My notes | ملاحظاتي |
| `details.notesSaving` | Saving… | جارٍ الحفظ… |
| `details.notesSaved` | Saved | تم الحفظ |
| `details.notesSaveFailed` | Couldn't save | تعذّر الحفظ |
| `details.notesSaveFailedMessage` | Couldn't save your notes | تعذّر حفظ ملاحظاتك |
| `details.notesLoadFailed` | Couldn't load your notes | تعذّر تحميل ملاحظاتك |
| `details.retry` | Retry | إعادة المحاولة |
| `details.statusUpdateFailed` | Couldn't update the status | تعذّر تحديث الحالة |
| `details.doneEditing` | Done | تم |
| `note.summary` / `note.summaryHint` | Summary / What is this paper about, in your own words? | الخلاصة / عمّ تتحدث هذه الورقة، بكلماتك أنت؟ |
| `note.researchQuestion` / `…Hint` | Research question / What question or problem does it address? | سؤال البحث / ما السؤال أو المشكلة التي تعالجها؟ |
| `note.method` / `…Hint` | Method / How did the authors approach it? | المنهجية / كيف تناولها المؤلفون؟ |
| `note.keyFindings` / `…Hint` | Key findings / What did they find? | أهم النتائج / ما الذي توصّلوا إليه؟ |
| `note.limitations` / `…Hint` | Limitations / What are its weaknesses or open questions? | القيود / ما نقاط ضعفها أو الأسئلة التي تتركها مفتوحة؟ |
| `note.thoughts` / `…Hint` | My thoughts / How does it relate to your work? | أفكاري / ما علاقتها ببحثك؟ |

`HashiyaDesignSystem`: `designsystem.openDetails` with Open details / فتح التفاصيل.

The note's Summary is **الخلاصة** because **الملخص** is already the Abstract. Existing strings are reused for Open DOI, Remove from library, Abstract, No abstract, Untitled and the status labels.

- **New on iOS only:** `details.notesLoadFailed` (Android can't fail a read in a way the UI sees) and `details.doneEditing` (the keyboard toolbar).
- **Not used on iOS:** `details_back` (the system back button supplies its own label).

## 10. Errors

| Case | Behaviour |
|---|---|
| Saving notes fails | The status line shows **Couldn't save**, and the banner "Couldn't save your notes" appears with **Retry**. The text stays in the fields, and the next edit also tries again. |
| Remove while the save fails | The screen stays, and nothing is removed. |
| Reading the notes fails | "Couldn't load your notes" with **Retry**, and no fields, so nothing can overwrite the stored notes. |
| Changing the status fails | The banner "Couldn't update the status"; the selector shows the stored status. |
| The paper stops being saved | The screen pops. `saveNotes` on it does nothing, so a flush after that is harmless. |
| Backgrounding during a write | The suspend waits for it (§6). |
| The system ends the app in the background | Only typing after the last write and before the app went inactive can be lost. The inactive flush makes that window tiny. |
| Very long notes | No limit: one `TEXT` column per section. |

## 11. Testing

Swift Testing, test-first, hand-written fakes and injected sleeps, as in specs 1–3.

| Target | Coverage |
|---|---|
| `HashiyaModelTests` | `PaperNotes`: the subscript and `with` for every section; `isEmpty` treats whitespace and newlines as blank. |
| `HashiyaDatabaseTests` | `saveNotes` creates, updates, and deletes the row when blank; `paper_search.notes` follows every save; returns false for an unsaved paper; `MATCH` finds a paper by a word only in its notes, including folded Arabic (a note with tashkeel found without it); an open `observeLibrary` with a notes-only match emits the paper after `saveNotes` (the `notifyChanges` requirement); `observePaper` emits the paper, then nil after delete; `deleteByOpenAlexID` returns the notes and leaves no row; `insert` with notes restores the row and the search column. **Migration `v2` → `v3`:** a database migrated `upTo: "v2"` with spec 3's fixture papers `a` and `b` and mixed statuses; after `v3`, every paper and status is kept, the `v2` searches (`"attention*"`, `"shazeer*"`, `"التعلم*"`, …) find the same papers, every `notes` column is `""`, `paper_notes` exists and is empty, and `paper_id` is still not searchable. The spec 3 `v1` → `v2` test keeps passing, and `v1` → `v3` passes too. |
| `HashiyaDataTests` | `GRDBLibraryRepository`: `observePaper` (then nil after remove); `notes` is empty with none and for an unsaved paper; save and read back; `saveNotes` on an unsaved paper does nothing; remove → restore keeps the notes and their searchability; the Library search by a note word; `PendingWrites.drained()` waits for a running write. `FakeLibraryRepository` covers the new members. |
| `FeaturePaperDetailsTests` | The view model with a `ManualSleeper`: loading waits for both the paper and the notes read; typing doesn't write before 500 ms and writes once after it, and a burst writes once; `saveState` goes `idle → saving → saved`; a failure gives `.failed` and `.notesSaveFailed`, and Retry writes again; `flush()` writes pending notes at once and writes nothing when nothing is new; two writes run in order; a notes change in the database after loading doesn't change the view model's notes; a notes read failure gives `.failed` and no write is ever made, and retry loads them; a failing status change sets `.statusUpdateFailed`; the paper becoming nil sets `exit = .closed`; `remove()` writes pending notes before `exit = .removed`; `remove()` with a failing save doesn't exit, and succeeds after the failure clears (Android's `removeWaitsWhenTheSaveFailsSoUndoCantRestoreStaleNotes`). Strings: every key resolves in English and Arabic. |
| `FeatureLibraryTests` | Preview tests removed on purpose; `remove(openAlexID:)` offers Undo, and Undo restores the paper with its notes. |
| `FeatureSearchTests` | `remove(openAlexID:)` removes, and sets `removeFailed` on failure. |
| `HashiyaDesignSystemTests` | **Open details** shows only with `onOpenDetails`, and calls it. |
| Snapshots (`HashiyaSnapshotTests`) | Details, English and Arabic × light and dark, on iOS 26 and iOS 18: a paper with notes filled (one Arabic note in the English UI, one English note in the Arabic UI); a paper with no notes and no abstract (Untitled); the **Couldn't save** state with its banner; the notes-load failure. Search's preview with **Open details**. Library baselines are re-recorded only if they change (the rows don't). |
| `HashiyaUITests` | Library row → Details → type in Summary → see **Saved** → back → reopen → the text is there; the Library search for a word only in that note finds the paper; Remove from **More options** → back on the Library with the Undo banner → Undo brings the paper back, and its Details still has the note; the tab bar is hidden on Details. (Search → **Open details** needs OpenAlex results; the UI-test stub search supplies them, so this is covered too: search, open a saved paper's sheet, **Open details**, Details opens; an unsaved paper's sheet has no **Open details**.) |

Typing into the field and the fields keeping their own text are covered through the view model and the UI test. Unlike Compose, SwiftUI has no in-process UI test API for a `TextField`.

## 12. Acceptance criteria (on a device or simulator)

1. Install the spec 3 build, save three papers and set one to Reading, then install this build over it. Every paper and status is there, and the Library search still finds them.
2. Tap a Library row. Details shows every author, the abstract and the status, and Open DOI / Open PDF open Safari.
3. Type in Summary and Method, pause, and see **Saved**. Go back, reopen, and the text is there.
4. Type a note, swipe home at once, then end the app from the app switcher. After reopening, the note is there.
5. The Library search for a word only in a note finds the paper, including an Arabic word typed without tashkeel against a note written with it.
6. Remove from **More options**: back on the Library with the Undo banner, and Undo brings the paper back with its notes and status.
7. In Search, a saved paper's sheet has **Open details**, which opens Details; an unsaved paper's sheet doesn't.
8. In العربية the Details screen mirrors, the labels and hints are Arabic, and an English note stays left-to-right. Light and dark both read well, and iOS 26 shows glass bars, menu and banners.
9. VoiceOver reads the title as a header, each field by its section name, and the save status.
10. CI (the "iOS" workflow) is green, with the new baselines recorded by `ios-record-snapshots`.

## 13. Risks

| Risk | Mitigation |
|---|---|
| The FTS rebuild loses or changes search rows | A copy through a temporary table with no re-normalization, and a migration test that compares searches before and after. |
| Notes lost when leaving or backgrounding | Debounce plus flushes on disappear, inactive and background; writes outlive the view; the suspend waits for them (§6). |
| The database overwrites typing | Notes are read once, and fields own their text; tests cover both. |
| Library search doesn't see a new note | `saveNotes` notifies `papers`; a database test covers it. |
| `navigationDestination` rebuilds the view model | The view model lives in `@State` inside `PaperDetailsScreen`, created once per pushed screen. |
| Pushing while Search's sheet dismisses glitches | The sheet is closed first, then the route is appended; the UI test covers the path. |
| The first `v3` migration on real installs | The migration tests from `v1` and from `v2`, plus acceptance check 1. |

## 14. Differences from Android

| Area | Android | iOS | Why |
|---|---|---|---|
| Notes read | First value of `observeNotes` | One `notes(openAlexID:)` read; a read failure shows **Couldn't load your notes** with Retry | Same "read once" rule. The failure is explicit, so an empty form never overwrites stored notes. |
| Remove hand-off | Navigation result key in the previous entry's `SavedStateHandle` | `onRemove(id)` closure to `RootView`, which pops and calls the tab's view model | SwiftUI has no navigation results; the closure is the direct form. |
| Flush on leaving | `ON_STOP` and `onCleared`, on the application scope | `.onDisappear` and scene phase `.inactive`/`.background`, through `PendingWrites` | Platform lifecycle; the suspended shared database needs the drain. |
| Field text | `rememberSaveable` `TextFieldValue` | `@State` text seeded once | Platform state. |
| Content direction | `TextDirection.Content` | `ContentDirection.of` per field | Existing iOS helper. |
| Keyboard | IME closes with Back | Keyboard toolbar **Done**; interactive dismiss | Return adds a line in a vertical field. |
| Save-failed and status messages | Snackbars | `HashiyaBanner`s | No system snackbar. |
| Hidden navigation bar | Bottom navigation hidden for non-top-level routes | `.toolbar(.hidden, for: .tabBar)` on Details | Same behaviour. |
| Restoring open Details | Back stack survives process death | Not restored (non-goal) | Library and Search still restore their state; notes are never lost. |
| Library change observation | Room invalidation covers the FTS table | `saveNotes` calls `notifyChanges(in: papers)` | SQLite's update hook skips virtual tables. |
| Strings | `details_back` | Not used; adds `details.notesLoadFailed`, `details.doneEditing` | System back button; iOS-only states. |
