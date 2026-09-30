# Sub-project 5: Collections and BibTeX export — Design

- **Date:** 2026-09-30
- **Status:** Awaiting review
- **Scope:** Fifth of six MVP sub-projects for Hashiya, Android only (builds on sub-projects 1–4; sub-project 4 merged in `ab5f552`). The iOS version gets its own spec once this one is merged.

## 1. Context

Hashiya's core loop is **Discover → Save → Read → Extract → Compare → Cite**. Sub-projects 1–4 made discovering, saving, finding and taking notes on papers work. The library is still one list, and nothing leaves the app. This sub-project covers **Compare** and **Cite**: saved papers can be grouped into collections, and a collection (or the whole library) can be exported as a `.bib` file for LaTeX, mainly Overleaf. A single paper's BibTeX can also be copied from its Details screen.

The same standards as before apply: modular architecture and dependency rules, English and Arabic with full RTL, TDD with hand-written fakes, Roborazzi screenshots with baselines recorded on CI Linux, and a green CI.

### Decisions made during brainstorming

| Topic | Decision |
|---|---|
| Main use | A `.bib` file for Overleaf or LaTeX, so entry types and fields must be right. Plus **Copy BibTeX** for one paper on Details. |
| Bibliographic fields | Stored at save time from OpenAlex (`type`, `biblio`, source type, publisher) in a schema v4 migration. BibTeX is generated on the device. Papers saved before v4 are refetched once, the first time they are exported or copied. |
| Collections | Flat (no nesting). A paper can be in any number of collections. |
| Where collections live | A selector in the Library's title ("All papers ▾"). Search and status chips work inside a collection. No new top-level tab. |
| Adding papers to collections | From Details: a Collections row opens a checklist sheet. |
| Cite keys | Google Scholar style (`vaswani2017attention`), assigned once and stored. They never change, not even after Remove and Undo. Not editable. |
| Platforms | Android in this spec. iOS follows in its own spec and plan. |

## 2. Goals and non-goals

### Goals

1. A user can create, rename and delete collections, and add a saved paper to any number of them from its Details screen.
2. The Library shows all papers or one collection, and its search and status chips work within the selected collection.
3. **Export .bib** writes every paper in the current collection (or the whole library) to a `.bib` file and opens the share sheet.
4. Each entry uses the right BibTeX type (`@article`, `@inproceedings`, `@misc`…) and fields, and compiles in Overleaf with the `plain` and `IEEEtran` styles.
5. A paper's cite key never changes once assigned, so `\cite` commands already in a thesis keep working after later exports.
6. **Copy BibTeX** on Details puts one entry on the clipboard.
7. Existing libraries upgrade in place: no paper, status, note or search result is lost.

### Non-goals

- Nested collections, collection colours or icons, or reordering collections.
- Editing cite keys.
- Importing `.bib` files.
- BibLaTeX-only entry types (`@online`) or other formats (RIS, CSL-JSON).
- Exporting notes.
- Showing a paper's collections on Library rows.
- Adding to collections from Search's preview sheet, or adding a paper by ID straight into the selected collection.
- Deduplicating papers that share a DOI.

## 3. Model (`core/model`)

```kotlin
/** Bibliographic details used for citations. Every field is null when the source has none. */
data class PublicationDetails(
    /** OpenAlex's work type, such as "article", "preprint", "book-chapter". */
    val workType: String? = null,
    /** OpenAlex's source type, such as "journal", "conference", "repository". */
    val sourceType: String? = null,
    val publisher: String? = null,
    val volume: String? = null,
    val issue: String? = null,
    val firstPage: String? = null,
    val lastPage: String? = null
)

data class Paper(
    // …existing fields unchanged…
    val publication: PublicationDetails = PublicationDetails()
)

data class Collection(val id: Long, val name: String, val paperCount: Int)
```

`PublicationDetails` keeps the OpenAlex strings as-is. `core/bibtex` interprets them, so a new OpenAlex type needs no migration.

