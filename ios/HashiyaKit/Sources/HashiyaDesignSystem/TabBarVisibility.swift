import SwiftUI

public extension View {
    /// On a pushed screen (Details, the reader): hides the tab bar where the screen needs the room, on iPhone, as
    /// before; keeps it in an iPad window, where both size classes are regular, so the app's navigation stays in reach
    /// (HIG: keep the tab bar visible). A large iPhone in landscape has a regular width but a compact height, so it
    /// still hides the bar.
    func hidesTabBarWhenCompact() -> some View {
        modifier(HidesTabBarWhenCompact())
    }
}

private struct HidesTabBarWhenCompact: ViewModifier {
    @Environment(\.horizontalSizeClass) private var horizontal
    @Environment(\.verticalSizeClass) private var vertical

    func body(content: Content) -> some View {
        content.toolbar(horizontal == .regular && vertical == .regular ? .automatic : .hidden, for: .tabBar)
    }
}
