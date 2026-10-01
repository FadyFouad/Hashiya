import Foundation

/// A user-made group of saved papers. `paperCount` is how many saved papers are in it.
public struct PaperCollection: Equatable, Hashable, Identifiable, Sendable {
    public let id: Int64
    public let name: String
    public let paperCount: Int

    public init(id: Int64, name: String, paperCount: Int) {
        self.id = id
        self.name = name
        self.paperCount = paperCount
    }
}

public let collectionNameMaxLength = 60

/// The name as stored: surrounding whitespace and newlines removed.
public func trimmedCollectionName(_ name: String) -> String {
    name.trimmingCharacters(in: .whitespacesAndNewlines)
}

/// A name is valid when, trimmed, it has 1 to `collectionNameMaxLength` characters. Swift counts `Character`s, so an emoji
/// counts once; Android counts UTF-16 units, so a name valid there is always valid here.
public func isValidCollectionName(_ name: String) -> Bool {
    (1...collectionNameMaxLength).contains(trimmedCollectionName(name).count)
}

/// Two collections may not share this key: the name trimmed and lowercased (locale-independent, like Kotlin's Locale.ROOT).
public func collectionNameKey(_ name: String) -> String {
    trimmedCollectionName(name).lowercased()
}
