# iOS Paper Details and Structured Notes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Load the `liquid-glass:liquid-glass` skill before any task that touches a view.

**Goal:** Give every saved paper on iOS a Details screen with six fixed note sections that save themselves, as Android sub-project 4 did. Tapping a Library row opens it; Search's preview sheet opens it for saved papers; the Library search finds papers by their notes; Remove on Details returns to the Library with its Undo, which brings the notes back. English and Arabic with right-to-left layouts, light and dark, Liquid Glass on iOS 26.

**Architecture:** `HashiyaModel` gains `NoteSection` and `PaperNotes`. `HashiyaDatabase` gains migration `v3`: the `paper_notes` table, and `paper_search` rebuilt with a `notes` column through a temporary copy. `PaperStore` gains `observePaper`, a one-shot `notes` read and `saveNotes`, which also tells open Library observations to fetch again. `deleteByOpenAlexID` returns the notes it cascaded, and `insert` can write them back. `HashiyaData` gains the three repository members, `RemovedPaper.notes`, and `PendingWrites`, which the app waits on before it suspends the shared database in the background. A new package target, `FeaturePaperDetails`, holds `PaperDetailsViewModel` and the views: the debounced autosave, a sequential write chain, notes read once, and fields that keep their own text. `RootView` owns one navigation path per tab and pushes Details from the Library and from Search. `HashiyaDesignSystem`'s preview loses the status selector (Details has it now) and gains **Open details**.

**Tech Stack:** Swift 6 (language mode 6, strict concurrency), SwiftUI with the iOS 26 SDK, iOS 17+ deployment target, Swift Testing, XCTest (UI tests), GRDB.swift 7.11.1 (SQLite FTS4), swift-snapshot-testing 1.19.6, String Catalogs, XcodeGen 2.46.0. CI: `macos-15`, Xcode 26.3, iPhone 16 on iOS 26.2 and on iOS 18.5.

**Spec:** docs/superpowers/specs/2026-09-30-ios-paper-details-and-notes-design.md

## Global Constraints

- Everything in the earlier iOS plans' Global Constraints still applies:
  - iOS 17 minimum; Swift 6 language mode with strict concurrency.
  - The code builds with CI's Xcode 26.3 (Swift 6.2): no isolated `deinit`, no `Mutex`; `OSAllocatedUnfairLock` for shared state.
  - XcodeGen: `ios/project.yml` is committed, and `ios/Hashiya.xcodeproj` is generated and git-ignored.
  - Exact dependency pins.
  - Features never import each other, `HashiyaNetwork`, `HashiyaDatabase` or GRDB.
  - Every user-visible string comes from a `Localizable.xcstrings` (`extractionState: manual`) through the target's `L10n`, shown with `Text(verbatim:)`. Numbers go through `PaperFormat.number`.
  - Snapshot suites are `@MainActor @Suite(.serialized)`, and baselines come only from CI.
  - Every glass API call sits inside `if #available(iOS 26, *)` or behind the `Glass.swift` helpers.
  - System chrome (navigation bar, menus, segmented picker, keyboard toolbar) gets no glass modifiers.
- **One new package target**, `FeaturePaperDetails` (with its test target `FeaturePaperDetailsTests`). This deliberately replaces the earlier plans' "no new package targets", as the spec decided. It depends on `HashiyaData`, `HashiyaModel` and `HashiyaDesignSystem` only.
- **Database:** exactly one new migration, `"v3"`, registered after `"v2"`, with the SQL in spec §4.1. Never `eraseDatabaseOnSchemaChange` or any destructive fallback.
  - `paper_search` keeps `tokenize=unicode61, notindexed=paper_id`.
  - Every write that adds, deletes or changes notes keeps `paper_search.notes` in step in the same transaction.
  - A `paper_notes` row exists only while some section isn't blank.
