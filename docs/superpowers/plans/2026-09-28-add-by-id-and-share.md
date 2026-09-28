# Add by DOI / arXiv ID + Android Share Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user add a specific paper by typing or pasting a DOI, arXiv ID or link into Search, by sharing a page from the browser, or from a new Library "Add paper" button — always showing the paper's preview before saving.

**Architecture:** A pure parser in `core/model` turns text into a `PaperIdentifier` (strict for typed input, lenient for shares). `core/network` gains an OpenAlex single-work lookup and a small arXiv title client (no API key). `core/data` adds `PaperLookupRepository`, which resolves DOIs directly and arXiv IDs via the arXiv DOI, then an OpenAlex landing-page filter cross-checked against arXiv's title. `feature/search` switches into "ID mode" when the submitted text is an identifier; `SearchRoute` gains arguments used by the Library "Add paper" button and by `MainActivity`'s `ACTION_SEND` handling.

**Tech Stack:** Kotlin 2.4.20, AGP 9.2.1, Jetpack Compose + Material 3, Navigation Compose 2.10.2 (type-safe routes), Hilt 2.60.1 + KSP, Retrofit 3.0.0 + OkHttp 5.5.0 + kotlinx.serialization, Paging 3.5.1, JUnit 4, Robolectric 4.17, Roborazzi 1.75.0, Turbine, MockWebServer 3.

**Spec:** `docs/superpowers/specs/2026-09-28-add-by-id-and-share-design.md`

## Global Constraints

- Everything from sub-project 1 still applies: base package `com.etatech.hashiya`; `compileSdk = 37`, `targetSdk = 36`, `minSdk = 24`; versions only in `gradle/libs.versions.toml`; KSP only; no `org.jetbrains.kotlin.android` plugin.
- Dependency rules: `feature/*` → `core/data`, `core/model`, `core/designsystem` only; `core/network`, `core/database`, `core/datastore` never depend on each other or on `core/model`; `core/model` has no Android dependency.
- Every user-visible string lives in both `res/values/strings.xml` and `res/values-ar/strings.xml` of the module that uses it. No user-visible string literals in composables.
- Layouts use start/end. Paper content uses `TextDirection.Content`. Direction-implying icons are `Icons.AutoMirrored`.
- Tests use hand-written fakes; no mocking libraries. Robolectric tests run at SDK 35 (`src/test/resources/robolectric.properties` → `sdk=35`). Add `@Config(qualifiers = PHONE_QUALIFIERS)` to a Compose UI test class when an element sits below Robolectric's small default viewport.
- Screenshot tests use `ScreenshotVariantRule` at `@get:Rule(order = 0)` and the compose rule at `order = 1`; every call to `captureScreenshot(name, variant, arabicText)` passes an `arabicText` that is a string from the app's `values-ar` resources (never paper content) and that appears on exactly one node.
- **Screenshot baselines come from CI Linux only.** Commit code without screenshots (`git add -A -- . ':(exclude).idea/**' ':(exclude,glob)**/src/test/screenshots/**'`), then run `bash scripts/record-screenshots-on-linux.sh` (10–15 minutes; run it in the background) and commit the downloaded PNGs (`git add -- ':(glob)**/src/test/screenshots/**'`). Local `recordRoborazziDebug` output is for inspection only.
- The OpenAlex API key is never logged, never placed in exception messages, and never sent to arXiv. arXiv requests carry the `User-Agent` `Hashiya-Android (https://github.com/FadyFouad/Hashiya)`.
- Git: work on branch `feat/add-by-id-and-share` in a worktree; never commit to `main`; never stage `.idea/`. Run `./gradlew spotlessApply` before every commit (ktlint `android_studio` style removes trailing commas and may collapse parameter lists — that is expected). Commit messages use `feat:`/`fix:`/`test:`/`docs:`/`build:` and contain no AI attribution.

## Review Focus

1. **A half-typed DOI resolving to a different paper while the user is still typing** → only the lookup for the latest submitted text may be shown; an older lookup that finishes later must not overwrite it. Pinned by `SearchViewModelTest.newerLookupReplacesOlderOne` (Task 5).
2. **Route arguments re-applied after the app is restored** → a shared or "Add paper" query must be applied once; after process death the user's edited text wins. Pinned by `SearchViewModelTest.routeArgsNotReappliedOverRestoredText` and `routeArgsAppliedOnlyOnce` (Task 5).
3. **Rotating the phone right after a share** → the share must not be handled twice (no second Search on the back stack). Pinned by `HashiyaAppNavigationTest.shareIsHandledOnceAcrossRecreation` (Task 8).
4. **Harmless title differences between OpenAlex and arXiv** (case, punctuation, extra spaces, curly quotes, a trailing period) → still a match, not "not found". Pinned by `TitleMatchingTest` (Task 4).
5. **Very long shared text** (a whole article pasted into the share) → no hang; only the first 2,000 characters are examined. Pinned by `PaperIdentifierTest.lenientIgnoresTextBeyondTwoThousandCharacters` (Task 1).

---

## File Structure

```
core/model/src/main/kotlin/com/etatech/hashiya/core/model/PaperIdentifier.kt          parser (Task 1)
core/network/src/main/java/com/etatech/hashiya/core/network/OpenAlexApi.kt             + getWork, findWorks (Task 2)
core/network/src/main/java/com/etatech/hashiya/core/network/OpenAlexLookupDataSource.kt (Task 2)
core/network/src/main/java/com/etatech/hashiya/core/network/ArxivDataSource.kt         client + title parser (Task 3)
core/network/src/main/java/com/etatech/hashiya/core/network/di/NetworkModule.kt        + bindings (Tasks 2–3)
core/data/src/main/java/com/etatech/hashiya/core/data/repository/PaperLookupRepository.kt           interface + LookupResult (Task 4)
core/data/src/main/java/com/etatech/hashiya/core/data/repository/OpenAlexPaperLookupRepository.kt   (Task 4)
core/data/src/main/java/com/etatech/hashiya/core/data/lookup/ArxivLookup.kt           filter + title matching (Task 4)
core/testing/src/main/java/com/etatech/hashiya/core/testing/FakePaperLookupRepository.kt (Task 4)
feature/search/.../navigation/SearchNavigation.kt      SearchRoute data class (Task 5)
feature/search/.../SearchNote.kt, SearchUiState.kt, SearchQueryState.kt, SearchViewModel.kt (Task 5)
feature/search/.../components/LookupStates.kt, SearchScreen.kt, SearchActions.kt, SearchField.kt, strings (Task 6)
core/designsystem/.../icon/HashiyaIcons.kt             + Add (Task 7)
feature/library/.../LibraryScreen.kt, navigation/LibraryNavigation.kt, strings (Task 7)
app/.../navigation/HashiyaApp.kt                       openSearch, Add paper, pending share (Tasks 7–8)
app/.../share/ShareToSearchRoute.kt, MainActivity.kt, AndroidManifest.xml (Task 8)
README.md (Task 9)
```

---

### Task 1: `core/model` — identifier parser

**Files:**
- Create: `core/model/src/main/kotlin/com/etatech/hashiya/core/model/PaperIdentifier.kt`
- Test: `core/model/src/test/kotlin/com/etatech/hashiya/core/model/PaperIdentifierTest.kt`

**Interfaces:**
- Consumes: `normalizeDoi(raw: String): String?` (existing, same package).
- Produces:
  - `sealed interface PaperIdentifier { data class Doi(val value: String); data class Arxiv(val id: String) }` — `Doi.value` is normalized (lowercase, no URL prefix); `Arxiv.id` has no version and no subject class (`"1706.03762"`, `"hep-th/9901001"`, `"math/0309136"`).
  - `fun parsePaperIdentifier(text: String): PaperIdentifier?` — strict (whole trimmed input).
  - `fun extractPaperIdentifier(text: String): PaperIdentifier?` — lenient (first link, then first bare ID, within the first 2,000 characters).

Note: the spec's example `math.GT/0309136` resolves on arXiv and OpenAlex only as `math/0309136` (verified live 2026-09-28: `id_list=math.GT/0309136` returns no entry; `math/0309136` returns "Regular points in affine Springer fibers"). The subject class is metadata, not part of the ID, so the parser drops it.

- [ ] **Step 1: Write the failing tests**

`core/model/src/test/kotlin/com/etatech/hashiya/core/model/PaperIdentifierTest.kt`:
```kotlin
package com.etatech.hashiya.core.model

import com.etatech.hashiya.core.model.PaperIdentifier.Arxiv
import com.etatech.hashiya.core.model.PaperIdentifier.Doi
import org.junit.Assert.assertEquals
import org.junit.Test

class PaperIdentifierTest {
    private val sici = "10.1002/(sici)1099-1212(199901/02)9:1<8::aid-oa453>3.0.co;2-z"

    private fun strict(vararg cases: Pair<String, PaperIdentifier?>) = cases.forEach { (input, expected) ->
        assertEquals("parsePaperIdentifier(\"$input\")", expected, parsePaperIdentifier(input))
    }

    private fun lenient(vararg cases: Pair<String, PaperIdentifier?>) = cases.forEach { (input, expected) ->
        assertEquals("extractPaperIdentifier(\"$input\")", expected, extractPaperIdentifier(input))
    }

    @Test
    fun strictRecognizesDois() = strict(
        "10.1038/nature14539" to Doi("10.1038/nature14539"),
        "  https://doi.org/10.1038/NATURE14539 " to Doi("10.1038/nature14539"),
        "http://dx.doi.org/10.1038/nature14539" to Doi("10.1038/nature14539"),
        "doi:10.1038/nature14539" to Doi("10.1038/nature14539"),
        "DOI: 10.1038/nature14539" to Doi("10.1038/nature14539"),
        "https://onlinelibrary.wiley.com/doi/full/10.1002/anie.201915678?af=R#section" to Doi("10.1002/anie.201915678"),
        "https://dl.acm.org/doi/10.1145/3292500.3330701" to Doi("10.1145/3292500.3330701"),
        "https://link.springer.com/article/10.1007/s11263-015-0816-y" to Doi("10.1007/s11263-015-0816-y"),
        "https://doi.org/10.1002/(SICI)1099-1212(199901/02)9:1%3C8::AID-OA453%3E3.0.CO;2-Z" to Doi(sici),
        "10.1038/nature14539." to Doi("10.1038/nature14539")
    )

    @Test
    fun strictRecognizesArxivIds() = strict(
        "1706.03762" to Arxiv("1706.03762"),
        "2401.00001v2" to Arxiv("2401.00001"),
        "ARXIV:2401.00001" to Arxiv("2401.00001"),
        "arXiv: 2401.00001" to Arxiv("2401.00001"),
        "https://arxiv.org/abs/1706.03762v5" to Arxiv("1706.03762"),
        "arxiv.org/pdf/2401.00001v2.pdf" to Arxiv("2401.00001"),
        "https://www.arxiv.org/abs/2310.06825" to Arxiv("2310.06825"),
        "http://export.arxiv.org/abs/hep-th/9901001v2" to Arxiv("hep-th/9901001"),
        "hep-th/9901001" to Arxiv("hep-th/9901001"),
        "math.GT/0309136" to Arxiv("math/0309136"),
        "10.48550/ARXIV.1706.03762" to Arxiv("1706.03762"),
        "https://doi.org/10.48550/arXiv.2310.06825" to Arxiv("2310.06825"),
        "10.48550/arXiv.math/0309136" to Arxiv("math/0309136")
    )

    @Test
    fun strictRejectsEverythingElse() = strict(
        "" to null,
        "   " to null,
        "machine learning" to null,
        "a study of 10.1038/nature14539" to null,
        "https://example.com/2401.00001" to null,
        "https://arxiv.org/list/cs.LG/recent" to null,
        "https://www.nature.com/articles/nature14539" to null,
        "10.1038" to null,
        "2401.001" to null,
        "2023.12345" to null,
        "1234" to null
    )

    @Test
    fun strictHandlesParentheses() = strict(
        "10.1000/abc(1)" to Doi("10.1000/abc(1)"),
        "(10.1000/abc)" to Doi("10.1000/abc")
    )

    @Test
    fun lenientFindsIdsInsideText() = lenient(
        "Attention Is All You Need https://arxiv.org/abs/1706.03762" to Arxiv("1706.03762"),
        "a study of 10.1038/nature14539." to Doi("10.1038/nature14539"),
        "(see doi: 10.1000/xyz123)" to Doi("10.1000/xyz123"),
        "Ref: 10.1000/abc(1), page 3" to Doi("10.1000/abc(1)"),
        "SICI $sici is old" to Doi(sici),
        "see 2401.00001, it is good" to Arxiv("2401.00001"),
        "check https://example.com/page and 10.1000/xyz" to Doi("10.1000/xyz"),
        "Check this out: https://arxiv.org/abs/2401.00001v2" to Arxiv("2401.00001")
    )

    @Test
    fun lenientPrefersTheFirstLink() = lenient(
        "https://arxiv.org/abs/2401.00001 also 10.1038/nature14539" to Arxiv("2401.00001"),
        "10.1038/nature14539 then https://arxiv.org/abs/2401.00001" to Arxiv("2401.00001")
    )

    @Test
    fun lenientRejectsTextWithoutIds() = lenient(
        "Deep learning https://www.nature.com/articles/nature14539" to null,
        "https://example.com/2401.00001" to null,
        "nothing to see here" to null,
        "" to null
    )

    @Test
    fun lenientIgnoresTextBeyondTwoThousandCharacters() {
        val longText = "x".repeat(2_000) + " 10.1038/nature14539"
        assertEquals(null, extractPaperIdentifier(longText))
        assertEquals(Doi("10.1038/nature14539"), extractPaperIdentifier("x".repeat(1_900) + " 10.1038/nature14539"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:model:test --tests "*PaperIdentifierTest"`
Expected: FAIL — `Unresolved reference: parsePaperIdentifier`, `extractPaperIdentifier`, `PaperIdentifier`.

- [ ] **Step 3: Implement**

