import Foundation
import HashiyaDatabase
import HashiyaModel

private let storedStatuses: Set<String> = ["to_read", "reading", "read"]

extension BackupPaper {
    /// The paper as rows under `localID`. Its PDF columns are set only when `staged` holds its file; notes with no text are
    /// dropped, like the app never stores empty notes.
    func toIncoming(localID: String, openAlexID: String, staged: (url: URL, size: Int64)?) -> IncomingPaper {
        var record = PaperRecord(
            id: localID,
            openAlexID: openAlexID,
            doi: doi.flatMap(normalizeDOI),
            title: title,
            year: year,
            venue: venue,
            abstract: abstract,
            citationCount: citationCount,
            isOpenAccess: isOpenAccess,
            oaPDFURL: oaPdfUrl,
            savedAt: savedAt,
            readingStatus: storedStatuses.contains(readingStatus) ? readingStatus : "to_read",
            publication: PublicationDetails(
                workType: workType,
                sourceType: sourceType,
                publisher: publisher,
                volume: volume,
                issue: issue,
                firstPage: firstPage,
                lastPage: lastPage
            ),
            citeKey: citeKey.flatMap { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : $0 },
            detailsFetched: detailsFetched
        )
        if let staged, let pdf {
            record.pdfSource = pdf.source == "downloaded" ? "downloaded" : "attached"
            record.pdfSize = staged.size
            record.pdfAddedAt = pdf.addedAt
            record.pdfLastPage = max(pdf.lastPage, 0)
        }
        let noteRecord = notes.flatMap { notes -> PaperNotesRecord? in
            let value = PaperNotes(
                summary: notes.summary,
                researchQuestion: notes.researchQuestion,
                method: notes.method,
                keyFindings: notes.keyFindings,
                limitations: notes.limitations,
                thoughts: notes.thoughts
            )
            return value.isEmpty ? nil : PaperNotesRecord(paperID: localID, notes: value, updatedAt: notes.updatedAt)
        }
        return IncomingPaper(
            ref: ref,
            paper: record,
            authors: authors.enumerated().map { index, author in
                PaperAuthorRecord(paperID: localID, position: index, name: author.name, openAlexAuthorID: author.openAlexAuthorId)
            },
            notes: noteRecord
        )
    }
}
