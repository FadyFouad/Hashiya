# OpenAlex Quota Protection (Android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Protect the shared OpenAlex budget on Android exactly as iOS does (PR #36): route requests without a personal key through a shared route (built-in key or a configurable proxy) under a per-device daily cap, then no key, then a "Daily search limit reached" state with the reset time; cache searches for a day; cap pages per search; and report the real `route` and `search_limit_reached` analytics events.

**Architecture:** In `:core:network`, an OkHttp `QuotaInterceptor` replaces `ApiKeyInterceptor`: it picks the route from `OpenAlexQuota` (cap count and used-up marks in a private `SharedPreferences` file), retries on the next route after a used-up 429, waits out per-second 429s, falls back from a failing proxy, and tags each response with the route it used. `RetrofitOpenAlexDataSource` answers metered calls from a disk `SearchCache` (only bodies that decoded) and exposes the route. `OpenAlexLimits` come from `app-config.json`'s `openAlex` section, fetched with the existing update check. The search layer stops at the page cap and reports it; Search shows the daily-limit state and a cap footer; Settings links to free personal keys.

**Tech Stack:** Kotlin, OkHttp 4/5 interceptors, Retrofit, kotlinx.serialization, Paging 3, Compose, Hilt, JUnit 4, MockWebServer, Robolectric/Roborazzi.

**Spec:** `docs/superpowers/specs/2026-10-05-openalex-quota-design.md`. iOS reference (merged, reviewed): `ios/HashiyaKit/Sources/HashiyaNetwork/{OpenAlexLimits,OpenAlexQuota,SearchCache,OpenAlexHTTP}.swift`.

