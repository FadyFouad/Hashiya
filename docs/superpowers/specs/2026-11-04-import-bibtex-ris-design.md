# Importing a library from BibTeX and RIS — Design

- **Date:** 2026-11-04
- **Status:** Awaiting review
- **Scope:** Android and iOS, the same feature on both. Builds on `main` at `c4f0085` (2026-10-07): collections and stored cite keys on both platforms, PDFs (Android schema 5, iOS migration `v5`), OpenAlex quota protection (`2026-10-05-openalex-quota-design.md`), usage analytics (`2026-10-05-analytics-design.md`), citation styles and backup and restore.
- **Revision:** second version. The first was written against an older `main` (`7ad31b4`); §15 lists what changed.

## 1. Context

Most students who try Hashiya already keep a library somewhere: Zotero, Mendeley, EndNote, or a `.bib` file in an Overleaf project. Today the only way in is one paper at a time from Search. This feature imports a whole `.bib` or `.ris` file in one go. Entries with a DOI are matched to OpenAlex, so they get the same abstract, authors and citation details as papers saved from Search. Entries without a DOI, or with one OpenAlex doesn't know, become local papers built from the file's own fields. Nothing in the file is thrown away silently.

The standards of the earlier sub-projects apply: modular architecture and dependency rules, English and Arabic with full RTL, TDD with hand-written fakes, Roborazzi screenshots with baselines recorded on CI Linux, and snapshot tests on iOS.

### Decisions

| Topic | Decision |
|---|---|
| Formats | BibTeX and BibLaTeX (`.bib`) and RIS (`.ris`). The format is chosen from the content, not the extension (§4.1). |
| Parsers | Written in this repo as pure modules: `core/importer` (Kotlin/JVM) and `HashiyaImport` (Swift, no dependencies). Both pass the same fixtures with the same expected JSON (§10). |
| Matching | By DOI only, in batches of 50 per OpenAlex request (`filter=doi:a|b|…`). An arXiv eprint becomes its arXiv DOI (`10.48550/arXiv.<id>`). No title search (§6). |
| Unmatched entries | Kept as local papers built from the file. Their id is `local-<uuid>` in the existing `open_alex_id` column, so every screen keyed by that id keeps working (§7.2). |
| Cite keys | A BibTeX entry's key is stored as the paper's cite key when it's free, so a thesis's `\cite{}` commands keep working after the next export (§7.3). |
| Duplicates | A paper already in the library is skipped. An entry repeated in the file is imported once (§5). |
| Preview | Before anything is saved, a screen shows found, matched, kept-from-file, already-in-library, repeated and unreadable counts (§8.2). |
| Background | A WorkManager job on Android; a background task plus resume at launch on iOS. Both resume an interrupted import without saving anything twice (§7.5, §12). |
| Collection | Optional, on by default: put the imported papers in a new collection named after the file, on both platforms. |
| Quota | Batched DOI lookups are metered list calls and go through the existing quota routes. An import may use at most half of the day's shared allowance (`dailyDeviceCalls`), so searches always keep the other half; beyond that it uses the keyless route, then free single lookups (§6.3). |
| Schema | Android Room version 6, iOS GRDB migration `v6`: the same two import tables on both (§7.4, §12). |
| Analytics | One `import` event when an import ends, and the screen value `import`, from the closed lists of the analytics spec (§8.4). |

## 2. Goals and non-goals

### Goals

1. A user can pick a `.bib` or `.ris` file from the system picker, or send one to Hashiya from another app (Open with / Share), and see a preview before anything is saved.
2. Exports from Zotero (built-in and Better BibTeX), Mendeley, EndNote, Google Scholar and hand-written Overleaf files parse to the exact results listed in `testdata/import/expected/`.
3. One malformed entry never stops the rest of the file. The preview says how many entries couldn't be read.
4. Entries with a DOI are matched to OpenAlex with batched requests; a 500-entry file costs at most 10 list requests.
5. Every entry that has a title or a DOI ends up in the library, matched or not, unless the paper is already there.
6. A BibTeX entry's cite key survives into the library, so the next `.bib` export uses the same key.
7. The import keeps going when the user leaves the app, resumes after the process is killed, and can be cancelled.
8. The imported papers can go into a new collection named after the file.
9. English and Arabic, with correct RTL, on every new screen.

### Non-goals

- PDF attachments referenced in the file (`file = {…}`, `L1`).
- Zotero or Mendeley collections, groups and tags (`mendeley-groups`, `KW`, `keywords`).
- Syncing with Zotero, Mendeley or EndNote.
- `.nbib` (PubMed), EndNote XML, CSL-JSON.
- Matching by title, author or year.
- Editing entries in the preview or choosing which entries to import.
- Undo for a whole import (papers can be removed one by one as usual).

## 3. Research

Facts the design relies on, checked on 2026-11-04. OpenAlex's own pages were not reachable from the environment the spec was written in, so the OpenAlex rows come from search-engine extracts of those pages and are marked as such; they should be rechecked in a browser before release.

