import Foundation

/// A section of the fixed note template, in on-screen order.
public enum NoteSection: CaseIterable, Sendable {
    case summary, researchQuestion, method, keyFindings, limitations, thoughts
}

/// The user's notes on a saved paper, one plain-text field per `NoteSection`. Text is kept exactly as typed.
public struct PaperNotes: Equatable, Hashable, Sendable {
    public var summary: String
    public var researchQuestion: String
    public var method: String
    public var keyFindings: String
    public var limitations: String
    public var thoughts: String

    public init(
        summary: String = "",
        researchQuestion: String = "",
        method: String = "",
        keyFindings: String = "",
        limitations: String = "",
        thoughts: String = ""
    ) {
        self.summary = summary
        self.researchQuestion = researchQuestion
        self.method = method
        self.keyFindings = keyFindings
        self.limitations = limitations
        self.thoughts = thoughts
    }

    public subscript(section: NoteSection) -> String {
        get {
            switch section {
            case .summary: summary
            case .researchQuestion: researchQuestion
            case .method: method
            case .keyFindings: keyFindings
            case .limitations: limitations
            case .thoughts: thoughts
            }
        }
        set {
            switch section {
            case .summary: summary = newValue
            case .researchQuestion: researchQuestion = newValue
            case .method: method = newValue
            case .keyFindings: keyFindings = newValue
            case .limitations: limitations = newValue
            case .thoughts: thoughts = newValue
            }
        }
    }

    /// A copy with `section` set to `text`.
    public func with(_ section: NoteSection, _ text: String) -> PaperNotes {
        var copy = self
        copy[section] = text
        return copy
    }

    /// True when every section is empty or whitespace (newlines included) only.
    public var isEmpty: Bool {
        NoteSection.allCases.allSatisfy { self[$0].trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }
}
