package com.etatech.hashiya.core.data.importer

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportFileStoreTest {
    private val directory = File(Files.createTempDirectory("files").toFile(), "imports")
    private val files = ImportFileStore(directory)

    @Test
    fun `text is written, read back and deleted per job`() {
        files.write(1, "TY  - JOUR\nTI  - تحليل\nER  - ")
        files.write(2, "@misc{k}")
        assertEquals("TY  - JOUR\nTI  - تحليل\nER  - ", files.read(1))
        files.delete(1)
        assertNull(files.read(1))
        assertEquals("@misc{k}", files.read(2))
    }

    @Test
    fun `a job with no file reads as null`() {
        assertNull(files.read(9))
    }
}
