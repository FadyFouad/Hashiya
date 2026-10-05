import FirebaseAnalytics
import HashiyaDiagnostics
import os

/// Release builds' usage statistics: the closed events and properties of `AnalyticsEvent`/`AnalyticsProperty`, nothing
/// else. Logging is ignored while the switch is off.
final class FirebaseAnalyticsTracking: AnalyticsTracking {
    private let enabled = OSAllocatedUnfairLock(initialState: false)

    func log(_ event: AnalyticsEvent) {
        guard enabled.withLock({ $0 }) else { return }
        Analytics.logEvent(event.name, parameters: event.parameters)
    }

    func setProperty(_ property: AnalyticsProperty, _ value: some ClosedValue) {
        Analytics.setUserProperty(value.rawValue, forName: property.rawValue)
    }

    func setEnabled(_ isOn: Bool) {
        enabled.withLock { $0 = isOn }
        Analytics.setConsent([.analyticsStorage: .granted, .adStorage: .denied, .adUserData: .denied, .adPersonalization: .denied])
        Analytics.setAnalyticsCollectionEnabled(isOn)
        if !isOn { Analytics.resetAnalyticsData() }
    }
}
