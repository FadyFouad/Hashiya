# Backup and Restore (iOS) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The iOS/iPadOS half of backup and restore: export the library to a `.hashiya` file (optionally with PDFs), restore one by merging it into the library, open `.hashiya` files from Files/Mail/AirDrop, and keep downloaded PDFs out of iCloud backups — byte-compatible with the Android app (PR #33).

**Architecture:** One universal app; nothing here is iPhone- or iPad-specific except where noted. `HashiyaDatabase` gains a `PaperStore` backup extension (snapshot, matching, one-transaction merge). `HashiyaData` gains the archive format (Codable models kept apart from the GRDB records), a ZIPFoundation-based reader/writer, and `ArchiveLibraryBackup`, which stages PDFs as `.part` files inside the PDF repository's store gate, merges, then renames them into place. `FeatureSettings` gains a Backup section, an Export screen and a Restore screen; the app target declares the `.hashiya` document type and routes `onOpenURL` to Restore in the window that opened the file.

**Tech Stack:** Swift 6 (strict concurrency), SwiftUI (iOS 17+), GRDB 7.11.1, ZIPFoundation (new), Swift Testing, swift-snapshot-testing 1.19.6, XCTest UI tests, XcodeGen, String Catalogs (en + ar).

**Spec:** `docs/superpowers/specs/2026-10-04-backup-and-restore-design.md` (binding). Android reference implementation: branch `feat/android-backup-restore` / PR #33 (`android/core/data/.../backup/`, `android/core/database/.../dao/BackupDao.kt`) — the iOS behaviour must match it, including every fix made during its review.

## Global Constraints

- Deployment target iOS 17.0; Swift 6 language mode; everything crossing tasks is `Sendable`.
- Format number `1`. A reader rejects a higher `format`; unknown JSON fields are ignored; missing optional fields use defaults (the shared fixture omits `citationCount`, `isOpenAccess`, `year` on some papers).
- Archive entries: `manifest.json`, `library.json`, `pdfs/<ref>.pdf`. Suggested name `Hashiya-library-YYYY-MM-DD.hashiya` (UTC date). Exported type `com.etatech.hashiya.backup`, extension `hashiya`, conforms to `public.zip-archive`.
- `library.json` capped at 50 MB (`50 * 1024 * 1024` bytes counted while reading, never trusting the entry header); `manifest.json` at 64 KB.
- Restore merges; the device wins every conflict. No Replace mode. The export never contains settings or the API key.
- Matching: same `openAlexId` when the backup paper has one; only when it has none, the same normalized DOI (`normalizeDOI` from HashiyaModel); otherwise new.
- **Backup papers without a usable OpenAlex id (missing or blank after trimming) are skipped and counted** (`papersSkipped`): the app keys every paper operation by OpenAlex id and maps a nil id to `""` (PaperMapping.swift), so such rows would collide. Their PDFs are not staged; collections drop their refs. Same as Android.
- A PDF entry is read only by the exact name `pdfs/<that paper's ref>.pdf`; an entry whose declared size exceeds `PdfFileStore.maxPdfBytes` counts as missing before any free-space check.
- Downloaded PDFs (`pdf_source = downloaded`) have `isExcludedFromBackup = true`; attached PDFs stay in backups.
- All new strings in the String Catalogs, English and Arabic; Arabic plurals need all six forms (`zero one two few many other`) — `python3 ios/scripts/check-translations.py` must pass. Strings use the existing `L10n` pattern (`Text(verbatim: L10n.string("…"))`), never `LocalizedStringKey`.
- Features (`FeatureSettings`) never import GRDB or HashiyaDatabase.
- Commits authored `Fady <fady.fouad.a@gmail.com>`; no AI attribution anywhere. Commit prefix `feat(ios):` / `fix(ios):` / `test(ios):`.
- New files in the **app target** (`ios/Hashiya/`) need `xcodegen generate --spec ios/project.yml`; package files don't.
- Test commands (from repo root):
  - package tests: `cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:<Target>/<Suite>` (use any installed iPhone simulator; `xcrun simctl list devices available`).
  - app + snapshots: `xcodegen generate --spec ios/project.yml && xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -skip-testing:HashiyaUITests`.
  - Snapshot baselines are recorded only on CI (`bash ios/scripts/record-snapshots-on-ci.sh`); locally a new or changed snapshot fails by design — report it, don't record locally.

## Review Focus

1. **Opening a `.hashiya` from Files on iPad with two Hashiya windows open** — only the window that received the file shows Restore; a second restore started from the other window while one is applying is refused with a clear message, never interleaved. (Task 7 `aSecondApplyWhileOneRunsIsRefused`; Task 9 routing by `onOpenURL`, which SwiftUI delivers to one scene.)
2. **The app goes to the background mid-restore** — the merge must not fail because `HashiyaApp` suspends the shared database: restore registers as a store (`storesFinished` waits for it) and holds background time. (Task 3 `storesFinishedWaitsForAGateStore`; Task 7 uses `background.begin`.)
3. **The Android-made fixture restores on iOS and an iOS export restores on Android** — field names, nulls, defaults and dates identical. (Task 1 decodes the shared fixture; Task 5 `exportedJsonUsesTheSharedFieldNames`.)
4. **A backup paper with no OpenAlex id** — skipped and counted, no row with `open_alex_id = NULL` appears, no list shows a blank-id paper. (Task 7 `papersWithoutAnOpenAlexIdAreSkipped` reads back through `GRDBLibraryRepository.observeLibrary`.)
5. **Cancelling after the merge committed / process killed between merge and renames** — PDFs either land or the row is cleared by the next sweep; no `.part` left behind. (Task 2 sweep clears missing-file rows; Task 7 runs merge + renames in an unstructured task.)

---

## File Structure

**ios/HashiyaKit/Package.swift** — add ZIPFoundation, linked to `HashiyaData` (and `HashiyaDataTests`).

**HashiyaDatabase**
- Create `Sources/HashiyaDatabase/BackupRows.swift` — `BackupSnapshot`, `IncomingPaper`, `IncomingCollection`, `MergeOutcome`, `PdfTotals`.
- Create `Sources/HashiyaDatabase/PaperStore+Backup.swift` — snapshot, counts, `matchFor`, `merge`.
- Modify `Sources/HashiyaDatabase/PaperStore.swift` — `writer` from `private` to `let` (internal) so the extension file can use it; add `clearPdfs(paperIDs:)` if convenient.
- Test `Tests/HashiyaDatabaseTests/PaperStoreBackupTests.swift`.

**HashiyaData**
- Create `Sources/HashiyaData/Backup/BackupFormat.swift` — Codable archive models, constants, coders, dates, file name.
- Create `Sources/HashiyaData/Backup/BackupArchive.swift` — `writeArchive`, `readArchive`.
- Create `Sources/HashiyaData/Backup/LibraryBackup.swift` — public protocol and result types.
- Create `Sources/HashiyaData/Backup/ArchiveLibraryBackup.swift` — the implementation.
- Create `Sources/HashiyaData/Backup/IncomingMapping.swift` — `BackupPaper` → `IncomingPaper`.
- Modify `Sources/HashiyaData/Pdf/PdfFileStore.swift` (stage/commit/exclusion), `Pdf/GRDBPdfRepository.swift` (sweep, exclusion, `withStoreGate`), `LibraryRepositories.swift`, `LiveDependencies.swift`.
- Tests `Tests/HashiyaDataTests/BackupFormatTests.swift`, `BackupArchiveTests.swift`, `ArchiveLibraryBackupTests.swift`, additions to `PdfFileStoreTests.swift` / `GRDBPdfRepositoryTests.swift`.

**HashiyaTesting** — `FakeLibraryBackup.swift` (+ `Tests/HashiyaDataTests/FakeLibraryBackupTests.swift`).

**FeatureSettings**
- Create `Strings.swift` (internal `L10n`, moved out of SettingsView.swift), `BackupSection.swift`, `ExportBackupView.swift`, `RestoreViewModel.swift`, `RestoreView.swift`, `UTType+Hashiya.swift`.
- Modify `SettingsViewModel.swift`, `SettingsView.swift`, `Resources/Localizable.xcstrings`.
- Tests `Tests/FeatureSettingsTests/SettingsBackupTests.swift`, `RestoreViewModelTests.swift`; snapshots `ios/HashiyaSnapshotTests/BackupSnapshotTests.swift`.

**App target** — modify `ios/Hashiya/Info.plist` (document type, exported UTI, `LSSupportsOpeningDocumentsInPlace = NO`), `AppContainer.swift` (factories), `RootView.swift` (`onOpenURL` → Restore sheet), `UITestingStubs.swift` (backup dependency). UI test `ios/HashiyaUITests/BackupFlowTests.swift`.

---

### Task 1: ZIPFoundation and the archive format

**Files:**
- Modify: `ios/HashiyaKit/Package.swift`
- Create: `ios/HashiyaKit/Sources/HashiyaData/Backup/BackupFormat.swift`
- Test: `ios/HashiyaKit/Tests/HashiyaDataTests/BackupFormatTests.swift`

**Interfaces:**
- Produces (internal to HashiyaData unless marked): `BackupFormat.version = 1`, `.manifestEntry`, `.libraryEntry`, `.maxLibraryBytes = 50 * 1024 * 1024`, `.maxManifestBytes = 64 * 1024`, `static func pdfEntry(ref: Int) -> String`, `static func fileName(at ms: Int64) -> String`, `static func isoUTC(_ ms: Int64) -> String`, `static func parseISOUTC(_ text: String) -> Int64?`, `static let encoder: JSONEncoder`, `static let decoder: JSONDecoder`; models `BackupManifest`, `BackupLibrary`, `BackupPaper`, `BackupAuthor`, `BackupNotes`, `BackupPdf`, `BackupCollection` (all `Codable, Equatable, Sendable`); test helper `sharedFixtureURL` (test file only).

- [ ] **Step 1: Add the dependency**

In `Package.swift` add to `dependencies`:

```swift
.package(url: "https://github.com/weichsel/ZIPFoundation.git", exact: "0.9.19"),
```

and a constant next to `grdb`:

```swift
let zip: Target.Dependency = .product(name: "ZIPFoundation", package: "ZIPFoundation")
```

Add `zip` to the `HashiyaData` target's dependencies and to `HashiyaDataTests`. Run `cd ios/HashiyaKit && swift package resolve`; if 0.9.19 doesn't resolve or fails Swift 6 strict concurrency, use the newest 0.9.x that does and say so in the report.

- [ ] **Step 2: Write the failing test**

`ios/HashiyaKit/Tests/HashiyaDataTests/BackupFormatTests.swift`:

```swift
import Foundation
import Testing
import ZIPFoundation
@testable import HashiyaData

/// `testdata/backup/format-1.hashiya` at the repository root, shared with Android's tests.
let sharedFixtureURL: URL = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent()   // HashiyaDataTests
    .deletingLastPathComponent()   // Tests
    .deletingLastPathComponent()   // HashiyaKit
    .deletingLastPathComponent()   // ios
    .deletingLastPathComponent()   // repository root
    .appending(path: "testdata/backup/format-1.hashiya")

struct BackupFormatTests {
    private func entryData(_ archive: Archive, _ name: String) throws -> Data {
        var data = Data()
        _ = try archive.extract(try #require(archive[name]), consumer: { data.append($0) })
        return data
    }

    @Test func decodesTheSharedFixture() throws {
        let archive = try Archive(url: sharedFixtureURL, accessMode: .read)
        let manifest = try BackupFormat.decoder.decode(BackupManifest.self, from: entryData(archive, BackupFormat.manifestEntry))
        #expect(manifest.format == 1)
        #expect(manifest.includesPdfs)

        let library = try BackupFormat.decoder.decode(BackupLibrary.self, from: entryData(archive, BackupFormat.libraryEntry))
        #expect(library.papers.map(\.ref) == [1, 2, 3])
        let first = library.papers[0]
        #expect(first.openAlexId == "W2741809807")
        #expect(first.citeKey == "lecun2015deep")
        #expect(first.authors.map(\.name) == ["Yann LeCun", "Yoshua Bengio", "Geoffrey Hinton"])
        #expect(first.authors[2].openAlexAuthorId == nil)
        #expect(first.notes?.thoughts == "Cite in chapter 2")
        #expect(first.pdf == BackupPdf(source: "downloaded", addedAt: 1_790_000_200_000, lastPage: 4, file: "pdfs/1.pdf"))

        let second = library.papers[1]
        #expect(second.openAlexId == nil)
        #expect(second.title == "التعلم العميق في معالجة اللغة العربية")
        #expect(second.citationCount == 0)
        #expect(second.isOpenAccess == false)
        #expect(second.pdf?.file == nil)

        #expect(library.papers[2].readingStatus == "read")
        #expect(library.collections == [
            BackupCollection(name: "Thesis", createdAt: 1_790_000_600_000, papers: [1, 2]),
            BackupCollection(name: "مراجعة", createdAt: 1_790_000_700_000, papers: [3]),
        ])
        #expect(archive[BackupFormat.pdfEntry(ref: 1)] != nil)
    }

    @Test func roundTripsAPaper() throws {
        let paper = BackupPaper(ref: 7, title: "T", savedAt: 5, authors: [BackupAuthor(name: "A", openAlexAuthorId: "A1")],
                                notes: BackupNotes(summary: "s", updatedAt: 6),
                                pdf: BackupPdf(source: "attached", addedAt: 1, lastPage: 2, file: nil))
        let data = try BackupFormat.encoder.encode(BackupLibrary(papers: [paper], collections: []))
        #expect(try BackupFormat.decoder.decode(BackupLibrary.self, from: data).papers == [paper])
    }

    @Test func datesAndFileNamesAreUTC() {
        #expect(BackupFormat.isoUTC(1_790_000_000_000) == "2026-09-21T14:13:20Z")
        #expect(BackupFormat.parseISOUTC("2026-10-04T14:05:00Z") == 1_791_122_700_000)
        #expect(BackupFormat.parseISOUTC("yesterday") == nil)
        #expect(BackupFormat.fileName(at: 1_790_000_000_000) == "Hashiya-library-2026-09-21.hashiya")
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:HashiyaDataTests/BackupFormatTests`
Expected: build FAILS — `BackupFormat` / `BackupManifest` not found.

- [ ] **Step 4: Write the format**

`ios/HashiyaKit/Sources/HashiyaData/Backup/BackupFormat.swift`:

