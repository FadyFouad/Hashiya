@testable import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import SwiftUI
import Testing
import UIKit

@MainActor
@Suite(.serialized)
struct ReadingStatusSelectorTests {
    private func inLanguage<T>(_ language: String, _ body: () -> T) -> T {
        let previous = HashiyaLanguage.override
        HashiyaLanguage.override = language
        defer { HashiyaLanguage.override = previous }
        return body()
    }

    /// The strings `view` looks up while it is laid out in a window, in English.
    private func renderedStrings(of view: some View) -> [String] {
        inLanguage("en") {
            let previous = HashiyaStrings.recordedLookups
            HashiyaStrings.recordedLookups = []
            defer { HashiyaStrings.recordedLookups = previous }
            let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
            window.rootViewController = UIHostingController(rootView: view)
            window.makeKeyAndVisible()
            window.layoutIfNeeded()
            let rendered = HashiyaStrings.recordedLookups ?? []
            window.isHidden = true
            return rendered
        }
    }

    @Test func statusesHaveTheirNamesInBothLanguages() {
        #expect(inLanguage("en") { ReadingStatus.allCases.map(readingStatusLabel) } == ["To read", "Reading", "Read"])
        #expect(inLanguage("ar") { ReadingStatus.allCases.map(readingStatusLabel) } == ["للقراءة", "قيد القراءة", "مقروءة"])
    }

    @Test func thePreviewShowsOpenDetailsOnlyWhenGiven() {
        let with = renderedStrings(of: PaperPreviewContent(
            paper: SamplePapers.attention, inLibrary: true, onToggleSave: {}, onOpenDOI: nil, onOpenDetails: {}
        ))
        let without = renderedStrings(of: PaperPreviewContent(
            paper: SamplePapers.attention, inLibrary: true, onToggleSave: {}, onOpenDOI: nil
        ))

        #expect(with.contains("Open details"))
        #expect(with.contains("Remove from library"))
        #expect(!without.contains("Open details"))
    }

    @Test func sharedStringsResolveInBothLanguages() {
        #expect(inLanguage("en") { [DesignSystemStrings.abstract, DesignSystemStrings.noAbstract, DesignSystemStrings.openDOI, DesignSystemStrings.removeFromLibrary] }
            == ["Abstract", "No abstract available", "Open DOI", "Remove from library"])
        #expect(inLanguage("en") { DesignSystemStrings.openAccess(hasPDF: true) } == "Open access · PDF available")
        #expect(inLanguage("ar") { DesignSystemStrings.openAccess(hasPDF: true) } == "وصول مفتوح · ملف PDF متاح")
        #expect(inLanguage("ar") { DesignSystemStrings.removeFromLibrary } == "إزالة من المكتبة")
    }

    @Test func choosingAnotherSegmentCallsOnStatusChange() {
        var received: [ReadingStatus] = []
        let selector = ReadingStatusSelector(status: .toRead) { received.append($0) }

        selector.selection.wrappedValue = .reading
        selector.selection.wrappedValue = .toRead

        #expect(received == [.reading])
        #expect(selector.selection.wrappedValue == .toRead)
    }
}
