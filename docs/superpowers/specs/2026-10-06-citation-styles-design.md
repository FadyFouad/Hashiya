# Citation styles (APA 7 and IEEE)

Status: approved in conversation 2026-10-06. Android first, then iOS.

## 1. Goal

Students who write in Word, Pages or Google Docs can cite their saved papers in their university's style, not only
LaTeX users. A paper's Details copies a formatted citation; the Library exports a formatted reference list for a
collection or the whole library. Formatting is offline, from the data already saved.

## 2. Scope

| In | Out |
|----|-----|
| APA 7 and IEEE, next to the existing BibTeX | Harvard, MLA, Chicago (later; each is a new formatter) |
| Copy citation on Details (rich text + plain fallback) | A CSL engine |
| Reference-list export as `.rtf` | `.docx` export, in-text citations, citation numbering across a document |
| The last style used is remembered for copy and export | A Settings entry for the style |
| English-language citation conventions | Arabic citation conventions ("و" instead of "&", Arabic numerals) |

## 3. The formatter

A pure module with no UI and no network: Android `:core:citation` (Kotlin JVM library), iOS `HashiyaCitation`
(Swift package target).

- **Input:** a saved `Paper` with its `PublicationDetails` — the data BibTeX uses.
- **Kind of work:** article, conference paper, book chapter, book, thesis, report, preprint, other. Decided by the same
  table BibTeX uses today (`entryType` in `:core:bibtex` / `HashiyaBibTeX`), moved to a place both modules share
  (`:core:model` / `HashiyaModel`, as `WorkKind`) so citations and BibTeX can't disagree. BibTeX keeps its output
  unchanged.
- **Output:** a `StyledCitation` — an ordered list of text runs, each plain or italic.
- **Renderers** (pure functions over `StyledCitation`):
  - **Plain text** — clipboard fallback and accessibility.
  - **HTML** — `<i>…</i>` for italics, everything else escaped; the rich clipboard flavour (Android
    `ClipData.newHtmlText`, iOS `UIPasteboard` with `public.html` plus `public.utf8-plain-text`).
  - **RTF** — the exported file. Every character above U+007F is written as `\uN?` (signed 16-bit, surrogate pairs
    for characters above U+FFFF), so Arabic and other scripts survive; `\`, `{` and `}` are escaped. Each entry is
    its own paragraph; APA entries get a 0.5-inch hanging indent (`\fi-720\li720`).
- **Reference list:**
  - **APA:** sorted by first author's family name (case- and diacritic-insensitive), then year (no year last), then
    title.
  - **IEEE:** numbered `[1]`, `[2]`, … in the order the papers were saved to the library, oldest first (inside a
    collection too: the app doesn't record when a paper joined a collection).

### 3.1 Text rules

- Titles are kept exactly as saved — no forced APA sentence case (guessing proper nouns does more harm than good).
  Arabic and other right-to-left titles are kept as they are.
- **Untitled:** APA `[Untitled]`, IEEE `"Untitled"`.
- **Pages:** an en dash between first and last page; a single page is `p. x` (APA and IEEE).
- **DOI:** APA `https://doi.org/<doi>`; IEEE `doi: <doi>`. No DOI: the paper's open-access PDF URL if any (APA as is;
  IEEE `[Online]. Available: <url>`), otherwise nothing.
- A missing part is left out with its punctuation; formatting never fails.

### 3.2 Authors

- Names come as saved ("Ashish Vaswani"). Family name = the last word; initials from the other words, hyphenated
  given names keep the hyphen ("Jean-Paul Sartre" → "Sartre, J.-P." / "J.-P. Sartre").
- One-word names (organisations, mononyms) and names in Arabic script are kept whole, in the family-name position.
- **APA:** "Family, I. I." joined with ", " and ", & " before the last; with 21 or more, the first 19, ", . . . ",
  then the last.
- **IEEE:** "I. I. Family" joined with ", " and ", and " before the last (" and " for two); with 7 or more, the first
  author and *et al.* (italic run).
- No authors: APA starts with the title (then the year); IEEE starts with the title.

### 3.3 Templates

Italic parts are marked *like this*.

