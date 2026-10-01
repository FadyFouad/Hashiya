import GRDB
import HashiyaDatabase
import HashiyaModel
import Testing

/// Mirrors Android's PDF cases in `PaperDaoTest`.
struct PdfStoreTests {
    private let queue: DatabaseQueue
    private let store: PaperStore

    init() throws {
        queue = try HashiyaDatabase.openInMemory()
        store = PaperStore(writer: queue)
    }

    private func savePaper(_ id: String, _ openAlexID: String, pdf: PaperPdf? = nil) async throws {
        let paper = PaperRecord(
            id: id, openAlexID: openAlexID, doi: nil, title: "Title \(id)", year: 2020, venue: nil, abstract: nil,
            citationCount: 0, isOpenAccess: true, oaPDFURL: "https://arxiv.org/pdf/\(openAlexID)", savedAt: 1, pdf: pdf
        )
        let saved = PaperWithAuthors(paper: paper, authors: [])
        try await store.insert(paper: saved.paper, authors: saved.authors, search: saved.searchRow)
    }

    /// The first value of `stream`.
    private func first<T: Sendable>(_ stream: AsyncStream<T>) async -> T? {
        for await value in stream {
            return value
        }
        return nil
    }

    /// The first value of `stream` after `values` that satisfies `predicate`, within 5 s.
    private func value<T: Sendable>(_ stream: AsyncStream<T>, where predicate: @escaping @Sendable (T) -> Bool) async -> T? {
        await withTaskGroup(of: T?.self) { group in
            group.addTask {
                for await value in stream where predicate(value) {
                    return value
                }
                return nil
            }
            group.addTask {
                try? await Task.sleep(for: .seconds(5))
                return nil
            }
            let result = await group.next() ?? nil
            group.cancelAll()
            return result
        }
    }

    @Test func aPaperHasNoPdfUntilOneIsSet() async throws {
        try await savePaper("p1", "W1")

        #expect(await first(store.observePdf(openAlexID: "W1")) == .some(nil))
        #expect(try await store.paperID(openAlexID: "W1") == "p1")
        #expect(try await store.paperID(openAlexID: "W404") == nil)
    }

    @Test func setPdfStoresTheColumnsAndStartsOnTheFirstPage() async throws {
        try await savePaper("p1", "W1")
        try await store.setPdf(paperID: "p1", source: "downloaded", size: 2_048, addedAt: 10)

        let stored = await first(store.observePdf(openAlexID: "W1")) ?? nil
        #expect(stored == PdfColumns(source: "downloaded", size: 2_048, addedAt: 10, lastPage: 0))
        #expect(stored?.pdf == PaperPdf(source: .downloaded, sizeBytes: 2_048, addedAt: 10, lastPage: 0))
    }

    @Test func replacingAPdfResetsTheLastPage() async throws {
        try await savePaper("p1", "W1")
        try await store.setPdf(paperID: "p1", source: "downloaded", size: 2_048, addedAt: 10)
        try await store.setPdfLastPage(paperID: "p1", page: 12)

        try await store.setPdf(paperID: "p1", source: "attached", size: 4_096, addedAt: 20)

        let stored = await first(store.observePdf(openAlexID: "W1")) ?? nil
        #expect(stored?.pdf == PaperPdf(source: .attached, sizeBytes: 4_096, addedAt: 20, lastPage: 0))
    }

    @Test func theLastPageIsKeptOnlyWhileAPdfIsSet() async throws {
        try await savePaper("p1", "W1")
        try await savePaper("p2", "W2")
        try await store.setPdf(paperID: "p1", source: "attached", size: 1, addedAt: 1)

        try await store.setPdfLastPage(paperID: "p1", page: 7)
        try await store.setPdfLastPage(paperID: "p2", page: 3)

        #expect((await first(store.observePdf(openAlexID: "W1")) ?? nil)?.lastPage == 7)
        let p2Page = try await queue.read { db in try Int?.fetchOne(db, sql: "SELECT pdf_last_page FROM papers WHERE id = 'p2'") }
        #expect(p2Page == .some(nil))
    }

    @Test func setPdfReportsWhetherARowTookIt() async throws {
        try await savePaper("p1", "W1")

        // The download and attach paths delete their file when no row took it.
        #expect(try await store.setPdf(paperID: "missing", source: "downloaded", size: 10, addedAt: 5) == false)
        #expect(try await store.setPdf(paperID: "p1", source: "downloaded", size: 10, addedAt: 5) == true)
    }

