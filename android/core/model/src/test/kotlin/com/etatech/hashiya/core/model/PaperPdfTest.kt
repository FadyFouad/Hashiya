package com.etatech.hashiya.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PaperPdfTest {
    private val paper = Paper(
        openAlexId = "W1",
        doi = null,
        title = "Attention Is All You Need",
        authors = emptyList(),
        year = 2017,
        venue = null,
        abstract = null,
        citationCount = 0,
        isOpenAccess = true,
        openAccessPdfUrl = "https://arxiv.org/pdf/1706.03762"
    )

    @Test
    fun aNewPdfStartsOnTheFirstPage() {
        assertEquals(0, PaperPdf(PdfSource.Downloaded, sizeBytes = 2_400_000, addedAt = 5).lastPage)
    }

    @Test
    fun libraryPapersHaveNoPdfUnlessToldOtherwise() {
        assertFalse(LibraryPaper(paper, ReadingStatus.ToRead).hasPdf)
        assertNotEquals(LibraryPaper(paper, ReadingStatus.ToRead), LibraryPaper(paper, ReadingStatus.ToRead, hasPdf = true))
    }

    @Test
    fun storageKeepsDownloadedAndAttachedApart() {
        val storage = PdfStorage(downloadedBytes = 10, downloadedCount = 1, attachedBytes = 20, attachedCount = 2)
        assertEquals(listOf(10L, 20L), listOf(storage.downloadedBytes, storage.attachedBytes))
        assertEquals(listOf(1, 2), listOf(storage.downloadedCount, storage.attachedCount))
    }
}
