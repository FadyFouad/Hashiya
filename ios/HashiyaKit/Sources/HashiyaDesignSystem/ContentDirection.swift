import SwiftUI

/// The writing direction of paper content (titles, authors, venues, abstracts), independent of the UI language.
public enum ContentDirection {
    /// Right-to-left when the first strong character is Arabic or Hebrew, left-to-right when it is any
    /// other letter, nil when there is no letter (use the UI direction).
    public static func of(_ text: String) -> LayoutDirection? {
        for scalar in text.unicodeScalars {
            if isRightToLeft(scalar.value) { return .rightToLeft }
            if scalar.properties.isAlphabetic { return .leftToRight }
        }
        return nil
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
        Text(verbatim: text)
            .font(.hashiya(style))
            .foregroundStyle(color)
            .lineLimit(lineLimit)
            .multilineTextAlignment(.leading)
            .frame(maxWidth: .infinity, alignment: .leading)
            .environment(\.layoutDirection, ContentDirection.of(text) ?? uiDirection)
    }
}
