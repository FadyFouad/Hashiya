import FirebaseCore
import Foundation
import HashiyaDiagnostics

/// Builds the app's diagnostics. Release builds configure Firebase and get the live reporters; Debug builds (and so the
/// snapshot and UI tests) never configure Firebase and get `.none`.
enum DiagnosticsStartup {
    static let testCrashArgument = "-hashiya-test-crash"
    static let testCrashFlag = "diagnostics.crashOnNextLaunch"

    static func make(arguments: [String] = ProcessInfo.processInfo.arguments) -> Diagnostics {
        #if DEBUG
        return .none
        #else
        FirebaseApp.configure()
        let diagnostics = Diagnostics(crash: FirebaseCrashReporting(), analytics: FirebaseAnalyticsTracking(), isLive: true)
        // Collection follows the switches at once, so a failure while opening the library is still reported.
        let privacy = PrivacySettings()
        diagnostics.crash.setEnabled(privacy.crashReportsEnabled)
        diagnostics.analytics.setEnabled(privacy.analyticsEnabled)
        scheduleTestCrashIfAsked(arguments: arguments)
        return diagnostics
        #endif
    }

    /// Release only: a launch with `-hashiya-test-crash` (from Xcode) arms one crash for the next launch from the Home
    /// Screen, which happens two seconds in, without a debugger attached.
    private static func scheduleTestCrashIfAsked(arguments: [String]) {
        let defaults = UserDefaults.standard
        if arguments.contains(testCrashArgument) {
            defaults.set(true, forKey: testCrashFlag)
        } else if defaults.bool(forKey: testCrashFlag) {
            defaults.removeObject(forKey: testCrashFlag)
            // A constant message: crash reports include trap messages, so they never hold anything a person typed or read.
            DispatchQueue.main.asyncAfter(deadline: .now() + 2) { fatalError("Hashiya test crash") }
        }
    }
}
