package com.etatech.hashiya.core.importer

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The iOS package can't read files outside its directory, so its tests use a copy of testdata/import. Both platforms must
 * parse the same bytes against the same expected JSON: this fails when the copy drifts.
 */
class IosFixtureCopyTest {
    private val iosCopy = File(importTestData.parentFile.parentFile, "ios/HashiyaKit/Tests/HashiyaImportTests/Fixtures")

    private fun files(root: File): List<String> = root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).path }.sorted().toList()

    @Test
    fun theIosCopyMatchesTestdataByteForByte() {
        val names = files(importTestData)
        assertEquals(names, files(iosCopy))
        names.forEach { name -> assertArrayEquals(name, File(importTestData, name).readBytes(), File(iosCopy, name).readBytes()) }
    }
}
