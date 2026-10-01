/// Bibliographic details used for citations, as OpenAlex reports them. Every field is nil when the source has none.
/// The strings are kept as-is; HashiyaBibTeX interprets them, so a new OpenAlex type needs no migration.
public struct PublicationDetails: Equatable, Hashable, Sendable {
    /// OpenAlex's work type, such as "article", "preprint", "book-chapter".
    public var workType: String?
    /// OpenAlex's source type, such as "journal", "conference", "repository".
    public var sourceType: String?
    public var publisher: String?
    public var volume: String?
    public var issue: String?
    public var firstPage: String?
    public var lastPage: String?

    public init(
        workType: String? = nil,
        sourceType: String? = nil,
        publisher: String? = nil,
        volume: String? = nil,
        issue: String? = nil,
        firstPage: String? = nil,
        lastPage: String? = nil
    ) {
        self.workType = workType
        self.sourceType = sourceType
        self.publisher = publisher
        self.volume = volume
        self.issue = issue
        self.firstPage = firstPage
        self.lastPage = lastPage
    }
}
