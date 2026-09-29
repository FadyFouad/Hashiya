@testable import HashiyaData
import HashiyaModel
import Testing

struct ReadingStatusMappingTests {
    /// These strings are stored on the user's phone: renaming a case must never change them.
    @Test func storedValuesAreFixed() {
        #expect(ReadingStatus.allCases.map(\.storedValue) == ["to_read", "reading", "read"])
    }

    @Test func storedValuesReadBack() {
        for status in ReadingStatus.allCases {
            #expect(ReadingStatus(stored: status.storedValue) == status)
        }
    }

    @Test(arguments: ["archived", "", "ToRead", "toRead"])
    func anUnknownStoredValueReadsAsToRead(stored: String) {
        #expect(ReadingStatus(stored: stored) == .toRead)
    }
}
