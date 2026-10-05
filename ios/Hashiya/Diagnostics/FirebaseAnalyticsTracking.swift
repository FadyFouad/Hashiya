import FirebaseAnalytics
import HashiyaDiagnostics

/// Release builds' usage statistics: the closed events and properties of `AnalyticsEvent`/`AnalyticsProperty`, nothing
/// else. Nothing is sent while the switch is off. Turning it off clears the user properties too, so turning it on again
/// sets them again from their last values.
final class FirebaseAnalyticsTracking: AnalyticsTracking {
    private let analyticsSwitch = AnalyticsSwitch()

    func log(_ event: AnalyticsEvent) {
        guard analyticsSwitch.isOn else { return }
        Analytics.logEvent(event.name, parameters: event.parameters)
    }

    func setProperty(_ property: AnalyticsProperty, _ value: some ClosedValue) {
        analyticsSwitch.setProperty(property, value, send: Self.send)
    }

    func setEnabled(_ isOn: Bool) {
        analyticsSwitch.setEnabled(isOn, apply: { isOn in
            Analytics.setConsent([.analyticsStorage: .granted, .adStorage: .denied, .adUserData: .denied, .adPersonalization: .denied])
            Analytics.setAnalyticsCollectionEnabled(isOn)
            if !isOn { Analytics.resetAnalyticsData() }
        }, send: Self.send)
    }

    private static func send(_ property: AnalyticsProperty, _ value: String) {
        Analytics.setUserProperty(value, forName: property.rawValue)
    }
}
