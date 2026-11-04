package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.model.ImportJob
import kotlinx.coroutines.flow.Flow

/** Files over this size are refused before they are read any further (spec §7.6). */
const val MAX_IMPORT_FILE_BYTES = 10 * 1024 * 1024

/** Importing a .bib or .ris file: one job at a time, in the background, with a preview before anything is saved. */
interface ImportRepository {
    /** The current job (the newest one the user hasn't dismissed), or null. */
    fun observeCurrentJob(): Flow<ImportJob?>

    /**
     * Starts a job for the file: Analyzing, or Failed(TooLarge) when [bytes] is over [MAX_IMPORT_FILE_BYTES]. A job that is still
     * analyzing or importing is returned instead, and a finished one is dismissed first.
     */
    suspend fun start(fileName: String, bytes: ByteArray): ImportJob

    /** From Ready: saves the papers in the background, into a new collection when [addToCollection]. */
    suspend fun confirm(jobId: Long, addToCollection: Boolean)

    /** Stops the job. Papers already saved stay. */
    suspend fun cancel(jobId: Long)

    /** Forgets a job that isn't running, and its copy of the file. */
    suspend fun dismiss(jobId: Long)
}
