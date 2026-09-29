/// A keyword search with its sort and filters.
public struct SearchQuery: Equatable, Hashable, Sendable {
    public var text: String
    public var sort: SearchSort
    public var years: YearFilter
    public var openAccessOnly: Bool

    public init(text: String, sort: SearchSort = .relevance, years: YearFilter = .anyTime, openAccessOnly: Bool = false) {
        self.text = text
        self.sort = sort
        self.years = years
        self.openAccessOnly = openAccessOnly
    }

    /// True when a year or open-access filter is set. The sort is not a filter.
    public var hasActiveFilters: Bool { years != .anyTime || openAccessOnly }
}

public enum SearchSort: String, CaseIterable, Sendable {
    case relevance, mostCited, newest
}

public enum YearFilter: Equatable, Hashable, Sendable {
    case anyTime
    /// Papers published in this year or later. Presets: 2024, 2020, 2015.
    case since(Int)
    /// Papers published from `from` through `to`, with `from <= to`. Build it with `YearFilter.between(_:_:)`.
    case between(from: Int, to: Int)

    /// A range filter, or nil when `from` is after `to`.
    public static func between(_ from: Int, _ to: Int) -> YearFilter? {
        from <= to ? .between(from: from, to: to) : nil
    }
}
