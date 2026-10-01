@testable import FeatureLibrary
import Foundation
import HashiyaData
import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import Testing

/// Android's `LibraryCollectionsViewModelTest`, case by case, plus the iOS share sheet's busy rule.
@MainActor
struct LibraryCollectionsViewModelTests {
    private static let bib = "@misc{paper2020,\n}\n"

    private let sleeper = ManualSleeper()
    /// Newest first: ViT, BERT, Attention.
    private let library = FakeLibraryRepository(saved: [SamplePapers.vit, SamplePapers.bert, SamplePapers.attention])
    private let collections: FakeCollectionsRepository
    private let share = ShareRecorder()
    private let exportDirectory = FileManager.default.temporaryDirectory.appendingPathComponent("library-export-\(UUID().uuidString)")

    init() {
        collections = FakeCollectionsRepository(library: library)
    }

    private func makeViewModel(
        citations: FakeCitationRepository = FakeCitationRepository(export: CitationResult(bibtex: bib, complete: true)),
        exportFiles: ExportFiles? = nil
    ) -> LibraryViewModel {
        LibraryViewModel(
            library: library,
            collections: collections,
            citations: citations,
            exportFiles: exportFiles ?? ExportFiles(directory: exportDirectory),
            share: { [share] url in await share.share(url) },
            sleep: sleeper.sleep
        )
    }

    /// "Thesis" with BERT and ViT.
    private func thesis() async throws -> PaperCollection {
        guard case let .done(id) = try await collections.create(name: "Thesis") else {
            Issue.record("Thesis wasn't created")
            return PaperCollection(id: 0, name: "", paperCount: 0)
        }
        try await collections.setMembership(collectionID: id, openAlexID: SamplePapers.bert.openAlexID, member: true)
        try await collections.setMembership(collectionID: id, openAlexID: SamplePapers.vit.openAlexID, member: true)
        return PaperCollection(id: id, name: "Thesis", paperCount: 2)
    }

    private func ids(_ viewModel: LibraryViewModel) -> [String] {
        viewModel.papers.map(\.paper.openAlexID)
    }

    // MARK: Selecting

    @Test func selectingACollectionFiltersTheListAndCounts() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        #expect(await eventually { viewModel.viewTotal == 3 && viewModel.collections == [thesis] })

        viewModel.selectCollection(thesis.id)

