import HashiyaModel
import HashiyaNetwork

extension NetworkFailure {
    public func asSearchError() -> SearchError {
        switch self {
        case .connectivity:
            .offline
        case let .http(code, usedUserKey) where code == 401 || code == 403:
            usedUserKey ? .invalidUserKey : .serviceUnavailable
        case .http(429, _):
            .rateLimited
        case let .http(code, _) where (500...599).contains(code):
            .serviceUnavailable
        case let .dailyLimit(resetAt):
            .dailyLimit(resetAt: resetAt)
        case .http, .malformedResponse, .unknown:
            .unexpected
        }
    }
}
