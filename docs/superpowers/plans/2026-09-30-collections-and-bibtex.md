# Collections and BibTeX Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Group saved papers into collections, filter the Library by collection, export a collection (or the whole library) as a `.bib` file through the share sheet, and copy one paper's BibTeX from Details.

**Architecture:** A new plain-JVM module `core/bibtex` turns `Paper`s into BibTeX and generates cite keys. Room schema v4 adds bibliographic columns and a stored cite key to `papers`, plus `collections` and `collection_papers`. `core/data` gains `CollectionsRepository` and `CitationRepository`, which refetches missing details from OpenAlex once, assigns stored keys, then builds the text. Library gets a collection selector, swipe-from-collection and Export; Details gets a Collections row, a checklist sheet and Copy BibTeX.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room (KSP) with FTS4, Hilt, Retrofit + kotlinx.serialization, coroutines/Flow, JUnit 4, Robolectric, Turbine, Roborazzi.

**Spec:** `docs/superpowers/specs/2026-09-30-collections-and-bibtex-design.md`

## Global Constraints

- Android only. No iOS changes.
- Commits are authored `Fady <fady.fouad.a@gmail.com>`. No `Co-Authored-By`, no AI credits anywhere (commits, code, docs).
- Module dependency rules: `core/bibtex` depends only on `core/model`; features never depend on `core/database` or `core/network`.
- Schema version 4. The migration only adds columns, tables and indices; nothing is rewritten and `paper_search` is untouched. No destructive fallback.
- Every new user-facing string exists in `values/strings.xml` and `values-ar/strings.xml`, with the exact text from spec §10 (plus `details_copy_failed`, added to the spec in Task 13).
- Apostrophes in string resources are escaped (`Couldn\'t`).
- Collection names: trimmed, 1–60 characters, unique by `trim().lowercase(Locale.ROOT)`.
- Cite keys: `[a-z0-9]` only, always start with a letter, stored once in `papers.cite_key` (unique), never changed.
- Refetches: at most 4 concurrent OpenAlex requests; a failure never stops an export or a copy.
- Export covers the whole collection or library, ignoring the search text and status chip.
- Kotlin style: ktlint `android_studio`, max line length 140 (`./gradlew spotlessApply` before each commit).
- Tests: TDD, hand-written fakes, Robolectric for Android; screenshot baselines are recorded on CI Linux only (`bash scripts/record-screenshots-on-linux.sh`), never locally.
- The model type is named `PaperCollection` (the spec's `Collection`), so it never shadows `kotlin.collections.Collection`.

## Review Focus

1. **Restoring a removed paper whose cite key was taken meanwhile** must still restore the paper (without the key), not silently fail as "already saved". Test in Task 5.
2. **A collection deleted while the Library shows it** (from Details, or by Undo timing) must drop the Library back to All papers, not show an empty collection forever. Test in Task 11.
3. **Titles, names and venues with LaTeX specials** (`&`, `%`, `_`, `{`, `\`, `~`, `^`) must produce a `.bib` that compiles; DOIs with `_` must not be escaped. Tests in Task 3.
4. **Offline export of a pre-v4 library** must still share a file and must leave `details_fetched = 0` so the next online export fills volume and pages. Test in Task 8.
5. **Two collections whose names differ only by case or surrounding spaces** (`"Thesis"`, `" thesis "`) must be rejected as the same name, on create and on rename. Tests in Tasks 5 (DAO), 7 (repository) and 11 (dialog).

---

## File structure

**New module `core/bibtex`** (`com.etatech.hashiya.core.bibtex`):
- `CiteKeys.kt`: base key, ASCII folding, suffixes, `assign`.
- `BibTeX.kt`: `CitablePaper`, `BibTeX.entry`, `BibTeX.file`.
- `EntryType.kt`: entry-type rules.
- `LatexText.kt`: escaping, whitespace cleanup, capital protection.
- Tests: `CiteKeysTest.kt`, `BibTeXTest.kt`, `EntryTypeTest.kt`, `LatexTextTest.kt`.

**`core/model`:** `PublicationDetails.kt` (new), `PaperCollection.kt` (new), `Paper.kt` (gains `publication`).

**`core/network`:** `OpenAlexApi.kt` (`WORK_FIELDS`), `model/NetworkWorks.kt` (type, biblio, source type, publisher).

**`core/database`:**
- `model/PaperEntity.kt` (new columns), `model/CollectionEntity.kt`, `model/CollectionPaperEntity.kt`, `model/CollectionWithCount.kt` (new), `model/DeletedPaper.kt` (links).
- `dao/CollectionDao.kt`, `dao/CitationDao.kt` (new), `dao/PaperDao.kt` (collection filter, links on delete/restore, key-safe restore).
- `migration/Migrations.kt` (`MIGRATION_3_4`), `HashiyaDatabase.kt` (v4), `di/DatabaseModule.kt`, `schemas/…/4.json`.

**`core/data`:**
- `mapping/NetworkWorkMapping.kt`, `mapping/PaperEntityMapping.kt` (publication).
- `repository/LibraryRepository.kt`, `RoomLibraryRepository.kt` (collection filter, `RemovedPaper` fields).
- `repository/CollectionsRepository.kt`, `RoomCollectionsRepository.kt` (new).
- `repository/CitationRepository.kt`, `RoomCitationRepository.kt` (new).
- `di/DataModule.kt`.

**`core/testing`:** `FakeLibraryRepository.kt` (collections), `FakeCollectionsRepository.kt`, `FakeCitationRepository.kt` (new).

**`core/designsystem`:** `component/CollectionNameDialog.kt` (new), `icon/HashiyaIcons.kt`, strings.

**`feature/library`:** `LibraryViewModel.kt`, `LibraryUiState.kt`, `LibraryActions.kt`, `LibraryScreen.kt`, `components/CollectionSelectorSheet.kt` (new), `export/BibExportFile.kt` (new), `AndroidManifest.xml` + `res/xml/bib_export_paths.xml` (new), strings, tests.

**`feature/paperdetails`:** `PaperDetailsViewModel.kt`, `PaperDetailsUiState.kt`, `PaperDetailsActions.kt`, `PaperDetailsScreen.kt`, `CollectionsRow.kt` + `CollectionChecklistSheet.kt` (new), `CopyConfirmation.kt` (new), strings, tests.

**Other:** `settings.gradle.kts`, `.github/workflows/ci.yml`, `app/src/test/…/HashiyaAppNavigationTest.kt`, `README.md`, the spec.

---
### Task 1: Model types and the `core/bibtex` module with cite keys

**Files:**
- Create: `core/model/src/main/kotlin/com/etatech/hashiya/core/model/PublicationDetails.kt`
- Create: `core/model/src/main/kotlin/com/etatech/hashiya/core/model/PaperCollection.kt`
- Modify: `core/model/src/main/kotlin/com/etatech/hashiya/core/model/Paper.kt`
- Create: `core/model/src/test/kotlin/com/etatech/hashiya/core/model/PaperCollectionTest.kt`
- Create: `core/bibtex/build.gradle.kts`
- Modify: `settings.gradle.kts`, `.github/workflows/ci.yml`
- Create: `core/bibtex/src/main/kotlin/com/etatech/hashiya/core/bibtex/CiteKeys.kt`
- Test: `core/bibtex/src/test/kotlin/com/etatech/hashiya/core/bibtex/CiteKeysTest.kt`

**Interfaces:**
- Produces:
  - `data class PublicationDetails(workType: String? = null, sourceType: String? = null, publisher: String? = null, volume: String? = null, issue: String? = null, firstPage: String? = null, lastPage: String? = null)`
  - `Paper.publication: PublicationDetails = PublicationDetails()` (last constructor parameter)
  - `data class PaperCollection(val id: Long, val name: String, val paperCount: Int)`
  - `const val COLLECTION_NAME_MAX_LENGTH = 60`, `fun isValidCollectionName(name: String): Boolean`, `fun collectionNameKey(name: String): String`
  - `object CiteKeys { fun base(paper: Paper): String; fun assign(papers: List<Paper>, taken: Set<String>): List<String> }`
  - `internal fun asciiFold(text: String): String`, `internal fun keySuffix(n: Int): String`

- [ ] **Step 1: Write the failing model test**

```kotlin
package com.etatech.hashiya.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperCollectionTest {
    @Test
    fun namesAreTrimmedAndLimitedTo60Characters() {
        assertTrue(isValidCollectionName("Chapter 2"))
        assertTrue(isValidCollectionName("  x  "))
        assertTrue(isValidCollectionName("a".repeat(60)))
        assertFalse(isValidCollectionName("a".repeat(61)))
        assertFalse(isValidCollectionName(""))
        assertFalse(isValidCollectionName("   "))
    }

    @Test
    fun nameKeyIgnoresCaseAndSurroundingSpaces() {
        assertEquals("thesis refs", collectionNameKey("  Thesis Refs "))
        assertEquals(collectionNameKey("Thesis"), collectionNameKey(" thesis "))
        assertEquals("الفصل الثاني", collectionNameKey(" الفصل الثاني "))
    }

    @Test
    fun paperHasEmptyPublicationDetailsByDefault() {
        val paper = Paper("W1", null, "T", emptyList(), null, null, null, 0, false, null)
        assertEquals(PublicationDetails(), paper.publication)
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :core:model:test --tests '*PaperCollectionTest*'`
Expected: compilation FAIL (`isValidCollectionName`, `PublicationDetails` unresolved).

- [ ] **Step 3: Add the model types**

`PublicationDetails.kt`:

```kotlin
package com.etatech.hashiya.core.model

/**
 * Bibliographic details used for citations, as OpenAlex reports them. Every field is null when the source has none.
 * The strings are kept as-is; core/bibtex interprets them, so a new OpenAlex type needs no migration.
 */
data class PublicationDetails(
    /** OpenAlex's work type, such as "article", "preprint", "book-chapter". */
    val workType: String? = null,
    /** OpenAlex's source type, such as "journal", "conference", "repository". */
    val sourceType: String? = null,
    val publisher: String? = null,
    val volume: String? = null,
    val issue: String? = null,
    val firstPage: String? = null,
    val lastPage: String? = null
)
```

`PaperCollection.kt`:

```kotlin
package com.etatech.hashiya.core.model

import java.util.Locale

/** A user-made group of saved papers. [paperCount] is how many saved papers are in it. */
data class PaperCollection(val id: Long, val name: String, val paperCount: Int)

const val COLLECTION_NAME_MAX_LENGTH = 60

/** A name is valid when, trimmed, it has 1 to [COLLECTION_NAME_MAX_LENGTH] characters. */
fun isValidCollectionName(name: String): Boolean = name.trim().length in 1..COLLECTION_NAME_MAX_LENGTH

/** Two collections may not share this key: the name trimmed and lowercased. */
fun collectionNameKey(name: String): String = name.trim().lowercase(Locale.ROOT)
```

In `Paper.kt`, add a last parameter to `Paper` (after `openAccessPdfUrl`):

```kotlin
    val openAccessPdfUrl: String?,
    val publication: PublicationDetails = PublicationDetails()
)
```

- [ ] **Step 4: Run the model tests**

Run: `./gradlew :core:model:test`
Expected: PASS.

- [ ] **Step 5: Create the `core/bibtex` module**

`core/bibtex/build.gradle.kts`:

```kotlin
plugins {
    id("hashiya.jvm.library")
}

dependencies {
    api(project(":core:model"))
}
```

In `settings.gradle.kts`, after `include(":core:model")`, add `include(":core:bibtex")`.

In `.github/workflows/ci.yml`, change the line `./gradlew spotlessCheck assembleDebug testDebugUnitTest :core:model:test lintDebug` to:

```
          ./gradlew spotlessCheck assembleDebug testDebugUnitTest :core:model:test :core:bibtex:test lintDebug
```

(keep the surrounding YAML exactly as it is; only `:core:bibtex:test` is added).

- [ ] **Step 6: Write the failing cite-key tests**

```kotlin
package com.etatech.hashiya.core.bibtex

import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import org.junit.Assert.assertEquals
import org.junit.Test

class CiteKeysTest {
    private fun paper(title: String, vararg authors: String, year: Int? = 2017) = Paper(
        openAlexId = "W1",
        doi = null,
        title = title,
        authors = authors.map { Author(it, null) },
        year = year,
        venue = null,
        abstract = null,
        citationCount = 0,
        isOpenAccess = false,
        openAccessPdfUrl = null
    )

    @Test
    fun surnameYearAndFirstMeaningfulTitleWord() {
        assertEquals("vaswani2017attention", CiteKeys.base(paper("Attention Is All You Need", "Ashish Vaswani", "Noam Shazeer")))
    }

    @Test
    fun stopWordsAreSkipped() {
        assertEquals("smith2020deep", CiteKeys.base(paper("On the Deep Nature of Things", "Jane Smith", year = 2020)))
        assertEquals("smith2020learning", CiteKeys.base(paper("Towards Using: Learning", "Jane Smith", year = 2020)))
    }

    @Test
    fun accentsAndSpecialLettersFoldToAscii() {
        assertEquals("muller2019uber", CiteKeys.base(paper("Über Netze", "Jörg Müller", year = 2019)))
        assertEquals("strasse2019grosse", CiteKeys.base(paper("Große Modelle", "Anna Straße", year = 2019)))
        assertEquals("lukasz2019aeon", CiteKeys.base(paper("Æon", "Jan Łukasz", year = 2019)))
    }

    @Test
    fun punctuationInsideWordsIsDropped() {
        assertEquals(
            "devlin2019bert",
            CiteKeys.base(paper("BERT: Pre-training of Deep Bidirectional Transformers", "Jacob Devlin", year = 2019))
        )
        assertEquals("oneill2021selfattention", CiteKeys.base(paper("Self-Attention", "Mary O'Neill", year = 2021)))
    }

    @Test
    fun missingYearIsNd() {
        assertEquals("smithndgraphs", CiteKeys.base(paper("Graphs", "Jane Smith", year = null)))
    }

    @Test
    fun nonLatinOrMissingAuthorStartsWithPaper() {
        assertEquals("paper2019", CiteKeys.base(paper("تطبيقات التعلم العميق", "محمد علي", year = 2019)))
        assertEquals("paper2019deep", CiteKeys.base(paper("Deep nets", "محمد علي", year = 2019)))
        assertEquals("paper2019deep", CiteKeys.base(paper("Deep nets", year = 2019)))
        assertEquals("papernd", CiteKeys.base(paper("", year = null)))
    }

    @Test
    fun suffixesRunAToZThenAa() {
        assertEquals("", keySuffix(0))
        assertEquals("a", keySuffix(1))
        assertEquals("z", keySuffix(26))
        assertEquals("aa", keySuffix(27))
        assertEquals("ab", keySuffix(28))
    }

    @Test
    fun assignAvoidsTakenKeysAndEachOther() {
        val same = paper("Deep nets", "Jane Smith", year = 2020)
        assertEquals(
            listOf("smith2020deepa", "smith2020deepb", "vaswani2017attention"),
            CiteKeys.assign(listOf(same, same, paper("Attention", "Ashish Vaswani")), taken = setOf("smith2020deep"))
        )
    }

    @Test
    fun assignRunsPastZ() {
        val same = paper("Deep", "Jane Smith", year = 2020)
        val taken = (0..26).map { "smith2020deep" + keySuffix(it) }.toSet()
        assertEquals(listOf("smith2020deepaa"), CiteKeys.assign(listOf(same), taken))
    }
}
```

- [ ] **Step 7: Run it to see it fail**

Run: `./gradlew :core:bibtex:test`
Expected: compilation FAIL (`CiteKeys` unresolved).

- [ ] **Step 8: Implement `CiteKeys.kt`**

```kotlin
package com.etatech.hashiya.core.bibtex

import com.etatech.hashiya.core.model.Paper
import java.text.Normalizer
import java.util.Locale

/** Google Scholar–style cite keys: surname, year, first meaningful title word, e.g. "vaswani2017attention". */
object CiteKeys {
    private val STOP_WORDS = setOf(
        "a", "an", "the", "on", "of", "in", "for", "and", "to", "with", "from", "by", "via", "is", "are", "towards", "toward",
        "using", "at"
    )
    private val WHITESPACE = Regex("""\s+""")

    /** The key before collision suffixes. Always starts with a letter: "paper" stands in for a surname with no Latin letters. */
    fun base(paper: Paper): String {
        val surname = paper.authors.firstOrNull()?.name?.trim()?.split(WHITESPACE)?.lastOrNull()?.let(::asciiFold).orEmpty()
        val year = paper.year?.toString() ?: "nd"
        val word = paper.title.trim().split(WHITESPACE).map(::asciiFold).firstOrNull { it.isNotEmpty() && it !in STOP_WORDS }.orEmpty()
        return surname.ifEmpty { "paper" } + year + word
    }

    /** Keys for [papers], in order: each its [base] or the base plus the first free suffix, avoiding [taken] and each other. */
    fun assign(papers: List<Paper>, taken: Set<String>): List<String> {
        val used = taken.toMutableSet()
        return papers.map { paper ->
            val base = base(paper)
            val key = generateSequence(0) { it + 1 }.map { base + keySuffix(it) }.first { it !in used }
            used += key
            key
        }
    }
}

private val SPECIAL_LETTERS = mapOf(
    'ß' to "ss", 'æ' to "ae", 'Æ' to "ae", 'ø' to "o", 'Ø' to "o", 'đ' to "d", 'Đ' to "d", 'ł' to "l", 'Ł' to "l", 'ı' to "i",
    'œ' to "oe", 'Œ' to "oe"
)

/** Lowercase ASCII letters and digits only: accents dropped, a few letters spelled out, everything else (Arabic too) removed. */
internal fun asciiFold(text: String): String {
    val spelled = buildString { text.forEach { c -> append(SPECIAL_LETTERS[c] ?: c) } }
    return Normalizer.normalize(spelled, Normalizer.Form.NFD).lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' }
}

/** 0 → "", 1 → "a" … 26 → "z", 27 → "aa", 28 → "ab" … */
internal fun keySuffix(n: Int): String {
    val letters = StringBuilder()
    var rest = n
    while (rest > 0) {
        rest--
        letters.append('a' + rest % 26)
        rest /= 26
    }
    return letters.reverse().toString()
}
```

- [ ] **Step 9: Run the tests**

Run: `./gradlew :core:bibtex:test`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
./gradlew spotlessApply
git add core/model core/bibtex settings.gradle.kts .github/workflows/ci.yml
git commit -m "feat: add publication details, collection names and cite keys"
```

---

### Task 2: LaTeX text and entry types

**Files:**
- Create: `core/bibtex/src/main/kotlin/com/etatech/hashiya/core/bibtex/LatexText.kt`
- Create: `core/bibtex/src/main/kotlin/com/etatech/hashiya/core/bibtex/EntryType.kt`
- Test: `core/bibtex/src/test/kotlin/com/etatech/hashiya/core/bibtex/LatexTextTest.kt`
- Test: `core/bibtex/src/test/kotlin/com/etatech/hashiya/core/bibtex/EntryTypeTest.kt`

**Interfaces:**
- Consumes: `PublicationDetails` (Task 1).
- Produces:
  - `internal fun escapeLatex(text: String): String`
  - `internal fun cleanWhitespace(text: String): String`
  - `internal fun protectCapitals(text: String): String`
  - `internal enum class EntryType(val bibName: String, val venueField: String?, val hasPublisher: Boolean) { Article, InProceedings, InCollection, Book, PhdThesis, TechReport, Misc }`
  - `internal fun entryType(details: PublicationDetails): EntryType`

- [ ] **Step 1: Write the failing tests**

`LatexTextTest.kt`:

```kotlin
package com.etatech.hashiya.core.bibtex

import org.junit.Assert.assertEquals
import org.junit.Test

class LatexTextTest {
    @Test
    fun escapesEverySpecialCharacter() {
        assertEquals("R\\&D 50\\% \\$5 \\#1 a\\_b \\textbraceleft{}x", escapeLatex("R&D 50% $5 #1 a_b {x"))
        assertEquals("a\\textasciitilde{}b\\textasciicircum{}c", escapeLatex("a~b^c"))
        assertEquals("C:\\textbackslash{}dir", escapeLatex("C:\\dir"))
    }

    @Test
    fun keepsUnicode() {
        assertEquals("Jörg Müller · تعلم", escapeLatex("Jörg Müller · تعلم"))
    }

    @Test
    fun collapsesWhitespace() {
        assertEquals("Deep learning for graphs", cleanWhitespace("  Deep\nlearning \t for   graphs "))
    }

    @Test
    fun protectsWordsWithInnerCapitals() {
        assertEquals("{BERT:} Pre-training of Deep Models", protectCapitals("BERT: Pre-training of Deep Models"))
        assertEquals("{ImageNet} and {COVID-19} on an {iPhone}", protectCapitals("ImageNet and COVID-19 on an iPhone"))
        assertEquals("The deep A", protectCapitals("The deep A"))
        assertEquals("تعلم {GPU}", protectCapitals("تعلم GPU"))
    }
}
```

`EntryTypeTest.kt`:

```kotlin
package com.etatech.hashiya.core.bibtex

import com.etatech.hashiya.core.model.PublicationDetails
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryTypeTest {
    private fun type(work: String?, source: String?) = entryType(PublicationDetails(workType = work, sourceType = source))

    @Test
    fun followsTheSpecTableInOrder() {
        assertEquals(EntryType.InProceedings, type("article", "conference"))
        assertEquals(EntryType.InProceedings, type("preprint", "conference"))
        assertEquals(EntryType.InCollection, type("book-chapter", "book series"))
        assertEquals(EntryType.Book, type("book", null))
        assertEquals(EntryType.PhdThesis, type("dissertation", "repository"))
        assertEquals(EntryType.TechReport, type("report", null))
        assertEquals(EntryType.Misc, type("preprint", "journal"))
        assertEquals(EntryType.Misc, type("article", "repository"))
        assertEquals(EntryType.Article, type("article", "journal"))
        assertEquals(EntryType.Article, type("review", "journal"))
        assertEquals(EntryType.Article, type("letter", "journal"))
        assertEquals(EntryType.Article, type("editorial", "journal"))
    }

    @Test
    fun anythingElseIsMisc() {
        assertEquals(EntryType.Misc, type("article", null))
        assertEquals(EntryType.Misc, type("dataset", "repository"))
        assertEquals(EntryType.Misc, type(null, null))
        assertEquals(EntryType.Misc, type("erratum", "journal"))
    }

    @Test
    fun ignoresCase() {
        assertEquals(EntryType.Article, type("Article", "Journal"))
    }

    @Test
    fun venueFieldsAndPublisher() {
        assertEquals("journal", EntryType.Article.venueField)
        assertEquals("booktitle", EntryType.InProceedings.venueField)
        assertEquals("booktitle", EntryType.InCollection.venueField)
        assertEquals(null, EntryType.Book.venueField)
        assertEquals("school", EntryType.PhdThesis.venueField)
        assertEquals("institution", EntryType.TechReport.venueField)
        assertEquals("howpublished", EntryType.Misc.venueField)
        assertEquals(
            setOf(EntryType.Book, EntryType.InCollection, EntryType.TechReport, EntryType.Misc),
            EntryType.entries.filter { it.hasPublisher }.toSet()
        )
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :core:bibtex:test`
Expected: compilation FAIL (`escapeLatex`, `entryType` unresolved).

- [ ] **Step 3: Implement `LatexText.kt`**

```kotlin
package com.etatech.hashiya.core.bibtex

private val WHITESPACE = Regex("""\s+""")

/** Escapes the characters LaTeX treats specially. Everything else, Arabic and accented letters included, stays UTF-8. */
internal fun escapeLatex(text: String): String = buildString {
    text.forEach { c ->
        append(
            when (c) {
                '\\' -> "\\textbackslash{}"
                '&', '%', '$', '#', '_', '{', '}' -> "\\$c"
                '~' -> "\\textasciitilde{}"
                '^' -> "\\textasciicircum{}"
                else -> c
            }
        )
    }
}

internal fun cleanWhitespace(text: String): String = text.trim().replace(WHITESPACE, " ")

/** Wraps words with a capital after their first character in braces, so bibliography styles keep BERT, ImageNet, iPhone. */
internal fun protectCapitals(text: String): String =
    text.split(' ').joinToString(" ") { word -> if (word.drop(1).any { it.isUpperCase() }) "{$word}" else word }
```

- [ ] **Step 4: Implement `EntryType.kt`**

```kotlin
package com.etatech.hashiya.core.bibtex

import com.etatech.hashiya.core.model.PublicationDetails
import java.util.Locale

/** A BibTeX entry type, the field that holds the paper's venue, and whether a publisher field belongs in it. */
internal enum class EntryType(val bibName: String, val venueField: String?, val hasPublisher: Boolean) {
    Article("article", "journal", hasPublisher = false),
    InProceedings("inproceedings", "booktitle", hasPublisher = false),
    InCollection("incollection", "booktitle", hasPublisher = true),
    Book("book", null, hasPublisher = true),
    PhdThesis("phdthesis", "school", hasPublisher = false),
    TechReport("techreport", "institution", hasPublisher = true),
    Misc("misc", "howpublished", hasPublisher = true)
}

private val JOURNAL_WORK_TYPES = setOf("article", "review", "letter", "editorial")

/** The spec's table, first match wins: a conference article is @inproceedings, a repository article @misc. */
internal fun entryType(details: PublicationDetails): EntryType {
    val work = details.workType?.lowercase(Locale.ROOT)
    val source = details.sourceType?.lowercase(Locale.ROOT)
    return when {
        source == "conference" -> EntryType.InProceedings
        work == "book-chapter" -> EntryType.InCollection
        work == "book" -> EntryType.Book
        work == "dissertation" -> EntryType.PhdThesis
        work == "report" -> EntryType.TechReport
        work == "preprint" || source == "repository" -> EntryType.Misc
        work in JOURNAL_WORK_TYPES && source == "journal" -> EntryType.Article
        else -> EntryType.Misc
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :core:bibtex:test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add core/bibtex
git commit -m "feat: add LaTeX escaping and BibTeX entry types"
```

---

### Task 3: BibTeX entries and files

**Files:**
- Create: `core/bibtex/src/main/kotlin/com/etatech/hashiya/core/bibtex/BibTeX.kt`
- Test: `core/bibtex/src/test/kotlin/com/etatech/hashiya/core/bibtex/BibTeXTest.kt`

**Interfaces:**
- Consumes: `escapeLatex`, `cleanWhitespace`, `protectCapitals`, `entryType`, `EntryType` (Task 2); `Paper`, `PublicationDetails` (Task 1).
- Produces:
  - `data class CitablePaper(val paper: Paper, val citeKey: String)`
  - `object BibTeX { fun entry(paper: CitablePaper): String; fun file(papers: List<CitablePaper>): String }`

The expected strings in this test are the fixture list the iOS spec will copy; keep them in this one file.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.etatech.hashiya.core.bibtex

import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PublicationDetails
import org.junit.Assert.assertEquals
import org.junit.Test

/** The BibTeX fixtures. The iOS port copies this file's cases one to one. */
class BibTeXTest {
    private fun paper(
        title: String = "Deep learning",
        authors: List<String> = listOf("Yann LeCun", "Yoshua Bengio"),
        year: Int? = 2015,
        venue: String? = "Nature",
        doi: String? = "10.1038/nature14539",
        pdf: String? = null,
        details: PublicationDetails = PublicationDetails(workType = "article", sourceType = "journal")
    ) = Paper(
        openAlexId = "W1",
        doi = doi,
        title = title,
        authors = authors.map { Author(it, null) },
        year = year,
        venue = venue,
        abstract = null,
        citationCount = 0,
        isOpenAccess = pdf != null,
        openAccessPdfUrl = pdf,
        publication = details
    )

    @Test
    fun journalArticleWithEveryField() {
        val entry = BibTeX.entry(
            CitablePaper(
                paper(
                    details = PublicationDetails(
                        workType = "article",
                        sourceType = "journal",
                        publisher = "Springer Nature",
                        volume = "521",
                        issue = "7553",
                        firstPage = "436",
                        lastPage = "444"
                    )
                ),
                "lecun2015deep"
            )
        )
        assertEquals(
            """
            @article{lecun2015deep,
              author = {Yann LeCun and Yoshua Bengio},
              title = {Deep learning},
              year = {2015},
              journal = {Nature},
              volume = {521},
              number = {7553},
              pages = {436--444},
              doi = {10.1038/nature14539}
            }

            """.trimIndent(),
            entry
        )
    }

    @Test
    fun conferencePaperIsInproceedingsWithBooktitle() {
        val entry = BibTeX.entry(
            CitablePaper(
                paper(
                    title = "BERT: Pre-training of Deep Bidirectional Transformers",
                    authors = listOf("Jacob Devlin"),
                    year = 2019,
                    venue = "North American Chapter of the Association for Computational Linguistics",
                    doi = "10.18653/v1/n19-1423",
                    details = PublicationDetails(workType = "article", sourceType = "conference", firstPage = "4171", lastPage = "4186")
                ),
                "devlin2019bert"
            )
        )
        assertEquals(
            """
            @inproceedings{devlin2019bert,
              author = {Jacob Devlin},
              title = {{BERT:} Pre-training of Deep Bidirectional Transformers},
              year = {2019},
              booktitle = {North American Chapter of the Association for Computational Linguistics},
              pages = {4171--4186},
              doi = {10.18653/v1/n19-1423}
            }

            """.trimIndent(),
            entry
        )
    }

    @Test
    fun arxivPreprintIsMiscWithEprint() {
        val entry = BibTeX.entry(
            CitablePaper(
                paper(
                    title = "Attention Is All You Need",
                    authors = listOf("Ashish Vaswani"),
                    year = 2017,
                    venue = "arXiv (Cornell University)",
                    doi = "10.48550/arxiv.1706.03762",
                    pdf = "https://arxiv.org/pdf/1706.03762",
                    details = PublicationDetails(workType = "preprint", sourceType = "repository", publisher = "Cornell University")
                ),
                "vaswani2017attention"
            )
        )
        assertEquals(
            """
            @misc{vaswani2017attention,
              author = {Ashish Vaswani},
              title = {Attention Is All You Need},
              year = {2017},
              howpublished = {arXiv (Cornell University)},
              publisher = {Cornell University},
              doi = {10.48550/arxiv.1706.03762},
              eprint = {1706.03762},
              archivePrefix = {arXiv}
            }

            """.trimIndent(),
            entry
        )
    }

    @Test
    fun venueFieldPerEntryType() {
        fun firstVenueLine(work: String, source: String?) = BibTeX.entry(
            CitablePaper(paper(venue = "Venue", details = PublicationDetails(workType = work, sourceType = source)), "k")
        ).lines().firstOrNull { it.contains("{Venue}") }
        assertEquals("  booktitle = {Venue},", firstVenueLine("book-chapter", "book series"))
        assertEquals(null, firstVenueLine("book", null))
        assertEquals("  school = {Venue},", firstVenueLine("dissertation", null))
        assertEquals("  institution = {Venue},", firstVenueLine("report", null))
        assertEquals("  howpublished = {Venue},", firstVenueLine("dataset", null))
    }

    @Test
    fun entryTypeLines() {
        fun header(work: String, source: String?) =
            BibTeX.entry(CitablePaper(paper(details = PublicationDetails(workType = work, sourceType = source)), "k")).lines().first()
        assertEquals("@incollection{k,", header("book-chapter", null))
        assertEquals("@book{k,", header("book", null))
        assertEquals("@phdthesis{k,", header("dissertation", null))
        assertEquals("@techreport{k,", header("report", null))
    }

    @Test
    fun publisherOnlyOnTypesThatTakeOne() {
        fun hasPublisher(work: String, source: String?) = BibTeX.entry(
            CitablePaper(paper(details = PublicationDetails(workType = work, sourceType = source, publisher = "P")), "k")
        ).contains("publisher = {P}")
        assertEquals(false, hasPublisher("article", "journal"))
        assertEquals(false, hasPublisher("article", "conference"))
        assertEquals(true, hasPublisher("book", null))
        assertEquals(true, hasPublisher("book-chapter", null))
        assertEquals(true, hasPublisher("report", null))
        assertEquals(true, hasPublisher("preprint", null))
    }

    @Test
    fun missingFieldsAreLeftOutAndUrlOnlyWithoutDoi() {
        val entry = BibTeX.entry(
            CitablePaper(
                paper(
                    title = "Notes",
                    authors = emptyList(),
                    year = null,
                    venue = null,
                    doi = null,
                    pdf = "https://example.org/a_b.pdf",
                    details = PublicationDetails()
                ),
                "papernd"
            )
        )
        assertEquals(
            """
            @misc{papernd,
              title = {Notes},
              url = {https://example.org/a_b.pdf}
            }

            """.trimIndent(),
            entry
        )
        val withDoi = BibTeX.entry(CitablePaper(paper(pdf = "https://example.org/x.pdf"), "k"))
        assertEquals(false, withDoi.contains("url ="))
    }

    @Test
    fun singlePageAndEqualPages() {
        fun pages(first: String?, last: String?) = BibTeX.entry(
            CitablePaper(paper(details = PublicationDetails("article", "journal", firstPage = first, lastPage = last)), "k")
        ).lines().firstOrNull { it.trimStart().startsWith("pages") }
        assertEquals("  pages = {e12},", pages("e12", null))
        assertEquals("  pages = {7},", pages("7", "7"))
        assertEquals(null, pages(null, "9"))
    }

    @Test
    fun escapesValuesButNotDoiOrUrl() {
        val entry = BibTeX.entry(
            CitablePaper(
                paper(
                    title = "R&D at 50%: the {x}_y   case",
                    authors = listOf("A. O'Brien & Co"),
                    venue = "J. Stuff & Things",
                    doi = "10.1000/a_b%c"
                ),
                "k"
            )
        )
        assertEquals(true, entry.contains("  author = {A. O'Brien \\& Co},"))
        // "R&D" has a capital after its first character, so it is protected like an acronym.
        assertEquals(true, entry.contains("  title = {{R\\&D} at 50\\%: the \\textbraceleft{}x\\textbraceright{}\\_y case},"))
        assertEquals(true, entry.contains("  journal = {J. Stuff \\& Things},"))
        assertEquals(true, entry.contains("  doi = {10.1000/a_b%c}"))
    }

    @Test
    fun protectsCapitalsInTitleAndVenueButNotAuthors() {
        val entry = BibTeX.entry(
            CitablePaper(paper(title = "ImageNet on iPhone", authors = listOf("DeWitt McDonald"), venue = "IEEE TPAMI"), "k")
        )
        assertEquals(true, entry.contains("  title = {{ImageNet} on {iPhone}},"))
        assertEquals(true, entry.contains("  journal = {{IEEE} {TPAMI}},"))
        assertEquals(true, entry.contains("  author = {DeWitt McDonald},"))
    }

    @Test
    fun arabicTextStaysUtf8() {
        val entry = BibTeX.entry(CitablePaper(paper(title = "تطبيقات التعلم العميق", authors = listOf("محمد علي")), "paper2015"))
        assertEquals(true, entry.contains("  author = {محمد علي},"))
        assertEquals(true, entry.contains("  title = {تطبيقات التعلم العميق},"))
    }

    @Test
    fun fileSortsByKeyAndSeparatesWithOneBlankLine() {
        val b = CitablePaper(paper(title = "B"), "bkey")
        val a = CitablePaper(paper(title = "A"), "akey")
        val file = BibTeX.file(listOf(b, a))
        assertEquals(BibTeX.entry(a) + "\n" + BibTeX.entry(b), file)
        assertEquals(true, file.endsWith("}\n"))
        assertEquals("", BibTeX.file(emptyList()))
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :core:bibtex:test --tests '*BibTeXTest*'`
Expected: compilation FAIL (`BibTeX`, `CitablePaper` unresolved).

- [ ] **Step 3: Implement `BibTeX.kt`**

```kotlin
package com.etatech.hashiya.core.bibtex

import com.etatech.hashiya.core.model.Paper

/** A saved paper ready to cite: its metadata and its stored key. */
data class CitablePaper(val paper: Paper, val citeKey: String)

private const val ARXIV_DOI_PREFIX = "10.48550/arxiv."

object BibTeX {
    /** One entry, fields in a fixed order, empty ones left out, ending with a newline. */
    fun entry(paper: CitablePaper): String {
        val fields = fields(paper.paper)
        val body = if (fields.isEmpty()) "" else fields.joinToString(",\n", postfix = "\n") { (name, value) -> "  $name = {$value}" }
        return "@${entryType(paper.paper.publication).bibName}{${paper.citeKey},\n$body}\n"
    }

    /** Entries sorted by cite key, separated by one blank line, ending with a newline. Empty for no papers. */
    fun file(papers: List<CitablePaper>): String = papers.sortedBy { it.citeKey }.joinToString("\n") { entry(it) }

    private fun fields(paper: Paper): List<Pair<String, String>> {
        val details = paper.publication
        val type = entryType(details)
        fun text(value: String?) = value?.let(::cleanWhitespace)?.takeIf { it.isNotEmpty() }?.let(::escapeLatex)
        val doi = paper.doi?.trim()?.takeIf { it.isNotEmpty() }
        val firstPage = text(details.firstPage)
        val lastPage = text(details.lastPage)
        return listOfNotNull(
            paper.authors.mapNotNull { text(it.name) }.takeIf { it.isNotEmpty() }?.let { "author" to it.joinToString(" and ") },
            text(paper.title)?.let { "title" to protectCapitals(it) },
            paper.year?.let { "year" to it.toString() },
            type.venueField?.let { field ->
                text(paper.venue)?.let { venue ->
                    field to if (field == "journal" || field == "booktitle") protectCapitals(venue) else venue
                }
            },
            text(details.volume)?.let { "volume" to it },
            text(details.issue)?.let { "number" to it },
            firstPage?.let { "pages" to if (lastPage == null || lastPage == it) it else "$it--$lastPage" },
            text(details.publisher)?.takeIf { type.hasPublisher }?.let { "publisher" to it },
            doi?.let { "doi" to it },
            doi?.takeIf { it.startsWith(ARXIV_DOI_PREFIX, ignoreCase = true) }?.let { "eprint" to it.substring(ARXIV_DOI_PREFIX.length) },
            doi?.takeIf { it.startsWith(ARXIV_DOI_PREFIX, ignoreCase = true) }?.let { "archivePrefix" to "arXiv" },
            paper.openAccessPdfUrl?.trim()?.takeIf { doi == null && it.isNotEmpty() }?.let { "url" to it }
        )
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :core:bibtex:test`
Expected: PASS. If `venueFieldPerEntryType` fails on the `book` case, check that `EntryType.Book.venueField` is null; the venue must not appear.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add core/bibtex
git commit -m "feat: generate BibTeX entries and files"
```

---
### Task 4: Fetch and map publication details from OpenAlex

**Files:**
- Modify: `core/network/src/main/java/com/etatech/hashiya/core/network/OpenAlexApi.kt:11-13`
- Modify: `core/network/src/main/java/com/etatech/hashiya/core/network/model/NetworkWorks.kt`
- Modify: `core/network/src/test/resources/works_page.json`
- Test: `core/network/src/test/java/com/etatech/hashiya/core/network/model/OpenAlexParsingTest.kt`
- Modify: `core/data/src/main/java/com/etatech/hashiya/core/data/mapping/NetworkWorkMapping.kt`
- Test: `core/data/src/test/java/com/etatech/hashiya/core/data/mapping/NetworkWorkMappingTest.kt`

**Interfaces:**
- Consumes: `PublicationDetails`, `Paper.publication` (Task 1).
- Produces:
  - `NetworkWork.type: String?`, `NetworkWork.biblio: NetworkBiblio?`
  - `data class NetworkBiblio(volume: String?, issue: String?, firstPage: String?, lastPage: String?)`
  - `NetworkSource.type: String?`, `NetworkSource.hostOrganizationName: String?`
  - `NetworkWork.asPaper()` now fills `publication`.
  - `internal fun NetworkWork.asPublicationDetails(): PublicationDetails` (used by Task 8's refetch).

- [ ] **Step 1: Extend the fixture**

In `works_page.json`, in the first result (Attention), add these two lines right after `"publication_year": 2017,`:

```json
      "type": "preprint",
      "biblio": { "volume": "30", "issue": null, "first_page": "5998", "last_page": "6008" },
```

and change its `primary_location.source` line to:

```json
        "source": { "id": "https://openalex.org/S4306420609", "display_name": "Neural Information Processing Systems", "type": "conference", "host_organization_name": "Neural Information Processing Systems Foundation" }
```

The second (sparse) result stays as it is: no `type`, no `biblio`.

- [ ] **Step 2: Write the failing parsing assertions**

In `OpenAlexParsingTest.parsesCompleteWork`, add at the end:

```kotlin
        assertEquals("preprint", work.type)
        assertEquals(NetworkBiblio(volume = "30", issue = null, firstPage = "5998", lastPage = "6008"), work.biblio)
        assertEquals("conference", work.primaryLocation?.source?.type)
        assertEquals("Neural Information Processing Systems Foundation", work.primaryLocation?.source?.hostOrganizationName)
```

In `toleratesNullsInSparseWork`, add:

```kotlin
        assertNull(work.type)
        assertNull(work.biblio)
```

In `OpenAlexLookupDataSourceTest.getWorkRequestsTheWorkWithSelectedFields`, the `WORK_FIELDS` assertion already covers the new fields once the constant changes; also add a direct check to that file:

```kotlin
    @Test
    fun workFieldsIncludeTypeAndBiblio() {
        val fields = WORK_FIELDS.split(",")
        assertTrue("type" in fields)
        assertTrue("biblio" in fields)
    }
```

(add `import org.junit.Assert.assertTrue` if missing).

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :core:network:testDebugUnitTest`
Expected: compilation FAIL (`type`, `NetworkBiblio` unresolved).

- [ ] **Step 4: Implement the network changes**

In `OpenAlexApi.kt`:

```kotlin
internal const val WORK_FIELDS =
    "id,doi,display_name,publication_year,primary_location,authorships," +
        "cited_by_count,open_access,best_oa_location,abstract_inverted_index,type,biblio"
```

In `NetworkWorks.kt`, add to `NetworkWork` after `abstractInvertedIndex`:

```kotlin
    @SerialName("abstract_inverted_index") val abstractInvertedIndex: Map<String, List<Int>>? = null,
    /** OpenAlex's work type, e.g. "article", "preprint", "book-chapter". */
    val type: String? = null,
    val biblio: NetworkBiblio? = null
)

@Serializable
data class NetworkBiblio(
    val volume: String? = null,
    val issue: String? = null,
    @SerialName("first_page") val firstPage: String? = null,
    @SerialName("last_page") val lastPage: String? = null
)
```

and replace `NetworkSource` with:

```kotlin
@Serializable
data class NetworkSource(
    @SerialName("display_name") val displayName: String? = null,
    /** e.g. "journal", "conference", "repository". */
    val type: String? = null,
    @SerialName("host_organization_name") val hostOrganizationName: String? = null
)
```

- [ ] **Step 5: Run the network tests**

Run: `./gradlew :core:network:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Write the failing mapping tests**

In `NetworkWorkMappingTest`, add (imports: `com.etatech.hashiya.core.model.PublicationDetails`, `com.etatech.hashiya.core.network.model.NetworkBiblio`):

```kotlin
    @Test
    fun mapsPublicationDetails() {
        val paper = NetworkWork(
            id = "https://openalex.org/W1",
            primaryLocation = NetworkLocation(source = NetworkSource("Nature", type = "journal", hostOrganizationName = "Springer Nature")),
            type = "article",
            biblio = NetworkBiblio(volume = "521", issue = "7553", firstPage = "436", lastPage = "444")
        ).asPaper()

        assertEquals(
            PublicationDetails(
                workType = "article",
                sourceType = "journal",
                publisher = "Springer Nature",
                volume = "521",
                issue = "7553",
                firstPage = "436",
                lastPage = "444"
            ),
            paper.publication
        )
    }

    @Test
    fun blankPublicationStringsBecomeNull() {
        val paper = NetworkWork(
            id = "https://openalex.org/W1",
            primaryLocation = NetworkLocation(source = NetworkSource("X", type = " ", hostOrganizationName = "")),
            type = "",
            biblio = NetworkBiblio(volume = " ", issue = null, firstPage = "", lastPage = null)
        ).asPaper()

        assertEquals(PublicationDetails(), paper.publication)
    }
```

and in `mapsSparseWorkWithSafeDefaults` add `assertEquals(PublicationDetails(), paper.publication)`.

- [ ] **Step 7: Run them to see them fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*NetworkWorkMappingTest*'`
Expected: FAIL (`publication` is the empty default).

- [ ] **Step 8: Implement the mapping**

In `NetworkWorkMapping.kt`, add the import `com.etatech.hashiya.core.model.PublicationDetails`, add `publication = asPublicationDetails()` as the last argument of the `Paper(...)` call in `asPaper()`, and add:

```kotlin
/** The citation details OpenAlex reports for this work; blank strings become null. */
internal fun NetworkWork.asPublicationDetails(): PublicationDetails {
    val source = primaryLocation?.source
    return PublicationDetails(
        workType = type.orNullIfBlank(),
        sourceType = source?.type.orNullIfBlank(),
        publisher = source?.hostOrganizationName.orNullIfBlank(),
        volume = biblio?.volume.orNullIfBlank(),
        issue = biblio?.issue.orNullIfBlank(),
        firstPage = biblio?.firstPage.orNullIfBlank(),
        lastPage = biblio?.lastPage.orNullIfBlank()
    )
}

private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
```

- [ ] **Step 9: Run the data tests**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*NetworkWorkMappingTest*'`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
./gradlew spotlessApply
git add core/network core/data/src/main/java/com/etatech/hashiya/core/data/mapping/NetworkWorkMapping.kt core/data/src/test/java/com/etatech/hashiya/core/data/mapping/NetworkWorkMappingTest.kt
git commit -m "feat: fetch work type, biblio and publisher from OpenAlex"
```

---

### Task 5: Schema v4, migration and DAOs

**Files:**
- Modify: `core/database/src/main/java/com/etatech/hashiya/core/database/model/PaperEntity.kt`
- Create: `core/database/src/main/java/com/etatech/hashiya/core/database/model/CollectionEntity.kt`
- Create: `core/database/src/main/java/com/etatech/hashiya/core/database/model/CollectionPaperEntity.kt`
- Create: `core/database/src/main/java/com/etatech/hashiya/core/database/model/CollectionWithCount.kt`
- Modify: `core/database/src/main/java/com/etatech/hashiya/core/database/model/DeletedPaper.kt`
- Create: `core/database/src/main/java/com/etatech/hashiya/core/database/dao/CollectionDao.kt`
- Create: `core/database/src/main/java/com/etatech/hashiya/core/database/dao/CitationDao.kt`
- Modify: `core/database/src/main/java/com/etatech/hashiya/core/database/dao/PaperDao.kt`
- Modify: `core/database/src/main/java/com/etatech/hashiya/core/database/migration/Migrations.kt`
- Modify: `core/database/src/main/java/com/etatech/hashiya/core/database/HashiyaDatabase.kt`
- Modify: `core/database/src/main/java/com/etatech/hashiya/core/database/di/DatabaseModule.kt`
- Create: `core/database/schemas/com.etatech.hashiya.core.database.HashiyaDatabase/4.json` (generated)
- Test: `core/database/src/test/java/com/etatech/hashiya/core/database/migration/MigrationTest.kt`
- Test: `core/database/src/test/java/com/etatech/hashiya/core/database/dao/PaperDaoTest.kt`
- Test: `core/database/src/test/java/com/etatech/hashiya/core/database/dao/CollectionDaoTest.kt` (new)
- Test: `core/database/src/test/java/com/etatech/hashiya/core/database/dao/CitationDaoTest.kt` (new)

**Interfaces:**
- Produces (all in `com.etatech.hashiya.core.database`):
  - `PaperEntity` gains `workType`, `sourceType`, `publisher`, `volume`, `issue`, `firstPage`, `lastPage`, `citeKey: String?` (all default null) and `detailsFetched: Boolean = false`.
  - `CollectionEntity(id: Long = 0, name: String, nameKey: String, createdAt: Long)`, `CollectionPaperEntity(collectionId: Long, paperId: String, addedAt: Long)`, `CollectionWithCount(id: Long, name: String, paperCount: Int)`.
  - `DeletedPaper(paper: PaperWithAuthors, notes: PaperNotesEntity?, collectionLinks: List<CollectionPaperEntity> = emptyList())`.
  - `PaperDao.observeLibrary(match: String?, status: String?, collectionId: Long?)`, `PaperDao.observeStatusCounts(match: String?, collectionId: Long?)`.
  - `PaperDao.insertPaperWithAuthors(paper, authors, search, notes = null, collectionLinks = emptyList())`: restores links whose collection exists; drops a cite key another paper holds.
  - `CollectionDao`: `observeCollections(): Flow<List<CollectionWithCount>>`, `insertCollection(name, nameKey, createdAt): Long?` (null on clash), `renameCollection(id, name, nameKey): Boolean` (false on clash), `deleteCollection(id)`, `observeCollectionIdsForPaper(openAlexId): Flow<List<Long>>`, `addToCollection(collectionId, openAlexId, addedAt)`, `removeFromCollection(collectionId, openAlexId)`.
  - `CitationDao`: `getPapers(collectionId: Long?): List<PaperWithAuthors>` (saved order, oldest first), `getPaper(openAlexId): PaperWithAuthors?`, `updatePublicationDetails(...)`, `markDetailsFetched(paperId)`, `allCiteKeys(): List<String>`, `assignCiteKeys(keys: Map<String, String>)`.
  - `MIGRATION_3_4`.

- [ ] **Step 1: Add the entities and bump the version (no behaviour yet)**

Replace `PaperEntity.kt`'s entity with:

```kotlin
@Entity(
    tableName = "papers",
    indices = [
        Index(value = ["open_alex_id"], unique = true),
        // Not unique: OpenAlex sometimes has several works (preprint, published version) with one DOI,
        // and each must be savable. Deduplication by DOI is a later sub-project's decision.
        Index(value = ["doi"]),
        // Many NULLs are allowed; a key, once assigned, belongs to one paper.
        Index(value = ["cite_key"], unique = true)
    ]
)
data class PaperEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "open_alex_id") val openAlexId: String?,
    val doi: String?,
    val title: String,
    val year: Int?,
    val venue: String?,
    val abstract: String?,
    @ColumnInfo(name = "citation_count") val citationCount: Int,
    @ColumnInfo(name = "is_open_access") val isOpenAccess: Boolean,
    @ColumnInfo(name = "oa_pdf_url") val oaPdfUrl: String?,
    @ColumnInfo(name = "saved_at") val savedAt: Long,
    /** One of "to_read", "reading", "read"; core/data maps it to ReadingStatus. */
    @ColumnInfo(name = "reading_status", defaultValue = "'to_read'") val readingStatus: String,
    @ColumnInfo(name = "work_type") val workType: String? = null,
    @ColumnInfo(name = "source_type") val sourceType: String? = null,
    val publisher: String? = null,
    val volume: String? = null,
    val issue: String? = null,
    @ColumnInfo(name = "first_page") val firstPage: String? = null,
    @ColumnInfo(name = "last_page") val lastPage: String? = null,
    /** Assigned the first time the paper is exported or copied, then never changed. */
    @ColumnInfo(name = "cite_key") val citeKey: String? = null,
    /** True once the columns above come from an OpenAlex response that included them; rows from before v4 start false. */
    @ColumnInfo(name = "details_fetched", defaultValue = "0") val detailsFetched: Boolean = false
)
```

`CollectionEntity.kt`:

```kotlin
package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "collections", indices = [Index(value = ["name_key"], unique = true)])
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** The name trimmed and lowercased (collectionNameKey); enforces "no two collections with the same name". */
    @ColumnInfo(name = "name_key") val nameKey: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
```

`CollectionPaperEntity.kt`:

```kotlin
package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** A saved paper's membership in a collection. Deleting either side deletes the link, never the other side. */
@Entity(
    tableName = "collection_papers",
    primaryKeys = ["collection_id", "paper_id"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(entity = PaperEntity::class, parentColumns = ["id"], childColumns = ["paper_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("paper_id")]
)
data class CollectionPaperEntity(
    @ColumnInfo(name = "collection_id") val collectionId: Long,
    @ColumnInfo(name = "paper_id") val paperId: String,
    @ColumnInfo(name = "added_at") val addedAt: Long
)
```

`CollectionWithCount.kt`:

```kotlin
package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo

data class CollectionWithCount(val id: Long, val name: String, @ColumnInfo(name = "paper_count") val paperCount: Int)
```

`DeletedPaper.kt`:

```kotlin
/** What [com.etatech.hashiya.core.database.dao.PaperDao.deleteByOpenAlexId] deleted, so it can be restored. */
data class DeletedPaper(
    val paper: PaperWithAuthors,
    val notes: PaperNotesEntity?,
    val collectionLinks: List<CollectionPaperEntity> = emptyList()
)
```

In `HashiyaDatabase.kt`: add `CollectionEntity::class, CollectionPaperEntity::class` to `entities`, set `version = 4`, and add `abstract fun collectionDao(): CollectionDao` and `abstract fun citationDao(): CitationDao` (create the two DAO files with `@Dao abstract class CollectionDao` / `@Dao abstract class CitationDao` and empty bodies for now).

- [ ] **Step 2: Generate the v4 schema**

Run: `./gradlew :core:database:compileDebugKotlin :core:database:copyRoomSchemas`
Expected: BUILD SUCCESSFUL and `core/database/schemas/com.etatech.hashiya.core.database.HashiyaDatabase/4.json` exists. If `copyRoomSchemas` isn't the task name, run `./gradlew :core:database:tasks --all | grep -i schema` and use the Room schema copy task it lists (the `merge…UnitTestAssets` hook in `core/database/build.gradle.kts` depends on `copyRoomSchemas`).

Open `4.json` and find the `createSql` of `collections`, `collection_papers` and their indices, and the `papers` index on `cite_key`. They must equal the statements in Step 4 with `${TABLE_NAME}` replaced by the table name. If Room generated anything different (spacing included), use Room's text in Step 4.

- [ ] **Step 3: Write the failing migration tests**

In `MigrationTest.kt`, change `openWithRoom()` to add `MIGRATION_3_4` to `addMigrations(...)`, change the two existing `dao.observeLibrary(match, null)` calls in the `ids` helpers to `dao.observeLibrary(match, null, null)`, and add:

```kotlin
    /** A version 3 library as sub-project 4 left it: one paper with a note and a status, one bare. */
    private fun createVersion3() {
        helper.createDatabase(DB_NAME, 3).use { db ->
            db.execSQL(
                "INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, " +
                    "oa_pdf_url, saved_at, reading_status) VALUES ('a', 'W1', '10.48550/arxiv.1706.03762', " +
                    "'Attention Is All You Need', 2017, 'Neural Information Processing Systems', " +
                    "'The dominant sequence transduction models', 128412, 1, NULL, 100, 'read')"
            )
            db.execSQL("INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 0, 'Ashish Vaswani', NULL)")
            db.execSQL(
                "INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, " +
                    "oa_pdf_url, saved_at, reading_status) VALUES ('b', 'W2', NULL, 'تطبيقات التَّعلُّم العميق', NULL, NULL, NULL, 0, 0, " +
                    "NULL, 200, 'to_read')"
            )
            db.execSQL(
                "INSERT INTO paper_notes (paper_id, summary, research_question, method, key_findings, limitations, thoughts, updated_at) " +
                    "VALUES ('a', 'Transformers', '', 'Ablation study', '', '', '', 5)"
            )
            db.execSQL(
                "INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes) VALUES ('a', 'attention is all you need', " +
                    "'ashish vaswani', 'the dominant sequence transduction models', 'neural information processing systems', " +
                    "'transformers ablation study')"
            )
            db.execSQL(
                "INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes) " +
                    "VALUES ('b', 'تطبيقات التعلم العميق', '', '', '', '')"
            )
        }
    }

    @Test
    fun migration3To4KeepsEverythingAndValidatesAgainstVersion4Schema() {
        createVersion3()

        // Validates every table and index, including collections, collection_papers and the cite_key index, against 4.json.
        helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4).use { db ->
            assertEquals(listOf("a:read", "b:to_read"), db.strings("SELECT id || ':' || reading_status FROM papers ORDER BY id"))
            assertEquals(listOf("Ablation study"), db.strings("SELECT method FROM paper_notes"))
            assertEquals(
                listOf("a:transformers ablation study", "b:"),
                db.strings("SELECT paper_id || ':' || notes FROM paper_search ORDER BY paper_id")
            )
            assertEquals(
                listOf("a:0:1:1", "b:0:1:1"),
                db.strings(
                    "SELECT id || ':' || details_fetched || ':' || (cite_key IS NULL) || ':' || (work_type IS NULL AND volume IS NULL) " +
                        "FROM papers ORDER BY id"
                )
            )
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM collections"))
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM collection_papers"))
        }
    }

    @Test
    fun libraryMigratedFromVersion3IsSearchableAndTakesCollections() = runTest {
        createVersion3()
        helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4).close()

        val database = openWithRoom()
        try {
            val dao = database.paperDao()
            suspend fun ids(match: String?, collectionId: Long? = null) =
                dao.observeLibrary(match, null, collectionId).first().map { it.paper.id }

            assertEquals(listOf("a"), ids("\"ablation*\""))
            assertEquals(listOf("b"), ids("\"التعلم*\""))
            val collections = database.collectionDao()
            val id = checkNotNull(collections.insertCollection("Thesis", "thesis", createdAt = 1))
            collections.addToCollection(id, "W1", addedAt = 2)
            assertEquals(listOf("a"), ids(null, id))
        } finally {
            database.close()
        }
    }

    @Test
    fun version1LibraryMigratesAllTheWayToVersion4() {
        createVersion1()

        helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).use { db ->
            assertEquals(
                listOf("a:to_read:0", "b:to_read:0"),
                db.strings("SELECT id || ':' || reading_status || ':' || details_fetched FROM papers ORDER BY id")
            )
        }
    }
```

Add `import com.etatech.hashiya.core.database.migration.MIGRATION_3_4` is not needed (same package); `MIGRATION_3_4` is `internal` in the same module.

- [ ] **Step 4: Write `MIGRATION_3_4`**

Append to `Migrations.kt`:

```kotlin
/**
 * Adds the citation columns to papers (every existing paper has details_fetched = 0, so it is refetched once before its first
 * export), the cite_key index, and the collections tables. Nothing existing is rewritten, and the search index is untouched.
 */
internal val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("work_type", "source_type", "publisher", "volume", "issue", "first_page", "last_page", "cite_key").forEach { column ->
            db.execSQL("ALTER TABLE papers ADD COLUMN `$column` TEXT")
        }
        db.execSQL("ALTER TABLE papers ADD COLUMN `details_fetched` INTEGER NOT NULL DEFAULT 0")
        // The CREATE statements are exactly what Room generates (schemas/…/4.json), so the schema validates.
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_papers_cite_key` ON `papers` (`cite_key`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`name_key` TEXT NOT NULL, `created_at` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_collections_name_key` ON `collections` (`name_key`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `collection_papers` (`collection_id` INTEGER NOT NULL, `paper_id` TEXT NOT NULL, " +
                "`added_at` INTEGER NOT NULL, PRIMARY KEY(`collection_id`, `paper_id`), " +
                "FOREIGN KEY(`collection_id`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`paper_id`) REFERENCES `papers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_papers_paper_id` ON `collection_papers` (`paper_id`)")
    }
}
```

In `DatabaseModule.kt`: `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)` (with the import), and add:

```kotlin
    @Provides
    fun provideCollectionDao(database: HashiyaDatabase): CollectionDao = database.collectionDao()

    @Provides
    fun provideCitationDao(database: HashiyaDatabase): CitationDao = database.citationDao()
```

- [ ] **Step 5: Write the failing DAO tests**

`CollectionDaoTest.kt`:

```kotlin
package com.etatech.hashiya.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.database.model.CollectionWithCount
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.searchEntityFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionDaoTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var dao: CollectionDao
    private lateinit var papers: PaperDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.collectionDao()
        papers = db.paperDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun savePaper(id: String, openAlexId: String) {
        val paper = PaperEntity(id, openAlexId, null, "Title $id", 2020, null, null, 0, false, null, savedAt = 1, readingStatus = "to_read")
        papers.insertPaperWithAuthors(paper, emptyList(), searchEntityFor(id, paper.title, emptyList(), null, null))
    }

    private fun count(table: String): Int = db.query("SELECT COUNT(*) FROM $table", null).use {
        it.moveToFirst()
        it.getInt(0)
    }

    @Test
    fun createsCollectionsSortedByNameKeyWithCounts() = runTest {
        val b = checkNotNull(dao.insertCollection("beta", "beta", createdAt = 1))
        val a = checkNotNull(dao.insertCollection("Alpha", "alpha", createdAt = 2))
        savePaper("p1", "W1")
        dao.addToCollection(a, "W1", addedAt = 3)

        assertEquals(listOf(CollectionWithCount(a, "Alpha", 1), CollectionWithCount(b, "beta", 0)), dao.observeCollections().first())
    }

    @Test
    fun nameClashIsReportedNotThrown() = runTest {
        val id = checkNotNull(dao.insertCollection("Thesis", "thesis", createdAt = 1))
        assertNull(dao.insertCollection(" thesis ", "thesis", createdAt = 2))
        val other = checkNotNull(dao.insertCollection("Other", "other", createdAt = 3))

        assertFalse(dao.renameCollection(other, "THESIS", "thesis"))
        assertTrue(dao.renameCollection(id, "thesis", "thesis"))
        assertTrue(dao.renameCollection(other, "Chapter 2", "chapter 2"))
        assertEquals(listOf("Chapter 2", "thesis"), dao.observeCollections().first().map { it.name })
    }

    @Test
    fun membershipIsIdempotentAndFollowsThePaper() = runTest {
        val id = checkNotNull(dao.insertCollection("A", "a", createdAt = 1))
        savePaper("p1", "W1")

        dao.addToCollection(id, "W1", addedAt = 2)
        dao.addToCollection(id, "W1", addedAt = 3)
        assertEquals(listOf(id), dao.observeCollectionIdsForPaper("W1").first())

        dao.removeFromCollection(id, "W1")
        assertEquals(emptyList<Long>(), dao.observeCollectionIdsForPaper("W1").first())
        dao.addToCollection(id, "W-unsaved", addedAt = 4)
        assertEquals(0, count("collection_papers"))
    }

    @Test
    fun deletingACollectionKeepsItsPapersAndDeletingAPaperKeepsItsCollections() = runTest {
        val a = checkNotNull(dao.insertCollection("A", "a", createdAt = 1))
        val b = checkNotNull(dao.insertCollection("B", "b", createdAt = 1))
        savePaper("p1", "W1")
        savePaper("p2", "W2")
        dao.addToCollection(a, "W1", addedAt = 2)
        dao.addToCollection(b, "W2", addedAt = 2)

        dao.deleteCollection(a)
        assertEquals(2, count("papers"))
        assertEquals(1, count("collection_papers"))

        papers.deleteByOpenAlexId("W2")
        assertEquals(listOf("B"), dao.observeCollections().first().map { it.name })
        assertEquals(0, count("collection_papers"))
    }
}
```

`CitationDaoTest.kt`:

```kotlin
package com.etatech.hashiya.core.database.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.searchEntityFor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CitationDaoTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var dao: CitationDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.citationDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun savePaper(id: String, openAlexId: String, savedAt: Long, citeKey: String? = null) {
        val paper = PaperEntity(
            id, openAlexId, null, "Title $id", 2020, null, null, 0, false, null, savedAt = savedAt, readingStatus = "to_read",
            citeKey = citeKey
        )
        db.paperDao().insertPaperWithAuthors(paper, emptyList(), searchEntityFor(id, paper.title, emptyList(), null, null))
    }

    @Test
    fun papersComeOldestSavedFirstAndCanBeLimitedToACollection() = runTest {
        savePaper("p1", "W1", savedAt = 20)
        savePaper("p2", "W2", savedAt = 10)
        val collections = db.collectionDao()
        val id = checkNotNull(collections.insertCollection("A", "a", createdAt = 1))
        collections.addToCollection(id, "W1", addedAt = 1)

        assertEquals(listOf("p2", "p1"), dao.getPapers(null).map { it.paper.id })
        assertEquals(listOf("p1"), dao.getPapers(id).map { it.paper.id })
        assertEquals("p1", dao.getPaper("W1")?.paper?.id)
    }

    @Test
    fun updatesDetailsAndMarksThemFetched() = runTest {
        savePaper("p1", "W1", savedAt = 1)

        dao.updatePublicationDetails("p1", "article", "journal", "Springer", "521", "7553", "436", "444")

        val paper = checkNotNull(dao.getPaper("W1")).paper
        assertEquals(listOf("article", "journal", "Springer", "521", "7553", "436", "444"),
            listOf(paper.workType, paper.sourceType, paper.publisher, paper.volume, paper.issue, paper.firstPage, paper.lastPage))
        assertTrue(paper.detailsFetched)
    }

    @Test
    fun markDetailsFetchedOnlySetsTheFlag() = runTest {
        savePaper("p1", "W1", savedAt = 1)
        dao.markDetailsFetched("p1")
        assertTrue(checkNotNull(dao.getPaper("W1")).paper.detailsFetched)
    }

    @Test
    fun assignsKeysAndRejectsATakenOne() = runTest {
        savePaper("p1", "W1", savedAt = 1, citeKey = "smith2020deep")
        savePaper("p2", "W2", savedAt = 2)

        dao.assignCiteKeys(mapOf("p2" to "smith2020deepa"))
        assertEquals(setOf("smith2020deep", "smith2020deepa"), dao.allCiteKeys().toSet())

        savePaper("p3", "W3", savedAt = 3)
        val failed = runCatching { dao.assignCiteKeys(mapOf("p3" to "smith2020deep")) }.exceptionOrNull()
        assertTrue(failed is SQLiteConstraintException)
        assertEquals(null, dao.getPaper("W3")?.paper?.citeKey)
    }
}
```

In `PaperDaoTest.kt`, change the `ids` helper to

```kotlin
    private suspend fun ids(match: String? = null, status: String? = null, collectionId: Long? = null) =
        dao.observeLibrary(match, status, collectionId).first().map { it.paper.id }
