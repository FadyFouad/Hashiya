import Foundation
import GRDB
import HashiyaDatabase
import HashiyaModel
import Testing

struct MigrationTests {
    /// A database migrated only to `version`, as an install of that version left it.
    private func version(_ version: String) throws -> DatabaseQueue {
        let queue = try DatabaseQueue()
        try HashiyaDatabase.migrator.migrate(queue, upTo: version)
        return queue
    }

    /// A database migrated only to `v1`, as plans 1 and 2 left every install.
    private func version1() throws -> DatabaseQueue {
        try version("v1")
    }

    /// Android's `MigrationTest` fixture: one paper whose second author was inserted first, and an Arabic paper with
    /// tashkeel and no authors, abstract or venue.
    private func insertVersion1Fixture(into queue: DatabaseQueue) throws {
        try queue.write { db in
            try db.execute(sql: """
                INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, oa_pdf_url, saved_at)
                VALUES ('a', 'W1', '10.48550/arxiv.1706.03762', 'Attention Is All You Need', 2017,
                        'Neural Information Processing Systems', 'The dominant sequence transduction models', 128412, 1, NULL, 100);
                INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 1, 'Noam Shazeer', NULL);
                INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 0, 'Ashish Vaswani', NULL);
                INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, oa_pdf_url, saved_at)
                VALUES ('b', 'W2', NULL, 'تطبيقات التَّعلُّم العميق', NULL, NULL, NULL, 0, 0, NULL, 200);
                """)
        }
    }

    /// The first value of `stream`.
    private func first<T: Sendable>(_ stream: AsyncStream<T>) async -> T? {
        for await value in stream {
            return value
        }
        return nil
    }

    @Test func theMigrationsAreV1ThenV2ThenV3() {
        #expect(HashiyaDatabase.migrator.migrations == ["v1", "v2", "v3"])
    }

    @Test func v1CreatesAndroidsVersion1Schema() throws {
        let queue = try version1()
        try queue.read { db in
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
            #expect(try !db.tableExists("paper_search"))
        }
    }

    @Test func v2AddsTheReadingStatusAndTheSearchIndex() throws {
        let (columns, sql) = try version("v2").read { db in
            (try db.columns(in: "papers"), try String.fetchOne(db, sql: "SELECT sql FROM sqlite_master WHERE name = 'paper_search'"))
        }
        let status = try #require(columns.last)
        #expect(status.name == "reading_status")
        #expect(status.type == "TEXT")
        #expect(status.isNotNull)
        #expect(status.defaultValueSQL == "'to_read'")
        #expect(sql == "CREATE VIRTUAL TABLE paper_search USING fts4(paper_id, title, authors, abstract, venue, tokenize=unicode61, notindexed=paper_id)")
    }

    @Test func migratingKeepsEveryPaperAsToReadAndIndexesItWithItsAuthorsInOrder() throws {
        let queue = try version1()
        try insertVersion1Fixture(into: queue)

        try HashiyaDatabase.migrator.migrate(queue)

        let (statuses, authors, index, rest) = try queue.read { db in
            (
                try String.fetchAll(db, sql: "SELECT id || ':' || reading_status FROM papers ORDER BY id"),
                try String.fetchAll(db, sql: "SELECT name FROM paper_authors ORDER BY position"),
                try String.fetchAll(db, sql: "SELECT paper_id || ':' || title || ':' || authors FROM paper_search ORDER BY paper_id"),
                try String.fetchAll(db, sql: "SELECT abstract || '|' || venue FROM paper_search ORDER BY paper_id")
            )
        }
        #expect(statuses == ["a:to_read", "b:to_read"])
        #expect(authors == ["Ashish Vaswani", "Noam Shazeer"])
        #expect(index == ["a:attention is all you need:ashish vaswani noam shazeer", "b:تطبيقات التعلم العميق:"])
        #expect(rest == ["the dominant sequence transduction models|neural information processing systems", "|"])
    }

    @Test func theMigratedLibraryIsListedNewestFirstAndSearchable() async throws {
        let queue = try version1()
        try insertVersion1Fixture(into: queue)
        try HashiyaDatabase.migrator.migrate(queue)
        let store = PaperStore(writer: queue)

        func ids(_ match: String?) async -> [String]? {
            await first(store.observeLibrary(match: match, status: nil))?.papers.map(\.paper.id)
        }

        #expect(await ids(nil) == ["b", "a"])
        #expect(await ids("\"attention*\"") == ["a"])
        #expect(await ids("\"shazeer*\"") == ["a"])
        #expect(await ids("\"transduction*\"") == ["a"])
        #expect(await ids("\"neural*\" \"processing*\"") == ["a"])
        #expect(await ids("\"التعلم*\"") == ["b"])
        #expect(await first(store.observeLibrary(match: nil, status: nil))?.papers.first?.paper.readingStatus == "to_read")
    }

