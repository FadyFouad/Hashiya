package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.model.searchableText
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeLibraryRepository : LibraryRepository {
    private val rows = MutableStateFlow<List<RemovedPaper>>(emptyList())
    private var clock = 0L

    /** When true, [save] throws like a failing disk would. */
    var failOnSave = false

    /** When true, [remove] throws like a failing disk would. */
    var failOnRemove = false

    /** When true, [setStatus] throws like a failing disk would. */
    var failOnSetStatus = false

    /** When true, [saveNotes] throws like a failing disk would. */
    var failOnSaveNotes = false

    /** Every [saveNotes] call that didn't throw, in order, including those for papers that aren't saved. */
    val notesSaves = mutableListOf<Pair<String, PaperNotes>>()

    override fun observeLibrary(query: String, status: ReadingStatus?): Flow<List<LibraryPaper>> = rows.map { list ->
        list.filter { (status == null || it.status == status) && it.matches(query) }
            .sortedByDescending { it.savedAt }
            .map { LibraryPaper(it.paper, it.status) }
    }

    override fun observeStatusCounts(query: String): Flow<Map<ReadingStatus, Int>> = rows.map { list ->
        val matching = list.filter { it.matches(query) }
        ReadingStatus.entries.associateWith { status -> matching.count { it.status == status } }
    }

    override fun observeSavedIds(): Flow<Set<String>> = rows.map { list -> list.map { it.paper.openAlexId }.toSet() }

    override fun observePaper(openAlexId: String): Flow<LibraryPaper?> = rows.map { list ->
        list.firstOrNull { it.paper.openAlexId == openAlexId }?.let { LibraryPaper(it.paper, it.status) }
    }

    override fun observeNotes(openAlexId: String): Flow<PaperNotes> = rows.map { list ->
        list.firstOrNull { it.paper.openAlexId == openAlexId }?.notes ?: PaperNotes()
    }

    override suspend fun save(paper: Paper) {
        if (failOnSave) throw IOException("disk full")
        if (isSaved(paper.openAlexId)) return
        rows.update { it + RemovedPaper(paper, localId = "local-${paper.openAlexId}", savedAt = ++clock, status = ReadingStatus.ToRead) }
    }

    override suspend fun setStatus(openAlexId: String, status: ReadingStatus) {
        if (failOnSetStatus) throw IOException("disk full")
        rows.update { list -> list.map { if (it.paper.openAlexId == openAlexId) it.copy(status = status) else it } }
    }

    override suspend fun saveNotes(openAlexId: String, notes: PaperNotes) {
        if (failOnSaveNotes) throw IOException("disk full")
        notesSaves += openAlexId to notes
        rows.update { list -> list.map { if (it.paper.openAlexId == openAlexId) it.copy(notes = notes) else it } }
    }

    override suspend fun remove(openAlexId: String): RemovedPaper? {
        if (failOnRemove) throw IOException("disk full")
        val row = rows.value.firstOrNull { it.paper.openAlexId == openAlexId } ?: return null
        rows.update { it - row }
        return row
    }

    override suspend fun restore(removed: RemovedPaper) {
        if (isSaved(removed.paper.openAlexId)) return
        rows.update { it + removed }
    }

    private fun isSaved(openAlexId: String) = rows.value.any { it.paper.openAlexId == openAlexId }
}

private val NOT_LETTER_OR_DIGIT = Regex("""[^\p{L}\p{N}]+""")

private fun words(text: String) = searchableText(text).split(NOT_LETTER_OR_DIGIT).filter { it.isNotEmpty() }

/** Like the real index: every word of [query] must start a word of the title, authors, abstract, venue or notes. */
private fun RemovedPaper.matches(query: String): Boolean {
    val noteText = NoteSection.entries.joinToString(" ") { notes[it] }
    val indexed = words(
        listOf(paper.title, paper.authors.joinToString(" ") { it.name }, paper.abstract.orEmpty(), paper.venue.orEmpty(), noteText)
            .joinToString(" ")
    )
    return words(query).all { word -> indexed.any { it.startsWith(word) } }
}
