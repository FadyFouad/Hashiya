/// OpenAlex's (workType, sourceType) for an entry, so a BibTeX export gives the same entry type back (spec §4.2 and §4.3).
struct EntryKind: Equatable {
    var workType: String?
    var sourceType: String?

    static let none = EntryKind(workType: nil, sourceType: nil)
    static let journalArticle = EntryKind(workType: "article", sourceType: "journal")
    static let conferencePaper = EntryKind(workType: "article", sourceType: "conference")
    static let book = EntryKind(workType: "book", sourceType: nil)
    static let chapter = EntryKind(workType: "book-chapter", sourceType: nil)
    static let thesis = EntryKind(workType: "dissertation", sourceType: nil)
    static let report = EntryKind(workType: "report", sourceType: nil)
    static let preprint = EntryKind(workType: "preprint", sourceType: "repository")
}

func bibTeXKind(type: String, doi: String?, journal: String?) -> EntryKind {
    let kind: EntryKind = switch type {
    case "article": .journalArticle
    case "inproceedings", "conference": .conferencePaper
    case "book", "mvbook", "proceedings": .book
    case "incollection", "inbook", "bookinbook": .chapter
    case "phdthesis", "mastersthesis", "thesis": .thesis
    case "techreport", "report": .report
    default: .none
    }
    let preprint = isArxivDOI(doi) && (kind.workType == nil || isScholarArxivJournal(journal))
    return preprint ? .preprint : kind
}

let risBookTypes: Set<String> = ["BOOK", "EBOOK", "EDBOOK"]

func risKind(type: String, doi: String?) -> EntryKind {
    let kind: EntryKind = switch type {
    case "JOUR", "JFULL", "EJOUR": .journalArticle
    case "MGZN", "NEWS": EntryKind(workType: "article", sourceType: nil)
    case "CONF", "CPAPER": .conferencePaper
    case "BOOK", "EBOOK", "EDBOOK": .book
    case "CHAP", "ECHAP": .chapter
    case "THES": .thesis
    case "RPRT": .report
    default: .none
    }
    return kind.workType == nil && isArxivDOI(doi) ? .preprint : kind
}
