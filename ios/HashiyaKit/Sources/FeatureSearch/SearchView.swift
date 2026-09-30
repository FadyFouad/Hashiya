import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// The Search tab's screen. Put it in a `NavigationStack`.
public struct SearchView: View {
    @Bindable private var viewModel: SearchViewModel
    private let onOpenSettings: () -> Void
    private let onOpenPaper: (String) -> Void

    @SceneStorage(SearchSceneState.textKey) private var storedText = ""
    @SceneStorage(SearchSceneState.sortKey) private var storedSort = SearchSort.relevance.rawValue
    @SceneStorage(SearchSceneState.yearKindKey) private var storedYearKind: String?
    @SceneStorage(SearchSceneState.yearFromKey) private var storedYearFrom: Int?
    @SceneStorage(SearchSceneState.yearToKey) private var storedYearTo: Int?
    @SceneStorage(SearchSceneState.openAccessKey) private var storedOpenAccess = false

    @State private var showsYearRange = false
    @State private var isSearchActive = false
    @State private var detailsRequest: String?
    @Environment(\.openURL) private var openURL

    /// - Parameter onOpenPaper: Open details in a saved paper's sheet, with its OpenAlex ID, once the sheet is gone.
    public init(viewModel: SearchViewModel, onOpenSettings: @escaping () -> Void, onOpenPaper: @escaping (String) -> Void = { _ in }) {
        self.viewModel = viewModel
        self.onOpenSettings = onOpenSettings
        self.onOpenPaper = onOpenPaper
    }

