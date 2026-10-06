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
