import HashiyaDiagnostics
import Testing

struct AnalyticsSwitchTests {
    private struct Sent: Equatable {
        let property: AnalyticsProperty
        let value: String
    }

    @Test func startsOffAndSendsNoPropertyWhileOff() {
        let analyticsSwitch = AnalyticsSwitch()
        var sent: [Sent] = []

        analyticsSwitch.setProperty(.language, LanguageKey.ar) { sent.append(Sent(property: $0, value: $1)) }

        #expect(!analyticsSwitch.isOn)
        #expect(sent.isEmpty)
    }

    @Test func sendsPropertiesWhileOn() {
        let analyticsSwitch = AnalyticsSwitch()
        var sent: [Sent] = []
        analyticsSwitch.setEnabled(true, apply: { _ in }) { sent.append(Sent(property: $0, value: $1)) }

        analyticsSwitch.setProperty(.hasOwnKey, YesNo.yes) { sent.append(Sent(property: $0, value: $1)) }

        #expect(analyticsSwitch.isOn)
        #expect(sent == [Sent(property: .hasOwnKey, value: "yes")])
    }

    @Test func turningOnAgainSendsTheLastValueOfEachProperty() {
        let analyticsSwitch = AnalyticsSwitch()
        var sent: [Sent] = []
        let record = { (property: AnalyticsProperty, value: String) in sent.append(Sent(property: property, value: value)) }
        analyticsSwitch.setEnabled(true, apply: { _ in }, send: record)
        analyticsSwitch.setProperty(.language, LanguageKey.en, send: record)
        analyticsSwitch.setProperty(.librarySizeBucket, LibrarySizeBucket.upTo50, send: record)
        analyticsSwitch.setEnabled(false, apply: { _ in }, send: record)
        analyticsSwitch.setProperty(.language, LanguageKey.ar, send: record)
        sent = []

        var applied: [Bool] = []
        analyticsSwitch.setEnabled(true, apply: { applied.append($0) }, send: record)

        #expect(applied == [true])
        #expect(Set(sent.map { "\($0.property.rawValue)=\($0.value)" }) == ["language=ar", "library_size_bucket=1-50"])
    }

    @Test func turningOffSendsNothing() {
        let analyticsSwitch = AnalyticsSwitch()
        var sent: [Sent] = []
        let record = { (property: AnalyticsProperty, value: String) in sent.append(Sent(property: property, value: value)) }
        analyticsSwitch.setProperty(.hasOwnKey, YesNo.no, send: record)

        var applied: [Bool] = []
        analyticsSwitch.setEnabled(false, apply: { applied.append($0) }, send: record)

        #expect(applied == [false])
        #expect(sent.isEmpty)
        #expect(!analyticsSwitch.isOn)
    }
}
