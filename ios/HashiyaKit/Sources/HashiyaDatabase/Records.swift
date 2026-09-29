import GRDB

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
        savedAt: Int64
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
    }

    enum CodingKeys: String, CodingKey {
        case id, doi, title, year, venue, abstract
        case openAlexID = "open_alex_id"
        case citationCount = "citation_count"
        case isOpenAccess = "is_open_access"
        case oaPDFURL = "oa_pdf_url"
        case savedAt = "saved_at"
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

/// A saved paper with its authors, sorted by position.
public struct PaperWithAuthors: Equatable, Sendable {
    public var paper: PaperRecord
    public var authors: [PaperAuthorRecord]

    public init(paper: PaperRecord, authors: [PaperAuthorRecord]) {
        self.paper = paper
        self.authors = authors
    }
}
