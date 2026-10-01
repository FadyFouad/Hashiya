@testable import FeaturePaperDetails
import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import SwiftUI
import Testing

@MainActor
@Suite(.serialized)
struct PaperDetailsSnapshotTests {
    private func screen(
        _ paper: Paper,
        status: ReadingStatus = .toRead,
        collections: [PaperCollection] = [],
        memberIDs: Set<Int64> = [],
        notes: PaperNotes? = PaperNotes(),
        saveState: NotesSaveState = .idle,
        message: PaperDetailsMessage? = nil
    ) -> some View {
        NavigationStack {
            PaperDetailsContent(
                paper: LibraryPaper(paper: paper, status: status),
                collections: collections,
                memberIDs: memberIDs,
                notes: notes,
                saveState: saveState,
                message: message,
                actions: PaperDetailsActions()
            )
        }
    }

    /// Four collections, three holding the paper: the chips wrap, and an Arabic name sits beside English ones.
    private let sampleCollections = [
        PaperCollection(id: 1, name: "Thesis, chapter 2", paperCount: 4),
        PaperCollection(id: 2, name: "NLP reading group", paperCount: 7),
        PaperCollection(id: 3, name: "مراجعة الأدبيات", paperCount: 2),
        PaperCollection(id: 4, name: "Not this one", paperCount: 1),
    ]

    /// The header, status, both links and the abstract.
    @Test func paper() {
        assertHashiyaSnapshots(of: screen(SamplePapers.attention, status: .reading), named: "paper", arabicText: "فتح ملف PDF")
    }

    /// Filled notes on a paper with no abstract, so the fields are on screen: an Arabic note in an English UI and an
    /// English note in an Arabic UI each lay out in their own direction.
    @Test func notesFilled() {
        let notes = PaperNotes(
            summary: "Vision transformers match CNNs when pre-trained on enough data.",
            researchQuestion: "هل تكفي المحوّلات وحدها لتصنيف الصور؟",
            method: "Patches of 16×16 pixels as tokens."
        )
        assertHashiyaSnapshots(of: screen(SamplePapers.vit, notes: notes, saveState: .saved), named: "notes", arabicText: "تم الحفظ")
    }

    /// No title, no authors, no abstract and no notes.
    @Test func untitledWithoutNotes() {
        assertHashiyaSnapshots(of: screen(SamplePapers.untitled), named: "untitled", arabicText: "عمّ تتحدث هذه الورقة، بكلماتك أنت؟")
    }

    @Test func couldNotSave() {
        let view = screen(SamplePapers.vit, notes: PaperNotes(summary: "Unsaved text"), saveState: .failed, message: .notesSaveFailed)
        assertHashiyaSnapshots(of: view, named: "saveFailed", arabicText: "تعذّر حفظ ملاحظاتك")
    }

    @Test func couldNotLoadNotes() {
        assertHashiyaSnapshots(of: screen(SamplePapers.vit, notes: nil), named: "loadFailed", arabicText: "تعذّر تحميل ملاحظاتك")
    }

    /// The Collections row naming three collections. The "paper" state above shows the row with none.
    @Test func collectionsRow() {
        let view = screen(SamplePapers.attention, status: .reading, collections: sampleCollections, memberIDs: [1, 2, 3])
        assertHashiyaSnapshots(of: view, named: "collections", arabicText: "المجموعات")
    }

    @Test func checklist() {
        let view = CollectionsChecklist(collections: sampleCollections, memberIDs: [1, 3], message: nil, actions: CollectionsChecklistActions())
        assertHashiyaSnapshots(of: view, named: "checklist", arabicText: "مجموعة جديدة")
    }

    @Test func checklistWithoutCollections() {
        let view = CollectionsChecklist(collections: [], memberIDs: [], message: nil, actions: CollectionsChecklistActions())
        assertHashiyaSnapshots(of: view, named: "checklistEmpty", arabicText: "اجمع الأوراق لفصل أو مقرر أو مشروع.")
    }
}