- **Notes are read once** per Details screen (`LibraryRepository.notes(openAlexID:)`); no database change reaches the view model's notes after that. Each note field owns its text in `@State`, seeded once, and never reads the view model's notes back.
- **Writes** go through `PaperDetailsViewModel.write(_:)`, the only place that calls `saveNotes`. Writes are chained, so they run in order; they are never cancelled; and each is tracked by `PendingWrites`.
- **Remove** exits only after the pending notes are saved. If saving fails, nothing is removed.
- **Strings:** keys, English and Arabic verbatim from spec §9 (Android's texts). Add them with the Python snippets below, which write a catalog exactly as Xcode formats it; never hand-edit the JSON.
- **Verification is CI.** This work runs in a cloud session that can't build or test iOS. Each task ends by pushing and reading the "iOS" workflow's result for that commit through the GitHub API; the run takes about 25 minutes. The `Run:` lines give the command a Mac would run, and CI runs the same scheme. Never say a task works until CI showed it.
  - Until Task 8 records the new baselines, a run fails only in the new snapshot tests, with `No reference was found on disk. Automatically recorded snapshot: …`. Read every failure in the log; any other failure is real.
- **Git:** work on `feat/ios-paper-details-and-notes` (already created from `main` at `ab5f552`, holding the spec).
  - Every `git add` lists explicit paths. Never stage `__Snapshots__` by hand before Task 8, and never stage `.idea/`.
  - Commit messages use `feat:`/`test:`/`docs:`/`ci:` and carry no AI or Claude attribution, nor do code comments, docs or PR text.
  - Before every push, `git log --format='%an <%ae>' origin/main..HEAD` must print only `Fady <fady.fouad.a@gmail.com>`.

## Review Focus

1. **Typing a note, then leaving right away** (back, Home, or the app switcher) → the note is stored. Pinned by:
   - `PaperDetailsViewModelTests.flushWritesPendingNotesAtOnce`;
   - `aWriteOutlivesTheViewModel`;
   - `PendingWritesTests.drainedWaitsForARunningWrite` (Tasks 3 and 5);
   - `PaperDetailsFlowTests.testNotesSaveAndAreFoundByTheLibrarySearch` (Task 7);
   - acceptance check 4 (Task 8).
2. **A database change while typing** → never overwrites the text. Pinned by `PaperDetailsViewModelTests.aLaterDatabaseChangeDoesNotReplaceTypedNotes` and `aFailedNotesReadNeverWritesAndRetryLoadsThem` (Task 5). The fields' own `@State` is covered by the UI test typing into two fields in a row.
3. **Remove while a save fails** → nothing is removed, and the screen stays with **Couldn't save** and **Retry**. Remove after a successful save → back on the Library with Undo, which restores the notes. Pinned by:
   - `PaperDetailsViewModelTests.removeWaitsWhenTheSaveFailsSoUndoCantRestoreStaleNotes`;
   - `removeSavesPendingNotesFirst` (Task 5);
   - `GRDBLibraryRepositoryTests.removeThenRestoreKeepsTheNotesAndTheirSearch` (Task 3);
   - `PaperDetailsFlowTests.testRemoveFromDetailsOffersUndoWithTheNotes` (Task 7).
4. **Upgrading a spec 3 install** → every paper, status and search result is kept, and notes become searchable. Pinned by `MigrationTests.migratingFromV2KeepsEveryPaperStatusAndSearch` and `v3RebuildsTheSearchIndexWithANotesColumn` (Task 2).
5. **The Library search after writing a note** → an open search shows the paper without any other change. Pinned by `PaperStoreTests.anOpenLibrarySearchSeesANewNote` (Task 2).

---

## File Structure

```
ios/HashiyaKit/Sources/HashiyaModel/PaperNotes.swift                                                   (Task 1)
ios/HashiyaKit/Sources/HashiyaDatabase/HashiyaDatabase.swift, Records.swift, PaperStore.swift          migration v3, notes records, store ops (Task 2)
ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift, PaperMapping.swift, PendingWrites.swift     (Task 3; a minimal LibraryRepository edit in Task 2)
ios/HashiyaKit/Sources/HashiyaTesting/FakeLibraryRepository.swift                                      (Task 3)
ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/PaperPreviewContent.swift, DesignSystemStrings.swift,
  Resources/Localizable.xcstrings                                                                      (Task 4)
ios/HashiyaKit/Package.swift                                                                           HashiyaDatabaseTests → HashiyaModel (Task 2); FeaturePaperDetails (Task 5)
ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsViewModel.swift, L10n.swift, Resources/Localizable.xcstrings (Task 5)
ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsContent.swift, NoteField.swift, PaperDetailsScreen.swift (Task 6)
ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift, LibraryView.swift                        (Task 7)
ios/HashiyaKit/Sources/FeatureSearch/SearchViewModel.swift, SearchView.swift                           (Task 7)
ios/Hashiya/RootView.swift, AppContainer.swift; ios/project.yml                                        (Task 7; project.yml in Tasks 5 and 6)
ios/HashiyaSnapshotTests/PaperDetailsSnapshotTests.swift, DesignSystemSnapshotTests.swift              (Tasks 4 and 6)
ios/HashiyaUITests/LibraryFlowTests.swift, PaperDetailsFlowTests.swift                                  (Task 7)
ios/README.md, README.md                                                                               (Task 8)
```

## Where this plan departs from the spec (and why)

- **`PendingWrites.track(_:)` instead of `run(_:)`** (spec §6). The view model builds each write as a `Task<Bool, Never>`, because Remove needs its result, and hands the task to `track`. `drained()` is as specified.
- **A write's task holds the view model until it ends** (spec §7.4 said it captures the repository and the value, not `self`). The chained write reads and updates `savedNotes` and `saveState`, which live on the view model. Keeping the view model alive a few milliseconds longer is harmless, and nothing cancels the task. `PaperDetailsViewModelTests.aWriteOutlivesTheViewModel` pins that a write started just before the last reference goes away still lands.
- **The view model's state is several observable properties, not one enum** (spec §7.3's `PaperDetailsState`): `paper`, `notesLoad` (`.loading`, `.loaded`, `.failed`), `notes`, `saveState`, `message`, `exit`, and `isLoaded`. A retry after a failed read keeps `.failed` on screen until the read succeeds, so the screen never drops back to the skeleton. `PaperDetailsContent` takes `notes: PaperNotes?` (nil = couldn't load).
- **The view model starts its work in `start()`, which the screen calls from `.task`**, not in `init`. `navigationDestination` builds a `PaperDetailsScreen` on every parent update, and `State(wrappedValue:)` evaluates its argument each time, keeping only the first instance. So `init` only stores its dependencies, and the discarded copies start nothing.
- **Search opens Details from the sheet's `onDismiss`.** **Open details** records the paper and closes the sheet, and the route is appended once the sheet is gone, so the push never overlaps the sheet's dismissal (spec §13's risk).
- **Design-system strings for other targets:** Details shows Abstract, No abstract, Open DOI, Remove from library and the open-access badge texts, which live in the design system's catalog behind its internal `L10n`. A small public `DesignSystemStrings` exposes exactly those, so no string is duplicated.
- **The preview's status selector test and baseline are replaced.** `ReadingStatusSelectorTests.thePreviewShowsTheSelectorOnlyWithAStatus` becomes `thePreviewShowsOpenDetailsOnlyWhenGiven`. `DesignSystemSnapshotTests.previewWithTheStatusSelector` becomes `previewWithOpenDetails`, and the old baselines are deleted.
- **`LibraryView` and `SearchView` take `onOpenPaper` with a default of `{ _ in }`**, so the snapshot suites that don't navigate keep compiling unchanged.
- **Extra API** the spec implies but doesn't name:
  - `NoteSection.key`: the catalog key stem, internal to `FeaturePaperDetails`;
  - `PaperNotesRecord.notes` and `init(paperID:notes:updatedAt:)`;
  - `DeletedPaper { saved: PaperWithAuthors; notes: PaperNotesRecord? }`;
  - `PaperWithAuthors.searchRow(notes:)`;
  - `RemovedPaper.init(…, notes: PaperNotes = PaperNotes())`, so existing call sites compile;
  - `FakeLibraryRepository`: `setFailSaveNotes(_:)`, `setFailNotesRead(_:)`, `holdNotesSaves()`, `releaseNotesSaves()`, `notesWriteAttempts`, `notes(of:)`;
  - `LibraryViewModel.remove(openAlexID:)`, `SearchViewModel.remove(openAlexID:)`;
  - `PaperDetailsActions` (the content's callbacks);
  - accessibility identifiers `note.<key>` on the fields and `details.saveStatus` on the save-status line, which the UI tests use.

---
### Task 1: `HashiyaModel` — `NoteSection` and `PaperNotes`

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaModel/PaperNotes.swift`
- Test: Create `ios/HashiyaKit/Tests/HashiyaModelTests/PaperNotesTests.swift`

**Interfaces:**
- Produces (module `HashiyaModel`, all `public`):
  - `enum NoteSection: CaseIterable, Sendable { case summary, researchQuestion, method, keyFindings, limitations, thoughts }`
  - `struct PaperNotes: Equatable, Hashable, Sendable` with the six `String` properties, `init(summary:researchQuestion:method:keyFindings:limitations:thoughts:)` (every argument defaults to `""`), `subscript(section:) -> String { get set }`, `with(_:_:) -> PaperNotes` and `isEmpty: Bool`.

- [ ] **Step 1: Write the failing test**

`ios/HashiyaKit/Tests/HashiyaModelTests/PaperNotesTests.swift`:
```swift
import HashiyaModel
import Testing

struct PaperNotesTests {
    @Test func sectionsAreInTheTemplatesOrder() {
        #expect(NoteSection.allCases == [.summary, .researchQuestion, .method, .keyFindings, .limitations, .thoughts])
    }

    @Test(arguments: NoteSection.allCases)
    func eachSectionReadsAndWritesItsOwnText(section: NoteSection) {
        let notes = PaperNotes().with(section, "Text for \(section)")

        #expect(notes[section] == "Text for \(section)")
        for other in NoteSection.allCases where other != section {
            #expect(notes[other] == "")
        }
    }

    @Test func theSubscriptSetsASection() {
        var notes = PaperNotes(summary: "S")
        notes[.thoughts] = "T"
        #expect(notes == PaperNotes(summary: "S", thoughts: "T"))
        #expect(notes.summary == "S")
        #expect(notes.thoughts == "T")
    }

    @Test func textIsKeptExactlyAsTyped() {
        let text = "  Two lines\nwith spaces  "
        #expect(PaperNotes().with(.method, text).method == text)
    }

    @Test func blankNotesAreEmpty() {
        #expect(PaperNotes().isEmpty)
        #expect(PaperNotes(summary: "   ", method: "\n\t", thoughts: " \n ").isEmpty)
        #expect(!PaperNotes(limitations: " x ").isEmpty)
        #expect(!PaperNotes(keyFindings: "٣").isEmpty)
    }
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaModelTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL: `cannot find 'NoteSection' in scope`, then `** TEST FAILED **`.

- [ ] **Step 3: Implement**

`ios/HashiyaKit/Sources/HashiyaModel/PaperNotes.swift`:
```swift
import Foundation

/// A section of the fixed note template, in on-screen order.
public enum NoteSection: CaseIterable, Sendable {
    case summary, researchQuestion, method, keyFindings, limitations, thoughts
}

/// The user's notes on a saved paper, one plain-text field per `NoteSection`. Text is kept exactly as typed.
public struct PaperNotes: Equatable, Hashable, Sendable {
    public var summary: String
    public var researchQuestion: String
    public var method: String
    public var keyFindings: String
    public var limitations: String
    public var thoughts: String

    public init(
        summary: String = "",
        researchQuestion: String = "",
        method: String = "",
        keyFindings: String = "",
        limitations: String = "",
        thoughts: String = ""
    ) {
        self.summary = summary
        self.researchQuestion = researchQuestion
        self.method = method
        self.keyFindings = keyFindings
        self.limitations = limitations
        self.thoughts = thoughts
    }

    public subscript(section: NoteSection) -> String {
        get {
            switch section {
            case .summary: summary
            case .researchQuestion: researchQuestion
            case .method: method
            case .keyFindings: keyFindings
            case .limitations: limitations
            case .thoughts: thoughts
            }
        }
        set {
            switch section {
            case .summary: summary = newValue
            case .researchQuestion: researchQuestion = newValue
            case .method: method = newValue
            case .keyFindings: keyFindings = newValue
            case .limitations: limitations = newValue
            case .thoughts: thoughts = newValue
            }
        }
    }

    /// A copy with `section` set to `text`.
    public func with(_ section: NoteSection, _ text: String) -> PaperNotes {
        var copy = self
        copy[section] = text
        return copy
    }

    /// True when every section is empty or whitespace (newlines included) only.
    public var isEmpty: Bool {
        NoteSection.allCases.allSatisfy { self[$0].trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }
}
```

- [ ] **Step 4: Run it and see it pass**

Run: the Step 2 command.
Expected: PASS: `✔ Test run with … tests passed`, `** TEST SUCCEEDED **`.

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaModel/PaperNotes.swift ios/HashiyaKit/Tests/HashiyaModelTests/PaperNotesTests.swift
git commit -m "feat: add the fixed note template to the iOS model"
```

Tasks 1–2 are pushed together at the end of Task 2 (Task 1 alone changes nothing CI can observe beyond its own tests, and pushing it separately costs a 25-minute run).

---
### Task 2: `HashiyaDatabase` — migration `v3`, notes records, and the store's notes operations

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaDatabase/HashiyaDatabase.swift`, `Records.swift`, `PaperStore.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift` (only `remove`, so the package keeps compiling)
- Modify: `ios/HashiyaKit/Package.swift` (`HashiyaDatabaseTests` gains `"HashiyaModel"`)
- Test: Modify `ios/HashiyaKit/Tests/HashiyaDatabaseTests/MigrationTests.swift` and `PaperStoreTests.swift`

**Interfaces:**
- Consumes: Task 1's `PaperNotes`, `NoteSection`; spec 3's `searchableText`, `PaperSearchRow`, `PaperWithAuthors`, `PaperStore`.
- Produces (module `HashiyaDatabase`, `public`):
  - `struct PaperNotesRecord: Codable, Equatable, Sendable, FetchableRecord, PersistableRecord` (`paper_notes`), with `paperID`, the six sections, `updatedAt: Int64`, `init(paperID:notes:updatedAt:)` and `var notes: PaperNotes`;
  - `PaperSearchRow.notes: String`, `init(…, notes: String = "")`, `make(…, notes: PaperNotes? = nil)` and `static func notesText(_: PaperNotes) -> String`;
  - `PaperWithAuthors.searchRow(notes: PaperNotes?) -> PaperSearchRow` (the `searchRow` property stays, meaning no notes);
  - `struct DeletedPaper: Equatable, Sendable { var saved: PaperWithAuthors; var notes: PaperNotesRecord? }`;
  - `PaperStore`:
    - `observePaper(openAlexID:) -> AsyncStream<PaperWithAuthors?>`;
    - `notes(openAlexID:) async throws -> PaperNotesRecord?`;
    - `saveNotes(openAlexID:notes:updatedAt:) async throws -> Bool`;
    - `deleteByOpenAlexID(_:) -> DeletedPaper?`;
    - `insert(paper:authors:search:notes:)`, where `notes` defaults to nil.

**Watch out:** the `v2` migration's backfill called `PaperSearchRow.insert`, which from now on writes a `notes` column that doesn't exist yet at `v2`. A migration must never change once shipped, so `v2` gets its own frozen five-column `INSERT`. `v1CreatesAndroidsVersion1Schema` and `migratingKeepsEveryPaperAsToReadAndIndexesItWithItsAuthorsInOrder` (a fresh `v1` → latest run) catch a mistake here.

- [ ] **Step 1: Write the failing tests**

In `ios/HashiyaKit/Package.swift`, change the `HashiyaDatabaseTests` line to:
```swift
        .testTarget(name: "HashiyaDatabaseTests", dependencies: ["HashiyaDatabase", "HashiyaModel", grdb]),
```

`ios/HashiyaKit/Tests/HashiyaDatabaseTests/MigrationTests.swift`:

1. Add `import HashiyaModel` after `import HashiyaDatabase`.
2. Replace `private func version1()` with a helper for any version, and keep `version1()` as a call to it:
```swift
    /// A database migrated only to `version`, as an install of that version left it.
    private func version(_ version: String) throws -> DatabaseQueue {
        let queue = try DatabaseQueue()
        try HashiyaDatabase.migrator.migrate(queue, upTo: version)
        return queue
    }

    /// A database migrated only to `v1`, as plans 1 and 2 left every install.
    private func version1() throws -> DatabaseQueue {
        try version("v1")
    }
```
3. Replace `theMigrationsAreV1ThenV2` with:
```swift
    @Test func theMigrationsAreV1ThenV2ThenV3() {
        #expect(HashiyaDatabase.migrator.migrations == ["v1", "v2", "v3"])
    }
```
4. In `v2AddsTheReadingStatusAndTheSearchIndex`, replace `try HashiyaDatabase.openInMemory().read { db in` with `try version("v2").read { db in` (the test describes `v2`; `v3` rebuilds the table).
5. In `reopeningAFileKeepsTheLibrary`, replace `== ["v1", "v2"])` with `== ["v1", "v2", "v3"])`.
6. Add these tests before `foreignKeysAreEnforced`:
```swift
    @Test func v3AddsTheNotesTable() throws {
        try HashiyaDatabase.openInMemory().read { db in
            let columns = try db.columns(in: "paper_notes")
            #expect(columns.map(\.name) == [
                "paper_id", "summary", "research_question", "method", "key_findings", "limitations", "thoughts", "updated_at",
            ])
            #expect(columns.map(\.type) == ["TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "INTEGER"])
            #expect(columns.allSatisfy(\.isNotNull))
            #expect(try db.primaryKey("paper_notes").columns == ["paper_id"])
            #expect(try db.foreignKeys(on: "paper_notes").map(\.destinationTable) == ["papers"])
            let onDelete = try String.fetchOne(db, sql: "SELECT on_delete FROM pragma_foreign_key_list('paper_notes')")
            #expect(onDelete == "CASCADE")
        }
    }

    @Test func v3RebuildsTheSearchIndexWithANotesColumn() throws {
        let sql = try HashiyaDatabase.openInMemory().read { db in
            try String.fetchOne(db, sql: "SELECT sql FROM sqlite_master WHERE name = 'paper_search'")
        }
        #expect(sql == "CREATE VIRTUAL TABLE paper_search USING fts4(paper_id, title, authors, abstract, venue, notes, tokenize=unicode61, notindexed=paper_id)")
    }

    /// A spec 3 install: every paper, status and search result is kept, and no paper has notes yet.
    @Test func migratingFromV2KeepsEveryPaperStatusAndSearch() async throws {
        let queue = try version1()
        try insertVersion1Fixture(into: queue)
        try HashiyaDatabase.migrator.migrate(queue, upTo: "v2")
        try await queue.write { db in
            try db.execute(sql: "UPDATE papers SET reading_status = 'reading' WHERE id = 'a'")
        }
        let indexBefore = try queue.read { db in
            try String.fetchAll(db, sql: "SELECT paper_id || '|' || title || '|' || authors || '|' || abstract || '|' || venue FROM paper_search ORDER BY paper_id")
        }

        try HashiyaDatabase.migrator.migrate(queue)

        let (statuses, indexAfter, notes, noteRows, temporary) = try queue.read { db in
            (
                try String.fetchAll(db, sql: "SELECT id || ':' || reading_status FROM papers ORDER BY id"),
                try String.fetchAll(db, sql: "SELECT paper_id || '|' || title || '|' || authors || '|' || abstract || '|' || venue FROM paper_search ORDER BY paper_id"),
                try String.fetchAll(db, sql: "SELECT notes FROM paper_search ORDER BY paper_id"),
                try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM paper_notes"),
                try db.tableExists("paper_search_copy")
            )
        }
        #expect(statuses == ["a:reading", "b:to_read"])
        #expect(indexAfter == indexBefore)
        #expect(notes == ["", ""])
        #expect(noteRows == 0)
        #expect(!temporary)

        let store = PaperStore(writer: queue)
        func ids(_ match: String?) async -> [String]? {
            await first(store.observeLibrary(match: match, status: nil))?.papers.map(\.paper.id)
        }
        #expect(await ids(nil) == ["b", "a"])
        #expect(await ids("\"attention*\"") == ["a"])
        #expect(await ids("\"shazeer*\"") == ["a"])
        #expect(await ids("\"transduction*\"") == ["a"])
        #expect(await ids("\"التعلم*\"") == ["b"])
    }

    /// Once the notes column exists, a note written after the upgrade is searchable.
    @Test func notesAreSearchableAfterTheUpgrade() async throws {
        let queue = try version1()
        try insertVersion1Fixture(into: queue)
        try HashiyaDatabase.migrator.migrate(queue, upTo: "v2")
        try HashiyaDatabase.migrator.migrate(queue)
        let store = PaperStore(writer: queue)

        #expect(try await store.saveNotes(openAlexID: "W2", notes: PaperNotes(method: "Ablation study"), updatedAt: 1))

        #expect(await first(store.observeLibrary(match: "\"ablation*\"", status: nil))?.papers.map(\.paper.id) == ["b"])
    }
```

`ios/HashiyaKit/Tests/HashiyaDatabaseTests/PaperStoreTests.swift`:

1. Add `import HashiyaModel` after `import HashiyaDatabase`.
2. In `deleteReturnsTheRowAndRemovesItsAuthorsAndSearchRow`, replace the two `deleted?.…` lines with:
```swift
        #expect(deleted?.saved.paper == record)
        #expect(deleted?.saved.authors.map(\.name) == ["Ada", "Grace"])
        #expect(deleted?.notes == nil)
```
3. In `reinsertingADeletedRowKeepsItsIDSavedAtStatusAndSearchRow`, replace the insert line with:
```swift
        try await store.insert(paper: deleted.saved.paper, authors: deleted.saved.authors, search: deleted.saved.searchRow)
```
4. Add at the end of the struct:
```swift
    // MARK: Notes

    private func noteRow(_ paperID: String) throws -> PaperNotesRecord? {
        try queue.read { db in try PaperNotesRecord.fetchOne(db, key: paperID) }
    }

    private func searchNotes(_ paperID: String) throws -> String? {
        try queue.read { db in try String.fetchOne(db, sql: "SELECT notes FROM paper_search WHERE paper_id = ?", arguments: [paperID]) }
    }

    @Test func savingNotesCreatesUpdatesAndDeletesTheRow() async throws {
        try await save(paper(1, savedAt: 1_000))

        #expect(try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(summary: "First"), updatedAt: 5))
        #expect(try noteRow("local-1") == PaperNotesRecord(paperID: "local-1", notes: PaperNotes(summary: "First"), updatedAt: 5))

        #expect(try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(summary: "First", thoughts: "Later"), updatedAt: 6))
        #expect(try noteRow("local-1")?.notes == PaperNotes(summary: "First", thoughts: "Later"))
        #expect(try noteRow("local-1")?.updatedAt == 6)
        #expect(try count("paper_notes") == 1)

        #expect(try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(summary: "  ", method: "\n"), updatedAt: 7))
        #expect(try noteRow("local-1") == nil)
        #expect(try count("paper_notes") == 0)
    }

    @Test func theSearchColumnFollowsEverySave() async throws {
        try await save(paper(1, savedAt: 1_000))

        try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(summary: "Café", keyFindings: "التَّعلُّم"), updatedAt: 1)
        #expect(try searchNotes("local-1") == "cafe   التعلم  ")

        try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(), updatedAt: 2)
        #expect(try searchNotes("local-1") == "")
    }

    @Test func savingNotesForAnUnsavedPaperWritesNothing() async throws {
        #expect(try await store.saveNotes(openAlexID: "W404", notes: PaperNotes(summary: "x"), updatedAt: 1) == false)
        #expect(try count("paper_notes") == 0)
    }

    @Test func aSearchFindsAWordOnlyInTheNotesWithFolding() async throws {
        try await save(paper(1, title: "Deep learning", savedAt: 1_000))
        try await save(paper(2, title: "Other", savedAt: 2_000))
        try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(thoughts: "Try the ablation on التَّعلُّم المعزّز"), updatedAt: 1)

        #expect(await ids(match: "\"ablation*\"") == ["local-1"])
        #expect(await ids(match: "\"التعلم*\"") == ["local-1"])
        #expect(await ids(match: "\"ablation*\" \"deep*\"") == ["local-1"])
        #expect(await ids(match: "\"ablation*\" \"other*\"") == [])
    }

    /// SQLite doesn't report a virtual table's writes to GRDB, so `saveNotes` notifies `papers` itself.
    @Test(.timeLimit(.minutes(1)))
    func anOpenLibrarySearchSeesANewNote() async throws {
        try await save(paper(1, savedAt: 1_000))
        var iterator = store.observeLibrary(match: "\"ablation*\"", status: nil).makeAsyncIterator()
        #expect(await iterator.next()?.papers.isEmpty == true)

        try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(method: "Ablation"), updatedAt: 1)

        #expect(await iterator.next()?.papers.map(\.paper.id) == ["local-1"])
    }

    @Test func notesAreReadByOpenAlexID() async throws {
        try await save(paper(1, savedAt: 1_000))
        #expect(try await store.notes(openAlexID: "W1") == nil)

        try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(summary: "S"), updatedAt: 3)

        #expect(try await store.notes(openAlexID: "W1") == PaperNotesRecord(paperID: "local-1", notes: PaperNotes(summary: "S"), updatedAt: 3))
        #expect(try await store.notes(openAlexID: "W404") == nil)
    }

    @Test func observePaperEmitsThePaperThenNilAfterDelete() async throws {
        try await save(paper(1, savedAt: 1_000), "Ada", "Grace")
        var iterator = store.observePaper(openAlexID: "W1").makeAsyncIterator()

        let first = await iterator.next()
        #expect(first??.paper.id == "local-1")
        #expect(first??.authors.map(\.name) == ["Ada", "Grace"])

        _ = try await store.deleteByOpenAlexID("W1")
        #expect(await value(of: store.observePaper(openAlexID: "W1")) == .some(nil))
        let next = await iterator.next()
        #expect(next == .some(nil))
    }

    @Test func deleteReturnsTheNotesAndLeavesNoRow() async throws {
        try await save(paper(1, savedAt: 1_000))
        try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(limitations: "Small sample"), updatedAt: 4)

        let deleted = try #require(try await store.deleteByOpenAlexID("W1"))

        #expect(deleted.notes == PaperNotesRecord(paperID: "local-1", notes: PaperNotes(limitations: "Small sample"), updatedAt: 4))
        #expect(try count("paper_notes") == 0)
        #expect(try count("paper_search") == 0)
    }

    @Test func insertingWithNotesRestoresTheRowAndTheSearchColumn() async throws {
        try await save(paper(1, savedAt: 1_000), "Ada")
        try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(method: "Ablation"), updatedAt: 4)
        let deleted = try #require(try await store.deleteByOpenAlexID("W1"))
        let notes = try #require(deleted.notes)

        try await store.insert(
            paper: deleted.saved.paper,
            authors: deleted.saved.authors,
            search: deleted.saved.searchRow(notes: notes.notes),
            notes: notes
        )

        #expect(try noteRow("local-1") == notes)
        #expect(try searchNotes("local-1") == "  ablation   ")
        #expect(await ids(match: "\"ablation*\"") == ["local-1"])
    }

    @Test func theNotesTextFoldsEverySectionAndIsEmptyForBlankNotes() {
        #expect(PaperSearchRow.notesText(PaperNotes()) == "")
        #expect(PaperSearchRow.notesText(PaperNotes(summary: "  ")) == "")
        #expect(PaperSearchRow.notesText(PaperNotes(
            summary: "A", researchQuestion: "B", method: "C", keyFindings: "D", limitations: "E", thoughts: "Ö"
        )) == "a b c d e o")
    }
```

The sections are joined with single spaces, so a lone Method is `"  ablation   "`: two empty sections before it and three after.

- [ ] **Step 2: Run them and see them fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaDatabaseTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL: `cannot find 'PaperNotesRecord' in scope`, `value of type 'PaperWithAuthors' has no member 'saved'`, then `** TEST FAILED **`.

- [ ] **Step 3: Implement**

`ios/HashiyaKit/Sources/HashiyaDatabase/HashiyaDatabase.swift`:

1. Replace the `migrator`'s doc comment with:
```swift
    /// `v1`: Android's Room version 1 schema. `v2`: Android's version 2 — the reading status and the search index.
    /// `v3`: Android's version 3 — the notes table, and the search index rebuilt with a notes column.
```
2. In `"v2"`, replace the backfill's `try PaperSearchRow.make(…).insert(db)` statement with a frozen five-column insert (a shipped migration must never change with the current schema):
```swift
            for row in try Row.fetchAll(db, sql: "SELECT id, title, abstract, venue FROM papers") {
                let id: String = row["id"]
                let search = PaperSearchRow.make(
                    paperID: id,
                    title: row["title"],
                    authorNames: authorNames[id] ?? [],
                    abstract: row["abstract"],
                    venue: row["venue"]
                )
                // The v2 index has these five columns; `PaperSearchRow.insert` writes the current schema's.
                try db.execute(
                    sql: "INSERT INTO paper_search (paper_id, title, authors, abstract, venue) VALUES (?, ?, ?, ?, ?)",
                    arguments: [search.paperID, search.title, search.authors, search.abstract, search.venue]
                )
            }
```
3. After the `"v2"` registration, add:
```swift
        migrator.registerMigration("v3") { db in
            // An FTS table can't be altered: copy its rows out, recreate it with `notes`, and copy them back
            // unchanged (nothing is re-normalized; no paper has notes yet).
            try db.execute(sql: """
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
                """)
        }
```

`ios/HashiyaKit/Sources/HashiyaDatabase/Records.swift`:

1. Replace the whole `PaperSearchRow` struct with:
```swift
/// A row of the full-text index `paper_search`: one per saved paper, keyed by the paper's local id (stored, not
/// indexed). FTS rows don't cascade, so `PaperStore` writes and deletes them with their paper.
public struct PaperSearchRow: Equatable, Sendable {
    public var paperID: String
    public var title: String
    /// Author names joined with spaces.
    public var authors: String
    public var abstract: String
    public var venue: String
    /// `notesText` of the paper's notes; "" when it has none.
    public var notes: String

    public init(paperID: String, title: String, authors: String, abstract: String, venue: String, notes: String = "") {
        self.paperID = paperID
        self.title = title
        self.authors = authors
        self.abstract = abstract
        self.venue = venue
        self.notes = notes
    }

    /// The row for a paper, every column passed through `searchableText`. New saves, Undo and migration `v2` use it.
    public static func make(
        paperID: String,
        title: String,
        authorNames: [String],
        abstract: String?,
        venue: String?,
        notes: PaperNotes? = nil
    ) -> PaperSearchRow {
        PaperSearchRow(
            paperID: paperID,
            title: searchableText(title),
            authors: searchableText(authorNames.joined(separator: " ")),
            abstract: searchableText(abstract ?? ""),
            venue: searchableText(venue ?? ""),
            notes: notes.map(notesText) ?? ""
        )
    }

    /// The six sections joined with spaces, through `searchableText`; "" for blank notes, which have no row.
    public static func notesText(_ notes: PaperNotes) -> String {
        guard !notes.isEmpty else { return "" }
        return searchableText(NoteSection.allCases.map { notes[$0] }.joined(separator: " "))
    }

    func insert(_ db: Database) throws {
        try db.execute(
            sql: "INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes) VALUES (?, ?, ?, ?, ?, ?)",
            arguments: [paperID, title, authors, abstract, venue, notes]
        )
    }
}

/// A row of `paper_notes`: a saved paper's notes. It exists only while some section isn't blank.
public struct PaperNotesRecord: Codable, Equatable, Sendable, FetchableRecord, PersistableRecord {
    public static let databaseTableName = "paper_notes"

    public var paperID: String
    public var summary: String
    public var researchQuestion: String
    public var method: String
    public var keyFindings: String
    public var limitations: String
    public var thoughts: String
    /// Epoch milliseconds of the last save. Nothing reads it yet.
    public var updatedAt: Int64

    public init(paperID: String, notes: PaperNotes, updatedAt: Int64) {
        self.paperID = paperID
        summary = notes.summary
        researchQuestion = notes.researchQuestion
        method = notes.method
        keyFindings = notes.keyFindings
        limitations = notes.limitations
        thoughts = notes.thoughts
        self.updatedAt = updatedAt
    }

    public var notes: PaperNotes {
        PaperNotes(
            summary: summary,
            researchQuestion: researchQuestion,
            method: method,
            keyFindings: keyFindings,
            limitations: limitations,
            thoughts: thoughts
        )
    }

    enum CodingKeys: String, CodingKey {
        case summary, method, limitations, thoughts
        case paperID = "paper_id"
        case researchQuestion = "research_question"
        case keyFindings = "key_findings"
        case updatedAt = "updated_at"
    }
}
```
2. In `PaperWithAuthors`, replace the `searchRow` property with:
```swift
    /// This paper's row in the search index, with no notes.
    public var searchRow: PaperSearchRow {
        searchRow(notes: nil)
    }

    /// This paper's row in the search index with `notes` in its notes column.
    public func searchRow(notes: PaperNotes?) -> PaperSearchRow {
        PaperSearchRow.make(
            paperID: paper.id,
            title: paper.title,
            authorNames: authors.sorted { $0.position < $1.position }.map(\.name),
            abstract: paper.abstract,
            venue: paper.venue,
            notes: notes
        )
    }
```
3. After `PaperWithAuthors`, add:
```swift
/// What `PaperStore.deleteByOpenAlexID` deleted: the paper with its authors, and its notes if it had any.
public struct DeletedPaper: Equatable, Sendable {
    public var saved: PaperWithAuthors
    public var notes: PaperNotesRecord?

    public init(saved: PaperWithAuthors, notes: PaperNotesRecord?) {
        self.saved = saved
        self.notes = notes
    }
}
```

`ios/HashiyaKit/Sources/HashiyaDatabase/PaperStore.swift`:

1. Add `import HashiyaModel` after `import GRDB`.
2. After `observeSavedOpenAlexIDs()`, add:
```swift
    /// The saved paper with its authors, or nil once it isn't saved. Each call starts its own observation.
    public func observePaper(openAlexID: String) -> AsyncStream<PaperWithAuthors?> {
        stream(ValueObservation.tracking { db in try Self.paper(db, openAlexID: openAlexID) })
    }

    /// A saved paper's notes, read once; nil when it has none or isn't saved.
    public func notes(openAlexID: String) async throws -> PaperNotesRecord? {
        try await writer.read { db in
            try PaperNotesRecord.fetchOne(
                db,
                sql: """
                    SELECT paper_notes.* FROM paper_notes
                    JOIN papers ON papers.id = paper_notes.paper_id
                    WHERE papers.open_alex_id = ?
                    """,
                arguments: [openAlexID]
            )
        }
    }

    /// Saves a saved paper's notes, or deletes them when blank, and updates its search row. Returns false, writing
    /// nothing, when the paper isn't saved.
    @discardableResult
    public func saveNotes(openAlexID: String, notes: PaperNotes, updatedAt: Int64) async throws -> Bool {
        try await writer.write { db in
            guard let paperID = try String.fetchOne(db, sql: "SELECT id FROM papers WHERE open_alex_id = ?", arguments: [openAlexID]) else {
                return false
            }
            if notes.isEmpty {
                try db.execute(sql: "DELETE FROM paper_notes WHERE paper_id = ?", arguments: [paperID])
            } else {
                try PaperNotesRecord(paperID: paperID, notes: notes, updatedAt: updatedAt).insert(db, onConflict: .replace)
            }
            try db.execute(
                sql: "UPDATE paper_search SET notes = ? WHERE paper_id = ?",
                arguments: [PaperSearchRow.notesText(notes), paperID]
            )
            // SQLite's update hook doesn't report writes to a virtual table, so an open Library search wouldn't
            // fetch again and miss the note.
            try db.notifyChanges(in: Table(PaperRecord.databaseTableName))
            return true
        }
    }

    static func paper(_ db: Database, openAlexID: String) throws -> PaperWithAuthors? {
        guard let paper = try PaperRecord.filter(Column("open_alex_id") == openAlexID).fetchOne(db) else {
            return nil
        }
        let authors = try PaperAuthorRecord
            .filter(Column("paper_id") == paper.id)
            .order(Column("position"))
            .fetchAll(db)
        return PaperWithAuthors(paper: paper, authors: authors)
    }
```
3. Replace `insert(paper:authors:search:)` with:
```swift
    /// Inserts the paper unless one with the same `id` or `open_alex_id` exists, then its authors, its search row and
    /// its notes. Returns false, writing nothing, when the paper already exists. `search.notes` must already hold
    /// `notes`' search text.
    @discardableResult
    public func insert(
        paper: PaperRecord,
        authors: [PaperAuthorRecord],
        search: PaperSearchRow,
        notes: PaperNotesRecord? = nil
    ) async throws -> Bool {
        precondition(search.paperID == paper.id, "The search row must belong to the paper")
        precondition(notes.map { $0.paperID == paper.id } ?? true, "The notes must belong to the paper")
        return try await writer.write { db in
            try paper.insert(db, onConflict: .ignore)
            guard db.changesCount > 0 else { return false }
            for author in authors {
                try author.insert(db)
            }
            try search.insert(db)
            try notes?.insert(db)
            return true
        }
    }
```
4. Replace `deleteByOpenAlexID(_:)` with:
```swift
    /// Deletes the paper (its authors and notes cascade) and its search row, and returns what was deleted, or nil if
    /// it was not saved.
    public func deleteByOpenAlexID(_ openAlexID: String) async throws -> DeletedPaper? {
        try await writer.write { db in
            guard let saved = try Self.paper(db, openAlexID: openAlexID) else { return nil }
            let notes = try PaperNotesRecord.fetchOne(db, key: saved.paper.id)
            try saved.paper.delete(db)
            // FTS rows don't cascade.
            try db.execute(sql: "DELETE FROM paper_search WHERE paper_id = ?", arguments: [saved.paper.id])
            return DeletedPaper(saved: saved, notes: notes)
        }
    }
```
5. In `notifyExternalChanges()`, add after the `PaperAuthorRecord` line:
```swift
            try db.notifyChanges(in: Table(PaperNotesRecord.databaseTableName))
```

`ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift`: in `GRDBLibraryRepository.remove(openAlexID:)`, replace the body with (Task 3 adds the notes):
```swift
        guard let deleted = try await store.deleteByOpenAlexID(openAlexID) else { return nil }
        let saved = deleted.saved.asLibraryPaper()
        return RemovedPaper(paper: saved.paper, localID: deleted.saved.paper.id, savedAt: deleted.saved.paper.savedAt, status: saved.status)
```

- [ ] **Step 4: Run them and see them pass**

Run: the Step 2 command, then the same for `-only-testing:HashiyaDataTests` (the repository still passes).
Expected: PASS: `** TEST SUCCEEDED **` for both.

- [ ] **Step 5: Commit, push and read CI**

```bash
git add ios/HashiyaKit/Package.swift ios/HashiyaKit/Sources/HashiyaDatabase/HashiyaDatabase.swift \
  ios/HashiyaKit/Sources/HashiyaDatabase/Records.swift ios/HashiyaKit/Sources/HashiyaDatabase/PaperStore.swift \
  ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift \
  ios/HashiyaKit/Tests/HashiyaDatabaseTests/MigrationTests.swift ios/HashiyaKit/Tests/HashiyaDatabaseTests/PaperStoreTests.swift
git commit -m "feat: store paper notes and search them on iOS (migration v3)"
git log --format='%an <%ae>' origin/main..HEAD   # only Fady <fady.fouad.a@gmail.com>
git push -u origin feat/ios-paper-details-and-notes
```
Then list the "iOS" workflow's runs for the branch (`mcp__github__actions_list`, `list_workflow_runs`, `ios.yml`, branch filter) and wait for the run on this commit's SHA. Expected: `success`. On failure, read the failed job's log (`mcp__github__get_job_logs`, `failed_only: true`) and fix before Task 3.

---
### Task 3: `HashiyaData` — the notes in the repository, Undo with notes, `PendingWrites`, and the fake

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift`
- Create: `ios/HashiyaKit/Sources/HashiyaData/PendingWrites.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaTesting/FakeLibraryRepository.swift` (full replacement below; it keeps every existing member)
- Test: Modify `ios/HashiyaKit/Tests/HashiyaDataTests/GRDBLibraryRepositoryTests.swift`; create `ios/HashiyaKit/Tests/HashiyaDataTests/PendingWritesTests.swift` and `FakeLibraryRepositoryTests.swift`

**Interfaces:**
- Consumes: Task 2's `PaperStore` operations, `DeletedPaper`, `PaperNotesRecord`, `PaperWithAuthors.searchRow(notes:)`.
- Produces (module `HashiyaData`, `public`):
  - `LibraryRepository`:
    - `observePaper(openAlexID:) -> AsyncStream<LibraryPaper?>`;
    - `notes(openAlexID:) async throws -> PaperNotes`;
    - `saveNotes(openAlexID:notes:) async throws`.
  - `RemovedPaper.notes: PaperNotes`, and `init(paper:localID:savedAt:status:notes:)` with `notes` defaulting to `PaperNotes()`.
  - `@MainActor final class PendingWrites`: `init()`, `track<T>(_ task: Task<T, Never>)`, `drained() async`, `isIdle: Bool`.
- Produces (module `HashiyaTesting`): `FakeLibraryRepository` implements the members above and adds:
  - `init(saved:statuses:notes:)`, where `notes` is keyed by OpenAlex ID;
  - `notes(of:) -> PaperNotes`;
  - `setFailSaveNotes(_:)`, `setFailNotesRead(_:)`;
  - `holdNotesSaves()`, `releaseNotesSaves()`, `heldNotesSaves: Int`;
  - `notesWriteAttempts: [PaperNotes]`, every `saveNotes` call in order once it runs, failed ones included.

  Its stand-in search also matches the notes.

- [ ] **Step 1: Write the failing tests**

Add to `ios/HashiyaKit/Tests/HashiyaDataTests/GRDBLibraryRepositoryTests.swift`, at the end of the struct:
```swift
    // MARK: Notes

    @Test func observePaperEmitsThePaperWithItsStatusThenNilAfterRemove() async throws {
        try await repository.save(SamplePapers.attention)
        try await repository.setStatus(openAlexID: SamplePapers.attention.openAlexID, status: .reading)

        #expect(await value(of: repository.observePaper(openAlexID: SamplePapers.attention.openAlexID))
            == .some(LibraryPaper(paper: SamplePapers.attention, status: .reading)))

        _ = try await repository.remove(openAlexID: SamplePapers.attention.openAlexID)
        #expect(await value(of: repository.observePaper(openAlexID: SamplePapers.attention.openAlexID)) == .some(nil))
    }

    @Test func notesAreEmptyWhenThereAreNoneOrThePaperIsNotSaved() async throws {
        try await repository.save(paper("W1"))
        #expect(try await repository.notes(openAlexID: "W1") == PaperNotes())
        #expect(try await repository.notes(openAlexID: "W404") == PaperNotes())
    }

    @Test func savedNotesReadBack() async throws {
        try await repository.save(paper("W1"))
        let notes = PaperNotes(summary: "Transformers", method: "Self-attention", thoughts: "أفكار")

        try await repository.saveNotes(openAlexID: "W1", notes: notes)

        #expect(try await repository.notes(openAlexID: "W1") == notes)
    }

    @Test func savingNotesForAnUnsavedPaperDoesNothing() async throws {
        try await repository.saveNotes(openAlexID: "W404", notes: PaperNotes(summary: "x"))
        #expect(try await repository.notes(openAlexID: "W404") == PaperNotes())
        #expect(await library()?.libraryTotal == 0)
    }

    @Test func theLibrarySearchFindsAWordOnlyInTheNotes() async throws {
        try await repository.save(paper("W1", title: "Deep nets"))
        try await repository.save(paper("W2", title: "Other"))
        try await repository.saveNotes(openAlexID: "W1", notes: PaperNotes(keyFindings: "Ablation shows التَّعلُّم helps"))

        #expect(await ids("ablation") == ["W1"])
        #expect(await ids("التعلم") == ["W1"])
        #expect(await ids("ABLAT deep") == ["W1"])
        #expect(await ids("ablation other") == [])
    }

    @Test func removeThenRestoreKeepsTheNotesAndTheirSearch() async throws {
        try await repository.save(paper("W1"))
        try await repository.setStatus(openAlexID: "W1", status: .read)
        let notes = PaperNotes(limitations: "Small sample")
        try await repository.saveNotes(openAlexID: "W1", notes: notes)

        let removed = try #require(try await repository.remove(openAlexID: "W1"))
        #expect(removed.notes == notes)
        #expect(removed.status == .read)
        #expect(await ids("sample") == [])

        try await repository.restore(removed)

        #expect(try await repository.notes(openAlexID: "W1") == notes)
        #expect(await ids("sample") == ["W1"])
        #expect(await library()?.papers.first?.status == .read)
    }

    @Test func restoringAPaperWithoutNotesAddsNoNotes() async throws {
        try await repository.save(paper("W1"))
        let removed = try #require(try await repository.remove(openAlexID: "W1"))
        #expect(removed.notes == PaperNotes())

        try await repository.restore(removed)

        #expect(try await repository.notes(openAlexID: "W1") == PaperNotes())
        let rows = try await queue.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM paper_notes") }
        #expect(rows == 0)
    }
```

`ios/HashiyaKit/Tests/HashiyaDataTests/PendingWritesTests.swift`:
```swift
import HashiyaData
import HashiyaTesting
import Testing

@MainActor
struct PendingWritesTests {
    @Test func isIdleWithNothingTracked() async {
        let writes = PendingWrites()
        #expect(writes.isIdle)
        await writes.drained()
    }

    @Test func drainedWaitsForARunningWrite() async {
        let writes = PendingWrites()
        let gate = AsyncGate()
        let write = Task { await gate.wait(); return true }
        writes.track(write)
        #expect(!writes.isIdle)

        let drained = Task { await writes.drained(); return true }
        try? await Task.sleep(for: .milliseconds(50))
        #expect(!writes.isIdle)

        gate.open()
        #expect(await drained.value)
        #expect(writes.isIdle)
    }

    @Test func drainedWaitsForEveryTrackedWrite() async {
        let writes = PendingWrites()
        let first = AsyncGate()
        let second = AsyncGate()
        writes.track(Task { await first.wait() })
        writes.track(Task { await second.wait() })

        first.open()
        #expect(await eventually { !writes.isIdle })
        second.open()
        await writes.drained()
        #expect(writes.isIdle)
    }
}

/// Suspends `wait()` callers until `open()`.
@MainActor
private final class AsyncGate {
    private var isOpen = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func wait() async {
        guard !isOpen else { return }
        await withCheckedContinuation { waiters.append($0) }
    }

    func open() {
        isOpen = true
        let waiting = waiters
        waiters = []
        waiting.forEach { $0.resume() }
    }
}
```

`ios/HashiyaKit/Tests/HashiyaDataTests/FakeLibraryRepositoryTests.swift`:
```swift
import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

struct FakeLibraryRepositoryTests {
    private func first<T: Sendable>(_ stream: AsyncStream<T>) async -> T? {
        for await value in stream {
            return value
        }
        return nil
    }

    @Test func notesSaveReadAndFollowRemoveAndRestore() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let id = SamplePapers.attention.openAlexID
        #expect(try await library.notes(openAlexID: id) == PaperNotes())

        try await library.saveNotes(openAlexID: id, notes: PaperNotes(method: "Ablation"))
        #expect(try await library.notes(openAlexID: id) == PaperNotes(method: "Ablation"))
        #expect(await first(library.observeLibrary(query: "ablation", status: nil))?.papers.map(\.id) == [id])

        let removed = try #require(try await library.remove(openAlexID: id))
        #expect(removed.notes == PaperNotes(method: "Ablation"))
        #expect(try await library.notes(openAlexID: id) == PaperNotes())
        try await library.restore(removed)
        #expect(library.notes(of: id) == PaperNotes(method: "Ablation"))
    }

    @Test func savingNotesForAnUnsavedPaperDoesNothing() async throws {
        let library = FakeLibraryRepository()
        try await library.saveNotes(openAlexID: "W404", notes: PaperNotes(summary: "x"))
        #expect(library.notes(of: "W404") == PaperNotes())
        #expect(library.notesWriteAttempts == [PaperNotes(summary: "x")])
    }

    @Test func observePaperFollowsStatusAndRemoval() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.bert])
        var iterator = library.observePaper(openAlexID: SamplePapers.bert.openAlexID).makeAsyncIterator()
        #expect(await iterator.next() == .some(LibraryPaper(paper: SamplePapers.bert, status: .toRead)))

        try await library.setStatus(openAlexID: SamplePapers.bert.openAlexID, status: .read)
        #expect(await iterator.next() == .some(LibraryPaper(paper: SamplePapers.bert, status: .read)))

        _ = try await library.remove(openAlexID: SamplePapers.bert.openAlexID)
        #expect(await iterator.next() == .some(nil))
    }

    @Test func failuresAndHeldSaves() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.vit], notes: [SamplePapers.vit.openAlexID: PaperNotes(summary: "S")])
        let id = SamplePapers.vit.openAlexID
        #expect(library.notes(of: id) == PaperNotes(summary: "S"))

        library.setFailNotesRead(true)
        await #expect(throws: FakeLibraryRepository.Failure.self) { try await library.notes(openAlexID: id) }
        library.setFailSaveNotes(true)
        await #expect(throws: FakeLibraryRepository.Failure.self) { try await library.saveNotes(openAlexID: id, notes: PaperNotes()) }
        library.setFailSaveNotes(false)

        library.holdNotesSaves()
        let save = Task { try await library.saveNotes(openAlexID: id, notes: PaperNotes(summary: "Held")) }
        while library.heldNotesSaves == 0 { await Task.yield() }
        #expect(library.notes(of: id) == PaperNotes(summary: "S"))
        library.releaseNotesSaves()
        try await save.value
        #expect(library.notes(of: id) == PaperNotes(summary: "Held"))
        #expect(library.notesWriteAttempts == [PaperNotes(), PaperNotes(summary: "Held")])
    }
}
```

- [ ] **Step 2: Run them and see them fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaDataTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL: `value of type 'GRDBLibraryRepository' has no member 'observePaper'`, `cannot find 'PendingWrites' in scope`, then `** TEST FAILED **`.

- [ ] **Step 3: Implement**

`ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift`:

1. In `protocol LibraryRepository`, after `refreshAfterExternalChanges()`, add:
```swift
    /// The saved paper with its status; nil when it isn't saved or stops being saved. Each call returns a new stream
    /// starting with the current value.
    func observePaper(openAlexID: String) -> AsyncStream<LibraryPaper?>
    /// The paper's notes, read once; empty when it has none or isn't saved.
    func notes(openAlexID: String) async throws -> PaperNotes
    /// Saves the notes (blank notes delete them) and updates the search index. Not saved → no-op.
    func saveNotes(openAlexID: String, notes: PaperNotes) async throws
