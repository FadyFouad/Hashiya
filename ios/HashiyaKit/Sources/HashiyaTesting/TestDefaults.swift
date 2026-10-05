import Foundation
import os

/// A fresh, empty `UserDefaults` suite per call, so tests never share or leak stored values.
public enum TestDefaults {
    public static func make() -> UserDefaults {
        let name = "tests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defaults.removePersistentDomain(forName: name)
        return defaults
    }
}

/// A settable clock: pass `{ clock.date }` where a type takes `now: @Sendable () -> Date`.
public final class TestClock: Sendable {
    private let state: OSAllocatedUnfairLock<Date>

    public init(_ date: Date) {
        state = OSAllocatedUnfairLock(initialState: date)
    }

    /// "2026-10-05T21:30:00Z".
    public convenience init(_ iso: String) {
        self.init(ISO8601DateFormatter().date(from: iso)!)
    }

    public var date: Date { state.withLock { $0 } }

    public func advance(by seconds: TimeInterval) {
        state.withLock { $0 += seconds }
    }

    public func set(_ date: Date) {
        state.withLock { $0 = date }
    }
}
