package com.etatech.hashiya.core.data.backup

import com.etatech.hashiya.core.data.pdf.StageResult
import com.etatech.hashiya.core.database.model.IncomingPaper
import com.etatech.hashiya.core.database.model.PDF_SOURCE_ATTACHED
import com.etatech.hashiya.core.database.model.PDF_SOURCE_DOWNLOADED
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperNotesEntity
import com.etatech.hashiya.core.model.normalizeDoi

private val STORED_STATUSES = setOf("to_read", "reading", "read")

/** The OpenAlex id the paper is stored under; null when it has none, and then the restore skips it. */
internal val BackupPaper.usableOpenAlexId: String? get() = openAlexId?.trim()?.ifEmpty { null }

/**
 * The paper as rows under [localId]. Its PDF columns are set only when [staged] holds its file; notes with no text are dropped,
 * like the app never stores empty notes.
 */
internal fun BackupPaper.toIncoming(localId: String, staged: StageResult.Staged?): IncomingPaper {
    val backupPdf = pdf
    val entity = PaperEntity(
        id = localId,
        openAlexId = usableOpenAlexId,
        doi = doi?.let(::normalizeDoi),
        title = title,
        year = year,
        venue = venue,
        abstract = abstract,
        citationCount = citationCount,
        isOpenAccess = isOpenAccess,
        oaPdfUrl = oaPdfUrl,
        savedAt = savedAt,
        readingStatus = readingStatus.takeIf { it in STORED_STATUSES } ?: "to_read",
        workType = workType,
        sourceType = sourceType,
        publisher = publisher,
        volume = volume,
        issue = issue,
        firstPage = firstPage,
        lastPage = lastPage,
        citeKey = citeKey?.ifBlank { null },
        detailsFetched = detailsFetched,
        pdfSource = if (staged != null && backupPdf != null) {
            if (backupPdf.source == PDF_SOURCE_DOWNLOADED) PDF_SOURCE_DOWNLOADED else PDF_SOURCE_ATTACHED
        } else {
            null
        },
        pdfSize = staged?.size,
        pdfAddedAt = if (staged != null) backupPdf?.addedAt else null,
        pdfLastPage = if (staged != null) backupPdf?.lastPage?.coerceAtLeast(0) else null
    )
    val noteRow = notes?.let {
        PaperNotesEntity(localId, it.summary, it.researchQuestion, it.method, it.keyFindings, it.limitations, it.thoughts, it.updatedAt)
    }?.takeIf { row ->
        listOf(row.summary, row.researchQuestion, row.method, row.keyFindings, row.limitations, row.thoughts).any { it.isNotBlank() }
    }
    return IncomingPaper(
        ref = ref,
        paper = entity,
        authors = authors.mapIndexed { index, author -> PaperAuthorEntity(localId, index, author.name, author.openAlexAuthorId) },
        notes = noteRow
    )
}
