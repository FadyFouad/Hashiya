/// A page of `GET /works`. Tolerant: unknown keys are ignored and every field except `id`, `meta`
/// and `authorships[].author` may be missing or null.
public struct NetworkWorksResponse: Decodable, Equatable, Sendable {
    public let meta: NetworkMeta
    public let results: [NetworkWork]

    public init(meta: NetworkMeta, results: [NetworkWork]) {
        self.meta = meta
        self.results = results
    }

    enum CodingKeys: String, CodingKey { case meta, results }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        meta = try container.decode(NetworkMeta.self, forKey: .meta)
        results = try container.decodeIfPresent([NetworkWork].self, forKey: .results) ?? []
    }
}

public struct NetworkMeta: Decodable, Equatable, Sendable {
    public let count: Int64
    public let nextCursor: String?

    public init(count: Int64, nextCursor: String?) {
        self.count = count
        self.nextCursor = nextCursor
    }

    enum CodingKeys: String, CodingKey {
        case count
        case nextCursor = "next_cursor"
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        count = try container.decodeIfPresent(Int64.self, forKey: .count) ?? 0
        nextCursor = try container.decodeIfPresent(String.self, forKey: .nextCursor)
    }
}

public struct NetworkWork: Decodable, Equatable, Sendable {
    public let id: String
    public let doi: String?
    public let displayName: String?
    public let publicationYear: Int?
    public let primaryLocation: NetworkLocation?
    public let authorships: [NetworkAuthorship]
    public let citedByCount: Int
    public let openAccess: NetworkOpenAccess?
    public let bestOALocation: NetworkLocation?
    public let abstractInvertedIndex: [String: [Int]]?
    /// OpenAlex's work type, e.g. "article", "preprint", "book-chapter".
    public let type: String?
    public let biblio: NetworkBiblio?

    public init(
        id: String,
        doi: String? = nil,
        displayName: String? = nil,
        publicationYear: Int? = nil,
        primaryLocation: NetworkLocation? = nil,
        authorships: [NetworkAuthorship] = [],
        citedByCount: Int = 0,
        openAccess: NetworkOpenAccess? = nil,
        bestOALocation: NetworkLocation? = nil,
        abstractInvertedIndex: [String: [Int]]? = nil,
        type: String? = nil,
        biblio: NetworkBiblio? = nil
    ) {
        self.id = id
        self.doi = doi
        self.displayName = displayName
        self.publicationYear = publicationYear
        self.primaryLocation = primaryLocation
        self.authorships = authorships
        self.citedByCount = citedByCount
        self.openAccess = openAccess
        self.bestOALocation = bestOALocation
        self.abstractInvertedIndex = abstractInvertedIndex
        self.type = type
        self.biblio = biblio
    }

    enum CodingKeys: String, CodingKey {
        case id, doi, authorships, type, biblio
        case displayName = "display_name"
        case publicationYear = "publication_year"
        case primaryLocation = "primary_location"
        case citedByCount = "cited_by_count"
        case openAccess = "open_access"
        case bestOALocation = "best_oa_location"
        case abstractInvertedIndex = "abstract_inverted_index"
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        doi = try container.decodeIfPresent(String.self, forKey: .doi)
        displayName = try container.decodeIfPresent(String.self, forKey: .displayName)
        publicationYear = try container.decodeIfPresent(Int.self, forKey: .publicationYear)
        primaryLocation = try container.decodeIfPresent(NetworkLocation.self, forKey: .primaryLocation)
        authorships = try container.decodeIfPresent([NetworkAuthorship].self, forKey: .authorships) ?? []
        citedByCount = try container.decodeIfPresent(Int.self, forKey: .citedByCount) ?? 0
        openAccess = try container.decodeIfPresent(NetworkOpenAccess.self, forKey: .openAccess)
        bestOALocation = try container.decodeIfPresent(NetworkLocation.self, forKey: .bestOALocation)
        abstractInvertedIndex = try container.decodeIfPresent([String: [Int]].self, forKey: .abstractInvertedIndex)
        type = try container.decodeIfPresent(String.self, forKey: .type)
        biblio = try container.decodeIfPresent(NetworkBiblio.self, forKey: .biblio)
    }
}

/// A work's `biblio`: where it sits in its source. Every field may be missing or null.
public struct NetworkBiblio: Decodable, Equatable, Sendable {
    public let volume: String?
    public let issue: String?
    public let firstPage: String?
    public let lastPage: String?

    public init(volume: String? = nil, issue: String? = nil, firstPage: String? = nil, lastPage: String? = nil) {
        self.volume = volume
        self.issue = issue
        self.firstPage = firstPage
        self.lastPage = lastPage
    }

    enum CodingKeys: String, CodingKey {
        case volume, issue
        case firstPage = "first_page"
        case lastPage = "last_page"
    }
}

public struct NetworkLocation: Decodable, Equatable, Sendable {
    public let pdfURL: String?
    public let source: NetworkSource?
    /// Whether this copy is free to read. False when OpenAlex leaves it out.
    public let isOA: Bool

    public init(pdfURL: String? = nil, source: NetworkSource? = nil, isOA: Bool = false) {
        self.pdfURL = pdfURL
        self.source = source
        self.isOA = isOA
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        pdfURL = try container.decodeIfPresent(String.self, forKey: .pdfURL)
        source = try container.decodeIfPresent(NetworkSource.self, forKey: .source)
        isOA = try container.decodeIfPresent(Bool.self, forKey: .isOA) ?? false
    }

    enum CodingKeys: String, CodingKey {
        case source
        case pdfURL = "pdf_url"
        case isOA = "is_oa"
    }
}

/// A work with only its locations: every place OpenAlex knows it is hosted, open or not.
struct NetworkWorkLocations: Decodable, Sendable {
    let locations: [NetworkLocation]

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        locations = try container.decodeIfPresent([NetworkLocation].self, forKey: .locations) ?? []
    }

    enum CodingKeys: String, CodingKey {
        case locations
    }
}

public struct NetworkSource: Decodable, Equatable, Sendable {
    public let displayName: String?
    /// e.g. "journal", "conference", "repository".
    public let type: String?
    /// The publisher, e.g. "Springer Nature".
    public let hostOrganizationName: String?

    public init(displayName: String?, type: String? = nil, hostOrganizationName: String? = nil) {
        self.displayName = displayName
        self.type = type
        self.hostOrganizationName = hostOrganizationName
    }

    enum CodingKeys: String, CodingKey {
        case type
        case displayName = "display_name"
        case hostOrganizationName = "host_organization_name"
    }
}

public struct NetworkAuthorship: Decodable, Equatable, Sendable {
    public let author: NetworkAuthor

    public init(author: NetworkAuthor) {
        self.author = author
    }
}

public struct NetworkAuthor: Decodable, Equatable, Sendable {
    public let id: String?
    public let displayName: String?

    public init(id: String?, displayName: String?) {
        self.id = id
        self.displayName = displayName
    }

    enum CodingKeys: String, CodingKey {
        case id
        case displayName = "display_name"
    }
}

public struct NetworkOpenAccess: Decodable, Equatable, Sendable {
    public let isOA: Bool

    public init(isOA: Bool) {
        self.isOA = isOA
    }

    enum CodingKeys: String, CodingKey {
        case isOA = "is_oa"
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        isOA = try container.decodeIfPresent(Bool.self, forKey: .isOA) ?? false
    }
}
