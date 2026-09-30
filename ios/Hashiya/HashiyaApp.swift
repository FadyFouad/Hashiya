import HashiyaDesignSystem
import SwiftUI

@main
struct HashiyaApp: App {
    /// Nil while the app hosts `HashiyaSnapshotTests`: then nothing opens the library or starts a task.
    @State private var container: AppContainer?

    init() {
        HashiyaFonts.register()
        HashiyaFonts.applyNavigationBarFonts()
        _container = State(initialValue: Self.isSnapshotTestHost ? nil : AppContainer.make())
    }

    var body: some Scene {
        WindowGroup {
            if let container {
                RootView(container: container)
            } else {
                // The snapshot tests draw into this window.
                Color.clear
            }
        }
    }

    /// True while the app hosts `HashiyaSnapshotTests` (Debug only): XCTest sets this variable in its host's
    /// environment. UI tests launch the app without it.
    private static var isSnapshotTestHost: Bool {
        #if DEBUG
        ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
        #else
        false
        #endif
    }
}
