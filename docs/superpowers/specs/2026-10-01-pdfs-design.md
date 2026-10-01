# Sub-project 6: PDFs — Design

- **Date:** 2026-10-01
- **Status:** Awaiting review
- **Scope:** Sixth MVP sub-project for Hashiya, Android only, building on sub-projects 1–5 (sub-project 5 merged in `7ad31b4`). The iOS version gets its own spec once this one is merged.

## 1. Context

Hashiya's core loop is **Discover → Save → Read → Extract → Compare → Cite**. Every step except **Read** happens in the app; today "Open PDF" on Details sends the user to the browser. This sub-project lets each saved paper hold one PDF, downloaded from its open-access link or attached from a file, and read in the app next to its notes, offline.

The same standards as before apply: modular architecture and dependency rules, English and Arabic with full RTL, TDD with hand-written fakes, Roborazzi screenshots recorded on CI Linux, and a green CI.

### Decisions made during brainstorming

| Topic | Decision |
|---|---|
| Reading | An in-app reader. Android uses the platform `PdfRenderer` (no new dependency, minSdk 24); iOS will use PDFKit. |
| Searching PDF text | Not in this sub-project. The Library search index keeps room for a PDF-text column later. |
| Downloading | On demand from Details. Nothing downloads automatically. |
| Attaching | From Details only, through the system file picker. Sharing a PDF into the app is not included. |
| How many | One PDF per paper. |
| Where | App-private storage, never synced or exposed to other apps except through Share. |

## 2. Goals and non-goals

### Goals

1. A saved paper with an open-access link can download its PDF once and read it offline.
2. Any saved paper can attach a PDF from the device or a cloud provider, and replace or remove it.
3. The reader shows the PDF with smooth scrolling and zoom, resumes on the last page, and opens the paper's notes over it.
4. A link that leads to a web page instead of a PDF is recognised and explained, with a way out (browser, attach).
5. Removing a paper and tapping **Undo** keeps its PDF; a final removal deletes the file.
6. Settings shows the space used by downloaded PDFs and can delete them.
7. Existing libraries upgrade in place: no paper, status, note, collection, cite key or search result is lost.

### Non-goals

- Searching inside PDFs, text selection, highlights, annotations or drawing.
- Password-protected PDFs.
- More than one PDF per paper, or other file types.
- Sharing a PDF into the app, or downloading automatically on save.
- Background downloads that survive the app being killed (WorkManager).
- Syncing or backing up PDFs.
- Opening the stored file in another app's viewer (Share is available).

## 3. Model (`core/model`)

```kotlin
enum class PdfSource { Downloaded, Attached }

/** The PDF stored for a saved paper. */
data class PaperPdf(
    val source: PdfSource,
    val sizeBytes: Long,
    val addedAt: Long,
    /** Zero-based page the reader last showed; 0 for a new file. */
    val lastPage: Int = 0
)

data class LibraryPaper(/* …existing fields… */ val hasPdf: Boolean = false)

data class PdfStorage(val downloadedBytes: Long, val downloadedCount: Int, val attachedBytes: Long, val attachedCount: Int)
```

`Paper` itself does not change; the PDF is local state, not catalogue metadata.

## 4. Database (`core/database`), schema version 5

### 4.1 Columns

New nullable columns on `papers`: `pdf_source TEXT` (`"downloaded"` or `"attached"`), `pdf_size INTEGER`, `pdf_added_at INTEGER`, `pdf_last_page INTEGER`. A row has a PDF exactly when `pdf_source` is not null; the four columns are written and cleared together.

### 4.2 Migration 4 → 5

Four `ALTER TABLE papers ADD COLUMN` statements, copied from the exported `5.json`. Nothing existing is rewritten; the FTS table and the collection tables are untouched. `5.json` is committed next to `1.json`–`4.json`.

### 4.3 DAO

`PaperDao` gains:

