package com.etatech.hashiya.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.etatech.hashiya.core.bibtex.BibTeX
import com.etatech.hashiya.core.bibtex.CitablePaper
import com.etatech.hashiya.core.bibtex.CiteKeys
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PublicationDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs cite keys and BibTeX export on Android's ICU regex engine, which rejects some patterns the JVM unit tests accept
 * (a `(?U)` flag crashed copying on device). Needs a device or emulator: `./gradlew :core:data:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class BibTeXOnDeviceTest {
    private val paper = Paper(
        openAlexId = "W1",
        doi = "10.1038/nature14539",
        title = "\u0085Deep learning for　graphs",
        authors = listOf(Author("José Müller", null), Author("محمد علي", null)),
        year = 2015,
        venue = "Nature",
        abstract = null,
        citationCount = 0,
        isOpenAccess = false,
        openAccessPdfUrl = null,
        publication = PublicationDetails(workType = "article", sourceType = "journal")
    )

    @Test
    fun citeKeysSplitOnUnicodeWhitespace() {
        assertEquals(listOf("muller2015deep", "muller2015deepa"), CiteKeys.assign(listOf(paper, paper), taken = emptySet()))
    }

    @Test
    fun exportCollapsesUnicodeWhitespace() {
        val file = BibTeX.file(listOf(CitablePaper(paper, "muller2015deep")))
        assertTrue(file, file.contains("title = {Deep learning for graphs}"))
        assertTrue(file, file.contains("author = {José Müller and محمد علي}"))
    }
}
