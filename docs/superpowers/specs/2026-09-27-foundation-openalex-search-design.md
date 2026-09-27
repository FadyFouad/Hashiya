# Sub-project 1: Foundation + OpenAlex Search — Design

- **Date:** 2026-09-27
- **Status:** Awaiting review
- **Scope:** First of six MVP sub-projects for Hashiya

## 1. Context

Hashiya is a native Android app that helps master's and PhD students manage their research journey. Every feature serves the core loop **Discover → Save → Read → Extract → Compare → Cite**.

**Goal:** a high-quality portfolio project that is judged equally by recruiters (screenshots, demo, README) and by senior Android engineers (architecture, tests). It may become a real product, so decisions must not block that.

### MVP roadmap (build order)

| # | Sub-project | Contents |
|---|---|---|
| **1** | **Foundation + OpenAlex search** (this spec) | Module architecture, DI, database, network, design system, OpenAlex search with filters, preview sheet, save to library, "In library" badge, basic Library list, Settings |
| 2 | Add by DOI / arXiv + Share intent | Resolve IDs via OpenAlex; receive `ACTION_SEND` from browsers |
| 3 | Library | Room FTS (title, abstract, notes), reading status, "My Library / OpenAlex" tabs on Search |
| 4 | Paper Details + structured notes | Full details screen, fixed note template |
| 5 | Collections + BibTeX export | |
| 6 | PDFs | Attach a PDF or download the open-access version |

Post-MVP: Topic Feed (WorkManager), Literature Review Matrix.

### Decisions made during brainstorming

| Topic | Decision |
|---|---|
| Audience | Recruiters and engineers equally → modular architecture, real tests, polished UI |
| API key | Built-in key from `local.properties` → `BuildConfig`, with an optional user override in Settings (DataStore) |
| Search features | Keyword search + filter/sort chips + preview bottom sheet. Recent searches deferred |
| Languages | English and Arabic from day one, full RTL support |
| Visual style | "Modern academic": neutral surfaces, single deep-teal brand color, Inter + IBM Plex Sans Arabic, dense tool-like layout |
| Architecture | Now in Android–style modules with convention plugins in `build-logic`, trimmed to what this sub-project needs |

## 2. Goals and non-goals

### Goals

1. A user can search OpenAlex by keyword and scroll through paged results.
2. A user can sort by relevance, most cited, or newest, filter by year, and restrict to open access.
3. A user can open a preview sheet for any result and save or remove the paper.
4. Saved papers show an "In library" badge in search results and appear in a basic Library list that works offline.
5. A user can override the built-in API key and switch the app language (System / English / Arabic).
6. The module structure, test suite, and CI establish the patterns every later sub-project follows.

### Non-goals (this sub-project)

- Searching inside the library (FTS) and the "My Library / OpenAlex" tabs (sub-project 3).
- Reading status, notes, collections, PDFs, BibTeX (sub-projects 3–6).
- Adding papers by DOI/arXiv or Share intent (sub-project 2).
- Recent searches, offline caching of search results, a connectivity monitor.
- A domain/use-case layer (added when real business logic appears).
- Instrumented (emulator) tests, benchmarks, Baseline Profiles.
- Accounts, sync, backend.

## 3. Architecture

### 3.1 Modules

```
app/                 NavHost, Hilt application, MainActivity, locale config
build-logic/         convention plugins
core/model           pure Kotlin/JVM: Paper, Author, SearchQuery, SearchSort, errors
core/network         Retrofit + OkHttp + kotlinx.serialization, OpenAlex DTOs, API key interceptor
core/database        Room database, entities, DAOs
core/datastore       Preferences DataStore: user API key override
core/data            repository interfaces + implementations, DTO/entity ↔ model mapping, PagingSource
core/designsystem    theme, typography, bundled fonts, shared components
core/testing         fakes, sample data, test rules (test dependencies only)
feature/search       search screen, filter chips, preview sheet, SearchViewModel
feature/library      basic library list, LibraryViewModel
feature/settings     API key + language, SettingsViewModel
```

### 3.2 Dependency rules

- `app` → all `feature/*`, `core/data`, `core/designsystem`.
- `feature/*` → `core/data` (interfaces only), `core/model`, `core/designsystem`. Features never depend on `core/network`, `core/database`, or `core/datastore`, and never on each other.
- `core/data` → `core/network`, `core/database`, `core/datastore`, `core/model`.
- `core/network`, `core/database`, `core/datastore` are leaves: they do not depend on each other or on `core/model`. Their DTOs and entities are internal to them and converted by `core/data`.
- `core/designsystem` → `core/model` (shared components render `Paper`).
- `core/model` has no Android dependencies (`hashiya.jvm.library`).
- `core/testing` is only used from `testImplementation`.

