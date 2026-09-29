import HashiyaModel
import Testing

struct SearchQueryTests {
    @Test func aPlainQueryHasNoActiveFilters() {
        #expect(!SearchQuery(text: "bert").hasActiveFilters)
    }

    @Test(arguments: SearchSort.allCases)
    func sortIsNotAFilter(sort: SearchSort) {
        #expect(!SearchQuery(text: "bert", sort: sort).hasActiveFilters)
    }

    @Test func yearsAndOpenAccessAreFilters() {
        #expect(SearchQuery(text: "bert", years: .since(2020)).hasActiveFilters)
        #expect(SearchQuery(text: "bert", years: .between(from: 2015, to: 2020)).hasActiveFilters)
        #expect(SearchQuery(text: "bert", openAccessOnly: true).hasActiveFilters)
    }

    @Test func betweenRejectsAStartAfterTheEnd() {
        #expect(YearFilter.between(2021, 2020) == nil)
    }

    @Test func betweenAcceptsOrderedAndEqualYears() {
        #expect(YearFilter.between(2015, 2020) == .between(from: 2015, to: 2020))
        #expect(YearFilter.between(2020, 2020) == .between(from: 2020, to: 2020))
    }

    @Test func defaultsAreRelevanceAnyTimeAndAllAccess() {
        let query = SearchQuery(text: "bert")
        #expect(query.sort == .relevance)
        #expect(query.years == .anyTime)
        #expect(!query.openAccessOnly)
    }
}
