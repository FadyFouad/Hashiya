import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// The Library tab's screen. Put it in a `NavigationStack`. Works offline.
public struct LibraryView: View {
    @Bindable private var viewModel: LibraryViewModel
    private let onGoToSearch: () -> Void
    private let onAddPaper: () -> Void
    private let onOpenSettings: () -> Void
    private let onOpenPaper: (String) -> Void
    private let onOpenInNewWindow: ((String) -> Void)?
    private let selectedID: String?

    /// Space under the list's last row, so the Add paper button never covers it.
    static let addPaperClearance: CGFloat = 88

    @SceneStorage(LibraryViewModel.queryKey) private var storedQuery = ""
    @SceneStorage(LibraryViewModel.statusKey) private var storedStatus = ""
    @SceneStorage(LibraryViewModel.collectionKey) private var storedCollection = -1

    /// - Parameters:
    ///   - onAddPaper: the Add paper button; the app opens Search ready for input.
    ///   - onOpenPaper: a row tap, with the paper's OpenAlex ID; the app pushes Details, or shows it beside the list.
    ///   - onOpenInNewWindow: a row's Open in New Window, with the paper's OpenAlex ID; nil where the app can't open
    ///     windows (iPhone), which hides it.
    ///   - selectedID: the paper shown in the detail pane beside the list, highlighted; nil when Details is pushed.
    public init(
        viewModel: LibraryViewModel,
        onGoToSearch: @escaping () -> Void,
        onAddPaper: @escaping () -> Void,
        onOpenSettings: @escaping () -> Void,
        onOpenPaper: @escaping (String) -> Void = { _ in },
        onOpenInNewWindow: ((String) -> Void)? = nil,
        selectedID: String? = nil
    ) {
        self.onOpenInNewWindow = onOpenInNewWindow
        self.selectedID = selectedID
        self.viewModel = viewModel
        self.onGoToSearch = onGoToSearch
        self.onAddPaper = onAddPaper
        self.onOpenSettings = onOpenSettings
        self.onOpenPaper = onOpenPaper
    }

