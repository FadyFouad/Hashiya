import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// A row's status as a labelled pill that opens a menu of the three statuses (the current one checked).
/// Choosing another status calls `onChange` at once; choosing the current one does nothing.
struct ReadingStatusBadge: View {
    let status: ReadingStatus
    let onChange: (ReadingStatus) -> Void

    var body: some View {
        Menu {
            Picker(selection: Binding(get: { status }, set: { newValue in
                if newValue != status { onChange(newValue) }
            })) {
                ForEach(ReadingStatus.allCases, id: \.self) { status in
                    Text(verbatim: readingStatusLabel(status)).tag(status)
                }
            } label: {
                EmptyView()
            }
        } label: {
            ReadingStatusPill(status: status)
        }
        .accessibilityLabel(Text(verbatim: L10n.statusBadgeDescription(status)))
    }
}

/// iOS 26: a glass pill, tinted with the primary colour for Reading. Before: To read outlined, Reading filled with
/// the primary container, Read filled. Read always adds a check, and the label always names the status, so it is
/// never told by colour alone.
struct ReadingStatusPill: View {
    let status: ReadingStatus

    var body: some View {
        HStack(spacing: 4) {
            if status == .read {
                Image(systemName: "checkmark")
                    .font(.system(size: 14 * 0.8, weight: .semibold))
                    .frame(width: 14, height: 14)
            }
            Text(verbatim: readingStatusLabel(status))
                .font(.hashiya(.label))
                .lineLimit(1)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .modifier(PillSurface(status: status))
        .fixedSize()
    }
}

private struct PillSurface: ViewModifier {
    let status: ReadingStatus

    /// A pill: just under half the default height. (A `Capsule`'s outline renders with seams in layer snapshots.)
    private static let shape = RoundedRectangle(cornerRadius: 11, style: .continuous)

    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content
                .foregroundStyle(status == .reading ? HashiyaColors.onPrimary : HashiyaColors.onSurface)
                .glassEffect(status == .reading ? .regular.tint(HashiyaColors.primary).interactive() : .regular.interactive(), in: .capsule)
                .contentShape(.capsule)
        } else {
            content
                .foregroundStyle(foreground)
                .background {
                    if status == .toRead {
                        Self.shape.strokeBorder(HashiyaColors.outline, lineWidth: 1)
                    } else {
                        Self.shape.fill(fill)
                    }
                }
                .contentShape(Self.shape)
        }
    }

    private var foreground: Color {
        switch status {
        case .toRead: HashiyaColors.onSurfaceVariant
        case .reading: HashiyaColors.onPrimaryContainer
        case .read: HashiyaColors.onSurface
        }
    }

    private var fill: Color {
        status == .reading ? HashiyaColors.primaryContainer : HashiyaColors.surfaceContainerHighest
    }
}
