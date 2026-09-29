import Foundation
import HashiyaModel

/// What the Share Extension does with what was shared.
public enum ShareLookupInput: Equatable, Sendable {
    case lookup(PaperIdentifier)
    /// No identifier; the page title is shown, not searched.
    case noIdentifier(pageTitle: String)
    case nothing
}

/// The first identifier in the shared URL and text; else the page title (trimmed, at most 300 characters,
/// ignored when it is only a link); else nothing.
public func shareLookupInput(url: URL?, text: String?, title: String?) -> ShareLookupInput {
    let combined = [url?.absoluteString, text].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: "\n")
    let trimmedTitle = String((title ?? "").trimmingCharacters(in: .whitespacesAndNewlines).prefix(300))
    let pageTitle = trimmedTitle.isEmpty || looksLikeLink(trimmedTitle) ? nil : trimmedTitle
    if let identifier = extractPaperIdentifier(combined) {
        return .lookup(identifier)
    }
    if let pageTitle {
        return .noIdentifier(pageTitle: pageTitle)
    }
    return .nothing
}
