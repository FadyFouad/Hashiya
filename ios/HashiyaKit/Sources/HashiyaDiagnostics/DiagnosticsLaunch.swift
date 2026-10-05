/// What the app does with diagnostics at launch, before anything else can fail: collection follows the switches (only
/// in live builds), then the crash keys and the analytics user properties are set.
public enum DiagnosticsLaunch {
    public static func apply(diagnostics: Diagnostics, privacy: PrivacySettings, languageCode: String, librarySize: Int?, hasOwnKey: Bool) {
        diagnostics.crash.setEnabled(diagnostics.isLive && privacy.crashReportsEnabled)
        diagnostics.analytics.setEnabled(diagnostics.isLive && privacy.analyticsEnabled)
        let language = languageKey(languageCode)
        diagnostics.crash.setKey(.language, language)
        diagnostics.crash.setKey(.backupInProgress, BackupPhase.none)
        diagnostics.analytics.setProperty(.language, language)
        diagnostics.analytics.setProperty(.hasOwnKey, hasOwnKey ? YesNo.yes : YesNo.no)
        if let librarySize {
            let bucket = librarySizeBucket(librarySize)
            diagnostics.crash.setKey(.librarySizeBucket, bucket)
            diagnostics.analytics.setProperty(.librarySizeBucket, bucket)
        }
    }
}
