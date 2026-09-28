package com.etatech.hashiya.core.data.mapping

import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperWithAuthors
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
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
        openAccessPdfUrl = "https://example.org/x.pdf"
    )

    @Test
    fun roundTripsThroughEntities() {
        val entities = paper.asEntities(localId = "local-1", savedAt = 42L)

        assertEquals("local-1", entities.paper.id)
        assertEquals(42L, entities.paper.savedAt)
        assertEquals(listOf(0, 1), entities.authors.map { it.position })
        assertEquals(paper, PaperWithAuthors(entities.paper, entities.authors).asPaper())
    }

    @Test
    fun sortsAuthorsByPositionWhenReading() {
        val entities = paper.asEntities(localId = "local-1", savedAt = 42L)
        val shuffled = PaperWithAuthors(entities.paper, entities.authors.reversed())

        assertEquals(listOf("First", "Second"), shuffled.asPaper().authors.map { it.name })
    }

    @Test
    fun authorEntitiesPointAtThePaper() {
        val authors: List<PaperAuthorEntity> = paper.asEntities(localId = "local-1", savedAt = 0).authors
        assertEquals(setOf("local-1"), authors.map { it.paperId }.toSet())
    }
}
