@testable import FeatureReader
import Foundation
import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
@Suite(.serialized)
struct ReaderViewModelTests {
    private let library = FakeLibraryRepository(saved: [SamplePapers.attention])
    private let pdfs = FakePdfRepository()
    private let sleeper = ManualSleeper()
    private let notesSleeper = ManualSleeper()
    private let pendingWrites = PendingWrites()
    private let id = SamplePapers.attention.openAlexID

    private func viewModel() -> ReaderViewModel {
        ReaderViewModel(
            openAlexID: id,
            pdfs: pdfs,
            library: library,
            notes: NotesEditor(openAlexID: id, library: library, pendingWrites: pendingWrites, sleep: notesSleeper.sleep),
            sleep: sleeper.sleep
        )
    }

    /// A stored PDF of `pages` pages, last read on `lastPage`.
    private func storePdf(pages: Int = 5, lastPage: Int = 0) throws {
        pdfs.setFile(try TestPDF.make(pages: pages), for: id)
        pdfs.setPdf(id, PaperPdf(source: .downloaded, sizeBytes: 2_048, addedAt: 1, lastPage: lastPage))
    }

    /// Starts `viewModel` in the background, as the screen's `.task` does, and waits until it left `.loading` and read
    /// the notes (or closed).
    private func started(_ viewModel: ReaderViewModel) async -> Task<Void, Never> {
        let task = Task { await viewModel.start() }
        _ = await eventually {
            (viewModel.state != .loading && viewModel.notes.notesLoad != .loading) || viewModel.exit != nil
        }
        return task
    }

    private func pagesSaved() -> [Int] { pdfs.lastPages.filter { $0.0 == id }.map(\.1) }

    @Test func opensOnTheStoredLastPage() async throws {
        try storePdf(pages: 5, lastPage: 3)
        let viewModel = viewModel()
        let task = await started(viewModel)

        #expect(viewModel.state == .ready(pageCount: 5, startPage: 3))
        #expect(viewModel.currentPage == 3)
        #expect(viewModel.title == SamplePapers.attention.title)
        #expect(viewModel.document?.pageCount == 5)
        task.cancel()
    }

    @Test func aStoredPageBeyondTheEndOpensOnTheLastPage() async throws {
        try storePdf(pages: 2, lastPage: 9)
        let viewModel = viewModel()
        let task = await started(viewModel)

        #expect(viewModel.state == .ready(pageCount: 2, startPage: 1))
        task.cancel()
    }

    @Test func aFileThatCantBeOpenedShowsCantOpen() async throws {
        pdfs.setFile(try TestPDF.damaged(), for: id)
        pdfs.setPdf(id, PaperPdf(source: .attached, sizeBytes: 40, addedAt: 1))
        let viewModel = viewModel()
        let task = await started(viewModel)

        #expect(viewModel.state == .cantOpen)
        #expect(viewModel.document == nil)
        #expect(viewModel.exit == nil)
        task.cancel()
    }

    @Test func aPasswordProtectedFileShowsCantOpen() async throws {
        pdfs.setFile(try TestPDF.make(pages: 2, password: "secret"), for: id)
        pdfs.setPdf(id, PaperPdf(source: .attached, sizeBytes: 900, addedAt: 1))
        let viewModel = viewModel()
        let task = await started(viewModel)

        #expect(viewModel.state == .cantOpen)
        task.cancel()
    }

    @Test func thePageIsSavedOneSecondAfterItStopsChanging() async throws {
        try storePdf()
        let viewModel = viewModel()
        let task = await started(viewModel)

        viewModel.onPageChanged(2)
        await sleeper.waitForSleeper()
        #expect(pagesSaved().isEmpty)
        sleeper.advance(by: .seconds(1))

        #expect(await eventually { pagesSaved() == [2] })
        task.cancel()
    }

    @Test func aNewPageRestartsTheWait() async throws {
        try storePdf()
        let viewModel = viewModel()
        let task = await started(viewModel)

        viewModel.onPageChanged(1)
        await sleeper.waitForSleeper()
        sleeper.advance(by: .milliseconds(600))
        viewModel.onPageChanged(4)
        _ = await eventually { sleeper.pendingCount == 1 }
        sleeper.advance(by: .seconds(1))

        #expect(await eventually { pagesSaved() == [4] })
        task.cancel()
    }

    @Test func theStoredPageIsNotWrittenAgain() async throws {
        try storePdf(lastPage: 2)
        let viewModel = viewModel()
        let task = await started(viewModel)

        viewModel.onPageChanged(2)
        await sleeper.waitForSleeper()
        sleeper.advance(by: .seconds(1))
        viewModel.onDisappear()

        try await Task.sleep(for: .milliseconds(100))
        #expect(pagesSaved().isEmpty)
        task.cancel()
    }

    @Test func disappearingSavesTheCurrentPageAtOnce() async throws {
        try storePdf()
        let viewModel = viewModel()
        let task = await started(viewModel)

        viewModel.onPageChanged(3)
        viewModel.onDisappear()

        #expect(await eventually { pagesSaved() == [3] })
        task.cancel()
    }

