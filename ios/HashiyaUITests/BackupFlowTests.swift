import XCTest

/// Export's counts, end to end with `-ui-testing`. The system file pickers aren't driven.
@MainActor
final class BackupFlowTests: XCTestCase {
    func testExportShowsTheLibraryCounts() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-ui-testing", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()

        // Save one paper, the way LibraryFlowTests does.
        XCTAssertTrue(app.staticTexts["No saved papers yet"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Go to Search"].tap()
        let field = app.searchFields["Search, or paste a DOI, arXiv ID or link"]
        XCTAssertTrue(field.waitForExistence(timeout: UITestTimeout.long))
        field.tap()
        field.typeText("attention\n")
        XCTAssertTrue(app.staticTexts["About 3 results"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Save"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["In library"].waitForExistence(timeout: UITestTimeout.long))

        app.tab("Library").tap()
        let settings = app.buttons["Settings"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.staticTexts["1 paper"].waitForExistence(timeout: UITestTimeout.long))
        settings.tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: UITestTimeout.long))
        let export = app.buttons["settings.exportLibrary"]
        // The Backup section is below the fold on a phone.
        for _ in 0..<5 where !export.isHittable { app.swipeUp() }
        XCTAssertTrue(export.waitForExistence(timeout: UITestTimeout.long))
        // The row is disabled until the library's counts have loaded.
        let enabled = expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: export)
        wait(for: [enabled], timeout: UITestTimeout.long)
        export.tap()
        XCTAssertTrue(app.staticTexts["1 paper · 0 collections"].waitForExistence(timeout: UITestTimeout.long))
    }
}
