# iOS sub-project 5: Collections and BibTeX export — Design

- **Date:** 2026-10-01
- **Status:** Awaiting review
- **Scope:** iOS counterpart of Android sub-project 5 (`2026-09-30-collections-and-bibtex-design.md`), matching the Android code on `feat/collections-and-bibtex` (PR #18) at `fbc1c7c`, including its review fixes. When PR #18 merges, diff its final state against this spec before implementing. Builds on the iOS specs 1–4 and the Liquid Glass work; their platform decisions, package rules, string conventions, testing and CI apply unchanged.

## 1. Context

On iOS the Library is still one list, and nothing leaves the app. This spec adds **Compare** and **Cite** as Android sub-project 5 did: saved papers can be grouped into collections, a collection (or the whole library) can be exported as a `.bib` file for LaTeX (mainly Overleaf), and one paper's BibTeX can be copied from Details.

The BibTeX output must be byte-for-byte the same as Android's for the same paper, so a thesis can mix exports from both apps.

### Decisions

| Topic | Decision |
|---|---|
| Behaviour | As Android §2 (goals and non-goals), §6 (BibTeX rules), §7 (repositories) and §11 (errors), unless §13 below says otherwise. |
| Model | `PublicationDetails` and `PaperCollection` in `HashiyaModel`, as Android's. `Paper` gains `publication`. |
| Schema | GRDB migration `"v4"`, the same columns, tables and indices as Android's `MIGRATION_3_4`. |
| BibTeX module | New package target and product `HashiyaBibTeX`, depending only on `HashiyaModel`. No regular expressions (§5.4). |
| Collection selector | `.toolbarTitleMenu` on the Library's navigation title, not a sheet. Rename and Delete act on the collection being shown. |
| Name entry | A small sheet, `CollectionNameSheet` in `HashiyaDesignSystem`, so the "name taken" error can show under the field. |
| Swipe in a collection | Removes the paper from that collection only, as on Android. |
| Export | The text is built in memory, written to `Caches/exports/`, and shared with `UIActivityViewController`. Export stays busy until the share sheet closes. |
| Copy BibTeX | `UIPasteboard`. The "BibTeX copied" banner always shows, because iOS has no system confirmation for copying. |
| Look | English and Arabic with right-to-left layouts, light and dark, Liquid Glass on iOS 26 through the existing `Glass.swift` helpers. |

## 2. Goals and non-goals

### Goals

Android's seven goals, on iOS:

1. Create, rename and delete collections, and add a saved paper to any number of them from Details.
2. The Library shows all papers or one collection, and its search and status chips work within it.
3. **Export .bib** writes every paper in the current view (ignoring search and chips) to a `.bib` file and opens the share sheet.
4. Entries use the right type and fields, and compile in Overleaf with `plain` and `IEEEtran`.
5. A cite key never changes once assigned, including across Remove → Undo.
6. **Copy BibTeX** on Details puts one entry on the clipboard.
7. Existing libraries upgrade in place: no paper, status, note or search result is lost.

### Non-goals

Android's non-goals, plus:

- A collection picker in the Share Extension or in Search's preview sheet.
- Drag and drop of papers between collections.
- An iPad or multi-window layout (the app is iPhone only).

## 3. Model (`HashiyaModel`)

```swift
/// Bibliographic details used for citations. Every field is nil when the source has none.
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
}

public struct Paper { /* …existing fields unchanged… */ public var publication: PublicationDetails }

public struct PaperCollection: Equatable, Hashable, Identifiable, Sendable {
    public let id: Int64
    public let name: String
    public let paperCount: Int
}

public let collectionNameMaxLength = 60
/// True when the trimmed name is 1–60 characters. Used by the repository and by the name sheet.
public func isValidCollectionName(_ name: String) -> Bool
```

`publication` defaults to `PublicationDetails()` in the memberwise initializer, so existing call sites and fixtures keep compiling.

Android keeps the same rule in `core/model` (`isValidCollectionName`). Kotlin counts UTF-16 units; Swift counts `Character`s, so an emoji counts as one character on iOS and two on Android. Names that pass on Android always pass on iOS.

## 4. Network and mapping

- `OpenAlexSearchClient.selectFields` gains `type,biblio`. The lookup client reuses it.
- `NetworkWork` gains `type: String?` and `biblio: NetworkBiblio?` (`volume`, `issue`, `firstPage`, `lastPage`, decoded from `first_page`/`last_page`).
- `NetworkSource` gains `type: String?` and `hostOrganizationName: String?` (`host_organization_name`).
- `NetworkWork.asPaper()` fills `publication`. The publisher is `primaryLocation.source.hostOrganizationName`. Blank strings become nil.
- `works_page.json` is replaced with Android's updated recorded response (`core/network/src/test/resources/works_page.json`), byte for byte, as the fixtures already are.

## 5. Database (`HashiyaDatabase`), migration `"v4"`

### 5.1 Migration

Raw SQL, like `v1`–`v3`, copied from Android's `MIGRATION_3_4`:

- `ALTER TABLE papers ADD COLUMN` for `work_type`, `source_type`, `publisher`, `volume`, `issue`, `first_page`, `last_page`, `cite_key` (all `TEXT`), and `details_fetched INTEGER NOT NULL DEFAULT 0`.
- `CREATE UNIQUE INDEX` on `papers(cite_key)`. SQLite allows many NULLs.
- `collections(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, name_key TEXT NOT NULL, created_at INTEGER NOT NULL)` with a unique index on `name_key`.
- `collection_papers(collection_id, paper_id, added_at)`, primary key `(collection_id, paper_id)`, foreign keys to `collections(id)` and `papers(id)` with `ON DELETE CASCADE`, and an index on `paper_id`.

Nothing existing is rewritten, and `paper_search` is untouched. `name_key` is the trimmed name lowercased with `lowercased()` (locale-independent, like Kotlin's `Locale.ROOT`).

### 5.2 Records

`PaperRecord` gains the nine columns. New `CollectionRecord` and `CollectionPaperRecord`. `DeletedPaper` gains `collectionLinks: [CollectionPaperRecord]`. A `CollectionWithCount` row type carries the count.

### 5.3 `PaperStore` changes

All operations stay one transaction each, and observations stay `AsyncStream`s, so callers never import GRDB.

- `observeLibrary(match:status:collectionID:)`. With a collection, the papers, the status counts and the total join `collection_papers`. `total` is the collection's paper count, so the empty-collection state can tell "no papers" from "no matches".
- `insert(…)` writes the publication columns and `details_fetched`.
- `deleteByOpenAlexID` also returns the paper's collection links, its cite key and its `details_fetched`.
- Restore (`insert` with a `DeletedPaper`) re-adds the links whose collection still exists, and the cite key unless another paper took it meanwhile (then it stays NULL and is reassigned on the next export). One transaction with the paper.
- Collections, as Android's `CollectionDao`: `observeCollections()` sorted by `name_key`; `observeCollectionIDs(openAlexID:)`; `insertCollection` returning nil on a name clash; `renameCollection` returning false on a clash or a missing collection; `collectionExists`; `deleteCollection`; `addToCollection` (does nothing when the paper isn't saved or is already in it); `removeFromCollection`.
- Citations, as Android's `CitationDao`: `citablePapers(collectionID:)` ordered by `saved_at, rowid`; `citablePaper(openAlexID:)`; `updatePublicationDetails(paperID:details:)` setting `details_fetched = 1`; `markDetailsFetched(paperID:)`; `assignCiteKeys([String: String])`, all or none; `allCiteKeys()`.
- Every collection write calls `notifyChanges` where an observation would otherwise miss it, as `saveNotes` does.

## 6. BibTeX (`HashiyaBibTeX`, new)

```swift
/// A saved paper ready to cite: its metadata and its stored key.
public struct CitablePaper: Equatable, Sendable { public let paper: Paper; public let citeKey: String }

public enum BibTeX {
    public static func entry(_ paper: CitablePaper) -> String
    /// Entries sorted by cite key, separated by one blank line, ending with a newline. Empty for no papers.
    public static func file(_ papers: [CitablePaper]) -> String
}

public enum CiteKeys {
    public static func base(_ paper: Paper) -> String
    public static func assign(_ papers: [Paper], taken: Set<String>) -> [String]
}
```

### 6.1 Rules

Entry types, field order, escaping, title protection and cite keys follow Android §6.1–6.4 exactly. The Swift code is a port of `core/bibtex` file by file: `BibTeX.swift`, `EntryType.swift`, `LatexText.swift`, `CiteKeys.swift`.

### 6.2 Unicode handling

Swift's `String` and `Character` differ from Kotlin's `Char`, so the port works on Unicode scalars wherever Android works on chars:

- **No regular expressions.** Android's `(?U)\s+` crashed on Android's ICU engine (fixed in `1607144`), and Swift's engines have their own differences. Whitespace runs are collapsed by hand, with `Unicode.Scalar.Properties.isWhitespace`, which matches Java's `(?U)\s` (Unicode `White_Space`). That includes NEL, the line separator, no-break space and the ideographic space.
- Splitting authors and titles into words uses the same whitespace test.
- **ASCII folding:** lowercase, apply Android's special-letter map (ß, ẞ → ss, æ → ae, ø → o, đ → d, ł → l, ı → i, œ → oe), then `decomposedStringWithCompatibilityMapping` (NFKD), then keep only scalars in `a`–`z` and `0`–`9`. Working on scalars matters: "é" is one `Character` but two scalars after NFKD.
- **Title protection** checks for an uppercase letter after the first character with `Character.isUppercase`, as Kotlin's `isUpperCase` does.
- **Escaping** walks characters, as Android does. The special characters are all ASCII, so scalars and characters agree.

### 6.3 Testing against Android

Android's `BibTeXTest`, `CiteKeysTest`, `LatexTextTest` and `EntryTypeTest` are ported case by case with the same inputs and expected strings, including the Unicode whitespace cases. A comment at the top of each test file says it mirrors the Android file, as `Fixtures.swift` does.

## 7. Repositories (`HashiyaData`)

### 7.1 `LibraryRepository`

- `observeLibrary(query:status:collectionID: Int64?)`, nil meaning All papers. `LibrarySnapshot.libraryTotal` is the total of the current view (the collection's count in a collection).
- `save` stores `publication` with `details_fetched = 1`.
- `RemovedPaper` gains `collectionIDs: Set<Int64>`, `citeKey: String?` and `detailsFetched: Bool`, and `restore` puts them back as §5.3 says.

### 7.2 `CollectionsRepository` (new)

```swift
public enum CollectionResult: Equatable, Sendable { case done(id: Int64), nameTaken, invalidName, notFound }

public protocol CollectionsRepository: Sendable {
    func observeCollections() -> AsyncStream<[PaperCollection]>
    func observeCollectionIDs(openAlexID: String) -> AsyncStream<Set<Int64>>
    func create(name: String) async throws -> CollectionResult
    func rename(id: Int64, name: String) async throws -> CollectionResult
    func delete(id: Int64) async throws
    func setMembership(collectionID: Int64, openAlexID: String, member: Bool) async throws
}
```

Names are trimmed (whitespace and newlines), and `isValidCollectionName` must hold, or the result is `.invalidName`. `rename` returns `.notFound` when the collection was deleted. `GRDBCollectionsRepository` sits beside `GRDBLibraryRepository` on the same `PaperStore`.

### 7.3 `CitationRepository` (new)

```swift
/// `complete` is false when at least one exported paper still has details_fetched = 0 after the refetch.
public struct CitationResult: Equatable, Sendable { public let bibtex: String; public let complete: Bool }

public protocol CitationRepository: Sendable {
    /// One paper's entry, refetching its details first if needed. Nil if it isn't saved.
    func entry(openAlexID: String) async throws -> CitationResult?
    /// Every paper in `collectionID` (nil = whole library), regardless of any search or status filter.
    func export(collectionID: Int64?) async throws -> CitationResult
}
```

Both follow Android's three steps:

1. **Refetch** papers with `details_fetched = 0` through `OpenAlexLookupService.work(id:)` with the stored `W…` id, at most 4 at a time in a `TaskGroup`. A paper OpenAlex no longer has (nil) is marked fetched. A failure leaves the flag at 0 and doesn't stop the export.
2. **Assign keys** to every saved paper without one, across the whole library, in `saved_at, rowid` order, with `CiteKeys.assign` against `allCiteKeys()`, in one transaction. A unique-key clash retries once with fresh keys.
3. **Read the rows again and build** with `BibTeX.entry` or `BibTeX.file`. `complete` comes from these rows, so a paper removed meanwhile drops out.

Cancellation stops the refetch and throws `CancellationError`.

### 7.4 Export files (`HashiyaData`)

```swift
public struct ExportFiles: Sendable {
    public init(directory: URL)   // live: Caches/exports
    /// Deletes earlier files in the directory, then writes `bibtex` as UTF-8 to `<name>.bib` atomically.
    public func write(_ bibtex: String, name: String) throws -> URL
    /// "hashiya-library" for nil; otherwise the name with / \ : * ? " < > | and control characters replaced by "-", trimmed, "collection" if empty.
    public static func fileName(collectionName: String?) -> String
}
```

### 7.5 Wiring

`LiveDependencies` gains `collections` and `citations`. `AppContainer`'s `makeLibraryViewModel()` and `makePaperDetailsViewModel(openAlexID:)` pass them in. `HashiyaTesting` gains `FakeCollectionsRepository` and `FakeCitationRepository`, lock-based like `FakeLibraryRepository`, with `setFail…` toggles and a way to hold an export open.

## 8. Library (`FeatureLibrary`)

### 8.1 Title menu

The navigation title is the current view's name: "All papers" or the collection's name. `.toolbarTitleMenu` holds three groups:

1. A `Picker` with **All papers** and each collection. Each item shows the name with its paper count as the subtitle (the existing paper-count plural). The current one is checked.
2. **New collection**, which opens `CollectionNameSheet`. On `.done(id)` the Library switches to the new collection.
3. Only when a collection is shown: **Rename "X"** (the name sheet, prefilled) and **Delete "X"…**, which asks with a `confirmationDialog`: "Delete "X"? Its papers stay in your library." Delete shows All papers again.

The menu is available whenever the library isn't empty.

### 8.2 State

- `LibraryViewModel` gains `collections: [PaperCollection]`, `collectionID: Int64?`, `exporting: Bool`, and the messages `collectionsUpdateFailed`, `exportFailed` and `exportIncomplete`.
- `selectCollection(_:)` restarts the one library observation with the new id, keeping the query and status.
- The selection is kept in `@SceneStorage("library_collection")` (as `Int`, -1 meaning All papers) and handed to `restore(text:status:collectionID:)` with the query and status.
- When the selected id disappears from `observeCollections()`, the selection falls back to All papers.

### 8.3 Empty and filtered states

- A collection with no papers shows "No papers in this collection yet. Add papers from their details screen." without the search field or chips (a new `LibraryState.emptyCollection`).
- A collection whose papers don't match the search or chip shows the existing `noMatches` state.

### 8.4 Swipe

- In All papers, swiping removes the paper from the library with Undo, as today.
- In a collection, the destructive swipe action is **Remove from collection**. It calls `setMembership(…, member: false)` and shows the banner "Removed from X" with Undo, which re-adds the paper.
- A swipe in a collection that was just deleted does nothing. An Undo into a collection deleted meanwhile is dropped silently.

### 8.5 Export

- **Export .bib** is a `square.and.arrow.up` toolbar button before Settings, with the accessibility label "Export .bib". It is shown when the current view has papers, ignoring search and chips.
- Tapping it sets `exporting`, which replaces the button with a `ProgressView`. Another tap does nothing.
- The view model calls `export(collectionID:)`, writes the file with `ExportFiles`, then awaits an injected `share: (URL) async -> Void`.
  - The live `share` presents `UIActivityViewController` from the key window's top view controller and returns from its `completionWithItemsHandler`.
  - `exporting` clears only after that, so Export stays busy until the share sheet closes, as on Android.
- After the sheet closes, the banner "Some entries may be incomplete. Export again when you're online." appears if `complete` is false.
- If building or writing fails, the banner "Couldn't export" appears and nothing is shared. The text is built in memory first, so no partial file is shared.

## 9. Details (`FeaturePaperDetails`)

- **Collections row:** below the reading status. It shows the paper's collections as plain capsules, or "Not in any collection". The whole row is one button that opens the checklist sheet. VoiceOver reads "Collections" and the names.
- **Checklist sheet:**
  - A list with one row per collection and a check mark when the paper is in it. Tapping a row calls `setMembership` at once.
  - The marks follow `observeCollectionIDs`. A failed toggle shows "Couldn't update collections", and the mark goes back to the stored state.
  - **New collection** opens `CollectionNameSheet`. On `.done(id)` the paper is added to the new collection. While a Create is running, a second one is ignored.
  - With no collections, the sheet shows only **New collection** and "Group papers for a chapter, a course or a project."
  - Its banners show inside the sheet, so they aren't hidden behind it.
- **Copy BibTeX:** in **More options**, above **Remove from library**.
  - It calls `CitationRepository.entry`, then an injected `copy: (String) -> Void` (live: `UIPasteboard.general.string = text`).
  - The banner "BibTeX copied" appears, or "Some details may be missing. Copy again when you're online." when `complete` is false.
  - A thrown error shows "Couldn't copy BibTeX" and copies nothing.
- `PaperDetailsViewModel` gains `collections`, `memberIDs`, `copying`, and the matching `PaperDetailsMessage` cases.

## 10. `CollectionNameSheet` (`HashiyaDesignSystem`)

```swift
public struct CollectionNameSheet: View {
    public enum Mode { case create, rename }
    public init(mode: Mode, initialName: String = "", error: String?, onSubmit: @escaping (String) -> Void, onCancel: @escaping () -> Void)
}
```

- A navigation bar with Cancel and Create/Save, a title of "New collection" or "Rename collection", and one focused `TextField` labelled "Collection name".
- The confirm button is enabled only when `isValidCollectionName` holds. Return submits.
- `error` shows under the field in red, and the sheet stays open. The caller passes "A collection with that name already exists" on `.nameTaken`, and closes the sheet on `.done`.
- Typing clears the error.
- `.presentationDetents([.height(…)])`, sized to fit, with the field's text direction following its content (`ContentDirection.of`).

The design system can't see `HashiyaData`, so it takes the clash error as text from the callers.

## 11. Strings

Android's §10 keys with iOS naming, in each module's `Localizable.xcstrings`. The English and Arabic texts are copied from Android's `strings.xml`.

| Module | Keys |
|---|---|
| `HashiyaDesignSystem` | `collection.nameLabel`, `collection.newTitle`, `collection.renameTitle`, `collection.create`, `collection.save`, `collection.cancel`, `collection.nameTaken` |
| `FeatureLibrary` | `library.allPapers`, `library.newCollection`, `library.deleteCollectionMessage`, `library.collectionEmpty`, `library.removedFromCollection`, `library.exportBib`, `library.exportFailed`, `library.exportIncomplete`, `library.collectionsUpdateFailed`, plus iOS-only `library.renameCollection` ("Rename "%@"" / "إعادة تسمية «%@»"), `library.deleteCollection` ("Delete "%@"…" / "حذف «%@»…"), `library.deleteCollectionTitle` ("Delete "%@"?" / "حذف «%@»؟"), `library.removeFromCollection` ("Remove from collection" / "إزالة من المجموعة") |
| `FeaturePaperDetails` | `details.collections`, `details.noCollections`, `details.collectionsHint`, `details.newCollection`, `details.copyBibtex`, `details.bibtexCopied`, `details.bibtexIncomplete`, `details.collectionsUpdateFailed`, `details.copyFailed` |

- Android's `library_choose_collection`, `library_collection_options`, `library_rename` and `library_delete` are not needed: the title menu has no sheet title and no per-row menus.
- "BibTeX" and ".bib" stay in Latin script. In Arabic, ".bib" is preceded by a left-to-right mark (U+200E), exactly as in Android's string, so the dot stays attached.
- Collection names inside strings are isolated with FSI/PDI, as `L10n` already does for paper titles.

## 12. Testing

Swift Testing, test-first, hand-written fakes and injected sleeps, as in specs 1–4.

| Target | Coverage |
|---|---|
| `HashiyaBibTeXTests` (new) | Android's four test files ported case by case (§6.3): every entry-type row and the rule order; field order and omitted fields; pages; publisher only on the listed types; arXiv `eprint`/`archivePrefix`; `url` only without a DOI, with braces encoded; escaping, including braces as commands and `\#MeToo` double braces; title protection; whitespace collapsing with NEL, U+2028, no-break and ideographic spaces; cite keys with accents, ß, ligatures, an Arabic-only name, "Group 7", no authors, no year, stop words, an empty title, collisions to `z`, `aa`, and `taken`; `file` sorting and separators. |
| `HashiyaModelTests` | `PublicationDetails` defaults; `Paper` still builds without `publication`; `isValidCollectionName` with Android's cases, plus an emoji counting as one. |
| `HashiyaNetworkTests` | `type`, `biblio`, source `type` and `host_organization_name` decode from the recorded response; missing fields decode as nil; the select list includes `type,biblio`. |
| `HashiyaDatabaseTests` | **Migration `v3` → `v4`** with Android's migration fixture: papers, authors, statuses, notes and the `v3` searches are unchanged; new columns are NULL with `details_fetched = 0`; `v1` → `v4` passes too. Collections: create, a clash ignoring case and surrounding spaces, rename (clash, missing), delete cascading links but not papers, counts, sorting. Library and counts filtered by collection with a query and status; `total` per view. Delete capturing links, cite key and flag; restore skipping deleted collections and a taken cite key. The unique cite-key index. `citablePapers` order with equal `saved_at`. Observations emit after collection writes. |
| `HashiyaDataTests` | `save` stores publication details with the flag at 1; citations refetch only flagged-0 papers, at most 4 at once (a counting fake lookup service); a failed refetch gives `complete == false` and keeps the flag; a nil lookup marks the paper fetched; keys are assigned once, stable across a second export and across Remove → Undo; `export` ignores filters; a paper removed during the refetch drops out; `create` and `rename` return `.invalidName` for a blank or 61-character name; `ExportFiles` file names and that earlier files are deleted. |
| `FeatureLibraryTests` | Selecting a collection filters the list and counts; the scene-restored id is used; a deleted collection falls back to All papers; the empty-collection state; swipe in a collection removes membership only, with Undo; swipe and Undo after the collection is deleted; create, rename (name taken keeps the sheet's error), delete; the export states (running, shared, incomplete, failed) with a held `share`, and a second tap while running does nothing. |
| `FeaturePaperDetailsTests` | The row follows the stored membership; a toggle and a failed toggle; New collection adds the paper, and a double Create runs once; Copy BibTeX: copied text and banner, incomplete, failure copies nothing. |
| `HashiyaDesignSystemTests` | `CollectionNameSheet` enables confirm only for a valid name. |
| Strings | Every new key resolves in English and Arabic. |
| Snapshots (`HashiyaSnapshotTests`) | English and Arabic × light and dark, on iOS 26 and iOS 18: the Library filtered by a collection (title showing its name); an empty collection; the name sheet with the clash error; the Details collections row with and without collections; the checklist sheet with and without collections. The title menu is a system menu and isn't snapshotted. |
| `HashiyaUITests` | Details → Collections → New collection "Thesis" → tick → back to the Library → title menu → "Thesis" → the paper is listed; swipe removes it from "Thesis" only, Undo brings it back; More options → Copy BibTeX shows "BibTeX copied" (the pasteboard itself isn't read in UI tests). |

## 13. Differences from Android

| Area | Android | iOS | Why |
|---|---|---|---|
| Collection selector | Bottom sheet from the title, with a ⋮ menu per row | `.toolbarTitleMenu`; Rename and Delete act on the shown collection | The native iOS pattern (Files, Photos); one tap to switch. |
| Name entry | `CollectionNameDialog` | `CollectionNameSheet` | A SwiftUI alert can't show an error under its field. |
| Selection survives | `SavedStateHandle` | `@SceneStorage` | Platform state, next to the query and status. |
| Share | `FileProvider` + `ACTION_SEND` chooser, MIME `text/x-bibtex` | `UIActivityViewController` with the file URL | No provider needed on iOS. |
| "Incomplete" after export | Queued until `ON_RESUME` | Shown when the share sheet's completion handler fires | The view model awaits the sheet. |
| "BibTeX copied" | Below API 33 only | Always | iOS shows no confirmation for copying. |
| Whitespace in BibTeX | `[\s\p{Z}\u0085]+` regex | Hand-written scalar loop | Avoids regex engine differences entirely. |
| Name length | UTF-16 units | `Character`s | Swift's natural count; never stricter than Android. |
| Unused strings | `library_choose_collection`, `library_collection_options`, `library_rename`, `library_delete` | Not used; adds `library.renameCollection`, `library.deleteCollection`, `library.removeFromCollection` | Title menu instead of a sheet. |

## 14. Acceptance criteria (on a device)

1. Install the current `main` build, save papers with notes and statuses, then install this branch over it. Everything is still there, and the Library search still finds notes.
2. Create two collections from Details, add a paper to both, then rename one and delete the other from the Library title menu. The paper stays in the library.
3. In a collection, search and the status chips narrow the list. Swiping removes the paper from the collection only, with Undo.
4. Export a collection, upload the `.bib` to Overleaf, and cite every key. The document compiles with `plain` and `IEEEtran`, and journal, conference and arXiv papers print correctly.
5. Export the same papers from the Android app. The two files are identical, apart from keys assigned in a different order across separately built libraries.
6. Save another paper, add it to the collection, and export again. The earlier keys haven't changed.
7. Copy BibTeX from Details and paste it into Notes. It is one well-formed entry.
8. With a library saved under the `main` build, turn on airplane mode and export. The file is shared, and the "may be incomplete" banner appears after the sheet closes. Export again online, and the entries gain volume and pages.
9. In العربية the title menu, sheets and banners are right-to-left and in Arabic, and ".bib" and "BibTeX" read correctly. Light and dark both read well, and iOS 26 shows glass bars and banners.
10. CI (the "iOS" workflow) is green, with the new baselines recorded by `ios-record-snapshots`.

## 15. Risks

| Risk | Mitigation |
|---|---|
| iOS output drifts from Android's | Android's tests ported case by case, and acceptance check 5. |
| Unicode handling differs between Swift and Kotlin | Scalar-based folding and whitespace (§6.2), with the tricky cases from Android's tests. |
| `.toolbarTitleMenu` isn't reachable with the large title | If the device check finds it awkward, the Library uses `.navigationBarTitleDisplayMode(.inline)`. |
| Presenting `UIActivityViewController` from SwiftUI glitches | It is presented from the top view controller in UIKit, not through a SwiftUI `.sheet`, and the UI test opens it. |
| Share targets don't recognise `.bib` | The file is plain UTF-8 text with a `.bib` name. Overleaf, Files, Mail and Drive accept it. |
| The `v4` migration breaks existing libraries | It only adds columns and tables. Migration tests from `v1` and `v3`, plus acceptance check 1. |
| PR #18 changes after this spec | Diff its merged state against this spec before the plan is executed. |