```

and change every other direct `dao.observeLibrary(x, y)` call to `dao.observeLibrary(x, y, null)` and every `dao.observeStatusCounts(x)` to `dao.observeStatusCounts(x, null)` (run `grep -n "observeLibrary(\|observeStatusCounts(" core/database/src/test` to find them). Then add these tests (imports: `CollectionPaperEntity`):

```kotlin
    @Test
    fun libraryAndCountsCanBeLimitedToACollection() = runTest {
        save(paper("a", "W1", 100, title = "Graph networks", status = "read"), "Ada")
        save(paper("b", "W2", 200, title = "Graph kernels"), "Bo")
        save(paper("c", "W3", 300, title = "Other"), "Cy")
        val collections = db.collectionDao()
        val id = checkNotNull(collections.insertCollection("A", "a", createdAt = 1))
        collections.addToCollection(id, "W1", addedAt = 1)
        collections.addToCollection(id, "W3", addedAt = 1)

        assertEquals(listOf("c", "a"), ids(collectionId = id))
        assertEquals(listOf("a"), ids(match = "\"graph*\"", collectionId = id))
        assertEquals(listOf("a"), ids(status = "read", collectionId = id))
        assertEquals(
            listOf(StatusCount("read", 1), StatusCount("to_read", 1)),
            dao.observeStatusCounts(null, id).first().sortedBy { it.readingStatus }
        )
    }

    @Test
    fun deleteCapturesCollectionLinksAndRestoreSkipsDeletedCollections() = runTest {
        save(paper("a", "W1", 100), "Ada")
        val collections = db.collectionDao()
        val kept = checkNotNull(collections.insertCollection("Kept", "kept", createdAt = 1))
        val gone = checkNotNull(collections.insertCollection("Gone", "gone", createdAt = 1))
        collections.addToCollection(kept, "W1", addedAt = 5)
        collections.addToCollection(gone, "W1", addedAt = 6)

        val deleted = checkNotNull(dao.deleteByOpenAlexId("W1"))
        assertEquals(setOf(kept, gone), deleted.collectionLinks.map { it.collectionId }.toSet())
        collections.deleteCollection(gone)

        val row = deleted.paper
        assertTrue(
            dao.insertPaperWithAuthors(
                row.paper,
                row.authors,
                searchEntityFor(row.paper.id, row.paper.title, row.authors.map { it.name }, null, null),
                collectionLinks = deleted.collectionLinks
            )
        )
        assertEquals(listOf(kept), collections.observeCollectionIdsForPaper("W1").first())
    }

    @Test
    fun restoreDropsACiteKeyAnotherPaperTookMeanwhile() = runTest {
        save(paper("a", "W1", 100).copy(citeKey = "ada2020title"), "Ada")
        val deleted = checkNotNull(dao.deleteByOpenAlexId("W1"))
        save(paper("b", "W2", 200).copy(citeKey = "ada2020title"), "Bo")

        val row = deleted.paper
        val restored = dao.insertPaperWithAuthors(
            row.paper,
            row.authors,
            searchEntityFor(row.paper.id, row.paper.title, row.authors.map { it.name }, null, null)
        )

        assertTrue(restored)
        assertEquals(listOf("b", "a"), ids())
        assertEquals(null, dao.observeByOpenAlexId("W1").first()?.paper?.citeKey)
    }
