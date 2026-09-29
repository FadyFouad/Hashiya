import GRDB
import HashiyaDatabase
import Testing

struct PaperStoreTests {
    private let queue: DatabaseQueue
    private let store: PaperStore

    init() throws {
        queue = try HashiyaDatabase.openInMemory()
        store = PaperStore(writer: queue)
    }

    private func paper(
        _ n: Int,
        openAlexID: String? = nil,
        doi: String? = nil,
        title: String? = nil,
        abstract: String? = nil,
        venue: String? = "Venue",
        status: String = "to_read",
        savedAt: Int64
    ) -> PaperRecord {
        PaperRecord(
            id: "local-\(n)",
            openAlexID: openAlexID ?? "W\(n)",
            doi: doi,
            title: title ?? "Paper \(n)",
            year: 2020,
            venue: venue,
            abstract: abstract,
            citationCount: n,
            isOpenAccess: n.isMultiple(of: 2),
            oaPDFURL: nil,
            savedAt: savedAt,
            readingStatus: status
        )
    }

    private func authors(of paper: PaperRecord, _ names: [String]) -> [PaperAuthorRecord] {
        names.enumerated().map { PaperAuthorRecord(paperID: paper.id, position: $0.offset, name: $0.element, openAlexAuthorID: nil) }
    }

    /// Saves `paper` with `names` and its search row, as the repository does.
    @discardableResult
    private func save(_ paper: PaperRecord, _ names: String...) async throws -> Bool {
        let saved = PaperWithAuthors(paper: paper, authors: authors(of: paper, names))
        return try await store.insert(paper: saved.paper, authors: saved.authors, search: saved.searchRow)
    }

    /// The first value of `stream` that satisfies `predicate`.
    private func value<T: Sendable>(of stream: AsyncStream<T>, where predicate: (T) -> Bool = { _ in true }) async -> T? {
        for await value in stream where predicate(value) {
            return value
        }
        return nil
    }

    private func library(match: String? = nil, status: String? = nil) async -> LibraryRows? {
        await value(of: store.observeLibrary(match: match, status: status))
    }

    private func ids(match: String? = nil, status: String? = nil) async -> [String]? {
        await library(match: match, status: status)?.papers.map(\.paper.id)
    }

    private func count(_ table: String) throws -> Int? {
        try queue.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(table)") }
    }

    @Test func savedPapersAreNewestFirstWithAuthorsInOrder() async throws {
        try await save(paper(1, savedAt: 1_000), "Ada", "Grace")
        try await save(paper(2, savedAt: 2_000), "Zed", "Amy", "Bob")

        let saved = await library()?.papers
        #expect(saved?.map(\.paper.id) == ["local-2", "local-1"])
        #expect(saved?.first?.authors.map(\.name) == ["Zed", "Amy", "Bob"])
        #expect(saved?.first?.authors.map(\.position) == [0, 1, 2])
        #expect(saved?.last?.authors.map(\.name) == ["Ada", "Grace"])
    }

    @Test func savedPapersRoundTripEveryColumn() async throws {
        let record = PaperRecord(
            id: "local-9", openAlexID: "W9", doi: "10.1000/xyz", title: "Full", year: 2017, venue: "NeurIPS",
            abstract: "Text", citationCount: 128_412, isOpenAccess: true, oaPDFURL: "https://arxiv.org/pdf/1706.03762",
            savedAt: 1_727_000_000_000, readingStatus: "reading"
        )
        try await save(record)

        #expect(await library()?.papers.first?.paper == record)
    }

    @Test func savingAgainIsANoOp() async throws {
        #expect(try await save(paper(1, savedAt: 1_000), "Ada"))

        #expect(try await save(paper(2, openAlexID: "W1", title: "Changed", savedAt: 2_000), "Someone") == false)
        #expect(try await save(paper(1, openAlexID: "W3", title: "Changed", savedAt: 3_000)) == false)

        let saved = await library()?.papers
        #expect(saved?.map(\.paper.title) == ["Paper 1"])
        #expect(saved?.first?.authors.map(\.name) == ["Ada"])
        #expect(try count("paper_authors") == 1)
        #expect(try count("paper_search") == 1)
    }

    @Test func twoWorksWithOneDOIBothSave() async throws {
        #expect(try await save(paper(1, doi: "10.1000/xyz", savedAt: 1)))
        #expect(try await save(paper(2, doi: "10.1000/xyz", savedAt: 2)))

        #expect(await library()?.papers.count == 2)
    }

    @Test func deleteReturnsTheRowAndRemovesItsAuthorsAndSearchRow() async throws {
        let record = paper(1, savedAt: 1_000)
        try await save(record, "Ada", "Grace")

        let deleted = try await store.deleteByOpenAlexID("W1")
        #expect(deleted?.paper == record)
        #expect(deleted?.authors.map(\.name) == ["Ada", "Grace"])
        #expect(await library()?.papers.isEmpty == true)
        #expect(try count("paper_authors") == 0)
        #expect(try count("paper_search") == 0)
    }

