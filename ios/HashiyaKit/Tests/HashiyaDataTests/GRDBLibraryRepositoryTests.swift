import Foundation
import GRDB
import HashiyaData
import HashiyaDatabase
import HashiyaModel
import HashiyaTesting
import os
import Testing

struct GRDBLibraryRepositoryTests {
    private let queue: DatabaseQueue
    private let repository: GRDBLibraryRepository

    /// A repository on a fresh in-memory database whose clock advances 1 000 ms per save.
    init() throws {
        let clock = OSAllocatedUnfairLock(initialState: Int64(0))
        let ids = OSAllocatedUnfairLock(initialState: 0)
        queue = try HashiyaDatabase.openInMemory()
        repository = GRDBLibraryRepository(
            store: PaperStore(writer: queue),
            now: { clock.withLock { $0 += 1_000; return $0 } },
            newID: { ids.withLock { $0 += 1; return "local-\($0)" } }
        )
    }

    /// Android's `RoomLibraryRepositoryTest` paper: two authors, and the title, abstract and venue given.
    private func paper(_ id: String, title: String? = nil, abstract: String? = nil, venue: String? = nil) -> Paper {
        Paper(
            openAlexID: id,
            title: title ?? "Paper \(id)",
            authors: [Author(name: "First"), Author(name: "Second")],
            year: 2020,
            venue: venue,
            abstract: abstract
        )
    }

    private func value<T: Sendable>(of stream: AsyncStream<T>, where predicate: (T) -> Bool = { _ in true }) async -> T? {
        for await value in stream where predicate(value) {
            return value
        }
        return nil
    }

    private func library(_ query: String = "", status: ReadingStatus? = nil) async -> LibrarySnapshot? {
        await value(of: repository.observeLibrary(query: query, status: status))
    }

    private func ids(_ query: String = "", status: ReadingStatus? = nil) async -> [String]? {
        await library(query, status: status)?.papers.map(\.paper.openAlexID)
    }

    private func counts(_ query: String) async -> [ReadingStatus: Int]? {
        await library(query)?.counts
    }

    @Test func savedPapersAreNewestFirstWithAuthorsInOrderAndStartAsToRead() async throws {
        try await repository.save(SamplePapers.attention)
        try await repository.save(SamplePapers.bert)

        let papers = await library()?.papers
        #expect(papers == [LibraryPaper(paper: SamplePapers.bert, status: .toRead), LibraryPaper(paper: SamplePapers.attention, status: .toRead)])
    }

    @Test func savedIDsListTheLibrary() async throws {
        #expect(await value(of: repository.observeSavedIDs()) == [])
        try await repository.save(SamplePapers.attention)
        try await repository.save(SamplePapers.vit)

        #expect(await value(of: repository.observeSavedIDs()) == ["W2626778328", "W3094502228"])
    }

    @Test func savingTwiceKeepsOneCopyAndItsStatus() async throws {
        try await repository.save(paper("W1"))
        try await repository.setStatus(openAlexID: "W1", status: .reading)
        try await repository.save(paper("W1"))

        #expect(await library()?.papers == [LibraryPaper(paper: paper("W1"), status: .reading)])
    }

    @Test func removeThenRestoreReturnsThePaperToItsPositionWithItsStatus() async throws {
        for paper in [SamplePapers.attention, SamplePapers.bert, SamplePapers.vit] {
            try await repository.save(paper)
        }
        try await repository.setStatus(openAlexID: SamplePapers.bert.openAlexID, status: .reading)

        let removed = try #require(try await repository.remove(openAlexID: SamplePapers.bert.openAlexID))
        #expect(removed == RemovedPaper(paper: SamplePapers.bert, localID: "local-2", savedAt: 2_000, status: .reading))
        #expect(await ids() == [SamplePapers.vit.openAlexID, SamplePapers.attention.openAlexID])

        try await repository.restore(removed)
        #expect(await ids() == [SamplePapers.vit.openAlexID, SamplePapers.bert.openAlexID, SamplePapers.attention.openAlexID])
        #expect(await ids(status: .reading) == [SamplePapers.bert.openAlexID])
        #expect(await ids("devlin bidirectional") == [SamplePapers.bert.openAlexID])
    }

    @Test func restoreAfterSavingAgainIsANoOp() async throws {
        try await repository.save(SamplePapers.attention)
        try await repository.save(SamplePapers.bert)
        let removed = try #require(try await repository.remove(openAlexID: SamplePapers.attention.openAlexID))
        try await repository.save(SamplePapers.attention)

        try await repository.restore(removed)

        #expect(await ids() == [SamplePapers.attention.openAlexID, SamplePapers.bert.openAlexID])
    }

    @Test func removingAnUnknownPaperReturnsNil() async throws {
        #expect(try await repository.remove(openAlexID: "W404") == nil)
    }