| Topic | Finding | Source |
|---|---|---|
| OpenAlex OR filter | Up to 100 values joined with `|` in one filter, per the current docs; the older LLM guide said 50. OR works within one filter only. | https://developers.openalex.org/guides/filtering.md (extract), https://github.com/ourresearch/openalex-docs/blob/main/api-guide-for-llms.md |
| DOI filter values | Both `https://doi.org/10.x/y` and bare `10.x/y` work in `filter=doi:`. | https://blog.openalex.org/fetch-multiple-dois-in-one-openalex-api-request/ |
| `per_page` | Default 25, maximum 100 (the archived guide still says 200). | https://developers.openalex.org/guides/llm-quick-reference.md (extract) |
| Pricing | Usage-based in USD since Feb 13, 2026: free budget $1/day with a key, about $0.10/day without. Singleton `/works/{id}` free; list or filter call $0.0001; `search=` call $0.001. | https://blog.openalex.org/openalex-api-new-features-and-usage-based-pricing/, https://help.openalex.org/access/example-costs/ (extracts) |
| Rate limits | 10 requests/s without a key, 30 with a free key. HTTP 429 for both a spent budget and too many requests per second; a spent budget comes with a `Retry-After` of hours. | Same, and https://github.com/QAtlasHub/doiget/issues/667 |
| RIS syntax | `XX  - value`, record from `TY` to `ER`; `AU`/`A1` one author per line as "Last, First"; `A2` editors; `DA` `YYYY/MM/DD/other` with empty parts; `PY` year; `Y1` old-spec date. | Zotero's `RIS.js` (lastUpdated 2026-01-05), https://github.com/zotero/translators/blob/master/RIS.js |
| RIS quirks | Zotero writes CRLF and `ER  - ` with a trailing space; tolerates a missing `ER`; EndNote puts the DOI in `M3`; `CPAPER` is a conference paper. | Same |
| Zotero BibTeX | `journal`, `year` and an unquoted `month` macro; UTF-8 by default (accents escaped only with another charset); `doi` and `url` left raw; capitals protected with braces; corporate authors in double braces. | https://github.com/zotero/translators/blob/master/BibTeX.js (lastUpdated 2026-08-03) |
| Better BibTeX | Unicode-to-LaTeX on by default for BibTeX, off for BibLaTeX; BibLaTeX output uses `date`, `journaltitle`, `eprint`/`eprinttype`. | https://retorque.re/zotero-better-bibtex/installation/preferences/export/ |
| BibLaTeX aliases | `journal`/`journaltitle` and `year`/`date` are aliases; accept both. | biblatex manual, quoted in https://debbugs.gnu.org/db/44/44976.html |
| Mendeley | Can escape LaTeX specials, so `_` arrives as `\_` or `{\_}`; arXiv papers carry `arxivId`, `eprint`, `archivePrefix`. | https://latex.org/forum/viewtopic.php?p=82795 |
| Google Scholar | No DOI in its BibTeX; arXiv papers come as `@article` with `journal={arXiv preprint arXiv:…}`. | https://libanswers.caltech.edu/faq/225020 |
| Android picker | `ACTION_OPEN_DOCUMENT` through `ActivityResultContracts.OpenDocument`, no permission; read with `ContentResolver.openInputStream`. | https://developer.android.com/training/data-storage/shared/documents-files |
| MIME types | `application/x-bibtex`, `text/x-bibtex`, `application/x-research-info-systems` are unregistered and many providers report `application/octet-stream` or `text/plain`, so the picker asks for `*/*` and the content decides. | https://lxr.kde.org/source/office/kbibtex/mime/bibliography.xml, https://developer.android.com/training/sharing/receive |
| Intent filters | `pathPattern` only applies with a scheme and host, so it can't match `content:` URIs by extension; filter by MIME type. | https://developer.android.com/guide/topics/manifest/data-element |
| WorkManager | Stable 2.12.0 (2026-09-23), `minSdk` 24 and `compileSdk` ≥ 33, both met (24 and 37). Expedited work fits short tasks but needs `getForegroundInfo()` on Android 11 and earlier, so this import uses regular work, which WorkManager starts at once while the app is in the foreground. | https://developer.android.com/jetpack/androidx/releases/work, https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work |
| `dataSync` services | Capped at 6 h per 24 h on Android 15+; Google recommends WorkManager instead. | https://developer.android.com/develop/background-work/services/fgs/timeout |
| AGP 9 modules | Built-in Kotlin replaces only `kotlin-android`; a plain JVM module still applies `org.jetbrains.kotlin.jvm`, as `core/bibtex` already does through `hashiya.jvm.library`. | https://developer.android.com/build/migrate-to-built-in-kotlin |
| iOS picker | SwiftUI `fileImporter` returns security-scoped URLs: call `startAccessingSecurityScopedResource()` and balance it. | https://developer.apple.com/documentation/swiftui/view/fileimporter(ispresented:allowedcontenttypes:oncompletion:) |
| UTTypes | Undeclared formats go in `UTImportedTypeDeclarations`; `public.` is reserved, so the identifiers are the app's own; conform to `public.plain-text`; tag with extensions and MIME types. | https://developer.apple.com/documentation/uniformtypeidentifiers/defining-file-and-data-types-for-your-app |
| Open In | `CFBundleDocumentTypes` plus `LSSupportsOpeningDocumentsInPlace`; `onOpenURL` delivers the file URL. | https://developer.apple.com/documentation/bundleresources/information-property-list/lssupportsopeningdocumentsinplace, https://developer.apple.com/documentation/swiftui/view/onopenurl(perform:) |
| iOS background | `beginBackgroundTask` gives finite extra time; `BGContinuedProcessingTask` is iOS 26+ only and needs a Live Activity, so iOS 17 uses the former plus resume on launch. | https://developer.apple.com/documentation/uikit/uiapplication/beginbackgroundtask(withname:expirationhandler:), https://developer.apple.com/documentation/backgroundtasks/bgcontinuedprocessingtask |
| SwiftPM resources | Test resources need `resources: [.copy(…)]`; `Bundle.module` exists only when a target declares resources. Xcode 26.3 ships Swift 6.2.3. | https://developer.apple.com/documentation/xcode/bundling-resources-with-a-swift-package |

### Library or hand-written parsers

No maintained parser exists for both Kotlin and Swift with the same behaviour, and the hard part (LaTeX decoding, name forms, DOI recovery, error recovery) is application policy that would sit on top of any library anyway. The rules below fit in a few hundred lines per platform, and the shared fixtures keep the two platforms identical. So the parsers are written here, with no new dependency.

## 4. Parsing (`core/importer`, `HashiyaImport`)

Both modules are pure: no I/O, no Android or UIKit, no network. They depend only on the model module (`core/model`, `HashiyaModel`) for `normalizeDoi` and `searchableText`.

```kotlin
enum class ImportFormat { BibTeX, Ris }

/** One reference read from the file, with the fields Hashiya uses. Empty strings are null. */
data class ImportedEntry(
    val line: Int,              // 1-based line where the entry starts ('@' or TY)
    val entryType: String,      // BibTeX type lowercased ("article"), or the RIS TY code ("JOUR")
    val citeKey: String?,       // BibTeX only
    val title: String,          // "" when the entry has none
    val authors: List<String>,  // display names, "Given Family", in file order
    val year: Int?,
    val venue: String?,
    val doi: String?,           // normalizeDoi form: lowercase, no prefix
    val url: String?,
    val abstract: String?,
    val volume: String?,
    val issue: String?,
    val firstPage: String?,
    val lastPage: String?,
    val publisher: String?,
    val workType: String?,      // OpenAlex vocabulary, so core/bibtex exports the right entry type
    val sourceType: String?
)

data class ImportIssue(val line: Int, val kind: ImportIssueKind)   // Malformed, MissingType, Empty
data class ImportParseResult(val format: ImportFormat, val entries: List<ImportedEntry>, val issues: List<ImportIssue>)

object ImportParser {
    /** Null when the text is neither BibTeX nor RIS. */
    fun parse(text: String): ImportParseResult?
    /** UTF-8 with any BOM dropped; Windows-1252 when the bytes aren't valid UTF-8. */
    fun decode(bytes: ByteArray): String
}
```

### 4.1 Text, encoding and format

1. Bytes are decoded as UTF-8. A leading BOM (`EF BB BF`) is dropped. If the bytes are not valid UTF-8, they are decoded as Windows-1252 (old EndNote exports).
2. `\r\n` and lone `\r` become `\n` before parsing, so line numbers are the same for every line ending.
3. The format comes from the content: the first non-blank line starting with a RIS tag line (`TY  -`, §4.3) means RIS; otherwise any `@` followed by letters and `{` or `(` means BibTeX; otherwise the file is unsupported. The extension is not trusted, since pickers and share sheets often lose it.
4. "Whitespace" is always the fixed set space, tab, `\n`, `\r`, form feed, vertical tab and U+00A0, so both platforms agree. Every text field has runs of whitespace collapsed to one space and is trimmed; an empty result is null.

### 4.2 BibTeX and BibLaTeX

**Splitting.** The text is split into chunks at every line whose first non-whitespace character is `@` followed by letters, optional whitespace and `{` or `(`. Each chunk is parsed on its own, in order. An entry with an unbalanced brace therefore ends at the next entry instead of swallowing the rest of the file. (A line inside an abstract that starts with `@article{` would split it; this never occurs in real exports.) Text before the first `@` is ignored, as BibTeX does.

