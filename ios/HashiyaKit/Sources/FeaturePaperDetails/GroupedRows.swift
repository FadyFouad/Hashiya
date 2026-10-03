import HashiyaDesignSystem
import SwiftUI

/// Details groups Collections, PDF and DOI into one list, as on Android: rows on surfaceContainer, 2pt apart, the
/// group's outer corners rounded 20pt and the joins between rows 4pt.
enum GroupedRows {
    static let gap: CGFloat = 2

    /// Every row's icon sits in a box this wide, so the labels line up whatever the symbol's own width.
    static let iconWidth: CGFloat = 28

    static func shape(index: Int, count: Int) -> UnevenRoundedRectangle {
        let top: CGFloat = index == 0 ? 20 : 4
        let bottom: CGFloat = index == count - 1 ? 20 : 4
        return UnevenRoundedRectangle(topLeadingRadius: top, bottomLeadingRadius: bottom, bottomTrailingRadius: bottom, topTrailingRadius: top)
    }
}

/// A grouped row's small label ("Collections", "PDF", "DOI") over its value.
struct GroupedRowLabel: View {
    let text: String

    var body: some View {
        Text(verbatim: text)
            .font(.hashiya(.label))
            .foregroundStyle(HashiyaColors.onSurfaceVariant)
    }
}

/// The paper's DOI as the group's last row: label, the DOI on one line, an open mark. The whole row opens the DOI page.
struct DoiRow: View {
    let doi: String
    let shape: UnevenRoundedRectangle
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .center, spacing: 16) {
                Image(systemName: "link")
                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    .frame(width: GroupedRows.iconWidth)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    GroupedRowLabel(text: L10n.string("details.doi"))
                    // A DOI is Latin text: left to right even in Arabic, on the reading side of the row.
                    Text(verbatim: doi)
                        .font(.hashiya(.body))
                        .foregroundStyle(HashiyaColors.onSurface)
                        .lineLimit(1)
                        .truncationMode(.tail)
                        .environment(\.layoutDirection, .leftToRight)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "arrow.up.forward.square")
                    .foregroundStyle(HashiyaColors.primary)
                    .accessibilityHidden(true)
            }
            .padding(16)
            .frame(minHeight: 72)
            .background(shape.fill(HashiyaColors.surfaceContainer))
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "\(L10n.string("details.doi")), \(doi)"))
        .accessibilityHint(Text(verbatim: DesignSystemStrings.openDOI))
        .accessibilityAddTraits(.isLink)
        .accessibilityIdentifier("details.doi")
    }
}