```
2. Replace `struct RemovedPaper` with:
```swift
/// What `remove` deleted, so Undo can put it back in the same place with the same status and notes.
public struct RemovedPaper: Equatable, Sendable {
    public var paper: Paper
    public var localID: String
    public var savedAt: Int64
    public var status: ReadingStatus
    public var notes: PaperNotes

    public init(paper: Paper, localID: String, savedAt: Int64, status: ReadingStatus, notes: PaperNotes = PaperNotes()) {
        self.paper = paper
        self.localID = localID
        self.savedAt = savedAt
        self.status = status
        self.notes = notes
    }
}
```
3. In `GRDBLibraryRepository`, replace `remove(openAlexID:)` and `restore(_:)` with:
```swift
    public func remove(openAlexID: String) async throws -> RemovedPaper? {
        guard let deleted = try await store.deleteByOpenAlexID(openAlexID) else { return nil }
        let saved = deleted.saved.asLibraryPaper()
        return RemovedPaper(
            paper: saved.paper,
            localID: deleted.saved.paper.id,
            savedAt: deleted.saved.paper.savedAt,
            status: saved.status,
            notes: deleted.notes?.notes ?? PaperNotes()
        )
    }

    public func restore(_ removed: RemovedPaper) async throws {
        let records = removed.paper.asRecords(localID: removed.localID, savedAt: removed.savedAt, status: removed.status)
        let notes = removed.notes.isEmpty ? nil : removed.notes
        try await store.insert(
            paper: records.paper,
            authors: records.authors,
            search: records.searchRow(notes: notes),
            notes: notes.map { PaperNotesRecord(paperID: removed.localID, notes: $0, updatedAt: now()) }
        )
    }
```
4. After `refreshAfterExternalChanges()` in `GRDBLibraryRepository`, add:
```swift
    public func observePaper(openAlexID: String) -> AsyncStream<LibraryPaper?> {
        store.observePaper(openAlexID: openAlexID).mapped { $0?.asLibraryPaper() }
    }

    public func notes(openAlexID: String) async throws -> PaperNotes {
        try await store.notes(openAlexID: openAlexID)?.notes ?? PaperNotes()
    }

    public func saveNotes(openAlexID: String, notes: PaperNotes) async throws {
        try await store.saveNotes(openAlexID: openAlexID, notes: notes, updatedAt: now())
    }
