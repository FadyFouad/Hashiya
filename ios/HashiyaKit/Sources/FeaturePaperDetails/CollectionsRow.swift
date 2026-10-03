import HashiyaDesignSystem
import SwiftUI

/// "Collections" with the paper's collections as chips, or "Not in any collection": the first row of the Details
/// group. The whole row is one button that opens the checklist.
struct CollectionsRow: View {
    let names: [String]
    let shape: UnevenRoundedRectangle
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .center, spacing: 16) {
                Image(systemName: "folder")
                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    .frame(width: GroupedRows.iconWidth)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 4) {
                    GroupedRowLabel(text: L10n.string("details.collections"))
                    if names.isEmpty {
                        Text(verbatim: L10n.string("details.noCollections"))
                            .font(.hashiya(.body))
                            .foregroundStyle(HashiyaColors.onSurface)
                    } else {
                        ChipFlow(spacing: 8) {
                            ForEach(names, id: \.self) { name in
                                CollectionChip(name: name)
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.forward")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    .accessibilityHidden(true)
            }
            .padding(16)
            .frame(minHeight: 72)
            .background(shape.fill(HashiyaColors.surfaceContainer))
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        // VoiceOver reads "Collections" and the names (or "Not in any collection") as one button.
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("details.collections")
    }
}

/// One collection's name. Plain: tapping it does nothing of its own; the row is the button.
private struct CollectionChip: View {
    let name: String

    var body: some View {
        Text(verbatim: name)
            .font(.hashiya(.badge))
            .lineLimit(1)
            .foregroundStyle(HashiyaColors.onSecondaryContainer)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(Capsule().fill(HashiyaColors.secondaryContainer))
    }
}

/// Lays its children out in rows, wrapping to the next row when one doesn't fit. SwiftUI mirrors a custom layout
/// right to left, so in Arabic the first chip sits on the right.
struct ChipFlow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        let rows = arrange(subviews, width: width)
        let height = rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(rows.count - 1, 0))
        let widest = rows.map(\.width).max() ?? 0
        return CGSize(width: width.isFinite ? width : widest, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(subviews, width: bounds.width) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(ProposedViewSize(width: bounds.width, height: nil))
                let width = min(size.width, bounds.width)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(width: width, height: size.height))
                x += width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func arrange(_ subviews: Subviews, width: CGFloat) -> [Row] {
        var rows: [Row] = []
        var row = Row()
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(ProposedViewSize(width: width, height: nil))
            let itemWidth = min(size.width, width)
            if !row.indices.isEmpty, row.width + spacing + itemWidth > width {
                rows.append(row)
                row = Row()
            }
            row.width += (row.indices.isEmpty ? 0 : spacing) + itemWidth
            row.height = max(row.height, size.height)
            row.indices.append(index)
        }
        if !row.indices.isEmpty { rows.append(row) }
        return rows
    }
}
