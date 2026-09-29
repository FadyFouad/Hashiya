import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// Custom range: two year wheels (1900 through the current year). Apply is disabled while From is after To.
struct YearRangeSheet: View {
    let onApply: (YearFilter) -> Void
    let years: [Int]

    @State private var from: Int
    @State private var to: Int
    @Environment(\.dismiss) private var dismiss

    init(current: YearFilter, currentYear: Int = YearRangeSheet.currentGregorianYear(), onApply: @escaping (YearFilter) -> Void) {
        self.onApply = onApply
        years = Self.allowedYears(currentYear: currentYear)
        if case let .between(from, to) = current {
            _from = State(initialValue: from)
            _to = State(initialValue: to)
        } else {
            _from = State(initialValue: currentYear)
            _to = State(initialValue: currentYear)
        }
    }

    /// OpenAlex years are Gregorian, so the device calendar (Islamic, Buddhist, ...) must not decide the year.
    static func currentGregorianYear(_ date: Date = Date()) -> Int {
        Calendar(identifier: .gregorian).component(.year, from: date)
    }

    /// 1900 through `currentYear`; never an invalid range.
    static func allowedYears(currentYear: Int) -> [Int] {
        Array(1900...max(1900, currentYear))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 8) {
                HStack(spacing: 16) {
                    wheel(L10n.string("search.yearFrom"), selection: $from)
                    wheel(L10n.string("search.yearTo"), selection: $to)
                }
                if from > to {
                    Text(verbatim: L10n.string("search.yearErrorOrder"))
                        .font(.hashiya(.meta))
                        .foregroundStyle(HashiyaColors.error)
                        .multilineTextAlignment(.center)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(HashiyaColors.surface)
            .navigationTitle(Text(verbatim: L10n.string("search.yearDialogTitle")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        dismiss()
                    } label: {
                        Text(verbatim: L10n.string("search.cancel"))
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        if let range = YearFilter.between(from, to) {
                            onApply(range)
                            dismiss()
                        }
                    } label: {
                        Text(verbatim: L10n.string("search.apply"))
                    }
                    .disabled(from > to)
                }
            }
        }
    }

    private func wheel(_ label: String, selection: Binding<Int>) -> some View {
        VStack(spacing: 4) {
            Text(verbatim: label)
                .font(.hashiya(.label))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
            Picker(selection: selection) {
                ForEach(years, id: \.self) { year in
                    Text(verbatim: PaperFormat.year(year)).tag(year)
                }
            } label: {
                Text(verbatim: label)
            }
            .pickerStyle(.wheel)
        }
        .frame(maxWidth: .infinity)
    }
}
