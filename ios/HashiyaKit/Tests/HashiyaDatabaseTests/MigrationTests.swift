import Foundation
import GRDB
import HashiyaDatabase
import Testing

struct MigrationTests {
    @Test func v1IsTheOnlyMigration() {
        #expect(HashiyaDatabase.migrator.migrations == ["v1"])
    }

    @Test func v1CreatesAndroidsVersion1Schema() throws {
        let db = try HashiyaDatabase.openInMemory()
        try db.read { db in
            let papers = try db.columns(in: "papers")
            #expect(papers.map(\.name) == [
                "id", "open_alex_id", "doi", "title", "year", "venue", "abstract",
                "citation_count", "is_open_access", "oa_pdf_url", "saved_at",
            ])
            #expect(papers.map(\.type) == [
                "TEXT", "TEXT", "TEXT", "TEXT", "INTEGER", "TEXT", "TEXT", "INTEGER", "INTEGER", "TEXT", "INTEGER",
            ])
            #expect(papers.filter(\.isNotNull).map(\.name) == ["id", "title", "citation_count", "is_open_access", "saved_at"])
            #expect(try db.primaryKey("papers").columns == ["id"])

            let indexes = try db.indexes(on: "papers").filter { $0.name.hasPrefix("index_") }.sorted { $0.name < $1.name }
            #expect(indexes.map(\.name) == ["index_papers_doi", "index_papers_open_alex_id"])
            #expect(indexes.map(\.isUnique) == [false, true])

            let authors = try db.columns(in: "paper_authors")
            #expect(authors.map(\.name) == ["paper_id", "position", "name", "open_alex_author_id"])
            #expect(authors.filter(\.isNotNull).map(\.name) == ["paper_id", "position", "name"])
            #expect(try db.primaryKey("paper_authors").columns == ["paper_id", "position"])

            let foreignKeys = try db.foreignKeys(on: "paper_authors")
            #expect(foreignKeys.map(\.destinationTable) == ["papers"])
            #expect(foreignKeys.first?.mapping.map(\.origin) == ["paper_id"])
            let onDelete = try String.fetchOne(db, sql: "SELECT on_delete FROM pragma_foreign_key_list('paper_authors')")
            #expect(onDelete == "CASCADE")
        }
    }

    @Test func foreignKeysAreEnforced() throws {
        let db = try HashiyaDatabase.openInMemory()
        let enabled = try db.read { db in try Bool.fetchOne(db, sql: "PRAGMA foreign_keys") }
        #expect(enabled == true)
    }

    @Test func reopeningAFileKeepsTheLibrary() async throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appending(path: "hashiya.sqlite")

        let first = try HashiyaDatabase.openPool(at: url)
        try await first.write { db in
            try db.execute(sql: """
                INSERT INTO papers (id, title, citation_count, is_open_access, saved_at)
                VALUES ('local-1', 'Kept', 0, 0, 1)
                """)
        }
        try first.close()

        let second = try HashiyaDatabase.openPool(at: url)
        let titles = try await second.read { db in try String.fetchAll(db, sql: "SELECT title FROM papers") }
        #expect(titles == ["Kept"])
        #expect(try await second.read { db in try HashiyaDatabase.migrator.appliedMigrations(db) } == ["v1"])
        let journalMode = try await second.read { db in try String.fetchOne(db, sql: "PRAGMA journal_mode") }
        #expect(journalMode == "wal")
    }
}
