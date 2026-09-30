import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// Sort, Year and Open access, in a horizontally scrolling row.
struct FilterChips: View {
    let query: SearchQuery
    let onSort: (SearchSort) -> Void
    let onYears: (YearFilter) -> Void
    let onCustomRange: () -> Void
    let onOpenAccess: (Bool) -> Void

    static let yearPresets = [2024, 2020, 2015]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HashiyaGlassGroup(spacing: 8) {
                HStack(spacing: 8) {
                    Menu {
                        ForEach(SearchSort.allCases, id: \.self) { sort in
                            Button {
                                onSort(sort)
                            } label: {
                                menuLabel(L10n.sortLabel(sort), checked: query.sort == sort)
                            }
                        }
                    } label: {
                        ChipLabel(text: L10n.sortLabel(query.sort), isSelected: query.sort != .relevance, trailingChevron: true)
                    }
                    Menu {
                        Button {
                            onYears(.anyTime)
                        } label: {
                            menuLabel(L10n.string("search.yearAny"), checked: query.years == .anyTime)
                        }
                        ForEach(Self.yearPresets, id: \.self) { year in
                            Button {
                                onYears(.since(year))
                            } label: {
                                menuLabel(L10n.yearLabel(.since(year)), checked: query.years == .since(year))
                            }
                        }
                        Button(action: onCustomRange) {
                            menuLabel(L10n.string("search.yearCustom"), checked: isCustomRange)
                        }
                    } label: {
                        ChipLabel(text: L10n.yearLabel(query.years), isSelected: query.years != .anyTime, trailingChevron: true)
                    }
                    Button {
                        onOpenAccess(!query.openAccessOnly)
                    } label: {
                        ChipLabel(text: L10n.string("search.openAccess"), isSelected: query.openAccessOnly, leadingCheckmark: query.openAccessOnly)
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(query.openAccessOnly ? .isSelected : [])
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
        }
    }

    private var isCustomRange: Bool {
        if case .between = query.years { return true }
        return false
    }

    @ViewBuilder
    private func menuLabel(_ text: String, checked: Bool) -> some View {
        if checked {
            Label {
                Text(verbatim: text)
            } icon: {
                Image(systemName: "checkmark")
            }
        } else {
            Text(verbatim: text)
        }
    }
}

/// A chip: glass on iOS 26 (tinted when selected); before, outlined or filled with the primary container colour.
struct ChipLabel: View {
    let text: String
    let isSelected: Bool
    var leadingCheckmark = false
    var trailingChevron = false

    var body: some View {
        HStack(spacing: 4) {
            if leadingCheckmark {
                Image(systemName: "checkmark").font(.system(size: 11, weight: .semibold))
            }
            Text(verbatim: text).font(.hashiya(.label))
            if trailingChevron {
                Image(systemName: "chevron.down").font(.system(size: 10, weight: .semibold))
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .hashiyaChip(isSelected: isSelected)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
