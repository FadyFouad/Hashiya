/// Whether a notes editor has read the stored notes.
public enum NotesLoad: Equatable, Sendable {
    case loading, loaded, failed
}

/// The line beside "My notes".
public enum NotesSaveState: Equatable, Sendable {
    /// No write yet on this screen.
    case idle
    case saving, saved, failed
}
