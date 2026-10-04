package com.etatech.hashiya.core.testing

import android.net.Uri
import com.etatech.hashiya.core.data.backup.BackupException
import com.etatech.hashiya.core.data.backup.BackupFailure
import com.etatech.hashiya.core.data.backup.BackupSummary
import com.etatech.hashiya.core.data.backup.ExportedFile
import com.etatech.hashiya.core.data.backup.LibraryBackup
import com.etatech.hashiya.core.data.backup.OpenFailure
import com.etatech.hashiya.core.data.backup.OpenResult
import com.etatech.hashiya.core.data.backup.PreparedBackup
import com.etatech.hashiya.core.data.backup.RestoreResult
import com.etatech.hashiya.core.data.backup.exportedFileForTest
import com.etatech.hashiya.core.data.backup.preparedBackupForTest
import java.io.File
import kotlinx.coroutines.CompletableDeferred

/** Records every call; tests set what each operation returns or throws. */
class FakeLibraryBackup : LibraryBackup {
    var summary = BackupSummary(papers = 0, collections = 0, pdfCount = 0, pdfBytes = 0)
    var exportFailure: BackupFailure? = null
    var saveFailure: BackupFailure? = null
    var missingPdfs = 0

    /** When set, [export] suspends until this completes. */
    var exportGate: CompletableDeferred<Unit>? = null
    var openResult: OpenResult = OpenResult.Failed(OpenFailure.NotABackup)
    var applyResult = RestoreResult(papersAdded = 0, notesAdded = 0, collectionsCreated = 0, pdfsAdded = 0, pdfsMissing = 0, papersSkipped = 0)
    var applyFailure: BackupFailure? = null

    val exports = mutableListOf<Boolean>()
    val saved = mutableListOf<Uri>()
    val opened = mutableListOf<Uri>()
    val applied = mutableListOf<PreparedBackup>()
    var discardedExports = 0
        private set
    var discardedBackups = 0
        private set

    fun preparedBackup(): PreparedBackup = preparedBackupForTest(File("backup.hashiya"))

    override suspend fun summary(): BackupSummary = summary

    override suspend fun export(includePdfs: Boolean, onProgress: (Float) -> Unit): ExportedFile {
        exports += includePdfs
        exportGate?.await()
        exportFailure?.let { throw BackupException(it) }
        onProgress(1f)
        return exportedFileForTest(File("export.hashiya"), "Hashiya-library-2026-10-04.hashiya", missingPdfs)
    }

    override suspend fun save(exported: ExportedFile, destination: Uri) {
        saveFailure?.let { throw BackupException(it) }
        saved += destination
    }

    override fun discard(exported: ExportedFile) {
        discardedExports++
    }

    override suspend fun open(source: Uri): OpenResult {
        opened += source
        return openResult
    }

    override suspend fun apply(backup: PreparedBackup, onProgress: (Float) -> Unit): RestoreResult {
        applied += backup
        applyFailure?.let { throw BackupException(it) }
        onProgress(1f)
        return applyResult
    }

    override fun discard(backup: PreparedBackup) {
        discardedBackups++
    }
}
