import Foundation


private let doiPrefixes = [
    "https://doi.org/",
    "http://doi.org/",
    "https://dx.doi.org/",
    "http://dx.doi.org/",
    "doi:",
]

/// A DOI in canonical form ("10.1000/xyz": lowercase, no prefix), or nil when `raw` is not a DOI.
public func normalizeDOI(_ raw: String) -> String? {
    var value = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    if let prefix = doiPrefixes.first(where: { value.hasPrefix($0) }) {
        value = String(value.dropFirst(prefix.count)).trimmingCharacters(in: .whitespacesAndNewlines)
    }
    return value.hasPrefix("10.") && value.contains("/") ? value : nil
}
