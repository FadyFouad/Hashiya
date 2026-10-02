@testable import FeaturePaperDetails
import Foundation
import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct PaperDetailsPdfTests {
    private let pendingWrites = PendingWrites()
    private let pdfs = FakePdfRepository()
    private let id = SamplePapers.attention.openAlexID
    private let link = URL(string: "https://arxiv.org/pdf/1706.03762")!
    private let stored = PaperPdf(source: .downloaded, sizeBytes: 2_400_000, addedAt: 1)

    private func started(_ library: FakeLibraryRepository) async -> (PaperDetailsViewModel, Task<Void, Never>) {
        let viewModel = PaperDetailsViewModel(
            openAlexID: id,
            library: library,
            pendingWrites: pendingWrites,
            collections: FakeCollectionsRepository(),
            citations: FakeCitationRepository(),
            pdfs: pdfs,
            copy: { _ in }
        )
        let task = Task { await viewModel.start() }
        _ = await eventually { viewModel.isLoaded }
        return (viewModel, task)
    }

    // MARK: The row's state and actions

    @Test func aRunningDownloadWinsThenAStoredFileThenAFailure() {
        let running = DownloadState.running(bytes: 10, total: 100)
        #expect(PdfRow(pdf: stored, download: running, link: link).state == .downloading(bytes: 10, total: 100))
        #expect(PdfRow(pdf: stored, download: .failed(.http), link: link).state == .stored(stored))
        #expect(PdfRow(pdf: nil, download: .failed(.notPDF), link: link).state == .failed(.notPDF))
        #expect(PdfRow(pdf: nil, download: nil, link: link).state == .available)
        #expect(PdfRow(pdf: nil, download: nil, link: nil).state == .none)
    }

    @Test func aMissingLinkFailureFallsBackToTheNoPdfState() {
        #expect(PdfRow(pdf: nil, download: .failed(.noLink), link: nil).state == .none)
        #expect(PdfRow(pdf: nil, download: .failed(.noLink), link: link).state == .available)
    }

    @Test func eachStateOffersAndroidsActions() {
        #expect(PdfRow(state: .available, link: link).primary == [.download])
        #expect(PdfRow(state: .available, link: link).overflow == [.attach])
        #expect(PdfRow(state: .none, link: nil).primary == [.attach])
        #expect(PdfRow(state: .none, link: nil).overflow.isEmpty)
        #expect(PdfRow(state: .downloading(bytes: 0, total: nil), link: link).primary == [.cancel])
        #expect(PdfRow(state: .stored(stored), link: link).primary == [.read])
        #expect(PdfRow(state: .stored(stored), link: link).overflow == [.replace, .remove, .openLink])
        #expect(PdfRow(state: .stored(stored), link: nil).overflow == [.replace, .remove])
        #expect(PdfRow(state: .failed(.offline), link: link).primary == [.tryAgain, .openInBrowser, .attach])
        #expect(PdfRow(state: .failed(.offline), link: nil).primary == [.attach])
        #expect(PdfRow(state: .failed(.offline), link: link).overflow.isEmpty)
    }

    // MARK: The view model

    @Test func theRowFollowsTheStoreAndTheDownload() async {
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]))
        defer { task.cancel() }
        #expect(viewModel.pdf == PdfRow(state: .available, link: link))

        pdfs.setDownload(id, .running(bytes: 5, total: 50))
        #expect(await eventually { viewModel.pdf.state == .downloading(bytes: 5, total: 50) })

        pdfs.setDownload(id, nil)
        pdfs.setPdf(id, stored)
        #expect(await eventually { viewModel.pdf.state == .stored(stored) })
    }

    /// A push cancels the screen's `.task`; on Back it runs `start()` again, and the row must follow the store again.
    @Test func startingAgainAfterACancelFollowsThePdfAgain() async {
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]))
        task.cancel()
        await task.value
        let again = Task { await viewModel.start() }
        defer { again.cancel() }

        pdfs.setDownload(id, .running(bytes: 5, total: 50))
        #expect(await eventually { viewModel.pdf.state == .downloading(bytes: 5, total: 50) })
        pdfs.setDownload(id, nil)
        pdfs.setPdf(id, stored)
        #expect(await eventually { viewModel.pdf.state == .stored(stored) })
    }

    @Test func aPaperWithoutALinkOffersAttach() async {
        let bert = SamplePapers.bert.openAlexID
        let viewModel = PaperDetailsViewModel(
            openAlexID: bert,
            library: FakeLibraryRepository(saved: [SamplePapers.bert]),
            pendingWrites: pendingWrites,
            collections: FakeCollectionsRepository(),
            citations: FakeCitationRepository(),
            pdfs: pdfs,
            copy: { _ in }
        )
        let task = Task { await viewModel.start() }
        defer { task.cancel() }
        _ = await eventually { viewModel.isLoaded }

        #expect(viewModel.pdf == PdfRow(state: .none, link: nil))
        #expect(viewModel.pdf.primary == [.attach])
    }

    @Test func downloadTryAgainAndCancelGoToTheRepository() async {
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]))
        defer { task.cancel() }

        #expect(viewModel.handle(.download) == nil)
        #expect(viewModel.handle(.tryAgain) == nil)
        #expect(viewModel.handle(.cancel) == nil)

        #expect(pdfs.downloads == [id, id])
        #expect(pdfs.cancels == [id])
    }

    @Test func theLinksOpenInTheBrowser() async {
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]))
        defer { task.cancel() }

        #expect(viewModel.handle(.openInBrowser) == link)
        #expect(viewModel.handle(.openLink) == link)
    }

    @Test func attachOpensThePickerAndReplaceAsksFirst() async {
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]))
        defer { task.cancel() }

        _ = viewModel.handle(.attach)
        #expect(viewModel.showingFileImporter)
        viewModel.showingFileImporter = false

        _ = viewModel.handle(.replace)
        #expect(viewModel.pdfConfirmation == .replace)
        #expect(!viewModel.showingFileImporter)
        viewModel.confirmReplace()
        #expect(viewModel.pdfConfirmation == nil)
        #expect(viewModel.showingFileImporter)
    }

    @Test func removeAsksFirstThenRemoves() async {
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]))
        defer { task.cancel() }

        _ = viewModel.handle(.remove)
        #expect(viewModel.pdfConfirmation == .remove)
        #expect(pdfs.removals.isEmpty)

        await viewModel.removePdf()
        #expect(viewModel.pdfConfirmation == nil)
        #expect(pdfs.removals == [id])
    }

    @Test func eachAttachResultSaysWhy() async {
        let (viewModel, task) = await started(FakeLibraryRepository(saved: [SamplePapers.attention]))
        defer { task.cancel() }
        let file = URL(fileURLWithPath: "/tmp/paper.pdf")

        pdfs.setAttachResult(.done)
        await viewModel.attachPdf(from: file)
        #expect(viewModel.message == nil)
        #expect(pdfs.attaches.map(\.0) == [id])
        #expect(pdfs.attaches.map(\.1) == [file])

        pdfs.setAttachResult(.notPDF)
        await viewModel.attachPdf(from: file)
        #expect(viewModel.message == .pdfAttachNotPdf)

        pdfs.setAttachResult(.tooLarge)
        await viewModel.attachPdf(from: file)
        #expect(viewModel.message == .pdfAttachTooLarge)

        pdfs.setAttachResult(.unreadable)
        await viewModel.attachPdf(from: file)
        #expect(viewModel.message == .pdfAttachFailed)
    }

    // MARK: Read, with the notes saved first

    @Test func readSavesTypedNotesBeforeOpeningTheReader() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        pdfs.setPdf(id, stored)
        viewModel.updateNote(.summary, "Typed just now")

        _ = viewModel.handle(.read)

        #expect(await eventually { viewModel.openReader })
        #expect(library.notes(of: id) == PaperNotes(summary: "Typed just now"))
    }

    @Test func readStaysWhenTheNotesCannotBeSaved() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        library.setFailSaveNotes(true)
        viewModel.updateNote(.summary, "Unsaved")

        await viewModel.readPdf()

        #expect(!viewModel.openReader)
        #expect(viewModel.message == .notesSaveFailed)
    }

    @Test func returningFromTheReaderShowsNotesWrittenThere() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention], notes: [id: PaperNotes(summary: "Before")])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }

        await viewModel.readPdf()
        viewModel.readerOpened()
        #expect(!viewModel.openReader)
        try await library.saveNotes(openAlexID: id, notes: PaperNotes(summary: "Written in the reader"))
        await viewModel.onReaderClosed()

        #expect(viewModel.notes == PaperNotes(summary: "Written in the reader"))
        #expect(viewModel.notesVersion == 1)
    }

    @Test func appearingWithoutHavingOpenedTheReaderReloadsNothing() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention], notes: [id: PaperNotes(summary: "Before")])
        let (viewModel, task) = await started(library)
        defer { task.cancel() }
        try await library.saveNotes(openAlexID: id, notes: PaperNotes(summary: "Elsewhere"))

        await viewModel.onReaderClosed()

        #expect(viewModel.notes == PaperNotes(summary: "Before"))
        #expect(viewModel.notesVersion == 0)
    }
}
