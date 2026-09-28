package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.model.Paper
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    /** Newest first. */
    fun observeSavedPapers(): Flow<List<Paper>>

    fun observeSavedIds(): Flow<Set<String>>

    /** Saving a paper that is already saved does nothing. */
    suspend fun save(paper: Paper)

    /** Returns what was removed, for Undo, or null if the paper was not saved. */
    suspend fun remove(openAlexId: String): RemovedPaper?

    /** Puts a removed paper back where it was. Does nothing if it has been saved again meanwhile. */
    suspend fun restore(removed: RemovedPaper)
}

data class RemovedPaper(val paper: Paper, val localId: String, val savedAt: Long)
