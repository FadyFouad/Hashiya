import Foundation

/// BibTeX and BibLaTeX (spec §4.2). `parse` never throws: a broken entry becomes an `ImportIssue` and the rest go on.
/// Android's `BibTeXParser`.
enum BibTeXParser {
    private static let months: [String: String] = [
        "jan": "January", "feb": "February", "mar": "March", "apr": "April", "may": "May", "jun": "June",
        "jul": "July", "aug": "August", "sep": "September", "oct": "October", "nov": "November", "dec": "December",
    ]

    private static let extendedNameKeys: Set<String> = ["family", "given", "prefix", "suffix", "useprefix"]

    static func parse(_ text: String) -> ImportParseResult {
        var macros = months
        var entries: [ImportedEntry] = []
        var issues: [ImportIssue] = []
        for chunk in chunks(text) {
            var reader = Reader(s: chunk.scalars)
            let raw: RawEntry?
            do {
                raw = try reader.entry(macros: &macros)
            } catch {
                issues.append(ImportIssue(line: chunk.line, kind: .malformed))
                continue
            }
            guard let raw else { continue }
            let entry = build(raw, line: chunk.line)
            if entry.title.isEmpty, entry.doi == nil {
                issues.append(ImportIssue(line: chunk.line, kind: .empty))
            } else {
                entries.append(entry)
            }
        }
        return ImportParseResult(format: .bibTeX, entries: entries, issues: issues)
    }

    private struct Chunk {
        var scalars: [Unicode.Scalar]
        var line: Int
    }

    /// Splits at every line that starts (after spaces) with `@`, letters, optional spaces and `{` or `(`.
    private static func chunks(_ text: String) -> [Chunk] {
        let scalars = Array(text.unicodeScalars)
        var starts: [(offset: Int, line: Int)] = []
        var offset = 0
        var line = 1
        while offset <= scalars.count {
            var lineEnd = offset
            while lineEnd < scalars.count, scalars[lineEnd] != "\n" { lineEnd += 1 }
            if startsEntry(scalars, offset, lineEnd) { starts.append((offset, line)) }
            offset = lineEnd + 1
            line += 1
        }
        return starts.enumerated().map { index, start in
            let end = index + 1 < starts.count ? starts[index + 1].offset : scalars.count
            return Chunk(scalars: Array(scalars[start.offset..<end]), line: start.line)
        }
    }

    private static func startsEntry(_ s: [Unicode.Scalar], _ from: Int, _ to: Int) -> Bool {
        var i = from
        while i < to, s[i] == " " || s[i] == "\t" { i += 1 }
        guard i < to, s[i] == "@" else { return false }
        i += 1
        let lettersStart = i
        while i < to, isASCIILetter(s[i]) { i += 1 }
        guard i > lettersStart else { return false }
        while i < to, s[i] == " " || s[i] == "\t" { i += 1 }
        return i < to && (s[i] == "{" || s[i] == "(")
    }

    private struct Malformed: Error {}

    struct RawEntry {
        var type: String
        var key: String?
        var fields: [String: String]
    }

    /// Reads one chunk: `@type{key, name = value, …}`. Returns nil for @comment, @preamble and @string.
    private struct Reader {
        let s: [Unicode.Scalar]
        var p = 0

        init(s: [Unicode.Scalar]) {
            self.s = s
        }

        mutating func entry(macros: inout [String: String]) throws -> RawEntry? {
            while p < s.count, s[p] != "@" { p += 1 }
            p += 1
            let type = word().lowercased()
            skipSpaces()
            let close: Unicode.Scalar
            switch peek() {
            case "{": close = "}"
            case "(": close = ")"
            default: throw Malformed()
            }
            p += 1
            switch type {
            case "comment", "preamble":
                return nil
            case "string":
                // The first of a repeated name wins, as in Android's parser.
                var defined = Set<String>()
                for (name, value) in try fields(close: close, macros: macros) where defined.insert(name).inserted {
                    macros[name] = value
                }
                return nil
            default:
                break
            }
            let (key, hasFields) = try key(close: close)
            let fields = hasFields ? try fields(close: close, macros: macros) : []
            var map: [String: String] = [:]
            for (name, value) in fields where map[name] == nil { map[name] = value }
            return RawEntry(type: type, key: key, fields: map)
        }

        /// The key, and whether fields follow. Rewinds when the entry has no key and starts with a field.
        private mutating func key(close: Unicode.Scalar) throws -> (String?, Bool) {
            skipSpaces()
            let start = p
            while p < s.count, s[p] != ",", s[p] != "=", s[p] != close, !isSpace(s[p]) { p += 1 }
            let key = p > start ? string(s[start..<p]) : nil
            skipSpaces()
            switch peek() {
            case ",":
                p += 1
                return (key, true)
            case close:
                p += 1
                return (key, false)
            case "=":
                p = start
                return (nil, true)
            default:
                throw Malformed()
            }
        }