## 4. Network (`core/network`)

- `WORK_FIELDS` gains `type,biblio`. `primary_location` is already selected and returns the whole source object.
- `NetworkWork` gains `type: String?` and `biblio: NetworkBiblio?` (`volume`, `issue`, `first_page`, `last_page`, all `String?`).
- `NetworkSource` gains `type: String?` and `host_organization_name: String?`.
- `NetworkWorkMapping` fills `Paper.publication` from these. The publisher is `primary_location.source.host_organization_name`. Blank strings become null.

## 5. Database (`core/database`), schema version 4

### 5.1 `papers` columns

New nullable `TEXT` columns: `work_type`, `source_type`, `publisher`, `volume`, `issue`, `first_page`, `last_page` and `cite_key`, plus `details_fetched INTEGER NOT NULL DEFAULT 0`.

- `cite_key` has a unique index. SQLite allows many NULLs in a unique index.
- `details_fetched` is 1 once the paper's publication details come from a v4-era OpenAlex response. Every save from v4 on writes 1. Rows from v3 keep 0 until they are refetched. The flag exists so a paper that genuinely has no volume or pages isn't refetched on every export.

### 5.2 Collections tables

```kotlin
@Entity(tableName = "collections", indices = [Index(value = ["name_key"], unique = true)])
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** name trimmed and lowercased (Locale.ROOT); enforces "no two collections with the same name". */
    @ColumnInfo(name = "name_key") val nameKey: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "collection_papers",
    primaryKeys = ["collection_id", "paper_id"],
    foreignKeys = [
        ForeignKey(entity = CollectionEntity::class, parentColumns = ["id"], childColumns = ["collection_id"], onDelete = CASCADE),
        ForeignKey(entity = PaperEntity::class, parentColumns = ["id"], childColumns = ["paper_id"], onDelete = CASCADE)
    ],
    indices = [Index("paper_id")]
)
data class CollectionPaperEntity(
    @ColumnInfo(name = "collection_id") val collectionId: Long,
    @ColumnInfo(name = "paper_id") val paperId: String,
    @ColumnInfo(name = "added_at") val addedAt: Long
)
```

Deleting a collection deletes its links, never its papers. Deleting a paper deletes its links.

### 5.3 DAOs

A new `CollectionDao`:

- `observeCollections(): Flow<List<CollectionWithCount>>`, sorted by `name_key`;
- `insert`, `rename` and `delete`, where `insert` and `rename` report a name clash instead of throwing;
- `observeCollectionIdsForPaper(paperId)`;
- `setMembership(collectionId, paperId, member: Boolean, addedAt)`.

`PaperDao` changes:

- `observeLibrary` and `observeStatusCounts` gain an optional `collectionId`. When it is set, they join `collection_papers`. The FTS match and status filter stay as they are.
- `deleteByOpenAlexId` also captures the paper's collection links. `DeletedPaper` gains `collectionLinks: List<CollectionPaperEntity>`.
- Restore reinserts the links whose collection still exists, in one transaction with the paper.
- New queries:
  - papers with `details_fetched = 0` in a set;
  - `updatePublicationDetails(paperId, …)`, which also sets `details_fetched = 1`;
  - `assignCiteKeys(Map<String, String>)`;
  - `allCiteKeys()`.

### 5.4 Migration 3 → 4

`ALTER TABLE papers ADD COLUMN` for each new column, `CREATE UNIQUE INDEX` on `cite_key`, and `CREATE TABLE` plus indices for the two new tables. The statements are copied from the exported `4.json`. Nothing existing is rewritten, and the FTS table is untouched.

### 5.5 Schema export

`4.json` is committed alongside `1.json`–`3.json`.

## 6. BibTeX (`core/bibtex`, new)

A plain Kotlin/JVM module that depends only on `core/model`. It has no Android dependency and no I/O.

