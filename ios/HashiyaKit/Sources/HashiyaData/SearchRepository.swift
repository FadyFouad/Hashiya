import HashiyaDiagnostics
import HashiyaModel
import HashiyaNetwork

public protocol SearchRepository: Sendable {
    /// One page. `cursor` nil = first page. Throws `SearchError` or `CancellationError`.
    func searchPage(_ query: SearchQuery, cursor: String?) async throws -> SearchPage
    /// Pages one search may load; read when each page arrives.
    var maxPagesPerQuery: Int { get }
}

public struct SearchPage: Equatable, Sendable {
    public var papers: [Paper]
    /// OpenAlex's `meta.count`.
    public var totalCount: Int64
    /// Nil when `meta.next_cursor` is null or the page has no results.
    public var nextCursor: String?
    /// How the page was obtained; nil when the service doesn't say.
    public var route: SearchRoute?
    /// The research area worked out from a first page's topics; `unknown` for later pages.
    public var category: ResearchCategory

    public init(
        papers: [Paper], totalCount: Int64, nextCursor: String?,
        route: SearchRoute? = nil, category: ResearchCategory = .unknown
    ) {
        self.papers = papers
        self.totalCount = totalCount
        self.nextCursor = nextCursor
        self.route = route
        self.category = category
    }
}

public struct OpenAlexSearchRepository: SearchRepository {
    private let service: any OpenAlexSearchService
    private let maxPages: @Sendable () -> Int

    public init(
        service: any OpenAlexSearchService,
        maxPagesPerQuery: @escaping @Sendable () -> Int = { OpenAlexLimits.defaults.maxPagesPerQuery }
    ) {
        self.service = service
        maxPages = maxPagesPerQuery
    }

    public var maxPagesPerQuery: Int { maxPages() }

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
            nextCursor: response.results.isEmpty ? nil : response.meta.nextCursor,
            route: response.route.map(SearchRoute.init),
            category: cursor == nil
                ? ResearchCategory.classify(response.results.map { TopicIDs(subfield: $0.primaryTopic?.subfieldID, field: $0.primaryTopic?.fieldID, domain: $0.primaryTopic?.domainID) })
                : .unknown
        )
    }
}

extension SearchRoute {
    init(_ route: RequestRoute) {
        switch route {
        case .user: self = .user
        case .shared: self = .shared
        case .keyless: self = .keyless
        case .cached: self = .cached
        }
    }
}
