package com.etatech.hashiya.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperCollection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomCollectionsRepositoryTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var repository: RoomCollectionsRepository
    private lateinit var library: RoomLibraryRepository
    private var clock = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCollectionsRepository(db.collectionDao(), now = { ++clock })
        var ids = 0
        library = RoomLibraryRepository(db.paperDao(), now = { ++clock }, newId = { "local-${++ids}" })
    }

    @After
    fun tearDown() = db.close()

    private fun paper(id: String) = Paper(id, null, "Paper $id", listOf(Author("A", null)), 2020, null, null, 0, false, null)

    private suspend fun names() = repository.observeCollections().first().map { it.name }

    @Test
    fun createTrimsTheNameAndRejectsDuplicatesByCaseAndSpaces() = runTest {
        val created = repository.create("  Thesis  ")
        assertEquals(CollectionResult.Done(1), created)
        assertEquals(CollectionResult.NameTaken, repository.create("Thesis"))
        assertEquals(CollectionResult.NameTaken, repository.create(" thesis "))
        assertEquals(CollectionResult.NameTaken, repository.create("thesis"))
        assertEquals(CollectionResult.NameTaken, repository.create(" THESIS "))
        assertEquals(CollectionResult.NameTaken, repository.create("tHeSiS\t"))
        assertEquals(listOf(PaperCollection(1, "Thesis", 0)), repository.observeCollections().first())
    }

    @Test
    fun invalidNamesAreRejected() = runTest {
        assertEquals(CollectionResult.InvalidName, repository.create("   "))
        assertEquals(CollectionResult.InvalidName, repository.create("x".repeat(61)))
        assertEquals(CollectionResult.Done(1), repository.create("  " + "x".repeat(60) + "  "))
        val id = (repository.create("A") as CollectionResult.Done).id
        assertEquals(CollectionResult.InvalidName, repository.rename(id, ""))
        assertEquals(CollectionResult.InvalidName, repository.rename(id, "   "))
        assertEquals(CollectionResult.InvalidName, repository.rename(id, "x".repeat(61)))
        assertEquals(listOf("A", "x".repeat(60)), names())
    }

    @Test
    fun renameRejectsAnotherCollectionsNameByCaseAndSpaces() = runTest {
        val thesis = (repository.create("Thesis") as CollectionResult.Done).id
        val other = (repository.create("Other") as CollectionResult.Done).id

        assertEquals(CollectionResult.NameTaken, repository.rename(other, "Thesis"))
        assertEquals(CollectionResult.NameTaken, repository.rename(other, " thesis "))
        assertEquals(CollectionResult.NameTaken, repository.rename(other, "THESIS"))
        assertEquals(CollectionResult.NameTaken, repository.rename(other, "  tHeSiS"))
        assertEquals(listOf("Other", "Thesis"), names())

        assertEquals(CollectionResult.Done(thesis), repository.rename(thesis, " thesis "))
        assertEquals(listOf("Other", "thesis"), names())
        assertEquals(CollectionResult.Done(thesis), repository.rename(thesis, "THESIS"))
        // The same name again: the UPDATE still matches the row, so it is Done, not NotFound.
        assertEquals(CollectionResult.Done(thesis), repository.rename(thesis, "THESIS"))
        assertEquals(listOf("Other", "THESIS"), names())
    }

    @Test
    fun renameAllowsANewCaseButNotAnotherCollectionsName() = runTest {
        val a = (repository.create("Alpha") as CollectionResult.Done).id
        val b = (repository.create("Beta") as CollectionResult.Done).id

        assertEquals(CollectionResult.NameTaken, repository.rename(b, " alpha"))
        assertEquals(CollectionResult.Done(a), repository.rename(a, "ALPHA"))
        assertEquals(CollectionResult.Done(b), repository.rename(b, " Gamma "))
        assertEquals(listOf("ALPHA", "Gamma"), names())
        assertEquals(CollectionResult.Done(3), repository.create("beta"))
    }

    @Test
    fun renamingAMissingCollectionIsNotFound() = runTest {
        val id = (repository.create("Thesis") as CollectionResult.Done).id
        repository.delete(id)

        assertEquals(CollectionResult.NotFound, repository.rename(id, "Chapter 2"))
        assertEquals(CollectionResult.NotFound, repository.rename(id + 100, "Chapter 2"))
        assertEquals(emptyList<PaperCollection>(), repository.observeCollections().first())
    }

    @Test
    fun membershipAndDelete() = runTest {
        library.save(paper("W1"))
        val id = (repository.create("A") as CollectionResult.Done).id

        repository.setMembership(id, "W1", member = true)
        assertEquals(setOf(id), repository.observeCollectionIds("W1").first())
        assertEquals(1, repository.observeCollections().first().single().paperCount)

        repository.setMembership(id, "W1", member = false)
        assertEquals(emptySet<Long>(), repository.observeCollectionIds("W1").first())

        repository.setMembership(id, "W1", member = true)
        repository.delete(id)
        assertEquals(emptyList<PaperCollection>(), repository.observeCollections().first())
        assertEquals(listOf("W1"), library.observeLibrary("", null).first().map { it.paper.openAlexId })
    }

    @Test
    fun addingAnUnsavedPaperDoesNothing() = runTest {
        val id = (repository.create("A") as CollectionResult.Done).id

        repository.setMembership(id, "W-unsaved", member = true)
        assertEquals(emptySet<Long>(), repository.observeCollectionIds("W-unsaved").first())
        assertEquals(0, repository.observeCollections().first().single().paperCount)
    }
}