- `observePdf(openAlexId): Flow<PdfColumns?>`;
- `setPdf(paperId, source, size, addedAt)` (sets `pdf_last_page = 0`), `clearPdf(paperId)`, `setPdfLastPage(paperId, page)`;
- `pdfPaperIds(): List<String>` (every paper id with a PDF, for the orphan sweep) and `downloadedPdfPaperIds()`;
- `pdfStorage()`: sums and counts by source.

`observeLibrary` adds `pdf_source IS NOT NULL AS hasPdf` to each row. `deleteByOpenAlexId` returns the PDF columns in `DeletedPaper`, and restore writes them back in the same transaction as the paper.

## 5. Files (`core/data`)

### 5.1 `PdfFileStore`

Owns one folder, `filesDir/pdfs/` (not the cache, so the system never clears it), passed in so tests use a temporary folder.

```kotlin
class PdfFileStore(private val dir: File) {
    fun file(paperId: String): File                       // dir/<paperId>.pdf
    /** Copies [input] to a temporary file in [dir], checks it, then renames it over the final file. */
    fun store(paperId: String, input: InputStream, maxBytes: Long, onProgress: (Long) -> Unit): StoreResult
    fun delete(paperId: String)
    /** Deletes every file whose paper id is not in [keep], and every leftover temporary file. */
    fun sweep(keep: Set<String>)
}

sealed interface StoreResult { data class Stored(val size: Long) : StoreResult; data object NotPdf : StoreResult; data object TooLarge : StoreResult }
```

- A file is a PDF when `%PDF-` appears in its first 1024 bytes. Most files start with it; some carry a short preamble, which PDF readers accept too.
- The limit is **100 MB**. Copying stops as soon as it is passed, and the temporary file is deleted.
- The rename is atomic within the folder, so a crash never leaves a half-written `<id>.pdf`.

### 5.2 `PdfRepository`

```kotlin
interface PdfRepository {
    fun observePdf(openAlexId: String): Flow<PaperPdf?>
    /** Running downloads, by OpenAlex id, so Details shows progress after it is reopened. */
    fun observeDownload(openAlexId: String): Flow<DownloadState?>
    fun download(openAlexId: String)                    // starts it in the application scope; no-op if one is running
    fun cancelDownload(openAlexId: String)
    suspend fun attach(openAlexId: String, uri: Uri): AttachResult
    suspend fun remove(openAlexId: String)
    suspend fun setLastPage(openAlexId: String, page: Int)
    fun pdfFile(openAlexId: String): File               // for the reader and Share
    suspend fun storage(): PdfStorage
    suspend fun deleteDownloaded()
    /** Deletes a removed paper's file once its removal is final, unless the paper is saved again. */
    suspend fun discardRemoved(openAlexId: String)
    suspend fun sweepOrphans()                         // called once at startup
}

sealed interface DownloadState {
    data class Running(val bytes: Long, val totalBytes: Long?) : DownloadState
    data class Failed(val reason: DownloadFailure) : DownloadState
}
enum class DownloadFailure { Offline, NotPdf, TooLarge, Http, NoLink }
enum class AttachResult { Done, NotPdf, TooLarge, Unreadable }
```

- **Download:** one GET to `openAccessPdfUrl` through the shared OkHttp client with a separate call timeout of 2 minutes, following redirects. The body goes through `PdfFileStore.store`. On `Stored`, the row is set to `Downloaded` with its size and time. A `Failed` state is kept in memory until the next attempt or the next app start.
- **Attach:** reads the URI through `ContentResolver.openInputStream` and stores it the same way, as `Attached`. Replacing a PDF is an attach over an existing one.
- **Remove:** clears the columns, then deletes the file.
- Cancelling deletes the temporary file and clears the state.
- **Undo:** `LibraryRepository.remove` leaves the file in place. The file of a removed paper is deleted when the removal becomes final: the Library's Undo banner times out or is dismissed, and the Library calls `PdfRepository.discardRemoved(openAlexId)`, which deletes the file only if no row has that paper again. The startup sweep catches the app being killed while Undo was on screen.
- **Delete downloaded:** clears the columns of every `Downloaded` row, then deletes those files. Attached files stay.