    @Test func v3AddsTheNotesTable() throws {
        try HashiyaDatabase.openInMemory().read { db in
            let columns = try db.columns(in: "paper_notes")
            #expect(columns.map(\.name) == [
                "paper_id", "summary", "research_question", "method", "key_findings", "limitations", "thoughts", "updated_at",
            ])
            #expect(columns.map(\.type) == ["TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "INTEGER"])
            #expect(columns.allSatisfy { $0.isNotNull })
            #expect(try db.primaryKey("paper_notes").columns == ["paper_id"])
            #expect(try db.foreignKeys(on: "paper_notes").map(\.destinationTable) == ["papers"])
            let onDelete = try String.fetchOne(db, sql: "SELECT on_delete FROM pragma_foreign_key_list('paper_notes')")
            #expect(onDelete == "CASCADE")
        }
    }

    @Test func v3RebuildsTheSearchIndexWithANotesColumn() throws {
        let sql = try HashiyaDatabase.openInMemory().read { db in
            try String.fetchOne(db, sql: "SELECT sql FROM sqlite_master WHERE name = 'paper_search'")
        }
        #expect(sql == "CREATE VIRTUAL TABLE paper_search USING fts4(paper_id, title, authors, abstract, venue, notes, tokenize=unicode61, notindexed=paper_id)")
    }

    /// A spec 3 install: every paper, status and search result is kept, and no paper has notes yet.
    @Test func migratingFromV2KeepsEveryPaperStatusAndSearch() async throws {
        let queue = try version1()
        try insertVersion1Fixture(into: queue)
        try HashiyaDatabase.migrator.migrate(queue, upTo: "v2")
        try await queue.write { db in
            try db.execute(sql: "UPDATE papers SET reading_status = 'reading' WHERE id = 'a'")
        }
        let indexBefore = try await queue.read { db in
            try String.fetchAll(db, sql: "SELECT paper_id || '|' || title || '|' || authors || '|' || abstract || '|' || venue FROM paper_search ORDER BY paper_id")
        }

        try HashiyaDatabase.migrator.migrate(queue)

        let (statuses, indexAfter, notes, noteRows, temporary) = try await queue.read { db in
            (
                try String.fetchAll(db, sql: "SELECT id || ':' || reading_status FROM papers ORDER BY id"),
                try String.fetchAll(db, sql: "SELECT paper_id || '|' || title || '|' || authors || '|' || abstract || '|' || venue FROM paper_search ORDER BY paper_id"),
                try String.fetchAll(db, sql: "SELECT notes FROM paper_search ORDER BY paper_id"),
                try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM paper_notes"),
                try db.tableExists("paper_search_copy")
            )
        }
        #expect(statuses == ["a:reading", "b:to_read"])
        #expect(indexAfter == indexBefore)
        #expect(notes == ["", ""])
        #expect(noteRows == 0)
        #expect(!temporary)

        let store = PaperStore(writer: queue)
        func ids(_ match: String?) async -> [String]? {
            await first(store.observeLibrary(match: match, status: nil))?.papers.map(\.paper.id)
        }
        #expect(await ids(nil) == ["b", "a"])
        #expect(await ids("\"attention*\"") == ["a"])
        #expect(await ids("\"shazeer*\"") == ["a"])
        #expect(await ids("\"transduction*\"") == ["a"])
        #expect(await ids("\"التعلم*\"") == ["b"])
    }

    /// Once the notes column exists, a note written after the upgrade is searchable.
    @Test func notesAreSearchableAfterTheUpgrade() async throws {
        let queue = try version1()
        try insertVersion1Fixture(into: queue)
        try HashiyaDatabase.migrator.migrate(queue, upTo: "v2")
        try HashiyaDatabase.migrator.migrate(queue)
        let store = PaperStore(writer: queue)

        #expect(try await store.saveNotes(openAlexID: "W2", notes: PaperNotes(method: "Ablation study"), updatedAt: 1))

        #expect(await first(store.observeLibrary(match: "\"ablation*\"", status: nil))?.papers.map(\.paper.id) == ["b"])
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
        #expect(try await second.read { db in try HashiyaDatabase.migrator.appliedMigrations(db) } == ["v1", "v2", "v3"])
        let journalMode = try await second.read { db in try String.fetchOne(db, sql: "PRAGMA journal_mode") }
        #expect(journalMode == "wal")
    }

    /// A plan 2 install opened by this version: the file is migrated in place and nothing is lost.
    @Test func openingAVersion1FileMigratesItInPlace() async throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appending(path: "hashiya.sqlite")
        let old = try DatabaseQueue(path: url.path(percentEncoded: false))
        try HashiyaDatabase.migrator.migrate(old, upTo: "v1")
        try insertVersion1Fixture(into: old)
        try old.close()

        let store = try PaperStore.open(at: url)

        let library = await first(store.observeLibrary(match: "\"vaswani*\"", status: "to_read"))
        #expect(library?.papers.map(\.paper.id) == ["a"])
        #expect(library?.total == 2)
    }
}
