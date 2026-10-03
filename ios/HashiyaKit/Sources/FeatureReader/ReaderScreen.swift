import HashiyaDesignSystem
import SwiftUI
import UniformTypeIdentifiers

/// The reader, pushed on a tab's navigation stack. It hides the tab bar, saves the page and the notes when it goes away
/// and when the app leaves the foreground, and reports when it should be popped.
public struct ReaderScreen: View {
    /// How long the page pill stays after the page stops changing.
    static let pillDuration: Duration = .milliseconds(1500)

    @State private var viewModel: ReaderViewModel
    @State private var controller = ReaderPDFController()
    @State private var showsPill = false
    @State private var importingReplacement = false
    private let onClose: () -> Void

    @Environment(\.scenePhase) private var scenePhase

    /// - Parameters:
    ///   - viewModel: evaluated on every update, but only the first instance is kept; its `init` starts nothing.
    ///   - onClose: Back after the notes saved, or nothing left to read; pop this screen.
    public init(viewModel: @autoclosure () -> ReaderViewModel, onClose: @escaping () -> Void) {
        _viewModel = State(wrappedValue: viewModel())
        self.onClose = onClose
    }

    public var body: some View {
        ReaderContent(
            title: viewModel.title,
            state: viewModel.state,
            pageLabel: pageLabel,
            showsPill: showsPill,
            fileURL: viewModel.fileURL,
            message: viewModel.message,
            notesSaveFailed: viewModel.notes.saveState == .failed,
            actions: actions
        ) {
            if let document = viewModel.document, case let .ready(_, startPage) = viewModel.state {
                PDFKitView(
                    document: document,
                    startPage: startPage,
                    controller: controller,
                    onPageChanged: { viewModel.onPageChanged($0) }
                )
            }
        }
        .hidesTabBarWhenCompact()
        .task { await viewModel.start() }
        .onDisappear { viewModel.onDisappear() }
        .onChange(of: scenePhase) { _, phase in
            // Inactive comes first on the way to the background: write before the database is suspended.
            if phase != .active { viewModel.onDisappear() }
        }
        .onChange(of: viewModel.exit) { _, exit in
            if exit != nil { onClose() }
        }
        .task(id: viewModel.currentPage) {
            // A newer page restarts the 1.5 s.
            guard case .ready = viewModel.state else { return }
            showsPill = true
            guard (try? await Task.sleep(for: Self.pillDuration)) != nil else { return }
            showsPill = false
        }
        .task(id: viewModel.message) {
            guard viewModel.message != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
            viewModel.message = nil
        }
        .sheet(isPresented: $viewModel.showingNotes, onDismiss: { viewModel.notesClosed() }) {
            ReaderNotesSheet(
                notes: viewModel.notes.notesLoad == .loaded ? viewModel.notes.notes : nil,
                saveState: viewModel.notes.saveState,
                onChange: { section, text in viewModel.notes.onNoteChange(section: section, text: text) },
                onClose: { viewModel.notesClosed() }
            )
            .presentationDetents([.medium, .large])
        }
        .fileImporter(isPresented: $importingReplacement, allowedContentTypes: [.pdf]) { result in
            switch result {
            case .success(let url): Task { await viewModel.replace(with: url) }
            case .failure: viewModel.message = .attachFailed
            }
        }
    }

    private var pageLabel: String? {
        guard case let .ready(pageCount, _) = viewModel.state else { return nil }
        return L10n.page(viewModel.currentPage, of: pageCount)
    }

    private var actions: ReaderActions {
        var actions = ReaderActions()
        let viewModel = viewModel
        let controller = controller
        actions.back = { Task { await viewModel.back() } }
        actions.search = { controller.showFind() }
        actions.showNotes = { viewModel.showNotes() }
        actions.replace = { importingReplacement = true }
        actions.removePdf = { Task { await viewModel.removePdf() } }
        actions.retrySave = { viewModel.notes.retry() }
        return actions
    }
}
