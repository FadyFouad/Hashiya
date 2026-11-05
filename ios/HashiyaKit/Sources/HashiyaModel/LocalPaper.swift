/// Papers imported from a file that OpenAlex couldn't match are stored with this prefix and a UUID in place of an
/// OpenAlex id, so every screen keyed by `openAlexID` works for them unchanged. Android's `LOCAL_PAPER_ID_PREFIX`.
public let localPaperIDPrefix = "local-"

/// Whether `openAlexID` belongs to a paper saved from an import file rather than from OpenAlex: there is nothing to ask
/// OpenAlex about it. Android's `isLocalPaperId`.
public func isLocalPaperID(_ openAlexID: String) -> Bool {
    openAlexID.hasPrefix(localPaperIDPrefix)
}
