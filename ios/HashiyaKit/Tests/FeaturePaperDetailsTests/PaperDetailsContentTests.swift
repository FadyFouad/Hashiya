@testable import FeaturePaperDetails
import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import SwiftUI
import Testing
import UIKit

/// Hostless package tests have no accessibility tree, so these lay the content out in a window and check which
/// strings it looked up (as `ReadingStatusSelectorTests` does). Typing is covered by `PaperDetailsFlowTests`.
@MainActor
@Suite(.serialized)
struct PaperDetailsContentTests {
    private func renderedStrings(of view: some View) -> [String] {
        let previous = (HashiyaLanguage.override, HashiyaStrings.recordedLookups)
        HashiyaLanguage.override = "en"
        HashiyaStrings.recordedLookups = []
        defer {
            HashiyaLanguage.override = previous.0
            HashiyaStrings.recordedLookups = previous.1
        }
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 4_000))
        window.rootViewController = UIHostingController(rootView: NavigationStack { view })
        window.makeKeyAndVisible()
        window.layoutIfNeeded()
        let rendered = HashiyaStrings.recordedLookups ?? []
        window.isHidden = true
        return rendered
    }

    private func content(
        _ paper: Paper,
        collections: [PaperCollection] = [],
        memberIDs: Set<Int64> = [],
        notes: PaperNotes? = PaperNotes(),
        saveState: NotesSaveState = .idle,
        message: PaperDetailsMessage? = nil,
        pdf: PdfRow? = nil
    ) -> PaperDetailsContent {
        PaperDetailsContent(
            paper: LibraryPaper(paper: paper, status: .toRead),
            collections: collections,
            memberIDs: memberIDs,
            pdf: pdf,
            notes: notes,
            saveState: saveState,
            message: message,
            actions: PaperDetailsActions()
        )
    }

    @Test func theSixSectionsAppearInOrder() {
        let rendered = renderedStrings(of: content(SamplePapers.vit))
        let labels = ["Summary", "Research question", "Method", "Key findings", "Limitations", "My thoughts"]
        let positions = labels.compactMap { rendered.firstIndex(of: $0) }
        #expect(positions.count == labels.count)
        #expect(positions == positions.sorted())
        #expect(rendered.contains("My notes"))
        #expect(rendered.contains("What is this paper about, in your own words?"))
    }

    @Test func thePdfRowReplacesOpenPDF() {
        let attention = renderedStrings(of: content(SamplePapers.attention))
        let bert = renderedStrings(of: content(SamplePapers.bert))
        #expect(!attention.contains("Open PDF"))
        #expect(attention.contains("Available to download"))
        #expect(attention.contains("Download PDF"))
        #expect(attention.contains("Open DOI"))
        #expect(bert.contains("No PDF"))
        #expect(bert.contains("Attach PDF"))
        #expect(bert.contains("Open DOI"))
    }

    @Test func thePdfRowShowsEachState() {
        let stored = PaperPdf(source: .attached, sizeBytes: 2_400_000, addedAt: 1)
        let storedRow = renderedStrings(of: content(SamplePapers.attention, pdf: PdfRow(state: .stored(stored), link: nil)))
        #expect(storedRow.contains("2.4 MB · Attached"))
        #expect(storedRow.contains("Read PDF"))

        let downloading = renderedStrings(of: content(
            SamplePapers.attention,
            pdf: PdfRow(state: .downloading(bytes: 1_000_000, total: 4_000_000), link: nil)
        ))
        #expect(downloading.contains("1 MB of 4 MB"))
        #expect(downloading.contains("Cancel"))

        let failed = renderedStrings(of: content(
            SamplePapers.attention,
            pdf: PdfRow(state: .failed(.notPDF), link: URL(string: "https://example.org/paper"))
        ))
        #expect(failed.contains("Couldn't get the PDF"))
        #expect(failed.contains("This link opens a web page, not a PDF."))
        #expect(failed.contains("Try again"))
        #expect(failed.contains("Open in browser"))
        #expect(failed.contains("Attach PDF"))
    }

    /// Collections, PDF and DOI are one group; the DOI is a row (labelled DOI, "Open DOI" for VoiceOver), hidden
    /// without a DOI.
    @Test func theDoiIsARowOfTheGroup() {
        let withDoi = renderedStrings(of: content(SamplePapers.attention))
        #expect(withDoi.contains("DOI"))
        #expect(withDoi.contains("Open DOI"))
        let positions = ["Collections", "PDF", "DOI"].compactMap { withDoi.firstIndex(of: $0) }
        #expect(positions.count == 3)
        #expect(positions == positions.sorted())

        let noDoi = renderedStrings(of: content(SamplePapers.vit))
        #expect(!noDoi.contains("DOI"))
        #expect(!noDoi.contains("Open DOI"))
    }

    @Test func theAttachMessagesShowTheirBanners() {
        #expect(renderedStrings(of: content(SamplePapers.vit, message: .pdfAttachNotPdf)).contains("That file isn't a PDF."))
        #expect(renderedStrings(of: content(SamplePapers.vit, message: .pdfAttachTooLarge)).contains("The PDF is larger than 100 MB."))
        #expect(renderedStrings(of: content(SamplePapers.vit, message: .pdfAttachFailed)).contains("Couldn't read that file."))
    }

    @Test func untitledAndNoAbstractRender() {
        let rendered = renderedStrings(of: content(SamplePapers.untitled))
        #expect(rendered.contains("Untitled"))
        #expect(rendered.contains("No abstract available"))
        #expect(!rendered.contains("Open DOI"))
    }

    @Test func theSaveStatusLineShowsEachState() {
        #expect(!renderedStrings(of: content(SamplePapers.vit, saveState: .idle)).contains("Saved"))
        #expect(renderedStrings(of: content(SamplePapers.vit, saveState: .saving)).contains("Saving…"))
        #expect(renderedStrings(of: content(SamplePapers.vit, saveState: .saved)).contains("Saved"))
        #expect(renderedStrings(of: content(SamplePapers.vit, saveState: .failed)).contains("Couldn't save"))
    }

    @Test func aSaveFailureShowsTheBannerWithRetry() {
        let rendered = renderedStrings(of: content(SamplePapers.vit, saveState: .failed, message: .notesSaveFailed))
        #expect(rendered.contains("Couldn't save your notes"))
        #expect(rendered.contains("Retry"))
    }

    @Test func notesThatCouldNotBeReadShowRetryAndNoFields() {
        let rendered = renderedStrings(of: content(SamplePapers.vit, notes: nil))
        #expect(rendered.contains("Couldn't load your notes"))
        #expect(rendered.contains("Retry"))
        #expect(!rendered.contains("Summary"))
    }

    @Test func theCollectionsRowSaysWhenThePaperIsInNone() {
        let none = renderedStrings(of: content(SamplePapers.vit))
        #expect(none.contains("Collections"))
        #expect(none.contains("Not in any collection"))

        let thesis = PaperCollection(id: 1, name: "Thesis", paperCount: 1)
        let notMember = renderedStrings(of: content(SamplePapers.vit, collections: [thesis]))
        #expect(notMember.contains("Not in any collection"))

        let member = renderedStrings(of: content(SamplePapers.vit, collections: [thesis], memberIDs: [1]))
        #expect(member.contains("Collections"))
        #expect(!member.contains("Not in any collection"))
    }

    @Test func eachNewMessageShowsItsBanner() {
        #expect(renderedStrings(of: content(SamplePapers.vit, message: .bibtexCopied)).contains("BibTeX copied"))
        #expect(renderedStrings(of: content(SamplePapers.vit, message: .apaCopied)).contains("APA citation copied"))
        #expect(renderedStrings(of: content(SamplePapers.vit, message: .ieeeCopied)).contains("IEEE citation copied"))
        #expect(renderedStrings(of: content(SamplePapers.vit, message: .citationIncomplete))
            .contains("Some details may be missing. Copy again when you're online."))
        #expect(renderedStrings(of: content(SamplePapers.vit, message: .copyFailed)).contains("Couldn't copy the citation"))
        #expect(renderedStrings(of: content(SamplePapers.vit, message: .collectionsUpdateFailed))
            .contains("Couldn't update collections"))
    }

    @Test func theChecklistOffersNewCollectionAndExplainsWhenEmpty() {
        let empty = renderedStrings(of: CollectionsChecklist(collections: [], memberIDs: [], message: nil, actions: CollectionsChecklistActions()))
        #expect(empty.contains("New collection"))
        #expect(empty.contains("Group papers for a chapter, a course or a project."))

        let some = renderedStrings(of: CollectionsChecklist(
            collections: [PaperCollection(id: 1, name: "Thesis", paperCount: 1)],
            memberIDs: [1],
            message: .collectionsUpdateFailed,
            actions: CollectionsChecklistActions()
        ))
        #expect(some.contains("New collection"))
        #expect(!some.contains("Group papers for a chapter, a course or a project."))
        #expect(some.contains("Couldn't update collections"))
    }
}
