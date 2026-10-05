/// A work's primary topic as OpenAlex ids ("https://openalex.org/subfields/1702", …/fields/17, …/domains/3). Only these
/// ids reach the classifier — never titles, topic names or the query.
public struct TopicIDs: Equatable, Sendable {
    public let subfield: String?
    public let field: String?
    public let domain: String?

    public init(subfield: String?, field: String?, domain: String?) {
        self.subfield = subfield
        self.field = field
        self.domain = domain
    }

    var isEmpty: Bool { subfield == nil && field == nil && domain == nil }
}

extension ResearchCategory {
    private static let subfields: [Int: ResearchCategory] = [
        1702: .ai, 1707: .computerVision, 1703: .theory, 1705: .networks, 1708: .systems,
        1712: .software, 1709: .hci, 1710: .informationSystems, 1704: .graphics, 1711: .signalProcessing,
    ]
    private static let fields: [Int: ResearchCategory] = [17: .csOther, 26: .mathematics, 22: .engineering]
    private static let domains: [Int: ResearchCategory] = [3: .physicalSciences, 1: .lifeSciences, 2: .socialSciences, 4: .healthSciences]

    /// One work's category: its subfield, else its field, else its domain; anything unrecognised is `unknown`.
    public static func of(_ ids: TopicIDs) -> ResearchCategory {
        if let number = number(ids.subfield, kind: "subfields"), let category = subfields[number] { return category }
        if let number = number(ids.field, kind: "fields"), let category = fields[number] { return category }
        if let number = number(ids.domain, kind: "domains"), let category = domains[number] { return category }
        return .unknown
    }

    /// A search's category: of the first 10 results that have a topic, the most common category if at least 3 were
    /// mapped and it has at least 40% of them with no tie; otherwise `unknown`.
    public static func classify(_ topics: [TopicIDs]) -> ResearchCategory {
        let mapped = Array(topics.lazy.filter { !$0.isEmpty }.prefix(10).map(of))
        guard mapped.count >= 3 else { return .unknown }
        var votes: [ResearchCategory: Int] = [:]
        for category in mapped { votes[category, default: 0] += 1 }
        let ranked = votes.sorted { $0.value > $1.value }
        guard let top = ranked.first, ranked.dropFirst().first?.value != top.value,
              top.value * 5 >= mapped.count * 2 else { return .unknown }
        return top.key
    }

    /// The number at the end of "https://openalex.org/<kind>/<number>"; anything else is nil.
    private static func number(_ id: String?, kind: String) -> Int? {
        guard let id, id.hasPrefix("https://openalex.org/\(kind)/") else { return nil }
        return Int(id.dropFirst("https://openalex.org/\(kind)/".count))
    }
}
