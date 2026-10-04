# Backup and restore — Design

- **Date:** 2026-10-04
- **Status:** Approved in brainstorming; awaiting spec review
- **Scope:** Both platforms. The file format and behavior here are shared; Android ships first, then iOS. Each platform gets its own implementation plan. Crash reporting, the other half of the pre-release "backup and crash reporting" item, gets its own spec.

## 1. Context

A user's whole library (papers, notes, reading status, collections, PDFs) lives only on the device. Two gaps follow:

- **A lost or replaced phone.** OS backups cover part of this, but on Android the backup rules are still the generated templates, so Auto Backup copies everything, PDFs included, and stops backing up the app once its data passes the 25 MB cloud quota.
- **Owning the data.** There is no way to keep a copy of the library as a file, move it to another device or platform, or hand it to a colleague.

Both databases share one schema (iOS migrates to Android's Room version 5), PDFs live in a `pdfs` folder next to the database on each platform, and each platform's `PdfRepository.sweepOrphans()` deletes PDF files that have no row at startup.

The same standards as before apply: modular architecture and dependency rules, English and Arabic with full RTL, TDD with hand-written fakes, screenshot tests recorded on CI, and a green CI.

### Decisions made during brainstorming

| Topic | Decision |
|---|---|
| What it protects against | Mostly a lost or replaced phone, and the user owning their library as a file. Moving between Android and iOS comes from using one format. |
| Mechanism | A manual export to a file plus fixed OS backup rules. No scheduled or automatic exports, no reminders. |
| PDFs in the file | The user chooses at export time; off by default. |
| Restore | Merge into the current library; the current device wins every conflict. No Replace mode. |
| File format | A versioned JSON archive, independent of the database schema. Not a copy of the SQLite file, and not BibTeX. |
| Settings and API key | Never in the export file, which may be shared. OS backups carry them. |

## 2. Goals and non-goals

### Goals

1. From Settings, export the library to a `.hashiya` file saved anywhere the system save dialog offers, with or without PDFs.
2. Restore a `.hashiya` file made on either platform, from Settings or by opening the file, merging it into the library without losing anything already there.
3. OS backups keep the database and settings, keep within Android's cloud quota, and never leave a paper pointing at a PDF that isn't there.

### Non-goals

- Scheduled or automatic backups, and reminders to back up.
- Replacing the library on restore.
- Exporting settings or the API key.
- Matching papers by title and year.
- Sync between devices.

## 3. File format (format 1)

A `.hashiya` file is a zip archive (media type `application/zip`). The suggested name is `Hashiya-library-YYYY-MM-DD.hashiya`. Both apps register the extension so the file opens in Hashiya.

```
manifest.json
library.json
pdfs/<ref>.pdf      only when PDFs were included
```

### manifest.json

```json
{ "format": 1, "app": "0.3.0 (Android)", "exportedAt": "2026-10-04T14:05:00Z",
  "papers": 182, "collections": 6, "includesPdfs": true }
```

### library.json

```json
{
  "papers": [
    {
      "ref": 3,
      "openAlexId": "W2741809807", "doi": "10.1000/xyz", "title": "…", "year": 2017,
      "venue": "…", "abstract": "…", "citationCount": 12, "isOpenAccess": true,
      "oaPdfUrl": "https://…", "savedAt": 1790000000000, "readingStatus": "reading",
      "workType": "article", "sourceType": "journal", "publisher": "…",
      "volume": "4", "issue": "2", "firstPage": "10", "lastPage": "19",
      "citeKey": "smith2017deep", "detailsFetched": true,
      "authors": [ { "name": "A. Smith", "openAlexAuthorId": "A123" } ],
      "notes": { "summary": "…", "researchQuestion": "…", "method": "…", "keyFindings": "…",
                 "limitations": "…", "thoughts": "…", "updatedAt": 1790000000000 },
      "pdf": { "source": "downloaded", "addedAt": 1790000000000, "lastPage": 4, "file": "pdfs/3.pdf" }
    }
  ],
  "collections": [ { "name": "Thesis", "createdAt": 1790000000000, "papers": [3, 17, 42] } ]
}
```

Rules:

- Field values mirror the database columns: times are epoch milliseconds, `readingStatus` is `to_read`, `reading` or `read`, `pdf.source` is `downloaded` or `attached`. Optional columns may be `null` or absent.
- `authors` is in position order.
- `notes` is `null` when the paper has no notes row.
- `pdf` is `null` when no PDF is stored. When one is stored but not in the archive (PDFs switched off, or the file was missing at export), `file` is `null`.
- No local database ids. Each paper has a `ref` that is unique within the file, and collections refer to papers by `ref`. The PDF entry name uses the `ref`.
- The search index, `name_key` and `pdf_size` are not stored; they are rebuilt on restore.
- A reader rejects a `format` higher than it knows and ignores fields it doesn't know. Adding optional fields doesn't change the format number; anything that changes the meaning of an existing field does.

## 4. Export

1. Settings gets a **Backup** section with **Export library** and **Restore from backup**. Export is disabled while the library is empty.
2. Export opens a sheet with the counts ("182 papers · 6 collections"), an **Include PDFs** switch, off by default, labelled with the count and total size of stored PDFs (summed from `pdf_size`), and an **Export** button. With no stored PDFs the switch is hidden.
3. The archive is built in a temporary file in the cache directory, with progress and Cancel. The library is read in one read transaction so the snapshot is consistent. PDFs are streamed into the zip one at a time.
4. The system save dialog opens with the suggested name: `ActivityResultContracts.CreateDocument` on Android, `.fileExporter` on iOS. The temporary file is deleted on success, failure or cancel.
5. Success shows "Library exported". If some stored PDFs were missing on disk, they are written with `"file": null` and the message says how many were left out.
6. A write failure (out of space, the provider refused) shows "Couldn't export" with a short reason and leaves no partial file.

Zip support: `java.util.zip` on Android; on iOS, **ZIPFoundation** (MIT, Swift Package) added to HashiyaKit, since Foundation can't read zip archives.

## 5. Restore

### Flow

1. The user picks a file from **Restore from backup** (`ActivityResultContracts.OpenDocument` on Android, `.fileImporter` on iOS) or opens a `.hashiya` file from another app. Both land on the same restore screen.
2. The app reads `manifest.json` and `library.json` and validates them before writing anything:
   - Not a zip, or no manifest: "This isn't a Hashiya backup."
   - `format` higher than supported: "This backup was made by a newer version of Hashiya. Update the app to restore it."
   - `library.json` malformed, over 50 MB, or with collections pointing at unknown refs: "This backup is damaged."
3. A preview shows the backup's date and counts, how many papers are new and how many are already in the library, and **Add to library** / Cancel.
4. Applying shows progress, then a result such as "Added 150 papers, notes on 3 existing papers, 6 collections, 38 PDFs", including how many PDFs were referenced but missing from the archive.

### Matching

A backup paper matches a library paper by, in order:

1. the same `openAlexId`, when the backup paper has one;
2. otherwise the same DOI, compared lowercase without a `https://doi.org/` or `doi:` prefix;
3. otherwise it is new.

Android note: the Android app can't show a paper with no `openAlexId` yet, so its restore skips those papers (null or blank id), with their PDFs and collection links. The preview and the result say how many were skipped.

### Merge rules (the device wins)

| Case | Result |
|---|---|
| New paper | Inserted with all its fields, authors, notes and PDF, and its search row written as a normal save writes it. A `citeKey` already used by another paper is dropped (null) and assigned again at the next export or copy. |
| Matched paper's fields and reading status | Unchanged. |
| Matched paper's notes | The backup's notes are added only when the library paper has none. |
| Matched paper's PDF | The backup's PDF is added only when the library paper has none and the archive holds the file. |
| Collections | Matched by name key (trimmed, lowercased). Missing ones are created with the backup's name and `createdAt`. Membership becomes the union; links are added for new and matched papers alike. |

### Atomicity and safety

- PDFs are copied into the PDF store first, through the same checks as an attach (`%PDF-` header, size limit), while holding the lock the sweep uses. Then every database write happens in one transaction. If the transaction fails, the PDFs copied in this run are deleted, so the library is left exactly as it was.
- Only `manifest.json`, `library.json` and the `pdfs/` entries that `library.json` references are read. Entries with `..`, absolute paths or other names are ignored. Each PDF entry's size is checked against free space before copying.
- A referenced PDF missing from the archive, or one that fails the PDF checks, is skipped and counted; it doesn't fail the restore.

## 6. OS backups and missing PDFs

### Android Auto Backup

`data_extraction_rules.xml` (Android 12+) and `backup_rules.xml` (Android 11 and older) replace the templates:

| | Cloud backup | Device-to-device transfer |
|---|---|---|
| Library database | included | included |
| `files/datastore/` (settings, API key) | included | included |
| `files/pdfs/` | excluded | included |

Cloud backup sets `disableIfNoEncryptionCapabilities="true"`, so the API key is only uploaded in an end-to-end encrypted backup. `backup_rules.xml` has no device-transfer section and follows the cloud column.

### iOS

The App Group container is already in iCloud and device backups. A PDF stored with source `downloaded` is marked `isExcludedFromBackup`, because it can be downloaded again; an `attached` PDF stays in backups. A one-time pass at startup marks existing downloaded PDFs.

### Rows whose PDF is gone

`sweepOrphans()` on both platforms gains a second step: for rows whose `pdf_*` columns say a PDF is stored but whose file is missing, it clears the four columns, so the paper shows the normal "Download PDF" / "Attach PDF" state. It runs under the existing sweep lock, so it can't race a store in progress. This covers OS restores without PDFs and any other lost file.

## 7. Architecture

- A `LibraryBackup` unit in the data layer (`core/data` on Android, `HashiyaData` on iOS) with three operations: export (`includePdfs`, output stream or URL, progress), read and validate (returns a preview), and apply (returns a result). It uses the existing DAOs, stores and the PDF store; it holds no UI state.
- The archive's JSON types are their own small models with a codec (kotlinx.serialization on Android, `Codable` on iOS), separate from the database entities, so schema changes don't silently change the file format.
- Settings drives a backup state: idle, exporting or restoring with progress, preview, done, error. The restore screen is reachable from Settings and from opening a file.
- All new strings in English and Arabic; layouts checked in RTL.

## 8. Testing

### Shared fixture

`testdata/backup/format-1/` holds a reviewable `manifest.json`, `library.json` and a tiny PDF; `scripts/make-backup-fixture.sh` zips it into `testdata/backup/format-1.hashiya`, which is checked in. It contains a paper with every field set, a paper with neither OpenAlex id nor DOI, notes, Arabic and other non-Latin text, two collections, one PDF in the archive and one PDF reference with `"file": null`. Unit tests on both platforms decode it and assert the same values.

### Unit tests

In-memory database and a temporary PDF folder:

- Round trip: export, then restore into an empty library, gives the same papers, author order, notes, statuses, collections and PDFs, and Library search finds the restored papers.
- Each merge rule: match by OpenAlex id; match by normalized DOI; no match; the library's notes and PDF kept; backup notes and PDF filling gaps; collection names merged case-insensitively; cite key clash.
- Atomicity: a failure injected during the transaction leaves the database unchanged and no copied PDFs behind.
- Rejections: not a zip, no manifest, newer format, malformed JSON, oversized `library.json`, unknown collection refs, `..` entry names, a referenced PDF missing from the archive, an entry that isn't a PDF.
- Export: a PDF missing on disk is written with `"file": null` and counted; with PDFs off the archive has no `pdfs/` entries.
- Sweep: rows with a missing file are cleared; files with no row are still deleted.
- iOS: downloaded PDFs are excluded from backup, attached ones are not.

### UI and screenshot tests

One UI test per platform: Settings → Export shows the export sheet with the right counts and PDF size. The system pickers aren't driven. Screenshot tests for the export sheet and the restore preview, in English and Arabic.

### Manual device checks

- Android: `adb shell bmgr backupnow com.etatech.hashiya`, uninstall, reinstall: the library and settings come back, the PDFs don't, and those papers show "Download PDF".
- Both: export with PDFs to Drive or Files on one platform and restore it on the other.

## 9. Delivery

1. Android, one PR: format, export, restore, backup rules, sweep change.
2. iOS, one PR, built and tested on the Mac: the same, plus ZIPFoundation and the backup exclusion.

Format 1 ships in the first release after 0.2.0.
