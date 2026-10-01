import XCTest

extension XCUIApplication {
    /// Taps the banner's Undo and waits for `result`. Right after a row's swipe action, iOS 18 can swallow the next tap
    /// while the list closes its swipe state: on CI the banner stayed up and nothing happened. So an Undo that is still
    /// on screen after a few seconds is tapped once more.
    @MainActor
    func tapUndo(expecting result: XCUIElement) -> Bool {
        let undo = buttons["Undo"]
        undo.tap()
        if result.waitForExistence(timeout: 5) { return true }
        if undo.exists { undo.tap() }
        return result.waitForExistence(timeout: UITestTimeout.long)
    }
}
