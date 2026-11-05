# Accessibility pass (TalkBack, VoiceOver, large text, Arabic)

Status: draft 4, 2026-11-05, revised after three reviews against origin/main (c4f0085). Ready for approval before the
plans. Android and iOS in one spec, delivered as one PR per platform. Starts after the BibTeX/RIS import is merged,
because it covers the Import screen too.

## 1. Goal

Someone who uses a screen reader, the largest text sizes, or Arabic with a screen reader can do every common task in
Hashiya without sighted help: search OpenAlex, save a paper, add one by DOI, find it in the Library, change its status,
put it in a collection, write notes, download and open the PDF, cite or export it, import a .bib/.ris file, back up
and restore.

There are two targets:

- **iOS:** be able to honestly declare **VoiceOver, Larger Text, Sufficient Contrast and Dark Interface** in the App
  Store's Accessibility Nutrition Labels. App Store Connect allows a feature to be declared only when users can complete
  all of the app's common tasks with it. The labels are optional today, but Apple says they will become required for
  new apps and updates.
- **Android:** reach the same level with TalkBack, font scale up to 200%, and contrast. Google Play has no equivalent
  label, so the Android target is defined by the checks in §7 rather than a store form.

## 2. Scope

| In | Out |
|----|-----|
| Every screen on both platforms, including Import, iPad and tablet layouts | Voice Control and Switch Control certification (spot-checked only, §7.3) |
| Labels, roles, states, headings, grouping and reading order | Captions and audio description (the app has no video or audio) |
| Swipe and long-press actions reachable from TalkBack/VoiceOver | A custom in-app screen reader for PDFs |
| Text up to Android 200% / iOS AX5, with nothing clipped or overlapped | Reflowing PDF page text |
| Contrast of text **and** of UI boundaries in light and dark (§4.6) | A high-contrast theme of our own (system settings only) |
| Speaking English paper titles in English inside the Arabic UI (§4.3) | Translating paper metadata |
| Announcements for progress and results (search, download, import, backup) | |
| PDF reader: page text exposed to TalkBack where Android allows it (§4.4) | Tagged-PDF structure (headings, tables inside the PDF) |
| Reduce Motion / animator scale respected | |
| Automated checks added to the existing test suites (§7) | |

## 3. What the audit found (origin/main at c4f0085)

The app already has a reasonable base. On Android, 54 `contentDescription`s (30 of them deliberately `null` on
decorative icons), roles on custom clickables and toggles, `selected` states on rows and chips,
`minimumInteractiveComponentSize` where custom rows are clickable, `TextDirection.Content` on titles and search fields,
a polite live region in the notes fields, and a 2× font-scale screenshot of `PaperCard`. On iOS, 18 labels, 14 trait
additions (6 of them `.isHeader`), 16 hidden decorative elements and a custom action on one row. iOS uses system
`swipeActions` and `contextMenu`, which VoiceOver already exposes as actions.

The gaps, in order of how much they block a task:

1. **Android PDF reader is silent.** Pages are drawn as bitmaps (`PdfPages.kt`). TalkBack only hears "Page n of m", so
   a TalkBack user can't read a paper. Android 15 (API 35) added `PdfRenderer.Page.getTextContents()`, and
   `PdfRendererPreV` brings the same APIs to API 30–34. Android's own documentation names accessibility and TalkBack as
   a use for this text. iOS uses PDFKit's `PDFView`, which is expected to expose page text to VoiceOver. Verify that on
   a device before planning any work there.
2. **Android Library row actions are unreachable with TalkBack.** `SwipeToDismissBox` can't be swiped with TalkBack,
   and the row menu (`RowMenu`) opens only through `SecondaryClickMenu`, which reacts to a mouse right-click and nothing
   else. Remove still works from the paper's Details, so the task isn't blocked, but Remove and Change status need custom
   actions on the row (§4.1). Search results use the same `SecondaryClickMenu`.
3. **No headings on Android.** There is no `heading()` semantics anywhere, so TalkBack users can't jump between
   sections in Details, Settings or the Import preview. iOS has headers on only 6 elements.
4. **English titles in an Arabic UI.** Most papers are English. With the app in Arabic, TalkBack and VoiceOver read
   titles, authors and abstracts with the Arabic voice. Neither platform marks the language of the content today.