`core/model/src/main/kotlin/com/etatech/hashiya/core/model/PaperIdentifier.kt`:
```kotlin
package com.etatech.hashiya.core.model

/** A paper identifier recognized in text typed or shared by the user. */
sealed interface PaperIdentifier {
    /** A DOI in the canonical form returned by [normalizeDoi], e.g. "10.1038/nature14539". */
    data class Doi(val value: String) : PaperIdentifier

    /** An arXiv ID without version or subject class, e.g. "1706.03762", "hep-th/9901001", "math/0309136". */
    data class Arxiv(val id: String) : PaperIdentifier
}

private const val MAX_SHARED_TEXT = 2_000

// New style: YYMM.NNNN(N) with a real month, optional version.
private const val NEW_ARXIV = """\d{2}(?:0[1-9]|1[0-2])\.\d{4,5}(?:v\d+)?"""

// Old style: archive[.SUBJECT]/YYMMNNN, optional version.
private const val OLD_ARXIV = """[a-z]+(?:-[a-z]+)?(?:\.[a-z]{2})?/\d{7}(?:v\d+)?"""
private const val ANY_ARXIV = "(?:$NEW_ARXIV|$OLD_ARXIV)"

private val ARXIV_URL = Regex(
    """(?:https?://)?(?:www\.|export\.)?arxiv\.org/(?:abs|pdf)/($ANY_ARXIV)(?:\.pdf)?/?""",
    RegexOption.IGNORE_CASE
)
private val ARXIV_PREFIXED = Regex("""arxiv:($ANY_ARXIV)""", RegexOption.IGNORE_CASE)
private val BARE_ARXIV = Regex(ANY_ARXIV, RegexOption.IGNORE_CASE)
private val ARXIV_DOI = Regex("""10\.48550/arxiv\.($ANY_ARXIV)""", RegexOption.IGNORE_CASE)
private val BARE_DOI = Regex("""10\.\d{4,9}/\S+""")
private val DOI_IN_PATH = Regex("""10\.\d{4,9}/.+""")
private val SPACE_AFTER_PREFIX = Regex("""(?i)\b(arxiv:|doi:)\s+""")
private val WHITESPACE = Regex("""\s+""")
private val DOI_HOSTS = setOf("doi.org", "dx.doi.org", "www.doi.org")
private const val TRAILING_JUNK = ".,;:!?\"'>]"
private const val LEADING_JUNK = "([\"'<"

/** Strict: the whole trimmed [text] must be an identifier or a supported link. Used for the Search box. */
fun parsePaperIdentifier(text: String): PaperIdentifier? {
    val input = SPACE_AFTER_PREFIX.replace(text.trim(), "$1")
    if (input.isEmpty() || input.any { it.isWhitespace() }) return null
    return parseToken(input)
}

/** Lenient: the first supported link in [text], otherwise the first bare identifier. Used for shared text. */
fun extractPaperIdentifier(text: String): PaperIdentifier? {
    val tokens = SPACE_AFTER_PREFIX.replace(text.take(MAX_SHARED_TEXT), "$1")
        .split(WHITESPACE)
        .filter { it.isNotEmpty() }
    return tokens.firstNotNullOfOrNull { token -> if (isUrl(token)) parseToken(token) else null }
        ?: tokens.firstNotNullOfOrNull { token -> if (isUrl(token)) null else parseToken(token) }
}

private fun parseToken(rawToken: String): PaperIdentifier? {
    val token = trimTrailingJunk(rawToken.trimStart { it in LEADING_JUNK })
    if (token.isEmpty()) return null
    if (isUrl(token)) return parseUrl(token)
    ARXIV_PREFIXED.matchEntire(token)?.let { return PaperIdentifier.Arxiv(canonicalArxiv(it.groupValues[1])) }
    BARE_ARXIV.matchEntire(token)?.let { return PaperIdentifier.Arxiv(canonicalArxiv(it.value)) }
    val doi = if (token.startsWith("doi:", ignoreCase = true)) token.substring(4) else token
    return if (BARE_DOI.matches(doi)) doiIdentifier(doi) else null
}

private fun isUrl(token: String): Boolean {
    val value = token.trimStart { it in LEADING_JUNK }.lowercase()
    return "://" in value || value.startsWith("www.") || value.startsWith("arxiv.org/") ||
        value.startsWith("doi.org/") || value.startsWith("dx.doi.org/")
}

private fun parseUrl(url: String): PaperIdentifier? {
    val withoutQuery = url.substringBefore('#').substringBefore('?')
    ARXIV_URL.matchEntire(withoutQuery)?.let { return PaperIdentifier.Arxiv(canonicalArxiv(it.groupValues[1])) }
    val withoutScheme = withoutQuery.substringAfter("://")
    val host = withoutScheme.substringBefore('/').lowercase()
    val path = percentDecode(withoutScheme.substringAfter('/', missingDelimiterValue = ""))
    val doiPart = if (host in DOI_HOSTS) path else DOI_IN_PATH.find(path)?.value
    return doiPart?.let(::doiIdentifier)
}

private fun doiIdentifier(raw: String): PaperIdentifier? {
    val doi = normalizeDoi(trimTrailingJunk(raw.trimEnd('/'))) ?: return null
    if (!BARE_DOI.matches(doi)) return null
    ARXIV_DOI.matchEntire(doi)?.let { return PaperIdentifier.Arxiv(canonicalArxiv(it.groupValues[1])) }
    return PaperIdentifier.Doi(doi)
}

/** Removes the version, a ".pdf" suffix and an old-style subject class ("math.GT/0309136" → "math/0309136"). */
private fun canonicalArxiv(raw: String): String = raw.lowercase()
    .removeSuffix(".pdf")
    .replace(Regex("""v\d+$"""), "")
    .replace(Regex("""^([a-z]+(?:-[a-z]+)?)\.[a-z]{2}/"""), "$1/")

/** Drops trailing punctuation; a trailing ")" only when the parentheses are unbalanced. */
private fun trimTrailingJunk(value: String): String {
    var result = value
    while (result.isNotEmpty()) {
        val last = result.last()
        val unbalancedParen = last == ')' && result.count { it == '(' } < result.count { it == ')' }
        if (last in TRAILING_JUNK || unbalancedParen) result = result.dropLast(1) else break
    }
    return result
}

private fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < value.length) {
        val hex = if (value[i] == '%' && i + 2 < value.length) value.substring(i + 1, i + 3).toIntOrNull(16) else null
        if (hex != null) {
            bytes += hex.toByte()
            i += 3
        } else {
            bytes += value[i].toString().encodeToByteArray().toList()
            i += 1
        }
    }
    return bytes.toByteArray().decodeToString()
}
```

Note on the `(10.1000/abc)` strict case: the leading `(` is dropped as leading junk, which leaves `10.1000/abc)` with an unbalanced `)`, so it is removed too.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:model:test`
Expected: `BUILD SUCCESSFUL`; all `PaperIdentifierTest` tests plus the existing `core/model` tests pass. If a table row fails, fix the parser — never delete or weaken the row.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**'
git commit -m "feat: recognize DOIs, arXiv IDs and paper links in text"
```

---
### Task 2: `core/network` — OpenAlex single-work lookup

**Files:**
- Modify: `core/network/src/main/java/com/etatech/hashiya/core/network/OpenAlexApi.kt`
- Create: `core/network/src/main/java/com/etatech/hashiya/core/network/OpenAlexLookupDataSource.kt`
- Modify: `core/network/src/main/java/com/etatech/hashiya/core/network/di/NetworkModule.kt`
- Test: `core/network/src/test/resources/work.json`, `core/network/src/test/java/com/etatech/hashiya/core/network/OpenAlexLookupDataSourceTest.kt`

**Interfaces:**
- Consumes: `OpenAlexApi`, `WORK_FIELDS`, `buildOpenAlexOkHttpClient`, `buildOpenAlexApi`, `toNetworkException()`, `NetworkException`, `NetworkFailure`, `UserApiKeySource`, `NetworkWork`, `NetworkWorksResponse`, test helper `readFixture(name)` (all existing).
- Produces (package `com.etatech.hashiya.core.network`):
  - `interface OpenAlexLookupDataSource { suspend fun getWork(id: String): NetworkWork?; suspend fun findWorks(filter: String, perPage: Int): NetworkWorksResponse }` — `getWork` returns null for HTTP 404 and 400; both throw `NetworkException` for everything else.
  - Hilt binding `OpenAlexLookupDataSource` → `RetrofitOpenAlexLookupDataSource`. The existing `OpenAlexDataSource` is unchanged.

- [ ] **Step 1: Add the fixture**

`core/network/src/test/resources/work.json` (one work, the shape `GET /works/{id}?select=…` returns):
```json
{
  "id": "https://openalex.org/W2919115771",
  "doi": "https://doi.org/10.1038/nature14539",
  "display_name": "Deep learning",
  "publication_year": 2015,
  "primary_location": { "is_oa": false, "pdf_url": null, "source": { "id": "https://openalex.org/S137773608", "display_name": "Nature" } },
  "authorships": [
    { "author_position": "first", "author": { "id": "https://openalex.org/A5001", "display_name": "Yann LeCun" } },
    { "author_position": "middle", "author": { "id": "https://openalex.org/A5002", "display_name": "Yoshua Bengio" } },
    { "author_position": "last", "author": { "id": "https://openalex.org/A5003", "display_name": "Geoffrey Hinton" } }
  ],
  "cited_by_count": 72000,
  "open_access": { "is_oa": false, "oa_status": "closed", "oa_url": null },
  "best_oa_location": null,
  "abstract_inverted_index": { "Deep": [0], "learning": [1], "allows": [2] }
}
```

- [ ] **Step 2: Write the failing tests**

`core/network/src/test/java/com/etatech/hashiya/core/network/OpenAlexLookupDataSourceTest.kt`:
```kotlin
package com.etatech.hashiya.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OpenAlexLookupDataSourceTest {
    private val server = MockWebServer()
    private val keySource = object : UserApiKeySource {
        override val userKey: StateFlow<String?> = MutableStateFlow(null)
    }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        if (server.started) server.close()
    }

    private fun dataSource(): OpenAlexLookupDataSource {
        val client = buildOpenAlexOkHttpClient(keySource, builtInKey = "built-in-key", logger = null)
        return RetrofitOpenAlexLookupDataSource(buildOpenAlexApi(server.url("/"), client))
    }

    private fun enqueue(code: Int, body: String = "{}") {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    @Test
    fun getWorkRequestsTheWorkWithSelectedFields() = runTest {
        enqueue(200, readFixture("work.json"))

        val work = dataSource().getWork("doi:10.1038/nature14539")

        val url = server.takeRequest().url
        assertEquals(listOf("works", "doi:10.1038/nature14539"), url.pathSegments)
        assertEquals(WORK_FIELDS, url.queryParameter("select"))
        assertEquals("built-in-key", url.queryParameter("api_key"))
        assertEquals("https://openalex.org/W2919115771", work?.id)
        assertEquals("Deep learning", work?.displayName)
    }

    @Test
    fun notFoundIsNull() = runTest {
        enqueue(404)
        assertNull(dataSource().getWork("doi:10.9999/does-not-exist"))
    }

    @Test
    fun badRequestIsNull() = runTest {
        enqueue(400)
        assertNull(dataSource().getWork("doi:10.1234/weird"))
    }

    @Test
    fun otherFailuresThrow() = runTest {
        enqueue(429)
        try {
            dataSource().getWork("doi:10.1038/nature14539")
            fail("Expected NetworkException")
        } catch (e: NetworkException) {
            assertEquals(NetworkFailure.Http(code = 429, usedUserKey = false), e.failure)
        }
    }

    @Test
    fun siciDoiSurvivesPathEncoding() = runTest {
        val id = "doi:10.1002/(sici)1099-1212(199901/02)9:1<8::aid-oa453>3.0.co;2-z"
        enqueue(200, readFixture("work.json"))

        dataSource().getWork(id)

        assertEquals(listOf("works", id), server.takeRequest().url.pathSegments)
    }

    @Test
    fun hashInDoiSurvivesPathEncoding() = runTest {
        val id = "doi:10.1234/abc#1"
        enqueue(200, readFixture("work.json"))

        dataSource().getWork(id)

        assertEquals(listOf("works", id), server.takeRequest().url.pathSegments)
    }

    @Test
    fun findWorksSendsFilterAndPageSize() = runTest {
        val filter = "locations.landing_page_url:http://arxiv.org/abs/1810.04805|https://arxiv.org/abs/1810.04805"
        enqueue(200, readFixture("works_page.json"))

        val response = dataSource().findWorks(filter, perPage = 2)

        val url = server.takeRequest().url
        assertEquals("/works", url.encodedPath)
        assertEquals(filter, url.queryParameter("filter"))
        assertEquals("2", url.queryParameter("per_page"))
        assertEquals(WORK_FIELDS, url.queryParameter("select"))
        assertEquals(2, response.results.size)
    }

    @Test
    fun findWorksFailuresThrow() = runTest {
        val dataSource = dataSource()
        server.close()
        try {
            dataSource.findWorks("locations.landing_page_url:http://arxiv.org/abs/1", perPage = 2)
            fail("Expected NetworkException")
        } catch (e: NetworkException) {
            assertEquals(NetworkFailure.Connectivity, e.failure)
        }
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :core:network:testDebugUnitTest --tests "*OpenAlexLookupDataSourceTest"`
Expected: FAIL — `Unresolved reference: OpenAlexLookupDataSource`, `RetrofitOpenAlexLookupDataSource`.

- [ ] **Step 4: Implement**

In `core/network/src/main/java/com/etatech/hashiya/core/network/OpenAlexApi.kt`, add the imports `com.etatech.hashiya.core.network.model.NetworkWork` and `retrofit2.http.Path`, and add these two functions inside `internal interface OpenAlexApi` (after `searchWorks`):
```kotlin
    /** [id] is any id OpenAlex resolves, e.g. "doi:10.1038/nature14539". Retrofit's default encoding encodes "/" and "#". */
    @GET("works/{id}")
    suspend fun getWork(@Path("id") id: String, @Query("select") select: String = WORK_FIELDS): NetworkWork

    @GET("works")
    suspend fun findWorks(
        @Query("filter") filter: String,
        @Query("per_page") perPage: Int,
        @Query("select") select: String = WORK_FIELDS
    ): NetworkWorksResponse
```

`core/network/src/main/java/com/etatech/hashiya/core/network/OpenAlexLookupDataSource.kt`:
```kotlin
package com.etatech.hashiya.core.network

import com.etatech.hashiya.core.network.model.NetworkWork
import com.etatech.hashiya.core.network.model.NetworkWorksResponse
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Looks up single works by identifier. */
interface OpenAlexLookupDataSource {
    /**
     * The work OpenAlex resolves [id] to (e.g. "doi:10.1038/nature14539"), or null when OpenAlex has none.
     * Callers pass only well-formed ids, so HTTP 400 also means "none".
     * @throws NetworkException on any other failure.
     */
    suspend fun getWork(id: String): NetworkWork?

    /** @throws NetworkException on any failure. */
    suspend fun findWorks(filter: String, perPage: Int): NetworkWorksResponse
}

private val NOT_FOUND_CODES = setOf(400, 404)

internal class RetrofitOpenAlexLookupDataSource @Inject constructor(private val api: OpenAlexApi) : OpenAlexLookupDataSource {
    override suspend fun getWork(id: String): NetworkWork? = try {
        api.getWork(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        val networkException = e.toNetworkException()
        val code = (networkException.failure as? NetworkFailure.Http)?.code
        if (code in NOT_FOUND_CODES) null else throw networkException
    }

    override suspend fun findWorks(filter: String, perPage: Int): NetworkWorksResponse = try {
        api.findWorks(filter = filter, perPage = perPage)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw e.toNetworkException()
    }
}
```

In `core/network/src/main/java/com/etatech/hashiya/core/network/di/NetworkModule.kt`, add the imports `com.etatech.hashiya.core.network.OpenAlexLookupDataSource` and `com.etatech.hashiya.core.network.RetrofitOpenAlexLookupDataSource`, and add to `NetworkBindingsModule`:
```kotlin
    @Binds
    abstract fun bindOpenAlexLookupDataSource(impl: RetrofitOpenAlexLookupDataSource): OpenAlexLookupDataSource
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:network:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; the 8 new tests and all existing `core/network` tests pass.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**'
git commit -m "feat: look up single OpenAlex works by identifier"
```

---

### Task 3: `core/network` — arXiv title client

