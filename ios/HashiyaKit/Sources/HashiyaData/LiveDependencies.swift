import Foundation
import HashiyaDatabase
import HashiyaNetwork

/// The long-lived objects of the app (and, in spec 2, of the Share Extension), built one way.
public struct LiveDependencies: Sendable {
    public let libraryRepository: any LibraryRepository
    public let searchRepository: any SearchRepository
    public let lookupRepository: any PaperLookupRepository
    public let preferences: any UserPreferencesRepository

    public init(
        libraryRepository: any LibraryRepository,
        searchRepository: any SearchRepository,
        lookupRepository: any PaperLookupRepository,
        preferences: any UserPreferencesRepository
    ) {
        self.libraryRepository = libraryRepository
        self.searchRepository = searchRepository
        self.lookupRepository = lookupRepository
        self.preferences = preferences
    }

    /// The real graph: the App Group database, the Keychain, OpenAlex over one URLSession and arXiv over its own.
    /// Reads `OpenAlexAPIKey` and `KeychainAccessGroup` from `bundle`'s Info.plist.
    public static func live(bundle: Bundle = .main) throws -> LiveDependencies {
        let preferences = KeychainUserPreferencesRepository(
            keychain: SystemKeychainStore(accessGroup: infoValue(bundle.object(forInfoDictionaryKey: "KeychainAccessGroup")))
        )
        let session = OpenAlexSession.make()
        let builtInKey = builtInAPIKey(from: bundle.object(forInfoDictionaryKey: "OpenAlexAPIKey"))
        let searchClient = OpenAlexSearchClient(session: session, builtInKey: builtInKey, userKeySource: preferences)
        let lookupClient = OpenAlexLookupClient(session: session, builtInKey: builtInKey, userKeySource: preferences)
        return LiveDependencies(
            libraryRepository: GRDBLibraryRepository(store: try PaperStore.shared()),
            searchRepository: OpenAlexSearchRepository(service: searchClient),
            lookupRepository: OpenAlexPaperLookupRepository(openAlex: lookupClient, arxiv: ArxivTitleClient()),
            preferences: preferences
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
