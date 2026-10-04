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

/// Backing the library up to a `.hashiya` file and restoring from one.
public protocol LibraryBackup: Sendable {
    func summary() async throws -> BackupSummary

    /// Builds the archive in a temporary folder.
    /// - Throws: `BackupError`, or `CancellationError` when cancelled (nothing is left behind).
    func export(includePdfs: Bool, onProgress: @escaping @Sendable (Double) -> Void) async throws -> ExportedFile

    /// Deletes the export's temporary folder; call it when the user is done with the file or cancels.
    func discard(_ exported: ExportedFile)
}
