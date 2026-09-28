package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    /** Papers matching [query] (blank = all) and [status] (null = all), newest saved first. */
    fun observeLibrary(query: String, status: ReadingStatus?): Flow<List<LibraryPaper>>

    /** How many papers of each status match [query]; statuses with none are 0. */
    fun observeStatusCounts(query: String): Flow<Map<ReadingStatus, Int>>

    fun observeSavedIds(): Flow<Set<String>>

    /** New papers start as To read. Saving a paper that is already saved does nothing. */
    suspend fun save(paper: Paper)

    /** Changes the status without reordering the library. Does nothing if the paper isn't saved. */
    suspend fun setStatus(openAlexId: String, status: ReadingStatus)

    /** Returns what was removed, for Undo, or null if the paper was not saved. */
    suspend fun remove(openAlexId: String): RemovedPaper?

    /** Puts a removed paper back where it was, with its status. Does nothing if it has been saved again meanwhile. */
    suspend fun restore(removed: RemovedPaper)
}

data class RemovedPaper(val paper: Paper, val localId: String, val savedAt: Long, val status: ReadingStatus)
