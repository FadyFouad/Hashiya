# iOS Crash Reporting and Usage Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Firebase Crashlytics and minimised Firebase Analytics to the iOS app, behind a closed-list diagnostics layer, with two Settings switches (on by default), crash non-fatals from the data-safety paths, the analytics events of the spec (including the on-device research category), a corrected privacy manifest, updated store answers, and the privacy-policy update that must ship first.

**Architecture:** A new dependency-free SwiftPM target `HashiyaDiagnostics` holds the protocols (`CrashReporting`, `AnalyticsTracking`), closed enums (keys, sites, events, research categories), the error sanitiser, the category classifier and the UserDefaults-backed `PrivacySettings`. Firebase appears only in the `Hashiya` app target (`FirebaseCrashReporting`, `FirebaseAnalyticsTracking`, `DiagnosticsStartup`), configured only in Release builds. Everything else receives a `Diagnostics` value that defaults to `.none` (no-ops), so the Share Extension, Debug builds and all tests never collect.

**Tech Stack:** Swift 6, SwiftUI, Swift Testing, XcodeGen, firebase-ios-sdk 12.19.2 (`FirebaseCrashlytics`, `FirebaseAnalyticsCore` — the variant without advertising-id support), GRDB (existing).

**Specs:** `docs/superpowers/specs/2026-10-04-crash-reporting-design.md` (iOS parts) and `docs/superpowers/specs/2026-10-05-analytics-design.md`. Where they differ, the analytics spec is newer.

**Starting point:** branch `feat/ios-diagnostics` from `main` **after PR #36 (iOS OpenAlex quota) is merged** — this plan uses its `OpenAlexQuota`/`OpenAlexHTTP` routing. Then `git merge origin/docs/analytics-spec` (or the branch holding this plan and the analytics spec) so the docs travel with the code. If #36 isn't merged yet, branch from `origin/feat/ios-openalex-quota` instead and rebase onto `main` once it is.

## Global Constraints

- Firebase only in the `Hashiya` app target; never in HashiyaKit targets or the Share Extension. Pinned exact: firebase-ios-sdk `12.19.2`; products `FirebaseCrashlytics` and `FirebaseAnalyticsCore`.
- Collection only in Release builds; never in Debug, unit, snapshot or UI tests. Info.plist: `FirebaseCrashlyticsCollectionEnabled` NO, `FIREBASE_ANALYTICS_COLLECTION_ENABLED` NO, `GOOGLE_ANALYTICS_IDFV_COLLECTION_ENABLED` NO, `GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS` NO, `FirebaseAutomaticScreenReportingEnabled` NO, `FirebaseAppDelegateProxyEnabled` NO.
- Consent at launch: analytics storage granted; ad storage, ad user data, ad personalization denied.
- Switches: **Send crash reports** and **Share usage statistics**, both on by default, independent, stored in `UserDefaults.standard` keys `privacy.crashReportsEnabled` / `privacy.analyticsEnabled`. Off → collection stops; crash off also deletes unsent reports; analytics off also calls `resetAnalyticsData()`.
- Closed lists only. Never sent anywhere: titles, DOIs, OpenAlex ids, search text, notes, collection names, file names/paths, URLs, the API key, error messages, topic names.
- Crash keys: `screen` (library/search/details/reader/settings/restore/export), `language` (en/ar/system), `librarySizeBucket` (0, 1-50, 51-500, 501-5000, 5000+), `backupInProgress` (none/export/restore). Sites: migration, databaseOpen, restore, export, pdfStore, unexpectedUiError. A non-fatal carries the site, the error's type name, NSError domain and code — never its message or userInfo.
- Non-fatal triggers: restore ends in `writeFailed` or `unreadable` (not `noSpace`/`busy`); export ends in `writeFailed`; a PDF write fails for a reason other than not-a-PDF/too-large; the generic catch blocks in the Settings export and Restore view models; database open/migration failure (recorded before the existing `fatalError`).
- Analytics events, parameters and values exactly as the analytics spec §4 / §4.1 (event names snake_case; parameter values are strings).
- Research category: from the first page's first 10 results with a `primary_topic`; ≥ 3 mapped and a single top category with ≥ 40% → that category, else `unknown`; keyword searches only; mapping table exactly spec §4.1.
- Copy (English / Arabic) exactly as written in the tasks; every new String Catalog key has a translated `ar` entry (`ios/scripts/check-translations.py`).
- The privacy-policy PR (Task 10) must be merged before any build with this work reaches users (TestFlight external testers or the App Store).
- Commits: author `Fady <fady.fouad.a@gmail.com>`; no AI attribution anywhere. Never print or commit `ios/Config/Secrets.xcconfig`.

## Rulings on the specs for iOS

