@testable import FeatureSearch
import Foundation
import Testing

struct YearRangeTests {
    private let date = Date(timeIntervalSince1970: 1_790_000_000) // September 2026

    @Test(arguments: [Calendar.Identifier.gregorian, .islamicUmmAlQura, .islamicCivil, .buddhist, .persian, .japanese])
    func currentYearIsGregorianWhateverTheDeviceCalendar(identifier: Calendar.Identifier) {
        var deviceCalendar = Calendar(identifier: identifier)
        deviceCalendar.timeZone = TimeZone(identifier: "UTC")!
        // The device calendar disagrees with the Gregorian year, so the default must not use it.
        let year = YearRangeSheet.currentGregorianYear(date)
        #expect(year == 2026)
        let years = YearRangeSheet.allowedYears(currentYear: year)
        #expect(years.first == 1900)
        #expect(years.last == 2026)
        #expect(!years.isEmpty)
        if identifier != .gregorian, identifier != .japanese {
            #expect(deviceCalendar.component(.year, from: date) != 2026)
        }
    }

    @Test func allowedYearsNeverTrapsOnATooSmallYear() {
        #expect(YearRangeSheet.allowedYears(currentYear: 1447) == [1900])
    }
}
