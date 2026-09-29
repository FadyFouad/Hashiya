import HashiyaDesignSystem
import Testing
import UIKit

@MainActor
struct ThemeTests {
    private func hex(_ color: UIColor, _ style: UIUserInterfaceStyle) -> String {
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        color.resolvedColor(with: UITraitCollection(userInterfaceStyle: style)).getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return String(format: "#%02X%02X%02X", Int((red * 255).rounded()), Int((green * 255).rounded()), Int((blue * 255).rounded()))
    }

    @Test(arguments: [
        (HashiyaPalette.primary, "#0B6E6E", "#7FD4D2"),
        (HashiyaPalette.onPrimary, "#FFFFFF", "#003737"),
        (HashiyaPalette.primaryContainer, "#D7ECEA", "#004F4F"),
        (HashiyaPalette.onPrimaryContainer, "#002020", "#9CF1EE"),
        (HashiyaPalette.secondaryContainer, "#E0F2EF", "#1F3F3D"),
        (HashiyaPalette.onSecondaryContainer, "#0B3B3A", "#CCE8E6"),
        (HashiyaPalette.surface, "#FFFFFF", "#0E1417"),
        (HashiyaPalette.onSurface, "#0F1720", "#DEE3E6"),
        (HashiyaPalette.onSurfaceVariant, "#5B6770", "#BEC8CC"),
        (HashiyaPalette.outline, "#D5DBDF", "#3A4448"),
        (HashiyaPalette.outlineVariant, "#E3E8EB", "#2A3236"),
        (HashiyaPalette.surfaceContainerHigh, "#EBEEF0", "#242B2E"),
        (HashiyaPalette.surfaceContainerHighest, "#E3E8EB", "#2F3639"),
        (HashiyaPalette.error, "#BA1A1A", "#FFB4AB"),
        (HashiyaPalette.errorContainer, "#FFDAD6", "#93000A"),
        (HashiyaPalette.onErrorContainer, "#410002", "#FFDAD6"),
        (HashiyaPalette.inversePrimary, "#7FD4D2", "#0B6E6E"),
    ])
    func colorsMatchTheAndroidTheme(color: UIColor, light: String, dark: String) {
        #expect(hex(color, .light) == light)
        #expect(hex(color, .dark) == dark)
    }

    @Test func bundledFontsRegister() {
        HashiyaFonts.register()
        for weight in [HashiyaFonts.Weight.regular, .medium, .semiBold] {
            for arabic in [false, true] {
                let name = HashiyaFonts.postScriptName(weight, arabic: arabic)
                #expect(UIFont(name: name, size: 14) != nil, "\(name) is not registered")
            }
        }
    }
}
