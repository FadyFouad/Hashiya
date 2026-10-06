# Feedback and Rating (iOS) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An About section in Settings (Send feedback, Rate Hashiya, Version) and a rating prompt through SwiftUI's `requestReview`, asked after a few saves or a BibTeX export — the iOS side of what Android shipped in PR #45.

**Architecture:** The rule, the counters and the prompt live in `HashiyaDiagnostics`, next to crash reporting and analytics, and travel in the existing `Diagnostics` bundle as `diagnostics.review` — so no view model's initializer changes. `.none` (Debug builds, the Share Extension, tests) never asks. In Release, `DiagnosticsStartup` builds a `DefaultReviewPrompt` whose requester bumps a `@MainActor @Observable ReviewRequests`; `RootView` watches it and calls SwiftUI's `requestReview`. The About section is a public SwiftUI view in `FeatureSettings`, with the mail and store URLs built by small testable functions.

**Tech Stack:** Swift 6, SwiftUI, StoreKit (`RequestReviewAction`), Swift Testing, swift-snapshot-testing, XcodeGen.

**Spec:** `docs/superpowers/specs/2026-10-06-feedback-and-rating-design.md`

## Global Constraints

- Feedback address `fady.fouad.a@gmail.com`; subject "Hashiya feedback" / «ملاحظات حول حاشية».
- Info line: `Hashiya 0.3.0 (3) · iOS 26.0 · iPhone17,1 · ar` (version and build, system name and version, device model identifier, app language). Nothing from the library.
- Rule: at least **5** saves **or** one BibTeX export; first opened at least **3 days** ago; never asked or last asked at least **120 days** ago. A stored time in the future means "not yet".
- Counters only go up; Copy BibTeX for one paper and restores don't count; Share Extension saves don't count (it has `Diagnostics.none`).
- "Last asked" is stored when the request is made.
- Off in Debug builds (`DiagnosticsStartup.make()` returns `.none`), so UI tests and snapshot tests never ask.
- No new analytics events. Counters stay in `UserDefaults` keys under `reviewPrompt.`; they count whether or not usage statistics are on.
- Rate link: `https://apps.apple.com/app/id6817343027?action=write-review`.
- Version row: "Version 0.3.0 (3)" / «الإصدار 0.3.0 (3)» with Latin digits; the Arabic string wraps the value in U+2066 … U+2069.
- No mail app: copy the address and show "Email address copied: fady.fouad.a@gmail.com" / «تم نسخ عنوان البريد: …» (the address wrapped in U+2068 … U+2069).
- Strings: About «حول التطبيق», Send feedback «إرسال ملاحظات», Rate Hashiya «قيّم حاشية».
- Every String Catalog key has an Arabic translation (`python3 ios/scripts/check-translations.py` must pass); after a local build, restore `ios/Hashiya/InfoPlist.xcstrings` and `ios/HashiyaShare/InfoPlist.xcstrings` if Xcode rewrote them (`git checkout -- <file>`), and stage files by name, never `git add -A`.
- Commits authored `Fady <fady.fouad.a@gmail.com>`, no AI attribution or trailers.

Test command (swap the `-only-testing:` target per step):
```bash
cd ios && xcodegen generate --spec project.yml >/dev/null && cd ..
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya \
  -destination 'platform=iOS Simulator,id=F857F105-E1D5-449B-BBB0-0FA009FBD574' \
  -only-testing:HashiyaDiagnosticsTests -collect-test-diagnostics never 2>&1 | tail -40
```
Snapshot baselines are recorded on CI (Task 6), not locally.

## Review Focus

1. A save made from the preview sheet must not ask over the sheet; it asks when the sheet closes. In the wide layout the preview is a pane, not a sheet, so it asks at once (Task 3 tests both).
2. Closing a preview without a save never asks (Task 3 test) — the Android review found this.
3. The Arabic version and email lines keep their order (Task 4 tests the exact strings).
4. With two iPad windows, one request shows one prompt: the first window to see it takes it (Task 2 test of `ReviewRequests.take()`).
5. A device clock set backwards never asks early (Task 1 test).

---

### Task 1: The rule, the store and the prompt in `HashiyaDiagnostics`

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaDiagnostics/ReviewPrompting.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaDiagnostics/Diagnostics.swift` (add `review`)
- Modify: `ios/HashiyaKit/Sources/HashiyaTesting/FakeDiagnostics.swift` (`FakeReviewPrompting`; `.fake(..., review:)`)
- Test: `ios/HashiyaKit/Tests/HashiyaDiagnosticsTests/ReviewPromptTests.swift`

**Interfaces:**
- Produces: `public struct ReviewCounters: Equatable, Sendable { firstOpenedAt: Date?; saves: Int; exported: Bool; lastAskedAt: Date? }`; `public enum ReviewRule { static let minSaves = 5; static let settle: TimeInterval; static let gap: TimeInterval; static func shouldAsk(_: ReviewCounters, now: Date) -> Bool }`; `public protocol ReviewPrompting: Sendable { func markOpened(); func recordSave(); func recordExport(); func askIfDue() }`; `public struct NoReviewPrompting: ReviewPrompting`; `public final class UserDefaultsReviewCounterStore: @unchecked Sendable` with `init(defaults: UserDefaults = .standard)`, `read()`, `markOpened(now:)`, `addSave()`, `markExported()`, `markAsked(now:)`; `public final class DefaultReviewPrompt: ReviewPrompting` with `init(store: UserDefaultsReviewCounterStore, now: @escaping @Sendable () -> Date = { Date() }, request: @escaping @Sendable () -> Void)`; `Diagnostics.review: any ReviewPrompting` (init parameter `review: any ReviewPrompting = NoReviewPrompting()`); `FakeReviewPrompting` with `opened`, `saves`, `exports`, `asks`.

- [ ] **Step 1: Write the failing tests**

`ReviewPromptTests.swift`:
```swift
import Foundation
import HashiyaDiagnostics
import HashiyaTesting
import Testing

