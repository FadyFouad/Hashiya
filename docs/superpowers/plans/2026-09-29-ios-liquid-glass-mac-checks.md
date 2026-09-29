# iOS Liquid Glass: checks to run on the Mac

Tasks 4–7 and Task 8's README step of `2026-09-29-ios-liquid-glass.md` were written without Xcode, so nothing
in them has been built, run or looked at. This file lists every build, snapshot, image and UI-test step those
tasks call for, in the order to run them. Tick a box only after the check passes on the Mac.

## Setup and rules

Run everything from the worktree root on branch `feat/ios-liquid-glass`.

```bash
df -h /System/Volumes/Data          # before every build: another session also builds on this Mac
xcodegen generate --spec ios/project.yml
export IOS26='platform=iOS Simulator,name=iPhone 17 Pro,OS=26.4'
export IOS18='platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2'
export FILTER='(^/|^xcodebuild: |^macro expansion ).*error:|^✘|✔ Test run|Executed [0-9]+ test|\*\* TEST'
```

- **Record iOS 26 only for the suites a task changed**, with `TEST_RUNNER_SNAPSHOT_RECORD=1` and one
  `-only-testing:HashiyaSnapshotTests/<Suite>` per suite. The recording run always fails (recording does); the
  next run without the variable must pass.
