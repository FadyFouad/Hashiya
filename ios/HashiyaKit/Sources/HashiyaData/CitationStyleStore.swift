import Foundation
import HashiyaModel
import Observation

/// The style Copy citation and Export use first; APA until one is chosen. Observable, so every screen that lists the
/// styles follows a choice made on another one. The app shares one instance.
@Observable
public final class CitationStyleStore: @unchecked Sendable {
    // `@unchecked Sendable`: `style` is only read and written from the main-actor view models and the app container,
    // and `UserDefaults` is itself thread-safe.
    @ObservationIgnored private let defaults: UserDefaults
    private static let key = "citationStyle"

    public private(set) var style: CitationStyle

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        style = CitationStyle(storedValue: defaults.string(forKey: Self.key))
    }

    public func set(_ style: CitationStyle) {
        defaults.set(style.rawValue, forKey: Self.key)
        self.style = style
    }
}
