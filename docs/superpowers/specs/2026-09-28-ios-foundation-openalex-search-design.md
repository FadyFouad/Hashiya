# iOS sub-project 1: Foundation + OpenAlex Search — Design

- **Date:** 2026-09-28
- **Status:** Awaiting review
- **Scope:** iOS counterpart of Android sub-project 1 (`2026-09-27-foundation-openalex-search-design.md`), matching the Android behaviour merged on `main` at `67e850c`. First of three iOS specs; `2026-09-28-ios-add-by-id-and-share-design.md` and `2026-09-28-ios-library-search-and-status-design.md` build on it.

## 1. Context

Hashiya helps master's and PhD students manage their research along the loop **Discover → Save → Read → Extract → Compare → Cite**. The Android app (Kotlin) has shipped sub-projects 1–3. This spec describes the iOS (Swift) app's foundation and its first feature set so that it behaves exactly like Android sub-project 1: OpenAlex keyword search with filters, a preview sheet, saving to an offline library, a basic Library list and Settings.

The iOS app is a separate native app in the same repository, under `ios/`. It says exactly what the Android app says (same English and Arabic strings, §10), sends the same OpenAlex requests (§6.1) and stores the same data (§5). Where iOS conventions differ (tabs, sheets, `.searchable`, per-app language in iOS Settings), this spec follows iOS and lists the difference in §15.

This spec is self-contained on behaviour: nothing in it requires reading the Kotlin code. Specs 2 and 3 refer to this spec for every platform decision.

### Decisions

| Topic | Decision |
|---|---|
| UI framework | SwiftUI, minimum iOS 17. `@Observable` view models on `@MainActor`, Swift concurrency (async/await; the 300 ms debounce is a cancellable `Task`), `NavigationStack`, String Catalogs. |
| Persistence | GRDB.swift over SQLite. Migrations with `DatabaseMigrator` (`v1` here, `v2` in spec 3). Changes observed with `ValueObservation`, exposed to the rest of the app as `AsyncStream`s. |
| Repository layout | Everything under `ios/` in this repository: `ios/Hashiya.xcodeproj` with the app target `Hashiya` (the Share Extension target `HashiyaShare` arrives in spec 2) and a local Swift package `ios/HashiyaKit/Package.swift`. The Xcode project is generated with XcodeGen from the committed `ios/project.yml` (`xcodegen generate --spec ios/project.yml`); `ios/Hashiya.xcodeproj` itself is git-ignored. |
| Package libraries | `HashiyaModel` (pure Swift), `HashiyaNetwork` (URLSession + Codable OpenAlex client, `NetworkFailure`, abstract rebuilder; does not see `HashiyaModel`), `HashiyaDatabase` (GRDB), `HashiyaData` (repositories, error mapping), `HashiyaDesignSystem`, `HashiyaTesting` (fakes, sample papers, `URLProtocolStub`, snapshot helpers), `FeatureSearch`, `FeatureLibrary`, `FeatureSettings`. |
| Dependency rules | As on Android: features depend only on `HashiyaData`, `HashiyaModel` and `HashiyaDesignSystem`; enforced by the package manifest (§3.2). |
| Dependency injection | Manual: an `AppContainer` in the app target builds everything; initializer injection everywhere; no DI framework. |
| OpenAlex key | Built-in key from a git-ignored `ios/Config/Secrets.xcconfig`, passed through Info.plist; missing → requests without a key. The user's override lives in the Keychain, in a keychain access group shared with the Share Extension (spec 2), and is never logged. |
| Network hygiene | Debug-only request logging with `api_key` redacted. Timeouts: 10 s connect, 20 s request (mapping in §6.5). |
| Language | iOS pattern: Settings has a **Language** row that opens the app's page in iOS Settings (`UIApplication.openSettingsURLString`); no in-app picker. English + Arabic in a `Localizable.xcstrings` per package target that has resources; a CI step fails when an Arabic translation is missing. |
| RTL | SwiftUI mirrors automatically; layouts use leading/trailing only; paper content uses its own writing direction (English titles stay left-to-right in the Arabic UI); direction-implying icons flip. |
| Visual style | "Modern academic", matching Android's screenshots: brand teal `#0B6E6E`, light/dark color sets in an asset catalog, Inter (English) and IBM Plex Sans Arabic (Arabic), both bundled and registered (OFL). |
| Navigation | `TabView` with **Library** and **Search**, each in its own `NavigationStack`; **Settings** is a sheet opened from a toolbar gear on both tabs. |
| Testing | Swift Testing (`@Test`, `#expect`, parameterised tables) with hand-written fakes; `URLProtocolStub` + JSON/XML fixtures copied from the Android test resources (no live network); GRDB in-memory databases and migration tests; pointfreeco `swift-snapshot-testing` for every screen and state × English/Arabic × light/dark, baselines recorded only on the CI macOS runner on a pinned simulator via a record workflow and script mirroring `scripts/record-screenshots-on-linux.sh`; a few XCUITests for end-to-end flows. |
| CI | GitHub Actions `ios.yml` on `macos-15`, running only when `ios/**` changes, plus the record workflow. |
| Out of scope | iPad-specific layouts beyond SwiftUI defaults; widgets. Android sub-project 4 continues on Android only. |

## 2. Goals and non-goals

### Goals

1. A user can search OpenAlex by keyword and scroll through paged results.
2. A user can sort by relevance, most cited or newest, filter by year, and restrict to open access.
3. A user can open a preview sheet for any result and save or remove the paper.
4. Saved papers show an "In library" badge in results and appear in a Library list that works offline.
5. A user can override the built-in API key, and can switch the app's language through iOS Settings.
6. The package structure, tests, snapshot baselines and CI establish the patterns specs 2 and 3 follow.

### Non-goals (this spec)

- Adding papers by DOI, arXiv ID or link, and the Share Extension (spec 2).
- Library search and reading status (spec 3).
- Recent searches, offline caching of search results, a connectivity monitor.
- A domain/use-case layer.
- iPad-specific layouts, widgets, accounts, sync.

## 3. Architecture

### 3.1 Layout under `ios/`

```
ios/
  project.yml                   XcodeGen spec: targets, settings, schemes (committed)
  Hashiya.xcodeproj             generated by `xcodegen generate`; git-ignored
  Hashiya/                      app target: HashiyaApp (@main), AppContainer, RootView, Info.plist,
                                Hashiya.entitlements, Assets.xcassets (AppIcon), Localizable.xcstrings,
                                InfoPlist.xcstrings
  HashiyaUITests/               XCUITests
  Config/
    Base.xcconfig               committed; optionally includes Secrets.xcconfig
    Secrets.example.xcconfig    committed template
    Secrets.xcconfig            git-ignored; holds OPENALEX_API_KEY
  HashiyaKit/
    Package.swift
    Sources/<Target>/…
    Tests/<Target>Tests/…
  scripts/
    check-translations.py
    record-snapshots-on-ci.sh
  README.md                     setup, including the API key
```

### 3.2 Package targets and dependency rules

`ios/HashiyaKit/Package.swift` (swift-tools-version 6.0, platforms `.iOS(.v17)`) declares one library product per target below. External dependencies: GRDB.swift (current major version, 7.x at the time of writing — verify against current docs) and pointfreeco `swift-snapshot-testing` (test and `HashiyaTesting` only).

