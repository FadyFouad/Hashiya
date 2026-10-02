import HashiyaDesignSystem
import SwiftUI

/// The banner for a Details message, on the screen or inside the checklist sheet.
struct PaperDetailsBanner: View {
    let message: PaperDetailsMessage?
    var retrySave: () -> Void = {}

    var body: some View {
        switch message {
        case .notesSaveFailed:
            HashiyaBanner(
                text: L10n.string("details.notesSaveFailedMessage"),
                actionTitle: L10n.string("details.retry"),
                action: retrySave
            )
        case .statusUpdateFailed:
            HashiyaBanner(text: L10n.string("details.statusUpdateFailed"))
        case .collectionsUpdateFailed:
            HashiyaBanner(text: L10n.string("details.collectionsUpdateFailed"))
        case .bibtexCopied:
            HashiyaBanner(text: L10n.string("details.bibtexCopied"))
        case .bibtexIncomplete:
            HashiyaBanner(text: L10n.string("details.bibtexIncomplete"))
        case .copyFailed:
            HashiyaBanner(text: L10n.string("details.copyFailed"))
        case .pdfAttachNotPdf:
            HashiyaBanner(text: L10n.string("details.pdfAttachNotPdf"))
        case .pdfAttachTooLarge:
            HashiyaBanner(text: L10n.string("details.pdfTooLarge"))
        case .pdfAttachFailed:
            HashiyaBanner(text: L10n.string("details.pdfAttachFailed"))
        case nil:
            EmptyView()
        }
    }
}
