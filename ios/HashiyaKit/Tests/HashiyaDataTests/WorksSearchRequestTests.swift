import HashiyaData
import HashiyaModel
import HashiyaNetwork
import Testing

struct WorksSearchRequestTests {
    @Test func aPlainQueryIsTrimmedWithNoFilterOrSort() {
        let request = SearchQuery(text: "  bert ").worksSearchRequest(cursor: nil)
        #expect(request == WorksSearchRequest(search: "bert", filter: nil, sort: nil, cursor: "*", perPage: 25))
    }

    @Test(arguments: [
        (SearchSort.relevance, nil),
        (.mostCited, "cited_by_count:desc"),
        (.newest, "publication_date:desc"),
    ] as [(SearchSort, String?)])
    func mapsEachSort(sort: SearchSort, expected: String?) {
        #expect(SearchQuery(text: "bert", sort: sort).worksSearchRequest(cursor: nil).sort == expected)
    }

    @Test(arguments: [
        (YearFilter.anyTime, false, nil),
        (.since(2020), false, "publication_year:>2019"),
        (.since(2024), false, "publication_year:>2023"),
        (.between(from: 2015, to: 2020), false, "publication_year:2015-2020"),
        (.between(from: 2020, to: 2020), false, "publication_year:2020-2020"),
        (.since(2020), true, "publication_year:>2019,is_oa:true"),
        (.between(from: 2015, to: 2020), true, "publication_year:2015-2020,is_oa:true"),
        (.anyTime, true, "is_oa:true"),
    ] as [(YearFilter, Bool, String?)])
    func mapsYearsAndOpenAccess(years: YearFilter, openAccessOnly: Bool, expected: String?) {
        let query = SearchQuery(text: "bert", years: years, openAccessOnly: openAccessOnly)
        #expect(query.worksSearchRequest(cursor: nil).filter == expected)
    }

    @Test func passesTheCursorThrough() {
        let request = SearchQuery(text: "bert").worksSearchRequest(cursor: "IlsxMDAuMCwgJ1czMTc3ODI4OTA5J10i")
        #expect(request.cursor == "IlsxMDAuMCwgJ1czMTc3ODI4OTA5J10i")
    }
}
