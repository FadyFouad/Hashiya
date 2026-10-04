import Foundation
import GRDB

/// Reads the library for an export and merges a backup into it. The device wins every conflict.
extension PaperStore {
    /// Everything an export writes, read in one transaction so it is consistent.
    public func backupSnapshot() async throws -> BackupSnapshot {
        try await writer.read { db in
            let papers = try PaperRecord.order(Column("saved_at"), Column("rowid")).fetchAll(db)
            let authors = Dictionary(grouping: try PaperAuthorRecord.fetchAll(db), by: \.paperID)
            return BackupSnapshot(
                papers: papers.map { paper in
                    PaperWithAuthors(paper: paper, authors: (authors[paper.id] ?? []).sorted { $0.position < $1.position })
                },
                notes: try PaperNotesRecord.fetchAll(db),
                collections: try CollectionRecord.order(Column("name_key")).fetchAll(db),
                links: try CollectionPaperRecord.order(Column("added_at")).fetchAll(db)
            )
        }
    }

    public func paperCount() async throws -> Int {
        try await writer.read { db in try PaperRecord.fetchCount(db) }
    }

    public func collectionCount() async throws -> Int {
        try await writer.read { db in try CollectionRecord.fetchCount(db) }
    }

    public func pdfTotals() async throws -> PdfTotals {
        try await writer.read { db in
            let row = try Row.fetchOne(
                db,
                sql: "SELECT COUNT(*) AS count, COALESCE(SUM(pdf_size), 0) AS bytes FROM papers WHERE pdf_source IS NOT NULL"
            )
            return PdfTotals(count: row?["count"] ?? 0, bytes: row?["bytes"] ?? 0)
        }
    }

    /// The local id of the saved paper a backup paper matches: by `openAlexID` when it has one, otherwise by `doi` (already
    /// normalized). DOIs aren't unique here (a preprint and its published version can both be saved), so a paper with an
    /// OpenAlex id never matches by DOI.
    public func matchFor(openAlexID: String?, doi: String?) async throws -> String? {
        try await writer.read { db in try Self.match(db, openAlexID: openAlexID, doi: doi) }
    }

    /// Adds what the library lacks and keeps everything it has, atomically. The device wins every conflict: a matched paper
    /// keeps all its columns, a backup's notes or PDF columns only fill a gap.
    public func merge(papers: [IncomingPaper], collections: [IncomingCollection], now: Int64) async throws -> MergeOutcome {
        try await writer.write { db in
            var localIDs: [Int: String] = [:]
            var outcome = MergeOutcome(added: 0, matched: 0, notesAdded: 0, collectionsCreated: 0, pdfTargets: [:])
            for incoming in papers {
                var paper = incoming.paper
                if let existing = try Self.match(db, openAlexID: paper.openAlexID, doi: paper.doi) {
                    outcome.matched += 1
                    localIDs[incoming.ref] = existing
                    let hasNotes = try Bool.fetchOne(
                        db,
                        sql: "SELECT EXISTS(SELECT 1 FROM paper_notes WHERE paper_id = ?)",
                        arguments: [existing]
                    ) ?? false
                    if let notes = incoming.notes, !hasNotes {
                        var row = notes
                        row.paperID = existing
                        try row.insert(db)
                        try db.execute(
                            sql: "UPDATE paper_search SET notes = ? WHERE paper_id = ?",
                            arguments: [PaperSearchRow.notesText(notes.notes), existing]
                        )
                        outcome.notesAdded += 1
                    }
                    if let source = paper.pdfSource {
                        try db.execute(
                            sql: """
                                UPDATE papers SET pdf_source = ?, pdf_size = ?, pdf_added_at = ?, pdf_last_page = ?
                                WHERE id = ? AND pdf_source IS NULL
                                """,
                            arguments: [source, paper.pdfSize ?? 0, paper.pdfAddedAt ?? now, paper.pdfLastPage ?? 0, existing]
                        )
                        if db.changesCount > 0 {
                            outcome.pdfTargets[incoming.ref] = existing
                        }
                    }
                } else {
                    if let key = paper.citeKey,
                       try Bool.fetchOne(db, sql: "SELECT EXISTS(SELECT 1 FROM papers WHERE cite_key = ?)", arguments: [key]) == true {
                        paper.citeKey = nil
                    }
                    try paper.insert(db)
                    for author in incoming.authors {
                        try author.insert(db)
                    }
                    try PaperSearchRow.make(
                        paperID: paper.id,
                        title: paper.title,
                        authorNames: incoming.authors.sorted { $0.position < $1.position }.map(\.name),
                        abstract: paper.abstract,
                        venue: paper.venue,
                        notes: incoming.notes?.notes
                    ).insert(db)
                    try incoming.notes?.insert(db)
                    localIDs[incoming.ref] = paper.id
                    if paper.pdfSource != nil {
                        outcome.pdfTargets[incoming.ref] = paper.id
                    }
                    outcome.added += 1
                }
            }
            for collection in collections {
                var id = try Int64.fetchOne(db, sql: "SELECT id FROM collections WHERE name_key = ?", arguments: [collection.nameKey])
                if id == nil {
                    var record = CollectionRecord(name: collection.name, nameKey: collection.nameKey, createdAt: collection.createdAt)
                    try record.insert(db)
                    id = record.id
                    outcome.collectionsCreated += 1
                }
                guard let collectionID = id else { continue }
                var seen = Set<String>()
                for paperID in collection.refs.compactMap({ localIDs[$0] }) where seen.insert(paperID).inserted {
                    try db.execute(
                        sql: "INSERT OR IGNORE INTO collection_papers (collection_id, paper_id, added_at) VALUES (?, ?, ?)",
                        arguments: [collectionID, paperID, now]
                    )
                }
            }
            // SQLite's update hook doesn't report writes to the virtual table paper_search. A matched paper's notes only
            // reach the index through it, so an open Library search wouldn't fetch again without this.
            try db.notifyChanges(in: Table(PaperRecord.databaseTableName))
            return outcome
        }
    }

    private static func match(_ db: Database, openAlexID: String?, doi: String?) throws -> String? {
        if let openAlexID {
            return try String.fetchOne(db, sql: "SELECT id FROM papers WHERE open_alex_id = ?", arguments: [openAlexID])
        }
        if let doi {
            return try String.fetchOne(db, sql: "SELECT id FROM papers WHERE doi = ? ORDER BY saved_at LIMIT 1", arguments: [doi])
        }
        return nil
    }
}