```kotlin
/** A saved paper ready to cite: its metadata and its stored key. */
data class CitablePaper(val paper: Paper, val citeKey: String)

object BibTeX {
    fun entry(paper: CitablePaper): String
    /** Entries sorted by cite key, separated by one blank line, ending with a newline. */
    fun file(papers: List<CitablePaper>): String
}

object CiteKeys {
    /** The base key before collision suffixes, e.g. "vaswani2017attention". */
    fun base(paper: Paper): String
    /** Keys for [papers], in order, avoiding [taken] and each other. */
    fun assign(papers: List<Paper>, taken: Set<String>): List<String>
}
```

### 6.1 Entry type

The first row that matches wins:

| OpenAlex `type` / source `type` | Entry | Venue field |
|---|---|---|
| source type `conference` (any work type) | `@inproceedings` | `booktitle` |
| `book-chapter` | `@incollection` | `booktitle` |
| `book` | `@book` | (none) |
| `dissertation` | `@phdthesis` | `school` |
| `report` | `@techreport` | `institution` |
| `preprint`, or source type `repository` | `@misc` | `howpublished` |
| `article`, `review`, `letter` or `editorial` with source type `journal` | `@article` | `journal` |
| anything else | `@misc` | `howpublished` |

Rule order matters. A conference paper that OpenAlex types as `article` must become `@inproceedings`, and an arXiv `article` whose source is a repository must become `@misc`.

`@phdthesis` needs a `school`. When the venue is missing, the field is left out. BibTeX warns about it but still compiles.

### 6.2 Fields

In this order, each left out when empty:

1. `author`: the authors' display names joined with ` and `, unchanged. BibTeX parses "First von Last" itself. A name containing ` and ` or a comma (an organisation, or "Last, First") is wrapped in braces so BibTeX keeps it as one author.
2. `title`.
3. `year`.
4. The venue field from 6.1, set to `Paper.venue`.
5. `volume`, then `number` (from issue).
6. `pages`: `first--last`, or `first` alone when there is no last page.
7. `publisher`: only for `@book`, `@incollection`, `@techreport` and `@misc`. Journal articles don't normally carry one.
8. `doi`: bare, without `https://doi.org/`.
9. `eprint` and `archivePrefix = {arXiv}`: when the DOI starts with `10.48550/arXiv.` (case-insensitive). `eprint` is the part after it; both are left out when that part is empty.
10. `url`: only when there is no DOI, set to `openAccessPdfUrl`, with `{` and `}` percent-encoded.

Formatting: two-space indent, `field = {value},` on each line with no comma after the last field, and the closing `}` on its own line.

### 6.3 Escaping and title protection

- In every value, `\` becomes `\textbackslash{}`, and `& % $ # _ { } ~ ^` become `\&`, `\%`, `\$`, `\#`, `\_`, `\textbraceleft{}`, `\textbraceright{}`, `\textasciitilde{}` and `\textasciicircum{}`. Braces use commands because BibTeX counts braces without looking at backslashes, so a lone `\{` would unbalance the entry. The DOI and URL are not escaped, because BibTeX styles pass them to `\url`/`\doi`, which handle these characters themselves.
- All other characters, including Arabic and accented letters, stay as UTF-8.
- In `title`, `booktitle` and `journal`, a word with an uppercase letter after its first character (`BERT`, `ImageNet`, `COVID-19`, `iPhone`) is wrapped in braces so styles don't lowercase it. A word that starts with an escaped character (`\#MeToo`) gets double braces, because BibTeX treats a group starting with `{\` as one special character and lowercases the rest.
- Line breaks and runs of whitespace (Unicode whitespace included) collapse to one space.

### 6.4 Cite keys

`base(paper)` is built from three parts, each ASCII-folded and kept as lowercase `[a-z0-9]` only:

1. **Surname:** the last whitespace-separated word of the first author's display name.
2. **Year:** the year, or `nd` when missing.
3. **Title word:** the first title word that isn't a stop word (a, an, the, on, of, in, for, and, to, with, from, by, via, is, are, towards, toward, using, at), and isn't empty after folding.

