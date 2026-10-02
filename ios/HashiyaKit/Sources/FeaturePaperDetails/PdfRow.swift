import Foundation
import HashiyaData
import HashiyaModel

/// What the PDF row on Details shows (spec §7, Android's `PdfRowState`).
public enum PdfRowState: Equatable, Sendable {
    /// No PDF yet, and the paper has an open-access link.
    case available
    /// No PDF and no link.
    case none
    case downloading(bytes: Int64, total: Int64?)
    case stored(PaperPdf)
    case failed(DownloadFailure)
}

/// Every action the PDF row can offer.
public enum PdfAction: Equatable, Hashable, Sendable {
    case read, download, cancel, attach, tryAgain, openInBrowser, replace, remove, openLink
}

/// Replace and Remove ask first.
public enum PdfConfirmation: Equatable, Sendable {
    case replace, remove
}

/// The row's state with the paper's open-access `link`, and the actions they allow.
public struct PdfRow: Equatable, Sendable {
    public var state: PdfRowState
    public var link: URL?

    public init(state: PdfRowState, link: URL?) {
        self.state = state
        self.link = link
    }

    /// A running download wins, then a stored file, then a failure. A `.noLink` failure means the link is gone, so the
    /// row falls back to the no-PDF state.
    public init(pdf: PaperPdf?, download: DownloadState?, link: URL?) {
        self.link = link
        if case let .running(bytes, total)? = download {
            state = .downloading(bytes: bytes, total: total)
        } else if let pdf {
            state = .stored(pdf)
        } else if case let .failed(reason)? = download, reason != .noLink {
            state = .failed(reason)
        } else {
            state = link == nil ? .none : .available
        }
    }

    /// Shown as buttons under the row; `.read` is the row itself.
    public var primary: [PdfAction] {
        switch state {
        case .available: [.download]
        case .none: [.attach]
        case .downloading: [.cancel]
        case .stored: [.read]
        case .failed: link == nil ? [.attach] : [.tryAgain, .openInBrowser, .attach]
        }
    }

    /// Shown in the row's menu.
    public var overflow: [PdfAction] {
        switch state {
        case .available: [.attach]
        case .stored: link == nil ? [.replace, .remove] : [.replace, .remove, .openLink]
        case .none, .downloading, .failed: []
        }
    }
}