5. **Outline contrast.** `outline` is `#D5DBDF` on white (1.40:1) in light and `#3A4448` on `#0E1417` (1.86:1) in dark,
   on both platforms. It's lower still on the tinted `surfaceContainer*` colours that cards and sheets use. Text
   contrast is fine everywhere (body text 14–18:1, `onSurfaceVariant` 5.8:1 light, 10.9:1 dark). WCAG 1.4.11 asks for
   3:1 for borders that are the only way to see a component (text field outlines, unselected chips). It's used in 24
   places on Android.
6. **Fixed sizes on iOS.** Five `.font(.system(size:))` (40 pt empty-state symbols, 10–14 pt chip and badge glyphs),
   `.lineLimit(1)` in three places (collection row, grouped row, reading-status badge) and `.fixedSize()` on the badge.
   At AX sizes these either don't grow or clip.
7. **No automated accessibility checks.** No `performAccessibilityAudit` in the 9 UI test files, no large-type
   snapshots on iOS, and only one large-font screenshot on Android.
8. **iOS Undo banner is too short for VoiceOver.** Banners stay for `HashiyaBanner.duration`, a fixed 4 seconds, and
   nothing on iOS checks whether VoiceOver is running, so a VoiceOver user can't reach Undo before it goes. The timer
   isn't in the banner: each screen repeats it as a `.task(id:)` with `Task.sleep(for: HashiyaBanner.duration)`, in 10
   places across 7 screens (Library ×3, Details, Reader, Search, Share lookup, Settings ×2). On Android, Compose's
   `SnackbarHost` already lengthens the timeout through the accessibility manager.
9. **Motion.** No `accessibilityReduceMotion` checks on iOS. Android animations go through Compose, which already
   follows the system animator scale, so only custom ones need checking.

## 4. Changes

### 4.1 Semantics, both platforms

- **Labels:** every icon-only control has a label that names the action ("Remove from library", not "Trash"). Labels
  are localized, and never contain "button" (the role says that).
- **Grouping:** a paper row (Library and Search) is one element. It reads title, then authors trimmed to the first one
  or two plus "and n others" (the full list is in Details), year, venue, reading status, "PDF downloaded" and "in
  library", in that order. The abstract is never part of the row. Android uses `mergeDescendants`; iOS uses
  `.accessibilityElement(children: .combine)`.
- **Row actions:** custom actions, not separate focus stops. They are Open, Save or Remove, "Mark as To read", "Mark as
  Reading", "Mark as Read" (one per status except the current one), Copy citation, and Open in new window (iPad). A
  custom action can't take a value, so Change status becomes separate actions; that's also fewer steps than opening a
  menu. Android uses `customActions`; iOS uses `.accessibilityAction(named:)`. The status badge on its own keeps its
  role and its state (`stateDescription` on Android, `accessibilityValue` on iOS).
- **Headings:** screen titles and section titles in Details (Abstract, Notes, Collections/PDF/DOI), Settings sections,
  Import preview groups and the reader's notes pane. Android uses `semantics { heading() }`; iOS uses `.isHeader`.
- **States:** reading status, the PDF row's state (not downloaded / downloading n% / downloaded / attached), switches,
  and selected filter chips are announced as state, not baked into the label.
- **Order:** on tablet and iPad two-pane layouts, the list is read before the detail and the reader before its notes.
  Android uses `isTraversalGroup` per pane.

### 4.2 Announcements

Polite announcements, no focus moves, for: search results loaded ("23 results"), quota reached, a PDF download that
finishes or fails, an import that finishes ("Imported 120 papers, 14 kept from the file"), and backup and restore
results. Android uses `liveRegion = Polite` on the status text; iOS uses `AccessibilityNotification.Announcement`
(iOS 17). Each announcement has a test.

Material 3 snackbars are already announced by TalkBack, so Android adds nothing for them; announcing them as well
would make TalkBack say them twice. iOS's custom `HashiyaBanner` is announced, since nothing announces it today.

**Undo while a screen reader is on:**

