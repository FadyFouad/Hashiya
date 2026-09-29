import HashiyaModel

/// The chips as `@SceneStorage` values, under the Android `SavedStateHandle` keys.
public struct SearchSceneState: Equatable, Sendable {
    public static let textKey = "search_text"
    public static let sortKey = "search_sort"
    public static let yearKindKey = "search_year_kind"
    public static let yearFromKey = "search_year_from"
    public static let yearToKey = "search_year_to"
    public static let openAccessKey = "search_oa"

    /// `relevance`, `mostCited` or `newest`.
    public var sort: String
    /// `since`, `between`, or nil for any time.
    public var yearKind: String?
    public var yearFrom: Int?
    public var yearTo: Int?
    public var openAccess: Bool

    public init(sort: String, yearKind: String?, yearFrom: Int?, yearTo: Int?, openAccess: Bool) {
        self.sort = sort
        self.yearKind = yearKind
        self.yearFrom = yearFrom
        self.yearTo = yearTo
        self.openAccess = openAccess
    }

    /// The values that store `query`'s chips.
    public init(_ query: SearchQuery) {
        sort = query.sort.rawValue
        openAccess = query.openAccessOnly
        switch query.years {
        case .anyTime:
            yearKind = nil
            yearFrom = nil
            yearTo = nil
        case let .since(year):
            yearKind = "since"
            yearFrom = year
            yearTo = nil
        case let .between(from, to):
            yearKind = "between"
            yearFrom = from
            yearTo = to
        }
    }

    /// The chips these values describe, with `text`. Missing or invalid values fall back to the defaults.
    public func query(text: String) -> SearchQuery {
        SearchQuery(text: text, sort: SearchSort(rawValue: sort) ?? .relevance, years: years, openAccessOnly: openAccess)
    }

    private var years: YearFilter {
        switch (yearKind, yearFrom, yearTo) {
        case let ("since", from?, _):
            .since(from)
        case let ("between", from?, to?):
            YearFilter.between(from, to) ?? .anyTime
        default:
            .anyTime
        }
    }
}