**Files:**
- Create: `core/network/src/main/java/com/etatech/hashiya/core/network/ArxivDataSource.kt`
- Modify: `core/network/src/main/java/com/etatech/hashiya/core/network/di/NetworkModule.kt`
- Test: `core/network/src/test/resources/arxiv_bert.xml`, `arxiv_empty.xml`, `arxiv_error.xml`
- Test: `core/network/src/test/java/com/etatech/hashiya/core/network/ArxivDataSourceTest.kt`

**Interfaces:**
- Consumes: `NetworkException`, `NetworkFailure` (existing).
- Produces (package `com.etatech.hashiya.core.network`):
  - `interface ArxivDataSource { suspend fun title(id: String): String? }` — null when arXiv has no paper with that ID (empty feed or an error entry); throws `NetworkException` (`Connectivity`, `Http(code, usedUserKey = false)`, `MalformedResponse`).
  - Hilt `@Provides @Singleton ArxivDataSource` built on its own `OkHttpClient` **without** the OpenAlex API-key interceptor.

- [ ] **Step 1: Add the fixtures** (shapes copied from live `export.arxiv.org` responses on 2026-09-28)

`core/network/src/test/resources/arxiv_bert.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/" xmlns:arxiv="http://arxiv.org/schemas/atom">
  <id>https://arxiv.org/api/a1b2c3</id>
  <title>arXiv Query: search_query=&amp;id_list=1810.04805&amp;start=0&amp;max_results=10</title>
  <updated>2026-09-28T00:00:00Z</updated>
  <opensearch:totalResults>1</opensearch:totalResults>
  <entry>
    <id>http://arxiv.org/abs/1810.04805v2</id>
    <title>BERT: Pre-training of Deep Bidirectional Transformers for
  Language Understanding</title>
    <summary>We introduce a new language representation model called BERT.</summary>
    <author><name>Jacob Devlin</name></author>
  </entry>
</feed>
```

`core/network/src/test/resources/arxiv_empty.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/">
  <id>https://arxiv.org/api/d4e5f6</id>
  <title>arXiv Query: search_query=&amp;id_list=2401.99999&amp;start=0&amp;max_results=10</title>
  <updated>2026-09-28T00:00:00Z</updated>
  <opensearch:totalResults>0</opensearch:totalResults>
</feed>
```

`core/network/src/test/resources/arxiv_error.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/">
  <id>https://arxiv.org/api/g7h8i9</id>
  <title>arXiv Query: search_query=&amp;id_list=notanid&amp;start=0&amp;max_results=10</title>
  <updated>2026-09-28T00:00:00Z</updated>
  <opensearch:totalResults>1</opensearch:totalResults>
  <entry>
    <id>https://arxiv.org/api/errors#incorrect_id_format_for_notanid</id>
    <title>Error</title>
    <summary>incorrect id format for notanid</summary>
  </entry>
</feed>
```

- [ ] **Step 2: Write the failing tests**

`core/network/src/test/java/com/etatech/hashiya/core/network/ArxivDataSourceTest.kt`:
```kotlin
package com.etatech.hashiya.core.network

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ArxivDataSourceTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        if (server.started) server.close()
    }

    private fun dataSource(): ArxivDataSource = OkHttpArxivDataSource(buildArxivOkHttpClient(), server.url("/"))

    private fun enqueue(code: Int, body: String) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    private suspend fun failureOf(block: suspend () -> Unit): NetworkFailure {
        try {
            block()
        } catch (e: NetworkException) {
            return e.failure
        }
        fail("Expected NetworkException")
        error("unreachable")
    }

    @Test
    fun requestsTheIdWithUserAgentAndNoApiKey() = runTest {
        enqueue(200, readFixture("arxiv_bert.xml"))

        dataSource().title("hep-th/9901001")

        val request = server.takeRequest()
        assertEquals("/api/query", request.url.encodedPath)
        assertEquals("hep-th/9901001", request.url.queryParameter("id_list"))
        assertNull(request.url.queryParameter("api_key"))
        assertEquals(ARXIV_USER_AGENT, request.headers["User-Agent"])
    }

    @Test
    fun readsTheEntryTitleWithCollapsedWhitespace() = runTest {
        enqueue(200, readFixture("arxiv_bert.xml"))
        assertEquals(
            "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding",
            dataSource().title("1810.04805")
        )
    }

    @Test
    fun emptyFeedMeansNoSuchPaper() = runTest {
        enqueue(200, readFixture("arxiv_empty.xml"))
        assertNull(dataSource().title("2401.99999"))
    }

    @Test
    fun errorEntryMeansNoSuchPaper() = runTest {
        enqueue(200, readFixture("arxiv_error.xml"))
        assertNull(dataSource().title("notanid"))
    }

    @Test
    fun serverErrorIsHttpFailure() = runTest {
        enqueue(503, "down")
        assertEquals(NetworkFailure.Http(code = 503, usedUserKey = false), failureOf { dataSource().title("1810.04805") })
    }

    @Test
    fun unreachableIsConnectivityFailure() = runTest {
        val dataSource = dataSource()
        server.close()
        assertEquals(NetworkFailure.Connectivity, failureOf { dataSource.title("1810.04805") })
    }

    @Test
    fun notAFeedIsMalformed() = runTest {
        enqueue(200, "<html><body>maintenance</body></html>")
        assertEquals(NetworkFailure.MalformedResponse, failureOf { dataSource().title("1810.04805") })
    }

    @Test
    fun decodesXmlEntities() {
        val xml = """<feed xmlns="http://www.w3.org/2005/Atom"><entry><id>http://arxiv.org/abs/1</id>
            |<title>Graphs &amp; Networks: &lt;A&gt; &#8211; &#x3B1; &quot;study&quot;</title></entry></feed>""".trimMargin()
        assertEquals("Graphs & Networks: <A> – α \"study\"", parseArxivTitle(xml))
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :core:network:testDebugUnitTest --tests "*ArxivDataSourceTest"`
Expected: FAIL — `Unresolved reference: ArxivDataSource`, `OkHttpArxivDataSource`, `buildArxivOkHttpClient`, `ARXIV_USER_AGENT`, `parseArxivTitle`.

- [ ] **Step 4: Implement**

`core/network/src/main/java/com/etatech/hashiya/core/network/ArxivDataSource.kt`:
```kotlin
package com.etatech.hashiya.core.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal const val ARXIV_BASE_URL = "https://export.arxiv.org/"
internal const val ARXIV_USER_AGENT = "Hashiya-Android (https://github.com/FadyFouad/Hashiya)"

/** Reads paper titles from arXiv's API. Used only to check OpenAlex matches; never sends the OpenAlex key. */
interface ArxivDataSource {
    /**
     * The title arXiv has for [id] (e.g. "1810.04805", "hep-th/9901001"), or null when arXiv has no such paper.
     * @throws NetworkException when arXiv can't be reached or answers with an error or unreadable data.
     */
    suspend fun title(id: String): String?
}

/** A plain client: no OpenAlex API-key interceptor, no logging of arXiv traffic. */
internal fun buildArxivOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

internal class OkHttpArxivDataSource(private val client: OkHttpClient, private val baseUrl: HttpUrl) : ArxivDataSource {
    override suspend fun title(id: String): String? {
        val url = baseUrl.newBuilder()
            .addPathSegments("api/query")
            .addQueryParameter("id_list", id)
            .build()
        val request = Request.Builder().url(url).header("User-Agent", ARXIV_USER_AGENT).build()
        val body = withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw NetworkException(NetworkFailure.Http(code = response.code, usedUserKey = false))
                    }
                    response.body.string()
                }
            } catch (e: IOException) {
                throw NetworkException(NetworkFailure.Connectivity, e)
            }
        }
        return parseArxivTitle(body)
    }
}

private val FEED = Regex("""<feed[\s>]""")
private val ENTRY = Regex("""<entry>(.*?)</entry>""", RegexOption.DOT_MATCHES_ALL)
private val ENTRY_ID = Regex("""<id>(.*?)</id>""", RegexOption.DOT_MATCHES_ALL)
private val ENTRY_TITLE = Regex("""<title[^>]*>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
private val WHITESPACE = Regex("""\s+""")
private val NUMERIC_ENTITY = Regex("""&#(x[0-9a-fA-F]+|\d+);""")

/** The first entry's title, or null for an empty feed or an arXiv error entry. */
internal fun parseArxivTitle(xml: String): String? {
    if (!FEED.containsMatchIn(xml)) throw NetworkException(NetworkFailure.MalformedResponse)
    val entry = ENTRY.find(xml)?.groupValues?.get(1) ?: return null
    if ("/api/errors" in ENTRY_ID.find(entry)?.groupValues?.get(1).orEmpty()) return null
    val rawTitle = ENTRY_TITLE.find(entry)?.groupValues?.get(1) ?: throw NetworkException(NetworkFailure.MalformedResponse)
    return decodeXmlEntities(rawTitle).trim().replace(WHITESPACE, " ").ifEmpty { null }
}

private fun decodeXmlEntities(value: String): String = NUMERIC_ENTITY.replace(value) { match ->
    val code = match.groupValues[1]
    val codePoint = if (code.startsWith("x")) code.drop(1).toInt(16) else code.toInt()
    String(Character.toChars(codePoint))
}
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")
```

In `core/network/src/main/java/com/etatech/hashiya/core/network/di/NetworkModule.kt`, add the imports `com.etatech.hashiya.core.network.ARXIV_BASE_URL`, `com.etatech.hashiya.core.network.ArxivDataSource`, `com.etatech.hashiya.core.network.OkHttpArxivDataSource`, `com.etatech.hashiya.core.network.buildArxivOkHttpClient`, and add to `object NetworkModule`:
```kotlin
    @Provides
    @Singleton
    fun provideArxivDataSource(): ArxivDataSource = OkHttpArxivDataSource(buildArxivOkHttpClient(), ARXIV_BASE_URL.toHttpUrl())
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:network:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; the 8 new tests and all existing `core/network` tests pass.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**'
git commit -m "feat: read paper titles from the arXiv API"
```

---
### Task 4: `core/data` — `PaperLookupRepository`

**Files:**
- Create: `core/data/src/main/java/com/etatech/hashiya/core/data/repository/PaperLookupRepository.kt`
- Create: `core/data/src/main/java/com/etatech/hashiya/core/data/lookup/ArxivLookup.kt`
- Create: `core/data/src/main/java/com/etatech/hashiya/core/data/repository/OpenAlexPaperLookupRepository.kt`
- Modify: `core/data/src/main/java/com/etatech/hashiya/core/data/di/DataModule.kt`
- Create: `core/testing/src/main/java/com/etatech/hashiya/core/testing/FakePaperLookupRepository.kt`
- Test: `core/data/src/test/java/com/etatech/hashiya/core/data/FakeLookupDataSources.kt`
- Test: `core/data/src/test/java/com/etatech/hashiya/core/data/lookup/ArxivLookupTest.kt`, `TitleMatchingTest.kt`
- Test: `core/data/src/test/java/com/etatech/hashiya/core/data/repository/OpenAlexPaperLookupRepositoryTest.kt`

**Interfaces:**
- Consumes: `PaperIdentifier` (Task 1); `OpenAlexLookupDataSource` (Task 2); `ArxivDataSource` (Task 3); existing `NetworkWork.asPaper()`, `NetworkFailure.asSearchError()`, `NetworkException`, `NetworkWorksResponse`, `NetworkMeta(count, nextCursor)`, `SearchError`.
- Produces:
  - `com.etatech.hashiya.core.data.repository.PaperLookupRepository { suspend fun lookup(identifier: PaperIdentifier): LookupResult }`
  - `sealed interface LookupResult { data class Found(val paper: Paper); data class NotFound(val arxivTitle: String?); data class Failed(val error: SearchError) }`
  - Hilt binding `PaperLookupRepository` → `OpenAlexPaperLookupRepository`.
  - `com.etatech.hashiya.core.testing.FakePaperLookupRepository` with `lookups: List<PaperIdentifier>`, `results: MutableMap<PaperIdentifier, LookupResult>` (default `NotFound(null)`), `holdLookups()` / `releaseLookups()`.

- [ ] **Step 1: Write the test fakes**

`core/data/src/test/java/com/etatech/hashiya/core/data/FakeLookupDataSources.kt`:
```kotlin
package com.etatech.hashiya.core.data

import com.etatech.hashiya.core.network.ArxivDataSource
import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.OpenAlexLookupDataSource
import com.etatech.hashiya.core.network.model.NetworkMeta
import com.etatech.hashiya.core.network.model.NetworkWork
import com.etatech.hashiya.core.network.model.NetworkWorksResponse

internal class FakeOpenAlexLookupDataSource : OpenAlexLookupDataSource {
    val workRequests = mutableListOf<String>()
    val findRequests = mutableListOf<Pair<String, Int>>()
    var works: Map<String, NetworkWork> = emptyMap()
    var found: List<NetworkWork> = emptyList()
    var getFailure: NetworkFailure? = null
    var findFailure: NetworkFailure? = null

    override suspend fun getWork(id: String): NetworkWork? {
        workRequests += id
        getFailure?.let { throw NetworkException(it) }
        return works[id]
    }

    override suspend fun findWorks(filter: String, perPage: Int): NetworkWorksResponse {
        findRequests += filter to perPage
        findFailure?.let { throw NetworkException(it) }
        return NetworkWorksResponse(meta = NetworkMeta(count = found.size.toLong()), results = found)
    }
}

internal class FakeArxivDataSource : ArxivDataSource {
    val requests = mutableListOf<String>()
    var titles: Map<String, String> = emptyMap()
    var failure: NetworkFailure? = null

    override suspend fun title(id: String): String? {
        requests += id
        failure?.let { throw NetworkException(it) }
        return titles[id]
    }
}
```

- [ ] **Step 2: Write the failing tests**

`core/data/src/test/java/com/etatech/hashiya/core/data/lookup/ArxivLookupTest.kt`:
```kotlin
package com.etatech.hashiya.core.data.lookup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArxivLookupTest {
    @Test
    fun filterCoversUnversionedVersionedAndDoiLandingPages() {
        val pages = listOf(
            "http://arxiv.org/abs/1810.04805",
            "http://arxiv.org/abs/1810.04805v1",
            "http://arxiv.org/abs/1810.04805v2",
            "http://arxiv.org/abs/1810.04805v3",
            "http://arxiv.org/abs/1810.04805v4",
            "http://arxiv.org/abs/1810.04805v5",
            "https://arxiv.org/abs/1810.04805",
            "https://arxiv.org/abs/1810.04805v1",
            "https://arxiv.org/abs/1810.04805v2",
            "https://arxiv.org/abs/1810.04805v3",
            "https://arxiv.org/abs/1810.04805v4",
            "https://arxiv.org/abs/1810.04805v5",
            "https://doi.org/10.48550/arxiv.1810.04805"
        )
        assertEquals("locations.landing_page_url:" + pages.joinToString("|"), arxivLandingPageFilter("1810.04805"))
    }

    @Test
    fun oldStyleIdsKeepTheirSlash() {
        assertTrue(arxivLandingPageFilter("hep-th/9901001").contains("http://arxiv.org/abs/hep-th/9901001|"))
    }
}
```

