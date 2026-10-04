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
}

data class BackupSummary(val papers: Int, val collections: Int, val pdfCount: Int, val pdfBytes: Long)

class ExportedFile internal constructor(internal val file: File, val fileName: String, val missingPdfs: Int)

enum class BackupFailure { NoSpace, WriteFailed, Unreadable }

class BackupException(val failure: BackupFailure, cause: Throwable? = null) : Exception(failure.name, cause)
