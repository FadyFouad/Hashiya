import Foundation
import HashiyaModel
import HashiyaNetwork

extension SearchQuery {
    /// OpenAlex's page size for keyword search.
    public static let pageSize = 25

    /// The `GET /works` parameters for this query. `cursor` nil is the first page ("*").
    public func worksSearchRequest(cursor: String?) -> WorksSearchRequest {
        WorksSearchRequest(
            search: openAlexSearch,
            filter: openAlexFilter,
            sort: openAlexSort,
            cursor: cursor ?? "*",
            perPage: Self.pageSize
        )
    }

    /// The text without ? and *: OpenAlex reads them as wildcards and rejects them in its default (stemmed) search
    /// with a 400, so a title such as "ChatGPT for good? …" failed. They carry no meaning for a keyword search.
    private var openAlexSearch: String {
        text.components(separatedBy: CharacterSet(charactersIn: "?*").union(.whitespacesAndNewlines))
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }

    private var openAlexFilter: String? {
        var filters: [String] = []
        switch years {
        case .anyTime:
            break
        case let .since(year):
            filters.append("publication_year:>\(year - 1)")
        case let .between(from, to):
            filters.append("publication_year:\(from)-\(to)")
        }
        if openAccessOnly {
            filters.append("is_oa:true")
        }
        return filters.isEmpty ? nil : filters.joined(separator: ",")
    }

    private var openAlexSort: String? {
        switch sort {
        case .relevance: nil
        case .mostCited: "cited_by_count:desc"
        case .newest: "publication_date:desc"
        }
    }
}
