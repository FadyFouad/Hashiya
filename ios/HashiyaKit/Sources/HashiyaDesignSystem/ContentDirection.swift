import SwiftUI

/// The writing direction of paper content (titles, authors, venues, abstracts), independent of the UI language.
public enum ContentDirection {
    /// Right-to-left when the first strong character is Arabic or Hebrew, left-to-right when it is any
    /// other letter, nil when there is no letter (use the UI direction).
    ///
    /// Directional isolates (U+2066–U+2069) are looked through on purpose: Arabic formats wrap their
    /// arguments in FSI…PDI (the first author in "library.etAl"), and the paper's own text decides its
    /// direction. The Unicode bidi algorithm skips isolates when it picks a paragraph direction (rule P2),
    /// so shown text goes through `paragraph(_:uiDirection:)`, which makes the two agree.
    public static func of(_ text: String) -> LayoutDirection? {
        for scalar in text.unicodeScalars {
            if isRightToLeft(scalar.value) { return .rightToLeft }
            if scalar.properties.isAlphabetic { return .leftToRight }
        }
        return nil
    }

    /// The direction to lay `text` out in (its own, else the UI's) and the text to show: `text` prefixed
    /// with U+200E (LRM) or U+200F (RLM), so the bidi paragraph direction is that same direction.
    public static func paragraph(_ text: String, uiDirection: LayoutDirection) -> (text: String, direction: LayoutDirection) {
        let direction = of(text) ?? uiDirection
        let mark = direction == .rightToLeft ? "\u{200F}" : "\u{200E}"
        return (mark + text, direction)
    }

    private static func isRightToLeft(_ value: UInt32) -> Bool {
        (0x0590...0x08FF).contains(value) || (0xFB1D...0xFDFF).contains(value) || (0xFE70...0xFEFF).contains(value)
    }
}

/// Paper text laid out in its own direction: full width, aligned to its own leading edge.
public struct PaperText: View {
    private let text: String
    private let style: HashiyaTextStyle
    private let color: Color
    private let lineLimit: Int?

    @Environment(\.layoutDirection) private var uiDirection

    public init(_ text: String, style: HashiyaTextStyle, color: Color = HashiyaColors.onSurface, lineLimit: Int? = nil) {
        self.text = text
        self.style = style
        self.color = color
        self.lineLimit = lineLimit
    }

    public var body: some View {
        let paragraph = ContentDirection.paragraph(text, uiDirection: uiDirection)
        Text(verbatim: paragraph.text)
            .font(.hashiya(style))
            .foregroundStyle(color)
            .lineLimit(lineLimit)
            .multilineTextAlignment(.leading)
            .frame(maxWidth: .infinity, alignment: .leading)
            .environment(\.layoutDirection, paragraph.direction)
            .accessibilityLabel(Text(verbatim: text))
    }
}