- **`route` = `cached`** when the on-device search cache answered the first page (added to the spec).
- **Lookups' `route`:** lookups are free and never metered, so a lookup's `search` event sends `user` with a personal key, else `shared`.
- **Lookup failures** (`LookupState.failed`) send no `search` event; `found` sends `results_bucket` `1-25`, `notFound` sends `0`.
- **`paper_saved(from: share)`** is never sent on iOS: the Share Extension has no analytics (spec non-goal). The value stays in the enum for Android.
- **`paper_removed`** fires when a removal becomes final: the Library's Undo window expiring or being replaced by another removal; removing from Search, from Details opened from Search, or from an iPad paper window (no Undo there).
- **Test crash trigger:** a Release build launched once with `-hashiya-test-crash` (from Xcode, Release run configuration) stores a flag; the next launch from the Home Screen (no debugger) crashes two seconds after start and clears the flag. Debug builds ignore it. (Crashlytics doesn't report crashes while a debugger is attached, and Home Screen launches carry no arguments.)
- **The database-open `fatalError` message** no longer includes the error (its description can contain file paths); the error goes to Crashlytics as a sanitised non-fatal first.
- **Fatal crashes on iOS** are signal/Mach crash reports (stack frames only); no extra sanitising is needed, unlike Android's exception messages.

## Review Focus

1. A user who turns **Share usage statistics** off and relaunches must see it still off, and no event may be logged after the switch is off — Task 4 (UI test) and Task 3 (tracker guards) test it.
2. A search whose text is a paper title must produce events that contain none of its words; a note containing a DOI likewise — Task 6 tests it with the fake tracker.
3. OpenAlex topic ids in unexpected shapes (`"https://openalex.org/subfields/abc"`, a bare `"1702"`, missing subfield) must classify to `unknown` or fall through, never crash — Task 2 tests it.
4. A non-fatal for an error whose description contains a file path must send only type/domain/code — Task 1 tests the sanitiser with an NSError carrying a path in its userInfo and localized description.
5. Debug builds (and UI tests) must never configure Firebase — Task 3 guards with `#if DEBUG` and a test of `DiagnosticsStartup` with the no-op graph.

## How to run tests (this Mac)

```bash
cd ios && xcodegen generate --spec project.yml >/dev/null && cd ..
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya \
  -destination 'platform=iOS Simulator,id=F857F105-E1D5-449B-BBB0-0FA009FBD574' \
  -only-testing:HashiyaDiagnosticsTests -collect-test-diagnostics never 2>&1 | tail -40
```

Swap `-only-testing:` for the target in each step. Snapshot baselines are recorded on CI (Task 11), not locally. If a build fails with an unrelated "Cannot find type" error, delete the Hashiya DerivedData folder and retry.

---

### Task 1: The `HashiyaDiagnostics` target

**Files:**
- Modify: `ios/HashiyaKit/Package.swift`
- Create: `ios/HashiyaKit/Sources/HashiyaDiagnostics/CrashReporting.swift`
- Create: `ios/HashiyaKit/Sources/HashiyaDiagnostics/AnalyticsTracking.swift`
- Create: `ios/HashiyaKit/Sources/HashiyaDiagnostics/Diagnostics.swift`
- Create: `ios/HashiyaKit/Sources/HashiyaDiagnostics/PrivacySettings.swift`
- Create: `ios/HashiyaKit/Sources/HashiyaTesting/FakeDiagnostics.swift`
- Test: `ios/HashiyaKit/Tests/HashiyaDiagnosticsTests/DiagnosticsTests.swift`
- Modify: `ios/project.yml` (scheme test target list)

**Interfaces:**
- Produces: `CrashReporting` (`setEnabled(_:)`, `setKey(_:_:)`, `record(_:site:)`), `NoCrashReporting`, `CrashKey`, `CrashSite`, `ReportedError(error:site:)` (`site`, `type`, `domain`, `code`), `librarySizeBucket(_:) -> String`, `languageKey(_:) -> String`; `AnalyticsTracking` (`log(_:)`, `setProperty(_:_:)`, `setEnabled(_:)`), `NoAnalytics`, `AnalyticsEvent` (+ `name`, `parameters`), `AnalyticsProperty`, `Screen`, `SearchKind`, `SearchRoute`, `ResultsBucket(count:)`, `LimitKind`, `SaveSource`, `ExportFormat`, `PdfOrigin`, `ResearchCategory` (cases only; classifier in Task 2); `Diagnostics` (`crash`, `analytics`, `isLive`, `.none`, `screenShown(_:)`); `PrivacySettings(defaults:)`; `FakeCrashReporting`, `FakeAnalytics`.

- [ ] **Step 1: Add the target**

In `Package.swift`: add the product `.library(name: "HashiyaDiagnostics", targets: ["HashiyaDiagnostics"])`, the target `.target(name: "HashiyaDiagnostics")`, add `"HashiyaDiagnostics"` to the dependencies of `HashiyaData`, every `Feature*` target and `HashiyaTesting`, and add `.testTarget(name: "HashiyaDiagnosticsTests", dependencies: ["HashiyaDiagnostics", "HashiyaTesting"])`. In `ios/project.yml`, add `- package: HashiyaKit/HashiyaDiagnosticsTests` to the scheme's test targets (next to `HashiyaModelTests`), and `- HashiyaDiagnostics` to the `Hashiya` app target's package products.

- [ ] **Step 2: Write the failing tests**

`ios/HashiyaKit/Tests/HashiyaDiagnosticsTests/DiagnosticsTests.swift`:

```swift
import Foundation
import HashiyaDiagnostics
import HashiyaTesting
import Testing

struct DiagnosticsTests {
    @Test(arguments: [(0, "0"), (1, "1-50"), (50, "1-50"), (51, "51-500"), (500, "51-500"), (501, "501-5000"), (5000, "501-5000"), (5001, "5000+")])
    func librarySizeBuckets(papers: Int, bucket: String) {
        #expect(librarySizeBucket(papers) == bucket)
    }

    @Test(arguments: [("en", "en"), ("ar", "ar"), ("fr", "system"), ("", "system")])
    func languageKeys(code: String, key: String) {
        #expect(languageKey(code) == key)
    }

    @Test(arguments: [(0, ResultsBucket.zero), (1, .upTo25), (25, .upTo25), (26, .upTo200), (200, .upTo200), (201, .over200)])
    func resultBuckets(count: Int, bucket: ResultsBucket) {
        #expect(ResultsBucket(count: Int64(count)) == bucket)
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
```

- [ ] **Step 3: Run them to verify they fail**

Run `-only-testing:HashiyaDiagnosticsTests`. Expected: build failure — `no such module 'HashiyaDiagnostics'` or missing types.

- [ ] **Step 4: Implement the crash types**

`ios/HashiyaKit/Sources/HashiyaDiagnostics/CrashReporting.swift`:

```swift
import Foundation

/// Reports crashes' context and non-fatal failures. Only the app target knows the service behind it. Keys and sites are
/// closed lists, so free text — titles, searches, notes, paths — can't be attached by accident.
public protocol CrashReporting: Sendable {
    /// Starts or stops collection. Stopping also drops reports not yet sent.
    func setEnabled(_ enabled: Bool)
    func setKey(_ key: CrashKey, _ value: String)
    /// Reports `error` as `ReportedError` would describe it: never its message or user info.
    func record(_ error: any Error, site: CrashSite)
}

/// Debug builds, extensions and tests: reports nothing.
public struct NoCrashReporting: CrashReporting {
    public init() {}
    public func setEnabled(_ enabled: Bool) {}
    public func setKey(_ key: CrashKey, _ value: String) {}
    public func record(_ error: any Error, site: CrashSite) {}
}

public enum CrashKey: String, CaseIterable, Sendable {
    case screen, language, librarySizeBucket, backupInProgress
}

public enum CrashSite: String, CaseIterable, Sendable {
    case migration, databaseOpen, restore, export, pdfStore, unexpectedUiError
}

/// What a non-fatal report may carry: where it happened and what kind of error it was. Messages, user info and
/// associated values can hold titles, searches or file paths, so they never get here.
public struct ReportedError: Equatable, Sendable, CustomStringConvertible {
    public let site: CrashSite
    /// The error's Swift type name, e.g. "HashiyaData.BackupError" or "NSError".
    public let type: String
    public let domain: String
    public let code: Int

    public init(site: CrashSite, type: String, domain: String, code: Int) {
        self.site = site
        self.type = type
        self.domain = domain
        self.code = code
    }

    public init(error: any Error, site: CrashSite) {
        let bridged = error as NSError
        self.init(site: site, type: String(reflecting: Swift.type(of: error)), domain: bridged.domain, code: bridged.code)
    }

    public var description: String { "\(site.rawValue): \(type) \(domain) \(code)" }
}

/// The library's size, coarse enough to say nothing about a person.
public func librarySizeBucket(_ papers: Int) -> String {
    switch papers {
    case ...0: "0"
    case ...50: "1-50"
    case ...500: "51-500"
    case ...5000: "501-5000"
    default: "5000+"
    }
}

/// en, ar or system: the closed values the language key and property allow.
public func languageKey(_ code: String) -> String {
    switch code {
    case "en": "en"
    case "ar": "ar"
    default: "system"
    }
}
```

(Note: `String(reflecting:)` of a local test enum includes the test's module and scope; the test only checks the suffix.)

- [ ] **Step 5: Implement the analytics types**

`ios/HashiyaKit/Sources/HashiyaDiagnostics/AnalyticsTracking.swift`:

```swift
/// Counts how features are used. Only the app target knows the service behind it. Events, parameters and their values
/// are closed lists, so nothing a person typed or read can be attached.
public protocol AnalyticsTracking: Sendable {
    func log(_ event: AnalyticsEvent)
    func setProperty(_ property: AnalyticsProperty, _ value: String)
    /// Starts or stops collection. Stopping also clears the analytics id and events not yet sent.
    func setEnabled(_ enabled: Bool)
}

/// Debug builds, extensions and tests: counts nothing.
public struct NoAnalytics: AnalyticsTracking {
    public init() {}
    public func log(_ event: AnalyticsEvent) {}
    public func setProperty(_ property: AnalyticsProperty, _ value: String) {}
    public func setEnabled(_ enabled: Bool) {}
}

public enum AnalyticsProperty: String, CaseIterable, Sendable {
    case librarySizeBucket = "library_size_bucket"
    case language
    case hasOwnKey = "has_own_key"
}

public enum Screen: String, CaseIterable, Sendable {
    case library, search, details, reader, settings, restore, export
}

public enum SearchKind: String, Sendable { case keyword, doi, arxiv, link }
public enum SearchRoute: String, Sendable { case user, shared, keyless, cached }
public enum LimitKind: String, Sendable { case daily, pageCap = "page_cap" }
public enum SaveSource: String, Sendable { case search, lookup, share }
public enum ExportFormat: String, Sendable { case bibtex, backup }
public enum PdfOrigin: String, Sendable { case downloaded, attached }

public enum ResultsBucket: String, Sendable {
    case zero = "0", upTo25 = "1-25", upTo200 = "26-200", over200 = "200+"

    public init(count: Int64) {
        switch count {
        case ...0: self = .zero
        case ...25: self = .upTo25
        case ...200: self = .upTo200
        default: self = .over200
        }
    }
}

/// The research area of a keyword search, worked out on the device from the results' OpenAlex topics (see
/// `ResearchCategory.classify`). Never from the query.
public enum ResearchCategory: String, CaseIterable, Sendable {
    case ai, computerVision = "computer_vision", theory, networks, systems, software, hci
    case informationSystems = "information_systems", graphics, signalProcessing = "signal_processing"
    case csOther = "cs_other", mathematics, engineering, physicalSciences = "physical_sciences"
    case lifeSciences = "life_sciences", socialSciences = "social_sciences", healthSciences = "health_sciences"
    case unknown
}

public enum AnalyticsEvent: Equatable, Sendable {
    case search(kind: SearchKind, hasFilters: Bool, route: SearchRoute, results: ResultsBucket, category: ResearchCategory?)
    /// `page` 2…40; larger values are sent as 40.
    case searchMore(page: Int)
    case searchLimitReached(LimitKind)
    case paperSaved(from: SaveSource)
    case paperRemoved
    case noteEdited
    case collectionCreated
    case paperAddedToCollection
    case export(format: ExportFormat, withPdfs: Bool)
    case restore(succeeded: Bool)
    case pdfOpened(source: PdfOrigin)
    case pdfDownloaded(succeeded: Bool)
    case screenView(Screen)

    public var name: String {
        switch self {
        case .search: "search"
        case .searchMore: "search_more"
        case .searchLimitReached: "search_limit_reached"
        case .paperSaved: "paper_saved"
        case .paperRemoved: "paper_removed"
        case .noteEdited: "note_edited"
        case .collectionCreated: "collection_created"
        case .paperAddedToCollection: "paper_added_to_collection"
        case .export: "export"
        case .restore: "restore"
        case .pdfOpened: "pdf_opened"
        case .pdfDownloaded: "pdf_downloaded"
        case .screenView: "screen_view"
        }
    }

    public var parameters: [String: String] {
        switch self {
        case let .search(kind, hasFilters, route, results, category):
            var parameters = ["kind": kind.rawValue, "has_filters": yesNo(hasFilters), "route": route.rawValue, "results_bucket": results.rawValue]
            if let category { parameters["category"] = category.rawValue }
            return parameters
        case let .searchMore(page): return ["page": String(min(max(page, 2), 40))]
        case let .searchLimitReached(kind): return ["kind": kind.rawValue]
        case let .paperSaved(from): return ["from": from.rawValue]
        case .paperRemoved, .noteEdited, .collectionCreated, .paperAddedToCollection: return [:]
        case let .export(format, withPdfs): return ["format": format.rawValue, "with_pdfs": yesNo(withPdfs)]
        case let .restore(succeeded): return ["result": succeeded ? "ok" : "failed"]
        case let .pdfOpened(source): return ["source": source.rawValue]
        case let .pdfDownloaded(succeeded): return ["result": succeeded ? "ok" : "failed"]
        case let .screenView(screen): return ["screen": screen.rawValue]
        }
    }
}

private func yesNo(_ value: Bool) -> String { value ? "yes" : "no" }
```

- [ ] **Step 6: Implement `Diagnostics` and `PrivacySettings`**

`ios/HashiyaKit/Sources/HashiyaDiagnostics/Diagnostics.swift`:

```swift
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
```

`ios/HashiyaKit/Sources/HashiyaDiagnostics/PrivacySettings.swift`:

```swift
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
```

- [ ] **Step 7: Add the fakes**

`ios/HashiyaKit/Sources/HashiyaTesting/FakeDiagnostics.swift`:

```swift
import HashiyaDiagnostics
import os

/// Records every call, for tests.
public final class FakeCrashReporting: CrashReporting {
    private struct State {
        var enabled: [Bool] = []
        var keys: [CrashKey: String] = [:]
        var records: [ReportedError] = []
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    public init() {}

    public var enabledCalls: [Bool] { state.withLock { $0.enabled } }
    public var keys: [CrashKey: String] { state.withLock { $0.keys } }
    public var records: [ReportedError] { state.withLock { $0.records } }

    public func setEnabled(_ enabled: Bool) { state.withLock { $0.enabled.append(enabled) } }
    public func setKey(_ key: CrashKey, _ value: String) { state.withLock { $0.keys[key] = value } }
    public func record(_ error: any Error, site: CrashSite) {
        let reported = ReportedError(error: error, site: site)
        state.withLock { $0.records.append(reported) }
    }
}

/// Records every call, for tests.
public final class FakeAnalytics: AnalyticsTracking {
    private struct State {
        var events: [AnalyticsEvent] = []
        var properties: [AnalyticsProperty: String] = [:]
        var enabled: [Bool] = []
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    public init() {}

    public var events: [AnalyticsEvent] { state.withLock { $0.events } }
    public var properties: [AnalyticsProperty: String] { state.withLock { $0.properties } }
    public var enabledCalls: [Bool] { state.withLock { $0.enabled } }

    public func log(_ event: AnalyticsEvent) { state.withLock { $0.events.append(event) } }
    public func setProperty(_ property: AnalyticsProperty, _ value: String) { state.withLock { $0.properties[property] = value } }
    public func setEnabled(_ enabled: Bool) { state.withLock { $0.enabled.append(enabled) } }
}

extension Diagnostics {
    /// Fakes for both, not live.
    public static func fake(crash: FakeCrashReporting = FakeCrashReporting(), analytics: FakeAnalytics = FakeAnalytics()) -> Diagnostics {
        Diagnostics(crash: crash, analytics: analytics, isLive: false)
    }
}
```

- [ ] **Step 8: Run the tests to verify they pass**

Run `-only-testing:HashiyaDiagnosticsTests`. Expected: all pass. Then build the whole scheme once (`xcodebuild build-for-testing …`) to confirm the new dependency edges compile.

- [ ] **Step 9: Commit**

```bash
git add ios/HashiyaKit ios/project.yml
git commit -m "feat(ios): add the closed-list diagnostics layer for crash reports and usage statistics"
```

---

### Task 2: The research category from OpenAlex topics, and the route a search used

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaDiagnostics/ResearchCategoryClassifier.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaNetwork/NetworkModels.swift` (`NetworkWork`, `NetworkWorksResponse`, new `NetworkTopic`)
- Modify: `ios/HashiyaKit/Sources/HashiyaNetwork/OpenAlexSearchClient.swift` (`selectFields`, `searchWorks`)
- Modify: `ios/HashiyaKit/Sources/HashiyaNetwork/OpenAlexHTTP.swift` (report the route)
- Modify: `ios/HashiyaKit/Sources/HashiyaData/SearchRepository.swift` (`SearchPage.route`, `SearchPage.category`)
- Modify: `ios/HashiyaKit/Package.swift` (`HashiyaNetwork` doesn't depend on Diagnostics; `HashiyaData` already does)
- Test: `ios/HashiyaKit/Tests/HashiyaDiagnosticsTests/ResearchCategoryTests.swift`, `ios/HashiyaKit/Tests/HashiyaNetworkTests/NetworkModelsTests.swift`, `ios/HashiyaKit/Tests/HashiyaNetworkTests/OpenAlexRoutingTests.swift`, `ios/HashiyaKit/Tests/HashiyaDataTests/OpenAlexSearchRepositoryTests.swift`

**Interfaces:**
- Consumes: `ResearchCategory`, `SearchRoute` (Task 1).
- Produces: `ResearchCategory.classify(_ topics: [TopicIDs]) -> ResearchCategory`, `TopicIDs(subfield: String?, field: String?, domain: String?)` (OpenAlex id strings); `NetworkWork.primaryTopic: NetworkTopic?` (`subfieldID`, `fieldID`, `domainID`); `NetworkWorksResponse.route: RequestRoute?` with `public enum RequestRoute { case user, shared, keyless, cached }` in HashiyaNetwork; `SearchPage.route: SearchRoute?`, `SearchPage.category: ResearchCategory` (defaults `nil` / `.unknown`).

- [ ] **Step 1: Write the failing classifier tests**

`ios/HashiyaKit/Tests/HashiyaDiagnosticsTests/ResearchCategoryTests.swift`:

```swift
import HashiyaDiagnostics
import Testing

struct ResearchCategoryTests {
    private func topic(subfield: Int? = nil, field: Int? = nil, domain: Int? = nil) -> TopicIDs {
        TopicIDs(
            subfield: subfield.map { "https://openalex.org/subfields/\($0)" },
            field: field.map { "https://openalex.org/fields/\($0)" },
            domain: domain.map { "https://openalex.org/domains/\($0)" }
        )
    }

    @Test(arguments: [
        (1702, ResearchCategory.ai), (1707, .computerVision), (1703, .theory), (1705, .networks), (1708, .systems),
        (1712, .software), (1709, .hci), (1710, .informationSystems), (1704, .graphics), (1711, .signalProcessing),
    ])
    func everyComputerScienceSubfieldMaps(subfield: Int, category: ResearchCategory) {
        #expect(ResearchCategory.of(topic(subfield: subfield, field: 17, domain: 3)) == category)
    }

    @Test func anotherComputerScienceSubfieldIsCSOther() {
        #expect(ResearchCategory.of(topic(subfield: 1706, field: 17, domain: 3)) == .csOther)
    }

    @Test(arguments: [
        (26, 3, ResearchCategory.mathematics), (22, 3, .engineering), (31, 3, .physicalSciences),
        (13, 1, .lifeSciences), (33, 2, .socialSciences), (27, 4, .healthSciences),
    ])
    func fieldsAndDomainsMap(topicField: Int, domain: Int, category: ResearchCategory) {
        #expect(ResearchCategory.of(topic(subfield: topicField * 100 + 1, field: topicField, domain: domain)) == category)
    }

    @Test(arguments: [
        TopicIDs(subfield: "https://openalex.org/subfields/abc", field: nil, domain: nil),
        TopicIDs(subfield: "1702", field: nil, domain: nil),
        TopicIDs(subfield: nil, field: nil, domain: "https://openalex.org/domains/9"),
        TopicIDs(subfield: nil, field: nil, domain: nil),
    ])
    func malformedOrUnknownIdsAreUnknown(ids: TopicIDs) {
        #expect(ResearchCategory.of(ids) == .unknown)
    }

    @Test func aMalformedSubfieldFallsThroughToItsField() {
        #expect(ResearchCategory.of(TopicIDs(subfield: "x", field: "https://openalex.org/fields/26", domain: nil)) == .mathematics)
    }

    @Test func theMostCommonCategoryWinsWithAtLeast40Percent() {
        let ai = topic(subfield: 1702, field: 17, domain: 3)
        let vision = topic(subfield: 1707, field: 17, domain: 3)
        let theory = topic(subfield: 1703, field: 17, domain: 3)
        #expect(ResearchCategory.classify([ai, ai, vision, theory, ai]) == .ai)            // 60%
        #expect(ResearchCategory.classify([ai, ai, vision, vision, theory]) == .unknown)    // tie
        #expect(ResearchCategory.classify([ai, ai, vision, theory, topic(subfield: 1705, field: 17, domain: 3)]) == .ai)  // exactly 40%
        #expect(ResearchCategory.classify([ai, vision, theory]) == .unknown)                // 33%
        #expect(ResearchCategory.classify([ai, ai]) == .unknown)                            // fewer than 3
        #expect(ResearchCategory.classify([]) == .unknown)
    }

    @Test func resultsWithoutATopicAreSkippedAndOnlyTheFirstTenCount() {
        let ai = topic(subfield: 1702, field: 17, domain: 3)
        let vision = topic(subfield: 1707, field: 17, domain: 3)
        let none = TopicIDs(subfield: nil, field: nil, domain: nil)
        #expect(ResearchCategory.classify([none, none, ai, ai, ai]) == .ai)
        // The first ten mapped are vision; the ai ones after them don't count.
        #expect(ResearchCategory.classify(Array(repeating: vision, count: 10) + Array(repeating: ai, count: 15)) == .computerVision)
    }
}
```

(`none` entries are "results without a `primary_topic`": callers pass `nil` topics as all-nil `TopicIDs`; they are skipped and don't use up the ten.)

- [ ] **Step 2: Run them to verify they fail**

Run `-only-testing:HashiyaDiagnosticsTests`. Expected: build failure — `cannot find 'TopicIDs' in scope`.

- [ ] **Step 3: Implement the classifier**

`ios/HashiyaKit/Sources/HashiyaDiagnostics/ResearchCategoryClassifier.swift`:

```swift
/// A work's primary topic as OpenAlex ids ("https://openalex.org/subfields/1702", …/fields/17, …/domains/3). Only these
/// ids reach the classifier — never titles, topic names or the query.
public struct TopicIDs: Equatable, Sendable {
    public let subfield: String?
    public let field: String?
    public let domain: String?

    public init(subfield: String?, field: String?, domain: String?) {
        self.subfield = subfield
        self.field = field
        self.domain = domain
    }

    var isEmpty: Bool { subfield == nil && field == nil && domain == nil }
}

extension ResearchCategory {
    private static let subfields: [Int: ResearchCategory] = [
        1702: .ai, 1707: .computerVision, 1703: .theory, 1705: .networks, 1708: .systems,
        1712: .software, 1709: .hci, 1710: .informationSystems, 1704: .graphics, 1711: .signalProcessing,
    ]
    private static let fields: [Int: ResearchCategory] = [17: .csOther, 26: .mathematics, 22: .engineering]
    private static let domains: [Int: ResearchCategory] = [3: .physicalSciences, 1: .lifeSciences, 2: .socialSciences, 4: .healthSciences]

    /// One work's category: its subfield, else its field, else its domain; anything unrecognised is `unknown`.
    public static func of(_ ids: TopicIDs) -> ResearchCategory {
        if let number = number(ids.subfield, kind: "subfields") {
            if let category = subfields[number] { return category }
        }
        if let number = number(ids.field, kind: "fields"), let category = fields[number] { return category }
        if let number = number(ids.domain, kind: "domains"), let category = domains[number] { return category }
        return .unknown
    }

    /// A search's category: of the first 10 results that have a topic, the most common category if at least 3 were
    /// mapped and it has at least 40% of them with no tie; otherwise `unknown`.
    public static func classify(_ topics: [TopicIDs]) -> ResearchCategory {
        let mapped = topics.lazy.filter { !$0.isEmpty }.prefix(10).map(of)
        guard mapped.count >= 3 else { return .unknown }
        var votes: [ResearchCategory: Int] = [:]
        for category in mapped { votes[category, default: 0] += 1 }
        let ranked = votes.sorted { $0.value > $1.value }
        guard let top = ranked.first, ranked.dropFirst().first?.value != top.value,
              top.value * 5 >= mapped.count * 2 else { return .unknown }
        return top.key
    }

    /// The number at the end of "https://openalex.org/<kind>/<number>"; anything else is nil.
    private static func number(_ id: String?, kind: String) -> Int? {
        guard let id, id.hasPrefix("https://openalex.org/\(kind)/") else { return nil }
        return Int(id.dropFirst("https://openalex.org/\(kind)/".count))
    }
}
```

Note the subfield branch: a recognised subfield number not in the table (e.g. 1706, or 2601) falls through to the field, so 1706 → field 17 → `csOther`, and 2601 → field 26 → `mathematics`.

- [ ] **Step 4: Run the classifier tests to verify they pass**

Run `-only-testing:HashiyaDiagnosticsTests`. Expected: all pass. (`unknown` itself can win a vote — e.g. mostly non-CS-field works in an unmapped domain; that's correct.)

- [ ] **Step 5: Write the failing network and repository tests**

In `NetworkModelsTests.swift` add:

```swift
    @Test func decodesThePrimaryTopicIds() throws {
        let json = #"{"id":"https://openalex.org/W1","primary_topic":{"id":"https://openalex.org/T10036","display_name":"Advanced Neural Network Applications","subfield":{"id":"https://openalex.org/subfields/1707","display_name":"Computer Vision and Pattern Recognition"},"field":{"id":"https://openalex.org/fields/17","display_name":"Computer Science"},"domain":{"id":"https://openalex.org/domains/3","display_name":"Physical Sciences"}}}"#
        let work = try JSONDecoder().decode(NetworkWork.self, from: Data(json.utf8))
        #expect(work.primaryTopic == NetworkTopic(subfieldID: "https://openalex.org/subfields/1707", fieldID: "https://openalex.org/fields/17", domainID: "https://openalex.org/domains/3"))
    }

    @Test func aWorkWithoutAPrimaryTopicStillDecodes() throws {
        let work = try JSONDecoder().decode(NetworkWork.self, from: Data(#"{"id":"https://openalex.org/W1","primary_topic":null}"#.utf8))
        #expect(work.primaryTopic == nil)
    }
```

In `OpenAlexLookupClientTests.swift` (next to `selectedFieldsIncludeTypeAndBiblio`) add:

```swift
    @Test func selectedFieldsIncludeThePrimaryTopic() {
        #expect(OpenAlexSearchClient.selectFields.split(separator: ",").contains("primary_topic"))
    }
```

In `OpenAlexRoutingTests.swift` add (using that file's helpers `search(_:_:userKey:cache:)`, `quota()`, `request`):

```swift
    @Test func aSearchReportsTheRouteItUsed() async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        #expect(try await search(server, quota()).searchWorks(request).route == .shared)
        #expect(try await search(server, quota(), userKey: "mine").searchWorks(request).route == .user)
    }

    @Test func aCachedSearchReportsCached() async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        let client = search(server, quota(), cache: true)
        _ = try await client.searchWorks(request)
        #expect(try await client.searchWorks(request).route == .cached)
    }

    @Test func aSearchAfterTheSharedBudgetIsUsedUpReportsKeyless() async throws {
        let server = URLProtocolStub.Server { request in
            Self.query(request)["api_key"] == nil ? .json(page) : Self.usedUp()
        }
        #expect(try await search(server, quota()).searchWorks(request).route == .keyless)
    }
```

In `OpenAlexSearchRepositoryTests.swift` add (follow that file's fake-service pattern):

```swift
    @Test func aFirstPageCarriesTheRouteAndTheResearchCategory() async throws {
        let ai = NetworkTopic(subfieldID: "https://openalex.org/subfields/1702", fieldID: "https://openalex.org/fields/17", domainID: "https://openalex.org/domains/3")
        var response = NetworkWorksResponse(meta: NetworkMeta(count: 3, nextCursor: nil), results: (1...3).map { NetworkWork(id: "https://openalex.org/W\($0)", primaryTopic: ai) })
        response.route = .keyless
        let page = try await OpenAlexSearchRepository(service: FakeOpenAlexSearchService(response: response)).searchPage(SearchQuery(text: "bert"), cursor: nil)
        #expect(page.route == .keyless)
        #expect(page.category == .ai)
    }
```

(Use the existing `FakeOpenAlexSearchService` initialiser from `HashiyaTesting/FakeOpenAlexSearchService.swift`; adapt the call to its real signature. If `NetworkWorksResponse`/`NetworkMeta` have no public memberwise initialisers, add ones with these labels.)

- [ ] **Step 6: Run them to verify they fail**

Run `-only-testing:HashiyaNetworkTests` and `-only-testing:HashiyaDataTests`. Expected: build failures for `primaryTopic`, `NetworkTopic`, `route`, `category`.

- [ ] **Step 7: Decode the topic and select it**

In `NetworkModels.swift`:

```swift
/// A work's primary topic, as the ids of its subfield, field and domain. Topic and area names are not kept.
public struct NetworkTopic: Decodable, Equatable, Sendable {
    public let subfieldID: String?
    public let fieldID: String?
    public let domainID: String?

    public init(subfieldID: String?, fieldID: String?, domainID: String?) {
        self.subfieldID = subfieldID
        self.fieldID = fieldID
        self.domainID = domainID
    }

    private struct Ref: Decodable { let id: String? }
    private enum CodingKeys: String, CodingKey { case subfield, field, domain }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        subfieldID = (try? container.decodeIfPresent(Ref.self, forKey: .subfield))?.id
        fieldID = (try? container.decodeIfPresent(Ref.self, forKey: .field))?.id
        domainID = (try? container.decodeIfPresent(Ref.self, forKey: .domain))?.id
    }
}
```

In `NetworkWork`: add `public let primaryTopic: NetworkTopic?`; `case primaryTopic = "primary_topic"` in `CodingKeys`; `primaryTopic = try? container.decodeIfPresent(NetworkTopic.self, forKey: .primaryTopic)` in `init(from:)` (a malformed topic must never fail the whole work); and `primaryTopic: NetworkTopic? = nil` as the last parameter of the memberwise `init`.

In `NetworkWorksResponse`: add `public var route: RequestRoute? = nil` (not decoded — keep it out of `CodingKeys`), and above it:

```swift
/// How a search's response was obtained: the user's key, the shared route, no key, or the on-device cache.
public enum RequestRoute: Sendable, Equatable {
    case user, shared, keyless, cached
}
```

In `OpenAlexSearchClient.selectFields`, append `,primary_topic`.

- [ ] **Step 8: Report the route from `OpenAlexHTTP`**

Change `send` to return `(Data, RequestRoute)`: the user-key branch returns `.user`; the `quota == nil` branch returns `.shared`; in the loop, a 2xx returns `current == .shared ? .shared : .keyless`. `get(path:query:)` keeps returning `Data` (drop the route). Add:

```swift
    /// Like `get(_:path:query:)`, and says how the response was obtained (`.cached` for a cache hit).
    func getRouted<T: Decodable>(_ type: T.Type, path: String, query: [(name: String, value: String)]) async throws -> (T, RequestRoute) {
        let metered = path == "/works"
        let cacheKey = metered ? SearchCache.key(path: path, query: query) : nil
        if let cacheKey, let cached = cache?.data(for: cacheKey), let value = try? Self.decode(type, from: cached) {
            return (value, .cached)
        }
        let (data, route) = try await send(path: path, query: query, metered: metered)
        let value = try Self.decode(type, from: data)
        if let cacheKey { cache?.store(data, for: cacheKey) }
        return (value, route)
    }
```

and make `get(_:path:query:)` call `getRouted` and drop the route. In `OpenAlexSearchClient.searchWorks`, use `getRouted` and set `response.route = route` before returning.

- [ ] **Step 9: Carry route and category on `SearchPage`**

In `SearchRepository.swift`, add to `SearchPage` `public var route: SearchRoute?` and `public var category: ResearchCategory` (init parameters `route: SearchRoute? = nil, category: ResearchCategory = .unknown`), `import HashiyaDiagnostics`, and in `OpenAlexSearchRepository.searchPage` build them:

```swift
        return SearchPage(
            papers: response.results.map { $0.asPaper() },
            totalCount: response.meta.count,
            nextCursor: response.results.isEmpty ? nil : response.meta.nextCursor,
            route: response.route.map(SearchRoute.init),
            category: cursor == nil
                ? ResearchCategory.classify(response.results.map { TopicIDs(subfield: $0.primaryTopic?.subfieldID, field: $0.primaryTopic?.fieldID, domain: $0.primaryTopic?.domainID) })
                : .unknown
        )
```

with, in the same file:

```swift
extension SearchRoute {
    init(_ route: RequestRoute) {
        switch route {
        case .user: self = .user
        case .shared: self = .shared
        case .keyless: self = .keyless
        case .cached: self = .cached
        }
    }
}
```

- [ ] **Step 10: Run the tests to verify they pass**

Run `-only-testing:HashiyaDiagnosticsTests`, `-only-testing:HashiyaNetworkTests`, `-only-testing:HashiyaDataTests`. Expected: all pass, including the existing select-field assertions (update any test that compares the whole `selectFields` string).

- [ ] **Step 11: Commit**

```bash
git add ios/HashiyaKit
git commit -m "feat(ios): work out a search's research area from its results' topics, and report its route"
```

---

### Task 3: Firebase in the app, collection only in Release builds

**Files:**
- Modify: `ios/project.yml` (package, products, Crashlytics dSYM script, script sandboxing)
- Create: `ios/Hashiya/GoogleService-Info.plist` (from the session scratchpad `firebase/GoogleService-Info.plist`, or re-download: `firebase apps:sdkconfig IOS 1:10078456816:ios:b46501427f2408bcb12b35 --project hashiya-research > ios/Hashiya/GoogleService-Info.plist`)
- Modify: `ios/Hashiya/Info.plist`
- Create: `ios/Hashiya/Diagnostics/FirebaseCrashReporting.swift`
- Create: `ios/Hashiya/Diagnostics/FirebaseAnalyticsTracking.swift`
- Create: `ios/Hashiya/Diagnostics/DiagnosticsStartup.swift`
- Modify: `ios/Hashiya/HashiyaApp.swift`, `ios/Hashiya/AppContainer.swift`, `ios/Hashiya/RootView.swift`, `ios/Hashiya/PaperWindow.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaData/LiveDependencies.swift` (accept `crash: any CrashReporting = NoCrashReporting()`; pass it to the PDF repository — used in Task 5)
- Test: `ios/HashiyaKit/Tests/HashiyaDiagnosticsTests/DiagnosticsStartupTests.swift` (the startup logic lives in the package as `DiagnosticsLaunch`, so it is testable)
- Create: `ios/HashiyaKit/Sources/HashiyaDiagnostics/DiagnosticsLaunch.swift`

**Interfaces:**
- Consumes: Task 1 types.
- Produces: `DiagnosticsLaunch.apply(diagnostics:privacy:languageCode:librarySize:hasOwnKey:)` (sets enabled/keys/properties); `Diagnostics` held by `AppContainer.diagnostics` and injected into the SwiftUI environment by `RootView` and `PaperWindow`; `AppContainer.make(arguments:diagnostics:)`.

- [ ] **Step 1: Write the failing launch tests**

`ios/HashiyaKit/Tests/HashiyaDiagnosticsTests/DiagnosticsStartupTests.swift`:

```swift
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
```

- [ ] **Step 2: Run them to verify they fail**

Run `-only-testing:HashiyaDiagnosticsTests`. Expected: build failure — `cannot find 'DiagnosticsLaunch'`.

- [ ] **Step 3: Implement `DiagnosticsLaunch`**

`ios/HashiyaKit/Sources/HashiyaDiagnostics/DiagnosticsLaunch.swift`:

```swift
/// What the app does with diagnostics at launch, before anything else can fail: collection follows the switches (only
/// in live builds), then the crash keys and the analytics user properties are set.
public enum DiagnosticsLaunch {
    public static func apply(diagnostics: Diagnostics, privacy: PrivacySettings, languageCode: String, librarySize: Int?, hasOwnKey: Bool) {
        diagnostics.crash.setEnabled(diagnostics.isLive && privacy.crashReportsEnabled)
        diagnostics.analytics.setEnabled(diagnostics.isLive && privacy.analyticsEnabled)
        let language = languageKey(languageCode)
        diagnostics.crash.setKey(.language, language)
        diagnostics.crash.setKey(.backupInProgress, "none")
        diagnostics.analytics.setProperty(.language, language)
        diagnostics.analytics.setProperty(.hasOwnKey, hasOwnKey ? "yes" : "no")
        if let librarySize {
            let bucket = librarySizeBucket(librarySize)
            diagnostics.crash.setKey(.librarySizeBucket, bucket)
            diagnostics.analytics.setProperty(.librarySizeBucket, bucket)
        }
    }
}
```

Run the tests: expected pass.

- [ ] **Step 4: Add Firebase to the project**

In `ios/project.yml`:

```yaml
packages:
  HashiyaKit:
    path: HashiyaKit
  Firebase:
    url: https://github.com/firebase/firebase-ios-sdk
    exactVersion: 12.19.2
```

In the `Hashiya` target's `dependencies`, add:

```yaml
      - package: Firebase
        product: FirebaseCrashlytics
      - package: Firebase
        product: FirebaseAnalyticsCore
```

In the `Hashiya` target's `settings.base`, add `ENABLE_USER_SCRIPT_SANDBOXING: NO` (Crashlytics' upload script reads the package checkout) and add, under the `Hashiya` target:

```yaml
    postBuildScripts:
      - name: Upload Crashlytics symbols (Release)
        basedOnDependencyAnalysis: false
        script: |
          if [ "${CONFIGURATION}" = "Release" ]; then
            "${BUILD_DIR%/Build/*}/SourcePackages/checkouts/firebase-ios-sdk/Crashlytics/run"
          fi
        inputFiles:
          - ${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}
          - ${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}/Contents/Resources/DWARF/${PRODUCT_NAME}
          - ${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}/Contents/Info.plist
          - $(TARGET_BUILD_DIR)/$(UNLOCALIZED_RESOURCES_FOLDER_PATH)/GoogleService-Info.plist
          - $(TARGET_BUILD_DIR)/$(EXECUTABLE_PATH)
```

Copy the `GoogleService-Info.plist` into `ios/Hashiya/` (it is committed, per the crash spec; the key gets restricted to the bundle id in Google Cloud). The `Hashiya` sources glob picks it up as a resource; check `xcodegen generate` lists it in Copy Bundle Resources.

- [ ] **Step 5: Turn collection off until the app decides**

Add to `ios/Hashiya/Info.plist` (alphabetical position in the dict):

```xml
	<key>FIREBASE_ANALYTICS_COLLECTION_ENABLED</key>
	<false/>
	<key>FirebaseAppDelegateProxyEnabled</key>
	<false/>
	<key>FirebaseAutomaticScreenReportingEnabled</key>
	<false/>
	<key>FirebaseCrashlyticsCollectionEnabled</key>
	<false/>
	<key>GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS</key>
	<false/>
	<key>GOOGLE_ANALYTICS_IDFV_COLLECTION_ENABLED</key>
	<false/>
```

- [ ] **Step 6: The Firebase implementations (app target)**

`ios/Hashiya/Diagnostics/FirebaseCrashReporting.swift`:

```swift
import FirebaseCrashlytics
import Foundation
import HashiyaDiagnostics

/// Release builds' crash reporter. Non-fatals go through `ReportedError`: site, type, domain and code only.
struct FirebaseCrashReporting: CrashReporting {
    func setEnabled(_ enabled: Bool) {
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(enabled)
        if !enabled { Crashlytics.crashlytics().deleteUnsentReports() }
    }

    func setKey(_ key: CrashKey, _ value: String) {
        Crashlytics.crashlytics().setCustomValue(value, forKey: key.rawValue)
    }

    func record(_ error: any Error, site: CrashSite) {
        // Crashlytics keeps a non-fatal recorded while collection is off and sends it once it is on again.
        guard Crashlytics.crashlytics().isCrashlyticsCollectionEnabled() else { return }
        let reported = ReportedError(error: error, site: site)
        Crashlytics.crashlytics().record(error: NSError(domain: "\(site.rawValue).\(reported.domain)", code: reported.code, userInfo: ["type": reported.type]))
    }
}
```

`ios/Hashiya/Diagnostics/FirebaseAnalyticsTracking.swift`:

```swift
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

    func setProperty(_ property: AnalyticsProperty, _ value: String) {
        Analytics.setUserProperty(value, forName: property.rawValue)
    }

    func setEnabled(_ isOn: Bool) {
        enabled.withLock { $0 = isOn }
        Analytics.setConsent([.analyticsStorage: .granted, .adStorage: .denied, .adUserData: .denied, .adPersonalization: .denied])
        Analytics.setAnalyticsCollectionEnabled(isOn)
        if !isOn { Analytics.resetAnalyticsData() }
    }
}
```

`ios/Hashiya/Diagnostics/DiagnosticsStartup.swift`:

```swift
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
            DispatchQueue.main.asyncAfter(deadline: .now() + 2) { fatalError("Hashiya test crash") }
        }
    }
}
```

- [ ] **Step 7: Wire it into the app**

- `HashiyaApp.init`: before building the container, `let diagnostics = Self.isSnapshotTestHost ? Diagnostics.none : DiagnosticsStartup.make()`, then `AppContainer.make(diagnostics: diagnostics)`.
- `AppContainer`: add `let diagnostics: Diagnostics`, an `init` parameter `diagnostics: Diagnostics = .none`, and `make(arguments:diagnostics:)`. In `make`'s `catch`:

```swift
        } catch {
            diagnostics.crash.record(error, site: error is HashiyaDatabase.MigrationError ? .migration : .databaseOpen)
            // The error's description can hold file paths: it went to the crash report sanitised, not here.
            fatalError("Could not open the library database")
        }
