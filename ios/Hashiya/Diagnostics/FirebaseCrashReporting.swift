import FirebaseCrashlytics
import Foundation
import HashiyaDiagnostics

/// Release builds' crash reporter. Non-fatals go through `ReportedError`: site, type, domain and code only.
struct FirebaseCrashReporting: CrashReporting {
    func setEnabled(_ enabled: Bool) {
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(enabled)
        if !enabled { Crashlytics.crashlytics().deleteUnsentReports() }
    }

    func setKey(_ key: CrashKey, _ value: some ClosedValue) {
        Crashlytics.crashlytics().setCustomValue(value.rawValue, forKey: key.rawValue)
    }

    func record(_ error: any Error, site: CrashSite) {
        // Crashlytics keeps a non-fatal recorded while collection is off and sends it once it is on again.
        guard Crashlytics.crashlytics().isCrashlyticsCollectionEnabled() else { return }
        let reported = ReportedError(error: error, site: site)
        Crashlytics.crashlytics().record(error: NSError(domain: "\(site.rawValue).\(reported.domain)", code: reported.code, userInfo: ["type": reported.type]))
    }
}
