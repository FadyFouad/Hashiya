import HashiyaData
import HashiyaDiagnostics
import HashiyaModel
import HashiyaNetwork
import HashiyaTesting
import Testing

struct OpenAlexSearchRepositoryTests {
    private func response(ids: [String], count: Int64 = 48_210, next: String?) -> NetworkWorksResponse {
        NetworkWorksResponse(
            meta: NetworkMeta(count: count, nextCursor: next),
            results: ids.map { NetworkWork(id: "https://openalex.org/\($0)", displayName: "Title \($0)") }
        )
    }

    @Test func theFirstPageReportsTheTotalAndTheNextCursor() async throws {
        let service = FakeOpenAlexSearchService(replies: [.success(response(ids: ["W1", "W2"], next: "c2"))])
        let page = try await OpenAlexSearchRepository(service: service).searchPage(SearchQuery(text: "bert"), cursor: nil)

        #expect(page.papers.map(\.openAlexID) == ["W1", "W2"])
        #expect(page.totalCount == 48_210)
        #expect(page.nextCursor == "c2")
        #expect(service.requests.map(\.cursor) == ["*"])
    }

    @Test func theNextPageUsesThePreviousCursor() async throws {
        let service = FakeOpenAlexSearchService(replies: [
            .success(response(ids: ["W1"], next: "c2")),
            .success(response(ids: ["W2"], next: "c3")),
        ])
        let repository = OpenAlexSearchRepository(service: service)
        let first = try await repository.searchPage(SearchQuery(text: "bert"), cursor: nil)
        _ = try await repository.searchPage(SearchQuery(text: "bert"), cursor: first.nextCursor)

        #expect(service.requests.map(\.cursor) == ["*", "c2"])
        #expect(service.requests.map(\.search) == ["bert", "bert"])
    }

    @Test func endsWhenThereIsNoNextCursor() async throws {
        let service = FakeOpenAlexSearchService(replies: [.success(response(ids: ["W1"], next: nil))])
        let page = try await OpenAlexSearchRepository(service: service).searchPage(SearchQuery(text: "bert"), cursor: nil)
        #expect(page.nextCursor == nil)
    }

    @Test func endsWhenAPageIsEmptyEvenWithACursor() async throws {
        let service = FakeOpenAlexSearchService(replies: [.success(response(ids: [], count: 0, next: "c2"))])
        let page = try await OpenAlexSearchRepository(service: service).searchPage(SearchQuery(text: "bert"), cursor: nil)
        #expect(page.papers.isEmpty)
        #expect(page.nextCursor == nil)
    }

    @Test(arguments: [
        (NetworkFailure.connectivity, SearchError.offline),
        (.http(code: 401, usedUserKey: true), .invalidUserKey),
        (.malformedResponse, .unexpected),
    ])
    func networkFailuresBecomeSearchErrors(failure: NetworkFailure, expected: SearchError) async {
        let service = FakeOpenAlexSearchService(replies: [.failure(failure)])
        await #expect(throws: expected) {
            try await OpenAlexSearchRepository(service: service).searchPage(SearchQuery(text: "bert"), cursor: nil)
        }
    }

    @Test func cancellationPassesThrough() async {
        let service = FakeOpenAlexSearchService(replies: [.failure(CancellationError())])
        await #expect(throws: CancellationError.self) {
            try await OpenAlexSearchRepository(service: service).searchPage(SearchQuery(text: "bert"), cursor: nil)
        }
    }

    @Test func aFirstPageCarriesTheRouteAndTheResearchCategory() async throws {
        let ai = NetworkTopic(subfieldID: "https://openalex.org/subfields/1702", fieldID: "https://openalex.org/fields/17", domainID: "https://openalex.org/domains/3")
        var response = NetworkWorksResponse(meta: NetworkMeta(count: 3, nextCursor: nil), results: (1...3).map { NetworkWork(id: "https://openalex.org/W\($0)", primaryTopic: ai) })
        response.route = .keyless
        let service = FakeOpenAlexSearchService(replies: [.success(response)])
        let page = try await OpenAlexSearchRepository(service: service).searchPage(SearchQuery(text: "bert"), cursor: nil)
        #expect(page.route == .keyless)
        #expect(page.category == .ai)
    }
}