    @Test func setStatusDoesNotReorder() async throws {
        try await repository.save(paper("W1"))
        try await repository.save(paper("W2"))

        try await repository.setStatus(openAlexID: "W1", status: .read)

        #expect(await ids() == ["W2", "W1"])
        #expect(await ids(status: .read) == ["W1"])
    }

    @Test func setStatusOfAnUnsavedPaperDoesNothing() async throws {
        try await repository.setStatus(openAlexID: "W404", status: .read)
        #expect(await ids() == [])
    }

    @Test func searchFindsTheTitleAuthorsAbstractAndVenueByPrefix() async throws {
        try await repository.save(paper("W1", title: "Attention Is All You Need", abstract: "The Transformer architecture", venue: "NeurIPS"))
        var resnet = paper("W2", title: "Deep Residual Learning", venue: "CVPR")
        resnet.authors = [Author(name: "Kaiming He")]
        try await repository.save(resnet)

        #expect(await ids("transf") == ["W1"])
        #expect(await ids("kaiming") == ["W2"])
        #expect(await ids("neurips") == ["W1"])
        #expect(await ids("DEEP resid") == ["W2"])
        #expect(await ids("attention residual") == [])
        #expect(await ids("   ") == ["W2", "W1"])
    }

    @Test func searchIgnoresAccentsTashkeelAndAlefForms() async throws {
        try await repository.save(paper("W1", title: "Schrödinger equations"))
        try await repository.save(paper("W2", title: "تطبيقات التعلم العميق في معالجة اللغة"))
        try await repository.save(paper("W3", title: "أساسيات الإحصاء"))

        #expect(await ids("schrodinger") == ["W1"])
        #expect(await ids("التَّعلُّم") == ["W2"])
        #expect(await ids("اساسيات") == ["W3"])
        #expect(await ids("الاحصاء") == ["W3"])
    }

    @Test func searchMatchesArabicIndicAndASCIIDigitsEitherWay() async throws {
        try await repository.save(paper("W1", title: "COVID-19 outcomes"))
        try await repository.save(paper("W2", title: "جائحة كوفيد-١٩"))

        #expect(await ids("١٩") == ["W2", "W1"])
        #expect(await ids("19") == ["W2", "W1"])
        #expect(await ids("كوفيد ۱۹") == ["W2"])
    }

    /// Whatever the user types reaches SQLite as plain words. A MATCH syntax error would end the stream without a value.
    @Test(arguments: ["\"", "C++", "templates\"", "-templates", "-x", "(guide", "title:guide", "BERT:", "a AND", "NEAR/2", "*", "^x"])
    func searchTextWithFTSSyntaxNeverFails(query: String) async throws {
        try await repository.save(paper("W1", title: "C++ templates: a guide"))

        #expect(await library(query) != nil)
        #expect(await library(query, status: .reading) != nil)
    }

    @Test func quotesAndStarsAreIgnored() async throws {
        try await repository.save(paper("W1", title: "C++ templates: a guide"))

        #expect(await ids("\"templates") == ["W1"])
        #expect(await ids("*") == ["W1"])
    }

    @Test func countsFollowTheSearchAndFillMissingStatusesWithZero() async throws {
        try await repository.save(paper("W1", title: "Transformers one"))
        try await repository.save(paper("W2", title: "Transformers two"))
        try await repository.save(paper("W3", title: "Convolutions"))
        try await repository.setStatus(openAlexID: "W1", status: .reading)

        #expect(await counts("") == [.toRead: 2, .reading: 1, .read: 0])
        #expect(await counts("transf") == [.toRead: 1, .reading: 1, .read: 0])
        #expect(await counts("missing") == [.toRead: 0, .reading: 0, .read: 0])
        #expect(await library("missing")?.matchingTotal == 0)
        #expect(await library("missing")?.libraryTotal == 3)
    }

    @Test func anUnknownStoredStatusReadsAndCountsAsToRead() async throws {
        try await repository.save(paper("W1"))
        try await repository.save(paper("W2"))
        try await queue.write { db in
            try db.execute(sql: "UPDATE papers SET reading_status = 'archived' WHERE open_alex_id = 'W1'")
        }

        #expect(await library()?.papers.last == LibraryPaper(paper: paper("W1"), status: .toRead))
        #expect(await counts("") == [.toRead: 2, .reading: 0, .read: 0])
    }

    /// Papers, counts and the total come from one read, so they never disagree — not even while the only paper goes.
    @Test func removingTheOnlyPaperNeverEmitsASnapshotWhosePapersAndCountsDisagree() async throws {
        try await repository.save(paper("W1", title: "Transformers"))

        var snapshots: [LibrarySnapshot] = []
        for await snapshot in repository.observeLibrary(query: "transf", status: .toRead) {
            snapshots.append(snapshot)
            if snapshots.count == 1 {
                _ = try await repository.remove(openAlexID: "W1")
            }
            if snapshot.libraryTotal == 0 { break }
        }

        #expect(snapshots.first?.papers.map(\.paper.openAlexID) == ["W1"])
        #expect(snapshots.last == LibrarySnapshot(papers: [], counts: [.toRead: 0, .reading: 0, .read: 0], libraryTotal: 0))
        for snapshot in snapshots {
            #expect(snapshot.papers.count == snapshot.counts[.toRead])
            #expect(snapshot.libraryTotal >= snapshot.papers.count)
        }
    }

