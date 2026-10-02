import HashiyaDesignSystem
import HashiyaModel
import SwiftUI
import UIKit

/// The Settings sheet: the OpenAlex key, the space PDFs use and a Language row that opens iOS Settings.
public struct SettingsView: View {
    @Bindable private var viewModel: SettingsViewModel
    @State private var showsKey = false
    @State private var confirmingDelete = false
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    public init(viewModel: SettingsViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        NavigationStack {
            Form {
                apiKeySection
                storageSection
                languageSection
            }
            .scrollContentBackground(.hidden)
            .background(HashiyaColors.surface)
            .navigationTitle(Text(verbatim: L10n.string("settings.title")))
            .navigationBarTitleDisplayMode(.inline)
            .task { await viewModel.loadStorage() }
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

/// This target's strings.
@MainActor
enum L10n {
    static func string(_ key: String) -> String {
        HashiyaStrings.string(key, bundle: .module)
    }

    static func format(_ key: String, _ arguments: any CVarArg...) -> String {
        HashiyaStrings.format(key, bundle: .module, arguments)
    }

    /// "Downloaded PDFs · 2.4 MB · 3 files".
    static func downloadedPdfs(bytes: Int64, count: Int) -> String {
        format("settings.downloadedPdfs", Int64(count), PaperFormat.fileSize(bytes), PaperFormat.number(count))
    }

    /// "Attached PDFs · 1 MB · 1 file".
    static func attachedPdfs(bytes: Int64, count: Int) -> String {
        format("settings.attachedPdfs", Int64(count), PaperFormat.fileSize(bytes), PaperFormat.number(count))
    }

    /// The Delete downloaded PDFs confirmation.
    static func deleteDownloadedMessage(count: Int) -> String {
        format("settings.deleteDownloadedMessage", Int64(count), PaperFormat.number(count))
    }
}