- **Never** `rm -rf` a `__Snapshots__` folder, **never** record on iOS 18, **never** stage `__Snapshots__`.
- **Never erase a simulator.** Before UI tests that follow snapshot runs on the same simulator (they share the
  app's bundle ID and scene storage), uninstall the app instead:
  `xcrun simctl uninstall 'iPhone 17 Pro' com.etatech.hashiya` (or `'iPhone 16 Pro'`; if a name is ambiguous,
  use the UDID from `xcrun simctl list devices`). This also removes the Share Extension.
- Snapshot tolerances (already in `HashiyaSnapshots.swift`): iOS 26 `(0.98, 0.95)`, iOS 18 `(1, 0.98)`; images
  render with `drawHierarchyInKeyWindow` and `traits.displayGamut = .SRGB`.

## Run order

1. Task 3 carry-over
2. Task 4
3. Task 5
4. Task 6

## Task 3 carry-over

- [ ] **iOS 18 DesignSystemSnapshotTests pass without recording** (confirms Task 3's four new images and that
  iOS 18 did not change):
  ```bash
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS18" -collect-test-diagnostics never -only-testing:HashiyaSnapshotTests/DesignSystemSnapshotTests 2>&1 | grep -E "$FILTER"
  ```
  Expect `** TEST SUCCEEDED **`. A failure here is an iOS 18 regression: stop and report, do not re-record.

## Task 4: Preview buttons in a glass bottom bar

Commit: `feat: put the iOS 26 preview buttons in a glass bar the paper scrolls under`

- [ ] **Build + record iOS 26 for the three suites that show the preview:**
  ```bash
  TEST_RUNNER_SNAPSHOT_RECORD=1 xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS26" -collect-test-diagnostics never -only-testing:HashiyaSnapshotTests/DesignSystemSnapshotTests -only-testing:HashiyaSnapshotTests/ShareSnapshotTests -only-testing:HashiyaSnapshotTests/SearchSnapshotTests 2>&1 | grep -E "$FILTER"
  ```
  Expect it to compile (no `error:` lines; `safeAreaBar` and the glass button styles need the iOS 26 SDK) and
  to fail only because it recorded.
- [ ] **Verify iOS 26:** the same command without `TEST_RUNNER_SNAPSHOT_RECORD=1`. Expect `** TEST SUCCEEDED **`.
- [ ] **Look at the images** in `ios/HashiyaSnapshotTests/__Snapshots__/iOS26/`:
  - `DesignSystemSnapshotTests/previewOpenAccessWithPDF.previewOpenAccessPDF-EnglishLight.png`: Open DOI is
    glass with teal text, Save to library is solid teal glass, and there is no divider above them.
  - `DesignSystemSnapshotTests/previewWithTheStatusSelector.previewStatus-*.png`: the selector sits above the
    buttons.
  - `ShareSnapshotTests/found.found-ArabicDark.png`: the same bar, mirrored, under "حاشية" and the glass Done
    button.
  - None of them is blank or partly blank around the buttons (Review Focus 1).
- [ ] **iOS 18 unchanged** (no recording; covers the Task 3 carry-over too):
  ```bash
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS18" -collect-test-diagnostics never -only-testing:HashiyaSnapshotTests 2>&1 | grep -E "$FILTER"
  ```
  Expect `** TEST SUCCEEDED **`.
- [ ] **UI tests that use the preview, on iOS 26:**
  ```bash
  xcrun simctl uninstall 'iPhone 17 Pro' com.etatech.hashiya
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS26" -collect-test-diagnostics never -only-testing:HashiyaUITests/LibraryFlowTests/testPastingAnArxivIDShowsThePaperToSave -only-testing:HashiyaUITests/LibraryFlowTests/testThePreviewChangesTheStatusAndTheSearchKeyHidesTheKeyboard -only-testing:HashiyaUITests/ShareFlowTests 2>&1 | grep -E "$FILTER"
  ```
  Expect `** TEST SUCCEEDED **`; `app.buttons["Save to library"]` still finds the glass button.
- [ ] **The UI tests launch the real app, not the snapshot host's blank window:**
  ```bash
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS26" -collect-test-diagnostics never -only-testing:HashiyaUITests/LaunchTests 2>&1 | grep -E "$FILTER"
  ```
  Expect `** TEST SUCCEEDED **` (it finds the Library and Search tabs). While it and the run above go, the
  simulator shows the tab bar and the Library/Search screens, not an empty window. `HashiyaApp` only shows
  `Color.clear` when `XCTestConfigurationFilePath` is set, which XCTest sets for the hosted snapshot bundle and
  not for UI tests; a UI-test failure that says an element was not found and a blank simulator mean this broke.

## Task 5: Glass Search chips and suggestions

Commit: `feat: make the iOS 26 Search filter chips and suggestions glass`

- [ ] **Build + record iOS 26 Search:**
  ```bash
  TEST_RUNNER_SNAPSHOT_RECORD=1 xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS26" -collect-test-diagnostics never -only-testing:HashiyaSnapshotTests/SearchSnapshotTests 2>&1 | grep -E "$FILTER"
  ```
  Expect no `error:` lines; it fails only because it recorded.
- [ ] **Verify iOS 26:** the same command without `TEST_RUNNER_SNAPSHOT_RECORD=1`. Expect `** TEST SUCCEEDED **`.
- [ ] **Look at the images** in `ios/HashiyaSnapshotTests/__Snapshots__/iOS26/SearchSnapshotTests/`:
  - `filtersAndBanner.filtersAndBanner-EnglishLight.png`: Most cited, 2015–2020 and ✓ Open access are teal glass
    capsules with white text.
  - `idle.idle-EnglishDark.png`: the three suggestions are clear glass capsules; in `idle.idle-Arabic*.png` they
    stay left to right.
  - `results.results-*.png`: Relevance, Any time and Open access are clear glass.
- [ ] **iOS 18 unchanged** (no recording): `ChipLabel` now takes its colours from `hashiyaChip`, whose pre-iOS 26
  branch must draw exactly what `ChipLabel` drew before.
  ```bash
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS18" -collect-test-diagnostics never -only-testing:HashiyaSnapshotTests 2>&1 | grep -E "$FILTER"
  ```
  Expect `** TEST SUCCEEDED **`.

## Task 6: Glass Library chips, status badges and Add paper

Commit: `feat: make the iOS 26 Library chips, status badges and Add paper glass`

- [ ] **Build + record iOS 26 Library:**
  ```bash
  TEST_RUNNER_SNAPSHOT_RECORD=1 xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS26" -collect-test-diagnostics never -only-testing:HashiyaSnapshotTests/LibrarySnapshotTests 2>&1 | grep -E "$FILTER"
  ```
  Expect no `error:` lines (in particular `AddPaperButton.body`'s `let button` plus `if #available` in the view
  builder); it fails only because it recorded.
- [ ] **Verify iOS 26:** the same command without `TEST_RUNNER_SNAPSHOT_RECORD=1`. Expect `** TEST SUCCEEDED **`.
- [ ] **Look at the images** in `ios/HashiyaSnapshotTests/__Snapshots__/iOS26/LibrarySnapshotTests/`:
  - `papersWithChipsAndBadges.papers-EnglishLight.png`: ✓ All is teal glass, the others clear glass; Reading is a
    teal glass pill, ✓ Read and To read are clear glass; Add paper is solid teal glass with no grey shadow.
  - `papersWithChipsAndBadges.papers-ArabicDark.png`: all of this mirrored, with Add paper bottom left.
  - `statusBadges.badges-*.png`: the three pills.
  - `undoBanner.undo-*.png` and `statusUpdateFailedBanner.statusFailed-*.png`: the glass banners sit just above
    Add paper.
- [ ] **iOS 18 unchanged** (no recording; the chips, pills and Add paper keep their pre-iOS 26 look, shadow included):
  ```bash
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS18" -collect-test-diagnostics never -only-testing:HashiyaSnapshotTests 2>&1 | grep -E "$FILTER"
  ```
  Expect `** TEST SUCCEEDED **`.
- [ ] **Library UI tests on iOS 26:**
  ```bash
  xcrun simctl uninstall 'iPhone 17 Pro' com.etatech.hashiya
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS26" -collect-test-diagnostics never -only-testing:HashiyaUITests/LibraryFlowTests 2>&1 | grep -E "$FILTER"
  ```
  Expect `** TEST SUCCEEDED **`. `testChangingAStatusFiltersAndSearchesTheLibrary` proves the glass badge still
  opens its menu (not the preview) and that the chips keep `.isSelected`. If a test fails on a leftover search
  text or tab, uninstall the app again (not erase) and rerun once; report a failure that survives that.

## Deferred findings for the final review

- [ ] Task 1: the tolerance comment in `ios/HashiyaKit/Sources/HashiyaTesting/HashiyaSnapshots.swift` ("Glass
  re-renders with faint noise…") should name the cause, the sRGB display-gamut rendering, instead of "faint
  noise".
- [ ] Task 1: `SnapshotHostTests.theKeyWindowRenderShowsTheBackgroundAroundGlass` samples only the background
  corner; its glass proof should also sample a pixel inside or next to the glass.
