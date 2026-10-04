package com.etatech.hashiya.core.database.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.database.model.IncomingCollection
import com.etatech.hashiya.core.database.model.IncomingPaper
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperNotesEntity
import com.etatech.hashiya.core.database.model.asPaperNotes
import com.etatech.hashiya.core.database.model.searchEntityFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupDaoTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var dao: BackupDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.backupDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entity(id: String, openAlexId: String? = null, doi: String? = null, title: String = "Paper $id", citeKey: String? = null) =
        PaperEntity(
            id = id, openAlexId = openAlexId, doi = doi, title = title, year = 2020, venue = null, abstract = null,
            citationCount = 0, isOpenAccess = false, oaPdfUrl = null, savedAt = 1, readingStatus = "to_read", citeKey = citeKey
        )

    private fun notes(paperId: String, summary: String) = PaperNotesEntity(paperId, summary, "", "", "", "", "", updatedAt = 1)

    /** Saves a paper the way the app does, so the device side of a merge looks real. */
    private suspend fun saved(entity: PaperEntity, notes: PaperNotesEntity? = null) {
        db.paperDao().insertPaperWithAuthors(
            entity,
            listOf(PaperAuthorEntity(entity.id, 0, "Device Author", null)),
            searchEntityFor(entity.id, entity.title, listOf("Device Author"), null, null, notes?.asPaperNotes()),
            notes
        )
    }

    private fun incoming(ref: Int, entity: PaperEntity, notes: PaperNotesEntity? = null, authors: List<String> = listOf("A")) =
        IncomingPaper(ref, entity, authors.mapIndexed { i, name -> PaperAuthorEntity(entity.id, i, name, null) }, notes)

    @Test
    fun addsNewPapersWithAuthorsNotesAndSearchRow() = runTest {
        val outcome = dao.merge(listOf(incoming(1, entity("n1", "W1"), notes("n1", "Backup summary"), listOf("Ada", "Grace"))), emptyList(), now = 9)

        assertEquals(1, outcome.added)
        val paper = db.paperDao().getByOpenAlexId("W1")!!
        assertEquals(listOf("Ada", "Grace"), paper.authors.sortedBy { it.position }.map { it.name })
        assertEquals("Backup summary", db.paperDao().observeNotes("W1").first()?.summary)
        assertEquals(1, db.paperDao().observeLibrary("summary*", null, null).first().size)
    }

    @Test
    fun matchesByOpenAlexIdAndKeepsTheDevicePaper() = runTest {
        saved(entity("d1", "W1", title = "Device title").copy(readingStatus = "read"), notes("d1", "Device notes"))

        val outcome = dao.merge(
            listOf(incoming(1, entity("n1", "W1", title = "Backup title"), notes("n1", "Backup notes"))),
            emptyList(),
            now = 9
        )

        assertEquals(0, outcome.added)
        assertEquals(1, outcome.matched)
        assertEquals(0, outcome.notesAdded)
        val paper = db.paperDao().getByOpenAlexId("W1")!!.paper
        assertEquals("Device title", paper.title)
        assertEquals("read", paper.readingStatus)
        assertEquals("Device notes", db.paperDao().observeNotes("W1").first()?.summary)
    }

    @Test
    fun backupNotesFillAMatchedPaperWithoutNotes() = runTest {
        saved(entity("d1", "W1"))
        val outcome = dao.merge(listOf(incoming(1, entity("n1", "W1"), notes("n1", "Backup notes"))), emptyList(), now = 9)
        assertEquals(1, outcome.notesAdded)
        assertEquals("Backup notes", db.paperDao().observeNotes("W1").first()?.summary)
        assertEquals(1, db.paperDao().observeLibrary("backup*", null, null).first().size)
    }

    @Test
    fun matchesByDoiOnlyWhenTheBackupPaperHasNoOpenAlexId() = runTest {
        saved(entity("d1", "W1", doi = "10.1/x"))

        val byDoi = dao.merge(listOf(incoming(1, entity("n1", openAlexId = null, doi = "10.1/x"))), emptyList(), now = 9)
        assertEquals(1, byDoi.matched)

        // Another work with the same DOI (a preprint and its published version) is a separate paper.
        val otherWork = dao.merge(listOf(incoming(1, entity("n2", openAlexId = "W2", doi = "10.1/x"))), emptyList(), now = 9)
        assertEquals(1, otherWork.added)
    }

    @Test
    fun aPaperWithNeitherIdIsAlwaysNew() = runTest {
        val first = dao.merge(listOf(incoming(1, entity("n1"))), emptyList(), now = 9)
        val second = dao.merge(listOf(incoming(1, entity("n2"))), emptyList(), now = 9)
        assertEquals(1, first.added)
        assertEquals(1, second.added)
        assertEquals(2, dao.paperCount())
    }

    @Test
    fun aTakenCiteKeyIsDropped() = runTest {
        saved(entity("d1", "W1", citeKey = "smith2020"))
        dao.merge(listOf(incoming(1, entity("n1", "W2", citeKey = "smith2020"))), emptyList(), now = 9)
        assertNull(db.paperDao().getByOpenAlexId("W2")!!.paper.citeKey)
    }

    @Test
    fun pdfColumnsAreSetOnlyWhenTheMatchedPaperHasNone() = runTest {
        saved(entity("d1", "W1"))
        saved(entity("d2", "W2"))
        db.paperDao().setPdf("d2", "attached", 5, 1)
        val withPdf = { id: String, oa: String -> entity(id, oa).copy(pdfSource = "downloaded", pdfSize = 7, pdfAddedAt = 3, pdfLastPage = 2) }

        val outcome = dao.merge(listOf(incoming(1, withPdf("n1", "W1")), incoming(2, withPdf("n2", "W2")), incoming(3, withPdf("n3", "W3"))), emptyList(), now = 9)

        assertEquals(mapOf(1 to "d1", 3 to "n3"), outcome.pdfTargets)
        assertEquals(2, db.paperDao().getByOpenAlexId("W1")!!.paper.pdfLastPage)
        assertEquals("attached", db.paperDao().getByOpenAlexId("W2")!!.paper.pdfSource)
    }

    @Test
    fun collectionsMergeByNameKeyAndLinkNewAndMatchedPapers() = runTest {
        saved(entity("d1", "W1"))
        val existing = db.collectionDao().insertCollection("Thesis", "thesis", 1)!!

        val outcome = dao.merge(
            listOf(incoming(1, entity("n1", "W1")), incoming(2, entity("n2", "W2"))),
            listOf(IncomingCollection("THESIS ", "thesis", 5, listOf(1, 2)), IncomingCollection("Review", "review", 6, listOf(2, 99))),
            now = 9
        )

        assertEquals(1, outcome.collectionsCreated)
        val counts = db.collectionDao().observeCollections().first().associate { it.name to it.paperCount }
        assertEquals(mapOf("Review" to 1, "Thesis" to 2), counts)
        assertTrue(db.collectionDao().observeCollectionIdsForPaper("W1").first().contains(existing))
    }

    @Test
    fun aFailureRollsBackTheWholeMerge() = runTest {
        val broken = IncomingPaper(2, entity("n2", "W2"), listOf(PaperAuthorEntity("no-such-paper", 0, "X", null)), null)
        try {
            dao.merge(listOf(incoming(1, entity("n1", "W1")), broken), emptyList(), now = 9)
            fail("Expected the foreign key to fail")
        } catch (e: SQLiteConstraintException) {
            // expected
        }
        assertEquals(0, dao.paperCount())
    }

    @Test
    fun snapshotReadsEverything() = runTest {
        saved(entity("d1", "W1"), notes("d1", "N"))
        val id = db.collectionDao().insertCollection("C", "c", 1)!!
        db.collectionDao().addToCollection(id, "W1", 2)

        val snapshot = dao.snapshot()

        assertEquals(listOf("d1"), snapshot.papers.map { it.paper.id })
        assertEquals(listOf("N"), snapshot.notes.map { it.summary })
        assertEquals(listOf("C"), snapshot.collections.map { it.name })
        assertEquals(listOf(id to "d1"), snapshot.links.map { it.collectionId to it.paperId })
    }
}
