import HashiyaDiagnostics
import HashiyaTesting
import Testing

struct DiagnosticsStartupTests {
    private func launch(isLive: Bool, crashOn: Bool = true, analyticsOn: Bool = true) -> (FakeCrashReporting, FakeAnalytics) {
        let crash = FakeCrashReporting()
        let analytics = FakeAnalytics()
        let defaults = TestDefaults.make()
        let privacy = PrivacySettings(defaults: defaults)
        privacy.setCrashReportsEnabled(crashOn)
        privacy.setAnalyticsEnabled(analyticsOn)
        DiagnosticsLaunch.apply(
            diagnostics: Diagnostics(crash: crash, analytics: analytics, isLive: isLive),
            privacy: privacy, languageCode: "ar", librarySize: 120, hasOwnKey: true
        )
        return (crash, analytics)
    }

    @Test func aLiveBuildFollowsTheSwitches() {
        let (crash, analytics) = launch(isLive: true, crashOn: true, analyticsOn: false)
        #expect(crash.enabledCalls == [true])
        #expect(analytics.enabledCalls == [false])
    }

    @Test func aBuildThatIsNotLiveNeverEnablesCollection() {
        let (crash, analytics) = launch(isLive: false)
        #expect(crash.enabledCalls == [false])
        #expect(analytics.enabledCalls == [false])
    }

    @Test func setsTheKeysAndProperties() {
        let (crash, analytics) = launch(isLive: true)
        #expect(crash.keys == [.language: "ar", .librarySizeBucket: "51-500", .backupInProgress: "none"])
        #expect(analytics.properties == [.language: "ar", .librarySizeBucket: "51-500", .hasOwnKey: "yes"])
    }
}
