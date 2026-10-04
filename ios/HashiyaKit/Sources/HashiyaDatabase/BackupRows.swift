import Foundation

/// Everything an export writes, read in one transaction so it is consistent.
public struct BackupSnapshot: Sendable {
    public var papers: [PaperWithAuthors]
    public var notes: [PaperNotesRecord]
    public var collections: [CollectionRecord]
    public var links: [CollectionPaperRecord]

    public init(papers: [PaperWithAuthors], notes: [PaperNotesRecord], collections: [CollectionRecord], links: [CollectionPaperRecord]) {
        self.papers = papers
        self.notes = notes
        self.collections = collections
        self.links = links
    }
}

/// How many saved papers have a PDF and their total size.
public struct PdfTotals: Equatable, Sendable {
    public var count: Int
    public var bytes: Int64

    public init(count: Int, bytes: Int64) {
        self.count = count
        self.bytes = bytes
    }
}

/// A backup paper ready to merge. `paper` has a fresh local id, and its `pdf*` columns are set only when a staged PDF goes
/// with it; `authors` and `notes` belong to that id.
public struct IncomingPaper: Sendable {
    public var ref: Int
    public var paper: PaperRecord
    public var authors: [PaperAuthorRecord]
    public var notes: PaperNotesRecord?

    public init(ref: Int, paper: PaperRecord, authors: [PaperAuthorRecord], notes: PaperNotesRecord?) {
        self.ref = ref
        self.paper = paper
        self.authors = authors
        self.notes = notes
    }
}

/// `refs` are backup refs; ones that name no paper in the merge are skipped.
public struct IncomingCollection: Sendable {
    public var name: String
    public var nameKey: String
    public var createdAt: Int64
    public var refs: [Int]

    public init(name: String, nameKey: String, createdAt: Int64, refs: [Int]) {
        self.name = name
        self.nameKey = nameKey
        self.createdAt = createdAt
        self.refs = refs
    }
}

/// `pdfTargets`: backup ref to the local id whose PDF columns now point at that ref's staged file.
public struct MergeOutcome: Equatable, Sendable {
    public var added: Int
    public var matched: Int
    public var notesAdded: Int
    public var collectionsCreated: Int
    public var pdfTargets: [Int: String]

    public init(added: Int, matched: Int, notesAdded: Int, collectionsCreated: Int, pdfTargets: [Int: String]) {
        self.added = added
        self.matched = matched
        self.notesAdded = notesAdded
        self.collectionsCreated = collectionsCreated
        self.pdfTargets = pdfTargets
    }
}
