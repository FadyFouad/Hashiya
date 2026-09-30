import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// One note section: its name above a growing text field. The field owns what is on screen: its text is seeded once
/// from the view model and then only reported back, so typing (and marked text being composed) never waits for the
/// view model's state to come back. The view model reads the stored notes once, so nothing else changes the text.
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
        let label = L10n.noteLabel(section)
        VStack(alignment: .leading, spacing: 6) {
            Text(verbatim: label)
                .font(.hashiya(.label))
                .foregroundStyle(isFocused ? HashiyaColors.primary : HashiyaColors.onSurfaceVariant)
                .frame(maxWidth: .infinity, alignment: .leading)
                // The field carries the same label for VoiceOver.
                .accessibilityHidden(true)
            TextField(
                text: $text,
                prompt: Text(verbatim: L10n.noteHint(section)).foregroundStyle(HashiyaColors.onSurfaceVariant),
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
