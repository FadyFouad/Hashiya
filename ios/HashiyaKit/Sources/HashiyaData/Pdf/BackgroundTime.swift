/// Extra time to finish a PDF download after the app goes to the background. The app's implementation uses
/// `UIApplication.beginBackgroundTask`; the Share Extension, previews and tests use `NoBackgroundTime`.
public protocol BackgroundTimeGranting: Sendable {
    /// Starts a grant. `onExpiry` runs when the system is about to suspend the app; the download then cancels itself.
    func begin(name: String, onExpiry: @escaping @Sendable () -> Void) async -> any BackgroundTimeToken
}

public protocol BackgroundTimeToken: Sendable {
    /// Ends the grant. Safe to call more than once.
    func end() async
}

/// No background time: the download simply stops with the process.
public struct NoBackgroundTime: BackgroundTimeGranting {
    public init() {}

    public func begin(name: String, onExpiry: @escaping @Sendable () -> Void) async -> any BackgroundTimeToken {
        NoToken()
    }

    private struct NoToken: BackgroundTimeToken {
        func end() async {}
    }
}
