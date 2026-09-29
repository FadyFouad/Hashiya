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
    @Environment(\.scenePhase) private var scenePhase

    init(container: AppContainer) {
        self.container = container
        _libraryViewModel = State(initialValue: container.makeLibraryViewModel())
        _searchViewModel = State(initialValue: container.makeSearchViewModel())
    }

    var body: some View {
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
        .onChange(of: scenePhase, initial: true) { _, phase in
            switch phase {
            case .active:
                // Papers saved in the Share Extension appear in the Library and as "In library".
                SharedLibraryDatabase.resume()
                Task { await container.libraryRepository.refreshAfterExternalChanges() }
            case .background:
                SharedLibraryDatabase.suspend()
            default:
                break
            }
        }
    }
}