ASCII folding lowercases, applies a small map for letters that don't decompose (ß and ẞ → ss, æ → ae, ø → o, đ → d, ł → l, ı → i, œ → oe), then uses NFKD decomposition (which also splits ligatures such as ﬁ) and drops combining marks. Arabic and other non-Latin letters fold to nothing.

If the title word folds to nothing it is left out. Leading digits are dropped from the surname. If the surname then is empty (no authors, a non-Latin name, or a name like "Group 7"), `paper` takes its place, e.g. `paper2019`, `paper2019deep` or `papernd`. A key therefore always starts with a letter.

`assign` gives each paper `base`, then `base + "a"`, `"b"` … `"z"`, `"aa"`, `"ab"`… until the key is free of `taken` and of keys already assigned in the same call.

## 7. Repositories (`core/data`)

### 7.1 `LibraryRepository` changes

- `observeLibrary(query, status, collectionId: Long?)` and `observeStatusCounts(query, collectionId: Long?)`, where null means All papers.
- `save(paper)` stores `paper.publication` and sets `details_fetched = 1`.
- `RemovedPaper` gains `collectionIds: Set<Long>`, `citeKey: String?` and `detailsFetched: Boolean`.
- `restore` puts all of them back: memberships only for collections that still exist, and the cite key only if no other paper took it meanwhile (otherwise it goes back to null and is reassigned on the next export).

### 7.2 `CollectionsRepository` (new)

```kotlin
interface CollectionsRepository {
    fun observeCollections(): Flow<List<Collection>>
    fun observeCollectionIds(openAlexId: String): Flow<Set<Long>>
    suspend fun create(name: String): CollectionResult   // Created(id) | NameTaken | InvalidName
    suspend fun rename(id: Long, name: String): CollectionResult   // also NotFound when the collection was deleted
    suspend fun delete(id: Long)
    suspend fun setMembership(collectionId: Long, openAlexId: String, member: Boolean)
}
```

Names are trimmed. After trimming they must be 1–60 characters, or the result is `InvalidName`. Uniqueness uses the `name_key` column, which is trimmed and lowercased with `Locale.ROOT`.

### 7.3 `CitationRepository` (new)

```kotlin
interface CitationRepository {
    /** One paper's entry, refetching its details first if needed. Null if it isn't saved. */
    suspend fun entry(openAlexId: String): CitationResult?
    /** Every paper in [collectionId] (null = whole library), regardless of any search or status filter. */
    suspend fun export(collectionId: Long?): CitationResult
}

/** [complete] is false when at least one paper still has details_fetched = 0 after the refetch attempt. */
data class CitationResult(val bibtex: String, val complete: Boolean)
```

Both methods follow the same three steps:

1. **Refetch.** For papers with `details_fetched = 0`, call `getWork` through the existing lookup, at most 4 at a time. Store the publication details and set the flag. A failure leaves the flag at 0 and doesn't stop the export.
2. **Assign keys.** For papers without a cite key, in `saved_at` order, call `CiteKeys.assign` against `allCiteKeys()`, and store the keys in one transaction.
3. **Build.** Call `BibTeX.entry` or `BibTeX.file`.

Keys are assigned before building, so an entry never goes out without a stored key.

## 8. Library (`feature/library`)

### 8.1 Collection selector

- The top bar's title becomes a button with the current name ("All papers" or the collection's name) and a ▾ icon. It is shown when the library isn't Empty.
- Tapping it opens a modal bottom sheet with these rows:
  1. **All papers**, with the total count.
  2. Each collection, with its count (the existing `library_paper_count` plural) and a ⋮ menu with **Rename** and **Delete**.
  3. **New collection**.
- The selected row has a check mark.
- **New** and **Rename** open a dialog with one text field. The confirm button is enabled only when the trimmed name is 1–60 characters. `NameTaken` shows "A collection with that name already exists" under the field.
- **Delete** asks for confirmation: "Delete "%1$s"? Its papers stay in your library."
- The selected collection id is kept in `SavedStateHandle`. If it disappears from `observeCollections()`, the selection falls back to All papers.

