import Foundation
import HashiyaDatabase
import HashiyaModel

/// The Library for one search, status filter and collection, read in one go so its parts always describe the same moment.
public struct LibrarySnapshot: Equatable, Sendable {
    /// Papers matching the search and the status, newest saved first.
    public var papers: [LibraryPaper]
    /// Papers matching the search (whatever their status) per status; all three keys are present.
    public var counts: [ReadingStatus: Int]
    /// Every paper in the current view (the collection, or the whole library), ignoring the search and the status.
    public var libraryTotal: Int
    /// Every saved paper, whatever the collection, search and status.
    public var allPapersTotal: Int

    /// Papers matching the search: the All chip.
    public var matchingTotal: Int { counts.values.reduce(0, +) }

    /// `allPapersTotal` nil means the view is the whole library, so it equals `libraryTotal`.
    public init(papers: [LibraryPaper], counts: [ReadingStatus: Int], libraryTotal: Int, allPapersTotal: Int? = nil) {
        self.papers = papers
        self.counts = counts
        self.libraryTotal = libraryTotal
        self.allPapersTotal = allPapersTotal ?? libraryTotal
    }
}

public protocol LibraryRepository: Sendable {
    /// One consistent snapshot per database change. Blank query = all; nil status = all; nil collection = all papers.
    /// Each call returns a new stream starting with the current value.
    func observeLibrary(query: String, status: ReadingStatus?, collectionID: Int64?) -> AsyncStream<LibrarySnapshot>
    /// The OpenAlex IDs in the library. Each call returns a new stream starting with the current value.
    func observeSavedIDs() -> AsyncStream<Set<String>>
    /// Starts as To read, with its publication details marked fetched. Already saved → no-op.
    func save(_ paper: Paper) async throws
    /// Doesn't reorder. Not saved → no-op.
    func setStatus(openAlexID: String, status: ReadingStatus) async throws
    /// Nil if the paper was not saved. The result carries its collections and cite key for Undo.
    func remove(openAlexID: String) async throws -> RemovedPaper?
    /// Puts a removed paper back with the same local ID, saved time, status, notes, cite key and collections. Collections
    /// deleted meanwhile are skipped, and a cite key another paper took meanwhile is dropped. No-op if it was saved again
    /// meanwhile.
    func restore(_ removed: RemovedPaper) async throws
    /// Makes every observation fetch again, so papers saved by the Share Extension appear. Failures are ignored.
    func refreshAfterExternalChanges() async
    /// The saved paper with its status; nil when it isn't saved or stops being saved. Each call returns a new stream
    /// starting with the current value.
    func observePaper(openAlexID: String) -> AsyncStream<LibraryPaper?>
    /// The paper's notes, read once; empty when it has none or isn't saved.
    func notes(openAlexID: String) async throws -> PaperNotes
    /// Saves the notes (blank notes delete them) and updates the search index. Not saved → no-op.
    func saveNotes(openAlexID: String, notes: PaperNotes) async throws
}

extension LibraryRepository {
    /// The whole library: no collection.
    public func observeLibrary(query: String, status: ReadingStatus?) -> AsyncStream<LibrarySnapshot> {
        observeLibrary(query: query, status: status, collectionID: nil)
    }
}

/// What `remove` deleted, so Undo can put it back in the same place with the same status, notes, cite key and collections.
public struct RemovedPaper: Equatable, Sendable {
    public var paper: Paper
    public var localID: String
    public var savedAt: Int64
    public var status: ReadingStatus
    public var notes: PaperNotes
    public var collectionIDs: Set<Int64>
    public var citeKey: String?
    /// False for a paper saved before v4 whose details were never refetched.
    public var detailsFetched: Bool
    /// When the paper was added to each of `collectionIDs`; restored as-is.
    public var collectionLinksAddedAt: [Int64: Int64]
    /// The paper's PDF, put back on restore; the file itself stays on disk until `PdfRepository.discardRemoved`.
    public var pdf: PaperPdf?

