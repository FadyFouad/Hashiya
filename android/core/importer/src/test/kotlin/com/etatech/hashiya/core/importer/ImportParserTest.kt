package com.etatech.hashiya.core.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportParserTest {
    @Test
    fun `decode drops a UTF-8 byte order mark`() {
        assertEquals(
            "TY",
            ImportParser.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 'T'.code.toByte(), 'Y'.code.toByte()))
        )
    }

    @Test
    fun `decode reads UTF-8`() {
        assertEquals("Gödel تحليل", ImportParser.decode("Gödel تحليل".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `decode falls back to Windows-1252 when the bytes are not UTF-8`() {
        val bytes = byteArrayOf(0x47, 0xF6.toByte(), 0x64, 0x65, 0x6C, 0x20, 0x93.toByte(), 0x71, 0x94.toByte(), 0x20, 0x96.toByte())
        assertEquals("Gödel “q” –", ImportParser.decode(bytes))
    }

    @Test
    fun `parse drops a BOM left in the text`() {
        assertEquals(ImportFormat.Ris, ImportParser.parse("TY  - JOUR\nTI  - T\nER  - ")?.format)
    }

    @Test
    fun `CRLF and lone CR count as line ends`() {
        val crlf = ImportParser.parse("TY  - JOUR\r\nTI  - One\r\nER  - \r\n\r\nTY  - JOUR\r\nTI  - Two\r\nER  - \r\n")!!
        assertEquals(listOf(1 to "One", 5 to "Two"), crlf.entries.map { it.line to it.title })
        val cr = ImportParser.parse("@misc{a, title = {A}}\r@misc{b,\r title = {B}}")!!
        assertEquals(listOf(1 to "A", 2 to "B"), cr.entries.map { it.line to it.title })
    }

    @Test
    fun `the format comes from the content`() {
        assertEquals(ImportFormat.Ris, ImportParser.parse("\n\nTY  - JOUR\nTI  - T\nER  - ")?.format)
        assertEquals(ImportFormat.BibTeX, ImportParser.parse("Exported by Mendeley\n\n@article{k, title = {T}}")?.format)
        assertEquals(ImportFormat.BibTeX, ImportParser.parse("% comment\n  @Article ( k, title = {T} )")?.format)
    }

    @Test
    fun `a RIS file whose first record lacks TY is still RIS`() {
        val result = ImportParser.parse("AU  - Stray, A\nER  - \nTY  - JOUR\nTI  - T\nER  - ")
        assertEquals(ImportFormat.Ris, result?.format)
        assertEquals(listOf(ImportIssue(1, ImportIssueKind.MissingType)), result?.issues)
    }

    @Test
    fun `anything else is not supported`() {
        assertNull(ImportParser.parse(""))
        assertNull(ImportParser.parse("Dear reviewer, please email me@example.org {soon}."))
        assertNull(ImportParser.parse("<?xml version=\"1.0\"?><records/>"))
        assertNull(ImportParser.parse("PMID- 12345\nTI  - Medline is not RIS"))
    }
}
