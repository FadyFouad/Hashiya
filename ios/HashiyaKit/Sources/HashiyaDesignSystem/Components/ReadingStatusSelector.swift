import HashiyaModel
import SwiftUI

/// A status's name: "To read", "Reading", "Read". The one place statuses get their names (badge, menu, chips, selector).
@MainActor
public func readingStatusLabel(_ status: ReadingStatus) -> String {
    switch status {
    case .toRead: L10n.string("status.toRead")
    case .reading: L10n.string("status.reading")
    case .read: L10n.string("status.read")
    }
}

/// To read · Reading · Read as a segmented control. Choosing another status calls `onChange`; the control
/// always shows `status`, so it reflects what is stored.
public struct ReadingStatusSelector: View {
    private let status: ReadingStatus
    private let onChange: (ReadingStatus) -> Void

    public init(status: ReadingStatus, onChange: @escaping (ReadingStatus) -> Void) {
        self.status = status
        self.onChange = onChange
    }

    var selection: Binding<ReadingStatus> {
        Binding(get: { status }, set: { newValue in
            if newValue != status { onChange(newValue) }
        })
    }

    public var body: some View {
        Picker(selection: selection) {
            ForEach(ReadingStatus.allCases, id: \.self) { status in
                Text(verbatim: readingStatusLabel(status)).tag(status)
            }
        } label: {
            EmptyView()
        }
        .pickerStyle(.segmented)
    }
}
