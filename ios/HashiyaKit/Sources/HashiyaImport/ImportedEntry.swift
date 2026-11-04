public enum ImportFormat: String, Equatable, Sendable {
    case bibTeX = "bibtex"
    case ris
}

/// One reference read from an import file, with the fields Hashiya uses. Text is decoded (no LaTeX), whitespace is
/// collapsed and empty values are nil. `workType` and `sourceType` use OpenAlex's vocabulary. Android's `ImportedEntry`.
public struct ImportedEntry: Equatable, Sendable {
    /// The 1-based line where the entry starts: its `@` in BibTeX, its `TY` in RIS.
    public var line: Int
    /// The BibTeX type lowercased ("article"), or the RIS `TY` code ("JOUR").
    public var entryType: String
    /// The BibTeX key as written; always nil for RIS.
    public var citeKey: String?
    /// "" when the entry has none.
    public var title: String
    /// Display names ("Given Family"), in the file's order.
    public var authors: [String]
    public var year: Int?
    public var venue: String?
    /// In `normalizeDOI` form: lowercase, no prefix.
    public var doi: String?
    public var url: String?
    public var abstract: String?
    public var volume: String?
    public var issue: String?
    public var firstPage: String?
    public var lastPage: String?
    public var publisher: String?
    public var workType: String?
    public var sourceType: String?

    public init(
        line: Int,
        entryType: String,
        citeKey: String? = nil,
        title: String = "",
        authors: [String] = [],
        year: Int? = nil,
        venue: String? = nil,
        doi: String? = nil,
        url: String? = nil,
        abstract: String? = nil,
        volume: String? = nil,
        issue: String? = nil,
        firstPage: String? = nil,
        lastPage: String? = nil,
        publisher: String? = nil,
        workType: String? = nil,
        sourceType: String? = nil
    ) {
        self.line = line
        self.entryType = entryType
        self.citeKey = citeKey
        self.title = title
        self.authors = authors
        self.year = year
        self.venue = venue
        self.doi = doi
        self.url = url
        self.abstract = abstract
        self.volume = volume
        self.issue = issue
        self.firstPage = firstPage
        self.lastPage = lastPage
        self.publisher = publisher
        self.workType = workType
        self.sourceType = sourceType
    }
}

public enum ImportIssueKind: String, Equatable, Sendable {
    /// A BibTeX entry with a syntax error, or one that never closes.
    case malformed
    /// RIS tag lines outside a record, with no `TY` before them.
    case missingType
    /// An entry with neither a title nor a DOI: nothing to import.
    case empty
}

/// An entry that was skipped, and where it starts.
public struct ImportIssue: Equatable, Sendable {
    public var line: Int
    public var kind: ImportIssueKind

    public init(line: Int, kind: ImportIssueKind) {
        self.line = line
        self.kind = kind
    }
}

public struct ImportParseResult: Equatable, Sendable {
    public var format: ImportFormat
    public var entries: [ImportedEntry]
    public var issues: [ImportIssue]

    public init(format: ImportFormat, entries: [ImportedEntry], issues: [ImportIssue]) {
        self.format = format
        self.entries = entries
        self.issues = issues
    }
}
