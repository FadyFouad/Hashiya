import FeatureLibrary
import FeaturePaperDetails
import FeatureReader
import FeatureSearch
import FeatureSettings
import HashiyaData
import HashiyaDesignSystem
import SwiftUI

/// Library and Search tabs, each in its own navigation stack that can push a saved paper's Details and its PDF reader;
/// Settings as a sheet from either.
struct RootView: View {
    enum Tab: Hashable { case library, search }

    private let container: AppContainer
    @State private var selectedTab = Tab.library
    @State private var showsSettings = false
    /// Details, and the reader above it.
    @State private var libraryPath = NavigationPath()
    @State private var searchPath = NavigationPath()
    /// Wide windows (regular width): the paper shown beside the Library's list, and each detail pane's own stack (the
    /// reader above Details; in Search, Details and the reader above the preview).
    @State private var libraryPaperID: String?
    @State private var libraryDetailPath = NavigationPath()
    @State private var searchDetailPath = NavigationPath()
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
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
        // The window's width class for every screen (iPad windows, Split View, Stage Manager, rotation).
        .measuresLayoutClass()
        .onChange(of: scenePhase, initial: true) { _, phase in
            phaseGeneration += 1
            switch phase {
            case .active:
                // Papers saved in the Share Extension appear in the Library and as "In library".
                SharedLibraryDatabase.resume()
                Task { await container.libraryRepository.refreshAfterExternalChanges() }
                Task { await appUpdate.check() }
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
    }

    private func openStore() {
        if let url = appUpdate.requiredUpdate?.storeURL { openURL(url) }
    }

    /// iPadOS 18+: a tab bar that becomes a sidebar (a compact bar at the top of an iPad window, with the system's
    /// sidebar button; the system remembers whether the sidebar is open). iPhone looks the same as before. iOS 17 keeps
    /// the plain tab bar. Search keeps the plain tab role, so iOS 26 doesn't split it off into its own button on iPhone.
    @ViewBuilder
    private var tabView: some View {
        if #available(iOS 18, *) {
            TabView(selection: $selectedTab) {
                SwiftUI.Tab(value: Tab.library) {
                    libraryStack
                } label: {
                    tabLabel("nav.library", systemImage: "books.vertical")
                }
                SwiftUI.Tab(value: Tab.search) {
                    searchStack
                } label: {
                    tabLabel("nav.search", systemImage: "magnifyingglass")
                }
            }
            .tabViewStyle(.sidebarAdaptable)
        } else {
            TabView(selection: $selectedTab) {
                libraryStack
                    .tabItem { tabLabel("nav.library", systemImage: "books.vertical") }
                    .tag(Tab.library)
                searchStack
                    .tabItem { tabLabel("nav.search", systemImage: "magnifyingglass") }
                    .tag(Tab.search)
            }
        }
    }

    private func tabLabel(_ key: String, systemImage: String) -> some View {
        Label {
            Text(verbatim: AppStrings.string(key))
        } icon: {
            Image(systemName: systemImage)
        }
    }

    @ViewBuilder
    private var libraryStack: some View {
        if showsPanes {
            // The list always shows beside the detail; the tab bar's own button is the only sidebar toggle.
            NavigationSplitView(columnVisibility: .constant(.all)) {
                libraryList(onOpenPaper: { id in
                    libraryPaperID = id
                    libraryDetailPath = NavigationPath()
                }, selectedID: libraryPaperID)
                .navigationSplitViewColumnWidth(min: 320, ideal: 380, max: 480)
                    .toolbar(removing: .sidebarToggle)
            } detail: {
                // A new stack per paper (its identity): Details keeps its first view model, so another paper needs a
                // new screen, and its reader must not stay stacked above it. The identity goes on the stack, not on
                // its root view: a root with a changing identity doesn't show what is pushed above it.
                NavigationStack(path: $libraryDetailPath) {
                    libraryPaneRoot
                        .navigationDestination(for: ReaderRoute.self) { route in
                            reader(route, in: .library)
                        }
                }
                .id(libraryPaperID)
            }
            .navigationSplitViewStyle(.balanced)
        } else {
            NavigationStack(path: $libraryPath) {
                libraryList(onOpenPaper: { libraryPath.append(PaperDetailsRoute(openAlexID: $0)) }, selectedID: nil)
                    .navigationDestination(for: PaperDetailsRoute.self) { route in
                        details(route, in: .library)
                    }
                    .navigationDestination(for: ReaderRoute.self) { route in
                        reader(route, in: .library)
                    }
            }
        }
    }

    @ViewBuilder
    private var libraryPaneRoot: some View {
        if let id = libraryPaperID {
            details(PaperDetailsRoute(openAlexID: id), in: .library)
        } else {
            NoSelectionView.library
        }
    }

