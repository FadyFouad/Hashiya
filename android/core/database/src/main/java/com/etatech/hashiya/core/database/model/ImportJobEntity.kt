package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** An import of a .bib or .ris file; core/data maps it to ImportJob. The file's text lives in filesDir, not here. */
@Entity(tableName = "import_jobs")
data class ImportJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** One of "analyzing", "ready", "importing", "done", "failed", "cancelled". */
    val state: String,
    /** One of "unsupported", "too_large", "unreadable", "nothing_to_import" when [state] is "failed". */
    val failure: String? = null,
    @ColumnInfo(name = "add_to_collection") val addToCollection: Boolean = true,
    /** The new collection's name, made unique when the import phase creates it. */
    @ColumnInfo(name = "collection_name") val collectionName: String = "",
    /** Set once the collection exists. Not a foreign key: a collection deleted meanwhile only means its links are skipped. */
    @ColumnInfo(name = "collection_id") val collectionId: Long? = null,
    val found: Int = 0,
    val matched: Int = 0,
    val local: Int = 0,
    @ColumnInfo(name = "in_library") val inLibrary: Int = 0,
    val repeated: Int = 0,
    val unreadable: Int = 0,
    @ColumnInfo(name = "not_checked") val notChecked: Int = 0,
    val processed: Int = 0,
    val imported: Int = 0
)