struct ReviewRuleTests {
    private let day: TimeInterval = 24 * 60 * 60
    private let opened = Date(timeIntervalSince1970: 1_000 * 24 * 60 * 60)

    private func counters(saves: Int = 0, exported: Bool = false, lastAskedAt: Date? = nil, firstOpenedAt: Date?? = nil) -> ReviewCounters {
        ReviewCounters(firstOpenedAt: firstOpenedAt ?? opened, saves: saves, exported: exported, lastAskedAt: lastAskedAt)
    }

    @Test func fiveSavesAfterThreeDaysAsks() {
        #expect(ReviewRule.shouldAsk(counters(saves: 5), now: opened + 3 * day))
    }

    @Test func fourSavesDoNot() {
        #expect(!ReviewRule.shouldAsk(counters(saves: 4), now: opened + 30 * day))
    }

    @Test func oneExportIsEnough() {
        #expect(ReviewRule.shouldAsk(counters(exported: true), now: opened + 3 * day))
    }

    @Test func notBeforeThreeDays() {
        #expect(!ReviewRule.shouldAsk(counters(saves: 9), now: opened + 3 * day - 1))
    }

    @Test func neverOpenedNeverAsks() {
        #expect(!ReviewRule.shouldAsk(counters(saves: 9, firstOpenedAt: .some(nil)), now: opened + 30 * day))
    }

    @Test func waitsHundredTwentyDaysAfterAsking() {
        let asked = opened + 10 * day
        #expect(!ReviewRule.shouldAsk(counters(saves: 9, lastAskedAt: asked), now: asked + 120 * day - 1))
        #expect(ReviewRule.shouldAsk(counters(saves: 9, lastAskedAt: asked), now: asked + 120 * day))
    }

    @Test func aClockSetBackwardsNeverAsksEarly() {
        #expect(!ReviewRule.shouldAsk(counters(saves: 9), now: opened - day))
        #expect(!ReviewRule.shouldAsk(counters(saves: 9, lastAskedAt: opened + 50 * day), now: opened + 40 * day))
    }
}

struct DefaultReviewPromptTests {
    private final class Clock: @unchecked Sendable {
        var now = Date(timeIntervalSince1970: 1_000 * 24 * 60 * 60)
    }

    private final class Requests: @unchecked Sendable {
        var count = 0
    }

    private let day: TimeInterval = 24 * 60 * 60
    private let clock = Clock()
    private let requests = Requests()
    private let defaults = TestDefaults.make()

    private func prompt() -> DefaultReviewPrompt {
        DefaultReviewPrompt(store: UserDefaultsReviewCounterStore(defaults: defaults), now: { [clock] in clock.now }, request: { [requests] in requests.count += 1 })
    }

    @Test func asksOnceTheRuleHoldsAndRecordsWhen() {
        let prompt = prompt()
        prompt.markOpened()
        for _ in 0..<5 { prompt.recordSave() }
        prompt.askIfDue()
        #expect(requests.count == 0)
        clock.now += 3 * day
        prompt.askIfDue()
        #expect(requests.count == 1)
        #expect(UserDefaultsReviewCounterStore(defaults: defaults).read().lastAskedAt == clock.now)
        prompt.askIfDue()
        #expect(requests.count == 1)
    }

