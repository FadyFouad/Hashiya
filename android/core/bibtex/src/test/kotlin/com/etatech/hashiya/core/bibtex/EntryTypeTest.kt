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
