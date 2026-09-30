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
        notes: PaperNotes? = PaperNotes(),
        saveState: NotesSaveState = .idle,
        message: PaperDetailsMessage? = nil
    ) -> some View {
        NavigationStack {
            PaperDetailsContent(
                paper: LibraryPaper(paper: paper, status: status),
                notes: notes,
                saveState: saveState,
                message: message,
                actions: PaperDetailsActions()
            )
        }
    }

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
}
