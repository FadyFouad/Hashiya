import Foundation
import HashiyaData
import HashiyaModel
import HashiyaNetwork
import HashiyaTesting
import Testing

struct PaperMappingTests {
    private func pageWorks() throws -> [NetworkWork] {
        try JSONDecoder().decode(NetworkWorksResponse.self, from: Fixtures.data("works_page.json")).results
    }

    @Test func mapsACompleteWork() throws {
        let paper = try pageWorks()[0].asPaper()
        #expect(paper == Paper(
            openAlexID: "W2626778328",
            doi: "10.48550/arxiv.1706.03762",
            title: "Attention Is All You Need",
            authors: [
                Author(name: "Ashish Vaswani", openAlexID: "A5103024730"),
                Author(name: "Noam Shazeer", openAlexID: "A5021878400"),
                Author(name: "Illia Polosukhin", openAlexID: nil),
            ],
            year: 2017,
            venue: "Neural Information Processing Systems",
            abstract: "The dominant sequence transduction models are based on attention.",
            citationCount: 128_412,
            isOpenAccess: true,
            openAccessPDFURL: "https://arxiv.org/pdf/1706.03762"
        ))
    }

    @Test func mapsASparseWork() throws {
        let paper = try pageWorks()[1].asPaper()
        #expect(paper == Paper(openAlexID: "W4385245566", title: ""))
    }

    @Test func dropsNamelessAuthorsAndTrimsTheTitle() {
        let work = NetworkWork(
            id: "https://openalex.org/W1",
            displayName: "  Spaced title \n",
            authorships: [
                NetworkAuthorship(author: NetworkAuthor(id: "https://openalex.org/A1", displayName: nil)),
                NetworkAuthorship(author: NetworkAuthor(id: nil, displayName: "   ")),
                NetworkAuthorship(author: NetworkAuthor(id: "https://openalex.org/A3", displayName: "Kept Name")),
            ]
        )
        let paper = work.asPaper()
        #expect(paper.title == "Spaced title")
        #expect(paper.authors == [Author(name: "Kept Name", openAlexID: "A3")])
    }

    @Test func dropsAnInvalidDOI() {
        #expect(NetworkWork(id: "https://openalex.org/W1", doi: "not-a-doi").asPaper().doi == nil)
    }

    @Test func missingOpenAccessIsFalse() {
        #expect(NetworkWork(id: "https://openalex.org/W1", openAccess: nil).asPaper().isOpenAccess == false)
    }

    @Test func recordsRoundTripAPaperWithAuthorsInOrder() {
        let records = SamplePapers.attention.asRecords(localID: "local-1", savedAt: 42)
        #expect(records.paper.id == "local-1")
        #expect(records.paper.savedAt == 42)
        #expect(records.authors.map(\.position) == [0, 1, 2, 3, 4])
        #expect(records.authors.allSatisfy { $0.paperID == "local-1" })
        #expect(records.asPaper() == SamplePapers.attention)
    }
}
