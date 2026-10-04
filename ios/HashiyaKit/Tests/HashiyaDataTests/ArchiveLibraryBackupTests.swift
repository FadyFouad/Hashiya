import Foundation
import GRDB
import HashiyaDatabase
import HashiyaModel
import HashiyaNetwork
import os
import Testing
import ZIPFoundation
@testable import HashiyaData

private struct NoDownloads: PdfDownloading {
    func download(url: URL) async throws -> PdfDownload {
        throw CancellationError()
    }
}

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

    static func backup(
        store: PaperStore,
        files: PdfFileStore,
        work: URL,
        maxPdfBytes: Int64 = PdfFileStore.maxPdfBytes,
        beforeMerge: @escaping @Sendable () async -> Void = {},
        merge: (@Sendable ([IncomingPaper], [IncomingCollection], Int64) async throws -> MergeOutcome)? = nil
    ) -> ArchiveLibraryBackup {
        let ids = OSAllocatedUnfairLock(initialState: 0)
        return ArchiveLibraryBackup(
            store: store,
            pdfs: GRDBPdfRepository(store: store, files: files, downloader: NoDownloads()),
            files: files,
            workDirectory: work,
            appVersion: "0.3.0 (iOS)",
            background: NoBackgroundTime(),
            now: { 1_790_000_000_000 },
            newID: { ids.withLock { $0 += 1; return "restored-\($0)" } },
            maxPdfBytes: maxPdfBytes,
            beforeMerge: beforeMerge,
            merge: merge
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

    @Test func openPreviewsTheFixtureAgainstTheLibrary() async throws {
        try await library.save(paper("W3"))
        guard case let .ready(prepared, preview) = await backup.open(sharedFixtureURL) else {
            Issue.record("not ready")
            return
        }
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

    @Test func concurrentFirstUsesDoNotDeleteEachOthersFiles() async throws {
        try await library.save(paper("W1"))
        let work = root.appending(path: "work", directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: work, withIntermediateDirectories: true)
        try Data("x".utf8).write(to: work.appending(path: "restore-old.hashiya"))
        async let exported = backup.export(includePdfs: false, onProgress: { _ in })
        async let opened = backup.open(sharedFixtureURL)
        let file = try await exported
        guard case let .ready(prepared, _) = await opened else {
            Issue.record("not ready")
            return
        }
        #expect(FileManager.default.fileExists(atPath: file.url.path))
        #expect(FileManager.default.fileExists(atPath: prepared.url.path))
    }

    func ready(_ url: URL, using: ArchiveLibraryBackup? = nil) async throws -> PreparedBackup {
        guard case let .ready(prepared, _) = await (using ?? backup).open(url) else { throw BackupError.unreadable }
        return prepared
    }

    func partFiles() -> [String] {
        ((try? FileManager.default.contentsOfDirectory(atPath: files.directory.path)) ?? []).filter { $0.hasSuffix(".part") }
    }

    @Test func applyingTheFixtureRestoresEverythingButPapersWithoutAnOpenAlexID() async throws {
        let result = try await backup.apply(try await ready(sharedFixtureURL), onProgress: { _ in })
        #expect(result == RestoreResult(papersAdded: 2, notesAdded: 0, collectionsCreated: 2, pdfsAdded: 1, pdfsMissing: 0, papersSkipped: 1))
        let deep = try #require(await store.citablePaper(openAlexID: "W2741809807"))
        #expect(deep.paper.readingStatus == "reading")
        #expect(deep.paper.citeKey == "lecun2015deep")
        #expect(deep.paper.pdfLastPage == 4)
        #expect(try String(contentsOf: files.file(paperID: deep.paper.id), encoding: .utf8).hasPrefix("%PDF-1.4"))
        // A downloaded PDF stays out of iCloud backups.
        #expect(files.isExcludedFromBackup(paperID: deep.paper.id))
        #expect(partFiles().isEmpty)
    }

    @Test func papersWithoutAnOpenAlexIdAreSkipped() async throws {
        _ = try await backup.apply(try await ready(sharedFixtureURL), onProgress: { _ in })
        // Read back the way the Library screen does: no paper with a blank id may appear.
        var snapshot: LibrarySnapshot?
        for await value in library.observeLibrary(query: "", status: nil, collectionID: nil) {
            snapshot = value
            break
        }
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
        #expect(partFiles().isEmpty)
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

    @Test func aPdfEntryFailingItsChecksumCountsAsMissing() async throws {
        let url = root.appending(path: "corrupt.hashiya")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        do {
            let archive = try Archive(url: url, accessMode: .create)
            for (name, text) in [(BackupFormat.manifestEntry, #"{"format":1}"#),
                                 (BackupFormat.libraryEntry, #"{"papers":[{"ref":1,"openAlexId":"W1","title":"A","savedAt":1,"pdf":{"source":"attached","addedAt":1,"file":"pdfs/1.pdf"}}]}"#),
                                 ("pdfs/1.pdf", "%PDF-1.4 intact-content")] {
                let data = Data(text.utf8)
                // Stored, not deflated: the changed bytes still extract, and only the checksum can tell.
                try archive.addEntry(with: name, type: .file, uncompressedSize: Int64(data.count), compressionMethod: .none) { p, s in data.subdata(in: Int(p)..<(Int(p) + s)) }
            }
        }
        var bytes = try Data(contentsOf: url)
        let range = try #require(bytes.range(of: Data("intact".utf8)))
        bytes.replaceSubrange(range, with: Data("broken".utf8))
        try bytes.write(to: url)

        let result = try await backup.apply(try await ready(url), onProgress: { _ in })

        #expect(result.papersAdded == 1 && result.pdfsAdded == 0 && result.pdfsMissing == 1)
        let paper = try #require(await store.citablePaper(openAlexID: "W1"))
        #expect(paper.paper.pdfSource == nil)
        #expect(!FileManager.default.fileExists(atPath: files.file(paperID: paper.paper.id).path))
        #expect(partFiles().isEmpty)
    }

    @Test func anOversizedPdfEntryIsSkippedNotNoSpace() async throws {
        // The fixture's pdfs/1.pdf is 142 bytes, far over this limit, so it counts as missing before any space check.
        let small = Self.backup(store: store, files: files, work: root.appending(path: "small-work", directoryHint: .isDirectory), maxPdfBytes: 4)
        let result = try await small.apply(try await ready(sharedFixtureURL, using: small), onProgress: { _ in })
        #expect(result == RestoreResult(papersAdded: 2, notesAdded: 0, collectionsCreated: 2, pdfsAdded: 0, pdfsMissing: 1, papersSkipped: 1))
        let deep = try #require(await store.citablePaper(openAlexID: "W2741809807"))
        #expect(deep.paper.pdfSource == nil)
        #expect(!FileManager.default.fileExists(atPath: files.file(paperID: deep.paper.id).path))
        #expect(partFiles().isEmpty)
    }

    @Test func aSecondApplyWhileOneRunsIsRefused() async throws {
        let (entered, enteredContinuation) = AsyncStream<Void>.makeStream()
        let (release, releaseContinuation) = AsyncStream<Void>.makeStream()
        let held = Self.backup(
            store: store,
            files: files,
            work: root.appending(path: "held-work", directoryHint: .isDirectory),
            beforeMerge: {
                enteredContinuation.yield()
                for await _ in release {
                    break
                }
            }
        )
        let first = try await ready(sharedFixtureURL, using: held)
        let second = try await ready(sharedFixtureURL, using: held)
        let running = Task { try await held.apply(first, onProgress: { _ in }) }
        for await _ in entered {
            break
        }

        await #expect(throws: BackupError.busy) { try await held.apply(second, onProgress: { _ in }) }

        // Finishing the stream releases this restore and lets every later one through.
        releaseContinuation.finish()
        let result = try await running.value
        #expect(result.papersAdded == 2)
        // The refused restore didn't hold the lock: once the first ends, another may run.
        let again = try await held.apply(second, onProgress: { _ in })
        #expect(again.papersAdded == 0)
    }

    @Test func aFailedMergeLeavesNoStagedPdfsAndChangesNothing() async throws {
        struct MergeFailed: Error {}
        let failing = Self.backup(
            store: store,
            files: files,
            work: root.appending(path: "failing-work", directoryHint: .isDirectory),
            merge: { _, _, _ in throw MergeFailed() }
        )
        let prepared = try await ready(sharedFixtureURL, using: failing)

        await #expect(throws: BackupError.writeFailed) { try await failing.apply(prepared, onProgress: { _ in }) }

        #expect(try await store.paperCount() == 0)
        #expect(try await store.collectionCount() == 0)
        #expect(partFiles().isEmpty)
        #expect(((try? FileManager.default.contentsOfDirectory(atPath: files.directory.path)) ?? []).allSatisfy { !$0.hasSuffix(".pdf") })
    }
}
