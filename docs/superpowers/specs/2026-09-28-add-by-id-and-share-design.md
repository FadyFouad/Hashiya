# Sub-project 2: Add by DOI / arXiv ID + Android Share — Design

- **Date:** 2026-09-28
- **Status:** Awaiting review
- **Scope:** Second of six MVP sub-projects for Hashiya (builds on sub-project 1, merged in `3f721a9`)

## 1. Context

Hashiya's core loop is **Discover → Save → Read → Extract → Compare → Cite**. Sub-project 1 delivered OpenAlex keyword search, a preview sheet, an offline library and settings. This sub-project completes **Save**: a user can add a specific paper by its DOI or arXiv ID, or by sharing a page from the browser with Android Share. Metadata comes from OpenAlex, as before.

The same standards as sub-project 1 apply: modular architecture and dependency rules, English and Arabic with full RTL, TDD with hand-written fakes, Roborazzi screenshots with baselines recorded on CI Linux, and a green CI.

### Decisions made during brainstorming

| Topic | Decision |
|---|---|
| Shared links | Handle links and text that contain an ID (doi.org, arXiv abs/pdf, publisher pages with a DOI in the address, text containing a DOI). Anything else falls back to a keyword search for the page title. |
| After a paper is found | Show the preview first; the user taps Save. |
| Where IDs are typed | The Search box recognizes IDs; the Library gets an **Add paper** button that opens Search with the keyboard up. No separate screen. |
| arXiv resolution | OpenAlex only, two tries, plus a title cross-check against the arXiv API in the fallback path. |
| Parser | Split into a strict parser for typed input and a lenient extractor for shares. |
| arXiv API unreachable during the cross-check | Show the error with Retry; never show an unverified match. |
| OpenAlex 400 for a well-formed ID | Treated as "not found", not as an error. |

### Live findings that shaped the design (queried 2026-09-28)

| Check | Result |
|---|---|
| `GET /works/doi:<doi>` and `/works/https://doi.org/<doi>` | Work returned for existing DOIs; 404 for unknown ones |
| `GET /works/doi:10.48550/arXiv.<id>` | 200 for `2401.00001`, `2310.06825`, `2005.14165`; **404** for `1706.03762` and `1810.04805`, although OpenAlex has both papers |
| Stored arXiv landing pages | Mostly `http://arxiv.org/abs/<id>`; sometimes `https://…`, `…/pdf/<id>v5`, or `https://doi.org/10.48550/arxiv.<id>` (the latter is how "Attention Is All You Need" is stored) |
| `abs` landing page stored only with a version suffix | 3 of 555 sampled arXiv works (~0.5%); a filter with a version does not match unversioned records |
| Fallback filter for `1810.04805` | Exactly one result, and it is **wrong** ("AI-Assisted Pipeline for Dynamic Generation…"); arXiv's own title is "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding" |
| DOIs with `<`, `>`, `;`, `(`, `)` and a second `/` in `/works/{id}` | 200 whether sent raw, partly encoded, or fully encoded (`/` → `%2F`) |

## 2. Goals and non-goals

### Goals

1. Typing or pasting a DOI, arXiv ID, or supported link into Search shows that paper's preview, from which it can be saved or removed.
2. Sharing a page from a browser opens Hashiya on the matching paper's preview, or on a keyword search for the page title when the page has no ID.
3. The Library has an **Add paper** button that opens Search ready for input.
4. arXiv IDs resolve through OpenAlex even when OpenAlex's arXiv DOI record is missing, and a wrong OpenAlex match is never presented as the paper.
5. Every lookup state (looking, found, not found, error) is clear, localized, and tested, including screenshots in both languages and themes.

### Non-goals

- Adding papers that OpenAlex does not have (manual entry).
- Using the arXiv API as a metadata source (it is used only to check titles).
- Resolving publisher pages without an ID in the address (e.g. an IEEE Xplore `ieeexplore.ieee.org/document/<n>` page) other than through the page-title search.
- PDFs, notes, collections, library full-text search (later sub-projects).
- Sharing files or images into Hashiya; only `text/plain` shares.

## 3. Identifier parsing (`core/model`)

```kotlin
sealed interface PaperIdentifier {
    data class Doi(val value: String) : PaperIdentifier   // normalized via normalizeDoi(), e.g. "10.1038/nature14539"
    data class Arxiv(val id: String) : PaperIdentifier    // no version, no subject class: "1706.03762", "hep-th/9901001", "math/0309136"
}

/** Strict, for the Search box: matches only when the whole trimmed input is an ID or a supported link. */
fun parsePaperIdentifier(text: String): PaperIdentifier?

/** Lenient, for shares: finds the first supported link or DOI anywhere in the text. */
fun extractPaperIdentifier(text: String): PaperIdentifier?
```

