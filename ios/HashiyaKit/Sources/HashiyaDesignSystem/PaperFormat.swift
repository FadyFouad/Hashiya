import Foundation
import HashiyaModel

/// How paper data is written in the UI, in the current UI language.
@MainActor
public enum PaperFormat {
    /// The title, or "Untitled" when OpenAlex has none.
    public static func title(_ paper: Paper) -> String {
        paper.title.isEmpty ? L10n.string("designsystem.untitled") : paper.title
    }

    /// A number with the locale's grouping and digits: "128,412", "١٢٨٬٤١٢".
    public static func number(_ value: some BinaryInteger) -> String {
        Int64(value).formatted(.number.locale(HashiyaLanguage.locale))
    }

    /// A year without grouping: "2024", never "2,024".
    public static func year(_ year: Int) -> String {
        year.formatted(.number.grouping(.never).locale(HashiyaLanguage.locale))
    }

    /// The first three names joined with ", ", plus "+N" when there are more. Nil when there are none.
    public static func authorsLine(_ authors: [Author]) -> String? {
        guard !authors.isEmpty else { return nil }
        let names = authors.prefix(3).map(\.name).joined(separator: ", ")
        guard authors.count > 3 else { return names }
        return L10n.format("designsystem.authorsMore", names, number(authors.count - 3))
    }

    /// Authors · year · venue, skipping what is missing.
    public static func cardMeta(_ paper: Paper) -> String {
        [authorsLine(paper.authors), paper.year.map(year), paper.venue].compactMap { $0 }.joined(separator: " · ")
    }

    /// Venue · year · "128,412 citations", for the preview.
    public static func previewMeta(_ paper: Paper) -> String {
        [paper.venue, paper.year.map(year), citations(paper.citationCount)].compactMap { $0 }.joined(separator: " · ")
    }

    /// "128K cited".
    public static func compactCitations(_ count: Int) -> String {
        L10n.format("designsystem.citedCount", count.formatted(.number.notation(.compactName).locale(HashiyaLanguage.locale)))
    }

    /// "128,412 citations".
    public static func citations(_ count: Int) -> String {
        L10n.format("designsystem.citations", number(count))
    }
}

/// The address of a DOI.
public enum DOILink {
    /// `https://doi.org/{doi}` with the DOI percent-encoded for a URL path ("/" kept); nil if no valid URL results.
    public static func url(for doi: String) -> URL? {
        guard let path = doi.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) else { return nil }
        return URL(string: "https://doi.org/" + path)
    }
}