        #expect(await eventually { ids(viewModel) == [SamplePapers.vit.openAlexID, SamplePapers.bert.openAlexID] })
        #expect(viewModel.filter?.total == 2)
        #expect(viewModel.selectedCollection == thesis)
        #expect(viewModel.viewTotal == 2)
        #expect(viewModel.allPapersTotal == 3)
        #expect(viewModel.storedCollection == Int(thesis.id))
    }

    @Test func searchAndChipApplyInsideTheCollection() async throws {
        let thesis = try await thesis()
        try await library.setStatus(openAlexID: SamplePapers.bert.openAlexID, status: .read)
        let viewModel = makeViewModel()

        viewModel.selectCollection(thesis.id)
        viewModel.setStatusFilter(.read)

        #expect(await eventually { ids(viewModel) == [SamplePapers.bert.openAlexID] })
        #expect(viewModel.viewTotal == 2)
    }

    @Test func anEmptyCollectionHasItsOwnState() async throws {
        guard case let .done(id) = try await collections.create(name: "Empty") else { return }
        let viewModel = makeViewModel()
        #expect(await eventually { viewModel.collections.count == 1 })

        viewModel.selectCollection(id)

        #expect(await eventually { viewModel.state == .emptyCollection })
        #expect(viewModel.viewTotal == 0)
        #expect(!viewModel.canExport)
        #expect(viewModel.allPapersTotal == 3)
    }

    @Test func theSceneRestoredCollectionIsShown() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()

        viewModel.restore(text: "", status: "", collectionID: LibraryViewModel.collectionID(stored: Int(thesis.id)))

        #expect(viewModel.collectionID == thesis.id)
        #expect(await eventually { ids(viewModel) == [SamplePapers.vit.openAlexID, SamplePapers.bert.openAlexID] })
        #expect(LibraryViewModel.collectionID(stored: -1) == nil)
    }

    @Test func aRestoredCollectionThatIsGoneFallsBackToAllPapers() async {
        let viewModel = makeViewModel()

        viewModel.restore(text: "", status: "", collectionID: 99)

        #expect(await eventually { viewModel.collectionID == nil && viewModel.papers.count == 3 })
        #expect(viewModel.storedCollection == -1)
    }

    @Test func aDeletedCollectionFallsBackToAllPapers() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.papers.count == 2 })

        // Deleted elsewhere (from Details).
        try await collections.delete(id: thesis.id)

        #expect(await eventually { viewModel.collectionID == nil && viewModel.papers.count == 3 })
        #expect(viewModel.selectedCollection == nil)
        #expect(viewModel.storedCollection == -1)
    }

    @Test func selectingACollectionDeletedMeanwhileFallsBackToAllPapers() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        #expect(await eventually { viewModel.collections.count == 1 })
        try await collections.delete(id: thesis.id)
        #expect(await eventually { viewModel.collections.isEmpty })

        viewModel.selectCollection(thesis.id)

        #expect(viewModel.collectionID == nil)
        #expect(await eventually { viewModel.papers.count == 3 })
    }

    // MARK: Swipe in a collection

    @Test func swipeInACollectionRemovesOnlyTheMembershipWithUndo() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.papers.count == 2 && viewModel.selectedCollection != nil })

        await viewModel.removeFromCollection(openAlexID: SamplePapers.bert.openAlexID)

        #expect(viewModel.pendingCollectionUndo == CollectionUndo(collectionID: thesis.id, collectionName: "Thesis", openAlexID: SamplePapers.bert.openAlexID))
        #expect(viewModel.pendingUndo == nil)
        #expect(await eventually { ids(viewModel) == [SamplePapers.vit.openAlexID] })
        #expect(library.savedPapers.count == 3)
        viewModel.selectCollection(nil)
        #expect(await eventually { viewModel.papers.count == 3 })

        await viewModel.undoCollectionRemoval()

        #expect(viewModel.pendingCollectionUndo == nil)
        #expect(collections.collectionIDs(of: SamplePapers.bert.openAlexID) == [thesis.id])
    }

    /// A collection deleted a moment ago: the swipe must never remove the paper from the library.
    @Test func swipeInADeletedCollectionDoesNothing() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.papers.count == 2 && viewModel.selectedCollection != nil })

        try await collections.delete(id: thesis.id)
        await viewModel.removeFromCollection(openAlexID: SamplePapers.bert.openAlexID)

        #expect(viewModel.pendingUndo == nil)
        #expect(library.savedPapers.contains(SamplePapers.bert))
        #expect(await eventually { viewModel.collectionID == nil && viewModel.papers.count == 3 })

        // After the fallback, a late swipe from a row drawn in the collection still does nothing.
        await viewModel.removeFromCollection(openAlexID: SamplePapers.vit.openAlexID)
        #expect(library.savedPapers.contains(SamplePapers.vit))
        #expect(viewModel.pendingUndo == nil)
    }

    @Test func undoIntoADeletedCollectionIsDropped() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.papers.count == 2 && viewModel.selectedCollection != nil })
        await viewModel.removeFromCollection(openAlexID: SamplePapers.bert.openAlexID)
        #expect(viewModel.pendingCollectionUndo != nil)

        try await collections.delete(id: thesis.id)
        #expect(await eventually { viewModel.collections.isEmpty })
        await viewModel.undoCollectionRemoval()

        #expect(viewModel.pendingCollectionUndo == nil)
        #expect(viewModel.message == nil)
        #expect(collections.collectionIDs(of: SamplePapers.bert.openAlexID).isEmpty)
        #expect(await eventually { viewModel.papers.count == 3 })
    }

    @Test func aCollectionSwipeInAllPapersDoesNothing() async throws {
        _ = try await thesis()
        let viewModel = makeViewModel()
        #expect(await eventually { viewModel.papers.count == 3 })

        await viewModel.removeFromCollection(openAlexID: SamplePapers.bert.openAlexID)

        #expect(viewModel.pendingCollectionUndo == nil)
        #expect(library.savedPapers.count == 3)
    }

    @Test func anExpiredCollectionUndoIsForgotten() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.selectedCollection != nil })
        await viewModel.removeFromCollection(openAlexID: SamplePapers.bert.openAlexID)

        viewModel.collectionUndoExpired()
        await viewModel.undoCollectionRemoval()

        #expect(viewModel.pendingCollectionUndo == nil)
        #expect(collections.collectionIDs(of: SamplePapers.bert.openAlexID).isEmpty)
    }

    // MARK: New, rename and delete

    @Test func newCollectionShowsTheClashThenCreatesAndShowsIt() async throws {
        _ = try await thesis()
        let viewModel = makeViewModel()
        #expect(await eventually { viewModel.collections.count == 1 })

        viewModel.showNewCollection()
        #expect(viewModel.nameSheet == NameSheet(mode: .create, initialName: "", error: nil, collectionID: nil))

        await viewModel.submitName(" thesis ")
        #expect(viewModel.nameSheet?.error == DesignSystemStrings.collectionNameTaken)

        await viewModel.submitName("Chapter 2")
        #expect(viewModel.nameSheet == nil)
        #expect(await eventually { viewModel.collections.map(\.name) == ["Chapter 2", "Thesis"] })
        let chapter = try #require(viewModel.collections.first { $0.name == "Chapter 2" })
        #expect(viewModel.collectionID == chapter.id)
        #expect(await eventually { viewModel.state == .emptyCollection })
    }

    @Test func renameAndDelete() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.selectedCollection != nil })

        viewModel.showRename()
        #expect(viewModel.nameSheet == NameSheet(mode: .rename, initialName: "Thesis", error: nil, collectionID: thesis.id))
        await viewModel.submitName("Dissertation")
        #expect(viewModel.nameSheet == nil)
        #expect(await eventually { viewModel.collections.map(\.name) == ["Dissertation"] })
        #expect(viewModel.collectionID == thesis.id)

        viewModel.requestDelete()
        let pending = try #require(viewModel.pendingDelete)
        #expect(pending.name == "Dissertation")
        await viewModel.confirmDelete(pending)

        #expect(viewModel.pendingDelete == nil)
        #expect(viewModel.collectionID == nil)
        #expect(await eventually { viewModel.collections.isEmpty && viewModel.papers.count == 3 })
    }

    @Test func renameShowsTheClashForTheSameNameInAnotherCaseOrSpacing() async throws {
        let thesis = try await thesis()
        guard case let .done(chapterID) = try await collections.create(name: "Chapter 2") else { return }
        let viewModel = makeViewModel()
        viewModel.selectCollection(chapterID)
        #expect(await eventually { viewModel.selectedCollection?.name == "Chapter 2" })

        viewModel.showRename()
        await viewModel.submitName(" thesis ")
        #expect(viewModel.nameSheet?.error == DesignSystemStrings.collectionNameTaken)
        viewModel.dismissNameSheet()
        #expect(viewModel.nameSheet == nil)
        #expect(viewModel.collections.map(\.name) == ["Chapter 2", "Thesis"])

        // A new case of its own name is not a clash.
        viewModel.selectCollection(thesis.id)
        viewModel.showRename()
        await viewModel.submitName("THESIS")
        #expect(viewModel.nameSheet == nil)
        #expect(await eventually { viewModel.collections.map(\.name) == ["Chapter 2", "THESIS"] })
    }

    @Test func theSameClashTwiceShowsTheErrorAgain() async throws {
        _ = try await thesis()
        let viewModel = makeViewModel()
        viewModel.showNewCollection()

        await viewModel.submitName("Thesis")
        #expect(viewModel.nameSheet?.error != nil)
        await viewModel.submitName("Thesis")
        #expect(viewModel.nameSheet?.error == DesignSystemStrings.collectionNameTaken)
    }

    @Test func renamingACollectionDeletedMeanwhileClosesTheSheetWithAMessage() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.selectedCollection != nil })
        viewModel.showRename()
        try await collections.delete(id: thesis.id)

        await viewModel.submitName("Dissertation")

        #expect(viewModel.nameSheet == nil)
        #expect(viewModel.message == .collectionsUpdateFailed)
        #expect(await eventually { viewModel.collections.isEmpty && viewModel.collectionID == nil })
    }

    @Test func aFailedCollectionChangeClosesTheSheetWithAMessage() async throws {
        _ = try await thesis()
        let viewModel = makeViewModel()
        collections.setFailWrites(true)

        viewModel.showNewCollection()
        await viewModel.submitName("Chapter 2")

        #expect(viewModel.nameSheet == nil)
        #expect(viewModel.message == .collectionsUpdateFailed)
    }

    @Test func aDoubleCreateRunsOnce() async throws {
        let viewModel = makeViewModel()
        collections.holdCreates()
        viewModel.showNewCollection()

        let first = Task { await viewModel.submitName("Chapter 2") }
        #expect(await eventually { collections.heldCreates == 1 })
        await viewModel.submitName("Chapter 2")
        collections.releaseCreates()
        await first.value

        #expect(collections.createdNames == ["Chapter 2"])
        #expect(viewModel.nameSheet == nil)
    }

    @Test func aFailedDeleteShowsTheMessage() async throws {
        let thesis = try await thesis()
        let viewModel = makeViewModel()
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.selectedCollection != nil })
        collections.setFailWrites(true)

        viewModel.requestDelete()
        await viewModel.confirmDelete(try #require(viewModel.pendingDelete))

        #expect(viewModel.message == .collectionsUpdateFailed)
        #expect(viewModel.collectionID == thesis.id)
    }

    // MARK: Export

    @Test func exportRunsForTheSelectedCollectionAndSharesItsFile() async throws {
        let thesis = try await thesis()
        let citations = FakeCitationRepository(export: CitationResult(bibtex: Self.bib, complete: true))
        let viewModel = makeViewModel(citations: citations)
        viewModel.selectCollection(thesis.id)
        #expect(await eventually { viewModel.selectedCollection != nil && viewModel.canExport })

        await viewModel.export()

        #expect(citations.exportCalls == [thesis.id])
        let shared = try #require(share.urls.first)
        #expect(shared.lastPathComponent == "Thesis.bib")
        #expect(try String(contentsOf: shared, encoding: .utf8) == Self.bib)
        #expect(!viewModel.exporting)
        #expect(viewModel.message == nil)
    }

    @Test func allPapersExportsTheWholeLibraryAsHashiyaLibraryBib() async throws {
        let citations = FakeCitationRepository(export: CitationResult(bibtex: Self.bib, complete: true))
        let viewModel = makeViewModel(citations: citations)
        #expect(await eventually { viewModel.papers.count == 3 })
        viewModel.updateText("nothing like this")
        viewModel.submitNow()
        #expect(await eventually { if case .noMatches = viewModel.state { true } else { false } })
        // The search and chip don't matter: the export covers the whole view.
        #expect(viewModel.canExport)

        await viewModel.export()

        #expect(citations.exportCalls == [nil])
        #expect(share.urls.map(\.lastPathComponent) == ["hashiya-library.bib"])
    }

    @Test func aSecondExportTapWhileRunningDoesNothing() async {
        let citations = FakeCitationRepository(export: CitationResult(bibtex: Self.bib, complete: true))
        citations.holdExports()
        let viewModel = makeViewModel(citations: citations)
        #expect(await eventually { viewModel.papers.count == 3 })

        let first = Task { await viewModel.export() }
        #expect(await eventually { citations.exportCalls.count == 1 })
        #expect(viewModel.exporting)
        await viewModel.export()
        citations.releaseExports()
        await first.value

        #expect(citations.exportCalls == [nil])
        #expect(share.urls.count == 1)
    }

    @Test func exportStaysBusyUntilTheShareSheetCloses() async {
        let citations = FakeCitationRepository(export: CitationResult(bibtex: Self.bib, complete: true))
        let viewModel = makeViewModel(citations: citations)
        #expect(await eventually { viewModel.papers.count == 3 })
        share.hold()

        let running = Task { await viewModel.export() }
        #expect(await eventually { share.urls.count == 1 })
        #expect(viewModel.exporting)
        await viewModel.export()
        #expect(citations.exportCalls.count == 1)

        share.release()
        await running.value
        #expect(!viewModel.exporting)
    }

    @Test func exportIncompleteShowsTheBannerAfterSharing() async {
        let citations = FakeCitationRepository(export: CitationResult(bibtex: Self.bib, complete: false))
        let viewModel = makeViewModel(citations: citations)
        #expect(await eventually { viewModel.papers.count == 3 })
        share.hold()

        let running = Task { await viewModel.export() }
        #expect(await eventually { share.urls.count == 1 })
        #expect(viewModel.message == nil)

        share.release()
        await running.value
        #expect(viewModel.message == .exportIncomplete)
    }

    @Test func aFailedExportShowsCouldntExportAndSharesNothing() async {
        let citations = FakeCitationRepository(export: CitationResult(bibtex: Self.bib, complete: true))
        citations.setFail(true)
        let viewModel = makeViewModel(citations: citations)
        #expect(await eventually { viewModel.papers.count == 3 })

        await viewModel.export()

        #expect(viewModel.message == .exportFailed)
        #expect(share.urls.isEmpty)
        #expect(!viewModel.exporting)
    }

    @Test func aFailedWriteShowsCouldntExportAndSharesNothing() async throws {
        // A plain file where the exports folder should be: creating the folder, and so the write, fails.
        let blocker = FileManager.default.temporaryDirectory.appendingPathComponent("library-blocker-\(UUID().uuidString)")
        try Data().write(to: blocker)
        let viewModel = makeViewModel(exportFiles: ExportFiles(directory: blocker))
        #expect(await eventually { viewModel.papers.count == 3 })

        await viewModel.export()

        #expect(viewModel.message == .exportFailed)
        #expect(share.urls.isEmpty)
    }
}

/// The share closure: records each file, and while held waits like an open share sheet.
@MainActor
private final class ShareRecorder {
    private(set) var urls: [URL] = []
    private var isHeld = false
    private var waiting: CheckedContinuation<Void, Never>?

    func share(_ url: URL) async {
        urls.append(url)
        guard isHeld else { return }
        await withCheckedContinuation { waiting = $0 }
    }

    func hold() {
        isHeld = true
    }

    func release() {
        isHeld = false
        waiting?.resume()
        waiting = nil
    }
}
