import XCTest

/// Sharing a link to the Share Extension from a real share sheet. The app presents the sheet for the URL
/// (`-ui-testing-share`); the Debug extension sees the App Group flag, uses the stub lookup and saves into the
/// UI tests' library file, which the app shows.
@MainActor
final class ShareFlowTests: XCTestCase {
    @MainActor
    func testSharingAnArxivLinkSavesThePaperToTheLibrary() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += [
            "-ui-testing", "-ui-testing-share", "https://arxiv.org/abs/1706.03762",
            "-AppleLanguages", "(en)", "-AppleLocale", "en_US",
        ]
        app.launch()

        let hashiya = app.cells["Hashiya"]
        XCTAssertTrue(hashiya.waitForExistence(timeout: 15))
        hashiya.tap()

        XCTAssertTrue(app.staticTexts["Attention Is All You Need"].waitForExistence(timeout: 10))
        app.buttons["Save to library"].tap()
        XCTAssertTrue(app.buttons["Remove from library"].waitForExistence(timeout: 5))
        app.buttons["Done"].tap()

        app.tabBars.buttons["Library"].tap()
        XCTAssertTrue(app.staticTexts["1 paper"].waitForExistence(timeout: 10))
        let row = app.cells.containing(NSPredicate(format: "label BEGINSWITH %@", "Attention Is All You Need")).firstMatch
        XCTAssertTrue(row.exists)
    }
}
