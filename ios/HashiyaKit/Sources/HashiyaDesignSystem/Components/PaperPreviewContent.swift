import HashiyaModel
import SwiftUI

/// The preview sheet's body: the full paper, the reading status (Library only), then Open details (saved papers in Search), Open DOI and Save/Remove.
public struct PaperPreviewContent: View {
    private let paper: Paper
    private let inLibrary: Bool
    private let status: ReadingStatus?
    private let onStatusChange: (ReadingStatus) -> Void
    private let onToggleSave: () -> Void
    private let onOpenDOI: ((String) -> Void)?
    private let onOpenDetails: (() -> Void)?

    /// - Parameters:
    ///   - status: the saved paper's status, shown as a segmented selector above the buttons; nil shows none.
    ///   - onOpenDOI: nil hides Open DOI.
    ///   - onOpenDetails: nil hides Open details.
    public init(
        paper: Paper,
        inLibrary: Bool,
        status: ReadingStatus? = nil,
        onStatusChange: @escaping (ReadingStatus) -> Void = { _ in },
        onToggleSave: @escaping () -> Void,
        onOpenDOI: ((String) -> Void)?,
        onOpenDetails: (() -> Void)? = nil
    ) {
        self.paper = paper
        self.inLibrary = inLibrary
        self.status = status
        self.onStatusChange = onStatusChange
        self.onToggleSave = onToggleSave
        self.onOpenDOI = onOpenDOI
        self.onOpenDetails = onOpenDetails
    }

    public var body: some View {
        if #available(iOS 26, *) {
            // The paper scrolls under the glass buttons; the bar's scroll edge effect keeps them legible.
            details
                .safeAreaBar(edge: .bottom, spacing: 0) { actions }
                .background(HashiyaColors.surface)
        } else {
            VStack(spacing: 0) {
                details
                actions
            }
            .background(HashiyaColors.surface)
        }
    }

    private var details: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                PaperText(PaperFormat.title(paper), style: .previewTitle)
                    .accessibilityAddTraits(.isHeader)
                if !paper.authors.isEmpty {
                    PaperText(paper.authors.map(\.name).joined(separator: ", "), style: .body, color: HashiyaColors.onSurfaceVariant)
                }
                Text(verbatim: PaperFormat.previewMeta(paper))
                    .font(.hashiya(.meta))
                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if paper.isOpenAccess {
                    StatusBadge(
                        text: L10n.string(paper.openAccessPDFURL == nil ? "designsystem.openAccess" : "designsystem.openAccessPDF"),
                        kind: .openAccess
                    )
                }
                Text(verbatim: L10n.string("designsystem.abstract"))
                    .font(.hashiya(.label))
                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    .padding(.top, 4)
                if let abstract = paper.abstract {
                    PaperText(abstract, style: .body)
                } else {
                    Text(verbatim: L10n.string("designsystem.noAbstract"))
                        .font(.hashiya(.body))
                        .foregroundStyle(HashiyaColors.onSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 24)
            .padding(.bottom, 16)
        }
    }

    /// The status selector (Library only) above Open DOI and Save/Remove.
    private var actions: some View {
        VStack(spacing: 0) {
            if let status {
                // 16 pt above; the buttons' 12 pt padding plus 4 makes 16 below.
                ReadingStatusSelector(status: status, onChange: onStatusChange)
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    .padding(.bottom, 4)
            }
            HashiyaGlassGroup(spacing: 12) {
                VStack(spacing: 12) {
                    if let onOpenDetails {
                        Button(action: onOpenDetails) {
                            Label {
                                Text(verbatim: L10n.string("designsystem.openDetails"))
                            } icon: {
                                Image(systemName: "doc.text")
                            }
                            .font(.hashiya(.label))
                            .frame(maxWidth: .infinity)
                        }
                        .hashiyaSecondaryButton()
                    }
                    HStack(spacing: 12) {
                        if let doi = paper.doi, let onOpenDOI {
                            Button {
                                onOpenDOI(doi)
                            } label: {
                                Label {
                                    Text(verbatim: L10n.string("designsystem.openDOI"))
                                } icon: {
                                    Image(systemName: "arrow.up.forward.square")
                                }
                                .font(.hashiya(.label))
                                .frame(maxWidth: .infinity)
                            }
                            .hashiyaSecondaryButton()
                        }
                        Button(action: onToggleSave) {
                            Text(verbatim: L10n.string(inLibrary ? "designsystem.removeFromLibrary" : "designsystem.saveToLibrary"))
                                .font(.hashiya(.label))
                                .foregroundStyle(HashiyaColors.onPrimary)
                                .frame(maxWidth: .infinity)
                        }
                        .hashiyaProminentButton()
                    }
                }
            }
            .controlSize(.large)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
    }
}