## 6. Details (`feature/paperdetails`)

A **PDF** row sits below the Collections row and replaces the old "Open PDF" link button ("Open DOI" stays).

| State | Row | Actions |
|---|---|---|
| No PDF, link exists | "PDF available to download" | **Download PDF** |
| No PDF, no link | "No PDF" | **Attach PDF** |
| Downloading | progress bar, "%1$s of %2$s" or "%1$s" when the size is unknown | **Cancel** |
| Stored | "PDF · %1$s · Downloaded" / "· Attached" | the row opens the reader; overflow: **Replace PDF**, **Remove PDF**, and **Open link in browser** when a link exists |
| Failed | "Couldn't get the PDF" and the reason (§11) | **Try again**, **Open in browser** (when a link exists), **Attach PDF** |

When a link exists, the overflow of the no-PDF and failed states also has **Attach PDF**. **Replace PDF** and **Remove PDF** ask for confirmation. Sizes use `Formatter.formatShortFileSize`.

## 7. Reader (`feature/reader`, new)

A new feature module with `ReaderScreen` and `ReaderViewModel`, reached from Details (`ReaderRoute(openAlexId)`). The bottom navigation is hidden, as on Details.

- **Pages:** a `LazyColumn` of pages. Each page is rendered by `PdfRenderer` to a bitmap at the screen width times the current zoom, only while it is near the viewport (the visible page and two on each side), and recycled when it leaves. Rendering runs on one background thread, because `PdfRenderer` allows one open page at a time. Page aspect ratios are read once on open, so the list never jumps.
- **Zoom:** pinch from 1× to 4×, and double-tap toggles 1× and 2.5×. At the new zoom, pages re-render once the gesture ends; during it they scale the existing bitmap.
- **Page pill:** "%1$d of %2$d", shown while scrolling and fading out 1.5 s after it stops.
- **Last page:** saved (debounced, 1 s) through `setLastPage`, and restored on open.
- **Toolbar:** back, the paper's title (one line), **Notes**, **Share**.
  - **Notes** opens a modal bottom sheet, half height, expandable, with the same six note fields and the same autosave as Details. The sheet reads the notes once, like Details.
  - **Share** sends the file through `ACTION_SEND`, MIME `application/pdf`, from a `FileProvider` (authority `${applicationId}.pdfs`, path `pdfs/`).
- **Errors:** a file `PdfRenderer` cannot open (damaged or password-protected) shows "This PDF can't be opened" with **Replace PDF** and **Remove PDF**.
- **RTL:** the toolbar and the pill mirror in Arabic; the pages never do.
- Rendering sits behind `interface PdfPageSource { val pageCount: Int; fun pageSize(index: Int): Size; suspend fun render(index: Int, width: Int): Bitmap; fun close() }`, so the view model is tested with fake pages and Roborazzi draws fake pages.

## 8. Library and Settings

- **Library rows** show a small PDF icon (content description "PDF available offline") when `hasPdf` is true, after the status badge.
- **Settings → Storage:** "Downloaded PDFs · %1$s · %2$d files" and, when any exist, "Attached PDFs · %1$s · %2$d files". **Delete downloaded PDFs** asks "Delete %1$d downloaded PDFs? You can download them again. Attached PDFs are kept." Shown only when there are downloaded PDFs.

## 9. Startup

`HashiyaApplication` calls `PdfRepository.sweepOrphans()` once, in the application scope, after the database opens. It deletes files without a row and leftover temporary files.

## 10. Strings (both locales)

