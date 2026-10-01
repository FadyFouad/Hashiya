import Foundation
import HashiyaDatabase
import HashiyaModel

/// The outcome of creating or renaming a collection.
public enum CollectionResult: Equatable, Sendable {
    case done(id: Int64)
    /// Another collection has the same name, ignoring case and surrounding whitespace.
    case nameTaken
    /// Blank, or longer than `collectionNameMaxLength` characters, after trimming.
    case invalidName
    /// Rename only: the collection was deleted.
    case notFound
}

public protocol CollectionsRepository: Sendable {
    /// Every collection with its paper count, sorted by name ignoring case. Each call returns a new stream starting with the
    /// current value.
    func observeCollections() -> AsyncStream<[PaperCollection]>
    /// The collections a paper is in; empty when it is in none or isn't saved.
    func observeCollectionIDs(openAlexID: String) -> AsyncStream<Set<Int64>>
    /// Trims the name. `.invalidName` unless it is valid; `.nameTaken` if another collection has it.
    func create(name: String) async throws -> CollectionResult
    /// As `create`, and `.notFound` when the collection was deleted.
    func rename(id: Int64, name: String) async throws -> CollectionResult
    /// Deletes the collection and its memberships, never its papers.
    func delete(id: Int64) async throws
    /// Adds or removes the paper. Adding an unsaved paper, or to a deleted collection, does nothing.
    func setMembership(collectionID: Int64, openAlexID: String, member: Bool) async throws
}

public struct GRDBCollectionsRepository: CollectionsRepository {
    private let store: PaperStore
    private let now: @Sendable () -> Int64

    /// - Parameter now: epoch milliseconds.
    public init(
        store: PaperStore,
        now: @escaping @Sendable () -> Int64 = { Int64((Date().timeIntervalSince1970 * 1000).rounded()) }
    ) {
        self.store = store
        self.now = now
    }

    public func observeCollections() -> AsyncStream<[PaperCollection]> {
        store.observeCollections().mapped { rows in
            rows.map { PaperCollection(id: $0.id, name: $0.name, paperCount: $0.paperCount) }
        }
    }

    public func observeCollectionIDs(openAlexID: String) -> AsyncStream<Set<Int64>> {
        store.observeCollectionIDs(openAlexID: openAlexID)
    }

    public func create(name: String) async throws -> CollectionResult {
        guard isValidCollectionName(name) else { return .invalidName }
        guard let id = try await store.insertCollection(
            name: trimmedCollectionName(name), nameKey: collectionNameKey(name), createdAt: now()
        ) else { return .nameTaken }
        return .done(id: id)
    }

    public func rename(id: Int64, name: String) async throws -> CollectionResult {
        guard isValidCollectionName(name) else { return .invalidName }
        if try await store.renameCollection(id: id, name: trimmedCollectionName(name), nameKey: collectionNameKey(name)) {
            return .done(id: id)
        }
        return try await store.collectionExists(id: id) ? .nameTaken : .notFound
    }

    public func delete(id: Int64) async throws {
        try await store.deleteCollection(id: id)
    }

    public func setMembership(collectionID: Int64, openAlexID: String, member: Bool) async throws {
        if member {
            try await store.addToCollection(collectionID: collectionID, openAlexID: openAlexID, addedAt: now())
        } else {
            try await store.removeFromCollection(collectionID: collectionID, openAlexID: openAlexID)
        }
    }
}
