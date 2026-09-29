import SwiftUI

/// A small label: "Open access" or "In library".
public struct StatusBadge: View {
    public enum Kind: Sendable {
        case openAccess, inLibrary
    }

    private let text: String
    private let kind: Kind

    public init(text: String, kind: Kind) {
        self.text = text
        self.kind = kind
    }

    public var body: some View {
        Text(verbatim: text)
            .font(.hashiya(.badge))
            .foregroundStyle(kind == .openAccess ? HashiyaColors.onSecondaryContainer : HashiyaColors.onSurface)
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(kind == .openAccess ? HashiyaColors.secondaryContainer : HashiyaColors.surfaceContainerHigh)
            )
    }
}
