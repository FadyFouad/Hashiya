import Foundation
import HashiyaDatabase
import HashiyaNetwork

/// The long-lived objects of the app (and, in spec 2, of the Share Extension), built one way.
public struct LiveDependencies: Sendable {
    public let libraryRepository: any LibraryRepository
    public let searchRepository: any SearchRepository
    public let lookupRepository: any PaperLookupRepository
    public let preferences: any UserPreferencesRepository
    public let collections: any CollectionsRepository
    public let citations: any CitationRepository
    public let exportFiles: ExportFiles
    public let pdfs: any PdfRepository

    public init(
        libraryRepository: any LibraryRepository,
        searchRepository: any SearchRepository,
        lookupRepository: any PaperLookupRepository,
        preferences: any UserPreferencesRepository,
        collections: any CollectionsRepository,
        citations: any CitationRepository,
        exportFiles: ExportFiles,
        pdfs: any PdfRepository
    ) {
        self.libraryRepository = libraryRepository
        self.searchRepository = searchRepository
        self.lookupRepository = lookupRepository
        self.preferences = preferences
        self.collections = collections
        self.citations = citations
        self.exportFiles = exportFiles
        self.pdfs = pdfs
    }

    /// The real graph: the App Group database (one store for the library, collections, citations and PDFs), the Keychain,
    /// OpenAlex over one URLSession, arXiv over its own and PDFs over a third. Reads `OpenAlexAPIKey` and
    /// `KeychainAccessGroup` from `bundle`'s Info.plist. `background` is the app's `UIKitBackgroundTime`; the Share
    /// Extension keeps the default and never downloads.
    public static func live(bundle: Bundle = .main, background: any BackgroundTimeGranting = NoBackgroundTime()) throws -> LiveDependencies {
        let preferences = KeychainUserPreferencesRepository(
            keychain: SystemKeychainStore(accessGroup: infoValue(bundle.object(forInfoDictionaryKey: "KeychainAccessGroup")))
        )
        let session = OpenAlexSession.make()
        let builtInKey = builtInAPIKey(from: bundle.object(forInfoDictionaryKey: "OpenAlexAPIKey"))
        let searchClient = OpenAlexSearchClient(session: session, builtInKey: builtInKey, userKeySource: preferences)
        let lookupClient = OpenAlexLookupClient(session: session, builtInKey: builtInKey, userKeySource: preferences)
        // The one PDF client of the process: its session lives as long as the app and is never invalidated.
        let pdf = PdfDependencies(files: try PdfFileStore.live(), downloader: PdfDownloadClient(), background: background)
        let repositories = LibraryRepositories(store: try PaperStore.shared(), lookup: lookupClient, pdf: pdf)
        return LiveDependencies(
            libraryRepository: repositories.library,
            searchRepository: OpenAlexSearchRepository(service: searchClient),
            lookupRepository: OpenAlexPaperLookupRepository(openAlex: lookupClient, arxiv: ArxivTitleClient()),
            preferences: preferences,
            collections: repositories.collections,
            citations: repositories.citations,
            exportFiles: .live,
            pdfs: repositories.pdfs
        )
    }

    /// The built-in key from Info.plist's `OpenAlexAPIKey`. Empty, or the unexpanded `$(OPENALEX_API_KEY)`
    /// when Secrets.xcconfig is missing, means there is none.
    public static func builtInAPIKey(from infoValue: Any?) -> String? {
        Self.infoValue(infoValue)
    }

    private static func infoValue(_ value: Any?) -> String? {
        guard let text = (value as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !text.isEmpty, !text.hasPrefix("$(") else { return nil }
        return text
    }
}
