@testable import FeatureSearch
import Foundation
import HashiyaDesignSystem
import HashiyaModel
import Testing

@MainActor
struct SearchStringsTests {
    private func inLanguage<T>(_ language: String, _ body: () -> T) -> T {
        let previous = HashiyaLanguage.override
        HashiyaLanguage.override = language
        defer { HashiyaLanguage.override = previous }
        return body()
    }

    @Test func resultCountPicksThePluralAndShowsTheFormattedCount() {
        #expect(inLanguage("en") { L10n.resultCount(48_210) } == "About 48,210 results")
        #expect(inLanguage("en") { L10n.resultCount(1) } == "About 1 result")
    }

    @Test(arguments: [
        (Int64(0), "لا توجد نتائج"),
        (1, "نتيجة واحدة تقريبًا"),
        (2, "نتيجتان تقريبًا"),
        (3, "حوالي \u{2068}3\u{2069} نتائج"),
        (11, "حوالي \u{2068}11\u{2069} نتيجة"),
        (100, "حوالي \u{2068}100\u{2069} نتيجة"),
    ])
    func arabicResultCountUsesEveryPluralForm(count: Int64, expected: String) {
        #expect(inLanguage("ar") { L10n.resultCount(count) } == expected)
    }

    @Test func yearLabelsAreNeverGrouped() {
        #expect(inLanguage("en") { L10n.yearLabel(.since(2020)) } == "Since 2020")
        #expect(inLanguage("en") { L10n.yearLabel(.between(from: 2015, to: 2020)) } == "2015–2020")
        #expect(inLanguage("en") { L10n.yearLabel(.anyTime) } == "Any time")
    }

    @Test func suggestionsAreNotTranslated() {
        #expect(inLanguage("ar") { L10n.string("search.suggestionLLM") } == "large language models")
        #expect(inLanguage("ar") { L10n.string("search.suggestionCRISPR") } == "CRISPR")
        #expect(inLanguage("ar") { L10n.string("search.suggestionClimate") } == "climate adaptation")
    }

    @Test func lookupLabelsNameTheIdentifier() {
        #expect(inLanguage("en") { L10n.lookupLooking(.doi("10.1038/nature14539")) } == "Looking up DOI 10.1038/nature14539…")
        #expect(inLanguage("en") { L10n.lookupLooking(.arxiv("1706.03762")) } == "Looking up arXiv 1706.03762…")
        // Arabic formatting isolates the argument, so the ID stays left-to-right.
        #expect(inLanguage("ar") { L10n.lookupLooking(.arxiv("1706.03762")) } == "جارٍ البحث عن arXiv \u{2068}1706.03762\u{2069}…")
        #expect(inLanguage("en") { L10n.lookupNotFoundTitle(.doi("10.9999/x")) } == "No paper found for this DOI")
        #expect(inLanguage("en") { L10n.lookupNotFoundTitle(.arxiv("1810.04805")) } == "No paper found for this arXiv ID")
    }

    @Test func searchForShortensLongTitlesAndIsolatesThem() {
        let bert = "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding"
        #expect(inLanguage("en") { L10n.searchTitleButton(bert) }
            == "Search for “\u{2068}BERT: Pre-training of Deep Bidirectional Transformers for L…\u{2069}”")
        #expect(inLanguage("ar") { L10n.searchTitleButton(bert) }
            == "ابحث عن «\u{2068}BERT: Pre-training of Deep Bidirectional Transformers for L…\u{2069}»")
    }

    @Test(arguments: [
        (String(repeating: "a", count: 60), String(repeating: "a", count: 60)),
        (String(repeating: "a", count: 61), String(repeating: "a", count: 59) + "…"),
        (String(repeating: "a", count: 58) + "  bcd", String(repeating: "a", count: 58) + "…"),
    ])
    func titlesOver60CharactersAreCut(title: String, shown: String) {
        #expect(L10n.shortenedTitle(title) == shown)
    }

    @Test(arguments: [SearchError.offline, .invalidUserKey, .serviceUnavailable, .rateLimited, .unexpected, .dailyLimit(resetAt: .distantPast)])
    func everyErrorHasATitleAndMessageInBothLanguages(error: SearchError) {
        for language in ["en", "ar"] {
            let text = inLanguage(language) { L10n.error(error) }
            #expect(!text.title.hasPrefix("search."))
            #expect(!text.message.hasPrefix("search."))
        }
    }

    @Test func theDailyLimitMessageShowsTheResetTimeInTheGivenZone() {
        let reset = ISO8601DateFormatter().date(from: "2026-10-06T00:00:00Z")!
        let riyadh = TimeZone(identifier: "Asia/Riyadh")!
        #expect(inLanguage("en") { L10n.dailyLimitMessage(reset, timeZone: riyadh) }.contains("3:00\u{202F}AM"))
        #expect(inLanguage("en") { L10n.error(.dailyLimit(resetAt: reset), timeZone: riyadh).title } == "Daily search limit reached")
    }

    @Test func theArabicDailyLimitMessageIsolatesTheTimeOnce() {
        let reset = ISO8601DateFormatter().date(from: "2026-10-06T00:00:00Z")!
        let message = inLanguage("ar") { L10n.dailyLimitMessage(reset, timeZone: .gmt) }
        #expect(message.components(separatedBy: "\u{2068}").count == 2)
        #expect(message.contains("\u{2069}"))
        #expect(message.contains("OpenAlex"))
    }

    @Test func thePageCapFooterShowsTheNumberOfResults() {
        #expect(inLanguage("en") { L10n.pageCap(200) } == "Showing the first 200 results. Refine your search to see more.")
        #expect(inLanguage("ar") { L10n.pageCap(200) }.contains("\u{2068}200\u{2069}"))
    }
}