`core/data/src/test/java/com/etatech/hashiya/core/data/lookup/TitleMatchingTest.kt`:
```kotlin
package com.etatech.hashiya.core.data.lookup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatchingTest {
    @Test
    fun ignoresCasePunctuationAndSpacing() {
        assertTrue(
            titlesMatch(
                "BERT: Pre-training of Deep  Bidirectional Transformers for Language Understanding.",
                "bert pre training of deep bidirectional transformers for language understanding"
            )
        )
    }

    @Test
    fun ignoresQuoteStyles() {
        assertTrue(titlesMatch("Don’t Stop Pretraining", "Don't stop pretraining"))
    }

    @Test
    fun worksForNonLatinTitles() {
        assertTrue(titlesMatch("تعلم الآلة", "تعلم الآلة."))
    }

    @Test
    fun differentTitlesDoNotMatch() {
        assertFalse(
            titlesMatch(
                "AI-Assisted Pipeline for Dynamic Generation of Trustworthy Health Supplement Content at Scale",
                "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding"
            )
        )
    }

    @Test
    fun emptyTitlesNeverMatch() {
        assertFalse(titlesMatch("", ""))
        assertFalse(titlesMatch("!!!", "..."))
    }
}
```

`core/data/src/test/java/com/etatech/hashiya/core/data/repository/OpenAlexPaperLookupRepositoryTest.kt`:
```kotlin
package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.data.FakeArxivDataSource
import com.etatech.hashiya.core.data.FakeOpenAlexLookupDataSource
import com.etatech.hashiya.core.data.lookup.arxivLandingPageFilter
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.model.NetworkWork
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAlexPaperLookupRepositoryTest {
    private val openAlex = FakeOpenAlexLookupDataSource()
    private val arxiv = FakeArxivDataSource()
    private val repository = OpenAlexPaperLookupRepository(openAlex, arxiv)

    private val bertTitle = "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding"
    private val bert = PaperIdentifier.Arxiv("1810.04805")
    private val attention = PaperIdentifier.Arxiv("1706.03762")

    private fun work(id: String, title: String) = NetworkWork(id = "https://openalex.org/$id", displayName = title)

    @Test
    fun doiIsLookedUpDirectly() = runTest {
        openAlex.works = mapOf("doi:10.1038/nature14539" to work("W2919115771", "Deep learning"))

        val result = repository.lookup(PaperIdentifier.Doi("10.1038/nature14539"))

        assertEquals("W2919115771", (result as LookupResult.Found).paper.openAlexId)
        assertTrue(openAlex.findRequests.isEmpty())
        assertTrue(arxiv.requests.isEmpty())
    }

    @Test
    fun unknownDoiIsNotFound() = runTest {
        assertEquals(LookupResult.NotFound(arxivTitle = null), repository.lookup(PaperIdentifier.Doi("10.9999/nothing")))
    }

    @Test
    fun offlineDoiLookupFails() = runTest {
        openAlex.getFailure = NetworkFailure.Connectivity
        assertEquals(LookupResult.Failed(SearchError.Offline), repository.lookup(PaperIdentifier.Doi("10.1038/nature14539")))
    }

    @Test
    fun rejectedUserKeyFails() = runTest {
        openAlex.getFailure = NetworkFailure.Http(code = 401, usedUserKey = true)
        assertEquals(LookupResult.Failed(SearchError.InvalidUserKey), repository.lookup(PaperIdentifier.Doi("10.1038/nature14539")))
    }

    @Test
    fun arxivDoiMatchIsTrusted() = runTest {
        openAlex.works = mapOf("doi:10.48550/arXiv.2310.06825" to work("W4387561528", "Mistral 7B"))

        val result = repository.lookup(PaperIdentifier.Arxiv("2310.06825"))

        assertEquals("W4387561528", (result as LookupResult.Found).paper.openAlexId)
        assertTrue(openAlex.findRequests.isEmpty())
        assertTrue(arxiv.requests.isEmpty())
    }

    @Test
    fun fallbackUsesTheLandingPageFilterAndChecksTheTitle() = runTest {
        openAlex.found = listOf(work("W2626778328", "Attention Is All You Need"))
        arxiv.titles = mapOf("1706.03762" to "Attention Is All You Need")

        val result = repository.lookup(attention)

        assertEquals("W2626778328", (result as LookupResult.Found).paper.openAlexId)
        assertEquals(listOf("doi:10.48550/arXiv.1706.03762"), openAlex.workRequests)
        assertEquals(listOf(arxivLandingPageFilter("1706.03762") to 2), openAlex.findRequests)
        assertEquals(listOf("1706.03762"), arxiv.requests)
    }

    @Test
    fun fallbackWithTheWrongTitleIsNotFound() = runTest {
        openAlex.found = listOf(work("W2896457183", "AI-Assisted Pipeline for Dynamic Generation of Trustworthy Health Supplement Content"))
        arxiv.titles = mapOf("1810.04805" to bertTitle)

        assertEquals(LookupResult.NotFound(arxivTitle = bertTitle), repository.lookup(bert))
    }

    @Test
    fun fallbackWithoutMatchesOffersTheArxivTitle() = runTest {
        arxiv.titles = mapOf("1810.04805" to bertTitle)
        assertEquals(LookupResult.NotFound(arxivTitle = bertTitle), repository.lookup(bert))
    }

    @Test
    fun fallbackWithoutMatchesAndArxivDownIsPlainNotFound() = runTest {
        arxiv.failure = NetworkFailure.Connectivity
        assertEquals(LookupResult.NotFound(arxivTitle = null), repository.lookup(bert))
    }

    @Test
    fun twoDifferentWorksAreNotFound() = runTest {
        openAlex.found = listOf(work("W1", bertTitle), work("W2", bertTitle))
        arxiv.titles = mapOf("1810.04805" to bertTitle)

        assertEquals(LookupResult.NotFound(arxivTitle = bertTitle), repository.lookup(bert))
    }

    @Test
    fun theSameWorkTwiceCountsAsOne() = runTest {
        openAlex.found = listOf(work("W1", bertTitle), work("W1", bertTitle))
        arxiv.titles = mapOf("1810.04805" to bertTitle)

        assertEquals("W1", (repository.lookup(bert) as LookupResult.Found).paper.openAlexId)
    }

    @Test
    fun crossCheckWhileArxivIsOfflineOffersRetry() = runTest {
        openAlex.found = listOf(work("W1", bertTitle))
        arxiv.failure = NetworkFailure.Connectivity

        assertEquals(LookupResult.Failed(SearchError.Offline), repository.lookup(bert))
    }

    @Test
    fun crossCheckWhenArxivErrorsIsUnavailable() = runTest {
        openAlex.found = listOf(work("W1", bertTitle))
        arxiv.failure = NetworkFailure.Http(code = 503, usedUserKey = false)
        assertEquals(LookupResult.Failed(SearchError.ServiceUnavailable), repository.lookup(bert))

        arxiv.failure = NetworkFailure.MalformedResponse
        assertEquals(LookupResult.Failed(SearchError.ServiceUnavailable), repository.lookup(bert))
    }

    @Test
    fun crossCheckWhenArxivHasNoSuchPaperIsNotFound() = runTest {
        openAlex.found = listOf(work("W1", bertTitle))
        assertEquals(LookupResult.NotFound(arxivTitle = null), repository.lookup(bert))
    }

    @Test
    fun rateLimitDuringTheFallbackFails() = runTest {
        openAlex.findFailure = NetworkFailure.Http(code = 429, usedUserKey = false)
        assertEquals(LookupResult.Failed(SearchError.RateLimited), repository.lookup(bert))
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*ArxivLookupTest" --tests "*TitleMatchingTest" --tests "*OpenAlexPaperLookupRepositoryTest"`
Expected: FAIL — `Unresolved reference: arxivLandingPageFilter`, `titlesMatch`, `OpenAlexPaperLookupRepository`, `LookupResult`.

- [ ] **Step 4: Implement**

`core/data/src/main/java/com/etatech/hashiya/core/data/repository/PaperLookupRepository.kt`:
```kotlin
package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.SearchError

/** Finds the single paper a DOI or arXiv ID refers to. */
interface PaperLookupRepository {
    suspend fun lookup(identifier: PaperIdentifier): LookupResult
}

sealed interface LookupResult {
    data class Found(val paper: Paper) : LookupResult

    /** [arxivTitle] is arXiv's own title for an arXiv ID OpenAlex couldn't match, so the UI can offer a title search. */
    data class NotFound(val arxivTitle: String?) : LookupResult

    data class Failed(val error: SearchError) : LookupResult
}
```

`core/data/src/main/java/com/etatech/hashiya/core/data/lookup/ArxivLookup.kt`:
```kotlin
package com.etatech.hashiya.core.data.lookup

internal const val ARXIV_DOI_PREFIX = "10.48550/arXiv."

/**
 * OpenAlex filter matching any landing page OpenAlex stores for an arXiv paper: the abs page over http or https,
 * with no version or v1–v5, and the arXiv DOI page. `|` means "any of" and keeps this a single request.
 */
internal fun arxivLandingPageFilter(id: String): String {
    val versions = listOf("") + (1..5).map { "v$it" }
    val absPages = listOf("http", "https").flatMap { scheme -> versions.map { version -> "$scheme://arxiv.org/abs/$id$version" } }
    return "locations.landing_page_url:" + (absPages + "https://doi.org/10.48550/arxiv.$id").joinToString("|")
}

private val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]+""")

internal fun normalizedTitle(title: String): String = title.lowercase().replace(NON_ALPHANUMERIC, " ").trim()

/** True when both titles have the same letters and digits in the same order, ignoring case and punctuation. */
internal fun titlesMatch(a: String, b: String): Boolean {
    val left = normalizedTitle(a)
    return left.isNotEmpty() && left == normalizedTitle(b)
}
```

`core/data/src/main/java/com/etatech/hashiya/core/data/repository/OpenAlexPaperLookupRepository.kt`:
```kotlin
package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.data.lookup.ARXIV_DOI_PREFIX
import com.etatech.hashiya.core.data.lookup.arxivLandingPageFilter
import com.etatech.hashiya.core.data.lookup.titlesMatch
import com.etatech.hashiya.core.data.mapping.asPaper
import com.etatech.hashiya.core.data.search.asSearchError
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.network.ArxivDataSource
import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.OpenAlexLookupDataSource
import com.etatech.hashiya.core.network.model.NetworkWork
import javax.inject.Inject

/**
 * DOIs resolve directly. arXiv IDs try the arXiv DOI first (trusted), then OpenAlex's landing-page filter, whose
 * single match is accepted only if its title matches arXiv's title — OpenAlex sometimes attaches the wrong work.
 */
internal class OpenAlexPaperLookupRepository @Inject constructor(
    private val openAlex: OpenAlexLookupDataSource,
    private val arxiv: ArxivDataSource
) : PaperLookupRepository {
    override suspend fun lookup(identifier: PaperIdentifier): LookupResult = try {
        when (identifier) {
            is PaperIdentifier.Doi -> openAlex.getWork("doi:${identifier.value}").toLookupResult()
            is PaperIdentifier.Arxiv -> lookupArxiv(identifier.id)
        }
    } catch (e: NetworkException) {
        LookupResult.Failed(e.failure.asSearchError())
    }

    private suspend fun lookupArxiv(id: String): LookupResult {
        openAlex.getWork("doi:$ARXIV_DOI_PREFIX$id")?.let { return LookupResult.Found(it.asPaper()) }
        val matches = openAlex.findWorks(arxivLandingPageFilter(id), perPage = 2).results.distinctBy { it.id }
        if (matches.size != 1) return LookupResult.NotFound(arxivTitleOrNull(id))
        val arxivTitle = try {
            arxiv.title(id)
        } catch (e: NetworkException) {
            return LookupResult.Failed(e.failure.asCrossCheckError())
        } ?: return LookupResult.NotFound(arxivTitle = null)
        val paper = matches.single().asPaper()
        return if (titlesMatch(paper.title, arxivTitle)) LookupResult.Found(paper) else LookupResult.NotFound(arxivTitle)
    }

    /** Only labels the "Search for …" button, so a failure here is not an error. */
    private suspend fun arxivTitleOrNull(id: String): String? = try {
        arxiv.title(id)
    } catch (e: NetworkException) {
        null
    }

    private fun NetworkWork?.toLookupResult(): LookupResult =
        this?.let { LookupResult.Found(it.asPaper()) } ?: LookupResult.NotFound(arxivTitle = null)
}

private fun NetworkFailure.asCrossCheckError(): SearchError =
    if (this == NetworkFailure.Connectivity) SearchError.Offline else SearchError.ServiceUnavailable
```

In `core/data/src/main/java/com/etatech/hashiya/core/data/di/DataModule.kt`, add the imports `com.etatech.hashiya.core.data.repository.OpenAlexPaperLookupRepository` and `com.etatech.hashiya.core.data.repository.PaperLookupRepository`, and add to `DataModule`:
```kotlin
    @Binds
    abstract fun bindPaperLookupRepository(impl: OpenAlexPaperLookupRepository): PaperLookupRepository
```

`core/testing/src/main/java/com/etatech/hashiya/core/testing/FakePaperLookupRepository.kt`:
```kotlin
package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.LookupResult
import com.etatech.hashiya.core.data.repository.PaperLookupRepository
import com.etatech.hashiya.core.model.PaperIdentifier
import kotlinx.coroutines.CompletableDeferred

class FakePaperLookupRepository : PaperLookupRepository {
    val lookups = mutableListOf<PaperIdentifier>()
    val results = mutableMapOf<PaperIdentifier, LookupResult>()
    private var gate: CompletableDeferred<Unit>? = null

    /** Makes every following lookup wait until [releaseLookups], so tests can see the Looking state. */
    fun holdLookups() {
        gate = CompletableDeferred()
    }

    fun releaseLookups() {
        gate?.complete(Unit)
        gate = null
    }

    override suspend fun lookup(identifier: PaperIdentifier): LookupResult {
        lookups += identifier
        gate?.await()
        return results[identifier] ?: LookupResult.NotFound(arxivTitle = null)
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest :core:testing:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; 2 `ArxivLookupTest` + 5 `TitleMatchingTest` + 15 `OpenAlexPaperLookupRepositoryTest` tests pass, plus all existing `core/data` and `core/testing` tests.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**'
git commit -m "feat: look up papers by DOI or arXiv ID with an arXiv title check"
```

---
### Task 5: `feature/search` — ID mode and route arguments in `SearchViewModel`

**Files:**
- Create: `feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchNote.kt`
- Modify: `feature/search/src/main/java/com/etatech/hashiya/feature/search/navigation/SearchNavigation.kt`
- Modify: `feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchUiState.kt`
- Modify: `feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchQueryState.kt`
- Modify: `feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchViewModel.kt` (full replacement below)
- Test: `feature/search/src/test/java/com/etatech/hashiya/feature/search/SearchViewModelTest.kt`

