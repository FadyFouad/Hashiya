import Foundation

/// How long a UI test waits for an element or state. GitHub's macOS runners launch the app and settle the UI far
/// more slowly than a Mac (3–7× in CI logs), so short waits failed at random; a long wait costs nothing when things
/// appear on time.
enum UITestTimeout {
    static let long: TimeInterval = 30
}
