/// The user's own OpenAlex key, read on every request. Nil or blank means "use the built-in key".
public protocol UserAPIKeySource: Sendable {
    var userKey: String? { get }
}