**Interfaces:**
- Consumes: `parsePaperIdentifier`, `PaperIdentifier` (Task 1); `PaperLookupRepository`, `LookupResult` (Task 4); `FakePaperLookupRepository` (Task 4); existing `SearchRepository`, `LibraryRepository`, `UserPreferencesRepository`, fakes, `MainDispatcherRule`, `SamplePapers`.
- Produces (package `com.etatech.hashiya.feature.search`):
  - `enum class SearchNote { NoIdInShare, NothingInShare }`
  - `navigation.SearchRoute(query: String? = null, pageTitle: String? = null, focusSearch: Boolean = false, note: String? = null)` — `note` holds a `SearchNote` name (plain strings keep navigation arguments simple).
  - `fun NavController.navigateToSearch(navOptions: NavOptions? = null, route: SearchRoute = SearchRoute())` — existing calls `navigateToSearch(options)` keep compiling.
  - `sealed interface LookupUiState { Looking(identifier); Found(paper); NotFound(identifier, searchTitle: String?); Failed(error: SearchError) }`
  - `SearchViewModel` gains the constructor parameter `paperLookupRepository: PaperLookupRepository` (last) and exposes `lookupState: StateFlow<LookupUiState?>`, `note: StateFlow<SearchNote?>`, `focusSearch: StateFlow<Boolean>`, `onRetryLookup()`, `onFocusHandled()`.

- [ ] **Step 1: Write the failing tests**

In `feature/search/src/test/java/com/etatech/hashiya/feature/search/SearchViewModelTest.kt`:

1. Add the imports:
```kotlin
import com.etatech.hashiya.core.data.repository.LookupResult
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.testing.FakePaperLookupRepository
```

2. Replace the fields and the `viewModel(...)` helper at the top of the class with:
```kotlin
    private val searchRepository = FakeSearchRepository()
    private val libraryRepository = FakeLibraryRepository()
    private val userPreferencesRepository = FakeUserPreferencesRepository()
    private val lookupRepository = FakePaperLookupRepository()
    private val savedStateHandle = SavedStateHandle()

    private val bertTitle = "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding"

    private fun TestScope.viewModel(handle: SavedStateHandle = savedStateHandle): SearchViewModel {
        val viewModel = SearchViewModel(handle, searchRepository, libraryRepository, userPreferencesRepository, lookupRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.selectedItem.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.savedIds.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.lookupState.collect() }
        runCurrent()
        return viewModel
    }
```

3. Add these tests to the class (keep every existing test unchanged):
```kotlin
    @Test
    fun pastedDoiIsLookedUpInsteadOfSearched() = runTest {
        val doi = PaperIdentifier.Doi("10.1038/nature14539")
        lookupRepository.results[doi] = LookupResult.Found(SamplePapers.attention)
        val viewModel = viewModel()

        viewModel.onTextChange("https://doi.org/10.1038/nature14539")
        advanceTimeBy(DEBOUNCE_MS + 1)
        runCurrent()

        assertEquals(listOf(doi), lookupRepository.lookups)
        assertTrue(searchRepository.queries.isEmpty())
        assertEquals(LookupUiState.Found(SamplePapers.attention), viewModel.lookupState.value)
    }

    @Test
    fun lookupShowsLookingUntilItFinishes() = runTest {
        val arxiv = PaperIdentifier.Arxiv("1706.03762")
        lookupRepository.results[arxiv] = LookupResult.Found(SamplePapers.attention)
        lookupRepository.holdLookups()
        val viewModel = viewModel()

        viewModel.onSuggestion("1706.03762")
        runCurrent()
        assertEquals(LookupUiState.Looking(arxiv), viewModel.lookupState.value)

        lookupRepository.releaseLookups()
        runCurrent()
        assertEquals(LookupUiState.Found(SamplePapers.attention), viewModel.lookupState.value)
    }

    @Test
    fun textContainingADoiIsAKeywordSearch() = runTest {
        val viewModel = viewModel()

        viewModel.onTextChange("a study of 10.1038/nature14539")
        advanceTimeBy(DEBOUNCE_MS + 1)
        runCurrent()

        assertEquals(listOf(SearchQuery("a study of 10.1038/nature14539")), searchRepository.queries)
        assertTrue(lookupRepository.lookups.isEmpty())
        assertNull(viewModel.lookupState.value)
    }

    @Test
    fun newerLookupReplacesOlderOne() = runTest {
        val first = PaperIdentifier.Doi("10.1000/first")
        val second = PaperIdentifier.Doi("10.1000/second")
        lookupRepository.results[first] = LookupResult.Found(SamplePapers.attention)
        lookupRepository.results[second] = LookupResult.Found(SamplePapers.bert)
        lookupRepository.holdLookups()
        val viewModel = viewModel()

        viewModel.onSuggestion("10.1000/first")
        runCurrent()
        viewModel.onSuggestion("10.1000/second")
        runCurrent()
        lookupRepository.releaseLookups()
        runCurrent()

        assertEquals(listOf(first, second), lookupRepository.lookups)
        assertEquals(LookupUiState.Found(SamplePapers.bert), viewModel.lookupState.value)
    }

    @Test
    fun retryRerunsTheLookup() = runTest {
        val doi = PaperIdentifier.Doi("10.1038/nature14539")
        lookupRepository.results[doi] = LookupResult.Failed(SearchError.Offline)
        val viewModel = viewModel()
        viewModel.onSuggestion("10.1038/nature14539")
        runCurrent()
        assertEquals(LookupUiState.Failed(SearchError.Offline), viewModel.lookupState.value)

        lookupRepository.results[doi] = LookupResult.Found(SamplePapers.attention)
        viewModel.onRetryLookup()
        runCurrent()

        assertEquals(listOf(doi, doi), lookupRepository.lookups)
        assertEquals(LookupUiState.Found(SamplePapers.attention), viewModel.lookupState.value)
    }

    @Test
    fun changingTheApiKeyRerunsTheLookup() = runTest {
        val viewModel = viewModel()
        viewModel.onSuggestion("1706.03762")
        runCurrent()

        userPreferencesRepository.setUserApiKey("new-key")
        runCurrent()

        assertEquals(2, lookupRepository.lookups.size)
    }

    @Test
    fun notFoundOffersTheArxivTitle() = runTest {
        val bert = PaperIdentifier.Arxiv("1810.04805")
        lookupRepository.results[bert] = LookupResult.NotFound(arxivTitle = bertTitle)
        val viewModel = viewModel()

        viewModel.onSuggestion("1810.04805")
        runCurrent()

        assertEquals(LookupUiState.NotFound(bert, searchTitle = bertTitle), viewModel.lookupState.value)
    }

    @Test
    fun routeQueryIsSubmittedImmediately() = runTest {
        val handle = SavedStateHandle(mapOf("query" to "arXiv:1706.03762", "pageTitle" to "Attention Is All You Need"))

        val viewModel = viewModel(handle)

        assertEquals(listOf(PaperIdentifier.Arxiv("1706.03762")), lookupRepository.lookups)
        assertEquals("arXiv:1706.03762", viewModel.uiState.value.text)
    }

    @Test
    fun notFoundFallsBackToTheSharedPageTitle() = runTest {
        val handle = SavedStateHandle(mapOf("query" to "10.1038/nature14539", "pageTitle" to "Deep learning"))

        val viewModel = viewModel(handle)

        assertEquals(
            LookupUiState.NotFound(PaperIdentifier.Doi("10.1038/nature14539"), searchTitle = "Deep learning"),
            viewModel.lookupState.value
        )
    }

    @Test
    fun editingTheTextForgetsThePageTitle() = runTest {
        val viewModel = viewModel(SavedStateHandle(mapOf("query" to "10.1038/nature14539", "pageTitle" to "Deep learning")))

        viewModel.onTextChange("1810.04805")
        advanceTimeBy(DEBOUNCE_MS + 1)
        runCurrent()

        assertEquals(LookupUiState.NotFound(PaperIdentifier.Arxiv("1810.04805"), searchTitle = null), viewModel.lookupState.value)
    }

    @Test
    fun routeArgsNotReappliedOverRestoredText() = runTest {
        val handle = SavedStateHandle(mapOf("query" to "10.1038/nature14539", "search_text" to "bert"))

        val viewModel = viewModel(handle)

        assertEquals("bert", viewModel.uiState.value.text)
        assertTrue(lookupRepository.lookups.isEmpty())
        assertEquals(listOf(SearchQuery("bert")), searchRepository.queries)
    }

    @Test
    fun routeArgsAppliedOnlyOnce() = runTest {
        val handle = SavedStateHandle(mapOf("query" to "10.1038/nature14539", "focusSearch" to true))
        val first = viewModel(handle)
        first.onTextChange("gpt")
        advanceTimeBy(DEBOUNCE_MS + 1)
        runCurrent()

        val recreated = viewModel(handle)

        assertEquals("gpt", recreated.uiState.value.text)
        assertFalse(recreated.focusSearch.value)
        assertEquals(1, lookupRepository.lookups.size)
    }

    @Test
    fun routeNoteShowsUntilTheTextChanges() = runTest {
        val viewModel = viewModel(SavedStateHandle(mapOf("query" to "Deep learning", "note" to SearchNote.NoIdInShare.name)))
        assertEquals(SearchNote.NoIdInShare, viewModel.note.value)

        viewModel.onTextChange("Deep learning review")

        assertNull(viewModel.note.value)
    }

    @Test
    fun focusIsRequestedOnce() = runTest {
        val viewModel = viewModel(SavedStateHandle(mapOf("focusSearch" to true)))
        assertTrue(viewModel.focusSearch.value)

        viewModel.onFocusHandled()

        assertFalse(viewModel.focusSearch.value)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :feature:search:testDebugUnitTest --tests "*SearchViewModelTest"`
Expected: FAIL — compilation errors: `Too many arguments for public constructor SearchViewModel`, `Unresolved reference: lookupState`, `LookupUiState`, `SearchNote`, `onRetryLookup`, `focusSearch`, `onFocusHandled`, `note`.

- [ ] **Step 3: Implement**

`feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchNote.kt`:
```kotlin
package com.etatech.hashiya.feature.search

/** Explains why Search opened the way it did after something was shared into the app. */
enum class SearchNote { NoIdInShare, NothingInShare }
```

`feature/search/src/main/java/com/etatech/hashiya/feature/search/navigation/SearchNavigation.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.feature.search.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.etatech.hashiya.feature.search.SearchScreen
import kotlinx.serialization.Serializable

/**
 * [query] is submitted immediately; [pageTitle] is a shared page's title for the not-found fallback;
 * [focusSearch] opens the keyboard; [note] is a `SearchNote` name. All are applied once.
 */
@Serializable
data class SearchRoute(
    val query: String? = null,
    val pageTitle: String? = null,
    val focusSearch: Boolean = false,
    val note: String? = null
)

fun NavController.navigateToSearch(navOptions: NavOptions? = null, route: SearchRoute = SearchRoute()) = navigate(route, navOptions)

fun NavGraphBuilder.searchScreen(onOpenSettings: () -> Unit) {
    composable<SearchRoute> { SearchScreen(onOpenSettings = onOpenSettings) }
}
```

In `feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchUiState.kt`, add the imports `com.etatech.hashiya.core.model.PaperIdentifier` and `com.etatech.hashiya.core.model.SearchError`, and append:
```kotlin
/** What Search shows when the submitted text is a DOI or arXiv ID ("ID mode"). */
sealed interface LookupUiState {
    data class Looking(val identifier: PaperIdentifier) : LookupUiState

    data class Found(val paper: Paper) : LookupUiState

    /** [searchTitle] is arXiv's title or the shared page's title, offered as a keyword search; null offers none. */
    data class NotFound(val identifier: PaperIdentifier, val searchTitle: String?) : LookupUiState

    data class Failed(val error: SearchError) : LookupUiState
}
```

In `feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchQueryState.kt`, append:
```kotlin
private const val KEY_ROUTE_APPLIED = "search_route_applied"
private const val KEY_PAGE_TITLE = "search_page_title"

// Names of SearchRoute's properties: navigation stores route arguments in the SavedStateHandle under these keys.
private const val ARG_QUERY = "query"
private const val ARG_PAGE_TITLE = "pageTitle"
private const val ARG_FOCUS_SEARCH = "focusSearch"
private const val ARG_NOTE = "note"

internal data class SearchRouteArgs(val query: String?, val pageTitle: String?, val focusSearch: Boolean, val note: SearchNote?)

/** The navigation arguments the first time this screen's state is created; null afterwards, including after process death. */
internal fun SavedStateHandle.consumeRouteArgs(): SearchRouteArgs? {
    if (get<Boolean>(KEY_ROUTE_APPLIED) == true || contains(KEY_TEXT)) return null
    this[KEY_ROUTE_APPLIED] = true
    return SearchRouteArgs(
        query = get<String>(ARG_QUERY)?.takeIf { it.isNotBlank() },
        pageTitle = get<String>(ARG_PAGE_TITLE)?.takeIf { it.isNotBlank() },
        focusSearch = get<Boolean>(ARG_FOCUS_SEARCH) ?: false,
        note = get<String>(ARG_NOTE)?.let { name -> SearchNote.entries.firstOrNull { it.name == name } }
    )
}

internal var SavedStateHandle.savedPageTitle: String?
    get() = get(KEY_PAGE_TITLE)
    set(value) {
        this[KEY_PAGE_TITLE] = value
    }
```

`feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchViewModel.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.LookupResult
import com.etatech.hashiya.core.data.repository.PaperLookupRepository
import com.etatech.hashiya.core.data.repository.SearchRepository
import com.etatech.hashiya.core.data.repository.SearchResults
import com.etatech.hashiya.core.data.repository.UserPreferencesRepository
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.SearchQuery
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.model.YearFilter
import com.etatech.hashiya.core.model.parsePaperIdentifier
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val DEBOUNCE_MS = 300L

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val searchRepository: SearchRepository,
    private val libraryRepository: LibraryRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val paperLookupRepository: PaperLookupRepository
) : ViewModel() {
    /** Arguments from Share or "Add paper": applied once, never over text restored after process death. */
    private val routeArgs = savedStateHandle.consumeRouteArgs()

    /** What the user sees: the field's text as typed plus the chip selections. */
    private val draft = MutableStateFlow(
        savedStateHandle.readSearchQuery().let { restored -> routeArgs?.query?.let { restored.copy(text = it) } ?: restored }
    )

    /** The text actually searched: set after the debounce, or immediately on IME search / suggestion / clear / route query. */
    private val submittedText = MutableStateFlow(draft.value.text)

    /** A shared page's title, offered as a title search if its ID isn't found; forgotten once the text is edited. */
    private val pageTitle = MutableStateFlow(routeArgs?.pageTitle ?: savedStateHandle.savedPageTitle)

    private val _note = MutableStateFlow(routeArgs?.note)
    val note: StateFlow<SearchNote?> = _note.asStateFlow()

    private val _focusSearch = MutableStateFlow(routeArgs?.focusSearch == true)
    val focusSearch: StateFlow<Boolean> = _focusSearch.asStateFlow()

    init {
        viewModelScope.launch {
            draft.map { it.text }.distinctUntilChanged().drop(1).debounce(DEBOUNCE_MS).collect { submittedText.value = it }
        }
        viewModelScope.launch {
            draft.collect { savedStateHandle.writeSearchQuery(it) }
        }
        viewModelScope.launch {
            pageTitle.collect { savedStateHandle.savedPageTitle = it }
        }
    }

    private val apiKey = userPreferencesRepository.userApiKey.distinctUntilChanged()

    /** The keyword query; null when the submitted text is blank or is a DOI / arXiv ID (ID mode). */
    private val activeQuery: StateFlow<SearchQuery?> = combine(draft, submittedText) { current, submitted ->
        submitted.trim().takeIf { it.isNotEmpty() && parsePaperIdentifier(it) == null }?.let { current.copy(text = it) }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** A new API key re-runs the active search, so fixing a rejected key in Settings takes effect right away. */
    private val results: StateFlow<SearchResults?> = combine(activeQuery, apiKey) { query, _ -> query }
        .map { query -> query?.let(searchRepository::search) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Cached per query. Library state is kept out of the paging stream so saving never re-maps cached pages. */
    val papers: Flow<PagingData<Paper>> = results
        .flatMapLatest { it?.papers ?: flowOf(PagingData.empty()) }
        .cachedIn(viewModelScope)

    /** OpenAlex IDs in the library; the UI combines this with each result to show "In library". */
    val savedIds: StateFlow<Set<String>> = libraryRepository.observeSavedIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val uiState: StateFlow<SearchUiState> = combine(
        draft,
        activeQuery,
        results.flatMapLatest { it?.totalCount ?: flowOf(null) }
    ) { current, active, count ->
        current.toUiState(isIdle = active == null, totalCount = count)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        draft.value.toUiState(isIdle = draft.value.text.isBlank(), totalCount = null)
    )

    /** The DOI or arXiv ID in the submitted text, or null for a keyword search. */
    private val identifier: StateFlow<PaperIdentifier?> = submittedText
        .map { parsePaperIdentifier(it) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, parsePaperIdentifier(submittedText.value))

    private val lookupRetries = MutableStateFlow(0)

    /** The latest lookup (a null result means "still looking"); a newer identifier, a retry or a key change cancels it. */
    private val lookup: StateFlow<Pair<PaperIdentifier, LookupResult?>?> =
        combine(identifier, lookupRetries, apiKey) { id, _, _ -> id }
            .flatMapLatest { id ->
                if (id == null) {
                    flowOf<Pair<PaperIdentifier, LookupResult?>?>(null)
                } else {
                    flow<Pair<PaperIdentifier, LookupResult?>?> {
                        emit(id to null)
                        emit(id to paperLookupRepository.lookup(id))
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val lookupState: StateFlow<LookupUiState?> = combine(lookup, pageTitle) { current, title ->
        current?.let { (id, result) -> result.toUiState(id, title) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val selectedPaper = MutableStateFlow<Paper?>(null)

    val selectedItem: StateFlow<PaperItem?> = combine(selectedPaper, savedIds) { paper, ids ->
        paper?.let { PaperItem(it, it.openAlexId in ids) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<SearchMessage?>(null)
    val message: StateFlow<SearchMessage?> = _message.asStateFlow()

    fun onTextChange(text: String) {
        if (text != draft.value.text) forgetShareContext()
        draft.update { it.copy(text = text) }
        if (text.isBlank()) submittedText.value = ""
    }

    fun onSearchAction() {
        submittedText.value = draft.value.text
    }

    fun onSuggestion(text: String) {
        forgetShareContext()
        draft.update { it.copy(text = text) }
        submittedText.value = text
    }

    fun onSortChange(sort: SearchSort) = draft.update { it.copy(sort = sort) }

    fun onYearFilterChange(years: YearFilter) = draft.update { it.copy(years = years) }

    fun onOpenAccessToggle() = draft.update { it.copy(openAccessOnly = !it.openAccessOnly) }

    fun onClearFilters() = draft.update { it.copy(years = YearFilter.AnyTime, openAccessOnly = false) }

    fun onRetryLookup() = lookupRetries.update { it + 1 }

    fun onFocusHandled() {
        _focusSearch.value = false
    }

    fun onPaperClick(paper: Paper) {
        selectedPaper.value = paper
    }

    fun onDismissPreview() {
        selectedPaper.value = null
    }

    fun onToggleSave(item: PaperItem) {
        viewModelScope.launch {
            try {
                if (item.inLibrary) {
                    libraryRepository.remove(item.paper.openAlexId)
                } else {
                    libraryRepository.save(item.paper)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = if (item.inLibrary) SearchMessage.RemoveFailed else SearchMessage.SaveFailed
            }
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    private fun forgetShareContext() {
        pageTitle.value = null
        _note.value = null
    }

    private fun SearchQuery.toUiState(isIdle: Boolean, totalCount: Long?) = SearchUiState(
        text = text,
        sort = sort,
        years = years,
        openAccessOnly = openAccessOnly,
        isIdle = isIdle,
        totalCount = totalCount
    )

    private fun LookupResult?.toUiState(identifier: PaperIdentifier, pageTitle: String?): LookupUiState = when (this) {
        null -> LookupUiState.Looking(identifier)
        is LookupResult.Found -> LookupUiState.Found(paper)
        is LookupResult.NotFound -> LookupUiState.NotFound(identifier, searchTitle = arxivTitle ?: pageTitle)
        is LookupResult.Failed -> LookupUiState.Failed(error)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :feature:search:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`; the 14 new `SearchViewModelTest` tests and every existing `feature/search` and `app` test pass (the app still compiles because `navigateToSearch(options)` keeps its first parameter and `hasRoute<SearchRoute>()` works for the data class).

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**'
git commit -m "feat: look up papers when Search is given a DOI or arXiv ID"
```

---
### Task 6: `feature/search` — ID-mode screen, share note, focus and new hint

**Files:**
- Create: `feature/search/src/main/java/com/etatech/hashiya/feature/search/components/LookupStates.kt`
- Modify: `feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchActions.kt` (full replacement below)
- Modify: `feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchScreen.kt` (full replacement below)
- Modify: `feature/search/src/main/res/values/strings.xml`, `feature/search/src/main/res/values-ar/strings.xml`
- Test: `feature/search/src/test/java/com/etatech/hashiya/feature/search/SearchLookupContentTest.kt`
- Test: `feature/search/src/test/java/com/etatech/hashiya/feature/search/SearchLookupScreenshotTest.kt`
- Test output: new `search_lookup_*` and `search_share_note-*` baselines; `search_idle-*` baselines change (new hint)

**Interfaces:**
- Consumes: `LookupUiState`, `SearchNote`, `SearchViewModel.lookupState/note/focusSearch/onRetryLookup/onFocusHandled` (Task 5); `PaperIdentifier` (Task 1); existing `PaperPreviewContent(paper, inLibrary, onToggleSave, onOpenDoi, modifier)`, `EmptyState`, `LoadingSkeleton`, `SearchErrorState`, `HashiyaIcons`, `SEARCH_FIELD_TAG`, test helpers `PHONE_QUALIFIERS`, `ScreenshotVariant`, `ScreenshotVariantRule`, `captureScreenshot`.
- Produces:
  - `SearchActions` gains `onRetryLookup: () -> Unit = {}` and `onFocusHandled: () -> Unit = {}`.
  - `SearchContent(...)` gains `lookupState: LookupUiState? = null`, `note: SearchNote? = null`, `focusSearch: Boolean = false` (after `modifier`, before `currentYear`), so every existing call keeps compiling.
  - The Search field hint reads "Search, or paste a DOI, arXiv ID or link" — Task 7's app test looks for it.

- [ ] **Step 1: Write the failing tests**

`feature/search/src/test/java/com/etatech/hashiya/feature/search/SearchLookupContentTest.kt`:
```kotlin
package com.etatech.hashiya.feature.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.feature.search.components.SEARCH_FIELD_TAG
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class SearchLookupContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()
    private val bertTitle = "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding"

    private val actions = SearchActions(
        onSuggestion = { events += "suggestion:$it" },
        onToggleSave = { events += "toggle:${it.paper.openAlexId}:${it.inLibrary}" },
        onRetryLookup = { events += "retry" },
        onOpenSettings = { events += "settings" },
        onFocusHandled = { events += "focusHandled" }
    )

    private fun show(
        text: String = "10.1038/nature14539",
        lookupState: LookupUiState? = null,
        note: SearchNote? = null,
        focusSearch: Boolean = false,
        savedIds: Set<String> = emptySet()
    ) = composeRule.setContent {
        HashiyaTheme {
            SearchContent(
                uiState = SearchUiState(text = text),
                papers = flowOf(PagingData.empty<Paper>()).collectAsLazyPagingItems(),
                savedIds = savedIds,
                selectedItem = null,
                message = null,
                actions = actions,
                lookupState = lookupState,
                note = note,
                focusSearch = focusSearch,
                currentYear = 2026
            )
        }
    }

    @Test
    fun lookingShowsTheIdentifierAndHidesFilters() {
        show(lookupState = LookupUiState.Looking(PaperIdentifier.Doi("10.1038/nature14539")))

        composeRule.onNodeWithText("Looking up DOI 10.1038/nature14539…").assertIsDisplayed()
        composeRule.onNodeWithText("Relevance").assertDoesNotExist()
    }

    @Test
    fun lookingForArxivNamesArxiv() {
        show(text = "1706.03762", lookupState = LookupUiState.Looking(PaperIdentifier.Arxiv("1706.03762")))
        composeRule.onNodeWithText("Looking up arXiv 1706.03762…").assertIsDisplayed()
    }

    @Test
    fun foundShowsThePreviewAndSaves() {
        show(lookupState = LookupUiState.Found(SamplePapers.attention))

        composeRule.onNodeWithText(SamplePapers.attention.title).assertIsDisplayed()
        composeRule.onNodeWithText("Save to library").performClick()
        assertEquals(listOf("toggle:${SamplePapers.attention.openAlexId}:false"), events)
    }

    @Test
    fun foundPaperAlreadySavedOffersRemove() {
        show(lookupState = LookupUiState.Found(SamplePapers.attention), savedIds = setOf(SamplePapers.attention.openAlexId))

        composeRule.onNodeWithText("Remove from library").performClick()
        assertEquals(listOf("toggle:${SamplePapers.attention.openAlexId}:true"), events)
    }

    @Test
    fun notFoundOffersATitleSearch() {
        show(text = "1810.04805", lookupState = LookupUiState.NotFound(PaperIdentifier.Arxiv("1810.04805"), bertTitle))

        composeRule.onNodeWithText("No paper found for this arXiv ID").assertIsDisplayed()
        composeRule.onNodeWithText("Search for", substring = true).performClick()
        assertEquals(listOf("suggestion:$bertTitle"), events)
    }

    @Test
    fun notFoundWithoutTitleHasNoButton() {
        show(lookupState = LookupUiState.NotFound(PaperIdentifier.Doi("10.1038/nature14539"), searchTitle = null))

        composeRule.onNodeWithText("No paper found for this DOI").assertIsDisplayed()
        composeRule.onNodeWithText("Search for", substring = true).assertDoesNotExist()
    }

    @Test
    fun failedLookupRetries() {
        show(lookupState = LookupUiState.Failed(SearchError.Offline))

        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(listOf("retry"), events)
    }

    @Test
    fun rejectedKeyOpensSettings() {
        show(lookupState = LookupUiState.Failed(SearchError.InvalidUserKey))

        composeRule.onNodeWithText("Open Settings").performClick()
        assertEquals(listOf("settings"), events)
    }

    @Test
    fun shareNoteIsShown() {
        show(text = "Deep learning", note = SearchNote.NoIdInShare)
        composeRule.onNodeWithText("No DOI or arXiv ID in the shared link — searching by page title.").assertIsDisplayed()
    }

    @Test
    fun emptyShareNoteIsShown() {
        show(text = "", note = SearchNote.NothingInShare)
        composeRule.onNodeWithText("Couldn't find a paper in what you shared.").assertIsDisplayed()
    }

    @Test
    fun focusRequestIsHonouredOnce() {
        show(text = "", focusSearch = true)

        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).assertIsFocused()
        assertEquals(listOf("focusHandled"), events)
    }

    @Test
    fun hintMentionsIds() {
        show(text = "")
        composeRule.onNodeWithText("Search, or paste a DOI, arXiv ID or link").assertIsDisplayed()
    }
}
```

`feature/search/src/test/java/com/etatech/hashiya/feature/search/SearchLookupScreenshotTest.kt`:
```kotlin
package com.etatech.hashiya.feature.search

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.ScreenshotVariant
import com.etatech.hashiya.core.testing.ScreenshotVariantRule
import com.etatech.hashiya.core.testing.captureScreenshot
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class SearchLookupScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val bertTitle = "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding"

    private fun capture(
        name: String,
        text: String,
        arabicText: String,
        lookupState: LookupUiState? = null,
        note: SearchNote? = null,
        data: PagingData<Paper> = PagingData.empty()
    ) = composeRule.captureScreenshot(name, variant, arabicText) {
        SearchContent(
            uiState = SearchUiState(text = text, isIdle = lookupState != null || text.isBlank(), totalCount = null),
            papers = flowOf(data).collectAsLazyPagingItems(),
            savedIds = emptySet(),
            selectedItem = null,
            message = null,
            actions = SearchActions(),
            lookupState = lookupState,
            note = note,
            currentYear = 2026
        )
    }

    @Test
    fun looking() = capture(
        "search_lookup_looking",
        text = "10.1038/nature14539",
        arabicText = "جارٍ البحث عن DOI",
        lookupState = LookupUiState.Looking(PaperIdentifier.Doi("10.1038/nature14539"))
    )

    @Test
    fun found() = capture(
        "search_lookup_found",
        text = "arXiv:1706.03762",
        arabicText = "حفظ في المكتبة",
        lookupState = LookupUiState.Found(SamplePapers.attention)
    )

    @Test
    fun notFound() = capture(
        "search_lookup_not_found",
        text = "1810.04805",
        arabicText = "لم يتم العثور على ورقة",
        lookupState = LookupUiState.NotFound(PaperIdentifier.Arxiv("1810.04805"), bertTitle)
    )

    @Test
    fun error() = capture(
        "search_lookup_error",
        text = "10.1038/nature14539",
        arabicText = "تعذّر الوصول إلى OpenAlex",
        lookupState = LookupUiState.Failed(SearchError.Offline)
    )

    @Test
    fun shareNote() = capture(
        "search_share_note",
        text = "Deep learning",
        arabicText = "لا يوجد DOI",
        note = SearchNote.NoIdInShare,
        data = PagingData.from(
            listOf(SamplePapers.bert, SamplePapers.vit),
            LoadStates(
                refresh = LoadState.NotLoading(endOfPaginationReached = false),
                prepend = LoadState.NotLoading(endOfPaginationReached = true),
                append = LoadState.NotLoading(endOfPaginationReached = true)
            )
        )
    )

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
```

Note for `shareNote`: the capture helper passes `isIdle = false` when there is text and no lookup, so the results list shows under the note.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :feature:search:testDebugUnitTest --tests "*SearchLookupContentTest" --tests "*SearchLookupScreenshotTest"`
Expected: FAIL — `No parameter with name 'onRetryLookup'`, `'onFocusHandled'`, `'lookupState'`, `'note'`, `'focusSearch'`.

- [ ] **Step 3: Add strings**

