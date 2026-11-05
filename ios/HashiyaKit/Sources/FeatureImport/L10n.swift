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

    /// A plural string whose count is shown in the locale's digits: "Import 3 papers".
    static func counted(_ key: String, _ count: Int) -> String {
        format(key, Int64(count), PaperFormat.number(count))
    }
}
