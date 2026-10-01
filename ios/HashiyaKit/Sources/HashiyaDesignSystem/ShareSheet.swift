import UIKit

/// The system share sheet for a file, presented from UIKit. SwiftUI's `.sheet` around a `UIActivityViewController`
/// shows a blank sheet first and can't report when the user is done.
@MainActor
public enum ShareSheet {
    /// Presents the share sheet for `fileURL` and returns when it closes, whether the file was shared or not.
    /// Returns at once when there is no window to present from.
    public static func present(fileURL: URL) async {
        guard let presenter = topViewController(), !presenter.isBeingDismissed else { return }
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            let finish = Finish(continuation)
            let controller = UIActivityViewController(activityItems: [fileURL], applicationActivities: nil)
            controller.completionWithItemsHandler = { _, _, _, _ in
                // UIKit calls this on the main thread when the sheet closes.
                MainActor.assumeIsolated { finish.run() }
            }
            presenter.present(controller, animated: true)
        }
    }

    /// The key window's front-most view controller.
    private static func topViewController() -> UIViewController? {
        let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
        var top = (windows.first(where: \.isKeyWindow) ?? windows.first)?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}

/// Resumes the continuation once, however many times the completion handler runs.
@MainActor
private final class Finish {
    private var continuation: CheckedContinuation<Void, Never>?

    init(_ continuation: CheckedContinuation<Void, Never>) {
        self.continuation = continuation
    }

    func run() {
        continuation?.resume()
        continuation = nil
    }
}
