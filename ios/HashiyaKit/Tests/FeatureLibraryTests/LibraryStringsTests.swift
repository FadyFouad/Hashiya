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
        #expect(inLanguage("en") { L10n.rowMeta(SamplePapers.attention) } == "\u{2068}Ashish Vaswani et al.\u{2069} · 2017 · Neural Information Processing Systems")
        #expect(inLanguage("en") { L10n.rowMeta(SamplePapers.arabicTitled) } == "محمد علي · 2022")
        #expect(inLanguage("en") { L10n.rowMeta(SamplePapers.untitled) } == "")
        // The "et al." part is isolated, so the year after it never joins its Arabic run.
        #expect(inLanguage("ar") { L10n.rowMeta(SamplePapers.attention) } == "\u{2068}\u{2068}Ashish Vaswani\u{2069} وآخرون\u{2069} · 2017 · Neural Information Processing Systems")
    }

    /// "·" beside Arabic digits reads like "٠", so Arabic puts the count in parentheses; Arabic formatting also isolates
    /// each argument (U+2068 … U+2069).
    @Test func chipsShowTheirCount() {
        #expect(inLanguage("en") { L10n.filterCount(readingStatusLabel(.reading), 3) } == "Reading · 3")
        #expect(inLanguage("en") { L10n.filterCount(L10n.string("library.filterAll"), 1_234) } == "All · 1,234")
        #expect(inLanguage("ar") { L10n.filterCount(readingStatusLabel(.reading), 3) } == "\u{2068}قيد القراءة\u{2069} (\u{2068}3\u{2069})")
    }

    @Test func theBadgeDescribesTheStatusAndTheAction() {
        #expect(inLanguage("en") { L10n.statusBadgeDescription(.toRead) } == "Status: To read. Change status")
        #expect(inLanguage("ar") { L10n.statusBadgeDescription(.read) } == "الحالة: \u{2068}مقروءة\u{2069}. تغيير الحالة")
    }

    /// Collection names are isolated (FSI…PDI) so an Arabic name in the English UI (or the reverse) keeps its place;
    /// Arabic formatting isolates its arguments itself.
    @Test func collectionStringsIsolateTheName() {
        #expect(inLanguage("en") { L10n.removedFromCollection("Thesis") } == "Removed from \u{2068}Thesis\u{2069}")
        #expect(inLanguage("ar") { L10n.removedFromCollection("Thesis") } == "أُزيلت من \u{2068}Thesis\u{2069}")
        #expect(inLanguage("en") { L10n.renameCollection("Thesis") } == "Rename \"\u{2068}Thesis\u{2069}\"")
        #expect(inLanguage("ar") { L10n.renameCollection("Thesis") } == "إعادة تسمية «\u{2068}Thesis\u{2069}»")
        #expect(inLanguage("en") { L10n.deleteCollection("Thesis") } == "Delete \"\u{2068}Thesis\u{2069}\"…")
        #expect(inLanguage("ar") { L10n.deleteCollection("Thesis") } == "حذف «\u{2068}Thesis\u{2069}»…")
        #expect(inLanguage("en") { L10n.deleteCollectionTitle("Thesis") } == "Delete \"\u{2068}Thesis\u{2069}\"?")
        #expect(inLanguage("ar") { L10n.deleteCollectionTitle("Thesis") } == "حذف «\u{2068}Thesis\u{2069}»؟")
    }

    @Test func theNewStringsHaveBothLanguages() {
        let english = [
            "library.allPapers": "All papers",
            "library.newCollection": "New collection",
            "library.delete": "Delete",
            "library.deleteCollectionMessage": "Its papers stay in your library.",
            "library.collectionEmpty": "No papers in this collection yet. Add papers from their details screen.",
            "library.removeFromCollection": "Remove from collection",
            "library.exportBib": "Export .bib",
            "library.exportFailed": "Couldn't export",
            "library.exportIncomplete": "Some entries may be incomplete. Export again when you're online.",
            "library.collectionsUpdateFailed": "Couldn't update collections",
        ]
        let arabic = [
            "library.allPapers": "كل الأوراق",
            "library.newCollection": "مجموعة جديدة",
            "library.delete": "حذف",
            "library.deleteCollectionMessage": "ستبقى أوراقها في مكتبتك.",
            "library.collectionEmpty": "لا توجد أوراق في هذه المجموعة بعد. أضف الأوراق من شاشة تفاصيلها.",
            "library.removeFromCollection": "إزالة من المجموعة",
            "library.exportBib": "تصدير ملف \u{200E}.bib",
            "library.exportFailed": "تعذّر التصدير",
            "library.exportIncomplete": "قد تكون بعض المداخل ناقصة. أعد التصدير عند الاتصال بالإنترنت.",
            "library.collectionsUpdateFailed": "تعذّر تحديث المجموعات",
        ]
        for (key, value) in english {
            #expect(inLanguage("en") { L10n.string(key) } == value)
        }
        for (key, value) in arabic {
            #expect(inLanguage("ar") { L10n.string(key) } == value)
        }
    }
}