    @Test func theStoreKeepsEverythingAndTheFirstOpen() {
        let store = UserDefaultsReviewCounterStore(defaults: defaults)
        store.markOpened(now: Date(timeIntervalSince1970: 10))
        store.markOpened(now: Date(timeIntervalSince1970: 20))
        store.addSave()
        store.addSave()
        store.markExported()
        store.markAsked(now: Date(timeIntervalSince1970: 30))
        #expect(UserDefaultsReviewCounterStore(defaults: defaults).read() == ReviewCounters(
            firstOpenedAt: Date(timeIntervalSince1970: 10), saves: 2, exported: true, lastAskedAt: Date(timeIntervalSince1970: 30)
        ))
    }

    @Test func anEmptyStoreReadsAsNothing() {
        #expect(UserDefaultsReviewCounterStore(defaults: defaults).read() == ReviewCounters(firstOpenedAt: nil, saves: 0, exported: false, lastAskedAt: nil))
    }

    @Test func noneNeverAsks() {
        let review = Diagnostics.none.review
        review.markOpened()
        for _ in 0..<9 { review.recordSave() }
        review.askIfDue()
        #expect(requests.count == 0)
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run the test command with `-only-testing:HashiyaDiagnosticsTests`.
Expected: build FAILS — `ReviewCounters`, `ReviewRule`, `DefaultReviewPrompt`, `UserDefaultsReviewCounterStore`, `Diagnostics.review` not found.

- [ ] **Step 3: Implement `ReviewPrompting.swift`**

```swift
import Foundation
import os

/// What the rating rule reads. Kept on the device; it never leaves it.
public struct ReviewCounters: Equatable, Sendable {
    /// When the app was first opened; nil before the first open was recorded.
    public var firstOpenedAt: Date?
    /// Papers saved since install. Removing a paper doesn't lower it.
    public var saves: Int
    /// Whether a BibTeX export ever finished.
    public var exported: Bool
    /// When the app last asked the system for its rating prompt; nil if never.
    public var lastAskedAt: Date?

    public init(firstOpenedAt: Date?, saves: Int, exported: Bool, lastAskedAt: Date?) {
        self.firstOpenedAt = firstOpenedAt
        self.saves = saves
        self.exported = exported
        self.lastAskedAt = lastAskedAt
    }
}

/// When to ask for a rating: once the app has shown its value (5 saves or a BibTeX export), the person has had it for
/// 3 days, and it hasn't asked in 120 days. A stored time later than now (the clock went back) reads as "not yet".
public enum ReviewRule {
    public static let minSaves = 5
    public static let settle: TimeInterval = 3 * 24 * 60 * 60
    public static let gap: TimeInterval = 120 * 24 * 60 * 60

    public static func shouldAsk(_ counters: ReviewCounters, now: Date) -> Bool {
        guard let firstOpenedAt = counters.firstOpenedAt else { return false }
        guard counters.saves >= minSaves || counters.exported else { return false }
        guard now.timeIntervalSince(firstOpenedAt) >= settle else { return false }
        guard let lastAskedAt = counters.lastAskedAt else { return true }
        return now.timeIntervalSince(lastAskedAt) >= gap
    }
}

/// Asks for a store rating at good moments. Only the app knows how to show the system's prompt.
public protocol ReviewPrompting: Sendable {
    /// The app opened; the first call starts the 3-day wait.
    func markOpened()
    /// A paper was saved from Search or Add by ID.
    func recordSave()
    /// A BibTeX export finished and its share sheet closed.
    func recordExport()
    /// Nothing covers the screen now: asks for the system's rating prompt if `ReviewRule` says so.
    func askIfDue()
}

/// Debug builds, the Share Extension and tests: counts nothing, never asks.
public struct NoReviewPrompting: ReviewPrompting {
    public init() {}
    public func markOpened() {}
    public func recordSave() {}
    public func recordExport() {}
    public func askIfDue() {}
}

/// The counters in `UserDefaults`, under `reviewPrompt.`.
public final class UserDefaultsReviewCounterStore: @unchecked Sendable {
    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public func read() -> ReviewCounters {
        ReviewCounters(
            firstOpenedAt: defaults.object(forKey: Key.firstOpenedAt) as? Date,
            saves: defaults.integer(forKey: Key.saves),
            exported: defaults.bool(forKey: Key.exported),
            lastAskedAt: defaults.object(forKey: Key.lastAskedAt) as? Date
        )
    }

    public func markOpened(now: Date) {
        if defaults.object(forKey: Key.firstOpenedAt) == nil { defaults.set(now, forKey: Key.firstOpenedAt) }
    }

    public func addSave() { defaults.set(defaults.integer(forKey: Key.saves) + 1, forKey: Key.saves) }

    public func markExported() { defaults.set(true, forKey: Key.exported) }

    public func markAsked(now: Date) { defaults.set(now, forKey: Key.lastAskedAt) }

    private enum Key {
        static let firstOpenedAt = "reviewPrompt.firstOpenedAt"
        static let saves = "reviewPrompt.saves"
        static let exported = "reviewPrompt.exported"
        static let lastAskedAt = "reviewPrompt.lastAskedAt"
    }
}

/// Counts and decides; "last asked" is stored when the request is made, since the system never says whether its
/// prompt appeared. Calls may come from any thread.
public final class DefaultReviewPrompt: ReviewPrompting {
    private let store: UserDefaultsReviewCounterStore
    private let now: @Sendable () -> Date
    private let request: @Sendable () -> Void
    private let lock = OSAllocatedUnfairLock()

    public init(store: UserDefaultsReviewCounterStore, now: @escaping @Sendable () -> Date = { Date() }, request: @escaping @Sendable () -> Void) {
        self.store = store
        self.now = now
        self.request = request
    }

    public func markOpened() { lock.withLock { store.markOpened(now: now()) } }

    public func recordSave() { lock.withLock { store.addSave() } }

    public func recordExport() { lock.withLock { store.markExported() } }

    public func askIfDue() {
        let ask = lock.withLock {
            let time = now()
            let ask = ReviewRule.shouldAsk(store.read(), now: time)
            if ask { store.markAsked(now: time) }
            return ask
        }
        if ask { request() }
    }
}
```

- [ ] **Step 4: Add `review` to `Diagnostics`**

In `Diagnostics.swift`: add `public let review: any ReviewPrompting` with a doc comment ("Asks for a store rating at good moments; `NoReviewPrompting` everywhere but Release builds of the app."); the initializer becomes `public init(crash: any CrashReporting, analytics: any AnalyticsTracking, isLive: Bool, review: any ReviewPrompting = NoReviewPrompting())` and sets it; `.none` needs no change (the default applies). Update the struct's doc comment: "The crash reporter, analytics tracker and rating prompt a part of the app was given."

- [ ] **Step 5: The fake**

In `FakeDiagnostics.swift` add:
```swift
/// Counts every call, for tests.
public final class FakeReviewPrompting: ReviewPrompting {
    private struct State {
        var opened = 0
        var saves = 0
        var exports = 0
        var asks = 0
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    public init() {}

    public var opened: Int { state.withLock { $0.opened } }
    public var saves: Int { state.withLock { $0.saves } }
    public var exports: Int { state.withLock { $0.exports } }
    public var asks: Int { state.withLock { $0.asks } }

    public func markOpened() { state.withLock { $0.opened += 1 } }
    public func recordSave() { state.withLock { $0.saves += 1 } }
    public func recordExport() { state.withLock { $0.exports += 1 } }
    public func askIfDue() { state.withLock { $0.asks += 1 } }
}
```
and change `.fake` to `public static func fake(crash: FakeCrashReporting = FakeCrashReporting(), analytics: FakeAnalytics = FakeAnalytics(), review: any ReviewPrompting = NoReviewPrompting()) -> Diagnostics { Diagnostics(crash: crash, analytics: analytics, isLive: false, review: review) }`.

- [ ] **Step 6: Run the tests**

Run the test command with `-only-testing:HashiyaDiagnosticsTests`.
Expected: all pass, including the 11 new ones.

- [ ] **Step 7: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaDiagnostics/ReviewPrompting.swift ios/HashiyaKit/Sources/HashiyaDiagnostics/Diagnostics.swift ios/HashiyaKit/Sources/HashiyaTesting/FakeDiagnostics.swift ios/HashiyaKit/Tests/HashiyaDiagnosticsTests/ReviewPromptTests.swift
git commit -m "feat(ios): decide when to ask for a store rating"
```

---

### Task 2: Requests and the system prompt in the app

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaDiagnostics/ReviewRequests.swift`
- Test: `ios/HashiyaKit/Tests/HashiyaDiagnosticsTests/ReviewRequestsTests.swift`
- Modify: `ios/Hashiya/Diagnostics/DiagnosticsStartup.swift` (Release: pass `review:`)
- Modify: `ios/Hashiya/HashiyaApp.swift` (`markOpened()` in `init`)
- Modify: `ios/Hashiya/RootView.swift` (watch requests, call `requestReview`)

**Interfaces:**
- Consumes: Task 1's `DefaultReviewPrompt`, `UserDefaultsReviewCounterStore`, `Diagnostics(review:)`.
- Produces: `@MainActor @Observable public final class ReviewRequests { public static let shared; public private(set) var count: Int; public func post(); public func take() -> Bool }`.

- [ ] **Step 1: Write the failing test**

```swift
import HashiyaDiagnostics
import Testing

@MainActor
struct ReviewRequestsTests {
    @Test func oneRequestIsTakenOnce() {
        let requests = ReviewRequests()
        requests.post()
        #expect(requests.count == 1)
        // Two iPad windows see the change: only the first shows the prompt.
        #expect(requests.take())
        #expect(!requests.take())
    }

    @Test func nothingToTakeBeforeARequest() {
        #expect(!ReviewRequests().take())
    }
}
```

- [ ] **Step 2: Run to verify it fails**

`-only-testing:HashiyaDiagnosticsTests` → build FAILS, `ReviewRequests` not found.

- [ ] **Step 3: Implement**

`ReviewRequests.swift`:
```swift
import Observation

/// Requests for the system's rating prompt. The app's windows watch `count`; the first to `take()` one shows it, so two
/// iPad windows never ask twice.
@MainActor
@Observable
public final class ReviewRequests {
    public static let shared = ReviewRequests()

    public private(set) var count = 0
    private var pending = false

    public init() {}

    public func post() {
        pending = true
        count += 1
    }

    /// True once per request.
    public func take() -> Bool {
        defer { pending = false }
        return pending
    }
}
```

- [ ] **Step 4: Run the test**

`-only-testing:HashiyaDiagnosticsTests` → all pass.

- [ ] **Step 5: Wire the app**

`DiagnosticsStartup.make()` Release branch: replace the `Diagnostics(...)` line with
```swift
        let review = DefaultReviewPrompt(store: UserDefaultsReviewCounterStore()) {
            Task { @MainActor in ReviewRequests.shared.post() }
        }
        let diagnostics = Diagnostics(crash: FirebaseCrashReporting(), analytics: FirebaseAnalyticsTracking(), isLive: true, review: review)
```
`HashiyaApp.init`, after `let diagnostics = …`: `diagnostics.review.markOpened()`.

`RootView.swift`: add `import StoreKit`, `@Environment(\.requestReview) private var requestReview`, and on the root view (next to `.environment(\.diagnostics, container.diagnostics)`):
```swift
        // The system's rating prompt, when the rule asked for one; one window takes it.
        .onChange(of: ReviewRequests.shared.count) {
            if ReviewRequests.shared.take() { requestReview() }
        }
```

- [ ] **Step 6: Build Release and Debug**

```bash
cd ios && xcodegen generate --spec project.yml >/dev/null && cd ..
xcodebuild build -project ios/Hashiya.xcodeproj -scheme Hashiya -configuration Release -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO 2>&1 | tail -5
xcodebuild build -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,id=F857F105-E1D5-449B-BBB0-0FA009FBD574' 2>&1 | tail -5
```
Expected: `** BUILD SUCCEEDED **` twice. Restore the InfoPlist string catalogs if Xcode rewrote them.

- [ ] **Step 7: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaDiagnostics/ReviewRequests.swift ios/HashiyaKit/Tests/HashiyaDiagnosticsTests/ReviewRequestsTests.swift ios/Hashiya/Diagnostics/DiagnosticsStartup.swift ios/Hashiya/HashiyaApp.swift ios/Hashiya/RootView.swift
git commit -m "feat(ios): show the system's rating prompt when the rule asks"
```

---

### Task 3: Count saves and exports, ask at safe moments

**Files:**
- Modify: `ios/HashiyaKit/Sources/FeatureSearch/SearchViewModel.swift` (`toggleSave`; new `askForReviewAfterPreview()`)
- Modify: `ios/HashiyaKit/Sources/FeatureSearch/SearchView.swift` (sheet `onDismiss`; pane save)
- Modify: `ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift` (`export()`)
- Test: `ios/HashiyaKit/Tests/FeatureSearchTests/SearchReviewPromptTests.swift`
- Test: `ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryReviewPromptTests.swift`

**Interfaces:**
- Consumes: `diagnostics.review`, `FakeReviewPrompting`, `.fake(review:)` (Task 1).
- Produces: `SearchViewModel.askForReviewAfterPreview()`.

- [ ] **Step 1: Write the failing Search tests**

Build the view model as `SearchAnalyticsTests.swift` does (`SearchViewModel(repository:lookup:library:preferences:diagnostics:)` with `.fake(review: review)`), using `SamplePapers.attention` (check the fixture name in `HashiyaTesting/SamplePapers.swift`) and a `FakeLibraryRepository` (check its failure switch for saves, e.g. `failOnSave`, and use it):
```swift
import FeatureSearch
import HashiyaDiagnostics
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct SearchReviewPromptTests {
    private let review = FakeReviewPrompting()
    private let library = FakeLibraryRepository()
    private let paper = SamplePapers.attention

    private func makeViewModel() -> SearchViewModel {
        SearchViewModel(
            repository: FakeSearchRepository(page: .of([])), lookup: FakePaperLookupRepository(), library: library,
            preferences: FakeUserPreferencesRepository(), diagnostics: .fake(review: review)
        )
    }

    @Test func aSaveFromTheListCountsAndAsks() async {
        let viewModel = makeViewModel()
        await viewModel.toggleSave(paper)
        #expect(review.saves == 1)
        #expect(review.asks == 1)
    }

    @Test func aSaveFromThePreviewAsksWhenThePreviewCloses() async {
        let viewModel = makeViewModel()
        viewModel.selectedPaper = paper
        await viewModel.toggleSave(paper)
        #expect(review.saves == 1)
        #expect(review.asks == 0)
        viewModel.askForReviewAfterPreview()
        #expect(review.asks == 1)
        viewModel.askForReviewAfterPreview()
        #expect(review.asks == 1)
    }

    @Test func closingThePreviewWithoutASaveDoesNotAsk() {
        let viewModel = makeViewModel()
        viewModel.selectedPaper = paper
        viewModel.askForReviewAfterPreview()
        #expect(review.asks == 0)
    }

    @Test func aFailedSaveDoesNotCount() async {
        let viewModel = makeViewModel()
        library.failOnSave = true
        await viewModel.toggleSave(paper)
        #expect(review.saves == 0)
        #expect(review.asks == 0)
    }
}
```
Match the real fake initializers (open `FakeSearchRepository`, `FakePaperLookupRepository`, `FakeUserPreferencesRepository`, `FakeLibraryRepository` and use their actual init signatures and failure switch).

- [ ] **Step 2: Run to verify they fail**

`-only-testing:FeatureSearchTests/SearchReviewPromptTests` → build FAILS (`askForReviewAfterPreview` missing).

- [ ] **Step 3: Implement in Search**

`SearchViewModel`: add
```swift
    /// A paper was saved from the preview; the rating prompt waits until the preview closes.
    private var reviewPending = false

    /// The preview sheet closed, or a save happened in the side pane: asks for a rating if a save is waiting.
    public func askForReviewAfterPreview() {
        guard reviewPending else { return }
        reviewPending = false
        diagnostics.review.askIfDue()
    }
```
In `toggleSave`, after `diagnostics.analytics.log(.paperSaved(...))`:
```swift
                diagnostics.review.recordSave()
                // Never over the preview sheet: that waits until the sheet closes.
                if selectedPaper == nil { diagnostics.review.askIfDue() } else { reviewPending = true }
```
`SearchView`: the sheet's `onDismiss: openRequestedDetails` becomes `onDismiss: { viewModel.askForReviewAfterPreview(); openRequestedDetails() }`; and in `preview(_:)`, `onToggleSave` becomes
```swift
            onToggleSave: {
                Task {
                    await viewModel.toggleSave(paper)
                    // Beside the results (wide windows) the preview is a pane, not a sheet: nothing to wait for.
                    if previewsInPane { viewModel.askForReviewAfterPreview() }
                }
            },
```
(`previewsInPane` is the view's existing property; check `preview(_:)` is also used for the pane — if the pane builds its content elsewhere, apply the same `onToggleSave` there.)

- [ ] **Step 4: Run the Search tests**

`-only-testing:FeatureSearchTests` → all pass.

- [ ] **Step 5: Write the failing Library tests**

Build the view model as `LibraryViewModelTests.makeViewModel` does, with `share: { _ in shareResult }` and `diagnostics: .fake(review: review)`; put one paper in the library first (`library.save(SamplePapers.attention)` — use the fake's API) so the export has something to write:
```swift
    @Test func anExportCountsAndAsksAfterTheShareSheetCloses() async {
        let viewModel = await makeViewModel(shareResult: true)
        await viewModel.export()
        #expect(review.exports == 1)
        #expect(review.asks == 1)
    }

    @Test func aCancelledOrFailedShareNeitherCountsNorAsks() async {
        let viewModel = await makeViewModel(shareResult: false)
        await viewModel.export()
        #expect(review.exports == 0)
        #expect(review.asks == 0)
    }
```
in `LibraryReviewPromptTests.swift` (`@MainActor struct`, imports as in `LibraryViewModelTests.swift`).

- [ ] **Step 6: Run to verify they fail**

`-only-testing:FeatureLibraryTests/LibraryReviewPromptTests` → FAIL (`exports == 0`).

- [ ] **Step 7: Implement in Library**

In `LibraryViewModel.export()`, after `diagnostics.analytics.log(.export(format: .bibtex, withPdfs: false))`:
```swift
        // `share` returns once the share sheet has closed, so nothing covers the screen now.
        diagnostics.review.recordExport()
        diagnostics.review.askIfDue()
```

- [ ] **Step 8: Run and commit**

`-only-testing:FeatureLibraryTests` and `-only-testing:FeatureSearchTests` → all pass.
```bash
git add ios/HashiyaKit/Sources/FeatureSearch/SearchViewModel.swift ios/HashiyaKit/Sources/FeatureSearch/SearchView.swift ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift ios/HashiyaKit/Tests/FeatureSearchTests/SearchReviewPromptTests.swift ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryReviewPromptTests.swift
git commit -m "feat(ios): count saves and BibTeX exports for the rating prompt"
```

---

### Task 4: The About section

**Files:**
- Create: `ios/HashiyaKit/Sources/FeatureSettings/Feedback.swift`
- Create: `ios/HashiyaKit/Sources/FeatureSettings/AboutSection.swift`
- Modify: `ios/HashiyaKit/Sources/FeatureSettings/SettingsView.swift` (section after Privacy; the copied banner)
- Modify: `ios/HashiyaKit/Sources/FeatureSettings/Resources/Localizable.xcstrings`
- Test: `ios/HashiyaKit/Tests/FeatureSettingsTests/FeedbackTests.swift`
- Test: `ios/HashiyaSnapshotTests/SettingsSnapshotTests.swift` (new `about` snapshot)

**Interfaces:**
- Produces: `public struct AppVersion: Equatable, Sendable { name: String; build: String; var label: String; static var current: AppVersion }`; `enum Feedback { static let address; static let rateURL: URL; static func infoLine(version:system:model:language:) -> String; static func mailURL(subject:version:system:model:language:) -> URL; static var deviceModel: String }`; `public struct AboutSection: View { public init(version: AppVersion = .current, onAddressCopied: @escaping () -> Void = {}) }`.

- [ ] **Step 1: Strings**

Add to `FeatureSettings/Resources/Localizable.xcstrings` (same JSON shape as the neighbouring `settings.privacyPolicy` entry, `"extractionState" : "manual"`, both `en` and `ar` `"state" : "translated"`):

| Key | en | ar |
|-----|----|----|
| `settings.about` | About | حول التطبيق |
| `settings.sendFeedback` | Send feedback | إرسال ملاحظات |
| `settings.rate` | Rate Hashiya | قيّم حاشية |
| `settings.version` | Version %@ | الإصدار ⁦%@⁩ |
| `settings.feedbackSubject` | Hashiya feedback | ملاحظات حول حاشية |
| `settings.feedbackCopied` | Email address copied: %@ | تم نسخ عنوان البريد: ⁨%@⁩ |

Write the U+2066/U+2068/U+2069 characters as JSON escapes (`⁦`), as other bidi strings in the catalogs do. Run `python3 ios/scripts/check-translations.py` → passes.

- [ ] **Step 2: Write the failing tests**

`FeedbackTests.swift`:
```swift
import Foundation
@testable import FeatureSettings
import HashiyaDesignSystem
import Testing

struct FeedbackTests {
    private let version = AppVersion(name: "0.3.0", build: "3")

    @Test func versionLabelUsesLatinDigits() {
        #expect(version.label == "0.3.0 (3)")
    }

    @Test func infoLine() {
        #expect(Feedback.infoLine(version: version, system: "iOS 26.0", model: "iPhone17,1", language: "ar") == "Hashiya 0.3.0 (3) · iOS 26.0 · iPhone17,1 · ar")
    }

    @Test func mailURLIsADraftToTheDeveloper() throws {
        let url = Feedback.mailURL(subject: "Hashiya feedback", version: version, system: "iOS 26.0", model: "iPhone17,1", language: "en")
        let components = try #require(URLComponents(url: url, resolvingAgainstBaseURL: false))
        #expect(components.scheme == "mailto")
        #expect(components.path == "fady.fouad.a@gmail.com")
        let items = Dictionary(uniqueKeysWithValues: (components.queryItems ?? []).map { ($0.name, $0.value ?? "") })
        #expect(items["subject"] == "Hashiya feedback")
        #expect(items["body"] == "\n\nHashiya 0.3.0 (3) · iOS 26.0 · iPhone17,1 · en")
    }

    @Test func rateOpensTheWriteAReviewPage() {
        #expect(Feedback.rateURL.absoluteString == "https://apps.apple.com/app/id6817343027?action=write-review")
    }

    @Test func arabicVersionKeepsItsNumbersInOrder() {
        HashiyaLanguage.override = "ar"
        defer { HashiyaLanguage.override = nil }
        #expect(L10n.format("settings.version", version.label) == "الإصدار \u{2066}0.3.0 (3)\u{2069}")
        #expect(L10n.format("settings.feedbackCopied", Feedback.address) == "تم نسخ عنوان البريد: \u{2068}fady.fouad.a@gmail.com\u{2069}")
    }
}
```
If `HashiyaLanguage.override` is not how other FeatureSettings tests switch to Arabic, use their mechanism (grep the test target for `override` / `"ar"`), and mark the struct `.serialized` if they do.

- [ ] **Step 3: Run to verify they fail**

`-only-testing:FeatureSettingsTests/FeedbackTests` → build FAILS (`AppVersion`, `Feedback` missing).

- [ ] **Step 4: Implement `Feedback.swift`**

```swift
import Foundation
import UIKit

/// This install's version, as stores and support quote it (Latin digits in every language).
public struct AppVersion: Equatable, Sendable {
    public let name: String
    public let build: String

    public init(name: String, build: String) {
        self.name = name
        self.build = build
    }

    public var label: String { "\(name) (\(build))" }

    public static var current: AppVersion {
        let info = Bundle.main.infoDictionary ?? [:]
        return AppVersion(name: info["CFBundleShortVersionString"] as? String ?? "", build: info["CFBundleVersion"] as? String ?? "")
    }
}

/// The feedback email and the store page.
enum Feedback {
    static let address = "fady.fouad.a@gmail.com"
    static let rateURL = URL(string: "https://apps.apple.com/app/id6817343027?action=write-review")!

    /// What the developer needs to reproduce a report; nothing from the library.
    static func infoLine(version: AppVersion, system: String, model: String, language: String) -> String {
        "Hashiya \(version.label) · \(system) · \(model) · \(language)"
    }

    /// A draft to the developer, the message first and the info line under it.
    static func mailURL(subject: String, version: AppVersion, system: String, model: String, language: String) -> URL {
        var components = URLComponents()
        components.scheme = "mailto"
        components.path = address
        components.queryItems = [
            URLQueryItem(name: "subject", value: subject),
            URLQueryItem(name: "body", value: "\n\n" + infoLine(version: version, system: system, model: model, language: language)),
        ]
        return components.url!
    }

    /// "iOS 26.0" (or "iPadOS 26.0").
    @MainActor static var system: String { "\(UIDevice.current.systemName) \(UIDevice.current.systemVersion)" }

    /// The model identifier, e.g. "iPhone17,1"; the simulated one on a simulator.
    static var deviceModel: String {
        if let simulated = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] { return simulated }
        var info = utsname()
        uname(&info)
        return withUnsafeBytes(of: &info.machine) { bytes in
            String(decoding: bytes.prefix { $0 != 0 }, as: UTF8.self)
        }
    }
}
```

- [ ] **Step 5: Run the tests**

`-only-testing:FeatureSettingsTests/FeedbackTests` → all pass.

- [ ] **Step 6: The section**

`AboutSection.swift`:
```swift
import HashiyaDesignSystem
import SwiftUI
import UIKit

