import Observation

/// Requests for the system's rating prompt. The app's windows watch `count`; the first to `take()` one shows it, so two
/// iPad windows never ask twice.
@MainActor
@Observable
public final class ReviewRequests {
    public static let shared = ReviewRequests()

    public private(set) var count = 0
    private var pending = false

    public init() {}

    public func post() {
        pending = true
        count += 1
    }

    /// True once per request.
    public func take() -> Bool {
        defer { pending = false }
        return pending
    }
}
