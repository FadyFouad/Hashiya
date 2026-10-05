import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// Search's body in ID mode: looking, the found paper, not found, an error, or a link without an ID.
struct LookupBody: View {
    let state: LookupState
    let isSaved: (Paper) -> Bool
    let onToggleSave: (Paper) -> Void
    let onOpenDOI: (String) -> Void
    let onSearchTitle: (String) -> Void
    let onRetry: () -> Void
    let onOpenSettings: () -> Void

    var body: some View {
        switch state {
        case let .looking(identifier):
            LookupLookingView(identifier: identifier)
        case let .found(paper):
            PaperPreviewContent(
                paper: paper,
                inLibrary: isSaved(paper),
                onToggleSave: { onToggleSave(paper) },
                onOpenDOI: onOpenDOI
            )
        case let .notFound(identifier, searchTitle):
            if let searchTitle {
                EmptyStateView(
                    icon: "doc.text.magnifyingglass",
                    title: L10n.lookupNotFoundTitle(identifier),
                    message: L10n.string("search.lookupNotFoundMessage"),
                    actionTitle: L10n.searchTitleButton(searchTitle),
                    action: { onSearchTitle(searchTitle) }
                )
            } else {
                EmptyStateView(
                    icon: "doc.text.magnifyingglass",
                    title: L10n.lookupNotFoundTitle(identifier),
                    message: L10n.string("search.lookupNotFoundMessage")
                )
            }
        case let .failed(error):
            SearchErrorView(error: error, onRetry: onRetry, onOpenSettings: onOpenSettings)
        case .noIDInLink:
            EmptyStateView(
                icon: "link",
                title: L10n.string("search.linkNoIDTitle"),
                message: L10n.string("search.linkNoIDMessage")
            )
        }
    }
}

/// "Looking up DOI …" (or "…arXiv…", or nothing while the ID is unknown) above one skeleton row.
struct LookupLookingView: View {
    let identifier: PaperIdentifier?

    var body: some View {
        VStack(spacing: 0) {
            if let identifier {
                Text(verbatim: L10n.lookupLooking(identifier))
                    .font(.hashiya(.body))
                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
            }
            LoadingSkeleton(rows: 1)
        }
    }
}

/// Spec 1's error state: Retry, or Open Settings for a rejected user key or a reached daily limit when
/// `onOpenSettings` is given.
struct SearchErrorView: View {
    let error: SearchError
    let onRetry: () -> Void
    let onOpenSettings: (() -> Void)?

    @Environment(\.timeZone) private var timeZone

    var body: some View {
        let text = L10n.error(error, timeZone: timeZone)
        if error.opensSettings, let onOpenSettings {
            ErrorStateView(title: text.title, message: text.message, actionTitle: L10n.string("search.openSettings"), action: onOpenSettings)
        } else {
            ErrorStateView(title: text.title, message: text.message, actionTitle: L10n.string("search.retry"), action: onRetry)
        }
    }
}

extension SearchError {
    /// A personal key in Settings is the way out of these.
    fileprivate var opensSettings: Bool {
        switch self {
        case .invalidUserKey, .dailyLimit: true
        case .offline, .rateLimited, .serviceUnavailable, .unexpected: false
        }
    }
}
