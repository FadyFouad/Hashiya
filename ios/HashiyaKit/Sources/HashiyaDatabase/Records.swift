import GRDB
import HashiyaModel

/// A row of `papers`.
public struct PaperRecord: Codable, Equatable, Sendable, FetchableRecord, PersistableRecord {
    public static let databaseTableName = "papers"

    /// Local UUID, lowercase.
    public var id: String
    public var openAlexID: String?
    public var doi: String?
    public var title: String
    public var year: Int?
    public var venue: String?
    public var abstract: String?
    public var citationCount: Int
    public var isOpenAccess: Bool
    public var oaPDFURL: String?
    /// Epoch milliseconds; the library's sort key.
    public var savedAt: Int64
    /// `to_read`, `reading` or `read` (`HashiyaData` maps them to `ReadingStatus`).
    public var readingStatus: String
    /// OpenAlex's work type, e.g. "article"; this and the six below are nil when unknown.
    public var workType: String?
    /// OpenAlex's source type, e.g. "journal".
    public var sourceType: String?
    public var publisher: String?
    public var volume: String?
    public var issue: String?
    public var firstPage: String?
    public var lastPage: String?
    /// Assigned the first time the paper is exported or copied, then never changed. Unique when set.
    public var citeKey: String?
    /// True once the columns above come from an OpenAlex response that included them; rows from before `v4` start false.
    public var detailsFetched: Bool
    /// `downloaded` or `attached` while a PDF is stored, nil otherwise; the three below are set and cleared with it.
    public var pdfSource: String?
    public var pdfSize: Int64?
    /// Epoch milliseconds.
    public var pdfAddedAt: Int64?
    /// Zero-based page the reader last showed.
    public var pdfLastPage: Int?

    public init(
        id: String,
        openAlexID: String?,
        doi: String?,
        title: String,
        year: Int?,
        venue: String?,
        abstract: String?,
        citationCount: Int,
        isOpenAccess: Bool,
        oaPDFURL: String?,
        savedAt: Int64,
        readingStatus: String = "to_read",
        publication: PublicationDetails = PublicationDetails(),
        citeKey: String? = nil,
        detailsFetched: Bool = false,
        pdf: PaperPdf? = nil
    ) {
        self.id = id
        self.openAlexID = openAlexID
        self.doi = doi
        self.title = title
        self.year = year
        self.venue = venue
        self.abstract = abstract
        self.citationCount = citationCount
        self.isOpenAccess = isOpenAccess
        self.oaPDFURL = oaPDFURL
        self.savedAt = savedAt
        self.readingStatus = readingStatus
        workType = publication.workType
        sourceType = publication.sourceType
        publisher = publication.publisher
        volume = publication.volume
        issue = publication.issue
        firstPage = publication.firstPage
        lastPage = publication.lastPage
        self.citeKey = citeKey
        self.detailsFetched = detailsFetched
        pdfSource = pdf?.source.rawValue
        pdfSize = pdf?.sizeBytes
        pdfAddedAt = pdf?.addedAt
        pdfLastPage = pdf?.lastPage
    }

    public var publication: PublicationDetails {
        PublicationDetails(
            workType: workType,
            sourceType: sourceType,
            publisher: publisher,
            volume: volume,
            issue: issue,
            firstPage: firstPage,
            lastPage: lastPage
        )
    }

    /// The stored PDF, or nil. A source other than "downloaded" counts as attached, as on Android.
    public var pdf: PaperPdf? {
        pdfSource.map { source in
            PaperPdf(
                source: source == PdfSource.downloaded.rawValue ? .downloaded : .attached,
                sizeBytes: pdfSize ?? 0,
                addedAt: pdfAddedAt ?? 0,
                lastPage: pdfLastPage ?? 0
            )
        }
    }

    enum CodingKeys: String, CodingKey {
        case id, doi, title, year, venue, abstract, publisher, volume, issue
        case openAlexID = "open_alex_id"
        case citationCount = "citation_count"
        case isOpenAccess = "is_open_access"
        case oaPDFURL = "oa_pdf_url"
        case savedAt = "saved_at"
        case readingStatus = "reading_status"
        case workType = "work_type"
        case sourceType = "source_type"
        case firstPage = "first_page"
        case lastPage = "last_page"
        case citeKey = "cite_key"
        case detailsFetched = "details_fetched"
        case pdfSource = "pdf_source"
        case pdfSize = "pdf_size"
        case pdfAddedAt = "pdf_added_at"
        case pdfLastPage = "pdf_last_page"
    }
}

