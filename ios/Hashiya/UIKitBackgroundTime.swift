import HashiyaData
import os
import UIKit

/// `UIApplication` background tasks for PDF downloads, so a download started just before switching apps can finish.
struct UIKitBackgroundTime: BackgroundTimeGranting {
    func begin(name: String, onExpiry: @escaping @Sendable () -> Void) async -> any BackgroundTimeToken {
        await MainActor.run {
            let token = Token()
            let id = UIApplication.shared.beginBackgroundTask(withName: name) {
                // UIKit calls this on the main thread, just before it suspends the app.
                onExpiry()
                MainActor.assumeIsolated { token.endOnMain() }
            }
            token.set(id)
            return token
        }
    }

    private final class Token: BackgroundTimeToken, @unchecked Sendable {
        private let id = OSAllocatedUnfairLock(initialState: UIBackgroundTaskIdentifier.invalid)

        func set(_ identifier: UIBackgroundTaskIdentifier) {
            id.withLock { $0 = identifier }
        }

        @MainActor func endOnMain() {
            let identifier = id.withLock { current -> UIBackgroundTaskIdentifier in
                defer { current = .invalid }
                return current
            }
            if identifier != .invalid { UIApplication.shared.endBackgroundTask(identifier) }
        }

        func end() async {
            await endOnMain()
        }
    }
}
