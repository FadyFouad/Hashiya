import SwiftUI

/// The tonal button used for Save on a card: secondary container fill.
public struct TonalButtonStyle: ButtonStyle {
    public init() {}

    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.hashiya(.label))
            .foregroundStyle(HashiyaColors.onSecondaryContainer)
            .padding(.horizontal, 14)
            .padding(.vertical, 6)
            .background(Capsule().fill(HashiyaColors.secondaryContainer))
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}
