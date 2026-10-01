@testable import HashiyaBibTeX
import HashiyaModel
import Testing

/// Mirrors Android's core/bibtex EntryTypeTest, case for case.
struct EntryTypeTests {
    private func type(_ work: String?, _ source: String?) -> EntryType {
        entryType(PublicationDetails(workType: work, sourceType: source))
    }

    @Test func followsTheSpecTableInOrder() {
        #expect(type("article", "conference") == .inProceedings)
        #expect(type("preprint", "conference") == .inProceedings)
        #expect(type("book-chapter", "book series") == .inCollection)
        #expect(type("book", nil) == .book)
        #expect(type("dissertation", "repository") == .phdThesis)
        #expect(type("report", nil) == .techReport)
        #expect(type("preprint", "journal") == .misc)
        #expect(type("article", "repository") == .misc)
        #expect(type("article", "journal") == .article)
        #expect(type("review", "journal") == .article)
        #expect(type("letter", "journal") == .article)
        #expect(type("editorial", "journal") == .article)
    }

    @Test func anythingElseIsMisc() {
        #expect(type("article", nil) == .misc)
        #expect(type("dataset", "repository") == .misc)
        #expect(type(nil, nil) == .misc)
        #expect(type("erratum", "journal") == .misc)
    }

    @Test func ignoresCase() {
        #expect(type("Article", "Journal") == .article)
    }

    @Test func venueFieldsAndPublisher() {
        #expect(EntryType.article.venueField == "journal")
        #expect(EntryType.inProceedings.venueField == "booktitle")
        #expect(EntryType.inCollection.venueField == "booktitle")
        #expect(EntryType.book.venueField == nil)
        #expect(EntryType.phdThesis.venueField == "school")
        #expect(EntryType.techReport.venueField == "institution")
        #expect(EntryType.misc.venueField == "howpublished")
        #expect(Set(EntryType.allCases.filter(\.hasPublisher)) == [.book, .inCollection, .techReport, .misc])
    }

    @Test func bibNames() {
        #expect(EntryType.allCases.map(\.bibName) == ["article", "inproceedings", "incollection", "book", "phdthesis", "techreport", "misc"])
    }
}
