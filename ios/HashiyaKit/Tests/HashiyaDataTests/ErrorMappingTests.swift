import HashiyaData
import HashiyaModel
import HashiyaNetwork
import Testing

struct ErrorMappingTests {
    @Test(arguments: [
        (NetworkFailure.connectivity, SearchError.offline),
        (.http(code: 401, usedUserKey: true), .invalidUserKey),
        (.http(code: 403, usedUserKey: true), .invalidUserKey),
        (.http(code: 401, usedUserKey: false), .serviceUnavailable),
        (.http(code: 403, usedUserKey: false), .serviceUnavailable),
        (.http(code: 429, usedUserKey: true), .rateLimited),
        (.http(code: 429, usedUserKey: false), .rateLimited),
        (.http(code: 500, usedUserKey: false), .serviceUnavailable),
        (.http(code: 503, usedUserKey: true), .serviceUnavailable),
        (.http(code: 599, usedUserKey: false), .serviceUnavailable),
        (.http(code: 400, usedUserKey: false), .unexpected),
        (.http(code: 404, usedUserKey: true), .unexpected),
        (.http(code: 302, usedUserKey: false), .unexpected),
        (.malformedResponse, .unexpected),
        (.unknown, .unexpected),
    ])
    func mapsEveryFailure(failure: NetworkFailure, expected: SearchError) {
        #expect(failure.asSearchError() == expected)
    }
}
