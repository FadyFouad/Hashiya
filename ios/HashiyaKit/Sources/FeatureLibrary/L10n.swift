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

    /// First author (alone when there is exactly one, else "et al."), year and venue.
    static func rowMeta(_ paper: Paper) -> String {
        let author: String? = switch paper.authors.count {
        case 0: nil
        case 1: paper.authors[0].name
        default: format("library.etAl", paper.authors[0].name)
        }
        return [author, paper.year.map(PaperFormat.year), paper.venue].compactMap { $0 }.joined(separator: " · ")
    }
}
