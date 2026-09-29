import HashiyaNetwork
import Testing

struct OpenAlexSessionTests {
    @Test func failsFastOfflineWithTheSpecTimeouts() {
        let configuration = OpenAlexSession.makeConfiguration()
        #expect(configuration.waitsForConnectivity == false)
        #expect(configuration.timeoutIntervalForRequest == 10)
        #expect(configuration.timeoutIntervalForResource == 20)
    }

    @Test func networkFailureDescriptionIsTheCaseNameOnly() {
        #expect(String(describing: NetworkFailure.http(code: 401, usedUserKey: true)) == "http")
        #expect(String(describing: NetworkFailure.connectivity) == "connectivity")
        #expect(String(describing: NetworkFailure.malformedResponse) == "malformedResponse")
        #expect(String(describing: NetworkFailure.unknown) == "unknown")
    }
}