```

- [ ] **Step 6: Run them to see them fail**

Run: `./gradlew :core:database:testDebugUnitTest`
Expected: compilation FAIL (DAO methods missing).

- [ ] **Step 7: Implement `CollectionDao`**

```kotlin
package com.etatech.hashiya.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.etatech.hashiya.core.database.model.CollectionEntity
import com.etatech.hashiya.core.database.model.CollectionWithCount
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CollectionDao {
    @Query(
        """
        SELECT collections.id, collections.name, COUNT(collection_papers.paper_id) AS paper_count FROM collections
        LEFT JOIN collection_papers ON collection_papers.collection_id = collections.id
        GROUP BY collections.id
        ORDER BY collections.name_key
        """
    )
    abstract fun observeCollections(): Flow<List<CollectionWithCount>>

    @Query(
        """
        SELECT collection_papers.collection_id FROM collection_papers
        JOIN papers ON papers.id = collection_papers.paper_id
        WHERE papers.open_alex_id = :openAlexId
        """
    )
    abstract fun observeCollectionIdsForPaper(openAlexId: String): Flow<List<Long>>

    @Query("SELECT id FROM collections WHERE name_key = :nameKey")
    protected abstract suspend fun idForNameKey(nameKey: String): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(collection: CollectionEntity): Long

    @Query("UPDATE collections SET name = :name, name_key = :nameKey WHERE id = :id")
    protected abstract suspend fun updateName(id: Long, name: String, nameKey: String)

    /** Returns the new id, or null when another collection already has [nameKey]. */
    @Transaction
    open suspend fun insertCollection(name: String, nameKey: String, createdAt: Long): Long? {
        if (idForNameKey(nameKey) != null) return null
        return insert(CollectionEntity(name = name, nameKey = nameKey, createdAt = createdAt))
    }

    /**
     * Returns false, changing nothing, when another collection already has [nameKey]. Renaming to a new case of the same name is allowed.
     */
    @Transaction
    open suspend fun renameCollection(id: Long, name: String, nameKey: String): Boolean {
        val owner = idForNameKey(nameKey)
        if (owner != null && owner != id) return false
        updateName(id, name, nameKey)
        return true
    }

    /** Its links cascade; its papers stay. */
    @Query("DELETE FROM collections WHERE id = :id")
    abstract suspend fun deleteCollection(id: Long)

    /** Does nothing when the paper isn't saved or is already in the collection. */
    @Query(
        """
        INSERT OR IGNORE INTO collection_papers (collection_id, paper_id, added_at)
        SELECT :collectionId, id, :addedAt FROM papers WHERE open_alex_id = :openAlexId
        """
    )
    abstract suspend fun addToCollection(collectionId: Long, openAlexId: String, addedAt: Long)

    @Query(
        """
        DELETE FROM collection_papers
        WHERE collection_id = :collectionId AND paper_id IN (SELECT id FROM papers WHERE open_alex_id = :openAlexId)
        """
    )
    abstract suspend fun removeFromCollection(collectionId: Long, openAlexId: String)
}
```

- [ ] **Step 8: Implement `CitationDao`**

```kotlin
package com.etatech.hashiya.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.etatech.hashiya.core.database.model.PaperWithAuthors

@Dao
abstract class CitationDao {
    /** Every saved paper, or those in [collectionId], oldest saved first: the order cite keys are assigned in. */
    @Transaction
    @Query(
        """
        SELECT * FROM papers
        WHERE (:collectionId IS NULL OR id IN (SELECT paper_id FROM collection_papers WHERE collection_id = :collectionId))
        ORDER BY saved_at ASC
        """
    )
    abstract suspend fun getPapers(collectionId: Long?): List<PaperWithAuthors>

    @Transaction
    @Query("SELECT * FROM papers WHERE open_alex_id = :openAlexId")
    abstract suspend fun getPaper(openAlexId: String): PaperWithAuthors?

    @Query(
        """
        UPDATE papers SET work_type = :workType, source_type = :sourceType, publisher = :publisher, volume = :volume,
            issue = :issue, first_page = :firstPage, last_page = :lastPage, details_fetched = 1
        WHERE id = :paperId
        """
    )
    abstract suspend fun updatePublicationDetails(
        paperId: String,
        workType: String?,
        sourceType: String?,
        publisher: String?,
        volume: String?,
        issue: String?,
        firstPage: String?,
        lastPage: String?
    )

    /** For a paper OpenAlex no longer has: asking again would never help. */
    @Query("UPDATE papers SET details_fetched = 1 WHERE id = :paperId")
    abstract suspend fun markDetailsFetched(paperId: String)

    @Query("SELECT cite_key FROM papers WHERE cite_key IS NOT NULL")
    abstract suspend fun allCiteKeys(): List<String>

    @Query("UPDATE papers SET cite_key = :citeKey WHERE id = :paperId AND cite_key IS NULL")
    protected abstract suspend fun setCiteKey(paperId: String, citeKey: String)

    /** Stores every key (paper id → key) or none: a key another paper holds throws SQLiteConstraintException. */
    @Transaction
    open suspend fun assignCiteKeys(keys: Map<String, String>) {
        keys.forEach { (paperId, key) -> setCiteKey(paperId, key) }
    }
}
```

- [ ] **Step 9: Change `PaperDao`**

Replace the two library queries:

```kotlin
    /** Newest saved first. [match] is an FTS MATCH expression, [status] a stored status, [collectionId] a collection; null means "any". */
    @Transaction
    @Query(
        """
        SELECT papers.* FROM papers
        WHERE (:match IS NULL OR papers.id IN (SELECT paper_id FROM paper_search WHERE paper_search MATCH :match))
          AND (:status IS NULL OR papers.reading_status = :status)
          AND (:collectionId IS NULL OR papers.id IN (SELECT paper_id FROM collection_papers WHERE collection_id = :collectionId))
        ORDER BY papers.saved_at DESC
        """
    )
    abstract fun observeLibrary(match: String?, status: String?, collectionId: Long?): Flow<List<PaperWithAuthors>>

    /**
     * How many papers matching [match] (null = all) in [collectionId] (null = all) have each stored status. Statuses with none are missing.
     */
    @Query(
        """
        SELECT reading_status, COUNT(*) AS count FROM papers
        WHERE (:match IS NULL OR papers.id IN (SELECT paper_id FROM paper_search WHERE paper_search MATCH :match))
          AND (:collectionId IS NULL OR papers.id IN (SELECT paper_id FROM collection_papers WHERE collection_id = :collectionId))
        GROUP BY reading_status
        """
    )
    abstract fun observeStatusCounts(match: String?, collectionId: Long?): Flow<List<StatusCount>>
```

Add these building blocks next to the others:

```kotlin
    @Query("SELECT * FROM collection_papers WHERE paper_id = :paperId")
    protected abstract suspend fun getCollectionLinks(paperId: String): List<CollectionPaperEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM papers WHERE cite_key = :citeKey)")
    protected abstract suspend fun citeKeyTaken(citeKey: String): Boolean

    @Query(
        """
        INSERT OR IGNORE INTO collection_papers (collection_id, paper_id, added_at)
        SELECT id, :paperId, :addedAt FROM collections WHERE id = :collectionId
        """
    )
    protected abstract suspend fun insertLinkIfCollectionExists(collectionId: Long, paperId: String, addedAt: Long)
```

Replace `insertPaperWithAuthors` and `deleteByOpenAlexId` with:

```kotlin
    /**
     * Writes the paper, its authors, its search row and (on a restore) its [notes] and [collectionLinks] atomically.
     * Links to collections deleted meanwhile are skipped, and a cite key another paper took meanwhile is dropped (it is
     * reassigned on the next export). Returns false, writing nothing, if it is already saved. [search] must already hold the
     * notes' search text.
     */
    @Transaction
    open suspend fun insertPaperWithAuthors(
        paper: PaperEntity,
        authors: List<PaperAuthorEntity>,
        search: PaperSearchEntity,
        notes: PaperNotesEntity? = null,
        collectionLinks: List<CollectionPaperEntity> = emptyList()
    ): Boolean {
        require(search.paperId == paper.id) { "The search row must belong to the paper" }
        require(notes == null || notes.paperId == paper.id) { "The notes must belong to the paper" }
        require(collectionLinks.all { it.paperId == paper.id }) { "The collection links must belong to the paper" }
        val key = paper.citeKey
        val row = if (key != null && citeKeyTaken(key)) paper.copy(citeKey = null) else paper
        if (insertPaper(row) == -1L) return false
        insertAuthors(authors)
        insertSearch(search)
        notes?.let { upsertNotes(it) }
        collectionLinks.forEach { insertLinkIfCollectionExists(it.collectionId, it.paperId, it.addedAt) }
        return true
    }

    /**
     * Deletes the paper (authors, notes and collection links cascade) and its search row, and returns what was deleted, so it can be
     * restored.
     */
    @Transaction
    open suspend fun deleteByOpenAlexId(openAlexId: String): DeletedPaper? {
        val existing = getByOpenAlexId(openAlexId) ?: return null
        val notes = getNotes(existing.paper.id)
        val links = getCollectionLinks(existing.paper.id)
        deleteById(existing.paper.id)
        deleteSearchById(existing.paper.id)
        return DeletedPaper(existing, notes, links)
    }
```

Note on `citeKeyTaken`: when the same paper is being inserted a second time (already saved), it holds its own key, so `citeKeyTaken` is true and the copy without a key is ignored by `insertPaper` anyway (the open_alex_id conflict). The result is still `false`, as before.

- [ ] **Step 10: Run the database tests**

Run: `./gradlew :core:database:testDebugUnitTest`
Expected: PASS. If `migration3To4…Validates…` fails with a schema mismatch, compare the failing table's expected and found SQL in the message with `4.json` and copy Room's statement into `MIGRATION_3_4`.

- [ ] **Step 11: Fix the callers in `core/data` so the project compiles**

In `RoomLibraryRepository.kt`, temporarily pass `null`: `paperDao.observeLibrary(ftsMatch(query), status?.storedValue, null)` and `paperDao.observeStatusCounts(ftsMatch(query), null)`. Task 6 replaces these.

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 12: Commit**

```bash
./gradlew spotlessApply
git add core/database core/data/src/main/java/com/etatech/hashiya/core/data/repository/RoomLibraryRepository.kt
git commit -m "feat: add schema v4 with citation columns and collections"
```

---
### Task 6: Library repository: publication details, collection filter, Remove and Undo

**Files:**
- Modify: `core/data/src/main/java/com/etatech/hashiya/core/data/mapping/PaperEntityMapping.kt`
- Modify: `core/data/src/main/java/com/etatech/hashiya/core/data/repository/LibraryRepository.kt`
- Modify: `core/data/src/main/java/com/etatech/hashiya/core/data/repository/RoomLibraryRepository.kt`
- Test: `core/data/src/test/java/com/etatech/hashiya/core/data/mapping/PaperEntityMappingTest.kt`
- Test: `core/data/src/test/java/com/etatech/hashiya/core/data/repository/RoomLibraryRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 5's DAO signatures; `PublicationDetails` (Task 1).
- Produces:
  - `Paper.asEntities(localId, savedAt, status, notes = null, citeKey: String? = null, detailsFetched: Boolean = true)`; `PaperWithAuthors.asPaper()` fills `publication`.
  - `LibraryRepository.observeLibrary(query: String, status: ReadingStatus?, collectionId: Long? = null)`
  - `LibraryRepository.observeStatusCounts(query: String, collectionId: Long? = null)`
  - `RemovedPaper(paper, localId, savedAt, status, notes = PaperNotes(), collectionIds: Set<Long> = emptySet(), citeKey: String? = null, detailsFetched: Boolean = true, collectionLinksAddedAt: Map<Long, Long> = emptyMap())`

