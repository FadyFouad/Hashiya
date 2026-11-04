/// An import of a .bib or .ris file. Android's `ImportJob`.
public enum ImportState: String, Equatable, Sendable {
    /// Reading the file and matching DOIs with OpenAlex; nothing is saved yet.
    case analyzing
    /// The preview: waiting for the user to confirm.
    case ready
    case importing
    case done
    case failed
    case cancelled
}

public enum ImportFailure: String, Equatable, Sendable {
    /// Neither BibTeX nor RIS.
    case unsupported
    /// Over the 10 MB limit.
    case tooLarge = "too_large"
    /// The file couldn't be read, or saving failed.
    case unreadable
    /// Every entry is already in the library or repeated in the file.
    case nothingToImport = "nothing_to_import"
}

/// What an import found and will do. `toImport` papers are saved: `matched` from OpenAlex and `local` from the file.
public struct ImportCounts: Equatable, Sendable {
    /// Entries read from the file.
    public var found: Int
    public var matched: Int
    /// Saved from the file's own fields: no DOI, a DOI OpenAlex doesn't know, or one that couldn't be checked.
    public var local: Int
    public var inLibrary: Int
    /// The same paper again later in the file.
    public var repeated: Int
    /// Entries that couldn't be read (malformed, or with nothing to import).
    public var unreadable: Int
    /// Part of `local`: DOIs that couldn't be looked up (offline, or a lookup that failed).
    public var notChecked: Int

    public var toImport: Int { matched + local }

    public init(found: Int = 0, matched: Int = 0, local: Int = 0, inLibrary: Int = 0, repeated: Int = 0, unreadable: Int = 0, notChecked: Int = 0) {
        self.found = found
        self.matched = matched
        self.local = local
        self.inLibrary = inLibrary
        self.repeated = repeated
        self.unreadable = unreadable
        self.notChecked = notChecked
    }
}

public struct ImportJob: Equatable, Sendable, Identifiable {
    public var id: Int64
    public var fileName: String
    public var state: ImportState
    public var counts: ImportCounts
    /// Entries handled so far in the import phase, out of `counts.toImport`.
    public var processed: Int
    /// Papers saved by this job.
    public var imported: Int
    public var addToCollection: Bool
    /// The new collection's name: the file name without its extension, made unique when the import starts.
    public var collectionName: String
    /// The collection the papers went into, once created.
    public var collectionID: Int64?
    public var failure: ImportFailure?

    public var isRunning: Bool { state == .analyzing || state == .importing }

    public init(
        id: Int64,
        fileName: String,
        state: ImportState,
        counts: ImportCounts = ImportCounts(),
        processed: Int = 0,
        imported: Int = 0,
        addToCollection: Bool = true,
        collectionName: String = "",
        collectionID: Int64? = nil,
        failure: ImportFailure? = nil
    ) {
        self.id = id
        self.fileName = fileName
        self.state = state
        self.counts = counts
        self.processed = processed
        self.imported = imported
        self.addToCollection = addToCollection
        self.collectionName = collectionName
        self.collectionID = collectionID
        self.failure = failure
    }
}

/// The new collection's name for an import of `fileName`: the name without its extension, trimmed to a collection name's
/// length, or "Import" when nothing is left. Android's `collectionNameFor`.
public func importCollectionName(fileName: String) -> String {
    let base: Substring
    if let dot = fileName.lastIndex(of: "."), dot > fileName.startIndex {
        base = fileName[..<dot]
    } else {
        base = Substring(fileName)
    }
    let trimmed = String(trimmedCollectionName(String(base)).prefix(collectionNameMaxLength))
    let name = trimmedCollectionName(trimmed)
    return name.isEmpty ? "Import" : name
}
