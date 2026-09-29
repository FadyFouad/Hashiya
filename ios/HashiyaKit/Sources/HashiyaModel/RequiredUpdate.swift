import Foundation

/// This build is no longer supported; `storeURL` is the app's store page.
public struct RequiredUpdate: Equatable, Sendable {
    public let storeURL: URL

    public init(storeURL: URL) {
        self.storeURL = storeURL
    }
}
