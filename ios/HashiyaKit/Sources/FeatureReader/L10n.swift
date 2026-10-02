import Foundation
import HashiyaDesignSystem

/// This target's strings.
@MainActor
enum L10n {
    static func string(_ key: String) -> String {
        HashiyaStrings.string(key, bundle: .module)
    }

    static func format(_ key: String, _ arguments: any CVarArg...) -> String {
        HashiyaStrings.format(key, bundle: .module, arguments)
    }

    /// "3 of 14" from a zero-based index, with the numbers formatted in the app's language.
    static func page(_ index: Int, of count: Int) -> String {
        format("reader.page", PaperFormat.number(index + 1), PaperFormat.number(count))
    }
}
