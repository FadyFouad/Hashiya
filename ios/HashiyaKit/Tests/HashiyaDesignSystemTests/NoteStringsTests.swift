@testable import HashiyaDesignSystem
import HashiyaModel
import Testing

@MainActor
struct NoteStringsTests {
    private func inLanguage<T>(_ language: String, _ body: () -> T) -> T {
        let previous = HashiyaLanguage.override
        HashiyaLanguage.override = language
        defer { HashiyaLanguage.override = previous }
        return body()
    }

    @Test func sectionsHaveTheirLabelsInOrder() {
        #expect(inLanguage("en") { NoteSection.allCases.map(NoteStrings.label) }
            == ["Summary", "Research question", "Method", "Key findings", "Limitations", "My thoughts"])
        #expect(inLanguage("ar") { NoteSection.allCases.map(NoteStrings.label) }
            == ["الخلاصة", "سؤال البحث", "المنهجية", "أهم النتائج", "القيود", "أفكاري"])
    }

    @Test func everySectionHasAHint() {
        #expect(inLanguage("en") { NoteStrings.hint(.summary) } == "What is this paper about, in your own words?")
        #expect(inLanguage("ar") { NoteStrings.hint(.thoughts) } == "ما علاقتها ببحثك؟")
        for section in NoteSection.allCases {
            #expect(inLanguage("en") { NoteStrings.hint(section) } != "note.\(section.key)Hint")
        }
    }

    @Test func theSaveStatusLineFollowsTheState() {
        #expect(inLanguage("en") { NoteStrings.saveStatus(.idle) } == nil)
        #expect(inLanguage("en") { NoteStrings.saveStatus(.saving) } == "Saving…")
        #expect(inLanguage("en") { NoteStrings.saveStatus(.saved) } == "Saved")
        #expect(inLanguage("en") { NoteStrings.saveStatus(.failed) } == "Couldn't save")
        #expect(inLanguage("ar") { NoteStrings.saveStatus(.saved) } == "تم الحفظ")
    }

    @Test func theHeadingAndDoneResolveInBothLanguages() {
        #expect(inLanguage("en") { L10n.string("notes.title") } == "My notes")
        #expect(inLanguage("ar") { L10n.string("notes.title") } == "ملاحظاتي")
        #expect(inLanguage("en") { L10n.string("notes.doneEditing") } == "Done")
        #expect(inLanguage("ar") { L10n.string("notes.doneEditing") } == "تم")
        // The collections checklist's Done shows the same text.
        #expect(inLanguage("en") { DesignSystemStrings.doneEditing } == "Done")
        #expect(inLanguage("ar") { DesignSystemStrings.doneEditing } == "تم")
    }
}
