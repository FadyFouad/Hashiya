package com.etatech.hashiya.core.importer

import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.PublicationDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LocalPaperTest {
    private val entry = ImportedEntry(
        line = 3,
        entryType = "techreport",
        citeKey = "doe2005",
        title = "A Report",
        authors = listOf("Jane Doe", "World Health Organization"),
        year = 2005,
        venue = "Example University",
        doi = "10.1/x",
        url = "https://example.org/report.PDF?download=1",
        abstract = "Abstract",
        volume = "1",
        issue = "TR-1",
        firstPage = "3",
        lastPage = "9",
        publisher = "Example Press",
        workType = "report",
        sourceType = null
    )

    @Test
    fun `a local paper carries the file's fields under a local id`() {
        val paper = entry.toLocalPaper("1234")
        assertEquals("local-1234", paper.openAlexId)
        assertEquals("10.1/x", paper.doi)
        assertEquals("A Report", paper.title)
        assertEquals(listOf(Author("Jane Doe", null), Author("World Health Organization", null)), paper.authors)
        assertEquals(2005, paper.year)
        assertEquals("Example University", paper.venue)
        assertEquals("Abstract", paper.abstract)
        assertEquals(0, paper.citationCount)
        assertFalse(paper.isOpenAccess)
        assertEquals(
            PublicationDetails(
                workType = "report",
                sourceType = null,
                publisher = "Example Press",
                volume = "1",
                issue = "TR-1",
                firstPage = "3",
                lastPage = "9"
            ),
            paper.publication
        )
    }

    @Test
    fun `only a link to a PDF is kept, as the PDF link`() {
        assertEquals("https://example.org/report.PDF?download=1", entry.toLocalPaper("1").openAccessPdfUrl)
        assertNull(entry.copy(url = "https://example.org/page").toLocalPaper("1").openAccessPdfUrl)
        assertNull(entry.copy(url = "https://example.org/pdf/page#x.pdf").toLocalPaper("1").openAccessPdfUrl)
        assertNull(entry.copy(url = null).toLocalPaper("1").openAccessPdfUrl)
    }
}
