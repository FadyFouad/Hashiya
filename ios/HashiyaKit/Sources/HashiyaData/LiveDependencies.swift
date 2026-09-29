import Foundation
import HashiyaDatabase
import HashiyaNetwork

/// The long-lived objects of the app (and, in spec 2, of the Share Extension), built one way.
public struct LiveDependencies: Sendable {
    public let libraryRepository: any LibraryRepository
    public let searchRepository: any SearchRepository
    public let preferences: any UserPreferencesRepository

    public init(
        libraryRepository: any LibraryRepository,
        searchRepository: any SearchRepository,
        preferences: any UserPreferencesRepository
    ) {
        self.libraryRepository = libraryRepository
        self.searchRepository = searchRepository
        self.preferences = preferences
    }

    /// The real graph: the App Group database, the Keychain and OpenAlex over URLSession.
    /// Reads `OpenAlexAPIKey` and `KeychainAccessGroup` from `bundle`'s Info.plist.
    public static func live(bundle: Bundle = .main) throws -> LiveDependencies {
        let preferences = KeychainUserPreferencesRepository(
            keychain: SystemKeychainStore(accessGroup: infoValue(bundle.object(forInfoDictionaryKey: "KeychainAccessGroup")))
        )
        let client = OpenAlexSearchClient(
            session: OpenAlexSession.make(),
            builtInKey: builtInAPIKey(from: bundle.object(forInfoDictionaryKey: "OpenAlexAPIKey")),
            userKeySource: preferences
        )
        return LiveDependencies(
            libraryRepository: GRDBLibraryRepository(store: try PaperStore.shared()),
            searchRepository: OpenAlexSearchRepository(service: client),
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