- [ ] **Step 1: Write the failing tests**

In `PaperEntityMappingTest`, give the sample `paper` a publication (`publication = PublicationDetails("article", "journal", "Pub", "1", "2", "3", "4")`, import `PublicationDetails`), and add:

```kotlin
    @Test
    fun storesPublicationDetailsCiteKeyAndFetchedFlag() {
        val entities =
            paper.asEntities(localId = "l", savedAt = 1, status = ReadingStatus.ToRead, citeKey = "first2020title", detailsFetched = false)

        assertEquals("article", entities.paper.workType)
        assertEquals("journal", entities.paper.sourceType)
        assertEquals("Pub", entities.paper.publisher)
        assertEquals(
            listOf("1", "2", "3", "4"),
            listOf(entities.paper.volume, entities.paper.issue, entities.paper.firstPage, entities.paper.lastPage)
        )
        assertEquals("first2020title", entities.paper.citeKey)
        assertEquals(false, entities.paper.detailsFetched)
        assertEquals(true, paper.asEntities("l", 1, ReadingStatus.ToRead).paper.detailsFetched)
    }
```

(`roundTripsThroughEntities` now also checks that `publication` survives the round trip.)

In `RoomLibraryRepositoryTest`, add (imports: `PublicationDetails`, `kotlinx.coroutines.flow.first` already there):

```kotlin
    @Test
    fun savesPublicationDetailsAsFetched() = runTest {
        val details = PublicationDetails("article", "journal", "Springer", "521", "7553", "436", "444")
        repository.save(paper("W1").copy(publication = details))

        val row = checkNotNull(db.citationDao().getPaper("W1")).paper
        assertEquals("Springer", row.publisher)
        assertEquals(true, row.detailsFetched)
        assertEquals(details, repository.observePaper("W1").first()?.paper?.publication)
    }

    @Test
    fun libraryAndCountsFollowTheSelectedCollection() = runTest {
        repository.save(paper("W1", title = "Graph networks"))
        repository.save(paper("W2", title = "Graph kernels"))
        val collections = db.collectionDao()
        val id = checkNotNull(collections.insertCollection("A", "a", createdAt = 1))
        collections.addToCollection(id, "W1", addedAt = 1)

        assertEquals(listOf("W1"), repository.observeLibrary("graph", null, id).first().map { it.paper.openAlexId })
        assertEquals(1, repository.observeStatusCounts("", id).first().values.sum())
        assertEquals(2, repository.observeStatusCounts("").first().values.sum())
    }

    @Test
    fun removeThenRestoreKeepsCollectionsCiteKeyAndFetchedFlag() = runTest {
        repository.save(paper("W1"))
        val collections = db.collectionDao()
        val kept = checkNotNull(collections.insertCollection("Kept", "kept", createdAt = 1))
        val gone = checkNotNull(collections.insertCollection("Gone", "gone", createdAt = 1))
        collections.addToCollection(kept, "W1", addedAt = 7)
        collections.addToCollection(gone, "W1", addedAt = 8)
        db.citationDao().assignCiteKeys(mapOf("local-1" to "first2020paper"))

        val removed = checkNotNull(repository.remove("W1"))
        assertEquals(setOf(kept, gone), removed.collectionIds)
        assertEquals("first2020paper", removed.citeKey)
        assertEquals(true, removed.detailsFetched)
        collections.deleteCollection(gone)
        repository.restore(removed)

        assertEquals(listOf(kept), collections.observeCollectionIdsForPaper("W1").first())
        val row = checkNotNull(db.citationDao().getPaper("W1")).paper
        assertEquals("first2020paper", row.citeKey)
        assertEquals(true, row.detailsFetched)
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*PaperEntityMappingTest*' --tests '*RoomLibraryRepositoryTest*'`
Expected: compilation FAIL (`citeKey` parameter, `collectionIds` unresolved).

- [ ] **Step 3: Implement the mapping**

In `PaperEntityMapping.kt`, change `asEntities` and `asPaper`:

```kotlin
internal fun Paper.asEntities(
    localId: String,
    savedAt: Long,
    status: ReadingStatus,
    notes: PaperNotes? = null,
    citeKey: String? = null,
    detailsFetched: Boolean = true
): PaperEntities = PaperEntities(
    paper = PaperEntity(
        id = localId,
        openAlexId = openAlexId,
        doi = doi,
        title = title,
        year = year,
        venue = venue,
        abstract = abstract,
        citationCount = citationCount,
        isOpenAccess = isOpenAccess,
        oaPdfUrl = openAccessPdfUrl,
        savedAt = savedAt,
        readingStatus = status.storedValue,
        workType = publication.workType,
        sourceType = publication.sourceType,
        publisher = publication.publisher,
        volume = publication.volume,
        issue = publication.issue,
        firstPage = publication.firstPage,
        lastPage = publication.lastPage,
        citeKey = citeKey,
        detailsFetched = detailsFetched
    ),
    authors = authors.mapIndexed { index, author ->
        PaperAuthorEntity(paperId = localId, position = index, name = author.name, openAlexAuthorId = author.openAlexId)
    },
    search = searchEntityFor(localId, title, authors.map { it.name }, abstract, venue, notes)
)

internal fun PaperWithAuthors.asPaper(): Paper = Paper(
    openAlexId = requireNotNull(paper.openAlexId) { "Papers without an OpenAlex ID are not supported yet" },
    doi = paper.doi,
    title = paper.title,
    authors = authors.sortedBy { it.position }.map { Author(it.name, it.openAlexAuthorId) },
    year = paper.year,
    venue = paper.venue,
    abstract = paper.abstract,
    citationCount = paper.citationCount,
    isOpenAccess = paper.isOpenAccess,
    openAccessPdfUrl = paper.oaPdfUrl,
    publication = PublicationDetails(
        workType = paper.workType,
        sourceType = paper.sourceType,
        publisher = paper.publisher,
        volume = paper.volume,
        issue = paper.issue,
        firstPage = paper.firstPage,
        lastPage = paper.lastPage
    )
)
```

(import `com.etatech.hashiya.core.model.PublicationDetails`).

- [ ] **Step 4: Implement the repository changes**

In `LibraryRepository.kt`:

```kotlin
    /**
     * Papers matching [query] (blank = all), [status] (null = all) and [collectionId] (null = all papers), newest saved first. Notes are
     * searched too.
     */
    fun observeLibrary(query: String, status: ReadingStatus?, collectionId: Long? = null): Flow<List<LibraryPaper>>

    /** How many papers of each status match [query] within [collectionId] (null = all papers); statuses with none are 0. */
    fun observeStatusCounts(query: String, collectionId: Long? = null): Flow<Map<ReadingStatus, Int>>
```

and extend the docs of `save`, `remove`, `restore` and `RemovedPaper`:

```kotlin
    /** New papers start as To read, with their publication details marked fetched. Saving a paper that is already saved does nothing. */
    suspend fun save(paper: Paper)

    /** Returns what was removed, including its collections and cite key, for Undo; null if the paper was not saved. */
    suspend fun remove(openAlexId: String): RemovedPaper?

    /**
     * Puts a removed paper back where it was, with its status, notes, cite key and collections. Collections deleted meanwhile are
     * skipped, and a cite key another paper took meanwhile is dropped. Does nothing if it has been saved again meanwhile.
     */
    suspend fun restore(removed: RemovedPaper)
}

data class RemovedPaper(
    val paper: Paper,
    val localId: String,
    val savedAt: Long,
    val status: ReadingStatus,
    val notes: PaperNotes = PaperNotes(),
    val collectionIds: Set<Long> = emptySet(),
    val citeKey: String? = null,
    val detailsFetched: Boolean = true,
    /** When the paper was added to each of [collectionIds]; restored as-is. */
    val collectionLinksAddedAt: Map<Long, Long> = emptyMap()
)
```

In `RoomLibraryRepository.kt`:

```kotlin
    override fun observeLibrary(query: String, status: ReadingStatus?, collectionId: Long?): Flow<List<LibraryPaper>> =
        paperDao.observeLibrary(ftsMatch(query), status?.storedValue, collectionId).map { rows ->
            rows.map { LibraryPaper(it.asPaper(), readingStatusOf(it.paper.readingStatus)) }
        }

    override fun observeStatusCounts(query: String, collectionId: Long?): Flow<Map<ReadingStatus, Int>> =
        paperDao.observeStatusCounts(ftsMatch(query), collectionId).map { rows ->
            // Unknown stored values read as To read, so they are counted there too.
            val counts = ReadingStatus.entries.associateWith { 0 }.toMutableMap()
            rows.forEach { row -> counts.merge(readingStatusOf(row.readingStatus), row.count, Int::plus) }
            counts
        }
```

Replace `remove` and `restore`:

```kotlin
    override suspend fun remove(openAlexId: String): RemovedPaper? = paperDao.deleteByOpenAlexId(openAlexId)?.let { deleted ->
        val row = deleted.paper
        RemovedPaper(
            paper = row.asPaper(),
            localId = row.paper.id,
            savedAt = row.paper.savedAt,
            status = readingStatusOf(row.paper.readingStatus),
            notes = deleted.notes?.asPaperNotes() ?: PaperNotes(),
            collectionIds = deleted.collectionLinks.map { it.collectionId }.toSet(),
            citeKey = row.paper.citeKey,
            detailsFetched = row.paper.detailsFetched,
            collectionLinksAddedAt = deleted.collectionLinks.associate { it.collectionId to it.addedAt }
        )
    }

    override suspend fun restore(removed: RemovedPaper) {
        val entities = removed.paper.asEntities(
            localId = removed.localId,
            savedAt = removed.savedAt,
            status = removed.status,
            notes = removed.notes,
            citeKey = removed.citeKey,
            detailsFetched = removed.detailsFetched
        )
        // Nothing reads updated_at yet, so a restore doesn't need the original value.
        val notes = removed.notes.takeUnless { it.isEmpty }?.asEntity(removed.localId, updatedAt = now())
        val links = removed.collectionIds.map { id ->
            CollectionPaperEntity(collectionId = id, paperId = removed.localId, addedAt = removed.collectionLinksAddedAt[id] ?: now())
        }
        paperDao.insertPaperWithAuthors(entities.paper, entities.authors, entities.search, notes, links)
    }
```

(import `com.etatech.hashiya.core.database.model.CollectionPaperEntity`).

- [ ] **Step 5: Run the data tests**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Update the shared fake so the project compiles**

`FakeLibraryRepository` in `core/testing` must match the new signatures. Change its two overrides to take `collectionId: Long?` and ignore it for now (Task 9 adds collection support):

```kotlin
    override fun observeLibrary(query: String, status: ReadingStatus?, collectionId: Long?): Flow<List<LibraryPaper>> = rows.map { list ->
        list.filter { (status == null || it.status == status) && it.matches(query) }
            .sortedByDescending { it.savedAt }
            .map { LibraryPaper(it.paper, it.status) }
    }

    override fun observeStatusCounts(query: String, collectionId: Long?): Flow<Map<ReadingStatus, Int>> = rows.map { list ->
        val matching = list.filter { it.matches(query) }
        ReadingStatus.entries.associateWith { status -> matching.count { it.status == status } }
    }
```

`LibraryViewModelTest`'s private `LaggingLibraryRepository` overrides both methods too; give its overrides the new parameter and pass it on:

```kotlin
        override fun observeLibrary(query: String, status: ReadingStatus?, collectionId: Long?): Flow<List<LibraryPaper>> =
            delegate.observeLibrary(query, status, collectionId).onEach { if (listLags) delay(1) }

        override fun observeStatusCounts(query: String, collectionId: Long?): Flow<Map<ReadingStatus, Int>> =
            delegate.observeStatusCounts(query, collectionId).onEach { if (countsLag) delay(1) }
```

Run: `./gradlew testDebugUnitTest`
Expected: PASS (every module).

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply
git add core/data core/testing feature/library/src/test
git commit -m "feat: store publication details and keep collections and cite keys through Undo"
```

---

### Task 7: Collections repository

**Files:**
- Create: `core/data/src/main/java/com/etatech/hashiya/core/data/repository/CollectionsRepository.kt`
- Create: `core/data/src/main/java/com/etatech/hashiya/core/data/repository/RoomCollectionsRepository.kt`
- Modify: `core/data/src/main/java/com/etatech/hashiya/core/data/di/DataModule.kt`
- Test: `core/data/src/test/java/com/etatech/hashiya/core/data/repository/RoomCollectionsRepositoryTest.kt`

**Interfaces:**
- Consumes: `CollectionDao` (Task 5); `PaperCollection`, `isValidCollectionName`, `collectionNameKey` (Task 1).
- Produces:

```kotlin
interface CollectionsRepository {
    fun observeCollections(): Flow<List<PaperCollection>>
    fun observeCollectionIds(openAlexId: String): Flow<Set<Long>>
    suspend fun create(name: String): CollectionResult
    suspend fun rename(id: Long, name: String): CollectionResult
    suspend fun delete(id: Long)
    suspend fun setMembership(collectionId: Long, openAlexId: String, member: Boolean)
}

sealed interface CollectionResult {
    data class Done(val id: Long) : CollectionResult
    data object NameTaken : CollectionResult
    data object InvalidName : CollectionResult
}
```

(`Done` is the spec's `Created`; `rename` returns it too, so one name reads right for both.)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.etatech.hashiya.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperCollection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomCollectionsRepositoryTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var repository: RoomCollectionsRepository
    private lateinit var library: RoomLibraryRepository
    private var clock = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCollectionsRepository(db.collectionDao(), now = { ++clock })
        var ids = 0
        library = RoomLibraryRepository(db.paperDao(), now = { ++clock }, newId = { "local-${++ids}" })
    }

    @After
    fun tearDown() = db.close()

    private fun paper(id: String) = Paper(id, null, "Paper $id", listOf(Author("A", null)), 2020, null, null, 0, false, null)

    @Test
    fun createTrimsTheNameAndRejectsDuplicatesByCaseAndSpaces() = runTest {
        val created = repository.create("  Thesis  ")
        assertEquals(CollectionResult.Done(1), created)
        assertEquals(CollectionResult.NameTaken, repository.create("thesis"))
        assertEquals(CollectionResult.NameTaken, repository.create(" THESIS "))
        assertEquals(listOf(PaperCollection(1, "Thesis", 0)), repository.observeCollections().first())
    }

    @Test
    fun invalidNamesAreRejected() = runTest {
        assertEquals(CollectionResult.InvalidName, repository.create("   "))
        assertEquals(CollectionResult.InvalidName, repository.create("x".repeat(61)))
        val id = (repository.create("A") as CollectionResult.Done).id
        assertEquals(CollectionResult.InvalidName, repository.rename(id, ""))
    }

    @Test
    fun renameAllowsANewCaseButNotAnotherCollectionsName() = runTest {
        val a = (repository.create("Alpha") as CollectionResult.Done).id
        val b = (repository.create("Beta") as CollectionResult.Done).id

        assertEquals(CollectionResult.NameTaken, repository.rename(b, " alpha"))
        assertEquals(CollectionResult.Done(a), repository.rename(a, "ALPHA"))
        assertEquals(listOf("ALPHA", "Beta"), repository.observeCollections().first().map { it.name })
    }

    @Test
    fun membershipAndDelete() = runTest {
        library.save(paper("W1"))
        val id = (repository.create("A") as CollectionResult.Done).id

        repository.setMembership(id, "W1", member = true)
        assertEquals(setOf(id), repository.observeCollectionIds("W1").first())
        assertEquals(1, repository.observeCollections().first().single().paperCount)

        repository.setMembership(id, "W1", member = false)
        assertEquals(emptySet<Long>(), repository.observeCollectionIds("W1").first())

        repository.setMembership(id, "W1", member = true)
        repository.delete(id)
        assertEquals(emptyList<PaperCollection>(), repository.observeCollections().first())
        assertEquals(listOf("W1"), library.observeLibrary("", null).first().map { it.paper.openAlexId })
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*RoomCollectionsRepositoryTest*'`
Expected: compilation FAIL.

- [ ] **Step 3: Implement**

`CollectionsRepository.kt`:

```kotlin
package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.model.PaperCollection
import kotlinx.coroutines.flow.Flow

interface CollectionsRepository {
    /** Every collection with its paper count, sorted by name ignoring case. */
    fun observeCollections(): Flow<List<PaperCollection>>

    /** The collections [openAlexId] is in; empty when it is in none or isn't saved. */
    fun observeCollectionIds(openAlexId: String): Flow<Set<Long>>

    /**
     * Trims the name. [CollectionResult.InvalidName] unless it is 1–60 characters; [CollectionResult.NameTaken] if another collection has
     * it, ignoring case.
     */
    suspend fun create(name: String): CollectionResult

    /** Same rules as [create]; renaming a collection to a new case of its own name is allowed. */
    suspend fun rename(id: Long, name: String): CollectionResult

    /** Deletes the collection; its papers stay in the library. */
    suspend fun delete(id: Long)

    /** Adds or removes the paper. Adding a paper that isn't saved, or is already in the collection, does nothing. */
    suspend fun setMembership(collectionId: Long, openAlexId: String, member: Boolean)
}

sealed interface CollectionResult {
    /** Created or renamed; [id] is the collection's. */
    data class Done(val id: Long) : CollectionResult

    data object NameTaken : CollectionResult

    data object InvalidName : CollectionResult
}
```

`RoomCollectionsRepository.kt`:

```kotlin
package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.database.dao.CollectionDao
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.core.model.collectionNameKey
import com.etatech.hashiya.core.model.isValidCollectionName
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomCollectionsRepository(private val collectionDao: CollectionDao, private val now: () -> Long) : CollectionsRepository {
    @Inject
    constructor(collectionDao: CollectionDao) : this(collectionDao, System::currentTimeMillis)

    override fun observeCollections(): Flow<List<PaperCollection>> =
        collectionDao.observeCollections().map { rows -> rows.map { PaperCollection(it.id, it.name, it.paperCount) } }

    override fun observeCollectionIds(openAlexId: String): Flow<Set<Long>> =
        collectionDao.observeCollectionIdsForPaper(openAlexId).map { it.toSet() }

    override suspend fun create(name: String): CollectionResult {
        if (!isValidCollectionName(name)) return CollectionResult.InvalidName
        val id = collectionDao.insertCollection(name.trim(), collectionNameKey(name), now()) ?: return CollectionResult.NameTaken
        return CollectionResult.Done(id)
    }

    override suspend fun rename(id: Long, name: String): CollectionResult {
        if (!isValidCollectionName(name)) return CollectionResult.InvalidName
        val renamed = collectionDao.renameCollection(id, name.trim(), collectionNameKey(name))
        return if (renamed) CollectionResult.Done(id) else CollectionResult.NameTaken
    }

    override suspend fun delete(id: Long) = collectionDao.deleteCollection(id)

    override suspend fun setMembership(collectionId: Long, openAlexId: String, member: Boolean) {
        if (member) {
            collectionDao.addToCollection(collectionId, openAlexId, now())
        } else {
            collectionDao.removeFromCollection(collectionId, openAlexId)
        }
    }
}
```

In `DataModule.kt`, add:

```kotlin
    @Binds
    abstract fun bindCollectionsRepository(impl: RoomCollectionsRepository): CollectionsRepository
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add core/data
git commit -m "feat: add the collections repository"
```

---

### Task 8: Citation repository

**Files:**
- Modify: `core/data/build.gradle.kts`
- Create: `core/data/src/main/java/com/etatech/hashiya/core/data/repository/CitationRepository.kt`
- Create: `core/data/src/main/java/com/etatech/hashiya/core/data/repository/RoomCitationRepository.kt`
- Modify: `core/data/src/main/java/com/etatech/hashiya/core/data/di/DataModule.kt`
- Test: `core/data/src/test/java/com/etatech/hashiya/core/data/repository/RoomCitationRepositoryTest.kt`

**Interfaces:**
- Consumes: `CitationDao` (Task 5); `OpenAlexLookupDataSource.getWork(id)`, `NetworkException` (`core/network`); `NetworkWork.asPublicationDetails()` (Task 4); `PaperWithAuthors.asPaper()` (Task 6); `BibTeX`, `CitablePaper`, `CiteKeys` (Tasks 1–3).
- Produces:

```kotlin
interface CitationRepository {
    suspend fun entry(openAlexId: String): CitationResult?
    suspend fun export(collectionId: Long?): CitationResult
}

data class CitationResult(val bibtex: String, val complete: Boolean)
```

- [ ] **Step 1: Add the dependency**

In `core/data/build.gradle.kts`, add `implementation(project(":core:bibtex"))` next to the other `project(...)` dependencies.

- [ ] **Step 2: Write the failing test**

```kotlin
package com.etatech.hashiya.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.data.FakeOpenAlexLookupDataSource
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.OpenAlexLookupDataSource
import com.etatech.hashiya.core.network.model.NetworkBiblio
import com.etatech.hashiya.core.network.model.NetworkLocation
import com.etatech.hashiya.core.network.model.NetworkSource
import com.etatech.hashiya.core.network.model.NetworkWork
import com.etatech.hashiya.core.network.model.NetworkWorksResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomCitationRepositoryTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var library: RoomLibraryRepository
    private val openAlex = FakeOpenAlexLookupDataSource()
    private var clock = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var ids = 0
        library = RoomLibraryRepository(db.paperDao(), now = { ++clock }, newId = { "local-${++ids}" })
    }

    @After
    fun tearDown() = db.close()

    private fun repository(source: OpenAlexLookupDataSource = openAlex) = RoomCitationRepository(db.citationDao(), source)

    private fun paper(id: String, surname: String, title: String = "Deep nets") =
        Paper(id, null, title, listOf(Author("Jane $surname", null)), 2020, "Nature", null, 0, false, null)

    /** Saves the paper as a v3 library left it: no details, details_fetched = 0. */
    private suspend fun saveUnfetched(paper: Paper) {
        library.save(paper)
        db.openHelper.writableDatabase.execSQL("UPDATE papers SET details_fetched = 0 WHERE open_alex_id = ?", arrayOf(paper.openAlexId))
    }

    private fun journalWork(id: String) = NetworkWork(
        id = "https://openalex.org/$id",
        type = "article",
        primaryLocation = NetworkLocation(source = NetworkSource("Nature", type = "journal")),
        biblio = NetworkBiblio(volume = "521", firstPage = "436", lastPage = "444")
    )

    @Test
    fun entryRefetchesOnceThenUsesStoredDetailsAndKey() = runTest {
        saveUnfetched(paper("W1", "Smith"))
        openAlex.works = mapOf("W1" to journalWork("W1"))

        val first = checkNotNull(repository().entry("W1"))
        val second = checkNotNull(repository().entry("W1"))

        assertTrue(first.complete)
        assertEquals(listOf("W1"), openAlex.workRequests)
        assertTrue(first.bibtex.startsWith("@article{smith2020deep,\n"))
        assertTrue(first.bibtex.contains("  volume = {521},"))
        assertEquals(first, second)
    }

    @Test
    fun papersSavedAfterV4AreNotRefetched() = runTest {
        library.save(paper("W1", "Smith"))
        repository().entry("W1")
        assertEquals(emptyList<String>(), openAlex.workRequests)
    }

    @Test
    fun unsavedPaperHasNoEntry() = runTest {
        assertNull(repository().entry("W404"))
    }

    @Test
    fun failedRefetchIsIncompleteAndRetriedNextTime() = runTest {
        saveUnfetched(paper("W1", "Smith"))
        openAlex.getFailure = NetworkFailure.Connectivity

        val offline = repository().export(null)
        assertFalse(offline.complete)
        assertTrue(offline.bibtex.startsWith("@misc{smith2020deep,"))

        openAlex.getFailure = null
        openAlex.works = mapOf("W1" to journalWork("W1"))
        val online = repository().export(null)
        assertTrue(online.complete)
        assertTrue(online.bibtex.startsWith("@article{smith2020deep,"))
        assertEquals(listOf("W1", "W1"), openAlex.workRequests)
    }

    @Test
    fun aWorkOpenAlexNoLongerHasIsMarkedFetched() = runTest {
        saveUnfetched(paper("W1", "Smith"))
        openAlex.works = emptyMap()

        assertTrue(repository().export(null).complete)
        repository().export(null)
        assertEquals(listOf("W1"), openAlex.workRequests)
    }

    @Test
    fun keysAreAssignedInSavedOrderAndNeverChange() = runTest {
        library.save(paper("W1", "Smith"))
        library.save(paper("W2", "Smith"))
        val first = repository().export(null)
        assertTrue(first.bibtex.contains("@misc{smith2020deep,"))
        assertTrue(first.bibtex.contains("@misc{smith2020deepa,"))

        // A paper saved later with the same base key gets the next suffix; the first two keep theirs.
        library.save(paper("W3", "Smith"))
        val removed = checkNotNull(library.remove("W1"))
        library.restore(removed)
        val again = repository().export(null)
        assertEquals(
            listOf("smith2020deep", "smith2020deepa", "smith2020deepb"),
            Regex("""@misc\{(\w+),""").findAll(again.bibtex).map { it.groupValues[1] }.toList()
        )
        assertEquals("smith2020deep", db.citationDao().getPaper("W1")?.paper?.citeKey)
    }

    @Test
    fun exportCoversTheWholeCollectionRegardlessOfStatus() = runTest {
        library.save(paper("W1", "Adams"))
        library.save(paper("W2", "Brown"))
        library.save(paper("W3", "Clark"))
        library.setStatus("W2", ReadingStatus.Read)
        val collections = db.collectionDao()
        val id = checkNotNull(collections.insertCollection("A", "a", createdAt = 1))
        collections.addToCollection(id, "W1", addedAt = 1)
        collections.addToCollection(id, "W2", addedAt = 1)

        val bibtex = repository().export(id).bibtex
        assertTrue(bibtex.contains("{adams2020deep,"))
        assertTrue(bibtex.contains("{brown2020deep,"))
        assertFalse(bibtex.contains("clark"))
    }

    @Test
    fun emptyCollectionExportsAnEmptyCompleteFile() = runTest {
        val id = checkNotNull(db.collectionDao().insertCollection("A", "a", createdAt = 1))
        assertEquals(CitationResult("", complete = true), repository().export(id))
    }

    @Test
    fun refetchesAtMostFourAtATime() = runTest {
        repeat(9) { saveUnfetched(paper("W$it", "S$it")) }
        var running = 0
        var peak = 0
        val slow = object : OpenAlexLookupDataSource {
            override suspend fun getWork(id: String): NetworkWork? {
                running++
                peak = maxOf(peak, running)
                delay(10)
                running--
                return journalWork(id)
            }

            override suspend fun findWorks(filter: String, perPage: Int): NetworkWorksResponse = error("unused")
        }

        assertTrue(repository(slow).export(null).complete)
        assertEquals(4, peak)
    }
}
```


