import Foundation
import HashiyaDiagnostics
import HashiyaModel
import HashiyaNetwork

/// Why a download stopped without a PDF.
public enum DownloadFailure: Sendable, Equatable {
    case offline
    /// The link led to a web page or another file type.
    case notPDF
    case tooLarge
    /// An error status, a timeout, an unusable link, or the file couldn't be written.
    case http
    /// The paper has no open-access link (any more).
    case noLink
}

/// A running or failed download. A finished or cancelled download has no state.
public enum DownloadState: Equatable, Sendable {
    case running(bytes: Int64, total: Int64?)
    case failed(DownloadFailure)
}

public enum AttachResult: Sendable, Equatable {
    case done
    case notPDF
    case tooLarge
    /// The file couldn't be read, or the paper isn't saved.
    case unreadable
}

/// Each saved paper's one PDF: downloaded from its open-access link or attached from a file, stored on the device.
public protocol PdfRepository: Sendable {
    /// The paper's stored PDF; nil when it has none. Each call returns a new stream starting with the current value.
    func observePdf(openAlexID: String) -> AsyncStream<PaperPdf?>
    /// The paper's running or failed download; nil when there is none. Each call returns a new stream starting with the
    /// current value.
    func observeDownload(openAlexID: String) -> AsyncStream<DownloadState?>
    /// Starts downloading the paper's open-access PDF. Does nothing while one is already running.
    func download(openAlexID: String)
    func cancelDownload(openAlexID: String)
    /// Copies the PDF at `url` (a file picked in Files) in, replacing the current one. Cancels a running download first.
    func attach(openAlexID: String, from url: URL) async -> AttachResult
    /// Deletes the PDF. Cancels a running download first.
    func remove(openAlexID: String) async throws
    func setLastPage(openAlexID: String, page: Int) async throws
    /// The stored file; nil when the paper has no PDF or its file is missing.
    func pdfFile(openAlexID: String) async -> URL?
    func storage() async throws -> PdfStorage
    /// Deletes every downloaded PDF; attached ones stay.
    func deleteDownloaded() async throws
    /// Deletes a removed paper's file once its removal is final (the Undo banner went away), unless Undo put it back.
    func discardRemoved(_ removed: RemovedPaper) async
    /// Deletes files no saved paper owns and `.part` files left by a store that never finished, clears the PDF of papers whose
    /// file is gone, and marks downloaded PDFs excluded from backups. Called once at launch.
    /// Waits for downloads and attaches that are writing a file, and holds new ones back until it is done, since a running
    /// store's `.part` file looks just like an abandoned one.
    func sweepOrphans() async
    /// Returns once no download or attach is running, including ones started while it waits. The app waits for this
    /// before it suspends the shared database in the background, which would refuse their writes: a download that
    /// finishes on its background time would otherwise lose its file.
    func storesFinished() async
}

/// What `LibraryRepositories` needs to build the PDF repository.
public struct PdfDependencies: Sendable {
    public let files: PdfFileStore
    public let downloader: any PdfDownloading
    public let background: any BackgroundTimeGranting
    /// OpenAlex's other links for a paper whose stored link fails. The default knows none.
    public let pdfLinks: any OpenAlexPdfLinksService
    /// Where the PDF store's failures are reported (the repository starts using it with the PDF reports).
    public let crash: any CrashReporting

    public init(
        files: PdfFileStore,
        downloader: any PdfDownloading,
        background: any BackgroundTimeGranting,
        pdfLinks: any OpenAlexPdfLinksService = NoPdfLinks(),
        crash: any CrashReporting = NoCrashReporting()
    ) {
        self.files = files
        self.downloader = downloader
        self.background = background
        self.pdfLinks = pdfLinks
        self.crash = crash
    }
}

/// Knows no other links: a failed download reports its own failure. For the UI tests and the Share Extension.
public struct NoPdfLinks: OpenAlexPdfLinksService {
    public init() {}

    public func pdfLocations(openAlexID: String) async throws -> [NetworkLocation] { [] }
}
