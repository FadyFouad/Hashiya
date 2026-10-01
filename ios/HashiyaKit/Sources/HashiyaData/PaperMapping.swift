import Foundation
import HashiyaDatabase
import HashiyaModel
import HashiyaNetwork

private let openAlexPrefix = "https://openalex.org/"

private func shortOpenAlexID(_ id: String) -> String {
    id.hasPrefix(openAlexPrefix) ? String(id.dropFirst(openAlexPrefix.count)) : id
}

/// `value` trimmed, or nil when that leaves nothing.
private func nilIfBlank(_ value: String?) -> String? {
    guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else { return nil }
    return trimmed
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
            openAccessPDFURL: bestOALocation?.pdfURL,
            publication: asPublicationDetails()
        )
    }

    /// The citation details OpenAlex reports for this work; blank strings become nil.
    func asPublicationDetails() -> PublicationDetails {
        let source = primaryLocation?.source
        return PublicationDetails(
            workType: nilIfBlank(type),
            sourceType: nilIfBlank(source?.type),
            publisher: nilIfBlank(source?.hostOrganizationName),
            volume: nilIfBlank(biblio?.volume),
            issue: nilIfBlank(biblio?.issue),
            firstPage: nilIfBlank(biblio?.firstPage),
            lastPage: nilIfBlank(biblio?.lastPage)
        )
    }
}

extension PaperWithAuthors {
    /// The paper with its stored status; an unknown stored value is To read.
    public func asLibraryPaper() -> LibraryPaper {
        LibraryPaper(paper: asPaper(), status: ReadingStatus(stored: paper.readingStatus), hasPdf: paper.pdfSource != nil)
    }

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
            openAccessPDFURL: paper.oaPDFURL,
            publication: paper.publication
        )
    }
}

extension Paper {
    /// A new save has its details from this OpenAlex response (`detailsFetched` true) and no key yet; Undo passes back the
    /// removed paper's key and flag.
    public func asRecords(
        localID: String,
        savedAt: Int64,
        status: ReadingStatus = .toRead,
        citeKey: String? = nil,
        detailsFetched: Bool = true,
        pdf: PaperPdf? = nil
    ) -> PaperWithAuthors {
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
                savedAt: savedAt,
                readingStatus: status.storedValue,
                publication: publication,
                citeKey: citeKey,
                detailsFetched: detailsFetched,
                pdf: pdf
            ),
            authors: authors.enumerated().map { index, author in
                PaperAuthorRecord(paperID: localID, position: index, name: author.name, openAlexAuthorID: author.openAlexID)
            }
        )
    }
}
