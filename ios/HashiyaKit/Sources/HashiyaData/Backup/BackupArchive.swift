import Foundation
import HashiyaDatabase
import ZIPFoundation

struct WrittenArchive: Equatable, Sendable {
    var papers: Int
    var collections: Int
    var missingPdfs: Int
}

/// Writes `snapshot` as a `.hashiya` archive at `url`. With `includePdfs`, each stored PDF that `pdfFile` finds is copied in
/// (stored, not compressed: PDFs barely compress); one that is missing is written with `"file": null` and counted. PDFs go
/// first and the JSON last, so `library.json` only names entries that were really written. Checks cancellation per PDF.
func writeArchive(
    _ snapshot: BackupSnapshot,
    includePdfs: Bool,
    pdfFile: (String) -> URL,
    manifest: (_ papers: Int, _ collections: Int) -> BackupManifest,
    to url: URL,
    onProgress: (Double) -> Void
) throws -> WrittenArchive {
    let archive = try Archive(url: url, accessMode: .create)
    var refs: [String: Int] = [:]
    for (index, row) in snapshot.papers.enumerated() {
        refs[row.paper.id] = index + 1
    }
    let notesByPaper = Dictionary(snapshot.notes.map { ($0.paperID, $0) }, uniquingKeysWith: { first, _ in first })
    let withPdf = snapshot.papers.filter { $0.paper.pdfSource != nil }
    var included = Set<String>()
    var missing = 0
    if includePdfs {
        for (index, row) in withPdf.enumerated() {
            try Task.checkCancellation()
            let source = pdfFile(row.paper.id)
            if FileManager.default.fileExists(atPath: source.path), let ref = refs[row.paper.id] {
                try archive.addEntry(with: BackupFormat.pdfEntry(ref: ref), fileURL: source, compressionMethod: .none)
                included.insert(row.paper.id)
            } else {
                missing += 1
            }
            onProgress(Double(index + 1) / Double(withPdf.count + 1))
        }
    }
    let linksByCollection = Dictionary(grouping: snapshot.links, by: \.collectionID)
    let papers = snapshot.papers.map { row -> BackupPaper in
        let paper = row.paper
        let ref = refs[paper.id] ?? 0
        let notes = notesByPaper[paper.id]
        return BackupPaper(
            ref: ref,
            openAlexId: paper.openAlexID,
            doi: paper.doi,
            title: paper.title,
            year: paper.year,
            venue: paper.venue,
            abstract: paper.abstract,
            citationCount: paper.citationCount,
            isOpenAccess: paper.isOpenAccess,
            oaPdfUrl: paper.oaPDFURL,
            savedAt: paper.savedAt,
            readingStatus: paper.readingStatus,
            workType: paper.workType,
            sourceType: paper.sourceType,
            publisher: paper.publisher,
            volume: paper.volume,
            issue: paper.issue,
            firstPage: paper.firstPage,
            lastPage: paper.lastPage,
            citeKey: paper.citeKey,
            detailsFetched: paper.detailsFetched,
            authors: row.authors.sorted { $0.position < $1.position }.map {
                BackupAuthor(name: $0.name, openAlexAuthorId: $0.openAlexAuthorID)
            },
            notes: notes.map {
                BackupNotes(
                    summary: $0.summary,
                    researchQuestion: $0.researchQuestion,
                    method: $0.method,
                    keyFindings: $0.keyFindings,
                    limitations: $0.limitations,
                    thoughts: $0.thoughts,
                    updatedAt: $0.updatedAt
                )
            },
            pdf: paper.pdfSource.map { source in
                BackupPdf(
                    source: source == "downloaded" ? "downloaded" : "attached",
                    addedAt: paper.pdfAddedAt ?? 0,
                    lastPage: paper.pdfLastPage ?? 0,
                    file: included.contains(paper.id) ? BackupFormat.pdfEntry(ref: ref) : nil
                )
            }
        )
    }
    let collections = snapshot.collections.map { collection in
        BackupCollection(
            name: collection.name,
            createdAt: collection.createdAt,
            papers: (linksByCollection[collection.id ?? -1] ?? []).compactMap { refs[$0.paperID] }
        )
    }
    try add(archive, BackupFormat.libraryEntry, try BackupFormat.encoder.encode(BackupLibrary(papers: papers, collections: collections)))
    try add(archive, BackupFormat.manifestEntry, try BackupFormat.encoder.encode(manifest(papers.count, collections.count)))
    onProgress(1)
    return WrittenArchive(papers: papers.count, collections: collections.count, missingPdfs: missing)
}

private func add(_ archive: Archive, _ path: String, _ data: Data) throws {
    try archive.addEntry(with: path, type: .file, uncompressedSize: Int64(data.count), compressionMethod: .deflate) { position, size in
        data.subdata(in: Int(position)..<(Int(position) + size))
    }
}

enum ArchiveRead: Equatable {
    case valid(BackupManifest, BackupLibrary)
    case invalid(OpenFailure)
}

/// Reads and checks the manifest and library of the archive at `url`. Only those two entries are read; PDFs are read by
/// exact name at restore. Never throws: anything unreadable is a typed failure.
func readArchive(_ url: URL) -> ArchiveRead {
    guard let archive = try? Archive(url: url, accessMode: .read) else {
        return .invalid(.notABackup)
    }
    guard let manifestEntry = archive[BackupFormat.manifestEntry],
          let manifestData = readEntry(archive, manifestEntry, max: BackupFormat.maxManifestBytes),
          let manifest = try? BackupFormat.decoder.decode(BackupManifest.self, from: manifestData)
    else {
        return .invalid(.notABackup)
    }
    if manifest.format > BackupFormat.version {
        return .invalid(.newerFormat)
    }
    if manifest.format < 1 {
        return .invalid(.damaged)
    }
    guard let libraryEntry = archive[BackupFormat.libraryEntry],
          let libraryData = readEntry(archive, libraryEntry, max: BackupFormat.maxLibraryBytes),
          let library = try? BackupFormat.decoder.decode(BackupLibrary.self, from: libraryData)
    else {
        return .invalid(.damaged)
    }
    let refs = library.papers.map(\.ref)
    guard Set(refs).count == refs.count else {
        return .invalid(.damaged)
    }
    let known = Set(refs)
    guard library.collections.allSatisfy({ $0.papers.allSatisfy(known.contains) }) else {
        return .invalid(.damaged)
    }
    return .valid(manifest, library)
}

/// The entry's bytes, or nil when more than `max` come out or it can't be read. Never trusts the declared size.
private func readEntry(_ archive: Archive, _ entry: Entry, max: Int) -> Data? {
    struct TooLarge: Error {}
    var data = Data()
    do {
        _ = try archive.extract(entry, skipCRC32: false) { chunk in
            data.append(chunk)
            if data.count > max {
                throw TooLarge()
            }
        }
        return data
    } catch {
        return nil
    }
}

extension BackupPaper {
    /// The OpenAlex id trimmed; nil when missing or blank. Papers without one are skipped on restore: the app keys every
    /// paper by its OpenAlex id.
    var usableOpenAlexID: String? {
        let trimmed = openAlexId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? nil : trimmed
    }
}
