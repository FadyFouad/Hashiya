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

/// The BibTeX type for a kind of work: preprints and anything else are @misc.
func entryType(_ details: PublicationDetails) -> EntryType {
    switch details.workKind {
    case .conference: .inProceedings
    case .chapter: .inCollection
    case .book: .book
    case .thesis: .phdThesis
    case .report: .techReport
    case .article: .article
    case .preprint, .other: .misc
    }
}
