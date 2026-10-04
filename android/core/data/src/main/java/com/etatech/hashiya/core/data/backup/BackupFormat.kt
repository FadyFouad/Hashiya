package com.etatech.hashiya.core.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The `.hashiya` archive, format 1 (docs/superpowers/specs/2026-10-04-backup-and-restore-design.md §3). These types are the
 * file format: they are kept apart from the Room entities so a schema change never silently changes what a backup holds.
 */
internal const val BACKUP_FORMAT = 1
internal const val MANIFEST_ENTRY = "manifest.json"
internal const val LIBRARY_ENTRY = "library.json"
internal const val MAX_LIBRARY_BYTES = 50 * 1024 * 1024
internal const val MAX_MANIFEST_BYTES = 64 * 1024

/** The only entry a paper's PDF may be read from; any other `pdf.file` counts as missing. */
internal fun pdfEntryName(ref: Int): String = "pdfs/$ref.pdf"

@Serializable
internal data class BackupManifest(
    val format: Int,
    val app: String = "",
    /** ISO 8601 in UTC, e.g. `2026-10-04T14:05:00Z`. */
    val exportedAt: String = "",
    val papers: Int = 0,
    val collections: Int = 0,
    val includesPdfs: Boolean = false
)

@Serializable
internal data class BackupLibrary(
    val papers: List<BackupPaper> = emptyList(),
    val collections: List<BackupCollection> = emptyList()
)

@Serializable
internal data class BackupPaper(
    /** Unique within the file; collections and the PDF entry refer to it. */
    val ref: Int,
    val openAlexId: String? = null,
    val doi: String? = null,
    val title: String,
    val year: Int? = null,
    val venue: String? = null,
    val abstract: String? = null,
    val citationCount: Int = 0,
    val isOpenAccess: Boolean = false,
    val oaPdfUrl: String? = null,
    val savedAt: Long,
    /** `to_read`, `reading` or `read`; anything else restores as `to_read`. */
    val readingStatus: String = "to_read",
    val workType: String? = null,
    val sourceType: String? = null,
    val publisher: String? = null,
    val volume: String? = null,
    val issue: String? = null,
    val firstPage: String? = null,
    val lastPage: String? = null,
    val citeKey: String? = null,
    val detailsFetched: Boolean = false,
    /** In position order. */
    val authors: List<BackupAuthor> = emptyList(),
    val notes: BackupNotes? = null,
    val pdf: BackupPdf? = null
)

@Serializable
internal data class BackupAuthor(val name: String, val openAlexAuthorId: String? = null)

@Serializable
internal data class BackupNotes(
    val summary: String = "",
    val researchQuestion: String = "",
    val method: String = "",
    val keyFindings: String = "",
    val limitations: String = "",
    val thoughts: String = "",
    val updatedAt: Long = 0
)

/** A stored PDF. [file] is null when the PDF isn't in the archive (PDFs left out, or missing at export). */
@Serializable
internal data class BackupPdf(val source: String, val addedAt: Long, val lastPage: Int = 0, val file: String? = null)

@Serializable
internal data class BackupCollection(val name: String, val createdAt: Long, val papers: List<Int> = emptyList())

internal val backupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}