### 8.2 Filtering and empty states

- The query and status chips apply within the selected collection, and the counts follow.
- A selected collection with no papers at all shows "No papers in this collection yet. Add papers from their details screen." The search field and chips are hidden.
- A collection with papers that don't match the current search and chip shows the existing `NoMatches` state.

### 8.3 Swipe

- In **All papers**, swiping removes the paper from the library, as today.
- In a collection, swiping removes the paper **from that collection only**. The snackbar says "Removed from %1$s", and Undo re-adds the paper to the collection.

### 8.4 Export

- **Export .bib** (an icon button, content description "Export .bib") sits before Settings in the top bar. It is shown when the current view has at least one paper, ignoring the search and chip.
- While the export runs, the button is replaced by a small progress indicator, and a second tap does nothing.
- On success the app writes `cacheDir/exports/<file>.bib`, deleting earlier files in that folder first, and fires `ACTION_SEND` with `EXTRA_STREAM`, MIME type `text/x-bibtex` and read permission, through a chooser.
  - The file name is `hashiya-library.bib` for All papers.
  - For a collection it is the collection's name with `/ \ : * ? " < > |` and control characters replaced by `-`, trimmed, and `collection` when that leaves nothing.
- A `FileProvider` (authority `${applicationId}.exports`) is declared in `feature/library`'s manifest with a `cache-path` for `exports/`.
- If `complete` is false, the snackbar "Some entries may be incomplete. Export again when you're online." is queued in the UI state and shown on the Library's next `ON_RESUME`, which is when the user comes back from the share sheet.

## 9. Details (`feature/paperdetails`)

- **Collections row:** it sits below the reading status. It shows the paper's collections as non-interactive chips, or "Not in any collection". The whole row is one button that opens the checklist sheet.
- **Checklist sheet:**
  - One row per collection, with a checkbox. Toggling it calls `setMembership` immediately.
  - Then **New collection**, which opens the same name dialog. On `Created(id)`, the paper is added to the new collection.
  - When there are no collections yet, the sheet shows only **New collection** and a line explaining it: "Group papers for a chapter, a course or a project."
- **Copy BibTeX:** a new overflow item above **Remove from library**.
  - It calls `CitationRepository.entry` and puts the result on the clipboard as plain text, with the label "BibTeX".
  - Below API 33 it shows the snackbar "BibTeX copied", because API 33 and above show the system's own clipboard confirmation.
  - If `complete` is false, it shows "Some entries may be incomplete…" instead, on every API level.

The name dialog and its validation are shared by Library and Details, so they live in `core/designsystem` as `CollectionNameDialog`, with its strings.

## 10. Strings (both locales)

