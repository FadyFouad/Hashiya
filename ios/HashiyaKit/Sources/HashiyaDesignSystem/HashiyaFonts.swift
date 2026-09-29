import CoreText
import SwiftUI
import UIKit

/// Inter for English, IBM Plex Sans Arabic for Arabic (it has Latin glyphs, so mixed text uses one family).
public enum HashiyaFonts {
    public enum Weight: String, Sendable {
        case regular = "Regular"
        case medium = "Medium"
        case semiBold = "SemiBold"
    }

    private static let files = [
        "Inter-Regular", "Inter-Medium", "Inter-SemiBold",
        "IBMPlexSansArabic-Regular", "IBMPlexSansArabic-Medium", "IBMPlexSansArabic-SemiBold",
    ]

    private static let registration: Void = {
        for name in files {
            guard let url = Bundle.module.url(forResource: name, withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    /// Registers the bundled fonts with Core Text. Safe to call more than once.
    public static func register() {
        _ = registration
    }

    /// Navigation bar titles in the Hashiya font of the current UI language, scaled with Dynamic Type.
    /// Affects navigation bars created afterwards.
    @MainActor
    public static func applyNavigationBarFonts() {
        let name = postScriptName(.semiBold, arabic: HashiyaLanguage.isArabic)
        guard let title = UIFont(name: name, size: 17), let largeTitle = UIFont(name: name, size: 34) else { return }
        let appearance = UINavigationBar.appearance()
        appearance.titleTextAttributes = [.font: UIFontMetrics(forTextStyle: .headline).scaledFont(for: title)]
        appearance.largeTitleTextAttributes = [.font: UIFontMetrics(forTextStyle: .largeTitle).scaledFont(for: largeTitle)]
    }

    /// The PostScript name, e.g. "Inter-SemiBold" or "IBMPlexSansArabic-SemiBold".
    public static func postScriptName(_ weight: Weight, arabic: Bool) -> String {
        (arabic ? "IBMPlexSansArabic-" : "Inter-") + weight.rawValue
    }
}

/// The type roles. Sizes scale with Dynamic Type relative to the given text style.
public enum HashiyaTextStyle: CaseIterable, Sendable {
    case previewTitle, stateTitle, cardTitle, body, meta, label, badge

    var size: CGFloat {
        switch self {
        case .previewTitle: 22
        case .stateTitle: 16
        case .cardTitle, .body: 14
        case .meta, .label: 12
        case .badge: 11
        }
    }

    var weight: HashiyaFonts.Weight {
        switch self {
        case .previewTitle, .stateTitle, .cardTitle: .semiBold
        case .body, .meta: .regular
        case .label, .badge: .medium
        }
    }

    var relativeTo: Font.TextStyle {
        switch self {
        case .previewTitle: .title2
        case .stateTitle: .headline
        case .cardTitle, .body: .subheadline
        case .meta, .label: .caption
        case .badge: .caption2
        }
    }
}

extension Font {
    /// The Hashiya font for `style` in the current UI language.
    @MainActor
    public static func hashiya(_ style: HashiyaTextStyle) -> Font {
        .custom(
            HashiyaFonts.postScriptName(style.weight, arabic: HashiyaLanguage.isArabic),
            size: style.size,
            relativeTo: style.relativeTo
        )
    }
}
