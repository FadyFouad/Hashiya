package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "papers",
    indices = [
        Index(value = ["open_alex_id"], unique = true),
        // Not unique: OpenAlex sometimes has several works (preprint, published version) with one DOI,
        // and each must be savable. Deduplication by DOI is a later sub-project's decision.
        Index(value = ["doi"])
    ]
)
data class PaperEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "open_alex_id") val openAlexId: String?,
    val doi: String?,
    val title: String,
    val year: Int?,
    val venue: String?,
    val abstract: String?,
    @ColumnInfo(name = "citation_count") val citationCount: Int,
    @ColumnInfo(name = "is_open_access") val isOpenAccess: Boolean,
    @ColumnInfo(name = "oa_pdf_url") val oaPdfUrl: String?,
    @ColumnInfo(name = "saved_at") val savedAt: Long,
    /** One of "to_read", "reading", "read"; core/data maps it to ReadingStatus. */
    @ColumnInfo(name = "reading_status", defaultValue = "'to_read'") val readingStatus: String
)
