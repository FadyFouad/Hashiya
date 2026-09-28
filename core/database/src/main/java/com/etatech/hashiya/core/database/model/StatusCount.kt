package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo

/** How many saved papers have one stored reading status. */
data class StatusCount(@ColumnInfo(name = "reading_status") val readingStatus: String, val count: Int)