- **iOS:** while VoiceOver is on, a banner with an action stays until it's dismissed instead of closing after 4
  seconds.
  - **One shared timer:** the 10 copies of the banner timer (§3, gap 8) move into one modifier in
    `HashiyaDesignSystem`. It reads the VoiceOver setting, takes an injectable sleep, and clears the screen's banner
    state when time is up. Every screen uses it, so the rule below applies everywhere and is tested once (§7.1).
  - **Reading the setting:** SwiftUI's `@Environment(\.accessibilityVoiceOverEnabled)`, not polling
    `UIAccessibility.isVoiceOverRunning`. The environment value updates if VoiceOver is switched on or off while a
    banner is up; switching it off starts the normal 4-second timer from that moment. Tests can set it, which matters
    because a test can't turn VoiceOver on (§7.1).
  - **Closing it:** the banner has a Close action, and responds to the escape gesture (two-finger scrub) through
    `.accessibilityAction(.escape)`. Escape reaches the banner only while VoiceOver focus is on it; anywhere else it
    keeps its usual meaning and goes back a screen.
  - **Undo from anywhere:** while an Undo banner is up, the magic tap (two-finger double-tap,
    `.accessibilityAction(.magicTap)`) performs Undo wherever VoiceOver focus is. A custom action belongs to one
    element, so it can't serve as a screen-wide Undo; the magic tap is the gesture made for this.
  - **More than one banner:** a new banner of the same kind replaces the old one, and the replaced banner's action is
    gone (a newer removal can't bring back the older one's Undo). Banners of different kinds stack, as today: the
    Library can show a message, a removal Undo and a collection-removal Undo together, each in its own slot with its
    own timer. Every new banner is announced.
  - The setting is read on the device only and never reported (§6).
- **Android:** a test pins down that the Undo snackbar's timeout is lengthened when the accessibility manager asks for
  it, so a future change to `SnackbarHost` handling can't quietly break it.

### 4.3 Content language

Titles, authors, venues and abstracts are spoken in their own language:

- **Detection:** a field is spoken as Arabic when it contains any Arabic-script letter (U+0600–U+06FF, U+0750–U+077F,
  U+08A0–U+08FF, U+FB50–U+FDFF, U+FE70–U+FEFF), and as English otherwise. Mixed fields are spoken as Arabic.
  - This rule is broader than the citation formatter's, which checks only U+0600–U+06FF (`PersonName.swift`). The
    formatter is left as it is: widening it would change citation output and belongs in its own change.
  - "No Arabic letters" covers more than English. French, German, Turkish and CJK titles are also spoken with the
    English voice. That's accepted for this pass, because almost all OpenAlex titles a student saves are English or
    Arabic, and it's still better than the Arabic voice. A proper language-identification step is listed in §9.
- **Android:** the field's `AnnotatedString` carries a `SpanStyle(localeList = ...)`. The plan must first verify that
  Compose turns this into a `LocaleSpan` that TalkBack follows. If it doesn't, it falls back to setting the semantics
  text with a `LocaleSpan` directly.
- **iOS:** `AttributedString` with the `accessibilitySpeechLanguage` attribute on the field. Verify on a device that
  VoiceOver switches voice.
- Only applied when the UI language differs from the field's language, so an English UI with English titles is
  unchanged.

### 4.4 Android PDF text

- **First, a time-boxed evaluation (one day)** of the Jetpack PDF viewer (`androidx.pdf`). Does it expose page text to
  TalkBack well enough to replace the custom renderer? Its outcome decides whether the rest of this section is built at
  all. If it does, write a separate decision, since switching changes the reader, its zoom, the notes pane and the
  screenshots.
- If the custom renderer stays, each page's semantics text becomes its extracted text (`Page.getTextContents()`):
  - through `PdfRenderer` on API 35+;
  - through `PdfRendererPreV` on API 30–34, **only** when
    `SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13`. The class's reference marks every API as "Also in
    S Extensions 13", and some API 30–34 devices don't have that extension. The plan rechecks the exact level against the
    reference when it's written.

  The text is extracted lazily when the page comes into view and cached per page. It's spoken page by page, with the
  existing "Page n of m" as the page's label.
- On API 24–29, on API 30–34 without the extension, and on pages with no text layer (scanned PDFs), the page keeps
  "Page n of m" and adds "no readable text on this page". There is no OCR.
- Search in PDF already highlights matches; it now also announces "Match 3 of 12".
- **Testing:** Robolectric can't be relied on to extract PDF text. The page text goes through the existing
  `PdfPageSource` abstraction, so `AndroidPdfPageSource` does the real extraction and the semantics tests use a fake
  source with known text.

### 4.5 Large text

