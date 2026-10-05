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
| Consent | On by default, with its own **Share usage statistics** switch in Settings. Kept after review (§7.1): asking first in the EU/UK, or asking everyone, was offered and declined. |
| Service | Firebase Analytics (GA4), in the existing project `hashiya-research`, minimised (§5). |
| Content | A closed list of events and enum-valued parameters (§4). Nothing a user typed or read. |
| Research areas | Keyword searches carry a coarse research `category`, derived on the device from the OpenAlex topics of the results — never from the query text, never by an external service (§4.1). |
| Delivery | Android analytics → Android OpenAlex quota → iOS crash reporting + analytics in one plan. |

## 2. Goals and non-goals

### Goals

1. Answer, from the Firebase console: how many people search, save, take notes, use collections, export and open PDFs; the search → save funnel; searches per user per day by route; how often the page cap and the daily limit are hit; how many users bring their own key; which research areas people search most.
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

- `search`, `search_more` and `search_limit_reached`: the search layer (Android: the search repository / paging source; iOS: `OpenAlexSearchRepository` and `SearchViewModel` for the page cap), because it knows the route, the result count and the first page's topics (for `category`, §4.1).
- The category classifier is a pure function in the analytics module (`:core:analytics` / `HashiyaDiagnostics`) taking plain subfield, field and domain numbers, so it is tested without network types.
- Every other event: the view model where the action happens.
- `screen_view`: where the crash `screen` key is set (Android: the NavController listener; iOS: RootView and each screen's `onAppear`).
- User properties: at launch, next to the crash keys; `has_own_key` again when the key changes in Settings.

### Closed lists

Events, parameters and their values are closed enums. A caller can't attach free text; adding one is a reviewed code change.

## 4. What is sent

| Event | Parameters (values) | Sent when |
|---|---|---|
| `search` | `kind`: `keyword` / `doi` / `arxiv` / `link`; `has_filters`: `yes` / `no`; `route`: `user` / `shared` / `keyless`; `results_bucket`: `0` / `1-25` / `26-200` / `200+`; `category`: §4.1 (keyword searches only) | a keyword search's first page arrives, or an id lookup finishes (`results_bucket` `0` or `1-25` for lookups) |
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

### 4.1 Research category

Answers "which research areas do people search most?" without the query leaving the device.

**Source.** Each work's `primary_topic` from OpenAlex: its subfield, field and domain ids (e.g. `https://openalex.org/subfields/1707`, `…/fields/17`, `…/domains/3`). The search request's `select` gains `primary_topic`; nothing else is requested and nothing is stored in the library. The query text is never classified, and no external service or model is involved: the mapping is a fixed table in the app.

**Taxonomy** (closed enum; the value sent is the left column):

| `category` | OpenAlex source |
|---|---|
| `ai` | subfield 1702 Artificial Intelligence |
| `computer_vision` | subfield 1707 Computer Vision and Pattern Recognition |
| `theory` | subfield 1703 Computational Theory and Mathematics |
| `networks` | subfield 1705 Computer Networks and Communications |
| `systems` | subfield 1708 Hardware and Architecture |
| `software` | subfield 1712 Software |
| `hci` | subfield 1709 Human-Computer Interaction |
| `information_systems` | subfield 1710 Information Systems |
| `graphics` | subfield 1704 Computer Graphics and Computer-Aided Design |
| `signal_processing` | subfield 1711 Signal Processing |
| `cs_other` | any other subfield of field 17 Computer Science (e.g. 1706 Computer Science Applications) |
| `mathematics` | field 26 Mathematics |
| `engineering` | field 22 Engineering |
| `physical_sciences` | any other field in domain 3 Physical Sciences |
| `life_sciences` | domain 1 Life Sciences |
| `social_sciences` | domain 2 Social Sciences |
| `health_sciences` | domain 4 Health Sciences |
| `unknown` | none of the above, no topic, or no clear winner |

Rows are checked top to bottom (subfield, then field, then domain). An id that isn't a URL ending in a number, or a number not in the table, falls through to the next level and finally to `unknown`, so a change to OpenAlex's taxonomy degrades to `unknown`, never to a wrong guess or a crash.

**Rule.** From the first page of a keyword search, take the first 10 results that have a `primary_topic`, in OpenAlex's order, and map each to a category. If at least 3 results were mapped and one category has the most votes with at least 40% of them (no tie for first), send it; otherwise send `unknown`. A search with no results sends `unknown`. Further pages don't change it. DOI, arXiv and link lookups don't send `category`.

**Why this is private.** The value is one of 18 coarse areas describing what OpenAlex returned, not what was typed; a rare query and a common one in the same area are indistinguishable. Paper ids, topic names, titles and the query never reach the analytics layer: the classifier gets only subfield, field and domain numbers, and returns the enum.

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
- **Privacy policy:** a "Usage statistics" section (English and Arabic) in the policy update (FadyFouad/Hashiya-Privacy-Policy PR #1): what is counted, what never is, the switch, Google as processor, 2-month retention. It says searches are counted with a broad research area worked out on the device from the results (e.g. "artificial intelligence"), and that search text is never sent.
- **`docs/store/metadata.md`:** updated answers.

### 7.1 The live policy and the release gate

The published policy (https://fadyfouad.github.io/Hashiya-Privacy-Policy/) currently says "Hashiya has no account, no usage analytics, no ads and no tracking" and promises: "If the app ever starts collecting data, we'll say so here before that version is released." So:

- The policy update (a new PR in FadyFouad/Hashiya-Privacy-Policy) removes "no usage analytics", keeps "no ads and no tracking" (true: no advertising id, ads signals denied, nothing linked to Ads), and adds the "Usage statistics" section in English and Arabic, with a new effective date.
- It must be merged **before** the first build with analytics reaches any user (TestFlight external testers or a store release). Each platform's PR carries this as a release-gate checkbox, and `docs/release.md` lists it.
- **Accepted risk:** analytics are on by default everywhere. In the EU/UK (ePrivacy/GDPR) and under consent-based laws such as Saudi Arabia's and Egypt's data-protection laws, analytics using an on-device identifier may require consent first. The user chose on-by-default knowing this; the switch, the minimised setup (§5) and the policy disclosure reduce but don't remove the risk. Revisit if the app is promoted in those regions or a regulator or store raises it.

## 8. Testing

### Unit tests (fake analytics)
- The switch defaults on; turning it off calls `setEnabled(false)` (and the Firebase implementation resets data); debug builds never enable collection.
- Each event fires once, from the right place, with the right parameters — e.g. a filtered keyword search with 48,210 results sends `search(keyword, yes, shared, 200+)`; a DOI lookup sends `kind: doi`; reaching the page cap sends `search_limit_reached(page_cap)`; `note_edited` once per paper per session; `restore` and `pdf_downloaded` send nothing on cancel.
- A search for a title and a note containing a DOI produce events containing neither string.
- Category classifier (pure, table-driven): every subfield in §4.1 maps to its value; another field-17 subfield gives `cs_other`; fields 26 and 22; each domain; malformed or unknown ids fall through to `unknown`; the vote — fewer than 3 mapped results, a tie for first, a winner under 40%, exactly 40%, results without a topic skipped, only the first 10 counted, no results.
- The search layer sends `category` for keyword searches only, computed from the first page; the search request selects `primary_topic`; a work without one still parses.
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
