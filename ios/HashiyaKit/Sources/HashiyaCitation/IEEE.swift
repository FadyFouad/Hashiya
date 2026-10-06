import HashiyaModel

/// IEEE references, from the data a saved paper has.
public enum IEEE {
    public static func format(_ paper: Paper) -> StyledCitation {
        var b = CitationBuilder()
        let d = paper.publication
        let kind = d.workKind
        let venue = paper.venue.nonBlank
        let publisher = d.publisher.nonBlank
        let year = paper.year.map(String.init)
        let doi = paper.doi.nonBlank.map { "doi: \(doiOf($0))" }
        let pages = pageRange(d.firstPage, d.lastPage).map { isSinglePage(d.firstPage, d.lastPage) ? "p. \($0)" : "pp. \($0)" }
        let title = Optional(paper.title).nonBlank

        let names = authors(paper.authors.map(\.name))
        if !names.isEmpty {
            b.append(names)
            b.text(", ")
        }
        if kind == .book {
            b.italic(title ?? "Untitled")
            b.endSentence()
            let rest = [publisher ?? venue, year, doi].compactMap { $0 }
            if !rest.isEmpty {
                b.text(" " + rest.joined(separator: ", "))
                b.endSentence()
            }
        } else {
            let tail: [[Run]]
            switch kind {
            case .article:
                tail = [
                    venue.map { [Run($0, italic: true)] },
                    d.volume.nonBlank.map { [Run("vol. \($0)")] },
                    d.issue.nonBlank.map { [Run("no. \($0)")] },
                    pages.map { [Run($0)] },
                    year.map { [Run($0)] },
                    doi.map { [Run($0)] },
                ].compactMap { $0 }
            case .conference:
                tail = [
                    venue.map { [Run("in "), Run($0, italic: true)] },
                    year.map { [Run($0)] },
                    pages.map { [Run($0)] },
                    doi.map { [Run($0)] },
                ].compactMap { $0 }
            case .chapter:
                let source: [Run]?
                if let venue {
                    let joiner = ".?!".contains(venue.last!) ? " " : ". "
                    source = [Run("in "), Run(venue, italic: true)] + (publisher.map { [Run(joiner + $0)] } ?? [])
                } else {
                    source = publisher.map { [Run($0)] }
                }
                tail = [
                    source,
                    year.map { [Run($0)] },
                    pages.map { [Run($0)] },
                    doi.map { [Run($0)] },
                ].compactMap { $0 }
            case .thesis:
                tail = [
                    [Run("Thesis")],
                    venue.map { [Run($0)] },
                    year.map { [Run($0)] },
                    doi.map { [Run($0)] },
                ].compactMap { $0 }
            case .report:
                tail = [
                    (publisher ?? venue).map { [Run($0)] },
                    [Run("Tech. Rep.")],
                    year.map { [Run($0)] },
                    doi.map { [Run($0)] },
                ].compactMap { $0 }
            default:
                tail = [
                    venue.map { [Run($0)] },
                    year.map { [Run($0)] },
                    doi.map { [Run($0)] },
                ].compactMap { $0 }
            }
            let shown = title ?? "Untitled"
            let closing = ".?!".contains(shown.last!) ? "" : (tail.isEmpty ? "." : ",")
            b.text("\"" + shown + closing + "\"")
            if !tail.isEmpty {
                b.text(" ")
                for (i, part) in tail.enumerated() {
                    if i > 0 { b.text(", ") }
                    b.append(part)
                }
                b.endSentence()
            }
        }
        if doi == nil, let url = paper.openAccessPDFURL.nonBlank { b.text(" [Online]. Available: \(url)") }
        return b.build()
    }

    /// Numbered [1], [2], … in the order given (the library's saved order).
    public static func list(_ papers: [Paper]) -> [StyledCitation] {
        papers.enumerated().map { i, p in
            var b = CitationBuilder()
            b.append([Run("[\(i + 1)] ")] + format(p).runs)
            return b.build()
        }
    }

    static func authors(_ names: [String]) -> [Run] {
        let written = names.map { name -> String in
            let p = personName(name)
            return p.initials.map { "\($0) \(p.family)" } ?? p.family
        }
        switch written.count {
        case 0: return []
        case 1: return [Run(written[0])]
        case 2: return [Run("\(written[0]) and \(written[1])")]
        case 3...6: return [Run(written.dropLast().joined(separator: ", ") + ", and " + written.last!)]
        default: return [Run("\(written[0]) "), Run("et al.", italic: true)]
        }
    }
}