```swift
import Foundation

/// The `.hashiya` archive, format 1 (docs/superpowers/specs/2026-10-04-backup-and-restore-design.md §3), shared with Android.
/// These types are the file format: they are kept apart from the GRDB records so a schema change never silently changes
/// what a backup holds. Missing fields decode to their defaults and unknown ones are ignored.
enum BackupFormat {
    static let version = 1
    static let manifestEntry = "manifest.json"
    static let libraryEntry = "library.json"
    static let maxLibraryBytes = 50 * 1024 * 1024
    static let maxManifestBytes = 64 * 1024

    /// The only entry a paper's PDF may be read from; any other `pdf.file` counts as missing.
    static func pdfEntry(ref: Int) -> String { "pdfs/\(ref).pdf" }

    static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return encoder
    }()

    static let decoder = JSONDecoder()

    /// `2026-10-04T14:05:00Z`.
    static func isoUTC(_ ms: Int64) -> String {
        utcFormatter("yyyy-MM-dd'T'HH:mm:ss'Z'").string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
    }

    /// Milliseconds for an `isoUTC` string; nil when it doesn't parse.
    static func parseISOUTC(_ text: String) -> Int64? {
        utcFormatter("yyyy-MM-dd'T'HH:mm:ss'Z'").date(from: text).map { Int64(($0.timeIntervalSince1970 * 1000).rounded()) }
    }

    /// `Hashiya-library-2026-10-04.hashiya`, dated in UTC like the manifest.
    static func fileName(at ms: Int64) -> String {
        "Hashiya-library-\(utcFormatter("yyyy-MM-dd").string(from: Date(timeIntervalSince1970: Double(ms) / 1000))).hashiya"
    }

    private static func utcFormatter(_ format: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = format
        formatter.isLenient = false
        return formatter
    }
}

struct BackupManifest: Codable, Equatable, Sendable {
    var format: Int
    var app = ""
    var exportedAt = ""
    var papers = 0
    var collections = 0
    var includesPdfs = false

    init(format: Int, app: String = "", exportedAt: String = "", papers: Int = 0, collections: Int = 0, includesPdfs: Bool = false) {
        self.format = format
        self.app = app
        self.exportedAt = exportedAt
        self.papers = papers
        self.collections = collections
        self.includesPdfs = includesPdfs
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        format = try c.decode(Int.self, forKey: .format)
        app = try c.decodeIfPresent(String.self, forKey: .app) ?? ""
        exportedAt = try c.decodeIfPresent(String.self, forKey: .exportedAt) ?? ""
        papers = try c.decodeIfPresent(Int.self, forKey: .papers) ?? 0
        collections = try c.decodeIfPresent(Int.self, forKey: .collections) ?? 0
        includesPdfs = try c.decodeIfPresent(Bool.self, forKey: .includesPdfs) ?? false
    }
}

struct BackupLibrary: Codable, Equatable, Sendable {
    var papers: [BackupPaper] = []
    var collections: [BackupCollection] = []

    init(papers: [BackupPaper] = [], collections: [BackupCollection] = []) {
        self.papers = papers
        self.collections = collections
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        papers = try c.decodeIfPresent([BackupPaper].self, forKey: .papers) ?? []
        collections = try c.decodeIfPresent([BackupCollection].self, forKey: .collections) ?? []
    }
}

struct BackupPaper: Codable, Equatable, Sendable {
    /// Unique within the file; collections and the PDF entry refer to it.
    var ref: Int
    var openAlexId: String?
    var doi: String?
    var title: String
    var year: Int?
    var venue: String?
    var abstract: String?
    var citationCount = 0
    var isOpenAccess = false
    var oaPdfUrl: String?
    var savedAt: Int64
    /// `to_read`, `reading` or `read`; anything else restores as `to_read`.
    var readingStatus = "to_read"
    var workType: String?
    var sourceType: String?
    var publisher: String?
    var volume: String?
    var issue: String?
    var firstPage: String?
    var lastPage: String?
    var citeKey: String?
    var detailsFetched = false
    /// In position order.
    var authors: [BackupAuthor] = []
    var notes: BackupNotes?
    var pdf: BackupPdf?

    init(ref: Int, openAlexId: String? = nil, doi: String? = nil, title: String, year: Int? = nil, venue: String? = nil,
         abstract: String? = nil, citationCount: Int = 0, isOpenAccess: Bool = false, oaPdfUrl: String? = nil, savedAt: Int64,
         readingStatus: String = "to_read", workType: String? = nil, sourceType: String? = nil, publisher: String? = nil,
         volume: String? = nil, issue: String? = nil, firstPage: String? = nil, lastPage: String? = nil, citeKey: String? = nil,
         detailsFetched: Bool = false, authors: [BackupAuthor] = [], notes: BackupNotes? = nil, pdf: BackupPdf? = nil) {
        self.ref = ref; self.openAlexId = openAlexId; self.doi = doi; self.title = title; self.year = year; self.venue = venue
        self.abstract = abstract; self.citationCount = citationCount; self.isOpenAccess = isOpenAccess; self.oaPdfUrl = oaPdfUrl
        self.savedAt = savedAt; self.readingStatus = readingStatus; self.workType = workType; self.sourceType = sourceType
        self.publisher = publisher; self.volume = volume; self.issue = issue; self.firstPage = firstPage; self.lastPage = lastPage
        self.citeKey = citeKey; self.detailsFetched = detailsFetched; self.authors = authors; self.notes = notes; self.pdf = pdf
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ref = try c.decode(Int.self, forKey: .ref)
        openAlexId = try c.decodeIfPresent(String.self, forKey: .openAlexId)
        doi = try c.decodeIfPresent(String.self, forKey: .doi)
        title = try c.decode(String.self, forKey: .title)
        year = try c.decodeIfPresent(Int.self, forKey: .year)
        venue = try c.decodeIfPresent(String.self, forKey: .venue)
        abstract = try c.decodeIfPresent(String.self, forKey: .abstract)
        citationCount = try c.decodeIfPresent(Int.self, forKey: .citationCount) ?? 0
        isOpenAccess = try c.decodeIfPresent(Bool.self, forKey: .isOpenAccess) ?? false
        oaPdfUrl = try c.decodeIfPresent(String.self, forKey: .oaPdfUrl)
        savedAt = try c.decode(Int64.self, forKey: .savedAt)
        readingStatus = try c.decodeIfPresent(String.self, forKey: .readingStatus) ?? "to_read"
        workType = try c.decodeIfPresent(String.self, forKey: .workType)
        sourceType = try c.decodeIfPresent(String.self, forKey: .sourceType)
        publisher = try c.decodeIfPresent(String.self, forKey: .publisher)
        volume = try c.decodeIfPresent(String.self, forKey: .volume)
        issue = try c.decodeIfPresent(String.self, forKey: .issue)
        firstPage = try c.decodeIfPresent(String.self, forKey: .firstPage)
        lastPage = try c.decodeIfPresent(String.self, forKey: .lastPage)
        citeKey = try c.decodeIfPresent(String.self, forKey: .citeKey)
        detailsFetched = try c.decodeIfPresent(Bool.self, forKey: .detailsFetched) ?? false
        authors = try c.decodeIfPresent([BackupAuthor].self, forKey: .authors) ?? []
        notes = try c.decodeIfPresent(BackupNotes.self, forKey: .notes)
        pdf = try c.decodeIfPresent(BackupPdf.self, forKey: .pdf)
    }
}

struct BackupAuthor: Codable, Equatable, Sendable {
    var name: String
    var openAlexAuthorId: String?
}

struct BackupNotes: Codable, Equatable, Sendable {
    var summary = ""
    var researchQuestion = ""
    var method = ""
    var keyFindings = ""
    var limitations = ""
    var thoughts = ""
    var updatedAt: Int64 = 0

    init(summary: String = "", researchQuestion: String = "", method: String = "", keyFindings: String = "",
         limitations: String = "", thoughts: String = "", updatedAt: Int64 = 0) {
        self.summary = summary; self.researchQuestion = researchQuestion; self.method = method
        self.keyFindings = keyFindings; self.limitations = limitations; self.thoughts = thoughts; self.updatedAt = updatedAt
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        summary = try c.decodeIfPresent(String.self, forKey: .summary) ?? ""
        researchQuestion = try c.decodeIfPresent(String.self, forKey: .researchQuestion) ?? ""
        method = try c.decodeIfPresent(String.self, forKey: .method) ?? ""
        keyFindings = try c.decodeIfPresent(String.self, forKey: .keyFindings) ?? ""
        limitations = try c.decodeIfPresent(String.self, forKey: .limitations) ?? ""
        thoughts = try c.decodeIfPresent(String.self, forKey: .thoughts) ?? ""
        updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
    }
}

/// A stored PDF. `file` is nil when the PDF isn't in the archive (PDFs left out, or missing at export).
struct BackupPdf: Codable, Equatable, Sendable {
    var source: String
    var addedAt: Int64
    var lastPage = 0
    var file: String?

    init(source: String, addedAt: Int64, lastPage: Int = 0, file: String? = nil) {
        self.source = source; self.addedAt = addedAt; self.lastPage = lastPage; self.file = file
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        source = try c.decode(String.self, forKey: .source)
        addedAt = try c.decode(Int64.self, forKey: .addedAt)
        lastPage = try c.decodeIfPresent(Int.self, forKey: .lastPage) ?? 0
        file = try c.decodeIfPresent(String.self, forKey: .file)
    }
}

struct BackupCollection: Codable, Equatable, Sendable {
    var name: String
    var createdAt: Int64
    var papers: [Int] = []

    init(name: String, createdAt: Int64, papers: [Int] = []) {
        self.name = name; self.createdAt = createdAt; self.papers = papers
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = try c.decode(String.self, forKey: .name)
        createdAt = try c.decode(Int64.self, forKey: .createdAt)
        papers = try c.decodeIfPresent([Int].self, forKey: .papers) ?? []
    }
}
```

(Format with one statement per line if the project's style requires; the semicolons above only keep the plan short.)

- [ ] **Step 5: Run the tests**

Run the Step 3 command. Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add ios/HashiyaKit/Package.swift ios/HashiyaKit/Package.resolved ios/HashiyaKit/Sources/HashiyaData/Backup ios/HashiyaKit/Tests/HashiyaDataTests/BackupFormatTests.swift
git commit -m "feat(ios): backup archive format, decoding the shared fixture"
```

(Include `Package.resolved` only if the package tracks it.)

---

### Task 2: The sweep clears missing PDFs; downloaded PDFs stay out of iCloud backups

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaData/Pdf/PdfFileStore.swift`, `Pdf/GRDBPdfRepository.swift`
- Test: `ios/HashiyaKit/Tests/HashiyaDataTests/GRDBPdfRepositoryTests.swift`, `PdfFileStoreTests.swift`

**Interfaces:**
- Produces: `PdfFileStore.setExcludedFromBackup(_ excluded: Bool, paperID: String)` (public; best effort, never throws); `PdfFileStore.isExcludedFromBackup(paperID:) -> Bool` (internal, for tests). `sweepOrphans()` also clears rows whose file is missing and marks every downloaded PDF excluded.

- [ ] **Step 1: Write the failing tests**

In `GRDBPdfRepositoryTests` (reuse its existing setup: in-memory store, temp `directory`, `files`, `repository`; save papers through its existing helper — read the file first):

```swift
@Test func sweepClearsRowsWhoseFileIsGone() async throws {
    let w1 = try await savedPaperID("W1")      // use the suite's existing helper that saves a paper and returns its local id
    let w2 = try await savedPaperID("W2")
    try await store.setPdf(paperID: w1, source: "downloaded", size: 10, addedAt: 1)
    try await store.setPdf(paperID: w2, source: "attached", size: 10, addedAt: 1)
    try FileManager.default.createDirectory(at: files.directory, withIntermediateDirectories: true)
    try Data("%PDF-1.4".utf8).write(to: files.file(paperID: w2))

    await repository.sweepOrphans()

    #expect(try await store.pdfPaperIDs() == [w2])
    #expect(FileManager.default.fileExists(atPath: files.file(paperID: w2).path))
}

@Test func sweepMarksDownloadedPdfsExcludedFromBackup() async throws {
    let w1 = try await savedPaperID("W1")
    let w2 = try await savedPaperID("W2")
    try FileManager.default.createDirectory(at: files.directory, withIntermediateDirectories: true)
    for id in [w1, w2] { try Data("%PDF-1.4".utf8).write(to: files.file(paperID: id)) }
    try await store.setPdf(paperID: w1, source: "downloaded", size: 8, addedAt: 1)
    try await store.setPdf(paperID: w2, source: "attached", size: 8, addedAt: 1)

    await repository.sweepOrphans()

    #expect(files.isExcludedFromBackup(paperID: w1))
    #expect(!files.isExcludedFromBackup(paperID: w2))
}

@Test func aDownloadedPdfIsExcludedFromBackupWhenStored() async throws {
    // Drive one download through the suite's ScriptedDownloader (copy the pattern of an existing successful-download test),
    // wait until observePdf reports it, then:
    // #expect(files.isExcludedFromBackup(paperID: localID))
}
```

Write the third test fully by copying the suite's existing "download stores the PDF" test and adding the final expectation.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:HashiyaDataTests/GRDBPdfRepositoryTests`
Expected: build fails (`isExcludedFromBackup` missing), then the sweep test fails.

- [ ] **Step 3: Implement**

In `PdfFileStore`:

```swift
/// Marks the paper's file as left out of (or back in) iCloud and device backups. A downloaded PDF can be fetched again,
/// so Apple's storage guidelines keep it out; an attached one can't, so it stays. Best effort: a missing file is fine.
public func setExcludedFromBackup(_ excluded: Bool, paperID: String) {
    var url = file(paperID: paperID)
    var values = URLResourceValues()
    values.isExcludedFromBackup = excluded
    try? url.setResourceValues(values)
}

func isExcludedFromBackup(paperID: String) -> Bool {
    (try? file(paperID: paperID).resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup) ?? false
}
```

In `GRDBPdfRepository`:
- In the download path, right after `store.setPdf(... source: PdfSource.downloaded.rawValue ...)` succeeds, call `files.setExcludedFromBackup(true, paperID: paperID)`. (Attach writes a new file through rename, which never carries the attribute, so attach needs nothing.)
- Replace `sweepOrphans`:

```swift
public func sweepOrphans() async {
    await gate.sweep { [store, files] in
        // A failed read must not count as "no PDFs": that would delete every file.
        guard let stored = try? await store.pdfPaperIDs() else { return }
        // Rows whose file is gone (a device restore, a lost file) go back to "no PDF", so Details offers it again.
        let missing = stored.filter { !FileManager.default.fileExists(atPath: files.file(paperID: $0).path) }
        for paperID in missing { try? await store.clearPdf(paperID: paperID) }
        files.sweep(keeping: stored.subtracting(missing))
        // Downloaded PDFs stored before this version are marked too; setting it again is harmless.
        for paperID in (try? await store.downloadedPdfPaperIDs()) ?? [] {
            files.setExcludedFromBackup(true, paperID: paperID)
        }
    }
}
```

Update the `sweepOrphans` doc comment in `PdfRepository.swift` to say it also clears rows whose file is gone and marks downloaded PDFs excluded from backup.

- [ ] **Step 4: Run the tests**

Run the Step 2 command plus `-only-testing:HashiyaDataTests/PdfFileStoreTests`. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaData/Pdf ios/HashiyaKit/Tests/HashiyaDataTests
git commit -m "feat(ios): sweep clears PDFs whose file is gone; downloaded PDFs stay out of backups"
```

---

### Task 3: Staged PDF writes and a store gate the backup can use

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaData/Pdf/PdfFileStore.swift`, `Pdf/GRDBPdfRepository.swift`
- Test: `PdfFileStoreTests.swift`, `GRDBPdfRepositoryTests.swift`

**Interfaces:**
- Produces:
  - `public enum StageResult: Equatable, Sendable { case staged(URL, size: Int64), notPDF, tooLarge }`
  - `PdfFileStore.stage(prefix: String, copying source: URL, maxBytes: Int64) throws -> StageResult` — like `store(copying:)` but leaves the checked file as `<prefix>-<uuid>.part` (no rename).
  - `PdfFileStore.commit(staged: URL, paperID: String) throws` — rename(2) over `<paperID>.pdf`; throws `PdfWriteError`.
  - `PdfFileStore.usableSpace() -> Int64` (volumeAvailableCapacityForImportantUsage of the folder; `Int64.max` when unknown).
  - `GRDBPdfRepository.withStoreGate<T: Sendable>(_ body: @Sendable () async throws -> T) async throws -> T` (internal) — counts as a store (`beginStore`/`endStore`, so `storesFinished()` waits for it) and runs inside the gate (so the sweep never runs alongside).

- [ ] **Step 1: Write the failing tests**

`PdfFileStoreTests` (reuse its temp directory setup):

```swift
@Test func stageKeepsAPartFileAndCommitMovesItIntoPlace() throws {
    let source = directory.appending(path: "in.pdf")
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    try Data("%PDF-1.4 hello".utf8).write(to: source)
    guard case let .staged(url, size) = try files.stage(prefix: "restore", copying: source, maxBytes: 1024) else {
        Issue.record("not staged"); return
    }
    #expect(url.lastPathComponent.hasSuffix(".part"))
    #expect(size == 14)
    #expect(!FileManager.default.fileExists(atPath: files.file(paperID: "p1").path))

    try files.commit(staged: url, paperID: "p1")

    #expect(!FileManager.default.fileExists(atPath: url.path))
    #expect(try String(contentsOf: files.file(paperID: "p1"), encoding: .utf8) == "%PDF-1.4 hello")
}

@Test func stageRejectsANonPdfAndLeavesNothing() throws {
    let source = directory.appending(path: "page.html")
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    try Data("<html>".utf8).write(to: source)
    #expect(try files.stage(prefix: "restore", copying: source, maxBytes: 1024) == .notPDF)
    #expect(try FileManager.default.contentsOfDirectory(atPath: directory.path) == ["page.html"])
}

@Test func sweepDeletesUncommittedStagedFiles() throws {
    let source = directory.appending(path: "in.pdf")
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    try Data("%PDF-1.4".utf8).write(to: source)
    guard case let .staged(url, _) = try files.stage(prefix: "restore", copying: source, maxBytes: 1024) else { return }
    #expect(FileManager.default.fileExists(atPath: url.path))
    files.sweep(keeping: [])
    #expect(!FileManager.default.fileExists(atPath: url.path))
}
```

`GRDBPdfRepositoryTests`:

```swift
@Test func storesFinishedWaitsForAGateStore() async throws {
    let release = AsyncStream<Void>.makeStream()
    let running = Task { try await repository.withStoreGate { for await _ in release.stream { break } } }
    try await Task.sleep(for: .milliseconds(50))
    let finished = OSAllocatedUnfairLock(initialState: false)
    let waiter = Task { await repository.storesFinished(); finished.withLock { $0 = true } }
    try await Task.sleep(for: .milliseconds(50))
    #expect(!finished.withLock { $0 })
    release.continuation.yield(())
    try await running.value
    await waiter.value
    #expect(finished.withLock { $0 })
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `… -only-testing:HashiyaDataTests/PdfFileStoreTests -only-testing:HashiyaDataTests/GRDBPdfRepositoryTests`
Expected: build fails (`stage`, `StageResult`, `withStoreGate` missing).

- [ ] **Step 3: Implement staging**

In `PdfFileStore.swift` add `StageResult` after `StoreResult`, then:

```swift
/// `store(copying:)` without the final move: the checked PDF stays in a `.part` file in this folder, named after `prefix`.
/// `commit` moves it into place, or the caller deletes it; the next `sweep` removes one that is left behind.
public func stage(prefix: String, copying source: URL, maxBytes: Int64) throws -> StageResult {
    let reader = try FileHandle(forReadingFrom: source)
    defer { try? reader.close() }
    let writer = try PartWriter(directory: directory, paperID: prefix)
    do {
        while let chunk = try reader.read(upToCount: Self.chunkSize), !chunk.isEmpty {
            if let verdict = try writer.append(chunk, maxBytes: maxBytes) {
                writer.discard()
                return verdict == .tooLarge ? .tooLarge : .notPDF
            }
        }
        return try writer.finishStaged()
    } catch {
        writer.discard()
        throw error
    }
}

/// Moves a staged file over `<paperID>.pdf`; rename(2) replaces the target atomically within one folder.
public func commit(staged: URL, paperID: String) throws {
    guard rename(staged.path, file(paperID: paperID).path) == 0 else { throw PdfWriteError() }
}

/// Bytes free for important data where the PDFs are kept; `Int64.max` when the system doesn't say.
public func usableSpace() -> Int64 {
    try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    let values = try? directory.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey])
    return values?.volumeAvailableCapacityForImportantUsage ?? Int64.max
}
```

In `PartWriter` add:

```swift
/// Syncs and closes the file and checks the header, keeping the `.part` file in place.
func finishStaged() throws -> StageResult {
    do {
        try handle.synchronize()
        try handle.close()
    } catch {
        throw PdfWriteError()
    }
    guard PdfFileStore.hasHeader(head) else {
        discard()
        return .notPDF
    }
    return .staged(url, size: total)
}
```

- [ ] **Step 4: Implement the gate entry point**

In `GRDBPdfRepository`:

```swift
/// Runs `body`, which writes PDFs into this repository's folder and records them, as one store: never alongside the
/// startup sweep, and `storesFinished()` waits for it. The backup's restore uses it.
func withStoreGate<T: Sendable>(_ body: @Sendable () async throws -> T) async throws -> T {
    beginStore()
    defer { endStore() }
    return try await storing(body)
}
```

- [ ] **Step 5: Run the tests**

Run the Step 2 command. Expected: PASS (and the rest of `GRDBPdfRepositoryTests` still pass).

- [ ] **Step 6: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaData/Pdf ios/HashiyaKit/Tests/HashiyaDataTests
git commit -m "feat(ios): staged PDF writes and a store gate for restores"
```

