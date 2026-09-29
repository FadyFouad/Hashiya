import SwiftUI

/// The UI language: English or Arabic. Follows the app's language (iOS Settings); snapshot tests
/// set `override` to render both languages in one run.
@MainActor
public enum HashiyaLanguage {
    /// "en" or "ar" to force a language (tests only); nil follows the system.
    public static var override: String?

    /// "ar" when the UI language is Arabic, else "en".
    public static var code: String {
        if let override { return override }
        return resolvedCode(preferredLocalizations: Bundle.main.preferredLocalizations)
    }

    /// "ar" when the app's preferred localization is Arabic (any region), else "en". This is the language
    /// the string bundles resolve to, which can differ from `Locale.current` (e.g. device languages [fr, ar]).
    nonisolated static func resolvedCode(preferredLocalizations: [String]) -> String {
        guard let first = preferredLocalizations.first else { return "en" }
        let language = first.split(whereSeparator: { $0 == "-" || $0 == "_" }).first.map(String.init)
        return language?.lowercased() == "ar" ? "ar" : "en"
    }

    public static var isArabic: Bool { code == "ar" }

    /// The locale used to format numbers.
    public static var locale: Locale {
        if let override { return Locale(identifier: override) }
        let current = Locale.current
        return current.language.languageCode?.identifier == code ? current : Locale(identifier: code)
    }

    public static var layoutDirection: LayoutDirection {
        isArabic ? .rightToLeft : .leftToRight
    }
}

/// Looks up a target's `Localizable.xcstrings` in the UI language.
@MainActor
public enum HashiyaStrings {
    /// Tests only: while non-nil, every string looked up or formatted is appended, so a snapshot
    /// can check that it was rendered with Arabic strings.
    public static var recordedLookups: [String]?

    /// The string for `key`. A key with no translation (`shouldTranslate: false`) falls back to English.
    public static func string(_ key: String, bundle: Bundle) -> String {
        var value = localized(bundle).localizedString(forKey: key, value: nil, table: nil)
        if value == key, let english = lproj("en", in: bundle) {
            value = english.localizedString(forKey: key, value: nil, table: nil)
        }
        recordedLookups?.append(value)
        return value
    }

    /// Formats with the UI locale, which also picks the plural form.
    public static func format(_ key: String, bundle: Bundle, _ arguments: [any CVarArg]) -> String {
        let value = String(format: string(key, bundle: bundle), locale: HashiyaLanguage.locale, arguments: arguments)
        recordedLookups?.append(value)
        return value
    }

    private static func localized(_ bundle: Bundle) -> Bundle {
        HashiyaLanguage.override.flatMap { lproj($0, in: bundle) } ?? bundle
    }

    private static func lproj(_ language: String, in bundle: Bundle) -> Bundle? {
        bundle.path(forResource: language, ofType: "lproj").flatMap(Bundle.init(path:))
    }
}

/// This target's strings.
@MainActor
enum L10n {
    static func string(_ key: String) -> String {
        HashiyaStrings.string(key, bundle: .module)
    }

    static func format(_ key: String, _ arguments: any CVarArg...) -> String {
        HashiyaStrings.format(key, bundle: .module, arguments)
    }
}