The shared preview sheet lives in `core/designsystem` as a stateless composable (`PaperPreviewSheet`) so both `feature/search` and `feature/library` can use it without depending on each other.

### 3.3 Convention plugins (`build-logic`)

| Plugin | Applies |
|---|---|
| `hashiya.android.application` | AGP application, SDK levels, Java/Kotlin toolchain |
| `hashiya.android.library` | AGP library, same defaults |
| `hashiya.android.compose` | Compose compiler + BOM + tooling |
| `hashiya.android.feature` | library + compose + hilt + `core/data`, `core/model`, `core/designsystem`, lifecycle/navigation/hilt-navigation-compose, test deps incl. `core/testing` |
| `hashiya.android.room` | Room + KSP, schema export directory |
| `hashiya.hilt` | Hilt + KSP |
| `hashiya.jvm.library` | Kotlin JVM library |
| `hashiya.android.test.screenshot` | Robolectric + Roborazzi |

All dependency versions live in `gradle/libs.versions.toml`. KSP is used everywhere; kapt is not allowed.

### 3.4 Platform and toolchain

- AGP 9.2.1, Kotlin 2.2.10 (from the template). AGP 9 compiles Kotlin itself, so modules do not apply `org.jetbrains.kotlin.android`.
- `compileSdk`/`targetSdk` 36, `minSdk` 24 (unchanged).
- **To verify during planning:** the exact Hilt, KSP, Room and Roborazzi versions that work with AGP 9.2.1 and Kotlin 2.2.10. If one does not, the plan adjusts the Kotlin/AGP version rather than dropping the library.

## 4. Data model

### 4.1 `core/model`

```kotlin
data class Paper(
    val openAlexId: String,        // "W2741809807" (short form, no URL prefix)
    val doi: String?,              // normalized: lowercase, no "https://doi.org/" prefix
    val title: String,             // "Untitled" substituted by core/data if missing
    val authors: List<Author>,     // in authorship order
    val year: Int?,
    val venue: String?,
    val abstract: String?,
    val citationCount: Int,
    val isOpenAccess: Boolean,
    val openAccessPdfUrl: String?,
)

data class Author(val name: String, val openAlexId: String?)

data class SearchQuery(
    val text: String,
    val sort: SearchSort = SearchSort.Relevance,
    val years: YearFilter = YearFilter.AnyTime,
    val openAccessOnly: Boolean = false,
)

enum class SearchSort { Relevance, MostCited, Newest }

sealed interface YearFilter {
    data object AnyTime : YearFilter
    data class Since(val year: Int) : YearFilter            // presets: 2024, 2020, 2015
    data class Between(val from: Int, val to: Int) : YearFilter
}

sealed interface SearchError {
    data object Offline : SearchError
    data object InvalidUserKey : SearchError
    data object RateLimited : SearchError
    data object ServiceUnavailable : SearchError
    data object Unexpected : SearchError
}
```

"Untitled" is a sentinel: `core/data` stores an empty title as `""`, and the UI displays the localized "Untitled" string. `core/model` stays free of UI strings.

### 4.2 Database (`core/database`)

Database `HashiyaDatabase`, version 1, schemas exported to `core/database/schemas/` and committed.

**`papers`**

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | UUID generated locally (keeps the door open for manual entries and sync) |
| `open_alex_id` | TEXT NULL | unique index |
| `doi` | TEXT NULL | unique index, normalized |
| `title` | TEXT NOT NULL | |
| `year` | INTEGER NULL | |
| `venue` | TEXT NULL | |
| `abstract` | TEXT NULL | |
| `citation_count` | INTEGER NOT NULL | |
| `is_open_access` | INTEGER NOT NULL | |
| `oa_pdf_url` | TEXT NULL | |
| `saved_at` | INTEGER NOT NULL | epoch millis; library sort key |

`open_alex_id` is nullable even though every paper in this sub-project has one, because later sub-projects may add papers from other sources.

**`paper_authors`**

| Column | Type | Notes |
|---|---|---|
| `paper_id` | TEXT | FK → `papers.id`, `ON DELETE CASCADE` |
| `position` | INTEGER | authorship order |
| `name` | TEXT NOT NULL | |
| `open_alex_author_id` | TEXT NULL | |