| Key | Module | English | Arabic |
|---|---|---|---|
| `details_pdf` | paperdetails | PDF | ملف PDF |
| `details_pdf_available` | paperdetails | PDF available to download | ملف PDF متاح للتنزيل |
| `details_pdf_none` | paperdetails | No PDF | لا يوجد ملف PDF |
| `details_pdf_download` | paperdetails | Download PDF | تنزيل ملف PDF |
| `details_pdf_attach` | paperdetails | Attach PDF | إرفاق ملف PDF |
| `details_pdf_replace` | paperdetails | Replace PDF | استبدال ملف PDF |
| `details_pdf_remove` | paperdetails | Remove PDF | إزالة ملف PDF |
| `details_pdf_cancel` | paperdetails | Cancel | إلغاء |
| `details_pdf_progress` | paperdetails | %1$s of %2$s | %1$s من %2$s |
| `details_pdf_downloaded` | paperdetails | PDF · %1$s · Downloaded | ملف PDF · %1$s · مُنزَّل |
| `details_pdf_attached` | paperdetails | PDF · %1$s · Attached | ملف PDF · %1$s · مُرفَق |
| `details_pdf_failed` | paperdetails | Couldn't get the PDF | تعذّر الحصول على ملف PDF |
| `details_pdf_offline` | paperdetails | You're offline. | أنت غير متصل بالإنترنت. |
| `details_pdf_not_pdf` | paperdetails | This link opens a web page, not a PDF. | يفتح هذا الرابط صفحة ويب وليس ملف PDF. |
| `details_pdf_too_large` | paperdetails | The PDF is larger than 100 MB. | ملف PDF أكبر من 100 ميغابايت. |
| `details_pdf_http` | paperdetails | The server didn't send the PDF. | لم يُرسل الخادم ملف PDF. |
| `details_pdf_try_again` | paperdetails | Try again | إعادة المحاولة |
| `details_pdf_open_browser` | paperdetails | Open in browser | فتح في المتصفح |
| `details_pdf_open_link` | paperdetails | Open link in browser | فتح الرابط في المتصفح |
| `details_pdf_replace_title` | paperdetails | Replace this PDF? | استبدال ملف PDF هذا؟ |
| `details_pdf_remove_title` | paperdetails | Remove this PDF? | إزالة ملف PDF هذا؟ |
| `details_pdf_attach_not_pdf` | paperdetails | That file isn't a PDF. | هذا الملف ليس ملف PDF. |
| `details_pdf_attach_failed` | paperdetails | Couldn't read that file. | تعذّرت قراءة هذا الملف. |
| `reader_page` | reader | %1$d of %2$d | %1$d من %2$d |
| `reader_notes` | reader | Notes | الملاحظات |
| `reader_share` | reader | Share | مشاركة |
| `reader_cant_open` | reader | This PDF can't be opened. | تعذّر فتح ملف PDF هذا. |
| `library_has_pdf` | library | PDF available offline | ملف PDF متاح دون اتصال |
| `settings_storage` | settings | Storage | التخزين |
| `settings_downloaded_pdfs` | settings | Downloaded PDFs · %1$s · %2$d files | ملفات PDF المُنزَّلة · %1$s · %2$d ملفات |
| `settings_attached_pdfs` | settings | Attached PDFs · %1$s · %2$d files | ملفات PDF المُرفَقة · %1$s · %2$d ملفات |
| `settings_delete_downloaded` | settings | Delete downloaded PDFs | حذف ملفات PDF المُنزَّلة |
| `settings_delete_downloaded_message` | settings | Delete %1$d downloaded PDFs? You can download them again. Attached PDFs are kept. | حذف %1$d من ملفات PDF المُنزَّلة؟ يمكنك تنزيلها مرة أخرى. ستبقى الملفات المُرفَقة. |

The file counts become plurals (`<plurals>`) in implementation, with Arabic's six forms. "PDF" stays in Latin script, wrapped with a left-to-right mark where needed.

## 11. Errors

