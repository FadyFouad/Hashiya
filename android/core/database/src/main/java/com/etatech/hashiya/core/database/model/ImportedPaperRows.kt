package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo

/** Everything one imported paper writes: the paper (with its cite key), its authors and its search row. */
data class ImportedPaperRows(val paper: PaperEntity, val authors: List<PaperAuthorEntity>, val search: PaperSearchEntity)

/** What an import compares entries with: every saved paper's identity, and whether this import saved it. */
data class LibraryKeyRow(
    val id: String,
    @ColumnInfo(name = "open_alex_id") val openAlexId: String?,
    val doi: String?,
    val title: String,
    val year: Int?,
    @ColumnInfo(name = "cite_key") val citeKey: String?,
    /** True when import_job_papers links the paper to the job asked about. */
    @ColumnInfo(name = "imported_by_job") val importedByJob: Boolean
)
