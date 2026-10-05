import FeaturePaperDetails
import FeatureReader
import HashiyaDesignSystem
import HashiyaDiagnostics
import SwiftUI

/// iPad: a saved paper's Details in a window of its own, with its reader above it. The window closes when the paper
/// stops being saved or is removed here.
struct PaperWindow: View {
    static let id = "paper"

    /// The window's paper; iPadOS keeps it to restore the window, and brings forward a window that already shows it.
    struct Value: Hashable, Codable {
        var openAlexID: String
        /// Empty, except in UI tests: an ID per launch, so a window iPadOS restored from an earlier test is never
        /// taken for one this test opens.
        var launch = Value.currentLaunch

        #if DEBUG
        static let currentLaunch = UITestingFlags.stubsEnabled ? UUID().uuidString : ""
        #else
        static let currentLaunch = ""
        #endif
    }

    private let container: AppContainer
    private let route: PaperDetailsRoute
    private let isFromEarlierTest: Bool
    /// The reader, above Details.
    @State private var path: [AppRoute] = []
    @Environment(\.dismissWindow) private var dismissWindow

    init(container: AppContainer, value: Value) {
        self.container = container
        route = PaperDetailsRoute(openAlexID: value.openAlexID)
        isFromEarlierTest = value.launch != Value.currentLaunch
    }

    var body: some View {
        // UI tests: a paper window left from an earlier test shows the main screen, so each test starts at the Library.
        if isFromEarlierTest {
            RootView(container: container)
        } else {
            paper
        }
    }

    private var paper: some View {
        NavigationStack(path: $path) {
            PaperDetailsScreen(
                viewModel: container.makePaperDetailsViewModel(openAlexID: route.openAlexID),
                onClose: { dismissWindow() },
                onRemove: { id in
                    dismissWindow()
                    Task {
                        if (try? await container.libraryRepository.remove(openAlexID: id)) != nil {
                            container.diagnostics.analytics.log(.paperRemoved)
                        }
                    }
                },
                onReadPdf: { id in path.append(.reader(ReaderRoute(openAlexID: id))) }
            )
            .navigationDestination(for: AppRoute.self) { route in
                if case .reader(let reader) = route {
                    ReaderScreen(
                        viewModel: container.makeReaderViewModel(openAlexID: reader.openAlexID),
                        onClose: { path.removeAll() }
                    )
                }
            }
        }
        .tint(HashiyaColors.primary)
        .measuresLayoutClass()
        .environment(\.diagnostics, container.diagnostics)
    }
}
