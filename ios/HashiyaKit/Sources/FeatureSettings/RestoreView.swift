import HashiyaData
import HashiyaDesignSystem
import SwiftUI

/// The Restore screen: what a backup file holds and what restoring it would do, then the outcome.
public struct RestoreView: View {
    @Bindable private var viewModel: RestoreViewModel
    private let onDone: () -> Void

    public init(viewModel: RestoreViewModel, onDone: @escaping () -> Void) {
        self.viewModel = viewModel
        self.onDone = onDone
    }

    public var body: some View {
        Form {
            switch viewModel.state {
            case .loading: loading
            case let .invalid(reason): invalid(reason)
            case let .preview(preview): previewSections(preview)
            case let .applying(progress): applying(progress)
            case let .done(result): done(result)
            case let .failed(error): failed(error)
            }
        }
        .scrollContentBackground(.hidden)
        .background(HashiyaColors.surface)
        .navigationTitle(Text(verbatim: L10n.string("restore.title")))
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(isApplying)
        .interactiveDismissDisabled(isApplying)
        .task { await viewModel.load() }
        // Leaving any way (Cancel, a swipe, the back button) frees the prepared copy; a running restore keeps its own.
        .onDisappear { viewModel.cancel() }
    }

    private var isApplying: Bool {
        if case .applying = viewModel.state { return true }
        return false
    }

    private var loading: some View {
        Section {
            HStack(spacing: 12) {
                ProgressView()
                line(L10n.string("restore.reading"), color: HashiyaColors.onSurfaceVariant)
            }
        }
    }

    private func invalid(_ reason: OpenFailure) -> some View {
        Section {
            line(L10n.restoreInvalid(reason))
            doneButton
        }
    }

    @ViewBuilder
    private func previewSections(_ preview: RestorePreview) -> some View {
        Section {
            if let exportedAt = preview.exportedAt {
                Text(verbatim: L10n.restoreFromDate(exportedAt))
                    .font(.hashiya(.body))
                    .foregroundStyle(HashiyaColors.onSurface)
            }
            Text(verbatim: L10n.restoreCounts(preview))
                .font(.hashiya(.body))
                .foregroundStyle(HashiyaColors.onSurface)
            Text(verbatim: L10n.restoreNewPapers(preview.newPapers))
                .font(.hashiya(.body))
                .foregroundStyle(HashiyaColors.onSurface)
            if preview.existingPapers > 0 {
                Text(verbatim: L10n.restoreExistingPapers(preview.existingPapers))
                    .font(.hashiya(.body))
                    .foregroundStyle(HashiyaColors.onSurface)
            }
            if preview.papersSkipped > 0 {
                Text(verbatim: L10n.restoreSkippedPapers(preview.papersSkipped))
                    .font(.hashiya(.body))
                    .foregroundStyle(HashiyaColors.onSurface)
            }
        } footer: {
            Text(verbatim: L10n.string("restore.keepsLibrary")).font(.hashiya(.meta))
        }
        Section {
            Button {
                viewModel.confirm()
            } label: {
                Text(verbatim: L10n.string("restore.add"))
                    .font(.hashiya(.label))
                    .foregroundStyle(HashiyaColors.onPrimary)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(HashiyaColors.primary)
            .accessibilityIdentifier("restore.add")
            Button {
                viewModel.cancel()
                onDone()
            } label: {
                Text(verbatim: L10n.string("restore.cancel"))
                    .font(.hashiya(.label))
                    .frame(maxWidth: .infinity)
            }
            .accessibilityIdentifier("restore.cancel")
        }
    }

    private func applying(_ progress: Double) -> some View {
        Section {
            ProgressView(value: progress)
                .tint(HashiyaColors.primary)
            line(L10n.string("restore.applying"), color: HashiyaColors.onSurfaceVariant)
        }
    }

    @ViewBuilder
    private func done(_ result: RestoreResult) -> some View {
        Section {
            Text(verbatim: L10n.string("restore.doneTitle"))
                .font(.hashiya(.stateTitle))
                .foregroundStyle(HashiyaColors.onSurface)
            line(L10n.restoreDoneBody(result))
            if result.pdfsMissing > 0 {
                line(L10n.restoreDoneMissingPdfs(result.pdfsMissing))
            }
            if result.papersSkipped > 0 {
                line(L10n.restoreDoneSkipped(result.papersSkipped))
            }
            doneButton
        }
    }

    private func failed(_ error: BackupError) -> some View {
        Section {
            line(L10n.restoreFailed(error))
            doneButton
        }
    }

    private var doneButton: some View {
        Button {
            onDone()
        } label: {
            Text(verbatim: L10n.string("restore.done"))
                .font(.hashiya(.label))
                .frame(maxWidth: .infinity)
        }
        .accessibilityIdentifier("restore.done")
    }

    private func line(_ string: String, color: Color = HashiyaColors.onSurface) -> some View {
        Text(verbatim: string)
            .font(.hashiya(.body))
            .foregroundStyle(color)
    }
}