    public var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(HashiyaColors.surface)
            .overlay(alignment: .bottom) {
                // The banners sit above the Add paper button (bottom trailing; bottom left in Arabic). On iOS 26
                // they are glass, grouped so they blend as they come and go.
                HashiyaGlassGroup(spacing: 12) {
                    VStack(alignment: .trailing, spacing: 0) {
                        if let messageText {
                            HashiyaBanner(text: messageText)
                        }
                        if viewModel.pendingUndo != nil {
                            HashiyaBanner(text: L10n.string("library.removed"), actionTitle: L10n.string("library.undo")) {
                                Task { await viewModel.undo() }
                            }
                        }
                        if let undo = viewModel.pendingCollectionUndo {
                            HashiyaBanner(text: L10n.removedFromCollection(undo.collectionName), actionTitle: L10n.string("library.undo")) {
                                Task { await viewModel.undoCollectionRemoval() }
                            }
                        }
                        if viewModel.isLoaded {
                            AddPaperButton(action: onAddPaper)
                                .padding(.horizontal, 16)
                                .padding(.bottom, 16)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .animation(.default, value: viewModel.pendingUndo)
            .animation(.default, value: viewModel.pendingCollectionUndo)
            .animation(.default, value: viewModel.message)
            .task(id: viewModel.pendingUndo) {
                // A newer removal cancels this task and restarts the 4 s.
                guard viewModel.pendingUndo != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                viewModel.undoExpired()
            }
            .task(id: viewModel.pendingCollectionUndo) {
                guard viewModel.pendingCollectionUndo != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                viewModel.collectionUndoExpired()
            }
            .task(id: viewModel.message) {
                // A newer message cancels this task: then it must not clear the new one.
                guard viewModel.message != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                viewModel.message = nil
            }
            .navigationTitle(Text(verbatim: title))
            .navigationBarTitleDisplayMode(.inline)
            .modifier(TitleMenu(viewModel: viewModel, isEnabled: viewModel.allPapersTotal > 0))
            .toolbar {
                if viewModel.canExport {
                    ToolbarItem(placement: .topBarTrailing) {
                        ExportButton(viewModel: viewModel)
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onOpenSettings) {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel(Text(verbatim: L10n.string("library.settings")))
                }
            }
            .sheet(item: $viewModel.nameSheet) { sheet in
                CollectionNameSheet(
                    mode: sheet.mode,
                    initialName: sheet.initialName,
                    error: sheet.error,
                    onSubmit: { name in Task { await viewModel.submitName(name) } },
                    onCancel: { viewModel.dismissNameSheet() }
                )
            }
            .confirmationDialog(
                Text(verbatim: viewModel.pendingDelete.map { L10n.deleteCollectionTitle($0.name) } ?? ""),
                isPresented: Binding(
                    get: { viewModel.pendingDelete != nil },
                    set: { if !$0 { viewModel.pendingDelete = nil } }
                ),
                titleVisibility: .visible,
                presenting: viewModel.pendingDelete
            ) { collection in
                // `presenting` hands the collection over, so clearing `pendingDelete` first can't lose it.
                Button(role: .destructive) {
                    Task { await viewModel.confirmDelete(collection) }
                } label: {
                    Text(verbatim: L10n.string("library.delete"))
                }
            } message: { _ in
                Text(verbatim: L10n.string("library.deleteCollectionMessage"))
            }
            .onAppear {
                viewModel.restore(
                    text: storedQuery,
                    status: storedStatus,
                    collectionID: LibraryViewModel.collectionID(stored: storedCollection)
                )
            }
            .onChange(of: viewModel.text) { _, text in storedQuery = text }
            .onChange(of: viewModel.status) { _, _ in storedStatus = viewModel.storedStatus }
            .onChange(of: viewModel.collectionID) { _, _ in storedCollection = viewModel.storedCollection }
    }

    /// "Library" while nothing is saved; otherwise the view's name: "All papers" or the collection's.
    private var title: String {
        switch viewModel.state {
        case .loading, .empty:
            L10n.string("library.title")
        case .emptyCollection, .noMatches, .papers:
            // A collection the list doesn't have yet keeps its last name, never "All papers" over its contents.
            viewModel.collectionTitle ?? L10n.string(viewModel.collectionID == nil ? "library.allPapers" : "library.title")
        }
    }

    private var messageText: String? {
        switch viewModel.message {
        case .statusUpdateFailed: L10n.string("library.statusUpdateFailed")
        case .collectionsUpdateFailed: L10n.string("library.collectionsUpdateFailed")
        case .exportFailed: L10n.string("library.exportFailed")
        case .exportIncomplete: L10n.string("library.exportIncomplete")
        case nil: nil
        }
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
        case .emptyCollection:
            EmptyStateView(icon: "folder", title: L10n.string("library.collectionEmpty"))
        case .papers, .noMatches:
            filtered
        }
    }

    /// Papers and No papers match share this container, its search field and its chips, so moving between them
    /// never rebuilds the field and the keyboard stays up while typing.
    private var filtered: some View {
        FilteredContent(state: viewModel.state, list: list, noMatches: noMatches)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .hashiyaTopBar {
                LibraryFilterChips(
                    selected: viewModel.status,
                    counts: viewModel.filter?.counts ?? [:],
                    onSelect: { viewModel.setStatusFilter($0) }
                )
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
        // Decided when the rows are drawn: a row drawn in a collection only ever leaves that collection, even if the
        // collection is deleted before the swipe lands.
        let inCollection = viewModel.collectionID != nil
        return List {
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
                        .onTapGesture { onOpenPaper(saved.id) }
                        .accessibilityAddTraits(.isButton)
                        .hoverEffect(.highlight)
                    ReadingStatusBadge(status: saved.status) { status in
                        Task { await viewModel.setStatus(of: saved.paper, to: status) }
                    }
                    .padding(.top, 4)
                    if saved.hasPdf {
                        Image(systemName: "doc.richtext")
                            .font(.footnote)
                            .foregroundStyle(HashiyaColors.onSurfaceVariant)
                            .padding(.top, 8)
                            .accessibilityLabel(Text(verbatim: L10n.string("library.hasPdf")))
                            .accessibilityIdentifier("library.pdfIcon")
                    }
                }
                // Keeps the badge's menu and the row's tap separate: tapping the badge never opens Details.
                .buttonStyle(.borderless)
                .listRowBackground(saved.id == selectedID ? HashiyaColors.secondaryContainer : HashiyaColors.surface)
                .accessibilityAddTraits(saved.id == selectedID ? .isSelected : [])
                .listRowSeparatorTint(HashiyaColors.outlineVariant)
                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                    removeButton(saved, inCollection: inCollection)
                }
                // Long press, or a secondary click with a pointer.
                .contextMenu {
                    Button {
                        ContextMenuAction.afterClosing { onOpenPaper(saved.id) }
                    } label: {
                        Label {
                            Text(verbatim: L10n.string("library.open"))
                        } icon: {
                            Image(systemName: "doc.text")
                        }
                    }
                    if let onOpenInNewWindow {
                        Button {
                            onOpenInNewWindow(saved.id)
                        } label: {
                            Label {
                                Text(verbatim: DesignSystemStrings.openInNewWindow)
                            } icon: {
                                Image(systemName: "macwindow.badge.plus")
                            }
                        }
                    }
                    removeButton(saved, inCollection: inCollection)
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .scrollDismissesKeyboard(.immediately)
        .contentMargins(.bottom, Self.addPaperClearance, for: .scrollContent)
    }

    /// Removes the paper from the library, or in a collection only from that collection; both with Undo.
    private func removeButton(_ saved: LibraryPaper, inCollection: Bool) -> some View {
        Button(role: .destructive) {
            Task {
                if inCollection {
                    await viewModel.removeFromCollection(openAlexID: saved.paper.openAlexID)
                } else {
                    await viewModel.remove(saved.paper)
                }
            }
        } label: {
            Label {
                Text(verbatim: L10n.string(inCollection ? "library.removeFromCollection" : "library.remove"))
            } icon: {
                Image(systemName: inCollection ? "folder.badge.minus" : "trash")
            }
        }
    }
}

/// The navigation title's menu, once something is saved. Applied through a modifier, so an empty library has no menu
/// at all (an empty `toolbarTitleMenu` would still draw its chevron).
private struct TitleMenu: ViewModifier {
    let viewModel: LibraryViewModel
    let isEnabled: Bool

    func body(content: Content) -> some View {
        if isEnabled {
            content.toolbarTitleMenu {
                LibraryTitleMenu(viewModel: viewModel)
            }
        } else {
            content
        }
    }
}

/// All papers and each collection (checked when shown, with its count), New collection, and Rename and Delete for the
/// shown collection.
private struct LibraryTitleMenu: View {
    @Bindable var viewModel: LibraryViewModel

    var body: some View {
        Picker(selection: Binding(
            get: { viewModel.collectionID ?? LibraryViewModel.allPapersTag },
            set: { viewModel.selectCollection($0 == LibraryViewModel.allPapersTag ? nil : $0) }
        )) {
            row(L10n.string("library.allPapers"), count: viewModel.allPapersTotal)
                .tag(LibraryViewModel.allPapersTag)
            ForEach(viewModel.collections) { collection in
                row(collection.name, count: collection.paperCount)
                    .tag(collection.id)
            }
        } label: {
            EmptyView()
        }
        .pickerStyle(.inline)

        Button {
            viewModel.showNewCollection()
        } label: {
            Label {
                Text(verbatim: L10n.string("library.newCollection"))
            } icon: {
                Image(systemName: "plus")
            }
        }

        if let selected = viewModel.selectedCollection {
            Section {
                Button {
                    viewModel.showRename()
                } label: {
                    Label {
                        Text(verbatim: L10n.renameCollection(selected.name))
                    } icon: {
                        Image(systemName: "pencil")
                    }
                }
                Button(role: .destructive) {
                    viewModel.requestDelete()
                } label: {
                    Label {
                        Text(verbatim: L10n.deleteCollection(selected.name))
                    } icon: {
                        Image(systemName: "trash")
                    }
                }
            }
        }
    }

    /// In a menu, the second text is the item's subtitle: "3 papers".
    private func row(_ name: String, count: Int) -> some View {
        VStack {
            Text(verbatim: name)
            Text(verbatim: L10n.paperCount(count))
        }
    }
}

/// Export .bib, or a spinner from the tap until the share sheet closes.
private struct ExportButton: View {
    let viewModel: LibraryViewModel

    var body: some View {
        if viewModel.exporting {
            ProgressView()
                .accessibilityLabel(Text(verbatim: L10n.string("library.exportBib")))
        } else {
            Button {
                Task { await viewModel.export() }
            } label: {
                Image(systemName: "square.and.arrow.up")
            }
            .accessibilityLabel(Text(verbatim: L10n.string("library.exportBib")))
        }
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

/// The floating "+ Add paper" capsule: glass on iOS 26, a filled capsule with a shadow before.
private struct AddPaperButton: View {
    let action: () -> Void

    var body: some View {
        let button = Button(action: action) {
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
        .hashiyaProminentButton()
        .buttonBorderShape(.capsule)

        if #available(iOS 26, *) {
            button
        } else {
            button.shadow(color: .black.opacity(0.15), radius: 6, y: 2)
        }
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
