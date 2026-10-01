package com.etatech.hashiya.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.PublicationDetails
import com.etatech.hashiya.core.model.ReadingStatus
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

    private fun paper(id: String, title: String = "Paper $id", abstract: String? = null, venue: String? = null) = Paper(
        openAlexId = id,
        doi = null,
        title = title,
        authors = listOf(Author("First", null), Author("Second", null)),
        year = 2020,
        venue = venue,
        abstract = abstract,
        citationCount = 0,
        isOpenAccess = false,
        openAccessPdfUrl = null
    )

    private suspend fun ids(query: String = "", status: ReadingStatus? = null) =
        repository.observeLibrary(query, status).first().map { it.paper.openAlexId }

    @Test
    fun savedPapersAreNewestFirstWithAuthorsInOrderAndStartAsToRead() = runTest {
        repository.save(paper("W1"))
        repository.save(paper("W2"))

        val saved = repository.observeLibrary("", null).first()
        assertEquals(listOf("W2", "W1"), saved.map { it.paper.openAlexId })
        assertEquals(listOf("First", "Second"), saved.first().paper.authors.map { it.name })
        assertEquals(listOf(ReadingStatus.ToRead, ReadingStatus.ToRead), saved.map { it.status })
    }

    @Test
    fun observesSavedIds() = runTest {
        repository.save(paper("W1"))
        assertEquals(setOf("W1"), repository.observeSavedIds().first())
    }

    @Test
    fun savingTwiceKeepsOneCopyAndItsStatus() = runTest {
        repository.save(paper("W1"))
        repository.setStatus("W1", ReadingStatus.Reading)
        repository.save(paper("W1"))

        assertEquals(listOf(LibraryPaper(paper("W1"), ReadingStatus.Reading)), repository.observeLibrary("", null).first())
    }

    @Test
    fun removeThenRestoreReturnsPaperToItsPositionWithItsStatus() = runTest {
        repository.save(paper("W1"))
        repository.save(paper("W2"))
        repository.save(paper("W3"))
        repository.setStatus("W2", ReadingStatus.Reading)

        val removed = repository.remove("W2")!!
        assertEquals(ReadingStatus.Reading, removed.status)
        assertEquals(listOf("W3", "W1"), ids())

        repository.restore(removed)
        assertEquals(listOf("W3", "W2", "W1"), ids())
        assertEquals(listOf("W2"), ids(status = ReadingStatus.Reading))
        assertEquals(listOf("W2"), ids(query = "paper w2"))
    }

    @Test
    fun restoreAfterPaperWasSavedAgainIsNoOp() = runTest {
        repository.save(paper("W1"))
        val removed = repository.remove("W1")!!
        repository.save(paper("W1"))

        repository.restore(removed)

        assertEquals(1, repository.observeLibrary("", null).first().size)
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

    @Test
    fun setStatusDoesNotReorder() = runTest {
        repository.save(paper("W1"))
        repository.save(paper("W2"))

        repository.setStatus("W1", ReadingStatus.Read)

        assertEquals(listOf("W2", "W1"), ids())
        assertEquals(listOf("W1"), ids(status = ReadingStatus.Read))
    }

    @Test
    fun setStatusOfUnsavedPaperDoesNothing() = runTest {
        repository.setStatus("missing", ReadingStatus.Read)
        assertEquals(emptyList<String>(), ids())
    }

    @Test
    fun searchFindsTitleAuthorAbstractAndVenueByPrefix() = runTest {
        repository.save(paper("W1", title = "Attention Is All You Need", abstract = "The Transformer architecture", venue = "NeurIPS"))
        repository.save(paper("W2", title = "Deep Residual Learning", venue = "CVPR").copy(authors = listOf(Author("Kaiming He", null))))

        assertEquals(listOf("W1"), ids("transf"))
        assertEquals(listOf("W2"), ids("kaiming"))
        assertEquals(listOf("W1"), ids("neurips"))
        assertEquals(listOf("W2"), ids("DEEP resid"))
        assertEquals(emptyList<String>(), ids("attention residual"))
        assertEquals(listOf("W2", "W1"), ids("   "))
    }

    @Test
    fun searchIgnoresAccentsTashkeelAndAlefForms() = runTest {
        repository.save(paper("W1", title = "Schrödinger equations"))
        repository.save(paper("W2", title = "تطبيقات التعلم العميق في معالجة اللغة"))
        repository.save(paper("W3", title = "أساسيات الإحصاء"))

        assertEquals(listOf("W1"), ids("schrodinger"))
        assertEquals(listOf("W2"), ids("التَّعلُّم"))
        assertEquals(listOf("W3"), ids("اساسيات"))
        assertEquals(listOf("W3"), ids("الاحصاء"))
    }

    @Test
    fun searchMatchesArabicIndicAndAsciiDigitsEitherWay() = runTest {
        repository.save(paper("W1", title = "COVID-19 outcomes"))
        repository.save(paper("W2", title = "جائحة كوفيد-١٩"))

        assertEquals(listOf("W2", "W1"), ids("١٩"))
        assertEquals(listOf("W2", "W1"), ids("19"))
        assertEquals(listOf("W2"), ids("كوفيد ۱۹"))
    }

    /** Whatever the user types, the query reaches SQLite as plain words: never a syntax error. */
    @Test
    fun searchTextWithFtsSyntaxNeverFails() = runTest {
        repository.save(paper("W1", title = "C++ templates: a guide"))

        val queries = listOf("\"", "C++", "templates\"", "-templates", "(guide", "title:guide", "BERT:", "a AND", "NEAR/2", "*", "^x")
        queries.forEach { query ->
            repository.observeLibrary(query, null).first()
            repository.observeStatusCounts(query).first()
        }
        assertEquals(listOf("W1"), ids("\"templates"))
        assertEquals(listOf("W1"), ids("*"))
    }

    @Test
    fun statusCountsFollowTheSearchAndFillMissingStatusesWithZero() = runTest {
        repository.save(paper("W1", title = "Transformers one"))
        repository.save(paper("W2", title = "Transformers two"))
        repository.save(paper("W3", title = "Convolutions"))
        repository.setStatus("W1", ReadingStatus.Reading)

        assertEquals(
            mapOf(ReadingStatus.ToRead to 2, ReadingStatus.Reading to 1, ReadingStatus.Read to 0),
            repository.observeStatusCounts("").first()
        )
        assertEquals(
            mapOf(ReadingStatus.ToRead to 1, ReadingStatus.Reading to 1, ReadingStatus.Read to 0),
            repository.observeStatusCounts("transf").first()
        )
        assertEquals(
            mapOf(ReadingStatus.ToRead to 0, ReadingStatus.Reading to 0, ReadingStatus.Read to 0),
            repository.observeStatusCounts("missing").first()
        )
    }

    @Test
    fun unknownStoredStatusReadsAsToReadAndIsCountedThere() = runTest {
        repository.save(paper("W1"))
        repository.save(paper("W2"))
        db.openHelper.writableDatabase.execSQL("UPDATE papers SET reading_status = 'archived' WHERE open_alex_id = 'W1'")

        assertEquals(ReadingStatus.ToRead, repository.observeLibrary("", null).first().last().status)
        assertEquals(
            mapOf(ReadingStatus.ToRead to 2, ReadingStatus.Reading to 0, ReadingStatus.Read to 0),
            repository.observeStatusCounts("").first()
        )
    }

    @Test
    fun observePaperFollowsTheSavedPaperAndItsStatus() = runTest {
        repository.save(paper("W1"))
        repository.setStatus("W1", ReadingStatus.Reading)

        assertEquals(LibraryPaper(paper("W1"), ReadingStatus.Reading), repository.observePaper("W1").first())
        repository.remove("W1")
        assertNull(repository.observePaper("W1").first())
    }

    @Test
    fun notesAreEmptyUntilSavedAndReadBackAfter() = runTest {
        repository.save(paper("W1"))
        assertEquals(PaperNotes(), repository.observeNotes("W1").first())

        repository.saveNotes("W1", PaperNotes(summary = "Transformers", limitations = "English only"))

        assertEquals(PaperNotes(summary = "Transformers", limitations = "English only"), repository.observeNotes("W1").first())
    }

    @Test
    fun savingNotesForAnUnsavedPaperDoesNothing() = runTest {
        repository.saveNotes("W1", PaperNotes(summary = "x"))

        assertEquals(PaperNotes(), repository.observeNotes("W1").first())
        assertEquals(emptyList<String>(), ids())
    }

    @Test
    fun libraryIsSearchableByItsNotes() = runTest {
        repository.save(paper("W1"))
        repository.save(paper("W2"))

        repository.saveNotes("W2", PaperNotes(method = "Randomized controlled trial"))

        assertEquals(listOf("W2"), ids("randomiz"))
        assertEquals(listOf("W2"), ids("Controlled TRIAL"))
        assertEquals(
            mapOf(ReadingStatus.ToRead to 1, ReadingStatus.Reading to 0, ReadingStatus.Read to 0),
            repository.observeStatusCounts("trial").first()
        )
    }

    @Test
    fun removeThenRestoreKeepsTheNotesAndTheirSearch() = runTest {
        repository.save(paper("W1"))
        repository.saveNotes("W1", PaperNotes(thoughts = "Cite in chapter two"))

        val removed = repository.remove("W1")!!
        assertEquals(PaperNotes(thoughts = "Cite in chapter two"), removed.notes)
        assertEquals(emptyList<String>(), ids("chapter"))

        repository.restore(removed)
        assertEquals(PaperNotes(thoughts = "Cite in chapter two"), repository.observeNotes("W1").first())
        assertEquals(listOf("W1"), ids("chapter"))
    }

    @Test
    fun removeThenRestoreWithoutNotesStaysWithoutNotes() = runTest {
        repository.save(paper("W1"))

        val removed = repository.remove("W1")!!
        repository.restore(removed)

        assertEquals(PaperNotes(), removed.notes)
        assertEquals(PaperNotes(), repository.observeNotes("W1").first())
        assertEquals(listOf("W1"), ids("paper"))
    }

    @Test
    fun savesPublicationDetailsAsFetched() = runTest {
        val details = PublicationDetails("article", "journal", "Springer", "521", "7553", "436", "444")
        repository.save(paper("W1").copy(publication = details))

        val row = checkNotNull(db.citationDao().getPaper("W1")).paper
        assertEquals("Springer", row.publisher)
        assertEquals(true, row.detailsFetched)
        assertEquals(details, repository.observePaper("W1").first()?.paper?.publication)
    }

    @Test
    fun libraryAndCountsFollowTheSelectedCollection() = runTest {
        repository.save(paper("W1", title = "Graph networks"))
        repository.save(paper("W2", title = "Graph kernels"))
        val collections = db.collectionDao()
        val id = checkNotNull(collections.insertCollection("A", "a", createdAt = 1))
        collections.addToCollection(id, "W1", addedAt = 1)

        assertEquals(listOf("W1"), repository.observeLibrary("graph", null, id).first().map { it.paper.openAlexId })
        assertEquals(1, repository.observeStatusCounts("", id).first().values.sum())
        assertEquals(2, repository.observeStatusCounts("").first().values.sum())
    }

    @Test
    fun removeThenRestoreKeepsCollectionsCiteKeyAndFetchedFlag() = runTest {
        val details = PublicationDetails("article", "journal", "Springer", "521", "7553", "436", "444")
        repository.save(paper("W1").copy(publication = details))
        val collections = db.collectionDao()
        val kept = checkNotNull(collections.insertCollection("Kept", "kept", createdAt = 1))
        val gone = checkNotNull(collections.insertCollection("Gone", "gone", createdAt = 1))
        collections.addToCollection(kept, "W1", addedAt = 7)
        collections.addToCollection(gone, "W1", addedAt = 8)
        db.citationDao().assignCiteKeys(mapOf("local-1" to "first2020paper"))

        val removed = checkNotNull(repository.remove("W1"))
        assertEquals(setOf(kept, gone), removed.collectionIds)
        assertEquals(mapOf(kept to 7L, gone to 8L), removed.collectionLinksAddedAt)
        assertEquals("first2020paper", removed.citeKey)
        assertEquals(true, removed.detailsFetched)
        collections.deleteCollection(gone)
        repository.restore(removed)

        assertEquals(listOf(kept), collections.observeCollectionIdsForPaper("W1").first())
        val row = checkNotNull(db.citationDao().getPaper("W1")).paper
        assertEquals("first2020paper", row.citeKey)
        assertEquals(true, row.detailsFetched)
        assertEquals(details, repository.observePaper("W1").first()?.paper?.publication)
    }

    @Test
    fun restoreKeepsAPreV4PaperUnfetched() = runTest {
        repository.save(paper("W1"))
        db.openHelper.writableDatabase.execSQL("UPDATE papers SET details_fetched = 0 WHERE open_alex_id = 'W1'")

        val removed = checkNotNull(repository.remove("W1"))
        assertEquals(false, removed.detailsFetched)
        repository.restore(removed)

        assertEquals(false, checkNotNull(db.citationDao().getPaper("W1")).paper.detailsFetched)
    }

    @Test
    fun restoreDropsACiteKeyAnotherPaperTookMeanwhile() = runTest {
        repository.save(paper("W1"))
        db.citationDao().assignCiteKeys(mapOf("local-1" to "first2020paper"))
        val removed = checkNotNull(repository.remove("W1"))
        repository.save(paper("W2"))
        db.citationDao().assignCiteKeys(mapOf("local-2" to "first2020paper"))

        repository.restore(removed)

        assertEquals(setOf("W1", "W2"), repository.observeSavedIds().first())
        assertNull(checkNotNull(db.citationDao().getPaper("W1")).paper.citeKey)
    }

    @Test
    fun aPaperWithAStoredPdfIsMarkedInTheLibrary() = runTest {
        repository.save(paper("W1"))
        repository.save(paper("W2"))
        val dao = db.paperDao()

        dao.setPdf(checkNotNull(dao.paperIdFor("W1")), "downloaded", size = 10, addedAt = 1)

        assertEquals(
            listOf("W2" to false, "W1" to true),
            repository.observeLibrary("", null).first().map { it.paper.openAlexId to it.hasPdf }
        )
    }
}
