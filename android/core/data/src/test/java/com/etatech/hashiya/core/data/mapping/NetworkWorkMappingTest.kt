package com.etatech.hashiya.core.data.mapping

import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.PublicationDetails
import com.etatech.hashiya.core.network.model.NetworkAuthor
import com.etatech.hashiya.core.network.model.NetworkAuthorship
import com.etatech.hashiya.core.network.model.NetworkBiblio
import com.etatech.hashiya.core.network.model.NetworkLocation
import com.etatech.hashiya.core.network.model.NetworkOpenAccess
import com.etatech.hashiya.core.network.model.NetworkSource
import com.etatech.hashiya.core.network.model.NetworkWork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkWorkMappingTest {
    @Test
    fun mapsCompleteWork() {
        val work = NetworkWork(
            id = "https://openalex.org/W2626778328",
            doi = "https://doi.org/10.48550/arXiv.1706.03762",
            displayName = "Attention Is All You Need",
            publicationYear = 2017,
            primaryLocation = NetworkLocation(source = NetworkSource("NeurIPS")),
            authorships = listOf(
                NetworkAuthorship(NetworkAuthor("https://openalex.org/A1", "Ashish Vaswani")),
                NetworkAuthorship(NetworkAuthor(null, "Noam Shazeer"))
            ),
            citedByCount = 128412,
            openAccess = NetworkOpenAccess(isOa = true),
            bestOaLocation = NetworkLocation(pdfUrl = "https://arxiv.org/pdf/1706.03762"),
            abstractInvertedIndex = mapOf("Hello" to listOf(0), "world" to listOf(1))
        )

        val paper = work.asPaper()

        assertEquals("W2626778328", paper.openAlexId)
        assertEquals("10.48550/arxiv.1706.03762", paper.doi)
        assertEquals("Attention Is All You Need", paper.title)
        assertEquals(listOf(Author("Ashish Vaswani", "A1"), Author("Noam Shazeer", null)), paper.authors)
        assertEquals(2017, paper.year)
        assertEquals("NeurIPS", paper.venue)
        assertEquals("Hello world", paper.abstract)
        assertEquals(128412, paper.citationCount)
        assertTrue(paper.isOpenAccess)
        assertEquals("https://arxiv.org/pdf/1706.03762", paper.openAccessPdfUrl)
    }

    @Test
    fun mapsSparseWorkWithSafeDefaults() {
        val paper = NetworkWork(
            id = "https://openalex.org/W1",
            authorships = listOf(NetworkAuthorship(NetworkAuthor("https://openalex.org/A9", null)))
        ).asPaper()

        assertEquals("W1", paper.openAlexId)
        assertNull(paper.doi)
        assertEquals("", paper.title)
        assertTrue("authors without a name are dropped", paper.authors.isEmpty())
        assertNull(paper.year)
        assertNull(paper.venue)
        assertNull(paper.abstract)
        assertFalse(paper.isOpenAccess)
        assertNull(paper.openAccessPdfUrl)
        assertEquals(PublicationDetails(), paper.publication)
    }

    @Test
    fun dropsInvalidDoi() {
        assertNull(NetworkWork(id = "https://openalex.org/W1", doi = "not-a-doi").asPaper().doi)
    }

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
}
