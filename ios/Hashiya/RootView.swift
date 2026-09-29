import FeatureLibrary
import FeatureSearch
import FeatureSettings
import HashiyaData
import HashiyaDesignSystem
import SwiftUI

/// Library and Search tabs, each in its own navigation stack; Settings as a sheet from either.
struct RootView: View {
    enum Tab: Hashable { case library, search }

    private let container: AppContainer
    @State private var selectedTab = Tab.library
    @State private var showsSettings = false
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
            switch phase {
            case .active:
                // Papers saved in the Share Extension appear in the Library and as "In library".
                SharedLibraryDatabase.resume()
                Task { await container.libraryRepository.refreshAfterExternalChanges() }
                Task { await appUpdate.check() }
            case .background:
                SharedLibraryDatabase.suspend()
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
            NavigationStack {
                LibraryView(
                    viewModel: libraryViewModel,
                    onGoToSearch: { selectedTab = .search },
                    onAddPaper: {
                        searchViewModel.startFresh(focus: true)
                        selectedTab = .search
                    },
                    onOpenSettings: { showsSettings = true }
                )
            }
            .tabItem {
                Label {
                    Text(verbatim: AppStrings.string("nav.library"))
                } icon: {
                    Image(systemName: "books.vertical")
                }
            }
            .tag(Tab.library)

            NavigationStack {
                SearchView(viewModel: searchViewModel, onOpenSettings: { showsSettings = true })
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

    /// Debug UI tests only (`-ui-testing-share <url>`); a Release build does nothing.
    private func presentUITestingShareSheetIfRequested() async {
        #if DEBUG
        await UITestingShareSheet.presentIfRequested {
            Task { await container.libraryRepository.refreshAfterExternalChanges() }
        }
        #endif
    }
}
