import HashiyaModel

func paper(
    title: String = "Attention is all you need",
    authors names: [String] = ["Ashish Vaswani", "Noam Shazeer"],
    year: Int? = 2017,
    venue: String? = "Advances in Neural Information Processing Systems",
    doi: String? = "10.5555/3295222.3295349",
    pdf: String? = nil,
    work: String? = "article",
    source: String? = "journal",
    publisher: String? = nil,
    volume: String? = nil,
    issue: String? = nil,
    first: String? = nil,
    last: String? = nil
) -> Paper {
    Paper(
        openAlexID: "W1",
        doi: doi,
        title: title,
        authors: names.map { Author(name: $0, openAlexID: nil) },
        year: year,
        venue: venue,
        isOpenAccess: pdf != nil,
        openAccessPDFURL: pdf,
        publication: PublicationDetails(
            workType: work, sourceType: source, publisher: publisher, volume: volume, issue: issue, firstPage: first, lastPage: last
        )
    )
}
