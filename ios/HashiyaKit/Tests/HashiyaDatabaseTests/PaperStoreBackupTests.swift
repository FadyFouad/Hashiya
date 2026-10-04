import Foundation
import GRDB
import HashiyaModel
import Testing
@testable import HashiyaDatabase

struct PaperStoreBackupTests {
    private let queue: DatabaseQueue
    private let store: PaperStore

    init() throws {
        queue = try HashiyaDatabase.openInMemory()
        store = PaperStore(writer: queue)
    }

    private func record(_ id: String, _ openAlexID: String? = nil, doi: String? = nil, title: String? = nil, citeKey: String? = nil) -> PaperRecord {
        PaperRecord(id: id, openAlexID: openAlexID, doi: doi, title: title ?? "Paper \(id)", year: 2020, venue: nil, abstract: nil,
                    citationCount: 0, isOpenAccess: false, oaPDFURL: nil, savedAt: 1, citeKey: citeKey)
    }

    private func notes(_ paperID: String, _ summary: String) -> PaperNotesRecord {
        PaperNotesRecord(paperID: paperID, notes: PaperNotes(summary: summary), updatedAt: 1)
    }

    /// Saves a paper the way the app does.
    private func saved(_ paper: PaperRecord, notes: PaperNotesRecord? = nil) async throws {
        let authors = [PaperAuthorRecord(paperID: paper.id, position: 0, name: "Device Author", openAlexAuthorID: nil)]
        let search = PaperSearchRow.make(paperID: paper.id, title: paper.title, authorNames: ["Device Author"], abstract: nil,
                                         venue: nil, notes: notes?.notes)
        try await store.insert(paper: paper, authors: authors, search: search, notes: notes)
    }

    private func incoming(_ ref: Int, _ paper: PaperRecord, notes: PaperNotesRecord? = nil, authors: [String] = ["A"]) -> IncomingPaper {
        IncomingPaper(ref: ref, paper: paper,
                      authors: authors.enumerated().map { PaperAuthorRecord(paperID: paper.id, position: $0.offset, name: $0.element, openAlexAuthorID: nil) },
                      notes: notes)
    }

    private func searchCount(_ match: String) async throws -> Int {
        try await queue.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM paper_search WHERE paper_search MATCH ?", arguments: [match]) ?? 0 }
    }

    @Test func addsNewPapersWithAuthorsNotesAndSearchRow() async throws {
        let outcome = try await store.merge(papers: [incoming(1, record("n1", "W1"), notes: notes("n1", "Backup summary"), authors: ["Ada", "Grace"])], collections: [], now: 9)
        #expect(outcome.added == 1)
        let paper = try #require(await store.citablePaper(openAlexID: "W1"))
        #expect(paper.authors.sorted { $0.position < $1.position }.map(\.name) == ["Ada", "Grace"])
        #expect(try await store.notes(openAlexID: "W1")?.summary == "Backup summary")
        #expect(try await searchCount("summary*") == 1)
    }

    @Test func matchesByOpenAlexIDAndKeepsTheDevicePaper() async throws {
        var device = record("d1", "W1", title: "Device title")
        device.readingStatus = "read"
        try await saved(device, notes: notes("d1", "Device notes"))
        let outcome = try await store.merge(papers: [incoming(1, record("n1", "W1", title: "Backup title"), notes: notes("n1", "Backup notes"))], collections: [], now: 9)
        #expect(outcome.added == 0 && outcome.matched == 1 && outcome.notesAdded == 0)
        let paper = try #require(await store.citablePaper(openAlexID: "W1"))
        #expect(paper.paper.title == "Device title")
        #expect(paper.paper.readingStatus == "read")
        #expect(try await store.notes(openAlexID: "W1")?.summary == "Device notes")
    }

    @Test func backupNotesFillAMatchedPaperWithoutNotes() async throws {
        try await saved(record("d1", "W1"))
        let outcome = try await store.merge(papers: [incoming(1, record("n1", "W1"), notes: notes("n1", "Backup notes"))], collections: [], now: 9)
        #expect(outcome.notesAdded == 1)
        #expect(try await store.notes(openAlexID: "W1")?.summary == "Backup notes")
        #expect(try await searchCount("backup*") == 1)
    }

