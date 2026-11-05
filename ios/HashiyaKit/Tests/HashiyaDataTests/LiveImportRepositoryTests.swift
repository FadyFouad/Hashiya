import Foundation
@testable import HashiyaData
import HashiyaDatabase
import HashiyaDiagnostics
import HashiyaModel
import HashiyaNetwork
import HashiyaTesting
import os
import Testing

/// The iOS counterpart of Android's `ImportRunnerTest` and `DefaultImportRepositoryTest`.
struct LiveImportRepositoryTests {
    private let directory = FileManager.default.temporaryDirectory.appending(path: "Imports-\(UUID().uuidString)")
    private let analytics = FakeAnalytics()

    private static let bib = """
        @article{lecun2015, title = {Deep learning}, author = {LeCun, Yann}, year = 2015, doi = {10.1038/nature14539}}
        @misc{local2019, title = {Lernverfahren f{\\"u}r gro{\\ss}e Sprachmodelle}, author = {M{\\"u}ller, J{\\"o}rg}, year = 2019}
        @article{again, title = {Deep learning}, doi = {https://doi.org/10.1038/NATURE14539}}
        @article{broken, title = {never closed,
        """

    private func work(_ doi: String) -> NetworkWork {
        NetworkWork(id: "https://openalex.org/W1", doi: "https://doi.org/" + doi, displayName: "OpenAlex: \(doi)")
    }

    private func repository(
        store: PaperStore, service: FakeOpenAlexLookupService = FakeOpenAlexLookupService()
    ) -> LiveImportRepository {
        let ids = OSAllocatedUnfairLock(initialState: 0)
        return LiveImportRepository(
            store: store, matcher: DOIMatcher(openAlex: service, pause: { _ in }), directory: directory, analytics: analytics,
            newID: { "id\(ids.withLock { $0 += 1; return $0 })" }, now: { 1_000 }
        )
    }

    /// The job once `condition` holds, from the repository's stream.
    private func job(_ repository: LiveImportRepository, until condition: (ImportJob?) -> Bool) async -> ImportJob? {
        for await job in repository.observeCurrentJob() where condition(job) {
            return job
        }
        return nil
    }

    private func papers(_ store: PaperStore) async throws -> [LibraryKeyRow] {
        try await store.libraryKeys(jobID: -1).sorted { ($0.citeKey ?? "") < ($1.citeKey ?? "") }
    }

    private func importToTheEnd(_ repository: LiveImportRepository, addToCollection: Bool = true) async -> ImportJob? {
        let started = await repository.start(fileName: "thesis.bib", data: Data(Self.bib.utf8))
        _ = await job(repository) { $0?.state == .ready }
        await repository.confirm(jobID: started.id, addToCollection: addToCollection)
        return await job(repository) { $0?.state == .done }
    }

    @Test func analysisCountsWhatTheFileHoldsAndSavesNothing() async throws {
        let store = try PaperStore.inMemory()
        let repository = repository(store: store, service: FakeOpenAlexLookupService(found: [work("10.1038/nature14539")]))
        let started = await repository.start(fileName: "thesis.bib", data: Data(Self.bib.utf8))
        #expect(started.collectionName == "thesis")
        let ready = await job(repository) { $0?.state == .ready }
        #expect(ready?.counts == ImportCounts(found: 3, matched: 1, local: 1, repeated: 1, unreadable: 1))
        #expect(try await papers(store).isEmpty)
        #expect(analytics.events.isEmpty)
    }

