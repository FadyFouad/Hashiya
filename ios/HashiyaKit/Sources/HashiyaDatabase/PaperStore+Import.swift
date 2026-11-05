import Foundation
import GRDB

/// Import jobs and the papers they save. Android's `ImportJobDao` and `PaperDao.insertImportBatch`.
extension PaperStore {
    /// The newest job, or nil (every job is deleted once the user dismisses it).
    public func observeLatestImportJob() -> AsyncStream<ImportJobRecord?> {
        stream(ValueObservation.tracking { db in
            try ImportJobRecord.order(Column("id").desc).fetchOne(db)
        })
    }

    public func importJob(id: Int64) async throws -> ImportJobRecord? {
        try await writer.read { db in try ImportJobRecord.fetchOne(db, key: id) }
    }

    /// The newest analyzing or importing job, or nil.
    public func runningImportJob() async throws -> ImportJobRecord? {
        try await writer.read { db in
            try ImportJobRecord
                .filter(["analyzing", "importing"].contains(Column("state")))
                .order(Column("id").desc)
                .fetchOne(db)
        }
    }

    /// Stores `job` (its id is ignored) and returns the new id.
    public func createImportJob(_ job: ImportJobRecord) async throws -> Int64 {
        try await writer.write { db in
            var record = job
            record.id = nil
            try record.insert(db)
            return record.id ?? db.lastInsertedRowID
        }
    }

    /// Replaces the stored job with `job` only while it is still in `expected`; false when it moved on (cancelled meanwhile).
    public func replaceImportJob(ifState expected: String, with job: ImportJobRecord) async throws -> Bool {
        try await writer.write { db in
            guard let id = job.id, try ImportJobRecord.fetchOne(db, key: id)?.state == expected else { return false }
            try job.update(db)
            return true
        }
    }

    /// Deletes the job; its `import_job_papers` rows cascade, its papers stay.
    public func deleteImportJob(id: Int64) async throws {
        try await writer.write { db in
            _ = try ImportJobRecord.deleteOne(db, key: id)
        }
    }

    /// Every saved paper's identity, marking the ones job `jobID` saved.
    public func libraryKeys(jobID: Int64) async throws -> [LibraryKeyRow] {
        try await writer.read { db in
            try Row.fetchAll(
                db,
                sql: """
                    SELECT papers.open_alex_id, papers.doi, papers.title, papers.year, papers.cite_key,
                        EXISTS(SELECT 1 FROM import_job_papers WHERE job_id = ? AND paper_id = papers.id) AS imported_by_job
                    FROM papers
                    """,
                arguments: [jobID]
            ).map { row in
                LibraryKeyRow(
                    openAlexID: row["open_alex_id"], doi: row["doi"], title: row["title"], year: row["year"],
                    citeKey: row["cite_key"], importedByJob: row["imported_by_job"]
                )
            }
        }
    }

    /// Saves one batch of an import atomically: each paper with its authors and search row (a paper already saved is skipped,
    /// a cite key another paper holds is dropped), its `import_job_papers` row, every paper the job saved linked to
    /// `collectionID` when it is set (a collection deleted meanwhile is skipped), and the job's progress. Returns how many
    /// papers were saved.
    @discardableResult
    public func insertImportBatch(
        jobID: Int64, papers: [PaperWithAuthors], collectionID: Int64?, addedAt: Int64, processed: Int
    ) async throws -> Int {
        try await writer.write { db in
            var saved = 0
            for imported in papers {
                var paper = imported.paper
                if let key = paper.citeKey,
                   try Bool.fetchOne(db, sql: "SELECT EXISTS(SELECT 1 FROM papers WHERE cite_key = ?)", arguments: [key]) == true {
                    paper.citeKey = nil
                }
                try paper.insert(db, onConflict: .ignore)
                guard db.changesCount > 0 else { continue }
                for author in imported.authors {
                    try author.insert(db)
                }
                try imported.searchRow.insert(db)
                try db.execute(
                    sql: "INSERT OR IGNORE INTO import_job_papers (job_id, paper_id) VALUES (?, ?)",
                    arguments: [jobID, paper.id]
                )
                saved += 1
            }
            if let collectionID {
                try db.execute(
                    sql: """
                        INSERT OR IGNORE INTO collection_papers (collection_id, paper_id, added_at)
                        SELECT ?, paper_id, ? FROM import_job_papers
                        WHERE job_id = ? AND EXISTS(SELECT 1 FROM collections WHERE id = ?)
                        """,
                    arguments: [collectionID, addedAt, jobID, collectionID]
                )
            }
            try db.execute(
                sql: """
                    UPDATE import_jobs SET processed = ?, imported = (SELECT COUNT(*) FROM import_job_papers WHERE job_id = ?)
                    WHERE id = ?
                    """,
                arguments: [processed, jobID, jobID]
            )
            return saved
        }
    }
}

extension PaperStore {
    /// The newest job, or nil.
    public func latestImportJob() async throws -> ImportJobRecord? {
        try await writer.read { db in try ImportJobRecord.order(Column("id").desc).fetchOne(db) }
    }
}