- [ ] **Step 3: Run it to see it fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*RoomCitationRepositoryTest*'`
Expected: compilation FAIL (`RoomCitationRepository` unresolved).

- [ ] **Step 4: Implement**

`CitationRepository.kt`:

```kotlin
package com.etatech.hashiya.core.data.repository

interface CitationRepository {
    /** One saved paper's BibTeX entry, refetching its details first if needed. Null if it isn't saved. */
    suspend fun entry(openAlexId: String): CitationResult?

    /** Every paper in [collectionId] (null = the whole library), regardless of any search or status filter. */
    suspend fun export(collectionId: Long?): CitationResult
}

/** [complete] is false when at least one paper's details still couldn't be fetched, so the entry may lack volume or pages. */
data class CitationResult(val bibtex: String, val complete: Boolean)
```

`RoomCitationRepository.kt`:

```kotlin
package com.etatech.hashiya.core.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.etatech.hashiya.core.bibtex.BibTeX
import com.etatech.hashiya.core.bibtex.CitablePaper
import com.etatech.hashiya.core.bibtex.CiteKeys
import com.etatech.hashiya.core.data.mapping.asPaper
import com.etatech.hashiya.core.data.mapping.asPublicationDetails
import com.etatech.hashiya.core.database.dao.CitationDao
import com.etatech.hashiya.core.database.model.PaperWithAuthors
import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.OpenAlexLookupDataSource
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val MAX_CONCURRENT_REFETCHES = 4

/** Refetches details papers saved before v4 lack, once each; assigns cite keys once; then builds the BibTeX text. */
internal class RoomCitationRepository @Inject constructor(
    private val citationDao: CitationDao,
    private val openAlex: OpenAlexLookupDataSource
) : CitationRepository {
    override suspend fun entry(openAlexId: String): CitationResult? {
        val stored = citationDao.getPaper(openAlexId) ?: return null
        val complete = refetch(listOf(stored))
        assignMissingKeys()
        val row = citationDao.getPaper(openAlexId) ?: return null
        return CitationResult(BibTeX.entry(row.citable()), complete)
    }

    override suspend fun export(collectionId: Long?): CitationResult {
        val complete = refetch(citationDao.getPapers(collectionId))
        assignMissingKeys()
        val rows = citationDao.getPapers(collectionId)
        return CitationResult(BibTeX.file(rows.map { it.citable() }), complete)
    }

    /** Returns true when every paper now has its details. A paper OpenAlex no longer has counts as done. */
    private suspend fun refetch(rows: List<PaperWithAuthors>): Boolean {
        val missing = rows.filter { !it.paper.detailsFetched }
        if (missing.isEmpty()) return true
        val permits = Semaphore(MAX_CONCURRENT_REFETCHES)
        val results = coroutineScope {
            missing.map { row ->
                async { permits.withPermit { refetchOne(row) } }
            }.awaitAll()
        }
        return results.all { it }
    }

    private suspend fun refetchOne(row: PaperWithAuthors): Boolean {
        val openAlexId = row.paper.openAlexId ?: return true
        val work = try {
            openAlex.getWork(openAlexId)
        } catch (e: NetworkException) {
            return false
        }
        if (work == null) {
            citationDao.markDetailsFetched(row.paper.id)
        } else {
            val details = work.asPublicationDetails()
            citationDao.updatePublicationDetails(
                row.paper.id,
                details.workType,
                details.sourceType,
                details.publisher,
                details.volume,
                details.issue,
                details.firstPage,
                details.lastPage
            )
        }
        return true
    }

    /**
     * Gives every keyless saved paper a key, oldest saved first, in one transaction. Keys are assigned across the whole library,
     * not just the exported papers, so a key never depends on which collection was exported first. A clash with a key another
     * export stored at the same moment retries once against the fresh set.
     */
    private suspend fun assignMissingKeys() {
        repeat(2) { attempt ->
            val keyless = citationDao.getPapers(null).filter { it.paper.citeKey == null }
            if (keyless.isEmpty()) return
            val keys = CiteKeys.assign(keyless.map { it.asPaper() }, citationDao.allCiteKeys().toSet())
            try {
                citationDao.assignCiteKeys(keyless.map { it.paper.id }.zip(keys).toMap())
                return
            } catch (e: SQLiteConstraintException) {
                if (attempt == 1) throw e
            }
        }
    }

    private fun PaperWithAuthors.citable() = CitablePaper(asPaper(), checkNotNull(paper.citeKey) { "Keys are assigned before building" })
}
```

In `DataModule.kt`, add:

```kotlin
    @Binds
    abstract fun bindCitationRepository(impl: RoomCitationRepository): CitationRepository
```

Why keys are assigned library-wide: the spec says keys are assigned "in saved-date order, the first time a paper is exported or copied". Assigning only the exported subset would give a paper saved earlier but exported later a suffix that depends on export order. Assigning every keyless paper at once keeps keys a pure function of saved order at the first export, which is what `keysAreAssignedInSavedOrderAndNeverChange` checks.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS. If `refetchesAtMostFourAtATime` sees a peak below 4, check that `delay` runs on the test scheduler (the repository must not switch dispatchers).

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add core/data
git commit -m "feat: build BibTeX from stored details, refetching old papers once"
```

---
### Task 9: Test fakes for collections and citations

**Files:**
- Modify: `core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeLibraryRepository.kt`
- Create: `core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeCollectionsRepository.kt`
- Create: `core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeCitationRepository.kt`
- Test: `core/testing/src/test/java/com/etatech/hashiya/core/testing/FakeCollectionsRepositoryTest.kt`

**Interfaces:**
- Consumes: `CollectionsRepository`, `CollectionResult` (Task 7); `CitationRepository`, `CitationResult` (Task 8); `LibraryRepository` with `collectionId` (Task 6).
- Produces:
  - `FakeLibraryRepository` filters by `collectionId`; `remove` captures `collectionIds`; `restore` re-adds memberships of collections that still exist.
  - `class FakeCollectionsRepository(private val library: FakeLibraryRepository) : CollectionsRepository` with `var failOnChange = false`.
  - `class FakeCitationRepository : CitationRepository` with `var complete`, `var failure: Exception?`, `var exportText`, `var entries: Map<String, String>`, `val exports: MutableList<Long?>`, `var gate: CompletableDeferred<Unit>?`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.CollectionResult
import com.etatech.hashiya.core.model.PaperCollection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeCollectionsRepositoryTest {
    private val library = FakeLibraryRepository()
    private val collections = FakeCollectionsRepository(library)

    @Test
    fun behavesLikeTheRealOne() = runTest {
        library.save(SamplePapers.attention)
        library.save(SamplePapers.bert)
        val id = (collections.create(" Thesis ") as CollectionResult.Done).id
        assertEquals(CollectionResult.NameTaken, collections.create("THESIS"))
        assertEquals(CollectionResult.InvalidName, collections.create(" "))

        collections.setMembership(id, SamplePapers.bert.openAlexId, member = true)
        assertEquals(listOf(PaperCollection(id, "Thesis", 1)), collections.observeCollections().first())
        assertEquals(listOf(SamplePapers.bert.title), library.observeLibrary("", null, id).first().map { it.paper.title })
        assertEquals(1, library.observeStatusCounts("", id).first().values.sum())

        val removed = checkNotNull(library.remove(SamplePapers.bert.openAlexId))
        assertEquals(setOf(id), removed.collectionIds)
        assertEquals(0, collections.observeCollections().first().single().paperCount)
        library.restore(removed)
        assertEquals(setOf(id), collections.observeCollectionIds(SamplePapers.bert.openAlexId).first())

        collections.delete(id)
        assertEquals(emptyList<PaperCollection>(), collections.observeCollections().first())
        assertEquals(2, library.observeLibrary("", null).first().size)
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :core:testing:testDebugUnitTest --tests '*FakeCollectionsRepositoryTest*'`
Expected: compilation FAIL.

- [ ] **Step 3: Add collection support to `FakeLibraryRepository`**

Add fields (next to `rows`):

```kotlin
    /** Collection id → name, and collection id → member ids. Owned here so the library can filter; FakeCollectionsRepository edits them. */
    internal val collectionNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    internal val memberships = MutableStateFlow<Map<Long, Set<String>>>(emptyMap())
```

Replace the two observers (import `kotlinx.coroutines.flow.combine`):

```kotlin
    override fun observeLibrary(query: String, status: ReadingStatus?, collectionId: Long?): Flow<List<LibraryPaper>> =
        combine(rows, memberships) { list, members ->
            list.filter { (status == null || it.status == status) && it.matches(query) && it.isIn(collectionId, members) }
                .sortedByDescending { it.savedAt }
                .map { LibraryPaper(it.paper, it.status) }
        }

    override fun observeStatusCounts(query: String, collectionId: Long?): Flow<Map<ReadingStatus, Int>> =
        combine(rows, memberships) { list, members ->
            val matching = list.filter { it.matches(query) && it.isIn(collectionId, members) }
            ReadingStatus.entries.associateWith { status -> matching.count { it.status == status } }
        }
```

Replace `remove` and `restore`:

```kotlin
    override suspend fun remove(openAlexId: String): RemovedPaper? {
        if (failOnRemove) throw IOException("disk full")
        val row = rows.value.firstOrNull { it.paper.openAlexId == openAlexId } ?: return null
        val ids = memberships.value.filterValues { openAlexId in it }.keys
        memberships.update { all -> all.mapValues { (_, members) -> members - openAlexId } }
        rows.update { it - row }
        return row.copy(collectionIds = ids)
    }

    override suspend fun restore(removed: RemovedPaper) {
        if (isSaved(removed.paper.openAlexId)) return
        rows.update { it + removed.copy(collectionIds = emptySet()) }
        val existing = removed.collectionIds.filter { it in collectionNames.value }
        memberships.update { all -> all + existing.associateWith { id -> all[id].orEmpty() + removed.paper.openAlexId } }
    }

    internal fun isSavedPaper(openAlexId: String) = isSaved(openAlexId)
```

and add at the bottom of the file:

```kotlin
private fun RemovedPaper.isIn(collectionId: Long?, members: Map<Long, Set<String>>) =
    collectionId == null || paper.openAlexId in members[collectionId].orEmpty()
```

- [ ] **Step 4: Write `FakeCollectionsRepository`**

```kotlin
package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.CollectionResult
import com.etatech.hashiya.core.data.repository.CollectionsRepository
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.core.model.collectionNameKey
import com.etatech.hashiya.core.model.isValidCollectionName
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Collections kept in [library], so filtering the library by collection and Remove/Undo behave like Room. */
class FakeCollectionsRepository(private val library: FakeLibraryRepository) : CollectionsRepository {
    private var nextId = 1L

    /** When true, every write throws like a failing disk would. */
    var failOnChange = false

    override fun observeCollections(): Flow<List<PaperCollection>> =
        combine(library.collectionNames, library.memberships, library.observeSavedIds()) { names, members, saved ->
            names.map { (id, name) -> PaperCollection(id, name, members[id].orEmpty().count { it in saved }) }
                .sortedBy { collectionNameKey(it.name) }
        }

    override fun observeCollectionIds(openAlexId: String): Flow<Set<Long>> =
        library.memberships.map { all -> all.filterValues { openAlexId in it }.keys }

    override suspend fun create(name: String): CollectionResult {
        check()
        if (!isValidCollectionName(name)) return CollectionResult.InvalidName
        if (taken(name, except = null)) return CollectionResult.NameTaken
        val id = nextId++
        library.collectionNames.update { it + (id to name.trim()) }
        return CollectionResult.Done(id)
    }

    override suspend fun rename(id: Long, name: String): CollectionResult {
        check()
        if (!isValidCollectionName(name)) return CollectionResult.InvalidName
        if (taken(name, except = id)) return CollectionResult.NameTaken
        library.collectionNames.update { it + (id to name.trim()) }
        return CollectionResult.Done(id)
    }

    override suspend fun delete(id: Long) {
        check()
        library.collectionNames.update { it - id }
        library.memberships.update { it - id }
    }

    override suspend fun setMembership(collectionId: Long, openAlexId: String, member: Boolean) {
        check()
        if (member && (collectionId !in library.collectionNames.value || !library.isSavedPaper(openAlexId))) return
        library.memberships.update { all ->
            val current = all[collectionId].orEmpty()
            all + (collectionId to if (member) current + openAlexId else current - openAlexId)
        }
    }

    private fun taken(name: String, except: Long?) =
        library.collectionNames.value.any { (id, existing) -> id != except && collectionNameKey(existing) == collectionNameKey(name) }

    private fun check() {
        if (failOnChange) throw IOException("disk full")
    }
}
```

- [ ] **Step 5: Write `FakeCitationRepository`**

```kotlin
package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.CitationRepository
import com.etatech.hashiya.core.data.repository.CitationResult
import kotlinx.coroutines.CompletableDeferred

class FakeCitationRepository : CitationRepository {
    /** What every result reports as [CitationResult.complete]. */
    var complete = true

    /** When set, [entry] and [export] throw it. */
    var failure: Exception? = null

    var exportText = "@misc{paper2020,\n}\n"

    /** openAlexId → the entry [entry] returns; ids not here are "not saved". */
    var entries: Map<String, String> = emptyMap()

    /** The collection id of every [export] call, in order. */
    val exports = mutableListOf<Long?>()

    /** When set, [export] waits for it, so a test can see the export running. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun entry(openAlexId: String): CitationResult? {
        failure?.let { throw it }
        return entries[openAlexId]?.let { CitationResult(it, complete) }
    }

    override suspend fun export(collectionId: Long?): CitationResult {
        exports += collectionId
        gate?.await()
        failure?.let { throw it }
        return CitationResult(exportText, complete)
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :core:testing:testDebugUnitTest`
Expected: PASS (`FakeLibraryRepositoryTest` included; its calls use the default `collectionId`).

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply
git add core/testing
git commit -m "test: add fakes for collections and citations"
```

---

### Task 10: The collection name dialog and icons

**Files:**
- Create: `core/designsystem/src/main/java/com/etatech/hashiya/core/designsystem/component/CollectionNameDialog.kt`
- Modify: `core/designsystem/src/main/java/com/etatech/hashiya/core/designsystem/icon/HashiyaIcons.kt`
- Modify: `core/designsystem/src/main/res/values/strings.xml`, `core/designsystem/src/main/res/values-ar/strings.xml`
- Test: `core/designsystem/src/test/java/com/etatech/hashiya/core/designsystem/component/CollectionNameDialogTest.kt`

**Interfaces:**
- Consumes: `isValidCollectionName`, `COLLECTION_NAME_MAX_LENGTH` (Task 1).
- Produces:
  - `@Composable fun CollectionNameDialog(initialName: String?, nameTaken: Boolean, onNameEdited: () -> Unit, onConfirm: (String) -> Unit, onDismiss: () -> Unit)`: `initialName == null` is New (title "New collection", button "Create"); otherwise Rename (title "Rename collection", button "Save").
  - `const val COLLECTION_NAME_FIELD_TAG = "collection_name_field"`
  - `HashiyaIcons.Export` (`Icons.Outlined.Share`), `HashiyaIcons.Copy` (`Icons.Outlined.ContentCopy`), `HashiyaIcons.Collection` (`Icons.Outlined.FolderOpen`).

- [ ] **Step 1: Add the strings**

`values/strings.xml` (before `</resources>`):

```xml
    <string name="collection_name_label">Collection name</string>
    <string name="collection_new_title">New collection</string>
    <string name="collection_rename_title">Rename collection</string>
    <string name="collection_create">Create</string>
    <string name="collection_save">Save</string>
    <string name="collection_cancel">Cancel</string>
    <string name="collection_name_taken">A collection with that name already exists</string>
