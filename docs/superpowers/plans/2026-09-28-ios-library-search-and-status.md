# iOS Library Search and Reading Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the iOS Library usable once it grows, like Android sub-project 3: search saved papers offline by words in their title, authors, abstract and venue (prefixes, case, accents, tashkeel, alef/yaa variants and Arabic-Indic digits all folded), track each paper as **To read**, **Reading** or **Read** from its row's badge or its preview, and filter by status with live counts — in English and Arabic, light and dark, with existing libraries upgraded in place.

**Architecture:** Builds on plans 1 and 2 without new package targets. `HashiyaModel` gains `ReadingStatus`, `LibraryPaper` and `searchableText`; `HashiyaDatabase` (now importing `HashiyaModel`) gains GRDB migration `v2` (the `reading_status` column and the FTS4 table `paper_search`, backfilled), keeps the index in step inside every `PaperStore` write, and reads papers, per-status counts and the library total in **one** `ValueObservation` fetch (`LibraryRows`); `HashiyaData` gains `ftsMatch`, the fixed stored status values and `LibrarySnapshot`, and its `LibraryRepository` replaces `observeSavedPapers()` with `observeLibrary(query:status:)` plus `setStatus`; `HashiyaDesignSystem` gains `readingStatusLabel` and the preview's segmented `ReadingStatusSelector`; `FeatureLibrary` gains a `LibraryState` view model (debounce, Search key, chips, restoration) and the searchable screen with chips and status badges. Because the snapshot is consistent, the view model decides Empty vs No papers match from one value and needs none of Android's stale-state rules.

**Tech Stack:** Swift 6 (language mode 6, strict concurrency), SwiftUI, iOS 17+, Swift Testing, XCTest (UI tests), GRDB.swift 7.11.1 (SQLite FTS4, `unicode61`), swift-snapshot-testing 1.19.6, String Catalogs, XcodeGen 2.46.0; local toolchain Xcode 27.0 with the iPhone 16 Pro iOS 18.2 simulator; CI `macos-15` with Xcode 16.4 and the iPhone 16 iOS 18.5 simulator.

**Spec:** docs/superpowers/specs/2026-09-28-ios-library-search-and-status-design.md

## Global Constraints

- Everything in plans 1 and 2's Global Constraints still applies: iOS 17 minimum and Swift 6 language mode with strict concurrency; code must also build with CI's Xcode 16.4 (Swift 6.1: no `Mutex`, no isolated `deinit`, no `@concurrent`; `OSAllocatedUnfairLock` for shared state, `TaskBag` for observation tasks); XcodeGen (`ios/project.yml` committed, `ios/Hashiya.xcodeproj` generated and git-ignored — run `xcodegen generate --spec ios/project.yml` after adding or removing files in `ios/Hashiya`, `ios/HashiyaShare`, `ios/Shared` or `ios/HashiyaUITests`); exact dependency pins (GRDB 7.11.1, swift-snapshot-testing 1.19.6); no new package targets; features never import `HashiyaNetwork`, `HashiyaDatabase` or GRDB, and never each other; every user-visible string from a `Localizable.xcstrings` (`extractionState: manual`) through the target's `L10n`, shown with `Text(verbatim:)`, numbers formatted with `PaperFormat.number` and passed as `%@`; snapshot suites `@MainActor @Suite(.serialized)`; snapshot baselines only from CI; the UI tests' own App Group library file; `refreshAfterExternalChanges()` after the Share Extension writes.
- Database: exactly one new migration, `"v2"`, registered after `"v1"` in `HashiyaDatabase.migrator`: `ALTER TABLE papers ADD COLUMN reading_status TEXT NOT NULL DEFAULT 'to_read'` and the raw `CREATE VIRTUAL TABLE paper_search USING fts4(paper_id, title, authors, abstract, venue, tokenize=unicode61, notindexed=paper_id)`, then the backfill (authors in position order) through `PaperSearchRow.make`. Never `eraseDatabaseOnSchemaChange` or any destructive fallback. Every write that adds or deletes a paper (`PaperStore.insert`, `deleteByOpenAlexID`) writes or deletes its `paper_search` row in the same transaction; `setStatus` never touches the index or `saved_at`.
- Stored statuses are the fixed strings `to_read`, `reading`, `read` (`ReadingStatus.storedValue` / `ReadingStatus(stored:)` in `HashiyaData`, never Swift case names); any other stored value reads and counts as To read. New saves (app and Share Extension) start as To read; Undo restores the status.
- `searchableText`: NFKD (`decomposedStringWithCompatibilityMapping`), drop every Mn/Mc/Me scalar, drop tatweel U+0640, `أ إ آ ٱ` → `ا`, `ى` → `ي`, every decimal digit (Nd) → its ASCII digit, then `lowercased()` (locale-independent). The index and the queries both pass through it.
- `ftsMatch`: `searchableText`, split on every run of scalars that are not letters (Lu, Ll, Lt, Lm, Lo) or numbers (Nd, Nl, No), each word as `"word*"` (the star **inside** the quotes), joined with single spaces; `nil` when no word is left. Nothing else ever reaches `MATCH`.
- `observeLibrary(query:status:)` emits one `LibrarySnapshot` per change from a single GRDB `ValueObservation` fetch (papers for query + status, per-status counts for the query with all three keys, whole-library total); the view model decides `.empty` from `libraryTotal == 0` and never combines separate streams.
- Strings: keys, English and Arabic verbatim from spec §9 (they match Android's `strings.xml`); `library.filterCount` is `%1$@ · %2$@` in English and `%1$@ (%2$@)` in Arabic (Android's `%1$s (%2$s)`), because "·" beside Arabic digits reads like "٠". Add them with the Python snippets below (they rewrite a catalog exactly as Xcode formats it); never hand-edit the JSON.
- A status is never shown by colour alone: the badge, chips and selector always carry the status's name, Read's badge adds a check, the selected chip adds a check and `.isSelected`, and the badge's VoiceOver label is "Status: <status>. Change status".
- Commands: run from the repository root of your worktree. Package tests: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:<Target>)`; app and UI tests: `xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -collect-test-diagnostics never`. Every command pipes through `grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'` (plan 2's filter plus `^macro expansion `, so errors inside `#expect` expansions show). If `xcodebuild` has printed its result but does not exit within a minute, stop it with Ctrl-C; the printed result stands. A package run that prints nothing for 15 minutes: stop it, run `xcrun simctl shutdown all`, and retry once.
- Local snapshot images: a task that changes a screen deletes that suite's local images before its green run (the step names the folder), so the first run records them and fails with `No reference was found on disk. Automatically recorded snapshot: …`, and the second run passes. Never stage `__Snapshots__`; Task 7 re-records every baseline on CI.
- Git: work on branch `feat/ios-library-search` in a worktree created from `main` once plan 2's `feat/ios-add-by-id` is merged (until then, from `feat/ios-add-by-id`): `git worktree add -b feat/ios-library-search ../Hashiya-ios-library-search main`. Never commit to `main`; never stage `.idea/`. Every `git add` lists explicit paths. Commit messages use `feat:`/`test:`/`docs:`/`ci:` and contain no AI or Claude attribution (no trailers, links or credits), nor do code comments, docs or PR text.

## Review Focus

1. **Installing this version over a plan-2 build** → every saved paper is still there, marked To read, and immediately findable by title, author (in position order), abstract, venue and Arabic words with tashkeel. Pinned by `MigrationTests.migratingKeepsEveryPaperAsToReadAndIndexesItWithItsAuthorsInOrder`, `theMigratedLibraryIsListedNewestFirstAndSearchable` and `openingAVersion1FileMigratesItInPlace` (Task 2), plus the in-place upgrade check in Task 7.
2. **Typing search syntax into the Library — `"`, `C++`, `title:guide`, `-x`, `NEAR/2`, `a AND`, `*`, `^x`** → never an SQLite error, a crash or an empty screen; quotes and stars are ignored (`"templates` and `*` still find the paper). Pinned by `FtsQueryTests.eachWordBecomesAQuotedPrefixTerm`, `blankOrPunctuationOnlyMeansNoSearch` and `GRDBLibraryRepositoryTests.searchTextWithFTSSyntaxNeverFails`, `quotesAndStarsAreIgnored` (Task 3), which run each input through real SQLite.
3. **Arabic and accented searches** → `التَّعلُّم` finds `التعلم`, `اساسيات` finds `أساسيات`, `١٩`, `۱۹` and `19` find each other, `schrodinger` finds `Schrödinger`, and a Turkish-locale device still lowercases `I` to `i`. Pinned by `SearchableTextTests.searchableTextFoldsCaseAccentsMarksLetterFormsAndDigits` (Task 1) and `GRDBLibraryRepositoryTests.searchIgnoresAccentsTashkeelAndAlefForms`, `searchMatchesArabicIndicAndASCIIDigitsEitherWay` (Task 3).
4. **Removing the last paper while searching, Undo, or clearing the search just as the 300 ms pause ends** → Empty (not "No papers match"), never a flash of No papers match, and a cleared search stays cleared. Pinned by `GRDBLibraryRepositoryTests.removingTheOnlyPaperNeverEmitsASnapshotWhosePapersAndCountsDisagree` (Task 3) and `LibraryViewModelTests.removingTheLastPaperDuringASearchShowsEmpty`, `removingAndRestoringTheOnlyPaperNeverShowsNoMatches`, `clearingRightAfterThePauseEndsKeepsTheSearchCleared` (Task 5).
5. **Changing a status** → tapping a row's badge opens its menu (never the preview) with the current status checked; the chip counts update at once; a paper moved out of the selected chip leaves the list but its open preview stays with the new status; Undo after removing a Reading paper brings it back as Reading, in place. Pinned by `LibraryFlowTests.testChangingAStatusFiltersAndSearchesTheLibrary` (Task 6), `LibraryViewModelTests.aStatusChangeOutOfTheChipKeepsThePreviewOpen`, `undoRestoresThePaperInPlaceWithItsStatus` (Task 5) and `GRDBLibraryRepositoryTests.removeThenRestoreReturnsThePaperToItsPositionWithItsStatus` (Task 3).

---

## File Structure

```
ios/HashiyaKit/Sources/HashiyaModel/ReadingStatus.swift, SearchableText.swift                         (Task 1)
ios/HashiyaKit/Package.swift                                                                          HashiyaDatabase → HashiyaModel (Task 2); HashiyaDataTests → GRDB (Task 3)
ios/HashiyaKit/Sources/HashiyaDatabase/HashiyaDatabase.swift, Records.swift, PaperStore.swift          migration v2, PaperSearchRow, LibraryRows (Task 2)
ios/HashiyaKit/Sources/HashiyaData/Search/FtsQuery.swift, ReadingStatusMapping.swift,
  PaperMapping.swift, LibraryRepository.swift                                                         (Task 3; minimal LibraryRepository edits in Task 2)
ios/HashiyaKit/Sources/HashiyaTesting/FakeLibraryRepository.swift                                     (Task 3)
ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/ReadingStatusSelector.swift,
  PaperPreviewContent.swift, Resources/Localizable.xcstrings                                          (Task 4)
ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift                                          (Task 5; a one-line edit in Task 3)
ios/HashiyaKit/Sources/FeatureLibrary/L10n.swift, LibraryView.swift, LibraryFilterChips.swift,
  ReadingStatusBadge.swift, Resources/Localizable.xcstrings                                           (Task 6; LibraryView edits in Task 5)
ios/HashiyaUITests/LibraryFlowTests.swift                                                             (Task 6)
ios/README.md, README.md                                                                              (Task 7)
```

## Where this plan departs from the spec (and why)

