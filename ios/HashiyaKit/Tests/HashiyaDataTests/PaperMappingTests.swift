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
            openAccessPDFURL: "https://arxiv.org/pdf/1706.03762",
            publication: PublicationDetails(
                workType: "preprint",
                sourceType: "conference",
                publisher: "Neural Information Processing Systems Foundation",
                volume: "30",
                issue: nil,
                firstPage: "5998",
                lastPage: "6008"
            )
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

    @Test func mapsPublicationDetails() {
        let paper = NetworkWork(
            id: "https://openalex.org/W1",
            primaryLocation: NetworkLocation(source: NetworkSource(displayName: "Nature", type: "journal", hostOrganizationName: "Springer Nature")),
            type: "article",
            biblio: NetworkBiblio(volume: "521", issue: "7553", firstPage: "436", lastPage: "444")
        ).asPaper()

        #expect(paper.publication == PublicationDetails(
            workType: "article",
            sourceType: "journal",
            publisher: "Springer Nature",
            volume: "521",
            issue: "7553",
            firstPage: "436",
            lastPage: "444"
        ))
    }

    @Test func blankPublicationStringsBecomeNil() {
        let paper = NetworkWork(
            id: "https://openalex.org/W1",
            primaryLocation: NetworkLocation(source: NetworkSource(displayName: "X", type: " ", hostOrganizationName: "")),
            type: "",
            biblio: NetworkBiblio(volume: " ", issue: nil, firstPage: "", lastPage: nil)
        ).asPaper()

        #expect(paper.publication == PublicationDetails())
    }

    @Test func publicationStringsAreTrimmed() {
        let paper = NetworkWork(id: "https://openalex.org/W1", type: " article\n", biblio: NetworkBiblio(volume: " 12 ")).asPaper()
        #expect(paper.publication.workType == "article")
        #expect(paper.publication.volume == "12")
    }

    @Test func recordsRoundTripAPaperWithAuthorsInOrder() {
        let records = SamplePapers.attention.asRecords(localID: "local-1", savedAt: 42)
        #expect(records.paper.id == "local-1")
        #expect(records.paper.savedAt == 42)
        #expect(records.authors.map(\.position) == [0, 1, 2, 3, 4])
        #expect(records.authors.allSatisfy { $0.paperID == "local-1" })
        #expect(records.asPaper() == SamplePapers.attention)
    }

    @Test func recordsCarryThePublicationDetailsTheKeyAndTheFlag() {
        var paper = SamplePapers.attention
        paper.publication = PublicationDetails(workType: "preprint", sourceType: "repository", volume: "30")

        let saved = paper.asRecords(localID: "local-1", savedAt: 42)
        #expect(saved.paper.publication == paper.publication)
        #expect(saved.paper.citeKey == nil)
        #expect(saved.paper.detailsFetched)
        #expect(saved.asPaper() == paper)

        let restored = paper.asRecords(localID: "local-1", savedAt: 42, citeKey: "vaswani2017attention", detailsFetched: false)
        #expect(restored.paper.citeKey == "vaswani2017attention")
        #expect(restored.paper.detailsFetched == false)
    }
}