/// A row of `paper_authors`.
public struct PaperAuthorRecord: Codable, Equatable, Sendable, FetchableRecord, PersistableRecord {
    public static let databaseTableName = "paper_authors"

    public var paperID: String
    /// Authorship order, from 0.
    public var position: Int
    public var name: String
    public var openAlexAuthorID: String?

    public init(paperID: String, position: Int, name: String, openAlexAuthorID: String?) {
        self.paperID = paperID
        self.position = position
        self.name = name
        self.openAlexAuthorID = openAlexAuthorID
    }

    enum CodingKeys: String, CodingKey {
        case position, name
        case paperID = "paper_id"
        case openAlexAuthorID = "open_alex_author_id"
    }
}

/// A row of the full-text index `paper_search`: one per saved paper, keyed by the paper's local id (stored, not
/// indexed). FTS rows don't cascade, so `PaperStore` writes and deletes them with their paper.
public struct PaperSearchRow: Equatable, Sendable {
    public var paperID: String
    public var title: String
    /// Author names joined with spaces.
    public var authors: String
    public var abstract: String
    public var venue: String
    /// `notesText` of the paper's notes; "" when it has none.
    public var notes: String

    public init(paperID: String, title: String, authors: String, abstract: String, venue: String, notes: String = "") {
        self.paperID = paperID
        self.title = title
        self.authors = authors
        self.abstract = abstract
        self.venue = venue
        self.notes = notes
    }

    /// The row for a paper, every column passed through `searchableText`. New saves, Undo and migration `v2` use it.
    public static func make(
        paperID: String,
        title: String,
        authorNames: [String],
        abstract: String?,
        venue: String?,
        notes: PaperNotes? = nil
    ) -> PaperSearchRow {
        PaperSearchRow(
            paperID: paperID,
            title: searchableText(title),
            authors: searchableText(authorNames.joined(separator: " ")),
            abstract: searchableText(abstract ?? ""),
            venue: searchableText(venue ?? ""),
            notes: notes.map(notesText) ?? ""
        )
    }

    /// The six sections joined with spaces, through `searchableText`; "" for blank notes, which have no row.
    public static func notesText(_ notes: PaperNotes) -> String {
        guard !notes.isEmpty else { return "" }
        return searchableText(NoteSection.allCases.map { notes[$0] }.joined(separator: " "))
    }

    func insert(_ db: Database) throws {
        try db.execute(
            sql: "INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes) VALUES (?, ?, ?, ?, ?, ?)",
            arguments: [paperID, title, authors, abstract, venue, notes]
        )
    }
}

/// A row of `paper_notes`: a saved paper's notes. It exists only while some section isn't blank.
public struct PaperNotesRecord: Codable, Equatable, Sendable, FetchableRecord, PersistableRecord {
    public static let databaseTableName = "paper_notes"

    public var paperID: String
    public var summary: String
    public var researchQuestion: String
    public var method: String
    public var keyFindings: String
    public var limitations: String
    public var thoughts: String
    /// Epoch milliseconds of the last save. Nothing reads it yet.
    public var updatedAt: Int64

    public init(paperID: String, notes: PaperNotes, updatedAt: Int64) {
        self.paperID = paperID
        summary = notes.summary
        researchQuestion = notes.researchQuestion
        method = notes.method
        keyFindings = notes.keyFindings
        limitations = notes.limitations
        thoughts = notes.thoughts
        self.updatedAt = updatedAt
    }

    public var notes: PaperNotes {
        PaperNotes(
            summary: summary,
            researchQuestion: researchQuestion,
            method: method,
            keyFindings: keyFindings,
            limitations: limitations,
            thoughts: thoughts
        )
    }

    enum CodingKeys: String, CodingKey {
        case summary, method, limitations, thoughts
        case paperID = "paper_id"
        case researchQuestion = "research_question"
        case keyFindings = "key_findings"
        case updatedAt = "updated_at"
    }
}

/// A saved paper with its authors, sorted by position.
public struct PaperWithAuthors: Equatable, Sendable {
    public var paper: PaperRecord
    public var authors: [PaperAuthorRecord]

    public init(paper: PaperRecord, authors: [PaperAuthorRecord]) {
        self.paper = paper
        self.authors = authors
    }

    /// This paper's row in the search index, with no notes.
    public var searchRow: PaperSearchRow {
        searchRow(notes: nil)
    }

    /// This paper's row in the search index with `notes` in its notes column.
    public func searchRow(notes: PaperNotes?) -> PaperSearchRow {
        PaperSearchRow.make(
            paperID: paper.id,
            title: paper.title,
            authorNames: authors.sorted { $0.position < $1.position }.map(\.name),
            abstract: paper.abstract,
            venue: paper.venue,
            notes: notes
        )
    }
}

