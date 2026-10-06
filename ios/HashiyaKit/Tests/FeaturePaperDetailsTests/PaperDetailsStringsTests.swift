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
            "details.copyBibtex", "details.bibtexCopied", "details.citationIncomplete",
            "details.copyApa", "details.copyIeee", "details.apaCopied", "details.ieeeCopied",
            "details.collectionsUpdateFailed", "details.copyFailed",
        ]
        for key in keys {
            #expect(inLanguage("en") { L10n.string(key) } != key)
            #expect(inLanguage("ar") { L10n.string(key) } != inLanguage("en") { L10n.string(key) })
        }
        #expect(inLanguage("en") { L10n.string("details.copyBibtex") } == "Copy BibTeX")
        #expect(inLanguage("ar") { L10n.string("details.copyBibtex") } == "نسخ BibTeX")
        #expect(inLanguage("en") { L10n.string("details.copyApa") } == "Copy APA 7 citation")
        #expect(inLanguage("ar") { L10n.string("details.copyIeee") } == "نسخ استشهاد IEEE")
        #expect(inLanguage("ar") { L10n.string("details.noCollections") } == "ليست في أي مجموعة")
    }

    @Test func thePdfStringsResolveInBothLanguages() {
        let keys = [
            "details.pdf", "details.pdfAvailable", "details.pdfNone", "details.pdfDownload", "details.pdfAttach",
            "details.pdfReplace", "details.pdfRemove", "details.pdfCancel", "details.pdfProgress", "details.pdfDownloaded",
            "details.pdfAttached", "details.pdfFailed", "details.pdfOffline", "details.pdfNotPdf", "details.pdfTooLarge",
            "details.pdfHttp", "details.pdfTryAgain", "details.pdfOpenBrowser", "details.pdfOpenLink",
            "details.pdfReplaceTitle", "details.pdfRemoveTitle", "details.pdfAttachNotPdf", "details.pdfAttachFailed",
            "details.pdfRead",
        ]
        for key in keys {
            #expect(inLanguage("en") { L10n.string(key) } != key)
            #expect(inLanguage("ar") { L10n.string(key) } != inLanguage("en") { L10n.string(key) })
        }
        // The row is labelled PDF, so its state line doesn't repeat it.
        #expect(inLanguage("en") { L10n.format("details.pdfDownloaded", "2.4 MB") } == "2.4 MB · Downloaded")
        // Arabic wraps the argument in bidi isolates (U+2068 … U+2069), as every Arabic format does.
        #expect(inLanguage("ar") { L10n.format("details.pdfDownloaded", PaperFormat.fileSize(2_400_000)) } == "\u{2068}2.4 م.ب\u{2069} · مُنزَّل")
        #expect(inLanguage("en") { L10n.format("details.pdfProgress", "1 MB", "4 MB") } == "1 MB of 4 MB")
        #expect(inLanguage("en") { L10n.string("details.pdfRead") } == "Read PDF")
        #expect(inLanguage("ar") { L10n.string("details.pdfRead") } == "قراءة ملف PDF")
        // "DOI" stays in Latin letters in Arabic too.
        #expect(inLanguage("ar") { L10n.string("details.doi") } == "DOI")
        #expect(inLanguage("ar") { L10n.string("details.pdfNotPdf") } == "يفتح هذا الرابط صفحة ويب وليس ملف PDF.")
        #expect(inLanguage("en") { L10n.string("details.openPDF") } == "details.openPDF")
    }
}
