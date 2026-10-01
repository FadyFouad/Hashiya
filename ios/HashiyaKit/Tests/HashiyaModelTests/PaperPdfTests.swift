import HashiyaModel
import Testing

/// Mirrors Android's `PaperPdfTest`.
struct PaperPdfTests {
    @Test func theSourcesAreStoredAsDownloadedAndAttached() {
        #expect(PdfSource.downloaded.rawValue == "downloaded")
        #expect(PdfSource.attached.rawValue == "attached")
        #expect(PdfSource(rawValue: "downloaded") == .downloaded)
    }

    @Test func aNewPdfStartsOnTheFirstPage() {
        let pdf = PaperPdf(source: .attached, sizeBytes: 2_048, addedAt: 7)
        #expect(pdf.lastPage == 0)
        #expect(pdf != PaperPdf(source: .attached, sizeBytes: 2_048, addedAt: 7, lastPage: 3))
    }

    @Test func emptyStorageHasNothing() {
        #expect(PdfStorage.empty == PdfStorage(downloadedBytes: 0, downloadedCount: 0, attachedBytes: 0, attachedCount: 0))
    }

    @Test func aLibraryPaperHasNoPdfUnlessToldSo() {
        let paper = Paper(openAlexID: "W1", title: "Attention Is All You Need")
        #expect(LibraryPaper(paper: paper, status: .toRead).hasPdf == false)
        #expect(LibraryPaper(paper: paper, status: .toRead, hasPdf: true).hasPdf)
        #expect(LibraryPaper(paper: paper, status: .toRead) != LibraryPaper(paper: paper, status: .toRead, hasPdf: true))
    }
}
