import Foundation
import GRDB
import HashiyaData
import HashiyaDatabase
import HashiyaModel
import os
import Testing

/// Mirrors Android's `RoomCollectionsRepositoryTest`.
struct GRDBCollectionsRepositoryTests {
    private let repository: GRDBCollectionsRepository
    private let library: GRDBLibraryRepository

    init() throws {
        let clock = OSAllocatedUnfairLock(initialState: Int64(0))
        let ids = OSAllocatedUnfairLock(initialState: 0)
        let store = PaperStore(writer: try HashiyaDatabase.openInMemory())
        repository = GRDBCollectionsRepository(store: store, now: { clock.withLock { $0 += 1; return $0 } })
        library = GRDBLibraryRepository(
            store: store,
            now: { clock.withLock { $0 += 1; return $0 } },
            newID: { ids.withLock { $0 += 1; return "local-\($0)" } }
        )
    }

    private func paper(_ id: String) -> Paper {
        Paper(openAlexID: id, title: "Paper \(id)", authors: [Author(name: "A")], year: 2020)
    }

    private func first<T: Sendable>(_ stream: AsyncStream<T>) async -> T? {
        for await value in stream { return value }
        return nil
    }

    private func collections() async -> [PaperCollection]? { await first(repository.observeCollections()) }
    private func names() async -> [String]? { await collections()?.map(\.name) }

    private func created(_ name: String) async throws -> Int64 {
        guard case .done(let id) = try await repository.create(name: name) else {
            Issue.record("Creating \(name) failed")
            return -1
        }
        return id
    }

    @Test func createTrimsTheNameAndRejectsDuplicatesByCaseAndSpaces() async throws {
        #expect(try await repository.create(name: "  Thesis  ") == .done(id: 1))
        #expect(try await repository.create(name: "Thesis") == .nameTaken)
        #expect(try await repository.create(name: " thesis ") == .nameTaken)
        #expect(try await repository.create(name: "thesis") == .nameTaken)
        #expect(try await repository.create(name: " THESIS ") == .nameTaken)
        #expect(try await repository.create(name: "tHeSiS\t") == .nameTaken)
        #expect(await collections() == [PaperCollection(id: 1, name: "Thesis", paperCount: 0)])
    }

    @Test func invalidNamesAreRejected() async throws {
        #expect(try await repository.create(name: "   ") == .invalidName)
        #expect(try await repository.create(name: String(repeating: "x", count: 61)) == .invalidName)
        #expect(try await repository.create(name: "  " + String(repeating: "x", count: 60) + "  ") == .done(id: 1))
        let id = try await created("A")
        #expect(try await repository.rename(id: id, name: "") == .invalidName)
        #expect(try await repository.rename(id: id, name: "   ") == .invalidName)
        #expect(try await repository.rename(id: id, name: String(repeating: "x", count: 61)) == .invalidName)
        #expect(await names() == ["A", String(repeating: "x", count: 60)])
    }

    @Test func renameRejectsAnotherCollectionsNameByCaseAndSpaces() async throws {
        let thesis = try await created("Thesis")
        let other = try await created("Other")

        #expect(try await repository.rename(id: other, name: "Thesis") == .nameTaken)
        #expect(try await repository.rename(id: other, name: " thesis ") == .nameTaken)
        #expect(try await repository.rename(id: other, name: "THESIS") == .nameTaken)
        #expect(try await repository.rename(id: other, name: "  tHeSiS") == .nameTaken)
        #expect(await names() == ["Other", "Thesis"])

        #expect(try await repository.rename(id: thesis, name: " thesis ") == .done(id: thesis))
        #expect(await names() == ["Other", "thesis"])
        #expect(try await repository.rename(id: thesis, name: "THESIS") == .done(id: thesis))
        // The same name again: the UPDATE still matches the row, so it is done, not notFound.
        #expect(try await repository.rename(id: thesis, name: "THESIS") == .done(id: thesis))
        #expect(await names() == ["Other", "THESIS"])
    }

    @Test func renameAllowsANewCaseButNotAnotherCollectionsName() async throws {
        let a = try await created("Alpha")
        let b = try await created("Beta")

        #expect(try await repository.rename(id: b, name: " alpha") == .nameTaken)
        #expect(try await repository.rename(id: a, name: "ALPHA") == .done(id: a))
        #expect(try await repository.rename(id: b, name: " Gamma ") == .done(id: b))
        #expect(await names() == ["ALPHA", "Gamma"])
        #expect(try await repository.create(name: "beta") == .done(id: 3))
    }

    @Test func renamingAMissingCollectionIsNotFound() async throws {
        let id = try await created("Thesis")
        try await repository.delete(id: id)

        #expect(try await repository.rename(id: id, name: "Chapter 2") == .notFound)
        #expect(try await repository.rename(id: id + 100, name: "Chapter 2") == .notFound)
        #expect(await collections() == [])
    }

    @Test func membershipAndDelete() async throws {
        try await library.save(paper("W1"))
        let id = try await created("A")

        try await repository.setMembership(collectionID: id, openAlexID: "W1", member: true)
        #expect(await first(repository.observeCollectionIDs(openAlexID: "W1")) == [id])
        #expect(await collections()?.first?.paperCount == 1)

        try await repository.setMembership(collectionID: id, openAlexID: "W1", member: false)
        #expect(await first(repository.observeCollectionIDs(openAlexID: "W1")) == [])

        try await repository.setMembership(collectionID: id, openAlexID: "W1", member: true)
        try await repository.delete(id: id)
        #expect(await collections() == [])
        #expect(await first(library.observeLibrary(query: "", status: nil))?.papers.map(\.paper.openAlexID) == ["W1"])
    }

    @Test func addingAnUnsavedPaperDoesNothing() async throws {
        let id = try await created("A")

        try await repository.setMembership(collectionID: id, openAlexID: "W-unsaved", member: true)
        #expect(await first(repository.observeCollectionIDs(openAlexID: "W-unsaved")) == [])
        #expect(await collections()?.first?.paperCount == 0)
    }

    @Test func anOpenObservationSeesCreateRenameAndDelete() async throws {
        var updates = repository.observeCollections().makeAsyncIterator()
        #expect(await updates.next() == [])

        let id = try await created("Thesis")
        #expect(await updates.next()?.map(\.name) == ["Thesis"])
        _ = try await repository.rename(id: id, name: "Chapter 2")
        #expect(await updates.next()?.map(\.name) == ["Chapter 2"])
        try await repository.delete(id: id)
        #expect(await updates.next() == [])
    }

    @Test func anEmojiCountsAsOneCharacter() async throws {
        let name = String(repeating: "📚", count: 60)
        #expect(try await repository.create(name: name) == .done(id: 1))
    }
}