Primary key `(paper_id, position)`. A separate table (not JSON) because the post-MVP Topic Feed follows authors.

**DAO operations**

- `observeSavedPapers(): Flow<List<PaperWithAuthors>>` ordered by `saved_at DESC`.
- `observeSavedOpenAlexIds(): Flow<List<String>>`.
- `insertPaperWithAuthors(paper, authors)` in one `@Transaction`; if `open_alex_id` already exists, it is a no-op.
- `deleteByOpenAlexId(id)`; returns the deleted row with authors so Undo can re-insert it unchanged (same `id` and `saved_at`).

### 4.3 Settings (`core/datastore`)

Preferences DataStore with one key: `user_api_key` (string, absent = use built-in). The app language is **not** stored here; it is owned by `AppCompatDelegate` (see §6.5).

## 5. Data flow

### 5.1 OpenAlex request

`GET https://api.openalex.org/works`

| Param | Value |
|---|---|
| `search` | trimmed query text |
| `filter` | comma-joined: `publication_year:>{year-1}` for `Since`, `publication_year:{from}-{to}` for `Between`, `is_oa:true` when `openAccessOnly` |
| `sort` | omitted for `Relevance`; `cited_by_count:desc` for `MostCited`; `publication_date:desc` for `Newest` |
| `per_page` | `25` |
| `cursor` | `*` for the first page, then `meta.next_cursor` |
| `select` | `id,doi,display_name,publication_year,primary_location,authorships,cited_by_count,open_access,best_oa_location,abstract_inverted_index` |
| `api_key` | added by the interceptor, never by callers |

Response mapping (done in `core/data`):

- `id` → strip `https://openalex.org/` → `openAlexId`.
- `doi` → lowercase, strip `https://doi.org/` → `doi`.
- `display_name` → `title` (`""` if null).
- `primary_location.source.display_name` → `venue`.
- `authorships[].author.{display_name,id}` → `authors` in order.
- `open_access.is_oa` → `isOpenAccess`; `best_oa_location.pdf_url` → `openAccessPdfUrl`.
- `abstract_inverted_index` (word → positions) → rebuild the text by placing each word at its positions and joining with spaces. The rebuilder lives in `core/network` as a pure function.
- `meta.count` → total result count shown above the list.
- `meta.next_cursor == null` or an empty `results` list → end of pagination.

### 5.2 API key selection

`core/network` defines `interface UserApiKeySource { val userKey: StateFlow<String?> }`. `core/data` implements it by collecting DataStore in an application-scoped coroutine, so the interceptor reads `userKey.value` without blocking. The interceptor uses the user key when present and `BuildConfig.OPENALEX_API_KEY` otherwise, appends it as `api_key`, and records which kind of key was used so error mapping can tell `InvalidUserKey` from `ServiceUnavailable`. Settings derives its status line from `userKey` alone (null → "Using built-in key").

The built-in key is read in `core/network`'s build script from `local.properties` (`OPENALEX_API_KEY`) into `BuildConfig.OPENALEX_API_KEY`. If the property is missing, the build still succeeds with an empty key, and search shows `ServiceUnavailable` until the user sets a key.

### 5.3 Search pipeline (`SearchViewModel`)

1. `query: MutableStateFlow<SearchQuery>`, persisted to and restored from `SavedStateHandle`.
2. Text changes are debounced by 300 ms; chip changes apply immediately. A blank query produces the Idle state and makes no request.
3. `distinctUntilChanged()` → `flatMapLatest { searchRepository.search(it) }` → `cachedIn(viewModelScope)`. Paging uses a `PagingSource` over the cursor API, with no `RemoteMediator` (search results are not stored).
4. The paged flow is combined with `libraryRepository.observeSavedIds(): Flow<Set<String>>`. Each item becomes `PaperItem(paper, inLibrary)`.
5. The UI collects with `collectAsLazyPagingItems()`; screen state (Idle / Loading / Results / Empty / Error) is derived from `LoadState.refresh` and the item count.

### 5.4 Save and remove

- `libraryRepository.save(paper)` writes paper and authors in one transaction. Saving an already-saved paper is a no-op.
- `libraryRepository.remove(openAlexId): RemovedPaper` returns what was deleted; `libraryRepository.restore(removed)` re-inserts it for Undo.
- The badge and the Library list update through the Room flows; no UI-side "saved" state exists.
- The preview sheet uses the `Paper` already loaded in the list; it makes no network call.

