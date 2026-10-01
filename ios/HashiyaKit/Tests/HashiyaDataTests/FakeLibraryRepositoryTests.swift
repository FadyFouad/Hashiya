import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

struct FakeLibraryRepositoryTests {
    private func first<T: Sendable>(_ stream: AsyncStream<T>) async -> T? {
        for await value in stream {
            return value
        }
        return nil
    }

    @Test func notesSaveReadAndFollowRemoveAndRestore() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let id = SamplePapers.attention.openAlexID
        #expect(try await library.notes(openAlexID: id) == PaperNotes())

        try await library.saveNotes(openAlexID: id, notes: PaperNotes(method: "Ablation"))
        #expect(try await library.notes(openAlexID: id) == PaperNotes(method: "Ablation"))
        #expect(await first(library.observeLibrary(query: "ablation", status: nil))?.papers.map(\.id) == [id])

        let removed = try #require(try await library.remove(openAlexID: id))
        #expect(removed.notes == PaperNotes(method: "Ablation"))
        #expect(try await library.notes(openAlexID: id) == PaperNotes())
        try await library.restore(removed)
        #expect(library.notes(of: id) == PaperNotes(method: "Ablation"))
    }

    @Test func savingNotesForAnUnsavedPaperDoesNothing() async throws {
        let library = FakeLibraryRepository()
        try await library.saveNotes(openAlexID: "W404", notes: PaperNotes(summary: "x"))
        #expect(library.notes(of: "W404") == PaperNotes())
        #expect(library.notesWriteAttempts == [PaperNotes(summary: "x")])
    }

    @Test func observePaperFollowsStatusAndRemoval() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.bert])
        var iterator = library.observePaper(openAlexID: SamplePapers.bert.openAlexID).makeAsyncIterator()
        #expect(await iterator.next() == .some(LibraryPaper(paper: SamplePapers.bert, status: .toRead)))

        try await library.setStatus(openAlexID: SamplePapers.bert.openAlexID, status: .read)
        #expect(await iterator.next() == .some(LibraryPaper(paper: SamplePapers.bert, status: .read)))

        _ = try await library.remove(openAlexID: SamplePapers.bert.openAlexID)
        #expect(await iterator.next() == .some(nil))
    }

    @Test func failuresAndHeldSaves() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.vit], notes: [SamplePapers.vit.openAlexID: PaperNotes(summary: "S")])
        let id = SamplePapers.vit.openAlexID
        #expect(library.notes(of: id) == PaperNotes(summary: "S"))

        library.setFailNotesRead(true)
        await #expect(throws: FakeLibraryRepository.Failure.self) { try await library.notes(openAlexID: id) }
        library.setFailSaveNotes(true)
        await #expect(throws: FakeLibraryRepository.Failure.self) { try await library.saveNotes(openAlexID: id, notes: PaperNotes()) }
        library.setFailSaveNotes(false)

        library.holdNotesSaves()
        let save = Task { try await library.saveNotes(openAlexID: id, notes: PaperNotes(summary: "Held")) }
        while library.heldNotesSaves == 0 { await Task.yield() }
        #expect(library.notes(of: id) == PaperNotes(summary: "S"))
        library.releaseNotesSaves()
        try await save.value
        #expect(library.notes(of: id) == PaperNotes(summary: "Held"))
        #expect(library.notesWriteAttempts == [PaperNotes(), PaperNotes(summary: "Held")])
    }

    @Test func aCollectionFiltersTheLibraryAndUndoBringsTheMembershipBack() async throws {
        let library = FakeLibraryRepository(
            saved: [SamplePapers.attention, SamplePapers.bert],
            collectionMembers: [7: [SamplePapers.bert.openAlexID]]
        )

        var stream = library.observeLibrary(query: "", status: nil, collectionID: 7).makeAsyncIterator()
        let first = try #require(await stream.next())
        #expect(first.papers.map(\.paper.openAlexID) == [SamplePapers.bert.openAlexID])
        #expect(first.libraryTotal == 1)
        #expect(first.allPapersTotal == 2)

        let removed = try #require(try await library.remove(openAlexID: SamplePapers.bert.openAlexID))
        #expect(removed.collectionIDs == [7])
        #expect(await stream.next()?.papers == [])
        try await library.restore(removed)
        #expect(await stream.next()?.papers.map(\.paper.openAlexID) == [SamplePapers.bert.openAlexID])
    }
}
