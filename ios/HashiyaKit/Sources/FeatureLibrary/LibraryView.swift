import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// The Library tab's screen. Put it in a `NavigationStack`. Works offline.
public struct LibraryView: View {
    @Bindable private var viewModel: LibraryViewModel
    private let onGoToSearch: () -> Void
    private let onOpenSettings: () -> Void

    @Environment(\.openURL) private var openURL

    public init(viewModel: LibraryViewModel, onGoToSearch: @escaping () -> Void, onOpenSettings: @escaping () -> Void) {
        self.viewModel = viewModel
        self.onGoToSearch = onGoToSearch
        self.onOpenSettings = onOpenSettings
    }

    public var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(HashiyaColors.surface)
            .overlay(alignment: .bottom) {
                if viewModel.pendingUndo != nil {
                    HashiyaBanner(text: L10n.string("library.removed"), actionTitle: L10n.string("library.undo")) {
                        Task { await viewModel.undo() }
                    }
                }
            }
            .animation(.default, value: viewModel.pendingUndo)
            .task(id: viewModel.pendingUndo) {
                // A newer removal cancels this task and restarts the 4 s.
                guard viewModel.pendingUndo != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                viewModel.undoExpired()
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
            .sheet(item: Binding(get: { viewModel.selectedPaper }, set: { viewModel.selectedPaperID = $0?.openAlexID })) { paper in
                preview(paper)
            }
    }

    @ViewBuilder
    private var content: some View {
        if !viewModel.isLoaded {
            LoadingSkeleton(rows: 4)
        } else if viewModel.papers.isEmpty {
            EmptyStateView(
                icon: "books.vertical",
                title: L10n.string("library.emptyTitle"),
                message: L10n.string("library.emptyMessage"),
                actionTitle: L10n.string("library.goToSearch")
            ) {
                onGoToSearch()
            }
        } else {
            list
        }
    }

    private var list: some View {
        List {
            Text(verbatim: L10n.paperCount(viewModel.papers.count))
                .font(.hashiya(.label))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .listRowSeparator(.hidden)
                .listRowBackground(HashiyaColors.surface)
            ForEach(viewModel.papers) { paper in
                LibraryRow(paper: paper)
                    .contentShape(Rectangle())
                    .onTapGesture { viewModel.select(paper) }
                    .accessibilityAddTraits(.isButton)
                    .listRowBackground(HashiyaColors.surface)
                    .listRowSeparatorTint(HashiyaColors.outlineVariant)
                    .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                        Button(role: .destructive) {
                            Task { await viewModel.remove(paper) }
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
    }

    private func preview(_ paper: Paper) -> some View {
        PaperPreviewContent(
            paper: paper,
            inLibrary: true,
            onToggleSave: { Task { await viewModel.remove(paper) } },
            onOpenDOI: { doi in
                if let url = DOILink.url(for: doi) { openURL(url) }
            }
        )
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
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
