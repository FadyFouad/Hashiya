package com.etatech.hashiya.core.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportPlannerTest {
    private fun entry(line: Int, title: String = "T$line", doi: String? = null, year: Int? = 2020) =
        ImportedEntry(line = line, entryType = "article", title = title, doi = doi, year = year)

    private fun kinds(plan: List<PlannedEntry>) = plan.map { it::class.simpleName to it.entry.line }

    @Test
    fun `title keys ignore case, accents, tashkeel and punctuation`() {
        assertEquals(titleKey("Über: Deep-Learning!", 2020), titleKey("uber deep learning", 2020))
        assertEquals(titleKey("تَحليل  المشاعر", null), titleKey("تحليل المشاعر", null))
        assertNull(titleKey("  ?! ", 2020))
    }

    @Test
    fun `an entry repeated in the file by DOI or by title key is imported once, the first wins`() {
        val plan = ImportPlanner.plan(
            listOf(
                entry(1, doi = "10.1/a"),
                entry(2, title = "Other title", doi = "10.1/a"),
                entry(3, title = "Same Title"),
                entry(4, title = "same title."),
                entry(5, title = "Same Title", year = 2021)
            ),
            ExistingLibrary()
        )
        assertEquals(
            listOf("ToImport" to 1, "Repeated" to 2, "ToImport" to 3, "Repeated" to 4, "ToImport" to 5),
            kinds(plan)
        )
    }

    @Test
    fun `an entry with a DOI is never a title repeat of one without`() {
        val plan = ImportPlanner.plan(listOf(entry(1, title = "Same"), entry(2, title = "Same", doi = "10.1/x")), ExistingLibrary())
        assertEquals(listOf("ToImport" to 1, "ToImport" to 2), kinds(plan))
    }

    @Test
    fun `papers already saved are found by DOI, or by title key when the entry has no DOI`() {
        val existing = ExistingLibrary(dois = setOf("10.1/saved"), titleKeys = setOfNotNull(titleKey("Saved Paper", 2019)))
        val plan = ImportPlanner.plan(
            listOf(
                entry(1, doi = "10.1/saved"),
                entry(2, title = "Saved paper", year = 2019),
                entry(3, title = "Saved paper", year = 2020),
                entry(4, title = "Saved Paper", year = 2019, doi = "10.1/new")
            ),
            existing
        )
        assertEquals(listOf("InLibrary" to 1, "InLibrary" to 2, "ToImport" to 3, "ToImport" to 4), kinds(plan))
    }

    @Test
    fun `papers this job saved before a restart count as imported, not as already in the library`() {
        val existing = ExistingLibrary(
            dois = setOf("10.1/mine", "10.1/theirs"),
            titleKeys = setOfNotNull(titleKey("Mine", 2020)),
            importedDois = setOf("10.1/mine"),
            importedTitleKeys = setOfNotNull(titleKey("Mine", 2020))
        )
        val plan = ImportPlanner.plan(
            listOf(entry(1, doi = "10.1/mine"), entry(2, title = "Mine"), entry(3, doi = "10.1/theirs")),
            existing
        )
        assertEquals(listOf("AlreadyImported" to 1, "AlreadyImported" to 2, "InLibrary" to 3), kinds(plan))
    }

    @Test
    fun `the nasty fixture imports the Transformer paper once`() {
        val entries = ImportParser.parse(ImportParser.decode(fixtureBytes("nasty.bib")))!!.entries
        val repeated = ImportPlanner.plan(entries, ExistingLibrary()).filterIsInstance<PlannedEntry.Repeated>()
        assertEquals(listOf("vaswani_again"), repeated.map { it.entry.citeKey })
    }
}
