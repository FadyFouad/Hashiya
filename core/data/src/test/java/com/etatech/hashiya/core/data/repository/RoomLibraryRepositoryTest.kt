package com.etatech.hashiya.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomLibraryRepositoryTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var repository: RoomLibraryRepository
    private var clock = 0L
    private var idCounter = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomLibraryRepository(db.paperDao(), now = { ++clock }, newId = { "local-${++idCounter}" })
    }

    @After
    fun tearDown() = db.close()

    private fun paper(id: String) = Paper(
        openAlexId = id,
        doi = null,
        title = "Paper $id",
        authors = listOf(Author("First", null), Author("Second", null)),
        year = 2020,
        venue = null,
        abstract = null,
        citationCount = 0,
        isOpenAccess = false,
        openAccessPdfUrl = null
    )

    @Test
    fun savedPapersAreNewestFirstWithAuthorsInOrder() = runTest {
        repository.save(paper("W1"))
        repository.save(paper("W2"))

        val saved = repository.observeSavedPapers().first()
        assertEquals(listOf("W2", "W1"), saved.map { it.openAlexId })
        assertEquals(listOf("First", "Second"), saved.first().authors.map { it.name })
    }

    @Test
    fun observesSavedIds() = runTest {
        repository.save(paper("W1"))
        assertEquals(setOf("W1"), repository.observeSavedIds().first())
    }

    @Test
    fun savingTwiceKeepsOneCopy() = runTest {
        repository.save(paper("W1"))
        repository.save(paper("W1"))
        assertEquals(1, repository.observeSavedPapers().first().size)
    }

    @Test
    fun removeThenRestoreReturnsPaperToItsPosition() = runTest {
        repository.save(paper("W1"))
        repository.save(paper("W2"))
        repository.save(paper("W3"))

        val removed = repository.remove("W2")!!
        assertEquals(listOf("W3", "W1"), repository.observeSavedPapers().first().map { it.openAlexId })

        repository.restore(removed)
        assertEquals(listOf("W3", "W2", "W1"), repository.observeSavedPapers().first().map { it.openAlexId })
    }

    @Test
    fun restoreAfterPaperWasSavedAgainIsNoOp() = runTest {
        repository.save(paper("W1"))
        val removed = repository.remove("W1")!!
        repository.save(paper("W1"))

        repository.restore(removed)

        assertEquals(1, repository.observeSavedPapers().first().size)
    }

    @Test
    fun removingUnknownPaperReturnsNull() = runTest {
        assertNull(repository.remove("missing"))
    }

    @Test
    fun worksSharingADoiAreBothSaved() = runTest {
        repository.save(paper("W1").copy(doi = "10.1000/xyz"))
        repository.save(paper("W2").copy(doi = "10.1000/xyz"))

        assertEquals(setOf("W1", "W2"), repository.observeSavedIds().first())
    }
}
