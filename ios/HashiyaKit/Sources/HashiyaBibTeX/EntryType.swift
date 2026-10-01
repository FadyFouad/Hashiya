import HashiyaModel

/// A BibTeX entry type, the field that holds the paper's venue, and whether a publisher field belongs in it.
enum EntryType: CaseIterable, Equatable, Hashable {
    case article, inProceedings, inCollection, book, phdThesis, techReport, misc

    var bibName: String {
        switch self {
        case .article: "article"
        case .inProceedings: "inproceedings"
        case .inCollection: "incollection"
        case .book: "book"
        case .phdThesis: "phdthesis"
        case .techReport: "techreport"
        case .misc: "misc"
        }
    }

    var venueField: String? {
        switch self {
        case .article: "journal"
        case .inProceedings, .inCollection: "booktitle"
        case .book: nil
        case .phdThesis: "school"
        case .techReport: "institution"
        case .misc: "howpublished"
        }
    }

    var hasPublisher: Bool {
        switch self {
        case .inCollection, .book, .techReport, .misc: true
        case .article, .inProceedings, .phdThesis: false
        }
    }
}

private let journalWorkTypes: Set<String> = ["article", "review", "letter", "editorial"]

/// The spec's table, first match wins: a conference article is @inproceedings, a repository article @misc.
func entryType(_ details: PublicationDetails) -> EntryType {
    let work = details.workType?.lowercased()
    let source = details.sourceType?.lowercased()
    if source == "conference" { return .inProceedings }
    if work == "book-chapter" { return .inCollection }
    if work == "book" { return .book }
    if work == "dissertation" { return .phdThesis }
    if work == "report" { return .techReport }
    if work == "preprint" || source == "repository" { return .misc }
    if let work, journalWorkTypes.contains(work), source == "journal" { return .article }
    return .misc
}
