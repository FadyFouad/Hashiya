import HashiyaData
import HashiyaDesignSystem
import SwiftUI

/// The Export screen: what a backup holds, whether to include the PDFs, and the save panel once the file is built.
public struct ExportBackupView: View {
    @Bindable private var viewModel: SettingsViewModel
    @Environment(\.dismiss) private var dismiss

    public init(viewModel: SettingsViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        Form {
            if let summary = viewModel.backup.summary {
                Section {
                    Text(verbatim: L10n.backupCounts(papers: summary.papers, collections: summary.collections))
                        .font(.hashiya(.body))
                        .foregroundStyle(HashiyaColors.onSurface)
                        .accessibilityIdentifier("export.counts")
                    if summary.pdfCount > 0 {
                        Toggle(isOn: includePdfs) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(verbatim: L10n.string("backup.includePdfs"))
                                    .font(.hashiya(.body))
                                    .foregroundStyle(HashiyaColors.onSurface)
                                Text(verbatim: L10n.backupPdfsSize(count: summary.pdfCount, bytes: summary.pdfBytes))
                                    .font(.hashiya(.meta))
                                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
                            }
                        }
                        .tint(HashiyaColors.primary)
                        .disabled(isBuilding)
                        .accessibilityIdentifier("export.includePdfs")
                    }
                } footer: {
                    Text(verbatim: L10n.string("backup.noSettings")).font(.hashiya(.meta))
                }
            }
            if case let .building(_, progress) = viewModel.backup.export {
                Section {
                    ProgressView(value: progress)
                        .tint(HashiyaColors.primary)
                    Text(verbatim: L10n.string("backup.exporting"))
                        .font(.hashiya(.meta))
                        .foregroundStyle(HashiyaColors.onSurfaceVariant)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(HashiyaColors.surface)
        .navigationTitle(Text(verbatim: L10n.string("backup.exportTitle")))
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(isBuilding)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                if isBuilding {
                    Button {
                        viewModel.cancelExport()
                        dismiss()
                    } label: {
                        Text(verbatim: L10n.string("settings.cancel"))
                    }
                    .accessibilityIdentifier("export.cancel")
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button {
                    viewModel.confirmExport()
                } label: {
                    Text(verbatim: L10n.string("backup.exportButton"))
                }
                .disabled(!isChoosing)
                .accessibilityIdentifier("export.confirm")
            }
        }
        .onAppear { viewModel.startExport() }
        .onDisappear { viewModel.cancelExport() }
        .onChange(of: viewModel.backup.message) { _, message in
            // Saved or failed, the export goes back to Settings, which shows the message.
            if message != nil { dismiss() }
        }
        // The system save panel moves the temporary file to the place the user picks, so no copy is held in memory.
        // Exactly one of the two closures runs for each presentation.
        .fileMover(isPresented: isReadyToSave, file: exportedURL) { result in
            viewModel.exportFinished((try? result.get()) != nil ? .saved : .failed)
        } onCancellation: {
            // A cancelled save sets no message, so the screen leaves from here.
            viewModel.exportFinished(.cancelled)
            dismiss()
        }
    }

    private var includePdfs: Binding<Bool> {
        Binding {
            switch viewModel.backup.export {
            case let .choosing(include), let .building(include, _): include
            case .idle, .readyToSave: false
            }
        } set: {
            viewModel.setIncludePdfs($0)
        }
    }

    private var isBuilding: Bool {
        if case .building = viewModel.backup.export { return true }
        return false
    }

    private var isChoosing: Bool {
        if case .choosing = viewModel.backup.export { return true }
        return false
    }

    /// Read-only: the save panel's completion or cancellation reports what happened.
    private var isReadyToSave: Binding<Bool> {
        Binding {
            if case .readyToSave = viewModel.backup.export { return true }
            return false
        } set: { _ in }
    }

    private var exportedURL: URL? {
        if case let .readyToSave(file) = viewModel.backup.export { return file.url }
        return nil
    }
}
