import SwiftUI

/// Static card-shaped placeholder rows (not animated, so snapshots are stable).
public struct LoadingSkeleton: View {
    private let rows: Int

    public init(rows: Int = 4) {
        self.rows = rows
    }

    public var body: some View {
        VStack(spacing: 0) {
            ForEach(0..<rows, id: \.self) { _ in
                VStack(alignment: .leading, spacing: 8) {
                    bar(0.85)
                    bar(0.6)
                    bar(0.35)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 12)
                .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(HashiyaColors.outlineVariant, lineWidth: 1))
                .padding(.horizontal, 12)
                .padding(.vertical, 5)
            }
            Spacer(minLength: 0)
        }
        .accessibilityHidden(true)
    }

    private func bar(_ fraction: CGFloat) -> some View {
        Color.clear
            .frame(height: 10)
            .frame(maxWidth: .infinity)
            .overlay(alignment: .leading) {
                GeometryReader { proxy in
                    RoundedRectangle(cornerRadius: 4)
                        .fill(HashiyaColors.surfaceContainerHigh)
                        .frame(width: proxy.size.width * fraction)
                }
            }
    }
}