- **Android:** every screen is checked at 200% with the nonlinear font scale. Nothing is clipped or overlaps, and
  `maxLines = 1` is used only where an ellipsis is fine and the full text is available elsewhere (the paper's Details).
  Fixed heights on text containers become `heightIn(min =)`.
- **iOS:** the fixed-size glyphs use `@ScaledMetric` or a text style (`.font(.largeTitle)` for the 40 pt symbols,
  `.caption2` for chip glyphs). The three `.lineLimit(1)` become `.lineLimit(1...3)`, or a vertical layout at
  `dynamicTypeSize.isAccessibilitySize`. The badge drops `.fixedSize()`. Rows switch from horizontal to stacked at AX
  sizes (`ViewThatFits` or an `AnyLayout` switch).
- Both: the reader's page indicator and the zoom buttons scale. The PDF itself is not reflowed (§2).

### 4.6 Contrast

- `outline` must reach 3:1 against **every surface an outlined component is drawn on**: `surface` and all five
  `surfaceContainer*` colours, in both themes, on both platforms. `#7A8489` in both themes passes all of them:
  - light: 3.83 (white), 3.62, 3.46, 3.28, and 3.10 (`surfaceContainerHighest`, `#E3E8EB`);
  - dark: 4.85 (`surface`), 5.05, 4.46, 4.27, 3.76, and 3.21 (`surfaceContainerHighest`, `#2F3639`).

  The first draft's `#8A9499` / `#6F7A7F` failed on the tinted containers (2.51:1 light, 2.79:1 dark on
  `surfaceContainerHighest`). One value for both themes keeps it simple; a darker light value is fine if it looks too
  pale, as long as the test passes. `outlineVariant` stays for purely decorative dividers.