| Kind | APA 7 | IEEE |
|---|---|---|
| Article | Authors (Year). Title. *Journal*, *Vol*(Issue), pages. DOI | A. Author, "Title," *Journal*, vol. V, no. N, pp. x–y, Year, doi: … |
| Conference paper | Authors (Year). Title. In *Proceedings* (pp. x–y). Publisher. DOI | A. Author, "Title," in *Proceedings*, Year, pp. x–y, doi: … |
| Book chapter | Authors (Year). Title. In *Book* (pp. x–y). Publisher. DOI | A. Author, "Title," in *Book*. Publisher, Year, pp. x–y. |
| Book | Authors (Year). *Title*. Publisher. DOI | A. Author, *Title*. Publisher, Year. |
| Thesis | Authors (Year). *Title* [Thesis, Institution]. DOI | A. Author, "Title," Thesis, Institution, Year. |
| Report | Authors (Year). *Title*. Institution. DOI | A. Author, "Title," Institution, Tech. Rep., Year. |
| Preprint | Authors (Year). *Title* [Preprint]. Repository. DOI | A. Author, "Title," Repository, Year, doi: … |
| Other | Authors (Year). *Title*. Venue. DOI | A. Author, "Title," Venue, Year. |

"Venue", "Journal", "Proceedings", "Book", "Institution" and "Repository" are the saved venue; "Publisher" is
`PublicationDetails.publisher`. No year: APA "(n.d.)", IEEE leaves it out.
OpenAlex does not say whether a thesis is a master's or a doctorate, so both styles use the neutral "Thesis"
rather than guess "Doctoral dissertation" / "Ph.D. dissertation".

## 4. Screens

### 4.1 Details

- Details' ⋮ menu lists **Copy APA 7 citation**, **Copy IEEE citation** and **Copy BibTeX**, the remembered style first
  (APA 7 the first time); choosing one copies in that style and remembers it. (On iOS, wherever Copy BibTeX lives
  today gets the same three choices.)
- The confirmation names the style: "APA citation copied" / "IEEE citation copied" / "BibTeX copied" (with Arabic
  translations).
- APA and IEEE go on the clipboard as rich text with a plain fallback; BibTeX stays plain.

### 4.2 Library export

- The export action offers **BibTeX (.bib) / APA 7 (.rtf) / IEEE (.rtf)**, the remembered style first.
- File names: `<name>.bib` as today; `<name> – APA.rtf`, `<name> – IEEE.rtf` (`hashiya-library` when no collection).
- MIME type for RTF: `application/rtf` (Android), `UTType.rtf` (iOS).
- The share sheet, the "may be incomplete" message and failures behave as for BibTeX today.

### 4.3 The remembered style

One on-device preference, `citationStyle` (`apa`, `ieee`, `bibtex`), shared by copy and export: Android DataStore with
the other preferences, iOS `UserDefaults`. Backed up like the other preferences.

## 5. Data flow

`CitationRepository.entry(openAlexId)` and `export(collectionId)` gain a style parameter and keep refetching missing
details (volume, pages) first, exactly as for BibTeX; `CitationResult` carries the plain text, and for APA/IEEE the
HTML and RTF, plus `complete`. No new network calls: the refetch already exists, and formatting is offline.

## 6. Privacy and analytics

- No new events. The existing `export` event's `format` gains the closed values `apa` and `ieee`.
- **Policy:** the policy says "exports (BibTeX or backup, with or without PDFs)"; it becomes "exports (BibTeX, APA,
  IEEE or backup, with or without PDFs)", English and Arabic, in FadyFouad/Hashiya-Privacy-Policy, merged before the
  release that ships this.
- Copying a citation logs nothing, as Copy BibTeX logs nothing today.

## 7. Testing

- **Golden tests per style and kind** — one per row of §3.3, built from the APA 7 manual's and the IEEE reference
  guide's own examples, asserting the runs (text and italic flags).
- Authors: 1, 2, 3, 6, 7, 20, 21 names; a one-word name; an Arabic-script name; hyphenated given names.
- Missing year, title, pages, volume, issue, DOI (with and without an open-access URL).
- Renderers: plain; HTML escaping (`<`, `&`, quotes); RTF escaping (`\`, `{`, `}`), Unicode (Arabic, an emoji above
  U+FFFF), hanging indent for APA only.
- Reference lists: APA ordering (including diacritics and no-year), IEEE numbering by date added.
- `WorkKind`: BibTeX's existing tests stay green after the move.
- Remembered style: default APA; copy and export update it.
- Details and Library UI tests for the menu and confirmation; screenshots/snapshots in English and Arabic.

## 8. Delivery

1. Android PR (delivered): `:core:citation`, `WorkKind` move, repository style parameter, Details and Library UI, preference,
   analytics values, tests, screenshots.
2. iOS PR (delivered): the same.
3. Policy PR (one line, English and Arabic) before the release that ships either.
