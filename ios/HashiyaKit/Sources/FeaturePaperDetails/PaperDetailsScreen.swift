import HashiyaDesignSystem
import HashiyaDiagnostics
import HashiyaModel
import SwiftUI
import UniformTypeIdentifiers

/// Details for a saved paper, pushed on a tab's navigation stack. It hides the tab bar, saves the notes when it
/// disappears and when the app leaves the foreground, and reports when it should go away.
public struct PaperDetailsScreen: View {
    @State private var viewModel: PaperDetailsViewModel
    /// The last Replace or Remove asked, so the dialog keeps its title while it closes (the answer is nil by then).
    @State private var lastConfirmation: PdfConfirmation?
    private let onClose: () -> Void
    private let onRemove: (String) -> Void
    private let onReadPdf: (String) -> Void

    @Environment(\.diagnostics) private var diagnostics
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    /// - Parameters:
    ///   - viewModel: evaluated on every update, but only the first instance is kept; its `init` starts nothing.
    ///   - onClose: the paper stopped being saved; pop this screen.
    ///   - onRemove: the user removed the paper and its notes are saved; pop this screen and remove it (with Undo).
    ///   - onReadPdf: the notes are saved; push the reader for this paper.
    public init(
        viewModel: @autoclosure () -> PaperDetailsViewModel,
        onClose: @escaping () -> Void,
        onRemove: @escaping (String) -> Void,
        onReadPdf: @escaping (String) -> Void = { _ in }
    ) {
        _viewModel = State(wrappedValue: viewModel())
        self.onClose = onClose
        self.onRemove = onRemove
        self.onReadPdf = onReadPdf
    }

    public var body: some View {
        content
            .hidesTabBarWhenCompact()
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
            .onChange(of: viewModel.openReader) { _, open in
                guard open else { return }
                viewModel.readerOpened()
                onReadPdf(viewModel.openAlexID)
            }
            .onAppear { diagnostics.screenShown(.details) }
            // Back from the reader: its Notes sheet may have written the notes.
            .onAppear { Task { await viewModel.onReaderClosed() } }
            .fileImporter(isPresented: $viewModel.showingFileImporter, allowedContentTypes: [.pdf]) { result in
                switch result {
                case .success(let url): Task { await viewModel.attachPdf(from: url) }
                case .failure: viewModel.message = .pdfAttachFailed
                }
            }
            .onChange(of: viewModel.pdfConfirmation) { _, confirmation in
                if let confirmation { lastConfirmation = confirmation }
            }
            .confirmationDialog(
                Text(verbatim: Self.confirmationTitle(viewModel.pdfConfirmation ?? lastConfirmation)),
                isPresented: Binding(get: { viewModel.pdfConfirmation != nil }, set: { if !$0 { viewModel.pdfConfirmation = nil } }),
                titleVisibility: .visible,
                // The dialog can clear its binding before a button's action runs, so the action gets the answer from here.
                presenting: viewModel.pdfConfirmation
            ) { confirmation in
                switch confirmation {
                case .replace:
                    Button {
                        viewModel.confirmReplace()
                    } label: {
                        Text(verbatim: L10n.string("details.pdfReplace"))
                    }
                case .remove:
                    Button(role: .destructive) {
                        Task { await viewModel.removePdf() }
                    } label: {
                        Text(verbatim: L10n.string("details.pdfRemove"))
                    }
                }
                Button(role: .cancel) {} label: {
                    Text(verbatim: L10n.string("details.pdfCancel"))
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
                pdf: viewModel.pdf,
                notes: viewModel.notesLoad == .failed ? nil : viewModel.notes,
                notesVersion: viewModel.notesVersion,
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
        actions.copyCitation = { style in Task { await viewModel.copyCitation(style) } }
        actions.citationStyle = viewModel.citationStyle
        actions.pdfAction = { action in
            if let link = viewModel.handle(action) { openURL(link) }
        }
        return actions
    }

    static func confirmationTitle(_ confirmation: PdfConfirmation?) -> String {
        L10n.string(confirmation == .remove ? "details.pdfRemoveTitle" : "details.pdfReplaceTitle")
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
