package com.etatech.hashiya.core.data.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.ImportCounts
import com.etatech.hashiya.core.model.ImportFailure
import com.etatech.hashiya.core.model.ImportJob
import com.etatech.hashiya.core.model.ImportState
import com.etatech.hashiya.core.model.Paper
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

/** The Room side of imports; ImportRunnerTest covers the logic against FakeImportStore, which follows the same rules. */
@RunWith(RobolectricTestRunner::class)
class RoomImportStoreTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var store: RoomImportStore
    private var ids = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomImportStore(db.importJobDao(), db.paperDao(), db.collectionDao(), newId = { "row${++ids}" })
    }

    @After
    fun tearDown() = db.close()

    private fun paper(id: String, doi: String? = null) =
        Paper(id, doi, "Title $id", listOf(Author("Ada Lovelace", null)), 2020, "Venue", null, 0, false, null)

    @Test
    fun jobsRoundTripWithEveryField() = runTest {
        val job = ImportJob(
            id = 0,
            fileName = "thesis.bib",
            state = ImportState.Failed,
            counts = ImportCounts(found = 9, matched = 4, local = 3, inLibrary = 1, repeated = 1, unreadable = 2, notChecked = 1),
            processed = 7,
            imported = 6,
            addToCollection = false,
            collectionName = "thesis",
            collectionId = 3,
            failure = ImportFailure.NothingToImport
        )
        val id = store.create(job, createdAt = 1)
        assertEquals(job.copy(id = id), store.job(id))
        assertEquals(id, store.observeLatest().first()?.id)
        assertNull(store.running())
    }

    @Test
    fun replaceIfFollowsTheStoredState() = runTest {
        val id = store.create(ImportJob(0, "a.ris", ImportState.Analyzing), createdAt = 1)
        assertEquals(id, store.running()?.id)
        val ready = checkNotNull(store.job(id)).copy(state = ImportState.Ready)
        assertTrue(store.replaceIf(ImportState.Analyzing, ready))
        assertFalse(store.replaceIf(ImportState.Analyzing, ready.copy(state = ImportState.Failed)))
        assertEquals(ImportState.Ready, store.job(id)?.state)
    }

    @Test
    fun batchesSavePapersWithKeysAndReportThemAsTheJobsOwn() = runTest {
        val id = store.create(ImportJob(0, "a.bib", ImportState.Importing), createdAt = 1)
        val collection = checkNotNull(store.createCollection("a", createdAt = 1))
        assertNull(store.createCollection(" A ", createdAt = 2))
        val saved = store.saveBatch(
            id,
            listOf(PaperToSave(paper("W1", doi = "10.1/x"), "smith2020"), PaperToSave(paper("local-2"), null)),
            collection,
            savedAt = 10,
            processed = 2
        )
        assertEquals(2, saved)
        assertEquals(2 to 2, checkNotNull(store.job(id)).let { it.processed to it.imported })
        val keys = store.libraryKeys(id).sortedBy { it.openAlexId }
        assertEquals(listOf("W1", "local-2"), keys.map { it.openAlexId })
        assertEquals(listOf("smith2020", null), keys.map { it.citeKey })
        assertTrue(keys.all { it.importedByJob })
        assertEquals(
            setOf("W1", "local-2"),
            db.collectionDao().let { dao ->
                listOf("W1", "local-2").filter { dao.observeCollectionIdsForPaper(it).first().contains(collection) }.toSet()
            }
        )
        // Imported papers keep the file's order: consecutive save times, newest last.
        assertEquals(listOf("local-2", "W1"), db.paperDao().observeLibrary(null, null, null).first().map { it.paper.openAlexId })
        store.delete(id)
        assertNull(store.job(id))
        assertEquals(2, db.paperDao().observeLibrary(null, null, null).first().size)
    }
}
