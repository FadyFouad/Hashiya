import SwiftUI

// Liquid Glass on iOS 26 and later; the teal styling of iOS 17 and 18 everywhere else.
// Only the surfaces in iOS spec 1 §16.2 use these: chips, banners, and the floating or sheet buttons.

/// Groups nearby glass shapes so they render and blend together (`GlassEffectContainer`); a plain wrapper before iOS 26.
public struct HashiyaGlassGroup<Content: View>: View {
    private let spacing: CGFloat
    private let content: Content

    public init(spacing: CGFloat, @ViewBuilder content: () -> Content) {
        self.spacing = spacing
        self.content = content()
    }

    // One view, not an `if #available` branch: a conditional body lays out through an implicit stack that places
    // `content` at a fixed width, and inside a horizontal ScrollView that truncates chip text.
    public var body: AnyView {
        if #available(iOS 26, *) {
            return AnyView(GlassEffectContainer(spacing: spacing) { content })
        } else {
            return AnyView(content)
        }
    }
}

public extension View {
    /// A filter chip's surface and text colour. iOS 26: an interactive glass capsule, tinted with the primary
    /// colour when selected. Before: outlined, or filled with the primary container when selected.
    /// Selection is never shown by colour alone: callers add a check and `.isSelected`.
    func hashiyaChip(isSelected: Bool) -> some View {
        modifier(ChipSurface(isSelected: isSelected))
    }

    /// The primary button of a surface (Save to library, Add paper): `.glassProminent` on iOS 26, `.borderedProminent`
    /// before; tinted with the primary colour.
    func hashiyaProminentButton() -> some View {
        modifier(ProminentButton())
    }

    /// A secondary button beside a prominent one (Open DOI): `.glass` on iOS 26, `.bordered` before; primary tint.
    func hashiyaSecondaryButton() -> some View {
        modifier(SecondaryButton())
    }
}

private struct ChipSurface: ViewModifier {
    let isSelected: Bool

    /// The pre-iOS 26 chip shape.
    private static let shape = RoundedRectangle(cornerRadius: 8)

    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content
                .foregroundStyle(isSelected ? HashiyaColors.onPrimary : HashiyaColors.onSurface)
                .glassEffect(isSelected ? .regular.tint(HashiyaColors.primary).interactive() : .regular.interactive(), in: .capsule)
                .contentShape(.capsule)
        } else {
            content
                .foregroundStyle(isSelected ? HashiyaColors.onPrimaryContainer : HashiyaColors.onSurface)
                .background(Self.shape.fill(isSelected ? HashiyaColors.primaryContainer : Color.clear))
                .overlay(Self.shape.strokeBorder(isSelected ? Color.clear : HashiyaColors.outline, lineWidth: 1))
                .contentShape(Self.shape)
        }
    }
}

private struct ProminentButton: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content.buttonStyle(.glassProminent).tint(HashiyaColors.primary)
        } else {
            content.buttonStyle(.borderedProminent).tint(HashiyaColors.primary)
        }
    }
}

private struct SecondaryButton: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content.buttonStyle(.glass).tint(HashiyaColors.primary)
        } else {
            content.buttonStyle(.bordered).tint(HashiyaColors.primary)
        }
    }
}

public extension View {
    /// A bar under the navigation bar (the filter chips). iOS 26: `safeAreaInset` with no background. Not
    /// `safeAreaBar`: iOS 26 hosts the large title inside the list, and a `safeAreaBar` extends the list's top scroll
    /// edge effect over it, which washes the title out. Before: `safeAreaInset` on the surface colour.
    func hashiyaTopBar<Bar: View>(@ViewBuilder _ bar: () -> Bar) -> some View {
        modifier(TopBar(bar: bar()))
    }
}

private struct TopBar<Bar: View>: ViewModifier {
    let bar: Bar

    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content.safeAreaInset(edge: .top, spacing: 0) { bar }
        } else {
            content.safeAreaInset(edge: .top, spacing: 0) { bar.background(HashiyaColors.surface) }
        }
    }
}
