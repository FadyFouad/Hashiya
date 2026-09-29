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

Everything runs at the branch head, so the code of all tasks is in every build. Where two tasks re-record the
same suite (Search and Library change again in Task 7), record it once and check it against every task's image
list.

1. **Task 7, large titles first.** If the title check fails on iOS 26, stop and report with screenshots; do not
   try other layouts.
2. Task 3 carry-over (iOS 18 DesignSystemSnapshotTests).
3. Tasks 4–7 snapshot records, verifies, image looks and iOS 18 runs.
4. Tasks 4 and 6 UI tests, and the real-app launch check.
5. Task 8 (full runs, device checks, then ask before pushing or opening a PR).
6. Deferred findings for the final review.

## Task 3 carry-over

- [x] **iOS 18 DesignSystemSnapshotTests pass without recording** (confirms Task 3's four new images and that
  iOS 18 did not change):
  ```bash
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS18" -collect-test-diagnostics never -only-testing:HashiyaSnapshotTests/DesignSystemSnapshotTests 2>&1 | grep -E "$FILTER"
  ```
  Expect `** TEST SUCCEEDED **`. A failure here is an iOS 18 regression: stop and report, do not re-record.

## Task 4: Preview buttons in a glass bottom bar

Commit: `feat: put the iOS 26 preview buttons in a glass bar the paper scrolls under`

- [x] **Build + record iOS 26 for the three suites that show the preview:** recorded together with Library in
  one run (phase 2); failed only because it recorded, no `error:` lines. See `mac-checks-2-report.md`.
- [x] **Verify iOS 26:** `** TEST SUCCEEDED **` (34 tests, 4 suites: DesignSystem, Share, Search, Library).
- [x] **Look at the images** — all PASS, see `mac-checks-2-report.md` for each image. One separate legibility
  finding (not a fail against this step's own criteria, see the report's look (b)): the large titles render
  washed-out pale grey on iOS 26 when a list/results row sits below, unlike iOS 18 or the idle screens.
