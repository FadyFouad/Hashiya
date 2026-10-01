import XCTest

/// End to end with `-ui-testing`: the UI tests' own library file, stub search and lookup, no network.
@MainActor
final class LibraryFlowTests: XCTestCase {
    @discardableResult
    private func launchApp() -> XCUIApplication {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-ui-testing", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        return app
    }

    @MainActor
    func testSaveFromSearchThenRemoveAndUndoInLibrary() {
        let app = launchApp()
        XCTAssertTrue(app.staticTexts["No saved papers yet"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Go to Search"].tap()

        let field = app.searchFields["Search, or paste a DOI, arXiv ID or link"]
        XCTAssertTrue(field.waitForExistence(timeout: UITestTimeout.long))
        field.tap()
        field.typeText("attention\n")
        XCTAssertTrue(app.staticTexts["About 3 results"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Save"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["In library"].waitForExistence(timeout: UITestTimeout.long))

        app.tabBars.buttons["Library"].tap()
        let row = app.cells.containing(NSPredicate(format: "label BEGINSWITH %@", "Attention Is All You Need")).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.staticTexts["1 paper"].exists)

        row.swipeLeft()
        app.buttons["Remove"].tap()
        XCTAssertTrue(app.staticTexts["Removed from library"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.staticTexts["No saved papers yet"].waitForExistence(timeout: UITestTimeout.long))

        app.buttons["Undo"].tap()
        XCTAssertTrue(row.waitForExistence(timeout: UITestTimeout.long))
    }

    @MainActor
    func testPastingAnArxivIDShowsThePaperToSave() {
        let app = launchApp()
        app.tabBars.buttons["Search"].tap()
        let field = app.searchFields["Search, or paste a DOI, arXiv ID or link"]
        XCTAssertTrue(field.waitForExistence(timeout: UITestTimeout.long))
        field.tap()
        field.typeText("arXiv:1706.03762\n")

        XCTAssertTrue(app.staticTexts["Attention Is All You Need"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Save to library"].tap()
        XCTAssertTrue(app.buttons["Remove from library"].waitForExistence(timeout: UITestTimeout.long))

        app.tabBars.buttons["Library"].tap()
        XCTAssertTrue(app.staticTexts["1 paper"].waitForExistence(timeout: UITestTimeout.long))
    }

    @MainActor
    func testAddPaperOpensSearchReadyForInput() {
        let app = launchApp()
        XCTAssertTrue(app.buttons["Add paper"].waitForExistence(timeout: UITestTimeout.long))

        app.buttons["Add paper"].tap()

        XCTAssertTrue(app.tabBars.buttons["Search"].isSelected)
        let field = app.searchFields["Search, or paste a DOI, arXiv ID or link"]
        XCTAssertTrue(field.waitForExistence(timeout: UITestTimeout.long))
        let focused = expectation(for: NSPredicate(format: "hasKeyboardFocus == true"), evaluatedWith: field)
        wait(for: [focused], timeout: UITestTimeout.long)
        XCTAssertEqual(field.value as? String, "Search, or paste a DOI, arXiv ID or link")
    }

    /// Saves the stub search's first two papers: Attention, then BERT (the newest, listed first).
    private func saveTwoPapers(in app: XCUIApplication) {
        app.tabBars.buttons["Search"].tap()
        let field = app.searchFields["Search, or paste a DOI, arXiv ID or link"]
        XCTAssertTrue(field.waitForExistence(timeout: UITestTimeout.long))
        field.tap()
        field.typeText("attention\n")
        XCTAssertTrue(app.staticTexts["About 3 results"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Save"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["In library"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Save"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts.matching(identifier: "In library").element(boundBy: 1).waitForExistence(timeout: UITestTimeout.long))
        app.tabBars.buttons["Library"].tap()
        XCTAssertTrue(app.staticTexts["2 papers"].waitForExistence(timeout: UITestTimeout.long))
    }

    private func row(_ titlePrefix: String, in app: XCUIApplication) -> XCUIElement {
        app.cells.containing(NSPredicate(format: "label BEGINSWITH %@", titlePrefix)).firstMatch
    }

    @MainActor
    func testChangingAStatusFiltersAndSearchesTheLibrary() {
        let app = launchApp()
        saveTwoPapers(in: app)
        XCTAssertTrue(app.buttons["All · 2"].isSelected)
        XCTAssertTrue(app.buttons["Reading · 0"].exists)

        // The badge's menu lists the three statuses with the current one checked.
        row("Attention Is All You Need", in: app).buttons["Status: To read. Change status"].tap()
        XCTAssertTrue(app.buttons["Reading"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertFalse(app.segmentedControls.firstMatch.exists, "Tapping the badge must not open Details")
        XCTAssertTrue(app.buttons["To read"].isSelected)
        XCTAssertFalse(app.buttons["Reading"].isSelected)
        XCTAssertFalse(app.buttons["Read"].isSelected)
        app.buttons["Reading"].tap()

        XCTAssertTrue(app.buttons["Reading · 1"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(row("Attention Is All You Need", in: app).buttons["Status: Reading. Change status"].exists)
        app.buttons["Reading · 1"].tap()
        XCTAssertTrue(app.staticTexts["1 paper"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.buttons["Reading · 1"].isSelected)
        XCTAssertFalse(row("BERT", in: app).exists)

        // A word from the other paper's title: No papers match, with the keyboard still up while typing.
        let field = app.searchFields["Search your library"]
        field.tap()
        field.typeText("bert")
        XCTAssertTrue(app.staticTexts["No papers match"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.keyboards.firstMatch.exists)

        app.buttons["Clear search and filters"].tap()
        XCTAssertTrue(app.staticTexts["2 papers"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.buttons["All · 2"].isSelected)
        XCTAssertTrue(row("BERT", in: app).exists)
        XCTAssertTrue(row("Attention Is All You Need", in: app).exists)
    }

    @MainActor
    func testDetailsChangesTheStatusAndTheSearchKeyHidesTheKeyboard() {
        let app = launchApp()
        saveTwoPapers(in: app)

        row("BERT", in: app).buttons.firstMatch.tap()
        let read = app.segmentedControls.buttons["Read"]
        XCTAssertTrue(read.waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.segmentedControls.buttons["To read"].isSelected)
        read.tap()
        XCTAssertTrue(read.isSelected)
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.buttons["Read · 1"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(row("BERT", in: app).buttons["Status: Read. Change status"].exists)

        let field = app.searchFields["Search your library"]
        field.tap()
        field.typeText("vaswani\n")
        XCTAssertTrue(app.staticTexts["1 paper"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(row("Attention Is All You Need", in: app).exists)
        let hidden = expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: app.keyboards.firstMatch)
        wait(for: [hidden], timeout: UITestTimeout.long)
    }

    /// iOS 26 hid the Library's large title above the list with the chips bar; it must show like on iOS 18.
    @MainActor
    func testTheLibraryShowsItsLargeTitle() {
        let app = launchApp()
        saveTwoPapers(in: app)
        XCTAssertTrue(app.navigationBars.staticTexts["All papers"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.navigationBars.staticTexts["All papers"].isHittable)
    }

    @MainActor
    func testSettingsSavesAndResetsTheUserKey() {
        let app = launchApp()
        app.buttons["Settings"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Using built-in key"].waitForExistence(timeout: UITestTimeout.long))

        let field = app.secureTextFields["API key"]
        field.tap()
        field.typeText("my-key")
        app.buttons["Save"].tap()
        XCTAssertTrue(app.staticTexts["Using your key"].waitForExistence(timeout: UITestTimeout.long))

        app.buttons["Reset to built-in"].tap()
        XCTAssertTrue(app.staticTexts["Using built-in key"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Done"].tap()
        XCTAssertTrue(app.tabBars.buttons["Library"].waitForExistence(timeout: UITestTimeout.long))
    }
}
