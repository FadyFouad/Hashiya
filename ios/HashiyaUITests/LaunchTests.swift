import XCTest

@MainActor
final class LaunchTests: XCTestCase {
    @MainActor
    func testLaunchShowsLibraryAndSearchTabs() {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()

        XCTAssertTrue(app.tab("Library").waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(app.tab("Search").exists)
        XCTAssertTrue(app.tab("Library").isSelected)
    }
}
