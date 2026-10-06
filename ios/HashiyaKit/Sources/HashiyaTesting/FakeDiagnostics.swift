import HashiyaDiagnostics
import os

/// Records every call, for tests.
public final class FakeCrashReporting: CrashReporting {
    private struct State {
        var enabled: [Bool] = []
        var keys: [CrashKey: String] = [:]
        var records: [ReportedError] = []
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    public init() {}

    public var enabledCalls: [Bool] { state.withLock { $0.enabled } }
    public var keys: [CrashKey: String] { state.withLock { $0.keys } }
    public var records: [ReportedError] { state.withLock { $0.records } }

    public func setEnabled(_ enabled: Bool) { state.withLock { $0.enabled.append(enabled) } }
    public func setKey(_ key: CrashKey, _ value: some ClosedValue) { state.withLock { $0.keys[key] = value.rawValue } }
    public func record(_ error: any Error, site: CrashSite) {
        let reported = ReportedError(error: error, site: site)
        state.withLock { $0.records.append(reported) }
    }
}

/// Records every call, for tests.
public final class FakeAnalytics: AnalyticsTracking {
    private struct State {
        var events: [AnalyticsEvent] = []
        var properties: [AnalyticsProperty: String] = [:]
        var enabled: [Bool] = []
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    public init() {}

    public var events: [AnalyticsEvent] { state.withLock { $0.events } }
    public var properties: [AnalyticsProperty: String] { state.withLock { $0.properties } }
    public var enabledCalls: [Bool] { state.withLock { $0.enabled } }

    public func log(_ event: AnalyticsEvent) { state.withLock { $0.events.append(event) } }
    public func setProperty(_ property: AnalyticsProperty, _ value: some ClosedValue) { state.withLock { $0.properties[property] = value.rawValue } }
    public func setEnabled(_ enabled: Bool) { state.withLock { $0.enabled.append(enabled) } }
}

extension Diagnostics {
    /// Fakes for both, not live.
    public static func fake(
        crash: FakeCrashReporting = FakeCrashReporting(),
        analytics: FakeAnalytics = FakeAnalytics(),
        review: any ReviewPrompting = NoReviewPrompting()
    ) -> Diagnostics {
        Diagnostics(crash: crash, analytics: analytics, isLive: false, review: review)
    }
}

/// Counts every call, for tests.
public final class FakeReviewPrompting: ReviewPrompting {
    private struct State {
        var opened = 0
        var saves = 0
        var exports = 0
        var asks = 0
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    public init() {}

    public var opened: Int { state.withLock { $0.opened } }
    public var saves: Int { state.withLock { $0.saves } }
    public var exports: Int { state.withLock { $0.exports } }
    public var asks: Int { state.withLock { $0.asks } }

    public func markOpened() { state.withLock { $0.opened += 1 } }
    public func recordSave() { state.withLock { $0.saves += 1 } }
    public func recordExport() { state.withLock { $0.exports += 1 } }
    public func askIfDue() { state.withLock { $0.asks += 1 } }
}