## 6. UI

Mockups: `.superpowers/brainstorm/48653-1790510759/content/screens.html` (local only, not committed).

### 6.1 Navigation

- `NavigationSuiteScaffold` with two top-level destinations: **Library** (start destination) and **Search**. Bottom bar on phones, navigation rail on larger screens.
- **Settings** opens from a gear icon in the top app bar of both top-level screens.
- Type-safe Navigation Compose routes (`@Serializable` objects). Each feature exposes `NavGraphBuilder.xxxScreen(...)` and `NavController.navigateToXxx()`; `app` wires them.

### 6.2 Search screen

- Search field at the top; IME action "Search" also triggers an immediate search, skipping the debounce.
- Chip row: **Sort ▾** (menu: Relevance, Most cited, Newest), **Year ▾** (menu: Any time, Since 2024, Since 2020, Since 2015, Custom range… → dialog with two year fields validated `from ≤ to`, both between 1900 and the current year), **Open access** (toggle chip).
- Result count line ("About 48,210 results"), formatted for the current locale.
- Result card: title (max 3 lines), authors (first three + "+N"), year, venue, compact citation count ("128k cited") via `android.icu.text.CompactDecimalFormat`, "Open access" badge, and either an "In library" badge or a **Save** button.
- States:
  - **Idle** (blank query): prompt plus three suggestion chips that fill the query.
  - **Loading** (first page): skeleton placeholders.
  - **Results**: list; appending pages shows a spinner footer, and an append error shows a footer row with **Retry**.
  - **Empty**: message plus **Clear filters** (shown only when a filter is active).
  - **Error** (first page): message and action per §7.

### 6.3 Preview sheet (`PaperPreviewSheet`)

Modal bottom sheet: full title, all authors, venue · year · citations, open-access badge ("PDF available" when `openAccessPdfUrl != null`), scrollable abstract (or "No abstract available"), and two actions: **Open DOI ↗** (hidden when `doi == null`; opens `https://doi.org/{doi}` in a Custom Tab or browser) and **Save to library** / **Remove from library**.

### 6.4 Library and Settings

- **Library:** saved papers, newest first. Rows show title, first author + "et al.", year, venue. Tap opens the preview sheet. Swipe to remove shows a snackbar with **Undo**. Empty state has a **Go to Search** button.
- **Settings:**
  - API key: status line ("Using built-in key" / "Using your key"), a masked text field with show/hide, **Save** and **Reset to built-in**. Saving trims whitespace; a blank value is treated as Reset.
  - Language: System default / English / العربية.

### 6.5 Localization and RTL

- All UI strings in `res/values/strings.xml` with `res/values-ar/strings.xml` for every module that has UI. No hardcoded strings in composables (enforced by Lint).
- `app/src/main/res/xml/locales_config.xml` declares `en` and `ar`; `android:localeConfig` is set in the manifest so Android 13+ system settings list the app.
- In-app language switching uses `AppCompatDelegate.setApplicationLocales`. This requires `MainActivity` to extend `AppCompatActivity`, an AppCompat-based window theme, and the `AppLocalesMetadataHolderService` entry with `autoStoreLocales=true` for API < 33.
- Layout uses start/end only. Icons that imply direction (back arrow, "Open DOI ↗") use auto-mirrored variants.
- Paper content (titles, authors, abstracts) is rendered with `TextDirection.Content` so English titles stay LTR inside an Arabic UI.
- Numbers use the locale's formatting; whatever digits the locale gives are accepted.

### 6.6 Design system (`core/designsystem`)

- Custom Material 3 `ColorScheme` for light and dark, derived from brand teal `#0B6E6E`. Dynamic color is off.
- Fonts bundled in `res/font` (both OFL-licensed): Inter for Latin, IBM Plex Sans Arabic for Arabic; a `FontFamily` fallback chain covers mixed text.
- Shared components: `HashiyaTheme`, `PaperCard`, `PaperPreviewSheet`, `FilterChipRow`, `StatusBadge`, `EmptyState`, `ErrorState`, `LoadingSkeleton`.
- Every component has `@Preview`s for light/dark and LTR/RTL.

## 7. Error handling

`core/network` classifies failures into its own internal `NetworkFailure` type (it does not depend on `core/model`). `core/data` maps `NetworkFailure` to `SearchError` and surfaces it through `LoadResult.Error` with a typed exception (`SearchException(error: SearchError)`).

