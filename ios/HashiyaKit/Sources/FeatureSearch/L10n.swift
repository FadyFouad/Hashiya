import Foundation
import HashiyaDesignSystem
import HashiyaModel

/// This target's strings.
@MainActor
enum L10n {
    static func string(_ key: String) -> String {
        HashiyaStrings.string(key, bundle: .module)
    }

    static func format(_ key: String, _ arguments: any CVarArg...) -> String {
        HashiyaStrings.format(key, bundle: .module, arguments)
    }

    /// "About 48,210 results": the plural form follows `count`, the text shows it formatted for the locale.
    static func resultCount(_ count: Int64) -> String {
        format("search.resultCount", count, PaperFormat.number(count))
    }

    static func sortLabel(_ sort: SearchSort) -> String {
        switch sort {
        case .relevance: string("search.sortRelevance")
        case .mostCited: string("search.sortMostCited")
        case .newest: string("search.sortNewest")
        }
    }

    static func yearLabel(_ years: YearFilter) -> String {
        switch years {
        case .anyTime: string("search.yearAny")
        case let .since(year): format("search.yearSince", PaperFormat.year(year))
        case let .between(from, to): format("search.yearBetween", PaperFormat.year(from), PaperFormat.year(to))
        }
    }

    /// "Looking up DOI 10.1038/nature14539…" / "Looking up arXiv 1706.03762…".
    static func lookupLooking(_ identifier: PaperIdentifier) -> String {
        switch identifier {
        case let .doi(doi): format("search.lookupLookingDOI", doi)
        case let .arxiv(id): format("search.lookupLookingArxiv", id)
        }
    }

    static func lookupNotFoundTitle(_ identifier: PaperIdentifier) -> String {
        switch identifier {
        case .doi: string("search.lookupNotFoundDOI")
        case .arxiv: string("search.lookupNotFoundArxiv")
        }
    }

    /// "Search for “…”" with the title shortened and isolated (U+2068 … U+2069) exactly once, so an English title
    /// stays left-to-right in Arabic. Formatting with the Arabic locale already isolates each argument.
    static func searchTitleButton(_ title: String) -> String {
        let shortened = shortenedTitle(title)
        let formatted = format("search.lookupSearchTitle", shortened)
        let isolated = "\u{2068}" + shortened + "\u{2069}"
        return formatted.contains(isolated) ? formatted : format("search.lookupSearchTitle", isolated)
    }

    /// Up to 60 characters as they are; longer titles keep their first 59, without trailing spaces, then "…".
    static func shortenedTitle(_ title: String) -> String {
        guard title.count > 60 else { return title }
        var cut = String(title.prefix(59))
        while cut.last?.isWhitespace == true { cut.removeLast() }
        return cut + "…"
    }

    /// Title and message of a first-page error.
    static func error(_ error: SearchError) -> (title: String, message: String) {
        switch error {
        case .offline: (string("search.errorOfflineTitle"), string("search.errorOfflineMessage"))
        case .invalidUserKey: (string("search.errorKeyTitle"), string("search.errorKeyMessage"))
        case .serviceUnavailable: (string("search.errorUnavailableTitle"), string("search.errorUnavailableMessage"))
        case .rateLimited: (string("search.errorRateTitle"), string("search.errorRateMessage"))
        case .unexpected: (string("search.errorUnexpectedTitle"), string("search.errorUnexpectedMessage"))
        }
    }
}