In `feature/search/src/main/res/values/strings.xml`, change `search_placeholder` and add the new strings before `</resources>`:
```xml
    <string name="search_placeholder">Search, or paste a DOI, arXiv ID or link</string>
```
```xml
    <string name="search_lookup_looking_doi">Looking up DOI %1$s…</string>
    <string name="search_lookup_looking_arxiv">Looking up arXiv %1$s…</string>
    <string name="search_lookup_not_found_doi">No paper found for this DOI</string>
    <string name="search_lookup_not_found_arxiv">No paper found for this arXiv ID</string>
    <string name="search_lookup_not_found_message">Check the ID, or search by the paper\'s title.</string>
    <string name="search_lookup_search_title">Search for “%1$s”</string>
    <string name="search_note_no_id">No DOI or arXiv ID in the shared link — searching by page title.</string>
    <string name="search_note_nothing">Couldn\'t find a paper in what you shared.</string>
```

In `feature/search/src/main/res/values-ar/strings.xml`, change `search_placeholder` and add before `</resources>`:
```xml
    <string name="search_placeholder">ابحث، أو الصق DOI أو معرّف arXiv أو رابطًا</string>
```
```xml
    <string name="search_lookup_looking_doi">جارٍ البحث عن DOI %1$s…</string>
    <string name="search_lookup_looking_arxiv">جارٍ البحث عن arXiv %1$s…</string>
    <string name="search_lookup_not_found_doi">لم يتم العثور على ورقة بهذا الـ DOI</string>
    <string name="search_lookup_not_found_arxiv">لم يتم العثور على ورقة بمعرّف arXiv هذا</string>
    <string name="search_lookup_not_found_message">تحقّق من المعرّف، أو ابحث بعنوان الورقة.</string>
    <string name="search_lookup_search_title">ابحث عن «%1$s»</string>
    <string name="search_note_no_id">لا يوجد DOI أو معرّف arXiv في الرابط المشارك — يتم البحث بعنوان الصفحة.</string>
    <string name="search_note_nothing">تعذّر العثور على ورقة فيما شاركته.</string>
```

- [ ] **Step 4: Implement**

`feature/search/src/main/java/com/etatech/hashiya/feature/search/components/LookupStates.kt`:
```kotlin
package com.etatech.hashiya.feature.search.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.component.EmptyState
import com.etatech.hashiya.core.designsystem.component.LoadingSkeleton
import com.etatech.hashiya.core.designsystem.component.PaperPreviewContent
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.feature.search.LookupUiState
import com.etatech.hashiya.feature.search.PaperItem
import com.etatech.hashiya.feature.search.R
import com.etatech.hashiya.feature.search.SearchNote

private const val MAX_TITLE_IN_BUTTON = 60

/** Search's body in ID mode: the found paper's preview is shown in the screen, not in a sheet. */
@Composable
internal fun LookupBody(
    state: LookupUiState,
    savedIds: Set<String>,
    onToggleSave: (PaperItem) -> Unit,
    onOpenDoi: (String) -> Unit,
    onSearchTitle: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        is LookupUiState.Looking -> Column(modifier.fillMaxWidth()) {
            Text(
                text = lookingLabel(state.identifier),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LoadingSkeleton(rows = 1)
        }

        is LookupUiState.Found -> {
            val item = PaperItem(state.paper, inLibrary = state.paper.openAlexId in savedIds)
            PaperPreviewContent(
                paper = state.paper,
                inLibrary = item.inLibrary,
                onToggleSave = { onToggleSave(item) },
                onOpenDoi = onOpenDoi,
                modifier = modifier.padding(top = 8.dp)
            )
        }

        is LookupUiState.NotFound -> EmptyState(
            icon = HashiyaIcons.SearchOff,
            title = stringResource(notFoundTitle(state.identifier)),
            message = stringResource(R.string.search_lookup_not_found_message),
            modifier = modifier,
            actionLabel = state.searchTitle?.let { stringResource(R.string.search_lookup_search_title, shortTitle(it)) },
            onAction = { state.searchTitle?.let(onSearchTitle) }
        )

        is LookupUiState.Failed -> SearchErrorState(state.error, onRetry, onOpenSettings, modifier)
    }
}

/** Why Search opened the way it did after a share. */
@Composable
internal fun SearchNoteBanner(note: SearchNote, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            when (note) {
                SearchNote.NoIdInShare -> R.string.search_note_no_id
                SearchNote.NothingInShare -> R.string.search_note_nothing
            }
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun lookingLabel(identifier: PaperIdentifier): String = when (identifier) {
    is PaperIdentifier.Doi -> stringResource(R.string.search_lookup_looking_doi, identifier.value)
    is PaperIdentifier.Arxiv -> stringResource(R.string.search_lookup_looking_arxiv, identifier.id)
}

@StringRes
private fun notFoundTitle(identifier: PaperIdentifier): Int = when (identifier) {
    is PaperIdentifier.Doi -> R.string.search_lookup_not_found_doi
    is PaperIdentifier.Arxiv -> R.string.search_lookup_not_found_arxiv
}

internal fun shortTitle(title: String): String =
    if (title.length <= MAX_TITLE_IN_BUTTON) title else title.take(MAX_TITLE_IN_BUTTON - 1).trimEnd() + "…"
```

`feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchActions.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya.feature.search

import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.model.YearFilter

/** Every user action on the search screen. Defaults are no-ops so tests set only what they check. */
internal data class SearchActions(
    val onTextChange: (String) -> Unit = {},
    val onSearchAction: () -> Unit = {},
    val onSuggestion: (String) -> Unit = {},
    val onSortChange: (SearchSort) -> Unit = {},
    val onYearFilterChange: (YearFilter) -> Unit = {},
    val onOpenAccessToggle: () -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onPaperClick: (Paper) -> Unit = {},
    val onToggleSave: (PaperItem) -> Unit = {},
    val onDismissPreview: () -> Unit = {},
    val onOpenDoi: (String) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onRetryLookup: () -> Unit = {},
    val onFocusHandled: () -> Unit = {}
)
```

In `feature/search/src/main/java/com/etatech/hashiya/feature/search/SearchScreen.kt`, make exactly these changes (everything else, including `SearchBody`, `ResultsList` and `asSearchError`, stays as it is):

1. Add the imports:
```kotlin
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.etatech.hashiya.feature.search.components.LookupBody
import com.etatech.hashiya.feature.search.components.SearchNoteBanner
```

2. Replace the `SearchScreen` function with:
```kotlin
@Composable
internal fun SearchScreen(onOpenSettings: () -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedItem.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val lookupState by viewModel.lookupState.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val focusSearch by viewModel.focusSearch.collectAsStateWithLifecycle()
    val papers = viewModel.papers.collectAsLazyPagingItems()
    val uriHandler = LocalUriHandler.current
    SearchContent(
        uiState = uiState,
        papers = papers,
        savedIds = savedIds,
        selectedItem = selectedItem,
        message = message,
        lookupState = lookupState,
        note = note,
        focusSearch = focusSearch,
        actions = SearchActions(
            onTextChange = viewModel::onTextChange,
            onSearchAction = viewModel::onSearchAction,
            onSuggestion = viewModel::onSuggestion,
            onSortChange = viewModel::onSortChange,
            onYearFilterChange = viewModel::onYearFilterChange,
            onOpenAccessToggle = viewModel::onOpenAccessToggle,
            onClearFilters = viewModel::onClearFilters,
            onPaperClick = viewModel::onPaperClick,
            onToggleSave = viewModel::onToggleSave,
            onDismissPreview = viewModel::onDismissPreview,
            onOpenDoi = { doi -> runCatching { uriHandler.openUri("https://doi.org/$doi") } },
            onOpenSettings = onOpenSettings,
            onMessageShown = viewModel::onMessageShown,
            onRetryLookup = viewModel::onRetryLookup,
            onFocusHandled = viewModel::onFocusHandled
        )
    )
}
```

3. Replace the `SearchContent` signature line block
```kotlin
    actions: SearchActions,
    modifier: Modifier = Modifier,
    currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
) {
```
with
```kotlin
    actions: SearchActions,
    modifier: Modifier = Modifier,
    lookupState: LookupUiState? = null,
    note: SearchNote? = null,
    focusSearch: Boolean = false,
    currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            // Wait for the first frame so the field is attached and can take focus.
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboard?.show()
            actions.onFocusHandled()
        }
    }
```

4. Replace the `Column(Modifier.padding(padding)) { … }` block inside the `Scaffold` content with:
```kotlin
        Column(Modifier.padding(padding)) {
            SearchField(uiState.text, actions.onTextChange, actions.onSearchAction, Modifier.focusRequester(focusRequester))
            note?.let { SearchNoteBanner(it) }
            if (lookupState == null) {
                FilterChipRow(
                    sort = uiState.sort,
                    years = uiState.years,
                    openAccessOnly = uiState.openAccessOnly,
                    currentYear = currentYear,
                    onSortChange = actions.onSortChange,
                    onYearFilterChange = actions.onYearFilterChange,
                    onOpenAccessToggle = actions.onOpenAccessToggle
                )
            }
            Box(Modifier.fillMaxSize()) {
                if (lookupState != null) {
                    LookupBody(
                        state = lookupState,
                        savedIds = savedIds,
                        onToggleSave = actions.onToggleSave,
                        onOpenDoi = actions.onOpenDoi,
                        onSearchTitle = actions.onSuggestion,
                        onRetry = actions.onRetryLookup,
                        onOpenSettings = actions.onOpenSettings
                    )
                } else {
                    SearchBody(uiState, papers, savedIds, actions)
                }
            }
        }
```

- [ ] **Step 5: Run the tests and inspect the screenshots locally**

Run: `./gradlew :feature:search:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; the 12 `SearchLookupContentTest` tests, the 20 `SearchLookupScreenshotTest` variants and all existing `feature/search` tests pass (the Arabic assertions included).

Run: `./gradlew :feature:search:recordRoborazziDebug` and open the new `search_lookup_*` and `search_share_note-*` files plus `search_idle-*`. Check: the found paper's preview fills the screen with Save to library and Open DOI; the Arabic variants mirror the layout while the English title reads left-to-right; the not-found button shows a shortened BERT title; the idle hint mentions DOI and arXiv. These local images are for inspection only.

- [ ] **Step 6: Commit the code without the local screenshots**

```bash
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**' ':(exclude,glob)**/src/test/screenshots/**'
git commit -m "feat: show paper lookups, share notes and the new hint in Search"
```

- [ ] **Step 7: Record the baselines on Linux and commit them**

Run (10–15 minutes; run it in the background): `bash scripts/record-screenshots-on-linux.sh`
Expected: ends with `Baselines copied from run <id>`; `git status` shows the 20 new `search_lookup_*` / `search_share_note-*` files and the 4 changed `search_idle-*` files, and no other changed baselines. Open one English and one Arabic image to confirm they match what you inspected.

```bash
git add -- ':(glob)**/src/test/screenshots/**'
git commit -m "test: record search lookup screenshot baselines on Linux"
```

---
### Task 7: `feature/library` + `app` — "Add paper" button

**Files:**
- Modify: `core/designsystem/src/main/java/com/etatech/hashiya/core/designsystem/icon/HashiyaIcons.kt`
- Modify: `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryScreen.kt`
- Modify: `feature/library/src/main/java/com/etatech/hashiya/feature/library/navigation/LibraryNavigation.kt`
- Modify: `feature/library/src/main/res/values/strings.xml`, `feature/library/src/main/res/values-ar/strings.xml`
- Modify: `app/src/main/java/com/etatech/hashiya/navigation/HashiyaApp.kt`
- Test: `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryContentTest.kt`
- Test: `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibrarySwipeUndoTest.kt` (call-site update only)
- Test: `app/src/test/java/com/etatech/hashiya/HashiyaAppNavigationTest.kt`
- Test output: `library_empty-*` and `library_papers-*` baselines change (the button appears)

**Interfaces:**
- Consumes: `SearchRoute(focusSearch = true)`, `navigateToSearch(navOptions, route)` (Task 5); the new Search hint (Task 6).
- Produces:
  - `HashiyaIcons.Add`.
  - `LibraryContent(..., onAddPaper: () -> Unit = {}, modifier)`, `LibraryScreen(onGoToSearch, onAddPaper, onOpenSettings)`, `NavGraphBuilder.libraryScreen(onGoToSearch: () -> Unit, onAddPaper: () -> Unit, onOpenSettings: () -> Unit)`.
  - In `app`: `internal fun NavController.openSearch(route: SearchRoute)` — opens a fresh Search above the Library with the route's arguments (Task 8 reuses it).

- [ ] **Step 1: Write the failing tests**

In `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryContentTest.kt`, in the `show(...)` helper add `onAddPaper = { events += "addPaper" },` right after `onOpenDoi = {}` (add a comma after `onOpenDoi = {}`), and add these tests:
```kotlin
    // The extended FAB's label is only in the unmerged semantics tree.
    @Test
    fun addPaperButtonOnEmptyLibrary() {
        show(LibraryUiState.Empty)

        composeRule.onNodeWithText("Add paper", useUnmergedTree = true).performClick()
        assertEquals(listOf("addPaper"), events)
    }

    @Test
    fun addPaperButtonWithPapers() {
        show(LibraryUiState.Papers(listOf(SamplePapers.bert)))

        composeRule.onNodeWithText("Add paper", useUnmergedTree = true).performClick()
        assertEquals(listOf("addPaper"), events)
    }
```

In `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibrarySwipeUndoTest.kt`, change the call `LibraryScreen(onGoToSearch = {}, onOpenSettings = {}, viewModel = viewModel)` to `LibraryScreen(onGoToSearch = {}, onAddPaper = {}, onOpenSettings = {}, viewModel = viewModel)` so it compiles with the new parameter.

In `app/src/test/java/com/etatech/hashiya/HashiyaAppNavigationTest.kt`, add the import `androidx.compose.ui.test.assertIsFocused` and these tests:
```kotlin
    @Test
    fun addPaperOpensSearchReadyForAnId() {
        waitForText("No saved papers yet")

        composeRule.onNodeWithText("Add paper", useUnmergedTree = true).performClick()

        waitForText("Search, or paste a DOI, arXiv ID or link")
        composeRule.onNodeWithText("Search, or paste a DOI, arXiv ID or link").assertIsFocused()
    }

    @Test
    fun libraryTabWorksAfterAddPaper() {
        waitForText("No saved papers yet")
        composeRule.onNodeWithText("Add paper", useUnmergedTree = true).performClick()
        waitForText("Search, or paste a DOI, arXiv ID or link")

        composeRule.onAllNodesWithText("Library").onFirst().performClick()

        waitForText("No saved papers yet")
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :feature:library:testDebugUnitTest --tests "*LibraryContentTest"`
Expected: FAIL — `No parameter with name 'onAddPaper' found`.

- [ ] **Step 3: Implement**

In `core/designsystem/src/main/java/com/etatech/hashiya/core/designsystem/icon/HashiyaIcons.kt`, add the import `androidx.compose.material.icons.outlined.Add` and, inside `object HashiyaIcons`:
```kotlin
    val Add: ImageVector = Icons.Outlined.Add
```

In `feature/library/src/main/res/values/strings.xml` add before `</resources>`:
```xml
    <string name="library_add_paper">Add paper</string>
```
In `feature/library/src/main/res/values-ar/strings.xml` add before `</resources>`:
```xml
    <string name="library_add_paper">إضافة ورقة</string>
```

In `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryScreen.kt`:

1. Add the import `androidx.compose.material3.ExtendedFloatingActionButton`.
2. Change the `LibraryScreen` signature to `internal fun LibraryScreen(onGoToSearch: () -> Unit, onAddPaper: () -> Unit, onOpenSettings: () -> Unit, viewModel: LibraryViewModel = hiltViewModel())` and pass `onAddPaper = onAddPaper,` to `LibraryContent` (after `onOpenDoi = …`).
3. In `LibraryContent`'s parameters, replace
```kotlin
    onOpenDoi: (String) -> Unit,
    modifier: Modifier = Modifier
) {
```
with
```kotlin
    onOpenDoi: (String) -> Unit,
    onAddPaper: () -> Unit = {},
    modifier: Modifier = Modifier
) {
```
4. In the `Scaffold(...)` call, add after `snackbarHost = { SnackbarHost(snackbarHostState) }` (add a comma after it):
```kotlin
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPaper,
                icon = { Icon(HashiyaIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.library_add_paper)) }
            )
        }
```

`feature/library/src/main/java/com/etatech/hashiya/feature/library/navigation/LibraryNavigation.kt` — replace `libraryScreen` with:
```kotlin
fun NavGraphBuilder.libraryScreen(onGoToSearch: () -> Unit, onAddPaper: () -> Unit, onOpenSettings: () -> Unit) {
    composable<LibraryRoute> {
        LibraryScreen(onGoToSearch = onGoToSearch, onAddPaper = onAddPaper, onOpenSettings = onOpenSettings)
    }
}
```

In `app/src/main/java/com/etatech/hashiya/navigation/HashiyaApp.kt`:

1. Replace the `libraryScreen(...)` call inside `NavHost` with:
```kotlin
            libraryScreen(
                onGoToSearch = { navController.navigateToTopLevel(TopLevelDestination.Search) },
                onAddPaper = { navController.openSearch(SearchRoute(focusSearch = true)) },
                onOpenSettings = { navController.navigateToSettings() }
            )
```
2. Add at the end of the file:
```kotlin
/**
 * Opens a fresh Search above the Library with [route]'s arguments, instead of restoring the previous search.
 * The pop saves state like the tab navigation does; without it, tapping the Library tab afterwards does nothing.
 */
