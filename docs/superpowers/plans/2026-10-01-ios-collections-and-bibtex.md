# iOS Collections and BibTeX Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Load the `liquid-glass:liquid-glass` skill before any task that touches a view.

**Goal:** Bring Android sub-project 5 to iOS. Saved papers can be grouped into flat collections, picked from the Library's title menu, and managed from Details. A collection or the whole library exports as a `.bib` file through the share sheet. Copy BibTeX on Details puts one entry on the clipboard. The output is byte-for-byte the same as Android's. English and Arabic with right-to-left layouts, light and dark, Liquid Glass on iOS 26.

**Architecture:**
- `HashiyaModel` gains `PublicationDetails`, `PaperCollection` and the collection-name rule.
- A new package target, `HashiyaBibTeX`, ports Android's `core/bibtex` file by file, without regular expressions.
- `HashiyaNetwork` fetches `type` and `biblio`.
- `HashiyaDatabase` gains migration `"v4"`: the citation columns on `papers`, plus the `collections` and `collection_papers` tables. `PaperStore` gains the collection and citation operations, and Undo keeps collection links and cite keys.
- `HashiyaData` gains `CollectionsRepository`, `CitationRepository` (refetch, then assign keys, then build) and `ExportFiles`.
- `HashiyaDesignSystem` gains `CollectionNameSheet` and a UIKit share-sheet presenter.
- `FeatureLibrary` gains the title menu, filtering by collection, swipe-out-of-collection and Export.
- `FeaturePaperDetails` gains the Collections row, the checklist sheet and Copy BibTeX.

**Tech Stack:** Swift 6 (language mode 6, strict concurrency), SwiftUI with the iOS 26 SDK, iOS 17+ deployment target, Swift Testing, XCTest (UI tests), GRDB.swift 7.11.1, swift-snapshot-testing 1.19.6, String Catalogs, XcodeGen 2.46.0. CI: `macos-15`, Xcode 26.3, iPhone 16 on iOS 26.2 and on iOS 18.5.

**Spec:** docs/superpowers/specs/2026-10-01-ios-collections-and-bibtex-design.md (and, for the BibTeX rules, the Android spec docs/superpowers/specs/2026-09-30-collections-and-bibtex-design.md §6)

## Global Constraints

- Everything in the earlier iOS plans' Global Constraints still applies:
  - iOS 17 minimum; Swift 6 language mode with strict concurrency.
  - The code builds with CI's Xcode 26.3 (Swift 6.2): no isolated `deinit`, no `Mutex`; `OSAllocatedUnfairLock` for shared state.
  - XcodeGen: `ios/project.yml` is committed, and `ios/Hashiya.xcodeproj` is generated and git-ignored.
  - Exact dependency pins. No new third-party dependencies.
  - Features never import each other, `HashiyaNetwork`, `HashiyaDatabase` or GRDB.
  - Every user-visible string comes from a `Localizable.xcstrings` (`extractionState: manual`) through the target's `L10n`, shown with `Text(verbatim:)`. Numbers go through `PaperFormat.number`.
  - Snapshot suites are `@MainActor @Suite(.serialized)`, and baselines come only from CI.
  - Every glass API call sits inside `if #available(iOS 26, *)` or behind the `Glass.swift` helpers. System chrome (navigation bar, title menu, menus, sheets' bars) gets no glass modifiers.
- **One new package target**, `HashiyaBibTeX`, with its test target `HashiyaBibTeXTests`. It depends on `HashiyaModel` only. `HashiyaData` depends on it, and no feature imports it.
- **BibTeX output equals Android's byte for byte.** Android's four `core/bibtex` test files are ported case by case with identical inputs and expected strings. **No regular expressions anywhere in `HashiyaBibTeX`** (spec §6.2).
- **Database:** exactly one new migration, `"v4"`, registered after `"v3"`, with the SQL from Android's `MIGRATION_3_4`. Never `eraseDatabaseOnSchemaChange` or any destructive fallback. `paper_search` is untouched.
- **Cite keys are stored once and never change.** They are assigned to every keyless paper across the whole library in `saved_at, rowid` order before any entry is built, and they survive Remove → Undo unless another paper took the key meanwhile.
- **Collections:** flat; names are trimmed and must be 1–60 `Character`s (`isValidCollectionName`). Uniqueness uses `name_key` (the trimmed name `lowercased()`). Deleting a collection never deletes papers.
- **Strings:** keys from spec §11. English and Arabic are copied verbatim from Android's `strings.xml` on `feat/collections-and-bibtex`, with `%1$s` written as `%@`. Add them with the Python snippets in the tasks, never by hand-editing the JSON.
- **Verification:**
  - **On the Mac** (the default for this work): each task runs its narrow `xcodebuild test … -only-testing:` command, then the full scheme (the `Run:` lines). The phone checks in Task 12 need the Mac.
  - **In a cloud session:** it can't build or test iOS. Push, and read the "iOS" workflow's result for that commit through the GitHub API (about 25 minutes).
  - Never say a task works until one of those showed it.
  - Until Task 12 records the new baselines, a run fails only in the new snapshot tests, with `No reference was found on disk. Automatically recorded snapshot: …`. Read every failure; any other failure is real.
- **Git:**
  - Work on `feat/ios-collections-and-bibtex`. It was created from `feat/collections-and-bibtex` at `fbc1c7c` and holds the spec. When PR #18 merges, rebase onto `main` and diff Android's final code against the spec before continuing.
  - Every `git add` lists explicit paths. Never stage `__Snapshots__` by hand before Task 12, and never stage `.idea/`.
  - Commit messages use `feat:`/`fix:`/`test:`/`docs:`/`ci:`. Commits, code comments, docs and PR text carry no AI or Claude attribution.
  - Before every push, `git log --format='%an <%ae>' origin/main..HEAD | sort -u` must print only `Fady <fady.fouad.a@gmail.com>`.

## Review Focus

1. **Unicode in names and titles:**
   - Example inputs: an author "José\u{00A0}Müller", a title starting with "\u{0085}" or using U+2028 or U+3000, ligatures ("ﬁ"), an Arabic-only author.
   - Expected: the same cite key and entry text as Android, and never a crash.
   - Pinned by the ported `LatexTextTests`/`CiteKeysTests` cases (Tasks 2–3), and by `BibTeXTests.unicodeWhitespaceMatchesAndroid` added in Task 3.
2. **Remove → Undo, then export:**
   - Expected: the paper comes back with its collections and the same cite key. If another paper took the key meanwhile, the restored paper gets a fresh key, and nobody's key changes.
   - Pinned by `PaperStoreTests.restoreKeepsLinksAndCiteKey`, `restoreDropsATakenCiteKey` (Task 5) and `GRDBCitationRepositoryTests.keysSurviveRemoveAndUndo` (Task 8).
3. **Exporting offline, or with OpenAlex failing for some papers:**
   - Expected: the file is still shared, then "may be incomplete" appears. The failed papers keep `details_fetched = 0`, so the next export retries them.
   - Pinned by `GRDBCitationRepositoryTests.aFailedRefetchIsIncompleteAndRetriedNextTime` (Task 8) and `LibraryViewModelTests.exportIncompleteShowsTheBannerAfterSharing` (Task 10).
4. **A collection deleted elsewhere while the Library shows it, or while its Undo banner is up:**
   - Expected: the Library falls back to All papers, a swipe never removes the paper from the library, and the Undo is dropped.
   - Pinned by `LibraryViewModelTests.aDeletedCollectionFallsBackToAllPapers`, `swipeInADeletedCollectionDoesNothing` and `undoIntoADeletedCollectionIsDropped` (Task 10).
5. **Tapping Export or Create twice quickly, or leaving while the share sheet is open:**
   - Expected: one export, one share sheet and one collection. Export stays busy until the share sheet closes.
   - Pinned by `LibraryViewModelTests.aSecondExportTapWhileRunningDoesNothing`, `exportStaysBusyUntilTheShareSheetCloses` (Task 10) and `PaperDetailsViewModelTests.aDoubleCreateRunsOnce` (Task 11).

---

## Where this plan departs from the spec (and why)

- **Extra model helpers:** `trimmedCollectionName(_:)` and `collectionNameKey(_:)` (trim, then `lowercased()`), ported from Android's `core/model`. `name_key` is always `collectionNameKey(name)`.
- **Kotlin-exact text helpers in `HashiyaBibTeX`:**
  - `splitOnWhitespace(_:)` keeps empty pieces at either end, as Kotlin's `split(Regex)` does.
  - `kotlinTrim(_:)` removes what Kotlin's `trim()` removes, which is not Unicode `White_Space`: NEL stays.
  - `isBibWhitespace(_:)` is Unicode `White_Space`.

  Matching Android byte for byte depends on these edges.
- **Narrow test runs use the package scheme:** `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package …)`. The full app scheme runs before every push. `HashiyaBibTeXTests` is added to the `Hashiya` scheme in `ios/project.yml`.
- **`PaperStore`:**
  - `observeLibrary`'s `collectionID` defaults to nil, so old call sites compile.
  - `LibraryRows.allTotal` holds the whole library's count.
  - `assignCiteKeys` throws `CiteKeyTakenError`, which `HashiyaData` retries once, without reading GRDB error codes.
  - `addToCollection` is one `INSERT OR IGNORE … SELECT`, which does nothing when the collection is missing.
  - Deletes call `notifyChanges(in: collection_papers)`.
- **Mapping:**
  - `Paper.asRecords(localID:savedAt:status:citeKey:detailsFetched:)` defaults to "no key, fetched", so every save from Task 5 on stores the details with `details_fetched = 1`.
  - `RemovedPaper.detailsFetched` defaults to `true`, as on Android.
  - `RemovedPaper.collectionLinksAddedAt` lets Undo restore each link's `added_at`.
- **One store for three repositories:** `LibraryRepositories.shared(fileName:fresh:lookup:)` (Task 8) builds the library, collections and citations repositories on one `PaperStore`. The UI-testing stubs use it with an offline lookup that answers "not found". The GRDB repositories are structs, like `GRDBLibraryRepository`.
- **Linked fakes:** `FakeCollectionsRepository(collections:memberships:library:)` mirrors memberships into `FakeLibraryRepository`, as Android's fake does, so the Library view-model tests see a collection's papers.
  - `createdNames` and `membershipCalls` record only successful calls.
  - Tests wait on `heldCreates` while creates are held.
  - `FakeOpenAlexLookupService` gains `setWorks(_:)`.
- **Library:**
  - `confirmDelete(_ collection:)` takes the collection, because a `confirmationDialog` clears its binding before the action runs.
  - The title is "Library" while loading or empty, and "All papers" or the collection's name otherwise.
  - A collection made from the title menu becomes the selection. The fallback to All papers waits until the new id appears in `observeCollections()`.
  - Android's `library_delete` is kept as `library.delete` for the confirmation's button.
- **Baselines that change:**
  - Library: `papers`, `filtered`, `noMatches`, `undo` and `statusFailed` are deleted in Task 10.
  - Details: every image is deleted in Task 11, because of the new row.
  - Task 12 records them on CI.
  - `LibraryFlowTests.testTheLibraryShowsItsLargeTitle` now expects "All papers".
- **App wiring lands with each feature:** Task 10 wires the Library (with the live share sheet), and Task 11 wires Details (with `UIPasteboard`), so the app builds after every task. Task 12 adds only UI tests, docs, baselines and checks.

---

## File Structure

```
ios/HashiyaKit/Sources/HashiyaModel/PublicationDetails.swift, PaperCollection.swift, Paper.swift            (Task 1)
ios/HashiyaKit/Sources/HashiyaBibTeX/LatexText.swift, EntryType.swift, CiteKeys.swift                         (Task 2)
ios/HashiyaKit/Sources/HashiyaBibTeX/BibTeX.swift                                                              (Task 3)
ios/HashiyaKit/Tests/HashiyaBibTeXTests/*                                                                      (Tasks 2–3)
ios/HashiyaKit/Package.swift; ios/project.yml                                                                  (Tasks 2, 8)
ios/HashiyaKit/Sources/HashiyaNetwork/NetworkModels.swift, OpenAlexSearchClient.swift; HashiyaData/PaperMapping.swift; works_page.json (Task 4)
ios/HashiyaKit/Sources/HashiyaDatabase/HashiyaDatabase.swift, Records.swift, PaperStore.swift                  (Tasks 5–6)
ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift, CollectionsRepository.swift                        (Task 7)
ios/HashiyaKit/Sources/HashiyaData/CitationRepository.swift, ExportFiles.swift, LiveDependencies.swift         (Task 8)
ios/HashiyaKit/Sources/HashiyaTesting/FakeLibraryRepository.swift, FakeCollectionsRepository.swift, FakeCitationRepository.swift, FakeLookupServices.swift (Tasks 7–8)
ios/Hashiya/UITestingStubs.swift                                                                               (Task 8)
ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/CollectionNameSheet.swift, ShareSheet.swift, DesignSystemStrings.swift (Task 9)
ios/HashiyaKit/Sources/FeatureLibrary/*; ios/Hashiya/AppContainer.swift                                         (Task 10)
ios/HashiyaKit/Sources/FeaturePaperDetails/*; ios/Hashiya/AppContainer.swift                                    (Task 11)
ios/HashiyaSnapshotTests/*                                                                                     (Tasks 9–11)
ios/HashiyaUITests/CollectionsFlowTests.swift, LibraryFlowTests.swift; ios/README.md, README.md                (Tasks 10, 12)
```

Each task below names its exact files.

---

### Task 1: `HashiyaModel` — `PublicationDetails`, `Paper.publication`, `PaperCollection` and collection names

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaModel/PublicationDetails.swift`
- Create: `ios/HashiyaKit/Sources/HashiyaModel/PaperCollection.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaModel/Paper.swift` (stored property, init parameter)
- Test: Create `ios/HashiyaKit/Tests/HashiyaModelTests/PaperCollectionTests.swift`

**Interfaces:**
- Consumes: nothing new.
- Produces (module `HashiyaModel`, all `public`):
  - `struct PublicationDetails: Equatable, Hashable, Sendable` with `workType`, `sourceType`, `publisher`, `volume`, `issue`, `firstPage`, `lastPage`, all `String?`, and `init(workType:sourceType:publisher:volume:issue:firstPage:lastPage:)`, every argument defaulting to `nil`.
  - `Paper.publication: PublicationDetails`, and the init parameter `publication: PublicationDetails = PublicationDetails()` placed last, so every existing call site compiles unchanged.
  - `struct PaperCollection: Equatable, Hashable, Identifiable, Sendable { id: Int64; name: String; paperCount: Int }` with `init(id:name:paperCount:)`.
  - `let collectionNameMaxLength = 60`
  - `func trimmedCollectionName(_ name: String) -> String`: `trimmingCharacters(in: .whitespacesAndNewlines)`.
  - `func isValidCollectionName(_ name: String) -> Bool`: 1...60 `Character`s after trimming.
  - `func collectionNameKey(_ name: String) -> String`: trimmed, then `lowercased()`.

- [ ] **Step 1: Write the failing test**

`ios/HashiyaKit/Tests/HashiyaModelTests/PaperCollectionTests.swift` (Android's `PaperCollectionTest`, case for case, plus the spec's emoji rule):
```swift
import HashiyaModel
import Testing

/// Mirrors Android's core/model PaperCollectionTest.
struct PaperCollectionTests {
    @Test func namesAreTrimmedAndLimitedTo60Characters() {
        #expect(isValidCollectionName("Chapter 2"))
        #expect(isValidCollectionName("  x  "))
        #expect(isValidCollectionName(String(repeating: "a", count: 60)))
        #expect(!isValidCollectionName(String(repeating: "a", count: 61)))
        #expect(!isValidCollectionName(""))
        #expect(!isValidCollectionName("   "))
        #expect(collectionNameMaxLength == 60)
    }

    /// Spec §3: Swift counts Characters, so an emoji is one (Android counts two UTF-16 units). Never stricter than Android.
    @Test func anEmojiCountsAsOneCharacter() {
        #expect(isValidCollectionName(String(repeating: "📚", count: 60)))
        #expect(!isValidCollectionName(String(repeating: "📚", count: 61)))
        #expect(isValidCollectionName("👩‍🔬" + String(repeating: "a", count: 59)))
    }

    @Test func trimmingRemovesSpacesAndNewlines() {
        #expect(trimmedCollectionName("  Thesis\n") == "Thesis")
        #expect(trimmedCollectionName("\t الفصل الثاني ") == "الفصل الثاني")
        #expect(!isValidCollectionName("\n\t"))
    }

    @Test func nameKeyIgnoresCaseAndSurroundingSpaces() {
        #expect(collectionNameKey("  Thesis Refs ") == "thesis refs")
        #expect(collectionNameKey("Thesis") == collectionNameKey(" thesis "))
        #expect(collectionNameKey(" الفصل الثاني ") == "الفصل الثاني")
    }

    @Test func paperHasEmptyPublicationDetailsByDefault() {
        let paper = Paper(openAlexID: "W1", title: "T")
        #expect(paper.publication == PublicationDetails())
        #expect(PublicationDetails().workType == nil)
        #expect(PublicationDetails().lastPage == nil)
    }

    @Test func publicationDetailsKeepTheOpenAlexStringsAsGiven() {
        let details = PublicationDetails(
            workType: "article", sourceType: "journal", publisher: "Springer Nature",
            volume: "521", issue: "7553", firstPage: "436", lastPage: "444"
        )
        let paper = Paper(openAlexID: "W1", title: "Deep learning", publication: details)
        #expect(paper.publication.workType == "article")
        #expect(paper.publication.sourceType == "journal")
        #expect(paper.publication.publisher == "Springer Nature")
        #expect(paper.publication.volume == "521")
        #expect(paper.publication.issue == "7553")
        #expect(paper.publication.firstPage == "436")
        #expect(paper.publication.lastPage == "444")
        #expect(paper != Paper(openAlexID: "W1", title: "Deep learning"))
    }

    @Test func aCollectionIsIdentifiedByItsID() {
        let collection = PaperCollection(id: 7, name: "Thesis", paperCount: 3)
        #expect(collection.id == 7)
        #expect(collection.name == "Thesis")
        #expect(collection.paperCount == 3)
    }
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaModelTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL: `cannot find 'isValidCollectionName' in scope`, `cannot find 'PublicationDetails' in scope`, then `** TEST FAILED **`.

- [ ] **Step 3: Implement**

`ios/HashiyaKit/Sources/HashiyaModel/PublicationDetails.swift`:
```swift
/// Bibliographic details used for citations, as OpenAlex reports them. Every field is nil when the source has none.
/// The strings are kept as-is; HashiyaBibTeX interprets them, so a new OpenAlex type needs no migration.
public struct PublicationDetails: Equatable, Hashable, Sendable {
    /// OpenAlex's work type, such as "article", "preprint", "book-chapter".
    public var workType: String?
    /// OpenAlex's source type, such as "journal", "conference", "repository".
    public var sourceType: String?
    public var publisher: String?
    public var volume: String?
    public var issue: String?
    public var firstPage: String?
    public var lastPage: String?

    public init(
        workType: String? = nil,
        sourceType: String? = nil,
        publisher: String? = nil,
        volume: String? = nil,
        issue: String? = nil,
        firstPage: String? = nil,
        lastPage: String? = nil
    ) {
        self.workType = workType
        self.sourceType = sourceType
        self.publisher = publisher
        self.volume = volume
        self.issue = issue
        self.firstPage = firstPage
        self.lastPage = lastPage
    }
}
```

`ios/HashiyaKit/Sources/HashiyaModel/PaperCollection.swift`:
```swift
import Foundation

/// A user-made group of saved papers. `paperCount` is how many saved papers are in it.
public struct PaperCollection: Equatable, Hashable, Identifiable, Sendable {
    public let id: Int64
    public let name: String
    public let paperCount: Int

    public init(id: Int64, name: String, paperCount: Int) {
        self.id = id
        self.name = name
        self.paperCount = paperCount
    }
}

public let collectionNameMaxLength = 60

/// The name as stored: surrounding whitespace and newlines removed.
public func trimmedCollectionName(_ name: String) -> String {
    name.trimmingCharacters(in: .whitespacesAndNewlines)
}

/// A name is valid when, trimmed, it has 1 to `collectionNameMaxLength` characters. Swift counts `Character`s, so an emoji
/// counts once; Android counts UTF-16 units, so a name valid there is always valid here.
public func isValidCollectionName(_ name: String) -> Bool {
    (1...collectionNameMaxLength).contains(trimmedCollectionName(name).count)
}

/// Two collections may not share this key: the name trimmed and lowercased (locale-independent, like Kotlin's Locale.ROOT).
public func collectionNameKey(_ name: String) -> String {
    trimmedCollectionName(name).lowercased()
}
```

`ios/HashiyaKit/Sources/HashiyaModel/Paper.swift`: add the stored property after `openAccessPDFURL`, the init parameter last, and its assignment.
```swift
    public var openAccessPDFURL: String?
    /// Bibliographic details for citations. Empty for papers saved before schema v4 until they are refetched.
    public var publication: PublicationDetails
```
```swift
        isOpenAccess: Bool = false,
        openAccessPDFURL: String? = nil,
        publication: PublicationDetails = PublicationDetails()
    ) {
        …
        self.openAccessPDFURL = openAccessPDFURL
        self.publication = publication
    }
```

- [ ] **Step 4: Run it and see it pass**

Run: the Step 2 command.
Expected: PASS: `✔ Test run with … tests passed`, `** TEST SUCCEEDED **`. The existing `PaperTests` still pass, because `publication` has a default.

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaModel/PublicationDetails.swift ios/HashiyaKit/Sources/HashiyaModel/PaperCollection.swift ios/HashiyaKit/Sources/HashiyaModel/Paper.swift ios/HashiyaKit/Tests/HashiyaModelTests/PaperCollectionTests.swift
git commit -m "feat: add publication details and collections to the iOS model"
```

Tasks 1–3 are pushed together at the end of Task 3.

---

### Task 2: `HashiyaBibTeX` — the target, LaTeX text, entry types and cite keys

**Files:**
- Modify: `ios/HashiyaKit/Package.swift` (product, target, test target)
- Modify: `ios/project.yml` (scheme `Hashiya` → `test.targets`)
- Create: `ios/HashiyaKit/Sources/HashiyaBibTeX/LatexText.swift`
- Create: `ios/HashiyaKit/Sources/HashiyaBibTeX/EntryType.swift`
- Create: `ios/HashiyaKit/Sources/HashiyaBibTeX/CiteKeys.swift`
- Test: Create `ios/HashiyaKit/Tests/HashiyaBibTeXTests/LatexTextTests.swift`, `EntryTypeTests.swift`, `CiteKeysTests.swift`

**Interfaces:**
- Consumes: `Paper`, `Author` and `PublicationDetails` from Task 1.
- Produces (module `HashiyaBibTeX`, which depends on `HashiyaModel` only):
  - `public enum CiteKeys { static func base(_ paper: Paper) -> String; static func assign(_ papers: [Paper], taken: Set<String>) -> [String] }`
  - internal, for Task 3 and the tests (`@testable import HashiyaBibTeX`):
    - `enum EntryType: CaseIterable { case article, inProceedings, inCollection, book, phdThesis, techReport, misc }`, with `bibName: String`, `venueField: String?` and `hasPublisher: Bool`;
    - `func entryType(_ details: PublicationDetails) -> EntryType`;
    - `func escapeLatex(_ text: String) -> String`, `func cleanWhitespace(_ text: String) -> String`, `func protectCapitals(_ text: String) -> String`;
    - `func asciiFold(_ text: String) -> String`, `func keySuffix(_ n: Int) -> String`;
    - `func isBibWhitespace(_ scalar: Unicode.Scalar) -> Bool`, `func kotlinTrim(_ text: String) -> String`, `func splitOnWhitespace(_ text: String) -> [String]`.
  - Package product `HashiyaBibTeX`. Task 8 adds it to `HashiyaData`'s dependencies; nothing else imports it.

The port works on Unicode scalars wherever Android works on UTF-16 chars, and uses no regular expressions (spec §6.2). Every case in Android's `LatexTextTest`, `EntryTypeTest` and `CiteKeysTest` is ported with identical inputs and expected strings. A few Swift-only cases pin the Kotlin edges the port reproduces.

- [ ] **Step 1: Add the target to the package and the scheme**

`ios/HashiyaKit/Package.swift`:
- In `products`, after `HashiyaModel`:
```swift
        .library(name: "HashiyaBibTeX", targets: ["HashiyaBibTeX"]),
```
- In `targets`, after `.target(name: "HashiyaModel"),`:
```swift
        .target(name: "HashiyaBibTeX", dependencies: ["HashiyaModel"]),
```
- After `.testTarget(name: "HashiyaModelTests", dependencies: ["HashiyaModel"]),`:
```swift
        .testTarget(name: "HashiyaBibTeXTests", dependencies: ["HashiyaBibTeX", "HashiyaModel"]),
```

`ios/project.yml`, scheme `Hashiya`, `test.targets`, after `- package: HashiyaKit/HashiyaModelTests`:
```yaml
        - package: HashiyaKit/HashiyaBibTeXTests
```
Without this, CI's `xcodebuild test -scheme Hashiya` never runs the new tests. The package scheme runs them regardless.

- [ ] **Step 2: Write the failing tests**

`ios/HashiyaKit/Tests/HashiyaBibTeXTests/LatexTextTests.swift`:
```swift
@testable import HashiyaBibTeX
import Testing

/// Mirrors Android's core/bibtex LatexTextTest, case for case, plus the Kotlin edges the port reproduces.
struct LatexTextTests {
    @Test func escapesEverySpecialCharacter() {
        #expect(escapeLatex("R&D 50% $5 #1 a_b {x") == #"R\&D 50\% \$5 \#1 a\_b \textbraceleft{}x"#)
        #expect(escapeLatex("}") == #"\textbraceright{}"#)
        #expect(escapeLatex("a~b^c") == #"a\textasciitilde{}b\textasciicircum{}c"#)
        #expect(escapeLatex(#"C:\dir"#) == #"C:\textbackslash{}dir"#)
        #expect(escapeLatex(#"\&"#) == #"\textbackslash{}\&"#)
    }

    @Test func keepsUnicode() {
        #expect(escapeLatex("Jörg Müller · تعلم") == "Jörg Müller · تعلم")
    }

    @Test func collapsesWhitespace() {
        #expect(cleanWhitespace("  Deep\nlearning \t for   graphs ") == "Deep learning for graphs")
        #expect(cleanWhitespace("\u{0085}Deep\u{2028}learning\u{00A0}for\u{3000}graphs ") == "Deep learning for graphs")
    }

    @Test func protectsWordsWithInnerCapitals() {
        #expect(protectCapitals("BERT: Pre-training of Deep Models") == "{BERT:} Pre-training of Deep Models")
        #expect(protectCapitals("ImageNet and COVID-19 on an iPhone") == "{ImageNet} and {COVID-19} on an {iPhone}")
        #expect(protectCapitals("The deep A") == "The deep A")
        #expect(protectCapitals("تعلم GPU") == "تعلم {GPU}")
        #expect(protectCapitals(#"(BERT) and R\&D"#) == #"{(BERT)} and {R\&D}"#)
        #expect(protectCapitals(escapeLatex("#MeToo era")) == #"{{\#MeToo}} era"#)
    }

    // Swift-only: the edges of Kotlin's trim() and split(Regex) that the port reproduces (spec §6.2).

    @Test func whitespaceIsUnicodeWhiteSpace() {
        for scalar: Unicode.Scalar in ["\t", "\n", "\u{0B}", "\u{0C}", "\r", " ", "\u{0085}", "\u{00A0}", "\u{1680}", "\u{2000}", "\u{200A}", "\u{2028}", "\u{2029}", "\u{202F}", "\u{205F}", "\u{3000}"] {
            #expect(isBibWhitespace(scalar), "U+\(String(scalar.value, radix: 16))")
        }
        // Zero-width space and the BOM are format characters, not whitespace, on Android too.
        #expect(!isBibWhitespace("\u{200B}"))
        #expect(!isBibWhitespace("\u{FEFF}"))
        #expect(cleanWhitespace("a\u{200B}b") == "a\u{200B}b")
    }

    @Test func trimMatchesKotlin() {
        // Kotlin's trim() removes U+001C–U+001F but not NEL (U+0085).
        #expect(kotlinTrim("\u{001C} x \u{001F}") == "x")
        #expect(kotlinTrim("\u{0085}x\u{0085}") == "\u{0085}x\u{0085}")
        #expect(kotlinTrim("\u{00A0}\u{3000}x\u{2028}") == "x")
        #expect(kotlinTrim("   ") == "")
        #expect(kotlinTrim("") == "")
    }

    @Test func splitKeepsLeadingAndTrailingEmptyPieces() {
        #expect(splitOnWhitespace("a  b") == ["a", "b"])
        #expect(splitOnWhitespace(" a b") == ["", "a", "b"])
        #expect(splitOnWhitespace("a b\u{0085}") == ["a", "b", ""])
        #expect(splitOnWhitespace("") == [""])
    }

    @Test func escapingWorksOnScalarsSoACombiningMarkCantHideABackslash() {
        // "\" + U+0301 is one Swift Character; Android escapes the backslash and keeps the mark.
        #expect(escapeLatex("\\\u{0301}") == "\\textbackslash{}\u{0301}")
    }
}
```

`ios/HashiyaKit/Tests/HashiyaBibTeXTests/EntryTypeTests.swift`:
```swift
@testable import HashiyaBibTeX
import HashiyaModel
import Testing

/// Mirrors Android's core/bibtex EntryTypeTest, case for case.
struct EntryTypeTests {
    private func type(_ work: String?, _ source: String?) -> EntryType {
        entryType(PublicationDetails(workType: work, sourceType: source))
    }

    @Test func followsTheSpecTableInOrder() {
        #expect(type("article", "conference") == .inProceedings)
        #expect(type("preprint", "conference") == .inProceedings)
        #expect(type("book-chapter", "book series") == .inCollection)
        #expect(type("book", nil) == .book)
        #expect(type("dissertation", "repository") == .phdThesis)
        #expect(type("report", nil) == .techReport)
        #expect(type("preprint", "journal") == .misc)
        #expect(type("article", "repository") == .misc)
        #expect(type("article", "journal") == .article)
        #expect(type("review", "journal") == .article)
        #expect(type("letter", "journal") == .article)
        #expect(type("editorial", "journal") == .article)
    }

    @Test func anythingElseIsMisc() {
        #expect(type("article", nil) == .misc)
        #expect(type("dataset", "repository") == .misc)
        #expect(type(nil, nil) == .misc)
        #expect(type("erratum", "journal") == .misc)
    }

    @Test func ignoresCase() {
        #expect(type("Article", "Journal") == .article)
    }

    @Test func venueFieldsAndPublisher() {
        #expect(EntryType.article.venueField == "journal")
        #expect(EntryType.inProceedings.venueField == "booktitle")
        #expect(EntryType.inCollection.venueField == "booktitle")
        #expect(EntryType.book.venueField == nil)
        #expect(EntryType.phdThesis.venueField == "school")
        #expect(EntryType.techReport.venueField == "institution")
        #expect(EntryType.misc.venueField == "howpublished")
        #expect(Set(EntryType.allCases.filter(\.hasPublisher)) == [.book, .inCollection, .techReport, .misc])
    }

    @Test func bibNames() {
        #expect(EntryType.allCases.map(\.bibName) == ["article", "inproceedings", "incollection", "book", "phdthesis", "techreport", "misc"])
    }
}
```

`ios/HashiyaKit/Tests/HashiyaBibTeXTests/CiteKeysTests.swift`:
```swift
@testable import HashiyaBibTeX
import HashiyaModel
import Testing

/// Mirrors Android's core/bibtex CiteKeysTest, case for case.
struct CiteKeysTests {
    private func paper(_ title: String, _ authors: String..., year: Int? = 2017) -> Paper {
        Paper(openAlexID: "W1", title: title, authors: authors.map { Author(name: $0) }, year: year)
    }

    @Test func surnameYearAndFirstMeaningfulTitleWord() {
        #expect(CiteKeys.base(paper("Attention Is All You Need", "Ashish Vaswani", "Noam Shazeer")) == "vaswani2017attention")
    }

    @Test func stopWordsAreSkipped() {
        #expect(CiteKeys.base(paper("On the Deep Nature of Things", "Jane Smith", year: 2020)) == "smith2020deep")
        #expect(CiteKeys.base(paper("Towards Using: Learning", "Jane Smith", year: 2020)) == "smith2020learning")
    }

    @Test func accentsAndSpecialLettersFoldToAscii() {
        #expect(CiteKeys.base(paper("Über Netze", "Jörg Müller", year: 2019)) == "muller2019uber")
        #expect(CiteKeys.base(paper("Große Modelle", "Anna Straße", year: 2019)) == "strasse2019grosse")
        #expect(CiteKeys.base(paper("Æon", "Jan Łukasz", year: 2019)) == "lukasz2019aeon")
    }

    @Test func punctuationInsideWordsIsDropped() {
        #expect(
            CiteKeys.base(paper("BERT: Pre-training of Deep Bidirectional Transformers", "Jacob Devlin", year: 2019))
                == "devlin2019bert"
        )
        #expect(CiteKeys.base(paper("Self-Attention", "Mary O'Neill", year: 2021)) == "oneill2021selfattention")
    }

    @Test func missingYearIsNd() {
        #expect(CiteKeys.base(paper("Graphs", "Jane Smith", year: nil)) == "smithndgraphs")
    }

    @Test func nonLatinOrMissingAuthorStartsWithPaper() {
        #expect(CiteKeys.base(paper("تطبيقات التعلم العميق", "محمد علي", year: 2019)) == "paper2019")
        #expect(CiteKeys.base(paper("Deep nets", "محمد علي", year: 2019)) == "paper2019deep")
        #expect(CiteKeys.base(paper("Deep nets", year: 2019)) == "paper2019deep")
        #expect(CiteKeys.base(paper("", year: nil)) == "papernd")
    }

    @Test func keyNeverStartsWithADigit() {
        #expect(CiteKeys.base(paper("Nets", "Group 7", year: 2020)) == "paper2020nets")
        #expect(CiteKeys.base(paper("Nets", "Team 3b", year: 2020)) == "b2020nets")
    }

    @Test func ligaturesAndCapitalSharpSFold() {
        #expect(CiteKeys.base(paper("Eﬃcient Nets", "Anna STRAẞE", year: 2020)) == "strasse2020efficient")
    }

    @Test func suffixesRunAToZThenAa() {
        #expect(keySuffix(0) == "")
        #expect(keySuffix(1) == "a")
        #expect(keySuffix(26) == "z")
        #expect(keySuffix(27) == "aa")
        #expect(keySuffix(28) == "ab")
    }

    @Test func assignAvoidsTakenKeysAndEachOther() {
        let same = paper("Deep nets", "Jane Smith", year: 2020)
        #expect(
            CiteKeys.assign([same, same, paper("Attention", "Ashish Vaswani")], taken: ["smith2020deep"])
                == ["smith2020deepa", "smith2020deepb", "vaswani2017attention"]
        )
    }

    @Test func assignRunsPastZ() {
        let same = paper("Deep", "Jane Smith", year: 2020)
        let taken = Set((0...26).map { "smith2020deep" + keySuffix($0) })
        #expect(CiteKeys.assign([same], taken: taken) == ["smith2020deepaa"])
    }

    // Swift-only: Unicode cases that differ between Swift Characters and Kotlin chars (spec §6.2).

    @Test func decomposedAccentsFoldLikePrecomposedOnes() {
        // "Mu\u{0308}ller" is one Character per letter in Swift but two scalars for the ü; both fold to "muller".
        #expect(CiteKeys.base(paper("Graphs", "Jörg Mu\u{0308}ller", year: 2019)) == "muller2019graphs")
    }

    @Test func unicodeSpacesSplitNamesAndTitles() {
        #expect(CiteKeys.base(paper("Deep\u{3000}learning", "José\u{00A0}Müller", year: 2015)) == "muller2015deep")
    }
}
```

- [ ] **Step 3: Run them and see them fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaBibTeXTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL: `cannot find 'escapeLatex' in scope`, `cannot find 'EntryType' in scope`, `cannot find 'CiteKeys' in scope` (or, before the sources exist, `error: target 'HashiyaBibTeX' … has no source files`), then `** TEST FAILED **`.

- [ ] **Step 4: Implement**

`ios/HashiyaKit/Sources/HashiyaBibTeX/LatexText.swift`:
```swift
import Foundation

// Android's core/bibtex works on UTF-16 chars and regular expressions. This port works on Unicode scalars and uses no
// regular expressions (Android's `(?U)\s+` crashed on device; engines differ), so the output matches byte for byte.

/// Unicode `White_Space`: exactly what Android's `[\s\p{Z}\u0085]` matches, including NEL, the line separator,
/// no-break spaces and the ideographic space.
func isBibWhitespace(_ scalar: Unicode.Scalar) -> Bool {
    scalar.properties.isWhitespace
}

/// What Kotlin's `Char.isWhitespace` accepts, which is what `String.trim()` removes: the Z categories plus U+0009–U+000D
/// and U+001C–U+001F. Not NEL (U+0085), unlike `isBibWhitespace`.
private func isKotlinWhitespace(_ scalar: Unicode.Scalar) -> Bool {
    switch scalar.value {
    case 0x09...0x0D, 0x1C...0x1F:
        return true
    default:
        switch scalar.properties.generalCategory {
        case .spaceSeparator, .lineSeparator, .paragraphSeparator: return true
        default: return false
        }
    }
}

func string<S: Sequence<Unicode.Scalar>>(_ scalars: S) -> String {
    var view = String.UnicodeScalarView()
    view.append(contentsOf: scalars)
    return String(view)
}

/// Kotlin's `String.trim()`.
func kotlinTrim(_ text: String) -> String {
    let scalars = Array(text.unicodeScalars)
    guard let start = scalars.firstIndex(where: { !isKotlinWhitespace($0) }),
          let end = scalars.lastIndex(where: { !isKotlinWhitespace($0) }) else { return "" }
    return string(scalars[start...end])
}

/// Kotlin's `split(WHITESPACE)` with `WHITESPACE` a run of `isBibWhitespace`: empty pieces at either end are kept.
func splitOnWhitespace(_ text: String) -> [String] {
    var pieces: [String] = []
    var current = String.UnicodeScalarView()
    var inRun = false
    for scalar in text.unicodeScalars {
        if isBibWhitespace(scalar) {
            if !inRun {
                pieces.append(String(current))
                current = String.UnicodeScalarView()
                inRun = true
            }
        } else {
            current.append(scalar)
            inRun = false
        }
    }
    pieces.append(String(current))
    return pieces
}

/// Kotlin's `split(' ')`: splits on U+0020 only and keeps empty pieces.
private func splitOnSpace(_ text: String) -> [String] {
    var pieces: [String] = []
    var current = String.UnicodeScalarView()
    for scalar in text.unicodeScalars {
        if scalar == " " {
            pieces.append(String(current))
            current = String.UnicodeScalarView()
        } else {
            current.append(scalar)
        }
    }
    pieces.append(String(current))
    return pieces
}

/// Escapes the characters LaTeX treats specially. Everything else, Arabic and accented letters included, stays UTF-8.
/// Braces become commands rather than `\{`, because BibTeX counts braces without looking at backslashes, so a lone `\{` in a
/// title would unbalance the entry. Walks scalars, so a combining mark after a backslash can't hide it.
func escapeLatex(_ text: String) -> String {
    var out = ""
    for scalar in text.unicodeScalars {
        switch scalar {
        case "\\": out += "\\textbackslash{}"
        case "&", "%", "$", "#", "_": out += "\\" + String(Character(scalar))
        case "{": out += "\\textbraceleft{}"
        case "}": out += "\\textbraceright{}"
        case "~": out += "\\textasciitilde{}"
        case "^": out += "\\textasciicircum{}"
        default: out.unicodeScalars.append(scalar)
        }
    }
    return out
}

/// Runs of whitespace (Unicode `White_Space`) become one space, then the ends are trimmed as Kotlin's `trim()` does.
func cleanWhitespace(_ text: String) -> String {
    kotlinTrim(splitOnWhitespace(text).joined(separator: " "))
}

/// Wraps words with a capital after their first character in braces, so bibliography styles keep BERT, ImageNet, iPhone.
/// A word starting with a command (`\#MeToo`) gets double braces: BibTeX treats `{\` as a special character and would
/// lowercase the rest of the group. Uppercase is the Unicode `Uppercase` property, as Kotlin's `Char.isUpperCase`.
func protectCapitals(_ text: String) -> String {
    splitOnSpace(text).map { word in
        let scalars = word.unicodeScalars
        guard scalars.dropFirst().contains(where: { $0.properties.isUppercase }) else { return word }
        return scalars.first == "\\" ? "{{\(word)}}" : "{\(word)}"
    }.joined(separator: " ")
}
```

`ios/HashiyaKit/Sources/HashiyaBibTeX/EntryType.swift`:
```swift
import HashiyaModel

/// A BibTeX entry type, the field that holds the paper's venue, and whether a publisher field belongs in it.
enum EntryType: CaseIterable, Equatable, Hashable {
    case article, inProceedings, inCollection, book, phdThesis, techReport, misc

    var bibName: String {
        switch self {
        case .article: "article"
        case .inProceedings: "inproceedings"
        case .inCollection: "incollection"
        case .book: "book"
        case .phdThesis: "phdthesis"
        case .techReport: "techreport"
        case .misc: "misc"
        }
    }

    var venueField: String? {
        switch self {
        case .article: "journal"
        case .inProceedings, .inCollection: "booktitle"
        case .book: nil
        case .phdThesis: "school"
        case .techReport: "institution"
        case .misc: "howpublished"
        }
    }

    var hasPublisher: Bool {
        switch self {
        case .inCollection, .book, .techReport, .misc: true
        case .article, .inProceedings, .phdThesis: false
        }
    }
}

private let journalWorkTypes: Set<String> = ["article", "review", "letter", "editorial"]

/// The spec's table, first match wins: a conference article is @inproceedings, a repository article @misc.
func entryType(_ details: PublicationDetails) -> EntryType {
    let work = details.workType?.lowercased()
    let source = details.sourceType?.lowercased()
    if source == "conference" { return .inProceedings }
    if work == "book-chapter" { return .inCollection }
    if work == "book" { return .book }
    if work == "dissertation" { return .phdThesis }
    if work == "report" { return .techReport }
    if work == "preprint" || source == "repository" { return .misc }
    if let work, journalWorkTypes.contains(work), source == "journal" { return .article }
    return .misc
}
```

`ios/HashiyaKit/Sources/HashiyaBibTeX/CiteKeys.swift`:
```swift
import Foundation
import HashiyaModel

/// Google Scholar–style cite keys: surname, year, first meaningful title word, e.g. "vaswani2017attention".
public enum CiteKeys {
    private static let stopWords: Set<String> = [
        "a", "an", "the", "on", "of", "in", "for", "and", "to", "with", "from", "by", "via", "is", "are", "towards", "toward",
        "using", "at",
    ]

    /// The key before collision suffixes. Always starts with a letter: "paper" stands in for a surname with no Latin letters,
    /// and leading digits are dropped from the surname ("Group 7" has no surname letters, so it becomes "paper").
    public static func base(_ paper: Paper) -> String {
        let lastName = paper.authors.first.flatMap { splitOnWhitespace(kotlinTrim($0.name)).last }.map(asciiFold) ?? ""
        let surname = String(lastName.drop { $0 >= "0" && $0 <= "9" })
        let year = paper.year.map { String($0) } ?? "nd"
        let word = splitOnWhitespace(kotlinTrim(paper.title)).map(asciiFold).first { !$0.isEmpty && !stopWords.contains($0) } ?? ""
        return (surname.isEmpty ? "paper" : surname) + year + word
    }

    /// Keys for `papers`, in order: each its `base` or the base plus the first free suffix, avoiding `taken` and each other.
    public static func assign(_ papers: [Paper], taken: Set<String>) -> [String] {
        var used = taken
        return papers.map { paper in
            let base = base(paper)
            var n = 0
            while used.contains(base + keySuffix(n)) { n += 1 }
            let key = base + keySuffix(n)
            used.insert(key)
            return key
        }
    }
}

private let specialLetters: [Unicode.Scalar: String] = ["ß": "ss", "æ": "ae", "ø": "o", "đ": "d", "ł": "l", "ı": "i", "œ": "oe"]
private let asciiLetters: ClosedRange<Unicode.Scalar> = "a"..."z"
private let asciiDigits: ClosedRange<Unicode.Scalar> = "0"..."9"

/// Lowercase ASCII letters and digits only: accents dropped, a few letters spelled out, everything else (Arabic too) removed.
/// NFKD also splits ligatures ("ﬃ" → "ffi") and full-width letters. Works on scalars: "é" is one Character but two scalars
/// after NFKD, and only the "e" is kept.
func asciiFold(_ text: String) -> String {
    var spelled = String.UnicodeScalarView()
    for scalar in text.lowercased().unicodeScalars {
        if let letters = specialLetters[scalar] {
            spelled.append(contentsOf: letters.unicodeScalars)
        } else {
            spelled.append(scalar)
        }
    }
    let decomposed = String(spelled).decomposedStringWithCompatibilityMapping.unicodeScalars
    return string(decomposed.filter { asciiLetters.contains($0) || asciiDigits.contains($0) })
}

/// 0 → "", 1 → "a" … 26 → "z", 27 → "aa", 28 → "ab" …
func keySuffix(_ n: Int) -> String {
    var letters: [Unicode.Scalar] = []
    var rest = n
    while rest > 0 {
        rest -= 1
        letters.append(Unicode.Scalar(UInt8(97 + rest % 26)))
        rest /= 26
    }
    return string(letters.reversed())
}
```

- [ ] **Step 5: Run them and see them pass**

Run: the Step 3 command.
Expected: PASS: `✔ Test run with … tests passed`, `** TEST SUCCEEDED **`. If a ported Android case fails, the Swift port is wrong. Never change an expected string that came from Android.

- [ ] **Step 6: Commit**

```bash
git add ios/HashiyaKit/Package.swift ios/project.yml ios/HashiyaKit/Sources/HashiyaBibTeX/LatexText.swift ios/HashiyaKit/Sources/HashiyaBibTeX/EntryType.swift ios/HashiyaKit/Sources/HashiyaBibTeX/CiteKeys.swift ios/HashiyaKit/Tests/HashiyaBibTeXTests/LatexTextTests.swift ios/HashiyaKit/Tests/HashiyaBibTeXTests/EntryTypeTests.swift ios/HashiyaKit/Tests/HashiyaBibTeXTests/CiteKeysTests.swift
git commit -m "feat: add LaTeX escaping, BibTeX entry types and cite keys on iOS"
```

---

### Task 3: `HashiyaBibTeX` — entries and files

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaBibTeX/BibTeX.swift`
- Test: Create `ios/HashiyaKit/Tests/HashiyaBibTeXTests/BibTeXTests.swift`

**Interfaces:**
- Consumes: `entryType(_:)`, `EntryType`, `escapeLatex`, `cleanWhitespace`, `protectCapitals`, `kotlinTrim` and `string(_:)` (Task 2); `CiteKeys.base` (Task 2, in the Unicode test); `Paper` and `PublicationDetails` (Task 1).
- Produces (module `HashiyaBibTeX`, `public`):
  - `struct CitablePaper: Equatable, Sendable { let paper: Paper; let citeKey: String; init(paper:citeKey:) }`
  - `enum BibTeX { static func entry(_ paper: CitablePaper) -> String; static func file(_ papers: [CitablePaper]) -> String }`. Task 8's `GRDBCitationRepository` calls these.

Every case in Android's `BibTeXTest` is ported with identical inputs and expected strings. `unicodeWhitespaceMatchesAndroid` repeats the cases of Android's on-device `BibTeXOnDeviceTest` (`core/data/src/androidTest` on `feat/collections-and-bibtex`).

- [ ] **Step 1: Write the failing test**

`ios/HashiyaKit/Tests/HashiyaBibTeXTests/BibTeXTests.swift`:
```swift
@testable import HashiyaBibTeX
import HashiyaModel
import Testing

/// Mirrors Android's core/bibtex BibTeXTest, case for case, and the on-device BibTeXOnDeviceTest. The output must match
/// Android's byte for byte.
struct BibTeXTests {
    private func paper(
        title: String = "Deep learning",
        authors: [String] = ["Yann LeCun", "Yoshua Bengio"],
        year: Int? = 2015,
        venue: String? = "Nature",
        doi: String? = "10.1038/nature14539",
        pdf: String? = nil,
        details: PublicationDetails = PublicationDetails(workType: "article", sourceType: "journal")
    ) -> Paper {
        Paper(
            openAlexID: "W1",
            doi: doi,
            title: title,
            authors: authors.map { Author(name: $0) },
            year: year,
            venue: venue,
            abstract: nil,
            citationCount: 0,
            isOpenAccess: pdf != nil,
            openAccessPDFURL: pdf,
            publication: details
        )
    }

    private func lines(_ text: String) -> [String] {
        text.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
    }

    @Test func journalArticleWithEveryField() {
        let entry = BibTeX.entry(
            CitablePaper(
                paper: paper(
                    details: PublicationDetails(
                        workType: "article",
                        sourceType: "journal",
                        publisher: "Springer Nature",
                        volume: "521",
                        issue: "7553",
                        firstPage: "436",
                        lastPage: "444"
                    )
                ),
                citeKey: "lecun2015deep"
            )
        )
        #expect(entry == """
            @article{lecun2015deep,
              author = {Yann LeCun and Yoshua Bengio},
              title = {Deep learning},
              year = {2015},
              journal = {Nature},
              volume = {521},
              number = {7553},
              pages = {436--444},
              doi = {10.1038/nature14539}
            }

            """)
    }

    @Test func conferencePaperIsInproceedingsWithBooktitle() {
        let entry = BibTeX.entry(
            CitablePaper(
                paper: paper(
                    title: "BERT: Pre-training of Deep Bidirectional Transformers",
                    authors: ["Jacob Devlin"],
                    year: 2019,
                    venue: "North American Chapter of the Association for Computational Linguistics",
                    doi: "10.18653/v1/n19-1423",
                    details: PublicationDetails(workType: "article", sourceType: "conference", firstPage: "4171", lastPage: "4186")
                ),
                citeKey: "devlin2019bert"
            )
        )
        #expect(entry == """
            @inproceedings{devlin2019bert,
              author = {Jacob Devlin},
              title = {{BERT:} Pre-training of Deep Bidirectional Transformers},
              year = {2019},
              booktitle = {North American Chapter of the Association for Computational Linguistics},
              pages = {4171--4186},
              doi = {10.18653/v1/n19-1423}
            }

            """)
    }

    @Test func arxivPreprintIsMiscWithEprint() {
        let entry = BibTeX.entry(
            CitablePaper(
                paper: paper(
                    title: "Attention Is All You Need",
                    authors: ["Ashish Vaswani"],
                    year: 2017,
                    venue: "arXiv (Cornell University)",
                    doi: "10.48550/arxiv.1706.03762",
                    pdf: "https://arxiv.org/pdf/1706.03762",
                    details: PublicationDetails(workType: "preprint", sourceType: "repository", publisher: "Cornell University")
                ),
                citeKey: "vaswani2017attention"
            )
        )
        #expect(entry == """
            @misc{vaswani2017attention,
              author = {Ashish Vaswani},
              title = {Attention Is All You Need},
              year = {2017},
              howpublished = {arXiv (Cornell University)},
              publisher = {Cornell University},
              doi = {10.48550/arxiv.1706.03762},
              eprint = {1706.03762},
              archivePrefix = {arXiv}
            }

            """)
    }

    @Test func venueFieldPerEntryType() {
        func firstVenueLine(_ work: String, _ source: String?) -> String? {
            lines(BibTeX.entry(CitablePaper(paper: paper(venue: "Venue", details: PublicationDetails(workType: work, sourceType: source)), citeKey: "k")))
                .first { $0.contains("{Venue}") }
        }
        #expect(firstVenueLine("book-chapter", "book series") == "  booktitle = {Venue},")
        #expect(firstVenueLine("book", nil) == nil)
        #expect(firstVenueLine("dissertation", nil) == "  school = {Venue},")
        #expect(firstVenueLine("report", nil) == "  institution = {Venue},")
        #expect(firstVenueLine("dataset", nil) == "  howpublished = {Venue},")
    }

    @Test func entryTypeLines() {
        func header(_ work: String, _ source: String?) -> String? {
            lines(BibTeX.entry(CitablePaper(paper: paper(details: PublicationDetails(workType: work, sourceType: source)), citeKey: "k"))).first
        }
        #expect(header("book-chapter", nil) == "@incollection{k,")
        #expect(header("book", nil) == "@book{k,")
        #expect(header("dissertation", nil) == "@phdthesis{k,")
        #expect(header("report", nil) == "@techreport{k,")
    }

    @Test func publisherOnlyOnTypesThatTakeOne() {
        func hasPublisher(_ work: String, _ source: String?) -> Bool {
            BibTeX.entry(
                CitablePaper(paper: paper(details: PublicationDetails(workType: work, sourceType: source, publisher: "P")), citeKey: "k")
            ).contains("publisher = {P}")
        }
        #expect(hasPublisher("article", "journal") == false)
        #expect(hasPublisher("article", "conference") == false)
        #expect(hasPublisher("book", nil) == true)
        #expect(hasPublisher("book-chapter", nil) == true)
        #expect(hasPublisher("report", nil) == true)
        #expect(hasPublisher("preprint", nil) == true)
    }

    @Test func missingFieldsAreLeftOutAndUrlOnlyWithoutDoi() {
        let entry = BibTeX.entry(
            CitablePaper(
                paper: paper(
                    title: "Notes",
                    authors: [],
                    year: nil,
                    venue: nil,
                    doi: nil,
                    pdf: "https://example.org/a_b.pdf",
                    details: PublicationDetails()
                ),
                citeKey: "papernd"
            )
        )
        #expect(entry == """
            @misc{papernd,
              title = {Notes},
              url = {https://example.org/a_b.pdf}
            }

            """)
        let withDoi = BibTeX.entry(CitablePaper(paper: paper(pdf: "https://example.org/x.pdf"), citeKey: "k"))
        #expect(withDoi.contains("url =") == false)
    }

    @Test func singlePageAndEqualPages() {
        func pages(_ first: String?, _ last: String?) -> String? {
            lines(
                BibTeX.entry(
                    CitablePaper(
                        paper: paper(details: PublicationDetails(workType: "article", sourceType: "journal", firstPage: first, lastPage: last)),
                        citeKey: "k"
                    )
                )
            ).first { line in line.drop { $0 == " " }.hasPrefix("pages") }
        }
        #expect(pages("e12", nil) == "  pages = {e12},")
        #expect(pages("7", "7") == "  pages = {7},")
        #expect(pages(nil, "9") == nil)
    }

    @Test func escapesValuesButNotDoiOrUrl() {
        let entry = BibTeX.entry(
            CitablePaper(
                paper: paper(
                    title: "R&D at 50%: the {x}_y   case",
                    authors: ["A. O'Brien & Co"],
                    venue: "J. Stuff & Things",
                    doi: "10.1000/a_b%c"
                ),
                citeKey: "k"
            )
        )
        #expect(entry.contains(#"  author = {A. O'Brien \& Co},"#))
        // "R&D" has a capital after its first character, so it is protected like an acronym.
        #expect(entry.contains(#"  title = {{R\&D} at 50\%: the \textbraceleft{}x\textbraceright{}\_y case},"#))
        #expect(entry.contains(#"  journal = {J. Stuff \& Things},"#))
        #expect(entry.contains("  doi = {10.1000/a_b%c}"))
    }

    @Test func protectsCapitalsInTitleAndVenueButNotAuthors() {
        let entry = BibTeX.entry(
            CitablePaper(paper: paper(title: "ImageNet on iPhone", authors: ["DeWitt McDonald"], venue: "IEEE TPAMI"), citeKey: "k")
        )
        #expect(entry.contains("  title = {{ImageNet} on {iPhone}},"))
        #expect(entry.contains("  journal = {{IEEE} {TPAMI}},"))
        #expect(entry.contains("  author = {DeWitt McDonald},"))
        // A word starting with an escaped character gets double braces, so BibTeX doesn't treat it as one special character.
        let hashtag = BibTeX.entry(CitablePaper(paper: paper(title: "The #MeToo movement"), citeKey: "k"))
        #expect(hashtag.contains(#"  title = {The {{\#MeToo}} movement},"#))
    }

    @Test func arabicTextStaysUtf8() {
        let entry = BibTeX.entry(CitablePaper(paper: paper(title: "تطبيقات التعلم العميق", authors: ["محمد علي"]), citeKey: "paper2015"))
        #expect(entry.contains("  author = {محمد علي},"))
        #expect(entry.contains("  title = {تطبيقات التعلم العميق},"))
    }

    @Test func fileSortsByKeyAndSeparatesWithOneBlankLine() {
        let b = CitablePaper(paper: paper(title: "B"), citeKey: "bkey")
        let a = CitablePaper(paper: paper(title: "A"), citeKey: "akey")
        let file = BibTeX.file([b, a])
        #expect(file == BibTeX.entry(a) + "\n" + BibTeX.entry(b))
        #expect(file.hasSuffix("}\n"))
        #expect(BibTeX.file([]) == "")
    }

    @Test func organisationAndCommaAuthorsStayWhole() {
        let entry = BibTeX.entry(
            CitablePaper(paper: paper(authors: ["Bill and Melinda Gates Foundation", "Smith, Jane", "Anand Kumar"]), citeKey: "k")
        )
        #expect(entry.contains("  author = {{Bill and Melinda Gates Foundation} and {Smith, Jane} and Anand Kumar},"))
    }

    @Test func bareArxivPrefixHasNoEprint() {
        let entry = BibTeX.entry(CitablePaper(paper: paper(doi: "10.48550/arXiv."), citeKey: "k"))
        #expect(entry.contains("eprint") == false)
        #expect(entry.contains("archivePrefix") == false)
    }

    @Test func urlBracesArePercentEncoded() {
        let entry = BibTeX.entry(CitablePaper(paper: paper(doi: nil, pdf: "https://x.org/a{b}.pdf"), citeKey: "k"))
        #expect(entry.contains("  url = {https://x.org/a%7Bb%7D.pdf}"))
    }

    /// The cases of Android's on-device BibTeXOnDeviceTest: Unicode spaces that crashed Android's regex engine on copy.
    @Test func unicodeWhitespaceMatchesAndroid() {
        let unicode = paper(
            title: "\u{0085}Deep\u{2028}learning\u{00A0}for\u{3000}graphs",
            authors: ["José\u{00A0}Müller", "محمد علي"],
            year: 2015
        )
        #expect(CiteKeys.base(unicode) == "muller2015deep")
        #expect(CiteKeys.assign([unicode, unicode], taken: []) == ["muller2015deep", "muller2015deepa"])
        let file = BibTeX.file([CitablePaper(paper: unicode, citeKey: "muller2015deep")])
        #expect(file.contains("  title = {Deep learning for graphs},"))
        #expect(file.contains("  author = {José Müller and محمد علي},"))
    }

    // Swift-only.

    @Test func anUppercaseArxivPrefixStillGivesAnEprint() {
        let entry = BibTeX.entry(CitablePaper(paper: paper(doi: "10.48550/ARXIV.2101.00001"), citeKey: "k"))
        #expect(entry.contains("  eprint = {2101.00001},"))
        #expect(entry.contains("  archivePrefix = {arXiv}"))
    }

    @Test func anEntryWithNoFieldsHasAnEmptyBody() {
        let entry = BibTeX.entry(
            CitablePaper(paper: paper(title: " ", authors: [], year: nil, venue: nil, doi: nil, details: PublicationDetails()), citeKey: "papernd")
        )
        #expect(entry == "@misc{papernd,\n}\n")
    }
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaBibTeXTests/BibTeXTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL: `cannot find 'BibTeX' in scope`, `cannot find 'CitablePaper' in scope`, then `** TEST FAILED **`.

- [ ] **Step 3: Implement**

`ios/HashiyaKit/Sources/HashiyaBibTeX/BibTeX.swift`:
```swift
import Foundation
import HashiyaModel

/// A saved paper ready to cite: its metadata and its stored key.
public struct CitablePaper: Equatable, Sendable {
    public let paper: Paper
    public let citeKey: String

    public init(paper: Paper, citeKey: String) {
        self.paper = paper
        self.citeKey = citeKey
    }
}

public enum BibTeX {
    private static let arxivDOIPrefix = Array("10.48550/arxiv.".unicodeScalars)

    /// One entry, fields in a fixed order, empty ones left out, ending with a newline.
    public static func entry(_ paper: CitablePaper) -> String {
        let fields = fields(paper.paper)
        let body = fields.isEmpty ? "" : fields.map { "  \($0.name) = {\($0.value)}" }.joined(separator: ",\n") + "\n"
        return "@\(entryType(paper.paper.publication).bibName){\(paper.citeKey),\n\(body)}\n"
    }

    /// Entries sorted by cite key, separated by one blank line, ending with a newline. Empty for no papers.
    public static func file(_ papers: [CitablePaper]) -> String {
        papers.sorted { $0.citeKey < $1.citeKey }.map(entry).joined(separator: "\n")
    }

    private static func fields(_ paper: Paper) -> [(name: String, value: String)] {
        let details = paper.publication
        let type = entryType(details)
        func text(_ value: String?) -> String? {
            guard let value else { return nil }
            let clean = cleanWhitespace(value)
            return clean.isEmpty ? nil : escapeLatex(clean)
        }
        let doi = paper.doi.map(kotlinTrim).flatMap { $0.isEmpty ? nil : $0 }
        let firstPage = text(details.firstPage)
        let lastPage = text(details.lastPage)
        let eprint = doi.flatMap(arxivEprint)
        let url = doi == nil ? paper.openAccessPDFURL.map(kotlinTrim).flatMap { $0.isEmpty ? nil : $0 } : nil
        let authors = paper.authors.compactMap { text($0.name) }.map { splitsAuthor($0) ? "{\($0)}" : $0 }

        var fields: [(name: String, value: String)] = []
        if !authors.isEmpty { fields.append(("author", authors.joined(separator: " and "))) }
        if let title = text(paper.title) { fields.append(("title", protectCapitals(title))) }
        if let year = paper.year { fields.append(("year", String(year))) }
        if let field = type.venueField, let venue = text(paper.venue) {
            fields.append((field, field == "journal" || field == "booktitle" ? protectCapitals(venue) : venue))
        }
        if let volume = text(details.volume) { fields.append(("volume", volume)) }
        if let issue = text(details.issue) { fields.append(("number", issue)) }
        if let firstPage {
            fields.append(("pages", lastPage == nil || lastPage == firstPage ? firstPage : "\(firstPage)--\(lastPage!)"))
        }
        if type.hasPublisher, let publisher = text(details.publisher) { fields.append(("publisher", publisher)) }
        if let doi { fields.append(("doi", doi)) }
        if let eprint {
            fields.append(("eprint", eprint))
            fields.append(("archivePrefix", "arXiv"))
        }
        // Not escaped (styles pass it to \url), but braces are percent-encoded so they can't unbalance the entry.
        if let url {
            fields.append(("url", url.replacingOccurrences(of: "{", with: "%7B").replacingOccurrences(of: "}", with: "%7D")))
        }
        return fields
    }

    /// The part after "10.48550/arXiv." (any case), or nil when the DOI doesn't start with it or nothing follows.
    private static func arxivEprint(_ doi: String) -> String? {
        let scalars = Array(doi.unicodeScalars)
        guard scalars.count >= arxivDOIPrefix.count,
              zip(scalars, arxivDOIPrefix).allSatisfy({ Character($0).lowercased() == String(Character($1)) }) else { return nil }
        let rest = string(scalars.dropFirst(arxivDOIPrefix.count))
        return rest.isEmpty ? nil : rest
    }

    /// BibTeX splits authors on " and " and reads a comma as "Last, First", so names with either are kept whole in braces.
    /// Android's `(?i)\sand\s|,`: after `cleanWhitespace` the only whitespace left is U+0020, and `(?i)` is ASCII-only.
    private static func splitsAuthor(_ name: String) -> Bool {
        let s = Array(name.unicodeScalars)
        if s.contains(",") { return true }
        guard s.count >= 5 else { return false }
        for i in 0...(s.count - 5) where s[i] == " " && s[i + 4] == " " {
            if (s[i + 1] == "a" || s[i + 1] == "A") && (s[i + 2] == "n" || s[i + 2] == "N") && (s[i + 3] == "d" || s[i + 3] == "D") {
                return true
            }
        }
        return false
    }
}
```

- [ ] **Step 4: Run it and see it pass**

Run: the Step 2 command without the suite (`-only-testing:HashiyaBibTeXTests`).
Expected: PASS: `✔ Test run with … tests passed`, `** TEST SUCCEEDED **`. As in Task 2, a failing Android case means the port is wrong, never the expected string.

- [ ] **Step 5: Run the full scheme**

Run: `cd ios && xcodegen generate --spec project.yml && xcodebuild test -project Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -skip-testing:HashiyaUITests 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: `** TEST SUCCEEDED **`, with `HashiyaBibTeXTests` among the suites run. In a cloud session, skip this step and rely on Step 6's CI run.

- [ ] **Step 6: Commit and push**

```bash
git add ios/HashiyaKit/Sources/HashiyaBibTeX/BibTeX.swift ios/HashiyaKit/Tests/HashiyaBibTeXTests/BibTeXTests.swift
git commit -m "feat: build BibTeX entries and files on iOS, matching Android"
git log --format='%an <%ae>' origin/main..HEAD | sort -u   # only: Fady <fady.fouad.a@gmail.com>
git push
```
Then wait for the "iOS" workflow's run on this commit's SHA. Expected: `success`. On failure, read the failed job's log and fix before Task 4.

---

### Task 4: `HashiyaNetwork` and the mapping: work type, `biblio`, source type and publisher

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaNetwork/NetworkModels.swift`, `ios/HashiyaKit/Sources/HashiyaNetwork/OpenAlexSearchClient.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaData/PaperMapping.swift` (only `NetworkWork.asPaper()`)
- Replace: `ios/HashiyaKit/Sources/HashiyaTesting/Resources/Fixtures/works_page.json` (Android's updated copy, byte for byte)
- Test: Modify `ios/HashiyaKit/Tests/HashiyaNetworkTests/NetworkModelsTests.swift`, `OpenAlexLookupClientTests.swift`, `OpenAlexSearchClientTests.swift`, `ios/HashiyaKit/Tests/HashiyaDataTests/PaperMappingTests.swift`

**Interfaces:**
- Consumes: Task 1's `PublicationDetails` and `Paper.publication` (init parameter `publication:`, last, defaulting to `PublicationDetails()`).
- Produces (module `HashiyaNetwork`, `public`):
  - `OpenAlexSearchClient.selectFields` ends with `,type,biblio`. The lookup client reuses it, so refetches get the fields too.
  - `struct NetworkBiblio: Decodable, Equatable, Sendable { let volume, issue, firstPage, lastPage: String? }`, with `init(volume:issue:firstPage:lastPage:)`, all defaulting to nil.
  - `NetworkWork.type: String?` and `NetworkWork.biblio: NetworkBiblio?`, with init parameters `type:` and `biblio:` (last, default nil).
  - `NetworkSource.type: String?` and `NetworkSource.hostOrganizationName: String?`, with `init(displayName:type:hostOrganizationName:)`; the last two default to nil.
- Produces (module `HashiyaData`): `NetworkWork.asPaper()` fills `publication`. The publisher is `primaryLocation?.source?.hostOrganizationName`. Each string is trimmed, and a blank one becomes nil.

- [ ] **Step 1: Copy Android's fixture**

This branch is based on the Android branch, so Android's updated fixture is in the working tree. The only change from the current copy: the first work gains `type`, `biblio`, and the source's `type` and `host_organization_name`.

```bash
cp core/network/src/test/resources/works_page.json ios/HashiyaKit/Sources/HashiyaTesting/Resources/Fixtures/works_page.json
cmp core/network/src/test/resources/works_page.json ios/HashiyaKit/Sources/HashiyaTesting/Resources/Fixtures/works_page.json && echo identical
```
Expected: `identical`. If the Android branch has been merged and rebased away, take it from `origin/main` instead: `git show origin/main:core/network/src/test/resources/works_page.json > ios/HashiyaKit/Sources/HashiyaTesting/Resources/Fixtures/works_page.json`.

- [ ] **Step 2: Write the failing tests**

`ios/HashiyaKit/Tests/HashiyaNetworkTests/NetworkModelsTests.swift`:

1. At the end of `parsesACompleteWork`, add:
```swift
        #expect(work.type == "preprint")
        #expect(work.biblio == NetworkBiblio(volume: "30", issue: nil, firstPage: "5998", lastPage: "6008"))
        #expect(work.primaryLocation?.source?.type == "conference")
        #expect(work.primaryLocation?.source?.hostOrganizationName == "Neural Information Processing Systems Foundation")
```
2. At the end of `parsesASparseWork`, add:
```swift
        #expect(work.type == nil)
        #expect(work.biblio == nil)
```
3. Add after `missingResultsIsAnEmptyPage`:
```swift
    @Test func missingBiblioAndSourceFieldsDecodeAsNil() throws {
        let json = #"{"id": "https://openalex.org/W1", "biblio": {}, "primary_location": {"source": {"display_name": "Nature"}}}"#
        let work = try JSONDecoder().decode(NetworkWork.self, from: Data(json.utf8))
        #expect(work.biblio == NetworkBiblio())
        #expect(work.primaryLocation?.source == NetworkSource(displayName: "Nature"))
        #expect(work.primaryLocation?.source?.type == nil)
        #expect(work.primaryLocation?.source?.hostOrganizationName == nil)
    }
```

`ios/HashiyaKit/Tests/HashiyaNetworkTests/OpenAlexLookupClientTests.swift`: add after `workRequestsTheWorkWithSelectedFields` (Android's `workFieldsIncludeTypeAndBiblio`):
```swift
    @Test func selectedFieldsIncludeTypeAndBiblio() {
        let fields = OpenAlexSearchClient.selectFields.split(separator: ",").map(String.init)
        #expect(fields.contains("type"))
        #expect(fields.contains("biblio"))
    }
```

`ios/HashiyaKit/Tests/HashiyaNetworkTests/OpenAlexSearchClientTests.swift`: two tests spell out the select list. Change both.

1. Line 44, the expected `"select"` value, becomes:
```swift
            "select": "id,doi,display_name,publication_year,primary_location,authorships,cited_by_count,open_access,best_oa_location,abstract_inverted_index,type,biblio",
```
2. Line 180, the logged request, becomes:
```swift
        #expect(logged.first == "GET /works?search=bert&per_page=25&cursor=%2A&select=id%2Cdoi%2Cdisplay_name%2Cpublication_year%2Cprimary_location%2Cauthorships%2Ccited_by_count%2Copen_access%2Cbest_oa_location%2Cabstract_inverted_index%2Ctype%2Cbiblio&api_key=██ → 200")
```

`ios/HashiyaKit/Tests/HashiyaDataTests/PaperMappingTests.swift`:

1. In `mapsACompleteWork`, the expected `Paper(…)` gains a last argument after `openAccessPDFURL: "https://arxiv.org/pdf/1706.03762"`:
```swift
            openAccessPDFURL: "https://arxiv.org/pdf/1706.03762",
            publication: PublicationDetails(
                workType: "preprint",
                sourceType: "conference",
                publisher: "Neural Information Processing Systems Foundation",
                volume: "30",
                issue: nil,
                firstPage: "5998",
                lastPage: "6008"
            )
```
2. `mapsASparseWork` stays as it is: `Paper(openAlexID: "W4385245566", title: "")` already has an empty `publication`.
3. Add after `missingOpenAccessIsFalse` (Android's `mapsPublicationDetails` and `blankPublicationStringsBecomeNull`):
```swift
    @Test func mapsPublicationDetails() {
        let paper = NetworkWork(
            id: "https://openalex.org/W1",
            primaryLocation: NetworkLocation(source: NetworkSource(displayName: "Nature", type: "journal", hostOrganizationName: "Springer Nature")),
            type: "article",
            biblio: NetworkBiblio(volume: "521", issue: "7553", firstPage: "436", lastPage: "444")
        ).asPaper()

        #expect(paper.publication == PublicationDetails(
            workType: "article",
            sourceType: "journal",
            publisher: "Springer Nature",
            volume: "521",
            issue: "7553",
            firstPage: "436",
            lastPage: "444"
        ))
    }

    @Test func blankPublicationStringsBecomeNil() {
        let paper = NetworkWork(
            id: "https://openalex.org/W1",
            primaryLocation: NetworkLocation(source: NetworkSource(displayName: "X", type: " ", hostOrganizationName: "")),
            type: "",
            biblio: NetworkBiblio(volume: " ", issue: nil, firstPage: "", lastPage: nil)
        ).asPaper()

        #expect(paper.publication == PublicationDetails())
    }

    @Test func publicationStringsAreTrimmed() {
        let paper = NetworkWork(id: "https://openalex.org/W1", type: " article\n", biblio: NetworkBiblio(volume: " 12 ")).asPaper()
        #expect(paper.publication.workType == "article")
        #expect(paper.publication.volume == "12")
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaNetworkTests -only-testing:HashiyaDataTests/PaperMappingTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: build errors: `value of type 'NetworkWork' has no member 'type'`, `cannot find 'NetworkBiblio' in scope`, `extra arguments at positions … in call` for `NetworkSource`.

- [ ] **Step 4: Implement the models**

`ios/HashiyaKit/Sources/HashiyaNetwork/OpenAlexSearchClient.swift`: replace `selectFields` with (Android's `WORK_FIELDS` order):
```swift
    public static let selectFields =
        "id,doi,display_name,publication_year,primary_location,authorships,cited_by_count,open_access,best_oa_location,abstract_inverted_index,type,biblio"
```

`ios/HashiyaKit/Sources/HashiyaNetwork/NetworkModels.swift`:

1. In `NetworkWork`:
   - Add the two properties after `abstractInvertedIndex`:
```swift
    /// OpenAlex's work type, e.g. "article", "preprint", "book-chapter".
    public let type: String?
    public let biblio: NetworkBiblio?
```
   - Add the two init parameters after `abstractInvertedIndex: [String: [Int]]? = nil`:
```swift
        abstractInvertedIndex: [String: [Int]]? = nil,
        type: String? = nil,
        biblio: NetworkBiblio? = nil
```
   - At the end of the init's body, add:
```swift
        self.type = type
        self.biblio = biblio
```
   - In `CodingKeys`, change the first case line to `case id, doi, authorships, type, biblio`.
   - At the end of `init(from:)`, add:
```swift
        type = try container.decodeIfPresent(String.self, forKey: .type)
        biblio = try container.decodeIfPresent(NetworkBiblio.self, forKey: .biblio)
```
2. Add after `NetworkWork`:
```swift
/// A work's `biblio`: where it sits in its source. Every field may be missing or null.
public struct NetworkBiblio: Decodable, Equatable, Sendable {
    public let volume: String?
    public let issue: String?
    public let firstPage: String?
    public let lastPage: String?

    public init(volume: String? = nil, issue: String? = nil, firstPage: String? = nil, lastPage: String? = nil) {
        self.volume = volume
        self.issue = issue
        self.firstPage = firstPage
        self.lastPage = lastPage
    }

    enum CodingKeys: String, CodingKey {
        case volume, issue
        case firstPage = "first_page"
        case lastPage = "last_page"
    }
}
```
3. Replace `NetworkSource` with:
```swift
public struct NetworkSource: Decodable, Equatable, Sendable {
    public let displayName: String?
    /// e.g. "journal", "conference", "repository".
    public let type: String?
    /// The publisher, e.g. "Springer Nature".
    public let hostOrganizationName: String?

    public init(displayName: String?, type: String? = nil, hostOrganizationName: String? = nil) {
        self.displayName = displayName
        self.type = type
        self.hostOrganizationName = hostOrganizationName
    }

    enum CodingKeys: String, CodingKey {
        case type
        case displayName = "display_name"
        case hostOrganizationName = "host_organization_name"
    }
}
```
The synthesized `Decodable` uses `decodeIfPresent` for optionals, so a missing key or `null` decodes as nil.

`ios/HashiyaKit/Sources/HashiyaData/PaperMapping.swift`:

1. In `NetworkWork.asPaper()`, after `openAccessPDFURL: bestOALocation?.pdfURL`, add:
```swift
            openAccessPDFURL: bestOALocation?.pdfURL,
            publication: asPublicationDetails()
```
2. Add below that extension's `asPaper()`, inside the same `extension NetworkWork`:
```swift
    /// The citation details OpenAlex reports for this work; blank strings become nil.
    func asPublicationDetails() -> PublicationDetails {
        let source = primaryLocation?.source
        return PublicationDetails(
            workType: nilIfBlank(type),
            sourceType: nilIfBlank(source?.type),
            publisher: nilIfBlank(source?.hostOrganizationName),
            volume: nilIfBlank(biblio?.volume),
            issue: nilIfBlank(biblio?.issue),
            firstPage: nilIfBlank(biblio?.firstPage),
            lastPage: nilIfBlank(biblio?.lastPage)
        )
    }
```
3. Add near `shortOpenAlexID` at the top of the file:
```swift
/// `value` trimmed, or nil when that leaves nothing.
private func nilIfBlank(_ value: String?) -> String? {
    guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else { return nil }
    return trimmed
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: the Step 3 command, then the whole package, since other suites decode the fixture:
`(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2') 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: `** TEST SUCCEEDED **`, with no `✘` lines. In a cloud session, push and read the "iOS" workflow for this commit instead.

- [ ] **Step 6: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaNetwork/NetworkModels.swift ios/HashiyaKit/Sources/HashiyaNetwork/OpenAlexSearchClient.swift \
  ios/HashiyaKit/Sources/HashiyaData/PaperMapping.swift ios/HashiyaKit/Sources/HashiyaTesting/Resources/Fixtures/works_page.json \
  ios/HashiyaKit/Tests/HashiyaNetworkTests/NetworkModelsTests.swift ios/HashiyaKit/Tests/HashiyaNetworkTests/OpenAlexLookupClientTests.swift \
  ios/HashiyaKit/Tests/HashiyaNetworkTests/OpenAlexSearchClientTests.swift ios/HashiyaKit/Tests/HashiyaDataTests/PaperMappingTests.swift
git commit -m "feat: fetch work type, biblio and publisher from OpenAlex on iOS"
```

---

### Task 5: `HashiyaDatabase`: migration `v4`, collections, and links and cite keys through delete and restore

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaDatabase/HashiyaDatabase.swift`, `Records.swift`, `PaperStore.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaData/PaperMapping.swift` (`asRecords` and `PaperWithAuthors.asPaper()` only)
- Test: Modify `ios/HashiyaKit/Tests/HashiyaDatabaseTests/MigrationTests.swift`, `PaperStoreTests.swift`, `ios/HashiyaKit/Tests/HashiyaDataTests/PaperMappingTests.swift`
- Test: Create `ios/HashiyaKit/Tests/HashiyaDatabaseTests/CollectionStoreTests.swift`

**Interfaces:**
- Consumes: Task 1's `PublicationDetails` and `collectionNameKey(_:)` (trim, then `lowercased()`; the tests key collections with it).
- Produces (module `HashiyaDatabase`, `public`):
  - `PaperRecord`:
    - new properties `workType`, `sourceType`, `publisher`, `volume`, `issue`, `firstPage`, `lastPage`, `citeKey: String?` and `detailsFetched: Bool`;
    - `init(id:openAlexID:doi:title:year:venue:abstract:citationCount:isOpenAccess:oaPDFURL:savedAt:readingStatus:publication:citeKey:detailsFetched:)`, where the last three default to `PublicationDetails()`, `nil` and `false`;
    - `var publication: PublicationDetails { get }`.
  - `struct CollectionRecord: Codable, Equatable, Sendable, FetchableRecord, MutablePersistableRecord` (`collections`): `id: Int64?`, `name`, `nameKey`, `createdAt: Int64`; `init(id:name:nameKey:createdAt:)`.
  - `struct CollectionPaperRecord: Codable, Equatable, Sendable, FetchableRecord, PersistableRecord` (`collection_papers`): `collectionID: Int64`, `paperID: String`, `addedAt: Int64`; `init(collectionID:paperID:addedAt:)`.
  - `struct CollectionWithCount: Codable, Equatable, Sendable, FetchableRecord`: `id: Int64`, `name: String`, `paperCount: Int`; `init(id:name:paperCount:)`.
  - `DeletedPaper.collectionLinks: [CollectionPaperRecord]`; `init(saved:notes:collectionLinks: = [])`.
  - `LibraryRows`:
    - `total` now means the papers in the current view (the collection, or the whole library), ignoring the search and the status;
    - new `allTotal: Int` is the whole library;
    - `init(papers:statusCounts:total:allTotal: Int? = nil)`, where nil means the same as `total`.
  - `PaperStore`:
    - `observeLibrary(match:status:collectionID: Int64? = nil)`;
    - `static librarySnapshot(_:match:status:collectionID: Int64? = nil)`;
    - `insert(paper:authors:search:notes:collectionLinks: [CollectionPaperRecord] = [])`;
    - `deleteByOpenAlexID` now also captures the links;
    - `observeCollections() -> AsyncStream<[CollectionWithCount]>`;
    - `observeCollectionIDs(openAlexID:) -> AsyncStream<Set<Int64>>`;
    - `insertCollection(name:nameKey:createdAt:) async throws -> Int64?`;
    - `renameCollection(id:name:nameKey:) async throws -> Bool`;
    - `collectionExists(id:) async throws -> Bool`;
    - `deleteCollection(id:) async throws`;
    - `addToCollection(collectionID:openAlexID:addedAt:) async throws`;
    - `removeFromCollection(collectionID:openAlexID:) async throws`.
- Produces (module `HashiyaData`):
  - `Paper.asRecords(localID:savedAt:status:citeKey: String? = nil, detailsFetched: Bool = true)`;
  - `PaperWithAuthors.asPaper()` returns the stored `publication`.

**Watch out:**
- `insert` uses `INSERT OR IGNORE` for the paper, and `OR IGNORE` also swallows a `cite_key` unique clash. A restore whose key another paper took would then silently restore nothing. So `insert` checks the key first and drops it when taken, as Android's `insertPaperWithAuthors` does.
- `v1`–`v3` must not change.

- [ ] **Step 1: Write the failing tests**

`ios/HashiyaKit/Tests/HashiyaDatabaseTests/MigrationTests.swift`:

1. Replace `theMigrationsAreV1ThenV2ThenV3` with:
```swift
    @Test func theMigrationsAreV1ThroughV4() {
        #expect(HashiyaDatabase.migrator.migrations == ["v1", "v2", "v3", "v4"])
    }
```
2. In `reopeningAFileKeepsTheLibrary`, the applied-migrations line becomes:
```swift
        #expect(try await second.read { db in try HashiyaDatabase.migrator.appliedMigrations(db) } == ["v1", "v2", "v3", "v4"])
```
3. Add after `insertVersion1Fixture(into:)` (Android's `createVersion3`):
```swift
    /// Android's `MigrationTest` version 3 fixture, as sub-project 4 left a library: one paper with a note and a status, one bare
    /// Arabic paper.
    private func version3WithFixture() throws -> DatabaseQueue {
        let queue = try version("v3")
        try queue.write { db in
            try db.execute(sql: """
                INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, oa_pdf_url, saved_at, reading_status)
                VALUES ('a', 'W1', '10.48550/arxiv.1706.03762', 'Attention Is All You Need', 2017,
                        'Neural Information Processing Systems', 'The dominant sequence transduction models', 128412, 1, NULL, 100, 'read');
                INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 0, 'Ashish Vaswani', NULL);
                INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, oa_pdf_url, saved_at, reading_status)
                VALUES ('b', 'W2', NULL, 'تطبيقات التَّعلُّم العميق', NULL, NULL, NULL, 0, 0, NULL, 200, 'to_read');
                INSERT INTO paper_notes (paper_id, summary, research_question, method, key_findings, limitations, thoughts, updated_at)
                VALUES ('a', 'Transformers', '', 'Ablation study', '', '', '', 5);
                INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes)
                VALUES ('a', 'attention is all you need', 'ashish vaswani', 'the dominant sequence transduction models',
                        'neural information processing systems', 'transformers ablation study');
                INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes) VALUES ('b', 'تطبيقات التعلم العميق', '', '', '', '');
                """)
        }
        return queue
    }
```
4. Add at the end of the suite:
```swift
    @Test func v4AddsTheCitationColumnsAndTheCollectionTables() throws {
        try HashiyaDatabase.openInMemory().read { db in
            let papers = try db.columns(in: "papers")
            let added = Array(papers.suffix(9))
            #expect(added.map(\.name) == [
                "work_type", "source_type", "publisher", "volume", "issue", "first_page", "last_page", "cite_key", "details_fetched",
            ])
            #expect(added.map(\.type) == ["TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "INTEGER"])
            #expect(added.filter(\.isNotNull).map(\.name) == ["details_fetched"])
            #expect(added.last?.defaultValueSQL == "0")

            let citeKeyIndex = try #require(try db.indexes(on: "papers").first { $0.name == "index_papers_cite_key" })
            #expect(citeKeyIndex.isUnique)
            #expect(citeKeyIndex.columns == ["cite_key"])

            let collections = try db.columns(in: "collections")
            #expect(collections.map(\.name) == ["id", "name", "name_key", "created_at"])
            #expect(collections.map(\.type) == ["INTEGER", "TEXT", "TEXT", "INTEGER"])
            #expect(collections.allSatisfy { $0.isNotNull })
            #expect(try db.primaryKey("collections").columns == ["id"])
            let nameIndex = try #require(try db.indexes(on: "collections").first { $0.name == "index_collections_name_key" })
            #expect(nameIndex.isUnique)
            #expect(nameIndex.columns == ["name_key"])

            let links = try db.columns(in: "collection_papers")
            #expect(links.map(\.name) == ["collection_id", "paper_id", "added_at"])
            #expect(links.map(\.type) == ["INTEGER", "TEXT", "INTEGER"])
            #expect(links.allSatisfy { $0.isNotNull })
            #expect(try db.primaryKey("collection_papers").columns == ["collection_id", "paper_id"])
            #expect(Set(try db.foreignKeys(on: "collection_papers").map(\.destinationTable)) == ["collections", "papers"])
            let onDelete = try String.fetchAll(db, sql: "SELECT DISTINCT on_delete FROM pragma_foreign_key_list('collection_papers')")
            #expect(onDelete == ["CASCADE"])
            let paperIndex = try #require(try db.indexes(on: "collection_papers").first { $0.name == "index_collection_papers_paper_id" })
            #expect(paperIndex.columns == ["paper_id"])
            #expect(!paperIndex.isUnique)
        }
    }

    /// A sub-project 4 install (Android's `migration3To4KeepsEverythingAndValidatesAgainstVersion4Schema`).
    @Test func migratingFromV3KeepsEverythingAndLeavesTheCitationStateEmpty() throws {
        let queue = try version3WithFixture()

        try HashiyaDatabase.migrator.migrate(queue)

        try queue.read { db in
            #expect(try String.fetchAll(db, sql: "SELECT id || ':' || reading_status FROM papers ORDER BY id") == ["a:read", "b:to_read"])
            #expect(try String.fetchAll(db, sql: "SELECT method FROM paper_notes") == ["Ablation study"])
            #expect(try String.fetchAll(db, sql: "SELECT paper_id || ':' || notes FROM paper_search ORDER BY paper_id") == [
                "a:transformers ablation study", "b:",
            ])
            #expect(try String.fetchAll(
                db,
                sql: """
                    SELECT id || ':' || details_fetched || ':' || (cite_key IS NULL) || ':' || (work_type IS NULL AND volume IS NULL)
                    FROM papers ORDER BY id
                    """
            ) == ["a:0:1:1", "b:0:1:1"])
            #expect(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM collections") == 0)
            #expect(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM collection_papers") == 0)
        }
    }

    /// Android's `libraryMigratedFromVersion3IsSearchableAndTakesCollections`.
    @Test func aLibraryMigratedFromV3IsSearchableAndTakesCollections() async throws {
        let queue = try version3WithFixture()
        try HashiyaDatabase.migrator.migrate(queue)
        let store = PaperStore(writer: queue)
        func ids(_ match: String?, collectionID: Int64? = nil) async -> [String]? {
            await first(store.observeLibrary(match: match, status: nil, collectionID: collectionID))?.papers.map(\.paper.id)
        }

        #expect(await ids("\"ablation*\"") == ["a"])
        #expect(await ids("\"التعلم*\"") == ["b"])
        let id = try #require(try await store.insertCollection(name: "Thesis", nameKey: "thesis", createdAt: 1))
        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 2)
        #expect(await ids(nil, collectionID: id) == ["a"])
    }

    /// Android's `version1LibraryMigratesAllTheWayToVersion4`.
    @Test func aV1LibraryMigratesAllTheWayToV4() throws {
        let queue = try version1()
        try insertVersion1Fixture(into: queue)

        try HashiyaDatabase.migrator.migrate(queue)

        let rows = try queue.read { db in
            try String.fetchAll(db, sql: "SELECT id || ':' || reading_status || ':' || details_fetched FROM papers ORDER BY id")
        }
        #expect(rows == ["a:to_read:0", "b:to_read:0"])
    }
```

`ios/HashiyaKit/Tests/HashiyaDatabaseTests/PaperStoreTests.swift`:

1. Replace the `library` and `ids` helpers with versions that take a collection:
```swift
    private func library(match: String? = nil, status: String? = nil, collectionID: Int64? = nil) async -> LibraryRows? {
        await value(of: store.observeLibrary(match: match, status: status, collectionID: collectionID))
    }

    private func ids(match: String? = nil, status: String? = nil, collectionID: Int64? = nil) async -> [String]? {
        await library(match: match, status: status, collectionID: collectionID)?.papers.map(\.paper.id)
    }

    /// A new collection's id, keyed by `collectionNameKey` as the repository keys it.
    private func collection(_ name: String) async throws -> Int64 {
        try #require(try await store.insertCollection(name: name, nameKey: collectionNameKey(name), createdAt: 1))
    }

    /// Restores `deleted` as the repository's Undo does, with its links.
    @discardableResult
    private func restore(_ deleted: DeletedPaper) async throws -> Bool {
        try await store.insert(
            paper: deleted.saved.paper,
            authors: deleted.saved.authors,
            search: deleted.saved.searchRow,
            notes: deleted.notes,
            collectionLinks: deleted.collectionLinks
        )
    }
```
2. Add at the end of the suite:
```swift
    @Test func publicationColumnsRoundTrip() async throws {
        let details = PublicationDetails(
            workType: "article", sourceType: "journal", publisher: "Springer Nature",
            volume: "521", issue: "7553", firstPage: "436", lastPage: "444"
        )
        var record = paper(1, savedAt: 1_000)
        record.workType = details.workType
        record.sourceType = details.sourceType
        record.publisher = details.publisher
        record.volume = details.volume
        record.issue = details.issue
        record.firstPage = details.firstPage
        record.lastPage = details.lastPage
        record.citeKey = "lecun2015deep"
        record.detailsFetched = true
        try await save(record)

        let saved = await library()?.papers.first?.paper
        #expect(saved == record)
        #expect(saved?.publication == details)
    }

    /// Android's `libraryAndCountsCanBeLimitedToACollection`, plus the totals.
    @Test func libraryAndCountsCanBeLimitedToACollection() async throws {
        try await save(paper(1, title: "Graph networks", status: "read", savedAt: 100), "Ada")
        try await save(paper(2, title: "Graph kernels", savedAt: 200), "Bo")
        try await save(paper(3, title: "Other", savedAt: 300), "Cy")
        let id = try await collection("A")
        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 1)
        try await store.addToCollection(collectionID: id, openAlexID: "W3", addedAt: 1)

        #expect(await ids(collectionID: id) == ["local-3", "local-1"])
        #expect(await ids(match: "\"graph*\"", collectionID: id) == ["local-1"])
        #expect(await ids(status: "read", collectionID: id) == ["local-1"])
        let rows = try #require(await library(match: "\"missing*\"", collectionID: id))
        #expect(rows.papers.isEmpty)
        #expect(rows.total == 2)
        #expect(rows.allTotal == 3)
        #expect(await library(collectionID: id)?.statusCounts == ["read": 1, "to_read": 1])
        #expect(await library()?.total == 3)
        #expect(await library()?.allTotal == 3)
    }

    @Test(.timeLimit(.minutes(1)))
    func anOpenCollectionViewSeesMembershipChanges() async throws {
        try await save(paper(1, savedAt: 100))
        let id = try await collection("A")
        var iterator = store.observeLibrary(match: nil, status: nil, collectionID: id).makeAsyncIterator()
        #expect(await iterator.next()?.papers.isEmpty == true)

        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 1)
        #expect(await iterator.next()?.papers.map(\.paper.id) == ["local-1"])

        try await store.removeFromCollection(collectionID: id, openAlexID: "W1")
        #expect(await iterator.next()?.papers.isEmpty == true)
    }

    @Test func deleteCapturesTheLinksTheCiteKeyAndTheFlag() async throws {
        var record = paper(1, savedAt: 100)
        record.citeKey = "ada2020paper"
        record.detailsFetched = true
        try await save(record, "Ada")
        let kept = try await collection("Kept")
        let gone = try await collection("Gone")
        try await store.addToCollection(collectionID: kept, openAlexID: "W1", addedAt: 5)
        try await store.addToCollection(collectionID: gone, openAlexID: "W1", addedAt: 6)

        let deleted = try #require(try await store.deleteByOpenAlexID("W1"))

        #expect(deleted.collectionLinks == [
            CollectionPaperRecord(collectionID: kept, paperID: "local-1", addedAt: 5),
            CollectionPaperRecord(collectionID: gone, paperID: "local-1", addedAt: 6),
        ])
        #expect(deleted.saved.paper.citeKey == "ada2020paper")
        #expect(deleted.saved.paper.detailsFetched)
        #expect(try count("collection_papers") == 0)
    }

    @Test func restoreKeepsLinksAndCiteKey() async throws {
        var record = paper(1, savedAt: 100)
        record.citeKey = "ada2020paper"
        try await save(record, "Ada")
        let first = try await collection("First")
        let second = try await collection("Second")
        try await store.addToCollection(collectionID: first, openAlexID: "W1", addedAt: 5)
        try await store.addToCollection(collectionID: second, openAlexID: "W1", addedAt: 6)
        let deleted = try #require(try await store.deleteByOpenAlexID("W1"))

        #expect(try await restore(deleted))

        #expect(await value(of: store.observeCollectionIDs(openAlexID: "W1")) == [first, second])
        #expect(await library()?.papers.first?.paper.citeKey == "ada2020paper")
        #expect(await ids(collectionID: second) == ["local-1"])
    }

    /// Android's `deleteCapturesCollectionLinksAndRestoreSkipsDeletedCollections`.
    @Test func restoreSkipsALinkWhoseCollectionWasDeleted() async throws {
        try await save(paper(1, savedAt: 100), "Ada")
        let kept = try await collection("Kept")
        let gone = try await collection("Gone")
        try await store.addToCollection(collectionID: kept, openAlexID: "W1", addedAt: 5)
        try await store.addToCollection(collectionID: gone, openAlexID: "W1", addedAt: 6)
        let deleted = try #require(try await store.deleteByOpenAlexID("W1"))
        try await store.deleteCollection(id: gone)

        #expect(try await restore(deleted))

        #expect(await value(of: store.observeCollectionIDs(openAlexID: "W1")) == [kept])
        #expect(try count("collection_papers") == 1)
    }

    /// Android's `restoreDropsACiteKeyAnotherPaperTookMeanwhile`.
    @Test func restoreDropsATakenCiteKey() async throws {
        var first = paper(1, savedAt: 100)
        first.citeKey = "k"
        try await save(first, "Ada")
        let deleted = try #require(try await store.deleteByOpenAlexID("W1"))
        var second = paper(2, savedAt: 200)
        second.citeKey = "k"
        try await save(second, "Bo")

        #expect(try await restore(deleted))

        let saved = await library()?.papers
        #expect(saved?.map(\.paper.id) == ["local-2", "local-1"])
        #expect(saved?.last?.paper.citeKey == nil)
        #expect(saved?.first?.paper.citeKey == "k")
    }

    /// Android's `savingAnAlreadySavedPaperWithACiteKeyIsStillANoOp`.
    @Test func savingAnAlreadySavedPaperWithACiteKeyIsStillANoOp() async throws {
        var record = paper(1, savedAt: 100)
        record.citeKey = "ada2020paper"
        #expect(try await save(record, "Ada"))

        var again = paper(1, savedAt: 999)
        again.citeKey = "ada2020paper"
        #expect(try await save(again, "Ada") == false)

        #expect(await library()?.papers.first?.paper.savedAt == 100)
        #expect(await library()?.papers.first?.paper.citeKey == "ada2020paper")
        #expect(try count("paper_authors") == 1)
    }

    @Test(.timeLimit(.minutes(1)))
    func anOpenCollectionsObservationSeesADeletedPaper() async throws {
        try await save(paper(1, savedAt: 100))
        let id = try await collection("A")
        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 1)
        var iterator = store.observeCollections().makeAsyncIterator()
        #expect(await iterator.next() == [CollectionWithCount(id: id, name: "A", paperCount: 1)])

        _ = try await store.deleteByOpenAlexID("W1")

        #expect(await iterator.next() == [CollectionWithCount(id: id, name: "A", paperCount: 0)])
    }
```

Create `ios/HashiyaKit/Tests/HashiyaDatabaseTests/CollectionStoreTests.swift` (Android's `CollectionDaoTest`):
```swift
import GRDB
import HashiyaDatabase
import HashiyaModel
import Testing

/// Mirrors Android's `CollectionDaoTest`.
struct CollectionStoreTests {
    private let queue: DatabaseQueue
    private let store: PaperStore

    init() throws {
        queue = try HashiyaDatabase.openInMemory()
        store = PaperStore(writer: queue)
    }

    private func savePaper(_ id: String, _ openAlexID: String) async throws {
        let paper = PaperRecord(
            id: id, openAlexID: openAlexID, doi: nil, title: "Title \(id)", year: 2020, venue: nil, abstract: nil,
            citationCount: 0, isOpenAccess: false, oaPDFURL: nil, savedAt: 1
        )
        try await store.insert(paper: paper, authors: [], search: PaperWithAuthors(paper: paper, authors: []).searchRow)
    }

    private func insert(_ name: String, createdAt: Int64 = 1) async throws -> Int64 {
        try #require(try await store.insertCollection(name: name, nameKey: collectionNameKey(name), createdAt: createdAt))
    }

    private func value<T: Sendable>(of stream: AsyncStream<T>) async -> T? {
        for await value in stream {
            return value
        }
        return nil
    }

    private func collections() async -> [CollectionWithCount]? {
        await value(of: store.observeCollections())
    }

    private func count(_ table: String) throws -> Int? {
        try queue.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(table)") }
    }

    @Test func createsCollectionsSortedByNameKeyWithCounts() async throws {
        let b = try await insert("beta", createdAt: 1)
        let a = try await insert("Alpha", createdAt: 2)
        try await savePaper("p1", "W1")
        try await store.addToCollection(collectionID: a, openAlexID: "W1", addedAt: 3)

        #expect(await collections() == [
            CollectionWithCount(id: a, name: "Alpha", paperCount: 1),
            CollectionWithCount(id: b, name: "beta", paperCount: 0),
        ])
    }

    @Test func aNameClashIsReportedNotThrown() async throws {
        let id = try await insert("Thesis")
        #expect(try await store.insertCollection(name: " thesis ", nameKey: collectionNameKey(" thesis "), createdAt: 2) == nil)
        let other = try await insert("Other", createdAt: 3)

        #expect(try await store.renameCollection(id: other, name: "THESIS", nameKey: "thesis") == false)
        #expect(await collections()?.map(\.name) == ["Other", "Thesis"])
        #expect(try await store.renameCollection(id: id, name: "thesis", nameKey: "thesis"))
        #expect(try await store.renameCollection(id: other, name: "Chapter 2", nameKey: "chapter 2"))
        #expect(await collections()?.map(\.name) == ["Chapter 2", "thesis"])
        #expect(try count("collections") == 2)
    }

    @Test func renamingAMissingCollectionReportsFalse() async throws {
        let id = try await insert("Thesis")
        try await store.deleteCollection(id: id)

        #expect(try await store.renameCollection(id: id, name: "Chapter 2", nameKey: "chapter 2") == false)
        #expect(try await store.renameCollection(id: id + 100, name: "Other", nameKey: "other") == false)
        #expect(try count("collections") == 0)
    }

    @Test func aCollectionExistsUntilDeleted() async throws {
        let id = try await insert("A")
        #expect(try await store.collectionExists(id: id))
        try await store.deleteCollection(id: id)
        #expect(try await store.collectionExists(id: id) == false)
    }

    @Test func aPapersCollectionIDsAreAllItsCollections() async throws {
        let a = try await insert("A")
        let b = try await insert("B")
        let c = try await insert("C")
        try await savePaper("p1", "W1")
        try await store.addToCollection(collectionID: c, openAlexID: "W1", addedAt: 2)
        try await store.addToCollection(collectionID: a, openAlexID: "W1", addedAt: 3)

        #expect(await value(of: store.observeCollectionIDs(openAlexID: "W1")) == [a, c])
        #expect(await value(of: store.observeCollectionIDs(openAlexID: "W404")) == [])
        _ = b
    }

    @Test func membershipIsIdempotentAndFollowsThePaper() async throws {
        let id = try await insert("A")
        try await savePaper("p1", "W1")

        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 2)
        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 3)
        #expect(await value(of: store.observeCollectionIDs(openAlexID: "W1")) == [id])

        try await store.removeFromCollection(collectionID: id, openAlexID: "W1")
        #expect(await value(of: store.observeCollectionIDs(openAlexID: "W1")) == [])
        try await store.addToCollection(collectionID: id, openAlexID: "W-unsaved", addedAt: 4)
        #expect(try count("collection_papers") == 0)
    }

    /// A swipe in a collection that was just deleted, or an Undo into one, must neither throw nor link anything.
    @Test func addingToAMissingCollectionDoesNothing() async throws {
        let id = try await insert("A")
        try await savePaper("p1", "W1")
        try await store.deleteCollection(id: id)

        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 2)
        try await store.removeFromCollection(collectionID: id, openAlexID: "W1")

        #expect(try count("collection_papers") == 0)
    }

    @Test func deletingACollectionKeepsItsPapersAndDeletingAPaperKeepsItsCollections() async throws {
        let a = try await insert("A")
        let b = try await insert("B")
        try await savePaper("p1", "W1")
        try await savePaper("p2", "W2")
        try await store.addToCollection(collectionID: a, openAlexID: "W1", addedAt: 2)
        try await store.addToCollection(collectionID: b, openAlexID: "W2", addedAt: 2)

        try await store.deleteCollection(id: a)
        #expect(try count("papers") == 2)
        #expect(try count("collection_papers") == 1)

        _ = try await store.deleteByOpenAlexID("W2")
        #expect(await collections()?.map(\.name) == ["B"])
        #expect(try count("collection_papers") == 0)
    }

    @Test(.timeLimit(.minutes(1)))
    func anOpenCollectionsObservationSeesADeletedCollection() async throws {
        let a = try await insert("A")
        let b = try await insert("B")
        try await savePaper("p1", "W1")
        try await store.addToCollection(collectionID: a, openAlexID: "W1", addedAt: 2)
        var iterator = store.observeCollections().makeAsyncIterator()
        #expect(await iterator.next()?.map(\.id) == [a, b])

        try await store.deleteCollection(id: a)

        #expect(await iterator.next() == [CollectionWithCount(id: b, name: "B", paperCount: 0)])
    }

    @Test(.timeLimit(.minutes(1)))
    func anOpenCollectionsObservationSeesARename() async throws {
        let a = try await insert("A")
        var iterator = store.observeCollections().makeAsyncIterator()
        #expect(await iterator.next()?.map(\.name) == ["A"])

        #expect(try await store.renameCollection(id: a, name: "Z", nameKey: "z"))

        #expect(await iterator.next()?.map(\.name) == ["Z"])
    }
}
```

`ios/HashiyaKit/Tests/HashiyaDataTests/PaperMappingTests.swift`: add after `recordsRoundTripAPaperWithAuthorsInOrder`:
```swift
    @Test func recordsCarryThePublicationDetailsTheKeyAndTheFlag() {
        var paper = SamplePapers.attention
        paper.publication = PublicationDetails(workType: "preprint", sourceType: "repository", volume: "30")

        let saved = paper.asRecords(localID: "local-1", savedAt: 42)
        #expect(saved.paper.publication == paper.publication)
        #expect(saved.paper.citeKey == nil)
        #expect(saved.paper.detailsFetched)
        #expect(saved.asPaper() == paper)

        let restored = paper.asRecords(localID: "local-1", savedAt: 42, citeKey: "vaswani2017attention", detailsFetched: false)
        #expect(restored.paper.citeKey == "vaswani2017attention")
        #expect(restored.paper.detailsFetched == false)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaDatabaseTests -only-testing:HashiyaDataTests/PaperMappingTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: build errors such as `extra argument 'collectionID' in call`, `cannot find 'CollectionPaperRecord' in scope`, `value of type 'PaperRecord' has no member 'citeKey'`.

- [ ] **Step 3: Add migration `v4`**

`ios/HashiyaKit/Sources/HashiyaDatabase/HashiyaDatabase.swift`:

1. Extend the `migrator` doc comment's last line:
```swift
    /// `v3`: Android's version 3 — the notes table, and the search index rebuilt with a notes column.
    /// `v4`: Android's version 4 — the citation columns on papers, and the collections tables.
```
2. Register after `"v3"`, before `return migrator`:
```swift
        migrator.registerMigration("v4") { db in
            // Android's MIGRATION_3_4, statement for statement. Every existing paper gets details_fetched = 0, so it is
            // refetched once before its first export. Nothing existing is rewritten, and the search index is untouched.
            for column in ["work_type", "source_type", "publisher", "volume", "issue", "first_page", "last_page", "cite_key"] {
                try db.execute(sql: "ALTER TABLE papers ADD COLUMN `\(column)` TEXT")
            }
            try db.execute(sql: """
                ALTER TABLE papers ADD COLUMN `details_fetched` INTEGER NOT NULL DEFAULT 0;
                CREATE UNIQUE INDEX IF NOT EXISTS `index_papers_cite_key` ON `papers` (`cite_key`);
                CREATE TABLE IF NOT EXISTS `collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `name_key` TEXT NOT NULL, `created_at` INTEGER NOT NULL);
                CREATE UNIQUE INDEX IF NOT EXISTS `index_collections_name_key` ON `collections` (`name_key`);
                CREATE TABLE IF NOT EXISTS `collection_papers` (`collection_id` INTEGER NOT NULL, `paper_id` TEXT NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`collection_id`, `paper_id`), FOREIGN KEY(`collection_id`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`paper_id`) REFERENCES `papers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
                CREATE INDEX IF NOT EXISTS `index_collection_papers_paper_id` ON `collection_papers` (`paper_id`);
                """)
        }
```

- [ ] **Step 4: Add the records**

`ios/HashiyaKit/Sources/HashiyaDatabase/Records.swift`:

1. In `PaperRecord`:
   - Add after `readingStatus`:
```swift
    /// OpenAlex's work type, e.g. "article"; this and the six below are nil when unknown.
    public var workType: String?
    /// OpenAlex's source type, e.g. "journal".
    public var sourceType: String?
    public var publisher: String?
    public var volume: String?
    public var issue: String?
    public var firstPage: String?
    public var lastPage: String?
    /// Assigned the first time the paper is exported or copied, then never changed. Unique when set.
    public var citeKey: String?
    /// True once the columns above come from an OpenAlex response that included them; rows from before `v4` start false.
    public var detailsFetched: Bool
```
   - Replace the init's last parameter and body end. After `readingStatus: String = "to_read"`, add:
```swift
        readingStatus: String = "to_read",
        publication: PublicationDetails = PublicationDetails(),
        citeKey: String? = nil,
        detailsFetched: Bool = false
```
     and at the end of the body:
```swift
        self.readingStatus = readingStatus
        workType = publication.workType
        sourceType = publication.sourceType
        publisher = publication.publisher
        volume = publication.volume
        issue = publication.issue
        firstPage = publication.firstPage
        lastPage = publication.lastPage
        self.citeKey = citeKey
        self.detailsFetched = detailsFetched
```
   - Add after the init:
```swift
    public var publication: PublicationDetails {
        PublicationDetails(
            workType: workType,
            sourceType: sourceType,
            publisher: publisher,
            volume: volume,
            issue: issue,
            firstPage: firstPage,
            lastPage: lastPage
        )
    }
```
   - Replace `CodingKeys` with:
```swift
    enum CodingKeys: String, CodingKey {
        case id, doi, title, year, venue, abstract, publisher, volume, issue
        case openAlexID = "open_alex_id"
        case citationCount = "citation_count"
        case isOpenAccess = "is_open_access"
        case oaPDFURL = "oa_pdf_url"
        case savedAt = "saved_at"
        case readingStatus = "reading_status"
        case workType = "work_type"
        case sourceType = "source_type"
        case firstPage = "first_page"
        case lastPage = "last_page"
        case citeKey = "cite_key"
        case detailsFetched = "details_fetched"
    }
```
2. Replace `DeletedPaper` with:
```swift
/// What `PaperStore.deleteByOpenAlexID` deleted: the paper with its authors (its cite key and `detailsFetched` ride in the
/// paper row), its notes if it had any, and its collection links, ordered by collection id.
public struct DeletedPaper: Equatable, Sendable {
    public var saved: PaperWithAuthors
    public var notes: PaperNotesRecord?
    public var collectionLinks: [CollectionPaperRecord]

    public init(saved: PaperWithAuthors, notes: PaperNotesRecord?, collectionLinks: [CollectionPaperRecord] = []) {
        self.saved = saved
        self.notes = notes
        self.collectionLinks = collectionLinks
    }
}
```
3. Replace `LibraryRows` with:
```swift
/// One consistent read of the library for a search, a status and a collection.
public struct LibraryRows: Equatable, Sendable {
    /// Papers matching the search, the status and the collection, newest saved first.
    public var papers: [PaperWithAuthors]
    /// Papers matching the search in the collection, per stored status; statuses with none are absent.
    public var statusCounts: [String: Int]
    /// Every paper in the current view (the collection, or the whole library), ignoring the search and the status.
    public var total: Int
    /// Every saved paper.
    public var allTotal: Int

    /// `allTotal` nil means the same as `total`, i.e. the view is the whole library.
    public init(papers: [PaperWithAuthors], statusCounts: [String: Int], total: Int, allTotal: Int? = nil) {
        self.papers = papers
        self.statusCounts = statusCounts
        self.total = total
        self.allTotal = allTotal ?? total
    }
}
```
4. Add at the end of the file:
```swift
/// A row of `collections`.
public struct CollectionRecord: Codable, Equatable, Sendable, FetchableRecord, MutablePersistableRecord {
    public static let databaseTableName = "collections"

    /// Nil until inserted.
    public var id: Int64?
    public var name: String
    /// The name trimmed and lowercased; unique, so no two collections share a name in any case.
    public var nameKey: String
    /// Epoch milliseconds.
    public var createdAt: Int64

    public init(id: Int64? = nil, name: String, nameKey: String, createdAt: Int64) {
        self.id = id
        self.name = name
        self.nameKey = nameKey
        self.createdAt = createdAt
    }

    public mutating func didInsert(_ inserted: InsertionSuccess) {
        id = inserted.rowID
    }

    enum CodingKeys: String, CodingKey {
        case id, name
        case nameKey = "name_key"
        case createdAt = "created_at"
    }
}

/// A row of `collection_papers`: a saved paper's membership in a collection. Deleting either side deletes the link,
/// never the other side.
public struct CollectionPaperRecord: Codable, Equatable, Sendable, FetchableRecord, PersistableRecord {
    public static let databaseTableName = "collection_papers"

    public var collectionID: Int64
    /// The paper's local id.
    public var paperID: String
    /// Epoch milliseconds.
    public var addedAt: Int64

    public init(collectionID: Int64, paperID: String, addedAt: Int64) {
        self.collectionID = collectionID
        self.paperID = paperID
        self.addedAt = addedAt
    }

    enum CodingKeys: String, CodingKey {
        case collectionID = "collection_id"
        case paperID = "paper_id"
        case addedAt = "added_at"
    }
}

/// A collection and how many saved papers it holds.
public struct CollectionWithCount: Codable, Equatable, Sendable, FetchableRecord {
    public var id: Int64
    public var name: String
    public var paperCount: Int

    public init(id: Int64, name: String, paperCount: Int) {
        self.id = id
        self.name = name
        self.paperCount = paperCount
    }

    enum CodingKeys: String, CodingKey {
        case id, name
        case paperCount = "paper_count"
    }
}
```

- [ ] **Step 5: Change and extend `PaperStore`**

`ios/HashiyaKit/Sources/HashiyaDatabase/PaperStore.swift`:

1. Replace `observeLibrary` and `librarySnapshot` with:
```swift
    /// The library for `match` (an FTS MATCH expression; nil = everything), `status` (a stored status; nil = any) and
    /// `collectionID` (nil = all papers), one consistent read per database change. Each call starts its own observation.
    public func observeLibrary(match: String?, status: String?, collectionID: Int64? = nil) -> AsyncStream<LibraryRows> {
        stream(ValueObservation.tracking { db in
            try Self.librarySnapshot(db, match: match, status: status, collectionID: collectionID)
        })
    }

    /// The papers, the counts per status for the same search and collection, the view's size and the whole library's size,
    /// read together.
    public static func librarySnapshot(_ db: Database, match: String?, status: String?, collectionID: Int64? = nil) throws -> LibraryRows {
        let matching = "(:match IS NULL OR papers.id IN (SELECT paper_id FROM paper_search WHERE paper_search MATCH :match))"
        let inCollection =
            "(:collection IS NULL OR papers.id IN (SELECT paper_id FROM collection_papers WHERE collection_id = :collection))"
        let papers = try PaperRecord.fetchAll(
            db,
            sql: """
                SELECT papers.* FROM papers
                WHERE \(matching)
                  AND \(inCollection)
                  AND (:status IS NULL OR papers.reading_status = :status)
                ORDER BY papers.saved_at DESC
                """,
            arguments: ["match": match, "status": status, "collection": collectionID]
        )
        var statusCounts: [String: Int] = [:]
        let counts = try Row.fetchAll(
            db,
            sql: "SELECT reading_status, COUNT(*) AS count FROM papers WHERE \(matching) AND \(inCollection) GROUP BY reading_status",
            arguments: ["match": match, "collection": collectionID]
        )
        for row in counts {
            statusCounts[row["reading_status"]] = row["count"]
        }
        let allTotal = try PaperRecord.fetchCount(db)
        let total = try collectionID.map { id in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM collection_papers WHERE collection_id = ?", arguments: [id]) ?? 0
        } ?? allTotal
        return LibraryRows(
            papers: try withAuthors(db, papers),
            statusCounts: statusCounts,
            total: total,
            allTotal: allTotal
        )
    }

    /// `papers` with their authors in position order, read in one query.
    static func withAuthors(_ db: Database, _ papers: [PaperRecord]) throws -> [PaperWithAuthors] {
        let authors = try PaperAuthorRecord
            .filter(papers.map(\.id).contains(Column("paper_id")))
            .order(Column("paper_id"), Column("position"))
            .fetchAll(db)
        let authorsByPaper = Dictionary(grouping: authors, by: \.paperID)
        return papers.map { PaperWithAuthors(paper: $0, authors: authorsByPaper[$0.id] ?? []) }
    }
```
2. Replace `insert` with:
```swift
    /// Inserts the paper unless one with the same `id` or `open_alex_id` exists, then its authors, its search row, its notes
    /// and (on a restore) its collection links. Links to collections deleted meanwhile are skipped, and a cite key another
    /// paper took meanwhile is dropped (the next export assigns a new one). Returns false, writing nothing, when the paper
    /// already exists. `search.notes` must already hold `notes`' search text.
    @discardableResult
    public func insert(
        paper: PaperRecord,
        authors: [PaperAuthorRecord],
        search: PaperSearchRow,
        notes: PaperNotesRecord? = nil,
        collectionLinks: [CollectionPaperRecord] = []
    ) async throws -> Bool {
        precondition(search.paperID == paper.id, "The search row must belong to the paper")
        precondition(notes.map { $0.paperID == paper.id } ?? true, "The notes must belong to the paper")
        precondition(collectionLinks.allSatisfy { $0.paperID == paper.id }, "The collection links must belong to the paper")
        return try await writer.write { db in
            var row = paper
            // `OR IGNORE` would also swallow a cite_key clash and silently save nothing. An already saved paper holds its
            // own key, so its copy loses the key here, but that insert is ignored anyway.
            if let key = row.citeKey,
               try Bool.fetchOne(db, sql: "SELECT EXISTS(SELECT 1 FROM papers WHERE cite_key = ?)", arguments: [key]) == true {
                row.citeKey = nil
            }
            try row.insert(db, onConflict: .ignore)
            guard db.changesCount > 0 else { return false }
            for author in authors {
                try author.insert(db)
            }
            try search.insert(db)
            try notes?.insert(db)
            for link in collectionLinks {
                try db.execute(
                    sql: """
                        INSERT OR IGNORE INTO collection_papers (collection_id, paper_id, added_at)
                        SELECT id, ?, ? FROM collections WHERE id = ?
                        """,
                    arguments: [link.paperID, link.addedAt, link.collectionID]
                )
            }
            return true
        }
    }
```
3. Replace `deleteByOpenAlexID` with:
```swift
    /// Deletes the paper (its authors, notes and collection links cascade) and its search row, and returns what was deleted,
    /// or nil if it was not saved.
    public func deleteByOpenAlexID(_ openAlexID: String) async throws -> DeletedPaper? {
        try await writer.write { db in
            guard let saved = try Self.paper(db, openAlexID: openAlexID) else { return nil }
            let notes = try PaperNotesRecord.fetchOne(db, key: saved.paper.id)
            let links = try CollectionPaperRecord
                .filter(Column("paper_id") == saved.paper.id)
                .order(Column("collection_id"))
                .fetchAll(db)
            try saved.paper.delete(db)
            // FTS rows don't cascade.
            try db.execute(sql: "DELETE FROM paper_search WHERE paper_id = ?", arguments: [saved.paper.id])
            // Open collection counts refresh without relying on the cascade being reported.
            try db.notifyChanges(in: Table(CollectionPaperRecord.databaseTableName))
            return DeletedPaper(saved: saved, notes: notes, collectionLinks: links)
        }
    }
```
4. In `notifyExternalChanges()`, add after the `PaperNotesRecord` line:
```swift
            try db.notifyChanges(in: Table(CollectionRecord.databaseTableName))
            try db.notifyChanges(in: Table(CollectionPaperRecord.databaseTableName))
```
5. Add a `// MARK: - Collections` section after `setStatus` (Android's `CollectionDao`):
```swift
    // MARK: - Collections

    /// Every collection with its paper count, sorted by the name key, so case never changes the order. Each call starts its
    /// own observation.
    public func observeCollections() -> AsyncStream<[CollectionWithCount]> {
        stream(ValueObservation.tracking { db in
            try CollectionWithCount.fetchAll(
                db,
                sql: """
                    SELECT collections.id, collections.name, COUNT(collection_papers.paper_id) AS paper_count FROM collections
                    LEFT JOIN collection_papers ON collection_papers.collection_id = collections.id
                    GROUP BY collections.id
                    ORDER BY collections.name_key
                    """
            )
        })
    }

    /// The ids of the collections holding the saved paper; empty when it isn't saved. Each call starts its own observation.
    public func observeCollectionIDs(openAlexID: String) -> AsyncStream<Set<Int64>> {
        stream(ValueObservation.tracking { db in
            try Int64.fetchSet(
                db,
                sql: """
                    SELECT collection_papers.collection_id FROM collection_papers
                    JOIN papers ON papers.id = collection_papers.paper_id
                    WHERE papers.open_alex_id = ?
                    """,
                arguments: [openAlexID]
            )
        })
    }

    /// Returns the new collection's id, or nil, writing nothing, when another collection already has `nameKey`.
    public func insertCollection(name: String, nameKey: String, createdAt: Int64) async throws -> Int64? {
        try await writer.write { db in
            guard try Int64.fetchOne(db, sql: "SELECT id FROM collections WHERE name_key = ?", arguments: [nameKey]) == nil else {
                return nil
            }
            var record = CollectionRecord(name: name, nameKey: nameKey, createdAt: createdAt)
            try record.insert(db)
            return record.id
        }
    }

    /// Returns false, changing nothing, when another collection already has `nameKey` or no collection has `id`. Renaming to
    /// another case of the same name is allowed.
    public func renameCollection(id: Int64, name: String, nameKey: String) async throws -> Bool {
        try await writer.write { db in
            if let owner = try Int64.fetchOne(db, sql: "SELECT id FROM collections WHERE name_key = ?", arguments: [nameKey]),
               owner != id {
                return false
            }
            try db.execute(sql: "UPDATE collections SET name = ?, name_key = ? WHERE id = ?", arguments: [name, nameKey, id])
            return db.changesCount > 0
        }
    }

    public func collectionExists(id: Int64) async throws -> Bool {
        try await writer.read { db in
            try Bool.fetchOne(db, sql: "SELECT EXISTS(SELECT 1 FROM collections WHERE id = ?)", arguments: [id]) ?? false
        }
    }

    /// Its links cascade; its papers stay.
    public func deleteCollection(id: Int64) async throws {
        try await writer.write { db in
            try db.execute(sql: "DELETE FROM collections WHERE id = ?", arguments: [id])
            try db.notifyChanges(in: Table(CollectionPaperRecord.databaseTableName))
        }
    }

    /// Does nothing when the paper isn't saved, the collection doesn't exist, or the paper is already in it. (`OR IGNORE`
    /// doesn't cover foreign-key failures, so both sides are selected rather than referenced.)
    public func addToCollection(collectionID: Int64, openAlexID: String, addedAt: Int64) async throws {
        try await writer.write { db in
            try db.execute(
                sql: """
                    INSERT OR IGNORE INTO collection_papers (collection_id, paper_id, added_at)
                    SELECT collections.id, papers.id, ? FROM collections, papers
                    WHERE collections.id = ? AND papers.open_alex_id = ?
                    """,
                arguments: [addedAt, collectionID, openAlexID]
            )
        }
    }

    public func removeFromCollection(collectionID: Int64, openAlexID: String) async throws {
        try await writer.write { db in
            try db.execute(
                sql: """
                    DELETE FROM collection_papers
                    WHERE collection_id = ? AND paper_id IN (SELECT id FROM papers WHERE open_alex_id = ?)
                    """,
                arguments: [collectionID, openAlexID]
            )
        }
    }
```

`ios/HashiyaKit/Sources/HashiyaData/PaperMapping.swift`:

1. In `PaperWithAuthors.asPaper()`, after `openAccessPDFURL: paper.oaPDFURL`, add:
```swift
            openAccessPDFURL: paper.oaPDFURL,
            publication: paper.publication
```
2. Replace `asRecords` with:
```swift
    /// A new save has its details from this OpenAlex response (`detailsFetched` true) and no key yet; Undo passes back the
    /// removed paper's key and flag.
    public func asRecords(
        localID: String,
        savedAt: Int64,
        status: ReadingStatus = .toRead,
        citeKey: String? = nil,
        detailsFetched: Bool = true
    ) -> PaperWithAuthors {
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
                readingStatus: status.storedValue,
                publication: publication,
                citeKey: citeKey,
                detailsFetched: detailsFetched
            ),
            authors: authors.enumerated().map { index, author in
                PaperAuthorRecord(paperID: localID, position: index, name: author.name, openAlexAuthorID: author.openAlexID)
            }
        )
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: the Step 2 command, then `-only-testing:HashiyaDataTests` (the repository and the fake still compile and pass).
Expected: `** TEST SUCCEEDED **`, with no `✘` lines. The existing `migratingFromV2KeepsEveryPaperStatusAndSearch`, `openingAVersion1FileMigratesItInPlace` (`total == 2`, no collection) and `reinsertingADeletedRowKeepsItsIDSavedAtStatusAndSearchRow` must still pass unchanged. In a cloud session, push and read the "iOS" workflow for this commit.

- [ ] **Step 7: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaDatabase/HashiyaDatabase.swift ios/HashiyaKit/Sources/HashiyaDatabase/Records.swift \
  ios/HashiyaKit/Sources/HashiyaDatabase/PaperStore.swift ios/HashiyaKit/Sources/HashiyaData/PaperMapping.swift \
  ios/HashiyaKit/Tests/HashiyaDatabaseTests/MigrationTests.swift ios/HashiyaKit/Tests/HashiyaDatabaseTests/PaperStoreTests.swift \
  ios/HashiyaKit/Tests/HashiyaDatabaseTests/CollectionStoreTests.swift ios/HashiyaKit/Tests/HashiyaDataTests/PaperMappingTests.swift
git commit -m "feat: add schema v4 with citation columns and collections on iOS"
```

---

### Task 6: `PaperStore` citation operations

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaDatabase/PaperStore.swift`
- Test: Create `ios/HashiyaKit/Tests/HashiyaDatabaseTests/CitationStoreTests.swift`

**Interfaces:**
- Consumes: Task 5's `PaperRecord` citation columns, `PaperStore.withAuthors`, `PaperStore.paper(_:openAlexID:)`, `insertCollection`, `addToCollection`.
- Produces (module `HashiyaDatabase`, `public`), as Android's `CitationDao`:
  - `struct CiteKeyTakenError: Error, Equatable`;
  - `PaperStore`:
    - `citablePapers(collectionID: Int64?) async throws -> [PaperWithAuthors]`: every saved paper, or those in the collection, `ORDER BY saved_at, rowid`;
    - `citablePaper(openAlexID:) async throws -> PaperWithAuthors?`;
    - `papersWithoutCiteKeys() async throws -> [PaperWithAuthors]`: the whole library, same order;
    - `updatePublicationDetails(paperID:details:) async throws`, which sets `details_fetched = 1`;
    - `markDetailsFetched(paperID:) async throws`;
    - `assignCiteKeys(_ keys: [String: String]) async throws`: paper id → key, all or none; throws `CiteKeyTakenError` when another paper holds a key; never changes a stored key;
    - `allCiteKeys() async throws -> Set<String>`.

- [ ] **Step 1: Write the failing tests**

Create `ios/HashiyaKit/Tests/HashiyaDatabaseTests/CitationStoreTests.swift`:
```swift
import GRDB
import HashiyaDatabase
import HashiyaModel
import Testing

/// Mirrors Android's `CitationDaoTest`.
struct CitationStoreTests {
    private let queue: DatabaseQueue
    private let store: PaperStore

    init() throws {
        queue = try HashiyaDatabase.openInMemory()
        store = PaperStore(writer: queue)
    }

    private func savePaper(_ id: String, _ openAlexID: String, savedAt: Int64, citeKey: String? = nil, authors: [String] = []) async throws {
        let paper = PaperRecord(
            id: id, openAlexID: openAlexID, doi: nil, title: "Title \(id)", year: 2020, venue: nil, abstract: nil,
            citationCount: 0, isOpenAccess: false, oaPDFURL: nil, savedAt: savedAt, citeKey: citeKey
        )
        let saved = PaperWithAuthors(
            paper: paper,
            authors: authors.enumerated().map { PaperAuthorRecord(paperID: id, position: $0.offset, name: $0.element, openAlexAuthorID: nil) }
        )
        try await store.insert(paper: saved.paper, authors: saved.authors, search: saved.searchRow)
    }

    @Test func papersComeOldestSavedFirstAndCanBeLimitedToACollection() async throws {
        try await savePaper("p1", "W1", savedAt: 20, authors: ["Ada", "Grace"])
        try await savePaper("p2", "W2", savedAt: 10)
        let id = try #require(try await store.insertCollection(name: "A", nameKey: "a", createdAt: 1))
        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 1)

        #expect(try await store.citablePapers(collectionID: nil).map(\.paper.id) == ["p2", "p1"])
        #expect(try await store.citablePapers(collectionID: id).map(\.paper.id) == ["p1"])
        #expect(try await store.citablePapers(collectionID: id).first?.authors.map(\.name) == ["Ada", "Grace"])
        #expect(try await store.citablePaper(openAlexID: "W1")?.paper.id == "p1")
        #expect(try await store.citablePaper(openAlexID: "W1")?.authors.map(\.name) == ["Ada", "Grace"])
        #expect(try await store.citablePaper(openAlexID: "W404") == nil)
    }

    /// Two papers saved in the same millisecond keep their save order, so keys never depend on local ids.
    @Test func papersSavedAtTheSameTimeKeepTheirSaveOrder() async throws {
        try await savePaper("p-b", "W1", savedAt: 10)
        try await savePaper("p-a", "W2", savedAt: 10)

        #expect(try await store.citablePapers(collectionID: nil).map(\.paper.id) == ["p-b", "p-a"])
        #expect(try await store.papersWithoutCiteKeys().map(\.paper.id) == ["p-b", "p-a"])
    }

    @Test func papersWithoutCiteKeysSkipKeyedPapersAcrossTheLibrary() async throws {
        try await savePaper("p1", "W1", savedAt: 30)
        try await savePaper("p2", "W2", savedAt: 10, citeKey: "smith2020deep")
        try await savePaper("p3", "W3", savedAt: 20)

        #expect(try await store.papersWithoutCiteKeys().map(\.paper.id) == ["p3", "p1"])
    }

    @Test func updatesDetailsAndMarksThemFetched() async throws {
        try await savePaper("p1", "W1", savedAt: 1)
        let details = PublicationDetails(
            workType: "article", sourceType: "journal", publisher: "Springer",
            volume: "521", issue: "7553", firstPage: "436", lastPage: "444"
        )

        try await store.updatePublicationDetails(paperID: "p1", details: details)

        let paper = try #require(try await store.citablePaper(openAlexID: "W1")).paper
        #expect(paper.publication == details)
        #expect(paper.detailsFetched)
    }

    @Test func updatingWithEmptyDetailsClearsThemAndStillMarksFetched() async throws {
        try await savePaper("p1", "W1", savedAt: 1)
        try await store.updatePublicationDetails(paperID: "p1", details: PublicationDetails(workType: "article"))

        try await store.updatePublicationDetails(paperID: "p1", details: PublicationDetails())

        let paper = try #require(try await store.citablePaper(openAlexID: "W1")).paper
        #expect(paper.publication == PublicationDetails())
        #expect(paper.detailsFetched)
    }

    @Test func markDetailsFetchedOnlySetsTheFlag() async throws {
        try await savePaper("p1", "W1", savedAt: 1)

        try await store.markDetailsFetched(paperID: "p1")

        let paper = try #require(try await store.citablePaper(openAlexID: "W1")).paper
        #expect(paper.detailsFetched)
        #expect(paper.workType == nil)
    }

    @Test func assignsKeysAndRejectsATakenOne() async throws {
        try await savePaper("p1", "W1", savedAt: 1, citeKey: "smith2020deep")
        try await savePaper("p2", "W2", savedAt: 2)

        try await store.assignCiteKeys(["p2": "smith2020deepa"])
        #expect(try await store.allCiteKeys() == ["smith2020deep", "smith2020deepa"])

        try await savePaper("p3", "W3", savedAt: 3)
        await #expect(throws: CiteKeyTakenError.self) {
            try await store.assignCiteKeys(["p3": "smith2020deep"])
        }
        #expect(try await store.citablePaper(openAlexID: "W3")?.paper.citeKey == nil)
    }

    @Test func aStoredKeyIsNeverChanged() async throws {
        try await savePaper("p1", "W1", savedAt: 1, citeKey: "smith2020deep")

        try await store.assignCiteKeys(["p1": "other2020key"])

        #expect(try await store.citablePaper(openAlexID: "W1")?.paper.citeKey == "smith2020deep")
    }

    @Test func aBatchWithATakenKeyStoresNone() async throws {
        try await savePaper("p1", "W1", savedAt: 1, citeKey: "smith2020deep")
        try await savePaper("p2", "W2", savedAt: 2)
        try await savePaper("p3", "W3", savedAt: 3)

        await #expect(throws: CiteKeyTakenError.self) {
            try await store.assignCiteKeys(["p2": "jones2021graph", "p3": "smith2020deep"])
        }

        #expect(try await store.allCiteKeys() == ["smith2020deep"])
    }

    @Test func noKeysAtFirst() async throws {
        try await savePaper("p1", "W1", savedAt: 1)
        #expect(try await store.allCiteKeys().isEmpty)
        try await store.assignCiteKeys([:])
        #expect(try await store.allCiteKeys().isEmpty)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaDatabaseTests/CitationStoreTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: build errors: `value of type 'PaperStore' has no member 'citablePapers'`, `cannot find 'CiteKeyTakenError' in scope`.

- [ ] **Step 3: Implement**

`ios/HashiyaKit/Sources/HashiyaDatabase/PaperStore.swift`:

1. Add at the top level, after the `PaperStore` struct:
```swift
/// `PaperStore.assignCiteKeys` was given a key another paper already holds. Nothing was stored.
public struct CiteKeyTakenError: Error, Equatable {
    public init() {}
}
```
2. Add a `// MARK: - Citations` section after the collections section (Android's `CitationDao`):
```swift
    // MARK: - Citations

    /// Every saved paper, or those in `collectionID`, oldest saved first: the order cite keys are assigned in. Papers saved in
    /// the same millisecond keep their save order (`rowid`).
    public func citablePapers(collectionID: Int64?) async throws -> [PaperWithAuthors] {
        try await writer.read { db in
            let papers = try PaperRecord.fetchAll(
                db,
                sql: """
                    SELECT * FROM papers
                    WHERE (:collection IS NULL OR id IN (SELECT paper_id FROM collection_papers WHERE collection_id = :collection))
                    ORDER BY saved_at ASC, rowid ASC
                    """,
                arguments: ["collection": collectionID]
            )
            return try Self.withAuthors(db, papers)
        }
    }

    /// The saved paper with its authors, read once; nil when it isn't saved.
    public func citablePaper(openAlexID: String) async throws -> PaperWithAuthors? {
        try await writer.read { db in try Self.paper(db, openAlexID: openAlexID) }
    }

    /// Every saved paper without a cite key, across the whole library, in the same order as `citablePapers`.
    public func papersWithoutCiteKeys() async throws -> [PaperWithAuthors] {
        try await writer.read { db in
            let papers = try PaperRecord.fetchAll(
                db,
                sql: "SELECT * FROM papers WHERE cite_key IS NULL ORDER BY saved_at ASC, rowid ASC"
            )
            return try Self.withAuthors(db, papers)
        }
    }

    /// Stores the paper's publication details, nil fields included, and marks them fetched.
    public func updatePublicationDetails(paperID: String, details: PublicationDetails) async throws {
        try await writer.write { db in
            try db.execute(
                sql: """
                    UPDATE papers SET work_type = ?, source_type = ?, publisher = ?, volume = ?, issue = ?, first_page = ?,
                        last_page = ?, details_fetched = 1
                    WHERE id = ?
                    """,
                arguments: [
                    details.workType, details.sourceType, details.publisher, details.volume, details.issue,
                    details.firstPage, details.lastPage, paperID,
                ]
            )
        }
    }

    /// For a paper OpenAlex no longer has: asking again would never help.
    public func markDetailsFetched(paperID: String) async throws {
        try await writer.write { db in
            try db.execute(sql: "UPDATE papers SET details_fetched = 1 WHERE id = ?", arguments: [paperID])
        }
    }

    /// Stores every key (paper id → key) or none. Only a paper without a key gets one: a stored key is never changed.
    /// Throws `CiteKeyTakenError` when another paper already holds one of the keys.
    public func assignCiteKeys(_ keys: [String: String]) async throws {
        do {
            try await writer.write { db in
                for (paperID, key) in keys {
                    try db.execute(
                        sql: "UPDATE papers SET cite_key = ? WHERE id = ? AND cite_key IS NULL",
                        arguments: [key, paperID]
                    )
                }
            }
        } catch let error as DatabaseError where error.extendedResultCode == .SQLITE_CONSTRAINT_UNIQUE {
            throw CiteKeyTakenError()
        }
    }

    public func allCiteKeys() async throws -> Set<String> {
        try await writer.read { db in
            try String.fetchSet(db, sql: "SELECT cite_key FROM papers WHERE cite_key IS NOT NULL")
        }
    }
```
The throw inside `writer.write` rolls the whole transaction back, so a batch with one taken key stores none (`aBatchWithATakenKeyStoresNone`).

- [ ] **Step 4: Run the tests to verify they pass**

Run: the Step 2 command, then `-only-testing:HashiyaDatabaseTests`.
Expected: `** TEST SUCCEEDED **`, with no `✘` lines. In a cloud session, push and read the "iOS" workflow for this commit.

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaDatabase/PaperStore.swift ios/HashiyaKit/Tests/HashiyaDatabaseTests/CitationStoreTests.swift
git commit -m "feat: read citable papers and store cite keys and publication details on iOS"
```

---

### Task 7: `HashiyaData` — the library by collection, Undo with collections and cite keys, `CollectionsRepository`, and the fakes

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift` (full replacement below)
- Create: `ios/HashiyaKit/Sources/HashiyaData/CollectionsRepository.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaTesting/FakeLibraryRepository.swift` (full replacement below; it keeps every existing member)
- Create: `ios/HashiyaKit/Sources/HashiyaTesting/FakeCollectionsRepository.swift`
- Test: Modify `ios/HashiyaKit/Tests/HashiyaDataTests/GRDBLibraryRepositoryTests.swift` and `FakeLibraryRepositoryTests.swift`; create `ios/HashiyaKit/Tests/HashiyaDataTests/GRDBCollectionsRepositoryTests.swift` and `FakeCollectionsRepositoryTests.swift`

**Interfaces:**
- Consumes:
  - Task 1: `PublicationDetails`, `Paper.publication`, `PaperCollection`, `trimmedCollectionName(_:)`, `isValidCollectionName(_:)`, `collectionNameKey(_:)`.
  - Task 5: `Paper.asRecords(localID:savedAt:status:citeKey:detailsFetched:)`; `PaperRecord`'s `workType`, `sourceType`, `publisher`, `volume`, `issue`, `firstPage`, `lastPage`, `citeKey`, `detailsFetched`; `CollectionPaperRecord(collectionID:paperID:addedAt:)`; `CollectionWithCount { id, name, paperCount }`; `DeletedPaper.collectionLinks`; `LibraryRows.allTotal`.
  - Task 5 `PaperStore`: `observeLibrary(match:status:collectionID:)`, `insert(paper:authors:search:notes:collectionLinks:)`, `observeCollections()`, `observeCollectionIDs(openAlexID:)`, `insertCollection(name:nameKey:createdAt:) -> Int64?`, `renameCollection(id:name:nameKey:) -> Bool`, `collectionExists(id:) -> Bool`, `deleteCollection(id:)`, `addToCollection(collectionID:openAlexID:addedAt:)`, `removeFromCollection(collectionID:openAlexID:)`.
  - Task 6 (tests only): `PaperStore.citablePaper(openAlexID:)`, `assignCiteKeys(_:)`.
- Produces (module `HashiyaData`, `public`):
  - `LibrarySnapshot.allPapersTotal: Int`, and `init(papers:counts:libraryTotal:allPapersTotal: Int? = nil)`, where nil means `libraryTotal`. `libraryTotal` now counts the current view: the collection, or the whole library.
  - `LibraryRepository.observeLibrary(query:status:collectionID:)`. A protocol extension keeps `observeLibrary(query:status:)`, forwarding `collectionID: nil`.
  - `RemovedPaper` gains `collectionIDs: Set<Int64> = []`, `citeKey: String? = nil`, `detailsFetched: Bool = true` and `collectionLinksAddedAt: [Int64: Int64] = [:]`, as init parameters after `notes`.
  - `GRDBLibraryRepository.remove` and `restore` carry the links, the cite key and the flag. `save` already stores `publication` with `details_fetched = 1` through Task 5's `asRecords`.
  - `enum CollectionResult: Equatable, Sendable { case done(id: Int64), nameTaken, invalidName, notFound }`.
  - `protocol CollectionsRepository: Sendable` (spec §7.2).
  - `struct GRDBCollectionsRepository: CollectionsRepository`, `init(store:now:)`.
- Produces (module `HashiyaTesting`):
  - `FakeLibraryRepository.init(saved:statuses:notes:collectionMembers:)`;
  - `collectionMembers`;
  - `setCollectionMembership(collectionID:openAlexID:member:)`;
  - `removeCollection(_:)`;
  - `FakeCollectionsRepository` (contract names, plus note 9);
  - `struct MembershipCall`.

- [ ] **Step 1: Write the failing tests**

In `ios/HashiyaKit/Tests/HashiyaDataTests/GRDBLibraryRepositoryTests.swift`:
- add a `store` property next to `queue`;
- replace the `library` helper;
- add the tests below at the end of the struct.

```swift
    // Properties, replacing `private let queue: DatabaseQueue`:
    private let queue: DatabaseQueue
    private let store: PaperStore

    // In init(), replacing `queue = try HashiyaDatabase.openInMemory()` and the repository line's `store:` argument:
    //     queue = try HashiyaDatabase.openInMemory()
    //     store = PaperStore(writer: queue)
    //     repository = GRDBLibraryRepository(store: store, now: …, newID: …)   // same closures as before

    // Replaces the existing `library(_:status:)` helper; existing calls keep compiling.
    private func library(_ query: String = "", status: ReadingStatus? = nil, collectionID: Int64? = nil) async -> LibrarySnapshot? {
        await value(of: repository.observeLibrary(query: query, status: status, collectionID: collectionID))
    }

    private static let details = PublicationDetails(
        workType: "article", sourceType: "journal", publisher: "Springer",
        volume: "521", issue: "7553", firstPage: "436", lastPage: "444"
    )

    private func withDetails(_ paper: Paper) -> Paper {
        var paper = paper
        paper.publication = Self.details
        return paper
    }

    /// Android's `savesPublicationDetailsAsFetched`.
    @Test func savesPublicationDetailsAsFetched() async throws {
        try await repository.save(withDetails(paper("W1")))

        let row = try #require(try await store.citablePaper(openAlexID: "W1")).paper
        #expect(row.publisher == "Springer")
        #expect(row.detailsFetched)
        #expect(await value(of: repository.observePaper(openAlexID: "W1"))??.paper.publication == Self.details)
    }

    /// Android's `libraryAndCountsFollowTheSelectedCollection`.
    @Test func libraryAndCountsFollowTheSelectedCollection() async throws {
        try await repository.save(paper("W1", title: "Graph networks"))
        try await repository.save(paper("W2", title: "Graph kernels"))
        let id = try #require(try await store.insertCollection(name: "A", nameKey: "a", createdAt: 1))
        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 1)

        #expect(await library("graph", collectionID: id)?.papers.map(\.paper.openAlexID) == ["W1"])
        let inCollection = try #require(await library(collectionID: id))
        #expect(inCollection.matchingTotal == 1)
        #expect(inCollection.libraryTotal == 1)
        #expect(inCollection.allPapersTotal == 2)
        let all = try #require(await library())
        #expect(all.matchingTotal == 2)
        #expect(all.libraryTotal == 2)
        #expect(all.allPapersTotal == 2)
    }

    @Test func anEmptyCollectionHasNoPapersButTheLibraryTotalStays() async throws {
        try await repository.save(paper("W1"))
        let id = try #require(try await store.insertCollection(name: "A", nameKey: "a", createdAt: 1))

        let snapshot = try #require(await library(collectionID: id))
        #expect(snapshot.papers.isEmpty)
        #expect(snapshot.libraryTotal == 0)
        #expect(snapshot.allPapersTotal == 1)
    }

    /// Android's `removeThenRestoreKeepsCollectionsCiteKeyAndFetchedFlag`.
    @Test func removeThenRestoreKeepsCollectionsCiteKeyAndFetchedFlag() async throws {
        try await repository.save(withDetails(paper("W1")))
        let kept = try #require(try await store.insertCollection(name: "Kept", nameKey: "kept", createdAt: 1))
        let gone = try #require(try await store.insertCollection(name: "Gone", nameKey: "gone", createdAt: 1))
        try await store.addToCollection(collectionID: kept, openAlexID: "W1", addedAt: 7)
        try await store.addToCollection(collectionID: gone, openAlexID: "W1", addedAt: 8)
        try await store.assignCiteKeys(["local-1": "first2020paper"])

        let removed = try #require(try await repository.remove(openAlexID: "W1"))
        #expect(removed.collectionIDs == [kept, gone])
        #expect(removed.collectionLinksAddedAt == [kept: 7, gone: 8])
        #expect(removed.citeKey == "first2020paper")
        #expect(removed.detailsFetched)
        try await store.deleteCollection(id: gone)
        try await repository.restore(removed)

        #expect(await value(of: store.observeCollectionIDs(openAlexID: "W1")) == [kept])
        let row = try #require(try await store.citablePaper(openAlexID: "W1")).paper
        #expect(row.citeKey == "first2020paper")
        #expect(row.detailsFetched)
        #expect(await value(of: repository.observePaper(openAlexID: "W1"))??.paper.publication == Self.details)
    }

    /// Android's `restoreKeepsAPreV4PaperUnfetched`.
    @Test func restoreKeepsAPreV4PaperUnfetched() async throws {
        try await repository.save(paper("W1"))
        try await queue.write { try $0.execute(sql: "UPDATE papers SET details_fetched = 0 WHERE open_alex_id = 'W1'") }

        let removed = try #require(try await repository.remove(openAlexID: "W1"))
        #expect(!removed.detailsFetched)
        try await repository.restore(removed)

        #expect(try await store.citablePaper(openAlexID: "W1")?.paper.detailsFetched == false)
    }

    /// Android's `restoreDropsACiteKeyAnotherPaperTookMeanwhile`.
    @Test func restoreDropsACiteKeyAnotherPaperTookMeanwhile() async throws {
        try await repository.save(paper("W1"))
        try await store.assignCiteKeys(["local-1": "first2020paper"])
        let removed = try #require(try await repository.remove(openAlexID: "W1"))
        try await repository.save(paper("W2"))
        try await store.assignCiteKeys(["local-2": "first2020paper"])

        try await repository.restore(removed)

        #expect(await value(of: repository.observeSavedIDs()) == ["W1", "W2"])
        #expect(try await store.citablePaper(openAlexID: "W1")?.paper.citeKey == nil)
    }

    @Test func aRemovedPaperBuiltWithoutTheNewFieldsRestoresAsFetchedWithNoCollections() async throws {
        try await repository.restore(RemovedPaper(paper: paper("W1"), localID: "local-9", savedAt: 5, status: .read))

        let row = try #require(try await store.citablePaper(openAlexID: "W1")).paper
        #expect(row.detailsFetched)
        #expect(row.citeKey == nil)
        #expect(await value(of: store.observeCollectionIDs(openAlexID: "W1")) == [])
    }
```

Create `ios/HashiyaKit/Tests/HashiyaDataTests/GRDBCollectionsRepositoryTests.swift`. It mirrors Android's `RoomCollectionsRepositoryTest` case by case:

```swift
import Foundation
import GRDB
import HashiyaData
import HashiyaDatabase
import HashiyaModel
import os
import Testing

/// Mirrors Android's `RoomCollectionsRepositoryTest`.
struct GRDBCollectionsRepositoryTests {
    private let repository: GRDBCollectionsRepository
    private let library: GRDBLibraryRepository

    init() throws {
        let clock = OSAllocatedUnfairLock(initialState: Int64(0))
        let ids = OSAllocatedUnfairLock(initialState: 0)
        let store = PaperStore(writer: try HashiyaDatabase.openInMemory())
        repository = GRDBCollectionsRepository(store: store, now: { clock.withLock { $0 += 1; return $0 } })
        library = GRDBLibraryRepository(
            store: store,
            now: { clock.withLock { $0 += 1; return $0 } },
            newID: { ids.withLock { $0 += 1; return "local-\($0)" } }
        )
    }

    private func paper(_ id: String) -> Paper {
        Paper(openAlexID: id, title: "Paper \(id)", authors: [Author(name: "A")], year: 2020)
    }

    private func first<T: Sendable>(_ stream: AsyncStream<T>) async -> T? {
        for await value in stream { return value }
        return nil
    }

    private func collections() async -> [PaperCollection]? { await first(repository.observeCollections()) }
    private func names() async -> [String]? { await collections()?.map(\.name) }

    private func created(_ name: String) async throws -> Int64 {
        guard case .done(let id) = try await repository.create(name: name) else {
            Issue.record("Creating \(name) failed")
            return -1
        }
        return id
    }

    @Test func createTrimsTheNameAndRejectsDuplicatesByCaseAndSpaces() async throws {
        #expect(try await repository.create(name: "  Thesis  ") == .done(id: 1))
        #expect(try await repository.create(name: "Thesis") == .nameTaken)
        #expect(try await repository.create(name: " thesis ") == .nameTaken)
        #expect(try await repository.create(name: "thesis") == .nameTaken)
        #expect(try await repository.create(name: " THESIS ") == .nameTaken)
        #expect(try await repository.create(name: "tHeSiS\t") == .nameTaken)
        #expect(await collections() == [PaperCollection(id: 1, name: "Thesis", paperCount: 0)])
    }

    @Test func invalidNamesAreRejected() async throws {
        #expect(try await repository.create(name: "   ") == .invalidName)
        #expect(try await repository.create(name: String(repeating: "x", count: 61)) == .invalidName)
        #expect(try await repository.create(name: "  " + String(repeating: "x", count: 60) + "  ") == .done(id: 1))
        let id = try await created("A")
        #expect(try await repository.rename(id: id, name: "") == .invalidName)
        #expect(try await repository.rename(id: id, name: "   ") == .invalidName)
        #expect(try await repository.rename(id: id, name: String(repeating: "x", count: 61)) == .invalidName)
        #expect(await names() == ["A", String(repeating: "x", count: 60)])
    }

    @Test func renameRejectsAnotherCollectionsNameByCaseAndSpaces() async throws {
        let thesis = try await created("Thesis")
        let other = try await created("Other")

        #expect(try await repository.rename(id: other, name: "Thesis") == .nameTaken)
        #expect(try await repository.rename(id: other, name: " thesis ") == .nameTaken)
        #expect(try await repository.rename(id: other, name: "THESIS") == .nameTaken)
        #expect(try await repository.rename(id: other, name: "  tHeSiS") == .nameTaken)
        #expect(await names() == ["Other", "Thesis"])

        #expect(try await repository.rename(id: thesis, name: " thesis ") == .done(id: thesis))
        #expect(await names() == ["Other", "thesis"])
        #expect(try await repository.rename(id: thesis, name: "THESIS") == .done(id: thesis))
        // The same name again: the UPDATE still matches the row, so it is done, not notFound.
        #expect(try await repository.rename(id: thesis, name: "THESIS") == .done(id: thesis))
        #expect(await names() == ["Other", "THESIS"])
    }

    @Test func renameAllowsANewCaseButNotAnotherCollectionsName() async throws {
        let a = try await created("Alpha")
        let b = try await created("Beta")

        #expect(try await repository.rename(id: b, name: " alpha") == .nameTaken)
        #expect(try await repository.rename(id: a, name: "ALPHA") == .done(id: a))
        #expect(try await repository.rename(id: b, name: " Gamma ") == .done(id: b))
        #expect(await names() == ["ALPHA", "Gamma"])
        #expect(try await repository.create(name: "beta") == .done(id: 3))
    }

    @Test func renamingAMissingCollectionIsNotFound() async throws {
        let id = try await created("Thesis")
        try await repository.delete(id: id)

        #expect(try await repository.rename(id: id, name: "Chapter 2") == .notFound)
        #expect(try await repository.rename(id: id + 100, name: "Chapter 2") == .notFound)
        #expect(await collections() == [])
    }

    @Test func membershipAndDelete() async throws {
        try await library.save(paper("W1"))
        let id = try await created("A")

        try await repository.setMembership(collectionID: id, openAlexID: "W1", member: true)
        #expect(await first(repository.observeCollectionIDs(openAlexID: "W1")) == [id])
        #expect(await collections()?.first?.paperCount == 1)

        try await repository.setMembership(collectionID: id, openAlexID: "W1", member: false)
        #expect(await first(repository.observeCollectionIDs(openAlexID: "W1")) == [])

        try await repository.setMembership(collectionID: id, openAlexID: "W1", member: true)
        try await repository.delete(id: id)
        #expect(await collections() == [])
        #expect(await first(library.observeLibrary(query: "", status: nil))?.papers.map(\.paper.openAlexID) == ["W1"])
    }

    @Test func addingAnUnsavedPaperDoesNothing() async throws {
        let id = try await created("A")

        try await repository.setMembership(collectionID: id, openAlexID: "W-unsaved", member: true)
        #expect(await first(repository.observeCollectionIDs(openAlexID: "W-unsaved")) == [])
        #expect(await collections()?.first?.paperCount == 0)
    }

    @Test func anOpenObservationSeesCreateRenameAndDelete() async throws {
        var updates = repository.observeCollections().makeAsyncIterator()
        #expect(await updates.next() == [])

        let id = try await created("Thesis")
        #expect(await updates.next()?.map(\.name) == ["Thesis"])
        _ = try await repository.rename(id: id, name: "Chapter 2")
        #expect(await updates.next()?.map(\.name) == ["Chapter 2"])
        try await repository.delete(id: id)
        #expect(await updates.next() == [])
    }

    @Test func anEmojiCountsAsOneCharacter() async throws {
        let name = String(repeating: "📚", count: 60)
        #expect(try await repository.create(name: name) == .done(id: 1))
    }
}
```

Append to `ios/HashiyaKit/Tests/HashiyaDataTests/FakeLibraryRepositoryTests.swift`, inside the struct:

```swift
    @Test func aCollectionFiltersTheLibraryAndUndoBringsTheMembershipBack() async throws {
        let library = FakeLibraryRepository(
            saved: [SamplePapers.attention, SamplePapers.bert],
            collectionMembers: [7: [SamplePapers.bert.openAlexID]]
        )

        var stream = library.observeLibrary(query: "", status: nil, collectionID: 7).makeAsyncIterator()
        let first = try #require(await stream.next())
        #expect(first.papers.map(\.paper.openAlexID) == [SamplePapers.bert.openAlexID])
        #expect(first.libraryTotal == 1)
        #expect(first.allPapersTotal == 2)

        let removed = try #require(try await library.remove(openAlexID: SamplePapers.bert.openAlexID))
        #expect(removed.collectionIDs == [7])
        #expect(await stream.next()?.papers == [])
        try await library.restore(removed)
        #expect(await stream.next()?.papers.map(\.paper.openAlexID) == [SamplePapers.bert.openAlexID])
    }
```

Create `ios/HashiyaKit/Tests/HashiyaDataTests/FakeCollectionsRepositoryTests.swift`:

```swift
import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

struct FakeCollectionsRepositoryTests {
    private func first<T: Sendable>(_ stream: AsyncStream<T>) async -> T? {
        for await value in stream { return value }
        return nil
    }

    @Test func createValidatesAndSortsLikeTheRealOne() async throws {
        let fake = FakeCollectionsRepository()
        #expect(try await fake.create(name: " Thesis ") == .done(id: 1))
        #expect(try await fake.create(name: "thesis") == .nameTaken)
        #expect(try await fake.create(name: "  ") == .invalidName)
        #expect(try await fake.create(name: "Alpha") == .done(id: 2))
        #expect(await first(fake.observeCollections())?.map(\.name) == ["Alpha", "Thesis"])
        #expect(fake.createdNames == [" Thesis ", "Alpha"])
    }

    @Test func membershipIsMirroredIntoTheLinkedLibrary() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let fake = FakeCollectionsRepository(collections: [PaperCollection(id: 3, name: "A", paperCount: 0)], library: library)

        try await fake.setMembership(collectionID: 3, openAlexID: SamplePapers.attention.openAlexID, member: true)

        #expect(library.collectionMembers == [3: [SamplePapers.attention.openAlexID]])
        #expect(await first(fake.observeCollections())?.first?.paperCount == 1)
        #expect(await first(fake.observeCollectionIDs(openAlexID: SamplePapers.attention.openAlexID)) == [3])
        #expect(fake.membershipCalls == [MembershipCall(collectionID: 3, openAlexID: SamplePapers.attention.openAlexID, member: true)])

        try await fake.delete(id: 3)
        #expect(library.collectionMembers == [:])
        #expect(fake.deletedIDs == [3])
    }

    @Test func failingWritesThrowAndChangeNothing() async throws {
        let fake = FakeCollectionsRepository(collections: [PaperCollection(id: 3, name: "A", paperCount: 0)])
        fake.setFailWrites(true)

        await #expect(throws: FakeCollectionsRepository.Failure.self) { try await fake.create(name: "B") }
        await #expect(throws: FakeCollectionsRepository.Failure.self) {
            try await fake.setMembership(collectionID: 3, openAlexID: "W1", member: true)
        }
        #expect(await first(fake.observeCollections())?.map(\.name) == ["A"])
        #expect(fake.createdNames.isEmpty)
        #expect(fake.membershipCalls.isEmpty)
    }

    @Test func aScriptedResultIsReturnedOnce() async throws {
        let fake = FakeCollectionsRepository()
        fake.setNextResult(.nameTaken)
        #expect(try await fake.create(name: "A") == .nameTaken)
        #expect(fake.createdNames.isEmpty)
        #expect(try await fake.create(name: "A") == .done(id: 1))
        #expect(fake.createdNames == ["A"])
    }
}
```

- [ ] **Step 2: Run them and see them fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaDataTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL: `cannot find 'GRDBCollectionsRepository' in scope`, `extra argument 'collectionID' in call`, `cannot find 'FakeCollectionsRepository' in scope`, then `** TEST FAILED **`.

- [ ] **Step 3: Implement**

`ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift` (full replacement):

```swift
import Foundation
import HashiyaDatabase
import HashiyaModel

/// The Library for one search, status filter and collection, read in one go so its parts always describe the same moment.
public struct LibrarySnapshot: Equatable, Sendable {
    /// Papers matching the search and the status, newest saved first.
    public var papers: [LibraryPaper]
    /// Papers matching the search (whatever their status) per status; all three keys are present.
    public var counts: [ReadingStatus: Int]
    /// Every paper in the current view (the collection, or the whole library), ignoring the search and the status.
    public var libraryTotal: Int
    /// Every saved paper, whatever the collection, search and status.
    public var allPapersTotal: Int

    /// Papers matching the search: the All chip.
    public var matchingTotal: Int { counts.values.reduce(0, +) }

    /// `allPapersTotal` nil means the view is the whole library, so it equals `libraryTotal`.
    public init(papers: [LibraryPaper], counts: [ReadingStatus: Int], libraryTotal: Int, allPapersTotal: Int? = nil) {
        self.papers = papers
        self.counts = counts
        self.libraryTotal = libraryTotal
        self.allPapersTotal = allPapersTotal ?? libraryTotal
    }
}

public protocol LibraryRepository: Sendable {
    /// One consistent snapshot per database change. Blank query = all; nil status = all; nil collection = all papers.
    /// Each call returns a new stream starting with the current value.
    func observeLibrary(query: String, status: ReadingStatus?, collectionID: Int64?) -> AsyncStream<LibrarySnapshot>
    /// The OpenAlex IDs in the library. Each call returns a new stream starting with the current value.
    func observeSavedIDs() -> AsyncStream<Set<String>>
    /// Starts as To read, with its publication details marked fetched. Already saved → no-op.
    func save(_ paper: Paper) async throws
    /// Doesn't reorder. Not saved → no-op.
    func setStatus(openAlexID: String, status: ReadingStatus) async throws
    /// Nil if the paper was not saved. The result carries its collections and cite key for Undo.
    func remove(openAlexID: String) async throws -> RemovedPaper?
    /// Puts a removed paper back with the same local ID, saved time, status, notes, cite key and collections. Collections
    /// deleted meanwhile are skipped, and a cite key another paper took meanwhile is dropped. No-op if it was saved again
    /// meanwhile.
    func restore(_ removed: RemovedPaper) async throws
    /// Makes every observation fetch again, so papers saved by the Share Extension appear. Failures are ignored.
    func refreshAfterExternalChanges() async
    /// The saved paper with its status; nil when it isn't saved or stops being saved. Each call returns a new stream
    /// starting with the current value.
    func observePaper(openAlexID: String) -> AsyncStream<LibraryPaper?>
    /// The paper's notes, read once; empty when it has none or isn't saved.
    func notes(openAlexID: String) async throws -> PaperNotes
    /// Saves the notes (blank notes delete them) and updates the search index. Not saved → no-op.
    func saveNotes(openAlexID: String, notes: PaperNotes) async throws
}

extension LibraryRepository {
    /// The whole library: no collection.
    public func observeLibrary(query: String, status: ReadingStatus?) -> AsyncStream<LibrarySnapshot> {
        observeLibrary(query: query, status: status, collectionID: nil)
    }
}

/// What `remove` deleted, so Undo can put it back in the same place with the same status, notes, cite key and collections.
public struct RemovedPaper: Equatable, Sendable {
    public var paper: Paper
    public var localID: String
    public var savedAt: Int64
    public var status: ReadingStatus
    public var notes: PaperNotes
    public var collectionIDs: Set<Int64>
    public var citeKey: String?
    /// False for a paper saved before v4 whose details were never refetched.
    public var detailsFetched: Bool
    /// When the paper was added to each of `collectionIDs`; restored as-is.
    public var collectionLinksAddedAt: [Int64: Int64]

    public init(
        paper: Paper,
        localID: String,
        savedAt: Int64,
        status: ReadingStatus,
        notes: PaperNotes = PaperNotes(),
        collectionIDs: Set<Int64> = [],
        citeKey: String? = nil,
        detailsFetched: Bool = true,
        collectionLinksAddedAt: [Int64: Int64] = [:]
    ) {
        self.paper = paper
        self.localID = localID
        self.savedAt = savedAt
        self.status = status
        self.notes = notes
        self.collectionIDs = collectionIDs
        self.citeKey = citeKey
        self.detailsFetched = detailsFetched
        self.collectionLinksAddedAt = collectionLinksAddedAt
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

    public func observeLibrary(query: String, status: ReadingStatus?, collectionID: Int64?) -> AsyncStream<LibrarySnapshot> {
        store.observeLibrary(match: ftsMatch(query), status: status?.storedValue, collectionID: collectionID).mapped { $0.asSnapshot() }
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
        let saved = deleted.saved.asLibraryPaper()
        return RemovedPaper(
            paper: saved.paper,
            localID: deleted.saved.paper.id,
            savedAt: deleted.saved.paper.savedAt,
            status: saved.status,
            notes: deleted.notes?.notes ?? PaperNotes(),
            collectionIDs: Set(deleted.collectionLinks.map(\.collectionID)),
            citeKey: deleted.saved.paper.citeKey,
            detailsFetched: deleted.saved.paper.detailsFetched,
            collectionLinksAddedAt: Dictionary(
                deleted.collectionLinks.map { ($0.collectionID, $0.addedAt) },
                uniquingKeysWith: { first, _ in first }
            )
        )
    }

    public func restore(_ removed: RemovedPaper) async throws {
        let records = removed.paper.asRecords(
            localID: removed.localID,
            savedAt: removed.savedAt,
            status: removed.status,
            citeKey: removed.citeKey,
            detailsFetched: removed.detailsFetched
        )
        let notes = removed.notes.isEmpty ? nil : removed.notes
        // Nothing reads added_at's exact value for a link without one, so now() stands in.
        let links = removed.collectionIDs.sorted().map { id in
            CollectionPaperRecord(collectionID: id, paperID: removed.localID, addedAt: removed.collectionLinksAddedAt[id] ?? now())
        }
        try await store.insert(
            paper: records.paper,
            authors: records.authors,
            search: records.searchRow(notes: notes),
            notes: notes.map { PaperNotesRecord(paperID: removed.localID, notes: $0, updatedAt: now()) },
            collectionLinks: links
        )
    }

    public func refreshAfterExternalChanges() async {
        try? await store.notifyExternalChanges()
    }

    public func observePaper(openAlexID: String) -> AsyncStream<LibraryPaper?> {
        store.observePaper(openAlexID: openAlexID).mapped { $0?.asLibraryPaper() }
    }

    public func notes(openAlexID: String) async throws -> PaperNotes {
        try await store.notes(openAlexID: openAlexID)?.notes ?? PaperNotes()
    }

    public func saveNotes(openAlexID: String, notes: PaperNotes) async throws {
        try await store.saveNotes(openAlexID: openAlexID, notes: notes, updatedAt: now())
    }
}

extension LibraryRows {
    /// Every stored status mapped to its `ReadingStatus` (unknown values count as To read); missing statuses are 0.
    func asSnapshot() -> LibrarySnapshot {
        var counts = Dictionary(uniqueKeysWithValues: ReadingStatus.allCases.map { ($0, 0) })
        for (stored, count) in statusCounts {
            counts[ReadingStatus(stored: stored), default: 0] += count
        }
        return LibrarySnapshot(
            papers: papers.map { $0.asLibraryPaper() },
            counts: counts,
            libraryTotal: total,
            allPapersTotal: allTotal
        )
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

Create `ios/HashiyaKit/Sources/HashiyaData/CollectionsRepository.swift`:

```swift
import Foundation
import HashiyaDatabase
import HashiyaModel

/// The outcome of creating or renaming a collection.
public enum CollectionResult: Equatable, Sendable {
    case done(id: Int64)
    /// Another collection has the same name, ignoring case and surrounding whitespace.
    case nameTaken
    /// Blank, or longer than `collectionNameMaxLength` characters, after trimming.
    case invalidName
    /// Rename only: the collection was deleted.
    case notFound
}

public protocol CollectionsRepository: Sendable {
    /// Every collection with its paper count, sorted by name ignoring case. Each call returns a new stream starting with the
    /// current value.
    func observeCollections() -> AsyncStream<[PaperCollection]>
    /// The collections a paper is in; empty when it is in none or isn't saved.
    func observeCollectionIDs(openAlexID: String) -> AsyncStream<Set<Int64>>
    /// Trims the name. `.invalidName` unless it is valid; `.nameTaken` if another collection has it.
    func create(name: String) async throws -> CollectionResult
    /// As `create`, and `.notFound` when the collection was deleted.
    func rename(id: Int64, name: String) async throws -> CollectionResult
    /// Deletes the collection and its memberships, never its papers.
    func delete(id: Int64) async throws
    /// Adds or removes the paper. Adding an unsaved paper, or to a deleted collection, does nothing.
    func setMembership(collectionID: Int64, openAlexID: String, member: Bool) async throws
}

public struct GRDBCollectionsRepository: CollectionsRepository {
    private let store: PaperStore
    private let now: @Sendable () -> Int64

    /// - Parameter now: epoch milliseconds.
    public init(
        store: PaperStore,
        now: @escaping @Sendable () -> Int64 = { Int64((Date().timeIntervalSince1970 * 1000).rounded()) }
    ) {
        self.store = store
        self.now = now
    }

    public func observeCollections() -> AsyncStream<[PaperCollection]> {
        store.observeCollections().mapped { rows in
            rows.map { PaperCollection(id: $0.id, name: $0.name, paperCount: $0.paperCount) }
        }
    }

    public func observeCollectionIDs(openAlexID: String) -> AsyncStream<Set<Int64>> {
        store.observeCollectionIDs(openAlexID: openAlexID)
    }

    public func create(name: String) async throws -> CollectionResult {
        guard isValidCollectionName(name) else { return .invalidName }
        guard let id = try await store.insertCollection(
            name: trimmedCollectionName(name), nameKey: collectionNameKey(name), createdAt: now()
        ) else { return .nameTaken }
        return .done(id: id)
    }

    public func rename(id: Int64, name: String) async throws -> CollectionResult {
        guard isValidCollectionName(name) else { return .invalidName }
        if try await store.renameCollection(id: id, name: trimmedCollectionName(name), nameKey: collectionNameKey(name)) {
            return .done(id: id)
        }
        return try await store.collectionExists(id: id) ? .nameTaken : .notFound
    }

    public func delete(id: Int64) async throws {
        try await store.deleteCollection(id: id)
    }

    public func setMembership(collectionID: Int64, openAlexID: String, member: Bool) async throws {
        if member {
            try await store.addToCollection(collectionID: collectionID, openAlexID: openAlexID, addedAt: now())
        } else {
            try await store.removeFromCollection(collectionID: collectionID, openAlexID: openAlexID)
        }
    }
}
```

`ios/HashiyaKit/Sources/HashiyaTesting/FakeLibraryRepository.swift` (full replacement):

```swift
import Foundation
import HashiyaData
import HashiyaModel
import os

/// An in-memory library with live streams. Saves get increasing times, so the newest is first.
/// Its search is a simple stand-in for the real index: every typed word must start a word of the paper's title,
/// authors, abstract, venue or notes, compared through `searchableText`. Collections are only memberships here
/// (`collectionMembers`); `FakeCollectionsRepository` keeps them in step when it is given this library.
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
        let collectionID: Int64?
        let continuation: AsyncStream<LibrarySnapshot>.Continuation
    }

    private struct PaperSubscription {
        let openAlexID: String
        let continuation: AsyncStream<LibraryPaper?>.Continuation
    }

    private struct State {
        var entries: [Entry] = []
        /// OpenAlex IDs per collection; a collection with no members has no key.
        var collectionMembers: [Int64: Set<String>] = [:]
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

        func inView(_ entry: Entry, collectionID: Int64?) -> Bool {
            guard let collectionID else { return true }
            return collectionMembers[collectionID]?.contains(entry.paper.openAlexID) ?? false
        }

        func snapshot(query: String, status: ReadingStatus?, collectionID: Int64?) -> LibrarySnapshot {
            let view = sorted.filter { inView($0, collectionID: collectionID) }
            let matching = view
                .filter { FakeLibraryRepository.matches($0.paper, notes: $0.notes, query: query) }
                .map { LibraryPaper(paper: $0.paper, status: $0.status) }
            var counts = Dictionary(uniqueKeysWithValues: ReadingStatus.allCases.map { ($0, 0) })
            for paper in matching {
                counts[paper.status, default: 0] += 1
            }
            return LibrarySnapshot(
                papers: matching.filter { status == nil || $0.status == status },
                counts: counts,
                libraryTotal: view.count,
                allPapersTotal: entries.count
            )
        }

        func paper(_ openAlexID: String) -> LibraryPaper? {
            entries.first { $0.paper.openAlexID == openAlexID }.map { LibraryPaper(paper: $0.paper, status: $0.status) }
        }

        func publish() {
            for subscription in subscriptions.values {
                subscription.continuation.yield(
                    snapshot(query: subscription.query, status: subscription.status, collectionID: subscription.collectionID)
                )
            }
            for subscription in paperSubscriptions.values {
                subscription.continuation.yield(paper(subscription.openAlexID))
            }
            let ids = ids
            idContinuations.values.forEach { $0.yield(ids) }
        }

        mutating func setMembership(collectionID: Int64, openAlexID: String, member: Bool) {
            var members = collectionMembers[collectionID] ?? []
            if member { members.insert(openAlexID) } else { members.remove(openAlexID) }
            collectionMembers[collectionID] = members.isEmpty ? nil : members
        }
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    /// `saved` is the initial library, newest first; `statuses` gives some of them a status by OpenAlex ID (else To
    /// read), `notes` some notes, and `collectionMembers` the OpenAlex IDs in each collection.
    public init(
        saved: [Paper] = [],
        statuses: [String: ReadingStatus] = [:],
        notes: [String: PaperNotes] = [:],
        collectionMembers: [Int64: Set<String>] = [:]
    ) {
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
            state.collectionMembers = collectionMembers.filter { !$0.value.isEmpty }
        }
    }

    public var savedPapers: [Paper] { state.withLock { $0.library.map(\.paper) } }
    /// The library with statuses, newest first.
    public var library: [LibraryPaper] { state.withLock { $0.library } }
    /// The OpenAlex IDs in each collection that has any.
    public var collectionMembers: [Int64: Set<String>] { state.withLock { $0.collectionMembers } }
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

    /// Adds or removes a saved paper's membership and re-emits. An unsaved paper is never added, as in the real store.
    public func setCollectionMembership(collectionID: Int64, openAlexID: String, member: Bool) {
        state.withLock { state in
            guard !member || state.ids.contains(openAlexID) else { return }
            state.setMembership(collectionID: collectionID, openAlexID: openAlexID, member: member)
            state.publish()
        }
    }

    /// Forgets a deleted collection's memberships and re-emits.
    public func removeCollection(_ collectionID: Int64) {
        state.withLock { state in
            state.collectionMembers[collectionID] = nil
            state.publish()
        }
    }

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

    public func observeLibrary(query: String, status: ReadingStatus?, collectionID: Int64?) -> AsyncStream<LibrarySnapshot> {
        let id = UUID()
        return AsyncStream { continuation in
            state.withLock { state in
                state.subscriptions[id] = Subscription(
                    query: query, status: status, collectionID: collectionID, continuation: continuation
                )
                continuation.yield(state.snapshot(query: query, status: status, collectionID: collectionID))
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
            let collectionIDs = Set(state.collectionMembers.filter { $0.value.contains(openAlexID) }.keys)
            for collectionID in collectionIDs {
                state.setMembership(collectionID: collectionID, openAlexID: openAlexID, member: false)
            }
            state.publish()
            return RemovedPaper(
                paper: entry.paper,
                localID: entry.localID,
                savedAt: entry.savedAt,
                status: entry.status,
                notes: entry.notes,
                collectionIDs: collectionIDs
            )
        }
    }

    /// Emits the current values again.
    public func refreshAfterExternalChanges() async {
        state.withLock { $0.publish() }
    }

    /// Puts the paper back with its memberships. Unlike the real store, the fake doesn't know which collections were
    /// deleted, so tests that delete one also call `removeCollection` after restoring.
    public func restore(_ removed: RemovedPaper) async throws {
        try state.withLock { state in
            if state.failSaves { throw Failure() }
            guard !state.ids.contains(removed.paper.openAlexID) else { return }
            state.entries.append(Entry(
                paper: removed.paper, localID: removed.localID, savedAt: removed.savedAt, status: removed.status, notes: removed.notes
            ))
            for collectionID in removed.collectionIDs {
                state.setMembership(collectionID: collectionID, openAlexID: removed.paper.openAlexID, member: true)
            }
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

Create `ios/HashiyaKit/Sources/HashiyaTesting/FakeCollectionsRepository.swift`:

```swift
import Foundation
import HashiyaData
import HashiyaModel
import os

/// One `setMembership` call that didn't throw (including one that changed nothing).
public struct MembershipCall: Equatable, Sendable {
    public var collectionID: Int64
    public var openAlexID: String
    public var member: Bool

    public init(collectionID: Int64, openAlexID: String, member: Bool) {
        self.collectionID = collectionID
        self.openAlexID = openAlexID
        self.member = member
    }
}

/// In-memory collections with live streams, validating names like the real repository. Given a `FakeLibraryRepository`,
/// it mirrors memberships and deletions into it, so a Library filtered by a collection follows.
public final class FakeCollectionsRepository: CollectionsRepository {
    public struct Failure: Error {}

    private struct State {
        var collections: [PaperCollection]
        /// Collection IDs per OpenAlex ID.
        var memberships: [String: Set<Int64>]
        var nextID: Int64
        var failWrites = false
        var nextResult: CollectionResult?
        /// Non-nil while creates are held: the waiting creates.
        var heldCreates: [CheckedContinuation<Void, Never>]?
        var createdNames: [String] = []
        var renamed: [Int64: String] = [:]
        var membershipCalls: [MembershipCall] = []
        var deletedIDs: [Int64] = []
        var collectionSubscriptions: [UUID: AsyncStream<[PaperCollection]>.Continuation] = [:]
        var idSubscriptions: [UUID: (openAlexID: String, continuation: AsyncStream<Set<Int64>>.Continuation)] = [:]

        var sorted: [PaperCollection] { collections.sorted { $0.name.lowercased() < $1.name.lowercased() } }

        func isTaken(_ name: String, except id: Int64? = nil) -> Bool {
            let key = trimmedCollectionName(name).lowercased()
            return collections.contains { $0.id != id && $0.name.lowercased() == key }
        }

        func publish() {
            let sorted = sorted
            collectionSubscriptions.values.forEach { $0.yield(sorted) }
            for subscription in idSubscriptions.values {
                subscription.continuation.yield(memberships[subscription.openAlexID] ?? [])
            }
        }

        mutating func setCount(_ id: Int64, by delta: Int) {
            guard let index = collections.firstIndex(where: { $0.id == id }) else { return }
            let collection = collections[index]
            collections[index] = PaperCollection(id: id, name: collection.name, paperCount: collection.paperCount + delta)
        }
    }

    private let state: OSAllocatedUnfairLock<State>
    private let library: FakeLibraryRepository?

    /// - Parameters:
    ///   - collections: the initial collections, with the counts given.
    ///   - memberships: collection IDs per OpenAlex ID.
    ///   - library: when given, memberships and deletions are mirrored into it.
    public init(
        collections: [PaperCollection] = [],
        memberships: [String: Set<Int64>] = [:],
        library: FakeLibraryRepository? = nil
    ) {
        self.library = library
        state = OSAllocatedUnfairLock(initialState: State(
            collections: collections,
            memberships: memberships,
            nextID: (collections.map(\.id).max() ?? 0) + 1
        ))
        for (openAlexID, ids) in memberships {
            for id in ids { library?.setCollectionMembership(collectionID: id, openAlexID: openAlexID, member: true) }
        }
    }

    /// The names of the creates that returned `.done`, as passed, in order.
    public var createdNames: [String] { state.withLock { $0.createdNames } }
    /// The last name each collection was renamed to.
    public var renamed: [Int64: String] { state.withLock { $0.renamed } }
    public var membershipCalls: [MembershipCall] { state.withLock { $0.membershipCalls } }
    public var deletedIDs: [Int64] { state.withLock { $0.deletedIDs } }
    /// One paper's collection IDs, read synchronously for assertions.
    public func collectionIDs(of openAlexID: String) -> Set<Int64> { state.withLock { $0.memberships[openAlexID] ?? [] } }
    /// The creates waiting while creates are held.
    public var heldCreates: Int { state.withLock { $0.heldCreates?.count ?? 0 } }

    /// When true, every write (`create`, `rename`, `delete`, `setMembership`) throws and changes nothing.
    public func setFailWrites(_ fail: Bool) { state.withLock { $0.failWrites = fail } }
    /// The next `create` or `rename` returns `result` without changing anything.
    public func setNextResult(_ result: CollectionResult?) { state.withLock { $0.nextResult = result } }

    /// Replaces the collections and re-emits.
    public func setCollections(_ collections: [PaperCollection]) {
        state.withLock { state in
            state.collections = collections
            state.nextID = max(state.nextID, (collections.map(\.id).max() ?? 0) + 1)
            state.publish()
        }
    }

    /// Replaces one paper's collection IDs and re-emits (not mirrored into the library).
    public func setMemberships(openAlexID: String, _ ids: Set<Int64>) {
        state.withLock { state in
            state.memberships[openAlexID] = ids
            state.publish()
        }
    }

    /// From now on `create` waits until `releaseCreates()`, so a test can tap Create twice while one runs.
    public func holdCreates() {
        state.withLock { if $0.heldCreates == nil { $0.heldCreates = [] } }
    }

    /// Lets every held create run, in order, and stops holding.
    public func releaseCreates() {
        let waiting = state.withLock { state -> [CheckedContinuation<Void, Never>] in
            defer { state.heldCreates = nil }
            return state.heldCreates ?? []
        }
        waiting.forEach { $0.resume() }
    }

    public func observeCollections() -> AsyncStream<[PaperCollection]> {
        let id = UUID()
        return AsyncStream { continuation in
            state.withLock { state in
                state.collectionSubscriptions[id] = continuation
                continuation.yield(state.sorted)
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.withLock { $0.collectionSubscriptions.removeValue(forKey: id) }
            }
        }
    }

    public func observeCollectionIDs(openAlexID: String) -> AsyncStream<Set<Int64>> {
        let id = UUID()
        return AsyncStream { continuation in
            state.withLock { state in
                state.idSubscriptions[id] = (openAlexID, continuation)
                continuation.yield(state.memberships[openAlexID] ?? [])
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.withLock { $0.idSubscriptions.removeValue(forKey: id) }
            }
        }
    }

    public func create(name: String) async throws -> CollectionResult {
        await waitWhileHeld()
        return try state.withLock { state in
            if state.failWrites { throw Failure() }
            if let result = state.nextResult {
                state.nextResult = nil
                return result
            }
            guard isValidCollectionName(name) else { return .invalidName }
            guard !state.isTaken(name) else { return .nameTaken }
            let id = state.nextID
            state.nextID += 1
            state.collections.append(PaperCollection(id: id, name: trimmedCollectionName(name), paperCount: 0))
            state.createdNames.append(name)
            state.publish()
            return .done(id: id)
        }
    }

    public func rename(id: Int64, name: String) async throws -> CollectionResult {
        try state.withLock { state in
            if state.failWrites { throw Failure() }
            if let result = state.nextResult {
                state.nextResult = nil
                return result
            }
            guard isValidCollectionName(name) else { return .invalidName }
            guard let index = state.collections.firstIndex(where: { $0.id == id }) else { return .notFound }
            guard !state.isTaken(name, except: id) else { return .nameTaken }
            let collection = state.collections[index]
            state.collections[index] = PaperCollection(id: id, name: trimmedCollectionName(name), paperCount: collection.paperCount)
            state.renamed[id] = trimmedCollectionName(name)
            state.publish()
            return .done(id: id)
        }
    }

    public func delete(id: Int64) async throws {
        try state.withLock { state in
            if state.failWrites { throw Failure() }
            state.deletedIDs.append(id)
            state.collections.removeAll { $0.id == id }
            for key in state.memberships.keys {
                state.memberships[key]?.remove(id)
            }
            state.publish()
        }
        library?.removeCollection(id)
    }

    public func setMembership(collectionID: Int64, openAlexID: String, member: Bool) async throws {
        let changed = try state.withLock { state -> Bool in
            if state.failWrites { throw Failure() }
            state.membershipCalls.append(MembershipCall(collectionID: collectionID, openAlexID: openAlexID, member: member))
            guard state.collections.contains(where: { $0.id == collectionID }) else { return false }
            var ids = state.memberships[openAlexID] ?? []
            let wasMember = ids.contains(collectionID)
            guard wasMember != member else { return false }
            if member { ids.insert(collectionID) } else { ids.remove(collectionID) }
            state.memberships[openAlexID] = ids
            state.setCount(collectionID, by: member ? 1 : -1)
            state.publish()
            return true
        }
        if changed {
            library?.setCollectionMembership(collectionID: collectionID, openAlexID: openAlexID, member: member)
        }
    }

    private func waitWhileHeld() async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            let held = state.withLock { state -> Bool in
                guard state.heldCreates != nil else { return false }
                state.heldCreates?.append(continuation)
                return true
            }
            if !held { continuation.resume() }
        }
    }
}
```

The fake doesn't check that the paper is saved when no library is linked. View-model tests that care link the library.

- [ ] **Step 4: Run them and see them pass**

Run: the Step 2 command, then with `-only-testing:FeatureLibraryTests -only-testing:FeatureSearchTests -only-testing:FeaturePaperDetailsTests`, which use the fake library.
Expected: PASS: `** TEST SUCCEEDED **` for both runs.

- [ ] **Step 5: Commit, push and verify**

```bash
git add ios/HashiyaKit/Sources/HashiyaData/LibraryRepository.swift ios/HashiyaKit/Sources/HashiyaData/CollectionsRepository.swift \
  ios/HashiyaKit/Sources/HashiyaTesting/FakeLibraryRepository.swift ios/HashiyaKit/Sources/HashiyaTesting/FakeCollectionsRepository.swift \
  ios/HashiyaKit/Tests/HashiyaDataTests/GRDBLibraryRepositoryTests.swift ios/HashiyaKit/Tests/HashiyaDataTests/GRDBCollectionsRepositoryTests.swift \
  ios/HashiyaKit/Tests/HashiyaDataTests/FakeLibraryRepositoryTests.swift ios/HashiyaKit/Tests/HashiyaDataTests/FakeCollectionsRepositoryTests.swift
git commit -m "feat: filter the iOS library by collection, keep collections and cite keys through Undo, and add the collections repository"
git log --format='%an <%ae>' origin/main..HEAD
git push
```
On the Mac, run the full scheme as in the verification line (`xcodegen generate`, then `xcodebuild test … -skip-testing:HashiyaUITests`). In a cloud session, read the "iOS" workflow run for this SHA. Expected: `success`.

---

### Task 8: `HashiyaData` — `CitationRepository`, `ExportFiles`, the citation fake, and one shared store

**Files:**
- Modify: `ios/HashiyaKit/Package.swift` (`HashiyaData` depends on `HashiyaBibTeX`)
- Create: `ios/HashiyaKit/Sources/HashiyaData/CitationRepository.swift`, `ios/HashiyaKit/Sources/HashiyaData/ExportFiles.swift`, `ios/HashiyaKit/Sources/HashiyaData/LibraryRepositories.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaData/LiveDependencies.swift`
- Modify: `ios/Hashiya/UITestingStubs.swift` (`dependencies()` only)
- Create: `ios/HashiyaKit/Sources/HashiyaTesting/FakeCitationRepository.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaTesting/FakeLookupServices.swift` (`setWorks(_:)`)
- Test: Create `ios/HashiyaKit/Tests/HashiyaDataTests/GRDBCitationRepositoryTests.swift`, `ExportFilesTests.swift` and `FakeCitationRepositoryTests.swift`

**Interfaces:**
- Consumes:
  - Task 3: `BibTeX.entry(_:)`, `BibTeX.file(_:)`, `CiteKeys.assign(_:taken:)`, `CitablePaper(paper:citeKey:)`.
  - Task 4: `NetworkWork.asPublicationDetails()`, `NetworkWork(…, type:, biblio:)`, `NetworkSource(displayName:type:)`, `NetworkBiblio`.
  - Task 6 `PaperStore`: `CiteKeyTakenError`, `citablePapers(collectionID:)`, `citablePaper(openAlexID:)`, `papersWithoutCiteKeys()`, `updatePublicationDetails(paperID:details:)`, `markDetailsFetched(paperID:)`, `assignCiteKeys(_:)`, `allCiteKeys()`.
  - Task 7: `PaperWithAuthors.asPaper()` with `publication`, `GRDBCollectionsRepository`, `GRDBLibraryRepository`.
- Produces (module `HashiyaData`, `public`):
  - `struct CitationResult: Equatable, Sendable { bibtex: String; complete: Bool }`, `init(bibtex:complete:)`;
  - `protocol CitationRepository: Sendable` (spec §7.3);
  - `struct GRDBCitationRepository`, `init(store:lookup:maxConcurrentRefetches: Int = 4)`;
  - `struct ExportFiles`: `init(directory:)`, `static var live`, `directory`, `write(_:name:) throws -> URL`, `static func fileName(collectionName:) -> String` (no extension: `write` adds `.bib`);
  - `struct LibraryRepositories { library: GRDBLibraryRepository; collections: GRDBCollectionsRepository; citations: GRDBCitationRepository }`, with `static func shared(fileName:fresh:lookup:) throws`;
  - `LiveDependencies` gains `collections`, `citations` and `exportFiles`, with init labels `collections:citations:exportFiles:` after `preferences:`.
- Produces (module `HashiyaTesting`):
  - `FakeCitationRepository`: `init(entry:export:)`, `setFail(_:)`, `setEntry(_:)`, `setExport(_:)`, `holdExports()`, `releaseExports()`, `heldExports`, `exportCalls: [Int64?]`, `entryCalls: [String]`, `Failure`;
  - `FakeOpenAlexLookupService.setWorks(_:)`.

- [ ] **Step 1: Write the failing tests**

In `ios/HashiyaKit/Sources/HashiyaTesting/FakeLookupServices.swift`, add this to `FakeOpenAlexLookupService`, after `setWorksFailure`. It is test support, so it goes in with the tests:

```swift
    /// Replaces what `work(id:)` returns, by ID.
    public func setWorks(_ works: [String: NetworkWork]) { state.withLock { $0.works = works } }
```

Create `ios/HashiyaKit/Tests/HashiyaDataTests/GRDBCitationRepositoryTests.swift`. It mirrors Android's `RoomCitationRepositoryTest` case by case:

```swift
import Foundation
import GRDB
import HashiyaData
import HashiyaDatabase
import HashiyaModel
import HashiyaNetwork
import HashiyaTesting
import os
import Testing

/// A journal article for `id` ("W1"), as OpenAlex returns it once `type` and `biblio` are selected.
private func journalWork(_ id: String) -> NetworkWork {
    NetworkWork(
        id: "https://openalex.org/\(id)",
        primaryLocation: NetworkLocation(source: NetworkSource(displayName: "Nature", type: "journal")),
        type: "article",
        biblio: NetworkBiblio(volume: "521", firstPage: "436", lastPage: "444")
    )
}

/// Runs `onWork` for each request, then answers with a journal article.
private struct ScriptedLookup: OpenAlexLookupService {
    let onWork: @Sendable (String) async throws -> Void

    func work(id: String) async throws -> NetworkWork? {
        try await onWork(id)
        return journalWork(id)
    }

    func works(filter: String, perPage: Int) async throws -> NetworkWorksResponse { throw NetworkFailure.unknown }
}

/// Counts requests running at once; each takes 20 ms.
private final class CountingLookup: OpenAlexLookupService {
    private struct Counts { var running = 0, peak = 0, requests = 0 }
    private let counts = OSAllocatedUnfairLock(initialState: Counts())

    var peak: Int { counts.withLock { $0.peak } }
    var requests: Int { counts.withLock { $0.requests } }

    func work(id: String) async throws -> NetworkWork? {
        counts.withLock { counts in
            counts.requests += 1
            counts.running += 1
            counts.peak = max(counts.peak, counts.running)
        }
        try await Task.sleep(for: .milliseconds(20))
        counts.withLock { $0.running -= 1 }
        return journalWork(id)
    }

    func works(filter: String, perPage: Int) async throws -> NetworkWorksResponse { throw NetworkFailure.unknown }
}

/// Mirrors Android's `RoomCitationRepositoryTest`.
struct GRDBCitationRepositoryTests {
    private let queue: DatabaseQueue
    private let store: PaperStore
    private let library: GRDBLibraryRepository
    private let openAlex = FakeOpenAlexLookupService()

    init() throws {
        let clock = OSAllocatedUnfairLock(initialState: Int64(0))
        let ids = OSAllocatedUnfairLock(initialState: 0)
        queue = try HashiyaDatabase.openInMemory()
        store = PaperStore(writer: queue)
        library = GRDBLibraryRepository(
            store: store,
            now: { clock.withLock { $0 += 1; return $0 } },
            newID: { ids.withLock { $0 += 1; return "local-\($0)" } }
        )
    }

    private func repository(_ lookup: (any OpenAlexLookupService)? = nil) -> GRDBCitationRepository {
        GRDBCitationRepository(store: store, lookup: lookup ?? openAlex)
    }

    private func paper(_ id: String, _ surname: String, title: String = "Deep nets") -> Paper {
        Paper(openAlexID: id, title: title, authors: [Author(name: "Jane \(surname)")], year: 2020, venue: "Nature")
    }

    /// Saves the paper as a v3 library left it: no details, details_fetched = 0.
    private func saveUnfetched(_ paper: Paper) async throws {
        try await library.save(paper)
        try await queue.write { db in
            try db.execute(sql: "UPDATE papers SET details_fetched = 0 WHERE open_alex_id = ?", arguments: [paper.openAlexID])
        }
    }

    private func detailsFetched(_ openAlexID: String) async throws -> Bool {
        try #require(try await store.citablePaper(openAlexID: openAlexID)).paper.detailsFetched
    }

    private func citeKey(_ openAlexID: String) async throws -> String? {
        try await store.citablePaper(openAlexID: openAlexID)?.paper.citeKey
    }

    /// The cite keys of the file's entries, in order.
    private func keys(_ bibtex: String) -> [String] {
        bibtex.split(separator: "\n").filter { $0.hasPrefix("@") }.compactMap { line in
            guard let open = line.firstIndex(of: "{"), let comma = line.firstIndex(of: ",") else { return nil }
            return String(line[line.index(after: open)..<comma])
        }
    }

    @Test func entryRefetchesOnceThenUsesStoredDetailsAndKey() async throws {
        try await saveUnfetched(paper("W1", "Smith"))
        openAlex.setWorks(["W1": journalWork("W1")])

        let first = try #require(try await repository().entry(openAlexID: "W1"))
        let second = try #require(try await repository().entry(openAlexID: "W1"))

        #expect(first.complete)
        #expect(openAlex.workRequests == ["W1"])
        #expect(first.bibtex.hasPrefix("@article{smith2020deep,\n"))
        #expect(first.bibtex.contains("  volume = {521},"))
        #expect(first == second)
    }

    @Test func papersSavedAfterV4AreNotRefetched() async throws {
        try await library.save(paper("W1", "Smith"))
        _ = try await repository().entry(openAlexID: "W1")
        _ = try await repository().export(collectionID: nil)
        #expect(openAlex.workRequests == [])
    }

    @Test func onlyUnfetchedPapersAreRefetched() async throws {
        try await library.save(paper("W1", "Smith"))
        try await saveUnfetched(paper("W2", "Jones"))
        openAlex.setWorks(["W2": journalWork("W2")])

        let result = try await repository().export(collectionID: nil)

        #expect(openAlex.workRequests == ["W2"])
        #expect(result.complete)
    }

    @Test func unsavedPaperHasNoEntry() async throws {
        #expect(try await repository().entry(openAlexID: "W404") == nil)
    }

    /// Android's `failedRefetchIsIncompleteAndRetriedNextTime`.
    @Test func aFailedRefetchIsIncompleteAndRetriedNextTime() async throws {
        try await saveUnfetched(paper("W1", "Smith"))
        openAlex.setWorkFailure(.connectivity)

        let offline = try await repository().export(collectionID: nil)
        #expect(!offline.complete)
        #expect(offline.bibtex.hasPrefix("@misc{smith2020deep,"))
        #expect(try await !detailsFetched("W1"))

        openAlex.setWorkFailure(nil)
        openAlex.setWorks(["W1": journalWork("W1")])
        let online = try await repository().export(collectionID: nil)
        #expect(online.complete)
        #expect(online.bibtex.hasPrefix("@article{smith2020deep,"))
        // pages is the last field here (no DOI or URL), so it has no trailing comma.
        #expect(online.bibtex.contains("  pages = {436--444}\n}"))
        #expect(openAlex.workRequests == ["W1", "W1"])
        #expect(try await detailsFetched("W1"))
    }

    @Test func oneFailedRefetchDoesNotStopTheOthers() async throws {
        try await saveUnfetched(paper("W1", "Adams"))
        try await saveUnfetched(paper("W2", "Brown"))
        let flaky = ScriptedLookup { id in if id == "W1" { throw NetworkFailure.connectivity } }

        let result = try await repository(flaky).export(collectionID: nil)

        #expect(!result.complete)
        #expect(result.bibtex.contains("@misc{adams2020deep,"))
        #expect(result.bibtex.contains("@article{brown2020deep,"))
        #expect(try await !detailsFetched("W1"))
        #expect(try await detailsFetched("W2"))
    }

    @Test func aWorkOpenAlexNoLongerHasIsMarkedFetched() async throws {
        try await saveUnfetched(paper("W1", "Smith"))
        openAlex.setWorks([:])

        #expect(try await repository().export(collectionID: nil).complete)
        _ = try await repository().export(collectionID: nil)
        #expect(openAlex.workRequests == ["W1"])
    }

    @Test func keysAreAssignedInSavedOrderAndNeverChange() async throws {
        try await library.save(paper("W1", "Smith"))
        try await library.save(paper("W2", "Smith"))
        let first = try await repository().export(collectionID: nil)
        #expect(first.bibtex.contains("@misc{smith2020deep,"))
        #expect(first.bibtex.contains("@misc{smith2020deepa,"))

        // A paper saved later with the same base key gets the next suffix; the first two keep theirs.
        try await library.save(paper("W3", "Smith"))
        let again = try await repository().export(collectionID: nil)
        #expect(keys(again.bibtex) == ["smith2020deep", "smith2020deepa", "smith2020deepb"])
        #expect(try await citeKey("W1") == "smith2020deep")
        #expect(try await citeKey("W3") == "smith2020deepb")
    }

    /// Export, remove a paper, restore it with Undo, export again: its key is unchanged.
    @Test func keysSurviveRemoveAndUndo() async throws {
        try await library.save(paper("W1", "Smith"))
        try await library.save(paper("W2", "Smith"))
        _ = try await repository().export(collectionID: nil)
        #expect(try await citeKey("W1") == "smith2020deep")

        let removed = try #require(try await library.remove(openAlexID: "W1"))
        try await library.restore(removed)
        let again = try await repository().export(collectionID: nil)

        #expect(try await citeKey("W1") == "smith2020deep")
        #expect(try await citeKey("W2") == "smith2020deepa")
        #expect(keys(again.bibtex) == ["smith2020deep", "smith2020deepa"])
    }

    @Test func keysDoNotDependOnWhichCollectionIsExportedFirst() async throws {
        try await library.save(paper("W1", "Smith"))
        try await library.save(paper("W2", "Smith"))
        let id = try #require(try await store.insertCollection(name: "A", nameKey: "a", createdAt: 1))
        try await store.addToCollection(collectionID: id, openAlexID: "W2", addedAt: 1)

        #expect(try await repository().export(collectionID: id).bibtex.hasPrefix("@misc{smith2020deepa,"))
        #expect(try await citeKey("W1") == "smith2020deep")
    }

    @Test func exportCoversTheWholeCollectionRegardlessOfStatus() async throws {
        try await library.save(paper("W1", "Adams"))
        try await library.save(paper("W2", "Brown"))
        try await library.save(paper("W3", "Clark"))
        try await library.setStatus(openAlexID: "W2", status: .read)
        let id = try #require(try await store.insertCollection(name: "A", nameKey: "a", createdAt: 1))
        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 1)
        try await store.addToCollection(collectionID: id, openAlexID: "W2", addedAt: 1)

        let bibtex = try await repository().export(collectionID: id).bibtex
        #expect(bibtex.contains("{adams2020deep,"))
        #expect(bibtex.contains("{brown2020deep,"))
        #expect(!bibtex.contains("clark"))
    }

    @Test func emptyCollectionExportsAnEmptyCompleteFile() async throws {
        let id = try #require(try await store.insertCollection(name: "A", nameKey: "a", createdAt: 1))
        #expect(try await repository().export(collectionID: id) == CitationResult(bibtex: "", complete: true))
    }

    @Test func aPaperRemovedDuringTheExportIsLeftOut() async throws {
        try await saveUnfetched(paper("W1", "Adams"))
        try await saveUnfetched(paper("W2", "Brown"))
        let library = self.library
        let removing = ScriptedLookup { id in if id == "W2" { _ = try await library.remove(openAlexID: "W2") } }

        let result = try await repository(removing).export(collectionID: nil)

        #expect(result.complete)
        #expect(result.bibtex.contains("{adams2020deep,"))
        #expect(!result.bibtex.contains("brown"))
    }

    @Test func aPaperRemovedDuringACopyHasNoEntry() async throws {
        try await saveUnfetched(paper("W1", "Adams"))
        let library = self.library
        let removing = ScriptedLookup { id in _ = try await library.remove(openAlexID: id) }

        #expect(try await repository(removing).entry(openAlexID: "W1") == nil)
    }

    @Test func refetchesAtMostFourAtATime() async throws {
        for index in 0..<9 {
            try await saveUnfetched(paper("W\(index)", "S\(index)"))
        }
        let slow = CountingLookup()

        #expect(try await repository(slow).export(collectionID: nil).complete)
        #expect(slow.peak == 4)
        #expect(slow.requests == 9)
    }

    @Test func cancellingTheExportStopsTheRefetch() async throws {
        for index in 0..<9 {
            try await saveUnfetched(paper("W\(index)", "S\(index)"))
        }
        let slow = CountingLookup()
        let repository = repository(slow)

        let export = Task { try await repository.export(collectionID: nil) }
        export.cancel()

        await #expect(throws: CancellationError.self) { try await export.value }
        #expect(slow.requests < 9)
    }
}
```

Create `ios/HashiyaKit/Tests/HashiyaDataTests/ExportFilesTests.swift`. It mirrors Android's `BibFileNameTest`, plus the file writing:

```swift
import Foundation
import HashiyaData
import Testing

struct ExportFilesTests {
    @Test func wholeLibrary() {
        #expect(ExportFiles.fileName(collectionName: nil) == "hashiya-library")
    }

    @Test func collectionNameWithUnsafeCharactersReplaced() {
        #expect(ExportFiles.fileName(collectionName: "Chapter 2") == "Chapter 2")
        #expect(ExportFiles.fileName(collectionName: "a/b\\c:d*e?f\"g<h>i|j") == "a-b-c-d-e-f-g-h-i-j")
        #expect(ExportFiles.fileName(collectionName: "tab\tx") == "tab-x")
        #expect(ExportFiles.fileName(collectionName: "الفصل الثاني") == "الفصل الثاني")
    }

    @Test func nothingLeftIsCollection() {
        #expect(ExportFiles.fileName(collectionName: "/") == "-")
        #expect(ExportFiles.fileName(collectionName: "   ") == "collection")
    }

    @Test func writesUTF8AndDeletesEarlierExports() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let files = ExportFiles(directory: directory)

        let first = try files.write("@misc{a,\n}\n", name: "hashiya-library")
        let second = try files.write("@misc{müller2020,\n}\n", name: "Thesis")

        #expect(second.lastPathComponent == "Thesis.bib")
        #expect(try String(contentsOf: second, encoding: .utf8) == "@misc{müller2020,\n}\n")
        #expect(!FileManager.default.fileExists(atPath: first.path))
        #expect(try FileManager.default.contentsOfDirectory(atPath: directory.path) == ["Thesis.bib"])
    }

    @Test func liveIsTheCachesExportsFolder() {
        #expect(ExportFiles.live.directory.lastPathComponent == "exports")
        #expect(ExportFiles.live.directory.deletingLastPathComponent().lastPathComponent == "Caches")
    }
}
```

Create `ios/HashiyaKit/Tests/HashiyaDataTests/FakeCitationRepositoryTests.swift`:

```swift
import HashiyaData
import HashiyaTesting
import Testing

struct FakeCitationRepositoryTests {
    @Test @MainActor func recordsCallsAndAnswersAsScripted() async throws {
        let fake = FakeCitationRepository(export: CitationResult(bibtex: "x", complete: false))

        #expect(try await fake.export(collectionID: 3) == CitationResult(bibtex: "x", complete: false))
        #expect(try await fake.entry(openAlexID: "W1")?.complete == true)
        #expect(fake.exportCalls == [3])
        #expect(fake.entryCalls == ["W1"])

        fake.setFail(true)
        await #expect(throws: FakeCitationRepository.Failure.self) { try await fake.export(collectionID: nil) }
    }

    @Test @MainActor func aHeldExportWaitsForRelease() async throws {
        let fake = FakeCitationRepository()
        fake.holdExports()
        let export = Task { try await fake.export(collectionID: nil) }
        #expect(await eventually { fake.heldExports == 1 })

        fake.releaseExports()
        #expect(try await export.value.complete)
    }
}
```

`eventually(timeout:_:)` is the existing helper in `HashiyaTesting/Eventually.swift`. It returns whether the condition became true.

- [ ] **Step 2: Run them and see them fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaDataTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL: `cannot find 'GRDBCitationRepository' in scope`, `cannot find 'ExportFiles' in scope`, `cannot find 'FakeCitationRepository' in scope`, then `** TEST FAILED **`.

- [ ] **Step 3: Implement**

In `ios/HashiyaKit/Package.swift`, change the `HashiyaData` target:

```swift
        .target(name: "HashiyaData", dependencies: ["HashiyaModel", "HashiyaNetwork", "HashiyaDatabase", "HashiyaBibTeX"]),
```

Create `ios/HashiyaKit/Sources/HashiyaData/CitationRepository.swift`:

```swift
import Foundation
import HashiyaBibTeX
import HashiyaDatabase
import HashiyaModel
import HashiyaNetwork

/// `complete` is false when at least one exported paper's details still couldn't be fetched, so its entry may lack volume or pages.
public struct CitationResult: Equatable, Sendable {
    public var bibtex: String
    public var complete: Bool

    public init(bibtex: String, complete: Bool) {
        self.bibtex = bibtex
        self.complete = complete
    }
}

public protocol CitationRepository: Sendable {
    /// One saved paper's BibTeX entry, refetching its details first if needed. Nil if it isn't saved.
    func entry(openAlexID: String) async throws -> CitationResult?
    /// Every paper in `collectionID` (nil = the whole library), regardless of any search or status filter.
    func export(collectionID: Int64?) async throws -> CitationResult
}

/// Refetches the details papers saved before v4 lack, once each; assigns cite keys once; then builds the BibTeX text.
public struct GRDBCitationRepository: CitationRepository {
    private let store: PaperStore
    private let lookup: any OpenAlexLookupService
    private let maxConcurrentRefetches: Int

    public init(store: PaperStore, lookup: any OpenAlexLookupService, maxConcurrentRefetches: Int = 4) {
        self.store = store
        self.lookup = lookup
        self.maxConcurrentRefetches = maxConcurrentRefetches
    }

    public func entry(openAlexID: String) async throws -> CitationResult? {
        guard let stored = try await store.citablePaper(openAlexID: openAlexID) else { return nil }
        try await refetch([stored])
        try await assignMissingKeys()
        // Nil when the paper was removed while its details were being fetched.
        guard let row = try await store.citablePaper(openAlexID: openAlexID), let citable = row.citable else { return nil }
        return CitationResult(bibtex: BibTeX.entry(citable), complete: row.hasDetails)
    }

    public func export(collectionID: Int64?) async throws -> CitationResult {
        try await refetch(store.citablePapers(collectionID: collectionID))
        try await assignMissingKeys()
        // Read again: papers removed meanwhile drop out. One saved after the keys were assigned has none yet and is left out too.
        let rows = try await store.citablePapers(collectionID: collectionID).filter { $0.paper.citeKey != nil }
        return CitationResult(bibtex: BibTeX.file(rows.compactMap(\.citable)), complete: rows.allSatisfy(\.hasDetails))
    }

    /// Fetches the details papers saved before v4 lack, at most `maxConcurrentRefetches` at a time.
    private func refetch(_ rows: [PaperWithAuthors]) async throws {
        let missing = rows.filter { !$0.hasDetails }
        guard !missing.isEmpty else { return }
        try await withThrowingTaskGroup(of: Void.self) { group in
            var pending = missing.makeIterator()
            for _ in 0..<min(maxConcurrentRefetches, missing.count) {
                guard let row = pending.next() else { break }
                group.addTask { try await refetchOne(row) }
            }
            while try await group.next() != nil {
                if let row = pending.next() {
                    group.addTask { try await refetchOne(row) }
                }
            }
        }
    }

    /// A failed request leaves details_fetched at 0, so the next export or copy asks again. Cancellation is rethrown.
    private func refetchOne(_ row: PaperWithAuthors) async throws {
        guard let openAlexID = row.paper.openAlexID else { return }
        let work: NetworkWork?
        do {
            work = try await lookup.work(id: openAlexID)
        } catch is NetworkFailure {
            return
        }
        // Both updates match no row if the paper was removed meanwhile.
        if let work {
            try await store.updatePublicationDetails(paperID: row.paper.id, details: work.asPublicationDetails())
        } else {
            try await store.markDetailsFetched(paperID: row.paper.id)
        }
    }

    /// Gives every keyless saved paper a key, oldest saved first, in one transaction. Keys are assigned across the whole library,
    /// not just the exported papers, so a key never depends on which collection was exported first. A clash with a key another
    /// export stored at the same moment retries once against the fresh set.
    private func assignMissingKeys() async throws {
        for attempt in 0..<2 {
            let keyless = try await store.papersWithoutCiteKeys()
            guard !keyless.isEmpty else { return }
            let keys = CiteKeys.assign(keyless.map { $0.asPaper() }, taken: try await store.allCiteKeys())
            do {
                try await store.assignCiteKeys(Dictionary(uniqueKeysWithValues: zip(keyless.map(\.paper.id), keys)))
                return
            } catch let clash as CiteKeyTakenError {
                if attempt == 1 { throw clash }
            }
        }
    }
}

extension PaperWithAuthors {
    fileprivate var citable: CitablePaper? {
        paper.citeKey.map { CitablePaper(paper: asPaper(), citeKey: $0) }
    }

    /// Whether the stored details are as complete as they will get. A paper with no OpenAlex ID has nothing to refetch.
    fileprivate var hasDetails: Bool {
        paper.detailsFetched || paper.openAlexID == nil
    }
}

/// For launches with no network (UI tests): every work is unknown, so papers are marked fetched and never asked for again.
struct OfflineLookupService: OpenAlexLookupService {
    func work(id: String) async throws -> NetworkWork? { nil }
    func works(filter: String, perPage: Int) async throws -> NetworkWorksResponse { throw NetworkFailure.connectivity }
}
```

Create `ios/HashiyaKit/Sources/HashiyaData/ExportFiles.swift`:

```swift
import Foundation

/// Where an export's `.bib` file is written before it is shared. Only the latest export is kept.
public struct ExportFiles: Sendable {
    public let directory: URL

    public init(directory: URL) {
        self.directory = directory
    }

    /// `Caches/exports`: the system may clear it, and nothing there needs a backup.
    public static var live: ExportFiles {
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        return ExportFiles(directory: caches.appendingPathComponent("exports", isDirectory: true))
    }

    /// Deletes earlier files in the directory, then writes `bibtex` as UTF-8 to `<name>.bib` atomically, and returns its URL.
    public func write(_ bibtex: String, name: String) throws -> URL {
        let files = FileManager.default
        try files.createDirectory(at: directory, withIntermediateDirectories: true)
        for earlier in try files.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) {
            try files.removeItem(at: earlier)
        }
        let url = directory.appendingPathComponent(name + ".bib", isDirectory: false)
        try Data(bibtex.utf8).write(to: url, options: .atomic)
        return url
    }

    private static let unsafe: Set<Unicode.Scalar> = ["/", "\\", ":", "*", "?", "\"", "<", ">", "|"]

    /// "hashiya-library" for the whole library; otherwise the collection's name with characters files can't hold (`/ \ : * ? " < >
    /// |` and ASCII control characters, as Android's `\p{Cntrl}`) replaced by "-", trimmed, and "collection" when nothing is left.
    /// No extension: `write` adds ".bib".
    public static func fileName(collectionName: String?) -> String {
        guard let collectionName else { return "hashiya-library" }
        var safe = String.UnicodeScalarView()
        for scalar in collectionName.unicodeScalars {
            let control = scalar.value < 0x20 || scalar.value == 0x7F
            safe.append(unsafe.contains(scalar) || control ? "-" : scalar)
        }
        let trimmed = String(safe).trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "collection" : trimmed
    }
}
```

Create `ios/HashiyaKit/Sources/HashiyaData/LibraryRepositories.swift`:

```swift
import Foundation
import HashiyaDatabase
import HashiyaNetwork

/// The library, collections and citations on one database store, so they share one connection pool and see each other's
/// writes at once.
public struct LibraryRepositories: Sendable {
    public let library: GRDBLibraryRepository
    public let collections: GRDBCollectionsRepository
    public let citations: GRDBCitationRepository

    init(store: PaperStore, lookup: any OpenAlexLookupService) {
        library = GRDBLibraryRepository(store: store)
        collections = GRDBCollectionsRepository(store: store)
        citations = GRDBCitationRepository(store: store, lookup: lookup)
    }

    /// The App Group database file `fileName`; `fresh` deletes it first (UI tests only). With no `lookup`, papers are never
    /// refetched: they count as complete with what is stored.
    public static func shared(
        fileName: String = HashiyaDatabase.fileName,
        fresh: Bool = false,
        lookup: (any OpenAlexLookupService)? = nil
    ) throws -> LibraryRepositories {
        let url = try HashiyaDatabase.sharedDatabaseURL(fileName: fileName)
        if fresh { try HashiyaDatabase.removeDatabase(at: url) }
        return LibraryRepositories(store: try PaperStore.open(at: url), lookup: lookup ?? OfflineLookupService())
    }
}
```

`ios/HashiyaKit/Sources/HashiyaData/LiveDependencies.swift`: replace the struct's stored properties, `init` and `live(bundle:)`. `builtInAPIKey` and `infoValue` stay as they are.

```swift
/// The long-lived objects of the app (and, in spec 2, of the Share Extension), built one way.
public struct LiveDependencies: Sendable {
    public let libraryRepository: any LibraryRepository
    public let searchRepository: any SearchRepository
    public let lookupRepository: any PaperLookupRepository
    public let preferences: any UserPreferencesRepository
    public let collections: any CollectionsRepository
    public let citations: any CitationRepository
    public let exportFiles: ExportFiles

    public init(
        libraryRepository: any LibraryRepository,
        searchRepository: any SearchRepository,
        lookupRepository: any PaperLookupRepository,
        preferences: any UserPreferencesRepository,
        collections: any CollectionsRepository,
        citations: any CitationRepository,
        exportFiles: ExportFiles
    ) {
        self.libraryRepository = libraryRepository
        self.searchRepository = searchRepository
        self.lookupRepository = lookupRepository
        self.preferences = preferences
        self.collections = collections
        self.citations = citations
        self.exportFiles = exportFiles
    }

    /// The real graph: the App Group database (one store for the library, collections and citations), the Keychain, OpenAlex
    /// over one URLSession and arXiv over its own. Reads `OpenAlexAPIKey` and `KeychainAccessGroup` from `bundle`'s Info.plist.
    public static func live(bundle: Bundle = .main) throws -> LiveDependencies {
        let preferences = KeychainUserPreferencesRepository(
            keychain: SystemKeychainStore(accessGroup: infoValue(bundle.object(forInfoDictionaryKey: "KeychainAccessGroup")))
        )
        let session = OpenAlexSession.make()
        let builtInKey = builtInAPIKey(from: bundle.object(forInfoDictionaryKey: "OpenAlexAPIKey"))
        let searchClient = OpenAlexSearchClient(session: session, builtInKey: builtInKey, userKeySource: preferences)
        let lookupClient = OpenAlexLookupClient(session: session, builtInKey: builtInKey, userKeySource: preferences)
        let repositories = LibraryRepositories(store: try PaperStore.shared(), lookup: lookupClient)
        return LiveDependencies(
            libraryRepository: repositories.library,
            searchRepository: OpenAlexSearchRepository(service: searchClient),
            lookupRepository: OpenAlexPaperLookupRepository(openAlex: lookupClient, arxiv: ArxivTitleClient()),
            preferences: preferences,
            collections: repositories.collections,
            citations: repositories.citations,
            exportFiles: .live
        )
    }
```

In `ios/Hashiya/UITestingStubs.swift`, replace `dependencies()`:

```swift
    static func dependencies() -> LiveDependencies {
        // One store for the library, collections and citations; no lookup, so Copy BibTeX never touches the network.
        let repositories = try! LibraryRepositories.shared(fileName: UITestingFlags.databaseFileName, fresh: true)
        return LiveDependencies(
            libraryRepository: repositories.library,
            searchRepository: StubSearchRepository(),
            lookupRepository: StubPaperLookupRepository(),
            preferences: KeychainUserPreferencesRepository(keychain: InMemoryKeychain()),
            collections: repositories.collections,
            citations: repositories.citations,
            exportFiles: .live
        )
    }
```

Update the type's doc comment: after "a lookup that knows arXiv 1706.03762", add ", and citations that never refetch". `AppContainer` doesn't change in this task; Task 12 hands the new dependencies to the view models.

Create `ios/HashiyaKit/Sources/HashiyaTesting/FakeCitationRepository.swift`:

```swift
import Foundation
import HashiyaData
import os

/// Scripted citations, recording every call. Exports can be held, so a test can see one in progress.
public final class FakeCitationRepository: CitationRepository {
    public struct Failure: Error {}

    public static let sampleEntry = CitationResult(bibtex: "@article{k,\n}\n", complete: true)

    private struct State {
        var entry: CitationResult?
        var export: CitationResult
        var fail = false
        /// Non-nil while exports are held: the waiting exports.
        var heldExports: [CheckedContinuation<Void, Never>]?
        var exportCalls: [Int64?] = []
        var entryCalls: [String] = []
    }

    private let state: OSAllocatedUnfairLock<State>

    /// - Parameters:
    ///   - entry: what every `entry(openAlexID:)` returns (nil: not saved).
    ///   - export: what every `export(collectionID:)` returns.
    public init(entry: CitationResult? = FakeCitationRepository.sampleEntry, export: CitationResult = FakeCitationRepository.sampleEntry) {
        state = OSAllocatedUnfairLock(initialState: State(entry: entry, export: export))
    }

    /// Every `export` call's collection, in order, recorded when it starts.
    public var exportCalls: [Int64?] { state.withLock { $0.exportCalls } }
    /// Every `entry` call's OpenAlex ID, in order.
    public var entryCalls: [String] { state.withLock { $0.entryCalls } }
    /// The exports waiting while exports are held.
    public var heldExports: Int { state.withLock { $0.heldExports?.count ?? 0 } }

    /// When true, `entry` and `export` throw `Failure`.
    public func setFail(_ fail: Bool) { state.withLock { $0.fail = fail } }
    public func setEntry(_ entry: CitationResult?) { state.withLock { $0.entry = entry } }
    public func setExport(_ export: CitationResult) { state.withLock { $0.export = export } }

    /// From now on `export` waits until `releaseExports()`.
    public func holdExports() {
        state.withLock { if $0.heldExports == nil { $0.heldExports = [] } }
    }

    /// Lets every held export run, in order, and stops holding.
    public func releaseExports() {
        let waiting = state.withLock { state -> [CheckedContinuation<Void, Never>] in
            defer { state.heldExports = nil }
            return state.heldExports ?? []
        }
        waiting.forEach { $0.resume() }
    }

    public func entry(openAlexID: String) async throws -> CitationResult? {
        try state.withLock { state in
            state.entryCalls.append(openAlexID)
            if state.fail { throw Failure() }
            return state.entry
        }
    }

    public func export(collectionID: Int64?) async throws -> CitationResult {
        state.withLock { $0.exportCalls.append(collectionID) }
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            let held = state.withLock { state -> Bool in
                guard state.heldExports != nil else { return false }
                state.heldExports?.append(continuation)
                return true
            }
            if !held { continuation.resume() }
        }
        return try state.withLock { state in
            if state.fail { throw Failure() }
            return state.export
        }
    }
}
```

- [ ] **Step 4: Run them and see them pass**

Run: the Step 2 command. Then check that the app still builds with the new `LiveDependencies`:
`cd ios && xcodegen generate --spec project.yml && xcodebuild build -project Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' 2>&1 | grep -E 'error:|\*\* BUILD'`
Expected: PASS: `** TEST SUCCEEDED **`, then `** BUILD SUCCEEDED **`.

- [ ] **Step 5: Commit, push and verify**

```bash
git add ios/HashiyaKit/Package.swift \
  ios/HashiyaKit/Sources/HashiyaData/CitationRepository.swift ios/HashiyaKit/Sources/HashiyaData/ExportFiles.swift \
  ios/HashiyaKit/Sources/HashiyaData/LibraryRepositories.swift ios/HashiyaKit/Sources/HashiyaData/LiveDependencies.swift \
  ios/Hashiya/UITestingStubs.swift \
  ios/HashiyaKit/Sources/HashiyaTesting/FakeCitationRepository.swift ios/HashiyaKit/Sources/HashiyaTesting/FakeLookupServices.swift \
  ios/HashiyaKit/Tests/HashiyaDataTests/GRDBCitationRepositoryTests.swift ios/HashiyaKit/Tests/HashiyaDataTests/ExportFilesTests.swift \
  ios/HashiyaKit/Tests/HashiyaDataTests/FakeCitationRepositoryTests.swift
git commit -m "feat: build BibTeX on iOS from stored details, refetching old papers once, and write the export file"
git log --format='%an <%ae>' origin/main..HEAD
git push
```
On the Mac, run the full scheme as in the verification line. In a cloud session, read the "iOS" workflow run for this SHA. Expected: `success`.

---

---

### Task 9: `HashiyaDesignSystem` — `CollectionNameSheet`, the share sheet presenter, and the collection strings

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/CollectionNameSheet.swift`
- Create: `ios/HashiyaKit/Sources/HashiyaDesignSystem/ShareSheet.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaDesignSystem/DesignSystemStrings.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaDesignSystem/Resources/Localizable.xcstrings` (through the Python snippet)
- Create: `ios/HashiyaKit/Tests/HashiyaDesignSystemTests/CollectionNameSheetTests.swift`
- Modify: `ios/HashiyaSnapshotTests/DesignSystemSnapshotTests.swift`

**Interfaces:**
- Consumes (Task 1): `isValidCollectionName(_:)`, `collectionNameMaxLength`.
- Produces:
  - `public struct CollectionNameSheet: View` with `public enum Mode: Sendable, Equatable { case create, rename }`, `public init(mode: Mode, initialName: String = "", error: String?, onSubmit: @escaping (String) -> Void, onCancel: @escaping () -> Void)` and `public static func canSubmit(_ name: String) -> Bool`.
  - `@MainActor public enum ShareSheet { public static func present(fileURL: URL) async }`.
  - `DesignSystemStrings.collectionNameTaken: String`.
  - Catalog keys `collection.nameLabel`, `collection.newTitle`, `collection.renameTitle`, `collection.create`, `collection.save`, `collection.cancel` and `collection.nameTaken`.

The sheet uses only system chrome: a navigation bar with Cancel and Create/Save, and a rounded-border text field. On iOS 26 the sheet and its toolbar buttons get Liquid Glass from the system, so **no glass modifiers** are added here. The share sheet is UIKit's own `UIActivityViewController`.

- [ ] **Step 1: Write the failing tests**

`ios/HashiyaKit/Tests/HashiyaDesignSystemTests/CollectionNameSheetTests.swift`:
```swift
@testable import HashiyaDesignSystem
import HashiyaModel
import SwiftUI
import Testing
import UIKit

@MainActor
@Suite(.serialized)
struct CollectionNameSheetTests {
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

    @Test func createAndSaveAreEnabledOnlyForAValidName() {
        #expect(!CollectionNameSheet.canSubmit(""))
        #expect(!CollectionNameSheet.canSubmit("   \n"))
        #expect(CollectionNameSheet.canSubmit("Thesis"))
        #expect(CollectionNameSheet.canSubmit("  Thesis  "))
        #expect(CollectionNameSheet.canSubmit(String(repeating: "x", count: collectionNameMaxLength)))
        #expect(CollectionNameSheet.canSubmit("  " + String(repeating: "x", count: collectionNameMaxLength) + "  "))
        #expect(!CollectionNameSheet.canSubmit(String(repeating: "x", count: collectionNameMaxLength + 1)))
    }

    @Test func newCollectionHasCreateAndRenameHasSave() {
        let create = renderedStrings(of: CollectionNameSheet(mode: .create, error: nil, onSubmit: { _ in }, onCancel: {}))
        #expect(create.contains("New collection"))
        #expect(create.contains("Create"))
        #expect(create.contains("Cancel"))
        #expect(!create.contains("Save"))

        let rename = renderedStrings(of: CollectionNameSheet(mode: .rename, initialName: "Thesis", error: nil, onSubmit: { _ in }, onCancel: {}))
        #expect(rename.contains("Rename collection"))
        #expect(rename.contains("Save"))
        #expect(!rename.contains("Create"))
    }

    @Test func theStringsHaveBothLanguages() {
        #expect(inLanguage("en") { DesignSystemStrings.collectionNameTaken } == "A collection with that name already exists")
        #expect(inLanguage("ar") { DesignSystemStrings.collectionNameTaken } == "توجد مجموعة بهذا الاسم بالفعل")
        #expect(inLanguage("en") { L10n.string("collection.nameLabel") } == "Collection name")
        #expect(inLanguage("ar") { L10n.string("collection.nameLabel") } == "اسم المجموعة")
        #expect(inLanguage("ar") { L10n.string("collection.newTitle") } == "مجموعة جديدة")
        #expect(inLanguage("ar") { L10n.string("collection.renameTitle") } == "إعادة تسمية المجموعة")
        #expect(inLanguage("ar") { L10n.string("collection.create") } == "إنشاء")
        #expect(inLanguage("ar") { L10n.string("collection.save") } == "حفظ")
        #expect(inLanguage("ar") { L10n.string("collection.cancel") } == "إلغاء")
    }
}
```

- [ ] **Step 2: Run them and see them fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaDesignSystemTests/CollectionNameSheetTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL with `cannot find 'CollectionNameSheet' in scope` and `type 'DesignSystemStrings' has no member 'collectionNameTaken'`, then `** TEST FAILED **`.

- [ ] **Step 3: Add the strings**

These are Android's `core/designsystem` strings, verbatim:
```bash
python3 - <<'EOF'
import json, pathlib
path = pathlib.Path("ios/HashiyaKit/Sources/HashiyaDesignSystem/Resources/Localizable.xcstrings")
catalog = json.loads(path.read_text(encoding="utf-8"))
for key, en, ar in [
    ("collection.nameLabel", "Collection name", "اسم المجموعة"),
    ("collection.newTitle", "New collection", "مجموعة جديدة"),
    ("collection.renameTitle", "Rename collection", "إعادة تسمية المجموعة"),
    ("collection.create", "Create", "إنشاء"),
    ("collection.save", "Save", "حفظ"),
    ("collection.cancel", "Cancel", "إلغاء"),
    ("collection.nameTaken", "A collection with that name already exists", "توجد مجموعة بهذا الاسم بالفعل"),
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

- [ ] **Step 4: Implement the sheet, the presenter and the string accessor**

In `ios/HashiyaKit/Sources/HashiyaDesignSystem/DesignSystemStrings.swift`, add inside `DesignSystemStrings` after `removeFromLibrary`:
```swift
    /// The name sheet's error, which the Library and Details pass back on a clash.
    public static var collectionNameTaken: String { L10n.string("collection.nameTaken") }
```

`ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/CollectionNameSheet.swift`:
```swift
import HashiyaModel
import SwiftUI

/// Names a new collection or renames one: one field, Cancel, and Create or Save. Present it as a sheet's content.
///
/// It doesn't validate against other collections: the caller submits, and passes "A collection with that name
/// already exists" back as `error` on a clash. The error stays under the field until the name is edited. A caller that
/// gets the same clash again clears `error` before submitting, so the error shows again.
public struct CollectionNameSheet: View {
    public enum Mode: Sendable, Equatable {
        case create, rename
    }

    private let mode: Mode
    private let error: String?
    private let onSubmit: (String) -> Void
    private let onCancel: () -> Void

    /// Seeded once from `initialName`; the sheet owns the typed text.
    @State private var name: String
    /// False once the name is edited after `error` appeared.
    @State private var showsError = true
    @FocusState private var isFocused: Bool
    @Environment(\.layoutDirection) private var uiDirection

    public init(
        mode: Mode,
        initialName: String = "",
        error: String?,
        onSubmit: @escaping (String) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.mode = mode
        self.error = error
        self.onSubmit = onSubmit
        self.onCancel = onCancel
        _name = State(initialValue: initialName)
    }

    /// Create or Save is enabled only for a valid name: 1–60 characters after trimming.
    public static func canSubmit(_ name: String) -> Bool {
        isValidCollectionName(name)
    }

    public var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 8) {
                TextField(text: $name) {
                    Text(verbatim: L10n.string("collection.nameLabel"))
                }
                .font(.hashiya(.body))
                .textFieldStyle(.roundedBorder)
                // A collection named in Arabic in the English UI (or the reverse) is typed in its own direction.
                .environment(\.layoutDirection, ContentDirection.of(name) ?? uiDirection)
                .focused($isFocused)
                .submitLabel(.done)
                .onSubmit(submit)
                .accessibilityIdentifier("collection.name")
                if showsError, let error {
                    Text(verbatim: error)
                        .font(.hashiya(.meta))
                        .foregroundStyle(HashiyaColors.error)
                        .accessibilityIdentifier("collection.nameError")
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(HashiyaColors.surface)
            .navigationTitle(Text(verbatim: L10n.string(mode == .create ? "collection.newTitle" : "collection.renameTitle")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(action: onCancel) {
                        Text(verbatim: L10n.string("collection.cancel"))
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: submit) {
                        Text(verbatim: L10n.string(mode == .create ? "collection.create" : "collection.save"))
                    }
                    .disabled(!Self.canSubmit(name))
                }
            }
        }
        // Tall enough for the bar, the field and a one-line error at the default text size; .medium for larger sizes.
        .presentationDetents([.height(200), .medium])
        .onAppear { isFocused = true }
        .onChange(of: name) { showsError = false }
        .onChange(of: error) { showsError = true }
    }

    private func submit() {
        guard Self.canSubmit(name) else { return }
        onSubmit(name)
    }
}
```

`ios/HashiyaKit/Sources/HashiyaDesignSystem/ShareSheet.swift`:
```swift
import UIKit

/// The system share sheet for a file, presented from UIKit. SwiftUI's `.sheet` around a `UIActivityViewController`
/// shows a blank sheet first and can't report when the user is done.
@MainActor
public enum ShareSheet {
    /// Presents the share sheet for `fileURL` and returns when it closes, whether the file was shared or not.
    /// Returns at once when there is no window to present from.
    public static func present(fileURL: URL) async {
        guard let presenter = topViewController(), !presenter.isBeingDismissed else { return }
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            let finish = Finish(continuation)
            let controller = UIActivityViewController(activityItems: [fileURL], applicationActivities: nil)
            controller.completionWithItemsHandler = { _, _, _, _ in
                // UIKit calls this on the main thread when the sheet closes.
                MainActor.assumeIsolated { finish.run() }
            }
            presenter.present(controller, animated: true)
        }
    }

    /// The key window's front-most view controller.
    private static func topViewController() -> UIViewController? {
        let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
        var top = (windows.first(where: \.isKeyWindow) ?? windows.first)?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}

/// Resumes the continuation once, however many times the completion handler runs.
@MainActor
private final class Finish {
    private var continuation: CheckedContinuation<Void, Never>?

    init(_ continuation: CheckedContinuation<Void, Never>) {
        self.continuation = continuation
    }

    func run() {
        continuation?.resume()
        continuation = nil
    }
}
```

- [ ] **Step 5: Run the tests and see them pass**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaDesignSystemTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: every `HashiyaDesignSystemTests` test passes, then `** TEST SUCCEEDED **`.

- [ ] **Step 6: Add the name sheet's snapshot (clash error shown)**

In `ios/HashiyaSnapshotTests/DesignSystemSnapshotTests.swift`, add this test inside `DesignSystemSnapshotTests`, after `updateRequired()`:
```swift
    /// The name sheet as the Library and Details show it after a clash. Rendered as the sheet's content, full screen.
    @Test func collectionNameSheetWithTheClash() {
        assertHashiyaSnapshots(of: NameTakenSheet(), named: "collectionNameTaken", arabicText: "توجد مجموعة بهذا الاسم بالفعل")
    }
```
and this fixture at the end of the file (it looks the error up in `body`, so the Arabic image gets the Arabic error):
```swift
private struct NameTakenSheet: View {
    var body: some View {
        CollectionNameSheet(
            mode: .create,
            initialName: "Thesis",
            error: DesignSystemStrings.collectionNameTaken,
            onSubmit: { _ in },
            onCancel: {}
        )
    }
}
```

Run: `xcodegen generate --spec ios/project.yml && xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaSnapshotTests/DesignSystemSnapshotTests -collect-test-diagnostics never 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST|No reference'`
Expected: only `collectionNameSheetWithTheClash` fails, with `No reference was found on disk. Automatically recorded snapshot: …`. Every other design-system snapshot passes. The baselines come from CI in Task 12, so don't stage the recorded PNGs.

- [ ] **Step 7: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/CollectionNameSheet.swift \
  ios/HashiyaKit/Sources/HashiyaDesignSystem/ShareSheet.swift \
  ios/HashiyaKit/Sources/HashiyaDesignSystem/DesignSystemStrings.swift \
  ios/HashiyaKit/Sources/HashiyaDesignSystem/Resources/Localizable.xcstrings \
  ios/HashiyaKit/Tests/HashiyaDesignSystemTests/CollectionNameSheetTests.swift \
  ios/HashiyaSnapshotTests/DesignSystemSnapshotTests.swift
git commit -m "feat: add the collection name sheet and the share sheet presenter"
```
On a cloud session, push and read the "iOS" workflow for this commit. The only expected failure is the new snapshot's `No reference was found on disk`.

---

### Task 10: `FeatureLibrary` — collections, the title menu, swipe in a collection, and Export .bib

**Files:**
- Modify (full file below): `ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift`
- Modify (full file below): `ios/HashiyaKit/Sources/FeatureLibrary/LibraryView.swift`
- Modify: `ios/HashiyaKit/Sources/FeatureLibrary/L10n.swift`
- Modify: `ios/HashiyaKit/Sources/FeatureLibrary/Resources/Localizable.xcstrings` (through the Python snippet)
- Modify: `ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryViewModelTests.swift` (the `makeViewModel` helper only)
- Create: `ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryCollectionsViewModelTests.swift`
- Modify: `ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryStringsTests.swift`
- Modify: `ios/HashiyaSnapshotTests/LibrarySnapshotTests.swift`
- Delete: the `papersWithChipsAndBadges.papers-*`, `aFilteredSearch.filtered-*`, `noMatches.noMatches-*`, `undoBanner.undo-*` and `statusUpdateFailedBanner.statusFailed-*` PNGs under `ios/HashiyaSnapshotTests/__Snapshots__/iOS18/LibrarySnapshotTests/` and `…/iOS26/LibrarySnapshotTests/`
- Modify: `ios/HashiyaUITests/LibraryFlowTests.swift` (`testTheLibraryShowsItsLargeTitle`)
- Modify: `ios/Hashiya/AppContainer.swift`

**Interfaces:**
- Consumes:
  - Task 1: `PaperCollection`.
  - Task 7: `LibraryRepository.observeLibrary(query:status:collectionID:)`, `LibrarySnapshot.libraryTotal` (the view) and `.allPapersTotal` (the whole library), `CollectionsRepository`, `CollectionResult`, `FakeCollectionsRepository(library:)` (contract note 1).
  - Task 8: `CitationRepository`, `CitationResult`, `ExportFiles(directory:)`, `ExportFiles.write(_:name:)`, `ExportFiles.fileName(collectionName:)`, `FakeCitationRepository`, and `LiveDependencies.collections`/`.citations`/`.exportFiles`.
  - Task 9: `CollectionNameSheet`, `ShareSheet.present(fileURL:)`, `DesignSystemStrings.collectionNameTaken`.
- Produces, used by Tasks 11–12:
  - `LibraryViewModel.init(library:collections:citations:exportFiles:share:sleep:)`.
  - `LibraryViewModel` members: `selectCollection(_:)`, `restore(text:status:collectionID:)`, `showNewCollection()`, `showRename()`, `submitName(_:) async`, `dismissNameSheet()`, `requestDelete()`, `confirmDelete(_:) async`, `removeFromCollection(openAlexID:) async`, `undoCollectionRemoval() async`, `collectionUndoExpired()` and `export() async`.
  - Its properties: `collections`, `collectionID`, `selectedCollection`, `exporting`, `canExport`, `nameSheet`, `pendingDelete` and `pendingCollectionUndo`.
  - `LibraryState.emptyCollection`.
  - `LibraryMessage` cases `.collectionsUpdateFailed`, `.exportFailed` and `.exportIncomplete`.
  - `AppContainer`: `collectionsRepository`, `citationRepository` and `exportFiles`.
  - Accessibility label "Export .bib" on the export button, which the UI tests use.

Views here use only system chrome (the title menu, toolbar buttons, a sheet, a confirmation dialog) and the existing `HashiyaBanner`/`HashiyaGlassGroup`. **No new glass modifiers.**

- [ ] **Step 1: Point the existing tests at the new initializer**

In `ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryViewModelTests.swift`, replace the `makeViewModel` helper:
```swift
    private func makeViewModel(_ library: FakeLibraryRepository) -> LibraryViewModel {
        LibraryViewModel(
            library: library,
            collections: FakeCollectionsRepository(library: library),
            citations: FakeCitationRepository(),
            exportFiles: ExportFiles(directory: FileManager.default.temporaryDirectory.appendingPathComponent("library-tests-\(UUID().uuidString)")),
            share: { _ in },
            sleep: sleeper.sleep
        )
    }
```
and add `import Foundation` under `@testable import FeatureLibrary`.

- [ ] **Step 2: Write the failing collection and export tests**

`ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryCollectionsViewModelTests.swift`:
```swift
@testable import FeatureLibrary
import Foundation
import HashiyaData
import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import Testing

/// Android's `LibraryCollectionsViewModelTest`, case by case, plus the iOS share sheet's busy rule.
@MainActor
struct LibraryCollectionsViewModelTests {
    private static let bib = "@misc{paper2020,\n}\n"

    private let sleeper = ManualSleeper()
    /// Newest first: ViT, BERT, Attention.
    private let library = FakeLibraryRepository(saved: [SamplePapers.vit, SamplePapers.bert, SamplePapers.attention])
    private let collections: FakeCollectionsRepository
    private let share = ShareRecorder()
    private let exportDirectory = FileManager.default.temporaryDirectory.appendingPathComponent("library-export-\(UUID().uuidString)")

    init() {
        collections = FakeCollectionsRepository(library: library)
    }

    private func makeViewModel(
        citations: FakeCitationRepository = FakeCitationRepository(export: CitationResult(bibtex: bib, complete: true)),
        exportFiles: ExportFiles? = nil
    ) -> LibraryViewModel {
        LibraryViewModel(
            library: library,
            collections: collections,
            citations: citations,
            exportFiles: exportFiles ?? ExportFiles(directory: exportDirectory),
            share: { [share] url in await share.share(url) },
            sleep: sleeper.sleep
        )
    }

    /// "Thesis" with BERT and ViT.
    private func thesis() async throws -> PaperCollection {
        guard case let .done(id) = try await collections.create(name: "Thesis") else {
            Issue.record("Thesis wasn't created")
            return PaperCollection(id: 0, name: "", paperCount: 0)
        }
        try await collections.setMembership(collectionID: id, openAlexID: SamplePapers.bert.openAlexID, member: true)
        try await collections.setMembership(collectionID: id, openAlexID: SamplePapers.vit.openAlexID, member: true)
        return PaperCollection(id: id, name: "Thesis", paperCount: 2)
    }

    private func ids(_ viewModel: LibraryViewModel) -> [String] {
        viewModel.papers.map(\.paper.openAlexID)
    }

    // MARK: Selecting

    @Test func selectingACollectionFiltersTheListAndCounts() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        #expect(await eventually { viewModel.viewTotal == 3 && viewModel.collections == [thesis] })

        viewModel.selectCollection(thesis.id)

        #expect(await eventually { ids(viewModel) == [SamplePapers.vit.openAlexID, SamplePapers.bert.openAlexID] })
        #expect(viewModel.filter?.total == 2)
        #expect(viewModel.selectedCollection == thesis)
        #expect(viewModel.viewTotal == 2)
        #expect(viewModel.allPapersTotal == 3)
        #expect(viewModel.storedCollection == Int(thesis.id))
    }

    @Test func searchAndChipApplyInsideTheCollection() async throws {
        let thesis = try await thesis()
        try await library.setStatus(openAlexID: SamplePapers.bert.openAlexID, status: .read)
        let viewModel = makeViewModel()

        viewModel.selectCollection(thesis.id)
        viewModel.setStatusFilter(.read)

        #expect(await eventually { ids(viewModel) == [SamplePapers.bert.openAlexID] })
        #expect(viewModel.viewTotal == 2)
    }

    @Test func anEmptyCollectionHasItsOwnState() async throws {
        guard case let .done(id) = try await collections.create(name: "Empty") else { return }
        let viewModel = makeViewModel()
        #expect(await eventually { viewModel.collections.count == 1 })

        viewModel.selectCollection(id)

        #expect(await eventually { viewModel.state == .emptyCollection })
        #expect(viewModel.viewTotal == 0)
        #expect(!viewModel.canExport)
        #expect(viewModel.allPapersTotal == 3)
    }

    @Test func theSceneRestoredCollectionIsShown() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()

        viewModel.restore(text: "", status: "", collectionID: LibraryViewModel.collectionID(stored: Int(thesis.id)))

        #expect(viewModel.collectionID == thesis.id)
        #expect(await eventually { ids(viewModel) == [SamplePapers.vit.openAlexID, SamplePapers.bert.openAlexID] })
        #expect(LibraryViewModel.collectionID(stored: -1) == nil)
    }

    @Test func aRestoredCollectionThatIsGoneFallsBackToAllPapers() async {
        let viewModel = makeViewModel()

        viewModel.restore(text: "", status: "", collectionID: 99)

        #expect(await eventually { viewModel.collectionID == nil && viewModel.papers.count == 3 })
        #expect(viewModel.storedCollection == -1)
    }

    @Test func aDeletedCollectionFallsBackToAllPapers() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.papers.count == 2 })

        // Deleted elsewhere (from Details).
        try await collections.delete(id: thesis.id)

        #expect(await eventually { viewModel.collectionID == nil && viewModel.papers.count == 3 })
        #expect(viewModel.selectedCollection == nil)
        #expect(viewModel.storedCollection == -1)
    }

    @Test func selectingACollectionDeletedMeanwhileFallsBackToAllPapers() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        #expect(await eventually { viewModel.collections.count == 1 })
        try await collections.delete(id: thesis.id)
        #expect(await eventually { viewModel.collections.isEmpty })

        viewModel.selectCollection(thesis.id)

        #expect(viewModel.collectionID == nil)
        #expect(await eventually { viewModel.papers.count == 3 })
    }

    // MARK: Swipe in a collection

    @Test func swipeInACollectionRemovesOnlyTheMembershipWithUndo() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.papers.count == 2 && viewModel.selectedCollection != nil })

        await viewModel.removeFromCollection(openAlexID: SamplePapers.bert.openAlexID)

        #expect(viewModel.pendingCollectionUndo == CollectionUndo(collectionID: thesis.id, collectionName: "Thesis", openAlexID: SamplePapers.bert.openAlexID))
        #expect(viewModel.pendingUndo == nil)
        #expect(await eventually { ids(viewModel) == [SamplePapers.vit.openAlexID] })
        #expect(library.savedPapers.count == 3)
        viewModel.selectCollection(nil)
        #expect(await eventually { viewModel.papers.count == 3 })

        await viewModel.undoCollectionRemoval()

        #expect(viewModel.pendingCollectionUndo == nil)
        #expect(collections.collectionIDs(of: SamplePapers.bert.openAlexID) == [thesis.id])
    }

    /// A collection deleted a moment ago: the swipe must never remove the paper from the library.
    @Test func swipeInADeletedCollectionDoesNothing() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.papers.count == 2 && viewModel.selectedCollection != nil })

        try await collections.delete(id: thesis.id)
        await viewModel.removeFromCollection(openAlexID: SamplePapers.bert.openAlexID)

        #expect(viewModel.pendingUndo == nil)
        #expect(library.savedPapers.contains(SamplePapers.bert))
        #expect(await eventually { viewModel.collectionID == nil && viewModel.papers.count == 3 })

        // After the fallback, a late swipe from a row drawn in the collection still does nothing.
        await viewModel.removeFromCollection(openAlexID: SamplePapers.vit.openAlexID)
        #expect(library.savedPapers.contains(SamplePapers.vit))
        #expect(viewModel.pendingUndo == nil)
    }

    @Test func undoIntoADeletedCollectionIsDropped() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.papers.count == 2 && viewModel.selectedCollection != nil })
        await viewModel.removeFromCollection(openAlexID: SamplePapers.bert.openAlexID)
        #expect(viewModel.pendingCollectionUndo != nil)

        try await collections.delete(id: thesis.id)
        #expect(await eventually { viewModel.collections.isEmpty })
        await viewModel.undoCollectionRemoval()

        #expect(viewModel.pendingCollectionUndo == nil)
        #expect(viewModel.message == nil)
        #expect(collections.collectionIDs(of: SamplePapers.bert.openAlexID).isEmpty)
        #expect(await eventually { viewModel.papers.count == 3 })
    }

    @Test func aCollectionSwipeInAllPapersDoesNothing() async throws {
        _ = try await thesis()
        let viewModel = makeViewModel()
        #expect(await eventually { viewModel.papers.count == 3 })

        await viewModel.removeFromCollection(openAlexID: SamplePapers.bert.openAlexID)

        #expect(viewModel.pendingCollectionUndo == nil)
        #expect(library.savedPapers.count == 3)
    }

    @Test func anExpiredCollectionUndoIsForgotten() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.selectedCollection != nil })
        await viewModel.removeFromCollection(openAlexID: SamplePapers.bert.openAlexID)

        viewModel.collectionUndoExpired()
        await viewModel.undoCollectionRemoval()

        #expect(viewModel.pendingCollectionUndo == nil)
        #expect(collections.collectionIDs(of: SamplePapers.bert.openAlexID).isEmpty)
    }

    // MARK: New, rename and delete

    @Test func newCollectionShowsTheClashThenCreatesAndShowsIt() async throws {
        _ = try await thesis()
        let viewModel = makeViewModel()
        #expect(await eventually { viewModel.collections.count == 1 })

        viewModel.showNewCollection()
        #expect(viewModel.nameSheet == NameSheet(mode: .create, initialName: "", error: nil, collectionID: nil))

        await viewModel.submitName(" thesis ")
        #expect(viewModel.nameSheet?.error == DesignSystemStrings.collectionNameTaken)

        await viewModel.submitName("Chapter 2")
        #expect(viewModel.nameSheet == nil)
        #expect(await eventually { viewModel.collections.map(\.name) == ["Chapter 2", "Thesis"] })
        let chapter = try #require(viewModel.collections.first { $0.name == "Chapter 2" })
        #expect(viewModel.collectionID == chapter.id)
        #expect(await eventually { viewModel.state == .emptyCollection })
    }

    @Test func renameAndDelete() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.selectedCollection != nil })

        viewModel.showRename()
        #expect(viewModel.nameSheet == NameSheet(mode: .rename, initialName: "Thesis", error: nil, collectionID: thesis.id))
        await viewModel.submitName("Dissertation")
        #expect(viewModel.nameSheet == nil)
        #expect(await eventually { viewModel.collections.map(\.name) == ["Dissertation"] })
        #expect(viewModel.collectionID == thesis.id)

        viewModel.requestDelete()
        let pending = try #require(viewModel.pendingDelete)
        #expect(pending.name == "Dissertation")
        await viewModel.confirmDelete(pending)

        #expect(viewModel.pendingDelete == nil)
        #expect(viewModel.collectionID == nil)
        #expect(await eventually { viewModel.collections.isEmpty && viewModel.papers.count == 3 })
    }

    @Test func renameShowsTheClashForTheSameNameInAnotherCaseOrSpacing() async throws {
        let thesis = try await thesis()
        guard case let .done(chapterID) = try await collections.create(name: "Chapter 2") else { return }
        let viewModel = makeViewModel()
        viewModel.selectCollection(chapterID)
        #expect(await eventually { viewModel.selectedCollection?.name == "Chapter 2" })

        viewModel.showRename()
        await viewModel.submitName(" thesis ")
        #expect(viewModel.nameSheet?.error == DesignSystemStrings.collectionNameTaken)
        viewModel.dismissNameSheet()
        #expect(viewModel.nameSheet == nil)
        #expect(viewModel.collections.map(\.name) == ["Chapter 2", "Thesis"])

        // A new case of its own name is not a clash.
        viewModel.selectCollection(thesis.id)
        viewModel.showRename()
        await viewModel.submitName("THESIS")
        #expect(viewModel.nameSheet == nil)
        #expect(await eventually { viewModel.collections.map(\.name) == ["Chapter 2", "THESIS"] })
    }

    @Test func theSameClashTwiceShowsTheErrorAgain() async throws {
        _ = try await thesis()
        let viewModel = makeViewModel()
        viewModel.showNewCollection()

        await viewModel.submitName("Thesis")
        #expect(viewModel.nameSheet?.error != nil)
        await viewModel.submitName("Thesis")
        #expect(viewModel.nameSheet?.error == DesignSystemStrings.collectionNameTaken)
    }

    @Test func renamingACollectionDeletedMeanwhileClosesTheSheetWithAMessage() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.selectedCollection != nil })
        viewModel.showRename()
        try await collections.delete(id: thesis.id)

        await viewModel.submitName("Dissertation")

        #expect(viewModel.nameSheet == nil)
        #expect(viewModel.message == .collectionsUpdateFailed)
        #expect(await eventually { viewModel.collections.isEmpty && viewModel.collectionID == nil })
    }

    @Test func aFailedCollectionChangeClosesTheSheetWithAMessage() async throws {
        _ = try await thesis()
        let viewModel = makeViewModel()
        collections.setFailWrites(true)

        viewModel.showNewCollection()
        await viewModel.submitName("Chapter 2")

        #expect(viewModel.nameSheet == nil)
        #expect(viewModel.message == .collectionsUpdateFailed)
    }

    @Test func aDoubleCreateRunsOnce() async throws {
        let viewModel = makeViewModel()
        collections.holdCreates()
        viewModel.showNewCollection()

        let first = Task { await viewModel.submitName("Chapter 2") }
        #expect(await eventually { collections.heldCreates == 1 })
        await viewModel.submitName("Chapter 2")
        collections.releaseCreates()
        await first.value

        #expect(collections.createdNames == ["Chapter 2"])
        #expect(viewModel.nameSheet == nil)
    }

    @Test func aFailedDeleteShowsTheMessage() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.selectedCollection != nil })
        collections.setFailWrites(true)

        viewModel.requestDelete()
        await viewModel.confirmDelete(try #require(viewModel.pendingDelete))

        #expect(viewModel.message == .collectionsUpdateFailed)
        #expect(viewModel.collectionID == thesis.id)
    }

    // MARK: Export

    @Test func exportRunsForTheSelectedCollectionAndSharesItsFile() async throws {
        let thesis = try await thesis()
        let citations = FakeCitationRepository(export: CitationResult(bibtex: Self.bib, complete: true))
        let viewModel = makeViewModel(citations: citations)
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.selectedCollection != nil && viewModel.canExport })

        await viewModel.export()

        #expect(citations.exportCalls == [thesis.id])
        let shared = try #require(share.urls.first)
        #expect(shared.lastPathComponent == "Thesis.bib")
        #expect(try String(contentsOf: shared, encoding: .utf8) == Self.bib)
        #expect(!viewModel.exporting)
        #expect(viewModel.message == nil)
    }

    @Test func allPapersExportsTheWholeLibraryAsHashiyaLibraryBib() async throws {
        let citations = FakeCitationRepository(export: CitationResult(bibtex: Self.bib, complete: true))
        let viewModel = makeViewModel(citations: citations)
        #expect(await eventually { viewModel.papers.count == 3 })
        viewModel.updateText("nothing like this")
        viewModel.submitNow()
        #expect(await eventually { if case .noMatches = viewModel.state { true } else { false } })
        // The search and chip don't matter: the export covers the whole view.
        #expect(viewModel.canExport)

        await viewModel.export()

        #expect(citations.exportCalls == [nil])
        #expect(share.urls.map(\.lastPathComponent) == ["hashiya-library.bib"])
    }

    @Test func aSecondExportTapWhileRunningDoesNothing() async {
        let citations = FakeCitationRepository(export: CitationResult(bibtex: Self.bib, complete: true))
        citations.holdExports()
        let viewModel = makeViewModel(citations: citations)
        #expect(await eventually { viewModel.papers.count == 3 })

        let first = Task { await viewModel.export() }
        #expect(await eventually { citations.exportCalls.count == 1 })
        #expect(viewModel.exporting)
        await viewModel.export()
        citations.releaseExports()
        await first.value

        #expect(citations.exportCalls == [nil])
        #expect(share.urls.count == 1)
    }

    @Test func exportStaysBusyUntilTheShareSheetCloses() async {
        let citations = FakeCitationRepository(export: CitationResult(bibtex: Self.bib, complete: true))
        let viewModel = makeViewModel(citations: citations)
        #expect(await eventually { viewModel.papers.count == 3 })
        share.hold()

        let running = Task { await viewModel.export() }
        #expect(await eventually { share.urls.count == 1 })
        #expect(viewModel.exporting)
        await viewModel.export()
        #expect(citations.exportCalls.count == 1)

        share.release()
        await running.value
        #expect(!viewModel.exporting)
    }

    @Test func exportIncompleteShowsTheBannerAfterSharing() async {
        let citations = FakeCitationRepository(export: CitationResult(bibtex: Self.bib, complete: false))
        let viewModel = makeViewModel(citations: citations)
        #expect(await eventually { viewModel.papers.count == 3 })
        share.hold()

        let running = Task { await viewModel.export() }
        #expect(await eventually { share.urls.count == 1 })
        #expect(viewModel.message == nil)

        share.release()
        await running.value
        #expect(viewModel.message == .exportIncomplete)
    }

    @Test func aFailedExportShowsCouldntExportAndSharesNothing() async {
        let citations = FakeCitationRepository(export: CitationResult(bibtex: Self.bib, complete: true))
        citations.setFail(true)
        let viewModel = makeViewModel(citations: citations)
        #expect(await eventually { viewModel.papers.count == 3 })

        await viewModel.export()

        #expect(viewModel.message == .exportFailed)
        #expect(share.urls.isEmpty)
        #expect(!viewModel.exporting)
    }

    @Test func aFailedWriteShowsCouldntExportAndSharesNothing() async throws {
        // A plain file where the exports folder should be: creating the folder, and so the write, fails.
        let blocker = FileManager.default.temporaryDirectory.appendingPathComponent("library-blocker-\(UUID().uuidString)")
        try Data().write(to: blocker)
        let viewModel = makeViewModel(exportFiles: ExportFiles(directory: blocker))
        #expect(await eventually { viewModel.papers.count == 3 })

        await viewModel.export()

        #expect(viewModel.message == .exportFailed)
        #expect(share.urls.isEmpty)
    }
}

/// The share closure: records each file, and while held waits like an open share sheet.
@MainActor
private final class ShareRecorder {
    private(set) var urls: [URL] = []
    private var isHeld = false
    private var waiting: CheckedContinuation<Void, Never>?

    func share(_ url: URL) async {
        urls.append(url)
        guard isHeld else { return }
        await withCheckedContinuation { waiting = $0 }
    }

    func hold() {
        isHeld = true
    }

    func release() {
        isHeld = false
        waiting?.resume()
        waiting = nil
    }
}
```

In `ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryStringsTests.swift`, add inside `LibraryStringsTests`:
```swift
    /// Collection names are isolated (FSI…PDI) so an Arabic name in the English UI (or the reverse) keeps its place;
    /// Arabic formatting isolates its arguments itself.
    @Test func collectionStringsIsolateTheName() {
        #expect(inLanguage("en") { L10n.removedFromCollection("Thesis") } == "Removed from \u{2068}Thesis\u{2069}")
        #expect(inLanguage("ar") { L10n.removedFromCollection("Thesis") } == "أُزيلت من \u{2068}Thesis\u{2069}")
        #expect(inLanguage("en") { L10n.renameCollection("Thesis") } == "Rename \"\u{2068}Thesis\u{2069}\"")
        #expect(inLanguage("ar") { L10n.renameCollection("Thesis") } == "إعادة تسمية «\u{2068}Thesis\u{2069}»")
        #expect(inLanguage("en") { L10n.deleteCollection("Thesis") } == "Delete \"\u{2068}Thesis\u{2069}\"…")
        #expect(inLanguage("ar") { L10n.deleteCollection("Thesis") } == "حذف «\u{2068}Thesis\u{2069}»…")
        #expect(inLanguage("en") { L10n.deleteCollectionTitle("Thesis") } == "Delete \"\u{2068}Thesis\u{2069}\"?")
        #expect(inLanguage("ar") { L10n.deleteCollectionTitle("Thesis") } == "حذف «\u{2068}Thesis\u{2069}»؟")
    }

    @Test func theNewStringsHaveBothLanguages() {
        let english = [
            "library.allPapers": "All papers",
            "library.newCollection": "New collection",
            "library.delete": "Delete",
            "library.deleteCollectionMessage": "Its papers stay in your library.",
            "library.collectionEmpty": "No papers in this collection yet. Add papers from their details screen.",
            "library.removeFromCollection": "Remove from collection",
            "library.exportBib": "Export .bib",
            "library.exportFailed": "Couldn't export",
            "library.exportIncomplete": "Some entries may be incomplete. Export again when you're online.",
            "library.collectionsUpdateFailed": "Couldn't update collections",
        ]
        let arabic = [
            "library.allPapers": "كل الأوراق",
            "library.newCollection": "مجموعة جديدة",
            "library.delete": "حذف",
            "library.deleteCollectionMessage": "ستبقى أوراقها في مكتبتك.",
            "library.collectionEmpty": "لا توجد أوراق في هذه المجموعة بعد. أضف الأوراق من شاشة تفاصيلها.",
            "library.removeFromCollection": "إزالة من المجموعة",
            "library.exportBib": "تصدير ملف \u{200E}.bib",
            "library.exportFailed": "تعذّر التصدير",
            "library.exportIncomplete": "قد تكون بعض المداخل ناقصة. أعد التصدير عند الاتصال بالإنترنت.",
            "library.collectionsUpdateFailed": "تعذّر تحديث المجموعات",
        ]
        for (key, value) in english {
            #expect(inLanguage("en") { L10n.string(key) } == value)
        }
        for (key, value) in arabic {
            #expect(inLanguage("ar") { L10n.string(key) } == value)
        }
    }
```

- [ ] **Step 3: Run them and see them fail**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:FeatureLibraryTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: FAIL to compile with `extra arguments at positions #2, #3, #4, #5 in call`, `cannot find 'NameSheet' in scope`, `cannot find 'CollectionUndo' in scope`, `type 'L10n' has no member 'removedFromCollection'`, then `** TEST FAILED **`.

- [ ] **Step 4: Add the strings**

English and Arabic are Android's `feature/library` strings verbatim, plus the iOS-only keys from spec §11. `library.delete` is Android's `library_delete`, kept for the confirmation button (contract note 5). Android's `library_choose_collection`, `library_collection_options` and `library_rename` are not used.
```bash
python3 - <<'EOF'
import json, pathlib
path = pathlib.Path("ios/HashiyaKit/Sources/FeatureLibrary/Resources/Localizable.xcstrings")
catalog = json.loads(path.read_text(encoding="utf-8"))
for key, en, ar in [
    ("library.allPapers", "All papers", "كل الأوراق"),
    ("library.newCollection", "New collection", "مجموعة جديدة"),
    ("library.renameCollection", "Rename \"%@\"", "إعادة تسمية «%@»"),
    ("library.deleteCollection", "Delete \"%@\"…", "حذف «%@»…"),
    ("library.deleteCollectionTitle", "Delete \"%@\"?", "حذف «%@»؟"),
    ("library.deleteCollectionMessage", "Its papers stay in your library.", "ستبقى أوراقها في مكتبتك."),
    ("library.delete", "Delete", "حذف"),
    ("library.collectionEmpty", "No papers in this collection yet. Add papers from their details screen.", "لا توجد أوراق في هذه المجموعة بعد. أضف الأوراق من شاشة تفاصيلها."),
    ("library.removedFromCollection", "Removed from %@", "أُزيلت من %@"),
    ("library.removeFromCollection", "Remove from collection", "إزالة من المجموعة"),
    ("library.exportBib", "Export .bib", "تصدير ملف ‎.bib"),
    ("library.exportFailed", "Couldn't export", "تعذّر التصدير"),
    ("library.exportIncomplete", "Some entries may be incomplete. Export again when you're online.", "قد تكون بعض المداخل ناقصة. أعد التصدير عند الاتصال بالإنترنت."),
    ("library.collectionsUpdateFailed", "Couldn't update collections", "تعذّر تحديث المجموعات"),
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

In `ios/HashiyaKit/Sources/FeatureLibrary/L10n.swift`, add inside `L10n` after `rowMeta`:
```swift
    /// A collection's name inside a sentence, isolated (FSI…PDI) so an Arabic name in the English UI keeps its place.
    /// Arabic formatting isolates every argument itself, so Arabic gets the name as it is.
    static func isolated(_ name: String) -> String {
        HashiyaLanguage.isArabic ? name : "\u{2068}" + name + "\u{2069}"
    }

    /// The Undo banner after a swipe in a collection: "Removed from Thesis".
    static func removedFromCollection(_ name: String) -> String {
        format("library.removedFromCollection", isolated(name))
    }

    /// The title menu's Rename item: "Rename "Thesis"".
    static func renameCollection(_ name: String) -> String {
        format("library.renameCollection", isolated(name))
    }

    /// The title menu's Delete item: "Delete "Thesis"…".
    static func deleteCollection(_ name: String) -> String {
        format("library.deleteCollection", isolated(name))
    }

    /// The delete confirmation's title: "Delete "Thesis"?".
    static func deleteCollectionTitle(_ name: String) -> String {
        format("library.deleteCollectionTitle", isolated(name))
    }
```

- [ ] **Step 5: Implement the view model**

Replace `ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift` with:
```swift
import Foundation
import HashiyaData
import HashiyaDesignSystem
import HashiyaModel
import Observation
import os

/// What the Library screen shows.
public enum LibraryState: Equatable, Sendable {
    /// Before the first snapshot.
    case loading
    /// Nothing saved; the search field and chips are hidden.
    case empty
    /// The selected collection has no papers at all; the search field and chips are hidden.
    case emptyCollection
    /// Papers are in view, but none match the search and the chip.
    case noMatches(LibraryFilter)
    case papers([LibraryPaper], LibraryFilter)
}

/// What the search field and the status chips show.
public struct LibraryFilter: Equatable, Sendable {
    /// The search text as typed.
    public var query: String
    /// The selected chip; nil is All.
    public var status: ReadingStatus?
    /// Papers matching the applied search per status, within the selected collection; all three keys are present.
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
    case collectionsUpdateFailed
    case exportFailed
    /// After the share sheet closed: some exported entries still lack their refetched details.
    case exportIncomplete
}

/// The name sheet on screen: New collection, or Rename for `collectionID`.
public struct NameSheet: Equatable, Identifiable, Sendable {
    public var mode: CollectionNameSheet.Mode
    /// The field's text when the sheet opens.
    public var initialName: String
    /// "A collection with that name already exists", under the field.
    public var error: String?
    /// The collection being renamed; nil for New collection.
    public var collectionID: Int64?

    /// Stable while the error changes, so the sheet isn't presented again.
    public var id: String { "\(mode)-\(collectionID ?? 0)" }

    public init(mode: CollectionNameSheet.Mode, initialName: String, error: String?, collectionID: Int64?) {
        self.mode = mode
        self.initialName = initialName
        self.error = error
        self.collectionID = collectionID
    }
}

/// A paper swiped out of a collection, which Undo puts back.
public struct CollectionUndo: Equatable, Sendable {
    public var collectionID: Int64
    public var collectionName: String
    public var openAlexID: String

    public init(collectionID: Int64, collectionName: String, openAlexID: String) {
        self.collectionID = collectionID
        self.collectionName = collectionName
        self.openAlexID = openAlexID
    }
}

@Observable
@MainActor
public final class LibraryViewModel {
    /// `@SceneStorage` keys, as Android's `SavedStateHandle` keys.
    public static let queryKey = "library_query"
    public static let statusKey = "library_status"
    public static let collectionKey = "library_collection"
    /// The title menu's tag and the stored value for All papers.
    public static let allPapersTag: Int64 = -1
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
    /// The latest removal from the library, which Undo can put back.
    public internal(set) var pendingUndo: RemovedPaper?
    public var message: LibraryMessage?

    /// Every collection, sorted by name, with its paper count.
    public private(set) var collections: [PaperCollection] = []
    /// The selected collection; nil is All papers.
    public private(set) var collectionID: Int64?
    /// Papers in the whole library, whatever the collection, search and chip: the title menu's All papers count.
    public private(set) var allPapersTotal = 0
    /// Papers in the current view (the collection, or the library), ignoring the search and chip.
    public private(set) var viewTotal = 0
    /// True from an export's start until its share sheet closes.
    public private(set) var exporting = false
    public internal(set) var nameSheet: NameSheet?
    /// The collection the delete confirmation asks about.
    public var pendingDelete: PaperCollection?
    /// The latest swipe out of a collection, which Undo can put back.
    public internal(set) var pendingCollectionUndo: CollectionUndo?

    /// Tests only: called with every new state.
    @ObservationIgnored var stateObserver: ((LibraryState) -> Void)?
    @ObservationIgnored private let library: any LibraryRepository
    @ObservationIgnored private let collectionsRepository: any CollectionsRepository
    @ObservationIgnored private let citations: any CitationRepository
    @ObservationIgnored private let exportFiles: ExportFiles
    @ObservationIgnored private let share: @MainActor (URL) async -> Void
    @ObservationIgnored private let sleep: @Sendable (Duration) async throws -> Void
    @ObservationIgnored private let filterObservation = TaskSlot()
    @ObservationIgnored private let collectionsObservation = TaskSlot()
    @ObservationIgnored private var debounceTask: Task<Void, Never>?
    @ObservationIgnored private var hasRestored = false
    /// False until the first collection list arrives: before it, an unknown selection isn't treated as deleted.
    @ObservationIgnored private var collectionsLoaded = false
    /// A collection just created and selected, not yet in `collections`: the fallback leaves it alone until it appears.
    @ObservationIgnored private var awaitedCollectionID: Int64?
    @ObservationIgnored private var isSubmittingName = false

    /// - Parameter share: presents the share sheet for the exported file and returns when it closes.
    public init(
        library: any LibraryRepository,
        collections: any CollectionsRepository,
        citations: any CitationRepository,
        exportFiles: ExportFiles,
        share: @escaping @MainActor (URL) async -> Void,
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) }
    ) {
        self.library = library
        self.collectionsRepository = collections
        self.citations = citations
        self.exportFiles = exportFiles
        self.share = share
        self.sleep = sleep
        observeFilter()
        observeCollections()
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
        case .loading, .empty, .emptyCollection: nil
        }
    }

    /// The chip as its `@SceneStorage` value: `toRead`, `reading`, `read`, or "" for All.
    public var storedStatus: String { status?.rawValue ?? "" }

    /// The selected collection as its `@SceneStorage` value; -1 is All papers.
    public var storedCollection: Int { Int(collectionID ?? Self.allPapersTag) }

    /// A stored `library_collection` value as a selection: nil for All papers (any negative value).
    public static func collectionID(stored: Int) -> Int64? {
        stored < 0 ? nil : Int64(stored)
    }

    /// The selected collection, once the collection list has it.
    public var selectedCollection: PaperCollection? {
        collectionID.flatMap { id in collections.first { $0.id == id } }
    }

    /// Export .bib shows when the current view has papers, whatever the search and chip.
    public var canExport: Bool { viewTotal > 0 }

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

    /// Restores the field, chip and collection saved with the scene, once; the text applies at once. `status` is
    /// `storedStatus`'s format; anything else is All. A collection that no longer exists falls back to All papers once
    /// the collection list arrives.
    public func restore(text restoredText: String, status restoredStatus: String, collectionID restoredCollection: Int64? = nil) {
        guard !hasRestored else { return }
        hasRestored = true
        let restored = ReadingStatus(rawValue: restoredStatus)
        guard !restoredText.isEmpty || restored != nil || restoredCollection != nil else { return }
        debounceTask?.cancel()
        text = restoredText
        appliedQuery = restoredText
        status = restored
        collectionID = restoredCollection.flatMap { isSelectable($0) ? $0 : nil }
        observeFilter()
    }

    private func apply(query: String) {
        guard query != appliedQuery else { return }
        appliedQuery = query
        observeFilter()
    }

    /// Replaces the observation for the applied search, chip and collection. The current state stays until the new
    /// first snapshot.
    private func observeFilter() {
        let query = appliedQuery
        let status = status
        let collectionID = collectionID
        filterObservation.replace(with: Task { [weak self, library] in
            for await snapshot in library.observeLibrary(query: query, status: status, collectionID: collectionID) {
                guard let self, !Task.isCancelled else { return }
                self.show(snapshot, collectionID: collectionID)
            }
        })
    }

    private func show(_ snapshot: LibrarySnapshot, collectionID: Int64?) {
        allPapersTotal = snapshot.allPapersTotal
        viewTotal = snapshot.libraryTotal
        let filter = LibraryFilter(query: text, status: status, counts: snapshot.counts)
        if snapshot.allPapersTotal == 0 {
            state = .empty
        } else if collectionID != nil, snapshot.libraryTotal == 0 {
            state = .emptyCollection
        } else if snapshot.papers.isEmpty {
            state = .noMatches(filter)
        } else {
            state = .papers(snapshot.papers, filter)
        }
    }

    // MARK: Collections

    /// The title menu: shows one collection, or All papers for nil. A collection deleted meanwhile shows All papers.
    public func selectCollection(_ id: Int64?) {
        let target = id.flatMap { isSelectable($0) ? $0 : nil }
        guard target != collectionID else { return }
        collectionID = target
        observeFilter()
    }

    /// Before the collection list arrives every id is selectable; after, only the listed ones.
    private func isSelectable(_ id: Int64) -> Bool {
        !collectionsLoaded || collections.contains { $0.id == id }
    }

    private func observeCollections() {
        collectionsObservation.replace(with: Task { [weak self, collectionsRepository] in
            for await list in collectionsRepository.observeCollections() {
                guard let self, !Task.isCancelled else { return }
                self.showCollections(list)
            }
        })
    }

    private func showCollections(_ list: [PaperCollection]) {
        collections = list
        collectionsLoaded = true
        if let awaited = awaitedCollectionID, list.contains(where: { $0.id == awaited }) {
            awaitedCollectionID = nil
        }
        // Deleted from the title menu or from Details, or a restored selection that is gone: back to All papers.
        if let id = collectionID, id != awaitedCollectionID, !list.contains(where: { $0.id == id }) {
            collectionID = nil
            observeFilter()
        }
    }

    public func showNewCollection() {
        nameSheet = NameSheet(mode: .create, initialName: "", error: nil, collectionID: nil)
    }

    /// Renames the shown collection.
    public func showRename() {
        guard let collection = selectedCollection else { return }
        nameSheet = NameSheet(mode: .rename, initialName: collection.name, error: nil, collectionID: collection.id)
    }

    public func dismissNameSheet() {
        nameSheet = nil
    }

    /// The name sheet's Create or Save. A clash keeps the sheet open with its error; a new collection is then shown.
    /// While one submit runs, another does nothing.
    public func submitName(_ name: String) async {
        guard let sheet = nameSheet, !isSubmittingName else { return }
        isSubmittingName = true
        defer { isSubmittingName = false }
        // Cleared first, so the same clash again shows the error again.
        nameSheet?.error = nil
        do {
            let result: CollectionResult
            if sheet.mode == .rename, let id = sheet.collectionID {
                result = try await collectionsRepository.rename(id: id, name: name)
            } else {
                result = try await collectionsRepository.create(name: name)
            }
            switch result {
            case let .done(id):
                nameSheet = nil
                if sheet.mode == .create {
                    awaitedCollectionID = id
                    collectionID = id
                    observeFilter()
                }
            case .nameTaken:
                // Only if the sheet is still open, so a dismissal meanwhile isn't undone.
                if nameSheet != nil {
                    nameSheet?.error = DesignSystemStrings.collectionNameTaken
                }
            case .invalidName:
                // The confirm button is disabled for invalid names, so this only happens on a race: keep the sheet.
                break
            case .notFound:
                // Renaming a collection deleted meanwhile (from Details).
                nameSheet = nil
                message = .collectionsUpdateFailed
            }
        } catch {
            nameSheet = nil
            message = .collectionsUpdateFailed
        }
    }

    /// The title menu's Delete: asks for confirmation for the shown collection.
    public func requestDelete() {
        pendingDelete = selectedCollection
    }

    /// The confirmation's Delete. The papers stay in the library; the Library shows All papers.
    public func confirmDelete(_ collection: PaperCollection) async {
        pendingDelete = nil
        do {
            try await collectionsRepository.delete(id: collection.id)
            if collectionID == collection.id {
                collectionID = nil
                observeFilter()
            }
        } catch {
            message = .collectionsUpdateFailed
        }
    }

    /// A swipe in a collection: takes the paper out of that collection only, with Undo. Does nothing when no collection
    /// is shown or the shown one was just deleted, so this never removes a paper from the library.
    public func removeFromCollection(openAlexID: String) async {
        guard let collection = selectedCollection else { return }
        do {
            try await collectionsRepository.setMembership(collectionID: collection.id, openAlexID: openAlexID, member: false)
            pendingCollectionUndo = CollectionUndo(collectionID: collection.id, collectionName: collection.name, openAlexID: openAlexID)
        } catch {
            message = .collectionsUpdateFailed
        }
    }

    /// Puts the latest swiped paper back into its collection. Dropped silently when the collection is gone.
    public func undoCollectionRemoval() async {
        guard let undo = pendingCollectionUndo else { return }
        pendingCollectionUndo = nil
        // The collection was deleted meanwhile: there is nothing to put the paper back into.
        guard collections.contains(where: { $0.id == undo.collectionID }) else { return }
        do {
            try await collectionsRepository.setMembership(collectionID: undo.collectionID, openAlexID: undo.openAlexID, member: true)
        } catch {
            // Deleted before the list caught up: dropped silently. Any other failure gets the usual message.
            let current = await collectionsRepository.observeCollections().first { _ in true } ?? []
            if current.contains(where: { $0.id == undo.collectionID }) {
                message = .collectionsUpdateFailed
            }
        }
    }

    /// The collection Undo banner timed out.
    public func collectionUndoExpired() {
        pendingCollectionUndo = nil
    }

    // MARK: Export

    /// Export .bib: builds every paper in the current view (ignoring the search and chip), writes the file and opens
    /// the share sheet. Busy until the share sheet closes, so another tap does nothing. "May be incomplete" shows once
    /// the sheet has closed.
    public func export() async {
        guard !exporting else { return }
        exporting = true
        defer { exporting = false }
        let id = collectionID
        // nil for All papers; a collection whose name isn't known yet gets the file name's fallback.
        let name = id.map { id in collections.first { $0.id == id }?.name ?? "" }
        let file: URL
        let complete: Bool
        do {
            let result = try await citations.export(collectionID: id)
            file = try exportFiles.write(result.bibtex, name: ExportFiles.fileName(collectionName: name))
            complete = result.complete
        } catch {
            message = .exportFailed
            return
        }
        await share(file)
        if !complete {
            message = .exportIncomplete
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

    // MARK: Remove and Undo

    /// A swipe in All papers: removes the paper; only the latest removal can be undone.
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

    /// Puts the latest removed paper back in its place, with its status, notes, collections and cite key.
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

- [ ] **Step 6: Implement the view**

Replace `ios/HashiyaKit/Sources/FeatureLibrary/LibraryView.swift` with:
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
    private let onOpenPaper: (String) -> Void

    /// Space under the list's last row, so the Add paper button never covers it.
    static let addPaperClearance: CGFloat = 88

    @SceneStorage(LibraryViewModel.queryKey) private var storedQuery = ""
    @SceneStorage(LibraryViewModel.statusKey) private var storedStatus = ""
    @SceneStorage(LibraryViewModel.collectionKey) private var storedCollection = -1

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

    public var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(HashiyaColors.surface)
            .overlay(alignment: .bottom) {
                // The banners sit above the Add paper button (bottom trailing; bottom left in Arabic). On iOS 26
                // they are glass, grouped so they blend as they come and go.
                HashiyaGlassGroup(spacing: 12) {
                    VStack(alignment: .trailing, spacing: 0) {
                        if let messageText {
                            HashiyaBanner(text: messageText)
                        }
                        if viewModel.pendingUndo != nil {
                            HashiyaBanner(text: L10n.string("library.removed"), actionTitle: L10n.string("library.undo")) {
                                Task { await viewModel.undo() }
                            }
                        }
                        if let undo = viewModel.pendingCollectionUndo {
                            HashiyaBanner(text: L10n.removedFromCollection(undo.collectionName), actionTitle: L10n.string("library.undo")) {
                                Task { await viewModel.undoCollectionRemoval() }
                            }
                        }
                        if viewModel.isLoaded {
                            AddPaperButton(action: onAddPaper)
                                .padding(.horizontal, 16)
                                .padding(.bottom, 16)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .animation(.default, value: viewModel.pendingUndo)
            .animation(.default, value: viewModel.pendingCollectionUndo)
            .animation(.default, value: viewModel.message)
            .task(id: viewModel.pendingUndo) {
                // A newer removal cancels this task and restarts the 4 s.
                guard viewModel.pendingUndo != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                viewModel.undoExpired()
            }
            .task(id: viewModel.pendingCollectionUndo) {
                guard viewModel.pendingCollectionUndo != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                viewModel.collectionUndoExpired()
            }
            .task(id: viewModel.message) {
                // A newer message cancels this task: then it must not clear the new one.
                guard viewModel.message != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                viewModel.message = nil
            }
            .navigationTitle(Text(verbatim: title))
            .modifier(TitleMenu(viewModel: viewModel, isEnabled: viewModel.allPapersTotal > 0))
            .toolbar {
                if viewModel.canExport {
                    ToolbarItem(placement: .topBarTrailing) {
                        ExportButton(viewModel: viewModel)
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onOpenSettings) {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel(Text(verbatim: L10n.string("library.settings")))
                }
            }
            .sheet(item: $viewModel.nameSheet) { sheet in
                CollectionNameSheet(
                    mode: sheet.mode,
                    initialName: sheet.initialName,
                    error: sheet.error,
                    onSubmit: { name in Task { await viewModel.submitName(name) } },
                    onCancel: { viewModel.dismissNameSheet() }
                )
            }
            .confirmationDialog(
                Text(verbatim: viewModel.pendingDelete.map { L10n.deleteCollectionTitle($0.name) } ?? ""),
                isPresented: Binding(
                    get: { viewModel.pendingDelete != nil },
                    set: { if !$0 { viewModel.pendingDelete = nil } }
                ),
                titleVisibility: .visible,
                presenting: viewModel.pendingDelete
            ) { collection in
                // `presenting` hands the collection over, so clearing `pendingDelete` first can't lose it.
                Button(role: .destructive) {
                    Task { await viewModel.confirmDelete(collection) }
                } label: {
                    Text(verbatim: L10n.string("library.delete"))
                }
            } message: { _ in
                Text(verbatim: L10n.string("library.deleteCollectionMessage"))
            }
            .onAppear {
                viewModel.restore(
                    text: storedQuery,
                    status: storedStatus,
                    collectionID: LibraryViewModel.collectionID(stored: storedCollection)
                )
            }
            .onChange(of: viewModel.text) { _, text in storedQuery = text }
            .onChange(of: viewModel.status) { _, _ in storedStatus = viewModel.storedStatus }
            .onChange(of: viewModel.collectionID) { _, _ in storedCollection = viewModel.storedCollection }
    }

    /// "Library" while nothing is saved; otherwise the view's name: "All papers" or the collection's.
    private var title: String {
        switch viewModel.state {
        case .loading, .empty:
            L10n.string("library.title")
        case .emptyCollection, .noMatches, .papers:
            viewModel.selectedCollection?.name ?? L10n.string("library.allPapers")
        }
    }

    private var messageText: String? {
        switch viewModel.message {
        case .statusUpdateFailed: L10n.string("library.statusUpdateFailed")
        case .collectionsUpdateFailed: L10n.string("library.collectionsUpdateFailed")
        case .exportFailed: L10n.string("library.exportFailed")
        case .exportIncomplete: L10n.string("library.exportIncomplete")
        case nil: nil
        }
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
        case .emptyCollection:
            EmptyStateView(icon: "folder", title: L10n.string("library.collectionEmpty"))
        case .papers, .noMatches:
            filtered
        }
    }

    /// Papers and No papers match share this container, its search field and its chips, so moving between them
    /// never rebuilds the field and the keyboard stays up while typing.
    private var filtered: some View {
        FilteredContent(state: viewModel.state, list: list, noMatches: noMatches)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .hashiyaTopBar {
                LibraryFilterChips(
                    selected: viewModel.status,
                    counts: viewModel.filter?.counts ?? [:],
                    onSelect: { viewModel.setStatusFilter($0) }
                )
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
        // Decided when the rows are drawn: a row drawn in a collection only ever leaves that collection, even if the
        // collection is deleted before the swipe lands.
        let inCollection = viewModel.collectionID != nil
        return List {
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
                        .onTapGesture { onOpenPaper(saved.id) }
                        .accessibilityAddTraits(.isButton)
                    ReadingStatusBadge(status: saved.status) { status in
                        Task { await viewModel.setStatus(of: saved.paper, to: status) }
                    }
                    .padding(.top, 4)
                }
                // Keeps the badge's menu and the row's tap separate: tapping the badge never opens Details.
                .buttonStyle(.borderless)
                .listRowBackground(HashiyaColors.surface)
                .listRowSeparatorTint(HashiyaColors.outlineVariant)
                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                    if inCollection {
                        Button(role: .destructive) {
                            Task { await viewModel.removeFromCollection(openAlexID: saved.paper.openAlexID) }
                        } label: {
                            Label {
                                Text(verbatim: L10n.string("library.removeFromCollection"))
                            } icon: {
                                Image(systemName: "folder.badge.minus")
                            }
                        }
                    } else {
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
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .scrollDismissesKeyboard(.immediately)
        .contentMargins(.bottom, Self.addPaperClearance, for: .scrollContent)
    }
}

/// The navigation title's menu, once something is saved. Applied through a modifier, so an empty library has no menu
/// at all (an empty `toolbarTitleMenu` would still draw its chevron).
private struct TitleMenu: ViewModifier {
    let viewModel: LibraryViewModel
    let isEnabled: Bool

    func body(content: Content) -> some View {
        if isEnabled {
            content.toolbarTitleMenu {
                LibraryTitleMenu(viewModel: viewModel)
            }
        } else {
            content
        }
    }
}

/// All papers and each collection (checked when shown, with its count), New collection, and Rename and Delete for the
/// shown collection.
private struct LibraryTitleMenu: View {
    @Bindable var viewModel: LibraryViewModel

    var body: some View {
        Picker(selection: Binding(
            get: { viewModel.collectionID ?? LibraryViewModel.allPapersTag },
            set: { viewModel.selectCollection($0 == LibraryViewModel.allPapersTag ? nil : $0) }
        )) {
            row(L10n.string("library.allPapers"), count: viewModel.allPapersTotal)
                .tag(LibraryViewModel.allPapersTag)
            ForEach(viewModel.collections) { collection in
                row(collection.name, count: collection.paperCount)
                    .tag(collection.id)
            }
        } label: {
            EmptyView()
        }
        .pickerStyle(.inline)

        Button {
            viewModel.showNewCollection()
        } label: {
            Label {
                Text(verbatim: L10n.string("library.newCollection"))
            } icon: {
                Image(systemName: "plus")
            }
        }

        if let selected = viewModel.selectedCollection {
            Section {
                Button {
                    viewModel.showRename()
                } label: {
                    Label {
                        Text(verbatim: L10n.renameCollection(selected.name))
                    } icon: {
                        Image(systemName: "pencil")
                    }
                }
                Button(role: .destructive) {
                    viewModel.requestDelete()
                } label: {
                    Label {
                        Text(verbatim: L10n.deleteCollection(selected.name))
                    } icon: {
                        Image(systemName: "trash")
                    }
                }
            }
        }
    }

    /// In a menu, the second text is the item's subtitle: "3 papers".
    private func row(_ name: String, count: Int) -> some View {
        VStack {
            Text(verbatim: name)
            Text(verbatim: L10n.paperCount(count))
        }
    }
}

/// Export .bib, or a spinner from the tap until the share sheet closes.
private struct ExportButton: View {
    let viewModel: LibraryViewModel

    var body: some View {
        if viewModel.exporting {
            ProgressView()
                .accessibilityLabel(Text(verbatim: L10n.string("library.exportBib")))
        } else {
            Button {
                Task { await viewModel.export() }
            } label: {
                Image(systemName: "square.and.arrow.up")
            }
            .accessibilityLabel(Text(verbatim: L10n.string("library.exportBib")))
        }
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

/// The floating "+ Add paper" capsule: glass on iOS 26, a filled capsule with a shadow before.
private struct AddPaperButton: View {
    let action: () -> Void

    var body: some View {
        let button = Button(action: action) {
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
        .hashiyaProminentButton()
        .buttonBorderShape(.capsule)

        if #available(iOS 26, *) {
            button
        } else {
            button.shadow(color: .black.opacity(0.15), radius: 6, y: 2)
        }
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

- [ ] **Step 7: Wire the app**

In `ios/Hashiya/AppContainer.swift`, add after `let appUpdateRepository: any AppUpdateRepository`:
```swift
    let collectionsRepository: any CollectionsRepository
    let citationRepository: any CitationRepository
    /// Where Export .bib writes its file before sharing it.
    let exportFiles: ExportFiles
```
In `init(dependencies:appUpdateRepository:)`, add after `preferences = dependencies.preferences`:
```swift
        collectionsRepository = dependencies.collections
        citationRepository = dependencies.citations
        exportFiles = dependencies.exportFiles
```
Replace `makeLibraryViewModel()` with:
```swift
    func makeLibraryViewModel() -> LibraryViewModel {
        LibraryViewModel(
            library: libraryRepository,
            collections: collectionsRepository,
            citations: citationRepository,
            exportFiles: exportFiles,
            share: { await ShareSheet.present(fileURL: $0) }
        )
    }
```

In `ios/HashiyaUITests/LibraryFlowTests.swift`, `testTheLibraryShowsItsLargeTitle` now expects the view's name. Replace its two assertions with:
```swift
        XCTAssertTrue(app.navigationBars.staticTexts["All papers"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.navigationBars.staticTexts["All papers"].isHittable)
```

- [ ] **Step 8: Run the package tests and see them pass**

Run: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:FeatureLibraryTests) 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: every `FeatureLibraryTests` test passes, including the six required ones (`aDeletedCollectionFallsBackToAllPapers`, `swipeInADeletedCollectionDoesNothing`, `undoIntoADeletedCollectionIsDropped`, `exportIncompleteShowsTheBannerAfterSharing`, `aSecondExportTapWhileRunningDoesNothing`, `exportStaysBusyUntilTheShareSheetCloses`), then `** TEST SUCCEEDED **`.

- [ ] **Step 9: Update the Library snapshots**

In `ios/HashiyaSnapshotTests/LibrarySnapshotTests.swift`:
1. Add `import Foundation` and `import HashiyaData` under `@testable import FeatureLibrary`.
2. Add this helper after `library()`:
```swift
    private func viewModel(_ library: FakeLibraryRepository, collections: FakeCollectionsRepository? = nil) -> LibraryViewModel {
        LibraryViewModel(
            library: library,
            collections: collections ?? FakeCollectionsRepository(library: library),
            citations: FakeCitationRepository(),
            exportFiles: ExportFiles(directory: FileManager.default.temporaryDirectory.appendingPathComponent("library-snapshots")),
            share: { _ in },
            sleep: sleeper.sleep
        )
    }
```
3. Replace each `LibraryViewModel(library: X)` and `LibraryViewModel(library: X, sleep: sleeper.sleep)` in the existing tests with `viewModel(X)`.
4. Add these tests after `statusUpdateFailedBanner()`:
```swift
    /// "Thesis" with Attention (Reading) and the Arabic-titled paper (Read): the title shows the collection's name.
    @Test func aCollection() async throws {
        let library = library()
        let collections = FakeCollectionsRepository(library: library)
        guard case let .done(id) = try await collections.create(name: "Thesis") else { return }
        try await collections.setMembership(collectionID: id, openAlexID: SamplePapers.attention.openAlexID, member: true)
        try await collections.setMembership(collectionID: id, openAlexID: SamplePapers.arabicTitled.openAlexID, member: true)
        let viewModel = viewModel(library, collections: collections)
        viewModel.selectCollection(id)
        _ = await eventually { viewModel.papers.count == 2 && viewModel.selectedCollection != nil }
        assertHashiyaSnapshots(of: screen(viewModel), named: "collection", arabicText: "قيد القراءة")
    }

    @Test func anEmptyCollection() async throws {
        let library = library()
        let collections = FakeCollectionsRepository(library: library)
        guard case let .done(id) = try await collections.create(name: "Thesis") else { return }
        let viewModel = viewModel(library, collections: collections)
        viewModel.selectCollection(id)
        _ = await eventually { viewModel.state == .emptyCollection && viewModel.selectedCollection != nil }
        assertHashiyaSnapshots(
            of: screen(viewModel),
            named: "collectionEmpty",
            arabicText: "لا توجد أوراق في هذه المجموعة بعد. أضف الأوراق من شاشة تفاصيلها."
        )
    }

    @Test func removedFromCollectionBanner() async throws {
        let library = library()
        let collections = FakeCollectionsRepository(library: library)
        guard case let .done(id) = try await collections.create(name: "Thesis") else { return }
        try await collections.setMembership(collectionID: id, openAlexID: SamplePapers.attention.openAlexID, member: true)
        try await collections.setMembership(collectionID: id, openAlexID: SamplePapers.vit.openAlexID, member: true)
        let viewModel = viewModel(library, collections: collections)
        viewModel.selectCollection(id)
        _ = await eventually { viewModel.papers.count == 2 && viewModel.selectedCollection != nil }
        await viewModel.removeFromCollection(openAlexID: SamplePapers.vit.openAlexID)
        _ = await eventually { viewModel.papers.count == 1 }
        assertHashiyaSnapshots(of: screen(viewModel), named: "removedFromCollection", arabicText: "تراجع")
    }
```
`"تراجع"` is the existing `library.undo` Arabic text (checked in the catalog). The banner's "Removed from" text is formatted with the name, so the plain Undo lookup is the Arabic proof.

The title menu itself is a system menu and isn't snapshotted (spec §12). The name sheet's snapshot is in Task 9.

5. Delete the baselines whose screens changed (the title is now "All papers" with the menu chevron, and Export .bib shows):
```bash
for os in iOS18 iOS26; do
  dir="ios/HashiyaSnapshotTests/__Snapshots__/$os/LibrarySnapshotTests"
  git rm -q "$dir"/papersWithChipsAndBadges.papers-*.png "$dir"/aFilteredSearch.filtered-*.png \
    "$dir"/noMatches.noMatches-*.png "$dir"/undoBanner.undo-*.png "$dir"/statusUpdateFailedBanner.statusFailed-*.png
done
```
`empty.empty-*` and `statusBadges.badges-*` stay: the empty library keeps the "Library" title, no menu and no export button.

Run: `xcodegen generate --spec ios/project.yml && xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaSnapshotTests/LibrarySnapshotTests -collect-test-diagnostics never 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST|No reference'`
Expected: `empty` and `statusBadges` pass. `papersWithChipsAndBadges`, `aFilteredSearch`, `noMatches`, `undoBanner`, `statusUpdateFailedBanner`, `aCollection`, `anEmptyCollection` and `removedFromCollectionBanner` fail only with `No reference was found on disk. Automatically recorded snapshot: …`. Look at the recorded images under `/tmp` or the simulator's container, from the path in the message: the title reads "All papers" or "Thesis" with a chevron, Export .bib sits before Settings, and the Arabic images are right-to-left. Don't stage them; Task 12 records the baselines on CI.

- [ ] **Step 10: Run the whole scheme**

Run: `python3 ios/scripts/check-translations.py && xcodegen generate --spec ios/project.yml && xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -skip-testing:HashiyaUITests -collect-test-diagnostics never 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected: the app builds. The only failures are the snapshot tests listed in Step 9 and Task 9's `collectionNameSheetWithTheClash`, each with `No reference was found on disk`. Read every failure: any other one is real.

- [ ] **Step 11: Commit**

```bash
git add ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift \
  ios/HashiyaKit/Sources/FeatureLibrary/LibraryView.swift \
  ios/HashiyaKit/Sources/FeatureLibrary/L10n.swift \
  ios/HashiyaKit/Sources/FeatureLibrary/Resources/Localizable.xcstrings \
  ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryViewModelTests.swift \
  ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryCollectionsViewModelTests.swift \
  ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryStringsTests.swift \
  ios/HashiyaSnapshotTests/LibrarySnapshotTests.swift \
  ios/HashiyaUITests/LibraryFlowTests.swift \
  ios/Hashiya/AppContainer.swift
git commit -m "feat: filter the iOS Library by collection and export it as .bib"
```
The `git rm` in Step 9 already staged the deleted baselines. On a cloud session, push and read the "iOS" workflow for this commit. The expected failures are the `No reference was found on disk` ones listed in Step 10. The UI tests should pass, including `testTheLibraryShowsItsLargeTitle` with "All papers".

---

### Task 11: `FeaturePaperDetails` — the Collections row, the checklist sheet, and Copy BibTeX

**Files:**
- Modify:
  - `ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsViewModel.swift`;
  - `PaperDetailsContent.swift`;
  - `PaperDetailsScreen.swift`;
  - `Resources/Localizable.xcstrings`.
- Create:
  - `ios/HashiyaKit/Sources/FeaturePaperDetails/CollectionsRow.swift`, the row and its chip flow;
  - `CollectionsChecklist.swift`, the sheet's content;
  - `PaperDetailsBanner.swift`, the banner switch shared by the screen and the sheet.
- Test:
  - Modify `ios/HashiyaKit/Tests/FeaturePaperDetailsTests/PaperDetailsViewModelTests.swift`, `PaperDetailsContentTests.swift` and `PaperDetailsStringsTests.swift`.
  - Modify `ios/HashiyaSnapshotTests/PaperDetailsSnapshotTests.swift`.
  - Delete every image under `ios/HashiyaSnapshotTests/__Snapshots__/iOS26/PaperDetailsSnapshotTests/` and `…/iOS18/PaperDetailsSnapshotTests/`. The row changes them all; Task 12 records them again.

**Interfaces:**
- Consumes:
  - Task 1: `PaperCollection`.
  - Task 7: `CollectionsRepository`, `CollectionResult`, `FakeCollectionsRepository`, `MembershipCall`.
  - Task 8: `CitationRepository`, `CitationResult`, `FakeCitationRepository`.
  - Task 9: `CollectionNameSheet(mode:initialName:error:onSubmit:onCancel:)` and `DesignSystemStrings.collectionNameTaken`.
  - Existing: `HashiyaBanner`, `HashiyaColors`, `.hashiya(_:)` fonts, `ManualSleeper`, `eventually`, `SamplePapers`.
- Produces (module `FeaturePaperDetails`, `public`):
  - `PaperDetailsViewModel.init(openAlexID:library:pendingWrites:collections:citations:copy:sleep:)`, where `copy: @escaping @MainActor (String) -> Void`.
  - Properties `collections: [PaperCollection]`, `memberIDs: Set<Int64>`, `showingChecklist: Bool` (settable), `showingNameSheet: Bool` (settable), `nameSheetError: String?`, `creatingCollection: Bool` and `copying: Bool`.
  - Methods `toggleCollection(_:) async`, `showNewCollection()`, `submitNewCollection(_:) async`, `dismissNameSheet()` and `copyBibTeX() async`.
  - `PaperDetailsMessage` gains `collectionsUpdateFailed`, `bibtexCopied`, `bibtexIncomplete` and `copyFailed`.
  - `PaperDetailsContent.init(paper:collections:memberIDs:notes:saveState:message:actions:)`.
  - `PaperDetailsActions.showCollections` and `.copyBibTeX`.
  - `CollectionsChecklist(collections:memberIDs:message:actions:)` and `CollectionsChecklistActions` (`toggle`, `newCollection`, `done`).

**Behaviour (spec §9, Android `PaperDetailsCollectionsViewModelTest`):**
- **Observing:** `start()` also follows `observeCollections()` and `observeCollectionIDs(openAlexID:)`. They stop when the paper stops being saved or the calling task is cancelled.
- **Collections row:** below the reading status, 16 pt above it. It shows the paper's collections, in the repository's order, as chips that wrap, or "Not in any collection". The whole row is one button that opens the checklist.
- **Toggling:** a tap calls `setMembership` at once, with no optimistic change. The check marks follow the store, so a failed toggle shows the stored state, and the banner says "Couldn't update collections".
- **New collection:**
  - `.done(id)` closes the name sheet and adds the paper to the new collection.
  - `.nameTaken` keeps the sheet open with the error.
  - A thrown error closes the sheet and shows "Couldn't update collections", as Android's `aFailedNewCollectionClosesTheDialogAndSaysSo` does.
  - While a Create runs, another is ignored.
- **Copy BibTeX:** in **More options**, above **Remove from library**.
  - A complete entry → copy it and show "BibTeX copied". An incomplete one → copy it and show "Some details may be missing…". A thrown error → copy nothing and show "Couldn't copy BibTeX".
  - Nil (the paper isn't saved any more) → nothing happens.
  - A second tap while copying is ignored.
- **Banners:** while the checklist is open, its banners show inside it, and the screen behind shows none.

- [ ] **Step 1: Write the failing tests**

In `ios/HashiyaKit/Tests/FeaturePaperDetailsTests/PaperDetailsViewModelTests.swift`, replace the top of the struct, from `private let sleeper` through the end of `started(_:)`, with:
```swift
    private let sleeper = ManualSleeper()
    private let pendingWrites = PendingWrites()
    private let collections = FakeCollectionsRepository()
    private let clipboard = Clipboard()
    private let id = SamplePapers.attention.openAlexID

    /// What the view model copied, in order.
    @MainActor
    private final class Clipboard {
        var texts: [String] = []
    }

    private func makeViewModel(
        _ library: FakeLibraryRepository,
        id: String? = nil,
        citations: FakeCitationRepository = FakeCitationRepository()
    ) -> PaperDetailsViewModel {
        let clipboard = clipboard
        return PaperDetailsViewModel(
            openAlexID: id ?? self.id,
            library: library,
            pendingWrites: pendingWrites,
            collections: collections,
            citations: citations,
            copy: { clipboard.texts.append($0) },
            sleep: sleeper.sleep
        )
    }

    /// A view model for `library` that has started and loaded; the returned task is its `start()`.
    private func started(
        _ library: FakeLibraryRepository,
        citations: FakeCitationRepository = FakeCitationRepository()
    ) async -> (PaperDetailsViewModel, Task<Void, Never>) {
        let viewModel = makeViewModel(library, citations: citations)
        let task = Task { await viewModel.start() }
        _ = await eventually { viewModel.isLoaded }
        return (viewModel, task)
    }
```

Append inside the struct, after the last existing test:
```swift
    // MARK: Collections

    @Test func collectionsAndMembershipFollowTheStore() async {
        collections.setCollections([
            PaperCollection(id: 1, name: "A", paperCount: 0),
            PaperCollection(id: 2, name: "B", paperCount: 1),
        ])
        collections.setMemberships(openAlexID: id, [2])
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]))
        defer { task.cancel() }

        #expect(await eventually { viewModel.collections.map(\.name) == ["A", "B"] && viewModel.memberIDs == [2] })

        collections.setMemberships(openAlexID: id, [1, 2])
        #expect(await eventually { viewModel.memberIDs == [1, 2] })
    }

    @Test func togglingAddsAndRemovesThePaper() async {
        collections.setCollections([PaperCollection(id: 1, name: "A", paperCount: 0)])
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]))
        defer { task.cancel() }
        _ = await eventually { viewModel.collections.count == 1 }

        await viewModel.toggleCollection(1)
        #expect(await eventually { viewModel.memberIDs == [1] })
        await viewModel.toggleCollection(1)
        #expect(await eventually { viewModel.memberIDs.isEmpty })

        #expect(collections.membershipCalls == [
            MembershipCall(collectionID: 1, openAlexID: id, member: true),
            MembershipCall(collectionID: 1, openAlexID: id, member: false),
        ])
        #expect(viewModel.message == nil)
    }

    @Test func aFailedToggleKeepsTheStoredStateAndSaysSo() async throws {
        collections.setCollections([PaperCollection(id: 1, name: "A", paperCount: 0)])
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]))
        defer { task.cancel() }
        _ = await eventually { viewModel.collections.count == 1 }
        collections.setFailWrites(true)

        await viewModel.toggleCollection(1)
        try await Task.sleep(for: .milliseconds(50))

        #expect(viewModel.memberIDs.isEmpty)
        #expect(viewModel.message == .collectionsUpdateFailed)
    }

    @Test func newCollectionAddsThePaperToIt() async {
        collections.setCollections([PaperCollection(id: 1, name: "Thesis", paperCount: 0)])
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]))
        defer { task.cancel() }

        viewModel.showNewCollection()
        #expect(viewModel.showingNameSheet)
        #expect(viewModel.nameSheetError == nil)

        collections.setNextResult(.nameTaken)
        await viewModel.submitNewCollection("thesis")
        #expect(viewModel.showingNameSheet)
        #expect(viewModel.nameSheetError == DesignSystemStrings.collectionNameTaken)

        await viewModel.submitNewCollection("Chapter 2")
        #expect(!viewModel.showingNameSheet)
        #expect(viewModel.nameSheetError == nil)
        #expect(collections.createdNames == ["Chapter 2"])
        #expect(await eventually { viewModel.collections.contains { $0.name == "Chapter 2" } })
        let chapter = viewModel.collections.first { $0.name == "Chapter 2" }!.id
        #expect(await eventually { viewModel.memberIDs == [chapter] })
        #expect(collections.membershipCalls == [MembershipCall(collectionID: chapter, openAlexID: id, member: true)])
    }

    @Test func aFailedNewCollectionClosesTheSheetAndSaysSo() async {
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]))
        defer { task.cancel() }
        collections.setFailWrites(true)

        viewModel.showNewCollection()
        await viewModel.submitNewCollection("Thesis")

        #expect(!viewModel.showingNameSheet)
        #expect(viewModel.message == .collectionsUpdateFailed)
        #expect(collections.createdNames.isEmpty)
        #expect(collections.membershipCalls.isEmpty)
    }

    @Test func aDoubleCreateRunsOnce() async {
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]))
        defer { task.cancel() }
        viewModel.showNewCollection()
        collections.holdCreates()

        let first = Task { await viewModel.submitNewCollection("Thesis") }
        #expect(await eventually { viewModel.creatingCollection })
        await viewModel.submitNewCollection("Thesis")
        collections.releaseCreates()
        await first.value

        #expect(collections.createdNames == ["Thesis"])
        #expect(collections.membershipCalls.count == 1)
        #expect(!viewModel.creatingCollection)
        #expect(!viewModel.showingNameSheet)
    }

    @Test func dismissingTheNameSheetClearsItsError() async {
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]))
        defer { task.cancel() }
        viewModel.showNewCollection()
        collections.setNextResult(.nameTaken)
        await viewModel.submitNewCollection("Thesis")

        viewModel.dismissNameSheet()

        #expect(!viewModel.showingNameSheet)
        #expect(viewModel.nameSheetError == nil)
    }

    // MARK: Copy BibTeX

    @Test func copyBibTeXCopiesTheEntryAndSaysSo() async {
        let entry = "@inproceedings{vaswani2017attention,\n  title = {Attention Is All You Need}\n}\n"
        let citations = FakeCitationRepository(entry: CitationResult(bibtex: entry, complete: true))
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]), citations: citations)
        defer { task.cancel() }

        await viewModel.copyBibTeX()

        #expect(citations.entryCalls == [id])
        #expect(clipboard.texts == [entry])
        #expect(viewModel.message == .bibtexCopied)
        #expect(!viewModel.copying)
    }

    @Test func anIncompleteEntryIsStillCopied() async {
        let citations = FakeCitationRepository(entry: CitationResult(bibtex: "@misc{k,\n}\n", complete: false))
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]), citations: citations)
        defer { task.cancel() }

        await viewModel.copyBibTeX()

        #expect(clipboard.texts == ["@misc{k,\n}\n"])
        #expect(viewModel.message == .bibtexIncomplete)
    }

    @Test func aFailedCopyCopiesNothingAndSaysSo() async {
        let citations = FakeCitationRepository()
        citations.setFail(true)
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]), citations: citations)
        defer { task.cancel() }

        await viewModel.copyBibTeX()

        #expect(clipboard.texts.isEmpty)
        #expect(viewModel.message == .copyFailed)
    }

    @Test func copyForAPaperNoLongerSavedDoesNothing() async {
        let citations = FakeCitationRepository(entry: nil)
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]), citations: citations)
        defer { task.cancel() }

        await viewModel.copyBibTeX()

        #expect(clipboard.texts.isEmpty)
        #expect(viewModel.message == nil)
    }
```
Add `import HashiyaDesignSystem` to the test file's imports (for `DesignSystemStrings`).

In `PaperDetailsContentTests.swift`:
- Replace the `content(_:notes:saveState:message:)` helper with:
```swift
    private func content(
        _ paper: Paper,
        collections: [PaperCollection] = [],
        memberIDs: Set<Int64> = [],
        notes: PaperNotes? = PaperNotes(),
        saveState: NotesSaveState = .idle,
        message: PaperDetailsMessage? = nil
    ) -> PaperDetailsContent {
        PaperDetailsContent(
            paper: LibraryPaper(paper: paper, status: .toRead),
            collections: collections,
            memberIDs: memberIDs,
            notes: notes,
            saveState: saveState,
            message: message,
            actions: PaperDetailsActions()
        )
    }
```
- Append:
```swift
    @Test func theCollectionsRowSaysWhenThePaperIsInNone() {
        let none = renderedStrings(of: content(SamplePapers.vit))
        #expect(none.contains("Collections"))
        #expect(none.contains("Not in any collection"))

        let thesis = PaperCollection(id: 1, name: "Thesis", paperCount: 1)
        let notMember = renderedStrings(of: content(SamplePapers.vit, collections: [thesis]))
        #expect(notMember.contains("Not in any collection"))

        let member = renderedStrings(of: content(SamplePapers.vit, collections: [thesis], memberIDs: [1]))
        #expect(member.contains("Collections"))
        #expect(!member.contains("Not in any collection"))
    }

    @Test func eachNewMessageShowsItsBanner() {
        #expect(renderedStrings(of: content(SamplePapers.vit, message: .bibtexCopied)).contains("BibTeX copied"))
        #expect(renderedStrings(of: content(SamplePapers.vit, message: .bibtexIncomplete))
            .contains("Some details may be missing. Copy again when you're online."))
        #expect(renderedStrings(of: content(SamplePapers.vit, message: .copyFailed)).contains("Couldn't copy BibTeX"))
        #expect(renderedStrings(of: content(SamplePapers.vit, message: .collectionsUpdateFailed))
            .contains("Couldn't update collections"))
    }

    @Test func theChecklistOffersNewCollectionAndExplainsWhenEmpty() {
        let empty = renderedStrings(of: CollectionsChecklist(collections: [], memberIDs: [], message: nil, actions: CollectionsChecklistActions()))
        #expect(empty.contains("New collection"))
        #expect(empty.contains("Group papers for a chapter, a course or a project."))

        let some = renderedStrings(of: CollectionsChecklist(
            collections: [PaperCollection(id: 1, name: "Thesis", paperCount: 1)],
            memberIDs: [1],
            message: .collectionsUpdateFailed,
            actions: CollectionsChecklistActions()
        ))
        #expect(some.contains("New collection"))
        #expect(!some.contains("Group papers for a chapter, a course or a project."))
        #expect(some.contains("Couldn't update collections"))
    }
```
(`renderedStrings` wraps its view in a `NavigationStack`. `CollectionsChecklist` has its own, and a nested stack still lays out, so the helper stays as it is.)

In `PaperDetailsStringsTests.swift`, append:
```swift
    @Test func theCollectionAndBibTeXStringsResolveInBothLanguages() {
        let keys = [
            "details.collections", "details.noCollections", "details.collectionsHint", "details.newCollection",
            "details.copyBibtex", "details.bibtexCopied", "details.bibtexIncomplete",
            "details.collectionsUpdateFailed", "details.copyFailed",
        ]
        for key in keys {
            #expect(inLanguage("en") { L10n.string(key) } != key)
            #expect(inLanguage("ar") { L10n.string(key) } != inLanguage("en") { L10n.string(key) })
        }
        #expect(inLanguage("en") { L10n.string("details.copyBibtex") } == "Copy BibTeX")
        #expect(inLanguage("ar") { L10n.string("details.copyBibtex") } == "نسخ BibTeX")
        #expect(inLanguage("ar") { L10n.string("details.noCollections") } == "ليست في أي مجموعة")
    }
```

In `ios/HashiyaSnapshotTests/PaperDetailsSnapshotTests.swift`, replace `screen(_:status:notes:saveState:message:)` with:
```swift
    private func screen(
        _ paper: Paper,
        status: ReadingStatus = .toRead,
        collections: [PaperCollection] = [],
        memberIDs: Set<Int64> = [],
        notes: PaperNotes? = PaperNotes(),
        saveState: NotesSaveState = .idle,
        message: PaperDetailsMessage? = nil
    ) -> some View {
        NavigationStack {
            PaperDetailsContent(
                paper: LibraryPaper(paper: paper, status: status),
                collections: collections,
                memberIDs: memberIDs,
                notes: notes,
                saveState: saveState,
                message: message,
                actions: PaperDetailsActions()
            )
        }
    }

    /// Four collections, three holding the paper: the chips wrap, and an Arabic name sits beside English ones.
    private let sampleCollections = [
        PaperCollection(id: 1, name: "Thesis, chapter 2", paperCount: 4),
        PaperCollection(id: 2, name: "NLP reading group", paperCount: 7),
        PaperCollection(id: 3, name: "مراجعة الأدبيات", paperCount: 2),
        PaperCollection(id: 4, name: "Not this one", paperCount: 1),
    ]
```
and append these tests:
```swift
    /// The Collections row naming three collections. The "paper" state above shows the row with none.
    @Test func collectionsRow() {
        let view = screen(SamplePapers.attention, status: .reading, collections: sampleCollections, memberIDs: [1, 2, 3])
        assertHashiyaSnapshots(of: view, named: "collections", arabicText: "المجموعات")
    }

    @Test func checklist() {
        let view = CollectionsChecklist(collections: sampleCollections, memberIDs: [1, 3], message: nil, actions: CollectionsChecklistActions())
        assertHashiyaSnapshots(of: view, named: "checklist", arabicText: "مجموعة جديدة")
    }

    @Test func checklistWithoutCollections() {
        let view = CollectionsChecklist(collections: [], memberIDs: [], message: nil, actions: CollectionsChecklistActions())
        assertHashiyaSnapshots(of: view, named: "checklistEmpty", arabicText: "اجمع الأوراق لفصل أو مقرر أو مشروع.")
    }
```

Delete the old Details baselines; every one changes with the row:
```bash
git rm -q ios/HashiyaSnapshotTests/__Snapshots__/iOS26/PaperDetailsSnapshotTests/*.png \
  ios/HashiyaSnapshotTests/__Snapshots__/iOS18/PaperDetailsSnapshotTests/*.png
```

- [ ] **Step 2: Run them and see them fail**

Run: `cd ios && xcodegen generate --spec project.yml && xcodebuild test -project Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -skip-testing:HashiyaUITests -only-testing:FeaturePaperDetailsTests -collect-test-diagnostics never 2>&1 | grep -E '(^/|^xcodebuild: ).*error:|^✘|✔ Test run|\*\* TEST'`
Expected: the build fails, because `PaperDetailsViewModel` has no `collections:` parameter and there is no `CollectionsChecklist` or `PaperDetailsMessage.bibtexCopied`.

- [ ] **Step 3: Implement**

Add the strings (spec §11; the English and Arabic texts are Android's `feature/paperdetails` strings, verbatim):
```bash
python3 - <<'EOF'
import json, pathlib
path = pathlib.Path("ios/HashiyaKit/Sources/FeaturePaperDetails/Resources/Localizable.xcstrings")
catalog = json.loads(path.read_text(encoding="utf-8"))
for key, en, ar in [
    ("details.collections", "Collections", "المجموعات"),
    ("details.noCollections", "Not in any collection", "ليست في أي مجموعة"),
    ("details.collectionsHint", "Group papers for a chapter, a course or a project.", "اجمع الأوراق لفصل أو مقرر أو مشروع."),
    ("details.newCollection", "New collection", "مجموعة جديدة"),
    ("details.copyBibtex", "Copy BibTeX", "نسخ BibTeX"),
    ("details.bibtexCopied", "BibTeX copied", "تم نسخ BibTeX"),
    ("details.bibtexIncomplete", "Some details may be missing. Copy again when you're online.", "قد تنقص بعض البيانات. انسخ مرة أخرى عند الاتصال بالإنترنت."),
    ("details.collectionsUpdateFailed", "Couldn't update collections", "تعذّر تحديث المجموعات"),
    ("details.copyFailed", "Couldn't copy BibTeX", "تعذّر نسخ BibTeX"),
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

`ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsViewModel.swift`:
- Replace the `PaperDetailsMessage` enum with:
```swift
public enum PaperDetailsMessage: Equatable, Sendable {
    case notesSaveFailed, statusUpdateFailed
    case collectionsUpdateFailed
    case bibtexCopied, bibtexIncomplete, copyFailed
}
```
- Add `import HashiyaDesignSystem` below `import HashiyaData`.
- Replace the class doc comment, the stored properties through `hasStarted`, and the `init` with:
```swift
/// A saved paper, its notes and its collections. The notes are read once and then only written: no database change
/// ever replaces what is being typed. Edits save 500 ms after typing stops and on `flush()`; writes run one after
/// another and are never cancelled, and `PendingWrites` tracks each until it ends. The collections and the paper's
/// membership follow the store; a toggle never changes them ahead of it.
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
    /// Every collection, in the repository's order.
    public private(set) var collections: [PaperCollection] = []
    /// The collections this paper is in, as stored.
    public private(set) var memberIDs: Set<Int64> = []
    /// The checklist sheet.
    public var showingChecklist = false
    /// The New collection name sheet, over the checklist.
    public var showingNameSheet = false
    /// "A collection with that name already exists" under the name field, or nil.
    public private(set) var nameSheetError: String?
    /// A Create is running; another is ignored until it ends.
    public private(set) var creatingCollection = false
    /// Copy BibTeX is running; another tap is ignored until it ends.
    public private(set) var copying = false

    @ObservationIgnored private let library: any LibraryRepository
    @ObservationIgnored private let pendingWrites: PendingWrites
    @ObservationIgnored private let collectionsRepository: any CollectionsRepository
    @ObservationIgnored private let citations: any CitationRepository
    @ObservationIgnored private let copy: @MainActor (String) -> Void
    @ObservationIgnored private let sleep: @Sendable (Duration) async throws -> Void
    /// What the database holds, as far as this screen knows: the notes read, then each successful write.
    @ObservationIgnored private var savedNotes = PaperNotes()
    @ObservationIgnored private var lastWrite: Task<Bool, Never>?
    @ObservationIgnored private var debounceTask: Task<Void, Never>?
    @ObservationIgnored private var hasStarted = false

    /// Stores its dependencies only; `start()` does the work. SwiftUI may build and discard several instances.
    /// - Parameter copy: puts text on the clipboard (the app passes `UIPasteboard.general`).
    public init(
        openAlexID: String,
        library: any LibraryRepository,
        pendingWrites: PendingWrites,
        collections: any CollectionsRepository,
        citations: any CitationRepository,
        copy: @escaping @MainActor (String) -> Void,
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) }
    ) {
        self.openAlexID = openAlexID
        self.library = library
        self.pendingWrites = pendingWrites
        self.collectionsRepository = collections
        self.citations = citations
        self.copy = copy
        self.sleep = sleep
    }
```
- Replace `start()` with:
```swift
    /// Reads the notes once and follows the paper, the collections and the paper's membership until the paper stops
    /// being saved or the calling task is cancelled. Later calls do nothing.
    public func start() async {
        guard !hasStarted else { return }
        hasStarted = true
        async let notesRead: Void = loadNotes()
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.followCollections() }
            group.addTask { await self.followMembership() }
            await self.followPaper()
            group.cancelAll()
        }
        await notesRead
    }

    private func followPaper() async {
        for await paper in library.observePaper(openAlexID: openAlexID) {
            guard let paper else {
                if exit == nil { exit = .closed }
                break
            }
            self.paper = paper
        }
    }

    private func followCollections() async {
        for await collections in collectionsRepository.observeCollections() {
            self.collections = collections
        }
    }

    private func followMembership() async {
        for await ids in collectionsRepository.observeCollectionIDs(openAlexID: openAlexID) {
            memberIDs = ids
        }
    }
```
- Append before the class's closing brace:
```swift
    // MARK: Collections

    /// Adds the paper to the collection, or takes it out. The check mark follows the store, so a failure leaves the
    /// stored state on screen.
    public func toggleCollection(_ id: Int64) async {
        do {
            try await collectionsRepository.setMembership(collectionID: id, openAlexID: openAlexID, member: !memberIDs.contains(id))
        } catch {
            message = .collectionsUpdateFailed
        }
    }

    public func showNewCollection() {
        nameSheetError = nil
        showingNameSheet = true
    }

    public func dismissNameSheet() {
        showingNameSheet = false
        nameSheetError = nil
    }

    /// Creates the collection and adds the paper to it. A taken name keeps the sheet open with the error; a failure
    /// closes it and says so. A second submit while one runs is ignored.
    public func submitNewCollection(_ name: String) async {
        guard !creatingCollection else { return }
        creatingCollection = true
        defer { creatingCollection = false }
        do {
            switch try await collectionsRepository.create(name: name) {
            case .done(let id):
                dismissNameSheet()
                try await collectionsRepository.setMembership(collectionID: id, openAlexID: openAlexID, member: true)
            case .nameTaken:
                nameSheetError = DesignSystemStrings.collectionNameTaken
            case .invalidName, .notFound:
                // The sheet only submits valid names, and a create never reports a missing collection.
                break
            }
        } catch {
            dismissNameSheet()
            message = .collectionsUpdateFailed
        }
    }

    // MARK: Copy BibTeX

    /// Puts the paper's entry on the clipboard. iOS shows no confirmation of its own, so the banner always does.
    public func copyBibTeX() async {
        guard !copying else { return }
        copying = true
        defer { copying = false }
        do {
            guard let result = try await citations.entry(openAlexID: openAlexID) else { return }
            copy(result.bibtex)
            message = result.complete ? .bibtexCopied : .bibtexIncomplete
        } catch is CancellationError {
            return
        } catch {
            message = .copyFailed
        }
    }
```

Create `ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsBanner.swift`:
```swift
import HashiyaDesignSystem
import SwiftUI

/// The banner for a Details message, on the screen or inside the checklist sheet.
struct PaperDetailsBanner: View {
    let message: PaperDetailsMessage?
    var retrySave: () -> Void = {}

    var body: some View {
        switch message {
        case .notesSaveFailed:
            HashiyaBanner(
                text: L10n.string("details.notesSaveFailedMessage"),
                actionTitle: L10n.string("details.retry"),
                action: retrySave
            )
        case .statusUpdateFailed:
            HashiyaBanner(text: L10n.string("details.statusUpdateFailed"))
        case .collectionsUpdateFailed:
            HashiyaBanner(text: L10n.string("details.collectionsUpdateFailed"))
        case .bibtexCopied:
            HashiyaBanner(text: L10n.string("details.bibtexCopied"))
        case .bibtexIncomplete:
            HashiyaBanner(text: L10n.string("details.bibtexIncomplete"))
        case .copyFailed:
            HashiyaBanner(text: L10n.string("details.copyFailed"))
        case nil:
            EmptyView()
        }
    }
}
```

Create `ios/HashiyaKit/Sources/FeaturePaperDetails/CollectionsRow.swift`:
```swift
import HashiyaDesignSystem
import SwiftUI

/// "Collections" with the paper's collections as chips, or "Not in any collection". The whole row is one button
/// that opens the checklist.
struct CollectionsRow: View {
    let names: [String]
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(verbatim: L10n.string("details.collections"))
                        .font(.hashiya(.label))
                        .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    if names.isEmpty {
                        Text(verbatim: L10n.string("details.noCollections"))
                            .font(.hashiya(.body))
                            .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    } else {
                        ChipFlow(spacing: 8) {
                            ForEach(names, id: \.self) { name in
                                CollectionChip(name: name)
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.forward")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    .accessibilityHidden(true)
            }
            .padding(.vertical, 12)
            .padding(.horizontal, 14)
            .background(RoundedRectangle(cornerRadius: 12).fill(HashiyaColors.surfaceContainerHigh))
            .contentShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        // VoiceOver reads "Collections" and the names (or "Not in any collection") as one button.
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("details.collections")
    }
}

/// One collection's name. Plain: tapping it does nothing of its own; the row is the button.
private struct CollectionChip: View {
    let name: String

    var body: some View {
        Text(verbatim: name)
            .font(.hashiya(.badge))
            .lineLimit(1)
            .foregroundStyle(HashiyaColors.onSecondaryContainer)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(Capsule().fill(HashiyaColors.secondaryContainer))
    }
}

/// Lays its children out in rows, wrapping to the next row when one doesn't fit. SwiftUI mirrors a custom layout
/// right to left, so in Arabic the first chip sits on the right.
struct ChipFlow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        let rows = arrange(subviews, width: width)
        let height = rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(rows.count - 1, 0))
        let widest = rows.map(\.width).max() ?? 0
        return CGSize(width: width.isFinite ? width : widest, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(subviews, width: bounds.width) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(ProposedViewSize(width: bounds.width, height: nil))
                let width = min(size.width, bounds.width)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(width: width, height: size.height))
                x += width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func arrange(_ subviews: Subviews, width: CGFloat) -> [Row] {
        var rows: [Row] = []
        var row = Row()
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(ProposedViewSize(width: width, height: nil))
            let itemWidth = min(size.width, width)
            if !row.indices.isEmpty, row.width + spacing + itemWidth > width {
                rows.append(row)
                row = Row()
            }
            row.width += (row.indices.isEmpty ? 0 : spacing) + itemWidth
            row.height = max(row.height, size.height)
            row.indices.append(index)
        }
        if !row.indices.isEmpty { rows.append(row) }
        return rows
    }
}
```

Create `ios/HashiyaKit/Sources/FeaturePaperDetails/CollectionsChecklist.swift`:
```swift
import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// What the checklist does when the user acts; `PaperDetailsScreen` wires these to the view model.
public struct CollectionsChecklistActions {
    public var toggle: (Int64) -> Void = { _ in }
    public var newCollection: () -> Void = {}
    public var done: () -> Void = {}

    public init() {}
}

/// The checklist sheet's content: every collection with a check mark when the paper is in it, then New collection.
/// With no collections, only New collection and a line explaining what collections are for. It brings its own
/// navigation bar, and its banners show inside it, so a sheet never hides them.
public struct CollectionsChecklist: View {
    private let collections: [PaperCollection]
    private let memberIDs: Set<Int64>
    private let message: PaperDetailsMessage?
    private let actions: CollectionsChecklistActions

    public init(collections: [PaperCollection], memberIDs: Set<Int64>, message: PaperDetailsMessage?, actions: CollectionsChecklistActions) {
        self.collections = collections
        self.memberIDs = memberIDs
        self.message = message
        self.actions = actions
    }

    public var body: some View {
        NavigationStack {
            List {
                if collections.isEmpty {
                    Section {
                        newCollectionButton
                    } footer: {
                        Text(verbatim: L10n.string("details.collectionsHint"))
                            .font(.hashiya(.meta))
                            .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    }
                } else {
                    Section {
                        ForEach(collections) { collection in
                            row(collection)
                        }
                    }
                    Section {
                        newCollectionButton
                    }
                }
            }
            .navigationTitle(Text(verbatim: L10n.string("details.collections")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: actions.done) {
                        Text(verbatim: L10n.string("details.doneEditing"))
                    }
                }
            }
            .overlay(alignment: .bottom) { PaperDetailsBanner(message: message) }
            .animation(.default, value: message)
        }
    }

    private func row(_ collection: PaperCollection) -> some View {
        let isMember = memberIDs.contains(collection.id)
        return Button {
            actions.toggle(collection.id)
        } label: {
            HStack(spacing: 12) {
                Text(verbatim: collection.name)
                    .font(.hashiya(.body))
                    .foregroundStyle(HashiyaColors.onSurface)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "checkmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(HashiyaColors.primary)
                    .opacity(isMember ? 1 : 0)
                    .accessibilityHidden(true)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isMember ? .isSelected : [])
        .accessibilityIdentifier("collection.\(collection.name)")
    }

    private var newCollectionButton: some View {
        Button(action: actions.newCollection) {
            Label {
                Text(verbatim: L10n.string("details.newCollection"))
            } icon: {
                Image(systemName: "plus")
            }
            .font(.hashiya(.body))
            .foregroundStyle(HashiyaColors.primary)
        }
    }
}
```
(The check mark's `opacity` is on a plain `Image`, not on glass. The sheet's glass comes from the system sheet, so no glass modifiers here.)

`ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsContent.swift`:
- Add to `PaperDetailsActions` after `retryLoadNotes`:
```swift
    public var showCollections: () -> Void = {}
    public var copyBibTeX: () -> Void = {}
```
- Replace the stored properties and the `init` with:
```swift
    private let paper: LibraryPaper
    private let collections: [PaperCollection]
    private let memberIDs: Set<Int64>
    private let notes: PaperNotes?
    private let saveState: NotesSaveState
    private let message: PaperDetailsMessage?
    private let actions: PaperDetailsActions

    @FocusState private var focusedSection: NoteSection?

    /// - Parameters:
    ///   - collections: every collection; the row shows those in `memberIDs`, in this order.
    ///   - notes: the notes to seed the fields with; nil shows "Couldn't load your notes" and no fields.
    public init(
        paper: LibraryPaper,
        collections: [PaperCollection] = [],
        memberIDs: Set<Int64> = [],
        notes: PaperNotes?,
        saveState: NotesSaveState,
        message: PaperDetailsMessage?,
        actions: PaperDetailsActions
    ) {
        self.paper = paper
        self.collections = collections
        self.memberIDs = memberIDs
        self.notes = notes
        self.saveState = saveState
        self.message = message
        self.actions = actions
    }
```
- In `body`, after `ReadingStatusSelector(...).padding(.top, 20)`, insert:
```swift
                CollectionsRow(names: collections.filter { memberIDs.contains($0.id) }.map(\.name), action: actions.showCollections)
                    .padding(.top, 16)
```
- Replace `.overlay(alignment: .bottom) { banner }` with `.overlay(alignment: .bottom) { PaperDetailsBanner(message: message, retrySave: actions.retrySave) }`, and delete the `banner` property.
- In `moreOptions`, insert above the destructive Remove button:
```swift
            Button(action: actions.copyBibTeX) {
                Label {
                    Text(verbatim: L10n.string("details.copyBibtex"))
                } icon: {
                    Image(systemName: "doc.on.doc")
                }
            }
```

`ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsScreen.swift`:
- Replace the `PaperDetailsContent(…)` call in `content` with:
```swift
            PaperDetailsContent(
                paper: paper,
                collections: viewModel.collections,
                memberIDs: viewModel.memberIDs,
                notes: viewModel.notesLoad == .failed ? nil : viewModel.notes,
                saveState: viewModel.saveState,
                // While the checklist is open its own banner shows the message; the screen behind shows none.
                message: viewModel.showingChecklist ? nil : viewModel.message,
                actions: actions
            )
```
- In `body`, after `.task(id: viewModel.message) { … }`, add:
```swift
            .sheet(isPresented: $viewModel.showingChecklist) {
                CollectionsChecklist(
                    collections: viewModel.collections,
                    memberIDs: viewModel.memberIDs,
                    message: viewModel.message,
                    actions: checklistActions
                )
                .presentationDetents([.medium, .large])
                .sheet(isPresented: $viewModel.showingNameSheet, onDismiss: { viewModel.dismissNameSheet() }) {
                    CollectionNameSheet(
                        mode: .create,
                        error: viewModel.nameSheetError,
                        onSubmit: { name in Task { await viewModel.submitNewCollection(name) } },
                        onCancel: { viewModel.dismissNameSheet() }
                    )
                }
            }
```
- In `actions`, add before `return actions`:
```swift
        actions.showCollections = { viewModel.showingChecklist = true }
        actions.copyBibTeX = { Task { await viewModel.copyBibTeX() } }
```
- Add:
```swift
    private var checklistActions: CollectionsChecklistActions {
        var actions = CollectionsChecklistActions()
        let viewModel = viewModel
        actions.toggle = { id in Task { await viewModel.toggleCollection(id) } }
        actions.newCollection = { viewModel.showNewCollection() }
        actions.done = { viewModel.showingChecklist = false }
        return actions
    }
```

- [ ] **Step 3b: Wire Details in the app**

Task 10 already added `collectionsRepository`, `citationRepository` and `exportFiles` to `ios/Hashiya/AppContainer.swift`. Add `import UIKit` below `import HashiyaDesignSystem` (if Task 10 didn't), and replace `makePaperDetailsViewModel(openAlexID:)` with:
```swift
    func makePaperDetailsViewModel(openAlexID: String) -> PaperDetailsViewModel {
        PaperDetailsViewModel(
            openAlexID: openAlexID,
            library: libraryRepository,
            pendingWrites: pendingWrites,
            collections: collectionsRepository,
            citations: citationRepository,
            copy: { UIPasteboard.general.string = $0 }
        )
    }
```
The app builds again after this step.

- [ ] **Step 4: Run them and see them pass**

Run: the Step 2 command. Expected: PASS, including `PaperDetailsViewModelTests.aDoubleCreateRunsOnce`.
Then run `HashiyaSnapshotTests` on a Mac (`-only-testing:HashiyaSnapshotTests/PaperDetailsSnapshotTests`). Expected: every Details image fails with `No reference was found on disk` (8 states × 4 variants), because Step 1 deleted the old ones. Open the recorded `collections` and `checklist` images under the test run's attachments and check:
- the chips wrap, and start on the right in Arabic;
- the check marks sit on the trailing side;
- the empty checklist shows the hint under New collection.
Don't commit those local images.

- [ ] **Step 5: Commit, push and read CI**

```bash
git add ios/Hashiya/AppContainer.swift ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsViewModel.swift ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsContent.swift \
  ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsScreen.swift ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsBanner.swift \
  ios/HashiyaKit/Sources/FeaturePaperDetails/CollectionsRow.swift ios/HashiyaKit/Sources/FeaturePaperDetails/CollectionsChecklist.swift \
  ios/HashiyaKit/Sources/FeaturePaperDetails/Resources/Localizable.xcstrings \
  ios/HashiyaKit/Tests/FeaturePaperDetailsTests/PaperDetailsViewModelTests.swift ios/HashiyaKit/Tests/FeaturePaperDetailsTests/PaperDetailsContentTests.swift \
  ios/HashiyaKit/Tests/FeaturePaperDetailsTests/PaperDetailsStringsTests.swift ios/HashiyaSnapshotTests/PaperDetailsSnapshotTests.swift
git commit -m "feat: add collections and Copy BibTeX to iOS paper details"
git log --format='%an <%ae>' origin/main..HEAD
git push
```
(The `git rm` of the old Details baselines in Step 1 is already staged.)

Expect the only failures in the snapshot steps to be `No reference was found on disk` for new images.

---

### Task 12: UI tests, READMEs, the CI baselines, full verification, and device checks

**Files:**
- Create: `ios/HashiyaUITests/CollectionsFlowTests.swift`. (Tasks 10 and 11 already wired `AppContainer`.)
- Modify: `ios/README.md`, `README.md`.
- Add: the baselines recorded on CI (Step 6).

**Interfaces:**
- Consumes:
  - Task 8: `LiveDependencies.collections`, `.citations` and `.exportFiles`, and `UITestingStubs.dependencies()`, already built on `LibraryRepositories.shared(fileName:fresh:lookup:)` with an offline lookup.
  - Task 9: `ShareSheet.present(fileURL:)`.
  - Task 10: `LibraryViewModel(library:collections:citations:exportFiles:share:)`.
  - Task 11: `PaperDetailsViewModel(openAlexID:library:pendingWrites:collections:citations:copy:)`.
- Produces: nothing new for other tasks.

Task 8 already shares one store for the UI-test library and gives the stubs an offline lookup. Task 12 adds no repository wiring of its own.

- [ ] **Step 1: Write the failing UI tests**

`ios/HashiyaUITests/CollectionsFlowTests.swift`:
```swift
import XCTest

/// Collections and BibTeX end to end with `-ui-testing`: the UI tests' own library file and the stub search, no
/// network. Papers saved from the stub search already have their publication details, and Task 8's offline lookup never refetches.
@MainActor
final class CollectionsFlowTests: XCTestCase {
    private func launchApp() -> XCUIApplication {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-ui-testing", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        return app
    }

    private let searchField = "Search, or paste a DOI, arXiv ID or link"

    /// Saves the stub search's first two papers, Attention and BERT, and leaves the app on the Search results.
    private func saveAttentionAndBERT(in app: XCUIApplication) {
        app.tabBars.buttons["Search"].tap()
        let field = app.searchFields[searchField]
        XCTAssertTrue(field.waitForExistence(timeout: UITestTimeout.long))
        field.tap()
        field.typeText("attention\n")
        XCTAssertTrue(app.staticTexts["About 3 results"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Save"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["In library"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Save"].firstMatch.tap()
        let twoSaved = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "count == 2"),
            object: app.staticTexts.matching(NSPredicate(format: "label == %@", "In library"))
        )
        XCTAssertEqual(XCTWaiter().wait(for: [twoSaved], timeout: UITestTimeout.long), .completed)
    }

    private func row(_ titlePrefix: String, in app: XCUIApplication) -> XCUIElement {
        app.cells.containing(NSPredicate(format: "label BEGINSWITH %@", titlePrefix)).firstMatch
    }

    private func openFromTheLibrary(_ titlePrefix: String, in app: XCUIApplication) {
        app.tabBars.buttons["Library"].tap()
        let row = row(titlePrefix, in: app)
        XCTAssertTrue(row.waitForExistence(timeout: UITestTimeout.long))
        row.buttons.firstMatch.tap()
        XCTAssertTrue(app.buttons["details.collections"].waitForExistence(timeout: UITestTimeout.long))
    }

    private func back(in app: XCUIApplication) {
        app.navigationBars.buttons.element(boundBy: 0).tap()
    }

    /// The Library's title menu (`toolbarTitleMenu`): the title is a button in the navigation bar.
    private func openTitleMenu(showing title: String, in app: XCUIApplication) {
        let bar = app.navigationBars.firstMatch
        let button = bar.buttons[title]
        if button.waitForExistence(timeout: UITestTimeout.long) {
            button.tap()
        } else {
            bar.staticTexts[title].tap()
        }
    }

    /// Text that contains `text`; banners isolate names with invisible bidi marks, so exact matches can't be used.
    private func text(containing text: String, in app: XCUIApplication) -> XCUIElement {
        app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
    }

    func testACollectionMadeOnDetailsFiltersTheLibraryAndSwipeRemovesFromItOnly() {
        let app = launchApp()
        saveAttentionAndBERT(in: app)
        openFromTheLibrary("Attention Is All You Need", in: app)
        XCTAssertTrue(app.staticTexts["Not in any collection"].exists)

        // Details → Collections → New collection "Thesis": the paper is in it.
        app.buttons["details.collections"].tap()
        let new = app.buttons["New collection"]
        XCTAssertTrue(new.waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.staticTexts["Group papers for a chapter, a course or a project."].exists)
        new.tap()
        let name = app.textFields["Collection name"]
        XCTAssertTrue(name.waitForExistence(timeout: UITestTimeout.long))
        name.typeText("Thesis")
        app.buttons["Create"].tap()
        let thesis = app.buttons["collection.Thesis"]
        XCTAssertTrue(thesis.waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(thesis.isSelected)
        app.buttons["Done"].tap()
        XCTAssertTrue(app.buttons["details.collections"].staticTexts["Thesis"].waitForExistence(timeout: UITestTimeout.long))

        // The Library's title menu → Thesis: only Attention.
        back(in: app)
        XCTAssertTrue(row("BERT", in: app).waitForExistence(timeout: UITestTimeout.long))
        openTitleMenu(showing: "All papers", in: app)
        app.buttons["Thesis"].tap()
        XCTAssertTrue(row("Attention Is All You Need", in: app).waitForExistence(timeout: UITestTimeout.long))
        XCTAssertFalse(row("BERT", in: app).exists)

        // Swipe removes it from Thesis only, with Undo.
        row("Attention Is All You Need", in: app).swipeLeft()
        app.buttons["Remove from collection"].tap()
        XCTAssertTrue(text(containing: "Removed from", in: app).waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.staticTexts["No papers in this collection yet. Add papers from their details screen."]
            .waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Undo"].tap()
        XCTAssertTrue(row("Attention Is All You Need", in: app).waitForExistence(timeout: UITestTimeout.long))

        // Swipe again, no Undo: All papers still has both.
        row("Attention Is All You Need", in: app).swipeLeft()
        app.buttons["Remove from collection"].tap()
        XCTAssertTrue(app.staticTexts["No papers in this collection yet. Add papers from their details screen."]
            .waitForExistence(timeout: UITestTimeout.long))
        openTitleMenu(showing: "Thesis", in: app)
        app.buttons["All papers"].tap()
        XCTAssertTrue(row("Attention Is All You Need", in: app).waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(row("BERT", in: app).exists)
    }

    func testCopyBibTeXFromDetailsSaysItCopied() {
        let app = launchApp()
        saveAttentionAndBERT(in: app)
        openFromTheLibrary("Attention Is All You Need", in: app)

        app.buttons["More options"].tap()
        let copy = app.buttons["Copy BibTeX"]
        XCTAssertTrue(copy.waitForExistence(timeout: UITestTimeout.long))
        copy.tap()

        // The pasteboard itself isn't read: reading it from a UI test asks for paste permission.
        XCTAssertTrue(app.staticTexts["BibTeX copied"].waitForExistence(timeout: UITestTimeout.long))
    }

    func testExportStaysBusyUntilTheShareSheetCloses() {
        let app = launchApp()
        saveAttentionAndBERT(in: app)
        app.tabBars.buttons["Library"].tap()
        let export = app.buttons["Export .bib"]
        XCTAssertTrue(export.waitForExistence(timeout: UITestTimeout.long))

        export.tap()
        let saveToFiles = app.cells["Save to Files"]
        XCTAssertTrue(saveToFiles.waitForExistence(timeout: UITestTimeout.long))
        XCTAssertFalse(app.buttons["Export .bib"].exists, "Export stays busy while the share sheet is open")

        app.buttons["Close"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Export .bib"].waitForExistence(timeout: UITestTimeout.long))
    }
}
```

`project.yml` picks up every file under `ios/HashiyaUITests`, so `xcodegen generate` adds the new one with no edit.

- [ ] **Step 2: Run them**

Run: `cd ios && xcodegen generate --spec project.yml && xcodebuild test -project Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -only-testing:HashiyaUITests/CollectionsFlowTests -collect-test-diagnostics never 2>&1 | grep -E '(^/|^xcodebuild: ).*error:|^✘|Executed [0-9]+ test|\*\* TEST'`
Expected: the features are already wired (Tasks 10 and 11), so these tests are the first end-to-end check, not a red step. Each test either passes, or fails on a query. If `openTitleMenu` can't find the title, print `app.navigationBars.firstMatch.debugDescription` once, read how the title menu appears on that OS, and match it. If the share sheet's close button isn't labelled `Close` on iOS 18.5 or 26.2, match it the same way. `Save to Files` is the system's own row for a file, and must exist. A failure that isn't a query is a real bug: fix it in the feature code with a unit test first. Never skip a test.

- [ ] **Step 3: (none — no app code changes in this task)**

- [ ] **Step 4: Run everything and see it pass**

Run: `python3 ios/scripts/check-translations.py && cd ios && xcodegen generate --spec project.yml && xcodebuild test -project Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' -collect-test-diagnostics never 2>&1 | grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`
Expected:
- every unit test passes;
- `CollectionsFlowTests`' three tests pass, as do the existing `LibraryFlowTests`, `PaperDetailsFlowTests`, `ShareFlowTests` and `LaunchTests`;
- the only failures are snapshot images without baselines, each with `No reference was found on disk`. They are Tasks 9 and 10's new and re-recorded images and Task 11's `PaperDetailsSnapshotTests` (32 per OS).

If `openTitleMenu` can't find the title, print `app.navigationBars.firstMatch.debugDescription` once in the test, read how the title menu appears on that OS, and match it. Never skip the test.

If the share sheet's close button isn't labelled `Close` on iOS 18.5 or 26.2, match it the same way. `Save to Files` is the system's own row for a file, and must exist.

- [ ] **Step 5: Commit, update the READMEs, and push**

```bash
git add ios/HashiyaUITests/CollectionsFlowTests.swift
git commit -m "test: cover iOS collections, export and Copy BibTeX end to end"
```

`ios/README.md`, in the paragraph that lists the package targets:
- Replace ``(targets `HashiyaModel`, `HashiyaNetwork`, `HashiyaDatabase`, `HashiyaData`,`` with ``(targets `HashiyaModel`, `HashiyaNetwork`, `HashiyaDatabase`, `HashiyaBibTeX`, `HashiyaData`,``.
- Replace ``then `v3` for the notes)`` with ``then `v3` for the notes, then `v4` for collections and citation details)``.
- After the sentence ending `and the manifest enforces it.`, insert: ``BibTeX is generated on the device by `HashiyaBibTeX`, a port of Android's `core/bibtex` whose tests mirror Android's case for case, so both apps export identical entries and cite keys.``

`README.md`, in the iOS paragraph:
- Replace `with the features of sub-projects 1 to 4` with `with the features of sub-projects 1 to 5`.
- Replace `a details screen for each saved paper with notes that save themselves and are searchable, and Settings` with `a details screen for each saved paper with notes that save themselves and are searchable, collections that filter the Library, a `.bib` export for Overleaf and Copy BibTeX on Details, and Settings`.

```bash
git add ios/README.md README.md
git commit -m "docs: describe iOS collections and BibTeX export"
git log --format='%an <%ae>' origin/main..HEAD   # only Fady <fady.fouad.a@gmail.com>
git push
```
Read the "iOS" run for this SHA. Expected: the unit and snapshot steps fail only on images without baselines, each with `No reference was found on disk`. The UI-test step doesn't run until the baselines exist, so Step 7 reads it.

- [ ] **Step 6: Record the baselines on CI**

**On a Mac with `gh` logged in:**
```bash
bash ios/scripts/record-snapshots-on-ci.sh
```
It pushes a recording branch, waits for the "iOS snapshot baselines" workflow, unpacks the images into the working tree and deletes the branch (15–25 minutes).

**In a cloud session:**
```bash
branch="record-snapshots-ios/$(git rev-parse --short HEAD)-$(date +%s)"
git push origin "HEAD:refs/heads/$branch"
```
Wait for the "iOS snapshot baselines" run on `$branch` (`ios-record-snapshots.yml`). Expected: `success`, and its log prints `Recorded N iOS26 baseline images` and `Recorded N iOS18 baseline images`.

Download its `ios-snapshot-baselines` artifact, `unzip` it, `tar -xf ios-snapshot-baselines.tar` at the repository root, and delete the branch with `git push origin --delete "$branch"`.

If the artifact download is blocked (it was on 2026-09-30, by the network policy for `productionresultssa2.blob.core.windows.net`), delete the branch, **stop, and ask Fady** to run `bash ios/scripts/record-snapshots-on-ci.sh` on a Mac at this commit and push the images. Continue at Step 7 once they are on the branch.

- [ ] **Step 7: Check the new images, and that nothing else changed**

```bash
git status --short ios/HashiyaSnapshotTests/__Snapshots__
```
Expected, and nothing else:
- `PaperDetailsSnapshotTests/` for iOS 26 and iOS 18: 8 states × 4 variants × 2 = **64 new files**. The 40 old ones were deleted in Task 11, so the 5 old states come back with the row added. The new states are `collections`, `checklist` and `checklistEmpty`.
- The `DesignSystemSnapshotTests` files Task 9 added, and the `LibrarySnapshotTests` files Task 10 added or re-recorded, with the counts those tasks list.

If any other existing image shows as modified (Search, Settings, Share), a change altered a screen it shouldn't have. Stop and find out why; don't commit it.

Look at each new image (the Read tool shows PNGs), at least the iOS 26 ones:
- **`paper`:** the Collections row under the status selector, saying "Not in any collection", with a trailing chevron (on the left in Arabic, pointing left).
- **`collections`:** three chips. They wrap to a second row if needed, and "Not this one" is absent. The Arabic name reads right to left in the English image, and in the Arabic images the chips start on the right.
- **`checklist`:** four rows with check marks on 1 and 3 (trailing side), then **New collection**, and a **Done** button.
- **`checklistEmpty`:** only **New collection** and the hint under it.
- **Other states** (`notes`, `untitled`, `saveFailed`, `loadFailed`): as before, plus the row.
- **Dark:** readable, and the row's background isn't white.
- **Library and name-sheet images:** per Tasks 9 and 10.

```bash
git add ios/HashiyaSnapshotTests/__Snapshots__/iOS26/PaperDetailsSnapshotTests ios/HashiyaSnapshotTests/__Snapshots__/iOS18/PaperDetailsSnapshotTests \
  ios/HashiyaSnapshotTests/__Snapshots__/iOS26/LibrarySnapshotTests ios/HashiyaSnapshotTests/__Snapshots__/iOS18/LibrarySnapshotTests \
  ios/HashiyaSnapshotTests/__Snapshots__/iOS26/DesignSystemSnapshotTests ios/HashiyaSnapshotTests/__Snapshots__/iOS18/DesignSystemSnapshotTests
git status --short ios/HashiyaSnapshotTests/__Snapshots__   # nothing left unstaged
git commit -m "test: record the iOS collections and BibTeX snapshot baselines on CI"
git log --format='%an <%ae>' origin/main..HEAD
git push
```

- [ ] **Step 8: Read the full CI run**

Wait for the "iOS" run on this SHA. Expected: `success` in every step:
- Arabic translations;
- unit and snapshot tests on iOS 26 and on iOS 18;
- UI tests on both, including `CollectionsFlowTests`' three tests.

On any failure, read the job log (`gh run view --log-failed` on a Mac, or `mcp__github__get_job_logs` with `failed_only: true` in a cloud session). Find the cause, fix it with a new commit, push, and read the next run.
- A UI test that fails is never "a flake": find out why.
- A snapshot mismatch in an existing image means a screen changed. Download the `ios-snapshot-diffs` artifact, or ask Fady to look at the diff.

Say it's done only when this run is green.

- [ ] **Step 9: Device checks (Fady, on an iPhone)**

These are spec §14's acceptance criteria that CI can't show. List them in the hand-off; don't claim them:
1. Install the current `main` build, save papers with notes and statuses, then install this branch over it. Everything is still there, and the Library search still finds notes.
2. Create two collections from Details, add a paper to both, then rename one and delete the other from the Library title menu. The paper stays in the library.
3. In a collection, search and the status chips narrow the list. Swiping removes the paper from the collection only, with Undo.
4. Export a collection, upload the `.bib` to Overleaf, and cite every key. The document compiles with `plain` and `IEEEtran`, and journal, conference and arXiv papers print correctly.
5. Export the same papers from the Android app. The two files are identical, apart from keys assigned in a different order across separately built libraries.
6. Save another paper, add it to the collection, and export again. The earlier keys haven't changed.
7. Copy BibTeX from Details and paste it into Notes. It is one well-formed entry.
8. With a library saved under the `main` build, turn on airplane mode and export. The file is shared, and the "may be incomplete" banner appears after the sheet closes. Export again online, and the entries gain volume and pages.
9. In العربية, the title menu, sheets and banners are right-to-left and in Arabic, and ".bib" and "BibTeX" read correctly. Light and dark both read well, and iOS 26 shows glass bars and banners.
10. VoiceOver reads the Collections row as one button with the collection names, and the checklist rows as selected or not.

Then check the authors one last time before the PR:
```bash
git log --format='%an <%ae>' origin/main..HEAD | sort -u   # exactly: Fady <fady.fouad.a@gmail.com>
```
