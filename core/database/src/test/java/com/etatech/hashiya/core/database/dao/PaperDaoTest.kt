package com.etatech.hashiya.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
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
class PaperDaoTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var dao: PaperDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.paperDao()
    }

    @After
    fun tearDown() = db.close()

    private fun paper(id: String, openAlexId: String, savedAt: Long, doi: String? = null) = PaperEntity(
        id = id,
        openAlexId = openAlexId,
        doi = doi,
        title = "Title $id",
        year = 2020,
        venue = "Venue",
        abstract = null,
        citationCount = 1,
        isOpenAccess = false,
        oaPdfUrl = null,
        savedAt = savedAt
    )

    private fun authors(paperId: String, vararg names: String) =
        names.mapIndexed { index, name -> PaperAuthorEntity(paperId, index, name, null) }

    private fun authorRowCount(): Int = db.query("SELECT COUNT(*) FROM paper_authors", null).use {
        it.moveToFirst()
        it.getInt(0)
    }

    @Test
    fun savedPapersAreNewestFirst() = runTest {
        dao.insertPaperWithAuthors(paper("a", "W1", savedAt = 100), authors("a", "Ada"))
        dao.insertPaperWithAuthors(paper("b", "W2", savedAt = 200), authors("b", "Bo"))

        assertEquals(listOf("b", "a"), dao.observeSavedPapers().first().map { it.paper.id })
    }

    @Test
    fun keepsAuthorPositions() = runTest {
        dao.insertPaperWithAuthors(paper("a", "W1", 100), authors("a", "First", "Second", "Third"))

        val saved = dao.observeSavedPapers().first().single()
        assertEquals(listOf("First", "Second", "Third"), saved.authors.sortedBy { it.position }.map { it.name })
    }

    @Test
    fun savingAgainIsANoOp() = runTest {
        assertTrue(dao.insertPaperWithAuthors(paper("a", "W1", 100), authors("a", "Ada")))
        assertFalse(dao.insertPaperWithAuthors(paper("other-id", "W1", 999), authors("other-id", "Ada")))

        val saved = dao.observeSavedPapers().first()
        assertEquals(1, saved.size)
        assertEquals(100L, saved.single().paper.savedAt)
        assertEquals(1, authorRowCount())
    }

    @Test
    fun worksSharingADoiCanBothBeSaved() = runTest {
        assertTrue(dao.insertPaperWithAuthors(paper("a", "W1", 100, doi = "10.1000/xyz"), emptyList()))
        assertTrue(dao.insertPaperWithAuthors(paper("b", "W2", 200, doi = "10.1000/xyz"), emptyList()))

        assertEquals(setOf("W1", "W2"), dao.observeSavedOpenAlexIds().first().toSet())
    }

    @Test
    fun deletingReturnsRowAndCascadesToAuthors() = runTest {
        dao.insertPaperWithAuthors(paper("a", "W1", 100), authors("a", "Ada", "Bo"))

        val removed = dao.deleteByOpenAlexId("W1")

        assertEquals("a", removed?.paper?.id)
        assertEquals(2, removed?.authors?.size)
        assertTrue(dao.observeSavedPapers().first().isEmpty())
        assertEquals(0, authorRowCount())
    }

    @Test
    fun restoringDeletedRowKeepsIdAndSavedAt() = runTest {
        dao.insertPaperWithAuthors(paper("a", "W1", 100), authors("a", "Ada"))
        dao.insertPaperWithAuthors(paper("b", "W2", 200), authors("b", "Bo"))
        val removed = dao.deleteByOpenAlexId("W1")!!

        dao.insertPaperWithAuthors(removed.paper, removed.authors)

        val saved = dao.observeSavedPapers().first()
        assertEquals(listOf("b", "a"), saved.map { it.paper.id })
        assertEquals(100L, saved.last().paper.savedAt)
    }

    @Test
    fun deletingUnknownPaperReturnsNull() = runTest {
        assertNull(dao.deleteByOpenAlexId("missing"))
    }

    @Test
    fun observesSavedOpenAlexIds() = runTest {
        dao.insertPaperWithAuthors(paper("a", "W1", 100), emptyList())
        dao.insertPaperWithAuthors(paper("b", "W2", 200), emptyList())

        assertEquals(setOf("W1", "W2"), dao.observeSavedOpenAlexIds().first().toSet())
    }
}
