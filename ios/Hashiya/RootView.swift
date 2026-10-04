import FeatureLibrary
import FeaturePaperDetails
import FeatureReader
import FeatureSearch
import FeatureSettings
import HashiyaData
import HashiyaDesignSystem
import SwiftUI

/// Library and Search tabs, each with its own list of pushed screens (a saved paper's Details and its PDF reader);
/// Settings as a sheet from either.
struct RootView: View {
    enum Tab: String { case library, search }

    private let container: AppContainer
    @State private var selectedTab = Tab.library
    @State private var showsSettings = false
    /// A `.hashiya` file opened from another app: this window shows Restore for it.
    @State private var openedBackup: OpenedBackup?
    /// The file whose Restore sheet is up until its dismissal has finished (`openedBackup` is cleared at its start).
    @State private var presentedBackup: OpenedBackup?
    @State private var backupQueue = OpenedBackupQueue()
    /// A restore is running in this window (in Settings or in the sheet).
    @State private var restoreApplying = false
    /// Details, and the reader above it. On wide windows the Library's first route is the paper beside its list and
    /// the rest its pane's stack; Search pushes them above the preview pane. The same lists drive both layouts.
    @State private var libraryRoutes: [AppRoute] = []
    @State private var searchRoutes: [AppRoute] = []
    /// Wide windows: whether each tab's list shows beside its detail.
    @State private var libraryColumns = NavigationSplitViewVisibility.all
    @State private var searchColumns = NavigationSplitViewVisibility.all
    /// What each list was before the reader opened: the reader hides the list for a wider page, and closing it brings
    /// back what the user had.
    @State private var libraryColumnsBeforeReader: NavigationSplitViewVisibility?
    @State private var searchColumnsBeforeReader: NavigationSplitViewVisibility?
    /// Each window's tab and open screens, restored when the system brings the window back.
    @SceneStorage("selectedTab") private var savedTab: String?
    @SceneStorage("libraryRoutes") private var savedLibraryRoutes: Data?
    @SceneStorage("searchRoutes") private var savedSearchRoutes: Data?
    @State private var restoredScene = false
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var libraryViewModel: LibraryViewModel
    @State private var searchViewModel: SearchViewModel
    @State private var appUpdate: AppUpdateModel
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL
    @Environment(\.openWindow) private var openWindow
    @Environment(\.supportsMultipleWindows) private var supportsMultipleWindows

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
        // The library database follows the whole app (HashiyaApp); each window checks for a required update.
        .onChange(of: scenePhase, initial: true) { _, phase in
            if phase == .active { Task { await appUpdate.check() } }
        }
    }

    /// Queues a `.hashiya` file the system handed over (a copy in Documents/Inbox).
    private func openBackup(_ url: URL) {
        guard let backup = OpenedBackup(openedURL: url) else { return }
        backupQueue.enqueue(backup)
        showNextBackup()
    }

    /// Shows the next queued file once nothing is in the way: a running restore and a Restore on screen are never
    /// replaced, and Settings closes first (its `onDismiss` asks again).
    private func showNextBackup() {
        switch backupQueue.next(isPresenting: openedBackup != nil || presentedBackup != nil, settingsOpen: showsSettings, restoreApplying: restoreApplying) {
        case .wait: break
        case .closeSettings: showsSettings = false
        case let .present(backup):
            presentedBackup = backup
            openedBackup = backup
        }
    }

    private func restoreApplyingChanged(_ applying: Bool) {
        restoreApplying = applying
        // A restore pushed inside Settings stays on screen until the user leaves it.
        if !applying && !showsSettings { showNextBackup() }
    }

    /// The restore works on its own copy, so the one the system put in Inbox isn't needed any more. Usually already
    /// deleted once the restore read it; this covers a sheet closed before then.
    private func restoreSheetDismissed() {
        presentedBackup?.removeInboxCopy()
        presentedBackup = nil
        openedBackup = nil
        showNextBackup()
    }

    private func openStore() {
        if let url = appUpdate.requiredUpdate?.storeURL { openURL(url) }
    }

    /// iPadOS 18+: the tab bar at the top of an iPad window, without a sidebar (the split views' own button shows and
    /// hides their list). iPhone looks the same as before. iOS 17 keeps the plain tab bar. Search keeps the plain tab
    /// role, so iOS 26 doesn't split it off into its own button on iPhone.
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
            .tabViewStyle(.tabBarOnly)
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
            // The split view's button hides the list, for more room to read.
            NavigationSplitView(columnVisibility: $libraryColumns) {
                libraryList(selectedID: libraryPaperID)
                    .navigationSplitViewColumnWidth(min: 320, ideal: 380, max: 480)
            } detail: {
                // A new stack per paper (its identity): Details keeps its first view model, so another paper needs a
                // new screen, and its reader must not stay stacked above it. The identity goes on the stack, not on
                // its root view: a root with a changing identity doesn't show what is pushed above it.
                NavigationStack(path: libraryPanePath) {
                    libraryPaneRoot
                        .navigationDestination(for: AppRoute.self) { destination($0, in: .library) }
                }
                .id(libraryPaperID)
            }
            .navigationSplitViewStyle(.balanced)
        } else {
            NavigationStack(path: $libraryRoutes) {
                libraryList(selectedID: nil)
                    .navigationDestination(for: AppRoute.self) { destination($0, in: .library) }
            }
        }
    }

    /// Wide windows: the paper beside the Library's list (its first route).
    private var libraryPaperID: String? {
        guard case .details(let route) = libraryRoutes.first else { return nil }
        return route.openAlexID
    }

    /// Wide windows: what is pushed above the paper in the Library's pane (its reader).
    private var libraryPanePath: Binding<[AppRoute]> {
        Binding {
            Array(libraryRoutes.dropFirst())
        } set: { path in
            libraryRoutes = Array(libraryRoutes.prefix(1)) + path
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

    /// Opening a paper replaces whatever was open: on a narrow window the list is the stack's root, on a wide one the
    /// paper goes beside it.
    private func libraryList(selectedID: String?) -> some View {
        LibraryView(
            viewModel: libraryViewModel,
            onGoToSearch: { selectedTab = .search },
            onAddPaper: addPaper,
            onOpenSettings: { showsSettings = true },
            onOpenPaper: { libraryRoutes = [.details(PaperDetailsRoute(openAlexID: $0))] },
            onOpenInNewWindow: openPaperWindow,
            selectedID: selectedID
        )
    }

    @ViewBuilder
    private var searchStack: some View {
        if showsPanes {
            NavigationSplitView(columnVisibility: $searchColumns) {
                SearchView(
                    viewModel: searchViewModel,
                    onOpenSettings: { showsSettings = true },
                    onOpenPaper: { searchRoutes = [.details(PaperDetailsRoute(openAlexID: $0))] },
                    onOpenInNewWindow: openPaperWindow,
                    previewsInPane: true
                )
                    .navigationSplitViewColumnWidth(min: 320, ideal: 400, max: 480)
            } detail: {
                NavigationStack(path: $searchRoutes) {
                    searchPreviewPane
                        .navigationDestination(for: AppRoute.self) { destination($0, in: .search) }
                }
            }
            .navigationSplitViewStyle(.balanced)
            // Another result replaces whatever was opened from the last one (a result's menu opens its Details along
            // with its preview).
            .onChange(of: searchViewModel.selectedPaper?.id) { _, id in
                if let id, searchRoutes.first?.openAlexID != id { searchRoutes = [] }
            }
        } else {
            NavigationStack(path: $searchRoutes) {
                SearchView(
                    viewModel: searchViewModel,
                    onOpenSettings: { showsSettings = true },
                    onOpenPaper: { searchRoutes.append(.details(PaperDetailsRoute(openAlexID: $0))) },
                    onOpenInNewWindow: openPaperWindow
                )
                .navigationDestination(for: AppRoute.self) { destination($0, in: .search) }
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
                onOpenDetails: saved ? { searchRoutes.append(.details(PaperDetailsRoute(openAlexID: paper.openAlexID))) } : nil
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
        .sheet(isPresented: $showsSettings, onDismiss: showNextBackup) {
            SettingsSheet(container: container, onRestoreApplyingChange: restoreApplyingChanged)
        }
        // The system activates one window for the file, so only that window shows Restore.
        .onOpenURL(perform: openBackup)
        .sheet(item: $openedBackup, onDismiss: restoreSheetDismissed) { opened in
            NavigationStack {
                OpenedBackupRestore(
                    container: container,
                    opened: opened,
                    onDone: { openedBackup = nil },
                    onApplyingChange: restoreApplyingChanged
                )
            }
        }
        .task { await presentUITestingShareSheetIfRequested() }
        .focusedSceneValue(\.appCommands, commandActions)
        .onAppear(perform: restoreScene)
        .onChange(of: selectedTab) { savedTab = selectedTab.rawValue }
        .onChange(of: libraryRoutes) { savedLibraryRoutes = libraryRoutes.sceneData }
        .onChange(of: searchRoutes) { savedSearchRoutes = searchRoutes.sceneData }
        .onChange(of: isReading(libraryRoutes)) { _, open in
            readerChanged(isOpen: open, columns: &libraryColumns, before: &libraryColumnsBeforeReader)
        }
        .onChange(of: isReading(searchRoutes)) { _, open in
            readerChanged(isOpen: open, columns: &searchColumns, before: &searchColumnsBeforeReader)
        }
        .onChange(of: showsPanes) { _, panes in
            if panes {
                // The split views mark their list hidden while they collapse for a narrow window: a wide one shows
                // the list again, unless the reader is open.
                libraryColumns = isReading(libraryRoutes) ? .detailOnly : .all
                searchColumns = isReading(searchRoutes) ? .detailOnly : .all
            } else if !searchRoutes.isEmpty {
                // A narrow window shows Search's preview as a sheet over the results: drop it when Details or the
                // reader is open above them, so the sheet doesn't cover what the user was reading.
                searchViewModel.selectedPaper = nil
            }
        }
    }

    /// The menu bar's commands in this window.
    private var commandActions: AppCommandActions {
        AppCommandActions(
            showLibrary: { selectedTab = .library },
            showSearch: { selectedTab = .search },
            addPaper: addPaper,
            openSettings: { showsSettings = true }
        )
    }

    /// Search, cleared and ready for input: the Library's Add paper and ⌘N.
    private func addPaper() {
        searchViewModel.startFresh(focus: true)
        selectedTab = .search
    }

    /// iPad: a saved paper in a window of its own. Nil where the app can't open windows (iPhone): no menu item there.
    private var openPaperWindow: ((String) -> Void)? {
        guard supportsMultipleWindows else { return nil }
        return { openWindow(id: PaperWindow.id, value: PaperWindow.Value(openAlexID: $0)) }
    }

    private func isReading(_ routes: [AppRoute]) -> Bool {
        routes.contains { if case .reader = $0 { true } else { false } }
    }

    /// Wide windows: the reader hides its tab's list, so the page gets the whole width; the list's button still shows
    /// it. Leaving the reader brings back the list as it was.
    private func readerChanged(isOpen: Bool, columns: inout NavigationSplitViewVisibility,
                               before: inout NavigationSplitViewVisibility?) {
        guard showsPanes else { return }
        if isOpen {
            before = columns
            columns = .detailOnly
        } else {
            columns = before ?? .all
            before = nil
        }
    }

    /// Brings back this window's tab and open screens once. UI tests always start fresh at the Library.
    private func restoreScene() {
        guard !restoredScene else { return }
        restoredScene = true
        guard !UITestingFlags.stubsEnabled else { return }
        selectedTab = savedTab.flatMap(Tab.init(rawValue:)) ?? .library
        libraryRoutes = .init(sceneData: savedLibraryRoutes)
        searchRoutes = .init(sceneData: savedSearchRoutes)
    }

    @ViewBuilder
    private func destination(_ route: AppRoute, in tab: Tab) -> some View {
        switch route {
        case .details(let details): self.details(details, in: tab)
        case .reader(let reader): self.reader(reader, in: tab)
        }
    }

    /// Details on `tab`'s stack. Remove pops it, then removes the paper the way that tab does: the Library with its
    /// Undo banner, Search like its sheet's toggle.
    private func details(_ route: PaperDetailsRoute, in tab: Tab) -> some View {
        PaperDetailsScreen(
            viewModel: container.makePaperDetailsViewModel(openAlexID: route.openAlexID),
            onClose: { close(.details(route), in: tab) },
            onRemove: { id in
                close(.details(route), in: tab)
                Task {
                    switch tab {
                    case .library: await libraryViewModel.remove(openAlexID: id)
                    case .search: await searchViewModel.remove(openAlexID: id)
                    }
                }
            },
            onReadPdf: { id in push(.reader(ReaderRoute(openAlexID: id)), in: tab) }
        )
    }

    /// The reader on `tab`'s stack, above the paper's Details.
    private func reader(_ route: ReaderRoute, in tab: Tab) -> some View {
        ReaderScreen(
            viewModel: container.makeReaderViewModel(openAlexID: route.openAlexID),
            onClose: { close(.reader(route), in: tab) }
        )
    }

    /// List and detail side by side: on a regular-width window (iPad full screen, most iPad windows), as Apple's split
    /// views do. Compact width (iPhone, narrow iPad windows) keeps one stack per tab.
    private var showsPanes: Bool { horizontalSizeClass == .regular }

    /// Closes `route` and anything above it. Closing the paper in the Library's pane shows the placeholder again.
    private func close(_ route: AppRoute, in tab: Tab) {
        switch tab {
        case .library: if let index = libraryRoutes.lastIndex(of: route) { libraryRoutes.removeSubrange(index...) }
        case .search: if let index = searchRoutes.lastIndex(of: route) { searchRoutes.removeSubrange(index...) }
        }
    }

    private func push(_ route: AppRoute, in tab: Tab) {
        switch tab {
        case .library: libraryRoutes.append(route)
        case .search: searchRoutes.append(route)
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

/// Settings with one view model for as long as the sheet is open: the sheet's content is rebuilt whenever the window
/// re-renders, and a new view model would lose what the Backup section has loaded.
private struct SettingsSheet: View {
    private let container: AppContainer
    private let onRestoreApplyingChange: (Bool) -> Void
    @State private var viewModel: SettingsViewModel

    init(container: AppContainer, onRestoreApplyingChange: @escaping (Bool) -> Void) {
        self.container = container
        self.onRestoreApplyingChange = onRestoreApplyingChange
        _viewModel = State(initialValue: container.makeSettingsViewModel())
    }

    var body: some View {
        SettingsView(
            viewModel: viewModel,
            makeRestoreViewModel: { container.makeRestoreViewModel(source: $0) },
            onRestoreApplyingChange: onRestoreApplyingChange
        )
    }
}

/// Restore with one view model for as long as the sheet is open (see `SettingsSheet`).
private struct OpenedBackupRestore: View {
    @State private var viewModel: RestoreViewModel
    let onDone: () -> Void
    let onApplyingChange: (Bool) -> Void

    /// The Inbox copy goes as soon as the restore has its own copy, so a restore the user never finishes doesn't keep it.
    init(container: AppContainer, opened: OpenedBackup, onDone: @escaping () -> Void, onApplyingChange: @escaping (Bool) -> Void) {
        _viewModel = State(initialValue: container.makeRestoreViewModel(source: opened.url, onSourceRead: opened.removeInboxCopy))
        self.onDone = onDone
        self.onApplyingChange = onApplyingChange
    }

    var body: some View {
        RestoreView(viewModel: viewModel, onDone: onDone, onApplyingChange: onApplyingChange)
    }
}
