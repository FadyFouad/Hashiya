import SwiftUI

/// The widest things get on large windows (iPad), as on Android: a column of content (Details, Settings) 840pt, a
/// button or text field 480pt. Narrower than that on every iPhone, so phones are unchanged.
public enum ContentWidth {
    public static let column: CGFloat = 840
    public static let control: CGFloat = 480
}

public extension View {
    /// Up to `width` wide, centered in what is left over, once the window is wide (`LayoutClass.expanded` and up).
    /// Narrower windows (every iPhone) get the view untouched, so their layout, down to the pixel, is as before.
    func centeredMaxWidth(_ width: CGFloat = ContentWidth.column) -> some View {
        modifier(CenteredMaxWidth(width: width, centers: true))
    }

    /// Up to `width` wide once the window is wide; untouched otherwise. For controls inside a column.
    func maxWidthWhenWide(_ width: CGFloat = ContentWidth.control) -> some View {
        modifier(CenteredMaxWidth(width: width, centers: false))
    }
}

private struct CenteredMaxWidth: ViewModifier {
    let width: CGFloat
    let centers: Bool
    @Environment(\.layoutClass) private var layoutClass

    func body(content: Content) -> some View {
        if layoutClass >= .expanded {
            if centers {
                content.frame(maxWidth: width).frame(maxWidth: .infinity)
            } else {
                content.frame(maxWidth: width)
            }
        } else {
            content
        }
    }
}
