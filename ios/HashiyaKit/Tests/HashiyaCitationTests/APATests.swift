@testable import HashiyaCitation
import HashiyaModel
import Testing

struct APATests {
    @Test func journalArticle() {
        #expect(APA.format(paper(venue: "Nature", doi: "10.1038/nature14539", volume: "521", issue: "7553", first: "436", last: "444")).runs == [
            Run("Vaswani, A., & Shazeer, N. (2017). Attention is all you need. "),
            Run("Nature", italic: true),
            Run(", "),
            Run("521", italic: true),
            Run("(7553), 436–444. https://doi.org/10.1038/nature14539"),
        ])
    }

    @Test func conferencePaper() {
        #expect(APA.format(paper(source: "conference", publisher: "Curran Associates", first: "5998", last: "6008")).plain ==
            "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. In Advances in Neural Information Processing Systems " +
            "(pp. 5998–6008). Curran Associates. https://doi.org/10.5555/3295222.3295349")
    }

    @Test func bookChapterSinglePage() {
        #expect(APA.format(paper(venue: "Deep Learning", work: "book-chapter", publisher: "MIT Press", first: "12", last: "12")).plain ==
            "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. In Deep Learning (p. 12). MIT Press. https://doi.org/10.5555/3295222.3295349")
    }

    @Test func bookItalicisesTheTitle() {
        #expect(APA.format(paper(work: "book", source: nil, publisher: "MIT Press")).runs == [
            Run("Vaswani, A., & Shazeer, N. (2017). "),
            Run("Attention is all you need", italic: true),
            Run(". MIT Press. https://doi.org/10.5555/3295222.3295349"),
        ])
    }

    @Test func thesis() {
        #expect(APA.format(paper(venue: "University of Toronto", work: "dissertation", source: nil)).plain ==
            "Vaswani, A., & Shazeer, N. (2017). Attention is all you need [Thesis, University of Toronto]. https://doi.org/10.5555/3295222.3295349")
    }

    @Test func reportUsesThePublisherElseTheVenue() {
        #expect(APA.format(paper(venue: "Google Research", work: "report", source: nil)).plain ==
            "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. Google Research. https://doi.org/10.5555/3295222.3295349")
    }

    @Test func preprint() {
        #expect(APA.format(paper(venue: "arXiv", doi: "https://doi.org/10.48550/arXiv.1706.03762", work: "preprint", source: "repository")).plain ==
            "Vaswani, A., & Shazeer, N. (2017). Attention is all you need [Preprint]. arXiv. https://doi.org/10.48550/arXiv.1706.03762")
    }

    @Test func other() {
        #expect(APA.format(paper(venue: "Zenodo", doi: nil, work: "dataset", source: nil)).plain ==
            "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. Zenodo.")
    }

    @Test func noDoiUsesTheOpenAccessLink() {
        #expect(APA.format(paper(venue: "Zenodo", doi: nil, pdf: "https://example.org/a.pdf", work: "dataset", source: nil)).plain ==
            "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. Zenodo. https://example.org/a.pdf")
    }

    @Test func noAuthorsNoYearNoTitle() {
        #expect(APA.format(paper(title: " ", authors: [], year: nil, venue: "Zenodo", doi: nil, work: nil, source: nil)).plain ==
            "[Untitled]. (n.d.). Zenodo.")
        #expect(APA.format(paper(authors: [], year: nil, venue: "Zenodo", doi: nil, work: nil, source: nil)).plain ==
            "Attention is all you need. (n.d.). Zenodo.")
    }

    @Test func aTitleEndingInAQuestionMarkGetsNoFullStop() {
        #expect(APA.format(paper(title: "Is attention all you need?", venue: "Zenodo", doi: nil, work: nil, source: nil)).plain ==
            "Vaswani, A., & Shazeer, N. (2017). Is attention all you need? Zenodo.")
    }

    @Test func authorLists() {
        func names(_ n: Int) -> [String] { (1...n).map { "Given\($0) Family\($0)" } }
        #expect(APA.authors(names(1)) == "Family1, G.")
        #expect(APA.authors(names(2)) == "Family1, G., & Family2, G.")
        #expect(APA.authors(names(3)) == "Family1, G., Family2, G., & Family3, G.")
        #expect(APA.authors(names(20)).components(separatedBy: "Family").count - 1 == 20)
        let many = APA.authors(names(21))
        #expect(many.contains("Family19, G., . . . Family21, G."))
        #expect(!many.contains("Family20"))
        #expect(APA.authors(["OpenAI", "Given2 Family2"]) == "OpenAI, & Family2, G.")
    }

    @Test func listSortsByFamilyIgnoringCaseAndDiacriticsThenYearThenTitle() {
        let list = APA.list([
            paper(title: "B", authors: ["Zoe Zed"], year: 2020),
            paper(title: "C", authors: ["Ann Émile"], year: nil),
            paper(title: "A", authors: ["Ann emile"], year: 2019),
            paper(title: "D", authors: [], year: 2018),
        ]).map { $0.plain.components(separatedBy: " (")[0] }
        #expect(list == ["D.", "emile, A.", "Émile, A.", "Zed, Z."])
    }

    @Test func aWholeNameGetsAFullStopBeforeTheYear() {
        #expect(APA.format(paper(authors: ["OpenAI"])).plain.hasPrefix("OpenAI. (2017). "))
        #expect(APA.format(paper(authors: ["Ashish Vaswani", "محمد علي"])).plain.hasPrefix("Vaswani, A., & محمد علي. (2017). "))
    }

    @Test func anIssueWithoutAVolumeIsSeparatedFromTheJournal() {
        let c = APA.format(paper(venue: "Nature", doi: nil, volume: nil, issue: "7553", first: "436", last: "444"))
        #expect(c.plain.contains("Nature, (7553), 436–444."))
        #expect(Array(c.runs.dropFirst().prefix(2)) == [Run("Nature", italic: true), Run(", (7553), 436–444.")])
    }

    @Test func aPublisherEndingInAFullStopGetsNoSecondOne() {
        #expect(APA.format(paper(work: "book", source: nil, publisher: "Springer-Verlag Inc.")).plain ==
            "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. Springer-Verlag Inc. https://doi.org/10.5555/3295222.3295349")
    }

    @Test func aVenueEndingInAFullStopGetsNoSecondOne() {
        #expect(APA.format(paper(venue: "Proc. IEEE Conf.", source: "conference", publisher: "Curran")).plain ==
            "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. In Proc. IEEE Conf. Curran. https://doi.org/10.5555/3295222.3295349")
    }

    /// Kotlin keys: أحمد→احمد, ابراهيم, إسماعيل→اسماعيل, آدم→ادم, اسامة. By UTF-16:
    /// ابراهيم (ب), احمد (ح), ادم (د), اسامة (س,ا), اسماعيل (س,م).
    @Test func arabicAlefVariantsSortAsTheirBareAlef() {
        let names = ["أحمد", "ابراهيم", "إسماعيل", "آدم", "اسامة"]
        let list = APA.list(names.map { paper(authors: [$0]) }).map { $0.plain.components(separatedBy: ". (")[0] }
        #expect(list == ["ابراهيم", "أحمد", "آدم", "اسامة", "إسماعيل"])
    }

    /// Kotlin's NFD splits 김 into jamo U+1100 U+1175 U+11B7, which come before 王 (U+738B) by UTF-16.
    @Test func hangulSortsAsItsDecomposedJamo() {
        let list = APA.list([paper(authors: ["王"]), paper(authors: ["김"])]).map { $0.plain.components(separatedBy: ". (")[0] }
        #expect(list == ["김", "王"])
    }

    /// 😀 is U+1F600, UTF-16 D83D DE00, so it precedes the fullwidth Ａ (key U+FF41) by UTF-16 units, though not by scalars.
    @Test func sortsByUTF16UnitsNotScalars() {
        let list = APA.list([paper(authors: ["Ａ"]), paper(authors: ["😀"])]).map { $0.plain.components(separatedBy: ". (")[0] }
        #expect(list == ["😀", "Ａ"])
    }

    @Test func sortKeyStripsMarksLikeAndroid() {
        #expect(sortKey("Émile") == "emile")
        #expect(sortKey("آدم").hasPrefix("ا"))
        #expect(sortKey("آدم").unicodeScalars.first?.value == 0x0627)
    }
}
