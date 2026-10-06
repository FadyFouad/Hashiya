import Foundation
import GRDB
import HashiyaData
import HashiyaDatabase
import HashiyaModel
import HashiyaNetwork
import HashiyaTesting
import os
import Testing

/// A journal article for `id` ("W1"), as OpenAlex returns it once `type` and `biblio` are selected.
private func journalWork(_ id: String) -> NetworkWork {
    NetworkWork(
        id: "https://openalex.org/\(id)",
        primaryLocation: NetworkLocation(source: NetworkSource(displayName: "Nature", type: "journal")),
        type: "article",
        biblio: NetworkBiblio(volume: "521", firstPage: "436", lastPage: "444")
    )
}

/// Runs `onWork` for each request, then answers with a journal article.
private struct ScriptedLookup: OpenAlexLookupService {
    let onWork: @Sendable (String) async throws -> Void

    func work(id: String) async throws -> NetworkWork? {
        try await onWork(id)
        return journalWork(id)
    }

    func works(filter: String, perPage: Int) async throws -> NetworkWorksResponse { throw NetworkFailure.unknown }
}

/// Counts requests running at once; each takes 20 ms.
private final class CountingLookup: OpenAlexLookupService {
    private struct Counts { var running = 0, peak = 0, requests = 0 }
    private let counts = OSAllocatedUnfairLock(initialState: Counts())

    var peak: Int { counts.withLock { $0.peak } }
    var requests: Int { counts.withLock { $0.requests } }

    func work(id: String) async throws -> NetworkWork? {
        counts.withLock { counts in
            counts.requests += 1
            counts.running += 1
            counts.peak = max(counts.peak, counts.running)
        }
        try await Task.sleep(for: .milliseconds(20))
        counts.withLock { $0.running -= 1 }
        return journalWork(id)
    }

    func works(filter: String, perPage: Int) async throws -> NetworkWorksResponse { throw NetworkFailure.unknown }
}

/// Mirrors Android's `RoomCitationRepositoryTest`.
struct GRDBCitationRepositoryTests {
    private let queue: DatabaseQueue
    private let store: PaperStore
    private let library: GRDBLibraryRepository
    private let openAlex = FakeOpenAlexLookupService()

    init() throws {
        let clock = OSAllocatedUnfairLock(initialState: Int64(0))
        let ids = OSAllocatedUnfairLock(initialState: 0)
        queue = try HashiyaDatabase.openInMemory()
        store = PaperStore(writer: queue)
        library = GRDBLibraryRepository(
            store: store,
            now: { clock.withLock { $0 += 1; return $0 } },
            newID: { ids.withLock { $0 += 1; return "local-\($0)" } }
        )
    }

    private func repository(_ lookup: (any OpenAlexLookupService)? = nil) -> GRDBCitationRepository {
        GRDBCitationRepository(store: store, lookup: lookup ?? openAlex)
    }

    private func paper(_ id: String, _ surname: String, title: String = "Deep nets") -> Paper {
        Paper(openAlexID: id, title: title, authors: [Author(name: "Jane \(surname)")], year: 2020, venue: "Nature")
    }

    /// Saves the paper as a v3 library left it: no details, details_fetched = 0.
    private func saveUnfetched(_ paper: Paper) async throws {
        try await library.save(paper)
        try await queue.write { db in
            try db.execute(sql: "UPDATE papers SET details_fetched = 0 WHERE open_alex_id = ?", arguments: [paper.openAlexID])
        }
    }

    private func detailsFetched(_ openAlexID: String) async throws -> Bool {
        try #require(try await store.citablePaper(openAlexID: openAlexID)).paper.detailsFetched
    }

    private func citeKey(_ openAlexID: String) async throws -> String? {
        try await store.citablePaper(openAlexID: openAlexID)?.paper.citeKey
    }

    /// The cite keys of the file's entries, in order.
    private func keys(_ bibtex: String) -> [String] {
        bibtex.split(separator: "\n").filter { $0.hasPrefix("@") }.compactMap { line in
            guard let open = line.firstIndex(of: "{"), let comma = line.firstIndex(of: ",") else { return nil }
            return String(line[line.index(after: open)..<comma])
        }
    }

    @Test func entryRefetchesOnceThenUsesStoredDetailsAndKey() async throws {
        try await saveUnfetched(paper("W1", "Smith"))
        openAlex.setWorks(["W1": journalWork("W1")])

        let first = try #require(try await repository().entry(openAlexID: "W1"))
        let second = try #require(try await repository().entry(openAlexID: "W1"))

        #expect(first.complete)
        #expect(openAlex.workRequests == ["W1"])
        #expect(first.text.hasPrefix("@article{smith2020deep,\n"))
        #expect(first.text.contains("  volume = {521},"))
        #expect(first == second)
    }

