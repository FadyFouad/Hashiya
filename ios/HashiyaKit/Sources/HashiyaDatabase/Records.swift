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
        readingStatus: String = "to_read"
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
    }

    enum CodingKeys: String, CodingKey {
        case id, doi, title, year, venue, abstract
        case openAlexID = "open_alex_id"
        case citationCount = "citation_count"
        case isOpenAccess = "is_open_access"
        case oaPDFURL = "oa_pdf_url"
        case savedAt = "saved_at"
        case readingStatus = "reading_status"
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

/// What `PaperStore.deleteByOpenAlexID` deleted: the paper with its authors, and its notes if it had any.
public struct DeletedPaper: Equatable, Sendable {
    public var saved: PaperWithAuthors
    public var notes: PaperNotesRecord?

    public init(saved: PaperWithAuthors, notes: PaperNotesRecord?) {
        self.saved = saved
        self.notes = notes
    }
}

/// One consistent read of the library for a search and a status.
public struct LibraryRows: Equatable, Sendable {
    /// Papers matching the search and the status, newest saved first.
    public var papers: [PaperWithAuthors]
    /// Papers matching the search per stored status; statuses with none are absent.
    public var statusCounts: [String: Int]
    /// Every saved paper, ignoring the search and the status.
    public var total: Int

    public init(papers: [PaperWithAuthors], statusCounts: [String: Int], total: Int) {
        self.papers = papers
        self.statusCounts = statusCounts
        self.total = total
    }
}