| Cause | `SearchError` | User sees |
|---|---|---|
| `IOException` / timeout | `Offline` | "Can't reach OpenAlex. Your library still works offline." + **Retry** |
| 401/403 with a user key | `InvalidUserKey` | "Your API key was rejected." + **Open Settings** |
| 401/403 with the built-in key | `ServiceUnavailable` | "Search is unavailable right now." + **Retry** |
| 429 | `RateLimited` | "Too many requests. Try again in a moment." + **Retry** (no automatic retry) |
| 5xx | `ServiceUnavailable` | as above |
| `SerializationException` or anything else | `Unexpected` | "Something went wrong." + **Retry** |

- A first-page error replaces the list with the error state; an append error shows a footer row with **Retry** and keeps loaded results.
- Save/remove are local and work offline. A database failure shows a snackbar ("Couldn't save the paper") and changes nothing else.
- OkHttp timeouts: connect 10 s, read 20 s.
- `HttpLoggingInterceptor` is added only in debug builds and redacts the `api_key` query parameter. The key never appears in error messages or logs.

## 8. Testing

Approach: test-first for all production code; **fakes, no mocking library**. `core/testing` provides `FakeSearchRepository`, `FakeLibraryRepository`, `FakeUserPreferencesRepository`, sample `Paper` data, and `MainDispatcherRule`.

| Module | Coverage | Tools |
|---|---|---|
| `core/network` | Request parameter building; parsing recorded OpenAlex JSON; abstract rebuilding; HTTP/timeout → `NetworkFailure` classification; key interceptor (user vs built-in) and log redaction | JUnit, MockWebServer, JSON fixtures in `src/test/resources` |
| `core/database` | Transactional insert, duplicate no-op, author order, cascade delete, remove + restore preserves `id` and `saved_at`, id flow | Room in-memory on Robolectric |
| `core/data` | `SearchQuery` → request parameters for every combination; DTO → model mapping incl. missing fields and DOI normalization; `NetworkFailure` → `SearchError`; `UserApiKeySource` implementation; `PagingSource` pages, end of results, errors | JUnit, `paging-testing` (`TestPager`) |
| `feature/*` ViewModels | Debounce with virtual time, IME immediate search, chip changes, `inLibrary` combination, `SavedStateHandle` restore, error → state, remove + Undo, settings save/reset | `kotlinx-coroutines-test`, Turbine, `asSnapshot()` |
| `feature/*` screens | Each state renders; tapping a result opens the sheet; Save toggles to Remove; Retry and Clear filters invoke callbacks | Compose UI tests on Robolectric |
| Screenshots | Every screen and state × light/dark × English LTR/Arabic RTL | Roborazzi; baselines committed |

### CI

GitHub Actions workflow on push and pull request: assemble debug, run all JVM tests, `verifyRoborazzi`, Android Lint, and Spotless (ktlint) check. CI builds with no `OPENALEX_API_KEY`; nothing in CI calls the real API.

## 9. Acceptance criteria

1. Fresh install opens on the empty Library; **Go to Search** reaches Search.
2. Typing a query shows results after the debounce; scrolling loads further pages until the end.
3. Each sort option and year filter changes the request as specified in §5.1 (verified by tests) and the visible results.
4. Tapping a result opens the preview sheet; **Save** makes the "In library" badge appear in the list and the paper appear in Library, and this works in airplane mode.
5. Library works fully offline; swipe-remove + **Undo** restores the paper in its original position.
6. Each error in §7 shows the specified message and action.
7. Setting a user API key switches the status line and the key used by requests; **Reset** reverts to built-in.
8. Switching to العربية mirrors the whole UI with Arabic strings; paper content stays in its own direction.
9. CI is green: tests, screenshot verification, Lint, Spotless.
10. The README shows screenshots in light/dark and English/Arabic, the module graph, and setup instructions for `OPENALEX_API_KEY`.

## 10. Risks

| Risk | Mitigation |
|---|---|
| Hilt/KSP/Room/Roborazzi not yet compatible with AGP 9.2.1 or Kotlin 2.2.10 | Verified as the first plan task; adjust versions before building modules |
| OpenAlex changes its key or rate-limit policy | Key handling is isolated in the interceptor and `UserApiKeySource` |
| A key built into the APK can be extracted | Accepted for a portfolio app; revisit (e.g. a proxy) if Hashiya becomes a product |
| Screenshot tests are flaky across machines | Run Roborazzi only on Robolectric in CI with a pinned JDK and fonts bundled in the app |
