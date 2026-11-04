@testable import HashiyaData
import Foundation
import HashiyaNetwork
import HashiyaTesting
import Testing

/// Ported from Android's `OpenAlexDoiMatcherTest.kt`.
struct DOIMatcherTests {
    private func work(_ doi: String, id: String? = nil) -> NetworkWork {
        NetworkWork(id: "https://openalex.org/" + (id ?? "W" + doi.replacingOccurrences(of: "/", with: "")), doi: "https://doi.org/" + doi, displayName: "Title of \(doi)")
    }

    private func dois(_ count: Int) -> [String] { (1...count).map { "10.1/\($0)" } }

    @Test func doisGoFiftyToAnImportRequestWithPerPageFifty() async {
        let service = FakeOpenAlexLookupService(found: dois(120).map { work($0) })
        let result = await DOIMatcher(openAlex: service, pause: { _ in }).match(dois(120))
        #expect(service.worksRequests.count == 3)
        #expect(service.worksRequests[0] == .init(filter: "doi:" + dois(50).joined(separator: "|"), perPage: 50))
        #expect(service.worksRequests[2].filter == "doi:" + dois(120).dropFirst(100).joined(separator: "|"))
        #expect(Set(result.found.keys) == Set(dois(120)))
        #expect(result.notFound.isEmpty && result.notChecked.isEmpty)
    }

    @Test func worksMapBackByNormalisedDOIAndTheFirstWins() async {
        let upper = NetworkWork(id: "https://openalex.org/W1", doi: "https://doi.org/10.1/A", displayName: "Upper case")
        let service = FakeOpenAlexLookupService(found: [upper, work("10.1/a", id: "W2"), work("10.9/unasked")])
        let result = await DOIMatcher(openAlex: service, pause: { _ in }).match(["10.1/a", "10.1/b"])
        #expect(result.found["10.1/a"]?.openAlexID == "W1")
        #expect(result.notFound == ["10.1/b"])
    }

    @Test func aDOIThatCantGoInAFilterIsLookedUpAlone() async {
        let service = FakeOpenAlexLookupService(works: ["doi:10.1/a,b": work("10.1/a,b")])
        let result = await DOIMatcher(openAlex: service, pause: { _ in }).match(["10.1/a,b"])
        #expect(service.workRequests == ["doi:10.1/a,b"])
        #expect(service.worksRequests.isEmpty)
        #expect(Set(result.found.keys) == ["10.1/a,b"])
    }

    @Test func thereIsNoCapOnRequests() async {
        let service = FakeOpenAlexLookupService()
        let result = await DOIMatcher(openAlex: service, pause: { _ in }).match(dois(2_050))
        #expect(service.worksRequests.count == 41)
        #expect(result.notFound.count == 2_050)
    }

    @Test func theDailyLimitSwitchesToFreeSingleLookups() async {
        let service = FakeOpenAlexLookupService(works: ["doi:10.1/2": work("10.1/2")])
        service.setWorksFailure(.dailyLimit(resetAt: Date(timeIntervalSince1970: 0)))
        let result = await DOIMatcher(openAlex: service, pause: { _ in }).match(dois(60))
        #expect(service.worksRequests.count == 1)
        #expect(service.workRequests == dois(60).map { "doi:" + $0 })
        #expect(Set(result.found.keys) == ["10.1/2"])
        #expect(result.notFound.count == 59)
        #expect(result.notChecked.isEmpty)
    }

    @Test func noConnectionStopsEveryLaterRequest() async {
        let service = FakeOpenAlexLookupService()
        service.setWorksFailure(.connectivity)
        let result = await DOIMatcher(openAlex: service, pause: { _ in }).match(dois(120) + ["10.1/a b"])
        #expect(service.worksRequests.count == 1)
        #expect(service.workRequests.isEmpty)
        #expect(result.notChecked == Set(dois(120) + ["10.1/a b"]))
    }

    @Test func anyOtherFailureSkipsThatBatchOnly() async {
        let service = FakeOpenAlexLookupService()
        service.setWorksFailure(.http(code: 503, usedUserKey: false))
        let result = await DOIMatcher(openAlex: service, pause: { _ in }).match(dois(120))
        #expect(service.worksRequests.count == 3)
        #expect(result.notChecked == Set(dois(120)))
    }

    @Test func requestsAreSpacedButTheFirstIsNot() async {
        let pauses = PauseLog()
        let service = FakeOpenAlexLookupService()
        _ = await DOIMatcher(openAlex: service, pause: { await pauses.add($0) }).match(dois(120))
        #expect(await pauses.values == [DOIMatcher.requestSpacing, DOIMatcher.requestSpacing])
    }
}

private actor PauseLog {
    var values: [Duration] = []
    func add(_ value: Duration) { values.append(value) }
}
