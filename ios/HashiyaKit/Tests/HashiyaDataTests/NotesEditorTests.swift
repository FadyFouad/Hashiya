import Foundation
import HashiyaData
import HashiyaDiagnostics
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

    @Test func reloadWaitsForAnotherEditorsWrite() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention], notes: [id: PaperNotes(summary: "Before")])
        let details = await loaded(library)
        let reader = await loaded(library)
        library.holdNotesSaves()
        reader.onNoteChange(section: .summary, text: "From the reader")
        reader.flush()
        #expect(await eventually { library.heldNotesSaves == 1 })

        let reload = Task { await details.reload() }
        // The reader's write is still held, so the reload must not have read the older notes yet.
        try? await Task.sleep(for: .milliseconds(50))
        #expect(details.notes == PaperNotes(summary: "Before"))
        library.releaseNotesSaves()
        await reload.value

        #expect(details.notes == PaperNotes(summary: "From the reader"))
        #expect(details.version == 1)
    }

    @Test func anEditAfterAReloadSavesOnTopOfTheReloadedNotes() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention], notes: [id: PaperNotes(summary: "Before")])
        let editor = await loaded(library)
        try await library.saveNotes(openAlexID: id, notes: PaperNotes(summary: "From the reader"))
        await editor.reload()

        editor.onNoteChange(section: .method, text: "Typed after")
        await pauseEnds()

        #expect(await eventually { editor.saveState == .saved })
        #expect(library.notes(of: id) == PaperNotes(summary: "From the reader", method: "Typed after"))
    }

    @Test func aSaveThatEndsWhileTheReloadReadsIsKept() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention], notes: [id: PaperNotes(summary: "Before")])
        let editor = await loaded(library)
        library.holdNotesReads()
        let reload = Task { await editor.reload() }
        #expect(await eventually { library.heldNotesReads == 1 })

        // Typed and saved while the read's answer, "Before", is on its way.
        editor.onNoteChange(section: .summary, text: "Typed during the read")
        await pauseEnds()
        #expect(await eventually { editor.saveState == .saved })
        #expect(!editor.hasUnsavedChanges)
        library.releaseNotesReads()
        await reload.value

        #expect(editor.notes == PaperNotes(summary: "Typed during the read"))
        #expect(editor.version == 0)
        #expect(library.notes(of: id) == PaperNotes(summary: "Typed during the read"))
    }

    // MARK: Usage statistics

    /// The once-per-session set is shared by the whole process, so each test gets papers of its own.
    private func freshID() -> String { "W-notes-\(UUID().uuidString)" }

    private func countingEditor(_ id: String, _ library: FakeLibraryRepository, _ analytics: FakeAnalytics) async -> NotesEditor {
        let editor = NotesEditor(
            openAlexID: id, library: library, pendingWrites: pendingWrites, diagnostics: .fake(analytics: analytics), sleep: sleeper.sleep
        )
        await editor.load()
        return editor
    }

    @Test func twoSavesOfOnePapersNotesAreCountedOnce() async {
        let id = freshID()
        let analytics = FakeAnalytics()
        let editor = await countingEditor(id, FakeLibraryRepository(saved: [SamplePapers.attention]), analytics)

        editor.onNoteChange(section: .summary, text: "A")
        await pauseEnds()
        #expect(await eventually { editor.saveState == .saved })
        editor.onNoteChange(section: .summary, text: "AB")
        await pauseEnds()
        #expect(await eventually { !editor.hasUnsavedChanges && editor.notes.summary == "AB" })

        #expect(analytics.events == [.noteEdited])
    }

    @Test func aSecondPaperIsCountedToo() async {
        let analytics = FakeAnalytics()
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let first = await countingEditor(freshID(), library, analytics)
        let second = await countingEditor(freshID(), library, analytics)

        first.onNoteChange(section: .summary, text: "A")
        await pauseEnds()
        #expect(await eventually { first.saveState == .saved })
        second.onNoteChange(section: .method, text: "B")
        await pauseEnds()
        #expect(await eventually { second.saveState == .saved })

        #expect(analytics.events == [.noteEdited, .noteEdited])
    }

    @Test func aFailedSaveIsNotCountedAndTheNextSuccessIs() async {
        let analytics = FakeAnalytics()
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let editor = await countingEditor(freshID(), library, analytics)
        library.setFailSaveNotes(true)

        editor.onNoteChange(section: .summary, text: "A")
        await pauseEnds()
        #expect(await eventually { editor.saveState == .failed })
        #expect(analytics.events.isEmpty)

        library.setFailSaveNotes(false)
        editor.retry()
        #expect(await eventually { editor.saveState == .saved })
        #expect(analytics.events == [.noteEdited])
    }

    @Test func theNoteTextNeverReachesTheEvent() async {
        let analytics = FakeAnalytics()
        let editor = await countingEditor(freshID(), FakeLibraryRepository(saved: [SamplePapers.attention]), analytics)

        editor.onNoteChange(section: .summary, text: "Follows 10.1038/nature14539")
        await pauseEnds()
        #expect(await eventually { editor.saveState == .saved })

        #expect(analytics.events == [.noteEdited])
        #expect(!String(describing: analytics.events).contains("nature14539"))
    }
}
