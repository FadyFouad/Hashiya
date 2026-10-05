import os

/// The papers whose notes were edited since the app started, so a `note_edited` event is sent once per paper per app
/// session. Kept in memory only and never sent anywhere.
public final class NotedPapers: Sendable {
    public static let shared = NotedPapers()

    private let ids = OSAllocatedUnfairLock(initialState: Set<String>())

    public init() {}

    /// True the first time `openAlexID` is seen.
    public func firstEdit(_ openAlexID: String) -> Bool {
        ids.withLock { $0.insert(openAlexID).inserted }
    }
}
