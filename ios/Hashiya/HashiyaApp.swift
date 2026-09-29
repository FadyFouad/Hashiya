import HashiyaDesignSystem
import SwiftUI

@main
struct HashiyaApp: App {
    @State private var container: AppContainer

    init() {
        HashiyaFonts.register()
        HashiyaFonts.applyNavigationBarFonts()
        _container = State(initialValue: AppContainer.make())
    }

    var body: some Scene {
        WindowGroup {
            RootView(container: container)
        }
    }
}