- Everything that relies on `outline` to be found (text fields, unselected chips, the collection checklist's boxes) is
  checked. Anything that also has a fill or a label can keep the lighter variant.
- The contrast pairs become a unit test on both platforms (§7.1), so a palette change can't silently regress.
- **Screenshots:** this change touches every screen that draws an outline, so it's its own commit. All affected
  baselines are re-recorded in that commit and nowhere else. Since Import lands first, the commit also re-records the
  Import screen's baselines; that's expected, not an unexplained change.

### 4.7 Touch targets and motion

- Every interactive element is at least 48×48 dp on Android and 44×44 pt on iOS, including chips, the badge's menu,
  and the rail's toggle.
- iOS: animations use `withAnimation(reduceMotion ? nil : ...)`. Android: no custom animation ignores the animator
  scale.

## 5. Arabic and RTL

- All new labels and announcements have Arabic strings. The translation check (`check-translations.py` and Android
  lint's missing-translation check) fails without them.
- Labels with numbers use the locale's digits, the same way existing counters do.
- Labels that embed a title wrap it in FSI/PDI (U+2068/U+2069), as `LookupStates.kt` already does, so an English title
  inside an Arabic sentence keeps its order.
- Reading order in RTL follows the visual order. TalkBack and VoiceOver handle this when layouts use start/end, which
  the audit confirms.

## 6. Privacy and analytics

None. No new events or properties. In particular, the app doesn't report whether a screen reader or a large text size
is on, because that is disability information and the privacy text promises only feature use.

## 7. Testing

### 7.1 Unit tests

- **Contrast:** every `on*`/base pair meets 4.5:1, and `outline` meets 3:1 against `surface` and every
  `surfaceContainer*`, in both themes.
- **Undo (iOS, on the shared timer modifier):** with `accessibilityVoiceOverEnabled` set in the test's environment and
  an injected sleep:
  - the banner stays until Close or escape while the value is on;
  - it closes after 4 seconds when the value is off, and starts that timer when the value turns off;
  - the magic tap performs Undo while an Undo banner is up;
  - a new banner of the same kind replaces the old one, while banners of different kinds stack, each with its own timer.
- **Undo (Android):** the Undo snackbar's timeout is lengthened when the accessibility manager asks for it.
- **Language detection:** English, Arabic, mixed, Persian/Urdu letters, digits only.
- **Announcement strings:** counts, plurals and Arabic digits.

### 7.2 Automated UI checks

**Android (Robolectric, runs in CI as today):**

- Semantics assertions per screen. Every clickable has a label and a role, the headings exist, paper rows are merged and
  expose their custom actions, and states are present. These are plain `onNode` assertions, which work on Robolectric.
- Roborazzi screenshots of every screen at 200% font scale, in English and Arabic, light only. Nonlinear font scaling
  exists only from API 34. The feature modules' `robolectric.properties` already pin `sdk=35`, so the scaling under test
  is the real one; any new test module must pin 34 or later.
- The Accessibility Test Framework's Compose checks (`enableAccessibilityChecks`) need API 34 on a real device or
  emulator. On Robolectric they install but only log a warning, so they don't count as a check there. They go in an
  instrumented test suite that is run by hand on an API 34+ emulator before each release at first. A CI emulator job is
  added once the suite has proven stable (§9).

**iOS (XCUITest, runs in CI on the existing simulators):**

- `performAccessibilityAudit()` on every screen the existing flow tests reach, with every audit type. Contrast is the
  exception: the simulator's results there are noisy, so it relies on §7.1 instead.
  - The Dynamic Type check usually flags system parts that don't scale, such as the navigation title. Expect a short
    per-element allow-list.
  - Every ignored issue is listed by element in the test with a reason, never a blanket exclusion.
- Snapshot tests at `.accessibility3` and `.accessibility5` for Library, Search, Details, Reader, Import and Settings,
  in English and Arabic, **light only**. That's 6 × 2 × 2 = 24 per iOS version, or 48 new baselines across CI's two
  versions. Dark mode at AX sizes adds no layout coverage, so it's left out; dark is covered by the contrast unit test.

### 7.3 Manual checks (before each release that touches UI; in `docs/release.md`)

On one Android phone with TalkBack and one iPhone with VoiceOver, each in English and Arabic, run every common task
from §1 without looking at the screen. Also run them at the largest text size, then a quick Voice Control and Switch
Control pass on iOS. On iOS 26, also check by eye the chips and banners drawn over Liquid Glass, in light and dark
with Increase Contrast and Reduce Transparency both off and on. The §7.1 contrast test checks the palette, not what
glass does to it. The results are written down in the release checklist. The iOS Nutrition Label answers are
updated only from that record.

## 8. Delivery

1. A spec review gate (this document).
2. **Android plan, then implementation.**
   - First, the time-boxed `androidx.pdf` evaluation (§4.4), with a gate on its result.
   - Then, in order: semantics and headings, custom actions, announcements and Undo, content language, large text,
     contrast (with its own re-recorded screenshots), PDF text (or the switch to `androidx.pdf`), and tests throughout.
   - A gate after semantics and custom actions, and another after the PDF work.
3. **iOS plan, then implementation,** in the same order without the PDF work. One gate after semantics, and one at the
   end.
4. CHANGELOG entries under [Unreleased]. `docs/release.md` gets the manual checklist. App Store Connect gets the four
   Nutrition Label answers, only after the §7.3 record exists.

## 9. Open questions

1. **When does the ATF suite move into CI?** The proposal is to run it by hand before releases first, and add an
   emulator job once it has been stable for a couple of releases.
2. **Android PDF:** keep the custom renderer and add text, or switch to `androidx.pdf`? The §4.4 evaluation answers
   this; the spec builds whichever wins.
3. **The new `outline` (`#7A8489`)** changes how every outlined field and chip looks. Check it on a device, in light
   and dark, before the commit. The light margin is thin (3.10:1 on `surfaceContainerHighest`), so the value can't get
   any lighter. The §7.1 test fails if it does.
4. **Content language:** detecting languages beyond Arabic/not-Arabic, for example with Android's
   `TextClassifier.detectLanguage` and iOS's `NLLanguageRecognizer`. This is out of scope for this pass; decide after
   it ships.

## 10. Sources

- App Store Connect Help, "Overview of Accessibility Nutrition Labels": the requirement to complete all common tasks
  with a feature before declaring it, and that labels will later be required for submissions.
  https://developer.apple.com/help/app-store-connect/manage-app-accessibility/overview-of-accessibility-nutrition-labels
- WWDC25 session 224, "Evaluate your app for Accessibility Nutrition Labels".
  https://developer.apple.com/videos/play/wwdc2025/224
- Apple, "Performing accessibility audits for your app" (audit types; the XCUITest audit matches Accessibility
  Inspector). https://developer.apple.com/documentation/accessibility/performing-accessibility-audits-for-your-app
- Android Developers, "Android PDF viewer": `getTextContents()` in Android V; `PdfRendererPreV` for API 30–34; text for
  TalkBack. https://developer.android.com/media/grow/pdf-viewer
- Android API reference, `PdfRendererPreV`: every API is "Added in API level 35, Also in S Extensions 13".
  https://developer.android.com/reference/android/graphics/pdf/PdfRendererPreV
- Android Developers, "Test your app's accessibility": the Accessibility Test Framework with Espresso and Robolectric.
  https://developer.android.com/guide/topics/ui/accessibility/views/testing-views
- `androidx.compose.ui:ui-test-junit4-accessibility` (`enableAccessibilityChecks`): requires API 34, and is inconclusive
  on Robolectric (AndroidX sources, as summarised in skydoves/android-testing-skills).
- WCAG 2.2: 1.4.3 Contrast (Minimum), 1.4.11 Non-text Contrast, 2.5.8 Target Size (Minimum).
