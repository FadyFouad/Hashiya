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
