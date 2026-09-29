import HashiyaDatabase
import Testing

struct PaperStoreTests {
    private func paper(_ n: Int, openAlexID: String? = nil, doi: String? = nil, savedAt: Int64) -> PaperRecord {
        PaperRecord(
            id: "local-\(n)",
            openAlexID: openAlexID ?? "W\(n)",
            doi: doi,
            title: "Paper \(n)",
            year: 2020,
            venue: "Venue",
            abstract: nil,
            citationCount: n,
            isOpenAccess: n.isMultiple(of: 2),
            oaPDFURL: nil,
            savedAt: savedAt
        )
    }

    private func authors(of paper: PaperRecord, _ names: String...) -> [PaperAuthorRecord] {
        names.enumerated().map { PaperAuthorRecord(paperID: paper.id, position: $0.offset, name: $0.element, openAlexAuthorID: nil) }
    }

    /// The first value of `stream` that satisfies `predicate`.
    private func value<T: Sendable>(of stream: AsyncStream<T>, where predicate: (T) -> Bool = { _ in true }) async -> T? {
        for await value in stream where predicate(value) {
            return value
        }
        return nil
    }

    @Test func savedPapersAreNewestFirstWithAuthorsInOrder() async throws {
        let store = try PaperStore.inMemory()
        let old = paper(1, savedAt: 1_000)
        let new = paper(2, savedAt: 2_000)
        try await store.insert(paper: old, authors: authors(of: old, "Ada", "Grace"))
        try await store.insert(paper: new, authors: authors(of: new, "Zed", "Amy", "Bob"))

        let saved = await value(of: store.observeSavedPapers())
        #expect(saved?.map(\.paper.id) == ["local-2", "local-1"])
        #expect(saved?.first?.authors.map(\.name) == ["Zed", "Amy", "Bob"])
        #expect(saved?.first?.authors.map(\.position) == [0, 1, 2])
        #expect(saved?.last?.authors.map(\.name) == ["Ada", "Grace"])
    }

    @Test func savedPapersRoundTripEveryColumn() async throws {
        let store = try PaperStore.inMemory()
        let record = PaperRecord(
            id: "local-9", openAlexID: "W9", doi: "10.1000/xyz", title: "Full", year: 2017, venue: "NeurIPS",
            abstract: "Text", citationCount: 128_412, isOpenAccess: true, oaPDFURL: "https://arxiv.org/pdf/1706.03762",
            savedAt: 1_727_000_000_000
        )
        try await store.insert(paper: record, authors: [])

        #expect(await value(of: store.observeSavedPapers())?.first?.paper == record)
    }

    @Test func savingAgainIsANoOp() async throws {
        let store = try PaperStore.inMemory()
        let first = paper(1, savedAt: 1_000)
        #expect(try await store.insert(paper: first, authors: authors(of: first, "Ada")))

        var sameWork = paper(2, openAlexID: "W1", savedAt: 2_000)
        sameWork.title = "Changed"
        #expect(try await store.insert(paper: sameWork, authors: authors(of: sameWork, "Someone")) == false)

        var sameID = paper(1, openAlexID: "W3", savedAt: 3_000)
        sameID.title = "Changed"
        #expect(try await store.insert(paper: sameID, authors: []) == false)

        let saved = await value(of: store.observeSavedPapers())
        #expect(saved?.map(\.paper.title) == ["Paper 1"])
        #expect(saved?.first?.authors.map(\.name) == ["Ada"])
    }

    @Test func twoWorksWithOneDOIBothSave() async throws {
        let store = try PaperStore.inMemory()
        #expect(try await store.insert(paper: paper(1, doi: "10.1000/xyz", savedAt: 1), authors: []))
        #expect(try await store.insert(paper: paper(2, doi: "10.1000/xyz", savedAt: 2), authors: []))

        #expect(await value(of: store.observeSavedPapers())?.count == 2)
    }

    @Test func deleteReturnsTheRowAndCascadesAuthors() async throws {
        let store = try PaperStore.inMemory()
        let record = paper(1, savedAt: 1_000)
        try await store.insert(paper: record, authors: authors(of: record, "Ada", "Grace"))

        let deleted = try await store.deleteByOpenAlexID("W1")
        #expect(deleted?.paper == record)
        #expect(deleted?.authors.map(\.name) == ["Ada", "Grace"])
        #expect(await value(of: store.observeSavedPapers())?.isEmpty == true)

        // No orphaned authors: re-inserting the same paper with no authors finds none.
        try await store.insert(paper: record, authors: [])
        #expect(await value(of: store.observeSavedPapers())?.first?.authors.isEmpty == true)
    }

    @Test func reinsertingADeletedRowKeepsItsIDAndSavedAt() async throws {
        let store = try PaperStore.inMemory()
        for (n, savedAt) in [(1, 1_000), (2, 2_000), (3, 3_000)] {
            let record = paper(n, savedAt: Int64(savedAt))
            try await store.insert(paper: record, authors: authors(of: record, "Author \(n)"))
        }

        let deleted = try #require(try await store.deleteByOpenAlexID("W2"))
        try await store.insert(paper: deleted.paper, authors: deleted.authors)

        let saved = await value(of: store.observeSavedPapers())
        #expect(saved?.map(\.paper.id) == ["local-3", "local-2", "local-1"])
        #expect(saved?[1].paper.savedAt == 2_000)
        #expect(saved?[1].authors.map(\.name) == ["Author 2"])
    }

    @Test func deletingAnUnknownPaperReturnsNil() async throws {
        let store = try PaperStore.inMemory()
        #expect(try await store.deleteByOpenAlexID("W404") == nil)
    }

    @Test func savedIDsFollowSavesAndDeletes() async throws {
        let store = try PaperStore.inMemory()
        let ids = store.observeSavedOpenAlexIDs()
        var iterator = ids.makeAsyncIterator()
        #expect(await iterator.next() == [])

        try await store.insert(paper: paper(1, savedAt: 1), authors: [])
        var noOpenAlexID = paper(2, savedAt: 2)
        noOpenAlexID.openAlexID = nil
        try await store.insert(paper: noOpenAlexID, authors: [])
        #expect(await value(of: store.observeSavedOpenAlexIDs(), where: { $0.count == 1 }) == ["W1"])

        _ = try await store.deleteByOpenAlexID("W1")
        #expect(await value(of: store.observeSavedOpenAlexIDs(), where: { $0.isEmpty }) == [])
    }
}
