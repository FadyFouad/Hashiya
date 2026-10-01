import GRDB
import HashiyaDatabase
import HashiyaModel
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

    private func library(match: String? = nil, status: String? = nil, collectionID: Int64? = nil) async -> LibraryRows? {
        await value(of: store.observeLibrary(match: match, status: status, collectionID: collectionID))
    }

    private func ids(match: String? = nil, status: String? = nil, collectionID: Int64? = nil) async -> [String]? {
        await library(match: match, status: status, collectionID: collectionID)?.papers.map(\.paper.id)
    }

    /// A new collection's id, keyed by `collectionNameKey` as the repository keys it.
    private func collection(_ name: String) async throws -> Int64 {
        try #require(try await store.insertCollection(name: name, nameKey: collectionNameKey(name), createdAt: 1))
    }

    /// Restores `deleted` as the repository's Undo does, with its links.
    @discardableResult
    private func restore(_ deleted: DeletedPaper) async throws -> Bool {
        try await store.insert(
            paper: deleted.saved.paper,
            authors: deleted.saved.authors,
            search: deleted.saved.searchRow,
            notes: deleted.notes,
            collectionLinks: deleted.collectionLinks
        )
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
        #expect(deleted?.saved.paper == record)
        #expect(deleted?.saved.authors.map(\.name) == ["Ada", "Grace"])
        #expect(deleted?.notes == nil)
        #expect(await library()?.papers.isEmpty == true)
        #expect(try count("paper_authors") == 0)
        #expect(try count("paper_search") == 0)
    }

    @Test func reinsertingADeletedRowKeepsItsIDSavedAtStatusAndSearchRow() async throws {
        for (n, savedAt) in [(1, 1_000), (2, 2_000), (3, 3_000)] {
            try await save(paper(n, status: n == 2 ? "reading" : "to_read", savedAt: Int64(savedAt)), "Author \(n)")
        }

        let deleted = try #require(try await store.deleteByOpenAlexID("W2"))
        try await store.insert(paper: deleted.saved.paper, authors: deleted.saved.authors, search: deleted.saved.searchRow)

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

    // MARK: Notes

    private func noteRow(_ paperID: String) throws -> PaperNotesRecord? {
        try queue.read { db in try PaperNotesRecord.fetchOne(db, key: paperID) }
    }

    private func searchNotes(_ paperID: String) throws -> String? {
        try queue.read { db in try String.fetchOne(db, sql: "SELECT notes FROM paper_search WHERE paper_id = ?", arguments: [paperID]) }
    }

    @Test func savingNotesCreatesUpdatesAndDeletesTheRow() async throws {
        try await save(paper(1, savedAt: 1_000))

        #expect(try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(summary: "First"), updatedAt: 5))
        #expect(try noteRow("local-1") == PaperNotesRecord(paperID: "local-1", notes: PaperNotes(summary: "First"), updatedAt: 5))

        #expect(try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(summary: "First", thoughts: "Later"), updatedAt: 6))
        #expect(try noteRow("local-1")?.notes == PaperNotes(summary: "First", thoughts: "Later"))
        #expect(try noteRow("local-1")?.updatedAt == 6)
        #expect(try count("paper_notes") == 1)

        #expect(try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(summary: "  ", method: "\n"), updatedAt: 7))
        #expect(try noteRow("local-1") == nil)
        #expect(try count("paper_notes") == 0)
    }

    @Test func theSearchColumnFollowsEverySave() async throws {
        try await save(paper(1, savedAt: 1_000))

        try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(summary: "Café", keyFindings: "التَّعلُّم"), updatedAt: 1)
        #expect(try searchNotes("local-1") == "cafe   التعلم  ")

        try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(), updatedAt: 2)
        #expect(try searchNotes("local-1") == "")
    }

    @Test func savingNotesForAnUnsavedPaperWritesNothing() async throws {
        #expect(try await store.saveNotes(openAlexID: "W404", notes: PaperNotes(summary: "x"), updatedAt: 1) == false)
        #expect(try count("paper_notes") == 0)
    }

    @Test func aSearchFindsAWordOnlyInTheNotesWithFolding() async throws {
        try await save(paper(1, title: "Deep learning", savedAt: 1_000))
        try await save(paper(2, title: "Other", savedAt: 2_000))
        try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(thoughts: "Try the ablation on التَّعلُّم المعزّز"), updatedAt: 1)

        #expect(await ids(match: "\"ablation*\"") == ["local-1"])
        #expect(await ids(match: "\"التعلم*\"") == ["local-1"])
        #expect(await ids(match: "\"ablation*\" \"deep*\"") == ["local-1"])
        #expect(await ids(match: "\"ablation*\" \"other*\"") == [])
    }

    /// SQLite doesn't report a virtual table's writes to GRDB, so `saveNotes` notifies `papers` itself.
    @Test(.timeLimit(.minutes(1)))
    func anOpenLibrarySearchSeesANewNote() async throws {
        try await save(paper(1, savedAt: 1_000))
        var iterator = store.observeLibrary(match: "\"ablation*\"", status: nil).makeAsyncIterator()
        #expect(await iterator.next()?.papers.isEmpty == true)

        try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(method: "Ablation"), updatedAt: 1)

        #expect(await iterator.next()?.papers.map(\.paper.id) == ["local-1"])
    }

    @Test func notesAreReadByOpenAlexID() async throws {
        try await save(paper(1, savedAt: 1_000))
        #expect(try await store.notes(openAlexID: "W1") == nil)

        try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(summary: "S"), updatedAt: 3)

        #expect(try await store.notes(openAlexID: "W1") == PaperNotesRecord(paperID: "local-1", notes: PaperNotes(summary: "S"), updatedAt: 3))
        #expect(try await store.notes(openAlexID: "W404") == nil)
    }

    @Test func observePaperEmitsThePaperThenNilAfterDelete() async throws {
        try await save(paper(1, savedAt: 1_000), "Ada", "Grace")
        var iterator = store.observePaper(openAlexID: "W1").makeAsyncIterator()

        let first = await iterator.next()
        #expect(first??.paper.id == "local-1")
        #expect(first??.authors.map(\.name) == ["Ada", "Grace"])

        _ = try await store.deleteByOpenAlexID("W1")
        #expect(await value(of: store.observePaper(openAlexID: "W1")) == .some(nil))
        let next = await iterator.next()
        #expect(next == .some(nil))
    }

    @Test func deleteReturnsTheNotesAndLeavesNoRow() async throws {
        try await save(paper(1, savedAt: 1_000))
        try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(limitations: "Small sample"), updatedAt: 4)

        let deleted = try #require(try await store.deleteByOpenAlexID("W1"))

        #expect(deleted.notes == PaperNotesRecord(paperID: "local-1", notes: PaperNotes(limitations: "Small sample"), updatedAt: 4))
        #expect(try count("paper_notes") == 0)
        #expect(try count("paper_search") == 0)
    }

    @Test func insertingWithNotesRestoresTheRowAndTheSearchColumn() async throws {
        try await save(paper(1, savedAt: 1_000), "Ada")
        try await store.saveNotes(openAlexID: "W1", notes: PaperNotes(method: "Ablation"), updatedAt: 4)
        let deleted = try #require(try await store.deleteByOpenAlexID("W1"))
        let notes = try #require(deleted.notes)

        try await store.insert(
            paper: deleted.saved.paper,
            authors: deleted.saved.authors,
            search: deleted.saved.searchRow(notes: notes.notes),
            notes: notes
        )

        #expect(try noteRow("local-1") == notes)
        #expect(try searchNotes("local-1") == "  ablation   ")
        #expect(await ids(match: "\"ablation*\"") == ["local-1"])
    }

    @Test func theNotesTextFoldsEverySectionAndIsEmptyForBlankNotes() {
        #expect(PaperSearchRow.notesText(PaperNotes()) == "")
        #expect(PaperSearchRow.notesText(PaperNotes(summary: "  ")) == "")
        #expect(PaperSearchRow.notesText(PaperNotes(
            summary: "A", researchQuestion: "B", method: "C", keyFindings: "D", limitations: "E", thoughts: "Ö"
        )) == "a b c d e o")
    }

    @Test func publicationColumnsRoundTrip() async throws {
        let details = PublicationDetails(
            workType: "article", sourceType: "journal", publisher: "Springer Nature",
            volume: "521", issue: "7553", firstPage: "436", lastPage: "444"
        )
        var record = paper(1, savedAt: 1_000)
        record.workType = details.workType
        record.sourceType = details.sourceType
        record.publisher = details.publisher
        record.volume = details.volume
        record.issue = details.issue
        record.firstPage = details.firstPage
        record.lastPage = details.lastPage
        record.citeKey = "lecun2015deep"
        record.detailsFetched = true
        try await save(record)

        let saved = await library()?.papers.first?.paper
        #expect(saved == record)
        #expect(saved?.publication == details)
    }

    /// Android's `libraryAndCountsCanBeLimitedToACollection`, plus the totals.
    @Test func libraryAndCountsCanBeLimitedToACollection() async throws {
        try await save(paper(1, title: "Graph networks", status: "read", savedAt: 100), "Ada")
        try await save(paper(2, title: "Graph kernels", savedAt: 200), "Bo")
        try await save(paper(3, title: "Other", savedAt: 300), "Cy")
        let id = try await collection("A")
        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 1)
        try await store.addToCollection(collectionID: id, openAlexID: "W3", addedAt: 1)

        #expect(await ids(collectionID: id) == ["local-3", "local-1"])
        #expect(await ids(match: "\"graph*\"", collectionID: id) == ["local-1"])
        #expect(await ids(status: "read", collectionID: id) == ["local-1"])
        let rows = try #require(await library(match: "\"missing*\"", collectionID: id))
        #expect(rows.papers.isEmpty)
        #expect(rows.total == 2)
        #expect(rows.allTotal == 3)
        #expect(await library(collectionID: id)?.statusCounts == ["read": 1, "to_read": 1])
        #expect(await library()?.total == 3)
        #expect(await library()?.allTotal == 3)
    }

    @Test(.timeLimit(.minutes(1)))
    func anOpenCollectionViewSeesMembershipChanges() async throws {
        try await save(paper(1, savedAt: 100))
        let id = try await collection("A")
        var iterator = store.observeLibrary(match: nil, status: nil, collectionID: id).makeAsyncIterator()
        #expect(await iterator.next()?.papers.isEmpty == true)

        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 1)
        #expect(await iterator.next()?.papers.map(\.paper.id) == ["local-1"])

        try await store.removeFromCollection(collectionID: id, openAlexID: "W1")
        #expect(await iterator.next()?.papers.isEmpty == true)
    }

    @Test func deleteCapturesTheLinksTheCiteKeyAndTheFlag() async throws {
        var record = paper(1, savedAt: 100)
        record.citeKey = "ada2020paper"
        record.detailsFetched = true
        try await save(record, "Ada")
        let kept = try await collection("Kept")
        let gone = try await collection("Gone")
        try await store.addToCollection(collectionID: kept, openAlexID: "W1", addedAt: 5)
        try await store.addToCollection(collectionID: gone, openAlexID: "W1", addedAt: 6)

        let deleted = try #require(try await store.deleteByOpenAlexID("W1"))

        #expect(deleted.collectionLinks == [
            CollectionPaperRecord(collectionID: kept, paperID: "local-1", addedAt: 5),
            CollectionPaperRecord(collectionID: gone, paperID: "local-1", addedAt: 6),
        ])
        #expect(deleted.saved.paper.citeKey == "ada2020paper")
        #expect(deleted.saved.paper.detailsFetched)
        #expect(try count("collection_papers") == 0)
    }

    @Test func restoreKeepsLinksAndCiteKey() async throws {
        var record = paper(1, savedAt: 100)
        record.citeKey = "ada2020paper"
        try await save(record, "Ada")
        let first = try await collection("First")
        let second = try await collection("Second")
        try await store.addToCollection(collectionID: first, openAlexID: "W1", addedAt: 5)
        try await store.addToCollection(collectionID: second, openAlexID: "W1", addedAt: 6)
        let deleted = try #require(try await store.deleteByOpenAlexID("W1"))

        #expect(try await restore(deleted))

        #expect(await value(of: store.observeCollectionIDs(openAlexID: "W1")) == [first, second])
        #expect(await library()?.papers.first?.paper.citeKey == "ada2020paper")
        #expect(await ids(collectionID: second) == ["local-1"])
    }

    /// Android's `deleteCapturesCollectionLinksAndRestoreSkipsDeletedCollections`.
    @Test func restoreSkipsALinkWhoseCollectionWasDeleted() async throws {
        try await save(paper(1, savedAt: 100), "Ada")
        let kept = try await collection("Kept")
        let gone = try await collection("Gone")
        try await store.addToCollection(collectionID: kept, openAlexID: "W1", addedAt: 5)
        try await store.addToCollection(collectionID: gone, openAlexID: "W1", addedAt: 6)
        let deleted = try #require(try await store.deleteByOpenAlexID("W1"))
        try await store.deleteCollection(id: gone)

        #expect(try await restore(deleted))

        #expect(await value(of: store.observeCollectionIDs(openAlexID: "W1")) == [kept])
        #expect(try count("collection_papers") == 1)
    }

    /// Android's `restoreDropsACiteKeyAnotherPaperTookMeanwhile`.
    @Test func restoreDropsATakenCiteKey() async throws {
        var first = paper(1, savedAt: 100)
        first.citeKey = "k"
        try await save(first, "Ada")
        let deleted = try #require(try await store.deleteByOpenAlexID("W1"))
        var second = paper(2, savedAt: 200)
        second.citeKey = "k"
        try await save(second, "Bo")

        #expect(try await restore(deleted))

        let saved = await library()?.papers
        #expect(saved?.map(\.paper.id) == ["local-2", "local-1"])
        #expect(saved?.last?.paper.citeKey == nil)
        #expect(saved?.first?.paper.citeKey == "k")
    }

    /// Android's `savingAnAlreadySavedPaperWithACiteKeyIsStillANoOp`.
    @Test func savingAnAlreadySavedPaperWithACiteKeyIsStillANoOp() async throws {
        var record = paper(1, savedAt: 100)
        record.citeKey = "ada2020paper"
        #expect(try await save(record, "Ada"))

        var again = paper(1, savedAt: 999)
        again.citeKey = "ada2020paper"
        #expect(try await save(again, "Ada") == false)

        #expect(await library()?.papers.first?.paper.savedAt == 100)
        #expect(await library()?.papers.first?.paper.citeKey == "ada2020paper")
        #expect(try count("paper_authors") == 1)
    }

    @Test(.timeLimit(.minutes(1)))
    func anOpenCollectionsObservationSeesADeletedPaper() async throws {
        try await save(paper(1, savedAt: 100))
        let id = try await collection("A")
        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 1)
        var iterator = store.observeCollections().makeAsyncIterator()
        #expect(await iterator.next() == [CollectionWithCount(id: id, name: "A", paperCount: 1)])

        _ = try await store.deleteByOpenAlexID("W1")

        #expect(await iterator.next() == [CollectionWithCount(id: id, name: "A", paperCount: 0)])
    }
}
