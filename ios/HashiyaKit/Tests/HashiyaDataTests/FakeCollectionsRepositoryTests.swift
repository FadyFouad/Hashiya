import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

struct FakeCollectionsRepositoryTests {
    private func first<T: Sendable>(_ stream: AsyncStream<T>) async -> T? {
        for await value in stream { return value }
        return nil
    }

    @Test func createValidatesAndSortsLikeTheRealOne() async throws {
        let fake = FakeCollectionsRepository()
        #expect(try await fake.create(name: " Thesis ") == .done(id: 1))
        #expect(try await fake.create(name: "thesis") == .nameTaken)
        #expect(try await fake.create(name: "  ") == .invalidName)
        #expect(try await fake.create(name: "Alpha") == .done(id: 2))
        #expect(await first(fake.observeCollections())?.map(\.name) == ["Alpha", "Thesis"])
        #expect(fake.createdNames == [" Thesis ", "Alpha"])
    }

    @Test func membershipIsMirroredIntoTheLinkedLibrary() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let fake = FakeCollectionsRepository(collections: [PaperCollection(id: 3, name: "A", paperCount: 0)], library: library)

        try await fake.setMembership(collectionID: 3, openAlexID: SamplePapers.attention.openAlexID, member: true)

        #expect(library.collectionMembers == [3: [SamplePapers.attention.openAlexID]])
        #expect(await first(fake.observeCollections())?.first?.paperCount == 1)
        #expect(await first(fake.observeCollectionIDs(openAlexID: SamplePapers.attention.openAlexID)) == [3])
        #expect(fake.membershipCalls == [MembershipCall(collectionID: 3, openAlexID: SamplePapers.attention.openAlexID, member: true)])

        try await fake.delete(id: 3)
        #expect(library.collectionMembers == [:])
        #expect(fake.deletedIDs == [3])
    }

    @Test func failingWritesThrowAndChangeNothing() async throws {
        let fake = FakeCollectionsRepository(collections: [PaperCollection(id: 3, name: "A", paperCount: 0)])
        fake.setFailWrites(true)

        await #expect(throws: FakeCollectionsRepository.Failure.self) { try await fake.create(name: "B") }
        await #expect(throws: FakeCollectionsRepository.Failure.self) {
            try await fake.setMembership(collectionID: 3, openAlexID: "W1", member: true)
        }
        #expect(await first(fake.observeCollections())?.map(\.name) == ["A"])
        #expect(fake.createdNames.isEmpty)
        #expect(fake.membershipCalls.isEmpty)
    }

    @Test func aScriptedResultIsReturnedOnce() async throws {
        let fake = FakeCollectionsRepository()
        fake.setNextResult(.nameTaken)
        #expect(try await fake.create(name: "A") == .nameTaken)
        #expect(fake.createdNames.isEmpty)
        #expect(try await fake.create(name: "A") == .done(id: 1))
        #expect(fake.createdNames == ["A"])
    }
}
