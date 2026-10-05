import Foundation
import HashiyaDiagnostics
import HashiyaTesting
import Testing

struct DiagnosticsTests {
    @Test(arguments: [(0, "0"), (1, "1-50"), (50, "1-50"), (51, "51-500"), (500, "51-500"), (501, "501-5000"), (5000, "501-5000"), (5001, "5000+")])
    func librarySizeBuckets(papers: Int, bucket: String) {
        #expect(librarySizeBucket(papers).rawValue == bucket)
    }

    @Test(arguments: [("en", "en"), ("ar", "ar"), ("fr", "system"), ("", "system"), ("ar-EG", "system"), ("EN", "system")])
    func languageKeys(code: String, key: String) {
        #expect(languageKey(code).rawValue == key)
    }

    @Test(arguments: [(0, ResultsBucket.zero), (1, .upTo25), (25, .upTo25), (26, .upTo200), (200, .upTo200), (201, .over200)])
    func resultBuckets(count: Int, bucket: ResultsBucket) {
        #expect(ResultsBucket(count: Int64(count)) == bucket)
    }

    @Test func crashKeysAndSitesAreTheClosedLists() {
        #expect(CrashKey.allCases.map(\.rawValue) == ["screen", "language", "librarySizeBucket", "backupInProgress"])
        #expect(CrashSite.allCases.map(\.rawValue) == ["migration", "databaseOpen", "restore", "export", "pdfStore", "unexpectedUiError"])
    }

    @Test func aReportedErrorKeepsOnlyTypeDomainAndCode() {
        let error = NSError(domain: NSCocoaErrorDomain, code: 640, userInfo: [
            NSFilePathErrorKey: "/var/mobile/Containers/Shared/AppGroup/X/Attention Is All You Need.pdf",
            NSLocalizedDescriptionKey: "Couldn't write “Attention Is All You Need.pdf”",
        ])
        let reported = ReportedError(error: error, site: .pdfStore)
        #expect(reported == ReportedError(site: .pdfStore, type: "NSError", domain: NSCocoaErrorDomain, code: 640))
        #expect(!"\(reported)".contains("Attention"))
    }

    @Test func aSwiftErrorReportsItsTypeName() {
        enum StoreFailure: Error { case diskFull(path: String) }
        let reported = ReportedError(error: StoreFailure.diskFull(path: "/secret/notes"), site: .restore)
        #expect(reported.type.hasSuffix("StoreFailure"))
        #expect(!"\(reported)".contains("secret"))
    }

    @Test func everyEventHasItsSpecNameAndOnlyClosedValues() {
        let cases: [(AnalyticsEvent, String, [String: String])] = [
            (.search(kind: .keyword, hasFilters: true, route: .shared, results: .over200, category: .ai), "search",
             ["kind": "keyword", "has_filters": "yes", "route": "shared", "results_bucket": "200+", "category": "ai"]),
            (.search(kind: .doi, hasFilters: false, route: .user, results: .upTo25, category: nil), "search",
             ["kind": "doi", "has_filters": "no", "route": "user", "results_bucket": "1-25"]),
            (.searchMore(page: 2), "search_more", ["page": "2"]),
            (.searchMore(page: 99), "search_more", ["page": "40"]),
            (.searchLimitReached(.pageCap), "search_limit_reached", ["kind": "page_cap"]),
            (.searchLimitReached(.daily), "search_limit_reached", ["kind": "daily"]),
            (.paperSaved(from: .lookup), "paper_saved", ["from": "lookup"]),
            (.paperRemoved, "paper_removed", [:]),
            (.noteEdited, "note_edited", [:]),
            (.collectionCreated, "collection_created", [:]),
            (.paperAddedToCollection, "paper_added_to_collection", [:]),
            (.export(format: .backup, withPdfs: true), "export", ["format": "backup", "with_pdfs": "yes"]),
            (.restore(succeeded: false), "restore", ["result": "failed"]),
            (.pdfOpened(source: .attached), "pdf_opened", ["source": "attached"]),
            (.pdfDownloaded(succeeded: true), "pdf_downloaded", ["result": "ok"]),
            (.screenView(.reader), "screen_view", ["screen": "reader"]),
        ]
        for (event, name, parameters) in cases {
            #expect(event.name == name)
            #expect(event.parameters == parameters)
        }
    }

    @Test func privacySettingsDefaultOnAndPersist() {
        let defaults = TestDefaults.make()
        #expect(PrivacySettings(defaults: defaults).crashReportsEnabled)
        #expect(PrivacySettings(defaults: defaults).analyticsEnabled)
        PrivacySettings(defaults: defaults).setAnalyticsEnabled(false)
        #expect(!PrivacySettings(defaults: defaults).analyticsEnabled)
        #expect(PrivacySettings(defaults: defaults).crashReportsEnabled)
    }

    @Test func screenShownSetsTheCrashKeyAndLogsAScreenView() {
        let crash = FakeCrashReporting()
        let analytics = FakeAnalytics()
        Diagnostics(crash: crash, analytics: analytics, isLive: false).screenShown(.settings)
        #expect(crash.keys[.screen] == "settings")
        #expect(analytics.events == [.screenView(.settings)])
    }
}
