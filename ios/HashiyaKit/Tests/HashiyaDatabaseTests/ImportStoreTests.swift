import GRDB
import HashiyaDatabase
import HashiyaModel
import Testing

/// Mirrors Android's `ImportJobDaoTest`.
struct ImportStoreTests {
    private let queue: DatabaseQueue
    private let store: PaperStore

    init() throws {
        queue = try HashiyaDatabase.openInMemory()
        store = PaperStore(writer: queue)
    }

    private func paper(_ id: String, citeKey: String? = nil, doi: String? = nil) -> PaperWithAuthors {
        PaperWithAuthors(
            paper: PaperRecord(
                id: id, openAlexID: "W\(id)", doi: doi, title: "Title \(id)", year: 2020, venue: nil, abstract: nil,
                citationCount: 0, isOpenAccess: false, oaPDFURL: nil, savedAt: 1, citeKey: citeKey
            ),
            authors: [PaperAuthorRecord(paperID: id, position: 0, name: "Author \(id)", openAlexAuthorID: nil)]
        )
    }

    private func newJob(state: String = "importing") async throws -> Int64 {
        try await store.createImportJob(ImportJobRecord(fileName: "thesis.bib", createdAt: 1, state: state, collectionName: "thesis"))
    }

    private func strings(_ sql: String) throws -> [String] {
        try queue.read { db in try String.fetchAll(db, sql: sql) }
    }

    @Test func aBatchSavesPapersAuthorsSearchRowsJobLinksAndProgressTogether() async throws {
        let job = try await newJob()
        let saved = try await store.insertImportBatch(
            jobID: job, papers: [paper("a"), paper("b")], collectionID: nil, addedAt: 5, processed: 2
        )
        #expect(saved == 2)
        #expect(try strings("SELECT paper_id FROM import_job_papers ORDER BY paper_id") == ["a", "b"])
        #expect(try strings("SELECT paper_id FROM paper_search ORDER BY paper_id") == ["a", "b"])
        #expect(try strings("SELECT name FROM paper_authors ORDER BY name") == ["Author a", "Author b"])
        let record = try #require(try await store.importJob(id: job))
        #expect(record.processed == 2)
        #expect(record.imported == 2)
    }

    @Test func aPaperAlreadySavedIsSkippedAndATakenCiteKeyDropped() async throws {
        let job = try await newJob()
        let first = paper("a", citeKey: "smith2020")
        try await store.insert(paper: first.paper, authors: first.authors, search: first.searchRow)
        let saved = try await store.insertImportBatch(
            jobID: job, papers: [paper("a"), paper("b", citeKey: "smith2020")], collectionID: nil, addedAt: 5, processed: 2
        )
        #expect(saved == 1)
        #expect(try strings("SELECT id || ':' || COALESCE(cite_key, '-') FROM papers ORDER BY id") == ["a:smith2020", "b:-"])
        #expect(try await store.importJob(id: job)?.imported == 1)
    }

    @Test func everyPaperTheJobSavedGoesIntoTheCollectionAndADeletedOneIsSkipped() async throws {
        let job = try await newJob()
        let collection = try #require(try await store.insertCollection(name: "Thesis", nameKey: "thesis", createdAt: 1))
        try await store.insertImportBatch(jobID: job, papers: [paper("a")], collectionID: nil, addedAt: 5, processed: 1)
        try await store.insertImportBatch(jobID: job, papers: [paper("b")], collectionID: collection, addedAt: 6, processed: 2)
        #expect(try strings("SELECT paper_id FROM collection_papers ORDER BY paper_id") == ["a", "b"])

        try await store.deleteCollection(id: collection)
        try await store.insertImportBatch(jobID: job, papers: [paper("c")], collectionID: collection, addedAt: 7, processed: 3)
        #expect(try strings("SELECT paper_id FROM collection_papers").isEmpty)
        #expect(try await store.importJob(id: job)?.imported == 3)
    }

    @Test func libraryKeysMarkThePapersThisJobSaved() async throws {
        let job = try await newJob()
        let other = try await newJob()
        let saved = paper("a", citeKey: "k", doi: "10.1/a")
        try await store.insert(paper: saved.paper, authors: saved.authors, search: saved.searchRow)
        try await store.insertImportBatch(jobID: job, papers: [paper("b")], collectionID: nil, addedAt: 5, processed: 1)
        let keys = try await store.libraryKeys(jobID: job).sorted { ($0.openAlexID ?? "") < ($1.openAlexID ?? "") }
        #expect(keys == [
            LibraryKeyRow(openAlexID: "Wa", doi: "10.1/a", title: "Title a", year: 2020, citeKey: "k", importedByJob: false),
            LibraryKeyRow(openAlexID: "Wb", doi: nil, title: "Title b", year: 2020, citeKey: nil, importedByJob: true),
        ])
        #expect(try await store.libraryKeys(jobID: other).allSatisfy { !$0.importedByJob })
    }

    @Test func replacingOnlyHappensWhileTheJobIsInTheExpectedState() async throws {
        let id = try await newJob(state: "analyzing")
        var record = try #require(try await store.importJob(id: id))
        record.state = "ready"
        #expect(try await store.replaceImportJob(ifState: "analyzing", with: record))
        record.state = "done"
        #expect(try await store.replaceImportJob(ifState: "analyzing", with: record) == false)
        #expect(try await store.importJob(id: id)?.state == "ready")
    }

    @Test func theRunningJobIsTheNewestAnalyzingOrImportingOne() async throws {
        _ = try await newJob(state: "done")
        let running = try await newJob(state: "importing")
        _ = try await newJob(state: "ready")
        #expect(try await store.runningImportJob()?.id == running)
    }

    @Test func deletingAJobKeepsItsPapers() async throws {
        let job = try await newJob()
        try await store.insertImportBatch(jobID: job, papers: [paper("a")], collectionID: nil, addedAt: 5, processed: 1)
        try await store.deleteImportJob(id: job)
        #expect(try strings("SELECT id FROM papers") == ["a"])
        #expect(try strings("SELECT paper_id FROM import_job_papers").isEmpty)
        #expect(try await store.importJob(id: job) == nil)
    }

    @Test func theLatestJobIsObserved() async throws {
        let id = try await newJob()
        var iterator = store.observeLatestImportJob().makeAsyncIterator()
        #expect(await iterator.next()??.id == id)
    }
}