    @Test func backSavesTypedNotesBeforeLeaving() async throws {
        try storePdf()
        let viewModel = viewModel()
        let task = await started(viewModel)

        viewModel.notes.onNoteChange(section: .summary, text: "Attention replaces recurrence.")
        await viewModel.back()

        #expect(library.notes(of: id).summary == "Attention replaces recurrence.")
        #expect(viewModel.exit == .back)
        task.cancel()
    }

    @Test func aFailedNotesSaveKeepsTheReaderOpen() async throws {
        try storePdf()
        let viewModel = viewModel()
        let task = await started(viewModel)
        library.setFailSaveNotes(true)

        viewModel.notes.onNoteChange(section: .method, text: "Self-attention only.")
        await viewModel.back()

        #expect(viewModel.exit == nil)
        #expect(viewModel.notes.saveState == .failed)
        task.cancel()
    }

    @Test func replacingAnUnreadableFileOpensTheNewOne() async throws {
        pdfs.setFile(try TestPDF.damaged(), for: id)
        pdfs.setPdf(id, PaperPdf(source: .attached, sizeBytes: 40, addedAt: 1))
        let viewModel = viewModel()
        let task = await started(viewModel)
        #expect(viewModel.state == .cantOpen)

        let replacement = try TestPDF.make(pages: 3)
        pdfs.setFile(replacement, for: id)
        pdfs.setAttachResult(.done)
        await viewModel.replace(with: replacement)

        #expect(viewModel.state == .ready(pageCount: 3, startPage: 0))
        #expect(pdfs.attaches.map(\.0) == [id])
        task.cancel()
    }

    @Test func replacingWithAFileThatIsntAPdfSaysSo() async throws {
        pdfs.setFile(try TestPDF.damaged(), for: id)
        pdfs.setPdf(id, PaperPdf(source: .attached, sizeBytes: 40, addedAt: 1))
        let viewModel = viewModel()
        let task = await started(viewModel)

        pdfs.setAttachResult(.notPDF)
        await viewModel.replace(with: try TestPDF.damaged())

        #expect(viewModel.message == .notPDF)
        #expect(viewModel.state == .cantOpen)
        task.cancel()
    }

    @Test func removingThePdfClosesTheReader() async throws {
        pdfs.setFile(try TestPDF.damaged(), for: id)
        pdfs.setPdf(id, PaperPdf(source: .attached, sizeBytes: 40, addedAt: 1))
        let viewModel = viewModel()
        let task = await started(viewModel)

        await viewModel.removePdf()

        #expect(pdfs.removals == [id])
        #expect(viewModel.exit == .closed)
        task.cancel()
    }

    @Test func removingThePdfSavesTypedNotesFirst() async throws {
        try storePdf()
        let viewModel = viewModel()
        let task = await started(viewModel)

        viewModel.notes.onNoteChange(section: .thoughts, text: "Read the appendix.")
        await viewModel.removePdf()

        #expect(library.notes(of: id).thoughts == "Read the appendix.")
        #expect(viewModel.exit == .closed)
        task.cancel()
    }

    @Test func aPdfRemovedElsewhereClosesTheReader() async throws {
        try storePdf()
        let viewModel = viewModel()
        let task = await started(viewModel)
        #expect(viewModel.exit == nil)

        pdfs.setPdf(id, nil)

        #expect(await eventually { viewModel.exit == .closed })
        task.cancel()
    }

    @Test func aPaperWithoutAPdfClosesAtOnce() async {
        let viewModel = viewModel()
        let task = await started(viewModel)

        #expect(viewModel.exit == .closed)
        task.cancel()
    }

    @Test func closingTheNotesSheetSavesTypedNotesAtOnce() async throws {
        try storePdf()
        let viewModel = viewModel()
        let task = await started(viewModel)
        viewModel.showNotes()
        #expect(viewModel.showingNotes)

        viewModel.notes.onNoteChange(section: .keyFindings, text: "BLEU 28.4 on WMT 2014.")
        viewModel.notesClosed()

        // Tracked before this turn ends, so Details' reload, which drains these writes first, reads it.
        #expect(!pendingWrites.isIdle)
        #expect(!viewModel.showingNotes)
        await pendingWrites.drained()
        #expect(library.notes(of: id).keyFindings == "BLEU 28.4 on WMT 2014.")
        #expect(notesSleeper.pendingCount == 0)
        task.cancel()
    }

    @Test func aPdfRemovedElsewhereSavesTypedNotesAsItCloses() async throws {
        try storePdf()
        let viewModel = viewModel()
        let task = await started(viewModel)

        viewModel.notes.onNoteChange(section: .limitations, text: "Quadratic in length.")
        pdfs.setPdf(id, nil)

        #expect(await eventually { viewModel.exit == .closed })
        await pendingWrites.drained()
        #expect(library.notes(of: id).limitations == "Quadratic in length.")
        task.cancel()
    }

    @Test func openingTheNotesAfterAFailedReadReadsThemAgain() async throws {
        try storePdf()
        library.setFailNotesRead(true)
        let viewModel = viewModel()
        let task = await started(viewModel)
        #expect(viewModel.notes.notesLoad == .failed)

        library.setFailNotesRead(false)
        viewModel.showNotes()

        #expect(await eventually { viewModel.notes.notesLoad == .loaded })
        task.cancel()
    }
}