    @Test func importingSavesMatchedAndLocalPapersWithTheirCiteKeysIntoTheCollection() async throws {
        let store = try PaperStore.inMemory()
        let service = FakeOpenAlexLookupService(found: [work("10.1038/nature14539")])
        let done = await importToTheEnd(repository(store: store, service: service))
        #expect(done?.imported == 2)
        #expect(done?.processed == 2)
        let keys = try await papers(store)
        #expect(keys.map(\.citeKey) == ["lecun2015", "local2019"])
        #expect(keys.map(\.title) == ["OpenAlex: 10.1038/nature14539", "Lernverfahren für große Sprachmodelle"])
        #expect(keys[1].openAlexID.map(isLocalPaperID) == true)
        let collection = try #require(done?.collectionID)
        #expect(try await store.citablePapers(collectionID: collection).count == 2)
        // The import phase used the analysis's lookups.
        #expect(service.worksRequests.count == 1)
        #expect(analytics.events == [
            .importFile(format: .bibtex, result: .ok, entries: .upTo50, matched: .most, toCollection: true),
        ])
    }

    @Test func aTakenCollectionNameGetsANumberAndNoCollectionWhenNotWanted() async throws {
        let store = try PaperStore.inMemory()
        _ = try await store.insertCollection(name: "Thesis", nameKey: collectionNameKey("Thesis"), createdAt: 1)
        let repository = repository(store: store)
        let done = await importToTheEnd(repository)
        #expect(done?.collectionName == "thesis (2)")

        await repository.dismiss(jobID: try #require(done?.id))
        let none = await repository.start(fileName: "other.bib", data: Data("@article{b, title = {B}}".utf8))
        _ = await job(repository) { $0?.state == .ready }
        await repository.confirm(jobID: none.id, addToCollection: false)
        let second = await job(repository) { $0?.state == .done }
        #expect(second?.collectionID == nil)
        #expect(second?.addToCollection == false)
    }

    @Test func aFileOverTheLimitOrNotBibTeXOrRISFails() async throws {
        let store = try PaperStore.inMemory()
        let repository = repository(store: store)
        let large = await repository.start(fileName: "huge.bib", data: Data(count: maxImportFileBytes + 1))
        #expect(large.failure == .tooLarge)
        await repository.dismiss(jobID: large.id)
        _ = await repository.start(fileName: "notes.txt", data: Data("just some notes".utf8))
        let failed = await job(repository) { $0?.state == .failed }
        #expect(failed?.failure == .unsupported)
        let results = analytics.events.compactMap { event -> ImportResult? in
            if case let .importFile(_, result, _, _, _) = event { return result }
            return nil
        }
        #expect(results == [.tooLarge, .unsupported])
    }

    @Test func aFileWithNothingNewFailsAsNothingToImport() async throws {
        let store = try PaperStore.inMemory()
        let saved = Paper(openAlexID: "W9", title: "Same", year: 2020).asRecords(localID: "p1", savedAt: 1)
        try await store.insert(paper: saved.paper, authors: [], search: saved.searchRow)
        let repository = repository(store: store)
        _ = await repository.start(fileName: "a.bib", data: Data("@article{a, title = {Same}, year = 2020}".utf8))
        #expect(await job(repository) { $0?.state == .failed }?.failure == .nothingToImport)
        #expect(analytics.events == [.importFile(format: .bibtex, result: .nothingNew, entries: .upTo50, matched: .none, toCollection: false)])
    }

    @Test func aJobLeftImportingResumesInANewRepositoryWithoutSavingTwice() async throws {
        let store = try PaperStore.inMemory()
        let done = try #require(await importToTheEnd(repository(store: store)))
        // The app was killed after the last batch, before the job was marked done: a new process finds it importing.
        var record = try #require(try await store.importJob(id: done.id))
        record.state = "importing"
        #expect(try await store.replaceImportJob(ifState: "done", with: record))
        let second = repository(store: store)
        await second.resumeIfNeeded()
        let resumed = await job(second) { $0?.state == .done }
        #expect(resumed?.imported == 2)
        #expect(resumed?.processed == 2)
        #expect(try await papers(store).count == 2)
        #expect(try await store.citablePapers(collectionID: try #require(resumed?.collectionID)).count == 2)
    }

    @Test func startReturnsTheRunningJobAndDismissesAFinishedOne() async throws {
        let store = try PaperStore.inMemory()
        let repository = repository(store: store)
        let done = try #require(await importToTheEnd(repository))
        let next = await repository.start(fileName: "next.bib", data: Data("@article{c, title = {C}}".utf8))
        #expect(next.id != done.id)
        #expect(try await store.importJob(id: done.id) == nil)
    }

    @Test func cancelStopsTheJobAndDismissForgetsIt() async throws {
        let store = try PaperStore.inMemory()
        let repository = repository(store: store)
        let started = await repository.start(fileName: "a.bib", data: Data(Self.bib.utf8))
        _ = await job(repository) { $0?.state == .ready }
        await repository.cancel(jobID: started.id)
        #expect(await job(repository) { $0 != nil }?.state == .cancelled)
        #expect(analytics.events == [.importFile(format: .bibtex, result: .cancelled, entries: .upTo50, matched: .none, toCollection: false)])
        await repository.dismiss(jobID: started.id)
        #expect(await job(repository) { _ in true } == nil)
        #expect(!FileManager.default.fileExists(atPath: directory.appending(path: "\(started.id).txt").path()))
    }

    @Test func aMissingFileFailsAsUnreadable() async throws {
        let store = try PaperStore.inMemory()
        let id = try await store.createImportJob(ImportJobRecord(fileName: "gone.ris", createdAt: 1, state: "analyzing"))
        let repository = repository(store: store)
        await repository.resumeIfNeeded()
        let failed = await job(repository) { $0?.state == .failed }
        #expect(failed?.id == id)
        #expect(failed?.failure == .unreadable)
    }
}
