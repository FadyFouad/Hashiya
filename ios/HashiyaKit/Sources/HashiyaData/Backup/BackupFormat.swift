import Foundation

/// The `.hashiya` archive, format 1 (docs/superpowers/specs/2026-10-04-backup-and-restore-design.md §3), shared with Android.
/// These types are the file format: they are kept apart from the GRDB records so a schema change never silently changes
/// what a backup holds. Missing fields decode to their defaults and unknown ones are ignored.
enum BackupFormat {
    static let version = 1
    static let manifestEntry = "manifest.json"
    static let libraryEntry = "library.json"
    static let maxLibraryBytes = 50 * 1024 * 1024
    static let maxManifestBytes = 64 * 1024

    /// The only entry a paper's PDF may be read from; any other `pdf.file` counts as missing.
    static func pdfEntry(ref: Int) -> String {
        "pdfs/\(ref).pdf"
    }

    static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return encoder
    }()

    static let decoder = JSONDecoder()

    /// `2026-10-04T14:05:00Z`.
    static func isoUTC(_ ms: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(ms) / 1000)
        return utcFormatter("yyyy-MM-dd'T'HH:mm:ss'Z'").string(from: date)
    }

    /// Milliseconds for an `isoUTC` string; nil when it doesn't parse.
    static func parseISOUTC(_ text: String) -> Int64? {
        guard let date = utcFormatter("yyyy-MM-dd'T'HH:mm:ss'Z'").date(from: text) else {
            return nil
        }
        return Int64((date.timeIntervalSince1970 * 1000).rounded())
    }

    /// `Hashiya-library-2026-10-04.hashiya`, dated in UTC like the manifest.
    static func fileName(at ms: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(ms) / 1000)
        return "Hashiya-library-\(utcFormatter("yyyy-MM-dd").string(from: date)).hashiya"
    }

    private static func utcFormatter(_ format: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = format
        formatter.isLenient = false
        return formatter
    }
}

struct BackupManifest: Codable, Equatable, Sendable {
    var format: Int
    var app = ""
    /// ISO 8601 in UTC, e.g. `2026-10-04T14:05:00Z`.
    var exportedAt = ""
    var papers = 0
    var collections = 0
    var includesPdfs = false

    init(
        format: Int,
        app: String = "",
        exportedAt: String = "",
        papers: Int = 0,
        collections: Int = 0,
        includesPdfs: Bool = false
    ) {
        self.format = format
        self.app = app
        self.exportedAt = exportedAt
        self.papers = papers
        self.collections = collections
        self.includesPdfs = includesPdfs
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        format = try c.decode(Int.self, forKey: .format)
        app = try c.decodeIfPresent(String.self, forKey: .app) ?? ""
        exportedAt = try c.decodeIfPresent(String.self, forKey: .exportedAt) ?? ""
        papers = try c.decodeIfPresent(Int.self, forKey: .papers) ?? 0
        collections = try c.decodeIfPresent(Int.self, forKey: .collections) ?? 0
        includesPdfs = try c.decodeIfPresent(Bool.self, forKey: .includesPdfs) ?? false
    }
}

struct BackupLibrary: Codable, Equatable, Sendable {
    var papers: [BackupPaper] = []
    var collections: [BackupCollection] = []

    init(papers: [BackupPaper] = [], collections: [BackupCollection] = []) {
        self.papers = papers
        self.collections = collections
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        papers = try c.decodeIfPresent([BackupPaper].self, forKey: .papers) ?? []
        collections = try c.decodeIfPresent([BackupCollection].self, forKey: .collections) ?? []
    }
}

struct BackupPaper: Codable, Equatable, Sendable {
    /// Unique within the file; collections and the PDF entry refer to it.
    var ref: Int
    var openAlexId: String?
    var doi: String?
    var title: String
    var year: Int?
    var venue: String?
    var abstract: String?
    var citationCount = 0
    var isOpenAccess = false
    var oaPdfUrl: String?
    var savedAt: Int64
    /// `to_read`, `reading` or `read`; anything else restores as `to_read`.
    var readingStatus = "to_read"
    var workType: String?
    var sourceType: String?
    var publisher: String?
    var volume: String?
    var issue: String?
    var firstPage: String?
    var lastPage: String?
    var citeKey: String?
    var detailsFetched = false
    /// In position order.
    var authors: [BackupAuthor] = []
    var notes: BackupNotes?
    var pdf: BackupPdf?