| Key | Module | English | Arabic |
|---|---|---|---|
| `collection_name_label` | designsystem | Collection name | اسم المجموعة |
| `collection_new_title` | designsystem | New collection | مجموعة جديدة |
| `collection_rename_title` | designsystem | Rename collection | إعادة تسمية المجموعة |
| `collection_create` | designsystem | Create | إنشاء |
| `collection_save` | designsystem | Save | حفظ |
| `collection_cancel` | designsystem | Cancel | إلغاء |
| `collection_name_taken` | designsystem | A collection with that name already exists | توجد مجموعة بهذا الاسم بالفعل |
| `library_all_papers` | library | All papers | كل الأوراق |
| `library_choose_collection` | library | Choose a collection | اختيار مجموعة |
| `library_new_collection` | library | New collection | مجموعة جديدة |
| `library_collection_options` | library | Options for %1$s | خيارات %1$s |
| `library_rename` | library | Rename | إعادة التسمية |
| `library_delete` | library | Delete | حذف |
| `library_delete_collection_title` | library | Delete "%1$s"? | حذف «%1$s»؟ |
| `library_delete_collection_message` | library | Its papers stay in your library. | ستبقى أوراقها في مكتبتك. |
| `library_collection_empty` | library | No papers in this collection yet. Add papers from their details screen. | لا توجد أوراق في هذه المجموعة بعد. أضف الأوراق من شاشة تفاصيلها. |
| `library_removed_from_collection` | library | Removed from %1$s | أُزيلت من %1$s |
| `library_export_bib` | library | Export .bib | تصدير ملف ‎.bib |
| `library_export_failed` | library | Couldn\'t export | تعذّر التصدير |
| `library_export_incomplete` | library | Some entries may be incomplete. Export again when you\'re online. | قد تكون بعض المداخل ناقصة. أعد التصدير عند الاتصال بالإنترنت. |
| `library_collections_update_failed` | library | Couldn\'t update collections | تعذّر تحديث المجموعات |
| `details_collections` | paperdetails | Collections | المجموعات |
| `details_no_collections` | paperdetails | Not in any collection | ليست في أي مجموعة |
| `details_collections_hint` | paperdetails | Group papers for a chapter, a course or a project. | اجمع الأوراق لفصل أو مقرر أو مشروع. |
| `details_new_collection` | paperdetails | New collection | مجموعة جديدة |
| `details_copy_bibtex` | paperdetails | Copy BibTeX | نسخ BibTeX |
| `details_bibtex_copied` | paperdetails | BibTeX copied | تم نسخ BibTeX |
| `details_bibtex_incomplete` | paperdetails | Some details may be missing. Copy again when you\'re online. | قد تنقص بعض البيانات. انسخ مرة أخرى عند الاتصال بالإنترنت. |
| `details_collections_update_failed` | paperdetails | Couldn\'t update collections | تعذّر تحديث المجموعات |

"BibTeX" and ".bib" stay in Latin script in Arabic, wrapped with a left-to-right mark where needed so the dot stays attached.

## 11. Errors

| Case | Behaviour |
|---|---|
| Creating, renaming, deleting or changing membership fails | The change doesn't happen, and the snackbar "Couldn't update collections" appears. The checklist shows the stored state again, so a failed tick un-ticks. |
| Name clash on create or rename | Shown under the dialog's field. The dialog stays open. |
| Refetch fails (offline, HTTP error, timeout) | Not an error. The export or copy continues with what is stored, and the "may be incomplete" snackbar appears. |
| Writing the file fails | The snackbar "Couldn't export" appears. The text is built in memory first, so no partial file is shared. |
| No app can receive the share | The chooser shows its own empty state. Nothing else is needed. |
| The selected collection is deleted elsewhere (from Details) | The Library falls back to All papers. |
| A paper is removed while its export is running | The export uses the list read at the start. A missing row during key assignment is skipped. |
| Unique-key clash when storing keys (a race with another export) | The transaction retries once with a fresh `allCiteKeys()`. |

## 12. Testing

All tests are written test-first and run on the JVM (Robolectric where Android is needed), with hand-written fakes.

- **`core/bibtex`:**
  - one fixture per row of the entry-type table, checking the entry type and the venue field;
  - rule order: a conference `article` becomes `@inproceedings`, and a repository `article` becomes `@misc`;
  - field order, omitted empty fields, pages with and without a last page, and publisher only on the listed types;
  - arXiv DOI → `eprint` and `archivePrefix`; `url` only without a DOI;
  - escaping of every special character, DOIs and URLs left unescaped, and whitespace collapsing;
  - title protection: `BERT`, `ImageNet`, `COVID-19`, `iPhone` get braces, and `The` and `deep` don't;
  - cite keys: accents (`Müller` → `muller`), `ß`, an Arabic-only name (`paper2019…`), no authors and no year (`papernd…`), a missing year (`nd`), stop words skipped, an empty title, collisions producing `a`, `b` … `z`, `aa`, and collisions with `taken`;
  - `file` sorts entries and separates them with one blank line.
  - The fixture list is kept in one file so the iOS spec can copy it.
