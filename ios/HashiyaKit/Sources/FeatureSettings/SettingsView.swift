import HashiyaData
import HashiyaDesignSystem
import HashiyaDiagnostics
import HashiyaModel
import SwiftUI
import UIKit

/// The Settings sheet: the OpenAlex key, the space PDFs use and a Language row that opens iOS Settings.
public struct SettingsView: View {
    @Bindable private var viewModel: SettingsViewModel
    @State private var showsKey = false
    @State private var confirmingDelete = false
    @State private var importing = false
    @State private var restoreSource: URL?
    @State private var addressCopied = false
    @Environment(\.diagnostics) private var diagnostics
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    private let makeRestoreViewModel: (URL) -> RestoreViewModel
    private let onRestoreApplyingChange: (Bool) -> Void

    public init(
        viewModel: SettingsViewModel,
        makeRestoreViewModel: @escaping (URL) -> RestoreViewModel,
        onRestoreApplyingChange: @escaping (Bool) -> Void = { _ in }
    ) {
        self.viewModel = viewModel
        self.makeRestoreViewModel = makeRestoreViewModel
        self.onRestoreApplyingChange = onRestoreApplyingChange
    }

    public var body: some View {
        NavigationStack {
            Form {
                apiKeySection
                storageSection
                BackupSection(summary: viewModel.backup.summary, onRestore: { importing = true })
                privacySection
                languageSection
                AboutSection(onAddressCopied: { addressCopied = true })
            }
            .scrollContentBackground(.hidden)
            .background(HashiyaColors.surface)
            .navigationTitle(Text(verbatim: L10n.string("settings.title")))
            .navigationBarTitleDisplayMode(.inline)
            .task { await viewModel.loadStorage() }
            .onAppear { diagnostics.screenShown(.settings) }
            // Also when the Export screen or a restore returns, so the counts are never stale.
            .onAppear { Task { await viewModel.loadBackupSummary() } }
            .navigationDestination(for: SettingsDestination.self) { destination in
                switch destination {
                case .export: ExportBackupView(viewModel: viewModel)
                }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.hashiyaBackup, .zip]) { result in
                if case let .success(url) = result { restoreSource = url }
            }
            .navigationDestination(item: $restoreSource) { url in
                RestoreDestination(
                    source: url,
                    makeViewModel: makeRestoreViewModel,
                    onDone: { restoreSource = nil },
                    onApplyingChange: onRestoreApplyingChange
                )
            }
            .overlay(alignment: .bottom) {
                if let message = viewModel.backup.message, let text = L10n.backupMessage(message) {
                    HashiyaBanner(text: text)
                }
            }
            .animation(.default, value: viewModel.backup.message)
            .overlay(alignment: .bottom) {
                if addressCopied {
                    HashiyaBanner(text: L10n.format("settings.feedbackCopied", Feedback.address))
                }
            }
            .animation(.default, value: addressCopied)
            .task(id: addressCopied) {
                guard addressCopied, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                addressCopied = false
            }
            .task(id: viewModel.backup.message) {
                // A newer message cancels this task: then it must not clear the new one.
                guard viewModel.backup.message != nil, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                viewModel.dismissMessage()
            }
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        dismiss()
                    } label: {
                        Text(verbatim: L10n.string("settings.done"))
                    }
                }
            }
        }
    }

    private var apiKeySection: some View {
        Section {
            Text(verbatim: L10n.string(viewModel.usingUserKey ? "settings.apiKeyUsingYours" : "settings.apiKeyUsingBuiltIn"))
                .font(.hashiya(.body))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
            HStack {
                Group {
                    if showsKey {
                        TextField(text: $viewModel.keyInput) { Text(verbatim: L10n.string("settings.apiKeyLabel")) }
                    } else {
                        SecureField(text: $viewModel.keyInput) { Text(verbatim: L10n.string("settings.apiKeyLabel")) }
                    }
                }
                .font(.hashiya(.body))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .environment(\.layoutDirection, .leftToRight)
                Button {
                    showsKey.toggle()
                } label: {
                    Image(systemName: showsKey ? "eye.slash" : "eye")
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(Text(verbatim: L10n.string(showsKey ? "settings.apiKeyHide" : "settings.apiKeyShow")))
            }
            HStack(spacing: 12) {
                Button {
                    Task { await viewModel.save() }
                } label: {
                    Text(verbatim: L10n.string("settings.save"))
                        .font(.hashiya(.label))
                        .foregroundStyle(HashiyaColors.onPrimary)
                }
                .buttonStyle(.borderedProminent)
                Button {
                    Task { await viewModel.reset() }
                } label: {
                    Text(verbatim: L10n.string("settings.reset")).font(.hashiya(.label))
                }
                .buttonStyle(.bordered)
                .disabled(!viewModel.usingUserKey)
            }
            .tint(HashiyaColors.primary)
        } header: {
            Text(verbatim: L10n.string("settings.apiKeySection"))
                .font(.hashiya(.stateTitle))
                .foregroundStyle(HashiyaColors.onSurface)
                .textCase(nil)
        } footer: {
            let text = L10n.string("settings.apiKeyFooter")
            Text((try? AttributedString(markdown: text)) ?? AttributedString(text))
                .font(.hashiya(.label))
                .tint(HashiyaColors.primary)
        }
    }

    @ViewBuilder
    private var storageSection: some View {
        let storage = viewModel.storage ?? .empty
        Section {
            Text(verbatim: L10n.downloadedPdfs(bytes: storage.downloadedBytes, count: storage.downloadedCount))
                .font(.hashiya(.body))
                .foregroundStyle(HashiyaColors.onSurface)
                .accessibilityIdentifier("settings.downloadedPdfs")
            if storage.attachedCount > 0 {
                Text(verbatim: L10n.attachedPdfs(bytes: storage.attachedBytes, count: storage.attachedCount))
                    .font(.hashiya(.body))
                    .foregroundStyle(HashiyaColors.onSurface)
                    .accessibilityIdentifier("settings.attachedPdfs")
            }
            if storage.downloadedCount > 0 {
                Button(role: .destructive) {
                    confirmingDelete = true
                } label: {
                    Text(verbatim: L10n.string("settings.deleteDownloaded")).font(.hashiya(.label))
                }
                .accessibilityIdentifier("settings.deleteDownloaded")
                .confirmationDialog(
                    Text(verbatim: L10n.deleteDownloadedMessage(count: storage.downloadedCount)),
                    isPresented: $confirmingDelete,
                    titleVisibility: .visible
                ) {
                    Button(role: .destructive) {
                        Task { await viewModel.deleteDownloaded() }
                    } label: {
                        Text(verbatim: L10n.string("settings.delete"))
                    }
                    .accessibilityIdentifier("settings.confirmDeleteDownloaded")
                    Button(role: .cancel) {} label: {
                        Text(verbatim: L10n.string("settings.cancel"))
                    }
                }
            }
        } header: {
            Text(verbatim: L10n.string("settings.storage"))
                .font(.hashiya(.stateTitle))
                .foregroundStyle(HashiyaColors.onSurface)
                .textCase(nil)
        }
    }

    private var privacySection: some View {
        Section {
            privacyToggle(
                titleKey: "settings.crashReports", footerKey: "settings.crashReportsFooter", identifier: "settings.crashReports",
                isOn: Binding(get: { viewModel.crashReportsEnabled }, set: { viewModel.setCrashReportsEnabled($0) })
            )
            privacyToggle(
                titleKey: "settings.analytics", footerKey: "settings.analyticsFooter", identifier: "settings.analytics",
                isOn: Binding(get: { viewModel.analyticsEnabled }, set: { viewModel.setAnalyticsEnabled($0) })
            )
            Button {
                openURL(Self.privacyPolicyURL)
            } label: {
                HStack {
                    Text(verbatim: L10n.string("settings.privacyPolicy")).font(.hashiya(.body)).foregroundStyle(HashiyaColors.onSurface)
                    Spacer()
                    Image(systemName: "arrow.up.forward.app").foregroundStyle(HashiyaColors.primary)
                }
            }
            .accessibilityIdentifier("settings.privacyPolicy")
        } header: {
            Text(verbatim: L10n.string("settings.privacySection"))
                .font(.hashiya(.stateTitle))
                .foregroundStyle(HashiyaColors.onSurface)
                .textCase(nil)
        }
    }

    private func privacyToggle(titleKey: String, footerKey: String, identifier: String, isOn: Binding<Bool>) -> some View {
        Toggle(isOn: isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: L10n.string(titleKey)).font(.hashiya(.body)).foregroundStyle(HashiyaColors.onSurface)
                Text(verbatim: L10n.string(footerKey)).font(.hashiya(.meta)).foregroundStyle(HashiyaColors.onSurfaceVariant)
            }
        }
        .tint(HashiyaColors.primary)
        .accessibilityIdentifier(identifier)
    }

    /// The published policy; Arabic opens its Arabic half.
    private static var privacyPolicyURL: URL {
        URL(string: "https://fadyfouad.github.io/Hashiya-Privacy-Policy/" + (HashiyaLanguage.isArabic ? "#ar" : ""))!
    }

    private var languageSection: some View {
        Section {
            Button {
                if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
            } label: {
                HStack {
                    Text(verbatim: languageName)
                        .font(.hashiya(.body))
                        .foregroundStyle(HashiyaColors.onSurface)
                    Spacer()
                    Image(systemName: "arrow.up.forward.app")
                        .foregroundStyle(HashiyaColors.primary)
                }
            }
        } header: {
            Text(verbatim: L10n.string("settings.languageSection"))
                .font(.hashiya(.stateTitle))
                .foregroundStyle(HashiyaColors.onSurface)
                .textCase(nil)
        } footer: {
            Text(verbatim: L10n.string("settings.languageFooter"))
                .font(.hashiya(.meta))
        }
    }

    /// The app language's own name: "English", "العربية".
    private var languageName: String {
        let code = HashiyaLanguage.code
        return Locale(identifier: code).localizedString(forLanguageCode: code) ?? code
    }
}

/// Restore with one view model for as long as it is shown: the destination is rebuilt whenever Settings re-renders, and a
/// new view model would open the file again and lose the progress.
private struct RestoreDestination: View {
    @State private var viewModel: RestoreViewModel
    let onDone: () -> Void
    let onApplyingChange: (Bool) -> Void

    init(source: URL, makeViewModel: (URL) -> RestoreViewModel, onDone: @escaping () -> Void, onApplyingChange: @escaping (Bool) -> Void) {
        _viewModel = State(initialValue: makeViewModel(source))
        self.onDone = onDone
        self.onApplyingChange = onApplyingChange
    }

    var body: some View {
        RestoreView(viewModel: viewModel, onDone: onDone, onApplyingChange: onApplyingChange)
    }
}