    private func libraryList(onOpenPaper: @escaping (String) -> Void, selectedID: String?) -> some View {
        LibraryView(
            viewModel: libraryViewModel,
            onGoToSearch: { selectedTab = .search },
            onAddPaper: {
                searchViewModel.startFresh(focus: true)
                selectedTab = .search
            },
            onOpenSettings: { showsSettings = true },
            onOpenPaper: onOpenPaper,
            selectedID: selectedID
        )
    }

    @ViewBuilder
    private var searchStack: some View {
        if showsPanes {
            // The list always shows beside the detail; the tab bar's own button is the only sidebar toggle.
            NavigationSplitView(columnVisibility: .constant(.all)) {
                SearchView(viewModel: searchViewModel, onOpenSettings: { showsSettings = true }, previewsInPane: true)
                    .navigationSplitViewColumnWidth(min: 320, ideal: 400, max: 480)
                    .toolbar(removing: .sidebarToggle)
            } detail: {
                NavigationStack(path: $searchDetailPath) {
                    searchPreviewPane
                        .navigationDestination(for: PaperDetailsRoute.self) { route in
                            details(route, in: .search)
                        }
                        .navigationDestination(for: ReaderRoute.self) { route in
                            reader(route, in: .search)
                        }
                }
            }
            .navigationSplitViewStyle(.balanced)
            // Another result replaces whatever was opened from the last one.
            .onChange(of: searchViewModel.selectedPaper?.id) { searchDetailPath = NavigationPath() }
        } else {
            NavigationStack(path: $searchPath) {
                SearchView(
                    viewModel: searchViewModel,
                    onOpenSettings: { showsSettings = true },
                    onOpenPaper: { searchPath.append(PaperDetailsRoute(openAlexID: $0)) }
                )
                .navigationDestination(for: PaperDetailsRoute.self) { route in
                    details(route, in: .search)
                }
                .navigationDestination(for: ReaderRoute.self) { route in
                    reader(route, in: .search)
                }
            }
        }
    }

    /// The picked result's preview, beside the results; Open details pushes Details inside this pane.
    @ViewBuilder
    private var searchPreviewPane: some View {
        if let paper = searchViewModel.selectedPaper {
            let saved = searchViewModel.isSaved(paper)
            PaperPreviewContent(
                paper: paper,
                inLibrary: saved,
                onToggleSave: { Task { await searchViewModel.toggleSave(paper) } },
                onOpenDOI: { doi in if let url = DOILink.url(for: doi) { openURL(url) } },
                onOpenDetails: saved ? { searchDetailPath.append(PaperDetailsRoute(openAlexID: paper.openAlexID)) } : nil
            )
            .id(paper.id)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        searchViewModel.selectedPaper = nil
                    } label: {
                        Image(systemName: "xmark")
                    }
                    .accessibilityLabel(Text(verbatim: DesignSystemStrings.closePreview))
                }
            }
        } else {
            NoSelectionView.search
        }
    }

    private var tabs: some View {
        tabView
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
            },
            onReadPdf: { id in push(ReaderRoute(openAlexID: id), in: tab) }
        )
    }

    /// The reader on `tab`'s stack, above the paper's Details.
    private func reader(_ route: ReaderRoute, in tab: Tab) -> some View {
        ReaderScreen(
            viewModel: container.makeReaderViewModel(openAlexID: route.openAlexID),
            onClose: { pop(tab) }
        )
    }

    /// List and detail side by side: on a regular-width window (iPad full screen, most iPad windows), as Apple's split
    /// views do. Compact width (iPhone, narrow iPad windows) keeps one stack per tab.
    private var showsPanes: Bool { horizontalSizeClass == .regular }

    private func pop(_ tab: Tab) {
        switch (tab, showsPanes) {
        case (.library, false): if !libraryPath.isEmpty { libraryPath.removeLast() }
        case (.search, false): if !searchPath.isEmpty { searchPath.removeLast() }
        // Closing Details at the root of the Library's pane clears the selection: the placeholder shows again.
        case (.library, true): if libraryDetailPath.isEmpty { libraryPaperID = nil } else { libraryDetailPath.removeLast() }
        case (.search, true): if !searchDetailPath.isEmpty { searchDetailPath.removeLast() }
        }
    }

    private func push(_ route: some Hashable, in tab: Tab) {
        switch (tab, showsPanes) {
        case (.library, false): libraryPath.append(route)
        case (.search, false): searchPath.append(route)
        case (.library, true): libraryDetailPath.append(route)
        case (.search, true): searchDetailPath.append(route)
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