```

  For `.migration` to be distinguishable, in `HashiyaDatabase.openPool` wrap the migrate call: `do { try migrator.migrate(pool) } catch { throw MigrationError(underlying: error) }` with `public struct MigrationError: Error { public let underlying: any Error }` in `HashiyaDatabase.swift` (`ReportedError` then reports `HashiyaDatabase.MigrationError`). The `-ui-testing` branch keeps `.none` diagnostics. Pass `crash: diagnostics.crash` into `LiveDependencies.live(background:crash:)`.
- After the container exists (end of `AppContainer.init`), start:

```swift
        Task { [diagnostics, backup, preferences] in
            let papers = try? await backup.summary().papers
            await MainActor.run {
                DiagnosticsLaunch.apply(
                    diagnostics: diagnostics, privacy: PrivacySettings(), languageCode: HashiyaLanguage.code,
                    librarySize: papers, hasOwnKey: preferences.currentUserAPIKey != nil
                )
            }
            for await key in preferences.userAPIKeyUpdates() {
                diagnostics.analytics.setProperty(.hasOwnKey, key == nil ? "no" : "yes")
            }
        }
```

  (`DiagnosticsLaunch.apply` calls `setEnabled` again with the same values; that's harmless.)
- `RootView` and `PaperWindow`: add `.environment(\.diagnostics, container.diagnostics)` on their top-level view.

- [ ] **Step 8: Build and test**

`cd ios && xcodegen generate` (it resolves the Firebase package — this takes a while the first time), then run `-only-testing:HashiyaDiagnosticsTests` and `-only-testing:HashiyaDataTests`, and `xcodebuild build` for the **Release** configuration as well (`-configuration Release CODE_SIGNING_ALLOWED=NO`) to check the Firebase code compiles and the dSYM script runs (it may print that it can't upload without credentials — that's fine; it must not fail the build. If it does, report it).

- [ ] **Step 9: Commit**

```bash
git add ios/project.yml ios/Hashiya ios/HashiyaKit
git commit -m "feat(ios): add Crashlytics and Analytics, configured only in Release builds and off until the switches say"
```

---

### Task 4: The Privacy section in Settings

**Files:**
- Modify: `ios/HashiyaKit/Sources/FeatureSettings/SettingsView.swift`, `SettingsViewModel.swift`, `Resources/Localizable.xcstrings`
- Modify: `ios/Hashiya/AppContainer.swift` (`makeSettingsViewModel`)
- Test: `ios/HashiyaKit/Tests/FeatureSettingsTests/SettingsViewModelTests.swift`, `ios/HashiyaSnapshotTests/SettingsSnapshotTests.swift`, new `ios/HashiyaUITests/PrivacyFlowTests.swift`
- Delete: the Settings snapshot baselines under `ios/HashiyaSnapshotTests/__Snapshots__/iOS18/SettingsSnapshotTests/` and `.../iOS26/SettingsSnapshotTests/` (the screen grew)

**Interfaces:**
- Consumes: `Diagnostics`, `PrivacySettings` (Task 1).
- Produces: `SettingsViewModel.init(preferences:pdfs:backup:privacy:diagnostics:)` with `privacy: PrivacySettings = PrivacySettings()`, `diagnostics: Diagnostics = .none`; `crashReportsEnabled`, `analyticsEnabled` (observable), `setCrashReportsEnabled(_:)`, `setAnalyticsEnabled(_:)`.

- [ ] **Step 1: Write the failing view-model tests**

Add to `SettingsViewModelTests.swift`:

```swift
    @Test func bothPrivacySwitchesStartOn() {
        let viewModel = SettingsViewModel(preferences: FakeUserPreferencesRepository(), pdfs: FakePdfRepository(), backup: FakeLibraryBackup(), privacy: PrivacySettings(defaults: TestDefaults.make()))
        #expect(viewModel.crashReportsEnabled)
        #expect(viewModel.analyticsEnabled)
    }

    @Test func turningUsageStatisticsOffStoresItAndStopsCollection() {
        let defaults = TestDefaults.make()
        let analytics = FakeAnalytics()
        let viewModel = SettingsViewModel(
            preferences: FakeUserPreferencesRepository(), pdfs: FakePdfRepository(), backup: FakeLibraryBackup(),
            privacy: PrivacySettings(defaults: defaults), diagnostics: Diagnostics(crash: FakeCrashReporting(), analytics: analytics, isLive: true)
        )
        viewModel.setAnalyticsEnabled(false)
        #expect(!viewModel.analyticsEnabled)
        #expect(!PrivacySettings(defaults: defaults).analyticsEnabled)
        #expect(analytics.enabledCalls == [false])
    }

    @Test func turningCrashReportsBackOnInADebugBuildDoesNotEnableCollection() {
        let crash = FakeCrashReporting()
        let viewModel = SettingsViewModel(
            preferences: FakeUserPreferencesRepository(), pdfs: FakePdfRepository(), backup: FakeLibraryBackup(),
            privacy: PrivacySettings(defaults: TestDefaults.make()), diagnostics: Diagnostics(crash: crash, analytics: FakeAnalytics(), isLive: false)
        )
        viewModel.setCrashReportsEnabled(false)
        viewModel.setCrashReportsEnabled(true)
        #expect(crash.enabledCalls == [false, false])
    }

    @Test func thePrivacyStringsExistInBothLanguages() {
        for language in ["en", "ar"] {
            let previous = HashiyaLanguage.override
            HashiyaLanguage.override = language
            defer { HashiyaLanguage.override = previous }
            for key in ["settings.privacySection", "settings.crashReports", "settings.crashReportsFooter", "settings.analytics", "settings.analyticsFooter", "settings.privacyPolicy"] {
                #expect(!L10n.string(key).hasPrefix("settings."))
            }
        }
    }
