import PDFKit
import SwiftUI
import UIKit

/// Lets the toolbar open the find bar of the `PDFView` on screen.
@MainActor
final class ReaderPDFController {
    fileprivate weak var view: PDFView?

    func showFind() {
        view?.findInteraction.presentFindNavigator(showingReplace: false)
    }
}

/// A `PDFView` that tells its coordinator when it has a size: the zoom limits and the start page need one.
private final class LayoutReportingPDFView: PDFView {
    var onLayout: ((PDFView) -> Void)?

    override func layoutSubviews() {
        super.layoutSubviews()
        onLayout?(self)
    }
}

/// The document in PDFKit: continuous vertical pages, pinch zoom from fit width to 4×, double-tap between fit width and
/// 2.5×, and the system find bar. Pages are never mirrored, whatever the app's language.
struct PDFKitView: UIViewRepresentable {
    let document: PDFDocument
    let startPage: Int
    let controller: ReaderPDFController
    let onPageChanged: (Int) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onPageChanged: onPageChanged)
    }

    func makeUIView(context: Context) -> PDFView {
        let view = LayoutReportingPDFView()
        view.semanticContentAttribute = .forceLeftToRight
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        view.autoScales = true
        view.isFindInteractionEnabled = true
        view.backgroundColor = .secondarySystemBackground
        view.accessibilityIdentifier = "reader.pages"
        view.document = document
        controller.view = view
        context.coordinator.attach(to: view, startPage: startPage)
        view.onLayout = { [weak coordinator = context.coordinator] view in coordinator?.didLayout(view) }
        let doubleTap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.toggleZoom))
        doubleTap.numberOfTapsRequired = 2
        view.addGestureRecognizer(doubleTap)
        return view
    }

    func updateUIView(_ view: PDFView, context: Context) {
        context.coordinator.onPageChanged = onPageChanged
        if view.document !== document {
            view.document = document
            context.coordinator.reset(startPage: startPage)
        }
    }

    static func dismantleUIView(_ view: PDFView, coordinator: Coordinator) {
        coordinator.detach()
    }

    @MainActor
    final class Coordinator: NSObject {
        static let doubleTapScale: CGFloat = 2.5
        static let maxScale: CGFloat = 4

        var onPageChanged: (Int) -> Void
        private weak var view: PDFView?
        private var observer: NSObjectProtocol?
        private var startPage = 0
        private var placed = false
        private var fitWidth: CGFloat = 0

        init(onPageChanged: @escaping (Int) -> Void) {
            self.onPageChanged = onPageChanged
        }

        func attach(to view: PDFView, startPage: Int) {
            self.view = view
            self.startPage = startPage
            observer = NotificationCenter.default.addObserver(forName: .PDFViewPageChanged, object: view, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated { self?.pageChanged() }
            }
        }

        func reset(startPage: Int) {
            self.startPage = startPage
            placed = false
            fitWidth = 0
        }

        func detach() {
            if let observer { NotificationCenter.default.removeObserver(observer) }
            observer = nil
        }

        /// Sets the zoom limits for the current width, and the first time, goes to the start page. A page shown at fit
        /// width stays at fit width when the width changes (the notes open beside it, a window resizes); a zoom the
        /// reader chose stays as it is.
        func didLayout(_ view: PDFView) {
            guard view.bounds.width > 0, view.document != nil else { return }
            let fit = view.scaleFactorForSizeToFit
            guard fit > 0, abs(fit - fitWidth) > 0.0001 else { return }
            let wasAtFitWidth = fitWidth > 0 && abs(view.scaleFactor - fitWidth) <= fitWidth * 0.01
            fitWidth = fit
            view.minScaleFactor = fit
            view.maxScaleFactor = fit * Self.maxScale
            if wasAtFitWidth || view.scaleFactor < fit { view.scaleFactor = fit }
            if !placed, let page = view.document?.page(at: startPage) {
                placed = true
                view.go(to: page)
            }
        }

        @objc func toggleZoom() {
            guard let view, fitWidth > 0 else { return }
            if view.scaleFactor > fitWidth * 1.01 {
                view.autoScales = true
                view.scaleFactor = fitWidth
            } else {
                view.autoScales = false
                view.scaleFactor = min(fitWidth * Self.doubleTapScale, view.maxScaleFactor)
            }
        }

        private func pageChanged() {
            guard let view, let document = view.document, let page = view.currentPage else { return }
            onPageChanged(document.index(for: page))
        }
    }
}