---

### Task 4: PaperStore backup — snapshot, matching and the merge transaction

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaDatabase/PaperStore.swift` (`private let writer` → `let writer`)
- Create: `ios/HashiyaKit/Sources/HashiyaDatabase/BackupRows.swift`, `PaperStore+Backup.swift`
- Test: `ios/HashiyaKit/Tests/HashiyaDatabaseTests/PaperStoreBackupTests.swift`

**Interfaces:**
- Produces (public, HashiyaDatabase):

```swift
public struct BackupSnapshot: Sendable { public var papers: [PaperWithAuthors]; public var notes: [PaperNotesRecord]; public var collections: [CollectionRecord]; public var links: [CollectionPaperRecord] }
public struct PdfTotals: Equatable, Sendable { public var count: Int; public var bytes: Int64 }
public struct IncomingPaper: Sendable { public var ref: Int; public var paper: PaperRecord; public var authors: [PaperAuthorRecord]; public var notes: PaperNotesRecord? }
public struct IncomingCollection: Sendable { public var name: String; public var nameKey: String; public var createdAt: Int64; public var refs: [Int] }
public struct MergeOutcome: Equatable, Sendable { public var added: Int; public var matched: Int; public var notesAdded: Int; public var collectionsCreated: Int; public var pdfTargets: [Int: String] }
extension PaperStore {
    public func backupSnapshot() async throws -> BackupSnapshot
    public func paperCount() async throws -> Int
    public func collectionCount() async throws -> Int
    public func pdfTotals() async throws -> PdfTotals
    public func matchFor(openAlexID: String?, doi: String?) async throws -> String?
    public func merge(papers: [IncomingPaper], collections: [IncomingCollection], now: Int64) async throws -> MergeOutcome
}
```

(each struct gets a public memberwise `init`.)

- [ ] **Step 1: Write the failing tests**

`ios/HashiyaKit/Tests/HashiyaDatabaseTests/PaperStoreBackupTests.swift`:

```swift
import Foundation
import GRDB
import HashiyaModel
import Testing
@testable import HashiyaDatabase

struct PaperStoreBackupTests {
    private let queue: DatabaseQueue
    private let store: PaperStore

    init() throws {
        queue = try HashiyaDatabase.openInMemory()
        store = PaperStore(writer: queue)
    }

    private func record(_ id: String, _ openAlexID: String? = nil, doi: String? = nil, title: String? = nil, citeKey: String? = nil) -> PaperRecord {
        PaperRecord(id: id, openAlexID: openAlexID, doi: doi, title: title ?? "Paper \(id)", year: 2020, venue: nil, abstract: nil,
                    citationCount: 0, isOpenAccess: false, oaPDFURL: nil, savedAt: 1, citeKey: citeKey)
    }

    private func notes(_ paperID: String, _ summary: String) -> PaperNotesRecord {
        PaperNotesRecord(paperID: paperID, notes: PaperNotes(summary: summary), updatedAt: 1)
    }

    /// Saves a paper the way the app does.
    private func saved(_ paper: PaperRecord, notes: PaperNotesRecord? = nil) async throws {
        let authors = [PaperAuthorRecord(paperID: paper.id, position: 0, name: "Device Author", openAlexAuthorID: nil)]
        let search = PaperSearchRow.make(paperID: paper.id, title: paper.title, authorNames: ["Device Author"], abstract: nil,
                                         venue: nil, notes: notes?.notes)
        try await store.insert(paper: paper, authors: authors, search: search, notes: notes)
    }

    private func incoming(_ ref: Int, _ paper: PaperRecord, notes: PaperNotesRecord? = nil, authors: [String] = ["A"]) -> IncomingPaper {
        IncomingPaper(ref: ref, paper: paper,
                      authors: authors.enumerated().map { PaperAuthorRecord(paperID: paper.id, position: $0.offset, name: $0.element, openAlexAuthorID: nil) },
                      notes: notes)
    }

    private func searchCount(_ match: String) async throws -> Int {
        try await queue.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM paper_search WHERE paper_search MATCH ?", arguments: [match]) ?? 0 }
    }

    @Test func addsNewPapersWithAuthorsNotesAndSearchRow() async throws {
        let outcome = try await store.merge(papers: [incoming(1, record("n1", "W1"), notes: notes("n1", "Backup summary"), authors: ["Ada", "Grace"])], collections: [], now: 9)
        #expect(outcome.added == 1)
        let paper = try #require(await store.citablePaper(openAlexID: "W1"))
        #expect(paper.authors.sorted { $0.position < $1.position }.map(\.name) == ["Ada", "Grace"])
        #expect(try await store.notes(openAlexID: "W1")?.summary == "Backup summary")
        #expect(try await searchCount("summary*") == 1)
    }

    @Test func matchesByOpenAlexIDAndKeepsTheDevicePaper() async throws {
        var device = record("d1", "W1", title: "Device title")
        device.readingStatus = "read"
        try await saved(device, notes: notes("d1", "Device notes"))
        let outcome = try await store.merge(papers: [incoming(1, record("n1", "W1", title: "Backup title"), notes: notes("n1", "Backup notes"))], collections: [], now: 9)
        #expect(outcome.added == 0 && outcome.matched == 1 && outcome.notesAdded == 0)
        let paper = try #require(await store.citablePaper(openAlexID: "W1"))
        #expect(paper.paper.title == "Device title")
        #expect(paper.paper.readingStatus == "read")
        #expect(try await store.notes(openAlexID: "W1")?.summary == "Device notes")
    }

    @Test func backupNotesFillAMatchedPaperWithoutNotes() async throws {
        try await saved(record("d1", "W1"))
        let outcome = try await store.merge(papers: [incoming(1, record("n1", "W1"), notes: notes("n1", "Backup notes"))], collections: [], now: 9)
        #expect(outcome.notesAdded == 1)
        #expect(try await store.notes(openAlexID: "W1")?.summary == "Backup notes")
        #expect(try await searchCount("backup*") == 1)
    }

    @Test func matchesByDoiOnlyWhenTheBackupPaperHasNoOpenAlexID() async throws {
        try await saved(record("d1", "W1", doi: "10.1/x"))
        #expect(try await store.merge(papers: [incoming(1, record("n1", nil, doi: "10.1/x"))], collections: [], now: 9).matched == 1)
        // Another work with the same DOI (a preprint and its published version) is a separate paper.
        #expect(try await store.merge(papers: [incoming(1, record("n2", "W2", doi: "10.1/x"))], collections: [], now: 9).added == 1)
    }

    @Test func aTakenCiteKeyIsDropped() async throws {
        try await saved(record("d1", "W1", citeKey: "smith2020"))
        _ = try await store.merge(papers: [incoming(1, record("n1", "W2", citeKey: "smith2020"))], collections: [], now: 9)
        #expect(try await store.citablePaper(openAlexID: "W2")?.paper.citeKey == nil)
    }

    @Test func pdfColumnsAreSetOnlyWhenTheMatchedPaperHasNone() async throws {
        try await saved(record("d1", "W1"))
        try await saved(record("d2", "W2"))
        try await store.setPdf(paperID: "d2", source: "attached", size: 5, addedAt: 1)
        func withPdf(_ id: String, _ oa: String) -> PaperRecord {
            var r = record(id, oa)
            r.pdfSource = "downloaded"; r.pdfSize = 7; r.pdfAddedAt = 3; r.pdfLastPage = 2
            return r
        }
        let outcome = try await store.merge(papers: [incoming(1, withPdf("n1", "W1")), incoming(2, withPdf("n2", "W2")), incoming(3, withPdf("n3", "W3"))], collections: [], now: 9)
        #expect(outcome.pdfTargets == [1: "d1", 3: "n3"])
        #expect(try await store.citablePaper(openAlexID: "W1")?.paper.pdfLastPage == 2)
        #expect(try await store.citablePaper(openAlexID: "W2")?.paper.pdfSource == "attached")
    }

    @Test func collectionsMergeByNameKeyAndLinkNewAndMatchedPapers() async throws {
        try await saved(record("d1", "W1"))
        _ = try #require(await store.insertCollection(name: "Thesis", nameKey: "thesis", createdAt: 1))
        let outcome = try await store.merge(
            papers: [incoming(1, record("n1", "W1")), incoming(2, record("n2", "W2"))],
            collections: [IncomingCollection(name: "THESIS", nameKey: "thesis", createdAt: 5, refs: [1, 2]),
                          IncomingCollection(name: "Review", nameKey: "review", createdAt: 6, refs: [2, 99])],
            now: 9)
        #expect(outcome.collectionsCreated == 1)
        var counts: [String: Int] = [:]
        for await list in store.observeCollections() { counts = Dictionary(uniqueKeysWithValues: list.map { ($0.name, $0.paperCount) }); break }
        #expect(counts == ["Thesis": 2, "Review": 1])
    }

    @Test func aFailureRollsBackTheWholeMerge() async throws {
        let broken = IncomingPaper(ref: 2, paper: record("n2", "W2"),
                                   authors: [PaperAuthorRecord(paperID: "no-such-paper", position: 0, name: "X", openAlexAuthorID: nil)], notes: nil)
        await #expect(throws: (any Error).self) {
            try await store.merge(papers: [incoming(1, record("n1", "W1")), broken], collections: [], now: 9)
        }
        #expect(try await store.paperCount() == 0)
    }

    @Test func snapshotReadsEverything() async throws {
        try await saved(record("d1", "W1"), notes: notes("d1", "N"))
        let id = try #require(await store.insertCollection(name: "C", nameKey: "c", createdAt: 1))
        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 2)
        let snapshot = try await store.backupSnapshot()
        #expect(snapshot.papers.map(\.paper.id) == ["d1"])
        #expect(snapshot.notes.map(\.summary) == ["N"])
        #expect(snapshot.collections.map(\.name) == ["C"])
        #expect(snapshot.links.map { "\($0.collectionID)-\($0.paperID)" } == ["\(id)-d1"])
        #expect(try await store.pdfTotals() == PdfTotals(count: 0, bytes: 0))
    }
}
```

Adapt names to the real records if they differ (e.g. `PaperRecord` field names such as `pdfSource`, `CollectionWithCount.paperCount`) — read Records.swift first.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:HashiyaDatabaseTests/PaperStoreBackupTests`
Expected: build fails (`merge`, `IncomingPaper` missing).

- [ ] **Step 3: Write the rows**

