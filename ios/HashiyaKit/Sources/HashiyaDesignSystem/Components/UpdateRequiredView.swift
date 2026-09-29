import SwiftUI

/// Shown instead of the whole app when this build is no longer supported. `onUpdate` opens the store page.
public struct UpdateRequiredView: View {
    private let onUpdate: () -> Void

    public init(onUpdate: @escaping () -> Void) {
        self.onUpdate = onUpdate
    }

    public var body: some View {
        EmptyStateView(
            icon: "arrow.down.app",
            title: L10n.string("designsystem.updateRequired.title"),
            message: L10n.string("designsystem.updateRequired.message"),
            actionTitle: L10n.string("designsystem.updateRequired.action"),
            action: onUpdate
        )
        .background(HashiyaColors.surface.ignoresSafeArea())
    }
}
