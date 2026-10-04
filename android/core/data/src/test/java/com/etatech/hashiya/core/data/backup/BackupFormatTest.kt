package com.etatech.hashiya.core.data.backup

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {
    private val fixture = File(System.getProperty("hashiya.testdata"), "backup/format-1.hashiya")

    private fun ZipFile.text(name: String) = getInputStream(getEntry(name)).use { it.reader(Charsets.UTF_8).readText() }

    @Test
    fun decodesTheSharedFixture() {
        ZipFile(fixture).use { zip ->
            val manifest = backupJson.decodeFromString(BackupManifest.serializer(), zip.text(MANIFEST_ENTRY))
            assertEquals(1, manifest.format)
            assertTrue(manifest.includesPdfs)

            val library = backupJson.decodeFromString(BackupLibrary.serializer(), zip.text(LIBRARY_ENTRY))
            assertEquals(listOf(1, 2, 3), library.papers.map { it.ref })

            val first = library.papers[0]
            assertEquals("W2741809807", first.openAlexId)
            assertEquals("lecun2015deep", first.citeKey)
            assertEquals(listOf("Yann LeCun", "Yoshua Bengio", "Geoffrey Hinton"), first.authors.map { it.name })
            assertNull(first.authors[2].openAlexAuthorId)
            assertEquals("Cite in chapter 2", first.notes?.thoughts)
            assertEquals(BackupPdf("downloaded", 1790000200000, 4, "pdfs/1.pdf"), first.pdf)

            val second = library.papers[1]
            assertNull(second.openAlexId)
            assertNull(second.doi)
            assertEquals("التعلم العميق في معالجة اللغة العربية", second.title)
            assertEquals(0, second.citationCount)
            assertNull(second.pdf?.file)

            assertEquals("read", library.papers[2].readingStatus)
            assertEquals(listOf(BackupCollection("Thesis", 1790000600000, listOf(1, 2)), BackupCollection("مراجعة", 1790000700000, listOf(3))), library.collections)
            assertTrue(zip.getEntry(pdfEntryName(1)) != null)
        }
    }

    @Test
    fun roundTripsAPaper() {
        val paper = BackupPaper(ref = 7, title = "T", savedAt = 5, notes = BackupNotes(summary = "s", updatedAt = 6))
        val text = backupJson.encodeToString(BackupLibrary.serializer(), BackupLibrary(papers = listOf(paper)))
        assertEquals(paper, backupJson.decodeFromString(BackupLibrary.serializer(), text).papers.single())
    }
}
