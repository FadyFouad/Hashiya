package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "collections", indices = [Index(value = ["name_key"], unique = true)])
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** The name trimmed and lowercased (collectionNameKey); enforces "no two collections with the same name". */
    @ColumnInfo(name = "name_key") val nameKey: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