    @Test func observationsFollowChanges() async throws {
        var iterator = repository.observeLibrary(query: "", status: nil).makeAsyncIterator()
        #expect(await iterator.next()?.papers == [])

        try await repository.save(SamplePapers.vit)
        var latest = await iterator.next()
        while latest?.papers.isEmpty == true {
            latest = await iterator.next()
        }
        #expect(latest?.papers == [LibraryPaper(paper: SamplePapers.vit, status: .toRead)])
    }

    /// A paper saved by the Share Extension (another pool on the same file) appears after a refresh, as To read and searchable.
    @Test func refreshShowsPapersSavedThroughAnotherPool() async throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appending(path: "hashiya.sqlite")
        let app = GRDBLibraryRepository(store: try PaperStore.open(at: url))
        let shareExtension = GRDBLibraryRepository(store: try PaperStore.open(at: url))
        var library = app.observeLibrary(query: "vaswani", status: .toRead).makeAsyncIterator()
        #expect(await library.next()?.papers == [])

        try await shareExtension.save(SamplePapers.attention)
        await app.refreshAfterExternalChanges()

        #expect(await library.next()?.papers == [LibraryPaper(paper: SamplePapers.attention, status: .toRead)])
    }

    // MARK: Notes

    @Test func observePaperEmitsThePaperWithItsStatusThenNilAfterRemove() async throws {
        try await repository.save(SamplePapers.attention)
        try await repository.setStatus(openAlexID: SamplePapers.attention.openAlexID, status: .reading)

        #expect(await value(of: repository.observePaper(openAlexID: SamplePapers.attention.openAlexID))
            == .some(LibraryPaper(paper: SamplePapers.attention, status: .reading)))

        _ = try await repository.remove(openAlexID: SamplePapers.attention.openAlexID)
        #expect(await value(of: repository.observePaper(openAlexID: SamplePapers.attention.openAlexID)) == .some(nil))
    }

    @Test func notesAreEmptyWhenThereAreNoneOrThePaperIsNotSaved() async throws {
        try await repository.save(paper("W1"))
        #expect(try await repository.notes(openAlexID: "W1") == PaperNotes())
        #expect(try await repository.notes(openAlexID: "W404") == PaperNotes())
    }

    @Test func savedNotesReadBack() async throws {
        try await repository.save(paper("W1"))
        let notes = PaperNotes(summary: "Transformers", method: "Self-attention", thoughts: "أفكار")

        try await repository.saveNotes(openAlexID: "W1", notes: notes)

        #expect(try await repository.notes(openAlexID: "W1") == notes)
    }

    @Test func savingNotesForAnUnsavedPaperDoesNothing() async throws {
        try await repository.saveNotes(openAlexID: "W404", notes: PaperNotes(summary: "x"))
        #expect(try await repository.notes(openAlexID: "W404") == PaperNotes())
        #expect(await library()?.libraryTotal == 0)
    }

    @Test func theLibrarySearchFindsAWordOnlyInTheNotes() async throws {
        try await repository.save(paper("W1", title: "Deep nets"))
        try await repository.save(paper("W2", title: "Other"))
        try await repository.saveNotes(openAlexID: "W1", notes: PaperNotes(keyFindings: "Ablation shows التَّعلُّم helps"))

        #expect(await ids("ablation") == ["W1"])
        #expect(await ids("التعلم") == ["W1"])
        #expect(await ids("ABLAT deep") == ["W1"])
        #expect(await ids("ablation other") == [])
    }

    @Test func removeThenRestoreKeepsTheNotesAndTheirSearch() async throws {
        try await repository.save(paper("W1"))
        try await repository.setStatus(openAlexID: "W1", status: .read)
        let notes = PaperNotes(limitations: "Small sample")
        try await repository.saveNotes(openAlexID: "W1", notes: notes)

        let removed = try #require(try await repository.remove(openAlexID: "W1"))
        #expect(removed.notes == notes)
        #expect(removed.status == .read)
        #expect(await ids("sample") == [])

        try await repository.restore(removed)

        #expect(try await repository.notes(openAlexID: "W1") == notes)
        #expect(await ids("sample") == ["W1"])
        #expect(await library()?.papers.first?.status == .read)
    }

    @Test func restoringAPaperWithoutNotesAddsNoNotes() async throws {
        try await repository.save(paper("W1"))
        let removed = try #require(try await repository.remove(openAlexID: "W1"))
        #expect(removed.notes == PaperNotes())

        try await repository.restore(removed)

        #expect(try await repository.notes(openAlexID: "W1") == PaperNotes())
        let rows = try await queue.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM paper_notes") }
        #expect(rows == 0)
    }
}
