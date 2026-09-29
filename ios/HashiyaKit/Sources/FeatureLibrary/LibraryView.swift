import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// The Library tab's screen. Put it in a `NavigationStack`. Works offline.
public struct LibraryView: View {
    @Bindable private var viewModel: LibraryViewModel
    private let onGoToSearch: () -> Void
    private let onAddPaper: () -> Void
    private let onOpenSettings: () -> Void

    /// Space under the list's last row, so the Add paper button never covers it.
    static let addPaperClearance: CGFloat = 88

    @SceneStorage(LibraryViewModel.queryKey) private var storedQuery = ""
    @SceneStorage(LibraryViewModel.statusKey) private var storedStatus = ""
    @Environment(\.openURL) private var openURL

    /// - Parameter onAddPaper: the Add paper button; the app opens Search ready for input.
    public init(
        viewModel: LibraryViewModel,
        onGoToSearch: @escaping () -> Void,
        onAddPaper: @escaping () -> Void,
        onOpenSettings: @escaping () -> Void
    ) {
        self.viewModel = viewModel
        self.onGoToSearch = onGoToSearch
        self.onAddPaper = onAddPaper
        self.onOpenSettings = onOpenSettings
    }

    public var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(HashiyaColors.surface)
            .overlay(alignment: .bottom) {
                // The banners sit above the Add paper button (bottom trailing; bottom left in Arabic).
                VStack(alignment: .trailing, spacing: 0) {
                    if viewModel.message == .statusUpdateFailed {
                        HashiyaBanner(text: L10n.string("library.statusUpdateFailed"))
                    }
                    if viewModel.pendingUndo != nil {
                        HashiyaBanner(text: L10n.string("library.removed"), actionTitle: L10n.string("library.undo")) {
                            Task { await viewModel.undo() }
                        }
                    }
                    if viewModel.isLoaded {
                        AddPaperButton(action: onAddPaper)
                            .padding(.horizontal, 16)
                            .padding(.bottom, 16)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .animation(.default, value: viewModel.pendingUndo)
            .animation(.default, value: viewModel.message)
            .task(id: viewModel.pendingUndo) {
                // A newer removal cancels this task and restarts the 4 s.
                guard viewModel.pendingUndo != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                viewModel.undoExpired()
            }
            .task(id: viewModel.message) {
                // A newer message cancels this task: then it must not clear the new one.
                guard viewModel.message != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                viewModel.message = nil
            }
            .navigationTitle(Text(verbatim: L10n.string("library.title")))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onOpenSettings) {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel(Text(verbatim: L10n.string("library.settings")))
                }
            }
            .sheet(item: Binding(get: { viewModel.selectedPaper }, set: { viewModel.selectedPaperID = $0?.id })) { saved in
                preview(saved)
            }
            .onAppear { viewModel.restore(text: storedQuery, status: storedStatus) }
            .onChange(of: viewModel.text) { _, text in storedQuery = text }
            .onChange(of: viewModel.status) { _, _ in storedStatus = viewModel.storedStatus }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .loading:
            LoadingSkeleton(rows: 4)
        case .empty:
            EmptyStateView(
                icon: "books.vertical",
                title: L10n.string("library.emptyTitle"),
                message: L10n.string("library.emptyMessage"),
                actionTitle: L10n.string("library.goToSearch")
            ) {
                onGoToSearch()
            }
        case .papers, .noMatches:
            filtered
        }
    }

    /// Papers and No papers match share this container, its search field and its chips, so moving between them
    /// never rebuilds the field and the keyboard stays up while typing.
    private var filtered: some View {
        FilteredContent(state: viewModel.state, list: list, noMatches: noMatches)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .safeAreaInset(edge: .top, spacing: 0) {
                LibraryFilterChips(
                    selected: viewModel.status,
                    counts: viewModel.filter?.counts ?? [:],
                    onSelect: { viewModel.setStatusFilter($0) }
                )
                .background(HashiyaColors.surface)
            }
            .searchable(
                text: Binding(get: { viewModel.text }, set: { viewModel.updateText($0) }),
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: Text(verbatim: L10n.string("library.searchHint"))
            )
            .onSubmit(of: .search) { viewModel.submitNow() }
    }

    private var noMatches: some View {
        EmptyStateView(
            icon: "doc.text.magnifyingglass",
            title: L10n.string("library.noMatchesTitle"),
            actionTitle: L10n.string("library.noMatchesAction")
        ) {
            viewModel.clearSearchAndFilters()
        }
    }

    private var list: some View {
        List {
            Text(verbatim: L10n.paperCount(viewModel.papers.count))
                .font(.hashiya(.label))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .listRowSeparator(.hidden)
                .listRowBackground(HashiyaColors.surface)
            ForEach(viewModel.papers) { saved in
                HStack(alignment: .top, spacing: 12) {
                    LibraryRow(paper: saved.paper)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                        .onTapGesture { viewModel.select(saved.paper) }
                        .accessibilityAddTraits(.isButton)
                    ReadingStatusBadge(status: saved.status) { status in
                        Task { await viewModel.setStatus(of: saved.paper, to: status) }
                    }
                    .padding(.top, 4)
                }
                // Keeps the badge's menu and the row's tap separate: tapping the badge never opens the preview.
                .buttonStyle(.borderless)
                .listRowBackground(HashiyaColors.surface)
                .listRowSeparatorTint(HashiyaColors.outlineVariant)
                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                    Button(role: .destructive) {
                        Task { await viewModel.remove(saved.paper) }
                    } label: {
                        Label {
                            Text(verbatim: L10n.string("library.remove"))
                        } icon: {
                            Image(systemName: "trash")
                        }
                    }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .scrollDismissesKeyboard(.immediately)
        .contentMargins(.bottom, Self.addPaperClearance, for: .scrollContent)
    }

    private func preview(_ saved: LibraryPaper) -> some View {
        PaperPreviewContent(
            paper: saved.paper,
            inLibrary: true,
            status: saved.status,
            onStatusChange: { status in Task { await viewModel.setStatus(of: saved.paper, to: status) } },
            onToggleSave: { Task { await viewModel.remove(saved.paper) } },
            onOpenDOI: { doi in
                if let url = DOILink.url(for: doi) { openURL(url) }
            }
        )
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}

/// The list, or No papers match, inside one view so their shared container keeps its identity.
private struct FilteredContent<List: View, NoMatches: View>: View {
    let state: LibraryState
    let list: List
    let noMatches: NoMatches

    var body: some View {
        if case .papers = state {
            list
        } else {
            noMatches
        }
    }
}

/// The floating "+ Add paper" capsule.
private struct AddPaperButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label {
                Text(verbatim: L10n.string("library.addPaper"))
            } icon: {
                Image(systemName: "plus")
            }
            .font(.hashiya(.label))
            .foregroundStyle(HashiyaColors.onPrimary)
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
        }
        .buttonStyle(.borderedProminent)
        .buttonBorderShape(.capsule)
        .tint(HashiyaColors.primary)
        .shadow(color: .black.opacity(0.15), radius: 6, y: 2)
    }
}

/// Title (two lines at most) and one meta line.
private struct LibraryRow: View {
    let paper: Paper

    var body: some View {
        let meta = L10n.rowMeta(paper)
        VStack(alignment: .leading, spacing: 4) {
            PaperText(PaperFormat.title(paper), style: .cardTitle, lineLimit: 2)
            if !meta.isEmpty {
                PaperText(meta, style: .meta, color: HashiyaColors.onSurfaceVariant, lineLimit: 1)
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}
