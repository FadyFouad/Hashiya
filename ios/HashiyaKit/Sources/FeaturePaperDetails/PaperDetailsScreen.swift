import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// Details for a saved paper, pushed on a tab's navigation stack. It hides the tab bar, saves the notes when it
/// disappears and when the app leaves the foreground, and reports when it should go away.
public struct PaperDetailsScreen: View {
    @State private var viewModel: PaperDetailsViewModel
    private let onClose: () -> Void
    private let onRemove: (String) -> Void

    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    /// - Parameters:
    ///   - viewModel: evaluated on every update, but only the first instance is kept; its `init` starts nothing.
    ///   - onClose: the paper stopped being saved; pop this screen.
    ///   - onRemove: the user removed the paper and its notes are saved; pop this screen and remove it (with Undo).
    public init(
        viewModel: @autoclosure () -> PaperDetailsViewModel,
        onClose: @escaping () -> Void,
        onRemove: @escaping (String) -> Void
    ) {
        _viewModel = State(wrappedValue: viewModel())
        self.onClose = onClose
        self.onRemove = onRemove
    }

    public var body: some View {
        content
            .toolbar(.hidden, for: .tabBar)
            .task { await viewModel.start() }
            .onDisappear { viewModel.flush() }
            .onChange(of: scenePhase) { _, phase in
                // Inactive comes first on the way to the background: write before the database is suspended.
                if phase != .active { viewModel.flush() }
            }
            .onChange(of: viewModel.exit) { _, exit in
                switch exit {
                case .closed: onClose()
                case .removed: onRemove(viewModel.openAlexID)
                case nil: break
                }
            }
            .task(id: viewModel.message) {
                // A newer message cancels this task: then it must not clear the new one.
                guard viewModel.message != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                viewModel.message = nil
            }
            .sheet(isPresented: $viewModel.showingChecklist) {
                CollectionsChecklist(
                    collections: viewModel.collections,
                    memberIDs: viewModel.memberIDs,
                    message: viewModel.message,
                    actions: checklistActions
                )
                .presentationDetents([.medium, .large])
                .sheet(isPresented: $viewModel.showingNameSheet, onDismiss: { viewModel.dismissNameSheet() }) {
                    CollectionNameSheet(
                        mode: .create,
                        error: viewModel.nameSheetError,
                        onSubmit: { name in Task { await viewModel.submitNewCollection(name) } },
                        onCancel: { viewModel.dismissNameSheet() }
                    )
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        if let paper = viewModel.paper, viewModel.notesLoad != .loading {
            PaperDetailsContent(
                paper: paper,
                collections: viewModel.collections,
                memberIDs: viewModel.memberIDs,
                notes: viewModel.notesLoad == .failed ? nil : viewModel.notes,
                saveState: viewModel.saveState,
                // While the checklist is open its own banner shows the message; the screen behind shows none.
                message: viewModel.showingChecklist ? nil : viewModel.message,
                actions: actions
            )
        } else {
            LoadingSkeleton(rows: 3)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(HashiyaColors.surface)
                .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var actions: PaperDetailsActions {
        var actions = PaperDetailsActions()
        let viewModel = viewModel
        actions.updateNote = { section, text in viewModel.updateNote(section, text) }
        actions.setStatus = { status in Task { await viewModel.setStatus(status) } }
        actions.openURL = { url in openURL(url) }
        actions.remove = { Task { await viewModel.remove() } }
        actions.retrySave = { viewModel.flush() }
        actions.retryLoadNotes = { Task { await viewModel.retryLoadNotes() } }
        actions.showCollections = { viewModel.showingChecklist = true }
        actions.copyBibTeX = { Task { await viewModel.copyBibTeX() } }
        return actions
    }

    private var checklistActions: CollectionsChecklistActions {
        var actions = CollectionsChecklistActions()
        let viewModel = viewModel
        actions.toggle = { id in Task { await viewModel.toggleCollection(id) } }
        actions.newCollection = { viewModel.showNewCollection() }
        actions.done = { viewModel.showingChecklist = false }
        return actions
    }
}
