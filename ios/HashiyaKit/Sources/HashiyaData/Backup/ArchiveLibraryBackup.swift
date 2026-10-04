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

    static let minFreeBytes: Int64 = 10 * 1024 * 1024

    public init(
        store: PaperStore,
        pdfs: GRDBPdfRepository,
        files: PdfFileStore,
        workDirectory: URL,
        appVersion: String,
        background: any BackgroundTimeGranting,
        now: @escaping @Sendable () -> Int64,
        newID: @escaping @Sendable () -> String
    ) {
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
