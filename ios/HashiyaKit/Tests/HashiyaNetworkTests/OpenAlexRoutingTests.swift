import Foundation
import HashiyaNetwork
import HashiyaTesting
import os
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

    /// `sleep` defaults to the manual sleeper; pass `{ _ in }` where a test must not wait.
    private func search(
        _ server: URLProtocolStub.Server, _ quota: OpenAlexQuota, userKey: String? = nil, cache: Bool = false,
        sleep: (@Sendable (Duration) async throws -> Void)? = nil
    ) -> OpenAlexSearchClient {
        OpenAlexSearchClient(
            session: server.session, builtInKey: "built-in", userKeySource: FixedUserAPIKeySource(userKey),
            quota: quota, cache: cache ? SearchCache(directory: cacheDirectory, now: { [clock] in clock.date }) : nil,
            sleep: sleep ?? { [sleeper] in try await sleeper.sleep($0) }, log: { _ in }
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
        let server = URLProtocolStub.Server { [page] request in
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
        let server = URLProtocolStub.Server { [page] request in
            Self.query(request)["api_key"] == nil ? .json(page) : .status(429, headers: ["X-RateLimit-Remaining": remaining])
        }
        // No waiting: read as a per-second limit, the retry would keep the key and fail here rather than hang.
        _ = try await search(server, quota(), sleep: { _ in }).searchWorks(request)
        #expect(server.requests.count == 2)
        #expect(Self.query(server.requests[1])["api_key"] == nil)
    }

    @Test func aNaNRetryAfterWaitsTheDefaultSecond() async throws {
        let replies = Counter(0)
        let server = URLProtocolStub.Server { [page] _ in replies.next() == 0 ? .status(429, headers: ["Retry-After": "nan"]) : .json(page) }
        let client = search(server, quota())
        let task = Task { [request] in try await client.searchWorks(request) }
        await sleeper.waitForSleeper()
        sleeper.advance(by: .seconds(1))
        _ = try await task.value
        #expect(server.requests.count == 2)
    }

    @Test func aNaNResetMeansTheNextMidnightUTC() async {
        let server = URLProtocolStub.Server(always: Self.usedUp(reset: "nan"))
        let quota = quota()
        await #expect(throws: NetworkFailure.dailyLimit(resetAt: TestClock("2026-10-06T00:00:00Z").date)) {
            try await search(server, quota).searchWorks(request)
        }
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
        let server = URLProtocolStub.Server { [page] _ in replies.next() == 0 ? .status(429, headers: ["X-RateLimit-Remaining": "50"]) : .json(page) }
        let client = search(server, quota())
        let task = Task { [request] in try await client.searchWorks(request) }
        await sleeper.waitForSleeper()
        sleeper.advance(by: .seconds(1))
        _ = try await task.value
        #expect(server.requests.count == 2)
        #expect(Self.query(server.requests[1])["api_key"] == "built-in")
    }

    @Test func aSecondPerSecondLimitIsTheRateLimitedError() async {
        let server = URLProtocolStub.Server(always: .status(429, headers: ["X-RateLimit-Remaining": "abc", "Retry-After": "10"]))
        let client = search(server, quota())
        let task = Task { [request] in try await client.searchWorks(request) }
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
        let task = Task { [request] in try await client.searchWorks(request) }
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
        let server = URLProtocolStub.Server { [page] request in request.url?.host() == "proxy.example" ? failure : .json(page) }
        let quota = quota(limits: OpenAlexLimits(dailyDeviceCalls: 60, maxPagesPerQuery: 8, baseURL: URL(string: "https://proxy.example")))
        _ = try await search(server, quota).searchWorks(request)
        #expect(server.requests.last?.url?.host() == "api.openalex.org")
        #expect(server.lastQuery["api_key"] == nil)
        #expect(quota.meteredRoute() == .shared)
    }

    @Test func aFailingProxyWithKeylessUsedUpIsUnavailableNotTheDailyLimit() async {
        let server = URLProtocolStub.Server(always: .status(503))
        let quota = quota(limits: OpenAlexLimits(dailyDeviceCalls: 60, maxPagesPerQuery: 8, baseURL: URL(string: "https://proxy.example")))
        quota.markUsedUp(.keyless, resetIn: 600)
        await #expect(throws: NetworkFailure.http(code: 503, usedUserKey: false)) {
            try await search(server, quota).searchWorks(request)
        }
        #expect(server.requests.count == 1)
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
        let server = URLProtocolStub.Server { [page] _ in replies.next() == 0 ? .status(500) : .json(page) }
        let client = search(server, quota(), cache: true)
        await #expect(throws: NetworkFailure.http(code: 500, usedUserKey: false)) { try await client.searchWorks(request) }
        _ = try await client.searchWorks(request)
        #expect(server.requests.count == 2)
    }

    @Test func anUndecodableReplyIsNotCached() async throws {
        let replies = Counter(0)
        let server = URLProtocolStub.Server { [page] _ in replies.next() == 0 ? .json("not json") : .json(page) }
        let client = search(server, quota(), cache: true)
        await #expect(throws: NetworkFailure.malformedResponse) { try await client.searchWorks(request) }
        _ = try await client.searchWorks(request)
        #expect(server.requests.count == 2)
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
