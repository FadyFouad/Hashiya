import Foundation
import GRDB

/// Opens the library database and migrates it. There is no destructive fallback: a failing
/// migration throws and never deletes the user's library.
public enum HashiyaDatabase {
    /// The App Group shared with the Share Extension.
    public static let appGroup = "group.com.etatech.hashiya"

    public enum OpenError: Error {
        case appGroupUnavailable
    }

    /// `v1`: Android's Room version 1 schema.
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
        return migrator
    }

    /// `<App Group container>/Library/Application Support/hashiya.sqlite`, creating the directory.
    public static func sharedDatabaseURL(appGroup: String = appGroup) throws -> URL {
        guard let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup) else {
            throw OpenError.appGroupUnavailable
        }
        let directory = container.appending(path: "Library/Application Support", directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory.appending(path: "hashiya.sqlite")
    }

    /// A WAL database pool at `url`, migrated to the latest version.
    public static func openPool(at url: URL) throws -> DatabasePool {
        let pool = try DatabasePool(path: url.path(percentEncoded: false), configuration: configuration())
        try migrator.migrate(pool)
        return pool
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
