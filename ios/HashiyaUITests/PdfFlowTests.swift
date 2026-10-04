import XCTest

/// The PDF end to end with `-ui-testing`: the stub library serves a small PDF for Attention's open-access link, so
/// nothing touches the network.
@MainActor
final class PdfFlowTests: XCTestCase {
    private func launchApp() -> XCUIApplication {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-ui-testing", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        return app
    }

    private let searchField = "Search, or paste a DOI, arXiv ID or link"

    private func noteField(_ key: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: "note.\(key)").firstMatch
    }

    private func attentionRow(in app: XCUIApplication) -> XCUIElement {
        app.cells.containing(NSPredicate(format: "label BEGINSWITH %@", "Attention Is All You Need")).firstMatch
    }

    /// The stored PDF row, the Read action. Found by its identifier: its combined label starts "PDF, PDF · …".
    private func storedPdfRow(in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: "details.pdfRead").firstMatch
    }

    private func readerBack(in app: XCUIApplication) -> XCUIElement {
        app.buttons["reader.back"]
    }

    /// Saves Attention from the stub search and opens it from the Library.
    private func openAttentionDetails(in app: XCUIApplication) {
        app.tab("Search").tap()
        let field = app.searchFields[searchField]
        XCTAssertTrue(field.waitForExistence(timeout: UITestTimeout.long))
        field.tap()
        field.typeText("attention\n")
        XCTAssertTrue(app.staticTexts["About 3 results"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Save"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["In library"].waitForExistence(timeout: UITestTimeout.long))
        app.tab("Library").tap()
        let row = attentionRow(in: app)
        XCTAssertTrue(row.waitForExistence(timeout: UITestTimeout.long))
        row.buttons.firstMatch.tap()
        XCTAssertTrue(noteField("summary", in: app).waitForExistence(timeout: UITestTimeout.long))
    }

    /// Downloads the stub PDF from Details and waits for the stored row.
    private func downloadThePdf(in app: XCUIApplication) {
        let download = app.buttons["Download PDF"]
        XCTAssertTrue(download.waitForExistence(timeout: UITestTimeout.long))
        download.tap()
        XCTAssertTrue(storedPdfRow(in: app).waitForExistence(timeout: UITestTimeout.long), "The stub PDF is stored and the row offers Read")
    }

    /// Opens the PDF row's menu and picks `action`, then confirms it in the dialog that follows.
    private func pickFromThePdfMenu(_ action: String, in app: XCUIApplication) {
        let menu = app.buttons["details.pdfMenu"]
        XCTAssertTrue(menu.waitForExistence(timeout: UITestTimeout.long))
        menu.tap()
        let item = app.buttons[action].firstMatch
        XCTAssertTrue(item.waitForExistence(timeout: UITestTimeout.long))
        item.tap()
        let title = action == "Replace PDF" ? "Replace this PDF?" : "Remove this PDF?"
        XCTAssertTrue(app.staticTexts[title].waitForExistence(timeout: UITestTimeout.long))
        let confirm = app.buttons[action].firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: UITestTimeout.long))
        confirm.tap()
    }

    func testDownloadReadAndWriteANoteFromTheReader() {
        let app = launchApp()
        openAttentionDetails(in: app)
        downloadThePdf(in: app)
        XCTAssertFalse(app.buttons["Download PDF"].exists)

        storedPdfRow(in: app).tap()
        let notes = app.buttons["reader.notes"]
        XCTAssertTrue(notes.waitForExistence(timeout: UITestTimeout.long), "The reader opens")
        XCTAssertTrue(app.descendants(matching: .any)["reader.pages"].waitForExistence(timeout: UITestTimeout.long))
        if !UITestDevice.isPad {
            // iPad keeps the tab bar at regular width.
            XCTAssertFalse(app.tabBars.firstMatch.isHittable, "The reader hides the tab bar")
        }

        // The reader hides the system back button, and a swipe from the leading edge doesn't leave it either.
        let window = app.windows.firstMatch
        window.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: 0.5))
            .press(forDuration: 0.05, thenDragTo: window.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)))
        XCTAssertTrue(readerBack(in: app).waitForExistence(timeout: UITestTimeout.long), "The edge swipe keeps the reader on screen")
        XCTAssertTrue(readerBack(in: app).isHittable)
        XCTAssertFalse(storedPdfRow(in: app).exists, "Details stays under the reader")

        notes.tap()
        let summary = noteField("summary", in: app)
        XCTAssertTrue(summary.waitForExistence(timeout: UITestTimeout.long))
        summary.tap()
        summary.typeText("Read in the reader")
        let close = app.buttons["reader.closeNotes"]
        XCTAssertTrue(close.waitForExistence(timeout: UITestTimeout.long))
        close.tap()

        // Back to Details: the note written in the reader is there, and the PDF row still says it's stored.
        readerBack(in: app).tap()
        let detailsSummary = noteField("summary", in: app)
        XCTAssertTrue(detailsSummary.waitForExistence(timeout: UITestTimeout.long))
        let noted = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", "Read in the reader"), object: detailsSummary)
        XCTAssertEqual(XCTWaiter().wait(for: [noted], timeout: UITestTimeout.long), .completed)
        XCTAssertTrue(storedPdfRow(in: app).waitForExistence(timeout: UITestTimeout.long))

        // Details follows the PDF again after the reader: removing it brings back Download PDF.
        pickFromThePdfMenu("Remove PDF", in: app)
        XCTAssertTrue(app.buttons["Download PDF"].waitForExistence(timeout: UITestTimeout.long), "The row follows the store after Back")
        XCTAssertFalse(storedPdfRow(in: app).exists)
    }

    /// Replace PDF, confirmed, opens the Files picker.
    func testReplaceOpensTheFilesPicker() {
        let app = launchApp()
        openAttentionDetails(in: app)
        downloadThePdf(in: app)

        pickFromThePdfMenu("Replace PDF", in: app)
        // The picker's own navigation bar, not the dialog's Cancel, which can linger while the dialog closes.
        let picker = app.navigationBars["FullDocumentManagerViewControllerNavigationBar"]
        XCTAssertTrue(picker.waitForExistence(timeout: UITestTimeout.long), "The Files picker appears")
        // iPad: the picker has a sidebar, and Cancel sits in the sidebar's bar.
        (UITestDevice.isPad ? app.navigationBars["DOCSidebarView"] : picker).buttons["Cancel"].tap()
        XCTAssertTrue(picker.waitForNonExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(storedPdfRow(in: app).waitForExistence(timeout: UITestTimeout.long), "Cancelling keeps the PDF")
        XCTAssertTrue(storedPdfRow(in: app).isHittable)
    }

    /// Remove → Undo keeps the PDF: the restored paper's Details still has its stored row.
    func testUndoKeepsThePdf() {
        let app = launchApp()
        openAttentionDetails(in: app)
        downloadThePdf(in: app)

        app.navigationBars.buttons["More options"].tap()
        app.buttons["Remove from library"].tap()
        XCTAssertTrue(app.staticTexts["Removed from library"].waitForExistence(timeout: UITestTimeout.long))
        let row = attentionRow(in: app)
        XCTAssertTrue(app.tapUndo(expecting: row))

        row.buttons.firstMatch.tap()
        XCTAssertTrue(storedPdfRow(in: app).waitForExistence(timeout: UITestTimeout.long))
    }
}
