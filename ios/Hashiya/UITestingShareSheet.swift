#if DEBUG
import UIKit

/// Launched with `-ui-testing-share <url>` (Debug only): presents the system share sheet for that URL, so a UI
/// test can pick Hashiya in a real share sheet.
@MainActor
enum UITestingShareSheet {
    /// - Parameter onFinish: called when the share sheet closes.
    static func presentIfRequested(arguments: [String] = ProcessInfo.processInfo.arguments, onFinish: @escaping () -> Void) async {
        guard let index = arguments.firstIndex(of: "-ui-testing-share"), index + 1 < arguments.count,
              let url = URL(string: arguments[index + 1]) else { return }
        // Wait for the window to be on screen.
        var root: UIViewController?
        while root == nil {
            root = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap(\.windows)
                .first(where: \.isKeyWindow)?
                .rootViewController
            if root == nil { try? await Task.sleep(for: .milliseconds(100)) }
        }
        let sheet = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        sheet.completionWithItemsHandler = { _, _, _, _ in onFinish() }
        root?.present(sheet, animated: true)
    }
}
#endif
