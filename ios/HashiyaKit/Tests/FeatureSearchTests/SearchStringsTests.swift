@testable import FeatureSearch
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

    @Test(arguments: [SearchError.offline, .invalidUserKey, .serviceUnavailable, .rateLimited, .unexpected])
    func everyErrorHasATitleAndMessageInBothLanguages(error: SearchError) {
        for language in ["en", "ar"] {
            let text = inLanguage(language) { L10n.error(error) }
            #expect(!text.title.hasPrefix("search."))
            #expect(!text.message.hasPrefix("search."))
        }
    }
}
