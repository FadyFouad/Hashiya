import Foundation

/// A paper identifier recognized in text typed or shared by the user.
public enum PaperIdentifier: Equatable, Hashable, Sendable {
    /// Normalized by `normalizeDOI`, e.g. "10.1038/nature14539".
    case doi(String)
    /// No version, no subject class: "1706.03762", "hep-th/9901001", "math/0309136".
    case arxiv(String)
}

/// Strict, for the Search box: the whole trimmed text must be an identifier or a supported link.
public func parsePaperIdentifier(_ text: String) -> PaperIdentifier? {
    let input = joiningPrefixes(text.trimmingCharacters(in: .whitespacesAndNewlines))
    guard !input.isEmpty, !input.contains(where: \.isWhitespace) else { return nil }
    return parseToken(input)
}

/// Lenient, for shares: the first supported link in the text, otherwise the first bare identifier.
public func extractPaperIdentifier(_ text: String) -> PaperIdentifier? {
    let tokens = asciiWhitespaceTokens(joiningPrefixes(String(text.prefix(maxSharedText))))
    return tokens.lazy.filter(isURL).compactMap(parseToken).first
        ?? tokens.lazy.filter { !isURL($0) }.compactMap(parseToken).first
}

/// True when the trimmed text is a single link-shaped token.
public func looksLikeLink(_ text: String) -> Bool {
    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
    return !trimmed.isEmpty && !trimmed.contains(where: \.isWhitespace) && isURL(trimmed)
}

// MARK: Patterns (ported from Android's PaperIdentifier.kt; digits are ASCII `[0-9]`, never `\d`)

private let maxSharedText = 2_000

// New style, optional version: YYMM.NNNN from 0704 (when it began) to 1412, YYMM.NNNNN from 1501 on.
private let month = #"(?:0[1-9]|1[0-2])"#
private let newArxiv4 = #"(?:07(?:0[4-9]|1[0-2])|(?:0[89]|1[0-4])"# + month + #")\.[0-9]{4}"#
private let newArxiv5 = #"(?:1[5-9]|[2-9][0-9])"# + month + #"\.[0-9]{5}"#
private let newArxiv = "(?:" + newArxiv4 + "|" + newArxiv5 + #")(?:v[0-9]+)?"#
// Old style: archive[.SUBJECT]/YYMMNNN, optional version.
private let oldArxiv = #"[a-z]+(?:-[a-z]+)?(?:\.[a-z]{2})?/[0-9]{7}(?:v[0-9]+)?"#
private let anyArxiv = "(?:" + newArxiv + "|" + oldArxiv + ")"
private let asciiWhitespaceClass = #"[ \t\n\x{0B}\f\r]"#

