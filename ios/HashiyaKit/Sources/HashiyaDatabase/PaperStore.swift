import GRDB
import os

/// The library's data access. Each operation is one transaction. Observations start with the
/// current value and are delivered as `AsyncStream`s, so callers never import GRDB.
public struct PaperStore: Sendable {
    private let writer: any DatabaseWriter

    public init(writer: any DatabaseWriter) {
        self.writer = writer
    }

    /// The store on the shared App Group database.
    public static func shared() throws -> PaperStore {
        PaperStore(writer: try HashiyaDatabase.openPool(at: HashiyaDatabase.sharedDatabaseURL()))
    }

    /// A store on a fresh in-memory database.
    public static func inMemory() throws -> PaperStore {
        PaperStore(writer: try HashiyaDatabase.openInMemory())
    }

    /// Saved papers, newest saved first. Each call starts its own observation.
    public func observeSavedPapers() -> AsyncStream<[PaperWithAuthors]> {
        stream(ValueObservation.tracking { db in
            let papers = try PaperRecord.order(Column("saved_at").desc).fetchAll(db)
            let authors = try PaperAuthorRecord.order(Column("paper_id"), Column("position")).fetchAll(db)
            let authorsByPaper = Dictionary(grouping: authors, by: \.paperID)
            return papers.map { PaperWithAuthors(paper: $0, authors: authorsByPaper[$0.id] ?? []) }
        })
    }

    /// The OpenAlex IDs of saved papers. Each call starts its own observation.
    public func observeSavedOpenAlexIDs() -> AsyncStream<Set<String>> {
        stream(ValueObservation.tracking { db in
            try String.fetchSet(db, sql: "SELECT open_alex_id FROM papers WHERE open_alex_id IS NOT NULL")
        })
    }

    /// Inserts the paper unless one with the same `id` or `open_alex_id` exists, then its authors.
    /// Returns false, writing nothing, when the paper already exists.
    @discardableResult
    public func insert(paper: PaperRecord, authors: [PaperAuthorRecord]) async throws -> Bool {
        try await writer.write { db in
            try paper.insert(db, onConflict: .ignore)
            guard db.changesCount > 0 else { return false }
            for author in authors {
                try author.insert(db)
            }
            return true
        }
    }

    /// Deletes the paper (its authors cascade) and returns what was deleted, or nil if it was not saved.
    public func deleteByOpenAlexID(_ openAlexID: String) async throws -> PaperWithAuthors? {
        try await writer.write { db in
            guard let paper = try PaperRecord.filter(Column("open_alex_id") == openAlexID).fetchOne(db) else {
                return nil
            }
            let authors = try PaperAuthorRecord
                .filter(Column("paper_id") == paper.id)
                .order(Column("position"))
                .fetchAll(db)
            try paper.delete(db)
            return PaperWithAuthors(paper: paper, authors: authors)
        }
    }

    private func stream<Value: Sendable>(_ observation: ValueObservation<ValueReducers.Fetch<Value>>) -> AsyncStream<Value> {
        let writer = self.writer
        return AsyncStream { continuation in
            let task = Task {
                do {
                    for try await value in observation.values(in: writer) {
                        continuation.yield(value)
                    }
                } catch {
                    #if DEBUG
                    Self.logger.error("Library observation failed: \(String(describing: error), privacy: .public)")
                    #endif
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    private static let logger = Logger(subsystem: "com.etatech.hashiya", category: "database")
}
