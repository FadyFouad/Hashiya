import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// All · To read · Reading · Read with their counts for the current search, in a horizontally scrolling row.
/// One is selected at a time: it is filled, carries a check and is announced as selected.
struct LibraryFilterChips: View {
    let selected: ReadingStatus?
    let counts: [ReadingStatus: Int]
    let onSelect: (ReadingStatus?) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HashiyaGlassGroup(spacing: 8) {
                HStack(spacing: 8) {
                    chip(nil, label: L10n.string("library.filterAll"), count: counts.values.reduce(0, +))
                    ForEach(ReadingStatus.allCases, id: \.self) { status in
                        chip(status, label: readingStatusLabel(status), count: counts[status] ?? 0)
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
        }
    }

    private func chip(_ status: ReadingStatus?, label: String, count: Int) -> some View {
        let isSelected = status == selected
        return Button {
            onSelect(status)
        } label: {
            HStack(spacing: 4) {
                if isSelected {
                    Image(systemName: "checkmark").font(.system(size: 11, weight: .semibold))
                }
                Text(verbatim: L10n.filterCount(label, count)).font(.hashiya(.label))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .hashiyaChip(isSelected: isSelected)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
