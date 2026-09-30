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
        notes: PaperNotes? = PaperNotes(),
        saveState: NotesSaveState = .idle,
        message: PaperDetailsMessage? = nil
    ) -> PaperDetailsContent {
        PaperDetailsContent(
            paper: LibraryPaper(paper: paper, status: .toRead),
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

    @Test func openPDFShowsOnlyWithAPDF() {
        let attention = renderedStrings(of: content(SamplePapers.attention))
        let bert = renderedStrings(of: content(SamplePapers.bert))
        #expect(attention.contains("Open PDF"))
        #expect(attention.contains("Open DOI"))
        #expect(!bert.contains("Open PDF"))
        #expect(bert.contains("Open DOI"))
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
}
