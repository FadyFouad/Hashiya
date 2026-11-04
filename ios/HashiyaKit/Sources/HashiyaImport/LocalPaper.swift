import HashiyaModel

extension ImportedEntry {
    /// The paper to save for an entry OpenAlex didn't match. Paper has no general URL field, so only a link to a PDF
    /// is kept, as the PDF link. Publication details are kept for citations and BibTeX export.
    public func localPaper(uuid: String) -> Paper {
        Paper(
            openAlexID: localPaperIDPrefix + uuid,
            doi: doi,
            title: title,
            authors: authors.map { Author(name: $0) },
            year: year,
            venue: venue,
            abstract: abstract,
            citationCount: 0,
            isOpenAccess: false,
            openAccessPDFURL: url.flatMap { isPDFLink($0) ? $0 : nil },
            publication: PublicationDetails(
                workType: workType,
                sourceType: sourceType,
                publisher: publisher,
                volume: volume,
                issue: issue,
                firstPage: firstPage,
                lastPage: lastPage
            )
        )
    }
}

private func isPDFLink(_ url: String) -> Bool {
    let path = url.split(separator: "#", maxSplits: 1, omittingEmptySubsequences: false).first ?? ""
    let beforeQuery = path.split(separator: "?", maxSplits: 1, omittingEmptySubsequences: false).first ?? ""
    return beforeQuery.lowercased().hasSuffix(".pdf")
}