        /// `name = value` pairs up to `close`, in order, names lowercased.
        private mutating func fields(close: Unicode.Scalar, macros: [String: String]) throws -> [(String, String)] {
            var fields: [(String, String)] = []
            let nameStops = Set("=,{}\"#()".unicodeScalars)
            while true {
                skipSpaces()
                if peek() == close {
                    p += 1
                    return fields
                }
                if peek() == "," {
                    p += 1
                    continue
                }
                let start = p
                while p < s.count, !isSpace(s[p]), !nameStops.contains(s[p]) { p += 1 }
                guard p > start else { throw Malformed() }
                let name = string(s[start..<p]).lowercased()
                skipSpaces()
                guard peek() == "=" else { throw Malformed() }
                p += 1
                let value = try value(macros: macros)
                fields.append((name, value))
                skipSpaces()
                if peek() == "," {
                    p += 1
                } else if peek() != close {
                    throw Malformed()
                }
            }
        }

        /// Parts joined by `#`: braced, quoted, a number or a macro.
        private mutating func value(macros: [String: String]) throws -> String {
            var out = ""
            while true {
                skipSpaces()
                guard let c = peek() else { throw Malformed() }
                if c == "{" {
                    out += try braced()
                } else if c == "\"" {
                    out += try quoted()
                } else if isASCIIDigitScalar(c) {
                    out += digits()
                } else if isASCIILetter(c) {
                    out += macros[macroName().lowercased()] ?? ""
                } else {
                    throw Malformed()
                }
                skipSpaces()
                if peek() == "#" { p += 1 } else { return out }
            }
        }

        private mutating func braced() throws -> String {
            let start = p + 1
            var depth = 0
            while p < s.count {
                if s[p] == "{" {
                    depth += 1
                } else if s[p] == "}" {
                    depth -= 1
                    if depth == 0 {
                        p += 1
                        return string(s[start..<(p - 1)])
                    }
                }
                p += 1
            }
            throw Malformed()
        }

        private mutating func quoted() throws -> String {
            p += 1
            let start = p
            var depth = 0
            while p < s.count {
                if s[p] == "{" {
                    depth += 1
                } else if s[p] == "}" {
                    depth -= 1
                } else if s[p] == "\"", depth == 0 {
                    p += 1
                    return string(s[start..<(p - 1)])
                }
                p += 1
            }
            throw Malformed()
        }

        private mutating func digits() -> String {
            let start = p
            while p < s.count, isASCIIDigitScalar(s[p]) { p += 1 }
            return string(s[start..<p])
        }

        private mutating func macroName() -> String {
            let start = p
            let extra = Set("_-:.+/".unicodeScalars)
            while p < s.count, isASCIILetter(s[p]) || isASCIIDigitScalar(s[p]) || extra.contains(s[p]) { p += 1 }
            return string(s[start..<p])
        }

        private mutating func word() -> String {
            let start = p
            while p < s.count, isASCIILetter(s[p]) { p += 1 }
            return string(s[start..<p])
        }

        private func peek() -> Unicode.Scalar? {
            p < s.count ? s[p] : nil
        }

        private mutating func skipSpaces() {
            while p < s.count, isSpace(s[p]) { p += 1 }
        }
    }

    private static func build(_ raw: RawEntry, line: Int) -> ImportedEntry {
        let fields = raw.fields
        func text(_ name: String) -> String? { fields[name].flatMap { collapse(LatexDecoder.decode($0)) } }
        func rawText(_ name: String) -> String? { fields[name].flatMap { collapse(cleanRaw($0)) } }

        let journal = text("journal") ?? text("journaltitle")
        let howPublished = fields["howpublished"]
        let howPublishedIsURL = howPublished.map {
            $0.range(of: "\\url", options: .caseInsensitive) != nil || $0.range(of: "http", options: .caseInsensitive) != nil
        } ?? false
        let url = rawText("url") ?? (howPublishedIsURL ? howPublished.flatMap(urlIn) : nil)
        let eprintType = (text("archiveprefix") ?? text("eprinttype"))?.lowercased()
        let doi = doiFrom(rawText("doi"))
            ?? (eprintType == "arxiv" ? arxivDOI(rawText("eprint")) : nil)
            ?? arxivDOI(rawText("arxivid"))
            ?? doiFrom(url)
            ?? scholarArxivDOI(journal)
        let (firstPage, lastPage) = splitPages(fields["pages"].map(cleanRaw))
        let kind = bibTeXKind(type: raw.type, doi: doi, journal: journal)
        let authors = names(fields["author"])
        let venue = journal ?? text("booktitle") ?? text("school") ?? text("institution")
            ?? (howPublishedIsURL ? nil : howPublished.flatMap { collapse(LatexDecoder.decode($0)) })
        return ImportedEntry(
            line: line,
            entryType: raw.type,
            citeKey: raw.key,
            title: text("title") ?? "",
            authors: authors.isEmpty ? names(fields["editor"]) : authors,
            year: firstYear(fields["year"]) ?? firstYear(fields["date"]),
            venue: venue,
            doi: doi,
            url: url,
            abstract: text("abstract"),
            volume: text("volume"),
            issue: text("number") ?? text("issue"),
            firstPage: firstPage,
            lastPage: lastPage,
            publisher: text("publisher"),
            workType: kind.workType,
            sourceType: kind.sourceType
        )
    }

