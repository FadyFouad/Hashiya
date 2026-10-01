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

    /// A chip: "Reading · 3"; in Arabic "قيد القراءة (3)", in the locale's digits.
    static func filterCount(_ label: String, _ count: Int) -> String {
        format("library.filterCount", label, PaperFormat.number(count))
    }

    /// The badge for VoiceOver: "Status: To read. Change status".
    static func statusBadgeDescription(_ status: ReadingStatus) -> String {
        format("library.statusBadgeDescription", readingStatusLabel(status))
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

    /// A collection's name inside a sentence, isolated (FSI…PDI) so an Arabic name in the English UI keeps its place.
    /// Arabic formatting isolates every argument itself, so Arabic gets the name as it is.
    static func isolated(_ name: String) -> String {
        HashiyaLanguage.isArabic ? name : "\u{2068}" + name + "\u{2069}"
    }

    /// The Undo banner after a swipe in a collection: "Removed from Thesis".
    static func removedFromCollection(_ name: String) -> String {
        format("library.removedFromCollection", isolated(name))
    }

    /// The title menu's Rename item: "Rename "Thesis"".
    static func renameCollection(_ name: String) -> String {
        format("library.renameCollection", isolated(name))
    }

    /// The title menu's Delete item: "Delete "Thesis"…".
    static func deleteCollection(_ name: String) -> String {
        format("library.deleteCollection", isolated(name))
    }

    /// The delete confirmation's title: "Delete "Thesis"?".
    static func deleteCollectionTitle(_ name: String) -> String {
        format("library.deleteCollectionTitle", isolated(name))
    }
}
