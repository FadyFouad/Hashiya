/// Where a saved paper's PDF came from. Stored as its raw value in `papers.pdf_source`.
public enum PdfSource: String, Sendable, Equatable {
    /// Fetched from the paper's open-access link; it can be downloaded again.
    case downloaded
    /// Picked by the user from Files; it can't be fetched again, so "delete downloaded PDFs" keeps it.
    case attached
}

/// The PDF stored for a saved paper.
public struct PaperPdf: Equatable, Hashable, Sendable {
    public var source: PdfSource
    public var sizeBytes: Int64
    /// Epoch milliseconds.
    public var addedAt: Int64
    /// Zero-based page the reader last showed; 0 for a new file.
    public var lastPage: Int

    public init(source: PdfSource, sizeBytes: Int64, addedAt: Int64, lastPage: Int = 0) {
        self.source = source
        self.sizeBytes = sizeBytes
        self.addedAt = addedAt
        self.lastPage = lastPage
    }
}

/// The space stored PDFs take, by source. Settings shows it.
public struct PdfStorage: Equatable, Sendable {
    public var downloadedBytes: Int64
    public var downloadedCount: Int
    public var attachedBytes: Int64
    public var attachedCount: Int

    public init(downloadedBytes: Int64, downloadedCount: Int, attachedBytes: Int64, attachedCount: Int) {
        self.downloadedBytes = downloadedBytes
        self.downloadedCount = downloadedCount
        self.attachedBytes = attachedBytes
        self.attachedCount = attachedCount
    }

    public static let empty = PdfStorage(downloadedBytes: 0, downloadedCount: 0, attachedBytes: 0, attachedCount: 0)
}