```

`values-ar/strings.xml`:

```xml
    <string name="collection_name_label">اسم المجموعة</string>
    <string name="collection_new_title">مجموعة جديدة</string>
    <string name="collection_rename_title">إعادة تسمية المجموعة</string>
    <string name="collection_create">إنشاء</string>
    <string name="collection_save">حفظ</string>
    <string name="collection_cancel">إلغاء</string>
    <string name="collection_name_taken">توجد مجموعة بهذا الاسم بالفعل</string>
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.etatech.hashiya.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionNameDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val confirmed = mutableListOf<String>()
    private var edits = 0

    private fun show(initialName: String? = null, nameTaken: Boolean = false) = composeRule.setContent {
        HashiyaTheme {
            CollectionNameDialog(
                initialName = initialName,
                nameTaken = nameTaken,
                onNameEdited = { edits++ },
                onConfirm = { confirmed += it },
                onDismiss = {}
            )
        }
    }

    @Test
    fun newCollectionIsCreatedOnlyWithAValidName() {
        show()
        composeRule.onNodeWithText("New collection").assertExists()
        composeRule.onNodeWithText("Create").assertIsNotEnabled()

        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextInput("   ")
        composeRule.onNodeWithText("Create").assertIsNotEnabled()
        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextInput("Thesis")
        composeRule.onNodeWithText("Create").assertIsEnabled().performClick()

        assertEquals(listOf("   Thesis"), confirmed)
        assertEquals(true, edits > 0)
    }

    @Test
    fun renameStartsWithTheCurrentNameAndRejectsTooLongNames() {
        show(initialName = "Chapter 2")
        composeRule.onNodeWithText("Rename collection").assertExists()
        composeRule.onNodeWithText("Chapter 2").assertExists()

        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextClearance()
        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextInput("x".repeat(61))
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun showsTheNameTakenError() {
        var taken by mutableStateOf(true)
        composeRule.setContent {
            HashiyaTheme {
                CollectionNameDialog(
                    initialName = null,
                    nameTaken = taken,
                    onNameEdited = { taken = false },
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithText("A collection with that name already exists").assertExists()
        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextInput("a")
        composeRule.onNodeWithText("A collection with that name already exists").assertDoesNotExist()
    }
}
```

- [ ] **Step 3: Run it to see it fail**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests '*CollectionNameDialogTest*'`
Expected: compilation FAIL.

- [ ] **Step 4: Implement the dialog and icons**

`CollectionNameDialog.kt`:

```kotlin
package com.etatech.hashiya.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.etatech.hashiya.core.designsystem.R
import com.etatech.hashiya.core.model.isValidCollectionName

const val COLLECTION_NAME_FIELD_TAG = "collection_name_field"

/**
 * Asks for a collection's name. [initialName] null is "New collection" with Create; otherwise "Rename collection" with Save.
 * The button is enabled only for a valid name (1–60 characters after trimming). [nameTaken] shows the clash error under the
 * field until the name is edited ([onNameEdited]). [onConfirm] gets the text as typed; the repository trims it.
 */
@Composable
fun CollectionNameDialog(
    initialName: String?,
    nameTaken: Boolean,
    onNameEdited: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName.orEmpty()) }
    val valid = isValidCollectionName(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initialName == null) R.string.collection_new_title else R.string.collection_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    onNameEdited()
                },
                label = { Text(stringResource(R.string.collection_name_label)) },
                singleLine = true,
                isError = nameTaken,
                supportingText = if (nameTaken) {
                    { Text(stringResource(R.string.collection_name_taken)) }
                } else {
                    null
                },
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(name) }),
                modifier = Modifier.testTag(COLLECTION_NAME_FIELD_TAG)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = valid) {
                Text(stringResource(if (initialName == null) R.string.collection_create else R.string.collection_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.collection_cancel)) }
        }
    )
}
```

(ktlint sorts the imports on `spotlessApply`.)

In `HashiyaIcons.kt`, add the imports `androidx.compose.material.icons.outlined.ContentCopy`, `androidx.compose.material.icons.outlined.FolderOpen`, `androidx.compose.material.icons.outlined.Share` and:

```kotlin
    val Export: ImageVector = Icons.Outlined.Share
    val Copy: ImageVector = Icons.Outlined.ContentCopy
    val Collection: ImageVector = Icons.Outlined.FolderOpen
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add core/designsystem
git commit -m "feat: add the collection name dialog"
```

---
### Task 11: Library ViewModel: collections, swipe from a collection, export

**Files:**
- Modify: `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryUiState.kt`
- Modify: `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryViewModel.kt`
- Create: `feature/library/src/main/java/com/etatech/hashiya/feature/library/export/BibFileName.kt`
- Test: `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryViewModelTest.kt`
- Test: `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryCollectionsViewModelTest.kt` (new)
- Test: `feature/library/src/test/java/com/etatech/hashiya/feature/library/export/BibFileNameTest.kt` (new)
- Modify: `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibrarySwipeUndoTest.kt` (constructor calls)

**Interfaces:**
- Consumes: `CollectionsRepository`, `CollectionResult` (Task 7), `CitationRepository` (Task 8), fakes (Task 9), `PaperCollection` (Task 1).
- Produces:
  - `LibraryViewModel(savedStateHandle, libraryRepository, collectionsRepository, citationRepository)`
  - `LibraryUiState.CollectionEmpty(val collection: PaperCollection)`
  - `data class LibraryHeader(collections: List<PaperCollection> = emptyList(), selected: PaperCollection? = null, viewSize: Int = 0, libraryCount: Int = 0, exporting: Boolean = false)`
  - `sealed interface CollectionDialog { New(nameTaken); Rename(collection, nameTaken); ConfirmDelete(collection) }`
  - `data class CollectionRemoval(val collection: PaperCollection, val openAlexId: String)`
  - `data class BibExport(val fileName: String, val bibtex: String, val complete: Boolean)`
  - `enum class LibraryMessage { StatusUpdateFailed, CollectionsUpdateFailed, ExportFailed, ExportIncomplete }`
  - ViewModel members: `header`, `dialog`, `pendingCollectionUndo`, `exportReady` (all `StateFlow`); `onSelectCollection(id: Long?)`, `onNewCollection()`, `onRenameCollection(c)`, `onDeleteCollection(c)`, `onDialogNameEdited()`, `onDialogConfirm(name)`, `onConfirmDelete()`, `onDialogDismiss()`, `onUndoCollectionRemove()`, `onCollectionUndoDismissed()`, `onExport()`, `onExportShared()`, `onExportFailed()`, `onScreenResumed()`.
  - `internal fun bibFileName(collectionName: String?): String`

- [ ] **Step 1: Write the failing file-name test**

```kotlin
package com.etatech.hashiya.feature.library.export

import org.junit.Assert.assertEquals
import org.junit.Test

class BibFileNameTest {
    @Test
    fun wholeLibrary() = assertEquals("hashiya-library.bib", bibFileName(null))

    @Test
    fun collectionNameWithUnsafeCharactersReplaced() {
        assertEquals("Chapter 2.bib", bibFileName("Chapter 2"))
        assertEquals("a-b-c-d-e-f-g-h-i-j.bib", bibFileName("a/b\\c:d*e?f\"g<h>i|j"))
        assertEquals("tab-x.bib", bibFileName("tab\tx"))
        assertEquals("الفصل الثاني.bib", bibFileName("الفصل الثاني"))
    }

    @Test
    fun nothingLeftIsCollection() {
        assertEquals("-.bib", bibFileName("/"))
        assertEquals("collection.bib", bibFileName("   "))
    }
}
```

- [ ] **Step 2: Implement `BibFileName.kt`**

```kotlin
package com.etatech.hashiya.feature.library.export

private val UNSAFE_FILE_NAME_CHARACTERS = Regex("""[/\\:*?"<>|\p{Cntrl}]""")

/** "hashiya-library.bib" for the whole library; otherwise the collection's name with characters files can't hold replaced by "-". */
internal fun bibFileName(collectionName: String?): String {
    if (collectionName == null) return "hashiya-library.bib"
    val safe = collectionName.replace(UNSAFE_FILE_NAME_CHARACTERS, "-").trim()
    return safe.ifEmpty { "collection" } + ".bib"
}
```

Run: `./gradlew :feature:library:testDebugUnitTest --tests '*BibFileNameTest*'`
Expected: PASS.

- [ ] **Step 3: Update the UI state types**

Replace `LibraryUiState.kt` from `sealed interface LibraryUiState` down with:

```kotlin
sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    /** Nothing saved yet; the search field and chips are hidden. */
    data object Empty : LibraryUiState

    /** The selected collection has no papers at all; the search field and chips are hidden. */
    data class CollectionEmpty(val collection: PaperCollection) : LibraryUiState

    /** The library (or the selected collection) has papers, but none match the search and the chip. */
    data class NoMatches(val filter: LibraryFilter) : LibraryUiState

    data class Papers(val papers: List<LibraryPaper>, val filter: LibraryFilter) : LibraryUiState
}

/** The top bar: the collection selector and whether Export is offered. */
data class LibraryHeader(
    val collections: List<PaperCollection> = emptyList(),
    /** Null is All papers. */
    val selected: PaperCollection? = null,
    /** Papers in the current view, ignoring the search and the chip; Export is offered when above 0. */
    val viewSize: Int = 0,
    /** Every saved paper: the count beside "All papers" in the selector. */
    val libraryCount: Int = 0,
    val exporting: Boolean = false
)

/** The dialog on top of the Library, if any. */
sealed interface CollectionDialog {
    data class New(val nameTaken: Boolean = false) : CollectionDialog

    data class Rename(val collection: PaperCollection, val nameTaken: Boolean = false) : CollectionDialog

    data class ConfirmDelete(val collection: PaperCollection) : CollectionDialog
}

/** A paper swiped out of [collection], for Undo. */
data class CollectionRemoval(val collection: PaperCollection, val openAlexId: String)

/** A .bib file ready to share. */
data class BibExport(val fileName: String, val bibtex: String, val complete: Boolean)

enum class LibraryMessage { StatusUpdateFailed, CollectionsUpdateFailed, ExportFailed, ExportIncomplete }
```

(import `com.etatech.hashiya.core.model.PaperCollection`).

- [ ] **Step 4: Write the failing ViewModel tests**

In `LibraryViewModelTest.kt`, add fields and change the two constructor calls:

```kotlin
    private val collections = FakeCollectionsRepository(repository)
    private val citations = FakeCitationRepository()
```

```kotlin
        val viewModel = LibraryViewModel(handle, repository, collections, citations)
```

```kotlin
        val lagging = LaggingLibraryRepository(repository, listLags, countsLag)
        val viewModel = LibraryViewModel(SavedStateHandle(), lagging, collections, citations)
```

In `LibrarySwipeUndoTest.kt`, change both `LibraryViewModel(SavedStateHandle(), repository)` to
`LibraryViewModel(SavedStateHandle(), repository, FakeCollectionsRepository(FakeLibraryRepository()), FakeCitationRepository())`
(imports from `com.etatech.hashiya.core.testing`).

Create `LibraryCollectionsViewModelTest.kt`:

```kotlin
package com.etatech.hashiya.feature.library

import androidx.lifecycle.SavedStateHandle
import com.etatech.hashiya.core.data.repository.CollectionResult
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.FakeCitationRepository
import com.etatech.hashiya.core.testing.FakeCollectionsRepository
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import com.etatech.hashiya.core.testing.SamplePapers
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryCollectionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val collections = FakeCollectionsRepository(library)
    private val citations = FakeCitationRepository()

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): LibraryViewModel {
        val viewModel = LibraryViewModel(handle, library, collections, citations)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.header.collect() }
        return viewModel
    }

    /** attention, bert and vit saved; bert and vit in "Thesis". */
    private suspend fun thesis(): PaperCollection {
        SamplePapers.all.forEach { library.save(it) }
        val id = (collections.create("Thesis") as CollectionResult.Done).id
        collections.setMembership(id, SamplePapers.bert.openAlexId, true)
        collections.setMembership(id, SamplePapers.vit.openAlexId, true)
        return PaperCollection(id, "Thesis", 2)
    }

    private fun LibraryViewModel.titles() = (uiState.value as LibraryUiState.Papers).papers.map { it.paper.title }

    @Test
    fun selectingACollectionFiltersTheListCountsAndHeader() = runTest {
        val thesis = thesis()
        val viewModel = viewModel()
        assertEquals(3, viewModel.header.value.viewSize)

        viewModel.onSelectCollection(thesis.id)
        advanceUntilIdle()

        assertEquals(listOf(SamplePapers.vit.title, SamplePapers.bert.title), viewModel.titles())
        assertEquals(2, (viewModel.uiState.value as LibraryUiState.Papers).filter.total)
        assertEquals(thesis, viewModel.header.value.selected)
        assertEquals(2, viewModel.header.value.viewSize)
        assertEquals(3, viewModel.header.value.libraryCount)
    }

    @Test
    fun searchAndChipApplyInsideTheCollection() = runTest {
        val thesis = thesis()
        library.setStatus(SamplePapers.bert.openAlexId, ReadingStatus.Read)
        val viewModel = viewModel()
        viewModel.onSelectCollection(thesis.id)
        viewModel.onStatusFilterChange(ReadingStatus.Read)
        advanceUntilIdle()

        assertEquals(listOf(SamplePapers.bert.title), viewModel.titles())
        assertEquals(2, viewModel.header.value.viewSize)
    }

    @Test
    fun emptyCollectionHasItsOwnState() = runTest {
        SamplePapers.all.forEach { library.save(it) }
        val id = (collections.create("Empty") as CollectionResult.Done).id
        val viewModel = viewModel()
        viewModel.onSelectCollection(id)
        advanceUntilIdle()

        assertEquals(LibraryUiState.CollectionEmpty(PaperCollection(id, "Empty", 0)), viewModel.uiState.value)
        assertEquals(0, viewModel.header.value.viewSize)
    }

    @Test
    fun selectionSurvivesProcessDeathAndFallsBackWhenTheCollectionIsDeleted() = runTest {
        val thesis = thesis()
        val handle = SavedStateHandle()
        viewModel(handle).onSelectCollection(thesis.id)
        advanceUntilIdle()

        val restored = viewModel(handle)
        advanceUntilIdle()
        assertEquals(thesis.id, restored.header.value.selected?.id)

        collections.delete(thesis.id)
        advanceUntilIdle()
        assertNull(restored.header.value.selected)
        assertEquals(3, restored.titles().size)
    }

    @Test
    fun swipeInACollectionRemovesOnlyTheMembershipWithUndo() = runTest {
        val thesis = thesis()
        val viewModel = viewModel()
        viewModel.onSelectCollection(thesis.id)
        advanceUntilIdle()

        viewModel.onRemove(SamplePapers.bert)
        advanceUntilIdle()

        assertEquals(CollectionRemoval(thesis, SamplePapers.bert.openAlexId), viewModel.pendingCollectionUndo.value)
        assertNull(viewModel.pendingUndo.value)
        assertEquals(listOf(SamplePapers.vit.title), viewModel.titles())
        viewModel.onSelectCollection(null)
        advanceUntilIdle()
        assertEquals(3, viewModel.titles().size)

        viewModel.onUndoCollectionRemove()
        advanceUntilIdle()
        assertNull(viewModel.pendingCollectionUndo.value)
        assertEquals(setOf(thesis.id), collections.observeCollectionIdsNow(SamplePapers.bert.openAlexId))
    }

    @Test
    fun swipeInAllPapersStillRemovesFromTheLibrary() = runTest {
        thesis()
        val viewModel = viewModel()
        viewModel.onRemove(SamplePapers.bert)
        advanceUntilIdle()

        assertEquals(SamplePapers.bert, viewModel.pendingUndo.value?.paper)
        assertNull(viewModel.pendingCollectionUndo.value)
    }

    @Test
    fun newCollectionDialogCreatesOrShowsTheClash() = runTest {
        thesis()
        val viewModel = viewModel()

        viewModel.onNewCollection()
        assertEquals(CollectionDialog.New(), viewModel.dialog.value)
        viewModel.onDialogConfirm(" thesis ")
        advanceUntilIdle()
        assertEquals(CollectionDialog.New(nameTaken = true), viewModel.dialog.value)
        viewModel.onDialogNameEdited()
        assertEquals(CollectionDialog.New(nameTaken = false), viewModel.dialog.value)

        viewModel.onDialogConfirm("Chapter 2")
        advanceUntilIdle()
        assertNull(viewModel.dialog.value)
        assertEquals(listOf("Chapter 2", "Thesis"), viewModel.header.value.collections.map { it.name })
    }

    @Test
    fun renameAndDelete() = runTest {
        val thesis = thesis()
        val viewModel = viewModel()

        viewModel.onRenameCollection(thesis)
        viewModel.onDialogConfirm("Dissertation")
        advanceUntilIdle()
        assertNull(viewModel.dialog.value)
        assertEquals(listOf("Dissertation"), viewModel.header.value.collections.map { it.name })

        viewModel.onDeleteCollection(viewModel.header.value.collections.single())
        assertTrue(viewModel.dialog.value is CollectionDialog.ConfirmDelete)
        viewModel.onConfirmDelete()
        advanceUntilIdle()
        assertNull(viewModel.dialog.value)
        assertEquals(emptyList<PaperCollection>(), viewModel.header.value.collections)
        assertEquals(3, viewModel.titles().size)
    }

    @Test
    fun aFailedCollectionChangeClosesTheDialogWithAMessage() = runTest {
        thesis()
        val viewModel = viewModel()
        collections.failOnChange = true

        viewModel.onNewCollection()
        viewModel.onDialogConfirm("Chapter 2")
        advanceUntilIdle()

        assertNull(viewModel.dialog.value)
        assertEquals(LibraryMessage.CollectionsUpdateFailed, viewModel.message.value)
    }

    @Test
    fun exportRunsOnceForTheSelectedCollectionAndNamesTheFile() = runTest {
        val thesis = thesis()
        val viewModel = viewModel()
        viewModel.onSelectCollection(thesis.id)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        citations.gate = gate

        viewModel.onExport()
        viewModel.onExport()
        advanceUntilIdle()
        assertTrue(viewModel.header.value.exporting)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf<Long?>(thesis.id), citations.exports)
        assertEquals(BibExport("Thesis.bib", citations.exportText, complete = true), viewModel.exportReady.value)
        assertEquals(false, viewModel.header.value.exporting)
        viewModel.onExportShared()
        assertNull(viewModel.exportReady.value)
        viewModel.onScreenResumed()
        assertNull(viewModel.message.value)
    }

    @Test
    fun incompleteExportShowsItsMessageWhenTheScreenResumes() = runTest {
        thesis()
        citations.complete = false
        val viewModel = viewModel()

        viewModel.onExport()
        advanceUntilIdle()
        assertEquals("hashiya-library.bib", viewModel.exportReady.value?.fileName)
        viewModel.onExportShared()
        assertNull(viewModel.message.value)

        viewModel.onScreenResumed()
        assertEquals(LibraryMessage.ExportIncomplete, viewModel.message.value)
        viewModel.onMessageShown()
        viewModel.onScreenResumed()
        assertNull(viewModel.message.value)
    }

    @Test
    fun failedExportShowsCouldntExport() = runTest {
        thesis()
        citations.failure = IOException("disk full")
        val viewModel = viewModel()

        viewModel.onExport()
        advanceUntilIdle()
        assertEquals(LibraryMessage.ExportFailed, viewModel.message.value)
        assertEquals(false, viewModel.header.value.exporting)

        citations.failure = null
        viewModel.onMessageShown()
        viewModel.onExport()
        advanceUntilIdle()
        viewModel.onExportFailed()
        assertNull(viewModel.exportReady.value)
        assertEquals(LibraryMessage.ExportFailed, viewModel.message.value)
    }
}

private suspend fun FakeCollectionsRepository.observeCollectionIdsNow(openAlexId: String): Set<Long> =
    observeCollectionIds(openAlexId).first()
```

- [ ] **Step 5: Run them to see them fail**

Run: `./gradlew :feature:library:testDebugUnitTest --tests '*LibraryCollectionsViewModelTest*'`
Expected: compilation FAIL (constructor and members missing).

- [ ] **Step 6: Implement the ViewModel**

Replace `LibraryViewModel.kt` with (existing behaviour unchanged; new parts marked by their doc comments):

```kotlin
package com.etatech.hashiya.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.data.repository.CitationRepository
import com.etatech.hashiya.core.data.repository.CollectionResult
import com.etatech.hashiya.core.data.repository.CollectionsRepository
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.feature.library.export.bibFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val SEARCH_DEBOUNCE_MS = 300L
private const val KEY_QUERY = "library_query"
private const val KEY_STATUS = "library_status"
private const val KEY_COLLECTION = "library_collection"

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val collectionsRepository: CollectionsRepository,
    private val citationRepository: CitationRepository
) : ViewModel() {
    /** The search text as typed. */
    private val query = MutableStateFlow(savedStateHandle.get<String>(KEY_QUERY).orEmpty())

    /** The selected status chip; null is All. */
    private val status = MutableStateFlow(
        savedStateHandle.get<String>(KEY_STATUS)?.let { name -> ReadingStatus.entries.firstOrNull { it.name == name } }
    )

    /** The selected collection's id; null is All papers. Falls back to null when the collection is deleted. */
    private val selectedId = MutableStateFlow(savedStateHandle.get<Long>(KEY_COLLECTION))

    /** The text actually searched: follows [query] after a pause, or at once on Search or Clear. Restored text applies at once. */
    private val appliedQuery = MutableStateFlow(query.value)

    private val collections: StateFlow<List<PaperCollection>> =
        collectionsRepository.observeCollections().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val exporting = MutableStateFlow(false)

    /** Set when an incomplete export was shared; shown when the Library resumes after the share sheet. */
    private var incompleteExportPending = false

    init {
        viewModelScope.launch {
            query.drop(1).debounce(SEARCH_DEBOUNCE_MS).collect { appliedQuery.value = it }
        }
        viewModelScope.launch {
            query.collect { savedStateHandle[KEY_QUERY] = it }
        }
        viewModelScope.launch {
            status.collect { savedStateHandle[KEY_STATUS] = it?.name }
        }
        viewModelScope.launch {
            selectedId.collect { savedStateHandle[KEY_COLLECTION] = it }
        }
        viewModelScope.launch {
            // A deleted collection (from its menu or from Details) drops the Library back to All papers.
            collectionsRepository.observeCollections().collect { list ->
                val id = selectedId.value
                if (id != null && list.none { it.id == id }) selectedId.value = null
            }
        }
    }

    /** Decides Empty (nothing saved) versus NoMatches (nothing matches), whatever the search, chip and collection. */
    private val libraryIsEmpty = libraryRepository.observeStatusCounts("").map { counts -> counts.values.sum() == 0 }.distinctUntilChanged()

    private val results = combine(appliedQuery, status, selectedId, ::Triple).flatMapLatest { (applied, selected, collectionId) ->
        combine(
            libraryRepository.observeLibrary(applied, selected, collectionId),
            libraryRepository.observeStatusCounts(applied, collectionId),
            libraryRepository.observeStatusCounts("", collectionId)
        ) { papers, counts, unfiltered ->
            Results(applied, selected, collectionId, papers, counts, viewSize = unfiltered.values.sum())
        }
    }

    val uiState: StateFlow<LibraryUiState> =
        combine(libraryIsEmpty, results, query, status, collections) { empty, results, typed, selected, collectionList ->
            val filter = LibraryFilter(query = typed, status = selected, counts = results.counts)
            val counted = results.status?.let { results.counts[it] ?: 0 } ?: results.counts.values.sum()
            val collection = results.collectionId?.let { id -> collectionList.firstOrNull { it.id == id } }
            when {
                // With no search, chip or collection, an empty list is an empty library, even before the emptiness query answers.
                empty ||
                    (results.papers.isEmpty() && results.status == null && results.applied.isBlank() && results.collectionId == null) ->
                    LibraryUiState.Empty

                // The collection list hasn't caught up with the selection (or it was just deleted): keep the last state.
                results.collectionId != null && collection == null -> null

                collection != null && results.viewSize == 0 -> LibraryUiState.CollectionEmpty(collection)

                results.papers.isNotEmpty() -> LibraryUiState.Papers(results.papers, filter)

                counted == 0 -> LibraryUiState.NoMatches(filter)

                // The list and the counts are separate queries: an empty list the counts disagree with is stale, so keep the last state.
                else -> null
            }
        }.filterNotNull().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    val header: StateFlow<LibraryHeader> = combine(
        collections,
        selectedId,
        selectedId.flatMapLatest { id -> libraryRepository.observeStatusCounts("", id) }.map { it.values.sum() },
        libraryRepository.observeStatusCounts("").map { it.values.sum() },
        exporting
    ) { list, id, size, total, running ->
        LibraryHeader(
            collections = list,
            selected = list.firstOrNull { it.id == id },
            viewSize = size,
            libraryCount = total,
            exporting = running
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryHeader())

    private val _pendingUndo = MutableStateFlow<RemovedPaper?>(null)
    val pendingUndo: StateFlow<RemovedPaper?> = _pendingUndo.asStateFlow()

    private val _pendingCollectionUndo = MutableStateFlow<CollectionRemoval?>(null)
    val pendingCollectionUndo: StateFlow<CollectionRemoval?> = _pendingCollectionUndo.asStateFlow()

    private val _message = MutableStateFlow<LibraryMessage?>(null)
    val message: StateFlow<LibraryMessage?> = _message.asStateFlow()

    private val _dialog = MutableStateFlow<CollectionDialog?>(null)
    val dialog: StateFlow<CollectionDialog?> = _dialog.asStateFlow()

    private val _exportReady = MutableStateFlow<BibExport?>(null)

    /** A file for the screen to write and share; the screen then calls [onExportShared] or [onExportFailed]. */
    val exportReady: StateFlow<BibExport?> = _exportReady.asStateFlow()

    fun onQueryChange(text: String) {
        query.value = text
    }

    /** The keyboard's Search key: search now instead of after the pause. */
    fun onSearch() {
        appliedQuery.value = query.value
    }

    fun onClearQuery() {
        query.value = ""
        appliedQuery.value = ""
    }

    fun onStatusFilterChange(status: ReadingStatus?) {
        this.status.value = status
    }

    fun onClearSearchAndFilters() {
        onClearQuery()
        status.value = null
    }

    fun onSelectCollection(id: Long?) {
        selectedId.value = id
    }

    fun onStatusChange(paper: Paper, status: ReadingStatus) {
        viewModelScope.launch {
            try {
                libraryRepository.setStatus(paper.openAlexId, status)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = LibraryMessage.StatusUpdateFailed
            }
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    /** A swipe: out of the selected collection when one is selected, otherwise out of the library. Both with Undo. */
    fun onRemove(paper: Paper) {
        // collections is eager, so this is current even when nothing collects the header.
        val collection = collections.value.firstOrNull { it.id == selectedId.value }
        if (collection == null) {
            remove(paper.openAlexId)
            return
        }
        collectionChange {
            collectionsRepository.setMembership(collection.id, paper.openAlexId, member = false)
            _pendingCollectionUndo.value = CollectionRemoval(collection, paper.openAlexId)
        }
    }

    /**
     * Details' "Remove from library", handed back through the Library's back stack entry: removed with Undo, like a swipe in All papers.
     */
    fun onRemoveRequested(openAlexId: String) = remove(openAlexId)

    private fun remove(openAlexId: String) {
        viewModelScope.launch {
            _pendingUndo.value = libraryRepository.remove(openAlexId)
        }
    }

    fun onUndoRemove() {
        val removed = _pendingUndo.value ?: return
        _pendingUndo.value = null
        viewModelScope.launch { libraryRepository.restore(removed) }
    }

    fun onUndoDismissed() {
        _pendingUndo.value = null
    }

    fun onUndoCollectionRemove() {
        val removal = _pendingCollectionUndo.value ?: return
        _pendingCollectionUndo.value = null
        collectionChange { collectionsRepository.setMembership(removal.collection.id, removal.openAlexId, member = true) }
    }

    fun onCollectionUndoDismissed() {
        _pendingCollectionUndo.value = null
    }

    fun onNewCollection() {
        _dialog.value = CollectionDialog.New()
    }

    fun onRenameCollection(collection: PaperCollection) {
        _dialog.value = CollectionDialog.Rename(collection)
    }

    fun onDeleteCollection(collection: PaperCollection) {
        _dialog.value = CollectionDialog.ConfirmDelete(collection)
    }

    /** Typing clears the "already exists" error. */
    fun onDialogNameEdited() {
        _dialog.update { current ->
            when (current) {
                is CollectionDialog.New -> current.copy(nameTaken = false)
                is CollectionDialog.Rename -> current.copy(nameTaken = false)
                else -> current
            }
        }
    }

    fun onDialogConfirm(name: String) {
        val current = _dialog.value
        collectionChange(closeDialogOnFailure = true) {
            val result = when (current) {
                is CollectionDialog.New -> collectionsRepository.create(name)
                is CollectionDialog.Rename -> collectionsRepository.rename(current.collection.id, name)
                else -> return@collectionChange
            }
            when (result) {
                is CollectionResult.Done -> _dialog.value = null
                CollectionResult.NameTaken -> _dialog.value = when (current) {
                    is CollectionDialog.New -> current.copy(nameTaken = true)
                    is CollectionDialog.Rename -> current.copy(nameTaken = true)
                    else -> current
                }
                // The dialog's button is disabled for invalid names, so this only happens on a race; keep the dialog open.
                CollectionResult.InvalidName -> Unit
            }
        }
    }

    fun onConfirmDelete() {
        val current = _dialog.value as? CollectionDialog.ConfirmDelete ?: return
        _dialog.value = null
        collectionChange { collectionsRepository.delete(current.collection.id) }
    }

    fun onDialogDismiss() {
        _dialog.value = null
    }

    /**
     * Builds the .bib for the whole current collection (or library), ignoring the search and chip. A second tap while running does nothing.
     */
    fun onExport() {
        if (exporting.value) return
        exporting.value = true
        val collectionId = selectedId.value
        val name = collections.value.firstOrNull { it.id == collectionId }?.name
        viewModelScope.launch {
            try {
                val result = citationRepository.export(collectionId)
                _exportReady.value = BibExport(bibFileName(name.takeIf { collectionId != null }), result.bibtex, result.complete)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = LibraryMessage.ExportFailed
            } finally {
                exporting.value = false
            }
        }
    }

    fun onExportShared() {
        val shared = _exportReady.value ?: return
        _exportReady.value = null
        if (!shared.complete) incompleteExportPending = true
    }

    fun onExportFailed() {
        _exportReady.value = null
        _message.value = LibraryMessage.ExportFailed
    }

    /** The user is back from the share sheet: now is when "may be incomplete" can be read. */
    fun onScreenResumed() {
        if (!incompleteExportPending) return
        incompleteExportPending = false
        _message.value = LibraryMessage.ExportIncomplete
    }

    private fun collectionChange(closeDialogOnFailure: Boolean = false, change: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                change()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (closeDialogOnFailure) _dialog.value = null
                _message.value = LibraryMessage.CollectionsUpdateFailed
            }
        }
    }
}

/** The papers and counts for one applied search, chip and collection, kept together so the state is decided from one emission. */
private data class Results(
    val applied: String,
    val status: ReadingStatus?,
    val collectionId: Long?,
    val papers: List<LibraryPaper>,
    val counts: Map<ReadingStatus, Int>,
    /** Papers in the collection (or library) ignoring the search and chip. */
    val viewSize: Int
)
```

`combine` with five flows is the largest typed overload; `uiState` and `header` use exactly five.

- [ ] **Step 7: Run the Library tests**

Run: `./gradlew :feature:library:testDebugUnitTest`
Expected: PASS, including the existing `LibraryViewModelTest` and `LibrarySwipeUndoTest` (they use All papers, which behaves as before). `LibraryContentTest` and `LibraryScreenshotTest` still compile because `LibraryContent` hasn't changed yet.

- [ ] **Step 8: Commit**

```bash
./gradlew spotlessApply
git add feature/library
git commit -m "feat: filter the Library by collection and export it"
```

---
### Task 12: Library screen: selector sheet, dialogs, export and sharing

**Files:**
- Modify: `feature/library/build.gradle.kts`
- Create: `feature/library/src/main/AndroidManifest.xml`
- Create: `feature/library/src/main/res/xml/bib_export_paths.xml`
- Create: `feature/library/src/main/java/com/etatech/hashiya/feature/library/export/BibExportFile.kt`
- Create: `feature/library/src/main/java/com/etatech/hashiya/feature/library/components/CollectionSelectorSheet.kt`
- Modify: `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryActions.kt`
- Modify: `feature/library/src/main/java/com/etatech/hashiya/feature/library/LibraryScreen.kt`
- Modify: `feature/library/src/main/res/values/strings.xml`, `feature/library/src/main/res/values-ar/strings.xml`
- Test: `feature/library/src/test/java/com/etatech/hashiya/feature/library/export/BibExportFileTest.kt` (new)
- Test: `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryCollectionsContentTest.kt` (new)
- Test: `feature/library/src/test/java/com/etatech/hashiya/feature/library/LibraryScreenshotTest.kt`

**Interfaces:**
- Consumes: everything Task 11 produces; `CollectionNameDialog` and icons (Task 10); `PaperCollection` (Task 1).
- Produces:
  - `internal fun writeBibFileTo(directory: File, export: BibExport): File`
  - `internal fun bibShareIntent(uri: Uri, fileName: String): Intent`
  - `internal const val BIB_MIME_TYPE = "text/x-bibtex"`, `internal const val EXPORT_PROGRESS_TAG = "export_progress"`
  - `LibraryContent(uiState, pendingUndo, actions, modifier, message, header = LibraryHeader(), dialog = null, pendingCollectionUndo = null)`

- [ ] **Step 1: Add the strings**

`values/strings.xml`:

```xml
    <string name="library_all_papers">All papers</string>
    <string name="library_choose_collection">Choose a collection</string>
    <string name="library_new_collection">New collection</string>
    <string name="library_collection_options">Options for %1$s</string>
    <string name="library_rename">Rename</string>
    <string name="library_delete">Delete</string>
    <string name="library_delete_collection_title">Delete \"%1$s\"?</string>
    <string name="library_delete_collection_message">Its papers stay in your library.</string>
    <string name="library_collection_empty">No papers in this collection yet. Add papers from their details screen.</string>
    <string name="library_removed_from_collection">Removed from %1$s</string>
    <string name="library_export_bib">Export .bib</string>
    <string name="library_export_failed">Couldn\'t export</string>
    <string name="library_export_incomplete">Some entries may be incomplete. Export again when you\'re online.</string>
    <string name="library_collections_update_failed">Couldn\'t update collections</string>
```

`values-ar/strings.xml` (the U+200E left-to-right mark before `.bib` keeps the dot on the right side of "bib"):

```xml
    <string name="library_all_papers">كل الأوراق</string>
    <string name="library_choose_collection">اختيار مجموعة</string>
    <string name="library_new_collection">مجموعة جديدة</string>
    <string name="library_collection_options">خيارات %1$s</string>
    <string name="library_rename">إعادة التسمية</string>
    <string name="library_delete">حذف</string>
    <string name="library_delete_collection_title">حذف «%1$s»؟</string>
    <string name="library_delete_collection_message">ستبقى أوراقها في مكتبتك.</string>
    <string name="library_collection_empty">لا توجد أوراق في هذه المجموعة بعد. أضف الأوراق من شاشة تفاصيلها.</string>
    <string name="library_removed_from_collection">أُزيلت من %1$s</string>
    <string name="library_export_bib">تصدير ملف &#x200E;.bib</string>
    <string name="library_export_failed">تعذّر التصدير</string>
    <string name="library_export_incomplete">قد تكون بعض المداخل ناقصة. أعد التصدير عند الاتصال بالإنترنت.</string>
    <string name="library_collections_update_failed">تعذّر تحديث المجموعات</string>
```

- [ ] **Step 2: Declare the FileProvider**

`feature/library/build.gradle.kts`, add:

```kotlin
dependencies {
    implementation(libs.androidx.core.ktx)
}
```

`feature/library/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <!-- Shares exported .bib files from cacheDir/exports without a storage permission. -->
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.exports"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/bib_export_paths" />
        </provider>
    </application>
</manifest>
```

`feature/library/src/main/res/xml/bib_export_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="exports" path="exports/" />
</paths>
```

- [ ] **Step 3: Write the failing file test**

```kotlin
package com.etatech.hashiya.feature.library.export

import android.content.Intent
import android.net.Uri
import com.etatech.hashiya.feature.library.BibExport
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BibExportFileTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun writesUtf8AndRemovesEarlierExports() {
        val dir = File(temp.root, "exports")
        writeBibFileTo(dir, BibExport("old.bib", "@misc{a,\n}\n", complete = true))

        val file = writeBibFileTo(dir, BibExport("الفصل.bib", "@misc{paper2019,\n  title = {تعلم}\n}\n", complete = true))

        assertEquals(listOf("الفصل.bib"), dir.list()!!.toList())
        assertEquals("@misc{paper2019,\n  title = {تعلم}\n}\n", file.readText(Charsets.UTF_8))
    }

    @Test
    fun shareIntentSendsTheFileWithReadPermission() {
        val uri = Uri.parse("content://com.etatech.hashiya.exports/exports/Thesis.bib")
        val intent = bibShareIntent(uri, "Thesis.bib")

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(BIB_MIME_TYPE, intent.type)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
    }
}
```

- [ ] **Step 4: Implement `BibExportFile.kt`**

```kotlin
package com.etatech.hashiya.feature.library.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.etatech.hashiya.feature.library.BibExport
import java.io.File

internal const val BIB_MIME_TYPE = "text/x-bibtex"
private const val EXPORTS_DIRECTORY = "exports"

/** Writes [export] into [directory], deleting earlier exports first, so the cache never holds more than the latest file. */
internal fun writeBibFileTo(directory: File, export: BibExport): File {
    directory.deleteRecursively()
    directory.mkdirs()
    return File(directory, export.fileName).apply { writeText(export.bibtex, Charsets.UTF_8) }
}

/** Writes the file to cacheDir/exports and returns a content Uri the share target can read (FileProvider in the manifest). */
internal fun writeBibFile(context: Context, export: BibExport): Uri {
    val file = writeBibFileTo(File(context.cacheDir, EXPORTS_DIRECTORY), export)
    return FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
}

internal fun bibShareIntent(uri: Uri, fileName: String): Intent = Intent(Intent.ACTION_SEND).apply {
    type = BIB_MIME_TYPE
    putExtra(Intent.EXTRA_STREAM, uri)
    putExtra(Intent.EXTRA_TITLE, fileName)
    // ClipData carries the read grant through the chooser to the app the user picks.
    clipData = ClipData.newRawUri(fileName, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
```

Run: `./gradlew :feature:library:testDebugUnitTest --tests '*BibExportFileTest*'`
Expected: PASS. (`getParcelableExtra(name, Class)` needs API 33; the Robolectric SDK is 35.)

- [ ] **Step 5: Extend `LibraryActions`**

Add to the data class (all no-op defaults):

```kotlin
    val onSelectCollection: (Long?) -> Unit = {},
    val onNewCollection: () -> Unit = {},
    val onRenameCollection: (PaperCollection) -> Unit = {},
    val onDeleteCollection: (PaperCollection) -> Unit = {},
    val onDialogNameEdited: () -> Unit = {},
    val onDialogConfirm: (String) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDialogDismiss: () -> Unit = {},
    val onUndoCollection: () -> Unit = {},
    val onCollectionUndoDismissed: () -> Unit = {},
    val onExport: () -> Unit = {},
```

(import `com.etatech.hashiya.core.model.PaperCollection`).

- [ ] **Step 6: Write the failing content tests**

```kotlin
package com.etatech.hashiya.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class LibraryCollectionsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()
    private val actions = LibraryActions(
        onSelectCollection = { events += "select:$it" },
        onNewCollection = { events += "new" },
        onRenameCollection = { events += "rename:${it.name}" },
        onDeleteCollection = { events += "delete:${it.name}" },
        onDialogConfirm = { events += "confirm:$it" },
        onConfirmDelete = { events += "confirmDelete" },
        onUndoCollection = { events += "undoCollection" },
        onExport = { events += "export" }
    )
    private val thesis = PaperCollection(1, "Thesis", 2)
    private val papers = LibraryUiState.Papers(
        listOf(LibraryPaper(SamplePapers.bert, ReadingStatus.ToRead)),
        LibraryFilter(counts = mapOf(ReadingStatus.ToRead to 1, ReadingStatus.Reading to 0, ReadingStatus.Read to 0))
    )

    private fun show(
        state: LibraryUiState = papers,
        header: LibraryHeader = LibraryHeader(collections = listOf(thesis), viewSize = 3, libraryCount = 3),
        dialog: CollectionDialog? = null,
        collectionUndo: CollectionRemoval? = null
    ) = composeRule.setContent {
        HashiyaTheme {
            LibraryContent(
                uiState = state,
                pendingUndo = null,
                actions = actions,
                header = header,
                dialog = dialog,
                pendingCollectionUndo = collectionUndo
            )
        }
    }

    @Test
    fun selectorListsAllPapersAndCollectionsAndSelects() {
        show()

        composeRule.onNodeWithText("All papers").performClick()
        composeRule.onNodeWithText("Thesis").assertIsDisplayed()
        composeRule.onNodeWithText("New collection").assertIsDisplayed()
        composeRule.onNodeWithText("Thesis").performClick()

        assertEquals(listOf("select:1"), events)
    }

    @Test
    fun selectorRowMenuRenamesAndDeletes() {
        show()
        composeRule.onNodeWithText("All papers").performClick()

        composeRule.onNodeWithContentDescription("Options for Thesis").performClick()
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithText("All papers").performClick()
        composeRule.onNodeWithContentDescription("Options for Thesis").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(listOf("rename:Thesis", "delete:Thesis"), events)
    }

    @Test
    fun newCollectionFromTheSelector() {
        show()
        composeRule.onNodeWithText("All papers").performClick()
        composeRule.onNodeWithText("New collection").performClick()
        assertEquals(listOf("new"), events)
    }

    @Test
    fun selectedCollectionIsTheTitle() {
        show(header = LibraryHeader(collections = listOf(thesis), selected = thesis, viewSize = 2, libraryCount = 3))
        composeRule.onNodeWithText("Thesis").assertIsDisplayed()
    }

    @Test
    fun exportIsOfferedOnlyWhenTheViewHasPapersAndShowsProgress() {
        var header by mutableStateOf(LibraryHeader(viewSize = 3, libraryCount = 3))
        composeRule.setContent {
            HashiyaTheme { LibraryContent(uiState = papers, pendingUndo = null, actions = actions, header = header) }
        }
        composeRule.onNodeWithContentDescription("Export .bib").performClick()
        assertEquals(listOf("export"), events)

        header = header.copy(exporting = true)
        composeRule.onNodeWithTag(EXPORT_PROGRESS_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Export .bib").assertDoesNotExist()

        header = LibraryHeader(viewSize = 0, libraryCount = 3)
        composeRule.onNodeWithTag(EXPORT_PROGRESS_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Export .bib").assertDoesNotExist()
    }

    @Test
    fun emptyCollectionExplainsHowToAddPapersWithoutSearch() {
        show(state = LibraryUiState.CollectionEmpty(thesis.copy(paperCount = 0)))
        composeRule.onNodeWithText("No papers in this collection yet. Add papers from their details screen.").assertIsDisplayed()
        composeRule.onNodeWithText("Search your library").assertDoesNotExist()
    }

    @Test
    fun removedFromCollectionSnackbarOffersUndo() {
        show(collectionUndo = CollectionRemoval(thesis, SamplePapers.bert.openAlexId))
        composeRule.onNodeWithText("Removed from Thesis").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("undoCollection"), events)
    }

    @Test
    fun deleteAsksForConfirmation() {
        show(dialog = CollectionDialog.ConfirmDelete(thesis))
        composeRule.onNodeWithText("Delete \"Thesis\"?").assertIsDisplayed()
        composeRule.onNodeWithText("Its papers stay in your library.").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(listOf("confirmDelete"), events)
    }

    @Test
    fun nameDialogConfirmsTheTypedName() {
        show(dialog = CollectionDialog.Rename(thesis))
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(listOf("confirm:Thesis"), events)
    }
}
```


- [ ] **Step 7: Run them to see them fail**

Run: `./gradlew :feature:library:testDebugUnitTest --tests '*LibraryCollectionsContentTest*'`
Expected: compilation FAIL (`LibraryContent` has no `header` parameter).

- [ ] **Step 8: Write `CollectionSelectorSheet.kt`**

```kotlin
package com.etatech.hashiya.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.feature.library.LibraryHeader
import com.etatech.hashiya.feature.library.R

/** "All papers", then each collection with its count and a Rename/Delete menu, then "New collection". Every choice closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionSelectorSheet(
    header: LibraryHeader,
    onSelect: (Long?) -> Unit,
    onNew: () -> Unit,
    onRename: (PaperCollection) -> Unit,
    onDelete: (PaperCollection) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.navigationBarsPadding()) {
            item {
                SelectorRow(
                    name = stringResource(R.string.library_all_papers),
                    count = header.libraryCount,
                    selected = header.selected == null,
                    onClick = {
                        onSelect(null)
                        onDismiss()
                    }
                )
            }
            items(header.collections, key = { it.id }) { collection ->
                SelectorRow(
                    name = collection.name,
                    count = collection.paperCount,
                    selected = header.selected?.id == collection.id,
                    onClick = {
                        onSelect(collection.id)
                        onDismiss()
                    },
                    menu = {
                        CollectionMenu(
                            name = collection.name,
                            onRename = {
                                onRename(collection)
                                onDismiss()
                            },
                            onDelete = {
                                onDelete(collection)
                                onDismiss()
                            }
                        )
                    }
                )
            }
            item {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_new_collection)) },
                    leadingContent = { Icon(HashiyaIcons.Add, contentDescription = null) },
                    modifier = Modifier.clickable(role = Role.Button) {
                        onNew()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun SelectorRow(name: String, count: Int, selected: Boolean, onClick: () -> Unit, menu: (@Composable () -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = { Text(pluralStringResource(R.plurals.library_paper_count, count, count)) },
        leadingContent = { Icon(if (selected) HashiyaIcons.Check else HashiyaIcons.Collection, contentDescription = null) },
        trailingContent = menu,
        modifier = Modifier
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick)
    )
}

@Composable
private fun CollectionMenu(name: String, onRename: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(HashiyaIcons.MoreOptions, contentDescription = stringResource(R.string.library_collection_options, name))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.library_rename)) }, onClick = {
                expanded = false
                onRename()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.library_delete)) }, onClick = {
                expanded = false
                onDelete()
            })
        }
    }
}
```

- [ ] **Step 9: Change `LibraryScreen.kt`**

In `LibraryScreen(...)`, collect the new state and wire the actions and the share:

```kotlin
    val header by viewModel.header.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val pendingCollectionUndo by viewModel.pendingCollectionUndo.collectAsStateWithLifecycle()
    val exportReady by viewModel.exportReady.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Coming back from the share sheet is when "may be incomplete" can be read.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }
    LaunchedEffect(exportReady) {
        val export = exportReady ?: return@LaunchedEffect
        try {
            val uri = withContext(Dispatchers.IO) { writeBibFile(context, export) }
            context.startActivity(Intent.createChooser(bibShareIntent(uri, export.fileName), null))
            viewModel.onExportShared()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            viewModel.onExportFailed()
        }
    }
```

and pass to `LibraryContent`:

```kotlin
        header = header,
        dialog = dialog,
        pendingCollectionUndo = pendingCollectionUndo,
```

plus these in the `LibraryActions(...)` there:

```kotlin
            onSelectCollection = viewModel::onSelectCollection,
            onNewCollection = viewModel::onNewCollection,
            onRenameCollection = viewModel::onRenameCollection,
            onDeleteCollection = viewModel::onDeleteCollection,
            onDialogNameEdited = viewModel::onDialogNameEdited,
            onDialogConfirm = viewModel::onDialogConfirm,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDialogDismiss = viewModel::onDialogDismiss,
            onUndoCollection = viewModel::onUndoCollectionRemove,
            onCollectionUndoDismissed = viewModel::onCollectionUndoDismissed,
            onExport = viewModel::onExport,
```

(imports: `android.content.Intent`, `androidx.compose.ui.platform.LocalContext`, `androidx.lifecycle.Lifecycle`, `androidx.lifecycle.compose.LifecycleEventEffect`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.withContext`, `kotlin.coroutines.cancellation.CancellationException`, `com.etatech.hashiya.feature.library.export.bibShareIntent`, `com.etatech.hashiya.feature.library.export.writeBibFile`).

Change `LibraryContent`'s signature to:

```kotlin
internal fun LibraryContent(
    uiState: LibraryUiState,
    pendingUndo: RemovedPaper?,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
    message: LibraryMessage? = null,
    header: LibraryHeader = LibraryHeader(),
    dialog: CollectionDialog? = null,
    pendingCollectionUndo: CollectionRemoval? = null
)
```

Inside it, add after the existing undo `LaunchedEffect`:

```kotlin
    val removedFromCollection = pendingCollectionUndo?.let { stringResource(R.string.library_removed_from_collection, it.collection.name) }
    LaunchedEffect(pendingCollectionUndo) {
        if (pendingCollectionUndo != null && removedFromCollection != null) {
            val result = snackbarHostState.showSnackbar(removedFromCollection, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) actions.onUndoCollection() else actions.onCollectionUndoDismissed()
        }
    }
```

Replace the message `LaunchedEffect` with:

```kotlin
    val statusUpdateFailed = stringResource(R.string.library_status_update_failed)
    val collectionsUpdateFailed = stringResource(R.string.library_collections_update_failed)
    val exportFailed = stringResource(R.string.library_export_failed)
    val exportIncomplete = stringResource(R.string.library_export_incomplete)
    LaunchedEffect(message) {
        val (text, duration) = when (message) {
            LibraryMessage.StatusUpdateFailed -> statusUpdateFailed to SnackbarDuration.Short
            LibraryMessage.CollectionsUpdateFailed -> collectionsUpdateFailed to SnackbarDuration.Short
            LibraryMessage.ExportFailed -> exportFailed to SnackbarDuration.Short
            LibraryMessage.ExportIncomplete -> exportIncomplete to SnackbarDuration.Long
            null -> return@LaunchedEffect
        }
        snackbarHostState.showSnackbar(text, duration = duration)
        actions.onMessageShown()
    }
```

Before `Scaffold`, add the sheet and dialog state:

```kotlin
    var selectorOpen by rememberSaveable { mutableStateOf(false) }
    val showsCollections = uiState !is LibraryUiState.Empty && uiState !is LibraryUiState.Loading
    if (selectorOpen && showsCollections) {
        CollectionSelectorSheet(
            header = header,
            onSelect = actions.onSelectCollection,
            onNew = actions.onNewCollection,
            onRename = actions.onRenameCollection,
            onDelete = actions.onDeleteCollection,
            onDismiss = { selectorOpen = false }
        )
    }
    CollectionDialogs(dialog, actions)
```

Replace the `TopAppBar(...)` with:

```kotlin
            TopAppBar(
                title = {
                    if (showsCollections) {
                        val name = header.selected?.name ?: stringResource(R.string.library_all_papers)
                        CollectionTitle(name, onClick = { selectorOpen = true })
                    } else {
                        Text(stringResource(R.string.library_title))
                    }
                },
                actions = {
                    if (showsCollections && header.viewSize > 0) {
                        if (header.exporting) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .size(24.dp)
                                    .testTag(EXPORT_PROGRESS_TAG)
                            )
                        } else {
                            IconButton(onClick = actions.onExport) {
                                Icon(HashiyaIcons.Export, contentDescription = stringResource(R.string.library_export_bib))
                            }
                        }
                    }
                    IconButton(onClick = actions.onOpenSettings) {
                        Icon(HashiyaIcons.Settings, contentDescription = stringResource(R.string.library_settings))
                    }
                }
            )
```

In the body, the `filter` `when` gains `is LibraryUiState.CollectionEmpty -> null` next to Loading/Empty, and the content `when` gains:

```kotlin
                    // No action: papers are added to a collection from their Details screen.
                    is LibraryUiState.CollectionEmpty -> EmptyState(
                        icon = HashiyaIcons.Collection,
                        title = stringResource(R.string.library_collection_empty),
                        message = null
                    )
```

Add at the bottom of the file:

```kotlin
internal const val EXPORT_PROGRESS_TAG = "export_progress"

@Composable
private fun CollectionTitle(name: String, onClick: () -> Unit) {
    val chooseLabel = stringResource(R.string.library_choose_collection)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClickLabel = chooseLabel, role = Role.Button, onClick = onClick)
    ) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Icon(HashiyaIcons.ArrowDropDown, contentDescription = null)
    }
}

