package com.etatech.hashiya.core.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.etatech.hashiya.core.data.pdf.PdfFileStore
import com.etatech.hashiya.core.data.pdf.PdfStoreGate
import com.etatech.hashiya.core.database.dao.BackupDao
import com.etatech.hashiya.core.model.normalizeDoi
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class ArchiveLibraryBackup(
    private val backupDao: BackupDao,
    private val fileStore: PdfFileStore,
    private val gate: PdfStoreGate,
    private val contentResolver: ContentResolver,
    /** A private folder for archives being built or read; cleared of leftovers on first use. */
    private val workDir: File,
    private val appVersion: String,
    private val now: () -> Long,
    private val newId: () -> String,
    private val io: CoroutineDispatcher
) : LibraryBackup {
    override suspend fun summary(): BackupSummary {
        val pdfs = backupDao.pdfTotals()
        return BackupSummary(backupDao.paperCount(), backupDao.collectionCount(), pdfs.count, pdfs.bytes)
    }

    override suspend fun export(includePdfs: Boolean, onProgress: (Float) -> Unit): ExportedFile = gate.storing {
        // Inside the gate so the startup sweep can't delete a PDF mid-copy.
        val snapshot = backupDao.snapshot()
        withContext(io) {
            val time = now()
            val file = File(workDir.apply { mkdirs() }, "export-${newId()}.hashiya")
            var done = false
            try {
                val written = file.outputStream().use { out ->
                    writeArchive(
                        snapshot = snapshot,
                        includePdfs = includePdfs,
                        pdfFile = fileStore::file,
                        manifest = { papers, collections ->
                            BackupManifest(BACKUP_FORMAT, appVersion, isoUtc(time), papers, collections, includePdfs)
                        },
                        out = out,
                        onProgress = onProgress,
                        checkCancelled = { ensureActive() }
                    )
                }
                done = true
                ExportedFile(file, backupFileName(time), written.missingPdfs)
            } catch (e: IOException) {
                throw BackupException(if (workDir.usableSpace < MIN_FREE_BYTES) BackupFailure.NoSpace else BackupFailure.WriteFailed, e)
            } finally {
                // Cancel and any other failure end here too; nobody holds an ExportedFile to discard.
                if (!done) file.delete()
            }
        }
    }

    override suspend fun save(exported: ExportedFile, destination: Uri) {
        withContext(io) {
            try {
                val out = contentResolver.openOutputStream(destination, "wt") ?: throw IOException("No output stream")
                out.use { exported.file.inputStream().use { input -> input.copyTo(it) } }
            } catch (e: Exception) {
                if (e !is IOException && e !is SecurityException) throw e
                // A half-written file at the destination is worse than none.
                runCatching { DocumentsContract.deleteDocument(contentResolver, destination) }
                throw BackupException(BackupFailure.WriteFailed, e)
            }
        }
    }

    override fun discard(exported: ExportedFile) {
        exported.file.delete()
    }

    override suspend fun open(source: Uri): OpenResult {
        val file = File(workDir.apply { mkdirs() }, "restore-${newId()}.hashiya")
        val read = withContext(io) {
            try {
                val input = contentResolver.openInputStream(source) ?: throw IOException("No input stream")
                input.use { from -> file.outputStream().use { from.copyTo(it) } }
                readArchive(file)
            } catch (e: Exception) {
                if (e !is IOException && e !is SecurityException) {
                    file.delete()
                    throw e
                }
                ArchiveRead.Invalid(OpenFailure.Unreadable)
            }
        }
        if (read !is ArchiveRead.Valid) {
            file.delete()
            return OpenResult.Failed((read as ArchiveRead.Invalid).reason)
        }
        val papers = read.library.papers
        val existing = papers.count { backupDao.matchFor(it.openAlexId?.trim()?.ifEmpty { null }, it.doi?.let(::normalizeDoi)) != null }
        val preview = RestorePreview(
            exportedAt = parseIsoUtc(read.manifest.exportedAt),
            papers = papers.size,
            collections = read.library.collections.size,
            pdfs = papers.count { it.pdf?.file != null },
            newPapers = papers.size - existing,
            existingPapers = existing
        )
        return OpenResult.Ready(PreparedBackup(file, read.library), preview)
    }

    override fun discard(backup: PreparedBackup) {
        backup.file.delete()
    }

    private companion object {
        /** Below this, a failed write is reported as "out of space". */
        const val MIN_FREE_BYTES = 10L * 1024 * 1024
    }
}
