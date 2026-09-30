# iOS Liquid Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Load the `liquid-glass:liquid-glass` skill before any task that touches a view.

**Goal:** On iOS 26 and later, give the app's own floating controls Liquid Glass: the filter and status chips, the status badges, the banners (Undo and the others), the preview buttons with a bottom bar the paper scrolls under, and the Add paper button. The share sheet gets the same preview. iOS 17 and 18 keep today's teal styling exactly. The build moves to Xcode 26, snapshots are recorded on both iOS 26 and iOS 18, and the missing iOS 26 large titles are fixed.

**Architecture:** One new design-system file, `Glass.swift`, holds every `#available(iOS 26, *)` branch that screens need: `HashiyaGlassGroup`, `hashiyaChip(isSelected:)`, `hashiyaProminentButton()`, `hashiyaSecondaryButton()` and `hashiyaTopBar`. The banner and the status pill have their own small private surface modifiers. A layer render cannot capture glass, so the five snapshot suites move from the package into a new app-hosted test bundle, `HashiyaSnapshotTests`. There `assertHashiyaSnapshots` draws the key window, and it stores baselines per OS under `__Snapshots__/iOS26/` and `__Snapshots__/iOS18/`. CI moves to Xcode 26.3 on `macos-15` and tests on iPhone 16 with iOS 26.2 and with iOS 18.5.

**Tech Stack:** Swift 6 (language mode 6, strict concurrency), SwiftUI with the iOS 26 SDK (`glassEffect`, `GlassEffectContainer`, `.glass`/`.glassProminent`, `safeAreaBar`), iOS 17+ deployment target, Swift Testing, XCTest (UI tests), swift-snapshot-testing 1.19.6, XcodeGen 2.46.0. Locally: Xcode 27.0 with the iPhone 17 Pro iOS 26.4 and iPhone 16 Pro iOS 18.2 simulators. CI: `macos-15`, Xcode 26.3, iPhone 16 on iOS 26.2 and on iOS 18.5.

**Spec:** docs/superpowers/specs/2026-09-28-ios-foundation-openalex-search-design.md §16 (Liquid Glass), on top of iOS specs 1–3.

## Superseded during verification

Verification found a few places where the plan text below no longer matches what got built. The tasks and commits
are still the record of what happened; treat these as corrections layered on top:

- **Task 1's snapshot helper** additionally sets `traits.displayGamut = .SRGB` and `SnapshotHostTests` sets
  `host.safeAreaRegions = []`, both in `3299755`, to fix a real iOS 26 vs. sRGB-reference colour mismatch found
  during verification (not in the plan text below).
- **Task 3's `HashiyaGlassGroup`** returns `AnyView` on both its iOS 26 and pre-iOS 26 paths, not a plain
  `if #available` conditional body — a conditional body is a dynamic view list that SwiftUI lays out differently
  inside a horizontal `ScrollView`, which truncated the Library filter chips' text (`94cc5a5`).
  See `debug-report.md`, Issue A.
- **Task 7's iOS 26 top bar** is a plain `safeAreaInset(edge: .top)` with no background, not `safeAreaBar` — a
  `safeAreaBar` extends the list's top scroll-edge effect over the large title and washes it out (`11661f9`). Its
  UI test checks the Library screen only, since iOS hides the navigation title while search is active by design on
  both iOS 18 and iOS 26 (`ce5f9f3`).
- **Task 1's snapshot tolerance** is `(precision: 0.999, perceptualPrecision: 0.95)` on iOS 26, not `(0.98, 0.95)`
  — the looser value let the Task 3 chip-truncation regression above pass unnoticed. See spec §16.5 for the
  measurements behind the final values.

## Global Constraints

- Everything in plans 1–3's Global Constraints still applies, except the toolchain lines this plan replaces: iOS 17 minimum; Swift 6 language mode with strict concurrency; XcodeGen (`ios/project.yml` committed, `ios/Hashiya.xcodeproj` generated and git-ignored, `xcodegen generate --spec ios/project.yml` after adding or removing files outside `ios/HashiyaKit`); exact dependency pins; no new package targets; features never import each other, `HashiyaNetwork`, `HashiyaDatabase` or GRDB; strings only from the String Catalogs through `L10n` and `Text(verbatim:)`; snapshot suites `@MainActor @Suite(.serialized)`; baselines only from CI.
- No new strings. No behaviour change. Accessibility labels, traits and identifiers stay exactly as they are (the UI tests find controls by them).
- Code must build with CI's Xcode 26.3 (Swift 6.2, iOS 26.2 SDK) and with Xcode 27.0 locally. Every glass API call sits inside `if #available(iOS 26, *)` (or an `@available(iOS 26, *)` context); the `else` branch is the current code, unchanged.
- Only `Glass.swift`, `HashiyaBanner.swift` and `ReadingStatusBadge.swift` call `glassEffect`. Screens use the `Glass.swift` helpers. System chrome (tab bar, navigation bars, toolbar buttons, `.searchable`, menus, segmented picker, sheets) gets no glass modifiers.
- Tint only with `HashiyaColors.primary`, and only on a selected chip, the Reading badge and prominent buttons.
- iOS 18 must not change: after Task 1 records the iOS 18 images locally, no later task deletes `ios/HashiyaSnapshotTests/__Snapshots__/iOS18/`. Every later iOS 18 run must pass against those images. The one exception is Task 7, which may re-record `iOS18/SearchSnapshotTests` and `iOS18/LibrarySnapshotTests` only if its fallback code changed, and it doesn't.
- Commands run from the worktree root. Local simulators: iOS 26: `-destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.4'`. iOS 18: `-destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2'`. Snapshot suites: `xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya <destination> -collect-test-diagnostics never -only-testing:HashiyaSnapshotTests`. Package tests: `(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package <destination>)`. Pipe every command through `grep -E '(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'`.
- Disk: each full build takes about 1 GB of DerivedData, and this Mac had under 2 GB free on 2026-09-29. Check `df -h /System/Volumes/Data` before a task. Don't pass a custom `-derivedDataPath`, so builds reuse one folder.
- Snapshots are recorded locally only to look at them. Never stage `__Snapshots__`. A task that changes a screen deletes that suite's local `iOS26/<Suite>` folder before its green run: the first run records and fails with `No reference was found on disk`, and the next run passes.
- Git: branch `feat/ios-liquid-glass` in a worktree from `main`: `git worktree add -b feat/ios-liquid-glass ../Hashiya-ios-liquid-glass main`. The main checkout has unrelated changes (`ios/Hashiya/InfoPlist.xcstrings`, `ios/HashiyaShare/InfoPlist.xcstrings`, `.idea/`), and another session may be building there, so never work in it. Every `git add` names explicit paths. Commits use `feat:`/`test:`/`ci:`/`docs:`/`fix:`, with author `Fady <fady.fouad.a@gmail.com>` and no AI or Claude attribution anywhere (commits, PR, comments, docs).

## Review Focus

