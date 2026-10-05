# Usage analytics — Design

- **Date:** 2026-10-05
- **Status:** Approved in brainstorming; awaiting spec review
- **Scope:** Both platforms. Android ships first (on top of crash reporting, already on `main`), then Android OpenAlex quota protection, then iOS crash reporting and analytics together. Plus store answers and a privacy-policy section.

## 1. Context

Hashiya has no usage data. Pre-release decisions need it: whether the core loop (search → save → notes / collections / export / PDFs) works, where people drop off, and how close real use comes to the OpenAlex budget (`docs/superpowers/specs/2026-10-05-openalex-quota-design.md`) and to anything a paid tier or ads would change.

Crash reporting (`docs/superpowers/specs/2026-10-04-crash-reporting-design.md`) already brought Firebase into the Android app (merged) and set the pattern: an interface in a pure module, Firebase only in the app target, a Settings **Privacy** section, collection only in release builds. That spec excluded Firebase Analytics; this one adds it, deliberately and minimised.

### Decisions made during brainstorming

| Topic | Decision |
|---|---|
| Goals | Mostly the core loop (A) and inputs for upcoming decisions — OpenAlex load, personal keys, page cap (C); active users and retention (B) as a by-product. |
| Consent | On by default, with its own **Share usage statistics** switch in Settings. |
| Service | Firebase Analytics (GA4), in the existing project `hashiya-research`, minimised (§5). |
| Content | A closed list of events and enum-valued parameters (§4). Nothing a user typed or read. |
| Delivery | Android analytics → Android OpenAlex quota → iOS crash reporting + analytics in one plan. |

## 2. Goals and non-goals

### Goals

1. Answer, from the Firebase console: how many people search, save, take notes, use collections, export and open PDFs; the search → save funnel; searches per user per day by route; how often the page cap and the daily limit are hit; how many users bring their own key.
2. Nothing a user typed or read ever leaves the device through analytics.
3. A user can turn it off in Settings; turning it off stops collection and clears the analytics id and unsent events.
4. Store answers, privacy manifests and the policy describe exactly what is collected.

### Non-goals

- Advertising ids, ad personalization, Google signals, cross-app tracking, user ids.
- Analytics in the iOS Share Extension.
- A consent prompt.
- BigQuery export, remote config, A/B tests.

## 3. Architecture

### Android

- New pure-Kotlin module `:core:analytics` (no Android, no Firebase):

```kotlin
interface Analytics {
    fun log(event: AnalyticsEvent)
    fun setProperty(property: AnalyticsProperty, value: String)
    fun setEnabled(enabled: Boolean)
}
object NoOpAnalytics : Analytics
sealed interface AnalyticsEvent { /* one subclass per event in §4, enum-typed parameters */ }
enum class AnalyticsProperty { LibrarySizeBucket, Language, HasOwnKey }
```

- `FirebaseAnalyticsTracker` lives in `:app`; Hilt binds it in release builds and `NoOpAnalytics` in debug builds (as `CrashReporter`). It maps each event to its Firebase name and parameters in one place.
- `FakeAnalytics` in `:core:testing` records every call.

### iOS

- The `HashiyaDiagnostics` target (created by the iOS crash-reporting work) gains `protocol AnalyticsTracking: Sendable`, `NoAnalytics`, `AnalyticsEvent` (an enum with associated enum values) and `AnalyticsProperty` — the same members as Android.
- `FirebaseAnalyticsTracking` lives in the `Hashiya` app target; everything that takes a tracker defaults to `NoAnalytics()`.
- A fake in `HashiyaTesting` records every call.

### Who sends what