**Recognized forms** (arXiv is checked before DOI):

- **arXiv**
  - `arxiv.org/abs/<id>` and `arxiv.org/pdf/<id>`, with or without scheme, `www.`, a `vN` suffix, or `.pdf`.
  - `arXiv:<id>`, case-insensitive.
  - A bare new-style ID (`YYMM.NNNN` or `YYMM.NNNNN`, optional `vN`) — only as the whole input (strict) or as a standalone token delimited by whitespace or string boundaries (lenient). Never inside a URL or a DOI.
  - An old-style ID `archive[.SUBJ]/YYMMNNN` (e.g. `hep-th/9901001`, `math.GT/0309136`), optional `vN`, under the same token rule. The subject class is dropped (`math.GT/0309136` → `math/0309136`): arXiv and OpenAlex resolve only the form without it (verified 2026-09-28).
  - The arXiv DOI `10.48550/arXiv.<id>`, case-insensitive, in any DOI form below. It becomes `Arxiv(id)` so it gets the arXiv fallback.
- **DOI**
  - `doi.org/<doi>` and `dx.doi.org/<doi>` links, and a `doi:` prefix.
  - Publisher URLs whose path contains a DOI (e.g. `/doi/10.xxxx/…`, `/doi/full/10.xxxx/…`, `/article/10.xxxx/…`): query and fragment removed, percent-decoding applied.
  - Nature Portfolio article links: `nature.com/articles/<id>` (with or without `www.`, and an optional `.pdf` suffix) → `10.1038/<id>` — covers Nature (`nature14539`), Scientific Reports (`s41598-…`), Nature Communications (`s41467-…`) and Nature news (`d41586-…`).
  - A bare `10.NNNN/…` DOI: as the whole input (strict) or anywhere in the text (lenient).
  - Every DOI is passed through `normalizeDoi()`.
- **Cleanup**
  - Trailing `.`, `,`, `;`, `:`, `]`, `>`, quotes are removed from DOIs found inside text.
  - A trailing `)` is removed only when the DOI's parentheses are unbalanced.
  - A version suffix is removed from arXiv IDs.

**Strict vs lenient**

- Typed text that merely contains a DOI stays a normal search: `a study of 10.1038/nature14539` → `parsePaperIdentifier` returns null, `extractPaperIdentifier` returns the DOI.
- Shared text like `Paper title https://arxiv.org/abs/1706.03762` → the extractor finds the link.
- Only the first 2,000 characters of shared text are examined; with several IDs, the first link wins, then the first bare ID.
- Pasting a single link with no DOI or arXiv ID in it into Search runs no keyword search and no lookup; it shows "No DOI or arXiv ID in this link" instead. Shares are unaffected: a shared page with no ID still falls back to a keyword search for the page title.

## 4. Lookup

### 4.1 Network (`core/network`)

- `OpenAlexDataSource.getWork(id: String): NetworkWork?` — `GET /works/{id}` with the existing `select` fields. Returns `null` on HTTP 404 and on HTTP 400 (callers only pass well-formed IDs); throws `NetworkException` for everything else, as today. `{id}` uses Retrofit's default `@Path` encoding (encodes `/`, `#`, `?`, `<`, `>`, `;`).
- `OpenAlexDataSource.findWorks(filter: String, perPage: Int): NetworkWorksResponse` — `GET /works?filter=…&per_page=…` with the same `select` fields and API-key handling.
- New `ArxivDataSource.title(id: String): String?` — `GET https://export.arxiv.org/api/query?id_list=<id>`. Returns the first entry's title with whitespace collapsed and XML entities decoded, or `null` when the feed has no entry for that ID. Throws `NetworkException` when unreachable, on 5xx, or when the XML can't be read. No API key is sent; requests carry a descriptive `User-Agent`.

### 4.2 Repository (`core/data`)

```kotlin
interface PaperLookupRepository {
    suspend fun lookup(identifier: PaperIdentifier): LookupResult
}

sealed interface LookupResult {
    data class Found(val paper: Paper) : LookupResult
    data class NotFound(val arxivTitle: String?) : LookupResult
    data class Failed(val error: SearchError) : LookupResult
}
```

