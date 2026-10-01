package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo

/** A collection and how many saved papers it holds. */
data class CollectionWithCount(val id: Long, val name: String, @ColumnInfo(name = "paper_count") val paperCount: Int)
