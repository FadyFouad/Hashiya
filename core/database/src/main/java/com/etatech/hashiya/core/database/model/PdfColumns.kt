package com.etatech.hashiya.core.database.model

import com.etatech.hashiya.core.model.PaperPdf
import com.etatech.hashiya.core.model.PdfSource
import com.etatech.hashiya.core.model.PdfStorage

/** Stored values of `papers.pdf_source`. */
const val PDF_SOURCE_DOWNLOADED = "downloaded"
const val PDF_SOURCE_ATTACHED = "attached"

/** A saved paper's PDF columns, read only when `pdf_source` is set. */
data class PdfColumns(val source: String, val size: Long, val addedAt: Long, val lastPage: Int)

/** Space used by stored PDFs, as [com.etatech.hashiya.core.database.dao.PaperDao.pdfStorage] sums it. */
data class PdfStorageRow(val downloadedBytes: Long, val downloadedCount: Int, val attachedBytes: Long, val attachedCount: Int)

/** Unknown stored sources read as attached, so they are never deleted by "Delete downloaded PDFs". */
fun pdfSourceOf(stored: String): PdfSource = if (stored == PDF_SOURCE_DOWNLOADED) PdfSource.Downloaded else PdfSource.Attached

val PdfSource.storedValue: String
    get() = when (this) {
        PdfSource.Downloaded -> PDF_SOURCE_DOWNLOADED
        PdfSource.Attached -> PDF_SOURCE_ATTACHED
    }

fun PdfColumns.asPaperPdf(): PaperPdf = PaperPdf(pdfSourceOf(source), sizeBytes = size, addedAt = addedAt, lastPage = lastPage)

fun PdfStorageRow.asPdfStorage(): PdfStorage = PdfStorage(downloadedBytes, downloadedCount, attachedBytes, attachedCount)

/** The entity's PDF, or null when none is stored. */
fun PaperEntity.pdf(): PaperPdf? {
    val source = pdfSource ?: return null
    return PaperPdf(pdfSourceOf(source), sizeBytes = pdfSize ?: 0, addedAt = pdfAddedAt ?: 0, lastPage = pdfLastPage ?: 0)
}

/** A copy with [pdf]'s four columns, or with all four cleared when [pdf] is null. Used to restore a removed paper's PDF. */
fun PaperEntity.withPdf(pdf: PaperPdf?): PaperEntity = copy(
    pdfSource = pdf?.source?.storedValue,
    pdfSize = pdf?.sizeBytes,
    pdfAddedAt = pdf?.addedAt,
    pdfLastPage = pdf?.lastPage
)
