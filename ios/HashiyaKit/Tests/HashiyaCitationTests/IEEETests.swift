@testable import HashiyaCitation
import HashiyaModel
import Testing

struct IEEETests {
    @Test func journalArticle() {
        #expect(IEEE.format(paper(venue: "Nature", doi: "https://doi.org/10.1038/nature14539", volume: "521", issue: "7553", first: "436", last: "444")).runs == [
            Run("A. Vaswani and N. Shazeer, \"Attention is all you need,\" "),
            Run("Nature", italic: true),
            Run(", vol. 521, no. 7553, pp. 436–444, 2017, doi: 10.1038/nature14539."),
        ])
    }

    @Test func conferencePaper() {
        #expect(IEEE.format(paper(source: "conference", first: "5998", last: "6008")).plain ==
            "A. Vaswani and N. Shazeer, \"Attention is all you need,\" in Advances in Neural Information Processing Systems, 2017, pp. 5998–6008, doi: 10.5555/3295222.3295349.")
    }

    @Test func bookChapter() {
        #expect(IEEE.format(paper(venue: "Deep Learning", work: "book-chapter", publisher: "MIT Press", first: "12")).plain ==
            "A. Vaswani and N. Shazeer, \"Attention is all you need,\" in Deep Learning. MIT Press, 2017, p. 12, doi: 10.5555/3295222.3295349.")
    }

    @Test func book() {
        #expect(IEEE.format(paper(work: "book", source: nil, publisher: "MIT Press")).runs == [
            Run("A. Vaswani and N. Shazeer, "),
            Run("Attention is all you need", italic: true),
            Run(". MIT Press, 2017, doi: 10.5555/3295222.3295349."),
        ])
    }

    @Test func thesisReportPreprintOther() {
        #expect(IEEE.format(paper(venue: "University of Toronto", doi: nil, work: "dissertation", source: nil)).plain ==
            "A. Vaswani and N. Shazeer, \"Attention is all you need,\" Thesis, University of Toronto, 2017.")
        #expect(IEEE.format(paper(venue: "Google Research", doi: nil, work: "report", source: nil)).plain ==
            "A. Vaswani and N. Shazeer, \"Attention is all you need,\" Google Research, Tech. Rep., 2017.")
        #expect(IEEE.format(paper(venue: "arXiv", doi: "10.48550/arXiv.1706.03762", work: "preprint", source: "repository")).plain ==
            "A. Vaswani and N. Shazeer, \"Attention is all you need,\" arXiv, 2017, doi: 10.48550/arXiv.1706.03762.")
        #expect(IEEE.format(paper(venue: "Zenodo", doi: nil, work: "dataset", source: nil)).plain ==
            "A. Vaswani and N. Shazeer, \"Attention is all you need,\" Zenodo, 2017.")
    }

    @Test func noDoiUsesTheOpenAccessLink() {
        #expect(IEEE.format(paper(venue: "Zenodo", doi: nil, pdf: "https://example.org/a.pdf", work: "dataset", source: nil)).plain ==
            "A. Vaswani and N. Shazeer, \"Attention is all you need,\" Zenodo, 2017. [Online]. Available: https://example.org/a.pdf")
    }

    @Test func noAuthorsNoYearNoVenue() {
        #expect(IEEE.format(paper(authors: [], year: nil, venue: nil, doi: nil, work: nil, source: nil)).plain ==
            "\"Attention is all you need.\"")
        #expect(IEEE.format(paper(title: "", authors: [], year: nil, venue: "Zenodo", doi: nil, work: nil, source: nil)).plain ==
            "\"Untitled,\" Zenodo.")
    }

    @Test func authorLists() {
        func names(_ n: Int) -> [String] { (1...n).map { "Given\($0) Family\($0)" } }
        func text(_ n: Int) -> String { Rendering.plain(StyledCitation(IEEE.authors(names(n)))) }
        #expect(text(1) == "G. Family1")
        #expect(text(2) == "G. Family1 and G. Family2")
        #expect(text(3) == "G. Family1, G. Family2, and G. Family3")
        #expect(text(6).components(separatedBy: "Family").count - 1 == 6)
        #expect(IEEE.authors(names(7)) == [Run("G. Family1 "), Run("et al.", italic: true)])
        #expect(Rendering.plain(StyledCitation(IEEE.authors(["Jean-Paul Sartre", "محمد عبد الله"]))) ==
            "J.-P. Sartre and محمد عبد الله")
    }

    @Test func listNumbersInTheOrderGiven() {
        let papers = ["B", "A"].map {
            paper(title: $0, authors: [], year: nil, venue: "Zenodo", doi: nil, work: nil, source: nil)
        }
        #expect(IEEE.list(papers).map(\.plain) == ["[1] \"B,\" Zenodo.", "[2] \"A,\" Zenodo."])
    }

    @Test func finalFullStopDoesNotDouble() {
        #expect(IEEE.format(paper(year: nil, venue: "Springer-Verlag Inc.", doi: nil, work: "dataset", source: nil)).plain ==
            "A. Vaswani and N. Shazeer, \"Attention is all you need,\" Springer-Verlag Inc.")
    }

    @Test func bookTitleEndingInAQuestionMarkTakesNoFullStop() {
        let c = IEEE.format(paper(title: "Is attention all you need?", doi: nil, work: "book", source: nil, publisher: "MIT Press"))
        #expect(c.plain == "A. Vaswani and N. Shazeer, Is attention all you need? MIT Press, 2017.")
        #expect(c.runs[1] == Run("Is attention all you need?", italic: true))
    }

    @Test func organisationAuthorIsKeptWhole() {
        #expect(IEEE.format(paper(authors: ["OpenAI"])).plain.hasPrefix("OpenAI, \"Attention"))
    }

    @Test func quotedTitleEndingInAMarkTakesNoExtraPunctuation() {
        let q = "Is attention all you need?"
        #expect(IEEE.format(paper(title: q, venue: "Zenodo", doi: nil, work: "dataset", source: nil)).plain ==
            "A. Vaswani and N. Shazeer, \"\(q)\" Zenodo, 2017.")
        #expect(IEEE.format(paper(title: q, year: nil, venue: nil, doi: nil, work: "dataset", source: nil)).plain ==
            "A. Vaswani and N. Shazeer, \"\(q)\"")
        #expect(IEEE.format(paper(title: "Deep learning.", venue: "Zenodo", doi: nil, work: "dataset", source: nil)).plain ==
            "A. Vaswani and N. Shazeer, \"Deep learning.\" Zenodo, 2017.")
    }

    @Test func chapterVenueEndingInAFullStopIsFollowedByThePublisherDirectly() {
        let c = IEEE.format(paper(venue: "Proc. Int. Conf.", doi: nil, work: "book-chapter", publisher: "Springer", first: "12"))
        #expect(c.plain == "A. Vaswani and N. Shazeer, \"Attention is all you need,\" in Proc. Int. Conf. Springer, 2017, p. 12.")
        #expect(c.runs[1] == Run("Proc. Int. Conf.", italic: true))
    }

    @Test func chapterWithoutAVenueKeepsThePublisher() {
        #expect(IEEE.format(paper(venue: nil, doi: nil, work: "book-chapter", publisher: "MIT Press", first: "12")).plain ==
            "A. Vaswani and N. Shazeer, \"Attention is all you need,\" MIT Press, 2017, p. 12.")
    }
}
