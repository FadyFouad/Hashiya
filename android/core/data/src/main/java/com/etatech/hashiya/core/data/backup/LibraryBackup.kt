package com.etatech.hashiya.core.data.backup

import android.net.Uri
import java.io.File

/** Exports the library to a `.hashiya` archive and merges one back in. */
interface LibraryBackup {
    suspend fun summary(): BackupSummary

    /** Builds the archive in a temporary file. Throws [BackupException]. */
    suspend fun export(includePdfs: Boolean, onProgress: (Float) -> Unit = {}): ExportedFile

    /** Copies [exported] to [destination] (from the system save dialog). Throws [BackupException]; a failed copy is deleted. */
    suspend fun save(exported: ExportedFile, destination: Uri)

    /** Deletes the temporary file. Safe to call more than once. */
    fun discard(exported: ExportedFile)

    /** Copies [source] into the app and checks it. Nothing is written to the library. */
    suspend fun open(source: Uri): OpenResult

    /** Deletes the copied archive. Safe to call more than once. */
    fun discard(backup: PreparedBackup)

    /**
     * Merges [backup] into the library; the library wins every conflict. All of it lands or none of it does. Throws
     * [BackupException]: [BackupFailure.NoSpace] before anything is written, [BackupFailure.Unreadable] when the archive can't be
     * read, [BackupFailure.WriteFailed] when the database write fails.
     */
    suspend fun apply(backup: PreparedBackup, onProgress: (Float) -> Unit = {}): RestoreResult
}

data class BackupSummary(val papers: Int, val collections: Int, val pdfCount: Int, val pdfBytes: Long)

class ExportedFile internal constructor(internal val file: File, val fileName: String, val missingPdfs: Int)

enum class BackupFailure { NoSpace, WriteFailed, Unreadable }

class BackupException(val failure: BackupFailure, cause: Throwable? = null) : Exception(failure.name, cause)

sealed interface OpenResult {
    data class Ready(val backup: PreparedBackup, val preview: RestorePreview) : OpenResult

    data class Failed(val reason: OpenFailure) : OpenResult
}

enum class OpenFailure { NotABackup, NewerFormat, Damaged, Unreadable }

/**
 * [exportedAt] is null when the manifest's date doesn't parse. [pdfs] counts PDFs in the archive. [papersSkipped] counts papers with no
 * OpenAlex id, which this app can't show yet; they are neither new nor existing.
 */
data class RestorePreview(
    val exportedAt: Long?,
    val papers: Int,
    val collections: Int,
    val pdfs: Int,
    val newPapers: Int,
    val existingPapers: Int,
    val papersSkipped: Int = 0
)

class PreparedBackup internal constructor(internal val file: File, internal val library: BackupLibrary)

data class RestoreResult(
    val papersAdded: Int,
    val notesAdded: Int,
    val collectionsCreated: Int,
    val pdfsAdded: Int,
    /** PDFs the backup names but couldn't give: not in the archive, not a PDF, or too large. */
    val pdfsMissing: Int,
    /** Papers with no OpenAlex id, left out because this app can't show them yet. */
    val papersSkipped: Int = 0
)
