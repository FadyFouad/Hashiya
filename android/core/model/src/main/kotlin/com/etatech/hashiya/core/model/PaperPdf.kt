package com.etatech.hashiya.core.model

/** Where a stored PDF came from. Downloaded ones can be fetched again; attached ones can't. */
enum class PdfSource { Downloaded, Attached }

/** The PDF stored for a saved paper. */
data class PaperPdf(
    val source: PdfSource,
    val sizeBytes: Long,
    /** When it was stored, in epoch milliseconds. */
    val addedAt: Long,
    /** Zero-based page the reader last showed; 0 for a new file. */
    val lastPage: Int = 0
)

/** Space used by stored PDFs, by where they came from. */
data class PdfStorage(val downloadedBytes: Long, val downloadedCount: Int, val attachedBytes: Long, val attachedCount: Int)
