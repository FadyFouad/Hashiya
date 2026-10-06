/// A person's family name and initials; `initials` is nil when the name is kept whole.
struct PersonName: Equatable {
    let family: String
    let initials: String?
}

/// "Aidan N. Gomez" → Gomez, A. N. One-word names (organisations) and Arabic-script names are kept whole.
func personName(_ name: String) -> PersonName {
    let parts = name.split(whereSeparator: \.isWhitespace).map(String.init)
    let trimmed = parts.joined(separator: " ")
    let arabic = trimmed.unicodeScalars.contains { (0x0600...0x06FF).contains($0.value) }
    guard parts.count >= 2, !arabic else { return PersonName(family: trimmed, initials: nil) }
    let initials = parts.dropLast().map { given in
        given.split(separator: "-").map { "\($0.first!.uppercased())." }.joined(separator: "-")
    }.joined(separator: " ")
    return PersonName(family: parts.last!, initials: initials)
}
