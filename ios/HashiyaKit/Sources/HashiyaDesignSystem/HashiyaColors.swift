import SwiftUI
import UIKit

/// The palette, from the Android theme. Each color resolves to its light or dark value from the trait collection.
public enum HashiyaColors {
    public static let primary = Color(uiColor: HashiyaPalette.primary)
    public static let onPrimary = Color(uiColor: HashiyaPalette.onPrimary)
    public static let primaryContainer = Color(uiColor: HashiyaPalette.primaryContainer)
    public static let onPrimaryContainer = Color(uiColor: HashiyaPalette.onPrimaryContainer)
    public static let secondaryContainer = Color(uiColor: HashiyaPalette.secondaryContainer)
    public static let onSecondaryContainer = Color(uiColor: HashiyaPalette.onSecondaryContainer)
    public static let surface = Color(uiColor: HashiyaPalette.surface)
    public static let onSurface = Color(uiColor: HashiyaPalette.onSurface)
    public static let onSurfaceVariant = Color(uiColor: HashiyaPalette.onSurfaceVariant)
    public static let outline = Color(uiColor: HashiyaPalette.outline)
    public static let outlineVariant = Color(uiColor: HashiyaPalette.outlineVariant)
    public static let surfaceContainer = Color(uiColor: HashiyaPalette.surfaceContainer)
    public static let surfaceContainerHigh = Color(uiColor: HashiyaPalette.surfaceContainerHigh)
    public static let surfaceContainerHighest = Color(uiColor: HashiyaPalette.surfaceContainerHighest)
    public static let error = Color(uiColor: HashiyaPalette.error)
    public static let errorContainer = Color(uiColor: HashiyaPalette.errorContainer)
    public static let onErrorContainer = Color(uiColor: HashiyaPalette.onErrorContainer)
    /// Primary with light and dark swapped: actions on the inverted banner.
    public static let inversePrimary = Color(uiColor: HashiyaPalette.inversePrimary)
}

/// The same palette as dynamic `UIColor`s, for UIKit appearance APIs and tests.
public enum HashiyaPalette {
    public static let primary = dynamic(light: 0x0B6E6E, dark: 0x7FD4D2)
    public static let onPrimary = dynamic(light: 0xFFFFFF, dark: 0x003737)
    public static let primaryContainer = dynamic(light: 0xD7ECEA, dark: 0x004F4F)
    public static let onPrimaryContainer = dynamic(light: 0x002020, dark: 0x9CF1EE)
    public static let secondaryContainer = dynamic(light: 0xE0F2EF, dark: 0x1F3F3D)
    public static let onSecondaryContainer = dynamic(light: 0x0B3B3A, dark: 0xCCE8E6)
    public static let surface = dynamic(light: 0xFFFFFF, dark: 0x0E1417)
    public static let onSurface = dynamic(light: 0x0F1720, dark: 0xDEE3E6)
    public static let onSurfaceVariant = dynamic(light: 0x5B6770, dark: 0xBEC8CC)
    public static let outline = dynamic(light: 0xD5DBDF, dark: 0x3A4448)
    public static let outlineVariant = dynamic(light: 0xE3E8EB, dark: 0x2A3236)
    public static let surfaceContainer = dynamic(light: 0xF1F4F5, dark: 0x1A2124)
    public static let surfaceContainerHigh = dynamic(light: 0xEBEEF0, dark: 0x242B2E)
    public static let surfaceContainerHighest = dynamic(light: 0xE3E8EB, dark: 0x2F3639)
    public static let error = dynamic(light: 0xBA1A1A, dark: 0xFFB4AB)
    public static let errorContainer = dynamic(light: 0xFFDAD6, dark: 0x93000A)
    public static let onErrorContainer = dynamic(light: 0x410002, dark: 0xFFDAD6)
    public static let inversePrimary = dynamic(light: 0x7FD4D2, dark: 0x0B6E6E)

    private static func dynamic(light: UInt32, dark: UInt32) -> UIColor {
        UIColor { traits in rgb(traits.userInterfaceStyle == .dark ? dark : light) }
    }

    private static func rgb(_ hex: UInt32) -> UIColor {
        UIColor(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
