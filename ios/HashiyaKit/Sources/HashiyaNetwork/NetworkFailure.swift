import Foundation

/// Why a request to OpenAlex failed. It never carries the underlying error: a `URLError`'s
/// `failingURL` would contain the `api_key` query item.
public enum NetworkFailure: Error, Equatable, Sendable, CustomStringConvertible {
    /// Any transport error: offline, timeout, DNS, TLS.
    case connectivity
    /// A status outside 200–299. `usedUserKey` tells a rejected user key from a rejected built-in key.
    case http(code: Int, usedUserKey: Bool)
    /// The body could not be decoded.
    case malformedResponse
    case unknown
    /// Every route for a search is used up until `resetAt`; no request was sent.
    case dailyLimit(resetAt: Date)

    /// The case name only, so the key can never reach a log or a message through it.
    public var description: String {
        switch self {
        case .connectivity: "connectivity"
        case .http: "http"
        case .malformedResponse: "malformedResponse"
        case .unknown: "unknown"
        case .dailyLimit: "dailyLimit"
        }
    }
}
