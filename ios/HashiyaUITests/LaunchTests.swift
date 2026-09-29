import XCTest

@MainActor
final class LaunchTests: XCTestCase {
    @MainActor
    func testLaunchShowsLibraryAndSearchTabs() {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()

        XCTAssertTrue(app.tabBars.buttons["Library"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.tabBars.buttons["Search"].exists)
        XCTAssertTrue(app.tabBars.buttons["Library"].isSelected)
    }
}
