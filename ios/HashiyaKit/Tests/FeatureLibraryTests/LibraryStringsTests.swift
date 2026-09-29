@testable import FeatureLibrary
import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct LibraryStringsTests {
    private func inLanguage<T>(_ language: String, _ body: () -> T) -> T {
        let previous = HashiyaLanguage.override
        HashiyaLanguage.override = language
        defer { HashiyaLanguage.override = previous }
        return body()
    }

    @Test func paperCountIsPlural() {
        #expect(inLanguage("en") { L10n.paperCount(1) } == "1 paper")
        #expect(inLanguage("en") { L10n.paperCount(3) } == "3 papers")
        #expect(inLanguage("ar") { L10n.paperCount(0) } == "لا توجد أوراق")
        #expect(inLanguage("ar") { L10n.paperCount(2) } == "ورقتان")
        #expect(inLanguage("ar") { L10n.paperCount(3) } == "\u{2068}3\u{2069} أوراق")
    }

    @Test func rowMetaShowsTheFirstAuthorYearAndVenue() {
        #expect(inLanguage("en") { L10n.rowMeta(SamplePapers.attention) } == "Ashish Vaswani et al. · 2017 · Neural Information Processing Systems")
        #expect(inLanguage("en") { L10n.rowMeta(SamplePapers.arabicTitled) } == "محمد علي · 2022")
        #expect(inLanguage("en") { L10n.rowMeta(SamplePapers.untitled) } == "")
    }
}
