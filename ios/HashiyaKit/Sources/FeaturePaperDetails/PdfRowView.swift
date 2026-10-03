import HashiyaData
import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// The PDF row, the middle of the Details group: what is stored or happening, the row's own action as a prominent
/// button (Download, Read or Attach, by state), the other actions as secondary buttons, and a menu for the rest.
struct PdfRowView: View {
    let row: PdfRow
    let shape: UnevenRoundedRectangle
    let onAction: (PdfAction) -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: "doc.richtext")
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .frame(width: GroupedRows.iconWidth)
                .padding(.top, 2)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                VStack(alignment: .leading, spacing: 4) {
                    GroupedRowLabel(text: L10n.string("details.pdf"))
                    detail
                }
                .accessibilityElement(children: .combine)
                if !row.primary.isEmpty {
                    // Wraps when the labels don't fit on one line (Arabic, larger text).
                    ChipFlow(spacing: 8) {
                        ForEach(Array(row.primary.enumerated()), id: \.element) { index, action in
                            actionButton(action, prominent: index == 0 && action != .cancel)
                        }
                    }
                    .padding(.top, 8)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if !row.overflow.isEmpty { menu }
        }
        .padding(16)
        .background(shape.fill(HashiyaColors.surfaceContainer))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("details.pdf")
    }

    @ViewBuilder
    private func actionButton(_ action: PdfAction, prominent: Bool) -> some View {
        let button = Button {
            onAction(action)
        } label: {
            if prominent, let icon = Self.icon(action) {
                Label {
                    Text(verbatim: Self.label(action))
                } icon: {
                    Image(systemName: icon)
                }
            } else {
                Text(verbatim: Self.label(action))
            }
        }
        .font(.hashiya(.label))
        // Read keeps the identifier the stored row had, for the UI tests that open the reader.
        .accessibilityIdentifier(action == .read ? "details.pdfRead" : "details.pdfAction.\(action)")
        if prominent {
            button.hashiyaProminentButton()
        } else {
            button.hashiyaSecondaryButton()
        }
    }

    @ViewBuilder
    private var detail: some View {
        switch row.state {
        case .available:
            muted(L10n.string("details.pdfAvailable"))
        case .none:
            muted(L10n.string("details.pdfNone"))
        case let .downloading(bytes, total):
            let done = PaperFormat.fileSize(bytes)
            if let total {
                let all = PaperFormat.fileSize(total)
                muted(L10n.format("details.pdfProgress", done, all))
            } else {
                muted(done)
            }
            if let total, total > 0 {
                ProgressView(value: Double(min(bytes, total)), total: Double(total))
                    .tint(HashiyaColors.primary)
            } else {
                ProgressView()
                    .progressViewStyle(.linear)
                    .tint(HashiyaColors.primary)
            }
        case let .stored(pdf):
            let size = PaperFormat.fileSize(pdf.sizeBytes)
            muted(L10n.format(pdf.source == .downloaded ? "details.pdfDownloaded" : "details.pdfAttached", size))
        case let .failed(reason):
            Text(verbatim: L10n.string("details.pdfFailed"))
                .font(.hashiya(.body))
                .foregroundStyle(HashiyaColors.error)
            Text(verbatim: L10n.string(Self.reasonKey(reason)))
                .font(.hashiya(.meta))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
        }
    }

    private var menu: some View {
        Menu {
            ForEach(row.overflow, id: \.self) { action in
                Button(role: action == .remove ? .destructive : nil) {
                    onAction(action)
                } label: {
                    Text(verbatim: Self.label(action))
                }
            }
        } label: {
            Image(systemName: "ellipsis.circle")
                .font(.title3)
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .frame(minWidth: 44, minHeight: 44)
        }
        .accessibilityLabel(Text(verbatim: L10n.string("details.moreOptions")))
        .accessibilityIdentifier("details.pdfMenu")
    }

    /// The state line under the label: normal text, as the group's other values.
    private func muted(_ text: String) -> some View {
        Text(verbatim: text)
            .font(.hashiya(.body))
            .foregroundStyle(HashiyaColors.onSurface)
    }

    /// The prominent button's symbol, as in the design: download, an open book, a plus.
    static func icon(_ action: PdfAction) -> String? {
        switch action {
        case .download: "arrow.down.circle"
        case .read: "book"
        case .attach: "plus"
        default: nil
        }
    }

    @MainActor
    static func label(_ action: PdfAction) -> String {
        switch action {
        case .read: L10n.string("details.pdfRead")
        case .download: L10n.string("details.pdfDownload")
        case .cancel: L10n.string("details.pdfCancel")
        case .attach: L10n.string("details.pdfAttach")
        case .tryAgain: L10n.string("details.pdfTryAgain")
        case .openInBrowser: L10n.string("details.pdfOpenBrowser")
        case .replace: L10n.string("details.pdfReplace")
        case .remove: L10n.string("details.pdfRemove")
        case .openLink: L10n.string("details.pdfOpenLink")
        }
    }

    static func reasonKey(_ reason: DownloadFailure) -> String {
        switch reason {
        case .offline: "details.pdfOffline"
        case .notPDF: "details.pdfNotPdf"
        case .tooLarge: "details.pdfTooLarge"
        case .http: "details.pdfHttp"
        // The row shows the no-PDF state for a missing link (`PdfRow(pdf:download:link:)`), so this is never on screen.
        case .noLink: "details.pdfFailed"
        }
    }
}
