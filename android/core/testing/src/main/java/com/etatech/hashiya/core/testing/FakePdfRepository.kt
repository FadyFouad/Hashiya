package com.etatech.hashiya.core.testing

import android.net.Uri
import com.etatech.hashiya.core.data.repository.AttachResult
import com.etatech.hashiya.core.data.repository.DownloadState
import com.etatech.hashiya.core.data.repository.PdfRepository
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.model.PaperPdf
import com.etatech.hashiya.core.model.PdfSource
import com.etatech.hashiya.core.model.PdfStorage
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Records every call; tests set the PDFs and download states the screens should see. */
class FakePdfRepository : PdfRepository {
    private val pdfs = MutableStateFlow<Map<String, PaperPdf>>(emptyMap())
    private val downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())

    private var attachResult = AttachResult.Done
    private var storageResult = PdfStorage(downloadedBytes = 0, downloadedCount = 0, attachedBytes = 0, attachedCount = 0)

    /** openAlexId → the file [pdfFile] returns while that paper has a PDF; unset ids get a placeholder path. */
    var files: Map<String, File> = emptyMap()

    val downloads = mutableListOf<String>()
    val cancels = mutableListOf<String>()
    val attaches = mutableListOf<Pair<String, Uri>>()
    val removals = mutableListOf<String>()
    val lastPages = mutableListOf<Pair<String, Int>>()

    /** OpenAlex ids of the removed papers passed to [discardRemoved], in order. */
    val discarded = mutableListOf<String>()
    var deleteDownloadedCalls = 0
        private set
    var sweeps = 0
        private set

    fun setPdf(openAlexId: String, pdf: PaperPdf?) {
        pdfs.update { if (pdf == null) it - openAlexId else it + (openAlexId to pdf) }
    }

    fun setDownload(openAlexId: String, state: DownloadState?) {
        downloadStates.update { if (state == null) it - openAlexId else it + (openAlexId to state) }
    }

    fun setAttachResult(result: AttachResult) {
        attachResult = result
    }

    fun setStorage(storage: PdfStorage) {
        storageResult = storage
    }

    override fun observePdf(openAlexId: String): Flow<PaperPdf?> = pdfs.map { it[openAlexId] }.distinctUntilChanged()

    override fun observeDownload(openAlexId: String): Flow<DownloadState?> = downloadStates.map { it[openAlexId] }.distinctUntilChanged()

    /** Records the call; tests drive what follows with [setDownload] and [setPdf]. */
    override fun download(openAlexId: String) {
        downloads += openAlexId
    }

    override fun cancelDownload(openAlexId: String) {
        cancels += openAlexId
        setDownload(openAlexId, null)
    }

    /** Records the call and, on [AttachResult.Done], stores a 1 KB attached PDF. */
    override suspend fun attach(openAlexId: String, uri: Uri): AttachResult {
        attaches += openAlexId to uri
        if (attachResult == AttachResult.Done) {
            setDownload(openAlexId, null)
            setPdf(openAlexId, PaperPdf(PdfSource.Attached, sizeBytes = 1_024, addedAt = 0))
        }
        return attachResult
    }

    override suspend fun remove(openAlexId: String) {
        removals += openAlexId
        setPdf(openAlexId, null)
    }

    override suspend fun setLastPage(openAlexId: String, page: Int) {
        lastPages += openAlexId to page
        pdfs.update { current -> current[openAlexId]?.let { current + (openAlexId to it.copy(lastPage = page)) } ?: current }
    }

    /** While a PDF is set: the file from [files], or a placeholder path, so tests that never read it needn't set one. */
    override suspend fun pdfFile(openAlexId: String): File? =
        if (pdfs.value[openAlexId] == null) null else files[openAlexId] ?: File("fake-pdfs/$openAlexId.pdf")

    override suspend fun storage(): PdfStorage = storageResult

    /** Drops downloaded PDFs and zeroes the downloaded numbers in [storage]; attached ones stay. */
    override suspend fun deleteDownloaded() {
        deleteDownloadedCalls++
        pdfs.update { current -> current.filterValues { it.source != PdfSource.Downloaded } }
        storageResult = storageResult.copy(downloadedBytes = 0, downloadedCount = 0)
    }

    override suspend fun discardRemoved(removed: RemovedPaper) {
        discarded += removed.paper.openAlexId
    }

    override suspend fun sweepOrphans() {
        sweeps++
    }
}