`ios/HashiyaKit/Sources/HashiyaDatabase/BackupRows.swift` — the five public structs from the Interfaces block, each with a public memberwise init and a one-line doc comment (mirror Android's `BackupRows.kt` comments).

- [ ] **Step 4: Write the store extension**

Change `private let writer` to `let writer` in `PaperStore.swift`. Then `ios/HashiyaKit/Sources/HashiyaDatabase/PaperStore+Backup.swift`:

```swift
import Foundation
import GRDB

/// Reads the library for an export and merges a backup into it. The device wins every conflict.
extension PaperStore {
    /// Everything an export writes, read in one transaction so it is consistent.
    public func backupSnapshot() async throws -> BackupSnapshot {
        try await writer.read { db in
            let papers = try PaperRecord.order(Column("saved_at"), Column("rowid")).fetchAll(db)
            let authors = Dictionary(grouping: try PaperAuthorRecord.fetchAll(db), by: \.paperID)
            return BackupSnapshot(
                papers: papers.map { PaperWithAuthors(paper: $0, authors: (authors[$0.id] ?? []).sorted { $0.position < $1.position }) },
                notes: try PaperNotesRecord.fetchAll(db),
                collections: try CollectionRecord.order(Column("name_key")).fetchAll(db),
                links: try CollectionPaperRecord.order(Column("added_at")).fetchAll(db)
            )
        }
    }

    public func paperCount() async throws -> Int {
        try await writer.read { db in try PaperRecord.fetchCount(db) }
    }

    public func collectionCount() async throws -> Int {
        try await writer.read { db in try CollectionRecord.fetchCount(db) }
    }

    public func pdfTotals() async throws -> PdfTotals {
        try await writer.read { db in
            let row = try Row.fetchOne(db, sql: "SELECT COUNT(*) AS count, COALESCE(SUM(pdf_size), 0) AS bytes FROM papers WHERE pdf_source IS NOT NULL")
            return PdfTotals(count: row?["count"] ?? 0, bytes: row?["bytes"] ?? 0)
        }
    }

    /// The local id of the saved paper a backup paper matches: by `openAlexID` when it has one, otherwise by `doi` (already
    /// normalized). DOIs aren't unique here (a preprint and its published version can both be saved), so a paper with an
    /// OpenAlex id never matches by DOI.
    public func matchFor(openAlexID: String?, doi: String?) async throws -> String? {
        try await writer.read { db in try Self.match(db, openAlexID: openAlexID, doi: doi) }
    }

    /// Adds what the library lacks and keeps everything it has, in one transaction (spec §5 "Merge rules").
    public func merge(papers: [IncomingPaper], collections: [IncomingCollection], now: Int64) async throws -> MergeOutcome {
        try await writer.write { db in
            var localIDs: [Int: String] = [:]
            var outcome = MergeOutcome(added: 0, matched: 0, notesAdded: 0, collectionsCreated: 0, pdfTargets: [:])
            for incoming in papers {
                var paper = incoming.paper
                if let existing = try Self.match(db, openAlexID: paper.openAlexID, doi: paper.doi) {
                    outcome.matched += 1
                    localIDs[incoming.ref] = existing
                    let hasNotes = try Bool.fetchOne(db, sql: "SELECT EXISTS(SELECT 1 FROM paper_notes WHERE paper_id = ?)", arguments: [existing]) ?? false
                    if let notes = incoming.notes, !hasNotes {
                        var row = notes
                        row.paperID = existing
                        try row.insert(db)
                        try db.execute(sql: "UPDATE paper_search SET notes = ? WHERE paper_id = ?",
                                       arguments: [PaperSearchRow.notesText(notes.notes), existing])
                        outcome.notesAdded += 1
                    }
                    if let source = paper.pdfSource {
                        try db.execute(sql: """
                            UPDATE papers SET pdf_source = ?, pdf_size = ?, pdf_added_at = ?, pdf_last_page = ?
                            WHERE id = ? AND pdf_source IS NULL
                            """, arguments: [source, paper.pdfSize ?? 0, paper.pdfAddedAt ?? now, paper.pdfLastPage ?? 0, existing])
                        if db.changesCount > 0 { outcome.pdfTargets[incoming.ref] = existing }
                    }
                } else {
                    if let key = paper.citeKey,
                       try Bool.fetchOne(db, sql: "SELECT EXISTS(SELECT 1 FROM papers WHERE cite_key = ?)", arguments: [key]) == true {
                        paper.citeKey = nil
                    }
                    try paper.insert(db)
                    for author in incoming.authors { try author.insert(db) }
                    try PaperSearchRow.make(
                        paperID: paper.id,
                        title: paper.title,
                        authorNames: incoming.authors.sorted { $0.position < $1.position }.map(\.name),
                        abstract: paper.abstract,
                        venue: paper.venue,
                        notes: incoming.notes?.notes
                    ).insert(db)
                    try incoming.notes?.insert(db)
                    localIDs[incoming.ref] = paper.id
                    if paper.pdfSource != nil { outcome.pdfTargets[incoming.ref] = paper.id }
                    outcome.added += 1
                }
            }
            for collection in collections {
                var id = try Int64.fetchOne(db, sql: "SELECT id FROM collections WHERE name_key = ?", arguments: [collection.nameKey])
                if id == nil {
                    var record = CollectionRecord(id: nil, name: collection.name, nameKey: collection.nameKey, createdAt: collection.createdAt)
                    try record.insert(db)
                    id = record.id
                    outcome.collectionsCreated += 1
                }
                guard let collectionID = id else { continue }
                var seen = Set<String>()
                for paperID in collection.refs.compactMap({ localIDs[$0] }) where seen.insert(paperID).inserted {
                    try db.execute(sql: "INSERT OR IGNORE INTO collection_papers (collection_id, paper_id, added_at) VALUES (?, ?, ?)",
                                   arguments: [collectionID, paperID, now])
                }
            }
            return outcome
        }
    }

    private static func match(_ db: Database, openAlexID: String?, doi: String?) throws -> String? {
        if let openAlexID {
            return try String.fetchOne(db, sql: "SELECT id FROM papers WHERE open_alex_id = ?", arguments: [openAlexID])
        }
        if let doi {
            return try String.fetchOne(db, sql: "SELECT id FROM papers WHERE doi = ? ORDER BY saved_at LIMIT 1", arguments: [doi])
        }
        return nil
    }
}
```

Adjust to the real APIs: `PaperNotesRecord.paperID` must be `var` (or rebuild the record), `CollectionRecord` init/`insert` (it is `MutablePersistableRecord`, so `didInsert` sets `id`), and `PaperSearchRow.insert(_:)` is internal to this module (fine here). `writer.write` on a `DatabaseQueue`/`DatabasePool` runs in one transaction and rolls back on throw.

- [ ] **Step 5: Run the tests**

Run the Step 2 command, then the whole `HashiyaDatabaseTests` target. Expected: PASS; schema unchanged (no new migration).

- [ ] **Step 6: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaDatabase ios/HashiyaKit/Tests/HashiyaDatabaseTests/PaperStoreBackupTests.swift
git commit -m "feat(ios): PaperStore reads a backup snapshot and merges a backup"
```

---

### Task 5: Writing the archive and the export operation

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaData/Backup/BackupArchive.swift`, `LibraryBackup.swift`, `ArchiveLibraryBackup.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaData/LibraryRepositories.swift`, `LiveDependencies.swift`, `ios/Hashiya/UITestingStubs.swift`
- Test: `ios/HashiyaKit/Tests/HashiyaDataTests/ArchiveLibraryBackupTests.swift`

**Interfaces:**
- Consumes: Task 1 format; Task 4 `backupSnapshot/paperCount/collectionCount/pdfTotals`; `PdfFileStore.file(paperID:)`; Task 3 `withStoreGate`.
- Produces (public, HashiyaData — final shape; Task 5 declares only the export members, Task 6 adds open/discard(PreparedBackup), Task 7 adds apply):

```swift
public protocol LibraryBackup: Sendable {
    func summary() async throws -> BackupSummary
    /// Builds the archive in a temporary file. Throws `BackupError`.
    func export(includePdfs: Bool, onProgress: @escaping @Sendable (Double) -> Void) async throws -> ExportedFile
    func discard(_ exported: ExportedFile)
    func open(_ source: URL) async -> OpenResult                                   // Task 6
    func apply(_ backup: PreparedBackup, onProgress: @escaping @Sendable (Double) -> Void) async throws -> RestoreResult  // Task 7
    func discard(_ backup: PreparedBackup)                                         // Task 6
}
public struct BackupSummary: Equatable, Sendable { public var papers, collections, pdfCount: Int; public var pdfBytes: Int64 }
public struct ExportedFile: Equatable, Sendable { public let url: URL; public let fileName: String; public let missingPdfs: Int }
public enum BackupError: Error, Equatable, Sendable { case noSpace, writeFailed, unreadable, busy }
```

  `ArchiveLibraryBackup(store: PaperStore, pdfs: GRDBPdfRepository, files: PdfFileStore, workDirectory: URL, appVersion: String, background: any BackgroundTimeGranting, now: @escaping @Sendable () -> Int64, newID: @escaping @Sendable () -> String)` (public `final class`, `Sendable` via immutable lets + a lock for mutable state). `LibraryRepositories.backup: ArchiveLibraryBackup`; `LiveDependencies.backup: any LibraryBackup` (new init parameter, last).
  The export's `url` lives in the work directory; the UI moves it with `.fileMover` (Task 8), so `discard` deletes it if it is still there.

- [ ] **Step 1: Write the failing tests**

`ios/HashiyaKit/Tests/HashiyaDataTests/ArchiveLibraryBackupTests.swift`:

```swift
import Foundation
import GRDB
import HashiyaDatabase
import HashiyaModel
import os
import Testing
import ZIPFoundation
@testable import HashiyaData

struct ArchiveLibraryBackupTests {
    let queue: DatabaseQueue
    let store: PaperStore
    let root: URL
    let files: PdfFileStore
    let library: GRDBLibraryRepository
    let backup: ArchiveLibraryBackup

    init() throws {
        queue = try HashiyaDatabase.openInMemory()
        store = PaperStore(writer: queue)
        root = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        files = PdfFileStore(directory: root.appending(path: "pdfs", directoryHint: .isDirectory))
        let ids = OSAllocatedUnfairLock(initialState: 0)
        library = GRDBLibraryRepository(store: store, now: { 1_000 }, newID: { ids.withLock { $0 += 1; return "local-\($0)" } })
        backup = Self.backup(store: store, files: files, work: root.appending(path: "work", directoryHint: .isDirectory))
    }

    static func backup(store: PaperStore, files: PdfFileStore, work: URL) -> ArchiveLibraryBackup {
        let ids = OSAllocatedUnfairLock(initialState: 0)
        return ArchiveLibraryBackup(
            store: store,
            pdfs: GRDBPdfRepository(store: store, files: files, downloader: ScriptedDownloader()),
            files: files,
            workDirectory: work,
            appVersion: "0.3.0 (iOS)",
            background: NoBackgroundTime(),
            now: { 1_790_000_000_000 },
            newID: { ids.withLock { $0 += 1; return "restored-\($0)" } }
        )
    }

    func paper(_ id: String, title: String? = nil) -> Paper {
        Paper(openAlexID: id, doi: "10.1/\(id)", title: title ?? "Paper \(id)",
              authors: [Author(name: "Jane Doe", openAlexID: "A1"), Author(name: "Omar", openAlexID: nil)],
              year: 2020, venue: "Nature", abstract: "Abstract", citationCount: 3, isOpenAccess: true, openAccessPDFURL: "https://x/\(id).pdf")
    }

    func storePdf(_ localID: String, _ text: String? = nil) async throws {
        let text = text ?? "%PDF-1.4 \(localID)"
        try FileManager.default.createDirectory(at: files.directory, withIntermediateDirectories: true)
        try Data(text.utf8).write(to: files.file(paperID: localID))
        try await store.setPdf(paperID: localID, source: "downloaded", size: Int64(text.utf8.count), addedAt: 5)
    }

    func text(_ archive: Archive, _ name: String) throws -> String {
        var data = Data()
        _ = try archive.extract(try #require(archive[name]), consumer: { data.append($0) })
        return String(decoding: data, as: UTF8.self)
    }

    @Test func summaryCountsPapersCollectionsAndPdfs() async throws {
        try await library.save(paper("W1"))
        try await library.save(paper("W2"))
        try await storePdf("local-1")
        _ = try await store.insertCollection(name: "C", nameKey: "c", createdAt: 1)
        #expect(try await backup.summary() == BackupSummary(papers: 2, collections: 1, pdfCount: 1, pdfBytes: 16))
    }

    @Test func exportWithoutPdfsWritesTheLibraryAndNoPdfEntries() async throws {
        try await library.save(paper("W1"))
        try await library.saveNotes(openAlexID: "W1", notes: PaperNotes(summary: "My summary"))
        try await storePdf("local-1")
        let collection = try #require(await store.insertCollection(name: "Thesis", nameKey: "thesis", createdAt: 7))
        try await store.addToCollection(collectionID: collection, openAlexID: "W1", addedAt: 8)

        let exported = try await backup.export(includePdfs: false, onProgress: { _ in })

        #expect(exported.fileName == "Hashiya-library-2026-09-21.hashiya")
        #expect(exported.url.lastPathComponent == exported.fileName)
        #expect(exported.missingPdfs == 0)
        let archive = try Archive(url: exported.url, accessMode: .read)
        #expect(archive[BackupFormat.pdfEntry(ref: 1)] == nil)
        let manifest = try BackupFormat.decoder.decode(BackupManifest.self, from: Data(text(archive, BackupFormat.manifestEntry).utf8))
        #expect(manifest == BackupManifest(format: 1, app: "0.3.0 (iOS)", exportedAt: "2026-09-21T14:13:20Z", papers: 1, collections: 1, includesPdfs: false))
        let written = try BackupFormat.decoder.decode(BackupLibrary.self, from: Data(text(archive, BackupFormat.libraryEntry).utf8))
        let first = try #require(written.papers.first)
        #expect(first.ref == 1 && first.openAlexId == "W1")
        #expect(first.authors == [BackupAuthor(name: "Jane Doe", openAlexAuthorId: "A1"), BackupAuthor(name: "Omar", openAlexAuthorId: nil)])
        #expect(first.notes?.summary == "My summary")
        #expect(first.pdf == BackupPdf(source: "downloaded", addedAt: 5, lastPage: 0, file: nil))
        #expect(written.collections == [BackupCollection(name: "Thesis", createdAt: 7, papers: [1])])
    }

    @Test func exportedJsonUsesTheSharedFieldNames() async throws {
        try await library.save(paper("W1"))
        let exported = try await backup.export(includePdfs: false, onProgress: { _ in })
        let json = try text(try Archive(url: exported.url, accessMode: .read), BackupFormat.libraryEntry)
        for key in ["\"openAlexId\"", "\"citationCount\"", "\"isOpenAccess\"", "\"oaPdfUrl\"", "\"savedAt\"", "\"readingStatus\"", "\"openAlexAuthorId\"", "\"ref\""] {
            #expect(json.contains(key), "missing \(key)")
        }
    }

    @Test func exportWithPdfsIncludesThemAndCountsMissingOnes() async throws {
        try await library.save(paper("W1"))
        try await library.save(paper("W2"))
        try await storePdf("local-1")
        try await storePdf("local-2")
        try FileManager.default.removeItem(at: files.file(paperID: "local-2"))

        let exported = try await backup.export(includePdfs: true, onProgress: { _ in })

        #expect(exported.missingPdfs == 1)
        let archive = try Archive(url: exported.url, accessMode: .read)
        #expect(try text(archive, "pdfs/1.pdf") == "%PDF-1.4 local-1")
        let papers = try BackupFormat.decoder.decode(BackupLibrary.self, from: Data(text(archive, BackupFormat.libraryEntry).utf8)).papers
        #expect(papers[0].pdf?.file == "pdfs/1.pdf")
        #expect(papers[1].pdf?.file == nil)
    }

    @Test func cancellingAnExportLeavesNoTempFile() async throws {
        try await library.save(paper("W1"))
        try await library.save(paper("W2"))
        try await storePdf("local-1")
        try await storePdf("local-2")
        let task = Task { try await backup.export(includePdfs: true, onProgress: { _ in withUnsafeCurrentTask { $0?.cancel() } }) }
        await #expect(throws: CancellationError.self) { try await task.value }
        let work = root.appending(path: "work")
        #expect(((try? FileManager.default.contentsOfDirectory(atPath: work.path)) ?? []).isEmpty)
    }

    @Test func discardDeletesTheTempFile() async throws {
        try await library.save(paper("W1"))
        let exported = try await backup.export(includePdfs: false, onProgress: { _ in })
        backup.discard(exported)
        #expect(!FileManager.default.fileExists(atPath: exported.url.path))
    }
}
```

Fix up construction details against real initializers (`Paper`, `GRDBPdfRepository`, `ScriptedDownloader` lives in the test target — reuse or use any stub `PdfDownloading`). `1_790_000_000_000` ms is `2026-09-21T14:13:20Z`.

- [ ] **Step 2: Run them to verify they fail**

Run: `… -only-testing:HashiyaDataTests/ArchiveLibraryBackupTests`
Expected: build fails (`ArchiveLibraryBackup` missing).

- [ ] **Step 3: Write the archive writer**

`ios/HashiyaKit/Sources/HashiyaData/Backup/BackupArchive.swift`:

```swift
import Foundation
import HashiyaDatabase
import ZIPFoundation

struct WrittenArchive: Equatable, Sendable {
    var papers: Int
    var collections: Int
    var missingPdfs: Int
}

/// Writes `snapshot` as a `.hashiya` archive at `url`. With `includePdfs`, each stored PDF that `pdfFile` finds is copied in
/// (stored, not compressed: PDFs barely compress); one that is missing is written with `"file": null` and counted. PDFs go
/// first and the JSON last, so `library.json` only names entries that were really written. Checks cancellation per PDF.
func writeArchive(
    _ snapshot: BackupSnapshot,
    includePdfs: Bool,
    pdfFile: (String) -> URL,
    manifest: (_ papers: Int, _ collections: Int) -> BackupManifest,
    to url: URL,
    onProgress: (Double) -> Void
) throws -> WrittenArchive {
    let archive = try Archive(url: url, accessMode: .create)
    var refs: [String: Int] = [:]
    for (index, row) in snapshot.papers.enumerated() { refs[row.paper.id] = index + 1 }
    let notesByPaper = Dictionary(snapshot.notes.map { ($0.paperID, $0) }, uniquingKeysWith: { first, _ in first })
    let withPdf = snapshot.papers.filter { $0.paper.pdfSource != nil }
    var included = Set<String>()
    var missing = 0
    if includePdfs {
        for (index, row) in withPdf.enumerated() {
            try Task.checkCancellation()
            let source = pdfFile(row.paper.id)
            if FileManager.default.fileExists(atPath: source.path), let ref = refs[row.paper.id] {
                try archive.addEntry(with: BackupFormat.pdfEntry(ref: ref), fileURL: source, compressionMethod: .none)
                included.insert(row.paper.id)
            } else {
                missing += 1
            }
            onProgress(Double(index + 1) / Double(withPdf.count + 1))
        }
    }
    let linksByCollection = Dictionary(grouping: snapshot.links, by: \.collectionID)
    let papers = snapshot.papers.map { row -> BackupPaper in
        let paper = row.paper
        let ref = refs[paper.id] ?? 0
        let notes = notesByPaper[paper.id]
        return BackupPaper(
            ref: ref, openAlexId: paper.openAlexID, doi: paper.doi, title: paper.title, year: paper.year, venue: paper.venue,
            abstract: paper.abstract, citationCount: paper.citationCount, isOpenAccess: paper.isOpenAccess, oaPdfUrl: paper.oaPDFURL,
            savedAt: paper.savedAt, readingStatus: paper.readingStatus, workType: paper.workType, sourceType: paper.sourceType,
            publisher: paper.publisher, volume: paper.volume, issue: paper.issue, firstPage: paper.firstPage, lastPage: paper.lastPage,
            citeKey: paper.citeKey, detailsFetched: paper.detailsFetched,
            authors: row.authors.sorted { $0.position < $1.position }.map { BackupAuthor(name: $0.name, openAlexAuthorId: $0.openAlexAuthorID) },
            notes: notes.map { BackupNotes(summary: $0.summary, researchQuestion: $0.researchQuestion, method: $0.method,
                                           keyFindings: $0.keyFindings, limitations: $0.limitations, thoughts: $0.thoughts, updatedAt: $0.updatedAt) },
            pdf: paper.pdfSource.map { source in
                BackupPdf(source: source == "downloaded" ? "downloaded" : "attached", addedAt: paper.pdfAddedAt ?? 0,
                          lastPage: paper.pdfLastPage ?? 0, file: included.contains(paper.id) ? BackupFormat.pdfEntry(ref: ref) : nil)
            }
        )
    }
    let collections = snapshot.collections.map { collection in
        BackupCollection(name: collection.name, createdAt: collection.createdAt,
                         papers: (linksByCollection[collection.id ?? -1] ?? []).compactMap { refs[$0.paperID] })
    }
    try add(archive, BackupFormat.libraryEntry, try BackupFormat.encoder.encode(BackupLibrary(papers: papers, collections: collections)))
    try add(archive, BackupFormat.manifestEntry, try BackupFormat.encoder.encode(manifest(papers.count, collections.count)))
    onProgress(1)
    return WrittenArchive(papers: papers.count, collections: collections.count, missingPdfs: missing)
}

private func add(_ archive: Archive, _ path: String, _ data: Data) throws {
    try archive.addEntry(with: path, type: .file, uncompressedSize: Int64(data.count), compressionMethod: .deflate) { position, size in
        data.subdata(in: Int(position)..<(Int(position) + size))
    }
}
```

Check ZIPFoundation's exact signatures for the resolved version (`addEntry(with:fileURL:compressionMethod:)` needs the file's *name* path in some versions — use the variant that takes an explicit entry path; if only `addEntry(with: relativePath, relativeTo: baseURL)` exists, read the file through the provider overload instead).

- [ ] **Step 4: Write the protocol and the export half of the implementation**

`ios/HashiyaKit/Sources/HashiyaData/Backup/LibraryBackup.swift`: the protocol (export members only for now), `BackupSummary`, `ExportedFile`, `BackupError` from the Interfaces block, with doc comments.

