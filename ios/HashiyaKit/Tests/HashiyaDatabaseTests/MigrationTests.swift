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

    /// Android's `MigrationTest` version 3 fixture, as sub-project 4 left a library: one paper with a note and a status, one bare
    /// Arabic paper.
    private func version3WithFixture() throws -> DatabaseQueue {
        let queue = try version("v3")
        try queue.write { db in
            try db.execute(sql: """
                INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, oa_pdf_url, saved_at, reading_status)
                VALUES ('a', 'W1', '10.48550/arxiv.1706.03762', 'Attention Is All You Need', 2017,
                        'Neural Information Processing Systems', 'The dominant sequence transduction models', 128412, 1, NULL, 100, 'read');
                INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 0, 'Ashish Vaswani', NULL);
                INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, oa_pdf_url, saved_at, reading_status)
                VALUES ('b', 'W2', NULL, 'تطبيقات التَّعلُّم العميق', NULL, NULL, NULL, 0, 0, NULL, 200, 'to_read');
                INSERT INTO paper_notes (paper_id, summary, research_question, method, key_findings, limitations, thoughts, updated_at)
                VALUES ('a', 'Transformers', '', 'Ablation study', '', '', '', 5);
                INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes)
                VALUES ('a', 'attention is all you need', 'ashish vaswani', 'the dominant sequence transduction models',
                        'neural information processing systems', 'transformers ablation study');
                INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes) VALUES ('b', 'تطبيقات التعلم العميق', '', '', '', '');
                """)
        }
        return queue
    }

    @Test func theMigrationsAreV1ThroughV5() {
        #expect(HashiyaDatabase.migrator.migrations == ["v1", "v2", "v3", "v4", "v5"])
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
        #expect(try await second.read { db in try HashiyaDatabase.migrator.appliedMigrations(db) } == ["v1", "v2", "v3", "v4", "v5"])
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

    @Test func v4AddsTheCitationColumnsAndTheCollectionTables() throws {
        try HashiyaDatabase.openInMemory().read { db in
            let papers = try db.columns(in: "papers")
            let added = Array(papers.dropLast(4).suffix(9))
            #expect(added.map(\.name) == [
                "work_type", "source_type", "publisher", "volume", "issue", "first_page", "last_page", "cite_key", "details_fetched",
            ])
            #expect(added.map(\.type) == ["TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "INTEGER"])
            #expect(added.filter(\.isNotNull).map(\.name) == ["details_fetched"])
            #expect(added.last?.defaultValueSQL == "0")

            let citeKeyIndex = try #require(try db.indexes(on: "papers").first { $0.name == "index_papers_cite_key" })
            #expect(citeKeyIndex.isUnique)
            #expect(citeKeyIndex.columns == ["cite_key"])

            let collections = try db.columns(in: "collections")
            #expect(collections.map(\.name) == ["id", "name", "name_key", "created_at"])
            #expect(collections.map(\.type) == ["INTEGER", "TEXT", "TEXT", "INTEGER"])
            #expect(collections.allSatisfy { $0.isNotNull })
            #expect(try db.primaryKey("collections").columns == ["id"])
            let nameIndex = try #require(try db.indexes(on: "collections").first { $0.name == "index_collections_name_key" })
            #expect(nameIndex.isUnique)
            #expect(nameIndex.columns == ["name_key"])

            let links = try db.columns(in: "collection_papers")
            #expect(links.map(\.name) == ["collection_id", "paper_id", "added_at"])
            #expect(links.map(\.type) == ["INTEGER", "TEXT", "INTEGER"])
            #expect(links.allSatisfy { $0.isNotNull })
            #expect(try db.primaryKey("collection_papers").columns == ["collection_id", "paper_id"])
            #expect(Set(try db.foreignKeys(on: "collection_papers").map(\.destinationTable)) == ["collections", "papers"])
            let onDelete = try String.fetchAll(db, sql: "SELECT DISTINCT on_delete FROM pragma_foreign_key_list('collection_papers')")
            #expect(onDelete == ["CASCADE"])
            let paperIndex = try #require(try db.indexes(on: "collection_papers").first { $0.name == "index_collection_papers_paper_id" })
            #expect(paperIndex.columns == ["paper_id"])
            #expect(!paperIndex.isUnique)
        }
    }

    /// A sub-project 4 install (Android's `migration3To4KeepsEverythingAndValidatesAgainstVersion4Schema`).
    @Test func migratingFromV3KeepsEverythingAndLeavesTheCitationStateEmpty() throws {
        let queue = try version3WithFixture()

        try HashiyaDatabase.migrator.migrate(queue)

        let (statuses, methods, search, citation, collections, links) = try queue.read { db in
            (
                try String.fetchAll(db, sql: "SELECT id || ':' || reading_status FROM papers ORDER BY id"),
                try String.fetchAll(db, sql: "SELECT method FROM paper_notes"),
                try String.fetchAll(db, sql: "SELECT paper_id || ':' || notes FROM paper_search ORDER BY paper_id"),
                try String.fetchAll(
                    db,
                    sql: """
                        SELECT id || ':' || details_fetched || ':' || (cite_key IS NULL) || ':' || (work_type IS NULL AND volume IS NULL)
                        FROM papers ORDER BY id
                        """
                ),
                try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM collections"),
                try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM collection_papers")
            )
        }
        #expect(statuses == ["a:read", "b:to_read"])
        #expect(methods == ["Ablation study"])
        #expect(search == ["a:transformers ablation study", "b:"])
        #expect(citation == ["a:0:1:1", "b:0:1:1"])
        #expect(collections == 0)
        #expect(links == 0)
    }

    /// Android's `libraryMigratedFromVersion3IsSearchableAndTakesCollections`.
    @Test func aLibraryMigratedFromV3IsSearchableAndTakesCollections() async throws {
        let queue = try version3WithFixture()
        try HashiyaDatabase.migrator.migrate(queue)
        let store = PaperStore(writer: queue)
        func ids(_ match: String?, collectionID: Int64? = nil) async -> [String]? {
            await first(store.observeLibrary(match: match, status: nil, collectionID: collectionID))?.papers.map(\.paper.id)
        }

        #expect(await ids("\"ablation*\"") == ["a"])
        #expect(await ids("\"التعلم*\"") == ["b"])
        let id = try #require(try await store.insertCollection(name: "Thesis", nameKey: "thesis", createdAt: 1))
        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 2)
        #expect(await ids(nil, collectionID: id) == ["a"])
    }

    /// Android's `version1LibraryMigratesAllTheWayToVersion4`.
    @Test func aV1LibraryMigratesAllTheWayToV4() throws {
        let queue = try version1()
        try insertVersion1Fixture(into: queue)

        try HashiyaDatabase.migrator.migrate(queue)

        let rows = try queue.read { db in
            try String.fetchAll(db, sql: "SELECT id || ':' || reading_status || ':' || details_fetched FROM papers ORDER BY id")
        }
        #expect(rows == ["a:to_read:0", "b:to_read:0"])
    }

    /// Android's `MIGRATION_4_5`: four nullable columns, nothing else.
    @Test func v5AddsTheFourPdfColumns() throws {
        try HashiyaDatabase.openInMemory().read { db in
            let added = Array(try db.columns(in: "papers").suffix(4))
            #expect(added.map(\.name) == ["pdf_source", "pdf_size", "pdf_added_at", "pdf_last_page"])
            #expect(added.map(\.type) == ["TEXT", "INTEGER", "INTEGER", "INTEGER"])
            #expect(added.allSatisfy { !$0.isNotNull })
            #expect(added.allSatisfy { $0.defaultValueSQL == nil })
        }
    }

    /// A sub-project 5 install (Android's `migration4To5KeepsEverything`): papers, notes, search, cite keys and collections
    /// are kept, and no paper has a PDF.
    @Test func migratingFromV4KeepsEverythingAndNoPaperHasAPdf() throws {
        let queue = try version3WithFixture()
        try HashiyaDatabase.migrator.migrate(queue, upTo: "v4")
        try queue.write { db in
            try db.execute(sql: """
                UPDATE papers SET cite_key = 'vaswani2017attention', details_fetched = 1 WHERE id = 'a';
                INSERT INTO collections (id, name, name_key, created_at) VALUES (1, 'Thesis', 'thesis', 1);
                INSERT INTO collection_papers (collection_id, paper_id, added_at) VALUES (1, 'a', 2);
                """)
        }

        try HashiyaDatabase.migrator.migrate(queue)

        let (rows, methods, search, links, pdfs) = try queue.read { db in
            (
                try String.fetchAll(
                    db,
                    sql: "SELECT id || ':' || reading_status || ':' || COALESCE(cite_key, '-') || ':' || details_fetched FROM papers ORDER BY id"
                ),
                try String.fetchAll(db, sql: "SELECT method FROM paper_notes"),
                try String.fetchAll(db, sql: "SELECT paper_id || ':' || notes FROM paper_search ORDER BY paper_id"),
                try String.fetchAll(db, sql: "SELECT collection_id || ':' || paper_id FROM collection_papers"),
                try Int.fetchOne(
                    db,
                    sql: """
                        SELECT COUNT(*) FROM papers
                        WHERE pdf_source IS NOT NULL OR pdf_size IS NOT NULL OR pdf_added_at IS NOT NULL OR pdf_last_page IS NOT NULL
                        """
                )
            )
        }
        #expect(rows == ["a:read:vaswani2017attention:1", "b:to_read:-:0"])
        #expect(methods == ["Ablation study"])
        #expect(search == ["a:transformers ablation study", "b:"])
        #expect(links == ["1:a"])
        #expect(pdfs == 0)
    }

    @Test func aV1LibraryMigratesAllTheWayToV5() throws {
        let queue = try version1()
        try insertVersion1Fixture(into: queue)

        try HashiyaDatabase.migrator.migrate(queue)

        let rows = try queue.read { db in
            try String.fetchAll(db, sql: "SELECT id || ':' || reading_status || ':' || (pdf_source IS NULL) FROM papers ORDER BY id")
        }
        #expect(rows == ["a:to_read:1", "b:to_read:1"])
    }
}