**Entries.** In each chunk: `@`, the type (letters, compared lowercased), `{` or `(`, then:

- `comment`: ignored. `preamble`: ignored.
- `string`: `name = value` defines a macro (name lowercased) for later entries.
- Anything else is a reference: the key up to the first `,` (trimmed; null if empty), then `name = value` fields separated by commas, an optional trailing comma, and the closing `}` or `)`.
- Field names are lowercased. A value is one or more parts joined by `#`: `{…}` with balanced braces, `"…"` (ending at a `"` outside braces), a number, or a macro name. `jan`…`dec` expand to `January`…`December`; an unknown macro expands to nothing, as in BibTeX.
- A syntax error, or reaching the end of the chunk before the closing delimiter, records `Malformed` at the entry's line and skips the entry. The other chunks are unaffected.

**Names.** `author` (or `editor` when there is no author) is split on the word `and` surrounded by whitespace, outside braces, case-insensitively. For each name, before decoding:

1. `others` is dropped.
2. A name wholly inside one pair of braces (`{World Health Organization}`) is a corporate name, kept as written.
3. BibLaTeX's extended form (`family=Linde, given=Anja, prefix=von der, useprefix=true`) becomes `given prefix family suffix`.
4. Otherwise it is split on commas outside braces: no comma keeps "First von Last" as written; one comma ("von Last, First") becomes "First von Last"; two ("von Last, Jr, First") become "First von Last Jr".

**Fields.**

