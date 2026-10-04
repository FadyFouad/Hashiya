import FeaturePaperDetails
import FeatureSettings
import HashiyaData
import HashiyaDesignSystem
import SwiftUI

@main
struct HashiyaApp: App {
    /// Nil while the app hosts `HashiyaSnapshotTests`: then nothing opens the library or starts a task.
    @State private var container: AppContainer?
    /// The whole app's phase (iPad windows: active while any window is, background once all are).
    @Environment(\.scenePhase) private var scenePhase
    /// Bumped on every phase change, so a background suspend that waited for writes is dropped once the app is active again.
    @State private var phaseGeneration = 0

    init() {
        HashiyaFonts.register()
        HashiyaFonts.applyNavigationBarFonts()
        _container = State(initialValue: Self.isSnapshotTestHost ? nil : AppContainer.make())
        // Backups another app handed over that an earlier run never got to restore.
        if !Self.isSnapshotTestHost {
            Task.detached(priority: .utility) {
                OpenedBackup.removeStaleInboxFiles()
            }
        }
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
        .commands { AppCommands() }
        .onChange(of: scenePhase, initial: true) { _, phase in phaseChanged(phase) }

        // iPad: one saved paper in its own window ("Open in New Window"); iPadOS restores it with its paper.
        WindowGroup(id: PaperWindow.id, for: PaperWindow.Value.self) { $value in
            if let container {
                if let value {
                    PaperWindow(container: container, value: value)
                } else {
                    // No paper (a saved value an older version wrote): the main screen rather than an empty window.
                    RootView(container: container)
                }
            }
        }
    }

    /// The shared library database follows the app, not a window: with two windows open, one going to the background
    /// must not suspend it under the other.
    private func phaseChanged(_ phase: ScenePhase) {
        guard let container else { return }
        phaseGeneration += 1
        switch phase {
        case .active:
            // Papers saved in the Share Extension appear in the Library and as "In library".
            SharedLibraryDatabase.resume()
            Task { await container.libraryRepository.refreshAfterExternalChanges() }
        case .background:
            // A suspended database refuses writes: let the notes Details just flushed land first, and let a PDF
            // download finish on its background time (iOS ends that time, which cancels it, if it runs too long).
            let generation = phaseGeneration
            Task {
                await container.pendingWrites.drained()
                await container.pdfRepository.storesFinished()
                guard phaseGeneration == generation else { return }
                SharedLibraryDatabase.suspend()
            }
        default:
            break
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