- **`PaperStore.observeLibrary(match:status:) -> AsyncStream<LibraryRows>`** (spec §5.3/§7: the repository runs `ValueObservation.tracking { PaperStore.librarySnapshot(db, …) }` itself). `HashiyaData` doesn't import GRDB (plan 1's rule), so the observation lives in `PaperStore` next to the others and hands back `LibraryRows` (`papers: [PaperWithAuthors]`, `statusCounts: [String: Int]`, `total: Int`); `PaperStore.librarySnapshot(_:match:status:)` is still the single read the spec describes, and the repository maps the rows to `LibrarySnapshot`.
- **Counts use the locale's digits, which are Latin for `Locale(identifier: "ar")` on iOS 18** (spec §1/§8.2: "قيد القراءة (٣)"). Chips format counts with `PaperFormat.number` like every other count in the app; iOS's plain Arabic locale gives "قيد القراءة (3)" (verified in `LibraryStringsTests.chipsShowTheirCount`), and Arabic-Indic digits appear where the user's region uses them (e.g. Arabic (Egypt)). The parentheses are kept either way. Arabic formatting also wraps each argument in U+2068 … U+2069.
- **The badge's open menu is checked by an XCUITest, not a snapshot** (spec §11 allowed this): a SwiftUI `Menu` opens a system popup in its own window, outside the hosted view that swift-snapshot-testing 1.19.6 renders. `LibrarySnapshotTests.statusBadges` captures the three badge styles; `LibraryFlowTests.testChangingAStatusFiltersAndSearchesTheLibrary` checks that the menu lists To read, Reading and Read as buttons with only the current one selected (verified: the `Picker` inside the `Menu` exposes the checked item as `isSelected`), that tapping the badge opens no preview, and picks Reading.
- **The badge pill is a continuous rounded rectangle (radius 11), not a `Capsule`**: a `Capsule`'s 1 pt outline rendered with seams at its ends in snapshots; the fill badges look the same either way.
- **Chips are drawn by `LibraryFilterChips` in `FeatureLibrary`**, repeating the look of Search's internal `ChipLabel` (features can't import each other, and moving it into the design system would change Search's baselines for no user-visible reason).
- **`PaperPreviewContent`'s selector sits 16 pt below the scrolling content and 16 pt above the buttons** (4 pt of its own plus the button row's 12 pt top padding).
- **Status restoration uses `""` for All** (spec: "absent = All"): `@SceneStorage` needs a non-optional default here; any value other than `toRead`, `reading`, `read` restores All.
- **Extra API** the spec implies but doesn't name: `LibraryPaper: Identifiable` (`id` = OpenAlex ID); `PaperRecord.readingStatus` (default `"to_read"`); `PaperSearchRow` (plain struct, inserted with raw SQL), `PaperWithAuthors.searchRow`, `LibraryRows`; `PaperWithAuthors.asLibraryPaper()`, `Paper.asRecords(localID:savedAt:status:)`; `LibraryViewModel` members `updateText(_:)`, `submitNow()`, `setStatusFilter(_:)`, `clearSearchAndFilters()`, `restore(text:status:)`, `setStatus(of:to:)`, `papers`, `filter`, `isLoaded`, `storedStatus`, `queryKey`/`statusKey`, `init(library:sleep:)`, `LibraryMessage.statusUpdateFailed`; `FakeLibraryRepository(saved:statuses:)`, `.library`, `setFailStatusUpdates(_:)` (its search: every typed word must start a word of the paper, through `searchableText`); `ReadingStatus.storedValue`, `init(stored:)` and `ftsMatch` are internal and tested with `@testable import HashiyaData`, so `HashiyaDataTests` also depends on GRDB (for writing an unknown stored status in a test).
- **"The preview selector appears only with a status" is tested by laying the view out in a `UIWindow`** and checking which strings it looked up (hostless package tests have no accessibility tree); choosing a segment is tested through the selector's binding and end to end in `LibraryFlowTests.testThePreviewChangesTheStatusAndTheSearchKeyHidesTheKeyboard`.
- **A paper whose stored status is unknown is counted under To read but not listed under the To read chip** (spec §5.3's query filters `reading_status = :status`, as Android does). Only a hand-edited database can contain such a value.

---
### Task 1: `HashiyaModel` — `ReadingStatus`, `LibraryPaper` and `searchableText`

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaModel/ReadingStatus.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaModel/SearchableText.swift`
- Test: Create `ios/HashiyaKit/Tests/HashiyaModelTests/ReadingStatusTests.swift`; modify `ios/HashiyaKit/Tests/HashiyaModelTests/SearchableTextTests.swift`

**Interfaces:**
- Consumes: plan 1's `Paper`; plan 2's `withoutArabicMarks(_:)` (kept unchanged next to the new function).
- Produces (module `HashiyaModel`, all `public`):
  - `enum ReadingStatus: String, CaseIterable, Sendable { case toRead, reading, read }`
  - `struct LibraryPaper: Equatable, Hashable, Sendable, Identifiable { var paper: Paper; var status: ReadingStatus; var id: String { paper.openAlexID }; init(paper:status:) }`
  - `func searchableText(_ text: String) -> String`

The `searchableText` table is Android's `SearchableTextTest.kt` (spec §4) as one parameterised test.

- [ ] **Step 1: Write the failing tests**

`ios/HashiyaKit/Tests/HashiyaModelTests/SearchableTextTests.swift`:
```swift
import HashiyaModel
import Testing

struct SearchableTextTests {
    @Test(arguments: [
        ("التَّعلُّم", "التعلم"),
        ("التّعلمُ", "التعلم"),
        ("ـالتعلمـ", "التعلم"),
        ("Schrödinger", "Schrödinger"),
        ("أإآ", "أإآ"),
        ("Deep Learning", "Deep Learning"),
        ("١٩", "١٩"),
        ("", ""),
    ])
    func withoutArabicMarksDropsOnlyTashkeelAndTatweel(input: String, expected: String) {
        #expect(withoutArabicMarks(input) == expected)
    }

    /// Ported from Android's `SearchableTextTest.kt`.
    @Test(arguments: [
        ("Schrödinger", "schrodinger"),
        ("schrodinger", "schrodinger"),
        ("SCHRÖDINGER", "schrodinger"),
        ("Schro\u{0308}dinger", "schrodinger"),
        ("Café Naïve", "cafe naive"),
        ("التَّعلُّم", "التعلم"),
        ("التعلم", "التعلم"),
        ("اَلتَّعَلُّمُ", "التعلم"),
        ("العـــربية", "العربية"),
        ("أحمد", "احمد"),
        ("إسلام", "اسلام"),
        ("آية", "اية"),
        ("ٱلكتاب", "الكتاب"),
        ("مستشفى", "مستشفي"),
        ("مستشفي", "مستشفي"),
        ("تعلُّم الآلة Machine LEARNING", "تعلم الالة machine learning"),
        ("١٩٨٤", "1984"),
        ("۱۹۸۴", "1984"),
        ("كوفيد-١٩", "كوفيد-19"),
        ("", ""),
        ("-- !? ()", "-- !? ()"),
        // Lowercasing never depends on the device's locale: on a Turkish phone "I" still becomes "i".
        ("TITLE", "title"),
    ])
    func searchableTextFoldsCaseAccentsMarksLetterFormsAndDigits(input: String, expected: String) {
        #expect(searchableText(input) == expected)
    }
}
```

`ios/HashiyaKit/Tests/HashiyaModelTests/ReadingStatusTests.swift`:
```swift
import HashiyaModel
import Testing

struct ReadingStatusTests {
    @Test func theStatusesAreToReadReadingAndReadInThatOrder() {
        #expect(ReadingStatus.allCases == [.toRead, .reading, .read])
    }

    @Test func aLibraryPaperIsIdentifiedByItsPaper() {
        let paper = Paper(openAlexID: "W2626778328", title: "Attention Is All You Need")
        let saved = LibraryPaper(paper: paper, status: .reading)
        #expect(saved.id == "W2626778328")
        #expect(saved != LibraryPaper(paper: paper, status: .read))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:HashiyaModelTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL — `ReadingStatusTests.swift:6:17: error: cannot find 'ReadingStatus' in scope` (and `cannot find 'LibraryPaper' in scope`), then `** TEST FAILED **`.

- [ ] **Step 3: Implement**

`ios/HashiyaKit/Sources/HashiyaModel/ReadingStatus.swift`:
```swift
/// Where the user is with a saved paper. New saves start as `.toRead`.
public enum ReadingStatus: String, CaseIterable, Sendable {
    case toRead, reading, read
}

/// A saved paper with its reading status.
public struct LibraryPaper: Equatable, Hashable, Sendable, Identifiable {
    public var paper: Paper
    public var status: ReadingStatus

    /// The paper's OpenAlex ID.
    public var id: String { paper.openAlexID }

    public init(paper: Paper, status: ReadingStatus) {
        self.paper = paper
        self.status = status
    }
}
```

`SearchableText.swift` keeps `withoutArabicMarks` and gains `searchableText` (full file):

`ios/HashiyaKit/Sources/HashiyaModel/SearchableText.swift`:
```swift
/// The text without Arabic diacritics and tatweel; everything else as typed.
public func withoutArabicMarks(_ text: String) -> String {
    // Scalars, not Characters: a mark combines with its letter into one Character.
    String(String.UnicodeScalarView(text.unicodeScalars.filter { !isArabicMark($0.value) }))
}

/// Lowercased text with accents and marks removed, Arabic letter variants unified and digits in ASCII, for full-text search.
/// The library's search index and the queries typed into it both pass through here, so they always agree.
public func searchableText(_ text: String) -> String {
    var folded = String.UnicodeScalarView()
    // NFKD first: "ö" becomes "o" + U+0308 and "أ" becomes "ا" + U+0654, so the marks can be dropped on their own.
    for scalar in text.decomposedStringWithCompatibilityMapping.unicodeScalars {
        switch scalar.properties.generalCategory {
        case .nonspacingMark, .spacingMark, .enclosingMark:
            continue
        default:
            break
        }
        switch scalar.value {
        case 0x0640:
            // Tatweel.
            continue
        case 0x0623, 0x0625, 0x0622, 0x0671:
            // أ إ آ ٱ → ا
            folded.append("\u{0627}")
        case 0x0649:
            // ى → ي
            folded.append("\u{064A}")
        default:
            folded.append(asciiDigit(scalar) ?? scalar)
        }
    }
    // `lowercased()` uses Unicode's default mapping, never the device's locale (a Turkish phone still gives "title").
    return String(folded).lowercased()
}

/// The ASCII digit of a decimal digit (Arabic-Indic "١", Persian "۱", …); nil for anything else.
private func asciiDigit(_ scalar: Unicode.Scalar) -> Unicode.Scalar? {
    guard scalar.properties.numericType == .decimal, let value = scalar.properties.numericValue else { return nil }
    return Unicode.Scalar(UInt8(ascii: "0") + UInt8(value))
}

/// Tashkeel and Quranic marks (U+0610–U+061A, U+064B–U+065F, U+0670, U+06D6–U+06DC, U+06DF–U+06E4,
/// U+06E7–U+06E8, U+06EA–U+06ED) and tatweel (U+0640).
private func isArabicMark(_ value: UInt32) -> Bool {
    switch value {
    case 0x0610...0x061A, 0x064B...0x065F, 0x0670, 0x06D6...0x06DC, 0x06DF...0x06E4, 0x06E7...0x06E8, 0x06EA...0x06ED, 0x0640:
        true
    default:
        false
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:HashiyaModelTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: `✔ Test run with 30 tests in 7 suites passed` and `** TEST SUCCEEDED **`.

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaModel/ReadingStatus.swift ios/HashiyaKit/Sources/HashiyaModel/SearchableText.swift \
  ios/HashiyaKit/Tests/HashiyaModelTests/SearchableTextTests.swift ios/HashiyaKit/Tests/HashiyaModelTests/ReadingStatusTests.swift
git commit -m "feat: add iOS reading statuses and the library's search text normalization"
```

---
### Task 2: `HashiyaDatabase` — migration `v2`, the FTS4 index in every write, and the one-read library snapshot

**Files:**
- Modify: `ios/HashiyaKit/Package.swift` (`HashiyaDatabase` depends on `HashiyaModel`)
- Modify: `ios/HashiyaKit/Sources/HashiyaDatabase/HashiyaDatabase.swift`, `Records.swift`, `PaperStore.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift` (three lines, so the package still builds; Task 3 replaces the file)
- Test: Modify `ios/HashiyaKit/Tests/HashiyaDatabaseTests/MigrationTests.swift`, `PaperStoreTests.swift`

**Interfaces:**
- Consumes: Task 1's `searchableText`; plan 1–2's `HashiyaDatabase.migrator`, `openPool(at:)`, `openInMemory()`, `PaperStore(writer:)`, `PaperStore.open(at:)`, `observeSavedOpenAlexIDs()`, `notifyExternalChanges()`.
- Produces (module `HashiyaDatabase`, all `public`):
  - migration `"v2"` in `HashiyaDatabase.migrator` (`migrations == ["v1", "v2"]`)
  - `PaperRecord.readingStatus: String` (column `reading_status`; init parameter `readingStatus: String = "to_read"`)
  - `struct PaperSearchRow: Equatable, Sendable { var paperID, title, authors, abstract, venue: String; init(paperID:title:authors:abstract:venue:); static func make(paperID: String, title: String, authorNames: [String], abstract: String?, venue: String?) -> PaperSearchRow }`
  - `PaperWithAuthors.searchRow: PaperSearchRow`
  - `struct LibraryRows: Equatable, Sendable { var papers: [PaperWithAuthors]; var statusCounts: [String: Int]; var total: Int }`
  - `PaperStore.observeLibrary(match: String?, status: String?) -> AsyncStream<LibraryRows>`; `static func librarySnapshot(_ db: Database, match: String?, status: String?) throws -> LibraryRows`; `@discardableResult func insert(paper: PaperRecord, authors: [PaperAuthorRecord], search: PaperSearchRow) async throws -> Bool` (precondition `search.paperID == paper.id`); `deleteByOpenAlexID(_:)` also deletes the search row; `@discardableResult func setStatus(openAlexID: String, status: String) async throws -> Int`
  - removed: `PaperStore.observeSavedPapers()` and the two-argument `insert(paper:authors:)`

The migration test builds a real `v1` database with `migrate(_:upTo: "v1")` (GRDB 7.11.1 API) and Android's `MigrationTest` fixture: paper `a` whose author at position 1 is inserted before position 0, and Arabic paper `b` with tashkeel and NULL abstract and venue. GRDB 7.11.1's FTS4 builder also offers `notIndexed()` and `.unicode61()`, but the raw SQL makes `notindexed=paper_id` certain and is what the schema test asserts.

- [ ] **Step 1: Write the failing tests**

`ios/HashiyaKit/Tests/HashiyaDatabaseTests/MigrationTests.swift`:
```swift
import Foundation
import GRDB
import HashiyaDatabase
import Testing

struct MigrationTests {
    /// A database migrated only to `v1`, as plans 1 and 2 left every install.
    private func version1() throws -> DatabaseQueue {
        let queue = try DatabaseQueue()
        try HashiyaDatabase.migrator.migrate(queue, upTo: "v1")
        return queue
    }

    /// Android's `MigrationTest` fixture: one paper whose second author was inserted first, and an Arabic paper with
    /// tashkeel and no authors, abstract or venue.
    private func insertVersion1Fixture(into queue: DatabaseQueue) throws {
        try queue.write { db in
            try db.execute(sql: """
                INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, oa_pdf_url, saved_at)
                VALUES ('a', 'W1', '10.48550/arxiv.1706.03762', 'Attention Is All You Need', 2017,
                        'Neural Information Processing Systems', 'The dominant sequence transduction models', 128412, 1, NULL, 100);
                INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 1, 'Noam Shazeer', NULL);
                INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 0, 'Ashish Vaswani', NULL);
                INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, oa_pdf_url, saved_at)
                VALUES ('b', 'W2', NULL, 'تطبيقات التَّعلُّم العميق', NULL, NULL, NULL, 0, 0, NULL, 200);
                """)
        }
    }

    /// The first value of `stream`.
    private func first<T: Sendable>(_ stream: AsyncStream<T>) async -> T? {
        for await value in stream {
            return value
        }
        return nil
    }

    @Test func theMigrationsAreV1ThenV2() {
        #expect(HashiyaDatabase.migrator.migrations == ["v1", "v2"])
    }

    @Test func v1CreatesAndroidsVersion1Schema() throws {
        let queue = try version1()
        try queue.read { db in
            let papers = try db.columns(in: "papers")
            #expect(papers.map(\.name) == [
                "id", "open_alex_id", "doi", "title", "year", "venue", "abstract",
                "citation_count", "is_open_access", "oa_pdf_url", "saved_at",
            ])
            #expect(papers.map(\.type) == [
                "TEXT", "TEXT", "TEXT", "TEXT", "INTEGER", "TEXT", "TEXT", "INTEGER", "INTEGER", "TEXT", "INTEGER",
            ])
            #expect(papers.filter(\.isNotNull).map(\.name) == ["id", "title", "citation_count", "is_open_access", "saved_at"])
            #expect(try db.primaryKey("papers").columns == ["id"])

            let indexes = try db.indexes(on: "papers").filter { $0.name.hasPrefix("index_") }.sorted { $0.name < $1.name }
            #expect(indexes.map(\.name) == ["index_papers_doi", "index_papers_open_alex_id"])
            #expect(indexes.map(\.isUnique) == [false, true])

            let authors = try db.columns(in: "paper_authors")
            #expect(authors.map(\.name) == ["paper_id", "position", "name", "open_alex_author_id"])
            #expect(authors.filter(\.isNotNull).map(\.name) == ["paper_id", "position", "name"])
            #expect(try db.primaryKey("paper_authors").columns == ["paper_id", "position"])

            let foreignKeys = try db.foreignKeys(on: "paper_authors")
            #expect(foreignKeys.map(\.destinationTable) == ["papers"])
            #expect(foreignKeys.first?.mapping.map(\.origin) == ["paper_id"])
            let onDelete = try String.fetchOne(db, sql: "SELECT on_delete FROM pragma_foreign_key_list('paper_authors')")
            #expect(onDelete == "CASCADE")
            #expect(try !db.tableExists("paper_search"))
        }
    }

    @Test func v2AddsTheReadingStatusAndTheSearchIndex() throws {
        let (columns, sql) = try HashiyaDatabase.openInMemory().read { db in
            (try db.columns(in: "papers"), try String.fetchOne(db, sql: "SELECT sql FROM sqlite_master WHERE name = 'paper_search'"))
        }
        let status = try #require(columns.last)
        #expect(status.name == "reading_status")
        #expect(status.type == "TEXT")
        #expect(status.isNotNull)
        #expect(status.defaultValueSQL == "'to_read'")
        #expect(sql == "CREATE VIRTUAL TABLE paper_search USING fts4(paper_id, title, authors, abstract, venue, tokenize=unicode61, notindexed=paper_id)")
    }

    @Test func migratingKeepsEveryPaperAsToReadAndIndexesItWithItsAuthorsInOrder() throws {
        let queue = try version1()
        try insertVersion1Fixture(into: queue)

        try HashiyaDatabase.migrator.migrate(queue)

        let (statuses, authors, index, rest) = try queue.read { db in
            (
                try String.fetchAll(db, sql: "SELECT id || ':' || reading_status FROM papers ORDER BY id"),
                try String.fetchAll(db, sql: "SELECT name FROM paper_authors ORDER BY position"),
                try String.fetchAll(db, sql: "SELECT paper_id || ':' || title || ':' || authors FROM paper_search ORDER BY paper_id"),
                try String.fetchAll(db, sql: "SELECT abstract || '|' || venue FROM paper_search ORDER BY paper_id")
            )
        }
        #expect(statuses == ["a:to_read", "b:to_read"])
        #expect(authors == ["Ashish Vaswani", "Noam Shazeer"])
        #expect(index == ["a:attention is all you need:ashish vaswani noam shazeer", "b:تطبيقات التعلم العميق:"])
        #expect(rest == ["the dominant sequence transduction models|neural information processing systems", "|"])
    }

    @Test func theMigratedLibraryIsListedNewestFirstAndSearchable() async throws {
        let queue = try version1()
        try insertVersion1Fixture(into: queue)
        try HashiyaDatabase.migrator.migrate(queue)
        let store = PaperStore(writer: queue)

        func ids(_ match: String?) async -> [String]? {
            await first(store.observeLibrary(match: match, status: nil))?.papers.map(\.paper.id)
        }

        #expect(await ids(nil) == ["b", "a"])
        #expect(await ids("\"attention*\"") == ["a"])
        #expect(await ids("\"shazeer*\"") == ["a"])
        #expect(await ids("\"transduction*\"") == ["a"])
        #expect(await ids("\"neural*\" \"processing*\"") == ["a"])
        #expect(await ids("\"التعلم*\"") == ["b"])
        #expect(await first(store.observeLibrary(match: nil, status: nil))?.papers.first?.paper.readingStatus == "to_read")
    }

    @Test func foreignKeysAreEnforced() throws {
        let db = try HashiyaDatabase.openInMemory()
        let enabled = try db.read { db in try Bool.fetchOne(db, sql: "PRAGMA foreign_keys") }
        #expect(enabled == true)
    }

    @Test func reopeningAFileKeepsTheLibrary() async throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appending(path: "hashiya.sqlite")

        let first = try HashiyaDatabase.openPool(at: url)
        try await first.write { db in
            try db.execute(sql: """
                INSERT INTO papers (id, title, citation_count, is_open_access, saved_at)
                VALUES ('local-1', 'Kept', 0, 0, 1)
                """)
        }
        try first.close()

        let second = try HashiyaDatabase.openPool(at: url)
        let titles = try await second.read { db in try String.fetchAll(db, sql: "SELECT title FROM papers") }
        #expect(titles == ["Kept"])
        #expect(try await second.read { db in try HashiyaDatabase.migrator.appliedMigrations(db) } == ["v1", "v2"])
        let journalMode = try await second.read { db in try String.fetchOne(db, sql: "PRAGMA journal_mode") }
        #expect(journalMode == "wal")
    }

    /// A plan 2 install opened by this version: the file is migrated in place and nothing is lost.
    @Test func openingAVersion1FileMigratesItInPlace() async throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appending(path: "hashiya.sqlite")
        let old = try DatabaseQueue(path: url.path(percentEncoded: false))
        try HashiyaDatabase.migrator.migrate(old, upTo: "v1")
        try insertVersion1Fixture(into: old)
        try old.close()

        let store = try PaperStore.open(at: url)

        let library = await first(store.observeLibrary(match: "\"vaswani*\"", status: "to_read"))
        #expect(library?.papers.map(\.paper.id) == ["a"])
        #expect(library?.total == 2)
    }
}
```

`ios/HashiyaKit/Tests/HashiyaDatabaseTests/PaperStoreTests.swift`:
```swift
import GRDB
import HashiyaDatabase
import Testing

struct PaperStoreTests {
    private let queue: DatabaseQueue
    private let store: PaperStore

    init() throws {
        queue = try HashiyaDatabase.openInMemory()
        store = PaperStore(writer: queue)
    }

    private func paper(
        _ n: Int,
        openAlexID: String? = nil,
        doi: String? = nil,
        title: String? = nil,
        abstract: String? = nil,
        venue: String? = "Venue",
        status: String = "to_read",
        savedAt: Int64
    ) -> PaperRecord {
        PaperRecord(
            id: "local-\(n)",
            openAlexID: openAlexID ?? "W\(n)",
            doi: doi,
            title: title ?? "Paper \(n)",
            year: 2020,
            venue: venue,
            abstract: abstract,
            citationCount: n,
            isOpenAccess: n.isMultiple(of: 2),
            oaPDFURL: nil,
            savedAt: savedAt,
            readingStatus: status
        )
    }

    private func authors(of paper: PaperRecord, _ names: [String]) -> [PaperAuthorRecord] {
        names.enumerated().map { PaperAuthorRecord(paperID: paper.id, position: $0.offset, name: $0.element, openAlexAuthorID: nil) }
    }

    /// Saves `paper` with `names` and its search row, as the repository does.
    @discardableResult
    private func save(_ paper: PaperRecord, _ names: String...) async throws -> Bool {
        let saved = PaperWithAuthors(paper: paper, authors: authors(of: paper, names))
        return try await store.insert(paper: saved.paper, authors: saved.authors, search: saved.searchRow)
    }

    /// The first value of `stream` that satisfies `predicate`.
    private func value<T: Sendable>(of stream: AsyncStream<T>, where predicate: (T) -> Bool = { _ in true }) async -> T? {
        for await value in stream where predicate(value) {
            return value
        }
        return nil
    }

    private func library(match: String? = nil, status: String? = nil) async -> LibraryRows? {
        await value(of: store.observeLibrary(match: match, status: status))
    }

    private func ids(match: String? = nil, status: String? = nil) async -> [String]? {
        await library(match: match, status: status)?.papers.map(\.paper.id)
    }

    private func count(_ table: String) throws -> Int? {
        try queue.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(table)") }
    }

    @Test func savedPapersAreNewestFirstWithAuthorsInOrder() async throws {
        try await save(paper(1, savedAt: 1_000), "Ada", "Grace")
        try await save(paper(2, savedAt: 2_000), "Zed", "Amy", "Bob")

        let saved = await library()?.papers
        #expect(saved?.map(\.paper.id) == ["local-2", "local-1"])
        #expect(saved?.first?.authors.map(\.name) == ["Zed", "Amy", "Bob"])
        #expect(saved?.first?.authors.map(\.position) == [0, 1, 2])
        #expect(saved?.last?.authors.map(\.name) == ["Ada", "Grace"])
    }

    @Test func savedPapersRoundTripEveryColumn() async throws {
        let record = PaperRecord(
            id: "local-9", openAlexID: "W9", doi: "10.1000/xyz", title: "Full", year: 2017, venue: "NeurIPS",
            abstract: "Text", citationCount: 128_412, isOpenAccess: true, oaPDFURL: "https://arxiv.org/pdf/1706.03762",
            savedAt: 1_727_000_000_000, readingStatus: "reading"
        )
        try await save(record)

        #expect(await library()?.papers.first?.paper == record)
    }

    @Test func savingAgainIsANoOp() async throws {
        #expect(try await save(paper(1, savedAt: 1_000), "Ada"))

        #expect(try await save(paper(2, openAlexID: "W1", title: "Changed", savedAt: 2_000), "Someone") == false)
        #expect(try await save(paper(1, openAlexID: "W3", title: "Changed", savedAt: 3_000)) == false)

        let saved = await library()?.papers
        #expect(saved?.map(\.paper.title) == ["Paper 1"])
        #expect(saved?.first?.authors.map(\.name) == ["Ada"])
        #expect(try count("paper_authors") == 1)
        #expect(try count("paper_search") == 1)
    }

    @Test func twoWorksWithOneDOIBothSave() async throws {
        #expect(try await save(paper(1, doi: "10.1000/xyz", savedAt: 1)))
        #expect(try await save(paper(2, doi: "10.1000/xyz", savedAt: 2)))

        #expect(await library()?.papers.count == 2)
    }

    @Test func deleteReturnsTheRowAndRemovesItsAuthorsAndSearchRow() async throws {
        let record = paper(1, savedAt: 1_000)
        try await save(record, "Ada", "Grace")

        let deleted = try await store.deleteByOpenAlexID("W1")
        #expect(deleted?.paper == record)
        #expect(deleted?.authors.map(\.name) == ["Ada", "Grace"])
        #expect(await library()?.papers.isEmpty == true)
        #expect(try count("paper_authors") == 0)
        #expect(try count("paper_search") == 0)
    }

    @Test func reinsertingADeletedRowKeepsItsIDSavedAtStatusAndSearchRow() async throws {
        for (n, savedAt) in [(1, 1_000), (2, 2_000), (3, 3_000)] {
            try await save(paper(n, status: n == 2 ? "reading" : "to_read", savedAt: Int64(savedAt)), "Author \(n)")
        }

        let deleted = try #require(try await store.deleteByOpenAlexID("W2"))
        try await store.insert(paper: deleted.paper, authors: deleted.authors, search: deleted.searchRow)

        let saved = await library()?.papers
        #expect(saved?.map(\.paper.id) == ["local-3", "local-2", "local-1"])
        #expect(saved?[1].paper.savedAt == 2_000)
        #expect(saved?[1].paper.readingStatus == "reading")
        #expect(saved?[1].authors.map(\.name) == ["Author 2"])
        #expect(await ids(match: "\"author*\" \"2*\"") == ["local-2"])
    }

    @Test func deletingAnUnknownPaperReturnsNil() async throws {
        #expect(try await store.deleteByOpenAlexID("W404") == nil)
    }

    @Test func savedIDsFollowSavesAndDeletes() async throws {
        var iterator = store.observeSavedOpenAlexIDs().makeAsyncIterator()
        #expect(await iterator.next() == [])

        try await save(paper(1, savedAt: 1))
        var noOpenAlexID = paper(2, savedAt: 2)
        noOpenAlexID.openAlexID = nil
        try await save(noOpenAlexID)
        #expect(await value(of: store.observeSavedOpenAlexIDs(), where: { $0.count == 1 }) == ["W1"])

        _ = try await store.deleteByOpenAlexID("W1")
        #expect(await value(of: store.observeSavedOpenAlexIDs(), where: { $0.isEmpty }) == [])
    }

    @Test func searchFindsTheTitleAuthorsAbstractAndVenue() async throws {
        try await save(
            paper(1, title: "Attention Is All You Need", abstract: "Sequence transduction", venue: "NeurIPS", savedAt: 100),
            "Ashish Vaswani"
        )
        try await save(paper(2, title: "Deep Residual Learning", abstract: "Image recognition", venue: "CVPR", savedAt: 200), "Kaiming He")

        #expect(await ids(match: "\"attention*\"") == ["local-1"])
        #expect(await ids(match: "\"vaswani*\"") == ["local-1"])
        #expect(await ids(match: "\"recognition*\"") == ["local-2"])
        #expect(await ids(match: "\"cvpr*\"") == ["local-2"])
        #expect(await ids(match: "\"transformer*\"") == [])
    }

    @Test func prefixesMatchLongerWordsAndEveryWordMustMatch() async throws {
        try await save(paper(1, title: "Transformers for language", savedAt: 100))
        try await save(paper(2, title: "Transformers for images", savedAt: 200))

        #expect(await ids(match: "\"transf*\"") == ["local-2", "local-1"])
        #expect(await ids(match: "\"transf*\" \"lang*\"") == ["local-1"])
    }

    /// The local id is stored in the index but not indexed, so it never matches a search.
    @Test func theLocalIDIsNotSearchable() async throws {
        var record = paper(1, title: "Deep learning", savedAt: 100)
        record.id = "zzlocalid"
        try await save(record)

        #expect(await ids(match: "\"zzlocalid*\"") == [])
        #expect(await ids(match: "\"deep*\"") == ["zzlocalid"])
    }

    @Test func searchCombinesWithStatus() async throws {
        try await save(paper(1, title: "Transformers one", status: "reading", savedAt: 100))
        try await save(paper(2, title: "Transformers two", savedAt: 200))
        try await save(paper(3, title: "Convolutions", status: "reading", savedAt: 300))

        #expect(await ids(status: "reading") == ["local-3", "local-1"])
        #expect(await ids(match: "\"transf*\"", status: "reading") == ["local-1"])
    }

    @Test func countsFollowTheSearchAndTheTotalIgnoresIt() async throws {
        try await save(paper(1, title: "Transformers one", status: "reading", savedAt: 100))
        try await save(paper(2, title: "Transformers two", savedAt: 200))
        try await save(paper(3, title: "Convolutions", status: "reading", savedAt: 300))

        #expect(await library()?.statusCounts == ["reading": 2, "to_read": 1])
        #expect(await library(match: "\"transf*\"")?.statusCounts == ["reading": 1, "to_read": 1])
        #expect(await library(match: "\"missing*\"")?.statusCounts == [:])
        #expect(await library(match: "\"missing*\"", status: "read")?.total == 3)
    }

    @Test func settingTheStatusKeepsTheOrderAndTheIndex() async throws {
        try await save(paper(1, title: "Transformers one", savedAt: 100))
        try await save(paper(2, title: "Transformers two", savedAt: 200))

        #expect(try await store.setStatus(openAlexID: "W1", status: "read") == 1)

        #expect(await ids() == ["local-2", "local-1"])
        #expect(await library()?.papers.last?.paper.readingStatus == "read")
        #expect(await ids(match: "\"transf*\"") == ["local-2", "local-1"])
        #expect(try count("paper_search") == 2)
    }

    @Test func settingTheStatusOfAnUnknownPaperChangesNothing() async throws {
        #expect(try await store.setStatus(openAlexID: "W404", status: "read") == 0)
    }

    @Test func aSearchRowMustBelongToItsPaper() {
        let search = PaperSearchRow.make(paperID: "local-1", title: "Café", authorNames: ["Ada", "Grace"], abstract: nil, venue: "NeurIPS")
        #expect(search == PaperSearchRow(paperID: "local-1", title: "cafe", authors: "ada grace", abstract: "", venue: "neurips"))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:HashiyaDatabaseTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL — `PaperStoreTests.swift:59:80: error: cannot find type 'LibraryRows' in scope`, `extra argument 'readingStatus' in call`, `extra argument 'search' in call`, `value of type 'PaperStore' has no member 'observeLibrary'`, then `** TEST FAILED **`.

- [ ] **Step 3: Implement**

`HashiyaDatabase` now depends on `HashiyaModel`:

```bash
python3 - <<'EOF'
import pathlib
p = pathlib.Path("ios/HashiyaKit/Package.swift")
s = p.read_text()
old = '.target(name: "HashiyaDatabase", dependencies: [grdb]),'
assert s.count(old) == 1
p.write_text(s.replace(old, '.target(name: "HashiyaDatabase", dependencies: ["HashiyaModel", grdb]),'))
EOF
```

`ios/HashiyaKit/Sources/HashiyaDatabase/HashiyaDatabase.swift`:
```swift
import Foundation
import GRDB

/// Opens the library database and migrates it. There is no destructive fallback: a failing
/// migration throws and never deletes the user's library.
public enum HashiyaDatabase {
    /// The App Group shared with the Share Extension.
    public static let appGroup = "group.com.etatech.hashiya"
    /// The library's file name in the App Group container.
    public static let fileName = "hashiya.sqlite"

    public enum OpenError: Error {
        case appGroupUnavailable
        /// The file coordinator neither opened the file nor reported an error.
        case coordinationFailed
    }

    /// `v1`: Android's Room version 1 schema. `v2`: Android's version 2 — the reading status and the search index.
    public static var migrator: DatabaseMigrator {
        var migrator = DatabaseMigrator()
        migrator.registerMigration("v1") { db in
            try db.execute(sql: """
                CREATE TABLE papers (
                  id TEXT NOT NULL PRIMARY KEY,
                  open_alex_id TEXT,
                  doi TEXT,
                  title TEXT NOT NULL,
                  year INTEGER,
                  venue TEXT,
                  abstract TEXT,
                  citation_count INTEGER NOT NULL,
                  is_open_access INTEGER NOT NULL,
                  oa_pdf_url TEXT,
                  saved_at INTEGER NOT NULL
                );
                CREATE UNIQUE INDEX index_papers_open_alex_id ON papers(open_alex_id);
                CREATE INDEX index_papers_doi ON papers(doi);
                CREATE TABLE paper_authors (
                  paper_id TEXT NOT NULL REFERENCES papers(id) ON DELETE CASCADE,
                  position INTEGER NOT NULL,
                  name TEXT NOT NULL,
                  open_alex_author_id TEXT,
                  PRIMARY KEY (paper_id, position)
                );
                """)
        }
        migrator.registerMigration("v2") { db in
            // Raw SQL rather than GRDB's FTS4 builder, so `notindexed=paper_id` is certain: a search never matches a local id.
            try db.execute(sql: """
                ALTER TABLE papers ADD COLUMN reading_status TEXT NOT NULL DEFAULT 'to_read';
                CREATE VIRTUAL TABLE paper_search USING fts4(paper_id, title, authors, abstract, venue, tokenize=unicode61, notindexed=paper_id);
                """)
            // Index every saved paper, its authors in position order, exactly as a new save would.
            var authorNames: [String: [String]] = [:]
            for row in try Row.fetchAll(db, sql: "SELECT paper_id, name FROM paper_authors ORDER BY paper_id, position") {
                authorNames[row["paper_id"], default: []].append(row["name"])
            }
            for row in try Row.fetchAll(db, sql: "SELECT id, title, abstract, venue FROM papers") {
                let id: String = row["id"]
                try PaperSearchRow.make(
                    paperID: id,
                    title: row["title"],
                    authorNames: authorNames[id] ?? [],
                    abstract: row["abstract"],
                    venue: row["venue"]
                ).insert(db)
            }
        }
        return migrator
    }

    /// `<App Group container>/Library/Application Support/<fileName>`, creating the directory.
    public static func sharedDatabaseURL(appGroup: String = appGroup, fileName: String = fileName) throws -> URL {
        guard let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup) else {
            throw OpenError.appGroupUnavailable
        }
        let directory = container.appending(path: "Library/Application Support", directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory.appending(path: fileName)
    }

    /// A WAL database pool at `url`, migrated to the latest version. The app and the Share Extension both open
    /// the App Group file this way (GRDB's "Sharing a Database" guide): the opening is coordinated with other
    /// processes, writes wait up to 5 s for another process's write, and the pool stops taking locks while
    /// the process is suspended (`suspend()`).
    public static func openPool(at url: URL) throws -> DatabasePool {
        var configuration = configuration()
        configuration.busyMode = .timeout(5)
        configuration.observesSuspensionNotifications = true

        var coordinationError: NSError?
        var result: Result<DatabasePool, any Error> = .failure(OpenError.coordinationFailed)
        NSFileCoordinator(filePresenter: nil).coordinate(writingItemAt: url, options: .forMerging, error: &coordinationError) { url in
            result = Result {
                let pool = try DatabasePool(path: url.path(percentEncoded: false), configuration: configuration)
                try migrator.migrate(pool)
                return pool
            }
        }
        if let coordinationError { throw coordinationError }
        return try result.get()
    }

    /// Deletes the database at `url` with its `-wal` and `-shm` files; missing files are fine. Close it first.
    public static func removeDatabase(at url: URL) throws {
        for suffix in ["", "-wal", "-shm"] {
            let file = URL(filePath: url.path(percentEncoded: false) + suffix)
            do {
                try FileManager.default.removeItem(at: file)
            } catch CocoaError.fileNoSuchFile {
                continue
            }
        }
    }

    /// Before the process is suspended: pools stop taking new locks, so iOS never kills it for holding one
    /// on a shared file (0xDEAD10CC). Writes fail until `resume()`.
    public static func suspend() {
        NotificationCenter.default.post(name: Database.suspendNotification, object: nil)
    }

    /// Back in the foreground: pools may take locks again.
    public static func resume() {
        NotificationCenter.default.post(name: Database.resumeNotification, object: nil)
    }

    /// A migrated in-memory database, for tests and UI-test launches.
    public static func openInMemory() throws -> DatabaseQueue {
        let queue = try DatabaseQueue(configuration: configuration())
        try migrator.migrate(queue)
        return queue
    }

    static func configuration() -> Configuration {
        var configuration = Configuration()
        configuration.foreignKeysEnabled = true
        return configuration
    }
}
```

`ios/HashiyaKit/Sources/HashiyaDatabase/Records.swift`:
```swift
import GRDB
import HashiyaModel

/// A row of `papers`.
public struct PaperRecord: Codable, Equatable, Sendable, FetchableRecord, PersistableRecord {
    public static let databaseTableName = "papers"

    /// Local UUID, lowercase.
    public var id: String
    public var openAlexID: String?
    public var doi: String?
    public var title: String
    public var year: Int?
    public var venue: String?
    public var abstract: String?
    public var citationCount: Int
    public var isOpenAccess: Bool
    public var oaPDFURL: String?
    /// Epoch milliseconds; the library's sort key.
    public var savedAt: Int64
    /// `to_read`, `reading` or `read` (`HashiyaData` maps them to `ReadingStatus`).
    public var readingStatus: String

    public init(
        id: String,
        openAlexID: String?,
        doi: String?,
        title: String,
        year: Int?,
        venue: String?,
        abstract: String?,
        citationCount: Int,
        isOpenAccess: Bool,
        oaPDFURL: String?,
        savedAt: Int64,
        readingStatus: String = "to_read"
    ) {
        self.id = id
        self.openAlexID = openAlexID
        self.doi = doi
        self.title = title
        self.year = year
        self.venue = venue
        self.abstract = abstract
        self.citationCount = citationCount
        self.isOpenAccess = isOpenAccess
        self.oaPDFURL = oaPDFURL
        self.savedAt = savedAt
        self.readingStatus = readingStatus
    }

    enum CodingKeys: String, CodingKey {
        case id, doi, title, year, venue, abstract
        case openAlexID = "open_alex_id"
        case citationCount = "citation_count"
        case isOpenAccess = "is_open_access"
        case oaPDFURL = "oa_pdf_url"
        case savedAt = "saved_at"
        case readingStatus = "reading_status"
    }
}

/// A row of `paper_authors`.
public struct PaperAuthorRecord: Codable, Equatable, Sendable, FetchableRecord, PersistableRecord {
    public static let databaseTableName = "paper_authors"

    public var paperID: String
    /// Authorship order, from 0.
    public var position: Int
    public var name: String
    public var openAlexAuthorID: String?

    public init(paperID: String, position: Int, name: String, openAlexAuthorID: String?) {
        self.paperID = paperID
        self.position = position
        self.name = name
        self.openAlexAuthorID = openAlexAuthorID
    }

    enum CodingKeys: String, CodingKey {
        case position, name
        case paperID = "paper_id"
        case openAlexAuthorID = "open_alex_author_id"
    }
}

/// A row of the full-text index `paper_search`: one per saved paper, keyed by the paper's local id (stored, not
/// indexed). FTS rows don't cascade, so `PaperStore` writes and deletes them with their paper.
public struct PaperSearchRow: Equatable, Sendable {
    public var paperID: String
    public var title: String
    /// Author names joined with spaces.
    public var authors: String
    public var abstract: String
    public var venue: String

    public init(paperID: String, title: String, authors: String, abstract: String, venue: String) {
        self.paperID = paperID
        self.title = title
        self.authors = authors
        self.abstract = abstract
        self.venue = venue
    }

    /// The row for a paper, every column passed through `searchableText`. New saves and migration `v2` both use it.
    public static func make(paperID: String, title: String, authorNames: [String], abstract: String?, venue: String?) -> PaperSearchRow {
        PaperSearchRow(
            paperID: paperID,
            title: searchableText(title),
            authors: searchableText(authorNames.joined(separator: " ")),
            abstract: searchableText(abstract ?? ""),
            venue: searchableText(venue ?? "")
        )
    }

    func insert(_ db: Database) throws {
        try db.execute(
            sql: "INSERT INTO paper_search (paper_id, title, authors, abstract, venue) VALUES (?, ?, ?, ?, ?)",
            arguments: [paperID, title, authors, abstract, venue]
        )
    }
}

/// A saved paper with its authors, sorted by position.
public struct PaperWithAuthors: Equatable, Sendable {
    public var paper: PaperRecord
    public var authors: [PaperAuthorRecord]

    public init(paper: PaperRecord, authors: [PaperAuthorRecord]) {
        self.paper = paper
        self.authors = authors
    }

    /// This paper's row in the search index.
    public var searchRow: PaperSearchRow {
        PaperSearchRow.make(
            paperID: paper.id,
            title: paper.title,
            authorNames: authors.sorted { $0.position < $1.position }.map(\.name),
            abstract: paper.abstract,
            venue: paper.venue
        )
    }
}

/// One consistent read of the library for a search and a status.
public struct LibraryRows: Equatable, Sendable {
    /// Papers matching the search and the status, newest saved first.
    public var papers: [PaperWithAuthors]
    /// Papers matching the search per stored status; statuses with none are absent.
    public var statusCounts: [String: Int]
    /// Every saved paper, ignoring the search and the status.
    public var total: Int

    public init(papers: [PaperWithAuthors], statusCounts: [String: Int], total: Int) {
        self.papers = papers
        self.statusCounts = statusCounts
        self.total = total
    }
}
```

`ios/HashiyaKit/Sources/HashiyaDatabase/PaperStore.swift`:
```swift
import Foundation
import GRDB
import os

/// The library's data access. Each operation is one transaction, and every write keeps the search index
/// `paper_search` in step with `papers`. Observations start with the current value and are delivered as
/// `AsyncStream`s, so callers never import GRDB.
public struct PaperStore: Sendable {
    private let writer: any DatabaseWriter

    public init(writer: any DatabaseWriter) {
        self.writer = writer
    }

    /// The store on the shared App Group database (`fileName` in its container).
    public static func shared(fileName: String = HashiyaDatabase.fileName) throws -> PaperStore {
        try open(at: HashiyaDatabase.sharedDatabaseURL(fileName: fileName))
    }

    /// The store on the database file at `url`.
    public static func open(at url: URL) throws -> PaperStore {
        PaperStore(writer: try HashiyaDatabase.openPool(at: url))
    }

    /// A store on a fresh in-memory database.
    public static func inMemory() throws -> PaperStore {
        PaperStore(writer: try HashiyaDatabase.openInMemory())
    }

    /// The library for `match` (an FTS MATCH expression; nil = everything) and `status` (a stored status; nil = any),
    /// one consistent read per database change. Each call starts its own observation.
    public func observeLibrary(match: String?, status: String?) -> AsyncStream<LibraryRows> {
        stream(ValueObservation.tracking { db in
            try Self.librarySnapshot(db, match: match, status: status)
        })
    }

    /// The papers, the counts per status for the same search, and the whole library's size, read together.
    public static func librarySnapshot(_ db: Database, match: String?, status: String?) throws -> LibraryRows {
        let matching = "(:match IS NULL OR papers.id IN (SELECT paper_id FROM paper_search WHERE paper_search MATCH :match))"
        let papers = try PaperRecord.fetchAll(
            db,
            sql: """
                SELECT papers.* FROM papers
                WHERE \(matching)
                  AND (:status IS NULL OR papers.reading_status = :status)
                ORDER BY papers.saved_at DESC
                """,
            arguments: ["match": match, "status": status]
        )
        let authors = try PaperAuthorRecord
            .filter(papers.map(\.id).contains(Column("paper_id")))
            .order(Column("paper_id"), Column("position"))
            .fetchAll(db)
        let authorsByPaper = Dictionary(grouping: authors, by: \.paperID)
        var statusCounts: [String: Int] = [:]
        let counts = try Row.fetchAll(
            db,
            sql: "SELECT reading_status, COUNT(*) AS count FROM papers WHERE \(matching) GROUP BY reading_status",
            arguments: ["match": match]
        )
        for row in counts {
            statusCounts[row["reading_status"]] = row["count"]
        }
        return LibraryRows(
            papers: papers.map { PaperWithAuthors(paper: $0, authors: authorsByPaper[$0.id] ?? []) },
            statusCounts: statusCounts,
            total: try PaperRecord.fetchCount(db)
        )
    }

    /// The OpenAlex IDs of saved papers. Each call starts its own observation.
    public func observeSavedOpenAlexIDs() -> AsyncStream<Set<String>> {
        stream(ValueObservation.tracking { db in
            try String.fetchSet(db, sql: "SELECT open_alex_id FROM papers WHERE open_alex_id IS NOT NULL")
        })
    }

    /// Inserts the paper unless one with the same `id` or `open_alex_id` exists, then its authors and its search row.
    /// Returns false, writing nothing, when the paper already exists.
    @discardableResult
    public func insert(paper: PaperRecord, authors: [PaperAuthorRecord], search: PaperSearchRow) async throws -> Bool {
        precondition(search.paperID == paper.id, "The search row must belong to the paper")
        return try await writer.write { db in
            try paper.insert(db, onConflict: .ignore)
            guard db.changesCount > 0 else { return false }
            for author in authors {
                try author.insert(db)
            }
            try search.insert(db)
            return true
        }
    }

    /// Deletes the paper (its authors cascade) and its search row, and returns what was deleted, or nil if it was not saved.
    public func deleteByOpenAlexID(_ openAlexID: String) async throws -> PaperWithAuthors? {
        try await writer.write { db in
            guard let paper = try PaperRecord.filter(Column("open_alex_id") == openAlexID).fetchOne(db) else {
                return nil
            }
            let authors = try PaperAuthorRecord
                .filter(Column("paper_id") == paper.id)
                .order(Column("position"))
                .fetchAll(db)
            try paper.delete(db)
            // FTS rows don't cascade.
            try db.execute(sql: "DELETE FROM paper_search WHERE paper_id = ?", arguments: [paper.id])
            return PaperWithAuthors(paper: paper, authors: authors)
        }
    }

    /// Sets a saved paper's stored status. Returns the number of papers changed: 0 when it is not saved.
    /// The order (`saved_at`) and the search index are not touched.
    @discardableResult
    public func setStatus(openAlexID: String, status: String) async throws -> Int {
        try await writer.write { db in
            try db.execute(sql: "UPDATE papers SET reading_status = ? WHERE open_alex_id = ?", arguments: [status, openAlexID])
            return db.changesCount
        }
    }

    /// Makes every observation fetch again. Observations only see writes made through this store's own
    /// database connection, not those of another process (the Share Extension).
    public func notifyExternalChanges() async throws {
        try await writer.write { db in
            try db.notifyChanges(in: Table(PaperRecord.databaseTableName))
            try db.notifyChanges(in: Table(PaperAuthorRecord.databaseTableName))
        }
    }

    private func stream<Value: Sendable>(_ observation: ValueObservation<ValueReducers.Fetch<Value>>) -> AsyncStream<Value> {
        let writer = self.writer
        return AsyncStream { continuation in
            let task = Task {
                do {
                    for try await value in observation.values(in: writer) {
                        continuation.yield(value)
                    }
                } catch {
                    #if DEBUG
                    Self.logger.error("Library observation failed: \(String(describing: error), privacy: .public)")
                    #endif
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    private static let logger = Logger(subsystem: "com.etatech.hashiya", category: "database")
}
```

The repository keeps compiling until Task 3 replaces it: it lists papers through `observeLibrary` and passes the search row.

```bash
python3 - <<'EOF'
import pathlib
p = pathlib.Path("ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift")
s = p.read_text()
reps = [
('''        store.observeSavedPapers().mapped { rows in rows.map { $0.asPaper() } }''',
 '''        store.observeLibrary(match: nil, status: nil).mapped { rows in rows.papers.map { $0.asPaper() } }'''),
('''        let records = paper.asRecords(localID: newID(), savedAt: now())
        try await store.insert(paper: records.paper, authors: records.authors)''',
 '''        let records = paper.asRecords(localID: newID(), savedAt: now())
        try await store.insert(paper: records.paper, authors: records.authors, search: records.searchRow)'''),
('''        let records = removed.paper.asRecords(localID: removed.localID, savedAt: removed.savedAt)
        try await store.insert(paper: records.paper, authors: records.authors)''',
 '''        let records = removed.paper.asRecords(localID: removed.localID, savedAt: removed.savedAt)
        try await store.insert(paper: records.paper, authors: records.authors, search: records.searchRow)'''),
]
for a, b in reps:
    assert s.count(a) == 1, a
    s = s.replace(a, b)
p.write_text(s)
EOF
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:HashiyaDatabaseTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: `✔ Test run with 26 tests in 3 suites passed` and `** TEST SUCCEEDED **`.

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:HashiyaDataTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: `✔ Test run with 55 tests in 9 suites passed` (plan 2's repository tests, unchanged) and `** TEST SUCCEEDED **`.

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Package.swift ios/HashiyaKit/Sources/HashiyaDatabase/HashiyaDatabase.swift ios/HashiyaKit/Sources/HashiyaDatabase/Records.swift \
  ios/HashiyaKit/Sources/HashiyaDatabase/PaperStore.swift ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift \
  ios/HashiyaKit/Tests/HashiyaDatabaseTests/MigrationTests.swift ios/HashiyaKit/Tests/HashiyaDatabaseTests/PaperStoreTests.swift
git commit -m "feat: add the iOS reading status column and full-text search index in migration v2"
```

---
### Task 3: `HashiyaData` — `ftsMatch`, stored statuses, `LibrarySnapshot`, `setStatus`, and the fake

**Files:**
- Modify: `ios/HashiyaKit/Package.swift` (`HashiyaDataTests` also depends on GRDB)
- Create: `ios/HashiyaKit/Sources/HashiyaData/Search/FtsQuery.swift`, `ios/HashiyaKit/Sources/HashiyaData/ReadingStatusMapping.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaData/PaperMapping.swift`, `ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift` (full replacement), `ios/HashiyaKit/Sources/HashiyaTesting/FakeLibraryRepository.swift` (full replacement), `ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift` (one loop, so the package builds; Task 5 replaces it)
- Test: Create `ios/HashiyaKit/Tests/HashiyaDataTests/FtsQueryTests.swift`, `ReadingStatusMappingTests.swift`; replace `GRDBLibraryRepositoryTests.swift`

**Interfaces:**
- Consumes: Tasks 1–2 (`searchableText`, `LibraryPaper`, `PaperStore.observeLibrary`, `insert(paper:authors:search:)`, `setStatus`, `PaperWithAuthors.searchRow`, `LibraryRows`).
- Produces (module `HashiyaData`):
  - internal `func ftsMatch(_ query: String) -> String?`; internal `ReadingStatus.storedValue: String`, `ReadingStatus.init(stored: String)`
  - `public struct LibrarySnapshot: Equatable, Sendable { var papers: [LibraryPaper]; var counts: [ReadingStatus: Int]; var libraryTotal: Int; var matchingTotal: Int { get }; init(papers:counts:libraryTotal:) }`
  - `public protocol LibraryRepository: Sendable { func observeLibrary(query: String, status: ReadingStatus?) -> AsyncStream<LibrarySnapshot>; func observeSavedIDs() -> AsyncStream<Set<String>>; func save(_ paper: Paper) async throws; func setStatus(openAlexID: String, status: ReadingStatus) async throws; func remove(openAlexID: String) async throws -> RemovedPaper?; func restore(_ removed: RemovedPaper) async throws; func refreshAfterExternalChanges() async }` (`observeSavedPapers()` is gone)
  - `public struct RemovedPaper: Equatable, Sendable { var paper: Paper; var localID: String; var savedAt: Int64; var status: ReadingStatus; init(paper:localID:savedAt:status:) }`
  - `PaperWithAuthors.asLibraryPaper() -> LibraryPaper`; `Paper.asRecords(localID:savedAt:status: ReadingStatus = .toRead) -> PaperWithAuthors`
  - `HashiyaTesting`: `FakeLibraryRepository(saved: [Paper] = [], statuses: [String: ReadingStatus] = [:])`, `.savedPapers: [Paper]`, `.library: [LibraryPaper]`, `setFailSaves(_:)`, `setFailRemoves(_:)`, `setFailStatusUpdates(_:)`

The repository tests port Android's `RoomLibraryRepositoryTest.kt` onto an in-memory GRDB database. A MATCH syntax error ends the observation stream without a value, so "never throws" is asserted as "a snapshot arrives" for each hostile input.

- [ ] **Step 1: Write the failing tests**

```bash
python3 - <<'EOF'
import pathlib
p = pathlib.Path("ios/HashiyaKit/Package.swift")
s = p.read_text()
old = '''            name: "HashiyaDataTests",
            dependencies: ["HashiyaData", "HashiyaDatabase", "HashiyaModel", "HashiyaNetwork", "HashiyaTesting"]'''
new = '''            name: "HashiyaDataTests",
            dependencies: ["HashiyaData", "HashiyaDatabase", "HashiyaModel", "HashiyaNetwork", "HashiyaTesting", grdb]'''
assert s.count(old) == 1
p.write_text(s.replace(old, new))
EOF
```

`ios/HashiyaKit/Tests/HashiyaDataTests/FtsQueryTests.swift`:
```swift
@testable import HashiyaData
import Testing

/// Ported from Android's `FtsQueryTest.kt`.
struct FtsQueryTests {
    @Test(arguments: [
        ("transf", "\"transf*\""),
        ("  Deep   LEARNING ", "\"deep*\" \"learning*\""),
        ("Schrödinger", "\"schrodinger*\""),
        ("التَّعلُّم", "\"التعلم*\""),
        ("أحمد", "\"احمد*\""),
        ("١٩", "\"19*\""),
        ("C++", "\"c*\""),
        ("\"attention", "\"attention*\""),
        ("title:deep", "\"title*\" \"deep*\""),
        ("-bert (gpt*)", "\"bert*\" \"gpt*\""),
        ("Ming-Wei", "\"ming*\" \"wei*\""),
        ("cats AND dogs", "\"cats*\" \"and*\" \"dogs*\""),
        ("OR NOT NEAR", "\"or*\" \"not*\" \"near*\""),
    ])
    func eachWordBecomesAQuotedPrefixTerm(input: String, expected: String) {
        #expect(ftsMatch(input) == expected)
    }

    @Test(arguments: ["", "   ", "\"*-():", "ـــ"])
    func blankOrPunctuationOnlyMeansNoSearch(input: String) {
        #expect(ftsMatch(input) == nil)
    }
}
```

`ios/HashiyaKit/Tests/HashiyaDataTests/ReadingStatusMappingTests.swift`:
```swift
@testable import HashiyaData
import HashiyaModel
import Testing

struct ReadingStatusMappingTests {
    /// These strings are stored on the user's phone: renaming a case must never change them.
    @Test func storedValuesAreFixed() {
        #expect(ReadingStatus.allCases.map(\.storedValue) == ["to_read", "reading", "read"])
    }

    @Test func storedValuesReadBack() {
        for status in ReadingStatus.allCases {
            #expect(ReadingStatus(stored: status.storedValue) == status)
        }
    }

    @Test(arguments: ["archived", "", "ToRead", "toRead"])
    func anUnknownStoredValueReadsAsToRead(stored: String) {
        #expect(ReadingStatus(stored: stored) == .toRead)
    }
}
```

`ios/HashiyaKit/Tests/HashiyaDataTests/GRDBLibraryRepositoryTests.swift`:
```swift
import Foundation
import GRDB
import HashiyaData
import HashiyaDatabase
import HashiyaModel
import HashiyaTesting
import os
import Testing

struct GRDBLibraryRepositoryTests {
    private let queue: DatabaseQueue
    private let repository: GRDBLibraryRepository

    /// A repository on a fresh in-memory database whose clock advances 1 000 ms per save.
    init() throws {
        let clock = OSAllocatedUnfairLock(initialState: Int64(0))
        let ids = OSAllocatedUnfairLock(initialState: 0)
        queue = try HashiyaDatabase.openInMemory()
        repository = GRDBLibraryRepository(
            store: PaperStore(writer: queue),
            now: { clock.withLock { $0 += 1_000; return $0 } },
            newID: { ids.withLock { $0 += 1; return "local-\($0)" } }
        )
    }

    /// Android's `RoomLibraryRepositoryTest` paper: two authors, and the title, abstract and venue given.
    private func paper(_ id: String, title: String? = nil, abstract: String? = nil, venue: String? = nil) -> Paper {
        Paper(
            openAlexID: id,
            title: title ?? "Paper \(id)",
            authors: [Author(name: "First"), Author(name: "Second")],
            year: 2020,
            venue: venue,
            abstract: abstract
        )
    }

    private func value<T: Sendable>(of stream: AsyncStream<T>, where predicate: (T) -> Bool = { _ in true }) async -> T? {
        for await value in stream where predicate(value) {
            return value
        }
        return nil
    }

    private func library(_ query: String = "", status: ReadingStatus? = nil) async -> LibrarySnapshot? {
        await value(of: repository.observeLibrary(query: query, status: status))
    }

    private func ids(_ query: String = "", status: ReadingStatus? = nil) async -> [String]? {
        await library(query, status: status)?.papers.map(\.paper.openAlexID)
    }

    private func counts(_ query: String) async -> [ReadingStatus: Int]? {
        await library(query)?.counts
    }

    @Test func savedPapersAreNewestFirstWithAuthorsInOrderAndStartAsToRead() async throws {
        try await repository.save(SamplePapers.attention)
        try await repository.save(SamplePapers.bert)

        let papers = await library()?.papers
        #expect(papers == [LibraryPaper(paper: SamplePapers.bert, status: .toRead), LibraryPaper(paper: SamplePapers.attention, status: .toRead)])
    }

    @Test func savedIDsListTheLibrary() async throws {
        #expect(await value(of: repository.observeSavedIDs()) == [])
        try await repository.save(SamplePapers.attention)
        try await repository.save(SamplePapers.vit)

        #expect(await value(of: repository.observeSavedIDs()) == ["W2626778328", "W3094502228"])
    }

    @Test func savingTwiceKeepsOneCopyAndItsStatus() async throws {
        try await repository.save(paper("W1"))
        try await repository.setStatus(openAlexID: "W1", status: .reading)
        try await repository.save(paper("W1"))

        #expect(await library()?.papers == [LibraryPaper(paper: paper("W1"), status: .reading)])
    }

    @Test func removeThenRestoreReturnsThePaperToItsPositionWithItsStatus() async throws {
        for paper in [SamplePapers.attention, SamplePapers.bert, SamplePapers.vit] {
            try await repository.save(paper)
        }
        try await repository.setStatus(openAlexID: SamplePapers.bert.openAlexID, status: .reading)

        let removed = try #require(try await repository.remove(openAlexID: SamplePapers.bert.openAlexID))
        #expect(removed == RemovedPaper(paper: SamplePapers.bert, localID: "local-2", savedAt: 2_000, status: .reading))
        #expect(await ids() == [SamplePapers.vit.openAlexID, SamplePapers.attention.openAlexID])

        try await repository.restore(removed)
        #expect(await ids() == [SamplePapers.vit.openAlexID, SamplePapers.bert.openAlexID, SamplePapers.attention.openAlexID])
        #expect(await ids(status: .reading) == [SamplePapers.bert.openAlexID])
        #expect(await ids("devlin bidirectional") == [SamplePapers.bert.openAlexID])
    }

    @Test func restoreAfterSavingAgainIsANoOp() async throws {
        try await repository.save(SamplePapers.attention)
        try await repository.save(SamplePapers.bert)
        let removed = try #require(try await repository.remove(openAlexID: SamplePapers.attention.openAlexID))
        try await repository.save(SamplePapers.attention)

        try await repository.restore(removed)

        #expect(await ids() == [SamplePapers.attention.openAlexID, SamplePapers.bert.openAlexID])
    }

    @Test func removingAnUnknownPaperReturnsNil() async throws {
        #expect(try await repository.remove(openAlexID: "W404") == nil)
    }

    @Test func setStatusDoesNotReorder() async throws {
        try await repository.save(paper("W1"))
        try await repository.save(paper("W2"))

        try await repository.setStatus(openAlexID: "W1", status: .read)

        #expect(await ids() == ["W2", "W1"])
        #expect(await ids(status: .read) == ["W1"])
    }

    @Test func setStatusOfAnUnsavedPaperDoesNothing() async throws {
        try await repository.setStatus(openAlexID: "W404", status: .read)
        #expect(await ids() == [])
    }

    @Test func searchFindsTheTitleAuthorsAbstractAndVenueByPrefix() async throws {
        try await repository.save(paper("W1", title: "Attention Is All You Need", abstract: "The Transformer architecture", venue: "NeurIPS"))
        var resnet = paper("W2", title: "Deep Residual Learning", venue: "CVPR")
        resnet.authors = [Author(name: "Kaiming He")]
        try await repository.save(resnet)

        #expect(await ids("transf") == ["W1"])
        #expect(await ids("kaiming") == ["W2"])
        #expect(await ids("neurips") == ["W1"])
        #expect(await ids("DEEP resid") == ["W2"])
        #expect(await ids("attention residual") == [])
        #expect(await ids("   ") == ["W2", "W1"])
    }

    @Test func searchIgnoresAccentsTashkeelAndAlefForms() async throws {
        try await repository.save(paper("W1", title: "Schrödinger equations"))
        try await repository.save(paper("W2", title: "تطبيقات التعلم العميق في معالجة اللغة"))
        try await repository.save(paper("W3", title: "أساسيات الإحصاء"))

        #expect(await ids("schrodinger") == ["W1"])
        #expect(await ids("التَّعلُّم") == ["W2"])
        #expect(await ids("اساسيات") == ["W3"])
        #expect(await ids("الاحصاء") == ["W3"])
    }

    @Test func searchMatchesArabicIndicAndASCIIDigitsEitherWay() async throws {
        try await repository.save(paper("W1", title: "COVID-19 outcomes"))
        try await repository.save(paper("W2", title: "جائحة كوفيد-١٩"))

        #expect(await ids("١٩") == ["W2", "W1"])
        #expect(await ids("19") == ["W2", "W1"])
        #expect(await ids("كوفيد ۱۹") == ["W2"])
    }

    /// Whatever the user types reaches SQLite as plain words. A MATCH syntax error would end the stream without a value.
    @Test(arguments: ["\"", "C++", "templates\"", "-templates", "-x", "(guide", "title:guide", "BERT:", "a AND", "NEAR/2", "*", "^x"])
    func searchTextWithFTSSyntaxNeverFails(query: String) async throws {
        try await repository.save(paper("W1", title: "C++ templates: a guide"))

        #expect(await library(query) != nil)
        #expect(await library(query, status: .reading) != nil)
    }

    @Test func quotesAndStarsAreIgnored() async throws {
        try await repository.save(paper("W1", title: "C++ templates: a guide"))

        #expect(await ids("\"templates") == ["W1"])
        #expect(await ids("*") == ["W1"])
    }

    @Test func countsFollowTheSearchAndFillMissingStatusesWithZero() async throws {
        try await repository.save(paper("W1", title: "Transformers one"))
        try await repository.save(paper("W2", title: "Transformers two"))
        try await repository.save(paper("W3", title: "Convolutions"))
        try await repository.setStatus(openAlexID: "W1", status: .reading)

        #expect(await counts("") == [.toRead: 2, .reading: 1, .read: 0])
        #expect(await counts("transf") == [.toRead: 1, .reading: 1, .read: 0])
        #expect(await counts("missing") == [.toRead: 0, .reading: 0, .read: 0])
        #expect(await library("missing")?.matchingTotal == 0)
        #expect(await library("missing")?.libraryTotal == 3)
    }

    @Test func anUnknownStoredStatusReadsAndCountsAsToRead() async throws {
        try await repository.save(paper("W1"))
        try await repository.save(paper("W2"))
        try await queue.write { db in
            try db.execute(sql: "UPDATE papers SET reading_status = 'archived' WHERE open_alex_id = 'W1'")
        }

        #expect(await library()?.papers.last == LibraryPaper(paper: paper("W1"), status: .toRead))
        #expect(await counts("") == [.toRead: 2, .reading: 0, .read: 0])
    }

    /// Papers, counts and the total come from one read, so they never disagree — not even while the only paper goes.
    @Test func removingTheOnlyPaperNeverEmitsASnapshotWhosePapersAndCountsDisagree() async throws {
        try await repository.save(paper("W1", title: "Transformers"))

        var snapshots: [LibrarySnapshot] = []
        for await snapshot in repository.observeLibrary(query: "transf", status: .toRead) {
            snapshots.append(snapshot)
            if snapshots.count == 1 {
                _ = try await repository.remove(openAlexID: "W1")
            }
            if snapshot.libraryTotal == 0 { break }
        }

        #expect(snapshots.first?.papers.map(\.paper.openAlexID) == ["W1"])
        #expect(snapshots.last == LibrarySnapshot(papers: [], counts: [.toRead: 0, .reading: 0, .read: 0], libraryTotal: 0))
        for snapshot in snapshots {
            #expect(snapshot.papers.count == snapshot.counts[.toRead])
            #expect(snapshot.libraryTotal >= snapshot.papers.count)
        }
    }

    @Test func observationsFollowChanges() async throws {
        var iterator = repository.observeLibrary(query: "", status: nil).makeAsyncIterator()
        #expect(await iterator.next()?.papers == [])

        try await repository.save(SamplePapers.vit)
        var latest = await iterator.next()
        while latest?.papers.isEmpty == true {
            latest = await iterator.next()
        }
        #expect(latest?.papers == [LibraryPaper(paper: SamplePapers.vit, status: .toRead)])
    }

    /// A paper saved by the Share Extension (another pool on the same file) appears after a refresh, as To read and searchable.
    @Test func refreshShowsPapersSavedThroughAnotherPool() async throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appending(path: "hashiya.sqlite")
        let app = GRDBLibraryRepository(store: try PaperStore.open(at: url))
        let shareExtension = GRDBLibraryRepository(store: try PaperStore.open(at: url))
        var library = app.observeLibrary(query: "vaswani", status: .toRead).makeAsyncIterator()
        #expect(await library.next()?.papers == [])

        try await shareExtension.save(SamplePapers.attention)
        await app.refreshAfterExternalChanges()

        #expect(await library.next()?.papers == [LibraryPaper(paper: SamplePapers.attention, status: .toRead)])
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:HashiyaDataTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL — `FtsQueryTests.swift:22:17: error: cannot find 'ftsMatch' in scope`, `ReadingStatusMappingTests.swift:8:44: error: value of type 'ReadingStatus' has no member 'storedValue'`, `ReadingStatusMappingTests.swift:19:30: error: incorrect argument label in call (have 'stored:', expected 'rawValue:')`, then `** TEST FAILED **`.

- [ ] **Step 3: Implement**

`ios/HashiyaKit/Sources/HashiyaData/Search/FtsQuery.swift`:
```swift
import HashiyaModel

/// The FTS MATCH expression for what the user typed, or nil when no word is left ("no search").
/// Each word becomes a quoted prefix term and every word must match: "Deep lear" → `"deep*" "lear*"`.
/// Only letters and numbers survive, so FTS syntax (quotes, `*`, `-`, parentheses, `column:`, `^`) never reaches
/// MATCH and `AND`/`OR`/`NOT`/`NEAR` are ordinary words. FTS4 reads a prefix only inside the quotes (`"transf*"`);
/// `"transf"*` would match the exact word.
func ftsMatch(_ query: String) -> String? {
    let words = searchableText(query).unicodeScalars
        .split { !isWordScalar($0) }
        .map { String(Substring($0)) }
    guard !words.isEmpty else { return nil }
    return words.map { "\"\($0)*\"" }.joined(separator: " ")
}

/// Letters (Lu, Ll, Lt, Lm, Lo) and numbers (Nd, Nl, No).
private func isWordScalar(_ scalar: Unicode.Scalar) -> Bool {
    switch scalar.properties.generalCategory {
    case .uppercaseLetter, .lowercaseLetter, .titlecaseLetter, .modifierLetter, .otherLetter,
         .decimalNumber, .letterNumber, .otherNumber:
        true
    default:
        false
    }
}
```

`ios/HashiyaKit/Sources/HashiyaData/ReadingStatusMapping.swift`:
```swift
import HashiyaModel

extension ReadingStatus {
    /// The value stored in `papers.reading_status`. Fixed strings, so renaming a case never changes stored data.
    var storedValue: String {
        switch self {
        case .toRead: "to_read"
        case .reading: "reading"
        case .read: "read"
        }
    }

    /// Reads a stored value back; anything unknown is To read.
    init(stored: String) {
        self = Self.allCases.first { $0.storedValue == stored } ?? .toRead
    }
}
```

`ios/HashiyaKit/Sources/HashiyaData/PaperMapping.swift`:
```swift
import Foundation
import HashiyaDatabase
import HashiyaModel
import HashiyaNetwork

private let openAlexPrefix = "https://openalex.org/"

private func shortOpenAlexID(_ id: String) -> String {
    id.hasPrefix(openAlexPrefix) ? String(id.dropFirst(openAlexPrefix.count)) : id
}

extension NetworkWork {
    public func asPaper() -> Paper {
        Paper(
            openAlexID: shortOpenAlexID(id),
            doi: doi.flatMap(normalizeDOI),
            title: displayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "",
            authors: authorships.compactMap { authorship in
                guard let name = authorship.author.displayName,
                      !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
                return Author(name: name, openAlexID: authorship.author.id.map(shortOpenAlexID))
            },
            year: publicationYear,
            venue: primaryLocation?.source?.displayName,
            abstract: rebuildAbstract(abstractInvertedIndex),
            citationCount: citedByCount,
            isOpenAccess: openAccess?.isOA ?? false,
            openAccessPDFURL: bestOALocation?.pdfURL
        )
    }
}

extension PaperWithAuthors {
    /// The paper with its stored status; an unknown stored value is To read.
    public func asLibraryPaper() -> LibraryPaper {
        LibraryPaper(paper: asPaper(), status: ReadingStatus(stored: paper.readingStatus))
    }

    public func asPaper() -> Paper {
        Paper(
            // Every paper saved by this version has an OpenAlex ID.
            openAlexID: paper.openAlexID ?? "",
            doi: paper.doi,
            title: paper.title,
            authors: authors.sorted { $0.position < $1.position }.map { Author(name: $0.name, openAlexID: $0.openAlexAuthorID) },
            year: paper.year,
            venue: paper.venue,
            abstract: paper.abstract,
            citationCount: paper.citationCount,
            isOpenAccess: paper.isOpenAccess,
            openAccessPDFURL: paper.oaPDFURL
        )
    }
}

extension Paper {
    public func asRecords(localID: String, savedAt: Int64, status: ReadingStatus = .toRead) -> PaperWithAuthors {
        PaperWithAuthors(
            paper: PaperRecord(
                id: localID,
                openAlexID: openAlexID,
                doi: doi,
                title: title,
                year: year,
                venue: venue,
                abstract: abstract,
                citationCount: citationCount,
                isOpenAccess: isOpenAccess,
                oaPDFURL: openAccessPDFURL,
                savedAt: savedAt,
                readingStatus: status.storedValue
            ),
            authors: authors.enumerated().map { index, author in
                PaperAuthorRecord(paperID: localID, position: index, name: author.name, openAlexAuthorID: author.openAlexID)
            }
        )
    }
}
```

`ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift`:
```swift
import Foundation
import HashiyaDatabase
import HashiyaModel

/// The Library for one search and status filter, read in one go so its parts always describe the same moment.
public struct LibrarySnapshot: Equatable, Sendable {
    /// Papers matching the search and the status, newest saved first.
    public var papers: [LibraryPaper]
    /// Papers matching the search (whatever their status) per status; all three keys are present.
    public var counts: [ReadingStatus: Int]
    /// Every saved paper, ignoring the search and the status.
    public var libraryTotal: Int

    /// Papers matching the search: the All chip.
    public var matchingTotal: Int { counts.values.reduce(0, +) }

    public init(papers: [LibraryPaper], counts: [ReadingStatus: Int], libraryTotal: Int) {
        self.papers = papers
        self.counts = counts
        self.libraryTotal = libraryTotal
    }
}

public protocol LibraryRepository: Sendable {
    /// One consistent snapshot per database change. Blank query = all; nil status = all. Each call returns a new
    /// stream starting with the current value.
    func observeLibrary(query: String, status: ReadingStatus?) -> AsyncStream<LibrarySnapshot>
    /// The OpenAlex IDs in the library. Each call returns a new stream starting with the current value.
    func observeSavedIDs() -> AsyncStream<Set<String>>
    /// Starts as To read. Already saved → no-op.
    func save(_ paper: Paper) async throws
    /// Doesn't reorder. Not saved → no-op.
    func setStatus(openAlexID: String, status: ReadingStatus) async throws
    /// Nil if the paper was not saved.
    func remove(openAlexID: String) async throws -> RemovedPaper?
    /// Puts a removed paper back with the same local ID, saved time and status. No-op if it was saved again meanwhile.
    func restore(_ removed: RemovedPaper) async throws
    /// Makes every observation fetch again, so papers saved by the Share Extension appear. Failures are ignored.
    func refreshAfterExternalChanges() async
}

/// What `remove` deleted, so Undo can put it back in the same place with the same status.
public struct RemovedPaper: Equatable, Sendable {
    public var paper: Paper
    public var localID: String
    public var savedAt: Int64
    public var status: ReadingStatus

    public init(paper: Paper, localID: String, savedAt: Int64, status: ReadingStatus) {
        self.paper = paper
        self.localID = localID
        self.savedAt = savedAt
        self.status = status
    }
}

public struct GRDBLibraryRepository: LibraryRepository {
    private let store: PaperStore
    private let now: @Sendable () -> Int64
    private let newID: @Sendable () -> String

    /// - Parameters:
    ///   - now: epoch milliseconds.
    ///   - newID: a new lowercase UUID string.
    public init(
        store: PaperStore,
        now: @escaping @Sendable () -> Int64 = { Int64((Date().timeIntervalSince1970 * 1000).rounded()) },
        newID: @escaping @Sendable () -> String = { UUID().uuidString.lowercased() }
    ) {
        self.store = store
        self.now = now
        self.newID = newID
    }

    /// The library in the App Group database file `fileName`; `fresh` deletes that file first (UI tests only).
    public static func shared(fileName: String = HashiyaDatabase.fileName, fresh: Bool = false) throws -> GRDBLibraryRepository {
        let url = try HashiyaDatabase.sharedDatabaseURL(fileName: fileName)
        if fresh { try HashiyaDatabase.removeDatabase(at: url) }
        return GRDBLibraryRepository(store: try PaperStore.open(at: url))
    }

    /// A repository on a fresh in-memory database (tests and UI-test launches).
    public static func inMemory(
        now: @escaping @Sendable () -> Int64 = { Int64((Date().timeIntervalSince1970 * 1000).rounded()) },
        newID: @escaping @Sendable () -> String = { UUID().uuidString.lowercased() }
    ) throws -> GRDBLibraryRepository {
        GRDBLibraryRepository(store: try PaperStore.inMemory(), now: now, newID: newID)
    }

    public func observeLibrary(query: String, status: ReadingStatus?) -> AsyncStream<LibrarySnapshot> {
        store.observeLibrary(match: ftsMatch(query), status: status?.storedValue).mapped { $0.asSnapshot() }
    }

    public func observeSavedIDs() -> AsyncStream<Set<String>> {
        store.observeSavedOpenAlexIDs()
    }

    public func save(_ paper: Paper) async throws {
        let records = paper.asRecords(localID: newID(), savedAt: now())
        try await store.insert(paper: records.paper, authors: records.authors, search: records.searchRow)
    }

    public func setStatus(openAlexID: String, status: ReadingStatus) async throws {
        try await store.setStatus(openAlexID: openAlexID, status: status.storedValue)
    }

    public func remove(openAlexID: String) async throws -> RemovedPaper? {
        guard let deleted = try await store.deleteByOpenAlexID(openAlexID) else { return nil }
        let saved = deleted.asLibraryPaper()
        return RemovedPaper(paper: saved.paper, localID: deleted.paper.id, savedAt: deleted.paper.savedAt, status: saved.status)
    }

    public func restore(_ removed: RemovedPaper) async throws {
        let records = removed.paper.asRecords(localID: removed.localID, savedAt: removed.savedAt, status: removed.status)
        try await store.insert(paper: records.paper, authors: records.authors, search: records.searchRow)
    }

    public func refreshAfterExternalChanges() async {
        try? await store.notifyExternalChanges()
    }
}

extension LibraryRows {
    /// Every stored status mapped to its `ReadingStatus` (unknown values count as To read); missing statuses are 0.
    func asSnapshot() -> LibrarySnapshot {
        var counts = Dictionary(uniqueKeysWithValues: ReadingStatus.allCases.map { ($0, 0) })
        for (stored, count) in statusCounts {
            counts[ReadingStatus(stored: stored), default: 0] += count
        }
        return LibrarySnapshot(papers: papers.map { $0.asLibraryPaper() }, counts: counts, libraryTotal: total)
    }
}

/// The App Group database's lifecycle for the app and the Share Extension, which never import GRDB.
public enum SharedLibraryDatabase {
    /// Call before the process is suspended; see `HashiyaDatabase.suspend()`.
    public static func suspend() {
        HashiyaDatabase.suspend()
    }

    /// Call when the process is active again.
    public static func resume() {
        HashiyaDatabase.resume()
    }
}

extension AsyncStream where Element: Sendable {
    /// A stream of `transform(element)`; cancelling it cancels the iteration of `self`.
    func mapped<T: Sendable>(_ transform: @escaping @Sendable (Element) -> T) -> AsyncStream<T> {
        AsyncStream<T> { continuation in
            let task = Task {
                for await element in self {
                    continuation.yield(transform(element))
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}
```

`ios/HashiyaKit/Sources/HashiyaTesting/FakeLibraryRepository.swift`:
```swift
import Foundation
import HashiyaData
import HashiyaModel
import os

/// An in-memory library with live streams. Saves get increasing times, so the newest is first.
/// Its search is a simple stand-in for the real index: every typed word must start a word of the paper's title,
/// authors, abstract or venue, compared through `searchableText`.
public final class FakeLibraryRepository: LibraryRepository {
    public struct Failure: Error {}

    private struct Entry {
        var paper: Paper
        var localID: String
        var savedAt: Int64
        var status: ReadingStatus
    }

    private struct Subscription {
        let query: String
        let status: ReadingStatus?
        let continuation: AsyncStream<LibrarySnapshot>.Continuation
    }

    private struct State {
        var entries: [Entry] = []
        var clock: Int64 = 0
        var failSaves = false
        var failRemoves = false
        var failStatusUpdates = false
        var subscriptions: [UUID: Subscription] = [:]
        var idContinuations: [UUID: AsyncStream<Set<String>>.Continuation] = [:]

        var library: [LibraryPaper] {
            entries.sorted { $0.savedAt > $1.savedAt }.map { LibraryPaper(paper: $0.paper, status: $0.status) }
        }
        var ids: Set<String> { Set(entries.map(\.paper.openAlexID)) }

        func snapshot(query: String, status: ReadingStatus?) -> LibrarySnapshot {
            let matching = library.filter { FakeLibraryRepository.matches($0.paper, query: query) }
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

        func publish() {
            for subscription in subscriptions.values {
                subscription.continuation.yield(snapshot(query: subscription.query, status: subscription.status))
            }
            let ids = ids
            idContinuations.values.forEach { $0.yield(ids) }
        }
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    /// `saved` is the initial library, newest first; `statuses` gives some of them a status by OpenAlex ID (else To read).
    public init(saved: [Paper] = [], statuses: [String: ReadingStatus] = [:]) {
        state.withLock { state in
            for paper in saved.reversed() {
                state.clock += 1
                state.entries.append(Entry(
                    paper: paper,
                    localID: "local-\(paper.openAlexID)",
                    savedAt: state.clock,
                    status: statuses[paper.openAlexID] ?? .toRead
                ))
            }
        }
    }

    public var savedPapers: [Paper] { state.withLock { $0.library.map(\.paper) } }
    /// The library with statuses, newest first.
    public var library: [LibraryPaper] { state.withLock { $0.library } }

    /// When true, `save` and `restore` throw.
    public func setFailSaves(_ fail: Bool) { state.withLock { $0.failSaves = fail } }
    /// When true, `remove` throws.
    public func setFailRemoves(_ fail: Bool) { state.withLock { $0.failRemoves = fail } }
    /// When true, `setStatus` throws.
    public func setFailStatusUpdates(_ fail: Bool) { state.withLock { $0.failStatusUpdates = fail } }

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

    public func save(_ paper: Paper) async throws {
        try state.withLock { state in
            if state.failSaves { throw Failure() }
            guard !state.ids.contains(paper.openAlexID) else { return }
            state.clock += 1
            state.entries.append(Entry(paper: paper, localID: "local-\(paper.openAlexID)", savedAt: state.clock, status: .toRead))
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

    public func remove(openAlexID: String) async throws -> RemovedPaper? {
        try state.withLock { state in
            if state.failRemoves { throw Failure() }
            guard let index = state.entries.firstIndex(where: { $0.paper.openAlexID == openAlexID }) else { return nil }
            let entry = state.entries.remove(at: index)
            state.publish()
            return RemovedPaper(paper: entry.paper, localID: entry.localID, savedAt: entry.savedAt, status: entry.status)
        }
    }

    public func restore(_ removed: RemovedPaper) async throws {
        try state.withLock { state in
            if state.failSaves { throw Failure() }
            guard !state.ids.contains(removed.paper.openAlexID) else { return }
            state.entries.append(Entry(paper: removed.paper, localID: removed.localID, savedAt: removed.savedAt, status: removed.status))
            state.publish()
        }
    }

    /// Emits the current values again.
    public func refreshAfterExternalChanges() async {
        state.withLock { $0.publish() }
    }

    static func matches(_ paper: Paper, query: String) -> Bool {
        let wanted = words(query)
        guard !wanted.isEmpty else { return true }
        let text = [paper.title, paper.authors.map(\.name).joined(separator: " "), paper.abstract ?? "", paper.venue ?? ""]
        let available = words(text.joined(separator: " "))
        return wanted.allSatisfy { word in available.contains { $0.hasPrefix(word) } }
    }

    private static func words(_ text: String) -> [String] {
        searchableText(text).split { !$0.isLetter && !$0.isNumber }.map(String.init)
    }
}
```

The Library view model reads the snapshot's papers until Task 5 rewrites it:

```bash
python3 - <<'EOF'
import pathlib
p = pathlib.Path("ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift")
s = p.read_text()
old = '''            for await papers in library.observeSavedPapers() {
                guard let self else { return }
'''
new = '''            for await snapshot in library.observeLibrary(query: "", status: nil) {
                guard let self else { return }
                let papers = snapshot.papers.map(\\.paper)
'''
assert s.count(old) == 1
p.write_text(s.replace(old, new))
EOF
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:HashiyaDataTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: `✔ Test run with 70 tests in 11 suites passed` and `** TEST SUCCEEDED **`.

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:FeatureLibraryTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'` and `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:FeatureSearchTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: `15 tests in 3 suites` and `83 tests in 8 suites` pass (the fake keeps `savedPapers` and `init(saved:)`; if the Library or Search images are missing locally, the first run records them).

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Package.swift ios/HashiyaKit/Sources/HashiyaData/Search/FtsQuery.swift ios/HashiyaKit/Sources/HashiyaData/ReadingStatusMapping.swift \
  ios/HashiyaKit/Sources/HashiyaData/PaperMapping.swift ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift \
  ios/HashiyaKit/Sources/HashiyaTesting/FakeLibraryRepository.swift ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift \
  ios/HashiyaKit/Tests/HashiyaDataTests/FtsQueryTests.swift ios/HashiyaKit/Tests/HashiyaDataTests/ReadingStatusMappingTests.swift \
  ios/HashiyaKit/Tests/HashiyaDataTests/GRDBLibraryRepositoryTests.swift
git commit -m "feat: observe the iOS library as one snapshot with search, statuses and counts"
```

---
### Task 4: `HashiyaDesignSystem` — status labels and the preview's segmented selector

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/ReadingStatusSelector.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/PaperPreviewContent.swift`, `ios/HashiyaKit/Sources/HashiyaDesignSystem/Resources/Localizable.xcstrings`
- Test: Create `ios/HashiyaKit/Tests/HashiyaDesignSystemTests/ReadingStatusSelectorTests.swift`; modify `DesignSystemSnapshotTests.swift`

**Interfaces:**
- Consumes: Task 1's `ReadingStatus`; plan 1's `PaperPreviewContent`, `HashiyaStrings.recordedLookups`, `assertHashiyaSnapshots`.
- Produces (module `HashiyaDesignSystem`, `public`):
  - `@MainActor func readingStatusLabel(_ status: ReadingStatus) -> String` (strings `status.toRead`, `status.reading`, `status.read`)
  - `struct ReadingStatusSelector: View { init(status: ReadingStatus, onChange: @escaping (ReadingStatus) -> Void) }` (internal `selection: Binding<ReadingStatus>`; choosing the current status does nothing)
  - `PaperPreviewContent.init(paper: Paper, inLibrary: Bool, status: ReadingStatus? = nil, onStatusChange: @escaping (ReadingStatus) -> Void = { _ in }, onToggleSave: @escaping () -> Void, onOpenDOI: ((String) -> Void)?)` — Search and the Share Extension keep calling it without a status and look as before.

- [ ] **Step 1: Write the failing tests**

`ios/HashiyaKit/Tests/HashiyaDesignSystemTests/ReadingStatusSelectorTests.swift`:
```swift
@testable import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import SwiftUI
import Testing
import UIKit

@MainActor
@Suite(.serialized)
struct ReadingStatusSelectorTests {
    private func inLanguage<T>(_ language: String, _ body: () -> T) -> T {
        let previous = HashiyaLanguage.override
        HashiyaLanguage.override = language
        defer { HashiyaLanguage.override = previous }
        return body()
    }

    /// The strings `view` looks up while it is laid out in a window, in English.
    private func renderedStrings(of view: some View) -> [String] {
        inLanguage("en") {
            let previous = HashiyaStrings.recordedLookups
            HashiyaStrings.recordedLookups = []
            defer { HashiyaStrings.recordedLookups = previous }
            let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
            window.rootViewController = UIHostingController(rootView: view)
            window.makeKeyAndVisible()
            window.layoutIfNeeded()
            let rendered = HashiyaStrings.recordedLookups ?? []
            window.isHidden = true
            return rendered
        }
    }

    @Test func statusesHaveTheirNamesInBothLanguages() {
        #expect(inLanguage("en") { ReadingStatus.allCases.map(readingStatusLabel) } == ["To read", "Reading", "Read"])
        #expect(inLanguage("ar") { ReadingStatus.allCases.map(readingStatusLabel) } == ["للقراءة", "قيد القراءة", "مقروءة"])
    }

    @Test func thePreviewShowsTheSelectorOnlyWithAStatus() {
        let withStatus = renderedStrings(of: PaperPreviewContent(
            paper: SamplePapers.attention, inLibrary: true, status: .reading, onToggleSave: {}, onOpenDOI: nil
        ))
        let withoutStatus = renderedStrings(of: PaperPreviewContent(
            paper: SamplePapers.attention, inLibrary: false, onToggleSave: {}, onOpenDOI: nil
        ))

        #expect(["To read", "Reading", "Read"].allSatisfy(withStatus.contains))
        #expect(withStatus.contains("Remove from library"))
        #expect(!withoutStatus.contains("To read"))
        #expect(withoutStatus.contains("Save to library"))
    }

    @Test func choosingAnotherSegmentCallsOnStatusChange() {
        var received: [ReadingStatus] = []
        let selector = ReadingStatusSelector(status: .toRead) { received.append($0) }

        selector.selection.wrappedValue = .reading
        selector.selection.wrappedValue = .toRead

        #expect(received == [.reading])
        #expect(selector.selection.wrappedValue == .toRead)
    }
}
```

`DesignSystemSnapshotTests.swift` gains the preview with its selector (full file):

`ios/HashiyaKit/Tests/HashiyaDesignSystemTests/DesignSystemSnapshotTests.swift`:
```swift
import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import SwiftUI
import Testing

@MainActor
@Suite(.serialized)
struct DesignSystemSnapshotTests {
    @Test func paperCards() {
        let cards = ScrollView {
            VStack(spacing: 0) {
                PaperCard(paper: SamplePapers.attention, inLibrary: true, onOpen: {}, onSave: {})
                PaperCard(paper: SamplePapers.bert, inLibrary: false, onOpen: {}, onSave: {})
                PaperCard(paper: SamplePapers.arabicTitled, inLibrary: false, onOpen: {}, onSave: {})
                PaperCard(paper: SamplePapers.untitled, inLibrary: false, onOpen: {}, onSave: {})
            }
        }
        .background(HashiyaColors.surface)
        assertHashiyaSnapshots(of: cards, named: "cards", arabicText: "في المكتبة")
    }

    @Test func previewOpenAccessWithPDF() {
        let preview = PaperPreviewContent(paper: SamplePapers.attention, inLibrary: false, onToggleSave: {}, onOpenDOI: { _ in })
        assertHashiyaSnapshots(of: preview, named: "previewOpenAccessPDF", arabicText: "وصول مفتوح · ملف PDF متاح")
    }

    @Test func previewWithoutAbstractInLibrary() {
        let preview = PaperPreviewContent(paper: SamplePapers.vit, inLibrary: true, onToggleSave: {}, onOpenDOI: { _ in })
        assertHashiyaSnapshots(of: preview, named: "previewNoAbstract", arabicText: "لا يوجد ملخص")
    }

    @Test func previewWithoutDOI() {
        let preview = PaperPreviewContent(paper: SamplePapers.arabicTitled, inLibrary: false, onToggleSave: {}, onOpenDOI: { _ in })
        assertHashiyaSnapshots(of: preview, named: "previewNoDOI", arabicText: "حفظ في المكتبة")
    }

    @Test func previewWithTheStatusSelector() {
        let preview = PaperPreviewContent(
            paper: SamplePapers.arabicTitled,
            inLibrary: true,
            status: .reading,
            onToggleSave: {},
            onOpenDOI: { _ in }
        )
        assertHashiyaSnapshots(of: preview, named: "previewStatus", arabicText: "قيد القراءة")
    }

    @Test func messageStatesAndBanner() {
        let states = VStack(spacing: 0) {
            EmptyStateView(icon: "books.vertical", title: "Empty", message: "Message", actionTitle: "Action", action: {})
            LoadingSkeleton(rows: 2)
            HashiyaBanner(text: "Banner", actionTitle: "Undo", action: {})
            PaperCard(paper: SamplePapers.untitled, inLibrary: true, onOpen: {}, onSave: {})
        }
        .background(HashiyaColors.surface)
        assertHashiyaSnapshots(of: states, named: "components", arabicText: "بدون عنوان")
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:HashiyaDesignSystemTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL — `DesignSystemSnapshotTests.swift:42:22: error: extra argument 'status' in call`, then `** TEST FAILED **`.

- [ ] **Step 3: Implement the strings, the selector and the preview**

```bash
python3 - <<'EOF'
import json, pathlib
path = pathlib.Path("ios/HashiyaKit/Sources/HashiyaDesignSystem/Resources/Localizable.xcstrings")
catalog = json.loads(path.read_text(encoding="utf-8"))
for key, en, ar in [
    ("status.toRead", "To read", "للقراءة"),
    ("status.reading", "Reading", "قيد القراءة"),
    ("status.read", "Read", "مقروءة"),
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

`ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/ReadingStatusSelector.swift`:
```swift
import HashiyaModel
import SwiftUI

/// A status's name: "To read", "Reading", "Read". The one place statuses get their names (badge, menu, chips, selector).
@MainActor
public func readingStatusLabel(_ status: ReadingStatus) -> String {
    switch status {
    case .toRead: L10n.string("status.toRead")
    case .reading: L10n.string("status.reading")
    case .read: L10n.string("status.read")
    }
}

/// To read · Reading · Read as a segmented control. Choosing another status calls `onChange`; the control
/// always shows `status`, so it reflects what is stored.
public struct ReadingStatusSelector: View {
    private let status: ReadingStatus
    private let onChange: (ReadingStatus) -> Void

    public init(status: ReadingStatus, onChange: @escaping (ReadingStatus) -> Void) {
        self.status = status
        self.onChange = onChange
    }

    var selection: Binding<ReadingStatus> {
        Binding(get: { status }, set: { newValue in
            if newValue != status { onChange(newValue) }
        })
    }

    public var body: some View {
        Picker(selection: selection) {
            ForEach(ReadingStatus.allCases, id: \.self) { status in
                Text(verbatim: readingStatusLabel(status)).tag(status)
            }
        } label: {
            EmptyView()
        }
        .pickerStyle(.segmented)
    }
}
```

`ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/PaperPreviewContent.swift`:
```swift
import HashiyaModel
import SwiftUI

/// The preview sheet's body: the full paper, the reading status (Library only), then Open DOI and Save/Remove.
public struct PaperPreviewContent: View {
    private let paper: Paper
    private let inLibrary: Bool
    private let status: ReadingStatus?
    private let onStatusChange: (ReadingStatus) -> Void
    private let onToggleSave: () -> Void
    private let onOpenDOI: ((String) -> Void)?

    /// - Parameters:
    ///   - status: the saved paper's status, shown as a segmented selector above the buttons; nil shows none.
    ///   - onOpenDOI: nil hides Open DOI.
    public init(
        paper: Paper,
        inLibrary: Bool,
        status: ReadingStatus? = nil,
        onStatusChange: @escaping (ReadingStatus) -> Void = { _ in },
        onToggleSave: @escaping () -> Void,
        onOpenDOI: ((String) -> Void)?
    ) {
        self.paper = paper
        self.inLibrary = inLibrary
        self.status = status
        self.onStatusChange = onStatusChange
        self.onToggleSave = onToggleSave
        self.onOpenDOI = onOpenDOI
    }

    public var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    PaperText(PaperFormat.title(paper), style: .previewTitle)
                        .accessibilityAddTraits(.isHeader)
                    if !paper.authors.isEmpty {
                        PaperText(paper.authors.map(\.name).joined(separator: ", "), style: .body, color: HashiyaColors.onSurfaceVariant)
                    }
                    Text(verbatim: PaperFormat.previewMeta(paper))
                        .font(.hashiya(.meta))
                        .foregroundStyle(HashiyaColors.onSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if paper.isOpenAccess {
                        StatusBadge(
                            text: L10n.string(paper.openAccessPDFURL == nil ? "designsystem.openAccess" : "designsystem.openAccessPDF"),
                            kind: .openAccess
                        )
                    }
                    Text(verbatim: L10n.string("designsystem.abstract"))
                        .font(.hashiya(.label))
                        .foregroundStyle(HashiyaColors.onSurfaceVariant)
                        .padding(.top, 4)
                    if let abstract = paper.abstract {
                        PaperText(abstract, style: .body)
                    } else {
                        Text(verbatim: L10n.string("designsystem.noAbstract"))
                            .font(.hashiya(.body))
                            .foregroundStyle(HashiyaColors.onSurfaceVariant)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 24)
                .padding(.bottom, 16)
            }
            if let status {
                // 16 pt above; the buttons' 12 pt padding plus 4 makes 16 below.
                ReadingStatusSelector(status: status, onChange: onStatusChange)
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    .padding(.bottom, 4)
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
                    .buttonStyle(.bordered)
                    .tint(HashiyaColors.primary)
                }
                Button(action: onToggleSave) {
                    Text(verbatim: L10n.string(inLibrary ? "designsystem.removeFromLibrary" : "designsystem.saveToLibrary"))
                        .font(.hashiya(.label))
                        .foregroundStyle(HashiyaColors.onPrimary)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(HashiyaColors.primary)
            }
            .controlSize(.large)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .background(HashiyaColors.surface)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
rm -rf ios/HashiyaKit/Tests/HashiyaDesignSystemTests/__Snapshots__/DesignSystemSnapshotTests
```

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:HashiyaDesignSystemTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected on the first run: 14 tests pass; the six snapshot tests each fail with 4 `No reference was found on disk. Automatically recorded snapshot: …` issues (`✘ Test run with 20 tests in 5 suites failed … with 24 issues`).

Run the same command again.
Expected: `✔ Test run with 20 tests in 5 suites passed` and `** TEST SUCCEEDED **`. Check `previewWithTheStatusSelector.previewStatus-ArabicLight.png`: the selector right-to-left (للقراءة on the right), قيد القراءة selected, above إزالة من المكتبة.

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaDesignSystem/Resources/Localizable.xcstrings ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/ReadingStatusSelector.swift \
  ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/PaperPreviewContent.swift \
  ios/HashiyaKit/Tests/HashiyaDesignSystemTests/ReadingStatusSelectorTests.swift ios/HashiyaKit/Tests/HashiyaDesignSystemTests/DesignSystemSnapshotTests.swift
git commit -m "feat: add iOS reading status labels and the preview's status selector"
```

---
### Task 5: `FeatureLibrary` view model — search with debounce, chips, Empty vs No papers match, statuses, restoration

**Files:**
- Modify: `ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift` (full replacement), `ios/HashiyaKit/Sources/FeatureLibrary/LibraryView.swift` (five edits, so it builds; Task 6 replaces it)
- Test: Replace `ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryViewModelTests.swift`

**Interfaces:**
- Consumes: Task 3's `LibraryRepository.observeLibrary`, `setStatus`, `LibrarySnapshot`, `RemovedPaper.status`, `FakeLibraryRepository(saved:statuses:)`, `setFailStatusUpdates`; plan 1's `TaskBag`, `ManualSleeper`, `eventually`.
- Produces (module `FeatureLibrary`, `public`):
  - `enum LibraryState: Equatable, Sendable { case loading, empty; case noMatches(LibraryFilter); case papers([LibraryPaper], LibraryFilter) }`
  - `struct LibraryFilter: Equatable, Sendable { var query: String; var status: ReadingStatus?; var counts: [ReadingStatus: Int]; var total: Int { get }; init(query:status:counts:) }`
  - `enum LibraryMessage: Equatable, Sendable { case statusUpdateFailed }`
  - `@Observable @MainActor final class LibraryViewModel`: `static let queryKey = "library_query"`, `statusKey = "library_status"`, `debounce = .milliseconds(300)`; `init(library: any LibraryRepository, sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) })`; read-only `state`, `text`, `appliedQuery`, `status`, `pendingUndo`, `isLoaded`, `papers: [LibraryPaper]`, `filter: LibraryFilter?`, `selectedPaper: LibraryPaper?`, `storedStatus: String`; settable `selectedPaperID: String?`, `message: LibraryMessage?`; `updateText(_:)`, `submitNow()`, `setStatusFilter(_:)`, `clearSearchAndFilters()`, `restore(text:status:)`, `setStatus(of: Paper, to: ReadingStatus) async`, `select(_:)`, `remove(_:) async`, `undo() async`, `undoExpired()`; internal `stateObserver: ((LibraryState) -> Void)?` for tests.

Each (applied query, chip) pair replaces the previous observation task; the old state stays on screen until the new stream's first snapshot, so there is no loading flash between keystrokes. The debounce task checks for cancellation after its sleep, so Search or Clear pressed just as the pause ends always wins.

- [ ] **Step 1: Write the failing tests**

`ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryViewModelTests.swift`:
```swift
@testable import FeatureLibrary
import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct LibraryViewModelTests {
    private let sleeper = ManualSleeper()

    private func makeViewModel(_ library: FakeLibraryRepository) -> LibraryViewModel {
        LibraryViewModel(library: library, sleep: sleeper.sleep)
    }

    private func counts(_ toRead: Int, _ reading: Int, _ read: Int) -> [ReadingStatus: Int] {
        [.toRead: toRead, .reading: reading, .read: read]
    }

    private func ids(_ viewModel: LibraryViewModel) -> [String] {
        viewModel.papers.map(\.paper.openAlexID)
    }

    /// Types `text` and lets the debounce elapse.
    private func type(_ text: String, into viewModel: LibraryViewModel) async {
        viewModel.updateText(text)
        await sleeper.waitForSleeper()
        sleeper.advance(by: .milliseconds(300))
    }

    /// Every state `viewModel` goes through from now on.
    private func recordStates(of viewModel: LibraryViewModel) -> StateRecorder {
        let recorder = StateRecorder()
        viewModel.stateObserver = { recorder.states.append($0) }
        return recorder
    }

    @Test func anEmptyLibraryIsEmpty() async {
        let viewModel = makeViewModel(FakeLibraryRepository())
        #expect(viewModel.state == .loading)
        #expect(await eventually { viewModel.state == .empty })
        #expect(viewModel.isLoaded)
    }

    @Test func papersAreNewestFirstWithTheirStatusesAndCounts() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.vit, SamplePapers.bert, SamplePapers.attention], statuses: [SamplePapers.bert.openAlexID: .reading])
        let viewModel = makeViewModel(library)

        let filter = LibraryFilter(query: "", status: nil, counts: counts(2, 1, 0))
        #expect(await eventually {
            viewModel.state == .papers([
                LibraryPaper(paper: SamplePapers.vit, status: .toRead),
                LibraryPaper(paper: SamplePapers.bert, status: .reading),
                LibraryPaper(paper: SamplePapers.attention, status: .toRead),
            ], filter)
        })
        #expect(filter.total == 3)
    }

    @Test func newSavesAppearAtTheTop() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.papers.count == 1 })

        try await library.save(SamplePapers.bert)
        #expect(await eventually { ids(viewModel) == [SamplePapers.bert.openAlexID, SamplePapers.attention.openAlexID] })
    }

    @Test func typingSearchesAfter300Milliseconds() async throws {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: SamplePapers.all))
        #expect(await eventually { viewModel.papers.count == 3 })

        viewModel.updateText("bert")
        await sleeper.waitForSleeper()
        sleeper.advance(by: .milliseconds(299))
        try await Task.sleep(for: .milliseconds(20))
        #expect(viewModel.appliedQuery == "")
        #expect(viewModel.papers.count == 3)

        sleeper.advance(by: .milliseconds(1))
        #expect(await eventually { ids(viewModel) == [SamplePapers.bert.openAlexID] })
        #expect(viewModel.appliedQuery == "bert")
        #expect(viewModel.filter?.query == "bert")
    }

    @Test func theSearchKeyAppliesAtOnce() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: SamplePapers.all))
        #expect(await eventually { viewModel.papers.count == 3 })

        viewModel.updateText("vaswani")
        viewModel.submitNow()

        #expect(viewModel.appliedQuery == "vaswani")
        #expect(await eventually { ids(viewModel) == [SamplePapers.attention.openAlexID] })
        #expect(sleeper.pendingCount == 0)
    }

    @Test func emptyingTheTextAppliesAtOnce() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: SamplePapers.all))
        await type("vaswani", into: viewModel)
        #expect(await eventually { viewModel.papers.count == 1 })

        viewModel.updateText("")

        #expect(viewModel.appliedQuery == "")
        #expect(await eventually { viewModel.papers.count == 3 })
        #expect(sleeper.pendingCount == 0)
    }

    @Test func aChipAndASearchCombine() async {
        let library = FakeLibraryRepository(saved: SamplePapers.all, statuses: [SamplePapers.bert.openAlexID: .reading])
        let viewModel = makeViewModel(library)
        await type("transf", into: viewModel)
        #expect(await eventually { viewModel.papers.count == 3 })

        viewModel.setStatusFilter(.reading)

        #expect(viewModel.status == .reading)
        #expect(await eventually {
            viewModel.state == .papers(
                [LibraryPaper(paper: SamplePapers.bert, status: .reading)],
                LibraryFilter(query: "transf", status: .reading, counts: counts(2, 1, 0))
            )
        })
    }

    @Test func noMatchesWhenTheLibraryHasPapersButNoneMatch() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: SamplePapers.all))
        await type("nothing like this", into: viewModel)

        #expect(await eventually {
            viewModel.state == .noMatches(LibraryFilter(query: "nothing like this", status: nil, counts: counts(0, 0, 0)))
        })

        viewModel.updateText("")
        viewModel.setStatusFilter(.read)
        #expect(await eventually { viewModel.state == .noMatches(LibraryFilter(query: "", status: .read, counts: counts(3, 0, 0))) })
    }

    @Test func clearSearchAndFiltersResetsBoth() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: SamplePapers.all))
        viewModel.setStatusFilter(.read)
        await type("vaswani", into: viewModel)
        #expect(await eventually { viewModel.appliedQuery == "vaswani" })
        #expect(await eventually { viewModel.filter?.counts == counts(1, 0, 0) })

        viewModel.clearSearchAndFilters()

        #expect(viewModel.text == "")
        #expect(viewModel.appliedQuery == "")
        #expect(viewModel.status == nil)
        #expect(await eventually { viewModel.papers.count == 3 })
    }

    /// The pause has ended but the debounced search hasn't run yet: Clear still wins.
    @Test func clearingRightAfterThePauseEndsKeepsTheSearchCleared() async throws {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: SamplePapers.all))
        #expect(await eventually { viewModel.papers.count == 3 })

        await type("vaswani", into: viewModel)
        viewModel.clearSearchAndFilters()
        try await Task.sleep(for: .milliseconds(50))

        #expect(viewModel.appliedQuery == "")
        #expect(viewModel.papers.count == 3)
    }

    @Test func restoresTheTextAndChipFromSceneStorageOnce() async {
        let library = FakeLibraryRepository(saved: SamplePapers.all, statuses: [SamplePapers.attention.openAlexID: .reading])
        let viewModel = makeViewModel(library)

        viewModel.restore(text: "attention", status: "reading")
        viewModel.restore(text: "", status: "")

        #expect(viewModel.text == "attention")
        #expect(viewModel.appliedQuery == "attention")
        #expect(viewModel.status == .reading)
        #expect(viewModel.storedStatus == "reading")
        #expect(await eventually { ids(viewModel) == [SamplePapers.attention.openAlexID] })
        #expect(sleeper.pendingCount == 0)
    }

    @Test func anUnknownStoredChipRestoresAll() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: SamplePapers.all))

        viewModel.restore(text: "", status: "archived")

        #expect(viewModel.status == nil)
        #expect(viewModel.storedStatus == "")
        #expect(await eventually { viewModel.papers.count == 3 })
    }

    @Test func aStatusChangeUpdatesTheListAndTheCounts() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: [SamplePapers.bert, SamplePapers.attention]))
        #expect(await eventually { viewModel.papers.count == 2 })

        await viewModel.setStatus(of: SamplePapers.attention, to: .reading)

        #expect(await eventually {
            viewModel.state == .papers([
                LibraryPaper(paper: SamplePapers.bert, status: .toRead),
                LibraryPaper(paper: SamplePapers.attention, status: .reading),
            ], LibraryFilter(query: "", status: nil, counts: counts(1, 1, 0)))
        })
    }

    @Test func aStatusChangeOutOfTheChipKeepsThePreviewOpen() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: [SamplePapers.bert, SamplePapers.attention]))
        viewModel.setStatusFilter(.toRead)
        #expect(await eventually { viewModel.papers.count == 2 })
        viewModel.select(SamplePapers.attention)
        #expect(await eventually { viewModel.selectedPaper != nil })

        await viewModel.setStatus(of: SamplePapers.attention, to: .read)

        #expect(await eventually { ids(viewModel) == [SamplePapers.bert.openAlexID] })
        #expect(await eventually { viewModel.selectedPaper == LibraryPaper(paper: SamplePapers.attention, status: .read) })
        #expect(viewModel.selectedPaperID == SamplePapers.attention.openAlexID)
    }

    @Test func aFailedStatusChangeShowsTheMessageAndKeepsTheStoredStatus() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        library.setFailStatusUpdates(true)
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.papers.count == 1 })

        await viewModel.setStatus(of: SamplePapers.attention, to: .read)

        #expect(viewModel.message == .statusUpdateFailed)
        #expect(viewModel.papers == [LibraryPaper(paper: SamplePapers.attention, status: .toRead)])
    }

    @Test func selectingOpensThePreviewAndDismissingClosesIt() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: [SamplePapers.attention]))
        #expect(await eventually { viewModel.isLoaded })

        viewModel.select(SamplePapers.attention)
        #expect(await eventually { viewModel.selectedPaper == LibraryPaper(paper: SamplePapers.attention, status: .toRead) })
        viewModel.selectedPaperID = nil
        #expect(viewModel.selectedPaper == nil)
    }

    @Test func removingOffersUndoAndClosesThePreview() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.bert, SamplePapers.attention])
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.papers.count == 2 })
        viewModel.select(SamplePapers.attention)

        await viewModel.remove(SamplePapers.attention)

        #expect(viewModel.selectedPaperID == nil)
        #expect(viewModel.pendingUndo?.paper == SamplePapers.attention)
        #expect(await eventually { ids(viewModel) == [SamplePapers.bert.openAlexID] })
    }

    @Test func theSheetClosesWhenThePaperDisappears() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.isLoaded })
        viewModel.select(SamplePapers.attention)

        _ = try await library.remove(openAlexID: SamplePapers.attention.openAlexID)

        #expect(await eventually { viewModel.selectedPaperID == nil })
        #expect(viewModel.selectedPaper == nil)
    }

    @Test func removingTheLastPaperDuringASearchShowsEmpty() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: [SamplePapers.attention]))
        viewModel.setStatusFilter(.toRead)
        await type("attention", into: viewModel)
        #expect(await eventually { viewModel.appliedQuery == "attention" && viewModel.papers.count == 1 })

        await viewModel.remove(SamplePapers.attention)

        #expect(await eventually { viewModel.state == .empty })
    }

    @Test func removingAndRestoringTheOnlyPaperNeverShowsNoMatches() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: [SamplePapers.attention]))
        viewModel.setStatusFilter(.toRead)
        await type("attention", into: viewModel)
        #expect(await eventually { viewModel.appliedQuery == "attention" && viewModel.papers.count == 1 })
        let recorder = recordStates(of: viewModel)

        await viewModel.remove(SamplePapers.attention)
        #expect(await eventually { viewModel.state == .empty })
        await viewModel.undo()
        #expect(await eventually { viewModel.papers.count == 1 })

        #expect(!recorder.states.isEmpty)
        #expect(!recorder.states.contains { if case .noMatches = $0 { true } else { false } })
    }

    @Test func undoRestoresThePaperInPlaceWithItsStatus() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.vit, SamplePapers.bert, SamplePapers.attention], statuses: [SamplePapers.bert.openAlexID: .reading])
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.papers.count == 3 })

        await viewModel.remove(SamplePapers.bert)
        #expect(await eventually { ids(viewModel) == [SamplePapers.vit.openAlexID, SamplePapers.attention.openAlexID] })
        await viewModel.undo()

        #expect(viewModel.pendingUndo == nil)
        #expect(await eventually {
            viewModel.papers == [
                LibraryPaper(paper: SamplePapers.vit, status: .toRead),
                LibraryPaper(paper: SamplePapers.bert, status: .reading),
                LibraryPaper(paper: SamplePapers.attention, status: .toRead),
            ]
        })
    }

    @Test func twoQuickRemovalsKeepOnlyTheLatest() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.vit, SamplePapers.bert, SamplePapers.attention])
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.papers.count == 3 })

        await viewModel.remove(SamplePapers.bert)
        await viewModel.remove(SamplePapers.vit)
        #expect(viewModel.pendingUndo?.paper == SamplePapers.vit)

        await viewModel.undo()
        #expect(await eventually { ids(viewModel) == [SamplePapers.vit.openAlexID, SamplePapers.attention.openAlexID] })
    }

    @Test func anExpiredUndoForgetsThePaper() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.isLoaded })

        await viewModel.remove(SamplePapers.attention)
        viewModel.undoExpired()
        await viewModel.undo()

        #expect(viewModel.pendingUndo == nil)
        #expect(library.savedPapers.isEmpty)
    }

    @Test func aFailedRemoveChangesNothing() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        library.setFailRemoves(true)
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.isLoaded })

        await viewModel.remove(SamplePapers.attention)

        #expect(viewModel.pendingUndo == nil)
        #expect(ids(viewModel) == [SamplePapers.attention.openAlexID])
    }
}

@MainActor
private final class StateRecorder {
    var states: [LibraryState] = []
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:FeatureLibraryTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL — `LibraryViewModelTests.swift:354:18: error: cannot find type 'LibraryState' in scope`, `LibraryViewModelTests.swift:12:59: error: extra argument 'sleep' in call`, `value of type 'LibraryViewModel' has no member 'updateText'`, then `** TEST FAILED **`.

- [ ] **Step 3: Implement the view model**

`ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift`:
```swift
import Foundation
import HashiyaData
import HashiyaModel
import Observation
import os

/// What the Library screen shows.
public enum LibraryState: Equatable, Sendable {
    /// Before the first snapshot.
    case loading
    /// Nothing saved; the search field and chips are hidden.
    case empty
    /// Papers are saved, but none match the search and the chip.
    case noMatches(LibraryFilter)
    case papers([LibraryPaper], LibraryFilter)
}

/// What the search field and the status chips show.
public struct LibraryFilter: Equatable, Sendable {
    /// The search text as typed.
    public var query: String
    /// The selected chip; nil is All.
    public var status: ReadingStatus?
    /// Papers matching the applied search per status; all three keys are present.
    public var counts: [ReadingStatus: Int]

    /// The All chip's count.
    public var total: Int { counts.values.reduce(0, +) }

    public init(query: String, status: ReadingStatus?, counts: [ReadingStatus: Int]) {
        self.query = query
        self.status = status
        self.counts = counts
    }
}

public enum LibraryMessage: Equatable, Sendable {
    case statusUpdateFailed
}

@Observable
@MainActor
public final class LibraryViewModel {
    /// `@SceneStorage` keys, as Android's `SavedStateHandle` keys.
    public static let queryKey = "library_query"
    public static let statusKey = "library_status"
    public static let debounce: Duration = .milliseconds(300)

    public private(set) var state: LibraryState = .loading {
        didSet { stateObserver?(state) }
    }
    /// Exactly what is in the search field.
    public private(set) var text = ""
    /// The search the list shows: `text` after the debounce, at once on the Search key or when the text is emptied.
    public private(set) var appliedQuery = ""
    /// The selected chip; nil is All.
    public private(set) var status: ReadingStatus?
    /// The paper in the preview sheet.
    public var selectedPaperID: String?
    /// The latest removal, which Undo can put back.
    public internal(set) var pendingUndo: RemovedPaper?
    public var message: LibraryMessage?

    /// Tests only: called with every new state.
    @ObservationIgnored var stateObserver: ((LibraryState) -> Void)?
    @ObservationIgnored private let library: any LibraryRepository
    @ObservationIgnored private let sleep: @Sendable (Duration) async throws -> Void
    @ObservationIgnored private let observations = TaskBag()
    @ObservationIgnored private let filterObservation = TaskSlot()
    @ObservationIgnored private var debounceTask: Task<Void, Never>?
    @ObservationIgnored private var hasRestored = false
    /// The whole library, for the preview: a status change that moves the paper out of the chip keeps the sheet open.
    private var allPapers: [LibraryPaper] = []

    public init(
        library: any LibraryRepository,
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) }
    ) {
        self.library = library
        self.sleep = sleep
        observations.add(Task { [weak self] in
            for await snapshot in library.observeLibrary(query: "", status: nil) {
                guard let self else { return }
                self.allPapers = snapshot.papers
                if let id = self.selectedPaperID, !snapshot.papers.contains(where: { $0.id == id }) {
                    self.selectedPaperID = nil
                }
            }
        })
        observeFilter()
    }

    /// False until the first snapshot arrives.
    public var isLoaded: Bool { state != .loading }

    /// The listed papers; empty unless the state is `.papers`.
    public var papers: [LibraryPaper] {
        if case let .papers(papers, _) = state { papers } else { [] }
    }

    /// The chips' counts and the typed search, in `.papers` and `.noMatches`.
    public var filter: LibraryFilter? {
        switch state {
        case let .papers(_, filter), let .noMatches(filter): filter
        case .loading, .empty: nil
        }
    }

    /// The selected paper with its current status; nil once it is gone.
    public var selectedPaper: LibraryPaper? {
        guard let id = selectedPaperID else { return nil }
        return allPapers.first { $0.id == id }
    }

    /// The chip as its `@SceneStorage` value: `toRead`, `reading`, `read`, or "" for All.
    public var storedStatus: String { status?.rawValue ?? "" }

    // MARK: Search and chips

    /// The field changed: search after the debounce; an emptied field applies at once.
    public func updateText(_ newText: String) {
        guard newText != text else { return }
        text = newText
        debounceTask?.cancel()
        if newText.isEmpty {
            apply(query: "")
            return
        }
        debounceTask = Task { [weak self, sleep] in
            do {
                try await sleep(Self.debounce)
            } catch {
                return
            }
            // Cancelled after the pause ended but before this ran (Search key, Clear): the newer text wins.
            guard !Task.isCancelled else { return }
            self?.apply(query: newText)
        }
    }

    /// The keyboard's Search key: search now instead of after the pause.
    public func submitNow() {
        debounceTask?.cancel()
        apply(query: text)
    }

    /// A chip: applies at once.
    public func setStatusFilter(_ newStatus: ReadingStatus?) {
        guard newStatus != status else { return }
        status = newStatus
        observeFilter()
    }

    /// No papers match's button.
    public func clearSearchAndFilters() {
        debounceTask?.cancel()
        text = ""
        let changed = !appliedQuery.isEmpty || status != nil
        appliedQuery = ""
        status = nil
        if changed { observeFilter() }
    }

    /// Restores the field and chip saved with the scene, once; the text applies at once. `status` is `storedStatus`'s
    /// format; anything else is All.
    public func restore(text restoredText: String, status restoredStatus: String) {
        guard !hasRestored else { return }
        hasRestored = true
        let restored = ReadingStatus(rawValue: restoredStatus)
        guard !restoredText.isEmpty || restored != nil else { return }
        debounceTask?.cancel()
        text = restoredText
        appliedQuery = restoredText
        status = restored
        observeFilter()
    }

    private func apply(query: String) {
        guard query != appliedQuery else { return }
        appliedQuery = query
        observeFilter()
    }

    /// Replaces the observation for the applied search and chip. The current state stays until the new first snapshot.
    private func observeFilter() {
        let query = appliedQuery
        let status = status
        filterObservation.replace(with: Task { [weak self, library] in
            for await snapshot in library.observeLibrary(query: query, status: status) {
                guard let self, !Task.isCancelled else { return }
                self.show(snapshot)
            }
        })
    }

    private func show(_ snapshot: LibrarySnapshot) {
        let filter = LibraryFilter(query: text, status: status, counts: snapshot.counts)
        if snapshot.libraryTotal == 0 {
            state = .empty
        } else if snapshot.papers.isEmpty {
            state = .noMatches(filter)
        } else {
            state = .papers(snapshot.papers, filter)
        }
    }

    // MARK: Status

    /// The badge's menu or the preview's selector. A failure shows a message; the stored status stays on screen.
    public func setStatus(of paper: Paper, to newStatus: ReadingStatus) async {
        do {
            try await library.setStatus(openAlexID: paper.openAlexID, status: newStatus)
        } catch {
            message = .statusUpdateFailed
        }
    }

    // MARK: Preview, remove and Undo

    public func select(_ paper: Paper) {
        selectedPaperID = paper.openAlexID
    }

    /// Closes the preview and removes the paper; only the latest removal can be undone.
    public func remove(_ paper: Paper) async {
        selectedPaperID = nil
        do {
            if let removed = try await library.remove(openAlexID: paper.openAlexID) {
                pendingUndo = removed
            }
        } catch {
            Self.log("remove failed")
        }
    }

    /// Puts the latest removed paper back in its place, with its status.
    public func undo() async {
        guard let removed = pendingUndo else { return }
        pendingUndo = nil
        do {
            try await library.restore(removed)
        } catch {
            Self.log("restore failed")
        }
    }

    /// The Undo banner timed out.
    public func undoExpired() {
        pendingUndo = nil
    }

    private static func log(_ message: StaticString) {
        #if DEBUG
        Logger(subsystem: "com.etatech.hashiya", category: "library").error("\(message)")
        #endif
    }
}

/// Holds one task at a time: a new one cancels the previous, and releasing the slot cancels the last.
private final class TaskSlot: Sendable {
    private let task = OSAllocatedUnfairLock<Task<Void, Never>?>(initialState: nil)

    func replace(with newTask: Task<Void, Never>) {
        task.withLock { current in
            current?.cancel()
            current = newTask
        }
    }

    deinit {
        task.withLock { $0?.cancel() }
    }
}
```

The current screen lists `LibraryPaper`s now (Task 6 redraws it):

```bash
python3 - <<'EOF'
import pathlib
p = pathlib.Path("ios/HashiyaKit/Sources/FeatureLibrary/LibraryView.swift")
s = p.read_text()
reps = [
('''        } else if viewModel.papers.isEmpty {''', '''        } else if viewModel.state == .empty {'''),
('''                LibraryRow(paper: paper)
                    .contentShape(Rectangle())
                    .onTapGesture { viewModel.select(paper) }''',
 '''                LibraryRow(paper: paper.paper)
                    .contentShape(Rectangle())
                    .onTapGesture { viewModel.select(paper.paper) }'''),
('''                            Task { await viewModel.remove(paper) }
                        } label: {''', '''                            Task { await viewModel.remove(paper.paper) }
                        } label: {'''),
('''                preview(paper)
            }''', '''                preview(paper.paper)
            }'''),
('''set: { viewModel.selectedPaperID = $0?.openAlexID }''', '''set: { viewModel.selectedPaperID = $0?.id }'''),
]
for a, b in reps:
    assert s.count(a) == 1, a
    s = s.replace(a, b)
p.write_text(s)
EOF
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:FeatureLibraryTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: `✔ Test run with 29 tests in 3 suites passed` and `** TEST SUCCEEDED **` (the screen looks as in plan 2, so existing local Library images still match; if they are missing, the first run records them and fails 3 snapshot tests with 12 issues, and the second passes).

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift ios/HashiyaKit/Sources/FeatureLibrary/LibraryView.swift \
  ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryViewModelTests.swift
git commit -m "feat: search and filter the iOS library by status in its view model"
```

---
### Task 6: Library screen — search field, status chips with counts, badges with a menu, No papers match, snapshots and the UI test

**Files:**
- Create: `ios/HashiyaKit/Sources/FeatureLibrary/LibraryFilterChips.swift`, `ios/HashiyaKit/Sources/FeatureLibrary/ReadingStatusBadge.swift`
- Modify: `ios/HashiyaKit/Sources/FeatureLibrary/L10n.swift`, `LibraryView.swift` (full replacement), `Resources/Localizable.xcstrings`
- Test: Modify `ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryStringsTests.swift`, `LibrarySnapshotTests.swift`, `ios/HashiyaUITests/LibraryFlowTests.swift`

**Interfaces:**
- Consumes: Task 5's `LibraryViewModel`; Task 4's `readingStatusLabel`, `PaperPreviewContent(status:onStatusChange:…)`; plan 1–2's `LibraryView.init(viewModel:onGoToSearch:onAddPaper:onOpenSettings:)` (unchanged, so `RootView` needs no change), `HashiyaBanner`, `EmptyStateView`, `PaperFormat.number`, the `-ui-testing` stub search (Attention, BERT, ViT).
- Produces: `L10n.filterCount(_ label: String, _ count: Int) -> String`, `L10n.statusBadgeDescription(_ status: ReadingStatus) -> String`; internal views `ReadingStatusBadge(status:onChange:)`, `ReadingStatusPill(status:)`, `LibraryFilterChips(selected:counts:onSelect:)`; strings `library.searchHint`, `library.filterAll`, `library.filterCount`, `library.statusBadgeDescription`, `library.noMatchesTitle`, `library.noMatchesAction`, `library.statusUpdateFailed`.

`.papers` and `.noMatches` render inside the same `filtered` container with the same `.searchable` and chip row, so the field is not rebuilt while typing into a search that stops matching (the UI test checks the keyboard is still up). The row is an `HStack` of the tappable text and the badge's `Menu` with `.buttonStyle(.borderless)`, so the badge never opens the preview.

- [ ] **Step 1: Write the failing tests**

`ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryStringsTests.swift`:
```swift
@testable import FeatureLibrary
import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct LibraryStringsTests {
    private func inLanguage<T>(_ language: String, _ body: () -> T) -> T {
        let previous = HashiyaLanguage.override
        HashiyaLanguage.override = language
        defer { HashiyaLanguage.override = previous }
        return body()
    }

    @Test func paperCountIsPlural() {
        #expect(inLanguage("en") { L10n.paperCount(1) } == "1 paper")
        #expect(inLanguage("en") { L10n.paperCount(3) } == "3 papers")
        #expect(inLanguage("ar") { L10n.paperCount(0) } == "لا توجد أوراق")
        #expect(inLanguage("ar") { L10n.paperCount(2) } == "ورقتان")
        #expect(inLanguage("ar") { L10n.paperCount(3) } == "\u{2068}3\u{2069} أوراق")
    }

    @Test func rowMetaShowsTheFirstAuthorYearAndVenue() {
        #expect(inLanguage("en") { L10n.rowMeta(SamplePapers.attention) } == "Ashish Vaswani et al. · 2017 · Neural Information Processing Systems")
        #expect(inLanguage("en") { L10n.rowMeta(SamplePapers.arabicTitled) } == "محمد علي · 2022")
        #expect(inLanguage("en") { L10n.rowMeta(SamplePapers.untitled) } == "")
    }

    /// "·" beside Arabic digits reads like "٠", so Arabic puts the count in parentheses; Arabic formatting also isolates
    /// each argument (U+2068 … U+2069).
    @Test func chipsShowTheirCount() {
        #expect(inLanguage("en") { L10n.filterCount(readingStatusLabel(.reading), 3) } == "Reading · 3")
        #expect(inLanguage("en") { L10n.filterCount(L10n.string("library.filterAll"), 1_234) } == "All · 1,234")
        #expect(inLanguage("ar") { L10n.filterCount(readingStatusLabel(.reading), 3) } == "\u{2068}قيد القراءة\u{2069} (\u{2068}3\u{2069})")
    }

    @Test func theBadgeDescribesTheStatusAndTheAction() {
        #expect(inLanguage("en") { L10n.statusBadgeDescription(.toRead) } == "Status: To read. Change status")
        #expect(inLanguage("ar") { L10n.statusBadgeDescription(.read) } == "الحالة: \u{2068}مقروءة\u{2069}. تغيير الحالة")
    }
}
```

`ios/HashiyaKit/Tests/FeatureLibraryTests/LibrarySnapshotTests.swift`:
```swift
@testable import FeatureLibrary
import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import SwiftUI
import Testing

@MainActor
@Suite(.serialized)
struct LibrarySnapshotTests {
    private let sleeper = ManualSleeper()

    private func screen(_ viewModel: LibraryViewModel) -> some View {
        NavigationStack {
            LibraryView(viewModel: viewModel, onGoToSearch: {}, onAddPaper: {}, onOpenSettings: {})
        }
    }

    /// Attention (Reading), an Arabic title (Read), ViT and an untitled paper (To read).
    private func library() -> FakeLibraryRepository {
        FakeLibraryRepository(
            saved: [SamplePapers.attention, SamplePapers.arabicTitled, SamplePapers.vit, SamplePapers.untitled],
            statuses: [SamplePapers.attention.openAlexID: .reading, SamplePapers.arabicTitled.openAlexID: .read]
        )
    }

    @Test func empty() async {
        let viewModel = LibraryViewModel(library: FakeLibraryRepository())
        _ = await eventually { viewModel.isLoaded }
        assertHashiyaSnapshots(of: screen(viewModel), named: "empty", arabicText: "الذهاب إلى البحث")
    }

    @Test func papersWithChipsAndBadges() async {
        let viewModel = LibraryViewModel(library: library())
        _ = await eventually { viewModel.papers.count == 4 }
        assertHashiyaSnapshots(of: screen(viewModel), named: "papers", arabicText: "قيد القراءة")
    }

    @Test func aFilteredSearch() async {
        let viewModel = LibraryViewModel(library: library(), sleep: sleeper.sleep)
        viewModel.setStatusFilter(.toRead)
        viewModel.updateText("transformers")
        viewModel.submitNow()
        _ = await eventually { viewModel.papers.count == 1 }
        assertHashiyaSnapshots(of: screen(viewModel), named: "filtered", arabicText: "ابحث في مكتبتك")
    }

    @Test func noMatches() async {
        let viewModel = LibraryViewModel(library: library(), sleep: sleeper.sleep)
        viewModel.updateText("quantum")
        viewModel.submitNow()
        _ = await eventually { viewModel.state != .loading && viewModel.papers.isEmpty }
        assertHashiyaSnapshots(of: screen(viewModel), named: "noMatches", arabicText: "مسح البحث والفلاتر")
    }

    /// The badge's three styles. Its open menu is a system popup outside the view, which a view snapshot can't
    /// capture; `LibraryFlowTests.testChangingAStatusFiltersAndSearchesTheLibrary` checks its items and selection.
    @Test func statusBadges() {
        let badges = VStack(alignment: .leading, spacing: 12) {
            ForEach(ReadingStatus.allCases, id: \.self) { status in
                ReadingStatusBadge(status: status) { _ in }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(HashiyaColors.surface)
        assertHashiyaSnapshots(of: badges, named: "badges", arabicText: "مقروءة")
    }

    @Test func undoBanner() async {
        let viewModel = LibraryViewModel(library: FakeLibraryRepository(saved: [SamplePapers.attention, SamplePapers.bert]))
        _ = await eventually { viewModel.papers.count == 2 }
        await viewModel.remove(SamplePapers.bert)
        _ = await eventually { viewModel.papers.count == 1 }
        assertHashiyaSnapshots(of: screen(viewModel), named: "undo", arabicText: "تمت الإزالة من المكتبة")
    }

    @Test func statusUpdateFailedBanner() async {
        let library = library()
        library.setFailStatusUpdates(true)
        let viewModel = LibraryViewModel(library: library)
        _ = await eventually { viewModel.papers.count == 4 }
        await viewModel.setStatus(of: SamplePapers.vit, to: .read)
        assertHashiyaSnapshots(of: screen(viewModel), named: "statusFailed", arabicText: "تعذّر تحديث الحالة")
    }
}
```

`LibraryFlowTests.swift` gains the status and search flows (full file):

`ios/HashiyaUITests/LibraryFlowTests.swift`:
```swift
import XCTest

/// End to end with `-ui-testing`: in-memory library, stub search, no network.
final class LibraryFlowTests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-ui-testing", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
    }

    @MainActor
    func testSaveFromSearchThenRemoveAndUndoInLibrary() {
        XCTAssertTrue(app.staticTexts["No saved papers yet"].waitForExistence(timeout: 10))
        app.buttons["Go to Search"].tap()

        let field = app.searchFields["Search, or paste a DOI, arXiv ID or link"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText("attention\n")
        XCTAssertTrue(app.staticTexts["About 3 results"].waitForExistence(timeout: 5))
        app.buttons["Save"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["In library"].waitForExistence(timeout: 5))

        app.tabBars.buttons["Library"].tap()
        let row = app.cells.containing(NSPredicate(format: "label BEGINSWITH %@", "Attention Is All You Need")).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["1 paper"].exists)

        row.swipeLeft()
        app.buttons["Remove"].tap()
        XCTAssertTrue(app.staticTexts["Removed from library"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["No saved papers yet"].waitForExistence(timeout: 5))

        app.buttons["Undo"].tap()
        XCTAssertTrue(row.waitForExistence(timeout: 5))
    }

    @MainActor
    func testPastingAnArxivIDShowsThePaperToSave() {
        app.tabBars.buttons["Search"].tap()
        let field = app.searchFields["Search, or paste a DOI, arXiv ID or link"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText("arXiv:1706.03762\n")

        XCTAssertTrue(app.staticTexts["Attention Is All You Need"].waitForExistence(timeout: 5))
        app.buttons["Save to library"].tap()
        XCTAssertTrue(app.buttons["Remove from library"].waitForExistence(timeout: 5))

        app.tabBars.buttons["Library"].tap()
        XCTAssertTrue(app.staticTexts["1 paper"].waitForExistence(timeout: 5))
    }

    @MainActor
    func testAddPaperOpensSearchReadyForInput() {
        XCTAssertTrue(app.buttons["Add paper"].waitForExistence(timeout: 10))

        app.buttons["Add paper"].tap()

        XCTAssertTrue(app.tabBars.buttons["Search"].isSelected)
        let field = app.searchFields["Search, or paste a DOI, arXiv ID or link"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        let focused = expectation(for: NSPredicate(format: "hasKeyboardFocus == true"), evaluatedWith: field)
        wait(for: [focused], timeout: 5)
        XCTAssertEqual(field.value as? String, "Search, or paste a DOI, arXiv ID or link")
    }

    /// Saves the stub search's first two papers: Attention, then BERT (the newest, listed first).
    @MainActor
    private func saveTwoPapers() {
        app.tabBars.buttons["Search"].tap()
        let field = app.searchFields["Search, or paste a DOI, arXiv ID or link"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText("attention\n")
        XCTAssertTrue(app.staticTexts["About 3 results"].waitForExistence(timeout: 5))
        app.buttons["Save"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["In library"].waitForExistence(timeout: 5))
        app.buttons["Save"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts.matching(identifier: "In library").element(boundBy: 1).waitForExistence(timeout: 5))
        app.tabBars.buttons["Library"].tap()
        XCTAssertTrue(app.staticTexts["2 papers"].waitForExistence(timeout: 5))
    }

    private func row(_ titlePrefix: String) -> XCUIElement {
        app.cells.containing(NSPredicate(format: "label BEGINSWITH %@", titlePrefix)).firstMatch
    }

    @MainActor
    func testChangingAStatusFiltersAndSearchesTheLibrary() {
        saveTwoPapers()
        XCTAssertTrue(app.buttons["All · 2"].isSelected)
        XCTAssertTrue(app.buttons["Reading · 0"].exists)

        // The badge's menu lists the three statuses with the current one checked.
        row("Attention Is All You Need").buttons["Status: To read. Change status"].tap()
        XCTAssertTrue(app.buttons["Reading"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.segmentedControls.firstMatch.exists, "Tapping the badge must not open the preview")
        XCTAssertTrue(app.buttons["To read"].isSelected)
        XCTAssertFalse(app.buttons["Reading"].isSelected)
        XCTAssertFalse(app.buttons["Read"].isSelected)
        app.buttons["Reading"].tap()

        XCTAssertTrue(app.buttons["Reading · 1"].waitForExistence(timeout: 5))
        XCTAssertTrue(row("Attention Is All You Need").buttons["Status: Reading. Change status"].exists)
        app.buttons["Reading · 1"].tap()
        XCTAssertTrue(app.staticTexts["1 paper"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Reading · 1"].isSelected)
        XCTAssertFalse(row("BERT").exists)

        // A word from the other paper's title: No papers match, with the keyboard still up while typing.
        let field = app.searchFields["Search your library"]
        field.tap()
        field.typeText("bert")
        XCTAssertTrue(app.staticTexts["No papers match"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.keyboards.firstMatch.exists)

        app.buttons["Clear search and filters"].tap()
        XCTAssertTrue(app.staticTexts["2 papers"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["All · 2"].isSelected)
        XCTAssertTrue(row("BERT").exists)
        XCTAssertTrue(row("Attention Is All You Need").exists)
    }

    @MainActor
    func testThePreviewChangesTheStatusAndTheSearchKeyHidesTheKeyboard() {
        saveTwoPapers()

        row("BERT").buttons.firstMatch.tap()
        let read = app.segmentedControls.buttons["Read"]
        XCTAssertTrue(read.waitForExistence(timeout: 5))
        XCTAssertTrue(app.segmentedControls.buttons["To read"].isSelected)
        read.tap()
        XCTAssertTrue(read.isSelected)
        app.swipeDown(velocity: .fast)
        XCTAssertTrue(app.buttons["Read · 1"].waitForExistence(timeout: 5))
        XCTAssertTrue(row("BERT").buttons["Status: Read. Change status"].exists)

        let field = app.searchFields["Search your library"]
        field.tap()
        field.typeText("vaswani\n")
        XCTAssertTrue(app.staticTexts["1 paper"].waitForExistence(timeout: 5))
        XCTAssertTrue(row("Attention Is All You Need").exists)
        let hidden = expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: app.keyboards.firstMatch)
        wait(for: [hidden], timeout: 5)
    }

    @MainActor
    func testSettingsSavesAndResetsTheUserKey() {
        app.buttons["Settings"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Using built-in key"].waitForExistence(timeout: 5))

        let field = app.secureTextFields["API key"]
        field.tap()
        field.typeText("my-key")
        app.buttons["Save"].tap()
        XCTAssertTrue(app.staticTexts["Using your key"].waitForExistence(timeout: 5))

        app.buttons["Reset to built-in"].tap()
        XCTAssertTrue(app.staticTexts["Using built-in key"].waitForExistence(timeout: 5))
        app.buttons["Done"].tap()
        XCTAssertTrue(app.tabBars.buttons["Library"].waitForExistence(timeout: 5))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:FeatureLibraryTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL — `LibraryStringsTests.swift:33:41: error: type 'L10n' has no member 'filterCount'` and `LibraryStringsTests.swift:39:41: error: type 'L10n' has no member 'statusBadgeDescription'`, then `** TEST FAILED **`.

- [ ] **Step 3: Implement the strings, chips, badge and screen**

```bash
python3 - <<'EOF'
import json, pathlib
path = pathlib.Path("ios/HashiyaKit/Sources/FeatureLibrary/Resources/Localizable.xcstrings")
catalog = json.loads(path.read_text(encoding="utf-8"))
for key, en, ar in [
    ("library.searchHint", "Search your library", "ابحث في مكتبتك"),
    ("library.filterAll", "All", "الكل"),
    ("library.filterCount", "%1$@ · %2$@", "%1$@ (%2$@)"),
    ("library.statusBadgeDescription", "Status: %1$@. Change status", "الحالة: %1$@. تغيير الحالة"),
    ("library.noMatchesTitle", "No papers match", "لا توجد أوراق مطابقة"),
    ("library.noMatchesAction", "Clear search and filters", "مسح البحث والفلاتر"),
    ("library.statusUpdateFailed", "Couldn't update the status", "تعذّر تحديث الحالة"),
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

`ios/HashiyaKit/Sources/FeatureLibrary/L10n.swift`:
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

    /// "3 papers".
    static func paperCount(_ count: Int) -> String {
        format("library.paperCount", Int64(count), PaperFormat.number(count))
    }

    /// A chip: "Reading · 3"; in Arabic "قيد القراءة (3)", in the locale's digits.
    static func filterCount(_ label: String, _ count: Int) -> String {
        format("library.filterCount", label, PaperFormat.number(count))
    }

    /// The badge for VoiceOver: "Status: To read. Change status".
    static func statusBadgeDescription(_ status: ReadingStatus) -> String {
        format("library.statusBadgeDescription", readingStatusLabel(status))
    }

    /// First author (alone when there is exactly one, else "et al."), year and venue.
    static func rowMeta(_ paper: Paper) -> String {
        let author: String? = switch paper.authors.count {
        case 0: nil
        case 1: paper.authors[0].name
        default: format("library.etAl", paper.authors[0].name)
        }
        return [author, paper.year.map(PaperFormat.year), paper.venue].compactMap { $0 }.joined(separator: " · ")
    }
}
```

`ios/HashiyaKit/Sources/FeatureLibrary/ReadingStatusBadge.swift`:
```swift
import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// A row's status as a labelled pill that opens a menu of the three statuses (the current one checked).
/// Choosing another status calls `onChange` at once; choosing the current one does nothing.
struct ReadingStatusBadge: View {
    let status: ReadingStatus
    let onChange: (ReadingStatus) -> Void

    var body: some View {
        Menu {
            Picker(selection: Binding(get: { status }, set: { newValue in
                if newValue != status { onChange(newValue) }
            })) {
                ForEach(ReadingStatus.allCases, id: \.self) { status in
                    Text(verbatim: readingStatusLabel(status)).tag(status)
                }
            } label: {
                EmptyView()
            }
        } label: {
            ReadingStatusPill(status: status)
        }
        .accessibilityLabel(Text(verbatim: L10n.statusBadgeDescription(status)))
    }
}

/// To read: outlined. Reading: filled with the primary container. Read: filled, with a check. The label always
/// names the status, so it is never told by colour alone.
struct ReadingStatusPill: View {
    let status: ReadingStatus

    var body: some View {
        HStack(spacing: 4) {
            if status == .read {
                Image(systemName: "checkmark")
                    .font(.system(size: 14 * 0.8, weight: .semibold))
                    .frame(width: 14, height: 14)
            }
            Text(verbatim: readingStatusLabel(status))
                .font(.hashiya(.label))
                .lineLimit(1)
        }
        .foregroundStyle(foreground)
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background {
            if status == .toRead {
                Self.shape.strokeBorder(HashiyaColors.outline, lineWidth: 1)
            } else {
                Self.shape.fill(fill)
            }
        }
        .contentShape(Self.shape)
        .fixedSize()
    }

    /// A pill: just under half the default height. (A `Capsule`'s outline renders with seams in layer snapshots.)
    private static let shape = RoundedRectangle(cornerRadius: 11, style: .continuous)

    private var foreground: Color {
        switch status {
        case .toRead: HashiyaColors.onSurfaceVariant
        case .reading: HashiyaColors.onPrimaryContainer
        case .read: HashiyaColors.onSurface
        }
    }

    private var fill: Color {
        status == .reading ? HashiyaColors.primaryContainer : HashiyaColors.surfaceContainerHighest
    }
}
```

`ios/HashiyaKit/Sources/FeatureLibrary/LibraryFilterChips.swift`:
```swift
import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// All · To read · Reading · Read with their counts for the current search, in a horizontally scrolling row.
/// One is selected at a time: it is filled, carries a check and is announced as selected.
struct LibraryFilterChips: View {
    let selected: ReadingStatus?
    let counts: [ReadingStatus: Int]
    let onSelect: (ReadingStatus?) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chip(nil, label: L10n.string("library.filterAll"), count: counts.values.reduce(0, +))
                ForEach(ReadingStatus.allCases, id: \.self) { status in
                    chip(status, label: readingStatusLabel(status), count: counts[status] ?? 0)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
        }
    }

    private func chip(_ status: ReadingStatus?, label: String, count: Int) -> some View {
        let isSelected = status == selected
        return Button {
            onSelect(status)
        } label: {
            HStack(spacing: 4) {
                if isSelected {
                    Image(systemName: "checkmark").font(.system(size: 11, weight: .semibold))
                }
                Text(verbatim: L10n.filterCount(label, count)).font(.hashiya(.label))
            }
            .foregroundStyle(isSelected ? HashiyaColors.onPrimaryContainer : HashiyaColors.onSurface)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(RoundedRectangle(cornerRadius: 8).fill(isSelected ? HashiyaColors.primaryContainer : Color.clear))
            .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(isSelected ? Color.clear : HashiyaColors.outline, lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
```

`ios/HashiyaKit/Sources/FeatureLibrary/LibraryView.swift`:
```swift
import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// The Library tab's screen. Put it in a `NavigationStack`. Works offline.
public struct LibraryView: View {
    @Bindable private var viewModel: LibraryViewModel
    private let onGoToSearch: () -> Void
    private let onAddPaper: () -> Void
    private let onOpenSettings: () -> Void

    /// Space under the list's last row, so the Add paper button never covers it.
    static let addPaperClearance: CGFloat = 88

    @SceneStorage(LibraryViewModel.queryKey) private var storedQuery = ""
    @SceneStorage(LibraryViewModel.statusKey) private var storedStatus = ""
    @Environment(\.openURL) private var openURL

    /// - Parameter onAddPaper: the Add paper button; the app opens Search ready for input.
    public init(
        viewModel: LibraryViewModel,
        onGoToSearch: @escaping () -> Void,
        onAddPaper: @escaping () -> Void,
        onOpenSettings: @escaping () -> Void
    ) {
        self.viewModel = viewModel
        self.onGoToSearch = onGoToSearch
        self.onAddPaper = onAddPaper
        self.onOpenSettings = onOpenSettings
    }

    public var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(HashiyaColors.surface)
            .overlay(alignment: .bottom) {
                // The banners sit above the Add paper button (bottom trailing; bottom left in Arabic).
                VStack(alignment: .trailing, spacing: 0) {
                    if viewModel.message == .statusUpdateFailed {
                        HashiyaBanner(text: L10n.string("library.statusUpdateFailed"))
                    }
                    if viewModel.pendingUndo != nil {
                        HashiyaBanner(text: L10n.string("library.removed"), actionTitle: L10n.string("library.undo")) {
                            Task { await viewModel.undo() }
                        }
                    }
                    if viewModel.isLoaded {
                        AddPaperButton(action: onAddPaper)
                            .padding(.horizontal, 16)
                            .padding(.bottom, 16)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .animation(.default, value: viewModel.pendingUndo)
            .animation(.default, value: viewModel.message)
            .task(id: viewModel.pendingUndo) {
                // A newer removal cancels this task and restarts the 4 s.
                guard viewModel.pendingUndo != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                viewModel.undoExpired()
            }
            .task(id: viewModel.message) {
                // A newer message cancels this task: then it must not clear the new one.
                guard viewModel.message != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                viewModel.message = nil
            }
            .navigationTitle(Text(verbatim: L10n.string("library.title")))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onOpenSettings) {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel(Text(verbatim: L10n.string("library.settings")))
                }
            }
            .sheet(item: Binding(get: { viewModel.selectedPaper }, set: { viewModel.selectedPaperID = $0?.id })) { saved in
                preview(saved)
            }
            .onAppear { viewModel.restore(text: storedQuery, status: storedStatus) }
            .onChange(of: viewModel.text) { _, text in storedQuery = text }
            .onChange(of: viewModel.status) { _, _ in storedStatus = viewModel.storedStatus }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .loading:
            LoadingSkeleton(rows: 4)
        case .empty:
            EmptyStateView(
                icon: "books.vertical",
                title: L10n.string("library.emptyTitle"),
                message: L10n.string("library.emptyMessage"),
                actionTitle: L10n.string("library.goToSearch")
            ) {
                onGoToSearch()
            }
        case .papers, .noMatches:
            filtered
        }
    }

    /// Papers and No papers match share this container, its search field and its chips, so moving between them
    /// never rebuilds the field and the keyboard stays up while typing.
    private var filtered: some View {
        FilteredContent(state: viewModel.state, list: list, noMatches: noMatches)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .safeAreaInset(edge: .top, spacing: 0) {
                LibraryFilterChips(
                    selected: viewModel.status,
                    counts: viewModel.filter?.counts ?? [:],
                    onSelect: { viewModel.setStatusFilter($0) }
                )
                .background(HashiyaColors.surface)
            }
            .searchable(
                text: Binding(get: { viewModel.text }, set: { viewModel.updateText($0) }),
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: Text(verbatim: L10n.string("library.searchHint"))
            )
            .onSubmit(of: .search) { viewModel.submitNow() }
    }

    private var noMatches: some View {
        EmptyStateView(
            icon: "doc.text.magnifyingglass",
            title: L10n.string("library.noMatchesTitle"),
            actionTitle: L10n.string("library.noMatchesAction")
        ) {
            viewModel.clearSearchAndFilters()
        }
    }

    private var list: some View {
        List {
            Text(verbatim: L10n.paperCount(viewModel.papers.count))
                .font(.hashiya(.label))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .listRowSeparator(.hidden)
                .listRowBackground(HashiyaColors.surface)
            ForEach(viewModel.papers) { saved in
                HStack(alignment: .top, spacing: 12) {
                    LibraryRow(paper: saved.paper)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                        .onTapGesture { viewModel.select(saved.paper) }
                        .accessibilityAddTraits(.isButton)
                    ReadingStatusBadge(status: saved.status) { status in
                        Task { await viewModel.setStatus(of: saved.paper, to: status) }
                    }
                    .padding(.top, 4)
                }
                // Keeps the badge's menu and the row's tap separate: tapping the badge never opens the preview.
                .buttonStyle(.borderless)
                .listRowBackground(HashiyaColors.surface)
                .listRowSeparatorTint(HashiyaColors.outlineVariant)
                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                    Button(role: .destructive) {
                        Task { await viewModel.remove(saved.paper) }
                    } label: {
                        Label {
                            Text(verbatim: L10n.string("library.remove"))
                        } icon: {
                            Image(systemName: "trash")
                        }
                    }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .scrollDismissesKeyboard(.immediately)
        .contentMargins(.bottom, Self.addPaperClearance, for: .scrollContent)
    }

    private func preview(_ saved: LibraryPaper) -> some View {
        PaperPreviewContent(
            paper: saved.paper,
            inLibrary: true,
            status: saved.status,
            onStatusChange: { status in Task { await viewModel.setStatus(of: saved.paper, to: status) } },
            onToggleSave: { Task { await viewModel.remove(saved.paper) } },
            onOpenDOI: { doi in
                if let url = DOILink.url(for: doi) { openURL(url) }
            }
        )
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}

/// The list, or No papers match, inside one view so their shared container keeps its identity.
private struct FilteredContent<List: View, NoMatches: View>: View {
    let state: LibraryState
    let list: List
    let noMatches: NoMatches

    var body: some View {
        if case .papers = state {
            list
        } else {
            noMatches
        }
    }
}

/// The floating "+ Add paper" capsule.
private struct AddPaperButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label {
                Text(verbatim: L10n.string("library.addPaper"))
            } icon: {
                Image(systemName: "plus")
            }
            .font(.hashiya(.label))
            .foregroundStyle(HashiyaColors.onPrimary)
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
        }
        .buttonStyle(.borderedProminent)
        .buttonBorderShape(.capsule)
        .tint(HashiyaColors.primary)
        .shadow(color: .black.opacity(0.15), radius: 6, y: 2)
    }
}

/// Title (two lines at most) and one meta line.
private struct LibraryRow: View {
    let paper: Paper

    var body: some View {
        let meta = L10n.rowMeta(paper)
        VStack(alignment: .leading, spacing: 4) {
            PaperText(PaperFormat.title(paper), style: .cardTitle, lineLimit: 2)
            if !meta.isEmpty {
                PaperText(meta, style: .meta, color: HashiyaColors.onSurfaceVariant, lineLimit: 1)
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
rm -rf ios/HashiyaKit/Tests/FeatureLibraryTests/__Snapshots__/LibrarySnapshotTests
```

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:FeatureLibraryTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected on the first run: 28 tests pass; the seven snapshot tests each fail with 4 `No reference was found on disk. Automatically recorded snapshot: …` issues (`✘ Test run with 35 tests in 3 suites failed … with 28 issues`).

Run the same command again.
Expected: `✔ Test run with 35 tests in 3 suites passed` and `** TEST SUCCEEDED **`. Check `papersWithChipsAndBadges.papers-EnglishLight.png` (chips `✓ All · 4`, `To read · 2`, `Reading · 1`, `Read · 1`; the outlined, filled and checked badges), `aFilteredSearch.filtered-ArabicDark.png` (chips mirrored, `(1) للقراءة ✓` selected, badge on the left) and `noMatches.noMatches-EnglishDark.png` (the field and chips above No papers match).

Run: `xcodegen generate --spec ios/project.yml && xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -collect-test-diagnostics never -only-testing:HashiyaUITests/LibraryFlowTests 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: `Executed 6 tests, with 0 failures` and `** TEST SUCCEEDED **` (about 95 s).

- [ ] **Step 5: Commit (without the local snapshot images)**

```bash
git add ios/HashiyaKit/Sources/FeatureLibrary/Resources/Localizable.xcstrings ios/HashiyaKit/Sources/FeatureLibrary/L10n.swift \
  ios/HashiyaKit/Sources/FeatureLibrary/ReadingStatusBadge.swift ios/HashiyaKit/Sources/FeatureLibrary/LibraryFilterChips.swift \
  ios/HashiyaKit/Sources/FeatureLibrary/LibraryView.swift ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryStringsTests.swift \
  ios/HashiyaKit/Tests/FeatureLibraryTests/LibrarySnapshotTests.swift ios/HashiyaUITests/LibraryFlowTests.swift
git commit -m "feat: add iOS Library search, status chips and status badges"
```

---
### Task 7: READMEs, full verification, CI baselines and device checks

**Files:**
- Modify: `ios/README.md`, `README.md`
- Create (from CI, Step 4): `ios/HashiyaKit/Tests/*/__Snapshots__/**.png`

**Interfaces:**
- Consumes: everything above; plan 1's `ios/scripts/check-translations.py`, `ios/scripts/record-snapshots-on-ci.sh`, `.github/workflows/ios.yml`, `ios-record-snapshots.yml`.
- Produces: documentation only. The workflows need no change: `ios.yml` runs every test of the `Hashiya` scheme on any change under `ios/` (no new test targets), and the recording workflow already skips the UI tests.

- [ ] **Step 1: Update the READMEs**

```bash
python3 - <<'EOF'
import pathlib
p = pathlib.Path("ios/README.md")
s = p.read_text()
old = "a preview sheet, an offline Library and Settings."
new = "a preview sheet, an offline Library you can search by title, author, abstract or venue (Arabic search ignores tashkeel and letter variants) and track as To read, Reading or Read, and Settings."
assert s.count(old) == 1
s = s.replace(old, new)
old = "the app refreshes its Library whenever it comes to the foreground."
new = "the app refreshes its Library whenever it comes to the foreground. The database is migrated in place with GRDB migrations (`v1`, then `v2` for the reading status and the full-text index); there is no destructive fallback."
assert s.count(old) == 1
p.write_text(s.replace(old, new))
p = pathlib.Path("README.md")
s = p.read_text()
old = "A native SwiftUI app with the features of sub-projects 1 and 2 lives in [`ios/`](ios/README.md): OpenAlex search with filters, adding a paper by DOI, arXiv ID or link, a Share Extension that saves the paper of a shared page, the preview sheet, the offline Library and Settings, in English and Arabic."
new = "A native SwiftUI app with the features of sub-projects 1 to 3 lives in [`ios/`](ios/README.md): OpenAlex search with filters, adding a paper by DOI, arXiv ID or link, a Share Extension that saves the paper of a shared page, the preview sheet, the offline Library with full-text search and reading status, and Settings, in English and Arabic."
assert s.count(old) == 1
p.write_text(s.replace(old, new))
EOF
```

- [ ] **Step 2: Run the full verification**

Run: `python3 ios/scripts/check-translations.py && xcodegen generate --spec ios/project.yml && xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -collect-test-diagnostics never 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: `All 8 String Catalogs have Arabic translations`; then eight Swift Testing runs — `✔ Test run with 30 tests in 7 suites` (model), `45 tests in 6 suites` (network), `26 tests in 3 suites` (database), `70 tests in 11 suites` (data), `20 tests in 5 suites` (design system), `83 tests in 8 suites` (search), `35 tests in 3 suites` (library), `7 tests in 2 suites` (settings), 316 tests in all — then `Executed 8 tests, with 0 failures` (UI: launch, six Library flows, share flow) and `** TEST SUCCEEDED **`. About three minutes on a warm build.

- [ ] **Step 3: Commit**

```bash
git add ios/README.md README.md
git commit -m "docs: describe iOS library search and reading status in the READMEs"
```

- [ ] **Step 4: Record the baselines on CI and let CI verify them**

Local images are not baselines. Delete them all (plan 2's committed Library and design-system baselines change: the search field, chips, badges and the preview), push, and record on the pinned runner (15–25 minutes; run it in the background):
```bash
rm -rf ios/HashiyaKit/Tests/*/__Snapshots__
git push -u origin feat/ios-library-search
bash ios/scripts/record-snapshots-on-ci.sh
git status --short
```
Expected: `Baselines copied from run <id>: 140 images` — `HashiyaDesignSystemTests` 24, `FeatureSearchTests` 80, `FeatureLibraryTests` 28, `FeatureSettingsTests` 8. Open `FeatureLibraryTests/__Snapshots__/LibrarySnapshotTests/papersWithChipsAndBadges.papers-ArabicLight.png`, `statusBadges.badges-EnglishDark.png` and `HashiyaDesignSystemTests/__Snapshots__/DesignSystemSnapshotTests/previewWithTheStatusSelector.previewStatus-EnglishLight.png` before committing.

```bash
git add -A -- ':(glob)ios/HashiyaKit/Tests/*/__Snapshots__/**'
git commit -m "test: record the iOS snapshot baselines for library search and reading status on CI"
git push
run_id=$(gh run list --branch feat/ios-library-search --workflow ios.yml --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$run_id" --exit-status
```
Expected: the `iOS` workflow's `test` job succeeds (translations check, 316 package tests with every snapshot verified against the CI baselines, 8 UI tests on iPhone 16 / iOS 18.5). If only snapshots fail, download `ios-snapshot-diffs` (`gh run download "$run_id" --name ios-snapshot-diffs`) and report; re-record only after an intended change. If `LibraryFlowTests.testChangingAStatusFiltersAndSearchesTheLibrary` fails only on CI at the menu's `isSelected` check, download the `.xcresult` and report what iOS 18.5 exposes for the checked item instead of weakening the test.

- [ ] **Step 5: Upgrade a plan-2 install in place (simulator)** — spec §12 check 1; done while writing this plan with the plan-2 and plan-3 Debug builds.

```bash
# In a checkout of the plan-2 commit (main before this branch): build and install it, launch it once.
xcodegen generate --spec ios/project.yml
xcodebuild build -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -derivedDataPath /tmp/hashiya-v2
xcrun simctl uninstall booted com.etatech.hashiya
xcrun simctl install booted /tmp/hashiya-v2/Build/Products/Debug-iphonesimulator/Hashiya.app
xcrun simctl launch booted com.etatech.hashiya; sleep 6; xcrun simctl terminate booted com.etatech.hashiya
# Save two papers in it by hand (Search "attention", Save two results), or seed plan 2's v1 schema directly:
DB="$(xcrun simctl get_app_container booted com.etatech.hashiya group.com.etatech.hashiya)/Library/Application Support/hashiya.sqlite"
sqlite3 "$DB" "SELECT identifier FROM grdb_migrations;"   # v1
sqlite3 "$DB" "INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, oa_pdf_url, saved_at) VALUES ('up-a', 'W2626778328', '10.48550/arxiv.1706.03762', 'Attention Is All You Need', 2017, 'Neural Information Processing Systems', 'The dominant sequence transduction models', 128412, 1, NULL, 100); INSERT INTO paper_authors VALUES ('up-a', 1, 'Noam Shazeer', NULL); INSERT INTO paper_authors VALUES ('up-a', 0, 'Ashish Vaswani', NULL); INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, oa_pdf_url, saved_at) VALUES ('up-b', 'W4000000001', NULL, 'تطبيقات التَّعلُّم العميق', NULL, NULL, NULL, 0, 0, NULL, 200);"
# Back on this branch: build and install over it (no uninstall), launch.
xcodebuild build -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -derivedDataPath /tmp/hashiya-v3
xcrun simctl install booted /tmp/hashiya-v3/Build/Products/Debug-iphonesimulator/Hashiya.app
xcrun simctl launch booted com.etatech.hashiya; sleep 8; xcrun simctl terminate booted com.etatech.hashiya
sqlite3 "$DB" "SELECT identifier FROM grdb_migrations; SELECT id||':'||reading_status FROM papers ORDER BY saved_at DESC; SELECT paper_id||':'||title||':'||authors FROM paper_search ORDER BY paper_id;"
```
Expected: `v1`, `v2`; `up-b:to_read`, `up-a:to_read`; `up-a:attention is all you need:ashish vaswani noam shazeer`, `up-b:تطبيقات التعلم العميق:`. In the app: both papers with a **To read** badge, chips `All · 2`, `To read · 2`, `Reading · 0`, `Read · 0`; searching `shazeer` and `التعلم` each finds its paper. Afterwards delete the seeded rows (`DELETE FROM papers WHERE id IN ('up-a','up-b'); DELETE FROM paper_search WHERE paper_id IN ('up-a','up-b');`). On a device, repeat by installing this build over a TestFlight or Xcode-installed plan-2 build that has saved papers.

- [ ] **Step 6: Acceptance checks on a simulator or device** (spec §12)

1. Library search for an author's surname (`shazeer`) and a word from an abstract (`transduction`) finds the paper; `transf` finds "Transformer"; repeat in airplane mode.
2. Change a status from a row's badge menu and from the preview's selector → the chip counts change at once; with the Reading chip selected, changing a paper to Read removes its row while its open preview stays, showing Read.
3. Chips combine with the search; "No papers match" → **Clear search and filters** resets both; type letter by letter into a search that stops matching → the keyboard stays up. The keyboard's Search key applies at once and hides the keyboard.
4. Remove a Reading paper, tap **Undo** → it returns as Reading, in its place.
5. **Not automated — Arabic:** Settings → Language → العربية: a search with and without tashkeel finds the same papers; `١٩` and `19` find the same papers; the chips read like "قيد القراءة (3)" (Arabic-Indic digits when the region uses them, e.g. Arabic (Egypt)); the layout mirrors (chips from the right, badges on the left).
6. **Not automated — Share Extension:** share an arXiv page from Safari, **Save to library**, return to Hashiya → the paper is listed as To read and found by the Library search (the Share Extension writes through the same `PaperStore.insert`; `GRDBLibraryRepositoryTests.refreshShowsPapersSavedThroughAnotherPool` covers it in code).
7. **Not automated — VoiceOver:** a row's badge reads "Status: To read. Change status, button"; the selected chip is announced with "Selected"; the Read badge's check is not read separately.
8. **Not automated on iOS 17:** repeat 3 on an iOS 17 simulator or device (only iOS 18 runtimes were installed while writing this plan).
