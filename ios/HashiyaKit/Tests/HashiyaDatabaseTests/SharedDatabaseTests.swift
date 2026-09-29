import Foundation
import GRDB
import HashiyaDatabase
import Testing

/// The app and the Share Extension open the same file with their own pools, as two processes do.
struct SharedDatabaseTests {
    private func temporaryDatabase() throws -> (url: URL, directory: URL) {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return (directory.appending(path: "hashiya.sqlite"), directory)
    }

    private func insert(_ id: String, into db: Database) throws {
        try db.execute(
            sql: "INSERT INTO papers (id, title, citation_count, is_open_access, saved_at) VALUES (?, ?, 0, 0, 1)",
            arguments: [id, id]
        )
    }

    @Test func aWriteWaitsForAnotherPoolsWriteInsteadOfFailing() async throws {
        let (url, directory) = try temporaryDatabase()
        defer { try? FileManager.default.removeItem(at: directory) }
        let app = try HashiyaDatabase.openPool(at: url)
        let shareExtension = try HashiyaDatabase.openPool(at: url)

        async let slowWrite: Void = app.write { db in
            try insert("local-1", into: db)
            Thread.sleep(forTimeInterval: 0.3)
        }
        try await Task.sleep(for: .milliseconds(50))
        try await shareExtension.write { db in try insert("local-2", into: db) }
        try await slowWrite

        #expect(try await app.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM papers") } == 2)
    }

    @Test func removeDatabaseDeletesTheFileAndItsCompanions() throws {
        let (url, directory) = try temporaryDatabase()
        defer { try? FileManager.default.removeItem(at: directory) }
        let pool = try HashiyaDatabase.openPool(at: url)
        try pool.close()

        try HashiyaDatabase.removeDatabase(at: url)

        #expect(try FileManager.default.contentsOfDirectory(atPath: directory.path(percentEncoded: false)) == [])
        try HashiyaDatabase.removeDatabase(at: url)
    }
}
