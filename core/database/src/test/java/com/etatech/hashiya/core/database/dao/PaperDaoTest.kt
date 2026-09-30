package com.etatech.hashiya.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.StatusCount
import com.etatech.hashiya.core.database.model.asPaperNotes
import com.etatech.hashiya.core.database.model.searchEntityFor
import com.etatech.hashiya.core.model.PaperNotes
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
class PaperDaoTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var dao: PaperDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.paperDao()
    }

    @After
    fun tearDown() = db.close()

    private fun paper(
        id: String,
        openAlexId: String,
        savedAt: Long,
        doi: String? = null,
        title: String = "Title $id",
        abstract: String? = null,
        venue: String? = "Venue",
        status: String = "to_read"
    ) = PaperEntity(
        id = id,
        openAlexId = openAlexId,
        doi = doi,
        title = title,
        year = 2020,
        venue = venue,
        abstract = abstract,
        citationCount = 1,
        isOpenAccess = false,
        oaPdfUrl = null,
        savedAt = savedAt,
        readingStatus = status
    )

    private fun authors(paperId: String, vararg names: String) =
        names.mapIndexed { index, name -> PaperAuthorEntity(paperId, index, name, null) }

    /** Saves [paper] with [authorNames] and its search row, as the repository does. */
    private suspend fun save(paper: PaperEntity, vararg authorNames: String): Boolean = dao.insertPaperWithAuthors(
        paper,
        authors(paper.id, *authorNames),
        searchEntityFor(paper.id, paper.title, authorNames.toList(), paper.abstract, paper.venue)
    )

    private fun count(table: String): Int = db.query("SELECT COUNT(*) FROM $table", null).use {
        it.moveToFirst()
        it.getInt(0)
    }

    private suspend fun ids(match: String? = null, status: String? = null) = dao.observeLibrary(match, status).first().map { it.paper.id }

    private fun searchNotes(paperId: String): String = db.query("SELECT notes FROM paper_search WHERE paper_id = ?", arrayOf(paperId)).use {
        it.moveToFirst()
        it.getString(0)
    }

    @Test
    fun savedPapersAreNewestFirst() = runTest {
        save(paper("a", "W1", savedAt = 100), "Ada")
        save(paper("b", "W2", savedAt = 200), "Bo")

        assertEquals(listOf("b", "a"), ids())
    }

    @Test
    fun keepsAuthorPositions() = runTest {
        save(paper("a", "W1", 100), "First", "Second", "Third")

        val saved = dao.observeLibrary(null, null).first().single()
        assertEquals(listOf("First", "Second", "Third"), saved.authors.sortedBy { it.position }.map { it.name })
    }

    @Test
    fun savingAgainIsANoOp() = runTest {
        assertTrue(save(paper("a", "W1", 100), "Ada"))
        assertFalse(save(paper("other-id", "W1", 999), "Ada"))

        val saved = dao.observeLibrary(null, null).first()
        assertEquals(1, saved.size)
        assertEquals(100L, saved.single().paper.savedAt)
        assertEquals(1, count("paper_authors"))
        assertEquals(1, count("paper_search"))
    }

    @Test
    fun worksSharingADoiCanBothBeSaved() = runTest {
        assertTrue(save(paper("a", "W1", 100, doi = "10.1000/xyz")))
        assertTrue(save(paper("b", "W2", 200, doi = "10.1000/xyz")))

        assertEquals(setOf("W1", "W2"), dao.observeSavedOpenAlexIds().first().toSet())
    }

    @Test
    fun deletingReturnsRowAndCascadesToAuthorsAndSearchRow() = runTest {
        save(paper("a", "W1", 100), "Ada", "Bo")

        val removed = dao.deleteByOpenAlexId("W1")

        assertEquals("a", removed?.paper?.paper?.id)
        assertEquals(2, removed?.paper?.authors?.size)
        assertNull(removed?.notes)
        assertTrue(dao.observeLibrary(null, null).first().isEmpty())
        assertEquals(0, count("paper_authors"))
        assertEquals(0, count("paper_search"))
    }

    @Test
    fun restoringDeletedRowKeepsIdSavedAtAndStatus() = runTest {
        save(paper("a", "W1", 100, status = "reading"), "Ada")
        save(paper("b", "W2", 200), "Bo")
        val removed = dao.deleteByOpenAlexId("W1")!!

        save(removed.paper.paper, "Ada")

        val saved = dao.observeLibrary(null, null).first()
        assertEquals(listOf("b", "a"), saved.map { it.paper.id })
        assertEquals(100L, saved.last().paper.savedAt)
        assertEquals("reading", saved.last().paper.readingStatus)
        assertEquals(listOf("a"), ids(match = "\"ada*\""))
    }

    @Test
    fun deletingUnknownPaperReturnsNull() = runTest {
        assertNull(dao.deleteByOpenAlexId("missing"))
    }

    @Test
    fun observesSavedOpenAlexIds() = runTest {
        save(paper("a", "W1", 100))
        save(paper("b", "W2", 200))

        assertEquals(setOf("W1", "W2"), dao.observeSavedOpenAlexIds().first().toSet())
    }

    @Test
    fun searchesTitleAuthorsAbstractAndVenue() = runTest {
        save(
            paper("a", "W1", 100, title = "Attention Is All You Need", abstract = "Sequence transduction", venue = "NeurIPS"),
            "Ashish Vaswani"
        )
        save(paper("b", "W2", 200, title = "Deep Residual Learning", abstract = "Image recognition", venue = "CVPR"), "Kaiming He")

        assertEquals(listOf("a"), ids(match = "\"attention*\""))
        assertEquals(listOf("a"), ids(match = "\"vaswani*\""))
        assertEquals(listOf("b"), ids(match = "\"recognition*\""))
        assertEquals(listOf("b"), ids(match = "\"cvpr*\""))
        assertEquals(emptyList<String>(), ids(match = "\"transformer*\""))
    }

    @Test
    fun prefixTermsMatchLongerWordsAndEveryWordMustMatch() = runTest {
        save(paper("a", "W1", 100, title = "Transformers for language"))
        save(paper("b", "W2", 200, title = "Transformers for images"))

        assertEquals(listOf("b", "a"), ids(match = "\"transf*\""))
        assertEquals(listOf("a"), ids(match = "\"transf*\" \"lang*\""))
    }

    /** The paper's local id is stored in the index but not indexed, so it never matches a search. */
    @Test
    fun paperIdIsNotSearchable() = runTest {
        save(paper("zzlocalid", "W1", 100, title = "Deep learning"))

        assertEquals(emptyList<String>(), ids(match = "\"zzlocalid*\""))
    }

    @Test
    fun searchCombinesWithStatus() = runTest {
        save(paper("a", "W1", 100, title = "Transformers one", status = "reading"))
        save(paper("b", "W2", 200, title = "Transformers two"))
        save(paper("c", "W3", 300, title = "Convolutions", status = "reading"))

        assertEquals(listOf("c", "a"), ids(status = "reading"))
        assertEquals(listOf("a"), ids(match = "\"transf*\"", status = "reading"))
    }

    @Test
    fun countsPerStatusFollowTheSearch() = runTest {
        save(paper("a", "W1", 100, title = "Transformers one", status = "reading"))
        save(paper("b", "W2", 200, title = "Transformers two"))
        save(paper("c", "W3", 300, title = "Convolutions", status = "reading"))

        assertEquals(
            setOf(StatusCount("reading", 2), StatusCount("to_read", 1)),
            dao.observeStatusCounts(null).first().toSet()
        )
        assertEquals(
            setOf(StatusCount("reading", 1), StatusCount("to_read", 1)),
            dao.observeStatusCounts("\"transf*\"").first().toSet()
        )
        assertEquals(emptyList<StatusCount>(), dao.observeStatusCounts("\"missing*\"").first())
    }

    @Test
    fun settingStatusKeepsOrderAndIndex() = runTest {
        save(paper("a", "W1", 100, title = "Transformers one"))
        save(paper("b", "W2", 200, title = "Transformers two"))

        assertEquals(1, dao.setStatus("W1", "read"))

        assertEquals(listOf("b", "a"), ids())
        assertEquals("read", dao.getByOpenAlexId("W1")?.paper?.readingStatus)
        assertEquals(listOf("b", "a"), ids(match = "\"transf*\""))
        assertEquals(2, count("paper_search"))
    }

    @Test
    fun settingStatusOfUnknownPaperChangesNothing() = runTest {
        assertEquals(0, dao.setStatus("missing", "read"))
    }

    @Test
    fun savingNotesStoresThemAndIndexesThem() = runTest {
        save(paper("a", "W1", 100))

        assertTrue(dao.saveNotes("W1", PaperNotes(summary = "Self-attention only", method = "Ablation"), updatedAt = 5))

        val stored = dao.observeNotes("W1").first()
        assertEquals(PaperNotes(summary = "Self-attention only", method = "Ablation"), stored?.asPaperNotes())
        assertEquals(5L, stored?.updatedAt)
        assertEquals(listOf("a"), ids(match = "\"ablation*\""))
    }

    @Test
    fun savingNotesAgainReplacesThemAndTheirIndex() = runTest {
        save(paper("a", "W1", 100))
        dao.saveNotes("W1", PaperNotes(method = "Ablation"), updatedAt = 5)

        dao.saveNotes("W1", PaperNotes(method = "Survey"), updatedAt = 6)

        assertEquals("Survey", dao.observeNotes("W1").first()?.method)
        assertEquals(1, count("paper_notes"))
        assertEquals(emptyList<String>(), ids(match = "\"ablation*\""))
        assertEquals(listOf("a"), ids(match = "\"survey*\""))
    }

    @Test
    fun savingBlankNotesDeletesTheRowAndClearsTheIndex() = runTest {
        save(paper("a", "W1", 100))
        dao.saveNotes("W1", PaperNotes(method = "Ablation"), updatedAt = 5)

        assertTrue(dao.saveNotes("W1", PaperNotes(method = "  "), updatedAt = 6))

        assertNull(dao.observeNotes("W1").first())
        assertEquals(0, count("paper_notes"))
        assertEquals("", searchNotes("a"))
        assertEquals(emptyList<String>(), ids(match = "\"ablation*\""))
        // The rest of the search row is untouched: the title ("Title a") still finds it.
        assertEquals(listOf("a"), ids(match = "\"title*\""))
    }

    @Test
    fun savingNotesForAnUnsavedPaperWritesNothing() = runTest {
        assertFalse(dao.saveNotes("missing", PaperNotes(summary = "x"), updatedAt = 1))

        assertEquals(0, count("paper_notes"))
    }

    @Test
    fun notesAreSearchedWithTheSameFolding() = runTest {
        save(paper("a", "W1", 100))
        dao.saveNotes("W1", PaperNotes(keyFindings = "التَّعلُّم العميق يتفوّق"), updatedAt = 1)

        assertEquals(listOf("a"), ids(match = "\"التعلم*\""))
    }

    @Test
    fun observingAPaperFollowsItUntilItIsDeleted() = runTest {
        save(paper("a", "W1", 100), "Ada")
        assertEquals("a", dao.observeByOpenAlexId("W1").first()?.paper?.id)

        dao.deleteByOpenAlexId("W1")

        assertNull(dao.observeByOpenAlexId("W1").first())
    }

    @Test
    fun deletingReturnsTheNotesAndLeavesNoNotesRow() = runTest {
        save(paper("a", "W1", 100))
        dao.saveNotes("W1", PaperNotes(thoughts = "Useful for chapter 2"), updatedAt = 7)

        val removed = dao.deleteByOpenAlexId("W1")

        assertEquals(PaperNotes(thoughts = "Useful for chapter 2"), removed?.notes?.asPaperNotes())
        assertEquals(0, count("paper_notes"))
    }

    @Test
    fun restoringWithNotesBringsBackTheRowAndItsIndex() = runTest {
        save(paper("a", "W1", 100), "Ada")
        dao.saveNotes("W1", PaperNotes(thoughts = "Useful for chapter 2"), updatedAt = 7)
        val removed = dao.deleteByOpenAlexId("W1")!!
        val notes = removed.notes!!.asPaperNotes()

        dao.insertPaperWithAuthors(
            removed.paper.paper,
            removed.paper.authors,
            searchEntityFor("a", removed.paper.paper.title, listOf("Ada"), null, "Venue", notes),
            removed.notes
        )

        assertEquals(notes, dao.observeNotes("W1").first()?.asPaperNotes())
        assertEquals(listOf("a"), ids(match = "\"chapter*\""))
    }
}
