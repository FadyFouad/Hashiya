import Foundation

/// RIS (spec §4.3). Lines are already `\n`-separated. `parse` never throws. Android's `RisParser`.
enum RISParser {
    private static let venueTags = ["T2", "JF", "JO", "BT", "JA", "J2"]
    private static let doiTags = ["DO", "UR", "L3", "M3", "N1"]

    /// A tag line split into its tag and value, or nil for any other line.
    static func tagLine(_ line: String) -> (tag: String, value: String)? {
        let s = Array(line.unicodeScalars)
        guard s.count >= 4 else { return nil }
        let isUpper: (Unicode.Scalar) -> Bool = { $0.value >= 0x41 && $0.value <= 0x5A }
        guard isUpper(s[0]), isUpper(s[1]) || isASCIIDigitScalar(s[1]) else { return nil }
        var i = 2
        var spaces = 0
        while i < s.count, s[i] == " ", spaces < 2 {
            i += 1
            spaces += 1
        }
        guard spaces > 0, i < s.count, s[i] == "-" else { return nil }
        i += 1
        if i < s.count, s[i] == " " { i += 1 }
        return (string(s[0..<2]), string(s[i...]))
    }

    private final class Record {
        let line: Int
        var tags: [(tag: String, value: String)] = []

        init(line: Int) {
            self.line = line
        }

        func values(_ tag: String) -> [String] {
            tags.filter { $0.tag == tag }.compactMap { collapse($0.value) }
        }

        func first(_ tag: String) -> String? {
            values(tag).first
        }
    }

    static func parse(_ text: String) -> ImportParseResult {
        var entries: [ImportedEntry] = []
        var issues: [ImportIssue] = []
        var record: Record?
        var strayLine: Int?
        /// The index of the open record's last tag, which a wrapped line continues.
        var lastTag: Int?

        func closeRecord() {
            if let open = record {
                let entry = build(open)
                if entry.title.isEmpty, entry.doi == nil {
                    issues.append(ImportIssue(line: open.line, kind: .empty))
                } else {
                    entries.append(entry)
                }
            }
            record = nil
        }

        func closeStray() {
            if let line = strayLine { issues.append(ImportIssue(line: line, kind: .missingType)) }
            strayLine = nil
        }

        for (index, line) in text.components(separatedBy: "\n").enumerated() {
            let lineNumber = index + 1
            guard let tag = tagLine(line) else {
                // A wrapped value continues; blank lines and text outside a record are ignored.
                if collapse(line) != nil, let open = record, let last = lastTag {
                    open.tags[last].value += " " + line
                }
                continue
            }
            switch tag.tag {
            case "TY":
                closeRecord()
                closeStray()
                let open = Record(line: lineNumber)
                open.tags.append(("TY", tag.value))
                record = open
                lastTag = nil
            case "ER":
                closeRecord()
                closeStray()
                lastTag = nil
            default:
                if let open = record {
                    open.tags.append((tag.tag, tag.value))
                    lastTag = open.tags.count - 1
                } else {
                    if strayLine == nil { strayLine = lineNumber }
                    lastTag = nil
                }
            }
        }
        closeRecord()
        closeStray()
        return ImportParseResult(format: .ris, entries: entries, issues: issues)
    }

    private static func build(_ record: Record) -> ImportedEntry {
        let type = record.first("TY") ?? ""
        let doi = doiTags.lazy.flatMap { record.values($0) }.compactMap(doiFrom).first
        var authorValues = record.tags.filter { $0.tag == "AU" || $0.tag == "A1" }.compactMap { collapse($0.value) }
        if authorValues.isEmpty { authorValues = record.values("A2") }
        let firstVenue = venueTags.lazy.compactMap { record.first($0) }.first
        let venue: String? = if risBookTypes.contains(type) {
            nil
        } else if type == "THES" || type == "RPRT" {
            firstVenue ?? record.first("PB")
        } else {
            firstVenue
        }
        let start = record.first("SP")
        let end = record.first("EP")
        let (firstPage, lastPage) = end == nil ? splitPages(start) : (start, end)
        let kind = risKind(type: type, doi: doi)
        return ImportedEntry(
            line: record.line,
            entryType: type,
            title: record.first("TI") ?? record.first("T1") ?? "",
            authors: authorValues.compactMap(displayName),
            year: firstYear(record.first("PY")) ?? firstYear(record.first("Y1")) ?? firstYear(record.first("DA")),
            venue: venue,
            doi: doi,
            url: record.first("UR"),
            abstract: record.first("AB") ?? record.first("N2"),
            volume: record.first("VL"),
            issue: record.first("IS"),
            firstPage: firstPage,
            lastPage: lastPage,
            publisher: record.first("PB"),
            workType: kind.workType,
            sourceType: kind.sourceType
        )
    }

    /// "Last, First" → "First Last"; "Last, First, Suffix" → "First Last Suffix"; no comma → as written.
    private static func displayName(_ raw: String) -> String? {
        let parts = raw.components(separatedBy: ",").compactMap(collapse)
        switch parts.count {
        case 0: return nil
        case 1: return parts[0]
        case 2: return "\(parts[1]) \(parts[0])"
        default: return "\(parts[1]) \(parts[0]) \(parts.dropFirst(2).joined(separator: " "))"
        }
    }
}