/// What `PaperStore.deleteByOpenAlexID` deleted: the paper with its authors (its cite key and `detailsFetched` ride in the
/// paper row), its notes if it had any, and its collection links, ordered by collection id.
public struct DeletedPaper: Equatable, Sendable {
    public var saved: PaperWithAuthors
    public var notes: PaperNotesRecord?
    public var collectionLinks: [CollectionPaperRecord]

    public init(saved: PaperWithAuthors, notes: PaperNotesRecord?, collectionLinks: [CollectionPaperRecord] = []) {
        self.saved = saved
        self.notes = notes
        self.collectionLinks = collectionLinks
    }
}

/// One consistent read of the library for a search, a status and a collection.
public struct LibraryRows: Equatable, Sendable {
    /// Papers matching the search, the status and the collection, newest saved first.
    public var papers: [PaperWithAuthors]
    /// Papers matching the search in the collection, per stored status; statuses with none are absent.
    public var statusCounts: [String: Int]
    /// Every paper in the current view (the collection, or the whole library), ignoring the search and the status.
    public var total: Int
    /// Every saved paper.
    public var allTotal: Int

    /// `allTotal` nil means the same as `total`, i.e. the view is the whole library.
    public init(papers: [PaperWithAuthors], statusCounts: [String: Int], total: Int, allTotal: Int? = nil) {
        self.papers = papers
        self.statusCounts = statusCounts
        self.total = total
        self.allTotal = allTotal ?? total
    }
}

/// A row of `collections`.
public struct CollectionRecord: Codable, Equatable, Sendable, FetchableRecord, MutablePersistableRecord {
    public static let databaseTableName = "collections"

    /// Nil until inserted.
    public var id: Int64?
    public var name: String
    /// The name trimmed and lowercased; unique, so no two collections share a name in any case.
    public var nameKey: String
    /// Epoch milliseconds.
    public var createdAt: Int64

    public init(id: Int64? = nil, name: String, nameKey: String, createdAt: Int64) {
        self.id = id
        self.name = name
        self.nameKey = nameKey
        self.createdAt = createdAt
    }

    public mutating func didInsert(_ inserted: InsertionSuccess) {
        id = inserted.rowID
    }

    enum CodingKeys: String, CodingKey {
        case id, name
        case nameKey = "name_key"
        case createdAt = "created_at"
    }
}

/// A row of `collection_papers`: a saved paper's membership in a collection. Deleting either side deletes the link,
/// never the other side.
public struct CollectionPaperRecord: Codable, Equatable, Sendable, FetchableRecord, PersistableRecord {
    public static let databaseTableName = "collection_papers"

    public var collectionID: Int64
    /// The paper's local id.
    public var paperID: String
    /// Epoch milliseconds.
    public var addedAt: Int64

    public init(collectionID: Int64, paperID: String, addedAt: Int64) {
        self.collectionID = collectionID
        self.paperID = paperID
        self.addedAt = addedAt
    }

    enum CodingKeys: String, CodingKey {
        case collectionID = "collection_id"
        case paperID = "paper_id"
        case addedAt = "added_at"
    }
}

/// A collection and how many saved papers it holds.
public struct CollectionWithCount: Codable, Equatable, Sendable, FetchableRecord {
    public var id: Int64
    public var name: String
    public var paperCount: Int

    public init(id: Int64, name: String, paperCount: Int) {
        self.id = id
        self.name = name
        self.paperCount = paperCount
    }

    enum CodingKeys: String, CodingKey {
        case id, name
        case paperCount = "paper_count"
    }
}

/// A saved paper's stored PDF, read by `PaperStore.observePdf`.
public struct PdfColumns: Equatable, Sendable, FetchableRecord, Decodable {
    /// `downloaded`, or anything else for attached.
    public var source: String
    public var size: Int64
    public var addedAt: Int64
    public var lastPage: Int

    public init(source: String, size: Int64, addedAt: Int64, lastPage: Int) {
        self.source = source
        self.size = size
        self.addedAt = addedAt
        self.lastPage = lastPage
    }

    public var pdf: PaperPdf {
        PaperPdf(
            source: source == PdfSource.downloaded.rawValue ? .downloaded : .attached,
            sizeBytes: size,
            addedAt: addedAt,
            lastPage: lastPage
        )
    }

    enum CodingKeys: String, CodingKey {
        case source, size
        case addedAt = "added_at"
        case lastPage = "last_page"
    }
}
