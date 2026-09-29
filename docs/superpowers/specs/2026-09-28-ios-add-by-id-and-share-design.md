# iOS sub-project 2: Add by DOI / arXiv ID / link + Share Extension — Design

- **Date:** 2026-09-28
- **Status:** Awaiting review
- **Scope:** iOS counterpart of Android sub-project 2 (`2026-09-28-add-by-id-and-share-design.md`), matching the Android behaviour merged on `main` at `67e850c`. Builds on `2026-09-28-ios-foundation-openalex-search-design.md` ("spec 1"), whose platform decisions, package rules, string conventions, testing and CI apply unchanged.

## 1. Context

Spec 1 delivers OpenAlex keyword search, a preview sheet, an offline Library and Settings. This spec completes **Save**: a user adds a specific paper by typing or pasting its DOI, arXiv ID or a link into Search, from the Library's **Add paper** button, or by sharing a page to Hashiya from Safari or any app through a Share Extension. Metadata comes from OpenAlex; arXiv's own API is used only to check titles.

The Android app receives shares in its main activity and opens Search. On iOS, the Share Extension `HashiyaShare` resolves the paper and saves it inside the share sheet, without opening the app (§8, §14).

### Decisions

| Topic | Decision |
|---|---|
| Parser | A port of the merged Android parser in `HashiyaModel`: strict for the Search box, lenient for shared text (first 2,000 characters). Every rule in §3 and every Android test case in §3.4. |
| DOI forms | Bare, `doi:`, `doi.org` / `dx.doi.org` links, publisher paths. `.pdf`, `/full`, `/abstract`, `/epdf`, `/pdf`, `/fulltext` and bioRxiv/medRxiv `vN` / `.full` are dropped only in publisher paths. Nature `nature.com/articles/<id>` → `10.1038/<id>`. |
| arXiv forms | New-style IDs 0704–1412 with 4 digits, 1501 on with 5, optional `vN`; old-style with the subject class dropped; `arXiv:` prefix; `abs`, `pdf` and `html` links; `10.48550/arXiv.<id>`. |
| Shared text | Links glued to text (Markdown `[t](url)`, `Link:https://…`) are found; trailing junk trimmed, `)` only when unbalanced; `looksLikeLink` detects a lone link. |
| Lookup clients | `OpenAlexLookupClient.work(id:)` (404/400 → `nil`; path-safe encoding keeps `#` and `;`) and `works(filter:perPage:)`. `ArxivTitleClient` with its own URLSession, User-Agent `Hashiya-iOS (https://github.com/FadyFouad/Hashiya)`, never the OpenAlex key, cancelled with its lookup. |
| Lookup repository | `PaperLookupRepository.lookup(_:) async -> LookupResult` (`.found` / `.notFound(arxivTitle:)` / `.failed(SearchError)`). DOI direct. arXiv: arXiv DOI first, then a landing-page filter with `per_page=2` (more than one distinct work → not found), then a title cross-check with the arXiv API (normalized, accent-insensitive). arXiv failure during the cross-check → `serviceUnavailable`. |
| Search ID mode | Looking ("Looking up DOI …" / arXiv) + skeleton; Found (preview inline, Save + Open DOI); Not found ("No paper found for this DOI" / "…arXiv ID", plus **Search for "…"** when arXiv's title is known, shortened to 60 characters and isolated so it stays left-to-right in Arabic); Failed + Retry. A pasted link with no ID → "No DOI or arXiv ID in this link", no request. |
| Keyword queries | OpenAlex keyword queries drop Arabic tashkeel and tatweel only (`withoutArabicMarks`); a query of only marks counts as empty. Search hint "Search, or paste a DOI, arXiv ID or link". |
| Library | An **Add paper** button opens Search focused; the list's bottom padding keeps the button off the last row. |
| Share Extension | Target `HashiyaShare`, activated for web URLs and text; reads a URL or text (plain or attributed) and a page title (capped at 300 characters; a title that is only a link is ignored). Lenient parser → lookup → sheet: Looking → preview with **Save to library** / Not found / Error + Retry; **Done** closes. No ID → "No DOI or arXiv ID on this page" + the page title. |
| Shared storage | App Group `group.com.etatech.hashiya` holds the GRDB database (WAL); the keychain access group holds the user key. Both were set up in spec 1. |
| Extension scope | Out of scope: keyword search by page title, and a second share while the sheet is open. |
| Tests | Ported parser tables and lookup scenarios (including the BERT wrong-work case and the title check), `URLProtocolStub` for OpenAlex and arXiv, extension view model states, an XCUITest sharing a URL through the share sheet, snapshots of every lookup state and of the extension sheet. |

### Live findings behind the arXiv flow (Android spec, queried 2026-09-28)

| Check | Result |
|---|---|
| `GET /works/doi:<doi>` | Work for existing DOIs; 404 for unknown ones |
| `GET /works/doi:10.48550/arXiv.<id>` | 200 for `2401.00001`, `2310.06825`, `2005.14165`; **404** for `1706.03762` and `1810.04805`, although OpenAlex has both papers |
| Stored arXiv landing pages | Mostly `http://arxiv.org/abs/<id>`; sometimes `https://…`, versioned, or `https://doi.org/10.48550/arxiv.<id>` |
| Landing-page filter for `1810.04805` | Exactly one result, and it is **wrong** ("AI-Assisted Pipeline for Dynamic Generation…"); arXiv's title is "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding" |
| DOIs with `<`, `>`, `;`, `(`, `)` and a second `/` in `/works/{id}` | 200 whether sent raw, partly or fully percent-encoded |

## 2. Goals and non-goals

### Goals

1. Typing or pasting a DOI, arXiv ID or supported link into Search shows that paper's preview, from which it can be saved or removed.
2. Sharing a page to Hashiya shows the matching paper in the share sheet, where it can be saved to the library.
3. The Library has an **Add paper** button that opens Search ready for input.
4. arXiv IDs resolve through OpenAlex even when OpenAlex's arXiv DOI record is missing, and a wrong OpenAlex match is never presented as the paper.
5. Every lookup state is clear, localized and covered by tests and snapshots in both languages and themes.

### Non-goals

- Adding papers OpenAlex doesn't have (manual entry); the arXiv API as a metadata source.
- Resolving publisher pages without an ID in the address (e.g. `ieeexplore.ieee.org/document/<n>`).
- Keyword search by page title from a share; handling a second share while the extension sheet is open.
- Sharing files or images into Hashiya.

## 3. Identifier parsing (`HashiyaModel`)

File `ios/HashiyaKit/Sources/HashiyaModel/PaperIdentifier.swift`.

```swift
public enum PaperIdentifier: Equatable, Hashable, Sendable {
    case doi(String)      // normalized by normalizeDOI, e.g. "10.1038/nature14539"
    case arxiv(String)    // no version, no subject class: "1706.03762", "hep-th/9901001", "math/0309136"
}

/// Strict, for the Search box: the whole trimmed text must be an identifier or a supported link.
public func parsePaperIdentifier(_ text: String) -> PaperIdentifier?
/// Lenient, for shares: the first supported link in the text, otherwise the first bare identifier.
public func extractPaperIdentifier(_ text: String) -> PaperIdentifier?
/// True when the trimmed text is a single link-shaped token.
public func looksLikeLink(_ text: String) -> Bool
```

### 3.1 Porting rules

- **ASCII classes.** Write digit classes as `[0-9]`, never `\d`: Swift `Regex` and `NSRegularExpression` treat `\d` as any Unicode digit, the Android patterns don't, so `١٧٠٦.٠٣٧٦٢` must not match. Where a pattern below is marked case-insensitive, only ASCII letters fold (write `[A-Za-z]` or lowercase the ASCII input; avoid Unicode case folding, which maps `K` (Kelvin sign) to `k`).
- **Whitespace.** "ASCII whitespace" is space, tab, LF, VT, FF, CR. "Whitespace" alone means Unicode whitespace (`Character.isWhitespace`).
- **Trim** means removing leading and trailing Unicode whitespace.
- Patterns marked *whole* must match the entire string; *first* means the first match anywhere; *suffix* matches at the end.

### 3.2 Patterns

In the table, `\|` stands for a literal `|` (alternation).

| Name | Pattern | Mode |
|---|---|---|
| `MONTH` | `(?:0[1-9]\|1[0-2])` | — |
| `NEW4` | `(?:07(?:0[4-9]\|1[0-2])\|(?:0[89]\|1[0-4])MONTH)\.[0-9]{4}` | 0704–0712, 0801–1412 with 4 digits |
| `NEW5` | `(?:1[5-9]\|[2-9][0-9])MONTH\.[0-9]{5}` | 1501 on with 5 digits |
| `NEW` | `(?:NEW4\|NEW5)(?:v[0-9]+)?` | — |
| `OLD` | `[a-z]+(?:-[a-z]+)?(?:\.[a-z]{2})?/[0-9]{7}(?:v[0-9]+)?` | — |
| `ANY` | `(?:NEW\|OLD)` | — |
| `ARXIV_URL` | `(?:https?://)?(?:www\.\|export\.)?arxiv\.org/(?:abs\|pdf\|html)/(ANY)(?:\.pdf)?/?` | whole, case-insensitive |
| `ARXIV_PREFIXED` | `arxiv:(ANY)` | whole, case-insensitive |
| `BARE_ARXIV` | `ANY` | whole, case-insensitive |
| `ARXIV_DOI` | `10\.48550/arxiv\.(ANY)` | whole, case-insensitive |
| `BARE_DOI` | `10\.[0-9]{4,9}/` followed by one or more non-ASCII-whitespace characters | whole |
| `DOI_IN_PATH` | `10\.[0-9]{4,9}/.+` | first |
| `VIEW_SEGMENT` | `/(?:full\|abstract\|epdf\|pdf\|fulltext)` | suffix, case-insensitive |
| `PDF_SUFFIX` | `\.pdf` | suffix, case-insensitive |
| `PREPRINT_SUFFIX` | `(?:v[0-9]+)?(?:\.full)?` | suffix, case-insensitive |
| `SPACE_AFTER_PREFIX` | `arxiv:` or `doi:` at a word boundary (start of text, or after a character that is not a letter, digit or `_`), followed by one or more ASCII whitespace | all occurrences, case-insensitive; replaced by the prefix alone |
| `URL_START` | `(https?://\|www\.\|(?:export\.)?arxiv\.org/\|(?:dx\.)?doi\.org/)` | first, case-insensitive |
| `NATURE_ARTICLE` | `articles/([A-Za-z0-9][A-Za-z0-9.-]*)` | whole, case-insensitive |

Constants: leading junk `(`, `[`, `"`, `'`, `<`; trailing junk `.`, `,`, `;`, `:`, `!`, `?`, `"`, `'`, `>`, `]`; DOI hosts `doi.org`, `dx.doi.org`, `www.doi.org`; Nature hosts `nature.com`, `www.nature.com`; shared-text limit 2,000 characters.

### 3.3 Algorithm

- **`parsePaperIdentifier(text)`**: trim, apply `SPACE_AFTER_PREFIX`; empty or containing any whitespace → `nil`; else `parseToken`.
- **`extractPaperIdentifier(text)`**: take the first 2,000 characters, apply `SPACE_AFTER_PREFIX`, split on runs of ASCII whitespace, drop empty tokens. Return the first non-nil `parseToken(t)` among tokens where `isURL(t)`; if none, the first non-nil `parseToken(t)` among the other tokens.
- **`looksLikeLink(text)`**: trimmed text non-empty, without whitespace, and `isURL`.
- **`isURL(token)`**: drop leading junk, lowercase; true if it contains `://` or starts with `www.`, `arxiv.org/`, `doi.org/` or `dx.doi.org/`. (A bare `nature.com/…` is not a URL.)
- **`parseToken(raw)`**: `token = trimTrailingJunk(raw without leading junk)`; empty → `nil`.
  - If `isURL(token)`: cut the token at the first `URL_START` match (keep the token if none), `trimTrailingJunk` again, and return `parseURL`.
  - Else `ARXIV_PREFIXED` → `.arxiv(canonicalArxiv(group 1))`; else `BARE_ARXIV` → `.arxiv(canonicalArxiv(token))`; else strip a case-insensitive `doi:` prefix and, if the rest matches `BARE_DOI`, return `doiIdentifier(rest)`; else `nil`.
- **`parseURL(url)`**: cut at the first `#`, then at the first `?`. If `ARXIV_URL` matches → `.arxiv(canonicalArxiv(group 1))`. Otherwise take the part after the first `://` (the whole string if there is none); host = up to the first `/`, lowercased; path = after the first `/` (empty if none), percent-decoded. Then:
  - host in DOI hosts → `doiIdentifier(path)` (suffixes kept);
  - host in Nature hosts → `natureDOI(path)` then `doiIdentifier`;
  - otherwise the first `DOI_IN_PATH` match, passed through `dropPublisherSuffix`, then `doiIdentifier`; no match → `nil`.
- **`natureDOI(path)`**: remove trailing `/`s, remove `PDF_SUFFIX`; if `NATURE_ARTICLE` matches → `"10.1038/" + group 1`.
- **`dropPublisherSuffix(doi)`**: remove trailing `/`s, remove `VIEW_SEGMENT`, then `PDF_SUFFIX`; if the result starts with `10.1101/` (bioRxiv, medRxiv), also remove `PREPRINT_SUFFIX`. (`…123456v1.full.pdf` → `…123456`.)
- **`doiIdentifier(raw)`**: remove trailing `/`s, `trimTrailingJunk`, `normalizeDOI` (spec 1 §4); `nil` → `nil`; must match `BARE_DOI`; if `ARXIV_DOI` matches → `.arxiv(canonicalArxiv(group 1))`; else `.doi(value)`.
- **`canonicalArxiv(raw)`**: lowercase; remove a `.pdf` suffix; remove a trailing `v[0-9]+`; replace a leading `([a-z]+(?:-[a-z]+)?)\.[a-z]{2}/` with `\1/` (`math.GT/0309136` → `math/0309136`: arXiv and OpenAlex resolve only the form without the subject class).
- **`trimTrailingJunk(value)`**: while the last character is trailing junk, or is `)` and the value has fewer `(` than `)`, drop it.
- **Percent-decoding** (path only): each `%` followed by two hex digits becomes that byte; every other character is kept as its UTF-8 bytes; the bytes are decoded as UTF-8 with invalid sequences replaced by U+FFFD. (`String.removingPercentEncoding` returns `nil` on any invalid sequence, so it can't be used as is.)

### 3.4 Test tables (ported from `PaperIdentifierTest.kt`)

`sici` = `10.1002/(sici)1099-1212(199901/02)9:1<8::aid-oa453>3.0.co;2-z`. `D(x)` = `.doi(x)`, `A(x)` = `.arxiv(x)`, `nil` = no identifier. Each group is one parameterised `@Test`.

**Strict — DOIs**

| Input | Expected |
|---|---|
| `10.1038/nature14539` | D(`10.1038/nature14539`) |
| `  https://doi.org/10.1038/NATURE14539 ` | D(`10.1038/nature14539`) |
| `http://dx.doi.org/10.1038/nature14539` | D(`10.1038/nature14539`) |
| `doi:10.1038/nature14539` | D(`10.1038/nature14539`) |
| `DOI: 10.1038/nature14539` | D(`10.1038/nature14539`) |
| `https://onlinelibrary.wiley.com/doi/full/10.1002/anie.201915678?af=R#section` | D(`10.1002/anie.201915678`) |
| `https://dl.acm.org/doi/10.1145/3292500.3330701` | D(`10.1145/3292500.3330701`) |
| `https://link.springer.com/article/10.1007/s11263-015-0816-y` | D(`10.1007/s11263-015-0816-y`) |
| `https://doi.org/10.1002/(SICI)1099-1212(199901/02)9:1%3C8::AID-OA453%3E3.0.CO;2-Z` | D(sici) |
| `10.1038/nature14539.` | D(`10.1038/nature14539`) |

**Strict — publisher links drop PDF and view suffixes**

| Input | Expected |
|---|---|
| `https://link.springer.com/content/pdf/10.1007/s11263-015-0816-y.pdf` | D(`10.1007/s11263-015-0816-y`) |
| `https://onlinelibrary.wiley.com/doi/10.1002/anie.201915678/full` | D(`10.1002/anie.201915678`) |
| `https://onlinelibrary.wiley.com/doi/10.1002/anie.201915678/abstract` | D(`10.1002/anie.201915678`) |
| `https://onlinelibrary.wiley.com/doi/10.1002/anie.201915678/epdf` | D(`10.1002/anie.201915678`) |
| `https://example.org/10.1002/anie.201915678/PDF/` | D(`10.1002/anie.201915678`) |
| `https://www.biorxiv.org/content/10.1101/2020.01.01.123456v1` | D(`10.1101/2020.01.01.123456`) |
| `https://www.biorxiv.org/content/10.1101/2020.01.01.123456v2.full` | D(`10.1101/2020.01.01.123456`) |
| `https://www.medrxiv.org/content/10.1101/2020.01.01.123456v1.full.pdf` | D(`10.1101/2020.01.01.123456`) |

**Strict — doi.org links and plain DOIs keep their suffixes**

| Input | Expected |
|---|---|
| `https://doi.org/10.1000/xyz.pdf` | D(`10.1000/xyz.pdf`) |
| `https://doi.org/10.1000/abc/full` | D(`10.1000/abc/full`) |
| `doi:10.1000/xyz.pdf` | D(`10.1000/xyz.pdf`) |
| `10.1101/2020.01.01.123456v1` | D(`10.1101/2020.01.01.123456v1`) |

**Strict — arXiv IDs**

| Input | Expected |
|---|---|
| `1706.03762` | A(`1706.03762`) |
| `2401.00001v2` | A(`2401.00001`) |
| `ARXIV:2401.00001` | A(`2401.00001`) |
| `arXiv: 2401.00001` | A(`2401.00001`) |
| `https://arxiv.org/abs/1706.03762v5` | A(`1706.03762`) |
| `arxiv.org/pdf/2401.00001v2.pdf` | A(`2401.00001`) |
| `https://arxiv.org/html/2401.00001v2` | A(`2401.00001`) |
| `https://www.arxiv.org/abs/2310.06825` | A(`2310.06825`) |
| `http://export.arxiv.org/abs/hep-th/9901001v2` | A(`hep-th/9901001`) |
| `hep-th/9901001` | A(`hep-th/9901001`) |
| `math.GT/0309136` | A(`math/0309136`) |
| `10.48550/ARXIV.1706.03762` | A(`1706.03762`) |
| `https://doi.org/10.48550/arXiv.2310.06825` | A(`2310.06825`) |
| `10.48550/arXiv.math/0309136` | A(`math/0309136`) |

**Strict — only real new-style shapes**

| Input | Expected |
|---|---|
| `0704.0001` | A(`0704.0001`) |
| `1412.6980` | A(`1412.6980`) |
| `2401.00001` | A(`2401.00001`) |
| `1706.0376` | nil |
| `2401.0001` | nil |
| `0612.0001` | nil |
| `1412.69801` | nil |

**Strict — rejects everything else**

| Input | Expected |
|---|---|
| (empty) | nil |
| `   ` | nil |
| `machine learning` | nil |
| `a study of 10.1038/nature14539` | nil |
| `https://example.com/2401.00001` | nil |
| `https://arxiv.org/list/cs.LG/recent` | nil |
| `10.1038` | nil |
| `2401.001` | nil |
| `2023.12345` | nil |
| `1234` | nil |

**Strict — Nature article links**

| Input | Expected |
|---|---|
| `https://www.nature.com/articles/d41586-026-02937-z` | D(`10.1038/d41586-026-02937-z`) |
| `https://www.nature.com/articles/nature14539.pdf` | D(`10.1038/nature14539`) |
| `https://www.nature.com/articles/nature14539` | D(`10.1038/nature14539`) |
| `https://nature.com/articles/s41598-021-81234-5?error=cookies_not_supported` | D(`10.1038/s41598-021-81234-5`) |
| `https://www.nature.com/nature/volumes/620` | nil |
| `https://www.nature.com/subjects/physics` | nil |

**Strict — parentheses and brackets**

| Input | Expected |
|---|---|
| `10.1000/abc(1)` | D(`10.1000/abc(1)`) |
| `(10.1000/abc)` | D(`10.1000/abc`) |
| `<https://arxiv.org/abs/1706.03762>` | A(`1706.03762`) |

**Lenient — IDs inside text**

| Input | Expected |
|---|---|
| `Attention Is All You Need https://arxiv.org/abs/1706.03762` | A(`1706.03762`) |
| `a study of 10.1038/nature14539.` | D(`10.1038/nature14539`) |
| `(see doi: 10.1000/xyz123)` | D(`10.1000/xyz123`) |
| `Ref: 10.1000/abc(1), page 3` | D(`10.1000/abc(1)`) |
| `SICI <sici> is old` (with the sici value) | D(sici) |
| `see 2401.00001, it is good` | A(`2401.00001`) |
| `check https://example.com/page and 10.1000/xyz` | D(`10.1000/xyz`) |
| `Check this out: https://arxiv.org/abs/2401.00001v2` | A(`2401.00001`) |
| `Check this out: [Attention Is All You Need](https://arxiv.org/abs/1706.03762)` | A(`1706.03762`) |
| `[Deep learning](https://doi.org/10.1038/nature14539)` | D(`10.1038/nature14539`) |
| `Link:https://arxiv.org/abs/2401.00001` | A(`2401.00001`) |

**Lenient — the first link wins**

| Input | Expected |
|---|---|
| `https://arxiv.org/abs/2401.00001 also 10.1038/nature14539` | A(`2401.00001`) |
| `10.1038/nature14539 then https://arxiv.org/abs/2401.00001` | A(`2401.00001`) |

**Lenient — Nature, and text without IDs**

| Input | Expected |
|---|---|
| `Read this https://www.nature.com/articles/nature14539 now` | D(`10.1038/nature14539`) |
| `https://example.com/2401.00001` | nil |
| `[x](https://example.com/2401.00001)` | nil |
| `nothing to see here` | nil |
| (empty) | nil |

**`looksLikeLink`**

| Input | Expected |
|---|---|
| `https://example.com/some/article` | true |
| `  www.example.com  ` | true |
| `10.1038/x` | false |
| `hello world` | false |
| `https://a.b c` | false |

**Shared-text limit**: 2,000 × `x` + ` 10.1038/nature14539` → nil; 1,900 × `x` + ` 10.1038/nature14539` → D(`10.1038/nature14539`).

**Added on iOS**: `١٧٠٦.٠٣٧٦٢` (Arabic-Indic digits) → nil in both parsers.

### 3.5 `withoutArabicMarks`

In `HashiyaModel/SearchableText.swift` (spec 3 adds `searchableText` to the same file):

```swift
/// The text without Arabic diacritics and tatweel; everything else as typed.
public func withoutArabicMarks(_ text: String) -> String
```

Removes Unicode scalars U+0610–U+061A, U+064B–U+065F, U+0670, U+06D6–U+06DC, U+06DF–U+06E4, U+06E7–U+06E8, U+06EA–U+06ED (tashkeel and Quranic marks) and U+0640 (tatweel). Work on `unicodeScalars`, not `Character`s: a mark combines with its letter into one `Character`.

| Input | Expected |
|---|---|
| `التَّعلُّم` | `التعلم` |
| `التّعلمُ` | `التعلم` |
| `ـالتعلمـ` | `التعلم` |
| `Schrödinger` | `Schrödinger` |
| `أإآ` | `أإآ` |
| `Deep Learning` | `Deep Learning` |
| `١٩` | `١٩` |
| (empty) | (empty) |

## 4. Lookup

### 4.1 Network (`HashiyaNetwork`)

```swift
public protocol OpenAlexLookupService: Sendable {
    /// The work OpenAlex resolves `id` to (e.g. "doi:10.1038/nature14539"), or nil on HTTP 404 or 400.
    func work(id: String) async throws -> NetworkWork?
    func works(filter: String, perPage: Int) async throws -> NetworkWorksResponse
}
public protocol ArxivTitleService: Sendable {
    /// arXiv's title for `id` ("1810.04805", "hep-th/9901001"), or nil when arXiv has no such paper.
    func title(id: String) async throws -> String?
}
```

- **`OpenAlexLookupClient`** shares spec 1's OpenAlex session, key handling, logging and failure classification.
  - `work(id:)`: `GET https://api.openalex.org/works/{id}?select=<spec 1 fields>` (+ `api_key`). `{id}` is one path segment: percent-encode everything except unreserved characters and `:`, so `/`, `#`, `;`, `<`, `>`, `(`, `)` become `%XX` and reach OpenAlex intact. Build the URL from `percentEncodedPath` (`URL.path` decodes `%2F`, so tests read `url.absoluteString` / the percent-encoded path and decode each segment). HTTP 404 and 400 → `nil` (callers only pass well-formed IDs); every other failure throws `NetworkFailure` as in spec 1.
  - `works(filter:perPage:)`: `GET /works?filter=…&per_page=…&select=…` with the same key handling.
- **`ArxivTitleClient`**: its own `URLSession` (ephemeral, same timeouts as spec 1, no logging, no key). `GET https://export.arxiv.org/api/query?id_list=<id>` with header `User-Agent: Hashiya-iOS (https://github.com/FadyFouad/Hashiya)`. Uses `URLSession.data(for:)`, so cancelling the lookup's task cancels the request. Non-2xx → `.http(code:, usedUserKey: false)`; transport error → `.connectivity`; unreadable → `.malformedResponse`.
- **`parseArxivTitle(_ xml: String) throws -> String?`** (same rules as Android, regex-based, not a full XML parser):
  1. No `<feed` followed by whitespace or `>` → throw `.malformedResponse`.
  2. No `<entry>…</entry>` (first one, across lines) → `nil` (empty feed).
  3. The entry's `<id>` contains `/api/errors` → `nil` (arXiv's error entry for a bad ID).
  4. No `<title…>…</title>` in the entry → throw `.malformedResponse`.
  5. Decode numeric entities `&#NNN;` and `&#xHH;`, then `&lt;`, `&gt;`, `&quot;`, `&apos;`, and `&amp;` last; trim; collapse runs of whitespace to one space; empty → `nil`.

### 4.2 Repository (`HashiyaData`)

```swift
public protocol PaperLookupRepository: Sendable {
    func lookup(_ identifier: PaperIdentifier) async -> LookupResult   // cancellation ends the task; no result is used
}
public enum LookupResult: Equatable, Sendable {
    case found(Paper)
    case notFound(arxivTitle: String?)   // arXiv's title for an arXiv ID OpenAlex couldn't match
    case failed(SearchError)
}
```

`OpenAlexPaperLookupRepository(openAlex:arxiv:)`:

- **DOI:** `work(id: "doi:<doi>")` → `.found(work.asPaper())` or `.notFound(arxivTitle: nil)`.
- **arXiv:**
  1. `work(id: "doi:10.48550/arXiv.<id>")` found → `.found` (trusted: OpenAlex matched the arXiv DOI itself).
  2. Otherwise one `works(filter: arxivLandingPageFilter(id), perPage: 2)`; results de-duplicated by work `id`.
  3. Not exactly one work → `.notFound(arxivTitle: <arXiv title, or nil if that fetch fails>)`.
  4. Exactly one → `ArxivTitleService.title(id:)`: any failure → `.failed(.serviceUnavailable)` (OpenAlex has just answered, so the device is online and "Can't reach OpenAlex" would be wrong); `nil` → `.notFound(arxivTitle: nil)`; titles match → `.found`; else `.notFound(arxivTitle: arxivTitle)`.
- Any `NetworkFailure` from OpenAlex → `.failed(failure.asSearchError())` (spec 1 §6.4).
- **`arxivLandingPageFilter(id)`** = `locations.landing_page_url:` followed by these 13 URLs joined with `|`: `http://arxiv.org/abs/<id>`, the same with `v1` … `v5`, `https://arxiv.org/abs/<id>`, the same with `v1` … `v5`, and `https://doi.org/10.48550/arxiv.<id>`. For `1810.04805`: `locations.landing_page_url:http://arxiv.org/abs/1810.04805|http://arxiv.org/abs/1810.04805v1|…|https://arxiv.org/abs/1810.04805v5|https://doi.org/10.48550/arxiv.1810.04805`. Old-style IDs keep their slash (`http://arxiv.org/abs/hep-th/9901001|…`).
- **`titlesMatch(a, b)`**: both normalized — NFKD (`decomposedStringWithCompatibilityMapping`), remove combining marks (general categories Mn, Mc, Me), lowercase, replace every run of characters that are neither letters nor numbers with one space, trim — are equal and non-empty. Accent encoding doesn't matter (`Schr\u{00F6}dinger` = `Scho\u{0308}dinger` = `Schrodinger`).

## 5. Search in ID mode (`FeatureSearch`)

### 5.1 `SearchViewModel` changes

- The active keyword query (spec 1 §7.1 rule 2) now also requires: `withoutArabicMarks(trimmed)` trimmed is non-empty (a query of only marks or tatweel is idle), `parsePaperIdentifier(submitted) == nil`, and `!looksLikeLink(submitted)`. The request's `search` is `withoutArabicMarks(text)` trimmed; letters, accents, digits and case are sent as typed.
- New state `lookup: LookupState?`:
  ```swift
  public enum LookupState: Equatable {
      case looking(PaperIdentifier)
      case found(Paper)
      case notFound(PaperIdentifier, searchTitle: String?)   // searchTitle = arXiv's title
      case failed(SearchError)
      case noIDInLink
  }
  ```
  - `parsePaperIdentifier(submitted)` non-nil → ID mode: no keyword request, `lookup = .looking(id)`, then the result of `PaperLookupRepository.lookup`. A newer submitted identifier cancels the running lookup.
  - `looksLikeLink(submitted)` and no identifier → `.noIDInLink`, no request of any kind.
  - Otherwise `lookup = nil` (keyword mode).
- **Retry** and an API key change re-run the current lookup. Restored text (`@SceneStorage`) is submitted at once, so a restored ID re-runs its lookup.
- `searchTitle(title)` (the **Search for** button) behaves like a suggestion: sets the text to the full title and submits at once.
- Save/remove in the found preview reuse `toggleSave` and its messages.
- `startFresh(focus: Bool)` (for **Add paper**): cancels running work, sets text to `""`, chips to defaults, `lookup = nil`, idle, and sets `focusRequested = true`; the view consumes the flag once.

### 5.2 UI

- The search prompt becomes `search.placeholder` = "Search, or paste a DOI, arXiv ID or link".
- In ID mode the chip row and the result count are hidden, and the body is `LookupBody`:
  - **Looking**: `search.lookupLookingDOI` ("Looking up DOI 10.1038/nature14539…") or `search.lookupLookingArxiv` ("Looking up arXiv 1706.03762…") in the secondary colour, 16×8 pt padding, then `LoadingSkeleton(rows: 1)`.
  - **Found**: `PaperPreviewContent` in the screen body (not a sheet), with **Open DOI** and **Save to library** / **Remove from library**; "In library" from `savedIDs`.
  - **Not found**: `EmptyStateView` (`doc.text.magnifyingglass`), title `search.lookupNotFoundDOI` or `search.lookupNotFoundArxiv`, message `search.lookupNotFoundMessage`, and, when `searchTitle` exists, a button `search.lookupSearchTitle` with the title shortened (≤ 60 characters kept; longer → the first 59 characters, trailing whitespace removed, plus `…`) and wrapped in U+2068 FIRST STRONG ISOLATE … U+2069 POP DIRECTIONAL ISOLATE, so an English title stays left-to-right inside the Arabic sentence. Formatting with the Arabic locale already wraps every argument in that pair (so the Arabic "Looking up …" text isolates its ID too); add the pair only when the formatted text lacks it, so it appears exactly once. For BERT: `Search for “BERT: Pre-training of Deep Bidirectional Transformers for L…”`.
  - **Failed**: spec 1's `ErrorStateView` mapping; Retry re-runs the lookup; `invalidUserKey` offers Open Settings.
  - **No ID in link**: `EmptyStateView`, title `search.linkNoIDTitle`, message `search.linkNoIDMessage`, no button.
- Focus: `SearchView` uses `.searchable(text:isPresented:placement:prompt:)`; when `focusRequested` is set it sets `isPresented = true` (activating the field and showing the keyboard — verify on iOS 17) and clears the flag.

## 6. Library: Add paper (`FeatureLibrary`)

- A floating **Add paper** button (`library.addPaper`, `plus` icon, capsule, prominent teal) at the bottom trailing corner, above the tab bar, in both the empty state and the list; it mirrors to the bottom left in Arabic.
- Tapping it calls `onAddPaper`, which `RootView` handles by calling `searchViewModel.startFresh(focus: true)` and switching to the Search tab.
- The list gets bottom content margin of 88 pt (`.contentMargins(.bottom, 88, for: .scrollContent)`) so the button never covers the last row; the Undo banner sits above the button.

## 7. Cross-process storage

- The app and the extension open the same database file in the App Group container with a GRDB `DatabasePool` (WAL), each running the migrator on open (the extension may run before the app ever has). Configure a busy timeout (5 s) so concurrent writers wait instead of failing, and follow GRDB's guidance for databases shared between processes, including its suspension handling that prevents the system from terminating a suspended process holding a lock in a shared container. Verified against GRDB 7.11.1: open under an `NSFileCoordinator` (`.forMerging`), `busyMode = .timeout(5)`, `observesSuspensionNotifications = true`, posting `Database.suspendNotification` when the app enters the background and before the extension completes, `Database.resumeNotification` when active (exposed to the app and extension as `SharedLibraryDatabase.suspend()/resume()` in `HashiyaData`).
- GRDB's `ValueObservation` only sees writes made through the same `DatabasePool` in the same process. `LibraryRepository` gains `func refreshAfterExternalChanges() async`, implemented by notifying GRDB that `papers` and `paper_authors` changed (`Database.notifyChanges(in:)` inside a write — verify against current docs), so every live observation re-fetches. `RootView` calls it whenever the scene phase becomes `.active`, so a paper saved in the share sheet appears in Library and as "In library" without relaunching. Fakes implement it as a no-op that re-emits.
- The Keychain item (spec 1 §6.3) is read by the extension through the shared access group; the extension never writes it.

## 8. Share Extension `HashiyaShare`

### 8.1 Target

- `ios/HashiyaShare/`: `ShareViewController.swift` (principal class, a `UIViewController` that hosts `ShareLookupView` in a `UIHostingController`), `ShareContainer.swift` (builds `LiveDependencies`, like `AppContainer`), `Info.plist`, `HashiyaShare.entitlements`, `Localizable.xcstrings` (none of its own strings today; the UI's strings come from `FeatureSearch`).
- Bundle ID `com.etatech.hashiya.share`; display name from `InfoPlist.xcstrings` = "Hashiya" / "حاشية"; it uses `Base.xcconfig`, so its Info.plist also carries `OpenAlexAPIKey = $(OPENALEX_API_KEY)`.
- `NSExtension`: point identifier `com.apple.share-services`; activation rule dictionary with `NSExtensionActivationSupportsWebURLWithMaxCount = 1` and `NSExtensionActivationSupportsText = YES` (verify key names against current docs).
- Entitlements: App Group `group.com.etatech.hashiya`; keychain access group `$(AppIdentifierPrefix)com.etatech.hashiya.shared`.
- Links `FeatureSearch`, `HashiyaData`, `HashiyaDesignSystem`, `HashiyaModel`. Code linked into the extension uses only extension-safe API (no `UIApplication.shared`); `FeatureSearch` never touches `UIApplication` (the app passes Open Settings in as a closure). Enable "Require Only App-Extension-Safe API" on the extension target.

### 8.2 Reading what was shared

`ShareViewController` reads the first `NSExtensionItem` of `extensionContext.inputItems`:

- **URL**: the first attachment conforming to `UTType.url` whose value is an `http`/`https` URL (file URLs are ignored).
- **Text**: `attributedContentText?.string`, plus any attachment conforming to `UTType.plainText` (a `String`, or an attributed string reduced to its string).
- **Title**: `attributedTitle?.string`; if empty and a URL was shared, the `attributedContentText` (browsers often put the page title there — verify which field Safari fills against current docs).

It then calls `shareLookupInput(url:text:title:)` and shows `ShareLookupView`. Loading the items is async; the sheet shows the Looking skeleton meanwhile.

### 8.3 `shareLookupInput` (`FeatureSearch`, pure)

```swift
public enum ShareLookupInput: Equatable {
    case lookup(PaperIdentifier)
    case noIdentifier(pageTitle: String)   // shown, not searched
    case nothing
}
public func shareLookupInput(url: URL?, text: String?, title: String?) -> ShareLookupInput
```

1. `combined` = `url?.absoluteString` and `text`, non-empty ones joined with a newline.
2. `pageTitle` = `title` trimmed and cut to its first 300 characters; `nil` if empty or `looksLikeLink`.
3. `extractPaperIdentifier(combined)` non-nil → `.lookup(id)`.
4. Else `pageTitle` non-nil → `.noIdentifier(pageTitle:)`.
5. Else `.nothing`.

Tests (ported from `ShareToSearchRouteTest.kt`):

| url | text | title | Expected |
|---|---|---|---|
| `https://arxiv.org/abs/1706.03762` | — | `Attention Is All You Need` | `.lookup(A(1706.03762))` |
| `https://doi.org/10.1038/nature14539` | — | `Deep learning \| Nature` | `.lookup(D(10.1038/nature14539))` |
| `https://dl.acm.org/doi/10.1145/3292500.3330701` | — | — | `.lookup(D(10.1145/3292500.3330701))` |
| — | `Check this out: https://arxiv.org/abs/2401.00001v2` | `  ` | `.lookup(A(2401.00001))` |
| `https://ieeexplore.ieee.org/document/1234567` | — | ` Deep learning ` | `.noIdentifier("Deep learning")` |
| `https://www.nature.com/articles/nature14539` | — | `Deep learning \| Nature` | `.lookup(D(10.1038/nature14539))` |
| — | — | `Deep learning` | `.noIdentifier("Deep learning")` |
| — | `just some words` | — | `.nothing` |
| — | — | `   ` | `.nothing` |
| `https://ieeexplore.ieee.org/document/9999999` | — | `https://ieeexplore.ieee.org/document/9999999` | `.nothing` |
| — | — | 400 × `A` | `.noIdentifier(300 × "A")` |
| `https://arxiv.org/abs/1706.03762` | — | 400 × `A` | `.lookup(A(1706.03762))` |

### 8.4 `ShareLookupViewModel` and `ShareLookupView` (`FeatureSearch`)

```swift
@Observable @MainActor public final class ShareLookupViewModel {
    public enum State: Equatable {
        case reading                       // items still loading
        case looking(PaperIdentifier)
        case found(Paper)
        case notFound(PaperIdentifier)
        case failed(SearchError)
        case noIdentifier(pageTitle: String)
        case nothing
    }
    public private(set) var state: State
    public private(set) var savedIDs: Set<String>
    public var message: SearchMessage?
    public init(lookup: any PaperLookupRepository, library: any LibraryRepository)
    public func start(_ input: ShareLookupInput) async
    public func retry() async
    public func toggleSave(_ paper: Paper) async
}
```

- `start` maps `.lookup` to Looking then the lookup result (`.found`, `.notFound`, `.failed`); `.noIdentifier` and `.nothing` show directly with no request.
- `retry` re-runs the last lookup. Save/remove failures set `message` (`search.saveFailed` / `search.removeFailed`, shown in a `HashiyaBanner`).
- The lookup runs in the view's `.task`; closing the sheet cancels it, and with it any arXiv request.

`ShareLookupView`: a `NavigationStack` titled `search.shareTitle` ("Hashiya"), toolbar **Done** (`search.shareDone`) that calls `extensionContext.completeRequest(returningItems: [])` through a closure. The system presents the extension as a sheet; its content is a compact single-paper layout. Body per state:

| State | Shows |
|---|---|
| reading, looking | as Search's Looking (`search.lookupLookingDOI` / `…Arxiv` once the ID is known) + one skeleton row |
| found | `PaperPreviewContent` with **Save to library** / **Remove from library** and `onOpenDOI: nil` (an extension can't open a browser) |
| notFound | `EmptyStateView`: `search.lookupNotFoundDOI` / `search.lookupNotFoundArxiv`, message `search.lookupNotFoundMessage`, no button |
| failed | spec 1's error title and message for the error, with **Retry** for every error, including `invalidUserKey` (the extension can't open the app's Settings) |
| noIdentifier | `EmptyStateView`: `search.shareNoIDTitle` ("No DOI or arXiv ID on this page"), message = the page title laid out in its own direction |
| nothing | `EmptyStateView`: `search.noteNothing` ("Couldn't find a paper in what you shared."), no message |

After **Save to library** the button turns into **Remove from library** (from `savedIDs`); **Done** closes the sheet and returns to the sharing app.

### 8.5 UI-test hooks (Debug builds only)

- Launched with `-ui-testing`, the app writes `uiTestingStubs = true` to the App Group `UserDefaults`; a Debug extension that sees the flag uses a stub `PaperLookupRepository` (in the extension target under `#if DEBUG`, with its own copy of the `SamplePapers.attention` data; the extension never links `HashiyaTesting`) returning that paper for `A(1706.03762)`. It writes the UI tests' own App Group library file `hashiya-ui-testing.sqlite`, which a `-ui-testing` app launch deletes and opens instead of plan 1's in-memory library (another process can't see an in-memory database, and tests must not touch the real library), so the app then shows the saved paper. The app writes the flag as `false` on every other Debug launch. Release builds contain neither the flag check nor the stub.
- Launched with `-ui-testing-share <url>`, the app presents a `UIActivityViewController` for that URL, so the XCUITest can pick Hashiya in a real share sheet. Verified on the iOS 18.2 simulator: an app's own Share Extension is listed in its own share sheet and the extension's elements are reachable from the app's `XCUIApplication`, so the test does not drive Safari; sharing from Safari is a manual acceptance check.

## 9. Strings

Same key convention as spec 1 §10.

`FeatureSearch` (new or changed):

| Key | English | Arabic |
|---|---|---|
| `search.placeholder` (changed) | Search, or paste a DOI, arXiv ID or link | ابحث، أو الصق DOI أو معرّف arXiv أو رابطًا |
| `search.lookupLookingDOI` | Looking up DOI %1$@… | جارٍ البحث عن DOI %1$@… |
| `search.lookupLookingArxiv` | Looking up arXiv %1$@… | جارٍ البحث عن arXiv %1$@… |
| `search.lookupNotFoundDOI` | No paper found for this DOI | لم يتم العثور على ورقة بهذا الـ DOI |
| `search.lookupNotFoundArxiv` | No paper found for this arXiv ID | لم يتم العثور على ورقة بمعرّف arXiv هذا |
| `search.lookupNotFoundMessage` | Check the ID, or search by the paper's title. | تحقّق من المعرّف، أو ابحث بعنوان الورقة. |
| `search.lookupSearchTitle` | Search for “%1$@” | ابحث عن «%1$@» |
| `search.linkNoIDTitle` | No DOI or arXiv ID in this link | لا يوجد DOI أو معرّف arXiv في هذا الرابط |
| `search.linkNoIDMessage` | Paste the paper's DOI or arXiv ID, or search by its title. | الصق DOI الورقة أو معرّف arXiv، أو ابحث بعنوانها. |
| `search.noteNothing` | Couldn't find a paper in what you shared. | تعذّر العثور على ورقة فيما شاركته. |
| `search.shareNoIDTitle` (*iOS only*) | No DOI or arXiv ID on this page | لا يوجد DOI أو معرّف arXiv في هذه الصفحة |
| `search.shareTitle` (*iOS only*) | Hashiya | حاشية |
| `search.shareDone` (*iOS only*) | Done | تم |

The extension also shows spec 1's `designsystem.*`, `search.error*`, `search.retry`, `search.saveFailed` and `search.removeFailed`.

`FeatureLibrary` (new):

| Key | English | Arabic |
|---|---|---|
| `library.addPaper` | Add paper | إضافة ورقة |

`HashiyaShare` `InfoPlist.xcstrings`: `CFBundleDisplayName` = Hashiya / حاشية.

Android strings not used on iOS: `search_note_no_id` (the extension never searches by page title).

## 10. Errors

| Cause | Result | User sees |
|---|---|---|
| OpenAlex 404 / 400 for a well-formed ID | `.notFound` | "No paper found…" (+ **Search for** in the app when arXiv's title is known) |
| OpenAlex offline / 429 / 5xx / rejected key / unreadable | `.failed` via spec 1 mapping | Error state with Retry (in the app, Open Settings for a rejected user key) |
| arXiv unreachable, 5xx or unreadable during the cross-check | `.failed(.serviceUnavailable)` | "Search is unavailable right now" + Retry |
| arXiv says the ID doesn't exist | `.notFound(arxivTitle: nil)` | "No paper found for this arXiv ID" |
| Pasted link without an ID | `.noIDInLink` | "No DOI or arXiv ID in this link" |
| Shared page without an ID | `.noIdentifier` | "No DOI or arXiv ID on this page" + page title |
| Nothing readable shared | `.nothing` | "Couldn't find a paper in what you shared." |
| Database write fails (app or extension) | message | "Couldn't save the paper" / "Couldn't remove the paper" |

The OpenAlex key is still never logged or placed in messages; the arXiv client never sends it.

## 11. Testing

Fixtures `work.json`, `arxiv_bert.xml`, `arxiv_empty.xml` and `arxiv_error.xml` are copied from `core/network/src/test/resources/`. `HashiyaTesting` gains `FakePaperLookupRepository` (scripted results, records lookups, can hold lookups until released), `FakeOpenAlexLookupService` and `FakeArxivTitleService` (scripted works / titles / failures, recording requests).

| Target | Coverage |
|---|---|
| `HashiyaModelTests` | Every table in §3.4 and §3.5 |
| `HashiyaNetworkTests` | `work(id:)`: path segments decode to `["works", "doi:10.1038/nature14539"]`, `select`, built-in `api_key`, parses `work.json` (id `https://openalex.org/W2919115771`, "Deep learning"); 404 → nil; 400 → nil; 429 throws `.http(429, usedUserKey: false)`; the sici DOI and `doi:10.1234/abc#1` survive path encoding; `works(filter:perPage:)` sends the exact filter, `per_page=2` and `select`, parses 2 results; unreachable → `.connectivity`. `ArxivTitleClient`: path `/api/query`, `id_list` decodes to `hep-th/9901001`, no `api_key`, the User-Agent header; BERT fixture → `BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding`; empty feed → nil; error entry → nil; 503 → `.http(503, usedUserKey: false)`; unreachable → `.connectivity`; `<html><body>maintenance</body></html>` → `.malformedResponse`; cancelling the calling task stops the stubbed request; entities `Graphs &amp; Networks: &lt;A&gt; &#8211; &#x3B1; &quot;study&quot;` → `Graphs & Networks: <A> – α "study"` |
| `HashiyaDataTests` | `arxivLandingPageFilter("1810.04805")` equals the exact 13-URL string; old-style keeps its slash. `titlesMatch`: case, punctuation and spacing (`BERT: Pre-training of Deep  Bidirectional …Understanding.` vs lowercase unpunctuated); quote styles (`Don’t Stop Pretraining` vs `Don't stop pretraining`); accent encoding and accents; Arabic (`تعلم الآلة` vs `تعلم الآلة.`); different titles don't match; `""`/`""` and `!!!`/`...` never match. Lookup repository with fakes: DOI looked up directly with no filter or arXiv request; unknown DOI → `.notFound(nil)`; offline → `.failed(.offline)`; 401 with user key → `.failed(.invalidUserKey)`; arXiv DOI hit (`doi:10.48550/arXiv.2310.06825`, "Mistral 7B") trusted with no further requests; fallback with a matching title → found, with exactly one work request `doi:10.48550/arXiv.1706.03762`, one filter request with `perPage` 2 and one arXiv request; the BERT case (single wrong work "AI-Assisted Pipeline…") → `.notFound(bertTitle)`; no matches → `.notFound(bertTitle)`; no matches and arXiv down → `.notFound(nil)`; two distinct works → `.notFound(bertTitle)`; the same work twice → found; cross-check with arXiv unreachable, 503 or malformed → `.failed(.serviceUnavailable)`; arXiv has no such paper → `.notFound(nil)`; 429 during the filter request → `.failed(.rateLimited)`. `refreshAfterExternalChanges` re-emits observations after a write made through a second `DatabasePool` on the same temporary file |
| `FeatureSearchTests` | Tatweel-only (`ـــ`) and tashkeel-only (`\u{064E}`) text is idle with no request, then `التَّعلُّم` searches (request text `التعلم`); a pasted DOI link is looked up, not searched; Looking until the lookup finishes; text containing a DOI is a keyword search; a pasted link without an ID → `.noIDInLink` with no request, then a keyword works again; a newer lookup replaces an older one; Retry and an API key change re-run the lookup; Not found offers arXiv's title and **Search for** submits it; `startFresh(focus:)` clears text, chips and lookup and requests focus once. `shareLookupInput` table (§8.3). `ShareLookupViewModel`: each state from each input and lookup result, Retry, save and remove with failure messages, cancellation leaves no state change |
| `FeatureLibraryTests` | Library snapshots show the Add paper button; that it calls `onAddPaper` is covered by the UI test below (hostless package tests can't tap a SwiftUI button) |
| Snapshots | Search: looking, found, not found with the title button, error, link without ID (× English/Arabic × light/dark). Library list and empty state re-recorded with the Add paper button. Extension sheet: looking, found (saved and not saved), not found, error, no ID with a page title (English and Arabic titles), nothing |
| `HashiyaUITests` | Share flow: launch with `-ui-testing -ui-testing-share https://arxiv.org/abs/1706.03762`, pick Hashiya in the share sheet, see "Attention Is All You Need", tap **Save to library**, see **Remove from library**, tap **Done**, open the Library tab, see the paper. Add paper: tap it, the Search field is active with the new hint |

## 12. Acceptance criteria (on a device or simulator)

1. Pasting `10.1038/nature14539` into Search shows the "Deep learning" preview; **Save to library** adds it to the Library.
2. Pasting `1706.03762`, `arXiv:2401.00001` or `https://arxiv.org/pdf/2005.14165v4` shows the right paper.
3. Pasting `1810.04805` shows "No paper found for this arXiv ID" with **Search for "BERT: Pre-training of Deep Bidirectional Transformers for L…"**, which runs a keyword search for the full title.
4. Typing `a study of 10.1038/nature14539` runs a normal keyword search.
5. In Safari, sharing an arXiv `abs` page, an arXiv PDF, a `doi.org` link and a Wiley or ACM article, and choosing Hashiya, shows the right paper in the sheet; **Save to library** then **Done** returns to Safari, and opening Hashiya shows the paper in the Library.
6. Sharing a nature.com article shows the paper; sharing an IEEE Xplore article shows "No DOI or arXiv ID on this page" with the page title.
7. In airplane mode, a lookup (in Search and in the share sheet) shows the offline error with Retry; after reconnecting, Retry finds the paper.
8. The Library's **Add paper** button opens Search with the keyboard up and the hint "Search, or paste a DOI, arXiv ID or link"; the button never covers the last row.
9. Pasting a link with no DOI or arXiv ID shows "No DOI or arXiv ID in this link" instead of searching the URL.
10. Searching `التَّعلُّم` returns the same results as `التعلم`; a query of only tashkeel stays idle.
11. Everything above works in Arabic with right-to-left layout; English titles, including the one in **Search for "…"**, stay left-to-right.
12. CI is green with the new snapshot baselines recorded by the record workflow.

## 13. Risks

| Risk | Mitigation |
|---|---|
| Two processes writing one SQLite file (locks, suspension terminations) | `DatabasePool` in WAL with a busy timeout, GRDB's shared-database guidance, short write transactions; covered by the two-pool test |
| The app misses writes made by the extension | `refreshAfterExternalChanges()` on every return to the foreground |
| Browsers fill `NSExtensionItem` fields differently | Lenient parser over URL + text; a title that is only a link is ignored; no-ID and nothing states are explicit |
| Share Extension memory limit | The extension loads one paper and one database connection pool; no images |
| OpenAlex's arXiv data is wrong or incomplete | Trusted path only for the arXiv DOI; fallback verified against arXiv's title |
| Title formatting differs between OpenAlex and arXiv | Letters-and-digits normalization; Not found still offers the title search in the app |
| arXiv rate guidance (one request per 3 s) | At most one or two arXiv requests per lookup, only when the arXiv DOI lookup fails |

## 14. Differences from Android

| Area | Android | iOS | Why |
|---|---|---|---|
| Receiving shares | `ACTION_SEND` opens the app on Search with the ID or the page title | Share Extension resolves and saves inside the share sheet; the app is not opened | iOS share extensions can't open their containing app; saving in place is the iOS pattern |
| Page title without an ID | Keyword search for the title with a note | Shows "No DOI or arXiv ID on this page" and the title; no search | Keyword search in the extension is out of scope |
| Not found after a share | **Search for** the page title (or arXiv title) | No button in the extension; the app's Search still offers arXiv's title | Same reason |
| Second share while open | New share replaces the current one (`onNewIntent`) | Not handled | The share sheet is modal; out of scope |
| Open DOI in a shared lookup | Available (preview in the app) | Hidden in the extension | Extensions can't open a browser |
| Rejected user key after a share | Open Settings | Retry | The extension can't open the app's Settings |
| Shared input | `EXTRA_TEXT` + `EXTRA_SUBJECT`/`EXTRA_TITLE` | URL attachment + text + `attributedTitle` (or content text) | Platform share model |
| Nothing usable in a share | Note on an empty Search | "Couldn't find a paper in what you shared." in the sheet | Same text, different place |
| Data sharing | One process | App Group database and keychain group; observations refreshed on foreground | Two processes |
| Parser digits | `\d` is ASCII in Java | Written as `[0-9]` | Swift `\d` matches any Unicode digit |
| iOS-only strings | — | `search.shareNoIDTitle`, `search.shareTitle`, `search.shareDone` | New UI |
| Unused Android string | — | `search_note_no_id` | No title search from shares |
