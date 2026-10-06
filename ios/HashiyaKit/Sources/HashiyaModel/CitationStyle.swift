/// How a citation or a reference list is written; the raw value is what the preference and analytics store.
public enum CitationStyle: String, Sendable, CaseIterable {
    case apa, ieee, bibtex

    /// APA when nothing, or something unknown, is stored.
    public init(storedValue: String?) {
        self = storedValue.flatMap(CitationStyle.init(rawValue:)) ?? .apa
    }
}