    /// The URL in `\url{…}`, or the whole value when it is a bare link.
    private static func urlIn(_ value: String) -> String? {
        if let start = value.range(of: "\\url{", options: .caseInsensitive) {
            let rest = value[start.upperBound...]
            let inner = rest.firstIndex(of: "}").map { rest[..<$0] } ?? rest
            return collapse(cleanRaw(String(inner)))
        }
        guard let clean = collapse(cleanRaw(value)), clean.lowercased().hasPrefix("http") else { return nil }
        return clean
    }

    /// Display names from a BibTeX name list (spec §4.2, Names).
    private static func names(_ raw: String?) -> [String] {
        guard let raw else { return [] }
        return splitOutsideBraces(raw).compactMap(displayName)
    }

    /// Splits on the word "and" (any case) between whitespace, outside braces.
    private static func splitOutsideBraces(_ raw: String) -> [String] {
        let s = Array(raw.unicodeScalars)
        var parts: [String] = []
        var depth = 0
        var start = 0
        var i = 0
        while i < s.count {
            let c = s[i]
            if c == "{" {
                depth += 1
            } else if c == "}" {
                depth -= 1
            } else if depth == 0, isSpace(c) {
                var j = i
                while j < s.count, isSpace(s[j]) { j += 1 }
                if j + 3 < s.count, string(s[j..<(j + 3)]).lowercased() == "and", isSpace(s[j + 3]) {
                    parts.append(string(s[start..<i]))
                    start = j + 3
                    i = start
                    continue
                }
            }
            i += 1
        }
        parts.append(string(s[start...]))
        return parts.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
    }

    private static func displayName(_ raw: String) -> String? {
        if raw.lowercased() == "others" { return nil }
        let scalars = Array(raw.unicodeScalars)
        if scalars.first == "{", closingBrace(scalars, 0) == scalars.count - 1 { return decoded(raw) }
        let parts = splitTopLevelCommas(raw)
        let isExtended = parts.count > 1 && parts.allSatisfy { part in
            guard let equals = part.firstIndex(of: "=") else { return false }
            return extendedNameKeys.contains(part[..<equals].trimmingCharacters(in: .whitespacesAndNewlines).lowercased())
        }
        if isExtended {
            var values: [String: String] = [:]
            for part in parts {
                guard let equals = part.firstIndex(of: "=") else { continue }
                values[part[..<equals].trimmingCharacters(in: .whitespacesAndNewlines).lowercased()] =
                    part[part.index(after: equals)...].trimmingCharacters(in: .whitespacesAndNewlines)
            }
            return joinNames([values["given"], values["prefix"], values["family"], values["suffix"]])
        }
        let pieces = parts.filter { !$0.isEmpty }
        switch pieces.count {
        case 0: return nil
        case 1: return decoded(pieces[0])
        case 2: return joinNames([pieces[1], pieces[0]])
        default: return joinNames([pieces[2], pieces[0], pieces[1]])
        }
    }

    private static func joinNames(_ parts: [String?]) -> String? {
        collapse(parts.compactMap { $0.map(decoded) }.joined(separator: " "))
    }

    private static func decoded(_ raw: String) -> String {
        collapse(LatexDecoder.decode(raw)) ?? ""
    }

    private static func splitTopLevelCommas(_ raw: String) -> [String] {
        let s = Array(raw.unicodeScalars)
        var parts: [String] = []
        var depth = 0
        var start = 0
        for (i, c) in s.enumerated() {
            if c == "{" {
                depth += 1
            } else if c == "}" {
                depth -= 1
            } else if c == ",", depth == 0 {
                parts.append(string(s[start..<i]).trimmingCharacters(in: .whitespacesAndNewlines))
                start = i + 1
            }
        }
        parts.append(string(s[start...]).trimmingCharacters(in: .whitespacesAndNewlines))
        return parts
    }

    private static func closingBrace(_ s: [Unicode.Scalar], _ open: Int) -> Int {
        var depth = 0
        for i in open..<s.count {
            if s[i] == "{" {
                depth += 1
            } else if s[i] == "}" {
                depth -= 1
                if depth == 0 { return i }
            }
        }
        return -1
    }
}
