package com.etatech.hashiya.core.data.mapping

import com.etatech.hashiya.core.model.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStatusMappingTest {
    /** These strings are stored on the user's phone: renaming the enum must never change them. */
    @Test
    fun storedValuesAreFixed() {
        assertEquals(listOf("to_read", "reading", "read"), ReadingStatus.entries.map { it.storedValue })
    }

    @Test
    fun readsStoredValuesBack() {
        ReadingStatus.entries.forEach { assertEquals(it, readingStatusOf(it.storedValue)) }
    }

    @Test
    fun unknownStoredValueReadsAsToRead() {
        assertEquals(ReadingStatus.ToRead, readingStatusOf("archived"))
        assertEquals(ReadingStatus.ToRead, readingStatusOf(""))
        assertEquals(ReadingStatus.ToRead, readingStatusOf("ToRead"))
    }
}
