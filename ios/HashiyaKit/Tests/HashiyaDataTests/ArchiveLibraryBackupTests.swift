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

    static func backup(store: PaperStore, files: PdfFileStore, work: URL) -> ArchiveLibraryBackup {
        let ids = OSAllocatedUnfairLock(initialState: 0)
        return ArchiveLibraryBackup(
            store: store,
            pdfs: GRDBPdfRepository(store: store, files: files, downloader: NoDownloads()),
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
}
