import Foundation
import HashiyaNetwork
import HashiyaTesting
import Testing

struct OpenAlexLookupClientTests {
    private func client(_ server: URLProtocolStub.Server) -> OpenAlexLookupClient {
        OpenAlexLookupClient(session: server.session, builtInKey: "built-in-key", userKeySource: FixedUserAPIKeySource(nil), log: { _ in })
    }

    /// The request's path segments, each percent-decoded (`URL.path` would decode "%2F" before splitting).
    private func pathSegments(_ server: URLProtocolStub.Server) -> [String] {
        guard let url = server.requests.last?.url,
              let path = URLComponents(url: url, resolvingAgainstBaseURL: false)?.percentEncodedPath else { return [] }
        return path.split(separator: "/").map { String($0).removingPercentEncoding ?? String($0) }
    }

    @Test func workRequestsTheWorkWithSelectedFields() async throws {
        let server = URLProtocolStub.Server(always: .json(Fixtures.string("work.json")))

        let work = try await client(server).work(id: "doi:10.1038/nature14539")

        #expect(pathSegments(server) == ["works", "doi:10.1038/nature14539"])
        #expect(server.lastQuery["select"] == OpenAlexSearchClient.selectFields)
        #expect(server.lastQuery["api_key"] == "built-in-key")
        #expect(work?.id == "https://openalex.org/W2919115771")
        #expect(work?.displayName == "Deep learning")
    }

    @Test func selectedFieldsIncludeTypeAndBiblio() {
        let fields = OpenAlexSearchClient.selectFields.split(separator: ",").map(String.init)
        #expect(fields.contains("type"))
        #expect(fields.contains("biblio"))
    }

    @Test(arguments: [404, 400])
    func notFoundAndBadRequestAreNil(code: Int) async throws {
        let server = URLProtocolStub.Server(always: .status(code, body: Data("{}".utf8)))
        #expect(try await client(server).work(id: "doi:10.9999/does-not-exist") == nil)
    }

    @Test func otherFailuresThrow() async {
        let server = URLProtocolStub.Server(always: .status(429))
        await #expect(throws: NetworkFailure.http(code: 429, usedUserKey: false)) {
            try await client(server).work(id: "doi:10.1038/nature14539")
        }
    }

    @Test(arguments: [
        "doi:10.1002/(sici)1099-1212(199901/02)9:1<8::aid-oa453>3.0.co;2-z",
        "doi:10.1234/abc#1",
    ])
    func idsSurvivePathEncoding(id: String) async throws {
        let server = URLProtocolStub.Server(always: .json(Fixtures.string("work.json")))

        _ = try await client(server).work(id: id)

        #expect(pathSegments(server) == ["works", id])
    }

    @Test func worksSendsTheFilterAndPageSize() async throws {
        let filter = "locations.landing_page_url:http://arxiv.org/abs/1810.04805|https://arxiv.org/abs/1810.04805"
        let server = URLProtocolStub.Server(always: .json(Fixtures.string("works_page.json")))

        let response = try await client(server).works(filter: filter, perPage: 2)

        #expect(server.requests.last?.url?.path() == "/works")
        #expect(server.lastQuery["filter"] == filter)
        #expect(server.lastQuery["per_page"] == "2")
        #expect(server.lastQuery["select"] == OpenAlexSearchClient.selectFields)
        #expect(server.lastQuery["api_key"] == "built-in-key")
        #expect(response.results.count == 2)
    }

    @Test func worksFailuresThrow() async {
        let server = URLProtocolStub.Server(always: .failure(.cannotConnectToHost))
        await #expect(throws: NetworkFailure.connectivity) {
            try await client(server).works(filter: "locations.landing_page_url:http://arxiv.org/abs/1", perPage: 2)
        }
    }
}
