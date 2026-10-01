import PDFKit
import SwiftUI

/// The document's pages as still images, drawn synchronously. Snapshot tests use it in place of `PDFKitView`, whose
/// tiles render asynchronously and would make baselines flaky.
public struct ReaderPageImages: View {
    private let document: PDFDocument
    private let width: CGFloat

    public init(document: PDFDocument, width: CGFloat = 360) {
        self.document = document
        self.width = width
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                ForEach(0..<document.pageCount, id: \.self) { index in
                    if let page = document.page(at: index) {
                        let box = page.bounds(for: .mediaBox)
                        let size = CGSize(width: width, height: width * box.height / max(box.width, 1))
                        Image(uiImage: page.thumbnail(of: size, for: .mediaBox))
                            .resizable()
                            .frame(width: size.width, height: size.height)
                            .shadow(radius: 1)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
        .background(Color(uiColor: .secondarySystemBackground))
    }
}
