package com.etatech.hashiya.core.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RisParserTest {
    private fun parse(vararg lines: String) = RisParser.parse(lines.joinToString("\n"))

    private fun single(vararg lines: String): ImportedEntry = parse(*lines).entries.single()

    @Test
    fun `a record from TY to ER`() {
        val entry = single("", "TY  - JOUR", "TI  - A title", "ER  - ")
        assertEquals(2, entry.line)
        assertEquals("JOUR", entry.entryType)
        assertEquals("A title", entry.title)
        assertNull(entry.citeKey)
    }

    @Test
    fun `tags with one space before the hyphen, and ER without a trailing space`() {
        val result = parse("TY - BOOK", "TI - One", "ER  -", "TY  - BOOK", "TI  - Two", "ER  -")
        assertEquals(listOf("One", "Two"), result.entries.map { it.title })
    }

    @Test
    fun `lines that are not tag lines continue the previous value`() {
        val entry = single("TY  - JOUR", "TI  - T", "AB  - First line", "  second line", "third", "", "ER  - ")
        assertEquals("First line second line third", entry.abstract)
    }

    @Test
    fun `a TY closes an open record, and so does the end of the file`() {
        val result = parse("TY  - JOUR", "TI  - One", "TY  - JOUR", "TI  - Two")
        assertEquals(listOf("One", "Two"), result.entries.map { it.title })
        assertEquals(emptyList<ImportIssue>(), result.issues)
    }

    @Test
    fun `tag lines outside a record are one MissingType issue and are skipped`() {
        val result =
            parse("TY  - JOUR", "TI  - One", "ER  - ", "AU  - Stray, A", "TI  - Stray", "ER  - ", "TY  - JOUR", "TI  - Two", "ER  - ")
        assertEquals(listOf("One", "Two"), result.entries.map { it.title })
        assertEquals(listOf(ImportIssue(4, ImportIssueKind.MissingType)), result.issues)
    }

    @Test
    fun `a record with neither title nor DOI is Empty`() {
        val result = parse("TY  - GEN", "TI  - ", "AU  - Doe, J", "ER  - ")
        assertEquals(listOf(ImportIssue(1, ImportIssueKind.Empty)), result.issues)
    }

    @Test
    fun `authors from AU and A1 in order, editors only when there are none`() {
        assertEquals(
            listOf("Yann LeCun", "Geoffrey Hinton", "John Smith Jr.", "World Health Organization"),
            single(
                "TY  - JOUR",
                "TI  - T",
                "AU  - LeCun, Yann",
                "A1  - Hinton, Geoffrey",
                "A2  - Editor, E",
                "AU  - Smith, John, Jr.",
                "AU  - World Health Organization",
                "ER  - "
            ).authors
        )
        assertEquals(listOf("E Editor"), single("TY  - EDBOOK", "TI  - T", "A2  - Editor, E", "ER  - ").authors)
    }

    @Test
    fun `title from TI, else T1`() {
        assertEquals("One", single("TY  - JOUR", "T1  - Two", "TI  - One", "ER  - ").title)
        assertEquals("Two", single("TY  - JOUR", "T1  - Two", "ER  - ").title)
    }

    @Test
    fun `year from PY, then Y1, then DA`() {
        assertEquals(2015, single("TY  - JOUR", "TI  - T", "DA  - 2016/01//", "PY  - 2015", "ER  - ").year)
        assertEquals(2020, single("TY  - JOUR", "TI  - T", "Y1  - 2020///", "ER  - ").year)
        assertEquals(2016, single("TY  - JOUR", "TI  - T", "DA  - 2016/01//", "ER  - ").year)
    }

    @Test
    fun `venue order and the book, thesis and report rules`() {
        assertEquals(
            "T2",
            single("TY  - JOUR", "TI  - T", "J2  - J2", "JA  - JA", "BT  - BT", "JO  - JO", "JF  - JF", "T2  - T2", "ER  - ").venue
        )
        assertEquals("JF", single("TY  - JOUR", "TI  - T", "J2  - J2", "JA  - JA", "BT  - BT", "JO  - JO", "JF  - JF", "ER  - ").venue)
        assertEquals("JO", single("TY  - JOUR", "TI  - T", "J2  - J2", "JA  - JA", "BT  - BT", "JO  - JO", "ER  - ").venue)
        assertEquals("BT", single("TY  - JOUR", "TI  - T", "J2  - J2", "JA  - JA", "BT  - BT", "ER  - ").venue)
        assertEquals("JA", single("TY  - JOUR", "TI  - T", "J2  - J2", "JA  - JA", "ER  - ").venue)
        assertEquals("J2", single("TY  - JOUR", "TI  - T", "J2  - J2", "ER  - ").venue)
        assertNull(single("TY  - BOOK", "TI  - T", "T2  - A series", "ER  - ").venue)
        assertEquals("Uni", single("TY  - THES", "TI  - T", "PB  - Uni", "ER  - ").venue)
        assertEquals("WHO", single("TY  - RPRT", "TI  - T", "PB  - WHO", "ER  - ").venue)
        assertNull(single("TY  - JOUR", "TI  - T", "PB  - Publisher", "ER  - ").venue)
    }

    @Test
    fun `pages from SP and EP, or a range in SP`() {
        val both = single("TY  - JOUR", "TI  - T", "SP  - 436", "EP  - 444", "ER  - ")
        assertEquals("436" to "444", both.firstPage to both.lastPage)
        val range = single("TY  - JOUR", "TI  - T", "SP  - 770-778", "ER  - ")
        assertEquals("770" to "778", range.firstPage to range.lastPage)
        val one = single("TY  - JOUR", "TI  - T", "SP  - e1001", "ER  - ")
        assertEquals("e1001" to null, one.firstPage to one.lastPage)
    }

    @Test
    fun `abstract from AB, else N2, and the other plain fields`() {
        val entry = single(
            "TY  - JOUR",
            "TI  - T",
            "N2  - n2",
            "AB  - ab",
            "VL  - 5",
            "IS  - 2",
            "PB  - P",
            "UR  - https://a",
            "UR  - https://b",
            "ER  - "
        )
        assertEquals("ab", entry.abstract)
        assertEquals("5", entry.volume)
        assertEquals("2", entry.issue)
        assertEquals("P", entry.publisher)
        assertEquals("https://a", entry.url)
        assertEquals("n2", single("TY  - JOUR", "TI  - T", "N2  - n2", "ER  - ").abstract)
    }

    @Test
    fun `DOI from DO, UR, L3, M3 or N1, in that order`() {
        assertEquals(
            "10.1/do",
            single("TY  - JOUR", "TI  - T", "N1  - 10.1/n1", "UR  - https://doi.org/10.1/ur", "DO  - 10.1/DO", "ER  - ").doi
        )
        assertEquals("10.1/ur", single("TY  - JOUR", "TI  - T", "N1  - 10.1/n1", "UR  - https://doi.org/10.1/ur", "ER  - ").doi)
        assertEquals("10.1/l3", single("TY  - JOUR", "TI  - T", "N1  - 10.1/n1", "L3  - 10.1/l3", "UR  - https://x.org", "ER  - ").doi)
        assertEquals("10.1/m3", single("TY  - JOUR", "TI  - T", "N1  - doi: 10.1/n1", "M3  - 10.1/m3", "ER  - ").doi)
        assertEquals("10.1/n1", single("TY  - JOUR", "TI  - T", "M3  - PhD Thesis", "N1  - doi: 10.1/n1", "ER  - ").doi)
        assertNull(single("TY  - JOUR", "TI  - T", "N1  - Cited by 10.", "ER  - ").doi)
    }

    @Test
    fun `an arXiv link becomes its arXiv DOI and makes an untyped record a preprint`() {
        val entry = single("TY  - GEN", "TI  - T", "UR  - https://arxiv.org/abs/1706.03762v7", "ER  - ")
        assertEquals("10.48550/arxiv.1706.03762", entry.doi)
        assertEquals("preprint" to "repository", entry.workType to entry.sourceType)
    }

    @Test
    fun `percent-encoded doi links are decoded`() {
        assertEquals(
            "10.1002/(sici)1097-0258",
            single("TY  - JOUR", "TI  - T", "UR  - https://doi.org/10.1002/%28SICI%291097-0258", "ER  - ").doi
        )
    }

    @Test
    fun `TY codes map to OpenAlex's work and source types`() {
        fun kinds(type: String) = single("TY  - $type", "TI  - T", "ER  - ").let { it.workType to it.sourceType }
        assertEquals("article" to "journal", kinds("JOUR"))
        assertEquals("article" to "journal", kinds("JFULL"))
        assertEquals("article" to "journal", kinds("EJOUR"))
        assertEquals("article" to null, kinds("MGZN"))
        assertEquals("article" to null, kinds("NEWS"))
        assertEquals("article" to "conference", kinds("CONF"))
        assertEquals("article" to "conference", kinds("CPAPER"))
        assertEquals("book" to null, kinds("BOOK"))
        assertEquals("book" to null, kinds("EDBOOK"))
        assertEquals("book-chapter" to null, kinds("CHAP"))
        assertEquals("dissertation" to null, kinds("THES"))
        assertEquals("report" to null, kinds("RPRT"))
        assertEquals(null to null, kinds("ELEC"))
        assertEquals(null to null, kinds("GEN"))
    }
}
