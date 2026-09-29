/// Single-work lookups on OpenAlex. Throws `NetworkFailure` or `CancellationError`.
public protocol OpenAlexLookupService: Sendable {
    /// The work OpenAlex resolves `id` to (e.g. "doi:10.1038/nature14539"), or nil on HTTP 404 or 400.
    func work(id: String) async throws -> NetworkWork?
    func works(filter: String, perPage: Int) async throws -> NetworkWorksResponse
}

/// Paper titles from arXiv's API, used only to check OpenAlex matches. Throws `NetworkFailure` or `CancellationError`.
public protocol ArxivTitleService: Sendable {
    /// arXiv's title for `id` ("1810.04805", "hep-th/9901001"), or nil when arXiv has no such paper.
    func title(id: String) async throws -> String?
}
