import SwiftUI

/// The crash reporter and analytics tracker a part of the app was given. `.none` (the default everywhere) reports and
/// counts nothing: Debug builds, the Share Extension and tests.
public struct Diagnostics: Sendable {
    public let crash: any CrashReporting
    public let analytics: any AnalyticsTracking
    /// True only in Release builds of the app, where Firebase is configured; switches only turn collection on then.
    public let isLive: Bool

    public init(crash: any CrashReporting, analytics: any AnalyticsTracking, isLive: Bool) {
        self.crash = crash
        self.analytics = analytics
        self.isLive = isLive
    }

    public static let none = Diagnostics(crash: NoCrashReporting(), analytics: NoAnalytics(), isLive: false)

    /// The visible screen changed: the crash `screen` key and a `screen_view` event.
    public func screenShown(_ screen: Screen) {
        crash.setKey(.screen, screen.rawValue)
        analytics.log(.screenView(screen))
    }
}

extension EnvironmentValues {
    /// Set once by the app's root views; feature screens read it to report which screen is showing.
    @Entry public var diagnostics: Diagnostics = .none
}
