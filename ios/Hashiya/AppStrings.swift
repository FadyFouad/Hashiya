import Foundation

/// The app target's own strings, from its Localizable.xcstrings.
enum AppStrings {
    static func string(_ key: String) -> String {
        Bundle.main.localizedString(forKey: key, value: nil, table: nil)
    }
}
