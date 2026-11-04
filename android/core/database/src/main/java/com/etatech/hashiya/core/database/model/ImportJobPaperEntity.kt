package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** A paper an import saved, so a resumed import knows its own papers from ones that were already in the library. */
@Entity(
    tableName = "import_job_papers",
    primaryKeys = ["job_id", "paper_id"],
    foreignKeys = [
        ForeignKey(
            entity = ImportJobEntity::class,
            parentColumns = ["id"],
            childColumns = ["job_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paper_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paper_id")]
)
data class ImportJobPaperEntity(@ColumnInfo(name = "job_id") val jobId: Long, @ColumnInfo(name = "paper_id") val paperId: String)
