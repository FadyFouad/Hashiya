import Foundation
import HashiyaModel
import HashiyaNetwork

public protocol PaperLookupRepository: Sendable {
    /// The paper `identifier` names. Cancelling the calling task ends the lookup; its result must not be used.
    func lookup(_ identifier: PaperIdentifier) async -> LookupResult
}

public enum LookupResult: Equatable, Sendable {
    case found(Paper)
    /// `arxivTitle`: arXiv's title for an arXiv ID OpenAlex couldn't match, when arXiv knows it.
    case notFound(arxivTitle: String?)
    case failed(SearchError)
}

/// DOIs resolve directly. arXiv IDs try the arXiv DOI first (trusted), then OpenAlex's landing-page filter, whose
/// single match is accepted only if its title matches arXiv's title — OpenAlex sometimes attaches the wrong work.
public struct OpenAlexPaperLookupRepository: PaperLookupRepository {
    private let openAlex: any OpenAlexLookupService
    private let arxiv: any ArxivTitleService

    public init(openAlex: any OpenAlexLookupService, arxiv: any ArxivTitleService) {
        self.openAlex = openAlex
        self.arxiv = arxiv
    }

    public func lookup(_ identifier: PaperIdentifier) async -> LookupResult {
        do {
            switch identifier {
            case let .doi(doi):
                return try await openAlex.work(id: "doi:" + doi).map { .found($0.asPaper()) } ?? .notFound(arxivTitle: nil)
            case let .arxiv(id):
                return try await lookupArxiv(id)
            }
        } catch let failure as NetworkFailure {
            return .failed(failure.asSearchError())
        } catch {
            // Cancelled: the caller ignores the result.
            return .failed(.unexpected)
        }
    }

    private func lookupArxiv(_ id: String) async throws -> LookupResult {
        if let work = try await openAlex.work(id: "doi:10.48550/arXiv." + id) {
            return .found(work.asPaper())
        }
        var seen = Set<String>()
        let matches = try await openAlex.works(filter: arxivLandingPageFilter(id), perPage: 2).results
            .filter { seen.insert($0.id).inserted }
        guard matches.count == 1 else {
            // The title only labels the "Search for …" button, so a failure here is not an error.
            return .notFound(arxivTitle: try? await arxiv.title(id: id))
        }
        let arxivTitle: String?
        do {
            arxivTitle = try await arxiv.title(id: id)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            // OpenAlex has just answered, so the device is online: arXiv trouble is the service being unavailable.
            return .failed(.serviceUnavailable)
        }
        guard let arxivTitle else { return .notFound(arxivTitle: nil) }
        let paper = matches[0].asPaper()
        return titlesMatch(paper.title, arxivTitle) ? .found(paper) : .notFound(arxivTitle: arxivTitle)
    }
}

/// OpenAlex filter matching any landing page OpenAlex stores for an arXiv paper: the abs page over http or https,
/// with no version or v1–v5, and the arXiv DOI page. `|` means "any of" and keeps this a single request.
func arxivLandingPageFilter(_ id: String) -> String {
    let versions = [""] + (1...5).map { "v\($0)" }
    let absPages = ["http", "https"].flatMap { scheme in versions.map { "\(scheme)://arxiv.org/abs/\(id)\($0)" } }
    return "locations.landing_page_url:" + (absPages + ["https://doi.org/10.48550/arxiv.\(id)"]).joined(separator: "|")
}

/// True when both titles have the same letters and digits in the same order, ignoring case, accents and punctuation.
func titlesMatch(_ a: String, _ b: String) -> Bool {
    let left = normalizedTitle(a)
    return !left.isEmpty && left == normalizedTitle(b)
}

/// Decomposes and drops accents first, so "ö" written as one character or as "o" + a mark (or plain "o") match.
private func normalizedTitle(_ title: String) -> String {
    let unmarked = title.decomposedStringWithCompatibilityMapping.unicodeScalars.filter { !isMark($0) }
    var result = ""
    var pendingSpace = false
    for scalar in String(String.UnicodeScalarView(unmarked)).lowercased().unicodeScalars {
        if isLetterOrNumber(scalar) {
            if pendingSpace, !result.isEmpty { result.append(" ") }
            pendingSpace = false
            result.unicodeScalars.append(scalar)
        } else {
            pendingSpace = true
        }
    }
    return result
}

private func isMark(_ scalar: Unicode.Scalar) -> Bool {
    switch scalar.properties.generalCategory {
    case .nonspacingMark, .spacingMark, .enclosingMark: true
    default: false
    }
}

private func isLetterOrNumber(_ scalar: Unicode.Scalar) -> Bool {
    switch scalar.properties.generalCategory {
    case .uppercaseLetter, .lowercaseLetter, .titlecaseLetter, .modifierLetter, .otherLetter,
         .decimalNumber, .letterNumber, .otherNumber:
        true
    default:
        false
    }
}
