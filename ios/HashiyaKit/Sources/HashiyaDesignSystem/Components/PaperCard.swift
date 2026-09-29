import HashiyaModel
import SwiftUI

/// A search result: title, meta line, badges, compact citations and Save.
public struct PaperCard: View {
    private let paper: Paper
    private let inLibrary: Bool
    private let onOpen: () -> Void
    private let onSave: () -> Void

    public init(paper: Paper, inLibrary: Bool, onOpen: @escaping () -> Void, onSave: @escaping () -> Void) {
        self.paper = paper
        self.inLibrary = inLibrary
        self.onOpen = onOpen
        self.onSave = onSave
    }

    public var body: some View {
        let title = PaperFormat.title(paper)
        let meta = PaperFormat.cardMeta(paper)
        let citations = PaperFormat.compactCitations(paper.citationCount)
        let openAccess = L10n.string("designsystem.openAccess")
        let inLibraryText = L10n.string("designsystem.inLibrary")

        VStack(alignment: .leading, spacing: 6) {
            // VoiceOver reads the card's text as one element (on the title); Save stays a separate button.
            PaperText(title, style: .cardTitle, lineLimit: 3)
                .accessibilityLabel(
                    [title, meta, paper.isOpenAccess ? openAccess : nil, inLibrary ? inLibraryText : nil, citations]
                        .compactMap { $0 }
                        .filter { !$0.isEmpty }
                        .joined(separator: ", ")
                )
                .accessibilityAddTraits(.isButton)
                .accessibilityAction { onOpen() }
            if !meta.isEmpty {
                PaperText(meta, style: .meta, color: HashiyaColors.onSurfaceVariant, lineLimit: 2)
                    .accessibilityHidden(true)
            }
            HStack(spacing: 6) {
                Group {
                    if paper.isOpenAccess {
                        StatusBadge(text: openAccess, kind: .openAccess)
                    }
                    if inLibrary {
                        StatusBadge(text: inLibraryText, kind: .inLibrary)
                    }
                    Text(verbatim: citations)
                        .font(.hashiya(.badge))
                        .foregroundStyle(HashiyaColors.onSurfaceVariant)
                }
                .accessibilityHidden(true)
                Spacer(minLength: 8)
                if !inLibrary {
                    Button(action: onSave) {
                        Text(verbatim: L10n.string("designsystem.save"))
                    }
                    .buttonStyle(TonalButtonStyle())
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 10).fill(HashiyaColors.surface))
        .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(HashiyaColors.outlineVariant, lineWidth: 1))
        .contentShape(RoundedRectangle(cornerRadius: 10))
        .onTapGesture(perform: onOpen)
        .padding(.horizontal, 12)
        .padding(.vertical, 5)
    }
}