    @Test func reinsertingADeletedRowKeepsItsIDSavedAtStatusAndSearchRow() async throws {
        for (n, savedAt) in [(1, 1_000), (2, 2_000), (3, 3_000)] {
            try await save(paper(n, status: n == 2 ? "reading" : "to_read", savedAt: Int64(savedAt)), "Author \(n)")
        }

        let deleted = try #require(try await store.deleteByOpenAlexID("W2"))
        try await store.insert(paper: deleted.paper, authors: deleted.authors, search: deleted.searchRow)

        let saved = await library()?.papers
        #expect(saved?.map(\.paper.id) == ["local-3", "local-2", "local-1"])
        #expect(saved?[1].paper.savedAt == 2_000)
        #expect(saved?[1].paper.readingStatus == "reading")
        #expect(saved?[1].authors.map(\.name) == ["Author 2"])
        #expect(await ids(match: "\"author*\" \"2*\"") == ["local-2"])
    }

    @Test func deletingAnUnknownPaperReturnsNil() async throws {
        #expect(try await store.deleteByOpenAlexID("W404") == nil)
    }

    @Test func savedIDsFollowSavesAndDeletes() async throws {
        var iterator = store.observeSavedOpenAlexIDs().makeAsyncIterator()
        #expect(await iterator.next() == [])

        try await save(paper(1, savedAt: 1))
        var noOpenAlexID = paper(2, savedAt: 2)
        noOpenAlexID.openAlexID = nil
        try await save(noOpenAlexID)
        #expect(await value(of: store.observeSavedOpenAlexIDs(), where: { $0.count == 1 }) == ["W1"])

        _ = try await store.deleteByOpenAlexID("W1")
        #expect(await value(of: store.observeSavedOpenAlexIDs(), where: { $0.isEmpty }) == [])
    }

    @Test func searchFindsTheTitleAuthorsAbstractAndVenue() async throws {
        try await save(
            paper(1, title: "Attention Is All You Need", abstract: "Sequence transduction", venue: "NeurIPS", savedAt: 100),
            "Ashish Vaswani"
        )
        try await save(paper(2, title: "Deep Residual Learning", abstract: "Image recognition", venue: "CVPR", savedAt: 200), "Kaiming He")

        #expect(await ids(match: "\"attention*\"") == ["local-1"])
        #expect(await ids(match: "\"vaswani*\"") == ["local-1"])
        #expect(await ids(match: "\"recognition*\"") == ["local-2"])
        #expect(await ids(match: "\"cvpr*\"") == ["local-2"])
        #expect(await ids(match: "\"transformer*\"") == [])
    }

    @Test func prefixesMatchLongerWordsAndEveryWordMustMatch() async throws {
        try await save(paper(1, title: "Transformers for language", savedAt: 100))
        try await save(paper(2, title: "Transformers for images", savedAt: 200))

        #expect(await ids(match: "\"transf*\"") == ["local-2", "local-1"])
        #expect(await ids(match: "\"transf*\" \"lang*\"") == ["local-1"])
    }

    /// The local id is stored in the index but not indexed, so it never matches a search.
    @Test func theLocalIDIsNotSearchable() async throws {
        var record = paper(1, title: "Deep learning", savedAt: 100)
        record.id = "zzlocalid"
        try await save(record)

        #expect(await ids(match: "\"zzlocalid*\"") == [])
        #expect(await ids(match: "\"deep*\"") == ["zzlocalid"])
    }

    @Test func searchCombinesWithStatus() async throws {
        try await save(paper(1, title: "Transformers one", status: "reading", savedAt: 100))
        try await save(paper(2, title: "Transformers two", savedAt: 200))
        try await save(paper(3, title: "Convolutions", status: "reading", savedAt: 300))

        #expect(await ids(status: "reading") == ["local-3", "local-1"])
        #expect(await ids(match: "\"transf*\"", status: "reading") == ["local-1"])
    }

    @Test func countsFollowTheSearchAndTheTotalIgnoresIt() async throws {
        try await save(paper(1, title: "Transformers one", status: "reading", savedAt: 100))
        try await save(paper(2, title: "Transformers two", savedAt: 200))
        try await save(paper(3, title: "Convolutions", status: "reading", savedAt: 300))

        #expect(await library()?.statusCounts == ["reading": 2, "to_read": 1])
        #expect(await library(match: "\"transf*\"")?.statusCounts == ["reading": 1, "to_read": 1])
        #expect(await library(match: "\"missing*\"")?.statusCounts == [:])
        #expect(await library(match: "\"missing*\"", status: "read")?.total == 3)
    }

    @Test func settingTheStatusKeepsTheOrderAndTheIndex() async throws {
        try await save(paper(1, title: "Transformers one", savedAt: 100))
        try await save(paper(2, title: "Transformers two", savedAt: 200))

        #expect(try await store.setStatus(openAlexID: "W1", status: "read") == 1)

        #expect(await ids() == ["local-2", "local-1"])
        #expect(await library()?.papers.last?.paper.readingStatus == "read")
        #expect(await ids(match: "\"transf*\"") == ["local-2", "local-1"])
        #expect(try count("paper_search") == 2)
    }

    @Test func settingTheStatusOfAnUnknownPaperChangesNothing() async throws {
        #expect(try await store.setStatus(openAlexID: "W404", status: "read") == 0)
    }

    @Test func aSearchRowMustBelongToItsPaper() {
        let search = PaperSearchRow.make(paperID: "local-1", title: "Café", authorNames: ["Ada", "Grace"], abstract: nil, venue: "NeurIPS")
        #expect(search == PaperSearchRow(paperID: "local-1", title: "cafe", authors: "ada grace", abstract: "", venue: "neurips"))
    }
}
