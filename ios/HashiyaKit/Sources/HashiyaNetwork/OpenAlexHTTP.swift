import Foundation

/// Sends GET requests to OpenAlex: adds the API key, encodes the query, logs (Debug only, key redacted)
/// and classifies failures. Shared by the OpenAlex clients.
struct OpenAlexHTTP: Sendable {
    let session: URLSession
    let baseURL: URL
    let builtInKey: String?
    let userKeySource: any UserAPIKeySource
    let log: @Sendable (String) -> Void

    /// The response body of a 2xx response. Throws `NetworkFailure` or `CancellationError`.
    func get(path: String, query: [(name: String, value: String)]) async throws -> Data {
        let userKey = userKeySource.userKey.flatMap(nonBlank)
        let key = userKey ?? builtInKey.flatMap(nonBlank)
        var items = query
        if let key { items.append((name: "api_key", value: key)) }

        guard var components = URLComponents(url: baseURL, resolvingAgainstBaseURL: false) else {
            throw NetworkFailure.unknown
        }
        components.percentEncodedPath = components.percentEncodedPath + path
        components.percentEncodedQueryItems = items.map { URLQueryItem(name: $0.name, value: Self.encode($0.value)) }
        guard let url = components.url else { throw NetworkFailure.unknown }

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(from: url)
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        } catch is CancellationError {
            throw CancellationError()
        } catch is URLError {
            log(RequestLog.line(method: "GET", url: url, outcome: "\(NetworkFailure.connectivity)"))
            throw NetworkFailure.connectivity
        } catch {
            throw NetworkFailure.unknown
        }

        guard let http = response as? HTTPURLResponse else { throw NetworkFailure.unknown }
        log(RequestLog.line(method: "GET", url: url, outcome: "\(http.statusCode)"))
        guard (200...299).contains(http.statusCode) else {
            throw NetworkFailure.http(code: http.statusCode, usedUserKey: userKey != nil)
        }
        return data
    }

    /// Percent-encodes everything outside the RFC 3986 unreserved set, so "+", "&", "=" and "%" survive.
    static func encode(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: unreserved) ?? ""
    }

    private static let unreserved = CharacterSet(
        charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    )

    private func nonBlank(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
