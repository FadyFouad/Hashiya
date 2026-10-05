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