- **DOI:** `getWork("doi:<doi>")` → `Found` or `NotFound(null)`.
- **arXiv:**
  1. `getWork("doi:10.48550/arXiv.<id>")` → if found, `Found` (trusted: OpenAlex matched the arXiv DOI itself).
  2. Otherwise one `findWorks(filter, perPage = 2)` where `filter = "locations.landing_page_url:" + joined with "|"` of: `http://arxiv.org/abs/<id>`, `https://arxiv.org/abs/<id>`, the same two with `v1`–`v5`, and `https://doi.org/10.48550/arxiv.<id>`.
  3. Zero results → `NotFound(arxivTitle)`; more than one distinct work → `NotFound(arxivTitle)`.
  4. Exactly one result → fetch `ArxivDataSource.title(id)` and compare with the OpenAlex title after normalization (lowercase, letters and digits only, collapsed spaces). Equal → `Found`; different → `NotFound(arxivTitle)`; arXiv says the ID doesn't exist → `NotFound(null)`.
  5. Where the arXiv title is needed for `NotFound` in steps 3 (zero or several results), it is fetched too; if that fetch fails, `NotFound(null)`.
- Any `NetworkException` from OpenAlex → `Failed(failure.asSearchError())`. Any `ArxivDataSource` failure during the step-4 cross-check → `Failed(SearchError.ServiceUnavailable)` (OpenAlex has just answered, so the device is online and "Can't reach OpenAlex" would be wrong).
- Mapping to `Paper` reuses `NetworkWork.asPaper()`.

## 5. Search screen in ID mode (`feature/search`)

- The submitted text (after the 300 ms delay, or immediately on the keyboard Search key, a suggestion, or a route query) is run through `parsePaperIdentifier`. Non-null → **ID mode**; otherwise unchanged keyword search.
- In ID mode the keyword query is `null` (no search request), and the filter chips and result count are hidden.
- `SearchViewModel` gains `PaperLookupRepository` and exposes `lookupState: StateFlow<LookupUiState?>`:
  - `null` — not in ID mode.
  - `Looking(identifier)` — one-row loading placeholder and "Looking up DOI 10.1038/nature14539…" / "Looking up arXiv 1706.03762…".
  - `Found(paper)` — the paper's preview shown **in the screen body** (`PaperPreviewContent`): title, all authors, venue, year, citations, abstract, **Save to library** / **Remove from library**, **Open DOI**. "In library" comes from the existing `savedIds`.
  - `NotFound(kind, arxivTitle, pageTitle)` — "No paper found for this DOI" / "…for this arXiv ID". A button **Search for "<title>"** runs a keyword search with `arxivTitle`, or `pageTitle` when there is no arXiv title; no button when neither exists.
  - `Failed(error)` — the existing error state: Retry re-runs the lookup; `InvalidUserKey` offers Open Settings.
- The newest lookup cancels the previous one. Retry and an API-key change re-run the current lookup. Save/Remove reuse `onToggleSave` and its "Couldn't save / Couldn't remove the paper" messages. Restoring after process death restores the text and re-runs the lookup.
- The search field hint becomes "Search, or paste a DOI, arXiv ID or link" / "ابحث، أو الصق DOI أو معرّف arXiv أو رابطًا".

## 6. Share and Add paper entry points

### 6.1 Search route arguments (`feature/search`)

```kotlin
@Serializable
data class SearchRoute(
    val query: String? = null,       // submitted immediately, skipping the delay
    val pageTitle: String? = null,   // shared page title, for the not-found fallback button
    val focusSearch: Boolean = false,
    val note: String? = null,        // a SearchNote name (NoIdInShare, NothingInShare); plain strings keep navigation arguments simple
)
```

- `SearchViewModel` applies the arguments once, and only when there is no restored search text.
- Share and Add paper always open a **fresh** Search with their arguments; tapping the Search tab keeps today's behavior of restoring the previous search. The navigation that opens it pops back to the Library with `saveState = true`, as the tab navigation does; without it the Library tab stops responding afterwards (found while verifying the plan).

### 6.2 Receiving shares (`app`)

- `MainActivity` declares an intent filter for `android.intent.action.SEND`, category `DEFAULT`, `mimeType="text/plain"`.
- `EXTRA_TEXT` is the shared text (usually the link); `EXTRA_SUBJECT` (or `EXTRA_TITLE`) is the page title. Shares are handled in `onCreate` and `onNewIntent`.
- A pure function `shareToSearchRoute(text: String?, subject: String?): SearchRoute`:
  1. `extractPaperIdentifier(text)` finds an ID → `SearchRoute(query = canonical, pageTitle = subject)` where canonical is the bare DOI or `arXiv:<id>`.
  2. No ID, non-blank subject → `SearchRoute(query = subject, note = NoIdInShare)`: keyword search with the note "No DOI or arXiv ID in the shared link — searching by page title."
  3. No ID, no subject → `SearchRoute(note = NothingInShare)`: "Couldn't find a paper in what you shared."
- Hashiya opens over the browser (standard share-target behavior); Back returns through the Library to the browser.

### 6.3 Add paper button (`feature/library`)

