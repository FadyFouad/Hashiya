import Foundation

/// `GET https://api.openalex.org/works/{id}` and `GET /works?filter=…`, with the search client's key
/// handling, logging and failure classification.
public final class OpenAlexLookupClient: OpenAlexLookupService {
    private let http: OpenAlexHTTP

    /// Pass the search client's session: both clients share one OpenAlex URLSession.
    public init(
        session: URLSession,
        builtInKey: String?,
        userKeySource: any UserAPIKeySource,
        baseURL: URL = OpenAlexSession.baseURL,
        log: @escaping @Sendable (String) -> Void = RequestLog.debug
    ) {
        http = OpenAlexHTTP(session: session, baseURL: baseURL, builtInKey: builtInKey, userKeySource: userKeySource, log: log)
    }

    public func work(id: String) async throws -> NetworkWork? {
        let data: Data
        do {
            data = try await http.get(path: "/works/" + Self.pathSegment(id), query: [(name: "select", value: OpenAlexSearchClient.selectFields)])
        } catch let NetworkFailure.http(code, _) where code == 404 || code == 400 {
            // Callers pass only well-formed IDs, so 400 also means OpenAlex has no such work.
            return nil
        }
        return try decode(NetworkWork.self, from: data)
    }

    public func works(filter: String, perPage: Int) async throws -> NetworkWorksResponse {
        let data = try await http.get(path: "/works", query: [
            (name: "filter", value: filter),
            (name: "per_page", value: String(perPage)),
            (name: "select", value: OpenAlexSearchClient.selectFields),
        ])
        return try decode(NetworkWorksResponse.self, from: data)
    }

    /// One path segment: everything but unreserved characters and ":" is percent-encoded, so "/", "#", ";",
    /// "<", ">", "(" and ")" reach OpenAlex inside the ID.
    static func pathSegment(_ id: String) -> String {
        id.addingPercentEncoding(withAllowedCharacters: segmentCharacters) ?? ""
    }

    private static let segmentCharacters = CharacterSet(
        charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~:"
    )

    private func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        do {
            return try JSONDecoder().decode(type, from: data)
        } catch is DecodingError {
            throw NetworkFailure.malformedResponse
        } catch {
            throw NetworkFailure.unknown
        }
    }
}
