import HashiyaDesignSystem
import SwiftUI
import UIKit

/// Settings → About: Send feedback, Rate Hashiya and the version.
public struct AboutSection: View {
    private let version: AppVersion
    private let onAddressCopied: () -> Void
    @Environment(\.openURL) private var openURL

    public init(version: AppVersion = .current, onAddressCopied: @escaping () -> Void = {}) {
        self.version = version
        self.onAddressCopied = onAddressCopied
    }

    public var body: some View {
        Section {
            Button {
                let url = Feedback.mailURL(
                    subject: L10n.string("settings.feedbackSubject"), version: version, system: Feedback.system,
                    model: Feedback.deviceModel, language: HashiyaLanguage.code
                )
                openURL(url) { accepted in
                    // No mail app: the address goes to the clipboard instead.
                    if !accepted {
                        UIPasteboard.general.string = Feedback.address
                        onAddressCopied()
                    }
                }
            } label: {
                row("settings.sendFeedback", systemImage: "envelope")
            }
            .accessibilityIdentifier("settings.sendFeedback")
            Button {
                openURL(Feedback.rateURL)
            } label: {
                row("settings.rate", systemImage: "star")
            }
            .accessibilityIdentifier("settings.rate")
            Text(verbatim: L10n.format("settings.version", version.label))
                .font(.hashiya(.meta))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .accessibilityIdentifier("settings.version")
        } header: {
            Text(verbatim: L10n.string("settings.about"))
                .font(.hashiya(.stateTitle))
                .foregroundStyle(HashiyaColors.onSurface)
                .textCase(nil)
        }
    }

    private func row(_ key: String, systemImage: String) -> some View {
        HStack {
            Text(verbatim: L10n.string(key)).font(.hashiya(.body)).foregroundStyle(HashiyaColors.onSurface)
            Spacer()
            Image(systemName: systemImage).foregroundStyle(HashiyaColors.primary)
        }
    }
}