    @Test func papersSavedAfterV4AreNotRefetched() async throws {
        try await library.save(paper("W1", "Smith"))
        _ = try await repository().entry(openAlexID: "W1")
        _ = try await repository().export(collectionID: nil)
        #expect(openAlex.workRequests == [])
    }

    @Test func onlyUnfetchedPapersAreRefetched() async throws {
        try await library.save(paper("W1", "Smith"))
        try await saveUnfetched(paper("W2", "Jones"))
        openAlex.setWorks(["W2": journalWork("W2")])

        let result = try await repository().export(collectionID: nil)

        #expect(openAlex.workRequests == ["W2"])
        #expect(result.complete)
    }

    @Test func unsavedPaperHasNoEntry() async throws {
        #expect(try await repository().entry(openAlexID: "W404") == nil)
    }

    /// Android's `failedRefetchIsIncompleteAndRetriedNextTime`.
    @Test func aFailedRefetchIsIncompleteAndRetriedNextTime() async throws {
        try await saveUnfetched(paper("W1", "Smith"))
        openAlex.setWorkFailure(.connectivity)

        let offline = try await repository().export(collectionID: nil)
        #expect(!offline.complete)
        #expect(offline.text.hasPrefix("@misc{smith2020deep,"))
        #expect(try await !detailsFetched("W1"))

        openAlex.setWorkFailure(nil)
        openAlex.setWorks(["W1": journalWork("W1")])
        let online = try await repository().export(collectionID: nil)
        #expect(online.complete)
        #expect(online.text.hasPrefix("@article{smith2020deep,"))
        // pages is the last field here (no DOI or URL), so it has no trailing comma.
        #expect(online.text.contains("  pages = {436--444}\n}"))
        #expect(openAlex.workRequests == ["W1", "W1"])
        #expect(try await detailsFetched("W1"))
    }

    @Test func oneFailedRefetchDoesNotStopTheOthers() async throws {
        try await saveUnfetched(paper("W1", "Adams"))
        try await saveUnfetched(paper("W2", "Brown"))
        let flaky = ScriptedLookup { id in if id == "W1" { throw NetworkFailure.connectivity } }

        let result = try await repository(flaky).export(collectionID: nil)

        #expect(!result.complete)
        #expect(result.text.contains("@misc{adams2020deep,"))
        #expect(result.text.contains("@article{brown2020deep,"))
        #expect(try await !detailsFetched("W1"))
        #expect(try await detailsFetched("W2"))
    }

    @Test func aWorkOpenAlexNoLongerHasIsMarkedFetched() async throws {
        try await saveUnfetched(paper("W1", "Smith"))
        openAlex.setWorks([:])

        #expect(try await repository().export(collectionID: nil).complete)
        _ = try await repository().export(collectionID: nil)
        #expect(openAlex.workRequests == ["W1"])
    }

    @Test func keysAreAssignedInSavedOrderAndNeverChange() async throws {
        try await library.save(paper("W1", "Smith"))
        try await library.save(paper("W2", "Smith"))
        let first = try await repository().export(collectionID: nil)
        #expect(first.text.contains("@misc{smith2020deep,"))
        #expect(first.text.contains("@misc{smith2020deepa,"))

        // A paper saved later with the same base key gets the next suffix; the first two keep theirs.
        try await library.save(paper("W3", "Smith"))
        let again = try await repository().export(collectionID: nil)
        #expect(keys(again.text) == ["smith2020deep", "smith2020deepa", "smith2020deepb"])
        #expect(try await citeKey("W1") == "smith2020deep")
        #expect(try await citeKey("W3") == "smith2020deepb")
    }

    /// Export, remove a paper, restore it with Undo, export again: its key is unchanged.
    @Test func keysSurviveRemoveAndUndo() async throws {
        try await library.save(paper("W1", "Smith"))
        try await library.save(paper("W2", "Smith"))
        _ = try await repository().export(collectionID: nil)
        #expect(try await citeKey("W1") == "smith2020deep")

        let removed = try #require(try await library.remove(openAlexID: "W1"))
        try await library.restore(removed)
        let again = try await repository().export(collectionID: nil)

        #expect(try await citeKey("W1") == "smith2020deep")
        #expect(try await citeKey("W2") == "smith2020deepa")
        #expect(keys(again.text) == ["smith2020deep", "smith2020deepa"])
    }

