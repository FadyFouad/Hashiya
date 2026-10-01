# iOS sub-project 6: PDFs — Design

- **Date:** 2026-10-01
- **Status:** Awaiting review
- **Scope:** iOS counterpart of Android sub-project 6 (`2026-10-01-pdfs-design.md`), matching the Android code merged on `main` in `9684be6` (PR #19), plus the https download fix in PR #21. Builds on the iOS specs 1–5; their platform decisions, package rules, string conventions, testing and CI apply unchanged.
- **Depends on:** iOS sub-project 5 (PR #20). Migration `"v5"` here follows its `"v4"`, so implementation starts after #20 merges, on this branch rebased onto `main`.

## 1. Context

Every saved paper can hold one PDF on Android, downloaded on demand from its open-access link or attached from a file, and read in an in-app reader next to its notes. This spec brings the same to iOS. Where Android's merged code differs from the Android spec, iOS follows the code; §13 lists those points.

### Decisions

| Topic | Decision |
|---|---|
| Behaviour | As Android spec §2 (goals and non-goals), §5–§9 and §11, as merged, unless §13 here says otherwise. |
| Reader | PDFKit `PDFView` in a new package target `FeatureReader`. Adds search inside the document (iOS only). |
| Downloading | On demand, through a dedicated ephemeral `URLSession`. `http://` links are upgraded to `https://` first. |
| Backgrounding | A running download asks for background time (`beginBackgroundTask`) and finishes if it can; when the time runs out it is cancelled cleanly and the row offers **Try again**. No background `URLSession`. |
| Attaching | From Details, through `.fileImporter` limited to PDFs; the file is copied in. |
| Storage | `pdfs/<papers.id>.pdf` in the App Group container next to the database. The Share Extension never reads or writes PDFs. |
| Look | English and Arabic with right-to-left layouts (pages never mirrored), light and dark, Liquid Glass on iOS 26 through the existing helpers. |

## 2. Goals and non-goals

### Goals

Android's seven goals, on iOS:

1. A saved paper with an open-access link downloads its PDF once and reads it offline.
2. Any saved paper can attach a PDF from Files (including iCloud Drive and other providers), and replace or remove it.
3. The reader scrolls and zooms smoothly, resumes on the last page, searches inside the document, and opens the paper's notes over the PDF.
4. A link that leads to a web page instead of a PDF is recognised and explained, with Open in browser and Attach.
5. Remove → Undo keeps the PDF; a final removal deletes the file.
6. Settings shows the space used by PDFs and can delete the downloaded ones.
7. Existing libraries upgrade in place.

### Non-goals

Android's non-goals (highlights, annotations, password-protected PDFs, more than one PDF, sharing a PDF into the app, automatic downloads, sync), plus:

- Background `URLSession` downloads that survive the app being suspended for long or killed.
- Opening the PDF in the Files app or another viewer, except through Share.
- Searching PDF text from the Library (deferred on both platforms).

## 3. Model (`HashiyaModel`)

```swift
public enum PdfSource: String, Sendable { case downloaded, attached }

public struct PaperPdf: Equatable, Hashable, Sendable {
    public var source: PdfSource
    public var sizeBytes: Int64
    public var addedAt: Int64
    public var lastPage: Int          // zero-based; 0 for a new file
}

public struct PdfStorage: Equatable, Sendable {
    public var downloadedBytes: Int64, downloadedCount: Int, attachedBytes: Int64, attachedCount: Int
}
```

`LibraryPaper` gains `hasPdf: Bool` (default `false`). `Paper` does not change.

## 4. Database (`HashiyaDatabase`), migration `"v5"`

- Four `ALTER TABLE papers ADD COLUMN` statements, the same as Android's `MIGRATION_4_5`: `pdf_source TEXT`, `pdf_size INTEGER`, `pdf_added_at INTEGER`, `pdf_last_page INTEGER`, all nullable. A row has a PDF exactly when `pdf_source` is not null.
- `PaperRecord` gains the four columns. `DeletedPaper` carries them through `saved`.
- `PaperStore` gains, mirroring Android's `PaperDao`: `observePdf(openAlexID:)` (last page `COALESCE`d to 0), `setPdf(paperID:source:size:addedAt:)` (resets the last page), `clearPdf(paperID:)`, `setPdfLastPage(paperID:page:)` (only while a PDF is set), `pdfPaperIDs()`, `downloadedPdfPaperIDs()`, `pdfStorage()` and `paperID(openAlexID:)`.
- The library snapshot sets `hasPdf` from `pdf_source`.

## 5. Files (`HashiyaData`)

`PdfFileStore`, owning one directory (the App Group container's `pdfs/` in the app, a temporary directory in tests):

- `file(paperID:) -> URL` (`<paperID>.pdf`).
- `store(paperID:from:maxBytes:onProgress:) throws -> StoreResult` (`.stored(size)`, `.notPDF`, `.tooLarge`): copies into a uniquely named `.part` file in the same directory, checks for `%PDF-` in the first 1024 bytes (stopping early once 1024 bytes have arrived without it), stops at `maxBytes`, calls `synchronize()`, then moves it over the final file. A write failure throws `PdfWriteError`.
- `delete(paperID:)`, and `sweep(keeping:)`, which removes every `.part` file and every `.pdf` whose paper ID isn't kept, and ignores other files.
- `maxPdfBytes = 100 * 1024 * 1024`.

## 6. Repository (`HashiyaData`)

```swift
public enum DownloadFailure: Sendable { case offline, notPDF, tooLarge, http, noLink }
public enum DownloadState: Equatable, Sendable { case running(bytes: Int64, total: Int64?), failed(DownloadFailure) }
public enum AttachResult: Sendable { case done, notPDF, tooLarge, unreadable }

public protocol PdfRepository: Sendable {
    func observePdf(openAlexID: String) -> AsyncStream<PaperPdf?>
    func observeDownload(openAlexID: String) -> AsyncStream<DownloadState?>
    func download(openAlexID: String)
    func cancelDownload(openAlexID: String)
    func attach(openAlexID: String, from url: URL) async -> AttachResult
    func remove(openAlexID: String) async throws
    func setLastPage(openAlexID: String, page: Int) async throws
    func pdfFile(openAlexID: String) async -> URL?
    func storage() async throws -> PdfStorage
    func deleteDownloaded() async throws
    func discardRemoved(_ removed: RemovedPaper) async
    func sweepOrphans() async
}
```

- **Download:** one task per paper in a long-lived task owned by the repository (the iOS counterpart of Android's application scope). It reads the paper's `oaPDFURL`; blank gives `.noLink`. The link goes through `upgradeToHTTPS(_:)` (`http://`, any case, becomes `https://`; other schemes unchanged), then a GET through `PdfDownloadClient`, a dedicated ephemeral `URLSession` (connect 15 s, resource 2 min, `Accept: application/pdf, */*`, redirects followed), never the OpenAlex session. The body streams into `PdfFileStore.store`.
- **Failure mapping**, as Android: no connection → `.offline`; HTTP error, timeout, a malformed link or a disk failure → `.http`; `.notPDF` and `.tooLarge` from the store.
- **Background time:** each running download holds a `UIApplication` background task (through an injected `BackgroundTimeGranting`, so tests need no UIKit). On expiry the download is cancelled; its `.part` file is deleted and the state clears, so the row offers Download or Try again.
- **Remove while downloading:** a removed paper's running download is cancelled; a download that finishes after its paper was removed deletes its file.
- **Attach:** reads the picked URL inside `startAccessingSecurityScopedResource()`, stores it as `.attached`; an unsaved paper gives `.unreadable`. Attach and Remove first cancel a running download.
- **Undo:** `RemovedPaper` gains `pdf: PaperPdf?`, captured on delete and written back on restore. `discardRemoved` deletes the file only if `removed.pdf != nil` and no saved paper has that local ID again.
- **Delete downloaded:** clears the columns of every downloaded PDF, then deletes those files. Attached files stay.
- **Startup:** `AppContainer` calls `sweepOrphans()` once after the database opens.
- `LiveDependencies` gains `pdfs: any PdfRepository`; `HashiyaTesting` gains `FakePdfRepository`, with the same recorders as Android's fake.

## 7. Details (`FeaturePaperDetails`)

- The **PDF row** replaces the "Open PDF" link button ("Open DOI" stays), below the Collections row.
- `PaperDetailsViewModel.pdf` is a `PdfRow(state, link)` with Android's states (`available`, `none`, `downloading`, `stored`, `failed`) and actions (Read, Download, Cancel, Attach, Try again, Open in browser, Replace, Remove, Open link), with Android's priority: a running download, then a stored file, then a failure other than `.noLink`, then the link.
- **Failed:** Try again and Open in browser only when a link exists; Attach always.
- **Replace** and **Remove** confirm first (`confirmationDialog`). Attach uses `.fileImporter(allowedContentTypes: [.pdf])`; its errors show as banners ("That file isn't a PDF.", "Couldn't read that file.", the 100 MB message).
- **Notes and the reader:** **Read** first saves pending notes (`saveNow`), then pushes the reader; returning reloads the notes when nothing typed is unsaved, as Android does, so notes written in the reader's sheet show on Details and are never overwritten.
- Sizes use `ByteCountFormatter` (`.file`) with `HashiyaLanguage.locale`.

## 8. Reader (`FeatureReader`, new)

A package target and product `FeatureReader` (depends on `HashiyaData`, `HashiyaModel`, `HashiyaDesignSystem`), with `FeatureReaderTests`. `RootView` pushes it from Details with `ReaderRoute(openAlexID:)`; the tab bar stays hidden.

- **Pages:** `PDFView` in a `UIViewRepresentable`, `displayMode = .singlePageContinuous`, `displayDirection = .vertical`, `autoScales = true`, min scale = fit width, max scale = 4× fit width. Double-tap zooms between fit width and 2.5×. Never mirrored in Arabic.
- **Search:** `isFindInteractionEnabled = true` on iOS 16+ (the app's floor is 17), opened from a toolbar search button.
- **Page pill:** "%1$d of %2$d" from `PDFViewPageChanged`, shown while scrolling, faded 1.5 s after it stops.
- **Last page:** restored on open (`go(to:)`), saved 1 s after the page stops changing, and flushed when the reader disappears.
- **Notes:** a sheet with medium and large detents holding the same note fields and the same autosave as Details. To share them without features importing each other, the note fields move to `HashiyaDesignSystem` (`NoteFields`) and the autosave to `HashiyaData` (`NotesEditor`), mirroring Android; `PaperDetailsViewModel` delegates to `NotesEditor`. The sheet saves before closing, and the reader saves before leaving.
- **Share:** `ShareLink(item: fileURL)` in the toolbar.
- **Can't open:** a file `PDFDocument(url:)` can't open (damaged, or locked with a password) shows "This PDF can't be opened." with **Replace PDF** and **Remove PDF**, without confirmation.
- `ReaderViewModel(openAlexID:pdfs:library:notes:)` is tested with a small PDF generated in the test.

## 9. Library and Settings

- **Library:** a small PDF symbol (`doc.richtext`, accessibility label "PDF available offline") after the status badge on rows where `hasPdf`. `LibraryViewModel` calls `pdfs.discardRemoved(removed)` when the Undo banner expires and when a newer removal replaces it; never on Undo, and not when leaving the screen (the startup sweep catches that).
- **Settings:** a **Storage** section: "Downloaded PDFs · %1$@ · %2$d files" always, "Attached PDFs · …" when any exist, and **Delete downloaded PDFs** when any downloaded exist, after the confirmation with Android's plural message. Storage loads when Settings opens and after deleting.

## 10. Strings

Android's keys (spec §10 plus the merged additions), in iOS naming, with English and Arabic copied verbatim from `main`'s `strings.xml` (`%1$s` → `%@`):

| Module | Keys |
|---|---|
| `FeaturePaperDetails` | `details.pdf`, `details.pdfAvailable`, `details.pdfNone`, `details.pdfDownload`, `details.pdfAttach`, `details.pdfReplace`, `details.pdfRemove`, `details.pdfCancel`, `details.pdfProgress`, `details.pdfDownloaded`, `details.pdfAttached`, `details.pdfFailed`, `details.pdfOffline`, `details.pdfNotPdf`, `details.pdfTooLarge`, `details.pdfHttp`, `details.pdfTryAgain`, `details.pdfOpenBrowser`, `details.pdfOpenLink`, `details.pdfReplaceTitle`, `details.pdfRemoveTitle`, `details.pdfAttachNotPdf`, `details.pdfAttachFailed` (the old `details.openPDF` is removed) |
| `FeatureReader` | `reader.page`, `reader.notes`, `reader.share`, `reader.cantOpen`, `reader.back`, `reader.closeNotes`, `reader.replacePdf`, `reader.removePdf`, `reader.notPdf`, `reader.tooLarge`, `reader.attachFailed`, `reader.notesSaveFailed`, `reader.retry`, plus iOS-only `reader.search` ("Search in PDF" / "البحث في ملف PDF") |
| `HashiyaDesignSystem` | the note field strings that `NoteFields` uses, moved from `FeaturePaperDetails` with their English and Arabic unchanged; the plan names them from the current catalog |
| `FeatureLibrary` | `library.hasPdf` |
| `FeatureSettings` | `settings.storage`, `settings.deleteDownloaded`, `settings.delete`, `settings.cancel`, and the plurals `settings.downloadedPdfs`, `settings.attachedPdfs`, `settings.deleteDownloadedMessage` as String Catalog plural variations with all six Arabic forms, copied from Android's `<plurals>` |

"PDF" stays in Latin script, isolated where needed, as before.

## 11. Errors

Android's §11 table applies. In addition:

| Case | Behaviour |
|---|---|
| The app goes to the background mid-download | Background time is requested; if it runs out, the download is cancelled, the `.part` file deleted, and the row offers Download / Try again. |
| A picked file isn't readable (provider offline, permission lost) | `.unreadable`: "Couldn't read that file." |
| The App Group container is unavailable | Downloads and attaches report `.http` / `.unreadable`; nothing is written elsewhere. |

## 12. Testing

Swift Testing, test-first, hand-written fakes, as in specs 1–5.

| Target | Coverage |
|---|---|
| `HashiyaModelTests` | `PaperPdf`, `PdfStorage`, `LibraryPaper.hasPdf` default. |
| `HashiyaDatabaseTests` | Migration `v4` → `v5` (everything kept, new columns null); `v1` → `v5`; each `PaperStore` PDF operation; `hasPdf` in the snapshot; delete returns the PDF columns and restore writes them. |
| `HashiyaDataTests` | `PdfFileStore` in a temporary directory (atomic move, `%PDF-` within 1024 bytes and a preamble accepted, the size limit, sweep keeps listed IDs and removes the rest and `.part` files). The repository with `URLProtocolStub`: progress, redirects, an HTML body → `.notPDF`, offline, HTTP error, one download per paper, cancel, `upgradeToHTTPS` (lowercase, uppercase, with a port, https unchanged), background-time expiry cancels and cleans up, remove cancels, attach (a PDF, not a PDF, too large, unreadable), Undo keeps the file and `discardRemoved` deletes it only once the paper is gone, `deleteDownloaded` keeps attached files, `sweepOrphans`. `NotesEditor` keeps the existing autosave tests passing. |
| `FeaturePaperDetailsTests` | Every PDF row state and action; Read saves notes first; returning reloads notes only when nothing is unsaved. |
| `FeatureReaderTests` | Opens on the stored last page; saves the page debounced and on disappear; can't-open for a damaged file; notes sheet reads once and autosaves. |
| `FeatureLibraryTests` | An expired Undo and a replaced banner discard the PDF; Undo doesn't. |
| `FeatureSettingsTests` | Storage lines and the delete flow. |
| Snapshots | English and Arabic × light and dark, on iOS 26 and 18: the Details PDF row in each state; the reader with a generated two-page PDF and the page pill; the Notes sheet over the reader; Settings Storage; a Library row with the PDF symbol. Existing Details baselines change (the row replaces Open PDF) and are re-recorded. |
| `HashiyaUITests` | Details → Download (UI-testing stub serves a small PDF) → Read → Notes → type → close → back → the note shows on Details. |

## 13. Differences from Android

| Area | Android | iOS | Why |
|---|---|---|---|
| Renderer | `PdfRenderer` pages in a lazy list | PDFKit `PDFView` | PDFKit is the platform viewer; it renders and caches pages itself. |
| Search inside the PDF | — | Find bar (`isFindInteractionEnabled`) | Free with PDFKit. |
| Background | Downloads stop with the process | Background time requested; cancelled cleanly on expiry | iOS suspends apps seconds after backgrounding. |
| File location | `filesDir/pdfs/` | App Group container `pdfs/` | Next to the shared database; one place to sweep. |
| Picker | `OpenDocument` | `.fileImporter` with security-scoped access | Platform API. |
| Share | `FileProvider` + `ACTION_SEND` | `ShareLink` | Platform API. |
| Notes sharing | `NotesEditor` in `core/data`, fields in `core/designsystem` | `NotesEditor` in `HashiyaData`, `NoteFields` in `HashiyaDesignSystem` | Same structure, iOS packages. |
| https upgrade | Added after merge (PR #21) | Built in from the start | Android's device lesson. |
| Extra string | — | `reader.search` | The find button. |

## 14. Acceptance criteria (on a device)

1. Install the sub-project 5 build with a library, then this build over it: everything is still there.
2. Download an arXiv paper with an `http://` link, turn on airplane mode, read it; scroll and zoom a 200-page PDF smoothly; search for a word inside it.
3. Close the reader on page 12; reopen: page 12.
4. Notes from the reader appear on Details.
5. Start a large download, switch apps for a few seconds, come back: it finished, or the row offers Try again.
6. A paywalled link shows "This link opens a web page, not a PDF."; Attach from iCloud Drive works.
7. Remove → Undo keeps the PDF; a final removal frees the space in Settings.
8. Delete downloaded PDFs keeps an attached one.
9. Share the PDF to Files or Mail.
10. In العربية, the row, reader controls and Settings are right-to-left and the pages are not mirrored.
11. CI (the "iOS" workflow) is green, with the new baselines recorded by `ios-record-snapshots`.

## 15. Risks

| Risk | Mitigation |
|---|---|
| OpenAlex links are `http://` or lead to landing pages | `upgradeToHTTPS`, the `%PDF-` check, and Attach as the way out. |
| Background time runs out on a slow network | Clean cancel and Try again; acceptance check 5. |
| Moving the notes autosave breaks Details | The existing autosave tests run against `NotesEditor` unchanged; the UI test covers notes across the reader. |
| PDFKit memory on very large PDFs | PDFKit tiles and caches pages; acceptance check 2 uses a 200-page PDF. |
| `v5` lands before `v4` | Implementation starts only after #20 merges; the plan's first step rebases onto `main`. |
| Flaky UI tests on slow CI runners | Waits use `UITestTimeout.long`; CI uploads the result bundle on failure. |
