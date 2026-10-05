import Foundation

/// The `openAlex` section of the remote config: how this device may use the shared OpenAlex budget.
public struct OpenAlexLimits: Codable, Equatable, Sendable {
    /// Metered calls per device per UTC day on the shared route; 0 turns the shared route off for metered calls.
    public var dailyDeviceCalls: Int
    /// Pages one search can load.
    public var maxPagesPerQuery: Int
    /// A proxy for the shared route, or nil to send the built-in key to api.openalex.org.
    public var baseURL: URL?

    public init(dailyDeviceCalls: Int, maxPagesPerQuery: Int, baseURL: URL?) {
        self.dailyDeviceCalls = dailyDeviceCalls
        self.maxPagesPerQuery = maxPagesPerQuery
        self.baseURL = baseURL
    }

    public static let defaults = OpenAlexLimits(dailyDeviceCalls: 60, maxPagesPerQuery: 8, baseURL: nil)

    /// Reads the section field by field: a missing, out-of-range, wrong-type or non-https value falls back to its
    /// default and the others still apply. Anything but an object means all defaults.
    public static func parse(_ section: Any?) -> OpenAlexLimits {
        guard let fields = section as? [String: Any] else { return .defaults }
        return OpenAlexLimits(
            dailyDeviceCalls: wholeNumber(fields["dailyDeviceCalls"], in: 0...1000) ?? defaults.dailyDeviceCalls,
            maxPagesPerQuery: wholeNumber(fields["maxPagesPerQuery"], in: 1...40) ?? defaults.maxPagesPerQuery,
            baseURL: httpsURL(fields["baseUrl"])
        )
    }

    private static func wholeNumber(_ value: Any?, in range: ClosedRange<Int>) -> Int? {
        // JSONSerialization gives booleans as NSNumber too.
        guard let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
        let double = number.doubleValue
        guard double.rounded() == double, let whole = Int(exactly: double), range.contains(whole) else { return nil }
        return whole
    }

    private static func httpsURL(_ value: Any?) -> URL? {
        guard let text = value as? String, let url = URL(string: text), url.scheme?.lowercased() == "https",
              let host = url.host(), !host.isEmpty else { return nil }
        return url
    }
}

/// The last valid `openAlex` section, kept so a failed fetch or a launch offline still has it.
/// `UserDefaults` is thread-safe.
public struct OpenAlexLimitsStore: @unchecked Sendable {
    public static let key = "openAlex.limits"

    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public var limits: OpenAlexLimits {
        guard let data = defaults.data(forKey: Self.key),
              let limits = try? JSONDecoder().decode(OpenAlexLimits.self, from: data) else { return .defaults }
        return limits
    }

    public func save(_ limits: OpenAlexLimits) {
        guard let data = try? JSONEncoder().encode(limits) else { return }
        defaults.set(data, forKey: Self.key)
    }
}
