import Foundation
import Testing
import ZIPFoundation
@testable import HashiyaData

/// `testdata/backup/format-1.hashiya` at the repository root, shared with Android's tests.
let sharedFixtureURL: URL = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent() // HashiyaDataTests
    .deletingLastPathComponent() // Tests
    .deletingLastPathComponent() // HashiyaKit
    .deletingLastPathComponent() // ios
    .deletingLastPathComponent() // repository root
    .appending(path: "testdata/backup/format-1.hashiya")

struct BackupFormatTests {
    private func entryData(_ archive: Archive, _ name: String) throws -> Data {
        var data = Data()
        let entry = try #require(archive[name])
        _ = try archive.extract(entry, consumer: { data.append($0) })
        return data
    }

    @Test func decodesTheSharedFixture() throws {
        let archive = try Archive(url: sharedFixtureURL, accessMode: .read)
        let manifest = try BackupFormat.decoder.decode(
            BackupManifest.self,
            from: entryData(archive, BackupFormat.manifestEntry)
        )
        #expect(manifest.format == 1)
        #expect(manifest.includesPdfs)

        let library = try BackupFormat.decoder.decode(
            BackupLibrary.self,
            from: entryData(archive, BackupFormat.libraryEntry)
        )
        #expect(library.papers.map(\.ref) == [1, 2, 3])
        let first = library.papers[0]
        #expect(first.openAlexId == "W2741809807")
        #expect(first.citeKey == "lecun2015deep")
        #expect(first.authors.map(\.name) == ["Yann LeCun", "Yoshua Bengio", "Geoffrey Hinton"])
        #expect(first.authors[2].openAlexAuthorId == nil)
        #expect(first.notes?.thoughts == "Cite in chapter 2")
        #expect(first.pdf == BackupPdf(source: "downloaded", addedAt: 1_790_000_200_000, lastPage: 4, file: "pdfs/1.pdf"))

        let second = library.papers[1]
        #expect(second.openAlexId == nil)
        #expect(second.title == "التعلم العميق في معالجة اللغة العربية")
        #expect(second.citationCount == 0)
        #expect(second.isOpenAccess == false)
        #expect(second.pdf?.file == nil)

        #expect(library.papers[2].readingStatus == "read")
        #expect(library.collections == [
            BackupCollection(name: "Thesis", createdAt: 1_790_000_600_000, papers: [1, 2]),
            BackupCollection(name: "مراجعة", createdAt: 1_790_000_700_000, papers: [3]),
        ])
        #expect(archive[BackupFormat.pdfEntry(ref: 1)] != nil)
    }

    @Test func roundTripsAPaper() throws {
        let paper = BackupPaper(
            ref: 7,
            title: "T",
            savedAt: 5,
            authors: [BackupAuthor(name: "A", openAlexAuthorId: "A1")],
            notes: BackupNotes(summary: "s", updatedAt: 6),
            pdf: BackupPdf(source: "attached", addedAt: 1, lastPage: 2, file: nil)
        )
        let data = try BackupFormat.encoder.encode(BackupLibrary(papers: [paper], collections: []))
        #expect(try BackupFormat.decoder.decode(BackupLibrary.self, from: data).papers == [paper])
    }

    @Test func datesAndFileNamesAreUTC() {
        #expect(BackupFormat.isoUTC(1_790_000_000_000) == "2026-09-21T14:13:20Z")
        #expect(BackupFormat.parseISOUTC("2026-10-04T14:05:00Z") == 1_791_122_700_000)
        #expect(BackupFormat.parseISOUTC("yesterday") == nil)
        #expect(BackupFormat.fileName(at: 1_790_000_000_000) == "Hashiya-library-2026-09-21.hashiya")
    }
}
