# OpenAlex Quota Protection (iOS) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Protect the shared OpenAlex budget on iOS: route each request through the user key, the shared route (built-in key or a configurable proxy) under a per-device daily cap, or no key; cache searches for a day; cap pages per search; and tell the user plainly when searching is out until a given time.

**Architecture:** Everything happens in `HashiyaNetwork`'s `OpenAlexHTTP`, the one place both OpenAlex clients send requests. A new `OpenAlexQuota` picks the route and remembers the cap count and used-up budgets in `UserDefaults`; a new `SearchCache` answers repeated searches from disk; `OpenAlexLimits` (from `app-config.json`'s `openAlex` section, fetched with the existing minimum-version check) sets the cap, the page limit and an optional proxy. Search shows a new "Daily search limit reached" state and a page-cap footer; Settings gains a footer pointing to free personal keys.

**Tech Stack:** Swift 6, SwiftUI, Swift Testing, URLSession, CryptoKit, `UserDefaults`, swift-snapshot-testing (existing).

**Spec:** `docs/superpowers/specs/2026-10-05-openalex-quota-design.md`

## Global Constraints

- Metered calls are `GET /works` (search and filter list); `GET /works/{id}` lookups are never metered, never counted, never blocked by Out.
- Route order for metered calls: User → Shared → Keyless → Out. With a user key set, only the User route is used.
- Shared route: `baseUrl` with no key if set; otherwise `https://api.openalex.org` with the built-in key. The built-in key is never sent to `baseUrl`; user-key and keyless requests never go to `baseUrl`.
- 429 with `X-RateLimit-Remaining` ≤ 0: mark the route used up until now + `X-RateLimit-Reset` seconds (next midnight UTC if missing or unreadable), store it, retry once on the next route.
- 429 with budget left (or no readable `Remaining`): wait `Retry-After` seconds capped at 3 (1 if absent), retry once on the same route, then the rate-limited error.
- Proxy unreachable or 5xx: that request moves to Keyless; the proxy isn't marked.
- Device cap: metered calls sent on Shared, counted when sent, per UTC day (not local day); cached answers don't count.
- Config `openAlex`: `dailyDeviceCalls` whole number 0–1000, default 60 (0 = Shared off for metered calls); `maxPagesPerQuery` whole number 1–40, default 8; `baseUrl` absolute `https://` URL or null, default null. Each field validated on its own.
- Cache: search and filter-list 2xx responses, key without `api_key` or host, 24 hours, 5 MB, least recently used removed first, in `Caches`.
- Nothing new leaves the device; no install id; rate limits are never reported as errors anywhere.
- Copy (English / Arabic) exactly as in the tasks; Arabic times wrapped in U+2068…U+2069 inside the sentence; every new String Catalog key has an `ar` translation in state `translated` (CI runs `ios/scripts/check-translations.py`).
- Commits: author `Fady <fady.fouad.a@gmail.com>`; no AI attribution anywhere.

## Rulings on the spec for iOS

- **Refresh skipping the cache (spec §6):** iOS Search has no pull-to-refresh, and its Retry only follows an error, which is never cached; so nothing needs to bypass the cache.
- **"Open Settings at the API key field" (spec §7):** the existing `onOpenSettings` opens Settings, whose first section is the API key; no scrolling is added.
- **Share Extension:** it builds its own `LiveDependencies`, so it has its own cap and marks in its own defaults, never fetches the config and uses the default limits. It mostly makes free lookups.

## Review Focus

1. A device east of UTC near local midnight (e.g. 00:30 in UTC+3, 21:30 UTC): the cap must not reset until UTC midnight — Task 3 tests it.
2. `X-RateLimit-Remaining` written as `0.0` or `-1` must count as used up; a non-number (`abc`) must be treated as a per-second limit — Task 6 tests it.
3. A proxy `baseUrl` with a path prefix (`https://proxy.example/openalex`) must receive `/openalex/works`, not `/works` — Task 6 tests it.
4. Cache keys must ignore `api_key` (same search via different routes hits) but differ for different cursors and filters — Task 4 tests it.
5. A search cancelled while waiting to retry after a per-second 429 must end with `CancellationError` and mark nothing — Task 6 tests it.

## How to run tests (this Mac)

All commands from the repository root. Generate the project once per task (it is git-ignored):

```bash
cd ios && xcodegen generate --spec project.yml >/dev/null && cd ..
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya \
  -destination 'platform=iOS Simulator,id=F857F105-E1D5-449B-BBB0-0FA009FBD574' \
  -only-testing:HashiyaNetworkTests -collect-test-diagnostics never 2>&1 | tail -40
```

Swap `-only-testing:` for the target named in each step (`HashiyaNetworkTests`, `HashiyaDataTests`, `HashiyaModelTests`, `FeatureSearchTests`, `FeatureSettingsTests`; a single test: `-only-testing:HashiyaNetworkTests/OpenAlexQuotaTests`). Snapshot tests (`HashiyaSnapshotTests`) are recorded on CI in Task 9, not locally.

---

### Task 1: Test helpers and the `openAlex` limits

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaTesting/URLProtocolStub.swift`
- Create: `ios/HashiyaKit/Sources/HashiyaTesting/TestDefaults.swift`
- Create: `ios/HashiyaKit/Sources/HashiyaNetwork/OpenAlexLimits.swift`
- Test: `ios/HashiyaKit/Tests/HashiyaNetworkTests/OpenAlexLimitsTests.swift`

**Interfaces:**
- Produces: `URLProtocolStub.Reply.status(Int, body: Data = Data(), headers: [String: String] = [:])`; `TestDefaults.make() -> UserDefaults`; `TestClock` (`init(_ date: Date)`, `var date: Date { get }`, `func advance(by: TimeInterval)`, `func set(_ date: Date)`); `OpenAlexLimits` (`dailyDeviceCalls: Int`, `maxPagesPerQuery: Int`, `baseURL: URL?`, `static let defaults`, `static func parse(_ section: Any?) -> OpenAlexLimits`); `OpenAlexLimitsStore` (`init(defaults: UserDefaults = .standard)`, `var limits: OpenAlexLimits`, `func save(_:)`).

- [ ] **Step 1: Let the stub answer with headers**

In `URLProtocolStub.swift`, change the `status` case and its use:

```swift
        /// A response with this status, body and headers.
        case status(Int, body: Data = Data(), headers: [String: String] = [:])
```

```swift
        case let .status(code, body, headers):
            let response = HTTPURLResponse(url: url, statusCode: code, httpVersion: "HTTP/1.1", headerFields: headers)!
```

(Existing `.status(404)`, `.status(200, body:)` and `.json(_:)` calls keep compiling.)

- [ ] **Step 2: Add the defaults and clock helpers**

`ios/HashiyaKit/Sources/HashiyaTesting/TestDefaults.swift`:

```swift
import Foundation
import os

/// A fresh, empty `UserDefaults` suite per call, so tests never share or leak stored values.
public enum TestDefaults {
    public static func make() -> UserDefaults {
        let name = "tests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defaults.removePersistentDomain(forName: name)
        return defaults
    }
}

/// A settable clock: pass `{ clock.date }` where a type takes `now: @Sendable () -> Date`.
public final class TestClock: Sendable {
    private let state: OSAllocatedUnfairLock<Date>

    public init(_ date: Date) {
        state = OSAllocatedUnfairLock(initialState: date)
    }

    /// "2026-10-05T21:30:00Z".
    public convenience init(_ iso: String) {
        self.init(ISO8601DateFormatter().date(from: iso)!)
    }

    public var date: Date { state.withLock { $0 } }

    public func advance(by seconds: TimeInterval) {
        state.withLock { $0 += seconds }
    }

    public func set(_ date: Date) {
        state.withLock { $0 = date }
    }
}
```

- [ ] **Step 3: Write the failing tests**

`ios/HashiyaKit/Tests/HashiyaNetworkTests/OpenAlexLimitsTests.swift`:

```swift
import Foundation
import HashiyaNetwork
import HashiyaTesting
import Testing

struct OpenAlexLimitsTests {
    private func parse(_ json: String) -> OpenAlexLimits {
        OpenAlexLimits.parse(try! JSONSerialization.jsonObject(with: Data(json.utf8), options: [.fragmentsAllowed]))
    }

    @Test func defaultsAreSixtyCallsEightPagesAndNoProxy() {
        #expect(OpenAlexLimits.defaults == OpenAlexLimits(dailyDeviceCalls: 60, maxPagesPerQuery: 8, baseURL: nil))
    }

