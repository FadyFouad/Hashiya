import HashiyaModel
import Testing

struct PaperTests {
    @Test func identityIsTheOpenAlexID() {
        let paper = Paper(openAlexID: "W2626778328", title: "Attention Is All You Need")
        #expect(paper.id == "W2626778328")
    }

    @Test func anUntitledPaperKeepsAnEmptyTitle() {
        let paper = Paper(openAlexID: "W4000000002", title: "")
        #expect(paper.title.isEmpty)
        #expect(paper.authors.isEmpty)
        #expect(paper.citationCount == 0)
        #expect(!paper.isOpenAccess)
    }
}
