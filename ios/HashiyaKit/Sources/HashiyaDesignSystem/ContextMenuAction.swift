import Foundation

/// Context menu actions that open a screen.
public enum ContextMenuAction {
    /// How long a context menu takes to close.
    static let closing: Duration = .milliseconds(350)

    /// Runs `action` once the menu has closed. On iPadOS 26.2, changing the selection and pushing Details while the menu
    /// was still closing (its source row re-rendering under it) left the app busy for a minute.
    @MainActor
    public static func afterClosing(_ action: @escaping @MainActor () -> Void) {
        Task { @MainActor in
            try? await Task.sleep(for: closing)
            action()
        }
    }
}
