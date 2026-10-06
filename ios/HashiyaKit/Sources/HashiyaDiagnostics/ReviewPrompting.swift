import Foundation
import os

/// What the rating rule reads. Kept on the device; it never leaves it.
public struct ReviewCounters: Equatable, Sendable {
    /// When the app was first opened; nil before the first open was recorded.
    public var firstOpenedAt: Date?
    /// Papers saved since install. Removing a paper doesn't lower it.
    public var saves: Int
    /// Whether a BibTeX export ever finished.
    public var exported: Bool
    /// When the app last asked the system for its rating prompt; nil if never.
    public var lastAskedAt: Date?

    public init(firstOpenedAt: Date?, saves: Int, exported: Bool, lastAskedAt: Date?) {
        self.firstOpenedAt = firstOpenedAt
        self.saves = saves
        self.exported = exported
        self.lastAskedAt = lastAskedAt
    }
}

/// When to ask for a rating: once the app has shown its value (5 saves or a BibTeX export), the person has had it for
/// 3 days, and it hasn't asked in 120 days. A stored time later than now (the clock went back) reads as "not yet".
public enum ReviewRule {
    public static let minSaves = 5
    public static let settle: TimeInterval = 3 * 24 * 60 * 60
    public static let gap: TimeInterval = 120 * 24 * 60 * 60

    public static func shouldAsk(_ counters: ReviewCounters, now: Date) -> Bool {
        guard let firstOpenedAt = counters.firstOpenedAt else { return false }
        guard counters.saves >= minSaves || counters.exported else { return false }
        guard now.timeIntervalSince(firstOpenedAt) >= settle else { return false }
        guard let lastAskedAt = counters.lastAskedAt else { return true }
        return now.timeIntervalSince(lastAskedAt) >= gap
    }
}

/// Asks for a store rating at good moments. Only the app knows how to show the system's prompt.
public protocol ReviewPrompting: Sendable {
    /// The app opened; the first call starts the 3-day wait.
    func markOpened()
    /// A paper was saved from Search or Add by ID.
    func recordSave()
    /// A BibTeX export finished and its share sheet closed.
    func recordExport()
    /// Nothing covers the screen now: asks for the system's rating prompt if `ReviewRule` says so.
    func askIfDue()
}

/// Debug builds, the Share Extension and tests: counts nothing, never asks.
public struct NoReviewPrompting: ReviewPrompting {
    public init() {}
    public func markOpened() {}
    public func recordSave() {}
    public func recordExport() {}
    public func askIfDue() {}
}

/// The counters in `UserDefaults`, under `reviewPrompt.`.
public final class UserDefaultsReviewCounterStore: @unchecked Sendable {
    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public func read() -> ReviewCounters {
        ReviewCounters(
            firstOpenedAt: defaults.object(forKey: Key.firstOpenedAt) as? Date,
            saves: defaults.integer(forKey: Key.saves),
            exported: defaults.bool(forKey: Key.exported),
            lastAskedAt: defaults.object(forKey: Key.lastAskedAt) as? Date
        )
    }

    public func markOpened(now: Date) {
        if defaults.object(forKey: Key.firstOpenedAt) == nil { defaults.set(now, forKey: Key.firstOpenedAt) }
    }

    public func addSave() { defaults.set(defaults.integer(forKey: Key.saves) + 1, forKey: Key.saves) }

    public func markExported() { defaults.set(true, forKey: Key.exported) }

    public func markAsked(now: Date) { defaults.set(now, forKey: Key.lastAskedAt) }

    private enum Key {
        static let firstOpenedAt = "reviewPrompt.firstOpenedAt"
        static let saves = "reviewPrompt.saves"
        static let exported = "reviewPrompt.exported"
        static let lastAskedAt = "reviewPrompt.lastAskedAt"
    }
}

/// Counts and decides; "last asked" is stored when the request is made, since the system never says whether its
/// prompt appeared. Calls may come from any thread.
public final class DefaultReviewPrompt: ReviewPrompting {
    private let store: UserDefaultsReviewCounterStore
    private let now: @Sendable () -> Date
    private let request: @Sendable () -> Void
    private let lock = OSAllocatedUnfairLock()

    public init(store: UserDefaultsReviewCounterStore, now: @escaping @Sendable () -> Date = { Date() }, request: @escaping @Sendable () -> Void) {
        self.store = store
        self.now = now
        self.request = request
    }

    public func markOpened() { lock.withLock { store.markOpened(now: now()) } }

    public func recordSave() { lock.withLock { store.addSave() } }

    public func recordExport() { lock.withLock { store.markExported() } }

    public func askIfDue() {
        let ask = lock.withLock {
            let time = now()
            let ask = ReviewRule.shouldAsk(store.read(), now: time)
            if ask { store.markAsked(now: time) }
            return ask
        }
        if ask { request() }
    }
}
