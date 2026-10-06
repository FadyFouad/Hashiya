import HashiyaModel
import Testing

struct WorkKindTests {
    private func kind(_ work: String?, _ source: String?) -> WorkKind {
        PublicationDetails(workType: work, sourceType: source).workKind
    }

    @Test func followsTheBibTeXTableInOrder() {
        #expect(kind("article", "conference") == .conference)
        #expect(kind("book-chapter", "book") == .chapter)
        #expect(kind("book", nil) == .book)
        #expect(kind("dissertation", nil) == .thesis)
        #expect(kind("report", nil) == .report)
        #expect(kind("preprint", nil) == .preprint)
        #expect(kind("article", "repository") == .preprint)
        #expect(kind("Article", "Journal") == .article)
        #expect(kind("review", "journal") == .article)
    }

    @Test func anythingElseIsOther() {
        #expect(kind(nil, nil) == .other)
        #expect(kind("article", nil) == .other)
        #expect(kind("dataset", "journal") == .other)
    }

    @Test func stylesRoundTripAndDefaultToAPA() {
        for style in CitationStyle.allCases { #expect(CitationStyle(storedValue: style.rawValue) == style) }
        #expect(CitationStyle(storedValue: nil) == .apa)
        #expect(CitationStyle(storedValue: "harvard") == .apa)
    }
}
