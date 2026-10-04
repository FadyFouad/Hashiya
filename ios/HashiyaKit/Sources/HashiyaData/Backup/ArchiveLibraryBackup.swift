import Foundation
import HashiyaDatabase
import HashiyaModel
import os
import ZIPFoundation

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
    /// The largest PDF entry a restore reads.
    private let maxPdfBytes: Int64
    /// Runs inside the store gate just before the merge; tests hold a restore there.
    private let beforeMerge: @Sendable () async -> Void
    private let merge: Merge
    private let workDirectoryReady = OSAllocatedUnfairLock(initialState: false)
    /// True while a restore runs. The app has one instance, shared by every window.
    private let applying = OSAllocatedUnfairLock(initialState: false)

    static let minFreeBytes: Int64 = 10 * 1024 * 1024

    typealias Merge = @Sendable (_ papers: [IncomingPaper], _ collections: [IncomingCollection], _ now: Int64) async throws -> MergeOutcome

    public convenience init(
        store: PaperStore,
        pdfs: GRDBPdfRepository,
        files: PdfFileStore,
        workDirectory: URL,
        appVersion: String,
        background: any BackgroundTimeGranting,
        now: @escaping @Sendable () -> Int64,
        newID: @escaping @Sendable () -> String
    ) {
        self.init(
            store: store,
            pdfs: pdfs,
            files: files,
            workDirectory: workDirectory,
            appVersion: appVersion,
            background: background,
            now: now,
            newID: newID,
            maxPdfBytes: PdfFileStore.maxPdfBytes,
            beforeMerge: {},
            merge: nil
        )
    }

    /// `maxPdfBytes`, `beforeMerge` and `merge` are seams for tests; nil `merge` is `store.merge`.
    init(
        store: PaperStore,
        pdfs: GRDBPdfRepository,
        files: PdfFileStore,
        workDirectory: URL,
        appVersion: String,
        background: any BackgroundTimeGranting,
        now: @escaping @Sendable () -> Int64,
        newID: @escaping @Sendable () -> String,
        maxPdfBytes: Int64 = PdfFileStore.maxPdfBytes,
        beforeMerge: @escaping @Sendable () async -> Void = {},
        merge: Merge? = nil
    ) {
        self.store = store
        self.pdfs = pdfs
        self.files = files
        self.workDirectory = workDirectory
        self.appVersion = appVersion
        self.background = background
        self.now = now
        self.newID = newID
        self.maxPdfBytes = maxPdfBytes
        self.beforeMerge = beforeMerge
        self.merge = merge ?? { papers, collections, now in
            try await store.merge(papers: papers, collections: collections, now: now)
        }
    }

    public func summary() async throws -> BackupSummary {
        let pdf = try await store.pdfTotals()
        return BackupSummary(
            papers: try await store.paperCount(),
            collections: try await store.collectionCount(),
            pdfCount: pdf.count,
            pdfBytes: pdf.bytes
        )
    }

    public func export(includePdfs: Bool, onProgress: @escaping @Sendable (Double) -> Void) async throws -> ExportedFile {
        let token = await background.begin(name: "Export library", onExpiry: {})
        do {
            let exported = try await buildExport(includePdfs: includePdfs, onProgress: onProgress)
            await token.end()
            return exported
        } catch {
            await token.end()
            throw error
        }
    }

    /// Deletes the export's folder; the file itself is gone already when `.fileMover` moved it.
    public func discard(_ exported: ExportedFile) {
        try? FileManager.default.removeItem(at: exported.url.deletingLastPathComponent())
    }

    public func open(_ source: URL) async -> OpenResult {
        guard (try? prepareWorkDirectory()) != nil else {
            return .failed(.unreadable)
        }
        let copy = workDirectory.appending(path: "restore-\(newID()).hashiya", directoryHint: .notDirectory)
        var ready = false
        defer {
            if !ready {
                try? FileManager.default.removeItem(at: copy)
            }
        }
        let read: ArchiveRead = await Task.detached {
            let scoped = source.startAccessingSecurityScopedResource()
            defer {
                if scoped {
                    source.stopAccessingSecurityScopedResource()
                }
            }
            do {
                try FileManager.default.copyItem(at: source, to: copy)
            } catch {
                return .invalid(.unreadable)
            }
            return readArchive(copy)
        }.value
        guard case let .valid(manifest, library) = read else {
            if case let .invalid(reason) = read {
                return .failed(reason)
            }
            return .failed(.unreadable)
        }
        var existing = 0
        var skipped = 0
        for paper in library.papers {
            guard let openAlexID = paper.usableOpenAlexID else {
                skipped += 1
                continue
            }
            if Task.isCancelled {
                return .failed(.unreadable)
            }
            // DOI matching only applies to papers without an OpenAlex id, and those are skipped.
            if let match = try? await store.matchFor(openAlexID: openAlexID, doi: nil), match != nil {
                existing += 1
            }
        }
        if Task.isCancelled {
            return .failed(.unreadable)
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

    public func apply(_ backup: PreparedBackup, onProgress: @escaping @Sendable (Double) -> Void) async throws -> RestoreResult {
        let claimed = applying.withLock { running -> Bool in
            if running {
                return false
            }
            running = true
            return true
        }
        guard claimed else {
            throw BackupError.busy
        }
        defer {
            applying.withLock { $0 = false }
        }
        let token = await background.begin(name: "Restore library", onExpiry: {})
        do {
            // Inside the gate: the startup sweep would delete the staged `.part` files, and the app waits for the gate
            // before it suspends the database.
            let result = try await pdfs.withStoreGate {
                try await self.restore(backup, onProgress: onProgress)
            }
            await token.end()
            return result
        } catch {
            await token.end()
            throw error
        }
    }

    /// Stages the backup's PDFs, merges it, then moves the PDFs the merge accepted into place.
    private func restore(_ backup: PreparedBackup, onProgress: @escaping @Sendable (Double) -> Void) async throws -> RestoreResult {
        // The app keys every paper by its OpenAlex id, so papers without one are left out with their PDFs and collection links.
        let restorable = backup.library.papers.compactMap { paper in
            paper.usableOpenAlexID.map { (paper: paper, openAlexID: $0) }
        }
        let refs = Set(restorable.map(\.paper.ref))
        var staged: [Int: (url: URL, size: Int64)] = [:]
        defer {
            for file in staged.values {
                try? FileManager.default.removeItem(at: file.url)
            }
        }
        var missing = 0
        let named = restorable.filter { $0.paper.pdf?.file != nil }
        do {
            let archive = try Archive(url: backup.url, accessMode: .read)
            for (index, item) in named.enumerated() {
                try Task.checkCancellation()
                // Only the paper's own entry name is ever read, so no entry can reach outside the PDF folder. An entry
                // declaring more than any PDF the app keeps is missing, checked before the space so a hostile size can't
                // stop the whole restore.
                let name = BackupFormat.pdfEntry(ref: item.paper.ref)
                if item.paper.pdf?.file == name, let entry = archive[name], entry.uncompressedSize <= UInt64(maxPdfBytes) {
                    if files.usableSpace() < Int64(entry.uncompressedSize) + Self.minFreeBytes {
                        throw BackupError.noSpace
                    }
                    if let file = try stagePdf(entry, from: archive) {
                        staged[item.paper.ref] = file
                    } else {
                        missing += 1
                    }
                } else {
                    missing += 1
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
            // IO errors and anything a malformed archive makes the zip reader throw.
            throw BackupError.unreadable
        }
        let papers = restorable.map { item in
            item.paper.toIncoming(localID: newID(), openAlexID: item.openAlexID, staged: staged[item.paper.ref])
        }
        let collections = backup.library.collections
            .filter { !trimmedCollectionName($0.name).isEmpty }
            .map { collection in
                IncomingCollection(
                    name: trimmedCollectionName(collection.name),
                    nameKey: collectionNameKey(collection.name),
                    createdAt: collection.createdAt,
                    refs: collection.papers.filter(refs.contains)
                )
            }
        await beforeMerge()
        try Task.checkCancellation()
        // Once the merge may have committed, its PDFs must land whatever happens to the caller. An unstructured task doesn't
        // inherit the caller's cancellation, and awaiting its value waits for it to finish.
        let toMove = staged
        staged = [:]
        let time = now()
        let moved = try await Task { [merge, files] in
            let outcome: MergeOutcome
            do {
                outcome = try await merge(papers, collections, time)
            } catch {
                for file in toMove.values {
                    try? FileManager.default.removeItem(at: file.url)
                }
                throw BackupError.writeFailed
            }
            var added = 0
            var failed = 0
            for (ref, file) in toMove {
                guard let target = outcome.pdfTargets[ref] else {
                    // The device already has a PDF for this paper.
                    try? FileManager.default.removeItem(at: file.url)
                    continue
                }
                do {
                    try files.commit(staged: file.url, paperID: target)
                    // A downloaded PDF can be fetched again, so it stays out of iCloud and device backups.
                    if papers.first(where: { $0.ref == ref })?.paper.pdfSource == "downloaded" {
                        files.setExcludedFromBackup(true, paperID: target)
                    }
                    added += 1
                } catch {
                    // A failed rename leaves the row without its file; the next startup sweep clears it.
                    try? FileManager.default.removeItem(at: file.url)
                    failed += 1
                }
            }
            return (outcome: outcome, added: added, failed: failed)
        }.value
        onProgress(1)
        return RestoreResult(
            papersAdded: moved.outcome.added,
            notesAdded: moved.outcome.notesAdded,
            collectionsCreated: moved.outcome.collectionsCreated,
            pdfsAdded: moved.added,
            pdfsMissing: missing + moved.failed,
            papersSkipped: backup.library.papers.count - restorable.count
        )
    }

    /// Copies the entry to a temporary file, counting bytes as they come out (never trusting the entry header), and stages
    /// it in the PDF folder. Nil when it is larger than `maxPdfBytes` or isn't a PDF.
    private func stagePdf(_ entry: Entry, from archive: Archive) throws -> (url: URL, size: Int64)? {
        struct TooLarge: Error {}
        let temp = workDirectory.appending(path: "entry-\(newID()).pdf", directoryHint: .notDirectory)
        defer {
            try? FileManager.default.removeItem(at: temp)
        }
        guard FileManager.default.createFile(atPath: temp.path, contents: nil),
              let handle = try? FileHandle(forWritingTo: temp)
        else {
            throw PdfWriteError()
        }
        defer {
            try? handle.close()
        }
        var total: Int64 = 0
        do {
            _ = try archive.extract(entry, skipCRC32: false) { chunk in
                total += Int64(chunk.count)
                if total > maxPdfBytes {
                    throw TooLarge()
                }
                do {
                    try handle.write(contentsOf: chunk)
                } catch {
                    throw PdfWriteError()
                }
            }
        } catch is TooLarge {
            return nil
        }
        switch try files.stage(prefix: "restore", copying: temp, maxBytes: maxPdfBytes) {
        case let .staged(url, size):
            return (url, size)
        case .notPDF, .tooLarge:
            return nil
        }
    }

    private func buildExport(includePdfs: Bool, onProgress: @escaping @Sendable (Double) -> Void) async throws -> ExportedFile {
        let snapshot: BackupSnapshot
        do {
            snapshot = try await store.backupSnapshot()
        } catch {
            throw BackupError.writeFailed
        }
        try prepareWorkDirectory()
        let time = now()
        // The save panel names the saved file after this one, so it carries the final name inside its own folder.
        let folder = workDirectory.appending(path: "export-\(newID())", directoryHint: .isDirectory)
        let fileName = BackupFormat.fileName(at: time)
        let url = folder.appending(path: fileName, directoryHint: .notDirectory)
        var succeeded = false
        defer {
            if !succeeded {
                try? FileManager.default.removeItem(at: folder)
            }
        }
        do {
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        } catch {
            throw BackupError.writeFailed
        }
        do {
            let written = try writeArchive(
                snapshot,
                includePdfs: includePdfs,
                pdfFile: files.file(paperID:),
                manifest: { [appVersion] papers, collections in
                    BackupManifest(
                        format: BackupFormat.version,
                        app: appVersion,
                        exportedAt: BackupFormat.isoUTC(time),
                        papers: papers,
                        collections: collections,
                        includesPdfs: includePdfs
                    )
                },
                to: url,
                onProgress: onProgress
            )
            try Task.checkCancellation()
            succeeded = true
            return ExportedFile(url: url, fileName: fileName, missingPdfs: written.missingPdfs)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw files.usableSpace() < Self.minFreeBytes ? BackupError.noSpace : BackupError.writeFailed
        }
    }

    /// Creates the work folder; the first time in a process, deletes what an earlier process left (a crash, a kill). No
    /// file of this process exists before then, and the app has one instance. All of it runs under the lock, so a second
    /// caller waits until the folder is ready instead of deleting the first one's files.
    func prepareWorkDirectory() throws {
        try workDirectoryReady.withLock { ready in
            if !ready {
                try? FileManager.default.removeItem(at: workDirectory)
            }
            do {
                try FileManager.default.createDirectory(at: workDirectory, withIntermediateDirectories: true)
            } catch {
                throw BackupError.writeFailed
            }
            ready = true
        }
    }
}
