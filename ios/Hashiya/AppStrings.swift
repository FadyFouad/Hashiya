import Foundation
import HashiyaDesignSystem

/// The app target's own strings, from its Localizable.xcstrings.
@MainActor
enum AppStrings {
    static func string(_ key: String) -> String {
        HashiyaStrings.string(key, bundle: .main)
    }
}
