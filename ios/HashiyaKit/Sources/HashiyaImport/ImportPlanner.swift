import Foundation
import HashiyaModel

/// A title reduced for comparison, with the year: the identity of an entry without a DOI.
public struct TitleKey: Hashable, Sendable {
    public var title: String
    public var year: Int?

    public init(title: String, year: Int?) {
        self.title = title
        self.year = year
    }
}

/// `title` through `searchableText` (no case, accents, tashkeel or letter variants), with every run of characters that
/// are not letters or digits as one space. Nil when nothing is left.
public func titleKey(_ title: String, year: Int?) -> TitleKey? {
    var words = String.UnicodeScalarView()
    var pendingSpace = false
    for scalar in searchableText(title).unicodeScalars {
        if scalar.properties.isAlphabetic || scalar.properties.numericType == .decimal {
            if pendingSpace, !words.isEmpty { words.append(" ") }
            pendingSpace = false
            words.append(scalar)
        } else {
            pendingSpace = true
        }
    }
    return words.isEmpty ? nil : TitleKey(title: String(words), year: year)
}

/// What is already saved. `importedDOIs` and `importedTitleKeys` are the papers this import saved before it was interrupted.
public struct ExistingLibrary: Sendable {
    public var dois: Set<String>
    public var titleKeys: Set<TitleKey>
    public var importedDOIs: Set<String>
    public var importedTitleKeys: Set<TitleKey>

    public init(
        dois: Set<String> = [],
        titleKeys: Set<TitleKey> = [],
        importedDOIs: Set<String> = [],
        importedTitleKeys: Set<TitleKey> = []
    ) {
        self.dois = dois
        self.titleKeys = titleKeys
        self.importedDOIs = importedDOIs
        self.importedTitleKeys = importedTitleKeys
    }
}

public enum PlannedEntry: Equatable, Sendable {
    /// To save: looked up on OpenAlex when it has a DOI, else built from the file.
    case toImport(ImportedEntry)
    /// Already saved before this import: skipped.
    case inLibrary(ImportedEntry)
    /// Saved by this import before it was interrupted: counts as imported.
    case alreadyImported(ImportedEntry)
    /// The same paper as an earlier entry in the file: skipped.
    case repeated(ImportedEntry)

    public var entry: ImportedEntry {
        switch self {
        case let .toImport(entry), let .inLibrary(entry), let .alreadyImported(entry), let .repeated(entry): entry
        }
    }
}

private enum Identity: Hashable {
    case doi(String)
    case title(TitleKey)
}

/// Android's `ImportPlanner` (spec §5).
public enum ImportPlanner {
    /// One plan per entry, in file order.
    public static func plan(_ entries: [ImportedEntry], existing: ExistingLibrary) -> [PlannedEntry] {
        var seen = Set<Identity>()
        return entries.map { entry in
            let identity: Identity? = entry.doi.map(Identity.doi) ?? titleKey(entry.title, year: entry.year).map(Identity.title)
            guard let identity else { return .toImport(entry) }
            if !seen.insert(identity).inserted { return .repeated(entry) }
            if identity.isIn(dois: existing.importedDOIs, titleKeys: existing.importedTitleKeys) { return .alreadyImported(entry) }
            if identity.isIn(dois: existing.dois, titleKeys: existing.titleKeys) { return .inLibrary(entry) }
            return .toImport(entry)
        }
    }
}

private extension Identity {
    func isIn(dois: Set<String>, titleKeys: Set<TitleKey>) -> Bool {
        switch self {
        case let .doi(doi): dois.contains(doi)
        case let .title(key): titleKeys.contains(key)
        }
    }
}
