import HashiyaData
import HashiyaNetwork
import Testing

/// Mirrors Android's `FallbackPdfLinksTest`.
struct FallbackPDFLinksTests {
    private func oa(_ url: String?, source: String? = nil, isOA: Bool = true) -> NetworkLocation {
        NetworkLocation(pdfURL: url, source: source.map { NetworkSource(displayName: $0) }, isOA: isOA)
    }

    @Test func keepsOpenAccessLinksOverHTTPSWithoutTheOneTried() {
        let links = fallbackPDFLinks(
            [
                oa("https://langtaosha.org.cn/download/10/108"),
                oa("https://closed.example/paper.pdf", isOA: false),
                oa(nil),
                oa("  "),
                oa("http://repository.example/a.pdf"),
            ],
            tried: "https://langtaosha.org.cn/download/10/108"
        )

        #expect(links == ["https://repository.example/a.pdf"])
    }

    @Test func putsArxivFirstThenKeepsOpenAlexsOrder() {
        let links = fallbackPDFLinks(
            [
                oa("https://one.example/a.pdf"),
                oa("https://two.example/b.pdf"),
                oa("https://export.arxiv.org/pdf/1706.03762v7"),
                oa("https://mirror.example/1706.03762.pdf", source: "arXiv (Cornell University)"),
                oa("http://arxiv.org/pdf/1706.03762"),
            ],
            tried: "https://stored.example/x"
        )

        #expect(links == [
            "https://export.arxiv.org/pdf/1706.03762v7",
            "https://mirror.example/1706.03762.pdf",
            "https://arxiv.org/pdf/1706.03762",
        ])
    }

    @Test func dropsDuplicatesAfterTheUpgradeAndKeepsAtMostThree() {
        let links = fallbackPDFLinks(
            [
                oa("http://arxiv.org/pdf/1706.03762"),
                oa("https://arxiv.org/pdf/1706.03762"),
                oa("https://one.example/a.pdf"),
                oa("https://two.example/b.pdf"),
                oa("https://three.example/c.pdf"),
            ],
            tried: "http://stored.example/x"
        )

        #expect(links == ["https://arxiv.org/pdf/1706.03762", "https://one.example/a.pdf", "https://two.example/b.pdf"])
    }

    @Test func theTriedLinkIsDroppedInEitherScheme() {
        #expect(fallbackPDFLinks([oa("http://arxiv.org/pdf/1"), oa("https://arxiv.org/pdf/1")], tried: "https://arxiv.org/pdf/1").isEmpty)
    }

    @Test func aHostThatOnlyEndsInArxivIsNotArxiv() {
        let links = fallbackPDFLinks([oa("https://one.example/a.pdf"), oa("https://notarxiv.org/b.pdf")], tried: "https://stored.example/x")

        #expect(links == ["https://one.example/a.pdf", "https://notarxiv.org/b.pdf"])
    }
}
