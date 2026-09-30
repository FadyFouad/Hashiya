import HashiyaTesting
import SwiftUI
import Testing
import UIKit

/// The snapshot helper's folder choice, and proof that the hosted renderer captures Liquid Glass.
@MainActor
struct SnapshotHostTests {
    @Test(arguments: [
        ("18.5", "iOS18"),
        ("18.2", "iOS18"),
        ("26.2", "iOS26"),
        ("26.4.1", "iOS26"),
        ("26", "iOS26"),
    ])
    func supportedVersionsMapToTheirMajorFolder(version: String, folder: String) {
        #expect(SnapshotOS.folder(systemVersion: version) == folder)
    }

    @Test(arguments: ["17.5", "27.0", "", "x.1"])
    func otherVersionsHaveNoFolder(version: String) {
        #expect(SnapshotOS.folder(systemVersion: version) == nil)
    }

    /// A layer render (the helper's old renderer) leaves glass and everything behind it blank; the key-window
    /// render must show the red background around the capsule and the glass over it.
    @Test func theKeyWindowRenderShowsTheBackgroundAroundGlass() throws {
        let keyWindow = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
        let window = try #require(keyWindow, "the app's key window (is the bundle hosted by Hashiya?)")
        let host = UIHostingController(rootView: ZStack {
            Color.red
            if #available(iOS 26, *) {
                Color.clear.frame(width: 200, height: 60).glassEffect(.regular, in: .capsule)
            }
        })
        // UIHostingController defaults to reserving the window's safe area for its content, which would leave
        // this corner sample point (inside the home-indicator inset) unpainted; the app's own root view doesn't
        // have this problem because SwiftUI's WindowGroup already ignores safe area for its scene content.
        host.safeAreaRegions = []
        let previousRoot = window.rootViewController
        window.rootViewController = host
        defer { window.rootViewController = previousRoot }
        host.view.frame = window.bounds
        host.view.layoutIfNeeded()

        let image = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
            _ = window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        }
        let corner = try #require(image.pixel(atX: 10, y: Int(window.bounds.height) - 10))
        #expect(corner.red > 0.8 && corner.green < 0.3 && corner.blue < 0.3, "background: \(corner)")
    }
}

private extension UIImage {
    /// The colour at a point, in points.
    func pixel(atX x: Int, y: Int) -> (red: CGFloat, green: CGFloat, blue: CGFloat)? {
        guard let cgImage else { return nil }
        let px = Int(CGFloat(x) * scale), py = Int(CGFloat(y) * scale)
        var data = [UInt8](repeating: 0, count: 4)
        guard let context = CGContext(
            data: &data, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return nil }
        context.draw(cgImage, in: CGRect(x: -px, y: py - cgImage.height + 1, width: cgImage.width, height: cgImage.height))
        return (CGFloat(data[0]) / 255, CGFloat(data[1]) / 255, CGFloat(data[2]) / 255)
    }
}
