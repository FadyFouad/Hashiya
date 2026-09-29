/// Why a search failed, as the UI tells the user.
public enum SearchError: Error, Equatable, Sendable {
    case offline
    case invalidUserKey
    case rateLimited
    case serviceUnavailable
    case unexpected
}