- A floating **Add paper** button (bottom end, mirrored in RTL) on the Library screen, in both the empty state and the list state.
- It navigates to `SearchRoute(focusSearch = true)`; Search focuses the field and opens the keyboard once.

## 7. Errors

| Cause | Result | User sees |
|---|---|---|
| OpenAlex 404 / 400 for a well-formed ID | `NotFound` | "No paper found…" (+ title button when available) |
| OpenAlex offline / 429 / 5xx / bad key / unreadable | `Failed` via existing mapping | Existing error state with Retry or Open Settings |
| arXiv API unreachable or unreadable during the cross-check | `Failed(ServiceUnavailable)` | Error state with Retry |
| arXiv says the ID doesn't exist | `NotFound(null)` | "No paper found for this arXiv ID" |
| Share without text | `NothingInShare` note | "Couldn't find a paper in what you shared" |

The API key is still never logged or placed in messages; the arXiv client sends no key.

## 8. Testing

TDD with hand-written fakes, as in sub-project 1.

| Module | Coverage |
|---|---|
| `core/model` | Table tests for both parsers: `math.GT/0309136`, `hep-th/9901001v2`, `arxiv.org/pdf/2401.00001v2.pdf`, `ARXIV:2401.00001`, `10.48550/ARXIV.1706.03762` (→ arXiv), `https://doi.org/10.1038/NATURE14539`, Wiley/ACM `/doi/…` links with `?query#fragment`, Springer `/article/10.1007/…`, SICI DOIs with `<>;` and a second `/`, balanced vs unbalanced `)`, trailing punctuation; must-not-match cases: a bare new-style ID inside a URL or DOI, keywords, and `a study of 10.1038/nature14539` (strict → null, lenient → DOI). |
| `core/network` | MockWebServer: `getWork` 200/404/400; path encoding for real SICI DOIs and a DOI containing `#`; the exact `findWorks` filter and `per_page`; `ArxivDataSource` parsing (fixture XML with entities, line breaks, empty feed); unreachable server. |
| `core/data` | `PaperLookupRepository` with fakes: DOI found / not found; arXiv DOI hit; fallback single match with matching title → Found; the BERT case (mismatched title) → `NotFound("BERT: …")`; two distinct works → NotFound; arXiv unreachable during the cross-check → Failed; 400 → NotFound. |
| `feature/search` | ViewModel: ID mode on/off, no search request in ID mode, cancellation, Retry and key change re-run the lookup, route arguments applied once and not over restored text, page-title fallback, notes. UI tests for every lookup state; screenshots (looking, found, not found with title button, error) × light/dark × English/Arabic. |
| `feature/library` | Add paper opens Search with focus. Library screenshots change and are re-recorded on Linux in the same task. |
| `app` | `shareToSearchRoute` table tests; Robolectric test that a `text/plain` `ACTION_SEND` resolves to `MainActivity`. |

## 9. Acceptance criteria

1. Pasting `10.1038/nature14539` into Search shows the "Deep learning" preview; Save adds it to the Library.
2. Pasting `1706.03762`, `arXiv:2401.00001` or `https://arxiv.org/pdf/2005.14165v4` shows the right paper.
3. Pasting `1810.04805` shows "No paper found for this arXiv ID" with **Search for "BERT: …"**, which runs a keyword search.
4. Typing `a study of 10.1038/nature14539` runs a normal keyword search.
5. Sharing from Chrome an arXiv `abs` page, an arXiv `pdf`, a `doi.org` link and a Wiley or ACM article opens Hashiya on the right preview.
6. Sharing a nature.com article opens the paper's preview; sharing an IEEE Xplore article (no ID in the address) opens a keyword search for the page title with the explanatory note.
7. In airplane mode, a lookup shows the offline error with Retry; Retry after reconnecting finds the paper.
8. The Library's Add paper button opens Search with the keyboard up and the new hint.
9. Pasting a link with no DOI or arXiv ID into Search shows "No DOI or arXiv ID in this link" instead of searching the URL as text.
10. Everything above works in Arabic with RTL; English titles stay left-to-right.
11. CI is green, with new screenshot baselines recorded on Linux.

## 10. Risks

| Risk | Mitigation |
|---|---|
| OpenAlex's arXiv data is wrong or incomplete | Trusted path only for the arXiv DOI; fallback verified against arXiv's title; mismatches become NotFound with a title search |
| Titles differ in formatting between OpenAlex and arXiv (LaTeX, punctuation) causing false NotFound | Normalization to letters and digits; NotFound still offers the title search, so the user reaches the paper |
| arXiv API rate guidance (one request per 3 s) | Called at most once per lookup, only when the arXiv DOI lookup fails |
| Share intents vary between browsers | Only `text/plain` handled; parser is lenient; unknown shares fall back to title search or a clear message |
