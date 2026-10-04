import SwiftUI

/// What the menu bar and its keyboard shortcuts do in the window in front (iPad with a keyboard; on iPhone a hardware
/// keyboard's shortcuts).
struct AppCommandActions {
    var showLibrary: () -> Void
    var showSearch: () -> Void
    var addPaper: () -> Void
    var openSettings: () -> Void
}

extension FocusedValues {
    @Entry var appCommands: AppCommandActions?
}

/// Add Paper (⌘N), Settings (⌘,), and the tabs (⌘1, ⌘2). Disabled while no window offers them (the update-required
/// screen). ⌘F stays the system's Find: in the reader it searches the PDF.
struct AppCommands: Commands {
    @FocusedValue(\.appCommands) private var actions

    var body: some Commands {
        CommandGroup(after: .newItem) {
            item("menu.addPaper", key: "n") { $0.addPaper() }
        }
        CommandGroup(replacing: .appSettings) {
            item("menu.settings", key: ",") { $0.openSettings() }
        }
        CommandGroup(before: .toolbar) {
            item("nav.library", key: "1") { $0.showLibrary() }
            item("nav.search", key: "2") { $0.showSearch() }
        }
    }

    private func item(_ titleKey: String, key shortcut: KeyEquivalent, perform: @escaping (AppCommandActions) -> Void) -> some View {
        Button {
            if let actions { perform(actions) }
        } label: {
            Text(verbatim: AppStrings.string(titleKey))
        }
        .keyboardShortcut(shortcut)
        .disabled(actions == nil)
    }
}
