package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "paper_authors",
    primaryKeys = ["paper_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paper_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PaperAuthorEntity(
    @ColumnInfo(name = "paper_id") val paperId: String,
    val position: Int,
    val name: String,
    @ColumnInfo(name = "open_alex_author_id") val openAlexAuthorId: String?
)
