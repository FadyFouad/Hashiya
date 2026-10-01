@testable import FeaturePaperDetails
import HashiyaDesignSystem
import HashiyaModel
import Testing

@MainActor
struct PaperDetailsStringsTests {
    private func inLanguage<T>(_ language: String, _ body: () -> T) -> T {
        let previous = HashiyaLanguage.override
        HashiyaLanguage.override = language
        defer { HashiyaLanguage.override = previous }
        return body()
    }

    @Test func theCollectionAndBibTeXStringsResolveInBothLanguages() {
        let keys = [
            "details.collections", "details.noCollections", "details.collectionsHint", "details.newCollection",
            "details.copyBibtex", "details.bibtexCopied", "details.bibtexIncomplete",
            "details.collectionsUpdateFailed", "details.copyFailed",
        ]
        for key in keys {
            #expect(inLanguage("en") { L10n.string(key) } != key)
            #expect(inLanguage("ar") { L10n.string(key) } != inLanguage("en") { L10n.string(key) })
        }
        #expect(inLanguage("en") { L10n.string("details.copyBibtex") } == "Copy BibTeX")
        #expect(inLanguage("ar") { L10n.string("details.copyBibtex") } == "نسخ BibTeX")
        #expect(inLanguage("ar") { L10n.string("details.noCollections") } == "ليست في أي مجموعة")
    }
}
