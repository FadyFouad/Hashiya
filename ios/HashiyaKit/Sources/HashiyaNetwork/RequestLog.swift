import Foundation
import os

/// Debug-only request logging. The `api_key` value is always replaced by "██".
public enum RequestLog {
    public static let redactedKey = "██"

    /// Logs through `os.Logger` in Debug builds; does nothing in Release builds.
    public static let debug: @Sendable (String) -> Void = { line in
        #if DEBUG
        logger.debug("\(line, privacy: .public)")
        #endif
    }

    /// "GET /works?search=bert&api_key=██ → 200": method, path and query, with the key redacted.
    public static func line(method: String, url: URL, outcome: String) -> String {
        "\(method) \(redactedPathAndQuery(url)) → \(outcome)"
    }

    static func redactedPathAndQuery(_ url: URL) -> String {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return "?" }
        let query = components.percentEncodedQueryItems?
            .map { item in "\(item.name)=\(item.name == "api_key" ? redactedKey : item.value ?? "")" }
            .joined(separator: "&")
        return components.percentEncodedPath + (query.map { "?\($0)" } ?? "")
    }

    private static let logger = Logger(subsystem: "com.etatech.hashiya", category: "network")
}