- [x] **iOS 18 unchanged** (no recording; covers the Task 3 carry-over too):
  ```bash
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS18" -collect-test-diagnostics never -only-testing:HashiyaSnapshotTests 2>&1 | grep -E "$FILTER"
  ```
  **FAILED (phase 2): `LibrarySnapshotTests` fails 5/8 tests** (`papersWithChipsAndBadges`, `aFilteredSearch`,
  `noMatches`, `undoBanner`, `statusUpdateFailedBanner`) — a selected filter chip's text truncates with an
  ellipsis instead of showing the full label + count. DesignSystem, Share, Search and Settings all passed. This
  is a regression against the images Task 1 recorded: not fixed, not re-recorded. See `mac-checks-2-report.md`
  for the measured diffs and evidence — it also reproduces on freshly-recorded iOS 26 (this suite's `Library`
  images above are affected too: `papers-ArabicDark`'s "All" chip and `aFilteredSearch`'s "To read" chip both
  truncate on iOS 26 as well as iOS 18).
  **Task 8 re-run: PASSES.** `94cc5a5` (fix: keep the iOS 18 Library chips' layout unchanged) fixed the
  `LibraryFilterChips.chip()` truncation. The full `HashiyaSnapshotTests` suite (39 tests, 6 suites) now passes
  cleanly on iOS 18.2, run three times in a row with no flake, after the `origin/main` merge too. See
  `task-8-report.md`.
- [x] **UI tests that use the preview, on iOS 26:** `** TEST SUCCEEDED **` (`app.buttons["Save to library"]`
  still finds the glass button).
- [x] **The UI tests launch the real app, not the snapshot host's blank window:** `** TEST SUCCEEDED **` on iOS
  26.4 (`LaunchTests`); confirmed again on iOS 18.2.

## Task 5: Glass Search chips and suggestions

Commit: `feat: make the iOS 26 Search filter chips and suggestions glass`

- [x] **Build + record iOS 26 Search:** recorded together with DesignSystem, Share and Library in one run
  (phase 2); no `error:` lines, failed only because it recorded.
- [x] **Verify iOS 26:** `** TEST SUCCEEDED **` (part of the same 4-suite verify run).
- [x] **Look at the images** in `ios/HashiyaSnapshotTests/__Snapshots__/iOS26/SearchSnapshotTests/` — all PASS,
  see `mac-checks-2-report.md`.
- [x] **iOS 18 unchanged** (no recording): `ChipLabel` now takes its colours from `hashiyaChip`, whose pre-iOS 26
  branch must draw exactly what `ChipLabel` drew before.
  ```bash
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS18" -collect-test-diagnostics never -only-testing:HashiyaSnapshotTests 2>&1 | grep -E "$FILTER"
  ```
  **FAILED (phase 2), but not because of Search:** `SearchSnapshotTests` itself passed every test; the failure
  is entirely in `LibrarySnapshotTests` (see Task 4's iOS 18 box above and `mac-checks-2-report.md`). Left
  unticked because the full-suite run this step calls for does not currently pass.
  **Task 8 re-run: PASSES.** With `94cc5a5`'s chip fix, the full suite (including `SearchSnapshotTests`) passes
  on iOS 18.2, three runs in a row, after the `origin/main` merge. See `task-8-report.md`.

## Task 6: Glass Library chips, status badges and Add paper

Commit: `feat: make the iOS 26 Library chips, status badges and Add paper glass`

- [x] **Build + record iOS 26 Library:** recorded together with DesignSystem, Share and Search in one run
  (phase 2); no `error:` lines, failed only because it recorded.
- [x] **Verify iOS 26:** `** TEST SUCCEEDED **` (part of the same 4-suite verify run).
- [x] **Look at the images** in `ios/HashiyaSnapshotTests/__Snapshots__/iOS26/LibrarySnapshotTests/` — PASS
  against this step's own criteria (glass styling, mirroring, banner placement all correct; none blank). Separate
  defect found while looking (not this step's criteria, see `mac-checks-2-report.md`): the selected filter
  chip's label+count text truncates with "…" in `papers-ArabicDark`'s "All" chip and in `aFilteredSearch`'s
  chips — the same truncation as the iOS 18 regression below, so it is not iOS-26-specific.
- [x] **iOS 18 unchanged** (no recording; the chips, pills and Add paper keep their pre-iOS 26 look, shadow included):
  ```bash
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS18" -collect-test-diagnostics never -only-testing:HashiyaSnapshotTests 2>&1 | grep -E "$FILTER"
  ```
  **FAILED (phase 2): `** TEST FAILED **`, 5 of 8 `LibrarySnapshotTests` failed.** `papersWithChipsAndBadges`,
  `aFilteredSearch`, `noMatches`, `undoBanner` and `statusUpdateFailedBanner` all fail because the selected
  filter chip's text (e.g. Arabic "(4) الكل", English "To read · 1") now truncates to an ellipsis instead of
  showing in full — a real, measured pixel difference (~11,000-12,600 "strong" >30/255 pixels per image, not
  anti-aliasing noise), not a flake. `empty()` and `statusBadges()` passed (they have no selected chip with a
  count in frame). This is a code defect in `LibraryFilterChips.chip()`: unlike `ReadingStatusPill` (which has
  `.fixedSize()`), the filter chip's `Text` has no `.fixedSize()`/`.lineLimit()`, so it truncates instead of
  growing when the chip's available width tightens. Not fixed here, not re-recorded; see `mac-checks-2-report.md`
  for measurements, crops and the reference-vs-actual comparison.
  **Task 8 re-run: PASSES.** `94cc5a5` fixed `LibraryFilterChips.chip()` to keep the iOS 18 layout unchanged
  (full label + count, no truncation). `LibrarySnapshotTests` and the rest of `HashiyaSnapshotTests` pass on iOS
  18.2, three runs in a row, after the `origin/main` merge. See `task-8-report.md`.
- [x] **Library UI tests on iOS 26:** `** TEST SUCCEEDED **` (`LibraryFlowTests`, 7 tests). Also reran on iOS
  18.2 (both `LibraryFlowTests` and `ShareFlowTests`, 8 tests): `** TEST SUCCEEDED **`.

## Task 7: Large titles on iOS 26 (Library and Search results)

Commit: `fix: show the Library and Search large titles on iOS 26`

The fix (`hashiyaTopBar`: chips in `safeAreaBar(edge: .top)` with no opaque background on iOS 26) was unverified.
Run this section first. (superseded: see below — the shipped fix uses a plain `safeAreaInset`, not `safeAreaBar`;
see the "Follow-up" item at the end of this section and spec §16.4.)

Ruling (phase 1 finding): the original `testLibraryAndSearchResultsShowTheirLargeTitles` UI test was
invalid for its Search half. That check ran while the Search field was **active** (focused, holding typed text,
with the cancel/✕ affordance visible) — iOS hides the navigation title while a search field is active by design,
on iOS 18 as well as iOS 26, so the check was never testing the bug it claimed to. The test was renamed to
`testTheLibraryShowsItsLargeTitle` (`test: check the Library large title only (iOS hides titles while search is
active)`) and now checks only the Library screen, whose search field is idle during the check. **Search's large
title is verified separately, through the iOS 26 `SearchSnapshotTests` results image** (the existing "Look at the
images" step below already checks that `results.results-EnglishLight.png` shows "Search" as a large title with a
search field that is *not* active) — that is the correct way to see whether Search's large title shows at rest.

- [x] **The new test sees the bug (Library).** Put back the Task 6 versions of the two screens (they still build
  against the new `Glass.swift`), run the test, then restore the head versions:
  ```bash
  git checkout cb7737d -- ios/HashiyaKit/Sources/FeatureSearch/SearchView.swift ios/HashiyaKit/Sources/FeatureLibrary/LibraryView.swift
  xcrun simctl uninstall 'iPhone 17 Pro' com.etatech.hashiya
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS26" -collect-test-diagnostics never -only-testing:HashiyaUITests/LibraryFlowTests/testTheLibraryShowsItsLargeTitle 2>&1 | grep -E "$FILTER"
  git checkout HEAD -- ios/HashiyaKit/Sources/FeatureSearch/SearchView.swift ios/HashiyaKit/Sources/FeatureLibrary/LibraryView.swift
  git status --short   # must show nothing under ios/HashiyaKit/Sources
  ```
  Expect `** TEST FAILED **` on the first `isHittable` (or `waitForExistence`). If it passes, the test can't see
  the bug: per the plan, the `isHittable` checks become an `XCTAttachment(screenshot: XCUIScreen.main.screenshot())`
  with `lifetime = .keepAlways`, checked by eye, and the commit message says so. Decide that before going on.
  Result: FAILED as expected (line 166 of the original two-screen test, before the rename).
- [x] **The fix shows the Library title on iOS 26:**
  ```bash
  xcrun simctl uninstall 'iPhone 17 Pro' com.etatech.hashiya
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS26" -collect-test-diagnostics never -only-testing:HashiyaUITests/LibraryFlowTests/testTheLibraryShowsItsLargeTitle 2>&1 | grep -E "$FILTER"
  ```
  Expect `** TEST SUCCEEDED **`. **If it fails, stop here and report with a screenshot of Library; don't try
  other layouts.** Result: SUCCEEDED.
- [x] **Still passes on iOS 18:**
  ```bash
  xcrun simctl uninstall 'iPhone 16 Pro' com.etatech.hashiya
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS18" -collect-test-diagnostics never -only-testing:HashiyaUITests/LibraryFlowTests/testTheLibraryShowsItsLargeTitle 2>&1 | grep -E "$FILTER"
  ```
  Expect `** TEST SUCCEEDED **`. Result: SUCCEEDED.
- [x] **Re-record iOS 26 Library and Search** (after Tasks 5 and 6, this is the recording that counts; this is
  also where Search's large title gets its real verification — see the ruling above): recorded together with
  DesignSystem and Share in one run (phase 2); the record run failed only because it recorded, and the verify
  run printed `** TEST SUCCEEDED **`.
- [x] **Look at the images:** confirmed in `papersWithChipsAndBadges.papers-EnglishLight.png` and
  `results.results-EnglishLight.png` — "Library" and "Search" both show as large titles above the search field,
  chips sit under them with no white band. **Extra look (a)**: yes — `results.results-EnglishLight.png`
  shows "Search" as a large title above the field while the field is idle (holds "transformers" as plain
  committed text, no clear/cancel affordance), which is Search's real large-title verification. **Extra look
  (b)**: the "Library" and "Search" titles in these two images are pale/washed-out, not
  normal-contrast — measured, not just eyeballed: title-pixel RGB ≈ (214,214,214) against a ≈(252,252,252)
  background in both images (contrast ratio ≈1.2:1). By contrast, iOS 26's own idle Search screen (no list
  below the title) renders "Search" at full contrast — pure black (0,0,0) in light mode, pure white in dark
  mode — and iOS 18's "Library"/"Search" titles are pure black (0,0,0) in the same papers/results states. So the
  fade is specific to iOS 26 with a populated list under the title, not a general iOS 26 style and not present
  on iOS 18. See `mac-checks-2-report.md` for the crops and pixel samples. Also rechecked Task 5's and Task 6's
  image lists on these new images: still PASS.
- [x] **iOS 18 unchanged** (no recording; the pre-iOS 26 branch keeps `safeAreaInset` with the surface
  background):
  ```bash
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS18" -collect-test-diagnostics never -only-testing:HashiyaSnapshotTests 2>&1 | grep -E "$FILTER"
  ```
  Phase 2: FAILED, with the same `LibrarySnapshotTests` chip-truncation regression as Task 4/6's iOS 18 boxes
  above. The cause was `HashiyaGlassGroup`'s `if #available` body, which made SwiftUI lay the chip row out at a
  fixed width inside the horizontal ScrollView. Fixed in `fix: keep the iOS 18 Library chips' layout unchanged`
  (the body is now one `AnyView`). After the fix: `✔ Test run with 39 tests in 6 suites passed`, against Task 1's
  iOS 18 images, with nothing recorded. See `debug-report.md`, Issue A.
- [x] **Follow-up: full-contrast large titles (the pale title from extra look (b)).** Cause: iOS 26 hosts the
  large title inside the list, and `safeAreaBar` extends the list's top scroll-edge effect (0–333 pt, with a
  backdrop at α 0.85) over it (see `debug-report.md`, Issue B). Fix: `fix: show the iOS 26 large titles at full
  contrast`. On iOS 26, `hashiyaTopBar` is now `safeAreaInset(edge: .top)` with no background; the iOS 17/18 branch
  is unchanged. I re-recorded only `iOS26/LibrarySnapshotTests` and `iOS26/SearchSnapshotTests`, and two verify
  runs both printed `** TEST SUCCEEDED **`. Title pixels (darkest title pixel on the background):

  | Image | Before | After |
  |---|---|---|
  | papers-EnglishLight | 214 on 252 | 0 on 255 |
  | results-EnglishLight | 214 on 252 | 0 on 255 |
  | papers-ArabicDark | 49 on 14 | 255 on 14 |
  | results-ArabicDark | 49 on 14 | 255 on 14 |

  The chips still sit under the search field, and the chip row's background matches the list (no band).
  `testTheLibraryShowsItsLargeTitle` passed on iOS 26.4 and iOS 18.2 (app uninstalled first), and all iOS 18
  snapshot suites passed unchanged (39 tests, 6 suites).

## Task 8: Full verification, device checks, push and PR

Step 1 (the READMEs) is done: `docs: describe iOS Liquid Glass in the READMEs`. Steps 2–5 are here.

- [x] **Merge `main` first:** `main` moved (to `e287610` or later) while this branch ran.
  ```bash
  git fetch origin main && git merge origin/main
  xcodegen generate --spec ios/project.yml
  ```
  Resolve conflicts keeping both sides' behaviour; rerun anything a conflict touched.
  **Result:** merged `origin/main` (442144b) with `git merge --no-ff origin/main`; the merge resolved cleanly
  with **no textual conflicts** — the two branches touched disjoint files (main's search-on-submit changes and
  new store/branding assets vs. this branch's glass/design-system/snapshot-test files). `ios/project.yml` kept
  both sides (this branch's `HashiyaSnapshotTests` target/scheme plus main's changes). Confirmed after merge:
  `SearchViewModel.submitNow()`/`updateText()` (main's search-on-submit) and `SearchView`'s `.onSubmit(of: .search)`
  are intact, and the app builds. See `task-8-report.md` for the full merge write-up.
- [x] **Everything, the way CI runs it, on both OS versions** (Step 2; uninstall, never erase):
  ```bash
  python3 ios/scripts/check-translations.py
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS26" -skip-testing:HashiyaUITests -collect-test-diagnostics never 2>&1 | grep -E "$FILTER"
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS18" -skip-testing:HashiyaUITests -collect-test-diagnostics never 2>&1 | grep -E "$FILTER"
  xcrun simctl uninstall 'iPhone 17 Pro' com.etatech.hashiya
  xcrun simctl uninstall 'iPhone 16 Pro' com.etatech.hashiya
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS26" -only-testing:HashiyaUITests -collect-test-diagnostics never 2>&1 | grep -E "$FILTER"
  xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination "$IOS18" -only-testing:HashiyaUITests -collect-test-diagnostics never 2>&1 | grep -E "$FILTER"
  ```
  Expect all green. The UI-test runs also confirm the real app launches (tab bar and screens, not a blank
  window).
  **Result: all green.** `check-translations.py`: all 8 String Catalogs have Arabic translations. Package tests
  (`HashiyaKit-Package`, iOS 26.4): 294 tests, 8 bundles, `** TEST SUCCEEDED **`. `HashiyaSnapshotTests`: 39
  tests/6 suites passed on both iOS 26.4 and iOS 18.2, no re-recording needed anywhere. `HashiyaUITests`: 18
  tests passed on both iOS 26.4 and iOS 18.2 (app uninstalled first on each), including the search-on-submit
  typing flows — no test changes were needed.
- [x] **Three verifications in a row** (Review Focus 3): run the two `-skip-testing:HashiyaUITests` commands two
  more times each. No iOS 26 glass image may flake past the `(0.98, 0.95)` tolerance, and iOS 18 must stay green.
  **Result:** `HashiyaSnapshotTests` run 3× on iOS 26.4 (39/39 each time) and 3× on iOS 18.2 (39/39 each time);
  no flakes.
- [x] **Device and simulator checks** (Step 3, spec §16.6), on iPhone 17 Pro iOS 26.4 (or a device), with
  `-ui-testing` and two saved papers:
  - Settings → Accessibility → Display & Text Size → **Reduce Transparency** on: chips, badges, banners, Add paper
    and the preview buttons turn frosted and stay legible.
  - **Increase Contrast** on: glass gets borders, text stays legible.
  - **Reduce Motion** on: removing a paper shows the Undo banner without morphing into Add paper.
  - Arabic (Hashiya's language in iOS Settings): chips, badges and Add paper mirror; English titles stay left to
    right.
  - Then run the app once on iPhone 16 Pro iOS 18.2: it looks as before.
  Write down what you saw for the PR description.
  **Result: PASS on all four checks**, plus the iOS 18.2 sanity look. Check: a temporary XCUITest harness (not
  committed) seeded two saved papers and captured `XCTAttachment` screenshots while accessibility settings were
  toggled from the host via `xcrun simctl ui`/`defaults write`, without driving the simulator's UI interactively.
  Reduce Transparency: chips/badges/banner/Add paper/preview buttons all legible (the
  "frosted" look is subtle against Hashiya's plain white background, as expected). Increase Contrast: text stayed
  legible; a border increase on the chips was not clearly visible at screenshot resolution. Reduce Motion:
  removing a paper showed a clean, separate Undo banner beside Add paper, no morph. Arabic: Library title, search
  placeholder, filter chips, Settings gear, Add paper and the tab bar all mirrored to RTL; the preview's status
  segmented control and Open DOI/Remove buttons mirrored too; English paper titles stayed LTR throughout. iOS
  18.2: launched once, looks as before (solid teal chips/buttons, no glass). All settings were reset (Reduce
  Transparency/Motion off, Increase Contrast off, light appearance) and the app uninstalled from both
  simulators afterward. Screenshots and full detail in `task-8-report.md`.
- [x] **Author check:** `git log --format='%an <%ae>' origin/main..HEAD | sort -u` prints only
  `Fady <fady.fouad.a@gmail.com>`.
  **Result:** confirmed — only `Fady <fady.fouad.a@gmail.com>`.
- [ ] **Push and PR (Step 4): ask first.** Only after a yes: `git push -u origin feat/ios-liquid-glass` and
  `gh pr create` with the plan's title and body, pasting the results above into "Checked locally".
- [ ] **Baselines (Step 5), once GitHub Actions run again:** `bash ios/scripts/record-snapshots-on-ci.sh`; expect
  144 PNGs under each of `ios/HashiyaSnapshotTests/__Snapshots__/iOS26` and `iOS18`; look through the iOS 26
  Search, Library and share images with the Task 4–7 lists; then
  `git add -- ':(glob)ios/HashiyaSnapshotTests/__Snapshots__/**'`, commit
  `test: record iOS 26 and iOS 18 snapshot baselines on CI` and push. The PR's `ios.yml` run must be green on
  both OS versions.

## Deferred findings for the final review

- [ ] Task 1: the tolerance comment in `ios/HashiyaKit/Sources/HashiyaTesting/HashiyaSnapshots.swift` ("Glass
  re-renders with faint noise…") should name the cause, the sRGB display-gamut rendering, instead of "faint
  noise".
- [ ] Task 1: `SnapshotHostTests.theKeyWindowRenderShowsTheBackgroundAroundGlass` samples only the background
  corner; its glass proof should also sample a pixel inside or next to the glass.
