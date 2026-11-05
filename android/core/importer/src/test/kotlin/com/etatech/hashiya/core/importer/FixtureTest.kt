package com.etatech.hashiya.core.importer

import org.junit.Assert.assertEquals
import org.junit.Test

/** Real-shape exports from testdata/import must parse to exactly the results in testdata/import/expected, on Android and iOS. */
class FixtureTest {
    private fun assertFixture(name: String) {
        val actual = ImportParser.parse(ImportParser.decode(fixtureBytes(name)))
        assertEquals(name, expectedJson(name), actual.toJson())
    }

    @Test
    fun zoteroBibTeX() = assertFixture("zotero.bib")

    @Test
    fun betterBibTeX() = assertFixture("better-bibtex.bib")

    @Test
    fun betterBibLaTeX() = assertFixture("better-biblatex.bib")

    @Test
    fun mendeleyBibTeX() = assertFixture("mendeley.bib")

    @Test
    fun googleScholarBibTeX() = assertFixture("google-scholar.bib")

    @Test
    fun nastyBibTeX() = assertFixture("nasty.bib")

    @Test
    fun zoteroRis() = assertFixture("zotero.ris")

    @Test
    fun endNoteRis() = assertFixture("endnote.ris")

    @Test
    fun mendeleyRis() = assertFixture("mendeley.ris")

    @Test
    fun nastyRis() = assertFixture("nasty.ris")

    @Test
    fun endNoteWindows1252Ris() = assertFixture("endnote-cp1252.ris")
}
