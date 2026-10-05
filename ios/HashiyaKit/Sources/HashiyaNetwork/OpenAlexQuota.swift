import Foundation
import os

/// How a request without the user's key goes out: the shared route (the built-in key, or the proxy in the limits) or
/// no key at all.
public enum OpenAlexRoute: Equatable, Sendable {
    case shared, keyless
}

/// Chooses the route for requests without a user key and remembers, in `defaults`: the metered calls sent on the
/// shared route today (by UTC day, as OpenAlex resets at midnight UTC), and until when OpenAlex said the shared and
/// keyless budgets are used up. Nothing here leaves the device. `UserDefaults` is thread-safe; the lock keeps each
/// read-and-update whole.
public final class OpenAlexQuota: @unchecked Sendable {
    enum Key {
        static let callsDay = "openAlex.sharedCallsDay"
        static let calls = "openAlex.sharedCalls"
        static let sharedUntil = "openAlex.sharedUsedUpUntil"
        static let keylessUntil = "openAlex.keylessUsedUpUntil"
    }

    private static let secondsPerDay: TimeInterval = 86_400

    private let defaults: UserDefaults
    private let limitsStore: OpenAlexLimitsStore
    private let hasBuiltInKey: Bool
    private let now: @Sendable () -> Date
    private let lock = OSAllocatedUnfairLock()

    public init(defaults: UserDefaults = .standard, hasBuiltInKey: Bool, now: @escaping @Sendable () -> Date = { Date() }) {
        self.defaults = defaults
        limitsStore = OpenAlexLimitsStore(defaults: defaults)
        self.hasBuiltInKey = hasBuiltInKey
        self.now = now
    }

    /// Read on every request, so limits fetched later apply to the next one.
    public var limits: OpenAlexLimits { limitsStore.limits }

    /// The route for a search or filter list: shared while under the cap and not used up, else keyless while that
    /// isn't used up, else nil (out).
    public func meteredRoute() -> OpenAlexRoute? {
        lock.withLock {
            let date = now()
            if sharedOpensForMetered(at: date) <= date { return .shared }
            return keylessUntil <= date ? .keyless : nil
        }
    }

    /// The route for a free lookup: shared unless it is used up (the cap counts metered calls only), else keyless.
    public func lookupRoute() -> OpenAlexRoute {
        lock.withLock { sharedConfigured && sharedUntil <= now() ? .shared : .keyless }
    }

    /// Where a request goes after `route` was used up or failed: keyless after shared (for metered calls only while
    /// keyless isn't used up), nothing after keyless.
    public func route(after route: OpenAlexRoute, metered: Bool) -> OpenAlexRoute? {
        guard route == .shared else { return nil }
        guard metered else { return .keyless }
        return lock.withLock { keylessUntil <= now() } ? .keyless : nil
    }

    /// One metered call sent on the shared route.
    public func recordSharedCall() {
        lock.withLock {
            let today = Self.utcDay(now())
            let calls = defaults.integer(forKey: Key.callsDay) == today ? defaults.integer(forKey: Key.calls) : 0
            defaults.set(today, forKey: Key.callsDay)
            defaults.set(calls + 1, forKey: Key.calls)
        }
    }

    /// OpenAlex said `route`'s budget is used up. `resetIn` is its `X-RateLimit-Reset`; nil, negative or more than
    /// two days means the next midnight UTC.
    public func markUsedUp(_ route: OpenAlexRoute, resetIn: TimeInterval?) {
        lock.withLock {
            let date = now()
            let until = resetIn.flatMap { (0...(2 * Self.secondsPerDay)).contains($0) ? date.addingTimeInterval($0) : nil }
                ?? Self.nextUTCMidnight(after: date)
            defaults.set(until.timeIntervalSince1970, forKey: route == .shared ? Key.sharedUntil : Key.keylessUntil)
        }
    }

    /// When a search can go out again: the earlier of the shared and keyless routes opening.
    public func nextAvailable() -> Date {
        lock.withLock {
            let date = now()
            let opens = [sharedOpensForMetered(at: date), keylessUntil].min() ?? date
            return max(opens, date)
        }
    }

    // MARK: Inside the lock

    private var sharedConfigured: Bool { hasBuiltInKey || limits.baseURL != nil }

    /// `.distantFuture` when the shared route is off for metered calls.
    private func sharedOpensForMetered(at date: Date) -> Date {
        let cap = limits.dailyDeviceCalls
        guard sharedConfigured, cap > 0 else { return .distantFuture }
        let today = Self.utcDay(date)
        let calls = defaults.integer(forKey: Key.callsDay) == today ? defaults.integer(forKey: Key.calls) : 0
        let capOpens = calls >= cap ? Self.nextUTCMidnight(after: date) : date
        return max(capOpens, sharedUntil)
    }

    private var sharedUntil: Date { Date(timeIntervalSince1970: defaults.double(forKey: Key.sharedUntil)) }
    private var keylessUntil: Date { Date(timeIntervalSince1970: defaults.double(forKey: Key.keylessUntil)) }

    private static func utcDay(_ date: Date) -> Int {
        Int((date.timeIntervalSince1970 / secondsPerDay).rounded(.down))
    }

    private static func nextUTCMidnight(after date: Date) -> Date {
        Date(timeIntervalSince1970: TimeInterval(utcDay(date) + 1) * secondsPerDay)
    }
}
