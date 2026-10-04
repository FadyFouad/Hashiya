import SwiftUI

/// The app's width breakpoints, the same as Android's (600 / 840 / 1200pt), read from the space the window actually has,
/// never from the device: an iPad can run the app in a phone-sized window, and an iPhone in a wide one.
///
/// The horizontal size class decides structure (a split view or a stack, the sidebar); `LayoutClass` decides finer
/// things, such as how wide a column of text may grow.
public enum LayoutClass: Int, Comparable, Sendable {
    case compact, medium, expanded, large

    public init(width: CGFloat) {
        switch width {
        case ..<600: self = .compact
        case ..<840: self = .medium
        case ..<1200: self = .expanded
        default: self = .large
        }
    }

    public static func < (lhs: LayoutClass, rhs: LayoutClass) -> Bool { lhs.rawValue < rhs.rawValue }
}

public extension EnvironmentValues {
    /// The window's `LayoutClass`, set once at the root by `measuresLayoutClass()`.
    @Entry var layoutClass: LayoutClass = .compact
}

public extension View {
    /// Measures this view's width (the window, at the root) and gives the subtree its `LayoutClass`; it updates as the
    /// window resizes, rotates or moves between split sizes.
    func measuresLayoutClass() -> some View {
        modifier(MeasuresLayoutClass())
    }
}

private struct MeasuresLayoutClass: ViewModifier {
    @State private var layoutClass = LayoutClass.compact

    func body(content: Content) -> some View {
        content
            .environment(\.layoutClass, layoutClass)
            .onGeometryChange(for: LayoutClass.self) { LayoutClass(width: $0.size.width) } action: { layoutClass = $0 }
    }
}