**Starting point:** branch `feat/android-quota` from `feat/android-analytics` (PR #40). **Rebase onto `main` as soon as #40 merges** (and before opening this PR), so the PR never targets #40's branch.

## Global Constraints

- Metered calls: `GET works?search=` (`searchWorks`) and `GET works?filter=` (`findWorks`). Lookups (`getWork`, `getWorkLocations`) are never metered, cached or blocked.
- Routes for metered calls: User (personal key) → Shared → Keyless → Out. With a personal key, only User. Shared = `baseUrl` with no key if set, else api.openalex.org with the built-in key; the built-in key never goes to `baseUrl`; user-key and keyless requests never go to `baseUrl`.
- 429 with `X-RateLimit-Remaining` ≤ 0 → mark that route used up until now + `X-RateLimit-Reset` seconds (next midnight UTC if missing, non-finite, negative or over 2 days), stored across restarts; retry once on the next route.
- 429 with budget left (or no readable `Remaining`) → wait `Retry-After` seconds capped at 3 (1 if absent or non-finite), retry once on the same route, then the rate-limited error.
- Proxy unreachable or 5xx → that request moves to Keyless; the proxy isn't marked. If every route is gone but `meteredRoute()` still isn't null (proxy failed, keyless used up) → HTTP 503, not the daily limit.
- Cap: metered calls sent on Shared, counted when sent, per UTC day; cached answers never count. Header values that parse as NaN/Infinity are ignored.
- Config `openAlex`: `dailyDeviceCalls` 0–1000 (default 60; 0 = Shared off for metered), `maxPagesPerQuery` 1–40 (default 8), `baseUrl` absolute `https://` or null; each field validated alone; last valid kept; a failed fetch keeps the stored values.
- Cache: search and filter-list bodies that decoded, keyed without `api_key` or host, 24 hours, 5 MB, least recently used removed first, in `cacheDir`; never backed up (cacheDir isn't in the backup rules).
- Quota state lives in `SharedPreferences` file `openalex_quota` (not in `datastore/`, so not in Auto Backup or device transfer).
- Analytics (from #40): `search.route` is now `user` / `shared` / `keyless` / `cached` from the actual response; `search_limit_reached(daily)` when Search shows the daily-limit state, `search_limit_reached(page_cap)` when a search reaches the cap. Nothing else changes.
- Copy exactly as in Tasks 4, 6 and 7, English and Arabic; never "anonymous", never "isn't linked to you".
- Commits: author `Fady <fady.fouad.a@gmail.com>`; no AI attribution. Spotless must pass.

## Rulings carried from iOS

- No pull-to-refresh on Android Search either; Retry follows an error, which is never cached — nothing bypasses the cache.
- "Open Settings" opens Settings (the API key section is first); no scrolling added.
- An undecodable 2xx isn't cached (iOS final-review fix).
- A failing proxy with keyless used up reports 503, not the daily limit (iOS final-review fix).
- A daily limit on page 2+ shows the limit message and Open Settings in the list footer, not the generic append error (iOS final-review fix).

## Review Focus

1. A device east of UTC near local midnight (00:30 in UTC+3): the cap resets at UTC midnight, not local midnight — Task 2 test.
2. `X-RateLimit-Remaining` of `0.0` or `-1` is used up; `abc`, `nan` and `inf` are per-second (Retry-After `nan` waits 1 s) — Task 5 tests.
3. A proxy `baseUrl` with a path prefix (`https://proxy.example/openalex`) receives `/openalex/works` — Task 5 test.
4. Cache keys ignore `api_key` and the host but differ for cursor, filter and path — Task 3 test.
5. A search cancelled while waiting to retry after a per-second 429 ends without a result and marks nothing — Task 5 test.

## How to run tests

From `android/`: `./gradlew :core:network:testDebugUnitTest`, `:core:data:testDebugUnitTest`, `:feature:search:testDebugUnitTest`, `:feature:settings:testDebugUnitTest`, `:app:testDebugUnitTest` (`--tests '*Name*'` for one class). Before committing: `./gradlew spotlessApply spotlessCheck`. Roborazzi baselines are recorded on Linux CI (Task 9). The disk is nearly full: never run `clean` across the project.

---

### Task 1: The `openAlex` limits and their store

**Files:**
- Create: `android/core/network/src/main/java/com/etatech/hashiya/core/network/quota/OpenAlexLimits.kt`
- Create: `android/core/network/src/main/java/com/etatech/hashiya/core/network/quota/QuotaPreferences.kt`
- Modify: `android/core/network/src/main/java/com/etatech/hashiya/core/network/AppConfigDataSource.kt` (`fetch(): RemoteAppConfig` with `android` + `openAlex`; keep `androidConfig()` as a thin wrapper or replace its callers)
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/repository/AppUpdateRepository.kt` (save the limits it fetched)
- Test: `android/core/network/src/test/java/com/etatech/hashiya/core/network/quota/OpenAlexLimitsTest.kt`, `AppConfigDataSourceTest.kt`, `core/data/src/test/.../AppUpdateRepositoryTest.kt`

**Interfaces (produced):**
- `data class OpenAlexLimits(val dailyDeviceCalls: Int, val maxPagesPerQuery: Int, val baseUrl: HttpUrl?)` with `companion { val Defaults = OpenAlexLimits(60, 8, null); fun parse(section: JsonElement?): OpenAlexLimits }`
- `interface QuotaPreferences { var limits: OpenAlexLimits; var sharedCallsDay: Long; var sharedCalls: Int; var sharedUsedUpUntil: Long; var keylessUsedUpUntil: Long }` (epoch millis) and `class SharedPreferencesQuotaPreferences(context: Context) : QuotaPreferences` (file `openalex_quota`, `commit()` is not needed — `apply()`), plus `class InMemoryQuotaPreferences : QuotaPreferences` for tests (in `:core:network` test fixtures or `:core:testing`).
- `data class RemoteAppConfig(val android: NetworkPlatformConfig?, val openAlex: OpenAlexLimits)`; `AppConfigDataSource.fetch(): RemoteAppConfig`.

- [ ] **Step 1: Write the failing tests**

`OpenAlexLimitsTest.kt` (mirror the iOS cases):

```kotlin
class OpenAlexLimitsTest {
    private fun parse(json: String) = OpenAlexLimits.parse(OpenAlexJson.parseToJsonElement(json))

    @Test fun defaults() = assertEquals(OpenAlexLimits(60, 8, null), OpenAlexLimits.Defaults)

    @Test fun readsEveryValidField() = assertEquals(
        OpenAlexLimits(0, 40, "https://proxy.example/openalex".toHttpUrl()),
        parse("""{"dailyDeviceCalls":0,"maxPagesPerQuery":40,"baseUrl":"https://proxy.example/openalex"}""")
    )

    @Test fun anInvalidCallCountFallsBackAlone() {
        for (value in listOf("-1", "1001", "\"60\"", "12.5", "true", "null")) {
            val limits = parse("""{"dailyDeviceCalls":$value,"maxPagesPerQuery":3}""")
            assertEquals(value, 60, limits.dailyDeviceCalls)
            assertEquals(value, 3, limits.maxPagesPerQuery)
        }
    }

    @Test fun anInvalidPageLimitFallsBack() {
        for (value in listOf("0", "41", "\"8\"")) assertEquals(8, parse("""{"maxPagesPerQuery":$value}""").maxPagesPerQuery)
    }

    @Test fun aBaseUrlThatIsNotHttpsIsIgnored() {
        for (value in listOf("\"http://proxy.example\"", "\"proxy.example\"", "\"https://\"", "5", "null")) {
            assertNull(value, parse("""{"baseUrl":$value}""").baseUrl)
        }
    }

    @Test fun aSectionThatIsNotAnObjectMeansAllDefaults() {
        for (json in listOf("[]", "5", "\"x\"", "null")) assertEquals(OpenAlexLimits.Defaults, parse(json))
    }
}
```

`AppConfigDataSourceTest.kt` (MockWebServer, like the existing tests): `readsTheOpenAlexSection`, `aMissingOpenAlexSectionMeansDefaults`, `aBadOpenAlexFieldDoesNotSpoilTheAndroidEntry` (`{"android":{"minimumVersionCode":2},"openAlex":{"dailyDeviceCalls":"lots"}}` → android entry kept, limits = Defaults); existing tests keep passing (through `fetch().android`).

`AppUpdateRepositoryTest.kt`: `savesTheOpenAlexLimitsItFetched` (fake data source returns limits `7, 2, null` → `InMemoryQuotaPreferences.limits` equals them) and `aFailedFetchKeepsTheStoredLimits`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:network:testDebugUnitTest :core:data:testDebugUnitTest`. Expected: compilation failures.

- [ ] **Step 3: Implement**

`OpenAlexLimits.kt`:

```kotlin
package com.etatech.hashiya.core.network.quota

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The `openAlex` section of the remote config: how this device may use the shared OpenAlex budget. */
data class OpenAlexLimits(val dailyDeviceCalls: Int, val maxPagesPerQuery: Int, val baseUrl: HttpUrl?) {
    companion object {
        val Defaults = OpenAlexLimits(dailyDeviceCalls = 60, maxPagesPerQuery = 8, baseUrl = null)

        /** Field by field: a missing, out-of-range, wrong-type or non-https value falls back to its default. */
        fun parse(section: JsonElement?): OpenAlexLimits {
            val fields = section as? JsonObject ?: return Defaults
            return OpenAlexLimits(
                dailyDeviceCalls = wholeNumber(fields["dailyDeviceCalls"], 0..1000) ?: Defaults.dailyDeviceCalls,
                maxPagesPerQuery = wholeNumber(fields["maxPagesPerQuery"], 1..40) ?: Defaults.maxPagesPerQuery,
                baseUrl = httpsUrl(fields["baseUrl"]),
            )
        }

        private fun wholeNumber(value: JsonElement?, range: IntRange): Int? {
            val primitive = value as? JsonPrimitive ?: return null
            if (primitive.isString || primitive.booleanOrNull != null) return null
            val number = primitive.doubleOrNull ?: return null
            if (number != Math.rint(number) || number < range.first || number > range.last) return null
            return number.toInt()
        }

        private fun httpsUrl(value: JsonElement?): HttpUrl? {
            val primitive = value as? JsonPrimitive ?: return null
            if (!primitive.isString) return null
            val url = primitive.content.toHttpUrlOrNull() ?: return null
            return url.takeIf { it.isHttps && it.host.isNotEmpty() }
        }
    }
}
```

`QuotaPreferences.kt` — the interface above; `SharedPreferencesQuotaPreferences` stores the limits as three keys (`limits.dailyDeviceCalls`, `limits.maxPagesPerQuery`, `limits.baseUrl`; absent → Defaults) and the counters as `Long`/`Int` keys `sharedCallsDay`, `sharedCalls`, `sharedUsedUpUntil`, `keylessUsedUpUntil`. Reads/writes are synchronous (`getX`, `edit { putX }` via `apply()`); `OpenAlexQuota` (Task 2) serializes access. Bind it in `NetworkModule` as a `@Singleton` from `@ApplicationContext`.

`AppConfigDataSource`: parse the body once into a `JsonObject`; decode `android` with the existing `RawPlatformConfig` logic; `openAlex = OpenAlexLimits.parse(json["openAlex"])`. Malformed JSON keeps throwing `MalformedResponse`.

`ConfigAppUpdateRepository`: inject `QuotaPreferences`; after a successful `fetch()`, `quotaPreferences.limits = config.openAlex` before checking the minimum version. A failed fetch changes nothing.

- [ ] **Step 4: Run the tests to verify they pass**, then `spotlessApply spotlessCheck`.

- [ ] **Step 5: Commit** — `feat(android): read the openAlex limits from the remote config`

---

### Task 2: `OpenAlexQuota` — routes, the device cap and used-up budgets

**Files:**
- Create: `android/core/network/src/main/java/com/etatech/hashiya/core/network/quota/OpenAlexQuota.kt`
- Test: `android/core/network/src/test/java/com/etatech/hashiya/core/network/quota/OpenAlexQuotaTest.kt`

**Interfaces:**
- Consumes: `QuotaPreferences`, `OpenAlexLimits` (Task 1).
- Produces: `enum class OpenAlexRoute { Shared, Keyless }`; `class OpenAlexQuota(prefs: QuotaPreferences, hasBuiltInKey: Boolean, now: () -> Long = System::currentTimeMillis)` with `val limits`, `fun meteredRoute(): OpenAlexRoute?`, `fun lookupRoute(): OpenAlexRoute`, `fun routeAfter(route: OpenAlexRoute, metered: Boolean): OpenAlexRoute?`, `fun recordSharedCall()`, `fun markUsedUp(route: OpenAlexRoute, resetInSeconds: Double?)`, `fun nextAvailable(): Long` (epoch millis).

- [ ] **Step 1: Write the failing tests** — port `ios/HashiyaKit/Tests/HashiyaNetworkTests/OpenAlexQuotaTests.swift` one to one (each test, same values, millis instead of `Date`), with `InMemoryQuotaPreferences` and a mutable clock starting at `2026-10-05T20:00:00Z`:
  - starts on Shared; without a built-in key or proxy everything is Keyless; a proxy makes Shared without a built-in key;
  - the cap moves metered calls to Keyless but not lookups; a zero cap turns Shared off for metered calls;
  - the count starts again at UTC midnight, not local midnight (set the clock to `2026-10-05T21:30:00Z` → still capped; `2026-10-06T00:00:01Z` → Shared);
  - a used-up Shared sends everything Keyless until its reset; both used up → `meteredRoute()` null, `lookupRoute()` Keyless, `nextAvailable()` the earliest reset;
  - the cap counts as used up until UTC midnight for `nextAvailable`;
  - a missing, negative or over-2-days reset means next UTC midnight;
  - marks and counts survive a new instance on the same preferences;
  - `routeAfter` cases; new limits apply to the next route.

- [ ] **Step 2: Run** `./gradlew :core:network:testDebugUnitTest --tests '*OpenAlexQuotaTest*'` — fails to compile.

- [ ] **Step 3: Implement**

```kotlin
package com.etatech.hashiya.core.network.quota

/** How a request without the user's key goes out. */
enum class OpenAlexRoute { Shared, Keyless }

/**
 * Chooses the route for requests without a user key and remembers, in [prefs]: the metered calls sent on the shared
 * route today (by UTC day, as OpenAlex resets at midnight UTC), and until when OpenAlex said each budget is used up.
 * Nothing here leaves the device.
 */
class OpenAlexQuota(
    private val prefs: QuotaPreferences,
    private val hasBuiltInKey: Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val limits: OpenAlexLimits get() = prefs.limits

    @Synchronized
    fun meteredRoute(): OpenAlexRoute? {
        val time = now()
        if (sharedOpensForMetered(time) <= time) return OpenAlexRoute.Shared
        return if (prefs.keylessUsedUpUntil <= time) OpenAlexRoute.Keyless else null
    }

    @Synchronized
    fun lookupRoute(): OpenAlexRoute =
        if (sharedConfigured && prefs.sharedUsedUpUntil <= now()) OpenAlexRoute.Shared else OpenAlexRoute.Keyless

    @Synchronized
    fun routeAfter(route: OpenAlexRoute, metered: Boolean): OpenAlexRoute? = when {
        route != OpenAlexRoute.Shared -> null
        !metered -> OpenAlexRoute.Keyless
        prefs.keylessUsedUpUntil <= now() -> OpenAlexRoute.Keyless
        else -> null
    }

    @Synchronized
    fun recordSharedCall() {
        val today = utcDay(now())
        val calls = if (prefs.sharedCallsDay == today) prefs.sharedCalls else 0
        prefs.sharedCallsDay = today
        prefs.sharedCalls = calls + 1
    }

    @Synchronized
    fun markUsedUp(route: OpenAlexRoute, resetInSeconds: Double?) {
        val time = now()
        val until = resetInSeconds
            ?.takeIf { it.isFinite() && it >= 0 && it <= 2 * DAY_SECONDS }
            ?.let { time + (it * 1000).toLong() }
            ?: nextUtcMidnight(time)
        if (route == OpenAlexRoute.Shared) prefs.sharedUsedUpUntil = until else prefs.keylessUsedUpUntil = until
    }

    /** When a search can go out again: the earlier of the shared and keyless routes opening. */
    @Synchronized
    fun nextAvailable(): Long {
        val time = now()
        return maxOf(minOf(sharedOpensForMetered(time), prefs.keylessUsedUpUntil), time)
    }

    private val sharedConfigured get() = hasBuiltInKey || prefs.limits.baseUrl != null

    /** [Long.MAX_VALUE] when the shared route is off for metered calls. */
    private fun sharedOpensForMetered(time: Long): Long {
        val cap = prefs.limits.dailyDeviceCalls
        if (!sharedConfigured || cap <= 0) return Long.MAX_VALUE
        val calls = if (prefs.sharedCallsDay == utcDay(time)) prefs.sharedCalls else 0
        val capOpens = if (calls >= cap) nextUtcMidnight(time) else time
        return maxOf(capOpens, prefs.sharedUsedUpUntil)
    }

    private companion object {
        const val DAY_SECONDS = 86_400.0
        const val DAY_MILLIS = 86_400_000L
        fun utcDay(time: Long) = Math.floorDiv(time, DAY_MILLIS)
        fun nextUtcMidnight(time: Long) = (utcDay(time) + 1) * DAY_MILLIS
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**, `spotlessApply spotlessCheck`.

- [ ] **Step 5: Commit** — `feat(android): choose the OpenAlex route under a per-device daily cap`

---

### Task 3: `SearchCache`

**Files:**
- Create: `android/core/network/src/main/java/com/etatech/hashiya/core/network/quota/SearchCache.kt`
- Test: `android/core/network/src/test/java/com/etatech/hashiya/core/network/quota/SearchCacheTest.kt`

**Interfaces:**
- Produces: `class SearchCache(directory: File, maxBytes: Long = 5_000_000, lifetimeMillis: Long = 86_400_000, now: () -> Long = System::currentTimeMillis)` with `fun read(key: String): String?`, `fun write(key: String, body: String)`, `companion fun key(path: String, query: List<Pair<String, String>>): String`.

- [ ] **Step 1: Write the failing tests** — port `ios/HashiyaKit/Tests/HashiyaNetworkTests/SearchCacheTests.swift` (a `TemporaryFolder` rule; a mutable clock): returns what was stored; expires after 24 h (86 399 s fresh, 86 401 s gone); removes the least recently used over the size limit (three ~100-byte entries, limit 250, the middle one read last survives); survives a new instance; the key ignores `api_key` and parameter order; the key differs for cursor, filter and path.

- [ ] **Step 2: Run** — fails to compile.

- [ ] **Step 3: Implement** — like iOS: file name = SHA-256 hex of the key (`MessageDigest`); content = 8-byte big-endian saved-at millis + the UTF-8 body; `read` rejects age < 0 or ≥ lifetime (deleting the file) and touches `setLastModified(now())`; `write` writes atomically (temp file + rename), sets `lastModified`, then trims oldest-`lastModified` files until the total ≤ `maxBytes`; all methods `@Synchronized`; every I/O failure is a miss, never an exception. `key()` drops `api_key`, sorts by name then value, joins `name=value` with `&` after `path?`.

- [ ] **Step 4: Run the tests to verify they pass**, `spotlessApply spotlessCheck`.

- [ ] **Step 5: Commit** — `feat(android): keep OpenAlex search pages on disk for a day`

---

### Task 4: The "Daily search limit reached" error

**Files:**
- Modify: `android/core/model/src/main/kotlin/com/etatech/hashiya/core/model/SearchError.kt` (`data class DailyLimit(val resetAtMillis: Long) : SearchError`)
- Modify: `android/core/network/src/main/java/com/etatech/hashiya/core/network/NetworkFailure.kt` (`data class DailyLimit(val resetAtMillis: Long) : NetworkFailure`; `class DailyLimitException(val resetAtMillis: Long) : IOException("dailyLimit")` mapped to it in `toNetworkException()` before the generic `IOException` branch)
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/search/SearchErrorMapping.kt`
- Modify: `android/feature/search/src/main/java/com/etatech/hashiya/feature/search/components/SearchStates.kt`, `SearchScreen.kt` (append footer), `res/values/strings.xml`, `res/values-ar/strings.xml`
- Test: `core/data/.../search/SearchErrorMappingTest.kt`, `core/network/.../NetworkFailureTest` (or the data-source tests), `feature/search/.../SearchStatesTest`/`SearchScreenTest` (Compose), `SearchScreenshotTest.kt` (new `search_daily_limit` and `search_append_daily_limit` captures)

**Interfaces:**
- Produces: `SearchError.DailyLimit(resetAtMillis)`, `NetworkFailure.DailyLimit(resetAtMillis)`, `DailyLimitException(resetAtMillis)`; `formatResetTime(resetAtMillis: Long, locale: Locale, zone: ZoneId): String` (internal in `:feature:search`).

- [ ] **Step 1: Write the failing tests**
  - `NetworkFailure.DailyLimit(5).asSearchError() == SearchError.DailyLimit(5)`; a `DailyLimitException` becomes `NetworkFailure.DailyLimit`.
  - `formatResetTime(2026-10-06T00:00Z, Locale.US, ZoneId.of("Asia/Riyadh"))` contains `"3:00"` and `"AM"`; in Arabic (`Locale("ar")`) the full message string contains the time wrapped once in U+2068…U+2069.
  - Compose: the daily-limit state shows the title, the message with the time, and an "Open Settings" button that calls `onOpenSettings`; an append error that is `DailyLimit` shows the message and Open Settings in the footer (not "Couldn't load more results").
  - Screenshot tests: `search_daily_limit` (first page) and `search_append_daily_limit` (footer), `arabicText = "تم بلوغ الحد اليومي للبحث"` / `"فتح الإعدادات"`, time zone fixed to `Asia/Riyadh` via the formatter's zone parameter.

- [ ] **Step 2: Run** — fails to compile.

- [ ] **Step 3: Implement**
  - Strings (en / ar):
    - `search_error_daily_limit_title`: "Daily search limit reached" / "تم بلوغ الحد اليومي للبحث"
    - `search_error_daily_limit_message`: "Search will be available again at %1$s. For more searches, add your own free OpenAlex key in Settings." / "سيتوفر البحث مجددًا الساعة ⁨%1$s⁩. لمزيد من عمليات البحث، أضف مفتاح OpenAlex مجانيًا خاصًا بك في الإعدادات."
  - `formatResetTime` uses `DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)` (the app's locale from `LocalConfiguration`, the device zone by default).
  - `SearchErrorState`: `is SearchError.DailyLimit` → the title/message above; `opensSettings` is true for `InvalidUserKey` and `DailyLimit`.
  - `SearchScreen` append footer: when `append` is `LoadState.Error` whose error maps to `SearchError.DailyLimit`, show the message with the time and an Open Settings `TextButton` instead of the generic text + Retry.

- [ ] **Step 4: Run** the affected modules' tests (screenshot verify will fail only for the new captures' missing baselines — say so), `spotlessApply spotlessCheck`.

- [ ] **Step 5: Commit** — `feat(android): explain a reached daily search limit and when it resets`

---

### Task 5: Route every OpenAlex request

**Files:**
- Create: `android/core/network/src/main/java/com/etatech/hashiya/core/network/quota/QuotaInterceptor.kt`
- Modify: `ApiKey.kt` (keep `UserApiKeySource`, `ApiKeyKind`; remove `ApiKeyInterceptor` or reduce it to the user-key path inside `QuotaInterceptor`), `OpenAlexClient.kt`, `di/NetworkModule.kt`, `OpenAlexApi.kt` (`searchWorks`/`findWorks` return `Response<ResponseBody>`), `OpenAlexDataSource.kt`, `OpenAlexLookupDataSource.kt`, `model/NetworkWorks.kt` (`NetworkWorksResponse.route: RequestRoute?` — `@Transient`)
- Test: `android/core/network/src/test/java/com/etatech/hashiya/core/network/quota/QuotaRoutingTest.kt` (MockWebServer), existing `OpenAlexDataSourceTest`/`OpenAlexLookupDataSourceTest` stay green

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces: `enum class RequestRoute { User, Shared, Keyless, Cached }`; `NetworkWorksResponse.route`; `QuotaInterceptor(userApiKeySource, builtInKey: String, quota: OpenAlexQuota, sleep: (Long) -> Unit = Thread::sleep)`; internal response header `X-Hashiya-Route` (set by the interceptor on the response it returns; never sent to a server).

- [ ] **Step 1: Write the failing tests** — port `ios/HashiyaKit/Tests/HashiyaNetworkTests/OpenAlexRoutingTests.swift` (all 20 cases incl. the final-review ones) to MockWebServer; for the proxy, a second MockWebServer whose URL (with a `/openalex` path) is the limits' `baseUrl`; a recording `sleep` lambda instead of `ManualSleeper` (assert the waited millis: 1000 for no `Retry-After` or `nan`, 3000 for `Retry-After: 10`):
  - a shared search sends the built-in key and counts; a used-up shared budget retries once without a key; `Remaining` `0.0`/`-1` are used up; both used up → `DailyLimitException` with the earliest reset; when out, a search sends nothing;
  - a lookup still goes out when search is out and never counts; a filter list is metered;
  - a per-second limit waits and retries once on the same route; a second one is HTTP 429; `abc`/`nan` Remaining are per-second; `Retry-After: nan` waits 1 s; `Reset: nan` → next UTC midnight;
  - cancelling the call during the wait ends with an `IOException` (`Canceled`) and marks nothing (cancel from another thread while `sleep` blocks on a latch);
  - the user key is the only route (429 → `Http(429, usedUserKey = true)`, nothing marked) and never goes to the proxy;
  - a proxy gets shared requests under its path with no key; a proxy 503 or connection failure falls back to keyless without marking it; a failing proxy with keyless used up → HTTP 503, not the daily limit;
  - the route is reported (`User`, `Shared`, `Keyless`); a repeated search is answered from the cache (`Cached`) without counting; failures and undecodable 200s are not cached.

- [ ] **Step 2: Run** — fails.

- [ ] **Step 3: Implement**
  - `QuotaInterceptor.intercept(chain)`: metered = path ends with `/works` and has a `search` or `filter` query parameter (lookups are `/works/{id}`). User key set → add it, proceed, tag `User`. Otherwise loop over routes exactly like iOS `OpenAlexHTTP.send` (see the Swift above in the iOS file): build the URL (Shared + proxy → proxy base + original path + query, no key; Shared without proxy → api + built-in key; Keyless → api, no key), `recordSharedCall()` for metered Shared sends, proceed (catch `IOException` only when the target is the proxy → next route), then on 2xx return the response with header `X-Hashiya-Route`; on 429 read the headers through a `number(name)` helper that returns null for non-finite values; used up → `markUsedUp` + next route (closing the old response); per-second → `sleep(min(retryAfter ?: 1.0, 3.0) * 1000)`, then if `chain.call().isCanceled()` throw `IOException("Canceled")`, retry once; proxy 5xx → next route; otherwise return the response as is (Retrofit turns it into `HttpException`). When routes run out: metered → `meteredRoute() == null` ? throw `DailyLimitException(nextAvailable())` : return a synthetic 503 response; lookups → a synthetic 429 response.
  - `RetrofitOpenAlexDataSource.searchWorks` / `findWorks`: build the cache key from the request parameters (path `/works`, the same query names and values Retrofit sends, without `api_key`); a hit decodes with `OpenAlexJson` and returns `route = Cached`; otherwise call the API (`Response<ResponseBody>`), on non-2xx throw `HttpException`-equivalent → `toNetworkException()`, decode the body string (a `SerializationException` → `MalformedResponse`, not cached), store the raw string only after decoding, and return `response.copy(route = header("X-Hashiya-Route"))`.
  - `NetworkModule`: provide `QuotaPreferences`, `OpenAlexQuota(prefs, hasBuiltInKey = BuildConfig.OPENALEX_API_KEY.isNotBlank())`, `SearchCache(File(context.cacheDir, "openalex-search"))`; the OpenAlex `OkHttpClient` uses `QuotaInterceptor` instead of `ApiKeyInterceptor` (keep the logging interceptor's `api_key` redaction).

- [ ] **Step 4: Run** `:core:network:testDebugUnitTest :core:data:testDebugUnitTest`, `spotlessApply spotlessCheck`.

- [ ] **Step 5: Commit** — `feat(android): route OpenAlex requests through the shared, keyless and user routes`

---

### Task 6: The page cap

**Files:**
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/paging/OpenAlexPagingSource.kt`, `repository/OpenAlexSearchRepository.kt`, `repository/SearchRepository.kt` (`SearchResults.capReached: StateFlow<Int?>` — the number of results shown when the cap stopped paging), `core/testing/.../FakeSearchRepository.kt`
- Modify: `android/feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchScreen.kt` (footer), `SearchViewModel.kt`/UI state as needed, strings en/ar
- Test: `OpenAlexPagingSourceTest`, `OpenAlexSearchRepositoryTest`, `SearchScreenTest`, `SearchScreenshotTest` (`search_page_cap`)

**Interfaces:**
- Consumes: `OpenAlexQuota.limits.maxPagesPerQuery` (inject `OpenAlexQuota` or a `() -> Int` into `OpenAlexSearchRepository`).
- Produces: `SearchResults.capReached: StateFlow<Int?>` (default `MutableStateFlow(null)`).

- [ ] **Step 1: Write the failing tests** — paging stops after `maxPagesPerQuery` pages (`nextKey = null` on the cap page even when OpenAlex has a next cursor) and sets `capReached = maxPages × 25`; a last page before the cap ends normally (`capReached` stays null); skipped duplicate pages count toward the cap (pages fetched, not pages shown); the Compose footer shows "Showing the first 200 results. Refine your search to see more." when `capReached = 200`.

- [ ] **Step 2: Run** — fails.

- [ ] **Step 3: Implement** — the paging source already counts `pages`; when `pages >= maxPages` and a next cursor exists, return `nextKey = null` and call `onCapReached(maxPages * PAGE_SIZE)`. Strings: `search_page_cap` "Showing the first %1$s results. Refine your search to see more." / "تُعرض أول ⁨%1$s⁩ نتيجة. حسِّن بحثك لرؤية المزيد." (format the number with the app locale's `NumberFormat`). The footer shows it when `append is LoadState.NotLoading && append.endOfPaginationReached && capReached != null`.

- [ ] **Step 4: Run** the affected tests, `spotlessApply spotlessCheck`.

- [ ] **Step 5: Commit** — `feat(android): stop a search at the configured page cap`

---

### Task 7: Analytics route and limit events; the Settings footer

**Files:**
- Modify: `android/core/data/.../repository/SearchRepository.kt` + `OpenAlexPagingSource.kt` (`FirstPage.route: SearchRoute?` from `NetworkWorksResponse.route`)
- Modify: `android/feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchViewModel.kt` (use `firstPage.route ?: <user/shared>`; log `SearchLimitReached(Daily)` when the first page or an append fails with `SearchError.DailyLimit`, once per search; `SearchLimitReached(PageCap)` when `capReached` becomes non-null, after the `search` event)
- Modify: `android/feature/settings/src/main/java/com/etatech/hashiya/feature/settings/SettingsScreen.kt` (API key section footer with a link), strings en/ar
- Test: `SearchAnalyticsTest.kt`, `SettingsContentTest.kt`, `SettingsScreenshotTest.kt`

- [ ] **Step 1: Write the failing tests**
  - a search whose first page has `route = Keyless` logs `Search(..., route = Keyless, ...)`; `Cached` likewise;
  - a first-page `DailyLimit` logs exactly `[SearchLimitReached(Daily)]` (no `search`); an append `DailyLimit` adds one `SearchLimitReached(Daily)`;
  - `capReached` → `[Search(...), SearchLimitReached(PageCap)]` in that order, once;
  - Settings shows "A free personal key gives you more daily searches." with a "Get a free key" link to `https://openalex.org/settings/api`.

- [ ] **Step 2: Run** — fails.

- [ ] **Step 3: Implement** — map `RequestRoute` to `SearchRoute` in the data layer (`User→User, Shared→Shared, Keyless→Keyless, Cached→Cached`); the view model prefers the reported route. For the daily limit, observe the paging `LoadState` errors the view model already sees (or a `SearchResults.failure: StateFlow<SearchError?>` fed by the paging source) — log once per search. Strings: `settings_api_key_footer` "A free personal key gives you more daily searches." / "يمنحك مفتاح شخصي مجاني المزيد من عمليات البحث اليومية." and `settings_api_key_get_free` "Get a free key" / "احصل على مفتاح مجاني" (a `TextButton` opening the URL with `LocalUriHandler`).

- [ ] **Step 4: Run** `:feature:search:testDebugUnitTest :feature:settings:testDebugUnitTest`, `spotlessApply spotlessCheck`.

- [ ] **Step 5: Commit** — `feat(android): report the real search route and the search limits`

---

### Task 8: Docs and changelog

- [ ] `docs/release.md` → "OpenAlex quota": say it applies to both apps; Android reads the same `openAlex` section (from the app version with this change), and its quota state lives in the app's private `openalex_quota` preferences (not backed up).
- [ ] `CHANGELOG.md` `[Unreleased]` → Added: "- **Android: searching keeps working when OpenAlex's shared budget runs out.** Searches without a personal OpenAlex key share a daily allowance per device, then fall back to OpenAlex's keyless budget; when both are used up, Search says when it works again. Repeated searches are kept for a day, and Settings links to free personal keys."
- [ ] In the spec §11, mark iOS (#36) and Android (this PR) delivered.
- [ ] Commit — `docs: the OpenAlex quota on Android`

---

### Task 9: Screenshot baselines (controller, after pushing the branch)

- [ ] From `feat/android-quota` (rebased on `main` after #40 merges): `scripts/record-screenshots-on-linux.sh`; look at `search_daily_limit-*`, `search_append_daily_limit-*`, `search_page_cap-*` and the Settings captures in English and Arabic (the time and number read in order inside the Arabic sentence) before committing.