    public init(
        paper: Paper,
        localID: String,
        savedAt: Int64,
        status: ReadingStatus,
        notes: PaperNotes = PaperNotes(),
        collectionIDs: Set<Int64> = [],
        citeKey: String? = nil,
        detailsFetched: Bool = true,
        collectionLinksAddedAt: [Int64: Int64] = [:],
        pdf: PaperPdf? = nil
    ) {
        self.paper = paper
        self.localID = localID
        self.savedAt = savedAt
        self.status = status
        self.notes = notes
        self.collectionIDs = collectionIDs
        self.citeKey = citeKey
        self.detailsFetched = detailsFetched
        self.collectionLinksAddedAt = collectionLinksAddedAt
        self.pdf = pdf
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

    public func observeLibrary(query: String, status: ReadingStatus?, collectionID: Int64?) -> AsyncStream<LibrarySnapshot> {
        store.observeLibrary(match: ftsMatch(query), status: status?.storedValue, collectionID: collectionID).mapped { $0.asSnapshot() }
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
        let saved = deleted.saved.asLibraryPaper()
        return RemovedPaper(
            paper: saved.paper,
            localID: deleted.saved.paper.id,
            savedAt: deleted.saved.paper.savedAt,
            status: saved.status,
            notes: deleted.notes?.notes ?? PaperNotes(),
            collectionIDs: Set(deleted.collectionLinks.map(\.collectionID)),
            citeKey: deleted.saved.paper.citeKey,
            detailsFetched: deleted.saved.paper.detailsFetched,
            collectionLinksAddedAt: Dictionary(
                deleted.collectionLinks.map { ($0.collectionID, $0.addedAt) },
                uniquingKeysWith: { first, _ in first }
            ),
            pdf: deleted.saved.paper.pdf
        )
    }

    public func restore(_ removed: RemovedPaper) async throws {
        let records = removed.paper.asRecords(
            localID: removed.localID,
            savedAt: removed.savedAt,
            status: removed.status,
            citeKey: removed.citeKey,
            detailsFetched: removed.detailsFetched,
            pdf: removed.pdf
        )
        let notes = removed.notes.isEmpty ? nil : removed.notes
        // Nothing reads added_at's exact value for a link without one, so now() stands in.
        let links = removed.collectionIDs.sorted().map { id in
            CollectionPaperRecord(collectionID: id, paperID: removed.localID, addedAt: removed.collectionLinksAddedAt[id] ?? now())
        }
        try await store.insert(
            paper: records.paper,
            authors: records.authors,
            search: records.searchRow(notes: notes),
            notes: notes.map { PaperNotesRecord(paperID: removed.localID, notes: $0, updatedAt: now()) },
            collectionLinks: links
        )
    }

    public func refreshAfterExternalChanges() async {
        try? await store.notifyExternalChanges()
    }

    public func observePaper(openAlexID: String) -> AsyncStream<LibraryPaper?> {
        store.observePaper(openAlexID: openAlexID).mapped { $0?.asLibraryPaper() }
    }

    public func notes(openAlexID: String) async throws -> PaperNotes {
        try await store.notes(openAlexID: openAlexID)?.notes ?? PaperNotes()
    }

    public func saveNotes(openAlexID: String, notes: PaperNotes) async throws {
        try await store.saveNotes(openAlexID: openAlexID, notes: notes, updatedAt: now())
    }
}

extension LibraryRows {
    /// Every stored status mapped to its `ReadingStatus` (unknown values count as To read); missing statuses are 0.
    func asSnapshot() -> LibrarySnapshot {
        var counts = Dictionary(uniqueKeysWithValues: ReadingStatus.allCases.map { ($0, 0) })
        for (stored, count) in statusCounts {
            counts[ReadingStatus(stored: stored), default: 0] += count
        }
        return LibrarySnapshot(
            papers: papers.map { $0.asLibraryPaper() },
            counts: counts,
            libraryTotal: total,
            allPapersTotal: allTotal
        )
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

extension AsyncStream where Element: Sendable {
    /// The stream's first value, or nil when it ends first.
    func firstElement() async -> Element? {
        for await element in self { return element }
        return nil
    }
}
