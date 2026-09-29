import Foundation
import HashiyaDesignSystem
import SnapshotTesting
import SwiftUI
import Testing
import UIKit

/// Snapshots `view` four times — English and Arabic, light and dark — as `<state>-EnglishLight`,
/// `-EnglishDark`, `-ArabicLight` and `-ArabicDark` on an iPhone 13-sized screen.
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
            }
            let variant = (language == "ar" ? "Arabic" : "English") + (style == .dark ? "Dark" : "Light")

            assertSnapshot(
                of: host,
                as: .image(on: .iPhone13, perceptualPrecision: 0.98, traits: traits),
                named: "\(state)-\(variant)",
                record: record,
                fileID: fileID,
                file: file,
                testName: testName,
                line: line,
                column: column
            )

            if language == "ar" {
                let rendered = HashiyaStrings.recordedLookups ?? []
                #expect(
                    rendered.contains(arabicText),
                    "\(state)-\(variant) did not render \"\(arabicText)\"; it rendered \(rendered)",
                    sourceLocation: SourceLocation(
                        fileID: String(describing: fileID),
                        filePath: String(describing: file),
                        line: Int(line),
                        column: Int(column)
                    )
                )
            }
        }
    }
}
