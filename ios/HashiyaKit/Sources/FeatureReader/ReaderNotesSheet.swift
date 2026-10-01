import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// The paper's notes over the PDF: "My notes" with the save status, the same fields as Details, and a close button.
/// Present it with medium and large detents; the reader's `NotesEditor` autosaves what is typed. The fields seed once
/// per opening of the sheet: the reader never reloads its notes.
struct ReaderNotesSheet: View {
    /// Nil while the notes are being read.
    let notes: PaperNotes?
    let saveState: NotesSaveState
    let onChange: (NoteSection, String) -> Void
    let onClose: () -> Void

    var body: some View {
        // The stack gives the close button a bar, and the fields their keyboard Done.
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    NotesHeading(saveState: saveState)
                    if let notes {
                        NoteFields(notes: notes, version: 0, onChange: onChange)
                    } else {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(HashiyaColors.surface)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                    }
                    .accessibilityLabel(Text(verbatim: L10n.string("reader.closeNotes")))
                    .accessibilityIdentifier("reader.closeNotes")
                }
            }
        }
    }
}
