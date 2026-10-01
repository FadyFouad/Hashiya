import GRDB
import HashiyaDatabase
import HashiyaModel
import Testing

/// Mirrors Android's `CollectionDaoTest`.
struct CollectionStoreTests {
    private let queue: DatabaseQueue
    private let store: PaperStore

    init() throws {
        queue = try HashiyaDatabase.openInMemory()
        store = PaperStore(writer: queue)
    }

    private func savePaper(_ id: String, _ openAlexID: String) async throws {
        let paper = PaperRecord(
            id: id, openAlexID: openAlexID, doi: nil, title: "Title \(id)", year: 2020, venue: nil, abstract: nil,
            citationCount: 0, isOpenAccess: false, oaPDFURL: nil, savedAt: 1
        )
        try await store.insert(paper: paper, authors: [], search: PaperWithAuthors(paper: paper, authors: []).searchRow)
    }

    private func insert(_ name: String, createdAt: Int64 = 1) async throws -> Int64 {
        try #require(try await store.insertCollection(name: name, nameKey: collectionNameKey(name), createdAt: createdAt))
    }

    private func value<T: Sendable>(of stream: AsyncStream<T>) async -> T? {
        for await value in stream {
            return value
        }
        return nil
    }

    private func collections() async -> [CollectionWithCount]? {
        await value(of: store.observeCollections())
    }

    private func count(_ table: String) throws -> Int? {
        try queue.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(table)") }
    }

    @Test func createsCollectionsSortedByNameKeyWithCounts() async throws {
        let b = try await insert("beta", createdAt: 1)
        let a = try await insert("Alpha", createdAt: 2)
        try await savePaper("p1", "W1")
        try await store.addToCollection(collectionID: a, openAlexID: "W1", addedAt: 3)

        #expect(await collections() == [
            CollectionWithCount(id: a, name: "Alpha", paperCount: 1),
            CollectionWithCount(id: b, name: "beta", paperCount: 0),
        ])
    }

    @Test func aNameClashIsReportedNotThrown() async throws {
        let id = try await insert("Thesis")
        #expect(try await store.insertCollection(name: " thesis ", nameKey: collectionNameKey(" thesis "), createdAt: 2) == nil)
        let other = try await insert("Other", createdAt: 3)

        #expect(try await store.renameCollection(id: other, name: "THESIS", nameKey: "thesis") == false)
        #expect(await collections()?.map(\.name) == ["Other", "Thesis"])
        #expect(try await store.renameCollection(id: id, name: "thesis", nameKey: "thesis"))
        #expect(try await store.renameCollection(id: other, name: "Chapter 2", nameKey: "chapter 2"))
        #expect(await collections()?.map(\.name) == ["Chapter 2", "thesis"])
        #expect(try count("collections") == 2)
    }

    @Test func renamingAMissingCollectionReportsFalse() async throws {
        let id = try await insert("Thesis")
        try await store.deleteCollection(id: id)

        #expect(try await store.renameCollection(id: id, name: "Chapter 2", nameKey: "chapter 2") == false)
        #expect(try await store.renameCollection(id: id + 100, name: "Other", nameKey: "other") == false)
        #expect(try count("collections") == 0)
    }

    @Test func aCollectionExistsUntilDeleted() async throws {
        let id = try await insert("A")
        #expect(try await store.collectionExists(id: id))
        try await store.deleteCollection(id: id)
        #expect(try await store.collectionExists(id: id) == false)
    }

    @Test func aPapersCollectionIDsAreAllItsCollections() async throws {
        let a = try await insert("A")
        let b = try await insert("B")
        let c = try await insert("C")
        try await savePaper("p1", "W1")
        try await store.addToCollection(collectionID: c, openAlexID: "W1", addedAt: 2)
        try await store.addToCollection(collectionID: a, openAlexID: "W1", addedAt: 3)

        #expect(await value(of: store.observeCollectionIDs(openAlexID: "W1")) == [a, c])
        #expect(await value(of: store.observeCollectionIDs(openAlexID: "W404")) == [])
        _ = b
    }

    @Test func membershipIsIdempotentAndFollowsThePaper() async throws {
        let id = try await insert("A")
        try await savePaper("p1", "W1")

        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 2)
        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 3)
        #expect(await value(of: store.observeCollectionIDs(openAlexID: "W1")) == [id])

        try await store.removeFromCollection(collectionID: id, openAlexID: "W1")
        #expect(await value(of: store.observeCollectionIDs(openAlexID: "W1")) == [])
        try await store.addToCollection(collectionID: id, openAlexID: "W-unsaved", addedAt: 4)
        #expect(try count("collection_papers") == 0)
    }

    /// A swipe in a collection that was just deleted, or an Undo into one, must neither throw nor link anything.
    @Test func addingToAMissingCollectionDoesNothing() async throws {
        let id = try await insert("A")
        try await savePaper("p1", "W1")
        try await store.deleteCollection(id: id)

        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 2)
        try await store.removeFromCollection(collectionID: id, openAlexID: "W1")

        #expect(try count("collection_papers") == 0)
    }

    @Test func deletingACollectionKeepsItsPapersAndDeletingAPaperKeepsItsCollections() async throws {
        let a = try await insert("A")
        let b = try await insert("B")
        try await savePaper("p1", "W1")
        try await savePaper("p2", "W2")
        try await store.addToCollection(collectionID: a, openAlexID: "W1", addedAt: 2)
        try await store.addToCollection(collectionID: b, openAlexID: "W2", addedAt: 2)

        try await store.deleteCollection(id: a)
        #expect(try count("papers") == 2)
        #expect(try count("collection_papers") == 1)

        _ = try await store.deleteByOpenAlexID("W2")
        #expect(await collections()?.map(\.name) == ["B"])
        #expect(try count("collection_papers") == 0)
    }

    @Test(.timeLimit(.minutes(1)))
    func anOpenCollectionsObservationSeesADeletedCollection() async throws {
        let a = try await insert("A")
        let b = try await insert("B")
        try await savePaper("p1", "W1")
        try await store.addToCollection(collectionID: a, openAlexID: "W1", addedAt: 2)
        var iterator = store.observeCollections().makeAsyncIterator()
        #expect(await iterator.next()?.map(\.id) == [a, b])

        try await store.deleteCollection(id: a)

        #expect(await iterator.next() == [CollectionWithCount(id: b, name: "B", paperCount: 0)])
    }

    @Test(.timeLimit(.minutes(1)))
    func anOpenCollectionsObservationSeesARename() async throws {
        let a = try await insert("A")
        var iterator = store.observeCollections().makeAsyncIterator()
        #expect(await iterator.next()?.map(\.name) == ["A"])

        #expect(try await store.renameCollection(id: a, name: "Z", nameKey: "z"))

        #expect(await iterator.next()?.map(\.name) == ["Z"])
    }
}
