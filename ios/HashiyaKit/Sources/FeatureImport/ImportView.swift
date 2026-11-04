import HashiyaDesignSystem
import HashiyaDiagnostics
import HashiyaModel
import SwiftUI
import UniformTypeIdentifiers

/// The types the picker offers. The app declares the first two (Info.plist); plain text and any data are there too,
/// because many providers don't know .bib or .ris, and the content decides the format (spec §8.1).
public enum ImportFileTypes {
    public static let bibTeX = UTType(importedAs: "com.etatech.hashiya.bibtex", conformingTo: .plainText)
    public static let ris = UTType(importedAs: "com.etatech.hashiya.ris", conformingTo: .plainText)
    public static let all: [UTType] = [bibTeX, ris, .plainText, .data]

    /// Whether a file another app handed over goes to Import: a `.bib` or `.ris` file. `.hashiya` backups go to Restore.
    public static func opens(_ url: URL) -> Bool {
        ["bib", "ris"].contains(url.pathExtension.lowercased())
    }
}

/// Import from BibTeX or RIS (spec §8.2), presented as a sheet. `openedURL` is a file opened in Hashiya from another app.
public struct ImportView: View {
    @Bindable private var viewModel: ImportViewModel
    @Environment(\.diagnostics) private var diagnostics
    private let openedURL: URL?
    private let onClose: () -> Void
    @State private var showsPicker = false

    public init(viewModel: ImportViewModel, openedURL: URL? = nil, onClose: @escaping () -> Void) {
        self.viewModel = viewModel
        self.openedURL = openedURL
        self.onClose = onClose
    }

    public var body: some View {
        NavigationStack {
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(HashiyaColors.surface)
                .navigationTitle(Text(verbatim: L10n.string("import.title")))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(action: onClose) {
                            Text(verbatim: L10n.string("import.close"))
                        }
                    }
                }
        }
        .fileImporter(isPresented: $showsPicker, allowedContentTypes: ImportFileTypes.all) { result in
            if case let .success(url) = result { Task { await viewModel.open(url: url) } }
        }
        .onAppear { diagnostics.screenShown(.importFile) }
        .task { await viewModel.observe() }
        .task(id: openedURL) {
            if let openedURL { await viewModel.open(url: openedURL) }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .start:
            EmptyStateView(
                icon: "square.and.arrow.down",
                title: L10n.string("import.startTitle"),
                message: L10n.string("import.startMessage"),
                actionTitle: L10n.string("import.chooseFile")
            ) {
                showsPicker = true
            }
        case .reading:
            working(L10n.string("import.reading"), cancellable: false)
        case let .analyzing(job):
            working(L10n.format("import.analyzing", job.fileName), cancellable: true)
        case let .ready(job):
            preview(job)
        case let .importing(job):
            importing(job)
        case let .done(job):
            done(job)
        case let .failed(failure):
            EmptyStateView(
                icon: "exclamationmark.triangle",
                title: L10n.string(Self.messageKey(failure)),
                actionTitle: L10n.string("import.chooseAnother")
            ) {
                chooseAnother()
            }
        case .cancelled:
            EmptyStateView(
                icon: "square.and.arrow.down",
                title: L10n.string("import.cancelled"),
                actionTitle: L10n.string("import.chooseAnother")
            ) {
                chooseAnother()
            }
        }
    }

    static func messageKey(_ failure: ImportFailure) -> String {
        switch failure {
        case .unsupported: "import.failedUnsupported"
        case .tooLarge: "import.failedTooLarge"
        case .unreadable: "import.failedUnreadable"
        case .nothingToImport: "import.failedNothing"
        }
    }

    private func chooseAnother() {
        Task {
            await viewModel.dismiss()
            showsPicker = true
        }
    }

    private func working(_ text: String, cancellable: Bool) -> some View {
        VStack(spacing: 16) {
            ProgressView()
            Text(verbatim: text)
                .multilineTextAlignment(.center)
            if cancellable {
                Button(L10n.string("import.cancel")) { Task { await viewModel.cancel() } }
            }
        }
        .padding(24)
    }

    private func preview(_ job: ImportJob) -> some View {
        List {
            Section {
                countRow("import.found", job.counts.found, always: true)
                countRow("import.matched", job.counts.matched)
                countRow("import.local", job.counts.local)
                countRow("import.inLibrary", job.counts.inLibrary)
                countRow("import.repeated", job.counts.repeated)
                countRow("import.unreadable", job.counts.unreadable)
            } header: {
                Text(verbatim: job.fileName)
            } footer: {
                if job.counts.notChecked > 0 {
                    Text(verbatim: L10n.counted("import.notChecked", job.counts.notChecked))
                }
            }
            Section {
                Toggle(isOn: $viewModel.addToCollection) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: L10n.string("import.addToCollection"))
                        Text(verbatim: job.collectionName)
                            .font(.subheadline)
                            .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    }
                }
            }
            Section {
                Button {
                    Task { await viewModel.confirm() }
                } label: {
                    Text(verbatim: L10n.counted("import.confirm", job.counts.toImport))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .listRowBackground(Color.clear)
                Button(role: .cancel) {
                    Task { await viewModel.cancel() }
                } label: {
                    Text(verbatim: L10n.string("import.cancel"))
                        .frame(maxWidth: .infinity)
                }
                .listRowBackground(Color.clear)
            }
        }
    }

    @ViewBuilder
    private func countRow(_ key: String, _ count: Int, always: Bool = false) -> some View {
        if count > 0 || always {
            HStack {
                Text(verbatim: L10n.string(key))
                Spacer()
                Text(verbatim: PaperFormat.number(count))
                    .font(.headline)
            }
        }
    }

    private func importing(_ job: ImportJob) -> some View {
        let total = max(job.counts.toImport, 1)
        let done = min(max(job.processed, 0), total)
        return VStack(spacing: 16) {
            ProgressView(value: Double(done), total: Double(total))
            Text(verbatim: L10n.format("import.progress", PaperFormat.number(done), PaperFormat.number(job.counts.toImport)))
            Text(verbatim: L10n.string("import.continues"))
                .font(.subheadline)
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .multilineTextAlignment(.center)
            Button(L10n.string("import.cancel")) { Task { await viewModel.cancel() } }
        }
        .padding(24)
    }

    private func done(_ job: ImportJob) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.circle")
                .font(.system(size: 48))
                .foregroundStyle(HashiyaColors.primary)
                .accessibilityHidden(true)
            Text(verbatim: L10n.counted("import.done", job.imported))
                .font(.title2)
                .multilineTextAlignment(.center)
            if job.collectionID != nil {
                Text(verbatim: L10n.format("import.doneCollection", job.collectionName))
                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    .multilineTextAlignment(.center)
            }
            Button {
                Task {
                    await viewModel.dismiss()
                    onClose()
                }
            } label: {
                Text(verbatim: L10n.string("import.showLibrary"))
            }
            .buttonStyle(.borderedProminent)
            Button(L10n.string("import.another")) { chooseAnother() }
        }
        .padding(24)
    }
}
