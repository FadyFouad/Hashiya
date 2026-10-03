import SwiftUI

/// A detail pane's placeholder while nothing is picked in the list beside it.
public struct NoSelectionView: View {
    private let message: String

    public init(message: String) {
        self.message = message
    }

    public var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "books.vertical")
                .font(.system(size: 40))
                .foregroundStyle(HashiyaColors.primary)
                .accessibilityHidden(true)
            Text(verbatim: L10n.string("designsystem.noPaperSelected"))
                .font(.hashiya(.stateTitle))
                .foregroundStyle(HashiyaColors.onSurface)
            Text(verbatim: message)
                .font(.hashiya(.body))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(HashiyaColors.surface)
        .accessibilityElement(children: .combine)
    }
}

public extension NoSelectionView {
    /// The Library's detail pane: details and notes appear here.
    static var library: NoSelectionView { NoSelectionView(message: L10n.string("designsystem.pickPaperMessage")) }
    /// Search's preview pane: the abstract appears here.
    static var search: NoSelectionView { NoSelectionView(message: L10n.string("designsystem.pickResultMessage")) }
}
