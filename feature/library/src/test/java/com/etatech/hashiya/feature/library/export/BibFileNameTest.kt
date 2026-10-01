package com.etatech.hashiya.feature.library.export

import org.junit.Assert.assertEquals
import org.junit.Test

class BibFileNameTest {
    @Test
    fun wholeLibrary() = assertEquals("hashiya-library.bib", bibFileName(null))

    @Test
    fun collectionNameWithUnsafeCharactersReplaced() {
        assertEquals("Chapter 2.bib", bibFileName("Chapter 2"))
        assertEquals("a-b-c-d-e-f-g-h-i-j.bib", bibFileName("a/b\\c:d*e?f\"g<h>i|j"))
        assertEquals("tab-x.bib", bibFileName("tab\tx"))
        assertEquals("الفصل الثاني.bib", bibFileName("الفصل الثاني"))
    }

    @Test
    fun nothingLeftIsCollection() {
        assertEquals("-.bib", bibFileName("/"))
        assertEquals("collection.bib", bibFileName("   "))
    }
}
