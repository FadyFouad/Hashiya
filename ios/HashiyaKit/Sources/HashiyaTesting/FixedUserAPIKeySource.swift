import HashiyaNetwork

/// A user key that never changes.
public struct FixedUserAPIKeySource: UserAPIKeySource {
    public let userKey: String?

    public init(_ userKey: String?) {
        self.userKey = userKey
    }
}
