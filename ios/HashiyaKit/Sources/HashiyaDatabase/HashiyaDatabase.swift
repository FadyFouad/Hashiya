import Foundation
import GRDB

/// Opens the library database and migrates it. There is no destructive fallback: a failing
/// migration throws and never deletes the user's library.
public enum HashiyaDatabase {
    /// The App Group shared with the Share Extension.
    public static let appGroup = "group.com.etatech.hashiya"
    /// The library's file name in the App Group container.
    public static let fileName = "hashiya.sqlite"

    public enum OpenError: Error {
        case appGroupUnavailable
        /// The file coordinator neither opened the file nor reported an error.
        case coordinationFailed
    }

    /// A migration failed while opening the library: the error it threw is kept, so crash reports can tell this apart
    /// from the file not opening at all.
    public struct MigrationError: Error {
        public let underlying: any Error

        public init(underlying: any Error) { self.underlying = underlying }
    }

    /// `v1`: Android's Room version 1 schema. `v2`: Android's version 2 — the reading status and the search index.
    /// `v3`: Android's version 3 — the notes table, and the search index rebuilt with a notes column.
    /// `v4`: Android's version 4 — the citation columns on papers, and the collections tables.
    /// `v5`: Android's version 5 — the stored PDF's columns on papers.
    public static var migrator: DatabaseMigrator {
        var migrator = DatabaseMigrator()
        migrator.registerMigration("v1") { db in
            try db.execute(sql: """
                CREATE TABLE papers (
                  id TEXT NOT NULL PRIMARY KEY,
                  open_alex_id TEXT,
                  doi TEXT,
                  title TEXT NOT NULL,
                  year INTEGER,
                  venue TEXT,
                  abstract TEXT,
                  citation_count INTEGER NOT NULL,
                  is_open_access INTEGER NOT NULL,
                  oa_pdf_url TEXT,
                  saved_at INTEGER NOT NULL
                );
                CREATE UNIQUE INDEX index_papers_open_alex_id ON papers(open_alex_id);
                CREATE INDEX index_papers_doi ON papers(doi);
                CREATE TABLE paper_authors (
                  paper_id TEXT NOT NULL REFERENCES papers(id) ON DELETE CASCADE,
                  position INTEGER NOT NULL,
                  name TEXT NOT NULL,
                  open_alex_author_id TEXT,
                  PRIMARY KEY (paper_id, position)
                );
                """)
        }
        migrator.registerMigration("v2") { db in
            // Raw SQL rather than GRDB's FTS4 builder, so `notindexed=paper_id` is certain: a search never matches a local id.
            try db.execute(sql: """
                ALTER TABLE papers ADD COLUMN reading_status TEXT NOT NULL DEFAULT 'to_read';
                CREATE VIRTUAL TABLE paper_search USING fts4(paper_id, title, authors, abstract, venue, tokenize=unicode61, notindexed=paper_id);
                """)
            // Index every saved paper, its authors in position order, exactly as a new save would.
            var authorNames: [String: [String]] = [:]
            for row in try Row.fetchAll(db, sql: "SELECT paper_id, name FROM paper_authors ORDER BY paper_id, position") {
                authorNames[row["paper_id"], default: []].append(row["name"])
            }
            for row in try Row.fetchAll(db, sql: "SELECT id, title, abstract, venue FROM papers") {
                let id: String = row["id"]
                let search = PaperSearchRow.make(
                    paperID: id,
                    title: row["title"],
                    authorNames: authorNames[id] ?? [],
                    abstract: row["abstract"],
                    venue: row["venue"]
                )
                // The v2 index has these five columns; `PaperSearchRow.insert` writes the current schema's.
                try db.execute(
                    sql: "INSERT INTO paper_search (paper_id, title, authors, abstract, venue) VALUES (?, ?, ?, ?, ?)",
                    arguments: [search.paperID, search.title, search.authors, search.abstract, search.venue]
                )
            }
        }
        migrator.registerMigration("v3") { db in
            // An FTS table can't be altered: copy its rows out, recreate it with `notes`, and copy them back
            // unchanged (nothing is re-normalized; no paper has notes yet).
            try db.execute(sql: """
                CREATE TABLE paper_notes (
                  paper_id TEXT NOT NULL PRIMARY KEY REFERENCES papers(id) ON DELETE CASCADE,
                  summary TEXT NOT NULL,
                  research_question TEXT NOT NULL,
                  method TEXT NOT NULL,
                  key_findings TEXT NOT NULL,
                  limitations TEXT NOT NULL,
                  thoughts TEXT NOT NULL,
                  updated_at INTEGER NOT NULL
                );
                CREATE TEMP TABLE paper_search_copy AS SELECT paper_id, title, authors, abstract, venue FROM paper_search;
                DROP TABLE paper_search;
                CREATE VIRTUAL TABLE paper_search USING fts4(paper_id, title, authors, abstract, venue, notes, tokenize=unicode61, notindexed=paper_id);
                INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes)
                  SELECT paper_id, title, authors, abstract, venue, '' FROM paper_search_copy;
                DROP TABLE paper_search_copy;
                """)
        }

        migrator.registerMigration("v4") { db in
            // Android's MIGRATION_3_4, statement for statement. Every existing paper gets details_fetched = 0, so it is
            // refetched once before its first export. Nothing existing is rewritten, and the search index is untouched.
            for column in ["work_type", "source_type", "publisher", "volume", "issue", "first_page", "last_page", "cite_key"] {
                try db.execute(sql: "ALTER TABLE papers ADD COLUMN `\(column)` TEXT")
            }
            try db.execute(sql: """
                ALTER TABLE papers ADD COLUMN `details_fetched` INTEGER NOT NULL DEFAULT 0;
                CREATE UNIQUE INDEX IF NOT EXISTS `index_papers_cite_key` ON `papers` (`cite_key`);
                CREATE TABLE IF NOT EXISTS `collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `name_key` TEXT NOT NULL, `created_at` INTEGER NOT NULL);
                CREATE UNIQUE INDEX IF NOT EXISTS `index_collections_name_key` ON `collections` (`name_key`);
                CREATE TABLE IF NOT EXISTS `collection_papers` (`collection_id` INTEGER NOT NULL, `paper_id` TEXT NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`collection_id`, `paper_id`), FOREIGN KEY(`collection_id`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`paper_id`) REFERENCES `papers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
                CREATE INDEX IF NOT EXISTS `index_collection_papers_paper_id` ON `collection_papers` (`paper_id`);
                """)
        }

        migrator.registerMigration("v5") { db in
            // Android's MIGRATION_4_5, statement for statement: four nullable columns, nothing existing rewritten. A paper has a
            // PDF exactly when pdf_source is set; the four are written and cleared together.
            try db.execute(sql: """
                ALTER TABLE papers ADD COLUMN `pdf_source` TEXT;
                ALTER TABLE papers ADD COLUMN `pdf_size` INTEGER;
                ALTER TABLE papers ADD COLUMN `pdf_added_at` INTEGER;
                ALTER TABLE papers ADD COLUMN `pdf_last_page` INTEGER;
                """)
        }
        return migrator
    }

