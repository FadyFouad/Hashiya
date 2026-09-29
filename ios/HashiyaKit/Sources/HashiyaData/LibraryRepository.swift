import Foundation
import HashiyaDatabase
import HashiyaModel

/// The Library for one search and status filter, read in one go so its parts always describe the same moment.
public struct LibrarySnapshot: Equatable, Sendable {
    /// Papers matching the search and the status, newest saved first.
    public var papers: [LibraryPaper]
    /// Papers matching the search (whatever their status) per status; all three keys are present.
    public var counts: [ReadingStatus: Int]
    /// Every saved paper, ignoring the search and the status.
    public var libraryTotal: Int

    /// Papers matching the search: the All chip.
    public var matchingTotal: Int { counts.values.reduce(0, +) }

    public init(papers: [LibraryPaper], counts: [ReadingStatus: Int], libraryTotal: Int) {
        self.papers = papers
        self.counts = counts
        self.libraryTotal = libraryTotal
    }
}

public protocol LibraryRepository: Sendable {
    /// One consistent snapshot per database change. Blank query = all; nil status = all. Each call returns a new
    /// stream starting with the current value.
    func observeLibrary(query: String, status: ReadingStatus?) -> AsyncStream<LibrarySnapshot>
    /// The OpenAlex IDs in the library. Each call returns a new stream starting with the current value.
    func observeSavedIDs() -> AsyncStream<Set<String>>
    /// Starts as To read. Already saved → no-op.
    func save(_ paper: Paper) async throws
    /// Doesn't reorder. Not saved → no-op.
    func setStatus(openAlexID: String, status: ReadingStatus) async throws
    /// Nil if the paper was not saved.
    func remove(openAlexID: String) async throws -> RemovedPaper?
    /// Puts a removed paper back with the same local ID, saved time and status. No-op if it was saved again meanwhile.
    func restore(_ removed: RemovedPaper) async throws
    /// Makes every observation fetch again, so papers saved by the Share Extension appear. Failures are ignored.
    func refreshAfterExternalChanges() async
}

/// What `remove` deleted, so Undo can put it back in the same place with the same status.
public struct RemovedPaper: Equatable, Sendable {
    public var paper: Paper
    public var localID: String
    public var savedAt: Int64
    public var status: ReadingStatus

    public init(paper: Paper, localID: String, savedAt: Int64, status: ReadingStatus) {
        self.paper = paper
        self.localID = localID
        self.savedAt = savedAt
        self.status = status
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

    /// The library in the App Group database file `fileName`; `fresh` deletes that file first (UI tests only).
    public static func shared(fileName: String = HashiyaDatabase.fileName, fresh: Bool = false) throws -> GRDBLibraryRepository {
        let url = try HashiyaDatabase.sharedDatabaseURL(fileName: fileName)
        if fresh { try HashiyaDatabase.removeDatabase(at: url) }
        return GRDBLibraryRepository(store: try PaperStore.open(at: url))
    }

    /// A repository on a fresh in-memory database (tests and UI-test launches).
    public static func inMemory(
        now: @escaping @Sendable () -> Int64 = { Int64((Date().timeIntervalSince1970 * 1000).rounded()) },
        newID: @escaping @Sendable () -> String = { UUID().uuidString.lowercased() }
    ) throws -> GRDBLibraryRepository {
        GRDBLibraryRepository(store: try PaperStore.inMemory(), now: now, newID: newID)
    }

    public func observeLibrary(query: String, status: ReadingStatus?) -> AsyncStream<LibrarySnapshot> {
        store.observeLibrary(match: ftsMatch(query), status: status?.storedValue).mapped { $0.asSnapshot() }
    }

    public func observeSavedIDs() -> AsyncStream<Set<String>> {
        store.observeSavedOpenAlexIDs()
    }

    public func save(_ paper: Paper) async throws {
        let records = paper.asRecords(localID: newID(), savedAt: now())
        try await store.insert(paper: records.paper, authors: records.authors, search: records.searchRow)
    }

    public func setStatus(openAlexID: String, status: ReadingStatus) async throws {
        try await store.setStatus(openAlexID: openAlexID, status: status.storedValue)
    }

    public func remove(openAlexID: String) async throws -> RemovedPaper? {
        guard let deleted = try await store.deleteByOpenAlexID(openAlexID) else { return nil }
        let saved = deleted.asLibraryPaper()
        return RemovedPaper(paper: saved.paper, localID: deleted.paper.id, savedAt: deleted.paper.savedAt, status: saved.status)
    }

    public func restore(_ removed: RemovedPaper) async throws {
        let records = removed.paper.asRecords(localID: removed.localID, savedAt: removed.savedAt, status: removed.status)
        try await store.insert(paper: records.paper, authors: records.authors, search: records.searchRow)
    }

    public func refreshAfterExternalChanges() async {
        try? await store.notifyExternalChanges()
    }
}

extension LibraryRows {
    /// Every stored status mapped to its `ReadingStatus` (unknown values count as To read); missing statuses are 0.
    func asSnapshot() -> LibrarySnapshot {
        var counts = Dictionary(uniqueKeysWithValues: ReadingStatus.allCases.map { ($0, 0) })
        for (stored, count) in statusCounts {
            counts[ReadingStatus(stored: stored), default: 0] += count
        }
        return LibrarySnapshot(papers: papers.map { $0.asLibraryPaper() }, counts: counts, libraryTotal: total)
    }
}

/// The App Group database's lifecycle for the app and the Share Extension, which never import GRDB.
public enum SharedLibraryDatabase {
    /// Call before the process is suspended; see `HashiyaDatabase.suspend()`.
    public static func suspend() {
        HashiyaDatabase.suspend()
    }

    /// Call when the process is active again.
    public static func resume() {
        HashiyaDatabase.resume()
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
