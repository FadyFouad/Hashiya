import HashiyaModel
import Testing

struct ReadingStatusTests {
    @Test func theStatusesAreToReadReadingAndReadInThatOrder() {
        #expect(ReadingStatus.allCases == [.toRead, .reading, .read])
    }

    @Test func aLibraryPaperIsIdentifiedByItsPaper() {
        let paper = Paper(openAlexID: "W2626778328", title: "Attention Is All You Need")
        let saved = LibraryPaper(paper: paper, status: .reading)
        #expect(saved.id == "W2626778328")
        #expect(saved != LibraryPaper(paper: paper, status: .read))
    }
}
