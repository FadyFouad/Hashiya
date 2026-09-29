import Foundation
import HashiyaDatabase
import HashiyaModel

public protocol LibraryRepository: Sendable {
    /// Saved papers, newest saved first. Each call returns a new stream starting with the current value.
    func observeSavedPapers() -> AsyncStream<[Paper]>
    /// The OpenAlex IDs in the library. Each call returns a new stream starting with the current value.
    func observeSavedIDs() -> AsyncStream<Set<String>>
    /// Already saved → no-op.
    func save(_ paper: Paper) async throws
    /// Nil if the paper was not saved.
    func remove(openAlexID: String) async throws -> RemovedPaper?
    /// Puts a removed paper back with the same local ID and saved time. No-op if it was saved again meanwhile.
    func restore(_ removed: RemovedPaper) async throws
}

/// What `remove` deleted, so Undo can put it back in the same place.
public struct RemovedPaper: Equatable, Sendable {
    public var paper: Paper
    public var localID: String
    public var savedAt: Int64

    public init(paper: Paper, localID: String, savedAt: Int64) {
        self.paper = paper
        self.localID = localID
        self.savedAt = savedAt
    }
}

public struct GRDBLibraryRepository: LibraryRepository {
    private let store: PaperStore
    private let now: @Sendable () -> Int64
    private let newID: @Sendable () -> String

    /// - Parameters:
    ///   - now: epoch milliseconds.
    ///   - newID: a new lowercase UUID string.
    public init(
        store: PaperStore,
        now: @escaping @Sendable () -> Int64 = { Int64((Date().timeIntervalSince1970 * 1000).rounded()) },
        newID: @escaping @Sendable () -> String = { UUID().uuidString.lowercased() }
    ) {
        self.store = store
        self.now = now
        self.newID = newID
    }

    /// A repository on a fresh in-memory database (tests and UI-test launches).
    public static func inMemory(
        now: @escaping @Sendable () -> Int64 = { Int64((Date().timeIntervalSince1970 * 1000).rounded()) },
        newID: @escaping @Sendable () -> String = { UUID().uuidString.lowercased() }
    ) throws -> GRDBLibraryRepository {
        GRDBLibraryRepository(store: try PaperStore.inMemory(), now: now, newID: newID)
    }

    public func observeSavedPapers() -> AsyncStream<[Paper]> {
        store.observeSavedPapers().mapped { rows in rows.map { $0.asPaper() } }
    }

    public func observeSavedIDs() -> AsyncStream<Set<String>> {
        store.observeSavedOpenAlexIDs()
    }

    public func save(_ paper: Paper) async throws {
        let records = paper.asRecords(localID: newID(), savedAt: now())
        try await store.insert(paper: records.paper, authors: records.authors)
    }

    public func remove(openAlexID: String) async throws -> RemovedPaper? {
        guard let deleted = try await store.deleteByOpenAlexID(openAlexID) else { return nil }
        return RemovedPaper(paper: deleted.asPaper(), localID: deleted.paper.id, savedAt: deleted.paper.savedAt)
    }

    public func restore(_ removed: RemovedPaper) async throws {
        let records = removed.paper.asRecords(localID: removed.localID, savedAt: removed.savedAt)
        try await store.insert(paper: records.paper, authors: records.authors)
    }
}

extension AsyncStream where Element: Sendable {
    /// A stream of `transform(element)`; cancelling it cancels the iteration of `self`.
    func mapped<T: Sendable>(_ transform: @escaping @Sendable (Element) -> T) -> AsyncStream<T> {
        AsyncStream<T> { continuation in
            let task = Task {
                for await element in self {
                    continuation.yield(transform(element))
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}
