import HashiyaModel
import SwiftUI

/// Names a new collection or renames one: one field, Cancel, and Create or Save. Present it as a sheet's content.
///
/// It doesn't validate against other collections: the caller submits, and passes "A collection with that name
/// already exists" back as `error` on a clash. The error stays under the field until the name is edited. A caller that
/// gets the same clash again clears `error` before submitting, so the error shows again.
public struct CollectionNameSheet: View {
    public enum Mode: Sendable, Equatable {
        case create, rename
    }

    private let mode: Mode
    private let error: String?
    private let onSubmit: (String) -> Void
    private let onCancel: () -> Void

    /// Seeded once from `initialName`; the sheet owns the typed text.
    @State private var name: String
    /// False once the name is edited after `error` appeared.
    @State private var showsError = true
    @FocusState private var isFocused: Bool
    @Environment(\.layoutDirection) private var uiDirection

    public init(
        mode: Mode,
        initialName: String = "",
        error: String?,
        onSubmit: @escaping (String) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.mode = mode
        self.error = error
        self.onSubmit = onSubmit
        self.onCancel = onCancel
        _name = State(initialValue: initialName)
    }

    /// Create or Save is enabled only for a valid name: 1–60 characters after trimming.
    public static func canSubmit(_ name: String) -> Bool {
        isValidCollectionName(name)
    }

    public var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 8) {
                TextField(text: $name) {
                    Text(verbatim: L10n.string("collection.nameLabel"))
                }
                .font(.hashiya(.body))
                .textFieldStyle(.roundedBorder)
                // A collection named in Arabic in the English UI (or the reverse) is typed in its own direction.
                .environment(\.layoutDirection, ContentDirection.of(name) ?? uiDirection)
                .focused($isFocused)
                .submitLabel(.done)
                .onSubmit(submit)
                .accessibilityIdentifier("collection.name")
                if showsError, let error {
                    Text(verbatim: error)
                        .font(.hashiya(.meta))
                        .foregroundStyle(HashiyaColors.error)
                        .accessibilityIdentifier("collection.nameError")
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(HashiyaColors.surface)
            .navigationTitle(Text(verbatim: L10n.string(mode == .create ? "collection.newTitle" : "collection.renameTitle")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(action: onCancel) {
                        Text(verbatim: L10n.string("collection.cancel"))
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: submit) {
                        Text(verbatim: L10n.string(mode == .create ? "collection.create" : "collection.save"))
                    }
                    .disabled(!Self.canSubmit(name))
                }
            }
        }
        // Tall enough for the bar, the field and a one-line error at the default text size; .medium for larger sizes.
        .presentationDetents([.height(200), .medium])
        .onAppear { isFocused = true }
        .onChange(of: name) { showsError = false }
        .onChange(of: error) { showsError = true }
    }

    private func submit() {
        guard Self.canSubmit(name) else { return }
        onSubmit(name)
    }
}