    init(
        ref: Int,
        openAlexId: String? = nil,
        doi: String? = nil,
        title: String,
        year: Int? = nil,
        venue: String? = nil,
        abstract: String? = nil,
        citationCount: Int = 0,
        isOpenAccess: Bool = false,
        oaPdfUrl: String? = nil,
        savedAt: Int64,
        readingStatus: String = "to_read",
        workType: String? = nil,
        sourceType: String? = nil,
        publisher: String? = nil,
        volume: String? = nil,
        issue: String? = nil,
        firstPage: String? = nil,
        lastPage: String? = nil,
        citeKey: String? = nil,
        detailsFetched: Bool = false,
        authors: [BackupAuthor] = [],
        notes: BackupNotes? = nil,
        pdf: BackupPdf? = nil
    ) {
        self.ref = ref
        self.openAlexId = openAlexId
        self.doi = doi
        self.title = title
        self.year = year
        self.venue = venue
        self.abstract = abstract
        self.citationCount = citationCount
        self.isOpenAccess = isOpenAccess
        self.oaPdfUrl = oaPdfUrl
        self.savedAt = savedAt
        self.readingStatus = readingStatus
        self.workType = workType
        self.sourceType = sourceType
        self.publisher = publisher
        self.volume = volume
        self.issue = issue
        self.firstPage = firstPage
        self.lastPage = lastPage
        self.citeKey = citeKey
        self.detailsFetched = detailsFetched
        self.authors = authors
        self.notes = notes
        self.pdf = pdf
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ref = try c.decode(Int.self, forKey: .ref)
        openAlexId = try c.decodeIfPresent(String.self, forKey: .openAlexId)
        doi = try c.decodeIfPresent(String.self, forKey: .doi)
        title = try c.decode(String.self, forKey: .title)
        year = try c.decodeIfPresent(Int.self, forKey: .year)
        venue = try c.decodeIfPresent(String.self, forKey: .venue)
        abstract = try c.decodeIfPresent(String.self, forKey: .abstract)
        citationCount = try c.decodeIfPresent(Int.self, forKey: .citationCount) ?? 0
        isOpenAccess = try c.decodeIfPresent(Bool.self, forKey: .isOpenAccess) ?? false
        oaPdfUrl = try c.decodeIfPresent(String.self, forKey: .oaPdfUrl)
        savedAt = try c.decode(Int64.self, forKey: .savedAt)
        readingStatus = try c.decodeIfPresent(String.self, forKey: .readingStatus) ?? "to_read"
        workType = try c.decodeIfPresent(String.self, forKey: .workType)
        sourceType = try c.decodeIfPresent(String.self, forKey: .sourceType)
        publisher = try c.decodeIfPresent(String.self, forKey: .publisher)
        volume = try c.decodeIfPresent(String.self, forKey: .volume)
        issue = try c.decodeIfPresent(String.self, forKey: .issue)
        firstPage = try c.decodeIfPresent(String.self, forKey: .firstPage)
        lastPage = try c.decodeIfPresent(String.self, forKey: .lastPage)
        citeKey = try c.decodeIfPresent(String.self, forKey: .citeKey)
        detailsFetched = try c.decodeIfPresent(Bool.self, forKey: .detailsFetched) ?? false
        authors = try c.decodeIfPresent([BackupAuthor].self, forKey: .authors) ?? []
        notes = try c.decodeIfPresent(BackupNotes.self, forKey: .notes)
        pdf = try c.decodeIfPresent(BackupPdf.self, forKey: .pdf)
    }
}

struct BackupAuthor: Codable, Equatable, Sendable {
    var name: String
    var openAlexAuthorId: String?
}

struct BackupNotes: Codable, Equatable, Sendable {
    var summary = ""
    var researchQuestion = ""
    var method = ""
    var keyFindings = ""
    var limitations = ""
    var thoughts = ""
    var updatedAt: Int64 = 0

    init(
        summary: String = "",
        researchQuestion: String = "",
        method: String = "",
        keyFindings: String = "",
        limitations: String = "",
        thoughts: String = "",
        updatedAt: Int64 = 0
    ) {
        self.summary = summary
        self.researchQuestion = researchQuestion
        self.method = method
        self.keyFindings = keyFindings
        self.limitations = limitations
        self.thoughts = thoughts
        self.updatedAt = updatedAt
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        summary = try c.decodeIfPresent(String.self, forKey: .summary) ?? ""
        researchQuestion = try c.decodeIfPresent(String.self, forKey: .researchQuestion) ?? ""
        method = try c.decodeIfPresent(String.self, forKey: .method) ?? ""
        keyFindings = try c.decodeIfPresent(String.self, forKey: .keyFindings) ?? ""
        limitations = try c.decodeIfPresent(String.self, forKey: .limitations) ?? ""
        thoughts = try c.decodeIfPresent(String.self, forKey: .thoughts) ?? ""
        updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
    }
}

/// A stored PDF. `file` is nil when the PDF isn't in the archive (PDFs left out, or missing at export).
struct BackupPdf: Codable, Equatable, Sendable {
    var source: String
    var addedAt: Int64
    var lastPage = 0
    var file: String?

    init(source: String, addedAt: Int64, lastPage: Int = 0, file: String? = nil) {
        self.source = source
        self.addedAt = addedAt
        self.lastPage = lastPage
        self.file = file
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        source = try c.decode(String.self, forKey: .source)
        addedAt = try c.decode(Int64.self, forKey: .addedAt)
        lastPage = try c.decodeIfPresent(Int.self, forKey: .lastPage) ?? 0
        file = try c.decodeIfPresent(String.self, forKey: .file)
    }
}

struct BackupCollection: Codable, Equatable, Sendable {
    var name: String
    var createdAt: Int64
    var papers: [Int] = []

    init(name: String, createdAt: Int64, papers: [Int] = []) {
        self.name = name
        self.createdAt = createdAt
        self.papers = papers
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = try c.decode(String.self, forKey: .name)
        createdAt = try c.decode(Int64.self, forKey: .createdAt)
        papers = try c.decodeIfPresent([Int].self, forKey: .papers) ?? []
    }
}
