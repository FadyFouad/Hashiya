import Foundation
import GRDB
import os

/// The library's data access. Each operation is one transaction, and every write keeps the search index
/// `paper_search` in step with `papers`. Observations start with the current value and are delivered as
/// `AsyncStream`s, so callers never import GRDB.
public struct PaperStore: Sendable {
    private let writer: any DatabaseWriter

    public init(writer: any DatabaseWriter) {
        self.writer = writer
    }

    /// The store on the shared App Group database (`fileName` in its container).
    public static func shared(fileName: String = HashiyaDatabase.fileName) throws -> PaperStore {
        try open(at: HashiyaDatabase.sharedDatabaseURL(fileName: fileName))
    }

    /// The store on the database file at `url`.
    public static func open(at url: URL) throws -> PaperStore {
        PaperStore(writer: try HashiyaDatabase.openPool(at: url))
    }

    /// A store on a fresh in-memory database.
    public static func inMemory() throws -> PaperStore {
        PaperStore(writer: try HashiyaDatabase.openInMemory())
    }

    /// The library for `match` (an FTS MATCH expression; nil = everything) and `status` (a stored status; nil = any),
    /// one consistent read per database change. Each call starts its own observation.
    public func observeLibrary(match: String?, status: String?) -> AsyncStream<LibraryRows> {
        stream(ValueObservation.tracking { db in
            try Self.librarySnapshot(db, match: match, status: status)
        })
    }

    /// The papers, the counts per status for the same search, and the whole library's size, read together.
    public static func librarySnapshot(_ db: Database, match: String?, status: String?) throws -> LibraryRows {
        let matching = "(:match IS NULL OR papers.id IN (SELECT paper_id FROM paper_search WHERE paper_search MATCH :match))"
        let papers = try PaperRecord.fetchAll(
            db,
            sql: """
                SELECT papers.* FROM papers
                WHERE \(matching)
                  AND (:status IS NULL OR papers.reading_status = :status)
                ORDER BY papers.saved_at DESC
                """,
            arguments: ["match": match, "status": status]
        )
        let authors = try PaperAuthorRecord
            .filter(papers.map(\.id).contains(Column("paper_id")))
            .order(Column("paper_id"), Column("position"))
            .fetchAll(db)
        let authorsByPaper = Dictionary(grouping: authors, by: \.paperID)
        var statusCounts: [String: Int] = [:]
        let counts = try Row.fetchAll(
            db,
            sql: "SELECT reading_status, COUNT(*) AS count FROM papers WHERE \(matching) GROUP BY reading_status",
            arguments: ["match": match]
        )
        for row in counts {
            statusCounts[row["reading_status"]] = row["count"]
        }
        return LibraryRows(
            papers: papers.map { PaperWithAuthors(paper: $0, authors: authorsByPaper[$0.id] ?? []) },
            statusCounts: statusCounts,
            total: try PaperRecord.fetchCount(db)
        )
    }

    /// The OpenAlex IDs of saved papers. Each call starts its own observation.
    public func observeSavedOpenAlexIDs() -> AsyncStream<Set<String>> {
        stream(ValueObservation.tracking { db in
            try String.fetchSet(db, sql: "SELECT open_alex_id FROM papers WHERE open_alex_id IS NOT NULL")
        })
    }

    /// Inserts the paper unless one with the same `id` or `open_alex_id` exists, then its authors and its search row.
    /// Returns false, writing nothing, when the paper already exists.
    @discardableResult
    public func insert(paper: PaperRecord, authors: [PaperAuthorRecord], search: PaperSearchRow) async throws -> Bool {
        precondition(search.paperID == paper.id, "The search row must belong to the paper")
        return try await writer.write { db in
            try paper.insert(db, onConflict: .ignore)
            guard db.changesCount > 0 else { return false }
            for author in authors {
                try author.insert(db)
            }
            try search.insert(db)
            return true
        }
    }

    /// Deletes the paper (its authors cascade) and its search row, and returns what was deleted, or nil if it was not saved.
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
            // FTS rows don't cascade.
            try db.execute(sql: "DELETE FROM paper_search WHERE paper_id = ?", arguments: [paper.id])
            return PaperWithAuthors(paper: paper, authors: authors)
        }
    }

    /// Sets a saved paper's stored status. Returns the number of papers changed: 0 when it is not saved.
    /// The order (`saved_at`) and the search index are not touched.
    @discardableResult
    public func setStatus(openAlexID: String, status: String) async throws -> Int {
        try await writer.write { db in
            try db.execute(sql: "UPDATE papers SET reading_status = ? WHERE open_alex_id = ?", arguments: [status, openAlexID])
            return db.changesCount
        }
    }

    /// Makes every observation fetch again. Observations only see writes made through this store's own
    /// database connection, not those of another process (the Share Extension).
    public func notifyExternalChanges() async throws {
        try await writer.write { db in
            try db.notifyChanges(in: Table(PaperRecord.databaseTableName))
            try db.notifyChanges(in: Table(PaperAuthorRecord.databaseTableName))
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
