package com.etatech.hashiya.core.data.mapping

import com.etatech.hashiya.core.model.ReadingStatus

/** The value stored in `papers.reading_status`. Fixed strings, so renaming the enum never changes stored data. */
internal val ReadingStatus.storedValue: String
    get() = when (this) {
        ReadingStatus.ToRead -> "to_read"
        ReadingStatus.Reading -> "reading"
        ReadingStatus.Read -> "read"
    }

/** Reads a stored value back; anything unknown is To read. */
internal fun readingStatusOf(stored: String): ReadingStatus =
    ReadingStatus.entries.firstOrNull { it.storedValue == stored } ?: ReadingStatus.ToRead
