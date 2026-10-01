import HashiyaData
import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// The PDF row: what is stored or happening, its buttons, and its menu. A stored PDF opens on tap.
struct PdfRowView: View {
    let row: PdfRow
    let onAction: (PdfAction) -> Void

    var body: some View {
        let buttons = row.primary.filter { $0 != .read }
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: 8) {
                summary
                if !row.overflow.isEmpty { menu }
            }
            if !buttons.isEmpty {
                // Wraps when the labels don't fit on one line (Arabic, larger text).
                ChipFlow(spacing: 8) {
                    ForEach(buttons, id: \.self) { action in
                        Button {
                            onAction(action)
                        } label: {
                            Text(verbatim: Self.label(action))
                        }
                        .buttonStyle(TonalButtonStyle())
                    }
                }
                .padding(.leading, 36)
            }
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 14)
        .background(RoundedRectangle(cornerRadius: 12).fill(HashiyaColors.surfaceContainerHigh))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("details.pdf")
    }

    @ViewBuilder
    private var summary: some View {
        let content = HStack(alignment: .top, spacing: 12) {
            Image(systemName: "doc.richtext")
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: L10n.string("details.pdf"))
                    .font(.hashiya(.label))
                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
                detail
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if case .stored = row.state {
                Image(systemName: "chevron.forward")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    .accessibilityHidden(true)
            }
        }
        if case .stored = row.state {
            Button {
                onAction(.read)
            } label: {
                content.contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("details.pdfRead")
        } else {
            content.accessibilityElement(children: .combine)
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

    private func muted(_ text: String) -> some View {
        Text(verbatim: text)
            .font(.hashiya(.body))
            .foregroundStyle(HashiyaColors.onSurfaceVariant)
    }

    @MainActor
    static func label(_ action: PdfAction) -> String {
        switch action {
        case .read: L10n.string("details.pdf")
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
