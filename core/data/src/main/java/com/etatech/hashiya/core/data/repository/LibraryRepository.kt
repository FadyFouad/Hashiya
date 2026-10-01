package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.PaperPdf
import com.etatech.hashiya.core.model.ReadingStatus
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    /**
     * Papers matching [query] (blank = all), [status] (null = all) and [collectionId] (null = all papers), newest saved first. Notes are
     * searched too.
     */
    fun observeLibrary(query: String, status: ReadingStatus?, collectionId: Long? = null): Flow<List<LibraryPaper>>

    /** How many papers of each status match [query] within [collectionId] (null = all papers); statuses with none are 0. */
    fun observeStatusCounts(query: String, collectionId: Long? = null): Flow<Map<ReadingStatus, Int>>

    fun observeSavedIds(): Flow<Set<String>>

    /** The saved paper with its status; null when it isn't saved (or stops being saved). */
    fun observePaper(openAlexId: String): Flow<LibraryPaper?>

    /** The paper's notes; empty PaperNotes when it has none or isn't saved. */
    fun observeNotes(openAlexId: String): Flow<PaperNotes>

    /** New papers start as To read, with their publication details marked fetched. Saving a paper that is already saved does nothing. */
    suspend fun save(paper: Paper)

    /** Changes the status without reordering the library. Does nothing if the paper isn't saved. */
    suspend fun setStatus(openAlexId: String, status: ReadingStatus)

    /** Saves the notes (blank notes delete them) and updates the search index. Does nothing if the paper isn't saved. */
    suspend fun saveNotes(openAlexId: String, notes: PaperNotes)

    /** Returns what was removed, including its collections and cite key, for Undo; null if the paper was not saved. */
    suspend fun remove(openAlexId: String): RemovedPaper?

    /**
     * Puts a removed paper back where it was, with its status, notes, cite key and collections. Collections deleted meanwhile are
     * skipped, and a cite key another paper took meanwhile is dropped. Does nothing if it has been saved again meanwhile.
     */
    suspend fun restore(removed: RemovedPaper)
}

data class RemovedPaper(
    val paper: Paper,
    val localId: String,
    val savedAt: Long,
    val status: ReadingStatus,
    val notes: PaperNotes = PaperNotes(),
    val collectionIds: Set<Long> = emptySet(),
    val citeKey: String? = null,
    val detailsFetched: Boolean = true,
    /** When the paper was added to each of [collectionIds]; restored as-is. */
    val collectionLinksAddedAt: Map<Long, Long> = emptyMap(),
    /** The paper's stored PDF; its file stays on disk until the removal is final. */
    val pdf: PaperPdf? = null
)
