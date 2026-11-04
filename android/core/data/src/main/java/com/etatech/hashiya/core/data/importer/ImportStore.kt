package com.etatech.hashiya.core.data.importer

import com.etatech.hashiya.core.model.ImportJob
import com.etatech.hashiya.core.model.ImportState
import com.etatech.hashiya.core.model.Paper
import kotlinx.coroutines.flow.Flow

/** A saved paper's identity, for planning an import against the library. */
internal data class LibraryKey(
    val openAlexId: String?,
    val doi: String?,
    val title: String,
    val year: Int?,
    val citeKey: String?,
    /** True when the import being asked about saved it. */
    val importedByJob: Boolean
)

/** A paper an import saves, with the cite key it keeps from the file (null: the next export assigns one). */
internal data class PaperToSave(val paper: Paper, val citeKey: String?)

/** Where import jobs and imported papers are stored. [RoomImportStore] in the app; a fake in tests. */
internal interface ImportStore {
    /** The newest job, or null. */
    fun observeLatest(): Flow<ImportJob?>

    suspend fun job(id: Long): ImportJob?

    /** The newest Analyzing or Importing job, or null. */
    suspend fun running(): ImportJob?

    /** Stores [job] (its id is ignored) and returns the new id. */
    suspend fun create(job: ImportJob, createdAt: Long): Long

    /** Replaces the stored job with [job] only while it is still in [expected]; false when it moved on (cancelled meanwhile). */
    suspend fun replaceIf(expected: ImportState, job: ImportJob): Boolean

    suspend fun delete(id: Long)

    suspend fun libraryKeys(jobId: Long): List<LibraryKey>

    /** A new collection named [name], or null when the name is taken. */
    suspend fun createCollection(name: String, createdAt: Long): Long?

    /**
     * Saves [papers] in one transaction with their job links, links every paper the job saved to [collectionId] when it is set,
     * and stores [processed] and the job's imported count. Returns how many were saved (a paper saved meanwhile is skipped).
     */
    suspend fun saveBatch(jobId: Long, papers: List<PaperToSave>, collectionId: Long?, savedAt: Long, processed: Int): Int
}
