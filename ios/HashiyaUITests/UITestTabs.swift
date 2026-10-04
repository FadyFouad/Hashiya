import XCTest

extension XCUIApplication {
    /// A tab: in the tab bar at the bottom on iPhone; on iPad the tabs sit at the top of the window, where XCUITest
    /// sees them as plain buttons named after their symbols (so the keyboard's Search key is never taken for one).
    func tab(_ name: String) -> XCUIElement {
        guard UITestDevice.isPad else { return tabBars.buttons[name] }
        let symbols = ["books.vertical", "magnifyingglass"]
        return buttons.matching(NSPredicate(format: "label == %@ AND identifier IN %@", name, symbols)).firstMatch
    }
}

enum UITestDevice {
    /// The tests' simulator is an iPad. Only for what the tests look for: the app itself lays out by window size.
    static var isPad: Bool { UIDevice.current.userInterfaceIdiom == .pad }
}