```

`ios/HashiyaKit/Sources/HashiyaData/PendingWrites.swift`:
```swift
/// Writes that must finish before the app suspends the shared database (`SharedLibraryDatabase.suspend()`), which
/// would refuse them. The Details screen tracks its note writes here; the app waits for `drained()` when it moves
/// to the background.
@MainActor
public final class PendingWrites {
    private var running = 0
    private var waiters: [CheckedContinuation<Void, Never>] = []

    public init() {}

    /// True when no tracked write is running.
    public var isIdle: Bool { running == 0 }

    /// Tracks `task` until it ends. Registering is synchronous, so a write started in the same main-actor turn as
    /// a later `drained()` call is always waited for.
    public func track<T: Sendable>(_ task: Task<T, Never>) {
        running += 1
        Task {
            _ = await task.value
            running -= 1
            guard running == 0 else { return }
            let waiting = waiters
            waiters = []
            waiting.forEach { $0.resume() }
        }
    }

    /// Returns once every tracked write has ended.
    public func drained() async {
        guard running > 0 else { return }
        await withCheckedContinuation { waiters.append($0) }
    }
}
```

`ios/HashiyaKit/Sources/HashiyaTesting/FakeLibraryRepository.swift`: replace the whole file with:
```swift
import Foundation
import HashiyaData
import HashiyaModel
import os

/// An in-memory library with live streams. Saves get increasing times, so the newest is first.
/// Its search is a simple stand-in for the real index: every typed word must start a word of the paper's title,
/// authors, abstract, venue or notes, compared through `searchableText`.
public final class FakeLibraryRepository: LibraryRepository {
    public struct Failure: Error {}

    private struct Entry {
        var paper: Paper
        var localID: String
        var savedAt: Int64
        var status: ReadingStatus
        var notes: PaperNotes
    }

    private struct Subscription {
        let query: String
        let status: ReadingStatus?
        let continuation: AsyncStream<LibrarySnapshot>.Continuation
    }

    private struct PaperSubscription {
        let openAlexID: String
        let continuation: AsyncStream<LibraryPaper?>.Continuation
    }

    private struct State {
        var entries: [Entry] = []
        var clock: Int64 = 0
        var failSaves = false
        var failRemoves = false
        var failStatusUpdates = false
        var failSaveNotes = false
        var failNotesRead = false
        /// Non-nil while saves are held: the waiting saves.
        var heldSaves: [CheckedContinuation<Void, Never>]?
        var notesWriteAttempts: [PaperNotes] = []
        var subscriptions: [UUID: Subscription] = [:]
        var paperSubscriptions: [UUID: PaperSubscription] = [:]
        var idContinuations: [UUID: AsyncStream<Set<String>>.Continuation] = [:]

        /// Newest saved first.
        var sorted: [Entry] { entries.sorted { $0.savedAt > $1.savedAt } }
        var library: [LibraryPaper] { sorted.map { LibraryPaper(paper: $0.paper, status: $0.status) } }
        var ids: Set<String> { Set(entries.map(\.paper.openAlexID)) }

        func snapshot(query: String, status: ReadingStatus?) -> LibrarySnapshot {
            let matching = sorted
                .filter { FakeLibraryRepository.matches($0.paper, notes: $0.notes, query: query) }
                .map { LibraryPaper(paper: $0.paper, status: $0.status) }
            var counts = Dictionary(uniqueKeysWithValues: ReadingStatus.allCases.map { ($0, 0) })
            for paper in matching {
                counts[paper.status, default: 0] += 1
            }
            return LibrarySnapshot(
                papers: matching.filter { status == nil || $0.status == status },
                counts: counts,
                libraryTotal: entries.count
            )
        }

        func paper(_ openAlexID: String) -> LibraryPaper? {
            entries.first { $0.paper.openAlexID == openAlexID }.map { LibraryPaper(paper: $0.paper, status: $0.status) }
        }

        func publish() {
            for subscription in subscriptions.values {
                subscription.continuation.yield(snapshot(query: subscription.query, status: subscription.status))
            }
            for subscription in paperSubscriptions.values {
                subscription.continuation.yield(paper(subscription.openAlexID))
            }
            let ids = ids
            idContinuations.values.forEach { $0.yield(ids) }
        }
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    /// `saved` is the initial library, newest first; `statuses` gives some of them a status by OpenAlex ID (else To
    /// read), and `notes` some notes.
    public init(saved: [Paper] = [], statuses: [String: ReadingStatus] = [:], notes: [String: PaperNotes] = [:]) {
        state.withLock { state in
            for paper in saved.reversed() {
                state.clock += 1
                state.entries.append(Entry(
                    paper: paper,
                    localID: "local-\(paper.openAlexID)",
                    savedAt: state.clock,
                    status: statuses[paper.openAlexID] ?? .toRead,
                    notes: notes[paper.openAlexID] ?? PaperNotes()
                ))
            }
        }
    }

    public var savedPapers: [Paper] { state.withLock { $0.library.map(\.paper) } }
    /// The library with statuses, newest first.
    public var library: [LibraryPaper] { state.withLock { $0.library } }
    /// A saved paper's stored notes; empty when it has none or isn't saved.
    public func notes(of openAlexID: String) -> PaperNotes {
        state.withLock { state in state.entries.first { $0.paper.openAlexID == openAlexID }?.notes ?? PaperNotes() }
    }
    /// Every `saveNotes` call in order once it runs (after any hold), failed ones included.
    public var notesWriteAttempts: [PaperNotes] { state.withLock { $0.notesWriteAttempts } }
    /// The saves waiting while saves are held.
    public var heldNotesSaves: Int { state.withLock { $0.heldSaves?.count ?? 0 } }

    /// When true, `save` and `restore` throw.
    public func setFailSaves(_ fail: Bool) { state.withLock { $0.failSaves = fail } }
    /// When true, `remove` throws.
    public func setFailRemoves(_ fail: Bool) { state.withLock { $0.failRemoves = fail } }
    /// When true, `setStatus` throws.
    public func setFailStatusUpdates(_ fail: Bool) { state.withLock { $0.failStatusUpdates = fail } }
    /// When true, `saveNotes` throws.
    public func setFailSaveNotes(_ fail: Bool) { state.withLock { $0.failSaveNotes = fail } }
    /// When true, `notes(openAlexID:)` throws.
    public func setFailNotesRead(_ fail: Bool) { state.withLock { $0.failNotesRead = fail } }

    /// From now on `saveNotes` waits until `releaseNotesSaves()`, so a test can see a write in progress.
    public func holdNotesSaves() {
        state.withLock { if $0.heldSaves == nil { $0.heldSaves = [] } }
    }

    /// Lets every held save run, in the order they started, and stops holding.
    public func releaseNotesSaves() {
        let waiting = state.withLock { state -> [CheckedContinuation<Void, Never>] in
            defer { state.heldSaves = nil }
            return state.heldSaves ?? []
        }
        waiting.forEach { $0.resume() }
    }

    public func observeLibrary(query: String, status: ReadingStatus?) -> AsyncStream<LibrarySnapshot> {
        let id = UUID()
        return AsyncStream { continuation in
            state.withLock { state in
                state.subscriptions[id] = Subscription(query: query, status: status, continuation: continuation)
                continuation.yield(state.snapshot(query: query, status: status))
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.withLock { $0.subscriptions.removeValue(forKey: id) }
            }
        }
    }

    public func observeSavedIDs() -> AsyncStream<Set<String>> {
        let id = UUID()
        return AsyncStream { continuation in
            state.withLock { state in
                state.idContinuations[id] = continuation
                continuation.yield(state.ids)
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.withLock { $0.idContinuations.removeValue(forKey: id) }
            }
        }
    }

    public func observePaper(openAlexID: String) -> AsyncStream<LibraryPaper?> {
        let id = UUID()
        return AsyncStream { continuation in
            state.withLock { state in
                state.paperSubscriptions[id] = PaperSubscription(openAlexID: openAlexID, continuation: continuation)
                continuation.yield(state.paper(openAlexID))
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.withLock { $0.paperSubscriptions.removeValue(forKey: id) }
            }
        }
    }

    public func save(_ paper: Paper) async throws {
        try state.withLock { state in
            if state.failSaves { throw Failure() }
            guard !state.ids.contains(paper.openAlexID) else { return }
            state.clock += 1
            state.entries.append(Entry(
                paper: paper, localID: "local-\(paper.openAlexID)", savedAt: state.clock, status: .toRead, notes: PaperNotes()
            ))
            state.publish()
        }
    }

    public func setStatus(openAlexID: String, status: ReadingStatus) async throws {
        try state.withLock { state in
            if state.failStatusUpdates { throw Failure() }
            guard let index = state.entries.firstIndex(where: { $0.paper.openAlexID == openAlexID }) else { return }
            state.entries[index].status = status
            state.publish()
        }
    }

    public func notes(openAlexID: String) async throws -> PaperNotes {
        try state.withLock { state in
            if state.failNotesRead { throw Failure() }
            return state.entries.first { $0.paper.openAlexID == openAlexID }?.notes ?? PaperNotes()
        }
    }

    public func saveNotes(openAlexID: String, notes: PaperNotes) async throws {
        await waitWhileHeld()
        try state.withLock { state in
            state.notesWriteAttempts.append(notes)
            if state.failSaveNotes { throw Failure() }
            guard let index = state.entries.firstIndex(where: { $0.paper.openAlexID == openAlexID }) else { return }
            state.entries[index].notes = notes
            state.publish()
        }
    }

    public func remove(openAlexID: String) async throws -> RemovedPaper? {
        try state.withLock { state in
            if state.failRemoves { throw Failure() }
            guard let index = state.entries.firstIndex(where: { $0.paper.openAlexID == openAlexID }) else { return nil }
            let entry = state.entries.remove(at: index)
            state.publish()
            return RemovedPaper(paper: entry.paper, localID: entry.localID, savedAt: entry.savedAt, status: entry.status, notes: entry.notes)
        }
    }

    /// Emits the current values again.
    public func refreshAfterExternalChanges() async {
        state.withLock { $0.publish() }
    }

    public func restore(_ removed: RemovedPaper) async throws {
        try state.withLock { state in
            if state.failSaves { throw Failure() }
            guard !state.ids.contains(removed.paper.openAlexID) else { return }
            state.entries.append(Entry(
                paper: removed.paper, localID: removed.localID, savedAt: removed.savedAt, status: removed.status, notes: removed.notes
            ))
            state.publish()
        }
    }

    private func waitWhileHeld() async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            let held = state.withLock { state -> Bool in
                guard state.heldSaves != nil else { return false }
                state.heldSaves?.append(continuation)
                return true
            }
            if !held { continuation.resume() }
        }
    }

    static func matches(_ paper: Paper, notes: PaperNotes = PaperNotes(), query: String) -> Bool {
        let wanted = words(query)
        guard !wanted.isEmpty else { return true }
        let text = [paper.title, paper.authors.map(\.name).joined(separator: " "), paper.abstract ?? "", paper.venue ?? ""]
            + NoteSection.allCases.map { notes[$0] }
        let available = words(text.joined(separator: " "))
        return wanted.allSatisfy { word in available.contains { $0.hasPrefix(word) } }
    }

    private static func words(_ text: String) -> [String] {
        searchableText(text).split { !$0.isLetter && !$0.isNumber }.map(String.init)
    }
}
```

- [ ] **Step 4: Run them and see them pass**

Run: the Step 2 command, then with `-only-testing:FeatureLibraryTests -only-testing:FeatureSearchTests` (they use the fake).
Expected: PASS: `** TEST SUCCEEDED **` for both.

- [ ] **Step 5: Commit, push and read CI**

```bash
git add ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift ios/HashiyaKit/Sources/HashiyaData/PendingWrites.swift \
  ios/HashiyaKit/Sources/HashiyaTesting/FakeLibraryRepository.swift \
  ios/HashiyaKit/Tests/HashiyaDataTests/GRDBLibraryRepositoryTests.swift ios/HashiyaKit/Tests/HashiyaDataTests/PendingWritesTests.swift \
  ios/HashiyaKit/Tests/HashiyaDataTests/FakeLibraryRepositoryTests.swift
git commit -m "feat: read and save notes in the iOS library repository, and bring them back with Undo"
git log --format='%an <%ae>' origin/main..HEAD
git push
```
Read the "iOS" run for this SHA as in Task 2. Expected: `success`.

---
### Task 4: `HashiyaDesignSystem` — **Open details** in the preview, and shared strings for Details

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/PaperPreviewContent.swift`, `Resources/Localizable.xcstrings`
- Create: `ios/HashiyaKit/Sources/HashiyaDesignSystem/DesignSystemStrings.swift`
- Test: Modify `ios/HashiyaKit/Tests/HashiyaDesignSystemTests/ReadingStatusSelectorTests.swift`; `ios/HashiyaSnapshotTests/DesignSystemSnapshotTests.swift`

**Interfaces:**
- Produces (module `HashiyaDesignSystem`, `public`):
  - `PaperPreviewContent.init(…, onOpenDetails: (() -> Void)? = nil)`: the new last parameter;
  - `@MainActor enum DesignSystemStrings`: `abstract`, `noAbstract`, `openDOI`, `removeFromLibrary`, `openAccess(hasPDF:)`.
- The status selector parameters (`status`, `onStatusChange`) stay until Task 7, when the Library preview, their only caller, goes away. Task 7 removes them and the `previewWithTheStatusSelector` snapshot.

- [ ] **Step 1: Write the failing tests**

In `ios/HashiyaKit/Tests/HashiyaDesignSystemTests/ReadingStatusSelectorTests.swift`, add after `thePreviewShowsTheSelectorOnlyWithAStatus`:
```swift
    @Test func thePreviewShowsOpenDetailsOnlyWhenGiven() {
        let with = renderedStrings(of: PaperPreviewContent(
            paper: SamplePapers.attention, inLibrary: true, onToggleSave: {}, onOpenDOI: nil, onOpenDetails: {}
        ))
        let without = renderedStrings(of: PaperPreviewContent(
            paper: SamplePapers.attention, inLibrary: true, onToggleSave: {}, onOpenDOI: nil
        ))

        #expect(with.contains("Open details"))
        #expect(with.contains("Remove from library"))
        #expect(!without.contains("Open details"))
    }

    @Test func sharedStringsResolveInBothLanguages() {
        #expect(inLanguage("en") { [DesignSystemStrings.abstract, DesignSystemStrings.noAbstract, DesignSystemStrings.openDOI, DesignSystemStrings.removeFromLibrary] }
            == ["Abstract", "No abstract available", "Open DOI", "Remove from library"])
        #expect(inLanguage("en") { DesignSystemStrings.openAccess(hasPDF: true) } == "Open access · PDF available")
        #expect(inLanguage("ar") { DesignSystemStrings.openAccess(hasPDF: true) } == "وصول مفتوح · ملف PDF متاح")
        #expect(inLanguage("ar") { DesignSystemStrings.removeFromLibrary } == "إزالة من المكتبة")
    }
```

