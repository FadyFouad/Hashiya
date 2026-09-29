import HashiyaModel
import Testing

/// Ported from Android's `PaperIdentifierTest.kt`; each Android `@Test` is one parameterised test.
struct PaperIdentifierTests {
    static let sici = "10.1002/(sici)1099-1212(199901/02)9:1<8::aid-oa453>3.0.co;2-z"

    @Test(arguments: [
        ("10.1038/nature14539", PaperIdentifier.doi("10.1038/nature14539")),
        ("  https://doi.org/10.1038/NATURE14539 ", .doi("10.1038/nature14539")),
        ("http://dx.doi.org/10.1038/nature14539", .doi("10.1038/nature14539")),
        ("doi:10.1038/nature14539", .doi("10.1038/nature14539")),
        ("DOI: 10.1038/nature14539", .doi("10.1038/nature14539")),
        ("https://onlinelibrary.wiley.com/doi/full/10.1002/anie.201915678?af=R#section", .doi("10.1002/anie.201915678")),
        ("https://dl.acm.org/doi/10.1145/3292500.3330701", .doi("10.1145/3292500.3330701")),
        ("https://link.springer.com/article/10.1007/s11263-015-0816-y", .doi("10.1007/s11263-015-0816-y")),
        ("https://doi.org/10.1002/(SICI)1099-1212(199901/02)9:1%3C8::AID-OA453%3E3.0.CO;2-Z", .doi(sici)),
        ("10.1038/nature14539.", .doi("10.1038/nature14539")),
    ])
    func strictRecognizesDOIs(input: String, expected: PaperIdentifier) {
        #expect(parsePaperIdentifier(input) == expected)
    }

    @Test(arguments: [
        ("https://link.springer.com/content/pdf/10.1007/s11263-015-0816-y.pdf", PaperIdentifier.doi("10.1007/s11263-015-0816-y")),
        ("https://onlinelibrary.wiley.com/doi/10.1002/anie.201915678/full", .doi("10.1002/anie.201915678")),
        ("https://onlinelibrary.wiley.com/doi/10.1002/anie.201915678/abstract", .doi("10.1002/anie.201915678")),
        ("https://onlinelibrary.wiley.com/doi/10.1002/anie.201915678/epdf", .doi("10.1002/anie.201915678")),
        ("https://example.org/10.1002/anie.201915678/PDF/", .doi("10.1002/anie.201915678")),
        ("https://www.biorxiv.org/content/10.1101/2020.01.01.123456v1", .doi("10.1101/2020.01.01.123456")),
        ("https://www.biorxiv.org/content/10.1101/2020.01.01.123456v2.full", .doi("10.1101/2020.01.01.123456")),
        ("https://www.medrxiv.org/content/10.1101/2020.01.01.123456v1.full.pdf", .doi("10.1101/2020.01.01.123456")),
    ])
    func publisherLinksDropPDFAndViewSuffixes(input: String, expected: PaperIdentifier) {
        #expect(parsePaperIdentifier(input) == expected)
    }

    @Test(arguments: [
        ("https://doi.org/10.1000/xyz.pdf", PaperIdentifier.doi("10.1000/xyz.pdf")),
        ("https://doi.org/10.1000/abc/full", .doi("10.1000/abc/full")),
        ("doi:10.1000/xyz.pdf", .doi("10.1000/xyz.pdf")),
        ("10.1101/2020.01.01.123456v1", .doi("10.1101/2020.01.01.123456v1")),
    ])
    func doiLinksAndPlainDOIsKeepTheirSuffixes(input: String, expected: PaperIdentifier) {
        #expect(parsePaperIdentifier(input) == expected)
    }

    @Test(arguments: [
        ("1706.03762", PaperIdentifier.arxiv("1706.03762")),
        ("2401.00001v2", .arxiv("2401.00001")),
        ("ARXIV:2401.00001", .arxiv("2401.00001")),
        ("arXiv: 2401.00001", .arxiv("2401.00001")),
        ("https://arxiv.org/abs/1706.03762v5", .arxiv("1706.03762")),
        ("arxiv.org/pdf/2401.00001v2.pdf", .arxiv("2401.00001")),
        ("https://arxiv.org/html/2401.00001v2", .arxiv("2401.00001")),
        ("https://www.arxiv.org/abs/2310.06825", .arxiv("2310.06825")),
        ("http://export.arxiv.org/abs/hep-th/9901001v2", .arxiv("hep-th/9901001")),
        ("hep-th/9901001", .arxiv("hep-th/9901001")),
        ("math.GT/0309136", .arxiv("math/0309136")),
        ("10.48550/ARXIV.1706.03762", .arxiv("1706.03762")),
        ("https://doi.org/10.48550/arXiv.2310.06825", .arxiv("2310.06825")),
        ("10.48550/arXiv.math/0309136", .arxiv("math/0309136")),
    ])
    func strictRecognizesArxivIDs(input: String, expected: PaperIdentifier) {
        #expect(parsePaperIdentifier(input) == expected)
    }

