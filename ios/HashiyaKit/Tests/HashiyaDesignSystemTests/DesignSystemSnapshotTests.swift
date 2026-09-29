import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import SwiftUI
import Testing

@MainActor
@Suite(.serialized)
struct DesignSystemSnapshotTests {
    @Test func paperCards() {
        let cards = ScrollView {
            VStack(spacing: 0) {
                PaperCard(paper: SamplePapers.attention, inLibrary: true, onOpen: {}, onSave: {})
                PaperCard(paper: SamplePapers.bert, inLibrary: false, onOpen: {}, onSave: {})
                PaperCard(paper: SamplePapers.arabicTitled, inLibrary: false, onOpen: {}, onSave: {})
                PaperCard(paper: SamplePapers.untitled, inLibrary: false, onOpen: {}, onSave: {})
            }
        }
        .background(HashiyaColors.surface)
        assertHashiyaSnapshots(of: cards, named: "cards", arabicText: "في المكتبة")
    }

    @Test func previewOpenAccessWithPDF() {
        let preview = PaperPreviewContent(paper: SamplePapers.attention, inLibrary: false, onToggleSave: {}, onOpenDOI: { _ in })
        assertHashiyaSnapshots(of: preview, named: "previewOpenAccessPDF", arabicText: "وصول مفتوح · ملف PDF متاح")
    }

    @Test func previewWithoutAbstractInLibrary() {
        let preview = PaperPreviewContent(paper: SamplePapers.vit, inLibrary: true, onToggleSave: {}, onOpenDOI: { _ in })
        assertHashiyaSnapshots(of: preview, named: "previewNoAbstract", arabicText: "لا يوجد ملخص")
    }

    @Test func previewWithoutDOI() {
        let preview = PaperPreviewContent(paper: SamplePapers.arabicTitled, inLibrary: false, onToggleSave: {}, onOpenDOI: { _ in })
        assertHashiyaSnapshots(of: preview, named: "previewNoDOI", arabicText: "حفظ في المكتبة")
    }

    @Test func previewWithTheStatusSelector() {
        let preview = PaperPreviewContent(
            paper: SamplePapers.arabicTitled,
            inLibrary: true,
            status: .reading,
            onToggleSave: {},
            onOpenDOI: { _ in }
        )
        assertHashiyaSnapshots(of: preview, named: "previewStatus", arabicText: "قيد القراءة")
    }

    @Test func messageStatesAndBanner() {
        let states = VStack(spacing: 0) {
            EmptyStateView(icon: "books.vertical", title: "Empty", message: "Message", actionTitle: "Action", action: {})
            LoadingSkeleton(rows: 2)
            HashiyaBanner(text: "Banner", actionTitle: "Undo", action: {})
            PaperCard(paper: SamplePapers.untitled, inLibrary: true, onOpen: {}, onSave: {})
        }
        .background(HashiyaColors.surface)
        assertHashiyaSnapshots(of: states, named: "components", arabicText: "بدون عنوان")
    }

    @Test func updateRequired() {
        assertHashiyaSnapshots(of: UpdateRequiredView(onUpdate: {}), named: "updateRequired", arabicText: "يلزم التحديث")
    }
}
