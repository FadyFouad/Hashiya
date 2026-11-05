import Foundation

/// Decoding and parsing an import file. Android's `ImportParser`.
public enum ImportParser {
    /// Nil when the text is neither BibTeX nor RIS.
    public static func parse(_ text: String) -> ImportParseResult? {
        var normalized = text
        if normalized.hasPrefix("\u{FEFF}") { normalized.removeFirst() }
        normalized = normalized.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n")
        let firstLine = normalized.components(separatedBy: "\n").first { collapse($0) != nil }
        if let firstLine, RISParser.tagLine(firstLine) != nil { return RISParser.parse(normalized) }
        if bibTeXEntry.firstMatch(in: normalized, range: NSRange(normalized.startIndex..., in: normalized)) != nil {
            return BibTeXParser.parse(normalized)
        }
        return nil
    }

    /// UTF-8 with any BOM dropped; Windows-1252 when the bytes aren't valid UTF-8.
    public static func decode(_ data: Data) -> String {
        let bom = Data([0xEF, 0xBB, 0xBF])
        let body = data.starts(with: bom) ? data.dropFirst(3) : data
        if let text = String(data: body, encoding: .utf8) { return text }
        // Old EndNote exports.
        return String(data: data, encoding: .windowsCP1252) ?? String(decoding: data, as: UTF8.self)
    }

    private static let bibTeXEntry = try! NSRegularExpression(pattern: #"@[A-Za-z]+[ \t]*[{(]"#)
}
