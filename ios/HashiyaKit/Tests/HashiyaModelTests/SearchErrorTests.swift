import HashiyaModel
import Testing

struct SearchErrorTests {
    @Test func casesAreDistinct() {
        let all: [SearchError] = [.offline, .invalidUserKey, .rateLimited, .serviceUnavailable, .unexpected]
        #expect(Set(all.map { "\($0)" }).count == 5)
    }
}
