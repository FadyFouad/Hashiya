/// A piece of a citation: plain or italic text.
public struct Run: Equatable, Sendable {
    public var text: String
    public var italic: Bool

    public init(_ text: String, italic: Bool = false) {
        self.text = text
        self.italic = italic
    }
}

/// A formatted citation as runs, so each output (plain, HTML, RTF) can show the italics its own way.
public struct StyledCitation: Equatable, Sendable {
    public var runs: [Run]

    public init(_ runs: [Run]) {
        self.runs = runs
    }

    public var plain: String { Rendering.plain(self) }
}

/// Builds the runs, merging neighbours of the same kind.
struct CitationBuilder {
    private(set) var runs: [Run] = []

    mutating func text(_ s: String) { add(Run(s)) }

    mutating func italic(_ s: String) { add(Run(s, italic: true)) }

    mutating func append(_ more: [Run]) { more.forEach { add($0) } }

    /// Ends a sentence: a full stop unless the text already ends with . ? or !.
    mutating func endSentence() {
        let last = runs.last?.text.last
        if last != "." && last != "?" && last != "!" { text(".") }
    }

    func build() -> StyledCitation { StyledCitation(runs) }

    private mutating func add(_ run: Run) {
        guard !run.text.isEmpty else { return }
        if let last = runs.last, last.italic == run.italic {
            runs[runs.count - 1].text += run.text
        } else {
            runs.append(run)
        }
    }
}