- `search`, `search_more` and `search_limit_reached`: the search layer (Android: the search repository / paging source; iOS: `OpenAlexSearchRepository` and `SearchViewModel` for the page cap), because it knows the route and the result count.
- Every other event: the view model where the action happens.
- `screen_view`: where the crash `screen` key is set (Android: the NavController listener; iOS: RootView and each screen's `onAppear`).
- User properties: at launch, next to the crash keys; `has_own_key` again when the key changes in Settings.

### Closed lists

Events, parameters and their values are closed enums. A caller can't attach free text; adding one is a reviewed code change.

## 4. What is sent

| Event | Parameters (values) | Sent when |
|---|---|---|
| `search` | `kind`: `keyword` / `doi` / `arxiv` / `link`; `has_filters`: `yes` / `no`; `route`: `user` / `shared` / `keyless`; `results_bucket`: `0` / `1-25` / `26-200` / `200+` | a keyword search's first page arrives, or an id lookup finishes (`results_bucket` `0` or `1-25` for lookups) |
| `search_more` | `page`: `2`…`40` | a further page of a keyword search arrives |
| `search_limit_reached` | `kind`: `daily` / `page_cap` | Search shows the daily-limit state, or a search reaches the page cap |
| `paper_saved` | `from`: `search` / `lookup` / `share` | a paper is saved |
| `paper_removed` | none | a paper is removed (after Undo's window) |
| `note_edited` | none | a note changes; at most once per paper per app session |
| `collection_created` | none | a collection is created |
| `paper_added_to_collection` | none | a paper is added to a collection |
| `export` | `format`: `bibtex` / `backup`; `with_pdfs`: `yes` / `no` | an export succeeds |
| `restore` | `result`: `ok` / `failed` | a restore ends (not on cancel) |
| `pdf_opened` | `source`: `downloaded` / `attached` | the reader opens a PDF |
| `pdf_downloaded` | `result`: `ok` / `failed` | a PDF download ends (not on cancel) |
| `screen_view` | `screen`: the crash `screen` values (`library`, `search`, `details`, `reader`, `settings`, `restore`, `export`) | the visible screen changes |

User properties: `library_size_bucket` (`0`, `1-50`, `51-500`, `501-5000`, `5000+`, as for crashes), `language` (`en` / `ar` / `system`), `has_own_key` (`yes` / `no`).

- `route` is the route the first page actually used. Until Android's quota protection lands, Android sends `user` with a personal key and `shared` otherwise; the quota PR fills in the real value.
- Firebase's automatic events (`first_open`, `session_start`, `app_update`, …) stay on: they give active users, sessions and retention. `screen_view` is sent manually; automatic screen reporting is off.
- Never sent: titles, DOIs, OpenAlex ids, search text, notes, collection names, file names or paths, URLs, the API key, error messages.

## 5. Firebase setup (minimised)

### Both platforms
- Collection disabled until the app decides: Android manifest `firebase_analytics_collection_enabled=false`; iOS Info.plist `FIREBASE_ANALYTICS_COLLECTION_ENABLED=NO`.
- No advertising id: Android `google_analytics_adid_collection_enabled=false`; iOS doesn't link `AdSupport`/`AppTrackingTransparency` and sets `GOOGLE_ANALYTICS_IDFV_COLLECTION_ENABLED=NO`.
- No ad personalization: `google_analytics_default_allow_ad_personalization_signals=false` (Android) / `GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS=NO` (iOS).
- Automatic screen reporting off: `google_analytics_automatic_screen_reporting_enabled=false` / `FirebaseAutomaticScreenReportingEnabled=NO`.
- Consent at launch: `analytics_storage` granted, `ad_storage`, `ad_user_data` and `ad_personalization` denied.

### Android
- `firebase-analytics` from the existing Firebase BoM, in `:app` only.

### iOS
- `FirebaseAnalytics` product from the `firebase-ios-sdk` package the crash-reporting work adds, in the app target only (not the Share Extension).

### Console (the user's part; into `docs/release.md`)
- Google Analytics enabled for `hashiya-research`, linked to a new GA4 property; data retention 2 months; Google signals off; no Ads links.

## 6. Consent

- **Settings:** the Privacy section gains a second switch, **Share usage statistics**, on by default, with the footer "Anonymous counts of how features are used help decide what to improve. Never your papers, notes or searches." English and Arabic.
- **Storage:** Android DataStore `analyticsEnabled` (default true); iOS app `UserDefaults` (default true).
- **At launch:** `setEnabled(isReleaseBuild && preference)`, then consent and user properties.
- **Turning it off:** `setAnalyticsCollectionEnabled(false)` and `resetAnalyticsData()` (clears the app-instance id and unsent events). **Turning it on** resumes from then with a new id.
- Debug builds, unit tests, UI tests and snapshot tests never collect.
- Independent of **Send crash reports**: either can be on without the other.

## 7. Privacy and store answers

- **Play → Data safety:** add App activity → App interactions, and Device or other IDs for Analytics (already declared for crash logs); collected, not shared, encrypted in transit, optional; purpose Analytics.
- **App Store → App Privacy:** add Product Interaction and Device ID for Analytics; not linked to identity; not used for tracking.
- **`PrivacyInfo.xcprivacy` (app):** add `NSPrivacyCollectedDataTypeProductInteraction` and `NSPrivacyCollectedDataTypeDeviceID` (purpose `NSPrivacyCollectedDataTypePurposeAnalytics`, not linked, no tracking). `NSPrivacyTracking` stays false; no tracking domains. Share Extension unchanged.
- **Privacy policy:** a "Usage statistics" section (English and Arabic) in the policy update (FadyFouad/Hashiya-Privacy-Policy PR #1): what is counted, what never is, the switch, Google as processor, 2-month retention.
- **`docs/store/metadata.md`:** updated answers.

## 8. Testing

### Unit tests (fake analytics)
- The switch defaults on; turning it off calls `setEnabled(false)` (and the Firebase implementation resets data); debug builds never enable collection.
- Each event fires once, from the right place, with the right parameters — e.g. a filtered keyword search with 48,210 results sends `search(keyword, yes, shared, 200+)`; a DOI lookup sends `kind: doi`; reaching the page cap sends `search_limit_reached(page_cap)`; `note_edited` once per paper per session; `restore` and `pdf_downloaded` send nothing on cancel.
- A search for a title and a note containing a DOI produce events containing neither string.
- Result-bucket and library-size-bucket boundaries; user properties at launch and `has_own_key` after a key change.
- The Firebase mapping: every event and parameter maps to the names in §4 (a table-driven test in the app target, with a fake logging function).

### UI and snapshot tests
- Settings' Privacy section with both switches — English and Arabic, light and dark.
- A UI test: turn Share usage statistics off, relaunch, it stays off.

### Manual checks (release builds)
- Firebase DebugView (Android: `adb shell setprop debug.firebase.analytics.app com.etatech.hashiya`; iOS: `-FIRDebugEnabled` in a TestFlight build): events arrive with only the listed parameters; no advertising id.
- With the switch off, nothing new arrives.

## 9. Delivery

1. **Android analytics:** `:core:analytics`, Firebase Analytics in `:app`, the switch, the events, Data safety answers.
2. **Android OpenAlex quota protection** (its own plan, from the quota spec): also reports the real `route`.
3. **iOS crash reporting + analytics:** one plan and PR — Firebase in the app target, `HashiyaDiagnostics`, the Privacy section with both switches, crash non-fatals, the events, privacy manifest, App Store answers.
4. **Policy:** the "Usage statistics" section joins PR #1 before the first release with analytics.
