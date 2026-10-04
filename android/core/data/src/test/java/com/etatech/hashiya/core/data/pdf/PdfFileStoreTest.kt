package com.etatech.hashiya.core.data.pdf

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfFileStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var dir: File
    private lateinit var store: PdfFileStore

    @Before
    fun setUp() {
        dir = File(tmp.root, "pdfs")
        store = PdfFileStore(dir)
    }

    private fun stream(bytes: ByteArray) = ByteArrayInputStream(bytes)

    private fun filesInDir(): List<String> = dir.listFiles().orEmpty().map { it.name }.sorted()

    @Test
    fun stageKeepsAPartFileAndCommitMovesItIntoPlace() {
        val staged = store.stage("restore", "%PDF-1.4 hello".byteInputStream(), maxBytes = 1024) {} as StageResult.Staged
        assertTrue(staged.file.name.endsWith(".part"))
        assertFalse(store.file("p1").exists())

        store.commit(staged.file, "p1")

        assertFalse(staged.file.exists())
        assertEquals("%PDF-1.4 hello", store.file("p1").readText())
        assertEquals(14L, staged.size)
    }

    @Test
    fun stageRejectsANonPdfAndLeavesNothing() {
        assertEquals(StageResult.NotPdf, store.stage("restore", "<html>".byteInputStream(), maxBytes = 1024) {})
        assertTrue(filesInDir().isEmpty())
    }

    @Test
    fun sweepDeletesUncommittedStagedFiles() {
        store.stage("restore", "%PDF-1.4".byteInputStream(), maxBytes = 1024) {}
        store.sweep(keep = emptySet())
        assertTrue(filesInDir().isEmpty())
    }

    @Test
    fun storesAPdfUnderThePaperIdAndReturnsItsSize() {
        val result = store.store("local-1", stream(PDF), MAX_PDF_BYTES) {}

        assertEquals(StoreResult.Stored(PDF.size.toLong()), result)
        assertEquals(File(dir, "local-1.pdf"), store.file("local-1"))
        assertArrayEquals(PDF, store.file("local-1").readBytes())
        assertEquals(listOf("local-1.pdf"), filesInDir())
    }

    @Test
    fun reportsTheBytesCopiedSoFar() {
        val big = PDF + ByteArray(200_000) { 'x'.code.toByte() }
        val progress = mutableListOf<Long>()

        store.store("local-1", stream(big), MAX_PDF_BYTES) { progress += it }

        assertTrue(progress.isNotEmpty())
        assertEquals(progress.sorted(), progress)
        assertEquals(big.size.toLong(), progress.last())
    }

    @Test
    fun acceptsAShortPreambleBeforeTheHeader() {
        val withPreamble = ByteArray(300) { ' '.code.toByte() } + PDF

        assertEquals(StoreResult.Stored(withPreamble.size.toLong()), store.store("local-1", stream(withPreamble), MAX_PDF_BYTES) {})
    }

    @Test
    fun rejectsAFileWithoutTheHeaderAndLeavesNothing() {
        assertEquals(StoreResult.NotPdf, store.store("local-1", stream(HTML), MAX_PDF_BYTES) {})
        assertEquals(emptyList<String>(), filesInDir())
    }

    @Test
    fun rejectsAHeaderThatOnlyAppearsAfterTheFirst1024Bytes() {
        val late = ByteArray(1024) { ' '.code.toByte() } + PDF

        assertEquals(StoreResult.NotPdf, store.store("local-1", stream(late), MAX_PDF_BYTES) {})
    }

    @Test
    fun stopsReadingALargeNonPdfSoonAfterItsStart() {
        val counting = CountingInputStream(stream(ByteArray(5_000_000) { 'a'.code.toByte() }))

        assertEquals(StoreResult.NotPdf, store.store("local-1", counting, MAX_PDF_BYTES) {})
        assertTrue("read ${counting.count} bytes", counting.count < 200_000)
    }

    @Test
    fun stopsAtTheSizeLimitAndLeavesNothing() {
        assertEquals(StoreResult.TooLarge, store.store("local-1", stream(PDF + ByteArray(100)), maxBytes = 64) {})
        assertEquals(emptyList<String>(), filesInDir())
    }

    @Test
    fun aFileExactlyAtTheLimitIsStored() {
        assertEquals(StoreResult.Stored(PDF.size.toLong()), store.store("local-1", stream(PDF), maxBytes = PDF.size.toLong()) {})
    }

    @Test
    fun storingAgainReplacesTheFile() {
        store.store("local-1", stream(PDF), MAX_PDF_BYTES) {}
        val second = PDF + "% second".toByteArray()

        store.store("local-1", stream(second), MAX_PDF_BYTES) {}

        assertArrayEquals(second, store.file("local-1").readBytes())
        assertEquals(listOf("local-1.pdf"), filesInDir())
    }

    @Test
    fun aRejectedReplacementKeepsTheCurrentFile() {
        store.store("local-1", stream(PDF), MAX_PDF_BYTES) {}

        assertEquals(StoreResult.NotPdf, store.store("local-1", stream(HTML), MAX_PDF_BYTES) {})
        assertArrayEquals(PDF, store.file("local-1").readBytes())
    }

    @Test
    fun aStreamThatBreaksOffLeavesNoTemporaryFile() {
        val breaking = object : InputStream() {
            private var sent = 0
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (sent > 0) throw IOException("connection reset")
                PDF.copyInto(b, off)
                sent = PDF.size
                return PDF.size
            }
        }

        try {
            store.store("local-1", breaking, MAX_PDF_BYTES) {}
            fail("Expected IOException")
        } catch (e: IOException) {
            // The read side's failure is passed on as is.
        }
        assertEquals(emptyList<String>(), filesInDir())
    }

    @Test
    fun deleteRemovesTheFile() {
        store.store("local-1", stream(PDF), MAX_PDF_BYTES) {}

        store.delete("local-1")
        store.delete("never-stored")

        assertFalse(store.file("local-1").exists())
    }

    @Test
    fun sweepKeepsListedIdsAndRemovesTheRest() {
        store.store("keep", stream(PDF), MAX_PDF_BYTES) {}
        store.store("orphan", stream(PDF), MAX_PDF_BYTES) {}

        store.sweep(keep = setOf("keep"))

        assertEquals(listOf("keep.pdf"), filesInDir())
    }

    @Test
    fun sweepRemovesTemporaryFiles() {
        store.store("keep", stream(PDF), MAX_PDF_BYTES) {}
        File(dir, "keep-123$TEMP_SUFFIX").writeText("half a download")
        File(dir, "gone-456$TEMP_SUFFIX").writeText("half an attach")

        store.sweep(keep = setOf("keep"))

        assertEquals(listOf("keep.pdf"), filesInDir())
    }

    @Test
    fun sweepWithNoFolderYetDoesNothing() {
        store.sweep(keep = emptySet())

        assertFalse(dir.exists())
    }

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var count = 0L
            private set

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
    }

    private companion object {
        val PDF = "%PDF-1.7\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n".toByteArray()
        val HTML = "<!DOCTYPE html><html><head><title>Sign in</title></head><body>Access denied</body></html>".toByteArray()
    }
}
