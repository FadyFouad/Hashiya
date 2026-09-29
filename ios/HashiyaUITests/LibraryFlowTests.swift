import XCTest

/// End to end with `-ui-testing`: in-memory library, stub search, no network.
final class LibraryFlowTests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-ui-testing", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
    }

    @MainActor
    func testSaveFromSearchThenRemoveAndUndoInLibrary() {
        XCTAssertTrue(app.staticTexts["No saved papers yet"].waitForExistence(timeout: 10))
        app.buttons["Go to Search"].tap()

        let field = app.searchFields["Search, or paste a DOI, arXiv ID or link"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText("attention\n")
        XCTAssertTrue(app.staticTexts["About 3 results"].waitForExistence(timeout: 5))
        app.buttons["Save"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["In library"].waitForExistence(timeout: 5))

        app.tabBars.buttons["Library"].tap()
        let row = app.cells.containing(NSPredicate(format: "label BEGINSWITH %@", "Attention Is All You Need")).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["1 paper"].exists)

        row.swipeLeft()
        app.buttons["Remove"].tap()
        XCTAssertTrue(app.staticTexts["Removed from library"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["No saved papers yet"].waitForExistence(timeout: 5))

        app.buttons["Undo"].tap()
        XCTAssertTrue(row.waitForExistence(timeout: 5))
    }

    @MainActor
    func testPastingAnArxivIDShowsThePaperToSave() {
        app.tabBars.buttons["Search"].tap()
        let field = app.searchFields["Search, or paste a DOI, arXiv ID or link"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText("arXiv:1706.03762\n")

        XCTAssertTrue(app.staticTexts["Attention Is All You Need"].waitForExistence(timeout: 5))
        app.buttons["Save to library"].tap()
        XCTAssertTrue(app.buttons["Remove from library"].waitForExistence(timeout: 5))

        app.tabBars.buttons["Library"].tap()
        XCTAssertTrue(app.staticTexts["1 paper"].waitForExistence(timeout: 5))
    }

    @MainActor
    func testSettingsSavesAndResetsTheUserKey() {
        app.buttons["Settings"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Using built-in key"].waitForExistence(timeout: 5))

        let field = app.secureTextFields["API key"]
        field.tap()
        field.typeText("my-key")
        app.buttons["Save"].tap()
        XCTAssertTrue(app.staticTexts["Using your key"].waitForExistence(timeout: 5))

        app.buttons["Reset to built-in"].tap()
        XCTAssertTrue(app.staticTexts["Using built-in key"].waitForExistence(timeout: 5))
        app.buttons["Done"].tap()
        XCTAssertTrue(app.tabBars.buttons["Library"].waitForExistence(timeout: 5))
    }
}
