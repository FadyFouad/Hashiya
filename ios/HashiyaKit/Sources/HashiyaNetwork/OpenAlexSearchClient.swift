import Foundation

/// `GET https://api.openalex.org/works` keyword search.
public final class OpenAlexSearchClient: OpenAlexSearchService {
    public static let selectFields =
        "id,doi,display_name,publication_year,primary_location,authorships,cited_by_count,open_access,best_oa_location,abstract_inverted_index,type,biblio"

    private let http: OpenAlexHTTP

    /// - Parameters:
    ///   - builtInKey: the key built into the app, or nil; used when the user has none.
    ///   - userKeySource: the user's override, read on every request.
    ///   - log: Debug request logging; receives lines with the key redacted.
    public init(
        session: URLSession,
        builtInKey: String?,
        userKeySource: any UserAPIKeySource,
        baseURL: URL = OpenAlexSession.baseURL,
        log: @escaping @Sendable (String) -> Void = RequestLog.debug
    ) {
        http = OpenAlexHTTP(session: session, baseURL: baseURL, builtInKey: builtInKey, userKeySource: userKeySource, log: log)
    }

    public func searchWorks(_ request: WorksSearchRequest) async throws -> NetworkWorksResponse {
        var query: [(name: String, value: String)] = [(name: "search", value: request.search)]
        if let filter = request.filter { query.append((name: "filter", value: filter)) }
        if let sort = request.sort { query.append((name: "sort", value: sort)) }
        query.append((name: "per_page", value: String(request.perPage)))
        query.append((name: "cursor", value: request.cursor))
        query.append((name: "select", value: Self.selectFields))

        let data = try await http.get(path: "/works", query: query)
        do {
            return try JSONDecoder().decode(NetworkWorksResponse.self, from: data)
        } catch is DecodingError {
            throw NetworkFailure.malformedResponse
        } catch {
            throw NetworkFailure.unknown
        }
    }
}
