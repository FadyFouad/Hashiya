import Foundation
import HashiyaNetwork
import HashiyaTesting
import Testing

struct OpenAlexLimitsTests {
    private func parse(_ json: String) -> OpenAlexLimits {
        OpenAlexLimits.parse(try! JSONSerialization.jsonObject(with: Data(json.utf8), options: [.fragmentsAllowed]))
    }

    @Test func defaultsAreSixtyCallsEightPagesAndNoProxy() {
        #expect(OpenAlexLimits.defaults == OpenAlexLimits(dailyDeviceCalls: 60, maxPagesPerQuery: 8, baseURL: nil))
    }

    @Test func readsEveryValidField() {
        let limits = parse(#"{"dailyDeviceCalls": 0, "maxPagesPerQuery": 40, "baseUrl": "https://proxy.example/openalex"}"#)
        #expect(limits == OpenAlexLimits(dailyDeviceCalls: 0, maxPagesPerQuery: 40, baseURL: URL(string: "https://proxy.example/openalex")))
    }

    @Test(arguments: ["-1", "1001", #""60""#, "12.5", "true", "null"])
    func anInvalidCallCountFallsBackAlone(value: String) {
        let limits = parse(#"{"dailyDeviceCalls": \#(value), "maxPagesPerQuery": 3}"#)
        #expect(limits.dailyDeviceCalls == 60)
        #expect(limits.maxPagesPerQuery == 3)
    }

    @Test func aMissingFieldFallsBackAlone() {
        #expect(parse(#"{"maxPagesPerQuery": 3}"#) == OpenAlexLimits(dailyDeviceCalls: 60, maxPagesPerQuery: 3, baseURL: nil))
    }

    @Test(arguments: [#"{"maxPagesPerQuery": 0}"#, #"{"maxPagesPerQuery": 41}"#, #"{"maxPagesPerQuery": "8"}"#])
    func anInvalidPageLimitFallsBack(json: String) {
        #expect(parse(json).maxPagesPerQuery == 8)
    }

    @Test(arguments: [
        #"{"baseUrl": "http://proxy.example"}"#, #"{"baseUrl": "proxy.example"}"#, #"{"baseUrl": "https://"}"#,
        #"{"baseUrl": 5}"#, #"{"baseUrl": null}"#,
    ])
    func aBaseURLThatIsNotHTTPSIsIgnored(json: String) {
        #expect(parse(json).baseURL == nil)
    }

    @Test(arguments: ["[]", "5", #""x""#, "null"])
    func aSectionThatIsNotAnObjectMeansAllDefaults(json: String) {
        #expect(parse(json) == .defaults)
    }

    @Test func theStoreKeepsTheLastSavedLimits() {
        let defaults = TestDefaults.make()
        #expect(OpenAlexLimitsStore(defaults: defaults).limits == .defaults)
        let saved = OpenAlexLimits(dailyDeviceCalls: 5, maxPagesPerQuery: 2, baseURL: URL(string: "https://proxy.example"))
        OpenAlexLimitsStore(defaults: defaults).save(saved)
        #expect(OpenAlexLimitsStore(defaults: defaults).limits == saved)
    }

    @Test func unreadableStoredLimitsMeanDefaults() {
        let defaults = TestDefaults.make()
        defaults.set(Data("nope".utf8), forKey: OpenAlexLimitsStore.key)
        #expect(OpenAlexLimitsStore(defaults: defaults).limits == .defaults)
    }
}
