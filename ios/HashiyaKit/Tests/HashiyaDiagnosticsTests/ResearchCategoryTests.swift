import HashiyaDiagnostics
import Testing

struct ResearchCategoryTests {
    private func topic(subfield: Int? = nil, field: Int? = nil, domain: Int? = nil) -> TopicIDs {
        TopicIDs(
            subfield: subfield.map { "https://openalex.org/subfields/\($0)" },
            field: field.map { "https://openalex.org/fields/\($0)" },
            domain: domain.map { "https://openalex.org/domains/\($0)" }
        )
    }

    @Test(arguments: [
        (1702, ResearchCategory.ai), (1707, .computerVision), (1703, .theory), (1705, .networks), (1708, .systems),
        (1712, .software), (1709, .hci), (1710, .informationSystems), (1704, .graphics), (1711, .signalProcessing),
    ])
    func everyComputerScienceSubfieldMaps(subfield: Int, category: ResearchCategory) {
        #expect(ResearchCategory.of(topic(subfield: subfield, field: 17, domain: 3)) == category)
    }

    @Test func anotherComputerScienceSubfieldIsCSOther() {
        #expect(ResearchCategory.of(topic(subfield: 1706, field: 17, domain: 3)) == .csOther)
    }

    @Test(arguments: [
        (26, 3, ResearchCategory.mathematics), (22, 3, .engineering), (31, 3, .physicalSciences),
        (13, 1, .lifeSciences), (33, 2, .socialSciences), (27, 4, .healthSciences),
    ])
    func fieldsAndDomainsMap(topicField: Int, domain: Int, category: ResearchCategory) {
        #expect(ResearchCategory.of(topic(subfield: topicField * 100 + 1, field: topicField, domain: domain)) == category)
    }

    @Test(arguments: [
        TopicIDs(subfield: "https://openalex.org/subfields/abc", field: nil, domain: nil),
        TopicIDs(subfield: "1702", field: nil, domain: nil),
        TopicIDs(subfield: nil, field: nil, domain: "https://openalex.org/domains/9"),
        TopicIDs(subfield: nil, field: nil, domain: nil),
    ])
    func malformedOrUnknownIdsAreUnknown(ids: TopicIDs) {
        #expect(ResearchCategory.of(ids) == .unknown)
    }

    @Test func aMalformedSubfieldFallsThroughToItsField() {
        #expect(ResearchCategory.of(TopicIDs(subfield: "x", field: "https://openalex.org/fields/26", domain: nil)) == .mathematics)
    }

    @Test func theMostCommonCategoryWinsWithAtLeast40Percent() {
        let ai = topic(subfield: 1702, field: 17, domain: 3)
        let vision = topic(subfield: 1707, field: 17, domain: 3)
        let theory = topic(subfield: 1703, field: 17, domain: 3)
        #expect(ResearchCategory.classify([ai, ai, vision, theory, ai]) == .ai)            // 60%
        #expect(ResearchCategory.classify([ai, ai, vision, vision, theory]) == .unknown)    // tie
        #expect(ResearchCategory.classify([ai, ai, vision, theory, topic(subfield: 1705, field: 17, domain: 3)]) == .ai)  // exactly 40%
        #expect(ResearchCategory.classify([ai, vision, theory]) == .unknown)                // 33%
        #expect(ResearchCategory.classify([ai, ai]) == .unknown)                            // fewer than 3
        #expect(ResearchCategory.classify([]) == .unknown)
    }

    @Test func resultsWithoutATopicAreSkippedAndOnlyTheFirstTenCount() {
        let ai = topic(subfield: 1702, field: 17, domain: 3)
        let vision = topic(subfield: 1707, field: 17, domain: 3)
        let none = TopicIDs(subfield: nil, field: nil, domain: nil)
        #expect(ResearchCategory.classify([none, none, ai, ai, ai]) == .ai)
        // The first ten mapped are vision; the ai ones after them don't count.
        #expect(ResearchCategory.classify(Array(repeating: vision, count: 10) + Array(repeating: ai, count: 15)) == .computerVision)
    }
}
