import HashiyaModel
import SwiftUI

/// The preview sheet's body: the full paper, then Open DOI and Save/Remove.
public struct PaperPreviewContent: View {
    private let paper: Paper
    private let inLibrary: Bool
    private let onToggleSave: () -> Void
    private let onOpenDOI: ((String) -> Void)?

    /// - Parameter onOpenDOI: nil hides Open DOI.
    public init(paper: Paper, inLibrary: Bool, onToggleSave: @escaping () -> Void, onOpenDOI: ((String) -> Void)?) {
        self.paper = paper
        self.inLibrary = inLibrary
        self.onToggleSave = onToggleSave
        self.onOpenDOI = onOpenDOI
    }

    public var body: some View {
        VStack(spacing: 0) {
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
                    .buttonStyle(.bordered)
                    .tint(HashiyaColors.primary)
                }
                Button(action: onToggleSave) {
                    Text(verbatim: L10n.string(inLibrary ? "designsystem.removeFromLibrary" : "designsystem.saveToLibrary"))
                        .font(.hashiya(.label))
                        .foregroundStyle(HashiyaColors.onPrimary)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(HashiyaColors.primary)
            }
            .controlSize(.large)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .background(HashiyaColors.surface)
    }
}
