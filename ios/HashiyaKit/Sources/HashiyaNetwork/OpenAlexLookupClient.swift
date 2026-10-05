import Foundation

/// `GET https://api.openalex.org/works/{id}` and `GET /works?filter=…`, with the search client's key
/// handling, logging and failure classification.
public final class OpenAlexLookupClient: OpenAlexLookupService, OpenAlexPdfLinksService {
    /// Only what a PDF download needs when the stored link fails: every place the work is hosted.
    public static let pdfLocationFields = "id,locations"

    private let http: OpenAlexHTTP

    /// Pass the search client's session: both clients share one OpenAlex URLSession.
    /// - Parameters:
    ///   - builtInKey: the key built into the app, or nil; used when the user has none.
    ///   - userKeySource: the user's override, read on every request.
    ///   - quota: picks the route without a user key; nil sends the built-in key with no routing (tests of requests).
    ///   - cache: answers repeated searches and filter lists.
    ///   - sleep: waits before retrying after a per-second limit.
    ///   - log: Debug request logging; receives lines with the key redacted.
    public init(
        session: URLSession,
        builtInKey: String?,
        userKeySource: any UserAPIKeySource,
        quota: OpenAlexQuota? = nil,
        cache: SearchCache? = nil,
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) },
        baseURL: URL = OpenAlexSession.baseURL,
        log: @escaping @Sendable (String) -> Void = RequestLog.debug
    ) {
        http = OpenAlexHTTP(
            session: session, baseURL: baseURL, builtInKey: builtInKey, userKeySource: userKeySource,
            quota: quota, cache: cache, sleep: sleep, log: log
        )
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

    public func pdfLocations(openAlexID: String) async throws -> [NetworkLocation] {
        let data: Data
        do {
            data = try await http.get(
                path: "/works/" + Self.pathSegment(openAlexID),
                query: [(name: "select", value: Self.pdfLocationFields)]
            )
        } catch let NetworkFailure.http(code, _) where code == 404 || code == 400 {
            return []
        }
        return try decode(NetworkWorkLocations.self, from: data).locations
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
