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
            fields
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) { closeButton }
                }
        }
    }

    /// The fields under "My notes", scrolling.
    var fields: some View {
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
    }

    var closeButton: some View {
        Button(action: onClose) {
            Image(systemName: "xmark")
        }
        .accessibilityLabel(Text(verbatim: L10n.string("reader.closeNotes")))
        .accessibilityIdentifier("reader.closeNotes")
    }
}

/// The notes beside the PDF on a regular-width window (iPad): the same fields as the sheet, in a column with its own
/// close button. Not an inspector, and not a navigation stack of its own: inside the split view's detail stack either
/// one pops the reader as soon as it is pushed.
struct ReaderNotesPanel: View {
    let sheet: ReaderNotesSheet

    static let width: CGFloat = 380

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                sheet.closeButton
                    .font(.title3)
                    .frame(minWidth: 44, minHeight: 44)
            }
            .padding(.horizontal, 8)
            sheet.fields
        }
        .frame(width: Self.width)
        .background(HashiyaColors.surface)
    }
}