private let arxivURL = Pattern(
    whole: #"(?:https?://)?(?:www\.|export\.)?arxiv\.org/(?:abs|pdf|html)/("# + anyArxiv + #")(?:\.pdf)?/?"#,
    ignoringASCIICase: true
)
private let arxivPrefixed = Pattern(whole: "arxiv:(" + anyArxiv + ")", ignoringASCIICase: true)
private let bareArxiv = Pattern(whole: anyArxiv, ignoringASCIICase: true)
private let arxivDOI = Pattern(whole: #"10\.48550/arxiv\.("# + anyArxiv + ")", ignoringASCIICase: true)
private let bareDOI = Pattern(whole: #"10\.[0-9]{4,9}/[^ \t\n\x{0B}\f\r]+"#)
private let doiInPath = Pattern(#"10\.[0-9]{4,9}/.+"#)
private let viewSegment = Pattern(#"/(?:full|abstract|epdf|pdf|fulltext)\z"#, ignoringASCIICase: true)
private let pdfSuffix = Pattern(#"\.pdf\z"#, ignoringASCIICase: true)
private let preprintSuffix = Pattern(#"(?:v[0-9]+)?(?:\.full)?\z"#, ignoringASCIICase: true)
private let spaceAfterPrefix = Pattern(#"(?<![\p{L}\p{Nd}_])(arxiv:|doi:)"# + asciiWhitespaceClass + "+", ignoringASCIICase: true)
private let urlStart = Pattern(#"(https?://|www\.|(?:export\.)?arxiv\.org/|(?:dx\.)?doi\.org/)"#, ignoringASCIICase: true)
private let natureArticle = Pattern(whole: #"articles/([A-Za-z0-9][A-Za-z0-9.-]*)"#, ignoringASCIICase: true)
private let arxivVersion = Pattern(#"v[0-9]+\z"#)
private let arxivSubjectClass = Pattern(#"\A([a-z]+(?:-[a-z]+)?)\.[a-z]{2}/"#)

private let doiHosts: Set<String> = ["doi.org", "dx.doi.org", "www.doi.org"]
private let natureHosts: Set<String> = ["nature.com", "www.nature.com"]
private let leadingJunk: Set<Character> = ["(", "[", "\"", "'", "<"]
private let trailingJunk: Set<Character> = [".", ",", ";", ":", "!", "?", "\"", "'", ">", "]"]

// MARK: Algorithm

/// "arXiv: 2401.00001" → "arXiv:2401.00001", so the prefix and the ID form one token.
private func joiningPrefixes(_ text: String) -> String {
    spaceAfterPrefix.replacingMatches(in: text) { $0.groups[1] }
}

private func parseToken(_ rawToken: String) -> PaperIdentifier? {
    let token = trimTrailingJunk(String(rawToken.drop { leadingJunk.contains($0) }))
    guard !token.isEmpty else { return nil }
    if isURL(token) {
        let urlToken = urlStart.firstMatch(in: token).map { trimTrailingJunk(token.suffix(fromUTF16Offset: $0.start)) } ?? token
        return parseURL(urlToken)
    }
    if let match = arxivPrefixed.wholeMatch(token) { return .arxiv(canonicalArxiv(match.groups[1])) }
    if bareArxiv.wholeMatch(token) != nil { return .arxiv(canonicalArxiv(token)) }
    let doi = token.asciiLowercased().hasPrefix("doi:") ? String(token.dropFirst(4)) : token
    return bareDOI.wholeMatch(doi) != nil ? doiIdentifier(doi) : nil
}

private func isURL(_ token: String) -> Bool {
    let value = String(token.drop { leadingJunk.contains($0) }).asciiLowercased()
    return value.contains("://") || value.hasPrefix("www.") || value.hasPrefix("arxiv.org/")
        || value.hasPrefix("doi.org/") || value.hasPrefix("dx.doi.org/")
}

private func parseURL(_ url: String) -> PaperIdentifier? {
    let withoutFragment = url.prefix { $0 != "#" }
    let withoutQuery = String(withoutFragment.prefix { $0 != "?" })
    if let match = arxivURL.wholeMatch(withoutQuery) { return .arxiv(canonicalArxiv(match.groups[1])) }
    let withoutScheme = withoutQuery.range(of: "://").map { String(withoutQuery[$0.upperBound...]) } ?? withoutQuery
    let host = String(withoutScheme.prefix { $0 != "/" }).asciiLowercased()
    let path = withoutScheme.firstIndex(of: "/").map { percentDecoded(String(withoutScheme[withoutScheme.index(after: $0)...])) } ?? ""
    let doiPart: String? = if doiHosts.contains(host) {
        path
    } else if natureHosts.contains(host) {
        natureDOI(path)
    } else {
        doiInPath.firstMatch(in: path).map { dropPublisherSuffix($0.groups[0]) }
    }
    return doiPart.flatMap(doiIdentifier)
}

/// Nature Portfolio article pages map straight to a DOI: "articles/nature14539" → "10.1038/nature14539".
private func natureDOI(_ path: String) -> String? {
    let article = pdfSuffix.removingMatch(from: droppingTrailingSlashes(path))
    return natureArticle.wholeMatch(article).map { "10.1038/" + $0.groups[1] }
}

/// Publisher links put views after the DOI ("….pdf", "…/full", "…/epdf"), and bioRxiv/medRxiv add a version
/// ("…v1", "…v1.full.pdf"). Only for DOIs found in a publisher's path; doi.org links and typed DOIs keep them.
private func dropPublisherSuffix(_ doi: String) -> String {
    let withoutView = pdfSuffix.removingMatch(from: viewSegment.removingMatch(from: droppingTrailingSlashes(doi)))
    return withoutView.hasPrefix("10.1101/") ? preprintSuffix.removingMatch(from: withoutView) : withoutView
}

private func doiIdentifier(_ raw: String) -> PaperIdentifier? {
    guard let doi = normalizeDOI(trimTrailingJunk(droppingTrailingSlashes(raw))), bareDOI.wholeMatch(doi) != nil else {
        return nil
    }
    if let match = arxivDOI.wholeMatch(doi) { return .arxiv(canonicalArxiv(match.groups[1])) }
    return .doi(doi)
}

/// Removes the version, a ".pdf" suffix and an old-style subject class ("math.GT/0309136" → "math/0309136").
private func canonicalArxiv(_ raw: String) -> String {
    var value = raw.asciiLowercased()
    if value.hasSuffix(".pdf") { value.removeLast(4) }
    value = arxivVersion.removingMatch(from: value)
    return arxivSubjectClass.replacingMatches(in: value) { $0.groups[1] + "/" }
}

/// Drops trailing punctuation; a trailing ")" only when the parentheses are unbalanced.
private func trimTrailingJunk(_ value: String) -> String {
    var result = value
    while let last = result.last {
        let unbalancedParenthesis = last == ")" && result.count(where: { $0 == "(" }) < result.count(where: { $0 == ")" })
        guard trailingJunk.contains(last) || unbalancedParenthesis else { break }
        result.removeLast()
    }
    return result
}

private func droppingTrailingSlashes(_ value: String) -> String {
    var result = value
    while result.hasSuffix("/") { result.removeLast() }
    return result
}

/// Splits on runs of ASCII whitespace (space, tab, LF, VT, FF, CR), dropping empty tokens.
private func asciiWhitespaceTokens(_ text: String) -> [String] {
    var tokens: [String] = []
    var current = String.UnicodeScalarView()
    for scalar in text.unicodeScalars {
        if [0x20, 0x09, 0x0A, 0x0B, 0x0C, 0x0D].contains(scalar.value) {
            if !current.isEmpty { tokens.append(String(current)) }
            current = String.UnicodeScalarView()
        } else {
            current.append(scalar)
        }
    }
    if !current.isEmpty { tokens.append(String(current)) }
    return tokens
}

/// Each "%" followed by two hex digits becomes that byte; everything else is kept as its UTF-8 bytes.
/// Invalid UTF-8 becomes U+FFFD (`removingPercentEncoding` would return nil instead).
private func percentDecoded(_ value: String) -> String {
    guard value.contains("%") else { return value }
    let input = Array(value.utf8)
    var bytes: [UInt8] = []
    var index = 0
    while index < input.count {
        if input[index] == UInt8(ascii: "%"), index + 2 < input.count,
           let high = hexValue(input[index + 1]), let low = hexValue(input[index + 2]) {
            bytes.append(high << 4 | low)
            index += 3
        } else {
            bytes.append(input[index])
            index += 1
        }
    }
    return String(decoding: bytes, as: UTF8.self)
}

private func hexValue(_ byte: UInt8) -> UInt8? {
    switch byte {
    case UInt8(ascii: "0")...UInt8(ascii: "9"): byte - UInt8(ascii: "0")
    case UInt8(ascii: "a")...UInt8(ascii: "f"): byte - UInt8(ascii: "a") + 10
    case UInt8(ascii: "A")...UInt8(ascii: "F"): byte - UInt8(ascii: "A") + 10
    default: nil
    }
}

// MARK: Matching

extension String {
    /// Only A–Z become a–z; every other character is kept, so UTF-16 offsets are unchanged.
    fileprivate func asciiLowercased() -> String {
        String(String.UnicodeScalarView(unicodeScalars.map { scalar in
            (65...90).contains(scalar.value) ? Unicode.Scalar(scalar.value + 32)! : scalar
        }))
    }

    fileprivate func suffix(fromUTF16Offset offset: Int) -> String {
        (self as NSString).substring(from: offset)
    }
}

/// An ICU pattern. Case-insensitive patterns match the ASCII-lowercased text, so only ASCII letters fold
/// (ICU's own case-insensitive mode would also fold "K", the Kelvin sign, into "k").
private struct Pattern: @unchecked Sendable {  // NSRegularExpression is immutable and thread-safe.
    struct Match {
        /// Group 0 is the whole match; a group that did not take part is "".
        var groups: [String]
        /// UTF-16 offsets in the searched text.
        var start: Int
        var end: Int
    }

    private let regex: NSRegularExpression
    private let ignoringASCIICase: Bool

    init(_ pattern: String, ignoringASCIICase: Bool = false) {
        regex = try! NSRegularExpression(pattern: pattern)
        self.ignoringASCIICase = ignoringASCIICase
    }

    /// A pattern that must match the entire text.
    init(whole pattern: String, ignoringASCIICase: Bool = false) {
        self.init(#"\A(?:"# + pattern + #")\z"#, ignoringASCIICase: ignoringASCIICase)
    }

    func wholeMatch(_ text: String) -> Match? {
        firstMatch(in: text)
    }

    func firstMatch(in text: String) -> Match? {
        matches(in: text).first
    }

    /// Removes the first match (used for suffix patterns ending in `\z`).
    func removingMatch(from text: String) -> String {
        guard let match = firstMatch(in: text) else { return text }
        return replacing([match], in: text) { _ in "" }
    }

    /// Replaces every match with `replacement(match)`; groups come from the original text.
    func replacingMatches(in text: String, with replacement: (Match) -> String) -> String {
        replacing(matches(in: text), in: text, with: replacement)
    }

    private func matches(in text: String) -> [Match] {
        let subject = ignoringASCIICase ? text.asciiLowercased() : text
        let original = text as NSString
        return regex.matches(in: subject, range: NSRange(location: 0, length: original.length)).map { result in
            Match(
                groups: (0..<result.numberOfRanges).map { index in
                    let range = result.range(at: index)
                    return range.location == NSNotFound ? "" : original.substring(with: range)
                },
                start: result.range.location,
                end: result.range.location + result.range.length
            )
        }
    }

    private func replacing(_ matches: [Match], in text: String, with replacement: (Match) -> String) -> String {
        let result = NSMutableString(string: text)
        for match in matches.reversed() {
            result.replaceCharacters(in: NSRange(location: match.start, length: match.end - match.start), with: replacement(match))
        }
        return result as String
    }
}
