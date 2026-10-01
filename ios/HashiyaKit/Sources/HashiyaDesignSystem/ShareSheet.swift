import UIKit

/// The system share sheet for a file, presented from UIKit. SwiftUI's `.sheet` around a `UIActivityViewController`
/// shows a blank sheet first and can't report when the user is done.
@MainActor
public enum ShareSheet {
    /// Presents the share sheet for `fileURL` and returns when it closes, whether the file was shared or not.
    /// Returns false at once when there is no window to present from, or when the presentation failed; true once the
    /// sheet has closed.
    @discardableResult
    public static func present(fileURL: URL) async -> Bool {
        guard let presenter = topViewController(),
              presenter.view.window != nil,
              presenter.presentedViewController == nil,
              !presenter.isBeingPresented,
              !presenter.isBeingDismissed
        else { return false }
        return await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            let finish = Finish(continuation)
            let controller = UIActivityViewController(activityItems: [fileURL], applicationActivities: nil)
            controller.completionWithItemsHandler = { activityType, completed, _, _ in
                // A cancelled share extension (Mail, say) also calls this, with a non-nil activityType and
                // completed == false, while the sheet stays open; only a nil type or a completed share closes it.
                guard activityType == nil || completed else { return }
                // UIKit calls this on the main thread when the sheet closes.
                MainActor.assumeIsolated { finish.run(true) }
            }
            // iPad shows the share sheet as a popover, which needs an anchor.
            if let popover = controller.popoverPresentationController {
                popover.sourceView = presenter.view
                popover.sourceRect = CGRect(x: presenter.view.bounds.midX, y: presenter.view.bounds.midY, width: 0, height: 0)
                popover.permittedArrowDirections = []
            }
            presenter.present(controller, animated: true) {
                // A presentation that silently failed never calls the completion handler above.
                if controller.presentingViewController == nil { finish.run(false) }
            }
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
    private var continuation: CheckedContinuation<Bool, Never>?

    init(_ continuation: CheckedContinuation<Bool, Never>) {
        self.continuation = continuation
    }

    func run(_ presented: Bool) {
        continuation?.resume(returning: presented)
        continuation = nil
    }
}