```

(Add `import HashiyaDiagnostics` to the test file.) Respect the existing rule that `init` must not read observable state: seed the two Bool properties by plain assignment from `privacy` in `init`.

- [ ] **Step 2: Run them to verify they fail**

Run `-only-testing:FeatureSettingsTests`. Expected: build failure — extra arguments `privacy`, `diagnostics`.

- [ ] **Step 3: Implement the view model part**

In `SettingsViewModel`: `import HashiyaDiagnostics`; `public internal(set) var crashReportsEnabled: Bool` and `analyticsEnabled: Bool`; `@ObservationIgnored private let privacy: PrivacySettings`, `@ObservationIgnored private let diagnostics: Diagnostics`; init params with defaults as in Interfaces, assigning `crashReportsEnabled = privacy.crashReportsEnabled` etc.; and:

```swift
    public func setCrashReportsEnabled(_ enabled: Bool) {
        crashReportsEnabled = enabled
        privacy.setCrashReportsEnabled(enabled)
        diagnostics.crash.setEnabled(diagnostics.isLive && enabled)
    }

    public func setAnalyticsEnabled(_ enabled: Bool) {
        analyticsEnabled = enabled
        privacy.setAnalyticsEnabled(enabled)
        diagnostics.analytics.setEnabled(diagnostics.isLive && enabled)
    }