`ios/HashiyaKit/Sources/HashiyaData/Backup/ArchiveLibraryBackup.swift`:

```swift
import Foundation
import HashiyaDatabase
import os

/// Exports the library to a `.hashiya` archive and merges one back in (spec §4–§5).
public final class ArchiveLibraryBackup: LibraryBackup {
    private let store: PaperStore
    private let pdfs: GRDBPdfRepository
    private let files: PdfFileStore
    /// A private folder for archives being built or read; cleared of leftovers the first time it is used in a process.
    private let workDirectory: URL
    private let appVersion: String
    private let background: any BackgroundTimeGranting
    private let now: @Sendable () -> Int64
    private let newID: @Sendable () -> String
    private let workDirectoryReady = OSAllocatedUnfairLock(initialState: false)

    public init(store: PaperStore, pdfs: GRDBPdfRepository, files: PdfFileStore, workDirectory: URL, appVersion: String,
                background: any BackgroundTimeGranting, now: @escaping @Sendable () -> Int64, newID: @escaping @Sendable () -> String) {
        self.store = store
        self.pdfs = pdfs
        self.files = files
        self.workDirectory = workDirectory
        self.appVersion = appVersion
        self.background = background
        self.now = now
        self.newID = newID
    }

    public func summary() async throws -> BackupSummary {
        let pdf = try await store.pdfTotals()
        return BackupSummary(papers: try await store.paperCount(), collections: try await store.collectionCount(),
                             pdfCount: pdf.count, pdfBytes: pdf.bytes)
    }

    public func export(includePdfs: Bool, onProgress: @escaping @Sendable (Double) -> Void) async throws -> ExportedFile {
        let token = await background.begin(name: "Export library", onExpiry: {})
        defer { token.end() }
        let snapshot: BackupSnapshot
        do { snapshot = try await store.backupSnapshot() } catch { throw BackupError.writeFailed }
        try prepareWorkDirectory()
        let time = now()
        // The save panel names the saved file after this one, so it carries the final name inside its own folder.
        let folder = workDirectory.appending(path: "export-\(newID())", directoryHint: .isDirectory)
        let url = folder.appending(path: BackupFormat.fileName(at: time), directoryHint: .notDirectory)
        var done = false
        defer { if !done { try? FileManager.default.removeItem(at: folder) } }
        do { try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true) } catch { throw BackupError.writeFailed }
        do {
            let written = try writeArchive(
                snapshot,
                includePdfs: includePdfs,
                pdfFile: files.file(paperID:),
                manifest: { [appVersion] papers, collections in
                    BackupManifest(format: BackupFormat.version, app: appVersion, exportedAt: BackupFormat.isoUTC(time),
                                   papers: papers, collections: collections, includesPdfs: includePdfs)
                },
                to: url,
                onProgress: onProgress
            )
            try Task.checkCancellation()
            done = true
            return ExportedFile(url: url, fileName: BackupFormat.fileName(at: time), missingPdfs: written.missingPdfs)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw files.usableSpace() < Self.minFreeBytes ? BackupError.noSpace : BackupError.writeFailed
        }
    }

    /// Deletes the export's folder; the file itself is gone already when `.fileMover` moved it.
    public func discard(_ exported: ExportedFile) {
        try? FileManager.default.removeItem(at: exported.url.deletingLastPathComponent())
    }

    /// Creates the work folder; the first time in a process, deletes what an earlier process left (a crash, a kill). No
    /// file of this process exists before then, and the app has one instance.
    func prepareWorkDirectory() throws {
        let first = workDirectoryReady.withLock { ready -> Bool in
            defer { ready = true }
            return !ready
        }
        if first { try? FileManager.default.removeItem(at: workDirectory) }
        do { try FileManager.default.createDirectory(at: workDirectory, withIntermediateDirectories: true) } catch { throw BackupError.writeFailed }
    }

    static let minFreeBytes: Int64 = 10 * 1024 * 1024
}
```

The background token type is whatever `BackgroundTimeGranting.begin` returns (`any BackgroundTimeToken`); adapt names.

- [ ] **Step 5: Wire it**

- `LibraryRepositories`: add `public let backup: ArchiveLibraryBackup`, built in `init` after `pdfs`:

```swift
backup = ArchiveLibraryBackup(
    store: store,
    pdfs: pdfs,
    files: pdf.files,
    workDirectory: FileManager.default.temporaryDirectory.appending(path: "backup", directoryHint: .isDirectory),
    appVersion: "\(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?") (iOS)",
    background: pdf.background,
    now: { Int64((Date().timeIntervalSince1970 * 1000).rounded()) },
    newID: { UUID().uuidString.lowercased() }
)
```

- `LiveDependencies`: add `public let backup: any LibraryBackup` as the last stored property and init parameter; `live(...)` passes `repositories.backup`.
- `ios/Hashiya/UITestingStubs.swift`: pass `backup: repositories.backup` (read the file; it builds `LibraryRepositories.shared(... fresh: true ...)`).

- [ ] **Step 6: Run the tests**

Run: `… -only-testing:HashiyaDataTests` (whole target). Expected: PASS. Then `xcodegen generate --spec ios/project.yml && xcodebuild build -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 17 Pro'` — the app builds.

- [ ] **Step 7: Commit**

```bash
git add ios/HashiyaKit ios/Hashiya/UITestingStubs.swift
git commit -m "feat(ios): export the library to a .hashiya archive"
```

---

### Task 6: Opening and validating a backup, with a preview

**Files:**
- Modify: `BackupArchive.swift` (reader), `LibraryBackup.swift`, `ArchiveLibraryBackup.swift`
- Test: `ios/HashiyaKit/Tests/HashiyaDataTests/BackupArchiveTests.swift`, additions to `ArchiveLibraryBackupTests.swift`

**Interfaces:**
- Produces:

```swift
// LibraryBackup additions
func open(_ source: URL) async -> OpenResult        // copies `source` into the work folder (security-scoped access handled here) and checks it
func discard(_ backup: PreparedBackup)

public enum OpenResult: Equatable, Sendable { case ready(PreparedBackup, RestorePreview), failed(OpenFailure) }
public enum OpenFailure: Equatable, Sendable { case notABackup, newerFormat, damaged, unreadable }
/// `exportedAt` is nil when the manifest's date doesn't parse. `papersSkipped`: papers without an OpenAlex id.
public struct RestorePreview: Equatable, Sendable { public var exportedAt: Int64?; public var papers, collections, pdfs, newPapers, existingPapers, papersSkipped: Int }
public struct PreparedBackup: Equatable, Sendable { let url: URL; let library: BackupLibrary }   // init internal

// BackupArchive.swift (internal)
enum ArchiveRead: Equatable { case valid(BackupManifest, BackupLibrary), invalid(OpenFailure) }
func readArchive(_ url: URL) -> ArchiveRead
extension BackupPaper { var usableOpenAlexID: String? }   // trimmed, nil when missing or blank
```

- [ ] **Step 1: Write the failing reader tests**

`ios/HashiyaKit/Tests/HashiyaDataTests/BackupArchiveTests.swift`:

```swift
import Foundation
import Testing
import ZIPFoundation
@testable import HashiyaData

struct BackupArchiveTests {
    private let manifest = #"{"format":1,"exportedAt":"2026-10-04T14:05:00Z"}"#
    private let library = #"{"papers":[{"ref":1,"title":"T","savedAt":1}],"collections":[{"name":"C","createdAt":1,"papers":[1]}]}"#

    private func zip(_ entries: [(String, Data)]) throws -> URL {
        let url = FileManager.default.temporaryDirectory.appending(path: "\(UUID().uuidString).hashiya")
        let archive = try Archive(url: url, accessMode: .create)
        for (name, data) in entries {
            try archive.addEntry(with: name, type: .file, uncompressedSize: Int64(data.count), compressionMethod: .deflate) { position, size in
                data.subdata(in: Int(position)..<(Int(position) + size))
            }
        }
        return url
    }

    private func zipText(_ entries: [(String, String)]) throws -> URL { try zip(entries.map { ($0.0, Data($0.1.utf8)) }) }

    private func failure(_ url: URL) -> OpenFailure? {
        if case let .invalid(reason) = readArchive(url) { return reason }
        return nil
    }

    @Test func readsTheSharedFixture() {
        guard case let .valid(_, library) = readArchive(sharedFixtureURL) else { Issue.record("not valid"); return }
        #expect(library.papers.count == 3)
    }

    @Test func readsAValidArchive() throws {
        guard case .valid = readArchive(try zipText([(BackupFormat.manifestEntry, manifest), (BackupFormat.libraryEntry, library)])) else {
            Issue.record("not valid"); return
        }
    }

    @Test func notAZip() throws {
        let url = FileManager.default.temporaryDirectory.appending(path: "\(UUID().uuidString).txt")
        try Data("hello".utf8).write(to: url)
        #expect(failure(url) == .notABackup)
    }

    @Test func zipWithoutManifest() throws { #expect(failure(try zipText([("other.txt", "x")])) == .notABackup) }
    @Test func manifestThatIsNotJson() throws { #expect(failure(try zipText([(BackupFormat.manifestEntry, "<xml/>"), (BackupFormat.libraryEntry, library)])) == .notABackup) }
    @Test func newerFormat() throws { #expect(failure(try zipText([(BackupFormat.manifestEntry, #"{"format":2}"#), (BackupFormat.libraryEntry, library)])) == .newerFormat) }
    @Test func formatZero() throws { #expect(failure(try zipText([(BackupFormat.manifestEntry, #"{"format":0}"#), (BackupFormat.libraryEntry, library)])) == .damaged) }
    @Test func missingLibrary() throws { #expect(failure(try zipText([(BackupFormat.manifestEntry, manifest)])) == .damaged) }
    @Test func malformedLibrary() throws { #expect(failure(try zipText([(BackupFormat.manifestEntry, manifest), (BackupFormat.libraryEntry, #"{"papers":[{"ref":1}]}"#)])) == .damaged) }
    @Test func duplicateRefs() throws {
        #expect(failure(try zipText([(BackupFormat.manifestEntry, manifest), (BackupFormat.libraryEntry, #"{"papers":[{"ref":1,"title":"A","savedAt":1},{"ref":1,"title":"B","savedAt":1}]}"#)])) == .damaged)
    }
    @Test func collectionPointingAtAnUnknownRef() throws {
        #expect(failure(try zipText([(BackupFormat.manifestEntry, manifest), (BackupFormat.libraryEntry, #"{"papers":[],"collections":[{"name":"C","createdAt":1,"papers":[5]}]}"#)])) == .damaged)
    }
    @Test func oversizedLibrary() throws {
        let huge = Data(repeating: UInt8(ascii: " "), count: BackupFormat.maxLibraryBytes + 1)
        #expect(failure(try zip([(BackupFormat.manifestEntry, Data(manifest.utf8)), (BackupFormat.libraryEntry, huge)])) == .damaged)
    }
    @Test func oversizedManifest() throws {
        let huge = Data(repeating: UInt8(ascii: " "), count: BackupFormat.maxManifestBytes + 1)
        #expect(failure(try zip([(BackupFormat.manifestEntry, huge), (BackupFormat.libraryEntry, Data(library.utf8))])) == .notABackup)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `… -only-testing:HashiyaDataTests/BackupArchiveTests`
Expected: build fails (`readArchive` missing).

- [ ] **Step 3: Write the reader**

Append to `BackupArchive.swift`:

```swift
enum ArchiveRead: Equatable {
    case valid(BackupManifest, BackupLibrary)
    case invalid(OpenFailure)
}

/// Reads and checks the manifest and library of the archive at `url`. Only those two entries are read; PDFs are read by
/// exact name at restore. Never throws: anything unreadable is a typed failure.
func readArchive(_ url: URL) -> ArchiveRead {
    guard let archive = try? Archive(url: url, accessMode: .read) else { return .invalid(.notABackup) }
    guard let manifestEntry = archive[BackupFormat.manifestEntry],
          let manifestData = readEntry(archive, manifestEntry, max: BackupFormat.maxManifestBytes),
          let manifest = try? BackupFormat.decoder.decode(BackupManifest.self, from: manifestData)
    else { return .invalid(.notABackup) }
    if manifest.format > BackupFormat.version { return .invalid(.newerFormat) }
    if manifest.format < 1 { return .invalid(.damaged) }
    guard let libraryEntry = archive[BackupFormat.libraryEntry],
          let libraryData = readEntry(archive, libraryEntry, max: BackupFormat.maxLibraryBytes),
          let library = try? BackupFormat.decoder.decode(BackupLibrary.self, from: libraryData)
    else { return .invalid(.damaged) }
    let refs = library.papers.map(\.ref)
    guard Set(refs).count == refs.count else { return .invalid(.damaged) }
    let known = Set(refs)
    guard library.collections.allSatisfy({ $0.papers.allSatisfy(known.contains) }) else { return .invalid(.damaged) }
    return .valid(manifest, library)
}

/// The entry's bytes, or nil when more than `max` come out or it can't be read. Never trusts the declared size.
private func readEntry(_ archive: Archive, _ entry: Entry, max: Int) -> Data? {
    struct TooLarge: Error {}
    var data = Data()
    do {
        _ = try archive.extract(entry, skipCRC32: false) { chunk in
            data.append(chunk)
            if data.count > max { throw TooLarge() }
        }
        return data
    } catch {
        return nil
    }
}

extension BackupPaper {
    /// The OpenAlex id trimmed; nil when missing or blank. Papers without one are skipped on restore: the app keys every
    /// paper by its OpenAlex id.
    var usableOpenAlexID: String? {
        let trimmed = openAlexId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? nil : trimmed
    }
}
```

- [ ] **Step 4: Run the reader tests**

Expected: PASS (13 tests).

- [ ] **Step 5: Write the failing `open` tests**

Add to `ArchiveLibraryBackupTests`:

```swift
@Test func openPreviewsTheFixtureAgainstTheLibrary() async throws {
    try await library.save(paper("W3"))
    guard case let .ready(prepared, preview) = await backup.open(sharedFixtureURL) else { Issue.record("not ready"); return }
    // Fixture: W2741809807 (new), ref 2 without an OpenAlex id (skipped), W3 (already saved).
    #expect(preview == RestorePreview(exportedAt: 1_791_122_700_000, papers: 3, collections: 2, pdfs: 1, newPapers: 1, existingPapers: 1, papersSkipped: 1))
    backup.discard(prepared)
    #expect(!FileManager.default.fileExists(atPath: prepared.url.path))
}

@Test func openRejectsANonBackupAndKeepsNoCopy() async throws {
    let text = root.appending(path: "notes.txt")
    try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    try Data("hello".utf8).write(to: text)
    #expect(await backup.open(text) == .failed(.notABackup))
    #expect(((try? FileManager.default.contentsOfDirectory(atPath: root.appending(path: "work").path)) ?? []).isEmpty)
}

@Test func openReportsAnUnreadableSource() async {
    #expect(await backup.open(root.appending(path: "missing.hashiya")) == .failed(.unreadable))
}

@Test func leftoversFromAnEarlierProcessAreClearedOnFirstUse() async throws {
    let work = root.appending(path: "work", directoryHint: .isDirectory)
    try FileManager.default.createDirectory(at: work, withIntermediateDirectories: true)
    let stale = work.appending(path: "restore-old.hashiya")
    try Data("x".utf8).write(to: stale)
    try await library.save(paper("W1"))
    let exported = try await backup.export(includePdfs: false, onProgress: { _ in })
    #expect(!FileManager.default.fileExists(atPath: stale.path))
    #expect(FileManager.default.fileExists(atPath: exported.url.path))
}
```

- [ ] **Step 6: Implement `open` and `discard(PreparedBackup)`**

Add the types to `LibraryBackup.swift` and the two members to the protocol. In `ArchiveLibraryBackup`:

```swift
public func open(_ source: URL) async -> OpenResult {
    guard (try? prepareWorkDirectory()) != nil else { return .failed(.unreadable) }
    let copy = workDirectory.appending(path: "restore-\(newID()).hashiya", directoryHint: .notDirectory)
    var ready = false
    defer { if !ready { try? FileManager.default.removeItem(at: copy) } }
    let read: ArchiveRead = await Task.detached {
        let scoped = source.startAccessingSecurityScopedResource()
        defer { if scoped { source.stopAccessingSecurityScopedResource() } }
        do { try FileManager.default.copyItem(at: source, to: copy) } catch { return .invalid(.unreadable) }
        return readArchive(copy)
    }.value
    guard case let .valid(manifest, library) = read else {
        if case let .invalid(reason) = read { return .failed(reason) }
        return .failed(.unreadable)
    }
    var existing = 0
    var skipped = 0
    for paper in library.papers {
        guard let openAlexID = paper.usableOpenAlexID else { skipped += 1; continue }
        if Task.isCancelled { return .failed(.unreadable) }
        if (try? await store.matchFor(openAlexID: openAlexID, doi: nil)) ?? nil != nil { existing += 1 }
    }
    let restorable = library.papers.count - skipped
    let preview = RestorePreview(
        exportedAt: BackupFormat.parseISOUTC(manifest.exportedAt),
        papers: library.papers.count,
        collections: library.collections.count,
        pdfs: library.papers.filter { $0.pdf?.file != nil }.count,
        newPapers: restorable - existing,
        existingPapers: existing,
        papersSkipped: skipped
    )
    ready = true
    return .ready(PreparedBackup(url: copy, library: library), preview)
}

