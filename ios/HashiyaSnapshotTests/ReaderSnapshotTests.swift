@testable import FeatureReader
import HashiyaData
import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import PDFKit
import SwiftUI
import Testing
import UIKit

@MainActor
@Suite(.serialized)
struct ReaderSnapshotTests {
    /// Two pages with a heading and a few lines each, drawn the same way every run.
    private static let document: PDFDocument = {
        let bounds = CGRect(x: 0, y: 0, width: 612, height: 792)
        let data = UIGraphicsPDFRenderer(bounds: bounds).pdfData { context in
            for page in 1...2 {
                context.beginPage()
                ("Attention Is All You Need — page \(page)" as NSString).draw(
                    at: CGPoint(x: 72, y: 72),
                    withAttributes: [.font: UIFont.boldSystemFont(ofSize: 20), .foregroundColor: UIColor.black]
                )
                for line in 0..<12 {
                    ("The Transformer relies entirely on attention, dispensing with recurrence." as NSString).draw(
                        at: CGPoint(x: 72, y: 120 + CGFloat(line) * 28),
                        withAttributes: [.font: UIFont.systemFont(ofSize: 13), .foregroundColor: UIColor.darkGray]
                    )
                }
            }
        }
        return PDFDocument(data: data)!
    }()

    private func reader(state: ReaderState, showsPill: Bool = false, message: ReaderMessage? = nil, notesSaveFailed: Bool = false) -> some View {
        NavigationStack {
            ReaderContent(
                title: SamplePapers.attention.title,
                state: state,
                pageLabel: L10n.page(0, of: 2),
                showsPill: showsPill,
                fileURL: URL(fileURLWithPath: "/tmp/attention.pdf"),
                message: message,
                notesSaveFailed: notesSaveFailed,
                actions: ReaderActions()
            ) {
                ReaderPageImages(document: Self.document)
            }
        }
    }

    /// The pages, the toolbar and the page pill.
    @Test func pages() {
        assertHashiyaSnapshots(of: reader(state: .ready(pageCount: 2, startPage: 0), showsPill: true), named: "pages", arabicText: "الملاحظات")
    }

    /// A damaged file: the message with Replace and Remove; no Search or Share.
    @Test func cantOpen() {
        assertHashiyaSnapshots(of: reader(state: .cantOpen), named: "cantOpen", arabicText: "تعذّر فتح ملف PDF هذا.")
    }

    /// The notes failed to save: the banner with Retry over the pages.
    @Test func notesSaveFailed() {
        assertHashiyaSnapshots(
            of: reader(state: .ready(pageCount: 2, startPage: 0), notesSaveFailed: true),
            named: "notesSaveFailed",
            arabicText: "تعذّر حفظ ملاحظاتك"
        )
    }

    /// The Notes sheet at its medium detent over the pages, notes typed and saved. Drawn in place of the system sheet:
    /// a presented sheet appears asynchronously, after a key-window snapshot is taken.
    @Test func notesSheet() {
        var notes = PaperNotes()
        notes[.summary] = "Attention alone, no recurrence: faster to train and better on translation."
        notes[.method] = "Encoder–decoder of stacked self-attention and feed-forward layers."
        let view = ZStack(alignment: .bottom) {
            reader(state: .ready(pageCount: 2, startPage: 0))
            Color.black.opacity(0.2)
                .ignoresSafeArea()
            ReaderNotesSheet(notes: notes, saveState: .saved, onChange: { _, _ in }, onClose: {})
                .frame(height: 440)
                .background(HashiyaColors.surface)
                .clipShape(UnevenRoundedRectangle(topLeadingRadius: 24, topTrailingRadius: 24, style: .continuous))
                .ignoresSafeArea(edges: .bottom)
        }
        assertHashiyaSnapshots(of: view, named: "notesSheet", arabicText: "إغلاق الملاحظات")
    }
}