```

In `AppContainer.makeSettingsViewModel`, pass `diagnostics: diagnostics`.

- [ ] **Step 4: Add the strings**

In `FeatureSettings/Resources/Localizable.xcstrings` (alphabetical keys, `extractionState: manual`, en + ar `translated`):

| Key | en | ar |
|---|---|---|
| `settings.privacySection` | Privacy | الخصوصية |
| `settings.crashReports` | Send crash reports | إرسال تقارير الأعطال |
| `settings.crashReportsFooter` | Crash details and app errors help fix bugs. They never include your papers, notes or searches. | تساعد تفاصيل الأعطال وأخطاء التطبيق على إصلاح المشكلات، ولا تتضمّن أبدًا أوراقك أو ملاحظاتك أو عمليات بحثك. |
| `settings.analytics` | Share usage statistics | مشاركة إحصاءات الاستخدام |
| `settings.analyticsFooter` | Anonymous counts of how features are used help decide what to improve. Never your papers, notes or searches. | تساعد الأعداد المجهولة لاستخدام الميزات على تحديد ما يجب تحسينه، دون أوراقك أو ملاحظاتك أو عمليات بحثك أبدًا. |
| `settings.privacyPolicy` | Privacy policy | سياسة الخصوصية |

- [ ] **Step 5: Add the section**

In `SettingsView`, insert `privacySection` after `BackupSection(...)` and before `languageSection`:

```swift
    private var privacySection: some View {
        Section {
            privacyToggle(
                titleKey: "settings.crashReports", footerKey: "settings.crashReportsFooter", identifier: "settings.crashReports",
                isOn: Binding(get: { viewModel.crashReportsEnabled }, set: { viewModel.setCrashReportsEnabled($0) })
            )
            privacyToggle(
                titleKey: "settings.analytics", footerKey: "settings.analyticsFooter", identifier: "settings.analytics",
                isOn: Binding(get: { viewModel.analyticsEnabled }, set: { viewModel.setAnalyticsEnabled($0) })
            )
            Button {
                openURL(Self.privacyPolicyURL)
            } label: {
                HStack {
                    Text(verbatim: L10n.string("settings.privacyPolicy")).font(.hashiya(.body)).foregroundStyle(HashiyaColors.onSurface)
                    Spacer()
                    Image(systemName: "arrow.up.forward.app").foregroundStyle(HashiyaColors.primary)
                }
            }
            .accessibilityIdentifier("settings.privacyPolicy")
        } header: {
            Text(verbatim: L10n.string("settings.privacySection"))
                .font(.hashiya(.stateTitle))
                .foregroundStyle(HashiyaColors.onSurface)
                .textCase(nil)
        }
    }

    private func privacyToggle(titleKey: String, footerKey: String, identifier: String, isOn: Binding<Bool>) -> some View {
        Toggle(isOn: isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: L10n.string(titleKey)).font(.hashiya(.body)).foregroundStyle(HashiyaColors.onSurface)
                Text(verbatim: L10n.string(footerKey)).font(.hashiya(.meta)).foregroundStyle(HashiyaColors.onSurfaceVariant)
            }
        }
        .tint(HashiyaColors.primary)
        .accessibilityIdentifier(identifier)
    }

    /// The published policy; Arabic opens its Arabic half.
    private static var privacyPolicyURL: URL {
        URL(string: "https://fadyfouad.github.io/Hashiya-Privacy-Policy/" + (HashiyaLanguage.isArabic ? "#ar" : ""))!
    }
```

Also add `.onAppear { diagnostics.screenShown(.settings) }` is done in Task 5 — not here.

- [ ] **Step 6: Add the UI test**

`ios/HashiyaUITests/PrivacyFlowTests.swift`:

```swift
import XCTest

final class PrivacyFlowTests: XCTestCase {
    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["-ui-testing", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        return app
    }

