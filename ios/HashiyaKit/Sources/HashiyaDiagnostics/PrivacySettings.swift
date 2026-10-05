import Foundation

/// The two Privacy switches, both on by default. Kept in the app's own defaults; the Share Extension never reads them.
/// `UserDefaults` is thread-safe.
public struct PrivacySettings: @unchecked Sendable {
    public static let crashReportsKey = "privacy.crashReportsEnabled"
    public static let analyticsKey = "privacy.analyticsEnabled"

    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public var crashReportsEnabled: Bool { defaults.object(forKey: Self.crashReportsKey) as? Bool ?? true }
    public var analyticsEnabled: Bool { defaults.object(forKey: Self.analyticsKey) as? Bool ?? true }

    public func setCrashReportsEnabled(_ enabled: Bool) { defaults.set(enabled, forKey: Self.crashReportsKey) }
    public func setAnalyticsEnabled(_ enabled: Bool) { defaults.set(enabled, forKey: Self.analyticsKey) }
}
