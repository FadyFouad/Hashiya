import Foundation

/// What a backup would hold right now.
public struct BackupSummary: Equatable, Sendable {
    public var papers: Int
    public var collections: Int
    public var pdfCount: Int
    public var pdfBytes: Int64

    public init(papers: Int, collections: Int, pdfCount: Int, pdfBytes: Int64) {
        self.papers = papers
        self.collections = collections
        self.pdfCount = pdfCount
        self.pdfBytes = pdfBytes
    }
}

/// A finished export. `url` is in the work folder under `fileName`; the UI moves it to where the user chooses.
public struct ExportedFile: Equatable, Sendable {
    public let url: URL
    public let fileName: String
    /// Stored PDFs whose file was gone, so they are not in the archive.
    public let missingPdfs: Int

    public init(url: URL, fileName: String, missingPdfs: Int) {
        self.url = url
        self.fileName = fileName
        self.missingPdfs = missingPdfs
    }
}

public enum BackupError: Error, Equatable, Sendable {
    case noSpace
    case writeFailed
    case unreadable
    case busy
}

public enum OpenResult: Equatable, Sendable {
    case ready(PreparedBackup, RestorePreview)
    case failed(OpenFailure)
}

public enum OpenFailure: Equatable, Sendable {
    case notABackup
    case newerFormat
    case damaged
    case unreadable
}

/// What restoring a backup would do. `exportedAt` is nil when the manifest's date doesn't parse. `papersSkipped` counts
/// papers without an OpenAlex id.
public struct RestorePreview: Equatable, Sendable {
    public var exportedAt: Int64?
    public var papers: Int
    public var collections: Int
    public var pdfs: Int
    public var newPapers: Int
    public var existingPapers: Int
    public var papersSkipped: Int

    public init(exportedAt: Int64?, papers: Int, collections: Int, pdfs: Int, newPapers: Int, existingPapers: Int, papersSkipped: Int) {
        self.exportedAt = exportedAt
        self.papers = papers
        self.collections = collections
        self.pdfs = pdfs
        self.newPapers = newPapers
        self.existingPapers = existingPapers
        self.papersSkipped = papersSkipped
    }
}

/// A backup copied into the work folder and checked. Give it back to `discard` when it isn't restored.
public struct PreparedBackup: Equatable, Sendable {
    let url: URL
    let library: BackupLibrary

    init(url: URL, library: BackupLibrary) {
        self.url = url
        self.library = library
    }
}

/// Backing the library up to a `.hashiya` file and restoring from one.
public protocol LibraryBackup: Sendable {
    func summary() async throws -> BackupSummary

    /// Builds the archive in a temporary folder.
    /// - Throws: `BackupError`, or `CancellationError` when cancelled (nothing is left behind).
    func export(includePdfs: Bool, onProgress: @escaping @Sendable (Double) -> Void) async throws -> ExportedFile

    /// Deletes the export's temporary folder; call it when the user is done with the file or cancels.
    func discard(_ exported: ExportedFile)

    /// Copies `source` into the work folder (security-scoped access is handled here) and checks it. The copy is deleted on
    /// every outcome except `.ready`.
    func open(_ source: URL) async -> OpenResult

    /// Deletes the open copy; call it when the user cancels or after a restore.
    func discard(_ backup: PreparedBackup)
}
