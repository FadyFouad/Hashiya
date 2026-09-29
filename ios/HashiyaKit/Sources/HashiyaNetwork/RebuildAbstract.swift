/// Rebuilds an abstract from OpenAlex's inverted index: every (word, position) pair, ordered by
/// position and joined with single spaces. Nil when the index is missing or empty, or the text is blank.
public func rebuildAbstract(_ invertedIndex: [String: [Int]]?) -> String? {
    guard let invertedIndex, !invertedIndex.isEmpty else { return nil }
    let placed = invertedIndex.flatMap { word, positions in positions.map { (position: $0, word: word) } }
    let text = placed
        .sorted { ($0.position, $0.word) < ($1.position, $1.word) }
        .map(\.word)
        .joined(separator: " ")
    return text.allSatisfy(\.isWhitespace) ? nil : text
}