@Composable
private fun CollectionDialogs(dialog: CollectionDialog?, actions: LibraryActions) {
    when (dialog) {
        null -> Unit

        is CollectionDialog.New -> key("new") {
            CollectionNameDialog(
                initialName = null,
                nameTaken = dialog.nameTaken,
                onNameEdited = actions.onDialogNameEdited,
                onConfirm = actions.onDialogConfirm,
                onDismiss = actions.onDialogDismiss
            )
        }

        is CollectionDialog.Rename -> key("rename", dialog.collection.id) {
            CollectionNameDialog(
                initialName = dialog.collection.name,
                nameTaken = dialog.nameTaken,
                onNameEdited = actions.onDialogNameEdited,
                onConfirm = actions.onDialogConfirm,
                onDismiss = actions.onDialogDismiss
            )
        }

        is CollectionDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = actions.onDialogDismiss,
            title = { Text(stringResource(R.string.library_delete_collection_title, dialog.collection.name)) },
            text = { Text(stringResource(R.string.library_delete_collection_message)) },
            confirmButton = { TextButton(onClick = actions.onConfirmDelete) { Text(stringResource(R.string.library_delete)) } },
            dismissButton = { TextButton(onClick = actions.onDialogDismiss) { Text(stringResource(DesignR.string.collection_cancel)) } }
        )
    }
}
```

(imports: `androidx.compose.foundation.layout.size`, `androidx.compose.material3.AlertDialog`, `androidx.compose.material3.CircularProgressIndicator`, `androidx.compose.material3.TextButton`, `androidx.compose.runtime.key`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.saveable.rememberSaveable`, `androidx.compose.runtime.setValue`, `androidx.compose.ui.platform.testTag`, `androidx.compose.ui.semantics.Role`, `com.etatech.hashiya.core.designsystem.R as DesignR`, `com.etatech.hashiya.core.designsystem.component.CollectionNameDialog`, `com.etatech.hashiya.feature.library.components.CollectionSelectorSheet`.)

- [ ] **Step 10: Run the Library tests**

Run: `./gradlew :feature:library:testDebugUnitTest`
Expected: PASS. Locally, screenshot tests only capture; they are verified against baselines only on CI (`-Proborazzi.test.verify=true`). The top bar now shows the selector and Export, so `library_papers`, `library_status_menu` and the other Library baselines change on purpose; Task 14 re-records them on CI.

- [ ] **Step 11: Update and add screenshot tests**

In `LibraryScreenshotTest.kt`:

- Extend the `capture` helper with `header: LibraryHeader = LibraryHeader(viewSize = 3, libraryCount = 3)` and `dialog: CollectionDialog? = null` and pass them to `LibraryContent`.
- In `papers()` and `statusMenu()`, change `arabicText = "المكتبة"` to `arabicText = "كل الأوراق"` (the title is now the selector).
- Add:

```kotlin
    private val thesis = PaperCollection(1, "Thesis", 2)
    private val chapter = PaperCollection(2, "الفصل الثاني", 1)
    private val collectionsHeader = LibraryHeader(collections = listOf(chapter, thesis), selected = thesis, viewSize = 2, libraryCount = 3)

    @Test
    fun collectionSelector() = capture(
        "library_collection_selector",
        library,
        arabicText = "مجموعة جديدة",
        wholeScreen = true,
        header = collectionsHeader
    ) {
        onNodeWithText("Thesis").performClick()
    }

    @Test
    fun filteredByCollection() = capture("library_collection_filtered", library, arabicText = "الكل", header = collectionsHeader)

    @Test
    fun emptyCollection() = capture(
        "library_collection_empty",
        LibraryUiState.CollectionEmpty(PaperCollection(3, "Empty", 0)),
        arabicText = "لا توجد أوراق في هذه المجموعة بعد. أضف الأوراق من شاشة تفاصيلها.",
        header = LibraryHeader(
            collections = listOf(PaperCollection(3, "Empty", 0)),
            selected = PaperCollection(3, "Empty", 0),
            libraryCount = 3
        )
    )

    @Test
    fun nameTaken() = capture(
        "library_collection_name_taken",
        library,
        arabicText = "توجد مجموعة بهذا الاسم بالفعل",
        wholeScreen = true,
        dialog = CollectionDialog.New(nameTaken = true)
    )
```

(imports: `androidx.compose.ui.test.onNodeWithText`, `com.etatech.hashiya.core.model.PaperCollection`).

These new tests have no baselines yet; Task 14 records them on CI.

- [ ] **Step 12: Commit**

```bash
./gradlew spotlessApply
git add feature/library core/designsystem
git commit -m "feat: add the Library collection selector, collection dialogs and .bib export"
```

---
### Task 13: Details: Collections row, checklist sheet and Copy BibTeX

**Files:**
- Modify: `feature/paperdetails/src/main/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsUiState.kt`
- Modify: `feature/paperdetails/src/main/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsViewModel.kt`
- Modify: `feature/paperdetails/src/main/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsActions.kt`
- Modify: `feature/paperdetails/src/main/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsScreen.kt`
- Create: `feature/paperdetails/src/main/java/com/etatech/hashiya/feature/paperdetails/CollectionsRow.kt`
- Create: `feature/paperdetails/src/main/java/com/etatech/hashiya/feature/paperdetails/CollectionChecklistSheet.kt`
- Create: `feature/paperdetails/src/main/java/com/etatech/hashiya/feature/paperdetails/CopyConfirmation.kt`
- Modify: `feature/paperdetails/src/main/res/values/strings.xml`, `feature/paperdetails/src/main/res/values-ar/strings.xml`
- Test: `feature/paperdetails/src/test/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsViewModelTest.kt` (constructor)
- Test: `feature/paperdetails/src/test/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsCollectionsViewModelTest.kt` (new)
- Test: `feature/paperdetails/src/test/java/com/etatech/hashiya/feature/paperdetails/CopyConfirmationTest.kt` (new)
- Test: `feature/paperdetails/src/test/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsCollectionsContentTest.kt` (new)
- Test: `feature/paperdetails/src/test/java/com/etatech/hashiya/feature/paperdetails/PaperDetailsScreenshotTest.kt`

**Interfaces:**
- Consumes: `CollectionsRepository`, `CollectionResult` (Task 7); `CitationRepository` (Task 8); fakes (Task 9); `CollectionNameDialog`, `HashiyaIcons.Copy`, `HashiyaIcons.Collection` (Task 10).
- Produces:
  - `PaperDetailsViewModel(savedStateHandle, libraryRepository, collectionsRepository, citationRepository, applicationScope)`
  - `PaperDetailsUiState.Loaded(paper, notes, saveState, collections: List<PaperCollection> = emptyList(), memberOf: Set<Long> = emptySet())`
  - `data class NewCollectionDialog(val nameTaken: Boolean = false)`, `data class CopiedBibTeX(val text: String, val complete: Boolean)`
  - `enum class PaperDetailsMessage { NotesSaveFailed, StatusUpdateFailed, CollectionsUpdateFailed, BibTeXCopied, BibTeXIncomplete, CopyFailed }`
  - ViewModel: `newCollectionDialog`, `copied` (StateFlows); `onToggleCollection(id: Long, member: Boolean)`, `onNewCollection()`, `onNewCollectionNameEdited()`, `onNewCollectionConfirm(name: String)`, `onNewCollectionDismiss()`, `onCopyBibTeX()`, `onCopyHandled(confirmation: PaperDetailsMessage?)`
  - `internal fun copyConfirmation(complete: Boolean, sdkInt: Int): PaperDetailsMessage?`
  - `internal const val COLLECTIONS_ROW_TAG = "collections_row"`

- [ ] **Step 1: Add the strings**

`values/strings.xml`:

```xml
    <string name="details_collections">Collections</string>
    <string name="details_no_collections">Not in any collection</string>
    <string name="details_collections_hint">Group papers for a chapter, a course or a project.</string>
    <string name="details_new_collection">New collection</string>
    <string name="details_copy_bibtex">Copy BibTeX</string>
    <string name="details_bibtex_copied">BibTeX copied</string>
    <string name="details_bibtex_incomplete">Some details may be missing. Copy again when you\'re online.</string>
    <string name="details_collections_update_failed">Couldn\'t update collections</string>
    <string name="details_copy_failed">Couldn\'t copy BibTeX</string>
```

`values-ar/strings.xml`:

```xml
    <string name="details_collections">المجموعات</string>
    <string name="details_no_collections">ليست في أي مجموعة</string>
    <string name="details_collections_hint">اجمع الأوراق لفصل أو مقرر أو مشروع.</string>
    <string name="details_new_collection">مجموعة جديدة</string>
    <string name="details_copy_bibtex">نسخ BibTeX</string>
    <string name="details_bibtex_copied">تم نسخ BibTeX</string>
    <string name="details_bibtex_incomplete">قد تنقص بعض البيانات. انسخ مرة أخرى عند الاتصال بالإنترنت.</string>
    <string name="details_collections_update_failed">تعذّر تحديث المجموعات</string>
    <string name="details_copy_failed">تعذّر نسخ BibTeX</string>
```

- [ ] **Step 2: Write the failing confirmation test and implement it**

`CopyConfirmationTest.kt`:

```kotlin
package com.etatech.hashiya.feature.paperdetails

import org.junit.Assert.assertEquals
import org.junit.Test

class CopyConfirmationTest {
    @Test
    fun incompleteAlwaysSaysSo() {
        assertEquals(PaperDetailsMessage.BibTeXIncomplete, copyConfirmation(complete = false, sdkInt = 32))
        assertEquals(PaperDetailsMessage.BibTeXIncomplete, copyConfirmation(complete = false, sdkInt = 35))
    }

    @Test
    fun copiedIsShownOnlyBelowAndroid13() {
        assertEquals(PaperDetailsMessage.BibTeXCopied, copyConfirmation(complete = true, sdkInt = 32))
        assertEquals(null, copyConfirmation(complete = true, sdkInt = 33))
    }
}
```

`CopyConfirmation.kt`:

```kotlin
package com.etatech.hashiya.feature.paperdetails

private const val SYSTEM_CLIPBOARD_CONFIRMATION_SDK = 33

/** What to show after copying: Android 13 and later confirm copies themselves, so only older versions get "BibTeX copied". */
internal fun copyConfirmation(complete: Boolean, sdkInt: Int): PaperDetailsMessage? = when {
    !complete -> PaperDetailsMessage.BibTeXIncomplete
    sdkInt < SYSTEM_CLIPBOARD_CONFIRMATION_SDK -> PaperDetailsMessage.BibTeXCopied
    else -> null
}
```

In `PaperDetailsUiState.kt`, change:

```kotlin
sealed interface PaperDetailsUiState {
    data object Loading : PaperDetailsUiState

    data class Loaded(
        val paper: LibraryPaper,
        val notes: PaperNotes,
        val saveState: NotesSaveState,
        /** Every collection, for the checklist. */
        val collections: List<PaperCollection> = emptyList(),
        /** The collections this paper is in. */
        val memberOf: Set<Long> = emptySet()
    ) : PaperDetailsUiState
}
```

and

```kotlin
enum class PaperDetailsMessage { NotesSaveFailed, StatusUpdateFailed, CollectionsUpdateFailed, BibTeXCopied, BibTeXIncomplete, CopyFailed }

/** The "New collection" dialog opened from the checklist. */
data class NewCollectionDialog(val nameTaken: Boolean = false)

/** An entry for the screen to put on the clipboard; the screen then calls onCopyHandled. */
data class CopiedBibTeX(val text: String, val complete: Boolean)
```

(import `com.etatech.hashiya.core.model.PaperCollection`).