    @Test(arguments: [
        ("0704.0001", PaperIdentifier?.some(.arxiv("0704.0001"))),
        ("1412.6980", .arxiv("1412.6980")),
        ("2401.00001", .arxiv("2401.00001")),
        ("1706.0376", nil),
        ("2401.0001", nil),
        ("0612.0001", nil),
        ("1412.69801", nil),
    ])
    func strictAcceptsOnlyRealNewStyleArxivShapes(input: String, expected: PaperIdentifier?) {
        #expect(parsePaperIdentifier(input) == expected)
    }

    @Test(arguments: [
        "",
        "   ",
        "machine learning",
        "a study of 10.1038/nature14539",
        "https://example.com/2401.00001",
        "https://arxiv.org/list/cs.LG/recent",
        "10.1038",
        "2401.001",
        "2023.12345",
        "1234",
    ])
    func strictRejectsEverythingElse(input: String) {
        #expect(parsePaperIdentifier(input) == nil)
    }

    @Test(arguments: [
        ("https://www.nature.com/articles/d41586-026-02937-z", PaperIdentifier?.some(.doi("10.1038/d41586-026-02937-z"))),
        ("https://www.nature.com/articles/nature14539.pdf", .doi("10.1038/nature14539")),
        ("https://www.nature.com/articles/nature14539", .doi("10.1038/nature14539")),
        ("https://nature.com/articles/s41598-021-81234-5?error=cookies_not_supported", .doi("10.1038/s41598-021-81234-5")),
        ("https://www.nature.com/nature/volumes/620", nil),
        ("https://www.nature.com/subjects/physics", nil),
    ])
    func strictRecognizesNatureArticleLinksAsDOIs(input: String, expected: PaperIdentifier?) {
        #expect(parsePaperIdentifier(input) == expected)
    }

    @Test(arguments: [
        ("10.1000/abc(1)", PaperIdentifier.doi("10.1000/abc(1)")),
        ("(10.1000/abc)", .doi("10.1000/abc")),
        ("<https://arxiv.org/abs/1706.03762>", .arxiv("1706.03762")),
    ])
    func strictHandlesParentheses(input: String, expected: PaperIdentifier) {
        #expect(parsePaperIdentifier(input) == expected)
    }

    @Test(arguments: [
        ("Attention Is All You Need https://arxiv.org/abs/1706.03762", PaperIdentifier.arxiv("1706.03762")),
        ("a study of 10.1038/nature14539.", .doi("10.1038/nature14539")),
        ("(see doi: 10.1000/xyz123)", .doi("10.1000/xyz123")),
        ("Ref: 10.1000/abc(1), page 3", .doi("10.1000/abc(1)")),
        ("SICI \(sici) is old", .doi(sici)),
        ("see 2401.00001, it is good", .arxiv("2401.00001")),
        ("check https://example.com/page and 10.1000/xyz", .doi("10.1000/xyz")),
        ("Check this out: https://arxiv.org/abs/2401.00001v2", .arxiv("2401.00001")),
        ("Check this out: [Attention Is All You Need](https://arxiv.org/abs/1706.03762)", .arxiv("1706.03762")),
        ("[Deep learning](https://doi.org/10.1038/nature14539)", .doi("10.1038/nature14539")),
        ("Link:https://arxiv.org/abs/2401.00001", .arxiv("2401.00001")),
    ])
    func lenientFindsIDsInsideText(input: String, expected: PaperIdentifier) {
        #expect(extractPaperIdentifier(input) == expected)
    }

    @Test(arguments: [
        ("https://arxiv.org/abs/2401.00001 also 10.1038/nature14539", PaperIdentifier.arxiv("2401.00001")),
        ("10.1038/nature14539 then https://arxiv.org/abs/2401.00001", .arxiv("2401.00001")),
    ])
    func lenientPrefersTheFirstLink(input: String, expected: PaperIdentifier) {
        #expect(extractPaperIdentifier(input) == expected)
    }

    @Test func lenientRecognizesNatureArticleLinks() {
        #expect(extractPaperIdentifier("Read this https://www.nature.com/articles/nature14539 now") == .doi("10.1038/nature14539"))
    }

    @Test(arguments: ["https://example.com/2401.00001", "[x](https://example.com/2401.00001)", "nothing to see here", ""])
    func lenientRejectsTextWithoutIDs(input: String) {
        #expect(extractPaperIdentifier(input) == nil)
    }

    @Test(arguments: [
        ("https://example.com/some/article", true),
        ("  www.example.com  ", true),
        ("10.1038/x", false),
        ("hello world", false),
        ("https://a.b c", false),
    ])
    func looksLikeLinkRecognizesSingleURLTokens(input: String, expected: Bool) {
        #expect(looksLikeLink(input) == expected)
    }

    @Test func lenientIgnoresTextBeyondTwoThousandCharacters() {
        #expect(extractPaperIdentifier(String(repeating: "x", count: 2_000) + " 10.1038/nature14539") == nil)
        #expect(extractPaperIdentifier(String(repeating: "x", count: 1_900) + " 10.1038/nature14539") == .doi("10.1038/nature14539"))
    }

    /// iOS only: Swift's and ICU's `\d` match any Unicode digit; Android's patterns and ours match ASCII digits only.
    @Test func arabicIndicDigitsAreNotAnArxivID() {
        #expect(parsePaperIdentifier("١٧٠٦.٠٣٧٦٢") == nil)
        #expect(extractPaperIdentifier("١٧٠٦.٠٣٧٦٢") == nil)
    }
}
