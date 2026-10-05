import Foundation

/// Sends GET requests to OpenAlex for both clients. Without a user key, `quota` picks the route (shared: the built-in
/// key or the limits' proxy; else keyless) and learns from 429s when a budget is used up. `GET /works` (search and
/// filter lists) is metered and cached; `GET /works/{id}` is free and always goes out. Encodes the query, logs (Debug
/// only, key redacted) and classifies failures.
struct OpenAlexHTTP: Sendable {
    let session: URLSession
    let baseURL: URL
    let builtInKey: String?
    let userKeySource: any UserAPIKeySource
    /// Nil: the user's key or the built-in key, with no routing, cap or fallback.
    let quota: OpenAlexQuota?
    let cache: SearchCache?
    let sleep: @Sendable (Duration) async throws -> Void
    let log: @Sendable (String) -> Void

    /// The decoded body of a 2xx response; a body that doesn't decode is `malformedResponse` and isn't cached.
    /// Throws `NetworkFailure` or `CancellationError`.
    func get<T: Decodable>(_ type: T.Type, path: String, query: [(name: String, value: String)]) async throws -> T {
        try await getRouted(type, path: path, query: query).0
    }

    /// Like `get(_:path:query:)`, and says how the response was obtained (`.cached` for a cache hit).
    func getRouted<T: Decodable>(_ type: T.Type, path: String, query: [(name: String, value: String)]) async throws -> (T, RequestRoute) {
        let metered = path == "/works"
        let cacheKey = metered ? SearchCache.key(path: path, query: query) : nil
        if let cacheKey, let cached = cache?.data(for: cacheKey), let value = try? Self.decode(type, from: cached) {
            return (value, .cached)
        }
        let (data, route) = try await send(path: path, query: query, metered: metered)
        let value = try Self.decode(type, from: data)
        if let cacheKey { cache?.store(data, for: cacheKey) }
        return (value, route)
    }

    /// The response body of a 2xx response, never cached. Throws `NetworkFailure` or `CancellationError`.
    func get(path: String, query: [(name: String, value: String)]) async throws -> Data {
        try await send(path: path, query: query, metered: path == "/works").0
    }

    static func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        do {
            return try JSONDecoder().decode(type, from: data)
        } catch is DecodingError {
            throw NetworkFailure.malformedResponse
        } catch {
            throw NetworkFailure.unknown
        }
    }

    private func send(path: String, query: [(name: String, value: String)], metered: Bool) async throws -> (Data, RequestRoute) {
        if let userKey = userKeySource.userKey.flatMap(nonBlank) {
            return (try await perform(base: baseURL, key: userKey, path: path, query: query).body(usedUserKey: true), .user)
        }
        guard let quota else {
            return (try await perform(base: baseURL, key: builtInKey.flatMap(nonBlank), path: path, query: query).body(usedUserKey: false), .shared)
        }

        var route: OpenAlexRoute? = metered ? quota.meteredRoute() : quota.lookupRoute()
        var waitedOnThisRoute = false
        while let current = route {
            let proxy = current == .shared ? quota.limits.baseURL : nil
            let key = current == .shared && proxy == nil ? builtInKey.flatMap(nonBlank) : nil
            if metered, current == .shared { quota.recordSharedCall() }

            let reply: Reply
            do {
                reply = try await perform(base: proxy ?? baseURL, key: key, path: path, query: query)
            } catch NetworkFailure.connectivity where proxy != nil {
                route = quota.route(after: current, metered: metered)
                waitedOnThisRoute = false
                continue
            }

            switch reply.status {
            case 200...299:
                return (reply.data, current == .shared ? .shared : .keyless)
            case 429 where reply.budgetUsedUp:
                quota.markUsedUp(current, resetIn: reply.resetSeconds)
                route = quota.route(after: current, metered: metered)
                waitedOnThisRoute = false
            case 429 where !waitedOnThisRoute:
                waitedOnThisRoute = true
                try await sleep(.seconds(min(reply.retryAfterSeconds ?? 1, 3)))
            case 500...599 where proxy != nil:
                route = quota.route(after: current, metered: metered)
                waitedOnThisRoute = false
            default:
                throw NetworkFailure.http(code: reply.status, usedUserKey: false)
            }
        }
        if metered {
            // Out of routes but not out for the day: the proxy failed and keyless is used up.
            guard quota.meteredRoute() == nil else { throw NetworkFailure.http(code: 503, usedUserKey: false) }
            throw NetworkFailure.dailyLimit(resetAt: quota.nextAvailable())
        }
        throw NetworkFailure.http(code: 429, usedUserKey: false)
    }

    /// One response, whatever its status.
    private struct Reply {
        let status: Int
        let data: Data
        let response: HTTPURLResponse

        /// `X-RateLimit-Remaining` at or below zero: the budget is used up for today.
        var budgetUsedUp: Bool {
            guard let remaining = number("X-RateLimit-Remaining") else { return false }
            return remaining <= 0
        }

        /// `X-RateLimit-Reset`: seconds until the budget resets.
        var resetSeconds: TimeInterval? { number("X-RateLimit-Reset") }

        var retryAfterSeconds: Double? { number("Retry-After").map { max($0, 0) } }

        func body(usedUserKey: Bool) throws -> Data {
            guard (200...299).contains(status) else { throw NetworkFailure.http(code: status, usedUserKey: usedUserKey) }
            return data
        }

        private func number(_ header: String) -> Double? {
            // "nan" and "inf" parse as Doubles; they mean nothing here.
            response.value(forHTTPHeaderField: header)
                .flatMap { Double($0.trimmingCharacters(in: .whitespaces)) }
                .flatMap { $0.isFinite ? $0 : nil }
        }
    }

    private func perform(base: URL, key: String?, path: String, query: [(name: String, value: String)]) async throws -> Reply {
        var items = query
        if let key { items.append((name: "api_key", value: key)) }

        guard var components = URLComponents(url: base, resolvingAgainstBaseURL: false) else {
            throw NetworkFailure.unknown
        }
        // Keeps a proxy's own path ("/openalex" + "/works"); a trailing slash on the base is dropped first.
        let basePath = components.percentEncodedPath.hasSuffix("/") ? String(components.percentEncodedPath.dropLast()) : components.percentEncodedPath
        components.percentEncodedPath = basePath + path
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
        return Reply(status: http.statusCode, data: data, response: http)
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
