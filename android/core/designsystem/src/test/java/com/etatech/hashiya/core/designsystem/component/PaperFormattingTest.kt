package com.etatech.hashiya.core.designsystem.component

import com.etatech.hashiya.core.model.Author
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaperFormattingTest {
    private fun authors(vararg names: String) = names.map { Author(it, null) }

    @Test
    fun keepsShortAuthorListsWhole() {
        assertEquals(AuthorSummary("Ada, Bo", 0), summarizeAuthors(authors("Ada", "Bo")))
    }

    @Test
    fun countsAuthorsBeyondTheLimit() {
        assertEquals(AuthorSummary("A, B, C", 2), summarizeAuthors(authors("A", "B", "C", "D", "E")))
    }

    @Test
    fun emptyAuthorList() {
        assertEquals(AuthorSummary("", 0), summarizeAuthors(emptyList()))
    }

    @Test
    fun compactsLargeCounts() {
        assertEquals("128K", compactCount(128_412, Locale.ENGLISH))
        assertEquals("999", compactCount(999, Locale.ENGLISH))
    }
}
