package com.etatech.hashiya.core.data.mapping

import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperWithAuthors
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PublicationDetails
import com.etatech.hashiya.core.model.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PaperEntityMappingTest {
    private val paper = Paper(
        openAlexId = "W1",
        doi = "10.1000/xyz",
        title = "Title",
        authors = listOf(Author("First", "A1"), Author("Second", null)),
        year = 2020,
        venue = "Venue",
        abstract = "Abstract",
        citationCount = 7,
        isOpenAccess = true,
        openAccessPdfUrl = "https://example.org/x.pdf",
        publication = PublicationDetails("article", "journal", "Pub", "1", "2", "3", "4")
    )

    @Test
    fun roundTripsThroughEntities() {
        val entities = paper.asEntities(localId = "local-1", savedAt = 42L, status = ReadingStatus.Reading)

        assertEquals("local-1", entities.paper.id)
        assertEquals(42L, entities.paper.savedAt)
        assertEquals("reading", entities.paper.readingStatus)
        assertEquals(listOf(0, 1), entities.authors.map { it.position })
        assertEquals(paper, PaperWithAuthors(entities.paper, entities.authors).asPaper())
        assertEquals(paper.publication, PaperWithAuthors(entities.paper, entities.authors).asPaper().publication)
    }

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

    @Test
    fun sortsAuthorsByPositionWhenReading() {
        val entities = paper.asEntities(localId = "local-1", savedAt = 42L, status = ReadingStatus.ToRead)
        val shuffled = PaperWithAuthors(entities.paper, entities.authors.reversed())

        assertEquals(listOf("First", "Second"), shuffled.asPaper().authors.map { it.name })
    }

    @Test
    fun authorEntitiesPointAtThePaper() {
        val authors: List<PaperAuthorEntity> = paper.asEntities(localId = "local-1", savedAt = 0, status = ReadingStatus.ToRead).authors
        assertEquals(setOf("local-1"), authors.map { it.paperId }.toSet())
    }

    @Test
    fun searchRowHoldsNormalizedTextForThePaper() {
        val search = paper.copy(title = "Schrödinger", abstract = null, venue = null)
            .asEntities(localId = "local-1", savedAt = 0, status = ReadingStatus.ToRead)
            .search

        assertEquals("local-1", search.paperId)
        assertEquals("schrodinger", search.title)
        assertEquals("first second", search.authors)
        assertEquals("", search.abstract)
        assertEquals("", search.venue)
    }
}
