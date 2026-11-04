import Foundation
import HashiyaModel

let arxivDOIPrefix = "10.48550/arxiv."

private func regex(_ pattern: String) -> NSRegularExpression {
    // The patterns are constants; a typo is a programming error.
    try! NSRegularExpression(pattern: pattern, options: [.caseInsensitive])
}

private let newArxivID = try! NSRegularExpression(pattern: #"^\d{4}\.\d{4,5}$"#)
private let oldArxivID = regex(#"^[a-z]+(?:-[a-z]+)?(?:\.[a-z]{2})?/\d{7}$"#)
private let arxivVersion = regex(#"v\d+$"#)
private let arxivURL = regex(#"arxiv\.org/(?:abs|pdf)/([^?#\s]+)"#)
private let scholarArxivJournal = regex(#"^arxiv preprint arxiv:(\S+)"#)

private func matches(_ expression: NSRegularExpression, _ text: String) -> Bool {
    expression.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) != nil
}

private func firstGroup(_ expression: NSRegularExpression, _ text: String) -> String? {
    guard let match = expression.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
          let range = Range(match.range(at: 1), in: text) else { return nil }
    return String(text[range])
}

/// The DOI arXiv assigns to `id` ("1706.03762v5" → "10.48550/arxiv.1706.03762"), or nil when `id` isn't an arXiv id.
func arxivDOI(_ id: String?) -> String? {
    guard let id else { return nil }
    var value = id.trimmingCharacters(in: .whitespacesAndNewlines)
    if value.hasSuffix(".pdf") { value = String(value.dropLast(4)) }
    if value.lowercased().hasPrefix("arxiv:") { value = String(value.dropFirst("arxiv:".count)) }
    value = arxivVersion.stringByReplacingMatches(in: value, range: NSRange(value.startIndex..., in: value), withTemplate: "")
    guard matches(newArxivID, value) || matches(oldArxivID, value) else { return nil }
    return arxivDOIPrefix + value.lowercased()
}

/// A DOI from a field that may hold one: a bare DOI, a `doi:` value, a doi.org link (percent-decoded), or an
/// arxiv.org link (as its arXiv DOI). Nil for anything else.
func doiFrom(_ value: String?) -> String? {
    guard let text = value?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty else { return nil }
    if let id = firstGroup(arxivURL, text) { return arxivDOI(id) }
    let decoded = text.lowercased().contains("doi.org/") ? percentDecode(text) : text
    guard let doi = normalizeDOI(decoded), !doi.unicodeScalars.contains(where: isSpace) else { return nil }
    return doi
}

/// Decodes each valid `%XX`, keeps any other `%` as written, and turns bytes that aren't UTF-8 into U+FFFD: Android's
/// `percentDecode`, byte for byte.
private func percentDecode(_ value: String) -> String {
    guard value.contains("%") else { return value }
    let utf8 = Array(value.utf8)
    var bytes: [UInt8] = []
    var i = 0
    while i < utf8.count {
        if utf8[i] == UInt8(ascii: "%"), i + 2 < utf8.count,
           let hex = UInt8(String(decoding: utf8[(i + 1)...(i + 2)], as: UTF8.self), radix: 16) {
            bytes.append(hex)
            i += 3
        } else {
            bytes.append(utf8[i])
            i += 1
        }
    }
    return String(decoding: bytes, as: UTF8.self)
}

/// Google Scholar's arXiv entries: `journal = {arXiv preprint arXiv:1810.04805}`.
func scholarArxivDOI(_ journal: String?) -> String? {
    guard let journal else { return nil }
    return firstGroup(scholarArxivJournal, journal.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap(arxivDOI)
}

func isArxivDOI(_ doi: String?) -> Bool {
    doi?.hasPrefix(arxivDOIPrefix) == true
}

func isScholarArxivJournal(_ journal: String?) -> Bool {
    guard let journal else { return false }
    return matches(scholarArxivJournal, journal.trimmingCharacters(in: .whitespacesAndNewlines))
}