    @Test func keysDoNotDependOnWhichCollectionIsExportedFirst() async throws {
        try await library.save(paper("W1", "Smith"))
        try await library.save(paper("W2", "Smith"))
        let id = try #require(try await store.insertCollection(name: "A", nameKey: "a", createdAt: 1))
        try await store.addToCollection(collectionID: id, openAlexID: "W2", addedAt: 1)

        #expect(try await repository().export(collectionID: id).text.hasPrefix("@misc{smith2020deepa,"))
        #expect(try await citeKey("W1") == "smith2020deep")
    }

    @Test func exportCoversTheWholeCollectionRegardlessOfStatus() async throws {
        try await library.save(paper("W1", "Adams"))
        try await library.save(paper("W2", "Brown"))
        try await library.save(paper("W3", "Clark"))
        try await library.setStatus(openAlexID: "W2", status: .read)
        let id = try #require(try await store.insertCollection(name: "A", nameKey: "a", createdAt: 1))
        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 1)
        try await store.addToCollection(collectionID: id, openAlexID: "W2", addedAt: 1)

        let bibtex = try await repository().export(collectionID: id).text
        #expect(bibtex.contains("{adams2020deep,"))
        #expect(bibtex.contains("{brown2020deep,"))
        #expect(!bibtex.contains("clark"))
    }

    @Test func emptyCollectionExportsAnEmptyCompleteFile() async throws {
        let id = try #require(try await store.insertCollection(name: "A", nameKey: "a", createdAt: 1))
        #expect(try await repository().export(collectionID: id) == CitationResult(text: "", complete: true))
    }

    @Test func anAPAEntryHasPlainAndHTMLText() async throws {
        try await library.save(paper("W1", "Smith"))
        let result = try #require(try await repository().entry(openAlexID: "W1", style: .apa))
        #expect(result.text.contains("(20"))
        #expect(result.html?.contains("<i>") == true)
        #expect(result.rtf == nil)
    }

    @Test func anIEEEExportNumbersInSavedOrderAndHasRTF() async throws {
        try await library.save(paper("W1", "Smith", title: "Zebra studies"))
        try await library.save(paper("W2", "Jones", title: "Aardvark studies"))
        let result = try await repository().export(collectionID: nil, style: .ieee)
        #expect(result.text.hasPrefix("[1] "))
        let lines = result.text.split(separator: "\n").map(String.init)
        #expect(try #require(lines.first { $0.hasPrefix("[1]") }).contains("Zebra"))
        #expect(try #require(lines.first { $0.hasPrefix("[2]") }).contains("Aardvark"))
        #expect(result.rtf?.hasPrefix("{\\rtf1") == true)
    }

    @Test func anEmptyCollectionExportsAnEmptyRTFDocument() async throws {
        let id = try #require(try await store.insertCollection(name: "A", nameKey: "a", createdAt: 1))
        let result = try await repository().export(collectionID: id, style: .apa)
        #expect(result.rtf == "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs24\n}")
        #expect(result.text == "")
        #expect(result.complete)
    }

    @Test func bibtexIsUnchangedAndHasNoRichForms() async throws {
        try await library.save(paper("W1", "Smith"))
        let result = try #require(try await repository().entry(openAlexID: "W1"))
        #expect(result.html == nil)
        #expect(result.rtf == nil)
    }

    @Test func aPaperRemovedDuringTheExportIsLeftOut() async throws {
        try await saveUnfetched(paper("W1", "Adams"))
        try await saveUnfetched(paper("W2", "Brown"))
        let library = self.library
        let removing = ScriptedLookup { id in if id == "W2" { _ = try await library.remove(openAlexID: "W2") } }

        let result = try await repository(removing).export(collectionID: nil)

        #expect(result.complete)
        #expect(result.text.contains("{adams2020deep,"))
        #expect(!result.text.contains("brown"))
    }

    @Test func aPaperRemovedDuringACopyHasNoEntry() async throws {
        try await saveUnfetched(paper("W1", "Adams"))
        let library = self.library
        let removing = ScriptedLookup { id in _ = try await library.remove(openAlexID: id) }

        #expect(try await repository(removing).entry(openAlexID: "W1") == nil)
    }

    @Test func refetchesAtMostFourAtATime() async throws {
        for index in 0..<9 {
            try await saveUnfetched(paper("W\(index)", "S\(index)"))
        }
        let slow = CountingLookup()

        #expect(try await repository(slow).export(collectionID: nil).complete)
        #expect(slow.peak == 4)
        #expect(slow.requests == 9)
    }

    @Test func cancellingTheExportStopsTheRefetch() async throws {
        for index in 0..<9 {
            try await saveUnfetched(paper("W\(index)", "S\(index)"))
        }
        let slow = CountingLookup()
        let repository = repository(slow)

        let export = Task { try await repository.export(collectionID: nil) }
        export.cancel()

        await #expect(throws: CancellationError.self) { try await export.value }
        #expect(slow.requests < 9)
    }
}