/// Settings → About: Send feedback, Rate Hashiya and the version.
public struct AboutSection: View {
    private let version: AppVersion
    private let onAddressCopied: () -> Void
    @Environment(\.openURL) private var openURL

    public init(version: AppVersion = .current, onAddressCopied: @escaping () -> Void = {}) {
        self.version = version
        self.onAddressCopied = onAddressCopied
    }

    public var body: some View {
        Section {
            Button {
                let url = Feedback.mailURL(
                    subject: L10n.string("settings.feedbackSubject"), version: version, system: Feedback.system,
                    model: Feedback.deviceModel, language: HashiyaLanguage.code
                )
                openURL(url) { accepted in
                    // No mail app: the address goes to the clipboard instead.
                    if !accepted {
                        UIPasteboard.general.string = Feedback.address
                        onAddressCopied()
                    }
                }
            } label: {
                row("settings.sendFeedback", systemImage: "envelope")
            }
            .accessibilityIdentifier("settings.sendFeedback")
            Button {
                openURL(Feedback.rateURL)
            } label: {
                row("settings.rate", systemImage: "star")
            }
            .accessibilityIdentifier("settings.rate")
            Text(verbatim: L10n.format("settings.version", version.label))
                .font(.hashiya(.meta))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .accessibilityIdentifier("settings.version")
        } header: {
            Text(verbatim: L10n.string("settings.about"))
                .font(.hashiya(.stateTitle))
                .foregroundStyle(HashiyaColors.onSurface)
                .textCase(nil)
        }
    }