    @Test func clearPdfClearsEveryColumn() async throws {
        try await savePaper("p1", "W1")
        try await store.setPdf(paperID: "p1", source: "downloaded", size: 9, addedAt: 1)
        try await store.setPdfLastPage(paperID: "p1", page: 2)

        try await store.clearPdf(paperID: "p1")

        #expect(await first(store.observePdf(openAlexID: "W1")) == .some(nil))
        let cleared = try await queue.read { db in
            try Int.fetchOne(
                db,
                sql: """
                    SELECT COUNT(*) FROM papers
                    WHERE pdf_source IS NULL AND pdf_size IS NULL AND pdf_added_at IS NULL AND pdf_last_page IS NULL
                    """
            )
        }
        #expect(cleared == 1)
    }

    @Test func anOpenObservationSeesThePdfArriveAndGo() async throws {
        try await savePaper("p1", "W1")
        let stream = store.observePdf(openAlexID: "W1")
        #expect(await first(stream) == .some(nil))

        let arrived = Task { await value(stream) { $0 != nil } }
        try await store.setPdf(paperID: "p1", source: "attached", size: 5, addedAt: 3)
        #expect((await arrived.value ?? nil)?.size == 5)

        let gone = Task { await value(stream) { $0 == nil } }
        try await store.clearPdf(paperID: "p1")
        #expect(await gone.value == .some(nil))
    }

    @Test func idsAndStorageAreSplitBySource() async throws {
        try await savePaper("p1", "W1")
        try await savePaper("p2", "W2")
        try await savePaper("p3", "W3")
        try await savePaper("p4", "W4")
        try await store.setPdf(paperID: "p1", source: "downloaded", size: 100, addedAt: 1)
        try await store.setPdf(paperID: "p2", source: "downloaded", size: 250, addedAt: 2)
        try await store.setPdf(paperID: "p3", source: "attached", size: 40, addedAt: 3)

        #expect(try await store.pdfPaperIDs() == ["p1", "p2", "p3"])
        #expect(Set(try await store.downloadedPdfPaperIDs()) == ["p1", "p2"])
        #expect(try await store.pdfStorage() == PdfStorage(downloadedBytes: 350, downloadedCount: 2, attachedBytes: 40, attachedCount: 1))
    }

    @Test func storageIsEmptyWithoutPdfs() async throws {
        try await savePaper("p1", "W1")
        #expect(try await store.pdfStorage() == .empty)
        #expect(try await store.pdfPaperIDs().isEmpty)
    }

    @Test func anUnknownSourceCountsAsAttached() async throws {
        try await savePaper("p1", "W1")
        try await store.setPdf(paperID: "p1", source: "shared", size: 8, addedAt: 1)

        #expect(try await store.pdfStorage() == PdfStorage(downloadedBytes: 0, downloadedCount: 0, attachedBytes: 8, attachedCount: 1))
        #expect((await first(store.observePdf(openAlexID: "W1")) ?? nil)?.pdf.source == .attached)
    }

    /// Undo: `deleteByOpenAlexID` returns the PDF columns, and `insert` writes them back.
    @Test func deleteAndRestoreKeepThePdfColumns() async throws {
        try await savePaper("p1", "W1")
        try await store.setPdf(paperID: "p1", source: "downloaded", size: 2_048, addedAt: 10)
        try await store.setPdfLastPage(paperID: "p1", page: 4)

        let deleted = try #require(try await store.deleteByOpenAlexID("W1"))
        #expect(deleted.saved.paper.pdf == PaperPdf(source: .downloaded, sizeBytes: 2_048, addedAt: 10, lastPage: 4))
        #expect(try await store.pdfPaperIDs().isEmpty)

        try await store.insert(paper: deleted.saved.paper, authors: deleted.saved.authors, search: deleted.saved.searchRow)
        #expect((await first(store.observePdf(openAlexID: "W1")) ?? nil)?.pdf == PaperPdf(
            source: .downloaded, sizeBytes: 2_048, addedAt: 10, lastPage: 4
        ))
    }

    @Test func theLibraryRowsCarryThePdfColumns() async throws {
        try await savePaper("p1", "W1")
        try await savePaper("p2", "W2", pdf: PaperPdf(source: .attached, sizeBytes: 3, addedAt: 1))

        let rows = await first(store.observeLibrary(match: nil, status: nil))
        let sources = Dictionary(uniqueKeysWithValues: rows?.papers.map { ($0.paper.id, $0.paper.pdfSource) } ?? [])
        #expect(sources["p1"] == .some(nil))
        #expect(sources["p2"] == "attached")
    }
}
