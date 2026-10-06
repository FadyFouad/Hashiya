import Foundation
import HashiyaModel

/// The style Copy citation and Export use first; APA until one is chosen.
public final class CitationStyleStore: @unchecked Sendable {
    private let defaults: UserDefaults
    private static let key = "citationStyle"

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public var style: CitationStyle { CitationStyle(storedValue: defaults.string(forKey: Self.key)) }

    public func set(_ style: CitationStyle) { defaults.set(style.rawValue, forKey: Self.key) }
}
