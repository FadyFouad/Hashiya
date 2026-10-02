import Foundation
import UIKit

/// PDFs written for a test: real files PDFKit opens, with a line of text on each page.
enum TestPDF {
    /// A PDF of `pages` US-letter pages in a fresh temporary file.
    static func make(pages: Int, password: String? = nil) throws -> URL {
        let format = UIGraphicsPDFRendererFormat()
        if let password {
            format.documentInfo = [
                kCGPDFContextUserPassword as String: password,
                kCGPDFContextOwnerPassword as String: password,
            ]
        }
        let bounds = CGRect(x: 0, y: 0, width: 612, height: 792)
        let data = UIGraphicsPDFRenderer(bounds: bounds, format: format).pdfData { context in
            for page in 1...pages {
                context.beginPage()
                ("Page \(page)" as NSString).draw(
                    at: CGPoint(x: 72, y: 72),
                    withAttributes: [.font: UIFont.systemFont(ofSize: 24)]
                )
            }
        }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).pdf")
        try data.write(to: url)
        return url
    }

    /// A file named .pdf that isn't one.
    static func damaged() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).pdf")
        try Data("<html>Sign in to read this article</html>".utf8).write(to: url)
        return url
    }
}