1. **A glass screen snapshots as a blank or partly blank image and still "passes"** (a layer render drops glass and everything behind it). Pinned by `SnapshotHostTests.theKeyWindowRenderShowsTheBackgroundAroundGlass` (Task 1) and by looking at each task's recorded iOS 26 images against the checklist in its step.
2. **iOS 17/18 users see any change at all.** Pinned by the iOS 18 runs in Tasks 3–7, which verify against the images Task 1 recorded before any glass code existed and must pass without re-recording.
3. **Glass snapshots flake between identical runs** (measured on 2026-09-29: faint differences, none above 30/255, on under 1 % of pixels, in 1–5 of 140 images per run). Pinned by the "three verifications in a row" gate in Tasks 1 and 8, with the iOS 26 tolerance set in Task 1.
4. **The UI tests break on iOS 26 or after the snapshot suites run in the same simulator**: the snapshot suites run inside the app, which shares the UI tests' bundle ID and scene storage. In one mixed run, `testAddPaperOpensSearchReadyForInput` read `large language models` from the search field and `testPastingAnArxivIDShowsThePaperToSave` failed; both passed on reruns. Pinned by CI erasing the simulators between the two steps (Task 2) and by Task 8 running the UI tests on both OS versions.
5. **Large titles on iOS 26 and Arabic RTL glass.** Library and Search with results show no large title on iOS 26 (already true on `main`). Arabic mirrors the glass chips, badges and the Add paper button (bottom left). Pinned by Task 7's UI test and images, and by the Arabic images of Tasks 3–6.

---

## File Structure

```
ios/project.yml                                                    HashiyaSnapshotTests target and scheme entry (Task 1)
ios/Hashiya/HashiyaApp.swift                                       idle when hosting the snapshot tests (Task 1)
ios/HashiyaKit/Package.swift                                       drop the __Snapshots__ excludes (Task 1)
ios/HashiyaKit/Sources/HashiyaTesting/HashiyaSnapshots.swift       SnapshotOS, key-window render, per-OS folders (Task 1)
ios/HashiyaSnapshotTests/SnapshotHostTests.swift                   new (Task 1)
ios/HashiyaSnapshotTests/{DesignSystem,Search,Share,Library,Settings}SnapshotTests.swift
                                                                   moved from ios/HashiyaKit/Tests/*/ (Task 1)
.github/workflows/ios.yml, ios-record-snapshots.yml                Xcode 26.3, two OS, erase between steps (Task 2)
ios/README.md                                                      toolchain and snapshot folders (Task 2)
ios/HashiyaKit/Sources/HashiyaDesignSystem/Glass.swift             new (Task 3; hashiyaTopBar in Task 7)
ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/HashiyaBanner.swift          (Task 3)
ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/PaperPreviewContent.swift    (Task 4)
ios/HashiyaKit/Sources/FeatureSearch/FilterChips.swift                              (Task 5)
ios/HashiyaKit/Sources/FeatureLibrary/LibraryFilterChips.swift, ReadingStatusBadge.swift, LibraryView.swift (Task 6)
ios/HashiyaKit/Sources/FeatureSearch/SearchView.swift, FeatureLibrary/LibraryView.swift  top bar (Task 7)
ios/HashiyaUITests/LibraryFlowTests.swift                          large-title test (Task 7)
README.md                                                          (Task 8)
```

## Where this plan departs from the spec (and why)

- **None on behaviour.** Spec §16 was written together with this plan, from the same spikes: a layer render blanks glass, a hosted bundle captures it, `@testable import` works from the hosted bundle, the iOS 26 large titles are missing on `main`, and glass images carry run-to-run noise.
- **The Arabic "All" chip is truncated ("الكل (…)") on iOS 18 and iOS 26 alike.** This came from spec 3's chips and has nothing to do with glass. It is left for a separate fix, so that Task 1's iOS 18 images keep today's look.

---

### Task 1: Snapshot suites in an app-hosted bundle, per-OS baselines

**Files:**
- Modify: `ios/project.yml`, `ios/Hashiya/HashiyaApp.swift`, `ios/HashiyaKit/Package.swift`, `ios/HashiyaKit/Sources/HashiyaTesting/HashiyaSnapshots.swift`
- Create: `ios/HashiyaSnapshotTests/SnapshotHostTests.swift`
- Move: the five `*SnapshotTests.swift` files into `ios/HashiyaSnapshotTests/`
- Delete: `ios/HashiyaKit/Tests/*/__Snapshots__/` (baselines from the old renderer and Xcode 16.4, now useless)

**Interfaces:**
- Produces: `public enum SnapshotOS { static let supportedMajorVersions: [Int]; static func folder(systemVersion: String) -> String? }` in `HashiyaTesting`; `assertHashiyaSnapshots(of:named:arabicText:…)` keeps its signature, so no suite changes.

- [ ] **Step 1: Create the worktree and check the author**

```bash
git worktree add -b feat/ios-liquid-glass ../Hashiya-ios-liquid-glass main
cd ../Hashiya-ios-liquid-glass
git config user.email   # must print fady.fouad.a@gmail.com
```

- [ ] **Step 2: Move the suites and drop the old baselines**

```bash
mkdir ios/HashiyaSnapshotTests
git mv ios/HashiyaKit/Tests/HashiyaDesignSystemTests/DesignSystemSnapshotTests.swift ios/HashiyaSnapshotTests/
git mv ios/HashiyaKit/Tests/FeatureSearchTests/SearchSnapshotTests.swift ios/HashiyaSnapshotTests/
git mv ios/HashiyaKit/Tests/FeatureSearchTests/ShareSnapshotTests.swift ios/HashiyaSnapshotTests/
git mv ios/HashiyaKit/Tests/FeatureLibraryTests/LibrarySnapshotTests.swift ios/HashiyaSnapshotTests/
git mv ios/HashiyaKit/Tests/FeatureSettingsTests/SettingsSnapshotTests.swift ios/HashiyaSnapshotTests/
git rm -rq ios/HashiyaKit/Tests/HashiyaDesignSystemTests/__Snapshots__ ios/HashiyaKit/Tests/FeatureSearchTests/__Snapshots__ ios/HashiyaKit/Tests/FeatureLibraryTests/__Snapshots__ ios/HashiyaKit/Tests/FeatureSettingsTests/__Snapshots__
```

In `ios/HashiyaKit/Package.swift`, remove `exclude: ["__Snapshots__"]` from the four test targets that had it (`HashiyaDesignSystemTests`, `FeatureSearchTests`, `FeatureLibraryTests`, `FeatureSettingsTests`), together with the comma before it, so each reads like:

```swift
        .testTarget(
            name: "FeatureSearchTests",
            dependencies: ["FeatureSearch", "HashiyaData", "HashiyaDesignSystem", "HashiyaModel", "HashiyaTesting"]
        ),
```

- [ ] **Step 3: Add the hosted target to `ios/project.yml`**

Insert before `schemes:`:

```yaml
  HashiyaSnapshotTests:
    type: bundle.unit-test
    platform: iOS
    sources:
      - path: HashiyaSnapshotTests
        excludes:
          - "__Snapshots__/**"
    configFiles:
      Debug: Config/Base.xcconfig
      Release: Config/Base.xcconfig
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: com.etatech.hashiya.snapshottests
        GENERATE_INFOPLIST_FILE: YES
    dependencies:
      - target: Hashiya
      - package: HashiyaKit
        products:
          - HashiyaTesting
          - HashiyaData
          - HashiyaDesignSystem
          - HashiyaModel
          - FeatureSearch
          - FeatureLibrary
          - FeatureSettings
```

