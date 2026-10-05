import Foundation
import HashiyaModel
import Testing

struct SearchErrorTests {
    @Test func casesAreDistinct() {
        let all: [SearchError] = [.offline, .invalidUserKey, .rateLimited, .serviceUnavailable, .unexpected, .dailyLimit(resetAt: .distantPast)]
        #expect(Set(all.map { "\($0)" }).count == 6)
    }
}
