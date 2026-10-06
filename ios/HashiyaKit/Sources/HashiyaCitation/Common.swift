import Foundation

/// "10.1/x" from "10.1/x", "https://doi.org/10.1/x" or "doi:10.1/x".
func doiOf(_ raw: String) -> String {
    raw.trimmingCharacters(in: .whitespacesAndNewlines)
        .replacingOccurrences(of: #"^(https?://(dx\.)?doi\.org/|doi:)"#, with: "", options: [.regularExpression, .caseInsensitive])
}

/// "436–444", or "12" for a single page; nil when there is no first page.
func pageRange(_ first: String?, _ last: String?) -> String? {
    guard let a = first.nonBlank else { return nil }
    guard let b = last.nonBlank, b != a else { return a }
    return "\(a)–\(b)"
}

func isSinglePage(_ first: String?, _ last: String?) -> Bool {
    guard let b = last.nonBlank else { return true }
    return b == first?.trimmingCharacters(in: .whitespacesAndNewlines)
}

/// Lower case without diacritics, for sorting.
func sortKey(_ text: String) -> String {
    let stripped = text.decomposedStringWithCanonicalMapping.unicodeScalars.filter { $0.properties.generalCategory != .nonspacingMark }
    return String(String.UnicodeScalarView(stripped)).lowercased()
}

extension Optional where Wrapped == String {
    /// Trimmed, or nil when empty.
    var nonBlank: String? {
        guard let trimmed = self?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else { return nil }
        return trimmed
    }
}