    private func row(_ key: String, systemImage: String) -> some View {
        HStack {
            Text(verbatim: L10n.string(key)).font(.hashiya(.body)).foregroundStyle(HashiyaColors.onSurface)
            Spacer()
            Image(systemName: systemImage).foregroundStyle(HashiyaColors.primary)
        }
    }
}
```
`SettingsView`: add `@State private var addressCopied = false`; in the `Form`, after `privacySection` and before `languageSection`, add `AboutSection(onAddressCopied: { addressCopied = true })`. Add a second overlay and its timer next to the existing backup-message ones:
```swift
            .overlay(alignment: .bottom) {
                if addressCopied {
                    HashiyaBanner(text: L10n.format("settings.feedbackCopied", Feedback.address))
                }
            }
            .animation(.default, value: addressCopied)
            .task(id: addressCopied) {
                guard addressCopied, (try? await Task.sleep(for: HashiyaBanner.duration)) != nil else { return }
                addressCopied = false
            }
```
(`L10n.format` takes `CVarArg`; pass `Feedback.address as NSString` or `as CVarArg` if the compiler asks. `HashiyaLanguage.code` is the "en"/"ar" property `isArabic` reads — check its name in `HashiyaLanguage.swift`.)

- [ ] **Step 7: Snapshot test**

In `ios/HashiyaSnapshotTests/SettingsSnapshotTests.swift` add:
```swift
    /// Settings → About on its own, in a Form as in Settings.
    @Test func about() {
        assertHashiyaSnapshots(
            of: Form { AboutSection(version: AppVersion(name: "0.3.0", build: "3")) }.scrollContentBackground(.hidden).background(HashiyaColors.surface),
            named: "about",
            arabicText: "حول التطبيق"
        )
    }
