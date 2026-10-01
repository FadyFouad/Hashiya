import GRDB
import HashiyaDatabase
import HashiyaModel
import Testing

/// Mirrors Android's `CitationDaoTest`.
struct CitationStoreTests {
    private let queue: DatabaseQueue
    private let store: PaperStore

    init() throws {
        queue = try HashiyaDatabase.openInMemory()
        store = PaperStore(writer: queue)
    }

    private func savePaper(_ id: String, _ openAlexID: String, savedAt: Int64, citeKey: String? = nil, authors: [String] = []) async throws {
        let paper = PaperRecord(
            id: id, openAlexID: openAlexID, doi: nil, title: "Title \(id)", year: 2020, venue: nil, abstract: nil,
            citationCount: 0, isOpenAccess: false, oaPDFURL: nil, savedAt: savedAt, citeKey: citeKey
        )
        let saved = PaperWithAuthors(
            paper: paper,
            authors: authors.enumerated().map { PaperAuthorRecord(paperID: id, position: $0.offset, name: $0.element, openAlexAuthorID: nil) }
        )
        try await store.insert(paper: saved.paper, authors: saved.authors, search: saved.searchRow)
    }

    @Test func papersComeOldestSavedFirstAndCanBeLimitedToACollection() async throws {
        try await savePaper("p1", "W1", savedAt: 20, authors: ["Ada", "Grace"])
        try await savePaper("p2", "W2", savedAt: 10)
        let id = try #require(try await store.insertCollection(name: "A", nameKey: "a", createdAt: 1))
        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 1)

        #expect(try await store.citablePapers(collectionID: nil).map(\.paper.id) == ["p2", "p1"])
        #expect(try await store.citablePapers(collectionID: id).map(\.paper.id) == ["p1"])
        #expect(try await store.citablePapers(collectionID: id).first?.authors.map(\.name) == ["Ada", "Grace"])
        #expect(try await store.citablePaper(openAlexID: "W1")?.paper.id == "p1")
        #expect(try await store.citablePaper(openAlexID: "W1")?.authors.map(\.name) == ["Ada", "Grace"])
        #expect(try await store.citablePaper(openAlexID: "W404") == nil)
    }

    /// Two papers saved in the same millisecond keep their save order, so keys never depend on local ids.
    @Test func papersSavedAtTheSameTimeKeepTheirSaveOrder() async throws {
        try await savePaper("p-b", "W1", savedAt: 10)
        try await savePaper("p-a", "W2", savedAt: 10)

        #expect(try await store.citablePapers(collectionID: nil).map(\.paper.id) == ["p-b", "p-a"])
        #expect(try await store.papersWithoutCiteKeys().map(\.paper.id) == ["p-b", "p-a"])
    }

    @Test func papersWithoutCiteKeysSkipKeyedPapersAcrossTheLibrary() async throws {
        try await savePaper("p1", "W1", savedAt: 30)
        try await savePaper("p2", "W2", savedAt: 10, citeKey: "smith2020deep")
        try await savePaper("p3", "W3", savedAt: 20)

        #expect(try await store.papersWithoutCiteKeys().map(\.paper.id) == ["p3", "p1"])
    }

    @Test func updatesDetailsAndMarksThemFetched() async throws {
        try await savePaper("p1", "W1", savedAt: 1)
        let details = PublicationDetails(
            workType: "article", sourceType: "journal", publisher: "Springer",
            volume: "521", issue: "7553", firstPage: "436", lastPage: "444"
        )

        try await store.updatePublicationDetails(paperID: "p1", details: details)

        let paper = try #require(try await store.citablePaper(openAlexID: "W1")).paper
        #expect(paper.publication == details)
        #expect(paper.detailsFetched)
    }

    @Test func updatingWithEmptyDetailsClearsThemAndStillMarksFetched() async throws {
        try await savePaper("p1", "W1", savedAt: 1)
        try await store.updatePublicationDetails(paperID: "p1", details: PublicationDetails(workType: "article"))

        try await store.updatePublicationDetails(paperID: "p1", details: PublicationDetails())

        let paper = try #require(try await store.citablePaper(openAlexID: "W1")).paper
        #expect(paper.publication == PublicationDetails())
        #expect(paper.detailsFetched)
    }

    @Test func markDetailsFetchedOnlySetsTheFlag() async throws {
        try await savePaper("p1", "W1", savedAt: 1)

        try await store.markDetailsFetched(paperID: "p1")

        let paper = try #require(try await store.citablePaper(openAlexID: "W1")).paper
        #expect(paper.detailsFetched)
        #expect(paper.workType == nil)
    }

    @Test func assignsKeysAndRejectsATakenOne() async throws {
        try await savePaper("p1", "W1", savedAt: 1, citeKey: "smith2020deep")
        try await savePaper("p2", "W2", savedAt: 2)

        try await store.assignCiteKeys(["p2": "smith2020deepa"])
        #expect(try await store.allCiteKeys() == ["smith2020deep", "smith2020deepa"])

        try await savePaper("p3", "W3", savedAt: 3)
        await #expect(throws: CiteKeyTakenError.self) {
            try await store.assignCiteKeys(["p3": "smith2020deep"])
        }
        #expect(try await store.citablePaper(openAlexID: "W3")?.paper.citeKey == nil)
    }

    @Test func aStoredKeyIsNeverChanged() async throws {
        try await savePaper("p1", "W1", savedAt: 1, citeKey: "smith2020deep")

        try await store.assignCiteKeys(["p1": "other2020key"])

        #expect(try await store.citablePaper(openAlexID: "W1")?.paper.citeKey == "smith2020deep")
    }

    @Test func aBatchWithATakenKeyStoresNone() async throws {
        try await savePaper("p1", "W1", savedAt: 1, citeKey: "smith2020deep")
        try await savePaper("p2", "W2", savedAt: 2)
        try await savePaper("p3", "W3", savedAt: 3)

        await #expect(throws: CiteKeyTakenError.self) {
            try await store.assignCiteKeys(["p2": "jones2021graph", "p3": "smith2020deep"])
        }

        #expect(try await store.allCiteKeys() == ["smith2020deep"])
    }

    @Test func noKeysAtFirst() async throws {
        try await savePaper("p1", "W1", savedAt: 1)
        #expect(try await store.allCiteKeys().isEmpty)
        try await store.assignCiteKeys([:])
        #expect(try await store.allCiteKeys().isEmpty)
    }
}