    @Test func matchesByDoiOnlyWhenTheBackupPaperHasNoOpenAlexID() async throws {
        try await saved(record("d1", "W1", doi: "10.1/x"))
        #expect(try await store.merge(papers: [incoming(1, record("n1", nil, doi: "10.1/x"))], collections: [], now: 9).matched == 1)
        // Another work with the same DOI (a preprint and its published version) is a separate paper.
        #expect(try await store.merge(papers: [incoming(1, record("n2", "W2", doi: "10.1/x"))], collections: [], now: 9).added == 1)
    }

    @Test func aTakenCiteKeyIsDropped() async throws {
        try await saved(record("d1", "W1", citeKey: "smith2020"))
        _ = try await store.merge(papers: [incoming(1, record("n1", "W2", citeKey: "smith2020"))], collections: [], now: 9)
        #expect(try await store.citablePaper(openAlexID: "W2")?.paper.citeKey == nil)
    }

    @Test func pdfColumnsAreSetOnlyWhenTheMatchedPaperHasNone() async throws {
        try await saved(record("d1", "W1"))
        try await saved(record("d2", "W2"))
        try await store.setPdf(paperID: "d2", source: "attached", size: 5, addedAt: 1)
        func withPdf(_ id: String, _ oa: String) -> PaperRecord {
            var r = record(id, oa)
            r.pdfSource = "downloaded"; r.pdfSize = 7; r.pdfAddedAt = 3; r.pdfLastPage = 2
            return r
        }
        let outcome = try await store.merge(papers: [incoming(1, withPdf("n1", "W1")), incoming(2, withPdf("n2", "W2")), incoming(3, withPdf("n3", "W3"))], collections: [], now: 9)
        #expect(outcome.pdfTargets == [1: "d1", 3: "n3"])
        #expect(try await store.citablePaper(openAlexID: "W1")?.paper.pdfLastPage == 2)
        #expect(try await store.citablePaper(openAlexID: "W2")?.paper.pdfSource == "attached")
    }

    @Test func collectionsMergeByNameKeyAndLinkNewAndMatchedPapers() async throws {
        try await saved(record("d1", "W1"))
        _ = try #require(await store.insertCollection(name: "Thesis", nameKey: "thesis", createdAt: 1))
        let outcome = try await store.merge(
            papers: [incoming(1, record("n1", "W1")), incoming(2, record("n2", "W2"))],
            collections: [IncomingCollection(name: "THESIS", nameKey: "thesis", createdAt: 5, refs: [1, 2]),
                          IncomingCollection(name: "Review", nameKey: "review", createdAt: 6, refs: [2, 99])],
            now: 9)
        #expect(outcome.collectionsCreated == 1)
        var counts: [String: Int] = [:]
        for await list in store.observeCollections() { counts = Dictionary(uniqueKeysWithValues: list.map { ($0.name, $0.paperCount) }); break }
        #expect(counts == ["Thesis": 2, "Review": 1])
    }

    @Test func aFailureRollsBackTheWholeMerge() async throws {
        let broken = IncomingPaper(ref: 2, paper: record("n2", "W2"),
                                   authors: [PaperAuthorRecord(paperID: "no-such-paper", position: 0, name: "X", openAlexAuthorID: nil)], notes: nil)
        await #expect(throws: (any Error).self) {
            try await store.merge(papers: [incoming(1, record("n1", "W1")), broken], collections: [], now: 9)
        }
        #expect(try await store.paperCount() == 0)
    }

    @Test func snapshotReadsEverything() async throws {
        try await saved(record("d1", "W1"), notes: notes("d1", "N"))
        let id = try #require(await store.insertCollection(name: "C", nameKey: "c", createdAt: 1))
        try await store.addToCollection(collectionID: id, openAlexID: "W1", addedAt: 2)
        let snapshot = try await store.backupSnapshot()
        #expect(snapshot.papers.map(\.paper.id) == ["d1"])
        #expect(snapshot.notes.map(\.summary) == ["N"])
        #expect(snapshot.collections.map(\.name) == ["C"])
        #expect(snapshot.links.map { "\($0.collectionID)-\($0.paperID)" } == ["\(id)-d1"])
        #expect(try await store.pdfTotals() == PdfTotals(count: 0, bytes: 0))
    }
}
