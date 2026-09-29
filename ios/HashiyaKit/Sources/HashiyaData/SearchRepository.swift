import HashiyaModel
import HashiyaNetwork

public protocol SearchRepository: Sendable {
    /// One page. `cursor` nil = first page. Throws `SearchError` or `CancellationError`.
    func searchPage(_ query: SearchQuery, cursor: String?) async throws -> SearchPage
}

public struct SearchPage: Equatable, Sendable {
    public var papers: [Paper]
    /// OpenAlex's `meta.count`.
    public var totalCount: Int64
    /// Nil when `meta.next_cursor` is null or the page has no results.
    public var nextCursor: String?

    public init(papers: [Paper], totalCount: Int64, nextCursor: String?) {
        self.papers = papers
        self.totalCount = totalCount
        self.nextCursor = nextCursor
    }
}

public struct OpenAlexSearchRepository: SearchRepository {
    private let service: any OpenAlexSearchService

    public init(service: any OpenAlexSearchService) {
        self.service = service
    }

    public func searchPage(_ query: SearchQuery, cursor: String?) async throws -> SearchPage {
        let response: NetworkWorksResponse
        do {
            response = try await service.searchWorks(query.worksSearchRequest(cursor: cursor))
        } catch let failure as NetworkFailure {
            throw failure.asSearchError()
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw SearchError.unexpected
        }
        return SearchPage(
            papers: response.results.map { $0.asPaper() },
            totalCount: response.meta.count,
            nextCursor: response.results.isEmpty ? nil : response.meta.nextCursor
        )
    }
}