    /// `<App Group container>/Library/Application Support/<fileName>`, creating the directory.
    public static func sharedDatabaseURL(appGroup: String = appGroup, fileName: String = fileName) throws -> URL {
        guard let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup) else {
            throw OpenError.appGroupUnavailable
        }
        let directory = container.appending(path: "Library/Application Support", directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory.appending(path: fileName)
    }

    /// A WAL database pool at `url`, migrated to the latest version. The app and the Share Extension both open
    /// the App Group file this way (GRDB's "Sharing a Database" guide): the opening is coordinated with other
    /// processes, writes wait up to 5 s for another process's write, and the pool stops taking locks while
    /// the process is suspended (`suspend()`).
    public static func openPool(at url: URL) throws -> DatabasePool {
        var configuration = configuration()
        configuration.busyMode = .timeout(5)
        configuration.observesSuspensionNotifications = true

        var coordinationError: NSError?
        var result: Result<DatabasePool, any Error> = .failure(OpenError.coordinationFailed)
        NSFileCoordinator(filePresenter: nil).coordinate(writingItemAt: url, options: .forMerging, error: &coordinationError) { url in
            result = Result {
                let pool = try DatabasePool(path: url.path(percentEncoded: false), configuration: configuration)
                do {
                    try migrator.migrate(pool)
                } catch {
                    throw MigrationError(underlying: error)
                }
                return pool
            }
        }
        if let coordinationError { throw coordinationError }
        return try result.get()
    }

    /// Deletes the database at `url` with its `-wal` and `-shm` files; missing files are fine. Close it first.
    public static func removeDatabase(at url: URL) throws {
        for suffix in ["", "-wal", "-shm"] {
            let file = URL(filePath: url.path(percentEncoded: false) + suffix)
            do {
                try FileManager.default.removeItem(at: file)
            } catch CocoaError.fileNoSuchFile {
                continue
            }
        }
    }

    /// Before the process is suspended: pools stop taking new locks, so iOS never kills it for holding one
    /// on a shared file (0xDEAD10CC). Writes fail until `resume()`.
    public static func suspend() {
        NotificationCenter.default.post(name: Database.suspendNotification, object: nil)
    }

    /// Back in the foreground: pools may take locks again.
    public static func resume() {
        NotificationCenter.default.post(name: Database.resumeNotification, object: nil)
    }

    /// A migrated in-memory database, for tests and UI-test launches.
    public static func openInMemory() throws -> DatabaseQueue {
        let queue = try DatabaseQueue(configuration: configuration())
        try migrator.migrate(queue)
        return queue
    }

    static func configuration() -> Configuration {
        var configuration = Configuration()
        configuration.foreignKeysEnabled = true
        return configuration
    }
}
