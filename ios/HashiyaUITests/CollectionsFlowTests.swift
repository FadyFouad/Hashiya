import XCTest

/// Collections and BibTeX end to end with `-ui-testing`: the UI tests' own library file and the stub search, no
/// network. Papers saved from the stub search already have their publication details, and Task 8's offline lookup never refetches.
@MainActor
final class CollectionsFlowTests: XCTestCase {
    private func launchApp() -> XCUIApplication {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-ui-testing", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        return app
    }

    private let searchField = "Search, or paste a DOI, arXiv ID or link"

    /// Saves the stub search's first two papers, Attention and BERT, and leaves the app on the Search results.
    private func saveAttentionAndBERT(in app: XCUIApplication) {
        app.tab("Search").tap()
        let field = app.searchFields[searchField]
        XCTAssertTrue(field.waitForExistence(timeout: UITestTimeout.long))
        field.tap()
        field.typeText("attention\n")
        XCTAssertTrue(app.staticTexts["About 3 results"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Save"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["In library"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Save"].firstMatch.tap()
        let twoSaved = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "count == 2"),
            object: app.staticTexts.matching(NSPredicate(format: "label == %@", "In library"))
        )
        XCTAssertEqual(XCTWaiter().wait(for: [twoSaved], timeout: UITestTimeout.long), .completed)
    }

    private func row(_ titlePrefix: String, in app: XCUIApplication) -> XCUIElement {
        app.cells.containing(NSPredicate(format: "label BEGINSWITH %@", titlePrefix)).firstMatch
    }

    private func openFromTheLibrary(_ titlePrefix: String, in app: XCUIApplication) {
        app.tab("Library").tap()
        let row = row(titlePrefix, in: app)
        XCTAssertTrue(row.waitForExistence(timeout: UITestTimeout.long))
        row.buttons.firstMatch.tap()
        XCTAssertTrue(app.buttons["details.collections"].waitForExistence(timeout: UITestTimeout.long))
    }

    private func back(in app: XCUIApplication) {
        app.navigationBars.buttons.element(boundBy: 0).tap()
    }

    /// The Library's title menu (`toolbarTitleMenu`): the title is a button in the navigation bar.
    private func openTitleMenu(showing title: String, in app: XCUIApplication) {
        // Any bar: on iPad the list's bar sits beside the detail pane's.
        let button = app.navigationBars.buttons[title].firstMatch
        if button.waitForExistence(timeout: UITestTimeout.long) {
            button.tap()
        } else {
            app.navigationBars.staticTexts[title].firstMatch.tap()
        }
    }

    /// The share sheet's own close control. iOS 18's sheet has a Close button; iOS 26 shows a popover without one, closed by tapping outside it. Waits for
    /// either for as long as other steps wait: on a slow CI runner the button can appear seconds after the sheet's rows.
    private func closeShareSheet(in app: XCUIApplication) {
        let close = app.buttons["Close"].firstMatch
        let outside = app.otherElements["PopoverDismissRegion"].firstMatch
        let either = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in close.exists || outside.exists },
            object: nil
        )
        guard XCTWaiter().wait(for: [either], timeout: UITestTimeout.long) == .completed else {
            return XCTFail("The share sheet showed neither a Close button nor a dismiss region")
        }
        if close.exists {
            close.tap()
        } else {
            outside.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.1)).tap()
        }
    }

    /// Text that contains `text`; banners isolate names with invisible bidi marks, so exact matches can't be used.
    private func text(containing text: String, in app: XCUIApplication) -> XCUIElement {
        app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
    }

    func testACollectionMadeOnDetailsFiltersTheLibraryAndSwipeRemovesFromItOnly() {
        let app = launchApp()
        saveAttentionAndBERT(in: app)
        openFromTheLibrary("Attention Is All You Need", in: app)
        XCTAssertTrue(app.staticTexts["Not in any collection"].exists)

        // Details → Collections → New collection "Thesis": the paper is in it.
        app.buttons["details.collections"].tap()
        let new = app.buttons["New collection"]
        XCTAssertTrue(new.waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.staticTexts["Group papers for a chapter, a course or a project."].exists)
        new.tap()
        let name = app.textFields["Collection name"]
        XCTAssertTrue(name.waitForExistence(timeout: UITestTimeout.long))
        name.typeText("Thesis")
        app.buttons["Create"].tap()
        let thesis = app.buttons["collection.Thesis"]
        XCTAssertTrue(thesis.waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(thesis.isSelected)
        app.buttons["Done"].tap()
        XCTAssertTrue(app.buttons["details.collections"].staticTexts["Thesis"].waitForExistence(timeout: UITestTimeout.long))

        // The Library's title menu → Thesis: only Attention. (iPad shows Details beside the list: no going back.)
        if !UITestDevice.isPad { back(in: app) }
        XCTAssertTrue(row("BERT", in: app).waitForExistence(timeout: UITestTimeout.long))
        openTitleMenu(showing: "All papers", in: app)
        app.buttons["Thesis"].tap()
        XCTAssertTrue(row("Attention Is All You Need", in: app).waitForExistence(timeout: UITestTimeout.long))
        let bertRow = row("BERT", in: app)
        let gone = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: bertRow)
        XCTAssertEqual(XCTWaiter().wait(for: [gone], timeout: UITestTimeout.long), .completed)
        XCTAssertFalse(bertRow.exists)

        // Swipe removes it from Thesis only, with Undo.
        row("Attention Is All You Need", in: app).swipeLeft()
        app.buttons["Remove from collection"].tap()
        XCTAssertTrue(text(containing: "Removed from", in: app).waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.staticTexts["No papers in this collection yet. Add papers from their details screen."]
            .waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.tapUndo(expecting: row("Attention Is All You Need", in: app)))

        // Swipe again, no Undo: All papers still has both.
        row("Attention Is All You Need", in: app).swipeLeft()
        app.buttons["Remove from collection"].tap()
        XCTAssertTrue(app.staticTexts["No papers in this collection yet. Add papers from their details screen."]
            .waitForExistence(timeout: UITestTimeout.long))
        openTitleMenu(showing: "Thesis", in: app)
        app.buttons["All papers"].tap()
        XCTAssertTrue(row("Attention Is All You Need", in: app).waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(row("BERT", in: app).exists)
    }

    func testCopyBibTeXFromDetailsSaysItCopied() {
        let app = launchApp()
        saveAttentionAndBERT(in: app)
        openFromTheLibrary("Attention Is All You Need", in: app)

        app.navigationBars.buttons["More options"].tap()
        let copy = app.buttons["Copy BibTeX"]
        XCTAssertTrue(copy.waitForExistence(timeout: UITestTimeout.long))
        copy.tap()

        // The pasteboard itself isn't read: reading it from a UI test asks for paste permission.
        XCTAssertTrue(app.staticTexts["BibTeX copied"].waitForExistence(timeout: UITestTimeout.long))
    }

    func testExportStaysBusyUntilTheShareSheetCloses() {
        let app = launchApp()
        saveAttentionAndBERT(in: app)
        app.tab("Library").tap()
        let export = app.buttons["Export .bib"]
        XCTAssertTrue(export.waitForExistence(timeout: UITestTimeout.long))

        export.tap()
        let saveToFiles = app.cells["Save to Files"]
        XCTAssertTrue(saveToFiles.waitForExistence(timeout: UITestTimeout.long))
        XCTAssertFalse(app.buttons["Export .bib"].exists, "Export stays busy while the share sheet is open")

        closeShareSheet(in: app)
        XCTAssertTrue(app.buttons["Export .bib"].waitForExistence(timeout: UITestTimeout.long))
    }
}