- **`core/network`:** `type`, `biblio`, source `type` and `host_organization_name` decode from a recorded OpenAlex response, and missing fields decode as null.
- **`core/database`:**
  - a migration test from 3 to 4: papers, authors, statuses, notes and FTS results are unchanged, and the new columns are null with `details_fetched = 0`;
  - `CollectionDao`: create, a name clash, rename, delete cascades links but not papers, counts, and sorting;
  - `PaperDao`: library and counts filtered by collection with a query and status, delete capturing links, restore skipping deleted collections, and the unique cite-key index.
- **`core/data`:**
  - `save` stores the publication details with `details_fetched = 1`;
  - `CitationRepository` refetches only papers with the flag at 0, at most 4 at a time; a failed refetch gives `complete = false` and keeps the flag at 0;
  - keys are assigned once and are stable across a second export and across Remove → Undo;
  - `export` covers the whole collection regardless of filters;
  - `CollectionsRepository` validates names.
- **`feature/library` ViewModel:**
  - selecting a collection filters the list and counts;
  - a deleted collection falls back to All papers;
  - swiping in a collection removes the membership only, and Undo restores it;
  - the export states (running, success, incomplete, failed), and a second tap while running does nothing;
  - file-name sanitising.
- **`feature/paperdetails` ViewModel:**
  - the collection chips follow the stored membership;
  - toggling and a failed toggle;
  - New collection adds the paper to it;
  - Copy BibTeX: copied, incomplete, and the API-level rule for the snackbar.
- **Roborazzi** (English and Arabic, light theme as before):
  - the selector sheet with collections;
  - the Library filtered by a collection;
  - an empty collection;
  - the name dialog with the clash error;
  - the Details Collections row, with and without collections;
  - the checklist sheet, with and without collections.
- **Navigation test:** create a collection from Details, go back, pick it in the Library selector, and see the paper.

## 13. Acceptance criteria (on device)

1. Install the sub-project 4 build, save papers with notes and statuses, then install this branch over it without uninstalling. Everything is still there and Library search still finds notes.
2. Create two collections from Details, add a paper to both, rename one and delete the other from the Library selector. The paper stays in the library.
3. In a collection, search and the status chips narrow the list, and swiping removes the paper from the collection only, with Undo.
4. Export a collection, upload the `.bib` to Overleaf, and cite every key. The document compiles with `plain` and with `IEEEtran`, and journal, conference and arXiv papers print correctly.
5. Save another paper, add it to the same collection, and export again. The earlier keys haven't changed.
6. Copy BibTeX from Details and paste it into a note app. It is one well-formed entry.
7. With a library saved under the sub-project 4 build, turn on airplane mode and export. The file is shared, and the "may be incomplete" snackbar appears. Export again online, and the entries gain volume and pages.
8. Switch the app to Arabic. The selector, the sheets and the dialog are right-to-left and in Arabic, and ".bib" and "BibTeX" read correctly.

## 14. Risks

| Risk | Mitigation |
|---|---|
| OpenAlex's type or source type is wrong for some papers, giving the wrong entry type | Rules fall back to `@misc`, which always compiles. The fixture table documents the mapping, so fixes are one-line changes. |
| Refetching a large old library is slow | Only papers with `details_fetched = 0` are refetched, once each and 4 at a time, and progress is shown. |
| Share targets ignore `text/x-bibtex` | Drive, Gmail and Files accept any type. If device check 4 finds a needed target missing, the MIME type changes to `text/plain`. The file name keeps `.bib` either way. |
| Cite keys change and break theses | Keys are stored once, have a unique index, and survive Remove → Undo. Tests cover stability across exports. |
| UTF-8 entries fail under pdfLaTeX | Accented Latin works with modern pdfLaTeX. Arabic needs XeLaTeX or LuaLaTeX, which is the user's LaTeX setup. The acceptance check uses Latin-script papers. |
| The migration breaks existing libraries | It only adds columns and tables, and the migration test compares data before and after. |
