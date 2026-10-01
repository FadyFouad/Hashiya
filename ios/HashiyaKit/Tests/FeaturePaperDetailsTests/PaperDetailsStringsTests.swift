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

    @Test func sectionsHaveTheirLabelsInOrder() {
        #expect(inLanguage("en") { NoteSection.allCases.map(L10n.noteLabel) }
            == ["Summary", "Research question", "Method", "Key findings", "Limitations", "My thoughts"])
        #expect(inLanguage("ar") { NoteSection.allCases.map(L10n.noteLabel) }
            == ["الخلاصة", "سؤال البحث", "المنهجية", "أهم النتائج", "القيود", "أفكاري"])
    }

    @Test func everySectionHasAHint() {
        #expect(inLanguage("en") { L10n.noteHint(.summary) } == "What is this paper about, in your own words?")
        #expect(inLanguage("ar") { L10n.noteHint(.thoughts) } == "ما علاقتها ببحثك؟")
        for section in NoteSection.allCases {
            #expect(inLanguage("en") { L10n.noteHint(section) } != "note.\(section.key)Hint")
        }
    }

    @Test func theSaveStatusLineFollowsTheState() {
        #expect(inLanguage("en") { L10n.saveStatus(.idle) } == nil)
        #expect(inLanguage("en") { L10n.saveStatus(.saving) } == "Saving…")
        #expect(inLanguage("en") { L10n.saveStatus(.saved) } == "Saved")
        #expect(inLanguage("en") { L10n.saveStatus(.failed) } == "Couldn't save")
        #expect(inLanguage("ar") { L10n.saveStatus(.saved) } == "تم الحفظ")
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
