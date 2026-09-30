package com.etatech.hashiya.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.database.model.CollectionWithCount
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.searchEntityFor
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
class CollectionDaoTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var dao: CollectionDao
    private lateinit var papers: PaperDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.collectionDao()
        papers = db.paperDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun savePaper(id: String, openAlexId: String) {
        val paper = PaperEntity(id, openAlexId, null, "Title $id", 2020, null, null, 0, false, null, savedAt = 1, readingStatus = "to_read")
        papers.insertPaperWithAuthors(paper, emptyList(), searchEntityFor(id, paper.title, emptyList(), null, null))
    }

    private fun count(table: String): Int = db.query("SELECT COUNT(*) FROM $table", null).use {
        it.moveToFirst()
        it.getInt(0)
    }

    @Test
    fun createsCollectionsSortedByNameKeyWithCounts() = runTest {
        val b = checkNotNull(dao.insertCollection("beta", "beta", createdAt = 1))
        val a = checkNotNull(dao.insertCollection("Alpha", "alpha", createdAt = 2))
        savePaper("p1", "W1")
        dao.addToCollection(a, "W1", addedAt = 3)

        assertEquals(listOf(CollectionWithCount(a, "Alpha", 1), CollectionWithCount(b, "beta", 0)), dao.observeCollections().first())
    }

    @Test
    fun nameClashIsReportedNotThrown() = runTest {
        val id = checkNotNull(dao.insertCollection("Thesis", "thesis", createdAt = 1))
        assertNull(dao.insertCollection(" thesis ", "thesis", createdAt = 2))
        val other = checkNotNull(dao.insertCollection("Other", "other", createdAt = 3))

        assertFalse(dao.renameCollection(other, "THESIS", "thesis"))
        assertEquals(listOf("Other", "Thesis"), dao.observeCollections().first().map { it.name })
        assertTrue(dao.renameCollection(id, "thesis", "thesis"))
        assertTrue(dao.renameCollection(other, "Chapter 2", "chapter 2"))
        assertEquals(listOf("Chapter 2", "thesis"), dao.observeCollections().first().map { it.name })
        assertEquals(2, count("collections"))
    }

    @Test
    fun membershipIsIdempotentAndFollowsThePaper() = runTest {
        val id = checkNotNull(dao.insertCollection("A", "a", createdAt = 1))
        savePaper("p1", "W1")

        dao.addToCollection(id, "W1", addedAt = 2)
        dao.addToCollection(id, "W1", addedAt = 3)
        assertEquals(listOf(id), dao.observeCollectionIdsForPaper("W1").first())

        dao.removeFromCollection(id, "W1")
        assertEquals(emptyList<Long>(), dao.observeCollectionIdsForPaper("W1").first())
        dao.addToCollection(id, "W-unsaved", addedAt = 4)
        assertEquals(0, count("collection_papers"))
    }

    @Test
    fun deletingACollectionKeepsItsPapersAndDeletingAPaperKeepsItsCollections() = runTest {
        val a = checkNotNull(dao.insertCollection("A", "a", createdAt = 1))
        val b = checkNotNull(dao.insertCollection("B", "b", createdAt = 1))
        savePaper("p1", "W1")
        savePaper("p2", "W2")
        dao.addToCollection(a, "W1", addedAt = 2)
        dao.addToCollection(b, "W2", addedAt = 2)

        dao.deleteCollection(a)
        assertEquals(2, count("papers"))
        assertEquals(1, count("collection_papers"))

        papers.deleteByOpenAlexId("W2")
        assertEquals(listOf("B"), dao.observeCollections().first().map { it.name })
        assertEquals(0, count("collection_papers"))
    }
}