| Target | Depends on | Contents |
|---|---|---|
| `HashiyaModel` | — | `Paper`, `Author`, `SearchQuery`, `SearchSort`, `YearFilter`, `SearchError`, `normalizeDOI` |
| `HashiyaNetwork` | — | OpenAlex DTOs, `OpenAlexSearchClient`, `UserAPIKeySource`, `NetworkFailure`, `rebuildAbstract`, request logging |
| `HashiyaDatabase` | GRDB | `HashiyaDatabase` (opening, migrator), records, `PaperStore` (the DAO) |
| `HashiyaData` | `HashiyaModel`, `HashiyaNetwork`, `HashiyaDatabase` | repository protocols and implementations, DTO/record ↔ model mapping, error mapping, Keychain key store, `LiveDependencies` factory |
| `HashiyaDesignSystem` | `HashiyaModel` | colors, fonts, formatting, shared components (`PaperCard`, `PaperPreviewContent`, `StatusBadge`, `EmptyStateView`, `ErrorStateView`, `LoadingSkeleton`, `HashiyaBanner`) |
| `FeatureSearch` | `HashiyaData`, `HashiyaModel`, `HashiyaDesignSystem` | `SearchView`, `SearchViewModel`, filter chips, year range sheet |
| `FeatureLibrary` | `HashiyaData`, `HashiyaModel`, `HashiyaDesignSystem` | `LibraryView`, `LibraryViewModel` |
| `FeatureSettings` | `HashiyaData`, `HashiyaModel`, `HashiyaDesignSystem` | `SettingsView`, `SettingsViewModel` |
| `HashiyaTesting` | `HashiyaData`, `HashiyaModel`, `HashiyaNetwork`, `HashiyaDesignSystem`, SnapshotTesting | fakes, `SamplePapers`, `URLProtocolStub`, `ManualSleeper`, snapshot helpers, fixtures |

Rules, all enforced by the manifest (a target can only import what it lists):

- Features never import `HashiyaNetwork`, `HashiyaDatabase` or GRDB, and never import each other. They see repository **protocols** from `HashiyaData`.
- `HashiyaNetwork` and `HashiyaDatabase` are leaves: they import neither each other nor `HashiyaModel`. Their types are converted by `HashiyaData`. (`HashiyaDatabase` gains `HashiyaModel` in spec 3, as Android's `core/database` did, for the migration's text normalization.)
- `HashiyaTesting` is used only by test targets; the app's `-ui-testing` stubs (§12) live in the app target under `#if DEBUG`.
- The app target imports every feature, `HashiyaData` and `HashiyaDesignSystem`.

`PaperPreviewContent` lives in `HashiyaDesignSystem` so Search and Library can both show it without depending on each other.

### 3.3 App composition

- `HashiyaApp` (`@main`) registers fonts (`HashiyaFonts.register()`), sets navigation-bar title fonts, builds one `AppContainer` and shows `RootView`.
- `AppContainer` (app target) owns the long-lived objects: the database writer, the URLSession-based clients, the Keychain store and the repositories. It builds them through `LiveDependencies` (in `HashiyaData`), so the Share Extension (spec 2) builds the same graph. It creates view models with initializer injection:
  ```swift
  @MainActor final class AppContainer {
      let libraryRepository: any LibraryRepository
      let searchRepository: any SearchRepository
      let preferences: any UserPreferencesRepository
      func makeSearchViewModel() -> SearchViewModel
      func makeLibraryViewModel() -> LibraryViewModel
      func makeSettingsViewModel() -> SettingsViewModel
  }
  ```
- View models are created once per scene and held with `@State` in `RootView`, so switching tabs keeps each tab's state.
- The database file lives in the App Group container `group.com.etatech.hashiya` from the first release (`<container>/Library/Application Support/hashiya.sqlite`), and the Keychain item uses the shared access group, so spec 2's Share Extension needs no data move. Bundle identifiers: app `com.etatech.hashiya`, extension (spec 2) `com.etatech.hashiya.share`.

### 3.4 Toolchain

- Xcode 16 or later (Swift 6 language mode, Swift Testing). The CI Xcode version is pinned in `ios.yml` (§12.2).
- XcodeGen (2.46.0 or later, `brew install xcodegen`) generates `ios/Hashiya.xcodeproj` from `ios/project.yml`. Target, scheme and build-setting changes are made in `project.yml`, never in the generated project; run `xcodegen generate --spec ios/project.yml` after pulling.
- Swift 6 strict concurrency: view models are `@MainActor`; repositories, clients and stores are `Sendable`.

## 4. Data model (`HashiyaModel`)

```swift
public struct Paper: Equatable, Hashable, Sendable, Identifiable {
    public var openAlexID: String          // "W2741809807": short form, no URL prefix
    public var doi: String?                // normalized by normalizeDOI: lowercase, no prefix
    public var title: String               // "" when OpenAlex has none; the UI shows "Untitled"
    public var authors: [Author]           // authorship order
    public var year: Int?
    public var venue: String?
    public var abstract: String?
    public var citationCount: Int
    public var isOpenAccess: Bool
    public var openAccessPDFURL: String?
    public var id: String { openAlexID }
}

public struct Author: Equatable, Hashable, Sendable { public var name: String; public var openAlexID: String? }

public struct SearchQuery: Equatable, Hashable, Sendable {
    public var text: String
    public var sort: SearchSort = .relevance
    public var years: YearFilter = .anyTime
    public var openAccessOnly: Bool = false
    public var hasActiveFilters: Bool { years != .anyTime || openAccessOnly }   // sort is not a filter
}

public enum SearchSort: String, CaseIterable, Sendable { case relevance, mostCited, newest }

public enum YearFilter: Equatable, Hashable, Sendable {
    case anyTime
    case since(Int)                        // presets 2024, 2020, 2015
    case between(from: Int, to: Int)       // from <= to; build with YearFilter.between(_:_:) which returns nil otherwise
}

public enum SearchError: Error, Equatable, Sendable { case offline, invalidUserKey, rateLimited, serviceUnavailable, unexpected }

public func normalizeDOI(_ raw: String) -> String?
```

`normalizeDOI`: trim whitespace, lowercase, remove the first matching prefix of `https://doi.org/`, `http://doi.org/`, `https://dx.doi.org/`, `http://dx.doi.org/`, `doi:` and trim again; return the value only if it starts with `10.` and contains `/`, else `nil`. Examples: `"https://doi.org/10.48550/arXiv.1706.03762"` → `"10.48550/arxiv.1706.03762"`; `"http://dx.doi.org/10.1000/XYZ"` → `"10.1000/xyz"`; `"  10.1000/xyz \n"` → `"10.1000/xyz"`; `""`, `"https://example.com/paper"`, `"10.1000"` → `nil`.

"Untitled" is a sentinel: the model keeps `""`; `HashiyaDesignSystem` shows the localized `designsystem.untitled`.

## 5. Database (`HashiyaDatabase`), migration `v1`

