package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.ReadingStatus
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** The fake must behave like the real repository, or feature tests prove nothing. */
class FakeLibraryRepositoryTest {
    private val repository = FakeLibraryRepository()

    private suspend fun titles(query: String = "", status: ReadingStatus? = null) =
        repository.observeLibrary(query, status).first().map { it.paper.title }

    @Test
    fun behavesLikeRoomRepository() = runTest {
        repository.save(SamplePapers.attention)
        repository.save(SamplePapers.bert)
        repository.save(SamplePapers.attention)
        assertEquals(
            listOf(LibraryPaper(SamplePapers.bert, ReadingStatus.ToRead), LibraryPaper(SamplePapers.attention, ReadingStatus.ToRead)),
            repository.observeLibrary("", null).first()
        )

        repository.setStatus(SamplePapers.attention.openAlexId, ReadingStatus.Reading)
        val removed = repository.remove(SamplePapers.attention.openAlexId)!!
        repository.restore(removed)
        assertEquals(
            listOf(LibraryPaper(SamplePapers.bert, ReadingStatus.ToRead), LibraryPaper(SamplePapers.attention, ReadingStatus.Reading)),
            repository.observeLibrary("", null).first()
        )
        assertEquals(
            setOf(SamplePapers.attention.openAlexId, SamplePapers.bert.openAlexId),
            repository.observeSavedIds().first()
        )
    }

    @Test
    fun searchesLikeTheIndex() = runTest {
        SamplePapers.all.forEach { repository.save(it) }
        repository.save(SamplePapers.arabicTitled)

        assertEquals(listOf(SamplePapers.vit.title, SamplePapers.bert.title, SamplePapers.attention.title), titles("transf"))
        assertEquals(listOf(SamplePapers.attention.title), titles("transf vaswani"))
        assertEquals(listOf(SamplePapers.bert.title), titles("naacl"))
        assertEquals(listOf(SamplePapers.arabicTitled.title), titles("التَّعلُّم"))
        assertEquals(emptyList<String>(), titles("vaswani devlin"))
        assertEquals(4, titles("  ").size)
    }

    @Test
    fun filtersAndCountsByStatus() = runTest {
        SamplePapers.all.forEach { repository.save(it) }
        repository.setStatus(SamplePapers.bert.openAlexId, ReadingStatus.Read)

        assertEquals(listOf(SamplePapers.bert.title), titles(status = ReadingStatus.Read))
        assertEquals(
            mapOf(ReadingStatus.ToRead to 2, ReadingStatus.Reading to 0, ReadingStatus.Read to 1),
            repository.observeStatusCounts("").first()
        )
        assertEquals(
            mapOf(ReadingStatus.ToRead to 0, ReadingStatus.Reading to 0, ReadingStatus.Read to 1),
            repository.observeStatusCounts("naacl").first()
        )
    }

    @Test
    fun keepsNotesLikeTheRoomRepository() = runTest {
        repository.save(SamplePapers.bert)
        assertEquals(PaperNotes(), repository.observeNotes(SamplePapers.bert.openAlexId).first())

        repository.saveNotes(SamplePapers.bert.openAlexId, PaperNotes(method = "Masked language model"))
        assertEquals(PaperNotes(method = "Masked language model"), repository.observeNotes(SamplePapers.bert.openAlexId).first())
        assertEquals(listOf(SamplePapers.bert.title), titles("masked"))

        val removed = repository.remove(SamplePapers.bert.openAlexId)!!
        assertEquals(PaperNotes(method = "Masked language model"), removed.notes)
        assertNull(repository.observePaper(SamplePapers.bert.openAlexId).first())
        repository.restore(removed)
        assertEquals(PaperNotes(method = "Masked language model"), repository.observeNotes(SamplePapers.bert.openAlexId).first())
        assertEquals(LibraryPaper(SamplePapers.bert, ReadingStatus.ToRead), repository.observePaper(SamplePapers.bert.openAlexId).first())
    }

    @Test
    fun notesForAnUnsavedPaperAreIgnoredButRecorded() = runTest {
        repository.saveNotes("missing", PaperNotes(summary = "x"))

        assertEquals(PaperNotes(), repository.observeNotes("missing").first())
        assertEquals(listOf("missing" to PaperNotes(summary = "x")), repository.notesSaves)
    }

    @Test
    fun failOnSaveNotesThrowsAndRecordsNothing() {
        repository.failOnSaveNotes = true

        assertThrows(IOException::class.java) { runBlocking { repository.saveNotes("W1", PaperNotes(summary = "x")) } }
        assertEquals(emptyList<Pair<String, PaperNotes>>(), repository.notesSaves)
    }
}
