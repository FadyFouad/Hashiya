/// Where the user is with a saved paper. New saves start as `.toRead`.
public enum ReadingStatus: String, CaseIterable, Sendable {
    case toRead, reading, read
}

/// A saved paper with its reading status.
public struct LibraryPaper: Equatable, Hashable, Sendable, Identifiable {
    public var paper: Paper
    public var status: ReadingStatus
    /// True when a PDF is stored for the paper (downloaded or attached), so it can be read offline.
    public var hasPdf: Bool

    /// The paper's OpenAlex ID.
    public var id: String { paper.openAlexID }

    public init(paper: Paper, status: ReadingStatus, hasPdf: Bool = false) {
        self.paper = paper
        self.status = status
        self.hasPdf = hasPdf
    }
}
