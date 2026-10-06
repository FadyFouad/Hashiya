# Feedback and rating

Status: approved in conversation 2026-10-06; Android first, then iOS.

## 1. Goal

Before the public release, testers and users can reach the developer from inside the app, and people who are getting
value from Hashiya are asked for a store rating. Nothing new leaves the device without the user's own action, so the
privacy policy and the store privacy answers stay as they are.

## 2. Scope

| In | Out |
|----|-----|
| An **About** section, last in Settings: Send feedback, Rate Hashiya, Version | New analytics events |
| A rating prompt through the system's own API (Google Play In-App Review, SwiftUI `requestReview`) | Our own "Do you like Hashiya?" dialog |
| English and Arabic | A feedback form or server |

## 3. About section

Last section of Settings on both platforms, after Privacy. Title "About" / «حول التطبيق».

| Row | Does |
|-----|------|
| **Send feedback** («إرسال ملاحظات») | Opens an email draft (`mailto:`) to `fady.fouad.a@gmail.com`. Subject "Hashiya feedback" / «ملاحظات حول حاشية». Body: two empty lines for the message, then one line: `Hashiya 0.3.0 (3) · Android 14 · Pixel 7 · ar` (version and build, platform and OS version, device model, app language). Nothing from the library. |
| **Rate Hashiya** («قيّم حاشية») | iOS: `https://apps.apple.com/app/id6817343027?action=write-review`. Android: `market://details?id=com.etatech.hashiya`, falling back to `https://play.google.com/store/apps/details?id=com.etatech.hashiya` when no app handles it. |
| **Version** | Text, not tappable: "Version 0.3.0 (3)" / «الإصدار 0.3.0 (3)», read from the build (`BuildConfig` / `Bundle.main`). The numbers are Latin, as stores and support use them. |

**No email app:** when nothing can open the draft (Android: no activity resolves `ACTION_SENDTO`; iOS: `openURL` reports failure), the address is copied to the clipboard and the screen says "Email address copied: fady.fouad.a@gmail.com" / «تم نسخ عنوان البريد: …».

## 4. Rating prompt

### 4.1 The rule

`ReviewPrompt` (pure logic, injected clock, persistent counters) answers `shouldAsk(now)`:

- **Value shown:** at least **5 papers saved** since install, **or** at least **one BibTeX export**;
- **and settled in:** the app was first opened at least **3 days** ago;
- **and not recently asked:** never asked, or the last request was at least **120 days** ago.

A clock that moved backwards (first-open or last-asked in the future) counts as "not yet": it never asks early, and it
corrects itself once the clock passes the stored time. Counters only go up; removing a paper doesn't lower the count.

### 4.2 Counted events

The same user actions the analytics already marks, counted whether or not usage statistics are on (the counters stay on
the device):

- **Saved:** every successful save from Search, Add by ID or the share sheet (Android `SearchViewModel` where
  `PaperSaved` is logged; iOS the equivalent). Restoring a backup does not count.
- **Exported:** a BibTeX export that finished writing its file (Android `LibraryViewModel` where `Export(Bibtex)` is
  logged; iOS the equivalent). Copy BibTeX for one paper does not count.

### 4.3 Asking

- After a counted event, if `shouldAsk`, the app requests the prompt once the action has finished: after the save's
  confirmation, or after the export's file sheet closed. Never over a sheet, picker or dialog.
- **Android:** `ReviewManager.requestReviewFlow()` then `launchReviewFlow(activity, info)`; failures are ignored.
- **iOS:** SwiftUI's `@Environment(\.requestReview)` from the window in front.
- "Last asked" is stored when the request is made, since neither system says whether the prompt appeared.
- **Off** in debug builds, UI tests and snapshot tests. (Google shows nothing to apps not installed from Play anyway.)

### 4.4 Storage

- **Android:** `SharedPreferences` file `review_prompt` (first-open, saves, exported, last-asked). Not backed up: the
  backup rules include only `database/`, `datastore/` and the locale file.
- **iOS:** `UserDefaults` keys under `reviewPrompt.`.

## 5. Privacy

No change. No analytics event, no network call of our own. The email is drafted for the user, who chooses to send it;
the policy already lists the address. The store prompts are the platforms' own.

## 6. Testing

- `ReviewPrompt` unit tests: below and at each threshold; export alone; the 3-day wait; the 120-day gap; a clock that
  went backwards; persistence across instances.
- Feedback email builder unit test: recipient, subject in both languages, the info line, no library data.
- Settings: Compose/XCUI tests that the three rows exist and open the right intent/URL (intent verification on Android,
  a URL-opening seam on iOS); the no-email-app fallback copies the address.
- Settings screenshots/snapshots re-recorded in English and Arabic, light and dark.

## 7. Delivery

1. Android PR (delivered): About section, `ReviewPrompt`, Play In-App Review (`com.google.android.play:review-ktx`), hooks, tests.
2. iOS PR (delivered): the same with `requestReview`.
3. CHANGELOG `[Unreleased]` lines for each.
