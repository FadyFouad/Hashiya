package com.etatech.hashiya.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.data.FakeOpenAlexLookupDataSource
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.OpenAlexLookupDataSource
import com.etatech.hashiya.core.network.model.NetworkBiblio
import com.etatech.hashiya.core.network.model.NetworkLocation
import com.etatech.hashiya.core.network.model.NetworkSource
import com.etatech.hashiya.core.network.model.NetworkWork
import com.etatech.hashiya.core.network.model.NetworkWorksResponse
import kotlinx.coroutines.delay
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
class RoomCitationRepositoryTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var library: RoomLibraryRepository
    private val openAlex = FakeOpenAlexLookupDataSource()
    private var clock = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var ids = 0
        library = RoomLibraryRepository(db.paperDao(), now = { ++clock }, newId = { "local-${++ids}" })
    }

    @After
    fun tearDown() = db.close()

    private fun repository(source: OpenAlexLookupDataSource = openAlex) = RoomCitationRepository(db.citationDao(), source)

    private fun paper(id: String, surname: String, title: String = "Deep nets") =
        Paper(id, null, title, listOf(Author("Jane $surname", null)), 2020, "Nature", null, 0, false, null)

    /** Saves the paper as a v3 library left it: no details, details_fetched = 0. */
    private suspend fun saveUnfetched(paper: Paper) {
        library.save(paper)
        db.openHelper.writableDatabase.execSQL("UPDATE papers SET details_fetched = 0 WHERE open_alex_id = ?", arrayOf(paper.openAlexId))
    }

    private fun journalWork(id: String) = NetworkWork(
        id = "https://openalex.org/$id",
        type = "article",
        primaryLocation = NetworkLocation(source = NetworkSource("Nature", type = "journal")),
        biblio = NetworkBiblio(volume = "521", firstPage = "436", lastPage = "444")
    )

    /** A data source that runs [onGet] for each request, then answers with a journal article. */
    private fun source(onGet: suspend (String) -> Unit) = object : OpenAlexLookupDataSource {
        override suspend fun getWork(id: String): NetworkWork? {
            onGet(id)
            return journalWork(id)
        }

        override suspend fun findWorks(filter: String, perPage: Int): NetworkWorksResponse = error("unused")
    }

    private suspend fun detailsFetched(openAlexId: String) = checkNotNull(db.citationDao().getPaper(openAlexId)).paper.detailsFetched

    @Test
    fun entryRefetchesOnceThenUsesStoredDetailsAndKey() = runTest {
        saveUnfetched(paper("W1", "Smith"))
        openAlex.works = mapOf("W1" to journalWork("W1"))

        val first = checkNotNull(repository().entry("W1"))
        val second = checkNotNull(repository().entry("W1"))

        assertTrue(first.complete)
        assertEquals(listOf("W1"), openAlex.workRequests)
        assertTrue(first.bibtex.startsWith("@article{smith2020deep,\n"))
        assertTrue(first.bibtex.contains("  volume = {521},"))
        assertEquals(first, second)
    }

    @Test
    fun papersSavedAfterV4AreNotRefetched() = runTest {
        library.save(paper("W1", "Smith"))
        repository().entry("W1")
        repository().export(null)
        assertEquals(emptyList<String>(), openAlex.workRequests)
    }

    @Test
    fun unsavedPaperHasNoEntry() = runTest {
        assertNull(repository().entry("W404"))
    }

    @Test
    fun failedRefetchIsIncompleteAndRetriedNextTime() = runTest {
        saveUnfetched(paper("W1", "Smith"))
        openAlex.getFailure = NetworkFailure.Connectivity

        val offline = repository().export(null)
        assertFalse(offline.complete)
        assertTrue(offline.bibtex.startsWith("@misc{smith2020deep,"))
        assertFalse(detailsFetched("W1"))

        openAlex.getFailure = null
        openAlex.works = mapOf("W1" to journalWork("W1"))
        val online = repository().export(null)
        assertTrue(online.complete)
        assertTrue(online.bibtex.startsWith("@article{smith2020deep,"))
        assertTrue(online.bibtex.contains("  pages = {436--444},"))
        assertEquals(listOf("W1", "W1"), openAlex.workRequests)
        assertTrue(detailsFetched("W1"))
    }

    @Test
    fun oneFailedRefetchDoesNotStopTheOthers() = runTest {
        saveUnfetched(paper("W1", "Adams"))
        saveUnfetched(paper("W2", "Brown"))
        val flaky = source { id -> if (id == "W1") throw NetworkException(NetworkFailure.Connectivity) }

        val result = repository(flaky).export(null)

        assertFalse(result.complete)
        assertTrue(result.bibtex.contains("@misc{adams2020deep,"))
        assertTrue(result.bibtex.contains("@article{brown2020deep,"))
        assertFalse(detailsFetched("W1"))
        assertTrue(detailsFetched("W2"))
    }

    @Test
    fun aWorkOpenAlexNoLongerHasIsMarkedFetched() = runTest {
        saveUnfetched(paper("W1", "Smith"))
        openAlex.works = emptyMap()

        assertTrue(repository().export(null).complete)
        repository().export(null)
        assertEquals(listOf("W1"), openAlex.workRequests)
    }

    @Test
    fun keysAreAssignedInSavedOrderAndNeverChange() = runTest {
        library.save(paper("W1", "Smith"))
        library.save(paper("W2", "Smith"))
        val first = repository().export(null)
        assertTrue(first.bibtex.contains("@misc{smith2020deep,"))
        assertTrue(first.bibtex.contains("@misc{smith2020deepa,"))

        // A paper saved later with the same base key gets the next suffix; the first two keep theirs.
        library.save(paper("W3", "Smith"))
        val removed = checkNotNull(library.remove("W1"))
        library.restore(removed)
        val again = repository().export(null)
        assertEquals(
            listOf("smith2020deep", "smith2020deepa", "smith2020deepb"),
            Regex("""@misc\{(\w+),""").findAll(again.bibtex).map { it.groupValues[1] }.toList()
        )
        assertEquals("smith2020deep", db.citationDao().getPaper("W1")?.paper?.citeKey)
    }

    @Test
    fun keysDoNotDependOnWhichCollectionIsExportedFirst() = runTest {
        library.save(paper("W1", "Smith"))
        library.save(paper("W2", "Smith"))
        val id = checkNotNull(db.collectionDao().insertCollection("A", "a", createdAt = 1))
        db.collectionDao().addToCollection(id, "W2", addedAt = 1)

        assertTrue(repository().export(id).bibtex.startsWith("@misc{smith2020deepa,"))
        assertEquals("smith2020deep", db.citationDao().getPaper("W1")?.paper?.citeKey)
    }

    @Test
    fun exportCoversTheWholeCollectionRegardlessOfStatus() = runTest {
        library.save(paper("W1", "Adams"))
        library.save(paper("W2", "Brown"))
        library.save(paper("W3", "Clark"))
        library.setStatus("W2", ReadingStatus.Read)
        val collections = db.collectionDao()
        val id = checkNotNull(collections.insertCollection("A", "a", createdAt = 1))
        collections.addToCollection(id, "W1", addedAt = 1)
        collections.addToCollection(id, "W2", addedAt = 1)

        val bibtex = repository().export(id).bibtex
        assertTrue(bibtex.contains("{adams2020deep,"))
        assertTrue(bibtex.contains("{brown2020deep,"))
        assertFalse(bibtex.contains("clark"))
    }

    @Test
    fun emptyCollectionExportsAnEmptyCompleteFile() = runTest {
        val id = checkNotNull(db.collectionDao().insertCollection("A", "a", createdAt = 1))
        assertEquals(CitationResult("", complete = true), repository().export(id))
    }

    @Test
    fun aPaperRemovedDuringTheExportIsLeftOut() = runTest {
        saveUnfetched(paper("W1", "Adams"))
        saveUnfetched(paper("W2", "Brown"))
        val removing = source { id -> if (id == "W2") library.remove("W2") }

        val result = repository(removing).export(null)

        assertTrue(result.complete)
        assertTrue(result.bibtex.contains("{adams2020deep,"))
        assertFalse(result.bibtex.contains("brown"))
    }

    @Test
    fun aPaperRemovedDuringACopyHasNoEntry() = runTest {
        saveUnfetched(paper("W1", "Adams"))
        val removing = source { id -> library.remove(id) }

        assertNull(repository(removing).entry("W1"))
    }

    @Test
    fun refetchesAtMostFourAtATime() = runTest {
        repeat(9) { saveUnfetched(paper("W$it", "S$it")) }
        var running = 0
        var peak = 0
        var requests = 0
        val slow = object : OpenAlexLookupDataSource {
            override suspend fun getWork(id: String): NetworkWork? {
                requests++
                running++
                peak = maxOf(peak, running)
                delay(10)
                running--
                return journalWork(id)
            }

            override suspend fun findWorks(filter: String, perPage: Int): NetworkWorksResponse = error("unused")
        }

        assertTrue(repository(slow).export(null).complete)
        assertEquals(4, peak)
        assertEquals(9, requests)
    }
}