    @Test func readsEveryValidField() {
        let limits = parse(#"{"dailyDeviceCalls": 0, "maxPagesPerQuery": 40, "baseUrl": "https://proxy.example/openalex"}"#)
        #expect(limits == OpenAlexLimits(dailyDeviceCalls: 0, maxPagesPerQuery: 40, baseURL: URL(string: "https://proxy.example/openalex")))
    }

    @Test(arguments: ["-1", "1001", #""60""#, "12.5", "true", "null"])
    func anInvalidCallCountFallsBackAlone(value: String) {
        let limits = parse(#"{"dailyDeviceCalls": \#(value), "maxPagesPerQuery": 3}"#)
        #expect(limits.dailyDeviceCalls == 60)
        #expect(limits.maxPagesPerQuery == 3)
    }

    @Test func aMissingFieldFallsBackAlone() {
        #expect(parse(#"{"maxPagesPerQuery": 3}"#) == OpenAlexLimits(dailyDeviceCalls: 60, maxPagesPerQuery: 3, baseURL: nil))
    }

    @Test(arguments: [#"{"maxPagesPerQuery": 0}"#, #"{"maxPagesPerQuery": 41}"#, #"{"maxPagesPerQuery": "8"}"#])
    func anInvalidPageLimitFallsBack(json: String) {
        #expect(parse(json).maxPagesPerQuery == 8)
    }

    @Test(arguments: [
        #"{"baseUrl": "http://proxy.example"}"#, #"{"baseUrl": "proxy.example"}"#, #"{"baseUrl": "https://"}"#,
        #"{"baseUrl": 5}"#, #"{"baseUrl": null}"#,
    ])
    func aBaseURLThatIsNotHTTPSIsIgnored(json: String) {
        #expect(parse(json).baseURL == nil)
    }

    @Test(arguments: ["[]", "5", #""x""#, "null"])
    func aSectionThatIsNotAnObjectMeansAllDefaults(json: String) {
        #expect(parse(json) == .defaults)
    }

    @Test func theStoreKeepsTheLastSavedLimits() {
        let defaults = TestDefaults.make()
        #expect(OpenAlexLimitsStore(defaults: defaults).limits == .defaults)
        let saved = OpenAlexLimits(dailyDeviceCalls: 5, maxPagesPerQuery: 2, baseURL: URL(string: "https://proxy.example"))
        OpenAlexLimitsStore(defaults: defaults).save(saved)
        #expect(OpenAlexLimitsStore(defaults: defaults).limits == saved)
    }

    @Test func unreadableStoredLimitsMeanDefaults() {
        let defaults = TestDefaults.make()
        defaults.set(Data("nope".utf8), forKey: OpenAlexLimitsStore.key)
        #expect(OpenAlexLimitsStore(defaults: defaults).limits == .defaults)
    }
}
```

- [ ] **Step 4: Run them to verify they fail**

Run the test command with `-only-testing:HashiyaNetworkTests/OpenAlexLimitsTests`.
Expected: build failure — `cannot find 'OpenAlexLimits' in scope`.

- [ ] **Step 5: Implement the limits and their store**

`ios/HashiyaKit/Sources/HashiyaNetwork/OpenAlexLimits.swift`:

```swift
import Foundation

/// The `openAlex` section of the remote config: how this device may use the shared OpenAlex budget.
public struct OpenAlexLimits: Codable, Equatable, Sendable {
    /// Metered calls per device per UTC day on the shared route; 0 turns the shared route off for metered calls.
    public var dailyDeviceCalls: Int
    /// Pages one search can load.
    public var maxPagesPerQuery: Int
    /// A proxy for the shared route, or nil to send the built-in key to api.openalex.org.
    public var baseURL: URL?

    public init(dailyDeviceCalls: Int, maxPagesPerQuery: Int, baseURL: URL?) {
        self.dailyDeviceCalls = dailyDeviceCalls
        self.maxPagesPerQuery = maxPagesPerQuery
        self.baseURL = baseURL
    }

    public static let defaults = OpenAlexLimits(dailyDeviceCalls: 60, maxPagesPerQuery: 8, baseURL: nil)

    /// Reads the section field by field: a missing, out-of-range, wrong-type or non-https value falls back to its
    /// default and the others still apply. Anything but an object means all defaults.
    public static func parse(_ section: Any?) -> OpenAlexLimits {
        guard let fields = section as? [String: Any] else { return .defaults }
        return OpenAlexLimits(
            dailyDeviceCalls: wholeNumber(fields["dailyDeviceCalls"], in: 0...1000) ?? defaults.dailyDeviceCalls,
            maxPagesPerQuery: wholeNumber(fields["maxPagesPerQuery"], in: 1...40) ?? defaults.maxPagesPerQuery,
            baseURL: httpsURL(fields["baseUrl"])
        )
    }

    private static func wholeNumber(_ value: Any?, in range: ClosedRange<Int>) -> Int? {
        // JSONSerialization gives booleans as NSNumber too.
        guard let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
        let double = number.doubleValue
        guard double.rounded() == double, let whole = Int(exactly: double), range.contains(whole) else { return nil }
        return whole
    }

    private static func httpsURL(_ value: Any?) -> URL? {
        guard let text = value as? String, let url = URL(string: text), url.scheme?.lowercased() == "https",
              let host = url.host(), !host.isEmpty else { return nil }
        return url
    }
}

/// The last valid `openAlex` section, kept so a failed fetch or a launch offline still has it.
/// `UserDefaults` is thread-safe.
public struct OpenAlexLimitsStore: @unchecked Sendable {
    public static let key = "openAlex.limits"

    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public var limits: OpenAlexLimits {
        guard let data = defaults.data(forKey: Self.key),
              let limits = try? JSONDecoder().decode(OpenAlexLimits.self, from: data) else { return .defaults }
        return limits
    }

    public func save(_ limits: OpenAlexLimits) {
        guard let data = try? JSONEncoder().encode(limits) else { return }
        defaults.set(data, forKey: Self.key)
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run `-only-testing:HashiyaNetworkTests` (the whole target, to confirm the stub change broke nothing).
Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaTesting ios/HashiyaKit/Sources/HashiyaNetwork/OpenAlexLimits.swift ios/HashiyaKit/Tests/HashiyaNetworkTests/OpenAlexLimitsTests.swift
git commit -m "feat(ios): read the openAlex limits from the remote config"
```

---

### Task 2: Fetch the limits with the update check

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaNetwork/AppConfigClient.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaData/AppUpdateRepository.swift`
- Modify: `ios/HashiyaKit/Tests/HashiyaNetworkTests/AppConfigClientTests.swift`
- Modify: `ios/HashiyaKit/Tests/HashiyaDataTests/AppUpdateTests.swift`

**Interfaces:**
- Consumes: `OpenAlexLimits.parse`, `OpenAlexLimitsStore` (Task 1).
- Produces: `RemoteAppConfig` (`ios: PlatformAppConfig?`, `openAlex: OpenAlexLimits`); `AppConfigService.fetch() async throws -> RemoteAppConfig` (replaces `iosConfig()`); `ConfigAppUpdateRepository.init(service:limitsStore:)` with `limitsStore: OpenAlexLimitsStore? = nil`.

- [ ] **Step 1: Update the client tests**

In `AppConfigClientTests.swift`, replace every `.iosConfig()` with `.fetch().ios` (e.g. `#expect(try await client(server).fetch().ios == PlatformAppConfig(minimumBuild: 3, storeUrl: appStore))`, `_ = try await client(server).fetch()`, `try await client(server).fetch()` inside `#expect(throws:)`), then add:

```swift
    @Test func readsTheOpenAlexSection() async throws {
        let server = URLProtocolStub.Server(always: .json(#"{"openAlex":{"dailyDeviceCalls":20,"maxPagesPerQuery":4,"baseUrl":"https://proxy.example"}}"#))
        let config = try await client(server).fetch()
        #expect(config.openAlex == OpenAlexLimits(dailyDeviceCalls: 20, maxPagesPerQuery: 4, baseURL: URL(string: "https://proxy.example")))
        #expect(config.ios == nil)
    }

    @Test func aMissingOpenAlexSectionMeansDefaults() async throws {
        let server = URLProtocolStub.Server(always: .json(sample))
        #expect(try await client(server).fetch().openAlex == .defaults)
    }

    @Test func aBadOpenAlexFieldDoesNotSpoilTheIOSEntry() async throws {
        let server = URLProtocolStub.Server(always: .json(#"{"ios":{"minimumBuild":2},"openAlex":{"dailyDeviceCalls":"lots"}}"#))
        let config = try await client(server).fetch()
        #expect(config.ios == PlatformAppConfig(minimumBuild: 2, storeUrl: nil))
        #expect(config.openAlex == .defaults)
    }
```

- [ ] **Step 2: Update the repository tests**

In `AppUpdateTests.swift`, replace the stub service and helper with:

```swift
private struct StubConfigService: AppConfigService {
    let answer: @Sendable () throws -> PlatformAppConfig?
    var openAlex = OpenAlexLimits.defaults
    func fetch() async throws -> RemoteAppConfig { RemoteAppConfig(ios: try answer(), openAlex: openAlex) }
}
```

(`repository(_:)` stays as it is.) Add to `ConfigAppUpdateRepositoryTests`:

```swift
    @Test func savesTheOpenAlexLimitsItFetched() async {
        let defaults = TestDefaults.make()
        let limits = OpenAlexLimits(dailyDeviceCalls: 7, maxPagesPerQuery: 2, baseURL: nil)
        let repository = ConfigAppUpdateRepository(
            service: StubConfigService(answer: { nil }, openAlex: limits),
            limitsStore: OpenAlexLimitsStore(defaults: defaults)
        )
        _ = await repository.requiredUpdate(currentBuild: 1)
        #expect(OpenAlexLimitsStore(defaults: defaults).limits == limits)
    }

    @Test func aFailedFetchKeepsTheStoredLimits() async {
        let defaults = TestDefaults.make()
        let stored = OpenAlexLimits(dailyDeviceCalls: 9, maxPagesPerQuery: 3, baseURL: nil)
        OpenAlexLimitsStore(defaults: defaults).save(stored)
        let repository = ConfigAppUpdateRepository(
            service: StubConfigService(answer: { throw NetworkFailure.connectivity }),
            limitsStore: OpenAlexLimitsStore(defaults: defaults)
        )
        _ = await repository.requiredUpdate(currentBuild: 1)
        #expect(OpenAlexLimitsStore(defaults: defaults).limits == stored)
    }
```

- [ ] **Step 3: Run them to verify they fail**

Run `-only-testing:HashiyaNetworkTests/AppConfigClientTests` then `-only-testing:HashiyaDataTests/ConfigAppUpdateRepositoryTests`.
Expected: build failure — `value of type 'AppConfigClient' has no member 'fetch'`.

- [ ] **Step 4: Implement `fetch()`**

In `AppConfigClient.swift`, add above the protocol:

```swift
/// Everything this app reads from the remote config.
public struct RemoteAppConfig: Equatable, Sendable {
    public let ios: PlatformAppConfig?
    /// Defaults when the file has no `openAlex` section.
    public let openAlex: OpenAlexLimits

    public init(ios: PlatformAppConfig?, openAlex: OpenAlexLimits) {
        self.ios = ios
        self.openAlex = openAlex
    }
}
```

Change the protocol to:

```swift
public protocol AppConfigService: Sendable {
    /// The iOS entry and the OpenAlex limits. Throws `NetworkFailure` when the file can't be fetched or read.
    func fetch() async throws -> RemoteAppConfig
}
```

Rename `iosConfig()` to `fetch()` and replace its final `do { … }` with:

```swift
        do {
            let ios = try JSONDecoder().decode(File.self, from: data).ios
            let object = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            return RemoteAppConfig(ios: ios, openAlex: OpenAlexLimits.parse(object?["openAlex"]))
        } catch {
            throw NetworkFailure.malformedResponse
        }
```

- [ ] **Step 5: Save the limits in the update check**

In `AppUpdateRepository.swift`, replace `ConfigAppUpdateRepository` with:

```swift
/// Never blocks by mistake: offline, an error, or a missing or malformed field all mean "no update required".
/// The same request refreshes the OpenAlex limits; a failed fetch keeps the stored ones.
public struct ConfigAppUpdateRepository: AppUpdateRepository {
    private let service: any AppConfigService
    private let limitsStore: OpenAlexLimitsStore?

    public init(service: any AppConfigService, limitsStore: OpenAlexLimitsStore? = nil) {
        self.service = service
        self.limitsStore = limitsStore
    }

    /// The real one, reading GitHub Pages and keeping the limits in the standard defaults.
    public static func live() -> ConfigAppUpdateRepository {
        ConfigAppUpdateRepository(service: AppConfigClient(), limitsStore: OpenAlexLimitsStore())
    }

    public func requiredUpdate(currentBuild: Int) async -> RequiredUpdate? {
        guard let file = try? await service.fetch() else { return nil }
        limitsStore?.save(file.openAlex)
        guard let config = file.ios,
              let minimum = config.minimumBuild,
              let link = config.storeUrl, let storeURL = URL(string: link), storeURL.scheme == "https", storeURL.host()?.isEmpty == false,
              currentBuild < minimum else { return nil }
        return RequiredUpdate(storeURL: storeURL)
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run `-only-testing:HashiyaNetworkTests` and `-only-testing:HashiyaDataTests`.
Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaNetwork/AppConfigClient.swift ios/HashiyaKit/Sources/HashiyaData/AppUpdateRepository.swift ios/HashiyaKit/Tests/HashiyaNetworkTests/AppConfigClientTests.swift ios/HashiyaKit/Tests/HashiyaDataTests/AppUpdateTests.swift
git commit -m "feat(ios): keep the OpenAlex limits fetched with the update check"
```

---

### Task 3: `OpenAlexQuota` — routes, the device cap and used-up budgets

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaNetwork/OpenAlexQuota.swift`
- Test: `ios/HashiyaKit/Tests/HashiyaNetworkTests/OpenAlexQuotaTests.swift`

**Interfaces:**
- Consumes: `OpenAlexLimits`, `OpenAlexLimitsStore` (Task 1); `TestDefaults`, `TestClock` (Task 1).
- Produces: `enum OpenAlexRoute { case shared, keyless }`; `OpenAlexQuota(defaults: UserDefaults = .standard, hasBuiltInKey: Bool, now: @escaping @Sendable () -> Date = { Date() })` with `var limits: OpenAlexLimits`, `func meteredRoute() -> OpenAlexRoute?`, `func lookupRoute() -> OpenAlexRoute`, `func route(after: OpenAlexRoute, metered: Bool) -> OpenAlexRoute?`, `func recordSharedCall()`, `func markUsedUp(_: OpenAlexRoute, resetIn: TimeInterval?)`, `func nextAvailable() -> Date`.

- [ ] **Step 1: Write the failing tests**

`ios/HashiyaKit/Tests/HashiyaNetworkTests/OpenAlexQuotaTests.swift`:

```swift
import Foundation
import HashiyaNetwork
import HashiyaTesting
import Testing

struct OpenAlexQuotaTests {
    private let defaults = TestDefaults.make()
    private let clock = TestClock("2026-10-05T20:00:00Z")

    private func quota(builtInKey: Bool = true, limits: OpenAlexLimits = .defaults) -> OpenAlexQuota {
        OpenAlexLimitsStore(defaults: defaults).save(limits)
        return OpenAlexQuota(defaults: defaults, hasBuiltInKey: builtInKey, now: { [clock] in clock.date })
    }

    private func date(_ iso: String) -> Date { ISO8601DateFormatter().date(from: iso)! }

    @Test func startsOnTheSharedRoute() {
        let quota = quota()
        #expect(quota.meteredRoute() == .shared)
        #expect(quota.lookupRoute() == .shared)
    }

    @Test func withoutABuiltInKeyOrProxyEverythingIsKeyless() {
        let quota = quota(builtInKey: false)
        #expect(quota.meteredRoute() == .keyless)
        #expect(quota.lookupRoute() == .keyless)
    }

    @Test func aProxyMakesTheSharedRouteWithoutABuiltInKey() {
        let quota = quota(builtInKey: false, limits: OpenAlexLimits(dailyDeviceCalls: 60, maxPagesPerQuery: 8, baseURL: URL(string: "https://proxy.example")))
        #expect(quota.meteredRoute() == .shared)
    }

    @Test func theCapMovesMeteredCallsToKeylessButNotLookups() {
        let quota = quota(limits: OpenAlexLimits(dailyDeviceCalls: 2, maxPagesPerQuery: 8, baseURL: nil))
        quota.recordSharedCall()
        #expect(quota.meteredRoute() == .shared)
        quota.recordSharedCall()
        #expect(quota.meteredRoute() == .keyless)
        #expect(quota.lookupRoute() == .shared)
    }

    @Test func aZeroCapTurnsTheSharedRouteOffForMeteredCalls() {
        let quota = quota(limits: OpenAlexLimits(dailyDeviceCalls: 0, maxPagesPerQuery: 8, baseURL: nil))
        #expect(quota.meteredRoute() == .keyless)
        #expect(quota.lookupRoute() == .shared)
    }

    @Test func theCountStartsAgainAtUTCMidnightNotLocalMidnight() {
        let quota = quota(limits: OpenAlexLimits(dailyDeviceCalls: 1, maxPagesPerQuery: 8, baseURL: nil))
        quota.recordSharedCall()
        // 00:30 in UTC+3 is still the same UTC day.
        clock.set(date("2026-10-05T21:30:00Z"))
        #expect(quota.meteredRoute() == .keyless)
        clock.set(date("2026-10-06T00:00:01Z"))
        #expect(quota.meteredRoute() == .shared)
    }

    @Test func aUsedUpSharedBudgetSendsEverythingKeylessUntilItsReset() {
        let quota = quota()
        quota.markUsedUp(.shared, resetIn: 600)
        #expect(quota.meteredRoute() == .keyless)
        #expect(quota.lookupRoute() == .keyless)
        clock.advance(by: 601)
        #expect(quota.meteredRoute() == .shared)
    }

    @Test func bothBudgetsUsedUpMeansOutUntilTheEarliestReset() {
        let quota = quota()
        quota.markUsedUp(.shared, resetIn: 3600)
        quota.markUsedUp(.keyless, resetIn: 1800)
        #expect(quota.meteredRoute() == nil)
        #expect(quota.lookupRoute() == .keyless)
        #expect(quota.nextAvailable() == clock.date.addingTimeInterval(1800))
    }

    @Test func theCapCountsAsUsedUpUntilUTCMidnightForNextAvailable() {
        let quota = quota(limits: OpenAlexLimits(dailyDeviceCalls: 1, maxPagesPerQuery: 8, baseURL: nil))
        quota.recordSharedCall()
        quota.markUsedUp(.keyless, resetIn: 6 * 3600)
        #expect(quota.meteredRoute() == nil)
        #expect(quota.nextAvailable() == date("2026-10-06T00:00:00Z"))
    }

    @Test(arguments: [nil, -5, 3 * 86_400] as [TimeInterval?])
    func aMissingOrUnbelievableResetMeansNextUTCMidnight(reset: TimeInterval?) {
        let quota = quota()
        quota.markUsedUp(.shared, resetIn: reset)
        quota.markUsedUp(.keyless, resetIn: reset)
        #expect(quota.nextAvailable() == date("2026-10-06T00:00:00Z"))
    }

    @Test func marksAndCountsSurviveANewInstance() {
        let first = quota(limits: OpenAlexLimits(dailyDeviceCalls: 1, maxPagesPerQuery: 8, baseURL: nil))
        first.recordSharedCall()
        first.markUsedUp(.keyless, resetIn: 600)
        let second = OpenAlexQuota(defaults: defaults, hasBuiltInKey: true, now: { [clock] in clock.date })
        #expect(second.meteredRoute() == nil)
    }

    @Test func theRouteAfterSharedIsKeylessAndAfterKeylessNothing() {
        let quota = quota()
        #expect(quota.route(after: .shared, metered: true) == .keyless)
        #expect(quota.route(after: .keyless, metered: true) == nil)
        quota.markUsedUp(.keyless, resetIn: 600)
        #expect(quota.route(after: .shared, metered: true) == nil)
        #expect(quota.route(after: .shared, metered: false) == .keyless)
        #expect(quota.route(after: .keyless, metered: false) == nil)
    }

    @Test func newLimitsApplyToTheNextRoute() {
        let quota = quota()
        quota.recordSharedCall()
        OpenAlexLimitsStore(defaults: defaults).save(OpenAlexLimits(dailyDeviceCalls: 1, maxPagesPerQuery: 8, baseURL: nil))
        #expect(quota.meteredRoute() == .keyless)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run `-only-testing:HashiyaNetworkTests/OpenAlexQuotaTests`.
Expected: build failure — `cannot find 'OpenAlexQuota' in scope`.

- [ ] **Step 3: Implement the quota**

`ios/HashiyaKit/Sources/HashiyaNetwork/OpenAlexQuota.swift`:

```swift
import Foundation
import os

/// How a request without the user's key goes out: the shared route (the built-in key, or the proxy in the limits) or
/// no key at all.
public enum OpenAlexRoute: Equatable, Sendable {
    case shared, keyless
}

/// Chooses the route for requests without a user key and remembers, in `defaults`: the metered calls sent on the
/// shared route today (by UTC day, as OpenAlex resets at midnight UTC), and until when OpenAlex said the shared and
/// keyless budgets are used up. Nothing here leaves the device. `UserDefaults` is thread-safe; the lock keeps each
/// read-and-update whole.
public final class OpenAlexQuota: @unchecked Sendable {
    enum Key {
        static let callsDay = "openAlex.sharedCallsDay"
        static let calls = "openAlex.sharedCalls"
        static let sharedUntil = "openAlex.sharedUsedUpUntil"
        static let keylessUntil = "openAlex.keylessUsedUpUntil"
    }

    private static let secondsPerDay: TimeInterval = 86_400

    private let defaults: UserDefaults
    private let limitsStore: OpenAlexLimitsStore
    private let hasBuiltInKey: Bool
    private let now: @Sendable () -> Date
    private let lock = OSAllocatedUnfairLock()

    public init(defaults: UserDefaults = .standard, hasBuiltInKey: Bool, now: @escaping @Sendable () -> Date = { Date() }) {
        self.defaults = defaults
        limitsStore = OpenAlexLimitsStore(defaults: defaults)
        self.hasBuiltInKey = hasBuiltInKey
        self.now = now
    }

    /// Read on every request, so limits fetched later apply to the next one.
    public var limits: OpenAlexLimits { limitsStore.limits }

    /// The route for a search or filter list: shared while under the cap and not used up, else keyless while that
    /// isn't used up, else nil (out).
    public func meteredRoute() -> OpenAlexRoute? {
        lock.withLock {
            let date = now()
            if sharedOpensForMetered(at: date) <= date { return .shared }
            return keylessUntil <= date ? .keyless : nil
        }
    }

    /// The route for a free lookup: shared unless it is used up (the cap counts metered calls only), else keyless.
    public func lookupRoute() -> OpenAlexRoute {
        lock.withLock { sharedConfigured && sharedUntil <= now() ? .shared : .keyless }
    }

    /// Where a request goes after `route` was used up or failed: keyless after shared (for metered calls only while
    /// keyless isn't used up), nothing after keyless.
    public func route(after route: OpenAlexRoute, metered: Bool) -> OpenAlexRoute? {
        guard route == .shared else { return nil }
        guard metered else { return .keyless }
        return lock.withLock { keylessUntil <= now() } ? .keyless : nil
    }

    /// One metered call sent on the shared route.
    public func recordSharedCall() {
        lock.withLock {
            let today = Self.utcDay(now())
            let calls = defaults.integer(forKey: Key.callsDay) == today ? defaults.integer(forKey: Key.calls) : 0
            defaults.set(today, forKey: Key.callsDay)
            defaults.set(calls + 1, forKey: Key.calls)
        }
    }

    /// OpenAlex said `route`'s budget is used up. `resetIn` is its `X-RateLimit-Reset`; nil, negative or more than
    /// two days means the next midnight UTC.
    public func markUsedUp(_ route: OpenAlexRoute, resetIn: TimeInterval?) {
        lock.withLock {
            let date = now()
            let until = resetIn.flatMap { (0...(2 * Self.secondsPerDay)).contains($0) ? date.addingTimeInterval($0) : nil }
                ?? Self.nextUTCMidnight(after: date)
            defaults.set(until.timeIntervalSince1970, forKey: route == .shared ? Key.sharedUntil : Key.keylessUntil)
        }
    }

    /// When a search can go out again: the earlier of the shared and keyless routes opening.
    public func nextAvailable() -> Date {
        lock.withLock {
            let date = now()
            let opens = [sharedOpensForMetered(at: date), keylessUntil].min() ?? date
            return max(opens, date)
        }
    }

    // MARK: Inside the lock

    private var sharedConfigured: Bool { hasBuiltInKey || limits.baseURL != nil }

    /// `.distantFuture` when the shared route is off for metered calls.
    private func sharedOpensForMetered(at date: Date) -> Date {
        let cap = limits.dailyDeviceCalls
        guard sharedConfigured, cap > 0 else { return .distantFuture }
        let today = Self.utcDay(date)
        let calls = defaults.integer(forKey: Key.callsDay) == today ? defaults.integer(forKey: Key.calls) : 0
        let capOpens = calls >= cap ? Self.nextUTCMidnight(after: date) : date
        return max(capOpens, sharedUntil)
    }

    private var sharedUntil: Date { Date(timeIntervalSince1970: defaults.double(forKey: Key.sharedUntil)) }
    private var keylessUntil: Date { Date(timeIntervalSince1970: defaults.double(forKey: Key.keylessUntil)) }

    private static func utcDay(_ date: Date) -> Int {
        Int((date.timeIntervalSince1970 / secondsPerDay).rounded(.down))
    }

    private static func nextUTCMidnight(after date: Date) -> Date {
        Date(timeIntervalSince1970: TimeInterval(utcDay(date) + 1) * secondsPerDay)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run `-only-testing:HashiyaNetworkTests/OpenAlexQuotaTests`.
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaNetwork/OpenAlexQuota.swift ios/HashiyaKit/Tests/HashiyaNetworkTests/OpenAlexQuotaTests.swift
git commit -m "feat(ios): choose the OpenAlex route under a per-device daily cap"
```

---

### Task 4: `SearchCache`

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaNetwork/SearchCache.swift`
- Test: `ios/HashiyaKit/Tests/HashiyaNetworkTests/SearchCacheTests.swift`

**Interfaces:**
- Consumes: `TestClock` (Task 1).
- Produces: `SearchCache(directory: URL, maxBytes: Int = 5_000_000, lifetime: TimeInterval = 86_400, now: @escaping @Sendable () -> Date = { Date() })`, `static func live() -> SearchCache?`, `static func key(path: String, query: [(name: String, value: String)]) -> String`, `func data(for key: String) -> Data?`, `func store(_ data: Data, for key: String)`.

- [ ] **Step 1: Write the failing tests**

`ios/HashiyaKit/Tests/HashiyaNetworkTests/SearchCacheTests.swift`:

```swift
import Foundation
import HashiyaNetwork
import HashiyaTesting
import Testing

struct SearchCacheTests {
    private let directory = FileManager.default.temporaryDirectory.appending(path: "SearchCacheTests-\(UUID().uuidString)")
    private let clock = TestClock("2026-10-05T10:00:00Z")

    private func cache(maxBytes: Int = 5_000_000) -> SearchCache {
        SearchCache(directory: directory, maxBytes: maxBytes, now: { [clock] in clock.date })
    }

    @Test func returnsWhatWasStored() {
        let cache = cache()
        cache.store(Data("page".utf8), for: "a")
        #expect(cache.data(for: "a") == Data("page".utf8))
        #expect(cache.data(for: "b") == nil)
    }

    @Test func entriesExpireAfter24Hours() {
        let cache = cache()
        cache.store(Data("page".utf8), for: "a")
        clock.advance(by: 86_399)
        #expect(cache.data(for: "a") != nil)
        clock.advance(by: 2)
        #expect(cache.data(for: "a") == nil)
    }

    @Test func removesTheLeastRecentlyUsedOverTheSizeLimit() {
        let cache = cache(maxBytes: 250)
        let body = Data(repeating: 1, count: 100)
        cache.store(body, for: "old")
        clock.advance(by: 10)
        cache.store(body, for: "used")
        clock.advance(by: 10)
        _ = cache.data(for: "old")  // now the most recently used
        clock.advance(by: 10)
        cache.store(body, for: "new")
        #expect(cache.data(for: "old") != nil)
        #expect(cache.data(for: "used") == nil)
        #expect(cache.data(for: "new") != nil)
    }

    @Test func survivesANewInstance() {
        cache().store(Data("page".utf8), for: "a")
        #expect(cache().data(for: "a") == Data("page".utf8))
    }

    @Test func theKeyIgnoresTheAPIKeyAndParameterOrder() {
        let a = SearchCache.key(path: "/works", query: [("search", "bert"), ("cursor", "*"), ("api_key", "one")])
        let b = SearchCache.key(path: "/works", query: [("api_key", "two"), ("cursor", "*"), ("search", "bert")])
        let c = SearchCache.key(path: "/works", query: [("search", "bert"), ("cursor", "*")])
        #expect(a == b)
        #expect(a == c)
        #expect(!a.contains("one"))
    }

    @Test func theKeyDiffersForCursorFilterAndPath() {
        let base = SearchCache.key(path: "/works", query: [("search", "bert"), ("cursor", "*")])
        #expect(base != SearchCache.key(path: "/works", query: [("search", "bert"), ("cursor", "abc")]))
        #expect(base != SearchCache.key(path: "/works", query: [("search", "bert"), ("cursor", "*"), ("filter", "is_oa:true")]))
        #expect(base != SearchCache.key(path: "/authors", query: [("search", "bert"), ("cursor", "*")]))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run `-only-testing:HashiyaNetworkTests/SearchCacheTests`.
Expected: build failure — `cannot find 'SearchCache' in scope`.

- [ ] **Step 3: Implement the cache**

`ios/HashiyaKit/Sources/HashiyaNetwork/SearchCache.swift`:

```swift
import CryptoKit
import Foundation
import os

/// Search and filter-list responses on disk: kept 24 hours, at most 5 MB, least recently used removed first. Each file
/// is named after a hash of the request without its key and starts with the time it was saved; its modification date
/// is when it was last used. Lives in Caches, so the system may clear it and it is never backed up.
public final class SearchCache: @unchecked Sendable {
    private let directory: URL
    private let maxBytes: Int
    private let lifetime: TimeInterval
    private let now: @Sendable () -> Date
    private let lock = OSAllocatedUnfairLock()
    private let files = FileManager.default

    public init(
        directory: URL,
        maxBytes: Int = 5_000_000,
        lifetime: TimeInterval = 24 * 60 * 60,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.directory = directory
        self.maxBytes = maxBytes
        self.lifetime = lifetime
        self.now = now
    }

    /// `Caches/OpenAlexSearch`.
    public static func live() -> SearchCache? {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
            .map { SearchCache(directory: $0.appending(path: "OpenAlexSearch", directoryHint: .isDirectory)) }
    }

    /// The request without `api_key`, parameters sorted, so the same search hits whichever route sent it.
    public static func key(path: String, query: [(name: String, value: String)]) -> String {
        let parameters = query
            .filter { $0.name != "api_key" }
            .sorted { ($0.name, $0.value) < ($1.name, $1.value) }
            .map { "\($0.name)=\($0.value)" }
            .joined(separator: "&")
        return path + "?" + parameters
    }

    /// The stored body, if it is younger than the lifetime; marks it used.
    public func data(for key: String) -> Data? {
        lock.withLock {
            let file = fileURL(key)
            guard let contents = try? Data(contentsOf: file), contents.count >= 8 else { return nil }
            let savedAt = contents.prefix(8).withUnsafeBytes { $0.loadUnaligned(as: Double.self) }
            let date = now()
            let age = date.timeIntervalSince1970 - savedAt
            guard age >= 0, age < lifetime else {
                try? files.removeItem(at: file)
                return nil
            }
            try? files.setAttributes([.modificationDate: date], ofItemAtPath: file.path)
            return Data(contents.dropFirst(8))
        }
    }

    public func store(_ data: Data, for key: String) {
        lock.withLock {
            try? files.createDirectory(at: directory, withIntermediateDirectories: true)
            let date = now()
            var savedAt = date.timeIntervalSince1970
            let header = withUnsafeBytes(of: &savedAt) { Data($0) }
            let file = fileURL(key)
            guard (try? (header + data).write(to: file, options: .atomic)) != nil else { return }
            try? files.setAttributes([.modificationDate: date], ofItemAtPath: file.path)
            trim()
        }
    }

    /// Inside the lock: removes the least recently used files until the total fits.
    private func trim() {
        let keys: [URLResourceKey] = [.fileSizeKey, .contentModificationDateKey]
        guard let urls = try? files.contentsOfDirectory(at: directory, includingPropertiesForKeys: keys) else { return }
        var entries = urls.map { url -> (url: URL, size: Int, used: Date) in
            let values = try? url.resourceValues(forKeys: Set(keys))
            return (url, values?.fileSize ?? 0, values?.contentModificationDate ?? .distantPast)
        }
        var total = entries.reduce(0) { $0 + $1.size }
        entries.sort { $0.used < $1.used }
        for entry in entries where total > maxBytes {
            try? files.removeItem(at: entry.url)
            total -= entry.size
        }
    }

    private func fileURL(_ key: String) -> URL {
        let hash = SHA256.hash(data: Data(key.utf8)).map { String(format: "%02x", $0) }.joined()
        return directory.appending(path: hash)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run `-only-testing:HashiyaNetworkTests/SearchCacheTests`.
Expected: all pass. (The LRU test's three files are 108 bytes each — 8-byte header + 100 — so the third pushes the total to 324 > 250 and the least recently used one, "used", goes.)

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaNetwork/SearchCache.swift ios/HashiyaKit/Tests/HashiyaNetworkTests/SearchCacheTests.swift
git commit -m "feat(ios): keep OpenAlex search pages on disk for a day"
```

---

### Task 5: The "Daily search limit reached" error

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaNetwork/NetworkFailure.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaModel/SearchError.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaData/ErrorMapping.swift`
- Modify: `ios/HashiyaKit/Sources/FeatureSearch/L10n.swift`
- Modify: `ios/HashiyaKit/Sources/FeatureSearch/LookupBody.swift` (`SearchErrorView`)
- Modify: `ios/HashiyaKit/Sources/FeatureSearch/Resources/Localizable.xcstrings`
- Modify: `ios/HashiyaKit/Tests/HashiyaModelTests/SearchErrorTests.swift`
- Modify: `ios/HashiyaKit/Tests/HashiyaDataTests/ErrorMappingTests.swift`
- Modify: `ios/HashiyaKit/Tests/FeatureSearchTests/SearchStringsTests.swift`
- Modify: `ios/HashiyaSnapshotTests/SearchSnapshotTests.swift`

**Interfaces:**
- Produces: `NetworkFailure.dailyLimit(resetAt: Date)`; `SearchError.dailyLimit(resetAt: Date)`; `L10n.error(_:timeZone:)`; `L10n.dailyLimitMessage(_ resetAt: Date, timeZone: TimeZone) -> String`.

- [ ] **Step 1: Write the failing tests**

`SearchErrorTests.swift`:

```swift
import Foundation
import HashiyaModel
import Testing

struct SearchErrorTests {
    @Test func casesAreDistinct() {
        let all: [SearchError] = [.offline, .invalidUserKey, .rateLimited, .serviceUnavailable, .unexpected, .dailyLimit(resetAt: .distantPast)]
        #expect(Set(all.map { "\($0)" }).count == 6)
    }
}
```

In `ErrorMappingTests.swift`, add:

```swift
    @Test func aDailyLimitKeepsItsResetTime() {
        let reset = Date(timeIntervalSince1970: 1_790_000_000)
        #expect(NetworkFailure.dailyLimit(resetAt: reset).asSearchError() == .dailyLimit(resetAt: reset))
    }
```

In `SearchStringsTests.swift`, add `.dailyLimit(resetAt: .distantPast)` to the `everyErrorHasATitleAndMessageInBothLanguages` arguments (its body is unchanged: `timeZone` has a default), and add:

```swift
    @Test func theDailyLimitMessageShowsTheResetTimeInTheGivenZone() {
        let reset = ISO8601DateFormatter().date(from: "2026-10-06T00:00:00Z")!
        let riyadh = TimeZone(identifier: "Asia/Riyadh")!
        #expect(inLanguage("en") { L10n.dailyLimitMessage(reset, timeZone: riyadh) }.contains("3:00\u{202F}AM"))
        #expect(inLanguage("en") { L10n.error(.dailyLimit(resetAt: reset), timeZone: riyadh).title } == "Daily search limit reached")
    }

    @Test func theArabicDailyLimitMessageIsolatesTheTimeOnce() {
        let reset = ISO8601DateFormatter().date(from: "2026-10-06T00:00:00Z")!
        let message = inLanguage("ar") { L10n.dailyLimitMessage(reset, timeZone: .gmt) }
        #expect(message.components(separatedBy: "\u{2068}").count == 2)
        #expect(message.contains("\u{2069}"))
        #expect(message.contains("OpenAlex"))
    }
```

(`.shortened` time in English uses a narrow no-break space, U+202F, before AM/PM. If the simulator's formatter gives a plain space, assert `.contains("3:00")` and `.contains("AM")` separately instead — record that change in your report.)

- [ ] **Step 2: Run them to verify they fail**

Run `-only-testing:HashiyaModelTests`.
Expected: build failure — `type 'SearchError' has no member 'dailyLimit'`.

- [ ] **Step 3: Add the cases and the mapping**

`SearchError.swift`:

```swift
import Foundation

/// Why a search failed, as the UI tells the user.
public enum SearchError: Error, Equatable, Sendable {
    case offline
    case invalidUserKey
    case rateLimited
    case serviceUnavailable
    case unexpected
    /// The shared and keyless budgets are used up for today; searching works again at `resetAt`.
    case dailyLimit(resetAt: Date)
}
```

`NetworkFailure.swift` — add `import Foundation` at the top, the case after `unknown`, and its description:

```swift
    /// Every route for a search is used up until `resetAt`; no request was sent.
    case dailyLimit(resetAt: Date)
```

```swift
        case .dailyLimit: "dailyLimit"
```

`ErrorMapping.swift` — add before the `case .http, .malformedResponse, .unknown:` line:

```swift
        case let .dailyLimit(resetAt):
            .dailyLimit(resetAt: resetAt)
```

- [ ] **Step 4: Add the strings**

In `FeatureSearch/Resources/Localizable.xcstrings`, add these keys inside `"strings"` (same shape as the existing `search.errorRateTitle`):

```json
    "search.errorDailyLimitTitle" : {
      "extractionState" : "manual",
      "localizations" : {
        "ar" : { "stringUnit" : { "state" : "translated", "value" : "تم بلوغ الحد اليومي للبحث" } },
        "en" : { "stringUnit" : { "state" : "translated", "value" : "Daily search limit reached" } }
      }
    },
    "search.errorDailyLimitMessage" : {
      "extractionState" : "manual",
      "localizations" : {
        "ar" : { "stringUnit" : { "state" : "translated", "value" : "سيتوفر البحث مجددًا الساعة %@. لمزيد من عمليات البحث، أضف مفتاح OpenAlex مجانيًا خاصًا بك في الإعدادات." } },
        "en" : { "stringUnit" : { "state" : "translated", "value" : "Search will be available again at %@. For more searches, add your own free OpenAlex key in Settings." } }
      }
    },
```

- [ ] **Step 5: Format the message and show Open Settings**

In `FeatureSearch/L10n.swift`, replace `error(_:)` with:

```swift
    /// Title and message of a first-page error. `timeZone` places a daily limit's reset time.
    static func error(_ error: SearchError, timeZone: TimeZone = .current) -> (title: String, message: String) {
        switch error {
        case .offline: (string("search.errorOfflineTitle"), string("search.errorOfflineMessage"))
        case .invalidUserKey: (string("search.errorKeyTitle"), string("search.errorKeyMessage"))
        case .serviceUnavailable: (string("search.errorUnavailableTitle"), string("search.errorUnavailableMessage"))
        case .rateLimited: (string("search.errorRateTitle"), string("search.errorRateMessage"))
        case .unexpected: (string("search.errorUnexpectedTitle"), string("search.errorUnexpectedMessage"))
        case let .dailyLimit(resetAt): (string("search.errorDailyLimitTitle"), dailyLimitMessage(resetAt, timeZone: timeZone))
        }
    }

    /// "Search will be available again at 3:00 AM. …", the time in the app's locale and `timeZone`, isolated
    /// (U+2068 … U+2069) exactly once so it keeps its order inside Arabic text.
    static func dailyLimitMessage(_ resetAt: Date, timeZone: TimeZone) -> String {
        var style = Date.FormatStyle(date: .omitted, time: .shortened).locale(HashiyaLanguage.locale)
        style.timeZone = timeZone
        let time = resetAt.formatted(style)
        let formatted = format("search.errorDailyLimitMessage", time)
        let isolated = "\u{2068}" + time + "\u{2069}"
        return formatted.contains(isolated) ? formatted : format("search.errorDailyLimitMessage", isolated)
    }
```

In `LookupBody.swift`, replace `SearchErrorView` with:

```swift
/// Spec 1's error state: Retry, or Open Settings for a rejected user key or a reached daily limit when
/// `onOpenSettings` is given.
struct SearchErrorView: View {
    let error: SearchError
    let onRetry: () -> Void
    let onOpenSettings: (() -> Void)?

    @Environment(\.timeZone) private var timeZone

    var body: some View {
        let text = L10n.error(error, timeZone: timeZone)
        if error.opensSettings, let onOpenSettings {
            ErrorStateView(title: text.title, message: text.message, actionTitle: L10n.string("search.openSettings"), action: onOpenSettings)
        } else {
            ErrorStateView(title: text.title, message: text.message, actionTitle: L10n.string("search.retry"), action: onRetry)
        }
    }
}

extension SearchError {
    /// A personal key in Settings is the way out of these.
    fileprivate var opensSettings: Bool {
        switch self {
        case .invalidUserKey, .dailyLimit: true
        case .offline, .rateLimited, .serviceUnavailable, .unexpected: false
        }
    }
}
```

- [ ] **Step 6: Add the snapshot test**

In `ios/HashiyaSnapshotTests/SearchSnapshotTests.swift`, add after `offlineError()`:

```swift
    @Test func dailyLimitError() async {
        let reset = ISO8601DateFormatter().date(from: "2026-10-06T00:00:00Z")!
        let viewModel = makeViewModel(FakeSearchRepository { _, _ in throw SearchError.dailyLimit(resetAt: reset) })
        viewModel.updateText("transformers")
        viewModel.submitNow()
        await viewModel.waitForPendingWork()
        #expect(viewModel.phase == .failed(.dailyLimit(resetAt: reset)))
        assertHashiyaSnapshots(
            of: screen(viewModel).environment(\.timeZone, TimeZone(identifier: "Asia/Riyadh")!),
            named: "dailyLimit",
            arabicText: "تم بلوغ الحد اليومي للبحث"
        )
    }
```

(Its baselines are recorded on CI in Task 9.)

- [ ] **Step 7: Run the tests to verify they pass**

Run `-only-testing:HashiyaModelTests`, `-only-testing:HashiyaDataTests`, `-only-testing:FeatureSearchTests`, then `python3 ios/scripts/check-translations.py`.
Expected: all pass; the script prints nothing and exits 0.

- [ ] **Step 8: Commit**

```bash
git add ios/HashiyaKit ios/HashiyaSnapshotTests/SearchSnapshotTests.swift
git commit -m "feat(ios): explain a reached daily search limit and when it resets"
```

---

### Task 6: Route every OpenAlex request

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaNetwork/OpenAlexHTTP.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaNetwork/OpenAlexSearchClient.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaNetwork/OpenAlexLookupClient.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaData/LiveDependencies.swift`
- Test: `ios/HashiyaKit/Tests/HashiyaNetworkTests/OpenAlexRoutingTests.swift`

**Interfaces:**
- Consumes: `OpenAlexQuota`, `OpenAlexRoute` (Task 3); `SearchCache` (Task 4); `NetworkFailure.dailyLimit(resetAt:)` (Task 5); `OpenAlexLimitsStore`, `TestDefaults`, `TestClock`, `URLProtocolStub.Reply.status(_:body:headers:)` (Task 1); `ManualSleeper` (existing, `HashiyaTesting`).
- Produces: both clients' `init` gain `quota: OpenAlexQuota? = nil, cache: SearchCache? = nil, sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) }` (after `userKeySource`, before `baseURL`); `LiveDependencies.live` wires them. With `quota` nil a client behaves exactly as before (existing client tests stay green).

- [ ] **Step 1: Write the failing tests**

`ios/HashiyaKit/Tests/HashiyaNetworkTests/OpenAlexRoutingTests.swift`:

```swift
import Foundation
import HashiyaNetwork
import HashiyaTesting
import Testing

struct OpenAlexRoutingTests {
    private let page = Fixtures.string("works_page.json")
    private let work = Fixtures.string("work.json")
    private let defaults = TestDefaults.make()
    private let clock = TestClock("2026-10-05T20:00:00Z")
    private let sleeper = ManualSleeper()
    private let cacheDirectory = FileManager.default.temporaryDirectory.appending(path: "RoutingTests-\(UUID().uuidString)")

    private func quota(limits: OpenAlexLimits = .defaults, builtInKey: Bool = true) -> OpenAlexQuota {
        OpenAlexLimitsStore(defaults: defaults).save(limits)
        return OpenAlexQuota(defaults: defaults, hasBuiltInKey: builtInKey, now: { [clock] in clock.date })
    }

    private func search(_ server: URLProtocolStub.Server, _ quota: OpenAlexQuota, userKey: String? = nil, cache: Bool = false) -> OpenAlexSearchClient {
        OpenAlexSearchClient(
            session: server.session, builtInKey: "built-in", userKeySource: FixedUserAPIKeySource(userKey),
            quota: quota, cache: cache ? SearchCache(directory: cacheDirectory, now: { [clock] in clock.date }) : nil,
            sleep: { [sleeper] in try await sleeper.sleep($0) }, log: { _ in }
        )
    }

    private func lookup(_ server: URLProtocolStub.Server, _ quota: OpenAlexQuota) -> OpenAlexLookupClient {
        OpenAlexLookupClient(
            session: server.session, builtInKey: "built-in", userKeySource: FixedUserAPIKeySource(nil),
            quota: quota, sleep: { [sleeper] in try await sleeper.sleep($0) }, log: { _ in }
        )
    }

    private let request = WorksSearchRequest(search: "bert", filter: nil, sort: nil, cursor: "*", perPage: 25)

    private static func query(_ request: URLRequest) -> [String: String] {
        let items = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems ?? []
        return Dictionary(items.map { ($0.name, $0.value ?? "") }, uniquingKeysWith: { _, last in last })
    }

    private static func usedUp(reset: String = "600") -> URLProtocolStub.Reply {
        .status(429, headers: ["X-RateLimit-Remaining": "0", "X-RateLimit-Reset": reset])
    }

    @Test func aSharedSearchSendsTheBuiltInKeyAndCounts() async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        let quota = quota(limits: OpenAlexLimits(dailyDeviceCalls: 1, maxPagesPerQuery: 8, baseURL: nil))
        _ = try await search(server, quota).searchWorks(request)
        #expect(server.lastQuery["api_key"] == "built-in")
        #expect(quota.meteredRoute() == .keyless)
    }

    @Test func aUsedUpSharedBudgetRetriesOnceWithoutAKey() async throws {
        let server = URLProtocolStub.Server { request in
            Self.query(request)["api_key"] == nil ? .json(page) : Self.usedUp()
        }
        let quota = quota()
        _ = try await search(server, quota).searchWorks(request)
        #expect(server.requests.count == 2)
        #expect(Self.query(server.requests[1])["api_key"] == nil)
        #expect(server.requests[1].url?.host() == "api.openalex.org")
        #expect(quota.meteredRoute() == .keyless)
    }

    @Test(arguments: ["0.0", "-1"])
    func remainingAtOrBelowZeroIsUsedUp(remaining: String) async throws {
        let server = URLProtocolStub.Server { request in
            Self.query(request)["api_key"] == nil ? .json(page) : .status(429, headers: ["X-RateLimit-Remaining": remaining])
        }
        _ = try await search(server, quota()).searchWorks(request)
        #expect(server.requests.count == 2)
    }

    @Test func bothBudgetsUsedUpGivesTheDailyLimitWithTheEarliestReset() async {
        let server = URLProtocolStub.Server { request in
            Self.query(request)["api_key"] == nil ? Self.usedUp(reset: "1800") : Self.usedUp(reset: "3600")
        }
        let quota = quota()
        await #expect(throws: NetworkFailure.dailyLimit(resetAt: clock.date.addingTimeInterval(1800))) {
            try await search(server, quota).searchWorks(request)
        }
        #expect(server.requests.count == 2)
    }

    @Test func whenOutASearchSendsNothing() async {
        let server = URLProtocolStub.Server(always: .json(page))
        let quota = quota()
        quota.markUsedUp(.shared, resetIn: 600)
        quota.markUsedUp(.keyless, resetIn: 900)
        await #expect(throws: NetworkFailure.dailyLimit(resetAt: clock.date.addingTimeInterval(600))) {
            try await search(server, quota).searchWorks(request)
        }
        #expect(server.requests.isEmpty)
    }

    @Test func aLookupStillGoesOutWhenSearchIsOutAndNeverCounts() async throws {
        let server = URLProtocolStub.Server(always: .json(work))
        let quota = quota(limits: OpenAlexLimits(dailyDeviceCalls: 1, maxPagesPerQuery: 8, baseURL: nil))
        _ = try await lookup(server, quota).work(id: "W1")
        #expect(quota.meteredRoute() == .shared)
        quota.markUsedUp(.shared, resetIn: 600)
        quota.markUsedUp(.keyless, resetIn: 600)
        _ = try await lookup(server, quota).work(id: "W1")
        #expect(server.requests.count == 2)
        #expect(Self.query(server.requests[1])["api_key"] == nil)
    }

    @Test func aFilterListIsMetered() async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        let quota = quota(limits: OpenAlexLimits(dailyDeviceCalls: 1, maxPagesPerQuery: 8, baseURL: nil))
        _ = try await lookup(server, quota).works(filter: "doi:10.1/x", perPage: 1)
        #expect(quota.meteredRoute() == .keyless)
    }

    @Test func aPerSecondLimitWaitsAndRetriesOnceOnTheSameRoute() async throws {
        let replies = Counter(0)
        let server = URLProtocolStub.Server { _ in replies.next() == 0 ? .status(429, headers: ["X-RateLimit-Remaining": "50"]) : .json(page) }
        let client = search(server, quota())
        let task = Task { try await client.searchWorks(request) }
        await sleeper.waitForSleeper()
        sleeper.advance(by: .seconds(1))
        _ = try await task.value
        #expect(server.requests.count == 2)
        #expect(Self.query(server.requests[1])["api_key"] == "built-in")
    }

    @Test func aSecondPerSecondLimitIsTheRateLimitedError() async {
        let server = URLProtocolStub.Server(always: .status(429, headers: ["X-RateLimit-Remaining": "abc", "Retry-After": "10"]))
        let client = search(server, quota())
        let task = Task { try await client.searchWorks(request) }
        await sleeper.waitForSleeper()
        sleeper.advance(by: .seconds(2))
        #expect(sleeper.pendingCount == 1)  // Retry-After 10 is capped at 3 seconds
        sleeper.advance(by: .seconds(1))
        await #expect(throws: NetworkFailure.http(code: 429, usedUserKey: false)) { try await task.value }
        #expect(server.requests.count == 2)
    }

    @Test func cancellingDuringTheWaitMarksNothing() async {
        let server = URLProtocolStub.Server(always: .status(429))
        let quota = quota()
        let client = search(server, quota)
        let task = Task { try await client.searchWorks(request) }
        await sleeper.waitForSleeper()
        task.cancel()
        await #expect(throws: CancellationError.self) { try await task.value }
        #expect(quota.meteredRoute() == .shared)
    }

    @Test func theUserKeyIsTheOnlyRoute() async {
        let server = URLProtocolStub.Server(always: Self.usedUp())
        let quota = quota()
        await #expect(throws: NetworkFailure.http(code: 429, usedUserKey: true)) {
            try await search(server, quota, userKey: "mine").searchWorks(request)
        }
        #expect(server.requests.count == 1)
        #expect(server.lastQuery["api_key"] == "mine")
        #expect(quota.meteredRoute() == .shared)
    }

    @Test func aProxyGetsSharedRequestsUnderItsPathWithNoKey() async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        let proxy = OpenAlexLimits(dailyDeviceCalls: 60, maxPagesPerQuery: 8, baseURL: URL(string: "https://proxy.example/openalex"))
        _ = try await search(server, quota(limits: proxy)).searchWorks(request)
        let url = try #require(server.requests.last?.url)
        #expect(url.host() == "proxy.example")
        #expect(url.path() == "/openalex/works")
        #expect(server.lastQuery["api_key"] == nil)
        #expect(server.lastQuery["search"] == "bert")
    }

    @Test(arguments: [URLProtocolStub.Reply.status(503), .failure(.cannotConnectToHost)])
    func aFailingProxyFallsBackToKeylessWithoutBeingMarked(failure: URLProtocolStub.Reply) async throws {
        let server = URLProtocolStub.Server { request in request.url?.host() == "proxy.example" ? failure : .json(page) }
        let quota = quota(limits: OpenAlexLimits(dailyDeviceCalls: 60, maxPagesPerQuery: 8, baseURL: URL(string: "https://proxy.example")))
        _ = try await search(server, quota).searchWorks(request)
        #expect(server.requests.last?.url?.host() == "api.openalex.org")
        #expect(server.lastQuery["api_key"] == nil)
        #expect(quota.meteredRoute() == .shared)
    }

    @Test func theUserKeyNeverGoesToTheProxy() async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        let proxy = OpenAlexLimits(dailyDeviceCalls: 60, maxPagesPerQuery: 8, baseURL: URL(string: "https://proxy.example"))
        _ = try await search(server, quota(limits: proxy), userKey: "mine").searchWorks(request)
        #expect(server.requests.last?.url?.host() == "api.openalex.org")
    }

    @Test func aRepeatedSearchIsAnsweredFromTheCacheWithoutCounting() async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        let quota = quota(limits: OpenAlexLimits(dailyDeviceCalls: 1, maxPagesPerQuery: 8, baseURL: nil))
        let client = search(server, quota, cache: true)
        _ = try await client.searchWorks(request)
        clock.advance(by: 60)
        _ = try await client.searchWorks(request)
        #expect(server.requests.count == 1)
        var next = request
        next.cursor = "abc"
        _ = try await client.searchWorks(next)
        #expect(server.requests.count == 2)
    }

    @Test func failuresAreNotCached() async throws {
        let replies = Counter(0)
        let server = URLProtocolStub.Server { _ in replies.next() == 0 ? .status(500) : .json(page) }
        let client = search(server, quota(), cache: true)
        await #expect(throws: NetworkFailure.http(code: 500, usedUserKey: false)) { try await client.searchWorks(request) }
        _ = try await client.searchWorks(request)
        #expect(server.requests.count == 2)
    }
}

/// Counts replies across the stub's threads.
private final class Counter: Sendable {
    private let lock: OSAllocatedUnfairLock<Int>
    init(_ value: Int) { lock = OSAllocatedUnfairLock(initialState: value) }
    /// The current count, then adds one.
    func next() -> Int { lock.withLock { value in defer { value += 1 }; return value } }
}
```

Add `import os` at the top of this file.

- [ ] **Step 2: Run them to verify they fail**

Run `-only-testing:HashiyaNetworkTests/OpenAlexRoutingTests`.
Expected: build failure — `extra arguments at positions … in call` (the clients don't take `quota` yet).

- [ ] **Step 3: Rewrite `OpenAlexHTTP`**

Replace `OpenAlexHTTP.swift` with:

```swift
import Foundation

/// Sends GET requests to OpenAlex for both clients. Without a user key, `quota` picks the route (shared: the built-in
/// key or the limits' proxy; else keyless) and learns from 429s when a budget is used up. `GET /works` (search and
/// filter lists) is metered and cached; `GET /works/{id}` is free and always goes out. Encodes the query, logs (Debug
/// only, key redacted) and classifies failures.
struct OpenAlexHTTP: Sendable {
    let session: URLSession
    let baseURL: URL
    let builtInKey: String?
    let userKeySource: any UserAPIKeySource
    /// Nil: the user's key or the built-in key, with no routing, cap or fallback.
    let quota: OpenAlexQuota?
    let cache: SearchCache?
    let sleep: @Sendable (Duration) async throws -> Void
    let log: @Sendable (String) -> Void

    /// The response body of a 2xx response. Throws `NetworkFailure` or `CancellationError`.
    func get(path: String, query: [(name: String, value: String)]) async throws -> Data {
        let metered = path == "/works"
        let cacheKey = metered ? SearchCache.key(path: path, query: query) : nil
        if let cacheKey, let cached = cache?.data(for: cacheKey) { return cached }
        let data = try await send(path: path, query: query, metered: metered)
        if let cacheKey { cache?.store(data, for: cacheKey) }
        return data
    }

    private func send(path: String, query: [(name: String, value: String)], metered: Bool) async throws -> Data {
        if let userKey = userKeySource.userKey.flatMap(nonBlank) {
            return try await perform(base: baseURL, key: userKey, path: path, query: query).body(usedUserKey: true)
        }
        guard let quota else {
            return try await perform(base: baseURL, key: builtInKey.flatMap(nonBlank), path: path, query: query).body(usedUserKey: false)
        }

        var route: OpenAlexRoute? = metered ? quota.meteredRoute() : quota.lookupRoute()
        var waitedOnThisRoute = false
        while let current = route {
            let proxy = current == .shared ? quota.limits.baseURL : nil
            let key = current == .shared && proxy == nil ? builtInKey.flatMap(nonBlank) : nil
            if metered, current == .shared { quota.recordSharedCall() }

            let reply: Reply
            do {
                reply = try await perform(base: proxy ?? baseURL, key: key, path: path, query: query)
            } catch NetworkFailure.connectivity where proxy != nil {
                route = quota.route(after: current, metered: metered)
                waitedOnThisRoute = false
                continue
            }

            switch reply.status {
            case 200...299:
                return reply.data
            case 429 where reply.budgetUsedUp:
                quota.markUsedUp(current, resetIn: reply.resetSeconds)
                route = quota.route(after: current, metered: metered)
                waitedOnThisRoute = false
            case 429 where !waitedOnThisRoute:
                waitedOnThisRoute = true
                try await sleep(.seconds(min(reply.retryAfterSeconds ?? 1, 3)))
            case 500...599 where proxy != nil:
                route = quota.route(after: current, metered: metered)
                waitedOnThisRoute = false
            default:
                throw NetworkFailure.http(code: reply.status, usedUserKey: false)
            }
        }
        if metered { throw NetworkFailure.dailyLimit(resetAt: quota.nextAvailable()) }
        throw NetworkFailure.http(code: 429, usedUserKey: false)
    }

    /// One response, whatever its status.
    private struct Reply {
        let status: Int
        let data: Data
        let response: HTTPURLResponse

        /// `X-RateLimit-Remaining` at or below zero: the budget is used up for today.
        var budgetUsedUp: Bool {
            guard let remaining = number("X-RateLimit-Remaining") else { return false }
            return remaining <= 0
        }

        /// `X-RateLimit-Reset`: seconds until the budget resets.
        var resetSeconds: TimeInterval? { number("X-RateLimit-Reset") }

        var retryAfterSeconds: Double? { number("Retry-After").map { max($0, 0) } }

        func body(usedUserKey: Bool) throws -> Data {
            guard (200...299).contains(status) else { throw NetworkFailure.http(code: status, usedUserKey: usedUserKey) }
            return data
        }

        private func number(_ header: String) -> Double? {
            response.value(forHTTPHeaderField: header).flatMap { Double($0.trimmingCharacters(in: .whitespaces)) }
        }
    }

    private func perform(base: URL, key: String?, path: String, query: [(name: String, value: String)]) async throws -> Reply {
        var items = query
        if let key { items.append((name: "api_key", value: key)) }

        guard var components = URLComponents(url: base, resolvingAgainstBaseURL: false) else {
            throw NetworkFailure.unknown
        }
        // Keeps a proxy's own path ("/openalex" + "/works"); a trailing slash on the base is dropped first.
        let basePath = components.percentEncodedPath.hasSuffix("/") ? String(components.percentEncodedPath.dropLast()) : components.percentEncodedPath
        components.percentEncodedPath = basePath + path
        components.percentEncodedQueryItems = items.map { URLQueryItem(name: $0.name, value: Self.encode($0.value)) }
        guard let url = components.url else { throw NetworkFailure.unknown }

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(from: url)
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        } catch is CancellationError {
            throw CancellationError()
        } catch is URLError {
            log(RequestLog.line(method: "GET", url: url, outcome: "\(NetworkFailure.connectivity)"))
            throw NetworkFailure.connectivity
        } catch {
            throw NetworkFailure.unknown
        }

        guard let http = response as? HTTPURLResponse else { throw NetworkFailure.unknown }
        log(RequestLog.line(method: "GET", url: url, outcome: "\(http.statusCode)"))
        return Reply(status: http.statusCode, data: data, response: http)
    }

    /// Percent-encodes everything outside the RFC 3986 unreserved set, so "+", "&", "=" and "%" survive.
    static func encode(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: unreserved) ?? ""
    }

    private static let unreserved = CharacterSet(
        charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    )

    private func nonBlank(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
```

- [ ] **Step 4: Pass the new parts through both clients**

In `OpenAlexSearchClient.swift` and `OpenAlexLookupClient.swift`, change `init` to (doc comment lines for the new parameters included):

```swift
    /// - Parameters:
    ///   - builtInKey: the key built into the app, or nil; used when the user has none.
    ///   - userKeySource: the user's override, read on every request.
    ///   - quota: picks the route without a user key; nil sends the built-in key with no routing (tests of requests).
    ///   - cache: answers repeated searches and filter lists.
    ///   - sleep: waits before retrying after a per-second limit.
    ///   - log: Debug request logging; receives lines with the key redacted.
    public init(
        session: URLSession,
        builtInKey: String?,
        userKeySource: any UserAPIKeySource,
        quota: OpenAlexQuota? = nil,
        cache: SearchCache? = nil,
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) },
        baseURL: URL = OpenAlexSession.baseURL,
        log: @escaping @Sendable (String) -> Void = RequestLog.debug
    ) {
        http = OpenAlexHTTP(
            session: session, baseURL: baseURL, builtInKey: builtInKey, userKeySource: userKeySource,
            quota: quota, cache: cache, sleep: sleep, log: log
        )
    }
```

(Keep the lookup client's existing first doc line, "Pass the search client's session: both clients share one OpenAlex URLSession.")

- [ ] **Step 5: Wire the live graph**

In `LiveDependencies.live`, replace the two client lines with:

```swift
        // One quota and one cache for both clients: filter lists count toward the cap and share the cache with search.
        let quota = OpenAlexQuota(hasBuiltInKey: builtInKey != nil)
        let cache = SearchCache.live()
        let searchClient = OpenAlexSearchClient(session: session, builtInKey: builtInKey, userKeySource: preferences, quota: quota, cache: cache)
        let lookupClient = OpenAlexLookupClient(session: session, builtInKey: builtInKey, userKeySource: preferences, quota: quota, cache: cache)
```

(The Share Extension calls `live()` too; it keeps its own standard defaults, never fetches the config and so uses the default limits — acceptable, it mostly makes free lookups.)

- [ ] **Step 6: Run the tests to verify they pass**

Run `-only-testing:HashiyaNetworkTests` and `-only-testing:HashiyaDataTests`.
Expected: all pass, including the existing client tests (they pass no quota).

- [ ] **Step 7: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaNetwork ios/HashiyaKit/Sources/HashiyaData/LiveDependencies.swift ios/HashiyaKit/Tests/HashiyaNetworkTests/OpenAlexRoutingTests.swift
git commit -m "feat(ios): route OpenAlex requests through the shared, keyless and user routes"
```

---

### Task 7: The page cap

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaData/SearchRepository.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaData/LiveDependencies.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaTesting/FakeSearchRepository.swift`
- Modify: `ios/Hashiya/UITestingStubs.swift`
- Modify: `ios/HashiyaKit/Sources/FeatureSearch/SearchViewModel.swift`
- Modify: `ios/HashiyaKit/Sources/FeatureSearch/SearchView.swift`
- Modify: `ios/HashiyaKit/Sources/FeatureSearch/L10n.swift`
- Modify: `ios/HashiyaKit/Sources/FeatureSearch/Resources/Localizable.xcstrings`
- Test: `ios/HashiyaKit/Tests/FeatureSearchTests/SearchViewModelTests.swift`, `ios/HashiyaKit/Tests/FeatureSearchTests/SearchStringsTests.swift`, `ios/HashiyaSnapshotTests/SearchSnapshotTests.swift`

**Interfaces:**
- Consumes: `OpenAlexQuota.limits` (Task 3), wired in `LiveDependencies` (Task 6).
- Produces: `SearchRepository.maxPagesPerQuery: Int { get }`; `OpenAlexSearchRepository.init(service:maxPagesPerQuery:)` with `maxPagesPerQuery: @escaping @Sendable () -> Int = { OpenAlexLimits.defaults.maxPagesPerQuery }`; `FakeSearchRepository.init(maxPagesPerQuery: Int = 1000, handler:)`; `AppendState.capReached(results: Int)`; `L10n.pageCap(_ results: Int) -> String`.

- [ ] **Step 1: Write the failing tests**

In `SearchViewModelTests.swift`, add (`makeViewModel(_:)` is the file's existing factory):

```swift
    @Test func pagingStopsAtTheCapWithAFooter() async {
        let repository = FakeSearchRepository(maxPagesPerQuery: 2) { _, cursor in
            let paper = SamplePapers.all[cursor == nil ? 0 : 1]
            return .of([paper], total: 500, next: "next-\(cursor ?? "first")")
        }
        let viewModel = makeViewModel(repository)
        viewModel.updateText("bert")
        viewModel.submitNow()
        await viewModel.waitForPendingWork()
        #expect(viewModel.append == .idle)
        viewModel.loadMore()
        await viewModel.waitForPendingWork()
        #expect(viewModel.append == .capReached(results: 50))
        viewModel.loadMore()
        await viewModel.waitForPendingWork()
        #expect(repository.calls.count == 2)
    }

    @Test func aLastPageBeforeTheCapEndsNormally() async {
        let repository = FakeSearchRepository(maxPagesPerQuery: 1) { _, _ in .of([SamplePapers.attention], total: 1, next: nil) }
        let viewModel = makeViewModel(repository)
        viewModel.updateText("bert")
        viewModel.submitNow()
        await viewModel.waitForPendingWork()
        #expect(viewModel.append == .endReached)
    }

    @Test func skippedDuplicatePagesCountTowardTheCap() async {
        let repository = FakeSearchRepository(maxPagesPerQuery: 2) { _, cursor in
            .of([SamplePapers.attention], total: 500, next: "next-\(cursor ?? "first")")
        }
        let viewModel = makeViewModel(repository)
        viewModel.updateText("bert")
        viewModel.submitNow()
        await viewModel.waitForPendingWork()
        viewModel.loadMore()
        await viewModel.waitForPendingWork()
        #expect(repository.calls.count == 2)
        #expect(viewModel.append == .capReached(results: 50))
    }
```

In `SearchStringsTests.swift`, add:

```swift
    @Test func thePageCapFooterShowsTheNumberOfResults() {
        #expect(inLanguage("en") { L10n.pageCap(200) } == "Showing the first 200 results. Refine your search to see more.")
        #expect(inLanguage("ar") { L10n.pageCap(200) }.contains("\u{2068}200\u{2069}"))
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run `-only-testing:FeatureSearchTests`.
Expected: build failure — `extra argument 'maxPagesPerQuery' in call`.

- [ ] **Step 3: Add the limit to the repository**

In `SearchRepository.swift`, add to the protocol:

```swift
    /// Pages one search may load; read when each page arrives.
    var maxPagesPerQuery: Int { get }
```

and change `OpenAlexSearchRepository` to:

```swift
public struct OpenAlexSearchRepository: SearchRepository {
    private let service: any OpenAlexSearchService
    private let maxPages: @Sendable () -> Int

    public init(
        service: any OpenAlexSearchService,
        maxPagesPerQuery: @escaping @Sendable () -> Int = { OpenAlexLimits.defaults.maxPagesPerQuery }
    ) {
        self.service = service
        maxPages = maxPagesPerQuery
    }

    public var maxPagesPerQuery: Int { maxPages() }
```

(`searchPage` stays unchanged.) In `LiveDependencies.live`, change the search repository line to:

```swift
            searchRepository: OpenAlexSearchRepository(service: searchClient, maxPagesPerQuery: { quota.limits.maxPagesPerQuery }),
```

In `FakeSearchRepository.swift`, add a stored `public let maxPagesPerQuery: Int`, change the designated init to `public init(maxPagesPerQuery: Int = 1000, handler: @escaping Handler)` (setting it), and the convenience init to `public convenience init(page: SearchPage, maxPagesPerQuery: Int = 1000) { self.init(maxPagesPerQuery: maxPagesPerQuery) { _, _ in page } }`. In `ios/Hashiya/UITestingStubs.swift`, add `var maxPagesPerQuery: Int { 1000 }` to `StubSearchRepository`.

- [ ] **Step 4: Stop paging at the cap**

In `SearchViewModel.swift`:

```swift
public enum AppendState: Equatable, Sendable {
    case idle, loading, endReached
    /// The search loaded its last allowed page; `results` is how many that is.
    case capReached(results: Int)
    case failed(SearchError)
}
```

Add `@ObservationIgnored private var pagesLoaded = 0` beside `nextCursor`, set `pagesLoaded = 0` in `resetResults()`, change the duplicate-page condition in `loadNextPage` to

```swift
                if !addedAny, self.append == .idle, duplicatePages + 1 < Self.maxDuplicatePages {
```

and replace `add(_:)` with:

```swift
    /// Appends the page's papers that were not shown yet for this query, and stops at the page cap.
    @discardableResult
    private func add(_ page: SearchPage) -> Bool {
        let new = page.papers.filter { seenIDs.insert($0.openAlexID).inserted }
        papers.append(contentsOf: new)
        nextCursor = page.nextCursor
        pagesLoaded += 1
        let cap = repository.maxPagesPerQuery
        if page.nextCursor == nil {
            append = .endReached
        } else if pagesLoaded >= cap {
            append = .capReached(results: cap * SearchQuery.pageSize)
        } else {
            append = .idle
        }
        return !new.isEmpty
    }
```

- [ ] **Step 5: Show the footer**

In `FeatureSearch/Resources/Localizable.xcstrings` add:

```json
    "search.pageCap" : {
      "extractionState" : "manual",
      "localizations" : {
        "ar" : { "stringUnit" : { "state" : "translated", "value" : "تُعرض أول %@ نتيجة. حسِّن بحثك لرؤية المزيد." } },
        "en" : { "stringUnit" : { "state" : "translated", "value" : "Showing the first %@ results. Refine your search to see more." } }
      }
    },
```

In `FeatureSearch/L10n.swift` add:

```swift
    /// "Showing the first 200 results. …", the number formatted for the locale. (Caps are multiples of 25, which all
    /// take Arabic's singular-noun form, so the string has no plural variants.)
    static func pageCap(_ results: Int) -> String {
        format("search.pageCap", PaperFormat.number(results))
    }
```

In `SearchView.swift`'s `footer`, add before `case .endReached:`:

```swift
        case let .capReached(results):
            Text(verbatim: L10n.pageCap(results))
                .font(.hashiya(.body))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
```

In `ios/HashiyaSnapshotTests/SearchSnapshotTests.swift`, add after `appendErrorFooter()`:

```swift
    @Test func pageCapFooter() async {
        let viewModel = await resultsViewModel()
        viewModel.append = .capReached(results: 200)
        assertHashiyaSnapshots(of: screen(viewModel), named: "pageCap", arabicText: "حسِّن بحثك لرؤية المزيد")
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run `-only-testing:FeatureSearchTests`, `-only-testing:HashiyaDataTests`, then `python3 ios/scripts/check-translations.py`; also build the app target once (`xcodebuild build -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,id=F857F105-E1D5-449B-BBB0-0FA009FBD574' 2>&1 | tail -5`) so `UITestingStubs.swift` compiles.
Expected: all pass; `** BUILD SUCCEEDED **`.

- [ ] **Step 7: Commit**

```bash
git add ios/HashiyaKit ios/Hashiya/UITestingStubs.swift ios/HashiyaSnapshotTests/SearchSnapshotTests.swift
git commit -m "feat(ios): stop a search at the configured page cap"
```

---

### Task 8: The Settings footer

**Files:**
- Modify: `ios/HashiyaKit/Sources/FeatureSettings/SettingsView.swift` (`apiKeySection`)
- Modify: `ios/HashiyaKit/Sources/FeatureSettings/Resources/Localizable.xcstrings`
- Test: `ios/HashiyaKit/Tests/FeatureSettingsTests/SettingsViewModelTests.swift` (string check)

**Interfaces:**
- Produces: string key `settings.apiKeyFooter`.

- [ ] **Step 1: Write the failing test**

Add to `SettingsViewModelTests.swift`, inside the existing `@MainActor` suite:

```swift
    @Test func theAPIKeyFooterLinksToFreeKeysInBothLanguages() {
        for language in ["en", "ar"] {
            let previous = HashiyaLanguage.override
            HashiyaLanguage.override = language
            defer { HashiyaLanguage.override = previous }
            let footer = L10n.string("settings.apiKeyFooter")
            #expect(footer.contains("(https://openalex.org/settings/api)"))
            #expect(!footer.hasPrefix("settings."))
        }
    }
```

(The file already has `@testable import FeatureSettings` and `import HashiyaDesignSystem`.)

- [ ] **Step 2: Run it to verify it fails**

Run `-only-testing:FeatureSettingsTests`.
Expected: FAIL — the footer is the raw key `settings.apiKeyFooter`.

- [ ] **Step 3: Add the string and the footer**

In `FeatureSettings/Resources/Localizable.xcstrings` add:

```json
    "settings.apiKeyFooter" : {
      "extractionState" : "manual",
      "localizations" : {
        "ar" : { "stringUnit" : { "state" : "translated", "value" : "يمنحك مفتاح شخصي مجاني المزيد من عمليات البحث اليومية. [احصل على مفتاح مجاني](https://openalex.org/settings/api)" } },
        "en" : { "stringUnit" : { "state" : "translated", "value" : "A free personal key gives you more daily searches. [Get a free key](https://openalex.org/settings/api)" } }
      }
    },
```

In `SettingsView.swift`'s `apiKeySection`, add after the `header:` closure:

```swift
        } footer: {
            let text = L10n.string("settings.apiKeyFooter")
            Text((try? AttributedString(markdown: text)) ?? AttributedString(text))
                .font(.hashiya(.label))
                .tint(HashiyaColors.primary)
        }
```

(i.e. the section becomes `Section { … } header: { … } footer: { … }`; the language section's footer shows the same shape.)

- [ ] **Step 4: Run the tests to verify they pass**

Run `-only-testing:FeatureSettingsTests`, then `python3 ios/scripts/check-translations.py`.
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Sources/FeatureSettings ios/HashiyaKit/Tests/FeatureSettingsTests
git commit -m "feat(ios): point Settings to free personal OpenAlex keys"
```

---

### Task 9: Docs, the spec's order and snapshot baselines

**Files:**
- Modify: `docs/release.md`
- Modify: `docs/superpowers/specs/2026-10-05-openalex-quota-design.md` (§11)
- Delete: `ios/HashiyaSnapshotTests/__Snapshots__/iOS18/SettingsSnapshotTests/*` and `ios/HashiyaSnapshotTests/__Snapshots__/iOS26/SettingsSnapshotTests/*` (the API key section grew)

- [ ] **Step 1: Document the quota**

Add to `docs/release.md`, before `## Each release`:

~~~markdown
## OpenAlex quota

Without a personal key, each install searches on the shared route — the built-in key, or a proxy once one is set — up
to a daily cap per device, then without a key, then shows "Daily search limit reached" with the reset time. Lookups
by id or DOI are free and always go out. Searches are cached on the device for 24 hours.

Usage of the built-in key: openalex.org → Settings → API (budget used today, resets at midnight UTC).

### The `openAlex` section of `app-config.json`

In FadyFouad/Hashiya-Privacy-Policy. Optional: the defaults apply without it; each field is checked on its own.

```json
"openAlex": { "dailyDeviceCalls": 60, "maxPagesPerQuery": 8, "baseUrl": null }
```

- `dailyDeviceCalls` (0–1000): searches and filter lists per device per UTC day on the shared route; 0 turns it off.
- `maxPagesPerQuery` (1–40): pages of 25 one search can load.
- `baseUrl` (`https://` or null): the proxy for the shared route.

Apps read it at launch; new values apply to the next search.

### Switching to a proxy

The proxy must:

1. Accept the same paths and query parameters as `https://api.openalex.org` (at least `GET /works` and
   `GET /works/{id}`) and return OpenAlex's bodies and status codes unchanged.
2. Add the OpenAlex key itself (the apps send none to it).
3. Pass through `X-RateLimit-Remaining`, `X-RateLimit-Reset` and `Retry-After`, and answer `429` with
   `X-RateLimit-Remaining: 0` when a client's budget is used up.
4. Limit by network address, keep no request logs, and store nothing about clients beyond short-lived counters.

Then:

1. Add to the privacy policy (English and Arabic): search requests pass through Hashiya's server, which sees network
   addresses and keeps no logs. Check the App Store privacy answers and Play's Data safety form against it.
2. Set `baseUrl` in `app-config.json`. Requests with a personal key, and keyless ones, still go straight to OpenAlex.
3. If the proxy fails (unreachable or 5xx), apps fall back to keyless requests, so search keeps working.
~~~

- [ ] **Step 2: Put iOS first in the spec's delivery**

In the spec's §11, swap items 1 and 2 so iOS is first, and change the header line `- **Scope:** … Android ships first, then iOS …` to `iOS ships first, then Android`.

- [ ] **Step 3: Remove the Settings baselines**

```bash
git rm -q ios/HashiyaSnapshotTests/__Snapshots__/iOS18/SettingsSnapshotTests/* ios/HashiyaSnapshotTests/__Snapshots__/iOS26/SettingsSnapshotTests/*
```

- [ ] **Step 4: Commit**

```bash
git add docs/release.md docs/superpowers/specs/2026-10-05-openalex-quota-design.md
git commit -m "docs: the OpenAlex quota, its config and the proxy contract"
```

- [ ] **Step 5: Record the snapshot baselines (controller, after pushing the branch)**

From this branch: `bash ios/scripts/record-snapshots-on-ci.sh`. Then look at the new `dailyLimit`, `pageCap` and Settings images in English and Arabic (the Arabic time must read in order, inside the sentence) before the PR's CI run.