| Case | Behaviour |
|---|---|
| Offline | Failed state, "You're offline." |
| HTTP error (4xx/5xx) or timeout | Failed state, "The server didn't send the PDF." |
| The body isn't a PDF (a landing page, a login wall) | Failed state, "This link opens a web page, not a PDF.", with **Open in browser** and **Attach PDF**. |
| Over 100 MB | Failed state, "The PDF is larger than 100 MB." |
| The link disappeared (refetch cleared it) | `NoLink`: the row falls back to "No PDF". |
| Attach: not a PDF / too large / unreadable URI | Snackbar with the matching message; nothing changes. |
| The disk is full | Treated as unreadable/HTTP failure; the temporary file is deleted. |
| The reader can't open the file | "This PDF can't be opened." with Replace and Remove. |
| The paper is removed while downloading | The download is cancelled and its temporary file deleted. |
| App killed during a download | The temporary file is swept on the next start; the row shows the no-PDF state. |

## 12. Testing

All tests are written test-first and run on the JVM (Robolectric where Android is needed), with hand-written fakes.

- **`core/database`:** migration 4 → 5 (everything kept, new columns null); `setPdf`/`clearPdf`/`setPdfLastPage`; `hasPdf` in the library query; delete returns the PDF columns and restore writes them back; `pdfStorage` sums by source.
- **`PdfFileStore`** (temporary folder): stores and renames atomically; rejects a file without `%PDF-` in its first 1024 bytes and accepts one with a short preamble; stops at the size limit and leaves no file; delete; sweep keeps listed ids and removes the rest and temporary files.
- **`PdfRepository`** (MockWebServer, fakes for the DAO): progress and the final size; redirects followed; an HTML body becomes `NotPdf`; offline and HTTP errors; one download per paper; cancel removes the temporary file; attach from a content URI (Robolectric), not a PDF, too large; remove; Undo keeps the file and `discardRemoved` deletes it only when the paper is gone; `deleteDownloaded` keeps attached files; `sweepOrphans`.
- **`feature/paperdetails` ViewModel:** every row state in §6 and each action.
- **`feature/reader` ViewModel** (fake `PdfPageSource`): opens on the stored last page; saves the page debounced; the can't-open state; Notes reads the notes once and autosaves.
- **Roborazzi** (English and Arabic, light): the Details PDF row in each state; the reader with fake pages and the page pill; the Notes sheet over the reader; the Settings storage section; a Library row with the PDF icon.
- **Navigation test:** Details → Read → Notes → back.

## 13. Acceptance criteria (on device)

1. Install the sub-project 5 build with a library, then this build over it. Everything is still there.
2. Download an arXiv paper's PDF, turn on airplane mode, and read it; zoom and scroll a 200-page PDF smoothly.
3. Close the reader on page 12, reopen it: it opens on page 12.
4. Open Notes from the reader, write, close, and see the note on Details.
5. A paywalled paper's link shows "This link opens a web page, not a PDF.", and Attach PDF from Drive works.
6. Remove a paper with a PDF, Undo: the PDF is still readable. Remove it again and let the banner go: the file is gone (Settings storage drops).
7. Settings → Delete downloaded PDFs keeps an attached PDF.
8. Share the PDF to another app.
9. In Arabic, the row, reader controls and Settings are right-to-left, and the pages are not mirrored.

## 14. Risks

| Risk | Mitigation |
|---|---|
| OpenAlex `pdf_url` often points to a landing page or is blocked | The `%PDF-` check, a clear message, and Attach as the way out. |
| `PdfRenderer` memory on large or tall pages | Render only near the viewport, at screen width × zoom, one page at a time, and recycle bitmaps. |
| `PdfRenderer` can't open some valid PDFs | The can't-open state with Replace; acceptance check 2 covers common sources. |
| A crash leaves partial or orphaned files | Temporary file plus rename; the startup sweep. |
| Storage growth | Settings storage with Delete downloaded; the 100 MB limit per file. |
| Removing a paper deletes a PDF the user wanted | The file survives the Undo window; deletion happens only when the removal is final. |
