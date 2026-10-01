import Foundation
import HashiyaModel

/// A saved paper ready to cite: its metadata and its stored key.
public struct CitablePaper: Equatable, Sendable {
    public let paper: Paper
    public let citeKey: String

    public init(paper: Paper, citeKey: String) {
        self.paper = paper
        self.citeKey = citeKey
    }
}

public enum BibTeX {
    private static let arxivDOIPrefix = Array("10.48550/arxiv.".unicodeScalars)

    /// One entry, fields in a fixed order, empty ones left out, ending with a newline.
    public static func entry(_ paper: CitablePaper) -> String {
        let fields = fields(paper.paper)
        let body = fields.isEmpty ? "" : fields.map { "  \($0.name) = {\($0.value)}" }.joined(separator: ",\n") + "\n"
        return "@\(entryType(paper.paper.publication).bibName){\(paper.citeKey),\n\(body)}\n"
    }

    /// Entries sorted by cite key, separated by one blank line, ending with a newline. Empty for no papers.
    public static func file(_ papers: [CitablePaper]) -> String {
        papers.sorted { $0.citeKey < $1.citeKey }.map(entry).joined(separator: "\n")
    }

    private static func fields(_ paper: Paper) -> [(name: String, value: String)] {
        let details = paper.publication
        let type = entryType(details)
        func text(_ value: String?) -> String? {
            guard let value else { return nil }
            let clean = cleanWhitespace(value)
            return clean.isEmpty ? nil : escapeLatex(clean)
        }
        let doi = paper.doi.map(kotlinTrim).flatMap { $0.isEmpty ? nil : $0 }
        let firstPage = text(details.firstPage)
        let lastPage = text(details.lastPage)
        let eprint = doi.flatMap(arxivEprint)
        let url = doi == nil ? paper.openAccessPDFURL.map(kotlinTrim).flatMap { $0.isEmpty ? nil : $0 } : nil
        let authors = paper.authors.compactMap { text($0.name) }.map { splitsAuthor($0) ? "{\($0)}" : $0 }

        var fields: [(name: String, value: String)] = []
        if !authors.isEmpty { fields.append(("author", authors.joined(separator: " and "))) }
        if let title = text(paper.title) { fields.append(("title", protectCapitals(title))) }
        if let year = paper.year { fields.append(("year", String(year))) }
        if let field = type.venueField, let venue = text(paper.venue) {
            fields.append((field, field == "journal" || field == "booktitle" ? protectCapitals(venue) : venue))
        }
        if let volume = text(details.volume) { fields.append(("volume", volume)) }
        if let issue = text(details.issue) { fields.append(("number", issue)) }
        if let firstPage {
            fields.append(("pages", lastPage == nil || lastPage == firstPage ? firstPage : "\(firstPage)--\(lastPage!)"))
        }
        if type.hasPublisher, let publisher = text(details.publisher) { fields.append(("publisher", publisher)) }
        if let doi { fields.append(("doi", doi)) }
        if let eprint {
            fields.append(("eprint", eprint))
            fields.append(("archivePrefix", "arXiv"))
        }
        // Not escaped (styles pass it to \url), but braces are percent-encoded so they can't unbalance the entry.
        if let url {
            fields.append(("url", url.replacingOccurrences(of: "{", with: "%7B").replacingOccurrences(of: "}", with: "%7D")))
        }
        return fields
    }

    /// The part after "10.48550/arXiv." (any case), or nil when the DOI doesn't start with it or nothing follows.
    private static func arxivEprint(_ doi: String) -> String? {
        let scalars = Array(doi.unicodeScalars)
        guard scalars.count >= arxivDOIPrefix.count,
              zip(scalars, arxivDOIPrefix).allSatisfy({ Character($0).lowercased() == String(Character($1)) }) else { return nil }
        let rest = string(scalars.dropFirst(arxivDOIPrefix.count))
        return rest.isEmpty ? nil : rest
    }

    /// BibTeX splits authors on " and " and reads a comma as "Last, First", so names with either are kept whole in braces.
    /// Android's `(?i)\sand\s|,`: after `cleanWhitespace` the only whitespace left is U+0020, and `(?i)` is ASCII-only.
    private static func splitsAuthor(_ name: String) -> Bool {
        let s = Array(name.unicodeScalars)
        if s.contains(",") { return true }
        guard s.count >= 5 else { return false }
        for i in 0...(s.count - 5) where s[i] == " " && s[i + 4] == " " {
            if (s[i + 1] == "a" || s[i + 1] == "A") && (s[i + 2] == "n" || s[i + 2] == "N") && (s[i + 3] == "d" || s[i + 3] == "D") {
                return true
            }
        }
        return false
    }
}