public func discard(_ backup: PreparedBackup) {
    try? FileManager.default.removeItem(at: backup.url)
}
```

(`matchFor` with `doi: nil` because every restorable paper has an OpenAlex id, and DOI matching applies only to papers without one.)

- [ ] **Step 7: Run the tests**

Run: `… -only-testing:HashiyaDataTests/ArchiveLibraryBackupTests -only-testing:HashiyaDataTests/BackupArchiveTests`. Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add ios/HashiyaKit
git commit -m "feat(ios): open and validate a backup with a restore preview"
```

---

### Task 7: Applying a backup

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaData/Backup/IncomingMapping.swift`
- Modify: `LibraryBackup.swift`, `ArchiveLibraryBackup.swift`
- Test: additions to `ArchiveLibraryBackupTests.swift`

**Interfaces:**
- Produces:

```swift
func apply(_ backup: PreparedBackup, onProgress: @escaping @Sendable (Double) -> Void) async throws -> RestoreResult
public struct RestoreResult: Equatable, Sendable { public var papersAdded, notesAdded, collectionsCreated, pdfsAdded, pdfsMissing, papersSkipped: Int }
// IncomingMapping.swift (internal)
extension BackupPaper { func toIncoming(localID: String, openAlexID: String, staged: (url: URL, size: Int64)?) -> IncomingPaper }
```

  `apply` throws `BackupError.noSpace` (before anything is written), `.unreadable` (archive can't be read), `.writeFailed` (the merge failed — nothing was written), `.busy` (another restore is running in any window). Cancellation before the merge throws `CancellationError`; once the merge starts, it and the PDF moves always finish.

- [ ] **Step 1: Write the failing tests**

Add to `ArchiveLibraryBackupTests`:

```swift
func ready(_ url: URL, using: ArchiveLibraryBackup? = nil) async throws -> PreparedBackup {
    guard case let .ready(prepared, _) = await (using ?? backup).open(url) else { throw BackupError.unreadable }
    return prepared
}

@Test func applyingTheFixtureRestoresEverythingButPapersWithoutAnOpenAlexID() async throws {
    let result = try await backup.apply(try await ready(sharedFixtureURL), onProgress: { _ in })
    #expect(result == RestoreResult(papersAdded: 2, notesAdded: 0, collectionsCreated: 2, pdfsAdded: 1, pdfsMissing: 0, papersSkipped: 1))
    let deep = try #require(await store.citablePaper(openAlexID: "W2741809807"))
    #expect(deep.paper.readingStatus == "reading")
    #expect(deep.paper.citeKey == "lecun2015deep")
    #expect(deep.paper.pdfLastPage == 4)
    #expect(try String(contentsOf: files.file(paperID: deep.paper.id), encoding: .utf8).hasPrefix("%PDF-1.4"))
    #expect(files.isExcludedFromBackup(paperID: deep.paper.id))       // a downloaded PDF stays out of iCloud backups
    #expect(((try? FileManager.default.contentsOfDirectory(atPath: files.directory.path)) ?? []).allSatisfy { !$0.hasSuffix(".part") })
}

@Test func papersWithoutAnOpenAlexIdAreSkipped() async throws {
    _ = try await backup.apply(try await ready(sharedFixtureURL), onProgress: { _ in })
    // Read back the way the Library screen does: no paper with a blank id may appear.
    var snapshot: LibrarySnapshot?
    for await value in library.observeLibrary(query: "", status: nil, collectionID: nil) { snapshot = value; break }
    let ids = try #require(snapshot).papers.map(\.paper.openAlexID)
    #expect(ids.sorted() == ["W2741809807", "W3"])
    #expect(!ids.contains(""))
}

@Test func roundTripIntoAnEmptyLibrary() async throws {
    try await library.save(paper("W1"))
    try await library.save(paper("W2", title: "Second"))
    try await library.saveNotes(openAlexID: "W1", notes: PaperNotes(summary: "S", thoughts: "T"))
    try await library.setStatus(openAlexID: "W2", status: .read)
    try await storePdf("local-1")
    let c = try #require(await store.insertCollection(name: "Thesis", nameKey: "thesis", createdAt: 7))
    try await store.addToCollection(collectionID: c, openAlexID: "W2", addedAt: 8)
    let exported = try await backup.export(includePdfs: true, onProgress: { _ in })

    let otherStore = PaperStore(writer: try HashiyaDatabase.openInMemory())
    let otherFiles = PdfFileStore(directory: root.appending(path: "other-pdfs", directoryHint: .isDirectory))
    let target = Self.backup(store: otherStore, files: otherFiles, work: root.appending(path: "other-work", directoryHint: .isDirectory))
    let result = try await target.apply(try await ready(exported.url, using: target), onProgress: { _ in })

    #expect(result.papersAdded == 2 && result.pdfsAdded == 1)
    let w1 = try #require(await otherStore.citablePaper(openAlexID: "W1"))
    #expect(try await otherStore.notes(openAlexID: "W1")?.summary == "S")
    #expect(try await otherStore.citablePaper(openAlexID: "W2")?.paper.readingStatus == "read")
    #expect(try String(contentsOf: otherFiles.file(paperID: w1.paper.id), encoding: .utf8) == "%PDF-1.4 local-1")
}

@Test func restoringTwiceAddsNothing() async throws {
    _ = try await backup.apply(try await ready(sharedFixtureURL), onProgress: { _ in })
    let second = try await backup.apply(try await ready(sharedFixtureURL), onProgress: { _ in })
    #expect(second.papersAdded == 0 && second.collectionsCreated == 0 && second.pdfsAdded == 0)
}

@Test func theDevicesPdfIsKept() async throws {
    try await library.save(Paper(openAlexID: "W2741809807", doi: nil, title: "Mine", authors: [], year: nil, venue: nil, abstract: nil,
                                 citationCount: 0, isOpenAccess: false, openAccessPDFURL: nil))
    try await storePdf("local-1", "%PDF-1.4 mine")
    let result = try await backup.apply(try await ready(sharedFixtureURL), onProgress: { _ in })
    #expect(result.pdfsAdded == 0)
    #expect(try String(contentsOf: files.file(paperID: "local-1"), encoding: .utf8) == "%PDF-1.4 mine")
}

