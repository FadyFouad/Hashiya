@testable import HashiyaData
import HashiyaModel
import HashiyaNetwork
import HashiyaTesting
import Testing

struct ArxivLookupTests {
    @Test func theFilterCoversUnversionedVersionedAndDOILandingPages() {
        let pages = [
            "http://arxiv.org/abs/1810.04805",
            "http://arxiv.org/abs/1810.04805v1",
            "http://arxiv.org/abs/1810.04805v2",
            "http://arxiv.org/abs/1810.04805v3",
            "http://arxiv.org/abs/1810.04805v4",
            "http://arxiv.org/abs/1810.04805v5",
            "https://arxiv.org/abs/1810.04805",
            "https://arxiv.org/abs/1810.04805v1",
            "https://arxiv.org/abs/1810.04805v2",
            "https://arxiv.org/abs/1810.04805v3",
            "https://arxiv.org/abs/1810.04805v4",
            "https://arxiv.org/abs/1810.04805v5",
            "https://doi.org/10.48550/arxiv.1810.04805",
        ]
        #expect(arxivLandingPageFilter("1810.04805") == "locations.landing_page_url:" + pages.joined(separator: "|"))
    }

    @Test func oldStyleIDsKeepTheirSlash() {
        #expect(arxivLandingPageFilter("hep-th/9901001").contains("http://arxiv.org/abs/hep-th/9901001|"))
    }

    @Test func titlesMatchIgnoringCasePunctuationAndSpacing() {
        #expect(titlesMatch(
            "BERT: Pre-training of Deep  Bidirectional Transformers for Language Understanding.",
            "bert pre training of deep bidirectional transformers for language understanding"
        ))
    }

    @Test func titlesMatchIgnoringQuoteStyles() {
        #expect(titlesMatch("Don’t Stop Pretraining", "Don't stop pretraining"))
    }

    @Test func titlesMatchIgnoringAccentEncodingAndAccents() {
        let composed = "Schr\u{00F6}dinger Equations"
        let decomposed = "Schro\u{0308}dinger equations"
        #expect(titlesMatch(composed, decomposed))
        #expect(titlesMatch(composed, "Schrodinger equations"))
    }

    @Test func titlesMatchForNonLatinTitles() {
        #expect(titlesMatch("تعلم الآلة", "تعلم الآلة."))
    }

    @Test func differentTitlesDoNotMatch() {
        #expect(!titlesMatch(
            "AI-Assisted Pipeline for Dynamic Generation of Trustworthy Health Supplement Content at Scale",
            "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding"
        ))
    }

    @Test func emptyTitlesNeverMatch() {
        #expect(!titlesMatch("", ""))
        #expect(!titlesMatch("!!!", "..."))
    }
}

struct OpenAlexPaperLookupRepositoryTests {
    private let bertTitle = "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding"
    private let bert = PaperIdentifier.arxiv("1810.04805")
    private let attention = PaperIdentifier.arxiv("1706.03762")

    private func work(_ id: String, _ title: String) -> NetworkWork {
        NetworkWork(id: "https://openalex.org/" + id, displayName: title)
    }

    private func repository(_ openAlex: FakeOpenAlexLookupService, _ arxiv: FakeArxivTitleService = FakeArxivTitleService()) -> OpenAlexPaperLookupRepository {
        OpenAlexPaperLookupRepository(openAlex: openAlex, arxiv: arxiv)
    }

    private func foundID(_ result: LookupResult) -> String? {
        if case let .found(paper) = result { paper.openAlexID } else { nil }
    }

    @Test func aDOIIsLookedUpDirectly() async {
        let openAlex = FakeOpenAlexLookupService(works: ["doi:10.1038/nature14539": work("W2919115771", "Deep learning")])
        let arxiv = FakeArxivTitleService()

        let result = await repository(openAlex, arxiv).lookup(.doi("10.1038/nature14539"))

        #expect(foundID(result) == "W2919115771")
        #expect(openAlex.worksRequests.isEmpty)
        #expect(arxiv.requests.isEmpty)
    }

    @Test func anUnknownDOIIsNotFound() async {
        #expect(await repository(FakeOpenAlexLookupService()).lookup(.doi("10.9999/nothing")) == .notFound(arxivTitle: nil))
    }

    @Test func anOfflineDOILookupFails() async {
        let openAlex = FakeOpenAlexLookupService()
        openAlex.setWorkFailure(.connectivity)
        #expect(await repository(openAlex).lookup(.doi("10.1038/nature14539")) == .failed(.offline))
    }

