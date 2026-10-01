import Foundation
import HashiyaDatabase
import HashiyaNetwork

/// The library, collections and citations on one database store, so they share one connection pool and see each other's
/// writes at once.
public struct LibraryRepositories: Sendable {
    public let library: GRDBLibraryRepository
    public let collections: GRDBCollectionsRepository
    public let citations: GRDBCitationRepository

    init(store: PaperStore, lookup: any OpenAlexLookupService) {
        library = GRDBLibraryRepository(store: store)
        collections = GRDBCollectionsRepository(store: store)
        citations = GRDBCitationRepository(store: store, lookup: lookup)
    }

    /// The App Group database file `fileName`; `fresh` deletes it first (UI tests only). With no `lookup`, papers are never
    /// refetched: they count as complete with what is stored.
    public static func shared(
        fileName: String = HashiyaDatabase.fileName,
        fresh: Bool = false,
        lookup: (any OpenAlexLookupService)? = nil
    ) throws -> LibraryRepositories {
        let url = try HashiyaDatabase.sharedDatabaseURL(fileName: fileName)
        if fresh { try HashiyaDatabase.removeDatabase(at: url) }
        return LibraryRepositories(store: try PaperStore.open(at: url), lookup: lookup ?? OfflineLookupService())
    }
}
