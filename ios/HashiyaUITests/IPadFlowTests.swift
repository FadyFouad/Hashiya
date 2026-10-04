import XCTest

/// iPad end to end with `-ui-testing`: list and detail side by side, a window resized narrow and wide again, the menu
/// bar's shortcuts and the context menus, and a paper in its own window. Skipped on iPhone.
@MainActor
final class IPadFlowTests: XCTestCase {
    private let searchField = "Search, or paste a DOI, arXiv ID or link"

    private func launchApp() throws -> XCUIApplication {
        try XCTSkipUnless(UITestDevice.isPad, "iPad layouts")
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-ui-testing", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        XCTAssertTrue(app.staticTexts["No saved papers yet"].waitForExistence(timeout: UITestTimeout.long))
        return app
    }

    private func saveAttention(in app: XCUIApplication) {
        app.tab("Search").tap()
        let field = app.searchFields[searchField]
        XCTAssertTrue(field.waitForExistence(timeout: UITestTimeout.long))
        field.tap()
        field.typeText("attention\n")
        XCTAssertTrue(app.staticTexts["About 3 results"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Save"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["In library"].firstMatch.waitForExistence(timeout: UITestTimeout.long))
    }

    private func attentionRow(in app: XCUIApplication) -> XCUIElement {
        app.cells.containing(NSPredicate(format: "label BEGINSWITH %@", "Attention Is All You Need")).firstMatch
    }

    /// Saves Attention, opens it beside the Library's list, downloads its PDF and opens the reader in the pane.
    private func openTheReader(in app: XCUIApplication) -> XCUIElement {
        saveAttention(in: app)
        app.tab("Library").tap()
        let row = attentionRow(in: app)
        XCTAssertTrue(row.waitForExistence(timeout: UITestTimeout.long))
        row.buttons.firstMatch.tap()
        let download = app.buttons["Download PDF"]
        XCTAssertTrue(download.waitForExistence(timeout: UITestTimeout.long), "Details shows beside the list")
        download.tap()
        let read = app.descendants(matching: .any)["details.pdfRead"]
        XCTAssertTrue(read.waitForExistence(timeout: UITestTimeout.long))
        read.tap()
        let back = app.buttons["reader.back"]
        XCTAssertTrue(back.waitForExistence(timeout: UITestTimeout.long), "The reader opens")
        return back
    }

    func testReaderOpensInThePaneWithTheNotesBesideIt() throws {
        let app = try launchApp()
        let back = openTheReader(in: app)

        // An inspector in the pane used to pop the reader a moment after it opened.
        sleep(3)
        XCTAssertTrue(back.exists, "The reader stays open")
        XCTAssertTrue(attentionRow(in: app).isHittable, "The list stays beside the reader")

        app.buttons["reader.notes"].firstMatch.tap()
        XCTAssertTrue(app.descendants(matching: .any)["note.summary"].waitForExistence(timeout: UITestTimeout.long))
        XCTAssertTrue(back.exists, "The notes open beside the PDF, not over the reader")
    }

    func testResizingTheWindowKeepsTheOpenPaper() throws {
        let app = try launchApp()
        let back = openTheReader(in: app)
        let full = app.windows.firstMatch.frame

        // Narrow (the system's smallest width): one stack, with the reader still on top.
        dragWindowCorner(of: app, to: CGVector(dx: full.minX + 420, dy: full.maxY - 70))
        try XCTSkipIf(
            app.windows.firstMatch.frame.width == full.width,
            "The simulator runs full-screen apps; resizing needs windowed apps (Settings > Multitasking)"
        )
        XCTAssertTrue(back.exists, "The reader stays open in a narrow window")

        // Wide again: the list comes back beside the paper, and the reader is still open.
        dragWindowCorner(of: app, to: CGVector(dx: full.maxX, dy: full.maxY - 70))
        XCTAssertTrue(back.exists, "The reader stays open after widening")
        XCTAssertTrue(attentionRow(in: app).isHittable, "The list shows again beside the reader")
    }

    func testKeyboardShortcutsAndContextMenus() throws {
        let app = try launchApp()
        let field = app.searchFields[searchField]

        app.typeKey("2", modifierFlags: .command)
        // The simulator can drop the first synthesized key press after launch; the app never receives it.
        if !field.waitForExistence(timeout: 5) { app.typeKey("2", modifierFlags: .command) }
        XCTAssertTrue(field.waitForExistence(timeout: UITestTimeout.long), "⌘2 shows Search")
        app.typeKey("1", modifierFlags: .command)
        XCTAssertTrue(app.staticTexts["No saved papers yet"].waitForExistence(timeout: UITestTimeout.long), "⌘1 shows the Library")

        app.typeKey("n", modifierFlags: .command)
        let focused = NSPredicate(format: "hasKeyboardFocus == true")
        wait(for: [expectation(for: focused, evaluatedWith: field)], timeout: UITestTimeout.long)
        field.typeText("attention\n")
        XCTAssertTrue(app.staticTexts["About 3 results"].waitForExistence(timeout: UITestTimeout.long))

        // A result's menu: Save, then Open details beside the results.
        let card = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label BEGINSWITH %@", "Attention Is All You Need")).firstMatch
        card.press(forDuration: 1.2)
        app.buttons["Save to library"].tap()
        XCTAssertTrue(app.staticTexts["In library"].firstMatch.waitForExistence(timeout: UITestTimeout.long))
        card.press(forDuration: 1.2)
        XCTAssertTrue(app.buttons["Remove from library"].waitForExistence(timeout: UITestTimeout.long))
        app.buttons["Open details"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Download PDF"].waitForExistence(timeout: UITestTimeout.long))

        // ⌘N again, with the field still active from the search: it's cleared and has the keyboard.
        app.typeKey("n", modifierFlags: .command)
        wait(for: [expectation(for: focused, evaluatedWith: field)], timeout: UITestTimeout.long)
        XCTAssertEqual(field.value as? String, searchField, "⌘N clears the field")

        // A Library row's menu: Open, then Remove with Undo.
        app.typeKey("1", modifierFlags: .command)
        let row = attentionRow(in: app)
        XCTAssertTrue(row.waitForExistence(timeout: UITestTimeout.long))
        row.press(forDuration: 1.2)
        app.buttons["Open"].tap()
        XCTAssertTrue(app.buttons["Download PDF"].waitForExistence(timeout: UITestTimeout.long))
        row.press(forDuration: 1.2)
        app.buttons["Remove"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Removed from library"].waitForExistence(timeout: UITestTimeout.long))

        app.typeKey(",", modifierFlags: .command)
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: UITestTimeout.long), "⌘, opens Settings")
    }

    func testOpenInNewWindowThenSaveAfterTheBackground() throws {
        let app = try launchApp()
        saveAttention(in: app)
        app.tab("Library").tap()
        let row = attentionRow(in: app)
        XCTAssertTrue(row.waitForExistence(timeout: UITestTimeout.long))
        row.press(forDuration: 1.2)
        app.buttons["Open in New Window"].tap()
        XCTAssertTrue(app.buttons["Download PDF"].waitForExistence(timeout: UITestTimeout.long), "The new window shows Details")

        // With two windows, the library database follows the whole app: back from the background, writes land.
        XCUIDevice.shared.press(.home)
        sleep(2)
        app.activate()
        let reading = app.buttons["Reading"].firstMatch
        XCTAssertTrue(reading.waitForExistence(timeout: UITestTimeout.long))
        reading.tap()
        let selected = NSPredicate(format: "isSelected == true")
        wait(for: [expectation(for: selected, evaluatedWith: reading)], timeout: UITestTimeout.long)

        // Removing the paper closes its window.
        app.navigationBars.buttons["More options"].firstMatch.tap()
        app.buttons["Remove from library"].tap()
        let gone = NSPredicate(format: "exists == false")
        wait(for: [expectation(for: gone, evaluatedWith: app.buttons["Download PDF"])], timeout: UITestTimeout.long)
    }

    /// Drags the window's bottom trailing corner (windowed apps) to `point`, in screen points.
    private func dragWindowCorner(of app: XCUIApplication, to point: CGVector) {
        let frame = app.windows.firstMatch.frame
        let screen = XCUIApplication(bundleIdentifier: "com.apple.springboard").coordinate(withNormalizedOffset: .zero)
        screen.withOffset(CGVector(dx: frame.maxX - 4, dy: frame.maxY - 4))
            .press(forDuration: 0.6, thenDragTo: screen.withOffset(point))
        sleep(3)
    }
}
