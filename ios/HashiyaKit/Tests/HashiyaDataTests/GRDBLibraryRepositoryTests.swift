import Foundation
import HashiyaData
import HashiyaDatabase
import HashiyaModel
import HashiyaTesting
import os
import Testing

struct GRDBLibraryRepositoryTests {
    /// A repository on a fresh in-memory database whose clock advances 1 000 ms per save.
    private func makeRepository() throws -> GRDBLibraryRepository {
        let clock = OSAllocatedUnfairLock(initialState: Int64(0))
        let ids = OSAllocatedUnfairLock(initialState: 0)
        return try GRDBLibraryRepository.inMemory(
            now: { clock.withLock { $0 += 1_000; return $0 } },
            newID: { ids.withLock { $0 += 1; return "local-\($0)" } }
        )
    }

    private func value<T: Sendable>(of stream: AsyncStream<T>, where predicate: (T) -> Bool = { _ in true }) async -> T? {
        for await value in stream where predicate(value) {
            return value
        }
        return nil
    }

    @Test func savedPapersAreNewestFirstWithAuthorsInOrder() async throws {
        let repository = try makeRepository()
        try await repository.save(SamplePapers.attention)
        try await repository.save(SamplePapers.bert)

        let papers = await value(of: repository.observeSavedPapers())
        #expect(papers == [SamplePapers.bert, SamplePapers.attention])
    }

    @Test func savedIDsListTheLibrary() async throws {
        let repository = try makeRepository()
        #expect(await value(of: repository.observeSavedIDs()) == [])
        try await repository.save(SamplePapers.attention)
        try await repository.save(SamplePapers.vit)

        #expect(await value(of: repository.observeSavedIDs()) == ["W2626778328", "W3094502228"])
    }

    @Test func savingTwiceKeepsOne() async throws {
        let repository = try makeRepository()
        try await repository.save(SamplePapers.attention)
        try await repository.save(SamplePapers.attention)

        #expect(await value(of: repository.observeSavedPapers())?.count == 1)
    }

    @Test func removeThenRestoreReturnsThePaperToItsPosition() async throws {
        let repository = try makeRepository()
        for paper in [SamplePapers.attention, SamplePapers.bert, SamplePapers.vit] {
            try await repository.save(paper)
        }

        let removed = try #require(try await repository.remove(openAlexID: SamplePapers.bert.openAlexID))
        #expect(removed == RemovedPaper(paper: SamplePapers.bert, localID: "local-2", savedAt: 2_000))
        #expect(await value(of: repository.observeSavedPapers()) == [SamplePapers.vit, SamplePapers.attention])

        try await repository.restore(removed)
        #expect(await value(of: repository.observeSavedPapers()) == [SamplePapers.vit, SamplePapers.bert, SamplePapers.attention])
    }

    @Test func restoreAfterSavingAgainIsANoOp() async throws {
        let repository = try makeRepository()
        try await repository.save(SamplePapers.attention)
        try await repository.save(SamplePapers.bert)
        let removed = try #require(try await repository.remove(openAlexID: SamplePapers.attention.openAlexID))
        try await repository.save(SamplePapers.attention)

        try await repository.restore(removed)

        #expect(await value(of: repository.observeSavedPapers()) == [SamplePapers.attention, SamplePapers.bert])
    }

    @Test func removingAnUnknownPaperReturnsNil() async throws {
        let repository = try makeRepository()
        #expect(try await repository.remove(openAlexID: "W404") == nil)
    }

    @Test func observationsFollowChanges() async throws {
        let repository = try makeRepository()
        let stream = repository.observeSavedPapers()
        var iterator = stream.makeAsyncIterator()
        #expect(await iterator.next() == [])

        try await repository.save(SamplePapers.vit)
        var latest = await iterator.next()
        while latest?.isEmpty == true {
            latest = await iterator.next()
        }
        #expect(latest == [SamplePapers.vit])
    }

    /// A paper saved by the Share Extension (another pool on the same file) appears after a refresh.
    @Test func refreshShowsPapersSavedThroughAnotherPool() async throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appending(path: "hashiya.sqlite")
        let app = GRDBLibraryRepository(store: try PaperStore.open(at: url))
        let shareExtension = GRDBLibraryRepository(store: try PaperStore.open(at: url))
        var papers = app.observeSavedPapers().makeAsyncIterator()
        #expect(await papers.next() == [])

        try await shareExtension.save(SamplePapers.attention)
        await app.refreshAfterExternalChanges()

        #expect(await papers.next() == [SamplePapers.attention])
    }
}
