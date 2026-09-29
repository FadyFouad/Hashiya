import SwiftUI

/// A message at the bottom of the screen, with an optional action. Screens remove it after 4 s.
public struct HashiyaBanner: View {
    /// How long a banner stays on screen.
    public static let duration: Duration = .seconds(4)

    private let text: String
    private let actionTitle: String?
    private let action: (() -> Void)?

    public init(text: String, actionTitle: String? = nil, action: (() -> Void)? = nil) {
        self.text = text
        self.actionTitle = actionTitle
        self.action = action
    }

    public var body: some View {
        HStack(spacing: 12) {
            Text(verbatim: text)
                .font(.hashiya(.body))
                .foregroundStyle(HashiyaColors.surface)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let actionTitle, let action {
                Button(action: action) {
                    Text(verbatim: actionTitle).font(.hashiya(.label))
                }
                .foregroundStyle(HashiyaColors.inversePrimary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 12).fill(HashiyaColors.onSurface))
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}
