import Foundation
import Testing
import ZIPFoundation
@testable import HashiyaData

struct BackupArchiveTests {
    private let manifest = #"{"format":1,"exportedAt":"2026-10-04T14:05:00Z"}"#
    private let library = #"{"papers":[{"ref":1,"title":"T","savedAt":1}],"collections":[{"name":"C","createdAt":1,"papers":[1]}]}"#

    private func zip(_ entries: [(String, Data)]) throws -> URL {
        let url = FileManager.default.temporaryDirectory.appending(path: "\(UUID().uuidString).hashiya")
        let archive = try Archive(url: url, accessMode: .create)
        for (name, data) in entries {
            try archive.addEntry(with: name, type: .file, uncompressedSize: Int64(data.count), compressionMethod: .deflate) { position, size in
                data.subdata(in: Int(position)..<(Int(position) + size))
            }
        }
        return url
    }

    private func zipText(_ entries: [(String, String)]) throws -> URL {
        try zip(entries.map { ($0.0, Data($0.1.utf8)) })
    }

    private func failure(_ url: URL) -> OpenFailure? {
        if case let .invalid(reason) = readArchive(url) {
            return reason
        }
        return nil
    }

    @Test func readsTheSharedFixture() {
        guard case let .valid(_, library) = readArchive(sharedFixtureURL) else {
            Issue.record("not valid")
            return
        }
        #expect(library.papers.count == 3)
    }

    @Test func readsAValidArchive() throws {
        guard case .valid = readArchive(try zipText([(BackupFormat.manifestEntry, manifest), (BackupFormat.libraryEntry, library)])) else {
            Issue.record("not valid")
            return
        }
    }

    @Test func notAZip() throws {
        let url = FileManager.default.temporaryDirectory.appending(path: "\(UUID().uuidString).txt")
        try Data("hello".utf8).write(to: url)
        #expect(failure(url) == .notABackup)
    }

    @Test func zipWithoutManifest() throws {
        #expect(failure(try zipText([("other.txt", "x")])) == .notABackup)
    }

    @Test func manifestThatIsNotJson() throws {
        #expect(failure(try zipText([(BackupFormat.manifestEntry, "<xml/>"), (BackupFormat.libraryEntry, library)])) == .notABackup)
    }

    @Test func newerFormat() throws {
        #expect(failure(try zipText([(BackupFormat.manifestEntry, #"{"format":2}"#), (BackupFormat.libraryEntry, library)])) == .newerFormat)
    }

    @Test func formatZero() throws {
        #expect(failure(try zipText([(BackupFormat.manifestEntry, #"{"format":0}"#), (BackupFormat.libraryEntry, library)])) == .damaged)
    }

    @Test func missingLibrary() throws {
        #expect(failure(try zipText([(BackupFormat.manifestEntry, manifest)])) == .damaged)
    }

    @Test func malformedLibrary() throws {
        #expect(failure(try zipText([(BackupFormat.manifestEntry, manifest), (BackupFormat.libraryEntry, #"{"papers":[{"ref":1}]}"#)])) == .damaged)
    }

    @Test func duplicateRefs() throws {
        let duplicates = #"{"papers":[{"ref":1,"title":"A","savedAt":1},{"ref":1,"title":"B","savedAt":1}]}"#
        #expect(failure(try zipText([(BackupFormat.manifestEntry, manifest), (BackupFormat.libraryEntry, duplicates)])) == .damaged)
    }

    @Test func collectionPointingAtAnUnknownRef() throws {
        let unknown = #"{"papers":[],"collections":[{"name":"C","createdAt":1,"papers":[5]}]}"#
        #expect(failure(try zipText([(BackupFormat.manifestEntry, manifest), (BackupFormat.libraryEntry, unknown)])) == .damaged)
    }

    @Test func oversizedLibrary() throws {
        let huge = Data(repeating: UInt8(ascii: " "), count: BackupFormat.maxLibraryBytes + 1)
        #expect(failure(try zip([(BackupFormat.manifestEntry, Data(manifest.utf8)), (BackupFormat.libraryEntry, huge)])) == .damaged)
    }

    @Test func oversizedManifest() throws {
        let huge = Data(repeating: UInt8(ascii: " "), count: BackupFormat.maxManifestBytes + 1)
        #expect(failure(try zip([(BackupFormat.manifestEntry, huge), (BackupFormat.libraryEntry, Data(library.utf8))])) == .notABackup)
    }
}
