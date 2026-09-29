import Foundation
import HashiyaDesignSystem
import SnapshotTesting
import SwiftUI
import Testing
import UIKit

/// The baseline folder for an iOS version: `iOS18` or `iOS26`. Any other major version has no baselines and
/// gives nil, so a run on it records nothing.
public enum SnapshotOS {
    public static let supportedMajorVersions = [18, 26]

    public static func folder(systemVersion: String) -> String? {
        guard let major = systemVersion.split(separator: ".").first.flatMap({ Int($0) }),
              supportedMajorVersions.contains(major) else { return nil }
        return "iOS\(major)"
    }
}

/// Snapshots `view` four times — English and Arabic, light and dark — as `<state>-EnglishLight`,
/// `-EnglishDark`, `-ArabicLight` and `-ArabicDark` on an iPhone 13-sized screen, into
/// `__Snapshots__/<SnapshotOS folder>/<test file name>/` next to the test file.
///
/// It draws the app's key window (`drawHierarchyInKeyWindow`), because a layer render leaves Liquid Glass
/// and everything behind it blank; so it only works in a test bundle hosted by the app (`HashiyaSnapshotTests`).
/// On an iOS version other than 18 or 26 it records an issue and draws nothing.
///
/// Each Arabic image must have rendered `arabicText` (a string from the app's Arabic catalogs, not
/// paper content, as the target's `L10n` returns it), so a silently English render fails. With `SNAPSHOT_RECORD=1` in the environment every
/// image is re-recorded (and the test fails, as recording always does); otherwise only missing
/// images are recorded.
@MainActor
public func assertHashiyaSnapshots(
    of view: some View,
    named state: String,
    arabicText: String,
    fileID: StaticString = #fileID,
    file: StaticString = #filePath,
    testName: String = #function,
    line: UInt = #line,
    column: UInt = #column
) {
    let sourceLocation = SourceLocation(
        fileID: String(describing: fileID),
        filePath: String(describing: file),
        line: Int(line),
        column: Int(column)
    )
    guard let folder = SnapshotOS.folder(systemVersion: UIDevice.current.systemVersion) else {
        Issue.record(
            "Snapshots have baselines for iOS 18 and iOS 26 only; this simulator runs iOS \(UIDevice.current.systemVersion)",
            sourceLocation: sourceLocation
        )
        return
    }
    let testFile = URL(fileURLWithPath: String(describing: file))
    let directory = testFile.deletingLastPathComponent()
        .appendingPathComponent("__Snapshots__")
        .appendingPathComponent(folder)
        .appendingPathComponent(testFile.deletingPathExtension().lastPathComponent)
    // Glass re-renders with faint noise (measured: none above 30/255, under 1 % of pixels), so iOS 26 allows a
    // little; iOS 18 keeps the strict comparison that proves its screens never change.
    let (precision, perceptualPrecision): (Float, Float) = folder == "iOS26" ? (0.98, 0.95) : (1, 0.98)
    HashiyaFonts.register()
    let record: SnapshotTestingConfiguration.Record =
        ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1" ? .all : .missing

    for language in ["en", "ar"] {
        for style in [UIUserInterfaceStyle.light, .dark] {
            // Restore what was there: rendering spins the run loop, so other tests' work can run inside it.
            let previousLanguage = HashiyaLanguage.override
            let previousLookups = HashiyaStrings.recordedLookups
            HashiyaLanguage.override = language
            HashiyaStrings.recordedLookups = []
            HashiyaFonts.applyNavigationBarFonts()
            defer {
                HashiyaLanguage.override = previousLanguage
                HashiyaStrings.recordedLookups = previousLookups
            }

            let direction: LayoutDirection = language == "ar" ? .rightToLeft : .leftToRight
            let host = UIHostingController(
                rootView: view
                    .tint(HashiyaColors.primary)
                    .environment(\.locale, Locale(identifier: language))
                    .environment(\.layoutDirection, direction)
                    .environment(\.colorScheme, style == .dark ? .dark : .light)
            )
            host.overrideUserInterfaceStyle = style
            let traits = UITraitCollection { traits in
                traits.userInterfaceStyle = style
                traits.layoutDirection = language == "ar" ? .rightToLeft : .leftToRight
                traits.preferredContentSizeCategory = .large
                traits.displayGamut = .SRGB
            }
            let variant = (language == "ar" ? "Arabic" : "English") + (style == .dark ? "Dark" : "Light")

            let failure = verifySnapshot(
                of: host,
                as: .image(
                    on: .iPhone13,
                    drawHierarchyInKeyWindow: true,
                    precision: precision,
                    perceptualPrecision: perceptualPrecision,
                    traits: traits
                ),
                named: "\(state)-\(variant)",
                record: record,
                snapshotDirectory: directory.path,
                fileID: fileID,
                file: file,
                testName: testName,
                line: line,
                column: column
            )
            if let failure {
                Issue.record(Comment(rawValue: failure), sourceLocation: sourceLocation)
            }

            if language == "ar" {
                let rendered = HashiyaStrings.recordedLookups ?? []
                #expect(
                    rendered.contains(arabicText),
                    "\(state)-\(variant) did not render \"\(arabicText)\"; it rendered \(rendered)",
                    sourceLocation: sourceLocation
                )
            }
        }
    }
}
