package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.CollectionResult
import com.etatech.hashiya.core.model.PaperCollection
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** The fake must behave like RoomCollectionsRepository, or feature tests prove nothing. */
class FakeCollectionsRepositoryTest {
    private val library = FakeLibraryRepository()
    private val collections = FakeCollectionsRepository(library)

    @Test
    fun behavesLikeTheRealOne() = runTest {
        library.save(SamplePapers.attention)
        library.save(SamplePapers.bert)
        val id = (collections.create(" Thesis ") as CollectionResult.Done).id
        assertEquals(CollectionResult.NameTaken, collections.create("THESIS"))
        assertEquals(CollectionResult.InvalidName, collections.create(" "))

        collections.setMembership(id, SamplePapers.bert.openAlexId, member = true)
        assertEquals(listOf(PaperCollection(id, "Thesis", 1)), collections.observeCollections().first())
        assertEquals(listOf(SamplePapers.bert.title), library.observeLibrary("", null, id).first().map { it.paper.title })
        assertEquals(1, library.observeStatusCounts("", id).first().values.sum())

        val removed = checkNotNull(library.remove(SamplePapers.bert.openAlexId))
        assertEquals(setOf(id), removed.collectionIds)
        assertEquals(0, collections.observeCollections().first().single().paperCount)
        library.restore(removed)
        assertEquals(setOf(id), collections.observeCollectionIds(SamplePapers.bert.openAlexId).first())

        collections.delete(id)
        assertEquals(emptyList<PaperCollection>(), collections.observeCollections().first())
        assertEquals(2, library.observeLibrary("", null).first().size)
    }

    @Test
    fun renameChecksInRoomsOrder() = runTest {
        val thesis = (collections.create("Thesis") as CollectionResult.Done).id
        val reading = (collections.create("Reading") as CollectionResult.Done).id

        assertEquals(CollectionResult.InvalidName, collections.rename(99, " "))
        assertEquals(CollectionResult.NotFound, collections.rename(99, "Reading"))
        assertEquals(CollectionResult.NameTaken, collections.rename(reading, " thesis "))
        assertEquals(CollectionResult.Done(thesis), collections.rename(thesis, "THESIS"))
        assertEquals(listOf("Reading", "THESIS"), collections.observeCollections().first().map { it.name })
    }

    @Test
    fun undoSkipsCollectionsDeletedMeanwhile() = runTest {
        library.save(SamplePapers.bert)
        val kept = (collections.create("Kept") as CollectionResult.Done).id
        val gone = (collections.create("Gone") as CollectionResult.Done).id
        collections.setMembership(kept, SamplePapers.bert.openAlexId, member = true)
        collections.setMembership(gone, SamplePapers.bert.openAlexId, member = true)

        val removed = checkNotNull(library.remove(SamplePapers.bert.openAlexId))
        assertEquals(setOf(kept, gone), removed.collectionIds)
        collections.delete(gone)
        library.restore(removed)

        assertEquals(setOf(kept), collections.observeCollectionIds(SamplePapers.bert.openAlexId).first())
        assertEquals(listOf(SamplePapers.bert.title), library.observeLibrary("", null, kept).first().map { it.paper.title })
        assertEquals(emptyList<String>(), library.observeLibrary("", null, gone).first().map { it.paper.title })
    }

    @Test
    fun membershipEdgeCases() = runTest {
        library.save(SamplePapers.bert)
        val id = (collections.create("Thesis") as CollectionResult.Done).id

        collections.setMembership(id, SamplePapers.vit.openAlexId, member = true)
        collections.setMembership(99, SamplePapers.vit.openAlexId, member = true)
        collections.setMembership(99, SamplePapers.bert.openAlexId, member = false)
        assertEquals(emptySet<Long>(), collections.observeCollectionIds(SamplePapers.vit.openAlexId).first())
        assertEquals(0, collections.observeCollections().first().single().paperCount)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { collections.setMembership(99, SamplePapers.bert.openAlexId, member = true) }
        }

        collections.setMembership(id, SamplePapers.bert.openAlexId, member = true)
        collections.setMembership(id, SamplePapers.bert.openAlexId, member = true)
        assertEquals(1, collections.observeCollections().first().single().paperCount)
        collections.setMembership(id, SamplePapers.bert.openAlexId, member = false)
        assertEquals(emptySet<Long>(), collections.observeCollectionIds(SamplePapers.bert.openAlexId).first())
    }

    @Test
    fun failOnChangeFailsEveryWrite() = runTest {
        library.save(SamplePapers.bert)
        val id = (collections.create("Thesis") as CollectionResult.Done).id
        collections.failOnChange = true

        assertThrows(IOException::class.java) { runBlocking { collections.create("Reading") } }
        assertThrows(IOException::class.java) { runBlocking { collections.rename(id, "Reading") } }
        assertThrows(IOException::class.java) { runBlocking { collections.setMembership(id, SamplePapers.bert.openAlexId, member = true) } }
        assertThrows(IOException::class.java) { runBlocking { collections.delete(id) } }
        assertEquals(listOf(PaperCollection(id, "Thesis", 0)), collections.observeCollections().first())
    }
}
