import Foundation
import HashiyaNetwork
import HashiyaTesting
import os
import Testing

struct OpenAlexSearchClientTests {
    private let page = Fixtures.string("works_page.json")

    private func request(
        search: String = "bert",
        filter: String? = nil,
        sort: String? = nil,
        cursor: String = "*"
    ) -> WorksSearchRequest {
        WorksSearchRequest(search: search, filter: filter, sort: sort, cursor: cursor, perPage: 25)
    }

    private func client(
        _ server: URLProtocolStub.Server,
        builtInKey: String? = nil,
        userKey: String? = nil,
        log: @escaping @Sendable (String) -> Void = { _ in }
    ) -> OpenAlexSearchClient {
        OpenAlexSearchClient(session: server.session, builtInKey: builtInKey, userKeySource: FixedUserAPIKeySource(userKey), log: log)
    }

    @Test func sendsEveryQueryParameterToWorks() async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        _ = try await client(server).searchWorks(
            request(search: "bert", filter: "publication_year:>2019,is_oa:true", sort: "cited_by_count:desc")
        )

        let url = try #require(server.requests.last?.url)
        #expect(url.scheme == "https")
        #expect(url.host() == "api.openalex.org")
        #expect(url.path() == "/works")
        #expect(server.lastQuery == [
            "search": "bert",
            "filter": "publication_year:>2019,is_oa:true",
            "sort": "cited_by_count:desc",
            "per_page": "25",
            "cursor": "*",
            "select": "id,doi,display_name,publication_year,primary_location,authorships,cited_by_count,open_access,best_oa_location,abstract_inverted_index",
        ])
    }

    @Test func omitsFilterAndSortWhenNil() async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        _ = try await client(server).searchWorks(request())

        #expect(server.lastQuery["filter"] == nil)
        #expect(server.lastQuery["sort"] == nil)
        #expect(server.lastQuery.keys.sorted() == ["cursor", "per_page", "search", "select"])
    }

    @Test func passesTheCursorThrough() async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        _ = try await client(server).searchWorks(request(cursor: "IlsxMDAuMCwgJ1czMTc3ODI4OTA5J10i"))

        #expect(server.lastQuery["cursor"] == "IlsxMDAuMCwgJ1czMTc3ODI4OTA5J10i")
    }

    @Test(arguments: ["تعلم الآلة: \"deep\", 100% & more", "C++", "a=b&c=d", "x+y z"])
    func searchTextRoundTripsExactly(text: String) async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        _ = try await client(server).searchWorks(request(search: text))

        #expect(server.lastQuery["search"] == text)
    }

    @Test func plusAndAmpersandArePercentEncoded() async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        _ = try await client(server).searchWorks(request(search: "C++ & more"))

        let query = try #require(server.requests.last?.url?.query(percentEncoded: true))
        #expect(query.contains("search=C%2B%2B%20%26%20more"))
    }

    @Test func usesTheBuiltInKey() async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        _ = try await client(server, builtInKey: "built-in-key").searchWorks(request())

        #expect(server.lastQuery["api_key"] == "built-in-key")
    }

    @Test func theUserKeyOverridesTheBuiltInKey() async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        _ = try await client(server, builtInKey: "built-in-key", userKey: "user-key").searchWorks(request())

        #expect(server.lastQuery["api_key"] == "user-key")
    }

    @Test func aBlankUserKeyFallsBackToTheBuiltInKey() async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        _ = try await client(server, builtInKey: "built-in-key", userKey: "   ").searchWorks(request())

        #expect(server.lastQuery["api_key"] == "built-in-key")
    }

    @Test(arguments: [nil, "", "  "] as [String?])
    func sendsNoKeyWhenThereIsNone(builtInKey: String?) async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        _ = try await client(server, builtInKey: builtInKey).searchWorks(request())

        #expect(server.lastQuery["api_key"] == nil)
    }

    @Test func parsesTheResponse() async throws {
        let server = URLProtocolStub.Server(always: .json(page))
        let response = try await client(server).searchWorks(request())

        #expect(response.meta.count == 48210)
        #expect(response.results.count == 2)
    }

    @Test func rejectedUserKeyReportsThatTheUserKeyWasUsed() async {
        let server = URLProtocolStub.Server(always: .status(401))
        await #expect(throws: NetworkFailure.http(code: 401, usedUserKey: true)) {
            try await client(server, builtInKey: "built-in-key", userKey: "user-key").searchWorks(request())
        }
    }

    @Test func rejectedBuiltInKeyReportsThatTheUserKeyWasNotUsed() async {
        let server = URLProtocolStub.Server(always: .status(403))
        await #expect(throws: NetworkFailure.http(code: 403, usedUserKey: false)) {
            try await client(server, builtInKey: "built-in-key").searchWorks(request())
        }
    }

    @Test(arguments: [429, 400, 500, 503])
    func otherStatusesKeepTheirCode(code: Int) async {
        let server = URLProtocolStub.Server(always: .status(code))
        await #expect(throws: NetworkFailure.http(code: code, usedUserKey: false)) {
            try await client(server).searchWorks(request())
        }
    }

    @Test(arguments: ["{\"unexpected\": true}", "not json", "{\"meta\": {\"count\": 1}, \"results\": [{\"doi\": null}]}"])
    func anUnreadableBodyIsMalformed(body: String) async {
        let server = URLProtocolStub.Server(always: .json(body))
        await #expect(throws: NetworkFailure.malformedResponse) {
            try await client(server).searchWorks(request())
        }
    }

    @Test(arguments: [URLError.Code.notConnectedToInternet, .timedOut, .cannotFindHost, .networkConnectionLost])
    func transportFailuresAreConnectivity(code: URLError.Code) async {
        let server = URLProtocolStub.Server(always: .failure(code))
        await #expect(throws: NetworkFailure.connectivity) {
            try await client(server).searchWorks(request())
        }
    }

    @Test func aCancelledTaskThrowsCancellationError() async {
        let server = URLProtocolStub.Server(always: .failure(.cancelled))
        await #expect(throws: CancellationError.self) {
            try await client(server).searchWorks(request())
        }
    }

    @Test func theKeyNeverAppearsInLogsOrErrors() async {
        let key = "secret-key-1234"
        let lines = OSAllocatedUnfairLock<[String]>(initialState: [])
        let log: @Sendable (String) -> Void = { line in lines.withLock { $0.append(line) } }
        var descriptions: [String] = []

        for reply in [URLProtocolStub.Reply.json(page), .status(401), .status(500), .json("{}"), .failure(.notConnectedToInternet)] {
            let server = URLProtocolStub.Server(always: reply)
            do {
                _ = try await client(server, userKey: key, log: log).searchWorks(request())
            } catch {
                descriptions += [String(describing: error), String(reflecting: error), error.localizedDescription]
            }
        }

        let logged = lines.withLock { $0 }
        #expect(logged.count == 5)
        #expect(logged.allSatisfy { $0.contains("api_key=██") })
        #expect(logged.first == "GET /works?search=bert&per_page=25&cursor=%2A&select=id%2Cdoi%2Cdisplay_name%2Cpublication_year%2Cprimary_location%2Cauthorships%2Ccited_by_count%2Copen_access%2Cbest_oa_location%2Cabstract_inverted_index&api_key=██ → 200")
        #expect(descriptions.count == 12)
        for text in logged + descriptions {
            #expect(!text.contains(key))
        }
    }
}
