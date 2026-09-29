import SwiftUI

/// A centred icon, title, optional message and optional action.
public struct EmptyStateView: View {
    private let icon: String
    private let title: String
    private let message: String?
    private let actionTitle: String?
    private let action: (() -> Void)?

    public init(icon: String, title: String, message: String? = nil, actionTitle: String? = nil, action: (() -> Void)? = nil) {
        self.icon = icon
        self.title = title
        self.message = message
        self.actionTitle = actionTitle
        self.action = action
    }

    public var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 40))
                .foregroundStyle(HashiyaColors.primary)
                .accessibilityHidden(true)
            Text(verbatim: title)
                .font(.hashiya(.stateTitle))
                .foregroundStyle(HashiyaColors.onSurface)
                .accessibilityAddTraits(.isHeader)
            if let message {
                Text(verbatim: message)
                    .font(.hashiya(.body))
                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
            }
            if let actionTitle, let action {
                Button(action: action) {
                    Text(verbatim: actionTitle)
                        .font(.hashiya(.label))
                        .foregroundStyle(HashiyaColors.onPrimary)
                }
                .buttonStyle(.borderedProminent)
                .tint(HashiyaColors.primary)
                .padding(.top, 4)
            }
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, 32)
        .padding(.vertical, 48)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// A first-page error: icon, title, message and one action (Retry or Open Settings).
public struct ErrorStateView: View {
    private let title: String
    private let message: String
    private let actionTitle: String
    private let action: () -> Void

    public init(title: String, message: String, actionTitle: String, action: @escaping () -> Void) {
        self.title = title
        self.message = message
        self.actionTitle = actionTitle
        self.action = action
    }

    public var body: some View {
        EmptyStateView(icon: "exclamationmark.circle", title: title, message: message, actionTitle: actionTitle, action: action)
    }
}
