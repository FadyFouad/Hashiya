package com.etatech.hashiya.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.database.model.ImportJobEntity
import com.etatech.hashiya.core.database.model.ImportedPaperRows
import com.etatech.hashiya.core.database.model.LibraryKeyRow
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
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
class ImportJobDaoTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var jobs: ImportJobDao
    private lateinit var papers: PaperDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        jobs = db.importJobDao()
        papers = db.paperDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun newJob(state: String = "importing"): Long =
        jobs.insert(ImportJobEntity(fileName = "lib.bib", createdAt = 1, state = state))

    private fun rows(id: String, openAlexId: String = "W$id", citeKey: String? = null, doi: String? = null): ImportedPaperRows {
        val paper = PaperEntity(
            id = id,
            openAlexId = openAlexId,
            doi = doi,
            title = "Title $id",
            year = 2020,
            venue = null,
            abstract = null,
            citationCount = 0,
            isOpenAccess = false,
            oaPdfUrl = null,
            savedAt = 10,
            readingStatus = "to_read",
            citeKey = citeKey,
            detailsFetched = true
        )
        return ImportedPaperRows(
            paper,
            listOf(PaperAuthorEntity(id, 0, "Ada Lovelace", null)),
            searchEntityFor(id, paper.title, listOf("Ada Lovelace"), null, null)
        )
    }

    private fun count(sql: String): Int = db.query(sql, null).use {
        it.moveToFirst()
        it.getInt(0)
    }

    @Test
    fun jobsAreStoredReadBackAndObservedNewestFirst() = runTest {
        assertNull(jobs.observeLatest().first())
        val first = newJob(state = "done")
        val second = newJob(state = "analyzing")
        assertEquals(second, jobs.observeLatest().first()?.id)
        assertEquals("done", jobs.get(first)?.state)
        jobs.update(jobs.get(second)!!.copy(state = "ready", found = 12, matched = 7, local = 3, inLibrary = 1, repeated = 1))
        val stored = jobs.get(second)!!
        assertEquals(
            listOf("ready", "12", "7", "3", "1", "1"),
            listOf(stored.state, "${stored.found}", "${stored.matched}", "${stored.local}", "${stored.inLibrary}", "${stored.repeated}")
        )
    }

    @Test
    fun runningIsTheNewestAnalyzingOrImportingJob() = runTest {
        newJob(state = "done")
        assertNull(jobs.running())
        val analyzing = newJob(state = "analyzing")
        assertEquals(analyzing, jobs.running()?.id)
        newJob(state = "failed")
        assertEquals(analyzing, jobs.running()?.id)
    }

    @Test
    fun moveStateOnlyMovesFromTheExpectedState() = runTest {
        val id = newJob(state = "importing")
        assertFalse(jobs.moveState(id, from = "ready", state = "cancelled"))
        assertTrue(jobs.moveState(id, from = "importing", state = "cancelled"))
        assertEquals("cancelled", jobs.get(id)?.state)
    }

    @Test
    fun replaceIfOnlyReplacesAJobStillInTheExpectedState() = runTest {
        val id = newJob(state = "analyzing")
        val job = jobs.get(id)!!
        assertTrue(jobs.replaceIf("analyzing", job.copy(state = "ready", found = 3)))
        assertFalse(jobs.replaceIf("analyzing", job.copy(state = "failed")))
        assertEquals("ready" to 3, jobs.get(id)!!.let { it.state to it.found })
    }

    @Test
    fun aBatchSavesPapersLinksThemToTheJobAndCountsThem() = runTest {
        val id = newJob()
        val saved = papers.insertImportBatch(id, listOf(rows("a"), rows("b")), collectionId = null, addedAt = 5, processed = 2)
        assertEquals(2, saved)
        assertEquals(2, count("SELECT COUNT(*) FROM papers"))
        assertEquals(2, count("SELECT COUNT(*) FROM paper_authors"))
        assertEquals(2, count("SELECT COUNT(*) FROM paper_search"))
        assertEquals(2, count("SELECT COUNT(*) FROM import_job_papers WHERE job_id = $id"))
        assertEquals(2 to 2, jobs.get(id)!!.let { it.processed to it.imported })
    }

    @Test
    fun aPaperAlreadySavedIsSkippedButStillCountsAsProcessed() = runTest {
        val id = newJob()
        papers.insertImportBatch(id, listOf(rows("a", openAlexId = "W1")), collectionId = null, addedAt = 5, processed = 1)
        val saved = papers.insertImportBatch(
            id,
            listOf(rows("b", openAlexId = "W1"), rows("c")),
            collectionId = null,
            addedAt = 5,
            processed = 3
        )
        assertEquals(1, saved)
        assertEquals(3 to 2, jobs.get(id)!!.let { it.processed to it.imported })
    }

    @Test
    fun aCiteKeyAnotherPaperHoldsIsDroppedNotFatal() = runTest {
        val id = newJob()
        papers.insertImportBatch(id, listOf(rows("a", citeKey = "smith2020")), collectionId = null, addedAt = 5, processed = 1)
        papers.insertImportBatch(id, listOf(rows("b", citeKey = "smith2020")), collectionId = null, addedAt = 5, processed = 2)
        assertEquals(1, count("SELECT COUNT(*) FROM papers WHERE id = 'b' AND cite_key IS NULL"))
    }

    @Test
    fun everyPaperTheJobSavedGoesIntoTheCollection() = runTest {
        val id = newJob()
        val collection = checkNotNull(db.collectionDao().insertCollection("lib", "lib", createdAt = 1))
        papers.insertImportBatch(id, listOf(rows("a")), collectionId = null, addedAt = 5, processed = 1)
        papers.insertImportBatch(id, listOf(rows("b")), collectionId = collection, addedAt = 6, processed = 2)
        assertEquals(2, count("SELECT COUNT(*) FROM collection_papers WHERE collection_id = $collection"))
    }

    @Test
    fun aCollectionDeletedMeanwhileIsSkippedAndThePapersAreStillSaved() = runTest {
        val id = newJob()
        val collection = checkNotNull(db.collectionDao().insertCollection("lib", "lib", createdAt = 1))
        db.collectionDao().deleteCollection(collection)
        assertEquals(1, papers.insertImportBatch(id, listOf(rows("a")), collectionId = collection, addedAt = 5, processed = 1))
        assertEquals(0, count("SELECT COUNT(*) FROM collection_papers"))
    }

    @Test
    fun deletingAJobKeepsItsPapers() = runTest {
        val id = newJob()
        papers.insertImportBatch(id, listOf(rows("a")), collectionId = null, addedAt = 5, processed = 1)
        jobs.delete(id)
        assertNull(jobs.get(id))
        assertEquals(1, count("SELECT COUNT(*) FROM papers"))
        assertEquals(0, count("SELECT COUNT(*) FROM import_job_papers"))
    }

    @Test
    fun libraryKeysTellTheJobsOwnPapersFromTheRest() = runTest {
        papers.insertPaperWithAuthors(
            rows("x", doi = "10.1/x").paper,
            emptyList(),
            searchEntityFor("x", "Title x", emptyList(), null, null)
        )
        val id = newJob()
        papers.insertImportBatch(id, listOf(rows("a", citeKey = "k")), collectionId = null, addedAt = 5, processed = 1)
        assertEquals(
            listOf(
                LibraryKeyRow("a", "Wa", null, "Title a", 2020, "k", importedByJob = true),
                LibraryKeyRow("x", "Wx", "10.1/x", "Title x", 2020, null, importedByJob = false)
            ),
            jobs.libraryKeys(id).sortedBy { it.id }
        )
    }
}
