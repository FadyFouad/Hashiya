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
                try migrator.migrate(pool)
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
