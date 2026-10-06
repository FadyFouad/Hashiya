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
