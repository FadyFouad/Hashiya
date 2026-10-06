/// What kind of work a paper is, from OpenAlex's work and source types; BibTeX and the citation styles share it.
public enum WorkKind: Sendable {
    case article, conference, chapter, book, thesis, report, preprint, other
}

private let journalWorkTypes: Set<String> = ["article", "review", "letter", "editorial"]

extension PublicationDetails {
    /// First match wins: a conference article is a conference paper, a repository article a preprint.
    public var workKind: WorkKind {
        let work = workType?.lowercased()
        let source = sourceType?.lowercased()
        if source == "conference" { return .conference }
        if work == "book-chapter" { return .chapter }
        if work == "book" { return .book }
        if work == "dissertation" { return .thesis }
        if work == "report" { return .report }
        if work == "preprint" || source == "repository" { return .preprint }
        if let work, journalWorkTypes.contains(work), source == "journal" { return .article }
        return .other
    }
}
