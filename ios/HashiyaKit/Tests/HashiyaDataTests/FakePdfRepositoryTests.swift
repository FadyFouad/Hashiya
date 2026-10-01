import Foundation
import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

struct FakePdfRepositoryTests {
    @Test func recordsCallsAndEmitsWhatTestsSet() async throws {
        let fake = FakePdfRepository()
        let pdf = PaperPdf(source: .downloaded, sizeBytes: 10, addedAt: 1)
        fake.setPdf("W1", pdf)
        fake.setDownload("W1", .running(bytes: 3, total: 10))

        var pdfs = fake.observePdf(openAlexID: "W1").makeAsyncIterator()
        var downloads = fake.observeDownload(openAlexID: "W1").makeAsyncIterator()
        #expect(await pdfs.next() == .some(pdf))
        #expect(await downloads.next() == .some(.running(bytes: 3, total: 10)))

        fake.download(openAlexID: "W1")
        fake.cancelDownload(openAlexID: "W1")
        _ = await fake.attach(openAlexID: "W1", from: URL(fileURLWithPath: "/tmp/a.pdf"))
        try await fake.setLastPage(openAlexID: "W1", page: 4)
        try await fake.remove(openAlexID: "W1")

        #expect(fake.downloads == ["W1"])
        #expect(fake.cancels == ["W1"])
        #expect(fake.attaches.map(\.openAlexID) == ["W1"])
        #expect(fake.lastPages.map(\.page) == [4])
        #expect(fake.removals == ["W1"])
        #expect(await fake.pdfFile(openAlexID: "W1") == nil)
    }

    @Test func pdfFileHasAPlaceholderWhileAPdfIsSet() async {
        let fake = FakePdfRepository()
        fake.setPdf("W1", PaperPdf(source: .attached, sizeBytes: 1, addedAt: 1))

        #expect(await fake.pdfFile(openAlexID: "W1") != nil)
    }

    @Test func deleteDownloadedZeroesTheDownloadedNumbersOnly() async throws {
        let fake = FakePdfRepository()
        fake.setStorage(PdfStorage(downloadedBytes: 5, downloadedCount: 1, attachedBytes: 7, attachedCount: 2))

        try await fake.deleteDownloaded()

        #expect(try await fake.storage() == PdfStorage(downloadedBytes: 0, downloadedCount: 0, attachedBytes: 7, attachedCount: 2))
        #expect(fake.deleteDownloadedCalls == 1)
    }
}