    public var body: some View {
        screen
            .navigationTitle(Text(verbatim: L10n.string("search.title")))
            .searchable(
                text: Binding(get: { viewModel.text }, set: { viewModel.updateText($0) }),
                isPresented: $isSearchActive,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: Text(verbatim: L10n.string("search.placeholder"))
            )
            .onSubmit(of: .search) { viewModel.submitNow() }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onOpenSettings) {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel(Text(verbatim: L10n.string("search.settings")))
                }
            }
            .sheet(item: $viewModel.selectedPaper, onDismiss: openRequestedDetails) { paper in
                preview(paper)
            }
            .sheet(isPresented: $showsYearRange) {
                YearRangeSheet(current: viewModel.query.years) { viewModel.setYears($0) }
                    .presentationDetents([.medium])
            }
            .onAppear(perform: restore)
            .onChange(of: viewModel.text) { _, text in storedText = text }
            .onChange(of: viewModel.query) { _, query in store(query) }
            .task(id: viewModel.focusRequested) {
                // Add paper: activate the field (and the keyboard) once, also when this tab appears for it.
                guard viewModel.focusRequested else { return }
                isSearchActive = true
                viewModel.focusHandled()
            }
    }

    private var screen: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(HashiyaColors.surface)
            .hashiyaTopBar {
                // ID mode has no chips.
                if viewModel.lookup == nil {
                    FilterChips(
                        query: viewModel.query,
                        onSort: { viewModel.setSort($0) },
                        onYears: { viewModel.setYears($0) },
                        onCustomRange: { showsYearRange = true },
                        onOpenAccess: { viewModel.setOpenAccessOnly($0) }
                    )
                }
            }
            .overlay(alignment: .bottom) {
                if let message = viewModel.message {
                    HashiyaBanner(text: L10n.string(message == .saveFailed ? "search.saveFailed" : "search.removeFailed"))
                }
            }
            .animation(.default, value: viewModel.message)
            .task(id: viewModel.message) {
                // A newer message cancels this task: then it must not clear the new one.
                guard viewModel.message != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                viewModel.message = nil
            }
    }

    private func preview(_ paper: Paper) -> some View {
        let saved = viewModel.isSaved(paper)
        return PaperPreviewContent(
            paper: paper,
            inLibrary: saved,
            onToggleSave: { Task { await viewModel.toggleSave(paper) } },
            onOpenDOI: openDOI,
            onOpenDetails: saved ? { openDetails(paper) } : nil
        )
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    /// Closes the sheet; Details is pushed once it is gone, so the push never overlaps the dismissal.
    private func openDetails(_ paper: Paper) {
        detailsRequest = paper.openAlexID
        viewModel.selectedPaper = nil
    }

    private func openRequestedDetails() {
        guard let id = detailsRequest else { return }
        detailsRequest = nil
        onOpenPaper(id)
    }

    private func openDOI(_ doi: String) {
        if let url = DOILink.url(for: doi) { openURL(url) }
    }

    private func restore() {
        let chips = SearchSceneState(
            sort: storedSort,
            yearKind: storedYearKind,
            yearFrom: storedYearFrom,
            yearTo: storedYearTo,
            openAccess: storedOpenAccess
        )
        viewModel.restore(text: storedText, query: chips.query(text: ""))
    }

    private func store(_ query: SearchQuery) {
        let chips = SearchSceneState(query)
        storedSort = chips.sort
        storedYearKind = chips.yearKind
        storedYearFrom = chips.yearFrom
        storedYearTo = chips.yearTo
        storedOpenAccess = chips.openAccess
    }

    @ViewBuilder
    private var content: some View {
        if let lookup = viewModel.lookup {
            LookupBody(
                state: lookup,
                isSaved: { viewModel.isSaved($0) },
                onToggleSave: { paper in Task { await viewModel.toggleSave(paper) } },
                onOpenDOI: openDOI,
                onSearchTitle: { viewModel.searchTitle($0) },
                onRetry: { viewModel.retry() },
                onOpenSettings: onOpenSettings
            )
        } else {
            keywordContent
        }
    }

    @ViewBuilder
    private var keywordContent: some View {
        switch viewModel.phase {
        case .idle:
            IdleView { viewModel.applySuggestion($0) }
        case .loading:
            LoadingSkeleton(rows: 4)
        case .results:
            results
        case .empty:
            if viewModel.query.hasActiveFilters {
                EmptyStateView(
                    icon: "doc.text.magnifyingglass",
                    title: L10n.string("search.emptyTitle"),
                    message: L10n.string("search.emptyMessage"),
                    actionTitle: L10n.string("search.clearFilters"),
                    action: { viewModel.clearFilters() }
                )
            } else {
                EmptyStateView(
                    icon: "doc.text.magnifyingglass",
                    title: L10n.string("search.emptyTitle"),
                    message: L10n.string("search.emptyMessage")
                )
            }
        case let .failed(error):
            SearchErrorView(error: error, onRetry: { viewModel.retry() }, onOpenSettings: onOpenSettings)
        }
    }

    private var results: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                if let total = viewModel.totalCount {
                    Text(verbatim: L10n.resultCount(total))
                        .font(.hashiya(.label))
                        .foregroundStyle(HashiyaColors.onSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 16)
                        .padding(.top, 4)
                        .padding(.bottom, 2)
                }
                ForEach(viewModel.papers) { paper in
                    PaperCard(
                        paper: paper,
                        inLibrary: viewModel.isSaved(paper),
                        onOpen: { viewModel.selectedPaper = paper },
                        onSave: { Task { await viewModel.toggleSave(paper) } }
                    )
                    .onAppear {
                        if paper.id == viewModel.papers.last?.id { viewModel.loadMore() }
                    }
                }
                footer
            }
            .padding(.bottom, 16)
        }
        .scrollDismissesKeyboard(.immediately)
    }

    @ViewBuilder
    private var footer: some View {
        switch viewModel.append {
        case .loading:
            ProgressView().padding(16)
        case .failed:
            HStack(spacing: 12) {
                Text(verbatim: L10n.string("search.appendError"))
                    .font(.hashiya(.body))
                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button {
                    viewModel.retryAppend()
                } label: {
                    Text(verbatim: L10n.string("search.retry")).font(.hashiya(.label))
                }
                .buttonStyle(.bordered)
                .tint(HashiyaColors.primary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        case .idle:
            // More pages exist. The last card's onAppear does not fire again after pages made only of
            // duplicates are skipped, so this invisible row asks for the next page when it comes into view.
            Color.clear
                .frame(height: 0)
                .accessibilityHidden(true)
                .onAppear { viewModel.loadMore() }
        case .endReached:
            EmptyView()
        }
    }
}

/// No active query: an invitation and three suggestions (not translated: they are search terms).
private struct IdleView: View {
    let onSuggestion: (String) -> Void

    private let suggestions = ["search.suggestionLLM", "search.suggestionCRISPR", "search.suggestionClimate"]

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 40))
                .foregroundStyle(HashiyaColors.primary)
                .accessibilityHidden(true)
            Text(verbatim: L10n.string("search.idleTitle"))
                .font(.hashiya(.stateTitle))
                .foregroundStyle(HashiyaColors.onSurface)
            Text(verbatim: L10n.string("search.idleMessage"))
                .font(.hashiya(.body))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
            VStack(spacing: 8) {
                ForEach(suggestions, id: \.self) { key in
                    let suggestion = L10n.string(key)
                    Button {
                        onSuggestion(suggestion)
                    } label: {
                        ChipLabel(text: suggestion, isSelected: false)
                    }
                    .buttonStyle(.plain)
                    .environment(\.layoutDirection, .leftToRight)
                }
            }
            .padding(.top, 8)
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, 32)
        .padding(.vertical, 48)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
