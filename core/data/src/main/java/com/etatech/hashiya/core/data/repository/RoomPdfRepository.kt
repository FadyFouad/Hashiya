package com.etatech.hashiya.core.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.etatech.hashiya.core.data.di.ApplicationScope
import com.etatech.hashiya.core.data.pdf.MAX_PDF_BYTES
import com.etatech.hashiya.core.data.pdf.PdfFileStore
import com.etatech.hashiya.core.data.pdf.PdfWriteException
import com.etatech.hashiya.core.data.pdf.StoreResult
import com.etatech.hashiya.core.database.dao.PaperDao
import com.etatech.hashiya.core.database.model.asPaperPdf
import com.etatech.hashiya.core.database.model.asPdfStorage
import com.etatech.hashiya.core.database.model.storedValue
import com.etatech.hashiya.core.model.PaperPdf
import com.etatech.hashiya.core.model.PdfSource
import com.etatech.hashiya.core.model.PdfStorage
import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.PdfDownloadDataSource
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
internal class RoomPdfRepository(
    private val paperDao: PaperDao,
    private val fileStore: PdfFileStore,
    private val downloader: PdfDownloadDataSource,
    private val contentResolver: ContentResolver,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val maxBytes: Long = MAX_PDF_BYTES,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : PdfRepository {
    @Inject
    constructor(
        paperDao: PaperDao,
        fileStore: PdfFileStore,
        downloader: PdfDownloadDataSource,
        contentResolver: ContentResolver,
        @ApplicationScope scope: CoroutineScope
    ) : this(paperDao, fileStore, downloader, contentResolver, scope, System::currentTimeMillis)

    /** Running and failed downloads by OpenAlex id. A finished or cancelled download has no entry. */
    private val downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())

    private val lock = Any()

    /** Guarded by [lock]. */
    private val jobs = mutableMapOf<String, Job>()

    /** Held by [sweepOrphans], which waits for [activeStores] to reach 0; stores take it briefly to count themselves in. */
    private val sweepLock = Mutex()
    private val activeStores = MutableStateFlow(0)

    /** Runs [block], which writes a PDF and records it, never while the sweep runs, so the sweep can't delete its file. */
    private suspend fun <T> storing(block: suspend () -> T): T {
        sweepLock.withLock { activeStores.update { it + 1 } }
        try {
            return block()
        } finally {
            activeStores.update { it - 1 }
        }
    }

    override fun observePdf(openAlexId: String): Flow<PaperPdf?> = paperDao.observePdf(openAlexId).map { it?.asPaperPdf() }

    override fun observeDownload(openAlexId: String): Flow<DownloadState?> = downloads.map { it[openAlexId] }.distinctUntilChanged()

    override fun download(openAlexId: String) {
        synchronized(lock) {
            val current = jobs[openAlexId]
            // A job that already reported a failure may still be winding down; Try again must replace it, not be ignored.
            if (current?.isActive == true && downloads.value[openAlexId] !is DownloadState.Failed) return
            current?.cancel()
            val job = scope.launch(start = CoroutineStart.LAZY) { runDownload(openAlexId) }
            jobs[openAlexId] = job
            // Removing the paper while its PDF downloads cancels the download, so no file outlives the paper.
            val watcher = scope.launch {
                paperDao.observeByOpenAlexId(openAlexId).first { it == null }
                job.cancel()
            }
            job.invokeOnCompletion {
                watcher.cancel()
                synchronized(lock) { if (jobs[openAlexId] === job) jobs.remove(openAlexId) }
            }
            setState(openAlexId, DownloadState.Running(bytes = 0, totalBytes = null))
            job.start()
        }
    }

    override fun cancelDownload(openAlexId: String) {
        synchronized(lock) { jobs[openAlexId] }?.cancel()
    }

    private suspend fun runDownload(openAlexId: String) {
        val self = currentCoroutineContext()[Job]

        // A cancelled download still winding down must not touch the state of one started after it.
        fun report(state: DownloadState?) {
            synchronized(lock) { if (jobs[openAlexId] === self) setState(openAlexId, state) }
        }

        try {
            val row = paperDao.getByOpenAlexId(openAlexId)?.paper ?: return report(null)
            val url = row.oaPdfUrl?.takeIf { it.isNotBlank() }?.let(::upgradeToHttps)
                ?: return report(DownloadState.Failed(DownloadFailure.NoLink))
            storing {
                val result = try {
                    downloader.download(url) { body, length ->
                        report(DownloadState.Running(bytes = 0, totalBytes = length))
                        fileStore.store(row.id, body, maxBytes) { bytes -> report(DownloadState.Running(bytes, length)) }
                    }
                } catch (e: NetworkException) {
                    val reason = if (e.failure == NetworkFailure.Connectivity) DownloadFailure.Offline else DownloadFailure.Http
                    return@storing report(DownloadState.Failed(reason))
                } catch (e: PdfWriteException) {
                    return@storing report(DownloadState.Failed(DownloadFailure.Http))
                }
                when (result) {
                    is StoreResult.Stored -> {
                        // Removed before the row was set: nothing points at the file. A removal after this point keeps
                        // the file for Undo; discardRemoved deletes it once the removal is final.
                        if (paperDao.setPdf(row.id, PdfSource.Downloaded.storedValue, result.size, now()) == 0) {
                            withContext(io) { fileStore.delete(row.id) }
                        }
                        report(null)
                    }

                    StoreResult.NotPdf -> report(DownloadState.Failed(DownloadFailure.NotPdf))

                    StoreResult.TooLarge -> report(DownloadState.Failed(DownloadFailure.TooLarge))
                }
            }
        } catch (e: CancellationException) {
            report(null)
            throw e
        }
    }

    private fun setState(openAlexId: String, state: DownloadState?) {
        downloads.update { if (state == null) it - openAlexId else it + (openAlexId to state) }
    }

    private suspend fun stopDownload(openAlexId: String) {
        synchronized(lock) { jobs[openAlexId] }?.cancelAndJoin()
    }

    override suspend fun attach(openAlexId: String, uri: Uri): AttachResult {
        stopDownload(openAlexId)
        val paperId = paperDao.paperIdFor(openAlexId) ?: return AttachResult.Unreadable
        return storing {
            val result = withContext(io) {
                try {
                    contentResolver.openInputStream(uri)?.use { input -> fileStore.store(paperId, input, maxBytes) {} }
                } catch (e: IOException) {
                    null
                } catch (e: SecurityException) {
                    null
                } catch (e: PdfWriteException) {
                    null
                }
            } ?: return@storing AttachResult.Unreadable
            when (result) {
                is StoreResult.Stored -> {
                    // Removed before the row was set: drop the copy instead of leaving it for the startup sweep.
                    if (paperDao.setPdf(paperId, PdfSource.Attached.storedValue, result.size, now()) == 0) {
                        withContext(io) { fileStore.delete(paperId) }
                        return@storing AttachResult.Unreadable
                    }
                    setState(openAlexId, null)
                    AttachResult.Done
                }

                StoreResult.NotPdf -> AttachResult.NotPdf

                StoreResult.TooLarge -> AttachResult.TooLarge
            }
        }
    }

    override suspend fun remove(openAlexId: String) {
        stopDownload(openAlexId)
        val paperId = paperDao.paperIdFor(openAlexId) ?: return
        paperDao.clearPdf(paperId)
        withContext(io) { fileStore.delete(paperId) }
    }

    override suspend fun setLastPage(openAlexId: String, page: Int) {
        val paperId = paperDao.paperIdFor(openAlexId) ?: return
        paperDao.setPdfLastPage(paperId, page)
    }

    override suspend fun pdfFile(openAlexId: String): File? {
        val row = paperDao.getByOpenAlexId(openAlexId)?.paper ?: return null
        if (row.pdfSource == null) return null
        return withContext(io) { fileStore.file(row.id).takeIf { it.exists() } }
    }

    override suspend fun storage(): PdfStorage = paperDao.pdfStorage().asPdfStorage()

    override suspend fun deleteDownloaded() {
        paperDao.downloadedPdfPaperIds().forEach { paperId ->
            paperDao.clearPdf(paperId)
            withContext(io) { fileStore.delete(paperId) }
        }
    }

    override suspend fun discardRemoved(removed: RemovedPaper) {
        if (removed.pdf == null) return
        // Undo put the paper back with its PDF under the same local id: the file is still in use.
        if (removed.localId in paperDao.pdfPaperIds()) return
        withContext(io) { fileStore.delete(removed.localId) }
    }

    override suspend fun sweepOrphans() {
        // A download or attach running alongside would lose its temporary file, or its new file before its row is set.
        sweepLock.withLock {
            activeStores.first { it == 0 }
            val keep = paperDao.pdfPaperIds().toSet()
            withContext(io) { fileStore.sweep(keep) }
        }
    }
}

/**
 * OpenAlex often gives `http://` links. Android blocks cleartext traffic, and on some networks plain HTTP is intercepted by the
 * provider's redirect page, so the PDF is always fetched over HTTPS. The hosts that serve PDFs (arXiv, journals) all offer it.
 */
internal fun upgradeToHttps(url: String): String =
    if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring("http://".length) else url
