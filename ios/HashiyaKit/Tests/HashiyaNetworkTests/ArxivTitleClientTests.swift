import Foundation
import HashiyaNetwork
import HashiyaTesting
import Testing

struct ArxivTitleClientTests {
    private let bertTitle = "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding"

    private func client(_ server: URLProtocolStub.Server) -> ArxivTitleClient {
        ArxivTitleClient(session: server.session)
    }

    @Test func requestsTheIDWithTheUserAgentAndNoAPIKey() async throws {
        let server = URLProtocolStub.Server(always: .json(Fixtures.string("arxiv_bert.xml")))

        _ = try await client(server).title(id: "hep-th/9901001")

        let request = try #require(server.requests.last)
        #expect(request.url?.host() == "export.arxiv.org")
        #expect(request.url?.path() == "/api/query")
        #expect(server.lastQuery == ["id_list": "hep-th/9901001"])
        #expect(request.value(forHTTPHeaderField: "User-Agent") == "Hashiya-iOS (https://github.com/FadyFouad/Hashiya)")
    }

    @Test func readsTheEntryTitleWithCollapsedWhitespace() async throws {
        let server = URLProtocolStub.Server(always: .json(Fixtures.string("arxiv_bert.xml")))
        #expect(try await client(server).title(id: "1810.04805") == bertTitle)
    }

    @Test(arguments: ["arxiv_empty.xml", "arxiv_error.xml"])
    func anEmptyFeedOrAnErrorEntryMeansNoSuchPaper(fixture: String) async throws {
        let server = URLProtocolStub.Server(always: .json(Fixtures.string(fixture)))
        #expect(try await client(server).title(id: "2401.99999") == nil)
    }

    @Test func aServerErrorIsAnHTTPFailure() async {
        let server = URLProtocolStub.Server(always: .status(503, body: Data("down".utf8)))
        await #expect(throws: NetworkFailure.http(code: 503, usedUserKey: false)) {
            try await client(server).title(id: "1810.04805")
        }
    }

    @Test func unreachableIsAConnectivityFailure() async {
        let server = URLProtocolStub.Server(always: .failure(.cannotConnectToHost))
        await #expect(throws: NetworkFailure.connectivity) {
            try await client(server).title(id: "1810.04805")
        }
    }

    @Test func notAFeedIsMalformed() async {
        let server = URLProtocolStub.Server(always: .json("<html><body>maintenance</body></html>"))
        await #expect(throws: NetworkFailure.malformedResponse) {
            try await client(server).title(id: "1810.04805")
        }
    }

    @Test(.timeLimit(.minutes(1)))
    func cancellingTheCallingTaskStopsTheRequest() async {
        let server = URLProtocolStub.Server(always: .stall)
        let client = client(server)
        let lookup = Task { try await client.title(id: "1810.04805") }
        while server.requests.isEmpty {
            try? await Task.sleep(for: .milliseconds(5))
        }

        lookup.cancel()

        await #expect(throws: CancellationError.self) { try await lookup.value }
    }

    @Test func decodesXMLEntities() throws {
        let xml = """
            <feed xmlns="http://www.w3.org/2005/Atom"><entry><id>http://arxiv.org/abs/1</id>
            <title>Graphs &amp; Networks: &lt;A&gt; &#8211; &#x3B1; &quot;study&quot;</title></entry></feed>
            """
        #expect(try parseArxivTitle(xml) == "Graphs & Networks: <A> – α \"study\"")
    }

    @Test func anEntryWithoutATitleIsMalformed() {
        let xml = #"<feed xmlns="http://www.w3.org/2005/Atom"><entry><id>http://arxiv.org/abs/1</id></entry></feed>"#
        #expect(throws: NetworkFailure.malformedResponse) { try parseArxivTitle(xml) }
    }
}
