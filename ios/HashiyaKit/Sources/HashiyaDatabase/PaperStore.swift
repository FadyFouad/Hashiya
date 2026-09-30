import Foundation
import GRDB
import HashiyaModel
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

    /// The saved paper with its authors, or nil once it isn't saved. Each call starts its own observation.
    public func observePaper(openAlexID: String) -> AsyncStream<PaperWithAuthors?> {
        stream(ValueObservation.tracking { db in try Self.paper(db, openAlexID: openAlexID) })
    }

    /// A saved paper's notes, read once; nil when it has none or isn't saved.
    public func notes(openAlexID: String) async throws -> PaperNotesRecord? {
        try await writer.read { db in
            try PaperNotesRecord.fetchOne(
                db,
                sql: """
                    SELECT paper_notes.* FROM paper_notes
                    JOIN papers ON papers.id = paper_notes.paper_id
                    WHERE papers.open_alex_id = ?
                    """,
                arguments: [openAlexID]
            )
        }
    }

    /// Saves a saved paper's notes, or deletes them when blank, and updates its search row. Returns false, writing
    /// nothing, when the paper isn't saved.
    @discardableResult
    public func saveNotes(openAlexID: String, notes: PaperNotes, updatedAt: Int64) async throws -> Bool {
        try await writer.write { db in
            guard let paperID = try String.fetchOne(db, sql: "SELECT id FROM papers WHERE open_alex_id = ?", arguments: [openAlexID]) else {
                return false
            }
            if notes.isEmpty {
                try db.execute(sql: "DELETE FROM paper_notes WHERE paper_id = ?", arguments: [paperID])
            } else {
                try PaperNotesRecord(paperID: paperID, notes: notes, updatedAt: updatedAt).insert(db, onConflict: .replace)
            }
            try db.execute(
                sql: "UPDATE paper_search SET notes = ? WHERE paper_id = ?",
                arguments: [PaperSearchRow.notesText(notes), paperID]
            )
            // SQLite's update hook doesn't report writes to a virtual table, so an open Library search wouldn't
            // fetch again and miss the note.
            try db.notifyChanges(in: Table(PaperRecord.databaseTableName))
            return true
        }
    }

    static func paper(_ db: Database, openAlexID: String) throws -> PaperWithAuthors? {
        guard let paper = try PaperRecord.filter(Column("open_alex_id") == openAlexID).fetchOne(db) else {
            return nil
        }
        let authors = try PaperAuthorRecord
            .filter(Column("paper_id") == paper.id)
            .order(Column("position"))
            .fetchAll(db)
        return PaperWithAuthors(paper: paper, authors: authors)
    }

    /// Inserts the paper unless one with the same `id` or `open_alex_id` exists, then its authors, its search row and
    /// its notes. Returns false, writing nothing, when the paper already exists. `search.notes` must already hold
    /// `notes`' search text.
    @discardableResult
    public func insert(
        paper: PaperRecord,
        authors: [PaperAuthorRecord],
        search: PaperSearchRow,
        notes: PaperNotesRecord? = nil
    ) async throws -> Bool {
        precondition(search.paperID == paper.id, "The search row must belong to the paper")
        precondition(notes.map { $0.paperID == paper.id } ?? true, "The notes must belong to the paper")
        return try await writer.write { db in
            try paper.insert(db, onConflict: .ignore)
            guard db.changesCount > 0 else { return false }
            for author in authors {
                try author.insert(db)
            }
            try search.insert(db)
            try notes?.insert(db)
            return true
        }
    }

    /// Deletes the paper (its authors and notes cascade) and its search row, and returns what was deleted, or nil if
    /// it was not saved.
    public func deleteByOpenAlexID(_ openAlexID: String) async throws -> DeletedPaper? {
        try await writer.write { db in
            guard let saved = try Self.paper(db, openAlexID: openAlexID) else { return nil }
            let notes = try PaperNotesRecord.fetchOne(db, key: saved.paper.id)
            try saved.paper.delete(db)
            // FTS rows don't cascade.
            try db.execute(sql: "DELETE FROM paper_search WHERE paper_id = ?", arguments: [saved.paper.id])
            return DeletedPaper(saved: saved, notes: notes)
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
            try db.notifyChanges(in: Table(PaperNotesRecord.databaseTableName))
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
