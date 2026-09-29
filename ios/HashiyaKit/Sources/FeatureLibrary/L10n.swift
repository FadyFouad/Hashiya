import Foundation
import HashiyaDesignSystem
import HashiyaModel

/// This target's strings.
@MainActor
enum L10n {
    static func string(_ key: String) -> String {
        HashiyaStrings.string(key, bundle: .module)
    }

    static func format(_ key: String, _ arguments: any CVarArg...) -> String {
        HashiyaStrings.format(key, bundle: .module, arguments)
    }

    /// "3 papers".
    static func paperCount(_ count: Int) -> String {
        format("library.paperCount", Int64(count), PaperFormat.number(count))
    }

    /// First author (alone when there is exactly one, else "et al."), year and venue. The "et al." part
    /// is isolated (FSI…PDI): in Arabic it ends in Arabic, and the year after it would otherwise join
    /// that right-to-left run and show before it in a left-to-right line.
    static func rowMeta(_ paper: Paper) -> String {
        let author: String? = switch paper.authors.count {
        case 0: nil
        case 1: paper.authors[0].name
        default: "\u{2068}" + format("library.etAl", paper.authors[0].name) + "\u{2069}"
        }
        return [author, paper.year.map(PaperFormat.year), paper.venue].compactMap { $0 }.joined(separator: " · ")
    }
}
