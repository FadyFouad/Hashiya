import Foundation
import HashiyaNetwork
import HashiyaTesting
import Testing

struct OpenAlexQuotaTests {
    private let defaults = TestDefaults.make()
    private let clock = TestClock("2026-10-05T20:00:00Z")

    private func quota(builtInKey: Bool = true, limits: OpenAlexLimits = .defaults) -> OpenAlexQuota {
        OpenAlexLimitsStore(defaults: defaults).save(limits)
        return OpenAlexQuota(defaults: defaults, hasBuiltInKey: builtInKey, now: { [clock] in clock.date })
    }

    private func date(_ iso: String) -> Date { ISO8601DateFormatter().date(from: iso)! }

    @Test func startsOnTheSharedRoute() {
        let quota = quota()
        #expect(quota.meteredRoute() == .shared)
        #expect(quota.lookupRoute() == .shared)
    }

    @Test func withoutABuiltInKeyOrProxyEverythingIsKeyless() {
        let quota = quota(builtInKey: false)
        #expect(quota.meteredRoute() == .keyless)
        #expect(quota.lookupRoute() == .keyless)
    }

    @Test func aProxyMakesTheSharedRouteWithoutABuiltInKey() {
        let quota = quota(builtInKey: false, limits: OpenAlexLimits(dailyDeviceCalls: 60, maxPagesPerQuery: 8, baseURL: URL(string: "https://proxy.example")))
        #expect(quota.meteredRoute() == .shared)
    }

    @Test func theCapMovesMeteredCallsToKeylessButNotLookups() {
        let quota = quota(limits: OpenAlexLimits(dailyDeviceCalls: 2, maxPagesPerQuery: 8, baseURL: nil))
        quota.recordSharedCall()
        #expect(quota.meteredRoute() == .shared)
        quota.recordSharedCall()
        #expect(quota.meteredRoute() == .keyless)
        #expect(quota.lookupRoute() == .shared)
    }

    @Test func aZeroCapTurnsTheSharedRouteOffForMeteredCalls() {
        let quota = quota(limits: OpenAlexLimits(dailyDeviceCalls: 0, maxPagesPerQuery: 8, baseURL: nil))
        #expect(quota.meteredRoute() == .keyless)
        #expect(quota.lookupRoute() == .shared)
    }

    @Test func theCountStartsAgainAtUTCMidnightNotLocalMidnight() {
        let quota = quota(limits: OpenAlexLimits(dailyDeviceCalls: 1, maxPagesPerQuery: 8, baseURL: nil))
        quota.recordSharedCall()
        // 00:30 in UTC+3 is still the same UTC day.
        clock.set(date("2026-10-05T21:30:00Z"))
        #expect(quota.meteredRoute() == .keyless)
        clock.set(date("2026-10-06T00:00:01Z"))
        #expect(quota.meteredRoute() == .shared)
    }

    @Test func aUsedUpSharedBudgetSendsEverythingKeylessUntilItsReset() {
        let quota = quota()
        quota.markUsedUp(.shared, resetIn: 600)
        #expect(quota.meteredRoute() == .keyless)
        #expect(quota.lookupRoute() == .keyless)
        clock.advance(by: 601)
        #expect(quota.meteredRoute() == .shared)
    }

    @Test func bothBudgetsUsedUpMeansOutUntilTheEarliestReset() {
        let quota = quota()
        quota.markUsedUp(.shared, resetIn: 3600)
        quota.markUsedUp(.keyless, resetIn: 1800)
        #expect(quota.meteredRoute() == nil)
        #expect(quota.lookupRoute() == .keyless)
        #expect(quota.nextAvailable() == clock.date.addingTimeInterval(1800))
    }

    @Test func theCapCountsAsUsedUpUntilUTCMidnightForNextAvailable() {
        let quota = quota(limits: OpenAlexLimits(dailyDeviceCalls: 1, maxPagesPerQuery: 8, baseURL: nil))
        quota.recordSharedCall()
        quota.markUsedUp(.keyless, resetIn: 6 * 3600)
        #expect(quota.meteredRoute() == nil)
        #expect(quota.nextAvailable() == date("2026-10-06T00:00:00Z"))
    }

    @Test(arguments: [nil, -5, 3 * 86_400] as [TimeInterval?])
    func aMissingOrUnbelievableResetMeansNextUTCMidnight(reset: TimeInterval?) {
        let quota = quota()
        quota.markUsedUp(.shared, resetIn: reset)
        quota.markUsedUp(.keyless, resetIn: reset)
        #expect(quota.nextAvailable() == date("2026-10-06T00:00:00Z"))
    }

    @Test func marksAndCountsSurviveANewInstance() {
        let first = quota(limits: OpenAlexLimits(dailyDeviceCalls: 1, maxPagesPerQuery: 8, baseURL: nil))
        first.recordSharedCall()
        first.markUsedUp(.keyless, resetIn: 600)
        let second = OpenAlexQuota(defaults: defaults, hasBuiltInKey: true, now: { [clock] in clock.date })
        #expect(second.meteredRoute() == nil)
    }

    @Test func theRouteAfterSharedIsKeylessAndAfterKeylessNothing() {
        let quota = quota()
        #expect(quota.route(after: .shared, metered: true) == .keyless)
        #expect(quota.route(after: .keyless, metered: true) == nil)
        quota.markUsedUp(.keyless, resetIn: 600)
        #expect(quota.route(after: .shared, metered: true) == nil)
        #expect(quota.route(after: .shared, metered: false) == .keyless)
        #expect(quota.route(after: .keyless, metered: false) == nil)
    }

    @Test func newLimitsApplyToTheNextRoute() {
        let quota = quota()
        quota.recordSharedCall()
        OpenAlexLimitsStore(defaults: defaults).save(OpenAlexLimits(dailyDeviceCalls: 1, maxPagesPerQuery: 8, baseURL: nil))
        #expect(quota.meteredRoute() == .keyless)
    }
}
