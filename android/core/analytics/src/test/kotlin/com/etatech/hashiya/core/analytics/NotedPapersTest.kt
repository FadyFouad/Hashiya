package com.etatech.hashiya.core.analytics

import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotedPapersTest {
    @Test
    fun onlyTheFirstEditOfAPaperCounts() {
        val id = UUID.randomUUID().toString()
        assertTrue(NotedPapers.firstEdit(id))
        assertFalse(NotedPapers.firstEdit(id))
        assertTrue(NotedPapers.firstEdit(UUID.randomUUID().toString()))
    }
}
