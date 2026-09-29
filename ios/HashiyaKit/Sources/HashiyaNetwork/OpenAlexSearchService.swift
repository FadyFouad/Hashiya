/// The parameters of `GET /works` that callers choose. `select`, `per_page` defaults and `api_key`
/// are the client's business.
public struct WorksSearchRequest: Equatable, Sendable {
    public var search: String
    public var filter: String?
    public var sort: String?
    public var cursor: String
    public var perPage: Int

    public init(search: String, filter: String?, sort: String?, cursor: String, perPage: Int) {
        self.search = search
        self.filter = filter
        self.sort = sort
        self.cursor = cursor
        self.perPage = perPage
    }
}

/// Keyword search on OpenAlex. Throws `NetworkFailure` or `CancellationError`.
public protocol OpenAlexSearchService: Sendable {
    func searchWorks(_ request: WorksSearchRequest) async throws -> NetworkWorksResponse
}
