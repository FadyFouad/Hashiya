import Foundation

/// The one URLSession used for OpenAlex.
public enum OpenAlexSession {
    public static let baseURL = URL(string: "https://api.openalex.org")!

    /// Ephemeral; fails at once when offline; 10 s idle limit (connecting and waiting for the first byte);
    /// 20 s for the whole request.
    public static func makeConfiguration() -> URLSessionConfiguration {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.waitsForConnectivity = false
        configuration.timeoutIntervalForRequest = 10
        configuration.timeoutIntervalForResource = 20
        return configuration
    }

    public static func make() -> URLSession {
        URLSession(configuration: makeConfiguration())
    }
}
