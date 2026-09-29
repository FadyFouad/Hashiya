import FeatureSearch
import HashiyaData
import HashiyaDesignSystem
import SwiftUI
import UIKit

/// The Share Extension's principal class: hosts `ShareLookupView` for the first shared item.
final class ShareViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        HashiyaFonts.register()
        HashiyaFonts.applyNavigationBarFonts()
        SharedLibraryDatabase.resume()

        let items = SharedItems(extensionContext?.inputItems.first as? NSExtensionItem)
        let host = UIHostingController(rootView: ShareLookupView(
            viewModel: ShareContainer.makeViewModel(),
            readInput: { await items.read() },
            onDone: { [weak self] in self?.close() }
        ))
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(host.view)
        host.didMove(toParent: self)
    }

    private func close() {
        // The extension is suspended next: stop taking database locks first.
        SharedLibraryDatabase.suspend()
        extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
    }
}
