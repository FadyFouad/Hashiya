import XCTest

final class PrivacyFlowTests: XCTestCase {
    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["-ui-testing", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        return app
    }

    private func analyticsSwitch(in app: XCUIApplication) -> XCUIElement {
        app.buttons["Settings"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: UITestTimeout.long))
        let toggle = app.switches["settings.analytics"]
        for _ in 0..<6 where !toggle.isHittable { app.swipeUp() }
        return toggle
    }

    func testTurningUsageStatisticsOffSurvivesARelaunch() {
        var app = launch()
        var toggle = analyticsSwitch(in: app)
        if (toggle.value as? String) == "0" { toggle.switches.firstMatch.tap() }   // start from on
        toggle.switches.firstMatch.tap()
        XCTAssertEqual(toggle.value as? String, "0")
        app.terminate()

        app = launch()
        toggle = analyticsSwitch(in: app)
        XCTAssertEqual(toggle.value as? String, "0")
        toggle.switches.firstMatch.tap()   // leave it on for other tests
        XCTAssertEqual(toggle.value as? String, "1")
    }
}
