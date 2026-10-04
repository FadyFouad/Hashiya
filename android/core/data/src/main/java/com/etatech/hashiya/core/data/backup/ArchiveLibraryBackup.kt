package com.etatech.hashiya.core.data.backup

import android.content.ContentResolver
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.DocumentsContract
import com.etatech.hashiya.core.data.pdf.MAX_PDF_BYTES
import com.etatech.hashiya.core.data.pdf.PdfFileStore
import com.etatech.hashiya.core.data.pdf.PdfStoreGate
import com.etatech.hashiya.core.data.pdf.PdfWriteException
import com.etatech.hashiya.core.data.pdf.StageResult
import com.etatech.hashiya.core.database.dao.BackupDao
import com.etatech.hashiya.core.database.model.IncomingCollection
import com.etatech.hashiya.core.database.model.IncomingPaper
import com.etatech.hashiya.core.database.model.MergeOutcome
import com.etatech.hashiya.core.model.collectionNameKey
import com.etatech.hashiya.core.model.normalizeDoi
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
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
    private val io: CoroutineDispatcher,
    private val merge: suspend (List<IncomingPaper>, List<IncomingCollection>, Long) -> MergeOutcome = backupDao::merge
) : LibraryBackup {
    private val workDirCleared = AtomicBoolean(false)

    /**
     * [workDir], created. The first call in this process deletes what an earlier process left (a crash, process death
     * or a cancelled job); nothing of this process exists before then.
     */
    private fun readyWorkDir(): File {
        workDir.mkdirs()
        if (workDirCleared.compareAndSet(false, true)) workDir.listFiles()?.forEach { it.deleteRecursively() }
        return workDir
    }

    override suspend fun summary(): BackupSummary {
        val pdfs = backupDao.pdfTotals()
        return BackupSummary(backupDao.paperCount(), backupDao.collectionCount(), pdfs.count, pdfs.bytes)
    }

    override suspend fun export(includePdfs: Boolean, onProgress: (Float) -> Unit): ExportedFile = gate.storing {
        // Inside the gate so the startup sweep can't delete a PDF mid-copy.
        val snapshot = backupDao.snapshot()
        withContext(io) {
            val time = now()
            val file = File(readyWorkDir(), "export-${newId()}.hashiya")
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
        val file = File(readyWorkDir(), "restore-${newId()}.hashiya")
        var ready = false
        try {
            val read = withContext(io) {
                try {
                    val input = contentResolver.openInputStream(source) ?: throw IOException("No input stream")
                    input.use { from -> file.outputStream().use { from.copyTo(it) } }
                    readArchive(file)
                } catch (e: Exception) {
                    if (e !is IOException && e !is SecurityException) throw e
                    ArchiveRead.Invalid(OpenFailure.Unreadable)
                }
            }
            if (read !is ArchiveRead.Valid) return OpenResult.Failed((read as ArchiveRead.Invalid).reason)
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
            return OpenResult.Ready(PreparedBackup(file, read.library), preview).also { ready = true }
        } finally {
            // Invalid, cancelled (the screen was left while reading) or failed: nobody holds a PreparedBackup to discard.
            if (!ready) file.delete()
        }
    }

    override fun discard(backup: PreparedBackup) {
        backup.file.delete()
    }

    override suspend fun apply(backup: PreparedBackup, onProgress: (Float) -> Unit): RestoreResult = gate.storing {
        // Inside the gate: the sweep would delete the staged `.part` files.
        val staged = mutableMapOf<Int, StageResult.Staged>()
        try {
            var pdfsMissing = 0
            withContext(io) {
                val named = backup.library.papers.filter { it.pdf?.file != null }
                try {
                    ZipFile(backup.file).use { zip ->
                        named.forEachIndexed { index, paper ->
                            // Only the paper's own entry name is ever read, so no entry can reach outside the PDF folder.
                            val entry = paper.pdf?.file?.takeIf { it == pdfEntryName(paper.ref) }?.let(zip::getEntry)
                            if (entry == null) {
                                pdfsMissing++
                            } else {
                                if (entry.size > 0 && fileStore.usableSpace() < entry.size + MIN_FREE_BYTES) {
                                    throw BackupException(BackupFailure.NoSpace)
                                }
                                when (val result = zip.getInputStream(entry).use { fileStore.stage("restore", it, MAX_PDF_BYTES) {} }) {
                                    is StageResult.Staged -> staged[paper.ref] = result
                                    StageResult.NotPdf, StageResult.TooLarge -> pdfsMissing++
                                }
                            }
                            onProgress((index + 1).toFloat() / (named.size + 1))
                        }
                    }
                } catch (e: IOException) {
                    throw BackupException(BackupFailure.Unreadable, e)
                } catch (e: PdfWriteException) {
                    throw BackupException(BackupFailure.NoSpace, e)
                }
            }
            val papers = backup.library.papers.map { it.toIncoming(newId(), staged[it.ref]) }
            val collections = backup.library.collections
                .filter { it.name.isNotBlank() }
                .map { IncomingCollection(it.name.trim(), collectionNameKey(it.name), it.createdAt, it.papers) }
            currentCoroutineContext().ensureActive()
            // Once the merge may have committed, the PDF moves must finish whatever happens to the caller.
            val (outcome, pdfsAdded) = withContext(NonCancellable) {
                val outcome = try {
                    merge(papers, collections, now())
                } catch (e: SQLiteException) {
                    throw BackupException(BackupFailure.WriteFailed, e)
                }
                var added = 0
                withContext(io) {
                    staged.forEach { (ref, file) ->
                        val target = outcome.pdfTargets[ref]
                        if (target == null) {
                            file.file.delete()
                        } else {
                            // A failed rename leaves a row without its file; the next startup sweep clears it.
                            runCatching { fileStore.commit(file.file, target) }.onSuccess { added++ }.onFailure { pdfsMissing++ }
                        }
                    }
                }
                outcome to added
            }
            staged.clear()
            onProgress(1f)
            RestoreResult(outcome.added, outcome.notesAdded, outcome.collectionsCreated, pdfsAdded, pdfsMissing)
        } finally {
            staged.values.forEach { it.file.delete() }
        }
    }

    private companion object {
        /** Below this, a failed write is reported as "out of space". */
        const val MIN_FREE_BYTES = 10L * 1024 * 1024
    }
}
