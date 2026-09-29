import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct PaperFormatTests {
    /// Runs `body` with the UI language forced to `language`.
    private func inLanguage<T>(_ language: String, _ body: () -> T) -> T {
        let previous = HashiyaLanguage.override
        HashiyaLanguage.override = language
        defer { HashiyaLanguage.override = previous }
        return body()
    }

    @Test func untitledPapersShowUntitled() {
        #expect(inLanguage("en") { PaperFormat.title(SamplePapers.untitled) } == "Untitled")
        #expect(inLanguage("ar") { PaperFormat.title(SamplePapers.untitled) } == "بدون عنوان")
        #expect(inLanguage("en") { PaperFormat.title(SamplePapers.bert) } == SamplePapers.bert.title)
    }

    @Test func authorsLineShowsThreeNamesAndTheRestAsACount() {
        #expect(inLanguage("en") { PaperFormat.authorsLine(SamplePapers.attention.authors) }
            == "Ashish Vaswani, Noam Shazeer, Niki Parmar +2")
        #expect(inLanguage("en") { PaperFormat.authorsLine(Array(SamplePapers.attention.authors.prefix(3))) }
            == "Ashish Vaswani, Noam Shazeer, Niki Parmar")
        #expect(inLanguage("en") { PaperFormat.authorsLine([]) } == nil)
    }

    @Test func cardMetaJoinsAuthorsYearAndVenue() {
        #expect(inLanguage("en") { PaperFormat.cardMeta(SamplePapers.vit) } == "Alexey Dosovitskiy, Lucas Beyer · 2021 · ICLR")
        #expect(inLanguage("en") { PaperFormat.cardMeta(SamplePapers.untitled) } == "")
    }

    @Test func previewMetaJoinsVenueYearAndCitations() {
        #expect(inLanguage("en") { PaperFormat.previewMeta(SamplePapers.attention) }
            == "Neural Information Processing Systems · 2017 · 128,412 citations")
        #expect(inLanguage("en") { PaperFormat.previewMeta(SamplePapers.untitled) } == "0 citations")
    }

    @Test func yearsAreNeverGrouped() {
        #expect(inLanguage("en") { PaperFormat.year(2024) } == "2024")
    }

    @Test func citationsUseCompactAndFullNumbers() {
        #expect(inLanguage("en") { PaperFormat.compactCitations(128_412) } == "128K cited")
        #expect(inLanguage("en") { PaperFormat.citations(128_412) } == "128,412 citations")
        // Arabic formatting isolates each argument (U+2068 … U+2069) so numbers keep their direction.
        #expect(inLanguage("ar") { PaperFormat.citations(12) } == "\u{2068}12\u{2069} استشهاد")
    }

    @Test func doiLinksKeepSlashesAndEncodeTheRest() {
        #expect(DOILink.url(for: "10.1000/xyz")?.absoluteString == "https://doi.org/10.1000/xyz")
        #expect(DOILink.url(for: "10.1002/(sici)1097<3>#1 x")?.absoluteString
            == "https://doi.org/10.1002/(sici)1097%3C3%3E%231%20x")
    }
}
