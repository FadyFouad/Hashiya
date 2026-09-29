import Foundation

/// The iOS entry of the app's remote config.
public struct PlatformAppConfig: Decodable, Equatable, Sendable {
    public let minimumBuild: Int?
    public let storeUrl: String?

    public init(minimumBuild: Int?, storeUrl: String?) {
        self.minimumBuild = minimumBuild
        self.storeUrl = storeUrl
    }
}

public protocol AppConfigService: Sendable {
    /// The iOS entry, or nil when the file has none. Throws `NetworkFailure` when the file can't be fetched or read.
    func iosConfig() async throws -> PlatformAppConfig?
}

/// Reads the app's remote config from GitHub Pages. It has its own URLSession with short timeouts, sends no
/// OpenAlex key and nothing about the user, and logs nothing. Cancelling the calling task cancels the request.
public final class AppConfigClient: AppConfigService {
    public static let url = URL(string: "https://fadyfouad.github.io/Hashiya-Privacy-Policy/app-config.json")!

    private struct File: Decodable {
        let ios: PlatformAppConfig?
    }

    private let session: URLSession
    private let url: URL

    public init(session: URLSession = URLSession(configuration: AppConfigClient.makeConfiguration()), url: URL = AppConfigClient.url) {
        self.session = session
        self.url = url
    }

    /// Ephemeral, with 5-second timeouts, so a slow GitHub never holds the check for long.
    public static func makeConfiguration() -> URLSessionConfiguration {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 5
        configuration.timeoutIntervalForResource = 5
        return configuration
    }

    public func iosConfig() async throws -> PlatformAppConfig? {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(from: url)
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        } catch is CancellationError {
            throw CancellationError()
        } catch is URLError {
            throw NetworkFailure.connectivity
        } catch {
            throw NetworkFailure.unknown
        }
        guard let http = response as? HTTPURLResponse else { throw NetworkFailure.unknown }
        guard (200...299).contains(http.statusCode) else {
            throw NetworkFailure.http(code: http.statusCode, usedUserKey: false)
        }
        do {
            return try JSONDecoder().decode(File.self, from: data).ios
        } catch {
            throw NetworkFailure.malformedResponse
        }
    }
}
