import FeatureLibrary
import FeaturePaperDetails
import FeatureSearch
import FeatureSettings
import HashiyaData
import HashiyaDesignSystem
import SwiftUI

/// Library and Search tabs, each in its own navigation stack that can push a saved paper's Details; Settings as a sheet from either.
struct RootView: View {
    enum Tab: Hashable { case library, search }

    private let container: AppContainer
    @State private var selectedTab = Tab.library
    @State private var showsSettings = false
    @State private var libraryPath: [PaperDetailsRoute] = []
    @State private var searchPath: [PaperDetailsRoute] = []
    /// Bumped on every scene phase change, so a background suspend that waited for writes is dropped once the app is active again.
    @State private var phaseGeneration = 0
    @State private var libraryViewModel: LibraryViewModel
    @State private var searchViewModel: SearchViewModel
    @State private var appUpdate: AppUpdateModel
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    init(container: AppContainer) {
        self.container = container
        _libraryViewModel = State(initialValue: container.makeLibraryViewModel())
        _searchViewModel = State(initialValue: container.makeSearchViewModel())
        _appUpdate = State(initialValue: container.makeAppUpdateModel())
    }

    var body: some View {
        Group {
            if appUpdate.requiredUpdate != nil {
                UpdateRequiredView(onUpdate: openStore)
            } else {
                tabs
            }
        }
        .onChange(of: scenePhase, initial: true) { _, phase in
            phaseGeneration += 1
            switch phase {
            case .active:
                // Papers saved in the Share Extension appear in the Library and as "In library".
                SharedLibraryDatabase.resume()
                Task { await container.libraryRepository.refreshAfterExternalChanges() }
                Task { await appUpdate.check() }
            case .background:
                // A suspended database refuses writes: let the notes Details just flushed land first.
                let generation = phaseGeneration
                Task {
                    await container.pendingWrites.drained()
                    guard phaseGeneration == generation else { return }
                    SharedLibraryDatabase.suspend()
                }
            default:
                break
            }
        }
    }

    private func openStore() {
        if let url = appUpdate.requiredUpdate?.storeURL { openURL(url) }
    }

    private var tabs: some View {
        TabView(selection: $selectedTab) {
            NavigationStack(path: $libraryPath) {
                LibraryView(
                    viewModel: libraryViewModel,
                    onGoToSearch: { selectedTab = .search },
                    onAddPaper: {
                        searchViewModel.startFresh(focus: true)
                        selectedTab = .search
                    },
                    onOpenSettings: { showsSettings = true },
                    onOpenPaper: { libraryPath.append(PaperDetailsRoute(openAlexID: $0)) }
                )
                .navigationDestination(for: PaperDetailsRoute.self) { route in
                    details(route, in: .library)
                }
            }
            .tabItem {
                Label {
                    Text(verbatim: AppStrings.string("nav.library"))
                } icon: {
                    Image(systemName: "books.vertical")
                }
            }
            .tag(Tab.library)

            NavigationStack(path: $searchPath) {
                SearchView(
                    viewModel: searchViewModel,
                    onOpenSettings: { showsSettings = true },
                    onOpenPaper: { searchPath.append(PaperDetailsRoute(openAlexID: $0)) }
                )
                .navigationDestination(for: PaperDetailsRoute.self) { route in
                    details(route, in: .search)
                }
            }
            .tabItem {
                Label {
                    Text(verbatim: AppStrings.string("nav.search"))
                } icon: {
                    Image(systemName: "magnifyingglass")
                }
            }
            .tag(Tab.search)
        }
        .tint(HashiyaColors.primary)
        .sheet(isPresented: $showsSettings) {
            SettingsView(viewModel: container.makeSettingsViewModel())
        }
        .task { await presentUITestingShareSheetIfRequested() }
    }

    /// Details on `tab`'s stack. Remove pops it, then removes the paper the way that tab does: the Library with its
    /// Undo banner, Search like its sheet's toggle.
    private func details(_ route: PaperDetailsRoute, in tab: Tab) -> some View {
        PaperDetailsScreen(
            viewModel: container.makePaperDetailsViewModel(openAlexID: route.openAlexID),
            onClose: { pop(tab) },
            onRemove: { id in
                pop(tab)
                Task {
                    switch tab {
                    case .library: await libraryViewModel.remove(openAlexID: id)
                    case .search: await searchViewModel.remove(openAlexID: id)
                    }
                }
            }
        )
    }

    private func pop(_ tab: Tab) {
        switch tab {
        case .library: if !libraryPath.isEmpty { libraryPath.removeLast() }
        case .search: if !searchPath.isEmpty { searchPath.removeLast() }
        }
    }

    /// Debug UI tests only (`-ui-testing-share <url>`); a Release build does nothing.
    private func presentUITestingShareSheetIfRequested() async {
        #if DEBUG
        await UITestingShareSheet.presentIfRequested {
            Task { await container.libraryRepository.refreshAfterExternalChanges() }
        }
        #endif
    }
}
