import Foundation
import HashiyaDatabase
import HashiyaModel
import HashiyaNetwork

private let openAlexPrefix = "https://openalex.org/"

private func shortOpenAlexID(_ id: String) -> String {
    id.hasPrefix(openAlexPrefix) ? String(id.dropFirst(openAlexPrefix.count)) : id
}

extension NetworkWork {
    public func asPaper() -> Paper {
        Paper(
            openAlexID: shortOpenAlexID(id),
            doi: doi.flatMap(normalizeDOI),
            title: displayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "",
            authors: authorships.compactMap { authorship in
                guard let name = authorship.author.displayName,
                      !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
                return Author(name: name, openAlexID: authorship.author.id.map(shortOpenAlexID))
            },
            year: publicationYear,
            venue: primaryLocation?.source?.displayName,
            abstract: rebuildAbstract(abstractInvertedIndex),
            citationCount: citedByCount,
            isOpenAccess: openAccess?.isOA ?? false,
            openAccessPDFURL: bestOALocation?.pdfURL
        )
    }
}

extension PaperWithAuthors {
    public func asPaper() -> Paper {
        Paper(
            // Every paper saved by this version has an OpenAlex ID.
            openAlexID: paper.openAlexID ?? "",
            doi: paper.doi,
            title: paper.title,
            authors: authors.sorted { $0.position < $1.position }.map { Author(name: $0.name, openAlexID: $0.openAlexAuthorID) },
            year: paper.year,
            venue: paper.venue,
            abstract: paper.abstract,
            citationCount: paper.citationCount,
            isOpenAccess: paper.isOpenAccess,
            openAccessPDFURL: paper.oaPDFURL
        )
    }
}

extension Paper {
    public func asRecords(localID: String, savedAt: Int64) -> PaperWithAuthors {
        PaperWithAuthors(
            paper: PaperRecord(
                id: localID,
                openAlexID: openAlexID,
                doi: doi,
                title: title,
                year: year,
                venue: venue,
                abstract: abstract,
                citationCount: citationCount,
                isOpenAccess: isOpenAccess,
                oaPDFURL: openAccessPDFURL,
                savedAt: savedAt
            ),
            authors: authors.enumerated().map { index, author in
                PaperAuthorRecord(paperID: localID, position: index, name: author.name, openAlexAuthorID: author.openAlexID)
            }
        )
    }
}