In the `Hashiya` scheme, add `HashiyaSnapshotTests: [test]` under `build: targets:` (before `HashiyaUITests: [test]`). Add the target to `test: targets:` before `- HashiyaUITests`, not parallelized: every suite draws into the app's one key window.

```yaml
        - name: HashiyaSnapshotTests
          parallelizable: false
```

XcodeGen sets `TEST_HOST` and `BUNDLE_LOADER` to `Hashiya.app` because the target depends on the app. Check after generating: `grep -c 'TEST_HOST = "$(BUILT_PRODUCTS_DIR)/Hashiya.app/Hashiya"' ios/Hashiya.xcodeproj/project.pbxproj` must print 2 (Debug and Release).

- [ ] **Step 4: Write the failing host tests**

`ios/HashiyaSnapshotTests/SnapshotHostTests.swift`:

```swift
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
```

- [ ] **Step 5: Run it and see it fail**

```bash
xcodegen generate --spec ios/project.yml
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.4' -collect-test-diagnostics never -only-testing:HashiyaSnapshotTests/SnapshotHostTests
```

Expected: a compile error, `cannot find 'SnapshotOS' in scope`.

- [ ] **Step 6: Make the app idle while it hosts the tests**

Replace `ios/Hashiya/HashiyaApp.swift` with:

```swift
import HashiyaDesignSystem
import SwiftUI

@main
struct HashiyaApp: App {
    /// Nil while the app hosts `HashiyaSnapshotTests`: then nothing opens the library or starts a task.
    @State private var container: AppContainer?

    init() {
        HashiyaFonts.register()
        HashiyaFonts.applyNavigationBarFonts()
        _container = State(initialValue: Self.isSnapshotTestHost ? nil : AppContainer.make())
    }

    var body: some Scene {
        WindowGroup {
            if let container {
                RootView(container: container)
            } else {
                // The snapshot tests draw into this window.
                Color.clear
            }
        }
    }

    /// True while the app hosts `HashiyaSnapshotTests` (Debug only): XCTest sets this variable in its host's
    /// environment. UI tests launch the app without it.
    private static var isSnapshotTestHost: Bool {
        #if DEBUG
        ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
        #else
        false
        #endif
    }
}
```

- [ ] **Step 7: Per-OS folders and the key-window render in the helper**

In `ios/HashiyaKit/Sources/HashiyaTesting/HashiyaSnapshots.swift`, replace the doc comment above `assertHashiyaSnapshots` with `SnapshotOS` plus a new doc comment:

```swift
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
```

At the start of the function body, before `HashiyaFonts.register()`, add:

```swift
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
```

Replace the whole `assertSnapshot(…)` call with:

```swift
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
```

In the Arabic `#expect`, replace the inline `SourceLocation(…)` argument with `sourceLocation: sourceLocation`.

- [ ] **Step 8: Run the host tests on both OS versions**

```bash
xcodegen generate --spec ios/project.yml
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.4' -collect-test-diagnostics never -only-testing:HashiyaSnapshotTests/SnapshotHostTests
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -collect-test-diagnostics never -only-testing:HashiyaSnapshotTests/SnapshotHostTests
```

Expected: `✔ Test run with 10 tests` on both. On iOS 18 the glass test still passes: its capsule is absent and the corner is red.

- [ ] **Step 9: Record every suite on both OS versions, then verify three times**

Run `-only-testing:HashiyaSnapshotTests` on iOS 26.4, then on iOS 18.2. The first run on each records 140 images and fails with `No reference was found on disk` (35 tests × 4). No Arabic `did not render` issue may appear. Check the count:

```bash
find ios/HashiyaSnapshotTests/__Snapshots__/iOS26 -name '*.png' | wc -l   # 140
find ios/HashiyaSnapshotTests/__Snapshots__/iOS18 -name '*.png' | wc -l   # 140
```

