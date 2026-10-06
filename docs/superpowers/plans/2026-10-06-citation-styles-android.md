# Citation Styles (Android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** APA 7 and IEEE next to BibTeX: copy a formatted citation from a paper's Details (rich text with a plain fallback) and export a formatted reference list (`.rtf`) from the Library, remembering the last style used.

**Architecture:** A new pure-Kotlin module `:core:citation` turns a `Paper` into a `StyledCitation` (runs of plain or italic text) per style, and renders it as plain text, HTML or RTF. The kind of work (`WorkKind`) moves to `:core:model` so BibTeX and the new styles share one table. `CitationRepository` gains a style parameter and returns plain/HTML/RTF; a `citationStyle` preference in DataStore remembers the choice. Details' overflow menu and the Library's export button offer the three styles.

**Tech Stack:** Kotlin (JVM modules), Jetpack Compose, Hilt, Room, DataStore, JUnit4, Robolectric, Roborazzi.

**Spec:** `docs/superpowers/specs/2026-10-06-citation-styles-design.md`

## Global Constraints

- Styles: `CitationStyle` = `Apa` ("apa"), `Ieee` ("ieee"), `Bibtex` ("bibtex"); default `Apa`.
- Formatting is offline; the only network use is the existing details refetch in `RoomCitationRepository`.
- BibTeX output must not change (its existing tests stay green, unedited except for the `.bibtex` → `.text` field rename).
- Titles are kept exactly as saved (no sentence case). Untitled: APA `[Untitled]`, IEEE `"Untitled"`.
- Pages use an en dash `–` (U+2013); a single page is `p. x` where the style uses `pp.`.
- DOI: APA `https://doi.org/<doi>`; IEEE `doi: <doi>`; no DOI → open-access URL (APA as is; IEEE `[Online]. Available: <url>`), else nothing. A stored DOI may already start with `https://doi.org/`; never double it.
- APA authors: `Family, I. I.`, `, ` between, `, & ` before the last; 21 or more → first 19, `, . . . `, last. IEEE: `I. I. Family`; two → ` and `; 3–6 → `, ` and `, and `; 7 or more → first author + ` ` + italic `et al.`.
- Names: family name = last word; initials from the other words; hyphenated given names keep the hyphen (`J.-P.`); one-word names and names containing Arabic-script letters (U+0600–U+06FF) are kept whole.
- Thesis is the neutral "Thesis" in both styles.
- APA list: sorted by first author's family name (no authors → title), case- and diacritic-insensitive, then year (none last), then title; 0.5-inch hanging indent in RTF. IEEE list: `[1] ` … in the library's saved order (the order `CitationDao.getPapers` returns).
- RTF: chars above U+007F → `\uN?` per UTF-16 unit (signed 16-bit); `\`, `{`, `}` escaped.
- Files: `<name>.bib`, `<name> – APA.rtf`, `<name> – IEEE.rtf` (en dash with spaces), `hashiya-library` when no collection; RTF MIME `application/rtf`.
- Analytics: `ExportFormat` gains `Apa("apa")`, `Ieee("ieee")`; no new events; copying logs nothing.
- minSdk 24 (no `java.time` in main code); run `./gradlew lintDebug` before each task's last commit.
- Commits authored `Fady <fady.fouad.a@gmail.com>`; no AI attribution or trailers.

## Review Focus

1. Arabic titles and names survive the `.rtf` file and the clipboard HTML (Task 2 tests RTF escaping of Arabic and an emoji above U+FFFF).
2. A DOI saved as `https://doi.org/10.…` is not printed as `https://doi.org/https://doi.org/…` (Tasks 3 and 4 test it).
3. A paper with no authors and no year still reads correctly: APA `Title. (n.d.). …`, IEEE starts with the title (Tasks 3 and 4).
4. An empty collection exports a valid empty RTF document, not a crash (Task 5 test).
5. A collection named with characters files can't hold still gets a safe `– APA.rtf` name (Task 7 test).

---

### Task 1: `WorkKind` and `CitationStyle` in `:core:model`

**Files:**
- Create: `android/core/model/src/main/kotlin/com/etatech/hashiya/core/model/WorkKind.kt`
- Create: `android/core/model/src/main/kotlin/com/etatech/hashiya/core/model/CitationStyle.kt`
- Modify: `android/core/bibtex/src/main/kotlin/com/etatech/hashiya/core/bibtex/EntryType.kt`
- Test: `android/core/model/src/test/kotlin/com/etatech/hashiya/core/model/WorkKindTest.kt`

**Interfaces:**
- Produces: `enum class WorkKind { Article, Conference, Chapter, Book, Thesis, Report, Preprint, Other }`; `fun PublicationDetails.workKind(): WorkKind`; `enum class CitationStyle(val id: String) { Apa("apa"), Ieee("ieee"), Bibtex("bibtex"); companion object { fun fromId(id: String?): CitationStyle } }`.