internal fun NavController.openSearch(route: SearchRoute) = navigateToSearch(
    navOptions = navOptions { popUpTo(graph.findStartDestination().id) { saveState = true } },
    route = route
)
```

- [ ] **Step 4: Run the tests and inspect the screenshots locally**

Run: `./gradlew :feature:library:testDebugUnitTest :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; the 2 new library tests, the 2 new app tests and all existing tests pass.

Run: `./gradlew :feature:library:recordRoborazziDebug` and open `library_empty-*` and `library_papers-*`. Check: the "Add paper" button sits bottom-right in English and bottom-left in Arabic, and doesn't cover the "Go to Search" button. These local images are for inspection only.

- [ ] **Step 5: Commit the code without the local screenshots**

```bash
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**' ':(exclude,glob)**/src/test/screenshots/**'
git commit -m "feat: add an Add paper button to the Library"
```

- [ ] **Step 6: Record the baselines on Linux and commit them**

Run (10–15 minutes; run it in the background): `bash scripts/record-screenshots-on-linux.sh`
Expected: ends with `Baselines copied from run <id>`; `git status` shows the 8 changed `library_*` files and no other changed baselines.

```bash
git add -- ':(glob)**/src/test/screenshots/**'
git commit -m "test: record library screenshot baselines with the Add paper button on Linux"
```

---

### Task 8: `app` — receive shares from the browser

**Files:**
- Create: `app/src/main/java/com/etatech/hashiya/share/ShareToSearchRoute.kt`
- Modify: `app/src/main/java/com/etatech/hashiya/MainActivity.kt` (full replacement below)
- Modify: `app/src/main/java/com/etatech/hashiya/navigation/HashiyaApp.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/etatech/hashiya/share/ShareToSearchRouteTest.kt`
- Test: `app/src/test/java/com/etatech/hashiya/share/ShareIntentFilterTest.kt`
- Test: `app/src/test/java/com/etatech/hashiya/ShareNavigationTest.kt`

**Interfaces:**
- Consumes: `extractPaperIdentifier`, `PaperIdentifier` (Task 1); `SearchRoute`, `SearchNote` (Task 5); `openSearch(route)` (Task 7).
- Produces: `internal fun shareToSearchRoute(text: String?, subject: String?): SearchRoute`; `HashiyaApp(navController, pendingSearch: SearchRoute? = null, onPendingSearchHandled: () -> Unit = {})`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/etatech/hashiya/share/ShareToSearchRouteTest.kt`:
```kotlin
package com.etatech.hashiya.share

import com.etatech.hashiya.feature.search.SearchNote
import com.etatech.hashiya.feature.search.navigation.SearchRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareToSearchRouteTest {
    @Test
    fun arxivLinkOpensTheArxivLookup() {
        assertEquals(
            SearchRoute(query = "arXiv:1706.03762", pageTitle = "Attention Is All You Need"),
            shareToSearchRoute("https://arxiv.org/abs/1706.03762", "Attention Is All You Need")
        )
    }

    @Test
    fun doiLinkOpensTheDoiLookup() {
        assertEquals(
            SearchRoute(query = "10.1038/nature14539", pageTitle = "Deep learning | Nature"),
            shareToSearchRoute("https://doi.org/10.1038/nature14539", "Deep learning | Nature")
        )
    }

    @Test
    fun publisherPageWithDoiOpensTheDoiLookup() {
        assertEquals(
            SearchRoute(query = "10.1145/3292500.3330701"),
            shareToSearchRoute("https://dl.acm.org/doi/10.1145/3292500.3330701", null)
        )
    }

    @Test
    fun idInsideSharedTextIsFound() {
        assertEquals(
            SearchRoute(query = "arXiv:2401.00001"),
            shareToSearchRoute("Check this out: https://arxiv.org/abs/2401.00001v2", "  ")
        )
    }

    @Test
    fun pageWithoutIdSearchesItsTitle() {
        assertEquals(
            SearchRoute(query = "Deep learning", note = SearchNote.NoIdInShare.name),
            shareToSearchRoute("https://www.nature.com/articles/nature14539", " Deep learning ")
        )
    }

    @Test
    fun missingTextButTitleSearchesTheTitle() {
        assertEquals(
            SearchRoute(query = "Deep learning", note = SearchNote.NoIdInShare.name),
            shareToSearchRoute(null, "Deep learning")
        )
    }

    @Test
    fun nothingUsableShowsTheNote() {
        assertEquals(SearchRoute(note = SearchNote.NothingInShare.name), shareToSearchRoute("just some words", null))
        assertEquals(SearchRoute(note = SearchNote.NothingInShare.name), shareToSearchRoute(null, "   "))
    }
}
```

`app/src/test/java/com/etatech/hashiya/share/ShareIntentFilterTest.kt`:
```kotlin
package com.etatech.hashiya.share

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ShareIntentFilterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun activitiesFor(type: String) = context.packageManager
        .queryIntentActivities(Intent(Intent.ACTION_SEND).setType(type).setPackage(context.packageName), 0)
        .map { it.activityInfo.name }

    @Test
    fun plainTextSharesOpenMainActivity() {
        assertEquals(listOf(MainActivity::class.java.name), activitiesFor("text/plain"))
    }

    @Test
    fun imageSharesAreNotHandled() {
        assertTrue(activitiesFor("image/png").isEmpty())
    }
}
```

`app/src/test/java/com/etatech/hashiya/ShareNavigationTest.kt`:
```kotlin
package com.etatech.hashiya

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Shares without a paper ID, so no request leaves the test. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ShareNavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private val nothingNote = "Couldn't find a paper in what you shared."

    private fun shareIntent(text: String) =
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)

    private fun waitForText(text: String) = composeRule.waitUntil(timeoutMillis = 5_000) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun shareWithoutAPaperOpensSearchWithANote() {
        ActivityScenario.launch<MainActivity>(shareIntent("just some words")).use {
            waitForText(nothingNote)
            composeRule.onNodeWithText(nothingNote).assertIsDisplayed()
        }
    }

    @Test
    fun shareIsHandledOnceAcrossRecreation() {
        ActivityScenario.launch<MainActivity>(shareIntent("just some words")).use { scenario ->
            waitForText(nothingNote)
            composeRule.onAllNodesWithText("Library").onFirst().performClick()
            waitForText("No saved papers yet")

            scenario.recreate()
            composeRule.waitForIdle()

            composeRule.onNodeWithText("No saved papers yet").assertIsDisplayed()
            composeRule.onNodeWithText(nothingNote).assertDoesNotExist()
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*ShareToSearchRouteTest" --tests "*ShareIntentFilterTest" --tests "*ShareNavigationTest"`
Expected: FAIL — `Unresolved reference: shareToSearchRoute` (the other two classes fail once it compiles: no intent filter, no share handling).

- [ ] **Step 3: Implement**

`app/src/main/java/com/etatech/hashiya/share/ShareToSearchRoute.kt`:
```kotlin
package com.etatech.hashiya.share

import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.extractPaperIdentifier
import com.etatech.hashiya.feature.search.SearchNote
import com.etatech.hashiya.feature.search.navigation.SearchRoute

/**
 * Where a share from another app should land: a lookup when the shared text holds a DOI or arXiv ID,
 * otherwise a keyword search for the page title, otherwise an empty Search with an explanation.
 */
internal fun shareToSearchRoute(text: String?, subject: String?): SearchRoute {
    val title = subject?.trim()?.takeIf { it.isNotEmpty() }
    val identifier = text?.let(::extractPaperIdentifier)
    return when {
        identifier != null -> SearchRoute(query = identifier.asQuery(), pageTitle = title)
        title != null -> SearchRoute(query = title, note = SearchNote.NoIdInShare.name)
        else -> SearchRoute(note = SearchNote.NothingInShare.name)
    }
}

/** Text the Search box's strict parser recognizes as the same identifier. */
private fun PaperIdentifier.asQuery(): String = when (this) {
    is PaperIdentifier.Doi -> value
    is PaperIdentifier.Arxiv -> "arXiv:$id"
}
```

`app/src/main/java/com/etatech/hashiya/MainActivity.kt` (replace the whole file):
```kotlin
package com.etatech.hashiya

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.feature.search.navigation.SearchRoute
import com.etatech.hashiya.navigation.HashiyaApp
import com.etatech.hashiya.share.shareToSearchRoute
import dagger.hilt.android.AndroidEntryPoint

/** AppCompatActivity so that AppCompatDelegate.setApplicationLocales can switch the language in-app. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    /** A shared page waiting to be opened in Search; cleared once navigation has happened. */
    private var pendingSearch by mutableStateOf<SearchRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a rotation the intent is still the share; it was handled before the activity was recreated.
        if (savedInstanceState == null) pendingSearch = intent.sharedSearchRoute()
        setContent {
            HashiyaTheme {
                HashiyaApp(pendingSearch = pendingSearch, onPendingSearchHandled = { pendingSearch = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.sharedSearchRoute()?.let { pendingSearch = it }
    }

    private fun Intent.sharedSearchRoute(): SearchRoute? {
        if (action != Intent.ACTION_SEND || type?.startsWith("text/") != true) return null
        val subject = getStringExtra(Intent.EXTRA_SUBJECT) ?: getStringExtra(Intent.EXTRA_TITLE)
        return shareToSearchRoute(getStringExtra(Intent.EXTRA_TEXT), subject)
    }
}
```

In `app/src/main/java/com/etatech/hashiya/navigation/HashiyaApp.kt`:

1. Add the import `androidx.compose.runtime.LaunchedEffect`.
2. Change the signature to:
```kotlin
@Composable
fun HashiyaApp(
    navController: NavHostController = rememberNavController(),
    pendingSearch: SearchRoute? = null,
    onPendingSearchHandled: () -> Unit = {}
) {
```
3. Inside the `NavigationSuiteScaffold { … }` content, right after the `NavHost(...) { … }` block (so the navigation graph exists when it runs), add:
```kotlin
        LaunchedEffect(pendingSearch) {
            pendingSearch?.let { route ->
                navController.openSearch(route)
                onPendingSearchHandled()
            }
        }
```

In `app/src/main/AndroidManifest.xml`, add a second intent filter inside `<activity android:name=".MainActivity" …>`, after the launcher filter:
```xml
            <intent-filter>
                <action android:name="android.intent.action.SEND" />

                <category android:name="android.intent.category.DEFAULT" />

                <data android:mimeType="text/plain" />
            </intent-filter>
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`; 7 `ShareToSearchRouteTest`, 2 `ShareIntentFilterTest`, 2 `ShareNavigationTest` tests and all existing app tests pass.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add -A -- . ':(exclude).idea/**'
git commit -m "feat: open shared links from the browser in Search"
```

---

### Task 9: README, full verification and on-device checks

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Update the README**

In `README.md`, under `## Features`, add this line after the first bullet (the OpenAlex search bullet):
```markdown
- Add a specific paper by pasting a DOI, arXiv ID or link into Search, with the "Add paper" button, or by sharing a page from the browser; the paper's preview opens before you save it.
```
In the `## Roadmap` list, change `2. Add by DOI / arXiv ID and Android Share` to `2. ✅ Add by DOI / arXiv ID and Android Share`.

- [ ] **Step 2: Run the full verification**

Run: `./gradlew spotlessCheck assembleDebug testDebugUnitTest :core:model:test lintDebug`
Expected: `BUILD SUCCESSFUL`; Lint reports no `MissingTranslation` errors.

Then let CI verify the screenshots against the Linux baselines:
```bash
git add README.md
git commit -m "docs: describe adding papers by ID and share"
git push -u origin feat/add-by-id-and-share
run_id=$(gh run list --branch feat/add-by-id-and-share --workflow ci.yml --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$run_id" --exit-status
```
Expected: the `build` job succeeds. If it fails only on screenshot diffs, download the `screenshot-diffs` artifact and report; re-record only when the change is intended.

- [ ] **Step 3: On-device acceptance checks** (needs a connected phone; `./gradlew :app:installDebug`)

1. Paste `10.1038/nature14539` into Search → the "Deep learning" preview; Save adds it to the Library.
2. Paste `1706.03762`, `arXiv:2401.00001` and `https://arxiv.org/pdf/2005.14165v4` → the right papers.
3. Paste `1810.04805` → "No paper found for this arXiv ID" with **Search for "BERT: …"**, which runs a keyword search.
4. Type `a study of 10.1038/nature14539` → a normal keyword search.
5. From Chrome, share an arXiv abs page, an arXiv PDF, a `doi.org` link and a Wiley or ACM article → Hashiya opens on the right preview.
6. Share a nature.com article → a keyword search for the page title with the explanatory note.
7. In airplane mode, a lookup shows the offline error with Retry; after reconnecting, Retry finds the paper.
8. Library → **Add paper** → Search opens with the keyboard up and the new hint.
9. Repeat a few of these in العربية: the UI mirrors; English titles stay left-to-right.
