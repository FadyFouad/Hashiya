package com.etatech.hashiya.core.data.repository

import android.net.Uri
import com.etatech.hashiya.core.model.PaperPdf
import com.etatech.hashiya.core.model.PdfStorage
import java.io.File
import kotlinx.coroutines.flow.Flow

/** Each saved paper's one PDF: downloaded from its open-access link or attached from a file, kept in app-private storage. */
interface PdfRepository {
    /** The stored PDF; null when the paper has none or isn't saved. */
    fun observePdf(openAlexId: String): Flow<PaperPdf?>

    /** The running or failed download, so Details shows it again after reopening; null when there is none. Kept in memory only. */
    fun observeDownload(openAlexId: String): Flow<DownloadState?>

    /** Starts downloading the open-access PDF in the app's scope. Does nothing while one is running for this paper. */
    fun download(openAlexId: String)

    /** Stops a running download and forgets it; nothing is stored. */
    fun cancelDownload(openAlexId: String)

    /** Copies the file at [uri] in as the paper's PDF, replacing any current one. Stops a running download first. */
    suspend fun attach(openAlexId: String, uri: Uri): AttachResult

    /** Deletes the PDF and its file. */
    suspend fun remove(openAlexId: String)

    suspend fun setLastPage(openAlexId: String, page: Int)

    /** The stored file, for the reader and Share; null when there is none. */
    suspend fun pdfFile(openAlexId: String): File?

    suspend fun storage(): PdfStorage

    /** Deletes every downloaded PDF; attached ones stay, since they can't be fetched again. */
    suspend fun deleteDownloaded()

    /**
     * Deletes a removed paper's file once its removal is final (the Undo banner went away), unless Undo restored the paper. Does
     * nothing for a paper that had no PDF.
     */
    suspend fun discardRemoved(removed: RemovedPaper)

    /**
     * Deletes files no paper points at and leftover temporary files, and clears the PDF of papers whose file is gone. Called once at
     * startup.
     */
    suspend fun sweepOrphans()
}

sealed interface DownloadState {
    /** [totalBytes] is null until the server answers, and when it doesn't send a length. */
    data class Running(val bytes: Long, val totalBytes: Long?) : DownloadState

    data class Failed(val reason: DownloadFailure) : DownloadState
}

enum class DownloadFailure { Offline, NotPdf, TooLarge, Http, NoLink }

enum class AttachResult { Done, NotPdf, TooLarge, Unreadable }
