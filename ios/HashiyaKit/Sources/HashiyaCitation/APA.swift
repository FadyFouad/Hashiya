import HashiyaModel

/// APA 7 references, from the data a saved paper has.
public enum APA {
    private static let italicTitle: Set<WorkKind> = [.book, .thesis, .report, .preprint, .other]

    public static func format(_ paper: Paper) -> StyledCitation {
        var b = CitationBuilder()
        let kind = paper.publication.workKind
        let venue = paper.venue.nonBlank
        let year = "(\(paper.year.map(String.init) ?? "n.d."))."
        if paper.authors.isEmpty {
            title(&b, paper, kind, venue)
            b.text(" \(year)")
        } else {
            b.text(authors(paper.authors.map(\.name)))
            b.endSentence()
            b.text(" \(year) ")
            title(&b, paper, kind, venue)
        }
        source(&b, paper, kind, venue)
        if let link = link(paper) { b.text(" \(link)") }
        return b.build()
    }

    /// Sorted by first author's family name (no authors: the title), then year (none last), then title.
    public static func list(_ papers: [Paper]) -> [StyledCitation] {
        papers.enumerated().sorted { l, r in
            let a = l.element, c = r.element
            let ka = sortKey(a.authors.first.map { personName($0.name).family } ?? a.title)
            let kc = sortKey(c.authors.first.map { personName($0.name).family } ?? c.title)
            if ka != kc { return ka < kc }
            if (a.year == nil) != (c.year == nil) { return a.year != nil }
            if let ya = a.year, let yc = c.year, ya != yc { return ya < yc }
            let ta = sortKey(a.title), tc = sortKey(c.title)
            if ta != tc { return ta < tc }
            return l.offset < r.offset
        }.map { format($0.element) }
    }

    static func authors(_ names: [String]) -> String {
        let written = names.map { name -> String in
            let p = personName(name)
            return p.initials.map { "\(p.family), \($0)" } ?? p.family
        }
        if written.count == 1 { return written[0] }
        if written.count <= 20 { return written.dropLast().joined(separator: ", ") + ", & " + written.last! }
        return written.prefix(19).joined(separator: ", ") + ", . . . " + written.last!
    }

    private static func title(_ b: inout CitationBuilder, _ paper: Paper, _ kind: WorkKind, _ venue: String?) {
        if let title = Optional(paper.title).nonBlank {
            if italicTitle.contains(kind) { b.italic(title) } else { b.text(title) }
        } else {
            b.text("[Untitled]")
        }
        switch kind {
        case .thesis: b.text(" [Thesis" + (venue.map { ", \($0)" } ?? "") + "]")
        case .preprint: b.text(" [Preprint]")
        default: break
        }
        b.endSentence()
    }

    private static func source(_ b: inout CitationBuilder, _ paper: Paper, _ kind: WorkKind, _ venue: String?) {
        let d = paper.publication
        let publisher = d.publisher.nonBlank
        let pages = pageRange(d.firstPage, d.lastPage)
        switch kind {
        case .article:
            if let venue {
                b.text(" ")
                b.italic(venue)
                let volume = d.volume.nonBlank
                if let volume {
                    b.text(", ")
                    b.italic(volume)
                }
                if let issue = d.issue.nonBlank { b.text(volume == nil ? ", (\(issue))" : "(\(issue))") }
                if let pages { b.text(", \(pages)") }
                b.endSentence()
            }
        case .conference, .chapter:
            if let venue {
                b.text(" In ")
                b.italic(venue)
                if let pages { b.text(isSinglePage(d.firstPage, d.lastPage) ? " (p. \(pages))" : " (pp. \(pages))") }
                b.endSentence()
            }
            if let publisher {
                b.text(" \(publisher)")
                b.endSentence()
            }
        case .book:
            if let publisher {
                b.text(" \(publisher)")
                b.endSentence()
            }
        case .thesis:
            break
        case .report:
            if let it = publisher ?? venue {
                b.text(" \(it)")
                b.endSentence()
            }
        case .preprint, .other:
            if let venue {
                b.text(" \(venue)")
                b.endSentence()
            }
        }
    }

    private static func link(_ paper: Paper) -> String? {
        paper.doi.nonBlank.map { "https://doi.org/\(doiOf($0))" } ?? paper.openAccessPDFURL.nonBlank
    }
}
