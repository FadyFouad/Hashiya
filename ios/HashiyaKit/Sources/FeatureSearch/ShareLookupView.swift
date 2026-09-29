import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// The Share Extension's sheet: "Hashiya" with Done, and one paper's lookup. It never opens URLs or Settings:
/// an extension can do neither.
public struct ShareLookupView: View {
    @Bindable private var viewModel: ShareLookupViewModel
    private let readInput: @MainActor @Sendable () async -> ShareLookupInput
    private let onDone: () -> Void

    /// Retry restarts the lookup task, so closing the sheet also cancels a retried lookup.
    @State private var attempt = 0

    /// - Parameters:
    ///   - readInput: loads what was shared; called once, while the sheet shows the Looking skeleton.
    ///   - onDone: closes the sheet (`completeRequest(returningItems:)`).
    public init(
        viewModel: ShareLookupViewModel,
        readInput: @escaping @MainActor @Sendable () async -> ShareLookupInput,
        onDone: @escaping () -> Void
    ) {
        self.viewModel = viewModel
        self.readInput = readInput
        self.onDone = onDone
    }

    public var body: some View {
        NavigationStack {
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(HashiyaColors.surface)
                .navigationTitle(Text(verbatim: L10n.string("search.shareTitle")))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button(action: onDone) {
                            Text(verbatim: L10n.string("search.shareDone"))
                        }
                    }
                }
                .overlay(alignment: .bottom) {
                    if let message = viewModel.message {
                        HashiyaBanner(text: L10n.string(message == .saveFailed ? "search.saveFailed" : "search.removeFailed"))
                    }
                }
                .animation(.default, value: viewModel.message)
                .task(id: viewModel.message) {
                    guard viewModel.message != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                    viewModel.message = nil
                }
        }
        .task(id: attempt) {
            if attempt > 0 {
                await viewModel.retry()
                return
            }
            guard viewModel.state == .reading else { return }
            let input = await readInput()
            guard !Task.isCancelled else { return }
            await viewModel.start(input)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .reading:
            LookupLookingView(identifier: nil)
        case let .looking(identifier):
            LookupLookingView(identifier: identifier)
        case let .found(paper):
            PaperPreviewContent(
                paper: paper,
                inLibrary: viewModel.isSaved(paper),
                onToggleSave: { Task { await viewModel.toggleSave(paper) } },
                onOpenDOI: nil
            )
        case let .notFound(identifier):
            EmptyStateView(
                icon: "doc.text.magnifyingglass",
                title: L10n.lookupNotFoundTitle(identifier),
                message: L10n.string("search.lookupNotFoundMessage")
            )
        case let .failed(error):
            SearchErrorView(error: error, onRetry: { attempt += 1 }, onOpenSettings: nil)
        case let .noIdentifier(pageTitle):
            NoIdentifierView(pageTitle: pageTitle)
        case .nothing:
            EmptyStateView(icon: "doc.text.magnifyingglass", title: L10n.string("search.noteNothing"))
        }
    }
}

/// "No DOI or arXiv ID on this page" and the page title, laid out in the title's own direction.
private struct NoIdentifierView: View {
    let pageTitle: String

    @Environment(\.layoutDirection) private var uiDirection

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "link")
                .font(.system(size: 40))
                .foregroundStyle(HashiyaColors.primary)
                .accessibilityHidden(true)
            Text(verbatim: L10n.string("search.shareNoIDTitle"))
                .font(.hashiya(.stateTitle))
                .foregroundStyle(HashiyaColors.onSurface)
                .accessibilityAddTraits(.isHeader)
            Text(verbatim: pageTitle)
                .font(.hashiya(.body))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .environment(\.layoutDirection, ContentDirection.of(pageTitle) ?? uiDirection)
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, 32)
        .padding(.vertical, 48)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