GRDB `DatabasePool` (WAL) at the path in §3.3; tests use an in-memory `DatabaseQueue`. Code takes `any DatabaseWriter` so both work. Foreign keys must be on (GRDB's default configuration enables them — verify against current docs). There is no destructive fallback: a failing migration must never delete the user's library.

`DatabaseMigrator` migration `"v1"` creates exactly Android's Room version 1 schema:

```sql
CREATE TABLE papers (
  id TEXT NOT NULL PRIMARY KEY,          -- UUID generated locally, lowercase
  open_alex_id TEXT,                     -- nullable: later sources may lack one
  doi TEXT,                              -- normalized
  title TEXT NOT NULL,
  year INTEGER,
  venue TEXT,
  abstract TEXT,
  citation_count INTEGER NOT NULL,
  is_open_access INTEGER NOT NULL,       -- 0/1
  oa_pdf_url TEXT,
  saved_at INTEGER NOT NULL              -- epoch milliseconds; library sort key
);
CREATE UNIQUE INDEX index_papers_open_alex_id ON papers(open_alex_id);
CREATE INDEX index_papers_doi ON papers(doi);   -- not unique: several works can share a DOI and each must be savable
CREATE TABLE paper_authors (
  paper_id TEXT NOT NULL REFERENCES papers(id) ON DELETE CASCADE,
  position INTEGER NOT NULL,             -- authorship order, from 0
  name TEXT NOT NULL,
  open_alex_author_id TEXT,
  PRIMARY KEY (paper_id, position)
);
```

Records: `PaperRecord` and `PaperAuthorRecord` (`Codable`, `FetchableRecord`, `PersistableRecord`, snake_case column names), `PaperWithAuthors { paper, authors }` with authors sorted by `position`.

`PaperStore` (the DAO), each operation one transaction:

| Operation | Behaviour |
|---|---|
| `observeSavedPapers() -> ValueObservation<…[PaperWithAuthors]>` | ordered by `saved_at DESC` |
| `observeSavedOpenAlexIDs()` | `SELECT open_alex_id FROM papers WHERE open_alex_id IS NOT NULL` |
| `insert(paper:authors:) -> Bool` | `INSERT OR IGNORE` the paper; only if a row was inserted, insert its authors. Returns `false` and writes nothing if the paper (same `open_alex_id` or `id`) already exists. |
| `deleteByOpenAlexID(_:) -> PaperWithAuthors?` | reads the paper with authors, deletes it (authors cascade), returns what was deleted, or `nil` if not saved |

## 6. Network and data flow

### 6.1 OpenAlex search request

`GET https://api.openalex.org/works`, sent by `OpenAlexSearchClient.searchWorks(_ request: WorksSearchRequest) async throws -> NetworkWorksResponse`. The client conforms to `OpenAlexSearchService`, the protocol `HashiyaData` depends on, so repository tests use a fake.

| Param | Value |
|---|---|
| `search` | the query text, trimmed (spec 2 also drops Arabic tashkeel and tatweel) |
| `filter` | comma-joined, omitted when empty: `publication_year:>{year-1}` for `.since(year)`, `publication_year:{from}-{to}` for `.between`, then `is_oa:true` when `openAccessOnly` |
| `sort` | omitted for relevance; `cited_by_count:desc` for most cited; `publication_date:desc` for newest |
| `per_page` | `25` |
| `cursor` | `*` for the first page, then the previous response's `meta.next_cursor` |
| `select` | `id,doi,display_name,publication_year,primary_location,authorships,cited_by_count,open_access,best_oa_location,abstract_inverted_index` |
| `api_key` | added by the client (§6.3), never by callers; omitted when there is no key |

Examples: `.since(2020)` → `publication_year:>2019`; `.between(2015, 2020)` → `publication_year:2015-2020`; since 2020 + open access → `publication_year:>2019,is_oa:true`; open access only → `is_oa:true`.

`WorksSearchRequest { search, filter: String?, sort: String?, cursor: String, perPage: Int }` is built in `HashiyaData` by `SearchQuery.worksSearchRequest(cursor:)`.

**Query encoding.** Build the URL with `URLComponents.percentEncodedQueryItems`, percent-encoding every character outside the RFC 3986 unreserved set (`A–Z a–z 0–9 - . _ ~`). `URLQueryItem`'s default encoding leaves `+` and `&`-adjacent cases ambiguous, so the explicit encoding is required. Test: the search text `تعلم الآلة: "deep", 100% & more` and `C++` round-trip exactly when the stub decodes the query.

### 6.2 Response mapping

DTOs (`Decodable`, tolerant): unknown keys ignored; every field except `id`, `meta` and `authorships[].author` may be missing or `null`; `cited_by_count` missing or `null` → `0`; `authorships` missing → `[]`; `open_access.is_oa` missing → `false`. A body without `meta` (e.g. `{"unexpected": true}`) is malformed.

```swift
struct NetworkWorksResponse: Decodable { let meta: NetworkMeta; let results: [NetworkWork] }   // results default []
struct NetworkMeta: Decodable { let count: Int64; let nextCursor: String? }                   // count default 0
struct NetworkWork: Decodable {
    let id: String; let doi: String?; let displayName: String?; let publicationYear: Int?
    let primaryLocation: NetworkLocation?; let authorships: [NetworkAuthorship]; let citedByCount: Int
    let openAccess: NetworkOpenAccess?; let bestOALocation: NetworkLocation?
    let abstractInvertedIndex: [String: [Int]]?
}
```

`NetworkWork.asPaper()` (in `HashiyaData`):

- `id` minus the prefix `https://openalex.org/` → `openAlexID`.
- `doi` → `normalizeDOI` (an invalid value such as `"not-a-doi"` → `nil`).
- `display_name` trimmed → `title` (`""` when missing).
- `authorships[].author`: authors whose `display_name` is missing or blank are dropped; `id` minus `https://openalex.org/` → `Author.openAlexID`.
- `primary_location.source.display_name` → `venue`; `publication_year` → `year`; `cited_by_count` → `citationCount`.
- `open_access.is_oa` → `isOpenAccess` (`false` when missing); `best_oa_location.pdf_url` → `openAccessPDFURL`.
- `abstract_inverted_index` → `rebuildAbstract` (in `HashiyaNetwork`): expand every (word, position) pair, sort by position, join with single spaces; `nil` when the index is missing, empty, or the result is blank. `{"world":[1],"Hello":[0]}` → `"Hello world"`; `{"the":[0,3],"cat":[1],"saw":[2],"dog":[4]}` → `"the cat saw the dog"`.

`meta.count` is the total shown above the list. Pagination ends when `meta.next_cursor` is `null` **or** the page's `results` is empty.

### 6.3 API key

- **Built-in key.** `ios/Config/Base.xcconfig` contains `#include? "Secrets.xcconfig"` (optional include — verify against current docs) and both targets use it. `Secrets.xcconfig` defines `OPENALEX_API_KEY = …`; the app's Info.plist has `OpenAlexAPIKey = $(OPENALEX_API_KEY)`. At launch, `LiveDependencies` reads `Bundle.main.object(forInfoDictionaryKey: "OpenAlexAPIKey")`; an empty value or the unexpanded text `$(OPENALEX_API_KEY)` means "no built-in key", and requests go out without `api_key` (OpenAlex accepts keyless requests at lower limits). `ios/Config/Secrets.xcconfig` is added to `.gitignore`; `Secrets.example.xcconfig` is committed.
- **User key.** Stored by `KeychainUserPreferencesRepository` (`HashiyaData`) as a generic password: service `com.etatech.hashiya.openalex`, account `user_api_key`, access group `$(AppIdentifierPrefix)com.etatech.hashiya.shared` (keychain-access-groups entitlement on the app, and on the extension in spec 2), accessibility after first unlock. Saving trims whitespace; a blank value deletes the item. The repository keeps the current value in memory (loaded at init), so the client reads it without touching the Keychain on every request.
- **Selection.** `HashiyaNetwork` declares `public protocol UserAPIKeySource: Sendable { var userKey: String? { get } }`, implemented by the Keychain repository. For every request the client uses the user key when it is non-blank, otherwise the built-in key, and remembers which kind it used so errors can tell `invalidUserKey` from `serviceUnavailable`.
- **Changes.** `UserPreferencesRepository.userAPIKeyUpdates() -> AsyncStream<String?>` yields the current value, then each change. Search re-runs the active search when it changes (§7.1), so fixing a rejected key in Settings takes effect at once.

### 6.4 Failure classification (`HashiyaNetwork`)

```swift
public enum NetworkFailure: Error, Equatable, Sendable {
    case connectivity
    case http(code: Int, usedUserKey: Bool)
    case malformedResponse
    case unknown
}
```

| Cause | `NetworkFailure` |
|---|---|
| Any `URLError` except `.cancelled` | `.connectivity` |
| HTTP status outside 200–299 | `.http(code:, usedUserKey:)` |
| `DecodingError` | `.malformedResponse` |
| Anything else | `.unknown` |
| Task cancelled (`CancellationError`, `URLError.cancelled`) | rethrown as `CancellationError`; never shown as an error |

`NetworkFailure` carries no underlying error: `URLError.failingURL` would contain the `api_key` query item. Its description is the case name only.

`HashiyaData` maps it to `SearchError` (`NetworkFailure.asSearchError()`):

| `NetworkFailure` | `SearchError` |
|---|---|
| `.connectivity` | `.offline` |
| `.http(401 or 403, usedUserKey: true)` | `.invalidUserKey` |
| `.http(401 or 403, usedUserKey: false)` | `.serviceUnavailable` |
| `.http(429, _)` | `.rateLimited` |
| `.http(500…599, _)` | `.serviceUnavailable` |
| any other `.http` (e.g. 400), `.malformedResponse`, `.unknown` | `.unexpected` |

### 6.5 Session, timeouts and logging

- One `URLSession` for OpenAlex with an ephemeral configuration, `waitsForConnectivity = false` (offline fails at once), `timeoutIntervalForRequest = 10` s and `timeoutIntervalForResource = 20` s. URLSession has no connect-only timeout; the 10 s idle limit covers connecting and waiting for the first byte, and the 20 s limit caps the whole request (Android's 20 s is a per-read limit; see §15).
- Request logging only in Debug builds (`#if DEBUG`), through `os.Logger` (subsystem `com.etatech.hashiya`, category `network`): method, path and query with the `api_key` value replaced by `██`, then the status code. Release builds log nothing. The key never appears in logs, errors or messages.

### 6.6 Search repository and paging

```swift
public protocol SearchRepository: Sendable {
    /// One page. `cursor` nil = first page. Throws SearchError or CancellationError.
    func searchPage(_ query: SearchQuery, cursor: String?) async throws -> SearchPage
}
public struct SearchPage: Equatable, Sendable {
    public var papers: [Paper]
    public var totalCount: Int64        // meta.count
    public var nextCursor: String?      // nil when meta.next_cursor is null or results is empty
}
```

`OpenAlexSearchRepository` maps the request and response as above. Paging state lives in `SearchViewModel` (§7.1).

### 6.7 Library repository

```swift
public protocol LibraryRepository: Sendable {
    func observeSavedPapers() -> AsyncStream<[Paper]>        // newest saved first; replaced in spec 3
    func observeSavedIDs() -> AsyncStream<Set<String>>       // OpenAlex IDs in the library
    func save(_ paper: Paper) async throws                   // already saved → no-op
    func remove(openAlexID: String) async throws -> RemovedPaper?   // nil if not saved
    func restore(_ removed: RemovedPaper) async throws       // same id and saved_at; no-op if saved again meanwhile
}
public struct RemovedPaper: Equatable, Sendable { public var paper: Paper; public var localID: String; public var savedAt: Int64 }
```

- `GRDBLibraryRepository(writer:now:newID:)`: `now` returns epoch milliseconds, `newID` a lowercase UUID string (injected for tests).
- Each `observe…` call returns a new stream backed by its own `ValueObservation` (an `AsyncStream` has one consumer). The observation starts with the current value. If the observation fails, the stream finishes; the failure is logged in Debug.
- Saving, removing and restoring change the badge and the Library through these observations; there is no UI-side "saved" state.

## 7. View models

All view models are `@Observable @MainActor final class`es in their feature targets, created by `AppContainer`. Timing is injected as `sleep: @Sendable (Duration) async throws -> Void` (default `Task.sleep(for:)`), so tests drive the debounce with `ManualSleeper` from `HashiyaTesting`.

### 7.1 `SearchViewModel`

State the view reads:

```swift
var text: String                       // exactly what is in the field
var query: SearchQuery                 // chips; query.text mirrors the submitted text
private(set) var phase: SearchPhase    // .idle, .loading, .results, .empty, .failed(SearchError)
private(set) var papers: [Paper]       // unique by openAlexID, in arrival order
private(set) var totalCount: Int64?    // nil until the first page arrives
private(set) var append: AppendState   // .idle, .loading, .failed(SearchError), .endReached
private(set) var savedIDs: Set<String>
var selectedPaper: Paper?              // preview sheet
var message: SearchMessage?            // .saveFailed, .removeFailed
```

Rules:

1. **Submitted text.** Typing starts (and restarts) a 300 ms debounce; when it elapses the text becomes the submitted text. The keyboard's Search key (`.onSubmit(of: .search)`) submits at once. A suggestion chip sets the text and submits at once. Text that becomes blank submits `""` at once.
2. **Active query.** The submitted text trimmed; blank → `phase = .idle`, no request. Otherwise the active query is `query` with that text. Chip changes apply at once to the active query. A new active query equal to the current one does nothing.
3. **First page.** A new active query cancels the previous search task, clears `papers`, `totalCount` and the seen IDs, sets `phase = .loading`, and requests the first page. Success: `totalCount = page.totalCount`, papers appended (see 5), `phase = .results`, or `.empty` if the page has no papers. Failure: `phase = .failed(error)`. Cancellation changes nothing.
4. **Next pages.** When the last row appears and `append == .idle` and a next cursor exists, `append = .loading` and the next page loads. Success appends; no next cursor → `.endReached`. Failure → `append = .failed(error)`, keeping loaded results; the footer's Retry requests the same cursor again.
5. **Duplicates.** OpenAlex can return a work on two pages; a work whose `openAlexID` was already shown for this query is dropped, so list identity stays unique.
6. **Retry** on the first-page error re-runs the first page.
7. **API key change.** Each value after the first from `userAPIKeyUpdates()` re-runs the active query's first page (nothing happens while idle).
8. **Saved IDs.** `observeSavedIDs()` feeds `savedIDs`; each card and the sheet derive "In library" as `savedIDs.contains(paper.openAlexID)`. Library state is never copied into `papers`.
9. **Save/remove.** `toggleSave(paper)` calls `remove` when the paper is in the library, else `save`. A thrown error sets `message = .saveFailed` or `.removeFailed`; the view shows it in a `HashiyaBanner` for 4 s and then clears it.
10. **Clear filters** resets years to `.anyTime` and `openAccessOnly` to `false`; the sort is kept.
11. **Restoration.** `SearchView` stores `text` and the chips with `@SceneStorage` under the Android keys `search_text`, `search_sort` (`relevance`, `mostCited`, `newest`), `search_year_kind` (`since`, `between`, absent = any time), `search_year_from`, `search_year_to`, `search_oa`. On first appearance it calls `restore(text:query:)`: restored text is submitted at once (no debounce). Missing or invalid values fall back to defaults (a `between` with from > to → any time).

### 7.2 `LibraryViewModel` (this spec's state; spec 3 extends it)

- `papers: [Paper]` from `observeSavedPapers()`; `isLoaded` false until the first value (the view shows `LoadingSkeleton` meanwhile).
- `selectedPaperID: String?`; the sheet shows the library's current copy of that paper and closes itself if the paper disappears.
- `remove(paper)`: closes the preview, calls `remove`, and sets `pendingUndo` to the returned `RemovedPaper`. A second removal replaces the first: only the latest can be undone.
- `undo()` restores `pendingUndo` and clears it; `undoExpired()` clears it without restoring.
- A failed remove or restore changes nothing on screen and is logged in Debug (the Android app shows no message for it either).

### 7.3 `SettingsViewModel`

- `usingUserKey: Bool` from `userAPIKeyUpdates()` (non-nil stored key).
- `keyInput: String`: the stored key until the user edits the field, then the edited text.
- `save()` stores `keyInput` (trimmed; blank = reset) and clears the edit. `reset()` removes the stored key and clears the edit; its button is disabled while `usingUserKey` is false.
- `openLanguageSettings()` opens `UIApplication.openSettingsURLString` through the view's `openURL`.

## 8. UI

### 8.1 Navigation (`RootView`, app target)

- `TabView` with two tabs, **Library** (selected at launch) and **Search**; tab labels `nav.library` / `nav.search`, SF Symbols `books.vertical` and `magnifyingglass`. Each tab content is a `NavigationStack`.
- Both screens have a toolbar gear (`gearshape`, accessibility label `library.settings` / `search.settings`) that presents Settings as a `.sheet`.
- Library's **Go to Search** switches the selected tab.

### 8.2 Search screen (`SearchView`)

- Navigation title `search.title`. Search field: `.searchable(text:placement: .navigationBarDrawer(displayMode: .always), prompt:)` with prompt `search.placeholder`; `.onSubmit(of: .search)` submits at once and the keyboard hides. iOS supplies the field's clear and Cancel buttons.
- Chip row (horizontal `ScrollView`, 12 pt side padding, 8 pt spacing) below the field:
  - **Sort** — a `Menu` whose label is the current sort (`search.sortRelevance` / `search.sortMostCited` / `search.sortNewest`) with a `chevron.down`; items Relevance, Most cited, Newest. Shown selected when the sort is not Relevance.
  - **Year** — a `Menu`: Any time (`search.yearAny`), Since 2024, Since 2020, Since 2015 (`search.yearSince`), Custom range… (`search.yearCustom`). Label: `search.yearAny`, `search.yearSince` or `search.yearBetween`. Selected when not Any time.
  - **Open access** — a toggle chip (`search.openAccess`) with a leading `checkmark` when on.
  - Selected chips use the teal container colour **and** expose `.isSelected` to VoiceOver.
- **Custom range sheet** (`YearRangeSheet`, `.medium` detent): title `search.yearDialogTitle`; two wheel `Picker`s labelled `search.yearFrom` and `search.yearTo`, each listing 1900 through the current year; initial values the active range's years, else the current year for both. When from > to, the error `search.yearErrorOrder` shows and **Apply** (`search.apply`) is disabled; **Cancel** (`search.cancel`) closes without change.
- Result count line above the list when known: `search.resultCount`, e.g. "About 48,210 results" (count formatted with the locale's grouping and digits).
- **Result card** (`PaperCard`): title (up to 3 lines); meta line (up to 2 lines) of authors · year · venue joined with " · ", where authors are the first three names joined with ", " plus `designsystem.authorsMore` ("+N") when there are more; a bottom row with the **Open access** badge when open access, the **In library** badge when saved, the compact citation text `designsystem.citedCount` ("128K cited") using `.number.notation(.compactName)`, and a **Save** button (`designsystem.save`, tonal style) at the trailing end when not saved. Tapping the card opens the preview sheet.
- States:
  - **Idle** (no active query): search icon, `search.idleTitle`, `search.idleMessage`, and three suggestion chips "large language models", "CRISPR", "climate adaptation" (not translated) that fill and submit the query.
  - **Loading** (first page): `LoadingSkeleton`, 4 static card-shaped rows (not animated, so snapshots are stable).
  - **Results**: count line, cards; footer spinner while appending; on append failure a footer row with `search.appendError` and a **Retry** button (`search.retry`).
  - **Empty**: `search.emptyTitle`, `search.emptyMessage`, and **Clear filters** (`search.clearFilters`) only when a year or open-access filter is active.
  - **Error** (first page): §11.
- Save/remove failures show a `HashiyaBanner` at the bottom with `search.saveFailed` / `search.removeFailed` for 4 s.

### 8.3 Preview sheet

`.sheet(item:)` with `.presentationDetents([.medium, .large])` and a drag indicator. Its body is `PaperPreviewContent(paper:inLibrary:onToggleSave:onOpenDOI:)` (`HashiyaDesignSystem`; `onOpenDOI: ((String) -> Void)?`, `nil` hides Open DOI), 16 pt side padding:

- Scrollable part: full title (preview-title style); all authors joined with ", " (omitted when there are none); a meta line of venue · year · `designsystem.citations` ("128,412 citations", full grouped number); the badge `designsystem.openAccessPDF` ("Open access · PDF available") when open access with a PDF URL, else `designsystem.openAccess` when open access; the label `designsystem.abstract`; the abstract, or `designsystem.noAbstract` in the secondary colour.
- Buttons row, each half width: **Open DOI** (`designsystem.openDOI`, bordered, with `arrow.up.forward.square`) only when the paper has a DOI — opens `https://doi.org/{doi}` with the DOI percent-encoded for a URL path (`/` kept); if no valid URL results, nothing happens — and **Save to library** / **Remove from library** (`designsystem.saveToLibrary` / `designsystem.removeFromLibrary`, prominent).
- It uses the `Paper` already loaded; no network call.

### 8.4 Library screen (`LibraryView`, this spec's state)

- Navigation title `library.title`; gear toolbar item.
- A plain `List`, newest saved first, headed by the count line `library.paperCount` ("3 papers"). Row: title (up to 2 lines, card-title style); one meta line: first author (alone if there is exactly one, else `library.etAl` "Vaswani et al."), year, venue, joined with " · ". Tap opens the preview sheet (`PaperPreviewContent` with `inLibrary: true`; its button removes).
- Swipe from the trailing edge: a destructive **Remove** action (`library.remove`, `trash` icon), full swipe allowed. Removing shows a `HashiyaBanner` at the bottom, "Removed from library" (`library.removed`) with **Undo** (`library.undo`), for 4 s; a new removal restarts it. Undo restores the paper with the same id and `saved_at`, so it returns to its original position. (iOS has no system snackbar; `HashiyaBanner` is ours.)
- **Empty**: books icon, `library.emptyTitle`, `library.emptyMessage`, and **Go to Search** (`library.goToSearch`).
- Everything here works offline.

### 8.5 Settings sheet (`SettingsView`)

A `NavigationStack` with title `settings.title` and a **Done** button (`settings.done`) that closes the sheet. A `Form`:

- Section header `settings.apiKeySection` ("OpenAlex API key"): status line `settings.apiKeyUsingBuiltIn` or `settings.apiKeyUsingYours`; the key field labelled `settings.apiKeyLabel`, a `SecureField` that switches to a `TextField` with an eye button (`eye` / `eye.slash`, accessibility labels `settings.apiKeyShow` / `settings.apiKeyHide`); autocorrection and autocapitalization off. Buttons **Save** (`settings.save`) and **Reset to built-in** (`settings.reset`, disabled while using the built-in key).
- Section header `settings.languageSection` ("Language"): one row showing the current app language's name (from `Locale.current`, e.g. "English", "العربية") with an `arrow.up.forward.app` icon; tapping opens the app's page in iOS Settings. Footer `settings.languageFooter`.

### 8.6 Localization and RTL

- Each package target with UI text has `Resources/Localizable.xcstrings` (`resources: [.process("Resources")]`); the app target has `Localizable.xcstrings` and `InfoPlist.xcstrings` (`CFBundleDisplayName`: "Hashiya" / "حاشية"). The project's known regions are `en` and `ar`, so iOS Settings lists the app's language option.
- Keys are identifiers (§10) with extraction state manual; views never contain literal user-facing text. Each target has an `L10n` helper that looks up `Bundle.module` and formats with `String.localizedStringWithFormat`, and views pass the result to `Text(verbatim:)`.
- Numbers: every number shown is formatted with the current locale (`FormatStyle`) and passed as a `%@` argument, so Arabic shows the locale's digits. Years use `.number.grouping(.never)` ("2024", never "2,024"). Plural strings take two arguments: `%1$lld` (the count, used only to choose the plural form) and `%2$@` (the formatted count shown). That a plural variant may show an argument other than the one it varies by must be confirmed for String Catalogs (verify against current docs); the English "About 48,210 results" snapshot checks it.
- Layout uses leading/trailing only. `arrow.up.forward.*` symbols mirror in right-to-left layouts (SF Symbols "forward" variants; verify in the Arabic snapshots).
- **Paper content direction.** Titles, authors, venues and abstracts are laid out in their own direction: `ContentDirection.of(_:)` (`HashiyaDesignSystem`) returns right-to-left when the first strong character is Arabic or Hebrew (U+0590–U+08FF, U+FB1D–U+FDFF, U+FE70–U+FEFF), left-to-right for any other letter, and the UI direction when there is no letter. Paper text views are full width, apply `.environment(\.layoutDirection, …)` from it and align leading, so an English title sits left-aligned in the Arabic UI.

### 8.7 Design system (`HashiyaDesignSystem`)

**Colors** — asset catalog color sets (light / dark), taken from the Android theme:

| Color set | Light | Dark | Used for |
|---|---|---|---|
| `Primary` (also AccentColor) | `#0B6E6E` | `#7FD4D2` | tint, buttons, icons in empty states |
| `OnPrimary` | `#FFFFFF` | `#003737` | text on primary |
| `PrimaryContainer` | `#D7ECEA` | `#004F4F` | selected chips |
| `OnPrimaryContainer` | `#002020` | `#9CF1EE` | text on selected chips |
| `SecondaryContainer` | `#E0F2EF` | `#1F3F3D` | Open access badge, tonal Save button |
| `OnSecondaryContainer` | `#0B3B3A` | `#CCE8E6` | text on the above |
| `Surface` | `#FFFFFF` | `#0E1417` | backgrounds |
| `OnSurface` | `#0F1720` | `#DEE3E6` | primary text |
| `OnSurfaceVariant` | `#5B6770` | `#BEC8CC` | secondary text |
| `Outline` | `#D5DBDF` | `#3A4448` | chip and field borders |
| `OutlineVariant` | `#E3E8EB` | `#2A3236` | card borders, dividers |
| `SurfaceContainerHigh` | `#EBEEF0` | `#242B2E` | In library badge, skeleton bars |
| `SurfaceContainerHighest` | `#E3E8EB` | `#2F3639` | (spec 3: Read badge) |
| `Error` | `#BA1A1A` | `#FFB4AB` | error text |
| `ErrorContainer` | `#FFDAD6` | `#93000A` | swipe-remove background |
| `OnErrorContainer` | `#410002` | `#FFDAD6` | text on it |

**Fonts** — Inter (Regular, Medium, SemiBold) and IBM Plex Sans Arabic (Regular, Medium, SemiBold) as `.ttf` resources of `HashiyaDesignSystem`, with their OFL licence files; registered at launch with `CTFontManagerRegisterFontsForURL` (package resources can't use `UIAppFonts`). The family follows the UI language: IBM Plex Sans Arabic when it is Arabic (it has Latin glyphs, so mixed text uses one family), Inter otherwise. Styles scale with Dynamic Type via `Font.custom(_:size:relativeTo:)`:

| Role | Size / weight | Relative to | Used for |
|---|---|---|---|
| `previewTitle` | 22 SemiBold | `.title2` | preview title |
| `stateTitle` | 16 SemiBold | `.headline` | empty/error titles, Settings section titles |
| `cardTitle` | 14 SemiBold | `.subheadline` | card and row titles |
| `body` | 14 Regular | `.subheadline` | abstract, messages, authors |
| `meta` | 12 Regular | `.caption` | meta lines |
| `label` | 12 Medium | `.caption` | count lines, abstract label |
| `badge` | 11 Medium | `.caption2` | badges, compact citations |

**Components and metrics**

- `PaperCard`: bordered (`OutlineVariant`, 1 pt), corner radius 10, 12 pt horizontal margin and 5 pt vertical margin, 12×10 pt inner padding.
- `StatusBadge(text:kind:)` with kinds `.openAccess` (`SecondaryContainer`) and `.inLibrary` (`SurfaceContainerHigh`): radius 6, padding 7×2, `badge` font.
- `EmptyStateView(icon:title:message:actionTitle:action:)` (message optional) and `ErrorStateView(title:message:actionTitle:action:)` (icon `exclamationmark.circle`): centred, 40 pt icon in `Primary`, 32 pt side and 48 pt vertical padding.
- `LoadingSkeleton(rows: 4)`: bordered rows with three bars at 85 %, 60 % and 35 % width, 10 pt high.
- `HashiyaBanner(text:actionTitle:action:)`: bottom overlay above the tab bar, dismissed after 4 s.
- Icons: Search idle `magnifyingglass`; no results `doc.text.magnifyingglass`; empty Library `books.vertical`.

### 8.8 Accessibility

- A result card's text (title, authors, year, venue, badges, citations) is one VoiceOver element (`.accessibilityElement(children: .combine)`); the **Save** button stays a separate button labelled "Save".
- Badges are text, so VoiceOver reads "Open access" and "In library".
- Chips expose `.isSelected` when selected, and the Open access toggle chip reads as a button whose selected state changes.
- Icon-only buttons have labels: gear (`library.settings` / `search.settings`), show/hide key (`settings.apiKeyShow` / `settings.apiKeyHide`).
- Dynamic Type: all text uses the relative styles above; layouts wrap rather than truncate except where a line limit is stated.

## 9. Security

- `ios/Config/Secrets.xcconfig` is git-ignored; CI builds without it (no key).
- The OpenAlex key is added only by the OpenAlex client; it is never logged (Release logs nothing; Debug redacts `api_key`), never included in an error or message, and `NetworkFailure` drops underlying errors that could carry the URL.
- A key built into the app bundle can be extracted; accepted for a portfolio app, as on Android.

## 10. Strings

**Key convention.** An Android name `<prefix>_<rest>` becomes the iOS key `<prefix>.<rest in lowerCamelCase>`: `search_error_offline_title` → `search.errorOfflineTitle`, `designsystem_open_access_pdf` → `designsystem.openAccessPDF`, `library_et_al` → `library.etAl`, `nav_library` → `nav.library`. Acronyms stay upper-case (`DOI`, `PDF`, `API`, `ID`, `LLM`, `CRISPR`); arXiv becomes `Arxiv` (`search.lookupLookingArxiv`). The same rule is used in specs 2 and 3. Keys marked *iOS only* have no Android counterpart. `%1$s` becomes `%1$@`; numbers are passed as formatted strings (`%@`) except plural selectors (`%1$lld`).

App target (`Localizable.xcstrings`, `InfoPlist.xcstrings`):

| Key | English | Arabic |
|---|---|---|
| `CFBundleDisplayName` (from `app_name`) | Hashiya | حاشية |
| `nav.library` | Library | المكتبة |
| `nav.search` | Search | البحث |

`HashiyaDesignSystem`:

| Key | English | Arabic |
|---|---|---|
| `designsystem.untitled` | Untitled | بدون عنوان |
| `designsystem.openAccess` | Open access | وصول مفتوح |
| `designsystem.openAccessPDF` | Open access · PDF available | وصول مفتوح · ملف PDF متاح |
| `designsystem.inLibrary` | In library | في المكتبة |
| `designsystem.save` | Save | حفظ |
| `designsystem.citedCount` | %1$@ cited | %1$@ استشهاد |
| `designsystem.citations` | %1$@ citations | %1$@ استشهاد |
| `designsystem.authorsMore` | %1$@ +%2$@ | %1$@ +%2$@ |
| `designsystem.abstract` | Abstract | الملخص |
| `designsystem.noAbstract` | No abstract available | لا يوجد ملخص |
| `designsystem.openDOI` | Open DOI | فتح DOI |
| `designsystem.saveToLibrary` | Save to library | حفظ في المكتبة |
| `designsystem.removeFromLibrary` | Remove from library | إزالة من المكتبة |

`FeatureSearch`:

| Key | English | Arabic |
|---|---|---|
| `search.title` | Search | البحث |
| `search.settings` | Settings | الإعدادات |
| `search.placeholder` | Search papers | ابحث عن أوراق |
| `search.sortRelevance` | Relevance | الأكثر صلة |
| `search.sortMostCited` | Most cited | الأكثر استشهادًا |
| `search.sortNewest` | Newest | الأحدث |
| `search.yearAny` | Any time | أي وقت |
| `search.yearSince` | Since %1$@ | منذ %1$@ |
| `search.yearBetween` | %1$@–%2$@ | %1$@–%2$@ |
| `search.yearCustom` | Custom range… | نطاق مخصص… |
| `search.openAccess` | Open access | وصول مفتوح |
| `search.resultCount` (plural on `%1$lld`, shows `%2$@`) | one: About %2$@ result · other: About %2$@ results | zero: لا توجد نتائج · one: نتيجة واحدة تقريبًا · two: نتيجتان تقريبًا · few: حوالي %2$@ نتائج · many: حوالي %2$@ نتيجة · other: حوالي %2$@ نتيجة |
| `search.idleTitle` | Search OpenAlex | ابحث في OpenAlex |
| `search.idleMessage` | Find papers by title, keyword or author | اعثر على الأوراق بالعنوان أو الكلمة المفتاحية أو المؤلف |
| `search.suggestionLLM` (not translated) | large language models | — |
| `search.suggestionCRISPR` (not translated) | CRISPR | — |
| `search.suggestionClimate` (not translated) | climate adaptation | — |
| `search.emptyTitle` | No papers match | لا توجد أوراق مطابقة |
| `search.emptyMessage` | Try fewer words or turn off filters | جرّب كلمات أقل أو أوقف عوامل التصفية |
| `search.clearFilters` | Clear filters | مسح عوامل التصفية |
| `search.errorOfflineTitle` | Can't reach OpenAlex | تعذّر الوصول إلى OpenAlex |
| `search.errorOfflineMessage` | Check your connection. Your library still works offline. | تحقق من اتصالك. مكتبتك لا تزال تعمل دون اتصال. |
| `search.errorKeyTitle` | Your API key was rejected | تم رفض مفتاح API الخاص بك |
| `search.errorKeyMessage` | Check the key in Settings, or reset to the built-in key. | تحقق من المفتاح في الإعدادات، أو ارجع إلى المفتاح المدمج. |
| `search.errorUnavailableTitle` | Search is unavailable right now | البحث غير متاح الآن |
| `search.errorUnavailableMessage` | Please try again in a moment. | يُرجى المحاولة مرة أخرى بعد قليل. |
| `search.errorRateTitle` | Too many requests | طلبات كثيرة جدًا |
| `search.errorRateMessage` | Try again in a moment. | حاول مرة أخرى بعد قليل. |
| `search.errorUnexpectedTitle` | Something went wrong | حدث خطأ ما |
| `search.errorUnexpectedMessage` | Please try again. | يُرجى المحاولة مرة أخرى. |
| `search.retry` | Retry | إعادة المحاولة |
| `search.openSettings` | Open Settings | فتح الإعدادات |
| `search.appendError` | Couldn't load more results | تعذّر تحميل المزيد من النتائج |
| `search.saveFailed` | Couldn't save the paper | تعذّر حفظ الورقة |
| `search.removeFailed` | Couldn't remove the paper | تعذّرت إزالة الورقة |
| `search.yearDialogTitle` | Custom year range | نطاق سنوات مخصص |
| `search.yearFrom` | From | من |
| `search.yearTo` | To | إلى |
| `search.yearErrorOrder` | The start year must not be after the end year | يجب ألا تكون سنة البداية بعد سنة النهاية |
| `search.apply` | Apply | تطبيق |
| `search.cancel` | Cancel | إلغاء |

`FeatureLibrary`:

| Key | English | Arabic |
|---|---|---|
| `library.title` | Library | المكتبة |
| `library.settings` | Settings | الإعدادات |
| `library.paperCount` (plural on `%1$lld`, shows `%2$@`) | one: %2$@ paper · other: %2$@ papers | zero: لا توجد أوراق · one: ورقة واحدة · two: ورقتان · few: %2$@ أوراق · many: %2$@ ورقة · other: %2$@ ورقة |
| `library.emptyTitle` | No saved papers yet | لا توجد أوراق محفوظة بعد |
| `library.emptyMessage` | Papers you save from Search will appear here | ستظهر هنا الأوراق التي تحفظها من البحث |
| `library.goToSearch` | Go to Search | الذهاب إلى البحث |
| `library.removed` | Removed from library | تمت الإزالة من المكتبة |
| `library.undo` | Undo | تراجع |
| `library.remove` | Remove | إزالة |
| `library.etAl` | %1$@ et al. | %1$@ وآخرون |

`FeatureSettings`:

| Key | English | Arabic |
|---|---|---|
| `settings.title` | Settings | الإعدادات |
| `settings.apiKeySection` | OpenAlex API key | مفتاح OpenAlex API |
| `settings.apiKeyLabel` | API key | مفتاح API |
| `settings.apiKeyUsingBuiltIn` | Using built-in key | يتم استخدام المفتاح المدمج |
| `settings.apiKeyUsingYours` | Using your key | يتم استخدام مفتاحك |
| `settings.apiKeyShow` | Show key | إظهار المفتاح |
| `settings.apiKeyHide` | Hide key | إخفاء المفتاح |
| `settings.save` | Save | حفظ |
| `settings.reset` | Reset to built-in | الرجوع إلى المفتاح المدمج |
| `settings.languageSection` | Language | اللغة |
| `settings.done` (*iOS only*) | Done | تم |
| `settings.languageFooter` (*iOS only*) | Opens iOS Settings, where you can choose the app's language. | يفتح إعدادات iOS، حيث يمكنك اختيار لغة التطبيق. |

Android strings not used on iOS: `settings_back` (the sheet has Done), `settings_language_system`, `settings_language_english`, `settings_language_arabic` (no in-app picker), `search_clear` (iOS supplies the field's clear button), `search_year_error_number` and `search_year_error_range` (pickers only offer valid years).

## 11. Error handling

| Cause | `SearchError` | User sees |
|---|---|---|
| No connection, timeout, any transport error | `offline` | `search.errorOfflineTitle` + `search.errorOfflineMessage` + **Retry** |
| 401/403 with the user's key | `invalidUserKey` | `search.errorKeyTitle` + `search.errorKeyMessage` + **Open Settings** (presents the Settings sheet) |
| 401/403 with the built-in key | `serviceUnavailable` | `search.errorUnavailableTitle` + `search.errorUnavailableMessage` + **Retry** |
| 429 | `rateLimited` | `search.errorRateTitle` + `search.errorRateMessage` + **Retry** (no automatic retry) |
| 5xx | `serviceUnavailable` | as above |
| Unreadable body, other HTTP codes, anything else | `unexpected` | `search.errorUnexpectedTitle` + `search.errorUnexpectedMessage` + **Retry** |

- A first-page error replaces the list with `ErrorStateView`; an append error shows the footer row with **Retry** and keeps the loaded results.
- Save and remove are local and work offline. A database failure shows the banner `search.saveFailed` / `search.removeFailed` and changes nothing else.

## 12. Testing

Test-first for all production code; Swift Testing with hand-written fakes, no mocking library. `HashiyaTesting` provides `FakeSearchRepository` (records queries and cursors, returns scripted pages or errors), `FakeLibraryRepository` (in-memory, with streams), `FakeUserPreferencesRepository`, `SamplePapers` (`attention`, `bert`, `vit`, `arabicTitled`, `untitled`, same data as Android's `SamplePapers`), `URLProtocolStub`, `ManualSleeper`, and `assertHashiyaSnapshots` (§12.1). Fixtures `works_page.json` and `work.json` are copied byte-for-byte from `core/network/src/test/resources/` into `HashiyaTesting/Resources/Fixtures/`.

| Target | Coverage |
|---|---|
| `HashiyaModelTests` | `normalizeDOI` cases of §4; `SearchQuery.hasActiveFilters` (sort is not a filter; year and open access are); `YearFilter.between` rejects from > to and accepts from == to |
| `HashiyaNetworkTests` | With `URLProtocolStub`: path `/works` and every query parameter of §6.1 (search, filter, sort, `per_page=25`, `cursor=*`, `select`); filter and sort omitted when nil; Arabic and punctuation round-trip; built-in key used, user key overrides, no key when neither is set; 401 with user key → `.http(401, usedUserKey: true)`, 403 with built-in key → `usedUserKey: false`, 429 keeps its code; `{"unexpected": true}` → `.malformedResponse`; transport failure → `.connectivity`; the key never appears in captured log lines or in any error's description. Parsing `works_page.json`: `meta.count` 48210, `next_cursor` `IlsxMDAuMCwgJ1czMTc3ODI4OTA5J10i`, 2 results; complete work fields (id `https://openalex.org/W2626778328`, three authors, the third with null id, 128412 citations, OA PDF `https://arxiv.org/pdf/1706.03762`, `"dominant"` at position 1); the sparse work's nulls (citations 0, OA false). `rebuildAbstract` cases of §6.2 and nil/empty → nil |
| `HashiyaDatabaseTests` | In-memory: newest first; author positions kept; saving again is a no-op; two works with one DOI both save; delete returns the row and cascades authors; delete + insert of the returned row keeps `id` and `saved_at`; deleting an unknown paper → nil; saved-IDs observation |
| `HashiyaDataTests` | `worksSearchRequest`: plain query (`"  bert "` → search `bert`, no filter or sort, cursor `*`, 25); each sort; since → `>year-1`; between; year + OA combined; OA alone; cursor passed through. `asPaper`: complete work, sparse work (empty title, nameless authors dropped, nil fields, OA false), invalid DOI dropped. `asSearchError`: every row of §6.4. Search repository: first page reports total, next page uses the previous cursor, end when no cursor, end when a page is empty, network failure → `SearchError`. Library repository on an in-memory database: newest first with authors in order, saved IDs, save twice keeps one, remove + restore returns the paper to its position, restore after re-save is a no-op, removing an unknown paper → nil. Keychain repository with an in-memory keychain fake behind a small `KeychainStore` protocol: trims, blank deletes, updates stream |
| `FeatureSearchTests` | Blank query idle with no request; debounce (nothing before 300 ms, one request after); Search key skips the debounce; text trimmed; chip changes apply at once; Clear filters keeps sort; clearing the text is idle at once; API key change re-runs the active search and does nothing while idle; saved IDs separate from papers; saving updates badges without a new search; toggle save then remove; save failure message once; remove failure message; total count exposed; duplicates across pages dropped; append failure keeps results and Retry reloads the same cursor; `@SceneStorage` values restore text and chips and submit at once |
| `FeatureLibraryTests` | Empty; newest first; select and dismiss preview; remove offers Undo and closes the preview; Undo restores in place; two quick removals keep only the latest; expired Undo forgets the paper |
| `FeatureSettingsTests` | Starts on built-in key; shows stored key; saves trimmed key; saving blank reverts; reset reverts |
| Snapshots | §12.1 |
| `HashiyaUITests` | One flow with the app launched with `-ui-testing` (Debug only: in-memory database and a stub `SearchRepository` returning `SamplePapers`): empty Library → Go to Search → type "attention" → Save the first result → Library tab lists it → swipe Remove → Undo → it is back |

### 12.1 Snapshots

- `swift-snapshot-testing`; each screen and state is rendered in a `UIHostingController` on a fixed device size (`.image(on: .iPhone13)`), in light and dark, English and Arabic: 4 images per state, named `<state>-EnglishLight`, `-EnglishDark`, `-ArabicLight`, `-ArabicDark` like Android's.
- Language: the test plan `HashiyaSnapshots.xctestplan` has two configurations, English and Arabic (application language and region; verify that package test bundles honour them); the helper also sets `.environment(\.layoutDirection, …)` and `\.locale`. Each Arabic image asserts that a known Arabic string is on screen, so a silently English render fails.
- Fonts are registered in the helper before rendering.
- States: Search idle, loading, results (with badges, count line, an Arabic-titled paper), empty with Clear filters, offline error, append error footer; paper cards; preview (open access with PDF, no abstract, no DOI); Library empty and papers; Settings built-in and user key.
- Baselines are recorded only by the record workflow (§12.2) and committed under each test target's `__Snapshots__/`. Local runs on other simulators or Xcode versions are not the source of truth.

### 12.2 CI

- `.github/workflows/ios.yml`: on `push` and `pull_request` with `paths: ['ios/**', '.github/workflows/ios.yml']`, `branches-ignore: ['record-snapshots-ios/**']`; `runs-on: macos-15`. Steps: select the pinned Xcode (`sudo xcode-select -s /Applications/Xcode_<pinned>.app`, version written in the workflow — verify against the current runner image); `python3 ios/scripts/check-translations.py`; install XcodeGen (`brew install xcodegen`) and generate the project (`xcodegen generate --spec ios/project.yml`); `xcodebuild test` on the `Hashiya` scheme with the CI test plan (all package test targets, snapshots, UI tests) on the pinned simulator (`iPhone 16` with the pinned iOS runtime); on failure, upload snapshot diffs as an artifact. No key and no live network.
- `check-translations.py`: for every `*.xcstrings` under `ios/`, every key not marked `shouldTranslate: false` must have an `ar` localization in state `translated`; plural keys must define Arabic `zero`, `one`, `two`, `few`, `many` and `other`. It prints each missing key and exits non-zero.
- `.github/workflows/ios-record-snapshots.yml`: on push to `record-snapshots-ios/**`, same runner, Xcode and simulator; installs XcodeGen and generates the project as above; runs the snapshot tests with `TEST_RUNNER_SNAPSHOT_RECORD=1` (xcodebuild passes it to the tests as `SNAPSHOT_RECORD=1`; the helper then records), ignores the test exit code (recording fails tests by design) and uploads `ios/**/__Snapshots__/**` as the artifact `ios-snapshot-baselines`.
- `ios/scripts/record-snapshots-on-ci.sh` mirrors `scripts/record-screenshots-on-linux.sh`: pushes `HEAD` to `record-snapshots-ios/<short sha>`, waits for the run with `gh run watch`, downloads `ios-snapshot-baselines` into the checkout, and deletes the branch on exit.
- The Android `ci.yml` `build` job's `if` also skips branches starting with `record-snapshots-ios/`, so recording iOS baselines doesn't run the Android build.

## 13. Acceptance criteria (on a device or simulator)

1. A fresh install opens on the empty Library; **Go to Search** switches to the Search tab.
2. Typing a query shows results after the pause; scrolling loads further pages until the end.
3. Each sort option and year filter changes the request as in §6.1 (checked by tests) and the visible results; Custom range… refuses a start year after the end year.
4. Tapping a result opens the preview sheet; **Save** shows the "In library" badge on the card and the paper in Library, also in airplane mode.
5. The Library works fully offline; swipe → Remove → **Undo** within 4 s puts the paper back in its original position.
6. Each error in §11 shows its message and action (offline in airplane mode; a wrong key entered in Settings shows the key error with Open Settings).
7. Entering a key in Settings switches the status line to "Using your key" and requests use it; **Reset to built-in** reverts.
8. Choosing العربية for Hashiya in iOS Settings (reached from the Language row) mirrors the whole UI with Arabic strings; English paper titles stay left-to-right.
9. CI is green: translations check, unit tests, snapshot verification, UI test.
10. `ios/README.md` explains generating and opening the project with XcodeGen, `Secrets.xcconfig` and `OPENALEX_API_KEY`, running tests and recording snapshots.

## 14. Risks

| Risk | Mitigation |
|---|---|
| Snapshot images differ across Xcode or simulator versions | Baselines recorded only on CI with pinned Xcode, runtime and device; fonts bundled |
| Package test bundles ignore the test plan's language | Each Arabic snapshot asserts an Arabic string; if the language setting is ignored, the helper loads the `ar.lproj` table directly |
| Plural variants that show a second argument are not supported by String Catalogs | Snapshot checks "About 48,210 results"; fallback is a `.stringsdict`-style substitution in the same catalog |
| URLSession timeouts don't map one-to-one to OkHttp's | Offline fails fast (`waitsForConnectivity = false`); limits documented in §6.5 |
| OpenAlex changes its key or rate-limit policy | Key handling is isolated in the client and `UserAPIKeySource` |
| A key in the app bundle can be extracted | Accepted for a portfolio app, as on Android |

## 15. Differences from Android

| Area | Android | iOS | Why |
|---|---|---|---|
| Navigation | Navigation suite (bottom bar / rail), Settings as a destination with Back | `TabView`; Settings as a sheet with Done | iOS conventions |
| Language switch | In-app System / English / العربية picker (AppCompat) | Language row opens iOS Settings | iOS per-app language lives in Settings |
| Undo and messages | Material snackbar | Custom `HashiyaBanner`, 4 s | iOS has no system snackbar |
| Search field | Custom text field with a clear button and `TextDirection.Content` | `.searchable`; iOS supplies clear/Cancel; the field follows iOS's own text direction | System search UI |
| Custom year range | Dialog with two number fields and three validation messages | Sheet with two year pickers (1900…current year); only the order check remains | Pickers can't produce an invalid year |
| Paging | Paging 3 `PagingSource` | View-model paging over `SearchRepository.searchPage` | No Paging library on iOS |
| State restoration | `SavedStateHandle` | `@SceneStorage` with the same key names | Platform mechanism |
| User key storage | DataStore preferences | Keychain, shared access group | Secure storage, shared with the extension |
| Built-in key | `local.properties` → `BuildConfig` | git-ignored `Secrets.xcconfig` → Info.plist | Platform build configuration |
| Timeouts | Connect 10 s, read 20 s | Request (idle) 10 s, resource 20 s | URLSession has no connect-only timeout |
| Database location | App's private database directory | App Group container from the start | Shared with the Share Extension in spec 2 without a move |
| Snapshot baselines | Roborazzi on CI Linux | swift-snapshot-testing on CI macOS | Platform tooling |
| Unused Android strings | — | `settings_back`, `settings_language_*`, `search_clear`, `search_year_error_number`, `search_year_error_range` not ported | No matching UI on iOS |
| iOS-only strings | — | `settings.done`, `settings.languageFooter` | New UI elements |
