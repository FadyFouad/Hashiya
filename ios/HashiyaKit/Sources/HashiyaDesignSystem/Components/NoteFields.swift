import HashiyaModel
import SwiftUI

/// Note strings shared by Details and the reader's Notes sheet.
@MainActor
enum NoteStrings {
    /// The section's name: "Summary", "Research question", ….
    static func label(_ section: NoteSection) -> String {
        L10n.string("note.\(section.key)")
    }

    /// The section's prompt: "What is this paper about, in your own words?", ….
    static func hint(_ section: NoteSection) -> String {
        L10n.string("note.\(section.key)Hint")
    }

    /// The line beside "My notes": nothing, "Saving…", "Saved" or "Couldn't save".
    static func saveStatus(_ state: NotesSaveState) -> String? {
        switch state {
        case .idle: nil
        case .saving: L10n.string("notes.saving")
        case .saved: L10n.string("notes.saved")
        case .failed: L10n.string("notes.saveFailed")
        }
    }
}

extension NoteSection {
    /// The stem of the section's catalog keys (`note.<key>`, `note.<key>Hint`) and of its field's accessibility
    /// identifier (`note.<key>`, which the UI tests use).
    var key: String {
        switch self {
        case .summary: "summary"
        case .researchQuestion: "researchQuestion"
        case .method: "method"
        case .keyFindings: "keyFindings"
        case .limitations: "limitations"
        case .thoughts: "thoughts"
        }
    }
}

/// "My notes" and the save status beside it.
public struct NotesHeading: View {
    private let saveState: NotesSaveState

    public init(saveState: NotesSaveState) {
        self.saveState = saveState
    }

    public var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(verbatim: L10n.string("notes.title"))
                .font(.hashiya(.stateTitle))
                .foregroundStyle(HashiyaColors.onSurface)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            if let status = NoteStrings.saveStatus(saveState) {
                Text(verbatim: status)
                    .font(.hashiya(.meta))
                    .foregroundStyle(saveState == .failed ? HashiyaColors.error : HashiyaColors.onSurfaceVariant)
                    .accessibilityIdentifier("details.saveStatus")
            }
        }
    }
}

/// The six note fields in order, with a keyboard Done button (shown inside a `NavigationStack`). Each field seeds its
/// text once from `notes`; a new `version` (a reload replaced the notes on screen) seeds them all again.
public struct NoteFields: View {
    private let notes: PaperNotes
    private let version: Int
    private let onChange: (NoteSection, String) -> Void

    @FocusState private var focusedSection: NoteSection?

    public init(notes: PaperNotes, version: Int, onChange: @escaping (NoteSection, String) -> Void) {
        self.notes = notes
        self.version = version
        self.onChange = onChange
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            ForEach(NoteSection.allCases, id: \.self) { section in
                NoteField(section: section, initialText: notes[section], focus: $focusedSection) { text in
                    onChange(section, text)
                }
            }
        }
        .id(version)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button {
                    focusedSection = nil
                } label: {
                    Text(verbatim: DesignSystemStrings.doneEditing)
                }
            }
        }
    }
}

/// One note section: its name above a growing text field. The field owns what is on screen: its text is seeded once
/// and then only reported back, so typing (and marked text being composed) never waits for the owner's state to come
/// back.
struct NoteField: View {
    let section: NoteSection
    let focus: FocusState<NoteSection?>.Binding
    let onChange: (String) -> Void

    @State private var text: String
    @Environment(\.layoutDirection) private var uiDirection

    init(section: NoteSection, initialText: String, focus: FocusState<NoteSection?>.Binding, onChange: @escaping (String) -> Void) {
        self.section = section
        self.focus = focus
        self.onChange = onChange
        _text = State(initialValue: initialText)
    }

    var body: some View {
        let isFocused = focus.wrappedValue == section
        let label = NoteStrings.label(section)
        VStack(alignment: .leading, spacing: 6) {
            Text(verbatim: label)
                .font(.hashiya(.label))
                .foregroundStyle(isFocused ? HashiyaColors.primary : HashiyaColors.onSurfaceVariant)
                .frame(maxWidth: .infinity, alignment: .leading)
                // The field carries the same label for VoiceOver.
                .accessibilityHidden(true)
            TextField(
                text: $text,
                prompt: Text(verbatim: NoteStrings.hint(section)).foregroundStyle(HashiyaColors.onSurfaceVariant),
                axis: .vertical
            ) {
                Text(verbatim: label)
            }
            .font(.hashiya(.body))
            .foregroundStyle(HashiyaColors.onSurface)
            .lineLimit(2...)
            .textInputAutocapitalization(.sentences)
            .multilineTextAlignment(.leading)
            // An Arabic note lays out right to left in an English UI, and an English note left to right in an Arabic one.
            .environment(\.layoutDirection, ContentDirection.of(text) ?? uiDirection)
            .focused(focus, equals: section)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(isFocused ? HashiyaColors.primary : HashiyaColors.outline, lineWidth: isFocused ? 2 : 1)
            )
            .accessibilityIdentifier("note.\(section.key)")
        }
        .onChange(of: text) { _, newText in onChange(newText) }
    }
}
