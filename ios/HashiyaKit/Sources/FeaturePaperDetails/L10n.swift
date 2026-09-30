import Foundation
import HashiyaDesignSystem
import HashiyaModel

/// This target's strings.
@MainActor
enum L10n {
    static func string(_ key: String) -> String {
        HashiyaStrings.string(key, bundle: .module)
    }

    static func format(_ key: String, _ arguments: any CVarArg...) -> String {
        HashiyaStrings.format(key, bundle: .module, arguments)
    }

    /// The section's name: "Summary", "Research question", ….
    static func noteLabel(_ section: NoteSection) -> String {
        string("note.\(section.key)")
    }

    /// The section's prompt: "What is this paper about, in your own words?", ….
    static func noteHint(_ section: NoteSection) -> String {
        string("note.\(section.key)Hint")
    }

    /// The line beside "My notes": nothing, "Saving…", "Saved" or "Couldn't save".
    static func saveStatus(_ state: NotesSaveState) -> String? {
        switch state {
        case .idle: nil
        case .saving: string("details.notesSaving")
        case .saved: string("details.notesSaved")
        case .failed: string("details.notesSaveFailed")
        }
    }
}

extension NoteSection {
    /// The stem of the section's catalog keys (`note.<key>`, `note.<key>Hint`) and of its field's accessibility
    /// identifier.
    var key: String {
        switch self {
        case .summary: "summary"
        case .researchQuestion: "researchQuestion"
        case .method: "method"
        case .keyFindings: "keyFindings"
        case .limitations: "limitations"
        case .thoughts: "thoughts"
        }
    }
}
