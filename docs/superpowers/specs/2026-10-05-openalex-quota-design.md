# OpenAlex quota protection — Design

- **Date:** 2026-10-05
- **Status:** Approved in brainstorming; awaiting spec review
- **Scope:** Both platforms. iOS ships first, then Android, each with its own plan and PR. No server is built; the contract for a later one is written down.

## 1. Context

Every install sends its OpenAlex requests with one built-in key (`OPENALEX_API_KEY`, compiled into the app), unless the user has entered their own key in Settings. OpenAlex now bills usage per key ([Authentication & Pricing](https://developers.openalex.org/api-reference/authentication), [Pricing](https://help.openalex.org/access/pricing/)):

| Call | Cost |
|---|---|
| Lookup by id or DOI (`/works/{id}`) | free |
| List + filter (`/works?filter=`) | $0.10 per 1,000 |
| Search (`/works?search=`) | $1 per 1,000 |

A free key has a **$1 daily budget**; no key has **$0.10**. Budgets reset at midnight UTC. Over budget, OpenAlex answers `429 Too Many Requests` with `X-RateLimit-Remaining` and `X-RateLimit-Reset` (seconds until the reset). Paid usage is pay-as-you-go in $1 steps or annual plans from $5,000.

So the built-in key covers about **1,000 search pages a day across all users together**, and it can be extracted from the app. Today the apps search only when a search is submitted (typing never sends a request), load 25 results per page, keep pages in memory for the current query, and show "Too many requests / Try again in a moment" on any 429. There is no daily budget per device, no cache across queries and no fallback.

Both apps already fetch `https://fadyfouad.github.io/Hashiya-Privacy-Policy/app-config.json` at launch for the minimum-version check.

### Decisions made during brainstorming

| Topic | Decision |
|---|---|
| Scale | Unknown. Build it so a server can be added later without an app update. |
| Shared budget used up | Fall back to no key; when that is used up too, explain the limit, show the reset time and link to Settings for a personal key. |
| Fairness | A per-device daily cap on the shared route, set in the remote config. |
| Approach | A remote-controlled request route in each app; a proxy is a config change later. |
| Privacy | Nothing new leaves the device; no install id. |

## 2. Goals and non-goals

### Goals

1. One heavy device can't use up the shared budget for everyone.
2. When the shared budget runs out, search keeps working on the keyless budget, then tells the user plainly when it will work again and how to get more.
3. Repeated searches within a day cost nothing.
4. The cap, the page limit and a proxy can be changed through `app-config.json`, with no app release.

### Non-goals

- Building the proxy (its contract is in §8).
- Accounts, install ids, analytics on search.
- Showing usage numbers to the user.
- Removing the built-in key (possible in a later release once a proxy is live).

## 3. What is metered

| Call | Android | iOS | Metered |
|---|---|---|---|
| Search | `OpenAlexApi.searchWorks` | `OpenAlexSearchClient.searchWorks` | yes |
| Filter list | `OpenAlexApi.findWorks` | `OpenAlexLookupClient.works(filter:perPage:)` | yes |
| Lookup by id | `getWork`, `getWorkLocations` | `work(id:)`, `pdfLocations(openAlexID:)` | no |

Requests to arXiv, PDF hosts and the config file are untouched.

## 4. Request routes

A new component in the network layer (`:core:network` on Android, `HashiyaNetwork` on iOS), named `RequestRoute` here, chooses how each OpenAlex request goes out.

### Metered calls

| Order | Route | Destination | Used when |
|---|---|---|---|
| 1 | **User** | api.openalex.org with the user's key | the user has set a key |
| 2 | **Shared** | `baseUrl` with no key if set; otherwise api.openalex.org with the built-in key | the device is under today's cap, `dailyDeviceCalls` > 0, and the shared route isn't marked used up |
| 3 | **Keyless** | api.openalex.org with no key | the keyless route isn't marked used up |
| 4 | **Out** | no request | — |

With a user key set, only the User route is used: a 429 on it is the user's own limit and shows the existing errors (an invalid key keeps today's message).

### Lookups

Lookups use the User route if a key is set, otherwise Shared (ignoring the cap, which counts metered calls only), and are never blocked by Out. If the shared route is marked used up, a lookup goes keyless; lookups are free, so this only matters if OpenAlex rejects it anyway, which shows today's errors.

### Responses

- **429 with `X-RateLimit-Remaining: 0`:** the route is marked used up until now + `X-RateLimit-Reset` seconds (or the next midnight UTC if the header is missing or unreadable). The mark is stored and survives restarts. The same request is retried once on the next route; the user just sees results.
- **429 with budget left (or no `Remaining` header):** a per-second limit. Wait `Retry-After` seconds, capped at 3, or 1 second if absent; retry once on the same route; if it fails again, show today's "Too many requests / Try again in a moment."
- **Proxy unreachable or 5xx:** this request moves to the Keyless route. The proxy isn't marked used up; the next request tries it again.
- **The built-in key is never sent to `baseUrl`.** User-key and keyless requests never go to `baseUrl`.

### The device cap

- A count of metered calls sent on the Shared route, with the UTC date it belongs to. On a new UTC date the count starts at 0, whatever the device's time zone.
- A call counts when it is sent, whatever the answer. Calls answered from the cache (§6) don't count.
- Stored with the app's preferences (DataStore; `UserDefaults`); excluded from Android Auto Backup and never in the `.hashiya` export.
- Reaching the cap means the Shared route is skipped until the next UTC midnight.

## 5. Remote config

`app-config.json` gains a section read by both platforms:

```json
"openAlex": {
  "dailyDeviceCalls": 60,
  "maxPagesPerQuery": 8,
  "baseUrl": null
}
```

| Field | Meaning | Valid | Default |
|---|---|---|---|
| `dailyDeviceCalls` | metered calls per device per UTC day on the Shared route | whole number 0–1000; 0 turns the Shared route off for metered calls | 60 |
| `maxPagesPerQuery` | pages one search can load (25 results each) | whole number 1–40 | 8 |
| `baseUrl` | proxy for the Shared route | an absolute `https://` URL, or null | null |

- Fetched at launch in the same request as the minimum-version check. The last valid values are stored; a failed fetch uses them, and a first launch without network uses the defaults.
- Each field is validated on its own: a missing, out-of-range, wrong-type or non-https value falls back to its default, and the other fields still apply. A missing or malformed `openAlex` section means all defaults.
- Released apps ignore unknown keys, so the file stays compatible with every version.
- New values apply to requests started after they are read; a running search isn't restarted.

## 6. Search cache

- Search and filter-list responses are stored on disk under a key built from the endpoint and its parameters (search text, filter, sort, per page, cursor, select), never the API key or the destination host.
- Entries expire after **24 hours**. The cache holds at most **5 MB**; the least recently used entries are removed first.
- A request with a fresh entry is answered from the cache: nothing is sent and nothing counts toward the cap. Explicit refresh (pull-to-refresh or the error state's Retry) skips the cache.
- Only successful (2xx) responses are stored.
- Stored in the app's cache directory (Android `cacheDir`, iOS `Caches`), so the system may clear it; not backed up, not exported.

## 7. What users see

### Page cap

When a search has loaded `maxPagesPerQuery` pages, paging stops and the list ends with a footer: **"Showing the first 200 results. Refine your search to see more."** (the number is 25 × `maxPagesPerQuery`).

### Daily limit reached

When a search resolves to the Out route, Search shows a new state in place of "Too many requests":

- **Title:** "Daily search limit reached"
- **Message:** "Search will be available again at 3:00 AM. For more searches, add your own free OpenAlex key in Settings." — the time is the earliest stored reset, in local time and the user's locale.
- **Button:** "Open Settings", which opens Settings at the API key field.

The library, notes, PDFs, collections and opening papers by DOI or link keep working. The keyless fallback is silent.

### Settings

The API key field gains a footer: "A free personal key gives you more daily searches." with a link to OpenAlex's sign-up page.

### Language

All new text in English and Arabic. The reset time uses the locale's digits and clock format; in Arabic, the time is wrapped in directional isolates (U+2068…U+2069) inside the sentence.

## 8. A later proxy (contract only)

When usage grows, a proxy can take the Shared route by setting `baseUrl`. It must:

1. Accept the same paths and query parameters as `https://api.openalex.org` (at least `GET /works` and `GET /works/{id}`), and answer with OpenAlex's bodies and status codes unchanged.
2. Add the OpenAlex key itself; the apps send none.
3. Pass through `X-RateLimit-Remaining`, `X-RateLimit-Reset` and `Retry-After`, and answer `429` with `X-RateLimit-Remaining: 0` when its own budget for a client is used up.
4. Limit by network address, keep no request logs, and store nothing about clients beyond short-lived counters.

Switching to a proxy also needs a privacy policy line (search requests pass through Hashiya's server, which sees network addresses and keeps no logs) and a look at the store answers. These steps go into `docs/release.md`.

## 9. Privacy

- Nothing new leaves the device: the cap count, reset marks and cache stay local; no install id.
- Store answers and the privacy policy don't change in this work.
- Rate limits and used-up budgets are normal states: never reported to Crashlytics as non-fatals.

## 10. Testing

### Unit tests (fakes, mock HTTP server)

- Route order: User → Shared → Keyless → Out; with a user key, only User.
- A 429 with `Remaining: 0` marks the route, stores the reset time, and retries once on the next route; the mark survives a restart and expires at the reset time; a missing `Reset` header means next midnight UTC.
- A 429 with budget left waits (capped), retries once on the same route, then gives the rate-limited error.
- The cap: the call after the cap goes keyless; the count resets on a new UTC date (including a device time-zone change); lookups and cached answers never count.
- Proxy: shared requests go to `baseUrl` with no `api_key`; user-key and keyless requests never go to `baseUrl`; a proxy 5xx or connection failure retries keyless and doesn't mark the proxy.
- Config: each invalid field falls back to its own default; a malformed section means all defaults; the last good config is used when the fetch fails.
- Cache: fresh hits send nothing; expiry at 24 hours; LRU removal at 5 MB; keys never contain a key; refresh skips the cache; errors aren't stored.
- Page cap: paging stops at `maxPagesPerQuery` and the footer appears.

### UI and snapshot tests

- Search's "Daily search limit reached" state and the page-cap footer; Settings' API key footer — English and Arabic, light and dark.

## 11. Delivery

1. iOS PR: route, cap, config, cache, page cap, Search state, Settings footer, `docs/release.md` (config fields, where to watch usage in the OpenAlex dashboard, the proxy contract and switch-on checklist).
2. Android PR: the same on Android.
3. After both ship: add the `openAlex` section to `app-config.json` in FadyFouad/Hashiya-Privacy-Policy, so the values are visible (the defaults apply until then).