(The values are the existing catalog's: `designsystem.noAbstract` is "No abstract available".)

In `ios/HashiyaSnapshotTests/DesignSystemSnapshotTests.swift`, add after `previewWithTheStatusSelector`:
```swift
    /// A saved paper's sheet in Search: Open details above Open DOI and Remove.
    @Test func previewWithOpenDetails() {
        let preview = PaperPreviewContent(
            paper: SamplePapers.attention,
            inLibrary: true,
            onToggleSave: {},
            onOpenDOI: { _ in },
            onOpenDetails: {}
        )
        assertHashiyaSnapshots(of: preview, named: "previewOpenDetails", arabicText: "فتح التفاصيل")
    }
```

- [ ] **Step 2: Run them and see them fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaDesignSystemTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL: `extra argument 'onOpenDetails' in call`, `cannot find 'DesignSystemStrings' in scope`, then `** TEST FAILED **`.

- [ ] **Step 3: Implement**

Add the string:
```bash
python3 - <<'EOF'
import json, pathlib
path = pathlib.Path("ios/HashiyaKit/Sources/HashiyaDesignSystem/Resources/Localizable.xcstrings")
catalog = json.loads(path.read_text(encoding="utf-8"))
for key, en, ar in [
    ("designsystem.openDetails", "Open details", "فتح التفاصيل"),
]:
    catalog["strings"][key] = {
        "extractionState": "manual",
        "localizations": {
            "ar": {"stringUnit": {"state": "translated", "value": ar}},
            "en": {"stringUnit": {"state": "translated", "value": en}},
        },
    }
path.write_text(json.dumps(catalog, indent=2, separators=(",", " : "), ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
EOF
```

`ios/HashiyaKit/Sources/HashiyaDesignSystem/DesignSystemStrings.swift`:
```swift
/// Design-system strings that other targets show beside its components (the Details screen), so no string is
/// duplicated in another catalog.
@MainActor
public enum DesignSystemStrings {
    public static var abstract: String { L10n.string("designsystem.abstract") }
    public static var noAbstract: String { L10n.string("designsystem.noAbstract") }
    public static var openDOI: String { L10n.string("designsystem.openDOI") }
    public static var removeFromLibrary: String { L10n.string("designsystem.removeFromLibrary") }

    /// The open-access badge: "Open access", or "Open access · PDF available" when there is a PDF.
    public static func openAccess(hasPDF: Bool) -> String {
        L10n.string(hasPDF ? "designsystem.openAccessPDF" : "designsystem.openAccess")
    }
}
```

`ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/PaperPreviewContent.swift`:

1. Replace the doc comment on the struct with `/// The preview sheet's body: the full paper, the reading status (Library only), then Open details (saved papers in Search), Open DOI and Save/Remove.`
2. Add `private let onOpenDetails: (() -> Void)?` after `onOpenDOI`.
3. Replace the initializer with:
```swift
    /// - Parameters:
    ///   - status: the saved paper's status, shown as a segmented selector above the buttons; nil shows none.
    ///   - onOpenDOI: nil hides Open DOI.
    ///   - onOpenDetails: nil hides Open details.
    public init(
        paper: Paper,
        inLibrary: Bool,
        status: ReadingStatus? = nil,
        onStatusChange: @escaping (ReadingStatus) -> Void = { _ in },
        onToggleSave: @escaping () -> Void,
        onOpenDOI: ((String) -> Void)?,
        onOpenDetails: (() -> Void)? = nil
    ) {
        self.paper = paper
        self.inLibrary = inLibrary
        self.status = status
        self.onStatusChange = onStatusChange
        self.onToggleSave = onToggleSave
        self.onOpenDOI = onOpenDOI
        self.onOpenDetails = onOpenDetails
    }
```
4. In `actions`, replace the `HashiyaGlassGroup(spacing: 12) { HStack(spacing: 12) { … } }` block (keep the `.controlSize(.large)` and paddings after it) with:
```swift
            HashiyaGlassGroup(spacing: 12) {
                VStack(spacing: 12) {
                    if let onOpenDetails {
                        Button(action: onOpenDetails) {
                            Label {
                                Text(verbatim: L10n.string("designsystem.openDetails"))
                            } icon: {
                                Image(systemName: "doc.text")
                            }
                            .font(.hashiya(.label))
                            .frame(maxWidth: .infinity)
                        }
                        .hashiyaSecondaryButton()
                    }
                    HStack(spacing: 12) {
                        if let doi = paper.doi, let onOpenDOI {
                            Button {
                                onOpenDOI(doi)
                            } label: {
                                Label {
                                    Text(verbatim: L10n.string("designsystem.openDOI"))
                                } icon: {
                                    Image(systemName: "arrow.up.forward.square")
                                }
                                .font(.hashiya(.label))
                                .frame(maxWidth: .infinity)
                            }
                            .hashiyaSecondaryButton()
                        }
                        Button(action: onToggleSave) {
                            Text(verbatim: L10n.string(inLibrary ? "designsystem.removeFromLibrary" : "designsystem.saveToLibrary"))
                                .font(.hashiya(.label))
                                .foregroundStyle(HashiyaColors.onPrimary)
                                .frame(maxWidth: .infinity)
                        }
                        .hashiyaProminentButton()
                    }
                }
            }
```
A one-child `VStack` has the same size as its child, so every existing preview baseline must still pass unchanged. CI proves it: the old `DesignSystemSnapshotTests`, `SearchSnapshotTests` and `ShareSnapshotTests` images must match.

- [ ] **Step 4: Run them and see them pass**

Run: the Step 2 command.
Expected: PASS. Then run `HashiyaSnapshotTests` (Mac: `xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaSnapshotTests`). Expected: only `previewWithOpenDetails` fails, with `No reference was found on disk`; every existing image matches.

- [ ] **Step 5: Commit, push and read CI**

```bash
git add ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/PaperPreviewContent.swift \
  ios/HashiyaKit/Sources/HashiyaDesignSystem/DesignSystemStrings.swift \
  ios/HashiyaKit/Sources/HashiyaDesignSystem/Resources/Localizable.xcstrings \
  ios/HashiyaKit/Tests/HashiyaDesignSystemTests/ReadingStatusSelectorTests.swift ios/HashiyaSnapshotTests/DesignSystemSnapshotTests.swift
git commit -m "feat: add Open details to the iOS preview sheet"
git log --format='%an <%ae>' origin/main..HEAD
git push
```
Read the "iOS" run for this SHA. Expected: the only failures are the four `previewWithOpenDetails` images per OS with `No reference was found on disk`. Any other failure is real and must be fixed first.

---
### Task 5: `FeaturePaperDetails` — the target, its strings, and the view model with autosave

**Files:**
- Modify: `ios/HashiyaKit/Package.swift`, `ios/project.yml`
- Create: `ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsViewModel.swift`, `L10n.swift`, `Resources/Localizable.xcstrings`
- Test: Create `ios/HashiyaKit/Tests/FeaturePaperDetailsTests/PaperDetailsViewModelTests.swift`, `PaperDetailsStringsTests.swift`

**Interfaces:**
- Consumes: Task 3's `LibraryRepository` members, `PendingWrites`, `FakeLibraryRepository`; `ManualSleeper`, `eventually`.
- Produces (module `FeaturePaperDetails`, `public`):
  - `struct PaperDetailsRoute: Hashable, Codable, Sendable { var openAlexID: String }`;
  - `enum NotesLoad { loading, loaded, failed }`, `enum NotesSaveState { idle, saving, saved, failed }`;
  - `enum PaperDetailsMessage { notesSaveFailed, statusUpdateFailed }`, `enum PaperDetailsExit { closed, removed }`;
  - `@Observable @MainActor final class PaperDetailsViewModel`:
    - `static let autosaveDelay` (500 ms);
    - `init(openAlexID:library:pendingWrites:sleep:)`;
    - properties `openAlexID`, `paper`, `notesLoad`, `notes`, `saveState`, `message`, `exit`, `isLoaded`;
    - methods `start() async`, `updateNote(_:_:)`, `flush()`, `retryLoadNotes() async`, `setStatus(_:) async`, `remove() async`.
- Internal: `L10n` with `string`, `format`, `noteLabel(_:)`, `noteHint(_:)`, `saveStatus(_:) -> String?`; `NoteSection.key`.

- [ ] **Step 1: Add the target, then write the failing tests**

`ios/HashiyaKit/Package.swift`:

1. In `products`, after the `FeatureLibrary` line, add:
```swift
        .library(name: "FeaturePaperDetails", targets: ["FeaturePaperDetails"]),
```
2. In `targets`, after the `FeatureLibrary` target, add:
```swift
        .target(
            name: "FeaturePaperDetails",
            dependencies: ["HashiyaData", "HashiyaModel", "HashiyaDesignSystem"],
            resources: [.process("Resources")]
        ),
```
3. After the `FeatureLibraryTests` test target, add:
```swift
        .testTarget(
            name: "FeaturePaperDetailsTests",
            dependencies: ["FeaturePaperDetails", "HashiyaData", "HashiyaDesignSystem", "HashiyaModel", "HashiyaTesting"]
        ),
```

`ios/project.yml`:

1. In the `Hashiya` target's `HashiyaKit` products, add `- FeaturePaperDetails` after `- FeatureLibrary`.
2. In `HashiyaSnapshotTests`' `HashiyaKit` products, add `- FeaturePaperDetails` after `- FeatureLibrary`.
3. In the scheme's `test.targets`, add `- package: HashiyaKit/FeaturePaperDetailsTests` after `- package: HashiyaKit/FeatureLibraryTests`.

Create the catalog with every string in spec §9. Some are used only from Task 6, but adding them together keeps `check-translations.py` and the strings test in one place:
```bash
mkdir -p ios/HashiyaKit/Sources/FeaturePaperDetails/Resources
python3 - <<'EOF'
import json, pathlib
path = pathlib.Path("ios/HashiyaKit/Sources/FeaturePaperDetails/Resources/Localizable.xcstrings")
catalog = {"sourceLanguage": "en", "strings": {}, "version": "1.0"}
for key, en, ar in [
    ("details.moreOptions", "More options", "خيارات أخرى"),
    ("details.openPDF", "Open PDF", "فتح ملف PDF"),
    ("details.notesTitle", "My notes", "ملاحظاتي"),
    ("details.notesSaving", "Saving…", "جارٍ الحفظ…"),
    ("details.notesSaved", "Saved", "تم الحفظ"),
    ("details.notesSaveFailed", "Couldn't save", "تعذّر الحفظ"),
    ("details.notesSaveFailedMessage", "Couldn't save your notes", "تعذّر حفظ ملاحظاتك"),
    ("details.notesLoadFailed", "Couldn't load your notes", "تعذّر تحميل ملاحظاتك"),
    ("details.retry", "Retry", "إعادة المحاولة"),
    ("details.statusUpdateFailed", "Couldn't update the status", "تعذّر تحديث الحالة"),
    ("details.doneEditing", "Done", "تم"),
    ("note.summary", "Summary", "الخلاصة"),
    ("note.summaryHint", "What is this paper about, in your own words?", "عمّ تتحدث هذه الورقة، بكلماتك أنت؟"),
    ("note.researchQuestion", "Research question", "سؤال البحث"),
    ("note.researchQuestionHint", "What question or problem does it address?", "ما السؤال أو المشكلة التي تعالجها؟"),
    ("note.method", "Method", "المنهجية"),
    ("note.methodHint", "How did the authors approach it?", "كيف تناولها المؤلفون؟"),
    ("note.keyFindings", "Key findings", "أهم النتائج"),
    ("note.keyFindingsHint", "What did they find?", "ما الذي توصّلوا إليه؟"),
    ("note.limitations", "Limitations", "القيود"),
    ("note.limitationsHint", "What are its weaknesses or open questions?", "ما نقاط ضعفها أو الأسئلة التي تتركها مفتوحة؟"),
    ("note.thoughts", "My thoughts", "أفكاري"),
    ("note.thoughtsHint", "How does it relate to your work?", "ما علاقتها ببحثك؟"),
]:
    catalog["strings"][key] = {
        "extractionState": "manual",
        "localizations": {
            "ar": {"stringUnit": {"state": "translated", "value": ar}},
            "en": {"stringUnit": {"state": "translated", "value": en}},
        },
    }
path.write_text(json.dumps(catalog, indent=2, separators=(",", " : "), ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
EOF
python3 ios/scripts/check-translations.py
```
Expected: `check-translations.py` prints nothing and exits 0.

`ios/HashiyaKit/Tests/FeaturePaperDetailsTests/PaperDetailsStringsTests.swift`:
```swift
@testable import FeaturePaperDetails
import HashiyaDesignSystem
import HashiyaModel
import Testing

@MainActor
struct PaperDetailsStringsTests {
    private func inLanguage<T>(_ language: String, _ body: () -> T) -> T {
        let previous = HashiyaLanguage.override
        HashiyaLanguage.override = language
        defer { HashiyaLanguage.override = previous }
        return body()
    }

    @Test func sectionsHaveTheirLabelsInOrder() {
        #expect(inLanguage("en") { NoteSection.allCases.map(L10n.noteLabel) }
            == ["Summary", "Research question", "Method", "Key findings", "Limitations", "My thoughts"])
        #expect(inLanguage("ar") { NoteSection.allCases.map(L10n.noteLabel) }
            == ["الخلاصة", "سؤال البحث", "المنهجية", "أهم النتائج", "القيود", "أفكاري"])
    }

    @Test func everySectionHasAHint() {
        #expect(inLanguage("en") { L10n.noteHint(.summary) } == "What is this paper about, in your own words?")
        #expect(inLanguage("ar") { L10n.noteHint(.thoughts) } == "ما علاقتها ببحثك؟")
        for section in NoteSection.allCases {
            #expect(inLanguage("en") { L10n.noteHint(section) } != "note.\(section.key)Hint")
        }
    }

    @Test func theSaveStatusLineFollowsTheState() {
        #expect(inLanguage("en") { L10n.saveStatus(.idle) } == nil)
        #expect(inLanguage("en") { L10n.saveStatus(.saving) } == "Saving…")
        #expect(inLanguage("en") { L10n.saveStatus(.saved) } == "Saved")
        #expect(inLanguage("en") { L10n.saveStatus(.failed) } == "Couldn't save")
        #expect(inLanguage("ar") { L10n.saveStatus(.saved) } == "تم الحفظ")
    }
}
```

`ios/HashiyaKit/Tests/FeaturePaperDetailsTests/PaperDetailsViewModelTests.swift`:
```swift
@testable import FeaturePaperDetails
import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct PaperDetailsViewModelTests {
    private let sleeper = ManualSleeper()
    private let pendingWrites = PendingWrites()
    private let id = SamplePapers.attention.openAlexID

    private func makeViewModel(_ library: FakeLibraryRepository, id: String? = nil) -> PaperDetailsViewModel {
        PaperDetailsViewModel(openAlexID: id ?? self.id, library: library, pendingWrites: pendingWrites, sleep: sleeper.sleep)
    }

    /// A view model for `library` that has started and loaded; the returned task is its `start()`.
    private func started(_ library: FakeLibraryRepository) async -> (PaperDetailsViewModel, Task<Void, Never>) {
        let viewModel = makeViewModel(library)
        let task = Task { await viewModel.start() }
        _ = await eventually { viewModel.isLoaded }
        return (viewModel, task)
    }

    /// Lets the 500 ms pause elapse.
    private func pauseEnds() async {
        await sleeper.waitForSleeper()
        sleeper.advance(by: .milliseconds(500))
    }

    // MARK: Loading

    @Test func loadingWaitsForThePaperAndTheNotes() async {
        let library = FakeLibraryRepository(
            saved: [SamplePapers.attention], statuses: [id: .reading], notes: [id: PaperNotes(summary: "Stored")]
        )
        let viewModel = makeViewModel(library)
        #expect(!viewModel.isLoaded)
        #expect(viewModel.notesLoad == .loading)

        let task = Task { await viewModel.start() }
        defer { task.cancel() }

        #expect(await eventually { viewModel.isLoaded })
        #expect(viewModel.paper == LibraryPaper(paper: SamplePapers.attention, status: .reading))
        #expect(viewModel.notesLoad == .loaded)
        #expect(viewModel.notes == PaperNotes(summary: "Stored"))
        #expect(viewModel.saveState == .idle)
    }

    @Test func aLaterDatabaseChangeDoesNotReplaceTypedNotes() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention], notes: [id: PaperNotes(summary: "Stored")])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        viewModel.updateNote(.summary, "Typing")

        try await library.saveNotes(openAlexID: id, notes: PaperNotes(summary: "From elsewhere"))
        try await Task.sleep(for: .milliseconds(50))

        #expect(viewModel.notes == PaperNotes(summary: "Typing"))
    }

    @Test func aFailedNotesReadNeverWritesAndRetryLoadsThem() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention], notes: [id: PaperNotes(method: "Stored")])
        library.setFailNotesRead(true)
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        #expect(viewModel.notesLoad == .failed)
        viewModel.updateNote(.method, "Would overwrite")
        viewModel.flush()
        #expect(sleeper.pendingCount == 0)
        await pendingWrites.drained()
        #expect(library.notesWriteAttempts.isEmpty)
        #expect(viewModel.notes == PaperNotes())

        library.setFailNotesRead(false)
        await viewModel.retryLoadNotes()
        #expect(viewModel.notesLoad == .loaded)
        #expect(viewModel.notes == PaperNotes(method: "Stored"))
    }

    @Test func aPaperThatIsNotSavedCloses() async {
        let viewModel = makeViewModel(FakeLibraryRepository(), id: "W404")
        let task = Task { await viewModel.start() }
        defer { task.cancel() }

        #expect(await eventually { viewModel.exit == .closed })
    }

    @Test func aPaperRemovedElsewhereCloses() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        _ = try await library.remove(openAlexID: id)

        #expect(await eventually { viewModel.exit == .closed })
    }

    @Test func startingTwiceStartsOnce() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        await viewModel.start()

        #expect(viewModel.isLoaded)
    }

    // MARK: Autosave

    @Test func typingWritesOnceAfter500Milliseconds() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        viewModel.updateNote(.summary, "A")
        viewModel.updateNote(.summary, "Ab")
        viewModel.updateNote(.method, "Ablation")
        await sleeper.waitForSleeper()
        sleeper.advance(by: .milliseconds(499))
        try await Task.sleep(for: .milliseconds(30))
        #expect(library.notesWriteAttempts.isEmpty)

        sleeper.advance(by: .milliseconds(1))

        #expect(await eventually { viewModel.saveState == .saved })
        #expect(library.notesWriteAttempts == [PaperNotes(summary: "Ab", method: "Ablation")])
        #expect(library.notes(of: id) == PaperNotes(summary: "Ab", method: "Ablation"))
    }

    @Test func theSaveStateGoesIdleSavingSaved() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        library.holdNotesSaves()
        #expect(viewModel.saveState == .idle)

        viewModel.updateNote(.thoughts, "Idea")
        await pauseEnds()

        #expect(await eventually { viewModel.saveState == .saving })
        library.releaseNotesSaves()
        #expect(await eventually { viewModel.saveState == .saved })
    }

    @Test func aFailedWriteShowsFailedAndRetryWritesAgain() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        library.setFailSaveNotes(true)

        viewModel.updateNote(.keyFindings, "Result")
        await pauseEnds()

        #expect(await eventually { viewModel.saveState == .failed })
        #expect(viewModel.message == .notesSaveFailed)
        #expect(viewModel.notes == PaperNotes(keyFindings: "Result"))

        library.setFailSaveNotes(false)
        viewModel.flush()

        #expect(await eventually { viewModel.saveState == .saved })
        #expect(library.notesWriteAttempts == [PaperNotes(keyFindings: "Result"), PaperNotes(keyFindings: "Result")])
        #expect(library.notes(of: id) == PaperNotes(keyFindings: "Result"))
    }

    @Test func theNextEditTriesAgainAfterAFailure() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        library.setFailSaveNotes(true)
        viewModel.updateNote(.summary, "One")
        await pauseEnds()
        #expect(await eventually { viewModel.saveState == .failed })

        library.setFailSaveNotes(false)
        viewModel.updateNote(.summary, "One more")
        await pauseEnds()

        #expect(await eventually { viewModel.saveState == .saved })
        #expect(library.notes(of: id) == PaperNotes(summary: "One more"))
    }

    @Test func flushWritesPendingNotesAtOnce() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        viewModel.updateNote(.limitations, "Small sample")

        viewModel.flush()
        await pendingWrites.drained()

        #expect(library.notes(of: id) == PaperNotes(limitations: "Small sample"))
        #expect(sleeper.pendingCount == 0)
        #expect(library.notesWriteAttempts.count == 1)
    }

    @Test func flushWritesNothingWhenNothingIsNew() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention], notes: [id: PaperNotes(summary: "Stored")])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        viewModel.flush()
        viewModel.updateNote(.summary, "Changed")
        viewModel.updateNote(.summary, "Stored")
        viewModel.flush()
        await pendingWrites.drained()

        #expect(library.notesWriteAttempts.isEmpty)
    }

    @Test func writesRunInOrder() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        library.holdNotesSaves()

        viewModel.updateNote(.summary, "First")
        viewModel.flush()
        viewModel.updateNote(.summary, "Second")
        viewModel.flush()
        #expect(await eventually { library.heldNotesSaves == 1 })
        library.releaseNotesSaves()
        await pendingWrites.drained()

        #expect(library.notesWriteAttempts == [PaperNotes(summary: "First"), PaperNotes(summary: "Second")])
        #expect(library.notes(of: id) == PaperNotes(summary: "Second"))
    }

    @Test func aWriteOutlivesTheViewModel() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        var viewModel: PaperDetailsViewModel? = makeViewModel(library)
        let task = Task { [viewModel] in await viewModel?.start() }
        _ = await eventually { viewModel?.isLoaded == true }
        library.holdNotesSaves()

        viewModel?.updateNote(.thoughts, "Keep this")
        viewModel?.flush()
        task.cancel()
        _ = await task.value
        viewModel = nil
        library.releaseNotesSaves()
        await pendingWrites.drained()

        #expect(library.notes(of: id) == PaperNotes(thoughts: "Keep this"))
    }

    // MARK: Status and remove

    @Test func aStatusChangeIsStoredAndShown() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        await viewModel.setStatus(.read)

        #expect(await eventually { viewModel.paper?.status == .read })
    }

    @Test func aFailedStatusChangeShowsTheMessageAndKeepsTheStoredStatus() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        library.setFailStatusUpdates(true)
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        await viewModel.setStatus(.read)

        #expect(viewModel.message == .statusUpdateFailed)
        #expect(viewModel.paper?.status == .toRead)
    }

    @Test func removeSavesPendingNotesFirst() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        viewModel.updateNote(.thoughts, "Keep this")

        await viewModel.remove()

        #expect(viewModel.exit == .removed)
        #expect(library.notes(of: id) == PaperNotes(thoughts: "Keep this"))
        #expect(sleeper.pendingCount == 0)
    }

    @Test func removeWaitsWhenTheSaveFailsSoUndoCantRestoreStaleNotes() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        library.setFailSaveNotes(true)
        viewModel.updateNote(.thoughts, "Keep this")

        await viewModel.remove()

        #expect(viewModel.exit == nil)
        #expect(viewModel.saveState == .failed)
        #expect(viewModel.message == .notesSaveFailed)

        library.setFailSaveNotes(false)
        await viewModel.remove()

        #expect(viewModel.exit == .removed)
        #expect(library.notes(of: id) == PaperNotes(thoughts: "Keep this"))
    }

    @Test func removeWithoutLoadedNotesExitsWithoutWriting() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        library.setFailNotesRead(true)
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        await viewModel.remove()

        #expect(viewModel.exit == .removed)
        #expect(library.notesWriteAttempts.isEmpty)
    }
}
```

- [ ] **Step 2: Run them and see them fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:FeaturePaperDetailsTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL: the target has no sources yet (`error: target 'FeaturePaperDetails' … has no source files` or `cannot find 'PaperDetailsViewModel' in scope`), then `** TEST FAILED **`.

- [ ] **Step 3: Implement**

`ios/HashiyaKit/Sources/FeaturePaperDetails/L10n.swift`:
```swift
import Foundation
import HashiyaDesignSystem
import HashiyaModel

/// This target's strings.
@MainActor
enum L10n {
    static func string(_ key: String) -> String {
        HashiyaStrings.string(key, bundle: .module)
    }

    static func format(_ key: String, _ arguments: any CVarArg...) -> String {
        HashiyaStrings.format(key, bundle: .module, arguments)
    }

    /// The section's name: "Summary", "Research question", ….
    static func noteLabel(_ section: NoteSection) -> String {
        string("note.\(section.key)")
    }

    /// The section's prompt: "What is this paper about, in your own words?", ….
    static func noteHint(_ section: NoteSection) -> String {
        string("note.\(section.key)Hint")
    }

    /// The line beside "My notes": nothing, "Saving…", "Saved" or "Couldn't save".
    static func saveStatus(_ state: NotesSaveState) -> String? {
        switch state {
        case .idle: nil
        case .saving: string("details.notesSaving")
        case .saved: string("details.notesSaved")
        case .failed: string("details.notesSaveFailed")
        }
    }
}

extension NoteSection {
    /// The stem of the section's catalog keys (`note.<key>`, `note.<key>Hint`) and of its field's accessibility
    /// identifier.
    var key: String {
        switch self {
        case .summary: "summary"
        case .researchQuestion: "researchQuestion"
        case .method: "method"
        case .keyFindings: "keyFindings"
        case .limitations: "limitations"
        case .thoughts: "thoughts"
        }
    }
}
```

`ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsViewModel.swift`:
```swift
import Foundation
import HashiyaData
import HashiyaModel
import Observation

/// The Details screen of a saved paper, pushed on the Library's or Search's navigation stack.
public struct PaperDetailsRoute: Hashable, Codable, Sendable {
    public var openAlexID: String

    public init(openAlexID: String) {
        self.openAlexID = openAlexID
    }
}

/// Whether the stored notes have been read.
public enum NotesLoad: Equatable, Sendable {
    case loading, loaded, failed
}

/// The line beside "My notes".
public enum NotesSaveState: Equatable, Sendable {
    /// No write yet on this screen.
    case idle
    case saving, saved, failed
}

public enum PaperDetailsMessage: Equatable, Sendable {
    case notesSaveFailed, statusUpdateFailed
}

/// Why the screen should go away: the paper stopped being saved, or the user removed it (after its notes saved).
public enum PaperDetailsExit: Equatable, Sendable {
    case closed, removed
}

/// A saved paper and its notes. The notes are read once and then only written: no database change ever replaces
/// what is being typed. Edits save 500 ms after typing stops and on `flush()`; writes run one after another and are
/// never cancelled, and `PendingWrites` tracks each until it ends.
@Observable
@MainActor
public final class PaperDetailsViewModel {
    public static let autosaveDelay: Duration = .milliseconds(500)

    public let openAlexID: String
    /// Nil until the first value, and after the paper stops being saved.
    public private(set) var paper: LibraryPaper?
    public private(set) var notesLoad: NotesLoad = .loading
    /// The notes as typed. Fields read this once, to seed themselves.
    public private(set) var notes = PaperNotes()
    public private(set) var saveState: NotesSaveState = .idle
    public var message: PaperDetailsMessage?
    public private(set) var exit: PaperDetailsExit?

    @ObservationIgnored private let library: any LibraryRepository
    @ObservationIgnored private let pendingWrites: PendingWrites
    @ObservationIgnored private let sleep: @Sendable (Duration) async throws -> Void
    /// What the database holds, as far as this screen knows: the notes read, then each successful write.
    @ObservationIgnored private var savedNotes = PaperNotes()
    @ObservationIgnored private var lastWrite: Task<Bool, Never>?
    @ObservationIgnored private var debounceTask: Task<Void, Never>?
    @ObservationIgnored private var hasStarted = false

    /// Stores its dependencies only; `start()` does the work. SwiftUI may build and discard several instances.
    public init(
        openAlexID: String,
        library: any LibraryRepository,
        pendingWrites: PendingWrites,
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) }
    ) {
        self.openAlexID = openAlexID
        self.library = library
        self.pendingWrites = pendingWrites
        self.sleep = sleep
    }

    /// The paper is in and the notes were read (or failed to be).
    public var isLoaded: Bool { paper != nil && notesLoad != .loading }

    /// Reads the notes once and follows the paper until the calling task is cancelled. Later calls do nothing.
    public func start() async {
        guard !hasStarted else { return }
        hasStarted = true
        async let notesRead: Void = loadNotes()
        for await paper in library.observePaper(openAlexID: openAlexID) {
            guard let paper else {
                if exit == nil { exit = .closed }
                break
            }
            self.paper = paper
        }
        await notesRead
    }

    /// "Couldn't load your notes" → Retry. The failure stays on screen until a read succeeds.
    public func retryLoadNotes() async {
        guard notesLoad == .failed else { return }
        await loadNotes()
    }

    private func loadNotes() async {
        do {
            let stored = try await library.notes(openAlexID: openAlexID)
            notes = stored
            savedNotes = stored
            notesLoad = .loaded
        } catch {
            notesLoad = .failed
        }
    }

    // MARK: Notes

    /// A field changed: keep it, and write 500 ms after typing stops.
    public func updateNote(_ section: NoteSection, _ text: String) {
        guard notesLoad == .loaded, notes[section] != text else { return }
        notes[section] = text
        debounceTask?.cancel()
        debounceTask = Task { [weak self, sleep] in
            do {
                try await sleep(Self.autosaveDelay)
            } catch {
                return
            }
            guard !Task.isCancelled, let self else { return }
            self.write(self.notes)
        }
    }

    /// Writes unsaved notes now: leaving the screen, the app going inactive or to the background, and Retry.
    public func flush() {
        debounceTask?.cancel()
        debounceTask = nil
        guard notesLoad == .loaded, notes != savedNotes else { return }
        write(notes)
    }

    /// Chains a write after the previous one, so writes run in order, and tracks it until it ends. The task holds
    /// this view model until then; nothing cancels it.
    @discardableResult
    private func write(_ value: PaperNotes) -> Task<Bool, Never> {
        let previous = lastWrite
        let task = Task {
            _ = await previous?.value
            return await self.save(value)
        }
        lastWrite = task
        pendingWrites.track(task)
        return task
    }

    /// Returns false only when the write failed.
    private func save(_ value: PaperNotes) async -> Bool {
        guard value != savedNotes else { return true }
        saveState = .saving
        do {
            try await library.saveNotes(openAlexID: openAlexID, notes: value)
            savedNotes = value
            saveState = .saved
            return true
        } catch {
            saveState = .failed
            message = .notesSaveFailed
            return false
        }
    }

    // MARK: Status and remove

    /// The selector. A failure shows a message; the selector keeps showing the stored status.
    public func setStatus(_ status: ReadingStatus) async {
        do {
            try await library.setStatus(openAlexID: openAlexID, status: status)
        } catch {
            message = .statusUpdateFailed
        }
    }

    /// Saves unsaved notes first, so Undo on the screen below restores what was just typed, then asks to leave.
    /// If that save fails, the screen stays with Couldn't save and Retry, so Undo can never bring back older notes.
    public func remove() async {
        debounceTask?.cancel()
        debounceTask = nil
        var saved = true
        if notesLoad == .loaded {
            saved = await write(notes).value
        }
        if saved { exit = .removed }
    }
}
```

- [ ] **Step 4: Run them and see them pass**

Run: the Step 2 command.
Expected: PASS: `✔ Test run with … tests passed`, `** TEST SUCCEEDED **`.

- [ ] **Step 5: Commit, push and read CI**

```bash
git add ios/HashiyaKit/Package.swift ios/project.yml ios/HashiyaKit/Sources/FeaturePaperDetails \
  ios/HashiyaKit/Tests/FeaturePaperDetailsTests
git commit -m "feat: add the iOS paper details view model with note autosave"
git log --format='%an <%ae>' origin/main..HEAD
git push
```
Read the "iOS" run for this SHA. Expected: the only failures are Task 4's `previewWithOpenDetails` images (`No reference was found on disk`). `FeaturePaperDetailsTests` must appear in the log and pass.

---
### Task 6: Details screen — content, note fields, the screen, and its snapshots

**Files:**
- Create: `ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsContent.swift`, `NoteField.swift`, `PaperDetailsScreen.swift`
- Test: Create `ios/HashiyaKit/Tests/FeaturePaperDetailsTests/PaperDetailsContentTests.swift`, `ios/HashiyaSnapshotTests/PaperDetailsSnapshotTests.swift`

**Interfaces:**
- Consumes: Task 5's view model, route and strings; Task 4's `DesignSystemStrings`; `PaperText`, `PaperFormat`, `DOILink`, `StatusBadge`, `ReadingStatusSelector`, `HashiyaBanner`, `HashiyaGlassGroup`, `hashiyaSecondaryButton()`, `LoadingSkeleton`, `ContentDirection`, `HashiyaColors`, `.hashiya(_:)` fonts.
- Produces (module `FeaturePaperDetails`, `public`):
  - `struct PaperDetailsActions`: the callbacks `updateNote`, `setStatus`, `openURL`, `remove`, `retrySave` and `retryLoadNotes`, each defaulting to a no-op, plus `init()`;
  - `struct PaperDetailsContent: View`, `init(paper:notes:saveState:message:actions:)`, where `notes: PaperNotes?` is nil when the notes couldn't be read;
  - `struct PaperDetailsScreen: View`, `init(viewModel:onClose:onRemove:)`, where `viewModel` is an `@autoclosure` and `onRemove` receives the OpenAlex ID.

**Layout (spec §7.2):**
- Content: one `ScrollView` with a `VStack`, 16 pt side padding, from top to bottom:
  - header (12 pt spacing);
  - `ReadingStatusSelector`, 20 pt above;
  - links, 16 pt above;
  - abstract, 24 pt above;
  - "My notes", 24 pt above, with the fields 16 pt apart.
- The banners overlay the bottom, glass on iOS 26 through `HashiyaBanner`.
- Navigation: inline empty title, a trailing **More options** `Menu` with the destructive **Remove from library**, and a keyboard toolbar **Done**.
- Tab bar hidden.

- [ ] **Step 1: Write the failing tests**

`ios/HashiyaKit/Tests/FeaturePaperDetailsTests/PaperDetailsContentTests.swift`:
```swift
@testable import FeaturePaperDetails
import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import SwiftUI
import Testing
import UIKit

/// Hostless package tests have no accessibility tree, so these lay the content out in a window and check which
/// strings it looked up (as `ReadingStatusSelectorTests` does). Typing is covered by `PaperDetailsFlowTests`.
@MainActor
@Suite(.serialized)
struct PaperDetailsContentTests {
    private func renderedStrings(of view: some View) -> [String] {
        let previous = (HashiyaLanguage.override, HashiyaStrings.recordedLookups)
        HashiyaLanguage.override = "en"
        HashiyaStrings.recordedLookups = []
        defer {
            HashiyaLanguage.override = previous.0
            HashiyaStrings.recordedLookups = previous.1
        }
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 4_000))
        window.rootViewController = UIHostingController(rootView: NavigationStack { view })
        window.makeKeyAndVisible()
        window.layoutIfNeeded()
        let rendered = HashiyaStrings.recordedLookups ?? []
        window.isHidden = true
        return rendered
    }

    private func content(
        _ paper: Paper,
        notes: PaperNotes? = PaperNotes(),
        saveState: NotesSaveState = .idle,
        message: PaperDetailsMessage? = nil
    ) -> PaperDetailsContent {
        PaperDetailsContent(
            paper: LibraryPaper(paper: paper, status: .toRead),
            notes: notes,
            saveState: saveState,
            message: message,
            actions: PaperDetailsActions()
        )
    }

    @Test func theSixSectionsAppearInOrder() {
        let rendered = renderedStrings(of: content(SamplePapers.vit))
        let labels = ["Summary", "Research question", "Method", "Key findings", "Limitations", "My thoughts"]
        let positions = labels.compactMap { rendered.firstIndex(of: $0) }
        #expect(positions.count == labels.count)
        #expect(positions == positions.sorted())
        #expect(rendered.contains("My notes"))
        #expect(rendered.contains("What is this paper about, in your own words?"))
    }

    @Test func openPDFShowsOnlyWithAPDF() {
        let attention = renderedStrings(of: content(SamplePapers.attention))
        let bert = renderedStrings(of: content(SamplePapers.bert))
        #expect(attention.contains("Open PDF"))
        #expect(attention.contains("Open DOI"))
        #expect(!bert.contains("Open PDF"))
        #expect(bert.contains("Open DOI"))
    }

    @Test func untitledAndNoAbstractRender() {
        let rendered = renderedStrings(of: content(SamplePapers.untitled))
        #expect(rendered.contains("Untitled"))
        #expect(rendered.contains("No abstract available"))
        #expect(!rendered.contains("Open DOI"))
    }

    @Test func theSaveStatusLineShowsEachState() {
        #expect(!renderedStrings(of: content(SamplePapers.vit, saveState: .idle)).contains("Saved"))
        #expect(renderedStrings(of: content(SamplePapers.vit, saveState: .saving)).contains("Saving…"))
        #expect(renderedStrings(of: content(SamplePapers.vit, saveState: .saved)).contains("Saved"))
        #expect(renderedStrings(of: content(SamplePapers.vit, saveState: .failed)).contains("Couldn't save"))
    }

    @Test func aSaveFailureShowsTheBannerWithRetry() {
        let rendered = renderedStrings(of: content(SamplePapers.vit, saveState: .failed, message: .notesSaveFailed))
        #expect(rendered.contains("Couldn't save your notes"))
        #expect(rendered.contains("Retry"))
    }

    @Test func notesThatCouldNotBeReadShowRetryAndNoFields() {
        let rendered = renderedStrings(of: content(SamplePapers.vit, notes: nil))
        #expect(rendered.contains("Couldn't load your notes"))
        #expect(rendered.contains("Retry"))
        #expect(!rendered.contains("Summary"))
    }
}
```

`ios/HashiyaSnapshotTests/PaperDetailsSnapshotTests.swift`:
```swift
@testable import FeaturePaperDetails
import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import SwiftUI
import Testing

@MainActor
@Suite(.serialized)
struct PaperDetailsSnapshotTests {
    private func screen(
        _ paper: Paper,
        status: ReadingStatus = .toRead,
        notes: PaperNotes? = PaperNotes(),
        saveState: NotesSaveState = .idle,
        message: PaperDetailsMessage? = nil
    ) -> some View {
        NavigationStack {
            PaperDetailsContent(
                paper: LibraryPaper(paper: paper, status: status),
                notes: notes,
                saveState: saveState,
                message: message,
                actions: PaperDetailsActions()
            )
        }
    }

    /// The header, status, both links and the abstract.
    @Test func paper() {
        assertHashiyaSnapshots(of: screen(SamplePapers.attention, status: .reading), named: "paper", arabicText: "فتح ملف PDF")
    }

    /// Filled notes on a paper with no abstract, so the fields are on screen: an Arabic note in an English UI and an
    /// English note in an Arabic UI each lay out in their own direction.
    @Test func notesFilled() {
        let notes = PaperNotes(
            summary: "Vision transformers match CNNs when pre-trained on enough data.",
            researchQuestion: "هل تكفي المحوّلات وحدها لتصنيف الصور؟",
            method: "Patches of 16×16 pixels as tokens."
        )
        assertHashiyaSnapshots(of: screen(SamplePapers.vit, notes: notes, saveState: .saved), named: "notes", arabicText: "تم الحفظ")
    }

    /// No title, no authors, no abstract and no notes.
    @Test func untitledWithoutNotes() {
        assertHashiyaSnapshots(of: screen(SamplePapers.untitled), named: "untitled", arabicText: "عمّ تتحدث هذه الورقة، بكلماتك أنت؟")
    }

    @Test func couldNotSave() {
        let view = screen(SamplePapers.vit, notes: PaperNotes(summary: "Unsaved text"), saveState: .failed, message: .notesSaveFailed)
        assertHashiyaSnapshots(of: view, named: "saveFailed", arabicText: "تعذّر حفظ ملاحظاتك")
    }

    @Test func couldNotLoadNotes() {
        assertHashiyaSnapshots(of: screen(SamplePapers.vit, notes: nil), named: "loadFailed", arabicText: "تعذّر تحميل ملاحظاتك")
    }
}
```

`arabicText` must equal one whole looked-up string: the helper checks `recordedLookups.contains(arabicText)`, and every target's lookups are recorded.

- [ ] **Step 2: Run them and see them fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:FeaturePaperDetailsTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL: `cannot find 'PaperDetailsContent' in scope`, then `** TEST FAILED **`.

- [ ] **Step 3: Implement**

`ios/HashiyaKit/Sources/FeaturePaperDetails/NoteField.swift`:
```swift
import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// One note section: its name above a growing text field. The field owns what is on screen: its text is seeded once
/// from the view model and then only reported back, so typing (and marked text being composed) never waits for the
/// view model's state to come back. The view model reads the stored notes once, so nothing else changes the text.
struct NoteField: View {
    let section: NoteSection
    let focus: FocusState<NoteSection?>.Binding
    let onChange: (String) -> Void

    @State private var text: String
    @Environment(\.layoutDirection) private var uiDirection

    init(section: NoteSection, initialText: String, focus: FocusState<NoteSection?>.Binding, onChange: @escaping (String) -> Void) {
        self.section = section
        self.focus = focus
        self.onChange = onChange
        _text = State(initialValue: initialText)
    }

    var body: some View {
        let isFocused = focus.wrappedValue == section
        let label = L10n.noteLabel(section)
        VStack(alignment: .leading, spacing: 6) {
            Text(verbatim: label)
                .font(.hashiya(.label))
                .foregroundStyle(isFocused ? HashiyaColors.primary : HashiyaColors.onSurfaceVariant)
                .frame(maxWidth: .infinity, alignment: .leading)
                // The field carries the same label for VoiceOver.
                .accessibilityHidden(true)
            TextField(
                text: $text,
                prompt: Text(verbatim: L10n.noteHint(section)).foregroundStyle(HashiyaColors.onSurfaceVariant),
                axis: .vertical
            ) {
                Text(verbatim: label)
            }
            .font(.hashiya(.body))
            .foregroundStyle(HashiyaColors.onSurface)
            .lineLimit(2...)
            .textInputAutocapitalization(.sentences)
            .multilineTextAlignment(.leading)
            // An Arabic note lays out right to left in an English UI, and an English note left to right in an Arabic one.
            .environment(\.layoutDirection, ContentDirection.of(text) ?? uiDirection)
            .focused(focus, equals: section)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(isFocused ? HashiyaColors.primary : HashiyaColors.outline, lineWidth: isFocused ? 2 : 1)
            )
            .accessibilityIdentifier("note.\(section.key)")
        }
        .onChange(of: text) { _, newText in onChange(newText) }
    }
}
```

`ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsContent.swift`:
```swift
import Foundation
import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// What Details does when the user acts; `PaperDetailsScreen` wires these to the view model.
public struct PaperDetailsActions {
    public var updateNote: (NoteSection, String) -> Void = { _, _ in }
    public var setStatus: (ReadingStatus) -> Void = { _ in }
    public var openURL: (URL) -> Void = { _ in }
    public var remove: () -> Void = {}
    public var retrySave: () -> Void = {}
    public var retryLoadNotes: () -> Void = {}

    public init() {}
}

/// A loaded paper's Details: the header, the status, the links, the abstract and the notes form, with the banners
/// and the navigation bar's More options. Put it in a `NavigationStack`.
public struct PaperDetailsContent: View {
    private let paper: LibraryPaper
    private let notes: PaperNotes?
    private let saveState: NotesSaveState
    private let message: PaperDetailsMessage?
    private let actions: PaperDetailsActions

    @FocusState private var focusedSection: NoteSection?

    /// - Parameter notes: the notes to seed the fields with; nil shows "Couldn't load your notes" and no fields.
    public init(paper: LibraryPaper, notes: PaperNotes?, saveState: NotesSaveState, message: PaperDetailsMessage?, actions: PaperDetailsActions) {
        self.paper = paper
        self.notes = notes
        self.saveState = saveState
        self.message = message
        self.actions = actions
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header
                ReadingStatusSelector(status: paper.status, onChange: actions.setStatus)
                    .padding(.top, 20)
                links
                abstract
                    .padding(.top, 24)
                notesSection
                    .padding(.top, 24)
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 32)
        }
        .scrollDismissesKeyboard(.interactively)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(HashiyaColors.surface)
        .overlay(alignment: .bottom) { banner }
        .animation(.default, value: message)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { moreOptions }
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button {
                    focusedSection = nil
                } label: {
                    Text(verbatim: L10n.string("details.doneEditing"))
                }
            }
        }
    }

    // MARK: Paper

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            PaperText(PaperFormat.title(paper.paper), style: .previewTitle)
                .accessibilityAddTraits(.isHeader)
            if !paper.paper.authors.isEmpty {
                PaperText(paper.paper.authors.map(\.name).joined(separator: ", "), style: .body, color: HashiyaColors.onSurfaceVariant)
            }
            Text(verbatim: PaperFormat.previewMeta(paper.paper))
                .font(.hashiya(.meta))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .frame(maxWidth: .infinity, alignment: .leading)
            if paper.paper.isOpenAccess {
                StatusBadge(text: DesignSystemStrings.openAccess(hasPDF: paper.paper.openAccessPDFURL != nil), kind: .openAccess)
            }
        }
    }

    @ViewBuilder
    private var links: some View {
        let doi = paper.paper.doi.flatMap(DOILink.url(for:))
        let pdf = paper.paper.openAccessPDFURL.flatMap(URL.init(string:))
        if doi != nil || pdf != nil {
            HashiyaGlassGroup(spacing: 12) {
                HStack(spacing: 12) {
                    if let doi {
                        linkButton(DesignSystemStrings.openDOI, icon: "arrow.up.forward.square") { actions.openURL(doi) }
                    }
                    if let pdf {
                        linkButton(L10n.string("details.openPDF"), icon: "doc.richtext") { actions.openURL(pdf) }
                    }
                }
            }
            .controlSize(.large)
            .padding(.top, 16)
        }
    }

    private func linkButton(_ title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label {
                Text(verbatim: title)
            } icon: {
                Image(systemName: icon)
            }
            .font(.hashiya(.label))
            .frame(maxWidth: .infinity)
        }
        .hashiyaSecondaryButton()
    }

    private var abstract: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(verbatim: DesignSystemStrings.abstract)
                .font(.hashiya(.label))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .accessibilityAddTraits(.isHeader)
            if let abstract = paper.paper.abstract {
                PaperText(abstract, style: .body)
            } else {
                Text(verbatim: DesignSystemStrings.noAbstract)
                    .font(.hashiya(.body))
                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    // MARK: Notes

    private var notesSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text(verbatim: L10n.string("details.notesTitle"))
                    .font(.hashiya(.stateTitle))
                    .foregroundStyle(HashiyaColors.onSurface)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
                if let status = L10n.saveStatus(saveState) {
                    Text(verbatim: status)
                        .font(.hashiya(.meta))
                        .foregroundStyle(saveState == .failed ? HashiyaColors.error : HashiyaColors.onSurfaceVariant)
                        .accessibilityIdentifier("details.saveStatus")
                }
            }
            if let notes {
                ForEach(NoteSection.allCases, id: \.self) { section in
                    NoteField(section: section, initialText: notes[section], focus: $focusedSection) { text in
                        actions.updateNote(section, text)
                    }
                }
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    Text(verbatim: L10n.string("details.notesLoadFailed"))
                        .font(.hashiya(.body))
                        .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    Button(action: actions.retryLoadNotes) {
                        Text(verbatim: L10n.string("details.retry")).font(.hashiya(.label))
                    }
                    .hashiyaSecondaryButton()
                }
            }
        }
    }

    // MARK: Chrome

    private var moreOptions: some View {
        Menu {
            Button(role: .destructive, action: actions.remove) {
                Label {
                    Text(verbatim: DesignSystemStrings.removeFromLibrary)
                } icon: {
                    Image(systemName: "trash")
                }
            }
        } label: {
            Image(systemName: "ellipsis.circle")
        }
        .accessibilityLabel(Text(verbatim: L10n.string("details.moreOptions")))
    }

    @ViewBuilder
    private var banner: some View {
        switch message {
        case .notesSaveFailed:
            HashiyaBanner(
                text: L10n.string("details.notesSaveFailedMessage"),
                actionTitle: L10n.string("details.retry"),
                action: actions.retrySave
            )
        case .statusUpdateFailed:
            HashiyaBanner(text: L10n.string("details.statusUpdateFailed"))
        case nil:
            EmptyView()
        }
    }
}
```

`ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsScreen.swift`:
```swift
import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// Details for a saved paper, pushed on a tab's navigation stack. It hides the tab bar, saves the notes when it
/// disappears and when the app leaves the foreground, and reports when it should go away.
public struct PaperDetailsScreen: View {
    @State private var viewModel: PaperDetailsViewModel
    private let onClose: () -> Void
    private let onRemove: (String) -> Void

    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    /// - Parameters:
    ///   - viewModel: evaluated on every update, but only the first instance is kept; its `init` starts nothing.
    ///   - onClose: the paper stopped being saved; pop this screen.
    ///   - onRemove: the user removed the paper and its notes are saved; pop this screen and remove it (with Undo).
    public init(
        viewModel: @autoclosure () -> PaperDetailsViewModel,
        onClose: @escaping () -> Void,
        onRemove: @escaping (String) -> Void
    ) {
        _viewModel = State(wrappedValue: viewModel())
        self.onClose = onClose
        self.onRemove = onRemove
    }

    public var body: some View {
        content
            .toolbar(.hidden, for: .tabBar)
            .task { await viewModel.start() }
            .onDisappear { viewModel.flush() }
            .onChange(of: scenePhase) { _, phase in
                // Inactive comes first on the way to the background: write before the database is suspended.
                if phase != .active { viewModel.flush() }
            }
            .onChange(of: viewModel.exit) { _, exit in
                switch exit {
                case .closed: onClose()
                case .removed: onRemove(viewModel.openAlexID)
                case nil: break
                }
            }
            .task(id: viewModel.message) {
                // A newer message cancels this task: then it must not clear the new one.
                guard viewModel.message != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                viewModel.message = nil
            }
    }

    @ViewBuilder
    private var content: some View {
        if let paper = viewModel.paper, viewModel.notesLoad != .loading {
            PaperDetailsContent(
                paper: paper,
                notes: viewModel.notesLoad == .failed ? nil : viewModel.notes,
                saveState: viewModel.saveState,
                message: viewModel.message,
                actions: actions
            )
        } else {
            LoadingSkeleton(rows: 3)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(HashiyaColors.surface)
                .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var actions: PaperDetailsActions {
        var actions = PaperDetailsActions()
        let viewModel = viewModel
        actions.updateNote = { section, text in viewModel.updateNote(section, text) }
        actions.setStatus = { status in Task { await viewModel.setStatus(status) } }
        actions.openURL = { url in openURL(url) }
        actions.remove = { Task { await viewModel.remove() } }
        actions.retrySave = { viewModel.flush() }
        actions.retryLoadNotes = { Task { await viewModel.retryLoadNotes() } }
        return actions
    }
}
```

- [ ] **Step 4: Run them and see them pass**

Run: the Step 2 command. Expected: PASS.
Then regenerate the project (`xcodegen generate --spec ios/project.yml`: the snapshot bundle has a new file) and run `HashiyaSnapshotTests` on a Mac. Expected: only the new images fail with `No reference was found on disk`. That means `PaperDetailsSnapshotTests` (5 states × 4 variants) and Task 4's `previewWithOpenDetails`.

- [ ] **Step 5: Commit, push and read CI**

```bash
git add ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsContent.swift ios/HashiyaKit/Sources/FeaturePaperDetails/NoteField.swift \
  ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsScreen.swift \
  ios/HashiyaKit/Tests/FeaturePaperDetailsTests/PaperDetailsContentTests.swift ios/HashiyaSnapshotTests/PaperDetailsSnapshotTests.swift
git commit -m "feat: add the iOS paper details screen with the notes form"
git log --format='%an <%ae>' origin/main..HEAD
git push
```
Read the "iOS" run for this SHA. Expected: only the new snapshot images fail, each with `No reference was found on disk`. `PaperDetailsContentTests` passes.

---
### Task 7: Wiring — the Library opens Details, Search opens it for saved papers, `RootView` navigation, and the UI tests

**Files:**
- Modify: `ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift`, `LibraryView.swift`
- Modify: `ios/HashiyaKit/Sources/FeatureSearch/SearchViewModel.swift`, `SearchView.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/PaperPreviewContent.swift` (drop the status selector parameters)
- Modify: `ios/Hashiya/RootView.swift`, `AppContainer.swift`
- Test: Modify `ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryViewModelTests.swift`, `ios/HashiyaKit/Tests/FeatureSearchTests/SearchViewModelTests.swift`, `ios/HashiyaKit/Tests/HashiyaDesignSystemTests/ReadingStatusSelectorTests.swift`, `ios/HashiyaSnapshotTests/DesignSystemSnapshotTests.swift`, `ios/HashiyaUITests/LibraryFlowTests.swift`; create `ios/HashiyaUITests/PaperDetailsFlowTests.swift`
- Delete: `ios/HashiyaSnapshotTests/__Snapshots__/iOS26/DesignSystemSnapshotTests/previewWithTheStatusSelector.previewStatus-*.png` and the same four under `iOS18/`

**Interfaces:**
- Consumes: Tasks 3–6.
- Produces:
  - `LibraryViewModel.remove(openAlexID:) async`;
  - `LibraryView(…, onOpenPaper: (String) -> Void = { _ in })`;
  - `SearchViewModel.remove(openAlexID:) async`;
  - `SearchView(…, onOpenPaper: (String) -> Void = { _ in })`;
  - `AppContainer.pendingWrites`, `AppContainer.makePaperDetailsViewModel(openAlexID:)`.
- Removes: `LibraryViewModel.selectedPaperID`, `selectedPaper`, `select(_:)` and the whole-library observation; `PaperPreviewContent`'s `status` and `onStatusChange`.

- [ ] **Step 1: Write the failing tests**

`ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryViewModelTests.swift`:

1. Delete `aStatusChangeOutOfTheChipKeepsThePreviewOpen`, `selectingOpensThePreviewAndDismissingClosesIt` and `theSheetClosesWhenThePaperDisappears` (the Library has no preview any more; Details has its own tests).
2. Replace `removingOffersUndoAndClosesThePreview` with:
```swift
    @Test func removingOffersUndo() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.bert, SamplePapers.attention])
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.papers.count == 2 })

        await viewModel.remove(SamplePapers.attention)

        #expect(viewModel.pendingUndo?.paper == SamplePapers.attention)
        #expect(await eventually { ids(viewModel) == [SamplePapers.bert.openAlexID] })
    }

    /// Remove on Details hands the paper back by its ID; Undo restores it with its status and notes.
    @Test func removingByIDOffersUndoThatRestoresTheNotes() async {
        let notes = PaperNotes(summary: "Kept")
        let library = FakeLibraryRepository(
            saved: [SamplePapers.bert, SamplePapers.attention],
            statuses: [SamplePapers.attention.openAlexID: .reading],
            notes: [SamplePapers.attention.openAlexID: notes]
        )
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.papers.count == 2 })

        await viewModel.remove(openAlexID: SamplePapers.attention.openAlexID)
        #expect(viewModel.pendingUndo?.notes == notes)
        #expect(await eventually { viewModel.papers.count == 1 })

        await viewModel.undo()

        #expect(await eventually { viewModel.papers.contains(LibraryPaper(paper: SamplePapers.attention, status: .reading)) })
        #expect(library.notes(of: SamplePapers.attention.openAlexID) == notes)
    }
```

`ios/HashiyaKit/Tests/FeatureSearchTests/SearchViewModelTests.swift`: add at the end of the struct:
```swift
    /// Remove on Details opened from Search: the same as the sheet's toggle removing.
    @Test func removingByIDRemovesThePaper() async throws {
        try await library.save(SamplePapers.bert)
        let viewModel = makeViewModel(repository())

        await viewModel.remove(openAlexID: SamplePapers.bert.openAlexID)

        #expect(library.savedPapers.isEmpty)
        #expect(viewModel.message == nil)
    }

    @Test func aFailedRemoveByIDShowsRemoveFailed() async throws {
        try await library.save(SamplePapers.bert)
        library.setFailRemoves(true)
        let viewModel = makeViewModel(repository())

        await viewModel.remove(openAlexID: SamplePapers.bert.openAlexID)

        #expect(viewModel.message == .removeFailed)
        #expect(library.savedPapers == [SamplePapers.bert])
    }
```

`ios/HashiyaKit/Tests/HashiyaDesignSystemTests/ReadingStatusSelectorTests.swift`: delete `thePreviewShowsTheSelectorOnlyWithAStatus`, whose parameters go away.

`ios/HashiyaSnapshotTests/DesignSystemSnapshotTests.swift`: delete `previewWithTheStatusSelector`, then delete its eight baselines:
```bash
git rm ios/HashiyaSnapshotTests/__Snapshots__/iOS26/DesignSystemSnapshotTests/previewWithTheStatusSelector.previewStatus-*.png \
  ios/HashiyaSnapshotTests/__Snapshots__/iOS18/DesignSystemSnapshotTests/previewWithTheStatusSelector.previewStatus-*.png
```

`ios/HashiyaUITests/LibraryFlowTests.swift`: replace `testThePreviewChangesTheStatusAndTheSearchKeyHidesTheKeyboard` with:
```swift
    @MainActor
    func testDetailsChangesTheStatusAndTheSearchKeyHidesTheKeyboard() {
        let app = launchApp()
        saveTwoPapers(in: app)

        row("BERT", in: app).buttons.firstMatch.tap()
        let read = app.segmentedControls.buttons["Read"]
        XCTAssertTrue(read.waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.segmentedControls.buttons["To read"].isSelected)
        read.tap()
        XCTAssertTrue(read.isSelected)
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.buttons["Read · 1"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(row("BERT", in: app).buttons["Status: Read. Change status"].exists)

        let field = app.searchFields["Search your library"]
        field.tap()
        field.typeText("vaswani\n")
        XCTAssertTrue(app.staticTexts["1 paper"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(row("Attention Is All You Need", in: app).exists)
        let hidden = expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: app.keyboards.firstMatch)
        wait(for: [hidden], timeout: UITestTimeout.long)
    }
```
In `testChangingAStatusFiltersAndSearchesTheLibrary`, change the message of `XCTAssertFalse(app.segmentedControls.firstMatch.exists, …)` to `"Tapping the badge must not open Details"`; the assertion itself stays.

`ios/HashiyaUITests/PaperDetailsFlowTests.swift`:
```swift
import XCTest

/// Details end to end with `-ui-testing`: the UI tests' own library file and the stub search, no network.
@MainActor
final class PaperDetailsFlowTests: XCTestCase {
    private func launchApp() -> XCUIApplication {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-ui-testing", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        return app
    }

    private let searchField = "Search, or paste a DOI, arXiv ID or link"

    /// Saves the stub search's first paper, Attention, and leaves the app on the Search results.
    private func saveAttention(in app: XCUIApplication) {
        app.tabBars.buttons["Search"].tap()
        let field = app.searchFields[searchField]
        XCTAssertTrue(field.waitForExistence(timeout: UITestTimeout.long))
        field.tap()
        field.typeText("attention\n")
        XCTAssertTrue(app.staticTexts["About 3 results"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Save"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["In library"].waitForExistence(timeout: UITestTimeout.long))
    }

    private func attentionRow(in app: XCUIApplication) -> XCUIElement {
        app.cells.containing(NSPredicate(format: "label BEGINSWITH %@", "Attention Is All You Need")).firstMatch
    }

    private func openAttentionFromTheLibrary(in app: XCUIApplication) {
        app.tabBars.buttons["Library"].tap()
        let row = attentionRow(in: app)
        XCTAssertTrue(row.waitForExistence(timeout: UITestTimeout.long))
        row.buttons.firstMatch.tap()
        XCTAssertTrue(app.textViews["note.summary"].waitForExistence(timeout: UITestTimeout.long))
    }

    /// A Search result: its title is one button whose label starts with the title (`PaperCard`).
    private func card(_ titlePrefix: String, in app: XCUIApplication) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", titlePrefix)).firstMatch
    }

    private func back(in app: XCUIApplication) {
        app.navigationBars.buttons.element(boundBy: 0).tap()
    }

    func testNotesSaveAndAreFoundByTheLibrarySearch() {
        let app = launchApp()
        saveAttention(in: app)
        openAttentionFromTheLibrary(in: app)
        XCTAssertFalse(app.tabBars.firstMatch.isHittable, "Details hides the tab bar")
        XCTAssertTrue(app.staticTexts["Ashish Vaswani, Noam Shazeer"].exists)

        let summary = app.textViews["note.summary"]
        summary.tap()
        summary.typeText("Ablation")
        let method = app.textViews["note.method"]
        method.tap()
        method.typeText("Encoder")
        XCTAssertTrue(app.staticTexts["Saved"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertEqual(summary.value as? String, "Ablation")
        XCTAssertEqual(method.value as? String, "Encoder")

        back(in: app)
        let field = app.searchFields["Search your library"]
        XCTAssertTrue(field.waitForExistence(timeout: UITestTimeout.long))
        field.tap()
        field.typeText("ablation\n")
        XCTAssertTrue(app.staticTexts["1 paper"].waitForExistence(timeout: UITestTimeout.long))

        attentionRow(in: app).buttons.firstMatch.tap()
        XCTAssertTrue(app.textViews["note.summary"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertEqual(app.textViews["note.summary"].value as? String, "Ablation")
    }

    func testRemoveFromDetailsOffersUndoWithTheNotes() {
        let app = launchApp()
        saveAttention(in: app)
        openAttentionFromTheLibrary(in: app)
        let summary = app.textViews["note.summary"]
        summary.tap()
        summary.typeText("Keep this")

        // Remove right away: the pending note is saved before the paper goes.
        app.buttons["More options"].tap()
        app.buttons["Remove from library"].tap()

        XCTAssertTrue(app.staticTexts["Removed from library"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.staticTexts["No saved papers yet"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Undo"].tap()

        let row = attentionRow(in: app)
        XCTAssertTrue(row.waitForExistence(timeout: UITestTimeout.long))
        row.buttons.firstMatch.tap()
        XCTAssertTrue(app.textViews["note.summary"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertEqual(app.textViews["note.summary"].value as? String, "Keep this")
    }

    func testSearchOpensDetailsForSavedPapersOnly() {
        let app = launchApp()
        saveAttention(in: app)

        // BERT isn't saved: its sheet has no Open details.
        card("BERT", in: app).tap()
        XCTAssertTrue(app.buttons["Save to library"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertFalse(app.buttons["Open details"].exists)
        app.swipeDown(velocity: .fast)

        card("Attention Is All You Need", in: app).tap()
        let open = app.buttons["Open details"]
        XCTAssertTrue(open.waitForExistence(timeout: UITestTimeout.long))
        open.tap()

        XCTAssertTrue(app.textViews["note.summary"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.buttons["More options"].exists)
        back(in: app)
        XCTAssertTrue(app.staticTexts["About 3 results"].waitForExistence(timeout: UITestTimeout.long))
    }
}
```

- [ ] **Step 2: Run them and see them fail**

Run the package tests: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:FeatureLibraryTests -only-testing:FeatureSearchTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL: `value of type 'LibraryViewModel' has no member 'remove(openAlexID:)'` (or `incorrect argument label`), then `** TEST FAILED **`.

- [ ] **Step 3: Implement**

`ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/PaperPreviewContent.swift`:
- Remove the `status` and `onStatusChange` properties, their initializer parameters and doc lines, and the `if let status { ReadingStatusSelector(…) … }` block at the top of `actions`.
- Unwrap the outer `VStack(spacing: 0)`, so `actions` is the `HashiyaGlassGroup` with its `.controlSize(.large)`, `.padding(.horizontal, 16)` and `.padding(.vertical, 12)`.
- Change the struct's doc comment to `/// The preview sheet's body: the full paper, then Open details (saved papers in Search), Open DOI and Save/Remove.`

`ReadingStatusSelector` stays in the design system: Details uses it.

`ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift`:
1. Delete `public var selectedPaperID: String?` and its comment, `@ObservationIgnored private let observations = TaskBag()`, `private var allPapers: [LibraryPaper] = []` and its comment, the `selectedPaper` computed property, and `select(_:)`.
2. In `init`, delete the `observations.add(Task { … })` block, so `init` sets `library` and `sleep`, then calls `observeFilter()`.
3. Change `// MARK: Preview, remove and Undo` to `// MARK: Remove and Undo`, and replace `remove(_:)` with:
```swift
    /// A swipe: removes the paper; only the latest removal can be undone.
    public func remove(_ paper: Paper) async {
        await remove(openAlexID: paper.openAlexID)
    }

    /// Remove on Details, after it saved the notes: the same as a swipe, with Undo.
    public func remove(openAlexID: String) async {
        do {
            if let removed = try await library.remove(openAlexID: openAlexID) {
                pendingUndo = removed
            }
        } catch {
            Self.log("remove failed")
        }
    }
```
4. In `undo()`'s doc comment, change `with its status.` to `with its status and notes.`

`ios/HashiyaKit/Sources/FeatureLibrary/LibraryView.swift`:
1. Add `private let onOpenPaper: (String) -> Void` after `onOpenSettings`, and change the initializer to:
```swift
    /// - Parameters:
    ///   - onAddPaper: the Add paper button; the app opens Search ready for input.
    ///   - onOpenPaper: a row tap, with the paper's OpenAlex ID; the app pushes Details.
    public init(
        viewModel: LibraryViewModel,
        onGoToSearch: @escaping () -> Void,
        onAddPaper: @escaping () -> Void,
        onOpenSettings: @escaping () -> Void,
        onOpenPaper: @escaping (String) -> Void = { _ in }
    ) {
        self.viewModel = viewModel
        self.onGoToSearch = onGoToSearch
        self.onAddPaper = onAddPaper
        self.onOpenSettings = onOpenSettings
        self.onOpenPaper = onOpenPaper
    }
```
2. Delete `@Environment(\.openURL) private var openURL`, the `.sheet(item: …) { saved in preview(saved) }` modifier, and the `preview(_:)` function.
3. In `list`, replace `.onTapGesture { viewModel.select(saved.paper) }` with `.onTapGesture { onOpenPaper(saved.id) }`, and change the comment above `.buttonStyle(.borderless)` to `// Keeps the badge's menu and the row's tap separate: tapping the badge never opens Details.`

`ios/HashiyaKit/Sources/FeatureSearch/SearchViewModel.swift`: replace `toggleSave(_:)` with:
```swift
    /// Removes the paper when it is in the library, else saves it.
    public func toggleSave(_ paper: Paper) async {
        if isSaved(paper) {
            await remove(openAlexID: paper.openAlexID)
        } else {
            do {
                try await library.save(paper)
            } catch {
                message = .saveFailed
            }
        }
    }

    /// The sheet's Remove, and Remove on Details opened from Search.
    public func remove(openAlexID: String) async {
        do {
            _ = try await library.remove(openAlexID: openAlexID)
        } catch {
            message = .removeFailed
        }
    }
```

`ios/HashiyaKit/Sources/FeatureSearch/SearchView.swift`:
1. Add `private let onOpenPaper: (String) -> Void` after `onOpenSettings`, and `@State private var detailsRequest: String?` after `isSearchActive`.
2. Replace the initializer with:
```swift
    /// - Parameter onOpenPaper: Open details in a saved paper's sheet, with its OpenAlex ID, once the sheet is gone.
    public init(viewModel: SearchViewModel, onOpenSettings: @escaping () -> Void, onOpenPaper: @escaping (String) -> Void = { _ in }) {
        self.viewModel = viewModel
        self.onOpenSettings = onOpenSettings
        self.onOpenPaper = onOpenPaper
    }
```
3. Replace `.sheet(item: $viewModel.selectedPaper) { paper in` with `.sheet(item: $viewModel.selectedPaper, onDismiss: openRequestedDetails) { paper in`.
4. Replace `preview(_:)` with, and add after it:
```swift
    private func preview(_ paper: Paper) -> some View {
        let saved = viewModel.isSaved(paper)
        return PaperPreviewContent(
            paper: paper,
            inLibrary: saved,
            onToggleSave: { Task { await viewModel.toggleSave(paper) } },
            onOpenDOI: openDOI,
            onOpenDetails: saved ? { openDetails(paper) } : nil
        )
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    /// Closes the sheet; Details is pushed once it is gone, so the push never overlaps the dismissal.
    private func openDetails(_ paper: Paper) {
        detailsRequest = paper.openAlexID
        viewModel.selectedPaper = nil
    }

    private func openRequestedDetails() {
        guard let id = detailsRequest else { return }
        detailsRequest = nil
        onOpenPaper(id)
    }
```

`ios/Hashiya/AppContainer.swift`:
1. Add `import FeaturePaperDetails` after `import FeatureLibrary`.
2. After `let appUpdateRepository: any AppUpdateRepository`, add:
```swift
    /// Note writes the app waits for before it suspends the shared database in the background.
    let pendingWrites = PendingWrites()
```
3. After `makeLibraryViewModel()`, add:
```swift
    func makePaperDetailsViewModel(openAlexID: String) -> PaperDetailsViewModel {
        PaperDetailsViewModel(openAlexID: openAlexID, library: libraryRepository, pendingWrites: pendingWrites)
    }
```

`ios/Hashiya/RootView.swift`:
1. Add `import FeaturePaperDetails` after `import FeatureLibrary`, and change the struct's doc comment to `/// Library and Search tabs, each in its own navigation stack that can push a saved paper's Details; Settings as a sheet from either.`
2. After `@State private var showsSettings = false`, add:
```swift
    @State private var libraryPath: [PaperDetailsRoute] = []
    @State private var searchPath: [PaperDetailsRoute] = []
    /// Bumped on every scene phase change, so a background suspend that waited for writes is dropped once the app is active again.
    @State private var phaseGeneration = 0
```
3. Replace the `.onChange(of: scenePhase, initial: true) { … }` block with:
```swift
        .onChange(of: scenePhase, initial: true) { _, phase in
            phaseGeneration += 1
            switch phase {
            case .active:
                // Papers saved in the Share Extension appear in the Library and as "In library".
                SharedLibraryDatabase.resume()
                Task { await container.libraryRepository.refreshAfterExternalChanges() }
                Task { await appUpdate.check() }
            case .background:
                // A suspended database refuses writes: let the notes Details just flushed land first.
                let generation = phaseGeneration
                Task {
                    await container.pendingWrites.drained()
                    guard phaseGeneration == generation else { return }
                    SharedLibraryDatabase.suspend()
                }
            default:
                break
            }
        }
```
4. Replace the Library tab's `NavigationStack { LibraryView(…) }` with:
```swift
            NavigationStack(path: $libraryPath) {
                LibraryView(
                    viewModel: libraryViewModel,
                    onGoToSearch: { selectedTab = .search },
                    onAddPaper: {
                        searchViewModel.startFresh(focus: true)
                        selectedTab = .search
                    },
                    onOpenSettings: { showsSettings = true },
                    onOpenPaper: { libraryPath.append(PaperDetailsRoute(openAlexID: $0)) }
                )
                .navigationDestination(for: PaperDetailsRoute.self) { route in
                    details(route, in: .library)
                }
            }
```
5. Replace the Search tab's `NavigationStack { SearchView(…) }` with:
```swift
            NavigationStack(path: $searchPath) {
                SearchView(
                    viewModel: searchViewModel,
                    onOpenSettings: { showsSettings = true },
                    onOpenPaper: { searchPath.append(PaperDetailsRoute(openAlexID: $0)) }
                )
                .navigationDestination(for: PaperDetailsRoute.self) { route in
                    details(route, in: .search)
                }
            }
```
6. Before `presentUITestingShareSheetIfRequested()`, add:
```swift
    /// Details on `tab`'s stack. Remove pops it, then removes the paper the way that tab does: the Library with its
    /// Undo banner, Search like its sheet's toggle.
    private func details(_ route: PaperDetailsRoute, in tab: Tab) -> some View {
        PaperDetailsScreen(
            viewModel: container.makePaperDetailsViewModel(openAlexID: route.openAlexID),
            onClose: { pop(tab) },
            onRemove: { id in
                pop(tab)
                Task {
                    switch tab {
                    case .library: await libraryViewModel.remove(openAlexID: id)
                    case .search: await searchViewModel.remove(openAlexID: id)
                    }
                }
            }
        )
    }

    private func pop(_ tab: Tab) {
        switch tab {
        case .library: if !libraryPath.isEmpty { libraryPath.removeLast() }
        case .search: if !searchPath.isEmpty { searchPath.removeLast() }
        }
    }
```

- [ ] **Step 4: Run everything and see it pass**

Run: `python3 ios/scripts/check-translations.py && xcodegen generate --spec ios/project.yml && xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -collect-test-diagnostics never 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: every unit and UI test passes. The only failures are the new snapshot images without baselines (`PaperDetailsSnapshotTests`, `previewWithOpenDetails`), each with `No reference was found on disk`.

- [ ] **Step 5: Commit, push and read CI**

```bash
git add ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift ios/HashiyaKit/Sources/FeatureLibrary/LibraryView.swift \
  ios/HashiyaKit/Sources/FeatureSearch/SearchViewModel.swift ios/HashiyaKit/Sources/FeatureSearch/SearchView.swift \
  ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/PaperPreviewContent.swift \
  ios/Hashiya/RootView.swift ios/Hashiya/AppContainer.swift \
  ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryViewModelTests.swift ios/HashiyaKit/Tests/FeatureSearchTests/SearchViewModelTests.swift \
  ios/HashiyaKit/Tests/HashiyaDesignSystemTests/ReadingStatusSelectorTests.swift ios/HashiyaSnapshotTests/DesignSystemSnapshotTests.swift \
  ios/HashiyaUITests/LibraryFlowTests.swift ios/HashiyaUITests/PaperDetailsFlowTests.swift
git commit -m "feat: open paper details from the iOS Library and Search, and remove from Details with Undo"
git log --format='%an <%ae>' origin/main..HEAD
git push
```
(The `git rm` of the old baselines in Step 1 is already staged.)

Read the "iOS" run for this SHA. The unit and snapshot steps fail only on the new images, so the UI-test step never runs in this run. To see the UI tests before the baselines exist, read the run in Task 8 after the baselines land. Expected here: every failure in the two snapshot steps is `No reference was found on disk` for a new image.

---
### Task 8: READMEs, the CI snapshot baselines, full verification, and device checks

**Files:**
- Modify: `ios/README.md`, `README.md`
- Add: every new baseline under `ios/HashiyaSnapshotTests/__Snapshots__/iOS26/` and `iOS18/`, recorded on CI:
  - `PaperDetailsSnapshotTests/*.png`: 5 states × 4 variants × 2 OSes = 40 images;
  - `DesignSystemSnapshotTests/previewWithOpenDetails.*.png`: 8 images.

- [ ] **Step 1: Update the READMEs**

`ios/README.md`, in the paragraph that lists the package targets:
- Add `FeaturePaperDetails` after `FeatureLibrary` in the target list.
- Replace `(`v1`, then `v2` for the reading status and the full-text index)` with `(`v1`, then `v2` for the reading status and the full-text index, then `v3` for the notes)`.

`README.md`, in the iOS paragraph:
- Replace `with the features of sub-projects 1 to 3` with `with the features of sub-projects 1 to 4`.
- Replace `the offline Library with full-text search and reading status` with `the offline Library with full-text search and reading status, a details screen for each saved paper with notes that save themselves and are searchable`.

```bash
git add ios/README.md README.md
git commit -m "docs: describe iOS paper details and notes"
```

- [ ] **Step 2: Record the baselines on CI**

```bash
git log --format='%an <%ae>' origin/main..HEAD   # only Fady <fady.fouad.a@gmail.com>
git push
branch="record-snapshots-ios/$(git rev-parse --short HEAD)-$(date +%s)"
git push origin "HEAD:refs/heads/$branch"
```
Wait for the "iOS snapshot baselines" run on `$branch` (`mcp__github__actions_list`, `list_workflow_runs`, `ios-record-snapshots.yml`). Expected: `success`, and its log prints `Recorded N iOS26 baseline images` and `Recorded N iOS18 baseline images`.

Download its `ios-snapshot-baselines` artifact (`mcp__github__actions_list`, `list_workflow_run_artifacts`, then `mcp__github__actions_get`, `download_workflow_run_artifact`, then fetch the URL):
- **If the download works:** `unzip` the artifact, `tar -xf ios-snapshot-baselines.tar` at the repository root, and delete the recording branch with `git push origin --delete "$branch"`.
- **If the download is blocked:** it was on 2026-09-30, when the environment's network policy denied `productionresultssa2.blob.core.windows.net`. Delete the recording branch, **stop, and ask Fady** to run `ios/scripts/record-snapshots-on-ci.sh` from a Mac at this commit and push the images. Continue at Step 3 once they are on the branch.

- [ ] **Step 3: Check the new images, and that nothing else changed**

```bash
git status --short ios/HashiyaSnapshotTests/__Snapshots__
```
Expected: only new files:
- `PaperDetailsSnapshotTests/` (40);
- `DesignSystemSnapshotTests/previewWithOpenDetails.*` (8).

If an existing image shows as modified, a change altered a screen it shouldn't have. Stop and find out why; don't commit it.

Look at each new image (the Read tool shows PNGs), at least the iOS 26 ones:
- **English:** left-to-right, with the header, status, both links and the abstract (`paper`).
- **Filled notes (`notes`):** the Arabic Research question aligned right inside the English UI; in the Arabic images, the English Summary and Method aligned left.
- **`untitled`:** Untitled with no abstract and every hint shown.
- **`saveFailed`:** **Couldn't save** in the error colour, and the banner with Retry.
- **`loadFailed`:** "Couldn't load your notes" with Retry, and no fields.
- **Dark:** readable, with no white fields.
- **iOS 26:** glass banner and buttons.

```bash
git add ios/HashiyaSnapshotTests/__Snapshots__/iOS26/PaperDetailsSnapshotTests ios/HashiyaSnapshotTests/__Snapshots__/iOS18/PaperDetailsSnapshotTests \
  ios/HashiyaSnapshotTests/__Snapshots__/iOS26/DesignSystemSnapshotTests/previewWithOpenDetails.* \
  ios/HashiyaSnapshotTests/__Snapshots__/iOS18/DesignSystemSnapshotTests/previewWithOpenDetails.*
git commit -m "test: record the iOS paper details snapshot baselines on CI"
git log --format='%an <%ae>' origin/main..HEAD
git push
```

- [ ] **Step 4: Read the full CI run**

Wait for the "iOS" run on this SHA. Expected: `success` in every step:
- Arabic translations;
- unit and snapshot tests on iOS 26 and on iOS 18;
- UI tests on both, including `PaperDetailsFlowTests`' three tests and the rewritten `LibraryFlowTests`.

On any failure, read the job log (`mcp__github__get_job_logs` with `failed_only: true`). Find the cause, fix it with a new commit, push, and read the next run.
- A UI test that fails is never "a flake": find out why.
- A snapshot mismatch in an existing image means a screen changed. Download the `ios-snapshot-diffs` artifact if the network allows it, or ask Fady to look at the diff.

Say it's done only when this run is green.

- [ ] **Step 5: Device checks (Fady, on a Mac or iPhone)**

These are the spec's acceptance criteria that CI can't show. List them for Fady in the hand-off, don't claim them:
1. Install the `main` build, save three papers and set one to Reading, then install this branch over it. Every paper and status is there, and the Library search still finds them.
2. Tap a row. Details shows every author, the abstract and the status, and Open DOI / Open PDF open Safari.
3. Type in Summary and Method, pause, and see **Saved**. Go back and reopen: the text is there.
4. Type a note, swipe home at once, then end the app from the app switcher. Reopen: the note is there.
5. The Library search for a word only in a note finds the paper, including an Arabic word typed without tashkeel against a note written with it.
6. Remove from **More options**: back on the Library with Undo, and Undo brings back the paper with its notes and status.
7. In Search, a saved paper's sheet has **Open details** and an unsaved one's doesn't.
8. In العربية: Details mirrors, the labels and hints are Arabic, and an English note stays left-to-right. Light and dark both read well, and iOS 26 shows glass.
9. VoiceOver reads the title as a header, each field by its section name, and the save status.
