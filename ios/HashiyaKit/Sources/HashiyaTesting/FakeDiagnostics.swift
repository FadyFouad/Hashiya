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
    public func setKey(_ key: CrashKey, _ value: String) { state.withLock { $0.keys[key] = value } }
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
    public func setProperty(_ property: AnalyticsProperty, _ value: String) { state.withLock { $0.properties[property] = value } }
    public func setEnabled(_ enabled: Bool) { state.withLock { $0.enabled.append(enabled) } }
}

extension Diagnostics {
    /// Fakes for both, not live.
    public static func fake(crash: FakeCrashReporting = FakeCrashReporting(), analytics: FakeAnalytics = FakeAnalytics()) -> Diagnostics {
        Diagnostics(crash: crash, analytics: analytics, isLive: false)
    }
}