| Entry field | From |
|---|---|
| `title` | `title` |
| `year` | the first run of exactly four ASCII digits in `year`, else in `date` |
| `venue` | `journal`, `journaltitle`, `booktitle`, `school`, `institution`, then `howpublished` unless it holds a URL |
| `volume`, `issue` | `volume`; `number`, else `issue` |
| `firstPage`, `lastPage` | `pages` split on the first run of `-` or `–`; one value fills `firstPage` only |
| `publisher`, `abstract` | `publisher`; `abstract` |
| `url` | `url`, else the `\url{…}` (or bare `http…`) in `howpublished` |
| `doi` | the first of: `doi`; an arXiv eprint (`eprint` with `archiveprefix` or `eprinttype` = arXiv, or Mendeley's `arxivid`), as `10.48550/arxiv.<id>` without version; `url` when it is a doi.org or arxiv.org link; `journal` of the form `arXiv preprint arXiv:<id>` (Google Scholar) |

**LaTeX decoding**, for every text field except `doi` and `url` (those only lose braces and the escapes `\_ \% \& \#`):

- Accents `\' \` \^ \" \~ \= \. \u \v \H \c \k \r \d \b \t`, with the letter as the next character or in braces (`\'e`, `\'{e}`, `{\"u}`, `\v{s}`, `\c c`). `\i` and `\j` as the accented letter give `i` and `j`. The combining mark goes after the first character and the field is NFC-normalised, so `\"{\i}` gives `ï`.
- Letters: `\ss ß`, `\aa å`, `\AA Å`, `\ae æ`, `\AE Æ`, `\oe œ`, `\OE Œ`, `\o ø`, `\O Ø`, `\l ł`, `\L Ł`, `\i ı`, `\j ȷ`. A control word swallows one following space and an immediately following `{}`.
- Escapes `\& \% \$ \# \_ \{ \}` give the character; `\ ` and `\\` give a space.
- Text symbols: `\textendash –`, `\textemdash —`, `\textquoteleft ‘`, `\textquoteright ’`, `\textquotedblleft “`, `\textquotedblright ”`, `\ldots`/`\dots`/`\textellipsis …`, `\textasciitilde ~`, `\textasciicircum ^`, `\textbackslash \`, `\textunderscore _`, `\textbraceleft {`, `\textbraceright }`, `\S §`, `\P ¶`, `\copyright ©`, `\textregistered ®`, `\texttrademark ™`, `\dag †`, `\LaTeX`, `\TeX`, and Greek letters `\alpha`…`\omega`, `\Gamma`…`\Omega`.
- Any other command is dropped and its braced argument kept: `\emph{Approach}` → `Approach`, `\url{x}` → `x`.
- Braces are removed, `$` is removed, `~` is a space, `---` is `—`, `--` is `–`, ` `` ` is `“` and `''` is `”`.
- Everything else, including Arabic and other non-Latin text, is kept as is.

**Types.** `workType`/`sourceType` use OpenAlex's vocabulary, so `core/bibtex` (§6.1 of the collections spec) exports the same entry type back:

| BibTeX type | workType | sourceType |
|---|---|---|
| `article` | `article` | `journal` |
| `inproceedings`, `conference` | `article` | `conference` |
| `book`, `mvbook`, `proceedings` | `book` | – |
| `incollection`, `inbook`, `bookinbook` | `book-chapter` | – |
| `phdthesis`, `mastersthesis`, `thesis` | `dissertation` | – |
| `techreport`, `report` | `report` | – |
| anything else (`misc`, `online`, `unpublished`…) | – | – |

Then, an entry that is arXiv-identified (its DOI starts with `10.48550/arxiv.`) becomes `preprint`/`repository` when its type had no workType, or when its `journal` is `arXiv preprint …`.

**Empty entries.** An entry with no title and no DOI records `Empty` and is skipped.

### 4.3 RIS

**Lines.** A tag line is two characters (`A`–`Z`, then `A`–`Z` or `0`–`9`), one or two spaces, `-`, an optional space and the value. Any other non-blank line continues the previous tag's value, joined with a space (wrapped abstracts). Blank lines are ignored.

**Records.** `TY` starts a record and `ER` ends it. A `TY` while a record is open closes that record first, and so does the end of the file, so a missing `ER` loses nothing. Tag lines outside a record (no `TY`) are skipped, with one `MissingType` issue at the first such line.

**Fields.**

| Entry field | From |
|---|---|
| `title` | `TI`, else `T1` |
| `authors` | `AU` and `A1` in order; `A2` (editors) only when there are none. "Last, First" becomes "First Last"; "Last, First, Suffix" becomes "First Last Suffix"; a name without a comma is kept. |
| `year` | first four-digit run of `PY`, else `Y1`, else `DA` |
| `venue` | `T2`, `JF`, `JO`, `BT`, `JA`, `J2`; none for `BOOK`, `EBOOK`, `EDBOOK`; `THES` and `RPRT` fall back to `PB` |
| `volume`, `issue` | `VL`; `IS` |
| `firstPage`, `lastPage` | `SP` and `EP`; an `SP` like `770-778` without `EP` is split |
| `publisher`, `abstract`, `url` | `PB`; `AB`, else `N2`; `UR` |
| `doi` | the first of `DO`, `UR`, `L3`, `M3`, `N1` that is a DOI, a `doi:` value, a doi.org link, or an arxiv.org link (as its arXiv DOI) |

`ID` is ignored: RIS has no cite key, so the next export assigns one as usual.

**Types.**

| TY | workType | sourceType |
|---|---|---|
| `JOUR`, `JFULL`, `EJOUR` | `article` | `journal` |
| `MGZN`, `NEWS` | `article` | – |
| `CONF`, `CPAPER` | `article` | `conference` |
| `BOOK`, `EBOOK`, `EDBOOK` | `book` | – |
| `CHAP`, `ECHAP` | `book-chapter` | – |
| `THES` | `dissertation` | – |
| `RPRT` | `report` | – |
| anything else | arXiv-identified: `preprint`/`repository`; otherwise – | |

A record with no title and no DOI records `Empty` and is skipped.

## 5. Planning the import (pure, in the same modules)

```kotlin
data class TitleKey(val title: String, val year: Int?)
fun titleKey(title: String, year: Int?): TitleKey?

/** What is saved; the imported* sets are the papers this import saved before an interruption. */
data class ExistingLibrary(
    val dois: Set<String> = emptySet(), val titleKeys: Set<TitleKey> = emptySet(),
    val importedDois: Set<String> = emptySet(), val importedTitleKeys: Set<TitleKey> = emptySet()
)

sealed interface PlannedEntry {
    val entry: ImportedEntry
    data class ToImport(...)        // looked up when it has a DOI, else saved from the file
    data class InLibrary(...)       // already saved: skipped
    data class AlreadyImported(...) // saved by this import before it was interrupted: counts as imported
    data class Repeated(...)        // same as an earlier entry in this file: skipped
}

object ImportPlanner { fun plan(entries: List<ImportedEntry>, existing: ExistingLibrary): List<PlannedEntry> }
```

- **Title key:** `searchableText(title)` with every run of characters that are not letters or digits replaced by one space, trimmed, plus the year.
- **Repeated in the file:** an entry whose DOI, or (when it has no DOI) whose title key, matches an earlier entry. The first one wins.
- **Already in the library:** the DOI is a saved paper's DOI, or (no DOI) the title key matches a saved paper's.
- **Already imported:** checked before "already in the library", against the papers this job saved (`import_job_papers`), so a resumed import counts them as imported.
- After the lookups, a matched entry whose OpenAlex id is already saved also counts as already in the library, and two entries that match the same work are one paper (the second is "repeated").

## 6. Matching with OpenAlex

### 6.1 Requests

- DOIs are sent 50 at a time: `GET /works?filter=doi:10.a/b|10.c/d|…&per_page=50&select=<WORK_FIELDS>`, through the existing `OpenAlexLookupDataSource.findWorks` (iOS `works(filter:perPage:)`). 50 is the safe number while the docs disagree between 50 and 100, and it is below the `per_page` maximum of 100, so one page always holds every match.
- A DOI containing `|`, `,` or whitespace can't go in the filter; it is looked up alone with `getWork("doi:…")`, which OpenAlex doesn't charge for.
- Results are matched back by `normalizeDoi(work.doi)`. If OpenAlex returns two works for one DOI, the first is used.
- Requests are sent one at a time, at least 100 ms apart, under the keyless limit of 10 per second.

### 6.2 What a match means

A matched entry is saved from the OpenAlex work, exactly as a paper saved from Search (abstract, authors with ids, citation count, open-access PDF, publication details), plus the file's cite key (§7.3). The file's other fields are not merged in: OpenAlex's record is the more complete one, and mixing sources would make the details inconsistent.

### 6.3 Cost and quota

The quota system (`2026-10-05-openalex-quota-design.md`) already meters `GET /works?filter=` as a list call ($0.10 per 1,000) and routes it User → Shared (the built-in key or proxy, under the per-device daily cap `dailyDeviceCalls`, default 60) → Keyless → Out, and the search cache stores filter responses for 24 hours. The import uses it as it is, with one addition:

- **A batch is one metered call**, whatever the number of DOIs in it. A 500-entry file costs at most 10 calls.
- **The import may use at most half of the day's shared allowance.** Its batches are tagged as import calls (Android: a Retrofit `@Tag` on a new `findWorksForImport`; iOS: `importWorks(filter:perPage:)`), and for them the quota offers the Shared route only while the device has made fewer than `dailyDeviceCalls / 2` shared calls today (rounded down). Every shared call still counts toward the same cap, so a large import never takes the half that searches rely on: with the default 60, an import can use 30 shared calls (1,500 DOIs) and searches keep at least 30. With a personal key, the User route is used as for any request.
- **Past that half, batches go keyless**, the route searches fall back to anyway; a 429 there marks the keyless budget used up as for any metered call.
- **When no metered route is left** (`DailyLimit`), the remaining DOIs are looked up one at a time with `GET /works/doi:…`, which OpenAlex doesn't charge for and the quota never blocks, at most 10 per second. So a large import finishes matching without spending any search budget, only more slowly.
- **Resuming costs nothing extra:** the batches the analysis sent are answered from the search cache for 24 hours.
- A connectivity failure stops the lookups; the remaining entries are saved from the file and counted as "not checked". Any other failure (5xx, malformed response) marks that batch "not checked" and goes on with the next. Per-second 429s are already waited out and retried by the quota layer.
- The separate 40-request cap of the first version is gone: the quota decides.

## 7. Android data layer

### 7.1 Model (`core/model`)

```kotlin
const val LOCAL_PAPER_ID_PREFIX = "local-"
val Paper.isLocal: Boolean get() = openAlexId.startsWith(LOCAL_PAPER_ID_PREFIX)

enum class ImportState { Analyzing, Ready, Importing, Done, Failed, Cancelled }
enum class ImportFailure { Unsupported, TooLarge, Unreadable, NothingToImport }

data class ImportCounts(
    val found: Int = 0, val matched: Int = 0, val local: Int = 0, val inLibrary: Int = 0,
    val repeated: Int = 0, val unreadable: Int = 0, val notChecked: Int = 0
) { val toImport: Int get() = matched + local }

data class ImportJob(
    val id: Long, val fileName: String, val state: ImportState, val counts: ImportCounts,
    val processed: Int, val imported: Int, val addToCollection: Boolean, val collectionName: String,
    val collectionId: Long?, val failure: ImportFailure?
)
```

### 7.2 Local papers

A local paper is stored like any other, with:

- `open_alex_id = "local-" + UUID`, so Details, notes, status, collections, PDFs (attach), citations, Remove and Undo work unchanged;
- the entry's DOI, title, authors (no author ids), year, venue, abstract and publication details (`workType`, `sourceType`, `publisher`, `volume`, `issue`, pages);
- `citation_count = 0`, `is_open_access = 0`, and `oa_pdf_url` only when the entry's URL ends in `.pdf`. `Paper` has no general URL field, and adding one is out of scope;
- `details_fetched = 1`, so the citation refetch never asks OpenAlex about it. `RoomCitationRepository` also skips local ids explicitly, and the PDF download's fallback (which asks OpenAlex for a work's other locations) treats a local paper as having none.

No row is ever written with a null `open_alex_id`, so `asPaper()` keeps its guard.

### 7.3 Cite keys

- A BibTeX entry's key is stored in `papers.cite_key` when no saved paper has it. If one does, the key gets the first free suffix `a`, `b` … `z`, `aa` …, as `CiteKeys.assign` does, so it stays recognisable. Keys are compared exactly, as the unique index does.
- Keys that are already safe in BibTeX are kept as written (`Devlin2019`, `ronneberger_u-net_2015`, `heDeepResidualLearning2016`). A key with characters BibTeX can't take in a key (whitespace, `,`, `{`, `}`, `"`, `#`, `%`, `'`, `(`, `)`, `=`, `\`) is dropped, and the next export assigns one.
- RIS entries have no key; the next export assigns one.

### 7.4 Schema version 6

`main` is at Room version 5 (the PDF columns). Version 6 adds new tables only; nothing existing is touched. iOS adds the same two tables in GRDB migration `v6` (§12).

```sql
CREATE TABLE import_jobs (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  file_name TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  state TEXT NOT NULL,                 -- analyzing | ready | importing | done | failed | cancelled
  failure TEXT,                        -- unsupported | too_large | unreadable | nothing_to_import
  add_to_collection INTEGER NOT NULL,
  collection_name TEXT NOT NULL,       -- from the file name; made unique when the collection is created
  collection_id INTEGER,               -- set when the collection is created, at the start of the import phase
  found INTEGER NOT NULL, matched INTEGER NOT NULL, local INTEGER NOT NULL, in_library INTEGER NOT NULL,
  repeated INTEGER NOT NULL, unreadable INTEGER NOT NULL, not_checked INTEGER NOT NULL,
  processed INTEGER NOT NULL, imported INTEGER NOT NULL
);
CREATE TABLE import_job_papers (
  job_id INTEGER NOT NULL REFERENCES import_jobs(id) ON DELETE CASCADE,
  paper_id TEXT NOT NULL REFERENCES papers(id) ON DELETE CASCADE,
  PRIMARY KEY (job_id, paper_id)
);
CREATE INDEX index_import_job_papers_paper_id ON import_job_papers(paper_id);
```

`collection_id` is not a foreign key: a collection deleted during an import only means its links are skipped. `6.json` is generated from these entities with Room's identity-hash algorithm (reproduced exactly for `1.json`–`5.json`). The import tables are not part of the `.hashiya` backup: a backup holds papers, notes and collections, which is where imported papers end up. The file's decoded text is kept in `filesDir/imports/<job id>.txt`, not in the database: a `.bib` can be several megabytes, beyond a cursor window. It is deleted when the job is dismissed.

### 7.5 The job

`ImportRepository` (interface in `core/data`, fake in `core/testing`):

```kotlin
interface ImportRepository {
    /** The latest job the user hasn't dismissed, or null. */
    fun observeCurrentJob(): Flow<ImportJob?>
    /** Copies the text, creates the job in Analyzing (or Failed) and schedules it. A job still running is returned instead. */
    suspend fun start(fileName: String, bytes: ByteArray): ImportJob
    /** From Ready: stores the collection choice and schedules the import phase. */
    suspend fun confirm(jobId: Long, addToCollection: Boolean)
    suspend fun cancel(jobId: Long)
    /** Forgets a finished, failed or cancelled job and deletes its file. */
    suspend fun dismiss(jobId: Long)
}
```

The logic (`ImportRunner`, `DefaultImportRepository`) is written against a small internal `ImportStore` interface, implemented by `RoomImportStore` over the DAOs, so it is tested on the plain JVM with an in-memory store that follows the same rules; `RoomImportStoreTest` covers the Room side.

`ImportRunner.run(jobId)` does one phase, chosen by the stored state, and is safe to run again at any point:

1. **Analyzing.** Read the file, parse, plan against the library, look up DOIs (§6), store the counts and move to `Ready`, or to `Failed(NothingToImport)` when nothing is left to import. The lookup results are kept in an in-memory cache keyed by job, so the import phase normally sends no requests.
2. **Importing.** Create the collection on the first run if asked (name = file name without extension, trimmed to 60 characters; `"<name> (2)"`, `"<name> (3)"` … if taken), parse and plan again, look up whatever the cache lacks (after process death), then save in batches of 50, each batch one transaction that writes the papers, their authors, search rows, cite keys, `import_job_papers` rows and collection links, and updates `processed`/`imported`. Then `Done`.
3. Re-planning makes resuming safe: papers this job already saved are now in the library and are found through `import_job_papers`, so they count as imported (and are put in the collection if missing), not as duplicates. Nothing is saved twice.

A worker in `core/data` (`ImportWorker`, a `CoroutineWorker`) runs `ImportRunner.run` and gets it through a Hilt `@EntryPoint`, so no `hilt-work` or custom `WorkerFactory` is needed. Work is unique per job (`import-<id>`, `ExistingWorkPolicy.APPEND_OR_REPLACE`, so a confirm that arrives while the analysis run is finishing queues behind it). WorkManager persists it, so after process death it runs again, and the runner continues from the stored state. Cancel cancels the work and marks the job `Cancelled`; papers already saved stay.

Only one job runs at a time. Picking a new file while a job is analyzing or importing shows that job instead.

### 7.6 Limits

- Files larger than **10 MB** fail with `TooLarge` before parsing (a 5,000-entry Zotero export with abstracts is about 5 MB).
- Lookups are limited by the quota as in §6.3.

## 8. Android UI (`feature/importer`)

### 8.1 Entry points

- **Library:** an **Import** icon button in the top bar (content description "Import .bib or .ris"), before Export, on every Library state, including the empty Library. It opens the Import screen. `LibraryActions.onImport` is nullable and null by default, so screens built without it (the existing screenshot tests) are unchanged.
- **Other apps:** `MainActivity` gains intent filters for `ACTION_VIEW` and `ACTION_SEND` with `application/x-bibtex`, `text/x-bibtex` and `application/x-research-info-systems`, and handles `ACTION_SEND` of `text/plain` that carries `EXTRA_STREAM` and no `EXTRA_TEXT` as an import too. These are recognised before the backup check, which opens any other `content:` VIEW as a `.hashiya` restore. The file's URI is passed to the Import screen, which reads it at once while the grant is valid.
- **Layout:** like Settings and Restore, Import is not a top-level destination: the bar hides on compact windows and the rail stays from medium width; the content is a centred column on wide windows.

### 8.2 The Import screen

A full-screen destination (`ImportRoute(uri: String? = null)`) with a back arrow, title "Import". It shows the current job, or the start state:

| State | Content |
|---|---|
| No job | Icon, "Import from BibTeX or RIS", "Export your library from Zotero, Mendeley, EndNote or Overleaf as a .bib or .ris file, then choose it here." and **Choose file**, which launches `OpenDocument` with `*/*`. |
| Reading / Analyzing | Progress indicator, "Reading the file…" while the picked file is read, then "Checking <file name> with OpenAlex…" with **Cancel**. |
| Ready | The file name, then rows: Found, Matched on OpenAlex, Kept from the file, Already in your library, Repeated in the file, Couldn't be read; rows with 0 are hidden except Found. A note when Not checked > 0: "N couldn't be checked online and will be kept from the file." A checkbox "Add to a new collection" with the name under it. **Import N papers** and **Cancel**. |
| Importing | Linear progress `processed / toImport`, "Importing N of M…", "You can leave the app; the import continues." **Cancel**. |
| Done | "Imported N papers" and "Added to <collection>" when a collection was made, **Show in library** (back to the Library, where the new collection is in the selector) and **Import another file**. |
| Failed | Unsupported: "This file isn't BibTeX or RIS." TooLarge: "This file is too large to import (over 10 MB)." Unreadable: "Couldn't read this file." NothingToImport: "Everything in this file is already in your library." **Choose another file**. |
| Cancelled | "Import cancelled. Papers already imported stay in your library." **Choose another file**. |

Leaving the screen never cancels the job. Coming back shows the job where it is.

### 8.3 Strings (both locales)

| Key | English | Arabic |
|---|---|---|
| `import_title` | Import | استيراد |
| `import_back` | Back | رجوع |
| `import_start_title` | Import from BibTeX or RIS | الاستيراد من BibTeX أو RIS |
| `import_start_message` | Export your library from Zotero, Mendeley, EndNote or Overleaf as a .bib or .ris file, then choose it here. | صدّر مكتبتك من Zotero أو Mendeley أو EndNote أو Overleaf كملف ‎.bib أو ‎.ris، ثم اختره هنا. |
| `import_choose_file` | Choose file | اختيار ملف |
| `import_choose_another` | Choose another file | اختيار ملف آخر |
| `import_reading` | Reading the file… | جارٍ قراءة الملف… |
| `import_analyzing` | Checking %1$s with OpenAlex… | جارٍ فحص %1$s في OpenAlex… |
| `import_count` | %1$d | %1$d (formatted in the app's locale) |
| `import_found` | Found | عُثر عليها |
| `import_matched` | Matched on OpenAlex | مطابقة في OpenAlex |
| `import_local` | Kept from the file | محفوظة من الملف |
| `import_in_library` | Already in your library | موجودة في مكتبتك |
| `import_repeated` | Repeated in the file | مكررة في الملف |
| `import_unreadable` | Couldn\'t be read | تعذّرت قراءتها |
| `import_not_checked` (plural) | %1$d entry couldn\'t be checked online and will be kept from the file. / … | (Arabic plural forms) |
| `import_add_to_collection` | Add to a new collection | إضافة إلى مجموعة جديدة |
| `import_confirm` (plural) | Import %1$d paper / Import %1$d papers | استيراد %1$d ورقة (Arabic plural forms) |
| `import_cancel` | Cancel | إلغاء |
| `import_progress` | Importing %1$d of %2$d… | جارٍ استيراد %1$d من %2$d… |
| `import_continues` | You can leave the app; the import continues. | يمكنك مغادرة التطبيق؛ سيستمر الاستيراد. |
| `import_done` (plural) | Imported %1$d paper / Imported %1$d papers | تم استيراد %1$d ورقة (Arabic plural forms) |
| `import_done_collection` | Added to %1$s | أُضيفت إلى %1$s |
| `import_show_library` | Show in library | عرض في المكتبة |
| `import_another` | Import another file | استيراد ملف آخر |
| `import_failed_unsupported` | This file isn\'t BibTeX or RIS. | هذا الملف ليس بصيغة BibTeX أو RIS. |
| `import_failed_too_large` | This file is too large to import (over 10 MB). | هذا الملف أكبر من أن يُستورد (أكثر من 10 ميغابايت). |
| `import_failed_unreadable` | Couldn\'t read this file. | تعذّرت قراءة هذا الملف. |
| `import_failed_nothing` | Everything in this file is already in your library. | كل ما في هذا الملف موجود في مكتبتك بالفعل. |
| `import_cancelled` | Import cancelled. Papers already imported stay in your library. | أُلغي الاستيراد. تبقى الأوراق المستوردة في مكتبتك. |
| `library_import` (library) | Import .bib or .ris | استيراد ملف ‎.bib أو ‎.ris |

"BibTeX", "RIS", "OpenAlex" and the app names stay in Latin script in Arabic; ".bib" and ".ris" get a left-to-right mark so the dot stays attached.

### 8.4 Analytics

Following the analytics spec's closed lists (nothing typed or read leaves the device):

| Event | Parameters (values) | Sent when |
|---|---|---|
| `import` | `format`: `bibtex` / `ris`; `result`: `ok` / `cancelled` / `unsupported` / `too_large` / `unreadable` / `nothing_new`; `entries_bucket`: `0` / `1-50` / `51-500` / `501-5000` / `5000+` (the library-size buckets, on entries found); `matched`: `none` / `some` / `most` / `all` (matched over entries to import: 0, under half, half or more, all); `to_collection`: `yes` / `no` | an import ends: done, cancelled or failed — once per job |

- The `screen` value list gains `import` (also the crash `screen` key) for the Import screen.
- Imported papers don't send `paper_saved` (one import would send thousands), and the new collection doesn't send `collection_created` or `paper_added_to_collection`: the `import` event's `to_collection` covers it.
- Never sent: file names, entry titles, DOIs, cite keys, counts beyond the buckets.
- Android: `AnalyticsEvent.Import` in `:core:analytics`, logged by `ImportRunner`. iOS: `AnalyticsEvent.importFile(...)` in `HashiyaDiagnostics`, logged by the import repository. The Firebase mapping is unchanged: events carry their own name and parameters.
- No store-answer or privacy-policy change: this is App activity / Product interaction, already declared, and the policy's "which features are used" covers it.

## 9. Errors

| Case | Behaviour |
|---|---|
| The file can't be opened or read (revoked grant, provider error) | Failed(Unreadable). |
| Neither BibTeX nor RIS | Failed(Unsupported). |
| Over 10 MB | Failed(TooLarge), before decoding. |
| Malformed entries | Skipped, counted as "Couldn't be read"; the rest import. |
| Every entry already saved or repeated | Failed(NothingToImport). |
| Offline, 429 or the 40-request cap during matching | Remaining DOIs are "not checked"; those entries are saved from the file. |
| The app is killed while analyzing | WorkManager runs the job again; analysis restarts (it saves nothing). |
| The app is killed while importing | WorkManager runs it again; the runner re-plans and continues; nothing is saved twice. |
| A batch transaction fails (disk full) | The worker returns failure; the job becomes Failed(Unreadable) with the papers of earlier batches kept. |
| A cite key taken between planning and saving | `insertPaperWithAuthors` drops it, as for Undo; the next export assigns one. |
| The collection name is taken | The next free `"<name> (n)"`. |
| The collection is deleted during the import | Links to it are skipped (`insertLinkIfCollectionExists`); papers are still saved. |

## 10. Fixtures and tests

### 10.1 Shared fixtures

`testdata/import/` holds real-shape files; `testdata/import/expected/<file>.json` holds the exact parse result each platform must produce: `format`, every entry with all 17 fields (nulls included), and every issue.

| File | Shape | What it covers |
|---|---|---|
| `zotero.bib` | Zotero built-in: tabs, trailing commas, `month = may`, UTF-8 | journal article; arXiv `@misc` with an arXiv DOI → preprint; `@inproceedings` with editors (ignored) and a DOI with `_`; `@phdthesis` with Unicode |
| `better-bibtex.bib` | Better BibTeX, BibTeX mode | `{{Nature Publishing Group}}`, `{{An}}`, `{\"u}`, `{\'a}{\v s}`, `archiveprefix`/`eprint` → arXiv DOI |
| `better-biblatex.bib` | Better BibLaTeX | `date`, `journaltitle`, `@online` with `eprinttype = {arXiv}` and a versioned eprint, `@book`, `@incollection`, the extended name form |
| `mendeley.bib` | Mendeley Desktop | header text before the first entry, `{{…}}` titles, `\_` in a DOI, `arxivId`/`eprint` without a `doi` field, `file` with `$\backslash$`, a `dx.doi.org` URL |
| `google-scholar.bib` | Google Scholar | no DOIs, lowercase titles, `{\L}ukasz`, `arXiv preprint arXiv:…` → preprint, `@book`, `@phdthesis` |
| `nasty.bib` | hand-made, UTF-8 BOM, CRLF | Arabic title, authors and journal; LaTeX accents, `\H`, `\c{c}`, `{\ss}`, `{\AA}`, `\v{S}`, `{\'\i}`, `{\o}`, `{\L}`; `\emph`, `$\alpha$`, `---`, ` ``…'' `, `\%`, `\&`; `@comment`, `@preamble`, three `@string`s (one in parentheses, one with `#`); uppercase types and fields; a DOI given as a URL; `and others`; an eprint without DOI; **an entry missing its closing brace in the middle**; a `(…)`-delimited entry with unquoted numbers and a DOI only in `url`; a corporate author; editors only; a report with no identifier; an entry with nothing to import; the same paper twice (by DOI) |
| `zotero.ris` | Zotero RIS, CRLF | `DA 2015/05//`, `A2` editors excluded, `CHAP` with the book as venue, `THES` with the university in `PB` |
| `endnote.ris` | EndNote, CRLF | `T1`, `JO`/`JA`, `Y1`, a wrapped `N2`, the DOI in `M3`, `SP 770-778` without `EP`, a corporate author, `ID` ignored |
| `mendeley.ris` | Mendeley | `CPAPER`, `JF`, an arXiv `UR` → arXiv DOI |
| `nasty.ris` | hand-made, UTF-8 BOM, CRLF | Arabic everything, a wrapped Arabic abstract, a DOI only in `UR`, **a record without `TY` in the middle**, an editors-only `EDBOOK` with "Last, First, Jr.", `DO  - doi: …`, an empty record, a `.pdf` URL, a last record without `ER` |
| `endnote-cp1252.ris` | old EndNote, Windows-1252, CRLF | the Windows-1252 fallback (`ö`, `Ü`, `ä`, curly quotes) |

### 10.2 Android tests (all written before the code)

- **`core/importer`** (JUnit, plain JVM): one test per fixture comparing the whole result with its JSON; unit tests for every rule in §4 (each accent and command, name forms, field fallbacks, DOI recovery order, type tables, line endings, BOM, Windows-1252, format sniffing, chunk recovery, RIS continuation lines, stray tags, missing `ER`); `ImportPlanner` (repeated by DOI and by title key, in library by DOI/title key, first wins); cite-key sanitising and suffixes; `LocalPaper` mapping (local id, `.pdf` rule, publication details).
- **`core/network`**: the quota's import share (an import call gets Shared below half the cap, Keyless above, and a search still gets Shared up to the full cap; every shared import call counts); `findWorksForImport` carries the tag and is cached like `findWorks`.
- **`core/analytics`**: the `import` event's parameters and buckets; `Screen.Import`.
- **`core/database`**: migration 5 → 6 (all v5 data, PDFs included, unchanged; new tables empty); `ImportJobDao` (create, update counts and state, observe, delete cascades links); `PaperDao.insertImportBatch` (papers, job links and collection links in one transaction, a taken cite key dropped, a deleted collection skipped).
- **`core/data`**: `OpenAlexDoiMatcher` (batches of 50 sent as import calls, `per_page`, filter text, the singleton path for odd DOIs, 100 ms spacing, `DailyLimit` switching the rest to free single lookups, connectivity stopping everything, 5xx skipping one batch, match-back by normalised DOI); `ImportRunner` with fakes (analyze counts per fixture, NothingToImport, resume after a crash mid-import imports nothing twice and counts the job's own papers as imported, collection naming and `(2)`, cancellation, local papers and kept cite keys, one `import` event per job with the right values); `RoomCitationRepository` never refetches a local paper; `asPaper` on a local row.
- **`feature/importer`** ViewModel: every state of §8.2, start with an unreadable URI, the 10 MB limit, confirm with and without the collection, cancel, dismiss; Compose tests for the buttons.
- **Roborazzi** (English and Arabic, light and dark, like the existing screens): start, analyzing, ready (with every row and the not-checked note), importing, done (with collection), failed (unsupported), cancelled. The Library's existing baselines don't change (§8.1).
- **`app`**: the new intent filters resolve to `MainActivity`; a VIEW of a `.bib` URI opens the Import screen.

### 10.3 iOS tests

- **`HashiyaImportTests`**: the same fixture tests reading the same JSON, and the same rule tests.
- **`HashiyaDatabaseTests`**: migration `v6` adds the two tables and keeps everything; the batch insert.
- **`HashiyaNetworkTests`**: the quota's import share.
- **`HashiyaDataTests`**: the matcher and the import repository with fakes, mirroring Android's cases, including the collection and the analytics event.
- **`FeatureImportTests`** and **`HashiyaSnapshotTests`**: the view model and every state in English and Arabic, light and dark, on iOS 26 and iOS 18.

## 11. Acceptance criteria (on device)

1. Export a Zotero collection of about 100 items as BibTeX and as RIS. Import the BibTeX: the preview's numbers add up (found = matched + kept + already + repeated), and after import every item is in the library and the new collection.
2. Export that collection from Hashiya as `.bib`. Keys of papers that came with a BibTeX key are the original Zotero keys.
3. Import the RIS of the same collection: everything is "Already in your library".
4. Import a Mendeley `.bib` with arXiv papers and a Google Scholar `.bib` with no DOIs: arXiv papers match; Scholar entries become local papers, open in Details, take notes and export.
5. Start a 500-entry import, switch to another app for a minute, come back: it finished, or continues. Kill the app from Recents during the import; reopen: it resumes and the count of imported papers is right, with no duplicates.
6. Turn on airplane mode before choosing the file: entries with DOIs are "not checked" and are kept from the file.
7. Open a `.bib` attachment from Gmail or the Files app with Hashiya: the Import screen opens on it.
8. Switch to Arabic: every new screen is right-to-left and in Arabic; Arabic titles and authors from `nasty.bib` read correctly in the Library.

## 12. iOS

iOS on `main` already has collections, stored cite keys and publication details (GRDB `v4`, Android's version 4) and PDFs (`v5`). So iOS gets the whole feature, collection option included:

- **`HashiyaImport`**: a new pure Swift package target with the same API (`ImportParser`, `ImportedEntry`, `ImportPlanner`, `ImportIssue`, `ImportCiteKeys`), depending only on `HashiyaModel`. `HashiyaImportTests` loads the shared fixtures and JSON through `resources: [.copy("Fixtures")]`, where `Fixtures` is a copy of `testdata/import` (a package can't reach files outside its directory). A JVM test in `core/importer` fails when the two folders differ in any byte, so the copy can't drift.
- **Matching** in `HashiyaData` with the same rules (§6) through `OpenAlexLookupService.importWorks(filter:perPage:)`, which `OpenAlexHTTP` sends as an import call: the quota's import share applies (§6.3), and `dailyLimit` switches to free single lookups.
- **Migration `v6`**: the same `import_jobs` and `import_job_papers` tables as Android version 6 (§7.4). No cite-key migration: `cite_key` exists since `v4`.
- **Local papers** with `local-<uuid>` ids and every field Android stores, publication details included, `details_fetched = 1`.
- **Job**: `LiveImportRepository`, an actor over `PaperStore`, keeps the job and its saved papers in the `v6` tables (each batch's papers, job links, collection links and progress in one transaction, as on Android) and the decoded text in Application Support/Imports. It runs each phase inside `beginBackgroundTask`, and continues a running job at launch and whenever the app becomes active (`resumeIfNeeded`) — after a suspended database refused a write in the background, for example. The same idempotent re-planning as Android.
- **Collection**: the same option, name and `(n)` rule, created through `PaperStore.insertCollection` with the same name key as `GRDBCollectionsRepository`.
- **UI**: `FeatureImport` with the same screens and strings; an **Import** toolbar button in the Library (shown only when the app passes `onImport`, so the Library's existing snapshots don't change); `fileImporter` with `UTType`s `com.etatech.hashiya.bibtex` (`bib`, `application/x-bibtex`, `text/x-bibtex`) and `com.etatech.hashiya.ris` (`ris`, `application/x-research-info-systems`) declared in `UTImportedTypeDeclarations`, conforming to `public.plain-text`; two more `CFBundleDocumentTypes` (`LSHandlerRank` Alternate) beside the `.hashiya` backup's, so "Open in Hashiya" gets a copy; `onOpenURL` sends `.bib` and `.ris` to Import and leaves `.hashiya` to Restore.
- **Analytics**: the `import` event and screen value of §8.4, through `AnalyticsTracking`.

Parsing and planning results must equal Android's on every fixture; both read the same JSON.

## 13. Risks

| Risk | Mitigation |
|---|---|
| OpenAlex's batch limit is really 50, not 100 | Batches are 50. |
| OpenAlex's pricing changes | Matching uses the cheapest metered call and the quota system's routes, cap and remote config; free single lookups finish what the quota won't pay for. |
| A huge import makes searches slow for the day | It can take at most half of the shared allowance (§6.3); the rest of its lookups are keyless or free. |
| A wrong DOI in the file matches the wrong paper | The DOI is the user's own data; the result is what the DOI points to, as with Add by DOI. |
| A user imports the same file twice | The second run finds everything in the library and says so. |
| Arabic BibTeX from hand-made files uses LaTeX Arabic packages (`\RL{}`, ArabTeX) | Unknown commands keep their argument, so `\RL{نص}` gives `نص`; ArabTeX transliteration is not decoded. |
| Huge files are slow to plan | Planning is linear with hash sets; the 10 MB cap bounds it. |
| Local papers never get OpenAlex data | Out of scope; a later "match again" action can use the stored DOI. |

## 14. Decisions made without review

Fady asked for this feature to be built end to end without stopping for approval. These calls were made along the way and are the first things to check. Items marked **(changed)** differ from the first version (§15).

1. **Module names** `core/importer` and `feature/importer` (iOS `HashiyaImport`, `FeatureImport`): `import` is a Kotlin keyword and can't be a package segment.
2. **Hand-written parsers**, no library (§3).
3. **Matching by DOI only**, including arXiv eprints and arXiv URLs as arXiv DOIs. Title search would cost ten times more per request and can attach the wrong paper.
4. **OpenAlex wins for matched entries**; only the cite key comes from the file.
5. **Local paper ids** are `local-<uuid>` in `open_alex_id`, instead of a schema change to make every screen work on a nullable id.
6. **URLs** are kept only when they point at a PDF, because `Paper` has no URL field.
7. **Taken cite keys** get a letter suffix rather than being dropped; unsafe keys are dropped.
8. **Editors stand in for authors** when an entry has none (both formats).
9. **(changed) The quota decides how lookups are paid for** (§6.3): an import may use at most half of the day's shared allowance; past that it goes keyless, then to free single lookups. The first version's own 40-request cap is gone. "Half" is a constant in the quota code, not a remote-config field, so changing it needs a release; it could join the `openAlex` config section later.
10. **(changed) An `import` analytics event** (§8.4) with closed values only, plus the screen value `import`. No `paper_saved` per imported paper.
11. **(changed) The collection option is on by default on both platforms**, named after the file.
12. **(changed) Schema numbers:** Android Room version 6, iOS GRDB `v6`, with the same tables; no iOS cite-key migration, since `cite_key` exists.
13. **(changed) The iOS job lives in the database**, like Android's, instead of a JSON file, so a batch and its record are one transaction.
14. **(changed) The Library's Import button is opt-in in code** (`onImport` null by default on both platforms), so existing Library screenshots and snapshots are unchanged; the app always passes it.
15. **WorkManager without `hilt-work`**: the worker gets its dependencies through a Hilt entry point, so the app's WorkManager configuration stays the default. Regular (not expedited) work, to avoid a notification on Android 11 and earlier.
16. **The picker asks for `*/*`** and the content decides the format.
17. **10 MB file limit.**
18. **Lookups happen before the preview**, in the background job, so the preview can show matched counts; their results are cached in memory for the import phase, and the search cache answers them again after process death.
19. **Missing `ER`** is accepted silently, as Zotero does; a record without `TY` is reported.
20. **The Library's empty state keeps its single action** (Go to Search); Import is in the top bar on every Library state.
21. **iOS "Show in library" closes the sheet**; on iOS the text under the progress bar says the import continues "while iOS allows it" and resumes later, because iOS gives background work only limited time.
22. **`BGContinuedProcessingTask` is not used**: it is iOS 26 only and needs a Live Activity; `beginBackgroundTask` plus resume at launch and on activation covers iOS 17 to 26 the same way.
23. **No store-answer or privacy-policy change**: the import sends nothing new beyond one closed-value analytics event, which the published "which features are used" already covers.

## 15. Changes from the first version

The first version was written against `main` at `7ad31b4` (2026-10-01), before quota protection, analytics, iOS collections and cite keys, PDFs, backup and citation styles were merged. Against `c4f0085`:

- **Quota:** the import's own 40-request cap became the quota system's routes with an import share of half the daily shared allowance and a fallback to free single lookups (§6.3).
- **Analytics:** "no analytics event, because the app has none" became the `import` event and the `import` screen value (§8.4).
- **iOS collections:** the "new collection" option, Android only in the first version, is on iOS too (§12).
- **Schemas:** Android 5 → 6 (main uses 5 for PDFs); iOS cite-key migration `v4` dropped (exists), import tables in `v6`.
- **iOS job storage:** JSON file → database tables.
- **CHANGELOG:** entries go under the existing `[Unreleased]` instead of a new file.
- **Paths:** the Android project is under `android/`; `testdata/import` stays at the repository root, beside `testdata/backup`.
- **Library button:** opt-in in code, so existing screenshots don't change.
- Unchanged: the parsers, their rules and the fixtures and expected results.
