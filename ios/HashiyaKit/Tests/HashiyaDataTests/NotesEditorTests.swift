import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct NotesEditorTests {
    private let sleeper = ManualSleeper()
    private let pendingWrites = PendingWrites()
    private let id = SamplePapers.attention.openAlexID

    private func loaded(_ library: FakeLibraryRepository) async -> NotesEditor {
        let editor = NotesEditor(openAlexID: id, library: library, pendingWrites: pendingWrites, sleep: sleeper.sleep)
        await editor.load()
        return editor
    }

    /// Lets the 500 ms pause elapse.
    private func pauseEnds() async {
        await sleeper.waitForSleeper()
        sleeper.advance(by: .milliseconds(500))
    }

    @Test func nothingIsReadUntilLoad() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention], notes: [id: PaperNotes(summary: "Stored")])
        let editor = NotesEditor(openAlexID: id, library: library, pendingWrites: pendingWrites, sleep: sleeper.sleep)
        #expect(editor.notesLoad == .loading)

        await editor.load()

        #expect(editor.notesLoad == .loaded)
        #expect(editor.notes == PaperNotes(summary: "Stored"))
        #expect(editor.saveState == .idle)
        #expect(!editor.hasUnsavedChanges)
    }

    @Test func typingWritesOnceAfterThePause() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let editor = await loaded(library)

        editor.onNoteChange(section: .summary, text: "A")
        editor.onNoteChange(section: .summary, text: "Ab")
        #expect(editor.hasUnsavedChanges)
        await pauseEnds()

        #expect(await eventually { editor.saveState == .saved })
        #expect(library.notesWriteAttempts == [PaperNotes(summary: "Ab")])
        #expect(!editor.hasUnsavedChanges)
    }

    @Test func saveNowWritesAtOnceAndReportsAFailure() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let editor = await loaded(library)
        var failures = 0
        editor.onSaveFailed = { failures += 1 }
        library.setFailSaveNotes(true)

        editor.onNoteChange(section: .method, text: "Ablation")
        let saved = await editor.saveNow()

        #expect(!saved)
        #expect(failures == 1)
        #expect(editor.saveState == .failed)
        #expect(sleeper.pendingCount == 0)

        library.setFailSaveNotes(false)
        #expect(await editor.saveNow())
        #expect(library.notes(of: id) == PaperNotes(method: "Ablation"))
    }

    @Test func saveNowBeforeLoadingWritesNothing() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let editor = NotesEditor(openAlexID: id, library: library, pendingWrites: pendingWrites, sleep: sleeper.sleep)

        #expect(await editor.saveNow())
        #expect(library.notesWriteAttempts.isEmpty)
    }

    @Test func reloadTakesNotesWrittenElsewhereAndBumpsTheVersion() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention], notes: [id: PaperNotes(summary: "Before")])
        let editor = await loaded(library)
        try await library.saveNotes(openAlexID: id, notes: PaperNotes(summary: "From the reader"))

        await editor.reload()

        #expect(editor.notes == PaperNotes(summary: "From the reader"))
        #expect(editor.version == 1)
        #expect(!editor.hasUnsavedChanges)
    }

    @Test func reloadOfUnchangedNotesKeepsTheVersion() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention], notes: [id: PaperNotes(summary: "Same")])
        let editor = await loaded(library)

        await editor.reload()

        #expect(editor.version == 0)
        #expect(editor.notes == PaperNotes(summary: "Same"))
    }

    @Test func reloadNeverReplacesUnsavedTyping() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let editor = await loaded(library)
        editor.onNoteChange(section: .thoughts, text: "Typing")
        try await library.saveNotes(openAlexID: id, notes: PaperNotes(thoughts: "Elsewhere"))

        await editor.reload()

        #expect(editor.notes == PaperNotes(thoughts: "Typing"))
        #expect(editor.version == 0)
    }
}