```
Match `assertHashiyaSnapshots`' real signature and how neighbouring tests import `HashiyaDesignSystem`. Do NOT record baselines locally.

- [ ] **Step 8: Run, check and commit**

`-only-testing:FeatureSettingsTests` → all pass; `python3 ios/scripts/check-translations.py` → passes; build the app scheme once. Restore the InfoPlist catalogs if Xcode rewrote them.
```bash
git add ios/HashiyaKit/Sources/FeatureSettings/Feedback.swift ios/HashiyaKit/Sources/FeatureSettings/AboutSection.swift ios/HashiyaKit/Sources/FeatureSettings/SettingsView.swift ios/HashiyaKit/Sources/FeatureSettings/Resources/Localizable.xcstrings ios/HashiyaKit/Tests/FeatureSettingsTests/FeedbackTests.swift ios/HashiyaSnapshotTests/SettingsSnapshotTests.swift
git commit -m "feat(ios): an About section with feedback, rating and the version"
```

---

### Task 5: Docs and changelog

- [ ] `CHANGELOG.md` `[Unreleased]` → Added: "- **iOS: send feedback and rate Hashiya.** Settings → About opens an email to the developer with the app version and device filled in (nothing from your library), opens the App Store to rate the app, and shows the version. After five saved papers or a BibTeX export, and no sooner than three days after installing, the app may ask for a rating through Apple's own prompt, at most once every four months."
- [ ] In the spec §7, mark the iOS PR delivered.
- [ ] `git add CHANGELOG.md docs/superpowers/specs/2026-10-06-feedback-and-rating-design.md && git commit -m "docs: feedback and the rating prompt on iOS"`

### Task 6: Snapshot baselines (controller, after pushing the branch)

- [ ] From the pushed branch: `bash ios/scripts/record-snapshots-on-ci.sh`; look at the `about` snapshots in English and Arabic (the version reads «الإصدار 0.3.0 (3)» in order) before committing them.