Run: `./gradlew :feature:paperdetails:testDebugUnitTest --tests '*CopyConfirmationTest*'`
Expected: PASS once the existing `when (message)` in `PaperDetailsScreen.kt` compiles: add a temporary `else -> Unit` branch there; Step 7 replaces it.

- [ ] **Step 3: Write the failing ViewModel tests**

In `PaperDetailsViewModelTest.kt`, change the constructor call to:

```kotlin
        val viewModel = PaperDetailsViewModel(
            SavedStateHandle(mapOf(ARG_OPEN_ALEX_ID to id)),
            libraryRepository,
            FakeCollectionsRepository(repository),
            FakeCitationRepository(),
            backgroundScope
        )
```

(imports from `com.etatech.hashiya.core.testing`).

`PaperDetailsCollectionsViewModelTest.kt`:

```kotlin
package com.etatech.hashiya.feature.paperdetails

import androidx.lifecycle.SavedStateHandle
import com.etatech.hashiya.core.data.repository.CollectionResult
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.core.testing.FakeCitationRepository
import com.etatech.hashiya.core.testing.FakeCollectionsRepository
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import com.etatech.hashiya.core.testing.SamplePapers
import java.io.IOException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class PaperDetailsCollectionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val collections = FakeCollectionsRepository(library)
    private val citations = FakeCitationRepository()
    private val paper = SamplePapers.bert
    private val id = paper.openAlexId

    private fun TestScope.viewModel(): PaperDetailsViewModel {
        val handle = SavedStateHandle(mapOf(ARG_OPEN_ALEX_ID to id))
        val viewModel = PaperDetailsViewModel(handle, library, collections, citations, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        return viewModel
    }

    private fun PaperDetailsViewModel.loaded() = uiState.value as PaperDetailsUiState.Loaded

    @Test
    fun loadedStateCarriesCollectionsAndMembership() = runTest {
        library.save(paper)
        val a = (collections.create("A") as CollectionResult.Done).id
        val b = (collections.create("B") as CollectionResult.Done).id
        collections.setMembership(b, id, true)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(PaperCollection(a, "A", 0), PaperCollection(b, "B", 1)), viewModel.loaded().collections)
        assertEquals(setOf(b), viewModel.loaded().memberOf)
    }

    @Test
    fun togglingChangesMembership() = runTest {
        library.save(paper)
        val a = (collections.create("A") as CollectionResult.Done).id
        val viewModel = viewModel()

        viewModel.onToggleCollection(a, member = true)
        advanceUntilIdle()
        assertEquals(setOf(a), viewModel.loaded().memberOf)

        viewModel.onToggleCollection(a, member = false)
        advanceUntilIdle()
        assertEquals(emptySet<Long>(), viewModel.loaded().memberOf)
    }

    @Test
    fun aFailedToggleKeepsTheStoredStateAndSaysSo() = runTest {
        library.save(paper)
        val a = (collections.create("A") as CollectionResult.Done).id
        val viewModel = viewModel()
        collections.failOnChange = true

        viewModel.onToggleCollection(a, member = true)
        advanceUntilIdle()

        assertEquals(emptySet<Long>(), viewModel.loaded().memberOf)
        assertEquals(PaperDetailsMessage.CollectionsUpdateFailed, viewModel.message.value)
    }

    @Test
    fun newCollectionIsCreatedWithThePaperInIt() = runTest {
        library.save(paper)
        collections.create("Thesis")
        val viewModel = viewModel()

        viewModel.onNewCollection()
        assertEquals(NewCollectionDialog(), viewModel.newCollectionDialog.value)
        viewModel.onNewCollectionConfirm("thesis")
        advanceUntilIdle()
        assertEquals(NewCollectionDialog(nameTaken = true), viewModel.newCollectionDialog.value)
        viewModel.onNewCollectionNameEdited()
        assertEquals(NewCollectionDialog(), viewModel.newCollectionDialog.value)

        viewModel.onNewCollectionConfirm("Chapter 2")
        advanceUntilIdle()
        assertNull(viewModel.newCollectionDialog.value)
        val chapter = viewModel.loaded().collections.first { it.name == "Chapter 2" }
        assertEquals(setOf(chapter.id), viewModel.loaded().memberOf)
    }

    @Test
    fun copyBibTeXHandsTheEntryToTheScreen() = runTest {
        library.save(paper)
        citations.entries = mapOf(id to "@inproceedings{devlin2019bert,\n}\n")
        val viewModel = viewModel()

        viewModel.onCopyBibTeX()
        advanceUntilIdle()
        assertEquals(CopiedBibTeX("@inproceedings{devlin2019bert,\n}\n", complete = true), viewModel.copied.value)

        viewModel.onCopyHandled(PaperDetailsMessage.BibTeXCopied)
        assertNull(viewModel.copied.value)
        assertEquals(PaperDetailsMessage.BibTeXCopied, viewModel.message.value)
    }

    @Test
    fun copyWithNothingToConfirmShowsNoMessage() = runTest {
        library.save(paper)
        citations.entries = mapOf(id to "@misc{k,\n}\n")
        val viewModel = viewModel()
        viewModel.onCopyBibTeX()
        advanceUntilIdle()

        viewModel.onCopyHandled(null)
        assertNull(viewModel.message.value)
    }

    @Test
    fun aFailedCopySaysSo() = runTest {
        library.save(paper)
        citations.failure = IOException("disk full")
        val viewModel = viewModel()

        viewModel.onCopyBibTeX()
        advanceUntilIdle()

        assertNull(viewModel.copied.value)
        assertEquals(PaperDetailsMessage.CopyFailed, viewModel.message.value)
    }
}
```

- [ ] **Step 4: Run them to see them fail**

Run: `./gradlew :feature:paperdetails:testDebugUnitTest --tests '*PaperDetailsCollectionsViewModelTest*'`
Expected: compilation FAIL.

- [ ] **Step 5: Implement the ViewModel changes**

Change the constructor and add fields (imports: `CitationRepository`, `CollectionResult`, `CollectionsRepository`):

```kotlin
class PaperDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val collectionsRepository: CollectionsRepository,
    private val citationRepository: CitationRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {
```

Replace `uiState` with:

```kotlin
    val uiState: StateFlow<PaperDetailsUiState> = combine(
        paper.filterNotNull(),
        notes.filterNotNull(),
        saveState,
        collectionsRepository.observeCollections(),
        collectionsRepository.observeCollectionIds(openAlexId)
    ) { current, typed, state, collections, memberOf ->
        PaperDetailsUiState.Loaded(current, typed, state, collections, memberOf)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaperDetailsUiState.Loading)
```

Add:

```kotlin
    private val _newCollectionDialog = MutableStateFlow<NewCollectionDialog?>(null)
    val newCollectionDialog: StateFlow<NewCollectionDialog?> = _newCollectionDialog.asStateFlow()

    private val _copied = MutableStateFlow<CopiedBibTeX?>(null)
    val copied: StateFlow<CopiedBibTeX?> = _copied.asStateFlow()

    fun onToggleCollection(collectionId: Long, member: Boolean) {
        collectionChange { collectionsRepository.setMembership(collectionId, openAlexId, member) }
    }

    fun onNewCollection() {
        _newCollectionDialog.value = NewCollectionDialog()
    }

    fun onNewCollectionNameEdited() {
        _newCollectionDialog.update { it?.copy(nameTaken = false) }
    }

    /** Creates the collection and puts this paper in it. */
    fun onNewCollectionConfirm(name: String) {
        collectionChange(closeDialogOnFailure = true) {
            when (val result = collectionsRepository.create(name)) {
                is CollectionResult.Done -> {
                    collectionsRepository.setMembership(result.id, openAlexId, member = true)
                    _newCollectionDialog.value = null
                }
                CollectionResult.NameTaken -> _newCollectionDialog.value = NewCollectionDialog(nameTaken = true)
                // The dialog's button is disabled for invalid names, so this only happens on a race; keep the dialog open.
                CollectionResult.InvalidName -> Unit
            }
        }
    }

    fun onNewCollectionDismiss() {
        _newCollectionDialog.value = null
    }

    fun onCopyBibTeX() {
        viewModelScope.launch {
            try {
                citationRepository.entry(openAlexId)?.let { _copied.value = CopiedBibTeX(it.bibtex, it.complete) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = PaperDetailsMessage.CopyFailed
            }
        }
    }

    /** The screen put [copied] on the clipboard; [confirmation] is what to show, from copyConfirmation. */
    fun onCopyHandled(confirmation: PaperDetailsMessage?) {
        _copied.value = null
        if (confirmation != null) _message.value = confirmation
    }

    private fun collectionChange(closeDialogOnFailure: Boolean = false, change: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                change()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (closeDialogOnFailure) _newCollectionDialog.value = null
                _message.value = PaperDetailsMessage.CollectionsUpdateFailed
            }
        }
    }
```

Run: `./gradlew :feature:paperdetails:testDebugUnitTest --tests '*ViewModelTest*'`
Expected: PASS (both ViewModel test classes).

- [ ] **Step 6: Write the failing content tests**

In `PaperDetailsActions.kt`, add:

```kotlin
    val onCopyBibTeX: () -> Unit = {},
    val onToggleCollection: (Long, Boolean) -> Unit = { _, _ -> },
    val onNewCollection: () -> Unit = {},
    val onNewCollectionNameEdited: () -> Unit = {},
    val onNewCollectionConfirm: (String) -> Unit = {},
    val onNewCollectionDismiss: () -> Unit = {},
```

`PaperDetailsCollectionsContentTest.kt`:

```kotlin
package com.etatech.hashiya.feature.paperdetails

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.etatech.hashiya.core.designsystem.component.COLLECTION_NAME_FIELD_TAG
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class PaperDetailsCollectionsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()
    private val actions = PaperDetailsActions(
        onCopyBibTeX = { events += "copy" },
        onRemove = { events += "remove" },
        onToggleCollection = { id, member -> events += "toggle:$id:$member" },
        onNewCollection = { events += "new" },
        onNewCollectionConfirm = { events += "confirm:$it" }
    )
    private val thesis = PaperCollection(1, "Thesis", 1)
    private val chapter = PaperCollection(2, "Chapter 2", 0)

    private fun show(
        collections: List<PaperCollection>,
        memberOf: Set<Long>,
        dialog: NewCollectionDialog? = null
    ) = composeRule.setContent {
        HashiyaTheme {
            PaperDetailsContent(
                uiState = PaperDetailsUiState.Loaded(
                    LibraryPaper(SamplePapers.bert, ReadingStatus.ToRead),
                    PaperNotes(),
                    NotesSaveState.Idle,
                    collections,
                    memberOf
                ),
                actions = actions,
                newCollectionDialog = dialog
            )
        }
    }

    @Test
    fun rowShowsTheCollectionsThePaperIsIn() {
        show(listOf(chapter, thesis), setOf(thesis.id))
        composeRule.onNodeWithTag(COLLECTIONS_ROW_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Thesis").assertIsDisplayed()
        composeRule.onNodeWithText("Chapter 2").assertDoesNotExist()
    }

    @Test
    fun rowSaysNotInAnyCollection() {
        show(emptyList(), emptySet())
        composeRule.onNodeWithText("Not in any collection").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun checklistTogglesAndCreates() {
        show(listOf(chapter, thesis), setOf(thesis.id))
        composeRule.onNodeWithTag(COLLECTIONS_ROW_TAG).performScrollTo().performClick()

        composeRule.onNodeWithTag(CHECKLIST_ROW_TAG_PREFIX + thesis.id).assertIsOn()
        composeRule.onNodeWithTag(CHECKLIST_ROW_TAG_PREFIX + chapter.id).assertIsOff().performClick()
        composeRule.onNodeWithTag(CHECKLIST_ROW_TAG_PREFIX + thesis.id).performClick()
        composeRule.onNodeWithText("New collection").performClick()

        assertEquals(listOf("toggle:2:true", "toggle:1:false", "new"), events)
    }

    @Test
    fun emptyChecklistExplainsCollections() {
        show(emptyList(), emptySet())
        composeRule.onNodeWithTag(COLLECTIONS_ROW_TAG).performScrollTo().performClick()
        composeRule.onNodeWithText("Group papers for a chapter, a course or a project.").assertIsDisplayed()
        composeRule.onNodeWithText("New collection").assertIsDisplayed()
    }

    @Test
    fun newCollectionDialogConfirms() {
        show(emptyList(), emptySet(), dialog = NewCollectionDialog())
        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextInput("Thesis")
        composeRule.onNodeWithText("Create").performClick()
        assertEquals(listOf("confirm:Thesis"), events)
    }

    @Test
    fun overflowOffersCopyBibTeXAboveRemove() {
        show(emptyList(), emptySet())
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Copy BibTeX").performClick()
        assertEquals(listOf("copy"), events)
    }
}
```


- [ ] **Step 7: Implement the screen**

`CollectionsRow.kt`:

```kotlin
package com.etatech.hashiya.feature.paperdetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.PaperCollection

internal const val COLLECTIONS_ROW_TAG = "collections_row"

/** "Collections" and the names of the ones this paper is in, as plain chips. The whole row is one button that opens the checklist. */
@Composable
internal fun CollectionsRow(collections: List<PaperCollection>, memberOf: Set<Long>, onClick: () -> Unit) {
    val names = collections.filter { it.id in memberOf }.map { it.name }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(COLLECTIONS_ROW_TAG)
    ) {
        Icon(HashiyaIcons.Collection, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(stringResource(R.string.details_collections), style = MaterialTheme.typography.labelMedium)
            if (names.isEmpty()) {
                Text(
                    stringResource(R.string.details_no_collections),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.forEach { name ->
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                name,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        Icon(HashiyaIcons.ArrowDropDown, contentDescription = null)
    }
}
```

`CollectionChecklistSheet.kt`:

```kotlin
package com.etatech.hashiya.feature.paperdetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.PaperCollection

internal const val CHECKLIST_ROW_TAG_PREFIX = "checklist_row_"

/** One checkbox per collection, applied at once, then "New collection". Stays open while toggling. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionChecklistSheet(
    collections: List<PaperCollection>,
    memberOf: Set<Long>,
    onToggle: (Long, Boolean) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            if (collections.isEmpty()) {
                Text(
                    stringResource(R.string.details_collections_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            collections.forEach { collection ->
                val checked = collection.id in memberOf
                ListItem(
                    headlineContent = { Text(collection.name) },
                    leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                    modifier = Modifier
                        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(collection.id, it) })
                        .testTag(CHECKLIST_ROW_TAG_PREFIX + collection.id)
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.details_new_collection)) },
                leadingContent = { Icon(HashiyaIcons.Add, contentDescription = null) },
                modifier = Modifier.clickable(role = Role.Button, onClick = onNew)
            )
        }
    }
}
```

In `PaperDetailsScreen.kt`:

1. `PaperDetailsScreen(...)`: collect `newCollectionDialog` and `copied`, put a copied entry on the clipboard, and wire the actions:

```kotlin
    val newCollectionDialog by viewModel.newCollectionDialog.collectAsStateWithLifecycle()
    val copied by viewModel.copied.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(copied) {
        val entry = copied ?: return@LaunchedEffect
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("BibTeX", entry.text))
        viewModel.onCopyHandled(copyConfirmation(entry.complete, Build.VERSION.SDK_INT))
    }
```

pass `newCollectionDialog = newCollectionDialog` to `PaperDetailsContent`, and add to its `PaperDetailsActions(...)`:

```kotlin
            onCopyBibTeX = viewModel::onCopyBibTeX,
            onToggleCollection = viewModel::onToggleCollection,
            onNewCollection = viewModel::onNewCollection,
            onNewCollectionNameEdited = viewModel::onNewCollectionNameEdited,
            onNewCollectionConfirm = viewModel::onNewCollectionConfirm,
            onNewCollectionDismiss = viewModel::onNewCollectionDismiss,
```

(imports: `android.content.ClipData`, `android.content.ClipboardManager`, `android.os.Build`, `androidx.compose.ui.platform.LocalContext`).

2. `PaperDetailsContent` gains `newCollectionDialog: NewCollectionDialog? = null` as its last parameter. Replace the message `LaunchedEffect` with one that covers every message:

```kotlin
    val collectionsUpdateFailed = stringResource(R.string.details_collections_update_failed)
    val bibtexCopied = stringResource(R.string.details_bibtex_copied)
    val bibtexIncomplete = stringResource(R.string.details_bibtex_incomplete)
    val copyFailed = stringResource(R.string.details_copy_failed)
    LaunchedEffect(message) {
        when (message) {
            PaperDetailsMessage.NotesSaveFailed -> {
                val result = snackbarHostState.showSnackbar(saveFailed, retry, duration = SnackbarDuration.Long)
                actions.onMessageShown()
                if (result == SnackbarResult.ActionPerformed) actions.onRetrySave()
            }

            PaperDetailsMessage.StatusUpdateFailed -> {
                snackbarHostState.showSnackbar(statusUpdateFailed)
                actions.onMessageShown()
            }

            PaperDetailsMessage.CollectionsUpdateFailed, PaperDetailsMessage.BibTeXCopied, PaperDetailsMessage.CopyFailed -> {
                val text = when (message) {
                    PaperDetailsMessage.CollectionsUpdateFailed -> collectionsUpdateFailed
                    PaperDetailsMessage.BibTeXCopied -> bibtexCopied
                    else -> copyFailed
                }
                snackbarHostState.showSnackbar(text)
                actions.onMessageShown()
            }

            PaperDetailsMessage.BibTeXIncomplete -> {
                snackbarHostState.showSnackbar(bibtexIncomplete, duration = SnackbarDuration.Long)
                actions.onMessageShown()
            }

            null -> Unit
        }
    }
```

(remove the temporary `else -> Unit` from Step 2).

3. Add the sheet and dialog before `Scaffold`:

```kotlin
    var checklistOpen by rememberSaveable { mutableStateOf(false) }
    val loaded = uiState as? PaperDetailsUiState.Loaded
    if (checklistOpen && loaded != null) {
        CollectionChecklistSheet(
            collections = loaded.collections,
            memberOf = loaded.memberOf,
            onToggle = actions.onToggleCollection,
            onNew = actions.onNewCollection,
            onDismiss = { checklistOpen = false }
        )
    }
    if (newCollectionDialog != null) {
        CollectionNameDialog(
            initialName = null,
            nameTaken = newCollectionDialog.nameTaken,
            onNameEdited = actions.onNewCollectionNameEdited,
            onConfirm = actions.onNewCollectionConfirm,
            onDismiss = actions.onNewCollectionDismiss
        )
    }
```

and pass `onOpenCollections = { checklistOpen = true }` into `DetailsBody` (new parameter), which renders the row right after the status selector:

```kotlin
        ReadingStatusSelector(state.paper.status, actions.onStatusChange)
        Spacer(Modifier.height(8.dp))
        CollectionsRow(state.collections, state.memberOf, onClick = onOpenCollections)
        PaperLinks(paper, actions.onOpenLink)
```

4. `OverflowMenu` takes `onCopyBibTeX` too; the call site becomes `OverflowMenu(actions.onCopyBibTeX, actions.onRemove)`, and its `DropdownMenu` lists Copy BibTeX first:

```kotlin
            DropdownMenuItem(
                text = { Text(stringResource(R.string.details_copy_bibtex)) },
                leadingIcon = { Icon(HashiyaIcons.Copy, contentDescription = null) },
                onClick = {
                    expanded = false
                    onCopyBibTeX()
                }
            )
```

(imports: `CollectionNameDialog`, `androidx.compose.runtime.setValue` is already imported, `rememberSaveable` already imported.)

- [ ] **Step 8: Run the Details tests**

Run: `./gradlew :feature:paperdetails:testDebugUnitTest`
Expected: PASS. The existing Details screenshots now show the Collections row, so their baselines change on purpose (re-recorded in Task 14).

- [ ] **Step 9: Add screenshot tests**

In `PaperDetailsScreenshotTest.kt`, add:

```kotlin
    private val thesis = PaperCollection(1, "Thesis", 2)
    private val chapter = PaperCollection(2, "الفصل الثاني", 1)

    @Test
    fun inCollections() = composeRule.captureScreenshot("details_collections", variant, arabicText = "المجموعات") {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(
                LibraryPaper(SamplePapers.vit, ReadingStatus.Reading),
                notes,
                NotesSaveState.Idle,
                listOf(chapter, thesis),
                setOf(chapter.id, thesis.id)
            ),
            actions = PaperDetailsActions()
        )
    }

    @Test
    fun checklist() = composeRule.captureScreenshot(
        "details_checklist",
        variant,
        arabicText = "مجموعة جديدة",
        wholeScreen = true,
        beforeCapture = { onNodeWithTag(COLLECTIONS_ROW_TAG).performClick() }
    ) {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(
                LibraryPaper(SamplePapers.vit, ReadingStatus.Reading),
                notes,
                NotesSaveState.Idle,
                listOf(chapter, thesis),
                setOf(thesis.id)
            ),
            actions = PaperDetailsActions()
        )
    }

    @Test
    fun emptyChecklist() = composeRule.captureScreenshot(
        "details_checklist_empty",
        variant,
        arabicText = "اجمع الأوراق لفصل أو مقرر أو مشروع.",
        wholeScreen = true,
        beforeCapture = { onNodeWithTag(COLLECTIONS_ROW_TAG).performClick() }
    ) {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(LibraryPaper(SamplePapers.vit, ReadingStatus.Reading), notes, NotesSaveState.Idle),
            actions = PaperDetailsActions()
        )
    }
```

(imports: `androidx.compose.ui.test.onNodeWithTag`, `androidx.compose.ui.test.performClick`, `com.etatech.hashiya.core.model.PaperCollection`).

Run: `./gradlew :feature:paperdetails:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
./gradlew spotlessApply
git add feature/paperdetails
git commit -m "feat: add collections and Copy BibTeX to paper details"
```

---

### Task 14: Navigation test, docs, baselines and full verification

**Files:**
- Modify: `app/src/test/java/com/etatech/hashiya/HashiyaAppNavigationTest.kt`
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-09-30-collections-and-bibtex-design.md`
- Modify: `feature/*/src/test/screenshots/*.png` (recorded on CI)

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Write the navigation test**

Add to `HashiyaAppNavigationTest`:

```kotlin
    @Test
    fun collectionCreatedOnDetailsFiltersTheLibrary() {
        runBlocking {
            libraryRepository.save(SamplePapers.bert)
            libraryRepository.save(SamplePapers.vit)
        }
        waitForText(SamplePapers.bert.title)
        composeRule.onNodeWithText(SamplePapers.bert.title).performClick()
        waitForText("My notes")

        // The row's tag is internal to feature/paperdetails, so the test finds it by its text.
        composeRule.onNodeWithText("Not in any collection").performScrollTo().performClick()
        composeRule.onNodeWithText("New collection").performClick()
        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextInput("Thesis")
        composeRule.onNodeWithText("Create").performClick()
        waitForText("Thesis")
        // The checklist sheet belongs to Details, so leaving Details closes it too.
        composeRule.onNodeWithContentDescription("Back").performClick()

        waitForText("All papers")
        composeRule.onNodeWithText("All papers").performClick()
        composeRule.onNodeWithText("Thesis").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(SamplePapers.vit.title).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText(SamplePapers.bert.title).assertIsDisplayed()
    }
```

Imports: `androidx.compose.ui.test.onNodeWithTag`, `androidx.compose.ui.test.performScrollTo`, `androidx.compose.ui.test.performTextInput`, `com.etatech.hashiya.core.designsystem.component.COLLECTION_NAME_FIELD_TAG` (public; `app` already depends on `core/designsystem`).

Run: `./gradlew :app:testDebugUnitTest --tests '*HashiyaAppNavigationTest*'`
Expected: PASS.

- [ ] **Step 2: Update the README**

In `README.md`'s `## Features` list, add after the line that starts "- Open a saved paper's details":

```markdown
- Group saved papers into collections and filter the Library by collection. Export a collection, or the whole library, as a `.bib` file for Overleaf or LaTeX, with entry types and cite keys that stay the same from one export to the next, or copy one paper's BibTeX from its details.
```

In the architecture diagram, add `core/data --> core/bibtex` and `core/bibtex --> core/model` lines:

```mermaid
    core/data --> core/network & core/database & core/datastore & core/bibtex & core/model
    core/bibtex --> core/model
```

(the first replaces the existing `core/data --> …` line). In `## Testing`, change `./gradlew testDebugUnitTest :core:model:test` to `./gradlew testDebugUnitTest :core:model:test :core:bibtex:test`. In `## Roadmap`, change `5. Collections and BibTeX export` to `5. ✅ Collections and BibTeX export`.

- [ ] **Step 3: Record the copy-failure string in the spec**

In the spec's §10 table, add after `details_collections_update_failed`:

```markdown
| `details_copy_failed` | paperdetails | Couldn\'t copy BibTeX | تعذّر نسخ BibTeX |
```

and in §11 add the row:

```markdown
| Reading the database fails during Copy BibTeX | The snackbar "Couldn't copy BibTeX" appears. Nothing is copied. |
```

Also change the spec's `Collection(id, name, paperCount)` mentions to `PaperCollection` (§1 decisions are unaffected; §3 and §7.2), and in §7.2 rename `Created(id)` to `Done(id)`, matching the code.

- [ ] **Step 4: Run the whole check locally**

Run: `./gradlew spotlessCheck assembleDebug testDebugUnitTest :core:model:test :core:bibtex:test lintDebug`
Expected: BUILD SUCCESSFUL. Fix any lint or ktlint finding before going on (ktlint's `max-line-length` of 140 isn't auto-fixed by `spotlessApply`; wrap such lines by hand).

- [ ] **Step 5: Commit**

```bash
git add app README.md docs/superpowers/specs/2026-09-30-collections-and-bibtex-design.md
git commit -m "docs: describe collections and BibTeX export"
```

- [ ] **Step 6: Record the screenshot baselines on CI**

Run: `bash scripts/record-screenshots-on-linux.sh`
Expected: "Baselines copied from run …". Then:

```bash
git status --short -- '*.png'
```

Commit the new images (`library_collection_*`, `details_collections*`, `details_checklist*`) and the changed ones for screens this work altered (every `library_*` with a top bar that now shows the selector or Export, every `details_*`). Leave other changed images uncommitted if the difference is anti-aliasing noise on screens this work didn't touch (restore them with `git checkout -- <file>`).

```bash
git add feature/library/src/test/screenshots feature/paperdetails/src/test/screenshots
git commit -m "test: record the collections and BibTeX screenshot baselines on CI"
```

- [ ] **Step 7: Check authorship and push**

```bash
git log --format='%an <%ae>' origin/main..HEAD | sort -u
```

Expected: only `Fady <fady.fouad.a@gmail.com>`. Then push the branch and open a PR (no AI credits in the title or body). CI must be green, including the screenshot verification.

- [ ] **Step 8: Device checks (the user's, after merge)**

List the spec's §13 acceptance criteria in the PR body as a checklist for the user.