Open `iOS18/LibrarySnapshotTests/papersWithChipsAndBadges.papers-EnglishLight.png`. It must show the large "Library" title, the outlined chips with a filled ✓ All, the three badge styles (To read outlined, Reading filled, ✓ Read filled) and the shadowed Add paper button. (`main` has no committed image of this state to compare with: its Library baselines predate spec 3's chips.) Then run each OS three more times. Each run must pass.

If an iOS 26 run fails, open the failure's reference image and the image in the simulator's `tmp/<Suite>/` folder named in the message, and measure the difference:

```bash
python3 -c "
from PIL import Image, ImageChops; import sys
a,b=(Image.open(p).convert('RGB') for p in sys.argv[1:3]); d=list(ImageChops.difference(a,b).getdata())
print('differing', sum(max(q)>0 for q in d)/len(d), 'strong', sum(max(q)>30 for q in d))" REFERENCE.png FAILED.png
```

If `strong` is 0, lower only the iOS 26 pair in Step 7 by 0.01 at a time, down to at most `(0.97, 0.93)`, and repeat the three runs. If `strong` is above 0, the render really differs: the likely cause is an animation still in flight (a banner's move transition, a glass materialize) or two suites sharing the window. Check that `parallelizable: false` made it into the generated scheme (`grep -A3 HashiyaSnapshotTests ios/Hashiya.xcodeproj/xcshareddata/xcschemes/Hashiya.xcscheme | grep parallelizable`). Stop and report with both images rather than lowering the tolerance further.

An iOS 18 run must pass all three times at `(1, 0.98)`.

- [ ] **Step 10: Package tests still pass without the moved suites**

```bash
(cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.4')
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 11: Commit (no images)**

```bash
git add ios/project.yml ios/Hashiya/HashiyaApp.swift ios/HashiyaKit/Package.swift ios/HashiyaKit/Sources/HashiyaTesting/HashiyaSnapshots.swift ios/HashiyaSnapshotTests/SnapshotHostTests.swift ios/HashiyaSnapshotTests/DesignSystemSnapshotTests.swift ios/HashiyaSnapshotTests/SearchSnapshotTests.swift ios/HashiyaSnapshotTests/ShareSnapshotTests.swift ios/HashiyaSnapshotTests/LibrarySnapshotTests.swift ios/HashiyaSnapshotTests/SettingsSnapshotTests.swift
git add -u ios/HashiyaKit/Tests
git status --short | grep -v '^??'     # only this task's files; no __Snapshots__ additions
git commit -m "test: render iOS snapshots in an app-hosted bundle, per iOS version"
```

---

### Task 2: CI on Xcode 26.3, testing iOS 26.2 and iOS 18.5

**Files:**
- Modify: `.github/workflows/ios.yml`, `.github/workflows/ios-record-snapshots.yml`, `ios/README.md`

**Interfaces:**
- Consumes: Task 1's `HashiyaSnapshotTests` target and its `__Snapshots__/iOS26|iOS18` folders.

- [ ] **Step 1: Check the runner image**

The `macos-15` image of 2026-09-07 (`actions/runner-images`, `images/macos/macos-15-arm64-Readme.md`) lists `/Applications/Xcode_26.3.app` and the iOS 18.5, 18.6, 26.0, 26.1 and 26.2 simulator runtimes, with `iPhone 16` on both 18.5 and 26.2. `macos-26` has no iOS 18 runtime, so it can't record the iOS 18 baselines. Re-read that README before editing. If Xcode 26.3 or either runtime has gone, use the newest Xcode 26.x the image lists together with its iOS 26.x runtime and any iOS 18.x runtime on the same image, and change every place below and in `ios/README.md` together.

- [ ] **Step 2: Rewrite the test job in `ios.yml`**

Replace the `jobs:` block with:

```yaml
jobs:
  test:
    runs-on: macos-15
    timeout-minutes: 90
    steps:
      - uses: actions/checkout@v7
      - name: Select Xcode 26.3
        run: sudo xcode-select -s /Applications/Xcode_26.3.app
      - name: Check the pinned simulator runtimes exist
        run: |
          xcrun simctl list runtimes | grep -q 'iOS 26.2' || { echo "::error::no iOS 26.2 runtime"; exit 1; }
          xcrun simctl list runtimes | grep -q 'iOS 18.5' || { echo "::error::no iOS 18.5 runtime"; exit 1; }
      - name: Check Arabic translations
        run: python3 ios/scripts/check-translations.py
      - name: Generate the Xcode project
        run: |
          brew install xcodegen
          xcodegen generate --spec ios/project.yml
      - name: Unit and snapshot tests on iOS 26 and iOS 18 (no API key, no live network)
        env:
          TEST_RUNNER_SNAPSHOT_ARTIFACTS: ${{ runner.temp }}/snapshot-diffs
        run: >-
          xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya
          -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2'
          -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.5'
          -skip-testing:HashiyaUITests
          -collect-test-diagnostics never
      # The snapshot suites ran inside the app, which the UI tests launch fresh; start them from clean simulators.
      - name: Reset the simulators
        run: |
          xcrun simctl shutdown all
          xcrun simctl erase all
      - name: UI tests on iOS 26 and iOS 18
        run: >-
          xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya
          -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2'
          -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.5'
          -only-testing:HashiyaUITests
          -collect-test-diagnostics never
      - name: Upload snapshot diffs
        if: failure()
        uses: actions/upload-artifact@v7
        with:
          name: ios-snapshot-diffs
          path: ${{ runner.temp }}/snapshot-diffs
          if-no-files-found: ignore
```

- [ ] **Step 3: Record both OS versions in `ios-record-snapshots.yml`**

Change the header comment's "pinned runner, Xcode and simulator" to "pinned runner, Xcode 26.3 and the iOS 26.2 and iOS 18.5 simulators". Change `Select Xcode 16.4` to:

```yaml
      - name: Select Xcode 26.3
        run: sudo xcode-select -s /Applications/Xcode_26.3.app
```

In the record step, replace the single `-destination` line with both destinations and add `-only-testing:HashiyaSnapshotTests` (the package tests have no snapshots now):

```yaml
          xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya \
            -destination 'platform=iOS Simulator,name=iPhone 16,OS=26.2' \
            -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.5' \
            -collect-test-diagnostics never \
            -only-testing:HashiyaSnapshotTests 2>&1 | tee xcodebuild.log
```

Replace the image count check with one per OS:

```yaml
          for os in iOS26 iOS18; do
            count=$(find ios/HashiyaSnapshotTests/__Snapshots__/$os -type f -name '*.png' 2>/dev/null | wc -l | tr -d ' ')
            echo "Recorded $count $os baseline images"
            if [ "$count" -eq 0 ]; then
              echo "::error::no $os snapshot baselines were recorded"
              exit 1
            fi
          done
```

The pack step (`find ios -path '*/__Snapshots__/*' …`) and `ios/scripts/record-snapshots-on-ci.sh` need no change.

- [ ] **Step 4: Check the YAML**

```bash
python3 -c "import yaml,sys; [yaml.safe_load(open(p)) for p in sys.argv[1:]]; print('ok')" .github/workflows/ios.yml .github/workflows/ios-record-snapshots.yml
grep -n 'Xcode_16\|OS=18.5\|OS=26.2' .github/workflows/ios*.yml
```

Expected: `ok`, no `Xcode_16` lines, and each workflow names both OS versions.

- [ ] **Step 5: Update `ios/README.md`**

In the test commands, change both `-destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2'` to `-destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.4'`, and add a line under the first command:

```bash
# The same on iOS 18 (the pre-Liquid Glass look)
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2'
```

Replace the "Snapshot baselines" paragraph's first sentences with:

> Snapshot tests render every screen in English and Arabic, light and dark, on iOS 26 (Liquid Glass) and on iOS 18 (the teal styling iOS 17 and 18 keep). They live in `ios/HashiyaSnapshotTests`, a test bundle hosted by the app, because only a window render captures Liquid Glass. Their baselines under `ios/HashiyaSnapshotTests/__Snapshots__/iOS26/` and `…/iOS18/` are recorded only on CI (`macos-15`, Xcode 26.3, iPhone 16 on iOS 26.2 and on iOS 18.5), which is the source of truth; images recorded on your Mac are for inspection only and are not committed. Building needs Xcode 26 or later.

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/ios.yml .github/workflows/ios-record-snapshots.yml ios/README.md
git commit -m "ci: build iOS with Xcode 26.3 and test on iOS 26.2 and iOS 18.5"
```

CI can't run until GitHub billing is fixed. Don't wait for it here; Task 8 covers it.

---

### Task 3: Glass primitives and the glass banner

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaDesignSystem/Glass.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/HashiyaBanner.swift`
- Test: `ios/HashiyaSnapshotTests/DesignSystemSnapshotTests.swift`

**Interfaces:**
- Produces, all `public` in `HashiyaDesignSystem`: `struct HashiyaGlassGroup<Content: View>: View { init(spacing: CGFloat, @ViewBuilder content: () -> Content) }`; `extension View { func hashiyaChip(isSelected: Bool) -> some View; func hashiyaProminentButton() -> some View; func hashiyaSecondaryButton() -> some View }`. `hashiyaChip` sets the foreground colour, the surface and the content shape. Callers add padding before it and the check and `.isSelected` themselves.

- [ ] **Step 1: Write the failing snapshot test**

Append to `DesignSystemSnapshotTests`:

```swift
    /// Chips, the two button styles and a banner over cards, so the glass has content to refract.
    @Test func glassSurfaces() {
        let surfaces = ZStack(alignment: .top) {
            VStack(spacing: 0) {
                PaperCard(paper: SamplePapers.attention, inLibrary: true, onOpen: {}, onSave: {})
                PaperCard(paper: SamplePapers.bert, inLibrary: false, onOpen: {}, onSave: {})
                PaperCard(paper: SamplePapers.arabicTitled, inLibrary: false, onOpen: {}, onSave: {})
            }
            VStack(spacing: 16) {
                HashiyaGlassGroup(spacing: 8) {
                    HStack(spacing: 8) {
                        chip(readingStatusLabel(.toRead), isSelected: false)
                        chip(readingStatusLabel(.reading), isSelected: true)
                    }
                }
                HashiyaGlassGroup(spacing: 12) {
                    HStack(spacing: 12) {
                        Button {} label: {
                            Text(verbatim: readingStatusLabel(.read)).frame(maxWidth: .infinity)
                        }
                        .hashiyaSecondaryButton()
                        Button {} label: {
                            Text(verbatim: readingStatusLabel(.reading))
                                .foregroundStyle(HashiyaColors.onPrimary)
                                .frame(maxWidth: .infinity)
                        }
                        .hashiyaProminentButton()
                    }
                }
                .controlSize(.large)
                .padding(.horizontal, 16)
                HashiyaBanner(text: readingStatusLabel(.toRead), actionTitle: readingStatusLabel(.read), action: {})
            }
            .padding(.top, 60)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(HashiyaColors.surface)
        assertHashiyaSnapshots(of: surfaces, named: "glass", arabicText: "قيد القراءة")
    }

    private func chip(_ text: String, isSelected: Bool) -> some View {
        Text(verbatim: text)
            .font(.hashiya(.label))
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .hashiyaChip(isSelected: isSelected)
    }
```

- [ ] **Step 2: Run it and see it fail**

Run the snapshot suites on iOS 26.4. Expected: compile errors `cannot find 'HashiyaGlassGroup' in scope` and `value of type 'some View' has no member 'hashiyaChip'`.

- [ ] **Step 3: Write `Glass.swift`**

```swift
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

    public var body: some View {
        if #available(iOS 26, *) {
            GlassEffectContainer(spacing: spacing) { content }
        } else {
            content
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
```

- [ ] **Step 4: Make the banner glass**

In `HashiyaBanner.swift`, replace everything from `public var body: some View {` to the end of the file with:

```swift
    public var body: some View {
        HStack(spacing: 12) {
            Text(verbatim: text)
                .font(.hashiya(.body))
                .foregroundStyle(textColor)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let actionTitle, let action {
                Button(action: action) {
                    Text(verbatim: actionTitle).font(.hashiya(.label))
                }
                .foregroundStyle(actionColor)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .modifier(BannerSurface())
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    /// iOS 26: dark text on glass. Before: the inverted surface.
    private var textColor: Color {
        if #available(iOS 26, *) { HashiyaColors.onSurface } else { HashiyaColors.surface }
    }

    private var actionColor: Color {
        if #available(iOS 26, *) { HashiyaColors.primary } else { HashiyaColors.inversePrimary }
    }
}

/// iOS 26: regular glass in a continuous rounded rectangle. Before: an `OnSurface` fill.
private struct BannerSurface: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content.glassEffect(.regular, in: .rect(cornerRadius: 16, style: .continuous))
        } else {
            content.background(RoundedRectangle(cornerRadius: 12).fill(HashiyaColors.onSurface))
        }
    }
}
```

- [ ] **Step 5: Record iOS 26, look, verify; iOS 18 must pass untouched**

```bash
rm -rf ios/HashiyaSnapshotTests/__Snapshots__/iOS26/DesignSystemSnapshotTests ios/HashiyaSnapshotTests/__Snapshots__/iOS26/SearchSnapshotTests ios/HashiyaSnapshotTests/__Snapshots__/iOS26/ShareSnapshotTests ios/HashiyaSnapshotTests/__Snapshots__/iOS26/LibrarySnapshotTests
```

These are the suites that show a banner or the new test. Run the snapshot suites on iOS 26.4 twice: the first run records, the second passes. In `iOS26/DesignSystemSnapshotTests/glassSurfaces.glass-EnglishLight.png`, check that:
- the "To read" chip is clear glass with dark text and the "Reading" chip is teal glass with white text;
- the Read button is glass with teal text, and the Reading button is solid teal glass;
- the banner is glass with dark text and a teal action;
- the cards behind them show through, bent at the edges;
- in the Arabic images everything mirrors, and in the dark images text stays legible.

In `iOS26/LibrarySnapshotTests/undoBanner.undo-*.png`, the Undo banner is glass. Then run on iOS 18.2 **without deleting anything**. Only `glassSurfaces` (4 new images) may fail with "No reference"; a second run passes. Every older image passes, which proves the fallback banner is unchanged.

- [ ] **Step 6: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaDesignSystem/Glass.swift ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/HashiyaBanner.swift ios/HashiyaSnapshotTests/DesignSystemSnapshotTests.swift
git commit -m "feat: add iOS 26 glass chips, buttons and banners to the design system"
```

---

### Task 4: Preview buttons in a glass bottom bar (app sheet and share sheet)

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/PaperPreviewContent.swift`
- Test: the existing preview tests in `DesignSystemSnapshotTests` and `ShareSnapshotTests` (`found`, `foundAndSaved`), and `SearchSnapshotTests.lookupFound`

**Interfaces:**
- Consumes: `HashiyaGlassGroup`, `hashiyaProminentButton()`, `hashiyaSecondaryButton()` (Task 3). `PaperPreviewContent.init` is unchanged.

- [ ] **Step 1: Make the tests fail on the new look**

```bash
rm -rf ios/HashiyaSnapshotTests/__Snapshots__/iOS26/DesignSystemSnapshotTests ios/HashiyaSnapshotTests/__Snapshots__/iOS26/ShareSnapshotTests ios/HashiyaSnapshotTests/__Snapshots__/iOS26/SearchSnapshotTests
```

The deleted images are re-recorded after the change. The check in this task is the look in Step 3, plus iOS 18 passing untouched.

- [ ] **Step 2: Split the body into the paper and the actions**

Replace everything from `public var body: some View {` to the end of the file with:

```swift
    public var body: some View {
        if #available(iOS 26, *) {
            // The paper scrolls under the glass buttons; the bar's scroll edge effect keeps them legible.
            details
                .safeAreaBar(edge: .bottom, spacing: 0) { actions }
                .background(HashiyaColors.surface)
        } else {
            VStack(spacing: 0) {
                details
                actions
            }
            .background(HashiyaColors.surface)
        }
    }

    private var details: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                PaperText(PaperFormat.title(paper), style: .previewTitle)
                    .accessibilityAddTraits(.isHeader)
                if !paper.authors.isEmpty {
                    PaperText(paper.authors.map(\.name).joined(separator: ", "), style: .body, color: HashiyaColors.onSurfaceVariant)
                }
                Text(verbatim: PaperFormat.previewMeta(paper))
                    .font(.hashiya(.meta))
                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if paper.isOpenAccess {
                    StatusBadge(
                        text: L10n.string(paper.openAccessPDFURL == nil ? "designsystem.openAccess" : "designsystem.openAccessPDF"),
                        kind: .openAccess
                    )
                }
                Text(verbatim: L10n.string("designsystem.abstract"))
                    .font(.hashiya(.label))
                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    .padding(.top, 4)
                if let abstract = paper.abstract {
                    PaperText(abstract, style: .body)
                } else {
                    Text(verbatim: L10n.string("designsystem.noAbstract"))
                        .font(.hashiya(.body))
                        .foregroundStyle(HashiyaColors.onSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 24)
            .padding(.bottom, 16)
        }
    }

    /// The status selector (Library only) above Open DOI and Save/Remove.
    private var actions: some View {
        VStack(spacing: 0) {
            if let status {
                // 16 pt above; the buttons' 12 pt padding plus 4 makes 16 below.
                ReadingStatusSelector(status: status, onChange: onStatusChange)
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    .padding(.bottom, 4)
            }
            HashiyaGlassGroup(spacing: 12) {
                HStack(spacing: 12) {
                    if let doi = paper.doi, let onOpenDOI {
                        Button {
                            onOpenDOI(doi)
                        } label: {
                            Label {
                                Text(verbatim: L10n.string("designsystem.openDOI"))
                            } icon: {
                                Image(systemName: "arrow.up.forward.square")
                            }
                            .font(.hashiya(.label))
                            .frame(maxWidth: .infinity)
                        }
                        .hashiyaSecondaryButton()
                    }
                    Button(action: onToggleSave) {
                        Text(verbatim: L10n.string(inLibrary ? "designsystem.removeFromLibrary" : "designsystem.saveToLibrary"))
                            .font(.hashiya(.label))
                            .foregroundStyle(HashiyaColors.onPrimary)
                            .frame(maxWidth: .infinity)
                    }
                    .hashiyaProminentButton()
                }
            }
            .controlSize(.large)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
    }
}
```

- [ ] **Step 3: Record iOS 26, look, verify; iOS 18 untouched**

Run the snapshot suites on iOS 26.4 twice: the first records, the second passes. Check:
- `iOS26/DesignSystemSnapshotTests/previewOpenAccessWithPDF.previewOpenAccessPDF-EnglishLight.png`: Open DOI is glass with teal text, Save to library is solid teal glass, and there's no divider above them.
- `previewWithTheStatusSelector.previewStatus-*`: the selector sits above the buttons.
- `iOS26/ShareSnapshotTests/found.found-ArabicDark.png`: the same bar, mirrored, under "حاشية" and the glass Done button.

Run on iOS 18.2 without deleting: everything passes.

- [ ] **Step 4: UI tests that use the preview, on iOS 26**

```bash
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.4' -collect-test-diagnostics never -only-testing:HashiyaUITests/LibraryFlowTests/testPastingAnArxivIDShowsThePaperToSave -only-testing:HashiyaUITests/LibraryFlowTests/testThePreviewChangesTheStatusAndTheSearchKeyHidesTheKeyboard -only-testing:HashiyaUITests/ShareFlowTests
```

Expected: `** TEST SUCCEEDED **`. The buttons keep their labels, so `app.buttons["Save to library"]` still finds them.

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/PaperPreviewContent.swift
git commit -m "feat: put the iOS 26 preview buttons in a glass bar the paper scrolls under"
```

---

### Task 5: Glass Search chips and suggestions

**Files:**
- Modify: `ios/HashiyaKit/Sources/FeatureSearch/FilterChips.swift`
- Test: `SearchSnapshotTests` (`idle`, `results`, `filtersAndBanner`, `emptyWithClearFilters`, …)

**Interfaces:**
- Consumes: `HashiyaGlassGroup`, `hashiyaChip(isSelected:)` (Task 3). `ChipLabel`'s properties are unchanged, so `IdleView`'s suggestions follow automatically.

- [ ] **Step 1: Delete the Search images**

```bash
rm -rf ios/HashiyaSnapshotTests/__Snapshots__/iOS26/SearchSnapshotTests
```

- [ ] **Step 2: Group the row and use the chip modifier**

In `FilterChips.body`, wrap the `HStack(spacing: 8) { … }` inside the `ScrollView` in `HashiyaGlassGroup(spacing: 8) { … }` and move the `.padding` modifiers onto the group, so it reads:

```swift
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HashiyaGlassGroup(spacing: 8) {
                HStack(spacing: 8) {
                    // … the Sort Menu, Year Menu and Open access Button exactly as they are …
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
        }
    }
```

Replace `ChipLabel`'s doc comment and its modifiers after the `HStack`:

```swift
/// A chip: glass on iOS 26 (tinted when selected); before, outlined or filled with the primary container colour.
struct ChipLabel: View {
    let text: String
    let isSelected: Bool
    var leadingCheckmark = false
    var trailingChevron = false

    var body: some View {
        HStack(spacing: 4) {
            if leadingCheckmark {
                Image(systemName: "checkmark").font(.system(size: 11, weight: .semibold))
            }
            Text(verbatim: text).font(.hashiya(.label))
            if trailingChevron {
                Image(systemName: "chevron.down").font(.system(size: 10, weight: .semibold))
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .hashiyaChip(isSelected: isSelected)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
```

- [ ] **Step 3: Record, look, verify; iOS 18 untouched**

Run the snapshot suites on iOS 26.4 twice. Check:
- `iOS26/SearchSnapshotTests/filtersAndBanner.filtersAndBanner-EnglishLight.png`: Most cited, 2015–2020 and ✓ Open access are teal glass capsules with white text.
- `idle.idle-EnglishDark.png`: the three suggestions are clear glass capsules and stay left to right in Arabic.
- `results.results-*`: Relevance, Any time and Open access are clear glass.

Run iOS 18.2 without deleting: everything passes.

- [ ] **Step 4: Commit**

```bash
git add ios/HashiyaKit/Sources/FeatureSearch/FilterChips.swift
git commit -m "feat: make the iOS 26 Search filter chips and suggestions glass"
```

---

### Task 6: Glass Library chips, status badges and Add paper

**Files:**
- Modify: `ios/HashiyaKit/Sources/FeatureLibrary/LibraryFilterChips.swift`, `ReadingStatusBadge.swift`, `LibraryView.swift`
- Test: `LibrarySnapshotTests` (all), `LibraryFlowTests`

**Interfaces:**
- Consumes: `HashiyaGlassGroup`, `hashiyaChip(isSelected:)`, `hashiyaProminentButton()` (Task 3). `ReadingStatusPill(status:)` and `ReadingStatusBadge(status:onChange:)` are unchanged.

- [ ] **Step 1: Delete the Library images**

```bash
rm -rf ios/HashiyaSnapshotTests/__Snapshots__/iOS26/LibrarySnapshotTests
```

- [ ] **Step 2: Status chips**

In `LibraryFilterChips.body`, wrap the `HStack` in `HashiyaGlassGroup(spacing: 8)` the same way as Task 5 Step 2, with the padding on the group:

```swift
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HashiyaGlassGroup(spacing: 8) {
                HStack(spacing: 8) {
                    chip(nil, label: L10n.string("library.filterAll"), count: counts.values.reduce(0, +))
                    ForEach(ReadingStatus.allCases, id: \.self) { status in
                        chip(status, label: readingStatusLabel(status), count: counts[status] ?? 0)
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
        }
    }
```

In `chip(_:label:count:)`, replace the label's `.foregroundStyle`, `.padding`, `.background`, `.overlay` and `.contentShape` lines with:

```swift
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .hashiyaChip(isSelected: isSelected)
```

- [ ] **Step 3: Status pill**

In `ReadingStatusBadge.swift`, replace `ReadingStatusPill` (from its doc comment to the end of the file) with:

```swift
/// iOS 26: a glass pill, tinted with the primary colour for Reading. Before: To read outlined, Reading filled with
/// the primary container, Read filled. Read always adds a check, and the label always names the status, so it is
/// never told by colour alone.
struct ReadingStatusPill: View {
    let status: ReadingStatus

    var body: some View {
        HStack(spacing: 4) {
            if status == .read {
                Image(systemName: "checkmark")
                    .font(.system(size: 14 * 0.8, weight: .semibold))
                    .frame(width: 14, height: 14)
            }
            Text(verbatim: readingStatusLabel(status))
                .font(.hashiya(.label))
                .lineLimit(1)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .modifier(PillSurface(status: status))
        .fixedSize()
    }
}

private struct PillSurface: ViewModifier {
    let status: ReadingStatus

    /// A pill: just under half the default height. (A `Capsule`'s outline renders with seams in layer snapshots.)
    private static let shape = RoundedRectangle(cornerRadius: 11, style: .continuous)

    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content
                .foregroundStyle(status == .reading ? HashiyaColors.onPrimary : HashiyaColors.onSurface)
                .glassEffect(status == .reading ? .regular.tint(HashiyaColors.primary).interactive() : .regular.interactive(), in: .capsule)
                .contentShape(.capsule)
        } else {
            content
                .foregroundStyle(foreground)
                .background {
                    if status == .toRead {
                        Self.shape.strokeBorder(HashiyaColors.outline, lineWidth: 1)
                    } else {
                        Self.shape.fill(fill)
                    }
                }
                .contentShape(Self.shape)
        }
    }

    private var foreground: Color {
        switch status {
        case .toRead: HashiyaColors.onSurfaceVariant
        case .reading: HashiyaColors.onPrimaryContainer
        case .read: HashiyaColors.onSurface
        }
    }

    private var fill: Color {
        status == .reading ? HashiyaColors.primaryContainer : HashiyaColors.surfaceContainerHighest
    }
}
```

- [ ] **Step 4: Add paper and the banner group**

In `LibraryView.body`'s `.overlay(alignment: .bottom)`, wrap the `VStack(alignment: .trailing, spacing: 0) { … }` in a group and keep the frame outside it:

```swift
            .overlay(alignment: .bottom) {
                // The banners sit above the Add paper button (bottom trailing; bottom left in Arabic). On iOS 26
                // they are glass, grouped so they blend as they come and go.
                HashiyaGlassGroup(spacing: 12) {
                    VStack(alignment: .trailing, spacing: 0) {
                        // … the two banners and the Add paper button exactly as they are …
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }
```

Replace `AddPaperButton` with:

```swift
/// The floating "+ Add paper" capsule: glass on iOS 26, a filled capsule with a shadow before.
private struct AddPaperButton: View {
    let action: () -> Void

    var body: some View {
        let button = Button(action: action) {
            Label {
                Text(verbatim: L10n.string("library.addPaper"))
            } icon: {
                Image(systemName: "plus")
            }
            .font(.hashiya(.label))
            .foregroundStyle(HashiyaColors.onPrimary)
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
        }
        .hashiyaProminentButton()
        .buttonBorderShape(.capsule)

        if #available(iOS 26, *) {
            button
        } else {
            button.shadow(color: .black.opacity(0.15), radius: 6, y: 2)
        }
    }
}
```

- [ ] **Step 5: Record, look, verify; iOS 18 untouched**

Run the snapshot suites on iOS 26.4 twice. Check:
- `iOS26/LibrarySnapshotTests/papersWithChipsAndBadges.papers-EnglishLight.png`: ✓ All is teal glass, the others clear glass; Reading is a teal glass pill, ✓ Read and To read are clear glass; Add paper is solid teal glass with no grey shadow.
- `papers-ArabicDark`: all of this mirrored, with Add paper bottom left.
- `statusBadges.badges-*`: the three pills.
- `undoBanner.undo-*` and `statusUpdateFailedBanner.statusFailed-*`: the glass banners sit just above Add paper.

Run iOS 18.2 without deleting: everything passes.

- [ ] **Step 6: Library UI tests on iOS 26**

```bash
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.4' -collect-test-diagnostics never -only-testing:HashiyaUITests/LibraryFlowTests
```

Expected: `** TEST SUCCEEDED **`. `testChangingAStatusFiltersAndSearchesTheLibrary` proves the glass badge still opens its menu, not the preview, and that the chips keep `.isSelected`. If a test fails on a leftover search text or tab, first run `xcrun simctl erase "iPhone 17 Pro"` after shutting it down (see Review Focus 4) and run again. Report a failure that survives the erase.

- [ ] **Step 7: Commit**

```bash
git add ios/HashiyaKit/Sources/FeatureLibrary/LibraryFilterChips.swift ios/HashiyaKit/Sources/FeatureLibrary/ReadingStatusBadge.swift ios/HashiyaKit/Sources/FeatureLibrary/LibraryView.swift
git commit -m "feat: make the iOS 26 Library chips, status badges and Add paper glass"
```

---

### Task 7: Large titles on iOS 26 (Library and Search results)

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaDesignSystem/Glass.swift`, `ios/HashiyaKit/Sources/FeatureSearch/SearchView.swift`, `ios/HashiyaKit/Sources/FeatureLibrary/LibraryView.swift`
- Test: `ios/HashiyaUITests/LibraryFlowTests.swift`, `LibrarySnapshotTests`, `SearchSnapshotTests`

**Interfaces:**
- Produces: `public extension View { func hashiyaTopBar<Bar: View>(@ViewBuilder _ bar: () -> Bar) -> some View }`.

What we know: on iOS 26.4, `main` shows no large title on the Library (papers) or on Search with results; the band where the title belongs is empty. Search idle, which has no scroll view, shows "Search". Both screens put the chips in `.safeAreaInset(edge: .top)` over a `List`/`ScrollView`, with an opaque `HashiyaColors.surface` background. The first candidate fix is to put the chips in iOS 26's `safeAreaBar(edge: .top)` without the opaque background, so they join the navigation bar's scroll edge effect. This is unverified. If the title still doesn't show after Step 4, stop and report with the screenshots; don't try other layouts without a decision.

- [ ] **Step 1: Write the failing UI test**

Add to `LibraryFlowTests`:

```swift
    /// iOS 26 hid the large title above a list with the chips bar; it must show like on iOS 18.
    @MainActor
    func testLibraryAndSearchResultsShowTheirLargeTitles() {
        let app = launchApp()
        saveTwoPapers(in: app)
        XCTAssertTrue(app.navigationBars.staticTexts["Library"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.navigationBars.staticTexts["Library"].isHittable)

        app.tabBars.buttons["Search"].tap()
        XCTAssertTrue(app.staticTexts["About 3 results"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.navigationBars.staticTexts["Search"].isHittable)
    }
```

- [ ] **Step 2: Run it on iOS 26 and see it fail**

```bash
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.4' -collect-test-diagnostics never -only-testing:HashiyaUITests/LibraryFlowTests/testLibraryAndSearchResultsShowTheirLargeTitles
```

Expected: FAIL on the first `isHittable` (or `waitForExistence`). Then run it on iOS 18.2, where it must PASS. If it passes on iOS 26 too, the test can't see the bug. In that case, replace the two `isHittable` checks with an `XCUIScreen.main.screenshot()` attachment (`XCTAttachment(screenshot:)`, `lifetime = .keepAlways`), check the attachment by eye in this step and in Step 4, and say so in the commit message.

- [ ] **Step 3: Add `hashiyaTopBar` and use it**

Append to `Glass.swift`:

```swift
public extension View {
    /// A bar under the navigation bar (the filter chips). iOS 26: `safeAreaBar`, which joins the navigation bar's
    /// scroll edge effect, so it needs no background of its own. Before: `safeAreaInset` on the surface colour.
    func hashiyaTopBar<Bar: View>(@ViewBuilder _ bar: () -> Bar) -> some View {
        modifier(TopBar(bar: bar()))
    }
}

private struct TopBar<Bar: View>: ViewModifier {
    let bar: Bar

    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content.safeAreaBar(edge: .top, spacing: 0) { bar }
        } else {
            content.safeAreaInset(edge: .top, spacing: 0) { bar.background(HashiyaColors.surface) }
        }
    }
}
```

In `SearchView.screen`, replace the `.safeAreaInset(edge: .top, spacing: 0) { … }` with the same body minus its `.background(HashiyaColors.surface)`:

```swift
            .hashiyaTopBar {
                // ID mode has no chips.
                if viewModel.lookup == nil {
                    FilterChips(
                        query: viewModel.query,
                        onSort: { viewModel.setSort($0) },
                        onYears: { viewModel.setYears($0) },
                        onCustomRange: { showsYearRange = true },
                        onOpenAccess: { viewModel.setOpenAccessOnly($0) }
                    )
                }
            }
```

In `LibraryView.filtered`, the same:

```swift
            .hashiyaTopBar {
                LibraryFilterChips(
                    selected: viewModel.status,
                    counts: viewModel.filter?.counts ?? [:],
                    onSelect: { viewModel.setStatusFilter($0) }
                )
            }
```

- [ ] **Step 4: Run the UI test on both OS versions, then the snapshots**

Run Step 2's command on iOS 26.4 and on iOS 18.2. Both must pass. Then:

```bash
rm -rf ios/HashiyaSnapshotTests/__Snapshots__/iOS26/LibrarySnapshotTests ios/HashiyaSnapshotTests/__Snapshots__/iOS26/SearchSnapshotTests
```

Run the snapshot suites on iOS 26.4 twice. In `iOS26/LibrarySnapshotTests/papersWithChipsAndBadges.papers-EnglishLight.png` and `iOS26/SearchSnapshotTests/results.results-EnglishLight.png`, "Library" and "Search" appear as large titles above the search field, and the chips sit under it with no white band. Run iOS 18.2 without deleting: everything passes. The iOS 18 branch still applies the surface background, so nothing changes there.

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaDesignSystem/Glass.swift ios/HashiyaKit/Sources/FeatureSearch/SearchView.swift ios/HashiyaKit/Sources/FeatureLibrary/LibraryView.swift ios/HashiyaUITests/LibraryFlowTests.swift
git commit -m "fix: show the Library and Search large titles on iOS 26"
```

---

### Task 8: Docs, full verification, baselines and device checks

**Files:**
- Modify: `README.md`, `ios/README.md`

- [ ] **Step 1: READMEs**

In `ios/README.md`, add to the first paragraph, after "…and saves the paper from the share sheet.":

> On iOS 26 and later the chips, reading-status badges, banners, the preview's buttons and the Add paper button use Liquid Glass; iOS 17 and 18 keep the teal styling.

In `README.md`'s iOS paragraph, after "in English and Arabic", add ", with Liquid Glass on iOS 26".

- [ ] **Step 2: Everything, the way CI runs it, on both OS versions**

```bash
python3 ios/scripts/check-translations.py
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.4' -skip-testing:HashiyaUITests -collect-test-diagnostics never
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -skip-testing:HashiyaUITests -collect-test-diagnostics never
xcrun simctl shutdown all && xcrun simctl erase "iPhone 17 Pro" && xcrun simctl erase "iPhone 16 Pro"
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.4' -only-testing:HashiyaUITests -collect-test-diagnostics never
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2' -only-testing:HashiyaUITests -collect-test-diagnostics never
```

Expected: all green. Run the two snapshot commands two more times each (three in a row, Review Focus 3). Erasing a simulator deletes everything on it; if the user keeps anything on those simulators, ask before erasing.

- [ ] **Step 3: Device and simulator checks (spec §16.6)**

On the iOS 26.4 simulator (or a device), with `-ui-testing` and two saved papers:
- Settings → Accessibility → Display & Text Size → **Reduce Transparency** on: chips, badges, banners, Add paper and the preview buttons turn frosted and stay legible.
- **Increase Contrast** on: glass gets borders, text stays legible.
- **Reduce Motion** on: removing a paper shows the Undo banner without morphing into Add paper.
- Arabic (Hashiya's language in iOS Settings): chips, badges and Add paper mirror; English titles stay left to right.

Then run the app once on the iOS 18.2 simulator and check it looks as before. Write down what you saw for the PR description.

- [ ] **Step 4: Commit, push, PR**

```bash
git add README.md ios/README.md
git commit -m "docs: describe iOS Liquid Glass in the READMEs"
git log --format='%an <%ae>' main..HEAD | sort -u    # only: Fady <fady.fouad.a@gmail.com>
git push -u origin feat/ios-liquid-glass
gh pr create --title "iOS Liquid Glass on iOS 26" --body-file <(printf '%s\n' \
  "Liquid Glass on the app's own floating controls on iOS 26 (chips, status badges, banners, preview buttons in a bottom bar, Add paper), with iOS 17/18 unchanged; the iOS 26 large titles fixed; CI on Xcode 26.3 testing iOS 26.2 and iOS 18.5; snapshots moved to an app-hosted bundle, because only a window render captures glass. Spec: iOS spec 1 §16." \
  "" \
  "Baselines: none committed yet. CI is blocked by GitHub billing, so the iOS 26 and iOS 18 baselines (Search, Library, share and the rest) are recorded with ios/scripts/record-snapshots-on-ci.sh once it runs." \
  "" \
  "Checked locally: <paste Step 2 and Step 3 results>")
```

- [ ] **Step 5: Baselines, once CI runs again**

This step is blocked while GitHub billing blocks Actions. When Actions run again:

```bash
bash ios/scripts/record-snapshots-on-ci.sh
find ios/HashiyaSnapshotTests/__Snapshots__/iOS26 -name '*.png' | wc -l    # 144 (36 tests × 4)
find ios/HashiyaSnapshotTests/__Snapshots__/iOS18 -name '*.png' | wc -l    # 144
git add -- ':(glob)ios/HashiyaSnapshotTests/__Snapshots__/**'
git commit -m "test: record iOS 26 and iOS 18 snapshot baselines on CI"
git push
```

Look through the recorded iOS 26 Search, Library and share images before committing, with the Task 3–7 checklists. The `ios.yml` run on the PR must then be green on both OS versions.