- [ ] **Step 1: Failing test** — `WorkKindTest.kt`:
```kotlin
package com.etatech.hashiya.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkKindTest {
    private fun kind(work: String?, source: String?) = PublicationDetails(workType = work, sourceType = source).workKind()

    @Test
    fun followsTheBibTeXTableInOrder() {
        assertEquals(WorkKind.Conference, kind("article", "conference"))
        assertEquals(WorkKind.Chapter, kind("book-chapter", "book"))
        assertEquals(WorkKind.Book, kind("book", null))
        assertEquals(WorkKind.Thesis, kind("dissertation", null))
        assertEquals(WorkKind.Report, kind("report", null))
        assertEquals(WorkKind.Preprint, kind("preprint", null))
        assertEquals(WorkKind.Preprint, kind("article", "repository"))
        assertEquals(WorkKind.Article, kind("Article", "Journal"))
        assertEquals(WorkKind.Article, kind("review", "journal"))
    }

    @Test
    fun anythingElseIsOther() {
        assertEquals(WorkKind.Other, kind(null, null))
        assertEquals(WorkKind.Other, kind("article", null))
        assertEquals(WorkKind.Other, kind("dataset", "journal"))
    }

    @Test
    fun stylesRoundTripTheirIdsAndDefaultToApa() {
        CitationStyle.entries.forEach { assertEquals(it, CitationStyle.fromId(it.id)) }
        assertEquals(CitationStyle.Apa, CitationStyle.fromId(null))
        assertEquals(CitationStyle.Apa, CitationStyle.fromId("harvard"))
    }
}
```
- [ ] **Step 2:** `cd android && ./gradlew :core:model:test` → FAIL to compile (`workKind`, `CitationStyle` unresolved).
- [ ] **Step 3: Implement.** `WorkKind.kt`:
```kotlin
package com.etatech.hashiya.core.model

import java.util.Locale

/** What kind of work a paper is, from OpenAlex's work and source types; BibTeX and the citation styles share it. */
enum class WorkKind { Article, Conference, Chapter, Book, Thesis, Report, Preprint, Other }

private val JOURNAL_WORK_TYPES = setOf("article", "review", "letter", "editorial")

/** First match wins: a conference article is a conference paper, a repository article a preprint. */
fun PublicationDetails.workKind(): WorkKind {
    val work = workType?.lowercase(Locale.ROOT)
    val source = sourceType?.lowercase(Locale.ROOT)
    return when {
        source == "conference" -> WorkKind.Conference
        work == "book-chapter" -> WorkKind.Chapter
        work == "book" -> WorkKind.Book
        work == "dissertation" -> WorkKind.Thesis
        work == "report" -> WorkKind.Report
        work == "preprint" || source == "repository" -> WorkKind.Preprint
        work in JOURNAL_WORK_TYPES && source == "journal" -> WorkKind.Article
        else -> WorkKind.Other
    }
}
```
`CitationStyle.kt`:
```kotlin
package com.etatech.hashiya.core.model

/** How a citation or a reference list is written; [id] is what the preference and analytics store. */
enum class CitationStyle(val id: String) {
    Apa("apa"),
    Ieee("ieee"),
    Bibtex("bibtex");

    companion object {
        /** APA when nothing, or something unknown, is stored. */
        fun fromId(id: String?): CitationStyle = entries.firstOrNull { it.id == id } ?: Apa
    }
}
```
In `EntryType.kt`, delete `JOURNAL_WORK_TYPES` and the `when` table; `entryType` becomes:
```kotlin
/** The BibTeX type for a kind of work: preprints and anything else are @misc. */
internal fun entryType(details: PublicationDetails): EntryType = when (details.workKind()) {
    WorkKind.Conference -> EntryType.InProceedings
    WorkKind.Chapter -> EntryType.InCollection
    WorkKind.Book -> EntryType.Book
    WorkKind.Thesis -> EntryType.PhdThesis
    WorkKind.Report -> EntryType.TechReport
    WorkKind.Article -> EntryType.Article
    WorkKind.Preprint, WorkKind.Other -> EntryType.Misc
}
```
(imports `com.etatech.hashiya.core.model.WorkKind`, `com.etatech.hashiya.core.model.workKind`; drop `java.util.Locale`).
- [ ] **Step 4:** `./gradlew :core:model:test :core:bibtex:test` → PASS (BibTeX's `EntryTypeTest` and `BibTeXTest` unchanged and green).
- [ ] **Step 5:** `./gradlew spotlessApply` then commit: `git add android/core/model android/core/bibtex && git commit -m "refactor(android): share the kind of work between BibTeX and citation styles"`

---

### Task 2: `:core:citation` — runs, names and renderers

**Files:**
- Modify: `android/settings.gradle.kts` (`include(":core:citation")` after `:core:bibtex`)
- Create: `android/core/citation/build.gradle.kts`
- Create: `android/core/citation/src/main/kotlin/com/etatech/hashiya/core/citation/StyledCitation.kt`
- Create: `android/core/citation/src/main/kotlin/com/etatech/hashiya/core/citation/Names.kt`
- Create: `android/core/citation/src/main/kotlin/com/etatech/hashiya/core/citation/Rendering.kt`
- Test: `android/core/citation/src/test/kotlin/com/etatech/hashiya/core/citation/NamesTest.kt`
- Test: `android/core/citation/src/test/kotlin/com/etatech/hashiya/core/citation/RenderingTest.kt`

**Interfaces:**
- Produces: `data class Run(val text: String, val italic: Boolean = false)`; `data class StyledCitation(val runs: List<Run>)` with `val plain: String`; `internal class CitationBuilder { fun text(s: String); fun italic(s: String); fun runs(r: List<Run>); fun endSentence(); fun build(): StyledCitation }`; `internal data class PersonName(val family: String, val initials: String?)`; `internal fun personName(name: String): PersonName`; `object Rendering { fun plain(c: StyledCitation): String; fun html(c: StyledCitation): String; fun rtf(entries: List<StyledCitation>, hangingIndent: Boolean): String }`.

- [ ] **Step 1: Module.** `build.gradle.kts`:
```kotlin
plugins {
    id("hashiya.jvm.library")
}

dependencies {
    api(project(":core:model"))
}
```
and `include(":core:citation")` in `settings.gradle.kts` after `include(":core:bibtex")`.

- [ ] **Step 2: Failing tests.** `NamesTest.kt`:
```kotlin
package com.etatech.hashiya.core.citation

import org.junit.Assert.assertEquals
import org.junit.Test

class NamesTest {
    @Test
    fun familyIsTheLastWordAndInitialsTheRest() {
        assertEquals(PersonName("Vaswani", "A."), personName("Ashish Vaswani"))
        assertEquals(PersonName("Gomez", "A. N."), personName("Aidan N. Gomez"))
        assertEquals(PersonName("Kaiser", "Ł."), personName("  Łukasz   Kaiser "))
    }

    @Test
    fun hyphenatedGivenNamesKeepTheHyphen() = assertEquals(PersonName("Sartre", "J.-P."), personName("Jean-Paul Sartre"))

    @Test
    fun oneWordAndArabicNamesStayWhole() {
        assertEquals(PersonName("OpenAI", null), personName("OpenAI"))
        assertEquals(PersonName("محمد عبد الله", null), personName("محمد عبد الله"))
    }
}
```
`RenderingTest.kt`:
```kotlin
package com.etatech.hashiya.core.citation

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderingTest {
    private val citation = StyledCitation(listOf(Run("A & B <x> \"q\". "), Run("Journal", italic = true), Run(", 1.")))

    @Test
    fun plainJoinsTheRuns() = assertEquals("A & B <x> \"q\". Journal, 1.", Rendering.plain(citation))

    @Test
    fun htmlEscapesAndItalicises() =
        assertEquals("A &amp; B &lt;x&gt; &quot;q&quot;. <i>Journal</i>, 1.", Rendering.html(citation))

    @Test
    fun rtfEscapesControlCharactersAndWritesUnicode() {
        val rtf = Rendering.rtf(listOf(StyledCitation(listOf(Run("a\\b{c}"), Run("ع😀", italic = true)))), hangingIndent = false)
        assertEquals(
            "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs24\n" +
                "{\\pard a\\\\b\\{c\\}{\\i \\u1593?\\u-10179?\\u-8704?}\\par}\n" +
                "}",
            rtf
        )
    }

    @Test
    fun rtfHangingIndentIsOnlyForApa() {
        val one = listOf(StyledCitation(listOf(Run("x"))))
        assertEquals(true, Rendering.rtf(one, hangingIndent = true).contains("{\\pard\\fi-720\\li720 x\\par}"))
        assertEquals(true, Rendering.rtf(one, hangingIndent = false).contains("{\\pard x\\par}"))
    }

    @Test
    fun anEmptyListIsAValidEmptyDocument() =
        assertEquals("{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs24\n}", Rendering.rtf(emptyList(), hangingIndent = true))
}
```
(`ع` is U+0639 = 1593; 😀 is U+1F600 = surrogates D83D DE00 = signed −10179, −8704.)
- [ ] **Step 3:** `./gradlew :core:citation:test` → FAIL to compile.
- [ ] **Step 4: Implement.** `StyledCitation.kt`:
```kotlin
package com.etatech.hashiya.core.citation

/** A piece of a citation: plain or italic text. */
data class Run(val text: String, val italic: Boolean = false)

/** A formatted citation as runs, so each output (plain, HTML, RTF) can show the italics its own way. */
data class StyledCitation(val runs: List<Run>) {
    val plain: String get() = Rendering.plain(this)
}

/** Builds the runs, merging neighbours of the same kind. */
internal class CitationBuilder {
    private val runs = mutableListOf<Run>()

    fun text(s: String) = add(Run(s))

    fun italic(s: String) = add(Run(s, italic = true))

    fun runs(more: List<Run>) = more.forEach(::add)

    /** Ends a sentence: a full stop unless the text already ends with . ? or !. */
    fun endSentence() {
        val last = runs.lastOrNull()?.text?.lastOrNull()
        if (last != '.' && last != '?' && last != '!') text(".")
    }

    fun build() = StyledCitation(runs.toList())

    private fun add(run: Run) {
        if (run.text.isEmpty()) return
        val last = runs.lastOrNull()
        if (last != null && last.italic == run.italic) runs[runs.size - 1] = last.copy(text = last.text + run.text) else runs += run
    }
}
```
`Names.kt`:
```kotlin
package com.etatech.hashiya.core.citation

/** A person's family name and initials; [initials] is null when the name is kept whole. */
internal data class PersonName(val family: String, val initials: String?)

private val WHITESPACE = Regex("\\s+")

/** "Aidan N. Gomez" → Gomez, A. N. One-word names (organisations) and Arabic-script names are kept whole. */
internal fun personName(name: String): PersonName {
    val trimmed = name.trim().replace(WHITESPACE, " ")
    val parts = trimmed.split(' ')
    if (parts.size < 2 || trimmed.any { it in '\u0600'..'\u06FF' }) return PersonName(trimmed, null)
    val initials = parts.dropLast(1).joinToString(" ") { given ->
        given.split('-').filter { it.isNotEmpty() }.joinToString("-") { "${it.first().uppercaseChar()}." }
    }
    return PersonName(parts.last(), initials)
}
```
`Rendering.kt`:
```kotlin
package com.etatech.hashiya.core.citation

/** Plain text, HTML for the rich clipboard, and RTF for exported reference lists. */
object Rendering {
    fun plain(citation: StyledCitation): String = citation.runs.joinToString("") { it.text }

    fun html(citation: StyledCitation): String = citation.runs.joinToString("") { run ->
        val escaped = escapeHtml(run.text)
        if (run.italic) "<i>$escaped</i>" else escaped
    }

    /** One paragraph per entry; APA entries get a 0.5-inch hanging indent. Readable by Word and Pages. */
    fun rtf(entries: List<StyledCitation>, hangingIndent: Boolean): String = buildString {
        append("{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs24\n")
        for (entry in entries) {
            append(if (hangingIndent) "{\\pard\\fi-720\\li720 " else "{\\pard ")
            for (run in entry.runs) {
                if (run.italic) append("{\\i ").append(escapeRtf(run.text)).append('}') else append(escapeRtf(run.text))
            }
            append("\\par}\n")
        }
        append('}')
    }

    private fun escapeHtml(text: String) = buildString {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }

    /** RTF is 7-bit: everything above U+007F is written as \uN? per UTF-16 unit (N signed 16-bit). */
    private fun escapeRtf(text: String) = buildString {
        for (c in text) {
            when {
                c == '\\' || c == '{' || c == '}' -> append('\\').append(c)
                c.code > 0x7F -> append("\\u").append(c.code.toShort().toInt()).append('?')
                else -> append(c)
            }
        }
    }
}
```
- [ ] **Step 5:** `./gradlew :core:citation:test` → PASS (8 tests). If the RTF emoji assertion fails, print `Rendering.rtf(...)` and check the surrogate maths, not the expectation.
- [ ] **Step 6:** `./gradlew spotlessApply`; commit: `git add android/settings.gradle.kts android/core/citation && git commit -m "feat(android): styled citations and their plain, HTML and RTF forms"`

---

### Task 3: APA 7

**Files:**
- Create: `android/core/citation/src/main/kotlin/com/etatech/hashiya/core/citation/Apa.kt`
- Create: `android/core/citation/src/main/kotlin/com/etatech/hashiya/core/citation/Common.kt`
- Test: `android/core/citation/src/test/kotlin/com/etatech/hashiya/core/citation/ApaTest.kt`
- Test: `android/core/citation/src/test/kotlin/com/etatech/hashiya/core/citation/TestPapers.kt`

**Interfaces:**
- Consumes: Task 1 `WorkKind`/`workKind()`; Task 2 `CitationBuilder`, `personName`, `StyledCitation`.
- Produces: `object Apa { fun format(paper: Paper): StyledCitation; fun list(papers: List<Paper>): List<StyledCitation> }`; internal helpers in `Common.kt`: `doiOf(raw: String): String`, `pageRange(first: String?, last: String?): String?` (returns `"x–y"` or `"x"`), `isSinglePage(first, last): Boolean`, `sortKey(text: String): String`.

- [ ] **Step 1: Test papers.** `TestPapers.kt`:
```kotlin
package com.etatech.hashiya.core.citation

import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PublicationDetails

internal fun paper(
    title: String = "Attention is all you need",
    authors: List<String> = listOf("Ashish Vaswani", "Noam Shazeer"),
    year: Int? = 2017,
    venue: String? = "Advances in Neural Information Processing Systems",
    doi: String? = "10.5555/3295222.3295349",
    pdf: String? = null,
    work: String? = "article",
    source: String? = "journal",
    publisher: String? = null,
    volume: String? = null,
    issue: String? = null,
    first: String? = null,
    last: String? = null
) = Paper(
    openAlexId = "W1", doi = doi, title = title, authors = authors.map { Author(it, null) }, year = year, venue = venue,
    abstract = null, citationCount = 0, isOpenAccess = pdf != null, openAccessPdfUrl = pdf,
    publication = PublicationDetails(work, source, publisher, volume, issue, first, last)
)
```
- [ ] **Step 2: Failing tests.** `ApaTest.kt` (one per kind, then the edges):
```kotlin
package com.etatech.hashiya.core.citation

import org.junit.Assert.assertEquals
import org.junit.Test

class ApaTest {
    private fun apa(p: com.etatech.hashiya.core.model.Paper) = Apa.format(p)

    @Test
    fun journalArticle() = assertEquals(
        listOf(
            Run("Vaswani, A., & Shazeer, N. (2017). Attention is all you need. "),
            Run("Nature", italic = true), Run(", "), Run("521", italic = true), Run("(7553), 436–444. https://doi.org/10.1038/nature14539")
        ),
        apa(paper(venue = "Nature", doi = "10.1038/nature14539", volume = "521", issue = "7553", first = "436", last = "444")).runs
    )

    @Test
    fun conferencePaper() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. In Advances in Neural Information Processing Systems " +
            "(pp. 5998–6008). Curran Associates. https://doi.org/10.5555/3295222.3295349",
        apa(paper(source = "conference", publisher = "Curran Associates", first = "5998", last = "6008")).plain
    )

    @Test
    fun bookChapterSinglePage() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. In Deep Learning (p. 12). MIT Press. https://doi.org/10.5555/3295222.3295349",
        apa(paper(work = "book-chapter", venue = "Deep Learning", publisher = "MIT Press", first = "12", last = "12")).plain
    )

    @Test
    fun bookItalicisesTheTitle() = assertEquals(
        listOf(Run("Vaswani, A., & Shazeer, N. (2017). "), Run("Attention is all you need", italic = true), Run(". MIT Press. https://doi.org/10.5555/3295222.3295349")),
        apa(paper(work = "book", source = null, publisher = "MIT Press")).runs
    )

    @Test
    fun thesis() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Attention is all you need [Thesis, University of Toronto]. https://doi.org/10.5555/3295222.3295349",
        apa(paper(work = "dissertation", source = null, venue = "University of Toronto")).plain
    )

    @Test
    fun reportUsesThePublisherElseTheVenue() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. Google Research. https://doi.org/10.5555/3295222.3295349",
        apa(paper(work = "report", source = null, venue = "Google Research")).plain
    )

    @Test
    fun preprint() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Attention is all you need [Preprint]. arXiv. https://doi.org/10.48550/arXiv.1706.03762",
        apa(paper(work = "preprint", source = "repository", venue = "arXiv", doi = "https://doi.org/10.48550/arXiv.1706.03762")).plain
    )

    @Test
    fun other() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. Zenodo.",
        apa(paper(work = "dataset", source = null, venue = "Zenodo", doi = null)).plain
    )

    @Test
    fun noDoiUsesTheOpenAccessLink() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. Zenodo. https://example.org/a.pdf",
        apa(paper(work = "dataset", source = null, venue = "Zenodo", doi = null, pdf = "https://example.org/a.pdf")).plain
    )

    @Test
    fun noAuthorsNoYearNoTitle() {
        assertEquals("[Untitled]. (n.d.). Zenodo.", apa(paper(title = " ", authors = emptyList(), year = null, work = null, source = null, venue = "Zenodo", doi = null)).plain)
        assertEquals("Attention is all you need. (n.d.). Zenodo.", apa(paper(authors = emptyList(), year = null, work = null, source = null, venue = "Zenodo", doi = null)).plain)
    }

    @Test
    fun aTitleEndingInAQuestionMarkGetsNoFullStop() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Is attention all you need? Zenodo.",
        apa(paper(title = "Is attention all you need?", work = null, source = null, venue = "Zenodo", doi = null)).plain
    )

    @Test
    fun authorLists() {
        fun names(n: Int) = (1..n).map { "Given$it Family$it" }
        assertEquals("Family1, G.", Apa.authors(names(1)))
        assertEquals("Family1, G., & Family2, G.", Apa.authors(names(2)))
        assertEquals("Family1, G., Family2, G., & Family3, G.", Apa.authors(names(3)))
        assertEquals(20, Apa.authors(names(20)).split("Family").size - 1)
        val many = Apa.authors(names(21))
        assertEquals(true, many.contains("Family19, G., . . . Family21, G."))
        assertEquals(false, many.contains("Family20"))
        assertEquals("OpenAI, & Family2, G.", Apa.authors(listOf("OpenAI", "Given2 Family2")))
    }

    @Test
    fun listSortsByFamilyIgnoringCaseAndDiacriticsThenYearThenTitle() {
        val list = Apa.list(
            listOf(
                paper(title = "B", authors = listOf("Zoe Zed"), year = 2020),
                paper(title = "C", authors = listOf("Ann Émile"), year = null),
                paper(title = "A", authors = listOf("Ann emile"), year = 2019),
                paper(title = "D", authors = emptyList(), year = 2018)
            )
        ).map { it.plain.substringBefore(" (") }
        assertEquals(listOf("D.", "emile, A.", "Émile, A.", "Zed, Z."), list)
    }
}
```
- [ ] **Step 3:** `./gradlew :core:citation:test` → FAIL to compile (`Apa` unresolved).
- [ ] **Step 4: Implement.** `Common.kt`:
```kotlin
package com.etatech.hashiya.core.citation

import java.text.Normalizer
import java.util.Locale

private val DOI_PREFIX = Regex("^(https?://(dx\\.)?doi\\.org/|doi:)", RegexOption.IGNORE_CASE)
private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** "10.1/x" from "10.1/x", "https://doi.org/10.1/x" or "doi:10.1/x". */
internal fun doiOf(raw: String): String = raw.trim().replace(DOI_PREFIX, "")

/** "436–444", or "12" for a single page; null when there is no first page. */
internal fun pageRange(first: String?, last: String?): String? {
    val a = first?.trim()?.ifEmpty { null } ?: return null
    val b = last?.trim()?.ifEmpty { null }
    return if (b == null || b == a) a else "$a–$b"
}

internal fun isSinglePage(first: String?, last: String?): Boolean {
    val b = last?.trim()?.ifEmpty { null }
    return b == null || b == first?.trim()
}

/** Lower case without diacritics, for sorting. */
internal fun sortKey(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase(Locale.ROOT)

internal fun String?.orNullIfBlank(): String? = this?.trim()?.ifEmpty { null }
```
`Apa.kt`:
```kotlin
package com.etatech.hashiya.core.citation

import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.WorkKind
import com.etatech.hashiya.core.model.workKind

/** APA 7 references, from the data a saved paper has (see the spec's table). */
object Apa {
    private val ITALIC_TITLE = setOf(WorkKind.Book, WorkKind.Thesis, WorkKind.Report, WorkKind.Preprint, WorkKind.Other)

    fun format(paper: Paper): StyledCitation {
        val b = CitationBuilder()
        val kind = paper.publication.workKind()
        val venue = paper.venue.orNullIfBlank()
        val year = "(${paper.year ?: "n.d."})."
        if (paper.authors.isEmpty()) {
            title(b, paper, kind, venue)
            b.text(" $year")
        } else {
            b.text(authors(paper.authors.map { it.name }))
            b.text(" $year ")
            title(b, paper, kind, venue)
        }
        source(b, paper, kind, venue)
        link(paper)?.let { b.text(" $it") }
        return b.build()
    }

    /** Sorted by first author's family name (no authors: the title), then year (none last), then title. */
    fun list(papers: List<Paper>): List<StyledCitation> = papers.sortedWith(
        compareBy<Paper> { sortKey(it.authors.firstOrNull()?.let { a -> personName(a.name).family } ?: it.title) }
            .thenBy { it.year == null }
            .thenBy { it.year }
            .thenBy { sortKey(it.title) }
    ).map(::format)

    internal fun authors(names: List<String>): String {
        val written = names.map { name -> personName(name).let { p -> p.initials?.let { "${p.family}, $it" } ?: p.family } }
        return when {
            written.size == 1 -> written[0]
            written.size <= 20 -> written.dropLast(1).joinToString(", ") + ", & " + written.last()
            else -> written.take(19).joinToString(", ") + ", . . . " + written.last()
        }
    }

    private fun title(b: CitationBuilder, paper: Paper, kind: WorkKind, venue: String?) {
        val title = paper.title.orNullIfBlank()
        when {
            title == null -> b.text("[Untitled]")
            kind in ITALIC_TITLE -> b.italic(title)
            else -> b.text(title)
        }
        when (kind) {
            WorkKind.Thesis -> b.text(" [Thesis" + (venue?.let { ", $it" } ?: "") + "]")
            WorkKind.Preprint -> b.text(" [Preprint]")
            else -> Unit
        }
        b.endSentence()
    }

    private fun source(b: CitationBuilder, paper: Paper, kind: WorkKind, venue: String?) {
        val d = paper.publication
        val publisher = d.publisher.orNullIfBlank()
        val pages = pageRange(d.firstPage, d.lastPage)
        when (kind) {
            WorkKind.Article -> if (venue != null) {
                b.text(" ")
                b.italic(venue)
                d.volume.orNullIfBlank()?.let { b.text(", "); b.italic(it) }
                d.issue.orNullIfBlank()?.let { b.text("($it)") }
                pages?.let { b.text(", $it") }
                b.text(".")
            }

            WorkKind.Conference, WorkKind.Chapter -> {
                if (venue != null) {
                    b.text(" In ")
                    b.italic(venue)
                    pages?.let { b.text(if (isSinglePage(d.firstPage, d.lastPage)) " (p. $it)" else " (pp. $it)") }
                    b.text(".")
                }
                publisher?.let { b.text(" $it."); }
            }

            WorkKind.Book -> publisher?.let { b.text(" $it.") }
            WorkKind.Thesis -> Unit
            WorkKind.Report -> (publisher ?: venue)?.let { b.text(" $it.") }
            WorkKind.Preprint, WorkKind.Other -> venue?.let { b.text(" $it.") }
        }
    }

    private fun link(paper: Paper): String? =
        paper.doi.orNullIfBlank()?.let { "https://doi.org/${doiOf(it)}" } ?: paper.openAccessPdfUrl.orNullIfBlank()
}
```
- [ ] **Step 5:** `./gradlew :core:citation:test` → PASS. Where an expected string disagrees with the code, check it against the spec's §3.3 table and APA 7 rules and fix whichever is wrong; record any change to an expectation in the ledger as a Ruling.
- [ ] **Step 6:** commit `feat(android): APA 7 references`.

---

### Task 4: IEEE

**Files:**
- Create: `android/core/citation/src/main/kotlin/com/etatech/hashiya/core/citation/Ieee.kt`
- Test: `android/core/citation/src/test/kotlin/com/etatech/hashiya/core/citation/IeeeTest.kt`

**Interfaces:**
- Consumes: Tasks 2–3 helpers.
- Produces: `object Ieee { fun format(paper: Paper): StyledCitation; fun list(papers: List<Paper>): List<StyledCitation>; internal fun authors(names: List<String>): List<Run> }`.

- [ ] **Step 1: Failing tests** — `IeeeTest.kt`:
```kotlin
package com.etatech.hashiya.core.citation

import org.junit.Assert.assertEquals
import org.junit.Test

class IeeeTest {
    private fun ieee(p: com.etatech.hashiya.core.model.Paper) = Ieee.format(p)

    @Test
    fun journalArticle() = assertEquals(
        listOf(Run("A. Vaswani and N. Shazeer, \"Attention is all you need,\" "), Run("Nature", italic = true), Run(", vol. 521, no. 7553, pp. 436–444, 2017, doi: 10.1038/nature14539.")),
        ieee(paper(venue = "Nature", doi = "https://doi.org/10.1038/nature14539", volume = "521", issue = "7553", first = "436", last = "444")).runs
    )

    @Test
    fun conferencePaper() = assertEquals(
        "A. Vaswani and N. Shazeer, \"Attention is all you need,\" in Advances in Neural Information Processing Systems, 2017, pp. 5998–6008, doi: 10.5555/3295222.3295349.",
        ieee(paper(source = "conference", first = "5998", last = "6008")).plain
    )

    @Test
    fun bookChapter() = assertEquals(
        "A. Vaswani and N. Shazeer, \"Attention is all you need,\" in Deep Learning. MIT Press, 2017, p. 12, doi: 10.5555/3295222.3295349.",
        ieee(paper(work = "book-chapter", venue = "Deep Learning", publisher = "MIT Press", first = "12")).plain
    )

    @Test
    fun book() = assertEquals(
        listOf(Run("A. Vaswani and N. Shazeer, "), Run("Attention is all you need", italic = true), Run(". MIT Press, 2017, doi: 10.5555/3295222.3295349.")),
        ieee(paper(work = "book", source = null, publisher = "MIT Press")).runs
    )

    @Test
    fun thesisReportPreprintOther() {
        assertEquals("A. Vaswani and N. Shazeer, \"Attention is all you need,\" Thesis, University of Toronto, 2017.", ieee(paper(work = "dissertation", source = null, venue = "University of Toronto", doi = null)).plain)
        assertEquals("A. Vaswani and N. Shazeer, \"Attention is all you need,\" Google Research, Tech. Rep., 2017.", ieee(paper(work = "report", source = null, venue = "Google Research", doi = null)).plain)
        assertEquals("A. Vaswani and N. Shazeer, \"Attention is all you need,\" arXiv, 2017, doi: 10.48550/arXiv.1706.03762.", ieee(paper(work = "preprint", source = "repository", venue = "arXiv", doi = "10.48550/arXiv.1706.03762")).plain)
        assertEquals("A. Vaswani and N. Shazeer, \"Attention is all you need,\" Zenodo, 2017.", ieee(paper(work = "dataset", source = null, venue = "Zenodo", doi = null)).plain)
    }

    @Test
    fun noDoiUsesTheOpenAccessLink() = assertEquals(
        "A. Vaswani and N. Shazeer, \"Attention is all you need,\" Zenodo, 2017. [Online]. Available: https://example.org/a.pdf",
        ieee(paper(work = "dataset", source = null, venue = "Zenodo", doi = null, pdf = "https://example.org/a.pdf")).plain
    )

    @Test
    fun noAuthorsNoYearNoVenue() {
        assertEquals("\"Attention is all you need.\"", ieee(paper(authors = emptyList(), year = null, work = null, source = null, venue = null, doi = null)).plain)
        assertEquals("\"Untitled,\" Zenodo.", ieee(paper(title = "", authors = emptyList(), year = null, work = null, source = null, venue = "Zenodo", doi = null)).plain)
    }

    @Test
    fun authorLists() {
        fun names(n: Int) = (1..n).map { "Given$it Family$it" }
        fun text(n: Int) = Rendering.plain(StyledCitation(Ieee.authors(names(n))))
        assertEquals("G. Family1", text(1))
        assertEquals("G. Family1 and G. Family2", text(2))
        assertEquals("G. Family1, G. Family2, and G. Family3", text(3))
        assertEquals(6, text(6).split("Family").size - 1)
        assertEquals(listOf(Run("G. Family1 "), Run("et al.", italic = true)), Ieee.authors(names(7)))
        assertEquals("J.-P. Sartre and محمد عبد الله", Rendering.plain(StyledCitation(Ieee.authors(listOf("Jean-Paul Sartre", "محمد عبد الله")))))
    }

    @Test
    fun listNumbersInTheOrderGiven() = assertEquals(
        listOf("[1] \"B,\" Zenodo.", "[2] \"A,\" Zenodo."),
        Ieee.list(listOf("B", "A").map { paper(title = it, authors = emptyList(), year = null, work = null, source = null, venue = "Zenodo", doi = null) }).map { it.plain }
    )
}
```
- [ ] **Step 2:** run → FAIL to compile.
- [ ] **Step 3: Implement** `Ieee.kt`:
```kotlin
package com.etatech.hashiya.core.citation

import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.WorkKind
import com.etatech.hashiya.core.model.workKind

/** IEEE references, from the data a saved paper has (see the spec's table). */
object Ieee {
    fun format(paper: Paper): StyledCitation {
        val b = CitationBuilder()
        val d = paper.publication
        val kind = d.workKind()
        val venue = paper.venue.orNullIfBlank()
        val publisher = d.publisher.orNullIfBlank()
        val year = paper.year?.toString()
        val doi = paper.doi.orNullIfBlank()?.let { "doi: ${doiOf(it)}" }
        val pages = pageRange(d.firstPage, d.lastPage)?.let { if (isSinglePage(d.firstPage, d.lastPage)) "p. $it" else "pp. $it" }
        val title = paper.title.orNullIfBlank()

        val names = authors(paper.authors.map { it.name })
        if (names.isNotEmpty()) {
            b.runs(names)
            b.text(", ")
        }
        if (kind == WorkKind.Book) {
            b.italic(title ?: "Untitled")
            b.text(".")
            val rest = listOfNotNull(publisher ?: venue, year, doi)
            if (rest.isNotEmpty()) b.text(" " + rest.joinToString(", ") + ".")
        } else {
            val tail: List<List<Run>> = when (kind) {
                WorkKind.Article -> listOfNotNull(
                    venue?.let { listOf(Run(it, italic = true)) },
                    d.volume.orNullIfBlank()?.let { listOf(Run("vol. $it")) },
                    d.issue.orNullIfBlank()?.let { listOf(Run("no. $it")) },
                    pages?.let { listOf(Run(it)) }, year?.let { listOf(Run(it)) }, doi?.let { listOf(Run(it)) }
                )
                WorkKind.Conference -> listOfNotNull(
                    venue?.let { listOf(Run("in "), Run(it, italic = true)) },
                    year?.let { listOf(Run(it)) }, pages?.let { listOf(Run(it)) }, doi?.let { listOf(Run(it)) }
                )
                WorkKind.Chapter -> listOfNotNull(
                    venue?.let { listOf(Run("in "), Run(it, italic = true)) + (publisher?.let { p -> listOf(Run(". $p")) } ?: emptyList()) },
                    year?.let { listOf(Run(it)) }, pages?.let { listOf(Run(it)) }, doi?.let { listOf(Run(it)) }
                )
                WorkKind.Thesis -> listOfNotNull(listOf(Run("Thesis")), venue?.let { listOf(Run(it)) }, year?.let { listOf(Run(it)) }, doi?.let { listOf(Run(it)) })
                WorkKind.Report -> listOfNotNull((publisher ?: venue)?.let { listOf(Run(it)) }, listOf(Run("Tech. Rep.")), year?.let { listOf(Run(it)) }, doi?.let { listOf(Run(it)) })
                else -> listOfNotNull(venue?.let { listOf(Run(it)) }, year?.let { listOf(Run(it)) }, doi?.let { listOf(Run(it)) })
            }
            b.text("\"" + (title ?: "Untitled") + (if (tail.isEmpty()) "." else ",") + "\"")
            if (tail.isNotEmpty()) {
                b.text(" ")
                tail.forEachIndexed { i, part ->
                    if (i > 0) b.text(", ")
                    b.runs(part)
                }
                b.text(".")
            }
        }
        if (doi == null) paper.openAccessPdfUrl.orNullIfBlank()?.let { b.text(" [Online]. Available: $it") }
        return b.build()
    }

    /** Numbered [1], [2], … in the order given (the library's saved order). */
    fun list(papers: List<Paper>): List<StyledCitation> =
        papers.mapIndexed { i, p -> StyledCitation(listOf(Run("[${i + 1}] ")) + format(p).runs).merged() }

    internal fun authors(names: List<String>): List<Run> {
        val written = names.map { name -> personName(name).let { p -> p.initials?.let { "$it ${p.family}" } ?: p.family } }
        return when {
            written.isEmpty() -> emptyList()
            written.size == 1 -> listOf(Run(written[0]))
            written.size == 2 -> listOf(Run("${written[0]} and ${written[1]}"))
            written.size <= 6 -> listOf(Run(written.dropLast(1).joinToString(", ") + ", and " + written.last()))
            else -> listOf(Run("${written[0]} "), Run("et al.", italic = true))
        }
    }

    private fun StyledCitation.merged(): StyledCitation = CitationBuilder().apply { runs(this@merged.runs) }.build()
}
```
Note: the book case's "Untitled" is italic and unquoted — consistent with IEEE book titles.
- [ ] **Step 4:** run → PASS. Same rule as Task 3 for any disagreement.
- [ ] **Step 5:** commit `feat(android): IEEE references`.

---

### Task 5: The repository and the remembered style

**Files:**
- Modify: `android/core/data/build.gradle.kts` (`implementation(project(":core:citation"))`)
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/repository/CitationRepository.kt`
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/repository/RoomCitationRepository.kt`
- Modify: `android/core/datastore/src/main/java/com/etatech/hashiya/core/datastore/UserPreferencesDataSource.kt`
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/repository/UserPreferencesRepository.kt`
- Modify: `android/core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeCitationRepository.kt`, `FakeUserPreferencesRepository.kt`
- Modify (field rename `.bibtex` → `.text`): every main and test file that reads `CitationResult.bibtex` (grep `\.bibtex\b` under `android/`; at least `PaperDetailsViewModel.kt`, `LibraryViewModel.kt`, `RoomCitationRepositoryTest.kt`, `ArchiveLibraryBackupTest.kt`)
- Test: `android/core/data/src/test/java/com/etatech/hashiya/core/data/repository/RoomCitationRepositoryTest.kt` (new tests), `android/core/datastore/src/test/java/com/etatech/hashiya/core/datastore/UserPreferencesDataSourceTest.kt` (new tests)

**Interfaces:**
- Consumes: Tasks 1–4 (`CitationStyle`, `Apa`, `Ieee`, `Rendering`).
- Produces:
  - `interface CitationRepository { suspend fun entry(openAlexId: String, style: CitationStyle = CitationStyle.Bibtex): CitationResult?; suspend fun export(collectionId: Long?, style: CitationStyle = CitationStyle.Bibtex): CitationResult }`
  - `data class CitationResult(val text: String, val html: String? = null, val rtf: String? = null, val complete: Boolean)` — `text` is BibTeX or the plain citation(s); `html` for APA/IEEE entries; `rtf` for APA/IEEE exports.
  - `UserPreferencesDataSource.citationStyleId: Flow<String?>`, `suspend fun setCitationStyleId(id: String)` (key `stringPreferencesKey("citation_style")`).
  - `UserPreferencesRepository.citationStyle: Flow<CitationStyle>`, `suspend fun setCitationStyle(style: CitationStyle)`.
  - `FakeCitationRepository`: `entries` stays `Map<String, String>` (plain text), new `val styles = mutableListOf<CitationStyle>()` recording every call's style; `entry` returns `CitationResult(text, html = if (style == Bibtex) null else "<i>$text</i>", complete = complete)`; `export` returns `CitationResult(exportText, rtf = if (style == Bibtex) null else "{\\rtf1 $exportText}", complete = complete)`.
  - `FakeUserPreferencesRepository.citationStyle` (`MutableStateFlow(CitationStyle.Apa)`).

- [ ] **Step 1: Failing tests.** In `UserPreferencesDataSourceTest.kt` (follow its `dataSource()` helper):
```kotlin
    @Test
    fun noCitationStyleByDefault() = runTest { assertEquals(null, dataSource().citationStyleId.first()) }

    @Test
    fun storesTheCitationStyle() = runTest {
        val source = dataSource()
        source.setCitationStyleId("ieee")
        assertEquals("ieee", source.citationStyleId.first())
    }
```
In `RoomCitationRepositoryTest.kt` (follow its existing setup: save papers through the DAO the way `exportCoversTheWholeCollectionRegardlessOfStatus` does, using a fake lookup that has details):
```kotlin
    @Test
    fun anApaEntryHasPlainAndHtmlText() = runTest {
        // save one paper as the existing entry tests do, then:
        val result = repository().entry(savedId, CitationStyle.Apa)!!
        assertEquals(true, result.text.contains("(20"))   // APA's "(year)."
        assertEquals(true, result.html!!.contains("<i>"))
        assertEquals(null, result.rtf)
    }

    @Test
    fun anIeeeExportNumbersInSavedOrderAndHasRtf() = runTest {
        // save two papers in a known order, as keysAreAssignedInSavedOrderAndNeverChange does, then:
        val result = repository().export(null, CitationStyle.Ieee)
        assertEquals(true, result.text.startsWith("[1] "))
        assertEquals(true, result.text.contains("\n\n[2] "))
        assertEquals(true, result.rtf!!.startsWith("{\\rtf1"))
    }

    @Test
    fun anEmptyCollectionExportsAnEmptyRtfDocument() = runTest {
        // create an empty collection as emptyCollectionExportsAnEmptyCompleteFile does, then:
        val result = repository().export(id, CitationStyle.Apa)
        assertEquals("", result.text)
        assertEquals("{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs24\n}", result.rtf)
        assertEquals(true, result.complete)
    }

    @Test
    fun bibtexIsUnchangedAndHasNoRichForms() = runTest {
        // same fixture as entryRefetchesOnceThenUsesStoredDetailsAndKey:
        val result = repository().entry(savedId)!!
        assertEquals(null, result.html)
        assertEquals(null, result.rtf)
    }
```
Replace the `// …` comment lines with the exact setup code from the named existing tests in the same file (copy their fixture lines; they already save papers and collections through the DAO).
- [ ] **Step 2:** `./gradlew :core:datastore:testDebugUnitTest :core:data:testDebugUnitTest` → FAIL to compile.
- [ ] **Step 3: Implement.**
  - `UserPreferencesDataSource`: `val citationStyleId: Flow<String?> = dataStore.data.map { it[CITATION_STYLE] }`; `suspend fun setCitationStyleId(id: String) { dataStore.edit { it[CITATION_STYLE] = id } }`; `val CITATION_STYLE = stringPreferencesKey("citation_style")` in the companion.
  - `UserPreferencesRepository`: `/** The style Copy citation and Export use first; APA until one is chosen. */ val citationStyle: Flow<CitationStyle>` and `suspend fun setCitationStyle(style: CitationStyle)`; the DataStore implementation: `override val citationStyle = dataSource.citationStyleId.map(CitationStyle::fromId)` and `override suspend fun setCitationStyle(style: CitationStyle) = dataSource.setCitationStyleId(style.id)`.
  - `CitationRepository.kt`: the interface and `CitationResult` from **Interfaces** above (update the KDoc: "One saved paper's citation in [style]…").
  - `RoomCitationRepository`: `entry(openAlexId, style)` — unchanged up to `val row = …`; then
```kotlin
        val paper = row.asPaper()
        val complete = row.hasDetails()
        return when (style) {
            CitationStyle.Bibtex -> row.citable()?.let { CitationResult(BibTeX.entry(it), complete = complete) }
            CitationStyle.Apa, CitationStyle.Ieee -> {
                val citation = if (style == CitationStyle.Apa) Apa.format(paper) else Ieee.format(paper)
                CitationResult(citation.plain, html = Rendering.html(citation), complete = complete)
            }
        }
```
    and `export(collectionId, style)` — unchanged up to `val rows = …`; then
```kotlin
        val complete = rows.all { it.hasDetails() }
        return when (style) {
            CitationStyle.Bibtex -> CitationResult(BibTeX.file(rows.mapNotNull { it.citable() }), complete = complete)
            CitationStyle.Apa, CitationStyle.Ieee -> {
                val papers = rows.map { it.asPaper() }
                val list = if (style == CitationStyle.Apa) Apa.list(papers) else Ieee.list(papers)
                CitationResult(
                    text = list.joinToString("\n\n") { it.plain },
                    rtf = Rendering.rtf(list, hangingIndent = style == CitationStyle.Apa),
                    complete = complete
                )
            }
        }
```
  - Rename `CitationResult.bibtex` → `.text` at every reader (grep). Existing constructor calls `CitationResult("…", complete = …)` keep compiling; positional `CitationResult(x, y)` calls must become `CitationResult(x, complete = y)`.
  - Fakes per **Interfaces**.
- [ ] **Step 4:** `./gradlew :core:datastore:testDebugUnitTest :core:data:testDebugUnitTest :core:bibtex:test :core:citation:test` → PASS; `./gradlew assembleDebug` compiles (feature modules only renamed the field).
- [ ] **Step 5:** lint + commit `feat(android): citations in APA and IEEE from the repository, and the remembered style`.

---

### Task 6: Details — Copy citation

**Files:**
- Modify: `android/feature/paperdetails/src/main/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsViewModel.kt` (inject `UserPreferencesRepository`; `citationStyle`; `onCopyCitation(style)`)
- Modify: `PaperDetailsUiState.kt` (`CopiedBibTeX` → `CopiedCitation(style, text, html, complete)`; messages)
- Modify: `CopyConfirmation.kt`, `PaperDetailsActions.kt`, `PaperDetailsScreen.kt`
- Modify: `res/values/strings.xml`, `res/values-ar/strings.xml`
- Modify tests constructing `PaperDetailsViewModel` (grep `PaperDetailsViewModel(` under `feature/paperdetails/src/test`) to pass `FakeUserPreferencesRepository()` after `citationRepository` — add `private val preferences = FakeUserPreferencesRepository()` where a test needs to read it.
- Test: `PaperDetailsCollectionsViewModelTest.kt`, `PaperDetailsCollectionsContentTest.kt`, `CopyConfirmationTest.kt`

**Interfaces:**
- Consumes: `CitationStyle`, `CitationRepository.entry(id, style)`, `CitationResult(text, html, …)`, `UserPreferencesRepository.citationStyle/setCitationStyle`.
- Produces: `PaperDetailsViewModel.citationStyle: StateFlow<CitationStyle>`; `fun onCopyCitation(style: CitationStyle)`; `data class CopiedCitation(val style: CitationStyle, val text: String, val html: String?, val complete: Boolean)`; `PaperDetailsMessage` values `ApaCopied`, `IeeeCopied`, `BibTeXCopied`, `CitationIncomplete` (renamed from `BibTeXIncomplete`), `CopyFailed`; `internal fun copyConfirmation(style: CitationStyle, complete: Boolean, sdkInt: Int): PaperDetailsMessage?`; `internal fun orderedStyles(remembered: CitationStyle): List<CitationStyle>` (remembered first, then the rest in `Apa, Ieee, Bibtex` order); `PaperDetailsActions.onCopyCitation: (CitationStyle) -> Unit`.

- [ ] **Step 1: Strings** (`values` / `values-ar`): replace `details_copy_bibtex`, `details_bibtex_copied`, `details_bibtex_incomplete`, `details_copy_failed` with:

| Key | en | ar |
|---|---|---|
| `details_copy_apa` | Copy APA 7 citation | نسخ استشهاد APA 7 |
| `details_copy_ieee` | Copy IEEE citation | نسخ استشهاد IEEE |
| `details_copy_bibtex` | Copy BibTeX | نسخ BibTeX |
| `details_apa_copied` | APA citation copied | تم نسخ استشهاد APA |
| `details_ieee_copied` | IEEE citation copied | تم نسخ استشهاد IEEE |
| `details_bibtex_copied` | BibTeX copied | تم نسخ BibTeX |
| `details_citation_incomplete` | Some details may be missing. Copy again when you're online. | (the current Arabic of `details_bibtex_incomplete`) |
| `details_copy_failed` | Couldn't copy the citation | تعذّر نسخ الاستشهاد |

- [ ] **Step 2: Failing tests.**
  - `CopyConfirmationTest.kt`: rewrite for the new signature — incomplete → `CitationIncomplete` for every style; below SDK 33 → `ApaCopied`/`IeeeCopied`/`BibTeXCopied` by style; 33+ → null; and `orderedStyles(CitationStyle.Ieee) == listOf(Ieee, Apa, Bibtex)`, `orderedStyles(Apa) == listOf(Apa, Ieee, Bibtex)`.
  - `PaperDetailsCollectionsViewModelTest.kt`: `copyBibTeXHandsTheEntryToTheScreen` becomes `copyCitationHandsTheEntryAndRemembersTheStyle`:
```kotlin
        viewModel.onCopyCitation(CitationStyle.Ieee)
        advanceUntilIdle()
        assertEquals(CopiedCitation(CitationStyle.Ieee, "<the entries text>", "<i><the entries text></i>", complete = true), viewModel.copied.value)
        assertEquals(CitationStyle.Ieee, preferences.citationStyle.first())
        assertEquals(listOf(CitationStyle.Ieee), citations.styles)
```
    (keep the test's existing fixture text; BibTeX copies assert `html = null`). Update the incomplete and failure tests to `onCopyCitation(CitationStyle.Bibtex)`.
  - `PaperDetailsCollectionsContentTest.kt`: `overflowOffersCopyBibTeXAboveRemove` becomes `overflowOffersTheThreeCopiesRememberedFirstAboveRemove` — open the overflow with `citationStyle = Ieee`, assert nodes "Copy IEEE citation", "Copy APA 7 citation", "Copy BibTeX", "Remove" exist and that clicking "Copy APA 7 citation" records `"copy:Apa"`; update the snackbar texts to the new strings.
- [ ] **Step 3:** run `./gradlew :feature:paperdetails:testDebugUnitTest -Proborazzi.test.verify=false` → FAIL to compile.
- [ ] **Step 4: Implement.**
  - ViewModel: constructor gains `private val preferencesRepository: UserPreferencesRepository` after `citationRepository`;
```kotlin
    val citationStyle: StateFlow<CitationStyle> = preferencesRepository.citationStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CitationStyle.Apa)

    fun onCopyCitation(style: CitationStyle) {
        viewModelScope.launch {
            preferencesRepository.setCitationStyle(style)
            try {
                citationRepository.entry(openAlexId, style)?.let { _copied.value = CopiedCitation(style, it.text, it.html, it.complete) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = PaperDetailsMessage.CopyFailed
            }
        }
    }
```
    (remove `onCopyBibTeX`; `_copied` becomes `MutableStateFlow<CopiedCitation?>`).
  - `CopyConfirmation.kt`:
```kotlin
internal fun copyConfirmation(style: CitationStyle, complete: Boolean, sdkInt: Int): PaperDetailsMessage? = when {
    !complete -> PaperDetailsMessage.CitationIncomplete
    sdkInt >= SYSTEM_CLIPBOARD_CONFIRMATION_SDK -> null
    style == CitationStyle.Apa -> PaperDetailsMessage.ApaCopied
    style == CitationStyle.Ieee -> PaperDetailsMessage.IeeeCopied
    else -> PaperDetailsMessage.BibTeXCopied
}

/** The remembered style first, then the others in the menu's usual order. */
internal fun orderedStyles(remembered: CitationStyle): List<CitationStyle> =
    listOf(remembered) + listOf(CitationStyle.Apa, CitationStyle.Ieee, CitationStyle.Bibtex).filter { it != remembered }
```
  - Screen: the clipboard effect writes `ClipData.newHtmlText(label, entry.text, entry.html)` when `entry.html != null`, else `ClipData.newPlainText("BibTeX", entry.text)`; label `"Citation"`; confirmation `copyConfirmation(entry.style, entry.complete, Build.VERSION.SDK_INT)`. `OverflowMenu(citationStyle, onCopyCitation, onRemove)` shows one `DropdownMenuItem` per `orderedStyles(citationStyle)` (text from `details_copy_apa` / `_ieee` / `_bibtex`, the copy icon on each), then Remove. Collect `viewModel.citationStyle` with `collectAsStateWithLifecycle()` and pass it down; `PaperDetailsActions.onCopyCitation: (CitationStyle) -> Unit = {}` replaces `onCopyBibTeX`. Snackbar texts map the new message values to the new strings.
- [ ] **Step 5:** `./gradlew :feature:paperdetails:testDebugUnitTest -Proborazzi.test.verify=false :app:hiltJavaCompileDebug` → PASS.
- [ ] **Step 6:** lint + commit `feat(android): copy a paper's citation in APA, IEEE or BibTeX`.

---

### Task 7: Library — export a reference list

**Files:**
- Modify: `android/core/analytics/src/main/kotlin/com/etatech/hashiya/core/analytics/ClosedValues.kt` (`ExportFormat` gains `Apa("apa")`, `Ieee("ieee")`)
- Modify: `android/core/analytics/src/test/kotlin/com/etatech/hashiya/core/analytics/AnalyticsEventTest.kt` (one line per new value)
- Modify: `android/feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryViewModel.kt`, `LibraryUiState.kt`, `LibraryActions.kt`, `LibraryScreen.kt`
- Modify: `android/feature/library/src/main/java/com/etatech/hashiya/feature/library/export/BibExportFile.kt` → rename to `ExportFile.kt`; `export/BibFileName.kt` → `export/ExportFileName.kt`
- Modify: `res/values/strings.xml`, `res/values-ar/strings.xml`
- Modify tests constructing `LibraryViewModel` (grep `LibraryViewModel(` under `feature/library/src/test`) to pass `FakeUserPreferencesRepository()` after `citationRepository`.
- Test: `LibraryCollectionsViewModelTest.kt`, `LibraryCollectionsContentTest.kt`, `export/ExportFileTest.kt` (renamed from `BibExportFileTest.kt`), `export/ExportFileNameTest.kt` (renamed from `BibFileNameTest.kt`)

**Interfaces:**
- Consumes: `CitationRepository.export(collectionId, style)`, `CitationResult(text, rtf, …)`, `UserPreferencesRepository.citationStyle/setCitationStyle`, `orderedStyles` — define a Library-local copy `internal fun exportStyles(remembered: CitationStyle): List<CitationStyle>` (same body as Task 6's `orderedStyles`; the modules don't depend on each other).
- Produces: `data class ReferenceExport(val fileName: String, val content: String, val mimeType: String, val complete: Boolean, val style: CitationStyle)` (replaces `BibExport`); `LibraryViewModel.citationStyle: StateFlow<CitationStyle>`; `fun onExport(style: CitationStyle)`; `internal fun exportFileName(collectionName: String?, style: CitationStyle): String`; `internal const val BIB_MIME_TYPE = "text/x-bibtex"`, `RTF_MIME_TYPE = "application/rtf"`; `writeExportFileTo(directory, export)`, `writeExportFile(context, export)`, `exportShareIntent(uri, export)`.

- [ ] **Step 1: Strings:** `library_export` "Export references" / «تصدير المراجع» (content description of the button); `library_export_bibtex` "BibTeX (.bib)" / «BibTeX (&#x200E;.bib)»; `library_export_apa` "APA 7 (.rtf)" / «APA 7 (&#x200E;.rtf)»; `library_export_ieee` "IEEE (.rtf)" / «IEEE (&#x200E;.rtf)». Remove `library_export_bib`.
- [ ] **Step 2: Failing tests.**
  - `ExportFileNameTest.kt`:
```kotlin
    @Test
    fun namesPerStyle() {
        assertEquals("hashiya-library.bib", exportFileName(null, CitationStyle.Bibtex))
        assertEquals("hashiya-library – APA.rtf", exportFileName(null, CitationStyle.Apa))
        assertEquals("Thesis – IEEE.rtf", exportFileName("Thesis", CitationStyle.Ieee))
        assertEquals("a-b – APA.rtf", exportFileName("a/b", CitationStyle.Apa))
        assertEquals("collection – APA.rtf", exportFileName("  ", CitationStyle.Apa))
    }
```
    (keep the old `BibFileNameTest` cases, ported to `exportFileName(…, CitationStyle.Bibtex)`).
  - `ExportFileTest.kt`: port `BibExportFileTest` to `ReferenceExport`, and add: an RTF export is written with its content and `exportShareIntent` has type `application/rtf`.
  - `LibraryCollectionsViewModelTest.kt`: the export tests call `onExport(CitationStyle.Bibtex)` and assert `ReferenceExport("Thesis.bib", citations.exportText, BIB_MIME_TYPE, complete = true, style = CitationStyle.Bibtex)`; add `anApaExportWritesTheRtfAndRemembersTheStyle` asserting `ReferenceExport("Thesis – APA.rtf", "{\\rtf1 ${citations.exportText}}", RTF_MIME_TYPE, true, CitationStyle.Apa)`, `preferences.citationStyle.first() == Apa`, and after `onExportShared()` the analytics event `Export(ExportFormat.Apa, withPdfs = false)`.
  - `LibraryCollectionsContentTest.kt`: the button's content description is now "Export references"; clicking it shows "BibTeX (.bib)", "APA 7 (.rtf)", "IEEE (.rtf)" (remembered first); choosing "IEEE (.rtf)" records `"export:Ieee"`.
  - `AnalyticsEventTest.kt`: `Export(ExportFormat.Apa, false)` → `format=apa`, and the same for `ieee`.
- [ ] **Step 3:** run → FAIL to compile.
- [ ] **Step 4: Implement.**
  - `ExportFormat`: `enum class ExportFormat(val id: String) { Bibtex("bibtex"), Apa("apa"), Ieee("ieee"), Backup("backup") }`.
  - ViewModel: inject `private val preferencesRepository: UserPreferencesRepository` after `citationRepository`; `citationStyle` as in Task 6; `onExport(style: CitationStyle)`:
```kotlin
    fun onExport(style: CitationStyle) {
        if (exporting.value) return
        exporting.value = true
        val collectionId = selectedId.value
        val name = collectionId?.let { id -> collections.value.firstOrNull { it.id == id }?.name.orEmpty() }
        viewModelScope.launch {
            preferencesRepository.setCitationStyle(style)
            try {
                val result = citationRepository.export(collectionId, style)
                _exportReady.value = ReferenceExport(
                    fileName = exportFileName(name, style),
                    content = result.rtf ?: result.text,
                    mimeType = if (style == CitationStyle.Bibtex) BIB_MIME_TYPE else RTF_MIME_TYPE,
                    complete = result.complete,
                    style = style
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                exporting.value = false
                _message.value = LibraryMessage.ExportFailed
            }
        }
    }
```
    `onExportShared()` logs `AnalyticsEvent.Export(shared.style.exportFormat(), withPdfs = false)` with a private `CitationStyle.exportFormat()` mapping `Bibtex→Bibtex`, `Apa→Apa`, `Ieee→Ieee`.
  - `ExportFileName.kt`: `exportFileName(collectionName, style)` — base = `"hashiya-library"` when null, else the sanitised name (`collection` when blank, same regex as today); suffix `.bib` for BibTeX, ` – APA.rtf` / ` – IEEE.rtf` otherwise.
  - `ExportFile.kt`: the old file with `BibExport` → `ReferenceExport`, `export.bibtex` → `export.content`, and the share intent's `type = export.mimeType` (signature `exportShareIntent(uri: Uri, export: ReferenceExport)`).
  - Screen: the export `IconButton` (content description `library_export`) toggles a `DropdownMenu` listing `exportStyles(citationStyle)` with the three labels; choosing one calls `actions.onExport(style)` and closes it; the progress indicator is unchanged. `LibraryActions.onExport: (CitationStyle) -> Unit = {}`; the share `LaunchedEffect` uses `writeExportFile` / `exportShareIntent(uri, export)`.
- [ ] **Step 5:** `./gradlew :core:analytics:test :feature:library:testDebugUnitTest -Proborazzi.test.verify=false :app:hiltJavaCompileDebug lintDebug` → PASS.
- [ ] **Step 6:** commit `feat(android): export a reference list in APA, IEEE or BibTeX`.

---

### Task 8: Docs, changelog, spec and screenshots

- [ ] `CHANGELOG.md` `[Unreleased]` → Added: "- **Android: APA 7 and IEEE citations.** A paper's ⋮ menu copies its citation in APA 7, IEEE or BibTeX — APA and IEEE paste with their italics into Word, Pages or Google Docs — and the Library's export saves a whole collection as an APA 7 or IEEE reference list (.rtf) as well as BibTeX. The style you used last comes first."
- [ ] Spec: §3 "IEEE … in the order the papers were added" → "in the order they were saved to the library (inside a collection too)"; §4.1 → "Details' ⋮ menu lists Copy APA 7 citation, Copy IEEE citation and Copy BibTeX, the remembered style first"; §8 mark the Android PR delivered.
- [ ] Commit `docs: APA 7 and IEEE citations on Android`.
- [ ] Controller, after pushing: `scripts/record-screenshots-on-linux.sh`; look at any changed Library and Details captures in English and Arabic before committing them.
