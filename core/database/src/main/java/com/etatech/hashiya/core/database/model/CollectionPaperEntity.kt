package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** A saved paper's membership in a collection. Deleting either side deletes the link, never the other side. */
@Entity(
    tableName = "collection_papers",
    primaryKeys = ["collection_id", "paper_id"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collection_id"],
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
data class CollectionPaperEntity(
    @ColumnInfo(name = "collection_id") val collectionId: Long,
    @ColumnInfo(name = "paper_id") val paperId: String,
    @ColumnInfo(name = "added_at") val addedAt: Long
)
