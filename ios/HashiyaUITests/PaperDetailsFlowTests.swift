import XCTest

/// Details end to end with `-ui-testing`: the UI tests' own library file and the stub search, no network.
@MainActor
final class PaperDetailsFlowTests: XCTestCase {
    private func launchApp() -> XCUIApplication {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-ui-testing", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        return app
    }

    private let searchField = "Search, or paste a DOI, arXiv ID or link"

    /// Saves the stub search's first paper, Attention, and leaves the app on the Search results.
    private func saveAttention(in app: XCUIApplication) {
        app.tabBars.buttons["Search"].tap()
        let field = app.searchFields[searchField]
        XCTAssertTrue(field.waitForExistence(timeout: UITestTimeout.long))
        field.tap()
        field.typeText("attention\n")
        XCTAssertTrue(app.staticTexts["About 3 results"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Save"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["In library"].waitForExistence(timeout: UITestTimeout.long))
    }

    private func attentionRow(in app: XCUIApplication) -> XCUIElement {
        app.cells.containing(NSPredicate(format: "label BEGINSWITH %@", "Attention Is All You Need")).firstMatch
    }

    private func openAttentionFromTheLibrary(in app: XCUIApplication) {
        app.tabBars.buttons["Library"].tap()
        let row = attentionRow(in: app)
        XCTAssertTrue(row.waitForExistence(timeout: UITestTimeout.long))
        row.buttons.firstMatch.tap()
        XCTAssertTrue(noteField("summary", in: app).waitForExistence(timeout: UITestTimeout.long))
    }

    /// A Search result: its title is one button whose label starts with the title (`PaperCard`).
    private func card(_ titlePrefix: String, in app: XCUIApplication) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", titlePrefix)).firstMatch
    }

    /// A note field by its section key. A vertical `TextField` is a text field or a text view depending on the iOS
    /// version, so it is found by its identifier alone.
    private func noteField(_ key: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: "note.\(key)").firstMatch
    }

    private func back(in app: XCUIApplication) {
        app.navigationBars.buttons.element(boundBy: 0).tap()
    }

    func testNotesSaveAndAreFoundByTheLibrarySearch() {
        let app = launchApp()
        saveAttention(in: app)
        openAttentionFromTheLibrary(in: app)
        XCTAssertFalse(app.tabBars.firstMatch.isHittable, "Details hides the tab bar")
        XCTAssertTrue(app.staticTexts["Ashish Vaswani, Noam Shazeer"].exists)

        let summary = noteField("summary", in: app)
        summary.tap()
        summary.typeText("Ablation")
        let method = noteField("method", in: app)
        method.tap()
        method.typeText("Encoder")
        XCTAssertTrue(app.staticTexts["Saved"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertEqual(summary.value as? String, "Ablation")
        XCTAssertEqual(method.value as? String, "Encoder")

        back(in: app)
        let field = app.searchFields["Search your library"]
        XCTAssertTrue(field.waitForExistence(timeout: UITestTimeout.long))
        field.tap()
        field.typeText("ablation\n")
        XCTAssertTrue(app.staticTexts["1 paper"].waitForExistence(timeout: UITestTimeout.long))

        attentionRow(in: app).buttons.firstMatch.tap()
        XCTAssertTrue(noteField("summary", in: app).waitForExistence(timeout: UITestTimeout.long))
        XCTAssertEqual(noteField("summary", in: app).value as? String, "Ablation")
    }

    func testRemoveFromDetailsOffersUndoWithTheNotes() {
        let app = launchApp()
        saveAttention(in: app)
        openAttentionFromTheLibrary(in: app)
        let summary = noteField("summary", in: app)
        summary.tap()
        summary.typeText("Keep this")

        // Remove right away: the pending note is saved before the paper goes.
        app.navigationBars.buttons["More options"].tap()
        app.buttons["Remove from library"].tap()

        XCTAssertTrue(app.staticTexts["Removed from library"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.staticTexts["No saved papers yet"].waitForExistence(timeout: UITestTimeout.long))
        let row = attentionRow(in: app)
        XCTAssertTrue(app.tapUndo(expecting: row))
        row.buttons.firstMatch.tap()
        XCTAssertTrue(noteField("summary", in: app).waitForExistence(timeout: UITestTimeout.long))
        XCTAssertEqual(noteField("summary", in: app).value as? String, "Keep this")
    }

    func testSearchOpensDetailsForSavedPapersOnly() {
        let app = launchApp()
        saveAttention(in: app)

        // BERT isn't saved: its sheet has no Open details.
        card("BERT", in: app).tap()
        XCTAssertTrue(app.buttons["Save to library"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertFalse(app.buttons["Open details"].exists)
        app.swipeDown(velocity: .fast)

        card("Attention Is All You Need", in: app).tap()
        let open = app.buttons["Open details"]
        XCTAssertTrue(open.waitForExistence(timeout: UITestTimeout.long))
        open.tap()

        XCTAssertTrue(noteField("summary", in: app).waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.navigationBars.buttons["More options"].exists)
        back(in: app)
        XCTAssertTrue(app.staticTexts["About 3 results"].waitForExistence(timeout: UITestTimeout.long))
    }
}