@Test func pdfEntryNameMustMatchRef() async throws {
    let url = root.appending(path: "evil.hashiya")
    try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    let archive = try Archive(url: url, accessMode: .create)
    for (name, text) in [(BackupFormat.manifestEntry, #"{"format":1}"#),
                         (BackupFormat.libraryEntry, #"{"papers":[{"ref":1,"openAlexId":"W1","title":"A","savedAt":1,"pdf":{"source":"attached","addedAt":1,"file":"pdfs/2.pdf"}},{"ref":2,"openAlexId":"W2","title":"B","savedAt":1}]}"#),
                         ("pdfs/2.pdf", "%PDF-1.4 not yours")] {
        let data = Data(text.utf8)
        try archive.addEntry(with: name, type: .file, uncompressedSize: Int64(data.count), compressionMethod: .deflate) { p, s in data.subdata(in: Int(p)..<(Int(p) + s)) }
    }
    let result = try await backup.apply(try await ready(url), onProgress: { _ in })
    #expect(result.pdfsAdded == 0 && result.pdfsMissing == 1)
}

@Test func anOversizedPdfEntryIsSkippedNotNoSpace() async throws {
    // Build an archive whose pdfs/1.pdf is larger than a tiny maxBytes by constructing the backup with a small limit:
    // give ArchiveLibraryBackup an internal `maxPdfBytes` parameter (default PdfFileStore.maxPdfBytes) and pass 4 here.
}

@Test func aSecondApplyWhileOneRunsIsRefused() async throws {
    // Hold the first apply inside the store gate (e.g. an internal test hook `beforeMerge: @Sendable () async -> Void` on
    // ArchiveLibraryBackup that awaits a stream), start a second apply on another prepared copy, and expect BackupError.busy.
}

@Test func aFailedMergeLeavesNoStagedPdfsAndChangesNothing() async throws {
    // Use an internal test hook `merge` (default `store.merge`) that throws; expect BackupError.writeFailed, no rows, no .part files.
}
```

Write the last three tests fully, adding the internal init parameters they name (`maxPdfBytes`, `beforeMerge`, `merge`) to `ArchiveLibraryBackup` with production defaults — the same seams Android uses.

- [ ] **Step 2: Run them to verify they fail**

Run: `… -only-testing:HashiyaDataTests/ArchiveLibraryBackupTests`. Expected: build fails (`apply`, `RestoreResult`).

- [ ] **Step 3: Write the mapping**

`ios/HashiyaKit/Sources/HashiyaData/Backup/IncomingMapping.swift`:

```swift
import Foundation
import HashiyaDatabase
import HashiyaModel

private let storedStatuses: Set<String> = ["to_read", "reading", "read"]

extension BackupPaper {
    /// The paper as rows under `localID`. Its PDF columns are set only when `staged` holds its file; notes with no text are
    /// dropped, like the app never stores empty notes.
    func toIncoming(localID: String, openAlexID: String, staged: (url: URL, size: Int64)?) -> IncomingPaper {
        var record = PaperRecord(
            id: localID, openAlexID: openAlexID, doi: doi.flatMap(normalizeDOI), title: title, year: year, venue: venue,
            abstract: abstract, citationCount: citationCount, isOpenAccess: isOpenAccess, oaPDFURL: oaPdfUrl, savedAt: savedAt,
            readingStatus: storedStatuses.contains(readingStatus) ? readingStatus : "to_read",
            publication: PublicationDetails(workType: workType, sourceType: sourceType, publisher: publisher, volume: volume,
                                            issue: issue, firstPage: firstPage, lastPage: lastPage),
            citeKey: citeKey.flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 },
            detailsFetched: detailsFetched
        )
        if let staged, let pdf {
            record.pdfSource = pdf.source == "downloaded" ? "downloaded" : "attached"
            record.pdfSize = staged.size
            record.pdfAddedAt = pdf.addedAt
            record.pdfLastPage = max(pdf.lastPage, 0)
        }
        let noteRecord = notes.flatMap { notes -> PaperNotesRecord? in
            let value = PaperNotes(summary: notes.summary, researchQuestion: notes.researchQuestion, method: notes.method,
                                   keyFindings: notes.keyFindings, limitations: notes.limitations, thoughts: notes.thoughts)
            return value.isEmpty ? nil : PaperNotesRecord(paperID: localID, notes: value, updatedAt: notes.updatedAt)
        }
        return IncomingPaper(
            ref: ref,
            paper: record,
            authors: authors.enumerated().map { PaperAuthorRecord(paperID: localID, position: $0.offset, name: $0.element.name, openAlexAuthorID: $0.element.openAlexAuthorId) },
            notes: noteRecord
        )
    }
}
```

(Adapt `PublicationDetails`/`PaperNotes` initializers to their real signatures.)

- [ ] **Step 4: Implement `apply`**

Add `RestoreResult` and the `apply` requirement to `LibraryBackup.swift`. In `ArchiveLibraryBackup` add `private let applying = OSAllocatedUnfairLock(initialState: false)` plus the three internal seams, then:

```swift
public func apply(_ backup: PreparedBackup, onProgress: @escaping @Sendable (Double) -> Void) async throws -> RestoreResult {
    // One restore at a time across every window.
    guard applying.withLock({ busy -> Bool in defer { busy = true }; return !busy }) else { throw BackupError.busy }
    defer { applying.withLock { $0 = false } }
    let token = await background.begin(name: "Restore library", onExpiry: {})
    defer { token.end() }
    return try await pdfs.withStoreGate { [self] in
        // Inside the gate: the startup sweep would delete staged `.part` files, and the app waits for it before suspending
        // the database.
        let restorable = backup.library.papers.compactMap { paper in paper.usableOpenAlexID.map { (paper, $0) } }
        let refs = Set(restorable.map(\.0.ref))
        var staged: [Int: (url: URL, size: Int64)] = [:]
        defer { staged.values.forEach { try? FileManager.default.removeItem(at: $0.url) } }
        var missing = 0
        let named = restorable.filter { $0.0.pdf?.file != nil }
        do {
            let archive = try Archive(url: backup.url, accessMode: .read)
            for (index, (paper, _)) in named.enumerated() {
                try Task.checkCancellation()
                // Only the paper's own entry name is ever read, so no entry can reach outside the PDF folder.
                guard paper.pdf?.file == BackupFormat.pdfEntry(ref: paper.ref), let entry = archive[BackupFormat.pdfEntry(ref: paper.ref)],
                      Int64(entry.uncompressedSize) <= maxPdfBytes
                else { missing += 1; continue }
                if files.usableSpace() < Int64(entry.uncompressedSize) + Self.minFreeBytes { throw BackupError.noSpace }
                let temp = workDirectory.appending(path: "entry-\(newID()).pdf", directoryHint: .notDirectory)
                defer { try? FileManager.default.removeItem(at: temp) }
                _ = try archive.extract(entry, to: temp, skipCRC32: false)
                switch try files.stage(prefix: "restore", copying: temp, maxBytes: maxPdfBytes) {
                case let .staged(url, size): staged[paper.ref] = (url, size)
                case .notPDF, .tooLarge: missing += 1
                }
                onProgress(Double(index + 1) / Double(named.count + 1))
            }
        } catch let error as BackupError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch is PdfWriteError {
            throw BackupError.noSpace
        } catch {
            throw BackupError.unreadable
        }
        try Task.checkCancellation()
        let papers = restorable.map { paper, openAlexID in paper.toIncoming(localID: newID(), openAlexID: openAlexID, staged: staged[paper.ref]) }
        let collections = backup.library.collections
            .filter { !$0.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            .map { IncomingCollection(name: $0.name.trimmingCharacters(in: .whitespacesAndNewlines), nameKey: collectionNameKey($0.name),
                                      createdAt: $0.createdAt, refs: $0.papers.filter(refs.contains)) }
        await beforeMerge()
        // From here on nothing is cancelled: once the merge commits, its PDFs must land. An unstructured task doesn't
        // inherit the caller's cancellation.
        let toMove = staged
        staged = [:]
        let (outcome, added, failedMoves) = try await Task { [merge, files, now] in
            let outcome: MergeOutcome
            do { outcome = try await merge(papers, collections, now()) } catch {
                toMove.values.forEach { try? FileManager.default.removeItem(at: $0.url) }
                throw BackupError.writeFailed
            }
            var added = 0
            var failed = 0
            for (ref, file) in toMove {
                guard let target = outcome.pdfTargets[ref] else { try? FileManager.default.removeItem(at: file.url); continue }
                do {
                    try files.commit(staged: file.url, paperID: target)
                    let downloaded = papers.first { $0.ref == ref }?.paper.pdfSource == "downloaded"
                    files.setExcludedFromBackup(downloaded, paperID: target)
                    added += 1
                } catch {
                    // A failed rename leaves the row without its file; the next startup sweep clears it.
                    try? FileManager.default.removeItem(at: file.url)
                    failed += 1
                }
            }
            return (outcome, added, failed)
        }.value
        onProgress(1)
        return RestoreResult(papersAdded: outcome.added, notesAdded: outcome.notesAdded, collectionsCreated: outcome.collectionsCreated,
                             pdfsAdded: added, pdfsMissing: missing + failedMoves, papersSkipped: backup.library.papers.count - restorable.count)
    }
}
```

`setExcludedFromBackup(false, …)` for an attached PDF is harmless (renamed `.part` files never carry the flag). `collectionNameKey` is in HashiyaModel. `archive.extract(_:to:skipCRC32:)` exists in ZIPFoundation; it writes the whole entry, so the declared-size check above plus `stage`'s byte count guard size.

- [ ] **Step 5: Run the tests**

Run: `… -only-testing:HashiyaDataTests` (whole target). Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add ios/HashiyaKit
git commit -m "feat(ios): restore a backup by merging it into the library"
```

---

### Task 8: Settings — Backup section and Export screen

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaTesting/FakeLibraryBackup.swift`, `ios/HashiyaKit/Tests/HashiyaDataTests/FakeLibraryBackupTests.swift`
- Create: `ios/HashiyaKit/Sources/FeatureSettings/Strings.swift`, `BackupSection.swift`, `ExportBackupView.swift`, `UTType+Hashiya.swift`
- Modify: `FeatureSettings/SettingsViewModel.swift`, `SettingsView.swift`, `Resources/Localizable.xcstrings`, `ios/Hashiya/AppContainer.swift`
- Test: `ios/HashiyaKit/Tests/FeatureSettingsTests/SettingsBackupTests.swift`, existing `SettingsViewModelTests.swift` (constructor), snapshots `ios/HashiyaSnapshotTests/BackupSnapshotTests.swift`

**Interfaces:**
- Consumes: `LibraryBackup`, `BackupSummary`, `ExportedFile`, `BackupError`.
- Produces:
  - `FakeLibraryBackup` (public final class, Sendable via lock): settable `summary`, `exportFailure: BackupError?`, `missingPdfs`, `openResult: OpenResult`, `applyResult`, `applyFailure`, `exportGate: AsyncStream<Void>?` (export waits for one value when set); records `exports: [Bool]`, `discardedExports`, `opened: [URL]`, `applied`, `discardedBackups`; `static func preparedBackup() -> PreparedBackup` (needs an internal-init escape: add `public static func forTesting(url:) -> PreparedBackup` on `PreparedBackup` in HashiyaData, documented as for fakes).
  - `SettingsViewModel(preferences:pdfs:backup:)` with `public internal(set) var backup = BackupState()`:

```swift
public struct BackupState: Equatable, Sendable {
    public var summary: BackupSummary?
    public var export: ExportState = .idle
    public var message: BackupMessage?
}
public enum ExportState: Equatable, Sendable {
    case idle
    case choosing(includePdfs: Bool)
    case building(includePdfs: Bool, progress: Double)
    /// The view presents `.fileMover` for the file; `exportFinished` reports what happened.
    case readyToSave(ExportedFile)
}
public enum BackupMessage: Equatable, Sendable { case exported(missingPdfs: Int), exportFailed(BackupError), exportCancelled }
```

  Functions: `loadBackupSummary() async`, `startExport()`, `setIncludePdfs(_:)`, `confirmExport()`, `cancelExport()`, `exportFinished(saved: Bool)`, `dismissMessage()`. `onAppear`/`.task` reloads the summary (stale-after-restore fix from Android).
  - `extension UTType { static let hashiyaBackup = UTType(exportedAs: "com.etatech.hashiya.backup", conformingTo: .zip) }` (public, in FeatureSettings; the app target's Info.plist declares it in Task 9).

- [ ] **Step 1: Write the fake and its tests**

Follow the pattern of `FakePdfRepository` (`OSAllocatedUnfairLock<State>`). `FakeLibraryBackupTests` checks that it records exports and returns the configured results (copy the shape of `FakePdfRepositoryTests`).

- [ ] **Step 2: Write the failing ViewModel tests**

`ios/HashiyaKit/Tests/FeatureSettingsTests/SettingsBackupTests.swift`:

```swift
import Foundation
import HashiyaData
import HashiyaTesting
import Testing
@testable import FeatureSettings

@MainActor struct SettingsBackupTests {
    let backup = FakeLibraryBackup()

    init() { backup.summary = BackupSummary(papers: 182, collections: 6, pdfCount: 41, pdfBytes: 238_000_000) }

    func viewModel() -> SettingsViewModel {
        SettingsViewModel(preferences: FakeUserPreferencesRepository(), pdfs: FakePdfRepository(), backup: backup)
    }

    @Test func loadsTheSummary() async {
        let vm = viewModel()
        await vm.loadBackupSummary()
        #expect(vm.backup.summary?.papers == 182)
    }

    @Test func exportsWithoutPdfsByDefaultThenSaves() async {
        let vm = viewModel()
        await vm.loadBackupSummary()
        vm.startExport()
        #expect(vm.backup.export == .choosing(includePdfs: false))
        vm.confirmExport()
        await eventually { if case .readyToSave = vm.backup.export { return true }; return false }
        #expect(backup.exports == [false])
        vm.exportFinished(saved: true)
        #expect(vm.backup.export == .idle)
        #expect(vm.backup.message == .exported(missingPdfs: 0))
        #expect(backup.discardedExports == 1)
    }

    @Test func includePdfsIsPassedThrough() async {
        let vm = viewModel()
        vm.startExport()
        vm.setIncludePdfs(true)
        vm.confirmExport()
        await eventually { backup.exports == [true] }
    }

    @Test func cancellingTheSaveDiscardsTheFile() async {
        let vm = viewModel()
        vm.startExport()
        vm.confirmExport()
        await eventually { if case .readyToSave = vm.backup.export { return true }; return false }
        vm.exportFinished(saved: false)
        #expect(vm.backup.export == .idle)
        #expect(vm.backup.message == nil)
        #expect(backup.discardedExports == 1)
    }

    @Test func cancellingABuildingExportReturnsToIdle() async {
        let gate = AsyncStream<Void>.makeStream()
        backup.exportGate = gate.stream
        let vm = viewModel()
        vm.startExport()
        vm.confirmExport()
        await eventually { if case .building = vm.backup.export { return true }; return false }
        vm.cancelExport()
        gate.continuation.yield(())
        #expect(vm.backup.export == .idle)
        try? await Task.sleep(for: .milliseconds(50))
        #expect(vm.backup.export == .idle)
        #expect(vm.backup.message == nil)
    }

    @Test func aFailedExportShowsItsReason() async {
        backup.exportFailure = .noSpace
        let vm = viewModel()
        vm.startExport()
        vm.confirmExport()
        await eventually { vm.backup.message == .exportFailed(.noSpace) }
        #expect(vm.backup.export == .idle)
    }

    @Test func exportOnlyStartsFromIdle() async {
        let vm = viewModel()
        vm.startExport()
        vm.confirmExport()
        await eventually { if case .readyToSave = vm.backup.export { return true }; return false }
        vm.startExport()
        if case .readyToSave = vm.backup.export {} else { Issue.record("a second export replaced the one waiting to be saved") }
    }
}
```

Update `SettingsViewModelTests` (and the existing `creatingItDoesNotMakeTheCreatorObserveIt` test) to pass `backup: FakeLibraryBackup()`.

- [ ] **Step 3: Run them to verify they fail**

Run: `cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:FeatureSettingsTests`. Expected: build fails.

- [ ] **Step 4: Implement the ViewModel**

In `SettingsViewModel` add `@ObservationIgnored private let libraryBackup: any LibraryBackup`, `@ObservationIgnored private var exportTask: Task<Void, Never>?`, the `backup` state, and:

```swift
public func loadBackupSummary() async {
    if let summary = try? await libraryBackup.summary() { backup.summary = summary }
}

public func startExport() {
    guard backup.export == .idle else { return }
    backup.export = .choosing(includePdfs: false)
}

public func setIncludePdfs(_ include: Bool) {
    if case .choosing = backup.export { backup.export = .choosing(includePdfs: include) }
}

public func confirmExport() {
    guard case let .choosing(includePdfs) = backup.export else { return }
    backup.export = .building(includePdfs: includePdfs, progress: 0)
    exportTask = Task { [weak self, libraryBackup] in
        do {
            let file = try await libraryBackup.export(includePdfs: includePdfs) { progress in
                Task { @MainActor [weak self] in
                    guard let self, case let .building(include, _) = self.backup.export else { return }
                    self.backup.export = .building(includePdfs: include, progress: progress)
                }
            }
            guard let self, !Task.isCancelled else { libraryBackup.discard(file); return }
            self.backup.export = .readyToSave(file)
        } catch let error as BackupError {
            self?.backup.export = .idle
            self?.backup.message = .exportFailed(error)
        } catch {
            // Cancelled: cancelExport already reset the state.
        }
    }
}

public func cancelExport() {
    guard case .building = backup.export else { return }
    exportTask?.cancel()
    exportTask = nil
    backup.export = .idle
}

/// What the save panel did with the file; `saved: false` when it was cancelled.
public func exportFinished(saved: Bool) {
    guard case let .readyToSave(file) = backup.export else { return }
    libraryBackup.discard(file)    // `.fileMover` moved it on success; this removes it if it is still there
    backup.export = .idle
    backup.message = saved ? .exported(missingPdfs: file.missingPdfs) : nil
}

public func dismissMessage() { backup.message = nil }
```

`init` must not read observable state (keep `creatingItDoesNotMakeTheCreatorObserveIt` green). Update `AppContainer.makeSettingsViewModel()` to pass `backup: dependencies.backup` (store the dependency in `AppContainer` like the others).

- [ ] **Step 5: Run the ViewModel tests**

Expected: PASS.

- [ ] **Step 6: Strings**

Move the private `L10n` enum out of `SettingsView.swift` into `Strings.swift` as `@MainActor enum L10n` (internal), unchanged. Add these keys to `FeatureSettings/Resources/Localizable.xcstrings` (en + ar; plurals with all six Arabic forms, mirroring how `settings.deleteDownloadedMessage` is stored):

| Key | English | Arabic |
|---|---|---|
| `backup.section` | Backup | النسخ الاحتياطي |
| `backup.description` | Save your library to a file, or add papers from a backup. | احفظ مكتبتك في ملف، أو أضف أوراقًا من نسخة احتياطية. |
| `backup.export` | Export library | تصدير المكتبة |
| `backup.restore` | Restore from backup | الاستعادة من نسخة احتياطية |
| `backup.exportTitle` | Export library | تصدير المكتبة |
| `backup.counts` (format `%1$@ · %2$@`) | %1$@ · %2$@ | %1$@ · %2$@ |
| `backup.papers` (plural %lld) | one: %lld paper / other: %lld papers | zero: لا أوراق · one: ورقة واحدة · two: ورقتان · few: %lld أوراق · many: %lld ورقة · other: %lld ورقة |
| `backup.collections` (plural %lld) | one: %lld collection / other: %lld collections | zero: لا مجموعات · one: مجموعة واحدة · two: مجموعتان · few: %lld مجموعات · many: %lld مجموعة · other: %lld مجموعة |
| `backup.includePdfs` | Include PDFs | تضمين ملفات PDF |
| `backup.pdfsSize` (plural %lld, %@) | one: %lld PDF, %@ / other: %lld PDFs, %@ | zero: لا ملفات PDF، %@ · one: ملف PDF واحد، %@ · two: ملفا PDF، %@ · few: %lld ملفات PDF، %@ · many: %lld ملف PDF، %@ · other: %lld ملف PDF، %@ |
| `backup.noSettings` | Your API key and settings aren't included. | لا يتضمن الملف مفتاح API ولا الإعدادات. |
| `backup.exportButton` | Export | تصدير |
| `backup.exporting` | Preparing backup… | جارٍ تجهيز النسخة الاحتياطية… |
| `backup.exported` | Library exported | تم تصدير المكتبة |
| `backup.exportedMissing` (plural %lld) | one: Library exported. %lld PDF was missing and wasn't included. / other: Library exported. %lld PDFs were missing and weren't included. | zero: تم تصدير المكتبة. · one: تم تصدير المكتبة. ملف PDF واحد كان مفقودًا ولم يُضمَّن. · two: تم تصدير المكتبة. ملفا PDF كانا مفقودين ولم يُضمَّنا. · few: تم تصدير المكتبة. %lld ملفات PDF كانت مفقودة ولم تُضمَّن. · many: تم تصدير المكتبة. %lld ملف PDF كان مفقودًا ولم يُضمَّن. · other: تم تصدير المكتبة. %lld ملف PDF كان مفقودًا ولم يُضمَّن. |
| `backup.exportFailedSpace` | Couldn't export — not enough storage space. | تعذّر التصدير — لا توجد مساحة تخزين كافية. |
| `backup.exportFailed` | Couldn't export the library. | تعذّر تصدير المكتبة. |

Add L10n helpers for the plural/format keys (`L10n.backupPapers(_:)`, etc.) next to the existing ones. Run `python3 ios/scripts/check-translations.py` — must pass.

- [ ] **Step 7: The UI**

`BackupSection.swift` — a `Section` placed in `SettingsView`'s `Form` after the storage section, styled like `storageSection` (header font/colour, `textCase(nil)`):
- description text;
- `NavigationLink(value: SettingsDestination.export)` "Export library", disabled when `summary?.papers ?? 0 == 0`, id `settings.exportLibrary`;
- a "Restore from backup" button (Task 9 wires its action; for now it takes a closure `onRestore`), id `settings.restoreBackup`.

`ExportBackupView.swift` — pushed via `.navigationDestination(for: SettingsDestination.self)` inside SettingsView's `NavigationStack`:
- `Form` with the counts line, an `Include PDFs` `Toggle` (hidden when `pdfCount == 0`; disabled while building) with the size as secondary text (`PaperFormat.fileSize`), the no-settings note, and while building a `ProgressView(value:)` + "Preparing backup…";
- toolbar: confirmation "Export" (disabled while building) and a Cancel that calls `cancelExport()` while building (and pops otherwise);
- `.onAppear { viewModel.startExport() }`, `.onDisappear { viewModel.cancelExport() }` (cancel only acts while building);
- `.fileMover(isPresented: <bound to export == .readyToSave>, file: exportedURL) { result in viewModel.exportFinished(saved: (try? result.get()) != nil) }` — `.fileMover` shows the system save panel and **moves** the temporary file to the chosen place, so no copy is held in memory. The temporary file already carries `ExportedFile.fileName` (Task 5 writes it inside its own folder), so the panel suggests the right name.
- After `exportFinished`, pop back to Settings and show the message as a banner (use the design system's `HashiyaBanner` if Settings can host it; otherwise an inline `Text` in the Backup section that clears via `dismissMessage()` after a few seconds — follow how Library shows `LibraryMessage`).

`SettingsView` calls `await viewModel.loadBackupSummary()` in its `.task` and again when the Export screen or Restore flow returns (`.onAppear` of the Form), so counts are never stale after a restore.

- [ ] **Step 8: Snapshots**

`ios/HashiyaSnapshotTests/BackupSnapshotTests.swift`:

```swift
import FeatureSettings
import HashiyaData
import HashiyaTesting
import SwiftUI
import Testing

@MainActor @Suite(.serialized) struct BackupSnapshotTests {
    @Test func exportScreen() async {
        let backup = FakeLibraryBackup()
        backup.summary = BackupSummary(papers: 182, collections: 6, pdfCount: 41, pdfBytes: 238_000_000)
        let viewModel = SettingsViewModel(preferences: FakeUserPreferencesRepository(), pdfs: FakePdfRepository(), backup: backup)
        await viewModel.loadBackupSummary()
        viewModel.startExport()
        viewModel.setIncludePdfs(true)
        assertHashiyaSnapshots(of: NavigationStack { ExportBackupView(viewModel: viewModel) }, named: "export", arabicText: "تضمين ملفات PDF")
    }
}
```

Make `ExportBackupView` public (with a public init taking the view model). Existing `SettingsSnapshotTests` baselines change because the Form gains a section — expected; they are re-recorded on CI.

- [ ] **Step 9: Run tests and build**

Run: package `FeatureSettingsTests` + `HashiyaDataTests/FakeLibraryBackupTests`; `python3 ios/scripts/check-translations.py`; `xcodegen generate --spec ios/project.yml && xcodebuild build -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 17 Pro'`. Expected: PASS / builds. Snapshot tests: new/changed baselines fail locally by design — list them in the report.

- [ ] **Step 10: Commit**

```bash
git add ios/HashiyaKit ios/Hashiya/AppContainer.swift ios/HashiyaSnapshotTests/BackupSnapshotTests.swift
git commit -m "feat(ios): export the library from Settings"
```

---

### Task 9: Restore screen, the `.hashiya` document type and opening files

**Files:**
- Create: `ios/HashiyaKit/Sources/FeatureSettings/RestoreViewModel.swift`, `RestoreView.swift`
- Modify: `FeatureSettings/SettingsView.swift`, `BackupSection.swift`, `Resources/Localizable.xcstrings`, `ios/Hashiya/Info.plist`, `ios/Hashiya/AppContainer.swift`, `ios/Hashiya/RootView.swift`
- Test: `ios/HashiyaKit/Tests/FeatureSettingsTests/RestoreViewModelTests.swift`, snapshots in `BackupSnapshotTests.swift`, UI test `ios/HashiyaUITests/BackupFlowTests.swift`

**Interfaces:**
- Consumes: `LibraryBackup.open/apply/discard(PreparedBackup)`, `OpenResult`, `RestorePreview`, `RestoreResult`, `BackupError`.
- Produces:

```swift
public enum RestoreState: Equatable, Sendable {
    case loading
    case invalid(OpenFailure)
    case preview(RestorePreview)
    case applying(progress: Double)
    case done(RestoreResult)
    case failed(BackupError)
}
@Observable @MainActor public final class RestoreViewModel {
    public init(source: URL, backup: any LibraryBackup)
    public internal(set) var state: RestoreState = .loading
    public func load() async          // view .task; opens once
    public func confirm()             // preview → applying → done/failed
    public func cancel()              // discards the prepared copy (not while applying)
}
public struct RestoreView: View { public init(viewModel: RestoreViewModel, onDone: @escaping () -> Void) }
// AppContainer
func makeRestoreViewModel(source: URL) -> RestoreViewModel
```

- [ ] **Step 1: Write the failing ViewModel tests**

`ios/HashiyaKit/Tests/FeatureSettingsTests/RestoreViewModelTests.swift`:

```swift
import Foundation
import HashiyaData
import HashiyaTesting
import Testing
@testable import FeatureSettings

@MainActor struct RestoreViewModelTests {
    let backup = FakeLibraryBackup()
    let preview = RestorePreview(exportedAt: 1, papers: 182, collections: 6, pdfs: 41, newPapers: 150, existingPapers: 31, papersSkipped: 1)
    let source = URL(fileURLWithPath: "/tmp/backup.hashiya")

    @Test func opensTheFileAndShowsThePreview() async {
        backup.openResult = .ready(FakeLibraryBackup.preparedBackup(), preview)
        let vm = RestoreViewModel(source: source, backup: backup)
        await vm.load()
        #expect(backup.opened == [source])
        #expect(vm.state == .preview(preview))
    }

    @Test func showsWhyAFileCantBeRestored() async {
        backup.openResult = .failed(.newerFormat)
        let vm = RestoreViewModel(source: source, backup: backup)
        await vm.load()
        #expect(vm.state == .invalid(.newerFormat))
    }

    @Test func confirmAppliesAndShowsTheResult() async {
        backup.openResult = .ready(FakeLibraryBackup.preparedBackup(), preview)
        backup.applyResult = RestoreResult(papersAdded: 150, notesAdded: 3, collectionsCreated: 6, pdfsAdded: 38, pdfsMissing: 3, papersSkipped: 1)
        let vm = RestoreViewModel(source: source, backup: backup)
        await vm.load()
        vm.confirm()
        await eventually { if case .done = vm.state { return true }; return false }
        #expect(vm.state == .done(backup.applyResult))
        #expect(backup.discardedBackups == 1)
    }

    @Test func aRefusedRestoreSaysAnotherIsRunning() async {
        backup.openResult = .ready(FakeLibraryBackup.preparedBackup(), preview)
        backup.applyFailure = .busy
        let vm = RestoreViewModel(source: source, backup: backup)
        await vm.load()
        vm.confirm()
        await eventually { vm.state == .failed(.busy) }
    }

    @Test func confirmOutsideThePreviewDoesNothing() async {
        backup.openResult = .failed(.damaged)
        let vm = RestoreViewModel(source: source, backup: backup)
        await vm.load()
        vm.confirm()
        #expect(backup.applied.isEmpty)
    }

    @Test func cancelDiscards() async {
        backup.openResult = .ready(FakeLibraryBackup.preparedBackup(), preview)
        let vm = RestoreViewModel(source: source, backup: backup)
        await vm.load()
        vm.cancel()
        #expect(backup.discardedBackups == 1)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `… -only-testing:FeatureSettingsTests/RestoreViewModelTests`. Expected: build fails.

- [ ] **Step 3: Implement the ViewModel**

```swift
import Foundation
import HashiyaData
import Observation

@Observable @MainActor public final class RestoreViewModel {
    public internal(set) var state: RestoreState = .loading
    @ObservationIgnored private let source: URL
    @ObservationIgnored private let backup: any LibraryBackup
    @ObservationIgnored private var prepared: PreparedBackup?
    @ObservationIgnored private var opened = false

    public init(source: URL, backup: any LibraryBackup) {
        self.source = source
        self.backup = backup
    }

    public func load() async {
        guard !opened else { return }
        opened = true
        switch await backup.open(source) {
        case let .ready(prepared, preview):
            self.prepared = prepared
            state = .preview(preview)
        case let .failed(reason):
            state = .invalid(reason)
        }
    }

    public func confirm() {
        guard case .preview = state, let prepared else { return }
        state = .applying(progress: 0)
        // Not tied to the view: a restore that has started finishes even if the screen goes away.
        Task { [weak self, backup] in
            let outcome: RestoreState
            do {
                let result = try await backup.apply(prepared) { progress in
                    Task { @MainActor [weak self] in
                        if case .applying = self?.state { self?.state = .applying(progress: progress) }
                    }
                }
                outcome = .done(result)
            } catch let error as BackupError {
                outcome = .failed(error)
            } catch {
                outcome = .failed(.writeFailed)
            }
            backup.discard(prepared)
            self?.prepared = nil
            self?.state = outcome
        }
    }

    public func cancel() {
        if case .applying = state { return }
        if let prepared { backup.discard(prepared) }
        prepared = nil
    }
}
```

(`RestoreState` from the Interfaces block in the same file.)

- [ ] **Step 4: Strings**

Add to `Localizable.xcstrings` (en + ar, plurals with six Arabic forms):

| Key | English | Arabic |
|---|---|---|
| `restore.title` | Restore from backup | الاستعادة من نسخة احتياطية |
| `restore.reading` | Reading backup… | جارٍ قراءة النسخة الاحتياطية… |
| `restore.fromDate` (%@) | Backup from %@ | نسخة احتياطية من %@ |
| `restore.counts` (%1$@ · %2$@ · %3$@) | %1$@ · %2$@ · %3$@ | %1$@ · %2$@ · %3$@ |
| `restore.pdfs` (plural) | one: %lld PDF / other: %lld PDFs | zero: لا ملفات PDF · one: ملف PDF واحد · two: ملفا PDF · few: %lld ملفات PDF · many: %lld ملف PDF · other: %lld ملف PDF |
| `restore.newPapers` (plural) | one: %lld new paper will be added / other: %lld new papers will be added | zero: لن تُضاف أوراق جديدة · one: ستُضاف ورقة جديدة واحدة · two: ستُضاف ورقتان جديدتان · few: ستُضاف %lld أوراق جديدة · many: ستُضاف %lld ورقة جديدة · other: ستُضاف %lld ورقة جديدة |
| `restore.existingPapers` (plural) | one: %lld is already in your library and stays as it is / other: %lld are already in your library and stay as they are | zero: لا توجد أوراق منها في مكتبتك · one: ورقة واحدة موجودة في مكتبتك وتبقى كما هي · two: ورقتان موجودتان في مكتبتك وتبقيان كما هما · few: %lld أوراق موجودة في مكتبتك وتبقى كما هي · many: %lld ورقة موجودة في مكتبتك وتبقى كما هي · other: %lld ورقة موجودة في مكتبتك وتبقى كما هي |
| `restore.skippedPapers` (plural) | one: %lld paper without an OpenAlex ID will be skipped / other: %lld papers without an OpenAlex ID will be skipped | zero: لن تُتخطّى أي ورقة · one: ستُتخطّى ورقة واحدة بلا معرّف OpenAlex · two: ستُتخطّى ورقتان بلا معرّف OpenAlex · few: ستُتخطّى %lld أوراق بلا معرّف OpenAlex · many: ستُتخطّى %lld ورقة بلا معرّف OpenAlex · other: ستُتخطّى %lld ورقة بلا معرّف OpenAlex |
| `restore.keepsLibrary` | Nothing in your library is changed or removed. | لن يُغيَّر أو يُحذف شيء من مكتبتك. |
| `restore.add` | Add to library | إضافة إلى المكتبة |
| `restore.cancel` | Cancel | إلغاء |
| `restore.applying` | Adding to your library… | جارٍ الإضافة إلى مكتبتك… |
| `restore.doneTitle` | Backup restored | تمت استعادة النسخة الاحتياطية |
| `restore.doneBody` (4 × %lld) | Papers added: %1$lld · Notes added to existing papers: %2$lld · Collections added: %3$lld · PDFs added: %4$lld | أُضيفت أوراق: %1$lld، وملاحظات على أوراق موجودة: %2$lld، ومجموعات: %3$lld، وملفات PDF: %4$lld. |
| `restore.doneMissingPdfs` (plural) | one: %lld PDF in the backup couldn't be restored. / other: %lld PDFs in the backup couldn't be restored. | zero: استُعيدت كل ملفات PDF. · one: تعذّرت استعادة ملف PDF واحد من النسخة. · two: تعذّرت استعادة ملفي PDF من النسخة. · few: تعذّرت استعادة %lld ملفات PDF من النسخة. · many: تعذّرت استعادة %lld ملف PDF من النسخة. · other: تعذّرت استعادة %lld ملف PDF من النسخة. |
| `restore.doneSkipped` (plural) | one: %lld paper without an OpenAlex ID was skipped. / other: %lld papers without an OpenAlex ID were skipped. | zero: لم تُتخطَّ أي ورقة. · one: تُخطّيت ورقة واحدة بلا معرّف OpenAlex. · two: تُخطّيت ورقتان بلا معرّف OpenAlex. · few: تُخطّيت %lld أوراق بلا معرّف OpenAlex. · many: تُخطّيت %lld ورقة بلا معرّف OpenAlex. · other: تُخطّيت %lld ورقة بلا معرّف OpenAlex. |
| `restore.done` | Done | تم |
| `restore.notBackup` | This isn't a Hashiya backup. | هذا الملف ليس نسخة احتياطية من حاشية. |
| `restore.newer` | This backup was made by a newer version of Hashiya. Update the app to restore it. | أُنشئت هذه النسخة بإصدار أحدث من حاشية. حدّث التطبيق لاستعادتها. |
| `restore.damaged` | This backup is damaged and can't be restored. | هذه النسخة الاحتياطية تالفة ولا يمكن استعادتها. |
| `restore.unreadable` | The file couldn't be read. | تعذّرت قراءة الملف. |
| `restore.failedSpace` | Not enough storage space to restore this backup. Nothing was changed. | لا توجد مساحة تخزين كافية لاستعادة هذه النسخة. لم يتغيّر شيء. |
| `restore.failedBusy` | Another restore is running. Try again when it has finished. | تجري استعادة أخرى الآن. حاول مجددًا بعد انتهائها. |
| `restore.failed` | The backup couldn't be restored. Nothing was changed. | تعذّرت استعادة النسخة الاحتياطية. لم يتغيّر شيء. |

Use the app's Arabic name as it appears in `ios/Hashiya/InfoPlist.xcstrings` (حاشية) — check it. Run `python3 ios/scripts/check-translations.py`.

- [ ] **Step 5: The Restore view**

`RestoreView.swift` (public): a `Form` (same styling as Settings) switching on `state`:
- `.loading`: `ProgressView` + "Reading backup…".
- `.invalid(reason)`: the reason text + "Done" (calls `onDone`).
- `.preview(p)`: "Backup from <date>" (`p.exportedAt` formatted `.dateTime.day().month().year()` with `HashiyaLanguage.locale`) when non-nil; counts line (`backup.papers`, `backup.collections`, `restore.pdfs`); new / existing / skipped lines (existing and skipped only when > 0); keeps-library note; "Add to library" (`.borderedProminent`, id `restore.add`) and "Cancel" (calls `cancel()` then `onDone`).
- `.applying(progress)`: "Adding to your library…" + `ProgressView(value:)`; `.interactiveDismissDisabled()` and no back button (`.navigationBarBackButtonHidden(true)`).
- `.done(r)`: title, `restore.doneBody`, missing-PDFs and skipped lines when > 0, "Done".
- `.failed(e)`: `restore.failedSpace` / `restore.failedBusy` / `restore.failed`, "Done".
- `.task { await viewModel.load() }`; navigation title `restore.title`.

- [ ] **Step 6: Entry points**

- **From Settings:** `BackupSection`'s Restore button sets `importing = true`; SettingsView has `.fileImporter(isPresented: $importing, allowedContentTypes: [.hashiyaBackup, .zip]) { result in if case let .success(url) = result { restoreSource = url } }` and `.navigationDestination(item: $restoreSource) { url in RestoreView(viewModel: makeRestoreViewModel(url), onDone: { restoreSource = nil }) }` — `SettingsView` gets a `makeRestoreViewModel: (URL) -> RestoreViewModel` init parameter (keep a default-less param and update the one call site in RootView; snapshot tests pass a closure building one with `FakeLibraryBackup`). `RestoreViewModel.load` → `backup.open` handles the security-scoped URL. When Restore pops, Settings' `.onAppear` reloads the summary.
- **From other apps:** `ios/Hashiya/Info.plist` gains:

```xml
<key>UTExportedTypeDeclarations</key>
<array>
    <dict>
        <key>UTTypeIdentifier</key><string>com.etatech.hashiya.backup</string>
        <key>UTTypeDescription</key><string>Hashiya Library Backup</string>
        <key>UTTypeConformsTo</key><array><string>public.zip-archive</string><string>public.data</string></array>
        <key>UTTypeTagSpecification</key>
        <dict>
            <key>public.filename-extension</key><array><string>hashiya</string></array>
            <key>public.mime-type</key><array><string>application/zip</string></array>
        </dict>
    </dict>
</array>
<key>CFBundleDocumentTypes</key>
<array>
    <dict>
        <key>CFBundleTypeName</key><string>Hashiya Library Backup</string>
        <key>CFBundleTypeRole</key><string>Viewer</string>
        <key>LSHandlerRank</key><string>Owner</string>
        <key>LSItemContentTypes</key><array><string>com.etatech.hashiya.backup</string></array>
    </dict>
</array>
<key>LSSupportsOpeningDocumentsInPlace</key><false/>
```

  With `LSSupportsOpeningDocumentsInPlace = NO` the system hands the app a copy in `Documents/Inbox`. In `RootView` add `.onOpenURL { url in guard url.pathExtension.lowercased() == "hashiya" else { return }; showsSettings = false; openedBackup = url }` and `.sheet(item: $openedBackup) { url in NavigationStack { RestoreView(viewModel: container.makeRestoreViewModel(source: url), onDone: { openedBackup = nil; try? FileManager.default.removeItem(at: url) }) } }` (wrap `URL` in an `Identifiable` struct if needed). SwiftUI delivers `onOpenURL` to the one scene the system activates, so on iPad only that window shows Restore. If the file is in `Documents/Inbox`, delete it on done (the restore works on its own copy in the work folder).
- `AppContainer.makeRestoreViewModel(source:) -> RestoreViewModel(source: source, backup: dependencies.backup)`.
- Run `xcodegen generate --spec ios/project.yml` (Info.plist change).

- [ ] **Step 7: Snapshots and UI test**

Add to `BackupSnapshotTests`:

```swift
@Test func restorePreview() async {
    let backup = FakeLibraryBackup()
    backup.openResult = .ready(FakeLibraryBackup.preparedBackup(),
                               RestorePreview(exportedAt: 1_791_122_700_000, papers: 182, collections: 6, pdfs: 41, newPapers: 150, existingPapers: 31, papersSkipped: 1))
    let viewModel = RestoreViewModel(source: URL(fileURLWithPath: "/tmp/b.hashiya"), backup: backup)
    await viewModel.load()
    assertHashiyaSnapshots(of: NavigationStack { RestoreView(viewModel: viewModel, onDone: {}) }, named: "restorePreview", arabicText: "إضافة إلى المكتبة")
}

@Test func restoreDone() async {
    let backup = FakeLibraryBackup()
    backup.openResult = .ready(FakeLibraryBackup.preparedBackup(), RestorePreview(exportedAt: nil, papers: 1, collections: 0, pdfs: 0, newPapers: 1, existingPapers: 0, papersSkipped: 0))
    backup.applyResult = RestoreResult(papersAdded: 150, notesAdded: 3, collectionsCreated: 6, pdfsAdded: 38, pdfsMissing: 3, papersSkipped: 1)
    let viewModel = RestoreViewModel(source: URL(fileURLWithPath: "/tmp/b.hashiya"), backup: backup)
    await viewModel.load()
    viewModel.confirm()
    await eventually { if case .done = viewModel.state { return true }; return false }
    assertHashiyaSnapshots(of: NavigationStack { RestoreView(viewModel: viewModel, onDone: {}) }, named: "restoreDone", arabicText: "تمت استعادة النسخة الاحتياطية")
}
```

`ios/HashiyaUITests/BackupFlowTests.swift` (one test, iPhone; the system pickers aren't driven):

```swift
import XCTest

final class BackupFlowTests: XCTestCase {
    func testExportShowsTheLibraryCounts() {
        let app = XCUIApplication()
        app.launchArguments += ["-ui-testing", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        // Save one paper the way LibraryFlowTests does (Search tab → stub result → Save); reuse its helpers.
        // Then open Settings, tap "Export library" and check the counts.
        app.buttons["Settings"].firstMatch.tap()
        let export = app.buttons["settings.exportLibrary"]
        XCTAssertTrue(export.waitForExistence(timeout: UITestTimeout.long))
        export.tap()
        XCTAssertTrue(app.staticTexts["1 paper · 0 collections"].waitForExistence(timeout: UITestTimeout.long))
    }
}
```

Fill in the save-a-paper steps from `LibraryFlowTests` (read it). Regenerate the project so the new UI test file is included.

- [ ] **Step 8: Run tests and build**

Package: `FeatureSettingsTests`, `HashiyaDataTests`, `HashiyaDatabaseTests`. `python3 ios/scripts/check-translations.py`. App: `xcodegen generate --spec ios/project.yml && xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:HashiyaUITests/BackupFlowTests` and the full `-skip-testing:HashiyaUITests` run (snapshot failures for new/changed baselines are expected; list them). Also run `-only-testing:HashiyaUITests/LaunchTests -only-testing:HashiyaUITests/IPadFlowTests` on an iPad simulator to confirm nothing on iPad broke.

- [ ] **Step 9: Commit**

```bash
git add ios
git commit -m "feat(ios): restore a backup from Settings or by opening the file"
```

---

## After the tasks

1. Record snapshot baselines on CI: `bash ios/scripts/record-snapshots-on-ci.sh`, then commit `ios/**/__Snapshots__/**` (Settings baselines change, new Backup baselines appear) — the user runs this.
2. Device checks (iPhone and iPad):
   - Export with PDFs → Save to Files → the file is named `Hashiya-library-….hashiya`.
   - Tap the file in Files → Hashiya opens Restore in that window; restoring the same file adds nothing.
   - Restore an Android-made export, and restore an iOS export on the Android build from PR #33.
   - Downloaded PDFs: `Settings → [name] → iCloud → Manage Storage → Hashiya` shrinks after the sweep (or check `isExcludedFromBackup` via Xcode's container download).
   - Arabic RTL on Export and Restore screens; iPad portrait and landscape, two windows.
