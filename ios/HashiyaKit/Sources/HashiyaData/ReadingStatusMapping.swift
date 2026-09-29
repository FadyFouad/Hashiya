import HashiyaModel

extension ReadingStatus {
    /// The value stored in `papers.reading_status`. Fixed strings, so renaming a case never changes stored data.
    var storedValue: String {
        switch self {
        case .toRead: "to_read"
        case .reading: "reading"
        case .read: "read"
        }
    }

    /// Reads a stored value back; anything unknown is To read.
    init(stored: String) {
        self = Self.allCases.first { $0.storedValue == stored } ?? .toRead
    }
}
