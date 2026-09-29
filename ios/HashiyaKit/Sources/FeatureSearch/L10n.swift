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