    private func analyticsSwitch(in app: XCUIApplication) -> XCUIElement {
        app.buttons["Settings"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: UITestTimeout.long))
        let toggle = app.switches["settings.analytics"]
        for _ in 0..<6 where !toggle.isHittable { app.swipeUp() }
        return toggle
    }

    func testTurningUsageStatisticsOffSurvivesARelaunch() {
        var app = launch()
        var toggle = analyticsSwitch(in: app)
        if (toggle.value as? String) == "0" { toggle.switches.firstMatch.tap() }   // start from on
        toggle.switches.firstMatch.tap()
        XCTAssertEqual(toggle.value as? String, "0")
        app.terminate()

        app = launch()
        toggle = analyticsSwitch(in: app)
        XCTAssertEqual(toggle.value as? String, "0")
        toggle.switches.firstMatch.tap()   // leave it on for other tests
        XCTAssertEqual(toggle.value as? String, "1")
    }
}
```

(If `Toggle` with a custom label exposes its switch directly as `app.switches["settings.analytics"]`, tap the element itself instead of `.switches.firstMatch`; check in the simulator and keep whichever works on both iOS 18 and 26.)

- [ ] **Step 7: Run the tests**

Run `-only-testing:FeatureSettingsTests`, then `-only-testing:HashiyaUITests/PrivacyFlowTests`, then `python3 ios/scripts/check-translations.py`. Expected: all pass. Delete the Settings snapshot baselines (`git rm -q ios/HashiyaSnapshotTests/__Snapshots__/iOS18/SettingsSnapshotTests/* ios/HashiyaSnapshotTests/__Snapshots__/iOS26/SettingsSnapshotTests/*`) — they are recorded in Task 11.

- [ ] **Step 8: Commit**

```bash
git add -A ios/HashiyaKit ios/Hashiya ios/HashiyaUITests ios/HashiyaSnapshotTests
git commit -m "feat(ios): add the Privacy section with crash-report and usage-statistics switches"
```

---

### Task 5: Crash non-fatals, the backup key and screens

**Files:**
- Modify: `ios/HashiyaKit/Sources/FeatureSettings/RestoreViewModel.swift`, `SettingsViewModel.swift`, `SettingsView.swift`, `RestoreView.swift`, `ExportBackupView.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaData/Pdf/GRDBPdfRepository.swift`, `ios/HashiyaKit/Sources/HashiyaData/Pdf/PdfRepository.swift` (`PdfDependencies.crash`), `LiveDependencies.swift`
- Modify: screens' `onAppear`: `FeatureLibrary/LibraryView.swift`, `FeatureSearch/SearchView.swift`, `FeaturePaperDetails/PaperDetailsScreen.swift`, `FeatureReader/ReaderScreen.swift`
- Modify: `ios/Hashiya/AppContainer.swift` (`makeRestoreViewModel` passes `diagnostics`)
- Test: `FeatureSettingsTests/RestoreViewModelTests.swift`, `FeatureSettingsTests/SettingsBackupTests.swift`, `HashiyaDataTests/` PDF repository tests

**Interfaces:**
- Consumes: `Diagnostics`, `CrashSite`, `FakeCrashReporting` (Task 1); `LiveDependencies.live(background:crash:)` (Task 3).
- Produces: `RestoreViewModel.init(source:backup:onSourceRead:diagnostics:)` (`diagnostics: Diagnostics = .none`); `PdfDependencies(…, crash: any CrashReporting = NoCrashReporting())`.

- [ ] **Step 1: Write the failing tests**

`RestoreViewModelTests.swift` — add (use the file's existing `FakeLibraryBackup` setup to make `apply` throw):

```swift
    @Test(arguments: [BackupError.writeFailed, .unreadable])
    func aWriteOrReadFailureIsReported(error: BackupError) async {
        let crash = FakeCrashReporting()
        // Make FakeLibraryBackup's apply throw `error` (see the file's other failure tests for the exact setup).
        let viewModel = restoreViewModel(applyError: error, diagnostics: .fake(crash: crash))
        await confirmAndWait(viewModel)
        #expect(crash.records.map(\.site) == [.restore])
        #expect(crash.keys[.backupInProgress] == "none")
    }

    @Test(arguments: [BackupError.noSpace, .busy])
    func noSpaceAndBusyAreNotReported(error: BackupError) async {
        let crash = FakeCrashReporting()
        let viewModel = restoreViewModel(applyError: error, diagnostics: .fake(crash: crash))
        await confirmAndWait(viewModel)
        #expect(crash.records.isEmpty)
    }

    @Test func anUnexpectedErrorIsReportedAsAnUIError() async {
        struct Odd: Error {}
        let crash = FakeCrashReporting()
        let viewModel = restoreViewModel(applyError: Odd(), diagnostics: .fake(crash: crash))
        await confirmAndWait(viewModel)
        #expect(crash.records.map(\.site) == [.unexpectedUiError])
    }

    @Test func aRestoreSetsAndClearsTheBackupKey() async {
        let crash = FakeCrashReporting()
        let viewModel = restoreViewModel(applyError: nil, diagnostics: .fake(crash: crash))
        await confirmAndWait(viewModel)
        #expect(crash.keys[.backupInProgress] == "none")
    }
```

`restoreViewModel(applyError:diagnostics:)` and `confirmAndWait(_:)` are small private helpers you add to the test file around its existing setup. Mirror these four tests for the backup export in `SettingsBackupTests.swift`: `writeFailed` → `.export`; `noSpace` → nothing; an unexpected error → `.unexpectedUiError`; the key goes `export` while building and back to `none`.

For the PDF store, add to the PDF repository tests: a download whose file write throws `PdfWriteError` records one `.pdfStore`; a not-PDF or too-large file records nothing; an attach whose copy throws records `.pdfStore`.

- [ ] **Step 2: Run them to verify they fail**

Run `-only-testing:FeatureSettingsTests` and `-only-testing:HashiyaDataTests`. Expected: build failures for `diagnostics`/`crash` parameters.

- [ ] **Step 3: Restore and export**

`RestoreViewModel`: add the `diagnostics` init parameter; in `confirm()`, set `diagnostics.crash.setKey(.backupInProgress, "restore")` when applying starts, and in the outcome handling:

```swift
        } catch let error as BackupError {
            if error == .writeFailed || error == .unreadable { diagnostics.crash.record(error, site: .restore) }
            outcome = .failed(error)
        } catch {
            if !(error is CancellationError) { diagnostics.crash.record(error, site: .unexpectedUiError) }
            // Never leave the screen on the progress bar.
            outcome = .failed(.writeFailed)
        }
        diagnostics.crash.setKey(.backupInProgress, "none")
```

`SettingsViewModel.confirmExport()`: set the key to `"export"` when building starts and back to `"none"` when the task ends (success, failure or cancel); in the typed catch record `.export` when `error == .writeFailed`; in the generic catch record `.unexpectedUiError` unless it's a `CancellationError`. `AppContainer.makeRestoreViewModel` passes `diagnostics: diagnostics`.

- [ ] **Step 4: The PDF store**

Add `public let crash: any CrashReporting` to `PdfDependencies` (init parameter `crash: any CrashReporting = NoCrashReporting()` last). In `GRDBPdfRepository.attempt`, in the two branches that produce `.failed(.http, …, wroteNothing: true)` from a thrown error (the `setPdf` row write and the final catch-all), record `crash.record(error, site: .pdfStore)` unless the error is a `NetworkFailure` or `CancellationError`. In `attachCounted`, record `.pdfStore` in the row-write failure and the catch-all branches (not for `.notPDF`/`.tooLarge`). `LiveDependencies.live(background:crash:)` passes `crash` into `PdfDependencies`.

- [ ] **Step 5: Screens**

Each screen view reads `@Environment(\.diagnostics) private var diagnostics` and adds `.onAppear { diagnostics.screenShown(.<screen>) }`: `LibraryView` (`.library`), `SearchView` (`.search`), `PaperDetailsScreen` (`.details`), `ReaderScreen` (`.reader`), `SettingsView` (`.settings`), `RestoreView` (`.restore`), `ExportBackupView` (`.export`). Use `.onAppear`, not `.task` (a push cancels a screen's `.task`). Add `import HashiyaDiagnostics` where needed.

- [ ] **Step 6: Run the tests to verify they pass**

Run `-only-testing:FeatureSettingsTests`, `-only-testing:HashiyaDataTests`, then build-for-testing the scheme.

- [ ] **Step 7: Commit**

```bash
git add ios/HashiyaKit ios/Hashiya
git commit -m "feat(ios): report restore, export and PDF write failures, the backup in progress and the screen"
```

---

### Task 6: Search, lookup, save and remove events

**Files:**
- Modify: `ios/HashiyaKit/Sources/FeatureSearch/SearchViewModel.swift`
- Modify: `ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift`
- Modify: `ios/Hashiya/AppContainer.swift`, `ios/Hashiya/PaperWindow.swift`, `ios/Hashiya/RootView.swift` (only if a view model is built there)
- Test: `ios/HashiyaKit/Tests/FeatureSearchTests/SearchAnalyticsTests.swift` (new), `ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryViewModelTests.swift`

**Interfaces:**
- Consumes: `AnalyticsEvent` and friends, `FakeAnalytics`, `Diagnostics.fake` (Task 1); `SearchPage.route`, `SearchPage.category` (Task 2).
- Produces: `SearchViewModel.init(…, diagnostics: Diagnostics = .none)`; `LibraryViewModel.init(…, diagnostics: Diagnostics = .none)`.

- [ ] **Step 1: Write the failing tests**

`ios/HashiyaKit/Tests/FeatureSearchTests/SearchAnalyticsTests.swift`:

```swift
@testable import FeatureSearch
import HashiyaData
import HashiyaDiagnostics
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct SearchAnalyticsTests {
    private let analytics = FakeAnalytics()

    private func viewModel(_ repository: FakeSearchRepository, lookup: FakePaperLookupRepository = FakePaperLookupRepository(), key: String? = nil) -> SearchViewModel {
        SearchViewModel(
            repository: repository, lookup: lookup, library: FakeLibraryRepository(),
            preferences: FakeUserPreferencesRepository(key: key), diagnostics: .fake(analytics: analytics)
        )
    }

    private func submit(_ text: String, _ viewModel: SearchViewModel) async {
        viewModel.updateText(text)
        viewModel.submitNow()
        await viewModel.waitForPendingWork()
    }

    @Test func aKeywordSearchSendsItsKindRouteResultsAndCategory() async {
        var page = SearchPage.of(SamplePapers.all, total: 48_210, next: "c2")
        page.route = .shared
        page.category = .ai
        let viewModel = viewModel(FakeSearchRepository(page: page))
        await submit("attention is all you need", viewModel)
        #expect(analytics.events.first == .search(kind: .keyword, hasFilters: false, route: .shared, results: .over200, category: .ai))
    }

    @Test func aFilteredSearchSaysSo() async {
        let viewModel = viewModel(FakeSearchRepository(page: .of([], total: 0)))
        viewModel.setOpenAccessOnly(true)
        await submit("bert", viewModel)
        #expect(analytics.events.contains(.search(kind: .keyword, hasFilters: true, route: .shared, results: .zero, category: .unknown)))
    }

    @Test func noEventCarriesTheSearchText() async {
        let viewModel = viewModel(FakeSearchRepository(page: .of(SamplePapers.all, total: 3)))
        await submit("Attention Is All You Need 10.1038/nature14539", viewModel)
        let text = analytics.events.map { "\($0.name) \($0.parameters)" }.joined()
        for word in ["Attention", "Need", "10.1038", "nature14539"] { #expect(!text.contains(word)) }
    }

    @Test func furtherPagesAndThePageCapAreCounted() async {
        let repository = FakeSearchRepository(maxPagesPerQuery: 2) { _, cursor in
            .of([SamplePapers.all[cursor == nil ? 0 : 1]], total: 500, next: "next-\(cursor ?? "first")")
        }
        let viewModel = viewModel(repository)
        await submit("bert", viewModel)
        viewModel.loadMore()
        await viewModel.waitForPendingWork()
        #expect(analytics.events.contains(.searchMore(page: 2)))
        #expect(analytics.events.contains(.searchLimitReached(.pageCap)))
    }

    @Test func theDailyLimitIsCounted() async {
        let viewModel = viewModel(FakeSearchRepository { _, _ in throw SearchError.dailyLimit(resetAt: .distantFuture) })
        await submit("bert", viewModel)
        #expect(analytics.events == [.searchLimitReached(.daily)])
    }

    @Test func aDOILookupIsASearchOfKindDOI() async {
        let lookup = FakePaperLookupRepository(otherwise: .found(SamplePapers.attention))
        let viewModel = viewModel(FakeSearchRepository(page: .of([])), lookup: lookup)
        await submit("10.48550/arXiv.1706.03762", viewModel)
        #expect(analytics.events == [.search(kind: .doi, hasFilters: false, route: .shared, results: .upTo25, category: nil)])
    }

    @Test func savingFromResultsAndFromALookupSayWhere() async {
        let viewModel = viewModel(FakeSearchRepository(page: .of([SamplePapers.bert])))
        await submit("bert", viewModel)
        await viewModel.toggleSave(SamplePapers.bert)
        #expect(analytics.events.last == .paperSaved(from: .search))

        let lookupViewModel = self.viewModel(FakeSearchRepository(page: .of([])), lookup: FakePaperLookupRepository(otherwise: .found(SamplePapers.attention)))
        await submit("arXiv:1706.03762", lookupViewModel)
        await lookupViewModel.toggleSave(SamplePapers.attention)
        #expect(analytics.events.last == .paperSaved(from: .lookup))
    }

    @Test func removingFromSearchIsFinalAtOnce() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.bert])
        let viewModel = SearchViewModel(repository: FakeSearchRepository(page: .of([SamplePapers.bert])), lookup: FakePaperLookupRepository(), library: library, preferences: FakeUserPreferencesRepository(), diagnostics: .fake(analytics: analytics))
        await submit("bert", viewModel)
        await viewModel.toggleSave(SamplePapers.bert)
        #expect(analytics.events.last == .paperRemoved)
    }
}
```

(Adapt `FakeLibraryRepository`/`FakePaperLookupRepository` initialisers and `SearchPage` mutation to their real APIs; the assertions are the requirement. If `SearchPage` properties are `let`, build it with the full initialiser.)

In `LibraryViewModelTests.swift` add: removing a paper logs nothing until `undoExpired()`, then one `.paperRemoved`; Undo logs nothing; a second removal makes the first final (`.paperRemoved` once).

- [ ] **Step 2: Run them to verify they fail**

Run `-only-testing:FeatureSearchTests` and `-only-testing:FeatureLibraryTests`. Expected: build failures for `diagnostics`.

- [ ] **Step 3: Implement in `SearchViewModel`**

Add the init parameter and `@ObservationIgnored private let diagnostics: Diagnostics`. Then:
- `loadFirstPage` success, after `self.add(page)`: `diagnostics.analytics.log(.search(kind: .keyword, hasFilters: active.hasActiveFilters, route: page.route ?? ownKeyRoute, results: ResultsBucket(count: page.totalCount), category: page.category))`, where `private var ownKeyRoute: SearchRoute { preferences.currentUserAPIKey != nil ? .user : .shared }` (keep a reference to `preferences`).
- First-page failure: if the error is `SearchError.dailyLimit`, log `.searchLimitReached(.daily)`; same in `loadNextPage`'s failure.
- In `add(_:)`: after `pagesLoaded += 1`, `if pagesLoaded >= 2 { diagnostics.analytics.log(.searchMore(page: pagesLoaded)) }`; when setting `.capReached`, log `.searchLimitReached(.pageCap)`.
- `runLookup`: when the result arrives — `.found` → `.search(kind: kind, hasFilters: false, route: ownKeyRoute, results: .upTo25, category: nil)`; `.notFound` → same with `.zero`; `.failed` → nothing. `kind`: `.doi` / `.arxiv` from the `PaperIdentifier`, but `.link` when the submitted text was a link (`looksLikeLink`/the same check `submit` uses).
- `toggleSave`: after a successful save, `.paperSaved(from: lookup != nil ? .lookup : .search)`; after a successful removal, `.paperRemoved`.

`AppContainer.makeSearchViewModel` passes `diagnostics: diagnostics`.

- [ ] **Step 4: Implement in `LibraryViewModel` and the iPad window**

`LibraryViewModel`: add the init parameter; log `.paperRemoved` in `undoExpired()` (when a pending removal exists) and where a previous pending removal is replaced in `remove(openAlexID:)`. `AppContainer.makeLibraryViewModel` passes `diagnostics`. `PaperWindow`'s `onRemove`: after a successful `remove`, `container.diagnostics.analytics.log(.paperRemoved)`.

- [ ] **Step 5: Run the tests to verify they pass**

Run `-only-testing:FeatureSearchTests`, `-only-testing:FeatureLibraryTests`.

- [ ] **Step 6: Commit**

```bash
git add ios/HashiyaKit ios/Hashiya
git commit -m "feat(ios): count searches, lookups, saves and removals"
```

---

### Task 7: Notes, collections, exports, restore and PDF events

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaData/NotesEditor.swift`
- Modify: `ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift` (collection create, BibTeX export)
- Modify: `ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsViewModel.swift` (create + add to collection)
- Modify: `ios/HashiyaKit/Sources/FeatureSettings/SettingsViewModel.swift` (backup export), `RestoreViewModel.swift` (restore)
- Modify: `ios/HashiyaKit/Sources/FeatureReader/ReaderViewModel.swift` (pdf opened)
- Modify: `ios/HashiyaKit/Sources/HashiyaData/Pdf/GRDBPdfRepository.swift`, `PdfRepository.swift` (`PdfDependencies.analytics`), `LiveDependencies.swift`
- Modify: `ios/Hashiya/AppContainer.swift`
- Test: the matching test files in `FeatureLibraryTests`, `FeaturePaperDetailsTests`, `FeatureSettingsTests`, `FeatureReaderTests`, `HashiyaDataTests`

**Interfaces:**
- Consumes: Task 1 types; `Diagnostics` parameters added in Tasks 4–6.
- Produces: `NotesEditor.init(…, diagnostics: Diagnostics = .none)`; `PaperDetailsViewModel.init(…, diagnostics: Diagnostics = .none)`; `ReaderViewModel.init(…, diagnostics: Diagnostics = .none)`; `PdfDependencies(…, analytics: any AnalyticsTracking = NoAnalytics())`; `LiveDependencies.live(background:crash:analytics:)`.

- [ ] **Step 1: Write the failing tests**

One test per event, each with `FakeAnalytics`:
- **Notes:** two saves of the same paper's notes log one `.noteEdited`; another paper logs a second; a failed save logs nothing. The note's text (use one containing "10.1038/nature14539") appears in no event. Once-per-session dedupe: a process-wide set of paper ids kept in memory only (`NotedPapers.shared`, an actor or a lock-guarded set in `HashiyaData`), never sent.
- **Collections:** `LibraryViewModel.submitName` in create mode logs `.collectionCreated` (rename logs nothing); `PaperDetailsViewModel.submitNewCollection` logs `.collectionCreated` then `.paperAddedToCollection`; `toggleCollection` adding logs `.paperAddedToCollection`, removing logs nothing.
- **Exports:** a BibTeX export whose share sheet was shown logs `.export(format: .bibtex, withPdfs: false)`; a failed one logs nothing; `exportFinished(.saved)` logs `.export(format: .backup, withPdfs: <the includePdfs chosen>)`, `.cancelled`/`.failed` log nothing.
- **Restore:** `.done` logs `.restore(succeeded: true)`; a failure logs `.restore(succeeded: false)`; cancelling before the merge logs nothing.
- **PDF opened:** `ReaderViewModel` reaching `.ready` logs `.pdfOpened(source:)` with the stored `PdfSource` (`.downloaded`/`.attached`), once; `.cantOpen` logs nothing.
- **PDF downloaded:** a download that stores the file logs `.pdfDownloaded(succeeded: true)`; one that ends `.failed` logs `succeeded: false`; a cancelled one logs nothing.

- [ ] **Step 2: Run them to verify they fail**

Run the affected targets. Expected: build failures for the new parameters.

- [ ] **Step 3: Implement**

- `NotesEditor`: after a successful `save`, `if NotedPapers.shared.firstEdit(openAlexID) { diagnostics.analytics.log(.noteEdited) }`. `AppContainer` passes `diagnostics` to both `NotesEditor`s (Reader's in `makeReaderViewModel`; Details' through `PaperDetailsViewModel`).
- `LibraryViewModel.submitName`: on `.done` with `sheet.mode == .create`, `.collectionCreated`. `export()`: after `guard await share(file)`, `.export(format: .bibtex, withPdfs: false)`.
- `PaperDetailsViewModel`: `submitNewCollection` → `.collectionCreated` after `.done`, `.paperAddedToCollection` after the membership write succeeds; `toggleCollection` → `.paperAddedToCollection` when adding succeeds.
- `SettingsViewModel`: remember `includePdfs` in `confirmExport()` (`@ObservationIgnored private var exportIncludesPdfs = false`); in `exportFinished(.saved)`, `.export(format: .backup, withPdfs: exportIncludesPdfs)`.
- `RestoreViewModel.confirm()`: `.restore(succeeded: true)` with `.done`; `.restore(succeeded: false)` in both failure branches except `CancellationError`.
- `ReaderViewModel`: when `open(_:page:)` reaches `.ready` the first time, `.pdfOpened(source: stored.source == .attached ? .attached : .downloaded)`.
- `GRDBPdfRepository.run`/`attemptDownload`: return a private result that distinguishes stored / failed / stopped; log `.pdfDownloaded(succeeded: true)` for stored, `false` for failed, nothing for stopped. `PdfDependencies` gains `analytics`, filled by `LiveDependencies.live(background:crash:analytics:)` from `AppContainer.make`.
- Every view model created in `AppContainer` gets `diagnostics: diagnostics`.

- [ ] **Step 4: Run the tests to verify they pass**

Run every unit-test target (`-only-testing:` each of HashiyaDataTests, FeatureLibraryTests, FeaturePaperDetailsTests, FeatureSettingsTests, FeatureReaderTests, FeatureSearchTests), then build-for-testing the scheme (UI tests and snapshot tests must still compile).

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit ios/Hashiya
git commit -m "feat(ios): count notes, collections, exports, restores and PDFs"
```

---

### Task 8: The privacy manifests

**Files:**
- Modify: `ios/Hashiya/PrivacyInfo.xcprivacy`, `ios/HashiyaShare/PrivacyInfo.xcprivacy`

- [ ] **Step 1: The app's manifest**

Replace `ios/Hashiya/PrivacyInfo.xcprivacy` with:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>NSPrivacyTracking</key>
	<false/>
	<key>NSPrivacyTrackingDomains</key>
	<array/>
	<key>NSPrivacyCollectedDataTypes</key>
	<array>
		<dict>
			<key>NSPrivacyCollectedDataType</key>
			<string>NSPrivacyCollectedDataTypeCrashData</string>
			<key>NSPrivacyCollectedDataTypeLinked</key>
			<false/>
			<key>NSPrivacyCollectedDataTypeTracking</key>
			<false/>
			<key>NSPrivacyCollectedDataTypePurposes</key>
			<array><string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string></array>
		</dict>
		<dict>
			<key>NSPrivacyCollectedDataType</key>
			<string>NSPrivacyCollectedDataTypeOtherDiagnosticData</string>
			<key>NSPrivacyCollectedDataTypeLinked</key>
			<false/>
			<key>NSPrivacyCollectedDataTypeTracking</key>
			<false/>
			<key>NSPrivacyCollectedDataTypePurposes</key>
			<array><string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string></array>
		</dict>
		<dict>
			<key>NSPrivacyCollectedDataType</key>
			<string>NSPrivacyCollectedDataTypeProductInteraction</string>
			<key>NSPrivacyCollectedDataTypeLinked</key>
			<false/>
			<key>NSPrivacyCollectedDataTypeTracking</key>
			<false/>
			<key>NSPrivacyCollectedDataTypePurposes</key>
			<array><string>NSPrivacyCollectedDataTypePurposeAnalytics</string></array>
		</dict>
		<dict>
			<key>NSPrivacyCollectedDataType</key>
			<string>NSPrivacyCollectedDataTypeDeviceID</string>
			<key>NSPrivacyCollectedDataTypeLinked</key>
			<false/>
			<key>NSPrivacyCollectedDataTypeTracking</key>
			<false/>
			<key>NSPrivacyCollectedDataTypePurposes</key>
			<array>
				<string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string>
				<string>NSPrivacyCollectedDataTypePurposeAnalytics</string>
			</array>
		</dict>
	</array>
	<key>NSPrivacyAccessedAPITypes</key>
	<array>
		<dict>
			<key>NSPrivacyAccessedAPIType</key>
			<string>NSPrivacyAccessedAPICategoryUserDefaults</string>
			<key>NSPrivacyAccessedAPITypeReasons</key>
			<array><string>CA92.1</string></array>
		</dict>
		<dict>
			<key>NSPrivacyAccessedAPIType</key>
			<string>NSPrivacyAccessedAPICategoryFileTimestamp</string>
			<key>NSPrivacyAccessedAPITypeReasons</key>
			<array><string>C617.1</string></array>
		</dict>
		<dict>
			<key>NSPrivacyAccessedAPIType</key>
			<string>NSPrivacyAccessedAPICategoryDiskSpace</string>
			<key>NSPrivacyAccessedAPITypeReasons</key>
			<array><string>E174.1</string></array>
		</dict>
	</array>
</dict>
</plist>
```

Reasons: **CA92.1** — the app's own UserDefaults (switches, OpenAlex quota and limits); **C617.1** — timestamps of files inside the app's containers (the search cache's last use, opened-backup inbox files); **E174.1** — free space checked before writing PDFs and backups.

- [ ] **Step 2: The Share Extension's manifest**

Keep `NSPrivacyTracking` false and `NSPrivacyCollectedDataTypes` empty (the extension collects nothing), and add the same three `NSPrivacyAccessedAPITypes` entries (it builds `LiveDependencies`, so it uses UserDefaults for the quota, the search cache's timestamps and the PDF store's free-space check).

- [ ] **Step 3: Check**

`plutil -lint ios/Hashiya/PrivacyInfo.xcprivacy ios/HashiyaShare/PrivacyInfo.xcprivacy` → both OK. Build the app. In Xcode, Product → Archive → Generate Privacy Report later (Task 11's release checklist) should list Crash Data, Other Diagnostic Data, Product Interaction and Device ID, not linked, not tracking.

- [ ] **Step 4: Commit**

```bash
git add ios/Hashiya/PrivacyInfo.xcprivacy ios/HashiyaShare/PrivacyInfo.xcprivacy
git commit -m "fix(ios): declare the collected data and the required-reason APIs in the privacy manifests"
```

---

### Task 9: Store answers, release docs and the changelog

**Files:**
- Modify: `docs/store/metadata.md` (App Store Connect: App Privacy)
- Modify: `docs/release.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: App Privacy answers**

Replace the `### App Store Connect: App Privacy` section of `docs/store/metadata.md` with:

```markdown
### App Store Connect: App Privacy

- **Data collection:** "Yes, we collect data from this app."
  - **Crash Data** — App Functionality; not linked to the user; not used for tracking.
  - **Other Diagnostic Data** — App Functionality; not linked; no tracking.
  - **Product Interaction** — Analytics; not linked; no tracking.
  - **Device ID** (Firebase's installation and app-instance ids) — App Functionality and Analytics; not linked; no tracking.
  - Nothing else: search text, papers, notes and the library stay on the device; searches go straight to OpenAlex and arXiv.
- **Tracking:** none. No advertising id (the app uses `FirebaseAnalyticsCore`, which has no IDFA support), no App Tracking Transparency prompt.
- **Privacy manifest:** `PrivacyInfo.xcprivacy` in the app declares the four types above and the required-reason APIs (UserDefaults CA92.1, file timestamps C617.1, disk space E174.1); the share extension declares no collected data and the same APIs.
- Check against Firebase's current Apple data-disclosure page before each release that changes Firebase.
```

- [ ] **Step 2: Release docs**

In `docs/release.md`:
- In `### Fill in the listing (App Store Connect → the app)`, replace "Data Not Collected" with "the answers in `docs/store/metadata.md` → App Privacy".
- In `## Crashlytics (Android)`, replace "No Firebase Analytics is included." with "Firebase Analytics is added by the usage-statistics work (see below)."
- Add a section before `## Large screens (Android)`:

```markdown
## Crashlytics and Analytics (iOS)

Release builds configure Firebase (project `hashiya-research`, app `1:10078456816:ios:b46501427f2408bcb12b35`); Debug
builds, tests and the Share Extension never do. Settings → Privacy has **Send crash reports** and **Share usage
statistics**, both on by default.

### One-time setup
1. Google Cloud → Credentials: restrict the iOS API key to iOS apps, bundle id `com.etatech.hashiya`.
2. Firebase console → Crashlytics → Enable (if not already). dSYMs upload from the Release build's script phase.
3. Firebase console → Project settings → Integrations → Google Analytics: link a GA4 property. In GA: data retention
   2 months; Google signals off; no Google Ads links.

### Before a release with this work
- [ ] The privacy-policy update (Usage statistics) is merged and live — **before** the build reaches TestFlight external
      testers or the App Store.
- [ ] App Privacy answers match `docs/store/metadata.md`; Xcode → Archive → Generate Privacy Report shows the same.
- [ ] Test crash: run the Release configuration from Xcode once with the argument `-hashiya-test-crash`, stop it, open
      the app from the Home Screen: it crashes after two seconds; reopen it; the crash appears in Crashlytics with
      readable frames and the keys `screen`, `language`, `librarySizeBucket`, `backupInProgress`.
- [ ] Analytics: run the Release configuration with `-FIRDebugEnabled`; Firebase DebugView shows the events with only
      the listed parameters (e.g. `search` with `category`), and no advertising id.
- [ ] With both switches off, nothing new arrives.
```

- [ ] **Step 3: Changelog**

Under `## [Unreleased]` → `### Added`, add:

```markdown
- **iOS: crash reports and usage statistics.** Release builds send crash reports (Firebase Crashlytics) and anonymous usage statistics (Firebase Analytics): which features are used and a broad research area worked out on the device from search results — never search text, papers or notes. Both are on by default and can be turned off in Settings → Privacy.
```

- [ ] **Step 4: Commit**

```bash
git add docs/store/metadata.md docs/release.md CHANGELOG.md
git commit -m "docs: iOS App Privacy answers, Crashlytics and Analytics release steps"
```

---

### Task 10: The privacy-policy update (release gate)

**Repository:** `FadyFouad/Hashiya-Privacy-Policy` (separate repo; its `index.html` holds the English and Arabic policy). Clone into the session scratchpad, branch `usage-statistics`, open a PR. **Do not merge** — the user merges it before the first build with analytics reaches users.

- [ ] **Step 1: English changes**

- Opening paragraph: replace "Hashiya has no account, no usage analytics, no ads and no tracking. … The only thing that can reach us is a crash report — technical details with nothing you wrote or read — and you can turn that off in Settings." with: "Hashiya has no account, no ads and no tracking. Your library, notes and PDFs stay on your device. We don't run a server. What can reach us is a crash report and anonymous usage statistics — never anything you wrote or read — and you can turn either off in Settings."
- New section after "Crash reports", titled **Usage statistics**:

  > To learn which features help and what to improve, Hashiya sends anonymous usage statistics to Firebase Analytics (Google Analytics for Firebase), a Google service. They contain:
  > - counts of what you do in the app: searches (whether filters were on, roughly how many results, and whether the search used Hashiya's shared allowance, your own key or no key), papers saved or removed, notes edited, collections created, exports, restores, PDFs downloaded or opened, and which screen is open;
  > - for keyword searches, a broad research area such as "artificial intelligence" or "computer networks", worked out on your device from the OpenAlex topics of the results — your search words are never sent;
  > - the app's language, a rough library size (for example "51–500 papers"), whether you use your own OpenAlex key, and the app version, device model and operating system;
  > - a random app-instance identifier created by Firebase, which isn't linked to you and isn't an advertising identifier.
  >
  > They never contain your search words, papers, titles, DOIs, notes, collection names, files or API key. Advertising features are off: no advertising identifier is collected and nothing is used for ads or shared with advertisers. Google processes this data for us under Firebase's privacy terms and keeps it for 2 months.
  >
  > Usage statistics are on by default. Turn them off in **Settings → Privacy → Share usage statistics**; the app then stops sending them and deletes its analytics identifier and anything not yet sent.

- "Deleting your data": add "Usage statistics can't be linked to you either; they are deleted after 2 months. To stop sending them, turn them off in Settings."
- "Children": "it collects no personal information from anyone; crash reports and usage statistics contain no personal details."
- Effective date: the PR's merge date (update both languages).

- [ ] **Step 2: Arabic changes (same structure)**

- Opening: "لا يحتاج تطبيق حاشية إلى حساب، ولا يعرض إعلانات ولا يتتبّعك. تبقى مكتبتك وملاحظاتك وملفات PDF على جهازك، وليس لدينا خادم. وما قد يصلنا هو تقرير عطل وإحصاءات استخدام مجهولة، دون أي شيء كتبته أو قرأته، ويمكنك إيقاف أيٍّ منهما من الإعدادات."
- Section **إحصاءات الاستخدام**:

  > لمعرفة الميزات المفيدة وما يجب تحسينه، يرسل حاشية إحصاءات استخدام مجهولة إلى Firebase Analytics ‏(Google Analytics for Firebase)، وهي خدمة من Google. وتتضمن:
  > - أعدادًا لما تفعله في التطبيق: عمليات البحث (وهل كانت عوامل التصفية مفعّلة، وعددًا تقريبيًا للنتائج، وهل استخدم البحث الحصة المشتركة لحاشية أو مفتاحك أو لا مفتاح)، والأوراق المحفوظة أو المُزالة، والملاحظات المعدّلة، والمجموعات المُنشأة، والتصدير، والاستعادة، وملفات PDF المُنزّلة أو المفتوحة، والشاشة المفتوحة؛
  > - ولعمليات البحث بالكلمات، مجالًا بحثيًا عامًا مثل «الذكاء الاصطناعي» أو «شبكات الحاسوب»، يُستنتج على جهازك من موضوعات OpenAlex للنتائج، ولا تُرسَل كلمات بحثك أبدًا؛
  > - لغة التطبيق، وحجمًا تقريبيًا للمكتبة (مثل «51–500 ورقة»)، وهل تستخدم مفتاح OpenAlex خاصًا بك، وإصدار التطبيق وطراز الجهاز ونظام التشغيل؛
  > - معرّفًا عشوائيًا لنسخة التطبيق تنشئه Firebase، لا يرتبط بك وليس معرّفًا إعلانيًا.
  >
  > ولا تتضمن أبدًا كلمات بحثك أو أوراقك أو عناوينها أو أرقام DOI أو ملاحظاتك أو أسماء مجموعاتك أو ملفاتك أو مفتاح API. وميزات الإعلانات متوقفة: لا يُجمع أي معرّف إعلاني ولا يُستخدم شيء للإعلانات ولا يُشارك مع المعلنين. وتعالج Google هذه البيانات نيابةً عنا وفق شروط الخصوصية في Firebase وتحتفظ بها شهرين.
  >
  > إحصاءات الاستخدام مفعّلة افتراضيًا. يمكنك إيقافها من **الإعدادات ← الخصوصية ← مشاركة إحصاءات الاستخدام**، فيتوقف التطبيق عن إرسالها ويحذف معرّف التحليلات وكل ما لم يُرسَل بعد.

- Deleting and Children: the same additions in Arabic ("ولا يمكن ربط إحصاءات الاستخدام بك كذلك، وتُحذف بعد شهرين. ولإيقاف إرسالها، أوقفها من الإعدادات." / "…ولا تتضمن تقارير الأعطال ولا إحصاءات الاستخدام أي تفاصيل شخصية.").
- Keep the page's existing markup patterns (headings, lists, links to Firebase's privacy terms) and its no-"·"-between-numbers rule for Arabic.

- [ ] **Step 2b: Open the PR**

```bash
cd <scratchpad>/policy-repo && git switch -c usage-statistics && git commit -am "Describe usage statistics" && git push -u origin usage-statistics
gh pr create --repo FadyFouad/Hashiya-Privacy-Policy --title "Usage statistics" --body "Adds the Usage statistics section (English and Arabic) for Hashiya's Firebase Analytics, removes \"no usage analytics\". Merge before the first build with analytics reaches users."
```

Check the author is `Fady <fady.fouad.a@gmail.com>` before pushing. Link the PR from the Hashiya PR's description (release gate).

---

### Task 11: Snapshot baselines (controller, after pushing the branch)

- [ ] From `feat/ios-diagnostics`: `bash ios/scripts/record-snapshots-on-ci.sh`. Look at the Settings images in English and Arabic (the Privacy section with both switches and the policy link) before committing them; drop unrelated re-recorded images.
