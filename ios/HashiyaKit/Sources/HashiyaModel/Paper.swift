/// A scholarly work, as shown in search results and stored in the library.
public struct Paper: Equatable, Hashable, Sendable, Identifiable {
    /// The short OpenAlex ID, e.g. "W2741809807", without the URL prefix.
    public var openAlexID: String
    /// Normalized by `normalizeDOI`: lowercase, no prefix.
    public var doi: String?
    /// "" when OpenAlex has no title; the UI shows "Untitled".
    public var title: String
    /// In authorship order.
    public var authors: [Author]
    public var year: Int?
    public var venue: String?
    public var abstract: String?
    public var citationCount: Int
    public var isOpenAccess: Bool
    public var openAccessPDFURL: String?

    public var id: String { openAlexID }

    public init(
        openAlexID: String,
        doi: String? = nil,
        title: String,
        authors: [Author] = [],
        year: Int? = nil,
        venue: String? = nil,
        abstract: String? = nil,
        citationCount: Int = 0,
        isOpenAccess: Bool = false,
        openAccessPDFURL: String? = nil
    ) {
        self.openAlexID = openAlexID
        self.doi = doi
        self.title = title
        self.authors = authors
        self.year = year
        self.venue = venue
        self.abstract = abstract
        self.citationCount = citationCount
        self.isOpenAccess = isOpenAccess
        self.openAccessPDFURL = openAccessPDFURL
    }
}

public struct Author: Equatable, Hashable, Sendable {
    public var name: String
    /// The short OpenAlex author ID, e.g. "A5103024730", when known.
    public var openAlexID: String?

    public init(name: String, openAlexID: String? = nil) {
        self.name = name
        self.openAlexID = openAlexID
    }
}