    @Test func aRejectedUserKeyFails() async {
        let openAlex = FakeOpenAlexLookupService()
        openAlex.setWorkFailure(.http(code: 401, usedUserKey: true))
        #expect(await repository(openAlex).lookup(.doi("10.1038/nature14539")) == .failed(.invalidUserKey))
    }

    @Test func anArxivDOIMatchIsTrusted() async {
        let openAlex = FakeOpenAlexLookupService(works: ["doi:10.48550/arXiv.2310.06825": work("W4387561528", "Mistral 7B")])
        let arxiv = FakeArxivTitleService()

        let result = await repository(openAlex, arxiv).lookup(.arxiv("2310.06825"))

        #expect(foundID(result) == "W4387561528")
        #expect(openAlex.worksRequests.isEmpty)
        #expect(arxiv.requests.isEmpty)
    }

    @Test func theFallbackUsesTheLandingPageFilterAndChecksTheTitle() async {
        let openAlex = FakeOpenAlexLookupService(found: [work("W2626778328", "Attention Is All You Need")])
        let arxiv = FakeArxivTitleService(titles: ["1706.03762": "Attention Is All You Need"])

        let result = await repository(openAlex, arxiv).lookup(attention)

        #expect(foundID(result) == "W2626778328")
        #expect(openAlex.workRequests == ["doi:10.48550/arXiv.1706.03762"])
        #expect(openAlex.worksRequests == [.init(filter: arxivLandingPageFilter("1706.03762"), perPage: 2)])
        #expect(arxiv.requests == ["1706.03762"])
    }

    /// OpenAlex's only landing-page match for BERT's arXiv ID is another paper (seen live on 2026-09-28).
    @Test func aFallbackWithTheWrongTitleIsNotFound() async {
        let openAlex = FakeOpenAlexLookupService(found: [
            work("W2896457183", "AI-Assisted Pipeline for Dynamic Generation of Trustworthy Health Supplement Content"),
        ])
        let arxiv = FakeArxivTitleService(titles: ["1810.04805": bertTitle])

        #expect(await repository(openAlex, arxiv).lookup(bert) == .notFound(arxivTitle: bertTitle))
    }

    @Test func aFallbackWithoutMatchesOffersTheArxivTitle() async {
        let arxiv = FakeArxivTitleService(titles: ["1810.04805": bertTitle])
        #expect(await repository(FakeOpenAlexLookupService(), arxiv).lookup(bert) == .notFound(arxivTitle: bertTitle))
    }

    @Test func aFallbackWithoutMatchesAndArxivDownIsPlainNotFound() async {
        let arxiv = FakeArxivTitleService()
        arxiv.setFailure(.connectivity)
        #expect(await repository(FakeOpenAlexLookupService(), arxiv).lookup(bert) == .notFound(arxivTitle: nil))
    }

    @Test func twoDifferentWorksAreNotFound() async {
        let openAlex = FakeOpenAlexLookupService(found: [work("W1", bertTitle), work("W2", bertTitle)])
        let arxiv = FakeArxivTitleService(titles: ["1810.04805": bertTitle])

        #expect(await repository(openAlex, arxiv).lookup(bert) == .notFound(arxivTitle: bertTitle))
    }

    @Test func theSameWorkTwiceCountsAsOne() async {
        let openAlex = FakeOpenAlexLookupService(found: [work("W1", bertTitle), work("W1", bertTitle)])
        let arxiv = FakeArxivTitleService(titles: ["1810.04805": bertTitle])

        #expect(foundID(await repository(openAlex, arxiv).lookup(bert)) == "W1")
    }

    @Test(arguments: [NetworkFailure.connectivity, .http(code: 503, usedUserKey: false), .malformedResponse])
    func aCrossCheckWhileArxivFailsIsUnavailable(failure: NetworkFailure) async {
        let openAlex = FakeOpenAlexLookupService(found: [work("W1", bertTitle)])
        let arxiv = FakeArxivTitleService()
        arxiv.setFailure(failure)

        #expect(await repository(openAlex, arxiv).lookup(bert) == .failed(.serviceUnavailable))
    }

    @Test func aCrossCheckWhenArxivHasNoSuchPaperIsNotFound() async {
        let openAlex = FakeOpenAlexLookupService(found: [work("W1", bertTitle)])
        #expect(await repository(openAlex).lookup(bert) == .notFound(arxivTitle: nil))
    }

    @Test func aRateLimitDuringTheFallbackFails() async {
        let openAlex = FakeOpenAlexLookupService()
        openAlex.setWorksFailure(.http(code: 429, usedUserKey: false))
        #expect(await repository(openAlex).lookup(bert) == .failed(.rateLimited))
    }
}
