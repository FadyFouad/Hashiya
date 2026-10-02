package com.etatech.hashiya.core.database.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.searchEntityFor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CitationDaoTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var dao: CitationDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.citationDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun savePaper(id: String, openAlexId: String, savedAt: Long, citeKey: String? = null) {
        val paper = PaperEntity(
            id,
            openAlexId,
            null,
            "Title $id",
            2020,
            null,
            null,
            0,
            false,
            null,
            savedAt = savedAt,
            readingStatus = "to_read",
            citeKey = citeKey
        )
        db.paperDao().insertPaperWithAuthors(paper, emptyList(), searchEntityFor(id, paper.title, emptyList(), null, null))
    }

    @Test
    fun papersComeOldestSavedFirstAndCanBeLimitedToACollection() = runTest {
        savePaper("p1", "W1", savedAt = 20)
        savePaper("p2", "W2", savedAt = 10)
        val collections = db.collectionDao()
        val id = checkNotNull(collections.insertCollection("A", "a", createdAt = 1))
        collections.addToCollection(id, "W1", addedAt = 1)

        assertEquals(listOf("p2", "p1"), dao.getPapers(null).map { it.paper.id })
        assertEquals(listOf("p1"), dao.getPapers(id).map { it.paper.id })
        assertEquals("p1", dao.getPaper("W1")?.paper?.id)
    }

    @Test
    fun updatesDetailsAndMarksThemFetched() = runTest {
        savePaper("p1", "W1", savedAt = 1)

        dao.updatePublicationDetails("p1", "article", "journal", "Springer", "521", "7553", "436", "444")

        val paper = checkNotNull(dao.getPaper("W1")).paper
        assertEquals(
            listOf("article", "journal", "Springer", "521", "7553", "436", "444"),
            listOf(paper.workType, paper.sourceType, paper.publisher, paper.volume, paper.issue, paper.firstPage, paper.lastPage)
        )
        assertTrue(paper.detailsFetched)
    }

    @Test
    fun markDetailsFetchedOnlySetsTheFlag() = runTest {
        savePaper("p1", "W1", savedAt = 1)
        dao.markDetailsFetched("p1")

        val paper = checkNotNull(dao.getPaper("W1")).paper
        assertTrue(paper.detailsFetched)
        assertEquals(null, paper.workType)
    }

    @Test
    fun assignsKeysAndRejectsATakenOne() = runTest {
        savePaper("p1", "W1", savedAt = 1, citeKey = "smith2020deep")
        savePaper("p2", "W2", savedAt = 2)

        dao.assignCiteKeys(mapOf("p2" to "smith2020deepa"))
        assertEquals(setOf("smith2020deep", "smith2020deepa"), dao.allCiteKeys().toSet())

        savePaper("p3", "W3", savedAt = 3)
        val failed = runCatching { dao.assignCiteKeys(mapOf("p3" to "smith2020deep")) }.exceptionOrNull()
        assertTrue(failed is SQLiteConstraintException)
        assertEquals(null, dao.getPaper("W3")?.paper?.citeKey)
    }

    @Test
    fun aStoredKeyIsNeverChanged() = runTest {
        savePaper("p1", "W1", savedAt = 1, citeKey = "smith2020deep")

        dao.assignCiteKeys(mapOf("p1" to "other2020key"))

        assertEquals("smith2020deep", dao.getPaper("W1")?.paper?.citeKey)
    }

    @Test
    fun aBatchWithATakenKeyStoresNone() = runTest {
        savePaper("p1", "W1", savedAt = 1, citeKey = "smith2020deep")
        savePaper("p2", "W2", savedAt = 2)
        savePaper("p3", "W3", savedAt = 3)

        val failed = runCatching { dao.assignCiteKeys(mapOf("p2" to "jones2021graph", "p3" to "smith2020deep")) }.exceptionOrNull()

        assertTrue(failed is SQLiteConstraintException)
        assertEquals(listOf("smith2020deep"), dao.allCiteKeys())
    }
}
