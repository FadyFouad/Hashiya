@testable import FeaturePaperDetails
import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct PaperDetailsViewModelTests {
    private let sleeper = ManualSleeper()
    private let pendingWrites = PendingWrites()
    private let id = SamplePapers.attention.openAlexID

    private func makeViewModel(_ library: FakeLibraryRepository, id: String? = nil) -> PaperDetailsViewModel {
        PaperDetailsViewModel(openAlexID: id ?? self.id, library: library, pendingWrites: pendingWrites, sleep: sleeper.sleep)
    }

    /// A view model for `library` that has started and loaded; the returned task is its `start()`.
    private func started(_ library: FakeLibraryRepository) async -> (PaperDetailsViewModel, Task<Void, Never>) {
        let viewModel = makeViewModel(library)
        let task = Task { await viewModel.start() }
        _ = await eventually { viewModel.isLoaded }
        return (viewModel, task)
    }

    /// Lets the 500 ms pause elapse.
    private func pauseEnds() async {
        await sleeper.waitForSleeper()
        sleeper.advance(by: .milliseconds(500))
    }

    // MARK: Loading

    @Test func loadingWaitsForThePaperAndTheNotes() async {
        let library = FakeLibraryRepository(
            saved: [SamplePapers.attention], statuses: [id: .reading], notes: [id: PaperNotes(summary: "Stored")]
        )
        let viewModel = makeViewModel(library)
        #expect(!viewModel.isLoaded)
        #expect(viewModel.notesLoad == .loading)

        let task = Task { await viewModel.start() }
        defer { task.cancel() }

        #expect(await eventually { viewModel.isLoaded })
        #expect(viewModel.paper == LibraryPaper(paper: SamplePapers.attention, status: .reading))
        #expect(viewModel.notesLoad == .loaded)
        #expect(viewModel.notes == PaperNotes(summary: "Stored"))
        #expect(viewModel.saveState == .idle)
    }

    @Test func aLaterDatabaseChangeDoesNotReplaceTypedNotes() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention], notes: [id: PaperNotes(summary: "Stored")])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        viewModel.updateNote(.summary, "Typing")

        try await library.saveNotes(openAlexID: id, notes: PaperNotes(summary: "From elsewhere"))
        try await Task.sleep(for: .milliseconds(50))

        #expect(viewModel.notes == PaperNotes(summary: "Typing"))
    }

    @Test func aFailedNotesReadNeverWritesAndRetryLoadsThem() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention], notes: [id: PaperNotes(method: "Stored")])
        library.setFailNotesRead(true)
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        #expect(viewModel.notesLoad == .failed)
        viewModel.updateNote(.method, "Would overwrite")
        viewModel.flush()
        #expect(sleeper.pendingCount == 0)
        await pendingWrites.drained()
        #expect(library.notesWriteAttempts.isEmpty)
        #expect(viewModel.notes == PaperNotes())

        library.setFailNotesRead(false)
        await viewModel.retryLoadNotes()
        #expect(viewModel.notesLoad == .loaded)
        #expect(viewModel.notes == PaperNotes(method: "Stored"))
    }

    @Test func aPaperThatIsNotSavedCloses() async {
        let viewModel = makeViewModel(FakeLibraryRepository(), id: "W404")
        let task = Task { await viewModel.start() }
        defer { task.cancel() }

        #expect(await eventually { viewModel.exit == .closed })
    }

    @Test func aPaperRemovedElsewhereCloses() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        _ = try await library.remove(openAlexID: id)

        #expect(await eventually { viewModel.exit == .closed })
    }

    @Test func startingTwiceStartsOnce() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        await viewModel.start()

        #expect(viewModel.isLoaded)
    }

    // MARK: Autosave

    @Test func typingWritesOnceAfter500Milliseconds() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        viewModel.updateNote(.summary, "A")
        viewModel.updateNote(.summary, "Ab")
        viewModel.updateNote(.method, "Ablation")
        await sleeper.waitForSleeper()
        sleeper.advance(by: .milliseconds(499))
        try await Task.sleep(for: .milliseconds(30))
        #expect(library.notesWriteAttempts.isEmpty)

        sleeper.advance(by: .milliseconds(1))

        #expect(await eventually { viewModel.saveState == .saved })
        #expect(library.notesWriteAttempts == [PaperNotes(summary: "Ab", method: "Ablation")])
        #expect(library.notes(of: id) == PaperNotes(summary: "Ab", method: "Ablation"))
    }

    @Test func theSaveStateGoesIdleSavingSaved() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        library.holdNotesSaves()
        #expect(viewModel.saveState == .idle)

        viewModel.updateNote(.thoughts, "Idea")
        await pauseEnds()

        #expect(await eventually { viewModel.saveState == .saving })
        library.releaseNotesSaves()
        #expect(await eventually { viewModel.saveState == .saved })
    }

    @Test func aFailedWriteShowsFailedAndRetryWritesAgain() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        library.setFailSaveNotes(true)

        viewModel.updateNote(.keyFindings, "Result")
        await pauseEnds()

        #expect(await eventually { viewModel.saveState == .failed })
        #expect(viewModel.message == .notesSaveFailed)
        #expect(viewModel.notes == PaperNotes(keyFindings: "Result"))

        library.setFailSaveNotes(false)
        viewModel.flush()

        #expect(await eventually { viewModel.saveState == .saved })
        #expect(library.notesWriteAttempts == [PaperNotes(keyFindings: "Result"), PaperNotes(keyFindings: "Result")])
        #expect(library.notes(of: id) == PaperNotes(keyFindings: "Result"))
    }

    @Test func theNextEditTriesAgainAfterAFailure() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        library.setFailSaveNotes(true)
        viewModel.updateNote(.summary, "One")
        await pauseEnds()
        #expect(await eventually { viewModel.saveState == .failed })

        library.setFailSaveNotes(false)
        viewModel.updateNote(.summary, "One more")
        await pauseEnds()

        #expect(await eventually { viewModel.saveState == .saved })
        #expect(library.notes(of: id) == PaperNotes(summary: "One more"))
    }

    @Test func flushWritesPendingNotesAtOnce() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        viewModel.updateNote(.limitations, "Small sample")

        viewModel.flush()
        await pendingWrites.drained()

        #expect(library.notes(of: id) == PaperNotes(limitations: "Small sample"))
        #expect(sleeper.pendingCount == 0)
        #expect(library.notesWriteAttempts.count == 1)
    }

    @Test func flushWritesNothingWhenNothingIsNew() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention], notes: [id: PaperNotes(summary: "Stored")])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        viewModel.flush()
        viewModel.updateNote(.summary, "Changed")
        viewModel.updateNote(.summary, "Stored")
        viewModel.flush()
        await pendingWrites.drained()

        #expect(library.notesWriteAttempts.isEmpty)
    }

    @Test func writesRunInOrder() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        library.holdNotesSaves()

        viewModel.updateNote(.summary, "First")
        viewModel.flush()
        viewModel.updateNote(.summary, "Second")
        viewModel.flush()
        #expect(await eventually { library.heldNotesSaves == 1 })
        library.releaseNotesSaves()
        await pendingWrites.drained()

        #expect(library.notesWriteAttempts == [PaperNotes(summary: "First"), PaperNotes(summary: "Second")])
        #expect(library.notes(of: id) == PaperNotes(summary: "Second"))
    }

    /// Reopening Details right after leaving it reads the notes the previous screen was still writing.
    @Test func aReopenedScreenReadsTheNotesThePreviousOneWasWriting() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (first, firstTask) = await started(library)
        defer { firstTask.cancel() }
        library.holdNotesSaves()
        first.updateNote(.summary, "Just typed")
        first.flush()
        #expect(await eventually { library.heldNotesSaves == 1 })

        let second = makeViewModel(library)
        let secondTask = Task { await second.start() }
        defer { secondTask.cancel() }
        try? await Task.sleep(for: .milliseconds(50))
        library.releaseNotesSaves()

        #expect(await eventually { second.isLoaded })
        #expect(second.notes == PaperNotes(summary: "Just typed"))
    }

    @Test func aWriteOutlivesTheViewModel() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        var viewModel: PaperDetailsViewModel? = makeViewModel(library)
        let task = Task { [viewModel] in await viewModel?.start() }
        _ = await eventually { viewModel?.isLoaded == true }
        library.holdNotesSaves()

        viewModel?.updateNote(.thoughts, "Keep this")
        viewModel?.flush()
        task.cancel()
        _ = await task.value
        viewModel = nil
        library.releaseNotesSaves()
        await pendingWrites.drained()

        #expect(library.notes(of: id) == PaperNotes(thoughts: "Keep this"))
    }

    // MARK: Status and remove

    @Test func aStatusChangeIsStoredAndShown() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        await viewModel.setStatus(.read)

        #expect(await eventually { viewModel.paper?.status == .read })
    }

    @Test func aFailedStatusChangeShowsTheMessageAndKeepsTheStoredStatus() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        library.setFailStatusUpdates(true)
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        await viewModel.setStatus(.read)

        #expect(viewModel.message == .statusUpdateFailed)
        #expect(viewModel.paper?.status == .toRead)
    }

    @Test func removeSavesPendingNotesFirst() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        viewModel.updateNote(.thoughts, "Keep this")

        await viewModel.remove()

        #expect(viewModel.exit == .removed)
        #expect(library.notes(of: id) == PaperNotes(thoughts: "Keep this"))
        #expect(sleeper.pendingCount == 0)
    }

    @Test func removeWaitsWhenTheSaveFailsSoUndoCantRestoreStaleNotes() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        library.setFailSaveNotes(true)
        viewModel.updateNote(.thoughts, "Keep this")

        await viewModel.remove()

        #expect(viewModel.exit == nil)
        #expect(viewModel.saveState == .failed)
        #expect(viewModel.message == .notesSaveFailed)

        library.setFailSaveNotes(false)
        await viewModel.remove()

        #expect(viewModel.exit == .removed)
        #expect(library.notes(of: id) == PaperNotes(thoughts: "Keep this"))
    }

    @Test func removeWithoutLoadedNotesExitsWithoutWriting() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        library.setFailNotesRead(true)
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        await viewModel.remove()

        #expect(viewModel.exit == .removed)
        #expect(library.notesWriteAttempts.isEmpty)
    }
}
