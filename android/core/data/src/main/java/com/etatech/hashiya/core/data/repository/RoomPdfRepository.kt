package com.etatech.hashiya.core.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.etatech.hashiya.core.analytics.Analytics
import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.analytics.NoOpAnalytics
import com.etatech.hashiya.core.crash.CrashReporter
import com.etatech.hashiya.core.crash.CrashSite
import com.etatech.hashiya.core.crash.NoOpCrashReporter
import com.etatech.hashiya.core.data.di.ApplicationScope
import com.etatech.hashiya.core.data.pdf.MAX_PDF_BYTES
import com.etatech.hashiya.core.data.pdf.PdfFileStore
import com.etatech.hashiya.core.data.pdf.PdfStoreGate
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
import com.etatech.hashiya.core.network.OpenAlexPdfLinksDataSource
import com.etatech.hashiya.core.network.PdfDownloadDataSource
import com.etatech.hashiya.core.network.model.NetworkLocation
import java.io.File
import java.io.IOException
import java.net.URI
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
import kotlinx.coroutines.withContext

@Singleton
internal class RoomPdfRepository(
    private val paperDao: PaperDao,
    private val fileStore: PdfFileStore,
    private val downloader: PdfDownloadDataSource,
    private val pdfLinks: OpenAlexPdfLinksDataSource,
    private val contentResolver: ContentResolver,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val maxBytes: Long = MAX_PDF_BYTES,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val gate: PdfStoreGate = PdfStoreGate(),
    private val crashReporter: CrashReporter = NoOpCrashReporter,
    private val analytics: Analytics = NoOpAnalytics
) : PdfRepository {
    @Inject
    constructor(
        paperDao: PaperDao,
        fileStore: PdfFileStore,
        downloader: PdfDownloadDataSource,
        pdfLinks: OpenAlexPdfLinksDataSource,
        contentResolver: ContentResolver,
        @ApplicationScope scope: CoroutineScope,
        gate: PdfStoreGate,
        crashReporter: CrashReporter,
        analytics: Analytics
    ) : this(
        paperDao, fileStore, downloader, pdfLinks, contentResolver, scope, System::currentTimeMillis,
        gate = gate, crashReporter = crashReporter, analytics = analytics
    )

    /** Running and failed downloads by OpenAlex id. A finished or cancelled download has no entry. */
    private val downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())

    private val lock = Any()

    /** Guarded by [lock]. */
    private val jobs = mutableMapOf<String, Job>()

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
            if (url == null) {
                analytics.log(AnalyticsEvent.PdfDownloaded(succeeded = false))
                return report(DownloadState.Failed(DownloadFailure.NoLink))
            }
            val first = attempt(row.id, url, ::report)
            if (first !is Attempt.Failed) {
                if (first == Attempt.Stored) analytics.log(AnalyticsEvent.PdfDownloaded(succeeded = true))
                return report(null)
            }
            if (first.triesOtherLinks) {
                // The stored link may have gone stale while OpenAlex knows other copies (e.g. arXiv): try those in turn,
                // and keep the first that gives the PDF as the paper's link.
                report(DownloadState.Running(bytes = 0, totalBytes = null))
                for (link in otherLinks(openAlexId, tried = url)) {
                    report(DownloadState.Running(bytes = 0, totalBytes = null))
                    when (val next = attempt(row.id, link, ::report)) {
                        Attempt.Stored -> {
                            paperDao.setOaPdfUrl(row.id, link)
                            analytics.log(AnalyticsEvent.PdfDownloaded(succeeded = true))
                            return report(null)
                        }

                        Attempt.Gone -> return report(null)

                        is Attempt.Failed -> if (next.wroteNothing) break
                    }
                }
            }
            analytics.log(AnalyticsEvent.PdfDownloaded(succeeded = false))
            report(DownloadState.Failed(first.reason))
        } catch (e: CancellationException) {
            report(null)
            throw e
        }
    }

    /** OpenAlex's other open-access PDF links for the paper, best first; none when the lookup fails. */
    private suspend fun otherLinks(openAlexId: String, tried: String): List<String> = try {
        fallbackPdfLinks(pdfLinks.pdfLocations(openAlexId), tried)
    } catch (e: NetworkException) {
        emptyList()
    }

    private sealed interface Attempt {
        /** The PDF is stored and recorded. */
        data object Stored : Attempt

        /** The paper was removed before the file was recorded; nothing is left. */
        data object Gone : Attempt

        /**
         * [triesOtherLinks] when the link itself was the problem (not a PDF, an error status), not the connection, the size or
         * the device. [wroteNothing] when the file couldn't be written, which another link won't change.
         */
        data class Failed(val reason: DownloadFailure, val triesOtherLinks: Boolean, val wroteNothing: Boolean = false) : Attempt
    }

    /** Downloads [url] into the paper's file and records it. Throws only [CancellationException]. */
    private suspend fun attempt(paperId: String, url: String, report: (DownloadState?) -> Unit): Attempt = gate.storing {
        val result = try {
            downloader.download(url) { body, length ->
                report(DownloadState.Running(bytes = 0, totalBytes = length))
                fileStore.store(paperId, body, maxBytes) { bytes -> report(DownloadState.Running(bytes, length)) }
            }
        } catch (e: NetworkException) {
            return@storing if (e.failure == NetworkFailure.Connectivity) {
                Attempt.Failed(DownloadFailure.Offline, triesOtherLinks = false)
            } else {
                Attempt.Failed(DownloadFailure.Http, triesOtherLinks = true)
            }
        } catch (e: PdfWriteException) {
            crashReporter.recordNonFatal(e, CrashSite.PdfStore)
            return@storing Attempt.Failed(DownloadFailure.Http, triesOtherLinks = false, wroteNothing = true)
        }
        when (result) {
            is StoreResult.Stored -> {
                // Removed before the row was set: nothing points at the file. A removal after this point keeps
                // the file for Undo; discardRemoved deletes it once the removal is final.
                if (paperDao.setPdf(paperId, PdfSource.Downloaded.storedValue, result.size, now()) == 0) {
                    withContext(io) { fileStore.delete(paperId) }
                    Attempt.Gone
                } else {
                    Attempt.Stored
                }
            }

            StoreResult.NotPdf -> Attempt.Failed(DownloadFailure.NotPdf, triesOtherLinks = true)

            StoreResult.TooLarge -> Attempt.Failed(DownloadFailure.TooLarge, triesOtherLinks = false)
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
        return gate.storing {
            val result = withContext(io) {
                try {
                    contentResolver.openInputStream(uri)?.use { input -> fileStore.store(paperId, input, maxBytes) {} }
                } catch (e: IOException) {
                    null
                } catch (e: SecurityException) {
                    null
                } catch (e: PdfWriteException) {
                    crashReporter.recordNonFatal(e, CrashSite.PdfStore)
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
        // A download, attach or restore running alongside would lose its temporary file, or its new file before its row is set.
        gate.sweeping {
            val stored = paperDao.pdfPaperIds().toSet()
            // Rows whose file is gone (an OS restore leaves PDFs out) go back to "no PDF", so Details offers to download it again.
            val missing = withContext(io) { stored.filterNot { fileStore.file(it).exists() } }
            missing.forEach { paperDao.clearPdf(it) }
            withContext(io) { fileStore.sweep(stored - missing.toSet()) }
        }
    }
}

/**
 * OpenAlex often gives `http://` links. Android blocks cleartext traffic, and on some networks plain HTTP is intercepted by the
 * provider's redirect page, so the PDF is always fetched over HTTPS. The hosts that serve PDFs (arXiv, journals) all offer it.
 */
internal fun upgradeToHttps(url: String): String =
    if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring("http://".length) else url

/** How many of OpenAlex's other links a download tries after the stored one fails. */
internal const val MAX_FALLBACK_LINKS = 3

/**
 * The open-access PDF links in [locations] to try after [tried] failed: over HTTPS, each once, without [tried], arXiv's first
 * (it serves real PDFs reliably), then in OpenAlex's order, at most [MAX_FALLBACK_LINKS].
 */
internal fun fallbackPdfLinks(locations: List<NetworkLocation>, tried: String): List<String> = locations
    .filter { it.isOa && !it.pdfUrl.isNullOrBlank() }
    .map { upgradeToHttps(it.pdfUrl!!.trim()) to it.isArxiv() }
    .distinctBy { it.first }
    .filter { it.first != upgradeToHttps(tried) }
    .sortedByDescending { it.second }
    .take(MAX_FALLBACK_LINKS)
    .map { it.first }

private fun NetworkLocation.isArxiv(): Boolean {
    val host = pdfUrl?.let { runCatching { URI(it.trim()).host }.getOrNull() }?.lowercase()
    val arxivHost = host == "arxiv.org" || host?.endsWith(".arxiv.org") == true
    return arxivHost || source?.displayName?.contains("arXiv", ignoreCase = true) == true
}
