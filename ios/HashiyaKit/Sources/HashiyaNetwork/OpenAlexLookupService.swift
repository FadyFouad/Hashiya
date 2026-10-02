/// Single-work lookups on OpenAlex. Throws `NetworkFailure` or `CancellationError`.
public protocol OpenAlexLookupService: Sendable {
    /// The work OpenAlex resolves `id` to (e.g. "doi:10.1038/nature14539"), or nil on HTTP 404 or 400.
    func work(id: String) async throws -> NetworkWork?
    func works(filter: String, perPage: Int) async throws -> NetworkWorksResponse
}

/// Where else a saved paper's PDF may be, for when its stored link no longer gives it. Throws `NetworkFailure` or
/// `CancellationError`. Mirrors Android's `OpenAlexPdfLinksDataSource`.
public protocol OpenAlexPdfLinksService: Sendable {
    /// Every location OpenAlex lists for the work `openAlexID` (e.g. "W2626778328"), in OpenAlex's order, or none on
    /// HTTP 404 or 400.
    func pdfLocations(openAlexID: String) async throws -> [NetworkLocation]
}

/// Paper titles from arXiv's API, used only to check OpenAlex matches. Throws `NetworkFailure` or `CancellationError`.
public protocol ArxivTitleService: Sendable {
    /// arXiv's title for `id` ("1810.04805", "hep-th/9901001"), or nil when arXiv has no such paper.
    func title(id: String) async throws -> String?
}
