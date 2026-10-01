@testable import FeatureReader
import HashiyaDesignSystem
import Testing

@MainActor
struct ReaderStringsTests {
    private func inLanguage<T>(_ language: String, _ body: () -> T) -> T {
        let previous = HashiyaLanguage.override
        HashiyaLanguage.override = language
        defer { HashiyaLanguage.override = previous }
        return body()
    }

    @Test func everyReaderStringResolvesInBothLanguages() {
        let keys = [
            "reader.back", "reader.notes", "reader.closeNotes", "reader.share", "reader.search", "reader.cantOpen",
            "reader.replacePdf", "reader.removePdf", "reader.notPdf", "reader.tooLarge", "reader.attachFailed",
            "reader.notesSaveFailed", "reader.retry",
        ]
        for key in keys {
            #expect(inLanguage("en") { L10n.string(key) } != key)
            #expect(inLanguage("ar") { L10n.string(key) } != inLanguage("en") { L10n.string(key) })
        }
        #expect(inLanguage("en") { L10n.string("reader.cantOpen") } == "This PDF can't be opened.")
        #expect(inLanguage("ar") { L10n.string("reader.search") } == "البحث في ملف PDF")
    }

    @Test func thePageLabelCountsFromOne() {
        #expect(inLanguage("en") { L10n.page(2, of: 14) } == "3 of 14")
        // The app's Arabic numbers are Latin digits, and formatting isolates each argument (U+2068 … U+2069), as
        // PaperFormat.citations does.
        #expect(inLanguage("ar") { L10n.page(0, of: 2) } == "\u{2068}1\u{2069} من \u{2068}2\u{2069}")
    }
}
