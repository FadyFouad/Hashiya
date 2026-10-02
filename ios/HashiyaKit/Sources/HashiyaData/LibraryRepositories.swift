import Foundation
import HashiyaDatabase
import HashiyaNetwork

/// The library, collections, citations and PDFs on one database store, so they share one connection pool and see each
/// other's writes at once.
public struct LibraryRepositories: Sendable {
    public let library: GRDBLibraryRepository
    public let collections: GRDBCollectionsRepository
    public let citations: GRDBCitationRepository
    public let pdfs: GRDBPdfRepository

    init(store: PaperStore, lookup: any OpenAlexLookupService, pdf: PdfDependencies) {
        library = GRDBLibraryRepository(store: store)
        collections = GRDBCollectionsRepository(store: store)
        citations = GRDBCitationRepository(store: store, lookup: lookup)
        pdfs = GRDBPdfRepository(
            store: store,
            files: pdf.files,
            downloader: pdf.downloader,
            pdfLinks: pdf.pdfLinks,
            background: pdf.background
        )
    }

    /// The App Group database file `fileName`; `fresh` deletes it, and the PDF folder, first (UI tests only). With no
    /// `lookup`, papers are never refetched: they count as complete with what is stored.
    public static func shared(
        fileName: String = HashiyaDatabase.fileName,
        fresh: Bool = false,
        lookup: (any OpenAlexLookupService)? = nil,
        pdf: PdfDependencies
    ) throws -> LibraryRepositories {
        let url = try HashiyaDatabase.sharedDatabaseURL(fileName: fileName)
        if fresh {
            try HashiyaDatabase.removeDatabase(at: url)
            try? FileManager.default.removeItem(at: pdf.files.directory)
        }
        return LibraryRepositories(store: try PaperStore.open(at: url), lookup: lookup ?? OfflineLookupService(), pdf: pdf)
    }
}
