package com.etatech.hashiya.core.data.backup

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val manifest = """{"format":1,"exportedAt":"2026-10-04T14:05:00Z"}"""
    private val library = """{"papers":[{"ref":1,"title":"T","savedAt":1}],"collections":[{"name":"C","createdAt":1,"papers":[1]}]}"""

    private fun zip(vararg entries: Pair<String, ByteArray>): File = tmp.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (name, bytes) ->
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
    }

    private fun zipText(vararg entries: Pair<String, String>) = zip(*entries.map { it.first to it.second.toByteArray() }.toTypedArray())

    private fun invalid(file: File) = (readArchive(file) as ArchiveRead.Invalid).reason

    @Test
    fun readsTheSharedFixture() {
        val read = readArchive(File(System.getProperty("hashiya.testdata"), "backup/format-1.hashiya")) as ArchiveRead.Valid
        assertEquals(3, read.library.papers.size)
    }

    @Test
    fun readsAValidArchive() {
        assertTrue(readArchive(zipText(MANIFEST_ENTRY to manifest, LIBRARY_ENTRY to library)) is ArchiveRead.Valid)
    }

    @Test
    fun notAZip() = assertEquals(OpenFailure.NotABackup, invalid(tmp.newFile().apply { writeText("hello") }))

    @Test
    fun zipWithoutManifest() = assertEquals(OpenFailure.NotABackup, invalid(zipText("other.txt" to "x")))

    @Test
    fun manifestThatIsNotJson() = assertEquals(OpenFailure.NotABackup, invalid(zipText(MANIFEST_ENTRY to "<xml/>", LIBRARY_ENTRY to library)))

    @Test
    fun newerFormat() = assertEquals(OpenFailure.NewerFormat, invalid(zipText(MANIFEST_ENTRY to """{"format":2}""", LIBRARY_ENTRY to library)))

    @Test
    fun missingLibrary() = assertEquals(OpenFailure.Damaged, invalid(zipText(MANIFEST_ENTRY to manifest)))

    @Test
    fun malformedLibrary() = assertEquals(OpenFailure.Damaged, invalid(zipText(MANIFEST_ENTRY to manifest, LIBRARY_ENTRY to """{"papers":[{"ref":1}]}""")))

    @Test
    fun duplicateRefs() = assertEquals(
        OpenFailure.Damaged,
        invalid(zipText(MANIFEST_ENTRY to manifest, LIBRARY_ENTRY to """{"papers":[{"ref":1,"title":"A","savedAt":1},{"ref":1,"title":"B","savedAt":1}]}"""))
    )

    @Test
    fun collectionPointingAtAnUnknownRef() = assertEquals(
        OpenFailure.Damaged,
        invalid(zipText(MANIFEST_ENTRY to manifest, LIBRARY_ENTRY to """{"papers":[],"collections":[{"name":"C","createdAt":1,"papers":[5]}]}"""))
    )

    @Test
    fun oversizedLibrary() {
        val huge = ByteArray(MAX_LIBRARY_BYTES + 1) { ' '.code.toByte() }
        assertEquals(OpenFailure.Damaged, invalid(zip(MANIFEST_ENTRY to manifest.toByteArray(), LIBRARY_ENTRY to huge)))
    }
}
