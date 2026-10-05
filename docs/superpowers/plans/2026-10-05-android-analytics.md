# Android Usage Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add minimised Firebase Analytics to the Android app behind a closed-list `:core:analytics` module, with a **Share usage statistics** switch (on by default), the spec's events (including the on-device research category), no advertising id, and updated Play answers — mirroring the iOS implementation (PR #37/#38).

**Architecture:** A pure-Kotlin `:core:analytics` module (like `:core:crash`) holds the `Analytics` interface, the closed events and values, the research-category classifier, the once-per-paper note set and the property switch. Firebase appears only in `:app` (`FirebaseAnalyticsTracker`, Hilt-bound in release builds; `NoOpAnalytics` in debug). View models and the PDF repository log events; the search layer reports each first page's total and category through `SearchResults`, and `SearchViewModel` decides whether a search was user-started.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Paging 3, DataStore, kotlinx.serialization, JUnit 4, Robolectric, Roborazzi, Firebase BoM 34.19.0 (`firebase-analytics`).

**Spec:** `docs/superpowers/specs/2026-10-05-analytics-design.md` (and its §7.1 release gate). iOS reference: `ios/HashiyaKit/Sources/HashiyaDiagnostics/`.

**Starting point:** branch `feat/android-analytics` from `feat/ios-diagnostics-main` (PR #38, which carries the spec). Rebase onto `main` once #38 merges.

## Global Constraints

- Firebase only in `:app`; never in core or feature modules. `firebase-analytics` from the existing BoM `34.19.0`.
- Collection only in release builds (`!isDebuggable`); debug builds, unit, Robolectric and screenshot tests never collect.
- Manifest meta-data, all `false`: `firebase_analytics_collection_enabled`, `google_analytics_adid_collection_enabled`, `google_analytics_ssaid_collection_enabled`, `google_analytics_default_allow_ad_personalization_signals`, `google_analytics_automatic_screen_reporting_enabled`. The merged manifest must NOT contain `com.google.android.gms.permission.AD_ID` (remove it with `tools:node="remove"`).
- Consent at every enable: analytics storage granted; ad storage, ad user data, ad personalization denied.
- Switch **Share usage statistics**: on by default, independent of **Send crash reports**, DataStore key `analytics_enabled`. Off → collection stops and `resetAnalyticsData()`; while off nothing is logged and no user property is sent; on again → cached user properties are sent again.
- Closed lists only: event names, parameters and values exactly as spec §4/§4.1; user properties `library_size_bucket`, `language`, `has_own_key` with closed values. Never sent: titles, DOIs, OpenAlex ids, search text, notes, collection names, file names/paths, URLs, the API key, error messages, topic names.
- Android has no quota yet: `route` is `user` (personal key set) or `shared`; `search_limit_reached` is not sent until the quota work adds limits.
- Searches the user didn't start (process-death restore, API-key change re-run) send no `search`/lookup event (iOS ruling R4).
- English/Arabic copy exactly as in Task 4; never "anonymous"; never "isn't linked to you" (say tied to a random identifier / not linked to your name or account).
- Commits: author `Fady <fady.fouad.a@gmail.com>`; no AI attribution anywhere. Spotless must pass.

## Rulings carried from iOS / decided for Android

- **`paper_saved(from: share)`**: Android has no share-extension save; a share opens Search with route args (`pageTitle`/`note`) and the user saves the looked-up paper there. A save of the lookup result while that share context is still active (not yet cleared by editing) sends `from: share`; other lookup saves send `lookup`; result-row saves send `search`.
- **Lookup events:** `search` with `kind` `doi`/`arxiv` (or `link` when the submitted text looked like a link), `has_filters: no`, `route` user/shared, `results_bucket` `1-25` (found) or `0` (not found), no `category`; a failed lookup sends nothing; a link without an id sends nothing.
- **`paper_removed`** when a removal is final: the Library's Undo dismissed or replaced by another removal; a removal from Search (`onToggleSave` remove branch, `onRemoveRequested`) only when the repository actually removed a paper. Leaving the Library while Undo shows sends nothing (no final hook; the startup sweep cleans up) — accepted.
- **Screens:** `screen_view` is sent from the existing NavController listener for library/search/details/reader/settings/restore; Android has no Export screen, so `export` is never a screen value on Android.
- **`search_more`**: one event per further page fetched (`page` 2…40, clamped).
- **`restore`** ok/failed only for an applied restore; opening a file that isn't a backup sends nothing; cancel sends nothing.

## Review Focus

1. A search for a title (or a note containing a DOI) must produce events containing none of its words — Task 5/6 tests with `FakeAnalytics`.
2. Process-death restore and an API-key change must not send `search`, while a chip change does — Task 5 tests.
3. The merged release manifest must not request `AD_ID` and must carry all five `false` flags — Task 3 Robolectric test.
4. A malformed `primary_topic` (numeric id, wrong type) must not fail the search page — Task 2 parsing test.
5. Turning statistics off then on must not lose `language`/`library_size_bucket`/`has_own_key`, and nothing is sent while off — Task 1 `AnalyticsSwitch` tests.

## How to run tests

From `android/`: `./gradlew :core:analytics:test`, `./gradlew :core:data:testDebugUnitTest`, `./gradlew :feature:search:testDebugUnitTest`, `./gradlew :app:testDebugUnitTest` etc. (`--tests '*Name*'` for one class). Before each commit: `./gradlew spotlessApply`. Screenshot baselines are recorded on Linux CI (Task 8), not locally.

---

### Task 1: The `:core:analytics` module

**Files:**
- Modify: `android/settings.gradle.kts` (`include(":core:analytics")` after `:core:crash`)
- Create: `android/core/analytics/build.gradle.kts`
- Create: `android/core/analytics/src/main/kotlin/com/etatech/hashiya/core/analytics/Analytics.kt`
- Create: `android/core/analytics/src/main/kotlin/com/etatech/hashiya/core/analytics/AnalyticsEvent.kt`
- Create: `android/core/analytics/src/main/kotlin/com/etatech/hashiya/core/analytics/ClosedValues.kt`
- Create: `android/core/analytics/src/main/kotlin/com/etatech/hashiya/core/analytics/ResearchCategory.kt`
- Create: `android/core/analytics/src/main/kotlin/com/etatech/hashiya/core/analytics/NotedPapers.kt`
- Create: `android/core/analytics/src/main/kotlin/com/etatech/hashiya/core/analytics/AnalyticsSwitch.kt`
- Create: `android/core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeAnalytics.kt`; Modify `android/core/testing/build.gradle.kts` (`api(project(":core:analytics"))`)
- Test: `android/core/analytics/src/test/kotlin/com/etatech/hashiya/core/analytics/{AnalyticsEventTest,ResearchCategoryTest,AnalyticsSwitchTest,NotedPapersTest}.kt`

**Interfaces (produced):**
- `interface Analytics { fun log(event: AnalyticsEvent); fun setProperty(property: AnalyticsProperty, value: ClosedValue); fun setEnabled(enabled: Boolean) }`, `object NoOpAnalytics : Analytics`
- `interface ClosedValue { val id: String }`; enums implementing it: `LibrarySize` (Zero "0", UpTo50 "1-50", UpTo500 "51-500", UpTo5000 "501-5000", Over5000 "5000+"; `companion fun of(papers: Int)`), `Language` (En "en", Ar "ar", System "system"; `companion fun of(tags: String)` — first tag before `-`, else System), `YesNo` (Yes "yes", No "no"; `of(Boolean)`), `Screen` (Library, Search, Details, Reader, Settings, Restore, Export → lowercase ids)
- `enum class AnalyticsProperty(val id: String) { LibrarySizeBucket("library_size_bucket"), Language("language"), HasOwnKey("has_own_key") }`
- `enum class SearchKind(id)`: Keyword "keyword", Doi "doi", Arxiv "arxiv", Link "link"; `SearchRoute`: User "user", Shared "shared", Keyless "keyless", Cached "cached"; `ResultsBucket`: Zero "0", UpTo25 "1-25", UpTo200 "26-200", Over200 "200+" (`of(count: Long)`); `LimitKind`: Daily "daily", PageCap "page_cap"; `SaveSource`: Search, Lookup, Share; `ExportFormat`: Bibtex "bibtex", Backup "backup"; `PdfOrigin`: Downloaded, Attached
- `sealed interface AnalyticsEvent { val name: String; val parameters: Map<String, String> }` with: `Search(kind, hasFilters: Boolean, route, results: ResultsBucket, category: ResearchCategory?)`, `SearchMore(page: Int)` (clamped 2..40), `SearchLimitReached(kind: LimitKind)`, `PaperSaved(from: SaveSource)`, `PaperRemoved`, `NoteEdited`, `CollectionCreated`, `PaperAddedToCollection`, `Export(format, withPdfs: Boolean)`, `Restore(succeeded: Boolean)`, `PdfOpened(source: PdfOrigin)`, `PdfDownloaded(succeeded: Boolean)`, `ScreenView(screen: Screen)`
- `enum class ResearchCategory(id)` (18 values of spec §4.1) and `data class TopicIds(val subfield: String?, val field: String?, val domain: String?)`; `ResearchCategory.of(ids: TopicIds)`, `ResearchCategory.classify(topics: List<TopicIds>)`
- `object NotedPapers { fun firstEdit(openAlexId: String): Boolean }` (in-memory, synchronized)
- `class AnalyticsSwitch(private val apply: (Boolean) -> Unit, private val send: (String, String) -> Unit) { val isOn: Boolean; fun setEnabled(enabled: Boolean); fun setProperty(property: AnalyticsProperty, value: ClosedValue) }`
- `class FakeAnalytics : Analytics` with `events: List<AnalyticsEvent>`, `properties: Map<AnalyticsProperty, String>`, `enabledCalls: List<Boolean>`

- [ ] **Step 1: Module skeleton**

`android/core/analytics/build.gradle.kts`:

```kotlin
plugins {
    id("hashiya.jvm.library")
}

dependencies {
    testImplementation(libs.junit)
}
```

Add `include(":core:analytics")` to `settings.gradle.kts` after `include(":core:crash")`, and `api(project(":core:analytics"))` to `core/testing/build.gradle.kts` next to `api(project(":core:crash"))`.

- [ ] **Step 2: Write the failing tests**

`AnalyticsEventTest.kt`:

```kotlin
package com.etatech.hashiya.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsEventTest {
    @Test
    fun everyEventHasItsSpecNameAndClosedParameters() {
        val cases = listOf(
            AnalyticsEvent.Search(SearchKind.Keyword, true, SearchRoute.Shared, ResultsBucket.Over200, ResearchCategory.Ai) to
                ("search" to mapOf("kind" to "keyword", "has_filters" to "yes", "route" to "shared", "results_bucket" to "200+", "category" to "ai")),
            AnalyticsEvent.Search(SearchKind.Doi, false, SearchRoute.User, ResultsBucket.UpTo25, null) to
                ("search" to mapOf("kind" to "doi", "has_filters" to "no", "route" to "user", "results_bucket" to "1-25")),
            AnalyticsEvent.SearchMore(2) to ("search_more" to mapOf("page" to "2")),
            AnalyticsEvent.SearchMore(99) to ("search_more" to mapOf("page" to "40")),
            AnalyticsEvent.SearchLimitReached(LimitKind.PageCap) to ("search_limit_reached" to mapOf("kind" to "page_cap")),
            AnalyticsEvent.PaperSaved(SaveSource.Share) to ("paper_saved" to mapOf("from" to "share")),
            AnalyticsEvent.PaperRemoved to ("paper_removed" to emptyMap()),
            AnalyticsEvent.NoteEdited to ("note_edited" to emptyMap()),
            AnalyticsEvent.CollectionCreated to ("collection_created" to emptyMap()),
            AnalyticsEvent.PaperAddedToCollection to ("paper_added_to_collection" to emptyMap()),
            AnalyticsEvent.Export(ExportFormat.Backup, true) to ("export" to mapOf("format" to "backup", "with_pdfs" to "yes")),
            AnalyticsEvent.Restore(false) to ("restore" to mapOf("result" to "failed")),
            AnalyticsEvent.PdfOpened(PdfOrigin.Attached) to ("pdf_opened" to mapOf("source" to "attached")),
            AnalyticsEvent.PdfDownloaded(true) to ("pdf_downloaded" to mapOf("result" to "ok")),
            AnalyticsEvent.ScreenView(Screen.Reader) to ("screen_view" to mapOf("screen" to "reader")),
        )
        for ((event, expected) in cases) {
            assertEquals(expected.first, event.name)
            assertEquals(expected.second, event.parameters)
        }
    }

    @Test
    fun bucketsAndKeys() {
        assertEquals(listOf("0", "1-25", "1-25", "26-200", "26-200", "200+"), listOf(0L, 1L, 25L, 26L, 200L, 201L).map { ResultsBucket.of(it).id })
        assertEquals(listOf("0", "1-50", "51-500", "501-5000", "5000+"), listOf(0, 50, 51, 5000, 5001).map { LibrarySize.of(it).id })
        assertEquals(listOf("en", "ar", "en", "system", "system"), listOf("en", "ar", "en-GB,ar", "fr", "").map { Language.of(it).id })
    }
}
```

`ResearchCategoryTest.kt` (mirrors the iOS tests):

```kotlin
package com.etatech.hashiya.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class ResearchCategoryTest {
    private fun topic(subfield: Int? = null, field: Int? = null, domain: Int? = null) = TopicIds(
        subfield?.let { "https://openalex.org/subfields/$it" },
        field?.let { "https://openalex.org/fields/$it" },
        domain?.let { "https://openalex.org/domains/$it" },
    )

    @Test
    fun everyComputerScienceSubfieldMaps() {
        val expected = mapOf(
            1702 to ResearchCategory.Ai, 1707 to ResearchCategory.ComputerVision, 1703 to ResearchCategory.Theory,
            1705 to ResearchCategory.Networks, 1708 to ResearchCategory.Systems, 1712 to ResearchCategory.Software,
            1709 to ResearchCategory.Hci, 1710 to ResearchCategory.InformationSystems, 1704 to ResearchCategory.Graphics,
            1711 to ResearchCategory.SignalProcessing, 1706 to ResearchCategory.CsOther,
        )
        for ((subfield, category) in expected) assertEquals(category, ResearchCategory.of(topic(subfield, 17, 3)))
    }

    @Test
    fun fieldsAndDomainsMap() {
        assertEquals(ResearchCategory.Mathematics, ResearchCategory.of(topic(2601, 26, 3)))
        assertEquals(ResearchCategory.Engineering, ResearchCategory.of(topic(2201, 22, 3)))
        assertEquals(ResearchCategory.PhysicalSciences, ResearchCategory.of(topic(3101, 31, 3)))
        assertEquals(ResearchCategory.LifeSciences, ResearchCategory.of(topic(1301, 13, 1)))
        assertEquals(ResearchCategory.SocialSciences, ResearchCategory.of(topic(3301, 33, 2)))
        assertEquals(ResearchCategory.HealthSciences, ResearchCategory.of(topic(2701, 27, 4)))
    }

    @Test
    fun malformedOrUnknownIdsAreUnknownOrFallThrough() {
        assertEquals(ResearchCategory.Unknown, ResearchCategory.of(TopicIds("https://openalex.org/subfields/abc", null, null)))
        assertEquals(ResearchCategory.Unknown, ResearchCategory.of(TopicIds("1702", null, null)))
        assertEquals(ResearchCategory.Unknown, ResearchCategory.of(TopicIds(null, null, "https://openalex.org/domains/9")))
        assertEquals(ResearchCategory.Mathematics, ResearchCategory.of(TopicIds("x", "https://openalex.org/fields/26", null)))
    }

    @Test
    fun theVote() {
        val ai = topic(1702, 17, 3)
        val vision = topic(1707, 17, 3)
        val theory = topic(1703, 17, 3)
        val none = TopicIds(null, null, null)
        assertEquals(ResearchCategory.Ai, ResearchCategory.classify(listOf(ai, ai, vision, theory, ai)))
        assertEquals(ResearchCategory.Unknown, ResearchCategory.classify(listOf(ai, ai, vision, vision, theory)))
        assertEquals(ResearchCategory.Ai, ResearchCategory.classify(listOf(ai, ai, vision, theory, topic(1705, 17, 3))))
        assertEquals(ResearchCategory.Unknown, ResearchCategory.classify(listOf(ai, vision, theory)))
        assertEquals(ResearchCategory.Unknown, ResearchCategory.classify(listOf(ai, ai)))
        assertEquals(ResearchCategory.Unknown, ResearchCategory.classify(emptyList()))
        assertEquals(ResearchCategory.Ai, ResearchCategory.classify(listOf(none, none, ai, ai, ai)))
        assertEquals(ResearchCategory.ComputerVision, ResearchCategory.classify(List(10) { vision } + List(15) { ai }))
    }
}
```

`AnalyticsSwitchTest.kt`:

```kotlin
package com.etatech.hashiya.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AnalyticsSwitchTest {
    private val applied = mutableListOf<Boolean>()
    private val sent = mutableListOf<Pair<String, String>>()
    private val switch = AnalyticsSwitch(apply = { applied += it }, send = { name, value -> sent += name to value })

    @Test
    fun startsOffAndSendsNothing() {
        assertFalse(switch.isOn)
        switch.setProperty(AnalyticsProperty.Language, Language.Ar)
        assertEquals(emptyList<Pair<String, String>>(), sent)
    }

    @Test
    fun turningOnSendsTheLatestValuesAndLaterOnesDirectly() {
        switch.setProperty(AnalyticsProperty.Language, Language.En)
        switch.setProperty(AnalyticsProperty.Language, Language.Ar)
        switch.setEnabled(true)
        switch.setProperty(AnalyticsProperty.HasOwnKey, YesNo.Yes)
        assertEquals(listOf(true), applied)
        assertEquals(listOf("language" to "ar", "has_own_key" to "yes"), sent)
    }

    @Test
    fun offThenOnSendsTheCachedValuesAgain() {
        switch.setEnabled(true)
        switch.setProperty(AnalyticsProperty.LibrarySizeBucket, LibrarySize.UpTo500)
        switch.setEnabled(false)
        switch.setProperty(AnalyticsProperty.Language, Language.En)
        sent.clear()
        switch.setEnabled(true)
        assertEquals(setOf("library_size_bucket" to "51-500", "language" to "en"), sent.toSet())
    }
}
```

`NotedPapersTest.kt`:

```kotlin
package com.etatech.hashiya.core.analytics

import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotedPapersTest {
    @Test
    fun onlyTheFirstEditOfAPaperCounts() {
        val id = UUID.randomUUID().toString()
        assertTrue(NotedPapers.firstEdit(id))
        assertFalse(NotedPapers.firstEdit(id))
        assertTrue(NotedPapers.firstEdit(UUID.randomUUID().toString()))
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :core:analytics:test`. Expected: compilation failure (unresolved `AnalyticsEvent` etc.).

- [ ] **Step 4: Implement**

`ClosedValues.kt`:

```kotlin
package com.etatech.hashiya.core.analytics

/** A value from a closed list: the only kind of value a crash key or analytics property may take. */
interface ClosedValue {
    val id: String
}

enum class LibrarySize(override val id: String) : ClosedValue {
    Zero("0"), UpTo50("1-50"), UpTo500("51-500"), UpTo5000("501-5000"), Over5000("5000+");

    companion object {
        /** The library's size, coarse enough to say nothing about a person. */
        fun of(papers: Int): LibrarySize = when {
            papers <= 0 -> Zero
            papers <= 50 -> UpTo50
            papers <= 500 -> UpTo500
            papers <= 5000 -> UpTo5000
            else -> Over5000
        }
    }
}

enum class Language(override val id: String) : ClosedValue {
    En("en"), Ar("ar"), System("system");

    companion object {
        /** From `AppCompatDelegate` language tags ("ar", "en-GB,ar", ""): the first tag's language. */
        fun of(tags: String): Language = when (tags.substringBefore(',').substringBefore('-')) {
            "en" -> En
            "ar" -> Ar
            else -> System
        }
    }
}

enum class YesNo(override val id: String) : ClosedValue {
    Yes("yes"), No("no");

    companion object {
        fun of(value: Boolean) = if (value) Yes else No
    }
}

enum class Screen(override val id: String) : ClosedValue {
    Library("library"), Search("search"), Details("details"), Reader("reader"), Settings("settings"), Restore("restore"), Export("export")
}

enum class AnalyticsProperty(val id: String) {
    LibrarySizeBucket("library_size_bucket"), Language("language"), HasOwnKey("has_own_key")
}

enum class SearchKind(val id: String) { Keyword("keyword"), Doi("doi"), Arxiv("arxiv"), Link("link") }

enum class SearchRoute(val id: String) { User("user"), Shared("shared"), Keyless("keyless"), Cached("cached") }

enum class LimitKind(val id: String) { Daily("daily"), PageCap("page_cap") }

enum class SaveSource(val id: String) { Search("search"), Lookup("lookup"), Share("share") }

enum class ExportFormat(val id: String) { Bibtex("bibtex"), Backup("backup") }

enum class PdfOrigin(val id: String) { Downloaded("downloaded"), Attached("attached") }

enum class ResultsBucket(val id: String) {
    Zero("0"), UpTo25("1-25"), UpTo200("26-200"), Over200("200+");

    companion object {
        fun of(count: Long): ResultsBucket = when {
            count <= 0 -> Zero
            count <= 25 -> UpTo25
            count <= 200 -> UpTo200
            else -> Over200
        }
    }
}
```

`Analytics.kt`:

```kotlin
package com.etatech.hashiya.core.analytics

/**
 * Counts how features are used. Only `:app` knows the service behind it. Events, parameters and values are closed lists,
 * so nothing a person typed or read can be attached.
 */
interface Analytics {
    fun log(event: AnalyticsEvent)

    fun setProperty(property: AnalyticsProperty, value: ClosedValue)

    /** Starts or stops collection. Stopping also clears the analytics id and events not yet sent. */
    fun setEnabled(enabled: Boolean)
}

/** Debug builds and tests: counts nothing. */
object NoOpAnalytics : Analytics {
    override fun log(event: AnalyticsEvent) = Unit

    override fun setProperty(property: AnalyticsProperty, value: ClosedValue) = Unit

    override fun setEnabled(enabled: Boolean) = Unit
}
```

`AnalyticsEvent.kt`:

```kotlin
package com.etatech.hashiya.core.analytics

/** The events of the analytics spec §4, with their closed parameters. */
sealed interface AnalyticsEvent {
    val name: String
    val parameters: Map<String, String>

    data class Search(
        val kind: SearchKind,
        val hasFilters: Boolean,
        val route: SearchRoute,
        val results: ResultsBucket,
        val category: ResearchCategory?,
    ) : AnalyticsEvent {
        override val name = "search"
        override val parameters: Map<String, String>
            get() = buildMap {
                put("kind", kind.id)
                put("has_filters", yesNo(hasFilters))
                put("route", route.id)
                put("results_bucket", results.id)
                category?.let { put("category", it.id) }
            }
    }

    /** [page] 2…40; others are sent clamped. */
    data class SearchMore(val page: Int) : AnalyticsEvent {
        override val name = "search_more"
        override val parameters get() = mapOf("page" to page.coerceIn(2, 40).toString())
    }

    data class SearchLimitReached(val kind: LimitKind) : AnalyticsEvent {
        override val name = "search_limit_reached"
        override val parameters get() = mapOf("kind" to kind.id)
    }

    data class PaperSaved(val from: SaveSource) : AnalyticsEvent {
        override val name = "paper_saved"
        override val parameters get() = mapOf("from" to from.id)
    }

    data object PaperRemoved : AnalyticsEvent {
        override val name = "paper_removed"
        override val parameters = emptyMap<String, String>()
    }

    data object NoteEdited : AnalyticsEvent {
        override val name = "note_edited"
        override val parameters = emptyMap<String, String>()
    }

    data object CollectionCreated : AnalyticsEvent {
        override val name = "collection_created"
        override val parameters = emptyMap<String, String>()
    }

    data object PaperAddedToCollection : AnalyticsEvent {
        override val name = "paper_added_to_collection"
        override val parameters = emptyMap<String, String>()
    }

    data class Export(val format: ExportFormat, val withPdfs: Boolean) : AnalyticsEvent {
        override val name = "export"
        override val parameters get() = mapOf("format" to format.id, "with_pdfs" to yesNo(withPdfs))
    }

    data class Restore(val succeeded: Boolean) : AnalyticsEvent {
        override val name = "restore"
        override val parameters get() = mapOf("result" to if (succeeded) "ok" else "failed")
    }

    data class PdfOpened(val source: PdfOrigin) : AnalyticsEvent {
        override val name = "pdf_opened"
        override val parameters get() = mapOf("source" to source.id)
    }

    data class PdfDownloaded(val succeeded: Boolean) : AnalyticsEvent {
        override val name = "pdf_downloaded"
        override val parameters get() = mapOf("result" to if (succeeded) "ok" else "failed")
    }

    data class ScreenView(val screen: Screen) : AnalyticsEvent {
        override val name = "screen_view"
        override val parameters get() = mapOf("screen" to screen.id)
    }
}

private fun yesNo(value: Boolean) = if (value) "yes" else "no"
```

`ResearchCategory.kt`:

```kotlin
package com.etatech.hashiya.core.analytics

/** A work's primary topic as OpenAlex ids ("https://openalex.org/subfields/1702", …/fields/17, …/domains/3). Never names. */
data class TopicIds(val subfield: String?, val field: String?, val domain: String?) {
    internal val isEmpty get() = subfield == null && field == null && domain == null
}

/** The research area of a keyword search, worked out on the device from the results' topics (spec §4.1). */
enum class ResearchCategory(val id: String) {
    Ai("ai"), ComputerVision("computer_vision"), Theory("theory"), Networks("networks"), Systems("systems"),
    Software("software"), Hci("hci"), InformationSystems("information_systems"), Graphics("graphics"),
    SignalProcessing("signal_processing"), CsOther("cs_other"), Mathematics("mathematics"), Engineering("engineering"),
    PhysicalSciences("physical_sciences"), LifeSciences("life_sciences"), SocialSciences("social_sciences"),
    HealthSciences("health_sciences"), Unknown("unknown");

    companion object {
        private val subfields = mapOf(
            1702 to Ai, 1707 to ComputerVision, 1703 to Theory, 1705 to Networks, 1708 to Systems,
            1712 to Software, 1709 to Hci, 1710 to InformationSystems, 1704 to Graphics, 1711 to SignalProcessing,
        )
        private val fields = mapOf(17 to CsOther, 26 to Mathematics, 22 to Engineering)
        private val domains = mapOf(3 to PhysicalSciences, 1 to LifeSciences, 2 to SocialSciences, 4 to HealthSciences)

        /** One work: its subfield, else its field, else its domain; anything unrecognised is [Unknown]. */
        fun of(ids: TopicIds): ResearchCategory =
            number(ids.subfield, "subfields")?.let(subfields::get)
                ?: number(ids.field, "fields")?.let(fields::get)
                ?: number(ids.domain, "domains")?.let(domains::get)
                ?: Unknown

        /**
         * A search: of the first 10 results that have a topic, the most common category if at least 3 were mapped and it has
         * at least 40% of them with no tie; otherwise [Unknown].
         */
        fun classify(topics: List<TopicIds>): ResearchCategory {
            val mapped = topics.asSequence().filterNot { it.isEmpty }.take(10).map(::of).toList()
            if (mapped.size < 3) return Unknown
            val ranked = mapped.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            val top = ranked.first()
            if (ranked.getOrNull(1)?.value == top.value || top.value * 5 < mapped.size * 2) return Unknown
            return top.key
        }

        private fun number(id: String?, kind: String): Int? {
            val prefix = "https://openalex.org/$kind/"
            return id?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.toIntOrNull()
        }
    }
}
```

`NotedPapers.kt`:

```kotlin
package com.etatech.hashiya.core.analytics

/** Papers whose notes were edited in this process, so `note_edited` counts each paper once per session. Never sent. */
object NotedPapers {
    private val ids = mutableSetOf<String>()

    /** True the first time [openAlexId] is seen in this process. */
    @Synchronized
    fun firstEdit(openAlexId: String): Boolean = ids.add(openAlexId)
}
```

`AnalyticsSwitch.kt`:

```kotlin
package com.etatech.hashiya.core.analytics

/**
 * The on/off state behind the Firebase tracker: user properties are remembered, sent only while on, and sent again when
 * collection is turned back on (resetting analytics data clears them on the service side).
 */
class AnalyticsSwitch(private val apply: (Boolean) -> Unit, private val send: (String, String) -> Unit) {
    private val properties = linkedMapOf<AnalyticsProperty, String>()
    private var on = false

    val isOn: Boolean
        @Synchronized get() = on

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        on = enabled
        apply(enabled)
        if (enabled) properties.forEach { (property, value) -> send(property.id, value) }
    }

    @Synchronized
    fun setProperty(property: AnalyticsProperty, value: ClosedValue) {
        properties[property] = value.id
        if (on) send(property.id, value.id)
    }
}
```

`FakeAnalytics.kt` (in `:core:testing`):

```kotlin
package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.analytics.Analytics
import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.analytics.AnalyticsProperty
import com.etatech.hashiya.core.analytics.ClosedValue

/** Records every call, for tests. */
class FakeAnalytics : Analytics {
    private val lock = Any()
    private val _events = mutableListOf<AnalyticsEvent>()
    private val _properties = mutableMapOf<AnalyticsProperty, String>()
    private val _enabledCalls = mutableListOf<Boolean>()

    val events: List<AnalyticsEvent> get() = synchronized(lock) { _events.toList() }
    val properties: Map<AnalyticsProperty, String> get() = synchronized(lock) { _properties.toMap() }
    val enabledCalls: List<Boolean> get() = synchronized(lock) { _enabledCalls.toList() }

    override fun log(event: AnalyticsEvent) = synchronized(lock) { _events += event }

    override fun setProperty(property: AnalyticsProperty, value: ClosedValue) = synchronized(lock) { _properties[property] = value.id }

    override fun setEnabled(enabled: Boolean) = synchronized(lock) { _enabledCalls += enabled }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :core:analytics:test :core:testing:compileDebugKotlin spotlessApply`. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/settings.gradle.kts android/core/analytics android/core/testing
git commit -m "feat(android): add the closed-list analytics module"
```

---

### Task 2: Research category and first-page summary from the search layer

**Files:**
- Modify: `android/core/network/src/main/java/com/etatech/hashiya/core/network/model/NetworkWorks.kt` (`NetworkWork.primaryTopic`, `NetworkTopic`, `NetworkTopicRef`)
- Modify: `android/core/network/src/main/java/com/etatech/hashiya/core/network/OpenAlexApi.kt` (`SEARCH_FIELDS` used by `searchWorks` only)
- Modify: `android/core/data/build.gradle.kts` (`implementation(project(":core:analytics"))`)
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/repository/SearchRepository.kt` (`SearchResults.firstPage`, `SearchResults.pagesLoaded`; `data class FirstPage(val total: Long, val category: ResearchCategory)`)
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/paging/OpenAlexPagingSource.kt`, `repository/OpenAlexSearchRepository.kt`
- Modify: `android/core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeSearchRepository.kt` (settable `firstPage`, `pagesLoaded`)
- Test: `core/network/src/test/.../model/OpenAlexParsingTest.kt`, `OpenAlexDataSourceTest.kt`; `core/data/src/test/.../paging/OpenAlexPagingSourceTest.kt`, `repository/OpenAlexSearchRepositoryTest.kt`

**Interfaces:**
- Consumes: `TopicIds`, `ResearchCategory` (Task 1).
- Produces: `SearchResults(papers, totalCount, firstPage: StateFlow<FirstPage?>, pagesLoaded: StateFlow<Int>)` — `firstPage` set when the first page of this search arrives (total + category); `pagesLoaded` counts pages fetched (1 after the first page, 2 after the next…). Existing constructions get defaults (`MutableStateFlow(null)`, `MutableStateFlow(0)`).

- [ ] **Step 1: Write the failing tests**

In `OpenAlexParsingTest.kt`:

```kotlin
    @Test
    fun parsesThePrimaryTopicIds() {
        val work = OpenAlexJson.decodeFromString<NetworkWork>(
            """{"id":"https://openalex.org/W1","primary_topic":{"id":"https://openalex.org/T1","display_name":"Name","subfield":{"id":"https://openalex.org/subfields/1707","display_name":"CV"},"field":{"id":"https://openalex.org/fields/17"},"domain":{"id":"https://openalex.org/domains/3"}}}"""
        )
        assertEquals("https://openalex.org/subfields/1707", work.primaryTopic?.subfield?.id)
        assertEquals("https://openalex.org/fields/17", work.primaryTopic?.field?.id)
        assertEquals("https://openalex.org/domains/3", work.primaryTopic?.domain?.id)
    }

    @Test
    fun aMissingOrOddPrimaryTopicDoesNotFailTheWork() {
        for (topic in listOf("null", "\"x\"", """{"subfield":{"id":1707}}""")) {
            val work = OpenAlexJson.decodeFromString<NetworkWork>("""{"id":"https://openalex.org/W1","primary_topic":$topic}""")
            assertEquals("https://openalex.org/W1", work.id)
        }
    }
```

(If kotlinx.serialization can't skip a wrongly typed `primary_topic` with the existing `OpenAlexJson` settings, give `primaryTopic` a custom lenient serializer that decodes the element as `JsonElement` and returns `null` on any shape it doesn't understand — the requirement is that the work still parses.)

In `OpenAlexDataSourceTest.sendsSearchParameters`, change the `select` assertion to `SEARCH_FIELDS` and add `assertTrue(SEARCH_FIELDS.split(",").contains("primary_topic"))`; lookups (`getWork`, `findWorks`) still assert `WORK_FIELDS`.

In `OpenAlexPagingSourceTest.kt` (use the file's existing `FakeOpenAlexDataSource`; extend its `enqueuePage` to accept an optional `topics: List<NetworkTopic?>` defaulting to none):

```kotlin
    @Test
    fun theFirstPageReportsItsTotalAndCategoryAndPagesAreCounted() = runTest {
        val ai = NetworkTopic(subfield = NetworkTopicRef("https://openalex.org/subfields/1702"), field = NetworkTopicRef("https://openalex.org/fields/17"), domain = NetworkTopicRef("https://openalex.org/domains/3"))
        dataSource.enqueuePage("W1", "W2", "W3", nextCursor = "c2", count = 48_210, topics = listOf(ai, ai, ai))
        dataSource.enqueuePage("W4", nextCursor = null, count = 48_210)
        val firstPage = MutableStateFlow<FirstPage?>(null)
        val pages = MutableStateFlow(0)
        val source = OpenAlexPagingSource(SearchQuery("bert"), dataSource, onFirstPage = { firstPage.value = it }, onPage = { pages.value = it })
        source.load(PagingSource.LoadParams.Refresh(null, 25, false))
        assertEquals(FirstPage(48_210, ResearchCategory.Ai), firstPage.value)
        assertEquals(1, pages.value)
        source.load(PagingSource.LoadParams.Append("c2", 25, false))
        assertEquals(2, pages.value)
    }
```

(Adapt the `LoadParams` construction and `runTest` setup to the file's existing style.)

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:network:testDebugUnitTest :core:data:testDebugUnitTest`. Expected: compilation failures (`primaryTopic`, `SEARCH_FIELDS`, `FirstPage`, `onFirstPage`).

- [ ] **Step 3: Implement**

`NetworkWorks.kt` — add to `NetworkWork` (last parameter, defaulted): `@SerialName("primary_topic") val primaryTopic: NetworkTopic? = null`, and:

```kotlin
/** A work's primary topic: only the ids of its subfield, field and domain are kept; names are ignored. */
@Serializable
data class NetworkTopic(val subfield: NetworkTopicRef? = null, val field: NetworkTopicRef? = null, val domain: NetworkTopicRef? = null)

@Serializable
data class NetworkTopicRef(val id: String? = null)
```

`OpenAlexApi.kt`:

```kotlin
/** What a keyword search asks for: the work fields plus the primary topic, used only to work out the research area. */
internal const val SEARCH_FIELDS = "$WORK_FIELDS,primary_topic"
```

and `searchWorks`'s `select` default becomes `SEARCH_FIELDS`.

`SearchRepository.kt`:

```kotlin
/** What the first page of a search says about it: OpenAlex's total and the research area of its results. */
data class FirstPage(val total: Long, val category: ResearchCategory)

data class SearchResults(
    val papers: Flow<PagingData<Paper>>,
    val totalCount: StateFlow<Long?>,
    /** Set when the first page arrives. */
    val firstPage: StateFlow<FirstPage?> = MutableStateFlow(null),
    /** Pages fetched so far for this search: 1 after the first page. */
    val pagesLoaded: StateFlow<Int> = MutableStateFlow(0),
)
```

`OpenAlexPagingSource` — replace `onTotalCount: (Long) -> Unit` with `onFirstPage: (FirstPage) -> Unit` and add `onPage: (Int) -> Unit`; keep a per-instance `private var pages = 0`; in `load` after a successful response:

```kotlin
        pages += 1
        onPage(pages)
        if (params.key == null) {
            val topics = response.results.map { work ->
                TopicIds(work.primaryTopic?.subfield?.id, work.primaryTopic?.field?.id, work.primaryTopic?.domain?.id)
            }
            onFirstPage(FirstPage(response.meta.count, ResearchCategory.classify(topics)))
        }
```

`OpenAlexSearchRepository.search` — keep `totalCount` (set from the first page's `total`), add `firstPage` and `pagesLoaded` `MutableStateFlow`s fed by the callbacks, and return them in `SearchResults`.

`FakeSearchRepository` — add constructor/var `firstPage: FirstPage? = null` and `pagesLoaded: Int = 0`, returned as `MutableStateFlow`s in each `SearchResults` it builds, plus a way for tests to emit later values (e.g. keep the last results' flows in `lastFirstPage: MutableStateFlow<FirstPage?>` / `lastPagesLoaded: MutableStateFlow<Int>`).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :core:network:testDebugUnitTest :core:data:testDebugUnitTest :feature:search:testDebugUnitTest spotlessApply`. Expected: PASS (existing search tests still green).

- [ ] **Step 5: Commit**

```bash
git add android/core
git commit -m "feat(android): work out a search's research area from its results' topics"
```

---

### Task 3: Firebase Analytics in `:app`, collection only in release builds

**Files:**
- Modify: `android/gradle/libs.versions.toml` (`firebase-analytics = { group = "com.google.firebase", name = "firebase-analytics" }`), `android/app/build.gradle.kts` (`implementation(libs.firebase.analytics)` after crashlytics; `implementation(project(":core:analytics"))`)
- Modify: `android/app/src/main/AndroidManifest.xml` (five meta-data flags, AD_ID removal)
- Create: `android/app/src/main/java/com/etatech/hashiya/analytics/FirebaseAnalyticsTracker.kt`, `AnalyticsModule.kt`, `AnalyticsStartup.kt`
- Modify: `android/app/src/main/java/com/etatech/hashiya/HashiyaApplication.kt`, `MainActivity.kt`, `crash/ScreenKey.kt` (+ its call site in `navigation/HashiyaApp.kt`)
- Test: `android/app/src/test/java/com/etatech/hashiya/analytics/{FirebaseAnalyticsTrackerTest,AnalyticsModuleTest,AnalyticsStartupTest,AnalyticsManifestTest}.kt`, `crash/ScreenKeyTest.kt`

**Interfaces:**
- Consumes: Task 1 (`Analytics`, `AnalyticsSwitch`, closed values, `FakeAnalytics`).
- Produces: Hilt `@Singleton Analytics` (release: `FirebaseAnalyticsTracker`, debug: `NoOpAnalytics`); `AnalyticsStartup(analytics, preferences, libraryBackup, languageTag: () -> String, isRelease: Boolean).run()`; `ReportScreens(navController, crashReporter, analytics)`.

- [ ] **Step 1: Write the failing tests**

`FirebaseAnalyticsTrackerTest.kt` (no Firebase: seams):

```kotlin
package com.etatech.hashiya.analytics

import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.analytics.AnalyticsProperty
import com.etatech.hashiya.core.analytics.Language
import com.etatech.hashiya.core.analytics.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseAnalyticsTrackerTest {
    private val calls = mutableListOf<String>()
    private val tracker = FirebaseAnalyticsTracker(
        logEvent = { name, params -> calls += "log:$name:$params" },
        setUserProperty = { name, value -> calls += "property:$name=$value" },
        setCollectionEnabled = { calls += "collection:$it" },
        resetAnalyticsData = { calls += "reset" },
        denyAdsConsent = { calls += "consent" },
    )

    @Test
    fun nothingIsLoggedOrSentWhileOff() {
        tracker.log(AnalyticsEvent.ScreenView(Screen.Library))
        tracker.setProperty(AnalyticsProperty.Language, Language.Ar)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun turningOnSetsConsentEnablesAndSendsCachedProperties() {
        tracker.setProperty(AnalyticsProperty.Language, Language.Ar)
        tracker.setEnabled(true)
        tracker.log(AnalyticsEvent.ScreenView(Screen.Library))
        assertEquals(listOf("consent", "collection:true", "property:language=ar", "log:screen_view:{screen=library}"), calls)
    }

    @Test
    fun turningOffDisablesAndResets() {
        tracker.setEnabled(true)
        calls.clear()
        tracker.setEnabled(false)
        assertEquals(listOf("consent", "collection:false", "reset"), calls)
    }
}
```

`AnalyticsModuleTest.kt` (Robolectric, like `CrashModuleTest`): a debuggable test app gets `NoOpAnalytics`.

`AnalyticsStartupTest.kt` (like `CrashStartupTest`, with `FakeAnalytics`, `FakeUserPreferencesRepository`, `FakeLibraryBackup`):

```kotlin
    @Test
    fun aReleaseBuildFollowsTheSwitchAndSetsTheProperties() = runTest {
        preferences.setAnalyticsEnabled(true)
        preferences.setUserApiKey("mine")
        backup.summary = BackupSummary(papers = 182, collections = 0, pdfCount = 0, pdfBytes = 0)
        startup(isRelease = true, languageTag = "ar").run()
        assertEquals(listOf(true), analytics.enabledCalls)
        assertEquals(mapOf(AnalyticsProperty.Language to "ar", AnalyticsProperty.LibrarySizeBucket to "51-500", AnalyticsProperty.HasOwnKey to "yes"), analytics.properties)
    }

    @Test
    fun aDebugBuildNeverEnables() = runTest {
        startup(isRelease = false, languageTag = "en").run()
        assertEquals(listOf(false), analytics.enabledCalls)
    }

    @Test
    fun switchedOffStaysOff() = runTest {
        preferences.setAnalyticsEnabled(false)
        startup(isRelease = true, languageTag = "en").run()
        assertEquals(listOf(false), analytics.enabledCalls)
    }
```

(`setAnalyticsEnabled` on the fake comes from Task 4; if Task 4 isn't done yet, add `analyticsEnabled`/`setAnalyticsEnabled` to `UserPreferencesRepository`, its DataStore implementation and the fake here, defaulting to true — Task 4 then only adds the UI.)

`AnalyticsManifestTest.kt` (Robolectric, like `CrashManifestTest`):

```kotlin
    @Test
    fun analyticsIsOffAndAdIdsAreNotCollectedUntilTheAppSaysSo() {
        val metaData = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        for (key in listOf(
            "firebase_analytics_collection_enabled", "google_analytics_adid_collection_enabled",
            "google_analytics_ssaid_collection_enabled", "google_analytics_default_allow_ad_personalization_signals",
            "google_analytics_automatic_screen_reporting_enabled",
        )) {
            assertFalse(key, metaData.getBoolean(key, true))
        }
    }

    @Test
    fun theAppDoesNotRequestTheAdvertisingIdPermission() {
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(requested.contains("com.google.android.gms.permission.AD_ID"))
    }
```

(If Robolectric's merged manifest in unit tests doesn't include library-merged permissions, also verify with `./gradlew :app:processReleaseMainManifest` and `grep -c AD_ID android/app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml` → `0`; record both in the report.)

`ScreenKeyTest.kt` — add: a destination change logs `ScreenView` with the same screen as the crash key, via `FakeAnalytics`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*Analytics*' --tests '*ScreenKey*'`. Expected: compilation failures.

- [ ] **Step 3: Implement**

`AndroidManifest.xml` — in `<manifest>` (with `xmlns:tools`): `<uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove" />`; in `<application>` next to the Crashlytics flags:

```xml
        <!-- Usage statistics stay off until the app decides (release builds and the Settings switch); no advertising or
             Android ids, no ad personalization, and screens are reported by the app itself. -->
        <meta-data android:name="firebase_analytics_collection_enabled" android:value="false" />
        <meta-data android:name="google_analytics_adid_collection_enabled" android:value="false" />
        <meta-data android:name="google_analytics_ssaid_collection_enabled" android:value="false" />
        <meta-data android:name="google_analytics_default_allow_ad_personalization_signals" android:value="false" />
        <meta-data android:name="google_analytics_automatic_screen_reporting_enabled" android:value="false" />
```

`FirebaseAnalyticsTracker.kt`:

```kotlin
package com.etatech.hashiya.analytics

import android.os.Bundle
import com.etatech.hashiya.core.analytics.Analytics
import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.analytics.AnalyticsProperty
import com.etatech.hashiya.core.analytics.AnalyticsSwitch
import com.etatech.hashiya.core.analytics.ClosedValue
import com.google.firebase.analytics.FirebaseAnalytics

/** Release builds' usage statistics: only the closed events and properties of `:core:analytics`. Nothing while off. */
class FirebaseAnalyticsTracker internal constructor(
    private val logEvent: (String, Map<String, String>) -> Unit,
    setUserProperty: (String, String) -> Unit,
    private val setCollectionEnabled: (Boolean) -> Unit,
    private val resetAnalyticsData: () -> Unit,
    private val denyAdsConsent: () -> Unit,
) : Analytics {
    constructor(analytics: FirebaseAnalytics) : this(
        logEvent = { name, parameters ->
            analytics.logEvent(name, Bundle().apply { parameters.forEach { (key, value) -> putString(key, value) } })
        },
        setUserProperty = analytics::setUserProperty,
        setCollectionEnabled = analytics::setAnalyticsCollectionEnabled,
        resetAnalyticsData = analytics::resetAnalyticsData,
        denyAdsConsent = {
            analytics.setConsent(
                mapOf(
                    FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to FirebaseAnalytics.ConsentStatus.GRANTED,
                    FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                    FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                    FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
                )
            )
        },
    )

    private val switch = AnalyticsSwitch(
        apply = { enabled ->
            denyAdsConsent()
            setCollectionEnabled(enabled)
            if (!enabled) resetAnalyticsData()
        },
        send = setUserProperty,
    )

    override fun log(event: AnalyticsEvent) {
        if (switch.isOn) logEvent(event.name, event.parameters)
    }

    override fun setProperty(property: AnalyticsProperty, value: ClosedValue) = switch.setProperty(property, value)

    override fun setEnabled(enabled: Boolean) = switch.setEnabled(enabled)
}
```

(Firebase's `setConsent` takes `Map<ConsentType, ConsentStatus>`; adapt the generic types if the API's Kotlin signature differs.)

`AnalyticsModule.kt`:

```kotlin
package com.etatech.hashiya.analytics

import android.content.Context
import com.etatech.hashiya.core.analytics.Analytics
import com.etatech.hashiya.core.analytics.NoOpAnalytics
import com.etatech.hashiya.crash.isDebuggable
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {
    @Provides
    @Singleton
    fun provideAnalytics(@ApplicationContext context: Context): Analytics =
        if (isDebuggable(context)) NoOpAnalytics else FirebaseAnalyticsTracker(FirebaseAnalytics.getInstance(context))
}
```

`AnalyticsStartup.kt`:

```kotlin
package com.etatech.hashiya.analytics

import com.etatech.hashiya.core.analytics.Analytics
import com.etatech.hashiya.core.analytics.AnalyticsProperty
import com.etatech.hashiya.core.analytics.Language
import com.etatech.hashiya.core.analytics.LibrarySize
import com.etatech.hashiya.core.analytics.YesNo
import com.etatech.hashiya.core.data.backup.LibraryBackup
import com.etatech.hashiya.core.data.repository.UserPreferencesRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Runs once at launch: collection follows the switch (release builds only), then the user properties are set. */
class AnalyticsStartup(
    private val analytics: Analytics,
    private val preferences: UserPreferencesRepository,
    private val libraryBackup: LibraryBackup,
    private val languageTag: () -> String,
    private val isRelease: Boolean,
) {
    suspend fun run() {
        analytics.setEnabled(isRelease && preferences.analyticsEnabled.first())
        analytics.setProperty(AnalyticsProperty.Language, Language.of(languageTag()))
        analytics.setProperty(AnalyticsProperty.HasOwnKey, YesNo.of(preferences.userApiKey.first() != null))
        val papers = try {
            libraryBackup.summary().papers
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (papers != null) analytics.setProperty(AnalyticsProperty.LibrarySizeBucket, LibrarySize.of(papers))
    }

    /** Keeps `has_own_key` current when the key changes in Settings. Never returns. */
    suspend fun followOwnKey() {
        preferences.userApiKey.map { it != null }.distinctUntilChanged().collect {
            analytics.setProperty(AnalyticsProperty.HasOwnKey, YesNo.of(it))
        }
    }
}
```

`HashiyaApplication.onCreate` — `@Inject lateinit var analytics: Analytics`; after the `CrashStartup` launch:

```kotlin
        scope.launch {
            val startup = AnalyticsStartup(
                analytics = analytics,
                preferences = preferences,
                libraryBackup = libraryBackup,
                languageTag = { AppCompatDelegate.getApplicationLocales().toLanguageTags() },
                isRelease = isRelease,
            )
            startup.run()
            startup.followOwnKey()
        }
```

`MainActivity` — next to the crash language key: `analytics.setProperty(AnalyticsProperty.Language, Language.of(AppCompatDelegate.getApplicationLocales().toLanguageTags()))` (inject `Analytics`).

`ScreenKey.kt` — `screenFor` stays; `ReportScreens(navController, reporter, analytics: Analytics)` also logs `AnalyticsEvent.ScreenView(screen)` for the same destination (map the existing string ids to `Screen` with a small `when`, or change `screenFor` to return `Screen?` and use `screen.id` for the crash key). Pass `analytics` from `MainActivity` through `HashiyaApp(...)`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest spotlessApply` and `./gradlew :app:assembleRelease` (unsigned is fine) to confirm the release variant builds with Firebase Analytics; check the merged release manifest has no `AD_ID`.

- [ ] **Step 5: Commit**

```bash
git add android/gradle/libs.versions.toml android/app
git commit -m "feat(android): add Firebase Analytics, only in release builds, with no advertising ids"
```

---

### Task 4: The Share usage statistics switch

**Files:**
- Modify: `android/core/datastore/src/main/java/com/etatech/hashiya/core/datastore/UserPreferencesDataSource.kt` (`analyticsEnabled` default true, key `analytics_enabled`)
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/repository/UserPreferencesRepository.kt` (interface + DataStore impl)
- Modify: `android/core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeUserPreferencesRepository.kt`
- Modify: `android/feature/settings/build.gradle.kts` (`implementation(project(":core:analytics"))`), `SettingsViewModel.kt`, `SettingsScreen.kt`, `res/values/strings.xml`, `res/values-ar/strings.xml`
- Test: `core/datastore/.../UserPreferencesDataSourceTest.kt`, `feature/settings/.../SettingsViewModelTest.kt`, `SettingsContentTest.kt`, `SettingsScreenshotTest.kt`, `SettingsBackupViewModelTest.kt` (constructor)
- Delete: `android/feature/settings/src/test/screenshots/settings_privacy-*.png` (re-recorded in Task 8)

**Interfaces:**
- Consumes: `Analytics`, `FakeAnalytics`, `Language` (Task 1).
- Produces: `UserPreferencesRepository.analyticsEnabled: Flow<Boolean>`, `suspend fun setAnalyticsEnabled(enabled: Boolean)`; `SettingsUiState.analyticsEnabled`; `SettingsViewModel(..., crashReporter, analytics: Analytics)`; `SettingsViewModel.onAnalyticsChange(enabled: Boolean)`; `SettingsContent(..., onAnalyticsChange: (Boolean) -> Unit = {})`.

- [ ] **Step 1: Write the failing tests**

`UserPreferencesDataSourceTest`: `analyticsOnByDefault`, `storesAnalyticsChoice` (copy the crash-reports tests).

`SettingsViewModelTest` (add `private val analytics = FakeAnalytics()` and pass it as the new last constructor argument everywhere, including the `initializer {}`):

```kotlin
    @Test
    fun usageStatisticsFollowTheStoredChoice() = runTest {
        preferences.setAnalyticsEnabled(false)
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.first { !it.analyticsEnabled }.analyticsEnabled)
    }

    @Test
    fun turningUsageStatisticsOffStoresItAndStopsCollection() = runTest {
        val viewModel = viewModel()
        viewModel.onAnalyticsChange(false)
        advanceUntilIdle()
        assertEquals(listOf(false), analytics.enabledCalls)
        assertFalse(preferences.analyticsEnabled.first())
    }

    @Test
    fun theStatisticsChoiceIsStoredEvenWhenSettingsCloses() = runTest { /* mirror theCrashReportsChoiceIsStoredEvenWhenSettingsCloses */ }

    @Test
    fun selectingALanguageUpdatesTheLanguageProperty() = runTest {
        val viewModel = viewModel()
        viewModel.onLanguageSelected(AppLanguage.Arabic)
        assertEquals("ar", analytics.properties[AnalyticsProperty.Language])
    }
```

`SettingsContentTest`: `usageStatisticsSwitchIsOnAndTurnsOff` / `…IsOffAndTurnsOn` (copy the crash-reports ones with the text "Share usage statistics"), and the footer text is shown.

`SettingsScreenshotTest.privacy`: pass `analyticsEnabled = true` (keep `arabicText = "إرسال تقارير الأعطال"`).

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:datastore:testDebugUnitTest :feature:settings:testDebugUnitTest`. Expected: compilation failures.

- [ ] **Step 3: Implement**

DataStore: `val analyticsEnabled: Flow<Boolean> = dataStore.data.map { it[ANALYTICS_ENABLED] ?: true }`, `suspend fun setAnalyticsEnabled(enabled: Boolean) { dataStore.edit { it[ANALYTICS_ENABLED] = enabled } }`, `val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")`. Repository and fake delegate the same way as `crashReportsEnabled`.

`SettingsViewModel`: new constructor parameter `private val analytics: Analytics` (after `crashReporter`); `SettingsUiState.analyticsEnabled: Boolean = true`; extend the outer `combine` with `preferences.analyticsEnabled`; and:

```kotlin
    fun onAnalyticsChange(enabled: Boolean) {
        analytics.setEnabled(enabled)
        viewModelScope.launch { withContext(NonCancellable) { preferences.setAnalyticsEnabled(enabled) } }
    }
```

(Debug builds get `NoOpAnalytics` from Hilt, so `setEnabled` there does nothing — same as the crash switch.) In `onLanguageSelected`, next to the crash key: `analytics.setProperty(AnalyticsProperty.Language, Language.of(<the same tags>))`.

Strings:
- `values/strings.xml`: `settings_usage_statistics` "Share usage statistics", `settings_usage_statistics_footer` "Counts of how features are used, tied to a random identifier, help decide what to improve. Never your papers, notes or searches."
- `values-ar/strings.xml`: `settings_usage_statistics` "مشاركة إحصاءات الاستخدام", `settings_usage_statistics_footer` "تساعد أعداد استخدام الميزات، المرتبطة بمعرّف عشوائي، على تحديد ما يجب تحسينه، دون أوراقك أو ملاحظاتك أو عمليات بحثك أبدًا."

`SettingsScreen.PrivacySection(crashReportsEnabled, onCrashReportsChange, analyticsEnabled, onAnalyticsChange)`: after the crash footer, a second row identical in shape (`toggleable(role = Role.Switch)`, `Switch(checked = analyticsEnabled, onCheckedChange = null)`, then its footer text), before the Privacy policy link. Wire `onAnalyticsChange = viewModel::onAnalyticsChange` and `uiState.analyticsEnabled`.

Delete the `settings_privacy-*` baselines (`git rm android/feature/settings/src/test/screenshots/settings_privacy-*.png`).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :core:datastore:testDebugUnitTest :feature:settings:testDebugUnitTest spotlessApply` (the privacy screenshot test will fail for the missing baseline only if run in verify mode — run only unit tests with `-Proborazzi.test.verify=false` if needed, as the other screenshot tasks did).

- [ ] **Step 5: Commit**

```bash
git add -A android/core android/feature/settings
git commit -m "feat(android): add the Share usage statistics switch"
```

---

### Task 5: Search, lookup, save and remove events

**Files:**
- Modify: `android/feature/search/build.gradle.kts` (`implementation(project(":core:analytics"))`), `SearchViewModel.kt`
- Modify: `android/feature/library/build.gradle.kts`, `LibraryViewModel.kt` (removal final)
- Test: `android/feature/search/src/test/java/com/etatech/hashiya/feature/search/SearchAnalyticsTest.kt` (new), `SearchViewModelTest.kt` (constructor), `feature/library/.../LibraryViewModelTest.kt`

**Interfaces:**
- Consumes: Task 1 events/values, `FakeAnalytics`; Task 2 `SearchResults.firstPage`/`pagesLoaded`, `FakeSearchRepository` emitting them.
- Produces: `SearchViewModel(savedStateHandle, searchRepository, libraryRepository, userPreferencesRepository, paperLookupRepository, analytics: Analytics)`; `LibraryViewModel(..., analytics: Analytics)`.

- [ ] **Step 1: Write the failing tests**

`SearchAnalyticsTest.kt` — build the view model like `SearchViewModelTest`'s helper with a `FakeAnalytics`; tests (assert the full event list where known):
- `aSubmittedKeywordSearchSendsSearchWhenItsFirstPageArrives`: `searchRepository.firstPage = FirstPage(48_210, ResearchCategory.Ai)`; type "bert", `onSearchAction()`; advance; events == `[Search(Keyword, false, Shared, Over200, Ai)]`.
- `aFilteredSearchSaysSo`: open-access chip on, then search → `hasFilters = true`; a chip change on an active search sends a second `search`.
- `aPersonalKeyMakesTheRouteUser`.
- `noEventCarriesTheSearchText`: search "Attention Is All You Need" and look up "10.1038/nature14539"; no event's `name`/`parameters` contains "Attention", "Need", "10.1038" or "nature14539".
- `furtherPagesAreCounted`: after the first page, set `lastPagesLoaded.value = 2` → `SearchMore(2)`.
- `aRestoredSearchSendsNothing`: a `SavedStateHandle` with a stored query (as `restoresQueryAfterProcessDeath` does) and a first page → no `search`.
- `anApiKeyChangeRerunSendsNothing`: search once (1 event), change the key → still 1 event.
- `aDoiLookupIsASearchOfKindDoi`: found → `[Search(Doi, false, Shared, UpTo25, null)]`; not found → `Zero`; failed → nothing; a link containing an arXiv id → kind `Link`.
- `savingSaysWhere`: result row → `PaperSaved(Search)`; found lookup → `PaperSaved(Lookup)`; found lookup that arrived from a share route (`pageTitle` set) → `PaperSaved(Share)`.
- `removingFromSearchCountsOnlyARealRemoval`: unsave a saved paper → `PaperRemoved`; `onRemoveRequested` for a paper that isn't saved → nothing.

`LibraryViewModelTest`: a removal sends nothing until `onUndoDismissed()` → one `PaperRemoved`; Undo → nothing; a second removal makes the first final → one `PaperRemoved`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :feature:search:testDebugUnitTest :feature:library:testDebugUnitTest`. Expected: compilation failures.

- [ ] **Step 3: Implement**

`SearchViewModel` (inject `analytics: Analytics`):
- **User-started searches (R4):** `private var countNextSearch = routeArgs != null` (a share or "Add paper" arrival is user-started); set `countNextSearch = true` in `onSearchAction()`, `onSuggestion(...)`, and the chip handlers (`onSortChange`, `onYearFilterChange`, `onOpenAccessToggle`, `onClearFilters`). In the `results` map, read-and-clear it per new `SearchResults`: `val counts = countNextSearch; countNextSearch = false`. A restore (initial `submittedText` from `SavedStateHandle`) and an `apiKey` change never set it.
- When `counts`, collect that `SearchResults`' `firstPage` (first non-null) and log `Search(SearchKind.Keyword, query.hasActiveFilters, route, ResultsBucket.of(first.total), first.category)` where `route = if (userApiKey.value != null) SearchRoute.User else SearchRoute.Shared`; and collect `pagesLoaded` logging `SearchMore(page)` for each page ≥ 2 (`distinctUntilChanged`). Run these collections in a job tied to the current results (`flatMapLatest`/`collectLatest`) so a superseded search stops logging.
- **Lookups:** the same flag pattern for lookups (`countNextLookup`, set by the same user actions and route args); when `paperLookupRepository.lookup(id)` returns and the flag was set: `Found` → `Search(kind, false, route, UpTo25, null)`, `NotFound` → `…Zero…`; `Failed` nothing. `kind`: `Link` if `looksLikeLink(submittedText)`, else `Doi`/`Arxiv` from the `PaperIdentifier`. `onRetryLookup()` sets the flag (user action).
- **Saves:** in `onToggleSave`, after a successful `save`: `from = when { lookup found matches item && shareContextActive -> Share; lookup found matches item -> Lookup; else -> Search }`, where `shareContextActive` = the share route args (`pageTitle` or share note) are still present (not cleared by `forgetShareContext()`).
- **Removals:** in `onToggleSave`'s remove branch and `onRemoveRequested`, log `PaperRemoved` only when `libraryRepository.remove(...)` returned non-null.

`LibraryViewModel` (inject `analytics`): log `PaperRemoved` in `onUndoDismissed()` when a pending removal existed, and in `remove()` where a previous pending removal is replaced (becomes final). Not in `onUndoRemove()`, not for collection-scoped removals.

Update every `SearchViewModel(...)`/`LibraryViewModel(...)` construction in tests to pass `FakeAnalytics()` (or `NoOpAnalytics`).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :feature:search:testDebugUnitTest :feature:library:testDebugUnitTest spotlessApply`. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/feature/search android/feature/library
git commit -m "feat(android): count searches, lookups, saves and removals"
```

---

### Task 6: Notes, collections, exports, restore and PDF events

**Files:**
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/notes/NotesEditor.kt` (optional `onSaved: () -> Unit = {}`)
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/repository/RoomPdfRepository.kt` (defaulted `analytics: Analytics = NoOpAnalytics`, `@Inject` constructor takes it)
- Modify: `android/feature/paperdetails/build.gradle.kts`, `PaperDetailsViewModel.kt`; `android/feature/reader/build.gradle.kts`, `ReaderViewModel.kt`; `LibraryViewModel.kt` (collection create, BibTeX export); `SettingsViewModel.kt` (backup export); `feature/settings/.../restore/RestoreViewModel.kt`
- Test: `PaperDetailsViewModelTest`, `PaperDetailsCollectionsViewModelTest`, `ReaderViewModelTest`, `LibraryCollectionsViewModelTest`, `LibraryViewModelTest`, `SettingsBackupViewModelTest`, `RestoreViewModelTest`, `RoomPdfRepositoryTest`

**Interfaces:**
- Consumes: Task 1 events, `NotedPapers`, `FakeAnalytics`.
- Produces: `analytics: Analytics` constructor parameters on `PaperDetailsViewModel`, `ReaderViewModel`, `RestoreViewModel` (last parameter); `NotesEditor(..., onSaveFailed, onSaved: () -> Unit = {})`.

- [ ] **Step 1: Write the failing tests** (one per behaviour, each with `FakeAnalytics`)
- **Notes:** two saves of one paper's notes → one `NoteEdited` (use a random id); another paper → a second; a failed save → nothing; a note containing "10.1038/nature14539" appears in no event.
- **Collections:** Library create (`CollectionDialog.New` → `Done`) → `CollectionCreated`; rename → nothing; Details new collection → `CollectionCreated` then `PaperAddedToCollection`; `onToggleCollection(id, member = true)` → `PaperAddedToCollection`; `member = false` → nothing; the Library's Undo of a collection removal → nothing.
- **Exports:** `onExportShared()` → `Export(Bibtex, false)`; `onExportFailed()` → nothing; backup `onSaveDestination(uri)` success → `Export(Backup, withPdfs = <the chosen includePdfs>)`; null uri (cancel) or failure → nothing.
- **Restore:** `Done` → `Restore(true)`; `Failed` → `Restore(false)`; cancel or an invalid file → nothing.
- **PDF opened:** the reader reaching `Ready` → `PdfOpened(source)` with the stored `PdfSource`, once per view model; `CantOpen` → nothing.
- **PDF downloaded:** stored (first link or fallback) → `PdfDownloaded(true)`; ends `Failed` → `PdfDownloaded(false)`; cancelled or paper gone → nothing.

- [ ] **Step 2: Run them to verify they fail**

Run the affected modules' `testDebugUnitTest`. Expected: compilation failures.

- [ ] **Step 3: Implement**
- `NotesEditor.save`: after a successful `libraryRepository.saveNotes(...)` (value changed), call `onSaved()`. `PaperDetailsViewModel` and `ReaderViewModel` pass `onSaved = { if (NotedPapers.firstEdit(openAlexId)) analytics.log(AnalyticsEvent.NoteEdited) }`.
- `LibraryViewModel.onDialogConfirm`: `CollectionCreated` on `Done` for `CollectionDialog.New` only. `onExportShared()`: `Export(Bibtex, withPdfs = false)`.
- `PaperDetailsViewModel.onNewCollectionConfirm`: `CollectionCreated` after `Done`, `PaperAddedToCollection` after the membership write; `onToggleCollection`: `PaperAddedToCollection` when `member` and the write succeeded.
- `SettingsViewModel`: remember `includePdfs` when building starts (a private field next to `exported`); on `BackupMessage.Exported` → `Export(Backup, includePdfs)`.
- `RestoreViewModel.onConfirm`: `Restore(true)` with `Done`, `Restore(false)` with `Failed` (not for `CancellationException`).
- `ReaderViewModel.load()`: after `_state.value = ReaderState.Ready(...)` the first time, `PdfOpened(if (pdf.source == PdfSource.Attached) PdfOrigin.Attached else PdfOrigin.Downloaded)`.
- `RoomPdfRepository.runDownload`: `PdfDownloaded(true)` where the outcome is `Attempt.Stored` (first link or a fallback link), `PdfDownloaded(false)` where it reports `DownloadState.Failed`; nothing for `Attempt.Gone` or cancellation. Hilt passes the app's `Analytics`.

Update every construction in tests (positional constructor calls) to pass `FakeAnalytics()`/`NoOpAnalytics`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest spotlessApply` (whole project). Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android
git commit -m "feat(android): count notes, collections, exports, restores and PDFs"
```

---

### Task 7: Play answers, release docs and changelog

**Files:**
- Modify: `docs/store/metadata.md` (Play Data safety), `docs/release.md` (Android analytics section), `CHANGELOG.md`

- [ ] **Step 1: Play Data safety**

In `### Play Console: Data safety`, update the collected data to:
- **App activity → App interactions** — collected for **Analytics**; not shared; optional (Settings → Privacy → Share usage statistics).
- **Location → Approximate location** — derived by Google Analytics from the IP address; Analytics; not shared; optional.
- **App info and performance → Crash logs, Diagnostics** — as today (App functionality, Analytics/stability).
- **Device or other IDs** — Firebase installation and app-instance ids; App functionality and Analytics.
- **Advertising ID:** "Does your app use advertising ID?" → **No** (the `AD_ID` permission is removed).
- Keep the deletion answer's wording ("tied only to a random per-install identifier, not to a name or account…") and add usage statistics: Google deletes detailed analytics data after 2 months; overall totals remain.

- [ ] **Step 2: Release docs**

In `docs/release.md`:
- In `## Crashlytics (Android)`, step 1 "turn Google Analytics off for it" becomes "link Google Analytics (GA4) when usage statistics ship: data retention 2 months, Google signals off, no Ads links"; replace the intro sentence about Android usage statistics with "Release builds also send usage statistics (Firebase Analytics) while Settings → Share usage statistics is on; debug builds never do."
- In the "Check before a release…" list add: run a release build with `adb shell setprop debug.firebase.analytics.app com.etatech.hashiya`; Firebase DebugView shows the events with only the listed parameters and no advertising id; with **Share usage statistics** off, nothing new arrives; the privacy-policy update (FadyFouad/Hashiya-Privacy-Policy#2) is merged before the build reaches any tester.

- [ ] **Step 3: Changelog**

Under `## [Unreleased]` → `### Added`:

```markdown
- **Android: usage statistics.** Release builds send usage statistics (Firebase Analytics), tied to a random identifier rather than your name or account: which features are used and a broad research area worked out on the device from search results — never search text, papers or notes. On by default; turn it off in Settings → Privacy → Share usage statistics.
```

- [ ] **Step 4: Commit**

```bash
git add docs/store/metadata.md docs/release.md CHANGELOG.md
git commit -m "docs: Android usage statistics in the Play answers, release steps and changelog"
```

---

### Task 8: Screenshot baselines (controller, after pushing the branch)

- [ ] From `feat/android-analytics`: `scripts/record-screenshots-on-linux.sh`. Look at `settings_privacy-*` in English and Arabic (both switches, footers, the policy link) before committing; drop unrelated re-recorded images.
