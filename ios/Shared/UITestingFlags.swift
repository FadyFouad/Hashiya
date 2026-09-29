#if DEBUG
import Foundation

/// Debug-only switches the UI tests pass from the app to the Share Extension through the App Group.
/// Compiled into both targets; Release builds contain none of it.
enum UITestingFlags {
    /// The app sets it at each launch: true with `-ui-testing`, false otherwise.
    static let stubsKey = "uiTestingStubs"
    /// The library file the app and the extension use while stubbed, so UI tests never touch the real library.
    static let databaseFileName = "hashiya-ui-testing.sqlite"

    private static var defaults: UserDefaults? { UserDefaults(suiteName: "group.com.etatech.hashiya") }

    static var stubsEnabled: Bool {
        get { defaults?.bool(forKey: stubsKey) ?? false }
        set { defaults?.set(newValue, forKey: stubsKey) }
    }
}
#endif
